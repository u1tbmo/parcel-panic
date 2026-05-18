package parcelpanic.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import parcelpanic.shared.GameState;
import parcelpanic.shared.PlayerIntent;

/// Represents a client connection to the authoritative game server.
public class GameClient {
  private Socket socket;
  private BufferedReader reader;
  private BufferedWriter writer;

  private volatile GameState latestState;
  private volatile boolean running = false;
  private int playerId = -1;

  public void connect(String host, int port) throws IOException {
    this.socket = new Socket(host, port);
    this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    this.running = true;

    // Listen for incoming states in background thread
    Thread thread = new Thread(this::listen, "GameClient-Reader");
    thread.setDaemon(true);
    thread.start();
  }

  private void listen() {
    try {
      String line;
      while (running && (line = reader.readLine()) != null) {
        if (line.startsWith("WELCOME:")) {
          // Parse client player ID assigned by server handshake
          this.playerId = Integer.parseInt(line.substring(8));

        } else if (line.startsWith("STATE:")) {
          // Deserialize authoritative game state
          String stateJson = line.substring(6);
          this.latestState = Serializer.deserializeGameState(stateJson);
        }
      }
    } catch (IOException e) {
      if (running) {
        System.err.println("[Client] Network reader exception: " + e.getMessage());
      }
    } finally {
      disconnect();
    }
  }

  public void sendIntent(PlayerIntent intent) {
    if (writer == null || !running) {
      return;
    }

    try {
      String json = Serializer.serializePlayerIntent(intent);
      writer.write("INTENT:" + json + "\n");
      writer.flush();
    } catch (IOException e) {
      System.err.println("[Client] Error writing player input: " + e.getMessage());
    }
  }

  public GameState getLatestState() {
    return latestState;
  }

  public int getPlayerId() {
    return playerId;
  }

  public void disconnect() {
    running = false;
    try {
      if (socket != null && !socket.isClosed()) {
        socket.close();
      }
    } catch (IOException e) {
      System.err.println("[Client] Error on disconnect: " + e.getMessage());
    }
  }
}
