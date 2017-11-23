/* 
 * Fast QR Code generator library
 * 
 * Copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/
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


final class BitBuffer {
	
	/*---- Fields ----*/
	
	int[] data;
	
	int bitLength;
	
	
	
	/*---- Constructors ----*/
	
	public BitBuffer() {
		data = new int[64];
		bitLength = 0;
	}
	
	
	
	/*---- Methods ----*/
	
	public int getBit(int index) {
		if (index < 0 || index >= bitLength)
			throw new IndexOutOfBoundsException();
		return (data[index >>> 5] >>> ~index) & 1;
	}
	
	
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
	
}
