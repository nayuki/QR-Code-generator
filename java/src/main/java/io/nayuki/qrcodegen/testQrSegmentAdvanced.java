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
	*Input: nin_version, 1, 10, 27, 0
	*Expected:
	*	All True
	 */

	@Test
	public void is_valid_Test() {
		assertEquals(QrSegmentAdvanced.is_valid_version(min_version, 1),1);
		assertEquals(QrSegmentAdvanced.is_valid_version(min_version, 10),1);
		assertEquals(QrSegmentAdvanced.is_valid_version(min_version, 27),1);
		assertNotEquals(QrSegmentAdvanced.is_valid_version(min_version, 0),1);
	}

	/**
	*Purpose: Using MCDC, Testing version set correctly.
	*Input: 1, 40, 41, 0, 3, 4
	*Expected:
	*	Overrange factor has return 0, So, not equal to 1
	*	Unless it has correct range in version's range, if you insert min_version is bigger that max_version, it return 0;
	 */
	
	public void not_Valid_Version_test() { 
		assertEquals(QrSegmentAdvanced.not_Valid_Version(1, 40), 1);
		assertNotEquals(QrSegmentAdvanced.not_Valid_Version(1, 41), 1);
		assertNotEquals(QrSegmentAdvanced.not_Valid_Version(0, 40), 1);
		assertNotEquals(QrSegmentAdvanced.not_Valid_Version(5, 4), 1);
	}	
	
	
}
