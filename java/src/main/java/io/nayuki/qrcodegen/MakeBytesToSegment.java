import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class MakeBytesToSegment  implements MakeSegment {

	/**
	 * Returns a segment representing the specified binary data
	 * encoded in byte mode. All input byte arrays are acceptable.
	 * <p>Any text string can be converted to UTF-8 bytes ({@code
	 * s.getBytes(StandardCharsets.UTF_8)}) and encoded as a byte mode segment.</p>
	 * @param data the binary data (not {@code null})
	 * @return a segment (not {@code null}) containing the data
	 * @throws NullPointerException if the array is {@code null}
	 */
	public QrSegment excute(String text) {
		byte[] data = text.getBytes(StandardCharsets.UTF_8));
		
		Objects.requireNonNull(data);
		BitBuffer bitBuffer = new BitBuffer();
		for (byte bits : data)
			changeByteToSegment(bitBuffer, bits);
		return new QrSegment(QrSegment.Mode.BYTE, data.length, bitBuffer);
	}
	
	public QrSegment excuteForBytedata(byte[] data) {
		Objects.requireNonNull(data);
		BitBuffer bitBuffer = new BitBuffer();
		for (byte bits : data)
			changeByteToSegment(bitBuffer, bits);
		return new QrSegment(QrSegment.Mode.BYTE, data.length, bitBuffer);
	}
	
	public static void changeByteToSegment(BitBuffer bitBuffer, byte bits) {
		bitBuffer.appendBits(bits & 0xFF, 8);
	}
}
