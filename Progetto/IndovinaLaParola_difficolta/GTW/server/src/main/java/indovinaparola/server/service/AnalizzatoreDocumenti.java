package indovinaparola.server.service;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Analizzatore di documenti testuali.
 * Calcola la <b>Term Frequency (TF)</b> di ogni parola presente nei file analizzati,
 * usando esclusivamente la <b>Java Stream API</b> (Modulo 5 corso JA26).
 *
 * <p>Implementa {@link Serializable} per permettere il salvataggio e il
 * ricaricamento dei risultati dell'analisi senza dover rielaborare i documenti.</p>
 *
 * <p>La mappa TF viene esposta tramite {@link Collections#unmodifiableMap} come
 * oggetto immutabile — tecnica raccomandata dal Modulo 6 (Concurrency) per
 * condivisione sicura tra thread.</p>
 */
public class AnalizzatoreDocumenti implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(AnalizzatoreDocumenti.class.getName());

    /** Mappa parola (lowercase) → frequenza relativa (TF = count / totalWords). */
    private Map<String, Double> termFrequency = new HashMap<>();

    /** Testo aggregato di tutti i documenti analizzati. */
    private String aggregatedText = "";

    /**
     * Analizza una lista di file di testo e calcola la Term Frequency.
     * L'elaborazione usa esclusivamente la Java Stream API (Modulo 5).
     *
     * @param files lista di file .txt da analizzare (non null, non vuota)
     * @throws IOException in caso di errore di lettura di uno dei file
     */
    public void analyze(List<File> files) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (File f : files) {
            byte[] bytes = Files.readAllBytes(f.toPath());
            sb.append(new String(bytes, "UTF-8")).append(" ");
        }
        aggregatedText = sb.toString();

        String[] tokens = aggregatedText
            .toLowerCase()
            .replaceAll("[^a-zA-Z\\s]", " ")
            .split("\\s+");

        final long totalWords = Arrays.stream(tokens)
            .filter(t -> !t.isEmpty())
            .count();

        if (totalWords == 0) {
            LOGGER.warning("Nessuna parola trovata nei documenti selezionati.");
            return;
        }

        termFrequency = Arrays.stream(tokens)
            .filter(t -> !t.isEmpty() && t.length() > 2)
            .collect(Collectors.groupingBy(t -> t, Collectors.counting()))
            .entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> (double) e.getValue() / totalWords
            ));

        LOGGER.info("Analisi completata: " + termFrequency.size()
            + " parole uniche su " + totalWords + " totali.");
    }

    /**
     * Restituisce la mappa Term Frequency come vista non modificabile.
     *
     * @return mappa non modificabile parola → TF
     */
    public Map<String, Double> getTermFrequency() {
        return Collections.unmodifiableMap(termFrequency);
    }

    /**
     * Restituisce il testo aggregato di tutti i documenti analizzati.
     *
     * @return testo completo
     */
    public String getAggregatedText() {
        return aggregatedText;
    }

    /**
     * Indica se l'analisi è stata effettuata con successo.
     *
     * @return true se la mappa TF contiene almeno un elemento
     */
    public boolean hasResults() {
        return !termFrequency.isEmpty();
    }

    /**
     * Estrae un estratto casuale dal testo analizzato di circa {@code numWords} parole.
     *
     * @param numWords numero approssimativo di parole dell'estratto
     * @return estratto testuale, stringa vuota se il testo è vuoto
     */
    public String extractRandomExcerpt(int numWords) {
        if (aggregatedText.isEmpty()) return "";
        String[] allWords = aggregatedText.split("\\s+");
        if (allWords.length <= numWords) return aggregatedText;
        Random rnd = new Random();
        int start = rnd.nextInt(allWords.length - numWords);
        return Arrays.stream(allWords, start, start + numWords)
            .collect(Collectors.joining(" "));
    }

    /**
     * Seleziona una parola dall'estratto in base al livello di difficoltà.
     *
     * <p>I criteri per livello sono:</p>
     * <ul>
     *   <li><b>Livello 1 – Facile:</b> parole comuni (TF alta), lunghezza ≥ 4.
     *       Le parole più frequenti sono le più note, quindi più facili da indovinare.</li>
     *   <li><b>Livello 2 – Medio:</b> parole con TF bassa (rare), lunghezza ≥ 4.
     *       Meno note ma non lunghe, difficoltà intermedia.</li>
     *   <li><b>Livello 3 – Difficile:</b> parole con TF molto bassa (rarissime),
     *       lunghezza ≥ 7. Combina rarità e lunghezza per massimizzare la difficoltà.</li>
     * </ul>
     *
     * <p>In tutti i casi vengono pre-selezionati i 5 migliori candidati secondo il
     * criterio del livello e poi estratto uno a caso, per evitare determinismo.</p>
     *
     * <p>Usa la Stream API con {@link Comparator} lambda (Moduli 4 e 5).</p>
     *
     * @param excerpt    estratto da cui selezionare la parola
     * @param difficulty livello: 1=Facile, 2=Medio, 3=Difficile
     * @return parola selezionata, oppure null se nessuna parola idonea trovata
     */
    public String selectWordFromExcerpt(String excerpt, int difficulty) {
        String[] words = excerpt.toLowerCase()
            .replaceAll("[^a-z\\s]", "")
            .split("\\s+");

        // Lunghezza minima e ordinamento variano per livello (Modulo 4 – lambda)
        final int minLen;
        final Comparator<String> byTf;

        if (difficulty <= 1) {
            // Facile: parole corte-medie, preferire TF alta (comuni)
            minLen = 4;
            byTf = (a, b) -> Double.compare(
                termFrequency.getOrDefault(b, 0.0),
                termFrequency.getOrDefault(a, 0.0)); // decrescente
        } else if (difficulty == 2) {
            // Medio: parole medie, preferire TF bassa (rare)
            minLen = 4;
            byTf = (a, b) -> Double.compare(
                termFrequency.getOrDefault(a, 0.0),
                termFrequency.getOrDefault(b, 0.0)); // crescente
        } else {
            // Difficile: parole lunghe, preferire TF bassissima (rarissime e lunghe)
            minLen = 7;
            byTf = (a, b) -> {
                double tfA = termFrequency.getOrDefault(a, 0.0);
                double tfB = termFrequency.getOrDefault(b, 0.0);
                // Ordine primario: TF crescente (più rara = prima)
                // Ordine secondario: lunghezza decrescente (più lunga = prima)
                int cmp = Double.compare(tfA, tfB);
                if (cmp != 0) return cmp;
                return Integer.compare(b.length(), a.length());
            };
        }

        // Pipeline Stream: filter (lunghezza + presenza in TF) → sorted → limit → collect
        List<String> candidates = Arrays.stream(words)
            .filter(w -> w.length() >= minLen && termFrequency.containsKey(w))
            .sorted(byTf)
            .limit(5)
            .collect(Collectors.toList());

        if (candidates.isEmpty()) return null;

        return candidates.get(new Random().nextInt(candidates.size()));
    }

    /**
     * Salva l'istanza corrente su file in formato serializzato binario.
     *
     * @param outputPath percorso del file di output (es. "data/analysis.dat")
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
     * Carica un'istanza di {@link AnalizzatoreDocumenti} precedentemente serializzata.
     *
     * @param inputPath percorso del file da caricare
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
