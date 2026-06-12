package indovinaparola.common;

import java.io.Serializable;

/**
 * Classe base del protocollo di comunicazione client-server.
 * Ogni messaggio ha un tipo (enum {@link Tipo}) e un carico Object
 * che viene castato in base al tipo ricevuto.
 * Implementa {@link Serializable} per la trasmissione via ObjectStream.
 */
public class Messaggio implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Tipi di messaggio scambiati nel protocollo.
     * Uso di enum (Modulo 2 - Tipi enum del corso JA26).
     */
    public enum Tipo {
        /** Richiesta di login da client a server. */
        LOGIN_REQUEST,
        /** Risposta del server al login. */
        LOGIN_RESPONSE,
        /** Richiesta di registrazione da client a server. */
        REGISTER_REQUEST,
        /** Risposta del server alla registrazione. */
        REGISTER_RESPONSE,
        /** Server notifica il client che è in attesa di avversario. */
        WAITING,
        /** Server invia la sfida ai due client. */
        CHALLENGE_START,
        /** Client invia la propria risposta al server. */
        CHALLENGE_ANSWER,
        /** Server invia l'esito della sfida ai due client. */
        CHALLENGE_RESULT,
        /** Client richiede lo storico delle proprie partite. */
        HISTORY_REQUEST,
        /** Server risponde con la lista delle partite passate. */
        HISTORY_RESPONSE,
        /** Client (già autenticato) richiede di rientrare in lista d'attesa per una nuova partita. */
        REQUEUE_REQUEST,
        /** Messaggio di errore generico. */
        ERROR,
        /** Notifica di disconnessione. */
        DISCONNECT
    }

    private final Tipo tipo;
    private final Object carico;

    /**
     * Costruisce un messaggio con tipo e carico.
     *
     * @param tipo    tipo del messaggio (non null)
     * @param carico dati associati al messaggio (può essere null)
     */
    public Messaggio(Tipo tipo, Object carico) {
        this.tipo = tipo;
        this.carico = carico;
    }

    /**
     * Restituisce il tipo del messaggio.
     *
     * @return tipo del messaggio
     */
    public Tipo getTipo() {
        return tipo;
    }

    /**
     * Restituisce il carico del messaggio.
     * Il chiamante deve effettuare il cast al tipo appropriato
     * in base a {@link #getTipo()}.
     *
     * @return carico (può essere null)
     */
    public Object getCarico() {
        return carico;
    }

    @Override
    public String toString() {
        return "Messaggio{tipo=" + tipo + ", carico=" + carico + "}";
    }
}
