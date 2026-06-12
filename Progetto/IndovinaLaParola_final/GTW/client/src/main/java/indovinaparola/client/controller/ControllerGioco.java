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

/**
 * Controller JavaFX per la finestra di gioco del client.
 *
 * <p>Gestisce tre pannelli sovrapposti in un {@code StackPane}:
 * <ul>
 *   <li>{@code waitingPane} — in attesa dell'avversario</li>
 *   <li>{@code gamePane}    — sfida in corso</li>
 *   <li>{@code pannelloRisultato}  — risultato finale</li>
 * </ul>
 * La visibilità e il {@code managed} sono alternati per mostrare
 * uno solo alla volta senza ricreare la scena.</p>
 *
 * <p>Il timer è implementato con {@link Timeline} di JavaFX (Modulo JavaFX corso JA26):
 * rimane nel JavaFX Application Thread ed è thread-safe per la GUI.</p>
 */
public class ControllerGioco {

    private static final Logger LOGGER = Logger.getLogger(ControllerGioco.class.getName());

    // --- Binding FXML ---
    @FXML private Label etichettaGiocatore;
    @FXML private Label etichettaStato;

    // Pannello attesa
    @FXML private VBox waitingPane;
    @FXML private Label dettaglioAttesa;

    // Pannello gioco
    @FXML private VBox gamePane;
    @FXML private Label etichettaTimer;
    @FXML private TextArea areaTesto;
    @FXML private Label etichettaParoleCifrate;
    @FXML private TextField campoRisposta;
    @FXML private Label etichettaFeedback;

    // Pannello risultato
    @FXML private VBox pannelloRisultato;
    @FXML private Label emojiRisultato;
    @FXML private Label titoloRisultato;
    @FXML private Label dettaglioRisultato;

    // --- Stato interno ---
    private String nomeUtente;
    private ConnessioneServer connessione;
    private Timeline timelineTimer;
    private int secondiRimanenti;
    private long tempoInizioSfida;

    /**
     * Inizializza il controller con i dati della sessione autenticata.
     * Deve essere chiamato dal {@link ControllerAccesso} dopo il caricamento FXML.
     *
     * @param nomeUtente   nomeUtente del giocatore autenticato
     * @param connessione connessione già aperta e autenticata con il server
     * @param waitingMsg messaggio di attesa iniziale da mostrare
     */
    public void init(final String nomeUtente,
                     final ConnessioneServer connessione,
                     final String waitingMsg) {
        this.nomeUtente = nomeUtente;
        this.connessione = connessione;

        etichettaGiocatore.setText("Giocatore: " + nomeUtente);
        dettaglioAttesa.setText(waitingMsg != null ? waitingMsg
            : "Connesso. In attesa dell'avversario...");

        // Aggiorna la callback dei messaggi: ora gestiamo noi i messaggi di gioco
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
     * Consegna manualmente un messaggio al controller, come se fosse
     * arrivato dal {@link ConnessioneServer}.
     *
     * <p>Usato da {@link ControllerAccesso} per "rigiocare" eventuali messaggi
     * (tipicamente {@code CHALLENGE_START}) ricevuti dal reader thread durante
     * la transizione tra la schermata di login e quella di gioco, prima che
     * questo controller registrasse la propria callback (fix race condition
     * sul secondo client).</p>
     *
     * @param msg messaggio da processare
     */
    public void consegnaMessaggio(Messaggio msg) {
        gestisciMessaggioServer(msg);
    }

    // ----------------------------------------------------------------
    // Gestione messaggi dal server
    // ----------------------------------------------------------------

    /**
     * Smista i messaggi ricevuti dal server al metodo appropriato.
     * Tutti gli aggiornamenti alla GUI passano per {@link Platform#runLater}
     * (Modulo 6 - thread safety con JavaFX).
     *
     * @param msg messaggio ricevuto dal server
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
     * Avvia la sfida ricevuta dal server.
     * Popola il testo e la parola cifrata, imposta il timer con {@link Timeline}.
     *
     * @param carico dati della sfida
     */
    private void avviaSfida(PayloadSfida carico) {
        tempoInizioSfida = System.currentTimeMillis();
        secondiRimanenti = carico.getSecondiTimeout();

        // Popola UI
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

        // Avvia il timer countdown con Timeline (Modulo JavaFX)
        avviaCountdown();
    }

    /**
     * Avvia il timer countdown con {@link Timeline} di JavaFX.
     *
     * <p>{@code Timeline} è la scelta corretta per task periodici nel foreground
     * (aggiornamento GUI): rimane nel FX Application Thread ed è thread-safe
     * (Modulo JavaFX corso JA26 — differenza tra Timeline e ScheduledExecutorService).</p>
     */
    private void avviaCountdown() {
        if (timelineTimer != null) timelineTimer.stop();

        etichettaTimer.setText(String.valueOf(secondiRimanenti));
        etichettaTimer.setStyle("-fx-text-fill: #f0f6fc; -fx-font-size: 32px; -fx-font-weight: bold;");

        // KeyFrame eseguito ogni secondo nel FX Application Thread
        timelineTimer = new Timeline(new KeyFrame(Duration.seconds(1),
            new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    secondiRimanenti--;
                    etichettaTimer.setText(String.valueOf(Math.max(0, secondiRimanenti)));

                    // Evidenzia in rosso gli ultimi 10 secondi
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
     * Mostra il pannello con il risultato finale della sfida.
     *
     * @param result risultato ricevuto dal server
     */
    private void mostraRisultato(RisultatoSfida result) {
        if (timelineTimer != null) timelineTimer.stop();
        etichettaStato.setText("Partita terminata");

        // Usa il metodo dell'enum Esito (Modulo 2 - enum con comportamento)
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
     * Gestisce i messaggi di errore ricevuti dal server durante la sfida.
     *
     * @param errorMsg messaggio di errore
     */
    private void gestisciErroreServer(String errorMsg) {
        if (errorMsg != null && errorMsg.contains("disconnesso")) {
            // Avversario disconnesso → torna in attesa dopo 3 secondi
            if (timelineTimer != null) timelineTimer.stop();
            etichettaStato.setText("Partita annullata");
            mostraFeedback("L'avversario si e' disconnesso. In attesa di nuovo avversario...");

            // Timeline one-shot per il ritardo (Modulo JavaFX)
            Timeline delay = new Timeline(new KeyFrame(Duration.seconds(3),
                new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent event) {
                        dettaglioAttesa.setText("In attesa di un nuovo avversario...");
                        mostraAttesa();
                    }
                }));
            delay.setCycleCount(1);
            delay.play();
        } else {
            mostraFeedback(errorMsg != null ? errorMsg : "Errore sconosciuto.");
        }
    }

    // ----------------------------------------------------------------
    // Azioni FXML
    // ----------------------------------------------------------------

    /**
     * Invia la risposta al server.
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
     * Naviga alla schermata dello storico partite.
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
     * Rimette il client in lista d'attesa per una nuova partita.
     */
    @FXML
    private void gestisciNuovaPartita() {
        dettaglioAttesa.setText("In attesa di un nuovo avversario...");
        mostraAttesa();
        etichettaStato.setText("In attesa...");
        // Richiede al server di rientrare in lista d'attesa per una nuova
        // partita: il client e' gia' autenticato sulla stessa connessione,
        // quindi NON va reinviato un LOGIN_REQUEST (carico null -> il server
        // farebbe un cast/NPE su PayloadAutenticazione e chiuderebbe la connessione).
        connessione.invia(new Messaggio(Messaggio.Tipo.REQUEUE_REQUEST, null));
    }

    // ----------------------------------------------------------------
    // Gestione visibilità pannelli
    // ----------------------------------------------------------------

    /**
     * Mostra il pannello di attesa, nasconde gli altri.
     */
    private void mostraAttesa() {
        setPaneVisible(waitingPane, true);
        setPaneVisible(gamePane, false);
        setPaneVisible(pannelloRisultato, false);
        etichettaStato.setText("In attesa...");
    }

    /**
     * Mostra il pannello di gioco, nasconde gli altri.
     */
    private void mostraGioco() {
        setPaneVisible(waitingPane, false);
        setPaneVisible(gamePane, true);
        setPaneVisible(pannelloRisultato, false);
    }

    /**
     * Mostra il pannello risultato, nasconde gli altri.
     */
    private void mostraPannelloRisultato() {
        setPaneVisible(waitingPane, false);
        setPaneVisible(gamePane, false);
        setPaneVisible(pannelloRisultato, true);
    }

    /**
     * Imposta visibilità e managed di un pannello VBox.
     * Impostare anche managed=false evita che il pannello occupi spazio
     * nel layout anche quando è invisible.
     *
     * @param pane    pannello da mostrare/nascondere
     * @param visible true per mostrare, false per nascondere
     */
    private void setPaneVisible(VBox pane, boolean visible) {
        pane.setVisible(visible);
        pane.setManaged(visible);
    }

    /**
     * Mostra un messaggio di feedback nella label dedicata.
     *
     * @param msg messaggio da mostrare
     */
    private void mostraFeedback(String msg) {
        etichettaFeedback.setText(msg);
    }
}
