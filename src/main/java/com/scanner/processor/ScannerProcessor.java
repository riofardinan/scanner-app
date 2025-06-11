package com.scanner.processor;

import java.awt.image.BufferedImage;
import java.io.File;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2RGB;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGRA2BGR;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGRA2RGB;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_GRAY2BGR;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_GRAY2RGB;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;

import com.scanner.export.ExportHandler;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

public class ScannerProcessor {

    private final EdgeDetector edgeDetector;
    private final PerspectiveCorrector perspectiveCorrector;
    private final ImageFilter imageFilter;
    private final ExportHandler exportHandler;

    public ScannerProcessor() {
        this.edgeDetector = new EdgeDetector();
        this.perspectiveCorrector = new PerspectiveCorrector();
        this.imageFilter = new ImageFilter();
        this.exportHandler = new ExportHandler();
    }

    public Image processImage(Image inputImage, String filterType) {
        System.out.println("Processing image dengan filter: " + filterType);

        // Convert JavaFX Image to OpenCV Mat
        Mat originalMat = imageToMat(inputImage);
        if (originalMat == null || originalMat.empty()) {
            throw new RuntimeException("Gagal mengkonversi input image ke Mat");
        }

        imwrite("debug/scanner/01_original_input.png", originalMat);

        // Debug: Edge detection
        Mat debugEdgeDetection = originalMat.clone();
        Mat edges = edgeDetector.detectEdges(debugEdgeDetection);
        imwrite("debug/scanner/02_edges.png", edges);
        edges.release();
        debugEdgeDetection.release();

        // Detect 4 corner points
        Point[] corners = edgeDetector.detectDocumentCorners(originalMat);
        // Perspective correction
        Mat corrected = perspectiveCorrector.correctPerspective(originalMat, corners);
        if (corrected == null) {
            originalMat.release();
            throw new RuntimeException("Deteksi tepi dokumen gagal. Pastikan gambar jelas dan dokumen terlihat penuh.");
        }
        imwrite("debug/scanner/03_corrected.png", corrected);

        // Apply filter
        Mat filtered = imageFilter.applyFilter(corrected, filterType);
        if (filtered == null || filtered.empty()) {
            originalMat.release();
            corrected.release();
            throw new RuntimeException("Filter gagal diterapkan");
        }
        imwrite("debug/scanner/04_filtered.png", filtered);

        // Convert back to JavaFX Image
        Image resultImage = matToImage(filtered);
        if (resultImage == null) {
            originalMat.release();
            corrected.release();
            filtered.release();
            throw new RuntimeException("Gagal mengkonversi hasil ke Image");
        }

        // Cleanup memory
        originalMat.release();
        corrected.release();
        filtered.release();

        System.out.println("Image processing completed successfully");
        return resultImage;
    }

    public void saveImage(Image image, File file, String format) {
        exportHandler.saveImage(image, file, format);
    }

    private Mat imageToMat(Image image) {
        try {
            /*
             * JavaFX Image -> BufferedImage (Java AWT) -> Frame (JavaCV) -> Mat (OpenCV) ->
             * Normalisasi ke BGR 3-channel
             */
            /*
             * BufferedImage adalah kelas Java yang merepresentasikan gambar
             * dalam memori bagian dari Java AWT (Abstract Window Toolkit)
             * yang menyediakan cara untuk memanipulasi gambar (pixel) di java.
             * 
             * parameter SwingFXUtils.fromFXImage(image, null) adalah:
             * - image: gambar yang akan dikonversi
             * - null: BufferedImage yang sudah ada (opsional). Setting null akan membuat
             * BufferedImage baru.
             */
            BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
            if (bufferedImage == null) {
                System.err.println("Gagal mengkonversi Image ke BufferedImage");
                return null;
            }

            /*
             * bgrImage akan menyimpan hasil konversi.
             * Selanjutnya cek apakah format BufferedImage adalah 3 byte BGR
             * (24-bit dengan urutan warna Blue-Green-Red). Ini adalah format yang
             * dibutuhkan OpenCV
             * 
             * Jika tidak, maka buat BufferedImage baru dengan format 3 byte BGR.
             * Graphics2D adalah kelas untuk menggambar/memanipulasi gambar
             * 
             * Jadi, buat BufferedImage baru dengan format 3 byte BGR,
             * lalu menggunakan Graphics2D untuk menggambar (copy) bufferedImage ke
             * bgrImage.
             * 
             * Jika gambar sudah dalam format BGR, gunakan langsung bufferedImage.
             */
            BufferedImage bgrImage;
            if (bufferedImage.getType() != BufferedImage.TYPE_3BYTE_BGR) {
                bgrImage = new BufferedImage(bufferedImage.getWidth(), bufferedImage.getHeight(),
                        BufferedImage.TYPE_3BYTE_BGR);
                java.awt.Graphics2D g2d = bgrImage.createGraphics();
                g2d.drawImage(bufferedImage, 0, 0, null);
                g2d.dispose();
            } else {
                bgrImage = bufferedImage;
            }

            /*
             * Java2DFrameConverter adalah kelas dari JavaCV yang digunakan untuk
             * mengonversi antara format Java AWT dan JavaCV.
             * Menggunakan Java2DFrameConverter untuk mengonversi BufferedImage ke Frame.
             * Frame adalah format yang digunakan JavaCV untuk menghubungkan antara Java AWT
             * dan OpenCV.
             * Frame menjadi jembatan antara Java dan OpenCV yang mendukung operasi
             * video/image processing.
             */
            Java2DFrameConverter java2DConverter = new Java2DFrameConverter();
            Frame frame = java2DConverter.convert(bgrImage);
            if (frame == null) {
                System.err.println("Gagal mengkonversi BufferedImage ke Frame");
                return null;
            }

            /*
             * OpenCVFrameConverter.ToMat adalah kelas dari JavaCV yang digunakan untuk
             * mengonversi antara format JavaCV dan OpenCV.
             * Menggunakan OpenCVFrameConverter.ToMat untuk mengonversi Frame ke Mat.
             * 
             * Mat adalah format native OpenCV.
             * Mat digunakan OpenCV untuk menyimpan gambar.
             * Mat memungkinkan operasi image processing OpenCV.
             */
            OpenCVFrameConverter.ToMat matConverter = new OpenCVFrameConverter.ToMat();
            Mat mat = matConverter.convert(frame);

            /*
             * Validasi apakah Mat valid
             * Jika Mat valid, maka cek apakah Mat memiliki 4 channel (BGRA) (Blue, Green,
             * Red, Alpha (transparansi))
             * Jika ya, maka konversi Mat ke BGR
             * Jika tidak, maka cek apakah Mat memiliki 1 channel (GRAY) (grayscale)
             * Jika ya, maka konversi Mat ke BGR
             * Jika tidak, maka return Mat
             */
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

    private Image matToImage(Mat mat) {
        if (mat == null || mat.empty()) {
            System.err.println("Mat kosong atau null dalam matToImage");
            return null;
        }

        try {
            /*
             * kebalikan dari imageToMat
             * Mat -> Frame -> BufferedImage -> JavaFX Image
             */
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
                if (displayMat != mat)
                    displayMat.release();
                return null;
            }

            Java2DFrameConverter java2DConverter = new Java2DFrameConverter();
            BufferedImage bufferedImage = java2DConverter.convert(frame);
            if (bufferedImage == null) {
                System.err.println("Gagal mengkonversi Frame ke BufferedImage");
                if (displayMat != mat)
                    displayMat.release();
                return null;
            }

            // Convert to JavaFX compatible format
            BufferedImage compatibleImage = new BufferedImage(
                    bufferedImage.getWidth(),
                    bufferedImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
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
}