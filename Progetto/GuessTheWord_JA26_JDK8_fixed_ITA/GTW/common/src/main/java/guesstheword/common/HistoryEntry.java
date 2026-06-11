package guesstheword.common;

import java.io.Serializable;

/**
 * Rappresenta una voce dello storico delle sfide di un giocatore.
 * Classe immutabile (tutti i campi final, nessun setter) — buona pratica
 * per oggetti di trasferimento dati (DTO) in ambiente multi-thread.
 * Compatibile JDK 8.
 */
public class HistoryEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String playedAt;
    private final String opponent;
    private final String outcome;
    private final long responseTimeMs;
    private final String correctWord;

    /**
     * Costruisce una voce dello storico.
     *
     * @param playedAt       data e ora della sfida (formato DATETIME SQLite)
     * @param opponent       username dell'avversario
     * @param outcome        esito: "win", "loss" o "draw"
     * @param responseTimeMs tempo di risposta in millisecondi (-1 se timeout)
     * @param correctWord    parola corretta della sfida
     */
    public HistoryEntry(String playedAt, String opponent, String outcome,
                        long responseTimeMs, String correctWord) {
        this.playedAt = playedAt;
        this.opponent = opponent;
        this.outcome = outcome;
        this.responseTimeMs = responseTimeMs;
        this.correctWord = correctWord;
    }

    /**
     * Restituisce la data e l'ora della sfida.
     *
     * @return stringa datetime
     */
    public String getPlayedAt() { return playedAt; }

    /**
     * Restituisce lo username dell'avversario.
     *
     * @return username avversario
     */
    public String getOpponent() { return opponent; }

    /**
     * Restituisce l'esito della sfida.
     *
     * @return "win", "loss" o "draw"
     */
    public String getOutcome() { return outcome; }

    /**
     * Restituisce il tempo di risposta in millisecondi.
     *
     * @return ms di risposta, -1 se timeout
     */
    public long getResponseTimeMs() { return responseTimeMs; }

    /**
     * Restituisce la parola corretta della sfida.
     *
     * @return parola corretta
     */
    public String getCorrectWord() { return correctWord; }

    @Override
    public String toString() {
        return "HistoryEntry{playedAt=" + playedAt
            + ", opponent=" + opponent
            + ", outcome=" + outcome
            + ", ms=" + responseTimeMs
            + ", word=" + correctWord + "}";
    }
}
