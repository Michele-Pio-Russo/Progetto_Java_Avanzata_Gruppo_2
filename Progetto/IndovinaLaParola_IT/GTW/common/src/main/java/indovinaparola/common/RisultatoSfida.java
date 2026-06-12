package indovinaparola.common;

import java.io.Serializable;

/**
 * Payload inviato dal server a entrambi i client al termine di una sfida.
 * Contiene il nome del vincitore (se presente), la parola corretta
 * e l'esito dal punto di vista del client ricevente.
 *
 * <p>Uso di enum annidato {@link Esito} — Modulo 2 corso JA26.</p>
 */
public class RisultatoSfida implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Esito della sfida dal punto di vista del client ricevente.
     * Esempio di enum con comportamento (Modulo 2 - corso JA26).
     */
    public enum Esito {
        /** Il client ricevente ha risposto correttamente per primo. */
        VITTORIA,
        /** L'avversario ha risposto correttamente per primo. */
        SCONFITTA,
        /** Nessuno ha risposto correttamente entro il timeout. */
        PAREGGIO;

        /**
         * Restituisce una stringa leggibile dell'esito in italiano.
         *
         * @return stringa esito
         */
        public String toDisplayString() {
            switch (this) {
                case VITTORIA:  return "Hai vinto!";
                case SCONFITTA: return "Hai perso.";
                case PAREGGIO: return "Pareggio!";
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
                case VITTORIA:  return "\uD83C\uDFC6"; // 🏆
                case SCONFITTA: return "\uD83D\uDE1E"; // 😞
                case PAREGGIO: return "\uD83E\uDD1D"; // 🤝
                default:   return "?";
            }
        }
    }

    private final String nomeUtenteVincitore;
    private final String parolaCorretta;
    private final Esito esito;

    /**
     * Costruisce il risultato della sfida.
     *
     * @param nomeUtenteVincitore nomeUtente del vincitore, null in caso di pareggio
     * @param parolaCorretta    parola originale corretta
     * @param esito        esito dal punto di vista del client ricevente
     */
    public RisultatoSfida(String nomeUtenteVincitore, String parolaCorretta, Esito esito) {
        this.nomeUtenteVincitore = nomeUtenteVincitore;
        this.parolaCorretta = parolaCorretta;
        this.esito = esito;
    }

    /**
     * Restituisce lo nomeUtente del vincitore.
     *
     * @return nomeUtente vincitore, o null in caso di pareggio
     */
    public String getNomeUtenteVincitore() {
        return nomeUtenteVincitore;
    }

    /**
     * Restituisce la parola originale corretta.
     *
     * @return parola corretta
     */
    public String getParolaCorretta() {
        return parolaCorretta;
    }

    /**
     * Restituisce l'esito della sfida dal punto di vista del ricevente.
     *
     * @return esito {@link Esito}
     */
    public Esito getEsito() {
        return esito;
    }
}
