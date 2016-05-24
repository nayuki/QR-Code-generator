QR Code generator library
=========================


Introduction
------------

This project aims to provide the best and clearest QR Code generator library. The primary goals are flexible options and absolute correctness. The secondary goals are compact implementation size and good documentation comments.

Home page with live JavaScript demo and extensive description: [https://www.nayuki.io/page/qr-code-generator-library](https://www.nayuki.io/page/qr-code-generator-library)


Features
--------

Core features:

* Available in 4 programming languages, all with nearly equal functionality: Java, JavaScript, Python, C++
* Significantly shorter code but more documentation comments compared to competing libraries
* Supports encoding all 40 versions (sizes) and all 4 error correction levels, as per the QR Code Model 2 standard
* Output formats: Raw modules/pixels of the QR symbol (all languages), SVG XML string (all languages), BufferedImage raster bitmap (Java only)
* Encodes numeric and special-alphanumeric text in less space than general text
* Open source code under the permissive MIT License

Manual parameters:

* User can specify minimum and maximum version numbers allowed, then library will automatically choose smallest version in the range that fits the data
* User can specify mask pattern manually, otherwise library will automatically evaluate all 8 masks and select the optimal one
* User can specify absolute error correction level, or allow the library to boost it if it doesn't increase the version number

Optional advanced features (Java only):

* Encodes Japanese Unicode text in kanji mode to save a lot of space compared to UTF-8 bytes
* Computes optimal segment mode switching for text with mixed numeric/alphanumeric/general parts


Examples
--------

Java language:

    import io.nayuki.qrcodegen.*;
    
    // Simple operation
    QrCode qr0 = QrCode.encodeText("Hello, world!", QrCode.Ecc.MEDIUM);
    BufferedImage img = qr0.toImage(4, 10);
    ImageIO.write(img, "png", new File("qr-code.png"));
    
    // Manual operation
    List<QrSegment> segs = QrSegment.makeSegments("3141592653589793238462643383");
    QrCode qr1 = QrCode.encodeSegments(segs, QrCode.Ecc.HIGH, 5, 5, 2, false);

JavaScript language:

    // Name abbreviated for the sake of these examples here
    var QRC = qrcodegen.QrCode;
    
    // Simple operation
    var qr0 = QRC.encodeText("Hello, world!", QRC.Ecc.MEDIUM);
    var svg = qr0.toSvgString(4);
    
    // Manual operation
    var segs = qrcodegen.QrSegment.makeSegments("3141592653589793238462643383");
    var qr1 = QRC.encodeSegments(segs, QRC.Ecc.HIGH, 5, 5, 2, false);

Python language:

    from qrcodegen import *
    
    # Simple operation
    qr0 = QrCode.encode_text("Hello, world!", QrCode.Ecc.MEDIUM)
    svg = qr0.to_svg_str(4)
    
    # Manual operation
    segs = QrSegment.make_segments("3141592653589793238462643383")
    qr1 = QrCode.encode_segments(segs, QrCode.Ecc.HIGH, 5, 5, 2, False)

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
    QrCode qr1 = QrCode::encodeSegments(segs, QrCode::Ecc::HIGH, 5, 5, 2, false);


License
-------

Copyright © 2016 Project Nayuki  
[https://www.nayuki.io/page/qr-code-generator-library](https://www.nayuki.io/page/qr-code-generator-library)

(MIT License)

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
