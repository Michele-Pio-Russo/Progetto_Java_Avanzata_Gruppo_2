package guesstheword.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Punto di ingresso dell'applicazione JavaFX lato client.
 * Carica la schermata di login e avvia la connessione con il server.
 */
public class ClientApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fxml/login.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 480, 600);
        primaryStage.setTitle("GuessTheWord — Accedi");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    /**
     * Main method — entry point per l'esecuzione del client JAR.
     *
     * @param args argomenti da riga di comando (non utilizzati)
     */
    public static void main(String[] args) {
        launch(args);
    }
}
