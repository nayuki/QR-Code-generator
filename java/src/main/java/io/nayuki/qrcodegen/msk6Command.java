package io.nayuki.qrcodegen;

public class msk6Command implements Command {
	private Msk6 theMsk6;
	
	public msk6Command(Msk6 theMsk6) {
		this.theMsk6 = theMsk6;
	}
	
	public boolean excute(int y, int x, int msk) {
		return theMsk6.operation(y, x, msk);
	}
}
