package com.scanner.export;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

public class ExportHandler {
    
    public void saveImage(Image image, File file, String format) {
        if (image == null || file == null || format == null) {
            System.err.println("Parameter tidak valid untuk export");
            return;
        }
        
        try {
            BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
            
            switch (format.toUpperCase()) {
                case "PDF":
                    saveAsPDF(bufferedImage, file);
                    break;
                case "PNG":
                case "JPG":
                case "JPEG":
                    ImageIO.write(bufferedImage, format.toLowerCase(), file);
                    break;
                default:
                    System.err.println("Format tidak didukung: " + format);
                    break;
            }
            
            System.out.println("File berhasil disimpan: " + file.getAbsolutePath());
            
        } catch (IOException e) {
            System.err.println("Error saat menyimpan file: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void saveAsPDF(BufferedImage image, File file) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            
            var pdImage = LosslessFactory.createFromImage(document, image);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                float pageWidth = page.getMediaBox().getWidth();
                float pageHeight = page.getMediaBox().getHeight();
                
                // Scale image to fit page while maintaining aspect ratio
                float imageWidth = image.getWidth();
                float imageHeight = image.getHeight();
                float scale = Math.min(pageWidth / imageWidth, pageHeight / imageHeight);
                
                float scaledWidth = imageWidth * scale;
                float scaledHeight = imageHeight * scale;
                
                // Center image on page
                float x = (pageWidth - scaledWidth) / 2;
                float y = (pageHeight - scaledHeight) / 2;
                
                contentStream.drawImage(pdImage, x, y, scaledWidth, scaledHeight);
            }
            
            document.save(file);
        }
    }
} 