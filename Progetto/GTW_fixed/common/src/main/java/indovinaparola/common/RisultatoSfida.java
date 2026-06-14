/**
* @file RisultatoSfida.java
* 
* @brief Payload inviato dal server a entrambi i client al termine di una sfida.
* Contiene il nome del vincitore (se presente), la parola corretta
* e l'esito dal punto di vista del client ricevente.
* 
* @author Gruppo 2
* 
* @version 1.0
*/

package indovinaparola.common;

import java.io.Serializable;

public class RisultatoSfida implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * @brief Esito della sfida dal punto di vista del client ricevente.
     */
    public enum Esito {
        
        VITTORIA,   ///@brief Il client ricevente ha risposto correttamente per primo.
        
        SCONFITTA,  ///@brief L'avversario ha risposto correttamente per primo.
        
        PAREGGIO;   ///@brief Nessuno ha risposto correttamente entro il timeout.

        /**
         * @brief Restituisce una stringa leggibile dell'esito in italiano.
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
         * @brief Restituisce l'emoji associata all'esito.
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
     * @brief Costruttore per creare un oggetto di tipo RisultatoSfida
     *
     * @param[in] nomeUtenteVincitore nome utente del vincitore, null in caso di pareggio
     * @param[in] parolaCorretta parola originale corretta
     * @param[in] esito esito dal punto di vista del client ricevente
     */
    public RisultatoSfida(String nomeUtenteVincitore, String parolaCorretta, Esito esito) {
        this.nomeUtenteVincitore = nomeUtenteVincitore;
        this.parolaCorretta = parolaCorretta;
        this.esito = esito;
    }

    /**
     * @brief Restituisce il nome utente del vincitore.
     *
     * @return nomeUtenteVincitore, o null in caso di pareggio
     */
    public String getNomeUtenteVincitore() {
        return nomeUtenteVincitore;
    }

    /**
     * @brief Restituisce la parola originale corretta.
     *
     * @return parolaCorretta
     */
    public String getParolaCorretta() {
        return parolaCorretta;
    }

    /**
     * @brief Restituisce l'esito della sfida dal punto di vista del ricevente.
     *
     * @return esito
     */
    public Esito getEsito() {
        return esito;
    }
}
