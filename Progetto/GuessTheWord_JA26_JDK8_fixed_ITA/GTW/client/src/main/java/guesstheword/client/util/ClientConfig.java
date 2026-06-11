package guesstheword.client.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Carica la configurazione del client dal file {@code client.properties}.
 * Il file deve trovarsi nella working directory di esecuzione.
 * I percorsi sono sempre relativi, mai assoluti.
 */
public class ClientConfig {

    private static final Logger LOGGER = Logger.getLogger(ClientConfig.class.getName());
    private final Properties props = new Properties();

    /**
     * Costruisce un ClientConfig caricando {@code properties/client.properties}.
     * In caso di file non trovato, usa valori di default (127.0.0.1:5000).
     */
    public ClientConfig() {
        try (FileInputStream fis = new FileInputStream("properties/client.properties")) {
            props.load(fis);
            LOGGER.info("client.properties caricato.");
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                "client.properties non trovato. Uso valori default: 127.0.0.1:5000", e);
        }
    }

    /**
     * Restituisce l'indirizzo IP del server.
     *
     * @return IP del server (default: 127.0.0.1)
     */
    public String getServerIp() {
        return props.getProperty("server.ip", "127.0.0.1").trim();
    }

    /**
     * Restituisce la porta del server.
     *
     * @return porta (default: 5000)
     */
    public int getServerPort() {
        try {
            return Integer.parseInt(
                props.getProperty("server.port", "5000").trim());
        } catch (NumberFormatException e) {
            return 5000;
        }
    }
}
