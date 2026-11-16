package com.pnrl.crisasterhub;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class CrisasterHub extends Application {

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
            e.printStackTrace();
            System.err.println("An error occured while FXML loading.");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
