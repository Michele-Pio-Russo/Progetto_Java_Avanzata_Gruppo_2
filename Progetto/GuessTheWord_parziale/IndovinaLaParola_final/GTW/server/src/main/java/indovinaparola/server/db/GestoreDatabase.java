package indovinaparola.server.db;

import indovinaparola.common.VoceStorico;

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
public class GestoreDatabase {

    private static final Logger LOGGER = Logger.getLogger(GestoreDatabase.class.getName());

    private static GestoreDatabase instance;
    private Connection connessione;
    private final String dbPath;

    /**
     * Costruttore privato — pattern Singleton.
     *
     * @param dbPath percorso relativo al file SQLite
     */
    private GestoreDatabase(String dbPath) {
        this.dbPath = dbPath;
    }

    /**
     * Restituisce l'istanza Singleton del GestoreDatabase.
     * Crea l'istanza al primo invocation (lazy initialization).
     *
     * @param dbPath percorso relativo al file database SQLite
     * @return istanza singleton
     */
    public static synchronized GestoreDatabase getInstance(String dbPath) {
        if (instance == null) {
            instance = new GestoreDatabase(dbPath);
        }
        return instance;
    }

    /**
     * Apre la connessione al database e inizializza schema e account predefiniti.
     * Deve essere chiamato una sola volta all'avvio del server.
     *
     * @throws SQLException in caso di errore di connessione o inizializzazione
     */
    public void connetti() throws SQLException {
        connessione = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        connessione.setAutoCommit(true);
        initSchema();
        seedAccounts();
        LOGGER.info("Database connesso: " + dbPath);
    }

    /**
     * Chiude la connessione al database.
     */
    public void disconnetti() {
        try {
            if (connessione != null && !connessione.isClosed()) {
                connessione.close();
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
        try (Statement stmt = connessione.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                "  user_id       INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  nomeUtente      TEXT    UNIQUE NOT NULL," +
                "  password_hash TEXT    NOT NULL," +
                "  ruolo          TEXT    NOT NULL DEFAULT 'player'," +
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
                "  esito          TEXT    NOT NULL," +
                "  response_time_ms INTEGER," +
                "  FOREIGN KEY (challenge_id) REFERENCES challenges(challenge_id)," +
                "  FOREIGN KEY (user_id)      REFERENCES users(user_id)" +
                ")"
            );
        }
    }

    /**
     * Inserisce gli account predefiniti se non sono già presenti nel database.
     * Account creati: admin/admin123 (ruolo admin), giocatore1/pass1, giocatore2/pass2 (ruolo player).
     *
     * @throws SQLException in caso di errore di inserimento
     */
    private void seedAccounts() throws SQLException {
        insertUserIfAbsent("admin",   "admin123", "admin");
        insertUserIfAbsent("giocatore1", "pass1",    "player");
        insertUserIfAbsent("giocatore2", "pass2",    "player");
    }

    /**
     * Inserisce un utente solo se lo nomeUtente non è già presente.
     *
     * @param nomeUtente nomeUtente da inserire
     * @param password password in chiaro (verrà hashata)
     * @param ruolo     ruolo: "admin" o "player"
     * @throws SQLException in caso di errore SQL
     */
    private void insertUserIfAbsent(String nomeUtente, String password, String ruolo)
            throws SQLException {
        // try-with-resources per PreparedStatement (Modulo 8)
        try (PreparedStatement check = connessione.prepareStatement(
                "SELECT COUNT(*) FROM users WHERE nomeUtente = ?")) {
            check.setString(1, nomeUtente);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (PreparedStatement ins = connessione.prepareStatement(
                            "INSERT INTO users(nomeUtente, password_hash, ruolo) VALUES(?,?,?)")) {
                        ins.setString(1, nomeUtente);
                        ins.setString(2, hashPassword(password));
                        ins.setString(3, ruolo);
                        ins.executeUpdate();
                        LOGGER.info("Account predefinito creato: " + nomeUtente);
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------
    // Autenticazione
    // ----------------------------------------------------------------

    /**
     * Autentica un utente verificando nomeUtente e hash della password.
     *
     * @param nomeUtente nomeUtente da verificare
     * @param password password in chiaro
     * @return ruolo dell'utente ("admin" o "player"), oppure null se le credenziali non sono valide
     */
    public String autentica(String nomeUtente, String password) {
        String sql = "SELECT ruolo FROM users WHERE nomeUtente = ? AND password_hash = ?";
        try (PreparedStatement ps = connessione.prepareStatement(sql)) {
            ps.setString(1, nomeUtente);
            ps.setString(2, hashPassword(password));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("ruolo");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Errore autenticazione per: " + nomeUtente, e);
        }
        return null;
    }

    /**
     * Registra un nuovo utente con ruolo "player".
     *
     * @param nomeUtente nomeUtente scelto dall'utente
     * @param password password in chiaro (verrà hashata)
     * @return true se la registrazione è avvenuta con successo,
     *         false se lo nomeUtente è già occupato
     */
    public boolean registraUtente(String nomeUtente, String password) {
        String sql = "INSERT INTO users(nomeUtente, password_hash, ruolo) VALUES(?,?,?)";
        try (PreparedStatement ps = connessione.prepareStatement(sql)) {
            ps.setString(1, nomeUtente);
            ps.setString(2, hashPassword(password));
            ps.setString(3, "player");
            ps.executeUpdate();
            LOGGER.info("Nuovo utente registrato: " + nomeUtente);
            return true;
        } catch (SQLException e) {
            // Violazione UNIQUE constraint → nomeUtente già esistente
            return false;
        }
    }

    /**
     * Restituisce l'identificativo numerico di un utente dato il suo nomeUtente.
     *
     * @param nomeUtente nomeUtente da cercare
     * @return user_id, oppure -1 se non trovato
     */
    public int getUserId(String nomeUtente) {
        String sql = "SELECT user_id FROM users WHERE nomeUtente = ?";
        try (PreparedStatement ps = connessione.prepareStatement(sql)) {
            ps.setString(1, nomeUtente);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("user_id");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Errore getUserId per: " + nomeUtente, e);
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
     * @param estrattoTesto   estratto testuale
     * @param encryptedWord parola cifrata
     * @param originalWord  parola originale
     * @param spostamento         spostamento del cifrario di Cesare
     * @param winnerUser    nomeUtente del vincitore (null se pareggio)
     * @param loserUser     nomeUtente del perdente (null se pareggio)
     * @param draw          true se la sfida è terminata in pareggio
     * @param tempoRispostaMs tempo di risposta del vincitore in ms (-1 se pareggio)
     */
    public void saveChallengeWithResults(
            String estrattoTesto, String encryptedWord, String originalWord, int spostamento,
            String winnerUser, String loserUser, boolean draw, long tempoRispostaMs) {

        if (connessione == null) {
            LOGGER.severe("saveChallengeWithResults: nessuna connessione al database "
                + "(probabile driver SQLite non caricato). Risultato NON salvato.");
            return;
        }

        try {
            // Inizio transazione esplicita
            connessione.setAutoCommit(false);

            try {
                // 1. Salva la sfida
                int idSfida = insertChallenge(estrattoTesto, encryptedWord, originalWord, spostamento);

                // 2. Salva i risultati
                if (draw) {
                    int id1 = getUserId(winnerUser != null ? winnerUser : "");
                    int id2 = getUserId(loserUser  != null ? loserUser  : "");
                    LOGGER.info("saveChallengeWithResults draw: u1=" + winnerUser + " id1=" + id1 + " | u2=" + loserUser + " id2=" + id2);
                    if (id1 > 0) insertResult(idSfida, id1, "draw", -1);
                    if (id2 > 0) insertResult(idSfida, id2, "draw", -1);
                } else {
                    int winnerId = getUserId(winnerUser);
                    int loserId  = getUserId(loserUser);
                    LOGGER.info("saveChallengeWithResults win: winner=" + winnerUser + " id=" + winnerId + " | loser=" + loserUser + " id=" + loserId);
                    if (winnerId > 0) insertResult(idSfida, winnerId, "win",  tempoRispostaMs);
                    if (loserId  > 0) insertResult(idSfida, loserId,  "loss", -1);
                }

                // Commit — tutto ok
                connessione.commit();
                LOGGER.info("Transazione sfida+risultati salvata. ChallengeId=" + idSfida);

            } catch (SQLException e) {
                // Rollback in caso di errore
                try { connessione.rollback(); } catch (SQLException ex) { /* ignore */ }
                LOGGER.log(Level.SEVERE, "Rollback transazione sfida: " + e.getMessage(), e);
                System.err.println("ERRORE DB saveChallengeWithResults: " + e.getMessage());
                e.printStackTrace();
            } finally {
                connessione.setAutoCommit(true);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Errore gestione transazione", e);
        } catch (RuntimeException e) {
            // Difesa extra: qualunque errore inatteso (es. NPE) non deve
            // propagarsi al thread di rete del GestoreClient, altrimenti
            // il client che ha inviato CHALLENGE_ANSWER verrebbe disconnesso.
            LOGGER.log(Level.SEVERE, "Errore inatteso nel salvataggio risultati sfida", e);
        }
    }

    /**
     * Inserisce una riga nella tabella challenges e restituisce il challenge_id generato.
     *
     * @param estrattoTesto   estratto testuale
     * @param encryptedWord parola cifrata
     * @param originalWord  parola originale
     * @param spostamento         spostamento del cifrario
     * @return challenge_id generato, oppure -1 in caso di errore
     * @throws SQLException in caso di errore SQL
     */
    private int insertChallenge(String estrattoTesto, String encryptedWord,
                                String originalWord, int spostamento) throws SQLException {
        String sql = "INSERT INTO challenges(text_excerpt, encrypted_word, original_word, caesar_shift)" +
                     " VALUES(?,?,?,?)";
        try (PreparedStatement ps = connessione.prepareStatement(sql)) {
            ps.setString(1, estrattoTesto);
            ps.setString(2, encryptedWord);
            ps.setString(3, originalWord);
            ps.setInt(4, spostamento);
            ps.executeUpdate();
        }
        // last_insert_rowid() è supportato da tutti i driver SQLite JDBC
        try (Statement st = connessione.createStatement();
             ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            if (rs.next()) return rs.getInt(1);
        }
        return -1;
    }

    /**
     * Inserisce un risultato per un singolo giocatore nella tabella results.
     *
     * @param idSfida    id della sfida
     * @param userId         id dell'utente
     * @param esito        esito: "win", "loss" o "draw"
     * @param tempoRispostaMs tempo di risposta in ms (-1 se timeout)
     * @throws SQLException in caso di errore SQL
     */
    private void insertResult(int idSfida, int userId,
                              String esito, long tempoRispostaMs) throws SQLException {
        String sql = "INSERT INTO results(challenge_id, user_id, esito, response_time_ms)" +
                     " VALUES(?,?,?,?)";
        try (PreparedStatement ps = connessione.prepareStatement(sql)) {
            ps.setInt(1, idSfida);
            ps.setInt(2, userId);
            ps.setString(3, esito);
            ps.setLong(4, tempoRispostaMs);
            ps.executeUpdate();
        }
    }

    // ----------------------------------------------------------------
    // Storico e statistiche
    // ----------------------------------------------------------------

    /**
     * Restituisce lo storico delle sfide di un utente, ordinate dalla più recente.
     *
     * @param nomeUtente nomeUtente del giocatore
     * @return lista di {@link VoceStorico}, vuota se nessuna sfida trovata
     */
    public List<VoceStorico> getHistory(String nomeUtente) {
        List<VoceStorico> list = new ArrayList<>();
        String sql =
            "SELECT c.played_at, c.original_word, r.esito, r.response_time_ms," +
            "  (SELECT u2.nomeUtente FROM results r2" +
            "   JOIN users u2 ON r2.user_id = u2.user_id" +
            "   WHERE r2.challenge_id = c.challenge_id AND u2.nomeUtente != ?) AS avversario " +
            "FROM results r " +
            "JOIN challenges c ON r.challenge_id = c.challenge_id " +
            "JOIN users u ON r.user_id = u.user_id " +
            "WHERE u.nomeUtente = ? " +
            "ORDER BY c.played_at DESC";
        try (PreparedStatement ps = connessione.prepareStatement(sql)) {
            ps.setString(1, nomeUtente);
            ps.setString(2, nomeUtente);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String avversario = rs.getString("avversario");
                    list.add(new VoceStorico(
                        rs.getString("played_at"),
                        avversario != null ? avversario : "?",
                        rs.getString("esito"),
                        rs.getLong("response_time_ms"),
                        rs.getString("original_word")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Errore getHistory per: " + nomeUtente, e);
        }
        return list;
    }

    /**
     * Classe interna che rappresenta le statistiche aggregate di un utente.
     * Usata per popolare la classifica nella GUI del server.
     */
    public static class UserStats {
        /** Username dell'utente. */
        public final String nomeUtente;
        /** Numero di sfide vinte. */
        public final int wins;
        /** Numero totale di sfide giocate. */
        public final int gamesPlayed;
        /** Tempo medio di risposta in millisecondi. */
        public final long avgResponseMs;

        /**
         * Costruisce le statistiche di un utente.
         *
         * @param nomeUtente      nomeUtente
         * @param wins          vittorie
         * @param gamesPlayed   partite totali
         * @param avgResponseMs tempo medio risposta in ms
         */
        public UserStats(String nomeUtente, int wins, int gamesPlayed, long avgResponseMs) {
            this.nomeUtente = nomeUtente;
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
            "SELECT u.nomeUtente," +
            "  SUM(CASE WHEN r.esito = 'win' THEN 1 ELSE 0 END) AS wins," +
            "  COUNT(*) AS games_played," +
            "  CAST(AVG(CASE WHEN r.response_time_ms >= 0 THEN r.response_time_ms END) AS INTEGER) AS avg_ms " +
            "FROM results r " +
            "JOIN users u ON r.user_id = u.user_id " +
            "GROUP BY r.user_id " +
            "ORDER BY wins DESC";
        try (Statement stmt = connessione.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                stats.add(new UserStats(
                    rs.getString("nomeUtente"),
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
