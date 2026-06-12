package indovinaparola.common;

import java.io.Serializable;
import java.util.List;

/**
 * Payload inviato dal server a entrambi i client all'avvio di una sfida.
 * Contiene l'estratto testuale con la parola cifrata, la lista delle
 * parole cifrate evidenziate e la durata del timer in secondi.
 */
public class PayloadSfida implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int idSfida;
    private final String estrattoTesto;
    private final List<String> paroleCifrate;
    private final int secondiTimeout;

    /**
     * Costruisce il carico della sfida.
     *
     * @param idSfida    identificativo della sfida nel database
     * @param estrattoTesto    estratto testuale con la parola cifrata tra parentesi quadre
     * @param paroleCifrate lista delle parole cifrate presenti nell'estratto
     * @param secondiTimeout durata massima della sfida in secondi
     */
    public PayloadSfida(int idSfida, String estrattoTesto,
                            List<String> paroleCifrate, int secondiTimeout) {
        this.idSfida = idSfida;
        this.estrattoTesto = estrattoTesto;
        this.paroleCifrate = paroleCifrate;
        this.secondiTimeout = secondiTimeout;
    }

    /**
     * Restituisce l'id della sfida nel database.
     *
     * @return challenge_id
     */
    public int getIdSfida() {
        return idSfida;
    }

    /**
     * Restituisce l'estratto testuale con la parola cifrata.
     *
     * @return estratto testo
     */
    public String getEstrattoTesto() {
        return estrattoTesto;
    }

    /**
     * Restituisce la lista delle parole cifrate nella sfida.
     *
     * @return lista parole cifrate
     */
    public List<String> getParoleCifrate() {
        return paroleCifrate;
    }

    /**
     * Restituisce la durata del timer in secondi.
     *
     * @return secondi disponibili per rispondere
     */
    public int getSecondiTimeout() {
        return secondiTimeout;
    }
}
