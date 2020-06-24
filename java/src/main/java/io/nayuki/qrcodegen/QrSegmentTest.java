package io.nayuki.qrcodegen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class QrSegmentTest {

	@Test
	void testMakeBytes() {
		byte[] data = {1, 2, 3, 4};
		QrSegment.makeBytes(data);
	}

	@Test
	void testMakeNumeric() {
		QrSegment.makeNumeric("1234567890");
	}
	
	@Test
	void testMakeAlphanumeric() {
		QrSegment.makeAlphanumeric("123ABCDEF");
	}
	
	@Test
	void testEmptyStringToMakeSegment() {
		String text = "";
		QrSegment.makeSegments(text);
	}
	
	@Test
	void testMakeBytesToMakeSegment() {
		byte[] data = {1, 2, 3, 4};
		String text = new String(data);
		QrSegment.makeSegments(text);
	}
	
	@Test
	void testMakeNumericToMakeSegment() {
		QrSegment.makeSegments("123457890");
	}
	
	@Test
	void testMakeAlphanumericToMakeSegment() {
		QrSegment.makeSegments("123ABCDEF");
	}
	
	@Test
	void testMakeEciToUnderOfRange() {
		int assignValue = -1;
		QrSegment.makeEci(assignValue);
	}
	
	@Test
	void testMakeEciToRange1() {
		int assignValue = 1 << 3;
		QrSegment.makeEci(assignValue);
	}
	
	@Test
	void testMakeEciToRange2() {
		int assignValue = 1 << 10;
		QrSegment.makeEci(assignValue);
	}
	
	@Test
	void testMakeEciToRange3() {
		int assignValue = 1 << 15;
		QrSegment.makeEci(assignValue);
	}
	
	@Test
	void testMakeEciToOutOFRange() {
		int assignValue = 1 << 22;
		QrSegment.makeEci(assignValue);
	}
	
	@Test
	void testGetData() {
		QrSegment qrSegment = QrSegment.makeNumeric("000");
		qrSegment.getData();
	}
	
	@Test
	void testGetTotalBits() {
		List<QrSegment> segments = null;
		segments=QrSegment.makeSegments("123456");
		QrSegment.getTotalBits(segments, 12);
	}
}
