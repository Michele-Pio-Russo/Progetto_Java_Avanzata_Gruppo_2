package guesstheword.client.controller;

import guesstheword.client.service.ServerConnection;
import guesstheword.common.ChallengePayload;
import guesstheword.common.ChallengeResult;
import guesstheword.common.Message;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller JavaFX per la finestra di gioco del client.
 * Gestisce la visualizzazione della sfida, il timer e l'invio della risposta.
 */
public class GameController {

    private static final Logger LOGGER = Logger.getLogger(GameController.class.getName());

    @FXML private Label playerLabel;
    @FXML private Label stateLabel;
    @FXML private VBox waitingPane;
    @FXML private Label waitingDetail;
    @FXML private VBox gamePane;
    @FXML private Label timerLabel;
    @FXML private TextArea textArea;
    @FXML private Label encryptedWordsLabel;
    @FXML private TextField answerField;
    @FXML private Label feedbackLabel;
    @FXML private VBox resultPane;
    @FXML private Label resultEmoji;
    @FXML private Label resultTitle;
    @FXML private Label resultDetail;

    private String username;
    private ServerConnection connection;
    private Timeline timerTimeline;
    private int remainingSeconds;
    private long challengeStartTime;
    private boolean answerSent = false;

    /**
     * Inizializza il controller con i dati della sessione.
     *
     * @param username   username del giocatore autenticato
     * @param connection connessione socket già aperta con il server
     * @param waitingMsg messaggio di attesa iniziale
     */
    public void init(String username, ServerConnection connection, String waitingMsg) {
        this.username = username;
        this.connection = connection;

        playerLabel.setText("👤 " + username);
        waitingDetail.setText(waitingMsg != null ? waitingMsg :
            "Connesso. La partita inizierà quando entrambi i giocatori sono pronti.");

        connection.setMessageCallback(this::handleServerMessage);
        connection.setDisconnectCallback(() ->
            Platform.runLater(() -> {
                stateLabel.setText("❌ Disconnesso");
                showFeedback("Connessione persa con il server.");
            })
        );

        showWaiting();
    }

    // ----------------------------------------------------------------
    // Message handling
    // ----------------------------------------------------------------

    private void handleServerMessage(Message msg) {
        Platform.runLater(() -> {
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
                    handleError((String) msg.getPayload());
                    break;
                default:
                    break;
            }
        });
    }

    private void startChallenge(ChallengePayload payload) {
        answerSent = false;
        challengeStartTime = System.currentTimeMillis();
        remainingSeconds = payload.getTimeoutSeconds();

        // Popola testo
        textArea.setText(payload.getTextExcerpt());

        // Parole cifrate
        List<String> words = payload.getEncryptedWords();
        encryptedWordsLabel.setText(String.join(", ", words));

        // Pulisci
        answerField.clear();
        feedbackLabel.setText("");
        answerField.setDisable(false);

        showGame();
        stateLabel.setText("⚡ In gioco!");

        // Avvia timer
        startTimer();
    }

    private void startTimer() {
        if (timerTimeline != null) timerTimeline.stop();
        timerLabel.setText(String.valueOf(remainingSeconds));

        timerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            remainingSeconds--;
            timerLabel.setText(String.valueOf(Math.max(0, remainingSeconds)));
            if (remainingSeconds <= 10) {
                timerLabel.setStyle("-fx-text-fill: #f85149; -fx-font-size: 32px; -fx-font-weight: bold;");
            }
            if (remainingSeconds <= 0) {
                timerTimeline.stop();
                answerField.setDisable(true);
                showFeedback("⏰ Tempo scaduto!");
            }
        }));
        timerTimeline.setCycleCount(remainingSeconds);
        timerTimeline.play();
    }

    private void showResult(ChallengeResult result) {
        if (timerTimeline != null) timerTimeline.stop();
        stateLabel.setText("✅ Partita terminata");

        String correctWord = result.getCorrectWord();
        switch (result.getOutcome()) {
            case WIN:
                resultEmoji.setText("🏆");
                resultTitle.setText("Hai vinto!");
                resultTitle.setStyle("-fx-text-fill: #3fb950; -fx-font-size: 28px; -fx-font-weight: bold;");
                resultDetail.setText("Complimenti! Hai indovinato la parola corretta.\n" +
                    "La parola era: \"" + correctWord + "\"");
                break;
            case LOSS:
                resultEmoji.setText("😞");
                resultTitle.setText("Hai perso.");
                resultTitle.setStyle("-fx-text-fill: #f85149; -fx-font-size: 28px; -fx-font-weight: bold;");
                String winner = result.getWinnerUsername();
                resultDetail.setText((winner != null ? winner + " ha risposto prima di te." : "") +
                    "\nLa parola corretta era: \"" + correctWord + "\"");
                break;
            case DRAW:
                resultEmoji.setText("🤝");
                resultTitle.setText("Pareggio!");
                resultTitle.setStyle("-fx-text-fill: #e3b341; -fx-font-size: 28px; -fx-font-weight: bold;");
                resultDetail.setText("Nessuno ha indovinato entro il tempo limite.\n" +
                    "La parola era: \"" + correctWord + "\"");
                break;
        }

        showResultPane();
    }

    private void handleError(String errorMsg) {
        if (errorMsg != null && errorMsg.contains("avversario si è disconnesso")) {
            showFeedback("⚠ " + errorMsg);
            stateLabel.setText("⚠ Partita annullata");
            if (timerTimeline != null) timerTimeline.stop();
            answerField.setDisable(true);
            // Torna alla schermata di attesa dopo 3s
            Timeline delay = new Timeline(new KeyFrame(Duration.seconds(3), e -> showWaiting()));
            delay.setCycleCount(1);
            delay.play();
        } else {
            showFeedback(errorMsg != null ? "❌ " + errorMsg : "Errore sconosciuto.");
        }
    }

    // ----------------------------------------------------------------
    // UI Actions
    // ----------------------------------------------------------------

    /**
     * Invia la risposta al server.
     */
    @FXML
    private void handleSubmitAnswer() {
        if (answerSent || answerField.isDisabled()) return;
        String answer = answerField.getText().trim();
        if (answer.isEmpty()) {
            showFeedback("Inserisci una risposta prima di inviare.");
            return;
        }
        connection.send(new Message(Message.Type.CHALLENGE_ANSWER, answer));
        showFeedback("📤 Risposta inviata: \"" + answer + "\"");
    }

    /**
     * Naviga allo storico partite.
     */
    @FXML
    private void handleViewHistory() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/history.fxml"));
            Parent root = loader.load();
            HistoryController hc = loader.getController();
            hc.init(username, connection, (Stage) resultPane.getScene().getWindow());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore caricamento history.fxml", e);
        }
    }

    /**
     * Rimette il client in attesa per una nuova partita.
     */
    @FXML
    private void handleNewGame() {
        waitingDetail.setText("In attesa di un nuovo avversario...");
        connection.send(new Message(Message.Type.LOGIN_REQUEST, null)); // re-trigger waiting
        // Il server risponderà con WAITING; nel frattempo mostriamo l'attesa
        showWaiting();
        stateLabel.setText("⏳ In attesa...");
    }

    // ----------------------------------------------------------------
    // Pane visibility helpers
    // ----------------------------------------------------------------

    private void showWaiting() {
        setVisible(waitingPane, true);
        setVisible(gamePane, false);
        setVisible(resultPane, false);
        stateLabel.setText("⏳ In attesa...");
    }

    private void showGame() {
        setVisible(waitingPane, false);
        setVisible(gamePane, true);
        setVisible(resultPane, false);
    }

    private void showResultPane() {
        setVisible(waitingPane, false);
        setVisible(gamePane, false);
        setVisible(resultPane, true);
    }

    private void setVisible(VBox pane, boolean visible) {
        pane.setVisible(visible);
        pane.setManaged(visible);
    }

    private void showFeedback(String msg) {
        feedbackLabel.setText(msg);
    }
}
