/* 
 * QR Code generator library (JavaScript)
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


/* 
 * Module "qrcodegen", public members:
 * - Class QrCode:
 *   - Function encodeText(str text, QrCode.Ecc ecl) -> QrCode
 *   - Function encodeBinary(list<byte> data, QrCode.Ecc ecl) -> QrCode
 *   - Function encodeSegments(list<QrSegment> segs, QrCode.Ecc ecl,
 *         int minVersion=1, int maxVersion=40, mask=-1, boostEcl=true) -> QrCode
 *   - Constants int MIN_VERSION, MAX_VERSION
 *   - Constructor QrCode(int version, QrCode.Ecc ecl, list<byte> dataCodewords, int mask)
 *   - Fields int version, size, mask
 *   - Field QrCode.Ecc errorCorrectionLevel
 *   - Method getModule(int x, int y) -> bool
 *   - Method drawCanvas(int scale, int border, HTMLCanvasElement canvas) -> void
 *   - Method toSvgString(int border) -> str
 *   - Enum Ecc:
 *     - Constants LOW, MEDIUM, QUARTILE, HIGH
 *     - Field int ordinal
 * - Class QrSegment:
 *   - Function makeBytes(list<byte> data) -> QrSegment
 *   - Function makeNumeric(str data) -> QrSegment
 *   - Function makeAlphanumeric(str data) -> QrSegment
 *   - Function makeSegments(str text) -> list<QrSegment>
 *   - Function makeEci(int assignVal) -> QrSegment
 *   - Constructor QrSegment(QrSegment.Mode mode, int numChars, list<int> bitData)
 *   - Field QrSegment.Mode mode
 *   - Field int numChars
 *   - Method getData() -> list<int>
 *   - Constants RegExp NUMERIC_REGEX, ALPHANUMERIC_REGEX
 *   - Enum Mode:
 *     - Constants NUMERIC, ALPHANUMERIC, BYTE, KANJI, ECI
 */
var qrcodegen = new function() {
	
	/*---- QR Code symbol class ----*/
	
	/* 
	 * A class that represents a QR Code symbol, which is a type of two-dimension barcode.
	 * Invented by Denso Wave and described in the ISO/IEC 18004 standard.
	 * Instances of this class represent an immutable square grid of black and white cells.
	 * The class provides static factory functions to create a QR Code from text or binary data.
	 * The class covers the QR Code Model 2 specification, supporting all versions (sizes)
	 * from 1 to 40, all 4 error correction levels, and 4 character encoding modes.
	 * 
	 * Ways to create a QR Code object:
	 * - High level: Take the payload data and call QrCode.encodeText() or QrCode.encodeBinary().
	 * - Mid level: Custom-make the list of segments and call QrCode.encodeSegments().
	 * - Low level: Custom-make the array of data codeword bytes (including
	 *   segment headers and final padding, excluding error correction codewords),
	 *   supply the appropriate version number, and call the QrCode() constructor.
	 * (Note that all ways require supplying the desired error correction level.)
	 * 
	 * This constructor creates a new QR Code with the given version number,
	 * error correction level, data codeword bytes, and mask number.
	 * This is a low-level API that most users should not use directly.
	 * A mid-level API is the encodeSegments() function.
	 */
	this.QrCode = function(version, errCorLvl, dataCodewords, mask) {
		
		/*---- Constructor (low level) ----*/
		
		// Check scalar arguments
		if (version < MIN_VERSION || version > MAX_VERSION)
			throw "Version value out of range";
		if (mask < -1 || mask > 7)
			throw "Mask value out of range";
		if (!(errCorLvl instanceof Ecc))
			throw "QrCode.Ecc expected";
		var size = version * 4 + 17;
		
		// Initialize both grids to be size*size arrays of Boolean false
		var row = [];
		for (var i = 0; i < size; i++)
			row.push(false);
		var modules    = [];  // Initially all white
		var isFunction = [];
		for (var i = 0; i < size; i++) {
			modules   .push(row.slice());
			isFunction.push(row.slice());
		}
		
		// Compute ECC, draw modules
		drawFunctionPatterns();
		var allCodewords = addEccAndInterleave(dataCodewords);
		drawCodewords(allCodewords);
		
		// Do masking
		if (mask == -1) {  // Automatically choose best mask
			var minPenalty = Infinity;
			for (var i = 0; i < 8; i++) {
				applyMask(i);
				drawFormatBits(i);
				var penalty = getPenaltyScore();
				if (penalty < minPenalty) {
					mask = i;
					minPenalty = penalty;
				}
				applyMask(i);  // Undoes the mask due to XOR
			}
		}
		if (mask < 0 || mask > 7)
			throw "Assertion error";
		applyMask(mask);  // Apply the final choice of mask
		drawFormatBits(mask);  // Overwrite old format bits
		
		isFunction = null;
		
		
		/*---- Read-only instance properties ----*/
		
		// The version number of this QR Code, which is between 1 and 40 (inclusive).
		// This determines the size of this barcode.
		Object.defineProperty(this, "version", {value:version});
		
		// The width and height of this QR Code, measured in modules, between
		// 21 and 177 (inclusive). This is equal to version * 4 + 17.
		Object.defineProperty(this, "size", {value:size});
		
		// The error correction level used in this QR Code.
		Object.defineProperty(this, "errorCorrectionLevel", {value:errCorLvl});
		
		// The index of the mask pattern used in this QR Code, which is between 0 and 7 (inclusive).
		// Even if a QR Code is created with automatic masking requested (mask = -1),
		// the resulting object still has a mask value between 0 and 7.
		Object.defineProperty(this, "mask", {value:mask});
		
		
		/*---- Accessor methods ----*/
		
		// Returns the color of the module (pixel) at the given coordinates, which is false
		// for white or true for black. The top left corner has the coordinates (x=0, y=0).
		// If the given coordinates are out of bounds, then false (white) is returned.
		this.getModule = function(x, y) {
			return 0 <= x && x < size && 0 <= y && y < size && modules[y][x];
		};
		
		
		/*---- Public instance methods ----*/
		
		// Draws this QR Code, with the given module scale and border modules, onto the given HTML
		// canvas element. The canvas's width and height is resized to (this.size + border * 2) * scale.
		// The drawn image is be purely black and white, and fully opaque.
		// The scale must be a positive integer and the border must be a non-negative integer.
		this.drawCanvas = function(scale, border, canvas) {
			if (scale <= 0 || border < 0)
				throw "Value out of range";
			var width = (size + border * 2) * scale;
			canvas.width = width;
			canvas.height = width;
			var ctx = canvas.getContext("2d");
			for (var y = -border; y < size + border; y++) {
				for (var x = -border; x < size + border; x++) {
					ctx.fillStyle = this.getModule(x, y) ? "#000000" : "#FFFFFF";
					ctx.fillRect((x + border) * scale, (y + border) * scale, scale, scale);
				}
			}
		};
		
		// Returns a string of SVG code for an image depicting this QR Code, with the given number
		// of border modules. The string always uses Unix newlines (\n), regardless of the platform.
		this.toSvgString = function(border) {
			if (border < 0)
				throw "Border must be non-negative";
			var parts = [];
			for (var y = 0; y < size; y++) {
				for (var x = 0; x < size; x++) {
					if (this.getModule(x, y))
						parts.push("M" + (x + border) + "," + (y + border) + "h1v1h-1z");
				}
			}
			return '<?xml version="1.0" encoding="UTF-8"?>\n' +
				'<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">\n' +
				'<svg xmlns="http://www.w3.org/2000/svg" version="1.1" viewBox="0 0 ' +
					(size + border * 2) + ' ' + (size + border * 2) + '" stroke="none">\n' +
				'\t<rect width="100%" height="100%" fill="#FFFFFF"/>\n' +
				'\t<path d="' + parts.join(" ") + '" fill="#000000"/>\n' +
				'</svg>\n';
		};
		
		
		/*---- Private helper methods for constructor: Drawing function modules ----*/
		
		// Reads this object's version field, and draws and marks all function modules.
		function drawFunctionPatterns() {
			// Draw horizontal and vertical timing patterns
			for (var i = 0; i < size; i++) {
				setFunctionModule(6, i, i % 2 == 0);
				setFunctionModule(i, 6, i % 2 == 0);
			}
			
			// Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
			drawFinderPattern(3, 3);
			drawFinderPattern(size - 4, 3);
			drawFinderPattern(3, size - 4);
			
			// Draw numerous alignment patterns
			var alignPatPos = getAlignmentPatternPositions();
			var numAlign = alignPatPos.length;
			for (var i = 0; i < numAlign; i++) {
				for (var j = 0; j < numAlign; j++) {
					// Don't draw on the three finder corners
					if (!(i == 0 && j == 0 || i == 0 && j == numAlign - 1 || i == numAlign - 1 && j == 0))
						drawAlignmentPattern(alignPatPos[i], alignPatPos[j]);
				}
			}
			
			// Draw configuration data
			drawFormatBits(0);  // Dummy mask value; overwritten later in the constructor
			drawVersion();
		}
		
		
		// Draws two copies of the format bits (with its own error correction code)
		// based on the given mask and this object's error correction level field.
		function drawFormatBits(mask) {
			// Calculate error correction code and pack bits
			var data = errCorLvl.formatBits << 3 | mask;  // errCorrLvl is uint2, mask is uint3
			var rem = data;
			for (var i = 0; i < 10; i++)
				rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
			var bits = (data << 10 | rem) ^ 0x5412;  // uint15
			if (bits >>> 15 != 0)
				throw "Assertion error";
			
			// Draw first copy
			for (var i = 0; i <= 5; i++)
				setFunctionModule(8, i, getBit(bits, i));
			setFunctionModule(8, 7, getBit(bits, 6));
			setFunctionModule(8, 8, getBit(bits, 7));
			setFunctionModule(7, 8, getBit(bits, 8));
			for (var i = 9; i < 15; i++)
				setFunctionModule(14 - i, 8, getBit(bits, i));
			
			// Draw second copy
			for (var i = 0; i < 8; i++)
				setFunctionModule(size - 1 - i, 8, getBit(bits, i));
			for (var i = 8; i < 15; i++)
				setFunctionModule(8, size - 15 + i, getBit(bits, i));
			setFunctionModule(8, size - 8, true);  // Always black
		}
		
		
		// Draws two copies of the version bits (with its own error correction code),
		// based on this object's version field, iff 7 <= version <= 40.
		function drawVersion() {
			if (version < 7)
				return;
			
			// Calculate error correction code and pack bits
			var rem = version;  // version is uint6, in the range [7, 40]
			for (var i = 0; i < 12; i++)
				rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
			var bits = version << 12 | rem;  // uint18
			if (bits >>> 18 != 0)
				throw "Assertion error";
			
			// Draw two copies
			for (var i = 0; i < 18; i++) {
				var bit = getBit(bits, i);
				var a = size - 11 + i % 3;
				var b = Math.floor(i / 3);
				setFunctionModule(a, b, bit);
				setFunctionModule(b, a, bit);
			}
		}
		
		
		// Draws a 9*9 finder pattern including the border separator,
		// with the center module at (x, y). Modules can be out of bounds.
		function drawFinderPattern(x, y) {
			for (var dy = -4; dy <= 4; dy++) {
				for (var dx = -4; dx <= 4; dx++) {
					var dist = Math.max(Math.abs(dx), Math.abs(dy));  // Chebyshev/infinity norm
					var xx = x + dx, yy = y + dy;
					if (0 <= xx && xx < size && 0 <= yy && yy < size)
						setFunctionModule(xx, yy, dist != 2 && dist != 4);
				}
			}
		}
		
		
		// Draws a 5*5 alignment pattern, with the center module
		// at (x, y). All modules must be in bounds.
		function drawAlignmentPattern(x, y) {
			for (var dy = -2; dy <= 2; dy++) {
				for (var dx = -2; dx <= 2; dx++)
					setFunctionModule(x + dx, y + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
			}
		}
		
		
		// Sets the color of a module and marks it as a function module.
		// Only used by the constructor. Coordinates must be in bounds.
		function setFunctionModule(x, y, isBlack) {
			modules[y][x] = isBlack;
			isFunction[y][x] = true;
		}
		
		
		/*---- Private helper methods for constructor: Codewords and masking ----*/
		
		// Returns a new byte string representing the given data with the appropriate error correction
		// codewords appended to it, based on this object's version and error correction level.
		function addEccAndInterleave(data) {
			if (data.length != QrCode.getNumDataCodewords(version, errCorLvl))
				throw "Invalid argument";
			
			// Calculate parameter numbers
			var numBlocks = QrCode.NUM_ERROR_CORRECTION_BLOCKS[errCorLvl.ordinal][version];
			var blockEccLen = QrCode.ECC_CODEWORDS_PER_BLOCK  [errCorLvl.ordinal][version];
			var rawCodewords = Math.floor(QrCode.getNumRawDataModules(version) / 8);
			var numShortBlocks = numBlocks - rawCodewords % numBlocks;
			var shortBlockLen = Math.floor(rawCodewords / numBlocks);
			
			// Split data into blocks and append ECC to each block
			var blocks = [];
			var rs = new ReedSolomonGenerator(blockEccLen);
			for (var i = 0, k = 0; i < numBlocks; i++) {
				var dat = data.slice(k, k + shortBlockLen - blockEccLen + (i < numShortBlocks ? 0 : 1));
				k += dat.length;
				var ecc = rs.getRemainder(dat);
				if (i < numShortBlocks)
					dat.push(0);
				blocks.push(dat.concat(ecc));
			}
			
			// Interleave (not concatenate) the bytes from every block into a single sequence
			var result = [];
			for (var i = 0; i < blocks[0].length; i++) {
				for (var j = 0; j < blocks.length; j++) {
					// Skip the padding byte in short blocks
					if (i != shortBlockLen - blockEccLen || j >= numShortBlocks)
						result.push(blocks[j][i]);
				}
			}
			if (result.length != rawCodewords)
				throw "Assertion error";
			return result;
		}
		
		
		// Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
		// data area of this QR Code. Function modules need to be marked off before this is called.
		function drawCodewords(data) {
			if (data.length != Math.floor(QrCode.getNumRawDataModules(version) / 8))
				throw "Invalid argument";
			var i = 0;  // Bit index into the data
			// Do the funny zigzag scan
			for (var right = size - 1; right >= 1; right -= 2) {  // Index of right column in each column pair
				if (right == 6)
					right = 5;
				for (var vert = 0; vert < size; vert++) {  // Vertical counter
					for (var j = 0; j < 2; j++) {
						var x = right - j;  // Actual x coordinate
						var upward = ((right + 1) & 2) == 0;
						var y = upward ? size - 1 - vert : vert;  // Actual y coordinate
						if (!isFunction[y][x] && i < data.length * 8) {
							modules[y][x] = getBit(data[i >>> 3], 7 - (i & 7));
							i++;
						}
						// If this QR Code has any remainder bits (0 to 7), they were assigned as
						// 0/false/white by the constructor and are left unchanged by this method
					}
				}
			}
			if (i != data.length * 8)
				throw "Assertion error";
		}
		
		
		// XORs the codeword modules in this QR Code with the given mask pattern.
		// The function modules must be marked and the codeword bits must be drawn
		// before masking. Due to the arithmetic of XOR, calling applyMask() with
		// the same mask value a second time will undo the mask. A final well-formed
		// QR Code needs exactly one (not zero, two, etc.) mask applied.
		function applyMask(mask) {
			if (mask < 0 || mask > 7)
				throw "Mask value out of range";
			for (var y = 0; y < size; y++) {
				for (var x = 0; x < size; x++) {
					var invert;
					switch (mask) {
						case 0:  invert = (x + y) % 2 == 0;                                  break;
						case 1:  invert = y % 2 == 0;                                        break;
						case 2:  invert = x % 3 == 0;                                        break;
						case 3:  invert = (x + y) % 3 == 0;                                  break;
						case 4:  invert = (Math.floor(x / 3) + Math.floor(y / 2)) % 2 == 0;  break;
						case 5:  invert = x * y % 2 + x * y % 3 == 0;                        break;
						case 6:  invert = (x * y % 2 + x * y % 3) % 2 == 0;                  break;
						case 7:  invert = ((x + y) % 2 + x * y % 3) % 2 == 0;                break;
						default:  throw "Assertion error";
					}
					if (!isFunction[y][x] && invert)
						modules[y][x] = !modules[y][x];
				}
			}
		}
		
		
		// Calculates and returns the penalty score based on state of this QR Code's current modules.
		// This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
		function getPenaltyScore() {
			var result = 0;
			
			// Adjacent modules in row having same color, and finder-like patterns
			for (var y = 0; y < size; y++) {
				var runHistory = [0,0,0,0,0,0,0];
				var color = false;
				var runX = 0;
				for (var x = 0; x < size; x++) {
					if (modules[y][x] == color) {
						runX++;
						if (runX == 5)
							result += QrCode.PENALTY_N1;
						else if (runX > 5)
							result++;
					} else {
						QrCode.addRunToHistory(runX, runHistory);
						if (!color && QrCode.hasFinderLikePattern(runHistory))
							result += QrCode.PENALTY_N3;
						color = modules[y][x];
						runX = 1;
					}
				}
				QrCode.addRunToHistory(runX, runHistory);
				if (color)
					QrCode.addRunToHistory(0, runHistory);  // Dummy run of white
				if (QrCode.hasFinderLikePattern(runHistory))
					result += QrCode.PENALTY_N3;
			}
			// Adjacent modules in column having same color, and finder-like patterns
			for (var x = 0; x < size; x++) {
				var runHistory = [0,0,0,0,0,0,0];
				var color = false;
				var runY = 0;
				for (var y = 0; y < size; y++) {
					if (modules[y][x] == color) {
						runY++;
						if (runY == 5)
							result += QrCode.PENALTY_N1;
						else if (runY > 5)
							result++;
					} else {
						QrCode.addRunToHistory(runY, runHistory);
						if (!color && QrCode.hasFinderLikePattern(runHistory))
							result += QrCode.PENALTY_N3;
						color = modules[y][x];
						runY = 1;
					}
				}
				QrCode.addRunToHistory(runY, runHistory);
				if (color)
					QrCode.addRunToHistory(0, runHistory);  // Dummy run of white
				if (QrCode.hasFinderLikePattern(runHistory))
					result += QrCode.PENALTY_N3;
			}
			
			// 2*2 blocks of modules having same color
			for (var y = 0; y < size - 1; y++) {
				for (var x = 0; x < size - 1; x++) {
					var   color = modules[y][x];
					if (  color == modules[y][x + 1] &&
					      color == modules[y + 1][x] &&
					      color == modules[y + 1][x + 1])
						result += QrCode.PENALTY_N2;
				}
			}
			
			// Balance of black and white modules
			var black = 0;
			modules.forEach(function(row) {
				row.forEach(function(color) {
					if (color)
						black++;
				});
			});
			var total = size * size;  // Note that size is odd, so black/total != 1/2
			// Compute the smallest integer k >= 0 such that (45-5k)% <= black/total <= (55+5k)%
			var k = Math.ceil(Math.abs(black * 20 - total * 10) / total) - 1;
			result += k * QrCode.PENALTY_N4;
			return result;
		}
		
		
		// Returns an ascending list of positions of alignment patterns for this version number.
		// Each position is in the range [0,177), and are used on both the x and y axes.
		// This could be implemented as lookup table of 40 variable-length lists of integers.
		function getAlignmentPatternPositions() {
			if (version == 1)
				return [];
			else {
				var numAlign = Math.floor(version / 7) + 2;
				var step = (version == 32) ? 26 :
					Math.ceil((size - 13) / (numAlign*2 - 2)) * 2;
				var result = [6];
				for (var pos = size - 7; result.length < numAlign; pos -= step)
					result.splice(1, 0, pos);
				return result;
			}
		}
		
		
		// Returns true iff the i'th bit of x is set to 1.
		function getBit(x, i) {
			return ((x >>> i) & 1) != 0;
		}
	};
	
	
	/*---- Static factory functions (high level) for QrCode ----*/
	
	/* 
	 * Returns a QR Code representing the given Unicode text string at the given error correction level.
	 * As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer
	 * Unicode code points (not UTF-16 code units) if the low error correction level is used. The smallest possible
	 * QR Code version is automatically chosen for the output. The ECC level of the result may be higher than the
	 * ecl argument if it can be done without increasing the version.
	 */
	this.QrCode.encodeText = function(text, ecl) {
		var segs = qrcodegen.QrSegment.makeSegments(text);
		return this.encodeSegments(segs, ecl);
	};
	
	
	/* 
	 * Returns a QR Code representing the given binary data at the given error correction level.
	 * This function always encodes using the binary segment mode, not any text mode. The maximum number of
	 * bytes allowed is 2953. The smallest possible QR Code version is automatically chosen for the output.
	 * The ECC level of the result may be higher than the ecl argument if it can be done without increasing the version.
	 */
	this.QrCode.encodeBinary = function(data, ecl) {
		var seg = qrcodegen.QrSegment.makeBytes(data);
		return this.encodeSegments([seg], ecl);
	};
	
	
	/*---- Static factory functions (mid level) for QrCode ----*/
	
	/* 
	 * Returns a QR Code representing the given segments with the given encoding parameters.
	 * The smallest possible QR Code version within the given range is automatically
	 * chosen for the output. Iff boostEcl is true, then the ECC level of the result
	 * may be higher than the ecl argument if it can be done without increasing the
	 * version. The mask number is either between 0 to 7 (inclusive) to force that
	 * mask, or -1 to automatically choose an appropriate mask (which may be slow).
	 * This function allows the user to create a custom sequence of segments that switches
	 * between modes (such as alphanumeric and byte) to encode text in less space.
	 * This is a mid-level API; the high-level API is encodeText() and encodeBinary().
	 */
	this.QrCode.encodeSegments = function(segs, ecl, minVersion, maxVersion, mask, boostEcl) {
		if (minVersion == undefined) minVersion = MIN_VERSION;
		if (maxVersion == undefined) maxVersion = MAX_VERSION;
		if (mask == undefined) mask = -1;
		if (boostEcl == undefined) boostEcl = true;
		if (!(MIN_VERSION <= minVersion && minVersion <= maxVersion && maxVersion <= MAX_VERSION) || mask < -1 || mask > 7)
			throw "Invalid value";
		
		// Find the minimal version number to use
		var version, dataUsedBits;
		for (version = minVersion; ; version++) {
			var dataCapacityBits = QrCode.getNumDataCodewords(version, ecl) * 8;  // Number of data bits available
			dataUsedBits = qrcodegen.QrSegment.getTotalBits(segs, version);
			if (dataUsedBits <= dataCapacityBits)
				break;  // This version number is found to be suitable
			if (version >= maxVersion)  // All versions in the range could not fit the given data
				throw "Data too long";
		}
		
		// Increase the error correction level while the data still fits in the current version number
		[this.Ecc.MEDIUM, this.Ecc.QUARTILE, this.Ecc.HIGH].forEach(function(newEcl) {  // From low to high
			if (boostEcl && dataUsedBits <= QrCode.getNumDataCodewords(version, newEcl) * 8)
				ecl = newEcl;
		});
		
		// Concatenate all segments to create the data bit string
		var bb = new BitBuffer();
		segs.forEach(function(seg) {
			bb.appendBits(seg.mode.modeBits, 4);
			bb.appendBits(seg.numChars, seg.mode.numCharCountBits(version));
			seg.getData().forEach(function(bit) {
				bb.push(bit);
			});
		});
		if (bb.length != dataUsedBits)
			throw "Assertion error";
		
		// Add terminator and pad up to a byte if applicable
		var dataCapacityBits = QrCode.getNumDataCodewords(version, ecl) * 8;
		if (bb.length > dataCapacityBits)
			throw "Assertion error";
		bb.appendBits(0, Math.min(4, dataCapacityBits - bb.length));
		bb.appendBits(0, (8 - bb.length % 8) % 8);
		if (bb.length % 8 != 0)
			throw "Assertion error";
		
		// Pad with alternating bytes until data capacity is reached
		for (var padByte = 0xEC; bb.length < dataCapacityBits; padByte ^= 0xEC ^ 0x11)
			bb.appendBits(padByte, 8);
		
		// Pack bits into bytes in big endian
		var dataCodewords = [];
		while (dataCodewords.length * 8 < bb.length)
			dataCodewords.push(0);
		bb.forEach(function(bit, i) {
			dataCodewords[i >>> 3] |= bit << (7 - (i & 7));
		});
		
		// Create the QR Code object
		return new this(version, ecl, dataCodewords, mask);
	};
	
	
	/*---- Private static helper functions for QrCode ----*/
	
	var QrCode = {};  // Private object to assign properties to. Not the same object as 'this.QrCode'.
	
	
	// Returns the number of data bits that can be stored in a QR Code of the given version number, after
	// all function modules are excluded. This includes remainder bits, so it might not be a multiple of 8.
	// The result is in the range [208, 29648]. This could be implemented as a 40-entry lookup table.
	QrCode.getNumRawDataModules = function(ver) {
		if (ver < MIN_VERSION || ver > MAX_VERSION)
			throw "Version number out of range";
		var result = (16 * ver + 128) * ver + 64;
		if (ver >= 2) {
			var numAlign = Math.floor(ver / 7) + 2;
			result -= (25 * numAlign - 10) * numAlign - 55;
			if (ver >= 7)
				result -= 36;
		}
		return result;
	};
	
	
	// Returns the number of 8-bit data (i.e. not error correction) codewords contained in any
	// QR Code of the given version number and error correction level, with remainder bits discarded.
	// This stateless pure function could be implemented as a (40*4)-cell lookup table.
	QrCode.getNumDataCodewords = function(ver, ecl) {
		return Math.floor(QrCode.getNumRawDataModules(ver) / 8) -
			QrCode.ECC_CODEWORDS_PER_BLOCK    [ecl.ordinal][ver] *
			QrCode.NUM_ERROR_CORRECTION_BLOCKS[ecl.ordinal][ver];
	};
	
	
	// Inserts the given value to the front of the given array, which shifts over the
	// existing values and deletes the last value. A helper function for getPenaltyScore().
	QrCode.addRunToHistory = function(run, history) {
		history.pop();
		history.unshift(run);
	};
	
	
	// Tests whether the given run history has the pattern of ratio 1:1:3:1:1 in the middle, and
	// surrounded by at least 4 on either or both ends. A helper function for getPenaltyScore().
	// Must only be called immediately after a run of white modules has ended.
	QrCode.hasFinderLikePattern = function(runHistory) {
		var n = runHistory[1];
		return n > 0 && runHistory[2] == n && runHistory[4] == n && runHistory[5] == n
			&& runHistory[3] == n * 3 && Math.max(runHistory[0], runHistory[6]) >= n * 4;
	};
	
	
	/*---- Constants and tables for QrCode ----*/
	
	var MIN_VERSION =  1;  // The minimum version number supported in the QR Code Model 2 standard
	var MAX_VERSION = 40;  // The maximum version number supported in the QR Code Model 2 standard
	Object.defineProperty(this.QrCode, "MIN_VERSION", {value:MIN_VERSION});
	Object.defineProperty(this.QrCode, "MAX_VERSION", {value:MAX_VERSION});
	
	// For use in getPenaltyScore(), when evaluating which mask is best.
	QrCode.PENALTY_N1 =  3;
	QrCode.PENALTY_N2 =  3;
	QrCode.PENALTY_N3 = 40;
	QrCode.PENALTY_N4 = 10;
	
	QrCode.ECC_CODEWORDS_PER_BLOCK = [
		// Version: (note that index 0 is for padding, and is set to an illegal value)
		//  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
		[null,  7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],  // Low
		[null, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28],  // Medium
		[null, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],  // Quartile
		[null, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],  // High
	];
	
	QrCode.NUM_ERROR_CORRECTION_BLOCKS = [
		// Version: (note that index 0 is for padding, and is set to an illegal value)
		//  0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
		[null, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4,  4,  4,  4,  4,  6,  6,  6,  6,  7,  8,  8,  9,  9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25],  // Low
		[null, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5,  5,  8,  9,  9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49],  // Medium
		[null, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8,  8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68],  // Quartile
		[null, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81],  // High
	];
	
	
	/*---- Public helper enumeration ----*/
	
	/* 
	 * The error correction level in a QR Code symbol. Immutable.
	 */
	this.QrCode.Ecc = {
		LOW     : new Ecc(0, 1),  // The QR Code can tolerate about  7% erroneous codewords
		MEDIUM  : new Ecc(1, 0),  // The QR Code can tolerate about 15% erroneous codewords
		QUARTILE: new Ecc(2, 3),  // The QR Code can tolerate about 25% erroneous codewords
		HIGH    : new Ecc(3, 2),  // The QR Code can tolerate about 30% erroneous codewords
	};
	
	
	// Private constructor.
	function Ecc(ord, fb) {
		// (Public) In the range 0 to 3 (unsigned 2-bit integer)
		Object.defineProperty(this, "ordinal", {value:ord});
		
		// (Package-private) In the range 0 to 3 (unsigned 2-bit integer)
		Object.defineProperty(this, "formatBits", {value:fb});
	}
	
	
	
	/*---- Data segment class ----*/
	
	/* 
	 * A segment of character/binary/control data in a QR Code symbol.
	 * Instances of this class are immutable.
	 * The mid-level way to create a segment is to take the payload data
	 * and call a static factory function such as QrSegment.makeNumeric().
	 * The low-level way to create a segment is to custom-make the bit buffer
	 * and call the QrSegment() constructor with appropriate values.
	 * This segment class imposes no length restrictions, but QR Codes have restrictions.
	 * Even in the most favorable conditions, a QR Code can only hold 7089 characters of data.
	 * Any segment longer than this is meaningless for the purpose of generating QR Codes.
	 * This constructor creates a QR Code segment with the given attributes and data.
	 * The character count (numChars) must agree with the mode and the bit buffer length,
	 * but the constraint isn't checked. The given bit buffer is cloned and stored.
	 */
	this.QrSegment = function(mode, numChars, bitData) {
		/*---- Constructor (low level) ----*/
		if (numChars < 0 || !(mode instanceof Mode))
			throw "Invalid argument";
		
		// The data bits of this segment. Accessed through getData().
		bitData = bitData.slice();  // Make defensive copy
		
		// The mode indicator of this segment.
		Object.defineProperty(this, "mode", {value:mode});
		
		// The length of this segment's unencoded data. Measured in characters for
		// numeric/alphanumeric/kanji mode, bytes for byte mode, and 0 for ECI mode.
		// Always zero or positive. Not the same as the data's bit length.
		Object.defineProperty(this, "numChars", {value:numChars});
		
		// Returns a new copy of the data bits of this segment.
		this.getData = function() {
			return bitData.slice();  // Make defensive copy
		};
	};
	
	
	/*---- Static factory functions (mid level) for QrSegment ----*/
	
	/* 
	 * Returns a segment representing the given binary data encoded in
	 * byte mode. All input byte arrays are acceptable. Any text string
	 * can be converted to UTF-8 bytes and encoded as a byte mode segment.
	 */
	this.QrSegment.makeBytes = function(data) {
		var bb = new BitBuffer();
		data.forEach(function(b) {
			bb.appendBits(b, 8);
		});
		return new this(this.Mode.BYTE, data.length, bb);
	};
	
	
	/* 
	 * Returns a segment representing the given string of decimal digits encoded in numeric mode.
	 */
	this.QrSegment.makeNumeric = function(digits) {
		if (!this.NUMERIC_REGEX.test(digits))
			throw "String contains non-numeric characters";
		var bb = new BitBuffer();
		for (var i = 0; i < digits.length; ) {  // Consume up to 3 digits per iteration
			var n = Math.min(digits.length - i, 3);
			bb.appendBits(parseInt(digits.substring(i, i + n), 10), n * 3 + 1);
			i += n;
		}
		return new this(this.Mode.NUMERIC, digits.length, bb);
	};
	
	
	/* 
	 * Returns a segment representing the given text string encoded in alphanumeric mode.
	 * The characters allowed are: 0 to 9, A to Z (uppercase only), space,
	 * dollar, percent, asterisk, plus, hyphen, period, slash, colon.
	 */
	this.QrSegment.makeAlphanumeric = function(text) {
		if (!this.ALPHANUMERIC_REGEX.test(text))
			throw "String contains unencodable characters in alphanumeric mode";
		var bb = new BitBuffer();
		var i;
		for (i = 0; i + 2 <= text.length; i += 2) {  // Process groups of 2
			var temp = QrSegment.ALPHANUMERIC_CHARSET.indexOf(text.charAt(i)) * 45;
			temp += QrSegment.ALPHANUMERIC_CHARSET.indexOf(text.charAt(i + 1));
			bb.appendBits(temp, 11);
		}
		if (i < text.length)  // 1 character remaining
			bb.appendBits(QrSegment.ALPHANUMERIC_CHARSET.indexOf(text.charAt(i)), 6);
		return new this(this.Mode.ALPHANUMERIC, text.length, bb);
	};
	
	
	/* 
	 * Returns a new mutable list of zero or more segments to represent the given Unicode text string.
	 * The result may use various segment modes and switch modes to optimize the length of the bit stream.
	 */
	this.QrSegment.makeSegments = function(text) {
		// Select the most efficient segment encoding automatically
		if (text == "")
			return [];
		else if (this.NUMERIC_REGEX.test(text))
			return [this.makeNumeric(text)];
		else if (this.ALPHANUMERIC_REGEX.test(text))
			return [this.makeAlphanumeric(text)];
		else
			return [this.makeBytes(toUtf8ByteArray(text))];
	};
	
	
	/* 
	 * Returns a segment representing an Extended Channel Interpretation
	 * (ECI) designator with the given assignment value.
	 */
	this.QrSegment.makeEci = function(assignVal) {
		var bb = new BitBuffer();
		if (assignVal < 0)
			throw "ECI assignment value out of range";
		else if (assignVal < (1 << 7))
			bb.appendBits(assignVal, 8);
		else if (assignVal < (1 << 14)) {
			bb.appendBits(2, 2);
			bb.appendBits(assignVal, 14);
		} else if (assignVal < 1000000) {
			bb.appendBits(6, 3);
			bb.appendBits(assignVal, 21);
		} else
			throw "ECI assignment value out of range";
		return new this(this.Mode.ECI, 0, bb);
	};
	
	
	// (Package-private) Calculates and returns the number of bits needed to encode the given segments at the
	// given version. The result is infinity if a segment has too many characters to fit its length field.
	this.QrSegment.getTotalBits = function(segs, version) {
		var result = 0;
		for (var i = 0; i < segs.length; i++) {
			var seg = segs[i];
			var ccbits = seg.mode.numCharCountBits(version);
			if (seg.numChars >= (1 << ccbits))
				return Infinity;  // The segment's length doesn't fit the field's bit width
			result += 4 + ccbits + seg.getData().length;
		}
		return result;
	};
	
	
	/*---- Constants for QrSegment ----*/
	
	var QrSegment = {};  // Private object to assign properties to. Not the same object as 'this.QrSegment'.
	
	// (Public) Describes precisely all strings that are encodable in numeric mode.
	// To test whether a string s is encodable: var ok = NUMERIC_REGEX.test(s);
	// A string is encodable iff each character is in the range 0 to 9.
	this.QrSegment.NUMERIC_REGEX = /^[0-9]*$/;
	
	// (Public) Describes precisely all strings that are encodable in alphanumeric mode.
	// To test whether a string s is encodable: var ok = ALPHANUMERIC_REGEX.test(s);
	// A string is encodable iff each character is in the following set: 0 to 9, A to Z
	// (uppercase only), space, dollar, percent, asterisk, plus, hyphen, period, slash, colon.
	this.QrSegment.ALPHANUMERIC_REGEX = /^[A-Z0-9 $%*+.\/:-]*$/;
	
	// (Private) The set of all legal characters in alphanumeric mode,
	// where each character value maps to the index in the string.
	QrSegment.ALPHANUMERIC_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";
	
	
	/*---- Public helper enumeration ----*/
	
	/* 
	 * Describes how a segment's data bits are interpreted. Immutable.
	 */
	this.QrSegment.Mode = {  // Constants
		NUMERIC     : new Mode(0x1, [10, 12, 14]),
		ALPHANUMERIC: new Mode(0x2, [ 9, 11, 13]),
		BYTE        : new Mode(0x4, [ 8, 16, 16]),
		KANJI       : new Mode(0x8, [ 8, 10, 12]),
		ECI         : new Mode(0x7, [ 0,  0,  0]),
	};
	
	
	// Private constructor.
	function Mode(mode, ccbits) {
		// (Package-private) The mode indicator bits, which is a uint4 value (range 0 to 15).
		Object.defineProperty(this, "modeBits", {value:mode});
		
		// (Package-private) Returns the bit width of the character count field for a segment in
		// this mode in a QR Code at the given version number. The result is in the range [0, 16].
		this.numCharCountBits = function(ver) {
			return ccbits[Math.floor((ver + 7) / 17)];
		};
	}
	
	
	
	/*---- Private helper functions and classes ----*/
	
	// Returns a new array of bytes representing the given string encoded in UTF-8.
	function toUtf8ByteArray(str) {
		str = encodeURI(str);
		var result = [];
		for (var i = 0; i < str.length; i++) {
			if (str.charAt(i) != "%")
				result.push(str.charCodeAt(i));
			else {
				result.push(parseInt(str.substring(i + 1, i + 3), 16));
				i += 2;
			}
		}
		return result;
	}
	
	
	
	/* 
	 * A private helper class that computes the Reed-Solomon error correction codewords for a sequence of
	 * data codewords at a given degree. Objects are immutable, and the state only depends on the degree.
	 * This class exists because each data block in a QR Code shares the same the divisor polynomial.
	 * This constructor creates a Reed-Solomon ECC generator for the given degree. This could be implemented
	 * as a lookup table over all possible parameter values, instead of as an algorithm.
	 */
	function ReedSolomonGenerator(degree) {
		if (degree < 1 || degree > 255)
			throw "Degree out of range";
		
		// Coefficients of the divisor polynomial, stored from highest to lowest power, excluding the leading term which
		// is always 1. For example the polynomial x^3 + 255x^2 + 8x + 93 is stored as the uint8 array {255, 8, 93}.
		var coefficients = [];
		
		// Start with the monomial x^0
		for (var i = 0; i < degree - 1; i++)
			coefficients.push(0);
		coefficients.push(1);
		
		// Compute the product polynomial (x - r^0) * (x - r^1) * (x - r^2) * ... * (x - r^{degree-1}),
		// drop the highest term, and store the rest of the coefficients in order of descending powers.
		// Note that r = 0x02, which is a generator element of this field GF(2^8/0x11D).
		var root = 1;
		for (var i = 0; i < degree; i++) {
			// Multiply the current product by (x - r^i)
			for (var j = 0; j < coefficients.length; j++) {
				coefficients[j] = ReedSolomonGenerator.multiply(coefficients[j], root);
				if (j + 1 < coefficients.length)
					coefficients[j] ^= coefficients[j + 1];
			}
			root = ReedSolomonGenerator.multiply(root, 0x02);
		}
		
		// Computes and returns the Reed-Solomon error correction codewords for the given
		// sequence of data codewords. The returned object is always a new byte array.
		// This method does not alter this object's state (because it is immutable).
		this.getRemainder = function(data) {
			// Compute the remainder by performing polynomial division
			var result = coefficients.map(function() { return 0; });
			data.forEach(function(b) {
				var factor = b ^ result.shift();
				result.push(0);
				coefficients.forEach(function(coef, i) {
					result[i] ^= ReedSolomonGenerator.multiply(coef, factor);
				});
			});
			return result;
		};
	}
	
	// This static function returns the product of the two given field elements modulo GF(2^8/0x11D). The arguments and
	// result are unsigned 8-bit integers. This could be implemented as a lookup table of 256*256 entries of uint8.
	ReedSolomonGenerator.multiply = function(x, y) {
		if (x >>> 8 != 0 || y >>> 8 != 0)
			throw "Byte out of range";
		// Russian peasant multiplication
		var z = 0;
		for (var i = 7; i >= 0; i--) {
			z = (z << 1) ^ ((z >>> 7) * 0x11D);
			z ^= ((y >>> i) & 1) * x;
		}
		if (z >>> 8 != 0)
			throw "Assertion error";
		return z;
	};
	
	
	
	/* 
	 * A private helper class that represents an appendable sequence of bits (0s and 1s).
	 * Mainly used by QrSegment. This constructor creates an empty bit buffer (length 0).
	 */
	function BitBuffer() {
		Array.call(this);
		
		// Appends the given number of low-order bits of the given value
		// to this buffer. Requires 0 <= len <= 31 and 0 <= val < 2^len.
		this.appendBits = function(val, len) {
			if (len < 0 || len > 31 || val >>> len != 0)
				throw "Value out of range";
			for (var i = len - 1; i >= 0; i--)  // Append bit by bit
				this.push((val >>> i) & 1);
		};
	}
	
	BitBuffer.prototype = Object.create(Array.prototype);
	BitBuffer.prototype.constructor = BitBuffer;
	
};
