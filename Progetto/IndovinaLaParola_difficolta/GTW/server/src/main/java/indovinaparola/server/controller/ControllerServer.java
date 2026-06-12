package indovinaparola.server.controller;

import indovinaparola.server.db.GestoreDatabase;
import indovinaparola.server.model.CoordinatoreGioco;
import indovinaparola.server.model.ReteServer;
import indovinaparola.server.service.ServizioAnalisi;
import indovinaparola.server.service.AnalizzatoreDocumenti;
import indovinaparola.server.util.CaricatoreConfigurazione;

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
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
 * Controller JavaFX per il pannello di amministrazione del server.
 * Gestisce: avvio/stop del server, selezione e analisi dei documenti,
 * visualizzazione dei risultati TF e della classifica utenti.
 *
 * <p>Implementa {@link Initializable} come da pattern FXML (Modulo JavaFX corso JA26).
 * Tutti gli aggiornamenti alla GUI da thread non-FX passano per {@link Platform#runLater}
 * (Modulo 6 - thread-safety con JavaFX).</p>
 */
public class ControllerServer implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(ControllerServer.class.getName());
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // --- Binding FXML ---
    @FXML private Label statusLabel;
    @FXML private Label portLabel;
    @FXML private ComboBox<String> difficoltaComboBox;
    @FXML private Label descrizioneDifficolta;
    @FXML private Button startServerBtn;
    @FXML private Button stopServerBtn;
    @FXML private Button analyzeBtn;
    @FXML private Button saveAnalysisBtn;
    @FXML private Button loadAnalysisBtn;
    @FXML private Button refreshStatsBtn;
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

    // --- Stato interno ---
    private final List<File> selectedFiles = new ArrayList<>();
    private AnalizzatoreDocumenti currentAnalyzer;
    private GestoreDatabase db;
    private CoordinatoreGioco coordinator;
    private ReteServer network;
    private ServizioAnalisi analysisService;
    private int serverPort = 5000;

    /**
     * Inizializza il controller dopo il caricamento del file FXML.
     * Configura le TableView, carica le properties e connette il database.
     *
     * @param location  URL della risorsa FXML (non usato)
     * @param resources ResourceBundle (non usato)
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTfTable();
        setupStatsTable();

        // Carica configurazione da file .properties
        try {
            CaricatoreConfigurazione cfg = new CaricatoreConfigurazione("properties/server.properties");
            serverPort = cfg.getInt("server.porta", 5000);
        } catch (IOException e) {
            log("ATTENZIONE: server.properties non trovato, uso porta 5000.");
        }
        portLabel.setText(String.valueOf(serverPort));

        // Connessione database
        try {
            db = GestoreDatabase.getInstance("db/database.db");
            db.connetti();
            log("Database connesso.");
        } catch (Exception e) {
            log("ERRORE Database: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Errore connessione DB", e);
        }

        // Creazione coordinatore di gioco
        // Lambda come Consumer<String> (Modulo 4): aggiorna la TextArea log
        coordinator = new CoordinatoreGioco(db, null);
        coordinator.setStatusCallback(new java.util.function.Consumer<String>() {
            @Override
            public void accept(String msg) {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        log("[GIOCO] " + msg);
                    }
                });
            }
        });

        // Setup ServizioAnalisi (JavaFX Service - Modulo JavaFX)
        analysisService = new ServizioAnalisi();

        // Binding dichiarativo: progressBar segue automaticamente il progress del Service
        progressBar.progressProperty().bind(analysisService.progressProperty());

        // Listener su messageProperty per aggiornare la label di progresss
        analysisService.messageProperty().addListener(
            (observable, oldValue, newValue) ->
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        progressLabel.setText(newValue);
                    }
                })
        );

        // Callback onSucceeded del Service (Modulo JavaFX)
        analysisService.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                currentAnalyzer = (AnalizzatoreDocumenti) event.getSource().getValue();
                coordinator.setAnalyzer(currentAnalyzer);
                populateTfTable(currentAnalyzer.getTermFrequency());
                saveAnalysisBtn.setDisable(false);
                analyzeBtn.setDisable(false);
                log("Analisi completata: "
                    + currentAnalyzer.getTermFrequency().size() + " parole.");
            }
        });

        // Callback onFailed del Service
        analysisService.setOnFailed(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                log("ERRORE analisi: " + event.getSource().getException().getMessage());
                analyzeBtn.setDisable(false);
            }
        });

        // Inizializza ComboBox difficoltà
        difficoltaComboBox.setItems(FXCollections.observableArrayList(
            "Facile", "Medio", "Difficile"));
        difficoltaComboBox.getSelectionModel().select(1); // default: Medio
        aggiornaDescrizioneDifficolta(2); // mostra descrizione iniziale

        log("Pannello server pronto. Porta: " + serverPort);
    }

    // ----------------------------------------------------------------
    // Controllo server
    // ----------------------------------------------------------------

    /**
     * Avvia il ServerSocket sulla porta configurata.
     */
    @FXML
    private void handleStartServer() {
        try {
            network = new ReteServer(serverPort, coordinator);
            network.start();
            statusLabel.setText("Server ATTIVO - porta " + serverPort);
            startServerBtn.setDisable(true);
            stopServerBtn.setDisable(false);
            log("Server avviato sulla porta " + serverPort + ".");
        } catch (IOException e) {
            log("ERRORE avvio server: " + e.getMessage());
        }
    }

    /**
     * Ferma il server chiudendo il ServerSocket.
     */
    @FXML
    private void handleStopServer() {
        if (network != null) network.stop();
        statusLabel.setText("Server FERMO");
        startServerBtn.setDisable(false);
        stopServerBtn.setDisable(true);
        log("Server fermato.");
    }

    /**
     * Aggiorna il livello di difficoltà nel coordinatore quando l'admin
     * cambia la selezione nella ComboBox.
     * Il nuovo livello sarà applicato a partire dalla sfida successiva.
     */
    /**
     * Aggiorna il livello di difficoltà nel coordinatore e la descrizione visiva
     * quando l'amministratore cambia la selezione nella ComboBox.
     */
    @FXML
    private void handleDifficoltaChanged() {
        int idx = difficoltaComboBox.getSelectionModel().getSelectedIndex();
        int livello = idx + 1; // idx 0=Facile→1, idx 1=Medio→2, idx 2=Difficile→3
        if (coordinator != null) {
            coordinator.setLivelloDifficolta(livello);
        }
        aggiornaDescrizioneDifficolta(livello);
    }

    /**
     * Aggiorna la label descrittiva affiancata alla ComboBox difficoltà.
     * Mostra i parametri chiave del livello selezionato: numero di parole cifrate,
     * criterio di selezione e durata del timer.
     *
     * @param livello 1=Facile, 2=Medio, 3=Difficile
     */
    private void aggiornaDescrizioneDifficolta(int livello) {
        if (descrizioneDifficolta == null) return;
        final String testo;
        final String stile;
        switch (livello) {
            case 1:
                testo  = "1 parola · comuni · 90s";
                stile  = "-fx-text-fill: #3fb950; -fx-font-weight: bold;"; // verde
                break;
            case 3:
                testo  = "3 parole · rarissime (>=7 lettere) · 40s";
                stile  = "-fx-text-fill: #f85149; -fx-font-weight: bold;"; // rosso
                break;
            default: // 2 = Medio
                testo  = "2 parole · rare · 60s";
                stile  = "-fx-text-fill: #d29922; -fx-font-weight: bold;"; // giallo
                break;
        }
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                descrizioneDifficolta.setText(testo);
                descrizioneDifficolta.setStyle(stile);
            }
        });
    }

    // ----------------------------------------------------------------
    // Documenti e analisi
    // ----------------------------------------------------------------

    /**
     * Apre il FileChooser per selezionare uno o più file .txt da analizzare.
     */
    @FXML
    private void handleSelectFiles() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Seleziona documenti .txt");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("File di testo", "*.txt"));
        File docDir = new File("documents");
        fc.setInitialDirectory(docDir.exists() ? docDir : new File("."));

        List<File> chosen = fc.showOpenMultipleDialog(documentListView.getScene().getWindow());
        if (chosen != null) {
            selectedFiles.addAll(chosen);
            ObservableList<String> names = FXCollections.observableArrayList();
            for (File f : selectedFiles) {
                names.add(f.getName());
            }
            documentListView.setItems(names);
            log("Selezionati " + chosen.size() + " file.");
        }
    }

    /**
     * Rimuove tutti i file dalla lista di selezione.
     */
    @FXML
    private void handleClearFiles() {
        selectedFiles.clear();
        documentListView.getItems().clear();
    }

    /**
     * Avvia l'analisi asincrona dei documenti selezionati tramite {@link ServizioAnalisi}.
     * L'interfaccia rimane reattiva durante l'elaborazione (Modulo JavaFX - Service).
     */
    @FXML
    private void handleAnalyze() {
        if (selectedFiles.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Nessun documento",
                "Seleziona almeno un file .txt prima di avviare l'analisi.");
            return;
        }
        analysisService.setFiles(new ArrayList<>(selectedFiles));
        if (analysisService.isRunning()) {
            analysisService.cancel();
        }
        analysisService.reset();
        analysisService.start();
        analyzeBtn.setDisable(true);
        log("Avvio analisi di " + selectedFiles.size() + " file...");
    }

    /**
     * Salva i risultati dell'analisi corrente in un file serializzato (.dat).
     */
    @FXML
    private void handleSaveAnalysis() {
        if (currentAnalyzer == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Salva analisi");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("File analisi", "*.dat"));
        File dataDir = new File("data");
        fc.setInitialDirectory(dataDir.exists() ? dataDir : new File("."));
        fc.setInitialFileName("analysis.dat");
        File f = fc.showSaveDialog(logArea.getScene().getWindow());
        if (f != null) {
            try {
                currentAnalyzer.saveToFile(f.getPath());
                log("Analisi salvata in: " + f.getName());
            } catch (IOException e) {
                log("ERRORE salvataggio analisi: " + e.getMessage());
            }
        }
    }

    /**
     * Carica un'analisi precedentemente salvata da file .dat.
     */
    @FXML
    private void handleLoadAnalysis() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Carica analisi");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("File analisi", "*.dat"));
        File dataDir = new File("data");
        fc.setInitialDirectory(dataDir.exists() ? dataDir : new File("."));
        File f = fc.showOpenDialog(logArea.getScene().getWindow());
        if (f != null) {
            try {
                currentAnalyzer = AnalizzatoreDocumenti.loadFromFile(f.getPath());
                coordinator.setAnalyzer(currentAnalyzer);
                populateTfTable(currentAnalyzer.getTermFrequency());
                saveAnalysisBtn.setDisable(false);
                log("Analisi caricata: " + currentAnalyzer.getTermFrequency().size() + " parole.");
            } catch (Exception e) {
                log("ERRORE caricamento analisi: " + e.getMessage());
            }
        }
    }

    /**
     * Aggiorna la classifica utenti leggendo le statistiche dal database.
     */
    @FXML
    private void handleRefreshStats() {
        List<GestoreDatabase.UserStats> stats = db.getAllStats();
        ObservableList<StatEntry> data = FXCollections.observableArrayList();
        for (GestoreDatabase.UserStats s : stats) {
            data.add(new StatEntry(s.nomeUtente, s.wins, s.gamesPlayed, s.avgResponseMs));
        }
        statsTableView.setItems(data);
        log("Classifica aggiornata: " + stats.size() + " utenti.");
    }

    /**
     * Pulisce la TextArea del log.
     */
    @FXML
    private void handleClearLog() {
        logArea.clear();
    }

    // ----------------------------------------------------------------
    // Setup tabelle
    // ----------------------------------------------------------------

    /**
     * Configura le colonne della TableView dei risultati Term Frequency.
     * Usa lambda come CellValueFactory (Modulo 4 - lambda in JavaFX).
     */
    private void setupTfTable() {
        // Lambda come Callback<CellDataFeatures, ObservableValue> (Modulo 4)
        wordColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().parola));
        freqColumn.setCellValueFactory(cellData ->
            new SimpleDoubleProperty(cellData.getValue().tf).asObject());
        freqColumn.setCellFactory(col -> new TableCell<TFEntry, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%.6f", item));
            }
        });
    }

    /**
     * Configura le colonne della TableView delle statistiche utenti.
     */
    private void setupStatsTable() {
        statUserCol.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().nomeUtente));
        statWinsCol.setCellValueFactory(c ->
            new SimpleIntegerProperty(c.getValue().wins).asObject());
        statGamesCol.setCellValueFactory(c ->
            new SimpleIntegerProperty(c.getValue().games).asObject());
        statAvgCol.setCellValueFactory(c ->
            new SimpleLongProperty(c.getValue().avgMs).asObject());
    }

    /**
     * Popola la TableView TF con i risultati dell'analisi, ordinati per frequenza decrescente.
     * Mostra al massimo 500 righe per limitare il consumo di memoria.
     *
     * @param tf mappa parola → TF da mostrare
     */
    private void populateTfTable(final Map<String, Double> tf) {
        final ObservableList<TFEntry> data = FXCollections.observableArrayList();

        // Stream per sorting e limit (Modulo 5)
        tf.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(500)
            .forEach(e -> data.add(new TFEntry(e.getKey(), e.getValue())));

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                tfTableView.setItems(data);
            }
        });
    }

    // ----------------------------------------------------------------
    // Utility
    // ----------------------------------------------------------------

    /**
     * Aggiunge una riga timestampata alla TextArea di log.
     *
     * @param msg messaggio da loggare
     */
    private void log(String msg) {
        final String line = "[" + LocalTime.now().format(TIME_FMT) + "] " + msg + "\n";
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                logArea.appendText(line);
            }
        });
        LOGGER.info(msg);
    }

    /**
     * Mostra un dialogo di avviso all'utente.
     *
     * @param tipo    tipo di alert
     * @param title   titolo del dialogo
     * @param content testo del messaggio
     */
    private void showAlert(final Alert.AlertType tipo,
                           final String title, final String content) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                Alert alert = new Alert(tipo);
                alert.setTitle(title);
                alert.setContentText(content);
                alert.showAndWait();
            }
        });
    }

    // ----------------------------------------------------------------
    // Classi interne per TableView (no record → Java 8)
    // ----------------------------------------------------------------

    /**
     * Riga per la TableView dei risultati Term Frequency.
     */
    public static class TFEntry {
        /** Parola. */ public final String parola;
        /** Frequenza relativa (TF). */ public final double tf;

        /**
         * Costruisce una voce TF.
         *
         * @param parola parola
         * @param tf   frequenza relativa
         */
        public TFEntry(String parola, double tf) {
            this.parola = parola;
            this.tf = tf;
        }
    }

    /**
     * Riga per la TableView della classifica utenti.
     */
    public static class StatEntry {
        /** Username. */      public final String  nomeUtente;
        /** Vittorie. */      public final int     wins;
        /** Partite totali. */public final int     games;
        /** Tempo medio ms. */public final long    avgMs;

        /**
         * Costruisce una voce della classifica.
         *
         * @param nomeUtente nomeUtente
         * @param wins     vittorie
         * @param games    partite totali
         * @param avgMs    tempo medio risposta in ms
         */
        public StatEntry(String nomeUtente, int wins, int games, long avgMs) {
            this.nomeUtente = nomeUtente;
            this.wins = wins;
            this.games = games;
            this.avgMs = avgMs;
        }
    }
}
