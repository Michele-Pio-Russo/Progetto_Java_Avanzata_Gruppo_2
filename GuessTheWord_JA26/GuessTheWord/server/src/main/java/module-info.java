module guesstheword.server {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires guesstheword.common;

    opens guesstheword.server.controller to javafx.fxml;
    opens guesstheword.server to javafx.graphics;

    exports guesstheword.server;
    exports guesstheword.server.controller;
    exports guesstheword.server.model;
    exports guesstheword.server.service;
    exports guesstheword.server.db;
    exports guesstheword.server.util;
}
