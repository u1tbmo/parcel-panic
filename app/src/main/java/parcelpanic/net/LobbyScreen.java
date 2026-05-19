package parcelpanic.net;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import parcelpanic.input.InputAction;
import parcelpanic.input.InputHintProvider;
import parcelpanic.media.AssetKeys.AudioKey;
import parcelpanic.media.AssetKeys.ColorKey;
import parcelpanic.media.AssetKeys.FontKey;
import parcelpanic.media.UiFactory;
import parcelpanic.screen.ContentScreen;
import parcelpanic.screens.MatchScreen;
import parcelpanic.util.SmoothedValue;
import parcelpanic.video.VideoManager;

/// A premium, fully keyboard-navigable Lobby UI that matches the home screen selection style.
public final class LobbyScreen extends ContentScreen {
  private enum Mode {
    CHOOSE,
    HOSTING,
    JOINING
  }

  private enum ChooseItem {
    HOST("Host Game"),
    JOIN("Join Game"),
    BACK("Back to Main Menu");

    private final String text;

    ChooseItem(String text) {
      this.text = text;
    }

    public String getText() {
      return text;
    }
  }

  private enum HostingItem {
    START_MATCH("Start Match"),
    CANCEL_HOST("Cancel Host");

    private final String text;

    HostingItem(String text) {
      this.text = text;
    }

    public String getText() {
      return text;
    }
  }

  private enum JoiningItem {
    BACK("Back");

    private final String text;

    JoiningItem(String text) {
      this.text = text;
    }

    public String getText() {
      return text;
    }
  }

  private Mode currentMode = Mode.CHOOSE;
  private int selectedIndex = 0;

  private final List<Label> itemLabels = new ArrayList<>();
  private final Map<Integer, SmoothedValue> offsets = new HashMap<>();

  private GameServer server;
  private java.net.Socket activeSocket;
  private int lastPlayerCount = -1;
  private boolean ipFieldFocused = true;
  private TextField ipField;
  private Label statusLabel;
  private Label ipInfoLabel;
  private BorderPane rootPane;
  private String resolvedIp = "Detecting...";

  private Color textColor;
  private Color mutedColor;
  private Color selectedColor;
  private Color surfaceBlack;

  private boolean socketHandedOffToGame = false;

  private final LanDiscoveryService lanDiscovery = new LanDiscoveryService();
  private VBox discoveredServersBox;

  @Override
  protected VideoManager.ViewportMode viewportMode() {
    return VideoManager.ViewportMode.FULL_WINDOW;
  }

  @Override
  public void fixedUpdate(double dtSeconds) {
    if (currentMode == Mode.HOSTING && server != null && statusLabel != null) {
      int count = server.getConnectedCount();
      if (count != lastPlayerCount) {
        lastPlayerCount = count;
        Platform.runLater(
            () -> {
              if (statusLabel != null && currentMode == Mode.HOSTING) {
                statusLabel.setText("Players Connected: " + count + " / 4");
              }
            });
      }
    }

    int itemCount = getItemCount();
    for (int i = 0; i < itemCount; i++) {
      double target = (i == selectedIndex) ? 12.0 : 0.0;
      SmoothedValue offset = offsets.get(i);
      if (offset != null) {
        offset.update(target);
      }
    }
  }

  @Override
  public void render(double alpha) {
    updateSelectionColors();

    int itemCount = getItemCount();
    for (int i = 0; i < itemCount; i++) {
      if (i < itemLabels.size()) {
        Label label = itemLabels.get(i);
        SmoothedValue offset = offsets.get(i);
        if (label != null && offset != null) {
          label.setTranslateX(offset.get(alpha));
        }
      }
    }
  }

  @Override
  protected void onBeforeBuild() {
    this.textColor = ctx.assets().getColor(ColorKey.TEXT_LIGHT);
    this.mutedColor = ctx.assets().getColor(ColorKey.TEXT_MUTED);
    this.selectedColor = ctx.assets().getColor(ColorKey.SUCCESS);
    this.surfaceBlack = ctx.assets().getColor(ColorKey.SURFACE_BLACK);

    this.rootPane =
        UiFactory.createBorderPane(VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT);
    this.rootPane.setFocusTraversable(true);
    this.rootPane.setBackground(
        UiFactory.createBackground(
                surfaceBlack, VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT)
            .getBackground());

    resolveIpAddressAsync();
    setMode(Mode.CHOOSE);
  }

  private void resolveIpAddressAsync() {
    new Thread(
            () -> {
              String ip = getLocalIpAddress();
              Platform.runLater(
                  () -> {
                    resolvedIp = ip;
                    if (ipInfoLabel != null) {
                      ipInfoLabel.setText("Share your IP with joiners: " + resolvedIp);
                    }
                  });
            },
            "IP-Resolver")
        .start();
  }

  @Override
  protected Node createContent() {
    buildUI();
    return rootPane;
  }

  private void setMode(Mode newMode) {
    this.currentMode = newMode;
    this.selectedIndex = 0;
    this.ipFieldFocused = (newMode == Mode.JOINING);
    this.lastPlayerCount = -1;
    this.offsets.clear();

    if (newMode == Mode.JOINING) {
      startLanListening();
    } else {
      lanDiscovery.stop();
    }

    int itemCount = getItemCount();
    for (int i = 0; i < itemCount; i++) {
      this.offsets.put(i, new SmoothedValue(0.0, 0.5));
    }

    if (rootPane != null) {
      Platform.runLater(this::buildUI);
    }
  }

  private int getItemCount() {
    return switch (currentMode) {
      case CHOOSE -> ChooseItem.values().length;
      case HOSTING -> HostingItem.values().length;
      case JOINING -> JoiningItem.values().length;
    };
  }

  /// Scans active network interfaces to find the host's actual local IPv4 address
  private String getLocalIpAddress() {
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        NetworkInterface iface = interfaces.nextElement();
        if (iface.isLoopback() || !iface.isUp()) continue;

        Enumeration<InetAddress> addresses = iface.getInetAddresses();
        while (addresses.hasMoreElements()) {
          InetAddress addr = addresses.nextElement();
          if (addr instanceof Inet4Address) {
            return addr.getHostAddress();
          }
        }
      }
    } catch (Exception ignored) {
    }
    try {
      return InetAddress.getLocalHost().getHostAddress();
    } catch (Exception e) {
      return "localhost";
    }
  }

  private void startLanListening() {
    lanDiscovery.startListening(
        ip ->
            Platform.runLater(
                () -> {
                  if (currentMode != Mode.JOINING || discoveredServersBox == null) {
                    return;
                  }

                  boolean alreadyExists =
                      discoveredServersBox.getChildren().stream()
                          .anyMatch(
                              node -> node.getUserData() != null && node.getUserData().equals(ip));

                  if (alreadyExists) {
                    return;
                  }

                  Button joinButton = new Button("Join " + ip);
                  joinButton.setUserData(ip);
                  joinButton.setStyle("-fx-font-size: 16px; -fx-padding: 8px 18px;");

                  joinButton.setOnAction(
                      e -> {
                        if (ipField != null) {
                          ipField.setText(ip);
                        }
                        connectToHost(ip);
                      });

                  discoveredServersBox.getChildren().add(joinButton);
                }));
  }

  private void buildUI() {
    if (rootPane == null) {
      return;
    }
    rootPane.setTop(null);
    rootPane.setCenter(null);
    rootPane.setBottom(null);

    Font titleFont = ctx.assets().getFont(FontKey.DISPLAY);
    Font labelFont = ctx.assets().getFont(FontKey.TITLE);
    Font menuFont = ctx.assets().getFont(FontKey.TITLE);

    itemLabels.clear();

    Label titleLabel;
    if (currentMode == Mode.CHOOSE) {
      titleLabel = UiFactory.createTitle("Multiplayer Lobby", titleFont, textColor);
    } else if (currentMode == Mode.HOSTING) {
      titleLabel = UiFactory.createTitle("Hosting Game", titleFont, textColor);
    } else {
      titleLabel = UiFactory.createTitle("Join Game", titleFont, textColor);
    }

    VBox topContainer = new VBox(titleLabel);
    topContainer.setAlignment(Pos.TOP_CENTER);
    topContainer.setPadding(new Insets(80, 0, 0, 0));

    VBox menuContainer = new VBox(20);
    menuContainer.setAlignment(Pos.CENTER_LEFT);
    menuContainer.setPadding(new Insets(0, 0, 0, 100));

    if (currentMode == Mode.CHOOSE) {
      Label desc =
          UiFactory.createLabel("Select your role to start or join a match", labelFont, textColor);
      desc.setPadding(new Insets(0, 0, 20, 0));
      menuContainer.getChildren().add(desc);

      for (ChooseItem item : ChooseItem.values()) {
        Label label = UiFactory.createLabel(item.getText(), menuFont, textColor);
        itemLabels.add(label);
        menuContainer.getChildren().add(label);
      }

    } else if (currentMode == Mode.HOSTING) {
      statusLabel = UiFactory.createLabel("Starting local server...", labelFont, textColor);
      ipInfoLabel =
          UiFactory.createLabel(
              "Share your IP with joiners: " + resolvedIp, labelFont, selectedColor);

      VBox infoBox = new VBox(10, statusLabel, ipInfoLabel);
      infoBox.setPadding(new Insets(0, 0, 20, 0));
      menuContainer.getChildren().add(infoBox);

      for (HostingItem item : HostingItem.values()) {
        Label label = UiFactory.createLabel(item.getText(), menuFont, textColor);
        itemLabels.add(label);
        menuContainer.getChildren().add(label);
      }

    } else if (currentMode == Mode.JOINING) {
      statusLabel =
          UiFactory.createLabel(
              "Enter Host IP, or select a discovered LAN server", labelFont, textColor);

      ipField = new TextField("localhost");
      ipField.setMaxWidth(300);
      ipField.setStyle("-fx-font-size: 16px; -fx-alignment: center;");
      ipField
          .focusedProperty()
          .addListener(
              (obs, oldVal, newVal) -> {
                ipFieldFocused = newVal;
                updateSelectionColors();
              });
      ipField.setOnKeyPressed(
          event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
              event.consume();
              connectToHost(ipField.getText());
            } else if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
              event.consume();
              handleBack();
            } else if (event.getCode() == javafx.scene.input.KeyCode.DOWN) {
              event.consume();
              if (rootPane != null) {
                rootPane.requestFocus();
              }
            }
          });
      Platform.runLater(() -> ipField.requestFocus());

      VBox fieldBox = new VBox(15, statusLabel, ipField);
      fieldBox.setAlignment(Pos.CENTER);
      fieldBox.setPadding(new Insets(0, 0, 20, 0));

      Label lanLabel = UiFactory.createLabel("LAN Servers", labelFont, selectedColor);

      discoveredServersBox = new VBox(10);
      discoveredServersBox.setAlignment(Pos.CENTER);

      menuContainer.getChildren().addAll(fieldBox, lanLabel, discoveredServersBox);

      for (JoiningItem item : JoiningItem.values()) {
        Label label = UiFactory.createLabel(item.getText(), menuFont, textColor);
        itemLabels.add(label);
        menuContainer.getChildren().add(label);
      }
    }

    Font hintFont = ctx.assets().getFont(FontKey.LABEL);
    Font iconFont = ctx.assets().getFont(FontKey.HINT);
    Color hintColor = ctx.assets().getColor(ColorKey.TEXT_HINT);

    Node navigateHint =
        UiFactory.createHint(
            InputHintProvider.getIcon(KeyCode.UP) + InputHintProvider.getIcon(KeyCode.DOWN),
            "Navigate",
            iconFont,
            hintFont,
            hintColor);

    Node selectHint =
        UiFactory.createHint(
            InputHintProvider.getIconForAction(InputAction.CONFIRM, ctx.settings().controls()),
            "Select",
            iconFont,
            hintFont,
            hintColor);

    HBox hintsRow = new HBox(40, navigateHint, selectHint);
    hintsRow.setAlignment(Pos.CENTER);

    VBox bottomContainer = new VBox(20, hintsRow);
    bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
    bottomContainer.setPadding(new Insets(0, 0, 60, 0));

    rootPane.setTop(topContainer);
    rootPane.setCenter(menuContainer);
    rootPane.setBottom(bottomContainer);

    updateSelectionColors();
    Platform.runLater(() -> rootPane.requestFocus());
  }

  private void updateSelectionColors() {
    int itemCount = getItemCount();
    for (int i = 0; i < itemCount; i++) {
      if (i < itemLabels.size()) {
        Label label = itemLabels.get(i);
        if (label != null) {
          boolean highlighted = false;
          if (currentMode == Mode.JOINING) {
            highlighted = !ipFieldFocused;
          } else {
            highlighted = (i == selectedIndex);
          }
          label.setTextFill(highlighted ? selectedColor : mutedColor);
        }
      }
    }
  }

  private void startHosting() {
    setMode(Mode.HOSTING);

    new Thread(
            () -> {
              try {
                server = new GameServer();
                server.start();
                lanDiscovery.startBroadcasting(resolvedIp);

                Platform.runLater(
                    () -> {
                      if (statusLabel != null) {
                        statusLabel.setText("Server started. Connecting local host player...");
                      }
                    });

                connectToHost("localhost");
              } catch (Exception e) {
                System.err.println(
                    "[LobbyScreen] GameServer starter thread encountered exception:");
                e.printStackTrace();
                Platform.runLater(
                    () -> {
                      if (statusLabel != null) {
                        statusLabel.setText("Failed to start server: " + e.getMessage());
                      }
                      if (server != null) {
                        server.stop();
                        server = null;
                      }
                    });
              }
            },
            "GameServer-Starter")
        .start();
  }

  private void closeActiveSocket() {
    if (activeSocket != null) {
      try {
        activeSocket.close();
      } catch (Exception ignored) {
      }
      activeSocket = null;
    }
  }

  private void stopHosting() {
    closeActiveSocket();
    if (server != null) {
      server.stop();
      server = null;
    }
    lanDiscovery.stop();
    setMode(Mode.CHOOSE);
  }

  private void connectToHost(String ip) {
    Platform.runLater(
        () -> {
          if (statusLabel != null) {
            statusLabel.setText("Connecting to " + ip + "...");
          }
          if (ipField != null) {
            ipField.setDisable(true);
          }
        });

    new Thread(
            () -> {
              Socket socket = null;
              try {
                socket = new Socket(ip, 5555);
                this.activeSocket = socket;
                BufferedReader reader =
                    new BufferedReader(new InputStreamReader(socket.getInputStream()));

                Platform.runLater(
                    () -> {
                      if (statusLabel != null) {
                        if (server != null) {
                          statusLabel.setText(
                              "Lobby Live! Ask players to connect to: " + resolvedIp);
                        } else {
                          statusLabel.setText("Connected! Waiting for host to start...");
                        }
                      }
                    });

                int playerId = -1;
                String line;
                while ((line = reader.readLine()) != null) {
                  if (line.startsWith("WELCOME:")) {
                    playerId = Integer.parseInt(line.substring(8));
                  } else if (line.equals("START")) {
                    final int finalPlayerId = playerId;
                    final Socket finalSocket = socket;

                    socketHandedOffToGame = true;
                    activeSocket = null;
                    lanDiscovery.stop();

                    Platform.runLater(
                        () -> {
                          try {
                            GameClient client = new GameClient();
                            client.startWithSocket(finalSocket, finalPlayerId);
                            ctx.navigator().requestSwitch(new MatchScreen(client));
                          } catch (Exception ex) {
                            if (statusLabel != null) {
                              statusLabel.setText("Failed to start client: " + ex.getMessage());
                            }
                            if (ipField != null) ipField.setDisable(false);
                          }
                        });
                    return;
                  }
                }

                if (socket != null) {
                  socket.close();
                }
                Platform.runLater(
                    () -> {
                      if (statusLabel != null) {
                        statusLabel.setText("Connection closed by server.");
                      }
                      if (ipField != null) ipField.setDisable(false);
                    });
              } catch (Exception e) {
                System.err.println("[LobbyScreen] Lobby-Connector thread encountered exception:");
                e.printStackTrace();
                try {
                  if (socket != null) {
                    socket.close();
                  }
                } catch (Exception ignored) {
                }
                Platform.runLater(
                    () -> {
                      if (statusLabel != null) {
                        statusLabel.setText("Connection failed: " + e.getMessage());
                      }
                      if (ipField != null) ipField.setDisable(false);
                    });
              }
            },
            "Lobby-Connector")
        .start();
  }

  private void handleConfirm() {
    if (currentMode == Mode.CHOOSE) {
      ChooseItem item = ChooseItem.values()[selectedIndex];
      switch (item) {
        case HOST -> startHosting();
        case JOIN -> setMode(Mode.JOINING);
        case BACK -> ctx.navigator().requestSwitch(new parcelpanic.screens.MenuScreen());
      }
    } else if (currentMode == Mode.HOSTING) {
      HostingItem item = HostingItem.values()[selectedIndex];
      switch (item) {
        case START_MATCH -> {
          if (server != null) {
            server.startMatch();
          }
        }
        case CANCEL_HOST -> stopHosting();
      }
    } else if (currentMode == Mode.JOINING) {
      JoiningItem item = JoiningItem.values()[selectedIndex];
      switch (item) {
        case BACK -> {
          closeActiveSocket();
          setMode(Mode.CHOOSE);
        }
      }
    }
  }

  private void handleBack() {
    if (currentMode == Mode.CHOOSE) {
      ctx.navigator().requestSwitch(new parcelpanic.screens.MenuScreen());
    } else if (currentMode == Mode.HOSTING) {
      stopHosting();
    } else if (currentMode == Mode.JOINING) {
      closeActiveSocket();
      setMode(Mode.CHOOSE);
    }
  }

  @Override
  public boolean supportsInputRepeat() {
    return true;
  }

  @Override
  public void onKeyPressed(InputAction action) {
    switch (action) {
      case UI_UP -> {
        if (currentMode == Mode.JOINING && ipField != null) {
          ipFieldFocused = true;
          updateSelectionColors();
          ipField.requestFocus();
        } else {
          int count = getItemCount();
          selectedIndex = (selectedIndex - 1 + count) % count;
          ctx.audio().playSound(AudioKey.MOVE_SELECTION);
        }
      }
      case UI_DOWN -> {
        int count = getItemCount();
        selectedIndex = (selectedIndex + 1) % count;
        ctx.audio().playSound(AudioKey.MOVE_SELECTION);
      }
      case CONFIRM -> {
        ctx.audio().playSound(AudioKey.CLICK);
        handleConfirm();
      }
      case BACK -> {
        handleBack();
      }
      default -> {}
    }
  }

  @Override
  protected void onBeforeExit() {
    lanDiscovery.stop();

    if (!socketHandedOffToGame) {
      closeActiveSocket();

      if (server != null) {
        server.stop();
        server = null;
      }
    }
  }
}