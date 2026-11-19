QR Code generator library - Nim
===============================


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
* Open-source code under the permissive MIT License

Manual parameters:

* User can specify minimum and maximum version numbers allowed, then library will automatically choose smallest version in the range that fits the data
* User can specify mask pattern manually, otherwise library will automatically evaluate all 8 masks and select the optimal one
* User can specify absolute error correction level, or allow the library to boost it if it doesn't increase the version number
* User can create a list of data segments manually and add ECI segments

More information about QR Code technology and this library's design can be found on the project home page.


Examples
--------

```nim
import qrcodegen
import qrcodegen_demo

# Simple operation
let qr0 = encodeText("Hello, world!", Ecc.medium)
let svg = qr0.toSvgString(4)

# Manual operation
let segs = makeSegments("3141592653589793238462643383")
let qr1 = encodeSegments(segs, Ecc.high,
  minVersion = 5, maxVersion = 5, mask = 2, boostEcl = false)
for y in 0..<qr1.size:
  for x in 0..<qr1.size:
    discard qr1.getModule(x, y)  # Paint this module.
```

More complete set of examples: [qrcodegen_demo.nim](qrcodegen_demo.nim).

From this directory, `nim build`, `nim buildRelease`, and `nim demo`
compile or run the demo. `nim test` runs a small set of QR and SVG checks;
`nim bench` times the encoder, and `nim clean` removes generated build output.

Package metadata is compatible with Atlas; there are no external dependencies.
Kanji segments accept Shift JIS bytes. Unicode-to-Shift-JIS conversion and optimal
mixed-mode segmentation are left to the caller. Text encoding does not insert ECI.
