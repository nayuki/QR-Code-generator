/* 
 * QR Code generator output demo (TypeScript)
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

"use strict";


namespace app {
	
	let outputElem = document.getElementById("output") as HTMLElement;
	
	
	// The main application program.
	function main(): void {
		while (outputElem.firstChild !== null)
			outputElem.removeChild(outputElem.firstChild);
		doBasicDemo();
		doVarietyDemo();
		doSegmentDemo();
		doMaskDemo();
	}
	
	
	// Creates a single QR Code, then appends it to the document.
	function doBasicDemo(): void {
		appendHeading("Basic");
		const text: string = "Hello, world!";  // User-supplied Unicode text
		const errCorLvl: qrcodegen.QrCode.Ecc = qrcodegen.QrCode.Ecc.LOW;  // Error correction level
		const qr: qrcodegen.QrCode = qrcodegen.QrCode.encodeText(text, errCorLvl);  // Make the QR Code symbol
		drawCanvas(qr, 10, 4, "#FFFFFF", "#000000", appendCanvas("hello-world-QR"));  // Draw it on screen
	}
	
	
	// Creates a variety of QR Codes that exercise different features of the library, and appends each one to the document.
	function doVarietyDemo(): void {
		appendHeading("Variety");
		let qr: qrcodegen.QrCode;
		const QrCode = qrcodegen.QrCode;  // Abbreviation
		
		// Numeric mode encoding (3.33 bits per digit)
		qr = QrCode.encodeText("314159265358979323846264338327950288419716939937510", QrCode.Ecc.MEDIUM);
		drawCanvas(qr, 13, 1, "#FFFFFF", "#000000", appendCanvas("pi-digits-QR"));
		
		// Alphanumeric mode encoding (5.5 bits per character)
		qr = QrCode.encodeText("DOLLAR-AMOUNT:$39.87 PERCENTAGE:100.00% OPERATIONS:+-*/", QrCode.Ecc.HIGH);
		drawCanvas(qr, 10, 2, "#FFFFFF", "#000000", appendCanvas("alphanumeric-QR"));
		
		// Unicode text as UTF-8
		qr = QrCode.encodeText("\u3053\u3093\u306B\u3061wa\u3001\u4E16\u754C\uFF01 \u03B1\u03B2\u03B3\u03B4", QrCode.Ecc.QUARTILE);
		drawCanvas(qr, 10, 3, "#FFFFFF", "#000000", appendCanvas("unicode-QR"));
		
		// Moderately large QR Code using longer text (from Lewis Carroll's Alice in Wonderland)
		qr = QrCode.encodeText(
			"Alice was beginning to get very tired of sitting by her sister on the bank, "
			+ "and of having nothing to do: once or twice she had peeped into the book her sister was reading, "
			+ "but it had no pictures or conversations in it, 'and what is the use of a book,' thought Alice "
			+ "'without pictures or conversations?' So she was considering in her own mind (as well as she could, "
			+ "for the hot day made her feel very sleepy and stupid), whether the pleasure of making a "
			+ "daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly "
			+ "a White Rabbit with pink eyes ran close by her.", QrCode.Ecc.HIGH);
		drawCanvas(qr, 6, 10, "#FFFFFF", "#000000", appendCanvas("alice-wonderland-QR"));
	}
	
	
	// Creates QR Codes with manually specified segments for better compactness.
	function doSegmentDemo(): void {
		appendHeading("Segment");
		let qr: qrcodegen.QrCode;
		let segs: Array<qrcodegen.QrSegment>;
		const QrCode = qrcodegen.QrCode;  // Abbreviation
		const QrSegment = qrcodegen.QrSegment;  // Abbreviation
		
		// Illustration "silver"
		const silver0: string = "THE SQUARE ROOT OF 2 IS 1.";
		const silver1: string = "41421356237309504880168872420969807856967187537694807317667973799";
		qr = QrCode.encodeText(silver0 + silver1, QrCode.Ecc.LOW);
		drawCanvas(qr, 10, 3, "#FFFFFF", "#000000", appendCanvas("sqrt2-monolithic-QR"));
		
		segs = [
			QrSegment.makeAlphanumeric(silver0),
			QrSegment.makeNumeric(silver1)];
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.LOW);
		drawCanvas(qr, 10, 3, "#FFFFFF", "#000000", appendCanvas("sqrt2-segmented-QR"));
		
		// Illustration "golden"
		const golden0: string = "Golden ratio \u03C6 = 1.";
		const golden1: string = "6180339887498948482045868343656381177203091798057628621354486227052604628189024497072072041893911374";
		const golden2: string = "......";
		qr = QrCode.encodeText(golden0 + golden1 + golden2, QrCode.Ecc.LOW);
		drawCanvas(qr, 8, 5, "#FFFFFF", "#000000", appendCanvas("phi-monolithic-QR"));
		
		segs = [
			QrSegment.makeBytes(toUtf8ByteArray(golden0)),
			QrSegment.makeNumeric(golden1),
			QrSegment.makeAlphanumeric(golden2)];
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.LOW);
		drawCanvas(qr, 8, 5, "#FFFFFF", "#000000", appendCanvas("phi-segmented-QR"));
		
		// Illustration "Madoka": kanji, kana, Cyrillic, full-width Latin, Greek characters
		const madoka: string = "\u300C\u9B54\u6CD5\u5C11\u5973\u307E\u3069\u304B\u2606\u30DE\u30AE\u30AB\u300D\u3063\u3066\u3001\u3000\u0418\u0410\u0418\u3000\uFF44\uFF45\uFF53\uFF55\u3000\u03BA\u03B1\uFF1F";
		qr = QrCode.encodeText(madoka, QrCode.Ecc.LOW);
		drawCanvas(qr, 9, 4, "#FFFFE0", "#303080", appendCanvas("madoka-utf8-QR"));
		
		const kanjiCharBits: Array<number> = [  // Kanji mode encoding (13 bits per character)
			0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 0, 1,
			1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0,
			0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0,
			0, 1, 0, 1, 0, 1, 1, 1, 0, 1, 1, 0, 1,
			0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, 1, 1,
			0, 0, 0, 0, 1, 0, 1, 0, 1, 1, 1, 0, 0,
			0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1,
			0, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1,
			0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 0, 1,
			0, 0, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1,
			0, 0, 0, 0, 1, 1, 0, 0, 0, 1, 1, 0, 1,
			0, 0, 0, 0, 1, 1, 0, 0, 0, 1, 0, 1, 0,
			0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0,
			0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 1,
			0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0, 0,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1,
			0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0,
			0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 1,
			0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 1,
			0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 1, 0, 1,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0,
			0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0,
		];
		segs = [new QrSegment(QrSegment.Mode.KANJI, kanjiCharBits.length / 13, kanjiCharBits)];
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.LOW);
		drawCanvas(qr, 9, 4, "#E0F0FF", "#404040", appendCanvas("madoka-kanji-QR"));
	}
	
	
	// Creates QR Codes with the same size and contents but different mask patterns.
	function doMaskDemo(): void {
		appendHeading("Mask");
		let qr: qrcodegen.QrCode;
		let segs: Array<qrcodegen.QrSegment>;
		const QrCode = qrcodegen.QrCode;  // Abbreviation
		
		// Project Nayuki URL
		segs = qrcodegen.QrSegment.makeSegments("https://www.nayuki.io/");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.HIGH, QrCode.MIN_VERSION, QrCode.MAX_VERSION, -1, true);  // Automatic mask
		drawCanvas(qr, 8, 6, "#E0FFE0", "#206020", appendCanvas("project-nayuki-automask-QR"));
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.HIGH, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 3, true);  // Force mask 3
		drawCanvas(qr, 8, 6, "#FFE0E0", "#602020", appendCanvas("project-nayuki-mask3-QR"));
		
		// Chinese text as UTF-8
		segs = qrcodegen.QrSegment.makeSegments("\u7DAD\u57FA\u767E\u79D1\uFF08Wikipedia\uFF0C\u8046\u807Di/\u02CCw\u026Ak\u1D7B\u02C8pi\u02D0di.\u0259/\uFF09\u662F\u4E00"
			+ "\u500B\u81EA\u7531\u5167\u5BB9\u3001\u516C\u958B\u7DE8\u8F2F\u4E14\u591A\u8A9E\u8A00\u7684\u7DB2\u8DEF\u767E\u79D1\u5168\u66F8\u5354\u4F5C\u8A08\u756B");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.MEDIUM, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 0, true);  // Force mask 0
		drawCanvas(qr, 10, 3, "#FFFFFF", "#000000", appendCanvas("unicode-mask0-QR"));
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.MEDIUM, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 1, true);  // Force mask 1
		drawCanvas(qr, 10, 3, "#FFFFFF", "#000000", appendCanvas("unicode-mask1-QR"));
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.MEDIUM, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 5, true);  // Force mask 5
		drawCanvas(qr, 10, 3, "#FFFFFF", "#000000", appendCanvas("unicode-mask5-QR"));
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.MEDIUM, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 7, true);  // Force mask 7
		drawCanvas(qr, 10, 3, "#FFFFFF", "#000000", appendCanvas("unicode-mask7-QR"));
	}
	
	
	function appendHeading(text: string): void {
		let h2 = outputElem.appendChild(document.createElement("h2"));
		h2.textContent = text;
	}
	
	
	function appendCanvas(caption: string): HTMLCanvasElement {
		let p = outputElem.appendChild(document.createElement("p"));
		p.textContent = caption + ":";
		let result = document.createElement("canvas");
		outputElem.appendChild(result);
		return result;
	}
	
	
	// Draws the given QR Code, with the given module scale and border modules, onto the given HTML
	// canvas element. The canvas's width and height is resized to (qr.size + border * 2) * scale.
	// The drawn image is purely dark and light, and fully opaque.
	// The scale must be a positive integer and the border must be a non-negative integer.
	function drawCanvas(qr: qrcodegen.QrCode, scale: number, border: number, lightColor: string, darkColor: string, canvas: HTMLCanvasElement): void {
		if (scale <= 0 || border < 0)
			throw new RangeError("Value out of range");
		const width: number = (qr.size + border * 2) * scale;
		canvas.width = width;
		canvas.height = width;
		let ctx = canvas.getContext("2d") as CanvasRenderingContext2D;
		for (let y = -border; y < qr.size + border; y++) {
			for (let x = -border; x < qr.size + border; x++) {
				ctx.fillStyle = qr.getModule(x, y) ? darkColor : lightColor;
				ctx.fillRect((x + border) * scale, (y + border) * scale, scale, scale);
			}
		}
	}
	
	
	function toUtf8ByteArray(str: string): Array<number> {
		str = encodeURI(str);
		let result: Array<number> = [];
		for (let i = 0; i < str.length; i++) {
			if (str.charAt(i) != "%")
				result.push(str.charCodeAt(i));
			else {
				result.push(parseInt(str.substring(i + 1, i + 3), 16));
				i += 2;
			}
		}
		return result;
	}
	
	
	main();
	
}
