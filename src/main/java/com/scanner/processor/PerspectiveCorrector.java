package com.scanner.processor;

import org.bytedeco.javacpp.FloatPointer;
import static org.bytedeco.opencv.global.opencv_core.BORDER_CONSTANT;
import static org.bytedeco.opencv.global.opencv_core.CV_32FC2;
import static org.bytedeco.opencv.global.opencv_imgproc.INTER_LINEAR;
import static org.bytedeco.opencv.global.opencv_imgproc.getPerspectiveTransform;
import static org.bytedeco.opencv.global.opencv_imgproc.warpPerspective;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;

public class PerspectiveCorrector {
    
    public Mat correctPerspective(Mat input, Point[] corners) {
        if (input == null || input.empty() || corners == null || corners.length != 4) {
            System.err.println("Input atau corners tidak valid");
            return null;
        }
        /**
         * corners[0] = topLeft 
         * corners[1] = topRight
         * corners[2] = bottomRight
         * corners[3] = bottomLeft
        */
        
        // Hitung dimensi output (Euclidean Distance)
        double width1 = distance(corners[0], corners[1]);  // top
        double width2 = distance(corners[2], corners[3]);  // bottom
        double height1 = distance(corners[0], corners[3]); // left  
        double height2 = distance(corners[1], corners[2]); // right
        
        // Ambil jarak terpanjang
        int maxWidth = (int) Math.max(width1, width2);
        int maxHeight = (int) Math.max(height1, height2);
        
        // Setup source dan destination points
        Mat srcPoints = new Mat(4, 1, CV_32FC2);
        Mat dstPoints = new Mat(4, 1, CV_32FC2);
        
        // Source: corners dari EdgeDetector (top-left, top-right, bottom-right, bottom-left)
        /**
         * Membuat pointer ke array float dengan 8 elemen
         * Format: [x0, y0, x1, y1, x2, y2, x3, y3]
         * put(index, value): 
         * index = Posisi dalam array (0-7)
         * value = Nilai float yang akan disimpan
         */
        FloatPointer srcPtr = new FloatPointer(8);
        srcPtr.put(0, corners[0].x()).put(1, corners[0].y());  // top-left
        srcPtr.put(2, corners[1].x()).put(3, corners[1].y());  // top-right
        srcPtr.put(4, corners[2].x()).put(5, corners[2].y());  // bottom-right
        srcPtr.put(6, corners[3].x()).put(7, corners[3].y());  // bottom-left
        srcPoints.ptr().put(srcPtr);
        
        // Destination: rectangle sempurna
        FloatPointer dstPtr = new FloatPointer(8);
        dstPtr.put(0, 0).put(1, 0);                    // top-left
        dstPtr.put(2, maxWidth).put(3, 0);             // top-right  
        dstPtr.put(4, maxWidth).put(5, maxHeight);     // bottom-right
        dstPtr.put(6, 0).put(7, maxHeight);            // bottom-left
        dstPoints.ptr().put(dstPtr);
        
        // Get transformation matrix dan apply
        Mat transform = getPerspectiveTransform(srcPoints, dstPoints);
        Mat result = new Mat();
        
        warpPerspective(input, result, transform, new Size(maxWidth, maxHeight), 
                       INTER_LINEAR, BORDER_CONSTANT, new Scalar(255, 255, 255, 0));
        
        // Cleanup
        srcPoints.release();
        dstPoints.release(); 
        transform.release();
        
        return result;
    }
    
    private double distance(Point p1, Point p2) {
        // Euclidean Distance
        return Math.sqrt(Math.pow(p1.x() - p2.x(), 2) + Math.pow(p1.y() - p2.y(), 2));
    }
} 