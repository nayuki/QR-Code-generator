/* 
 * QR Code generator demo (JavaScript)
 * 
 * Copyright (c) 2016 Project Nayuki
 * https://www.nayuki.io/page/qr-code-generator-library
 * 
 * (MIT License)
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * - The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 * - The Software is provided "as is", without warranty of any kind, express or
 *   implied, including but not limited to the warranties of merchantability,
 *   fitness for a particular purpose and noninfringement. In no event shall the
 *   authors or copyright holders be liable for any claim, damages or other
 *   liability, whether in an action of contract, tort or otherwise, arising from,
 *   out of or in connection with the Software or the use or other dealings in the
 *   Software.
 */

"use strict";


function redrawQrCode() {
	// Returns a QrCode.Ecc object based on the radio buttons in the HTML form.
	function getInputErrorCorrectionLevel() {
		if (document.getElementById("errcorlvl-medium").checked)
			return qrcodegen.QrCode.Ecc.MEDIUM;
		else if (document.getElementById("errcorlvl-quartile").checked)
			return qrcodegen.QrCode.Ecc.QUARTILE;
		else if (document.getElementById("errcorlvl-high").checked)
			return qrcodegen.QrCode.Ecc.HIGH;
		else  // In case no radio button is depressed
			return qrcodegen.QrCode.Ecc.LOW;
	}
	
	// Get form inputs and compute QR Code
	var ecl = getInputErrorCorrectionLevel();
	var text = document.getElementById("text-input").value;
	var segs = qrcodegen.QrSegment.makeSegments(text);
	var qr = qrcodegen.encodeSegments(segs, ecl);
	
	// Get scale and border
	var scale = parseInt(document.getElementById("scale-input").value, 10);
	var border = parseInt(document.getElementById("border-input").value, 10);
	if (scale <= 0 || border < 0 || scale > 30 || border > 100)
		return;
	
	// Draw QR Code onto canvas
	var canvas = document.getElementById("qrcode-canvas");
	var width = (qr.getSize() + border * 2) * scale;
	if (canvas.width != width) {
		canvas.width = width;
		canvas.height = width;
	}
	var ctx = canvas.getContext("2d");
	for (var y = -border; y < qr.getSize() + border; y++) {
		for (var x = -border; x < qr.getSize() + border; x++) {
			ctx.fillStyle = qr.getModule(x, y) == 1 ? "#000000" : "#FFFFFF";
			ctx.fillRect((x + border) * scale, (y + border) * scale, scale, scale);
		}
	}
	
	
	// Returns a string to describe the given list of segments.
	function describeSegments(segs) {
		if (segs.length == 0)
			return "none";
		else if (segs.length == 1) {
			var mode = segs[0].getMode();
			var Mode = qrcodegen.QrSegment.Mode;
			if (mode == Mode.NUMERIC     )  return "numeric";
			if (mode == Mode.ALPHANUMERIC)  return "alphanumeric";
			if (mode == Mode.BYTE        )  return "byte";
			if (mode == Mode.KANJI       )  return "kanji";
			return "unknown";
		} else
			return "multiple";
	}
	
	// Returns the number of Unicode code points in the given UTF-16 string.
	function countUnicodeChars(str) {
		var result = 0;
		for (var i = 0; i < str.length; i++, result++) {
			var c = str.charCodeAt(i);
			if (c < 0xD800 || c >= 0xE000)
				continue;
			else if (0xD800 <= c && c < 0xDC00) {  // High surrogate
				i++;
				var d = str.charCodeAt(i);
				if (0xDC00 <= d && d < 0xE000)  // Low surrogate
					continue;
			}
			throw "Invalid UTF-16 string";
		}
		return result;
	}
	
	// Show the QR Code symbol's statistics as a string
	var stats = "QR Code version = " + qr.getVersion() + ", ";
	stats += "mask pattern = " + qr.getMask() + ", ";
	stats += "character count = " + countUnicodeChars(text) + ",\n";
	stats += "encoding mode = " + describeSegments(segs) + ", ";
	stats += "error correction = level " + "LMQH".charAt(qr.getErrorCorrectionLevel().ordinal) + ", ";
	stats += "data bits = " + qrcodegen.QrSegment.getTotalBits(segs, qr.getVersion()) + ".";
	var elem = document.getElementById("statistics-output");
	while (elem.firstChild != null)
		elem.removeChild(elem.firstChild);
	elem.appendChild(document.createTextNode(stats));
}


redrawQrCode();
