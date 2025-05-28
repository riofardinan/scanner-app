package com.scanner.processor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;
import static org.bytedeco.opencv.global.opencv_imgproc.ADAPTIVE_THRESH_GAUSSIAN_C;
import static org.bytedeco.opencv.global.opencv_imgproc.CHAIN_APPROX_SIMPLE;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.Canny;
import static org.bytedeco.opencv.global.opencv_imgproc.FONT_HERSHEY_SIMPLEX;
import static org.bytedeco.opencv.global.opencv_imgproc.HoughLines;
import static org.bytedeco.opencv.global.opencv_imgproc.LINE_AA;
import static org.bytedeco.opencv.global.opencv_imgproc.MORPH_CLOSE;
import static org.bytedeco.opencv.global.opencv_imgproc.MORPH_RECT;
import static org.bytedeco.opencv.global.opencv_imgproc.RETR_EXTERNAL;
import static org.bytedeco.opencv.global.opencv_imgproc.THRESH_BINARY;
import static org.bytedeco.opencv.global.opencv_imgproc.adaptiveThreshold;
import static org.bytedeco.opencv.global.opencv_imgproc.approxPolyDP;
import static org.bytedeco.opencv.global.opencv_imgproc.arcLength;
import static org.bytedeco.opencv.global.opencv_imgproc.bilateralFilter;
import static org.bytedeco.opencv.global.opencv_imgproc.circle;
import static org.bytedeco.opencv.global.opencv_imgproc.contourArea;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgproc.dilate;
import static org.bytedeco.opencv.global.opencv_imgproc.drawContours;
import static org.bytedeco.opencv.global.opencv_imgproc.findContours;
import static org.bytedeco.opencv.global.opencv_imgproc.getStructuringElement;
import static org.bytedeco.opencv.global.opencv_imgproc.line;
import static org.bytedeco.opencv.global.opencv_imgproc.morphologyEx;
import static org.bytedeco.opencv.global.opencv_imgproc.putText;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_imgproc.Vec2fVector;

public class EdgeDetector {
    
    public Mat detectEdges(Mat input) {
        Mat gray = new Mat();
        Mat denoised = new Mat();
        Mat binary = new Mat();
        Mat morph = new Mat();
        Mat edges = new Mat();
        
        // Convert to grayscale
        cvtColor(input, gray, COLOR_BGR2GRAY);
        imwrite("debug/edge_detection/01_gray.png", gray);
        
        // Denoise
        bilateralFilter(gray, denoised, 9, 75, 75);
        imwrite("debug/edge_detection/02_denoised.png", denoised);
        
        // Adaptive thresholding
        adaptiveThreshold(denoised, binary, 255, ADAPTIVE_THRESH_GAUSSIAN_C, THRESH_BINARY, 11, 2);
        imwrite("debug/edge_detection/03_binary.png", binary);
        
        // Morphological operations to clean noise
        Mat kernel = getStructuringElement(MORPH_RECT, new Size(3, 3));
        morphologyEx(binary, morph, MORPH_CLOSE, kernel);
        imwrite("debug/edge_detection/04_morph.png", morph);
        
        // Canny edge detection
        Canny(morph, edges, 50, 150);
        imwrite("debug/edge_detection/05_canny.png", edges);
        
        // Dilate edges to strengthen
        Mat smallKernel = getStructuringElement(MORPH_RECT, new Size(2, 2));
        dilate(edges, edges, smallKernel);
        imwrite("debug/edge_detection/06_final.png", edges);
        
        // Cleanup
        gray.release();
        denoised.release();
        binary.release();
        morph.release();
        kernel.release();
        smallKernel.release();
        
        return edges;
    }
    
    public Point[] detectDocumentCorners(Mat input) {
        System.out.println("Detecting document corners...");
        
        // Edge detection
        Mat edges = detectEdges(input);
        
        // Try Hough Lines first
        Point[] houghCorners = detectCornersWithHoughLines(input, edges);
        if (houghCorners != null) {
            System.out.println("Corner detection successful with Hough Lines");
            edges.release();
            return houghCorners;
        }
        
        // Fallback contour detection
        System.out.println("Hough Lines failed, falling back to contour detection");
        Point[] contourCorners = detectCornersWithContours(input, edges);
        edges.release();
        
        if (contourCorners != null) {
            System.out.println("Corner detection successful with contours");
            return contourCorners;
        }
        
        System.err.println("Failed to detect document corners with all methods!");
        return null;
    }
    
    private Point[] detectCornersWithHoughLines(Mat input, Mat edges) {
        try {
            Vec2fVector lines = new Vec2fVector();
            int threshold = Math.max(100, Math.min(input.cols(), input.rows()) / 4); // Dynamic threshold
            HoughLines(edges, lines, 1, Math.PI/180, threshold, 0, 0, 0, Math.PI);

            System.out.println("Hough Lines detected: " + lines.size() + " lines with threshold: " + threshold);
            
            if (lines.size() < 4) {
                System.out.println("Tidak cukup garis untuk membentuk dokumen (minimal 4)");
                return null;
            }
            
            Mat debugLines = input.clone();
            for (long i = 0; i < lines.size(); i++) {
                float rho = lines.get(i).get(0);
                float theta = lines.get(i).get(1);
                double a = Math.cos(theta), b = Math.sin(theta);
                double x0 = a * rho, y0 = b * rho;
                Point pt1 = new Point((int)Math.round(x0 + 1000 * (-b)), (int)Math.round(y0 + 1000 * (a)));
                Point pt2 = new Point((int)Math.round(x0 - 1000 * (-b)), (int)Math.round(y0 - 1000 * (a)));
                    line(debugLines, pt1, pt2, new Scalar(0,0,255,255), 1, LINE_AA, 0);
            }
            imwrite("debug/edge_detection/07_houghlines.png", debugLines);

            // Pisahkan dan filter garis vertikal & horizontal
            List<double[]> verticals = new ArrayList<>();
            List<double[]> horizontals = new ArrayList<>();
            
            for (long i = 0; i < lines.size(); i++) {
                float rho = lines.get(i).get(0);
                float theta = lines.get(i).get(1);
                    
                    // Klasifikasi
                    double angleDeg = Math.toDegrees(theta);
                    
                    // Vertikal: 0-20° atau 160-180°
                    if ((angleDeg >= 0 && angleDeg <= 20) || (angleDeg >= 160 && angleDeg <= 180)) {
                    verticals.add(new double[]{rho, theta});
                    }
                    // Horizontal: 70-110°
                    else if (angleDeg >= 70 && angleDeg <= 110) {
                    horizontals.add(new double[]{rho, theta});
                }
            }
            
            System.out.println("Garis vertikal: " + verticals.size() + ", horizontal: " + horizontals.size());
            
            if (verticals.size() < 2 || horizontals.size() < 2) {
                System.out.println("Tidak cukup garis vertikal/horizontal");
                return null;
            }
            
            // Filter garis yang terlalu dekat (merge similar lines)
            verticals = filterSimilarLines(verticals, 30); // 30 pixel tolerance
            horizontals = filterSimilarLines(horizontals, 30);
            
            System.out.println("Setelah filter - Vertikal: " + verticals.size() + ", horizontal: " + horizontals.size());
            
            if (verticals.size() < 2 || horizontals.size() < 2) {
                System.out.println("Tidak cukup garis setelah filtering");
                return null;
            }
            
            // Ambil 2 garis terjauh dari masing-masing kelompok
            Comparator<double[]> byRho = Comparator.comparingDouble(a -> a[0]);
            verticals.sort(byRho);
            horizontals.sort(byRho);
                
            double[][] mainLines = new double[4][2];
            mainLines[0] = verticals.get(0); // leftmost vertical
            mainLines[1] = verticals.get(verticals.size() - 1); // rightmost vertical
            mainLines[2] = horizontals.get(0); // topmost horizontal
            mainLines[3] = horizontals.get(horizontals.size() - 1); // bottommost horizontal
            
            Mat debugMainLines = input.clone();
            Scalar[] colors = {new Scalar(255,0,0,255), new Scalar(0,255,0,255), 
                              new Scalar(0,0,255,255), new Scalar(255,255,0,255)};
            for (int i = 0; i < 4; i++) {
                double rho = mainLines[i][0];
                double theta = mainLines[i][1];
                double a = Math.cos(theta), b = Math.sin(theta);
                double x0 = a * rho, y0 = b * rho;
                Point pt1 = new Point((int)Math.round(x0 + 1000 * (-b)), (int)Math.round(y0 + 1000 * (a)));
                Point pt2 = new Point((int)Math.round(x0 - 1000 * (-b)), (int)Math.round(y0 - 1000 * (a)));
                line(debugMainLines, pt1, pt2, colors[i], 3, LINE_AA, 0);
            }
            imwrite("debug/edge_detection/08_main_lines.png", debugMainLines);
            
            // Cari titik potong antar garis (4 sudut)
            Point[] corners = new Point[4];
            corners[0] = intersection(mainLines[0], mainLines[2]); // left-top
            corners[1] = intersection(mainLines[1], mainLines[2]); // right-top
            corners[2] = intersection(mainLines[1], mainLines[3]); // right-bottom
            corners[3] = intersection(mainLines[0], mainLines[3]); // left-bottom
            
            // Validasi corners
            if (!validateCorners(corners, input)) {
                System.out.println("Corners tidak valid");
                return null;
            }
            
            Mat debugPoints = input.clone();
            for (int i = 0; i < 4; i++) {
                Point p = corners[i];
                circle(debugPoints, p, 8, new Scalar(0,255,0,255), -1, LINE_AA, 0);
                putText(debugPoints, String.valueOf(i), 
                    new Point(p.x() + 10, p.y()), 
                    FONT_HERSHEY_SIMPLEX, 0.7, 
                    new Scalar(255,255,255,255), 2, LINE_AA, false);
            }
            imwrite("debug/edge_detection/09_houghpoints.png", debugPoints);
            
            // Cleanup debug images
            debugLines.release();
            debugMainLines.release();
            debugPoints.release();
            
            return corners;
            
        } catch (Exception e) {
            System.err.println("Error dalam Hough Lines detection: " + e.getMessage());
            return null;
        }
    }
    
    private Point[] detectCornersWithContours(Mat input, Mat edges) {
        try {
            MatVector contours = new MatVector();
            Mat hierarchy = new Mat();
            findContours(edges, contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
            
            double maxArea = 0;
            Mat largestContour = null;
            
            // Cari contour dengan area terbesar
            for (int i = 0; i < contours.size(); i++) {
                Mat contour = contours.get(i);
                double area = contourArea(contour);
                double imageArea = input.cols() * input.rows();
                
                // contour harus cukup besar (minimal 10% dari gambar)
                if (area > maxArea && area > (imageArea * 0.1)) {
                    maxArea = area;
                    largestContour = contour;
                }
            }
            
            if (largestContour == null) {
                System.out.println("Tidak ditemukan contour yang cukup besar");
                hierarchy.release();
                return null;
            }
            
            // Approximate polygon
            Mat approx = new Mat();
            double epsilon = 0.02 * arcLength(largestContour, true);
            approxPolyDP(largestContour, approx, epsilon, true);
            
            System.out.println("Contour approximation menghasilkan " + approx.total() + " titik");
            
            if (approx.total() == 4) {
        Point[] corners = new Point[4];
                for (int i = 0; i < 4; i++) {
                    corners[i] = new Point((int)approx.ptr(i).getDouble(0), (int)approx.ptr(i).getDouble(1));
                }
                
                // Sort corners
                sortCorners(corners);
                
                Mat debugContour = input.clone();
                drawContours(debugContour, new MatVector(largestContour), -1, new Scalar(0,255,0,255), 2, LINE_AA, new Mat(), Integer.MAX_VALUE, new Point());
                for (int i = 0; i < 4; i++) {
                    circle(debugContour, corners[i], 8, new Scalar(0,0,255,255), -1, LINE_AA, 0);
                }
                imwrite("debug/edge_detection/10_contour_corners.png", debugContour);
                
                // Cleanup
                debugContour.release();
                approx.release();
                hierarchy.release();
                
                return corners;
            }
            
            // Cleanup
            approx.release();
            hierarchy.release();
            return null;
            
        } catch (Exception e) {
            System.err.println("Error dalam contour detection: " + e.getMessage());
            return null;
            }
        }
    
    private List<double[]> filterSimilarLines(List<double[]> lines, double tolerance) {
        List<double[]> filtered = new ArrayList<>();
        
        for (double[] line : lines) {
            boolean similar = false;
            for (double[] existing : filtered) {
                if (Math.abs(line[0] - existing[0]) < tolerance) {
                    similar = true;
                    break;
                }
            }
            if (!similar) {
                filtered.add(line);
            }
        }
        
        return filtered;
    }
    
    private boolean validateCorners(Point[] corners, Mat input) {
        if (corners == null || corners.length != 4) return false;
        
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

    private Point intersection(double[] line1, double[] line2) {
        double rho1 = line1[0], theta1 = line1[1];
        double rho2 = line2[0], theta2 = line2[1];
        double sinT1 = Math.sin(theta1), cosT1 = Math.cos(theta1);
        double sinT2 = Math.sin(theta2), cosT2 = Math.cos(theta2);
        
        double denominator = cosT1 * sinT2 - cosT2 * sinT1;
        if (Math.abs(denominator) < 1e-10) {
            // Garis paralel
            return new Point(0, 0);
        }
        
        double x = (sinT2 * rho1 - sinT1 * rho2) / denominator;
        double y = (cosT1 * rho2 - cosT2 * rho1) / denominator;
        return new Point((int)Math.round(x), (int)Math.round(y));
    }

    private void sortCorners(Point[] corners) {
        // Hitung center point
        double centerX = 0, centerY = 0;
        for (Point p : corners) {
            centerX += p.x();
            centerY += p.y();
        }
        centerX /= 4;
        centerY /= 4;
        
        // Klasifikasi berdasarkan posisi relatif terhadap center
        Point topLeft = null, topRight = null, bottomLeft = null, bottomRight = null;
        
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
        
        // Fallback jika ada null (gunakan metode sum/diff)
        if (topLeft == null || topRight == null || bottomLeft == null || bottomRight == null) {
            int topLeftIdx = 0, topRightIdx = 0, bottomRightIdx = 0, bottomLeftIdx = 0;
            double minSum = Double.MAX_VALUE, maxSum = -Double.MAX_VALUE;
            double minDiff = Double.MAX_VALUE, maxDiff = -Double.MAX_VALUE;
            
            for (int i = 0; i < 4; i++) {
                double sum = corners[i].x() + corners[i].y();
                double diff = corners[i].x() - corners[i].y();
                
                if (sum < minSum) { minSum = sum; topLeftIdx = i; }
                if (sum > maxSum) { maxSum = sum; bottomRightIdx = i; }
                if (diff < minDiff) { minDiff = diff; bottomLeftIdx = i; }
                if (diff > maxDiff) { maxDiff = diff; topRightIdx = i; }
            }
            
            topLeft = corners[topLeftIdx];
            topRight = corners[topRightIdx];
            bottomRight = corners[bottomRightIdx];
            bottomLeft = corners[bottomLeftIdx];
            }
        
        corners[0] = topLeft;
        corners[1] = topRight;
        corners[2] = bottomRight;
        corners[3] = bottomLeft;
    }
} 
