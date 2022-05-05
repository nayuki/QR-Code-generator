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


// Stores the parts of a QR Code that depend only on the version number,
// and does not depend on the data or error correction level or mask.
final class QrTemplate {
	
	// Use this memoizer to get instances of this class.
	public static final Memoizer<Integer,QrTemplate> MEMOIZER
		= new Memoizer<>(QrTemplate::new);
	
	
	private final int version;  // In the range [1, 40].
	private final int size;  // Derived from version.
	
	final int[] template;  // Length and values depend on version.
	final int[][] masks;  // masks.length == 8, and masks[i].length == template.length.
	final int[] dataOutputBitIndexes;  // Length and values depend on version.
	
	// Indicates function modules that are not subjected to masking. Discarded when constructor finishes.
	// Otherwise when the constructor is running, isFunction.length == template.length.
	private int[] isFunction;
	
	
	// Creates a QR Code template for the given version number.
	private QrTemplate(int ver) {
		if (ver < QrCode.MIN_VERSION || ver > QrCode.MAX_VERSION)
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
	
	
	// Reads this object's version field, and draws and marks all function modules.
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
		int[] alignPatPos = getAlignmentPatternPositions();
		int numAlign = alignPatPos.length;
		for (int i = 0; i < numAlign; i++) {
			for (int j = 0; j < numAlign; j++) {
				// Don't draw on the three finder corners
				if (!(i == 0 && j == 0 || i == 0 && j == numAlign - 1 || i == numAlign - 1 && j == 0))
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
		for (int i = 0; i < 8; i++)
			darkenFunctionModule(size - 1 - i, 8, 0);
		for (int i = 8; i < 15; i++)
			darkenFunctionModule(8, size - 15 + i, 0);
		darkenFunctionModule(8, size - 8, 1);  // Always dark
	}
	
	
	// Draws two copies of the version bits (with its own error correction code),
	// based on this object's version field, iff 7 <= version <= 40.
	private void drawVersion() {
		if (version < 7)
			return;
		
		// Calculate error correction code and pack bits
		int rem = version;  // version is uint6, in the range [7, 40]
		for (int i = 0; i < 12; i++)
			rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
		int bits = version << 12 | rem;  // uint18
		assert bits >>> 18 == 0;
		
		// Draw two copies
		for (int i = 0; i < 18; i++) {
			int bit = QrCode.getBit(bits, i);
			int a = size - 11 + i % 3;
			int b = i / 3;
			darkenFunctionModule(a, b, bit);
			darkenFunctionModule(b, a, bit);
		}
	}
	
	
	// Draws a 9*9 finder pattern including the border separator,
	// with the center module at (x, y). Modules can be out of bounds.
	private void drawFinderPattern(int x, int y) {
		for (int dy = -4; dy <= 4; dy++) {
			for (int dx = -4; dx <= 4; dx++) {
				int dist = Math.max(Math.abs(dx), Math.abs(dy));  // Chebyshev/infinity norm
				int xx = x + dx, yy = y + dy;
				if (0 <= xx && xx < size && 0 <= yy && yy < size)
					darkenFunctionModule(xx, yy, (dist != 2 && dist != 4) ? 1 : 0);
			}
		}
	}
	
	
	// Draws a 5*5 alignment pattern, with the center module
	// at (x, y). All modules must be in bounds.
	private void drawAlignmentPattern(int x, int y) {
		for (int dy = -2; dy <= 2; dy++) {
			for (int dx = -2; dx <= 2; dx++)
				darkenFunctionModule(x + dx, y + dy, Math.abs(Math.max(Math.abs(dx), Math.abs(dy)) - 1));
		}
	}
	
	
	// Computes and returns a new array of masks, based on this object's various fields.
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
	
	
	// Computes and returns an array of bit indexes, based on this object's various fields.
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
		assert i == result.length;
		return result;
	}
	
	
	// Returns the value of the bit at the given coordinates in the given grid.
	private int getModule(int[] grid, int x, int y) {
		assert 0 <= x && x < size;
		assert 0 <= y && y < size;
		int i = y * size + x;
		return QrCode.getBit(grid[i >>> 5], i);
	}
	
	
	// Marks the module at the given coordinates as a function module.
	// Also either sets that module dark or keeps its color unchanged.
	private void darkenFunctionModule(int x, int y, int enable) {
		assert 0 <= x && x < size;
		assert 0 <= y && y < size;
		assert enable == 0 || enable == 1;
		int i = y * size + x;
		template[i >>> 5] |= enable << i;
		isFunction[i >>> 5] |= 1 << i;
	}
	
	
	// Returns an ascending list of positions of alignment patterns for this version number.
	// Each position is in the range [0,177), and are used on both the x and y axes.
	// This could be implemented as lookup table of 40 variable-length lists of unsigned bytes.
	private int[] getAlignmentPatternPositions() {
		if (version == 1)
			return new int[]{};
		else {
			int numAlign = version / 7 + 2;
			int step = (version == 32) ? 26 :
				(version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2;
			int[] result = new int[numAlign];
			result[0] = 6;
			for (int i = result.length - 1, pos = size - 7; i >= 1; i--, pos -= step)
				result[i] = pos;
			return result;
		}
	}
	
	
	// Returns the number of data bits that can be stored in a QR Code of the given version number, after
	// all function modules are excluded. This includes remainder bits, so it might not be a multiple of 8.
	// The result is in the range [208, 29648]. This could be implemented as a 40-entry lookup table.
	static int getNumRawDataModules(int ver) {
		if (ver < QrCode.MIN_VERSION || ver > QrCode.MAX_VERSION)
			throw new IllegalArgumentException("Version number out of range");
		int result = (16 * ver + 128) * ver + 64;
		if (ver >= 2) {
			int numAlign = ver / 7 + 2;
			result -= (25 * numAlign - 10) * numAlign - 55;
			if (ver >= 7)
				result -= 36;
		}
		return result;
	}
	
}
