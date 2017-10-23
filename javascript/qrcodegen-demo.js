/* 
 * QR Code generator demo (JavaScript)
 * 
 * Copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/qr-code-generator-library
 * 
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
	// Show/hide rows based on bitmap/vector image output
	var bitmapOutput = document.getElementById("output-format-bitmap").checked;
	var scaleRow = document.getElementById("scale-row");
	var svgXmlRow = document.getElementById("svg-xml-row");
	if (bitmapOutput) {
		scaleRow.style.removeProperty("display");
		svgXmlRow.style.display = "none";
	} else {
		scaleRow.style.display = "none";
		svgXmlRow.style.removeProperty("display");
	}
	var svgXml = document.getElementById("svg-xml-output");
	svgXml.value = "";
	
	// Reset output images in case of early termination
	var canvas = document.getElementById("qrcode-canvas");
	var svg = document.getElementById("qrcode-svg");
	canvas.style.display = "none";
	svg.style.display = "none";
	
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
	var minVer = parseInt(document.getElementById("version-min-input").value, 10);
	var maxVer = parseInt(document.getElementById("version-max-input").value, 10);
	var mask = parseInt(document.getElementById("mask-input").value, 10);
	var boostEcc = document.getElementById("boost-ecc-input").checked;
	var qr = qrcodegen.QrCode.encodeSegments(segs, ecl, minVer, maxVer, mask, boostEcc);
	
	// Draw image output
	var border = parseInt(document.getElementById("border-input").value, 10);
	if (border < 0 || border > 100)
		return;
	if (bitmapOutput) {
		var scale = parseInt(document.getElementById("scale-input").value, 10);
		if (scale <= 0 || scale > 30)
			return;
		qr.drawCanvas(scale, border, canvas);
		canvas.style.removeProperty("display");
	} else {
		var code = qr.toSvgString(border);
		svg.setAttribute("viewBox", / viewBox="([^"]*)"/.exec(code)[1]);
		svg.querySelector("path").setAttribute("d", / d="([^"]*)"/.exec(code)[1]);
		svg.style.removeProperty("display");
		svgXml.value = qr.toSvgString(border);
	}
	
	
	// Returns a string to describe the given list of segments.
	function describeSegments(segs) {
		if (segs.length == 0)
			return "none";
		else if (segs.length == 1) {
			var mode = segs[0].mode;
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
	var stats = "QR Code version = " + qr.version + ", ";
	stats += "mask pattern = " + qr.mask + ", ";
	stats += "character count = " + countUnicodeChars(text) + ",\n";
	stats += "encoding mode = " + describeSegments(segs) + ", ";
	stats += "error correction = level " + "LMQH".charAt(qr.errorCorrectionLevel.ordinal) + ", ";
	stats += "data bits = " + qrcodegen.QrSegment.getTotalBits(segs, qr.version) + ".";
	var elem = document.getElementById("statistics-output");
	while (elem.firstChild != null)
		elem.removeChild(elem.firstChild);
	elem.appendChild(document.createTextNode(stats));
}


function handleVersionMinMax(which) {
	var minElem = document.getElementById("version-min-input");
	var maxElem = document.getElementById("version-max-input");
	var minVal = parseInt(minElem.value, 10);
	var maxVal = parseInt(maxElem.value, 10);
	minVal = Math.max(Math.min(minVal, qrcodegen.QrCode.MAX_VERSION), qrcodegen.QrCode.MIN_VERSION);
	maxVal = Math.max(Math.min(maxVal, qrcodegen.QrCode.MAX_VERSION), qrcodegen.QrCode.MIN_VERSION);
	if (which == "min" && minVal > maxVal)
		maxVal = minVal;
	else if (which == "max" && maxVal < minVal)
		minVal = maxVal;
	minElem.value = minVal.toString();
	maxElem.value = maxVal.toString();
	redrawQrCode();
}


redrawQrCode();
