package io.nayuki.qrcodegen;
import static org.junit.Assert.*;

import org.junit.Test;

public class testQrSegmentAdvanced {
	private QrSegmentAdvanced testing;
	private String overString = "asdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasd"
			+ "asdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasd"
			+ "asdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasd"
			+ "asdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasd"
			+ "asdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasdasd";	
	/**
	*Purpose: Testing when user insert over text length
	*Input: overString, Ecc.LOW, minversion = 1, maxversion = 40
	*Expected:
	*	Return throw
	*/
	@Test(expected=DataTooLongException.class)
	public void makeSegmentsOptimallyThrowtest() {
		testing.makeSegmentsOptimally(overString, Ecc.LOW, 1, 40);
	}

}
