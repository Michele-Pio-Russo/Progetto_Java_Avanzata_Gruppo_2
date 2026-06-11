package guesstheword.client.service;

import guesstheword.common.Message;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestisce la connessione socket TCP del client verso il server.
 *
 * <p>I messaggi in ingresso vengono consegnati tramite una callback
 * {@link Consumer}&lt;{@link Message}&gt; assegnata dal controller
 * tramite espressione lambda (Modulo 4 - Lambda corso JA26).</p>
 *
 * <p>Il thread di lettura è daemon (Modulo 7 Appendix - Networking):
 * non impedisce la chiusura della JVM quando la finestra viene chiusa.</p>
 *
 * <p>Ordine obbligatorio degli stream (Modulo 7): {@link ObjectOutputStream}
 * prima di {@link ObjectInputStream}, con {@code flush()} dopo OOS.</p>
 */
public class ServerConnection {

    private static final Logger LOGGER = Logger.getLogger(ServerConnection.class.getName());

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    /**
     * Callback invocata ad ogni messaggio ricevuto dal server.
     * Assegnata tramite lambda dal controller (Modulo 4).
     */
    private Consumer<Message> messageCallback;

    /**
     * Callback invocata alla disconnessione dal server.
     * Assegnata tramite lambda dal controller (Modulo 4).
     */
    private Runnable disconnectCallback;

    private volatile boolean connected = false;

    /**
     * Imposta la callback per i messaggi ricevuti dal server.
     *
     * @param callback {@link Consumer}&lt;{@link Message}&gt; invocato ad ogni messaggio
     */
    public void setMessageCallback(Consumer<Message> callback) {
        this.messageCallback = callback;
    }

    /**
     * Imposta la callback invocata alla disconnessione dal server.
     *
     * @param callback {@link Runnable} eseguito alla disconnessione
     */
    public void setDisconnectCallback(Runnable callback) {
        this.disconnectCallback = callback;
    }

    /**
     * Apre la connessione TCP con il server e avvia il thread daemon di lettura.
     *
     * @param host indirizzo IP del server
     * @param port porta del server
     * @throws IOException se la connessione non riesce
     */
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);

        // ORDINE OBBLIGATORIO: OOS prima di OIS + flush() (Modulo 7 Appendix)
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());

        connected = true;
        LOGGER.info("Connesso al server " + host + ":" + port);
        startReaderThread();
    }

    /**
     * Avvia il thread daemon di lettura dei messaggi in ingresso.
     */
    private void startReaderThread() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (connected && !socket.isClosed()) {
                        Message msg = (Message) in.readObject();
                        if (messageCallback != null) {
                            messageCallback.accept(msg);
                        }
                    }
                } catch (EOFException | java.net.SocketException e) {
                    LOGGER.info("Connessione al server chiusa.");
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Errore ricezione messaggio", e);
                } finally {
                    connected = false;
                    if (disconnectCallback != null) {
                        disconnectCallback.run();
                    }
                }
            }
        }, "ClientReaderThread");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Invia un messaggio al server in modo thread-safe.
     * Il metodo è {@code synchronized} per evitare interleaving
     * se più thread dovessero scrivere contemporaneamente.
     *
     * @param msg messaggio da inviare (non null)
     */
    public synchronized void send(Message msg) {
        if (!connected) {
            LOGGER.warning("Tentativo di invio su connessione chiusa.");
            return;
        }
        try {
            out.writeObject(msg);
            out.flush();
            out.reset(); // Evita object caching di ObjectOutputStream (Modulo 7)
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Errore invio messaggio", e);
        }
    }

    /**
     * Chiude la connessione con il server.
     */
    public void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Errore chiusura connessione", e);
        }
    }

    /**
     * Indica se la connessione è attiva.
     *
     * @return true se connesso al server
     */
    public boolean isConnected() {
        return connected;
    }
}
