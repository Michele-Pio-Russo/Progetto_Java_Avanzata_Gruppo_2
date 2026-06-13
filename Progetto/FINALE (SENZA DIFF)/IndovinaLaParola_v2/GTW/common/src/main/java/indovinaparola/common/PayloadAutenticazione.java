/**
* @file PayloadAutenticazione.java
* 
* @brief Payload per le richieste di autenticazione (login e registrazione).
* Trasporta nomeUtente e password in chiaro, la password viene hashata
* lato server prima di essere confrontata o salvata.
* 
* @author Gruppo 2
* 
* @version 1.0
*/

package indovinaparola.common;

import java.io.Serializable;

public class PayloadAutenticazione implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String nomeUtente;
    private final String password;

    /**
     * @brief Costruttore per creare un oggetto di tipo PayloadAutenticazione
     *
     * @param[in] nomeUtente nome utente
     * @param[in] password password in chiaro
     */
    public PayloadAutenticazione(String nomeUtente, String password) {
        this.nomeUtente = nomeUtente;
        this.password = password;
    }

    /**
     * @brief Restituisce lo nomeUtente.
     *
     * @return nomeUtente 
     */
    public String getNomeUtente() {
        return nomeUtente;
    }

    /**
     * @brief Restituisce la password in chiaro.
     *
     * @return password
     */
    public String getPassword() {
        return password;
    }
}
