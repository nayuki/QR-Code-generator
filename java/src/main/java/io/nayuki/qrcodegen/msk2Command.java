package io.nayuki.qrcodegen;

public class msk2Command implements Command {
	private Msk2 theMsk2;
	
	public msk2Command(Msk2 theMsk2) {
		this.theMsk2 = theMsk2;
	}
	
	public boolean excute(int y, int x, int msk) {
		return theMsk2.operation(y, x, msk);
	}
}
