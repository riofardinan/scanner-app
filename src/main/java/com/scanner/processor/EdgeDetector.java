package com.scanner.processor;

import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_GRAY2BGR;
import static org.bytedeco.opencv.global.opencv_imgproc.Canny;
import static org.bytedeco.opencv.global.opencv_imgproc.FONT_HERSHEY_SIMPLEX;
import static org.bytedeco.opencv.global.opencv_imgproc.HoughLines;
import static org.bytedeco.opencv.global.opencv_imgproc.INTER_AREA;
import static org.bytedeco.opencv.global.opencv_imgproc.LINE_AA;
import static org.bytedeco.opencv.global.opencv_imgproc.MORPH_CLOSE;
import static org.bytedeco.opencv.global.opencv_imgproc.MORPH_ELLIPSE;
import static org.bytedeco.opencv.global.opencv_imgproc.MORPH_RECT;
import static org.bytedeco.opencv.global.opencv_imgproc.THRESH_BINARY;
import static org.bytedeco.opencv.global.opencv_imgproc.THRESH_OTSU;
import static org.bytedeco.opencv.global.opencv_imgproc.circle;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgproc.dilate;
import static org.bytedeco.opencv.global.opencv_imgproc.getStructuringElement;
import static org.bytedeco.opencv.global.opencv_imgproc.line;
import static org.bytedeco.opencv.global.opencv_imgproc.morphologyEx;
import static org.bytedeco.opencv.global.opencv_imgproc.putText;
import static org.bytedeco.opencv.global.opencv_imgproc.resize;
import static org.bytedeco.opencv.global.opencv_imgproc.threshold;
import static org.bytedeco.opencv.global.opencv_photo.fastNlMeansDenoising;
import static org.bytedeco.opencv.global.opencv_core.kmeans;
import static org.bytedeco.opencv.global.opencv_core.KMEANS_PP_CENTERS;
import static org.bytedeco.opencv.global.opencv_core.*;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_core.TermCriteria;
import org.bytedeco.opencv.opencv_imgproc.Vec2fVector;

public class EdgeDetector {

    public Point[] detectDocumentCorners(Mat input) {
        System.out.println("Detecting document corners...");

        // Detect edges
        Mat edges = detectEdges(input);

        // Try Hough Lines first
        Point[] houghCorners = detectCornersWithHoughLines(edges);
        if (houghCorners != null) {
            System.out.println("Corner detection successful with Hough Lines");
            return houghCorners;
        }

        System.err.println("Failed to detect document corners!");
        return null;
    }

    public Mat detectEdges(Mat input) {
        Mat gray = new Mat();
        Mat resized = new Mat();
        Mat threshold = new Mat();
        Mat denoised = new Mat();
        Mat morph = new Mat();
        Mat edges = new Mat();

        // Convert to grayscale
        cvtColor(input, gray, COLOR_BGR2GRAY);
        imwrite("debug/edge_detection/00_gray.png", gray);

        // Always resize if image is too large
        if (gray.rows() > 1280) {
            double ratio = (double) 1280 / gray.rows();
            int width = (int) (gray.cols() * ratio);
            Size dim = new Size(width, 1280);
            resize(gray, resized, dim, 0, 0, INTER_AREA);
        } else {
            resized = gray.clone();
        }
        // Denoise
        /*
         * h: 10.0 - Filter strength
         * templateWindowSize: 7 - Size of template patch
         * searchWindowSize: 21 - Size of window used to compare patches
         */
        fastNlMeansDenoising(resized, denoised, 10.0f, 7, 21);
        imwrite("debug/edge_detection/01_denoised.png", denoised);

        /*
         * Threshold 0
         * Max value 255
         */
        threshold(denoised, threshold, 0, 255, THRESH_BINARY + THRESH_OTSU);
        imwrite("debug/edge_detection/02_threshold.png", threshold);

        // Morphological operations to clean noise
        /*
         * Kernel 3x3
         */
        Mat kernel = getStructuringElement(MORPH_ELLIPSE, new Size(3, 3));
        morphologyEx(threshold, morph, MORPH_CLOSE, kernel, new Point(-1, -1), 10, 0, null);
        imwrite("debug/edge_detection/03_morph.png", morph);

        // Canny edge detection
        /*
         * Threshold 50
         * Threshold 150
         */
        Canny(morph, edges, 50, 150, 3, false);
        imwrite("debug/edge_detection/04_canny.png", edges);

        // Dilate edges to strengthen
        /*
         * Kernel 2x2
         */
        Mat smallKernel = getStructuringElement(MORPH_RECT, new Size(2, 2));
        dilate(edges, edges, smallKernel);
        imwrite("debug/edge_detection/05_final.png", edges);

        // Cleanup
        gray.release();
        denoised.release();
        morph.release();
        kernel.release();
        smallKernel.release();

        return edges;
    }

    private Point[] detectCornersWithHoughLines(Mat edges) {
        try {
            Vec2fVector lines = new Vec2fVector();
            /*
             * Dynamic threshold
             * Mengambil nilai terkecil antara lebar dan tinggi gambar
             * Membagi nilai terkecil dengan 4
             * Mengambil nilai terbesar antara 100 dan hasil pembagian
             */
            int threshold = Math.max(100, Math.min(edges.cols(), edges.rows()) / 4); // Dynamic threshold
            HoughLines(edges, lines, 1, Math.PI / 180, threshold, 0, 0, 0, Math.PI);

            System.out.println("Hough Lines detected: " + lines.size() + " lines with threshold: " + threshold);

            if (lines.size() < 4) {
                System.out.println("Tidak cukup garis untuk membentuk dokumen (minimal 4)");
                return null;
            }

            // Debug: Gambar semua garis yang terdeteksi
            Mat debugLines = new Mat(edges.rows(), edges.cols(), edges.type());
            edges.copyTo(debugLines);
            Mat debugLinesColor = new Mat();
            cvtColor(debugLines, debugLinesColor, COLOR_GRAY2BGR);
            for (long i = 0; i < lines.size(); i++) {
                float rho = lines.get(i).get(0);
                float theta = lines.get(i).get(1);
                double a = Math.cos(theta), b = Math.sin(theta);
                double x0 = a * rho, y0 = b * rho;
                Point pt1 = new Point((int) Math.round(x0 + 1000 * (-b)), (int) Math.round(y0 + 1000 * (a)));
                Point pt2 = new Point((int) Math.round(x0 - 1000 * (-b)), (int) Math.round(y0 - 1000 * (a)));
                line(debugLinesColor, pt1, pt2, new Scalar(0, 0, 255, 255), 1, LINE_AA, 0);
            }
            imwrite("debug/edge_detection/07_houghlines.png", debugLinesColor);

            // Cari semua intersections antar garis
            List<Point> intersections = new ArrayList<>();

            /**
             * Loop yang bersarang untuk membuat semua kemungkinan pasangan unik dari
             * garis-garis yang ada di dalam lines.
             * rho1, theta1 untuk garis pertama
             * rho2, theta2 untuk garis kedua
             * Hitung selisih sudut antara dua garis untuk mengetahui apakah mereka saling
             * tegak lurus.
             * Jika sudut antara kedua garis 80-100 derajat, maka hitung intersection
             * Pastikan intersection berada dalam batas gambar
             */
            for (long i = 0; i < lines.size(); i++) {
                for (long j = i + 1; j < lines.size(); j++) {
                    float rho1 = lines.get(i).get(0);
                    float theta1 = lines.get(i).get(1);
                    float rho2 = lines.get(j).get(0);
                    float theta2 = lines.get(j).get(1);

                    // Hitung sudut antara dua garis
                    double angle = Math.abs(Math.toDegrees(theta1 - theta2));
                    if (angle > 90)
                        angle = 180 - angle;

                    // Filter: hanya ambil intersection dengan sudut mendekati 90 derajat (80-100
                    // derajat)
                    if (angle >= 80 && angle <= 100) {
                        Point intersection = calculateIntersection(rho1, theta1, rho2, theta2);

                        // Pastikan intersection berada dalam batas gambar
                        if (intersection != null &&
                                intersection.x() >= 0 && intersection.x() < edges.cols() &&
                                intersection.y() >= 0 && intersection.y() < edges.rows()) {
                            intersections.add(intersection);
                        }
                    }
                }
            }

            System.out.println("Total intersections found: " + intersections.size());

            if (intersections.size() < 4) {
                System.out.println("Tidak cukup intersections untuk membentuk quadrilateral");
                return null;
            }

            // Debug: Gambar semua intersections
            Mat debugIntersections = new Mat();
            cvtColor(debugLines, debugIntersections, COLOR_GRAY2BGR);
            for (Point p : intersections) {
                circle(debugIntersections, p, 3, new Scalar(0, 255, 255, 255), -1, LINE_AA, 0);
            }
            imwrite("debug/edge_detection/08_intersections.png", debugIntersections);

            // Clustering intersections menjadi 4 quadrilaterals menggunakan k-means
            Point[] corners = clusterIntersectionsToQuadrilateral(intersections, edges);

            if (corners == null) {
                System.out.println("Gagal melakukan clustering intersections");
                return null;
            }

            // Validasi corners
            if (!validateCorners(corners, edges)) {
                System.out.println("Corners tidak valid");
                return null;
            }

            // Debug: Gambar final quadrilaterals
            Mat debugQuadrilaterals = new Mat();
            cvtColor(debugLines, debugQuadrilaterals, COLOR_GRAY2BGR);
            for (int i = 0; i < 4; i++) {
                Point p = corners[i];
                circle(debugQuadrilaterals, p, 8, new Scalar(0, 255, 0, 255), -1, LINE_AA, 0);
                putText(debugQuadrilaterals, String.valueOf(i),
                        new Point(p.x() + 10, p.y()),
                        FONT_HERSHEY_SIMPLEX, 0.7,
                        new Scalar(255, 255, 255, 255), 2, LINE_AA, false);
            }
            imwrite("debug/edge_detection/09_quadrilaterals.png", debugQuadrilaterals);

            // Cleanup debug images
            debugLines.release();
            debugLinesColor.release();
            debugIntersections.release();
            debugQuadrilaterals.release();

            return corners;

        } catch (Exception e) {
            System.err.println("Error dalam Hough Lines detection: " + e.getMessage());
            return null;
        }
    }

    private Point calculateIntersection(float rho1, float theta1, float rho2, float theta2) {
        double sinT1 = Math.sin(theta1), cosT1 = Math.cos(theta1);
        double sinT2 = Math.sin(theta2), cosT2 = Math.cos(theta2);

        double denominator = cosT1 * sinT2 - cosT2 * sinT1;
        /**
         * Karena perhitungan komputer dengan angka desimal (floating-point)
         * bisa memiliki sedikit error presisi, kita tidak memeriksa denominator == 0.
         * Sebaliknya, kita memeriksa apakah nilai absolutnya sangat-sangat kecil
         * (kurang dari 0.0000000001).
         * Jika denominator tidak 0, maka hitung intersection
         */
        if (Math.abs(denominator) < 1e-10) {
            // Garis paralel
            return null;
        }

        double x = (sinT2 * rho1 - sinT1 * rho2) / denominator;
        double y = (cosT1 * rho2 - cosT2 * rho1) / denominator;
        return new Point((int) Math.round(x), (int) Math.round(y));
    }

    private Point[] clusterIntersectionsToQuadrilateral(List<Point> intersections, Mat image) {
        // Implementasi clustering menggunakan KMeans untuk lebih robust
        if (intersections.size() < 4)
            return null;

        try {
            // 1. Konversi List<Point> ke Mat untuk KMeans
            int nPoints = intersections.size();
            Mat samples = new Mat(nPoints, 2, CV_32FC1);

            for (int i = 0; i < nPoints; i++) {
                samples.ptr(i, 0).putFloat(intersections.get(i).x());
                samples.ptr(i, 1).putFloat(intersections.get(i).y());
            }

            // 2. Setup KMeans parameters
            int clusterCount = 4;
            Mat labels = new Mat();
            Mat centers = new Mat();
            TermCriteria criteria = new TermCriteria(
                    TermCriteria.EPS + TermCriteria.MAX_ITER,
                    100, // max iterations
                    0.1 // epsilon
            );

            // 3. Jalankan KMeans clustering
            kmeans(samples, clusterCount, labels, criteria, 3, KMEANS_PP_CENTERS, centers);

            // 4. Ambil centroid dari setiap cluster sebagai sudut
            Point[] clusterCenters = new Point[4];
            for (int i = 0; i < 4; i++) {
                float x = centers.ptr(i, 0).getFloat();
                float y = centers.ptr(i, 1).getFloat();
                clusterCenters[i] = new Point(Math.round(x), Math.round(y));
            }

            // 5. Urutkan sudut berdasarkan posisi (top-left, top-right, bottom-right,
            // bottom-left)
            Point[] sortedCorners = sortCornersByPosition(clusterCenters);

            // 6. Cleanup
            samples.release();
            labels.release();
            centers.release();

            System.out.println("KMeans clustering berhasil - 4 sudut ditemukan");
            return sortedCorners;

        } catch (Exception e) {
            System.err.println("Error dalam KMeans clustering: " + e.getMessage());
            // Return null jika KMeans gagal
            return null;
        }
    }

    private Point[] sortCornersByPosition(Point[] corners) {
        // Hitung center point dari 4 sudut
        double centerX = 0, centerY = 0;
        for (Point p : corners) {
            centerX += p.x();
            centerY += p.y();
        }
        centerX /= 4;
        centerY /= 4;

        Point topLeft = null, topRight = null, bottomLeft = null, bottomRight = null;

        // Klasifikasi berdasarkan posisi relatif terhadap center
        for (Point p : corners) {
            if (p.x() <= centerX && p.y() <= centerY) {
                topLeft = p;
            } else if (p.x() > centerX && p.y() <= centerY) {
                topRight = p;
            } else if (p.x() <= centerX && p.y() > centerY) {
                bottomLeft = p;
            } else {
                bottomRight = p;
            }
        }

        // Fallback jika ada yang null (gunakan metode sum/diff)
        if (topLeft == null || topRight == null || bottomLeft == null || bottomRight == null) {
            return sortCornersByDistance(corners);
        }

        return new Point[] { topLeft, topRight, bottomRight, bottomLeft };
    }

    private Point[] sortCornersByDistance(Point[] corners) {
        // Metode alternatif: sort berdasarkan sum dan diff koordinat
        int topLeftIdx = 0, topRightIdx = 0, bottomRightIdx = 0, bottomLeftIdx = 0;
        double minSum = Double.MAX_VALUE, maxSum = -Double.MAX_VALUE;
        double minDiff = Double.MAX_VALUE, maxDiff = -Double.MAX_VALUE;

        for (int i = 0; i < 4; i++) {
            double sum = corners[i].x() + corners[i].y();
            double diff = corners[i].x() - corners[i].y();

            if (sum < minSum) {
                minSum = sum;
                topLeftIdx = i;
            }
            if (sum > maxSum) {
                maxSum = sum;
                bottomRightIdx = i;
            }
            if (diff < minDiff) {
                minDiff = diff;
                bottomLeftIdx = i;
            }
            if (diff > maxDiff) {
                maxDiff = diff;
                topRightIdx = i;
            }
        }

        return new Point[] {
                corners[topLeftIdx],
                corners[topRightIdx],
                corners[bottomRightIdx],
                corners[bottomLeftIdx]
        };
    }

    private boolean validateCorners(Point[] corners, Mat input) {
        if (corners == null || corners.length != 4)
            return false;

        int width = input.cols();
        int height = input.rows();

        // Semua corners harus dalam batas gambar
        for (Point corner : corners) {
            if (corner.x() < 0 || corner.x() >= width ||
                    corner.y() < 0 || corner.y() >= height) {
                return false;
            }
        }

        // Area yang dibentuk harus cukup besar
        double area = calculateQuadrilateralArea(corners);
        double imageArea = width * height;
        if (area < imageArea * 0.1) { // minimal 10% dari gambar
            return false;
        }

        return true;
    }

    private double calculateQuadrilateralArea(Point[] corners) {
        // Shoelace formula
        double area = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            area += corners[i].x() * corners[j].y();
            area -= corners[j].x() * corners[i].y();
        }
        return Math.abs(area) / 2.0;
    }
}
