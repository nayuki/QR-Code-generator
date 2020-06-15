package io.nayuki.qrcodegen;

public class EciMode extends QrMode {
	protected EciMode(int mode, int... ccbits) {
		modeBits = mode;
		numBitsCharCount = ccbits;
	}
	
	protected EciMode() {
		modeBits = 0x7;
		numBitsCharCount[0] = 0;
		numBitsCharCount[1] = 0;
		numBitsCharCount[2] = 0;
	}
	
	public QrSegment making(int prmt) {
		return QrSegment.makeEci(prmt);
	}
	
	public int getcost(int pre, int codePoint) {
		return pre;
	}
}
