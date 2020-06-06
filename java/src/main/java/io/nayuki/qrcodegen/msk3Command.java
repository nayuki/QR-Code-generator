package io.nayuki.qrcodegen;

public class msk3Command implements Command {
	private Msk3 theMsk3;
	
	public msk3Command(Msk3 theMsk3) {
		this.theMsk3 = theMsk3;
	}
	
	public boolean excute(int y, int x, int msk) {
		return theMsk3.operation(y, x, msk);
	}
}
