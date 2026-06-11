package guesstheword.server.db;

import guesstheword.common.HistoryEntry;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestore del database SQLite dell'applicazione.
 * Implementa il pattern Singleton e il pattern DAO (Data Access Object).
 *
 * <p>Tutte le query usano esclusivamente {@link PreparedStatement} per prevenire
 * SQL injection. Le operazioni che coinvolgono più tabelle (es. salvataggio sfida
 * + risultati) sono gestite in transazione esplicita.</p>
 *
 * <p>Modulo 8 corso JA26: JDBC, PreparedStatement, transazioni, try-with-resources.</p>
 */
public class DatabaseManager {

    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());

    private static DatabaseManager instance;
    private Connection connection;
    private final String dbPath;

    /**
     * Costruttore privato — pattern Singleton.
     *
     * @param dbPath percorso relativo al file SQLite
     */
    private DatabaseManager(String dbPath) {
        this.dbPath = dbPath;
    }

    /**
     * Restituisce l'istanza Singleton del DatabaseManager.
     * Crea l'istanza al primo invocation (lazy initialization).
     *
     * @param dbPath percorso relativo al file database SQLite
     * @return istanza singleton
     */
    public static synchronized DatabaseManager getInstance(String dbPath) {
        if (instance == null) {
            instance = new DatabaseManager(dbPath);
        }
        return instance;
    }

    /**
     * Apre la connessione al database e inizializza schema e account predefiniti.
     * Deve essere chiamato una sola volta all'avvio del server.
     *
     * @throws SQLException in caso di errore di connessione o inizializzazione
     */
    public void connect() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        connection.setAutoCommit(true);
        initSchema();
        seedAccounts();
        LOGGER.info("Database connesso: " + dbPath);
    }

    /**
     * Chiude la connessione al database.
     */
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                LOGGER.info("Database disconnesso.");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Errore chiusura database", e);
        }
    }

    // ----------------------------------------------------------------
    // Schema e seeding
    // ----------------------------------------------------------------

    /**
     * Crea le tabelle del database se non esistono già.
     *
     * @throws SQLException in caso di errore DDL
     */
    private void initSchema() throws SQLException {
        // try-with-resources per Statement (Modulo 8)
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                "  user_id       INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  username      TEXT    UNIQUE NOT NULL," +
                "  password_hash TEXT    NOT NULL," +
                "  role          TEXT    NOT NULL DEFAULT 'player'," +
                "  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS challenges (" +
                "  challenge_id  INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  played_at     DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  text_excerpt  TEXT," +
                "  encrypted_word TEXT," +
                "  original_word TEXT," +
                "  caesar_shift  INTEGER" +
                ")"
            );
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS results (" +
                "  result_id        INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  challenge_id     INTEGER NOT NULL," +
                "  user_id          INTEGER NOT NULL," +
                "  outcome          TEXT    NOT NULL," +
                "  response_time_ms INTEGER," +
                "  FOREIGN KEY (challenge_id) REFERENCES challenges(challenge_id)," +
                "  FOREIGN KEY (user_id)      REFERENCES users(user_id)" +
                ")"
            );
        }
    }

    /**
     * Inserisce gli account predefiniti se non sono già presenti nel database.
     * Account creati: admin/admin123 (ruolo admin), player1/pass1, player2/pass2 (ruolo player).
     *
     * @throws SQLException in caso di errore di inserimento
     */
    private void seedAccounts() throws SQLException {
        insertUserIfAbsent("admin",   "admin123", "admin");
        insertUserIfAbsent("player1", "pass1",    "player");
        insertUserIfAbsent("player2", "pass2",    "player");
    }

    /**
     * Inserisce un utente solo se lo username non è già presente.
     *
     * @param username username da inserire
     * @param password password in chiaro (verrà hashata)
     * @param role     ruolo: "admin" o "player"
     * @throws SQLException in caso di errore SQL
     */
    private void insertUserIfAbsent(String username, String password, String role)
            throws SQLException {
        // try-with-resources per PreparedStatement (Modulo 8)
        try (PreparedStatement check = connection.prepareStatement(
                "SELECT COUNT(*) FROM users WHERE username = ?")) {
            check.setString(1, username);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (PreparedStatement ins = connection.prepareStatement(
                            "INSERT INTO users(username, password_hash, role) VALUES(?,?,?)")) {
                        ins.setString(1, username);
                        ins.setString(2, hashPassword(password));
                        ins.setString(3, role);
                        ins.executeUpdate();
                        LOGGER.info("Account predefinito creato: " + username);
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------
    // Autenticazione
    // ----------------------------------------------------------------

    /**
     * Autentica un utente verificando username e hash della password.
     *
     * @param username username da verificare
     * @param password password in chiaro
     * @return ruolo dell'utente ("admin" o "player"), oppure null se le credenziali non sono valide
     */
    public String authenticate(String username, String password) {
        String sql = "SELECT role FROM users WHERE username = ? AND password_hash = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hashPassword(password));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("role");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Errore autenticazione per: " + username, e);
        }
        return null;
    }

    /**
     * Registra un nuovo utente con ruolo "player".
     *
     * @param username username scelto dall'utente
     * @param password password in chiaro (verrà hashata)
     * @return true se la registrazione è avvenuta con successo,
     *         false se lo username è già occupato
     */
    public boolean registerUser(String username, String password) {
        String sql = "INSERT INTO users(username, password_hash, role) VALUES(?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hashPassword(password));
            ps.setString(3, "player");
            ps.executeUpdate();
            LOGGER.info("Nuovo utente registrato: " + username);
            return true;
        } catch (SQLException e) {
            // Violazione UNIQUE constraint → username già esistente
            return false;
        }
    }

    /**
     * Restituisce l'identificativo numerico di un utente dato il suo username.
     *
     * @param username username da cercare
     * @return user_id, oppure -1 se non trovato
     */
    public int getUserId(String username) {
        String sql = "SELECT user_id FROM users WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("user_id");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Errore getUserId per: " + username, e);
        }
        return -1;
    }

    // ----------------------------------------------------------------
    // Sfide e risultati — transazione esplicita (Modulo 8)
    // ----------------------------------------------------------------

    /**
     * Salva una sfida e i risultati di entrambi i giocatori in un'unica transazione atomica.
     * Se una delle operazioni fallisce, viene eseguito il rollback di tutta la transazione.
     *
     * <p>Dimostrazione di transazione esplicita JDBC (Modulo 8 corso JA26).</p>
     *
     * @param textExcerpt   estratto testuale
     * @param encryptedWord parola cifrata
     * @param originalWord  parola originale
     * @param shift         shift del cifrario di Cesare
     * @param winnerUser    username del vincitore (null se pareggio)
     * @param loserUser     username del perdente (null se pareggio)
     * @param draw          true se la sfida è terminata in pareggio
     * @param responseTimeMs tempo di risposta del vincitore in ms (-1 se pareggio)
     */
    public void saveChallengeWithResults(
            String textExcerpt, String encryptedWord, String originalWord, int shift,
            String winnerUser, String loserUser, boolean draw, long responseTimeMs) {

        try {
            // Inizio transazione esplicita
            connection.setAutoCommit(false);

            try {
                // 1. Salva la sfida
                int challengeId = insertChallenge(textExcerpt, encryptedWord, originalWord, shift);

                // 2. Salva i risultati
                if (draw) {
                    int id1 = getUserId(winnerUser != null ? winnerUser : "");
                    int id2 = getUserId(loserUser  != null ? loserUser  : "");
                    if (id1 > 0) insertResult(challengeId, id1, "draw", -1);
                    if (id2 > 0) insertResult(challengeId, id2, "draw", -1);
                } else {
                    int winnerId = getUserId(winnerUser);
                    int loserId  = getUserId(loserUser);
                    if (winnerId > 0) insertResult(challengeId, winnerId, "win",  responseTimeMs);
                    if (loserId  > 0) insertResult(challengeId, loserId,  "loss", -1);
                }

                // Commit — tutto ok
                connection.commit();
                LOGGER.info("Transazione sfida+risultati salvata. ChallengeId=" + challengeId);

            } catch (SQLException e) {
                // Rollback in caso di errore
                connection.rollback();
                LOGGER.log(Level.SEVERE, "Rollback transazione sfida", e);
            } finally {
                connection.setAutoCommit(true);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Errore gestione transazione", e);
        }
    }

    /**
     * Inserisce una riga nella tabella challenges e restituisce il challenge_id generato.
     *
     * @param textExcerpt   estratto testuale
     * @param encryptedWord parola cifrata
     * @param originalWord  parola originale
     * @param shift         shift del cifrario
     * @return challenge_id generato, oppure -1 in caso di errore
     * @throws SQLException in caso di errore SQL
     */
    private int insertChallenge(String textExcerpt, String encryptedWord,
                                String originalWord, int shift) throws SQLException {
        String sql = "INSERT INTO challenges(text_excerpt, encrypted_word, original_word, caesar_shift)" +
                     " VALUES(?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, textExcerpt);
            ps.setString(2, encryptedWord);
            ps.setString(3, originalWord);
            ps.setInt(4, shift);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    /**
     * Inserisce un risultato per un singolo giocatore nella tabella results.
     *
     * @param challengeId    id della sfida
     * @param userId         id dell'utente
     * @param outcome        esito: "win", "loss" o "draw"
     * @param responseTimeMs tempo di risposta in ms (-1 se timeout)
     * @throws SQLException in caso di errore SQL
     */
    private void insertResult(int challengeId, int userId,
                              String outcome, long responseTimeMs) throws SQLException {
        String sql = "INSERT INTO results(challenge_id, user_id, outcome, response_time_ms)" +
                     " VALUES(?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, challengeId);
            ps.setInt(2, userId);
            ps.setString(3, outcome);
            ps.setLong(4, responseTimeMs);
            ps.executeUpdate();
        }
    }

    // ----------------------------------------------------------------
    // Storico e statistiche
    // ----------------------------------------------------------------

    /**
     * Restituisce lo storico delle sfide di un utente, ordinate dalla più recente.
     *
     * @param username username del giocatore
     * @return lista di {@link HistoryEntry}, vuota se nessuna sfida trovata
     */
    public List<HistoryEntry> getHistory(String username) {
        List<HistoryEntry> list = new ArrayList<>();
        String sql =
            "SELECT c.played_at, c.original_word, r.outcome, r.response_time_ms," +
            "  (SELECT u2.username FROM results r2" +
            "   JOIN users u2 ON r2.user_id = u2.user_id" +
            "   WHERE r2.challenge_id = c.challenge_id AND u2.username != ?) AS opponent " +
            "FROM results r " +
            "JOIN challenges c ON r.challenge_id = c.challenge_id " +
            "JOIN users u ON r.user_id = u.user_id " +
            "WHERE u.username = ? " +
            "ORDER BY c.played_at DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String opponent = rs.getString("opponent");
                    list.add(new HistoryEntry(
                        rs.getString("played_at"),
                        opponent != null ? opponent : "?",
                        rs.getString("outcome"),
                        rs.getLong("response_time_ms"),
                        rs.getString("original_word")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Errore getHistory per: " + username, e);
        }
        return list;
    }

    /**
     * Classe interna che rappresenta le statistiche aggregate di un utente.
     * Usata per popolare la classifica nella GUI del server.
     */
    public static class UserStats {
        /** Username dell'utente. */
        public final String username;
        /** Numero di sfide vinte. */
        public final int wins;
        /** Numero totale di sfide giocate. */
        public final int gamesPlayed;
        /** Tempo medio di risposta in millisecondi. */
        public final long avgResponseMs;

        /**
         * Costruisce le statistiche di un utente.
         *
         * @param username      username
         * @param wins          vittorie
         * @param gamesPlayed   partite totali
         * @param avgResponseMs tempo medio risposta in ms
         */
        public UserStats(String username, int wins, int gamesPlayed, long avgResponseMs) {
            this.username = username;
            this.wins = wins;
            this.gamesPlayed = gamesPlayed;
            this.avgResponseMs = avgResponseMs;
        }
    }

    /**
     * Restituisce le statistiche aggregate di tutti i giocatori, ordinate per vittorie decrescenti.
     * Usato per popolare la classifica nella GUI amministratore.
     *
     * @return lista di {@link UserStats}, vuota se nessun risultato presente
     */
    public List<UserStats> getAllStats() {
        List<UserStats> stats = new ArrayList<>();
        String sql =
            "SELECT u.username," +
            "  SUM(CASE WHEN r.outcome = 'win' THEN 1 ELSE 0 END) AS wins," +
            "  COUNT(*) AS games_played," +
            "  CAST(AVG(CASE WHEN r.response_time_ms >= 0 THEN r.response_time_ms END) AS INTEGER) AS avg_ms " +
            "FROM results r " +
            "JOIN users u ON r.user_id = u.user_id " +
            "GROUP BY r.user_id " +
            "ORDER BY wins DESC";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                stats.add(new UserStats(
                    rs.getString("username"),
                    rs.getInt("wins"),
                    rs.getInt("games_played"),
                    rs.getLong("avg_ms")
                ));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Errore getAllStats", e);
        }
        return stats;
    }

    // ----------------------------------------------------------------
    // Utility
    // ----------------------------------------------------------------

    /**
     * Calcola l'hash SHA-256 di una password.
     * Le password non vengono mai salvate in chiaro nel database.
     *
     * @param password password in chiaro
     * @return stringa esadecimale dell'hash SHA-256
     * @throws RuntimeException se l'algoritmo SHA-256 non è disponibile nella JVM
     */
    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 non disponibile nella JVM", e);
        }
    }
}
