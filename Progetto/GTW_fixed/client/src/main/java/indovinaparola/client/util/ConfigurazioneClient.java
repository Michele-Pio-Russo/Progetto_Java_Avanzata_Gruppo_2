/**
 * @file ConfigurazioneClient.java
 *
 * @brief Carica la configurazione del client dal file {@code client.properties}.
 *
 * Il file deve trovarsi nella working directory di esecuzione.
 * In caso di file non trovato vengono utilizzati i valori di default
 * (127.0.0.1 per l'IP e 5000 per la porta).
 *
 * @author Gruppo 2
 *
 * @version 1.0.0
 */
package indovinaparola.client.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConfigurazioneClient {

    private static final Logger LOGGER = Logger.getLogger(ConfigurazioneClient.class.getName()); /// @brief Logger della classe per la registrazione degli errori
    private final Properties props = new Properties(); /// @brief Oggetto Properties contenente le coppie chiave-valore del file di configurazione

    /**
     * @brief Costruttore che carica il file {@code properties/client.properties}.
     *
     * Apre il file tramite FileInputStream e carica le proprietà.
     * In caso di errore di I/O utilizza i valori di default
     * senza interrompere l'esecuzione.
     */
    public ConfigurazioneClient() {
        try (FileInputStream fis = new FileInputStream("properties/client.properties")) {
            props.load(fis);
            LOGGER.info("client.properties caricato.");
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                "client.properties non trovato. Uso valori default: 127.0.0.1:5000", e);
        }
    }

    /**
     * @brief Restituisce l'indirizzo IP del server.
     *
     * Legge la proprietà server.ip dal file di configurazione.
     *
     * @return indirizzo IP del server (default: 27.0.0.1)
     */
    public String getServerIp() {
        return props.getProperty("server.ip", "127.0.0.1").trim();
    }

    /**
     * @brief Restituisce la porta del server.
     *
     * Legge la proprietà server.port dal file di configurazione
     * e la converte in intero. In caso di valore non numerico restituisce
     * il default senza propagare l'eccezione.
     *
     * @return porta del server (default: 5000)
     */
    public int getServerPort() {
        try {
            return Integer.parseInt(props.getProperty("server.port", "5000").trim());
        } catch (NumberFormatException e) {
            return 5000;
        }
    }
}