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

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


/**
 * Represents an immutable square grid of black and white cells for a QR Code symbol, and
 * provides static functions to create a QR Code from user-supplied textual or binary data.
 * <p>This class covers the QR Code model 2 specification, supporting all versions (sizes)
 * from 1 to 40, all 4 error correction levels, and only 3 character encoding modes.</p>
 */
public final class QrCode {
	
	/*---- Public static factory functions ----*/
	
	/**
	 * Returns a QR Code symbol representing the specified Unicode text string at the specified error correction level.
	 * As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer
	 * Unicode code points (not UTF-16 code units) if the low error correction level is used. The smallest possible
	 * QR Code version is automatically chosen for the output. The ECC level of the result may be higher than the
	 * ecl argument if it can be done without increasing the version.
	 * @param text the text to be encoded, which can be any Unicode string
	 * @param ecl the error correction level to use (will be boosted)
	 * @return a QR Code representing the text
	 * @throws NullPointerException if the text or error correction level is {@code null}
	 * @throws IllegalArgumentException if the text fails to fit in the largest version QR Code, which means it is too long
	 */
	public static QrCode encodeText(String text, Ecc ecl) {
		Objects.requireNonNull(text);
		Objects.requireNonNull(ecl);
		List<QrSegment> segs = QrSegment.makeSegments(text);
		return encodeSegments(segs, ecl);
	}
	
	
	/**
	 * Returns a QR Code symbol representing the specified binary data string at the specified error correction level.
	 * This function always encodes using the binary segment mode, not any text mode. The maximum number of
	 * bytes allowed is 2953. The smallest possible QR Code version is automatically chosen for the output.
	 * The ECC level of the result may be higher than the ecl argument if it can be done without increasing the version.
	 * @param data the binary data to encode
	 * @param ecl the error correction level to use (will be boosted)
	 * @return a QR Code representing the binary data
	 * @throws NullPointerException if the data or error correction level is {@code null}
	 * @throws IllegalArgumentException if the data fails to fit in the largest version QR Code, which means it is too long
	 */
	public static QrCode encodeBinary(byte[] data, Ecc ecl) {
		Objects.requireNonNull(data);
		Objects.requireNonNull(ecl);
		QrSegment seg = QrSegment.makeBytes(data);
		return encodeSegments(Arrays.asList(seg), ecl);
	}
	
	
	/**
	 * Returns a QR Code symbol representing the specified data segments at the specified error correction
	 * level or higher. The smallest possible QR Code version is automatically chosen for the output.
	 * <p>This function allows the user to create a custom sequence of segments that switches
	 * between modes (such as alphanumeric and binary) to encode text more efficiently.
	 * This function is considered to be lower level than simply encoding text or binary data.</p>
	 * @param segs the segments to encode
	 * @param ecl the error correction level to use (will be boosted)
	 * @return a QR Code representing the segments
	 * @throws NullPointerException if the list of segments, a segment, or the error correction level is {@code null}
	 * @throws IllegalArgumentException if the data is too long to fit in the largest version QR Code at the ECL
	 */
	public static QrCode encodeSegments(List<QrSegment> segs, Ecc ecl) {
		return encodeSegments(segs, ecl, MIN_VERSION, MAX_VERSION, -1, true);
	}
	
	
	/**
	 * Returns a QR Code symbol representing the specified data segments with the specified encoding parameters.
	 * The smallest possible QR Code version within the specified range is automatically chosen for the output.
	 * <p>This function allows the user to create a custom sequence of segments that switches
	 * between modes (such as alphanumeric and binary) to encode text more efficiently.
	 * This function is considered to be lower level than simply encoding text or binary data.</p>
	 * @param segs the segments to encode
	 * @param ecl the error correction level to use (may be boosted)
	 * @param minVersion the minimum allowed version of the QR symbol (at least 1)
	 * @param maxVersion the maximum allowed version of the QR symbol (at most 40)
	 * @param mask the mask pattern to use, which is either -1 for automatic choice or from 0 to 7 for fixed choice
	 * @param boostEcl increases the error correction level if it can be done without increasing the version number
	 * @return a QR Code representing the segments
	 * @throws NullPointerException if the list of segments, a segment, or the error correction level is {@code null}
	 * @throws IllegalArgumentException if 1 &le; minVersion &le; maxVersion &le; 40 is violated, or if mask
	 * &lt; &minus;1 or mask > 7, or if the data is too long to fit in a QR Code at maxVersion at the ECL
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
			if (version >= maxVersion)  // All versions in the range could not fit the given data
				throw new IllegalArgumentException("Data too long");
		}
		if (dataUsedBits == -1)
			throw new AssertionError();
		
		// Increase the error correction level while the data still fits in the current version number
		for (Ecc newEcl : Ecc.values()) {
			if (boostEcl && dataUsedBits <= getNumDataCodewords(version, newEcl) * 8)
				ecl = newEcl;
		}
		
		// Create the data bit string by concatenating all segments
		int dataCapacityBits = getNumDataCodewords(version, ecl) * 8;
		BitBuffer bb = new BitBuffer();
		for (QrSegment seg : segs) {
			bb.appendBits(seg.mode.modeBits, 4);
			bb.appendBits(seg.numChars, seg.mode.numCharCountBits(version));
			bb.appendData(seg);
		}
		
		// Add terminator and pad up to a byte if applicable
		bb.appendBits(0, Math.min(4, dataCapacityBits - bb.bitLength()));
		bb.appendBits(0, (8 - bb.bitLength() % 8) % 8);
		
		// Pad with alternate bytes until data capacity is reached
		for (int padByte = 0xEC; bb.bitLength() < dataCapacityBits; padByte ^= 0xEC ^ 0x11)
			bb.appendBits(padByte, 8);
		if (bb.bitLength() % 8 != 0)
			throw new AssertionError();
		
		// Create the QR Code symbol
		return new QrCode(version, ecl, bb.getBytes(), mask);
	}
	
	
	
	/*---- Public constants ----*/
	
	public static final int MIN_VERSION =  1;
	public static final int MAX_VERSION = 40;
	
	
	
	/*---- Instance fields ----*/
	
	// Public immutable scalar parameters
	
	/** This QR Code symbol's version number, which is always between 1 and 40 (inclusive). */
	public final int version;
	
	/** The width and height of this QR Code symbol, measured in modules.
	 * Always equal to version &times; 4 + 17, in the range 21 to 177. */
	public final int size;
	
	/** The error correction level used in this QR Code symbol. Never {@code null}. */
	public final Ecc errorCorrectionLevel;
	
	/** The mask pattern used in this QR Code symbol, in the range 0 to 7 (i.e. unsigned 3-bit integer).
	 * Note that even if a constructor was called with automatic masking requested
	 * (mask = -1), the resulting object will still have a mask value between 0 and 7. */
	public final int mask;
	
	// Private grids of modules/pixels (conceptually immutable)
	private boolean[][] modules;     // The modules of this QR Code symbol (false = white, true = black)
	private boolean[][] isFunction;  // Indicates function modules that are not subjected to masking
	
	
	
	/*---- Constructors ----*/
	
	/**
	 * Creates a new QR Code symbol with the specified version number, error correction level, binary data array, and mask number.
	 * <p>This is a cumbersome low-level constructor that should not be invoked directly by the user.
	 * To go one level up, see the {@link #encodeSegments(List,Ecc)} function.</p>
	 * @param ver the version number to use, which must be in the range 1 to 40, inclusive
	 * @param ecl the error correction level to use
	 * @param dataCodewords the raw binary user data to encode
	 * @param mask the mask pattern to use, which is either -1 for automatic choice or from 0 to 7 for fixed choice
	 * @throws NullPointerException if the byte array or error correction level is {@code null}
	 * @throws IllegalArgumentException if the version or mask value is out of range
	 */
	public QrCode(int ver, Ecc ecl, byte[] dataCodewords, int mask) {
		// Check arguments
		Objects.requireNonNull(ecl);
		if (ver < MIN_VERSION || ver > MAX_VERSION || mask < -1 || mask > 7)
			throw new IllegalArgumentException("Value out of range");
		Objects.requireNonNull(dataCodewords);
		
		// Initialize fields
		version = ver;
		size = ver * 4 + 17;
		errorCorrectionLevel = ecl;
		modules = new boolean[size][size];  // Entirely white grid
		isFunction = new boolean[size][size];
		
		// Draw function patterns, draw all codewords, do masking
		drawFunctionPatterns();
		byte[] allCodewords = appendErrorCorrection(dataCodewords);
		drawCodewords(allCodewords);
		this.mask = handleConstructorMasking(mask);
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
		return 0 <= x && x < size && 0 <= y && y < size && modules[y][x];
	}
	
	
	/**
	 * Returns a new image object representing this QR Code, with the specified module scale and number
	 * of border modules. For example, the arguments scale=10, border=4 means to pad the QR Code symbol
	 * with 4 white border modules on all four edges, then use 10*10 pixels to represent each module.
	 * The resulting image only contains the hex colors 000000 and FFFFFF.
	 * @param scale the module scale factor, which must be positive
	 * @param border the number of border modules to add, which must be non-negative
	 * @return an image representing this QR Code, with padding and scaling
	 * @throws IllegalArgumentException if the scale or border is out of range
	 */
	public BufferedImage toImage(int scale, int border) {
		if (scale <= 0 || border < 0)
			throw new IllegalArgumentException("Value out of range");
		if (border > Integer.MAX_VALUE / 2 || size + border * 2L > Integer.MAX_VALUE / scale)
			throw new IllegalArgumentException("Scale or border too large");
		
		BufferedImage result = new BufferedImage((size + border * 2) * scale, (size + border * 2) * scale, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < result.getHeight(); y++) {
			for (int x = 0; x < result.getWidth(); x++) {
				boolean val = getModule(x / scale - border, y / scale - border);
				result.setRGB(x, y, val ? 0x000000 : 0xFFFFFF);
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
	 */
	public String toSvgString(int border) {
		if (border < 0)
			throw new IllegalArgumentException("Border must be non-negative");
		if (size + border * 2L > Integer.MAX_VALUE)
			throw new IllegalArgumentException("Border too large");
		
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n");
		sb.append(String.format(
			"<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 %1$d %1$d\" stroke=\"none\">\n",
			size + border * 2));
		sb.append("\t<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n");
		sb.append("\t<path d=\"");
		boolean head = true;
		for (int y = -border; y < size + border; y++) {
			for (int x = -border; x < size + border; x++) {
				if (getModule(x, y)) {
					if (head)
						head = false;
					else
						sb.append(" ");
					sb.append(String.format("M%d,%dh1v1h-1z", x + border, y + border));
				}
			}
		}
		sb.append("\" fill=\"#000000\"/>\n");
		sb.append("</svg>\n");
		return sb.toString();
	}
	
	
	
	/*---- Private helper methods for constructor: Drawing function modules ----*/
	
	private void drawFunctionPatterns() {
		// Draw horizontal and vertical timing patterns
		for (int i = 0; i < size; i++) {
			setFunctionModule(6, i, i % 2 == 0);
			setFunctionModule(i, 6, i % 2 == 0);
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
		drawFormatBits(0);  // Dummy mask value; overwritten later in the constructor
		drawVersion();
	}
	
	
	// Draws two copies of the format bits (with its own error correction code)
	// based on the given mask and this object's error correction level field.
	private void drawFormatBits(int mask) {
		// Calculate error correction code and pack bits
		int data = errorCorrectionLevel.formatBits << 3 | mask;  // errCorrLvl is uint2, mask is uint3
		int rem = data;
		for (int i = 0; i < 10; i++)
			rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
		data = data << 10 | rem;
		data ^= 0x5412;  // uint15
		if (data >>> 15 != 0)
			throw new AssertionError();
		
		// Draw first copy
		for (int i = 0; i <= 5; i++)
			setFunctionModule(8, i, ((data >>> i) & 1) != 0);
		setFunctionModule(8, 7, ((data >>> 6) & 1) != 0);
		setFunctionModule(8, 8, ((data >>> 7) & 1) != 0);
		setFunctionModule(7, 8, ((data >>> 8) & 1) != 0);
		for (int i = 9; i < 15; i++)
			setFunctionModule(14 - i, 8, ((data >>> i) & 1) != 0);
		
		// Draw second copy
		for (int i = 0; i <= 7; i++)
			setFunctionModule(size - 1 - i, 8, ((data >>> i) & 1) != 0);
		for (int i = 8; i < 15; i++)
			setFunctionModule(8, size - 15 + i, ((data >>> i) & 1) != 0);
		setFunctionModule(8, size - 8, true);
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
			boolean bit = ((data >>> i) & 1) != 0;
			int a = size - 11 + i % 3, b = i / 3;
			setFunctionModule(a, b, bit);
			setFunctionModule(b, a, bit);
		}
	}
	
	
	// Draws a 9*9 finder pattern including the border separator, with the center module at (x, y).
	private void drawFinderPattern(int x, int y) {
		for (int i = -4; i <= 4; i++) {
			for (int j = -4; j <= 4; j++) {
				int dist = Math.max(Math.abs(i), Math.abs(j));  // Chebyshev/infinity norm
				int xx = x + j, yy = y + i;
				if (0 <= xx && xx < size && 0 <= yy && yy < size)
					setFunctionModule(xx, yy, dist != 2 && dist != 4);
			}
		}
	}
	
	
	// Draws a 5*5 alignment pattern, with the center module at (x, y).
	private void drawAlignmentPattern(int x, int y) {
		for (int i = -2; i <= 2; i++) {
			for (int j = -2; j <= 2; j++)
				setFunctionModule(x + j, y + i, Math.max(Math.abs(i), Math.abs(j)) != 1);
		}
	}
	
	
	// Sets the color of a module and marks it as a function module.
	// Only used by the constructor. Coordinates must be in range.
	private void setFunctionModule(int x, int y, boolean isBlack) {
		modules[y][x] = isBlack;
		isFunction[y][x] = true;
	}
	
	
	/*---- Private helper methods for constructor: Codewords and masking ----*/
	
	// Returns a new byte string representing the given data with the appropriate error correction
	// codewords appended to it, based on this object's version and error correction level.
	private byte[] appendErrorCorrection(byte[] data) {
		if (data.length != getNumDataCodewords(version, errorCorrectionLevel))
			throw new IllegalArgumentException();
		
		// Calculate parameter numbers
		int numBlocks = NUM_ERROR_CORRECTION_BLOCKS[errorCorrectionLevel.ordinal()][version];
		int blockEccLen = ECC_CODEWORDS_PER_BLOCK[errorCorrectionLevel.ordinal()][version];
		int rawCodewords = getNumRawDataModules(version) / 8;
		int numShortBlocks = numBlocks - rawCodewords % numBlocks;
		int shortBlockLen = rawCodewords / numBlocks;
		
		// Split data into blocks and append ECC to each block
		byte[][] blocks = new byte[numBlocks][];
		ReedSolomonGenerator rs = new ReedSolomonGenerator(blockEccLen);
		for (int i = 0, k = 0; i < numBlocks; i++) {
			byte[] dat = Arrays.copyOfRange(data, k, k + shortBlockLen - blockEccLen + (i < numShortBlocks ? 0 : 1));
			byte[] block = Arrays.copyOf(dat, shortBlockLen + 1);
			k += dat.length;
			byte[] ecc = rs.getRemainder(dat);
			System.arraycopy(ecc, 0, block, block.length - blockEccLen, ecc.length);
			blocks[i] = block;
		}
		
		// Interleave (not concatenate) the bytes from every block into a single sequence
		byte[] result = new byte[rawCodewords];
		for (int i = 0, k = 0; i < blocks[0].length; i++) {
			for (int j = 0; j < blocks.length; j++) {
				// Skip the padding byte in short blocks
				if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) {
					result[k] = blocks[j][i];
					k++;
				}
			}
		}
		return result;
	}
	
	
	// Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
	// data area of this QR Code symbol. Function modules need to be marked off before this is called.
	private void drawCodewords(byte[] data) {
		Objects.requireNonNull(data);
		if (data.length != getNumRawDataModules(version) / 8)
			throw new IllegalArgumentException();
		
		int i = 0;  // Bit index into the data
		// Do the funny zigzag scan
		for (int right = size - 1; right >= 1; right -= 2) {  // Index of right column in each column pair
			if (right == 6)
				right = 5;
			for (int vert = 0; vert < size; vert++) {  // Vertical counter
				for (int j = 0; j < 2; j++) {
					int x = right - j;  // Actual x coordinate
					boolean upward = ((right + 1) & 2) == 0;
					int y = upward ? size - 1 - vert : vert;  // Actual y coordinate
					if (!isFunction[y][x] && i < data.length * 8) {
						modules[y][x] = ((data[i >>> 3] >>> (7 - (i & 7))) & 1) != 0;
						i++;
					}
					// If there are any remainder bits (0 to 7), they are already
					// set to 0/false/white when the grid of modules was initialized
				}
			}
		}
		if (i != data.length * 8)
			throw new AssertionError();
	}
	
	
	// XORs the data modules in this QR Code with the given mask pattern. Due to XOR's mathematical
	// properties, calling applyMask(m) twice with the same value is equivalent to no change at all.
	// This means it is possible to apply a mask, undo it, and try another mask. Note that a final
	// well-formed QR Code symbol needs exactly one mask applied (not zero, not two, etc.).
	private void applyMask(int mask) {
		if (mask < 0 || mask > 7)
			throw new IllegalArgumentException("Mask value out of range");
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
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
				modules[y][x] ^= invert & !isFunction[y][x];
			}
		}
	}
	
	
	// A messy helper function for the constructors. This QR Code must be in an unmasked state when this
	// method is called. The given argument is the requested mask, which is -1 for auto or 0 to 7 for fixed.
	// This method applies and returns the actual mask chosen, from 0 to 7.
	private int handleConstructorMasking(int mask) {
		if (mask == -1) {  // Automatically choose best mask
			int minPenalty = Integer.MAX_VALUE;
			for (int i = 0; i < 8; i++) {
				drawFormatBits(i);
				applyMask(i);
				int penalty = getPenaltyScore();
				if (penalty < minPenalty) {
					mask = i;
					minPenalty = penalty;
				}
				applyMask(i);  // Undoes the mask due to XOR
			}
		}
		if (mask < 0 || mask > 7)
			throw new AssertionError();
		drawFormatBits(mask);  // Overwrite old format bits
		applyMask(mask);  // Apply the final choice of mask
		return mask;  // The caller shall assign this value to the final-declared field
	}
	
	
	// Calculates and returns the penalty score based on state of this QR Code's current modules.
	// This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
	private int getPenaltyScore() {
		int result = 0;
		
		// Adjacent modules in row having same color
		for (int y = 0; y < size; y++) {
			boolean colorX = false;
			for (int x = 0, runX = 0; x < size; x++) {
				if (x == 0 || modules[y][x] != colorX) {
					colorX = modules[y][x];
					runX = 1;
				} else {
					runX++;
					if (runX == 5)
						result += PENALTY_N1;
					else if (runX > 5)
						result++;
				}
			}
		}
		// Adjacent modules in column having same color
		for (int x = 0; x < size; x++) {
			boolean colorY = false;
			for (int y = 0, runY = 0; y < size; y++) {
				if (y == 0 || modules[y][x] != colorY) {
					colorY = modules[y][x];
					runY = 1;
				} else {
					runY++;
					if (runY == 5)
						result += PENALTY_N1;
					else if (runY > 5)
						result++;
				}
			}
		}
		
		// 2*2 blocks of modules having same color
		for (int y = 0; y < size - 1; y++) {
			for (int x = 0; x < size - 1; x++) {
				boolean color = modules[y][x];
				if (  color == modules[y][x + 1] &&
				      color == modules[y + 1][x] &&
				      color == modules[y + 1][x + 1])
					result += PENALTY_N2;
			}
		}
		
		// Finder-like pattern in rows
		for (int y = 0; y < size; y++) {
			for (int x = 0, bits = 0; x < size; x++) {
				bits = ((bits << 1) & 0x7FF) | (modules[y][x] ? 1 : 0);
				if (x >= 10 && (bits == 0x05D || bits == 0x5D0))  // Needs 11 bits accumulated
					result += PENALTY_N3;
			}
		}
		// Finder-like pattern in columns
		for (int x = 0; x < size; x++) {
			for (int y = 0, bits = 0; y < size; y++) {
				bits = ((bits << 1) & 0x7FF) | (modules[y][x] ? 1 : 0);
				if (y >= 10 && (bits == 0x05D || bits == 0x5D0))  // Needs 11 bits accumulated
					result += PENALTY_N3;
			}
		}
		
		// Balance of black and white modules
		int black = 0;
		for (boolean[] row : modules) {
			for (boolean color : row) {
				if (color)
					black++;
			}
		}
		int total = size * size;
		// Find smallest k such that (45-5k)% <= dark/total <= (55+5k)%
		for (int k = 0; black*20 < (9-k)*total || black*20 > (11+k)*total; k++)
			result += PENALTY_N4;
		return result;
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
	private static int getNumRawDataModules(int ver) {
		if (ver < MIN_VERSION || ver > MAX_VERSION)
			throw new IllegalArgumentException("Version number out of range");
		
		int size = ver * 4 + 17;
		int result = size * size;   // Number of modules in the whole QR symbol square
		result -= 64 * 3;           // Subtract the three finders with separators
		result -= 15 * 2 + 1;       // Subtract the format information and black module
		result -= (size - 16) * 2;  // Subtract the timing patterns
		// The five lines above are equivalent to: int result = (16 * ver + 128) * ver + 64;
		if (ver >= 2) {
			int numAlign = ver / 7 + 2;
			result -= (numAlign - 1) * (numAlign - 1) * 25;  // Subtract alignment patterns not overlapping with timing patterns
			result -= (numAlign - 2) * 2 * 20;  // Subtract alignment patterns that overlap with timing patterns
			// The two lines above are equivalent to: result -= (25 * numAlign - 10) * numAlign - 55;
			if (ver >= 7)
				result -= 18 * 2;  // Subtract version information
		}
		return result;
	}
	
	
	// Returns the number of 8-bit data (i.e. not error correction) codewords contained in any
	// QR Code of the given version number and error correction level, with remainder bits discarded.
	// This stateless pure function could be implemented as a (40*4)-cell lookup table.
	static int getNumDataCodewords(int ver, Ecc ecl) {
		if (ver < MIN_VERSION || ver > MAX_VERSION)
			throw new IllegalArgumentException("Version number out of range");
		return getNumRawDataModules(ver) / 8
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
	
	/**
	 * Represents the error correction level used in a QR Code symbol.
	 */
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
	
	
	
	/*---- Private helper class ----*/
	
	/**
	 * Computes the Reed-Solomon error correction codewords for a sequence of data codewords
	 * at a given degree. Objects are immutable, and the state only depends on the degree.
	 * This class exists because each data block in a QR Code shares the same the divisor polynomial.
	 */
	private static final class ReedSolomonGenerator {
		
		/*-- Immutable field --*/
		
		// Coefficients of the divisor polynomial, stored from highest to lowest power, excluding the leading term which
		// is always 1. For example the polynomial x^3 + 255x^2 + 8x + 93 is stored as the uint8 array {255, 8, 93}.
		private final byte[] coefficients;
		
		
		/*-- Constructor --*/
		
		/**
		 * Creates a Reed-Solomon ECC generator for the specified degree. This could be implemented
		 * as a lookup table over all possible parameter values, instead of as an algorithm.
		 * @param degree the divisor polynomial degree, which must be between 1 and 255
		 * @throws IllegalArgumentException if degree &lt; 1 or degree > 255
		 */
		public ReedSolomonGenerator(int degree) {
			if (degree < 1 || degree > 255)
				throw new IllegalArgumentException("Degree out of range");
			
			// Start with the monomial x^0
			coefficients = new byte[degree];
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
		}
		
		
		/*-- Method --*/
		
		/**
		 * Computes and returns the Reed-Solomon error correction codewords for the specified
		 * sequence of data codewords. The returned object is always a new byte array.
		 * This method does not alter this object's state (because it is immutable).
		 * @param data the sequence of data codewords
		 * @return the Reed-Solomon error correction codewords
		 * @throws NullPointerException if the data is {@code null}
		 */
		public byte[] getRemainder(byte[] data) {
			Objects.requireNonNull(data);
			
			// Compute the remainder by performing polynomial division
			byte[] result = new byte[coefficients.length];
			for (byte b : data) {
				int factor = (b ^ result[0]) & 0xFF;
				System.arraycopy(result, 1, result, 0, result.length - 1);
				result[result.length - 1] = 0;
				for (int i = 0; i < result.length; i++)
					result[i] ^= multiply(coefficients[i] & 0xFF, factor);
			}
			return result;
		}
		
		
		/*-- Static function --*/
		
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
		
	}
	
}
