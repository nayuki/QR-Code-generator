package io.nayuki.qrcodegen;

import java.nio.charset.StandardCharsets;

public class AlphanumericMode extends QrMode {
	protected AlphanumericMode(int mode, int... ccbits) {
		modeBits = mode;
		numBitsCharCount = ccbits;
	}
	
	protected AlphanumericMode() {
		modeBits = 0x2;
		numBitsCharCount[0] = 9;
		numBitsCharCount[1] = 11;
		numBitsCharCount[2] = 13;
	}
	
	public int getcost(int pre, int codePoint) {
		return pre + 33;
	}
	
	public QrSegment making(String str) {
		return QrSegment.makeAlphanumeric(str);
	}
}
