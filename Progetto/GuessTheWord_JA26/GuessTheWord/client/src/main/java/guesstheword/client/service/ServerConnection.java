package guesstheword.client.service;

import guesstheword.common.Message;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestisce la connessione socket del client verso il server.
 * I messaggi in ingresso vengono consegnati tramite callback.
 */
public class ServerConnection {

    private static final Logger LOGGER = Logger.getLogger(ServerConnection.class.getName());

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Consumer<Message> messageCallback;
    private Runnable disconnectCallback;
    private boolean connected = false;

    /**
     * Imposta la callback per i messaggi ricevuti dal server.
     *
     * @param callback consumer chiamato ad ogni messaggio
     */
    public void setMessageCallback(Consumer<Message> callback) {
        this.messageCallback = callback;
    }

    /**
     * Imposta la callback invocata alla disconnessione.
     *
     * @param callback runnable eseguita alla disconnessione
     */
    public void setDisconnectCallback(Runnable callback) {
        this.disconnectCallback = callback;
    }

    /**
     * Apre la connessione con il server e avvia il thread di lettura.
     *
     * @param host indirizzo IP del server
     * @param port porta del server
     * @throws IOException se la connessione fallisce
     */
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
        connected = true;
        LOGGER.info("Connesso al server " + host + ":" + port);
        startListening();
    }

    private void startListening() {
        Thread t = new Thread(() -> {
            try {
                while (connected && !socket.isClosed()) {
                    Message msg = (Message) in.readObject();
                    if (messageCallback != null) messageCallback.accept(msg);
                }
            } catch (EOFException | java.net.SocketException e) {
                LOGGER.info("Connessione al server chiusa.");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Errore ricezione messaggio", e);
            } finally {
                connected = false;
                if (disconnectCallback != null) disconnectCallback.run();
            }
        }, "ClientReaderThread");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Invia un messaggio al server.
     *
     * @param msg messaggio da inviare
     */
    public synchronized void send(Message msg) {
        if (!connected) return;
        try {
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Errore invio messaggio", e);
        }
    }

    /**
     * Chiude la connessione.
     */
    public void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Errore chiusura connessione", e);
        }
    }

    /**
     * Indica se la connessione è attiva.
     *
     * @return true se connesso
     */
    public boolean isConnected() { return connected; }
}
