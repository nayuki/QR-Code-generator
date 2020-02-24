/* 
 * QR Code generator library (Swift)
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

import BinaryKit
import Foundation

struct QRCode {
	// Scalar parameters:

	/// The version number of this QR Code, which is between 1 and 40 (inclusive).
	/// This determines the size of this barcode.
	public let version: QRCodeVersion
	/// The width and height of this QR Code, measured in modules, between
	/// 21 and 177 (inclusive). This is equal to version * 4 + 17.
	public let size: Int
	/// The error correction level used in this QR Code.
	public let errorCorrectionLevel: QRCodeECC
	/// The index of the mask pattern used in this QR Code, which is between 0 and 7 (inclusive).
	/// Even if a QR Code is created with automatic masking requested (mask = None),
	/// the resulting object still has a mask value between 0 and 7.
	public private(set) var mask: QRCodeMask

	// Grids of modules/pixels, with dimensions of size*size:
	
	/// The modules of this QR Code (false = white, true = black).
	/// Immutable after constructor finishes. Accessed through get_module().
	private var modules: [Bool]
	
	/// Indicates function modules that are not subjected to masking. Discarded when constructor finishes.
	private var isFunction: [Bool]
	
	/*---- Static factory functions (high level) ----*/

	/// Returns a QR Code representing the given Unicode text string at the given error correction level.
	/// 
	/// As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer Unicode
	/// code points (not UTF-8 code units) if the low error correction level is used. The smallest possible
	/// QR Code version is automatically chosen for the output. The ECC level of the result may be higher than
	/// the ecl argument if it can be done without increasing the version.
	/// 
	/// Returns a wrapped `QrCode` if successful, or `Err` if the
	/// data is too long to fit in any version at the given ECC level.
	public static func encode(text: String, ecl: QRCodeECC) throws -> Self {
		let chrs = Array(text)
		let segs = QRSegment.makeSegments(chrs)
		return try QRCode.encode(segments: segs, ecl: ecl)
	}
	
	/// Returns a QR Code representing the given binary data at the given error correction level.
	/// 
	/// This function always encodes using the binary segment mode, not any text mode. The maximum number of
	/// bytes allowed is 2953. The smallest possible QR Code version is automatically chosen for the output.
	/// The ECC level of the result may be higher than the ecl argument if it can be done without increasing the version.
	/// 
	/// Returns a wrapped `QrCode` if successful, or `Err` if the
	/// data is too long to fit in any version at the given ECC level.
	public static func encode(binary data: [UInt8], ecl: QRCodeECC) throws -> Self {
		let segs = [QRSegment.make(bytes: data)]
		return try QRCode.encode(segments: segs, ecl: ecl)
	}
	
	/*---- Static factory functions (mid level) ----*/
	
	/// Returns a QR Code representing the given segments at the given error correction level.
	/// 
	/// The smallest possible QR Code version is automatically chosen for the output. The ECC level
	/// of the result may be higher than the ecl argument if it can be done without increasing the version.
	/// 
	/// This function allows the user to create a custom sequence of segments that switches
	/// between modes (such as alphanumeric and byte) to encode text in less space.
	/// This is a mid-level API; the high-level API is `encode_text()` and `encode_binary()`.
	/// 
	/// Returns a wrapped `QrCode` if successful, or `Err` if the
	/// data is too long to fit in any version at the given ECC level.
	public static func encode(segments: [QRSegment], ecl: QRCodeECC) throws -> Self {
		try QRCode.encodeAdvanced(segments: segments, ecl: ecl, minVersion: qrCodeMinVersion, maxVersion: qrCodeMaxVersion, boostECL: true)
	}
	
	/// Returns a QR Code representing the given segments with the given encoding parameters.
	/// 
	/// The smallest possible QR Code version within the given range is automatically
	/// chosen for the output. Iff boostecl is `true`, then the ECC level of the result
	/// may be higher than the ecl argument if it can be done without increasing the
	/// version. The mask number is either between 0 to 7 (inclusive) to force that
	/// mask, or `None` to automatically choose an appropriate mask (which may be slow).
	/// 
	/// This function allows the user to create a custom sequence of segments that switches
	/// between modes (such as alphanumeric and byte) to encode text in less space.
	/// This is a mid-level API; the high-level API is `encode_text()` and `encode_binary()`.
	/// 
	/// Returns a wrapped `QrCode` if successful, or `Err` if the data is too
	/// long to fit in any version in the given range at the given ECC level.
	public static func encodeAdvanced(segments: [QRSegment], ecl: QRCodeECC, minVersion: QRCodeVersion, maxVersion: QRCodeVersion, mask: QRCodeMask? = nil, boostECL: Bool) throws -> Self {
		assert(minVersion <= maxVersion, "Invalid value")
		
		// Find the minimal version number to use
		var version = minVersion
		var dataUsedBits: UInt!
		while true {
			// Number of data bits available
			let dataCapacityBits: UInt = QRCode.getNumDataCodewords(version: version, ecl: ecl) * 8
			let dataUsed: UInt? = QRSegment.getTotalBits(segments: segments, version: version)
			if let used = dataUsed, used <= dataCapacityBits {
				// The version number is found to be suitable
				dataUsedBits = used
				break
			} else if version >= maxVersion {
				let msg: String
				if let used = dataUsed {
					msg = "Data length = \(used) bits, Max capacity = \(dataCapacityBits) bits"
				} else {
					msg = "Segment too long"
				}
			} else {
				version = QRCodeVersion(version.value + 1)
			}
		}
		
		// Increase error correction level while the data still fits in the current version number
		for newECL in [QRCodeECC.medium, QRCodeECC.quartile, QRCodeECC.high] {
			if boostECL && dataUsedBits <= QRCode.getNumDataCodewords(version: version, ecl: newECL) * 8 {
				ecl = newECL
			}
		}
		
		// Concatenate all segments to create the data bit string
		var bb = BitBuffer()
		for seg in segments {
			bb.appendBits(seg.mode.modeBits(), 4)
			bb.appendBits(UInt32(seg.numChars), seg.mode.numCharCountBits(version: version))
			bb.values += seg.data
		}
		
		assert(bb.count == dataUsedBits)
		
		// Add terminator and pad up to a byte if applicable
		let dataCapacityBits: UInt = QRCode.getNumDataCodeWords(version: version, ecl: ecl)
		assert(bb.count <= dataCapacityBits)
		var numZeroBits = min(4, dataCapacityBits - bb.count)
		bb.appendBits(0, UInt8(numZeroBits))
		numZeroBits = (0 &- bb.count) & 7
		bb.appendBits(0, UInt8(numZeroBits))
		assert(bb.count % 8 == 0)
		
		// Pad with alternating bytes until data capacity is reached
		let padBytes = [0xEC, 0x11]
		var i = 0
		while bb.count < dataCapacityBits {
			bb.appendBits(padBytes[i], 8)
			i += 1
			if i >= padBytes.count {
				i = 0
			}
		}
		
		// Pack bits into bytes in big endian
		var dataCodeWords = [UInt8](repeating: 0, bb.count / 8)
		for (i, bit) in bb.values.enumerated() {
			dataCodeWords[i >> 3] |= UInt8(bit) << (7 - (i & 7))
		}
		
		// Create the QRCode object
		return QRCode.encodeCodewords(version: version, ecl: ecl, dataCodeWords: dataCodeWords, mask: mask)
	}
	
	/*---- Constructor (low level) ----*/
	
	/// Creates a new QR Code with the given version number,
	/// error correction level, data codeword bytes, and mask number.
	/// 
	/// This is a low-level API that most users should not use directly.
	/// A mid-level API is the `encode_segments()` function.
	public static func encodeCodewords(version: QRCodeVersion, ecl: QRCodeECC, dataCodeWords: [UInt8], mask: QRCodeMask? = nil) -> Self {
		var mutMask = mask

		// Initialize fields
		let size = UInt(version.value)
		var result = Self(
			version: version,
			size: Int(size),
			mask: QRCodeMask(0), // Dummy value
			errorCorrectionLevel: ecl,
			modules: Array(repeating: false, count: size * size), // Initially all white
			isFunction: Array(repeating: false, count: size * size)
		)
		
		// Compute ECC, draw modules
		result.drawFunctionPatterns()
		let allCodeWords = result.addECCAndInterleave(dataCodeWords: dataCodeWords)
		result.drawCodewords(data: allCodeWords)
		
		// Do masking
		if mask == nil { // Automatically choose best mask
			var minPenalty = Int32.max
			for i in UInt8(0)..<8 {
				let newMask = QRCodeMask(i)
				result.apply(mask: newMask)
				result.drawFormatBits(mask: newMask)
				let penalty = result.getPenaltyScore()
				if penalty < minPenalty {
					mutMask = newMask
					minPenalty = penalty
				}
				result.apply(mask: newMask) // Undoes mask due to XOR
			}
		}
		let mask: QRCodeMask = mask!
		result.mask = mask
		result.apply(mask: mask) // Apply the final choice of mask
		result.drawFormatBits(mask: mask)
		
		result.isFunction = []
		return result
	}
	
	/*---- Public methods ----*/
	
	/// Returns the color of the module (pixel) at the given coordinates,
	/// which is `false` for white or `true` for black.
	/// 
	/// The top left corner has the coordinates (x=0, y=0). If the given
	/// coordinates are out of bounds, then `false` (white) is returned.
	public func getModule(x: Int, y: Int) -> Bool {
		0 <= x && x < size && 0 <= y && y < size && self[x, y]
	}
	
	private subscript(_ x: Int, _ y: Int) -> Bool {
		/// Returns the color of the module at the given coordinates, which
		/// are assumed to be in bounds.
		get { modules[y * size + x] }
		/// Sets the color of the module at the given coordintes.
		set { modules[y * size + x] = newValue }
	}

	/// Returns a string of SVG code for an image depicting
	/// this QR Code, with the given number of border modules.
	/// 
	/// The string always uses Unix newlines (\n), regardless of the platform.
	public func toSVGString(border: Int) -> String {
		assert(border >= 0, "Border must be non-negative")
		let dimension = size + (border * 2)
		let path = (0..<size).map { y in
			(0..<size).map { x in
				getModule(x: x, y: y)
					? "\(x != 0 || y != 0 ? " " : "")M\(x + border),\(y + border)h1v1h-1z"
					: ""
			}
		}
		var result = """
			<?xml version="1.0" encoding="UTF-8"?>
			<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
			<svg xmlns="http://www.w3.org/2000/svg" version="1.1" viewBox="0 0 \(dimension) \(dimension)" stroke="none">
			  <rect width="100%" height="100%" fill="#FFFFFF"/>
			  <rect width="100%" height="100%" fil="#FFFFFF"/>
			  <path d="\(path)" fill="#000000"/>
			</svg>
			"""
		return result
	}

	/*---- Private helper methods for constructor: Drawing function modules ----*/

	/// Reads this object's version field, and draws and marks all function modules.
	private mutating func drawFunctionPatterns() {
		// Draw horizontal and vertical timing patterns
		for i in 0..<size {
			setFunctionModule(x: 6, y: i, isBlack: i % 2 == 0)
			setFunctionModule(x: i, y: 6, isBlack: i % 2 == 0)
		}
		
		// Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
		drawFinderPattern(x: 3, y: 3)
		drawFinderPattern(x: size - 4, y: 3)
		drawFinderPattern(x: 3, y: size - 4)
		
		// Draw numerous alignment patterns
		let alignmentPatPos: [Int] = getAlignmentPatternPositions()
		let numAlign = alignmentPatPos.count
		for i in 0..<numAlign {
			for j in 0..<numAlign {
				// Don't draw on the three finder corners
				if !((i == 0 && j == 0) || (i == 0 && j == numAlign - 1) || (i == numAlign - 1 && j == 0)) {
					drawAlignmentPattern(x: alignmentPatPos[i], y: alignmentPatPos[j])
				}
			}
		}
		
		// Draw configuration data
		drawFormatBits(mask: QRCodeMask(0)) // Dummy mask value; overwritten later in the constructor
		drawVersion()
	}
	
	/// Draws two copies of the format bits (with its own error correction code)
	/// based on the given mask and this object's error correction level field.
	private mutating func drawFormatBits(mask: QRCodeMask) {
		// Calculate error correction code and pack bits

		// Error correction level is uint2, mask is uint3
		let data: UInt32 = errorCorrectionLevel.formatBits() << 3 | UInt32(mask.value)
		var rem: UInt32 = data
		for _ in 0..<10 {
			rem = (rem << 1) ^ ((rem > 9) * 0x537)
		}
		let bits: UInt32 = (data << 10 | rem) ^ 0x5412 // uint15
		
		// Draw first copy
		for i in 0..<6 {
			setFunctionModule(x: 8, y: i, isBlack: getBit(bits, i))
		}
		setFunctionModule(x: 8, y: 7, isBlack: getBit(bits, 6))
		setFunctionModule(x: 8, y: 8, isBlack: getBit(bits, 7))
		setFunctionModule(x: 7, y: 8, isBlack: getBit(bits, 8))
		for i in 9..<15 {
			setFunctionModule(x: 14 - i, y: 8, isBlack: getBit(bits, i))
		}
		
		// Draw second copy
		for i in 0..<8 {
			setFunctionModule(x: size - 1 - i, y: 8, isBlack: getBit(bits, i))
		}
		for i in 0..<15 {
			setFunctionModule(x: 8, y: size - 15 + i, isBlack: getBit(bits, i))
		}
		setFunctionModule(x: 8, y: size - 8, isBlack: true) // Always black
	}

	/// Draws two copies of the version bits (with its own error correction code),
	/// based on this object's version field, iff 7 <= version <= 40.
	private mutating func drawVersion() {
		guard version.value >= 7 else { return }
		
		// Calculate error correction code and pack bits
		let data = UInt32(version.value) // uint6, in the range [7, 40]
		var rem = data
		for _ in 0..<12 {
			rem = (rem << 1) ^ ((rem >> 11) * 0x1F25)
		}
		let bits: UInt32 = data << 12 | rem // uint18
		assert(bits >> 18 == 0)
		
		// Draw two copies
		for i in 0..<18 {
			let bit = getBit(bits, i)
			let a: Int = size - 11 + i % 3
			let b: Int = i / 3
			setFunctionModule(x: a, y: b, isBlack: bit)
			setFunctionModule(x: b, y: a, isBlack: bit)
		}
	}
	
	/// Draws a 9*9 finder pattern including the border separator,
	/// with the center module at (x, y). Modules can be out of bounds.
	private mutating func drawFinderPattern(x: Int, y: Int) {
		for dy in -4...4 {
			for dx in -4...4 {
				let xx: Int = x + dx
				let yy: Int = y + dy
				if 0 <= xx && xx < size && 0 <= yy && yy < size {
					let dist: Int = max(abs(dx), abs(dy))
					setFunctionModule(x: xx, y: yy, isBlack: dist != 2 && dist != 4)
				}
			}
		}
	}
	
	/// Draws a 5*5 alignment pattern, with the center module
	/// at (x, y). All modules must be in bounds.
	private mutating func drawAlignmentPattern(x: Int, y: Int) {
		for dy in -2...2 {
			for dx in -2...2 {
				setFunctionModule(x: x + dx, y: y + dy, isBlack: max(abs(dx), abs(dy)) != 1)
			}
		}
	}
	
	/// Sets the color of a module and marks it as a function mdoule.
	/// Only used by the constructor. Coordinates must be in bounds.
	private mutating func setFunctionModule(x: Int, y: Int, isBlack: Bool) {
		self[x, y] = isBlack
		isFunction[y * size + x] = true
	}

	/*---- Private helper methods for constructor: Codewords and masking ----*/

	/// Returns a new byte string representing the given data with the appropriate error correction
	/// codewords appended to it, based on this object's version and error correction level.
	private func addECCAndInterleave(data: [UInt8]) -> [UInt8] {
		let ver = version
		let ecl = errorCorrectionLevel
		assert(data.count == QRCode.getNumDataCodewords(version: ver, ecl: ecl), "Illegal argument")
		
		// Calculate parameter numbers
		let numBlocks: UInt = QRCode.tableGet(numErrorCorrectionBlocks, version: ver, ecl: ecl)
		let blockECCLen: UInt = QRCode.tableGet(eccCodewordsPerBlock, version: ver, ecl: ecl)
		let rawCodeWords: UInt = QRCode.getNumRawDataModules(version: ver) / 8
		let numShortBlocks: UInt = numBlocks - rawCodeWords % numBlocks
		let shortBlockLen: UInt = rawCodeWords / numBlocks
		
		// Split data into blocks and append ECC to each block
		var blocks = [[UInt8]]()
		let rsDiv: [UInt8] = QRCode.reedSolomonComputeDivisor(degree: blockECCLen)
		var k: UInt = 0
		for i in 0..<numBlocks {
			let datLen: UInt = shortBlockLen - blockECCLen + (i >= numShortBlocks ? 1 : 0)
			var dat = Array(d[k..<(k + datLen)])
			k += datLen
			let ecc: [UInt8] = QRCode.reedSolomonComputeRemainder(data: dat, divisor: rsDiv)
			if i < numShortBlocks {
				dat.append(0)
			}
			dat += ecc
			blocks.append(dat)
		}
		
		// Interleave (not concatenate) the bytes from every block into a single sequence
		var result = [UInt8]()
		for i in 0...shortBlockLen {
			for (j, block) in blocks.enumerated() {
				// Skip the padding byte in short blocks
				if i != shortBlockLen - blockECCLen || j >= numShortBlocks {
					result.push(block[i])
				}
			}
		}
		
		return result
	}
	
	/// Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
	/// data area of this QR Code. Function modules need to be marked off before this is called.
	private mutating func drawCodewords(data: [UInt8]) {
		assert(data.count == QRCode.getNumRawDataModules(version: version) / 8, "Illegal argument")
		
		var i: Int = 0 // Bit index into the data
		// Do the funny zigzag scan
		var right: Int = size - 1
		while right >= 1 { // Index of right column in each column pair
			if right == 6 {
				right = 5
			}
			for vert in 0..<size { // Vertical counter
				for j in 0..<2 {
					let x: Int = right - j // Actual x coordinate
					let upward: Bool = (right + 1) & 2 == 0
					let y: Int = upward ? (size - 1 - vert) : vert
					if !isFunction[y * size + x] && i < data.count * 8 {
						self[x, y] = getBit(UInt32(data[i >> 3]), 7 - (Int32(i & 7)))
						i += 1
					}
					// If this QR code has any remainder bits (0 to 7), they were assigned as
					// 0/false/white by the constructor and are left unchanged by this method
				}
			}
			right -= 2
		}
		assert(i == data.count * 8)
	}
	
	// XORs the codeword modules in this QR Code with the given mask pattern.
	// The function modules must be marked and the codeword bits must be drawn
	// before masking. Due to the arithmetic of XOR, calling applyMask() with
	// the same mask value a second time will undo the mask. A final well-formed
	// QR Code needs exactly one (not zero, two, etc.) mask applied.
	private mutating func apply(mask: QRCodeMask) {
		for y in 0..<size {
			for x in 0..<size {
				let invert: Bool
				switch mask.value {
					case 0: invert = (x + y) % 2 == 0
					case 1: invert = y % 2 == 0
					case 2: invert = x % 3 == 0
					case 3: invert = (x + y) % 3 == 0
					case 4: invert = (x / 3 + y / 2) % 2 == 0
					case 5: invert = x * y % 2 + x * y % 3 == 0
					case 6: invert = (x * y % 2 + x * y % 3) % 2 == 0
					case 7: invert = ((x + y) % 2 + x * y % 3) % 2 == 0
				}
				self[x, y] ^= invert & !isFunction[y * size + x]
			}
		}
	}

	/// Calculates and returns the penalty score based on state of this QR Code's current modules.
	/// This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
	private func getPenaltyScore() -> Int {
		var result: Int = 0
		
		// Adjacent modules in row having same color and finder-like patterns
		for y in 0..<size {
			var runColor = false
			var runX: Int = 0
			var runHistory = FinderPenalty(size)
			for x in 0..<size {
				if self[x, y] == runColor {
					runX += 1
					if runX == 5 {
						result += penaltyN1
					} else if runX > 5 {
						result += 1
					}
				} else {
					runHistory.addHistory(runLength: runX)
					if !runColor {
						result += runHistory.countPatterns() * penaltyN3
					}
					runColor = self[x, y]
					runX = 1
				}
			}
			result += runHistory.terminateAndCount(runColor: runColor, runLength: runX) * penaltyN3
		}
		
		// Adjacent modules in column having same color and finder-like patterns
		for x in 0..<size {
			var runColor = false
			var runY: Int = 0
			var runHistory = FinderPenalty(size)
			for y in 0..<size {
				if self[x, y] == runColor {
					runY += 1
					if runY == 5 {
						result += penaltyN1
					} else if runY > 5 {
						result += 1
					}
				} else {
					runHistory.addHistory(runLength: runY)
					if !runColor {
						result += runHistory.countPatterns() * penaltyN3
					}
					runColor = self[x, y]
					runY = 1
				}
			}
			result += runHistory.terminateAndCount(runColor: runColor, runLength: runY) * penaltyN3
		}
		
		// 2*2 blocks of modules having same color
		for y in 0..<(size - 1) {
			for x in 0..<(size - 1) {
				let color: Bool = self[x, y]
				if color == self[x + 1, y] && color == self[x, y + 1] && color == self[x + 1, y + 1] {
					result += penaltyN2
				}
			}
		}
		
		// Balance of black and white modules
		let black: Int = modules.map(Int.init).sum()
		let total: Int = size * size // Note that size is odd, so black/total != 1/2
		// Compute the smallest integer k >= 0 such that (45 - 5k)% <= black/total <= (55+5k)%
		let k: Int = (abs(black * 20 - total * 10) + total - 1) / total - 1
		result += k * penaltyN4
		return result
	}
}
