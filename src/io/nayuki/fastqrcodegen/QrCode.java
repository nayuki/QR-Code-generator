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


/**
 * A QR Code symbol, which is a type of two-dimension barcode.
 * Invented by Denso Wave and described in the ISO/IEC 18004 standard.
 * <p>Instances of this class represent an immutable square grid of black and white cells.
 * The class provides static factory functions to create a QR Code from text or binary data.
 * The class covers the QR Code Model 2 specification, supporting all versions (sizes)
 * from 1 to 40, all 4 error correction levels, and 4 character encoding modes.</p>
 * <p>Ways to create a QR Code object:</p>
 * <ul>
 *   <li><p>High level: Take the payload data and call {@link QrCode#encodeText(String,Ecc)}
 *     or {@link QrCode#encodeBinary(byte[],Ecc)}.</p></li>
 *   <li><p>Mid level: Custom-make the list of {@link QrSegment segments}
 *     and call {@link QrCode#encodeSegments(List,Ecc)} or
 *     {@link QrCode#encodeSegments(List,Ecc,int,int,int,boolean)}</p></li>
 *   <li><p>Low level: Custom-make the array of data codeword bytes (including segment headers and
 *     final padding, excluding error correction codewords), supply the appropriate version number,
 *     and call the {@link QrCode#QrCode(int,Ecc,byte[],int) constructor}.</p></li>
 * </ul>
 * <p>(Note that all ways require supplying the desired error correction level.)</p>
 * @see QrSegment
 */
public final class QrCode {
	
	/*---- Static factory functions (high level) ----*/
	
	/**
	 * Returns a QR Code representing the specified Unicode text string at the specified error correction level.
	 * As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer
	 * Unicode code points (not UTF-16 code units) if the low error correction level is used. The smallest possible
	 * QR Code version is automatically chosen for the output. The ECC level of the result may be higher than the
	 * ecl argument if it can be done without increasing the version.
	 * @param text the text to be encoded (not {@code null}), which can be any Unicode string
	 * @param ecl the error correction level to use (not {@code null}) (boostable)
	 * @return a QR Code (not {@code null}) representing the text
	 * @throws NullPointerException if the text or error correction level is {@code null}
	 * @throws DataTooLongException if the text fails to fit in the
	 * largest version QR Code at the ECL, which means it is too long
	 */
	public static QrCode encodeText(String text, Ecc ecl) {
		Objects.requireNonNull(text);
		Objects.requireNonNull(ecl);
		List<QrSegment> segs = QrSegment.makeSegments(text);
		return encodeSegments(segs, ecl);
	}
	
	
	/**
	 * Returns a QR Code representing the specified binary data at the specified error correction level.
	 * This function always encodes using the binary segment mode, not any text mode. The maximum number of
	 * bytes allowed is 2953. The smallest possible QR Code version is automatically chosen for the output.
	 * The ECC level of the result may be higher than the ecl argument if it can be done without increasing the version.
	 * @param data the binary data to encode (not {@code null})
	 * @param ecl the error correction level to use (not {@code null}) (boostable)
	 * @return a QR Code (not {@code null}) representing the data
	 * @throws NullPointerException if the data or error correction level is {@code null}
	 * @throws DataTooLongException if the data fails to fit in the
	 * largest version QR Code at the ECL, which means it is too long
	 */
	public static QrCode encodeBinary(byte[] data, Ecc ecl) {
		Objects.requireNonNull(data);
		Objects.requireNonNull(ecl);
		QrSegment seg = QrSegment.makeBytes(data);
		return encodeSegments(Arrays.asList(seg), ecl);
	}
	
	
	/*---- Static factory functions (mid level) ----*/
	
	/**
	 * Returns a QR Code representing the specified segments at the specified error correction
	 * level. The smallest possible QR Code version is automatically chosen for the output. The ECC level
	 * of the result may be higher than the ecl argument if it can be done without increasing the version.
	 * <p>This function allows the user to create a custom sequence of segments that switches
	 * between modes (such as alphanumeric and byte) to encode text in less space.
	 * This is a mid-level API; the high-level API is {@link #encodeText(String,Ecc)}
	 * and {@link #encodeBinary(byte[],Ecc)}.</p>
	 * @param segs the segments to encode
	 * @param ecl the error correction level to use (not {@code null}) (boostable)
	 * @return a QR Code (not {@code null}) representing the segments
	 * @throws NullPointerException if the list of segments, any segment, or the error correction level is {@code null}
	 * @throws DataTooLongException if the segments fail to fit in the
	 * largest version QR Code at the ECL, which means they are too long
	 */
	public static QrCode encodeSegments(List<QrSegment> segs, Ecc ecl) {
		return encodeSegments(segs, ecl, MIN_VERSION, MAX_VERSION, -1, true);
	}
	
	
	/**
	 * Returns a QR Code representing the specified segments with the specified encoding parameters.
	 * The smallest possible QR Code version within the specified range is automatically
	 * chosen for the output. Iff boostEcl is {@code true}, then the ECC level of the
	 * result may be higher than the ecl argument if it can be done without increasing
	 * the version. The mask number is either between 0 to 7 (inclusive) to force that
	 * mask, or &#x2212;1 to automatically choose an appropriate mask (which may be slow).
	 * <p>This function allows the user to create a custom sequence of segments that switches
	 * between modes (such as alphanumeric and byte) to encode text in less space.
	 * This is a mid-level API; the high-level API is {@link #encodeText(String,Ecc)}
	 * and {@link #encodeBinary(byte[],Ecc)}.</p>
	 * @param segs the segments to encode
	 * @param ecl the error correction level to use (not {@code null}) (boostable)
	 * @param minVersion the minimum allowed version of the QR Code (at least 1)
	 * @param maxVersion the maximum allowed version of the QR Code (at most 40)
	 * @param mask the mask number to use (between 0 and 7 (inclusive)), or &#x2212;1 for automatic mask
	 * @param boostEcl increases the ECC level as long as it doesn't increase the version number
	 * @return a QR Code (not {@code null}) representing the segments
	 * @throws NullPointerException if the list of segments, any segment, or the error correction level is {@code null}
	 * @throws IllegalArgumentException if 1 &#x2264; minVersion &#x2264; maxVersion &#x2264; 40
	 * or &#x2212;1 &#x2264; mask &#x2264; 7 is violated
	 * @throws DataTooLongException if the segments fail to fit in
	 * the maxVersion QR Code at the ECL, which means they are too long
	 */
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
			if (version >= maxVersion) {  // All versions in the range could not fit the given data
				String msg = "Segment too long";
				if (dataUsedBits != -1)
					msg = String.format("Data length = %d bits, Max capacity = %d bits", dataUsedBits, dataCapacityBits);
				throw new DataTooLongException(msg);
			}
		}
		assert dataUsedBits != -1;
		
		// Increase the error correction level while the data still fits in the current version number
		for (Ecc newEcl : Ecc.values()) {  // From low to high
			if (boostEcl && dataUsedBits <= getNumDataCodewords(version, newEcl) * 8)
				ecl = newEcl;
		}
		
		// Concatenate all segments to create the data bit string
		BitBuffer bb = new BitBuffer();
		for (QrSegment seg : segs) {
			bb.appendBits(seg.mode.modeBits, 4);
			bb.appendBits(seg.numChars, seg.mode.numCharCountBits(version));
			bb.appendBits(seg.data, seg.bitLength);
		}
		assert bb.bitLength == dataUsedBits;
		
		// Add terminator and pad up to a byte if applicable
		int dataCapacityBits = getNumDataCodewords(version, ecl) * 8;
		assert bb.bitLength <= dataCapacityBits;
		bb.appendBits(0, Math.min(4, dataCapacityBits - bb.bitLength));
		bb.appendBits(0, (8 - bb.bitLength % 8) % 8);
		assert bb.bitLength % 8 == 0;
		
		// Pad with alternating bytes until data capacity is reached
		for (int padByte = 0xEC; bb.bitLength < dataCapacityBits; padByte ^= 0xEC ^ 0x11)
			bb.appendBits(padByte, 8);
		
		// Create the QR Code object
		return new QrCode(version, ecl, bb.getBytes(), mask);
	}
	
	
	
	/*---- Instance fields ----*/
	
	// Public immutable scalar parameters:
	
	/** The version number of this QR Code, which is between 1 and 40 (inclusive).
	 * This determines the size of this barcode. */
	public final int version;
	
	/** The width and height of this QR Code, measured in modules, between
	 * 21 and 177 (inclusive). This is equal to version &#xD7; 4 + 17. */
	public final int size;
	
	/** The error correction level used in this QR Code, which is not {@code null}. */
	public final Ecc errorCorrectionLevel;
	
	/** The index of the mask pattern used in this QR Code, which is between 0 and 7 (inclusive).
	 * <p>Even if a QR Code is created with automatic masking requested (mask =
	 * &#x2212;1), the resulting object still has a mask value between 0 and 7. */
	public final int mask;
	
	// Private grid of modules/pixels:
	
	// The modules of this QR Code. Immutable after constructor finishes. Accessed through getModule().
	private final int[] modules;
	
	
	
	/*---- Constructor (low level) ----*/
	
	/**
	 * Constructs a QR Code with the specified version number,
	 * error correction level, data codeword bytes, and mask number.
	 * <p>This is a low-level API that most users should not use directly. A mid-level
	 * API is the {@link #encodeSegments(List,Ecc,int,int,int,boolean)} function.</p>
	 * @param ver the version number to use, which must be in the range 1 to 40 (inclusive)
	 * @param ecl the error correction level to use
	 * @param dataCodewords the bytes representing segments to encode (without ECC)
	 * @param mask the mask pattern to use, which is either &#x2212;1 for automatic choice or from 0 to 7 for fixed choice
	 * @throws NullPointerException if the byte array or error correction level is {@code null}
	 * @throws IllegalArgumentException if the version or mask value is out of range,
	 * or if the data is the wrong length for the specified version and error correction level
	 */
	public QrCode(int ver, Ecc ecl, byte[] dataCodewords, int mask) {
		// Check arguments and initialize fields
		if (ver < MIN_VERSION || ver > MAX_VERSION)
			throw new IllegalArgumentException("Version value out of range");
		if (mask < -1 || mask > 7)
			throw new IllegalArgumentException("Mask value out of range");
		version = ver;
		size = ver * 4 + 17;
		errorCorrectionLevel = Objects.requireNonNull(ecl);
		Objects.requireNonNull(dataCodewords);
		
		QrTemplate tpl = QrTemplate.getInstance(ver);
		modules = tpl.template.clone();
		
		// Compute ECC, draw modules, do masking
		byte[] allCodewords = addEccAndInterleave(dataCodewords);
		drawCodewords(tpl.dataOutputBitIndexes, allCodewords);
		this.mask = handleConstructorMasking(tpl.masks, mask);
	}
	
	
	
	/*---- Public instance methods ----*/
	
	/**
	 * Returns the color of the module (pixel) at the specified coordinates, which is {@code false}
	 * for white or {@code true} for black. The top left corner has the coordinates (x=0, y=0).
	 * If the specified coordinates are out of bounds, then {@code false} (white) is returned.
	 * @param x the x coordinate, where 0 is the left edge and size&#x2212;1 is the right edge
	 * @param y the y coordinate, where 0 is the top edge and size&#x2212;1 is the bottom edge
	 * @return {@code true} if the coordinates are in bounds and the module
	 * at that location is black, or {@code false} (white) otherwise
	 */
	public boolean getModule(int x, int y) {
		if (0 <= x && x < size && 0 <= y && y < size) {
			int i = y * size + x;
			return getBit(modules[i >>> 5], i) != 0;
		} else
			return false;
	}
	
	
	/**
	 * Returns a raster image depicting this QR Code, with the specified module scale and border modules.
	 * <p>For example, toImage(scale=10, border=4) means to pad the QR Code with 4 white
	 * border modules on all four sides, and use 10&#xD7;10 pixels to represent each module.
	 * The resulting image only contains the hex colors 000000 and FFFFFF.
	 * @param scale the side length (measured in pixels, must be positive) of each module
	 * @param border the number of border modules to add, which must be non-negative
	 * @return a new image representing this QR Code, with padding and scaling
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
	 * Returns a string of SVG code for an image depicting this QR Code, with the specified number
	 * of border modules. The string always uses Unix newlines (\n), regardless of the platform.
	 * @param border the number of border modules to add, which must be non-negative
	 * @return a string representing this QR Code as an SVG XML document
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
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				if (getModule(x, y)) {
					if (x != 0 || y != 0)
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
		int bits = (data << 10 | rem) ^ 0x5412;  // uint15
		assert bits >>> 15 == 0;
		
		// Draw first copy
		for (int i = 0; i <= 5; i++)
			setModule(8, i, getBit(bits, i));
		setModule(8, 7, getBit(bits, 6));
		setModule(8, 8, getBit(bits, 7));
		setModule(7, 8, getBit(bits, 8));
		for (int i = 9; i < 15; i++)
			setModule(14 - i, 8, getBit(bits, i));
		
		// Draw second copy
		for (int i = 0; i < 8; i++)
			setModule(size - 1 - i, 8, getBit(bits, i));
		for (int i = 8; i < 15; i++)
			setModule(8, size - 15 + i, getBit(bits, i));
		setModule(8, size - 8, 1);  // Always black
	}
	
	
	// Sets the module at the given coordinates to the given color.
	// Only used by the constructor. Coordinates must be in bounds.
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
		Objects.requireNonNull(data);
		if (data.length != getNumDataCodewords(version, errorCorrectionLevel))
			throw new IllegalArgumentException();
		
		// Calculate parameter numbers
		int numBlocks = NUM_ERROR_CORRECTION_BLOCKS[errorCorrectionLevel.ordinal()][version];
		int blockEccLen = ECC_CODEWORDS_PER_BLOCK  [errorCorrectionLevel.ordinal()][version];
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
			int bit = getBit(allCodewords[i >>> 3], ~i & 7);
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
	
	
	// A messy helper function for the constructor. This QR Code must be in an unmasked state when this
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
		int[] runHistory = new int[7];
		
		// Iterate over adjacent pairs of rows
		for (int index = 0, downIndex = size, end = size * size; index < end; ) {
			Arrays.fill(runHistory, 0);
			int color = 0;
			int runX = 0;
			int curRow = 0;
			int nextRow = 0;
			for (int x = 0; x < size; x++, index++, downIndex++) {
				int c = getBit(modules[index >>> 5], index);
				if (c == color) {
					runX++;
					if (runX == 5)
						result += PENALTY_N1;
					else if (runX > 5)
						result++;
				} else {
					addRunToHistory(runX, runHistory);
					if (color == 0 && hasFinderLikePattern(runHistory))
						result += PENALTY_N3;
					color = c;
					runX = 1;
				}
				black += c;
				if (downIndex < end) {
					curRow = ((curRow << 1) | c) & 3;
					nextRow = ((nextRow << 1) | getBit(modules[downIndex >>> 5], downIndex)) & 3;
					// 2*2 blocks of modules having same color
					if (x >= 1 && (curRow == 0 || curRow == 3) && curRow == nextRow)
						result += PENALTY_N2;
				}
			}
			addRunToHistory(runX, runHistory);
			if (color == 1)
				addRunToHistory(0, runHistory);  // Dummy run of white
			if (hasFinderLikePattern(runHistory))
				result += PENALTY_N3;
		}
		
		// Iterate over single columns
		for (int x = 0; x < size; x++) {
			Arrays.fill(runHistory, 0);
			int color = 0;
			int runY = 0;
			for (int y = 0, index = x; y < size; y++, index += size) {
				int c = getBit(modules[index >>> 5], index);
				if (c == color) {
					runY++;
					if (runY == 5)
						result += PENALTY_N1;
					else if (runY > 5)
						result++;
				} else {
					addRunToHistory(runY, runHistory);
					if (color == 0 && hasFinderLikePattern(runHistory))
						result += PENALTY_N3;
					color = c;
					runY = 1;
				}
			}
			addRunToHistory(runY, runHistory);
			if (color == 1)
				addRunToHistory(0, runHistory);  // Dummy run of white
			if (hasFinderLikePattern(runHistory))
				result += PENALTY_N3;
		}
		
		// Balance of black and white modules
		int total = size * size;  // Note that size is odd, so black/total != 1/2
		// Compute the smallest integer k >= 0 such that (45-5k)% <= black/total <= (55+5k)%
		int k = (Math.abs(black * 20 - total * 10) + total - 1) / total - 1;
		result += k * PENALTY_N4;
		return result;
	}
	
	
	
	/*---- Private helper functions ----*/
	
	// Returns the number of 8-bit data (i.e. not error correction) codewords contained in any
	// QR Code of the given version number and error correction level, with remainder bits discarded.
	// This stateless pure function could be implemented as a (40*4)-cell lookup table.
	static int getNumDataCodewords(int ver, Ecc ecl) {
		return QrTemplate.getNumRawDataModules(ver) / 8
			- ECC_CODEWORDS_PER_BLOCK    [ecl.ordinal()][ver]
			* NUM_ERROR_CORRECTION_BLOCKS[ecl.ordinal()][ver];
	}
	
	
	// Inserts the given value to the front of the given array, which shifts over the
	// existing values and deletes the last value. A helper function for getPenaltyScore().
	private static void addRunToHistory(int run, int[] history) {
		System.arraycopy(history, 0, history, 1, history.length - 1);
		history[0] = run;
	}
	
	
	// Tests whether the given run history has the pattern of ratio 1:1:3:1:1 in the middle, and
	// surrounded by at least 4 on either or both ends. A helper function for getPenaltyScore().
	// Must only be called immediately after a run of white modules has ended.
	private static boolean hasFinderLikePattern(int[] runHistory) {
		int n = runHistory[1];
		return n > 0 && runHistory[2] == n && runHistory[4] == n && runHistory[5] == n
			&& runHistory[3] == n * 3 && Math.max(runHistory[0], runHistory[6]) >= n * 4;
	}
	
	
	// Returns 0 or 1 based on the (i mod 32)'th bit of x.
	static int getBit(int x, int i) {
		return (x >>> i) & 1;
	}
	
	
	/*---- Constants and tables ----*/
	
	/** The minimum version number  (1) supported in the QR Code Model 2 standard. */
	public static final int MIN_VERSION =  1;
	
	/** The maximum version number (40) supported in the QR Code Model 2 standard. */
	public static final int MAX_VERSION = 40;
	
	
	// For use in getPenaltyScore(), when evaluating which mask is best.
	private static final int PENALTY_N1 =  3;
	private static final int PENALTY_N2 =  3;
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
	
	/**
	 * The error correction level in a QR Code symbol.
	 */
	public enum Ecc {
		// Must be declared in ascending order of error protection
		// so that the implicit ordinal() and values() work properly
		/** The QR Code can tolerate about  7% erroneous codewords. */ LOW(1),
		/** The QR Code can tolerate about 15% erroneous codewords. */ MEDIUM(0),
		/** The QR Code can tolerate about 25% erroneous codewords. */ QUARTILE(3),
		/** The QR Code can tolerate about 30% erroneous codewords. */ HIGH(2);
		
		// In the range 0 to 3 (unsigned 2-bit integer).
		final int formatBits;
		
		// Constructor.
		private Ecc(int fb) {
			formatBits = fb;
		}
	}
	
}
