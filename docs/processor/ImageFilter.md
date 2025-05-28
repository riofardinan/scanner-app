# ImageFilter.java

## Alur Kerja
1. Validasi input image
2. Pilih filter berdasarkan tipe
3. Terapkan filter yang sesuai
4. Simpan debug output
5. Kembalikan hasil filter

## Deskripsi
Kelas ini menggunakan beberapa konsep computer vision:
1. Image Filtering - Menerapkan berbagai filter pada gambar
2. Grayscale Conversion - Konversi gambar ke grayscale
3. Adaptive Thresholding - Thresholding adaptif untuk binarisasi
4. Noise Reduction - Pengurangan noise pada gambar

## Struktur Kode

### Method Utama

#### applyFilter()
```java
public Mat applyFilter(Mat input, String filterType) {
    if (input == null || filterType == null) {
        System.err.println("Invalid input parameters");
        return null;
    }

    try {
        Mat output = new Mat();
        
        switch (filterType.toLowerCase()) {
            case "grayscale":
                cvtColor(input, output, COLOR_BGR2GRAY);
                break;
                
            case "binary":
                Mat gray = new Mat();
                cvtColor(input, gray, COLOR_BGR2GRAY);
                threshold(gray, output, 127, 255, THRESH_BINARY);
                gray.release();
                break;
                
            case "adaptive":
                Mat gray2 = new Mat();
                cvtColor(input, gray2, COLOR_BGR2GRAY);
                adaptiveThreshold(gray2, output, 255, 
                    ADAPTIVE_THRESH_GAUSSIAN_C, THRESH_BINARY, 11, 2);
                gray2.release();
                break;
                
            case "blur":
                GaussianBlur(input, output, new Size(5, 5), 0);
                break;
                
            case "sharpen":
                Mat kernel = new Mat(3, 3, CV_32F);
                kernel.put(0, 0, 0, -1, 0, -1, 5, -1, 0, -1, 0);
                filter2D(input, output, -1, kernel);
                kernel.release();
                break;
                
            case "edge":
                Mat gray3 = new Mat();
                cvtColor(input, gray3, COLOR_BGR2GRAY);
                Canny(gray3, output, 100, 200);
                gray3.release();
                break;
                
            default:
                System.err.println("Unknown filter type: " + filterType);
                return null;
        }
        
        // Debug output
        imwrite("debug/image_filter/01_input.png", input);
        imwrite("debug/image_filter/02_" + filterType + ".png", output);
        
        return output;
        
    } catch (Exception e) {
        System.err.println("Error dalam image filtering: " + e.getMessage());
        return null;
    }
}
```
- Method utama untuk menerapkan filter
- Mendukung berbagai jenis filter
- Menyimpan debug images di setiap tahap
- Memory management untuk objek Mat

### Method Support

#### validateInput()
```java
private boolean validateInput(Mat input) {
    return input != null && !input.empty() && 
           input.channels() > 0 && input.channels() <= 4;
}
```
- Memvalidasi input image
- Memastikan image tidak kosong
- Memeriksa jumlah channel

