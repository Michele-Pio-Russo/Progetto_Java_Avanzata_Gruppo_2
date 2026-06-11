package guesstheword.client.controller;

import guesstheword.client.service.ServerConnection;
import guesstheword.common.ChallengePayload;
import guesstheword.common.ChallengeResult;
import guesstheword.common.Message;

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
 *   <li>{@code resultPane}  — risultato finale</li>
 * </ul>
 * La visibilità e il {@code managed} sono alternati per mostrare
 * uno solo alla volta senza ricreare la scena.</p>
 *
 * <p>Il timer è implementato con {@link Timeline} di JavaFX (Modulo JavaFX corso JA26):
 * rimane nel JavaFX Application Thread ed è thread-safe per la GUI.</p>
 */
public class GameController {

    private static final Logger LOGGER = Logger.getLogger(GameController.class.getName());

    // --- Binding FXML ---
    @FXML private Label playerLabel;
    @FXML private Label stateLabel;

    // Pannello attesa
    @FXML private VBox waitingPane;
    @FXML private Label waitingDetail;

    // Pannello gioco
    @FXML private VBox gamePane;
    @FXML private Label timerLabel;
    @FXML private TextArea textArea;
    @FXML private Label encryptedWordsLabel;
    @FXML private TextField answerField;
    @FXML private Label feedbackLabel;

    // Pannello risultato
    @FXML private VBox resultPane;
    @FXML private Label resultEmoji;
    @FXML private Label resultTitle;
    @FXML private Label resultDetail;

    // --- Stato interno ---
    private String username;
    private ServerConnection connection;
    private Timeline timerTimeline;
    private int remainingSeconds;
    private long challengeStartTime;

    /**
     * Inizializza il controller con i dati della sessione autenticata.
     * Deve essere chiamato dal {@link LoginController} dopo il caricamento FXML.
     *
     * @param username   username del giocatore autenticato
     * @param connection connessione già aperta e autenticata con il server
     * @param waitingMsg messaggio di attesa iniziale da mostrare
     */
    public void init(final String username,
                     final ServerConnection connection,
                     final String waitingMsg) {
        this.username = username;
        this.connection = connection;

        playerLabel.setText("Giocatore: " + username);
        waitingDetail.setText(waitingMsg != null ? waitingMsg
            : "Connesso. In attesa dell'avversario...");

        // Aggiorna la callback dei messaggi: ora gestiamo noi i messaggi di gioco
        connection.setMessageCallback(new Consumer<Message>() {
            @Override
            public void accept(Message msg) {
                handleServerMessage(msg);
            }
        });

        connection.setDisconnectCallback(new Runnable() {
            @Override
            public void run() {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        stateLabel.setText("Disconnesso dal server");
                        showFeedback("Connessione persa con il server.");
                        if (timerTimeline != null) timerTimeline.stop();
                    }
                });
            }
        });

        showWaiting();
    }

    /**
     * Consegna manualmente un messaggio al controller, come se fosse
     * arrivato dal {@link ServerConnection}.
     *
     * <p>Usato da {@link LoginController} per "rigiocare" eventuali messaggi
     * (tipicamente {@code CHALLENGE_START}) ricevuti dal reader thread durante
     * la transizione tra la schermata di login e quella di gioco, prima che
     * questo controller registrasse la propria callback (fix race condition
     * sul secondo client).</p>
     *
     * @param msg messaggio da processare
     */
    public void deliverMessage(Message msg) {
        handleServerMessage(msg);
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
    private void handleServerMessage(final Message msg) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                switch (msg.getType()) {
                    case WAITING:
                        waitingDetail.setText((String) msg.getPayload());
                        showWaiting();
                        break;
                    case CHALLENGE_START:
                        startChallenge((ChallengePayload) msg.getPayload());
                        break;
                    case CHALLENGE_RESULT:
                        showResult((ChallengeResult) msg.getPayload());
                        break;
                    case ERROR:
                        handleServerError((String) msg.getPayload());
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
     * @param payload dati della sfida
     */
    private void startChallenge(ChallengePayload payload) {
        challengeStartTime = System.currentTimeMillis();
        remainingSeconds = payload.getTimeoutSeconds();

        // Popola UI
        textArea.setText(payload.getTextExcerpt());
        List<String> words = payload.getEncryptedWords();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            sb.append(words.get(i));
            if (i < words.size() - 1) sb.append(", ");
        }
        encryptedWordsLabel.setText(sb.toString());

        answerField.clear();
        answerField.setDisable(false);
        feedbackLabel.setText("");

        showGame();
        stateLabel.setText("In gioco!");

        // Avvia il timer countdown con Timeline (Modulo JavaFX)
        startCountdown();
    }

    /**
     * Avvia il timer countdown con {@link Timeline} di JavaFX.
     *
     * <p>{@code Timeline} è la scelta corretta per task periodici nel foreground
     * (aggiornamento GUI): rimane nel FX Application Thread ed è thread-safe
     * (Modulo JavaFX corso JA26 — differenza tra Timeline e ScheduledExecutorService).</p>
     */
    private void startCountdown() {
        if (timerTimeline != null) timerTimeline.stop();

        timerLabel.setText(String.valueOf(remainingSeconds));
        timerLabel.setStyle("-fx-text-fill: #f0f6fc; -fx-font-size: 32px; -fx-font-weight: bold;");

        // KeyFrame eseguito ogni secondo nel FX Application Thread
        timerTimeline = new Timeline(new KeyFrame(Duration.seconds(1),
            new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    remainingSeconds--;
                    timerLabel.setText(String.valueOf(Math.max(0, remainingSeconds)));

                    // Evidenzia in rosso gli ultimi 10 secondi
                    if (remainingSeconds <= 10) {
                        timerLabel.setStyle(
                            "-fx-text-fill: #f85149; -fx-font-size: 32px; -fx-font-weight: bold;");
                    }

                    if (remainingSeconds <= 0) {
                        timerTimeline.stop();
                        answerField.setDisable(true);
                        showFeedback("Tempo scaduto!");
                    }
                }
            }));

        timerTimeline.setCycleCount(remainingSeconds);
        timerTimeline.play();
    }

    /**
     * Mostra il pannello con il risultato finale della sfida.
     *
     * @param result risultato ricevuto dal server
     */
    private void showResult(ChallengeResult result) {
        if (timerTimeline != null) timerTimeline.stop();
        stateLabel.setText("Partita terminata");

        // Usa il metodo dell'enum Outcome (Modulo 2 - enum con comportamento)
        resultEmoji.setText(result.getOutcome().toEmoji());
        resultTitle.setText(result.getOutcome().toDisplayString());

        switch (result.getOutcome()) {
            case WIN:
                resultTitle.setStyle(
                    "-fx-text-fill: #3fb950; -fx-font-size: 26px; -fx-font-weight: bold;");
                resultDetail.setText(
                    "Complimenti! Hai risposto correttamente.\n"
                    + "La parola era: \"" + result.getCorrectWord() + "\"");
                break;
            case LOSS:
                resultTitle.setStyle(
                    "-fx-text-fill: #f85149; -fx-font-size: 26px; -fx-font-weight: bold;");
                String winner = result.getWinnerUsername();
                resultDetail.setText(
                    (winner != null ? winner + " ha risposto prima di te.\n" : "")
                    + "La parola corretta era: \"" + result.getCorrectWord() + "\"");
                break;
            case DRAW:
                resultTitle.setStyle(
                    "-fx-text-fill: #e3b341; -fx-font-size: 26px; -fx-font-weight: bold;");
                resultDetail.setText(
                    "Nessuno ha indovinato entro il tempo limite.\n"
                    + "La parola era: \"" + result.getCorrectWord() + "\"");
                break;
        }

        showResultPane();
    }

    /**
     * Gestisce i messaggi di errore ricevuti dal server durante la sfida.
     *
     * @param errorMsg messaggio di errore
     */
    private void handleServerError(String errorMsg) {
        if (errorMsg != null && errorMsg.contains("disconnesso")) {
            // Avversario disconnesso → torna in attesa dopo 3 secondi
            if (timerTimeline != null) timerTimeline.stop();
            stateLabel.setText("Partita annullata");
            showFeedback("L'avversario si e' disconnesso. In attesa di nuovo avversario...");

            // Timeline one-shot per il ritardo (Modulo JavaFX)
            Timeline delay = new Timeline(new KeyFrame(Duration.seconds(3),
                new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent event) {
                        waitingDetail.setText("In attesa di un nuovo avversario...");
                        showWaiting();
                    }
                }));
            delay.setCycleCount(1);
            delay.play();
        } else {
            showFeedback(errorMsg != null ? errorMsg : "Errore sconosciuto.");
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
    private void handleSubmitAnswer() {
        if (answerField.isDisabled()) return;
        String answer = answerField.getText().trim();
        if (answer.isEmpty()) {
            showFeedback("Inserisci una risposta prima di inviare.");
            return;
        }
        connection.send(new Message(Message.Type.CHALLENGE_ANSWER, answer));
        showFeedback("Risposta inviata: \"" + answer + "\". In attesa di conferma...");
    }

    /**
     * Naviga alla schermata dello storico partite.
     */
    @FXML
    private void handleViewHistory() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/history.fxml"));
            Parent root = loader.load();
            HistoryController hc = loader.getController();
            Stage stage = (Stage) resultPane.getScene().getWindow();
            hc.init(username, connection, stage, root);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore caricamento history.fxml", e);
        }
    }

    /**
     * Rimette il client in lista d'attesa per una nuova partita.
     */
    @FXML
    private void handleNewGame() {
        waitingDetail.setText("In attesa di un nuovo avversario...");
        showWaiting();
        stateLabel.setText("In attesa...");
        // Richiede al server di rientrare in lista d'attesa per una nuova
        // partita: il client e' gia' autenticato sulla stessa connessione,
        // quindi NON va reinviato un LOGIN_REQUEST (payload null -> il server
        // farebbe un cast/NPE su AuthPayload e chiuderebbe la connessione).
        connection.send(new Message(Message.Type.REQUEUE_REQUEST, null));
    }

    // ----------------------------------------------------------------
    // Gestione visibilità pannelli
    // ----------------------------------------------------------------

    /**
     * Mostra il pannello di attesa, nasconde gli altri.
     */
    private void showWaiting() {
        setPaneVisible(waitingPane, true);
        setPaneVisible(gamePane, false);
        setPaneVisible(resultPane, false);
        stateLabel.setText("In attesa...");
    }

    /**
     * Mostra il pannello di gioco, nasconde gli altri.
     */
    private void showGame() {
        setPaneVisible(waitingPane, false);
        setPaneVisible(gamePane, true);
        setPaneVisible(resultPane, false);
    }

    /**
     * Mostra il pannello risultato, nasconde gli altri.
     */
    private void showResultPane() {
        setPaneVisible(waitingPane, false);
        setPaneVisible(gamePane, false);
        setPaneVisible(resultPane, true);
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
    private void showFeedback(String msg) {
        feedbackLabel.setText(msg);
    }
}
