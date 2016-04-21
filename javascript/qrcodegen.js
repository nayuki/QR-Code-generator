/* 
 * QR Code generator library (JavaScript)
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


/* 
 * Module "qrcodegen", public members:
 * - Class QrCode:
 *   - Function encodeText(str text, QrCode.Ecc ecl) -> QrCode
 *   - Function encodeBinary(list<int> data, QrCode.Ecc ecl) -> QrCode
 *   - Function encodeSegments(list<QrSegment> segs, QrCode.Ecc ecl,
 *         int minVersion=1, int maxVersion=40, mask=-1, boostEcl=true) -> QrCode
 *   - Constructor QrCode(QrCode qr, int mask)
 *   - Constructor QrCode(list<int> datacodewords, int mask, int version, QrCode.Ecc ecl)
 *   - Fields int version, size, mask
 *   - Field QrCode.Ecc errorCorrectionLevel
 *   - Method getModule(int x, int y) -> int
 *   - Method toSvgString(int border) -> str
 *   - Enum Ecc:
 *     - Constants LOW, MEDIUM, QUARTILE, HIGH
 *     - Field int ordinal
 * - Class QrSegment:
 *   - Function makeBytes(list<int> data) -> QrSegment
 *   - Function makeNumeric(str data) -> QrSegment
 *   - Function makeAlphanumeric(str data) -> QrSegment
 *   - Function makeSegments(str text) -> list<QrSegment>
 *   - Constructor QrSegment(QrSegment.Mode mode, int numChars, list<int> bitData)
 *   - Field QrSegment.Mode mode
 *   - Field int numChars
 *   - Method getBits() -> list<int>
 *   - Constants RegExp NUMERIC_REGEX, ALPHANUMERIC_REGEX
 *   - Enum Mode:
 *     - Constants NUMERIC, ALPHANUMERIC, BYTE, KANJI
 */
var qrcodegen = new function() {
	
	/*---- QR Code symbol class ----*/
	
	/* 
	 * A class that represents an immutable square grid of black and white cells for a QR Code symbol,
	 * with associated static functions to create a QR Code from user-supplied textual or binary data.
	 * This class covers the QR Code model 2 specification, supporting all versions (sizes)
	 * from 1 to 40, all 4 error correction levels.
	 * 
	 * This constructor can be called in one of two ways:
	 * - new QrCode(datacodewords, mask, version, errCorLvl):
	 *       Creates a new QR Code symbol with the given version number, error correction level, binary data array,
	 *       and mask number. This cumbersome constructor can be invoked directly by the user, but is considered
	 *       to be even lower level than QrCode.encodeSegments().
	 * - new QrCode(qr, mask):
	 *       Creates a new QR Code symbol based on the given existing object, but with a potentially different
	 *       mask pattern. The version, error correction level, codewords, etc. of the newly created object are
	 *       all identical to the argument object; only the mask may differ.
	 * In both cases, mask = -1 is for automatic choice or 0 to 7 for fixed choice.
	 */
	this.QrCode = function(initData, mask, version, errCorLvl) {
		
		/*---- Constructor ----*/
		
		// Check arguments and handle simple scalar fields
		if (mask < -1 || mask > 7)
			throw "Mask value out of range";
		if (initData instanceof Array) {
			if (version < 1 || version > 40)
				throw "Version value out of range";
		} else if (initData instanceof qrcodegen.QrCode) {
			if (version != undefined || errCorLvl != undefined)
				throw "Values must be undefined";
			version = initData.version;
			errCorLvl = initData.errorCorrectionLevel;
		} else
			throw "Invalid initial data";
		var size = version * 4 + 17;
		
		// Initialize both grids to be size*size arrays of Boolean false
		var row = [];
		for (var i = 0; i < size; i++)
			row.push(false);
		var modules = [];
		var isFunction = [];
		for (var i = 0; i < size; i++) {
			modules.push(row.slice());
			isFunction.push(row.slice());
		}
		
		// Handle grid fields
		if (initData instanceof Array) {
			// Draw function patterns, draw all codewords
			drawFunctionPatterns();
			var allCodewords = appendErrorCorrection(initData);
			drawCodewords(allCodewords);
		} else if (initData instanceof qrcodegen.QrCode) {
			for (var y = 0; y < size; y++) {
				for (var x = 0; x < size; x++) {
					modules[y][x] = initData.getModule(x, y) == 1;
					isFunction[y][x] = initData.isFunctionModule(x, y);
				}
			}
			applyMask(initData.mask);  // Undo old mask
		} else
			throw "Invalid initial data";
		
		// Handle masking
		if (mask == -1) {  // Automatically choose best mask
			var minPenalty = Infinity;
			for (var i = 0; i < 8; i++) {
				drawFormatBits(i);
				applyMask(i);
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
		drawFormatBits(mask);  // Overwrite old format bits
		applyMask(mask);  // Apply the final choice of mask
		
		
		/*---- Read-only instance properties ----*/
		
		// This QR Code symbol's version number, which is always between 1 and 40 (inclusive).
		Object.defineProperty(this, "version", {value:version});
		
		// The width and height of this QR Code symbol, measured in modules.
		// Always equal to version * 4 + 17, in the range 21 to 177.
		Object.defineProperty(this, "size", {value:size});
		
		// The error correction level used in this QR Code symbol.
		
		Object.defineProperty(this, "errorCorrectionLevel", {value:errCorLvl});
		
		// The mask pattern used in this QR Code symbol, in the range 0 to 7 (i.e. unsigned 3-bit integer).
		// Note that even if the constructor was called with automatic masking requested
		// (mask = -1), the resulting object will still have a mask value between 0 and 7.
		Object.defineProperty(this, "mask", {value:mask});
		
		
		/*---- Accessor methods ----*/
		
		// (Public) Returns the color of the module (pixel) at the given coordinates, which is either 0 for white or 1 for black. The top
		// left corner has the coordinates (x=0, y=0). If the given coordinates are out of bounds, then 0 (white) is returned.
		this.getModule = function(x, y) {
			if (0 <= x && x < size && 0 <= y && y < size)
				return modules[y][x] ? 1 : 0;
			else
				return 0;  // Infinite white border
		};
		
		// (Package-private) Tests whether the module at the given coordinates is a function module (true) or not (false).
		// The top left corner has the coordinates (x=0, y=0). If the given coordinates are out of bounds, then false is returned.
		// The JavaScript version of this library has this method because it is impossible to access private variables of another object.
		this.isFunctionModule = function(x, y) {
			if (0 <= x && x < size && 0 <= y && y < size)
				return isFunction[y][x];
			else
				return false;  // Infinite border
		};
		
		
		/*---- Public instance methods ----*/
		
		// Based on the given number of border modules to add as padding, this returns a
		// string whose contents represents an SVG XML file that depicts this QR Code symbol.
		this.toSvgString = function(border) {
			if (border < 0)
				throw "Border must be non-negative";
			var result = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
			result += "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n";
			result += "<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 " + (size + border * 2) + " " + (size + border * 2) + "\">\n";
			result += "\t<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\" stroke-width=\"0\"/>\n";
			result += "\t<path d=\"";
			var head = true;
			for (var y = -border; y < size + border; y++) {
				for (var x = -border; x < size + border; x++) {
					if (this.getModule(x, y) == 1) {
						if (head)
							head = false;
						else
							result += " ";
						result += "M" + (x + border) + "," + (y + border) + "h1v1h-1z";
					}
				}
			}
			result += "\" fill=\"#000000\" stroke-width=\"0\"/>\n";
			result += "</svg>\n";
			return result;
		};
		
		
		/*---- Private helper methods for constructor: Drawing function modules ----*/
		
		function drawFunctionPatterns() {
			// Draw the horizontal and vertical timing patterns
			for (var i = 0; i < size; i++) {
				setFunctionModule(6, i, i % 2 == 0);
				setFunctionModule(i, 6, i % 2 == 0);
			}
			
			// Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
			drawFinderPattern(3, 3);
			drawFinderPattern(size - 4, 3);
			drawFinderPattern(3, size - 4);
			
			// Draw the numerous alignment patterns
			var alignPatPos = QrCode.getAlignmentPatternPositions(version);
			var numAlign = alignPatPos.length;
			for (var i = 0; i < numAlign; i++) {
				for (var j = 0; j < numAlign; j++) {
					if (i == 0 && j == 0 || i == 0 && j == numAlign - 1 || i == numAlign - 1 && j == 0)
						continue;  // Skip the three finder corners
					else
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
			data = data << 10 | rem;
			data ^= 0x5412;  // uint15
			if (data >>> 15 != 0)
				throw "Assertion error";
			
			// Draw first copy
			for (var i = 0; i <= 5; i++)
				setFunctionModule(8, i, ((data >>> i) & 1) != 0);
			setFunctionModule(8, 7, ((data >>> 6) & 1) != 0);
			setFunctionModule(8, 8, ((data >>> 7) & 1) != 0);
			setFunctionModule(7, 8, ((data >>> 8) & 1) != 0);
			for (var i = 9; i < 15; i++)
				setFunctionModule(14 - i, 8, ((data >>> i) & 1) != 0);
			
			// Draw second copy
			for (var i = 0; i <= 7; i++)
				setFunctionModule(size - 1 - i, 8, ((data >>> i) & 1) != 0);
			for (var i = 8; i < 15; i++)
				setFunctionModule(8, size - 15 + i, ((data >>> i) & 1) != 0);
			setFunctionModule(8, size - 8, true);
		}
		
		
		// Draws two copies of the version bits (with its own error correction code),
		// based on this object's version field (which only has an effect for 7 <= version <= 40).
		function drawVersion() {
			if (version < 7)
				return;
			
			// Calculate error correction code and pack bits
			var rem = version;  // version is uint6, in the range [7, 40]
			for (var i = 0; i < 12; i++)
				rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
			var data = version << 12 | rem;  // uint18
			if (data >>> 18 != 0)
				throw "Assertion error";
			
			// Draw two copies
			for (var i = 0; i < 18; i++) {
				var bit = ((data >>> i) & 1) != 0;
				var a = size - 11 + i % 3, b = Math.floor(i / 3);
				setFunctionModule(a, b, bit);
				setFunctionModule(b, a, bit);
			}
		}
		
		
		// Draws a 9*9 finder pattern including the border separator, with the center module at (x, y).
		function drawFinderPattern(x, y) {
			for (var i = -4; i <= 4; i++) {
				for (var j = -4; j <= 4; j++) {
					var dist = Math.max(Math.abs(i), Math.abs(j));  // Chebyshev/infinity norm
					var xx = x + j, yy = y + i;
					if (0 <= xx && xx < size && 0 <= yy && yy < size)
						setFunctionModule(xx, yy, dist != 2 && dist != 4);
				}
			}
		}
		
		
		// Draws a 5*5 alignment pattern, with the center module at (x, y).
		function drawAlignmentPattern(x, y) {
			for (var i = -2; i <= 2; i++) {
				for (var j = -2; j <= 2; j++)
					setFunctionModule(x + j, y + i, Math.max(Math.abs(i), Math.abs(j)) != 1);
			}
		}
		
		
		// Sets the color of a module and marks it as a function module.
		// Only used by the constructor. Coordinates must be in range.
		function setFunctionModule(x, y, isBlack) {
			modules[y][x] = isBlack;
			isFunction[y][x] = true;
		}
		
		
		/*---- Private helper methods for constructor: Codewords and masking ----*/
		
		// Returns a new byte string representing the given data with the appropriate error correction
		// codewords appended to it, based on this object's version and error correction level.
		function appendErrorCorrection(data) {
			if (data.length != QrCode.getNumDataCodewords(version, errCorLvl))
				throw "Invalid argument";
			
			// Calculate parameter numbers
			var numBlocks = QrCode.NUM_ERROR_CORRECTION_BLOCKS[errCorLvl.ordinal][version];
			var totalEcc = QrCode.NUM_ERROR_CORRECTION_CODEWORDS[errCorLvl.ordinal][version];
			if (totalEcc % numBlocks != 0)
				throw "Assertion error";
			var blockEccLen = Math.floor(totalEcc / numBlocks);
			var numShortBlocks = numBlocks - Math.floor(QrCode.getNumRawDataModules(version) / 8) % numBlocks;
			var shortBlockLen = Math.floor(QrCode.getNumRawDataModules(version) / (numBlocks * 8));
			
			// Split data into blocks and append ECC to each block
			var blocks = [];
			var rs = new ReedSolomonGenerator(blockEccLen);
			for (var i = 0, k = 0; i < numBlocks; i++) {
				var dat = data.slice(k, k + shortBlockLen - blockEccLen + (i < numShortBlocks ? 0 : 1));
				k += dat.length;
				var ecc = rs.getRemainder(dat);
				if (i < numShortBlocks)
					dat.push(0);
				ecc.forEach(function(b) {
					dat.push(b);
				});
				blocks.push(dat);
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
			if (result.length != Math.floor(QrCode.getNumRawDataModules(version) / 8))
				throw "Assertion error";
			return result;
		}
		
		
		// Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
		// data area of this QR Code symbol. Function modules need to be marked off before this is called.
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
						var upwards = ((right & 2) == 0) ^ (x < 6);
						var y = upwards ? size - 1 - vert : vert;  // Actual y coordinate
						if (!isFunction[y][x] && i < data.length * 8) {
							modules[y][x] = ((data[i >>> 3] >>> (7 - (i & 7))) & 1) != 0;
							i++;
						}
					}
				}
			}
			if (i != data.length * 8)
				throw "Assertion error";
		}
		
		
		// XORs the data modules in this QR Code with the given mask pattern. Due to XOR's mathematical
		// properties, calling applyMask(m) twice with the same value is equivalent to no change at all.
		// This means it is possible to apply a mask, undo it, and try another mask. Note that a final
		// well-formed QR Code symbol needs exactly one mask applied (not zero, not two, etc.).
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
					modules[y][x] ^= invert & !isFunction[y][x];
				}
			}
		}
		
		
		// Calculates and returns the penalty score based on state of this QR Code's current modules.
		// This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
		function getPenaltyScore() {
			var result = 0;
			
			// Adjacent modules in row having same color
			for (var y = 0; y < size; y++) {
				var colorX = modules[y][0];
				for (var x = 1, runX = 1; x < size; x++) {
					if (modules[y][x] != colorX) {
						colorX = modules[y][x];
						runX = 1;
					} else {
						runX++;
						if (runX == 5)
							result += QrCode.PENALTY_N1;
						else if (runX > 5)
							result++;
					}
				}
			}
			// Adjacent modules in column having same color
			for (var x = 0; x < size; x++) {
				var colorY = modules[0][x];
				for (var y = 1, runY = 1; y < size; y++) {
					if (modules[y][x] != colorY) {
						colorY = modules[y][x];
						runY = 1;
					} else {
						runY++;
						if (runY == 5)
							result += QrCode.PENALTY_N1;
						else if (runY > 5)
							result++;
					}
				}
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
			
			// Finder-like pattern in rows
			for (var y = 0; y < size; y++) {
				for (var x = 0, bits = 0; x < size; x++) {
					bits = ((bits << 1) & 0x7FF) | (modules[y][x] ? 1 : 0);
					if (x >= 10 && (bits == 0x05D || bits == 0x5D0))  // Needs 11 bits accumulated
						result += QrCode.PENALTY_N3;
				}
			}
			// Finder-like pattern in columns
			for (var x = 0; x < size; x++) {
				for (var y = 0, bits = 0; y < size; y++) {
					bits = ((bits << 1) & 0x7FF) | (modules[y][x] ? 1 : 0);
					if (y >= 10 && (bits == 0x05D || bits == 0x5D0))  // Needs 11 bits accumulated
						result += QrCode.PENALTY_N3;
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
			var total = size * size;
			// Find smallest k such that (45-5k)% <= dark/total <= (55+5k)%
			for (var k = 0; black*20 < (9-k)*total || black*20 > (11+k)*total; k++)
				result += QrCode.PENALTY_N4;
			return result;
		}
	};
	
	
	/*---- Public static factory functions for QrCode ----*/
	
	/* 
	 * Returns a QR Code symbol representing the given Unicode text string at the given error correction level.
	 * As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer Unicode
	 * code points (not UTF-16 code units). The smallest possible QR Code version is automatically chosen for the output.
	 */
	this.QrCode.encodeText = function(text, ecl) {
		var segs = qrcodegen.QrSegment.makeSegments(text);
		return this.encodeSegments(segs, ecl);
	};
	
	
	/* 
	 * Returns a QR Code symbol representing the given binary data string at the given error correction level.
	 * This function always encodes using the binary segment mode, not any text mode. The maximum number of
	 * bytes allowed is 2953. The smallest possible QR Code version is automatically chosen for the output.
	 */
	this.QrCode.encodeBinary = function(data, ecl) {
		var seg = qrcodegen.QrSegment.makeBytes(data);
		return this.encodeSegments([seg], ecl);
	};
	
	
	/* 
	 * Returns a QR Code symbol representing the specified data segments with the specified encoding parameters.
	 * The smallest possible QR Code version within the specified range is automatically chosen for the output.
	 * This function allows the user to create a custom sequence of segments that switches
	 * between modes (such as alphanumeric and binary) to encode text more efficiently.
	 * This function is considered to be lower level than simply encoding text or binary data.
	 */
	this.QrCode.encodeSegments = function(segs, ecl, minVersion, maxVersion, mask, boostEcl) {
		if (minVersion == undefined) minVersion = 1;
		if (maxVersion == undefined) maxVersion = 40;
		if (mask == undefined) mask = -1;
		if (boostEcl == undefined) boostEcl = true;
		if (!(1 <= minVersion && minVersion <= maxVersion && maxVersion <= 40) || mask < -1 || mask > 7)
			throw "Invalid value";
		
		// Find the minimal version number to use
		var version, dataUsedBits;
		for (version = minVersion; ; version++) {
			var dataCapacityBits = QrCode.getNumDataCodewords(version, ecl) * 8;  // Number of data bits available
			dataUsedBits = qrcodegen.QrSegment.getTotalBits(segs, version);
			if (dataUsedBits != null && dataUsedBits <= dataCapacityBits)
				break;  // This version number is found to be suitable
			if (version >= maxVersion)  // All versions in the range could not fit the given data
				throw "Data too long";
		}
		
		// Increase the error correction level while the data still fits in the current version number
		[this.Ecc.MEDIUM, this.Ecc.QUARTILE, this.Ecc.HIGH].forEach(function(newEcl) {
			if (boostEcl && dataUsedBits <= QrCode.getNumDataCodewords(version, newEcl) * 8)
				ecl = newEcl;
		});
		
		// Create the data bit string by concatenating all segments
		var dataCapacityBits = QrCode.getNumDataCodewords(version, ecl) * 8;
		var bb = new BitBuffer();
		segs.forEach(function(seg) {
			bb.appendBits(seg.mode.modeBits, 4);
			bb.appendBits(seg.numChars, seg.mode.numCharCountBits(version));
			bb.appendData(seg);
		});
		
		// Add terminator and pad up to a byte if applicable
		bb.appendBits(0, Math.min(4, dataCapacityBits - bb.bitLength()));
		bb.appendBits(0, (8 - bb.bitLength() % 8) % 8);
		
		// Pad with alternate bytes until data capacity is reached
		for (var padByte = 0xEC; bb.bitLength() < dataCapacityBits; padByte ^= 0xEC ^ 0x11)
			bb.appendBits(padByte, 8);
		if (bb.bitLength() % 8 != 0)
			throw "Assertion error";
		
		// Create the QR Code symbol
		return new this(bb.getBytes(), mask, version, ecl);
	};
	
	
	/*---- Private static helper functions QrCode ----*/
	
	var QrCode = {};  // Private object to assign properties to. Not the same object as 'this.QrCode'.
	
	
	// Returns a sequence of positions of the alignment patterns in ascending order. These positions are
	// used on both the x and y axes. Each value in the resulting sequence is in the range [0, 177).
	// This stateless pure function could be implemented as table of 40 variable-length lists of integers.
	QrCode.getAlignmentPatternPositions = function(ver) {
		if (ver < 1 || ver > 40)
			throw "Version number out of range";
		else if (ver == 1)
			return [];
		else {
			var size = ver * 4 + 17;
			var numAlign = Math.floor(ver / 7) + 2;
			var step;
			if (ver != 32)
				step = Math.ceil((size - 13) / (2 * numAlign - 2)) * 2;
			else  // C-C-C-Combo breaker!
				step = 26;
			
			var result = [];
			for (var i = numAlign - 1, pos = size - 7; i >= 1; i--, pos -= step)
				result.push(pos);
			result.push(6);
			result.reverse();
			return result;
		}
	};
	
	
	// Returns the number of raw data modules (bits) available at the given version number.
	// These data modules are used for both user data codewords and error correction codewords.
	// This stateless pure function could be implemented as a 40-entry lookup table.
	QrCode.getNumRawDataModules = function(ver) {
		if (ver < 1 || ver > 40)
			throw "Version number out of range";
		var result = (16 * ver + 128) * ver + 64;
		if (ver >= 2) {
			var numAlign = Math.floor(ver / 7) + 2;
			result -= (25 * numAlign - 10) * numAlign - 55;
			if (ver >= 7)
				result -= 18 * 2;  // Subtract version information
		}
		return result;
	};
	
	
	// Returns the number of 8-bit data (i.e. not error correction) codewords contained in any
	// QR Code of the given version number and error correction level, with remainder bits discarded.
	// This stateless pure function could be implemented as a (40*4)-cell lookup table.
	QrCode.getNumDataCodewords = function(ver, ecl) {
		if (ver < 1 || ver > 40)
			throw "Version number out of range";
		return Math.floor(QrCode.getNumRawDataModules(ver) / 8) - QrCode.NUM_ERROR_CORRECTION_CODEWORDS[ecl.ordinal][ver];
	};
	
	
	/*---- Private tables of constants for QrCode ----*/
	
	// For use in getPenaltyScore(), when evaluating which mask is best.
	QrCode.PENALTY_N1 = 3;
	QrCode.PENALTY_N2 = 3;
	QrCode.PENALTY_N3 = 40;
	QrCode.PENALTY_N4 = 10;
	
	QrCode.NUM_ERROR_CORRECTION_CODEWORDS = [
		// Version: (note that index 0 is for padding, and is set to an illegal value)
		//  0,  1,  2,  3,  4,  5,   6,   7,   8,   9,  10,  11,  12,  13,  14,  15,  16,  17,  18,  19,  20,  21,  22,  23,  24,   25,   26,   27,   28,   29,   30,   31,   32,   33,   34,   35,   36,   37,   38,   39,   40    Error correction level
		[null,  7, 10, 15, 20, 26,  36,  40,  48,  60,  72,  80,  96, 104, 120, 132, 144, 168, 180, 196, 224, 224, 252, 270, 300,  312,  336,  360,  390,  420,  450,  480,  510,  540,  570,  570,  600,  630,  660,  720,  750],  // Low
		[null, 10, 16, 26, 36, 48,  64,  72,  88, 110, 130, 150, 176, 198, 216, 240, 280, 308, 338, 364, 416, 442, 476, 504, 560,  588,  644,  700,  728,  784,  812,  868,  924,  980, 1036, 1064, 1120, 1204, 1260, 1316, 1372],  // Medium
		[null, 13, 22, 36, 52, 72,  96, 108, 132, 160, 192, 224, 260, 288, 320, 360, 408, 448, 504, 546, 600, 644, 690, 750, 810,  870,  952, 1020, 1050, 1140, 1200, 1290, 1350, 1440, 1530, 1590, 1680, 1770, 1860, 1950, 2040],  // Quartile
		[null, 17, 28, 44, 64, 88, 112, 130, 156, 192, 224, 264, 308, 352, 384, 432, 480, 532, 588, 650, 700, 750, 816, 900, 960, 1050, 1110, 1200, 1260, 1350, 1440, 1530, 1620, 1710, 1800, 1890, 1980, 2100, 2220, 2310, 2430],  // High
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
	 * Represents the error correction level used in a QR Code symbol.
	 */
	this.QrCode.Ecc = {
		// Constants declared in ascending order of error protection
		LOW     : new Ecc(0, 1),
		MEDIUM  : new Ecc(1, 0),
		QUARTILE: new Ecc(2, 3),
		HIGH    : new Ecc(3, 2),
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
	 * A public class that represents a character string to be encoded in a QR Code symbol.
	 * Each segment has a mode, and a sequence of characters that is already encoded as
	 * a sequence of bits. Instances of this class are immutable.
	 * This segment class imposes no length restrictions, but QR Codes have restrictions.
	 * Even in the most favorable conditions, a QR Code can only hold 7089 characters of data.
	 * Any segment longer than this is meaningless for the purpose of generating QR Codes.
	 */
	this.QrSegment = function(mode, numChars, bitData) {
		if (numChars < 0 || !(mode instanceof Mode))
			throw "Invalid argument";
		
		// The mode indicator for this segment.
		Object.defineProperty(this, "mode", {value:mode});
		
		// The length of this segment's unencoded data, measured in characters. Always zero or positive.
		Object.defineProperty(this, "numChars", {value:numChars});
		
		// Returns a copy of all bits, which is an array of 0s and 1s.
		this.getBits = function() {
			return bitData.slice();
		};
	};
	
	
	/*---- Public static factory functions for QrSegment ----*/
	
	/* 
	 * Returns a segment representing the given binary data encoded in byte mode.
	 */
	this.QrSegment.makeBytes = function(data) {
		var bb = new BitBuffer();
		data.forEach(function(b) {
			bb.appendBits(b, 8);
		});
		return new this(this.Mode.BYTE, data.length, bb.getBits());
	};
	
	
	/* 
	 * Returns a segment representing the given string of decimal digits encoded in numeric mode.
	 */
	this.QrSegment.makeNumeric = function(digits) {
		if (!this.NUMERIC_REGEX.test(digits))
			throw "String contains non-numeric characters";
		var bb = new BitBuffer();
		var i;
		for (i = 0; i + 3 <= digits.length; i += 3)  // Process groups of 3
			bb.appendBits(parseInt(digits.substr(i, 3), 10), 10);
		var rem = digits.length - i;
		if (rem > 0)  // 1 or 2 digits remaining
			bb.appendBits(parseInt(digits.substring(i), 10), rem * 3 + 1);
		return new this(this.Mode.NUMERIC, digits.length, bb.getBits());
	};
	
	
	/* 
	 * Returns a segment representing the given text string encoded in alphanumeric mode. The characters allowed are:
	 * 0 to 9, A to Z (uppercase only), space, dollar, percent, asterisk, plus, hyphen, period, slash, colon.
	 */
	this.QrSegment.makeAlphanumeric = function(text) {
		if (!this.ALPHANUMERIC_REGEX.test(text))
			throw "String contains unencodable characters in alphanumeric mode";
		var bb = new BitBuffer();
		var i;
		for (i = 0; i + 2 <= text.length; i += 2) {  // Process groups of 2
			var temp = QrSegment.ALPHANUMERIC_ENCODING_TABLE[text.charCodeAt(i) - 32] * 45;
			temp += QrSegment.ALPHANUMERIC_ENCODING_TABLE[text.charCodeAt(i + 1) - 32];
			bb.appendBits(temp, 11);
		}
		if (i < text.length)  // 1 character remaining
			bb.appendBits(QrSegment.ALPHANUMERIC_ENCODING_TABLE[text.charCodeAt(i) - 32], 6);
		return new this(this.Mode.ALPHANUMERIC, text.length, bb.getBits());
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
	
	
	// Package-private helper function.
	this.QrSegment.getTotalBits = function(segs, version) {
		if (version < 1 || version > 40)
			throw "Version number out of range";
		var result = 0;
		for (var i = 0; i < segs.length; i++) {
			var seg = segs[i];
			var ccbits = seg.mode.numCharCountBits(version);
			// Fail if segment length value doesn't fit in the length field's bit-width
			if (seg.numChars >= (1 << ccbits))
				return null;
			result += 4 + ccbits + seg.getBits().length;
		}
		return result;
	};
	
	
	/*---- Constants for QrSegment ----*/
	
	var QrSegment = {};  // Private object to assign properties to. Not the same object as 'this.QrSegment'.
	
	// (Public) Can test whether a string is encodable in numeric mode (such as by using QrSegment.makeNumeric()).
	this.QrSegment.NUMERIC_REGEX = /^[0-9]*$/;
	
	// (Public) Can test whether a string is encodable in alphanumeric mode (such as by using QrSegment.makeAlphanumeric()).
	this.QrSegment.ALPHANUMERIC_REGEX = /^[A-Z0-9 $%*+.\/:-]*$/;
	
	// (Private) Maps shifted ASCII codes to alphanumeric mode character codes.
	QrSegment.ALPHANUMERIC_ENCODING_TABLE = [
		// SP,  !,  ",  #,  $,  %,  &,  ',  (,  ),  *,  +,  ,,  -,  .,  /,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9,  :,  ;,  <,  =,  >,  ?,  @,  // ASCII codes 32 to 64
		   36, -1, -1, -1, 37, 38, -1, -1, -1, -1, 39, 40, -1, 41, 42, 43,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 44, -1, -1, -1, -1, -1, -1,  // Array indices 0 to 32
		   10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,  // Array indices 33 to 58
		//  A,  B,  C,  D,  E,  F,  G,  H,  I,  J,  K,  L,  M,  N,  O,  P,  Q,  R,  S,  T,  U,  V,  W,  X,  Y,  Z,  // ASCII codes 65 to 90
	];
	
	
	/*---- Public helper enumeration ----*/
	
	/* 
	 * Represents the mode field of a segment. Immutable.
	 */
	this.QrSegment.Mode = {  // Constants
		NUMERIC     : new Mode(0x1, [10, 12, 14]),
		ALPHANUMERIC: new Mode(0x2, [ 9, 11, 13]),
		BYTE        : new Mode(0x4, [ 8, 16, 16]),
		KANJI       : new Mode(0x8, [ 8, 10, 12]),
	};
	
	
	// Private constructor.
	function Mode(mode, ccbits) {
		// (Package-private) An unsigned 4-bit integer value (range 0 to 15) representing the mode indicator bits for this mode object.
		Object.defineProperty(this, "modeBits", {value:mode});
		
		// (Package-private) Returns the bit width of the segment character count field for this mode object at the given version number.
		this.numCharCountBits = function(ver) {
			if      ( 1 <= ver && ver <=  9)  return ccbits[0];
			else if (10 <= ver && ver <= 26)  return ccbits[1];
			else if (27 <= ver && ver <= 40)  return ccbits[2];
			else  throw "Version number out of range";
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
				result.push(parseInt(str.substr(i + 1, 2), 16));
				i += 2;
			}
		}
		return result;
	}
	
	
	
	/* 
	 * A private helper class that computes the Reed-Solomon error correction codewords for a sequence of
	 * data codewords at a given degree. Objects are immutable, and the state only depends on the degree.
	 * This class exists because the divisor polynomial does not need to be recalculated for every input.
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
			root = (root << 1) ^ ((root >>> 7) * 0x11D);  // Multiply by 0x02 mod GF(2^8/0x11D)
		}
		
		// Computes and returns the Reed-Solomon error correction codewords for the given sequence of data codewords.
		// The returned object is always a new byte array. This method does not alter this object's state (because it is immutable).
		this.getRemainder = function(data) {
			// Compute the remainder by performing polynomial division
			var result = coefficients.map(function() { return 0; });
			data.forEach(function(b) {
				var factor = b ^ result[0];
				result.shift();
				result.push(0);
				for (var j = 0; j < result.length; j++)
					result[j] ^= ReedSolomonGenerator.multiply(coefficients[j], factor);
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
	 * A private helper class that represents an appendable sequence of bits.
	 * This constructor creates an empty bit buffer (length 0).
	 */
	function BitBuffer() {
		// Array of bits; each item is the integer 0 or 1
		var bitData = [];
		
		// Returns the number of bits in the buffer, which is a non-negative value.
		this.bitLength = function() {
			return bitData.length;
		};
		
		// Returns a copy of all bits.
		this.getBits = function() {
			return bitData.slice();
		};
		
		// Returns a copy of all bytes, padding up to the nearest byte.
		this.getBytes = function() {
			var result = [];
			var numBytes = Math.ceil(bitData.length / 8);
			for (var i = 0; i < numBytes; i++)
				result.push(0);
			bitData.forEach(function(bit, i) {
				result[i >>> 3] |= bit << (7 - (i & 7));
			});
			return result;
		};
		
		// Appends the given number of bits of the given value to this sequence.
		// If 0 <= len <= 31, then this requires 0 <= val < 2^len.
		this.appendBits = function(val, len) {
			if (len < 0 || len > 32 || len < 32 && (val >>> len) != 0)
				throw "Value out of range";
			for (var i = len - 1; i >= 0; i--)  // Append bit by bit
				bitData.push((val >>> i) & 1);
		};
		
		// Appends the bit data of the given segment to this bit buffer.
		this.appendData = function(seg) {
			seg.getBits().forEach(function(b) {  // Append bit by bit
				bitData.push(b);
			});
		};
	}
	
};
