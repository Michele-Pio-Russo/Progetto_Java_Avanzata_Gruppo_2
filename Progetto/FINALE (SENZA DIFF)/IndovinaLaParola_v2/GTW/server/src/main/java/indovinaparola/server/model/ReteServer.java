/**
 * @file ReteServer.java
 * @brief Questo file contiene gli attributi, il costruttore e i metodi setter, getter e toString della classe ReteServer
 *
 * Questa classe permette di istanziare un oggetto ReteServer, i metodi setter e getter permettono di
 * ottenere e modificare informazioni relative agli attributi, inoltre il metodo toString permette di stampare 
 * le informazioni relative alla classe ReteServer.
 *
 * @author Gruppo 2
 * @date 
 * @version 1.0.0
 */
package indovinaparola.server.model;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @brief Gestisce il {@link ServerSocket} e accetta le connessioni in ingresso dai client.
 * @brief Ogni connessione accettata viene affidata a un {@link GestoreClient} dedicato.
 *
 * Il loop di accettazione gira in un thread daemon separato per non bloccare
 * il JavaFX Application Thread.
 */
public class ReteServer {

    private static final Logger LOGGER = Logger.getLogger(ReteServer.class.getName()); ///< Logger della classe ReteServer

    private ServerSocket serverSocket; ///< Socket del server in ascolto per connessioni entranti
    private final int porta; ///< Porta TCP su cui il server è in ascolto
    private final CoordinatoreGioco coordinator; ///< Riferimento al coordinatore del gioco
    private volatile boolean running = false; ///< Flag di stato per indicare se il server è in esecuzione

    /**
     * @brief Costruisce il modulo di rete del server.
     *
     * @param[in] porta        porta di ascolto TCP
     * @param[in] coordinator coordinatore del gioco
     */
    public ReteServer(int porta, CoordinatoreGioco coordinator) {
        this.porta = porta;
        this.coordinator = coordinator;
    }

    /**
     * @brief Avvia il {@link ServerSocket} e il thread daemon di accettazione connessioni.
     *
     * @pre La porta TCP configurata deve essere libera e accessibile.
     * @post Il server è in ascolto e accetta nuove connessioni dai client in un thread separato.
     *
     * @throws IOException se la porta è già occupata o non disponibile
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(porta);
        running = true;
        LOGGER.info("ServerSocket aperto sulla porta " + porta);

        // Thread daemon: non impedisce la chiusura della JVM
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
     * @brief Ferma il server chiudendo il {@link ServerSocket}.
     * @brief La chiusura del socket causa un'eccezione nel thread di accettazione,
     * che viene ignorata grazie al controllo su {@code running}.
     *
     * @pre Il server deve essere attualmente in esecuzione.
     * @post Il ServerSocket viene chiuso e non vengono accettate nuove connessioni.
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
     * @brief Indica se il server è attivo e in ascolto.
     *
     * @return true se il ServerSocket è aperto
     */
    public boolean isRunning() {
        return running;
    }
}
