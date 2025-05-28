package com.scanner.processor;

import static org.bytedeco.opencv.global.opencv_core.addWeighted;
import static org.bytedeco.opencv.global.opencv_core.merge;
import static org.bytedeco.opencv.global.opencv_core.split;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;
import static org.bytedeco.opencv.global.opencv_imgproc.ADAPTIVE_THRESH_GAUSSIAN_C;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2YUV;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_GRAY2BGR;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_YUV2BGR;
import static org.bytedeco.opencv.global.opencv_imgproc.GaussianBlur;
import static org.bytedeco.opencv.global.opencv_imgproc.INTER_AREA;
import static org.bytedeco.opencv.global.opencv_imgproc.INTER_CUBIC;
import static org.bytedeco.opencv.global.opencv_imgproc.MORPH_RECT;
import static org.bytedeco.opencv.global.opencv_imgproc.THRESH_BINARY;
import static org.bytedeco.opencv.global.opencv_imgproc.adaptiveThreshold;
import static org.bytedeco.opencv.global.opencv_imgproc.createCLAHE;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgproc.dilate;
import static org.bytedeco.opencv.global.opencv_imgproc.erode;
import static org.bytedeco.opencv.global.opencv_imgproc.getStructuringElement;
import static org.bytedeco.opencv.global.opencv_imgproc.resize;
import static org.bytedeco.opencv.global.opencv_photo.fastNlMeansDenoisingColored;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Size;

public class ImageFilter {
    
    public Mat applyFilter(Mat input, String filterType) {
        if (input == null || input.empty()) {
            System.err.println("Input Mat kosong untuk filter");
            return null;
        }
        
        Mat result = null;
        switch (filterType.toLowerCase()) {
            case "hitam putih":
                result = applyBlackAndWhite(input);
                break;
            case "ditingkatkan":
                result = applyEnhanced(input);
                break;
            case "asli":
            default:
                result = applyOriginal(input);
                break;
        }
        
        return result;
    }
    
    public Mat applyOriginal(Mat input) {
        // Return original image without changes, ensure BGR format
        if (input.channels() != 3) {
            if (input.channels() == 1) {
                Mat bgr = new Mat();
                cvtColor(input, bgr, COLOR_GRAY2BGR);
                return bgr;
            }
        }
        return input.clone();
    }
    
    public Mat applyBlackAndWhite(Mat input) {
        try {
            Mat gray = new Mat();
            Mat binary = new Mat();
            Mat result = new Mat();
            
            // Convert to grayscale
            if (input.channels() == 3) {
                cvtColor(input, gray, COLOR_BGR2GRAY);
            } else if (input.channels() == 1) {
                gray = input.clone();
            } else {
                System.err.println("Format input tidak didukung untuk filter hitam putih: " + input.channels() + " channels");
                return input.clone();
            }
            imwrite("debug/filters/black_and_white/01_gray.png", gray);
            
            // Upsampling for better detail resolution
            Mat upsampled = new Mat();
            resize(gray, upsampled, new Size(gray.cols() * 2, gray.rows() * 2), 0, 0, INTER_CUBIC);
            imwrite("debug/filters/black_and_white/02_upsampled.png", upsampled);
            
            // Unsharp masking enhancement
            Mat enhanced = new Mat();
            Mat blur = new Mat();
            GaussianBlur(upsampled, blur, new Size(0, 0), 1.2);
            addWeighted(upsampled, 1.8, blur, -0.8, 0, enhanced);
            imwrite("debug/filters/black_and_white/03_enhanced.png", enhanced);
            
            // Adaptive contrast enhancement
            Mat contrasted = new Mat();
            createCLAHE(2.5, new Size(6, 6)).apply(enhanced, contrasted);
            imwrite("debug/filters/black_and_white/04_clahe.png", contrasted);
            
            // Multi-scale adaptive thresholding
            Mat binary1 = new Mat();
            Mat binary2 = new Mat();
            Mat binary3 = new Mat();
            
            adaptiveThreshold(contrasted, binary1, 255, ADAPTIVE_THRESH_GAUSSIAN_C, THRESH_BINARY, 3, 2);
            adaptiveThreshold(contrasted, binary2, 255, ADAPTIVE_THRESH_GAUSSIAN_C, THRESH_BINARY, 9, 5);
            adaptiveThreshold(contrasted, binary3, 255, ADAPTIVE_THRESH_GAUSSIAN_C, THRESH_BINARY, 19, 8);
            
            // Combine thresholds
            Mat combined = new Mat();
            addWeighted(binary1, 0.4, binary2, 0.4, 0, combined);
            addWeighted(combined, 1.0, binary3, 0.2, 0, binary);
            imwrite("debug/filters/black_and_white/05_binary.png", binary);
            
            // Morphological enhancement for text
            Mat textKernel = getStructuringElement(MORPH_RECT, new Size(1, 2));
            Mat charKernel = getStructuringElement(MORPH_RECT, new Size(2, 1));
            
            dilate(binary, binary, textKernel);
            erode(binary, binary, textKernel);
            dilate(binary, binary, charKernel);
            erode(binary, binary, charKernel);
            imwrite("debug/filters/black_and_white/06_morph.png", binary);
            
            // Downsample back to original size
            Mat final_binary = new Mat();
            resize(binary, final_binary, new Size(input.cols(), input.rows()), 0, 0, INTER_AREA);
            
            // Final sharpening
            Mat finalSharp = new Mat();
            Mat finalBlur = new Mat();
            GaussianBlur(final_binary, finalBlur, new Size(0, 0), 0.3);
            addWeighted(final_binary, 1.5, finalBlur, -0.5, 0, finalSharp);
            
            // Convert back to BGR 3-channel for consistency
            cvtColor(finalSharp, result, COLOR_GRAY2BGR);
            imwrite("debug/filters/black_and_white/07_result.png", result);
            
            // Cleanup
            gray.release();
            upsampled.release();
            enhanced.release();
            blur.release();
            contrasted.release();
            binary1.release();
            binary2.release();
            binary3.release();
            combined.release();
            binary.release();
            textKernel.release();
            charKernel.release();
            final_binary.release();
            finalSharp.release();
            finalBlur.release();
            
            return result;
            
        } catch (Exception e) {
            System.err.println("Error dalam applyBlackAndWhite: " + e.getMessage());
            e.printStackTrace();
            return input.clone();
        }
    }
    
    public Mat applyEnhanced(Mat input) {
        try {
            Mat result = input.clone();
            
            // Ensure input is BGR 3-channel
            if (input.channels() != 3) {
                System.err.println("Enhanced filter memerlukan BGR 3-channel input, mendapat: " + input.channels());
                if (input.channels() == 1) {
                    cvtColor(input, result, COLOR_GRAY2BGR);
                } else {
                    return input.clone();
                }
            }
            
            // Light denoising
            Mat denoised = new Mat();
            fastNlMeansDenoisingColored(result, denoised, 2, 2, 7, 21);
            imwrite("debug/filters/enhanced/01_denoised.png", denoised);
            
            // Subtle sharpening
            Mat sharpened = new Mat();
            Mat blurred = new Mat();
            GaussianBlur(denoised, blurred, new Size(0, 0), 0.5);
            addWeighted(denoised, 1.2, blurred, -0.2, 0, sharpened);
            imwrite("debug/filters/enhanced/02_sharpened.png", sharpened);
            
            // Gentle contrast enhancement - only on brightness, not color
            Mat contrasted = new Mat();
            
            // Convert to YUV to separate luminance from chrominance
            Mat yuv = new Mat();
            cvtColor(sharpened, yuv, COLOR_BGR2YUV);
            
            MatVector channels = new MatVector(3);
            split(yuv, channels);
            
            // Enhance only Y channel (luminance) with gentle CLAHE
            Mat enhancedY = new Mat();
            createCLAHE(1.2, new Size(8, 8)).apply(channels.get(0), enhancedY);
            channels.put(0, enhancedY);
            
            // U and V channels (chrominance) remain unchanged to preserve original colors
            merge(channels, yuv);
            cvtColor(yuv, contrasted, COLOR_YUV2BGR);
            imwrite("debug/filters/enhanced/03_contrasted.png", contrasted);
            
            // Final result - no additional color manipulation
            Mat final_result = contrasted.clone();
            imwrite("debug/filters/enhanced/04_final.png", final_result);
            
            // Cleanup
            denoised.release();
            sharpened.release();
            blurred.release();
            contrasted.release();
            yuv.release();
            enhancedY.release();
            
            return final_result;
            
        } catch (Exception e) {
            System.err.println("Error dalam applyEnhanced: " + e.getMessage());
            e.printStackTrace();
            return input.clone();
        }
    }
} 