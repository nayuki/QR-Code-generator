/* 
 * QR Code generator library - Optional advanced logic (Java)
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

import io.nayuki.qrcodegen.QrSegment.Mode;


/**
 * Splits text into optimal segments and encodes kanji segments.
 * Provides static functions only; not instantiable.
 * @see QrSegment
 * @see QrCode
 */
public final class QrSegmentAdvanced {
	
	/*---- Optimal list of segments encoder ----*/
	
	/**
	 * Returns a list of zero or more segments to represent the specified Unicode text string.
	 * The resulting list optimally minimizes the total encoded bit length, subjected to the constraints
	 * in the specified {error correction level, minimum version number, maximum version number}.
	 * <p>This function can utilize all four text encoding modes: numeric, alphanumeric, byte (UTF-8),
	 * and kanji. This can be considered as a sophisticated but slower replacement for {@link
	 * QrSegment#makeSegments(String)}. This requires more input parameters because it searches a
	 * range of versions, like {@link QrCode#encodeSegments(List,QrCode.Ecc,int,int,int,boolean)}.</p>
	 * @param text the text to be encoded (not {@code null}), which can be any Unicode string
	 * @param ecl the error correction level to use (not {@code null})
	 * @param minVersion the minimum allowed version of the QR Code (at least 1)
	 * @param maxVersion the maximum allowed version of the QR Code (at most 40)
	 * @return a new mutable list (not {@code null}) of segments (not {@code null})
	 * containing the text, minimizing the bit length with respect to the constraints
	 * @throws NullPointerException if the text or error correction level is {@code null}
	 * @throws IllegalArgumentException if 1 &#x2264; minVersion &#x2264; maxVersion &#x2264; 40 is violated
	 * @throws DataTooLongException if the text fails to fit in the maxVersion QR Code at the ECL
	 */
	public static List<QrSegment> makeSegmentsOptimally(String text, QrCode.Ecc ecl, int minVersion, int maxVersion) {
		// Check arguments
		Objects.requireNonNull(text);
		Objects.requireNonNull(ecl);
		if (not_Valid_Version(minVersion, maxVersion))
			throw new IllegalArgumentException("Invalid value");
		
		// Iterate through version numbers, and make tentative segments
		List<QrSegment> segs = null;
		int[] codePoints = toCodePoints(text);
		for (int version = minVersion; ; version++) {
			if (is_valid_version(minVersion, version))
				segs = makeSegmentsOptimally(codePoints, version);
			assert segs != null;
			
			// Check if the segments fit
			int dataCapacityBits = QrCode.getNumDataCodewords(version, ecl) * 8;
			int dataUsedBits = QrSegment.getTotalBits(segs, version);
			if (dataUsedBits != -1 && dataUsedBits <= dataCapacityBits)
				return segs;  // This version number is found to be suitable
			if (version >= maxVersion) {  // All versions in the range could not fit the given text
				String msg = "Segment too long";
				if (dataUsedBits != -1)
					msg = String.format("Data length = %d bits, Max capacity = %d bits", dataUsedBits, dataCapacityBits);
				throw new DataTooLongException(msg);
			}
		}
	}


	private static boolean is_valid_version(int minVersion, int version) {
		return version == minVersion || version == 10 || version == 27;
	}


	private static boolean not_Valid_Version(int minVersion, int maxVersion) {
		return !(QrCode.MIN_VERSION <= minVersion && minVersion <= maxVersion && maxVersion <= QrCode.MAX_VERSION);
	}
	
	
	// Returns a new list of segments that is optimal for the given text at the given version number.
	private static List<QrSegment> makeSegmentsOptimally(int[] codePoints, int version) {
		if (codePoints.length == 0)
			return new ArrayList<>();
		QrMode[] charModes = computeCharacterModes(codePoints, version);
		return splitIntoSegments(codePoints, charModes);
	}
	
	// Returns a new array representing the optimal mode per code point based on the given text and version.
	private static QrMode[] computeCharacterModes(int[] codePoints, int version) {
		if (codePoints.length == 0)
			throw new IllegalArgumentException();
		final QrMode[] modeTypes = {new ByteMode(), new AlphanumericMode(), new NumericMode(), new KanjiMode()};  // Do not modify
		final int numModes = modeTypes.length;
		
		// Segment header sizes, measured in 1/6 bits
		final int[] headCosts = new int[numModes];
		for (int i = 0; i < numModes; i++)
			headCosts[i] = (4 + modeTypes[i].numCharCountBits(version)) * 6;
		
		// charModes[i][j] represents the mode to encode the code point at
		// index i such that the final segment ends in modeTypes[j] and the
		// total number of bits is minimized over all possible choices
		QrMode[][] charModes = new QrMode[codePoints.length][numModes];
		
		// At the beginning of each iteration of the loop below,
		// prevCosts[j] is the exact minimum number of 1/6 bits needed to
		// encode the entire string prefix of length i, and end in modeTypes[j]
		int[] prevCosts = headCosts.clone();
		
		// Calculate costs using dynamic programming
		prevCosts = calculate_cost(codePoints, modeTypes, numModes, headCosts, charModes, prevCosts);
		
		// Find optimal ending mode
		QrMode curMode = null; 
		curMode = find_optimal(modeTypes, numModes, prevCosts, curMode);
		
		// Get optimal mode for each code point by tracing backwards
		QrMode[] result = new QrMode[codePoints.length];
		get_optimal(modeTypes, numModes, charModes, curMode, result);
		return result;
	}


	private static int[] calculate_cost(int[] codePoints, final QrMode[] modeTypes, final int numModes,
			final int[] headCosts, QrMode[][] charModes, int[] prevCosts) {
		for (int i = 0; i < codePoints.length; i++) {
			int codePoint = codePoints[i];
			int[] curCosts = new int[numModes];
			{  // Always extend a byte mode segment
				charModes[i][0] = new ByteMode();
				curCosts[0] = charModes[i][0].getcost(prevCosts[0], codePoint);
			}
			// Extend a segment if possible
			if (QrSegment.ALPHANUMERIC_CHARSET.indexOf(codePoint) != -1) {  // Is alphanumeric
				charModes[i][1] = new AlphanumericMode();
				curCosts[1] = charModes[i][1].getcost(prevCosts[1], codePoint);  // 5.5 bits per alphanumeric char
			}
			if ('0' <= codePoint && codePoint <= '9') {  // Is numeric
				charModes[i][2] = new NumericMode();
				curCosts[2] = charModes[i][2].getcost(prevCosts[2], codePoint);  // 3.33 bits per digit
			}
			if (isKanji(codePoint)) {
				charModes[i][3] = new KanjiMode();
				curCosts[3] = charModes[i][3].getcost(prevCosts[3], codePoint);;  // 13 bits per Shift JIS char
			}

			// Start new segment at the end to switch modes
			for (int j = 0; j < numModes; j++) {  // To mode
				for (int k = 0; k < numModes; k++) {  // From mode
					int newCost = (curCosts[k] + 5) / 6 * 6 + headCosts[j];
					if (charModes[i][k] != null && (charModes[i][j] == null || newCost < curCosts[j])) {
						curCosts[j] = newCost;
						charModes[i][j] = modeTypes[k];
					}
				}
			}
			
			prevCosts = curCosts;
		}
		return prevCosts;
	}


	private static void get_optimal(final QrMode[] modeTypes, final int numModes, QrMode[][] charModes, QrMode curMode,
			QrMode[] result) {
		for (int i = result.length - 1; i >= 0; i--) {
			for (int j = 0; j < numModes; j++) {
				if (modeTypes[j] == curMode) {
					curMode = charModes[i][j];
					result[i] = curMode;
					break;
				}
			}
		}
	}


	private static QrMode find_optimal(final QrMode[] modeTypes, final int numModes, int[] prevCosts, QrMode curMode) {
		for (int i = 0, minCost = 0; i < numModes; i++) {
			if (curMode == null || prevCosts[i] < minCost) {
				minCost = prevCosts[i];
				curMode = modeTypes[i];
			}
		}
		return curMode;
	}


	private static boolean is_numeric(int convertedPoint) {
		return '0' <= convertedPoint && convertedPoint <= '9';
	}


	private static boolean is_alphanumeric(int convertedPoint) {
		return QrSegment.ALPHANUMERIC_CHARSET.indexOf(convertedPoint) != -1;
	}
	
	
	// Returns a new list of segments based on the given text and modes, such that
	// consecutive code points in the same mode are put into the same segment.
	private static List<QrSegment> splitIntoSegments(int[] codePoints, QrMode[] charModes) {
		if (codePoints.length == 0)
			throw new IllegalArgumentException();
		List<QrSegment> result = new ArrayList<>();
		
		// Accumulate run of modes
		QrMode curMode = charModes[0];
		int start = 0;
		for (int i = 1; ; i++) {
			if (i < codePoints.length && charModes[i] == curMode)
				continue;
			String s = new String(codePoints, start, i - start);
			result.add(curMode.making(s));
			if (i >= codePoints.length)
				return result;
			curMode = charModes[i];
			start = i;
		}
	}
	
	
	// Returns a new array of Unicode code points (effectively
	// UTF-32 / UCS-4) representing the given UTF-16 string.
	private static int[] toCodePoints(String str) {
		int[] result = str.codePoints().toArray();
		for (int c : result) {
			if (Character.isSurrogate((char)c))
				throw new IllegalArgumentException("Invalid UTF-16 string");
		}
		return result;
	}
	
	
	// Returns the number of UTF-8 bytes needed to encode the given Unicode code point.
	public static int countUtf8Bytes(int currentPoint) {
		if      (currentPoint <        0) throw new IllegalArgumentException("Invalid code point");
		else if (currentPoint <     0x80) return 1;
		else if (currentPoint <    0x800) return 2;
		else if (currentPoint <  0x10000) return 3;
		else if (currentPoint < 0x110000) return 4;
		else                    throw new IllegalArgumentException("Invalid code point");
	}
	
	
	
	/*---- Kanji mode segment encoder ----*/
	
	/**
	 * Returns a segment representing the specified text string encoded in kanji mode.
	 * Broadly speaking, the set of encodable characters are {kanji used in Japan,
	 * hiragana, katakana, East Asian punctuation, full-width ASCII, Greek, Cyrillic}.
	 * Examples of non-encodable characters include {ordinary ASCII, half-width katakana,
	 * more extensive Chinese hanzi}.
	 * @param text the text (not {@code null}), with only certain characters allowed
	 * @return a segment (not {@code null}) containing the text
	 * @throws NullPointerException if the string is {@code null}
	 * @throws IllegalArgumentException if the string contains non-encodable characters
	 * @see #isEncodableAsKanji(String)
	 */
	public static QrSegment makeKanji(String text) {
		Objects.requireNonNull(text);
		BitBuffer bb = new BitBuffer();
		text.chars().forEachOrdered(c -> {
			int val = UNICODE_TO_QR_KANJI[c];
			if (val == -1)
				throw new IllegalArgumentException("String contains non-kanji-mode characters");
			bb.appendBits(val, 13);
		});
		return new QrSegment(Mode.KANJI, text.length(), bb);
	}
	
	
	/**
	 * Tests whether the specified string can be encoded as a segment in kanji mode.
	 * Broadly speaking, the set of encodable characters are {kanji used in Japan,
	 * hiragana, katakana, East Asian punctuation, full-width ASCII, Greek, Cyrillic}.
	 * Examples of non-encodable characters include {ordinary ASCII, half-width katakana,
	 * more extensive Chinese hanzi}.
	 * @param text the string to test for encodability (not {@code null})
	 * @return {@code true} iff each character is in the kanji mode character set
	 * @throws NullPointerException if the string is {@code null}
	 * @see #makeKanji(String)
	 */
	public static boolean isEncodableAsKanji(String text) {
		Objects.requireNonNull(text);
		return text.chars().allMatch(c -> isKanji((char)c));
	}
	
	
	private static boolean isKanji(int c) {
		return c < UNICODE_TO_QR_KANJI.length && UNICODE_TO_QR_KANJI[c] != -1;
	}
	
//	Data derived from ftp://ftp.unicode.org/Public/MAPPINGS/OBSOLETE/EASTASIA/JIS/SHIFTJIS.TXT
	private static final String PACKED_QR_KANJI_TO_UNICODE = readPacked_KANJI();
	
	
	private static short[] UNICODE_TO_QR_KANJI = new short[1 << 16];
	
	static {  // Unpack the Shift JIS table into a more computation-friendly form
		Arrays.fill(UNICODE_TO_QR_KANJI, (short)-1);
		byte[] bytes = Base64.getDecoder().decode(PACKED_QR_KANJI_TO_UNICODE);
		for (int i = 0; i < bytes.length; i += 2) {
			char convertChar = (char)(((bytes[i] & 0xFF) << 8) | (bytes[i + 1] & 0xFF));
			if (convertChar == 0xFFFF)
				continue;
			assert UNICODE_TO_QR_KANJI[convertChar] == -1;
			UNICODE_TO_QR_KANJI[convertChar] = (short)(i / 2);
		}
	}
	
	private static String readPacked_KANJI() {
		String str = "";
		try {
			File file = new File("./packet_KanJI.txt");
			Scanner scan = new Scanner(file);
			str +=scan.nextLine();
			scan.close();
		}
		catch(FileNotFoundException e){
		}
		
		return str;
	}
	
	
	/*---- Miscellaneous ----*/
	
	private QrSegmentAdvanced() {}  // Not instantiable
	
}
