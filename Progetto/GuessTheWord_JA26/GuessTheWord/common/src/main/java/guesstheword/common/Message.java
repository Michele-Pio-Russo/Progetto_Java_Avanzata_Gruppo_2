package guesstheword.common;

import java.io.Serializable;

/**
 * Classe base per tutti i messaggi scambiati tra client e server.
 * Implementa Serializable per la trasmissione via ObjectStream.
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Tipo del messaggio. */
    public enum Type {
        // Auth
        LOGIN_REQUEST,
        LOGIN_RESPONSE,
        REGISTER_REQUEST,
        REGISTER_RESPONSE,

        // Game flow
        WAITING,
        CHALLENGE_START,
        CHALLENGE_ANSWER,
        CHALLENGE_RESULT,

        // History
        HISTORY_REQUEST,
        HISTORY_RESPONSE,

        // Misc
        ERROR,
        DISCONNECT
    }

    private Type type;
    private Object payload;

    /**
     * Costruisce un messaggio con tipo e payload.
     *
     * @param type    tipo del messaggio
     * @param payload dati associati al messaggio
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
     *
     * @return payload
     */
    public Object getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "Message{type=" + type + ", payload=" + payload + "}";
    }
}
