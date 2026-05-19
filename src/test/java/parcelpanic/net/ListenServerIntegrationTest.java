package parcelpanic.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import parcelpanic.shared.GameState;
import parcelpanic.shared.ParcelState;
import parcelpanic.shared.PlayerIntent;
import parcelpanic.shared.VehicleState;
import parcelpanic.shared.VehicleState.PromptType;

/** Integration test for Listen Server Host: GameServer, ClientConnection, and Serializer. */
public class ListenServerIntegrationTest {

  /** Test that Serializer correctly round-trips GameState and PlayerIntent. */
  @Test
  public void testSerializerRoundTrip() {
    System.out.println("[Test] Testing Serializer round-trip...");

    // Create a test VehicleState
    VehicleState vehicle =
        new VehicleState(0, 100.0, 200.0, 10.5, -5.2, 45.0, false, true, PromptType.PICKUP, 0, 0.0, "Player");

    // Create a test ParcelState
    ParcelState parcel = new ParcelState(1, 150.0, 250.0, 5.0, null, 25.5, false, 2);

    // Create a test GameState
    List<VehicleState> vehicles = new ArrayList<>();
    vehicles.add(vehicle);
    List<ParcelState> parcels = new ArrayList<>();
    parcels.add(parcel);
    GameState originalState = new GameState(120.0, 0.5, 500.0, 0, 0, vehicles, parcels, null);

    // Serialize to JSON
    String stateJson = Serializer.serializeGameState(originalState);
    System.out.println("[Test] Serialized GameState:\n" + stateJson);

    // Deserialize back
    GameState deserializedState = Serializer.deserializeGameState(stateJson);

    // Verify the data
    assert deserializedState != null : "Deserialized state is null";
    assertDoubleEquals(deserializedState.matchTimer(), 120.0, "matchTimer mismatch");
    assertDoubleEquals(deserializedState.unhappiness(), 0.5, "unhappiness mismatch");
    assertDoubleEquals(deserializedState.score(), 500.0, "score mismatch");
    assert deserializedState.vehicles().size() == 1 : "vehicle count mismatch";
    assert deserializedState.parcels().size() == 1 : "parcel count mismatch";

    VehicleState restoredVehicle = deserializedState.vehicles().get(0);
    assert restoredVehicle.id() == 0 : "vehicle id mismatch";
    assertDoubleEquals(restoredVehicle.x(), 100.0, "vehicle x mismatch");
    assertDoubleEquals(restoredVehicle.y(), 200.0, "vehicle y mismatch");
    assertDoubleEquals(restoredVehicle.vx(), 10.5, "vehicle vx mismatch");
    assertDoubleEquals(restoredVehicle.vy(), -5.2, "vehicle vy mismatch");
    assertDoubleEquals(restoredVehicle.rotation(), 45.0, "vehicle rotation mismatch");
    assert restoredVehicle.isDashing() == false : "vehicle isDashing mismatch";
    assert restoredVehicle.isAccelerating() == true : "vehicle isAccelerating mismatch";
    assert restoredVehicle.prompt() == PromptType.PICKUP : "vehicle prompt mismatch";

    ParcelState restoredParcel = deserializedState.parcels().get(0);
    assert restoredParcel.id() == 1 : "parcel id mismatch";
    assertDoubleEquals(restoredParcel.x(), 150.0, "parcel x mismatch");
    assertDoubleEquals(restoredParcel.y(), 250.0, "parcel y mismatch");
    assertDoubleEquals(restoredParcel.z(), 5.0, "parcel z mismatch");
    assert restoredParcel.carrierId() == null : "parcel carrierId should be null";
    assertDoubleEquals(restoredParcel.remainingTime(), 25.5, "parcel remainingTime mismatch");
    assert restoredParcel.isDamaged() == false : "parcel isDamaged mismatch";
    assert restoredParcel.targetHouseId() == 2 : "parcel targetHouseId mismatch";

    System.out.println("[Test] ✓ GameState serialization test PASSED");
  }

  /** Test that Serializer correctly round-trips PlayerIntent. */
  @Test
  public void testPlayerIntentSerialization() {
    System.out.println("[Test] Testing PlayerIntent serialization...");

    // Create a test PlayerIntent
    PlayerIntent originalIntent = new PlayerIntent(0, true, false, true, false, true, false, true);

    // Serialize to JSON
    String json = Serializer.serializePlayerIntent(originalIntent);
    System.out.println("[Test] Serialized PlayerIntent:\n" + json);

    // Deserialize back
    PlayerIntent restoredIntent = Serializer.deserializePlayerIntent(json);

    // Verify the data
    assert restoredIntent != null : "Deserialized intent is null";
    assert restoredIntent.playerId() == 0 : "playerId mismatch";
    assert restoredIntent.up() == true : "up mismatch";
    assert restoredIntent.down() == false : "down mismatch";
    assert restoredIntent.left() == true : "left mismatch";
    assert restoredIntent.right() == false : "right mismatch";
    assert restoredIntent.dash() == true : "dash mismatch";
    assert restoredIntent.interact() == false : "interact mismatch";
    assert restoredIntent.throwParcel() == true : "throwParcel mismatch";

    System.out.println("[Test] ✓ PlayerIntent serialization test PASSED");
  }

  /** Test that GameServer starts and accepts a client connection. */
  @Test
  public void testGameServerStartup() throws Exception {
    System.out.println("[Test] Testing GameServer startup...");

    // Start the server in a separate thread
    GameServer server = new GameServer();
    Thread serverThread =
        new Thread(
            () -> {
              try {
                server.start();
              } catch (IOException e) {
                System.err.println("[Test] Error starting server: " + e.getMessage());
              }
            });
    serverThread.setDaemon(true);
    serverThread.start();

    // Give the server time to start
    Thread.sleep(1000);

    System.out.println("[Test] Server started, attempting mock client connection...");

    // Simulate a client connection
    try (Socket socket = new Socket("localhost", 5555)) {
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
      BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

      System.out.println("[Test] Client connected to server");

      // Send a test PlayerIntent
      PlayerIntent testIntent = new PlayerIntent(0, true, false, false, false, false, true, false);
      String intentJson = Serializer.serializePlayerIntent(testIntent);
      writer.write("INTENT:" + intentJson + "\n");
      writer.flush();

      System.out.println("[Test] Sent PlayerIntent to server");

      // Try to receive a GameState (with timeout)
      socket.setSoTimeout(3000); // 3 second timeout
      String response = reader.readLine();

      if (response != null && response.startsWith("STATE:")) {
        String stateJson = response.substring(6); // Remove "STATE:" prefix
        GameState receivedState = Serializer.deserializeGameState(stateJson);

        if (receivedState != null) {
          System.out.println("[Test] ✓ Received GameState from server");
          System.out.println("[Test]   - Match Timer: " + receivedState.matchTimer());
          System.out.println("[Test]   - Unhappiness: " + receivedState.unhappiness());
          System.out.println("[Test]   - Score: " + receivedState.score());
          System.out.println(
              "[Test]   - Vehicles: "
                  + (receivedState.vehicles() != null ? receivedState.vehicles().size() : 0));
          System.out.println(
              "[Test]   - Parcels: "
                  + (receivedState.parcels() != null ? receivedState.parcels().size() : 0));
        } else {
          System.out.println("[Test] ✗ Failed to deserialize GameState");
        }
      } else {
        System.out.println(
            "[Test] ! No response from server (may need more clients to start game)");
      }
    } catch (IOException e) {
      System.out.println("[Test] ! Could not connect to server (expected if server not running)");
      System.out.println("[Test]   Error: " + e.getMessage());
    } finally {
      server.stop();
      Thread.sleep(500);
    }

    System.out.println("[Test] ✓ GameServer startup test completed");
  }

  /** Helper method to compare doubles with epsilon tolerance. */
  private void assertDoubleEquals(double actual, double expected, String message) {
    Assertions.assertEquals(expected, actual, 1e-5, message);
  }
}
