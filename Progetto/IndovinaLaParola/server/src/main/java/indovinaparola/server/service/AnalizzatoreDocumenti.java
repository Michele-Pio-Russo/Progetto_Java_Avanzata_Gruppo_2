/**
 * @file AnalizzatoreDocumenti.java
 * @brief Elabora i documenti testuali e calcola la frequenza delle parole (Term Frequency).
 *
 * Questa classe permette di istanziare un oggetto AnalizzatoreDocumenti. Fornisce metodi per eseguire
 * l'analisi sui file di testo e metodi getter per ottenere i risultati (come la frequenza dei termini
 * calcolata e il testo aggregato), oltre a metodi per l'estrazione casuale di parole per le sfide.
 *
 * @author Gruppo 2
 * @date 
 * @version 1.0.0
 */
package indovinaparola.server.service;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @brief Analizzatore di documenti testuali.
 * @brief Calcola la Term Frequency (TF) di ogni parola presente nei file analizzati,
 * usando esclusivamente la Java Stream API.
 *
 * Implementa {@link Serializable} per permettere il salvataggio e il
 * ricaricamento dei risultati dell'analisi senza dover rielaborare i documenti.
 *
 * La mappa TF viene esposta tramite {@link Collections#unmodifiableMap} come
 * oggetto immutabile — tecnica raccomandata dal Modulo 6 (Concurrency) per
 * condivisione sicura tra thread.
 */
public class AnalizzatoreDocumenti implements Serializable {

    private static final long serialVersionUID = 1L; ///< Identificatore di versione per la serializzazione
    private static final Logger LOGGER = Logger.getLogger(AnalizzatoreDocumenti.class.getName());

    /** @brief Mappa parola (lowercase) → frequenza relativa (TF = count / totalWords). */
    private Map<String, Double> termFrequency = new HashMap<>();

    /** @brief Testo aggregato di tutti i documenti analizzati. */
    private String aggregatedText = "";

    /**
     * @brief Analizza una lista di file di testo e calcola la Term Frequency.
     * @brief L'elaborazione usa esclusivamente la Java Stream API.
     *
     * @param[in] files lista di file .txt da analizzare (non null, non vuota)
     * @throws IOException in caso di errore di lettura di uno dei file
     */
    public void analyze(List<File> files) throws IOException {
        // Lettura e concatenazione di tutti i file
        StringBuilder sb = new StringBuilder();
        for (File f : files) {
            byte[] bytes = Files.readAllBytes(f.toPath());
            sb.append(new String(bytes, "UTF-8")).append(" ");
        }
        aggregatedText = sb.toString();

        // Tokenizzazione: lowercase + rimozione punteggiatura + split
        String[] tokens = aggregatedText
            .toLowerCase()
            .replaceAll("[^a-zA-Z\\s]", " ")
            .split("\\s+");

        // Conteggio totale parole valide con Stream
        final long totalWords = Arrays.stream(tokens)
            .filter(t -> !t.isEmpty())
            .count();

        if (totalWords == 0) {
            LOGGER.warning("Nessuna parola trovata nei documenti selezionati.");
            return;
        }

        // Pipeline Stream:
        // filter → groupingBy (counting) → entrySet stream → toMap (calcolo TF)
        termFrequency = Arrays.stream(tokens)
            .filter(t -> !t.isEmpty() && t.length() > 2)
            .collect(Collectors.groupingBy(
                t -> t,                         // classifier: parola stessa
                Collectors.counting()           // downstream: conta occorrenze
            ))
            .entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> (double) e.getValue() / totalWords   // TF = count / N
            ));

        LOGGER.info("Analisi completata: " + termFrequency.size()
            + " parole uniche su " + totalWords + " totali.");
    }

    /**
     * @brief Restituisce la mappa Term Frequency come vista non modificabile.
     * @brief Esporre una vista immutabile protegge la struttura interna da modifiche
     * esterne e rende l'oggetto sicuro per la condivisione tra thread.
     *
     * @return mappa non modificabile parola → TF
     */
    public Map<String, Double> getTermFrequency() {
        return Collections.unmodifiableMap(termFrequency);
    }

    /**
     * @brief Restituisce il testo aggregato di tutti i documenti analizzati.
     *
     * @return testo completo
     */
    public String getAggregatedText() {
        return aggregatedText;
    }

    /**
     * @brief Indica se l'analisi è stata effettuata con successo.
     *
     * @return true se la mappa TF contiene almeno un elemento
     */
    public boolean hasResults() {
        return !termFrequency.isEmpty();
    }

    /**
     * @brief Estrae un estratto casuale dal testo analizzato di circa {@code numWords} parole.
     * @brief Usato dal server per fornire ai client il testo della sfida.
     *
     * @param[in] numWords numero approssimativo di parole dell'estratto
     * @return estratto testuale, stringa vuota se il testo è vuoto
     */
    public String extractRandomExcerpt(int numWords) {
        if (aggregatedText.isEmpty()) return "";
        String[] allWords = aggregatedText.split("\\s+");
        if (allWords.length <= numWords) return aggregatedText;
        Random rnd = new Random();
        int start = rnd.nextInt(allWords.length - numWords);
        // Stream per assemblare l'estratto
        return Arrays.stream(allWords, start, start + numWords)
            .collect(Collectors.joining(" "));
    }

    /**
     * @brief Seleziona una parola dall'estratto in base al livello di difficoltà.
     * @brief Con difficoltà alta vengono preferite parole con TF bassa (più rare),
     * con difficoltà bassa parole con TF alta (più comuni e facili).
     *
     * Usa la Stream API con {@link Comparator} lambda (Moduli 4 e 5).
     *
     * @param[in] excerpt    estratto da cui selezionare la parola
     * @param[in] difficulty livello: 1=facile (parole comuni), 2=medio, 3=difficile (parole rare)
     * @return parola selezionata, oppure null se nessuna parola idonea trovata
     */
    public String selectWordFromExcerpt(String excerpt, int difficulty) {
        String[] words = excerpt.toLowerCase()
            .replaceAll("[^a-z\\s]", "")
            .split("\\s+");

        // Lambda Comparator: ordina per TF ascendente (difficile) o discendente (facile)
        final boolean preferRare = (difficulty >= 2);
        Comparator<String> byTf = (a, b) -> {
            double tfA = termFrequency.getOrDefault(a, 0.0);
            double tfB = termFrequency.getOrDefault(b, 0.0);
            return preferRare
                ? Double.compare(tfA, tfB)   // crescente: parole rare prima
                : Double.compare(tfB, tfA);  // decrescente: parole comuni prima
        };

        // Pipeline Stream: filter → sorted (lambda Comparator) → limit → collect
        List<String> candidates = Arrays.stream(words)
            .filter(w -> w.length() >= 4 && termFrequency.containsKey(w))
            .sorted(byTf)
            .limit(5)
            .collect(Collectors.toList());

        if (candidates.isEmpty()) return null;

        // Selezione casuale tra i top-5 candidati
        return candidates.get(new Random().nextInt(candidates.size()));
    }

    /**
     * @brief Salva l'istanza corrente su file in formato serializzato binario.
     * @brief Permette di ricaricare i risultati dell'analisi nelle sessioni successive
     * senza dover rielaborare i documenti.
     *
     * @param[in] outputPath percorso del file di output (es. "data/analysis.dat")
     * @throws IOException in caso di errore di scrittura
     */
    public void saveToFile(String outputPath) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(outputPath))) {
            oos.writeObject(this);
        }
        LOGGER.info("Analisi salvata in: " + outputPath);
    }

    /**
     * @brief Carica un'istanza di {@link AnalizzatoreDocumenti} precedentemente serializzata.
     *
     * @param[in] inputPath percorso del file da caricare
     * @return istanza caricata con i risultati dell'analisi precedente
     * @throws IOException            in caso di errore di lettura
     * @throws ClassNotFoundException se la classe non è trovata nel classpath
     */
    public static AnalizzatoreDocumenti loadFromFile(String inputPath)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(inputPath))) {
            AnalizzatoreDocumenti loaded = (AnalizzatoreDocumenti) ois.readObject();
            LOGGER.info("Analisi caricata da: " + inputPath
                + " (" + loaded.getTermFrequency().size() + " parole)");
            return loaded;
        }
    }
}
