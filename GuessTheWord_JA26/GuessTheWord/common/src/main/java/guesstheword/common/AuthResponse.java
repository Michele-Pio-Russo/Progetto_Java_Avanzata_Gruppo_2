package guesstheword.common;

import java.io.Serializable;

/**
 * Payload di risposta per login/registrazione.
 */
public class AuthResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;
    private String message;
    private String role; // "admin" o "player"

    /**
     * Costruisce la risposta di autenticazione.
     *
     * @param success true se l'operazione è riuscita
     * @param message messaggio descrittivo
     * @param role    ruolo dell'utente (null se fallita)
     */
    public AuthResponse(boolean success, String message, String role) {
        this.success = success;
        this.message = message;
        this.role = role;
    }

    /** @return true se autenticazione riuscita */
    public boolean isSuccess() { return success; }

    /** @return messaggio di esito */
    public String getMessage() { return message; }

    /** @return ruolo utente */
    public String getRole() { return role; }
}
