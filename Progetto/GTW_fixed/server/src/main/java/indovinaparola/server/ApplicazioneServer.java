/**
 * @file ApplicazioneServer.java
 * @brief Questo file contiene gli attributi, il costruttore e i metodi setter, getter e toString della classe ApplicazioneServer
 *
 * Questa classe permette di istanziare un oggetto ApplicazioneServer, i metodi setter e getter permettono di
 * ottenere e modificare informazioni relative agli attributi, inoltre il metodo toString permette di stampare 
 * le informazioni relative alla classe ApplicazioneServer.
 *
 * @author Gruppo 2
 * @date 
 * @version 1.0.0
 */
package indovinaparola.server;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * @brief Punto di ingresso dell'applicazione JavaFX lato server.
 * @brief Carica il layout FXML del pannello di amministrazione e
 * mostra la finestra principale del server.
 */
public class ApplicazioneServer extends Application {

    /**
     * @brief Avvia la GUI del server caricando il file FXML principale.
     *
     * @pre Il file FXML /fxml/server.fxml deve essere presente nelle risorse.
     * @post L'interfaccia grafica del server viene mostrata a schermo.
     *
     * @param[in] primaryStage stage principale fornito dal framework JavaFX
     * @throws Exception in caso di errore nel caricamento FXML
     */
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fxml/server.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1100, 720);
        primaryStage.setTitle("IndovinaLaParola - Server Amministratore");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }

    /**
     * @brief Punto di ingresso principale dell'applicazione server.
     *
     * @pre L'ambiente JavaFX deve essere correttamente configurato.
     * @post L'applicazione viene lanciata in esecuzione.
     *
     * @param[in] args argomenti da riga di comando (non utilizzati)
     */
    public static void main(String[] args) {
        launch(args);
    }
}
