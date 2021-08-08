# 
# QR Code generator Distutils script (Python)
# 
# Copyright (c) Project Nayuki. (MIT License)
# https://www.nayuki.io/page/qr-code-generator-library
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy of
# this software and associated documentation files (the "Software"), to deal in
# the Software without restriction, including without limitation the rights to
# use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
# the Software, and to permit persons to whom the Software is furnished to do so,
# subject to the following conditions:
# - The above copyright notice and this permission notice shall be included in
#   all copies or substantial portions of the Software.
# - The Software is provided "as is", without warranty of any kind, express or
#   implied, including but not limited to the warranties of merchantability,
#   fitness for a particular purpose and noninfringement. In no event shall the
#   authors or copyright holders be liable for any claim, damages or other
#   liability, whether in an action of contract, tort or otherwise, arising from,
#   out of or in connection with the Software or the use or other dealings in the
#   Software.
# 

import setuptools


setuptools.setup(
	name = "qrcodegen",
	description = "High quality QR Code generator library for Python",
	version = "1.7.0",
	platforms = "OS Independent",
	python_requires = '>=3',
	license = "MIT License",
	
	author = "Project Nayuki",
	author_email = "me@nayuki.io",
	url = "https://www.nayuki.io/page/qr-code-generator-library",
	
	classifiers = [
		"Development Status :: 5 - Production/Stable",
		"Intended Audience :: Developers",
		"Intended Audience :: Information Technology",
		"License :: OSI Approved :: MIT License",
		"Operating System :: OS Independent",
		"Programming Language :: Python",
		"Programming Language :: Python :: 3",
		"Topic :: Multimedia :: Graphics",
		"Topic :: Software Development :: Libraries :: Python Modules",
	],
	
	long_description = """=========================
QR Code generator library
=========================


Introduction
------------

This project aims to be the best, clearest QR Code generator library. The primary goals are flexible options and absolute correctness. Secondary goals are compact implementation size and good documentation comments.

Home page with live JavaScript demo, extensive descriptions, and competitor comparisons: https://www.nayuki.io/page/qr-code-generator-library


Features
--------

Core features:

* Available in 6 programming languages, all with nearly equal functionality: Java, TypeScript/JavaScript, Python, Rust, C++, C
* Significantly shorter code but more documentation comments compared to competing libraries
* Supports encoding all 40 versions (sizes) and all 4 error correction levels, as per the QR Code Model 2 standard
* Output format: Raw modules/pixels of the QR symbol
* Encodes numeric and special-alphanumeric text in less space than general text
* Open source code under the permissive MIT License

Manual parameters:

* User can specify minimum and maximum version numbers allowed, then library will automatically choose smallest version in the range that fits the data
* User can specify mask pattern manually, otherwise library will automatically evaluate all 8 masks and select the optimal one
* User can specify absolute error correction level, or allow the library to boost it if it doesn't increase the version number
* User can create a list of data segments manually and add ECI segments


Usage
-----

Install this package by downloading the source code ZIP file from PyPI_, or by running ``pip install qrcodegen``.

Examples:

::

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

More complete set of examples: https://github.com/nayuki/QR-Code-generator/blob/master/python/qrcodegen-demo.py .

API documentation is in the source file itself, with a summary comment at the top: https://github.com/nayuki/QR-Code-generator/blob/master/python/qrcodegen.py .

.. _PyPI: https://pypi.python.org/pypi/qrcodegen""",
	
	py_modules = ["qrcodegen"],
)
