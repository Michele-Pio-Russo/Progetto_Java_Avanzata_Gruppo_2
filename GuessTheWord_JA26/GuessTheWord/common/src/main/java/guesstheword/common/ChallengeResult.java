package guesstheword.common;

import java.io.Serializable;

/**
 * Payload con l'esito di una sfida, inviato dal server a entrambi i client.
 */
public class ChallengeResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Esito della sfida dal punto di vista del ricevente. */
    public enum Outcome { WIN, LOSS, DRAW }

    private String winnerUsername;
    private String correctWord;
    private Outcome outcome;

    /**
     * Costruisce il risultato della sfida.
     *
     * @param winnerUsername username del vincitore (null in caso di pareggio)
     * @param correctWord    parola originale corretta
     * @param outcome        esito per il ricevente
     */
    public ChallengeResult(String winnerUsername, String correctWord, Outcome outcome) {
        this.winnerUsername = winnerUsername;
        this.correctWord = correctWord;
        this.outcome = outcome;
    }

    /** @return username vincitore */
    public String getWinnerUsername() { return winnerUsername; }

    /** @return parola corretta */
    public String getCorrectWord() { return correctWord; }

    /** @return esito dal punto di vista del ricevente */
    public Outcome getOutcome() { return outcome; }
}
