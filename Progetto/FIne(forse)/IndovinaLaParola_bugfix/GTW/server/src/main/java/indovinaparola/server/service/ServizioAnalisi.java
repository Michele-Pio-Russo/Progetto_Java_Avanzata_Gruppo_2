package indovinaparola.server.service;

import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.io.File;
import java.util.List;

/**
 * JavaFX Service per l'analisi asincrona dei documenti.
 *
 * <p>Estende {@link Service} invece di {@link Task} perché {@code Service} è
 * <b>riusabile</b>: può essere resettato e riavviato ({@code restart()}) ogni
 * volta che l'amministratore seleziona nuovi documenti. Un {@code Task} invece
 * è one-shot e non può essere riavviato. (Modulo JavaFX corso JA26)</p>
 *
 * <p>Il metodo {@code createTask()} crea un nuovo {@link Task} a ogni avvio del
 * Service. Il Task viene eseguito in un thread di background, mentre i binding
 * su {@code progressProperty()} e {@code messageProperty()} aggiornano la GUI
 * sul JavaFX Application Thread in modo thread-safe.</p>
 */
public class ServizioAnalisi extends Service<AnalizzatoreDocumenti> {

    private List<File> files;

    /**
     * Imposta la lista dei file da analizzare.
     * Deve essere chiamato prima di {@link #start()} o {@link #restart()}.
     *
     * @param files lista di file .txt da analizzare
     */
    public void setFiles(List<File> files) {
        this.files = files;
    }

    /**
     * Crea il {@link Task} che esegue l'analisi in background.
     * Questo metodo viene invocato automaticamente dal framework JavaFX
     * ogni volta che il Service viene avviato o resettato.
     *
     * @return Task che restituisce un {@link AnalizzatoreDocumenti} con i risultati
     */
    @Override
    protected Task<AnalizzatoreDocumenti> createTask() {
        // Copia locale per la lambda del Task (effectively final)
        final List<File> filesToProcess = files;

        return new Task<AnalizzatoreDocumenti>() {
            @Override
            protected AnalizzatoreDocumenti call() throws Exception {
                // updateMessage e updateProgress sono thread-safe:
                // aggiornano automaticamente le property sul FX thread
                updateMessage("Avvio analisi di " + filesToProcess.size() + " file...");
                updateProgress(0, filesToProcess.size());

                for (int i = 0; i < filesToProcess.size(); i++) {
                    updateMessage("Lettura: " + filesToProcess.get(i).getName());
                    updateProgress(i, filesToProcess.size());
                    // Piccola pausa per rendere visibile il progresso
                    Thread.sleep(50);
                }

                updateMessage("Calcolo Term Frequency...");
                AnalizzatoreDocumenti analizzatore = new AnalizzatoreDocumenti();
                analizzatore.analyze(filesToProcess);

                updateProgress(filesToProcess.size(), filesToProcess.size());
                updateMessage("Analisi completata: "
                    + analizzatore.getTermFrequency().size() + " parole uniche.");

                return analizzatore;
            }
        };
    }
}
