# ImageController.java

## Alur Kerja
1. Pengguna memilih gambar melalui handleUploadImage()
2. Gambar ditampilkan di originalImageView
3. Pengguna memilih filter dan memulai pemindaian melalui handleScan()
4. Hasil pemindaian ditampilkan di processedImageView
5. Pengguna dapat menyimpan hasil melalui handleSave()

## Struktur Kode

### FXML Injections
```java
@FXML private ImageView originalImageView;
@FXML private ImageView processedImageView;
@FXML private ComboBox<String> filterComboBox;
@FXML private ComboBox<String> exportFormatComboBox;
@FXML private ProgressBar progressBar;
@FXML private Label statusLabel;
```
Mengambil komponen ui dari file FXML menggunakan anotasi `@FXML`. Komponen dipanggil menggunakan id (mirip javascript, element diget menggunakan id)

### Inisialisasi
```java
@FXML
public void initialize() {
    scannerProcessor = new ScannerProcessor();
    
    // Initialize filter ComboBox
    filterComboBox.getItems().addAll("Asli", "Hitam Putih", "Ditingkatkan");
    filterComboBox.setValue("Asli");
    
    // Initialize export format ComboBox
    exportFormatComboBox.getItems().addAll("PNG", "JPEG", "PDF");
    exportFormatComboBox.setValue("PNG");
}
```
Method yang dipanggil setelah FXML dimuat. Menginisialisasi processor dan label dari combobox/dropdown button.

### Event Handlers

#### handleUploadImage()
```java
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
```
- Mengambil aksi dari komponen menggunakan anotasi `@FXML`, nama method sama dengan onAction di fxml.
- Menangani event button 'pilih gambar'
- Menggunakan FileChooser untuk memilih file
- Memuat gambar secara asinkron menggunakan Task (Jadi task itu menjalankan tugas di background, agar app tidak freeze saat menjalankan tugas berat)

#### handleScan()
```java
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
```
- Menangani event button 'scan'
- Memproses gambar menggunakan ScannerProcessor
- Menampilkan hasil secara asinkron

#### handleSave()
```java
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
```
- Menangani event button 'simpan'
- Menggunakan FileChooser untuk memilih lokasi
- Menyimpan hasil secara asinkron

### Utility Methods

#### updateStatus()
```java
private void updateStatus(String message) {
    Platform.runLater(() -> statusLabel.setText(message));
}
```
- Memperbarui status label dengan pesan yang diberikan
- Menggunakan Platform.runLater untuk thread safety

#### showProgress()
```java
private void showProgress(boolean show) {
    Platform.runLater(() -> progressBar.setVisible(show));
}
```
- Mengontrol visibilitas progress bar
- Menggunakan Platform.runLater untuk thread safety

#### showAlert()
```java
private void showAlert(String title, String content) {
    Platform.runLater(() -> {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    });
}
```
- Menampilkan dialog alert dengan pesan yang diberikan
- Menggunakan Platform.runLater untuk thread safety