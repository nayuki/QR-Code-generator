package io.nayuki.qrcodegen;

public class msk7Command implements Command {
	private Msk7 theMsk7;
	
	public msk7Command(Msk7 theMsk7) {
		this.theMsk7 = theMsk7;
	}
	
	public boolean excute(int y, int x, int msk) {
		return theMsk7.operation(y, x, msk);
	}
}
