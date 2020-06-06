package io.nayuki.qrcodegen;

public class Msk4 {
	public boolean operation(int y, int x, int msk) {
		return ((x / 3 + y / 2) % 2 == 0);
	}
}
