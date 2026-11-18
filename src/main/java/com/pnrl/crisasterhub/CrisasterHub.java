package com.pnrl.crisasterhub;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CrisasterHub extends Application {
    private static final Logger LOGGER = Logger.getLogger(CrisasterHub.class.getName());

    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/login.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);

            scene.setFill(Color.TRANSPARENT);

            primaryStage.setTitle("CrisasterHub");
            primaryStage.setFullScreenExitHint("");
            primaryStage.setScene(scene);
            primaryStage.setResizable(true);
            primaryStage.show();
            primaryStage.setFullScreen(true);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Application startup failed", e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
