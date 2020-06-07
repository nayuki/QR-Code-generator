package io.nayuki.qrcodegen;

import java.nio.charset.StandardCharsets;

public class ByteMode extends QrMode {
	protected ByteMode(int mode, int... ccbits) {
		modeBits = mode;
		numBitsCharCount = ccbits;
	}
	
	protected ByteMode() {
		modeBits = 0x4;
		numBitsCharCount[0] = 8;
		numBitsCharCount[1] = 16;
		numBitsCharCount[2] = 16;
	}
	
	public int getcost(int pre, int codePoint) {
		return pre + QrSegmentAdvanced.countUtf8Bytes(codePoint) * 8 * 6;
	}
	
	public QrSegment making(String str) {
		return QrSegment.makeBytes(str.getBytes(StandardCharsets.UTF_8));
	}
}
