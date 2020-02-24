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

/*---- QrSegment functionality ----*/

/// A segment of character/binary/control data in a QR Code symbol.
/// 
/// Instances of this struct are immutable.
/// 
/// The mid-level way to create a segment is to take the payload data
/// and call a static factory function such as `QrSegment::make_numeric()`.
/// The low-level way to create a segment is to custom-make the bit buffer
/// and call the `QrSegment::new()` constructor with appropriate values.
/// 
/// This segment struct imposes no length restrictions, but QR Codes have restrictions.
/// Even in the most favorable conditions, a QR Code can only hold 7089 characters of data.
/// Any segment longer than this is meaningless for the purpose of generating QR Codes.
public struct QRSegment: Hashable {
	/// The mode indicator of this segment.
	public let mode: Mode
	/// The length of this segment's unencoded data. Measured in characters for
	/// numeric/alphanumeric/kanji mode, bytes for byte mode, and 0 for ECI mode.
	/// Not the same as the data's bit length.
	public let numChars: UInt
	/// The data bits of this segment.
	public let data: [Bool]
	
	/*---- Static factory functions (mid level) ----*/

	/// Returns a segment representing the given binary data encoded in byte mode.
	/// 
	/// All input byte slices are acceptable.
	/// 
	/// Any text string can be converted to UTF-8 bytes and encoded as a byte mode segment.
	public static func makeBytes(data: [UInt8]) -> Self {
		var bb = BitBuffer([])
		for b in data {
			bb.appendBits(UInt32(b), 8)
		}
		return QRSegment(mode: .byte, numChars: data.count, data: bb.bits)
	}
	
	/// Returns a segment representing the given string of decimal digits encoded in numeric mode.
	/// 
	/// Panics if the string contains non-digit characters.
	public static func makeNumeric(text: [Character]) -> Self {
		var bb = BitBuffer([])
		var accumData: UInt32 = 0
		var accumCount: UInt8 = 0
		for c in text {
			assert(c.isNumber && c.isASCII, "String contains non-numeric characters")
			accumData = accumData * 10 + (UInt32(c.asciiValue!) - UInt32("0".asciiValue!))
			accumCount += 1
			if accumCount == 3 {
				bb.appendBits(accumData, 10)
				accumData = 0
				accumCount = 0
			}
		}
		if accumCount > 0 { // 1 or 2 digits remaining
			bb.appendBits(accumData, accumCount * 3 + 1)
		}
		return QRSegment(mode: .numeric, numChars: text.count, data: bb.bits)
	}
	
	/// Returns a segment representing the given text string encoded in alphanumeric mode.
	/// 
	/// The characters allowed are: 0 to 9, A to Z (uppercase only), space,
	/// dollar, percent, asterisk, plus, hyphen, period, slash, colon.
	/// 
	/// Panics if the string contains non-encodable characters.
	public static func makeAlphanumeric(text: [Character]) -> Self {
		var bb = BitBuffer([])
		var accumData: UInt32 = 0
		var accumCount: UInt32 = 0
		for c in text {
			guard let i = alphanumericCharset.firstIndex(of: c) else {
				fatalError("String contains unencodable characters in alphanumeric mode")
			}
			accumData = accumData * 45 + UInt32(i)
			accumCount += 1
			if accumCount == 2 {
				bb.appendBits(accumData, 11)
				accumData = 0
				accumCount = 0
			}
		}
		if accumCount > 0 { // 1 character remaining
			bb.appendBits(accumData, 6)
		}
		return QRSegment(mode: .alphanumeric, numChars: text.count, data: bb.bits)
	}
	
	/// Returns a list of zero or more segments to represent the given Unicode text string.
	/// 
	/// The result may use various segment modes and switch
	/// modes to optimize the length of the bit stream.
	public static func makeSegments(text: [Character]) -> Self {
		if text.isEmpty {
			return []
		} else if QRSegment.isNumeric(text) {
			return [QRSegment.makeNumeric(text)]
		} else if QRSegment.isAlphanumeric(text) {
			return [QRSegment.makeAlphanumeric(text)]
		} else {
			let s = String(text)
			return [QRSegment.makeBytes([UInt8](s.data(using: .utf8)!))]
		}
	}
	
	/// Returns a segment representing an Extended Channel Interpretation
	/// (ECI) designator with the given assignment value.
	public static func makeECI(assignVal: UInt32) -> Self {
		var bb = BitBuffer([])
		if assignVal < (1 << 7) {
			bb.appendBits(assignVal, 8)
		} else if assignVal < (1 << 14) {
			bb.appendBits(2, 2)
			bb.appendBits(assignVal, 14)
		} else if assignVal < 1_000_000 {
			bb.appendBits(6, 3)
			bb.appendBits(assignVal, 21)
		} else {
			fatalError("ECI assignment value out of range")
		}
		return QRSegment(mode: .eci, numChars: 0, data: bb.bits)
	}
}
