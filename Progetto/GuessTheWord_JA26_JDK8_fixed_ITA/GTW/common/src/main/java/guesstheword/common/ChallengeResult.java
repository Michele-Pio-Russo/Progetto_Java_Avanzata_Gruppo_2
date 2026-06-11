package guesstheword.common;

import java.io.Serializable;

/**
 * Payload inviato dal server a entrambi i client al termine di una sfida.
 * Contiene il nome del vincitore (se presente), la parola corretta
 * e l'esito dal punto di vista del client ricevente.
 *
 * <p>Uso di enum annidato {@link Outcome} — Modulo 2 corso JA26.</p>
 */
public class ChallengeResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Esito della sfida dal punto di vista del client ricevente.
     * Esempio di enum con comportamento (Modulo 2 - corso JA26).
     */
    public enum Outcome {
        /** Il client ricevente ha risposto correttamente per primo. */
        WIN,
        /** L'avversario ha risposto correttamente per primo. */
        LOSS,
        /** Nessuno ha risposto correttamente entro il timeout. */
        DRAW;

        /**
         * Restituisce una stringa leggibile dell'esito in italiano.
         *
         * @return stringa esito
         */
        public String toDisplayString() {
            switch (this) {
                case WIN:  return "Hai vinto!";
                case LOSS: return "Hai perso.";
                case DRAW: return "Pareggio!";
                default:   return "Sconosciuto";
            }
        }

        /**
         * Restituisce l'emoji associata all'esito.
         *
         * @return stringa emoji
         */
        public String toEmoji() {
            switch (this) {
                case WIN:  return "\uD83C\uDFC6"; // 🏆
                case LOSS: return "\uD83D\uDE1E"; // 😞
                case DRAW: return "\uD83E\uDD1D"; // 🤝
                default:   return "?";
            }
        }
    }

    private final String winnerUsername;
    private final String correctWord;
    private final Outcome outcome;

    /**
     * Costruisce il risultato della sfida.
     *
     * @param winnerUsername username del vincitore, null in caso di pareggio
     * @param correctWord    parola originale corretta
     * @param outcome        esito dal punto di vista del client ricevente
     */
    public ChallengeResult(String winnerUsername, String correctWord, Outcome outcome) {
        this.winnerUsername = winnerUsername;
        this.correctWord = correctWord;
        this.outcome = outcome;
    }

    /**
     * Restituisce lo username del vincitore.
     *
     * @return username vincitore, o null in caso di pareggio
     */
    public String getWinnerUsername() {
        return winnerUsername;
    }

    /**
     * Restituisce la parola originale corretta.
     *
     * @return parola corretta
     */
    public String getCorrectWord() {
        return correctWord;
    }

    /**
     * Restituisce l'esito della sfida dal punto di vista del ricevente.
     *
     * @return esito {@link Outcome}
     */
    public Outcome getOutcome() {
        return outcome;
    }
}
