/**
 * @file CaricatoreConfigurazione.java
 * @brief Questo file contiene gli attributi, il costruttore e i metodi setter, getter e toString della classe CaricatoreConfigurazione
 *
 * Questa classe permette di istanziare un oggetto CaricatoreConfigurazione, i metodi setter e getter permettono di
 * ottenere e modificare informazioni relative agli attributi, inoltre il metodo toString permette di stampare 
 * le informazioni relative alla classe CaricatoreConfigurazione.
 *
 * @author Gruppo 2
 * @date 
 * @version 1.0.0
 */
package indovinaparola.server.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @brief Carica i parametri di configurazione dal file {@code server.properties}.
 * @brief I percorsi sono sempre relativi alla directory di esecuzione (working directory),
 * mai assoluti, in modo che il JAR sia portabile.
 */
public class CaricatoreConfigurazione {

    private static final Logger LOGGER = Logger.getLogger(CaricatoreConfigurazione.class.getName()); ///< Logger della classe CaricatoreConfigurazione
    private final Properties props = new Properties();

    /**
     * @brief Carica il file .properties dal percorso relativo specificato.
     *
     * @param[in] relativePath percorso relativo al file (es. "properties/server.properties")
     * @throws IOException se il file non esiste o non è leggibile
     */
    public CaricatoreConfigurazione(String relativePath) throws IOException {
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
     * @brief Restituisce il valore di una proprietà come stringa.
     *
     * @param[in] key          chiave della proprietà
     * @param[in] defaultValue valore restituito se la chiave non esiste
     * @return valore della proprietà o defaultValue
     */
    public String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue).trim();
    }

    /**
     * @brief Restituisce il valore di una proprietà come intero.
     *
     * @param[in] key          chiave della proprietà
     * @param[in] defaultValue valore restituito se la chiave non esiste o non è parsabile
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
