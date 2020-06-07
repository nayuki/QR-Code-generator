package io.nayuki.qrcodegen;

public class msk1Command implements Command {
	private Msk1 theMsk1;
	
	public msk1Command(Msk1 theMsk1) {
		this.theMsk1 = theMsk1;
	}
	
	public boolean excute(int y, int x, int msk) {
		return theMsk1.operation(y, x, msk);
	}
}
