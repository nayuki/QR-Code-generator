QR Code generator library - WebAssembly (WAT)
=============================================


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

```javascript
import {createQrCodeGenerator, Ecc} from "./qrcodegen.mjs";
const generator = await createQrCodeGenerator();

// Simple operation
const qr0 = generator.encodeText("Hello, world!", {ecl: Ecc.MEDIUM});
const svg = qr0.toSvgString(4);

// Manual operation
const segs = [generator.makeNumeric("3141592653589793238462643383")];
const qr1 = generator.encodeSegments(segs, {
  ecl: Ecc.HIGH, minVersion: 5, maxVersion: 5, mask: 2, boostEcl: false,
});
for (let y = 0; y < qr1.size; y++) {
  for (let x = 0; x < qr1.size; x++) {
    // Paint qr1.getModule(x, y).
  }
}
```

More complete set of examples: [qrcodegen-demo.mjs](qrcodegen-demo.mjs).

From this directory, build with `npm ci` and `npm run build`, run examples with
`npm run demo -- "Hello, world!"`, and run QR regression checks with `npm test`.

All QR encoding is implemented in `qrcodegen.wat`. The module has no imports.
The JavaScript interface handles UTF-8 conversion, memory access, and rendering.
For direct use from another host, the exported functions and memory layout are
documented in the WAT source. Nonempty caller buffers start at `get_input_ptr()`;
encoders return the symbol size, -1 for excessive data, or -2 for invalid arguments.
Kanji segments accept Shift JIS bytes; text encoding does not insert ECI.
