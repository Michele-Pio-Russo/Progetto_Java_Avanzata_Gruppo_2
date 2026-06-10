# GuessTheWord — Progetto Finale JA26

## Descrizione

**GuessTheWord** è un gioco multiplayer client-server in Java con GUI JavaFX.
Due giocatori si sfidano a indovinare una parola cifrata con il **Cifrario di Cesare**,
estratta da un corpus testuale analizzato dal server tramite **Term Frequency**.

---

## Architettura del Progetto

```
GuessTheWord/
├── common/          ← Classi condivise (Message, payload, CaesarCipher…)
├── server/          ← Applicazione server (JavaFX + rete + DB + analisi)
├── client/          ← Applicazione client (JavaFX + login + gioco + storico)
├── run/             ← Directory di esecuzione (properties, db, documents, data)
└── pom.xml          ← Build Maven multi-modulo
```

### Moduli

| Modulo   | Descrizione |
|----------|-------------|
| `common` | Protocollo di comunicazione: `Message`, `AuthPayload`, `AuthResponse`, `ChallengePayload`, `ChallengeResult`, `HistoryEntry`, `CaesarCipher` |
| `server` | GUI amministratore, analisi TF, ServerSocket, GameCoordinator, DatabaseManager (SQLite) |
| `client` | GUI login/registrazione, finestra di gioco con timer, storico partite |

---

## Requisiti

- **Java 11+** (consigliato Java 17 LTS)
- **Maven 3.8+**
- **JavaFX 17** (incluso nel fat-JAR tramite Maven Shade)
- **SQLite JDBC** (incluso automaticamente)

---

## Compilazione

Dalla directory radice del progetto:

```bash
mvn clean package -DskipTests
```

Vengono generati due fat-JAR:
- `server/target/GuessTheWord-Server.jar`
- `client/target/GuessTheWord-Client.jar`

---

## Struttura Directory di Esecuzione

Prima di avviare, copia i JAR nella directory `run/` e verifica la struttura:

```
run/
├── GuessTheWord-Server.jar
├── GuessTheWord-Client.jar
├── properties/
│   ├── server.properties      ← porta, percorso DB, timeout sfida
│   └── client.properties      ← IP e porta del server
├── db/                        ← Il database SQLite viene creato automaticamente qui
├── documents/                 ← Metti qui i file TXT da analizzare
│   ├── testo_italiano.txt
│   └── testo_matematica.txt
└── data/                      ← Analisi serializzate (.dat)
```

---

## Avvio

### 1. Avvia il Server

```bash
cd run
java -jar GuessTheWord-Server.jar
```

Si apre la GUI amministratore. Operazioni da fare:

1. **Seleziona file TXT** → `📂 Seleziona file TXT` (scegli quelli nella cartella `documents/`)
2. **Avvia Analisi** → `▶ Avvia Analisi` (barra di avanzamento)
3. **(Opzionale) Salva Analisi** → per ricaricarla in seguito senza rianalizzare
4. **Avvia Server** → `▶ Avvia Server` (il server ascolta sulla porta configurata)

### 2. Avvia due Client

In due terminali separati (o su macchine diverse se si configura `server.ip`):

```bash
cd run
java -jar GuessTheWord-Client.jar
```

Ogni client mostra la schermata di **login/registrazione**.

---

## Account Predefiniti

Al primo avvio il database viene popolato automaticamente:

| Username | Password | Ruolo  |
|----------|----------|--------|
| admin    | admin123 | admin  |
| player1  | pass1    | player |
| player2  | pass2    | player |

> Gli utenti **admin** accedono alla sola GUI server (non giocano).
> I **player** accedono tramite il client e partecipano alle sfide.

---

## Flusso di Gioco

```
Client 1 ──login──► Server ──waiting──► Client 1
Client 2 ──login──► Server ──waiting──► Client 2
                        │
                   (2 giocatori pronti)
                        │
              Estrae parola dal corpus
              Cifra con Cesare (shift casuale)
              Invia estratto + parola cifrata
                        │
          ┌─────────────┴─────────────┐
       Client 1                    Client 2
    Inserisce risposta          Inserisce risposta
          │                            │
          └────────► Server ◄──────────┘
                        │
              Primo che risponde correttamente VINCE
              (o timeout → pareggio)
                        │
              Salva risultato nel DB
              Invia ChallengeResult a entrambi
```

---

## Configurazione

### `properties/server.properties`

```properties
server.port=5000
db.path=db/database.db
analysis.save.path=data/analysis.dat
game.timeout.seconds=60
game.excerpt.words=60
```

### `properties/client.properties`

```properties
server.ip=127.0.0.1
server.port=5000
```

Per giocare in rete locale, sostituisci `127.0.0.1` con l'IP della macchina server.

---

## Funzionalità Implementate

### Server (Admin GUI)
- [x] Avvio/stop del server TCP
- [x] Selezione multipla di file TXT
- [x] **Analisi asincrona Term Frequency** con JavaFX Service (non blocca la GUI)
- [x] Tabella risultati TF (parola → frequenza, ordinata per frequenza)
- [x] Salvataggio/caricamento analisi serializzata
- [x] **Classifica utenti** (vittorie, partite, tempo medio risposta)
- [x] Log di sistema in tempo reale

### Client (Player GUI)
- [x] **Login** con username/password
- [x] **Registrazione** nuovo account
- [x] Schermata di attesa avversario con indicatore di caricamento
- [x] **Finestra di gioco**: estratto testo, parola cifrata evidenziata, timer countdown
- [x] Invio risposta (tasto Invio o pulsante)
- [x] Feedback risposta errata (senza penalità, può riprovare)
- [x] **Schermata risultato**: vinto/perso/pareggio con parola corretta
- [x] **Storico partite** con data, avversario, esito, parola, tempo risposta

### Meccanica di Gioco
- [x] Cifrario di Cesare con shift casuale (1–25)
- [x] Selezione parola basata su Term Frequency (parole più rare = più difficili)
- [x] Estratto testuale di ~60 parole dal corpus analizzato
- [x] Timer configurabile (default 60 secondi)
- [x] Salvataggio automatico di ogni sfida nel database SQLite

---

## Database Schema

```sql
-- Utenti registrati
users (user_id, username, password_hash, role, created_at)

-- Sfide giocate
challenges (challenge_id, played_at, text_excerpt, encrypted_word, original_word, caesar_shift)

-- Risultati per giocatore
results (result_id, challenge_id, user_id, outcome, response_time_ms)
```

---

## Note Tecniche

- **Serializzazione**: tutta la comunicazione usa `ObjectOutputStream`/`ObjectInputStream` con classi `Serializable`
- **Thread safety**: `GameCoordinator` è `synchronized`; la GUI usa `Platform.runLater()`
- **Password**: hash SHA-256 lato server (non vengono mai salvate in chiaro)
- **Timeout**: gestito con `ScheduledExecutorService` lato server
- **Stream API**: l'analisi TF usa Java Stream API per tokenizzazione, conteggio e ordinamento

---

## Autore

Progetto Finale — Corso Java Avanzato JA26
