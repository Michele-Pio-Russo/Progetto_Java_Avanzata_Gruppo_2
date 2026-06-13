/**
 * @file ServizioAnalisi.java
 * @brief Questo file contiene gli attributi, il costruttore e i metodi setter, getter e toString della classe ServizioAnalisi
 *
 * Questa classe permette di istanziare un oggetto ServizioAnalisi, i metodi setter e getter permettono di
 * ottenere e modificare informazioni relative agli attributi, inoltre il metodo toString permette di stampare 
 * le informazioni relative alla classe ServizioAnalisi.
 *
 * @author Gruppo 2
 * @date 
 * @version 1.0.0
 */
package indovinaparola.server.service;

import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.io.File;
import java.util.List;

/**
 * @brief JavaFX Service per l'analisi asincrona dei documenti.
 *
 * Estende {@link Service} invece di {@link Task} perché {@code Service} è
 * <b>riusabile</b>: può essere resettato e riavviato ({@code restart()}) ogni
 * @brief volta che l'amministratore seleziona nuovi documenti. Un {@code Task} invece
 * è one-shot e non può essere riavviato.
 *
 * Il metodo {@code createTask()} crea un nuovo {@link Task} a ogni avvio del
 * Service. Il Task viene eseguito in un thread di background, mentre i binding
 * su {@code progressProperty()} e {@code messageProperty()} aggiornano la GUI
 * sul JavaFX Application Thread in modo thread-safe.
 */
public class ServizioAnalisi extends Service<AnalizzatoreDocumenti> {

    private List<File> files;

    /**
     * @brief Imposta la lista dei file da analizzare.
     * @brief Deve essere chiamato prima di {@link #start()} o {@link #restart()}.
     *
     * @param[in] files lista di file .txt da analizzare
     */
    public void setFiles(List<File> files) {
        this.files = files;
    }

    /**
     * @brief Crea il {@link Task} che esegue l'analisi in background.
     * @brief Questo metodo viene invocato automaticamente dal framework JavaFX
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
