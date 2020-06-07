package io.nayuki.qrcodegen;

public class MskCommandFactory {
	public static Command getCommand(int msk) {
		Command theCommand = null;
		
		Msk0 msk0 = new Msk0();
		Msk1 msk1 = new Msk1();
		Msk2 msk2 = new Msk2();
		Msk3 msk3 = new Msk3();
		Msk4 msk4 = new Msk4();
		Msk5 msk5 = new Msk5();
		Msk6 msk6 = new Msk6();
		Msk7 msk7 = new Msk7();
		
		switch (msk) {
			case 0:
				theCommand = new msk0Command(msk0);
				break;
			case 1: 
				theCommand = new msk1Command(msk1);
				break;
			case 2: 
				theCommand = new msk2Command(msk2);
				break;
			case 3:
				theCommand = new msk3Command(msk3);
				break;
			case 4: 
				theCommand = new msk4Command(msk4);
				break;
			case 5: 
				theCommand = new msk5Command(msk5);
				break;
			case 6: 
				theCommand = new msk6Command(msk6);
				break;
			case 7: 
				theCommand = new msk7Command(msk7);
				break;
			default: 
				throw new AssertionError();
		}
		return theCommand;
	}
}