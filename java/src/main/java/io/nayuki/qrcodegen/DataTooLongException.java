/* 
 * QR Code generator library (Java)
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

package io.nayuki.qrcodegen;


/**
 * Thrown when the supplied data does not fit any QR Code version. Ways to handle this exception include:
 * <ul>
 *   <li><p>Decrease the error correction level if it was greater than {@code Ecc.LOW}.</p></li>
 *   <li><p>If the advanced {@code encodeSegments()} function with 6 arguments or the
 *     {@code makeSegmentsOptimally()} function was called, then increase the maxVersion argument
 *     if it was less than {@link QrCode#MAX_VERSION}. (This advice does not apply to the other
 *     factory functions because they search all versions up to {@code QrCode.MAX_VERSION}.)</p></li>
 *   <li><p>Split the text data into better or optimal segments in order to reduce the number of
 *     bits required. (See {@link QrSegmentAdvanced#makeSegmentsOptimally(CharSequence,QrCode.Ecc,int,int)
 *     QrSegmentAdvanced.makeSegmentsOptimally()}.)</p></li>
 *   <li><p>Change the text or binary data to be shorter.</p></li>
 *   <li><p>Change the text to fit the character set of a particular segment mode (e.g. alphanumeric).</p></li>
 *   <li><p>Propagate the error upward to the caller/user.</p></li>
 * </ul>
 * @see QrCode#encodeText(CharSequence, QrCode.Ecc)
 * @see QrCode#encodeBinary(byte[], QrCode.Ecc)
 * @see QrCode#encodeSegments(java.util.List, QrCode.Ecc)
 * @see QrCode#encodeSegments(java.util.List, QrCode.Ecc, int, int, int, boolean)
 * @see QrSegmentAdvanced#makeSegmentsOptimally(CharSequence, QrCode.Ecc, int, int)
 */
public class DataTooLongException extends IllegalArgumentException {
	
	public DataTooLongException() {}
	
	
	public DataTooLongException(String msg) {
		super(msg);
	}
	
}
