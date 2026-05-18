package com.bruno.grpc.view;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Launcher extends Application {

    private Controller controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Launcher.class.getResource("/gui/main.fxml"));
        Scene scene = new Scene(fxmlLoader.load());

        controller = fxmlLoader.getController();

        stage.setTitle("Jogo de Adivinhação");
        stage.setScene(scene);

        // Encerra as conexões gRPC ao fechar a janela
        stage.setOnCloseRequest(e -> {
            if (controller != null) controller.shutdown();
        });

        stage.show();
    }

    @Override
    public void stop() {
        if (controller != null) controller.shutdown();
    }

    public static void main(String[] args) {
        Application.launch(Launcher.class, args);
    }

}
