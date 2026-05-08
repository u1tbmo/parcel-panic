package parcelpanic.view.sprites;

import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import parcelpanic.shared.ParcelState;

public class ParcelSprite extends Rectangle {
    public ParcelSprite() {
        super(20, 20, Color.BROWN);
    }

    public void render(ParcelState parcel) {
        setTranslateX(parcel.x());
        setTranslateY(parcel.y());

        if (parcel.isDamaged()) {
            setFill(Color.RED);
        } else {
            setFill(Color.BROWN);
        }
    }
}