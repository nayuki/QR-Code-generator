QR Code generator library
=========================


Introduction
------------

This project aims to be the best, clearest QR Code generator library. The primary goals are flexible options and absolute correctness. Secondary goals are compact implementation size and good documentation comments.

Home page with live JavaScript demo, extensive descriptions, and competitor comparisons: https://www.nayuki.io/page/qr-code-generator-library


Features
--------

Core features:

* Available in 6 programming languages, all with nearly equal functionality: Java, JavaScript, Python, C++, C, Rust
* Significantly shorter code but more documentation comments compared to competing libraries
* Supports encoding all 40 versions (sizes) and all 4 error correction levels, as per the QR Code Model 2 standard
* Output formats: Raw modules/pixels of the QR symbol, SVG XML string
* Encodes numeric and special-alphanumeric text in less space than general text
* Open source code under the permissive MIT License

Manual parameters:

* User can specify minimum and maximum version numbers allowed, then library will automatically choose smallest version in the range that fits the data
* User can specify mask pattern manually, otherwise library will automatically evaluate all 8 masks and select the optimal one
* User can specify absolute error correction level, or allow the library to boost it if it doesn't increase the version number
* User can create a list of data segments manually and add ECI segments


Examples
--------

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

More complete set of examples: https://github.com/nayuki/QR-Code-generator/blob/master/rust/examples/qrcodegen-demo.rs .
