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

import java.util.BitSet;
import java.util.Objects;


/**
 * An appendable sequence of bits. Bits are packed in big endian within a byte.
 */
public final class BitBuffer implements Cloneable {
	
	/*---- Fields ----*/
	
	private BitSet data;
	private int bitLength;
	
	
	
	/*---- Constructor ----*/
	
	// Creates an empty bit buffer (length 0).
	public BitBuffer() {
		data = new BitSet();
		bitLength = 0;
	}
	
	
	
	/*---- Methods ----*/
	
	// Returns the number of bits in the buffer, which is a non-negative value.
	public int bitLength() {
		return bitLength;
	}
	
	
	// Returns the bit at the given index, yielding 0 or 1, or throwing IndexOutOfBoundsException.
	public int getBit(int index) {
		if (index < 0 || index >= bitLength)
			throw new IndexOutOfBoundsException();
		return data.get(index) ? 1 : 0;
	}
	
	
	// Returns a copy of all bytes, padding up to the nearest byte. Bits are packed in big endian within a byte.
	public byte[] getBytes() {
		byte[] result = new byte[(bitLength + 7) / 8];
		for (int i = 0; i < bitLength; i++)
			result[i >>> 3] |= data.get(i) ? 1 << (7 - (i & 7)) : 0;
		return result;
	}
	
	
	// Appends the given number of bits of the given value to this sequence.
	// If 0 <= len <= 31, then this requires 0 <= val < 2^len.
	public void appendBits(int val, int len) {
		if (len < 0 || len > 31 || val >>> len != 0)
			throw new IllegalArgumentException("Value out of range");
		for (int i = len - 1; i >= 0; i--, bitLength++)  // Append bit by bit
			data.set(bitLength, ((val >>> i) & 1) != 0);
	}
	
	
	// Appends the data of the given segment to this bit buffer.
	public void appendData(QrSegment seg) {
		Objects.requireNonNull(seg);
		BitBuffer bb = seg.data;
		for (int i = 0; i < bb.bitLength; i++, bitLength++)  // Append bit by bit
			data.set(bitLength, bb.data.get(i));
	}
	
	
	// Returns a copy of this bit buffer object.
	public BitBuffer clone() {
		try {
			BitBuffer result = (BitBuffer)super.clone();
			result.data = (BitSet)result.data.clone();
			return result;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError(e);
		}
	}
	
}
