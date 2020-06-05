package io.nayuki.qrcodegen;

public class Button {
	public Command theCommand;

	public Button(Command theCommand) {
		setCommand(theCommand);
	}

	public void setCommand(Command newCommand) {
		this.theCommand = newCommand;
	}

	public boolean pressed(int y, int x, int msk) {
		return theCommand.excute(y, x, msk);
	}
}
