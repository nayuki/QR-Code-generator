package io.nayuki.qrcodegen;

public class Msk5 {
	public boolean operation(int y, int x, int msk) {
		return (x * y % 2 + x * y % 3 == 0);
	}
}
