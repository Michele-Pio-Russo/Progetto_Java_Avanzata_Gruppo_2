package indovinaparola.server.model;

import indovinaparola.common.*;
import indovinaparola.server.db.GestoreDatabase;
import indovinaparola.server.service.AnalizzatoreDocumenti;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
 *
 * <h3>Sistema di difficoltà</h3>
 * <p>Il livello è impostato dall'amministratore tramite l'interfaccia grafica del server
 * e influenza tre parametri della sfida:</p>
 * <ul>
 *   <li><b>Selezione della parola</b> – criteri di rarità e lunghezza (vedi
 *       {@link AnalizzatoreDocumenti#selectWordFromExcerpt}).</li>
 *   <li><b>Numero di parole cifrate</b> – 1 (Facile), 2 (Medio), 3 (Difficile).</li>
 *   <li><b>Timeout</b> – 90 s (Facile), 60 s (Medio), 40 s (Difficile).</li>
 * </ul>
 */
public class CoordinatoreGioco {

    private static final Logger LOGGER = Logger.getLogger(CoordinatoreGioco.class.getName());

    /** Numero di parole dell'estratto inviato ai client. */
    private static final int EXCERPT_WORDS = 60;

    // Timeout per livello di difficoltà (secondi)
    private static final int TIMEOUT_FACILE    = 90;
    private static final int TIMEOUT_MEDIO     = 60;
    private static final int TIMEOUT_DIFFICILE = 40;

    // Numero di parole da cifrare per livello
    private static final int PAROLE_FACILE    = 1;
    private static final int PAROLE_MEDIO     = 2;
    private static final int PAROLE_DIFFICILE = 3;

    private final GestoreDatabase db;
    private AnalizzatoreDocumenti analizzatore;

    /** Livello di difficoltà corrente: 1=Facile, 2=Medio, 3=Difficile. */
    private int livelloDifficolta = 2;

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
    /** Parole originali (più parole se livello > 1). */
    private List<String> paroleOriginali = new ArrayList<>();
    /** Parole cifrate corrispondenti alle originali. */
    private List<String> paroleCifrate = new ArrayList<>();
    private String estrattoCorrente;
    private long tempoInizioSfida;
    private boolean sfidaAttiva = false;
    private ScheduledFuture<?> taskTimeout;

    /**
     * Costruisce il coordinatore del gioco.
     *
     * @param db         gestore del database (già connesso)
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

    /**
     * Imposta il livello di difficoltà per le sfide successive.
     * Chiamato dall'interfaccia amministratore quando l'admin cambia la selezione.
     *
     * @param livello 1=Facile, 2=Medio, 3=Difficile
     */
    public synchronized void setLivelloDifficolta(int livello) {
        if (livello < 1 || livello > 3) {
            log("ATTENZIONE: livello difficoltà non valido (" + livello + "), uso Medio.");
            this.livelloDifficolta = 2;
        } else {
            this.livelloDifficolta = livello;
        }
        String[] nomi = {"", "Facile", "Medio", "Difficile"};
        log("Difficoltà impostata: " + nomi[this.livelloDifficolta]);
    }

    /**
     * Restituisce il livello di difficoltà corrente.
     *
     * @return 1=Facile, 2=Medio, 3=Difficile
     */
    public synchronized int getLivelloDifficolta() {
        return livelloDifficolta;
    }

    // ----------------------------------------------------------------
    // Gestione autenticazione
    // ----------------------------------------------------------------

    /**
     * Gestisce una richiesta di login da parte di un client.
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
     * in lista d'attesa per una nuova partita.
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
     * Avvia una nuova sfida tra due giocatori applicando il livello di difficoltà corrente.
     *
     * <p>In base al livello vengono determinati:</p>
     * <ul>
     *   <li>il numero di parole da cifrare nell'estratto;</li>
     *   <li>il timeout del conto alla rovescia;</li>
     *   <li>i criteri di selezione delle parole (rarità + lunghezza,
     *       delegati a {@link AnalizzatoreDocumenti#selectWordFromExcerpt}).</li>
     * </ul>
     *
     * <p>Le parole selezionate sono cifrate con il Cifrario di Cesare, ognuna con
     * uno shift casuale indipendente, e sostituite nell'estratto tra parentesi quadre.</p>
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

        // Determina parametri in base al livello di difficoltà
        final int numParole;
        final int timeoutSec;
        switch (livelloDifficolta) {
            case 1:
                numParole  = PAROLE_FACILE;
                timeoutSec = TIMEOUT_FACILE;
                break;
            case 3:
                numParole  = PAROLE_DIFFICILE;
                timeoutSec = TIMEOUT_DIFFICILE;
                break;
            default: // 2 = Medio
                numParole  = PAROLE_MEDIO;
                timeoutSec = TIMEOUT_MEDIO;
                break;
        }

        // Estrai un estratto casuale
        String excerpt = analizzatore.extractRandomExcerpt(EXCERPT_WORDS);

        // Raccogli le parole da cifrare (Stream + Set per unicità, Modulo 5)
        List<String> paroleSelezionate = new ArrayList<>();
        Set<String> usate = new HashSet<>();
        int tentativi = 0;
        while (paroleSelezionate.size() < numParole && tentativi < 20) {
            String parola = analizzatore.selectWordFromExcerpt(excerpt, livelloDifficolta);
            if (parola != null && !usate.contains(parola)) {
                paroleSelezionate.add(parola);
                usate.add(parola);
            }
            tentativi++;
        }

        if (paroleSelezionate.isEmpty()) {
            log("ERRORE: nessuna parola idonea trovata per la sfida.");
            p1.invia(new Messaggio(Messaggio.Tipo.ERROR, "Errore interno: nessuna parola trovata."));
            p2.invia(new Messaggio(Messaggio.Tipo.ERROR, "Errore interno: nessuna parola trovata."));
            return;
        }

        // Cifra ogni parola con shift casuale indipendente (Stream API, Modulo 5)
        Random rnd = new Random();
        List<String> cifrate = paroleSelezionate.stream()
            .map(p -> CifrarioCesare.cifra(p, rnd.nextInt(25) + 1))
            .collect(Collectors.toList());

        // Sostituisce ogni parola originale nell'estratto con la versione cifrata
        String testoSfida = excerpt;
        for (int i = 0; i < paroleSelezionate.size(); i++) {
            testoSfida = testoSfida.replaceAll(
                "(?i)\\b" + paroleSelezionate.get(i) + "\\b",
                "[" + cifrate.get(i) + "]");
        }

        // Salva stato sfida
        paroleOriginali  = new ArrayList<>(paroleSelezionate);
        paroleCifrate    = new ArrayList<>(cifrate);
        estrattoCorrente = testoSfida;
        tempoInizioSfida = System.currentTimeMillis();
        sfidaAttiva      = true;

        PayloadSfida carico = new PayloadSfida(
            0, testoSfida, new ArrayList<>(cifrate), timeoutSec, livelloDifficolta);

        p1.invia(new Messaggio(Messaggio.Tipo.CHALLENGE_START, carico));
        p2.invia(new Messaggio(Messaggio.Tipo.CHALLENGE_START, carico));

        String[] nomiLiv = {"", "Facile", "Medio", "Difficile"};
        log("Sfida avviata [" + nomiLiv[livelloDifficolta] + "]: "
            + p1.getNomeUtente() + " vs " + p2.getNomeUtente()
            + " | parole: " + paroleSelezionate
            + " | timeout: " + timeoutSec + "s");

        // Timeout automatico (Modulo 6 - ScheduledExecutorService)
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
        }, timeoutSec, TimeUnit.SECONDS);
    }

    /**
     * Gestisce la risposta di un giocatore alla sfida in corso.
     * La risposta è corretta se corrisponde a <em>una qualsiasi</em> delle parole
     * originali cifrate (in modo case-insensitive).
     *
     * @param client client che ha inviato la risposta
     * @param guess  parola inserita dal giocatore
     */
    public synchronized void gestisciRisposta(GestoreClient client, String guess) {
        if (!sfidaAttiva) return;
        if (client != giocatore1 && client != giocatore2) return;
        if (guess == null) return;

        long responseTime = System.currentTimeMillis() - tempoInizioSfida;

        // Stream API (Modulo 5): verifica se guess corrisponde ad una parola originale
        boolean corretta = paroleOriginali.stream()
            .anyMatch(p -> p.equalsIgnoreCase(guess.trim()));

        if (corretta) {
            terminaSfida(client, responseTime, false);
        } else {
            client.invia(new Messaggio(Messaggio.Tipo.ERROR, "Risposta errata. Riprova!"));
            log(client.getNomeUtente() + " ha risposto erroneamente: " + guess);
        }
    }

    /**
     * Termina la sfida in corso, invia i risultati ai client e persiste
     * i dati nel database in una transazione atomica.
     *
     * @param winner          client vincitore, null in caso di pareggio/timeout
     * @param tempoRispostaMs tempo di risposta del vincitore in ms
     * @param isDraw          true se la sfida è terminata in pareggio (timeout)
     */
    private void terminaSfida(GestoreClient winner, long tempoRispostaMs, boolean isDraw) {
        if (!sfidaAttiva) return;
        sfidaAttiva = false;
        if (taskTimeout != null) taskTimeout.cancel(false);

        // Prima parola originale usata come riferimento per il DB e il client
        String parolaRif   = paroleOriginali.isEmpty() ? "" : paroleOriginali.get(0);
        String cifrataRif  = paroleCifrate.isEmpty()   ? "" : paroleCifrate.get(0);
        // Shift ricostruibile dal primo elemento (solo per compatibilità DB)
        int spostamentoRif = 0;

        GestoreClient loser = (winner == giocatore1) ? giocatore2 : giocatore1;

        if (!isDraw && winner != null) {
            winner.invia(new Messaggio(Messaggio.Tipo.CHALLENGE_RESULT,
                new RisultatoSfida(winner.getNomeUtente(), parolaRif, RisultatoSfida.Esito.VITTORIA)));
            loser.invia(new Messaggio(Messaggio.Tipo.CHALLENGE_RESULT,
                new RisultatoSfida(winner.getNomeUtente(), parolaRif, RisultatoSfida.Esito.SCONFITTA)));

            log("DB: salvataggio vittoria in corso...");
            if (db == null) {
                log("DB: ERRORE - riferimento db e' null!");
            } else {
                db.saveChallengeWithResults(
                    estrattoCorrente, cifrataRif, parolaRif, spostamentoRif,
                    winner.getNomeUtente(), loser.getNomeUtente(), false, tempoRispostaMs);
                log("DB: salvataggio completato.");
            }
            log("Vince: " + winner.getNomeUtente() + " in " + tempoRispostaMs + "ms.");
        } else {
            RisultatoSfida draw = new RisultatoSfida(null, parolaRif, RisultatoSfida.Esito.PAREGGIO);
            if (giocatore1 != null) giocatore1.invia(new Messaggio(Messaggio.Tipo.CHALLENGE_RESULT, draw));
            if (giocatore2 != null) giocatore2.invia(new Messaggio(Messaggio.Tipo.CHALLENGE_RESULT, draw));

            String u1 = giocatore1 != null ? giocatore1.getNomeUtente() : "";
            String u2 = giocatore2 != null ? giocatore2.getNomeUtente() : "";
            log("DB: salvataggio pareggio in corso...");
            if (db == null) {
                log("DB: ERRORE - riferimento db e' null!");
            } else {
                db.saveChallengeWithResults(
                    estrattoCorrente, cifrataRif, parolaRif, spostamentoRif,
                    u1, u2, true, -1);
                log("DB: salvataggio completato.");
            }
            log("Pareggio/Timeout. Parola: " + parolaRif);
        }

        giocatore1 = null;
        giocatore2 = null;
    }

    /**
     * Gestisce la disconnessione inaspettata di un client.
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
            giocatore1  = null;
            giocatore2  = null;
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
     * Scrive un messaggio nel log del sistema.
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
