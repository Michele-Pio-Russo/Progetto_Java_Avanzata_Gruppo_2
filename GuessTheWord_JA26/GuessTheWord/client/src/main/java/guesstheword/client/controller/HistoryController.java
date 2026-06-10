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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller JavaFX per la finestra dello storico partite.
 * Richiede al server la lista delle sfide giocate e le visualizza in tabella.
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
     * Inizializza il controller e richiede lo storico al server.
     *
     * @param username   username del giocatore
     * @param connection connessione già aperta con il server
     * @param owner      stage proprietario (per il ritorno al gioco)
     */
    public void init(String username, ServerConnection connection, Stage owner) {
        this.username = username;
        this.connection = connection;
        this.ownerStage = owner;

        setupTable();

        // Override temporaneo della callback per ricevere HISTORY_RESPONSE
        connection.setMessageCallback(this::handleMessage);

        // Richiedi lo storico
        connection.send(new Message(Message.Type.HISTORY_REQUEST, null));

        // Carica la scena history
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/history.fxml"));
            // Già caricato — aggiorna solo lo stage
            owner.setTitle("GuessTheWord — Storico di " + username);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Errore aggiornamento titolo stage", e);
        }
    }

    private void setupTable() {
        dateCol.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getPlayedAt()));
        opponentCol.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getOpponent()));
        outcomeCol.setCellValueFactory(c ->
            new SimpleStringProperty(formatOutcome(c.getValue().getOutcome())));
        wordCol.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getCorrectWord()));
        timeCol.setCellValueFactory(c ->
            new SimpleLongProperty(c.getValue().getResponseTimeMs()).asObject());

        // Colora la colonna esito
        outcomeCol.setCellFactory(col -> new TableCell<HistoryEntry, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.contains("Vinto")) {
                        setStyle("-fx-text-fill: #3fb950; -fx-font-weight: bold;");
                    } else if (item.contains("Perso")) {
                        setStyle("-fx-text-fill: #f85149; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #e3b341; -fx-font-weight: bold;");
                    }
                }
            }
        });

        // Colonna tempo: mostra "-" per timeout
        timeCol.setCellFactory(col -> new TableCell<HistoryEntry, Long>() {
            @Override
            protected void updateItem(Long item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(item < 0 ? "—" : String.valueOf(item));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(Message msg) {
        if (msg.getType() == Message.Type.HISTORY_RESPONSE) {
            List<HistoryEntry> entries = (List<HistoryEntry>) msg.getPayload();
            Platform.runLater(() -> {
                ObservableList<HistoryEntry> data = FXCollections.observableArrayList(entries);
                historyTable.setItems(data);
            });
        }
    }

    /**
     * Torna alla finestra di gioco.
     */
    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/game.fxml"));
            Parent root = loader.load();
            GameController gc = loader.getController();
            gc.init(username, connection, "In attesa di un nuovo avversario...");

            ownerStage.setScene(new Scene(root, 800, 600));
            ownerStage.setTitle("GuessTheWord — " + username);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore ritorno alla finestra di gioco", e);
        }
    }

    private String formatOutcome(String outcome) {
        if (outcome == null) return "—";
        switch (outcome.toLowerCase()) {
            case "win":  return "🏆 Vinto";
            case "loss": return "😞 Perso";
            case "draw": return "🤝 Pareggio";
            default:     return outcome;
        }
    }
}
