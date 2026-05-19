import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

// =====================================================================
// CMPE412 Project 2026 — Distributed Inventory Management
//   Client side: JavaFX GUI that connects to the InventoryServer over
//   plain text sockets.
//
//   port = 5000 + (last 4 digits of student ID) % 1000
//   ID 22203941 -> 5000 + (3941 % 1000) -> 5000 + 941 -> 5941
// =====================================================================
public class InventoryClient extends Application {

    static final String STUDENT_ID   = "22203941";
    static final int    DEFAULT_PORT =
            5000 + (Integer.parseInt(
                        STUDENT_ID.substring(STUDENT_ID.length() - 4))
                    % 1000);

    private Stage      stage;
    private TextField  ipField;
    private TextField  portField;
    private Button     connectButton;
    private Button     disconnectButton;
    private Button     verifyButton;
    private Label      statusLabel;

    private FlowPane   filePanel;
    private Button     overviewButton;

    private TableView<Product>            table;
    private ObservableList<Product>       tableData;
    private TextArea                      overviewArea;

    private Socket         socket;
    private BufferedReader in;
    private PrintWriter    out;
    private Thread         readerThread;
    private volatile boolean connected = false;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setTitle("CMPE412 Inventory Client");

        // ---------------- TOP : connection bar ----------------
        HBox connBar = new HBox(6);
        connBar.setPadding(new Insets(8));

        ipField   = new TextField("localhost");
        ipField.setPrefColumnCount(12);
        portField = new TextField(String.valueOf(DEFAULT_PORT));
        portField.setPrefColumnCount(6);

        connectButton    = new Button("Connect");
        disconnectButton = new Button("Disconnect");
        verifyButton     = new Button("Verify");
        disconnectButton.setDisable(true);
        verifyButton.setDisable(true);

        statusLabel = new Label("DISCONNECTED");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;");

        connBar.getChildren().addAll(
            new Label("Server IP:"), ipField,
            new Label("Port:"),      portField,
            connectButton, disconnectButton, verifyButton,
            new Label("  Status:"),   statusLabel
        );

        // ---------------- CENTER : files panel + split pane ----------------
        filePanel = new FlowPane(6, 6);
        filePanel.setPadding(new Insets(6));
        TitledPane filesTitled = new TitledPane("Inventory Files", filePanel);
        filesTitled.setCollapsible(false);

        tableData = FXCollections.observableArrayList();
        table = new TableView<>(tableData);

        TableColumn<Product, String> idCol    = new TableColumn<>("Product ID");
        TableColumn<Product, String> nameCol  = new TableColumn<>("Name");
        TableColumn<Product, String> priceCol = new TableColumn<>("Price");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        idCol.setPrefWidth(100);
        nameCol.setPrefWidth(220);
        priceCol.setPrefWidth(100);
        table.getColumns().add(idCol);
        table.getColumns().add(nameCol);
        table.getColumns().add(priceCol);
        table.setStyle("-fx-font-family: 'monospace';");

        TitledPane tableTitled = new TitledPane("File contents", table);
        tableTitled.setCollapsible(false);

        overviewArea = new TextArea();
        overviewArea.setEditable(false);
        overviewArea.setStyle("-fx-font-family: 'monospace';");
        TitledPane overviewTitled = new TitledPane("Overview", overviewArea);
        overviewTitled.setCollapsible(false);

        SplitPane split = new SplitPane(tableTitled, overviewTitled);
        split.setDividerPositions(0.6);

        BorderPane centerPane = new BorderPane();
        centerPane.setTop(filesTitled);
        centerPane.setCenter(split);

        BorderPane root = new BorderPane();
        root.setTop(connBar);
        root.setCenter(centerPane);

        // ---------------- Listeners ----------------
        connectButton.setOnAction(e -> connect());
        disconnectButton.setOnAction(e -> disconnect(true));
        verifyButton.setOnAction(e -> sendLine("VERIFY"));

        stage.setScene(new Scene(root, 1000, 650));
        stage.setOnCloseRequest(e -> disconnect(true));
        stage.show();
    }

    // -----------------------------------------------------------------
    // Connect / Disconnect
    // -----------------------------------------------------------------
    private void connect() {
        if (connected) return;

        String ip = ipField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException nfe) {
            showAlert(AlertType.ERROR, "Invalid port",
                      "Please enter a valid port number.");
            return;
        }

        try {
            socket = new Socket(ip, port);
            in     = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
            out    = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException ioe) {
            showAlert(AlertType.ERROR, "Connection failed",
                      "Could not connect to " + ip + ":" + port
                      + "\n" + ioe.getMessage());
            return;
        }

        connected = true;
        statusLabel.setText("CONNECTED");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #008c00;");
        connectButton.setDisable(true);
        disconnectButton.setDisable(false);
        verifyButton.setDisable(false);
        ipField.setDisable(true);
        portField.setDisable(true);

        readerThread = new Thread(this::readerLoop, "ClientReader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void disconnect(boolean sendBye) {
        if (!connected) return;
        connected = false;

        if (sendBye && out != null) {
            try { out.println("BYE"); } catch (Exception ignored) { }
        }

        try { if (socket != null) socket.close(); } catch (IOException ignored) { }

        Platform.runLater(() -> {
            statusLabel.setText("DISCONNECTED");
            statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;");
            connectButton.setDisable(false);
            disconnectButton.setDisable(true);
            verifyButton.setDisable(true);
            ipField.setDisable(false);
            portField.setDisable(false);
            filePanel.getChildren().clear();
        });
    }

    // -----------------------------------------------------------------
    // Reader loop — parses every framed message from the server
    // -----------------------------------------------------------------
    private void readerLoop() {
        try {
            String header;
            while (connected && (header = in.readLine()) != null) {
                header = header.trim();
                if (header.isEmpty()) continue;

                if (header.startsWith("FILES ")) {
                    int n = Integer.parseInt(header.substring(6).trim());
                    final String[] names = new String[n];
                    for (int i = 0; i < n; i++) names[i] = in.readLine();
                    showFileButtons(names);

                } else if (header.startsWith("FILE ")) {
                    int n = Integer.parseInt(header.substring(5).trim());
                    final String[] rows = new String[n];
                    for (int i = 0; i < n; i++) rows[i] = in.readLine();
                    in.readLine(); // consume END
                    showFileRows(rows);

                } else if (header.equals("OVERVIEW")) {
                    String avgLine = in.readLine();
                    String maxLine = in.readLine();
                    String minLine = in.readLine();
                    String totLine = in.readLine();
                    in.readLine(); // END
                    showOverview(avgLine, maxLine, minLine, totLine);

                } else if (header.startsWith("VERIFY ")) {
                    final String hash = header.substring(7).trim();
                    Platform.runLater(() ->
                        showAlert(AlertType.INFORMATION, "VERIFY response",
                                  "Verification hash: " + hash));

                } else if (header.startsWith("ERROR")) {
                    final String err = header;
                    Platform.runLater(() ->
                        showAlert(AlertType.WARNING, "Server error", err));
                }
            }
        } catch (IOException ioe) {
            if (connected) {
                final String msg = ioe.getMessage();
                Platform.runLater(() ->
                    showAlert(AlertType.WARNING, "Disconnected",
                              "Lost connection: " + msg));
            }
        } finally {
            disconnect(false);
        }
    }

    // -----------------------------------------------------------------
    // GUI mutation helpers (always invoked on the JavaFX thread)
    // -----------------------------------------------------------------
    private void showFileButtons(final String[] names) {
        Platform.runLater(() -> {
            filePanel.getChildren().clear();
            for (final String name : names) {
                Button b = new Button(name);
                b.setOnAction(e -> sendLine("GET " + name));
                filePanel.getChildren().add(b);
            }
            overviewButton = new Button("Get Overview");
            overviewButton.setStyle("-fx-text-fill: #005aa0;");
            overviewButton.setOnAction(e -> sendLine("OVERVIEW"));
            filePanel.getChildren().add(overviewButton);
        });
    }

    private void showFileRows(final String[] rows) {
        Platform.runLater(() -> {
            tableData.clear();
            for (String row : rows) {
                if (row == null) continue;
                String[] data = row.split(",");
                if (data.length >= 3) {
                    tableData.add(new Product(
                        data[0].trim(), data[1].trim(), data[2].trim()));
                }
            }
        });
    }

    private void showOverview(final String avgLine, final String maxLine,
                              final String minLine, final String totLine) {
        Platform.runLater(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Inventory Overview ===\n\n");

            if (avgLine != null && avgLine.startsWith("AVG ")) {
                sb.append("Average price : $")
                  .append(avgLine.substring(4).trim()).append("\n");
            }
            if (maxLine != null && maxLine.startsWith("MAX ")) {
                String[] parts = maxLine.substring(4).split("\\|", 2);
                sb.append("Highest price : $").append(parts[0].trim());
                if (parts.length > 1) sb.append("   (").append(parts[1]).append(")");
                sb.append("\n");
            }
            if (minLine != null && minLine.startsWith("MIN ")) {
                String[] parts = minLine.substring(4).split("\\|", 2);
                sb.append("Lowest  price : $").append(parts[0].trim());
                if (parts.length > 1) sb.append("   (").append(parts[1]).append(")");
                sb.append("\n");
            }
            if (totLine != null && totLine.startsWith("TOTAL ")) {
                sb.append("\nTotal unique products after merge: ")
                  .append(totLine.substring(6).trim()).append("\n");
            }

            overviewArea.setText(sb.toString());
        });
    }

    private void sendLine(String line) {
        if (!connected || out == null) return;
        out.println(line);
    }

    private void showAlert(AlertType type, String title, String content) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.showAndWait();
    }

    // -----------------------------------------------------------------
    // Row model for TableView
    // -----------------------------------------------------------------
    public static class Product {
        private final SimpleStringProperty id;
        private final SimpleStringProperty name;
        private final SimpleStringProperty price;

        public Product(String id, String name, String price) {
            this.id    = new SimpleStringProperty(id);
            this.name  = new SimpleStringProperty(name);
            this.price = new SimpleStringProperty(price);
        }

        public String getId()    { return id.get(); }
        public String getName()  { return name.get(); }
        public String getPrice() { return price.get(); }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
