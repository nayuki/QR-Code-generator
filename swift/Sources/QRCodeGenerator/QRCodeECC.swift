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

/// The error correction level in a QR Code symbol.
public enum QRCodeECC: UInt {
	/// The QR Code can tolerate about  7% erroneous codewords.
	case low = 0
	/// The QR Code can tolerate about 15% erroneous codewords.
	case medium = 1
	/// The QR Code can tolerate about 25% erroneous codewords.
	case quartile = 2
	/// The QR Code can tolerate about 30% erroneous codewords.
	case high = 3
	
	/// Returns an unsigned 2-bit integer (in the range 0 to 3).
	var ordinal: UInt { rawValue }

	/// Returns an unsigned 2-bit integer (in the range 0 to 3).
	var formatBits: UInt32 {
		switch self {
			case .low: return 1
			case .medium: return 0
			case .quartile: return 3
			case .high: return 2
		}
	}
}
