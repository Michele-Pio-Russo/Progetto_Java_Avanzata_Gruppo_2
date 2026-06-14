/**
 * @file CoordinatoreGioco.java
 * @brief Questo file contiene gli attributi, il costruttore e i metodi setter, getter e toString della classe CoordinatoreGioco
 *
 * Questa classe permette di istanziare un oggetto CoordinatoreGioco, i metodi setter e getter permettono di
 * ottenere e modificare informazioni relative agli attributi, inoltre il metodo toString permette di stampare 
 * le informazioni relative alla classe CoordinatoreGioco.
 *
 * @author Gruppo 2
 * @date 
 * @version 1.0.0
 */
package indovinaparola.server.model;

import indovinaparola.common.*;
import indovinaparola.server.db.GestoreDatabase;
import indovinaparola.server.service.AnalizzatoreDocumenti;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * @brief Coordinatore centrale della logica di gioco lato server.
 *
 * Gestisce: autenticazione, lista d'attesa giocatori, avvio e risoluzione
 * delle sfide, timeout automatico e persistenza dei risultati nel database.
 *
 * Tutti i metodi pubblici sono {@code synchronized} perché questo oggetto
 * è condiviso tra i thread di ogni {@link GestoreClient}. Il monitor implicito
 * di Java garantisce l'accesso esclusivo e la visibilità degli aggiornamenti
 *.
 *
 * Il timeout della sfida è gestito con {@link ScheduledExecutorService}
 *, che evita l'uso di {@code Thread.sleep()} nei thread di gioco.
 *
 * La callback di log usa {@link Consumer}&lt;String&gt; come interfaccia funzionale
 * assegnata tramite lambda dal controller JavaFX.
 */
public class CoordinatoreGioco {

    private static final Logger LOGGER = Logger.getLogger(CoordinatoreGioco.class.getName()); ///< Logger della classe CoordinatoreGioco

    /** @brief Durata massima di una sfida in secondi (configurabile). */
    private int timeoutSeconds = 60; ///< Durata massima di una sfida in secondi

    /** @brief Numero di parole dell'estratto inviato ai client. */
    private int excerptWords = 60; ///< Numero di parole dell'estratto inviato ai client

    private final GestoreDatabase db; ///< Riferimento al gestore del database
    private AnalizzatoreDocumenti analizzatore; ///< Riferimento all'analizzatore documenti

    /** @brief Giocatori autenticati in attesa di un avversario. */
    private final List<GestoreClient> giocatoriInAttesa = new ArrayList<>(); ///< Lista dei client autenticati in attesa di giocare

    /** @brief Tutti i client connessi al server. */
    private final List<GestoreClient> tuttiIClient = new ArrayList<>(); ///< Lista di tutti i client connessi

    /**
     * @brief Callback per aggiornare la TextArea di log nella GUI del server.
     * Assegnata tramite lambda dal controller.
     */
    private Consumer<String> statusCallback; ///< Callback per notificare messaggi alla GUI

    /**
     * @brief Executor per il timeout automatico della sfida.
     * Un thread singolo è sufficiente per gestire i timeout sequenziali.
     *
     */
    private final ScheduledExecutorService scheduler = ///< Executor per schedulare i timeout delle sfide
        Executors.newSingleThreadScheduledExecutor();

    // --- Stato della sfida in corso ---
    private GestoreClient giocatore1; ///< Primo giocatore impegnato nella sfida corrente
    private GestoreClient giocatore2; ///< Secondo giocatore impegnato nella sfida corrente
    private String parolaOriginaleCorrente; ///< Parola da indovinare, in chiaro
    private String parolaCifrataCorrente; ///< Parola cifrata da mostrare ai giocatori
    private int spostamentoCorrente; ///< Chiave (spostamento) del cifrario
    private String estrattoCorrente; ///< Estratto del testo con la parola cifrata
    private long tempoInizioSfida; ///< Timestamp di inizio della sfida
    private boolean sfidaAttiva = false;
    private ScheduledFuture<?> taskTimeout; ///< Task di timeout per la sfida corrente

    /**
     * @brief Costruisce il coordinatore del gioco.
     *
     * @param[in] db       gestore del database (già connesso)
     * @param[in] analizzatore analizzatore documenti, può essere null inizialmente
     */
    public CoordinatoreGioco(GestoreDatabase db, AnalizzatoreDocumenti analizzatore) {
        this.db = db;
        this.analizzatore = analizzatore;
    }

    /**
     * @brief Configura i parametri di gioco letti dalle properties del server.
     *
     * @param[in] timeoutSeconds durata massima sfida in secondi
     * @param[in] excerptWords   numero di parole dell'estratto
     */
    public void setGameConfig(int timeoutSeconds, int excerptWords) {
        this.timeoutSeconds = timeoutSeconds;
        this.excerptWords = excerptWords;
    }

    /**
     * @brief Imposta la callback per i messaggi di log da mostrare nella GUI server.
     * Viene assegnata dal controller tramite espressione lambda.
     *
     * Esempio di uso nel controller:
     * <pre>
     * coordinator.setStatusCallback(msg -&gt;
     *     Platform.runLater(() -&gt; logArea.appendText(msg + "\n")));
     * </pre>
     *
     * @param[in] callback {@link Consumer}&lt;String&gt; che riceve ogni messaggio di log
     */
    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    /**
     * @brief Aggiorna l'analizzatore documenti (dopo una nuova analisi da parte dell'admin).
     *
     * @param[in] analizzatore nuovo analizzatore con i risultati aggiornati
     */
    public synchronized void setAnalyzer(AnalizzatoreDocumenti analizzatore) {
        this.analizzatore = analizzatore;
        log("Analizzatore aggiornato: " + analizzatore.getTermFrequency().size() + " parole.");
    }


    /**
     * @brief Gestisce una richiesta di login da parte di un client.
     * Autentica le credenziali nel database e, se valide, aggiunge il
     * giocatore alla lista d'attesa.
     *
     * @param[in] client  client che ha inviato la richiesta
     * @param[in] carico credenziali di autenticazione
     */
    public synchronized void gestisciLogin(GestoreClient client, PayloadAutenticazione carico) {
        String ruolo = db.autentica(carico.getNomeUtente(), carico.getPassword());
        if (ruolo != null) {
            client.setNomeUtente(carico.getNomeUtente());
            client.setRuolo(ruolo);
            client.setAutenticato(true);
            client.invia(new Messaggio(Messaggio.Tipo.LOGIN_RESPONSE,
                new RispostaAutenticazione(true, "Login effettuato.", ruolo)));
            log("Login: " + carico.getNomeUtente() + " [" + ruolo + "]");

            if ("player".equals(ruolo)) {
                aggiungiInListaAttesa(client);
            }
        } else {
            client.invia(new Messaggio(Messaggio.Tipo.LOGIN_RESPONSE,
                new RispostaAutenticazione(false, "Credenziali non valide.", null)));
            log("Login fallito per: " + carico.getNomeUtente());
        }
    }

    /**
     * @brief Gestisce una richiesta di registrazione da parte di un client.
     * Valida i dati e registra il nuovo utente nel database.
     *
     * @param[in] client  client richiedente
     * @param[in] carico credenziali del nuovo account
     */
    public synchronized void gestisciRegistrazione(GestoreClient client, PayloadAutenticazione carico) {
        String nome = carico.getNomeUtente() == null ? "" : carico.getNomeUtente().trim();
        String pass = carico.getPassword()    == null ? "" : carico.getPassword().trim();

        if (nome.isEmpty() || pass.isEmpty()) {
            client.invia(new Messaggio(Messaggio.Tipo.REGISTER_RESPONSE,
                new RispostaAutenticazione(false, "Username e password non possono essere vuoti o contenere solo spazi.", null)));
            return;
        }
        if (nome.length() < 3) {
            client.invia(new Messaggio(Messaggio.Tipo.REGISTER_RESPONSE,
                new RispostaAutenticazione(false, "Username deve avere almeno 3 caratteri.", null)));
            return;
        }
        if (pass.length() < 4) {
            client.invia(new Messaggio(Messaggio.Tipo.REGISTER_RESPONSE,
                new RispostaAutenticazione(false, "Password deve avere almeno 4 caratteri.", null)));
            return;
        }
        boolean ok = db.registraUtente(nome, pass);
        if (ok) {
            client.invia(new Messaggio(Messaggio.Tipo.REGISTER_RESPONSE,
                new RispostaAutenticazione(true, "Registrazione completata. Ora accedi.", null)));
            log("Registrato: " + carico.getNomeUtente());
        } else {
            client.invia(new Messaggio(Messaggio.Tipo.REGISTER_RESPONSE,
                new RispostaAutenticazione(false, "Username gia' esistente.", null)));
        }
    }


    /**
     * @brief Gestisce la richiesta di un client già autenticato di rientrare
     * in lista d'attesa per una nuova partita (pulsante "Nuova partita"
     * lato client, dopo {@code CHALLENGE_RESULT}).
     *
     * A differenza di {@link #gestisciLogin}, questo metodo non richiede
     * nuove credenziali: il client è già autenticato sulla stessa
     * connessione TCP. Evita inoltre di reinserire due volte lo stesso
     * client se già presente in {@code giocatoriInAttesa}.
     *
     * @param[in] client client già autenticato che richiede una nuova partita
     */
    public synchronized void gestisciNuovaRichiesta(GestoreClient client) {
        if (!client.isAutenticato() || !"player".equals(client.getRuolo())) {
            client.invia(new Messaggio(Messaggio.Tipo.ERROR,
                "Devi effettuare il login prima di richiedere una nuova partita."));
            return;
        }
        if (giocatoriInAttesa.contains(client)) {
            return;
        }
        log("Richiesta nuova partita: " + client.getNomeUtente());
        aggiungiInListaAttesa(client);
    }

    /**
     * @brief Aggiunge un giocatore alla lista d'attesa e avvia la sfida
     * quando sono presenti esattamente due giocatori.
     *
     * @pre Il client non deve essere già in attesa.
     * @post Il client viene inserito in lista e la partita ha inizio se i giocatori in attesa diventano due.
     *
     * @param[in] client client da aggiungere alla lista
     */
    private void aggiungiInListaAttesa(GestoreClient client) {
        // Controlla che lo stesso username non sia già in lista d'attesa
        // (evita che un giocatore giochi contro se stesso con due client)
        for (GestoreClient g : giocatoriInAttesa) {
            if (g.getNomeUtente() != null &&
                g.getNomeUtente().equalsIgnoreCase(client.getNomeUtente())) {
                client.invia(new Messaggio(Messaggio.Tipo.ERROR,
                    "Sei gia' in lista d'attesa con un altro client. Impossibile giocare contro te stesso."));
                log("Accesso duplicato bloccato: " + client.getNomeUtente());
                return;
            }
        }

        client.invia(new Messaggio(Messaggio.Tipo.WAITING,
            "In attesa dell'avversario... (" + (giocatoriInAttesa.size() + 1) + "/2)"));
        giocatoriInAttesa.add(client);
        log("In attesa: " + client.getNomeUtente()
            + " (" + giocatoriInAttesa.size() + "/2)");

        if (giocatoriInAttesa.size() >= 2) {
            giocatore1 = giocatoriInAttesa.remove(0);
            giocatore2 = giocatoriInAttesa.remove(0);
            avviaSfida(giocatore1, giocatore2);
        }
    }

    /**
     * @brief Avvia una nuova sfida tra due giocatori.
     * Estrae un estratto dal corpus analizzato, sceglie una parola,
     * la cifra con il Cifrario di Cesare con spostamento casuale, invia il
     * carico ai due client e schedula il timeout automatico.
     *
     * @param[in] p1 primo giocatore
     * @param[in] p2 secondo giocatore
     */
    private void avviaSfida(GestoreClient p1, GestoreClient p2) {
        if (analizzatore == null || !analizzatore.hasResults()) {
            String err = "Il server non ha documenti analizzati. Impossibile avviare la sfida.";
            log("ERRORE: " + err);
            p1.invia(new Messaggio(Messaggio.Tipo.ERROR, err));
            p2.invia(new Messaggio(Messaggio.Tipo.ERROR, err));
            return;
        }

        // Estrae estratto e parola
        String excerpt = analizzatore.extractRandomExcerpt(excerptWords);
        String parola = analizzatore.selectWordFromExcerpt(excerpt, 2);
        if (parola == null) {
            log("Impossibile selezionare parola dall'estratto. Riprovo...");
            excerpt = analizzatore.extractRandomExcerpt(excerptWords);
            parola = analizzatore.selectWordFromExcerpt(excerpt, 1);
        }
        if (parola == null) {
            log("ERRORE: nessuna parola idonea trovata.");
            return;
        }

        // Cifra la parola con spostamento casuale 1–25
        int spostamento = new Random().nextInt(25) + 1;
        String encrypted = CifrarioCesare.cifra(parola, spostamento);

        // Sostituisce la parola nell'estratto con quella cifrata tra parentesi quadre
        // Pattern.quote() protegge da caratteri speciali regex nella parola
        String textWithCipher = excerpt.replaceAll(
            "(?i)\\b" + java.util.regex.Pattern.quote(parola) + "\\b", "[" + encrypted + "]");

        // Salva lo stato della sfida in corso
        parolaOriginaleCorrente = parola;
        parolaCifrataCorrente = encrypted;
        spostamentoCorrente = spostamento;
        estrattoCorrente = textWithCipher;
        tempoInizioSfida = System.currentTimeMillis();
        sfidaAttiva = true;

        List<String> encryptedList = Collections.singletonList(encrypted);
        PayloadSfida carico = new PayloadSfida(0, textWithCipher, encryptedList, timeoutSeconds);

        p1.invia(new Messaggio(Messaggio.Tipo.CHALLENGE_START, carico));
        p2.invia(new Messaggio(Messaggio.Tipo.CHALLENGE_START, carico));

        log("Sfida avviata: " + p1.getNomeUtente() + " vs " + p2.getNomeUtente()
            + " | parola: " + parola + " | spostamento: " + spostamento);

        // Timeout automatico con ScheduledExecutorService
        // La lambda cattura 'this' per chiamare terminaSfida in modo synchronized
        taskTimeout = scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                synchronized (CoordinatoreGioco.this) {
                    if (sfidaAttiva) {
                        log("Timeout scaduto per la sfida.");
                        terminaSfida(null, -1, true);
                    }
                }
            }
        }, timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * @brief Gestisce la risposta di un giocatore alla sfida in corso.
     * Se la risposta è corretta, termina la sfida decretando il vincitore.
     * Se è errata, notifica solo il client che ha sbagliato.
     *
     * @pre Una sfida deve essere attualmente in corso e il client deve esservi partecipante.
     * @post Se la risposta è corretta, la sfida finisce e vengono registrati i risultati. Altrimenti viene inviato un errore al client.
     *
     * @param[in] client client che ha inviato la risposta
     * @param[in] guess  parola inserita dal giocatore
     */
    public synchronized void gestisciRisposta(GestoreClient client, String guess) {
        if (!sfidaAttiva) return;
        if (client != giocatore1 && client != giocatore2) return;
        if (guess == null) return;

        long responseTime = System.currentTimeMillis() - tempoInizioSfida;

        if (guess.trim().equalsIgnoreCase(parolaOriginaleCorrente)) {
            // Risposta corretta → il client che ha risposto vince
            terminaSfida(client, responseTime, false);
        } else {
            // Risposta errata → notifica solo questo client, può riprovare
            client.invia(new Messaggio(Messaggio.Tipo.ERROR,
                "Risposta errata. Riprova!"));
            log(client.getNomeUtente() + " ha risposto erroneamente: " + guess);
        }
    }

    /**
     * @brief Termina la sfida in corso, invia i risultati ai client e persiste
     * i dati nel database in una transazione atomica.
     *
     * @param[in] winner         client vincitore, null in caso di pareggio/timeout
     * @param[in] tempoRispostaMs tempo di risposta del vincitore in ms
     * @param[in] isDraw         true se la sfida è terminata in pareggio (timeout)
     */
    private void terminaSfida(GestoreClient winner, long tempoRispostaMs, boolean isDraw) {
        if (!sfidaAttiva) return;
        sfidaAttiva = false;
        if (taskTimeout != null) taskTimeout.cancel(false);

        GestoreClient loser = (winner == giocatore1) ? giocatore2 : giocatore1;

        if (!isDraw && winner != null) {
            // Vittoria
            winner.invia(new Messaggio(Messaggio.Tipo.CHALLENGE_RESULT,
                new RisultatoSfida(winner.getNomeUtente(), parolaOriginaleCorrente, RisultatoSfida.Esito.VITTORIA)));
            loser.invia(new Messaggio(Messaggio.Tipo.CHALLENGE_RESULT,
                new RisultatoSfida(winner.getNomeUtente(), parolaOriginaleCorrente, RisultatoSfida.Esito.SCONFITTA)));

            // Salva in transazione
            log("DB: salvataggio vittoria in corso...");
            if (db == null) {
                log("DB: ERRORE - riferimento db e' null!");
            } else {
                db.saveChallengeWithResults(
                    estrattoCorrente, parolaCifrataCorrente, parolaOriginaleCorrente, spostamentoCorrente,
                    winner.getNomeUtente(), loser.getNomeUtente(), false, tempoRispostaMs);
            }

            log("Vince: " + winner.getNomeUtente() + " in " + tempoRispostaMs + "ms. Parola: " + parolaOriginaleCorrente);
        } else {
            // Pareggio / timeout
            RisultatoSfida draw = new RisultatoSfida(null, parolaOriginaleCorrente, RisultatoSfida.Esito.PAREGGIO);
            if (giocatore1 != null) giocatore1.invia(new Messaggio(Messaggio.Tipo.CHALLENGE_RESULT, draw));
            if (giocatore2 != null) giocatore2.invia(new Messaggio(Messaggio.Tipo.CHALLENGE_RESULT, draw));

            String u1 = giocatore1 != null ? giocatore1.getNomeUtente() : "";
            String u2 = giocatore2 != null ? giocatore2.getNomeUtente() : "";
            log("DB: salvataggio pareggio in corso... u1=" + u1 + " u2=" + u2);
            if (db == null) {
                log("DB: ERRORE - riferimento db e' null!");
            } else {
                db.saveChallengeWithResults(
                    estrattoCorrente, parolaCifrataCorrente, parolaOriginaleCorrente, spostamentoCorrente,
                    u1, u2, true, -1);
            }

            log("Pareggio/Timeout. Parola: " + parolaOriginaleCorrente);
        }

        giocatore1 = null;
        giocatore2 = null;
    }

    /**
     * @brief Gestisce la disconnessione inaspettata di un client.
     * Rimuove il client dalla lista d'attesa; se la disconnessione avviene
     * durante una sfida attiva, annulla la sfida e notifica l'altro giocatore.
     *
     * @param[in] client client disconnesso
     */
    public synchronized void onClientDisconnesso(GestoreClient client) {
        tuttiIClient.remove(client);
        giocatoriInAttesa.remove(client);

        if (sfidaAttiva && (client == giocatore1 || client == giocatore2)) {
            GestoreClient other = (client == giocatore1) ? giocatore2 : giocatore1;
            log("Disconnessione durante la sfida: " + client.getNomeUtente());
            if (other != null) {
                other.invia(new Messaggio(Messaggio.Tipo.ERROR,
                    "L'avversario si e' disconnesso. La partita e' annullata."));
            }
            if (taskTimeout != null) taskTimeout.cancel(false);
            sfidaAttiva = false;
            giocatore1 = null;
            giocatore2 = null;
        }
    }

    /**
     * @brief Gestisce la richiesta dello storico partite di un client.
     *
     * @param[in] client client richiedente
     */
    public synchronized void gestisciRichiestaStorico(GestoreClient client) {
        List<VoceStorico> history = db.getHistory(client.getNomeUtente());
        client.invia(new Messaggio(Messaggio.Tipo.HISTORY_RESPONSE,
            new ArrayList<>(history)));
    }

    /**
     * @brief Restituisce il database manager.
     *
     * @return istanza del database manager
     */
    public GestoreDatabase getDb() { return db; }

    /**
     * @brief Aggiunge un client alla lista dei client connessi.
     *
     * @param[in] client client da aggiungere
     */
    public synchronized void aggiungiClient(GestoreClient client) {
        tuttiIClient.add(client);
    }

    /**
     * @brief Disconnette tutti i client attualmente connessi.
     * Viene chiamato quando il server si ferma, per chiudere le socket.
     */
    public synchronized void disconnettiTutti() {
        log("Disconnessione di tutti i client in corso...");
        List<GestoreClient> copia = new ArrayList<>(tuttiIClient);
        for (GestoreClient client : copia) {
            client.invia(new Messaggio(Messaggio.Tipo.ERROR, "Il server e' stato spento."));
            client.close();
        }
        tuttiIClient.clear();
        giocatoriInAttesa.clear();
        if (sfidaAttiva) {
            if (taskTimeout != null) taskTimeout.cancel(false);
            sfidaAttiva = false;
            giocatore1 = null;
            giocatore2 = null;
        }
    }

    /**
     * @brief Scrive un messaggio nel log del sistema, chiamando anche la callback
     * della GUI se impostata.
     *
     * @param[in] msg messaggio di log
     */
    private void log(String msg) {
        LOGGER.info(msg);
        if (statusCallback != null) {
            statusCallback.accept(msg);
        }
    }
}
