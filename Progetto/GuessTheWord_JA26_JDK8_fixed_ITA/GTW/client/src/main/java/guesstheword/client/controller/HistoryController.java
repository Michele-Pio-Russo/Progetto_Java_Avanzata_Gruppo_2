package guesstheword.client.controller;

import guesstheword.client.service.ServerConnection;
import guesstheword.common.HistoryEntry;
import guesstheword.common.Message;

import javafx.application.Platform;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller JavaFX per la schermata dello storico partite.
 * Richiede al server la lista delle sfide passate e le visualizza
 * in una {@link TableView} con colorazione per esito.
 */
public class HistoryController {

    private static final Logger LOGGER = Logger.getLogger(HistoryController.class.getName());

    @FXML private TableView<HistoryEntry> historyTable;
    @FXML private TableColumn<HistoryEntry, String> dateCol;
    @FXML private TableColumn<HistoryEntry, String> opponentCol;
    @FXML private TableColumn<HistoryEntry, String> outcomeCol;
    @FXML private TableColumn<HistoryEntry, String> wordCol;
    @FXML private TableColumn<HistoryEntry, Long>   timeCol;

    private ServerConnection connection;
    private Stage ownerStage;
    private String username;

    /**
     * Inizializza il controller con i dati della sessione.
     * Configura la tabella, aggiorna la callback dei messaggi e
     * invia la richiesta dello storico al server.
     *
     * @param username   username del giocatore
     * @param connection connessione già aperta con il server
     * @param owner      stage proprietario (per la navigazione di ritorno)
     * @param root       radice della scena già caricata da history.fxml
     *                   dal chiamante (es. {@code GameController})
     */
    public void init(final String username,
                     final ServerConnection connection,
                     final Stage owner,
                     final Parent root) {
        this.username = username;
        this.connection = connection;
        this.ownerStage = owner;

        setupTable();

        // Aggiorna la callback per ricevere HISTORY_RESPONSE
        connection.setMessageCallback(new Consumer<Message>() {
            @Override
            public void accept(Message msg) {
                handleMessage(msg);
            }
        });

        // Richiede lo storico al server
        connection.send(new Message(Message.Type.HISTORY_REQUEST, null));

        // Mostra la scena history già caricata dal chiamante
        owner.setScene(new Scene(root, 820, 500));
        owner.setTitle("GuessTheWord - Storico di " + username);
    }

    /**
     * Configura le colonne della TableView con CellValueFactory lambda (Modulo 4)
     * e CellFactory per la colorazione dell'esito.
     */
    private void setupTable() {
        // Lambda come Callback<CellDataFeatures, ObservableValue> (Modulo 4)
        dateCol.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getPlayedAt()));
        opponentCol.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getOpponent()));
        wordCol.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getCorrectWord()));
        outcomeCol.setCellValueFactory(c ->
            new SimpleStringProperty(formatOutcome(c.getValue().getOutcome())));
        timeCol.setCellValueFactory(c ->
            new SimpleLongProperty(c.getValue().getResponseTimeMs()).asObject());

        // CellFactory per colorare la colonna esito
        outcomeCol.setCellFactory(col -> new TableCell<HistoryEntry, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.startsWith("Vinto")) {
                        setStyle("-fx-text-fill: #3fb950; -fx-font-weight: bold;");
                    } else if (item.startsWith("Perso")) {
                        setStyle("-fx-text-fill: #f85149; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #e3b341; -fx-font-weight: bold;");
                    }
                }
            }
        });

        // CellFactory per la colonna tempo: mostra "—" per valori negativi (timeout)
        timeCol.setCellFactory(col -> new TableCell<HistoryEntry, Long>() {
            @Override
            protected void updateItem(Long item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item < 0 ? "\u2014" : String.valueOf(item));
                }
            }
        });
    }

    /**
     * Gestisce i messaggi dal server. Si aspetta {@link Message.Type#HISTORY_RESPONSE}.
     *
     * @param msg messaggio ricevuto
     */
    @SuppressWarnings("unchecked")
    private void handleMessage(final Message msg) {
        if (msg.getType() == Message.Type.HISTORY_RESPONSE) {
            final List<HistoryEntry> entries = (List<HistoryEntry>) msg.getPayload();
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    ObservableList<HistoryEntry> data =
                        FXCollections.observableArrayList(entries);
                    historyTable.setItems(data);
                }
            });
        }
    }

    /**
     * Torna alla schermata di gioco (pannello di attesa).
     */
    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/game.fxml"));
            Parent root = loader.load();
            GameController gc = loader.getController();
            gc.init(username, connection, "In attesa di un nuovo avversario...");

            ownerStage.setScene(new Scene(root, 820, 620));
            ownerStage.setTitle("GuessTheWord - " + username);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore ritorno alla schermata di gioco", e);
        }
    }

    /**
     * Converte la stringa di esito dal database in un testo leggibile.
     *
     * @param outcome "win", "loss" o "draw"
     * @return stringa formattata in italiano
     */
    private String formatOutcome(String outcome) {
        if (outcome == null) return "\u2014";
        if ("win".equalsIgnoreCase(outcome))  return "Vinto";
        if ("loss".equalsIgnoreCase(outcome)) return "Perso";
        if ("draw".equalsIgnoreCase(outcome)) return "Pareggio";
        return outcome;
    }
}
