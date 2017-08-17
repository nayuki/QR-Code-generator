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

import static io.nayuki.qrcodegen.QrSegment.Mode.ALPHANUMERIC;
import static io.nayuki.qrcodegen.QrSegment.Mode.BYTE;
import static io.nayuki.qrcodegen.QrSegment.Mode.NUMERIC;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
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
	 * that overlaps with {@link QrCode#encodeSegments(List,QrCode.Ecc,int,int,int,boolean)}.</p>
	 * @param text the text to be encoded, which can be any Unicode string
	 * @param ecl the error correction level to use
	 * @param minVersion the minimum allowed version of the QR symbol (at least 1)
	 * @param maxVersion the maximum allowed version of the QR symbol (at most 40)
	 * @return a list of segments containing the text, minimizing the bit length with respect to the constraints
	 * @throws NullPointerException if the data or error correction level is {@code null}
	 * @throws IllegalArgumentException if 1 &le; minVersion &le; maxVersion &le; 40 is violated,
	 * or if the data is too long to fit in a QR Code at maxVersion at the ECL
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
		QrSegment.Mode[] charModes = computeCharacterModes(data, version, bitCosts);
		return splitIntoSegments(data, charModes);
	}
	
	
	private static int[][] computeBitCosts(byte[] data, int version) {
		// Segment header sizes, measured in 1/6 bits
		int bytesCost   = (4 + BYTE        .numCharCountBits(version)) * 6;
		int alphnumCost = (4 + ALPHANUMERIC.numCharCountBits(version)) * 6;
		int numberCost  = (4 + NUMERIC     .numCharCountBits(version)) * 6;
		
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
			char c = (char)data[i];
			result[0][j] = result[0][i] + 48;  // 8 bits per byte
			if (isAlphanumeric(c))
				result[1][j] = result[1][i] + 33;  // 5.5 bits per alphanumeric char
			if (isNumeric(c))
				result[2][j] = result[2][i] + 20;  // 3.33 bits per digit
			
			// Switch modes, rounding up fractional bits
			result[0][j] = Math.min((Math.min(result[1][j], result[2][j]) + 5) / 6 * 6 + bytesCost  , result[0][j]);
			result[1][j] = Math.min((Math.min(result[2][j], result[0][j]) + 5) / 6 * 6 + alphnumCost, result[1][j]);
			result[2][j] = Math.min((Math.min(result[0][j], result[1][j]) + 5) / 6 * 6 + numberCost , result[2][j]);
		}
		return result;
	}
	
	
	private static QrSegment.Mode[] computeCharacterModes(byte[] data, int version, int[][] bitCosts) {
		// Segment header sizes, measured in 1/6 bits
		int bytesCost   = (4 + BYTE        .numCharCountBits(version)) * 6;
		int alphnumCost = (4 + ALPHANUMERIC.numCharCountBits(version)) * 6;
		int numberCost  = (4 + NUMERIC     .numCharCountBits(version)) * 6;
		
		// Infer the mode used for last character by taking the minimum
		QrSegment.Mode curMode;
		int end = bitCosts[0].length - 1;
		if (bitCosts[0][end] <= Math.min(bitCosts[1][end], bitCosts[2][end]))
			curMode = BYTE;
		else if (bitCosts[1][end] <= bitCosts[2][end])
			curMode = ALPHANUMERIC;
		else
			curMode = NUMERIC;
		
		// Work backwards to calculate optimal encoding mode for each character
		QrSegment.Mode[] result = new QrSegment.Mode[data.length];
		if (data.length == 0)
			return result;
		result[data.length - 1] = curMode;
		for (int i = data.length - 2; i >= 0; i--) {
			char c = (char)data[i];
			if (curMode == NUMERIC) {
				if (isNumeric(c))
					curMode = NUMERIC;
				else if (isAlphanumeric(c) && (bitCosts[1][i] + 33 + 5) / 6 * 6 + numberCost == bitCosts[2][i + 1])
					curMode = ALPHANUMERIC;
				else
					curMode = BYTE;
			} else if (curMode == ALPHANUMERIC) {
				if (isNumeric(c) && (bitCosts[2][i] + 20 + 5) / 6 * 6 + alphnumCost == bitCosts[1][i + 1])
					curMode = NUMERIC;
				else if (isAlphanumeric(c))
					curMode = ALPHANUMERIC;
				else
					curMode = BYTE;
			} else if (curMode == BYTE) {
				if (isNumeric(c) && (bitCosts[2][i] + 20 + 5) / 6 * 6 + bytesCost == bitCosts[0][i + 1])
					curMode = NUMERIC;
				else if (isAlphanumeric(c) && (bitCosts[1][i] + 33 + 5) / 6 * 6 + bytesCost == bitCosts[0][i + 1])
					curMode = ALPHANUMERIC;
				else
					curMode = BYTE;
			} else
				throw new AssertionError();
			result[i] = curMode;
		}
		return result;
	}
	
	
	private static List<QrSegment> splitIntoSegments(byte[] data, QrSegment.Mode[] charModes) {
		List<QrSegment> result = new ArrayList<>();
		if (data.length == 0)
			return result;
		
		// Accumulate run of modes
		QrSegment.Mode curMode = charModes[0];
		int start = 0;
		for (int i = 1; i < data.length; i++) {
			if (charModes[i] != curMode) {
				if (curMode == BYTE)
					result.add(QrSegment.makeBytes(Arrays.copyOfRange(data, start, i)));
				else {
					String temp = new String(data, start, i - start, StandardCharsets.US_ASCII);
					if (curMode == NUMERIC)
						result.add(QrSegment.makeNumeric(temp));
					else if (curMode == ALPHANUMERIC)
						result.add(QrSegment.makeAlphanumeric(temp));
					else
						throw new AssertionError();
				}
				curMode = charModes[i];
				start = i;
			}
		}
		
		// Final segment
		if (curMode == BYTE)
			result.add(QrSegment.makeBytes(Arrays.copyOfRange(data, start, data.length)));
		else {
			String temp = new String(data, start, data.length - start, StandardCharsets.US_ASCII);
			if (curMode == NUMERIC)
				result.add(QrSegment.makeNumeric(temp));
			else if (curMode == ALPHANUMERIC)
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
	 * @param text the text to be encoded, which must fall in the kanji mode subset of characters
	 * @return a segment containing the data
	 * @throws NullPointerException if the string is {@code null}
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
		return new QrSegment(QrSegment.Mode.KANJI, text.length(), bb);
	}
	
	
	/**
	 * Tests whether the specified text string can be encoded as a segment in kanji mode.
	 * <p>Note that broadly speaking, the set of encodable characters are {kanji used in Japan, hiragana, katakana,
	 * Asian punctuation, full-width ASCII}.<br/>
	 * In particular, non-encodable characters are {normal ASCII, half-width katakana, more extensive Chinese hanzi}.
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
	
	
	// Data derived from ftp://ftp.unicode.org/Public/MAPPINGS/OBSOLETE/EASTASIA/JIS/SHIFTJIS.TXT
	private static final String PACKED_QR_KANJI_TO_UNICODE =
		"MAAwATAC/wz/DjD7/xr/G/8f/wEwmzCcALT/QACo/z7/4/8/MP0w/jCdMJ4wA07dMAUwBjAHMPwgFSAQ/w8AXDAcIBb/XCAmICUgGCAZIBwgHf8I/wkwFDAV/zv/Pf9b/10wCDAJMAowCzAMMA0wDjAPMBAwEf8LIhIAsQDX//8A9/8dImD/HP8eImYiZyIeIjQmQiZA" +
		"ALAgMiAzIQP/5f8EAKIAo/8F/wP/Bv8K/yAApyYGJgUlyyXPJc4lxyXGJaEloCWzJbIlvSW8IDswEiGSIZAhkSGTMBP/////////////////////////////IggiCyKGIocigiKDIioiKf////////////////////8iJyIoAKwh0iHUIgAiA///////////////////" +
		"//////////8iICKlIxIiAiIHImEiUiJqImsiGiI9Ih0iNSIrIiz//////////////////yErIDAmbyZtJmogICAhALb//////////yXv/////////////////////////////////////////////////xD/Ef8S/xP/FP8V/xb/F/8Y/xn///////////////////8h" +
		"/yL/I/8k/yX/Jv8n/yj/Kf8q/yv/LP8t/y7/L/8w/zH/Mv8z/zT/Nf82/zf/OP85/zr///////////////////9B/0L/Q/9E/0X/Rv9H/0j/Sf9K/0v/TP9N/07/T/9Q/1H/Uv9T/1T/Vf9W/1f/WP9Z/1r//////////zBBMEIwQzBEMEUwRjBHMEgwSTBKMEswTDBN" +
		"ME4wTzBQMFEwUjBTMFQwVTBWMFcwWDBZMFowWzBcMF0wXjBfMGAwYTBiMGMwZDBlMGYwZzBoMGkwajBrMGwwbTBuMG8wcDBxMHIwczB0MHUwdjB3MHgweTB6MHswfDB9MH4wfzCAMIEwgjCDMIQwhTCGMIcwiDCJMIowizCMMI0wjjCPMJAwkTCSMJP/////////////" +
		"////////////////////////MKEwojCjMKQwpTCmMKcwqDCpMKowqzCsMK0wrjCvMLAwsTCyMLMwtDC1MLYwtzC4MLkwujC7MLwwvTC+ML8wwDDBMMIwwzDEMMUwxjDHMMgwyTDKMMswzDDNMM4wzzDQMNEw0jDTMNQw1TDWMNcw2DDZMNow2zDcMN0w3jDf//8w4DDh" +
		"MOIw4zDkMOUw5jDnMOgw6TDqMOsw7DDtMO4w7zDwMPEw8jDzMPQw9TD2/////////////////////wORA5IDkwOUA5UDlgOXA5gDmQOaA5sDnAOdA54DnwOgA6EDowOkA6UDpgOnA6gDqf////////////////////8DsQOyA7MDtAO1A7YDtwO4A7kDugO7A7wDvQO+" +
		"A78DwAPBA8MDxAPFA8YDxwPIA8n/////////////////////////////////////////////////////////////////////////////////////////////////////////////BBAEEQQSBBMEFAQVBAEEFgQXBBgEGQQaBBsEHAQdBB4EHwQgBCEEIgQjBCQEJQQm" +
		"BCcEKAQpBCoEKwQsBC0ELgQv////////////////////////////////////////BDAEMQQyBDMENAQ1BFEENgQ3BDgEOQQ6BDsEPAQ9//8EPgQ/BEAEQQRCBEMERARFBEYERwRIBEkESgRLBEwETQROBE///////////////////////////////////yUAJQIlDCUQ" +
		"JRglFCUcJSwlJCU0JTwlASUDJQ8lEyUbJRclIyUzJSslOyVLJSAlLyUoJTclPyUdJTAlJSU4JUL/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////" +
		"////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////" +
		"////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////" +
		"////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////" +
		"////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////" +
		"////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////" +
		"////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////" +
		"////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////" +
		"////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////" +
		"////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////" +
		"/////////////////////////////////////06cVRZaA5Y/VMBhG2MoWfaQIoR1gxx6UGCqY+FuJWXthGaCppv1aJNXJ2WhYnFbm1nQhnuY9H1ifb6bjmIWfJ+It1uJXrVjCWaXaEiVx5eNZ09O5U8KT01PnVBJVvJZN1nUWgFcCWDfYQ9hcGYTaQVwunVPdXB5+32t" +
		"fe+Aw4QOiGOLApBVkHpTO06VTqVX34CykMF4704AWPFuopA4ejKDKIKLnC9RQVNwVL1U4VbgWftfFZjybeuA5IUt////////lmKWcJagl/tUC1PzW4dwz3+9j8KW6FNvnVx6uk4ReJOB/G4mVhhVBGsdhRqcO1nlU6ltZnTclY9WQk6RkEuW8oNPmQxT4VW2WzBfcWYg" +
		"ZvNoBGw4bPNtKXRbdsh6Tpg0gvGIW4pgku1tsnWrdsqZxWCmiwGNipWyaY5TrVGG//9XElgwWURbtF72YChjqWP0bL9vFHCOcRRxWXHVcz9+AYJ2gtGFl5BgkludG1hpZbxsWnUlUflZLlllX4Bf3GK8ZfpqKmsna7Rzi3/BiVadLJ0OnsRcoWyWg3tRBFxLYbaBxmh2" +
		"cmFOWU/6U3hgaW4pek+X804LUxZO7k9VTz1PoU9zUqBT71YJWQ9awVu2W+F50WaHZ5xntmtMbLNwa3PCeY15vno8e4eCsYLbgwSDd4Pvg9OHZoqyVimMqI/mkE6XHoaKT8Rc6GIRcll1O4Hlgr2G/ozAlsWZE5nVTstPGonjVt5YSljKXvtf62AqYJRgYmHQYhJi0GU5" +
		"////////m0FmZmiwbXdwcHVMdoZ9dYKlh/mVi5aOjJ1R8VK+WRZUs1uzXRZhaGmCba94jYTLiFeKcpOnmrhtbJmohtlXo2f/hs6SDlKDVodUBF7TYuFkuWg8aDhru3NyeLp6a4maidKNa48DkO2Vo5aUl2lbZlyzaX2YTZhOY5t7IGor//9qf2i2nA1vX1JyVZ1gcGLs" +
		"bTtuB27RhFuJEI9EThScOVP2aRtqOpeEaCpRXHrDhLKR3JOMVludKGgigwWEMXylUgiCxXTmTn5Pg1GgW9JSClLYUudd+1WaWCpZ5luMW5hb215yXnlgo2EfYWNhvmPbZWJn0WhTaPprPmtTbFdvIm+Xb0V0sHUYduN3C3r/e6F8IX3pfzZ/8ICdgmaDnomzisyMq5CE" +
		"lFGVk5WRlaKWZZfTmSiCGE44VCtcuF3Mc6l2THc8XKl/640LlsGYEZhUmFhPAU8OU3FVnFZoV/pZR1sJW8RckF4MXn5fzGPuZzpl12XiZx9oy2jE////////al9eMGvFbBdsfXV/eUhbY3oAfQBfvYmPihiMtI13jsyPHZjimg6bPE6AUH1RAFmTW5xiL2KAZOxrOnKg" +
		"dZF5R3+ph/uKvItwY6yDypegVAlUA1WraFRqWIpweCdndZ7NU3RbooEahlCQBk4YTkVOx08RU8pUOFuuXxNgJWVR//9nPWxCbHJs43B4dAN6dnquewh9Gnz+fWZl53JbU7tcRV3oYtJi4GMZbiCGWooxjd2S+G8BeaabWk6oTqtOrE+bT6BQ0VFHevZRcVH2U1RTIVN/" +
		"U+tVrFiDXOFfN19KYC9gUGBtYx9lWWpLbMFywnLtd++A+IEFggiFTpD3k+GX/5lXmlpO8FHdXC1mgWltXEBm8ml1c4loUHyBUMVS5FdHXf6TJmWkayNrPXQ0eYF5vXtLfcqCuYPMiH+JX4s5j9GR0VQfkoBOXVA2U+VTOnLXc5Z36YLmjq+ZxpnImdJRd2Eahl5VsHp6" +
		"UHZb05BHloVOMmrbkedcUVxI////////Y5h6n2yTl3SPYXqqcYqWiHyCaBd+cGhRk2xS8lQbhauKE3+kjs2Q4VNmiIh5QU/CUL5SEVFEVVNXLXPqV4tZUV9iX4RgdWF2YWdhqWOyZDplbGZvaEJuE3Vmej18+31MfZl+S39rgw6DSobNigiKY4tmjv2YGp2PgriPzpvo" +
		"//9Sh2IfZINvwJaZaEFQkWsgbHpvVHp0fVCIQIojZwhO9lA5UCZQZVF8UjhSY1WnVw9YBVrMXvphsmH4YvNjcmkcailyfXKscy54FHhvfXl3DICpiYuLGYzijtKQY5N1lnqYVZoTnnhRQ1OfU7Nee18mbhtukHOEc/59Q4I3igCK+pZQTk5QC1PkVHxW+lnRW2Rd8V6r" +
		"XydiOGVFZ69uVnLQfMqItIChgOGD8IZOioeN6JI3lseYZ58TTpROkk8NU0hUSVQ+Wi9fjF+hYJ9op2qOdFp4gYqeiqSLd5GQTl6byU6kT3xPr1AZUBZRSVFsUp9SuVL+U5pT41QR////////VA5ViVdRV6JZfVtUW11bj13lXedd9154XoNeml63XxhgUmFMYpdi2GOn" +
		"ZTtmAmZDZvRnbWghaJdpy2xfbSptaW4vbp11MnaHeGx6P3zgfQV9GH1efbGAFYADgK+AsYFUgY+CKoNSiEyIYYsbjKKM/JDKkXWScXg/kvyVpJZN//+YBZmZmtidO1JbUqtT91QIWNVi92/gjGqPX565UUtSO1RKVv16QJF3nWCe0nNEbwmBcHURX/1g2pqoctuPvGtk" +
		"mANOylbwV2RYvlpaYGhhx2YPZgZoOWixbfd11X06gm6bQk6bT1BTyVUGXW9d5l3uZ/tsmXRzeAKKUJOWiN9XUF6nYytQtVCsUY1nAFTJWF5Zu1uwX2liTWOhaD1rc24IcH2Rx3KAeBV4JnltZY59MIPciMGPCZabUmRXKGdQf2qMoVG0V0KWKlg6aYqAtFSyXQ5X/HiV" +
		"nfpPXFJKVItkPmYoZxRn9XqEe1Z9IpMvaFybrXs5UxlRilI3////////W99i9mSuZOZnLWu6hamW0XaQm9ZjTJMGm6t2v2ZSTglQmFPCXHFg6GSSZWNoX3Hmc8p1I3uXfoKGlYuDjNuReJkQZaxmq2uLTtVO1E86T39SOlP4U/JV41bbWOtZy1nJWf9bUFxNXgJeK1/X" +
		"YB1jB2UvW1xlr2W9ZehnnWti//9re2wPc0V5SXnBfPh9GX0rgKKBAoHziZaKXoppimaKjIrujMeM3JbMmPxrb06LTzxPjVFQW1db+mFIYwFmQmshbstsu3I+dL111HjBeTqADIAzgeqElI+ebFCef18Pi1idK3r6jvhbjZbrTgNT8Vf3WTFayVukYIluf28Gdb6M6luf" +
		"hQB74FByZ/SCnVxhhUp+HoIOUZlcBGNojWZlnHFueT59F4AFix2OypBuhseQqlAfUvpcOmdTcHxyNZFMkciTK4LlW8JfMWD5TjtT1luIYktnMWuKculz4HougWuNo5FSmZZRElPXVGpb/2OIajl9rJcAVtpTzlRo////////W5dcMV3eT+5hAWL+bTJ5wHnLfUJ+TX/S" +
		"ge2CH4SQiEaJcouQjnSPL5AxkUuRbJbGkZxOwE9PUUVTQV+TYg5n1GxBbgtzY34mkc2Sg1PUWRlbv23ReV1+LnybWH5xn1H6iFOP8E/KXPtmJXeseuOCHJn/UcZfqmXsaW9riW3z//9ulm9kdv59FF3hkHWRh5gGUeZSHWJAZpFm2W4aXrZ90n9yZviFr4X3ivhSqVPZ" +
		"WXNej1+QYFWS5JZkULdRH1LdUyBTR1PsVOhVRlUxVhdZaFm+WjxbtVwGXA9cEVwaXoReil7gX3Bif2KEYttjjGN3ZgdmDGYtZnZnfmiiah9qNWy8bYhuCW5YcTxxJnFndcd3AXhdeQF5ZXnweuB7EXynfTmAloPWhIuFSYhdiPOKH4o8ilSKc4xhjN6RpJJmk36UGJac" +
		"l5hOCk4ITh5OV1GXUnBXzlg0WMxbIl44YMVk/mdhZ1ZtRHK2dXN6Y4S4i3KRuJMgVjFX9Jj+////////Yu1pDWuWce1+VIB3gnKJ5pjfh1WPsVw7TzhP4U+1VQdaIFvdW+lfw2FOYy9lsGZLaO5pm214bfF1M3W5dx95XnnmfTOB44KvhaqJqoo6jquPm5Aykd2XB066" +
		"TsFSA1h1WOxcC3UaXD2BTooKj8WWY5dteyWKz5gIkWJW81Oo//+QF1Q5V4JeJWOobDRwindhfIt/4IhwkEKRVJMQkxiWj3RemsRdB11pZXBnoo2olttjbmdJaRmDxZgXlsCI/m+EZHpb+E4WcCx1XWYvUcRSNlLiWdNfgWAnYhBlP2V0Zh9mdGjyaBZrY24FcnJ1H3bb" +
		"fL6AVljwiP2Jf4qgipOKy5AdkZKXUpdZZYl6DoEGlrteLWDcYhplpWYUZ5B383pNfE1+PoEKjKyNZI3hjl94qVIHYtljpWRCYpiKLXqDe8CKrJbqfXaCDIdJTtlRSFNDU2Bbo1wCXBZd3WImYkdksGgTaDRsyW1FbRdn029ccU5xfWXLen97rX3a////////fkp/qIF6" +
		"ghuCOYWmim6Mzo31kHiQd5KtkpGVg5uuUk1VhG84cTZRaHmFflWBs3zOVkxYUVyoY6pm/mb9aVpy2XWPdY55DnlWed98l30gfUSGB4o0ljuQYZ8gUOdSdVPMU+JQCVWqWO5ZT3I9W4tcZFMdYONg82NcY4NjP2O7//9kzWXpZvld42nNaf1vFXHlTol16Xb4epN8333P" +
		"fZyAYYNJg1iEbIS8hfuIxY1wkAGQbZOXlxyaElDPWJdhjoHThTWNCJAgT8NQdFJHU3Ngb2NJZ19uLI2zkB9P11xejMplz32aU1KIllF2Y8NbWFtrXApkDWdRkFxO1lkaWSpscIpRVT5YFVmlYPBiU2fBgjVpVZZAmcSaKE9TWAZb/oAQXLFeL1+FYCBhS2I0Zv9s8G7e" +
		"gM6Bf4LUiIuMuJAAkC6Wip7bm9tO41PwWSd7LJGNmEyd+W7dcCdTU1VEW4ViWGKeYtNsom/vdCKKF5Q4b8GK/oM4UeeG+FPq////////U+lPRpBUj7BZaoExXf166o+/aNqMN3L4nEhqPYqwTjlTWFYGV2ZixWOiZeZrTm3hbltwrXfteu97qn27gD2AxobLipWTW1bj" +
		"WMdfPmWtZpZqgGu1dTeKx1Akd+VXMF8bYGVmemxgdfR6Gn9ugfSHGJBFmbN7yXVcevl7UYTE//+QEHnpepKDNlrhd0BOLU7yW5lf4GK9Zjxn8WzohmuId4o7kU6S85nQahdwJnMqgueEV4yvTgFRRlHLVYtb9V4WXjNegV8UXzVfa1+0YfJjEWaiZx1vbnJSdTp3OoB0" +
		"gTmBeId2ir+K3I2FjfOSmpV3mAKc5VLFY1d29GcVbIhzzYzDk66Wc20lWJxpDmnMj/2TmnXbkBpYWmgCY7Rp+09Dbyxn2I+7hSZ9tJNUaT9vcFdqWPdbLH0scipUCpHjnbROrU9OUFxQdVJDjJ5USFgkW5peHV6VXq1e918fYIxitWM6Y9Bor2xAeId5jnoLfeCCR4oC" +
		"iuaORJAT////////kLiRLZHYnw5s5WRYZOJldW70doR7G5Bpk9FuulTyX7lkpI9Nj+2SRFF4WGtZKVxVXpdt+36PdRyMvI7imFtwuU8da79vsXUwlvtRTlQQWDVYV1msXGBfkmWXZ1xuIXZ7g9+M7ZAUkP2TTXgleDpSql6mVx9ZdGASUBJRWlGs//9RzVIAVRBYVFhY" +
		"WVdblVz2XYtgvGKVZC1ncWhDaLxo33bXbdhub22bcG9xyF9Tddh5d3tJe1R7UnzWfXFSMIRjhWmF5IoOiwSMRo4PkAOQD5QZlnaYLZowldhQzVLVVAxYAlwOYadknm0ed7N65YD0hASQU5KFXOCdB1M/X5dfs22ccnl3Y3m/e+Rr0nLsiq1oA2phUfh6gWk0XEqc9oLr" +
		"W8WRSXAeVnhcb2DHZWZsjIxakEGYE1RRZseSDVlIkKNRhU5NUeqFmYsOcFhjepNLaWKZtH4EdXdTV2lgjt+W42xdToxcPF8Qj+lTAozRgImGeV7/ZeVOc1Fl////////WYJcP5fuTvtZil/Nio1v4XmweWJb54RxcytxsV50X/Vje2SaccN8mE5DXvxOS1fcVqJgqW/D" +
		"fQ2A/YEzgb+PsomXhqRd9GKKZK2Jh2d3bOJtPnQ2eDRaRn91gq2ZrE/zXsNi3WOSZVdnb3bDckyAzIC6jymRTVANV/lakmiF//9pc3Fkcv2Mt1jyjOCWapAZh3955HfnhClPL1JlU1pizWfPbMp2fXuUfJWCNoWEj+tm3W8gcgZ+G4OrmcGeplH9e7F4cnu4gId7SGro" +
		"XmGAjHVRdWBRa5Jibox2epGXmupPEH9wYpx7T5WlnOlWelhZhuSWvE80UiRTSlPNU9teBmQsZZFnf2w+bE5ySHKvc+11VH5BgiyF6Yype8SRxnFpmBKY72M9Zml1anbkeNCFQ4buUypTUVQmWYNeh198YLJiSWJ5YqtlkGvUbMx1snaueJF52H3Lf3eApYirirmMu5B/" +
		"l16Y22oLfDhQmVw+X65nh2vYdDV3CX+O////////nztnynoXUzl1i5rtX2aBnYPxgJhfPF/FdWJ7RpA8aGdZ61qbfRB2fossT/VfamoZbDdvAnTieWiIaIpVjHle32PPdcV50oLXkyiS8oSchu2cLVTBX2xljG1ccBWMp4zTmDtlT3T2Tg1O2FfgWStaZlvMUaheA16c" +
		"YBZidmV3//9lp2ZubW5yNnsmgVCBmoKZi1yMoIzmjXSWHJZET65kq2tmgh6EYYVqkOhcAWlTmKiEeoVXTw9Sb1+pXkVnDXmPgXmJB4mGbfVfF2JVbLhOz3Jpm5JSBlQ7VnRYs2GkYm5xGllufIl83n0blvBlh4BeThlPdVF1WEBeY15zXwpnxE4mhT2ViZZbfHOYAVD7" +
		"WMF2VninUiV3pYURe4ZQT1kJckd7x33oj7qP1JBNT79SyVopXwGXrU/dgheS6lcDY1VraXUriNyPFHpCUt9Yk2FVYgpmrmvNfD+D6VAjT/hTBVRGWDFZSVudXPBc710pXpZisWNnZT5luWcL////////bNVs4XD5eDJ+K4DegrOEDITshwKJEooqjEqQppLSmP2c851s" +
		"Tk9OoVCNUlZXSlmoXj1f2F/ZYj9mtGcbZ9Bo0lGSfSGAqoGoiwCMjIy/kn6WMlQgmCxTF1DVU1xYqGSyZzRyZ3dmekaR5lLDbKFrhlgAXkxZVGcsf/tR4XbG//9kaXjom1Seu1fLWblmJ2eaa85U6WnZXlWBnGeVm6pn/pxSaF1Opk/jU8hiuWcrbKuPxE+tfm2ev04H" +
		"YWJugG8rhRNUc2cqm0Vd83uVXKxbxoccbkqE0XoUgQhZmXyNbBF3IFLZWSJxIXJfd9uXJ51haQtaf1oYUaVUDVR9Zg5234/3kpic9Fnqcl1uxVFNaMl9v33sl2KeumR4aiGDAlmEW19r23MbdvJ9soAXhJlRMmcontl27mdiUv+ZBVwkYjt8foywVU9gtn0LlYBTAU5f" +
		"UbZZHHI6gDaRzl8ld+JThF95fQSFrIozjo2XVmfzha6UU2EJYQhsuXZS////////iu2POFUvT1FRKlLHU8tbpV59YKBhgmPWZwln2m5nbYxzNnM3dTF5UIjVipiQSpCRkPWWxIeNWRVOiE9ZTg6KiY8/mBBQrV58WZZbuV64Y9pj+mTBZtxpSmnYbQtutnGUdSh6r3+K" +
		"gACESYTJiYGLIY4KkGWWfZkKYX5ikWsy//9sg210f8x//G3Af4WHuoj4Z2WDsZg8lvdtG31hhD2Rak5xU3VdUGsEb+uFzYYtiadSKVQPXGVnTmiodAZ0g3XiiM+I4ZHMluKWeF+Lc4d6y4ROY6B1ZVKJbUFunHQJdVl4a3ySloZ63J+NT7ZhbmXFhlxOhk6uUNpOIVHM" +
		"W+5lmWiBbbxzH3ZCd616HHzngm+K0pB8kc+WdZgYUpt90VArU5hnl23LcdB0M4HojyqWo5xXnp90YFhBbZl9L5heTuRPNk+LUbdSsV26YBxzsnk8gtOSNJa3lvaXCp6Xn2Jmpmt0UhdSo3DIiMJeyWBLYZBvI3FJfD599IBv////////hO6QI5MsVEKbb2rTcImMwo3v" +
		"lzJStFpBXspfBGcXaXxplG1qbw9yYnL8e+2AAYB+h0uQzlFtnpN5hICLkzKK1lAtVIyKcWtqjMSBB2DRZ6Cd8k6ZTpicEIprhcGFaGkAbn54l4FV////////////////////////////////////////////////////////////////////////////////////////" +
		"/////////////////////////////18MThBOFU4qTjFONk48Tj9OQk5WTlhOgk6FjGtOioISXw1Ojk6eTp9OoE6iTrBOs062Ts5OzU7ETsZOwk7XTt5O7U7fTvdPCU9aTzBPW09dT1dPR092T4hPj0+YT3tPaU9wT5FPb0+GT5ZRGE/UT99Pzk/YT9tP0U/aT9BP5E/l" +
		"UBpQKFAUUCpQJVAFTxxP9lAhUClQLE/+T+9QEVAGUENQR2cDUFVQUFBIUFpQVlBsUHhQgFCaUIVQtFCy////////UMlQylCzUMJQ1lDeUOVQ7VDjUO5Q+VD1UQlRAVECURZRFVEUURpRIVE6UTdRPFE7UT9RQFFSUUxRVFFievhRaVFqUW5RgFGCVthRjFGJUY9RkVGT" +
		"UZVRllGkUaZRolGpUapRq1GzUbFRslGwUbVRvVHFUclR21HghlVR6VHt//9R8FH1Uf5SBFILUhRSDlInUipSLlIzUjlST1JEUktSTFJeUlRSalJ0UmlSc1J/Un1SjVKUUpJScVKIUpGPqI+nUqxSrVK8UrVSwVLNUtdS3lLjUuaY7VLgUvNS9VL4UvlTBlMIdThTDVMQ" +
		"Uw9TFVMaUyNTL1MxUzNTOFNAU0ZTRU4XU0lTTVHWU15TaVNuWRhTe1N3U4JTllOgU6ZTpVOuU7BTtlPDfBKW2VPfZvxx7lPuU+hT7VP6VAFUPVRAVCxULVQ8VC5UNlQpVB1UTlSPVHVUjlRfVHFUd1RwVJJUe1SAVHZUhFSQVIZUx1SiVLhUpVSsVMRUyFSo////////" +
		"VKtUwlSkVL5UvFTYVOVU5lUPVRRU/VTuVO1U+lTiVTlVQFVjVUxVLlVcVUVVVlVXVThVM1VdVZlVgFSvVYpVn1V7VX5VmFWeVa5VfFWDValVh1WoVdpVxVXfVcRV3FXkVdRWFFX3VhZV/lX9VhtV+VZOVlBx31Y0VjZWMlY4//9Wa1ZkVi9WbFZqVoZWgFaKVqBWlFaP" +
		"VqVWrla2VrRWwla8VsFWw1bAVshWzlbRVtNW11buVvlXAFb/VwRXCVcIVwtXDVcTVxhXFlXHVxxXJlc3VzhXTlc7V0BXT1dpV8BXiFdhV39XiVeTV6BXs1ekV6pXsFfDV8ZX1FfSV9NYClfWV+NYC1gZWB1YclghWGJYS1hwa8BYUlg9WHlYhVi5WJ9Yq1i6WN5Yu1i4" +
		"WK5YxVjTWNFY11jZWNhY5VjcWORY31jvWPpY+Vj7WPxY/VkCWQpZEFkbaKZZJVksWS1ZMlk4WT560llVWVBZTllaWVhZYllgWWdZbFlp////////WXhZgVmdT15Pq1mjWbJZxlnoWdxZjVnZWdpaJVofWhFaHFoJWhpaQFpsWklaNVo2WmJaalqaWrxavlrLWsJavVrj" +
		"Wtda5lrpWtZa+lr7WwxbC1sWWzJa0FsqWzZbPltDW0VbQFtRW1VbWltbW2VbaVtwW3NbdVt4ZYhbeluA//9bg1umW7hbw1vHW8lb1FvQW+Rb5lviW95b5VvrW/Bb9lvzXAVcB1wIXA1cE1wgXCJcKFw4XDlcQVxGXE5cU1xQXE9bcVxsXG5OYlx2XHlcjFyRXJRZm1yr" +
		"XLtctly8XLdcxVy+XMdc2VzpXP1c+lztXYxc6l0LXRVdF11cXR9dG10RXRRdIl0aXRldGF1MXVJdTl1LXWxdc112XYddhF2CXaJdnV2sXa5dvV2QXbddvF3JXc1d013SXdZd213rXfJd9V4LXhpeGV4RXhteNl43XkReQ15AXk5eV15UXl9eYl5kXkdedV52XnqevF5/" +
		"XqBewV7CXshe0F7P////////XtZe417dXtpe217iXuFe6F7pXuxe8V7zXvBe9F74Xv5fA18JX11fXF8LXxFfFl8pXy1fOF9BX0hfTF9OXy9fUV9WX1dfWV9hX21fc193X4Nfgl9/X4pfiF+RX4dfnl+ZX5hfoF+oX61fvF/WX/tf5F/4X/Ff3WCzX/9gIWBg//9gGWAQ" +
		"YClgDmAxYBtgFWArYCZgD2A6YFpgQWBqYHdgX2BKYEZgTWBjYENgZGBCYGxga2BZYIFgjWDnYINgmmCEYJtglmCXYJJgp2CLYOFguGDgYNNgtF/wYL1gxmC1YNhhTWEVYQZg9mD3YQBg9GD6YQNhIWD7YPFhDWEOYUdhPmEoYSdhSmE/YTxhLGE0YT1hQmFEYXNhd2FY" +
		"YVlhWmFrYXRhb2FlYXFhX2FdYVNhdWGZYZZhh2GsYZRhmmGKYZFhq2GuYcxhymHJYfdhyGHDYcZhumHLf3lhzWHmYeNh9mH6YfRh/2H9Yfxh/mIAYghiCWINYgxiFGIb////////Yh5iIWIqYi5iMGIyYjNiQWJOYl5iY2JbYmBiaGJ8YoJiiWJ+YpJik2KWYtRig2KU" +
		"Ytdi0WK7Ys9i/2LGZNRiyGLcYsxiymLCYsdim2LJYwxi7mLxYydjAmMIYu9i9WNQYz5jTWQcY09jlmOOY4Bjq2N2Y6Njj2OJY59jtWNr//9jaWO+Y+ljwGPGY+NjyWPSY/ZjxGQWZDRkBmQTZCZkNmUdZBdkKGQPZGdkb2R2ZE5lKmSVZJNkpWSpZIhkvGTaZNJkxWTH" +
		"ZLtk2GTCZPFk54IJZOBk4WKsZONk72UsZPZk9GTyZPplAGT9ZRhlHGUFZSRlI2UrZTRlNWU3ZTZlOHVLZUhlVmVVZU1lWGVeZV1lcmV4ZYJlg4uKZZtln2WrZbdlw2XGZcFlxGXMZdJl22XZZeBl4WXxZ3JmCmYDZftnc2Y1ZjZmNGYcZk9mRGZJZkFmXmZdZmRmZ2Zo" +
		"Zl9mYmZwZoNmiGaOZolmhGaYZp1mwWa5Zslmvma8////////ZsRmuGbWZtpm4GY/ZuZm6WbwZvVm92cPZxZnHmcmZyeXOGcuZz9nNmdBZzhnN2dGZ15nYGdZZ2NnZGeJZ3BnqWd8Z2pnjGeLZ6ZnoWeFZ7dn72e0Z+xns2fpZ7hn5GfeZ91n4mfuZ7lnzmfGZ+dqnGge" +
		"aEZoKWhAaE1oMmhO//9os2graFloY2h3aH9on2iPaK1olGidaJtog2quaLlodGi1aKBoumkPaI1ofmkBaMppCGjYaSJpJmjhaQxozWjUaOdo1Wk2aRJpBGjXaONpJWj5aOBo72koaSppGmkjaSFoxml5aXdpXGl4aWtpVGl+aW5pOWl0aT1pWWkwaWFpXmldaYFpammy" +
		"aa5p0Gm/acFp02m+ac5b6GnKad1pu2nDaadqLmmRaaBpnGmVabRp3mnoagJqG2n/awpp+WnyaedqBWmxah5p7WoUaetqCmoSasFqI2oTakRqDGpyajZqeGpHamJqWWpmakhqOGoiapBqjWqgaoRqomqj////////apeGF2q7asNqwmq4arNqrGreatFq32qqatpq6mr7" +
		"awWGFmr6axJrFpsxax9rOGs3dtxrOZjua0drQ2tJa1BrWWtUa1trX2tha3hreWt/a4BrhGuDa41rmGuVa55rpGuqa6trr2uya7Frs2u3a7xrxmvLa9Nr32vsa+tr82vv//+evmwIbBNsFGwbbCRsI2xebFVsYmxqbIJsjWyabIFsm2x+bGhsc2ySbJBsxGzxbNNsvWzX" +
		"bMVs3WyubLFsvmy6bNts72zZbOptH4hNbTZtK209bThtGW01bTNtEm0MbWNtk21kbVpteW1ZbY5tlW/kbYVt+W4VbgpttW3HbeZtuG3Gbext3m3Mbeht0m3Fbfpt2W3kbdVt6m3ubi1ubm4ubhlucm5fbj5uI25rbitudm5Nbh9uQ246bk5uJG7/bh1uOG6CbqpumG7J" +
		"brdu0269bq9uxG6ybtRu1W6PbqVuwm6fb0FvEXBMbuxu+G7+bz9u8m8xbu9vMm7M////////bz5vE273b4Zvem94b4FvgG9vb1tv829tb4JvfG9Yb45vkW/Cb2Zvs2+jb6FvpG+5b8Zvqm/fb9Vv7G/Ub9hv8W/ub9twCXALb/pwEXABcA9v/nAbcBpvdHAdcBhwH3Aw" +
		"cD5wMnBRcGNwmXCScK9w8XCscLhws3CucN9wy3Dd//9w2XEJcP1xHHEZcWVxVXGIcWZxYnFMcVZxbHGPcftxhHGVcahxrHHXcblxvnHScclx1HHOceBx7HHncfVx/HH5cf9yDXIQchtyKHItcixyMHIycjtyPHI/ckByRnJLclhydHJ+coJygXKHcpJylnKicqdyuXKy" +
		"csNyxnLEcs5y0nLicuBy4XL5cvdQD3MXcwpzHHMWcx1zNHMvcylzJXM+c05zT57Yc1dzanNoc3BzeHN1c3tzenPIc7NzznO7c8Bz5XPuc950onQFdG90JXP4dDJ0OnRVdD90X3RZdEF0XHRpdHB0Y3RqdHZ0fnSLdJ50p3TKdM901HPx////////dOB043TndOl07nTy" +
		"dPB08XT4dPd1BHUDdQV1DHUOdQ11FXUTdR51JnUsdTx1RHVNdUp1SXVbdUZ1WnVpdWR1Z3VrdW11eHV2dYZ1h3V0dYp1iXWCdZR1mnWddaV1o3XCdbN1w3W1db11uHW8dbF1zXXKddJ12XXjdd51/nX///91/HYBdfB1+nXydfN2C3YNdgl2H3YndiB2IXYidiR2NHYw" +
		"djt2R3ZIdkZ2XHZYdmF2YnZodml2anZndmx2cHZydnZ2eHZ8doB2g3aIdot2jnaWdpN2mXaadrB2tHa4drl2unbCds121nbSdt524Xbldud26oYvdvt3CHcHdwR3KXckdx53JXcmdxt3N3c4d0d3Wndod2t3W3dld393fnd5d453i3eRd6B3nnewd7Z3uXe/d7x3vXe7" +
		"d8d3zXfXd9p33Hfjd+53/HgMeBJ5JnggeSp4RXiOeHR4hnh8eJp4jHijeLV4qniveNF4xnjLeNR4vni8eMV4ynjs////////eOd42nj9ePR5B3kSeRF5GXkseSt5QHlgeVd5X3laeVV5U3l6eX95inmdeaefS3mqea55s3m5ebp5yXnVeed57HnheeN6CHoNehh6GXog" +
		"eh95gHoxejt6Pno3ekN6V3pJemF6Ynppn516cHp5en16iHqXepV6mHqWeql6yHqw//96tnrFesR6v5CDesd6ynrNes961XrTetl62nrdeuF64nrmeu168HsCew97CnsGezN7GHsZex57NXsoezZ7UHt6ewR7TXsLe0x7RXt1e2V7dHtne3B7cXtse257nXuYe597jXuc" +
		"e5p7i3uSe497XXuZe8t7wXvMe897tHvGe9176XwRfBR75nvlfGB8AHwHfBN783v3fBd8DXv2fCN8J3wqfB98N3wrfD18THxDfFR8T3xAfFB8WHxffGR8VnxlfGx8dXyDfJB8pHytfKJ8q3yhfKh8s3yyfLF8rny5fL18wHzFfMJ82HzSfNx84ps7fO988nz0fPZ8+n0G" +
		"////////fQJ9HH0VfQp9RX1LfS59Mn0/fTV9Rn1zfVZ9Tn1yfWh9bn1PfWN9k32JfVt9j319fZt9un2ufaN9tX3Hfb19q349faJ9r33cfbh9n32wfdh93X3kfd59+33yfeF+BX4KfiN+IX4SfjF+H34Jfgt+In5GfmZ+O341fjl+Q343//9+Mn46fmd+XX5Wfl5+WX5a" +
		"fnl+an5pfnx+e36DfdV+fY+ufn9+iH6Jfox+kn6QfpN+lH6Wfo5+m36cfzh/On9Ff0x/TX9Of1B/UX9Vf1R/WH9ff2B/aH9pf2d/eH+Cf4Z/g3+If4d/jH+Uf55/nX+af6N/r3+yf7l/rn+2f7iLcX/Ff8Z/yn/Vf9R/4X/mf+l/83/5mNyABoAEgAuAEoAYgBmAHIAh" +
		"gCiAP4A7gEqARoBSgFiAWoBfgGKAaIBzgHKAcIB2gHmAfYB/gISAhoCFgJuAk4CagK1RkICsgNuA5YDZgN2AxIDagNaBCYDvgPGBG4EpgSOBL4FL////////louBRoE+gVOBUYD8gXGBboFlgWaBdIGDgYiBioGAgYKBoIGVgaSBo4FfgZOBqYGwgbWBvoG4gb2BwIHC" +
		"gbqByYHNgdGB2YHYgciB2oHfgeCB54H6gfuB/oIBggKCBYIHggqCDYIQghaCKYIrgjiCM4JAglmCWIJdglqCX4Jk//+CYoJogmqCa4IugnGCd4J4gn6CjYKSgquCn4K7gqyC4YLjgt+C0oL0gvOC+oOTgwOC+4L5gt6DBoLcgwmC2YM1gzSDFoMygzGDQIM5g1CDRYMv" +
		"gyuDF4MYg4WDmoOqg5+DooOWgyODjoOHg4qDfIO1g3ODdYOgg4mDqIP0hBOD64POg/2EA4PYhAuDwYP3hAeD4IPyhA2EIoQgg72EOIUGg/uEbYQqhDyFWoSEhHeEa4SthG6EgoRphEaELIRvhHmENYTKhGKEuYS/hJ+E2YTNhLuE2oTQhMGExoTWhKGFIYT/hPSFF4UY" +
		"hSyFH4UVhRSE/IVAhWOFWIVI////////hUGGAoVLhVWFgIWkhYiFkYWKhaiFbYWUhZuF6oWHhZyFd4V+hZCFyYW6hc+FuYXQhdWF3YXlhdyF+YYKhhOGC4X+hfqGBoYihhqGMIY/hk1OVYZUhl+GZ4ZxhpOGo4aphqqGi4aMhraGr4bEhsaGsIbJiCOGq4bUht6G6Ybs" +
		"//+G34bbhu+HEocGhwiHAIcDhvuHEYcJhw2G+YcKhzSHP4c3hzuHJYcphxqHYIdfh3iHTIdOh3SHV4doh26HWYdTh2OHaogFh6KHn4eCh6+Hy4e9h8CH0JbWh6uHxIezh8eHxoe7h++H8ofgiA+IDYf+h/aH94gOh9KIEYgWiBWIIoghiDGINog5iCeIO4hEiEKIUohZ" +
		"iF6IYohriIGIfoieiHWIfYi1iHKIgoiXiJKIroiZiKKIjYikiLCIv4ixiMOIxIjUiNiI2YjdiPmJAoj8iPSI6IjyiQSJDIkKiROJQ4keiSWJKokriUGJRIk7iTaJOIlMiR2JYIle////////iWaJZIltiWqJb4l0iXeJfomDiYiJiomTiZiJoYmpiaaJrImvibKJuom9" +
		"ib+JwInaidyJ3YnnifSJ+IoDihaKEIoMihuKHYolijaKQYpbilKKRopIinyKbYpsimKKhYqCioSKqIqhipGKpYqmipqKo4rEis2KworaiuuK84rn//+K5IrxixSK4IriiveK3orbiwyLB4saiuGLFosQixeLIIszl6uLJosriz6LKItBi0yLT4tOi0mLVotbi1qLa4tf" +
		"i2yLb4t0i32LgIuMi46LkouTi5aLmYuajDqMQYw/jEiMTIxOjFCMVYxijGyMeIx6jIKMiYyFjIqMjYyOjJSMfIyYYh2MrYyqjL2MsoyzjK6MtozIjMGM5IzjjNqM/Yz6jPuNBI0FjQqNB40PjQ2NEJ9OjROMzY0UjRaNZ41tjXGNc42BjZmNwo2+jbqNz43ajdaNzI3b" +
		"jcuN6o3rjd+N4438jgiOCY3/jh2OHo4Qjh+OQo41jjCONI5K////////jkeOSY5MjlCOSI5ZjmSOYI4qjmOOVY52jnKOfI6BjoeOhY6EjouOio6TjpGOlI6ZjqqOoY6sjrCOxo6xjr6OxY7IjsuO247jjvyO+47rjv6PCo8FjxWPEo8ZjxOPHI8fjxuPDI8mjzOPO485" +
		"j0WPQo8+j0yPSY9Gj06PV49c//+PYo9jj2SPnI+fj6OPrY+vj7eP2o/lj+KP6o/vkIeP9JAFj/mP+pARkBWQIZANkB6QFpALkCeQNpA1kDmP+JBPkFCQUZBSkA6QSZA+kFaQWJBekGiQb5B2lqiQcpCCkH2QgZCAkIqQiZCPkKiQr5CxkLWQ4pDkYkiQ25ECkRKRGZEy" +
		"kTCRSpFWkViRY5FlkWmRc5FykYuRiZGCkaKRq5GvkaqRtZG0kbqRwJHBkcmRy5HQkdaR35HhkduR/JH1kfaSHpH/khSSLJIVkhGSXpJXkkWSSZJkkkiSlZI/kkuSUJKckpaSk5KbklqSz5K5kreS6ZMPkvqTRJMu////////kxmTIpMakyOTOpM1kzuTXJNgk3yTbpNW" +
		"k7CTrJOtk5STuZPWk9eT6JPlk9iTw5Pdk9CTyJPklBqUFJQTlAOUB5QQlDaUK5Q1lCGUOpRBlFKURJRblGCUYpRelGqSKZRwlHWUd5R9lFqUfJR+lIGUf5WClYeVipWUlZaVmJWZ//+VoJWolaeVrZW8lbuVuZW+lcpv9pXDlc2VzJXVldSV1pXcleGV5ZXiliGWKJYu" +
		"li+WQpZMlk+WS5Z3llyWXpZdll+WZpZylmyWjZaYlpWWl5aqlqeWsZaylrCWtJa2lriWuZbOlsuWyZbNiU2W3JcNltWW+ZcElwaXCJcTlw6XEZcPlxaXGZcklyqXMJc5lz2XPpdEl0aXSJdCl0mXXJdgl2SXZpdoUtKXa5dxl3mXhZd8l4GXepeGl4uXj5eQl5yXqJem" +
		"l6OXs5e0l8OXxpfIl8uX3Jftn0+X8nrfl/aX9ZgPmAyYOJgkmCGYN5g9mEaYT5hLmGuYb5hw////////mHGYdJhzmKqYr5ixmLaYxJjDmMaY6ZjrmQOZCZkSmRSZGJkhmR2ZHpkkmSCZLJkumT2ZPplCmUmZRZlQmUuZUZlSmUyZVZmXmZiZpZmtma6ZvJnfmduZ3ZnY" +
		"mdGZ7ZnumfGZ8pn7mfiaAZoPmgWZ4poZmiuaN5pFmkKaQJpD//+aPppVmk2aW5pXml+aYpplmmSaaZprmmqarZqwmryawJrPmtGa05rUmt6a35rimuOa5prvmuua7pr0mvGa95r7mwabGJsamx+bIpsjmyWbJ5somymbKpsumy+bMptEm0ObT5tNm06bUZtYm3Sbk5uD" +
		"m5GblpuXm5+boJuom7SbwJvKm7mbxpvPm9Gb0pvjm+Kb5JvUm+GcOpvym/Gb8JwVnBScCZwTnAycBpwInBKcCpwEnC6cG5wlnCScIZwwnEecMpxGnD6cWpxgnGecdpx4nOec7JzwnQmdCJzrnQOdBp0qnSadr50jnR+dRJ0VnRKdQZ0/nT6dRp1I////////nV2dXp1k" +
		"nVGdUJ1ZnXKdiZ2Hnaudb516nZqdpJ2pnbKdxJ3BnbuduJ26ncadz53Cndmd0534nead7Z3vnf2eGp4bnh6edZ55nn2egZ6InouejJ6SnpWekZ6dnqWeqZ64nqqerZdhnsyezp7PntCe1J7cnt6e3Z7gnuWe6J7v//+e9J72nvee+Z77nvye/Z8Hnwh2t58VnyGfLJ8+" +
		"n0qfUp9Un2OfX59gn2GfZp9nn2yfap93n3Kfdp+Vn5yfoFgvaceQWXRkUdxxmf//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////" +
		"////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////" +
		"////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////" +
		"/////////////////////////////////////////////w==";
	
	
	private static short[] UNICODE_TO_QR_KANJI = new short[65536];
	
	static {  // Unpack the Shift JIS table into a more computation-friendly form
		Arrays.fill(UNICODE_TO_QR_KANJI, (short)-1);
		byte[] bytes = Base64.getDecoder().decode(PACKED_QR_KANJI_TO_UNICODE);
		for (int i = 0; i < bytes.length; i += 2) {
			int j = ((bytes[i] & 0xFF) << 8) | (bytes[i + 1] & 0xFF);
			if (j == 0xFFFF)
				continue;
			if (UNICODE_TO_QR_KANJI[j] != -1)
				throw new AssertionError();
			UNICODE_TO_QR_KANJI[j] = (short)(i / 2);
		}
	}
	
}
