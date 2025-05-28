# ScannerProcessor.java

## Alur Kerja
1. Konversi input JavaFX Image ke OpenCV Mat
2. Edge Detection (EdgeDetector)
3. Perspective Correction (PerspectiveCorrector)
4. Image Filtering (ImageFilter)
5. Konversi hasil kembali ke JavaFX Image
6. Pembersihan memori untuk objek OpenCV

## Struktur Kode

### Komponen Utama
```java
private final EdgeDetector edgeDetector;
private final PerspectiveCorrector perspectiveCorrector;
private final ImageFilter imageFilter;
private final ExportHandler exportHandler;
```
- Komponen-komponen yang diintegrasikan untuk pemrosesan gambar
- Menggunakan `final` agar komponen tidak bisa diubah setelah inisialisasi

### Constructor
```java
public ScannerProcessor() {
    this.edgeDetector = new EdgeDetector();
    this.perspectiveCorrector = new PerspectiveCorrector();
    this.imageFilter = new ImageFilter();
    this.exportHandler = new ExportHandler();
}
```
- Menginisialisasi semua komponen yang dibutuhkan
- Setiap komponen dibuat sekali dan digunakan berulang kali

### Method Utama

#### processImage()
```java
public Image processImage(Image inputImage, String filterType) {
    System.out.println("Processing image dengan filter: " + filterType);
    
    // Convert JavaFX Image to OpenCV Mat
    Mat originalMat = imageToMat(inputImage);
    if (originalMat == null || originalMat.empty()) {
        throw new RuntimeException("Gagal mengkonversi input image ke Mat");
    }
    
    // Debug save input
    imwrite("debug/scanner/01_original_input.png", originalMat);
    
    // Edge detection untuk mencari kontur dokumen
    Mat matForEdgeDetection = originalMat.clone();
    Mat edges = edgeDetector.detectEdges(matForEdgeDetection);
    imwrite("debug/scanner/02_edges.png", edges);
    
    // Detect 4 corner points
    Point[] corners = edgeDetector.detectDocumentCorners(originalMat);
    matForEdgeDetection.release();
    
    // Perspective correction
    Mat corrected = perspectiveCorrector.correctPerspective(originalMat, corners);
    if (corrected == null) {
        originalMat.release();
        edges.release();
        throw new RuntimeException("Deteksi tepi dokumen gagal. Pastikan gambar jelas dan dokumen terlihat penuh.");
    }
    imwrite("debug/scanner/03_corrected.png", corrected);
    
    // Apply filter
    Mat filtered = imageFilter.applyFilter(corrected, filterType);
    if (filtered == null || filtered.empty()) {
        originalMat.release();
        corrected.release();
        edges.release();
        throw new RuntimeException("Filter gagal diterapkan");
    }
    imwrite("debug/scanner/04_filtered.png", filtered);
    
    // Convert back to JavaFX Image
    Image resultImage = matToImage(filtered);
    if (resultImage == null) {
        originalMat.release();
        corrected.release();
        filtered.release();
        edges.release();
        throw new RuntimeException("Gagal mengkonversi hasil ke Image");
    }
    
    // Cleanup
    originalMat.release();
    corrected.release();
    filtered.release();
    edges.release();
    
    System.out.println("Image processing completed successfully");
    return resultImage;
}
```
- Mengkoordinasikan seluruh alur pemrosesan
- Menangani konversi format dan memory management
- Menyimpan debug images di setiap tahap
- Membersihkan memori setelah selesai

#### saveImage()
```java
public void saveImage(Image image, File file, String format) {
    exportHandler.saveImage(image, file, format);
}
```
- Menangani export hasil pemindaian
- Mendelegasikan ke ExportHandler
- Mendukung berbagai format (PNG, JPEG, PDF)

### Method Support

#### imageToMat()
```java
private Mat imageToMat(Image image) {
    try {
        BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
        if (bufferedImage == null) {
            System.err.println("Gagal mengkonversi Image ke BufferedImage");
            return null;
        }
        
        // Ensure BufferedImage is in correct RGB format
        BufferedImage rgbImage;
        if (bufferedImage.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            rgbImage = new BufferedImage(bufferedImage.getWidth(), bufferedImage.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            java.awt.Graphics2D g2d = rgbImage.createGraphics();
            g2d.drawImage(bufferedImage, 0, 0, null);
            g2d.dispose();
        } else {
            rgbImage = bufferedImage;
        }
        
        Java2DFrameConverter java2DConverter = new Java2DFrameConverter();
        Frame frame = java2DConverter.convert(rgbImage);
        if (frame == null) {
            System.err.println("Gagal mengkonversi BufferedImage ke Frame");
            return null;
        }
        
        OpenCVFrameConverter.ToMat matConverter = new OpenCVFrameConverter.ToMat();
        Mat mat = matConverter.convert(frame);
        
        if (mat != null && !mat.empty()) {
            // Ensure BGR 3-channel format (OpenCV default)
            if (mat.channels() == 4) {
                Mat bgr = new Mat();
                cvtColor(mat, bgr, COLOR_BGRA2BGR);
                mat.release();
                return bgr;
            } else if (mat.channels() == 1) {
                Mat bgr = new Mat();
                cvtColor(mat, bgr, COLOR_GRAY2BGR);
                mat.release();
                return bgr;
            }
            return mat;
        }
        
        return null;
    } catch (Exception e) {
        System.err.println("Error dalam imageToMat: " + e.getMessage());
        e.printStackTrace();
        return null;
    }
}
```
- Mengkonversi JavaFX Image ke OpenCV Mat
- Menangani berbagai format input
- Memastikan format output yang benar (BGR)
- Membersihkan memori untuk objek sementara

#### matToImage()
```java
private Image matToImage(Mat mat) {
    if (mat == null || mat.empty()) {
        System.err.println("Mat kosong atau null dalam matToImage");
        return null;
    }
    
    try {
        Mat displayMat = mat;
        
        // Convert BGR to RGB for correct display (OpenCV uses BGR, JavaFX uses RGB)
        if (mat.channels() == 3) {
            displayMat = new Mat();
            cvtColor(mat, displayMat, COLOR_BGR2RGB);
        } else if (mat.channels() == 1) {
            displayMat = new Mat();
            cvtColor(mat, displayMat, COLOR_GRAY2RGB);
        } else if (mat.channels() == 4) {
            displayMat = new Mat();
            cvtColor(mat, displayMat, COLOR_BGRA2RGB);
        } else {
            System.err.println("Format Mat tidak didukung: " + mat.channels() + " channels");
            return null;
        }
        
        OpenCVFrameConverter.ToMat matConverter = new OpenCVFrameConverter.ToMat();
        Frame frame = matConverter.convert(displayMat);
        if (frame == null) {
            System.err.println("Gagal mengkonversi Mat ke Frame");
            if (displayMat != mat) displayMat.release();
            return null;
        }
        
        Java2DFrameConverter java2DConverter = new Java2DFrameConverter();
        BufferedImage bufferedImage = java2DConverter.convert(frame);
        if (bufferedImage == null) {
            System.err.println("Gagal mengkonversi Frame ke BufferedImage");
            if (displayMat != mat) displayMat.release();
            return null;
        }
        
        // Convert to JavaFX compatible format
        BufferedImage compatibleImage = new BufferedImage(
            bufferedImage.getWidth(), 
            bufferedImage.getHeight(), 
            BufferedImage.TYPE_INT_RGB
        );
        java.awt.Graphics2D g2d = compatibleImage.createGraphics();
        g2d.drawImage(bufferedImage, 0, 0, null);
        g2d.dispose();
        
        Image resultImage = SwingFXUtils.toFXImage(compatibleImage, null);
        
        // Cleanup if we created new Mat
        if (displayMat != mat) {
            displayMat.release();
        }
        
        return resultImage;
        
    } catch (Exception e) {
        System.err.println("Error dalam matToImage: " + e.getMessage());
        e.printStackTrace();
        return null;
    }
}
```
- Mengkonversi OpenCV Mat ke JavaFX Image
- Menangani konversi warna (BGR ke RGB)
- Memastikan format output yang kompatibel
- Membersihkan memori untuk objek sementara