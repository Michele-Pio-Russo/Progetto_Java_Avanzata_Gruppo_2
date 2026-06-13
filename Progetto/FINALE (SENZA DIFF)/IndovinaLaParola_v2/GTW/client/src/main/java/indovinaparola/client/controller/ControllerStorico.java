package indovinaparola.client.controller;

import indovinaparola.client.service.ConnessioneServer;
import indovinaparola.common.VoceStorico;
import indovinaparola.common.Messaggio;

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
public class ControllerStorico {

    private static final Logger LOGGER = Logger.getLogger(ControllerStorico.class.getName());

    @FXML private TableView<VoceStorico> historyTable;
    @FXML private TableColumn<VoceStorico, String> dateCol;
    @FXML private TableColumn<VoceStorico, String> opponentCol;
    @FXML private TableColumn<VoceStorico, String> outcomeCol;
    @FXML private TableColumn<VoceStorico, String> wordCol;
    @FXML private TableColumn<VoceStorico, Long>   timeCol;

    private ConnessioneServer connessione;
    private Stage stageProprietario;
    private String nomeUtente;

    /**
     * Inizializza il controller con i dati della sessione.
     * Configura la tabella, aggiorna la callback dei messaggi e
     * invia la richiesta dello storico al server.
     *
     * @param nomeUtente   nomeUtente del giocatore
     * @param connessione connessione già aperta con il server
     * @param owner      stage proprietario (per la navigazione di ritorno)
     * @param root       radice della scena già caricata da history.fxml
     *                   dal chiamante (es. {@code ControllerGioco})
     */
    public void init(final String nomeUtente,
                     final ConnessioneServer connessione,
                     final Stage owner,
                     final Parent root) {
        this.nomeUtente = nomeUtente;
        this.connessione = connessione;
        this.stageProprietario = owner;

        configuraTabella();

        // Aggiorna la callback per ricevere HISTORY_RESPONSE
        connessione.setMessageCallback(new Consumer<Messaggio>() {
            @Override
            public void accept(Messaggio msg) {
                gestisciMessaggio(msg);
            }
        });

        // Richiede lo storico al server
        connessione.invia(new Messaggio(Messaggio.Tipo.HISTORY_REQUEST, null));

        // Mostra la scena history già caricata dal chiamante
        owner.setScene(new Scene(root, 820, 500));
        owner.setTitle("IndovinaLaParola - Storico di " + nomeUtente);
    }

    /**
     * Configura le colonne della TableView con CellValueFactory lambda
     * e CellFactory per la colorazione dell'esito.
     */
    private void configuraTabella() {
        // Lambda come Callback<CellDataFeatures, ObservableValue>
        dateCol.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getDataPartita()));
        opponentCol.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getAvversario()));
        wordCol.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getParolaCorretta()));
        outcomeCol.setCellValueFactory(c ->
            new SimpleStringProperty(formatOutcome(c.getValue().getEsito())));
        timeCol.setCellValueFactory(c ->
            new SimpleLongProperty(c.getValue().getTempoRispostaMs()).asObject());

        // CellFactory per colorare la colonna esito
        outcomeCol.setCellFactory(col -> new TableCell<VoceStorico, String>() {
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
        timeCol.setCellFactory(col -> new TableCell<VoceStorico, Long>() {
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
     * Gestisce i messaggi dal server. Si aspetta {@link Messaggio.Tipo#HISTORY_RESPONSE}.
     *
     * @param msg messaggio ricevuto
     */
    @SuppressWarnings("unchecked")
    private void gestisciMessaggio(final Messaggio msg) {
        if (msg.getTipo() == Messaggio.Tipo.HISTORY_RESPONSE) {
            final List<VoceStorico> entries = (List<VoceStorico>) msg.getCarico();
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    ObservableList<VoceStorico> data =
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
    private void gestisciIndietro() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/game.fxml"));
            Parent root = loader.load();
            ControllerGioco gc = loader.getController();
            gc.init(nomeUtente, connessione, "In attesa di un nuovo avversario...");

            stageProprietario.setScene(new Scene(root, 820, 620));
            stageProprietario.setTitle("IndovinaLaParola - " + nomeUtente);

            // Notifica il server che il giocatore è di nuovo disponibile
            connessione.invia(new Messaggio(Messaggio.Tipo.REQUEUE_REQUEST, null));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore ritorno alla schermata di gioco", e);
        }
    }

    /**
     * Converte la stringa di esito dal database in un testo leggibile.
     *
     * @param esito "win", "loss" o "draw"
     * @return stringa formattata in italiano
     */
    private String formatOutcome(String esito) {
        if (esito == null) return "\u2014";
        if ("win".equalsIgnoreCase(esito))  return "Vinto";
        if ("loss".equalsIgnoreCase(esito)) return "Perso";
        if ("draw".equalsIgnoreCase(esito)) return "Pareggio";
        return esito;
    }
}
