QR Code generator library - Python
==================================


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

```python
from qrcodegen import *

# Simple operation
qr0 = QrCode.encode_text("Hello, world!", QrCode.Ecc.MEDIUM)
svg = to_svg_str(qr0, 4)  # See qrcodegen-demo

# Manual operation
segs = QrSegment.make_segments("3141592653589793238462643383")
qr1 = QrCode.encode_segments(segs, QrCode.Ecc.HIGH, 5, 5, 2, False)
for y in range(qr1.get_size()):
    for x in range(qr1.get_size()):
        (... paint qr1.get_module(x, y) ...)
```

More complete set of examples: https://github.com/nayuki/QR-Code-generator/blob/master/python/qrcodegen-demo.py .
