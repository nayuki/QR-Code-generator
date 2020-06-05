package io.nayuki.qrcodegen;

public class msk5Command implements Command {
	private Msk5 theMsk5;
	
	public msk5Command(Msk5 theMsk5) {
		this.theMsk5 = theMsk5;
	}
	
	public boolean excute(int y, int x, int msk) {
		return theMsk5.operation(y, x, msk);
	}
}
