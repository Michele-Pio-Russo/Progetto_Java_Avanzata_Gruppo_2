package guesstheword.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Punto di ingresso dell'applicazione JavaFX lato client.
 * Carica la schermata di login ({@code login.fxml}) e avvia la GUI.
 */
public class ClientApp extends Application {

    /**
     * Avvia la GUI del client caricando il file FXML della schermata di login.
     *
     * @param primaryStage stage principale fornito dal framework JavaFX
     * @throws Exception in caso di errore nel caricamento FXML
     */
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fxml/login.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 460, 580);
        primaryStage.setTitle("GuessTheWord - Accedi");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    /**
     * Punto di ingresso principale dell'applicazione client.
     *
     * @param args argomenti da riga di comando (non utilizzati)
     */
    public static void main(String[] args) {
        launch(args);
    }
}
