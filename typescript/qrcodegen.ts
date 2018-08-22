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


namespace qrcodegen {
	
	type bit = number;
	type byte = number;
	type int = number;
	
	
	/*---- QR Code symbol class ----*/
	
	/* 
	 * A class that represents an immutable square grid of black and white cells for a QR Code symbol,
	 * with associated static functions to create a QR Code from user-supplied textual or binary data.
	 * This class covers the QR Code model 2 specification, supporting all versions (sizes)
	 * from 1 to 40, all 4 error correction levels.
	 * This constructor creates a new QR Code symbol with the given version number, error correction level, binary data array,
	 * and mask number. mask = -1 is for automatic choice, or 0 to 7 for fixed choice. This is a cumbersome low-level constructor
	 * that should not be invoked directly by the user. To go one level up, see the QrCode.encodeSegments() function.
	 */
	export class QrCode {
		
		/*-- Public static factory functions --*/
		
		// Returns a QR Code symbol representing the specified Unicode text string at the specified error correction level.
		// As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer
		// Unicode code points (not UTF-16 code units) if the low error correction level is used. The smallest possible
		// QR Code version is automatically chosen for the output. The ECC level of the result may be higher than the
		// ecl argument if it can be done without increasing the version.
		public static encodeText(text: string, ecl: QrCode_Ecc): QrCode {
			let segs: Array<QrSegment> = qrcodegen.QrSegment.makeSegments(text);
			return QrCode.encodeSegments(segs, ecl);
		}
		
		
		// Returns a QR Code symbol representing the given binary data string at the given error correction level.
		// This function always encodes using the binary segment mode, not any text mode. The maximum number of
		// bytes allowed is 2953. The smallest possible QR Code version is automatically chosen for the output.
		// The ECC level of the result may be higher than the ecl argument if it can be done without increasing the version.
		public static encodeBinary(data: Array<byte>, ecl: QrCode_Ecc): QrCode {
			let seg: QrSegment = qrcodegen.QrSegment.makeBytes(data);
			return QrCode.encodeSegments([seg], ecl);
		}
		
		
		// Returns a QR Code symbol representing the given data segments with the given encoding parameters.
		// The smallest possible QR Code version within the given range is automatically chosen for the output.
		// This function allows the user to create a custom sequence of segments that switches
		// between modes (such as alphanumeric and binary) to encode text more efficiently.
		// This function is considered to be lower level than simply encoding text or binary data.
		public static encodeSegments(segs: Array<QrSegment>, ecl: QrCode_Ecc,
				minVersion: int = 1, maxVersion: int = 1,
				mask: int = -1, boostEcl: boolean = true): QrCode {
			
			if (!(QrCode.MIN_VERSION <= minVersion && minVersion <= maxVersion && maxVersion <= QrCode.MAX_VERSION)
					|| mask < -1 || mask > 7)
				throw "Invalid value";
			
			// Find the minimal version number to use
			let version: int;
			let dataUsedBits: int;
			for (version = minVersion; ; version++) {
				let dataCapacityBits: int = QrCode.getNumDataCodewords(version, ecl) * 8;  // Number of data bits available
				let usedBits: number|null = QrSegment.getTotalBits(segs, version);
				if (usedBits != null && usedBits <= dataCapacityBits) {
					dataUsedBits = usedBits;
					break;  // This version number is found to be suitable
				}
				if (version >= maxVersion)  // All versions in the range could not fit the given data
					throw "Data too long";
			}
			
			// Increase the error correction level while the data still fits in the current version number
			[QrCode_Ecc.MEDIUM, QrCode_Ecc.QUARTILE, QrCode_Ecc.HIGH].forEach((newEcl: QrCode_Ecc) => {  // From low to high
				if (boostEcl && dataUsedBits <= QrCode.getNumDataCodewords(version, newEcl) * 8)
					ecl = newEcl;
			});
			
			// Concatenate all segments to create the data bit string
			let dataCapacityBits: int = QrCode.getNumDataCodewords(version, ecl) * 8;
			let bb = new BitBuffer();
			segs.forEach((seg: QrSegment) => {
				bb.appendBits(seg.mode.modeBits, 4);
				bb.appendBits(seg.numChars, seg.mode.numCharCountBits(version));
				seg.getBits().forEach(
					(b: bit) => bb.push(b));
			});
			
			// Add terminator and pad up to a byte if applicable
			bb.appendBits(0, Math.min(4, dataCapacityBits - bb.length));
			bb.appendBits(0, (8 - bb.length % 8) % 8);
			
			// Pad with alternate bytes until data capacity is reached
			for (let padByte = 0xEC; bb.length < dataCapacityBits; padByte ^= 0xEC ^ 0x11)
				bb.appendBits(padByte, 8);
			if (bb.length % 8 != 0)
				throw "Assertion error";
			
			// Create the QR Code symbol
			return new QrCode(bb.getBytes(), mask, version, ecl);
		}
		
		
		/*-- Fields --*/
		
		// This QR Code symbol's version number, which is always between 1 and 40 (inclusive).
		public readonly version: int;
		
		// The width and height of this QR Code symbol, measured in modules.
		// Always equal to version * 4 + 17, in the range 21 to 177.
		public readonly size: int;
		
		// The error correction level used in this QR Code symbol.
		public readonly errorCorrectionLevel: QrCode_Ecc;
		
		// The mask pattern used in this QR Code symbol, in the range 0 to 7 (i.e. unsigned 3-bit integer).
		// Note that even if the constructor was called with automatic masking requested
		// (mask = -1), the resulting object will still have a mask value between 0 and 7.
		public readonly mask: int;
		
		// The modules of this QR Code symbol (false = white, true = black).
		private readonly modules: Array<Array<boolean>> = [];
		
		// Indicates function modules that are not subjected to masking.
		private readonly isFunction: Array<Array<boolean>> = [];
		
		
		public constructor(datacodewords: Array<byte>, mask: int, version: int, errCorLvl: QrCode_Ecc) {
			// Check arguments and handle simple scalar fields
			if (mask < -1 || mask > 7)
				throw "Mask value out of range";
			if (version < QrCode.MIN_VERSION || version > QrCode.MAX_VERSION)
				throw "Version value out of range";
			this.version = version;
			this.size = version * 4 + 17;
			this.errorCorrectionLevel = errCorLvl;
			
			// Initialize both grids to be size*size arrays of Boolean false
			let row: Array<boolean> = [];
			for (let i = 0; i < this.size; i++)
				row.push(false);
			for (let i = 0; i < this.size; i++) {
				this.modules.push(row.slice());
				this.isFunction.push(row.slice());
			}
			
			// Handle grid fields, draw function patterns, draw all codewords
			this.drawFunctionPatterns();
			let allCodewords: Array<byte> = this.appendErrorCorrection(datacodewords);
			this.drawCodewords(allCodewords);
			
			// Handle masking
			if (mask == -1) {  // Automatically choose best mask
				let minPenalty: int = 1000000000;
				for (let i = 0; i < 8; i++) {
					this.drawFormatBits(i);
					this.applyMask(i);
					let penalty: int = this.getPenaltyScore();
					if (penalty < minPenalty) {
						mask = i;
						minPenalty = penalty;
					}
					this.applyMask(i);  // Undoes the mask due to XOR
				}
			}
			if (mask < 0 || mask > 7)
				throw "Assertion error";
			this.mask = mask;
			this.drawFormatBits(mask);  // Overwrite old format bits
			this.applyMask(mask);  // Apply the final choice of mask
		}
		
		
		/*-- Accessor methods --*/
		
		// Returns the color of the module (pixel) at the given coordinates, which is either
		// false for white or true for black. The top left corner has the coordinates (x=0, y=0).
		// If the given coordinates are out of bounds, then false (white) is returned.
		public getModule(x: int, y: int): boolean {
			return 0 <= x && x < this.size && 0 <= y && y < this.size && this.modules[y][x];
		}
		
		
		/*-- Public instance methods --*/
		
		// Draws this QR Code symbol with the given module scale and number of modules onto the given HTML canvas element.
		// The canvas will be resized to a width and height of (this.size + border * 2) * scale. The painted image will be purely
		// black and white with no transparent regions. The scale must be a positive integer, and the border must be a non-negative integer.
		public drawCanvas(scale: int, border: int, canvas: HTMLCanvasElement): void {
			if (scale <= 0 || border < 0)
				throw "Value out of range";
			let width: int = (this.size + border * 2) * scale;
			canvas.width = width;
			canvas.height = width;
			let ctx = canvas.getContext("2d") as CanvasRenderingContext2D;
			for (let y = -border; y < this.size + border; y++) {
				for (let x = -border; x < this.size + border; x++) {
					ctx.fillStyle = this.getModule(x, y) ? "#000000" : "#FFFFFF";
					ctx.fillRect((x + border) * scale, (y + border) * scale, scale, scale);
				}
			}
		}
		
		
		// Based on the given number of border modules to add as padding, this returns a
		// string whose contents represents an SVG XML file that depicts this QR Code symbol.
		// Note that Unix newlines (\n) are always used, regardless of the platform.
		public toSvgString(border: int): string {
			if (border < 0)
				throw "Border must be non-negative";
			let parts: Array<string> = [];
			for (let y = -border; y < this.size + border; y++) {
				for (let x = -border; x < this.size + border; x++) {
					if (this.getModule(x, y))
						parts.push(`M${x + border},${y + border}h1v1h-1z`);
				}
			}
			return `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<svg xmlns="http://www.w3.org/2000/svg" version="1.1" viewBox="0 0 ${this.size + border * 2} ${this.size + border * 2}" stroke="none">
	<rect width="100%" height="100%" fill="#FFFFFF"/>
	<path d="${parts.join(" ")}" fill="#000000"/>
</svg>
`
		}
		
		
		/*-- Private helper methods for constructor: Drawing function modules --*/
		
		private drawFunctionPatterns(): void {
			// Draw horizontal and vertical timing patterns
			for (let i = 0; i < this.size; i++) {
				this.setFunctionModule(6, i, i % 2 == 0);
				this.setFunctionModule(i, 6, i % 2 == 0);
			}
			
			// Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
			this.drawFinderPattern(3, 3);
			this.drawFinderPattern(this.size - 4, 3);
			this.drawFinderPattern(3, this.size - 4);
			
			// Draw numerous alignment patterns
			let alignPatPos: Array<int> = QrCode.getAlignmentPatternPositions(this.version);
			let numAlign: int = alignPatPos.length;
			for (let i = 0; i < numAlign; i++) {
				for (let j = 0; j < numAlign; j++) {
					if (i == 0 && j == 0 || i == 0 && j == numAlign - 1 || i == numAlign - 1 && j == 0)
						continue;  // Skip the three finder corners
					else
						this.drawAlignmentPattern(alignPatPos[i], alignPatPos[j]);
				}
			}
			
			// Draw configuration data
			this.drawFormatBits(0);  // Dummy mask value; overwritten later in the constructor
			this.drawVersion();
		}
		
		
		// Draws two copies of the format bits (with its own error correction code)
		// based on the given mask and this object's error correction level field.
		private drawFormatBits(mask: int): void {
			// Calculate error correction code and pack bits
			let data: int = this.errorCorrectionLevel.formatBits << 3 | mask;  // errCorrLvl is uint2, mask is uint3
			let rem: int = data;
			for (let i = 0; i < 10; i++)
				rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
			data = data << 10 | rem;
			data ^= 0x5412;  // uint15
			if (data >>> 15 != 0)
				throw "Assertion error";
			
			// Draw first copy
			for (let i = 0; i <= 5; i++)
				this.setFunctionModule(8, i, getBit(data, i));
			this.setFunctionModule(8, 7, getBit(data, 6));
			this.setFunctionModule(8, 8, getBit(data, 7));
			this.setFunctionModule(7, 8, getBit(data, 8));
			for (let i = 9; i < 15; i++)
				this.setFunctionModule(14 - i, 8, getBit(data, i));
			
			// Draw second copy
			for (let i = 0; i <= 7; i++)
				this.setFunctionModule(this.size - 1 - i, 8, getBit(data, i));
			for (let i = 8; i < 15; i++)
				this.setFunctionModule(8, this.size - 15 + i, getBit(data, i));
			this.setFunctionModule(8, this.size - 8, true);  // Always black
		}
		
		
		// Draws two copies of the version bits (with its own error correction code),
		// based on this object's version field (which only has an effect for 7 <= version <= 40).
		private drawVersion(): void {
			if (this.version < 7)
				return;
			
			// Calculate error correction code and pack bits
			let rem: int = this.version;  // version is uint6, in the range [7, 40]
			for (let i = 0; i < 12; i++)
				rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
			let data: int = this.version << 12 | rem;  // uint18
			if (data >>> 18 != 0)
				throw "Assertion error";
			
			// Draw two copies
			for (let i = 0; i < 18; i++) {
				let bt: boolean = getBit(data, i);
				let a: int = this.size - 11 + i % 3;
				let b: int = Math.floor(i / 3);
				this.setFunctionModule(a, b, bt);
				this.setFunctionModule(b, a, bt);
			}
		}
		
		
		// Draws a 9*9 finder pattern including the border separator, with the center module at (x, y).
		private drawFinderPattern(x: int, y: int): void {
			for (let i = -4; i <= 4; i++) {
				for (let j = -4; j <= 4; j++) {
					let dist: int = Math.max(Math.abs(i), Math.abs(j));  // Chebyshev/infinity norm
					let xx: int = x + j;
					let yy: int = y + i;
					if (0 <= xx && xx < this.size && 0 <= yy && yy < this.size)
						this.setFunctionModule(xx, yy, dist != 2 && dist != 4);
				}
			}
		}
		
		
		// Draws a 5*5 alignment pattern, with the center module at (x, y).
		private drawAlignmentPattern(x: int, y: int): void {
			for (let i = -2; i <= 2; i++) {
				for (let j = -2; j <= 2; j++)
					this.setFunctionModule(x + j, y + i, Math.max(Math.abs(i), Math.abs(j)) != 1);
			}
		}
		
		
		// Sets the color of a module and marks it as a function module.
		// Only used by the constructor. Coordinates must be in range.
		private setFunctionModule(x: int, y: int, isBlack: boolean): void {
			this.modules[y][x] = isBlack;
			this.isFunction[y][x] = true;
		}
		
		
		/*-- Private helper methods for constructor: Codewords and masking --*/
		
		// Returns a new byte string representing the given data with the appropriate error correction
		// codewords appended to it, based on this object's version and error correction level.
		private appendErrorCorrection(data: Array<byte>): Array<byte> {
			const ver: int = this.version;
			const ecl: QrCode_Ecc = this.errorCorrectionLevel;
			if (data.length != QrCode.getNumDataCodewords(ver, ecl))
				throw "Invalid argument";
			
			// Calculate parameter numbers
			let numBlocks: int = QrCode_NUM_ERROR_CORRECTION_BLOCKS[ecl.ordinal][ver];
			let blockEccLen: int = QrCode_ECC_CODEWORDS_PER_BLOCK[ecl.ordinal][ver];
			let rawCodewords: int = Math.floor(QrCode.getNumRawDataModules(ver) / 8);
			let numShortBlocks: int = numBlocks - rawCodewords % numBlocks;
			let shortBlockLen: int = Math.floor(rawCodewords / numBlocks);
			
			// Split data into blocks and append ECC to each block
			let blocks: Array<Array<byte>> = [];
			let rs = new ReedSolomonGenerator(blockEccLen);
			for (let i = 0, k = 0; i < numBlocks; i++) {
				let dat: Array<byte> = data.slice(k, k + shortBlockLen - blockEccLen + (i < numShortBlocks ? 0 : 1));
				k += dat.length;
				let ecc: Array<byte> = rs.getRemainder(dat);
				if (i < numShortBlocks)
					dat.push(0);
				ecc.forEach(
					(b: byte) => dat.push(b));
				blocks.push(dat);
			}
			
			// Interleave (not concatenate) the bytes from every block into a single sequence
			let result: Array<byte> = [];
			for (let i = 0; i < blocks[0].length; i++) {
				for (let j = 0; j < blocks.length; j++) {
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
		// data area of this QR Code symbol. Function modules need to be marked off before this is called.
		private drawCodewords(data: Array<byte>): void {
			if (data.length != Math.floor(QrCode.getNumRawDataModules(this.version) / 8))
				throw "Invalid argument";
			let i: int = 0;  // Bit index into the data
			// Do the funny zigzag scan
			for (let right = this.size - 1; right >= 1; right -= 2) {  // Index of right column in each column pair
				if (right == 6)
					right = 5;
				for (let vert = 0; vert < this.size; vert++) {  // Vertical counter
					for (let j = 0; j < 2; j++) {
						let x: int = right - j;  // Actual x coordinate
						let upward: boolean = ((right + 1) & 2) == 0;
						let y: int = upward ? this.size - 1 - vert : vert;  // Actual y coordinate
						if (!this.isFunction[y][x] && i < data.length * 8) {
							this.modules[y][x] = getBit(data[i >>> 3], 7 - (i & 7));
							i++;
						}
						// If there are any remainder bits (0 to 7), they are already
						// set to 0/false/white when the grid of modules was initialized
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
		// QR Code symbol needs exactly one (not zero, two, etc.) mask applied.
		private applyMask(mask: int): void {
			if (mask < 0 || mask > 7)
				throw "Mask value out of range";
			for (let y = 0; y < this.size; y++) {
				for (let x = 0; x < this.size; x++) {
					let invert: boolean;
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
					if (invert && !this.isFunction[y][x])
						this.modules[y][x] = !this.modules[y][x];
				}
			}
		}
		
		
		// Calculates and returns the penalty score based on state of this QR Code's current modules.
		// This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
		private getPenaltyScore(): int {
			let result: int = 0;
			
			// Adjacent modules in row having same color
			for (let y = 0; y < this.size; y++) {
				for (let x = 0, runX = 0, colorX = false; x < this.size; x++) {
					if (x == 0 || this.modules[y][x] != colorX) {
						colorX = this.modules[y][x];
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
			for (let x = 0; x < this.size; x++) {
				for (let y = 0, runY = 0, colorY = false; y < this.size; y++) {
					if (y == 0 || this.modules[y][x] != colorY) {
						colorY = this.modules[y][x];
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
			for (let y = 0; y < this.size - 1; y++) {
				for (let x = 0; x < this.size - 1; x++) {
					let color: boolean = this.modules[y][x];
					if (  color == this.modules[y][x + 1] &&
					      color == this.modules[y + 1][x] &&
					      color == this.modules[y + 1][x + 1])
						result += QrCode.PENALTY_N2;
				}
			}
			
			// Finder-like pattern in rows
			for (let y = 0; y < this.size; y++) {
				for (let x = 0, bits = 0; x < this.size; x++) {
					bits = ((bits << 1) & 0x7FF) | (this.modules[y][x] ? 1 : 0);
					if (x >= 10 && (bits == 0x05D || bits == 0x5D0))  // Needs 11 bits accumulated
						result += QrCode.PENALTY_N3;
				}
			}
			// Finder-like pattern in columns
			for (let x = 0; x < this.size; x++) {
				for (let y = 0, bits = 0; y < this.size; y++) {
					bits = ((bits << 1) & 0x7FF) | (this.modules[y][x] ? 1 : 0);
					if (y >= 10 && (bits == 0x05D || bits == 0x5D0))  // Needs 11 bits accumulated
						result += QrCode.PENALTY_N3;
				}
			}
			
			// Balance of black and white modules
			let black: int = 0;
			this.modules.forEach((row: Array<boolean>) => {
				row.forEach((color: boolean) => {
					if (color)
						black++;
				});
			});
			let total: int = this.size * this.size;
			// Find smallest k such that (45-5k)% <= dark/total <= (55+5k)%
			for (let k = 0; black*20 < (9-k)*total || black*20 > (11+k)*total; k++)
				result += QrCode.PENALTY_N4;
			return result;
		}
		
		
		/*-- Private static helper functions QrCode --*/
		
		// Returns a sequence of positions of the alignment patterns in ascending order. These positions are
		// used on both the x and y axes. Each value in the resulting sequence is in the range [0, 177).
		// This stateless pure function could be implemented as table of 40 variable-length lists of integers.
		private static getAlignmentPatternPositions(ver: int): Array<int> {
			if (ver < QrCode.MIN_VERSION || ver > QrCode.MAX_VERSION)
				throw "Version number out of range";
			else if (ver == 1)
				return [];
			else {
				let size: int = ver * 4 + 17;
				let numAlign: int = Math.floor(ver / 7) + 2;
				let step: int;
				if (ver != 32)
					step = Math.ceil((size - 13) / (2 * numAlign - 2)) * 2;
				else  // C-C-C-Combo breaker!
					step = 26;
				
				let result: Array<int> = [6];
				for (let i = 0, pos = size - 7; i < numAlign - 1; i++, pos -= step)
					result.splice(1, 0, pos);
				return result;
			}
		}
		
		
		// Returns the number of data bits that can be stored in a QR Code of the given version number, after
		// all function modules are excluded. This includes remainder bits, so it might not be a multiple of 8.
		// The result is in the range [208, 29648]. This could be implemented as a 40-entry lookup table.
		private static getNumRawDataModules(ver: int): int {
			if (ver < QrCode.MIN_VERSION || ver > QrCode.MAX_VERSION)
				throw "Version number out of range";
			let result: int = (16 * ver + 128) * ver + 64;
			if (ver >= 2) {
				let numAlign: int = Math.floor(ver / 7) + 2;
				result -= (25 * numAlign - 10) * numAlign - 55;
				if (ver >= 7)
					result -= 18 * 2;  // Subtract version information
			}
			return result;
		}
		
		
		// Returns the number of 8-bit data (i.e. not error correction) codewords contained in any
		// QR Code of the given version number and error correction level, with remainder bits discarded.
		// This stateless pure function could be implemented as a (40*4)-cell lookup table.
		private static getNumDataCodewords(ver: int, ecl: QrCode_Ecc): int {
			if (ver < QrCode.MIN_VERSION || ver > QrCode.MAX_VERSION)
				throw "Version number out of range";
			return Math.floor(QrCode.getNumRawDataModules(ver) / 8) -
				QrCode_ECC_CODEWORDS_PER_BLOCK[ecl.ordinal][ver] *
				QrCode_NUM_ERROR_CORRECTION_BLOCKS[ecl.ordinal][ver];
		}
		
		
		/*-- Constants --*/
		
		public static get MIN_VERSION(): int { return  1; }
		public static get MAX_VERSION(): int { return 40; }
		
		
		// For use in getPenaltyScore(), when evaluating which mask is best.
		private static get PENALTY_N1(): int { return 3; }
		private static get PENALTY_N2(): int { return 3; }
		private static get PENALTY_N3(): int { return 40; }
		private static get PENALTY_N4(): int { return 10; }
		
	}
	
	/*-- Private tables of constants --*/
	
	const QrCode_ECC_CODEWORDS_PER_BLOCK: Array<Array<int>> = [
		// Version: (note that index 0 is for padding, and is set to an illegal value)
		//0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
		[-1,  7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],  // Low
		[-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28],  // Medium
		[-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],  // Quartile
		[-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],  // High
	];
	
	const QrCode_NUM_ERROR_CORRECTION_BLOCKS: Array<Array<int>> = [
		// Version: (note that index 0 is for padding, and is set to an illegal value)
		//0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
		[-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4,  4,  4,  4,  4,  6,  6,  6,  6,  7,  8,  8,  9,  9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25],  // Low
		[-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5,  5,  8,  9,  9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49],  // Medium
		[-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8,  8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68],  // Quartile
		[-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81],  // High
	];
	
	
	// Returns true iff the i'th bit of x is set to 1.
	function getBit(x: int, i: int): boolean {
		return ((x >>> i) & 1) != 0;
	}
	
	
	
	/*---- Public helper enumeration ----*/
	
	/* 
	 * Represents the error correction level used in a QR Code symbol.
	 */
	export class QrCode_Ecc {
		
		/*-- Constants --*/
		
		public static get LOW     () { return new QrCode_Ecc(0, 1); }
		public static get MEDIUM  () { return new QrCode_Ecc(1, 0); }
		public static get QUARTILE() { return new QrCode_Ecc(2, 3); }
		public static get HIGH    () { return new QrCode_Ecc(3, 2); }
		
		
		/*-- Fields --*/
		
		// In the range 0 to 3 (unsigned 2-bit integer).
		public readonly ordinal: int;
		
		// (Package-private) In the range 0 to 3 (unsigned 2-bit integer).
		public readonly formatBits: int;
		
		
		/*-- Constructor --*/
		
		private constructor(ord: int, fb: int) {
			this.ordinal = ord;
			this.formatBits = fb;
		}
		
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
	export class QrSegment {
		
		/*-- Static factory functions --*/
		
		// Returns a segment representing the given binary data encoded in byte mode.
		public static makeBytes(data: Array<byte>): QrSegment {
			let bb = new BitBuffer();
			data.forEach(
				(b: byte) => bb.appendBits(b, 8));
			return new QrSegment(QrSegment_Mode.BYTE, data.length, bb);
		}
		
		
		// Returns a segment representing the given string of decimal digits encoded in numeric mode.
		public static makeNumeric(digits: string): QrSegment {
			if (!this.NUMERIC_REGEX.test(digits))
				throw "String contains non-numeric characters";
			let bb = new BitBuffer();
			let i: int;
			for (i = 0; i + 3 <= digits.length; i += 3)  // Process groups of 3
				bb.appendBits(parseInt(digits.substr(i, 3), 10), 10);
			let rem: int = digits.length - i;
			if (rem > 0)  // 1 or 2 digits remaining
				bb.appendBits(parseInt(digits.substring(i), 10), rem * 3 + 1);
			return new QrSegment(QrSegment_Mode.NUMERIC, digits.length, bb);
		}
		
		
		// Returns a segment representing the given text string encoded in alphanumeric mode.
		// The characters allowed are: 0 to 9, A to Z (uppercase only), space,
		// dollar, percent, asterisk, plus, hyphen, period, slash, colon.
		public static makeAlphanumeric(text: string): QrSegment {
			if (!this.ALPHANUMERIC_REGEX.test(text))
				throw "String contains unencodable characters in alphanumeric mode";
			let bb = new BitBuffer();
			let i: int;
			for (i = 0; i + 2 <= text.length; i += 2) {  // Process groups of 2
				let temp: int = QrSegment.ALPHANUMERIC_CHARSET.indexOf(text.charAt(i)) * 45;
				temp += QrSegment.ALPHANUMERIC_CHARSET.indexOf(text.charAt(i + 1));
				bb.appendBits(temp, 11);
			}
			if (i < text.length)  // 1 character remaining
				bb.appendBits(QrSegment.ALPHANUMERIC_CHARSET.indexOf(text.charAt(i)), 6);
			return new QrSegment(QrSegment_Mode.ALPHANUMERIC, text.length, bb);
		}
		
		
		// Returns a new mutable list of zero or more segments to represent the given Unicode text string.
		// The result may use various segment modes and switch modes to optimize the length of the bit stream.
		public static makeSegments(text: string): Array<QrSegment> {
			// Select the most efficient segment encoding automatically
			if (text == "")
				return [];
			else if (this.NUMERIC_REGEX.test(text))
				return [QrSegment.makeNumeric(text)];
			else if (this.ALPHANUMERIC_REGEX.test(text))
				return [QrSegment.makeAlphanumeric(text)];
			else
				return [QrSegment.makeBytes(QrSegment.toUtf8ByteArray(text))];
		}
		
		
		// Returns a segment representing an Extended Channel Interpretation
		// (ECI) designator with the given assignment value.
		public static makeEci(assignVal: int): QrSegment {
			let bb = new BitBuffer();
			if (0 <= assignVal && assignVal < (1 << 7))
				bb.appendBits(assignVal, 8);
			else if ((1 << 7) <= assignVal && assignVal < (1 << 14)) {
				bb.appendBits(2, 2);
				bb.appendBits(assignVal, 14);
			} else if ((1 << 14) <= assignVal && assignVal < 1000000) {
				bb.appendBits(6, 3);
				bb.appendBits(assignVal, 21);
			} else
				throw "ECI assignment value out of range";
			return new QrSegment(QrSegment_Mode.ECI, 0, bb);
		}
		
		
		/*-- Fields --*/
		
		// The mode indicator for this segment.
		public readonly mode: QrSegment_Mode;
		
		// The length of this segment's unencoded data, measured in characters. Always zero or positive.
		public readonly numChars: int;
		
		private readonly bitData: Array<bit>;
		
		
		/*-- Constructor --*/
		
		public constructor(mode: QrSegment_Mode, numChars: int, bitData: Array<bit>) {
			if (numChars < 0)
				throw "Invalid argument";
			this.mode = mode;
			this.numChars = numChars;
			this.bitData = bitData.slice();  // Make defensive copy
		}
		
		
		/*-- Methods --*/
		
		// Returns a copy of all bits, which is an array of 0s and 1s.
		public getBits(): Array<bit> {
			return this.bitData.slice();  // Make defensive copy
		}
		
		
		// Package-private helper function.
		public static getTotalBits(segs: Array<QrSegment>, version: int): int|null {
			if (version < QrCode.MIN_VERSION || version > QrCode.MAX_VERSION)
				throw "Version number out of range";
			let result: int = 0;
			for (let i = 0; i < segs.length; i++) {
				let seg: QrSegment = segs[i];
				let ccbits: int = seg.mode.numCharCountBits(version);
				// Fail if segment length value doesn't fit in the length field's bit-width
				if (seg.numChars >= (1 << ccbits))
					return null;
				result += 4 + ccbits + seg.getBits().length;
			}
			return result;
		}
		
		
		// Returns a new array of bytes representing the given string encoded in UTF-8.
		private static toUtf8ByteArray(str: string): Array<byte> {
			str = encodeURI(str);
			let result: Array<byte> = [];
			for (let i = 0; i < str.length; i++) {
				if (str.charAt(i) != "%")
					result.push(str.charCodeAt(i));
				else {
					result.push(parseInt(str.substr(i + 1, 2), 16));
					i += 2;
				}
			}
			return result;
		}
		
		
		/*-- Constants --*/
		
		// Can test whether a string is encodable in numeric mode (such as by using QrSegment.makeNumeric()).
		public static get NUMERIC_REGEX(): RegExp {
			return /^[0-9]*$/;
		}
		
		// Can test whether a string is encodable in alphanumeric mode (such as by using QrSegment.makeAlphanumeric()).
		public static get ALPHANUMERIC_REGEX(): RegExp {
			return /^[A-Z0-9 $%*+.\/:-]*$/;
		}
		
		// The set of all legal characters in alphanumeric mode, where each character value maps to the index in the string.
		private static get ALPHANUMERIC_CHARSET(): string {
			return "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";
		}
		
	}
	
	
	
	/*---- Public helper enumeration ----*/
	
	/* 
	 * Represents the mode field of a segment. Immutable.
	 */
	export class QrSegment_Mode {
		
		/*-- Constants --*/
		
		public static get NUMERIC     () { return new QrSegment_Mode(0x1, [10, 12, 14]); }
		public static get ALPHANUMERIC() { return new QrSegment_Mode(0x2, [ 9, 11, 13]); }
		public static get BYTE        () { return new QrSegment_Mode(0x4, [ 8, 16, 16]); }
		public static get KANJI       () { return new QrSegment_Mode(0x8, [ 8, 10, 12]); }
		public static get ECI         () { return new QrSegment_Mode(0x7, [ 0,  0,  0]); }
		
		
		/*-- Fields --*/
		
		// An unsigned 4-bit integer value (range 0 to 15) representing the mode indicator bits for this mode object.
		public readonly modeBits: int;
		
		private readonly numBitsCharCount: [int,int,int];
		
		
		/*-- Constructor --*/
		
		private constructor(mode: int, ccbits: [int,int,int]) {
			this.modeBits = mode;
			this.numBitsCharCount = ccbits;
		}
		
		
		/*-- Method --*/
		
		// (Package-private) Returns the bit width of the segment character count field for this mode object at the given version number.
		public numCharCountBits(ver: int): int {
			if      ( 1 <= ver && ver <=  9)  return this.numBitsCharCount[0];
			else if (10 <= ver && ver <= 26)  return this.numBitsCharCount[1];
			else if (27 <= ver && ver <= 40)  return this.numBitsCharCount[2];
			else  throw "Version number out of range";
		}
		
	}
	
	
	
	/*---- Private helper classes ----*/
	
	/* 
	 * Computes the Reed-Solomon error correction codewords for a sequence of data codewords
	 * at a given degree. Objects are immutable, and the state only depends on the degree.
	 * This class exists because each data block in a QR Code shares the same the divisor polynomial.
	 */
	class ReedSolomonGenerator {
		
		// Coefficients of the divisor polynomial, stored from highest to lowest power, excluding the leading term which
		// is always 1. For example the polynomial x^3 + 255x^2 + 8x + 93 is stored as the uint8 array {255, 8, 93}.
		private readonly coefficients: Array<byte> = [];
		
		
		// Creates a Reed-Solomon ECC generator for the given degree. This could be implemented
		// as a lookup table over all possible parameter values, instead of as an algorithm.
		public constructor(degree: int) {
			if (degree < 1 || degree > 255)
				throw "Degree out of range";
			let coefs = this.coefficients;
			
			// Start with the monomial x^0
			for (let i = 0; i < degree - 1; i++)
				coefs.push(0);
			coefs.push(1);
			
			// Compute the product polynomial (x - r^0) * (x - r^1) * (x - r^2) * ... * (x - r^{degree-1}),
			// drop the highest term, and store the rest of the coefficients in order of descending powers.
			// Note that r = 0x02, which is a generator element of this field GF(2^8/0x11D).
			let root = 1;
			for (let i = 0; i < degree; i++) {
				// Multiply the current product by (x - r^i)
				for (let j = 0; j < coefs.length; j++) {
					coefs[j] = ReedSolomonGenerator.multiply(coefs[j], root);
					if (j + 1 < coefs.length)
						coefs[j] ^= coefs[j + 1];
				}
				root = ReedSolomonGenerator.multiply(root, 0x02);
			}
		}
		
		
		// Computes and returns the Reed-Solomon error correction codewords for the given
		// sequence of data codewords. The returned object is always a new byte array.
		// This method does not alter this object's state (because it is immutable).
		public getRemainder(data: Array<byte>): Array<byte> {
			// Compute the remainder by performing polynomial division
			let result: Array<byte> = this.coefficients.map(_ => 0);
			data.forEach((b: byte) => {
				let factor: byte = b ^ (result.shift() as int);
				result.push(0);
				for (let i = 0; i < result.length; i++)
					result[i] ^= ReedSolomonGenerator.multiply(this.coefficients[i], factor);
			});
			return result;
		}
		
		
		// Returns the product of the two given field elements modulo GF(2^8/0x11D). The arguments and result
		// are unsigned 8-bit integers. This could be implemented as a lookup table of 256*256 entries of uint8.
		private static multiply(x: byte, y: byte): byte {
			if (x >>> 8 != 0 || y >>> 8 != 0)
				throw "Byte out of range";
			// Russian peasant multiplication
			let z: int = 0;
			for (let i = 7; i >= 0; i--) {
				z = (z << 1) ^ ((z >>> 7) * 0x11D);
				z ^= ((y >>> i) & 1) * x;
			}
			if (z >>> 8 != 0)
				throw "Assertion error";
			return z as byte;
		}
		
	}
	
	
	
	/* 
	 * An appendable sequence of bits. The implicit constructor creates an empty bit buffer (length 0).
	 */
	class BitBuffer extends Array<bit> {
		
		// Packs this buffer's bits into bytes in big endian,
		// padding with '0' bit values, and returns the new array.
		public getBytes(): Array<byte> {
			let result: Array<byte> = [];
			while (result.length * 8 < this.length)
				result.push(0);
			this.forEach((b: bit, i: int) =>
				result[i >>> 3] |= b << (7 - (i & 7)));
			return result;
		}
		
		
		// Appends the given number of low bits of the given
		// value to this sequence. Requires 0 <= val < 2^len.
		public appendBits(val: int, len: int): void {
			if (len < 0 || len > 31 || val >>> len != 0)
				throw "Value out of range";
			for (let i = len - 1; i >= 0; i--)  // Append bit by bit
				this.push((val >>> i) & 1);
		}
		
	}
	
}
