package io.nayuki.qrcodegen;
import static org.junit.Assert.*;

import java.util.EmptyStackException;

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
	
	private String nullstring = "";
	
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
		assertEquals(QrSegmentAdvanced.is_valid_version(min_version, 1),true);
		assertEquals(QrSegmentAdvanced.is_valid_version(min_version, 10),true);
		assertEquals(QrSegmentAdvanced.is_valid_version(min_version, 27),true);
		assertNotEquals(QrSegmentAdvanced.is_valid_version(min_version, 0),true);
	}

	/**
	*Purpose: Using MCDC, Testing version set correctly.
	*Input: 1, 40, 41, 0, 3, 4
	*Expected:
	*	Over range factor has return 0, So, not equal to 1
	*	Unless it has correct range in version's range, if you insert min_version is bigger that max_version, it return 0;
	 */
	
	@Test
	public void not_Valid_Version_test() { 
		assertEquals(QrSegmentAdvanced.not_Valid_Version(1, 40), false);
		assertNotEquals(QrSegmentAdvanced.not_Valid_Version(1, 41), false);
		assertNotEquals(QrSegmentAdvanced.not_Valid_Version(0, 40), false);
		assertNotEquals(QrSegmentAdvanced.not_Valid_Version(5, 4), false);
	}	

	/**
	*Purpose: This program checking whether its String is empty. SO, i test it working correctly
	*Input: 1, nullstring, Ecc.Low, 1, 40
	*Expected:
	*	occur AssertError
	 */	
	@Test(expected = AssertionError.class)
	public void emptyString_test() {
		testing.makeSegmentsOptimally(nullstring, Ecc.LOW, 1, 40);
	}

}
