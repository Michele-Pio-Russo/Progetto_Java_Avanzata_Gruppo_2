package guesstheword.common;

import java.io.Serializable;

/**
 * Rappresenta un record dello storico delle sfide di un giocatore.
 */
public class HistoryEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private String playedAt;
    private String opponent;
    private String outcome;
    private long responseTimeMs;
    private String correctWord;

    /**
     * Costruisce una voce dello storico.
     *
     * @param playedAt       data/ora della sfida
     * @param opponent       username avversario
     * @param outcome        esito (win/loss/draw)
     * @param responseTimeMs tempo di risposta in ms
     * @param correctWord    parola corretta
     */
    public HistoryEntry(String playedAt, String opponent, String outcome,
                        long responseTimeMs, String correctWord) {
        this.playedAt = playedAt;
        this.opponent = opponent;
        this.outcome = outcome;
        this.responseTimeMs = responseTimeMs;
        this.correctWord = correctWord;
    }

    /** @return data e ora */
    public String getPlayedAt() { return playedAt; }

    /** @return username avversario */
    public String getOpponent() { return opponent; }

    /** @return esito */
    public String getOutcome() { return outcome; }

    /** @return tempo di risposta */
    public long getResponseTimeMs() { return responseTimeMs; }

    /** @return parola corretta */
    public String getCorrectWord() { return correctWord; }
}
