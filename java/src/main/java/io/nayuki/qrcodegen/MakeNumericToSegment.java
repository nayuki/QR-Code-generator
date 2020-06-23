import java.util.Objects;

public class MakeNumericToSegment  implements MakeSegment {

	/**
	 * Returns a segment representing the specified string of decimal digits encoded in numeric mode.
	 * @param digits the text (not {@code null}), with only digits from 0 to 9 allowed
	 * @return a segment (not {@code null}) containing the text
	 * @throws NullPointerException if the string is {@code null}
	 * @throws IllegalArgumentException if the string contains non-digit characters
	 */
	public QrSegment excute(String digits) {
		Objects.requireNonNull(digits);
		if (containNonNumericCharaters(digits))
			throw new IllegalArgumentException("String contains non-numeric characters");
		
		BitBuffer bitBuffer = new BitBuffer();
		changeNumericToSegment(digits, bitBuffer);
		return new QrSegment(QrSegment.Mode.NUMERIC, digits.length(), bitBuffer);
	}
	
	public static void changeNumericToSegment(String digits, BitBuffer bitBuffer) {
		for (int i = 0; i < digits.length(); ) {  // Consume up to 3 digits per iteration
			int n = Math.min(digits.length() - i, 3);
			bitBuffer.appendBits(Integer.parseInt(digits.substring(i, i + n)), n * 3 + 1);
			i += n;
		}
	}
	
	public static boolean containNonNumericCharaters(String digits) {
		return !QrSegment.NUMERIC_REGEX.matcher(digits).matches();
	}
}