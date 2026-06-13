/**
 * @file ControllerGioco.java
 *
 * @brief Controller JavaFX per la finestra di gioco del client.
 *
 * Gestisce tre pannelli sovrapposti in un StackPane: il pannello di attesa,
 * il pannello di gioco e il pannello del risultato finale.
 * La visibilità e il managed sono alternati per mostrare un solo pannello
 * alla volta senza ricreare la scena.
 * Il timer è implementato con Timeline di JavaFX, che rimane nel
 * JavaFX Application Thread ed è thread-safe per la GUI.
 *
 * @author Gruppo 2
 *
 * @version 1.0.0
 */
package indovinaparola.client.controller;

import indovinaparola.client.service.ConnessioneServer;
import indovinaparola.common.PayloadSfida;
import indovinaparola.common.RisultatoSfida;
import indovinaparola.common.Messaggio;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ControllerGioco {

    private static final Logger LOGGER = Logger.getLogger(ControllerGioco.class.getName()); /// @brief Logger della classe per la registrazione degli errori

    @FXML private Label etichettaGiocatore; /// @brief Label con il nome del giocatore autenticato
    @FXML private Label etichettaStato;     /// @brief Label che mostra lo stato corrente della partita

    @FXML private VBox waitingPane;     /// @brief Pannello mostrato mentre si attende l'avversario
    @FXML private Label dettaglioAttesa; /// @brief Label con il messaggio di dettaglio durante l'attesa

    @FXML private VBox gamePane;                    /// @brief Pannello mostrato durante la sfida
    @FXML private Label etichettaTimer;             /// @brief Label che mostra i secondi rimanenti
    @FXML private TextArea areaTesto;               /// @brief Area di testo con l'estratto da analizzare
    @FXML private Label etichettaParoleCifrate;     /// @brief Label con le parole cifrate da indovinare
    @FXML private TextField campoRisposta;          /// @brief Campo di testo per inserire la risposta
    @FXML private Label etichettaFeedback;          /// @brief Label per i messaggi di feedback all'utente

    @FXML private VBox pannelloRisultato;   /// @brief Pannello mostrato al termine della sfida
    @FXML private Label emojiRisultato;     /// @brief Label con l'emoji rappresentativa dell'esito
    @FXML private Label titoloRisultato;    /// @brief Label con il titolo dell'esito (vittoria, sconfitta, pareggio)
    @FXML private Label dettaglioRisultato; /// @brief Label con i dettagli del risultato finale

    private String nomeUtente;          /// @brief Nome del giocatore autenticato
    private ConnessioneServer connessione; /// @brief Connessione socket attiva con il server
    private Timeline timelineTimer;     /// @brief Timeline JavaFX per il countdown della sfida
    private int secondiRimanenti;       /// @brief Secondi rimanenti nel countdown corrente
    private long tempoInizioSfida;      /// @brief Timestamp di inizio della sfida in millisecondi

    /**
     * @brief Inizializza il controller con i dati della sessione autenticata.
     *
     * Imposta le label iniziali, registra le callback per i messaggi
     * e la disconnessione, e mostra il pannello di attesa.
     * Deve essere chiamato da ControllerAccesso dopo il caricamento FXML.
     *
     * @param[in] nomeUtente  nome del giocatore autenticato
     * @param[in] connessione connessione già aperta e autenticata con il server
     * @param[in] waitingMsg  messaggio di attesa iniziale da mostrare
     */
    public void init(final String nomeUtente, final ConnessioneServer connessione, final String waitingMsg) {
        this.nomeUtente = nomeUtente;
        this.connessione = connessione;

        etichettaGiocatore.setText("Giocatore: " + nomeUtente);
        dettaglioAttesa.setText(waitingMsg != null ? waitingMsg : "Connesso. In attesa dell'avversario...");

        connessione.setMessageCallback(new Consumer<Messaggio>() {
            @Override
            public void accept(Messaggio msg) {
                gestisciMessaggioServer(msg);
            }
        });

        connessione.setDisconnectCallback(new Runnable() {
            @Override
            public void run() {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        etichettaStato.setText("Disconnesso dal server");
                        mostraFeedback("Connessione persa con il server.");
                        if (timelineTimer != null) timelineTimer.stop();
                    }
                });
            }
        });

        mostraAttesa();
    }

    /**
     * @brief Consegna manualmente un messaggio al controller.
     *
<<<<<<< Updated upstream
     * Usato da {@link ControllerAccesso} per "rigiocare" eventuali messaggi
     * (tipicamente {@code CHALLENGE_START}) ricevuti dal reader thread durante
     * la transizione tra la schermata di login e quella di gioco, prima che
     * questo controller registrasse la propria callback (fix race condition
     * sul secondo client).
=======
     * Usato da {@code ControllerAccesso} per riconsegnare eventuali messaggi
     * (tipicamente {@code CHALLENGE_START}) ricevuti durante la transizione
     * tra la schermata di login e quella di gioco, prima che questo controller
     * registrasse la propria callback (fix race condition sul secondo client).
>>>>>>> Stashed changes
     *
     * @param[in] msg messaggio da processare
     */
    public void consegnaMessaggio(Messaggio msg) {
        gestisciMessaggioServer(msg);
    }

    /**
<<<<<<< Updated upstream
     * Smista i messaggi ricevuti dal server al metodo appropriato.
     * Tutti gli aggiornamenti alla GUI passano per {@link Platform#runLater}
     *.
=======
     * @brief Smista i messaggi ricevuti dal server al metodo appropriato.
>>>>>>> Stashed changes
     *
     * Tutti gli aggiornamenti alla GUI passano per {@code Platform.runLater}
     * per garantire la thread safety con il JavaFX Application Thread.
     *
     * @param[in] msg messaggio ricevuto dal server
     */
    private void gestisciMessaggioServer(final Messaggio msg) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                switch (msg.getTipo()) {
                    case WAITING:
                        dettaglioAttesa.setText((String) msg.getCarico());
                        mostraAttesa();
                        break;
                    case CHALLENGE_START:
                        avviaSfida((PayloadSfida) msg.getCarico());
                        break;
                    case CHALLENGE_RESULT:
                        mostraRisultato((RisultatoSfida) msg.getCarico());
                        break;
                    case ERROR:
                        gestisciErroreServer((String) msg.getCarico());
                        break;
                    default:
                        break;
                }
            }
        });
    }

    /**
     * @brief Avvia la sfida ricevuta dal server.
     *
     * Popola l'area di testo e la label delle parole cifrate, abilita
     * il campo di risposta e avvia il timer countdown tramite {@code Timeline}.
     *
     * @param[in] carico dati della sfida ricevuti dal server
     */
    private void avviaSfida(PayloadSfida carico) {
        tempoInizioSfida = System.currentTimeMillis();
        secondiRimanenti = carico.getSecondiTimeout();

        areaTesto.setText(carico.getEstrattoTesto());
        List<String> words = carico.getParoleCifrate();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            sb.append(words.get(i));
            if (i < words.size() - 1) sb.append(", ");
        }
        etichettaParoleCifrate.setText(sb.toString());

        campoRisposta.clear();
        campoRisposta.setDisable(false);
        etichettaFeedback.setText("");

        mostraGioco();
        etichettaStato.setText("In gioco!");

        avviaCountdown();
    }

    /**
     * @brief Avvia il timer countdown con Timeline di JavaFX.
     *
     * Crea una Timeline con un KeyFrame al secondo che decrementa
     * il contatore e aggiorna la label del timer. Gli ultimi 10 secondi vengono
     * evidenziati in rosso. Allo scadere del tempo il campo di risposta viene disabilitato.
     */
    private void avviaCountdown() {
        if (timelineTimer != null) timelineTimer.stop();

        etichettaTimer.setText(String.valueOf(secondiRimanenti));
        etichettaTimer.setStyle("-fx-text-fill: #f0f6fc; -fx-font-size: 32px; -fx-font-weight: bold;");

        timelineTimer = new Timeline(new KeyFrame(Duration.seconds(1),
            new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    secondiRimanenti--;
                    etichettaTimer.setText(String.valueOf(Math.max(0, secondiRimanenti)));

                    if (secondiRimanenti <= 10) {
                        etichettaTimer.setStyle(
                            "-fx-text-fill: #f85149; -fx-font-size: 32px; -fx-font-weight: bold;");
                    }

                    if (secondiRimanenti <= 0) {
                        timelineTimer.stop();
                        campoRisposta.setDisable(true);
                        mostraFeedback("Tempo scaduto!");
                    }
                }
            }));

        timelineTimer.setCycleCount(secondiRimanenti);
        timelineTimer.play();
    }

    /**
     * @brief Mostra il pannello con il risultato finale della sfida.
     *
     * Ferma il timer, aggiorna emoji, titolo e dettaglio in base all'esito
     * (vittoria, sconfitta o pareggio) e mostra il pannello del risultato.
     *
     * @param[in] result risultato della sfida ricevuto dal server
     */
    private void mostraRisultato(RisultatoSfida result) {
        if (timelineTimer != null) timelineTimer.stop();
        campoRisposta.setDisable(true);
        etichettaStato.setText("Partita terminata");

        emojiRisultato.setText(result.getEsito().toEmoji());
        titoloRisultato.setText(result.getEsito().toDisplayString());

        switch (result.getEsito()) {
            case VITTORIA:
                titoloRisultato.setStyle(
                    "-fx-text-fill: #3fb950; -fx-font-size: 26px; -fx-font-weight: bold;");
                dettaglioRisultato.setText(
                    "Complimenti! Hai risposto correttamente.\n"
                    + "La parola era: \"" + result.getParolaCorretta() + "\"");
                break;
            case SCONFITTA:
                titoloRisultato.setStyle(
                    "-fx-text-fill: #f85149; -fx-font-size: 26px; -fx-font-weight: bold;");
                String winner = result.getNomeUtenteVincitore();
                dettaglioRisultato.setText(
                    (winner != null ? winner + " ha risposto prima di te.\n" : "")
                    + "La parola corretta era: \"" + result.getParolaCorretta() + "\"");
                break;
            case PAREGGIO:
                titoloRisultato.setStyle(
                    "-fx-text-fill: #e3b341; -fx-font-size: 26px; -fx-font-weight: bold;");
                dettaglioRisultato.setText(
                    "Nessuno ha indovinato entro il tempo limite.\n"
                    + "La parola era: \"" + result.getParolaCorretta() + "\"");
                break;
        }

        mostraPannelloRisultato();
    }

    /**
     * @brief Gestisce i messaggi di errore ricevuti dal server durante la sfida.
     *
     * Se l'errore segnala la disconnessione dell'avversario, ferma il timer
     * e rimette automaticamente il client in lista d'attesa dopo 3 secondi
     * tramite una {@code Timeline} one-shot. Altrimenti mostra il messaggio
     * di errore nella label di feedback.
     *
     * @param[in] errorMsg messaggio di errore ricevuto dal server
     */
    private void gestisciErroreServer(String errorMsg) {
        if (errorMsg != null && errorMsg.contains("disconnesso")) {
            if (timelineTimer != null) timelineTimer.stop();
            etichettaStato.setText("Partita annullata");
            mostraFeedback("L'avversario si e' disconnesso. In attesa di nuovo avversario...");
            Timeline delay = new Timeline(new KeyFrame(Duration.seconds(3),
                new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent event) {
                        dettaglioAttesa.setText("In attesa di un nuovo avversario...");
                        mostraAttesa();
                        connessione.invia(new Messaggio(Messaggio.Tipo.REQUEUE_REQUEST, null));
                    }
                }));
            delay.setCycleCount(1);
            delay.play();
        } else {
            mostraFeedback(errorMsg != null ? errorMsg : "Errore sconosciuto.");
        }
    }

    /**
     * @brief Invia la risposta dell'utente al server.
     *
     * Legge il contenuto del campo di risposta, verifica che non sia vuoto
     * e invia un messaggio CHALLENGE_ANSWER al server.
     * Collegato al pulsante "Invia" e all'evento onAction del TextField (tasto Invio).
     */
    @FXML
    private void gestisciInvioRisposta() {
        if (campoRisposta.isDisabled()) return;
        String risposta = campoRisposta.getText().trim();
        if (risposta.isEmpty()) {
            mostraFeedback("Inserisci una risposta prima di inviare.");
            return;
        }
        connessione.invia(new Messaggio(Messaggio.Tipo.CHALLENGE_ANSWER, risposta));
        mostraFeedback("Risposta inviata: \"" + risposta + "\". In attesa di conferma...");
    }

    /**
     * @brief Naviga alla schermata dello storico partite.
     *
     * Carica il file history.fxml, inizializza ControllerStorico
     * e sostituisce la scena corrente.
     */
    @FXML
    private void gestisciVisualizzaStorico() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/history.fxml"));
            Parent root = loader.load();
            ControllerStorico hc = loader.getController();
            Stage stage = (Stage) pannelloRisultato.getScene().getWindow();
            hc.init(nomeUtente, connessione, stage, root);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore caricamento history.fxml", e);
        }
    }

    /**
     * @brief Rimette il client in lista d'attesa per una nuova partita.
     *
     * Mostra il pannello di attesa e invia al server un messaggio
     * REQUEUE_REQUEST. Non reinvia le credenziali poiché il client
     * è già autenticato sulla connessione corrente.
     */
    @FXML
    private void gestisciNuovaPartita() {
        dettaglioAttesa.setText("In attesa di un nuovo avversario...");
        mostraAttesa();
        etichettaStato.setText("In attesa...");
        connessione.invia(new Messaggio(Messaggio.Tipo.REQUEUE_REQUEST, null));
    }

    /**
     * @brief Mostra il pannello di attesa e nasconde gli altri.
     */
    private void mostraAttesa() {
        setPaneVisible(waitingPane, true);
        setPaneVisible(gamePane, false);
        setPaneVisible(pannelloRisultato, false);
        etichettaStato.setText("In attesa...");
    }

    /**
     * @brief Mostra il pannello di gioco e nasconde gli altri.
     */
    private void mostraGioco() {
        setPaneVisible(waitingPane, false);
        setPaneVisible(gamePane, true);
        setPaneVisible(pannelloRisultato, false);
    }

    /**
     * @brief Mostra il pannello del risultato e nasconde gli altri.
     */
    private void mostraPannelloRisultato() {
        setPaneVisible(waitingPane, false);
        setPaneVisible(gamePane, false);
        setPaneVisible(pannelloRisultato, true);
    }

    /**
     * @brief Imposta visibilità e managed di un pannello VBox.
     *
     * Impostare managed=false evita che il pannello occupi spazio
     * nel layout anche quando è invisibile.
     *
     * @param[in] pane    pannello da mostrare o nascondere
     * @param[in] visible true per mostrare, false per nascondere
     */
    private void setPaneVisible(VBox pane, boolean visible) {
        pane.setVisible(visible);
        pane.setManaged(visible);
    }

    /**
     * @brief Mostra un messaggio di feedback nella label dedicata.
     *
     * @param[in] msg testo del messaggio da visualizzare
     */
    private void mostraFeedback(String msg) {
        etichettaFeedback.setText(msg);
    }
}