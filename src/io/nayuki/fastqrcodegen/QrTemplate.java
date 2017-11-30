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

import static io.nayuki.fastqrcodegen.QrCode.MAX_VERSION;
import static io.nayuki.fastqrcodegen.QrCode.MIN_VERSION;
import java.lang.ref.SoftReference;


final class QrTemplate {
	
	/*---- Factory members ----*/
	
	public static QrTemplate getInstance(int version) {
		if (version < MIN_VERSION || version > MAX_VERSION)
			throw new IllegalArgumentException("Version out of range");
		
		while (true) {
			synchronized(cache) {
				SoftReference<QrTemplate> ref = cache[version];
				if (ref != null) {
					QrTemplate result = ref.get();
					if (result != null)
						return result;
					cache[version] = null;
				}
				
				if (!isPending[version]) {
					isPending[version] = true;
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
			QrTemplate tpl = new QrTemplate(version);
			synchronized(cache) {
				cache[version] = new SoftReference<>(tpl);
			}
			return tpl;
		} finally {
			synchronized(cache) {
				isPending[version] = false;
				cache.notifyAll();
			}
		}
	}
	
	
	@SuppressWarnings("unchecked")
	private static final SoftReference<QrTemplate>[] cache = new SoftReference[MAX_VERSION + 1];
	
	private static final boolean[] isPending = new boolean[MAX_VERSION + 1];
	
	
	
	/*---- Instance members ----*/
	
	private final int version;
	private final int size;
	
	final int[] template;
	final int[][] masks;
	final int[] dataOutputBitIndexes;
	
	private int[] isFunction;  // Discarded at end of constructor
	
	
	private QrTemplate(int ver) {
		if (ver < MIN_VERSION || ver > MAX_VERSION)
			throw new IllegalArgumentException("Version out of range");
		version = ver;
		size = version * 4 + 17;
		template = new int[(size * size + 31) / 32];
		isFunction = new int[template.length];
		
		drawFunctionPatterns();  // Reads and writes fields
		masks = generateMasks();  // Reads fields, returns array
		dataOutputBitIndexes = generateZigzagScan();  // Reads fields, returns array
		isFunction = null;
	}
	
	
	private void drawFunctionPatterns() {
		// Draw horizontal and vertical timing patterns
		for (int i = 0; i < size; i++) {
			darkenFunctionModule(6, i, ~i & 1);
			darkenFunctionModule(i, 6, ~i & 1);
		}
		
		// Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
		drawFinderPattern(3, 3);
		drawFinderPattern(size - 4, 3);
		drawFinderPattern(3, size - 4);
		
		// Draw numerous alignment patterns
		int[] alignPatPos = getAlignmentPatternPositions(version);
		int numAlign = alignPatPos.length;
		for (int i = 0; i < numAlign; i++) {
			for (int j = 0; j < numAlign; j++) {
				if (i == 0 && j == 0 || i == 0 && j == numAlign - 1 || i == numAlign - 1 && j == 0)
					continue;  // Skip the three finder corners
				else
					drawAlignmentPattern(alignPatPos[i], alignPatPos[j]);
			}
		}
		
		// Draw configuration data
		drawDummyFormatBits();
		drawVersion();
	}
	
	
	// Draws two blank copies of the format bits.
	private void drawDummyFormatBits() {
		// Draw first copy
		for (int i = 0; i <= 5; i++)
			darkenFunctionModule(8, i, 0);
		darkenFunctionModule(8, 7, 0);
		darkenFunctionModule(8, 8, 0);
		darkenFunctionModule(7, 8, 0);
		for (int i = 9; i < 15; i++)
			darkenFunctionModule(14 - i, 8, 0);
		
		// Draw second copy
		for (int i = 0; i <= 7; i++)
			darkenFunctionModule(size - 1 - i, 8, 0);
		for (int i = 8; i < 15; i++)
			darkenFunctionModule(8, size - 15 + i, 0);
		darkenFunctionModule(8, size - 8, 1);
	}
	
	
	// Draws two copies of the version bits (with its own error correction code),
	// based on this object's version field (which only has an effect for 7 <= version <= 40).
	private void drawVersion() {
		if (version < 7)
			return;
		
		// Calculate error correction code and pack bits
		int rem = version;  // version is uint6, in the range [7, 40]
		for (int i = 0; i < 12; i++)
			rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
		int data = version << 12 | rem;  // uint18
		if (data >>> 18 != 0)
			throw new AssertionError();
		
		// Draw two copies
		for (int i = 0; i < 18; i++) {
			int bit = (data >>> i) & 1;
			int a = size - 11 + i % 3, b = i / 3;
			darkenFunctionModule(a, b, bit);
			darkenFunctionModule(b, a, bit);
		}
	}
	
	
	// Draws a 9*9 finder pattern including the border separator, with the center module at (x, y).
	private void drawFinderPattern(int x, int y) {
		for (int i = -4; i <= 4; i++) {
			for (int j = -4; j <= 4; j++) {
				int dist = Math.max(Math.abs(i), Math.abs(j));  // Chebyshev/infinity norm
				int xx = x + j, yy = y + i;
				if (0 <= xx && xx < size && 0 <= yy && yy < size)
					darkenFunctionModule(xx, yy, (dist != 2 && dist != 4) ? 1 : 0);
			}
		}
	}
	
	
	// Draws a 5*5 alignment pattern, with the center module at (x, y).
	private void drawAlignmentPattern(int x, int y) {
		for (int i = -2; i <= 2; i++) {
			for (int j = -2; j <= 2; j++)
				darkenFunctionModule(x + j, y + i, (Math.max(Math.abs(i), Math.abs(j)) != 1) ? 1 : 0);
		}
	}
	
	
	private int[][] generateMasks() {
		int[][] result = new int[8][template.length];
		for (int mask = 0; mask < result.length; mask++) {
			int[] maskModules = result[mask];
			for (int y = 0, i = 0; y < size; y++) {
				for (int x = 0; x < size; x++, i++) {
					boolean invert;
					switch (mask) {
						case 0:  invert = (x + y) % 2 == 0;                    break;
						case 1:  invert = y % 2 == 0;                          break;
						case 2:  invert = x % 3 == 0;                          break;
						case 3:  invert = (x + y) % 3 == 0;                    break;
						case 4:  invert = (x / 3 + y / 2) % 2 == 0;            break;
						case 5:  invert = x * y % 2 + x * y % 3 == 0;          break;
						case 6:  invert = (x * y % 2 + x * y % 3) % 2 == 0;    break;
						case 7:  invert = ((x + y) % 2 + x * y % 3) % 2 == 0;  break;
						default:  throw new AssertionError();
					}
					int bit = (invert ? 1 : 0) & ~getModule(isFunction, x, y);
					maskModules[i >>> 5] |= bit << i;
				}
			}
		}
		return result;
	}
	
	
	private int[] generateZigzagScan() {
		int[] result = new int[getNumRawDataModules(version) / 8 * 8];
		int i = 0;  // Bit index into the data
		for (int right = size - 1; right >= 1; right -= 2) {  // Index of right column in each column pair
			if (right == 6)
				right = 5;
			for (int vert = 0; vert < size; vert++) {  // Vertical counter
				for (int j = 0; j < 2; j++) {
					int x = right - j;  // Actual x coordinate
					boolean upward = ((right + 1) & 2) == 0;
					int y = upward ? size - 1 - vert : vert;  // Actual y coordinate
					if (getModule(isFunction, x, y) == 0 && i < result.length) {
						result[i] = y * size + x;
						i++;
					}
				}
			}
		}
		if (i != result.length)
			throw new AssertionError();
		return result;
	}
	
	
	private int getModule(int[] grid, int x, int y) {
		assert 0 <= x && x < size;
		assert 0 <= y && y < size;
		int i = y * size + x;
		return (grid[i >>> 5] >>> i) & 1;
	}
	
	
	private void darkenFunctionModule(int x, int y, int enable) {
		assert 0 <= x && x < size;
		assert 0 <= y && y < size;
		assert enable == 0 || enable == 1;
		int i = y * size + x;
		template[i >>> 5] |= enable << i;
		isFunction[i >>> 5] |= 1 << i;
	}
	
	
	/*---- Private static helper functions ----*/
	
	// Returns a set of positions of the alignment patterns in ascending order. These positions are
	// used on both the x and y axes. Each value in the resulting array is in the range [0, 177).
	// This stateless pure function could be implemented as table of 40 variable-length lists of unsigned bytes.
	private static int[] getAlignmentPatternPositions(int ver) {
		if (ver < MIN_VERSION || ver > MAX_VERSION)
			throw new IllegalArgumentException("Version number out of range");
		else if (ver == 1)
			return new int[]{};
		else {
			int numAlign = ver / 7 + 2;
			int step;
			if (ver != 32) {
				// ceil((size - 13) / (2*numAlign - 2)) * 2
				step = (ver * 4 + numAlign * 2 + 1) / (2 * numAlign - 2) * 2;
			} else  // C-C-C-Combo breaker!
				step = 26;
			
			int[] result = new int[numAlign];
			result[0] = 6;
			for (int i = result.length - 1, pos = ver * 4 + 10; i >= 1; i--, pos -= step)
				result[i] = pos;
			return result;
		}
	}
	
	
	// Returns the number of data bits that can be stored in a QR Code of the given version number, after
	// all function modules are excluded. This includes remainder bits, so it might not be a multiple of 8.
	// The result is in the range [208, 29648]. This could be implemented as a 40-entry lookup table.
	static int getNumRawDataModules(int ver) {
		if (ver < MIN_VERSION || ver > MAX_VERSION)
			throw new IllegalArgumentException("Version number out of range");
		int result = (16 * ver + 128) * ver + 64;
		if (ver >= 2) {
			int numAlign = ver / 7 + 2;
			result -= (25 * numAlign - 10) * numAlign - 55;
			if (ver >= 7)
				result -= 18 * 2;  // Subtract version information
		}
		return result;
	}
	
}
