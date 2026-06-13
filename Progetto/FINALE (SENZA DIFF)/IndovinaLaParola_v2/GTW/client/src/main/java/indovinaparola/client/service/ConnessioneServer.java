/**
 * @file ConnessioneServer.java
 *
 * @brief Gestisce la connessione socket TCP del client verso il server.
 *
 * I messaggi in ingresso vengono consegnati tramite una callback
 * Consumer assegnata dal controller. Il thread di lettura è daemon
 * e non impedisce la chiusura della JVM quando la finestra viene chiusa.
 * L'ordine di inizializzazione degli stream è obbligatorio:
 * ObjectOutputStream prima di ObjectInputStream, con flush() dopo OOS.
 *
 * @author Gruppo 2
 *
 * @version 1.0.0
 */
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

    private static final Logger LOGGER = Logger.getLogger(ConnessioneServer.class.getName()); /// @brief Logger della classe per la registrazione degli errori

    private Socket socket;           /// @brief Socket TCP verso il server
    private ObjectOutputStream out;  /// @brief Stream di output per la serializzazione dei messaggi
    private ObjectInputStream in;    /// @brief Stream di input per la deserializzazione dei messaggi

    private Consumer<Messaggio> callbackMessaggio;    /// @brief Callback invocata ad ogni messaggio ricevuto dal server
    private Runnable callbackDisconnessione;          /// @brief Callback invocata alla disconnessione dal server

    private volatile boolean connesso = false; /// @brief Indica se la connessione con il server è attiva


    /**
     * Imposta la callback per i messaggi ricevuti dal server.
=======
     * @brief Imposta la callback per i messaggi ricevuti dal server.
     *
     * @param[in] callback Consumer invocato ad ogni messaggio ricevuto
     */
    public void setMessageCallback(Consumer<Messaggio> callback) {
        this.callbackMessaggio = callback;
    }

    /**
     * @brief Imposta la callback invocata alla disconnessione dal server.
     *
     * @param[in] callback Runnable eseguito alla disconnessione
     */
    public void setDisconnectCallback(Runnable callback) {
        this.callbackDisconnessione = callback;
    }

    /**
     * @brief Apre la connessione TCP con il server e avvia il thread daemon di lettura.
     *
     * Inizializza gli stream nell'ordine obbligatorio: ObjectOutputStream
     * prima di ObjectInputStream, con flush() dopo OOS per sbloccare
     * il costruttore dell'OIS lato server.
     *
     * @param[in] host  indirizzo IP del server
     * @param[in] porta porta del server
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
     * @brief Avvia il thread daemon di lettura dei messaggi in ingresso.
     *
     * Il thread legge continuamente oggetti dallo stream finché la connessione
     * è attiva. Alla chiusura o in caso di errore invoca la callback di disconnessione.
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
     * @brief Invia un messaggio al server in modo thread-safe.
     *
     * Il metodo è synchronized per evitare interleaving se più thread
     * dovessero scrivere contemporaneamente. Dopo ogni invio viene chiamato
     * reset() per evitare il caching degli oggetti da parte di ObjectOutputStream.
     *
     * @param[in] msg messaggio da inviare (non null)
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
            out.reset();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Errore invio messaggio", e);
        }
    }

    /**
     * @brief Chiude la connessione con il server.
     *
     * Imposta il flag connesso a false e chiude il socket se ancora aperto.
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
     * @brief Indica se la connessione con il server è attiva.
     *
     * @return true se connesso al server, false altrimenti
     */
    public boolean isConnesso() {
        return connesso;
    }
}