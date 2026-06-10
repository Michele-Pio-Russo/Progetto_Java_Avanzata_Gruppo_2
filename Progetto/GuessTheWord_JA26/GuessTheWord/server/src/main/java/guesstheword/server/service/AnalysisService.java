package guesstheword.server.service;

import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.io.File;
import java.util.List;

/**
 * JavaFX Service per l'analisi asincrona dei documenti.
 * Mantiene l'interfaccia grafica del server reattiva durante l'elaborazione.
 */
public class AnalysisService extends Service<DocumentAnalyzer> {

    private List<File> filesToAnalyze;

    /**
     * Imposta i file da analizzare.
     *
     * @param files lista di file TXT da analizzare
     */
    public void setFiles(List<File> files) {
        this.filesToAnalyze = files;
    }

    /**
     * Crea il Task che esegue l'analisi in background.
     *
     * @return Task che restituisce un DocumentAnalyzer con i risultati
     */
    @Override
    protected Task<DocumentAnalyzer> createTask() {
        final List<File> files = filesToAnalyze;
        return new Task<DocumentAnalyzer>() {
            @Override
            protected DocumentAnalyzer call() throws Exception {
                updateMessage("Avvio analisi...");
                DocumentAnalyzer analyzer = new DocumentAnalyzer();
                updateProgress(0, files.size());

                for (int i = 0; i < files.size(); i++) {
                    updateMessage("Analisi file: " + files.get(i).getName());
                    updateProgress(i + 1, files.size());
                }

                analyzer.analyze(files);
                updateMessage("Analisi completata: " +
                    analyzer.getTermFrequency().size() + " parole trovate.");
                updateProgress(files.size(), files.size());
                return analyzer;
            }
        };
    }
}
