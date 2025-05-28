package com.scanner.app;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class DocumentScannerApp extends Application {

    static {
        // Load OpenCV native library
        Loader.load(opencv_java.class);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_view.fxml"));
            Parent root = loader.load();
            
            primaryStage.setTitle("Document Scanner");
            primaryStage.setScene(new Scene(root));
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
} 