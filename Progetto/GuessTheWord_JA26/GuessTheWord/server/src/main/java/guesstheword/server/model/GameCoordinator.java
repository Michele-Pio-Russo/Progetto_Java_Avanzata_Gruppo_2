package guesstheword.server.model;

import guesstheword.common.*;
import guesstheword.server.db.DatabaseManager;
import guesstheword.server.service.DocumentAnalyzer;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Coordinatore centrale del gioco lato server.
 * Gestisce autenticazione, accoppiamento client, avvio e risoluzione delle sfide.
 */
public class GameCoordinator {

    private static final Logger LOGGER = Logger.getLogger(GameCoordinator.class.getName());
    private static final int TIMEOUT_SECONDS = 60;
    private static final int EXCERPT_WORDS = 60;

    private final DatabaseManager db;
    private DocumentAnalyzer analyzer;

    /** Client autenticati in attesa di sfida (max 2). */
    private final List<ClientHandler> waitingPlayers = new ArrayList<>();

    /** Callback per aggiornare la UI del server. */
    private Consumer<String> statusCallback;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Stato partita in corso
    private ClientHandler player1;
    private ClientHandler player2;
    private String currentOriginalWord;
    private int currentChallengeId;
    private long challengeStartTime;
    private boolean challengeActive = false;
    private ScheduledFuture<?> timeoutTask;

    /**
     * Costruisce il coordinatore con il database e l'analizzatore.
     *
     * @param db       gestore database
     * @param analyzer analizzatore documenti (può essere null inizialmente)
     */
    public GameCoordinator(DatabaseManager db, DocumentAnalyzer analyzer) {
        this.db = db;
        this.analyzer = analyzer;
    }

    /**
     * Imposta la callback per i log di stato sulla UI del server.
     *
     * @param callback consumer di stringhe di log
     */
    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    /**
     * Aggiorna l'analizzatore (dopo una nuova analisi da parte dell'admin).
     *
     * @param analyzer nuovo analizzatore
     */
    public synchronized void setAnalyzer(DocumentAnalyzer analyzer) {
        this.analyzer = analyzer;
        log("Analizzatore aggiornato: " + analyzer.getTermFrequency().size() + " parole.");
    }

    // ----------------------------------------------------------------
    // Autenticazione
    // ----------------------------------------------------------------

    /**
     * Gestisce una richiesta di login da un client.
     *
     * @param client  client che ha inviato la richiesta
     * @param payload credenziali
     */
    public synchronized void handleLogin(ClientHandler client, AuthPayload payload) {
        String role = db.authenticate(payload.getUsername(), payload.getPassword());
        if (role != null) {
            client.setUsername(payload.getUsername());
            client.setRole(role);
            client.setAuthenticated(true);
            client.send(new Message(Message.Type.LOGIN_RESPONSE,
                new AuthResponse(true, "Login effettuato con successo.", role)));
            log("Login: " + payload.getUsername() + " [" + role + "]");

            if ("player".equals(role)) {
                addPlayerToWaitingList(client);
            }
        } else {
            client.send(new Message(Message.Type.LOGIN_RESPONSE,
                new AuthResponse(false, "Credenziali non valide.", null)));
            log("Login fallito per: " + payload.getUsername());
        }
    }

    /**
     * Gestisce una richiesta di registrazione da un client.
     *
     * @param client  client richiedente
     * @param payload credenziali nuove
     */
    public synchronized void handleRegister(ClientHandler client, AuthPayload payload) {
        if (payload.getUsername() == null || payload.getUsername().trim().isEmpty() ||
            payload.getPassword() == null || payload.getPassword().trim().isEmpty()) {
            client.send(new Message(Message.Type.REGISTER_RESPONSE,
                new AuthResponse(false, "Username e password non possono essere vuoti.", null)));
            return;
        }
        boolean ok = db.registerUser(payload.getUsername(), payload.getPassword());
        if (ok) {
            client.send(new Message(Message.Type.REGISTER_RESPONSE,
                new AuthResponse(true, "Registrazione completata. Ora puoi effettuare il login.", null)));
            log("Registrato nuovo utente: " + payload.getUsername());
        } else {
            client.send(new Message(Message.Type.REGISTER_RESPONSE,
                new AuthResponse(false, "Username già esistente.", null)));
        }
    }

    // ----------------------------------------------------------------
    // Gestione partita
    // ----------------------------------------------------------------

    private void addPlayerToWaitingList(ClientHandler client) {
        // Notifica che è in attesa
        client.send(new Message(Message.Type.WAITING, "In attesa dell'avversario..."));
        waitingPlayers.add(client);
        log("Giocatore in attesa: " + client.getUsername() +
            " (" + waitingPlayers.size() + "/2)");

        if (waitingPlayers.size() >= 2) {
            player1 = waitingPlayers.remove(0);
            player2 = waitingPlayers.remove(0);
            startChallenge(player1, player2);
        }
    }

    private void startChallenge(ClientHandler p1, ClientHandler p2) {
        if (analyzer == null || !analyzer.hasResults()) {
            log("ATTENZIONE: Nessun documento analizzato. Impossibile avviare la sfida.");
            String errMsg = "Il server non ha documenti disponibili per la sfida.";
            p1.send(new Message(Message.Type.ERROR, errMsg));
            p2.send(new Message(Message.Type.ERROR, errMsg));
            return;
        }

        String excerpt = analyzer.extractRandomExcerpt(EXCERPT_WORDS);
        String word = analyzer.selectWordFromExcerpt(excerpt, 1);

        if (word == null) {
            log("Impossibile selezionare parola dall'estratto.");
            return;
        }

        int shift = new Random().nextInt(25) + 1; // 1–25
        String encrypted = CaesarCipher.encrypt(word, shift);

        // Sostituisce la parola nell'estratto con quella cifrata (case-insensitive)
        String textWithCipher = excerpt.replaceAll(
            "(?i)\\b" + word + "\\b", "[" + encrypted + "]");

        currentOriginalWord = word;
        currentChallengeId = db.saveChallenge(textWithCipher, encrypted, word, shift);
        challengeStartTime = System.currentTimeMillis();
        challengeActive = true;

        List<String> encryptedList = Collections.singletonList(encrypted);
        ChallengePayload payload = new ChallengePayload(
            currentChallengeId, textWithCipher, encryptedList, TIMEOUT_SECONDS);

        p1.send(new Message(Message.Type.CHALLENGE_START, payload));
        p2.send(new Message(Message.Type.CHALLENGE_START, payload));

        log("Sfida avviata tra " + p1.getUsername() + " e " + p2.getUsername() +
            " | parola: " + word + " | shift: " + shift);

        // Timeout automatico
        timeoutTask = scheduler.schedule(() -> {
            synchronized (GameCoordinator.this) {
                if (challengeActive) {
                    endChallenge(null, -1);
                }
            }
        }, TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Gestisce la risposta di un giocatore alla sfida.
     *
     * @param client client che ha risposto
     * @param guess  risposta inviata
     */
    public synchronized void handleAnswer(ClientHandler client, String guess) {
        if (!challengeActive) return;
        if (client != player1 && client != player2) return;

        long responseTime = System.currentTimeMillis() - challengeStartTime;

        if (guess != null && guess.trim().equalsIgnoreCase(currentOriginalWord)) {
            // Risposta corretta
            endChallenge(client, responseTime);
        } else {
            // Risposta errata — notifica solo il client che ha sbagliato
            client.send(new Message(Message.Type.ERROR, "Risposta errata, riprova!"));
            log(client.getUsername() + " ha risposto erroneamente: " + guess);
        }
    }

    private void endChallenge(ClientHandler winner, long responseTimeMs) {
        if (!challengeActive) return;
        challengeActive = false;
        if (timeoutTask != null) timeoutTask.cancel(false);

        ClientHandler loser = (winner == player1) ? player2 : player1;

        if (winner != null) {
            // Vincitore trovato
            ChallengeResult winResult = new ChallengeResult(
                winner.getUsername(), currentOriginalWord, ChallengeResult.Outcome.WIN);
            ChallengeResult lossResult = new ChallengeResult(
                winner.getUsername(), currentOriginalWord, ChallengeResult.Outcome.LOSS);

            winner.send(new Message(Message.Type.CHALLENGE_RESULT, winResult));
            loser.send(new Message(Message.Type.CHALLENGE_RESULT, lossResult));

            // Persiste risultati
            int wId = db.getUserId(winner.getUsername());
            int lId = db.getUserId(loser.getUsername());
            db.saveResult(currentChallengeId, wId, "win", responseTimeMs);
            db.saveResult(currentChallengeId, lId, "loss", -1);

            log("Sfida conclusa: vince " + winner.getUsername() +
                " in " + responseTimeMs + "ms. Parola: " + currentOriginalWord);
        } else {
            // Timeout / pareggio
            ChallengeResult draw = new ChallengeResult(
                null, currentOriginalWord, ChallengeResult.Outcome.DRAW);
            if (player1 != null) player1.send(new Message(Message.Type.CHALLENGE_RESULT, draw));
            if (player2 != null) player2.send(new Message(Message.Type.CHALLENGE_RESULT, draw));

            int id1 = db.getUserId(player1 != null ? player1.getUsername() : "");
            int id2 = db.getUserId(player2 != null ? player2.getUsername() : "");
            if (id1 > 0) db.saveResult(currentChallengeId, id1, "draw", -1);
            if (id2 > 0) db.saveResult(currentChallengeId, id2, "draw", -1);

            log("Sfida terminata in pareggio (timeout). Parola: " + currentOriginalWord);
        }

        player1 = null;
        player2 = null;
    }

    /**
     * Gestisce la richiesta dello storico partite da un client.
     *
     * @param client client richiedente
     */
    public synchronized void handleHistoryRequest(ClientHandler client) {
        List<guesstheword.common.HistoryEntry> history =
            db.getHistory(client.getUsername());
        client.send(new Message(Message.Type.HISTORY_RESPONSE, new ArrayList<>(history)));
    }

    /**
     * Callback invocata quando un client si disconnette inaspettatamente.
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
                    "L'avversario si è disconnesso. La partita è annullata."));
            }
            if (timeoutTask != null) timeoutTask.cancel(false);
            challengeActive = false;
            player1 = null;
            player2 = null;
        }
    }

    private void log(String msg) {
        LOGGER.info(msg);
        if (statusCallback != null) {
            statusCallback.accept(msg);
        }
    }

    /**
     * Restituisce il database manager.
     *
     * @return database manager
     */
    public DatabaseManager getDb() { return db; }
}
