package indovinaparola.common;

import java.io.Serializable;

/**
 * Payload per le richieste di autenticazione (login e registrazione).
 * Trasporta nomeUtente e password in chiaro; la password viene hashata
 * lato server prima di essere confrontata o salvata.
 */
public class PayloadAutenticazione implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String nomeUtente;
    private final String password;

    /**
     * Costruisce il carico di autenticazione.
     *
     * @param nomeUtente nome utente
     * @param password password in chiaro
     */
    public PayloadAutenticazione(String nomeUtente, String password) {
        this.nomeUtente = nomeUtente;
        this.password = password;
    }

    /**
     * Restituisce lo nomeUtente.
     *
     * @return nomeUtente
     */
    public String getNomeUtente() {
        return nomeUtente;
    }

    /**
     * Restituisce la password in chiaro.
     *
     * @return password
     */
    public String getPassword() {
        return password;
    }
}
