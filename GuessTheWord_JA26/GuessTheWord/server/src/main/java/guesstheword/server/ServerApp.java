package guesstheword.server;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Punto di ingresso dell'applicazione JavaFX lato server.
 * Carica il layout FXML e mostra la finestra di amministrazione.
 */
public class ServerApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fxml/server.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1100, 720);
        primaryStage.setTitle("GuessTheWord — Server Amministratore");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }

    /**
     * Main method — entry point per l'esecuzione del server JAR.
     *
     * @param args argomenti da riga di comando (non utilizzati)
     */
    public static void main(String[] args) {
        launch(args);
    }
}
