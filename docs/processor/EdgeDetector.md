# EdgeDetector.java

## Deskripsi
Kelas ini menggunakan beberapa konsep computer vision:
1. Edge Detection - Menggunakan algoritma Canny
2. Hough Transform - Untuk deteksi garis
3. Contour Detection - Sebagai fallback untuk deteksi sudut
4. Image Processing - Operasi morfologi dan thresholding

## Alur Kerja
1. Deteksi tepi menggunakan Canny
2. Coba deteksi sudut dengan Hough Lines
3. Jika gagal, gunakan deteksi contour
4. Validasi sudut yang terdeteksi
5. Urutkan sudut dalam urutan yang konsisten

## Struktur Kode

### Method Utama

#### detectEdges()
```java
public Mat detectEdges(Mat input) {
    Mat gray = new Mat();
    Mat denoised = new Mat();
    Mat binary = new Mat();
    Mat morph = new Mat();
    Mat edges = new Mat();
    
    // Convert to grayscale
    cvtColor(input, gray, COLOR_BGR2GRAY);
    imwrite("debug/edge_detection/01_gray.png", gray);
    
    // Denoise while preserving edges
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
    
    // Dilate edges to strengthen them
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
```
- Method utama untuk deteksi tepi
- Menghasilkan matriks tepi yang dapat digunakan untuk deteksi sudut
- Menyimpan debug images di setiap tahap

#### detectDocumentCorners()
```java
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
    
    // Fallback to contour detection
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
```
- Method utama untuk deteksi sudut dokumen
- Menggunakan dua pendekatan: Hough Lines dan Contour Detection
- Mengembalikan array 4 titik yang merepresentasikan sudut dokumen

### Method Support

#### detectCornersWithHoughLines()
```java
private Point[] detectCornersWithHoughLines(Mat input, Mat edges) {
    try {
        // Hough Line Transform dengan parameter yang lebih ketat
        Vec2fVector lines = new Vec2fVector();
        int threshold = Math.max(100, Math.min(input.cols(), input.rows()) / 4); // Dynamic threshold
        HoughLines(edges, lines, 1, Math.PI/180, threshold, 0, 0, 0, Math.PI);

        System.out.println("Hough Lines detected: " + lines.size() + " lines with threshold: " + threshold);
        
        if (lines.size() < 4) {
            System.out.println("Tidak cukup garis untuk membentuk dokumen (minimal 4)");
            return null;
        }
        
        // Debug menggunakan CLONE dari input
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
            
            // Klasifikasi yang lebih akurat
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
        
        // Filter garis yang terlalu dekat
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
        
        // Debug: gambar garis utama
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
        
        // Debug: gambar titik sudut
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
```
- Menggunakan Hough Transform untuk deteksi garis
- Memfilter garis berdasarkan orientasi
- Mencari titik potong untuk mendapatkan sudut
- Menyimpan debug images di setiap tahap

#### detectCornersWithContours()
```java
private Point[] detectCornersWithContours(Mat input, Mat edges) {
    try {
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(edges, contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
        
        double maxArea = 0;
        Mat largestContour = null;
        
        // Find largest contour
        for (long i = 0; i < contours.size(); i++) {
            Mat contour = contours.get(i);
            double area = contourArea(contour);
            if (area > maxArea) {
                maxArea = area;
                largestContour = contour;
            }
        }
        
        if (largestContour == null) {
            System.out.println("Tidak ada contour yang ditemukan");
            return null;
        }
        
        // Debug: gambar semua contour
        Mat debugContours = input.clone();
        drawContours(debugContours, contours, -1, new Scalar(0,255,0,255), 2, LINE_AA, hierarchy, 0, new Point());
        imwrite("debug/edge_detection/10_contours.png", debugContours);
        
        // Approximate polygon
        Mat approxCurve = new Mat();
        double epsilon = 0.02 * arcLength(largestContour, true);
        approxPolyDP(largestContour, approxCurve, epsilon, true);
        
        if (approxCurve.total() != 4) {
            System.out.println("contour tidak membentuk quadrilateral (4 sudut)");
            return null;
        }
        
        // Convert to points
        Point[] corners = new Point[4];
        for (int i = 0; i < 4; i++) {
            corners[i] = new Point(approxCurve.ptr(i).getInt(0), approxCurve.ptr(i).getInt(1));
        }
        
        // Debug: gambar sudut yang terdeteksi
        Mat debugPoints = input.clone();
        for (int i = 0; i < 4; i++) {
            Point p = corners[i];
            circle(debugPoints, p, 8, new Scalar(0,255,0,255), -1, LINE_AA, 0);
            putText(debugPoints, String.valueOf(i), 
                new Point(p.x() + 10, p.y()), 
                FONT_HERSHEY_SIMPLEX, 0.7, 
                new Scalar(255,255,255,255), 2, LINE_AA, false);
        }
        imwrite("debug/edge_detection/11_contour_points.png", debugPoints);
        
        // Cleanup
        debugContours.release();
        debugPoints.release();
        approxCurve.release();
        hierarchy.release();
        
        return corners;
        
    } catch (Exception e) {
        System.err.println("Error dalam contour detection: " + e.getMessage());
        return null;
    }
}
```
- Method fallback menggunakan deteksi contour
- Memfilter contour berdasarkan area
- Menggunakan approximately polygon untuk mendapatkan sudut
- Menyimpan debug images di setiap tahap

### Method Utilitas

#### filterSimilarLines()
```java
private List<double[]> filterSimilarLines(List<double[]> lines, double tolerance) {
    List<double[]> filtered = new ArrayList<>();
    for (double[] line : lines) {
        boolean isSimilar = false;
        for (double[] existing : filtered) {
            if (Math.abs(line[0] - existing[0]) < tolerance) {
                isSimilar = true;
                break;
            }
        }
        if (!isSimilar) {
            filtered.add(line);
        }
    }
    return filtered;
}
```
- Memfilter garis yang terlalu dekat
- Menggabungkan garis yang mirip

#### validateCorners()
```java
private boolean validateCorners(Point[] corners, Mat input) {
    if (corners == null || corners.length != 4) {
        return false;
    }
    
    // Check if corners are within image bounds
    for (Point corner : corners) {
        if (corner.x() < 0 || corner.x() >= input.cols() ||
            corner.y() < 0 || corner.y() >= input.rows()) {
            return false;
        }
    }
    
    // Check if corners form a valid quadrilateral
    double area = calculateQuadrilateralArea(corners);
    double minArea = input.cols() * input.rows() * 0.1; // 10% of image area
    
    return area > minArea;
}
```
- Memvalidasi sudut yang terdeteksi
- Memastikan sudut membentuk quadrilateral yang valid

#### calculateQuadrilateralArea()
```java
private double calculateQuadrilateralArea(Point[] corners) {
    double area = 0;
    for (int i = 0; i < 4; i++) {
        Point p1 = corners[i];
        Point p2 = corners[(i + 1) % 4];
        area += p1.x() * p2.y() - p2.x() * p1.y();
    }
    return Math.abs(area) / 2;
}
```
- Menghitung luas quadrilateral yang dibentuk oleh sudut

#### intersection()
```java
private Point intersection(double[] line1, double[] line2) {
    double rho1 = line1[0], theta1 = line1[1];
    double rho2 = line2[0], theta2 = line2[1];
    
    double a1 = Math.cos(theta1), b1 = Math.sin(theta1);
    double a2 = Math.cos(theta2), b2 = Math.sin(theta2);
    
    double det = a1 * b2 - a2 * b1;
    if (Math.abs(det) < 1e-10) {
        return null; // Lines are parallel
    }
    
    double x = (b2 * rho1 - b1 * rho2) / det;
    double y = (-a2 * rho1 + a1 * rho2) / det;
    
    return new Point((int)Math.round(x), (int)Math.round(y));
}
```
- Menghitung titik potong antara dua garis

#### sortCorners()
```java
private void sortCorners(Point[] corners) {
    // Calculate center point
    double centerX = 0, centerY = 0;
    for (Point corner : corners) {
        centerX += corner.x();
        centerY += corner.y();
    }
    centerX /= 4;
    centerY /= 4;
    
    // Sort corners based on their position relative to center
    Arrays.sort(corners, (p1, p2) -> {
        double angle1 = Math.atan2(p1.y() - centerY, p1.x() - centerX);
        double angle2 = Math.atan2(p2.y() - centerY, p2.x() - centerX);
        return Double.compare(angle1, angle2);
    });
}
```
- Mengurutkan sudut dalam urutan yang konsisten
- Menggunakan posisi relatif terhadap pusat