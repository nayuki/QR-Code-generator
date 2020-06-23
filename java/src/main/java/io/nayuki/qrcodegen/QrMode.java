package io.nayuki.qrcodegen;

public abstract class QrMode {
	/*-- Fields --*/
	
	// The mode indicator bits, which is a uint4 value (range 0 to 15).
	int modeBits;
	
	// Number of character count bits for three different version ranges.
	protected int[] numBitsCharCount;
	
	int headCost;
	/*-- Method --*/
	
	// Returns the bit width of the character count field for a segment in this mode
	// in a QR Code at the given version number. The result is in the range [0, 16].
	int numCharCountBits(int ver) {
		assert QrCode.MIN_VERSION <= ver && ver <= QrCode.MAX_VERSION;
		return numBitsCharCount[(ver + 7) / 17];
	}
	
	protected QrMode() {
		this.modeBits = 0;
		this.numBitsCharCount = null;
	}
	
	public void get(QrMode md) {
		this.modeBits = md.modeBits;
		this.numBitsCharCount = md.numBitsCharCount;
	}
	
	public int whichMode() {
		return modeBits;
	}

	protected QrSegment making(String s) {
		return null;
	}
	
	public int getcost(int pre, int codePoint) {
		return pre;
	}
}