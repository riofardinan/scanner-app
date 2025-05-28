# ExportHandler.java

## Struktur Kode

### Package dan Import
```java
package com.scanner.export;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
```
- Package `com.scanner.export` mendefinisikan namespace untuk kelas export
- Import mencakup library Java I/O, PDFBox, dan JavaFX

### Metode Utama

#### saveImage()
```java
public boolean saveImage(Image image, String filePath) {
    if (image == null || filePath == null || filePath.isEmpty()) {
        System.err.println("Invalid input parameters");
        return false;
    }

    try {
        // Convert JavaFX Image to BufferedImage
        BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
        if (bufferedImage == null) {
            System.err.println("Failed to convert JavaFX Image to BufferedImage");
            return false;
        }

        // Get file extension
        String extension = filePath.substring(filePath.lastIndexOf(".") + 1).toLowerCase();
        
        // Save based on format
        switch (extension) {
            case "pdf":
                return saveAsPDF(bufferedImage, filePath);
                
            case "png":
                return ImageIO.write(bufferedImage, "PNG", new File(filePath));
                
            case "jpg":
            case "jpeg":
                return ImageIO.write(bufferedImage, "JPEG", new File(filePath));
                
            default:
                System.err.println("Unsupported file format: " + extension);
                return false;
        }
        
    } catch (Exception e) {
        System.err.println("Error saving image: " + e.getMessage());
        return false;
    }
}
```
- Method utama untuk menyimpan gambar
- Mendukung format PDF, PNG, dan JPEG
- Konversi dari JavaFX Image ke BufferedImage
- Penanganan error untuk setiap format

### Method Support

#### saveAsPDF()
```java
private boolean saveAsPDF(BufferedImage image, String filePath) {
    try {
        // Create new PDF document
        PDDocument document = new PDDocument();
        PDPage page = new PDPage();
        document.addPage(page);
        
        // Get page dimensions
        PDRectangle pageSize = page.getMediaBox();
        float pageWidth = pageSize.getWidth();
        float pageHeight = pageSize.getHeight();
        
        // Calculate image dimensions to maintain aspect ratio
        float imageWidth = image.getWidth();
        float imageHeight = image.getHeight();
        float scale = Math.min(pageWidth / imageWidth, pageHeight / imageHeight);
        
        // Calculate position to center image
        float x = (pageWidth - imageWidth * scale) / 2;
        float y = (pageHeight - imageHeight * scale) / 2;
        
        // Create content stream
        PDPageContentStream contentStream = new PDPageContentStream(document, page);
        
        // Draw image
        PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
        contentStream.drawImage(pdImage, x, y, imageWidth * scale, imageHeight * scale);
        
        // Close content stream
        contentStream.close();
        
        // Save document
        document.save(filePath);
        document.close();
        
        return true;
        
    } catch (Exception e) {
        System.err.println("Error saving PDF: " + e.getMessage());
        return false;
    }
}
```
- Menyimpan gambar sebagai PDF
- Menjaga aspect ratio gambar
- Posisi gambar di tengah halaman
- Penanganan error PDF

## Alur Kerja
1. Validasi parameter input
2. Konversi JavaFX Image ke BufferedImage
3. Deteksi format file dari ekstensi
4. Untuk PDF:
   - Buat dokumen baru
   - Hitung dimensi dan posisi
   - Gambar image ke halaman
   - Simpan dokumen
5. Untuk PNG/JPEG:
   - Langsung simpan menggunakan ImageIO

