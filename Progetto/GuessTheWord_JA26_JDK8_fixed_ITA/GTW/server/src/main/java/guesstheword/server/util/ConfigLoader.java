package guesstheword.server.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Carica i parametri di configurazione dal file {@code server.properties}.
 * I percorsi sono sempre relativi alla directory di esecuzione (working directory),
 * mai assoluti, in modo che il JAR sia portabile.
 */
public class ConfigLoader {

    private static final Logger LOGGER = Logger.getLogger(ConfigLoader.class.getName());
    private final Properties props = new Properties();

    /**
     * Carica il file .properties dal percorso relativo specificato.
     *
     * @param relativePath percorso relativo al file (es. "properties/server.properties")
     * @throws IOException se il file non esiste o non è leggibile
     */
    public ConfigLoader(String relativePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(relativePath)) {
            props.load(fis);
            LOGGER.info("Configurazione caricata da: " + relativePath);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                "File di configurazione non trovato: " + relativePath
                + ". Verranno usati i valori di default.", e);
            throw e;
        }
    }

    /**
     * Restituisce il valore di una proprietà come stringa.
     *
     * @param key          chiave della proprietà
     * @param defaultValue valore restituito se la chiave non esiste
     * @return valore della proprietà o defaultValue
     */
    public String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue).trim();
    }

    /**
     * Restituisce il valore di una proprietà come intero.
     *
     * @param key          chiave della proprietà
     * @param defaultValue valore restituito se la chiave non esiste o non è parsabile
     * @return intero parsato o defaultValue
     */
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key,
                String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
