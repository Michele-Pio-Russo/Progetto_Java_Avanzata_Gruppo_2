/**
* @file RispostaAutenticazione.java
* 
* @brief Payload di risposta del server per login e registrazione.
* Indica l'esito dell'operazione, un messaggio descrittivo
* e, in caso di successo, il ruolo assegnato all'utente.
* 
* @author Gruppo 2
* 
* @version 1.0
*/

package indovinaparola.common;

import java.io.Serializable;

public class RispostaAutenticazione implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean successo;
    private final String messaggio;
    private final String ruolo;   ///@brief  "admin" | "player" | null se fallito

    /**
     * @brief Costruttore per creare un oggetto di tipo RispostaAutenticazione
     *
     * @param[in] successo true se l'operazione è riuscita
     * @param[in] messaggio messaggio descrittivo da mostrare al client
     * @param[in] ruolo ruolo dell'utente ("admin" o "player"), null se fallita
     */
    public RispostaAutenticazione(boolean successo, String messaggio, String ruolo) {
        this.successo = successo;
        this.messaggio = messaggio;
        this.ruolo = ruolo;
    }

    /**
     * @brief Indica se l'autenticazione o la registrazione è riuscita. (true se è avvenuta con successo)
     *
     * @return successo
     */
    public boolean isSuccesso() {
        return successo;
    }

    /**
     * @brief Restituisce il messaggio di esito da mostrare all'utente.
     *
     * @return messaggio 
     */
    public String getMessaggio() {
        return messaggio;
    }

    /**
     * @brief Restituisce il ruolo dell'utente autenticato. ("admin", "player" o null)
     *
     * @return ruolo
     */
    public String getRuolo() {
        return ruolo;
    }
}
