import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// =====================================================================
// CMPE412 Project 2026 — Distributed Inventory Management
//   Server side: JavaFX GUI + accept loop + per-client handler thread
//
// PERSONALIZATION (anti-plagiarism, see project PDF):
//   Student Name : ahmed mohamed
//   Student ID   : 22203941
//   Default port : 5000 + (last 4 digits of ID) % 1000
//                = 5000 + (3941 % 1000)
//                = 5000 + 941
//                = 5941
//   Digit sum    : 2+2+2+0+3+9+4+1 = 23
//   VERIFY hash  : (digit_sum * active_port) % 1000
//                = (23 * 5941) % 1000  = 643
//   Startup log  : "Server started by Student ahmed mohamed (ID: 22203941)"
// =====================================================================
public class InventoryServer extends Application {

    static final String STUDENT_NAME = "ahmed mohamed";
    static final String STUDENT_ID   = "22203941";
    static final int    DEFAULT_PORT =
            5000 + (Integer.parseInt(
                        STUDENT_ID.substring(STUDENT_ID.length() - 4))
                    % 1000);
    static final int    DIGIT_SUM    = digitSum(STUDENT_ID);

    static final String[] INVENTORY_FILES = {
        "Electronics.txt", "Clothing.txt", "Groceries.txt", "Books.txt"
    };

    private Stage      stage;
    private Label      ipLabel;
    private TextField  portField;
    private Button     startButton;
    private Button     stopButton;
    private Button     clearLogButton;
    private Label      statusLabel;
    private TextArea   logArea;

    private ServerSocket    serverSocket;
    private Thread          acceptThread;
    private ExecutorService clientPool;
    private volatile boolean running = false;
    private volatile int     activePort = DEFAULT_PORT;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setTitle("CMPE412 Inventory Server  -  "
                       + STUDENT_NAME + " (" + STUDENT_ID + ")");

        // ---------------- TOP : connection bar ----------------
        HBox northPanel = new HBox(8);
        northPanel.setPadding(new Insets(8));

        String ipText;
        try {
            ipText = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            ipText = "unknown";
        }
        ipLabel = new Label("Server IP: " + ipText + "    Port: ");
        ipLabel.setStyle("-fx-font-weight: bold;");

        portField = new TextField(String.valueOf(DEFAULT_PORT));
        portField.setPrefColumnCount(6);

        startButton = new Button("Start");
        stopButton  = new Button("Stop");
        stopButton.setDisable(true);

        statusLabel = new Label("STOPPED");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;");

        northPanel.getChildren().addAll(
            ipLabel, portField, startButton, stopButton,
            new Label("  Status:"), statusLabel
        );

        // ---------------- CENTER : activity log ----------------
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setStyle("-fx-font-family: 'monospace';");
        TitledPane logTitled = new TitledPane("Activity Log", logArea);
        logTitled.setCollapsible(false);

        // ---------------- BOTTOM : clear log ----------------
        HBox southPanel = new HBox();
        southPanel.setPadding(new Insets(6));
        clearLogButton = new Button("Clear Log");
        southPanel.getChildren().add(clearLogButton);
        southPanel.setStyle("-fx-alignment: center-right;");

        BorderPane root = new BorderPane();
        root.setTop(northPanel);
        root.setCenter(logTitled);
        root.setBottom(southPanel);

        // ---------------- Listeners ----------------
        startButton.setOnAction(e -> startServer());
        stopButton.setOnAction(e -> stopServer());
        clearLogButton.setOnAction(e -> logArea.clear());

        stage.setScene(new Scene(root, 750, 540));
        stage.setOnCloseRequest(e -> {
            stopServer();
            Platform.exit();
        });
        stage.show();
    }

    // -----------------------------------------------------------------
    // Start / Stop
    // -----------------------------------------------------------------
    private void startServer() {
        if (running) return;

        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1024 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException nfe) {
            showAlert(AlertType.ERROR, "Invalid port",
                      "Please enter a valid port number (1024 - 65535).");
            return;
        }

        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException ioe) {
            showAlert(AlertType.ERROR, "Socket error",
                      "Could not bind to port " + port + ": " + ioe.getMessage());
            return;
        }

        activePort = port;
        running    = true;
        clientPool = Executors.newCachedThreadPool();

        log("Server started by Student " + STUDENT_NAME
            + " (ID: " + STUDENT_ID + ")");
        log("Server started on port " + activePort);

        acceptThread = new Thread(this::acceptLoop, "AcceptThread");
        acceptThread.setDaemon(true);
        acceptThread.start();

        startButton.setDisable(true);
        stopButton.setDisable(false);
        portField.setDisable(true);
        statusLabel.setText("RUNNING");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #008c00;");
    }

    private void stopServer() {
        if (!running) return;
        running = false;

        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) { }

        if (clientPool != null) clientPool.shutdownNow();

        log("Server stopped");

        startButton.setDisable(false);
        stopButton.setDisable(true);
        portField.setDisable(false);
        statusLabel.setText("STOPPED");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;");
    }

    // -----------------------------------------------------------------
    // Accept loop — runs on its own thread
    // -----------------------------------------------------------------
    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                clientPool.execute(new ClientHandler(client, this));
            } catch (IOException ioe) {
                if (running) {
                    log("Accept error: " + ioe.getMessage());
                }
                break;
            }
        }
    }

    // -----------------------------------------------------------------
    // Helpers used by ClientHandler
    // -----------------------------------------------------------------
    public int getActivePort() {
        return activePort;
    }

    public List<String> getAvailableFiles() {
        List<String> existing = new ArrayList<>();
        for (String name : INVENTORY_FILES) {
            if (new File(name).exists()) existing.add(name);
        }
        return existing;
    }

    public void log(final String msg) {
        Platform.runLater(() -> {
            logArea.appendText(msg + "\n");
            logArea.positionCaret(logArea.getText().length());
        });
    }

    private void showAlert(AlertType type, String title, String content) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.showAndWait();
    }

    private static int digitSum(String id) {
        int s = 0;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c >= '0' && c <= '9') s += (c - '0');
        }
        return s;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

// =====================================================================
// One handler per connected client. Lives in the same file as the
// server.
//
// Wire protocol (text-based, one command per line):
//
//   on connect ->  server pushes
//                       FILES <n>
//                       <filename1>
//                       ...
//                       <filenameN>
//
//   GET <filename> -> server pushes
//                       FILE <lineCount>
//                       <line 1>
//                       ...
//                       END
//
//   OVERVIEW       -> server runs InventoryMerger.merge(...) and pushes
//                       OVERVIEW
//                       AVG <avg>
//                       MAX <price>|<name>
//                       MIN <price>|<name>
//                       TOTAL <count>
//                       END
//
//   VERIFY         -> server pushes
//                       VERIFY <hash>     where hash = (digitSum * activePort) % 1000
//
//   BYE            -> server logs disconnect and closes the socket
//   anything else  -> server pushes  ERROR unknown command
// =====================================================================
class ClientHandler implements Runnable {

    private final Socket          socket;
    private final InventoryServer server;

    public ClientHandler(Socket socket, InventoryServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        String clientIp = socket.getInetAddress().getHostAddress();
        server.log("Client connected from " + clientIp);

        try (BufferedReader in = new BufferedReader(
                                    new InputStreamReader(socket.getInputStream()));
             PrintWriter    out = new PrintWriter(socket.getOutputStream(), true)) {

            List<String> files = server.getAvailableFiles();
            out.println("FILES " + files.size());
            for (String f : files) out.println(f);
            server.log("Sending file list to " + clientIp);

            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("GET ")) {
                    String fname = line.substring(4).trim();
                    handleGet(fname, out, clientIp);

                } else if (line.equals("OVERVIEW")) {
                    handleOverview(out, clientIp);

                } else if (line.equals("VERIFY")) {
                    int hash = (InventoryServer.DIGIT_SUM
                                * server.getActivePort()) % 1000;
                    out.println("VERIFY " + hash);
                    server.log("Sent VERIFY hash " + hash + " to " + clientIp);

                } else if (line.equalsIgnoreCase("BYE")) {
                    break;

                } else {
                    out.println("ERROR unknown command");
                    server.log("Unknown command from " + clientIp + ": " + line);
                }
            }

        } catch (IOException ioe) {
            server.log("Client " + clientIp + " I/O error: " + ioe.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) { }
            server.log("Client disconnected: " + clientIp);
        }
    }

    private void handleGet(String fname, PrintWriter out, String clientIp) {
        List<String> allowed = server.getAvailableFiles();
        if (!allowed.contains(fname)) {
            out.println("ERROR file not found");
            server.log("Client " + clientIp + " requested unknown file: " + fname);
            return;
        }

        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(fname))) {
            String l;
            while ((l = br.readLine()) != null) lines.add(l);
        } catch (IOException ioe) {
            out.println("ERROR " + ioe.getMessage());
            server.log("Error reading " + fname + ": " + ioe.getMessage());
            return;
        }

        out.println("FILE " + lines.size());
        for (String l : lines) out.println(l);
        out.println("END");
        server.log("Client requested file " + fname
                   + " from " + clientIp + " (" + lines.size() + " lines)");
    }

    private void handleOverview(PrintWriter out, String clientIp) {
        server.log("Client " + clientIp + " requested OVERVIEW — running thread pool merge");
        try {
            InventoryMerger.MergeResult r =
                InventoryMerger.merge(server.getAvailableFiles());

            out.println("OVERVIEW");
            out.printf ("AVG %.2f%n", r.avg);
            out.printf ("MAX %.2f|%s%n", r.maxPrice, r.maxName);
            out.printf ("MIN %.2f|%s%n", r.minPrice, r.minName);
            out.println("TOTAL " + r.totalProducts);
            out.println("END");

            server.log("Overview sent to " + clientIp
                       + "  (avg=" + String.format("%.2f", r.avg)
                       + ", max=" + r.maxName
                       + ", min=" + r.minName + ")");
        } catch (Exception ex) {
            out.println("ERROR overview failed: " + ex.getMessage());
            server.log("Overview error: " + ex.getMessage());
        }
    }
}
