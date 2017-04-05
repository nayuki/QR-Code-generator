/* 
 * QR Code generator library - Optional advanced logic (Java)
 * 
 * Copyright (c) Project Nayuki
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

import io.nayuki.qrcodegen.QrSegment.Mode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


public final class QrSegmentAdvanced {

	/*---- Optimal list of segments encoder ----*/

    /**
     * Returns a new mutable list of zero or more segments to represent the specified Unicode text string.
     * The resulting list optimally minimizes the total encoded bit length, subjected to the constraints given
     * by the specified {error correction level, minimum version number, maximum version number}, plus the additional
     * constraint that the segment modes {NUMERIC, ALPHANUMERIC, BYTE} can be used but KANJI cannot be used.
     * <p>This function can be viewed as a significantly more sophisticated and slower replacement
     * for {@link QrSegment#makeSegments(String)}, but requiring more input parameters in a way
     * that overlaps with {@link QrCode#encodeSegments(List, QrCode.Ecc, int, int, int, boolean)}.</p>
     *
     * @param text       the text to be encoded, which can be any Unicode string
     * @param ecl        the error correction level to use
     * @param minVersion the minimum allowed version of the QR symbol (at least 1)
     * @param maxVersion the maximum allowed version of the QR symbol (at most 40)
     * @return a list of segments containing the text, minimizing the bit length with respect to the constraints
     * @throws NullPointerException     if the data or error correction level is {@code null}
     * @throws IllegalArgumentException if 1 &le; minVersion &le; maxVersion &le; 40 is violated,
     *                                  or if the data is too long to fit in a QR Code at maxVersion at the ECL
     */
    public static List<QrSegment> makeSegmentsOptimally(String text, QrCode.Ecc ecl, int minVersion, int maxVersion) {
        // Check arguments
        Objects.requireNonNull(text);
        Objects.requireNonNull(ecl);
        if (!(1 <= minVersion && minVersion <= maxVersion && maxVersion <= 40))
            throw new IllegalArgumentException("Invalid value");

        // Iterate through version numbers, and make tentative segments
        List<QrSegment> segs = null;
        for (int version = minVersion; version <= maxVersion; version++) {
            if (version == minVersion || version == 10 || version == 27)
                segs = makeSegmentsOptimally(text, version);

            // Check if the segments fit
            int dataCapacityBits = QrCode.getNumDataCodewords(version, ecl) * 8;
            int dataUsedBits = QrSegment.getTotalBits(segs, version);
            if (dataUsedBits != -1 && dataUsedBits <= dataCapacityBits)
                return segs;
        }
        throw new IllegalArgumentException("Data too long");
    }


    // Returns a list of segments that is optimal for the given text at the given version number.
    private static List<QrSegment> makeSegmentsOptimally(String text, int version) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int[][] bitCosts = computeBitCosts(data, version);
        Mode[] charModes = computeCharacterModes(data, version, bitCosts);
        return splitIntoSegments(data, charModes);
    }


    private static int[][] computeBitCosts(byte[] data, int version) {
        // Segment header sizes, measured in 1/6 bits
        int bytesCost = (4 + Mode.BYTE.numCharCountBits(version)) * 6;
        int alphnumCost = (4 + Mode.ALPHANUMERIC.numCharCountBits(version)) * 6;
        int numberCost = (4 + Mode.NUMERIC.numCharCountBits(version)) * 6;

        // result[mode][len] is the number of 1/6 bits to encode the first len characters of the text, ending in the mode
        int[][] result = new int[3][data.length + 1];
        Arrays.fill(result[1], Integer.MAX_VALUE / 2);
        Arrays.fill(result[2], Integer.MAX_VALUE / 2);
        result[0][0] = bytesCost;
        result[1][0] = alphnumCost;
        result[2][0] = numberCost;

        // Calculate the cost table using dynamic programming
        for (int i = 0; i < data.length; i++) {
            // Encode a character
            int j = i + 1;
            char c = (char) data[i];
            result[0][j] = result[0][i] + 48;  // 8 bits per byte
            if (isAlphanumeric(c))
                result[1][j] = result[1][i] + 33;  // 5.5 bits per alphanumeric char
            if (isNumeric(c))
                result[2][j] = result[2][i] + 20;  // 3.33 bits per digit

            // Switch modes, rounding up fractional bits
            result[0][j] = Math.min((Math.min(result[1][j], result[2][j]) + 5) / 6 * 6 + bytesCost, result[0][j]);
            result[1][j] = Math.min((Math.min(result[2][j], result[0][j]) + 5) / 6 * 6 + alphnumCost, result[1][j]);
            result[2][j] = Math.min((Math.min(result[0][j], result[1][j]) + 5) / 6 * 6 + numberCost, result[2][j]);
        }
        return result;
    }


    private static Mode[] computeCharacterModes(byte[] data, int version, int[][] bitCosts) {
        // Segment header sizes, measured in 1/6 bits
        int bytesCost = (4 + Mode.BYTE.numCharCountBits(version)) * 6;
        int alphnumCost = (4 + Mode.ALPHANUMERIC.numCharCountBits(version)) * 6;
        int numberCost = (4 + Mode.NUMERIC.numCharCountBits(version)) * 6;

        // Infer the mode used for last character by taking the minimum
        Mode curMode;
        int end = bitCosts[0].length - 1;
        if (bitCosts[0][end] <= Math.min(bitCosts[1][end], bitCosts[2][end]))
            curMode = Mode.BYTE;
        else if (bitCosts[1][end] <= bitCosts[2][end])
            curMode = Mode.ALPHANUMERIC;
        else
            curMode = Mode.NUMERIC;

        // Work backwards to calculate optimal encoding mode for each character
        Mode[] result = new Mode[data.length];
        if (data.length == 0)
            return result;
        result[data.length - 1] = curMode;
        for (int i = data.length - 2; i >= 0; i--) {
            char c = (char) data[i];
            if (curMode == Mode.NUMERIC) {
                if (isNumeric(c))
                    curMode = Mode.NUMERIC;
                else if (isAlphanumeric(c) && (bitCosts[1][i] + 33 + 5) / 6 * 6 + numberCost == bitCosts[2][i + 1])
                    curMode = Mode.ALPHANUMERIC;
                else
                    curMode = Mode.BYTE;
            } else if (curMode == Mode.ALPHANUMERIC) {
                if (isNumeric(c) && (bitCosts[2][i] + 20 + 5) / 6 * 6 + alphnumCost == bitCosts[1][i + 1])
                    curMode = Mode.NUMERIC;
                else if (isAlphanumeric(c))
                    curMode = Mode.ALPHANUMERIC;
                else
                    curMode = Mode.BYTE;
            } else if (curMode == Mode.BYTE) {
                if (isNumeric(c) && (bitCosts[2][i] + 20 + 5) / 6 * 6 + bytesCost == bitCosts[0][i + 1])
                    curMode = Mode.NUMERIC;
                else if (isAlphanumeric(c) && (bitCosts[1][i] + 33 + 5) / 6 * 6 + bytesCost == bitCosts[0][i + 1])
                    curMode = Mode.ALPHANUMERIC;
                else
                    curMode = Mode.BYTE;
            } else
                throw new AssertionError();
            result[i] = curMode;
        }
        return result;
    }


    private static List<QrSegment> splitIntoSegments(byte[] data, Mode[] charModes) {
        List<QrSegment> result = new ArrayList<>();
        if (data.length == 0)
            return result;

        // Accumulate run of modes
        Mode curMode = charModes[0];
        int start = 0;
        for (int i = 1; i < data.length; i++) {
            if (charModes[i] != curMode) {
                if (curMode == Mode.BYTE)
                    result.add(QrSegment.makeBytes(Arrays.copyOfRange(data, start, i)));
                else {
                    String temp = new String(data, start, i - start, StandardCharsets.US_ASCII);
                    if (curMode == Mode.NUMERIC)
                        result.add(QrSegment.makeNumeric(temp));
                    else if (curMode == Mode.ALPHANUMERIC)
                        result.add(QrSegment.makeAlphanumeric(temp));
                    else
                        throw new AssertionError();
                }
                curMode = charModes[i];
                start = i;
            }
        }

        // Final segment
        if (curMode == Mode.BYTE)
            result.add(QrSegment.makeBytes(Arrays.copyOfRange(data, start, data.length)));
        else {
            String temp = new String(data, start, data.length - start, StandardCharsets.US_ASCII);
            if (curMode == Mode.NUMERIC)
                result.add(QrSegment.makeNumeric(temp));
            else if (curMode == Mode.ALPHANUMERIC)
                result.add(QrSegment.makeAlphanumeric(temp));
            else
                throw new AssertionError();
        }
        return result;
    }


    private static boolean isAlphanumeric(char c) {
        return isNumeric(c) || 'A' <= c && c <= 'Z' || " $%*+./:-".indexOf(c) != -1;
    }

    private static boolean isNumeric(char c) {
        return '0' <= c && c <= '9';
    }

	
	/*---- Kanji mode segment encoder ----*/

    /**
     * Returns a segment representing the specified string encoded in kanji mode.
     * <p>Note that broadly speaking, the set of encodable characters are {kanji used in Japan, hiragana, katakana,
     * Asian punctuation, full-width ASCII}.<br/>
     * In particular, non-encodable characters are {normal ASCII, half-width katakana, more extensive Chinese hanzi}.
     *
     * @param text the text to be encoded, which must fall in the kanji mode subset of characters
     * @return a segment containing the data
     * @throws NullPointerException     if the string is {@code null}
     * @throws IllegalArgumentException if the string contains non-kanji-mode characters
     * @see #isEncodableAsKanji(String)
     */
    public static QrSegment makeKanjiSegment(String text) {
        Objects.requireNonNull(text);
        BitBuffer bb = new BitBuffer();
        for (int i = 0; i < text.length(); i++) {
            int val = UNICODE_TO_QR_KANJI[text.charAt(i)];
            if (val == -1)
                throw new IllegalArgumentException("String contains non-kanji-mode characters");
            bb.appendBits(val, 13);
        }
        return new QrSegment(Mode.KANJI, text.length(), bb.getBytes(), bb.bitLength());
    }


    /**
     * Tests whether the specified text string can be encoded as a segment in kanji mode.
     * <p>Note that broadly speaking, the set of encodable characters are {kanji used in Japan, hiragana, katakana,
     * Asian punctuation, full-width ASCII}.<br/>
     * In particular, non-encodable characters are {normal ASCII, half-width katakana, more extensive Chinese hanzi}.
     *
     * @param text the string to test for encodability
     * @return {@code true} if and only if the string can be encoded in kanji mode
     * @throws NullPointerException if the string is {@code null}
     * @see #makeKanjiSegment(String)
     */
    public static boolean isEncodableAsKanji(String text) {
        Objects.requireNonNull(text);
        for (int i = 0; i < text.length(); i++) {
            if (UNICODE_TO_QR_KANJI[text.charAt(i)] == -1)
                return false;
        }
        return true;
    }


    private static short[] UNICODE_TO_QR_KANJI = new short[65536];

    static {  // Unpack the Shift JIS table into a more computation-friendly form
        Arrays.fill(UNICODE_TO_QR_KANJI, (short) -1);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        InputStream is = null;
        try {
            is = QrSegmentAdvanced.class.getResourceAsStream("qr_kanji_to_unicode.bin");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                // Ignore
            }
        }
        final byte[] bytes = os.toByteArray();
        for (int i = 0; i < bytes.length; i += 2) {
            int j = ((bytes[i] & 0xFF) << 8) | (bytes[i + 1] & 0xFF);
            if (j == 0xFFFF)
                continue;
            if (UNICODE_TO_QR_KANJI[j] != -1)
                throw new AssertionError();
            UNICODE_TO_QR_KANJI[j] = (short) (i / 2);
        }
    }

}
