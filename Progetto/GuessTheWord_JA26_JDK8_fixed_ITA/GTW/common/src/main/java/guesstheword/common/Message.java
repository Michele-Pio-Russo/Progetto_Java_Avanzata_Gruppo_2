package guesstheword.common;

import java.io.Serializable;

/**
 * Classe base del protocollo di comunicazione client-server.
 * Ogni messaggio ha un tipo (enum {@link Type}) e un payload Object
 * che viene castato in base al tipo ricevuto.
 * Implementa {@link Serializable} per la trasmissione via ObjectStream.
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Tipi di messaggio scambiati nel protocollo.
     * Uso di enum (Modulo 2 - Tipi enum del corso JA26).
     */
    public enum Type {
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

    private final Type type;
    private final Object payload;

    /**
     * Costruisce un messaggio con tipo e payload.
     *
     * @param type    tipo del messaggio (non null)
     * @param payload dati associati al messaggio (può essere null)
     */
    public Message(Type type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    /**
     * Restituisce il tipo del messaggio.
     *
     * @return tipo del messaggio
     */
    public Type getType() {
        return type;
    }

    /**
     * Restituisce il payload del messaggio.
     * Il chiamante deve effettuare il cast al tipo appropriato
     * in base a {@link #getType()}.
     *
     * @return payload (può essere null)
     */
    public Object getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "Message{type=" + type + ", payload=" + payload + "}";
    }
}
