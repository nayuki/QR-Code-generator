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

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


public final class QrCode {
	
	/*---- Public static factory functions ----*/
	
	public static QrCode encodeText(String text, Ecc ecl) {
		Objects.requireNonNull(text);
		Objects.requireNonNull(ecl);
		List<QrSegment> segs = QrSegment.makeSegments(text);
		return encodeSegments(segs, ecl);
	}
	
	
	public static QrCode encodeBinary(byte[] data, Ecc ecl) {
		Objects.requireNonNull(data);
		Objects.requireNonNull(ecl);
		QrSegment seg = QrSegment.makeBytes(data);
		return encodeSegments(Arrays.asList(seg), ecl);
	}
	
	
	public static QrCode encodeSegments(List<QrSegment> segs, Ecc ecl) {
		return encodeSegments(segs, ecl, MIN_VERSION, MAX_VERSION, -1, true);
	}
	
	
	public static QrCode encodeSegments(List<QrSegment> segs, Ecc ecl, int minVersion, int maxVersion, int mask, boolean boostEcl) {
		Objects.requireNonNull(segs);
		Objects.requireNonNull(ecl);
		if (!(MIN_VERSION <= minVersion && minVersion <= maxVersion && maxVersion <= MAX_VERSION) || mask < -1 || mask > 7)
			throw new IllegalArgumentException("Invalid value");
		
		// Find the minimal version number to use
		int version, dataUsedBits;
		for (version = minVersion; ; version++) {
			int dataCapacityBits = getNumDataCodewords(version, ecl) * 8;  // Number of data bits available
			dataUsedBits = QrSegment.getTotalBits(segs, version);
			if (dataUsedBits != -1 && dataUsedBits <= dataCapacityBits)
				break;  // This version number is found to be suitable
			if (version >= maxVersion)  // All versions in the range could not fit the given data
				throw new IllegalArgumentException("Data too long");
		}
		assert dataUsedBits != -1;
		
		// Increase the error correction level while the data still fits in the current version number
		for (Ecc newEcl : Ecc.values()) {  // From low to high
			if (boostEcl && dataUsedBits <= getNumDataCodewords(version, newEcl) * 8)
				ecl = newEcl;
		}
		
		// Concatenate all segments to create the data bit string
		int dataCapacityBits = getNumDataCodewords(version, ecl) * 8;
		BitBuffer bb = new BitBuffer();
		for (QrSegment seg : segs) {
			bb.appendBits(seg.mode.modeBits, 4);
			bb.appendBits(seg.numChars, seg.mode.numCharCountBits(version));
			bb.appendBits(seg.data, seg.bitLength);
		}
		
		// Add terminator and pad up to a byte if applicable
		bb.appendBits(0, Math.min(4, dataCapacityBits - bb.bitLength));
		bb.appendBits(0, (8 - bb.bitLength % 8) % 8);
		
		// Pad with alternating bytes until data capacity is reached
		for (int padByte = 0xEC; bb.bitLength < dataCapacityBits; padByte ^= 0xEC ^ 0x11)
			bb.appendBits(padByte, 8);
		assert bb.bitLength % 8 == 0;
		
		// Create the QR Code symbol
		return new QrCode(version, ecl, bb.getBytes(), mask);
	}
	
	
	
	/*---- Public constants ----*/
	
	public static final int MIN_VERSION =  1;
	public static final int MAX_VERSION = 40;
	
	
	
	/*---- Instance fields ----*/
	
	public final int version;
	
	public final int size;
	
	public final Ecc errorCorrectionLevel;
	
	public final int mask;
	
	private final int[] modules;
	
	
	
	/*---- Constructor ----*/
	
	public QrCode(int ver, Ecc ecl, byte[] dataCodewords, int mask) {
		// Check arguments
		errorCorrectionLevel = Objects.requireNonNull(ecl);
		if (ver < MIN_VERSION || ver > MAX_VERSION || mask < -1 || mask > 7)
			throw new IllegalArgumentException("Value out of range");
		Objects.requireNonNull(dataCodewords);
		
		// Initialize fields
		version = ver;
		size = ver * 4 + 17;
		
		QrTemplate tpl = QrTemplate.getInstance(ver);
		modules = tpl.template.clone();  
		
		// Draw function patterns, draw all codewords, do masking
		byte[] allCodewords = addEccAndInterleave(dataCodewords);
		drawCodewords(tpl.dataOutputBitIndexes, allCodewords);
		this.mask = handleConstructorMasking(tpl.masks, mask);
	}
	
	
	
	/*---- Public instance methods ----*/
	
	/**
	 * Returns the color of the module (pixel) at the specified coordinates, which is either
	 * false for white or true for black. The top left corner has the coordinates (x=0, y=0).
	 * If the specified coordinates are out of bounds, then false (white) is returned.
	 * @param x the x coordinate, where 0 is the left edge and size&minus;1 is the right edge
	 * @param y the y coordinate, where 0 is the top edge and size&minus;1 is the bottom edge
	 * @return the module's color, which is either false (white) or true (black)
	 */
	public boolean getModule(int x, int y) {
		if (0 <= x && x < size && 0 <= y && y < size) {
			int i = y * size + x;
			return ((modules[i >>> 5] >>> i) & 1) != 0;
		} else
			return false;
	}
	
	
	/**
	 * Returns a new image object representing this QR Code, with the specified module scale and number
	 * of border modules. For example, the arguments scale=10, border=4 means to pad the QR Code symbol
	 * with 4 white border modules on all four edges, then use 10*10 pixels to represent each module.
	 * The resulting image only contains the hex colors 000000 and FFFFFF.
	 * @param scale the module scale factor, which must be positive
	 * @param border the number of border modules to add, which must be non-negative
	 * @return an image representing this QR Code, with padding and scaling
	 * @throws IllegalArgumentException if the scale or border is out of range, or if
	 * {scale, border, size} cause the image dimensions to exceed Integer.MAX_VALUE
	 */
	public BufferedImage toImage(int scale, int border) {
		if (scale <= 0 || border < 0)
			throw new IllegalArgumentException("Value out of range");
		if (border > Integer.MAX_VALUE / 2 || size + border * 2L > Integer.MAX_VALUE / scale)
			throw new IllegalArgumentException("Scale or border too large");
		
		BufferedImage result = new BufferedImage((size + border * 2) * scale, (size + border * 2) * scale, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < result.getHeight(); y++) {
			for (int x = 0; x < result.getWidth(); x++) {
				boolean color = getModule(x / scale - border, y / scale - border);
				result.setRGB(x, y, color ? 0x000000 : 0xFFFFFF);
			}
		}
		return result;
	}
	
	
	/**
	 * Based on the specified number of border modules to add as padding, this returns a
	 * string whose contents represents an SVG XML file that depicts this QR Code symbol.
	 * Note that Unix newlines (\n) are always used, regardless of the platform.
	 * @param border the number of border modules to add, which must be non-negative
	 * @return a string representing this QR Code as an SVG document
	 * @throws IllegalArgumentException if the border is negative
	 */
	public String toSvgString(int border) {
		if (border < 0)
			throw new IllegalArgumentException("Border must be non-negative");
		long brd = border;
		StringBuilder sb = new StringBuilder()
			.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n")
			.append(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 %1$d %1$d\" stroke=\"none\">\n",
				size + brd * 2))
			.append("\t<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n")
			.append("\t<path d=\"");
		boolean head = true;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				if (getModule(x, y)) {
					if (head)
						head = false;
					else
						sb.append(" ");
					sb.append(String.format("M%d,%dh1v1h-1z", x + brd, y + brd));
				}
			}
		}
		return sb
			.append("\" fill=\"#000000\"/>\n")
			.append("</svg>\n")
			.toString();
	}
	
	
	
	/*---- Private helper methods for constructor: Drawing function modules ----*/
	
	// Draws two copies of the format bits (with its own error correction code)
	// based on the given mask and this object's error correction level field.
	private void drawFormatBits(int mask) {
		// Calculate error correction code and pack bits
		int data = errorCorrectionLevel.formatBits << 3 | mask;  // errCorrLvl is uint2, mask is uint3
		int rem = data;
		for (int i = 0; i < 10; i++)
			rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
		int bits  = (data << 10 | rem) ^ 0x5412;  // uint15
		assert bits >>> 15 == 0;
		
		// Draw first copy
		for (int i = 0; i <= 5; i++)
			setModule(8, i, (bits >>> i) & 1);
		setModule(8, 7, (bits >>> 6) & 1);
		setModule(8, 8, (bits >>> 7) & 1);
		setModule(7, 8, (bits >>> 8) & 1);
		for (int i = 9; i < 15; i++)
			setModule(14 - i, 8, (bits >>> i) & 1);
		
		// Draw second copy
		for (int i = 0; i <= 7; i++)
			setModule(size - 1 - i, 8, (bits >>> i) & 1);
		for (int i = 8; i < 15; i++)
			setModule(8, size - 15 + i, (bits >>> i) & 1);
		setModule(8, size - 8, 1);
	}
	
	
	private void setModule(int x, int y, int black) {
		assert 0 <= x && x < size;
		assert 0 <= y && y < size;
		int i = y * size + x;
		if (black == 0)
			modules[i >>> 5] &= ~(1 << i);
		else if (black == 1)
			modules[i >>> 5] |= 1 << i;
		else
			throw new IllegalArgumentException();
	}
	
	
	/*---- Private helper methods for constructor: Codewords and masking ----*/
	
	// Returns a new byte string representing the given data with the appropriate error correction
	// codewords appended to it, based on this object's version and error correction level.
	private byte[] addEccAndInterleave(byte[] data) {
		if (data.length != getNumDataCodewords(version, errorCorrectionLevel))
			throw new IllegalArgumentException();
		
		// Calculate parameter numbers
		int numBlocks = NUM_ERROR_CORRECTION_BLOCKS[errorCorrectionLevel.ordinal()][version];
		int blockEccLen = ECC_CODEWORDS_PER_BLOCK[errorCorrectionLevel.ordinal()][version];
		int rawCodewords = QrTemplate.getNumRawDataModules(version) / 8;
		int numShortBlocks = numBlocks - rawCodewords % numBlocks;
		int shortBlockDataLen = rawCodewords / numBlocks - blockEccLen;
		
		// Split data into blocks, calculate ECC, and interleave
		// (not concatenate) the bytes into a single sequence
		byte[] result = new byte[rawCodewords];
		ReedSolomonGenerator rs = ReedSolomonGenerator.getInstance(blockEccLen);
		byte[] ecc = new byte[blockEccLen];  // Temporary storage per iteration
		for (int i = 0, k = 0; i < numBlocks; i++) {
			int datLen = shortBlockDataLen + (i < numShortBlocks ? 0 : 1);
			rs.getRemainder(data, k, datLen, ecc);
			for (int j = 0, l = i; j < datLen; j++, k++, l += numBlocks) {  // Copy data
				if (j == shortBlockDataLen)
					l -= numShortBlocks;
				result[l] = data[k];
			}
			for (int j = 0, l = data.length + i; j < blockEccLen; j++, l += numBlocks)  // Copy ECC
				result[l] = ecc[j];
		}
		return result;
	}
	
	
	// Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
	// data area of this QR Code symbol. Function modules need to be marked off before this is called.
	private void drawCodewords(int[] dataOutputBitIndexes, byte[] allCodewords) {
		Objects.requireNonNull(dataOutputBitIndexes);
		Objects.requireNonNull(allCodewords);
		if (allCodewords.length * 8 != dataOutputBitIndexes.length)
			throw new IllegalArgumentException();
		for (int i = 0; i < dataOutputBitIndexes.length; i++) {
			int j = dataOutputBitIndexes[i];
			int bit = (allCodewords[i >>> 3] >>> (~i & 7)) & 1;
			modules[j >>> 5] |= bit << j;
		}
	}
	
	
	// XORs the codeword modules in this QR Code with the given mask pattern.
	// The function modules must be marked and the codeword bits must be drawn
	// before masking. Due to the arithmetic of XOR, calling applyMask() with
	// the same mask value a second time will undo the mask. A final well-formed
	// QR Code symbol needs exactly one (not zero, two, etc.) mask applied.
	private void applyMask(int[] mask) {
		if (mask.length != modules.length)
			throw new IllegalArgumentException();
		for (int i = 0; i < mask.length; i++)
			modules[i] ^= mask[i];
	}
	
	
	// A messy helper function for the constructors. This QR Code must be in an unmasked state when this
	// method is called. The given argument is the requested mask, which is -1 for auto or 0 to 7 for fixed.
	// This method applies and returns the actual mask chosen, from 0 to 7.
	private int handleConstructorMasking(int[][] masks, int mask) {
		if (mask == -1) {  // Automatically choose best mask
			int minPenalty = Integer.MAX_VALUE;
			for (int i = 0; i < 8; i++) {
				drawFormatBits(i);
				applyMask(masks[i]);
				int penalty = getPenaltyScore();
				if (penalty < minPenalty) {
					mask = i;
					minPenalty = penalty;
				}
				applyMask(masks[i]);  // Undoes the mask due to XOR
			}
		}
		assert 0 <= mask && mask <= 7;
		drawFormatBits(mask);  // Overwrite old format bits
		applyMask(masks[mask]);  // Apply the final choice of mask
		return mask;  // The caller shall assign this value to the final-declared field
	}
	
	
	// Calculates and returns the penalty score based on state of this QR Code's current modules.
	// This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
	private int getPenaltyScore() {
		int result = 0;
		int black = 0;
		
		// Iterate over adjacent pairs of rows
		for (int index = 0, downIndex = size, end = size * size; index < end; ) {
			int bits = 0;
			int downBits = 0;
			int runColor = 0;
			int runLen = 0;
			for (int x = 0; x < size; x++, index++, downIndex++) {
				
				// Adjacent modules having same color
				int bit = (modules[index >>> 5] >>> index) & 1;
				if (bit != runColor) {
					runColor = bit;
					runLen = 1;
				} else {
					runLen++;
					if (runLen == 5)
						result += PENALTY_N1;
					else if (runLen > 5)
						result++;
				}
				
				black += bit;
				bits = ((bits & 0b1111111111) << 1) | bit;
				if (downIndex < end) {
					downBits = ((downBits & 1) << 1) | ((modules[downIndex >>> 5] >>> downIndex) & 1);
					// 2*2 blocks of modules having same color
					if (x >= 1 && (downBits == 0 || downBits == 3) && downBits == (bits & 3))
						result += PENALTY_N2;
				}
				
				// Finder-like pattern
				if (x >= 10 && (bits == 0b00001011101 || bits == 0b10111010000))
					result += PENALTY_N3;
			}
		}
		
		// Iterate over single columns
		for (int x = 0; x < size; x++) {
			int bits = 0;
			int runColor = 0;
			int runLen = 0;
			for (int y = 0, index = x; y < size; y++, index += size) {
				
				// Adjacent modules having same color
				int bit = (modules[index >>> 5] >>> index) & 1;
				if (bit != runColor) {
					runColor = bit;
					runLen = 1;
				} else {
					runLen++;
					if (runLen == 5)
						result += PENALTY_N1;
					else if (runLen > 5)
						result++;
				}
				
				// Finder-like pattern
				bits = ((bits & 0b1111111111) << 1) | bit;
				if (y >= 10 && (bits == 0b00001011101 || bits == 0b10111010000))
					result += PENALTY_N3;
			}
		}
		
		// Balance of black and white modules
		int total = size * size;  // Note that size is odd, so black/total != 1/2
		// Compute the smallest integer k >= 0 such that (45-5k)% <= black/total <= (55+5k)%
		int k = (Math.abs(black * 20 - total * 10) + total - 1) / total - 1;
		result += k * PENALTY_N4;
		return result;
	}
	
	
	
	/*---- Private static helper functions ----*/
	
	// Returns the number of 8-bit data (i.e. not error correction) codewords contained in any
	// QR Code of the given version number and error correction level, with remainder bits discarded.
	// This stateless pure function could be implemented as a (40*4)-cell lookup table.
	static int getNumDataCodewords(int ver, Ecc ecl) {
		if (ver < MIN_VERSION || ver > MAX_VERSION)
			throw new IllegalArgumentException("Version number out of range");
		return QrTemplate.getNumRawDataModules(ver) / 8
			- ECC_CODEWORDS_PER_BLOCK[ecl.ordinal()][ver]
			* NUM_ERROR_CORRECTION_BLOCKS[ecl.ordinal()][ver];
	}
	
	
	/*---- Private tables of constants ----*/
	
	// For use in getPenaltyScore(), when evaluating which mask is best.
	private static final int PENALTY_N1 = 3;
	private static final int PENALTY_N2 = 3;
	private static final int PENALTY_N3 = 40;
	private static final int PENALTY_N4 = 10;
	
	
	private static final byte[][] ECC_CODEWORDS_PER_BLOCK = {
		// Version: (note that index 0 is for padding, and is set to an illegal value)
		//0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
		{-1,  7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},  // Low
		{-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28},  // Medium
		{-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},  // Quartile
		{-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},  // High
	};
	
	private static final byte[][] NUM_ERROR_CORRECTION_BLOCKS = {
		// Version: (note that index 0 is for padding, and is set to an illegal value)
		//0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
		{-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4,  4,  4,  4,  4,  6,  6,  6,  6,  7,  8,  8,  9,  9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25},  // Low
		{-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5,  5,  8,  9,  9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49},  // Medium
		{-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8,  8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68},  // Quartile
		{-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81},  // High
	};
	
	
	
	/*---- Public helper enumeration ----*/
	
	public enum Ecc {
		// These enum constants must be declared in ascending order of error protection,
		// for the sake of the implicit ordinal() method and values() function.
		LOW(1), MEDIUM(0), QUARTILE(3), HIGH(2);
		
		// In the range 0 to 3 (unsigned 2-bit integer).
		final int formatBits;
		
		// Constructor.
		private Ecc(int fb) {
			formatBits = fb;
		}
	}
	
}
