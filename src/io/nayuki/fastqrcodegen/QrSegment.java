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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


public final class QrSegment {
	
	/*---- Static factory functions ----*/
	
	public static QrSegment makeBytes(byte[] data) {
		Objects.requireNonNull(data);
		int[] bits = new int[(data.length + 3) / 4];
		for (int i = 0; i < data.length; i++)
			bits[i >>> 2] |= (data[i] & 0xFF) << (~i << 3);
		return new QrSegment(Mode.BYTE, data.length, bits, data.length * 8);
	}
	
	
	public static QrSegment makeNumeric(String digits) {
		Objects.requireNonNull(digits);
		BitBuffer bb = new BitBuffer();
		int accumData = 0;
		int accumCount = 0;
		for (int i = 0; i < digits.length(); i++) {
			char c = digits.charAt(i);
			if (c < '0' || c > '9')
				throw new IllegalArgumentException("String contains non-numeric characters");
			accumData = accumData * 10 + (c - '0');
			accumCount++;
			if (accumCount == 3) {
				bb.appendBits(accumData, 10);
				accumData = 0;
				accumCount = 0;
			}
		}
		if (accumCount > 0)  // 1 or 2 digits remaining
			bb.appendBits(accumData, accumCount * 3 + 1);
		return new QrSegment(Mode.NUMERIC, digits.length(), bb.data, bb.bitLength);
	}
	
	
	public static QrSegment makeAlphanumeric(String text) {
		Objects.requireNonNull(text);
		BitBuffer bb = new BitBuffer();
		int accumData = 0;
		int accumCount = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c >= ALPHANUMERIC_MAP.length || ALPHANUMERIC_MAP[c] == -1)
				throw new IllegalArgumentException("String contains unencodable characters in alphanumeric mode");
			accumData = accumData * 45 + ALPHANUMERIC_MAP[c];
			accumCount++;
			if (accumCount == 2) {
				bb.appendBits(accumData, 11);
				accumData = 0;
				accumCount = 0;
			}
		}
		if (accumCount > 0)  // 1 character remaining
			bb.appendBits(accumData, 6);
		return new QrSegment(Mode.ALPHANUMERIC, text.length(), bb.data, bb.bitLength);
	}
	
	
	public static List<QrSegment> makeSegments(String text) {
		Objects.requireNonNull(text);
		
		// Select the most efficient segment encoding automatically
		List<QrSegment> result = new ArrayList<>();
		if (text.equals(""));  // Leave result empty
		else if (isNumeric(text))
			result.add(makeNumeric(text));
		else if (isAlphanumeric(text))
			result.add(makeAlphanumeric(text));
		else
			result.add(makeBytes(text.getBytes(StandardCharsets.UTF_8)));
		return result;
	}
	
	
	public static QrSegment makeEci(int assignVal) {
		BitBuffer bb = new BitBuffer();
		if (0 <= assignVal && assignVal < (1 << 7))
			bb.appendBits(assignVal, 8);
		else if ((1 << 7) <= assignVal && assignVal < (1 << 14)) {
			bb.appendBits(2, 2);
			bb.appendBits(assignVal, 14);
		} else if ((1 << 14) <= assignVal && assignVal < 1000000) {
			bb.appendBits(6, 3);
			bb.appendBits(assignVal, 21);
		} else
			throw new IllegalArgumentException("ECI assignment value out of range");
		return new QrSegment(Mode.ECI, 0, bb.data, bb.bitLength);
	}
	
	
	public static boolean isNumeric(String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c < '0' || c > '9')
				return false;
		}
		return true;
	}
	
	
	public static boolean isAlphanumeric(String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c >= ALPHANUMERIC_MAP.length || ALPHANUMERIC_MAP[c] == -1)
				return false;
		}
		return true;
	}
	
	
	
	/*---- Instance fields ----*/
	
	public final Mode mode;
	
	public final int numChars;
	
	final int[] data;
	
	final int bitLength;
	
	
	/*---- Constructor ----*/
	
	public QrSegment(Mode md, int numCh, int[] data, int bitLen) {
		Objects.requireNonNull(md);
		Objects.requireNonNull(data);
		if (numCh < 0 || bitLen < 0 || bitLen > data.length * 32)
			throw new IllegalArgumentException("Invalid value");
		mode = md;
		numChars = numCh;
		this.data = data;
		bitLength = bitLen;
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
	
	private static final int[] ALPHANUMERIC_MAP;
	
	static {
		final String ALPHANUMERIC_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";
		int maxCh = -1;
		for (int i = 0; i < ALPHANUMERIC_CHARSET.length(); i++)
			maxCh = Math.max(ALPHANUMERIC_CHARSET.charAt(i), maxCh);
		ALPHANUMERIC_MAP = new int[maxCh + 1];
		Arrays.fill(ALPHANUMERIC_MAP, -1);
		for (int i = 0; i < ALPHANUMERIC_CHARSET.length(); i++)
			ALPHANUMERIC_MAP[ALPHANUMERIC_CHARSET.charAt(i)] = i;
	}
	
	
	
	/*---- Public helper enumeration ----*/
	
	public enum Mode {
		
		/*-- Constants --*/
		
		NUMERIC     (0x1, 10, 12, 14),
		ALPHANUMERIC(0x2,  9, 11, 13),
		BYTE        (0x4,  8, 16, 16),
		KANJI       (0x8,  8, 10, 12),
		ECI         (0x7,  0,  0,  0);
		
		
		/*-- Fields --*/
		
		final int modeBits;
		
		private final int[] numBitsCharCount;
		
		
		/*-- Constructor --*/
		
		private Mode(int mode, int... ccbits) {
			this.modeBits = mode;
			numBitsCharCount = ccbits;
		}
		
		
		/*-- Method --*/
		
		int numCharCountBits(int ver) {
			if      ( 1 <= ver && ver <=  9)  return numBitsCharCount[0];
			else if (10 <= ver && ver <= 26)  return numBitsCharCount[1];
			else if (27 <= ver && ver <= 40)  return numBitsCharCount[2];
			else  throw new IllegalArgumentException("Version number out of range");
		}
		
	}
	
}
