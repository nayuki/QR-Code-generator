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

import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.Objects;


final class ReedSolomonGenerator {
	
	/*---- Factory members ----*/
	
	public static ReedSolomonGenerator getInstance(int degree) {
		if (degree < 1 || degree > MAX_DEGREE)
			throw new IllegalArgumentException("Degree out of range");
		
		while (true) {
			synchronized(cache) {
				SoftReference<ReedSolomonGenerator> ref = cache[degree];
				if (ref != null) {
					ReedSolomonGenerator result = ref.get();
					if (result != null)
						return result;
					cache[degree] = null;
				}
				
				if (!isPending[degree]) {
					isPending[degree] = true;
					break;
				}
				
				try {
					cache.wait();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}
		
		try {
			ReedSolomonGenerator rs = new ReedSolomonGenerator(degree);
			synchronized(cache) {
				cache[degree] = new SoftReference<>(rs);
			}
			return rs;
		} finally {
			synchronized(cache) {
				isPending[degree] = false;
				cache.notifyAll();
			}
		}
	}
	
	
	private static final int MAX_DEGREE = 30;
	
	@SuppressWarnings("unchecked")
	private static final SoftReference<ReedSolomonGenerator>[] cache = new SoftReference[MAX_DEGREE + 1];
	
	private static final boolean[] isPending = new boolean[MAX_DEGREE + 1];
	
	
	
	/*---- Instance members ----*/
	
	private byte[][] multiplies;
	
	
	private ReedSolomonGenerator(int degree) {
		if (degree < 1 || degree > 255)
			throw new IllegalArgumentException("Degree out of range");
		
		// Start with the monomial x^0
		byte[] coefficients = new byte[degree];
		coefficients[degree - 1] = 1;
		
		// Compute the product polynomial (x - r^0) * (x - r^1) * (x - r^2) * ... * (x - r^{degree-1}),
		// drop the highest term, and store the rest of the coefficients in order of descending powers.
		// Note that r = 0x02, which is a generator element of this field GF(2^8/0x11D).
		int root = 1;
		for (int i = 0; i < degree; i++) {
			// Multiply the current product by (x - r^i)
			for (int j = 0; j < coefficients.length; j++) {
				coefficients[j] = (byte)multiply(coefficients[j] & 0xFF, root);
				if (j + 1 < coefficients.length)
					coefficients[j] ^= coefficients[j + 1];
			}
			root = multiply(root, 0x02);
		}
		
		multiplies = new byte[degree][];
		for (int i = 0; i < multiplies.length; i++)
			multiplies[i] = MULTIPLICATION_TABLE[coefficients[i] & 0xFF];
	}
	
	
	public void getRemainder(byte[] data, int dataOff, int dataLen, byte[] result, int resultOff) {
		Objects.requireNonNull(data);
		Objects.requireNonNull(result);
		
		// Compute the remainder by performing polynomial division
		int resultEnd = resultOff + multiplies.length;
		Arrays.fill(result, resultOff, resultEnd, (byte)0);
		for (int i = dataOff, dataEnd = dataOff + dataLen; i < dataEnd; i++) {
			byte b = data[i];
			int factor = (b ^ result[resultOff]) & 0xFF;
			System.arraycopy(result, resultOff + 1, result, resultOff, multiplies.length - 1);
			result[resultEnd - 1] = 0;
			for (int j = 0; j < multiplies.length; j++)
				result[resultOff + j] ^= multiplies[j][factor];
		}
	}
	
	
	
	/*---- Constant members ----*/
	
	// Returns the product of the two given field elements modulo GF(2^8/0x11D). The arguments and result
	// are unsigned 8-bit integers. This could be implemented as a lookup table of 256*256 entries of uint8.
	private static int multiply(int x, int y) {
		if (x >>> 8 != 0 || y >>> 8 != 0)
			throw new IllegalArgumentException("Byte out of range");
		// Russian peasant multiplication
		int z = 0;
		for (int i = 7; i >= 0; i--) {
			z = (z << 1) ^ ((z >>> 7) * 0x11D);
			z ^= ((y >>> i) & 1) * x;
		}
		if (z >>> 8 != 0)
			throw new AssertionError();
		return z;
	}
	
	
	private static final byte[][] MULTIPLICATION_TABLE = new byte[256][256];
	
	static {
		for (int i = 0; i < MULTIPLICATION_TABLE.length; i++) {
			for (int j = 0; j <= i; j++) {
				byte k = (byte)multiply(i, j);
				MULTIPLICATION_TABLE[i][j] = k;
				MULTIPLICATION_TABLE[j][i] = k;
			}
		}
	}
	
}
