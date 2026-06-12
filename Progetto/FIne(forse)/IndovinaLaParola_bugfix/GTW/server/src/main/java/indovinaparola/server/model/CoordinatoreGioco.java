package indovinaparola.server.model;

import indovinaparola.common.*;
import indovinaparola.server.db.GestoreDatabase;
import indovinaparola.server.service.AnalizzatoreDocumenti;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Coordinatore centrale della logica di gioco lato server.
 *
 * <p>Gestisce: autenticazione, lista d'attesa giocatori, avvio e risoluzione
 * delle sfide, timeout automatico e persistenza dei risultati nel database.</p>
 *
 * <p>Tutti i metodi pubblici sono {@code synchronized} perché questo oggetto
 * è condiviso tra i thread di ogni {@link GestoreClient}. Il monitor implicito
 * di Java garantisce l'accesso esclusivo e la visibilità degli aggiornamenti
 * (Modulo 6 - Concurrency API corso JA26).</p>
 *
 * <p>Il timeout della sfida è gestito con {@link ScheduledExecutorService}
 * (Modulo 6), che evita l'uso di {@code Thread.sleep()} nei thread di gioco.</p>
 *
 * <p>La callback di log usa {@link Consumer}&lt;String&gt; come interfaccia funzionale
 * assegnata tramite lambda dal controller JavaFX (Modulo 4 - Lambda).</p>
 */
public class CoordinatoreGioco {

    private static final Logger LOGGER = Logger.getLogger(CoordinatoreGioco.class.getName());

    /** Durata massima di una sfida in secondi (configurabile). */
    private static final int TIMEOUT_SECONDS = 60;

    /** Numero di parole dell'estratto inviato ai client. */
    private static final int EXCERPT_WORDS = 60;

    private final GestoreDatabase db;
    private AnalizzatoreDocumenti analizzatore;

    /** Giocatori autenticati in attesa di un avversario. */
    private final List<GestoreClient> giocatoriInAttesa = new ArrayList<>();

    /**
     * Callback per aggiornare la TextArea di log nella GUI del server.
     * Assegnata tramite lambda dal controller (Modulo 4 - Consumer interfaccia funzionale).
     */
    private Consumer<String> statusCallback;

    /**
     * Executor per il timeout automatico della sfida.
     * Un thread singolo è sufficiente per gestire i timeout sequenziali.
     * (Modulo 6 - ScheduledExecutorService)
     */
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();

    // --- Stato della sfida in corso ---
    private GestoreClient giocatore1;
    private GestoreClient giocatore2;
    private String parolaOriginaleCorrente;
    private String parolaCifrataCorrente;
    private int spostamentoCorrente;
    private String estrattoCorrente;
    private long tempoInizioSfida;
    private boolean sfidaAttiva = false;
    private ScheduledFuture<?> taskTimeout;

    /**
     * Costruisce il coordinatore del gioco.
     *
     * @param db       gestore del database (già connesso)
     * @param analizzatore analizzatore documenti, può essere null inizialmente
     */
    public CoordinatoreGioco(GestoreDatabase db, AnalizzatoreDocumenti analizzatore) {
        this.db = db;
        this.analizzatore = analizzatore;
    }

    /**
     * Imposta la callback per i messaggi di log da mostrare nella GUI server.
     * Viene assegnata dal controller tramite espressione lambda (Modulo 4).
     *
     * <p>Esempio di uso nel controller:</p>
     * <pre>
     * coordinator.setStatusCallback(msg -&gt;
     *     Platform.runLater(() -&gt; logArea.appendText(msg + "\n")));
     * </pre>
     *
     * @param callback {@link Consumer}&lt;String&gt; che riceve ogni messaggio di log
     */
    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    /**
     * Aggiorna l'analizzatore documenti (dopo una nuova analisi da parte dell'admin).
     *
     * @param analizzatore nuovo analizzatore con i risultati aggiornati
     */
    public synchronized void setAnalyzer(AnalizzatoreDocumenti analizzatore) {
        this.analizzatore = analizzatore;
        log("Analizzatore aggiornato: " + analizzatore.getTermFrequency().size() + " parole.");
    }

    // ----------------------------------------------------------------
    // Gestione autenticazione
    // ----------------------------------------------------------------

    /**
     * Gestisce una richiesta di login da parte di un client.
     * Autentica le credenziali nel database e, se valide, aggiunge il
     * giocatore alla lista d'attesa.
     *
     * @param client  client che ha inviato la richiesta
     * @param carico credenziali di autenticazione
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
     * Gestisce una richiesta di registrazione da parte di un client.
     * Valida i dati e registra il nuovo utente nel database.
     *
     * @param client  client richiedente
     * @param carico credenziali del nuovo account
     */
    public synchronized void gestisciRegistrazione(GestoreClient client, PayloadAutenticazione carico) {
        if (carico.getNomeUtente() == null || carico.getNomeUtente().trim().isEmpty() ||
            carico.getPassword() == null || carico.getPassword().trim().isEmpty()) {
            client.invia(new Messaggio(Messaggio.Tipo.REGISTER_RESPONSE,
                new RispostaAutenticazione(false, "Username e password non possono essere vuoti.", null)));
            return;
        }
        boolean ok = db.registraUtente(carico.getNomeUtente().trim(), carico.getPassword());
        if (ok) {
            client.invia(new Messaggio(Messaggio.Tipo.REGISTER_RESPONSE,
                new RispostaAutenticazione(true, "Registrazione completata. Ora accedi.", null)));
            log("Registrato: " + carico.getNomeUtente());
        } else {
            client.invia(new Messaggio(Messaggio.Tipo.REGISTER_RESPONSE,
                new RispostaAutenticazione(false, "Username gia' esistente.", null)));
        }
    }

    // ----------------------------------------------------------------
    // Gestione partita
    // ----------------------------------------------------------------

    /**
     * Gestisce la richiesta di un client già autenticato di rientrare
     * in lista d'attesa per una nuova partita (pulsante "Nuova partita"
     * lato client, dopo {@code CHALLENGE_RESULT}).
     *
     * <p>A differenza di {@link #gestisciLogin}, questo metodo non richiede
     * nuove credenziali: il client è già autenticato sulla stessa
     * connessione TCP. Evita inoltre di reinserire due volte lo stesso
     * client se già presente in {@code giocatoriInAttesa}.</p>
     *
     * @param client client già autenticato che richiede una nuova partita
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
     * Aggiunge un giocatore alla lista d'attesa e avvia la sfida
     * quando sono presenti esattamente due giocatori.
     *
     * @param client client da aggiungere alla lista
     */
    private void aggiungiInListaAttesa(GestoreClient client) {
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
     * Avvia una nuova sfida tra due giocatori.
     * Estrae un estratto dal corpus analizzato, sceglie una parola,
     * la cifra con il Cifrario di Cesare con spostamento casuale, invia il
     * carico ai due client e schedula il timeout automatico.
     *
     * @param p1 primo giocatore
     * @param p2 secondo giocatore
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
        String excerpt = analizzatore.extractRandomExcerpt(EXCERPT_WORDS);
        String parola = analizzatore.selectWordFromExcerpt(excerpt, 2);
        if (parola == null) {
            log("Impossibile selezionare parola dall'estratto. Riprovo...");
            excerpt = analizzatore.extractRandomExcerpt(EXCERPT_WORDS);
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
        String textWithCipher = excerpt.replaceAll(
            "(?i)\\b" + parola + "\\b", "[" + encrypted + "]");

        // Salva lo stato della sfida in corso
        parolaOriginaleCorrente = parola;
        parolaCifrataCorrente = encrypted;
        spostamentoCorrente = spostamento;
        estrattoCorrente = textWithCipher;
        tempoInizioSfida = System.currentTimeMillis();
        sfidaAttiva = true;

        List<String> encryptedList = Collections.singletonList(encrypted);
        PayloadSfida carico = new PayloadSfida(0, textWithCipher, encryptedList, TIMEOUT_SECONDS);

        p1.invia(new Messaggio(Messaggio.Tipo.CHALLENGE_START, carico));
        p2.invia(new Messaggio(Messaggio.Tipo.CHALLENGE_START, carico));

        log("Sfida avviata: " + p1.getNomeUtente() + " vs " + p2.getNomeUtente()
            + " | parola: " + parola + " | spostamento: " + spostamento);

        // Timeout automatico con ScheduledExecutorService (Modulo 6)
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
        }, TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Gestisce la risposta di un giocatore alla sfida in corso.
     * Se la risposta è corretta, termina la sfida decretando il vincitore.
     * Se è errata, notifica solo il client che ha sbagliato.
     *
     * @param client client che ha inviato la risposta
     * @param guess  parola inserita dal giocatore
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
     * Termina la sfida in corso, invia i risultati ai client e persiste
     * i dati nel database in una transazione atomica.
     *
     * @param winner         client vincitore, null in caso di pareggio/timeout
     * @param tempoRispostaMs tempo di risposta del vincitore in ms
     * @param isDraw         true se la sfida è terminata in pareggio (timeout)
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

            // Salva in transazione (Modulo 8)
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
     * Gestisce la disconnessione inaspettata di un client.
     * Rimuove il client dalla lista d'attesa; se la disconnessione avviene
     * durante una sfida attiva, annulla la sfida e notifica l'altro giocatore.
     *
     * @param client client disconnesso
     */
    public synchronized void onClientDisconnesso(GestoreClient client) {
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
     * Gestisce la richiesta dello storico partite di un client.
     *
     * @param client client richiedente
     */
    public synchronized void gestisciRichiestaStorico(GestoreClient client) {
        List<VoceStorico> history = db.getHistory(client.getNomeUtente());
        client.invia(new Messaggio(Messaggio.Tipo.HISTORY_RESPONSE,
            new ArrayList<>(history)));
    }

    /**
     * Restituisce il database manager.
     *
     * @return istanza del database manager
     */
    public GestoreDatabase getDb() { return db; }

    /**
     * Scrive un messaggio nel log del sistema, chiamando anche la callback
     * della GUI se impostata.
     *
     * @param msg messaggio di log
     */
    private void log(String msg) {
        LOGGER.info(msg);
        if (statusCallback != null) {
            statusCallback.accept(msg);
        }
    }
}
