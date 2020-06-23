package io.nayuki.qrcodegen;

import static org.junit.Assert.*;
import org.junit.*;

public class QrCodeTest {

	@Test
	public void testFactory() {
		Command mskCommand0 = MskCommandFactory.getCommand(0);
		Button button0 = new Button(mskCommand0);
		assertEquals(true, button0.pressed(0, 2, 1));

		Command mskCommand1 = MskCommandFactory.getCommand(1);
		Button button1 = new Button(mskCommand1);
		assertEquals(true, button1.pressed(0, 2, 1));

		Command mskCommand2 = MskCommandFactory.getCommand(2);
		Button button2 = new Button(mskCommand2);
		assertEquals(true, button2.pressed(0, 3, 1));

		Command mskCommand3 = MskCommandFactory.getCommand(3);
		Button button3 = new Button(mskCommand3);
		assertEquals(true, button3.pressed(1, 2, 1));

		Command mskCommand4 = MskCommandFactory.getCommand(4);
		Button button4 = new Button(mskCommand4);
		assertEquals(true, button4.pressed(0, 2, 1));

		Command mskCommand5 = MskCommandFactory.getCommand(5);
		Button button5 = new Button(mskCommand5);
		assertEquals(true, button5.pressed(0, 2, 1));

		Command mskCommand6 = MskCommandFactory.getCommand(6);
		Button button6 = new Button(mskCommand6);
		assertEquals(true, button6.pressed(0, 2, 1));

		Command mskCommand7 = MskCommandFactory.getCommand(7);
		Button button7 = new Button(mskCommand7);
		assertEquals(true, button7.pressed(0, 4, 1));
	}

	@Test(expected = AssertionError.class)
	public void testAssertionError() {

		Command mskCommand = MskCommandFactory.getCommand(9);

	}

	@Test
	public void testApplyMask() {
		Ecc errCorLvl = Ecc.LOW;
		Ecc errCorLv2 = Ecc.MEDIUM;
		Ecc errCorLv3 = Ecc.QUARTILE;
		Ecc errCorLv4 = Ecc.HIGH;
		
		int version1 = 1;
		int version2 = 2;
		int version3 = 39;
		int version4 = 40;
		
		int size1 = version1 * 4 + 17;
		int size2 = version2 * 4 + 17;
		int size3 = version3 * 4 + 17;
		int size4 = version4 * 4 + 17;
		
		String text = "Hello, world!";

		QrCode qrcodeLOW = QrCode.encodeText(text, errCorLvl);
		QrCode qrcodeMEDIUM = QrCode.encodeText(text, errCorLv2);
		QrCode qrcodeQUARTILE = QrCode.encodeText(text, errCorLv3);
		QrCode qrcodeHIGH = QrCode.encodeText(text, errCorLv4);

		qrcodeLOW.excuteGetNumRawDataModules(version1);
		qrcodeMEDIUM.excuteGetNumRawDataModules(version2);
		qrcodeQUARTILE.excuteGetNumRawDataModules(version3);
		qrcodeHIGH.excuteGetNumRawDataModules(version4);

		qrcodeLOW.executeReedSolomonComputeDivisor(1);
		qrcodeMEDIUM.executeReedSolomonComputeDivisor(2);
		qrcodeQUARTILE.executeReedSolomonComputeDivisor(254);
		qrcodeHIGH.executeReedSolomonComputeDivisor(255);

		qrcodeLOW.setModules(size1);
		qrcodeLOW.setIsFunction(size1);
		qrcodeMEDIUM.setModules(size2);
		qrcodeMEDIUM.setIsFunction(size2);
		qrcodeQUARTILE.setModules(size3);
		qrcodeQUARTILE.setIsFunction(size3);
		qrcodeHIGH.setModules(size4);
		qrcodeHIGH.setIsFunction(size4);
		
		qrcodeLOW.applyMask(0);
		qrcodeMEDIUM.applyMask(1);
		qrcodeQUARTILE.applyMask(6);
		qrcodeHIGH.applyMask(7);

	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegalArgumentException_applyMask_MAXBound() {

		Ecc errCorLvl = Ecc.LOW;
		String text = "Hello, world!";

		QrCode qrcodeLOW = QrCode.encodeText(text, errCorLvl);
		qrcodeLOW.applyMask(8);

	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegalArgumentException_applyMask_MINBouond() {

		Ecc errCorLvl = Ecc.LOW;
		String text = "Hello, world!";

		QrCode qrcodeLOW = QrCode.encodeText(text, errCorLvl);
		qrcodeLOW.applyMask(-1);

	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegalArgumentException_getNumRawDataModules_MINBound() {

		Ecc errCorLvl = Ecc.LOW;
		String text = "Hello, world!";

		QrCode qrcodeLOW = QrCode.encodeText(text, errCorLvl);
		qrcodeLOW.excuteGetNumRawDataModules(0);

	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegalArgumentException_getNumRawDataModules_MAXBound() {

		Ecc errCorLvl = Ecc.LOW;
		String text = "Hello, world!";

		QrCode qrcodeLOW = QrCode.encodeText(text, errCorLvl);
		qrcodeLOW.excuteGetNumRawDataModules(41);

	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegalArgumentException_ReedSolomonComputeDivisor_MINBound() {

		Ecc errCorLvl = Ecc.LOW;
		String text = "Hello, world!";

		QrCode qrcodeLOW = QrCode.encodeText(text, errCorLvl);
		qrcodeLOW.executeReedSolomonComputeDivisor(0);

	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegalArgumentException_ReedSolomonComputeDivisor_MAXBound() {

		Ecc errCorLvl = Ecc.LOW;
		String text = "Hello, world!";

		QrCode qrcodeLOW = QrCode.encodeText(text, errCorLvl);
		qrcodeLOW.executeReedSolomonComputeDivisor(256);

	}
}