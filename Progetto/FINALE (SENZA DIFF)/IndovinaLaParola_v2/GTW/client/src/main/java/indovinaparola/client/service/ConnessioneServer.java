package indovinaparola.client.service;

import indovinaparola.common.Messaggio;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestisce la connessione socket TCP del client verso il server.
 *
 * I messaggi in ingresso vengono consegnati tramite una callback
 * {@link Consumer}&lt;{@link Messaggio}&gt; assegnata dal controller
 * tramite espressione lambda.
 *
 * Il thread di lettura è daemon:
 * non impedisce la chiusura della JVM quando la finestra viene chiusa.
 *
 * Ordine obbligatorio degli stream: {@link ObjectOutputStream}
 * prima di {@link ObjectInputStream}, con {@code flush()} dopo OOS.
 */
public class ConnessioneServer {

    private static final Logger LOGGER = Logger.getLogger(ConnessioneServer.class.getName());

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    /**
     * Callback invocata ad ogni messaggio ricevuto dal server.
     * Assegnata tramite lambda dal controller.
     */
    private Consumer<Messaggio> callbackMessaggio;

    /**
     * Callback invocata alla disconnessione dal server.
     * Assegnata tramite lambda dal controller.
     */
    private Runnable callbackDisconnessione;

    private volatile boolean connesso = false;

    /**
     * Imposta la callback per i messaggi ricevuti dal server.
     *
     * @param callback {@link Consumer}&lt;{@link Messaggio}&gt; invocato ad ogni messaggio
     */
    public void setMessageCallback(Consumer<Messaggio> callback) {
        this.callbackMessaggio = callback;
    }

    /**
     * Imposta la callback invocata alla disconnessione dal server.
     *
     * @param callback {@link Runnable} eseguito alla disconnessione
     */
    public void setDisconnectCallback(Runnable callback) {
        this.callbackDisconnessione = callback;
    }

    /**
     * Apre la connessione TCP con il server e avvia il thread daemon di lettura.
     *
     * @param host indirizzo IP del server
     * @param porta porta del server
     * @throws IOException se la connessione non riesce
     */
    public void connetti(String host, int porta) throws IOException {
        socket = new Socket(host, porta);

        // ORDINE OBBLIGATORIO: OOS prima di OIS + flush()
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());

        connesso = true;
        LOGGER.info("Connesso al server " + host + ":" + porta);
        avviaThreadLettura();
    }

    /**
     * Avvia il thread daemon di lettura dei messaggi in ingresso.
     */
    private void avviaThreadLettura() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (connesso && !socket.isClosed()) {
                        Messaggio msg = (Messaggio) in.readObject();
                        if (callbackMessaggio != null) {
                            callbackMessaggio.accept(msg);
                        }
                    }
                } catch (EOFException | java.net.SocketException e) {
                    LOGGER.info("Connessione al server chiusa.");
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Errore ricezione messaggio", e);
                } finally {
                    connesso = false;
                    if (callbackDisconnessione != null) {
                        callbackDisconnessione.run();
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
    public synchronized void invia(Messaggio msg) {
        if (!connesso) {
            LOGGER.warning("Tentativo di invio su connessione chiusa.");
            return;
        }
        try {
            out.writeObject(msg);
            out.flush();
            out.reset(); // Evita object caching di ObjectOutputStream
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Errore invio messaggio", e);
        }
    }

    /**
     * Chiude la connessione con il server.
     */
    public void disconnetti() {
        connesso = false;
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
    public boolean isConnesso() {
        return connesso;
    }
}
