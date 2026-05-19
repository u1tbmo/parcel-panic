package parcelpanic.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import parcelpanic.shared.GameState;
import parcelpanic.shared.PlayerIntent;

/// Represents a single client connected to the server. Each connection runs in its own thread and
/// handles receiving PlayerIntent from the client and sending GameState back.

public class ClientConnection implements Runnable {
  private final Socket socket;
  private final GameServer server;
  private final int clientId;
  private BufferedReader reader;
  private BufferedWriter writer;
  private volatile boolean connected = true;
  private volatile PlayerIntent latestIntent = null;
  private final BlockingQueue<GameState> statesToSend = new LinkedBlockingQueue<>();
  private String customName = null;
  private int carColorIndex = 1; // Default to Red, Style 1

  public ClientConnection(Socket socket, GameServer server, int clientId) {
    this.socket = socket;
    this.server = server;
    this.clientId = clientId;
  }

  @Override
  public void run() {
    try {
      reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

      System.out.println(
          "[Server] Client " + clientId + " connected from " + socket.getInetAddress());

      // Send welcome message with assigned player ID
      writer.write("WELCOME:" + (clientId + 1) + "\n");
      writer.flush();

      // Broadcast join announcement
      String joinMsg = ">>> Player " + (clientId + 1) + (clientId == 0 ? " (Host)" : "") + " has joined the lobby!";
      server.broadcastChatMessage(joinMsg);
      server.broadcastPlayerList();

      // Main client loop: receive intents and send state
      String line;
      while (connected && (line = reader.readLine()) != null) {
        // Parse incoming message
        if (line.startsWith("INTENT:")) {
          String encodedJson = line.substring(7); // Remove "INTENT:" prefix
          String json = new String(Base64.getDecoder().decode(encodedJson), StandardCharsets.UTF_8);

          PlayerIntent intent = Serializer.deserializePlayerIntent(json);
          if (intent != null) {
            latestIntent = intent;
          }
        } else if (line.startsWith("NAME_COLOR:")) {
          String[] parts = line.split(":", 3);
          if (parts.length >= 3) {
            this.customName = parts[1];
            try {
              this.carColorIndex = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {}
            server.broadcastPlayerList();
          }
        } else if (line.startsWith("CHAT:")) {
          String chatMsg = line.substring(5);
          server.broadcastChatMessage(getCustomName() + ": " + chatMsg);
        } else if (line.equals("PING")) {
          writer.write("PONG\n");
          writer.flush();
        }
      }
    } catch (IOException e) {
      System.err.println("[Server] Client " + clientId + " connection error: " + e.getMessage());
    } finally {
      disconnect();
    }
  }

  /// Notify the client that the match is starting.
  public void sendStart() {
    if (!connected) return;
    try {
      writer.write("START\n");
      writer.flush();
    } catch (IOException e) {
      connected = false;
    }
  }

  /// Send a chat message to this client.
  public void sendChatMessage(String message) {
    if (!connected) return;
    try {
      writer.write("CHAT:" + message + "\n");
      writer.flush();
    } catch (IOException e) {
      connected = false;
    }
  }

  /// Send a raw message to this client.
  public void sendRawMessage(String message) {
    if (!connected) return;
    try {
      writer.write(message + "\n");
      writer.flush();
    } catch (IOException e) {
      connected = false;
    }
  }

  /// Send a GameState to this client (thread-safe, queued). This is called by the server during the
  /// broadcast phase each frame.
  public void sendGameState(GameState state) {
    if (!connected) return;

    try {
      String json = Serializer.serializeGameState(state);
      if (json != null) {
        String encodedJson =
            Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        writer.write("STATE:" + encodedJson + "\n");
        writer.flush();
      }
    } catch (IOException e) {
      System.err.println(
          "[Server] Error sending GameState to client " + clientId + ": " + e.getMessage());
      connected = false;
    }
  }

  /// Retrieve the latest PlayerIntent from this client, or a default/null if none received yet. */
  public PlayerIntent getLatestIntent() {
    if (latestIntent == null) {
      // Return a default intent if none received
      return new PlayerIntent(clientId, false, false, false, false, false, false, false);
    }
    return latestIntent;
  }

  /// Get this client's ID.
  public int getClientId() {
    return clientId;
  }

  /// Check if this client is still connected.
  public boolean isConnected() {
    return connected;
  }

  /// Gracefully disconnect this client.
  public void disconnect() {
    if (!connected) return;

    connected = false;
    try {
      if (socket != null && !socket.isClosed()) {
        socket.close();
      }
    } catch (IOException e) {
      System.err.println(
          "[Server] Error closing client " + clientId + " socket: " + e.getMessage());
    }

    System.out.println("[Server] Client " + clientId + " disconnected");
    if (server.isRunning()) {
      server.broadcastChatMessage(">>> " + getCustomName() + " has left the lobby.");
    }
    server.clientDisconnected(clientId);
  }

  public String getCustomName() {
    return customName != null ? customName : "Player " + (clientId + 1);
  }

  public void setCustomName(String name) {
    this.customName = name;
  }

  public int getCarColorIndex() {
    return carColorIndex;
  }

  public void setCarColorIndex(int colorIndex) {
    this.carColorIndex = colorIndex;
  }
}
