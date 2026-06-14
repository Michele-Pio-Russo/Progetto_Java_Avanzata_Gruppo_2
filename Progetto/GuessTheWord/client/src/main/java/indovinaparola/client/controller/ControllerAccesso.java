/**
 * @file ControllerAccesso.java
 *
 * @brief Controller JavaFX per la schermata di login e registrazione del client.
 *
 * Gestisce la connessione iniziale al server in un thread separato,
 * la modalità alternata login/registrazione e la navigazione alla schermata di gioco.
 * La connessione avviene in un thread daemon per non bloccare il JavaFX Application Thread.
 *
 * @author Gruppo 2
 *
 * @version 1.0.0
 */
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

public class ControllerAccesso implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(ControllerAccesso.class.getName()); /// @brief Logger della classe per la registrazione degli errori

    @FXML private Label formTitle; /// @brief Titolo del form (login o registrazione)
    @FXML private TextField campoNomeUtente; /// @brief Campo di testo per l'inserimento del nome utente
    @FXML private PasswordField passwordField; /// @brief Campo per l'inserimento della password
    @FXML private VBox confirmBox; /// @brief Contenitore del campo di conferma password (visibile solo in modalità registrazione)
    @FXML private PasswordField confirmField; /// @brief Campo per la conferma della password in fase di registrazione
    @FXML private Label errorLabel; /// @brief Label per la visualizzazione dei messaggi di errore
    @FXML private Button pulsantePrincipale; /// @brief Pulsante principale per confermare login o registrazione
    @FXML private Button switchBtn; /// @brief Pulsante per alternare tra modalità login e registrazione
    @FXML private Label connectionLabel; /// @brief Label che mostra lo stato della connessione al server

    private boolean isRegistrationMode = false; /// @brief Indica se il form è in modalità registrazione (true) o login (false)
    private ConnessioneServer connessione; /// @brief Oggetto che gestisce la connessione socket con il server
    private String serverIp; /// @brief Indirizzo IP del server letto dal file di configurazione    
    private int serverPort; /// @brief Porta del server letta dal file di configurazione
 
    /**
     * @brief Inizializza il controller dopo il caricamento FXML.
     *
     * Carica la configurazione del server tramite ConfigurazioneClient,
     * imposta le callback per i messaggi e la disconnessione,
     * e avvia il tentativo di connessione al server.
     *
     * @param[in] location  URL della risorsa FXML (non utilizzato)
     * @param[in] resources ResourceBundle per la localizzazione (non utilizzato)
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        ConfigurazioneClient cfg = new ConfigurazioneClient();
        serverIp = cfg.getServerIp();
        serverPort = cfg.getServerPort();

        connessione = new ConnessioneServer();

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
                        connectionLabel.setText("Disconnesso dal server.");
                        mostraErrore("Connessione persa. Riavvia l'applicazione.");
                    }
                });
            }
        });

        tryConnect();
    }

    /**
     * @brief Tenta la connessione al server in un thread separato.
     *
     * Avvia un thread daemon che esegue la connessione senza bloccare
     * il JavaFX Application Thread. Aggiorna la label di stato al termine,
     * sia in caso di successo che di errore.
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

    /**
     * @brief Smista i messaggi ricevuti dal server al metodo appropriato.
     *
     * Tutti gli aggiornamenti alla GUI vengono eseguiti tramite Platform.runLater.
     * In caso di messaggio WAITING, sostituisce immediatamente la callback
     * con un buffer temporaneo per evitare la perdita di messaggi (es. CHALLENGE_START)
     * che potrebbero arrivare prima che ControllerGioco sia inizializzato.
     *
     * @param[in] msg messaggio ricevuto dal server
     */
    private void gestisciMessaggioServer(final Messaggio msg) {
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
     * @brief Gestisce la risposta del server a una richiesta di login.
     *
     * In caso di successo aggiorna la label di connessione con il nome utente.
     * La navigazione alla schermata di gioco avviene separatamente,
     * alla ricezione del messaggio WAITING.
     *
     * @param[in] resp risposta di autenticazione ricevuta dal server
     */
    private void gestisciRispostaLogin(RispostaAutenticazione resp) {
        if (resp.isSuccesso()) {
            connectionLabel.setText("Autenticato come: " + campoNomeUtente.getText().trim());
            clearError();
        } else {
            mostraErrore(resp.getMessaggio());
        }
    }

    /**
     * @brief Gestisce la risposta del server a una richiesta di registrazione.
     *
     * In caso di successo ripristina la modalità login e mostra un messaggio
     * di conferma in verde. In caso di errore mostra il messaggio di errore.
     *
     * @param[in] resp risposta di registrazione ricevuta dal server
     */
    private void gestisciRispostaRegistrazione(RispostaAutenticazione resp) {
        if (resp.isSuccesso()) {
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

    /**
     * @brief Gestisce il click sul pulsante principale (Accedi o Registrati).
     *
     * Valida i campi inseriti dall'utente e, se la connessione è attiva,
     * invia al server la richiesta di login o registrazione in base alla modalità corrente.
     * Disabilita il pulsante per evitare invii multipli.
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
        }

        if (!connessione.isConnesso()) {
            /// Se non si e' connessi al server, avvia un thread per il tentativo di connessione
            mostraErrore("Tentativo di connessione al server in corso...");
            pulsantePrincipale.setDisable(true);
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        connessione.connetti(serverIp, serverPort);
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                /// Una volta connesso con successo, esegue l'invio della richiesta
                                connectionLabel.setText("Connesso a " + serverIp + ":" + serverPort);
                                clearError();
                                inviaRichiesta(nomeUtente, password);
                            }
                        });
                    } catch (IOException e) {
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                /// Gestisce l'errore di connessione in caso di server non raggiungibile
                                pulsantePrincipale.setDisable(false);
                                mostraErrore("Server non raggiungibile. Accendi il server e riprova.");
                            }
                        });
                    }
                }
            });
            t.setDaemon(true);
            t.start();
            return;
        }

        inviaRichiesta(nomeUtente, password);
        pulsantePrincipale.setDisable(true);
    }

    /**
     * @brief Invia la richiesta al server (login o registrazione).
     *
     * @param[in] nomeUtente nome utente
     * @param[in] password password
     */
    private void inviaRichiesta(String nomeUtente, String password) {
        if (isRegistrationMode) {
            connessione.invia(new Messaggio(Messaggio.Tipo.REGISTER_REQUEST,
                new PayloadAutenticazione(nomeUtente, password)));
        } else {
            connessione.invia(new Messaggio(Messaggio.Tipo.LOGIN_REQUEST,
                new PayloadAutenticazione(nomeUtente, password)));
        }
    }

    /**
     * @brief Alterna tra la modalità login e la modalità registrazione.
     *
     * Aggiorna titolo, testo dei pulsanti e visibilità del campo
     * di conferma password in base alla modalità attivata.
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

    /**
     * @brief Carica la schermata di gioco e vi naviga sostituendo la scena corrente.
     *
     * Inizializza ControllerGioco tramite il metodo init e
     * riconsegna eventuali messaggi accodati durante la fase di navigazione,
     * per evitare che vadano persi prima che il nuovo controller sia pronto.
     *
     * @param[in] nomeUtente nome utente autenticato
     * @param[in] conn connessione già aperta con il server
     * @param[in] waitingMsg messaggio di attesa iniziale ricevuto dal server
     * @param[in] pendingMessages coda di messaggi arrivati durante la navigazione
     */
    private void vaiAGioco(final String nomeUtente, final ConnessioneServer conn, final String waitingMsg, final java.util.Queue<Messaggio> pendingMessages) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/game.fxml"));
            Parent root = loader.load();
            ControllerGioco gc = loader.getController();
            gc.init(nomeUtente, conn, waitingMsg);

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

    /**
     * @brief Mostra un messaggio di errore nella label dedicata.
     *
     * Imposta il testo in rosso e aggiorna il contenuto della label.
     *
     * @param[in] msg testo del messaggio di errore da visualizzare
     */
    private void mostraErrore(String msg) {
        errorLabel.setStyle("-fx-text-fill: #f85149;");
        errorLabel.setText(msg);
    }

    /**
     * @brief Nasconde il messaggio di errore pulendo il testo della label.
     */
    private void clearError() {
        errorLabel.setText("");
    }
}