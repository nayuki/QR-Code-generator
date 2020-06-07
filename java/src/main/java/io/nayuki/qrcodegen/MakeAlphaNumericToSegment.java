import java.util.Objects;

public class MakeAlphaNumericToSegment implements MakeSegment {

	/**
	 * Returns a segment representing the specified text string encoded in alphanumeric mode.
	 * The characters allowed are: 0 to 9, A to Z (uppercase only), space,
	 * dollar, percent, asterisk, plus, hyphen, period, slash, colon.
	 * @param text the text (not {@code null}), with only certain characters allowed
	 * @return a segment (not {@code null}) containing the text
	 * @throws NullPointerException if the string is {@code null}
	 * @throws IllegalArgumentException if the string contains non-encodable characters
	 */
	public QrSegment excute(String text) {
		Objects.requireNonNull(text);
		if (!QrSegment.ALPHANUMERIC_REGEX.matcher(text).matches())
			throw new IllegalArgumentException("String contains unencodable characters in alphanumeric mode");
		
		BitBuffer bitBuffer = new BitBuffer();
		changeAlphaNumericStringToSegment(text, bitBuffer);
		return new QrSegment(QrSegment.Mode.ALPHANUMERIC, text.length(), bitBuffer);
	}

	public static void changeAlphaNumericStringToSegment(String text, BitBuffer bitBuffer) {
		int i;
		for (i = 0; i <= text.length() - 2; i += 2) {  // Process groups of 2
			int temp = QrSegment.ALPHANUMERIC_CHARSET.indexOf(text.charAt(i)) * 45;
			temp += QrSegment.ALPHANUMERIC_CHARSET.indexOf(text.charAt(i + 1));
			bitBuffer.appendBits(temp, 11);
		}
		if (i < text.length())  // 1 character remaining
			bitBuffer.appendBits(QrSegment.ALPHANUMERIC_CHARSET.indexOf(text.charAt(i)), 6);
	}
}
