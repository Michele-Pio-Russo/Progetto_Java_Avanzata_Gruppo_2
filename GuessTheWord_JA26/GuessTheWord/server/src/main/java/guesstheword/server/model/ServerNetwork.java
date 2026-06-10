package guesstheword.server.model;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestisce il ServerSocket e accetta le connessioni in ingresso dai client.
 * Ogni connessione viene affidata a un {@link ClientHandler}.
 */
public class ServerNetwork {

    private static final Logger LOGGER = Logger.getLogger(ServerNetwork.class.getName());

    private ServerSocket serverSocket;
    private final int port;
    private final GameCoordinator coordinator;
    private boolean running = false;

    /**
     * Costruisce il modulo di rete del server.
     *
     * @param port        porta di ascolto
     * @param coordinator coordinatore del gioco
     */
    public ServerNetwork(int port, GameCoordinator coordinator) {
        this.port = port;
        this.coordinator = coordinator;
    }

    /**
     * Avvia il ServerSocket e il loop di accettazione connessioni in background.
     *
     * @throws IOException se la porta è già occupata
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        LOGGER.info("Server in ascolto sulla porta " + port);

        Thread acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    LOGGER.info("Nuova connessione da: " + clientSocket.getRemoteSocketAddress());
                    ClientHandler handler = new ClientHandler(clientSocket, coordinator);
                    handler.startListening();
                } catch (IOException e) {
                    if (running) {
                        LOGGER.log(Level.WARNING, "Errore accettazione connessione", e);
                    }
                }
            }
        }, "ServerAcceptThread");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /**
     * Ferma il server chiudendo il ServerSocket.
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Errore chiusura ServerSocket", e);
        }
    }

    /**
     * Indica se il server è attivo.
     *
     * @return true se in ascolto
     */
    public boolean isRunning() { return running; }
}
