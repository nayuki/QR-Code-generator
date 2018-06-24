QR Code generator library
=========================


Introduction
------------

This project aims to be the best, clearest QR Code generator library in multiple languages. The primary goals are flexible options and absolute correctness. Secondary goals are compact implementation size and good documentation comments.

Home page with live JavaScript demo, extensive descriptions, and competitor comparisons: [https://www.nayuki.io/page/qr-code-generator-library](https://www.nayuki.io/page/qr-code-generator-library)


Features
--------

Core features:

* Available in 7 programming languages, all with nearly equal functionality: Java, JavaScript, TypeScript, Python, C++, C, Rust
* Significantly shorter code but more documentation comments compared to competing libraries
* Supports encoding all 40 versions (sizes) and all 4 error correction levels, as per the QR Code Model 2 standard
* Output formats: Raw modules/pixels of the QR symbol (all languages), SVG XML string (all languages except C), `BufferedImage` raster bitmap (Java only), HTML5 canvas (JavaScript and TypeScript only)
* Encodes numeric and special-alphanumeric text in less space than general text
* Open source code under the permissive MIT License

Manual parameters:

* User can specify minimum and maximum version numbers allowed, then library will automatically choose smallest version in the range that fits the data
* User can specify mask pattern manually, otherwise library will automatically evaluate all 8 masks and select the optimal one
* User can specify absolute error correction level, or allow the library to boost it if it doesn't increase the version number
* User can create a list of data segments manually and add ECI segments (all languages except C)

Optional advanced features (Java only):

* Encodes Japanese Unicode text in kanji mode to save a lot of space compared to UTF-8 bytes
* Computes optimal segment mode switching for text with mixed numeric/alphanumeric/general parts

More information about QR Code technology and this library's design can be found on the project home page.


Examples
--------

Java language:

    import java.awt.image.BufferedImage;
    import java.io.File;
    import java.util.List;
    import javax.imageio.ImageIO;
    import io.nayuki.qrcodegen.*;
    
    // Simple operation
    QrCode qr0 = QrCode.encodeText("Hello, world!", QrCode.Ecc.MEDIUM);
    BufferedImage img = qr0.toImage(4, 10);
    ImageIO.write(img, "png", new File("qr-code.png"));
    
    // Manual operation
    List<QrSegment> segs = QrSegment.makeSegments("3141592653589793238462643383");
    QrCode qr1 = QrCode.encodeSegments(segs, QrCode.Ecc.HIGH, 5, 5, 2, false);
    for (int y = 0; y < qr1.size; y++) {
        for (int x = 0; x < qr1.size; x++) {
            (... paint qr1.getModule(x, y) ...)
        }
    }

JavaScript language:

    // Name abbreviated for the sake of these examples here
    var QRC = qrcodegen.QrCode;
    
    // Simple operation
    var qr0 = QRC.encodeText("Hello, world!", QRC.Ecc.MEDIUM);
    var svg = qr0.toSvgString(4);
    
    // Manual operation
    var segs = qrcodegen.QrSegment.makeSegments("3141592653589793238462643383");
    var qr1 = QRC.encodeSegments(segs, QRC.Ecc.HIGH, 5, 5, 2, false);
    for (var y = 0; y < qr1.size; y++) {
        for (var x = 0; x < qr1.size; x++) {
            (... paint qr1.getModule(x, y) ...)
        }
    }

TypeScript language:

    // Simple operation
    let qr0: qrcodegen.QrCode = qrcodegen.QrCode.encodeText(
        "Hello, world!", qrcodegen.QrCode_Ecc.MEDIUM);
    let svg: string = qr0.toSvgString(4);
    
    // Manual operation
    let segs: Array<qrcodegen.QrSegment> =
        qrcodegen.QrSegment.makeSegments("3141592653589793238462643383");
    let qr1: qrcodegen.QrCode = qrcodegen.QrCode.encodeSegments(
        segs, qrcodegen.QrCode_Ecc.HIGH, 5, 5, 2, false);
    for (let y = 0; y < qr1.size; y++) {
        for (let x = 0; x < qr1.size; x++) {
            (... paint qr1.getModule(x, y) ...)
        }
    }

Python language:

    from qrcodegen import *
    
    # Simple operation
    qr0 = QrCode.encode_text("Hello, world!", QrCode.Ecc.MEDIUM)
    svg = qr0.to_svg_str(4)
    
    # Manual operation
    segs = QrSegment.make_segments("3141592653589793238462643383")
    qr1 = QrCode.encode_segments(segs, QrCode.Ecc.HIGH, 5, 5, 2, False)
    for y in range(qr1.get_size()):
        for x in range(qr1.get_size()):
            (... paint qr1.get_module(x, y) ...)

C++ language:

    #include <string>
    #include <vector>
    #include "QrCode.hpp"
    using namespace qrcodegen;
    
    // Simple operation
    QrCode qr0 = QrCode::encodeText("Hello, world!", QrCode::Ecc::MEDIUM);
    std::string svg = qr0.toSvgString(4);
    
    // Manual operation
    std::vector<QrSegment> segs =
        QrSegment::makeSegments("3141592653589793238462643383");
    QrCode qr1 = QrCode::encodeSegments(
        segs, QrCode::Ecc::HIGH, 5, 5, 2, false);
    for (int y = 0; y < qr1.getSize(); y++) {
        for (int x = 0; x < qr1.getSize(); x++) {
            (... paint qr1.getModule(x, y) ...)
        }
    }

C language:

    #include <stdbool.h>
    #include <stdint.h>
    #include "qrcodegen.h"
    
    // Text data
    uint8_t qr0[qrcodegen_BUFFER_LEN_MAX];
    uint8_t tempBuffer[qrcodegen_BUFFER_LEN_MAX];
    bool ok = qrcodegen_encodeText("Hello, world!",
        tempBuffer, qr0, qrcodegen_Ecc_MEDIUM,
        qrcodegen_VERSION_MIN, qrcodegen_VERSION_MAX,
        qrcodegen_Mask_AUTO, true);
    if (!ok)
        return;
    
    int size = qrcodegen_getSize(qr0);
    for (int y = 0; y < size; y++) {
        for (int x = 0; x < size; x++) {
            (... paint qrcodegen_getModule(qr0, x, y) ...)
        }
    }
    
    // Binary data
    uint8_t dataAndTemp[qrcodegen_BUFFER_LEN_FOR_VERSION(7)]
        = {0xE3, 0x81, 0x82};
    uint8_t qr1[qrcodegen_BUFFER_LEN_FOR_VERSION(7)];
    ok = qrcodegen_encodeBinary(dataAndTemp, 3, qr1,
        qrcodegen_Ecc_HIGH, 2, 7, qrcodegen_Mask_4, false);

Rust language:

    extern crate qrcodegen;
    use qrcodegen::QrCode;
    use qrcodegen::QrCodeEcc;
    use qrcodegen::QrSegment;
    
    // Simple operation
    let qr0 = QrCode::encode_text("Hello, world!",
        QrCodeEcc::Medium).unwrap();
    let svg = qr0.to_svg_string(4);
    
    // Manual operation
    let chrs: Vec<char> = "3141592653589793238462643383".chars().collect();
    let segs = QrSegment::make_segments(&chrs);
    let qr1 = QrCode::encode_segments_advanced(
        &segs, QrCodeEcc::High, 5, 5, Some(2), false).unwrap();
    for y in 0 .. qr1.size() {
        for x in 0 .. qr1.size() {
            (... paint qr1.get_module(x, y) ...)
        }
    }


License
-------

Copyright © 2018 Project Nayuki. (MIT License)  
[https://www.nayuki.io/page/qr-code-generator-library](https://www.nayuki.io/page/qr-code-generator-library)

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

* The above copyright notice and this permission notice shall be included in
  all copies or substantial portions of the Software.

* The Software is provided "as is", without warranty of any kind, express or
  implied, including but not limited to the warranties of merchantability,
  fitness for a particular purpose and noninfringement. In no event shall the
  authors or copyright holders be liable for any claim, damages or other
  liability, whether in an action of contract, tort or otherwise, arising from,
  out of or in connection with the Software or the use or other dealings in the
  Software.
