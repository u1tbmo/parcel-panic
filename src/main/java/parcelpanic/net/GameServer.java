package parcelpanic.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import parcelpanic.logic.GameSimulation;
import parcelpanic.shared.GameState;
import parcelpanic.shared.PlayerIntent;
import parcelpanic.world.MapLoader;
import parcelpanic.world.TileMap;

/// The authoritative game server. Holds the master GameSimulation instance, accepts client
/// connections, collects their intents, updates the simulation, and broadcasts the resulting
/// GameState back to all clients each frame.
public class GameServer implements Runnable {
  private static final int PORT = 5555;
  private static final int MAX_CLIENTS = 4;
  private static final int MIN_CLIENTS_TO_START = 2;
  private static final double TICK_RATE = 1.0 / 60.0; // 60 FPS

  private static volatile GameServer activeServer = null;

  public static GameServer getActiveServer() {
    return activeServer;
  }

  private ServerSocket serverSocket;
  private GameSimulation simulation;
  private TileMap map;
  private final CopyOnWriteArrayList<ClientConnection> clients = new CopyOnWriteArrayList<>();
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicBoolean matchStarted = new AtomicBoolean(false);
  private final Object clientCountLock = new Object();

  public GameServer() {}

  /// Start the server: load the map, create the simulator, open the ServerSocket, and spawn threads
  /// to accept client connections.
  public void start() throws IOException {
    System.out.println("[Server] Initializing...");

    GameServer currentActive = activeServer;
    if (currentActive != null) {
      System.out.println("[Server] Stopping active server before bind...");
      currentActive.stop();
    }
    activeServer = this;

    // Load the map (same as the client)
    map = MapLoader.loadFromText("/maps/map.txt");
    if (map == null) {
      throw new RuntimeException("Failed to load map");
    }

    // Create the master simulation
    simulation = new GameSimulation(map);

    // Open ServerSocket
    serverSocket = new ServerSocket(PORT);
    System.out.println("[Server] Listening on port " + PORT);

    running.set(true);

    // Spawn a thread to accept client connections
    new Thread(() -> acceptClients()).start();

    // Spawn a thread for the main game loop
    new Thread(this).start();
  }

  /// Accept incoming client connections in a loop.
  private void acceptClients() {
    int nextClientId = 0;

    while (running.get() && !matchStarted.get() && nextClientId < MAX_CLIENTS) {
      try {
        Socket clientSocket = serverSocket.accept();
        int clientId = nextClientId++;

        ClientConnection conn = new ClientConnection(clientSocket, this, clientId);
        clients.add(conn);

        // Add a vehicle for this connected client on the authoritative server simulation.
        simulation.addPlayer(clientId, 0, 0);

        new Thread(conn).start();

        synchronized (clientCountLock) {
          clientCountLock.notifyAll();
        }

        System.out.println(
            "[Server] Client "
                + clientId
                + " accepted. ("
                + clients.size()
                + "/"
                + MAX_CLIENTS
                + ")");
      } catch (IOException e) {
        if (running.get()) {
          System.err.println("[Server] Error accepting client: " + e.getMessage());
        }
      }
    }

    System.out.println("[Server] Connection phase finished.");
  }

  public void startMatch() {
    if (clients.size() < MIN_CLIENTS_TO_START) {
      System.out.println("[Server] Cannot start match: not enough clients");
      return;
    }

    matchStarted.set(true);
    for (ClientConnection client : clients) {
      if (client.isConnected()) {
        simulation.setVehicleColor(client.getClientId(), client.getCarColorIndex());
      }
    }
    synchronized (clientCountLock) {
      clientCountLock.notifyAll();
    }
    broadcastStart();
  }

  public void broadcastStart() {
    System.out.println("[Server] Broadcasting START to all clients!");
    for (ClientConnection client : clients) {
      client.sendStart();
    }
  }

  public void broadcastChatMessage(String message) {
    System.out.println("[Server] Broadcasting CHAT: " + message);
    for (ClientConnection client : clients) {
      client.sendChatMessage(message);
    }
  }

  public void broadcastPlayerList() {
    List<String> playerNames = new ArrayList<>();
    for (ClientConnection conn : clients) {
      if (conn.isConnected()) {
        String name = conn.getCustomName();
        if (conn.getClientId() == 0) {
          name += " (Host)";
        }
        int colorIndex = conn.getCarColorIndex();
        int color = colorIndex / 10;
        int style = colorIndex % 10;
        String colorName =
            switch (color) {
              case 0 -> "Red";
              case 1 -> "Blue";
              case 2 -> "Green";
              case 3 -> "Yellow";
              case 4 -> "Orange";
              case 5 -> "Pink";
              case 6 -> "Magenta";
              default -> "Red";
            };
        playerNames.add(name + "|" + colorName);
      }
    }
    String packet = "PLAYERS:" + String.join(",", playerNames);
    System.out.println("[Server] Broadcasting " + packet);
    for (ClientConnection client : clients) {
      if (client.isConnected()) {
        client.sendRawMessage(packet);
      }
    }
  }

  public int getConnectedCount() {
    clients.removeIf(client -> !client.isConnected());
    return clients.size();
  }

  public boolean isRunning() {
    return running.get();
  }

  /// Main game loop: collect intents, simulate, broadcast state.
  @Override
  public void run() {
    // Wait for the host to start the match
    synchronized (clientCountLock) {
      while (!matchStarted.get() && running.get()) {
        try {
          clientCountLock.wait(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }

    System.out.println("[Server] Game loop starting");

    long lastTickTime = System.nanoTime();

    while (running.get()) {
      long now = System.nanoTime();
      double dt = (now - lastTickTime) / 1_000_000_000.0; // Convert nanoseconds to seconds
      lastTickTime = now;

      // Clamp dt to avoid huge jumps (e.g., on pause)
      if (dt > 0.1) dt = 0.1;

      // ===== GATHER PHASE =====
      // Collect intents from connected clients
      List<PlayerIntent> intents = new ArrayList<>();
      for (ClientConnection client : clients) {
        if (client.isConnected()) {
          intents.add(client.getLatestIntent());
        }
      }

      // ===== SIMULATE PHASE =====
      GameState newState = simulation.update(dt, intents);

      // ===== BROADCAST PHASE =====
      for (ClientConnection client : clients) {
        if (client.isConnected()) {
          client.sendGameState(newState);
        }
      }

      // Remove disconnected clients
      clients.removeIf(c -> !c.isConnected());

      // Check for match end conditions
      if (newState.matchTimer() <= 0 || newState.unhappiness() >= 1.0) {
        System.out.println(
            "[Server] Match ended. Timer: "
                + newState.matchTimer()
                + ", Unhappiness: "
                + newState.unhappiness());
        stop();
      }

      // Frame rate limiting
      try {
        long sleepTime = (long) ((TICK_RATE - dt) * 1_000); // milliseconds
        if (sleepTime > 0) {
          Thread.sleep(sleepTime);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    System.out.println("[Server] Game loop ended");
    cleanup();
  }

  /// Called by ClientConnection when a client disconnects.
  public void clientDisconnected(int clientId) {
    System.out.println("[Server] Client " + clientId + " disconnected");

    clients.removeIf(client -> client.getClientId() == clientId);

    if (simulation != null) {
      simulation.removePlayer(clientId);
    }
    broadcastPlayerList();

    if (matchStarted.get() && clients.size() < MIN_CLIENTS_TO_START) {
      System.out.println("[Server] Game interrupted: not enough clients");
      stop();
    }
  }

  /// Stop the server and clean up.
  public void stop() {
    if (!running.getAndSet(false)) {
      return;
    }

    System.out.println("[Server] Stopping server...");

    if (activeServer == this) {
      activeServer = null;
    }

    // Capture and clear clients immediately to prevent further broadcasts from other threads
    List<ClientConnection> toDisconnect = new ArrayList<>(clients);
    clients.clear();

    // Disconnect each client in a separate thread if possible, or just close sockets
    // Since we're often on the UI thread, we want this to be as fast as possible.
    for (ClientConnection client : toDisconnect) {
      try {
        client.disconnect();
      } catch (Exception e) {
        System.err.println(
            "[Server] Error disconnecting client during shutdown: " + e.getMessage());
      }
    }

    cleanup();
  }

  private void cleanup() {
    try {
      if (serverSocket != null && !serverSocket.isClosed()) {
        serverSocket.close();
      }
    } catch (IOException e) {
      System.err.println("[Server] Error closing ServerSocket: " + e.getMessage());
    }

    System.out.println("[Server] Shutdown complete");
  }

  /// Main entry point for testing.
  public static void main(String[] args) {
    try {
      GameServer server = new GameServer();
      server.start();

      // Keep the server running indefinitely
      Thread.currentThread().join();
    } catch (Exception e) {
      System.err.println("[Server] Fatal error: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
