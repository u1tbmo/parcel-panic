package parcelpanic.net;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/// Handles lightweight LAN server discovery using UDP broadcast.
public final class LanDiscoveryService {
  private static final int DISCOVERY_PORT = 5556;
  private static final String DISCOVERY_PREFIX = "PARCELPANIC_SERVER:";

  private volatile boolean running = false;
  private DatagramSocket listenerSocket;

  /// Start broadcasting this host as an available LAN server.
  public void startBroadcasting(String hostIp) {
    running = true;

    Thread thread =
        new Thread(
            () -> {
              try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);

                while (running) {
                  String message = DISCOVERY_PREFIX + hostIp;
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
  public void startListening(Consumer<String> onServerFound) {
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
                    String ip = message.substring(DISCOVERY_PREFIX.length());
                    onServerFound.accept(ip);
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
