/**
 * @file ControllerStorico.java
 *
 * @brief Controller JavaFX per la schermata dello storico partite.
 *
 * Richiede al server la lista delle sfide passate e le visualizza
 * in una TableView con colorazione per esito.
 * Le colonne sono configurate tramite CellValueFactory e CellFactory.
 *
 * @author Gruppo 2
 *
 * @version 1.0.0
 */
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

public class ControllerStorico {

    private static final Logger LOGGER = Logger.getLogger(ControllerStorico.class.getName()); /// @brief Logger della classe per la registrazione degli errori

    @FXML private TableView<VoceStorico> historyTable;                  /// @brief Tabella che mostra lo storico delle partite
    @FXML private TableColumn<VoceStorico, String> dateCol;             /// @brief Colonna con la data della partita
    @FXML private TableColumn<VoceStorico, String> opponentCol;         /// @brief Colonna con il nome dell'avversario
    @FXML private TableColumn<VoceStorico, String> outcomeCol;          /// @brief Colonna con l'esito della partita (colorata)
    @FXML private TableColumn<VoceStorico, String> wordCol;             /// @brief Colonna con la parola corretta della sfida
    @FXML private TableColumn<VoceStorico, Long>   timeCol;             /// @brief Colonna con il tempo di risposta in millisecondi

    private ConnessioneServer connessione;  /// @brief Connessione socket attiva con il server
    private Stage stageProprietario;        /// @brief Stage proprietario utilizzato per la navigazione di ritorno
    private String nomeUtente;             /// @brief Nome del giocatore autenticato

    /**
     * @brief Inizializza il controller con i dati della sessione.
     *
     * Configura la tabella, aggiorna la callback dei messaggi e
     * invia la richiesta dello storico al server.
     * Mostra la scena già caricata dal chiamante.
     *
     * @param[in] nomeUtente nome del giocatore autenticato
     * @param[in] connessione connessione già aperta con il server
     * @param[in] owner stage proprietario per la navigazione di ritorno
     * @param[in] root radice della scena già caricata da history.fxml
     */
    public void init(final String nomeUtente, final ConnessioneServer connessione, final Stage owner, final Parent root) {
        this.nomeUtente = nomeUtente;
        this.connessione = connessione;
        this.stageProprietario = owner;

        configuraTabella();

        connessione.setMessageCallback(new Consumer<Messaggio>() {
            @Override
            public void accept(Messaggio msg) {
                gestisciMessaggio(msg);
            }
        });

        connessione.setDisconnectCallback(new Runnable() {
            @Override
            public void run() {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        /// Gestisce la caduta della connessione navigando forzatamente al login
                        tornaAlLogin("Connessione persa con il server. L'applicazione tornera' alla schermata di accesso.");
                    }
                });
            }
        });

        connessione.invia(new Messaggio(Messaggio.Tipo.HISTORY_REQUEST, null));

        owner.setScene(new Scene(root, 820, 500));
        owner.setTitle("IndovinaLaParola - Storico di " + nomeUtente);
    }

    /**
     * @brief Configura le colonne della TableView.
     *
     * Imposta le CellValueFactory per ogni colonna e le CellFactory
     * per la colorazione dell'esito e la formattazione del tempo di risposta.
     * I valori negativi nella colonna tempo indicano timeout e vengono
     * visualizzati come "—".
     */
    private void configuraTabella() {
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
     * @brief Gestisce i messaggi ricevuti dal server.
     *
     * In caso di messaggioHISTORY_RESPONSE, popola la TableView
     * con la lista delle voci ricevute tramite Platform.runLater.
     *
     * @param[in] msg messaggio ricevuto dal server
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
     * @brief Torna alla schermata di gioco nel pannello di attesa.
     *
     * Carica game.fxml, inizializza ControllerGioco e
     * notifica il server che il giocatore è di nuovo disponibile
     * tramite un messaggio REQUEUE_REQUEST.
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

            connessione.invia(new Messaggio(Messaggio.Tipo.REQUEUE_REQUEST, null));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore ritorno alla schermata di gioco", e);
        }
    }

    /**
     * @brief Converte la stringa di esito dal database in un testo leggibile.
     *
     * @param[in] esito stringa di esito ("win", "loss" o "draw")
     * @return stringa formattata in italiano ("Vinto", "Perso", "Pareggio")
     */
    private String formatOutcome(String esito) {
        if (esito == null) return "\u2014";
        if ("win".equalsIgnoreCase(esito))  return "Vinto";
        if ("loss".equalsIgnoreCase(esito)) return "Perso";
        if ("draw".equalsIgnoreCase(esito)) return "Pareggio";
        return esito;
    }

    /**
     * @brief Ritorna alla schermata di accesso e mostra un messaggio di errore.
     * @param[in] motivo il messaggio da mostrare nell'alert
     */
    private void tornaAlLogin(String motivo) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            if (stageProprietario != null) {
                stageProprietario.setScene(new Scene(root));
                stageProprietario.setTitle("IndovinaLaParola");
                stageProprietario.centerOnScreen();
            }

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Disconnessione");
            alert.setHeaderText("Connessione interrotta");
            alert.setContentText(motivo);
            alert.showAndWait();

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore caricamento login.fxml", e);
        }
    }
}