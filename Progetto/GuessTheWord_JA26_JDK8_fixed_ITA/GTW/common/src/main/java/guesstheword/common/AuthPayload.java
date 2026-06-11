package guesstheword.common;

import java.io.Serializable;

/**
 * Payload per le richieste di autenticazione (login e registrazione).
 * Trasporta username e password in chiaro; la password viene hashata
 * lato server prima di essere confrontata o salvata.
 */
public class AuthPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String username;
    private final String password;

    /**
     * Costruisce il payload di autenticazione.
     *
     * @param username nome utente
     * @param password password in chiaro
     */
    public AuthPayload(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * Restituisce lo username.
     *
     * @return username
     */
    public String getUsername() {
        return username;
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
