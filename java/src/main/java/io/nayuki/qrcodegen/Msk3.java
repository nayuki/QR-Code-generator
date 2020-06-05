package io.nayuki.qrcodegen;

public class Msk3 {
	public boolean operation(int y, int x, int msk) {
		return ((x + y) % 3 == 0);
	}
}
