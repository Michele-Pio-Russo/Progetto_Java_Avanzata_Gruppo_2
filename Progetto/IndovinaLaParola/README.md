# GuessTheWord — Progetto Finale JA26

**Corso:** Java Programmazione Avanzata (JA26)  
**Università:** Università degli Studi di Salerno — DIEM  
**Anno Accademico:** 2025/2026  
**Compatibilità:** JDK 8+

---

## Descrizione

GuessTheWord è un gioco multiplayer client-server. Due giocatori si sfidano a
indovinare una parola cifrata con il **Cifrario di Cesare**, estratta da un corpus
testuale analizzato tramite **Term Frequency** (TF).

Il progetto dimostra i seguenti argomenti del corso JA26:

| Modulo | Tecnologia applicata |
|--------|----------------------|
| 2 — Enum & tipi | `Message.Type` (enum), `ChallengeResult.Outcome` (enum con metodi) |
| 4 — Lambda | `Consumer<String>` callback, `Comparator` lambda, `CellValueFactory` |
| 5 — Stream API | Pipeline TF: `groupingBy`, `toMap`, `sorted`, `limit`, `joining` |
| 6 — Concurrency | `ScheduledExecutorService`, `synchronized`, `unmodifiableMap`, `Platform.runLater` |
| 7 — Networking | `ServerSocket`, `ObjectOutputStream/InputStream`, thread daemon |
| 8 — JDBC | `PreparedStatement`, transazioni, DAO, `try-with-resources` |
| JavaFX | `Service`+`Task`, `Timeline`, FXML, CSS, `Property` binding |

---

## Struttura del Progetto

```
GuessTheWord/
├── pom.xml              ← root Maven (multi-modulo, JDK 8)
├── common/              ← classi Serializable condivise tra client e server
├── server/              ← GUI admin + rete + DB + analisi TF
├── client/              ← GUI login + gioco + storico
└── run/                 ← directory di esecuzione
    ├── properties/      ← server.properties, client.properties
    ├── documents/       ← file .txt da analizzare
    ├── db/              ← database SQLite (creato automaticamente)
    └── data/            ← analisi serializzate (.dat)
```

---

## Compilazione

Dalla directory radice del progetto:

```bash
mvn clean package -DskipTests
```

Vengono generati:
- `server/target/server.jar`
- `client/target/client.jar`

---

## Avvio

### Passo 1 — Copia i JAR nella cartella run/

```
run/
├── server.jar
├── client.jar
├── properties/
│   ├── server.properties
│   └── client.properties
├── documents/
│   ├── testo_informatica.txt
│   └── testo_matematica.txt
├── db/
└── data/
```

### Passo 2 — Avvia il Server

```bash
cd run
java -jar server.jar
```

Nella GUI del server:
1. Clicca **Seleziona file .txt** → scegli i file nella cartella `documents/`
2. Clicca **Avvia Analisi** → attendi il completamento (barra progresso)
3. Clicca **Avvia Server** → il log mostra "Server avviato sulla porta 5000"

### Passo 3 — Avvia due Client (terminali separati)

```bash
# Terminale 1
cd run && java -jar client.jar

# Terminale 2
cd run && java -jar client.jar
```

Ogni client mostra la schermata di login.

---

## Account Predefiniti

Creati automaticamente al primo avvio del server:

| Username | Password | Ruolo  |
|----------|----------|--------|
| admin    | admin123 | admin  |
| player1  | pass1    | player |
| player2  | pass2    | player |

Nuovi account si possono creare dalla schermata di registrazione del client.

---

## Flusso di Gioco

1. Entrambi i client eseguono il login con account "player"
2. Il server li accoppia automaticamente (lista d'attesa)
3. Il server estrae un brano dal corpus analizzato
4. Sceglie una parola e la cifra con il Cifrario di Cesare (shift casuale 1–25)
5. Invia ai due client il testo con la parola cifrata evidenziata tra `[...]`
6. I giocatori hanno **60 secondi** per digitare la parola originale
7. Chi risponde correttamente per primo **vince**
8. In caso di timeout → **pareggio**
9. I risultati vengono salvati nel database SQLite

---

## Configurazione

### `properties/server.properties`
```properties
server.port=5000
game.timeout.seconds=60
game.excerpt.words=60
```

### `properties/client.properties`
```properties
server.ip=127.0.0.1
server.port=5000
```

Per giocare in rete locale, sostituire `127.0.0.1` con l'IP della macchina server.

---

## Note Tecniche

- **Password**: hash SHA-256 (mai in chiaro nel DB)
- **Transazioni JDBC**: `saveChallenge` + `saveResult` in transazione atomica
- **Thread safety**: `GameCoordinator` completamente `synchronized`
- **Analisi asincrona**: `AnalysisService` (JavaFX `Service`) — la GUI rimane reattiva
- **Timer**: `Timeline` JavaFX — rimane nel FX Application Thread
- **Stream OOS**: `out.reset()` dopo ogni `writeObject` per evitare object caching
