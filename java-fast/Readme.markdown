QR Code generator library - Java, fast
======================================


Introduction
------------

This project aims to be the best, clearest QR Code generator library. The primary goals are flexible options and absolute correctness. Secondary goals are compact implementation size and good documentation comments.

Home page for this fast library with design explanation and benchmarks: https://www.nayuki.io/page/fast-qr-code-generator-library

Home page for the main project with live JavaScript demo, extensive descriptions, and competitor comparisons: https://www.nayuki.io/page/qr-code-generator-library


Features
--------

Core features:

* Approximately 1.5× to 10× faster than other Java implementation
* Shorter code but more documentation comments compared to competing libraries
* Supports encoding all 40 versions (sizes) and all 4 error correction levels, as per the QR Code Model 2 standard
* Output format: Raw modules/pixels of the QR symbol
* Detects finder-like penalty patterns more accurately than other implementations
* Encodes numeric and special-alphanumeric text in less space than general text
* Encodes Japanese Unicode text in kanji mode to save a lot of space compared to UTF-8 bytes
* Computes optimal segment mode switching for text with mixed numeric/alphanumeric/general/kanji parts
* Open-source code under the permissive MIT License

Manual parameters:

* User can specify minimum and maximum version numbers allowed, then library will automatically choose smallest version in the range that fits the data
* User can specify mask pattern manually, otherwise library will automatically evaluate all 8 masks and select the optimal one
* User can specify absolute error correction level, or allow the library to boost it if it doesn't increase the version number
* User can create a list of data segments manually and add ECI segments

More information about QR Code technology and this library's design can be found on the project home page.


Examples
--------

```java
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import io.nayuki.fastqrcodegen.*;

// Simple operation
QrCode qr0 = QrCode.encodeText("Hello, world!", QrCode.Ecc.MEDIUM);
BufferedImage img = toImage(qr0, 4, 10);  // See QrCodeGeneratorDemo
ImageIO.write(img, "png", new File("qr-code.png"));

// Manual operation
List<QrSegment> segs = QrSegment.makeSegments("3141592653589793238462643383");
QrCode qr1 = QrCode.encodeSegments(segs, QrCode.Ecc.HIGH, 5, 5, 2, false);
for (int y = 0; y < qr1.size; y++) {
    for (int x = 0; x < qr1.size; x++) {
        (... paint qr1.getModule(x, y) ...)
    }
}
```

More complete set of examples: https://github.com/nayuki/QR-Code-generator/blob/master/java-fast/io/nayuki/fastqrcodegen/QrCodeGeneratorDemo.java .
