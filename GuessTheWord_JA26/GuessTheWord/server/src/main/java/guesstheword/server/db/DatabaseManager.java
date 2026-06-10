package guesstheword.server.db;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import guesstheword.common.HistoryEntry;

/**
 * Gestisce la connessione al database SQLite e tutte le operazioni CRUD.
 * Inizializza automaticamente lo schema e gli account predefiniti al primo avvio.
 */
public class DatabaseManager {

    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());

    private static final String DB_URL_PREFIX = "jdbc:sqlite:";
    private static DatabaseManager instance;
    private Connection connection;
    private String dbPath;

    /**
     * Costruttore privato (singleton).
     *
     * @param dbPath percorso relativo al file DB
     */
    private DatabaseManager(String dbPath) {
        this.dbPath = dbPath;
    }

    /**
     * Restituisce l'istanza singleton del DatabaseManager.
     *
     * @param dbPath percorso relativo al file database
     * @return istanza singleton
     */
    public static synchronized DatabaseManager getInstance(String dbPath) {
        if (instance == null) {
            instance = new DatabaseManager(dbPath);
        }
        return instance;
    }

    /**
     * Apre la connessione al database e inizializza lo schema.
     *
     * @throws SQLException in caso di errore di connessione
     */
    public void connect() throws SQLException {
        connection = DriverManager.getConnection(DB_URL_PREFIX + dbPath);
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
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Errore chiusura DB", e);
        }
    }

    /**
     * Crea le tabelle se non esistono.
     */
    private void initSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                "  user_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  username TEXT UNIQUE NOT NULL," +
                "  password_hash TEXT NOT NULL," +
                "  role TEXT NOT NULL DEFAULT 'player'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS challenges (" +
                "  challenge_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  played_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  text_excerpt TEXT," +
                "  encrypted_word TEXT," +
                "  original_word TEXT," +
                "  caesar_shift INTEGER" +
                ")"
            );
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS results (" +
                "  result_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  challenge_id INTEGER NOT NULL," +
                "  user_id INTEGER NOT NULL," +
                "  outcome TEXT NOT NULL," +
                "  response_time_ms INTEGER," +
                "  FOREIGN KEY(challenge_id) REFERENCES challenges(challenge_id)," +
                "  FOREIGN KEY(user_id) REFERENCES users(user_id)" +
                ")"
            );
        }
    }

    /**
     * Inserisce gli account predefiniti se non esistono già.
     * Admin: admin/admin123 | Player1: player1/pass1 | Player2: player2/pass2
     */
    private void seedAccounts() throws SQLException {
        insertUserIfAbsent("admin",   "admin123",  "admin");
        insertUserIfAbsent("player1", "pass1",     "player");
        insertUserIfAbsent("player2", "pass2",     "player");
    }

    private void insertUserIfAbsent(String username, String password, String role) throws SQLException {
        String check = "SELECT COUNT(*) FROM users WHERE username=?";
        try (PreparedStatement ps = connection.prepareStatement(check)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1) == 0) {
                String insert = "INSERT INTO users(username, password_hash, role) VALUES(?,?,?)";
                try (PreparedStatement ins = connection.prepareStatement(insert)) {
                    ins.setString(1, username);
                    ins.setString(2, hashPassword(password));
                    ins.setString(3, role);
                    ins.executeUpdate();
                }
            }
        }
    }

    /**
     * Autentica un utente verificando username e password hash.
     *
     * @param username username
     * @param password password in chiaro
     * @return ruolo utente ("admin"/"player") o null se le credenziali non sono valide
     */
    public String authenticate(String username, String password) {
        String sql = "SELECT role FROM users WHERE username=? AND password_hash=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hashPassword(password));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("role");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Errore autenticazione", e);
        }
        return null;
    }

    /**
     * Registra un nuovo utente.
     *
     * @param username username
     * @param password password in chiaro
     * @return true se registrazione avvenuta con successo, false se username già esistente
     */
    public boolean registerUser(String username, String password) {
        String sql = "INSERT INTO users(username, password_hash, role) VALUES(?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hashPassword(password));
            ps.setString(3, "player");
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            // username già esistente (UNIQUE constraint)
            return false;
        }
    }

    /**
     * Restituisce l'ID utente dato lo username.
     *
     * @param username username
     * @return user_id o -1 se non trovato
     */
    public int getUserId(String username) {
        String sql = "SELECT user_id FROM users WHERE username=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("user_id");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Errore getUserId", e);
        }
        return -1;
    }

    /**
     * Salva una nuova sfida e restituisce il suo ID.
     *
     * @param textExcerpt  estratto di testo
     * @param encryptedWord parola cifrata
     * @param originalWord parola originale
     * @param shift        shift del cifrario
     * @return challenge_id generato
     */
    public int saveChallenge(String textExcerpt, String encryptedWord,
                             String originalWord, int shift) {
        String sql = "INSERT INTO challenges(text_excerpt, encrypted_word, original_word, caesar_shift)" +
                     " VALUES(?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, textExcerpt);
            ps.setString(2, encryptedWord);
            ps.setString(3, originalWord);
            ps.setInt(4, shift);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Errore saveChallenge", e);
        }
        return -1;
    }

    /**
     * Salva il risultato di un singolo partecipante a una sfida.
     *
     * @param challengeId    id della sfida
     * @param userId         id utente
     * @param outcome        esito (win/loss/draw)
     * @param responseTimeMs tempo risposta in ms (-1 se timeout)
     */
    public void saveResult(int challengeId, int userId, String outcome, long responseTimeMs) {
        String sql = "INSERT INTO results(challenge_id, user_id, outcome, response_time_ms) VALUES(?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, challengeId);
            ps.setInt(2, userId);
            ps.setString(3, outcome);
            ps.setLong(4, responseTimeMs);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Errore saveResult", e);
        }
    }

    /**
     * Restituisce lo storico delle sfide di un utente.
     *
     * @param username username del giocatore
     * @return lista di HistoryEntry
     */
    public List<HistoryEntry> getHistory(String username) {
        List<HistoryEntry> list = new ArrayList<>();
        String sql =
            "SELECT c.played_at, c.original_word, r.outcome, r.response_time_ms," +
            "       (SELECT u2.username FROM results r2 JOIN users u2 ON r2.user_id=u2.user_id" +
            "        WHERE r2.challenge_id=c.challenge_id AND u2.username != ?) AS opponent " +
            "FROM results r " +
            "JOIN challenges c ON r.challenge_id=c.challenge_id " +
            "JOIN users u ON r.user_id=u.user_id " +
            "WHERE u.username=? " +
            "ORDER BY c.played_at DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, username);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new HistoryEntry(
                    rs.getString("played_at"),
                    rs.getString("opponent") != null ? rs.getString("opponent") : "?",
                    rs.getString("outcome"),
                    rs.getLong("response_time_ms"),
                    rs.getString("original_word")
                ));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Errore getHistory", e);
        }
        return list;
    }

    /**
     * Struttura per le statistiche di un utente mostrate nell'admin.
     */
    public static class UserStats {
        public String username;
        public int wins;
        public int gamesPlayed;
        public long avgResponseMs;

        /**
         * Costruisce le statistiche di un utente.
         *
         * @param username      username
         * @param wins          vittorie
         * @param gamesPlayed   partite giocate
         * @param avgResponseMs tempo medio di risposta in ms
         */
        public UserStats(String username, int wins, int gamesPlayed, long avgResponseMs) {
            this.username = username;
            this.wins = wins;
            this.gamesPlayed = gamesPlayed;
            this.avgResponseMs = avgResponseMs;
        }
    }

    /**
     * Restituisce le statistiche di tutti gli utenti per la classifica admin.
     *
     * @return lista UserStats ordinata per vittorie decrescenti
     */
    public List<UserStats> getAllStats() {
        List<UserStats> stats = new ArrayList<>();
        String sql =
            "SELECT u.username," +
            "  SUM(CASE WHEN r.outcome='win' THEN 1 ELSE 0 END) AS wins," +
            "  COUNT(*) AS games_played," +
            "  CAST(AVG(r.response_time_ms) AS INTEGER) AS avg_ms " +
            "FROM results r " +
            "JOIN users u ON r.user_id=u.user_id " +
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

    /**
     * Calcola l'hash SHA-256 di una password.
     *
     * @param password password in chiaro
     * @return stringa hex dell'hash
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
            throw new RuntimeException("SHA-256 non disponibile", e);
        }
    }
}
