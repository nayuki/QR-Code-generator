/* 
 * QR Code generator library (Java)
 * 
 * Copyright (c) 2016 Project Nayuki
 * https://www.nayuki.io/page/qr-code-generator-library
 * 
 * (MIT License)
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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;


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
	 * As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer Unicode
	 * code points (not UTF-16 code units). The smallest possible QR Code version is automatically chosen for the output.
	 * @param text the text to be encoded, which can be any Unicode string
	 * @param ecl the error correction level to use
	 * @return a QR Code representing the text
	 * @throws NullPointerException if the text or error correction level is {@code null}
	 * @throws IllegalArgumentException if the text fails to fit in the largest version QR Code, which means it is too long
	 */
	public static QrCode encodeText(String text, Ecc ecl) {
		if (text == null || ecl == null)
			throw new NullPointerException();
		QrSegment seg = encodeTextToSegment(text);
		return encodeSegments(Arrays.asList(seg), ecl);
	}
	
	
	/**
	 * Returns a QR Code segment representing the specified Unicode text string.
	 * @param text the text to be encoded, which can be any Unicode string
	 * @return a QR Code representing the text
	 * @throws NullPointerException if the text is {@code null}
	 */
	public static QrSegment encodeTextToSegment(String text) {
		if (text == null)
			throw new NullPointerException();
		// Select the most efficient segment encoding automatically
		if (QrSegment.NUMERIC_REGEX.matcher(text).matches())
			return QrSegment.makeNumeric(text);
		else if (QrSegment.ALPHANUMERIC_REGEX.matcher(text).matches())
			return QrSegment.makeAlphanumeric(text);
		else
			return QrSegment.makeBytes(text.getBytes(StandardCharsets.UTF_8));
	}
	
	
	/**
	 * Returns a QR Code symbol representing the specified binary data string at the specified error correction level.
	 * This function always encodes using the binary segment mode, not any text mode. The maximum number of
	 * bytes allowed is 2953. The smallest possible QR Code version is automatically chosen for the output.
	 * @param data the binary data to encode
	 * @param ecl the error correction level to use
	 * @return a QR Code representing the binary data
	 * @throws NullPointerException if the data or error correction level is {@code null}
	 * @throws IllegalArgumentException if the data fails to fit in the largest version QR Code, which means it is too long
	 */
	public static QrCode encodeBinary(byte[] data, Ecc ecl) {
		if (data == null || ecl == null)
			throw new NullPointerException();
		QrSegment seg = QrSegment.makeBytes(data);
		return encodeSegments(Arrays.asList(seg), ecl);
	}
	
	
	/**
	 * Returns a QR Code symbol representing the specified data segments at the specified error
	 * correction level. The smallest possible QR Code version is automatically chosen for the output.
	 * <p>This function allows the user to create a custom sequence of segments that switches
	 * between modes (such as alphanumeric and binary) to encode text more efficiently. This
	 * function is considered to be lower level than simply encoding text or binary data.</p>
	 * @param segs the segments to encode
	 * @param ecl the error correction level to use
	 * @return a QR Code representing the segments
	 * @throws NullPointerException if the list of segments, a segment, or the error correction level is {@code null}
	 * @throws IllegalArgumentException if the data fails to fit in the largest version QR Code, which means it is too long
	 */
	public static QrCode encodeSegments(List<QrSegment> segs, Ecc ecl) {
		if (segs == null || ecl == null)
			throw new NullPointerException();
		
		// Find the minimal version number to use
		int version, dataCapacityBits;
		outer:
		for (version = 1; ; version++) {  // Increment until the data fits in the QR Code
			if (version > 40)  // All versions could not fit the given data
				throw new IllegalArgumentException("Data too long");
			dataCapacityBits = getNumDataCodewords(version, ecl) * 8;  // Number of data bits available
			
			// Calculate the total number of bits needed at this version number
			// to encode all the segments (i.e. segment metadata and payloads)
			int dataUsedBits = 0;
			for (QrSegment seg : segs) {
				if (seg == null)
					throw new NullPointerException();
				if (seg.numChars < 0)
					throw new AssertionError();
				int ccbits = seg.mode.numCharCountBits(version);
				if (seg.numChars >= (1 << ccbits)) {
					// Segment length value doesn't fit in the length field's bit-width, so fail immediately
					continue outer;
				}
				dataUsedBits += 4 + ccbits + seg.bitLength;
			}
			if (dataUsedBits <= dataCapacityBits)
				break;  // This version number is found to be suitable
		}
		
		// Create the data bit string by concatenating all segments
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
		return new QrCode(version, ecl, bb.getBytes(), -1);
	}
	
	
	
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
	 * Creates a new QR Code symbol with the specified version number, error correction level, binary data string, and mask number.
	 * <p>This cumbersome constructor can be invoked directly by the user, but is considered
	 * to be even lower level than {@link #encodeSegments(List,Ecc)}.</p>
	 * @param ver the version number to use, which must be in the range 1 to 40, inclusive
	 * @param ecl the error correction level to use
	 * @param dataCodewords the raw binary user data to encode
	 * @param mask the mask pattern to use, which is either -1 for automatic choice or from 0 to 7 for fixed choice
	 * @throws NullPointerException if the byte array or error correction level is {@code null}
	 * @throws IllegalArgumentException if the version or mask value is out of range
	 */
	public QrCode(int ver, Ecc ecl, byte[] dataCodewords, int mask) {
		// Check arguments
		if (ecl == null)
			throw new NullPointerException();
		if (ver < 1 || ver > 40 || mask < -1 || mask > 7)
			throw new IllegalArgumentException("Value out of range");
		if (dataCodewords == null)
			throw new NullPointerException();
		
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
	
	
	/**
	 * Creates a new QR Code symbol based on the specified existing object, but with a potentially
	 * different mask pattern. The version, error correction level, codewords, etc. of the newly
	 * created object are all identical to the argument object; only the mask may differ.
	 * @param qr the existing QR Code to copy and modify
	 * @param mask the new mask pattern, 0 to 7 to force a fixed choice or -1 for an automatic choice
	 * @throws NullPointerException if the QR Code is {@code null}
	 * @throws IllegalArgumentException if the mask value is out of range
	 */
	public QrCode(QrCode qr, int mask) {
		// Check arguments
		if (qr == null)
			throw new NullPointerException();
		if (mask < -1 || mask > 7)
			throw new IllegalArgumentException("Mask value out of range");
		
		// Copy scalar fields
		version = qr.version;
		size = qr.size;
		errorCorrectionLevel = qr.errorCorrectionLevel;
		
		// Handle grid fields
		isFunction = qr.isFunction;  // Shallow copy because the data is read-only
		modules = qr.modules.clone();  // Deep copy
		for (int i = 0; i < modules.length; i++)
			modules[i] = modules[i].clone();
		
		// Handle masking
		applyMask(qr.mask);  // Undo old mask
		this.mask = handleConstructorMasking(mask);
	}
	
	
	
	/*---- Public instance methods ----*/
	
	/**
	 * Returns the color of the module (pixel) at the specified coordinates, which is either 0 for white or 1 for black. The top
	 * left corner has the coordinates (x=0, y=0). If the specified coordinates are out of bounds, then 0 (white) is returned.
	 * @param x the x coordinate, where 0 is the left edge and size&minus;1 is the right edge
	 * @param y the y coordinate, where 0 is the top edge and size&minus;1 is the bottom edge
	 * @return the module's color, which is either 0 (white) or 1 (black)
	 */
	public int getModule(int x, int y) {
		if (0 <= x && x < size && 0 <= y && y < size)
			return modules[y][x] ? 1 : 0;
		else
			return 0;  // Infinite white border
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
		BufferedImage result = new BufferedImage((size + border * 2) * scale, (size + border * 2) * scale, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < result.getHeight(); y++) {
			for (int x = 0; x < result.getWidth(); x++) {
				int val = getModule(x / scale - border, y / scale - border);  // 0 or 1
				result.setRGB(x, y, val == 1 ? 0x000000 : 0xFFFFFF);
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
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n");
		sb.append(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 %1$d %1$d\">\n", size + border * 2));
		sb.append("\t<path d=\"");
		boolean head = true;
		for (int y = -border; y < size + border; y++) {
			for (int x = -border; x < size + border; x++) {
				if (getModule(x, y) == 1) {
					if (head)
						head = false;
					else
						sb.append(" ");
					sb.append(String.format("M%d,%dh1v1h-1z", x + border, y + border));
				}
			}
		}
		sb.append("\" fill=\"#000000\" stroke-width=\"0\"/>\n");
		sb.append("</svg>\n");
		return sb.toString();
	}
	
	
	
	/*---- Private helper methods for constructor: Drawing function modules ----*/
	
	private void drawFunctionPatterns() {
		// Draw the horizontal and vertical timing patterns
		for (int i = 0; i < size; i++) {
			setFunctionModule(6, i, i % 2 == 0);
			setFunctionModule(i, 6, i % 2 == 0);
		}
		
		// Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
		drawFinderPattern(3, 3);
		drawFinderPattern(size - 4, 3);
		drawFinderPattern(3, size - 4);
		
		// Draw the numerous alignment patterns
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
		if ((data & ((1 << 15) - 1)) != data)
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
		if ((data & ((1 << 18) - 1)) != data)
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
		int numBlocks = NUM_ERROR_CORRECTION_BLOCKS[errorCorrectionLevel.ordinal()][version];
		int numEcc = NUM_ERROR_CORRECTION_CODEWORDS[errorCorrectionLevel.ordinal()][version];
		if (numEcc % numBlocks != 0)
			throw new AssertionError();
		int eccLen = numEcc / numBlocks;
		int numShortBlocks = numBlocks - getNumRawDataModules(version) / 8 % numBlocks;
		int shortBlockLen = getNumRawDataModules(version) / 8 / numBlocks;
		
		byte[][] blocks = new byte[numBlocks][];
		ReedSolomonGenerator rs = new ReedSolomonGenerator(eccLen);
		for (int i = 0, k = 0; i < numBlocks; i++) {
			byte[] dat = Arrays.copyOfRange(data, k, k + shortBlockLen - eccLen + (i < numShortBlocks ? 0 : 1));
			byte[] block = Arrays.copyOf(dat, shortBlockLen + 1);
			k += dat.length;
			byte[] ecc = rs.getRemainder(dat);
			System.arraycopy(ecc, 0, block, block.length - eccLen, ecc.length);
			blocks[i] = block;
		}
		
		byte[] result = new byte[getNumRawDataModules(version) / 8];
		for (int i = 0, k = 0; i < blocks[0].length; i++) {
			for (int j = 0; j < blocks.length; j++) {
				if (i != shortBlockLen - eccLen || j >= numShortBlocks) {
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
		if (data == null)
			throw new NullPointerException();
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
					boolean upwards = ((right & 2) == 0) ^ (x < 6);
					int y = upwards ? size - 1 - vert : vert;  // Actual y coordinate
					if (!isFunction[y][x] && i < data.length * 8) {
						modules[y][x] = ((data[i >>> 3] >>> (7 - (i & 7))) & 1) != 0;
						i++;
					}
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
			boolean colorX = modules[y][0];
			for (int x = 1, runX = 1; x < size; x++) {
				if (modules[y][x] != colorX) {
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
			boolean colorY = modules[0][x];
			for (int y = 1, runY = 1; y < size; y++) {
				if (modules[y][x] != colorY) {
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
	
	
	
	/*---- Static helper functions ----*/
	
	// Returns a set of positions of the alignment patterns in ascending order. These positions are
	// used on both the x and y axes. Each value in the resulting array is in the range [0, 177).
	// This stateless pure function could be implemented as table of 40 variable-length lists of unsigned bytes.
	private static int[] getAlignmentPatternPositions(int ver) {
		if (ver < 1 || ver > 40)
			throw new IllegalArgumentException("Version number out of range");
		else if (ver == 1)
			return new int[]{};
		else {
			int numAlign = ver / 7 + 2;
			int step;
			if (ver != 32)
				step = (ver * 4 + numAlign * 2 + 1) / (2 * numAlign - 2) * 2;  // ceil((size - 13) / (2*numAlign - 2)) * 2
			else  // C-C-C-Combo breaker!
				step = 26;
			
			int[] result = new int[numAlign];
			int size = ver * 4 + 17;
			result[0] = 6;
			for (int i = result.length - 1, pos = size - 7; i >= 1; i--, pos -= step)
				result[i] = pos;
			return result;
		}
	}
	
	
	// Returns the number of raw data modules (bits) available at the given version number.
	// These data modules are used for both user data codewords and error correction codewords.
	// This stateless pure function could be implemented as a 40-entry lookup table.
	private static int getNumRawDataModules(int ver) {
		if (ver < 1 || ver > 40)
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
	private static int getNumDataCodewords(int ver, Ecc ecl) {
		if (ver < 1 || ver > 40)
			throw new IllegalArgumentException("Version number out of range");
		return getNumRawDataModules(ver) / 8 - NUM_ERROR_CORRECTION_CODEWORDS[ecl.ordinal()][ver];
	}
	
	
	/*---- Tables of constants ----*/
	
	// For use in getPenaltyScore(), when evaluating which mask is best.
	private static final int PENALTY_N1 = 3;
	private static final int PENALTY_N2 = 3;
	private static final int PENALTY_N3 = 40;
	private static final int PENALTY_N4 = 10;
	
	
	private static final short[][] NUM_ERROR_CORRECTION_CODEWORDS = {
		// Version: (note that index 0 is for padding, and is set to an illegal value)
		//0,  1,  2,  3,  4,  5,   6,   7,   8,   9,  10,  11,  12,  13,  14,  15,  16,  17,  18,  19,  20,  21,  22,  23,  24,   25,   26,   27,   28,   29,   30,   31,   32,   33,   34,   35,   36,   37,   38,   39,   40    Error correction level
		{-1,  7, 10, 15, 20, 26,  36,  40,  48,  60,  72,  80,  96, 104, 120, 132, 144, 168, 180, 196, 224, 224, 252, 270, 300,  312,  336,  360,  390,  420,  450,  480,  510,  540,  570,  570,  600,  630,  660,  720,  750},  // Low
		{-1, 10, 16, 26, 36, 48,  64,  72,  88, 110, 130, 150, 176, 198, 216, 240, 280, 308, 338, 364, 416, 442, 476, 504, 560,  588,  644,  700,  728,  784,  812,  868,  924,  980, 1036, 1064, 1120, 1204, 1260, 1316, 1372},  // Medium
		{-1, 13, 22, 36, 52, 72,  96, 108, 132, 160, 192, 224, 260, 288, 320, 360, 408, 448, 504, 546, 600, 644, 690, 750, 810,  870,  952, 1020, 1050, 1140, 1200, 1290, 1350, 1440, 1530, 1590, 1680, 1770, 1860, 1950, 2040},  // Quartile
		{-1, 17, 28, 44, 64, 88, 112, 130, 156, 192, 224, 264, 308, 352, 384, 432, 480, 532, 588, 650, 700, 750, 816, 900, 960, 1050, 1110, 1200, 1260, 1350, 1440, 1530, 1620, 1710, 1800, 1890, 1980, 2100, 2220, 2310, 2430},  // High
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
		// Constants declared in ascending order of error protection.
		LOW(1), MEDIUM(0), QUARTILE(3), HIGH(2);
		
		// In the range 0 to 3 (unsigned 2-bit integer).
		public final int formatBits;
		
		// Constructor.
		private Ecc(int fb) {
			formatBits = fb;
		}
	}
	
	
	
	/*---- Private helper class ----*/
	
	/**
	 * Computes the Reed-Solomon error correction codewords for a sequence of data codewords
	 * at a given degree. Objects are immutable, and the state only depends on the degree.
	 * This class exists because the divisor polynomial does not need to be recalculated for every input.
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
				root = (root << 1) ^ ((root >>> 7) * 0x11D);  // Multiply by 0x02 mod GF(2^8/0x11D)
			}
		}
		
		
		/*-- Method --*/
		
		/**
		 * Computes and returns the Reed-Solomon error correction codewords for the specified sequence of data codewords.
		 * The returned object is always a new byte array. This method does not alter this object's state (because it is immutable).
		 * @param data the sequence of data codewords
		 * @return the Reed-Solomon error correction codewords
		 * @throws NullPointerException if the data is {@code null}
		 */
		public byte[] getRemainder(byte[] data) {
			if (data == null)
				throw new NullPointerException();
			
			// Compute the remainder by performing polynomial division
			byte[] result = new byte[coefficients.length];
			for (byte b : data) {
				int factor = (b ^ result[0]) & 0xFF;
				System.arraycopy(result, 1, result, 0, result.length - 1);
				result[result.length - 1] = 0;
				for (int j = 0; j < result.length; j++)
					result[j] ^= multiply(coefficients[j] & 0xFF, factor);
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
