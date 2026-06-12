package indovinaparola.common;

import java.io.Serializable;

/**
 * Payload di risposta del server per login e registrazione.
 * Indica l'esito dell'operazione, un messaggio descrittivo
 * e, in caso di successo, il ruolo assegnato all'utente.
 */
public class RispostaAutenticazione implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean successo;
    private final String messaggio;
    private final String ruolo;   // "admin" | "player" | null se fallito

    /**
     * Costruisce la risposta di autenticazione.
     *
     * @param successo true se l'operazione è riuscita
     * @param messaggio messaggio descrittivo da mostrare al client
     * @param ruolo    ruolo dell'utente ("admin" o "player"), null se fallita
     */
    public RispostaAutenticazione(boolean successo, String messaggio, String ruolo) {
        this.successo = successo;
        this.messaggio = messaggio;
        this.ruolo = ruolo;
    }

    /**
     * Indica se l'autenticazione o la registrazione è riuscita.
     *
     * @return true se l'operazione è avvenuta con successo
     */
    public boolean isSuccesso() {
        return successo;
    }

    /**
     * Restituisce il messaggio di esito da mostrare all'utente.
     *
     * @return messaggio esito
     */
    public String getMessaggio() {
        return messaggio;
    }

    /**
     * Restituisce il ruolo dell'utente autenticato.
     *
     * @return "admin", "player", oppure null in caso di fallimento
     */
    public String getRuolo() {
        return ruolo;
    }
}
