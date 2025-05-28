package com.scanner.controller;

import java.io.File;

import com.scanner.processor.ScannerProcessor;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

public class ImageController {
    @FXML private ImageView originalImageView;
    @FXML private ImageView processedImageView;
    @FXML private ComboBox<String> filterComboBox;
    @FXML private ComboBox<String> exportFormatComboBox;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;

    private ScannerProcessor scannerProcessor;
    private int scanCounter = 1;

    @FXML
    public void initialize() {
        scannerProcessor = new ScannerProcessor();

        filterComboBox.getItems().addAll("Asli", "Hitam Putih", "Ditingkatkan");
        filterComboBox.setValue("Asli");
        
        exportFormatComboBox.getItems().addAll("PNG", "JPEG", "PDF");
        exportFormatComboBox.setValue("PNG");
    }

    @FXML
    private void handleUploadImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );

        File selectedFile = fileChooser.showOpenDialog(originalImageView.getScene().getWindow());
        if (selectedFile != null) {
            updateStatus("Memuat gambar...");
            showProgress(true);
            
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    Image image = new Image(selectedFile.toURI().toString());
                    Platform.runLater(() -> {
                        originalImageView.setImage(image);
                        updateStatus("Gambar berhasil dimuat");
                        showProgress(false);
                    });
                    return null;
                }
            };
            
            new Thread(task).start();
        }
    }

    @FXML
    private void handleScan() {
        if (originalImageView.getImage() == null) {
            showAlert("Peringatan", "Pilih gambar terlebih dahulu");
            return;
        }
        
        updateStatus("Memproses gambar...");
        showProgress(true);
        
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    String selectedFilter = filterComboBox.getValue();
                    Image processedImage = scannerProcessor.processImage(originalImageView.getImage(), selectedFilter);
                    
                    Platform.runLater(() -> {
                        processedImageView.setImage(processedImage);
                        updateStatus("Pemindaian selesai");
                        showProgress(false);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        showAlert("Deteksi Gagal", e.getMessage());
                        updateStatus("Deteksi tepi/koreksi perspektif gagal");
                        showProgress(false);
                    });
                }
                return null;
            }
        };
        
        new Thread(task).start();
    }

    @FXML
    private void handleSave() {
        if (processedImageView.getImage() == null) {
            showAlert("Peringatan", "Tidak ada hasil pemindaian untuk disimpan");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        String format = exportFormatComboBox.getValue().toLowerCase();
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(
                format.toUpperCase() + " Files", 
                "*." + format
            )
        );
        
        fileChooser.setInitialFileName("scanned_doc_" + scanCounter + "." + format);
        
        File selectedFile = fileChooser.showSaveDialog(processedImageView.getScene().getWindow());
        if (selectedFile != null) {
            updateStatus("Menyimpan hasil...");
            showProgress(true);
            
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    scannerProcessor.saveImage(processedImageView.getImage(), selectedFile, format);
                    
                    Platform.runLater(() -> {
                        updateStatus("Hasil berhasil disimpan");
                        showProgress(false);
                        scanCounter++;
                    });
                    return null;
                }
            };
            
            new Thread(task).start();
        }
    }

    private void updateStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    private void showProgress(boolean show) {
        Platform.runLater(() -> progressBar.setVisible(show));
    }

    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
} 