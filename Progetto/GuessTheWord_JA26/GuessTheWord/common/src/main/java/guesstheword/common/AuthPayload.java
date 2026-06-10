package guesstheword.common;

import java.io.Serializable;

/**
 * Payload per la richiesta di login o registrazione.
 */
public class AuthPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;
    private String password;

    /**
     * Crea un payload di autenticazione.
     *
     * @param username nome utente
     * @param password password in chiaro (sarà hashata lato server)
     */
    public AuthPayload(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * @return username
     */
    public String getUsername() { return username; }

    /**
     * @return password
     */
    public String getPassword() { return password; }
}
