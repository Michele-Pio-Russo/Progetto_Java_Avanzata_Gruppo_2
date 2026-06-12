package indovinaparola.common;

import java.io.Serializable;

/**
 * Rappresenta una voce dello storico delle sfide di un giocatore.
 * Classe immutabile (tutti i campi final, nessun setter) — buona pratica
 * per oggetti di trasferimento dati (DTO) in ambiente multi-thread.
 * Compatibile JDK 8.
 */
public class VoceStorico implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String dataPartita;
    private final String avversario;
    private final String esito;
    private final long tempoRispostaMs;
    private final String parolaCorretta;

    /**
     * Costruisce una voce dello storico.
     *
     * @param dataPartita       data e ora della sfida (formato DATETIME SQLite)
     * @param avversario       nomeUtente dell'avversario
     * @param esito        esito: "win", "loss" o "draw"
     * @param tempoRispostaMs tempo di risposta in millisecondi (-1 se timeout)
     * @param parolaCorretta    parola corretta della sfida
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
     * Restituisce la data e l'ora della sfida.
     *
     * @return stringa datetime
     */
    public String getDataPartita() { return dataPartita; }

    /**
     * Restituisce lo nomeUtente dell'avversario.
     *
     * @return nomeUtente avversario
     */
    public String getAvversario() { return avversario; }

    /**
     * Restituisce l'esito della sfida.
     *
     * @return "win", "loss" o "draw"
     */
    public String getEsito() { return esito; }

    /**
     * Restituisce il tempo di risposta in millisecondi.
     *
     * @return ms di risposta, -1 se timeout
     */
    public long getTempoRispostaMs() { return tempoRispostaMs; }

    /**
     * Restituisce la parola corretta della sfida.
     *
     * @return parola corretta
     */
    public String getParolaCorretta() { return parolaCorretta; }

    @Override
    public String toString() {
        return "VoceStorico{dataPartita=" + dataPartita
            + ", avversario=" + avversario
            + ", esito=" + esito
            + ", ms=" + tempoRispostaMs
            + ", parola=" + parolaCorretta + "}";
    }
}
