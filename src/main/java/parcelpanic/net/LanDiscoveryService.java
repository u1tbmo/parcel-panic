package parcelpanic.net;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/// Handles lightweight LAN server discovery using UDP broadcast.
/// This is only for finding available servers; the actual game still uses TCP.
public final class LanDiscoveryService {
  private static final int DISCOVERY_PORT = 5556;
  private static final String DISCOVERY_PREFIX = "PARCELPANIC_SERVER:";

  private volatile boolean running = false;
  private DatagramSocket listenerSocket;

  public record DiscoveredServer(String ip, int currentPlayers, int maxPlayers) {
    public boolean isFull() {
      return currentPlayers >= maxPlayers;
    }
  }

  /// Start broadcasting this host as an available LAN server.
  public void startBroadcasting(String hostIp, IntSupplier currentPlayersSupplier, int maxPlayers) {
    running = true;

    Thread thread =
        new Thread(
            () -> {
              try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);

                while (running) {
                  int currentPlayers = currentPlayersSupplier.getAsInt();

                  String message =
                      DISCOVERY_PREFIX + hostIp + ":" + currentPlayers + ":" + maxPlayers;

                  byte[] data = message.getBytes(StandardCharsets.UTF_8);

                  DatagramPacket packet =
                      new DatagramPacket(
                          data,
                          data.length,
                          InetAddress.getByName("255.255.255.255"),
                          DISCOVERY_PORT);

                  socket.send(packet);
                  Thread.sleep(1000);
                }
              } catch (Exception e) {
                if (running) {
                  System.err.println("[LAN Discovery] Broadcast error: " + e.getMessage());
                }
              }
            },
            "LAN-Broadcast");

    thread.setDaemon(true);
    thread.start();
  }

  /// Listen for LAN servers.
  public void startListening(Consumer<DiscoveredServer> onServerFound) {
    stop();
    running = true;

    Thread thread =
        new Thread(
            () -> {
              try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT)) {
                listenerSocket = socket;
                socket.setBroadcast(true);

                byte[] buffer = new byte[1024];

                while (running) {
                  DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                  socket.receive(packet);

                  String message =
                      new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

                  if (message.startsWith(DISCOVERY_PREFIX)) {
                    String payload = message.substring(DISCOVERY_PREFIX.length());
                    String[] parts = payload.split(":");

                    if (parts.length >= 3) {
                      String ip = parts[0];
                      int currentPlayers = Integer.parseInt(parts[1]);
                      int maxPlayers = Integer.parseInt(parts[2]);

                      onServerFound.accept(new DiscoveredServer(ip, currentPlayers, maxPlayers));
                    }
                  }
                }
              } catch (Exception e) {
                if (running) {
                  System.err.println("[LAN Discovery] Listener error: " + e.getMessage());
                }
              }
            },
            "LAN-Listener");

    thread.setDaemon(true);
    thread.start();
  }

  public void stop() {
    running = false;

    if (listenerSocket != null && !listenerSocket.isClosed()) {
      listenerSocket.close();
      listenerSocket = null;
    }
  }
}
