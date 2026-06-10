package guesstheword.server.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Carica i parametri di configurazione da file .properties.
 * I percorsi sono sempre relativi alla directory di esecuzione.
 */
public class ConfigLoader {

    private static final Logger LOGGER = Logger.getLogger(ConfigLoader.class.getName());

    private final Properties props = new Properties();

    /**
     * Carica il file .properties specificato.
     *
     * @param relativePath percorso relativo alla directory di esecuzione
     * @throws IOException se il file non è trovato o illeggibile
     */
    public ConfigLoader(String relativePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(relativePath)) {
            props.load(fis);
            LOGGER.info("Configurazione caricata da: " + relativePath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "File di configurazione non trovato: " + relativePath, e);
            throw e;
        }
    }

    /**
     * Restituisce il valore di una proprietà come stringa.
     *
     * @param key          chiave
     * @param defaultValue valore di default
     * @return valore della proprietà o defaultValue
     */
    public String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    /**
     * Restituisce il valore di una proprietà come intero.
     *
     * @param key          chiave
     * @param defaultValue valore di default
     * @return intero parsato o defaultValue
     */
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
