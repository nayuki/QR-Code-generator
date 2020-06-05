package io.nayuki.qrcodegen;

public interface Command {
	public abstract boolean excute(int y, int x, int msk);
}
