/**
* @file VoceStorico.java
* 
* @brief Rappresenta una voce dello storico delle sfide di un giocatore.
* Classe immutabile (tutti i campi final, nessun setter),
* per oggetti di trasferimento dati (DTO) in ambiente multi-thread.
* 
* @author Gruppo 2
* 
* @version 1.0
*/

package indovinaparola.common;

import java.io.Serializable;

public class VoceStorico implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String dataPartita;
    private final String avversario;
    private final String esito;
    private final long tempoRispostaMs;
    private final String parolaCorretta;

    /**
     * @brief Costruttore per creare un oggetto di tipo VoceStorico
     *
     * @param[in] dataPartita data e ora della sfida (formato DATETIME SQLite)
     * @param[in] avversario nomeUtente dell'avversario
     * @param[in] esito esito: "win", "loss" o "draw"
     * @param[in] tempoRispostaMs tempo di risposta in millisecondi (-1 se timeout)
     * @param[in] parolaCorretta parola corretta della sfida
     */
    public VoceStorico(String dataPartita, String avversario, String esito,
                        long tempoRispostaMs, String parolaCorretta) {
        this.dataPartita = dataPartita;
        this.avversario = avversario;
        this.esito = esito;
        this.tempoRispostaMs = tempoRispostaMs;
        this.parolaCorretta = parolaCorretta;
    }

    /**
     * @brief Restituisce la data e l'ora della sfida.
     *
     * @return dataPartita
     */
    public String getDataPartita() { 
        return dataPartita; 
    }

    /**
     * @brief Restituisce lo nomeUtente dell'avversario.
     *
     * @return avversario
     */
    public String getAvversario() { 
        return avversario; 
    }

    /**
     * @brief Restituisce l'esito della sfida.
     *
     * @return esito
     */
    public String getEsito() { 
        return esito; 
    }

    /**
     * @brief Restituisce il tempo di risposta in millisecondi.
     *
     * @return tempoRispostaMs
     */
    
    public long getTempoRispostaMs() { 
        return tempoRispostaMs; 
    }

    /**
     * @brief Restituisce la parola corretta della sfida.
     *
     * @return parolaCorretta
     */
    public String getParolaCorretta() { 
        return parolaCorretta; 
    }

    @Override
    public String toString() {
        return "VoceStorico{dataPartita=" + dataPartita
            + ", avversario=" + avversario
            + ", esito=" + esito
            + ", ms=" + tempoRispostaMs
            + ", parola=" + parolaCorretta + "}";
    }
}
