package io.nayuki.qrcodegen;

public class msk4Command implements Command {
	private Msk4 theMsk4;
	
	public msk4Command(Msk4 theMsk4) {
		this.theMsk4 = theMsk4;
	}
	
	public boolean excute(int y, int x, int msk) {
		return theMsk4.operation(y, x, msk);
	}
}
