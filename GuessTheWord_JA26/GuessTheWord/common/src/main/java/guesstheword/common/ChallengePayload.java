package guesstheword.common;

import java.io.Serializable;
import java.util.List;

/**
 * Payload inviato dal server ai client all'inizio di una sfida.
 * Contiene l'estratto con le parole cifrate e la durata del timer.
 */
public class ChallengePayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private String textExcerpt;
    private List<String> encryptedWords;
    private int timeoutSeconds;
    private int challengeId;

    /**
     * Costruisce il payload della sfida.
     *
     * @param challengeId    id della sfida nel DB
     * @param textExcerpt    estratto testuale con parole cifrate
     * @param encryptedWords lista delle parole cifrate evidenziate
     * @param timeoutSeconds durata della sfida in secondi
     */
    public ChallengePayload(int challengeId, String textExcerpt,
                            List<String> encryptedWords, int timeoutSeconds) {
        this.challengeId = challengeId;
        this.textExcerpt = textExcerpt;
        this.encryptedWords = encryptedWords;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** @return id della sfida */
    public int getChallengeId() { return challengeId; }

    /** @return testo estratto con parole cifrate */
    public String getTextExcerpt() { return textExcerpt; }

    /** @return lista parole cifrate */
    public List<String> getEncryptedWords() { return encryptedWords; }

    /** @return durata timer in secondi */
    public int getTimeoutSeconds() { return timeoutSeconds; }
}
