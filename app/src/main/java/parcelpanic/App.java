package parcelpanic;

import javafx.application.Application;
import javafx.stage.Stage;
import parcelpanic.runtime.Bootstrap;

public class App extends Application {

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void start(Stage primaryStage) {
    Bootstrap bootstrap = new Bootstrap(primaryStage);
    bootstrap.initialize();
    bootstrap.launch();
  }
}
