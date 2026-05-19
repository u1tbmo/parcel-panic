package parcelpanic.view;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import parcelpanic.shared.GameState;

public class HUD extends VBox {
  private final Label timerLabel = new Label();
  private final Label unhappinessLabel = new Label();

  public HUD() {
    getChildren().addAll(timerLabel, unhappinessLabel);
  }

  public void render(GameState state) {
    timerLabel.setText("Time: " + (int) state.matchTimer());
    unhappinessLabel.setText("Unhappiness: " + (int) state.unhappiness() + "%");
  }
}
