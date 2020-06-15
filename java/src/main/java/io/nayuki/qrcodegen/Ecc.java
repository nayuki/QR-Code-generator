package io.nayuki.qrcodegen;

/**
 * The error correction level in a QR Code symbol.
 */
public class Ecc {
	/** The QR Code can tolerate about 7% erroneous codewords. */
	public static final Ecc LOW = new Ecc(1,
			new byte[] { -1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30,
					30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30 },
			new byte[] { -1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13,
					14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25 });
	/** The QR Code can tolerate about 15% erroneous codewords. */
	public static final Ecc MEDIUM = new Ecc(0,
			new byte[] { -1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28,
					28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28 },
			new byte[] { -1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23,
					25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49 });
	/** The QR Code can tolerate about 25% erroneous codewords. */
	public static final Ecc QUARTILE = new Ecc(3,
			new byte[] { -1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30,
					30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30 },
			new byte[] { -1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29,
					34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68 });
	/** The QR Code can tolerate about 30% erroneous codewords. */
	public static final Ecc HIGH = new Ecc(2,
			new byte[] { -1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30,
					30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30 },
			new byte[] { -1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35,
					37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81 });

	private final int formatBits;
	private final byte[] blockLength;
	private final byte[] numberOfBlocks;

	// For formatBits and blockLength, array index is Version: (note that index 0 is
	// for padding, and is set to an illegal value)
	private Ecc(int formatBits, byte[] blockLength, byte[] numberOfBlocks) {
		this.formatBits = formatBits;
		this.blockLength = blockLength;
		this.numberOfBlocks = numberOfBlocks;
	}

	public int getFormatBits() {
		return formatBits;
	}

	public byte getBlockLength(int version) {
		return blockLength[version];
	}

	public byte getNumberOfBlock(int version) {
		return numberOfBlocks[version];
	}

	// Must in ascending order of error protection
	// so that values() work properly
	public static Ecc[] values() {
		return new Ecc[] { LOW, MEDIUM, QUARTILE, HIGH };
	}
}
