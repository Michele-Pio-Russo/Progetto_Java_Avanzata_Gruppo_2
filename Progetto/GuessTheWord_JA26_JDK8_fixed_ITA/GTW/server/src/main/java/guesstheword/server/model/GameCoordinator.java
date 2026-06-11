package guesstheword.server.model;

import guesstheword.common.*;
import guesstheword.server.db.DatabaseManager;
import guesstheword.server.service.DocumentAnalyzer;

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
 * è condiviso tra i thread di ogni {@link ClientHandler}. Il monitor implicito
 * di Java garantisce l'accesso esclusivo e la visibilità degli aggiornamenti
 * (Modulo 6 - Concurrency API corso JA26).</p>
 *
 * <p>Il timeout della sfida è gestito con {@link ScheduledExecutorService}
 * (Modulo 6), che evita l'uso di {@code Thread.sleep()} nei thread di gioco.</p>
 *
 * <p>La callback di log usa {@link Consumer}&lt;String&gt; come interfaccia funzionale
 * assegnata tramite lambda dal controller JavaFX (Modulo 4 - Lambda).</p>
 */
public class GameCoordinator {

    private static final Logger LOGGER = Logger.getLogger(GameCoordinator.class.getName());

    /** Durata massima di una sfida in secondi (configurabile). */
    private static final int TIMEOUT_SECONDS = 60;

    /** Numero di parole dell'estratto inviato ai client. */
    private static final int EXCERPT_WORDS = 60;

    private final DatabaseManager db;
    private DocumentAnalyzer analyzer;

    /** Giocatori autenticati in attesa di un avversario. */
    private final List<ClientHandler> waitingPlayers = new ArrayList<>();

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
    private ClientHandler player1;
    private ClientHandler player2;
    private String currentOriginalWord;
    private String currentEncryptedWord;
    private int currentShift;
    private String currentExcerpt;
    private long challengeStartTime;
    private boolean challengeActive = false;
    private ScheduledFuture<?> timeoutTask;

    /**
     * Costruisce il coordinatore del gioco.
     *
     * @param db       gestore del database (già connesso)
     * @param analyzer analizzatore documenti, può essere null inizialmente
     */
    public GameCoordinator(DatabaseManager db, DocumentAnalyzer analyzer) {
        this.db = db;
        this.analyzer = analyzer;
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
     * @param analyzer nuovo analizzatore con i risultati aggiornati
     */
    public synchronized void setAnalyzer(DocumentAnalyzer analyzer) {
        this.analyzer = analyzer;
        log("Analizzatore aggiornato: " + analyzer.getTermFrequency().size() + " parole.");
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
     * @param payload credenziali di autenticazione
     */
    public synchronized void handleLogin(ClientHandler client, AuthPayload payload) {
        String role = db.authenticate(payload.getUsername(), payload.getPassword());
        if (role != null) {
            client.setUsername(payload.getUsername());
            client.setRole(role);
            client.setAuthenticated(true);
            client.send(new Message(Message.Type.LOGIN_RESPONSE,
                new AuthResponse(true, "Login effettuato.", role)));
            log("Login: " + payload.getUsername() + " [" + role + "]");

            if ("player".equals(role)) {
                addToWaitingList(client);
            }
        } else {
            client.send(new Message(Message.Type.LOGIN_RESPONSE,
                new AuthResponse(false, "Credenziali non valide.", null)));
            log("Login fallito per: " + payload.getUsername());
        }
    }

    /**
     * Gestisce una richiesta di registrazione da parte di un client.
     * Valida i dati e registra il nuovo utente nel database.
     *
     * @param client  client richiedente
     * @param payload credenziali del nuovo account
     */
    public synchronized void handleRegister(ClientHandler client, AuthPayload payload) {
        if (payload.getUsername() == null || payload.getUsername().trim().isEmpty() ||
            payload.getPassword() == null || payload.getPassword().trim().isEmpty()) {
            client.send(new Message(Message.Type.REGISTER_RESPONSE,
                new AuthResponse(false, "Username e password non possono essere vuoti.", null)));
            return;
        }
        boolean ok = db.registerUser(payload.getUsername().trim(), payload.getPassword());
        if (ok) {
            client.send(new Message(Message.Type.REGISTER_RESPONSE,
                new AuthResponse(true, "Registrazione completata. Ora accedi.", null)));
            log("Registrato: " + payload.getUsername());
        } else {
            client.send(new Message(Message.Type.REGISTER_RESPONSE,
                new AuthResponse(false, "Username gia' esistente.", null)));
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
     * <p>A differenza di {@link #handleLogin}, questo metodo non richiede
     * nuove credenziali: il client è già autenticato sulla stessa
     * connessione TCP. Evita inoltre di reinserire due volte lo stesso
     * client se già presente in {@code waitingPlayers}.</p>
     *
     * @param client client già autenticato che richiede una nuova partita
     */
    public synchronized void handleRequeue(ClientHandler client) {
        if (!client.isAuthenticated() || !"player".equals(client.getRole())) {
            client.send(new Message(Message.Type.ERROR,
                "Devi effettuare il login prima di richiedere una nuova partita."));
            return;
        }
        if (waitingPlayers.contains(client)) {
            return;
        }
        log("Richiesta nuova partita: " + client.getUsername());
        addToWaitingList(client);
    }

    /**
     * Aggiunge un giocatore alla lista d'attesa e avvia la sfida
     * quando sono presenti esattamente due giocatori.
     *
     * @param client client da aggiungere alla lista
     */
    private void addToWaitingList(ClientHandler client) {
        client.send(new Message(Message.Type.WAITING,
            "In attesa dell'avversario... (" + (waitingPlayers.size() + 1) + "/2)"));
        waitingPlayers.add(client);
        log("In attesa: " + client.getUsername()
            + " (" + waitingPlayers.size() + "/2)");

        if (waitingPlayers.size() >= 2) {
            player1 = waitingPlayers.remove(0);
            player2 = waitingPlayers.remove(0);
            startChallenge(player1, player2);
        }
    }

    /**
     * Avvia una nuova sfida tra due giocatori.
     * Estrae un estratto dal corpus analizzato, sceglie una parola,
     * la cifra con il Cifrario di Cesare con shift casuale, invia il
     * payload ai due client e schedula il timeout automatico.
     *
     * @param p1 primo giocatore
     * @param p2 secondo giocatore
     */
    private void startChallenge(ClientHandler p1, ClientHandler p2) {
        if (analyzer == null || !analyzer.hasResults()) {
            String err = "Il server non ha documenti analizzati. Impossibile avviare la sfida.";
            log("ERRORE: " + err);
            p1.send(new Message(Message.Type.ERROR, err));
            p2.send(new Message(Message.Type.ERROR, err));
            return;
        }

        // Estrae estratto e parola
        String excerpt = analyzer.extractRandomExcerpt(EXCERPT_WORDS);
        String word = analyzer.selectWordFromExcerpt(excerpt, 2);
        if (word == null) {
            log("Impossibile selezionare parola dall'estratto. Riprovo...");
            excerpt = analyzer.extractRandomExcerpt(EXCERPT_WORDS);
            word = analyzer.selectWordFromExcerpt(excerpt, 1);
        }
        if (word == null) {
            log("ERRORE: nessuna parola idonea trovata.");
            return;
        }

        // Cifra la parola con shift casuale 1–25
        int shift = new Random().nextInt(25) + 1;
        String encrypted = CaesarCipher.encrypt(word, shift);

        // Sostituisce la parola nell'estratto con quella cifrata tra parentesi quadre
        String textWithCipher = excerpt.replaceAll(
            "(?i)\\b" + word + "\\b", "[" + encrypted + "]");

        // Salva lo stato della sfida in corso
        currentOriginalWord = word;
        currentEncryptedWord = encrypted;
        currentShift = shift;
        currentExcerpt = textWithCipher;
        challengeStartTime = System.currentTimeMillis();
        challengeActive = true;

        List<String> encryptedList = Collections.singletonList(encrypted);
        ChallengePayload payload = new ChallengePayload(0, textWithCipher, encryptedList, TIMEOUT_SECONDS);

        p1.send(new Message(Message.Type.CHALLENGE_START, payload));
        p2.send(new Message(Message.Type.CHALLENGE_START, payload));

        log("Sfida avviata: " + p1.getUsername() + " vs " + p2.getUsername()
            + " | parola: " + word + " | shift: " + shift);

        // Timeout automatico con ScheduledExecutorService (Modulo 6)
        // La lambda cattura 'this' per chiamare endChallenge in modo synchronized
        timeoutTask = scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                synchronized (GameCoordinator.this) {
                    if (challengeActive) {
                        log("Timeout scaduto per la sfida.");
                        endChallenge(null, -1, true);
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
    public synchronized void handleAnswer(ClientHandler client, String guess) {
        if (!challengeActive) return;
        if (client != player1 && client != player2) return;
        if (guess == null) return;

        long responseTime = System.currentTimeMillis() - challengeStartTime;

        if (guess.trim().equalsIgnoreCase(currentOriginalWord)) {
            // Risposta corretta → il client che ha risposto vince
            endChallenge(client, responseTime, false);
        } else {
            // Risposta errata → notifica solo questo client, può riprovare
            client.send(new Message(Message.Type.ERROR,
                "Risposta errata. Riprova!"));
            log(client.getUsername() + " ha risposto erroneamente: " + guess);
        }
    }

    /**
     * Termina la sfida in corso, invia i risultati ai client e persiste
     * i dati nel database in una transazione atomica.
     *
     * @param winner         client vincitore, null in caso di pareggio/timeout
     * @param responseTimeMs tempo di risposta del vincitore in ms
     * @param isDraw         true se la sfida è terminata in pareggio (timeout)
     */
    private void endChallenge(ClientHandler winner, long responseTimeMs, boolean isDraw) {
        if (!challengeActive) return;
        challengeActive = false;
        if (timeoutTask != null) timeoutTask.cancel(false);

        ClientHandler loser = (winner == player1) ? player2 : player1;

        if (!isDraw && winner != null) {
            // Vittoria
            winner.send(new Message(Message.Type.CHALLENGE_RESULT,
                new ChallengeResult(winner.getUsername(), currentOriginalWord, ChallengeResult.Outcome.WIN)));
            loser.send(new Message(Message.Type.CHALLENGE_RESULT,
                new ChallengeResult(winner.getUsername(), currentOriginalWord, ChallengeResult.Outcome.LOSS)));

            // Salva in transazione (Modulo 8)
            db.saveChallengeWithResults(
                currentExcerpt, currentEncryptedWord, currentOriginalWord, currentShift,
                winner.getUsername(), loser.getUsername(), false, responseTimeMs);

            log("Vince: " + winner.getUsername() + " in " + responseTimeMs + "ms. Parola: " + currentOriginalWord);
        } else {
            // Pareggio / timeout
            ChallengeResult draw = new ChallengeResult(null, currentOriginalWord, ChallengeResult.Outcome.DRAW);
            if (player1 != null) player1.send(new Message(Message.Type.CHALLENGE_RESULT, draw));
            if (player2 != null) player2.send(new Message(Message.Type.CHALLENGE_RESULT, draw));

            String u1 = player1 != null ? player1.getUsername() : "";
            String u2 = player2 != null ? player2.getUsername() : "";
            db.saveChallengeWithResults(
                currentExcerpt, currentEncryptedWord, currentOriginalWord, currentShift,
                u1, u2, true, -1);

            log("Pareggio/Timeout. Parola: " + currentOriginalWord);
        }

        player1 = null;
        player2 = null;
    }

    /**
     * Gestisce la disconnessione inaspettata di un client.
     * Rimuove il client dalla lista d'attesa; se la disconnessione avviene
     * durante una sfida attiva, annulla la sfida e notifica l'altro giocatore.
     *
     * @param client client disconnesso
     */
    public synchronized void onClientDisconnected(ClientHandler client) {
        waitingPlayers.remove(client);

        if (challengeActive && (client == player1 || client == player2)) {
            ClientHandler other = (client == player1) ? player2 : player1;
            log("Disconnessione durante la sfida: " + client.getUsername());
            if (other != null) {
                other.send(new Message(Message.Type.ERROR,
                    "L'avversario si e' disconnesso. La partita e' annullata."));
            }
            if (timeoutTask != null) timeoutTask.cancel(false);
            challengeActive = false;
            player1 = null;
            player2 = null;
        }
    }

    /**
     * Gestisce la richiesta dello storico partite di un client.
     *
     * @param client client richiedente
     */
    public synchronized void handleHistoryRequest(ClientHandler client) {
        List<HistoryEntry> history = db.getHistory(client.getUsername());
        client.send(new Message(Message.Type.HISTORY_RESPONSE,
            new ArrayList<>(history)));
    }

    /**
     * Restituisce il database manager.
     *
     * @return istanza del database manager
     */
    public DatabaseManager getDb() { return db; }

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
