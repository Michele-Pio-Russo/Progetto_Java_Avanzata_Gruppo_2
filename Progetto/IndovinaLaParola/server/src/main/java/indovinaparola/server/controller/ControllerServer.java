/**
 * @file ControllerServer.java
 * @brief Controller JavaFX per il pannello di amministrazione del server.
 *
 * Questa classe implementa Initializable e funge da controller per l'interfaccia JavaFX del server. 
 * Contiene i metodi di gestione degli eventi (handler) associati ai vari componenti dell'interfaccia, 
 * come i pulsanti per l'avvio del server e l'analisi dei file.
 *
 * @author Gruppo 2
 * @date 
 * @version 1.0.0
 */
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
 * @brief Controller JavaFX per il pannello di amministrazione del server.
 * @brief Gestisce: avvio/stop del server, selezione e analisi dei documenti,
 * visualizzazione dei risultati TF e della classifica utenti.
 *
 * Implementa {@link Initializable} come da pattern FXML.
 * Tutti gli aggiornamenti alla GUI da thread non-FX passano per {@link Platform#runLater}
 *.
 */
public class ControllerServer implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(ControllerServer.class.getName()); ///< Logger della classe ControllerServer
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss"); ///< Formattatore per i timestamp dei log

    // --- Binding FXML ---
    @FXML private Label statusLabel;
    @FXML private Label portLabel;
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
    private final List<File> selectedFiles = new ArrayList<>(); ///< Lista dei file selezionati per l'analisi
    private AnalizzatoreDocumenti currentAnalyzer; ///< Istanza corrente dell'analizzatore documenti
    private GestoreDatabase db; ///< Gestore del database
    private CoordinatoreGioco coordinator; ///< Coordinatore della logica di gioco
    private ReteServer network;
    private ServizioAnalisi analysisService; ///< Servizio asincrono per eseguire l'analisi
    private int serverPort = 5000;

    /**
     * @brief Inizializza il controller dopo il caricamento del file FXML.
     * @brief Configura le TableView, carica le properties e connette il database.
     *
     * @pre I componenti grafici definiti nell'FXML devono essere stati iniettati correttamente.
     * @post Il database è connesso, le tabelle configurate e il server è pronto all'uso.
     *
     * @param[in] location  URL della risorsa FXML (non usato)
     * @param[in] resources ResourceBundle (non usato)
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTfTable();
        setupStatsTable();

        // Carica configurazione da file .properties
        int gameTimeout = 60;
        int excerptWords = 60;
        try {
            CaricatoreConfigurazione cfg = new CaricatoreConfigurazione("properties/server.properties");
            serverPort   = cfg.getInt("server.port", 5000);
            gameTimeout  = cfg.getInt("game.timeout.seconds", 60);
            excerptWords = cfg.getInt("game.excerpt.words", 60);
        } catch (IOException e) {
            log("ATTENZIONE: server.properties non trovato, uso valori di default.");
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
        // Lambda come Consumer<String>: aggiorna la TextArea log
        coordinator = new CoordinatoreGioco(db, null);
        coordinator.setGameConfig(gameTimeout, excerptWords);
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

        // Callback onSucceeded del Service
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

        log("Pannello server pronto. Porta: " + serverPort);
    }


    /**
     * @brief Avvia il ServerSocket sulla porta configurata.
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
     * @brief Ferma il server chiudendo il ServerSocket.
     */
    @FXML
    private void handleStopServer() {
        if (network != null) network.stop();
        /// Chiude forzatamente anche tutte le connessioni attive dei client
        if (coordinator != null) coordinator.disconnettiTutti();
        statusLabel.setText("Server FERMO");
        startServerBtn.setDisable(false);
        stopServerBtn.setDisable(true);
        log("Server fermato.");
    }


    /**
     * @brief Apre il FileChooser per selezionare uno o più file .txt da analizzare.
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
     * @brief Rimuove tutti i file dalla lista di selezione.
     */
    @FXML
    private void handleClearFiles() {
        selectedFiles.clear();
        documentListView.getItems().clear();
    }

    /**
     * @brief Avvia l'analisi asincrona dei documenti selezionati tramite {@link ServizioAnalisi}.
     * @brief L'interfaccia rimane reattiva durante l'elaborazione.
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
     * @brief Salva i risultati dell'analisi corrente in un file serializzato (.dat).
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
     * @brief Carica un'analisi precedentemente salvata da file .dat.
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
     * @brief Aggiorna la classifica utenti leggendo le statistiche dal database.
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
     * @brief Pulisce la TextArea del log.
     */
    @FXML
    private void handleClearLog() {
        logArea.clear();
    }


    /**
     * @brief Configura le colonne della TableView dei risultati Term Frequency.
     * @brief Usa lambda come CellValueFactory.
     */
    private void setupTfTable() {
        // Lambda come Callback<CellDataFeatures, ObservableValue>
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
     * @brief Configura le colonne della TableView delle statistiche utenti.
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
     * @brief Popola la TableView TF con i risultati dell'analisi, ordinati per frequenza decrescente.
     * @brief Mostra al massimo 500 righe per limitare il consumo di memoria.
     *
     * @param[in] tf mappa parola → TF da mostrare
     */
    private void populateTfTable(final Map<String, Double> tf) {
        final ObservableList<TFEntry> data = FXCollections.observableArrayList();

        // Stream per sorting e limit
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


    /**
     * @brief Aggiunge una riga timestampata alla TextArea di log.
     *
     * @param[in] msg messaggio da loggare
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
     * @brief Mostra un dialogo di avviso all'utente.
     *
     * @param[in] tipo    tipo di alert
     * @param[in] title   titolo del dialogo
     * @param[in] content testo del messaggio
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


    /**
     * @brief Riga per la TableView dei risultati Term Frequency.
     */
    public static class TFEntry {
        /** @brief Parola. */ public final String parola;
        /** @brief Frequenza relativa (TF). */ public final double tf;

        /**
         * @brief Costruisce una voce TF.
         *
         * @param[in] parola parola
         * @param[in] tf   frequenza relativa
         */
        public TFEntry(String parola, double tf) {
            this.parola = parola;
            this.tf = tf;
        }
    }

    /**
     * @brief Riga per la TableView della classifica utenti.
     */
    public static class StatEntry {
        /** @brief Username. */      public final String  nomeUtente;
        /** @brief Vittorie. */      public final int     wins;
        /** @brief Partite totali. */public final int     games;
        /** @brief Tempo medio ms. */public final long    avgMs;

        /**
         * @brief Costruisce una voce della classifica.
         *
         * @param[in] nomeUtente nomeUtente
         * @param[in] wins     vittorie
         * @param[in] games    partite totali
         * @param[in] avgMs    tempo medio risposta in ms
         */
        public StatEntry(String nomeUtente, int wins, int games, long avgMs) {
            this.nomeUtente = nomeUtente;
            this.wins = wins;
            this.games = games;
            this.avgMs = avgMs;
        }
    }
}
