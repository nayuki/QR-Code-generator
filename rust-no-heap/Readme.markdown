QR Code generator library - Rust, no heap
=========================================


Introduction
------------

This project aims to be the best, clearest QR Code generator library. The primary goals are flexible options and absolute correctness. Secondary goals are compact implementation size and good documentation comments.

Home page with live JavaScript demo, extensive descriptions, and competitor comparisons: https://www.nayuki.io/page/qr-code-generator-library


Features
--------

Core features:

* Significantly shorter code but more documentation comments compared to competing libraries
* Supports encoding all 40 versions (sizes) and all 4 error correction levels, as per the QR Code Model 2 standard
* Output format: Raw modules/pixels of the QR symbol
* Detects finder-like penalty patterns more accurately than other implementations
* Encodes numeric and special-alphanumeric text in less space than general text
* Completely avoids heap allocation (e.g. `std::vec::Vec`), instead relying on suitably sized buffers from the caller and fixed-size stack allocations
* Open-source code under the permissive MIT License

Manual parameters:

* User can specify minimum and maximum version numbers allowed, then library will automatically choose smallest version in the range that fits the data
* User can specify mask pattern manually, otherwise library will automatically evaluate all 8 masks and select the optimal one
* User can specify absolute error correction level, or allow the library to boost it if it doesn't increase the version number
* User can create a list of data segments manually and add ECI segments

More information about QR Code technology and this library's design can be found on the project home page.


Examples
--------

```rust
extern crate qrcodegen;
use qrcodegen::Mask;
use qrcodegen::QrCode;
use qrcodegen::QrCodeEcc;
use qrcodegen::Version;

// Text data
let mut outbuffer  = vec![0u8; Version::MAX.buffer_len()];
let mut tempbuffer = vec![0u8; Version::MAX.buffer_len()];
let qr = QrCode::encode_text("Hello, world!",
    &mut tempbuffer, &mut outbuffer, QrCodeEcc::Medium,
    Version::MIN, Version::MAX, None, true).unwrap();
let svg = to_svg_string(&qr, 4);  // See qrcodegen-demo

// Binary data
let mut outbuffer   = vec![0u8; Version::MAX.buffer_len()];
let mut dataandtemp = vec![0u8; Version::MAX.buffer_len()];
dataandtemp[0] = 0xE3;
dataandtemp[1] = 0x81;
dataandtemp[2] = 0x82;
let qr = QrCode::encode_binary(&mut dataandtemp, 3,
    &mut outbuffer, QrCodeEcc::High,
    Version::new(2), Version::new(7),
    Some(Mask::new(4)), false).unwrap();
for y in 0 .. qr.size() {
    for x in 0 .. qr.size() {
        (... paint qr.get_module(x, y) ...)
    }
}
```

More complete set of examples: https://github.com/nayuki/QR-Code-generator/blob/master/rust-no-heap/examples/qrcodegen-demo.rs .
