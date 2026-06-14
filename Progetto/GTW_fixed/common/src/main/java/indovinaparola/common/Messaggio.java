/**
* @file Messaggio.java
* 
* @brief Sistema di comunicazione client-server
* Ogni messaggio ha un tipo (enum {@link Tipo}) e un carico Object
* che viene castato in base al tipo ricevuto.
* Implementa {@link Serializable} per la trasmissione via ObjectStream.
* 
* @author Gruppo 2
* 
* @version 1.0
*/

package indovinaparola.common;

import java.io.Serializable;

public class Messaggio implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * @brief Tipi di messaggio scambiati nel protocollo.
     */
    public enum Tipo {
        LOGIN_REQUEST,  ///@brief Richiesta di login da client a server.
        
        LOGIN_RESPONSE,  ///@brief Risposta del server al login.
        
        REGISTER_REQUEST,    ///@brief Richiesta di registrazione da client a server.
        
        REGISTER_RESPONSE,   ///@brief Risposta del server alla registrazione.
        
        WAITING,     ///@brief Il server notifica il client che è in attesa di avversario.
        
        CHALLENGE_START,     ///@brief Il server invia la sfida ai due client.
        
        CHALLENGE_ANSWER,    ///@brief Il client invia la propria risposta al server.
        
        CHALLENGE_RESULT,    ///@brief Il server invia l'esito della sfida ai due client.
        
        HISTORY_REQUEST,     ///@brief Il client richiede lo storico delle proprie partite.
        
        HISTORY_RESPONSE,    ///@brief Il server risponde con la lista delle partite passate.
        
        REQUEUE_REQUEST,     ///@brief Il client (già autenticato) richiede di rientrare in lista d'attesa per una nuova partita.
        
        ERROR,   ///@brief Messaggio di errore generico.
        
        DISCONNECT   ///@brief Notifica di disconnessione.
    }

    private final Tipo tipo;
    private final Object carico;

    /**
     * @brief Costruttore per creare un oggetto di tipo messaggio
     *
     * @param[in] tipo tipo del messaggio (non null)
     * @param[in] carico dati associati al messaggio (può essere null)
     */
    public Messaggio(Tipo tipo, Object carico) {
        this.tipo = tipo;
        this.carico = carico;
    }

    /**
     * @brief Restituisce il tipo del messaggio.
     *
     * @return tipo
     */
    public Tipo getTipo() {
        return tipo;
    }

    /**
     * @brief Restituisce il carico del messaggio. (può essere null)
     *
     * @return carico
     */
    public Object getCarico() {
        return carico;
    }

    @Override
    public String toString() {
        return "Messaggio{tipo=" + tipo + ", carico=" + carico + "}";
    }
}
