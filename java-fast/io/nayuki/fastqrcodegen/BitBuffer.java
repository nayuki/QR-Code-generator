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


// An appendable sequence of bits (0s and 1s), mainly used by QrSegment.
final class BitBuffer {
	
	/*---- Fields ----*/
	
	int[] data;  // In each 32-bit word, bits are filled from top down.
	
	int bitLength;  // Always non-negative.
	
	
	
	/*---- Constructor ----*/
	
	// Creates an empty bit buffer.
	public BitBuffer() {
		data = new int[64];
		bitLength = 0;
	}
	
	
	
	/*---- Methods ----*/
	
	// Returns the bit at the given index, yielding 0 or 1.
	public int getBit(int index) {
		if (index < 0 || index >= bitLength)
			throw new IndexOutOfBoundsException();
		return (data[index >>> 5] >>> ~index) & 1;
	}
	
	
	// Returns a new array representing this buffer's bits packed into
	// bytes in big endian. The current bit length must be a multiple of 8.
	public byte[] getBytes() {
		if (bitLength % 8 != 0)
			throw new IllegalStateException("Data is not a whole number of bytes");
		byte[] result = new byte[bitLength / 8];
		for (int i = 0; i < result.length; i++)
			result[i] = (byte)(data[i >>> 2] >>> (~i << 3));
		return result;
	}
	
	
	// Appends the given number of low-order bits of the given value
	// to this buffer. Requires 0 <= len <= 31 and 0 <= val < 2^len.
	public void appendBits(int val, int len) {
		if (len < 0 || len > 31 || val >>> len != 0)
			throw new IllegalArgumentException("Value out of range");
		if (len > Integer.MAX_VALUE - bitLength)
			throw new IllegalStateException("Maximum length reached");
		
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
	
	
	// Appends to this buffer the sequence of bits represented by the given
	// word array and given bit length. Requires 0 <= len <= 32 * vals.length.
	public void appendBits(int[] vals, int len) {
		Objects.requireNonNull(vals);
		if (len == 0)
			return;
		if (len < 0 || len > vals.length * 32L)
			throw new IllegalArgumentException("Value out of range");
		int wholeWords = len / 32;
		int tailBits = len % 32;
		if (tailBits > 0 && vals[wholeWords] << tailBits != 0)
			throw new IllegalArgumentException("Last word must have low bits clear");
		if (len > Integer.MAX_VALUE - bitLength)
			throw new IllegalStateException("Maximum length reached");
		
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
