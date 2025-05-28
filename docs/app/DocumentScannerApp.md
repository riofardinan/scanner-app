# DocumentScannerApp.java

## Alur Kerja
1. Aplikasi dimulai dari method `main()`
2. JavaFX runtime memanggil method `start()`
3. OpenCV library dimuat melalui static block
4. Tampilan utama dimuat dari file FXML
5. Window aplikasi ditampilkan ke pengguna

## Struktur Kode

### Class
```java
public class DocumentScannerApp extends Application
```
Kelas DocumentScannerApp  mewarisi dari Application JavaFX menandakan bahwa ini adalah aplikasi JavaFX

### Static Block
```java
static {
    // Load OpenCV native library
    Loader.load(opencv_java.class);
}
```
Static block ini dijalankan saat kelas pertama kali dimuat. Memuat library native OpenCV yang diperlukan untuk pemrosesan gambar

### Method Start
```java
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
```
Method ini dipanggil saat aplikasi dimulai yang memuat file FXML. Untuk penjelasan stage, scene, parent, node, root bisa baca di https://fxdocs.github.io/docs/html5/ 

### Method Main
```java
public static void main(String[] args) {
    launch(args);
}
```
Memanggil `launch()` dari kelas Application JavaFX untuk memulai aplikasi