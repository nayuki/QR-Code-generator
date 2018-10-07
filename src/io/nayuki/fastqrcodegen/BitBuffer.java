/* 
 * Fast QR Code generator library
 * 
 * Copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/fast-qr-code-generator-library
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

package io.nayuki.fastqrcodegen;

import java.util.Arrays;
import java.util.Objects;


/**
 * An appendable sequence of bits (0s and 1s). Mainly used by {@link QrSegment}.
 */
final class BitBuffer {
	
	/*---- Fields ----*/
	
	int[] data;
	
	int bitLength;
	
	
	
	/*---- Constructor ----*/
	
	/**
	 * Constructs an empty bit buffer (length 0).
	 */
	public BitBuffer() {
		data = new int[64];
		bitLength = 0;
	}
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Returns the length of this sequence, which is a non-negative value.
	 * @return the length of this sequence
	 */
	public int getBit(int index) {
		if (index < 0 || index >= bitLength)
			throw new IndexOutOfBoundsException();
		return (data[index >>> 5] >>> ~index) & 1;
	}
	
	
	public byte[] getBytes() {
		if (bitLength % 8 != 0)
			throw new IllegalStateException("Data is not a whole number of bytes");
		byte[] result = new byte[bitLength / 8];
		for (int i = 0; i < result.length; i++)
			result[i] = (byte)(data[i >>> 2] >>> (~i << 3));
		return result;
	}
	
	
	/**
	 * Appends the specified number of low-order bits of the specified value to this
	 * buffer. Requires 0 &#x2264; len &#x2264; 31 and 0 &#x2264; val &lt; 2<sup>len</sup>.
	 * @param val the value to append
	 * @param len the number of low-order bits in the value to take
	 * @throws IllegalArgumentException if the value or number of bits is out of range
	 * @throws IllegalStateException if appending the data
	 * would make bitLength exceed Integer.MAX_VALUE
	 */
	public void appendBits(int val, int len) {
		if (len < 0 || len > 31 || val >>> len != 0)
			throw new IllegalArgumentException("Value out of range");
		
		if (bitLength + len + 1 > data.length << 5)
			data = Arrays.copyOf(data, data.length * 2);
		assert bitLength + len <= data.length << 5;
		
		int remain = 32 - (bitLength & 0x1F);
		assert 1 <= remain && remain <= 32;
		if (remain < len) {
			data[bitLength >>> 5] |= val >>> (len - remain);
			bitLength += remain;
			assert (bitLength & 0x1F) == 0;
			len -= remain;
			val &= (1 << len) - 1;
			remain = 32;
		}
		data[bitLength >>> 5] |= val << (remain - len);
		bitLength += len;
	}
	
	
	public void appendBits(int[] vals, int len) {
		Objects.requireNonNull(vals);
		if (len == 0)
			return;
		if (len < 0 || len > vals.length * 32)
			throw new IllegalArgumentException("Value out of range");
		int wholeWords = len / 32;
		int tailBits = len % 32;
		if (tailBits > 0 && vals[wholeWords] << tailBits != 0)
			throw new IllegalArgumentException("Last word must have low bits clear");
		
		while (bitLength + len > data.length * 32)
			data = Arrays.copyOf(data, data.length * 2);
		
		int shift = bitLength % 32;
		if (shift == 0) {
			System.arraycopy(vals, 0, data, bitLength / 32, (len + 31) / 32);
			bitLength += len;
		} else {
			for (int i = 0; i < wholeWords; i++) {
				int word = vals[i];
				data[bitLength >>> 5] |= word >>> shift;
				bitLength += 32;
				data[bitLength >>> 5] = word << (32 - shift);
			}
			if (tailBits > 0)
				appendBits(vals[wholeWords] >>> (32 - tailBits), tailBits);
		}
	}
	
}
