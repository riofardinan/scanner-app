package com.scanner.processor;

import org.bytedeco.javacpp.FloatPointer;
import static org.bytedeco.opencv.global.opencv_core.BORDER_CONSTANT;
import static org.bytedeco.opencv.global.opencv_core.CV_32FC2;
import static org.bytedeco.opencv.global.opencv_imgproc.CHAIN_APPROX_SIMPLE;
import static org.bytedeco.opencv.global.opencv_imgproc.INTER_LINEAR;
import static org.bytedeco.opencv.global.opencv_imgproc.RETR_EXTERNAL;
import static org.bytedeco.opencv.global.opencv_imgproc.approxPolyDP;
import static org.bytedeco.opencv.global.opencv_imgproc.arcLength;
import static org.bytedeco.opencv.global.opencv_imgproc.contourArea;
import static org.bytedeco.opencv.global.opencv_imgproc.findContours;
import static org.bytedeco.opencv.global.opencv_imgproc.getPerspectiveTransform;
import static org.bytedeco.opencv.global.opencv_imgproc.warpPerspective;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;

public class PerspectiveCorrector {
    
    public Mat correctPerspective(Mat input, Point[] corners) {
        if (input == null || input.empty()) {
            System.err.println("Input Mat kosong");
            return null;
        }
        
        if (corners == null || corners.length != 4) {
            System.err.println("Corner tidak valid, mencoba deteksi kontur");
            corners = findContourCorners(input);
            if (corners == null) return null;
        }
        
        // Validasi dan clamp coordinates
        for (int i = 0; i < 4; i++) {
            corners[i] = new Point(
                Math.max(0, Math.min(corners[i].x(), input.cols() - 1)),
                Math.max(0, Math.min(corners[i].y(), input.rows() - 1))
            );
        }
        
        sortCorners(corners);
        
        // Hitung dimensi output
        double widthA = distance(corners[0], corners[1]);
        double widthB = distance(corners[2], corners[3]);
        double maxWidth = Math.max(widthA, widthB);
        
        double heightA = distance(corners[0], corners[3]);
        double heightB = distance(corners[1], corners[2]);
        double maxHeight = Math.max(heightA, heightB);
        
        if (maxWidth < 50 || maxHeight < 50) {
            System.err.println("Ukuran hasil terlalu kecil");
            return null;
        }
        
        // Setup transformation matrix
        Mat srcMat = new Mat(4, 1, CV_32FC2);
        Mat dstMat = new Mat(4, 1, CV_32FC2);
        
        FloatPointer srcPtr = new FloatPointer(8);
        srcPtr.put(0, (float)corners[0].x()).put(1, (float)corners[0].y());
        srcPtr.put(2, (float)corners[1].x()).put(3, (float)corners[1].y());
        srcPtr.put(4, (float)corners[2].x()).put(5, (float)corners[2].y());
        srcPtr.put(6, (float)corners[3].x()).put(7, (float)corners[3].y());
        srcMat.ptr().put(srcPtr);
        
        FloatPointer dstPtr = new FloatPointer(8);
        dstPtr.put(0, 0).put(1, 0);
        dstPtr.put(2, (float)maxWidth).put(3, 0);
        dstPtr.put(4, (float)maxWidth).put(5, (float)maxHeight);
        dstPtr.put(6, 0).put(7, (float)maxHeight);
        dstMat.ptr().put(dstPtr);
        
        // Validasi area quadrilateral
        double area = calculateQuadrilateralArea(corners);
        double minArea = input.cols() * input.rows() * 0.05;
        if (area < minArea) {
            System.err.println("Area quadrilateral terlalu kecil");
            srcMat.release();
            dstMat.release();
            return null;
        }
        
        Mat perspectiveTransform = getPerspectiveTransform(srcMat, dstMat);
        if (perspectiveTransform.empty()) {
            System.err.println("Gagal membuat transformation matrix");
            srcMat.release();
            dstMat.release();
            return null;
        }
        
        // Apply transformation
        Mat result = new Mat();
        Size outputSize = new Size((int)Math.round(maxWidth), (int)Math.round(maxHeight));
        
        try {
            warpPerspective(input, result, perspectiveTransform, outputSize, 
                          INTER_LINEAR, BORDER_CONSTANT, 
                          new Scalar(255, 255, 255, 0));
            
            if (result.empty() || result.cols() < 10 || result.rows() < 10) {
                System.err.println("Hasil perspective correction tidak valid");
                result.release();
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("Error dalam warpPerspective: " + e.getMessage());
            if (!result.empty()) result.release();
            return null;
        } finally {
            srcMat.release();
            dstMat.release();
            perspectiveTransform.release();
        }
        
        return result;
    }
    
    private Point[] findContourCorners(Mat input) {
        try {
            MatVector contours = new MatVector();
            Mat hierarchy = new Mat();
            findContours(input, contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
            
            double maxArea = 0;
            Mat largestContour = null;
            
            for (int i = 0; i < contours.size(); i++) {
                Mat contour = contours.get(i);
                double area = contourArea(contour);
                if (area > maxArea && area > (input.cols() * input.rows() * 0.1)) {
                    maxArea = area;
                    largestContour = contour;
                }
            }
            
            if (largestContour != null) {
                Point[] corners = findFourCorners(largestContour, input);
                hierarchy.release();
                return corners;
            }
            
            hierarchy.release();
            return null;
            
        } catch (Exception e) {
            System.err.println("Error dalam findContourCorners: " + e.getMessage());
            return null;
        }
    }
    
    private Point[] findFourCorners(Mat contour, Mat input) {
        try {
            Mat approx = new Mat();
            double epsilon = 0.02 * arcLength(contour, true);
            approxPolyDP(contour, approx, epsilon, true);
            
            if (approx.total() == 4) {
                Point[] corners = new Point[4];
                for (int i = 0; i < 4; i++) {
                    corners[i] = new Point(
                        (int)approx.ptr(i).getDouble(0), 
                        (int)approx.ptr(i).getDouble(1)
                    );
                }
                approx.release();
                return corners;
            }
            
            approx.release();
            return null;
            
        } catch (Exception e) {
            System.err.println("Error dalam findFourCorners: " + e.getMessage());
            return null;
        }
    }
    
    private void sortCorners(Point[] corners) {
        double centerX = 0, centerY = 0;
        for (Point p : corners) {
            centerX += p.x();
            centerY += p.y();
        }
        centerX /= 4;
        centerY /= 4;
        
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
    
    private double distance(Point p1, Point p2) {
        return Math.sqrt(Math.pow(p1.x() - p2.x(), 2) + Math.pow(p1.y() - p2.y(), 2));
    }
    
    private double calculateQuadrilateralArea(Point[] corners) {
        double area = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            area += corners[i].x() * corners[j].y();
            area -= corners[j].x() * corners[i].y();
        }
        return Math.abs(area) / 2.0;
    }
} 