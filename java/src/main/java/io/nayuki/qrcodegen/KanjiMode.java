package io.nayuki.qrcodegen;

public class KanjiMode extends QrMode {
	protected KanjiMode(int mode, int... ccbits) {
		modeBits = mode;
		numBitsCharCount = ccbits;
	}
	
	protected KanjiMode() {
		modeBits = 0x8;
		numBitsCharCount[0] = 8;
		numBitsCharCount[1] = 10;
		numBitsCharCount[2] = 12;
	}
	
	public int getcost(int pre, int codePoint) {
		return pre + 78;
	}
	
	public QrSegment making(String str) {
		return QrSegmentAdvanced.makeKanji(str);
	}
	
}
