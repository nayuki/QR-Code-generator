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

/// A number between 1 and 40 (inclusive).
public struct QRCodeVersion: Hashable, Comparable {
    public let value: UInt8
    
    public init(value: UInt8) {
        assert(1 <= value && value <= 40, "Version number out of range")
        self.value = value
    }
    
    public static func <(lhs: QRCodeVersion, rhs: QRCodeVersion) -> Bool {
        lhs.value < rhs.value
    }
}

/// A number between 0 and 7 (inclusive).
public struct QRCodeMask: Hashable {
    public let value: UInt8
    
    public init(value: UInt8) {
        assert(value <= 7, "Mask value out of range")
        self.value = value
    }
}

public enum QRCodeError: Error {
    /// The error type when the supplied data does not fit any QR Code version.
    ///
    /// Ways to handle this exception include:
    /// 
    /// - Decrease the error correction level if it was greater than `QRCodeECC.low`.
    /// - If the `encodeSegmentsAdvanced()` function was called, then increase the maxversion
    ///   argument if it was less than `qrCodeMaxVersion`. (This advice does not apply to the
    ///   other factory functions because they search all versions up to `qrCodeMaxVersion`.)
    /// - Split the text data into better or optimal segments in order to reduce the number of bits required.
    /// - Change the text or binary data to be shorter.
    /// - Change the text to fit the character set of a particular segment mode (e.g. alphanumeric).
    /// - Propagate the error upward to the caller/user.
    case dataTooLong(String)
}

/// Returns true iff the i'th bit of x is set to 1.
func getBit(x: UInt32, i: UInt32) -> Bool {
    (x >> i) & 1 != 0
}
