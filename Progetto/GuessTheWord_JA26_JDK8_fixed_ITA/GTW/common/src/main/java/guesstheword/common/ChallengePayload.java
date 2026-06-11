package guesstheword.common;

import java.io.Serializable;
import java.util.List;

/**
 * Payload inviato dal server a entrambi i client all'avvio di una sfida.
 * Contiene l'estratto testuale con la parola cifrata, la lista delle
 * parole cifrate evidenziate e la durata del timer in secondi.
 */
public class ChallengePayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int challengeId;
    private final String textExcerpt;
    private final List<String> encryptedWords;
    private final int timeoutSeconds;

    /**
     * Costruisce il payload della sfida.
     *
     * @param challengeId    identificativo della sfida nel database
     * @param textExcerpt    estratto testuale con la parola cifrata tra parentesi quadre
     * @param encryptedWords lista delle parole cifrate presenti nell'estratto
     * @param timeoutSeconds durata massima della sfida in secondi
     */
    public ChallengePayload(int challengeId, String textExcerpt,
                            List<String> encryptedWords, int timeoutSeconds) {
        this.challengeId = challengeId;
        this.textExcerpt = textExcerpt;
        this.encryptedWords = encryptedWords;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Restituisce l'id della sfida nel database.
     *
     * @return challenge_id
     */
    public int getChallengeId() {
        return challengeId;
    }

    /**
     * Restituisce l'estratto testuale con la parola cifrata.
     *
     * @return estratto testo
     */
    public String getTextExcerpt() {
        return textExcerpt;
    }

    /**
     * Restituisce la lista delle parole cifrate nella sfida.
     *
     * @return lista parole cifrate
     */
    public List<String> getEncryptedWords() {
        return encryptedWords;
    }

    /**
     * Restituisce la durata del timer in secondi.
     *
     * @return secondi disponibili per rispondere
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
