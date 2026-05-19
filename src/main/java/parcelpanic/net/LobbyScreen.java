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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import parcelpanic.input.InputAction;
import parcelpanic.input.InputHintProvider;
import parcelpanic.media.AssetKeys.AudioKey;
import parcelpanic.media.AssetKeys.ColorKey;
import parcelpanic.media.AssetKeys.FontKey;
import parcelpanic.media.AssetKeys.ImageKey;
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
    NAME("Name: "),
    HOST("Host game"),
    JOIN("Join game"),
    BACK("Back to main menu");

    private final String text;

    ChooseItem(String text) {
      this.text = text;
    }

    public String getText() {
      return text;
    }
  }

  private enum HostingItem {
    START_MATCH("Start match"),
    CHANGE_COLOR("Change car color"),
    CANCEL_HOST("End session");

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
    CHANGE_COLOR("Change car color"),
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
  private final List<String> activePlayers = new ArrayList<>();
  private final Map<Integer, SmoothedValue> offsets = new HashMap<>();
  private String myPlayerName = "Player";
  private int myColorIndex = 1; // Default to Red, Style 1

  private GameServer server;
  private Socket activeSocket;
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
  private final Map<String, LanDiscoveryService.DiscoveredServer> discoveredServers =
      new LinkedHashMap<>();
  private final List<Label> discoveredServerLabels = new ArrayList<>();
  private Label enterIpLabel;
  private Label joinBackLabel;

  // Lobby Chat UI
  private VBox chatPanel;
  private VBox chatMessagesContainer;
  private ScrollPane chatScrollPane;
  private TextField chatInputField;
  private final Map<String, Color> playerChatColors = new HashMap<>();

  private final Color[] chatPalette =
      new Color[] {
        Color.web("#ff7675"),
        Color.web("#74b9ff"),
        Color.web("#55efc4"),
        Color.web("#ffeaa7"),
        Color.web("#a29bfe"),
        Color.web("#fd79a8"),
        Color.web("#81ecec"),
        Color.web("#fab1a0")
      };

  private final Random chatRandom = new Random();

  @Override
  protected VideoManager.ViewportMode viewportMode() {
    return VideoManager.ViewportMode.FULL_WINDOW;
  }

  @Override
  public void fixedUpdate(double dtSeconds) {
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
    this.activePlayers.clear();

    if (newMode == Mode.JOINING) {
      this.enterIpLabel = null;
      this.joinBackLabel = null;
      startLanListening();
    } else if (newMode != Mode.HOSTING) {
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
        discoveredServer ->
            Platform.runLater(
                () -> {
                  if (currentMode != Mode.JOINING || discoveredServersBox == null) {
                    return;
                  }

                  discoveredServers.put(discoveredServer.ip(), discoveredServer);
                  updateOrCreateDiscoveredServerLabel(discoveredServer);
                  rebuildJoinItemLabels();
                }));
  }

  private void updateOrCreateDiscoveredServerLabel(
      LanDiscoveryService.DiscoveredServer serverInfo) {
    String ip = serverInfo.ip();

    Label existing =
        discoveredServerLabels.stream()
            .filter(label -> ip.equals(label.getUserData()))
            .findFirst()
            .orElse(null);

    String text =
        serverInfo.isFull()
            ? "FULL  "
                + ip
                + "  ("
                + serverInfo.currentPlayers()
                + "/"
                + serverInfo.maxPlayers()
                + ")"
            : "JOIN  "
                + ip
                + "  ("
                + serverInfo.currentPlayers()
                + "/"
                + serverInfo.maxPlayers()
                + ")";

    if (existing != null) {
      existing.setText(text);
      existing.setDisable(serverInfo.isFull());
      return;
    }

    Font menuFont = ctx.assets().getFont(FontKey.TITLE);
    Label label = UiFactory.createLabel(text, menuFont, mutedColor);
    label.setUserData(ip);
    label.setDisable(serverInfo.isFull());

    label.setOnMouseClicked(
        e -> {
          LanDiscoveryService.DiscoveredServer latest = discoveredServers.get(ip);
          if (latest != null && !latest.isFull()) {
            manualHostIp = ip;
            connectToHost(ip);
          }
        });

    discoveredServerLabels.add(label);

    if (discoveredServersBox != null) {
      discoveredServersBox.getChildren().add(label);
    }
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
      titleLabel = UiFactory.createTitle("Lobby", titleFont, textColor);
    } else if (currentMode == Mode.HOSTING) {
      titleLabel = UiFactory.createTitle("Host Game", titleFont, textColor);
    } else if (currentMode == Mode.JOINING) {
      titleLabel = UiFactory.createTitle("Join Game", titleFont, textColor);
    } else {
      titleLabel = UiFactory.createTitle("Lobby", titleFont, textColor);
    }

    VBox topContainer = null;
    if (currentMode == Mode.CHOOSE || currentMode == Mode.JOINING) {
      topContainer = new VBox(titleLabel);
      topContainer.setAlignment(Pos.TOP_CENTER);
      topContainer.setPadding(new Insets(40, 0, 0, 0));
    }

    VBox menuContainer = new VBox(12);
    menuContainer.setAlignment(Pos.TOP_LEFT);
    menuContainer.setPadding(new Insets(40, 0, 0, 100));
    this.menuContainer = menuContainer;

    if (currentMode == Mode.HOSTING || currentMode == Mode.LOBBY) {
      titleLabel.setPadding(new Insets(0, 0, 10, 0));
      menuContainer.getChildren().add(titleLabel);
    }

    if (currentMode == Mode.CHOOSE) {
      Label desc =
          UiFactory.createLabel("Select your role to start or join a match", labelFont, textColor);
      desc.setPadding(new Insets(0, 0, 10, 0));
      menuContainer.getChildren().add(desc);

      for (ChooseItem item : ChooseItem.values()) {
        String text = item.getText();
        if (item == ChooseItem.NAME) {
          text += myPlayerName;
        }
        Label label = UiFactory.createLabel(text, menuFont, textColor);
        itemLabels.add(label);
        menuContainer.getChildren().add(label);
      }

    } else if (currentMode == Mode.HOSTING) {
      statusLabel = UiFactory.createLabel("Hosting...", labelFont, textColor);
      ipInfoLabel = UiFactory.createLabel("IP: " + resolvedIp, labelFont, selectedColor);

      VBox infoBox = new VBox(10, statusLabel, ipInfoLabel);
      infoBox.setPadding(new Insets(0, 0, 10, 0));
      menuContainer.getChildren().add(infoBox);

      for (HostingItem item : HostingItem.values()) {
        String text = item.getText();
        if (item == HostingItem.CHANGE_COLOR) {
          text = "Change car color: " + getColorName(myColorIndex);
        }
        Label label = UiFactory.createLabel(text, menuFont, textColor);
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

      Label lanLabel = UiFactory.createLabel("LAN Servers", labelFont, textColor);

      discoveredServersBox = new VBox(10);
      discoveredServersBox.setPadding(new Insets(0, 0, 0, 20));
      discoveredServersBox.setAlignment(Pos.CENTER_LEFT);

      discoveredServerLabels.clear();
      for (LanDiscoveryService.DiscoveredServer serverInfo : discoveredServers.values()) {
        updateOrCreateDiscoveredServerLabel(serverInfo);
      }

      menuContainer.getChildren().addAll(fieldBox, lanLabel, discoveredServersBox);

      enterIpLabel = UiFactory.createLabel(JoiningItem.ENTER_IP.getText(), menuFont, textColor);
      menuContainer.getChildren().add(enterIpLabel);

      joinBackLabel = UiFactory.createLabel(JoiningItem.BACK.getText(), menuFont, textColor);
      menuContainer.getChildren().add(joinBackLabel);

      rebuildJoinItemLabels();
    } else if (currentMode == Mode.LOBBY) {
      statusLabel = UiFactory.createLabel("Waiting for host...", labelFont, textColor);
      ipInfoLabel = UiFactory.createLabel("Host IP: " + manualHostIp, labelFont, selectedColor);
      VBox statusBox = new VBox(10, statusLabel, ipInfoLabel);
      statusBox.setAlignment(Pos.CENTER_LEFT);
      statusBox.setPadding(new Insets(0, 0, 10, 0));
      menuContainer.getChildren().add(statusBox);

      for (LobbyItem item : LobbyItem.values()) {
        String text = item.getText();
        if (item == LobbyItem.CHANGE_COLOR) {
          text = "Change Car Color: " + getColorName(myColorIndex);
        }
        Label label = UiFactory.createLabel(text, menuFont, textColor);
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
              InputHintProvider.getIcon(KeyCode.T), "Chat", iconFont, hintFont, hintColor);
      hintsRow.getChildren().add(chatHint);
    }

    VBox bottomContainer = new VBox(20, hintsRow);
    bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
    bottomContainer.setPadding(new Insets(0, 0, 30, 0));

    rootPane.setTop(topContainer);

    if (currentMode == Mode.HOSTING || currentMode == Mode.LOBBY) {
      VBox chatSide = buildChatPanel();
      chatSide.setPrefWidth(430);
      chatSide.setPrefHeight(350);

      VBox playersList = buildPlayersListSection(labelFont, menuFont);
      playersList.setPrefWidth(430);

      VBox rightContainer = new VBox(15, playersList, chatSide);
      rightContainer.setAlignment(Pos.TOP_LEFT);
      rightContainer.setPadding(new Insets(40, 0, 0, 0));
      rightContainer.setPrefWidth(430);

      HBox splitBox = new HBox(60);
      splitBox.setAlignment(Pos.CENTER);
      splitBox.setPadding(new Insets(0, 50, 0, 50));

      menuContainer.setPrefWidth(550);

      splitBox.getChildren().addAll(menuContainer, rightContainer);
      rootPane.setCenter(splitBox);
    } else {
      rootPane.setCenter(menuContainer);
    }

    rootPane.setBottom(bottomContainer);

    updateSelectionColors();
    Platform.runLater(() -> rootPane.requestFocus());
  }

  private VBox buildPlayersListSection(Font labelFont, Font menuFont) {
    Label playersHeader =
        UiFactory.createLabel(
            "PLAYERS IN LOBBY (" + activePlayers.size() + "/4)", labelFont, selectedColor);
    playersHeader.setStyle("-fx-font-weight: bold;");
    VBox playersListBox = new VBox(8);
    playersListBox.setPadding(new Insets(10, 15, 10, 15));
    playersListBox.setStyle(
        "-fx-background-color: rgba(255, 255, 255, 0.05); -fx-border-color: rgba(255, 255, 255, 0.1); -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px;");

    for (int i = 0; i < 4; i++) {
      if (i < activePlayers.size()) {
        String playerEntry = activePlayers.get(i);
        String name = playerEntry;
        String colorName = "Red";
        if (playerEntry.contains("|")) {
          String[] parts = playerEntry.split("\\|");
          name = parts[0];
          colorName = parts[1];
        }

        ImageKey key =
            switch (colorName.toLowerCase()) {
              case "red" -> ImageKey.VEHICLE_CAR_RED_1;
              case "blue" -> ImageKey.VEHICLE_CAR_BLUE_1;
              case "green" -> ImageKey.VEHICLE_CAR_GREEN_1;
              case "yellow" -> ImageKey.VEHICLE_CAR_YELLOW_1;
              case "orange" -> ImageKey.VEHICLE_CAR_ORANGE_1;
              case "pink" -> ImageKey.VEHICLE_CAR_PINK_1;
              case "magenta" -> ImageKey.VEHICLE_CAR_MAGENTA_1;
              default -> ImageKey.VEHICLE_CAR;
            };

        Image img = ctx.assets().getImage(key);
        ImageView view = new ImageView(img);
        view.setViewport(new Rectangle2D(0, 0, 22, 22)); // First frame facing North
        view.setFitWidth(30);
        view.setFitHeight(30);
        view.setSmooth(false); // crisp retro pixel art

        Label nameLabel = UiFactory.createLabel("  " + name, menuFont, textColor);

        HBox row = new HBox(view, nameLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        playersListBox.getChildren().add(row);
      } else {
        // Empty slot placeholder
        Rectangle rect = new Rectangle(30, 30);
        rect.setFill(Color.TRANSPARENT);
        rect.setStroke(Color.color(1, 1, 1, 0.15));
        rect.setStrokeWidth(1.5);
        rect.getStrokeDashArray().addAll(4.0, 4.0);

        Label openLabel = UiFactory.createLabel("  [Open Slot]", menuFont, mutedColor);
        openLabel.setStyle("-fx-opacity: 0.5;");

        HBox row = new HBox(rect, openLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        playersListBox.getChildren().add(row);
      }
    }
    VBox playersContainer = new VBox(10, playersHeader, playersListBox);
    playersContainer.setPadding(new Insets(0, 0, 15, 0));
    return playersContainer;
  }

  private void updatePlayerList(String playersStr) {
    activePlayers.clear();
    if (!playersStr.trim().isEmpty()) {
      for (String p : playersStr.split(",")) {
        activePlayers.add(p.trim());
      }
    }
    buildUI();
  }

  private String getColorName(int colorIndex) {
    int color = colorIndex / 10;
    return switch (color) {
      case 0 -> "Red";
      case 1 -> "Blue";
      case 2 -> "Green";
      case 3 -> "Yellow";
      case 4 -> "Orange";
      case 5 -> "Pink";
      case 6 -> "Magenta";
      default -> "Red";
    };
  }

  private int getNextColorIndex(int currentIndex, boolean forward) {
    int color = currentIndex / 10;
    int style = currentIndex % 10;
    if (style != 1 && style != 2) style = 1;

    if (forward) {
      color = (color + 1) % 7;
    } else {
      color = (color - 1 + 7) % 7;
    }
    return color * 10 + style;
  }

  private int toggleStyle(int currentIndex) {
    int color = currentIndex / 10;
    int style = currentIndex % 10;
    style = (style == 1) ? 2 : 1;
    return color * 10 + style;
  }

  private void sendNameColorUpdate() {
    if (activeSocket != null && !socketHandedOffToGame) {
      try {
        java.io.BufferedWriter writer =
            new java.io.BufferedWriter(
                new java.io.OutputStreamWriter(activeSocket.getOutputStream()));
        writer.write("NAME_COLOR:" + myPlayerName + ":" + myColorIndex + "\n");
        writer.flush();
      } catch (java.io.IOException e) {
        System.err.println("Failed to send NAME_COLOR update: " + e.getMessage());
      }
    }
  }

  private void updateSelectionColors() {
    for (int i = 0; i < itemLabels.size(); i++) {
      Label label = itemLabels.get(i);

      if (label != null) {
        boolean highlighted = (i == selectedIndex);

        boolean disabled = false;
        if (currentMode == Mode.HOSTING) {
          HostingItem item = HostingItem.values()[i];
          if (item == HostingItem.START_MATCH && activePlayers.size() < 2) {
            disabled = true;
          }
        }

        if (disabled) {
          label.setTextFill(highlighted ? Color.web("#444444") : Color.web("#222222"));
        } else {
          label.setTextFill(highlighted ? selectedColor : mutedColor);
        }
      }
    }

    if (discoveredServersBox != null) {
      for (Node node : discoveredServersBox.getChildren()) {
        if (node instanceof Label label && !itemLabels.contains(label)) {
          label.setTextFill(textColor);
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

                lanDiscovery.startBroadcasting(
                    resolvedIp, () -> server != null ? server.getConnectedCount() : 0, 4);

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
    chatPanel = null;
    chatMessagesContainer = null;
    chatScrollPane = null;
    chatInputField = null;
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
                sendNameColorUpdate();
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
                  } else if (line.startsWith("PLAYERS:")) {
                    String playersStr = line.substring(8);
                    Platform.runLater(() -> updatePlayerList(playersStr));
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
        case NAME -> openNameOverlay();
        case HOST -> startHosting();
        case JOIN -> setMode(Mode.JOINING);
        case BACK -> ctx.navigator().requestSwitch(new parcelpanic.screens.MenuScreen());
      }
    } else if (currentMode == Mode.HOSTING) {
      HostingItem item = HostingItem.values()[selectedIndex];
      switch (item) {
        case START_MATCH -> {
          if (activePlayers.size() >= 2 && server != null) {
            server.startMatch();
          } else {
            ctx.audio().playSound(AudioKey.ERROR);
          }
        }
        case CHANGE_COLOR -> {
          myColorIndex = getNextColorIndex(myColorIndex, true);
          sendNameColorUpdate();
          buildUI();
        }
        case CANCEL_HOST -> stopHosting();
      }
    } else if (currentMode == Mode.JOINING) {
      if (selectedIndex < discoveredServerLabels.size()) {
        Label selectedServerLabel = discoveredServerLabels.get(selectedIndex);
        String ip = (String) selectedServerLabel.getUserData();

        LanDiscoveryService.DiscoveredServer selectedServer = discoveredServers.get(ip);
        if (selectedServer != null && !selectedServer.isFull()) {
          manualHostIp = ip;
          connectToHost(ip);
        }
        return;
      }

      if (selectedIndex == discoveredServerLabels.size()) {
        openManualIpOverlay();
        return;
      }

      closeActiveSocket();
      setMode(Mode.CHOOSE);
    } else if (currentMode == Mode.LOBBY) {
      LobbyItem item = LobbyItem.values()[selectedIndex];
      switch (item) {
        case CHANGE_COLOR -> {
          myColorIndex = getNextColorIndex(myColorIndex, true);
          sendNameColorUpdate();
          buildUI();
        }
        case LEAVE -> {
          closeActiveSocket();
          connectingToHost = false;
          setMode(Mode.CHOOSE);
        }
      }
    }
  }

  private void openManualIpOverlay() {
    ctx.navigator()
        .push(
            new TextInputOverlay(
                "Enter Host IP",
                "Type the host IP and press Enter",
                manualHostIp,
                "e.g. 192.168.1.10",
                ip -> {
                  manualHostIp = ip;
                  connectToHost(ip);
                }));
  }

  private void openNameOverlay() {
    ctx.navigator()
        .push(
            new TextInputOverlay(
                "Set Your Name",
                "Type your name and press Enter",
                myPlayerName,
                "Enter your name",
                name -> {
                  myPlayerName = name.isEmpty() ? "Player" : name;
                  sendNameColorUpdate();
                  buildUI();
                }));
  }

  private final class TextInputOverlay implements parcelpanic.screen.Screen {
    private final String titleText;
    private final String helperText;
    private final String initialValue;
    private final String promptText;
    private final java.util.function.Consumer<String> onConfirm;
    private BorderPane overlayRoot;
    private TextField inputField;

    private TextInputOverlay(
        String title,
        String helper,
        String initialValue,
        String prompt,
        java.util.function.Consumer<String> onConfirm) {
      this.titleText = title;
      this.helperText = helper;
      this.initialValue = initialValue;
      this.promptText = prompt;
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
          UiFactory.createOverlay(
                  surface, 0.88, VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT)
              .getBackground());

      Font titleFont = ctx.assets().getFont(FontKey.DISPLAY);
      Font labelFont = ctx.assets().getFont(FontKey.TITLE);

      Label title = UiFactory.createTitle(titleText, titleFont, text);
      VBox topContainer = new VBox(title);
      topContainer.setAlignment(Pos.TOP_CENTER);
      topContainer.setPadding(new Insets(80, 0, 0, 0));

      Label helper = UiFactory.createLabel(helperText, labelFont, muted);

      inputField =
          UiFactory.createTextField(
              initialValue,
              promptText,
              ctx.assets().getFont(FontKey.BODY),
              this::confirm,
              this::cancel);
      inputField.setMaxWidth(320);

      VBox center = new VBox(16, helper, inputField);
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

      Platform.runLater(() -> inputField.requestFocus());
    }

    @Override
    public void exit() {
      overlayRoot = null;
      inputField = null;
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
      if (inputField != null) {
        String value = inputField.getText();
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
      connectingToHost = false;
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
      case UI_LEFT -> {
        if (currentMode == Mode.HOSTING) {
          HostingItem item = HostingItem.values()[selectedIndex];
          if (item == HostingItem.CHANGE_COLOR) {
            myColorIndex = getNextColorIndex(myColorIndex, false);
            sendNameColorUpdate();
            buildUI();
            ctx.audio().playSound(AudioKey.MOVE_SELECTION);
          }
        } else if (currentMode == Mode.LOBBY) {
          LobbyItem item = LobbyItem.values()[selectedIndex];
          if (item == LobbyItem.CHANGE_COLOR) {
            myColorIndex = getNextColorIndex(myColorIndex, false);
            sendNameColorUpdate();
            buildUI();
            ctx.audio().playSound(AudioKey.MOVE_SELECTION);
          }
        }
      }
      case UI_RIGHT -> {
        if (currentMode == Mode.HOSTING) {
          HostingItem item = HostingItem.values()[selectedIndex];
          if (item == HostingItem.CHANGE_COLOR) {
            myColorIndex = getNextColorIndex(myColorIndex, true);
            sendNameColorUpdate();
            buildUI();
            ctx.audio().playSound(AudioKey.MOVE_SELECTION);
          }
        } else if (currentMode == Mode.LOBBY) {
          LobbyItem item = LobbyItem.values()[selectedIndex];
          if (item == LobbyItem.CHANGE_COLOR) {
            myColorIndex = getNextColorIndex(myColorIndex, true);
            sendNameColorUpdate();
            buildUI();
            ctx.audio().playSound(AudioKey.MOVE_SELECTION);
          }
        }
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
    if (chatPanel != null) {
      return chatPanel;
    }
    chatPanel = new VBox(15);
    chatPanel.setPadding(new Insets(10, 15, 10, 15));
    chatPanel.setAlignment(Pos.TOP_CENTER);
    chatPanel.setStyle(
        "-fx-background-color: rgba(30, 30, 30, 0.65);"
            + " -fx-background-radius: 12px;"
            + " -fx-border-color: rgba(255, 255, 255, 0.15);"
            + " -fx-border-radius: 12px;"
            + " -fx-border-width: 1px;");

    Font titleFont = ctx.assets().getFont(FontKey.TITLE);
    Label chatTitle = UiFactory.createLabel("LOBBY CHAT", titleFont, selectedColor);
    chatTitle.setAlignment(Pos.CENTER);

    chatMessagesContainer = new VBox(8);
    chatMessagesContainer.setAlignment(Pos.TOP_LEFT);

    chatScrollPane = new ScrollPane(chatMessagesContainer);
    chatScrollPane.setFitToWidth(true);
    chatScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    chatScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    chatScrollPane.setPrefHeight(180);

    // Auto-scroll on container height changes (perfect dynamic scroll alignment)
    chatMessagesContainer
        .heightProperty()
        .addListener(
            (obs, oldVal, newVal) -> {
              chatScrollPane.setVvalue(1.0);
            });
    chatScrollPane.setStyle(
        "-fx-background: transparent; -fx-background-color: transparent; -fx-viewport-background-color: transparent;");

    chatInputField =
        UiFactory.createTextField(
            "",
            "Press Enter to send message...",
            ctx.assets().getFont(FontKey.BODY),
            this::sendChat,
            () -> rootPane.requestFocus());
    chatInputField.setPrefWidth(380);

    chatPanel.getChildren().addAll(chatTitle, chatScrollPane, chatInputField);
    return chatPanel;
  }

  private void addChatMessage(String msg) {
    if (chatMessagesContainer == null || chatScrollPane == null) {
      return;
    }

    Font chatFont = ctx.assets().getFont(FontKey.LABEL);

    String username = "Player";
    String messageBody = msg;

    int separator = msg.indexOf(":");

    if (separator > 0) {
      username = msg.substring(0, separator).trim();
      messageBody = msg.substring(separator + 1).trim();
    }

    Color usernameColor =
        playerChatColors.computeIfAbsent(
            username, key -> chatPalette[chatRandom.nextInt(chatPalette.length)]);

    Label usernameLabel = new Label(username + ": ");
    usernameLabel.setFont(chatFont);
    usernameLabel.setTextFill(usernameColor);

    Label messageLabel = new Label(messageBody);
    messageLabel.setFont(chatFont);
    messageLabel.setWrapText(true);
    messageLabel.setMaxWidth(320);

    HBox row = new HBox(4, usernameLabel, messageLabel);
    row.setAlignment(Pos.TOP_LEFT);

    chatMessagesContainer.getChildren().add(row);

    Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
  }

  private void sendChat() {
    if (chatInputField == null || activeSocket == null) return;
    String text = chatInputField.getText().trim();
    if (text.isEmpty()) return;

    new Thread(
            () -> {
              try {
                java.io.BufferedWriter writer =
                    new java.io.BufferedWriter(
                        new java.io.OutputStreamWriter(
                            activeSocket.getOutputStream(),
                            java.nio.charset.StandardCharsets.UTF_8));
                writer.write("CHAT:" + text + "\n");
                writer.flush();
              } catch (Exception e) {
                System.err.println("[LobbyScreen] Error sending chat: " + e.getMessage());
              }
            })
        .start();

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
