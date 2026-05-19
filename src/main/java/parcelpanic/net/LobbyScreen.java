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
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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
    JOINING,
    LOBBY
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
    ENTER_IP("Enter Host IP"),
    BACK("Back");

    private final String text;

    JoiningItem(String text) {
      this.text = text;
    }

    public String getText() {
      return text;
    }
  }

  private enum LobbyItem {
    LEAVE("Leave Lobby");

    private final String text;

    LobbyItem(String text) {
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
  private volatile boolean connectingToHost = false;
  private int lastPlayerCount = -1;
  private Label statusLabel;
  private Label ipInfoLabel;
  private BorderPane rootPane;
  private VBox menuContainer;
  private String manualHostIp = "localhost";
  private String resolvedIp = "Detecting...";

  private Color textColor;
  private Color mutedColor;
  private Color selectedColor;
  private Color surfaceBlack;

  private boolean socketHandedOffToGame = false;

  private final LanDiscoveryService lanDiscovery = new LanDiscoveryService();
  private VBox discoveredServersBox;
  private final List<String> discoveredServers = new ArrayList<>();
  private final List<Label> discoveredServerLabels = new ArrayList<>();
  private Label enterIpLabel;
  private Label joinBackLabel;

  // Lobby Chat UI
  private VBox chatPanel;
  private VBox chatMessagesContainer;
  private ScrollPane chatScrollPane;
  private TextField chatInputField;

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
                      ipInfoLabel.setText("IP: " + resolvedIp);
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
    this.lastPlayerCount = -1;
    this.offsets.clear();

    if (newMode == Mode.JOINING) {
      this.discoveredServers.clear();
      this.discoveredServerLabels.clear();
      this.enterIpLabel = null;
      this.joinBackLabel = null;
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
      case JOINING -> discoveredServers.size() + JoiningItem.values().length;
      case LOBBY -> LobbyItem.values().length;
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

                  if (discoveredServers.contains(ip)) {
                    return;
                  }

                  discoveredServers.add(ip);
                  addDiscoveredServerLabel(ip);
                  rebuildJoinItemLabels();
                }));
  }

  private void addDiscoveredServerLabel(String ip) {
    Font menuFont = ctx.assets().getFont(FontKey.TITLE);
    Label label = UiFactory.createLabel(ip, menuFont, mutedColor);
    label.setUserData(ip);
    label.setOnMouseClicked(
        e -> {
          manualHostIp = ip;
          connectToHost(ip);
        });
    discoveredServerLabels.add(label);
    discoveredServersBox.getChildren().add(label);
  }

  private void rebuildJoinItemLabels() {
    itemLabels.clear();
    itemLabels.addAll(discoveredServerLabels);
    if (enterIpLabel != null) {
      itemLabels.add(enterIpLabel);
    }
    if (joinBackLabel != null) {
      itemLabels.add(joinBackLabel);
    }
    int itemCount = getItemCount();

    // Ensure we have enough offsets without clearing existing ones
    for (int i = 0; i < itemCount; i++) {
      offsets.putIfAbsent(i, new SmoothedValue(0.0, 0.5));
    }

    if (selectedIndex >= itemCount) {
      selectedIndex = Math.max(0, itemCount - 1);
    }
    updateSelectionColors();
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
    } else if (currentMode == Mode.JOINING) {
      titleLabel = UiFactory.createTitle("Join Game", titleFont, textColor);
    } else {
      titleLabel = UiFactory.createTitle("Lobby", titleFont, textColor);
    }

    VBox topContainer = new VBox(titleLabel);
    topContainer.setAlignment(Pos.TOP_CENTER);
    topContainer.setPadding(new Insets(80, 0, 0, 0));

    VBox menuContainer = new VBox(20);
    menuContainer.setAlignment(Pos.CENTER_LEFT);
    menuContainer.setPadding(new Insets(0, 0, 0, 100));
    this.menuContainer = menuContainer;

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
              "IP: " + resolvedIp, labelFont, selectedColor);

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

      VBox fieldBox = new VBox(15, statusLabel);
      fieldBox.setAlignment(Pos.CENTER);
      fieldBox.setPadding(new Insets(0, 0, 20, 0));

      Label lanLabel = UiFactory.createLabel("LAN Servers", labelFont, selectedColor);

      discoveredServersBox = new VBox(10);
      discoveredServersBox.setPadding(new Insets(0, 0, 0, 20));
      discoveredServersBox.setAlignment(Pos.CENTER_LEFT);

      discoveredServerLabels.clear();
      for (String ip : discoveredServers) {
        addDiscoveredServerLabel(ip);
      }

      menuContainer.getChildren().addAll(fieldBox, lanLabel, discoveredServersBox);

      enterIpLabel = UiFactory.createLabel(JoiningItem.ENTER_IP.getText(), menuFont, textColor);
      menuContainer.getChildren().add(enterIpLabel);

      joinBackLabel = UiFactory.createLabel(JoiningItem.BACK.getText(), menuFont, textColor);
      menuContainer.getChildren().add(joinBackLabel);

      rebuildJoinItemLabels();
    } else if (currentMode == Mode.LOBBY) {
      statusLabel =
          UiFactory.createLabel(
              "Connected! Waiting for host to start the match...", labelFont, textColor);
      VBox statusBox = new VBox(12, statusLabel);
      statusBox.setAlignment(Pos.CENTER_LEFT);
      menuContainer.getChildren().add(statusBox);

      for (LobbyItem item : LobbyItem.values()) {
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

    if (currentMode == Mode.HOSTING || currentMode == Mode.LOBBY) {
      Node chatHint =
          UiFactory.createHint(
              InputHintProvider.getIcon(KeyCode.T),
              "Chat",
              iconFont,
              hintFont,
              hintColor);
      hintsRow.getChildren().add(chatHint);
    }

    VBox bottomContainer = new VBox(20, hintsRow);
    bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
    bottomContainer.setPadding(new Insets(0, 0, 60, 0));

    rootPane.setTop(topContainer);

    if (currentMode == Mode.HOSTING || currentMode == Mode.LOBBY) {
      VBox chatSide = buildChatPanel();
      HBox splitBox = new HBox(40);
      splitBox.setAlignment(Pos.CENTER);
      splitBox.setPadding(new Insets(0, 50, 0, 50));
      menuContainer.setPrefWidth(700);
      chatSide.setPrefWidth(430);
      splitBox.getChildren().addAll(menuContainer, chatSide);
      rootPane.setCenter(splitBox);
    } else {
      rootPane.setCenter(menuContainer);
    }

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
          boolean highlighted = (i == selectedIndex);
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
    connectingToHost = false;
    if (server != null) {
      server.stop();
      server = null;
    }
    lanDiscovery.stop();
    setMode(Mode.CHOOSE);
  }

  private void connectToHost(String ip) {
    if (connectingToHost) {
      return;
    }
    connectingToHost = true;
    Platform.runLater(
        () -> {
          if (statusLabel != null) {
            statusLabel.setText("Connecting to " + ip + "...");
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
                      if (server != null) {
                        buildUI();
                        if (statusLabel != null) {
                          statusLabel.setText(
                              "Lobby Live! Ask players to connect to: " + resolvedIp);
                        }
                      } else {
                        setMode(Mode.LOBBY);
                      }
                    });

                int playerId = -1;
                String line;
                while ((line = reader.readLine()) != null) {
                  if (line.startsWith("WELCOME:")) {
                    playerId = Integer.parseInt(line.substring(8));
                  } else if (line.startsWith("CHAT:")) {
                    String chatMsg = line.substring(5);
                    Platform.runLater(() -> addChatMessage(chatMsg));
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
                      closeActiveSocket();
                      connectingToHost = false;
                      if (server == null) {
                        ctx.navigator().push(new parcelpanic.screens.KickOverlay("Host Exited"));
                      }
                      setMode(Mode.CHOOSE);
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
                      boolean wasConnected = (activeSocket != null);
                      closeActiveSocket();
                      connectingToHost = false;
                      if (wasConnected && server == null) {
                        ctx.navigator().push(new parcelpanic.screens.KickOverlay("Host Exited"));
                      } else {
                        if (statusLabel != null) {
                          statusLabel.setText("Connection failed: " + e.getMessage());
                        }
                      }
                      if (server == null) {
                        setMode(Mode.CHOOSE);
                      }
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
      if (selectedIndex < discoveredServers.size()) {
        String ip = discoveredServers.get(selectedIndex);
        manualHostIp = ip;
        connectToHost(ip);
        return;
      }

      if (selectedIndex == discoveredServers.size()) {
        openManualIpOverlay();
        return;
      }

      closeActiveSocket();
      setMode(Mode.CHOOSE);
    } else if (currentMode == Mode.LOBBY) {
      LobbyItem item = LobbyItem.values()[selectedIndex];
      switch (item) {
        case LEAVE -> {
          closeActiveSocket();
          setMode(Mode.CHOOSE);
        }
      }
    }
  }

  private void openManualIpOverlay() {
    ctx.navigator().push(
        new HostIpOverlay(
            manualHostIp,
            ip -> {
              manualHostIp = ip;
              connectToHost(ip);
            }));
  }

  private final class HostIpOverlay implements parcelpanic.screen.Screen {
    private final String initialIp;
    private final java.util.function.Consumer<String> onConfirm;
    private BorderPane overlayRoot;
    private TextField ipField;

    private HostIpOverlay(String initialIp, java.util.function.Consumer<String> onConfirm) {
      this.initialIp = initialIp;
      this.onConfirm = onConfirm;
    }

    @Override
    public void enter(parcelpanic.runtime.AppContext ctx) {
      Color surface = ctx.assets().getColor(ColorKey.SURFACE_BLACK);
      Color text = ctx.assets().getColor(ColorKey.TEXT_LIGHT);
      Color muted = ctx.assets().getColor(ColorKey.TEXT_MUTED);
      Color accent = ctx.assets().getColor(ColorKey.SUCCESS);

      overlayRoot =
          UiFactory.createBorderPane(VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT);
      overlayRoot.setBackground(
          UiFactory.createOverlay(surface, 0.88, VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT)
              .getBackground());

      Font titleFont = ctx.assets().getFont(FontKey.DISPLAY);
      Font labelFont = ctx.assets().getFont(FontKey.TITLE);

      Label title = UiFactory.createTitle("Enter Host IP", titleFont, text);
      VBox topContainer = new VBox(title);
      topContainer.setAlignment(Pos.TOP_CENTER);
      topContainer.setPadding(new Insets(80, 0, 0, 0));

      Label helper = UiFactory.createLabel("Type the host IP and press Enter", labelFont, muted);
      ipField = new TextField(initialIp);
      ipField.setPromptText("e.g. 192.168.1.10");
      ipField.setMaxWidth(320);
      ipField.setStyle(
          String.format(
              "-fx-font-size: 18px; -fx-alignment: center; -fx-text-fill: %s;"
                  + " -fx-prompt-text-fill: %s; -fx-control-inner-background: %s;"
                  + " -fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 2px;"
                  + " -fx-background-radius: 10px; -fx-border-radius: 10px;"
                  + " -fx-padding: 8px 12px;",
              toCssColor(text),
              toCssColor(muted),
              toCssColor(surface, 0.85),
              toCssColor(surface, 0.92),
              toCssColor(accent)));
      ipField.setOnKeyPressed(
          event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
              event.consume();
              confirm();
            } else if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
              event.consume();
              cancel();
            }
          });

      VBox center = new VBox(16, helper, ipField);
      center.setAlignment(Pos.CENTER);

      Font hintFont = ctx.assets().getFont(FontKey.LABEL);
      Font iconFont = ctx.assets().getFont(FontKey.HINT);
      Color hintColor = ctx.assets().getColor(ColorKey.TEXT_HINT);

      Node enterHint =
          UiFactory.createHint(
              InputHintProvider.getIcon(KeyCode.ENTER), "Confirm", iconFont, hintFont, hintColor);
      Node escHint =
          UiFactory.createHint(
              InputHintProvider.getIcon(KeyCode.ESCAPE), "Cancel", iconFont, hintFont, hintColor);
      HBox hintsRow = new HBox(40, enterHint, escHint);
      hintsRow.setAlignment(Pos.CENTER);
      VBox bottomContainer = new VBox(20, hintsRow);
      bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
      bottomContainer.setPadding(new Insets(0, 0, 60, 0));

      overlayRoot.setTop(topContainer);
      overlayRoot.setCenter(center);
      overlayRoot.setBottom(bottomContainer);
      overlayRoot.setFocusTraversable(true);

      Platform.runLater(() -> ipField.requestFocus());
    }

    @Override
    public void exit() {
      overlayRoot = null;
      ipField = null;
    }

    @Override
    public Node getRoot() {
      return overlayRoot;
    }

    @Override
    public void fixedUpdate(double dtSeconds) {}

    @Override
    public void render(double alpha) {}

    @Override
    public void onKeyPressed(InputAction action) {}

    @Override
    public void onRawKeyPressed(KeyCode code) {}

    @Override
    public boolean suppressActionBindings() {
      return true;
    }

    private void confirm() {
      if (ipField != null) {
        String value = ipField.getText();
        onConfirm.accept(value == null ? "" : value.trim());
      }
      ctx.navigator().pop();
    }

    private void cancel() {
      ctx.navigator().pop();
    }
  }

  private static String toCssColor(Color color) {
    return toCssColor(color, color.getOpacity());
  }

  private static String toCssColor(Color color, double opacity) {
    int r = (int) Math.round(color.getRed() * 255.0);
    int g = (int) Math.round(color.getGreen() * 255.0);
    int b = (int) Math.round(color.getBlue() * 255.0);
    double a = Math.max(0.0, Math.min(1.0, opacity));
    return String.format("rgba(%d,%d,%d,%.2f)", r, g, b, a);
  }

  private void handleBack() {
    if (currentMode == Mode.CHOOSE) {
      ctx.navigator().requestSwitch(new parcelpanic.screens.MenuScreen());
    } else if (currentMode == Mode.HOSTING) {
      stopHosting();
    } else if (currentMode == Mode.JOINING || currentMode == Mode.LOBBY) {
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
        int count = getItemCount();
        selectedIndex = (selectedIndex - 1 + count) % count;
        ctx.audio().playSound(AudioKey.MOVE_SELECTION);
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
  public boolean suppressActionBindings() {
    return (chatInputField != null && chatInputField.isFocused()) || super.suppressActionBindings();
  }

  @Override
  public void onRawKeyPressed(KeyCode code) {
    if (code == KeyCode.T) {
      if (chatInputField != null && !chatInputField.isFocused()) {
        Platform.runLater(() -> chatInputField.requestFocus());
      }
    } else if (code == KeyCode.ESCAPE) {
      if (chatInputField != null && chatInputField.isFocused()) {
        Platform.runLater(() -> rootPane.requestFocus());
      }
    }
  }

  private VBox buildChatPanel() {
    chatPanel = new VBox(15);
    chatPanel.setPadding(new Insets(20));
    chatPanel.setAlignment(Pos.TOP_CENTER);
    chatPanel.setStyle(
        "-fx-background-color: rgba(30, 30, 30, 0.65);"
            + " -fx-background-radius: 12px;"
            + " -fx-border-color: rgba(255, 255, 255, 0.15);"
            + " -fx-border-radius: 12px;"
            + " -fx-border-width: 1px;");

    // Title
    Font titleFont = ctx.assets().getFont(FontKey.TITLE);
    Label chatTitle = UiFactory.createLabel("LOBBY CHAT", titleFont, selectedColor);
    chatTitle.setAlignment(Pos.CENTER);

    // Messages Container inside ScrollPane
    chatMessagesContainer = new VBox(8);
    chatMessagesContainer.setAlignment(Pos.TOP_LEFT);

    chatScrollPane = new ScrollPane(chatMessagesContainer);
    chatScrollPane.setFitToWidth(true);
    chatScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    chatScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    chatScrollPane.setPrefHeight(280);
    chatScrollPane.setStyle(
        "-fx-background: transparent; -fx-background-color: transparent; -fx-viewport-background-color: transparent;");

    // Input text field
    chatInputField = new TextField();
    chatInputField.setPromptText("Press Enter to send message...");
    chatInputField.setPrefWidth(380);
    chatInputField.setStyle(
        "-fx-background-color: rgba(0, 0, 0, 0.5);"
            + " -fx-text-fill: white;"
            + " -fx-prompt-text-fill: #888888;"
            + " -fx-border-color: rgba(255, 255, 255, 0.1);"
            + " -fx-border-radius: 6px;"
            + " -fx-background-radius: 6px;"
            + " -fx-padding: 8px 12px;");

    chatInputField.setOnAction(e -> sendChat());

    // Prevent navigation keys from bubbling when typing in chat
    chatInputField.setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.ESCAPE) {
            rootPane.requestFocus();
            event.consume();
          }
        });

    chatPanel.getChildren().addAll(chatTitle, chatScrollPane, chatInputField);
    return chatPanel;
  }

  private void addChatMessage(String msg) {
    if (chatMessagesContainer == null || chatScrollPane == null) return;

    Font chatFont = ctx.assets().getFont(FontKey.LABEL);
    Label messageLabel = new Label(msg);
    messageLabel.setFont(chatFont);
    messageLabel.setTextFill(textColor);
    messageLabel.setWrapText(true);
    messageLabel.setMaxWidth(380);

    chatMessagesContainer.getChildren().add(messageLabel);

    // Auto scroll to bottom
    Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
  }

  private void sendChat() {
    if (chatInputField == null || activeSocket == null) return;
    String text = chatInputField.getText().trim();
    if (text.isEmpty()) return;

    new Thread(() -> {
      try {
        java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(activeSocket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
        writer.write("CHAT:" + text + "\n");
        writer.flush();
      } catch (Exception e) {
        System.err.println("[LobbyScreen] Error sending chat: " + e.getMessage());
      }
    }).start();

    chatInputField.clear();
  }

  @Override
  protected void onBeforeExit() {
    lanDiscovery.stop();
    connectingToHost = false;

    if (!socketHandedOffToGame) {
      closeActiveSocket();

      if (server != null) {
        server.stop();
        server = null;
      }
    }
  }
}
