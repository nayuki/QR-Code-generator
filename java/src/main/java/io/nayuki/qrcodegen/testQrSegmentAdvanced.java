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
	
	private static int min_version = QrCode.MIN_VERSION;
	private static int max_version = QrCode.MAX_VERSION;
	
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
	
	/**
	*Purpose: Using MCDC, Testing version set correctly.
	*Input: overString, Ecc.LOW, minversion = 1, maxversion = 40
	*Expected:
	*	Return throw
	 */
//			return version == minVersion || version == 10 || version == 27;
	@Test
	public void is_valid_Test() {
		assertEquals(QrSegmentAdvanced.is_valid_version(min_version, 1),1);
		assertEquals(QrSegmentAdvanced.is_valid_version(min_version, 10),1);
		assertEquals(QrSegmentAdvanced.is_valid_version(min_version, 27),1);
		assertNotEquals(QrSegmentAdvanced.is_valid_version(min_version, 0),1);
	}

}
