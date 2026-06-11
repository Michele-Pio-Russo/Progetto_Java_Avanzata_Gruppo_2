package guesstheword.common;

import java.io.Serializable;

/**
 * Payload di risposta del server per login e registrazione.
 * Indica l'esito dell'operazione, un messaggio descrittivo
 * e, in caso di successo, il ruolo assegnato all'utente.
 */
public class AuthResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String message;
    private final String role;   // "admin" | "player" | null se fallito

    /**
     * Costruisce la risposta di autenticazione.
     *
     * @param success true se l'operazione è riuscita
     * @param message messaggio descrittivo da mostrare al client
     * @param role    ruolo dell'utente ("admin" o "player"), null se fallita
     */
    public AuthResponse(boolean success, String message, String role) {
        this.success = success;
        this.message = message;
        this.role = role;
    }

    /**
     * Indica se l'autenticazione o la registrazione è riuscita.
     *
     * @return true se l'operazione è avvenuta con successo
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Restituisce il messaggio di esito da mostrare all'utente.
     *
     * @return messaggio esito
     */
    public String getMessage() {
        return message;
    }

    /**
     * Restituisce il ruolo dell'utente autenticato.
     *
     * @return "admin", "player", oppure null in caso di fallimento
     */
    public String getRole() {
        return role;
    }
}
