/* 
 * QR Code generator library (Java)
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

package io.nayuki.qrcodegen;

import java.util.Arrays;


/**
 * An appendable sequence of bits. Bits are packed in big endian within a byte.
 */
final class BitBuffer {
	
	/*---- Fields ----*/
	
	private byte[] data;
	private int bitLength;
	
	
	
	/*---- Constructor ----*/
	
	// Creates an empty bit buffer (length 0).
	public BitBuffer() {
		data = new byte[16];
		bitLength = 0;
	}
	
	
	
	/*---- Methods ----*/
	
	// Returns the number of bits in the buffer, which is a non-negative value.
	public int bitLength() {
		return bitLength;
	}
	
	
	// Returns a copy of all bytes, padding up to the nearest byte.
	public byte[] getBytes() {
		return Arrays.copyOf(data, (bitLength + 7) / 8);
	}
	
	
	// Appends the given number of bits of the given value to this sequence.
	// If 0 <= len <= 31, then this requires 0 <= val < 2^len.
	public void appendBits(int val, int len) {
		if (len < 0 || len > 32 || len < 32 && (val >>> len) != 0)
			throw new IllegalArgumentException("Value out of range");
		ensureCapacity(bitLength + len);
		for (int i = len - 1; i >= 0; i--, bitLength++)  // Append bit by bit
			data[bitLength >>> 3] |= ((val >>> i) & 1) << (7 - (bitLength & 7));
	}
	
	
	// Appends the data of the given segment to this bit buffer.
	public void appendData(QrSegment seg) {
		if (seg == null)
			throw new NullPointerException();
		ensureCapacity(bitLength + seg.bitLength);
		for (int i = 0; i < seg.bitLength; i++, bitLength++) {  // Append bit by bit
			int bit = (seg.getByte(i >>> 3) >>> (7 - (i & 7))) & 1;
			data[bitLength >>> 3] |= bit << (7 - (bitLength & 7));
		}
	}
	
	
	// Expands the buffer if necessary, so that it can hold at least the given bit length.
	private void ensureCapacity(int newBitLen) {
		while (data.length * 8 < newBitLen)
			data = Arrays.copyOf(data, data.length * 2);
	}
	
}
