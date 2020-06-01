package io.nayuki.qrcodegen;

public class msk0Command implements Command{
	private Msk0 theMsk0;
	
	public msk0Command(Msk0 theMsk0) {
		this.theMsk0 = theMsk0;
	}
	
	public boolean excute(int y, int x, int msk) {
		return theMsk0.operation(y, x, msk);
	}
}
