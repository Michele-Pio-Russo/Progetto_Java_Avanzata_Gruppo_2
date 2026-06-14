/**
 * @file ApplicazioneClient.java
 * 
 * @brief Questa è la classe principale del lato client del gioco "Indovina la parola"
 * basata su JavaFX 
 * 
 * Classe che carica la schermata di login e avvia la GUI.
 * Questo è il primo componente ad essere eseguito quando si lancia l'applicazione client 
 * 
 * @author Gruppo 2
 * 
 * @version 1.0.0
 */
package indovinaparola.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ApplicazioneClient extends Application {

    /**
     * @brief Avvia la GUI del client caricando il file FXML della schermata di login.
     *
     * Carica il file FXML della schermata di login, crea una scena con dimensioni
     * fisse e la visualizza nella finestra principale dell'applicazione.
     * La finestra non è ridimensionabile.
     * 
     * @param[in] primaryStage stage principale fornito dal framework JavaFX
     * @throws Exception in caso di errore nel caricamento FXML
     */
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fxml/login.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 460, 580);
        primaryStage.setTitle("IndovinaLaParola - Accedi");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    /**
     * @brief Punto di ingresso principale dell'applicazione client.
     *
     * @param[in] args argomenti da riga di comando (non utilizzati)
     */
    public static void main(String[] args) {
        launch(args);
    }
}
