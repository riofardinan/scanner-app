# PerspectiveCorrector.java

## Alur Kerja
1. Validasi input dan koordinat sudut
2. Urutkan sudut dalam urutan yang konsisten
3. Hitung dimensi output berdasarkan jarak antar sudut
4. Setup matriks transformasi perspektif
5. Terapkan transformasi pada gambar
6. Simpan debug output

## Deskripsi
Kelas ini menggunakan beberapa konsep computer vision:
1. Perspective Transformation - Menggunakan matriks transformasi untuk memperbaiki perspektif
2. Homography - Menghitung matriks transformasi dari 4 titik sudut
3. Image Warping - Menerapkan transformasi pada gambar
4. Coordinate Mapping - Memetakan koordinat dari gambar asli ke gambar hasil

## Struktur Kode

### Method Utama

#### correctPerspective()
```java
public Mat correctPerspective(Mat input, Point[] corners) {
    if (input == null || corners == null || corners.length != 4) {
        System.err.println("Invalid input parameters");
        return null;
    }

    try {
        // Validasi dan clamp koordinat sudut
        for (Point corner : corners) {
            corner.x(Math.max(0, Math.min(corner.x(), input.cols() - 1)));
            corner.y(Math.max(0, Math.min(corner.y(), input.rows() - 1)));
        }

        // Urutkan sudut
        sortCorners(corners);

        // Hitung dimensi output
        double width1 = distance(corners[0], corners[1]);
        double width2 = distance(corners[2], corners[3]);
        double height1 = distance(corners[0], corners[3]);
        double height2 = distance(corners[1], corners[2]);

        int maxWidth = (int) Math.max(width1, width2);
        int maxHeight = (int) Math.max(height1, height2);

        // Setup matriks transformasi
        Mat srcPoints = new Mat(4, 1, CV_32FC2);
        Mat dstPoints = new Mat(4, 1, CV_32FC2);

        // Set source points
        for (int i = 0; i < 4; i++) {
            srcPoints.ptr(i).putFloat(corners[i].x()).putFloat(corners[i].y());
        }

        // Set destination points
        dstPoints.ptr(0).putFloat(0).putFloat(0);
        dstPoints.ptr(1).putFloat(maxWidth - 1).putFloat(0);
        dstPoints.ptr(2).putFloat(maxWidth - 1).putFloat(maxHeight - 1);
        dstPoints.ptr(3).putFloat(0).putFloat(maxHeight - 1);

        // Hitung matriks transformasi
        Mat perspectiveMatrix = getPerspectiveTransform(srcPoints, dstPoints);

        // Terapkan transformasi
        Mat output = new Mat();
        warpPerspective(input, output, perspectiveMatrix, new Size(maxWidth, maxHeight));

        // Debug output
        imwrite("debug/perspective_correction/01_input.png", input);
        Mat debugCorners = input.clone();
        for (int i = 0; i < 4; i++) {
            circle(debugCorners, corners[i], 8, new Scalar(0,255,0,255), -1, LINE_AA, 0);
            putText(debugCorners, String.valueOf(i), 
                new Point(corners[i].x() + 10, corners[i].y()), 
                FONT_HERSHEY_SIMPLEX, 0.7, 
                new Scalar(255,255,255,255), 2, LINE_AA, false);
        }
        imwrite("debug/perspective_correction/02_corners.png", debugCorners);
        imwrite("debug/perspective_correction/03_output.png", output);

        // Cleanup
        srcPoints.release();
        dstPoints.release();
        perspectiveMatrix.release();
        debugCorners.release();

        return output;

    } catch (Exception e) {
        System.err.println("Error dalam perspective correction: " + e.getMessage());
        return null;
    }
}
```
- Method utama untuk koreksi perspektif
- Memvalidasi input dan koordinat sudut
- Menghitung dimensi output
- Menerapkan transformasi perspektif
- Menyimpan debug images di setiap tahap

### Method Support

#### sortCorners()
```java
private void sortCorners(Point[] corners) {
    // Hitung titik pusat
    double centerX = 0, centerY = 0;
    for (Point corner : corners) {
        centerX += corner.x();
        centerY += corner.y();
    }
    centerX /= 4;
    centerY /= 4;

    // Urutkan sudut berdasarkan posisi relatif terhadap pusat
    Arrays.sort(corners, (p1, p2) -> {
        double angle1 = Math.atan2(p1.y() - centerY, p1.x() - centerX);
        double angle2 = Math.atan2(p2.y() - centerY, p2.x() - centerX);
        return Double.compare(angle1, angle2);
    });
}
```
- Mengurutkan sudut dalam urutan yang konsisten
- Menggunakan posisi relatif terhadap pusat

#### distance()
```java
private double distance(Point p1, Point p2) {
    double dx = p2.x() - p1.x();
    double dy = p2.y() - p1.y();
    return Math.sqrt(dx * dx + dy * dy);
}
```
- Menghitung jarak Euclidean antara dua titik

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