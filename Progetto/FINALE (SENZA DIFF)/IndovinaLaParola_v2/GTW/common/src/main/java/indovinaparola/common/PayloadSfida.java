/**
* @file PayloadSfida.java
* 
* @brief Payload inviato dal server a entrambi i client all'avvio di una sfida.
* Contiene l'estratto testuale con la parola cifrata, la lista delle
* parole cifrate evidenziate e la durata del timer in secondi.
* 
* @author Gruppo 2
* 
* @version 1.0
*/

package indovinaparola.common;

import java.io.Serializable;
import java.util.List;

public class PayloadSfida implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int idSfida;
    private final String estrattoTesto;
    private final List<String> paroleCifrate;
    private final int secondiTimeout;

    /**
     * @brief Costruttore per creare un oggetto di tipo PayloadSfida
     *
     * @param[in] idSfida identificativo della sfida nel database
     * @param[in] estrattoTesto estratto testuale con la parola cifrata tra parentesi quadre
     * @param[in] paroleCifrate lista delle parole cifrate presenti nell'estratto
     * @param[in] secondiTimeout durata massima della sfida in secondi
     */
    public PayloadSfida(int idSfida, String estrattoTesto,
                            List<String> paroleCifrate, int secondiTimeout) {
        this.idSfida = idSfida;
        this.estrattoTesto = estrattoTesto;
        this.paroleCifrate = paroleCifrate;
        this.secondiTimeout = secondiTimeout;
    }

    /**
     * @brief Restituisce l'id della sfida nel database.
     *
     * @return idSfida
     */
    public int getIdSfida() {
        return idSfida;
    }

    /**
     * @brief Restituisce l'estratto testuale con la parola cifrata.
     *
     * @return estrattoTesto 
     */
    public String getEstrattoTesto() {
        return estrattoTesto;
    }

    /**
     * @brief Restituisce la lista delle parole cifrate nella sfida.
     *
     * @return paroleCifrate 
     */
    public List<String> getParoleCifrate() {
        return paroleCifrate;
    }

    /**
     * @brief Restituisce la durata del timer in secondi.
     *
     * @return secondiTimeout
     */
    public int getSecondiTimeout() {
        return secondiTimeout;
    }
}
