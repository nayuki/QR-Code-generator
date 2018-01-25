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
 * An appendable sequence of bits (0's and 1's).
 */
public final class BitBuffer implements Cloneable {
	
	/*---- Fields ----*/
	
	private BitSet data;
	
	private int bitLength;
	
	
	
	/*---- Constructor ----*/
	
	/**
	 * Constructs an empty bit buffer (length 0).
	 */
	public BitBuffer() {
		data = new BitSet();
		bitLength = 0;
	}
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Returns the length of this sequence, which is a non-negative value.
	 * @return the length of this sequence
	 */
	public int bitLength() {
		return bitLength;
	}
	
	
	/**
	 * Returns the bit at the specified index, yielding 0 or 1.
	 * @param index the index to get the bit at
	 * @return the bit at the specified index
	 * @throws IndexOutOfBoundsException if index &lt; 0 or index &ge; bitLength
	 */
	public int getBit(int index) {
		if (index < 0 || index >= bitLength)
			throw new IndexOutOfBoundsException();
		return data.get(index) ? 1 : 0;
	}
	
	
	/**
	 * Packs this buffer's bits into bytes in big endian,
	 * padding with '0' bit values, and returns the new array.
	 * @return this sequence as a new array of bytes (not {@code null})
	 */
	public byte[] getBytes() {
		byte[] result = new byte[(bitLength + 7) / 8];
		for (int i = 0; i < bitLength; i++)
			result[i >>> 3] |= data.get(i) ? 1 << (7 - (i & 7)) : 0;
		return result;
	}
	
	
	/**
	 * Appends the specified number of low bits of the specified value
	 * to this sequence. Requires 0 &le; val &lt; 2<sup>len</sup>.
	 * @param val the value to append
	 * @param len the number of low bits in the value to take
	 */
	public void appendBits(int val, int len) {
		if (len < 0 || len > 31 || val >>> len != 0)
			throw new IllegalArgumentException("Value out of range");
		for (int i = len - 1; i >= 0; i--, bitLength++)  // Append bit by bit
			data.set(bitLength, ((val >>> i) & 1) != 0);
	}
	
	
	/**
	 * Appends the bit data of the specified segment to this bit buffer.
	 * @param seg the segment whose data to append (not {@code null})
	 * @throws NullPointerException if the segment is {@code null}
	 */
	public void appendData(QrSegment seg) {
		Objects.requireNonNull(seg);
		BitBuffer bb = seg.data;
		for (int i = 0; i < bb.bitLength; i++, bitLength++)  // Append bit by bit
			data.set(bitLength, bb.data.get(i));
	}
	
	
	/**
	 * Returns a copy of this bit buffer object.
	 * @return a copy of this bit buffer object
	 */
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
