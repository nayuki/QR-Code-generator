
package io.nayuki.qrcodegen;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;


public class QrCodeTestEncodeSegments {
	private static List<QrSegment> qrSegments;

	@BeforeClass
	public static void oneTimeSetUp() {
		qrSegments = QrSegment.makeSegments("Hello, World!");
	}
	
	/**
	 * Purpose: Make QrCode
	 * Input:  QrSegment.makeSegments("Hello, World!"), Ecc.LOW, 10, 20, 3, true
	 * Expected:
	 * 			QrCode made without Exception
	 */
	@Test
	public void testInRange() {
		assertNotNull(QrCode.encodeSegments(qrSegments, Ecc.LOW, 10, 20, 3, true));
	}
	
	/**
	 * Purpose: Try to make QrCode with a invalid argument(0 is less than minimal value in valid version range)
	 * Input:  QrSegment.makeSegments("Hello, World!"), Ecc.LOW, 0, QrCode.MAX_VERSION, 3, true
	 * Expected:
	 * 			throw IllegalArgumentException
	 */
	@Test(expected = IllegalArgumentException.class)
	public void testMinVersionOverLeftBoundary() {
		QrCode.encodeSegments(qrSegments, Ecc.LOW, 0, QrCode.MAX_VERSION, 3, true);
	}
	
	/**
	 * Purpose: Try to make QrCode with a invalid argument(41 is greater than maximal value in valid version range)
	 * Input:  QrSegment.makeSegments("Hello, World!"), Ecc.LOW, QrCode.MIN_VERSION, 41, 3, true
	 * Expected:
	 * 			throw IllegalArgumentException
	 */
	@Test(expected = IllegalArgumentException.class)
	public void testMaxVersionOverRightBoundary() {
		QrCode.encodeSegments(qrSegments, Ecc.LOW, QrCode.MIN_VERSION, 41, 3, true);
	}
	
	/**
	 * Purpose: Try to make QrCode with a invalid argument(minimal version input must be small or equal than maximal version input)
	 * Input:  QrSegment.makeSegments("Hello, World!"), Ecc.LOW, QrCode.MAX_VERSION, QrCode.MIN_VERSION, 3, true
	 * Expected:
	 * 			throw IllegalArgumentException
	 */
	@Test(expected = IllegalArgumentException.class)
	public void testMinVersionGreaterThanMaxVersion() {
		QrCode.encodeSegments(qrSegments, Ecc.LOW, QrCode.MAX_VERSION, QrCode.MIN_VERSION, 3, true);
	}
	
	/**
	 * Purpose: Try to make QrCode with a invalid argument(mask must be -1 or greater than -1)
	 * Input:  QrSegment.makeSegments("Hello, World!"), Ecc.LOW, QrCode.MIN_VERSION, QrCode.MAX_VERSION, -2, true
	 * Expected:
	 * 			throw IllegalArgumentException
	 */
	@Test(expected = IllegalArgumentException.class)
	public void testMaskOverLeftBoundary() {
		QrCode.encodeSegments(qrSegments, Ecc.LOW, QrCode.MIN_VERSION, QrCode.MAX_VERSION, -2, true);
	}
	
	/**
	 * Purpose: Try to make QrCode with a invalid argument(mask must be 7 or less than 7)
	 * Input:  QrSegment.makeSegments("Hello, World!"), Ecc.HIGH, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 8, true
	 * Expected:
	 * 			throw IllegalArgumentException
	 */
	@Test(expected = IllegalArgumentException.class)
	public void testMaksOverRightBoundary() {
		QrCode.encodeSegments(qrSegments, Ecc.HIGH, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 8, true);
	}

}
