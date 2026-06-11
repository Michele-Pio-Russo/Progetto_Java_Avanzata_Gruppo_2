package guesstheword.server.model;

import guesstheword.common.*;

import java.io.*;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestisce la connessione con un singolo client tramite socket TCP.
 * Per ogni client connesso viene creato un {@code ClientHandler} che avvia
 * un thread daemon dedicato alla lettura dei messaggi in ingresso.
 *
 * <p>Il metodo {@link #send(Message)} è {@code synchronized} per garantire
 * che messaggi inviati da thread diversi non si sovrappongano sullo stream
 * di uscita (Modulo 6 - thread safety).</p>
 *
 * <p>Pattern di networking (Modulo 7 Appendix corso JA26):
 * {@link ObjectOutputStream} deve essere creato <b>prima</b> di
 * {@link ObjectInputStream} e deve essere eseguito {@code flush()} subito
 * dopo, altrimenti si verifica un deadlock tra client e server.</p>
 */
public class ClientHandler {

    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private String username;
    private String role;
    private boolean authenticated = false;

    private final GameCoordinator coordinator;

    /**
     * Costruisce un handler per il client connesso al socket dato.
     * Inizializza gli stream seguendo l'ordine obbligatorio:
     * OOS prima di OIS, con flush() dopo OOS.
     *
     * @param socket      socket TCP del client appena accettato
     * @param coordinator coordinatore centrale del gioco
     */
    public ClientHandler(Socket socket, GameCoordinator coordinator) {
        this.socket = socket;
        this.coordinator = coordinator;
        try {
            // ORDINE OBBLIGATORIO: ObjectOutputStream PRIMA di ObjectInputStream
            // Senza il flush(), l'header OOS non viene inviato e il costruttore
            // OIS sull'altro capo si blocca in attesa → deadlock (Modulo 7)
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore inizializzazione stream per: "
                + socket.getRemoteSocketAddress(), e);
        }
    }

    /**
     * Avvia il thread daemon di lettura messaggi.
     * Il thread è daemon per non impedire la chiusura della JVM.
     */
    public void startListening() {
        Thread t = new Thread(this::listenLoop,
            "ClientHandler-" + socket.getRemoteSocketAddress());
        t.setDaemon(true);
        t.start();
    }

    /**
     * Loop di lettura messaggi dal client.
     * Gestisce {@link EOFException} e {@link java.net.SocketException} come
     * disconnessioni normali (non errori), e informa il coordinatore.
     */
    private void listenLoop() {
        try {
            while (!socket.isClosed()) {
                Message msg = (Message) in.readObject();
                dispatchMessage(msg);
            }
        } catch (EOFException | java.net.SocketException e) {
            // Disconnessione normale (client chiuso o rete interrotta)
            LOGGER.info("Client disconnesso: "
                + (username != null ? username : socket.getRemoteSocketAddress()));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Errore ricezione messaggio da: " + username, e);
        } finally {
            coordinator.onClientDisconnected(this);
            close();
        }
    }

    /**
     * Smista il messaggio ricevuto al metodo appropriato del coordinatore,
     * in base al tipo del messaggio (enum {@link Message.Type}).
     *
     * @param msg messaggio ricevuto dal client
     */
    private void dispatchMessage(Message msg) {
        switch (msg.getType()) {
            case LOGIN_REQUEST:
                coordinator.handleLogin(this, (AuthPayload) msg.getPayload());
                break;
            case REGISTER_REQUEST:
                coordinator.handleRegister(this, (AuthPayload) msg.getPayload());
                break;
            case CHALLENGE_ANSWER:
                coordinator.handleAnswer(this, (String) msg.getPayload());
                break;
            case HISTORY_REQUEST:
                coordinator.handleHistoryRequest(this);
                break;
            case REQUEUE_REQUEST:
                coordinator.handleRequeue(this);
                break;
            default:
                LOGGER.warning("Tipo messaggio non gestito: " + msg.getType());
                break;
        }
    }

    /**
     * Invia un messaggio al client in modo thread-safe.
     * Il metodo è {@code synchronized} per evitare che più thread
     * scrivano contemporaneamente sullo stesso {@link ObjectOutputStream}.
     * Usa {@code out.reset()} per evitare il caching degli oggetti serializzati
     * (problema tipico con ObjectOutputStream: Modulo 7 Appendix).
     *
     * @param msg messaggio da inviare (non null)
     */
    public synchronized void send(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
            out.reset(); // Forza la ri-serializzazione: evita object caching OOS
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Errore invio messaggio a: " + username, e);
        }
    }

    /**
     * Chiude la connessione socket con il client.
     */
    public void close() {
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Errore chiusura socket di: " + username, e);
        }
    }

    /**
     * Restituisce lo username del client autenticato.
     *
     * @return username, oppure null se non ancora autenticato
     */
    public String getUsername() { return username; }

    /**
     * Imposta lo username dopo l'autenticazione.
     *
     * @param username username autenticato
     */
    public void setUsername(String username) { this.username = username; }

    /**
     * Restituisce il ruolo del client.
     *
     * @return "admin" o "player", oppure null se non autenticato
     */
    public String getRole() { return role; }

    /**
     * Imposta il ruolo dopo l'autenticazione.
     *
     * @param role ruolo utente
     */
    public void setRole(String role) { this.role = role; }

    /**
     * Indica se il client è autenticato.
     *
     * @return true se l'autenticazione è avvenuta con successo
     */
    public boolean isAuthenticated() { return authenticated; }

    /**
     * Imposta il flag di autenticazione.
     *
     * @param authenticated true se autenticato
     */
    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    /**
     * Restituisce l'indirizzo remoto del socket come stringa.
     *
     * @return indirizzo IP e porta del client
     */
    public String getRemoteAddress() {
        return socket.getRemoteSocketAddress().toString();
    }
}
