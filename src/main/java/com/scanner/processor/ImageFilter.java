package com.scanner.processor;

import static org.bytedeco.opencv.global.opencv_core.addWeighted;
import static org.bytedeco.opencv.global.opencv_core.merge;
import static org.bytedeco.opencv.global.opencv_core.split;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2YUV;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_GRAY2BGR;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_YUV2BGR;
import static org.bytedeco.opencv.global.opencv_imgproc.GaussianBlur;
import static org.bytedeco.opencv.global.opencv_imgproc.THRESH_BINARY;
import static org.bytedeco.opencv.global.opencv_imgproc.createCLAHE;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgproc.threshold;
import static org.bytedeco.opencv.global.opencv_photo.fastNlMeansDenoising;
import static org.bytedeco.opencv.global.opencv_photo.fastNlMeansDenoisingColored;
import static org.bytedeco.opencv.global.opencv_imgproc.THRESH_OTSU;
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
            Mat denoised = new Mat();
            Mat threshold = new Mat();
            Mat result = new Mat();

            // Convert to grayscale
            if (input.channels() == 3) {
                cvtColor(input, gray, COLOR_BGR2GRAY);
            } else if (input.channels() == 1) {
                gray = input.clone();
            } else {
                System.err.println(
                        "Format input tidak didukung untuk filter hitam putih: " + input.channels() + " channels");
                return input.clone();
            }
            imwrite("debug/filters/black_and_white/01_gray.png", gray);

            fastNlMeansDenoising(gray, denoised, 3.0f, 7, 21);
            imwrite("debug/filters/black_and_white/02_denoised.png", denoised);

            threshold(denoised, threshold, 0, 255, THRESH_BINARY + THRESH_OTSU);
            imwrite("debug/filters/black_and_white/03_threshold.png", threshold);

            // Convert back to BGR 3-channel for consistency
            cvtColor(threshold, result, COLOR_GRAY2BGR);
            imwrite("debug/filters/black_and_white/04_result.png", result);

            // Cleanup
            gray.release();
            denoised.release();
            threshold.release();

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