package parcelpanic.view.sprites;

import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import parcelpanic.shared.VehicleState;

public class VehicleSprite extends Rectangle {
  public VehicleSprite() {
    super(40, 25, Color.BLUE);
  }

  public void render(VehicleState vehicle) {
    setTranslateX(vehicle.x());
    setTranslateY(vehicle.y());
    setRotate(vehicle.rotation());
  }
}
