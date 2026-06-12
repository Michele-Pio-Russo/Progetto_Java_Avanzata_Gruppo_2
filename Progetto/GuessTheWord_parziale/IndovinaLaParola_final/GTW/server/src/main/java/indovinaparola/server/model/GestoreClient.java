package indovinaparola.server.model;

import indovinaparola.common.*;

import java.io.*;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestisce la connessione con un singolo client tramite socket TCP.
 * Per ogni client connesso viene creato un {@code GestoreClient} che avvia
 * un thread daemon dedicato alla lettura dei messaggi in ingresso.
 *
 * <p>Il metodo {@link #invia(Messaggio)} è {@code synchronized} per garantire
 * che messaggi inviati da thread diversi non si sovrappongano sullo stream
 * di uscita (Modulo 6 - thread safety).</p>
 *
 * <p>Pattern di networking (Modulo 7 Appendix corso JA26):
 * {@link ObjectOutputStream} deve essere creato <b>prima</b> di
 * {@link ObjectInputStream} e deve essere eseguito {@code flush()} subito
 * dopo, altrimenti si verifica un deadlock tra client e server.</p>
 */
public class GestoreClient {

    private static final Logger LOGGER = Logger.getLogger(GestoreClient.class.getName());

    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private String nomeUtente;
    private String ruolo;
    private boolean autenticato = false;

    private final CoordinatoreGioco coordinator;

    /**
     * Costruisce un handler per il client connesso al socket dato.
     * Inizializza gli stream seguendo l'ordine obbligatorio:
     * OOS prima di OIS, con flush() dopo OOS.
     *
     * @param socket      socket TCP del client appena accettato
     * @param coordinator coordinatore centrale del gioco
     */
    public GestoreClient(Socket socket, CoordinatoreGioco coordinator) {
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
        Thread t = new Thread(this::cicloAscolto,
            "GestoreClient-" + socket.getRemoteSocketAddress());
        t.setDaemon(true);
        t.start();
    }

    /**
     * Loop di lettura messaggi dal client.
     * Gestisce {@link EOFException} e {@link java.net.SocketException} come
     * disconnessioni normali (non errori), e informa il coordinatore.
     */
    private void cicloAscolto() {
        try {
            while (!socket.isClosed()) {
                Messaggio msg = (Messaggio) in.readObject();
                smistaMessaggio(msg);
            }
        } catch (EOFException | java.net.SocketException e) {
            // Disconnessione normale (client chiuso o rete interrotta)
            LOGGER.info("Client disconnesso: "
                + (nomeUtente != null ? nomeUtente : socket.getRemoteSocketAddress()));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Errore ricezione messaggio da: " + nomeUtente, e);
        } finally {
            coordinator.onClientDisconnesso(this);
            close();
        }
    }

    /**
     * Smista il messaggio ricevuto al metodo appropriato del coordinatore,
     * in base al tipo del messaggio (enum {@link Messaggio.Tipo}).
     *
     * @param msg messaggio ricevuto dal client
     */
    private void smistaMessaggio(Messaggio msg) {
        switch (msg.getTipo()) {
            case LOGIN_REQUEST:
                coordinator.gestisciLogin(this, (PayloadAutenticazione) msg.getCarico());
                break;
            case REGISTER_REQUEST:
                coordinator.gestisciRegistrazione(this, (PayloadAutenticazione) msg.getCarico());
                break;
            case CHALLENGE_ANSWER:
                coordinator.gestisciRisposta(this, (String) msg.getCarico());
                break;
            case HISTORY_REQUEST:
                coordinator.gestisciRichiestaStorico(this);
                break;
            case REQUEUE_REQUEST:
                coordinator.gestisciNuovaRichiesta(this);
                break;
            default:
                LOGGER.warning("Tipo messaggio non gestito: " + msg.getTipo());
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
    public synchronized void invia(Messaggio msg) {
        try {
            out.writeObject(msg);
            out.flush();
            out.reset(); // Forza la ri-serializzazione: evita object caching OOS
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Errore invio messaggio a: " + nomeUtente, e);
        }
    }

    /**
     * Chiude la connessione socket con il client.
     */
    public void close() {
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Errore chiusura socket di: " + nomeUtente, e);
        }
    }

    /**
     * Restituisce lo nomeUtente del client autenticato.
     *
     * @return nomeUtente, oppure null se non ancora autenticato
     */
    public String getNomeUtente() { return nomeUtente; }

    /**
     * Imposta lo nomeUtente dopo l'autenticazione.
     *
     * @param nomeUtente nomeUtente autenticato
     */
    public void setNomeUtente(String nomeUtente) { this.nomeUtente = nomeUtente; }

    /**
     * Restituisce il ruolo del client.
     *
     * @return "admin" o "player", oppure null se non autenticato
     */
    public String getRuolo() { return ruolo; }

    /**
     * Imposta il ruolo dopo l'autenticazione.
     *
     * @param ruolo ruolo utente
     */
    public void setRuolo(String ruolo) { this.ruolo = ruolo; }

    /**
     * Indica se il client è autenticato.
     *
     * @return true se l'autenticazione è avvenuta con successo
     */
    public boolean isAutenticato() { return autenticato; }

    /**
     * Imposta il flag di autenticazione.
     *
     * @param autenticato true se autenticato
     */
    public void setAutenticato(boolean autenticato) {
        this.autenticato = autenticato;
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
