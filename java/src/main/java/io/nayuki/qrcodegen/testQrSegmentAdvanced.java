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

	/**
	*Purpose: This program has several mode and making these. When making these mode, find suitable mode using flag. So, i test this functions.
	*		  is_numeric -> 0 ~ 9 's UTF-8 number, is_alphanumeric -> "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:" stinrg set,  
	*Input: "0123456780", "A", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"
	*Expected:
	*	All is correct
	 */	
	@Test
	public void is_Corredt_Mode_test() {
		int[] correctStr = "09".codePoints().toArray();
		for (int forcnt : correctStr){
			assertEquals(QrSegmentAdvanced.is_numeric(forcnt), true);
		}
		int[] wrongStr = "a".codePoints().toArray();
		for (int forcnt : wrongStr){
			assertEquals(QrSegmentAdvanced.is_numeric(forcnt), false);
		}
		
		correctStr = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:".codePoints().toArray();
		for (int forcnt : correctStr){
			assertEquals(QrSegmentAdvanced.is_alphanumeric(forcnt), true);
		}
		
		wrongStr = "\\".codePoints().toArray();
		for (int forcnt : wrongStr){
			assertEquals(QrSegmentAdvanced.is_alphanumeric(forcnt), false);
		}
	}
	
	/**
	*Purpose: 'toCodePoints' function is convert String to integer, if String is in UTF-8. 
	*		  In UTF-8 has variable character why I haven't check all UTF-8's string. 
	*		  So, I just test character that are excluded at UTF-8.  
	*		  For checking exception occurs when a character other than UTF-8 is entered.
	*Input: "A�"
	*Expected:
	*	occur IllegalArgumentException
	 */	
	@Test(expected = IllegalArgumentException.class)
	public void toCodePoints_test() {
		String not_UTF = "A�";
		testing.makeSegmentsOptimally(not_UTF, Ecc.LOW, 1, 40);
	}
}
