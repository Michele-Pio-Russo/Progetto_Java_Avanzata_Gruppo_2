package guesstheword.server.controller;

import guesstheword.server.db.DatabaseManager;
import guesstheword.server.model.GameCoordinator;
import guesstheword.server.model.ServerNetwork;
import guesstheword.server.service.AnalysisService;
import guesstheword.server.service.DocumentAnalyzer;
import guesstheword.server.util.ConfigLoader;

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller JavaFX per l'interfaccia di amministrazione del server.
 */
public class ServerController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(ServerController.class.getName());
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // --- FXML bindings ---
    @FXML private Label statusLabel;
    @FXML private Label portLabel;
    @FXML private Button startServerBtn;
    @FXML private Button stopServerBtn;
    @FXML private Button analyzeBtn;
    @FXML private Button saveAnalysisBtn;
    @FXML private Button loadAnalysisBtn;
    @FXML private ListView<String> documentListView;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private TableView<TFEntry> tfTableView;
    @FXML private TableColumn<TFEntry, String> wordColumn;
    @FXML private TableColumn<TFEntry, Double> freqColumn;
    @FXML private TableView<StatEntry> statsTableView;
    @FXML private TableColumn<StatEntry, String> statUserCol;
    @FXML private TableColumn<StatEntry, Integer> statWinsCol;
    @FXML private TableColumn<StatEntry, Integer> statGamesCol;
    @FXML private TableColumn<StatEntry, Long> statAvgCol;
    @FXML private TextArea logArea;

    // --- Internal state ---
    private final List<File> selectedFiles = new ArrayList<>();
    private DocumentAnalyzer currentAnalyzer;
    private DatabaseManager db;
    private GameCoordinator coordinator;
    private ServerNetwork network;
    private AnalysisService analysisService;
    private int serverPort = 5000;
    private String lastSavePath = "data/analysis.dat";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTfTable();
        setupStatsTable();

        // Carica configurazione
        try {
            ConfigLoader cfg = new ConfigLoader("properties/server.properties");
            serverPort = cfg.getInt("server.port", 5000);
            portLabel.setText(String.valueOf(serverPort));
        } catch (IOException e) {
            log("⚠ server.properties non trovato, uso porta 5000");
        }

        // Init DB
        try {
            db = DatabaseManager.getInstance("db/database.db");
            db.connect();
            log("✅ Database connesso.");
        } catch (Exception e) {
            log("❌ Errore DB: " + e.getMessage());
        }

        // Init coordinator
        coordinator = new GameCoordinator(db, null);
        coordinator.setStatusCallback(msg -> Platform.runLater(() -> log("🎮 " + msg)));

        // Setup AnalysisService
        analysisService = new AnalysisService();
        analysisService.setOnSucceeded(ev -> {
            currentAnalyzer = (DocumentAnalyzer) ev.getSource().getValue();
            coordinator.setAnalyzer(currentAnalyzer);
            populateTfTable(currentAnalyzer.getTermFrequency());
            saveAnalysisBtn.setDisable(false);
            log("✅ Analisi completata: " + currentAnalyzer.getTermFrequency().size() + " parole.");
        });
        analysisService.setOnFailed(ev -> {
            log("❌ Analisi fallita: " + ev.getSource().getException().getMessage());
            progressBar.progressProperty().unbind();
            progressBar.setProgress(0);
        });
        analysisService.messageProperty().addListener((obs, o, n) ->
            Platform.runLater(() -> progressLabel.setText(n)));
        progressBar.progressProperty().bind(analysisService.progressProperty());

        log("🚀 Pannello server pronto.");
    }

    // ----------------------------------------------------------------
    // Server controls
    // ----------------------------------------------------------------

    /**
     * Avvia il ServerSocket.
     */
    @FXML
    private void handleStartServer() {
        try {
            network = new ServerNetwork(serverPort, coordinator);
            network.start();
            statusLabel.setText("🟢 Server attivo sulla porta " + serverPort);
            startServerBtn.setDisable(true);
            stopServerBtn.setDisable(false);
            log("🟢 Server avviato sulla porta " + serverPort);
        } catch (IOException e) {
            log("❌ Impossibile avviare il server: " + e.getMessage());
        }
    }

    /**
     * Ferma il server.
     */
    @FXML
    private void handleStopServer() {
        if (network != null) network.stop();
        statusLabel.setText("⚫ Server fermato");
        startServerBtn.setDisable(false);
        stopServerBtn.setDisable(true);
        log("⏹ Server fermato.");
    }

    // ----------------------------------------------------------------
    // Document & Analysis
    // ----------------------------------------------------------------

    /**
     * Apre il file chooser per selezionare file TXT.
     */
    @FXML
    private void handleSelectFiles() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Seleziona documenti TXT");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("File di testo (*.txt)", "*.txt"));
        fc.setInitialDirectory(new File("documents").exists() ? new File("documents") : new File("."));

        List<File> chosen = fc.showOpenMultipleDialog(documentListView.getScene().getWindow());
        if (chosen != null) {
            selectedFiles.addAll(chosen);
            ObservableList<String> names = FXCollections.observableArrayList();
            selectedFiles.forEach(f -> names.add(f.getName()));
            documentListView.setItems(names);
            log("📂 Selezionati " + chosen.size() + " file.");
        }
    }

    /**
     * Rimuove tutti i file dalla lista.
     */
    @FXML
    private void handleClearFiles() {
        selectedFiles.clear();
        documentListView.getItems().clear();
    }

    /**
     * Avvia l'analisi asincrona dei documenti selezionati.
     */
    @FXML
    private void handleAnalyze() {
        if (selectedFiles.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Nessun documento", "Seleziona almeno un file TXT.");
            return;
        }
        analysisService.setFiles(new ArrayList<>(selectedFiles));
        if (analysisService.isRunning()) analysisService.cancel();
        analysisService.reset();
        analysisService.start();
        analyzeBtn.setDisable(true);
        analysisService.setOnSucceeded(ev -> {
            analyzeBtn.setDisable(false);
            currentAnalyzer = (DocumentAnalyzer) ev.getSource().getValue();
            coordinator.setAnalyzer(currentAnalyzer);
            populateTfTable(currentAnalyzer.getTermFrequency());
            saveAnalysisBtn.setDisable(false);
            log("✅ Analisi completata: " + currentAnalyzer.getTermFrequency().size() + " parole.");
        });
        analysisService.setOnFailed(ev -> {
            analyzeBtn.setDisable(false);
            log("❌ Analisi fallita: " + ev.getSource().getException().getMessage());
        });
        log("▶ Avvio analisi di " + selectedFiles.size() + " file...");
    }

    /**
     * Salva i risultati dell'analisi in formato serializzato.
     */
    @FXML
    private void handleSaveAnalysis() {
        if (currentAnalyzer == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Salva analisi");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Dati analisi (*.dat)", "*.dat"));
        fc.setInitialDirectory(new File("data").exists() ? new File("data") : new File("."));
        fc.setInitialFileName("analysis.dat");
        File f = fc.showSaveDialog(logArea.getScene().getWindow());
        if (f != null) {
            try {
                currentAnalyzer.saveToFile(f.getPath());
                log("💾 Analisi salvata in: " + f.getName());
            } catch (IOException e) {
                log("❌ Errore salvataggio: " + e.getMessage());
            }
        }
    }

    /**
     * Carica un'analisi precedentemente salvata.
     */
    @FXML
    private void handleLoadAnalysis() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Carica analisi");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Dati analisi (*.dat)", "*.dat"));
        fc.setInitialDirectory(new File("data").exists() ? new File("data") : new File("."));
        File f = fc.showOpenDialog(logArea.getScene().getWindow());
        if (f != null) {
            try {
                currentAnalyzer = DocumentAnalyzer.loadFromFile(f.getPath());
                coordinator.setAnalyzer(currentAnalyzer);
                populateTfTable(currentAnalyzer.getTermFrequency());
                saveAnalysisBtn.setDisable(false);
                log("📥 Analisi caricata da: " + f.getName() +
                    " (" + currentAnalyzer.getTermFrequency().size() + " parole)");
            } catch (Exception e) {
                log("❌ Errore caricamento analisi: " + e.getMessage());
            }
        }
    }

    /**
     * Aggiorna la classifica dal DB.
     */
    @FXML
    private void handleRefreshStats() {
        List<DatabaseManager.UserStats> stats = db.getAllStats();
        ObservableList<StatEntry> data = FXCollections.observableArrayList();
        for (DatabaseManager.UserStats s : stats) {
            data.add(new StatEntry(s.username, s.wins, s.gamesPlayed, s.avgResponseMs));
        }
        statsTableView.setItems(data);
        log("🏆 Classifica aggiornata: " + stats.size() + " utenti.");
    }

    /**
     * Pulisce il log.
     */
    @FXML
    private void handleClearLog() {
        logArea.clear();
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private void setupTfTable() {
        wordColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().word));
        freqColumn.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().tf).asObject());
        freqColumn.setCellFactory(col -> new TableCell<TFEntry, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%.6f", item));
            }
        });
    }

    private void setupStatsTable() {
        statUserCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().username));
        statWinsCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().wins).asObject());
        statGamesCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().games).asObject());
        statAvgCol.setCellValueFactory(c -> new SimpleLongProperty(c.getValue().avgMs).asObject());
    }

    private void populateTfTable(Map<String, Double> tf) {
        ObservableList<TFEntry> data = FXCollections.observableArrayList();
        tf.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(500)
            .forEach(e -> data.add(new TFEntry(e.getKey(), e.getValue())));
        Platform.runLater(() -> tfTableView.setItems(data));
    }

    private void log(String msg) {
        String line = "[" + LocalTime.now().format(TIME_FMT) + "] " + msg + "\n";
        Platform.runLater(() -> {
            logArea.appendText(line);
            LOGGER.info(msg);
        });
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    // ----------------------------------------------------------------
    // Inner model classes for TableView
    // ----------------------------------------------------------------

    /** Riga per la tabella TF. */
    public static class TFEntry {
        public final String word;
        public final double tf;
        /** @param word parola; @param tf frequenza */
        public TFEntry(String word, double tf) { this.word = word; this.tf = tf; }
    }

    /** Riga per la tabella statistiche. */
    public static class StatEntry {
        public final String username;
        public final int wins;
        public final int games;
        public final long avgMs;
        /** @param username utente; @param wins vittorie; @param games partite; @param avgMs tempo medio */
        public StatEntry(String u, int w, int g, long a) { username=u; wins=w; games=g; avgMs=a; }
    }
}
