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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.imageio.ImageIO;
import io.nayuki.qrcodegen.QrCode;
import io.nayuki.qrcodegen.QrSegment;
import io.nayuki.qrcodegen.QrSegmentAdvanced;


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
		
		BufferedImage img = toImage(qr, 10, 4);          // Convert to bitmap image
		File imgFile = new File("hello-world-QR.png");   // File path for output
		ImageIO.write(img, "png", imgFile);              // Write image to file
		
		String svg = toSvgString(qr, 4, "#FFFFFF", "#000000");  // Convert to SVG XML code
		File svgFile = new File("hello-world-QR.svg");          // File path for output
		Files.write(svgFile.toPath(),                           // Write image to file
			svg.getBytes(StandardCharsets.UTF_8));
	}
	
	
	// Creates a variety of QR Codes that exercise different features of the library, and writes each one to file.
	private static void doVarietyDemo() throws IOException {
		QrCode qr;
		
		// Numeric mode encoding (3.33 bits per digit)
		qr = QrCode.encodeText("314159265358979323846264338327950288419716939937510", QrCode.Ecc.MEDIUM);
		writePng(toImage(qr, 13, 1), "pi-digits-QR.png");
		
		// Alphanumeric mode encoding (5.5 bits per character)
		qr = QrCode.encodeText("DOLLAR-AMOUNT:$39.87 PERCENTAGE:100.00% OPERATIONS:+-*/", QrCode.Ecc.HIGH);
		writePng(toImage(qr, 10, 2), "alphanumeric-QR.png");
		
		// Unicode text as UTF-8
		qr = QrCode.encodeText("こんにちwa、世界！ αβγδ", QrCode.Ecc.QUARTILE);
		writePng(toImage(qr, 10, 3), "unicode-QR.png");
		
		// Moderately large QR Code using longer text (from Lewis Carroll's Alice in Wonderland)
		qr = QrCode.encodeText(
			"Alice was beginning to get very tired of sitting by her sister on the bank, "
			+ "and of having nothing to do: once or twice she had peeped into the book her sister was reading, "
			+ "but it had no pictures or conversations in it, 'and what is the use of a book,' thought Alice "
			+ "'without pictures or conversations?' So she was considering in her own mind (as well as she could, "
			+ "for the hot day made her feel very sleepy and stupid), whether the pleasure of making a "
			+ "daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly "
			+ "a White Rabbit with pink eyes ran close by her.", QrCode.Ecc.HIGH);
		writePng(toImage(qr, 6, 10), "alice-wonderland-QR.png");
	}
	
	
	// Creates QR Codes with manually specified segments for better compactness.
	private static void doSegmentDemo() throws IOException {
		QrCode qr;
		List<QrSegment> segs;
		
		// Illustration "silver"
		String silver0 = "THE SQUARE ROOT OF 2 IS 1.";
		String silver1 = "41421356237309504880168872420969807856967187537694807317667973799";
		qr = QrCode.encodeText(silver0 + silver1, QrCode.Ecc.LOW);
		writePng(toImage(qr, 10, 3), "sqrt2-monolithic-QR.png");
		
		segs = Arrays.asList(
			QrSegment.makeAlphanumeric(silver0),
			QrSegment.makeNumeric(silver1));
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.LOW);
		writePng(toImage(qr, 10, 3), "sqrt2-segmented-QR.png");
		
		// Illustration "golden"
		String golden0 = "Golden ratio φ = 1.";
		String golden1 = "6180339887498948482045868343656381177203091798057628621354486227052604628189024497072072041893911374";
		String golden2 = "......";
		qr = QrCode.encodeText(golden0 + golden1 + golden2, QrCode.Ecc.LOW);
		writePng(toImage(qr, 8, 5), "phi-monolithic-QR.png");
		
		segs = Arrays.asList(
			QrSegment.makeBytes(golden0.getBytes(StandardCharsets.UTF_8)),
			QrSegment.makeNumeric(golden1),
			QrSegment.makeAlphanumeric(golden2));
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.LOW);
		writePng(toImage(qr, 8, 5), "phi-segmented-QR.png");
		
		// Illustration "Madoka": kanji, kana, Cyrillic, full-width Latin, Greek characters
		String madoka = "「魔法少女まどか☆マギカ」って、　ИАИ　ｄｅｓｕ　κα？";
		qr = QrCode.encodeText(madoka, QrCode.Ecc.LOW);
		writePng(toImage(qr, 9, 4, 0xFFFFE0, 0x303080), "madoka-utf8-QR.png");
		
		segs = Arrays.asList(QrSegmentAdvanced.makeKanji(madoka));
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.LOW);
		writePng(toImage(qr, 9, 4, 0xE0F0FF, 0x404040), "madoka-kanji-QR.png");
	}
	
	
	// Creates QR Codes with the same size and contents but different mask patterns.
	private static void doMaskDemo() throws IOException {
		QrCode qr;
		List<QrSegment> segs;
		
		// Project Nayuki URL
		segs = QrSegment.makeSegments("https://www.nayuki.io/");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.HIGH, QrCode.MIN_VERSION, QrCode.MAX_VERSION, -1, true);  // Automatic mask
		writePng(toImage(qr, 8, 6, 0xE0FFE0, 0x206020), "project-nayuki-automask-QR.png");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.HIGH, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 3, true);  // Force mask 3
		writePng(toImage(qr, 8, 6, 0xFFE0E0, 0x602020), "project-nayuki-mask3-QR.png");
		
		// Chinese text as UTF-8
		segs = QrSegment.makeSegments("維基百科（Wikipedia，聆聽i/ˌwɪkᵻˈpiːdi.ə/）是一個自由內容、公開編輯且多語言的網路百科全書協作計畫");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.MEDIUM, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 0, true);  // Force mask 0
		writePng(toImage(qr, 10, 3), "unicode-mask0-QR.png");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.MEDIUM, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 1, true);  // Force mask 1
		writePng(toImage(qr, 10, 3), "unicode-mask1-QR.png");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.MEDIUM, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 5, true);  // Force mask 5
		writePng(toImage(qr, 10, 3), "unicode-mask5-QR.png");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.MEDIUM, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 7, true);  // Force mask 7
		writePng(toImage(qr, 10, 3), "unicode-mask7-QR.png");
	}
	
	
	
	/*---- Utilities ----*/
	
	private static BufferedImage toImage(QrCode qr, int scale, int border) {
		return toImage(qr, scale, border, 0xFFFFFF, 0x000000);
	}
	
	
	/**
	 * Returns a raster image depicting the specified QR Code, with
	 * the specified module scale, border modules, and module colors.
	 * <p>For example, scale=10 and border=4 means to pad the QR Code with 4 light border
	 * modules on all four sides, and use 10&#xD7;10 pixels to represent each module.
	 * @param qr the QR Code to render (not {@code null})
	 * @param scale the side length (measured in pixels, must be positive) of each module
	 * @param border the number of border modules to add, which must be non-negative
	 * @param lightColor the color to use for light modules, in 0xRRGGBB format
	 * @param darkColor the color to use for dark modules, in 0xRRGGBB format
	 * @return a new image representing the QR Code, with padding and scaling
	 * @throws NullPointerException if the QR Code is {@code null}
	 * @throws IllegalArgumentException if the scale or border is out of range, or if
	 * {scale, border, size} cause the image dimensions to exceed Integer.MAX_VALUE
	 */
	private static BufferedImage toImage(QrCode qr, int scale, int border, int lightColor, int darkColor) {
		Objects.requireNonNull(qr);
		if (scale <= 0 || border < 0)
			throw new IllegalArgumentException("Value out of range");
		if (border > Integer.MAX_VALUE / 2 || qr.size + border * 2L > Integer.MAX_VALUE / scale)
			throw new IllegalArgumentException("Scale or border too large");
		
		BufferedImage result = new BufferedImage((qr.size + border * 2) * scale, (qr.size + border * 2) * scale, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < result.getHeight(); y++) {
			for (int x = 0; x < result.getWidth(); x++) {
				boolean color = qr.getModule(x / scale - border, y / scale - border);
				result.setRGB(x, y, color ? darkColor : lightColor);
			}
		}
		return result;
	}
	
	
	// Helper function to reduce code duplication.
	private static void writePng(BufferedImage img, String filepath) throws IOException {
		ImageIO.write(img, "png", new File(filepath));
	}
	
	
	/**
	 * Returns a string of SVG code for an image depicting the specified QR Code, with the specified
	 * number of border modules. The string always uses Unix newlines (\n), regardless of the platform.
	 * @param qr the QR Code to render (not {@code null})
	 * @param border the number of border modules to add, which must be non-negative
	 * @param lightColor the color to use for light modules, in any format supported by CSS, not {@code null}
	 * @param darkColor the color to use for dark modules, in any format supported by CSS, not {@code null}
	 * @return a string representing the QR Code as an SVG XML document
	 * @throws NullPointerException if any object is {@code null}
	 * @throws IllegalArgumentException if the border is negative
	 */
	private static String toSvgString(QrCode qr, int border, String lightColor, String darkColor) {
		Objects.requireNonNull(qr);
		Objects.requireNonNull(lightColor);
		Objects.requireNonNull(darkColor);
		if (border < 0)
			throw new IllegalArgumentException("Border must be non-negative");
		long brd = border;
		StringBuilder sb = new StringBuilder()
			.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n")
			.append(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 %1$d %1$d\" stroke=\"none\">\n",
				qr.size + brd * 2))
			.append("\t<rect width=\"100%\" height=\"100%\" fill=\"" + lightColor + "\"/>\n")
			.append("\t<path d=\"");
		for (int y = 0; y < qr.size; y++) {
			for (int x = 0; x < qr.size; x++) {
				if (qr.getModule(x, y)) {
					if (x != 0 || y != 0)
						sb.append(" ");
					sb.append(String.format("M%d,%dh1v1h-1z", x + brd, y + brd));
				}
			}
		}
		return sb
			.append("\" fill=\"" + darkColor + "\"/>\n")
			.append("</svg>\n")
			.toString();
	}
	
}
