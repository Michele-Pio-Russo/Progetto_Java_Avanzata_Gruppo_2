package guesstheword.client.controller;

import guesstheword.client.service.ServerConnection;
import guesstheword.client.util.ClientConfig;
import guesstheword.common.AuthPayload;
import guesstheword.common.AuthResponse;
import guesstheword.common.Message;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller per la finestra di login e registrazione del client.
 * Gestisce la comunicazione con il server per l'autenticazione.
 */
public class LoginController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());

    @FXML private Label formTitle;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private VBox confirmBox;
    @FXML private PasswordField confirmField;
    @FXML private Label errorLabel;
    @FXML private Button primaryBtn;
    @FXML private Button switchBtn;
    @FXML private Label connectionLabel;

    private boolean isRegistrationMode = false;
    private ServerConnection connection;
    private String serverIp;
    private int serverPort;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Carica config e connettiti
        try {
            ClientConfig cfg = new ClientConfig();
            serverIp = cfg.getServerIp();
            serverPort = cfg.getServerPort();
        } catch (IOException e) {
            serverIp = "127.0.0.1";
            serverPort = 5000;
            LOGGER.warning("client.properties non trovato, uso valori default.");
        }

        connection = new ServerConnection();
        connection.setMessageCallback(this::handleServerMessage);
        connection.setDisconnectCallback(() ->
            Platform.runLater(() -> {
                connectionLabel.setText("❌ Disconnesso dal server");
                showError("Connessione persa. Riavvia l'applicazione.");
            })
        );

        tryConnect();
    }

    private void tryConnect() {
        connectionLabel.setText("⏳ Connessione a " + serverIp + ":" + serverPort + "...");
        Thread t = new Thread(() -> {
            try {
                connection.connect(serverIp, serverPort);
                Platform.runLater(() ->
                    connectionLabel.setText("🟢 Connesso a " + serverIp + ":" + serverPort));
            } catch (IOException e) {
                Platform.runLater(() -> {
                    connectionLabel.setText("❌ Impossibile connettersi al server");
                    showError("Impossibile connettersi a " + serverIp + ":" + serverPort +
                              ". Verifica che il server sia avviato.");
                });
            }
        }, "ConnectThread");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Gestisce il click sul pulsante principale (Login / Registra).
     */
    @FXML
    private void handlePrimary() {
        clearError();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Username e password sono obbligatori.");
            return;
        }

        if (isRegistrationMode) {
            String confirm = confirmField.getText();
            if (!password.equals(confirm)) {
                showError("Le password non coincidono.");
                return;
            }
            connection.send(new Message(Message.Type.REGISTER_REQUEST,
                new AuthPayload(username, password)));
        } else {
            connection.send(new Message(Message.Type.LOGIN_REQUEST,
                new AuthPayload(username, password)));
        }

        primaryBtn.setDisable(true);
    }

    /**
     * Alterna tra modalità login e registrazione.
     */
    @FXML
    private void handleSwitch() {
        isRegistrationMode = !isRegistrationMode;
        clearError();
        if (isRegistrationMode) {
            formTitle.setText("Crea il tuo account");
            primaryBtn.setText("Registrati");
            switchBtn.setText("Hai già un account? Accedi");
            confirmBox.setVisible(true);
            confirmBox.setManaged(true);
        } else {
            formTitle.setText("Accedi al gioco");
            primaryBtn.setText("Accedi");
            switchBtn.setText("Non hai un account? Registrati");
            confirmBox.setVisible(false);
            confirmBox.setManaged(false);
        }
    }

    private void handleServerMessage(Message msg) {
        Platform.runLater(() -> {
            primaryBtn.setDisable(false);
            switch (msg.getType()) {
                case LOGIN_RESPONSE:
                    handleLoginResponse((AuthResponse) msg.getPayload());
                    break;
                case REGISTER_RESPONSE:
                    handleRegisterResponse((AuthResponse) msg.getPayload());
                    break;
                case WAITING:
                    navigateToGame(usernameField.getText().trim(), connection, (String) msg.getPayload());
                    break;
                default:
                    break;
            }
        });
    }

    private void handleLoginResponse(AuthResponse resp) {
        if (resp.isSuccess()) {
            // La navigazione avviene quando arriva WAITING
            connectionLabel.setText("✅ Autenticato come " + usernameField.getText().trim());
        } else {
            showError(resp.getMessage());
        }
    }

    private void handleRegisterResponse(AuthResponse resp) {
        if (resp.isSuccess()) {
            showError(""); // clear
            // Passa automaticamente a modalità login
            isRegistrationMode = false;
            formTitle.setText("Accedi al gioco");
            primaryBtn.setText("Accedi");
            switchBtn.setText("Non hai un account? Registrati");
            confirmBox.setVisible(false);
            confirmBox.setManaged(false);
            errorLabel.setStyle("-fx-text-fill: #3fb950;");
            errorLabel.setText("✅ " + resp.getMessage());
        } else {
            showError(resp.getMessage());
        }
    }

    private void navigateToGame(String username, ServerConnection conn, String waitingMsg) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/game.fxml"));
            Parent root = loader.load();
            GameController gc = loader.getController();
            gc.init(username, conn, waitingMsg);

            Stage stage = (Stage) primaryBtn.getScene().getWindow();
            stage.setScene(new Scene(root, 800, 600));
            stage.setTitle("GuessTheWord — " + username);
            stage.setMinWidth(700);
            stage.setMinHeight(520);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore caricamento game.fxml", e);
            showError("Errore interno: impossibile caricare la finestra di gioco.");
        }
    }

    private void showError(String msg) {
        errorLabel.setStyle("-fx-text-fill: #f85149;");
        errorLabel.setText(msg);
    }

    private void clearError() {
        errorLabel.setText("");
    }
}
