package indovinaparola.client.controller;

import indovinaparola.client.service.ConnessioneServer;
import indovinaparola.client.util.ConfigurazioneClient;
import indovinaparola.common.PayloadAutenticazione;
import indovinaparola.common.RispostaAutenticazione;
import indovinaparola.common.Messaggio;

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
 * <p>La callback dei messaggi usa {@link Consumer}&lt;{@link Messaggio}&gt;
 * come interfaccia funzionale assegnata tramite anonymous class Java 8
 * (Modulo 4 - Lambda corso JA26).</p>
 */
public class ControllerAccesso implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(ControllerAccesso.class.getName());

    @FXML private Label formTitle;
    @FXML private TextField campoNomeUtente;
    @FXML private PasswordField passwordField;
    @FXML private VBox confirmBox;
    @FXML private PasswordField confirmField;
    @FXML private Label errorLabel;
    @FXML private Button pulsantePrincipale;
    @FXML private Button switchBtn;
    @FXML private Label connectionLabel;

    private boolean isRegistrationMode = false;
    private ConnessioneServer connessione;
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
        ConfigurazioneClient cfg = new ConfigurazioneClient();
        serverIp = cfg.getServerIp();
        serverPort = cfg.getServerPort();

        connessione = new ConnessioneServer();

        // Consumer<Messaggio> assegnato con lambda (Modulo 4)
        connessione.setMessageCallback(new Consumer<Messaggio>() {
            @Override
            public void accept(Messaggio msg) {
                gestisciMessaggioServer(msg);
            }
        });

        // Runnable per la disconnessione (Modulo 4 - interfaccia funzionale)
        connessione.setDisconnectCallback(new Runnable() {
            @Override
            public void run() {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        connectionLabel.setText("Disconnesso dal server.");
                        mostraErrore("Connessione persa. Riavvia l'applicazione.");
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
                    connessione.connetti(serverIp, serverPort);
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
                            mostraErrore("Server non raggiungibile su "
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
    private void gestisciMessaggioServer(final Messaggio msg) {
        // ATTENZIONE - fix race condition (vedi vaiAGioco):
        // se il messaggio e' WAITING, il client potrebbe ricevere SUBITO DOPO
        // (sullo stesso reader thread) anche un CHALLENGE_START, prima ancora
        // che il thread JavaFX esegua la navigazione verso game.fxml e
        // ControllerGioco possa registrare il proprio callbackMessaggio.
        // In tal caso il CHALLENGE_START arriverebbe ancora qui e verrebbe
        // perso (default: break). Per evitarlo, appena riceviamo WAITING
        // sostituiamo SUBITO (sul reader thread, in modo sincrono) la callback
        // con un buffer che accoda eventuali messaggi successivi, da
        // "riconsegnare" a ControllerGioco una volta inizializzato.
        if (msg.getTipo() == Messaggio.Tipo.WAITING) {
            final java.util.Queue<Messaggio> pending = new java.util.concurrent.ConcurrentLinkedQueue<>();
            connessione.setMessageCallback(new Consumer<Messaggio>() {
                @Override
                public void accept(Messaggio m) {
                    pending.add(m);
                }
            });
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    pulsantePrincipale.setDisable(false);
                    vaiAGioco(
                        campoNomeUtente.getText().trim(),
                        connessione,
                        (String) msg.getCarico(),
                        pending);
                }
            });
            return;
        }

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                pulsantePrincipale.setDisable(false);
                switch (msg.getTipo()) {
                    case LOGIN_RESPONSE:
                        gestisciRispostaLogin((RispostaAutenticazione) msg.getCarico());
                        break;
                    case REGISTER_RESPONSE:
                        gestisciRispostaRegistrazione((RispostaAutenticazione) msg.getCarico());
                        break;
                    case ERROR:
                        mostraErrore((String) msg.getCarico());
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
    private void gestisciRispostaLogin(RispostaAutenticazione resp) {
        if (resp.isSuccesso()) {
            connectionLabel.setText("Autenticato come: " + campoNomeUtente.getText().trim());
            clearError();
            // La navigazione avviene quando arriva il messaggio WAITING
        } else {
            mostraErrore(resp.getMessaggio());
        }
    }

    /**
     * Gestisce la risposta del server alla registrazione.
     *
     * @param resp risposta registrazione
     */
    private void gestisciRispostaRegistrazione(RispostaAutenticazione resp) {
        if (resp.isSuccesso()) {
            // Torna in modalità login con messaggio di successo
            isRegistrationMode = false;
            formTitle.setText("Accedi al gioco");
            pulsantePrincipale.setText("Accedi");
            switchBtn.setText("Non hai un account? Registrati");
            confirmBox.setVisible(false);
            confirmBox.setManaged(false);
            errorLabel.setStyle("-fx-text-fill: #3fb950;");
            errorLabel.setText(resp.getMessaggio() + " Ora accedi.");
        } else {
            mostraErrore(resp.getMessaggio());
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
        final String nomeUtente = campoNomeUtente.getText().trim();
        final String password   = passwordField.getText().trim();

        if (nomeUtente.isEmpty() || password.isEmpty()) {
            mostraErrore("Username e password sono obbligatori e non possono contenere solo spazi.");
            return;
        }

        if (!connessione.isConnesso()) {
            mostraErrore("Non connesso al server. Attendi o riavvia.");
            return;
        }

        if (isRegistrationMode) {
            if (nomeUtente.length() < 3) {
                mostraErrore("Username deve avere almeno 3 caratteri.");
                return;
            }
            if (password.length() < 4) {
                mostraErrore("Password deve avere almeno 4 caratteri.");
                return;
            }
            final String confirm = confirmField.getText().trim();
            if (!password.equals(confirm)) {
                mostraErrore("Le password non coincidono.");
                return;
            }
            connessione.invia(new Messaggio(Messaggio.Tipo.REGISTER_REQUEST,
                new PayloadAutenticazione(nomeUtente, password)));
        } else {
            connessione.invia(new Messaggio(Messaggio.Tipo.LOGIN_REQUEST,
                new PayloadAutenticazione(nomeUtente, password)));
        }

        pulsantePrincipale.setDisable(true);
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
            pulsantePrincipale.setText("Registrati");
            switchBtn.setText("Hai gia' un account? Accedi");
            confirmBox.setVisible(true);
            confirmBox.setManaged(true);
        } else {
            formTitle.setText("Accedi al gioco");
            pulsantePrincipale.setText("Accedi");
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
     * @param nomeUtente   nomeUtente autenticato
     * @param conn       connessione già aperta con il server
     * @param waitingMsg messaggio di attesa iniziale
     */
    private void vaiAGioco(final String nomeUtente,
                                final ConnessioneServer conn,
                                final String waitingMsg,
                                final java.util.Queue<Messaggio> pendingMessages) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/game.fxml"));
            Parent root = loader.load();
            ControllerGioco gc = loader.getController();
            gc.init(nomeUtente, conn, waitingMsg);

            // Riconsegna eventuali messaggi (es. CHALLENGE_START) arrivati
            // sul reader thread mentre eravamo in fase di navigazione,
            // prima che ControllerGioco registrasse il proprio callbackMessaggio.
            Messaggio m;
            while ((m = pendingMessages.poll()) != null) {
                gc.consegnaMessaggio(m);
            }

            Stage stage = (Stage) pulsantePrincipale.getScene().getWindow();
            stage.setScene(new Scene(root, 820, 620));
            stage.setTitle("IndovinaLaParola - " + nomeUtente);
            stage.setResizable(true);
            stage.setMinWidth(700);
            stage.setMinHeight(520);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore caricamento game.fxml", e);
            mostraErrore("Errore interno: impossibile aprire la finestra di gioco.");
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
    private void mostraErrore(String msg) {
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
