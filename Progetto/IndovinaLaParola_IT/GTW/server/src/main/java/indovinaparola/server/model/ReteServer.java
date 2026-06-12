package indovinaparola.server.model;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestisce il {@link ServerSocket} e accetta le connessioni in ingresso dai client.
 * Ogni connessione accettata viene affidata a un {@link GestoreClient} dedicato.
 *
 * <p>Il loop di accettazione gira in un thread daemon separato per non bloccare
 * il JavaFX Application Thread (Modulo 7 Appendix corso JA26).</p>
 */
public class ReteServer {

    private static final Logger LOGGER = Logger.getLogger(ReteServer.class.getName());

    private ServerSocket serverSocket;
    private final int porta;
    private final CoordinatoreGioco coordinator;
    private volatile boolean running = false;

    /**
     * Costruisce il modulo di rete del server.
     *
     * @param porta        porta di ascolto TCP
     * @param coordinator coordinatore del gioco
     */
    public ReteServer(int porta, CoordinatoreGioco coordinator) {
        this.porta = porta;
        this.coordinator = coordinator;
    }

    /**
     * Avvia il {@link ServerSocket} e il thread daemon di accettazione connessioni.
     *
     * @throws IOException se la porta è già occupata o non disponibile
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(porta);
        running = true;
        LOGGER.info("ServerSocket aperto sulla porta " + porta);

        // Thread daemon: non impedisce la chiusura della JVM (Modulo 7)
        Thread acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        LOGGER.info("Nuova connessione da: "
                            + clientSocket.getRemoteSocketAddress());
                        GestoreClient handler = new GestoreClient(clientSocket, coordinator);
                        handler.startListening();
                    } catch (IOException e) {
                        if (running) {
                            LOGGER.log(Level.WARNING, "Errore accettazione connessione", e);
                        }
                        // Se running == false, il ServerSocket è stato chiuso intenzionalmente
                    }
                }
            }
        }, "ServerAcceptThread");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /**
     * Ferma il server chiudendo il {@link ServerSocket}.
     * La chiusura del socket causa un'eccezione nel thread di accettazione,
     * che viene ignorata grazie al controllo su {@code running}.
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
        LOGGER.info("Server fermato.");
    }

    /**
     * Indica se il server è attivo e in ascolto.
     *
     * @return true se il ServerSocket è aperto
     */
    public boolean isRunning() {
        return running;
    }
}
