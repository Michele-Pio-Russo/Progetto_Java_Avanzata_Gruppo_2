package guesstheword.server.model;

import guesstheword.common.*;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestisce la connessione con un singolo client tramite socket.
 * Ogni client ha il proprio thread di lettura messaggi.
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
     * Costruisce un handler per il client connesso.
     *
     * @param socket      socket TCP del client
     * @param coordinator coordinatore del gioco
     */
    public ClientHandler(Socket socket, GameCoordinator coordinator) {
        this.socket = socket;
        this.coordinator = coordinator;
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore inizializzazione stream", e);
        }
    }

    /**
     * Avvia il loop di lettura messaggi in un thread separato.
     */
    public void startListening() {
        Thread t = new Thread(this::listenLoop, "ClientHandler-" + socket.getRemoteSocketAddress());
        t.setDaemon(true);
        t.start();
    }

    private void listenLoop() {
        try {
            while (!socket.isClosed()) {
                Message msg = (Message) in.readObject();
                handleMessage(msg);
            }
        } catch (EOFException | java.net.SocketException e) {
            LOGGER.info("Client disconnesso: " + (username != null ? username : socket.getRemoteSocketAddress()));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Errore ricezione messaggio", e);
        } finally {
            coordinator.onClientDisconnected(this);
            close();
        }
    }

    private void handleMessage(Message msg) {
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
            default:
                LOGGER.warning("Messaggio non gestito: " + msg.getType());
        }
    }

    /**
     * Invia un messaggio al client in modo thread-safe.
     *
     * @param msg messaggio da inviare
     */
    public synchronized void send(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
            out.reset(); // evita object caching
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Errore invio messaggio a " + username, e);
        }
    }

    /**
     * Chiude la connessione con il client.
     */
    public void close() {
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Errore chiusura socket", e);
        }
    }

    // --- Getters / Setters ---

    /** @return username del client (null se non autenticato) */
    public String getUsername() { return username; }

    /** @param username imposta lo username */
    public void setUsername(String username) { this.username = username; }

    /** @return ruolo utente */
    public String getRole() { return role; }

    /** @param role imposta il ruolo */
    public void setRole(String role) { this.role = role; }

    /** @return true se il client è autenticato */
    public boolean isAuthenticated() { return authenticated; }

    /** @param authenticated imposta lo stato di autenticazione */
    public void setAuthenticated(boolean authenticated) { this.authenticated = authenticated; }

    /** @return indirizzo remoto del socket */
    public String getRemoteAddress() {
        return socket.getRemoteSocketAddress().toString();
    }
}
