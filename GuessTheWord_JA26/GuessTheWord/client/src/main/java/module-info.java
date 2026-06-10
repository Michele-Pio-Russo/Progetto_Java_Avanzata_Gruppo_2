module guesstheword.client {
    requires javafx.controls;
    requires javafx.fxml;
    requires guesstheword.common;

    opens guesstheword.client.controller to javafx.fxml;
    opens guesstheword.client to javafx.graphics;

    exports guesstheword.client;
    exports guesstheword.client.controller;
    exports guesstheword.client.service;
    exports guesstheword.client.util;
}
