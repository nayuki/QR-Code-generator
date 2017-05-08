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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;


/**
 * Represents a character string to be encoded in a QR Code symbol. Each segment has
 * a mode, and a sequence of characters that is already encoded as a sequence of bits.
 * Instances of this class are immutable.
 * <p>This segment class imposes no length restrictions, but QR Codes have restrictions.
 * Even in the most favorable conditions, a QR Code can only hold 7089 characters of data.
 * Any segment longer than this is meaningless for the purpose of generating QR Codes.</p>
 */
public final class QrSegment {
	
	/*---- Static factory functions ----*/
	
	/**
	 * Returns a segment representing the specified binary data encoded in byte mode.
	 * @param data the binary data
	 * @return a segment containing the data
	 * @throws NullPointerException if the array is {@code null}
	 */
	public static QrSegment makeBytes(byte[] data) {
		Objects.requireNonNull(data);
		return new QrSegment(Mode.BYTE, data.length, data, data.length * 8);
	}
	
	
	/**
	 * Returns a segment representing the specified string of decimal digits encoded in numeric mode.
	 * @param digits a string consisting of digits from 0 to 9
	 * @return a segment containing the data
	 * @throws NullPointerException if the string is {@code null}
	 * @throws IllegalArgumentException if the string contains non-digit characters
	 */
	public static QrSegment makeNumeric(String digits) {
		Objects.requireNonNull(digits);
		if (!NUMERIC_REGEX.matcher(digits).matches())
			throw new IllegalArgumentException("String contains non-numeric characters");
		
		BitBuffer bb = new BitBuffer();
		int i;
		for (i = 0; i + 3 <= digits.length(); i += 3)  // Process groups of 3
			bb.appendBits(Integer.parseInt(digits.substring(i, i + 3)), 10);
		int rem = digits.length() - i;
		if (rem > 0)  // 1 or 2 digits remaining
			bb.appendBits(Integer.parseInt(digits.substring(i)), rem * 3 + 1);
		return new QrSegment(Mode.NUMERIC, digits.length(), bb.getBytes(), bb.bitLength());
	}
	
	
	/**
	 * Returns a segment representing the specified text string encoded in alphanumeric mode. The characters allowed are:
	 * 0 to 9, A to Z (uppercase only), space, dollar, percent, asterisk, plus, hyphen, period, slash, colon.
	 * @param text a string of text, with only certain characters allowed
	 * @return a segment containing the data
	 * @throws NullPointerException if the string is {@code null}
	 * @throws IllegalArgumentException if the string contains non-encodable characters
	 */
	public static QrSegment makeAlphanumeric(String text) {
		Objects.requireNonNull(text);
		if (!ALPHANUMERIC_REGEX.matcher(text).matches())
			throw new IllegalArgumentException("String contains unencodable characters in alphanumeric mode");
		
		BitBuffer bb = new BitBuffer();
		int i;
		for (i = 0; i + 2 <= text.length(); i += 2) {  // Process groups of 2
			int temp = ALPHANUMERIC_CHARSET.indexOf(text.charAt(i)) * 45;
			temp += ALPHANUMERIC_CHARSET.indexOf(text.charAt(i + 1));
			bb.appendBits(temp, 11);
		}
		if (i < text.length())  // 1 character remaining
			bb.appendBits(ALPHANUMERIC_CHARSET.indexOf(text.charAt(i)), 6);
		return new QrSegment(Mode.ALPHANUMERIC, text.length(), bb.getBytes(), bb.bitLength());
	}
	
	
	/**
	 * Returns a new mutable list of zero or more segments to represent the specified Unicode text string.
	 * The result may use various segment modes and switch modes to optimize the length of the bit stream.
	 * @param text the text to be encoded, which can be any Unicode string
	 * @return a list of segments containing the text
	 * @throws NullPointerException if the text is {@code null}
	 */
	public static List<QrSegment> makeSegments(String text) {
		Objects.requireNonNull(text);
		
		// Select the most efficient segment encoding automatically
		List<QrSegment> result = new ArrayList<>();
		if (text.equals(""))
			return result;
		else if (NUMERIC_REGEX.matcher(text).matches())
			result.add(makeNumeric(text));
		else if (ALPHANUMERIC_REGEX.matcher(text).matches())
			result.add(makeAlphanumeric(text));
		else
			result.add(makeBytes(text.getBytes(StandardCharsets.UTF_8)));
		return result;
	}
	
	
	/**
	 * Returns a segment representing an Extended Channel Interpretation (ECI) designator with the specified assignment value.
	 * @param assignVal the ECI assignment number (see the AIM ECI specification)
	 * @return a segment containing the data
	 * @throws IllegalArgumentException if the value is outside the range [0, 10<sup>6</sup>)
	 */
	public static QrSegment makeEci(int assignVal) {
		byte[] data;
		if (0 <= assignVal && assignVal < (1 << 7))
			data = new byte[]{(byte)assignVal};
		else if ((1 << 7) <= assignVal && assignVal < (1 << 14))
			data = new byte[]{(byte)(0x80 | (assignVal >>> 8)), (byte)assignVal};
		else if ((1 << 14) <= assignVal && assignVal < 999999)
			data = new byte[]{(byte)(0xC0 | (assignVal >>> 16)), (byte)(assignVal >>> 8), (byte)assignVal};
		else
			throw new IllegalArgumentException("ECI assignment value out of range");
		return new QrSegment(Mode.ECI, 0, data, data.length * 8);
	}
	
	
	
	/*---- Instance fields ----*/
	
	/** The mode indicator for this segment. Never {@code null}. */
	public final Mode mode;
	
	/** The length of this segment's unencoded data, measured in characters. Always zero or positive. */
	public final int numChars;
	
	/** The bits of this segment packed into a byte array in big endian. Accessed through {@link getByte(int)}. Not {@code null}. */
	private final byte[] data;
	
	/** The length of this segment's encoded data, measured in bits. Satisfies 0 &le; {@code bitLength} &le; {@code data.length} &times; 8. */
	public final int bitLength;
	
	
	/*---- Constructor ----*/
	
	/**
	 * Creates a new QR Code data segment with the specified parameters and data.
	 * @param md the mode, which is not {@code null}
	 * @param numCh the data length in characters, which is non-negative
	 * @param bitLen the data length in bits, which is non-negative
	 * @param b the bits packed into bytes, which is not {@code null}
	 * @throws NullPointerException if the mode or array is {@code null}
	 * @throws IllegalArgumentException if the character count or bit length are negative or invalid
	 */
	public QrSegment(Mode md, int numCh, byte[] b, int bitLen) {
		Objects.requireNonNull(md);
		Objects.requireNonNull(b);
		if (numCh < 0 || bitLen < 0 || bitLen > b.length * 8L)
			throw new IllegalArgumentException("Invalid value");
		mode = md;
		numChars = numCh;
		data = Arrays.copyOf(b, (bitLen + 7) / 8);  // Trim to precise length and also make defensive copy
		bitLength = bitLen;
	}
	
	
	/*---- Method ----*/
	
	/**
	 * Returns the data byte at the specified index.
	 * @param index the index to retrieve from, satisfying 0 &le; {@code index} &lt; ceil({@code bitLength} &divide; 8)
	 * @return the data byte at the specified index
	 * @throws IndexOutOfBoundsException if the index is out of bounds
	 */
	public byte getByte(int index) {
		if (index < 0 || index > data.length)
			throw new IndexOutOfBoundsException();
		return data[index];
	}
	
	
	// Package-private helper function.
	static int getTotalBits(List<QrSegment> segs, int version) {
		Objects.requireNonNull(segs);
		if (version < 1 || version > 40)
			throw new IllegalArgumentException("Version number out of range");
		
		long result = 0;
		for (QrSegment seg : segs) {
			Objects.requireNonNull(seg);
			int ccbits = seg.mode.numCharCountBits(version);
			// Fail if segment length value doesn't fit in the length field's bit-width
			if (seg.numChars >= (1 << ccbits))
				return -1;
			result += 4L + ccbits + seg.bitLength;
			if (result > Integer.MAX_VALUE)
				return -1;
		}
		return (int)result;
	}
	
	
	/*---- Constants ----*/
	
	/** Can test whether a string is encodable in numeric mode (such as by using {@link #makeNumeric(String)}). */
	public static final Pattern NUMERIC_REGEX = Pattern.compile("[0-9]*");
	
	/** Can test whether a string is encodable in alphanumeric mode (such as by using {@link #makeAlphanumeric(String)}). */
	public static final Pattern ALPHANUMERIC_REGEX = Pattern.compile("[A-Z0-9 $%*+./:-]*");
	
	/** The set of all legal characters in alphanumeric mode, where each character value maps to the index in the string. */
	private static final String ALPHANUMERIC_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";
	
	
	
	/*---- Public helper enumeration ----*/
	
	/**
	 * The mode field of a segment. Immutable. Provides methods to retrieve closely related values.
	 */
	public enum Mode {
		
		/*-- Constants --*/
		
		NUMERIC     (0x1, 10, 12, 14),
		ALPHANUMERIC(0x2,  9, 11, 13),
		BYTE        (0x4,  8, 16, 16),
		KANJI       (0x8,  8, 10, 12),
		ECI         (0x7,  0,  0,  0);
		
		
		/*-- Fields --*/
		
		/** An unsigned 4-bit integer value (range 0 to 15) representing the mode indicator bits for this mode object. */
		final int modeBits;
		
		private final int[] numBitsCharCount;
		
		
		/*-- Constructor --*/
		
		private Mode(int mode, int... ccbits) {
			this.modeBits = mode;
			numBitsCharCount = ccbits;
		}
		
		
		/*-- Method --*/
		
		/**
		 * Returns the bit width of the segment character count field for this mode object at the specified version number.
		 * @param ver the version number, which is between 1 to 40, inclusive
		 * @return the number of bits for the character count, which is between 8 to 16, inclusive
		 * @throws IllegalArgumentException if the version number is out of range
		 */
		int numCharCountBits(int ver) {
			if      ( 1 <= ver && ver <=  9)  return numBitsCharCount[0];
			else if (10 <= ver && ver <= 26)  return numBitsCharCount[1];
			else if (27 <= ver && ver <= 40)  return numBitsCharCount[2];
			else  throw new IllegalArgumentException("Version number out of range");
		}
		
	}
	
}
