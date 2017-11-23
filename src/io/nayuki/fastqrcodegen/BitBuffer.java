package io.nayuki.fastqrcodegen;

import java.util.Arrays;


final class BitBuffer {
	
	/*---- Fields ----*/
	
	private int[] data;
	
	private int bitLength;
	
	
	
	/*---- Constructors ----*/
	
	public BitBuffer() {
		data = new int[64];
		bitLength = 0;
	}
	
	
	
	/*---- Methods ----*/
	
	public int getBit(int index) {
		if (index < 0 || index >= bitLength)
			throw new IndexOutOfBoundsException();
		return (data[index >>> 5] >>> ~index) & 1;
	}
	
	
	public void appendBits(int val, int len) {
		if (len < 0 || len > 31 || val >>> len != 0)
			throw new IllegalArgumentException("Value out of range");
		
		if (bitLength + len + 1 > data.length << 5)
			data = Arrays.copyOf(data, data.length * 2);
		assert bitLength + len <= data.length << 5;
		
		int remain = 32 - (bitLength & 0x1F);
		assert 1 <= remain && remain <= 32;
		if (remain < len) {
			data[bitLength >>> 5] |= val >>> (len - remain);
			bitLength += remain;
			assert (bitLength & 0x1F) == 0;
			len -= remain;
			val &= (1 << len) - 1;
			remain = 32;
		}
		data[bitLength >>> 5] |= val << (remain - len);
		bitLength += len;
	}
	
}
