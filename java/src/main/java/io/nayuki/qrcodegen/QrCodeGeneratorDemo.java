/* 
 * QR Code generator demo (Java)
 * 
 * Run this command-line program with no arguments. The program creates/overwrites a bunch of
 * PNG and SVG files in the current working directory to demonstrate the creation of QR Codes.
 * 
 * Copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/qr-code-generator-library
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * - The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 * - The Software is provided "as is", without warranty of any kind, express or
 *   implied, including but not limited to the warranties of merchantability,
 *   fitness for a particular purpose and noninfringement. In no event shall the
 *   authors or copyright holders be liable for any claim, damages or other
 *   liability, whether in an action of contract, tort or otherwise, arising from,
 *   out of or in connection with the Software or the use or other dealings in the
 *   Software.
 */

package io.nayuki.qrcodegen;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;


public final class QrCodeGeneratorDemo {
	
	// The main application program.
	public static void main(String[] args) throws IOException {
		doBasicDemo();
		doVarietyDemo();
		doSegmentDemo();
		doMaskDemo();
	}
	
	
	
	/*---- Demo suite ----*/
	
	// Creates a single QR Code, then writes it to a PNG file and an SVG file.
	private static void doBasicDemo() throws IOException {
		String text = "Hello, world!";          // User-supplied Unicode text
		QrCode.Ecc errCorLvl = QrCode.Ecc.LOW;  // Error correction level
		
		QrCode qr = QrCode.encodeText(text, errCorLvl);  // Make the QR Code symbol
		
		BufferedImage img = qr.toImage(10, 4);          // Convert to bitmap image
		File imgFile = new File("hello-world-QR.png");  // File path for output
		ImageIO.write(img, "png", imgFile);             // Write image to file
		
		String svg = qr.toSvgString(4);  // Convert to SVG XML code
		try (Writer out = new OutputStreamWriter(
				new FileOutputStream("hello-world-QR.svg"),
				StandardCharsets.UTF_8)) {
			out.write(svg);  // Create/overwrite file and write SVG data
		}
	}
	
	
	// Creates a variety of QR Codes that exercise different features of the library, and writes each one to file.
	private static void doVarietyDemo() throws IOException {
		QrCode qr;
		
		// Numeric mode encoding (3.33 bits per digit)
		qr = QrCode.encodeText("314159265358979323846264338327950288419716939937510", QrCode.Ecc.MEDIUM);
		writePng(qr.toImage(13, 1), "pi-digits-QR.png");
		
		// Alphanumeric mode encoding (5.5 bits per character)
		qr = QrCode.encodeText("DOLLAR-AMOUNT:$39.87 PERCENTAGE:100.00% OPERATIONS:+-*/", QrCode.Ecc.HIGH);
		writePng(qr.toImage(10, 2), "alphanumeric-QR.png");
		
		// Unicode text as UTF-8
		qr = QrCode.encodeText("こんにちwa、世界！ αβγδ", QrCode.Ecc.QUARTILE);
		writePng(qr.toImage(10, 3), "unicode-QR.png");
		
		// Moderately large QR Code using longer text (from Lewis Carroll's Alice in Wonderland)
		qr = QrCode.encodeText(
			"Alice was beginning to get very tired of sitting by her sister on the bank, "
			+ "and of having nothing to do: once or twice she had peeped into the book her sister was reading, "
			+ "but it had no pictures or conversations in it, 'and what is the use of a book,' thought Alice "
			+ "'without pictures or conversations?' So she was considering in her own mind (as well as she could, "
			+ "for the hot day made her feel very sleepy and stupid), whether the pleasure of making a "
			+ "daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly "
			+ "a White Rabbit with pink eyes ran close by her.", QrCode.Ecc.HIGH);
		writePng(qr.toImage(6, 10), "alice-wonderland-QR.png");
	}
	
	
	// Creates QR Codes with manually specified segments for better compactness.
	private static void doSegmentDemo() throws IOException {
		QrCode qr;
		List<QrSegment> segs;
		
		// Illustration "silver"
		String silver0 = "THE SQUARE ROOT OF 2 IS 1.";
		String silver1 = "41421356237309504880168872420969807856967187537694807317667973799";
		qr = QrCode.encodeText(silver0 + silver1, QrCode.Ecc.LOW);
		writePng(qr.toImage(10, 3), "sqrt2-monolithic-QR.png");
		
		segs = Arrays.asList(
			QrSegment.makeAlphanumeric(silver0),
			QrSegment.makeNumeric(silver1));
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.LOW);
		writePng(qr.toImage(10, 3), "sqrt2-segmented-QR.png");
		
		// Illustration "golden"
		String golden0 = "Golden ratio φ = 1.";
		String golden1 = "6180339887498948482045868343656381177203091798057628621354486227052604628189024497072072041893911374";
		String golden2 = "......";
		qr = QrCode.encodeText(golden0 + golden1 + golden2, QrCode.Ecc.LOW);
		writePng(qr.toImage(8, 5), "phi-monolithic-QR.png");
		
		segs = Arrays.asList(
			QrSegment.makeBytes(golden0.getBytes(StandardCharsets.UTF_8)),
			QrSegment.makeNumeric(golden1),
			QrSegment.makeAlphanumeric(golden2));
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.LOW);
		writePng(qr.toImage(8, 5), "phi-segmented-QR.png");
		
		// Illustration "Madoka": kanji, kana, Greek, Cyrillic, full-width Latin characters
		String madoka = "「魔法少女まどか☆マギカ」って、　ИАИ　ｄｅｓｕ　κα？";
		qr = QrCode.encodeText(madoka, QrCode.Ecc.LOW);
		writePng(qr.toImage(9, 4), "madoka-utf8-QR.png");
		
		int[] kanjiChars = {  // Kanji mode encoding (13 bits per character)
			0x0035, 0x1002, 0x0FC0, 0x0AED, 0x0AD7,
			0x015C, 0x0147, 0x0129, 0x0059, 0x01BD,
			0x018D, 0x018A, 0x0036, 0x0141, 0x0144,
			0x0001, 0x0000, 0x0249, 0x0240, 0x0249,
			0x0000, 0x0104, 0x0105, 0x0113, 0x0115,
			0x0000, 0x0208, 0x01FF, 0x0008,
		};
		BitBuffer bb = new BitBuffer();
		for (int c : kanjiChars)
			bb.appendBits(c, 13);
		segs = Arrays.asList(new QrSegment(QrSegment.Mode.KANJI, kanjiChars.length, bb));
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.LOW);
		writePng(qr.toImage(9, 4), "madoka-kanji-QR.png");
	}
	
	
	// Creates QR Codes with the same size and contents but different mask patterns.
	private static void doMaskDemo() throws IOException {
		QrCode qr;
		List<QrSegment> segs;
		
		// Project Nayuki URL
		segs = QrSegment.makeSegments("https://www.nayuki.io/");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.HIGH, QrCode.MIN_VERSION, QrCode.MAX_VERSION, -1, true);  // Automatic mask
		writePng(qr.toImage(8, 6), "project-nayuki-automask-QR.png");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.HIGH, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 3, true);  // Force mask 3
		writePng(qr.toImage(8, 6), "project-nayuki-mask3-QR.png");
		
		// Chinese text as UTF-8
		segs = QrSegment.makeSegments("維基百科（Wikipedia，聆聽i/ˌwɪkᵻˈpiːdi.ə/）是一個自由內容、公開編輯且多語言的網路百科全書協作計畫");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.MEDIUM, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 0, true);  // Force mask 0
		writePng(qr.toImage(10, 3), "unicode-mask0-QR.png");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.MEDIUM, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 1, true);  // Force mask 1
		writePng(qr.toImage(10, 3), "unicode-mask1-QR.png");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.MEDIUM, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 5, true);  // Force mask 5
		writePng(qr.toImage(10, 3), "unicode-mask5-QR.png");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.MEDIUM, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 7, true);  // Force mask 7
		writePng(qr.toImage(10, 3), "unicode-mask7-QR.png");
	}
	
	
	
	/*---- Utilities ----*/
	
	// Helper function to reduce code duplication.
	private static void writePng(BufferedImage img, String filepath) throws IOException {
		ImageIO.write(img, "png", new File(filepath));
	}
	
}
