/*
 * QR Code generator library (TypeScript)
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
var qrcodegen;
(function (qrcodegen) {
    /*---- QR Code symbol class ----*/
    /*
     * A QR Code symbol, which is a type of two-dimension barcode.
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
     */
    var QrCode = /** @class */ (function () {
        /*-- Constructor (low level) and fields --*/
        // Creates a new QR Code with the given version number,
        // error correction level, data codeword bytes, and mask number.
        // This is a low-level API that most users should not use directly.
        // A mid-level API is the encodeSegments() function.
        function QrCode(
        // The version number of this QR Code, which is between 1 and 40 (inclusive).
        // This determines the size of this barcode.
        version, 
        // The error correction level used in this QR Code.
        errorCorrectionLevel, dataCodewords, 
        // The index of the mask pattern used in this QR Code, which is between 0 and 7 (inclusive).
        // Even if a QR Code is created with automatic masking requested (mask = -1),
        // the resulting object still has a mask value between 0 and 7.
        mask) {
            this.version = version;
            this.errorCorrectionLevel = errorCorrectionLevel;
            this.mask = mask;
            // The modules of this QR Code (false = white, true = black).
            // Immutable after constructor finishes. Accessed through getModule().
            this.modules = [];
            // Indicates function modules that are not subjected to masking. Discarded when constructor finishes.
            this.isFunction = [];
            // Check scalar arguments
            if (version < QrCode.MIN_VERSION || version > QrCode.MAX_VERSION)
                throw "Version value out of range";
            if (mask < -1 || mask > 7)
                throw "Mask value out of range";
            this.size = version * 4 + 17;
            // Initialize both grids to be size*size arrays of Boolean false
            var row = [];
            for (var i = 0; i < this.size; i++)
                row.push(false);
            for (var i = 0; i < this.size; i++) {
                this.modules.push(row.slice()); // Initially all white
                this.isFunction.push(row.slice());
            }
            // Compute ECC, draw modules
            this.drawFunctionPatterns();
            var allCodewords = this.addEccAndInterleave(dataCodewords);
            this.drawCodewords(allCodewords);
            // Do masking
            if (mask == -1) { // Automatically choose best mask
                var minPenalty = 1000000000;
                for (var i = 0; i < 8; i++) {
                    this.applyMask(i);
                    this.drawFormatBits(i);
                    var penalty = this.getPenaltyScore();
                    if (penalty < minPenalty) {
                        mask = i;
                        minPenalty = penalty;
                    }
                    this.applyMask(i); // Undoes the mask due to XOR
                }
            }
            if (mask < 0 || mask > 7)
                throw "Assertion error";
            this.mask = mask;
            this.applyMask(mask); // Apply the final choice of mask
            this.drawFormatBits(mask); // Overwrite old format bits
            this.isFunction = [];
        }
        /*-- Static factory functions (high level) --*/
        // Returns a QR Code representing the given Unicode text string at the given error correction level.
        // As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer
        // Unicode code points (not UTF-16 code units) if the low error correction level is used. The smallest possible
        // QR Code version is automatically chosen for the output. The ECC level of the result may be higher than the
        // ecl argument if it can be done without increasing the version.
        QrCode.encodeText = function (text, ecl) {
            var segs = qrcodegen.QrSegment.makeSegments(text);
            return QrCode.encodeSegments(segs, ecl);
        };
        // Returns a QR Code representing the given binary data at the given error correction level.
        // This function always encodes using the binary segment mode, not any text mode. The maximum number of
        // bytes allowed is 2953. The smallest possible QR Code version is automatically chosen for the output.
        // The ECC level of the result may be higher than the ecl argument if it can be done without increasing the version.
        QrCode.encodeBinary = function (data, ecl) {
            var seg = qrcodegen.QrSegment.makeBytes(data);
            return QrCode.encodeSegments([seg], ecl);
        };
        /*-- Static factory functions (mid level) --*/
        // Returns a QR Code representing the given segments with the given encoding parameters.
        // The smallest possible QR Code version within the given range is automatically
        // chosen for the output. Iff boostEcl is true, then the ECC level of the result
        // may be higher than the ecl argument if it can be done without increasing the
        // version. The mask number is either between 0 to 7 (inclusive) to force that
        // mask, or -1 to automatically choose an appropriate mask (which may be slow).
        // This function allows the user to create a custom sequence of segments that switches
        // between modes (such as alphanumeric and byte) to encode text in less space.
        // This is a mid-level API; the high-level API is encodeText() and encodeBinary().
        QrCode.encodeSegments = function (segs, ecl, minVersion, maxVersion, mask, boostEcl) {
            if (minVersion === void 0) { minVersion = 1; }
            if (maxVersion === void 0) { maxVersion = 40; }
            if (mask === void 0) { mask = -1; }
            if (boostEcl === void 0) { boostEcl = true; }
            if (!(QrCode.MIN_VERSION <= minVersion && minVersion <= maxVersion && maxVersion <= QrCode.MAX_VERSION)
                || mask < -1 || mask > 7)
                throw "Invalid value";
            // Find the minimal version number to use
            var version;
            var dataUsedBits;
            for (version = minVersion;; version++) {
                var dataCapacityBits_1 = QrCode.getNumDataCodewords(version, ecl) * 8; // Number of data bits available
                var usedBits = QrSegment.getTotalBits(segs, version);
                if (usedBits <= dataCapacityBits_1) {
                    dataUsedBits = usedBits;
                    break; // This version number is found to be suitable
                }
                if (version >= maxVersion) // All versions in the range could not fit the given data
                    throw "Data too long";
            }
            // Increase the error correction level while the data still fits in the current version number
            for (var _i = 0, _a = [QrCode.Ecc.MEDIUM, QrCode.Ecc.QUARTILE, QrCode.Ecc.HIGH]; _i < _a.length; _i++) { // From low to high
                var newEcl = _a[_i];
                if (boostEcl && dataUsedBits <= QrCode.getNumDataCodewords(version, newEcl) * 8)
                    ecl = newEcl;
            }
            // Concatenate all segments to create the data bit string
            var bb = [];
            for (var _b = 0, segs_1 = segs; _b < segs_1.length; _b++) {
                var seg = segs_1[_b];
                appendBits(seg.mode.modeBits, 4, bb);
                appendBits(seg.numChars, seg.mode.numCharCountBits(version), bb);
                for (var _c = 0, _d = seg.getData(); _c < _d.length; _c++) {
                    var b = _d[_c];
                    bb.push(b);
                }
            }
            if (bb.length != dataUsedBits)
                throw "Assertion error";
            // Add terminator and pad up to a byte if applicable
            var dataCapacityBits = QrCode.getNumDataCodewords(version, ecl) * 8;
            if (bb.length > dataCapacityBits)
                throw "Assertion error";
            appendBits(0, Math.min(4, dataCapacityBits - bb.length), bb);
            appendBits(0, (8 - bb.length % 8) % 8, bb);
            if (bb.length % 8 != 0)
                throw "Assertion error";
            // Pad with alternating bytes until data capacity is reached
            for (var padByte = 0xEC; bb.length < dataCapacityBits; padByte ^= 0xEC ^ 0x11)
                appendBits(padByte, 8, bb);
            // Pack bits into bytes in big endian
            var dataCodewords = [];
            while (dataCodewords.length * 8 < bb.length)
                dataCodewords.push(0);
            bb.forEach(function (b, i) {
                return dataCodewords[i >>> 3] |= b << (7 - (i & 7));
            });
            // Create the QR Code object
            return new QrCode(version, ecl, dataCodewords, mask);
        };
        /*-- Accessor methods --*/
        // Returns the color of the module (pixel) at the given coordinates, which is false
        // for white or true for black. The top left corner has the coordinates (x=0, y=0).
        // If the given coordinates are out of bounds, then false (white) is returned.
        QrCode.prototype.getModule = function (x, y) {
            return 0 <= x && x < this.size && 0 <= y && y < this.size && this.modules[y][x];
        };
        /*-- Public instance methods --*/
        // Draws this QR Code, with the given module scale and border modules, onto the given HTML
        // canvas element. The canvas's width and height is resized to (this.size + border * 2) * scale.
        // The drawn image is be purely black and white, and fully opaque.
        // The scale must be a positive integer and the border must be a non-negative integer.
        QrCode.prototype.drawCanvas = function (scale, border, canvas) {
            if (scale <= 0 || border < 0)
                throw "Value out of range";
            var width = (this.size + border * 2) * scale;
            canvas.width = width;
            canvas.height = width;
            var ctx = canvas.getContext("2d");
            for (var y = -border; y < this.size + border; y++) {
                for (var x = -border; x < this.size + border; x++) {
                    ctx.fillStyle = this.getModule(x, y) ? "#000000" : "#FFFFFF";
                    ctx.fillRect((x + border) * scale, (y + border) * scale, scale, scale);
                }
            }
        };
        // Returns a string of SVG code for an image depicting this QR Code, with the given number
        // of border modules. The string always uses Unix newlines (\n), regardless of the platform.
        QrCode.prototype.toSvgString = function (border) {
            if (border < 0)
                throw "Border must be non-negative";
            var parts = [];
            for (var y = 0; y < this.size; y++) {
                for (var x = 0; x < this.size; x++) {
                    if (this.getModule(x, y))
                        parts.push("M" + (x + border) + "," + (y + border) + "h1v1h-1z");
                }
            }
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 " + (this.size + border * 2) + " " + (this.size + border * 2) + "\" stroke=\"none\">\n\t<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n\t<path d=\"" + parts.join(" ") + "\" fill=\"#000000\"/>\n</svg>\n";
        };
        /*-- Private helper methods for constructor: Drawing function modules --*/
        // Reads this object's version field, and draws and marks all function modules.
        QrCode.prototype.drawFunctionPatterns = function () {
            // Draw horizontal and vertical timing patterns
            for (var i = 0; i < this.size; i++) {
                this.setFunctionModule(6, i, i % 2 == 0);
                this.setFunctionModule(i, 6, i % 2 == 0);
            }
            // Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
            this.drawFinderPattern(3, 3);
            this.drawFinderPattern(this.size - 4, 3);
            this.drawFinderPattern(3, this.size - 4);
            // Draw numerous alignment patterns
            var alignPatPos = this.getAlignmentPatternPositions();
            var numAlign = alignPatPos.length;
            for (var i = 0; i < numAlign; i++) {
                for (var j = 0; j < numAlign; j++) {
                    // Don't draw on the three finder corners
                    if (!(i == 0 && j == 0 || i == 0 && j == numAlign - 1 || i == numAlign - 1 && j == 0))
                        this.drawAlignmentPattern(alignPatPos[i], alignPatPos[j]);
                }
            }
            // Draw configuration data
            this.drawFormatBits(0); // Dummy mask value; overwritten later in the constructor
            this.drawVersion();
        };
        // Draws two copies of the format bits (with its own error correction code)
        // based on the given mask and this object's error correction level field.
        QrCode.prototype.drawFormatBits = function (mask) {
            // Calculate error correction code and pack bits
            var data = this.errorCorrectionLevel.formatBits << 3 | mask; // errCorrLvl is uint2, mask is uint3
            var rem = data;
            for (var i = 0; i < 10; i++)
                rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
            var bits = (data << 10 | rem) ^ 0x5412; // uint15
            if (bits >>> 15 != 0)
                throw "Assertion error";
            // Draw first copy
            for (var i = 0; i <= 5; i++)
                this.setFunctionModule(8, i, getBit(bits, i));
            this.setFunctionModule(8, 7, getBit(bits, 6));
            this.setFunctionModule(8, 8, getBit(bits, 7));
            this.setFunctionModule(7, 8, getBit(bits, 8));
            for (var i = 9; i < 15; i++)
                this.setFunctionModule(14 - i, 8, getBit(bits, i));
            // Draw second copy
            for (var i = 0; i < 8; i++)
                this.setFunctionModule(this.size - 1 - i, 8, getBit(bits, i));
            for (var i = 8; i < 15; i++)
                this.setFunctionModule(8, this.size - 15 + i, getBit(bits, i));
            this.setFunctionModule(8, this.size - 8, true); // Always black
        };
        // Draws two copies of the version bits (with its own error correction code),
        // based on this object's version field, iff 7 <= version <= 40.
        QrCode.prototype.drawVersion = function () {
            if (this.version < 7)
                return;
            // Calculate error correction code and pack bits
            var rem = this.version; // version is uint6, in the range [7, 40]
            for (var i = 0; i < 12; i++)
                rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
            var bits = this.version << 12 | rem; // uint18
            if (bits >>> 18 != 0)
                throw "Assertion error";
            // Draw two copies
            for (var i = 0; i < 18; i++) {
                var color = getBit(bits, i);
                var a = this.size - 11 + i % 3;
                var b = Math.floor(i / 3);
                this.setFunctionModule(a, b, color);
                this.setFunctionModule(b, a, color);
            }
        };
        // Draws a 9*9 finder pattern including the border separator,
        // with the center module at (x, y). Modules can be out of bounds.
        QrCode.prototype.drawFinderPattern = function (x, y) {
            for (var dy = -4; dy <= 4; dy++) {
                for (var dx = -4; dx <= 4; dx++) {
                    var dist = Math.max(Math.abs(dx), Math.abs(dy)); // Chebyshev/infinity norm
                    var xx = x + dx;
                    var yy = y + dy;
                    if (0 <= xx && xx < this.size && 0 <= yy && yy < this.size)
                        this.setFunctionModule(xx, yy, dist != 2 && dist != 4);
                }
            }
        };
        // Draws a 5*5 alignment pattern, with the center module
        // at (x, y). All modules must be in bounds.
        QrCode.prototype.drawAlignmentPattern = function (x, y) {
            for (var dy = -2; dy <= 2; dy++) {
                for (var dx = -2; dx <= 2; dx++)
                    this.setFunctionModule(x + dx, y + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        };
        // Sets the color of a module and marks it as a function module.
        // Only used by the constructor. Coordinates must be in bounds.
        QrCode.prototype.setFunctionModule = function (x, y, isBlack) {
            this.modules[y][x] = isBlack;
            this.isFunction[y][x] = true;
        };
        /*-- Private helper methods for constructor: Codewords and masking --*/
        // Returns a new byte string representing the given data with the appropriate error correction
        // codewords appended to it, based on this object's version and error correction level.
        QrCode.prototype.addEccAndInterleave = function (data) {
            var ver = this.version;
            var ecl = this.errorCorrectionLevel;
            if (data.length != QrCode.getNumDataCodewords(ver, ecl))
                throw "Invalid argument";
            // Calculate parameter numbers
            var numBlocks = QrCode.NUM_ERROR_CORRECTION_BLOCKS[ecl.ordinal][ver];
            var blockEccLen = QrCode.ECC_CODEWORDS_PER_BLOCK[ecl.ordinal][ver];
            var rawCodewords = Math.floor(QrCode.getNumRawDataModules(ver) / 8);
            var numShortBlocks = numBlocks - rawCodewords % numBlocks;
            var shortBlockLen = Math.floor(rawCodewords / numBlocks);
            // Split data into blocks and append ECC to each block
            var blocks = [];
            var rsDiv = QrCode.reedSolomonComputeDivisor(blockEccLen);
            for (var i = 0, k = 0; i < numBlocks; i++) {
                var dat = data.slice(k, k + shortBlockLen - blockEccLen + (i < numShortBlocks ? 0 : 1));
                k += dat.length;
                var ecc = QrCode.reedSolomonComputeRemainder(dat, rsDiv);
                if (i < numShortBlocks)
                    dat.push(0);
                blocks.push(dat.concat(ecc));
            }
            // Interleave (not concatenate) the bytes from every block into a single sequence
            var result = [];
            var _loop_1 = function (i) {
                blocks.forEach(function (block, j) {
                    // Skip the padding byte in short blocks
                    if (i != shortBlockLen - blockEccLen || j >= numShortBlocks)
                        result.push(block[i]);
                });
            };
            for (var i = 0; i < blocks[0].length; i++) {
                _loop_1(i);
            }
            if (result.length != rawCodewords)
                throw "Assertion error";
            return result;
        };
        // Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
        // data area of this QR Code. Function modules need to be marked off before this is called.
        QrCode.prototype.drawCodewords = function (data) {
            if (data.length != Math.floor(QrCode.getNumRawDataModules(this.version) / 8))
                throw "Invalid argument";
            var i = 0; // Bit index into the data
            // Do the funny zigzag scan
            for (var right = this.size - 1; right >= 1; right -= 2) { // Index of right column in each column pair
                if (right == 6)
                    right = 5;
                for (var vert = 0; vert < this.size; vert++) { // Vertical counter
                    for (var j = 0; j < 2; j++) {
                        var x = right - j; // Actual x coordinate
                        var upward = ((right + 1) & 2) == 0;
                        var y = upward ? this.size - 1 - vert : vert; // Actual y coordinate
                        if (!this.isFunction[y][x] && i < data.length * 8) {
                            this.modules[y][x] = getBit(data[i >>> 3], 7 - (i & 7));
                            i++;
                        }
                        // If this QR Code has any remainder bits (0 to 7), they were assigned as
                        // 0/false/white by the constructor and are left unchanged by this method
                    }
                }
            }
            if (i != data.length * 8)
                throw "Assertion error";
        };
        // XORs the codeword modules in this QR Code with the given mask pattern.
        // The function modules must be marked and the codeword bits must be drawn
        // before masking. Due to the arithmetic of XOR, calling applyMask() with
        // the same mask value a second time will undo the mask. A final well-formed
        // QR Code needs exactly one (not zero, two, etc.) mask applied.
        QrCode.prototype.applyMask = function (mask) {
            if (mask < 0 || mask > 7)
                throw "Mask value out of range";
            for (var y = 0; y < this.size; y++) {
                for (var x = 0; x < this.size; x++) {
                    var invert = void 0;
                    switch (mask) {
                        case 0:
                            invert = (x + y) % 2 == 0;
                            break;
                        case 1:
                            invert = y % 2 == 0;
                            break;
                        case 2:
                            invert = x % 3 == 0;
                            break;
                        case 3:
                            invert = (x + y) % 3 == 0;
                            break;
                        case 4:
                            invert = (Math.floor(x / 3) + Math.floor(y / 2)) % 2 == 0;
                            break;
                        case 5:
                            invert = x * y % 2 + x * y % 3 == 0;
                            break;
                        case 6:
                            invert = (x * y % 2 + x * y % 3) % 2 == 0;
                            break;
                        case 7:
                            invert = ((x + y) % 2 + x * y % 3) % 2 == 0;
                            break;
                        default: throw "Assertion error";
                    }
                    if (!this.isFunction[y][x] && invert)
                        this.modules[y][x] = !this.modules[y][x];
                }
            }
        };
        // Calculates and returns the penalty score based on state of this QR Code's current modules.
        // This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
        QrCode.prototype.getPenaltyScore = function () {
            var result = 0;
            // Adjacent modules in row having same color, and finder-like patterns
            for (var y = 0; y < this.size; y++) {
                var runColor = false;
                var runX = 0;
                var runHistory = [0, 0, 0, 0, 0, 0, 0];
                for (var x = 0; x < this.size; x++) {
                    if (this.modules[y][x] == runColor) {
                        runX++;
                        if (runX == 5)
                            result += QrCode.PENALTY_N1;
                        else if (runX > 5)
                            result++;
                    }
                    else {
                        this.finderPenaltyAddHistory(runX, runHistory);
                        if (!runColor)
                            result += this.finderPenaltyCountPatterns(runHistory) * QrCode.PENALTY_N3;
                        runColor = this.modules[y][x];
                        runX = 1;
                    }
                }
                result += this.finderPenaltyTerminateAndCount(runColor, runX, runHistory) * QrCode.PENALTY_N3;
            }
            // Adjacent modules in column having same color, and finder-like patterns
            for (var x = 0; x < this.size; x++) {
                var runColor = false;
                var runY = 0;
                var runHistory = [0, 0, 0, 0, 0, 0, 0];
                for (var y = 0; y < this.size; y++) {
                    if (this.modules[y][x] == runColor) {
                        runY++;
                        if (runY == 5)
                            result += QrCode.PENALTY_N1;
                        else if (runY > 5)
                            result++;
                    }
                    else {
                        this.finderPenaltyAddHistory(runY, runHistory);
                        if (!runColor)
                            result += this.finderPenaltyCountPatterns(runHistory) * QrCode.PENALTY_N3;
                        runColor = this.modules[y][x];
                        runY = 1;
                    }
                }
                result += this.finderPenaltyTerminateAndCount(runColor, runY, runHistory) * QrCode.PENALTY_N3;
            }
            // 2*2 blocks of modules having same color
            for (var y = 0; y < this.size - 1; y++) {
                for (var x = 0; x < this.size - 1; x++) {
                    var color = this.modules[y][x];
                    if (color == this.modules[y][x + 1] &&
                        color == this.modules[y + 1][x] &&
                        color == this.modules[y + 1][x + 1])
                        result += QrCode.PENALTY_N2;
                }
            }
            // Balance of black and white modules
            var black = 0;
            for (var _i = 0, _a = this.modules; _i < _a.length; _i++) {
                var row = _a[_i];
                black = row.reduce(function (sum, color) { return sum + (color ? 1 : 0); }, black);
            }
            var total = this.size * this.size; // Note that size is odd, so black/total != 1/2
            // Compute the smallest integer k >= 0 such that (45-5k)% <= black/total <= (55+5k)%
            var k = Math.ceil(Math.abs(black * 20 - total * 10) / total) - 1;
            result += k * QrCode.PENALTY_N4;
            return result;
        };
        /*-- Private helper functions --*/
        // Returns an ascending list of positions of alignment patterns for this version number.
        // Each position is in the range [0,177), and are used on both the x and y axes.
        // This could be implemented as lookup table of 40 variable-length lists of integers.
        QrCode.prototype.getAlignmentPatternPositions = function () {
            if (this.version == 1)
                return [];
            else {
                var numAlign = Math.floor(this.version / 7) + 2;
                var step = (this.version == 32) ? 26 :
                    Math.ceil((this.size - 13) / (numAlign * 2 - 2)) * 2;
                var result = [6];
                for (var pos = this.size - 7; result.length < numAlign; pos -= step)
                    result.splice(1, 0, pos);
                return result;
            }
        };
        // Returns the number of data bits that can be stored in a QR Code of the given version number, after
        // all function modules are excluded. This includes remainder bits, so it might not be a multiple of 8.
        // The result is in the range [208, 29648]. This could be implemented as a 40-entry lookup table.
        QrCode.getNumRawDataModules = function (ver) {
            if (ver < QrCode.MIN_VERSION || ver > QrCode.MAX_VERSION)
                throw "Version number out of range";
            var result = (16 * ver + 128) * ver + 64;
            if (ver >= 2) {
                var numAlign = Math.floor(ver / 7) + 2;
                result -= (25 * numAlign - 10) * numAlign - 55;
                if (ver >= 7)
                    result -= 36;
            }
            if (!(208 <= result && result <= 29648))
                throw "Assertion error";
            return result;
        };
        // Returns the number of 8-bit data (i.e. not error correction) codewords contained in any
        // QR Code of the given version number and error correction level, with remainder bits discarded.
        // This stateless pure function could be implemented as a (40*4)-cell lookup table.
        QrCode.getNumDataCodewords = function (ver, ecl) {
            return Math.floor(QrCode.getNumRawDataModules(ver) / 8) -
                QrCode.ECC_CODEWORDS_PER_BLOCK[ecl.ordinal][ver] *
                    QrCode.NUM_ERROR_CORRECTION_BLOCKS[ecl.ordinal][ver];
        };
        // Returns a Reed-Solomon ECC generator polynomial for the given degree. This could be
        // implemented as a lookup table over all possible parameter values, instead of as an algorithm.
        QrCode.reedSolomonComputeDivisor = function (degree) {
            if (degree < 1 || degree > 255)
                throw "Degree out of range";
            // Polynomial coefficients are stored from highest to lowest power, excluding the leading term which is always 1.
            // For example the polynomial x^3 + 255x^2 + 8x + 93 is stored as the uint8 array [255, 8, 93].
            var result = [];
            for (var i = 0; i < degree - 1; i++)
                result.push(0);
            result.push(1); // Start off with the monomial x^0
            // Compute the product polynomial (x - r^0) * (x - r^1) * (x - r^2) * ... * (x - r^{degree-1}),
            // and drop the highest monomial term which is always 1x^degree.
            // Note that r = 0x02, which is a generator element of this field GF(2^8/0x11D).
            var root = 1;
            for (var i = 0; i < degree; i++) {
                // Multiply the current product by (x - r^i)
                for (var j = 0; j < result.length; j++) {
                    result[j] = QrCode.reedSolomonMultiply(result[j], root);
                    if (j + 1 < result.length)
                        result[j] ^= result[j + 1];
                }
                root = QrCode.reedSolomonMultiply(root, 0x02);
            }
            return result;
        };
        // Returns the Reed-Solomon error correction codeword for the given data and divisor polynomials.
        QrCode.reedSolomonComputeRemainder = function (data, divisor) {
            var result = divisor.map(function (_) { return 0; });
            var _loop_2 = function (b) {
                var factor = b ^ result.shift();
                result.push(0);
                divisor.forEach(function (coef, i) {
                    return result[i] ^= QrCode.reedSolomonMultiply(coef, factor);
                });
            };
            for (var _i = 0, data_1 = data; _i < data_1.length; _i++) {
                var b = data_1[_i];
                _loop_2(b);
            }
            return result;
        };
        // Returns the product of the two given field elements modulo GF(2^8/0x11D). The arguments and result
        // are unsigned 8-bit integers. This could be implemented as a lookup table of 256*256 entries of uint8.
        QrCode.reedSolomonMultiply = function (x, y) {
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
        // Can only be called immediately after a white run is added, and
        // returns either 0, 1, or 2. A helper function for getPenaltyScore().
        QrCode.prototype.finderPenaltyCountPatterns = function (runHistory) {
            var n = runHistory[1];
            if (n > this.size * 3)
                throw "Assertion error";
            var core = n > 0 && runHistory[2] == n && runHistory[3] == n * 3 && runHistory[4] == n && runHistory[5] == n;
            return (core && runHistory[0] >= n * 4 && runHistory[6] >= n ? 1 : 0)
                + (core && runHistory[6] >= n * 4 && runHistory[0] >= n ? 1 : 0);
        };
        // Must be called at the end of a line (row or column) of modules. A helper function for getPenaltyScore().
        QrCode.prototype.finderPenaltyTerminateAndCount = function (currentRunColor, currentRunLength, runHistory) {
            if (currentRunColor) { // Terminate black run
                this.finderPenaltyAddHistory(currentRunLength, runHistory);
                currentRunLength = 0;
            }
            currentRunLength += this.size; // Add white border to final run
            this.finderPenaltyAddHistory(currentRunLength, runHistory);
            return this.finderPenaltyCountPatterns(runHistory);
        };
        // Pushes the given value to the front and drops the last value. A helper function for getPenaltyScore().
        QrCode.prototype.finderPenaltyAddHistory = function (currentRunLength, runHistory) {
            if (runHistory[0] == 0)
                currentRunLength += this.size; // Add white border to initial run
            runHistory.pop();
            runHistory.unshift(currentRunLength);
        };
        /*-- Constants and tables --*/
        // The minimum version number supported in the QR Code Model 2 standard.
        QrCode.MIN_VERSION = 1;
        // The maximum version number supported in the QR Code Model 2 standard.
        QrCode.MAX_VERSION = 40;
        // For use in getPenaltyScore(), when evaluating which mask is best.
        QrCode.PENALTY_N1 = 3;
        QrCode.PENALTY_N2 = 3;
        QrCode.PENALTY_N3 = 40;
        QrCode.PENALTY_N4 = 10;
        QrCode.ECC_CODEWORDS_PER_BLOCK = [
            // Version: (note that index 0 is for padding, and is set to an illegal value)
            //0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
            [-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],
            [-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28],
            [-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],
            [-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],
        ];
        QrCode.NUM_ERROR_CORRECTION_BLOCKS = [
            // Version: (note that index 0 is for padding, and is set to an illegal value)
            //0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
            [-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25],
            [-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49],
            [-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68],
            [-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81],
        ];
        return QrCode;
    }());
    qrcodegen.QrCode = QrCode;
    // Appends the given number of low-order bits of the given value
    // to the given buffer. Requires 0 <= len <= 31 and 0 <= val < 2^len.
    function appendBits(val, len, bb) {
        if (len < 0 || len > 31 || val >>> len != 0)
            throw "Value out of range";
        for (var i = len - 1; i >= 0; i--) // Append bit by bit
            bb.push((val >>> i) & 1);
    }
    // Returns true iff the i'th bit of x is set to 1.
    function getBit(x, i) {
        return ((x >>> i) & 1) != 0;
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
     */
    var QrSegment = /** @class */ (function () {
        /*-- Constructor (low level) and fields --*/
        // Creates a new QR Code segment with the given attributes and data.
        // The character count (numChars) must agree with the mode and the bit buffer length,
        // but the constraint isn't checked. The given bit buffer is cloned and stored.
        function QrSegment(
        // The mode indicator of this segment.
        mode, 
        // The length of this segment's unencoded data. Measured in characters for
        // numeric/alphanumeric/kanji mode, bytes for byte mode, and 0 for ECI mode.
        // Always zero or positive. Not the same as the data's bit length.
        numChars, 
        // The data bits of this segment. Accessed through getData().
        bitData) {
            this.mode = mode;
            this.numChars = numChars;
            this.bitData = bitData;
            if (numChars < 0)
                throw "Invalid argument";
            this.bitData = bitData.slice(); // Make defensive copy
        }
        /*-- Static factory functions (mid level) --*/
        // Returns a segment representing the given binary data encoded in
        // byte mode. All input byte arrays are acceptable. Any text string
        // can be converted to UTF-8 bytes and encoded as a byte mode segment.
        QrSegment.makeBytes = function (data) {
            var bb = [];
            for (var _i = 0, data_2 = data; _i < data_2.length; _i++) {
                var b = data_2[_i];
                appendBits(b, 8, bb);
            }
            return new QrSegment(QrSegment.Mode.BYTE, data.length, bb);
        };
        // Returns a segment representing the given string of decimal digits encoded in numeric mode.
        QrSegment.makeNumeric = function (digits) {
            if (!this.NUMERIC_REGEX.test(digits))
                throw "String contains non-numeric characters";
            var bb = [];
            for (var i = 0; i < digits.length;) { // Consume up to 3 digits per iteration
                var n = Math.min(digits.length - i, 3);
                appendBits(parseInt(digits.substr(i, n), 10), n * 3 + 1, bb);
                i += n;
            }
            return new QrSegment(QrSegment.Mode.NUMERIC, digits.length, bb);
        };
        // Returns a segment representing the given text string encoded in alphanumeric mode.
        // The characters allowed are: 0 to 9, A to Z (uppercase only), space,
        // dollar, percent, asterisk, plus, hyphen, period, slash, colon.
        QrSegment.makeAlphanumeric = function (text) {
            if (!this.ALPHANUMERIC_REGEX.test(text))
                throw "String contains unencodable characters in alphanumeric mode";
            var bb = [];
            var i;
            for (i = 0; i + 2 <= text.length; i += 2) { // Process groups of 2
                var temp = QrSegment.ALPHANUMERIC_CHARSET.indexOf(text.charAt(i)) * 45;
                temp += QrSegment.ALPHANUMERIC_CHARSET.indexOf(text.charAt(i + 1));
                appendBits(temp, 11, bb);
            }
            if (i < text.length) // 1 character remaining
                appendBits(QrSegment.ALPHANUMERIC_CHARSET.indexOf(text.charAt(i)), 6, bb);
            return new QrSegment(QrSegment.Mode.ALPHANUMERIC, text.length, bb);
        };
        // Returns a new mutable list of zero or more segments to represent the given Unicode text string.
        // The result may use various segment modes and switch modes to optimize the length of the bit stream.
        QrSegment.makeSegments = function (text) {
            // Select the most efficient segment encoding automatically
            if (text == "")
                return [];
            else if (this.NUMERIC_REGEX.test(text))
                return [QrSegment.makeNumeric(text)];
            else if (this.ALPHANUMERIC_REGEX.test(text))
                return [QrSegment.makeAlphanumeric(text)];
            else
                return [QrSegment.makeBytes(QrSegment.toUtf8ByteArray(text))];
        };
        // Returns a segment representing an Extended Channel Interpretation
        // (ECI) designator with the given assignment value.
        QrSegment.makeEci = function (assignVal) {
            var bb = [];
            if (assignVal < 0)
                throw "ECI assignment value out of range";
            else if (assignVal < (1 << 7))
                appendBits(assignVal, 8, bb);
            else if (assignVal < (1 << 14)) {
                appendBits(2, 2, bb);
                appendBits(assignVal, 14, bb);
            }
            else if (assignVal < 1000000) {
                appendBits(6, 3, bb);
                appendBits(assignVal, 21, bb);
            }
            else
                throw "ECI assignment value out of range";
            return new QrSegment(QrSegment.Mode.ECI, 0, bb);
        };
        /*-- Methods --*/
        // Returns a new copy of the data bits of this segment.
        QrSegment.prototype.getData = function () {
            return this.bitData.slice(); // Make defensive copy
        };
        // (Package-private) Calculates and returns the number of bits needed to encode the given segments at
        // the given version. The result is infinity if a segment has too many characters to fit its length field.
        QrSegment.getTotalBits = function (segs, version) {
            var result = 0;
            for (var _i = 0, segs_2 = segs; _i < segs_2.length; _i++) {
                var seg = segs_2[_i];
                var ccbits = seg.mode.numCharCountBits(version);
                if (seg.numChars >= (1 << ccbits))
                    return Infinity; // The segment's length doesn't fit the field's bit width
                result += 4 + ccbits + seg.bitData.length;
            }
            return result;
        };
        // Returns a new array of bytes representing the given string encoded in UTF-8.
        QrSegment.toUtf8ByteArray = function (str) {
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
        };
        /*-- Constants --*/
        // Describes precisely all strings that are encodable in numeric mode. To test
        // whether a string s is encodable: let ok: boolean = NUMERIC_REGEX.test(s);
        // A string is encodable iff each character is in the range 0 to 9.
        QrSegment.NUMERIC_REGEX = /^[0-9]*$/;
        // Describes precisely all strings that are encodable in alphanumeric mode. To test
        // whether a string s is encodable: let ok: boolean = ALPHANUMERIC_REGEX.test(s);
        // A string is encodable iff each character is in the following set: 0 to 9, A to Z
        // (uppercase only), space, dollar, percent, asterisk, plus, hyphen, period, slash, colon.
        QrSegment.ALPHANUMERIC_REGEX = /^[A-Z0-9 $%*+.\/:-]*$/;
        // The set of all legal characters in alphanumeric mode,
        // where each character value maps to the index in the string.
        QrSegment.ALPHANUMERIC_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";
        return QrSegment;
    }());
    qrcodegen.QrSegment = QrSegment;
})(qrcodegen || (qrcodegen = {}));
/*---- Public helper enumeration ----*/
(function (qrcodegen) {
    var QrCode;
    (function (QrCode) {
        /*
         * The error correction level in a QR Code symbol. Immutable.
         */
        var Ecc = /** @class */ (function () {
            /*-- Constructor and fields --*/
            function Ecc(
            // In the range 0 to 3 (unsigned 2-bit integer).
            ordinal, 
            // (Package-private) In the range 0 to 3 (unsigned 2-bit integer).
            formatBits) {
                this.ordinal = ordinal;
                this.formatBits = formatBits;
            }
            /*-- Constants --*/
            Ecc.LOW = new Ecc(0, 1); // The QR Code can tolerate about  7% erroneous codewords
            Ecc.MEDIUM = new Ecc(1, 0); // The QR Code can tolerate about 15% erroneous codewords
            Ecc.QUARTILE = new Ecc(2, 3); // The QR Code can tolerate about 25% erroneous codewords
            Ecc.HIGH = new Ecc(3, 2); // The QR Code can tolerate about 30% erroneous codewords
            return Ecc;
        }());
        QrCode.Ecc = Ecc;
    })(QrCode = qrcodegen.QrCode || (qrcodegen.QrCode = {}));
})(qrcodegen || (qrcodegen = {}));
/*---- Public helper enumeration ----*/
(function (qrcodegen) {
    var QrSegment;
    (function (QrSegment) {
        /*
         * Describes how a segment's data bits are interpreted. Immutable.
         */
        var Mode = /** @class */ (function () {
            /*-- Constructor and fields --*/
            function Mode(
            // The mode indicator bits, which is a uint4 value (range 0 to 15).
            modeBits, 
            // Number of character count bits for three different version ranges.
            numBitsCharCount) {
                this.modeBits = modeBits;
                this.numBitsCharCount = numBitsCharCount;
            }
            /*-- Method --*/
            // (Package-private) Returns the bit width of the character count field for a segment in
            // this mode in a QR Code at the given version number. The result is in the range [0, 16].
            Mode.prototype.numCharCountBits = function (ver) {
                return this.numBitsCharCount[Math.floor((ver + 7) / 17)];
            };
            /*-- Constants --*/
            Mode.NUMERIC = new Mode(0x1, [10, 12, 14]);
            Mode.ALPHANUMERIC = new Mode(0x2, [9, 11, 13]);
            Mode.BYTE = new Mode(0x4, [8, 16, 16]);
            Mode.KANJI = new Mode(0x8, [8, 10, 12]);
            Mode.ECI = new Mode(0x7, [0, 0, 0]);
            return Mode;
        }());
        QrSegment.Mode = Mode;
    })(QrSegment = qrcodegen.QrSegment || (qrcodegen.QrSegment = {}));
})(qrcodegen || (qrcodegen = {}));
