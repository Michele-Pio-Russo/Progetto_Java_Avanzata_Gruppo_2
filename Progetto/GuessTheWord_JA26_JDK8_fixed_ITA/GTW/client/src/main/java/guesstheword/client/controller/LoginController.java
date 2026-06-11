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
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller JavaFX per la schermata di login e registrazione del client.
 *
 * <p>Gestisce la connessione iniziale al server in un thread separato
 * (non blocca il JavaFX Application Thread), la modalità alternata
 * login/registrazione e la navigazione alla schermata di gioco.</p>
 *
 * <p>La callback dei messaggi usa {@link Consumer}&lt;{@link Message}&gt;
 * come interfaccia funzionale assegnata tramite anonymous class Java 8
 * (Modulo 4 - Lambda corso JA26).</p>
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

    /**
     * Inizializza il controller dopo il caricamento FXML.
     * Carica le properties e tenta la connessione al server.
     *
     * @param location  URL della risorsa (non usato)
     * @param resources ResourceBundle (non usato)
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        ClientConfig cfg = new ClientConfig();
        serverIp = cfg.getServerIp();
        serverPort = cfg.getServerPort();

        connection = new ServerConnection();

        // Consumer<Message> assegnato con lambda (Modulo 4)
        connection.setMessageCallback(new Consumer<Message>() {
            @Override
            public void accept(Message msg) {
                handleServerMessage(msg);
            }
        });

        // Runnable per la disconnessione (Modulo 4 - interfaccia funzionale)
        connection.setDisconnectCallback(new Runnable() {
            @Override
            public void run() {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        connectionLabel.setText("Disconnesso dal server.");
                        showError("Connessione persa. Riavvia l'applicazione.");
                    }
                });
            }
        });

        tryConnect();
    }

    /**
     * Tenta la connessione al server in un thread separato
     * per non bloccare il JavaFX Application Thread.
     */
    private void tryConnect() {
        connectionLabel.setText("Connessione a " + serverIp + ":" + serverPort + "...");

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    connection.connect(serverIp, serverPort);
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            connectionLabel.setText(
                                "Connesso a " + serverIp + ":" + serverPort);
                        }
                    });
                } catch (IOException e) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            connectionLabel.setText("Impossibile connettersi al server.");
                            showError("Server non raggiungibile su "
                                + serverIp + ":" + serverPort
                                + ". Verifica che il server sia avviato.");
                        }
                    });
                }
            }
        }, "ConnectThread");
        t.setDaemon(true);
        t.start();
    }

    // ----------------------------------------------------------------
    // Gestione messaggi dal server
    // ----------------------------------------------------------------

    /**
     * Smista i messaggi ricevuti dal server al metodo appropriato.
     * Tutti gli aggiornamenti alla GUI passano per {@link Platform#runLater}.
     *
     * @param msg messaggio ricevuto
     */
    private void handleServerMessage(final Message msg) {
        // ATTENZIONE - fix race condition (vedi navigateToGame):
        // se il messaggio e' WAITING, il client potrebbe ricevere SUBITO DOPO
        // (sullo stesso reader thread) anche un CHALLENGE_START, prima ancora
        // che il thread JavaFX esegua la navigazione verso game.fxml e
        // GameController possa registrare il proprio messageCallback.
        // In tal caso il CHALLENGE_START arriverebbe ancora qui e verrebbe
        // perso (default: break). Per evitarlo, appena riceviamo WAITING
        // sostituiamo SUBITO (sul reader thread, in modo sincrono) la callback
        // con un buffer che accoda eventuali messaggi successivi, da
        // "riconsegnare" a GameController una volta inizializzato.
        if (msg.getType() == Message.Type.WAITING) {
            final java.util.Queue<Message> pending = new java.util.concurrent.ConcurrentLinkedQueue<>();
            connection.setMessageCallback(new Consumer<Message>() {
                @Override
                public void accept(Message m) {
                    pending.add(m);
                }
            });
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    primaryBtn.setDisable(false);
                    navigateToGame(
                        usernameField.getText().trim(),
                        connection,
                        (String) msg.getPayload(),
                        pending);
                }
            });
            return;
        }

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                primaryBtn.setDisable(false);
                switch (msg.getType()) {
                    case LOGIN_RESPONSE:
                        handleLoginResponse((AuthResponse) msg.getPayload());
                        break;
                    case REGISTER_RESPONSE:
                        handleRegisterResponse((AuthResponse) msg.getPayload());
                        break;
                    case ERROR:
                        showError((String) msg.getPayload());
                        break;
                    default:
                        break;
                }
            }
        });
    }

    /**
     * Gestisce la risposta del server al login.
     *
     * @param resp risposta autenticazione
     */
    private void handleLoginResponse(AuthResponse resp) {
        if (resp.isSuccess()) {
            connectionLabel.setText("Autenticato come: " + usernameField.getText().trim());
            clearError();
            // La navigazione avviene quando arriva il messaggio WAITING
        } else {
            showError(resp.getMessage());
        }
    }

    /**
     * Gestisce la risposta del server alla registrazione.
     *
     * @param resp risposta registrazione
     */
    private void handleRegisterResponse(AuthResponse resp) {
        if (resp.isSuccess()) {
            // Torna in modalità login con messaggio di successo
            isRegistrationMode = false;
            formTitle.setText("Accedi al gioco");
            primaryBtn.setText("Accedi");
            switchBtn.setText("Non hai un account? Registrati");
            confirmBox.setVisible(false);
            confirmBox.setManaged(false);
            errorLabel.setStyle("-fx-text-fill: #3fb950;");
            errorLabel.setText(resp.getMessage() + " Ora accedi.");
        } else {
            showError(resp.getMessage());
        }
    }

    // ----------------------------------------------------------------
    // Azioni FXML
    // ----------------------------------------------------------------

    /**
     * Gestisce il click sul pulsante principale (Accedi o Registrati).
     */
    @FXML
    private void handlePrimary() {
        clearError();
        final String username = usernameField.getText().trim();
        final String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Username e password sono obbligatori.");
            return;
        }

        if (!connection.isConnected()) {
            showError("Non connesso al server. Attendi o riavvia.");
            return;
        }

        if (isRegistrationMode) {
            final String confirm = confirmField.getText();
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
     * Alterna tra modalità login e modalità registrazione.
     */
    @FXML
    private void handleSwitch() {
        clearError();
        isRegistrationMode = !isRegistrationMode;
        if (isRegistrationMode) {
            formTitle.setText("Crea il tuo account");
            primaryBtn.setText("Registrati");
            switchBtn.setText("Hai gia' un account? Accedi");
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

    // ----------------------------------------------------------------
    // Navigazione
    // ----------------------------------------------------------------

    /**
     * Carica la schermata di gioco e vi naviga sostituendo la scena corrente.
     *
     * @param username   username autenticato
     * @param conn       connessione già aperta con il server
     * @param waitingMsg messaggio di attesa iniziale
     */
    private void navigateToGame(final String username,
                                final ServerConnection conn,
                                final String waitingMsg,
                                final java.util.Queue<Message> pendingMessages) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/game.fxml"));
            Parent root = loader.load();
            GameController gc = loader.getController();
            gc.init(username, conn, waitingMsg);

            // Riconsegna eventuali messaggi (es. CHALLENGE_START) arrivati
            // sul reader thread mentre eravamo in fase di navigazione,
            // prima che GameController registrasse il proprio messageCallback.
            Message m;
            while ((m = pendingMessages.poll()) != null) {
                gc.deliverMessage(m);
            }

            Stage stage = (Stage) primaryBtn.getScene().getWindow();
            stage.setScene(new Scene(root, 820, 620));
            stage.setTitle("GuessTheWord - " + username);
            stage.setResizable(true);
            stage.setMinWidth(700);
            stage.setMinHeight(520);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore caricamento game.fxml", e);
            showError("Errore interno: impossibile aprire la finestra di gioco.");
        }
    }

    // ----------------------------------------------------------------
    // Utility UI
    // ----------------------------------------------------------------

    /**
     * Mostra un messaggio di errore nella Label dedicata.
     *
     * @param msg messaggio di errore
     */
    private void showError(String msg) {
        errorLabel.setStyle("-fx-text-fill: #f85149;");
        errorLabel.setText(msg);
    }

    /**
     * Nasconde il messaggio di errore.
     */
    private void clearError() {
        errorLabel.setText("");
    }
}
