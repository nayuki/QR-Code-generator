package io.nayuki.qrcodegen;

public class NumericMode extends QrMode {
	protected NumericMode(int mode, int... ccbits) {
		modeBits = mode;
		numBitsCharCount = ccbits;
	}
	
	protected NumericMode() {
		modeBits = 0x1;
		numBitsCharCount[0] = 10;
		numBitsCharCount[1] = 12;
		numBitsCharCount[2] = 1;
	}
	
	public int getcost(int pre, int codePoint) {
		return pre + 20;
	}
	
	public QrSegment making(String str) {
		return QrSegment.makeNumeric(str);
	}
}
