package parcelpanic;

import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import parcelpanic.runtime.Bootstrap;

public class App extends Application {

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void start(Stage primaryStage) {
    Image icon = new Image(App.class.getResourceAsStream("/assets/entities/parcel.png"));
    primaryStage.getIcons().add(icon);
    Bootstrap bootstrap = new Bootstrap(primaryStage);
    bootstrap.initialize();
    bootstrap.launch();
  }
}
