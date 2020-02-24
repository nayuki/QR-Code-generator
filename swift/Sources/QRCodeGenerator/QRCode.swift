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

struct QRCode {
	// Scalar parameters:

	/// The version number of this QR Code, which is between 1 and 40 (inclusive).
	/// This determines the size of this barcode.
	private let version: QRCodeVersion
	/// The width and height of this QR Code, measured in modules, between
	/// 21 and 177 (inclusive). This is equal to version * 4 + 17.
	private let size: Int
	/// The error correction level used in this QR Code.
	private let errorCorrectionLevel: QRCodeECC
	/// The index of the mask pattern used in this QR Code, which is between 0 and 7 (inclusive).
	/// Even if a QR Code is created with automatic masking requested (mask = None),
	/// the resulting object still has a mask value between 0 and 7.
	private let mask: Mask

	// Grids of modules/pixels, with dimensions of size*size:
	
	/// The modules of this QR Code (false = white, true = black).
	/// Immutable after constructor finishes. Accessed through get_module().
	private let modules: [Bool]
	
	/// Indicates function modules that are not subjected to masking. Discarded when constructor finishes.
	private let isFunction: [Bool]
	
	// TODO: Implement methods
}
