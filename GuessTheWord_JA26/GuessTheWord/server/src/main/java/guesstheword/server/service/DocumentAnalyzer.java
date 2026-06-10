package guesstheword.server.service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Analizzatore di documenti testuali.
 * Calcola la Term Frequency (TF) usando Java Stream API.
 * Supporta salvataggio/ricaricamento serializzato dei risultati.
 */
public class DocumentAnalyzer implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(DocumentAnalyzer.class.getName());

    /** Mappa parola → frequenza relativa (TF). */
    private Map<String, Double> termFrequency = new HashMap<>();

    /** Testo sorgente aggregato dai documenti analizzati. */
    private String aggregatedText = "";

    /**
     * Analizza uno o più file TXT e calcola la Term Frequency.
     * Usa Java Stream API per l'elaborazione.
     *
     * @param files lista di file da analizzare
     * @throws IOException in caso di errore di lettura
     */
    public void analyze(List<File> files) throws IOException {
        // Legge e concatena tutto il testo
        StringBuilder sb = new StringBuilder();
        for (File f : files) {
            String content = new String(Files.readAllBytes(f.toPath()));
            sb.append(content).append(" ");
        }
        aggregatedText = sb.toString();

        // Stream API: tokenizza, normalizza, conta, calcola TF
        String[] tokens = aggregatedText
            .toLowerCase()
            .replaceAll("[^a-zA-Zàèéìòùáéíóú\\s]", " ")
            .split("\\s+");

        long totalWords = Arrays.stream(tokens)
            .filter(t -> !t.isEmpty())
            .count();

        termFrequency = Arrays.stream(tokens)
            .filter(t -> !t.isEmpty() && t.length() > 2)
            .collect(Collectors.groupingBy(t -> t, Collectors.counting()))
            .entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> (double) e.getValue() / totalWords
            ));

        LOGGER.info("Analisi completata: " + termFrequency.size() + " parole uniche su " + totalWords + " totali.");
    }

    /**
     * Restituisce la mappa parola → frequenza relativa.
     *
     * @return mappa TF
     */
    public Map<String, Double> getTermFrequency() {
        return Collections.unmodifiableMap(termFrequency);
    }

    /**
     * Restituisce il testo aggregato.
     *
     * @return testo completo analizzato
     */
    public String getAggregatedText() {
        return aggregatedText;
    }

    /**
     * Indica se l'analisi è stata effettuata (la mappa non è vuota).
     *
     * @return true se ci sono risultati
     */
    public boolean hasResults() {
        return !termFrequency.isEmpty();
    }

    /**
     * Salva i risultati dell'analisi in formato serializzato.
     *
     * @param outputPath percorso file di output
     * @throws IOException in caso di errore di scrittura
     */
    public void saveToFile(String outputPath) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(outputPath))) {
            oos.writeObject(this);
        }
        LOGGER.info("Risultati analisi salvati in: " + outputPath);
    }

    /**
     * Carica un DocumentAnalyzer serializzato da file.
     *
     * @param inputPath percorso file da caricare
     * @return istanza caricata
     * @throws IOException            in caso di errore di lettura
     * @throws ClassNotFoundException se la classe non è trovata
     */
    public static DocumentAnalyzer loadFromFile(String inputPath)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(inputPath))) {
            DocumentAnalyzer loaded = (DocumentAnalyzer) ois.readObject();
            LOGGER.info("Risultati analisi caricati da: " + inputPath);
            return loaded;
        }
    }

    /**
     * Estrae un estratto casuale dal testo analizzato di circa {@code words} parole.
     *
     * @param words numero approssimativo di parole dell'estratto
     * @return estratto testuale
     */
    public String extractRandomExcerpt(int words) {
        if (aggregatedText.isEmpty()) return "";
        String[] allWords = aggregatedText.split("\\s+");
        if (allWords.length <= words) return aggregatedText;
        Random rnd = new Random();
        int start = rnd.nextInt(allWords.length - words);
        return String.join(" ", Arrays.copyOfRange(allWords, start, start + words));
    }

    /**
     * Seleziona una parola dall'estratto in base alla difficoltà.
     * Con difficoltà alta vengono preferite parole più rare (TF bassa).
     *
     * @param excerpt    estratto da cui selezionare
     * @param difficulty livello 1=facile, 2=medio, 3=difficile
     * @return parola selezionata o null se non trovata
     */
    public String selectWordFromExcerpt(String excerpt, int difficulty) {
        String[] words = excerpt.toLowerCase()
            .replaceAll("[^a-zA-Zàèéìòùáéíóú\\s]", "")
            .split("\\s+");

        List<String> candidates = Arrays.stream(words)
            .filter(w -> w.length() >= 4 && termFrequency.containsKey(w))
            .collect(Collectors.toList());

        if (candidates.isEmpty()) return null;

        // Ordina per TF: difficoltà alta → parole più rare
        candidates.sort((a, b) -> {
            double tfA = termFrequency.getOrDefault(a, 0.0);
            double tfB = termFrequency.getOrDefault(b, 0.0);
            return difficulty >= 2 ? Double.compare(tfA, tfB) : Double.compare(tfB, tfA);
        });

        // Scegli casualmente tra le prime 5 candidate
        int range = Math.min(5, candidates.size());
        Random rnd = new Random();
        return candidates.get(rnd.nextInt(range));
    }
}
