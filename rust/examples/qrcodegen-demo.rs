/* 
 * QR Code generator demo (Rust)
 * 
 * Run this command-line program with no arguments. The program computes a bunch of demonstration
 * QR Codes and prints them to the console. Also, the SVG code for one QR Code is printed as a sample.
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

extern crate qrcodegen;
use qrcodegen::Mask;
use qrcodegen::QrCode;
use qrcodegen::QrCodeEcc;
use qrcodegen::QrSegment;
use qrcodegen::QrCode_MAX_VERSION;
use qrcodegen::QrCode_MIN_VERSION;


// The main application program.
fn main() {
	do_basic_demo();
	do_variety_demo();
	do_segment_demo();
	do_mask_demo();
}



/*---- Demo suite ----*/

// Creates a single QR Code, then prints it to the console.
fn do_basic_demo() {
	let text: &'static str = "Hello, world!";  // User-supplied Unicode text
	let errcorlvl: QrCodeEcc = QrCodeEcc::Low;  // Error correction level
	
	// Make and print the QR Code symbol
	let qr: QrCode = QrCode::encode_text(text, errcorlvl).unwrap();
	print_qr(&qr);
	println!("{}", qr.to_svg_string(4));
}


// Creates a variety of QR Codes that exercise different features of the library, and prints each one to the console.
fn do_variety_demo() {
	// Numeric mode encoding (3.33 bits per digit)
	let qr = QrCode::encode_text("314159265358979323846264338327950288419716939937510", QrCodeEcc::Medium).unwrap();
	print_qr(&qr);
	
	// Alphanumeric mode encoding (5.5 bits per character)
	let qr = QrCode::encode_text("DOLLAR-AMOUNT:$39.87 PERCENTAGE:100.00% OPERATIONS:+-*/", QrCodeEcc::High).unwrap();
	print_qr(&qr);
	
	// Unicode text as UTF-8
	let qr = QrCode::encode_text("こんにちwa、世界！ αβγδ", QrCodeEcc::Quartile).unwrap();
	print_qr(&qr);
	
	// Moderately large QR Code using longer text (from Lewis Carroll's Alice in Wonderland)
	let qr = QrCode::encode_text(concat!(
		"Alice was beginning to get very tired of sitting by her sister on the bank, ",
		"and of having nothing to do: once or twice she had peeped into the book her sister was reading, ",
		"but it had no pictures or conversations in it, 'and what is the use of a book,' thought Alice ",
		"'without pictures or conversations?' So she was considering in her own mind (as well as she could, ",
		"for the hot day made her feel very sleepy and stupid), whether the pleasure of making a ",
		"daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly ",
		"a White Rabbit with pink eyes ran close by her."), QrCodeEcc::High).unwrap();
	print_qr(&qr);
}


// Creates QR Codes with manually specified segments for better compactness.
fn do_segment_demo() {
	// Illustration "silver"
	let silver0 = "THE SQUARE ROOT OF 2 IS 1.";
	let silver1 = "41421356237309504880168872420969807856967187537694807317667973799";
	let qr = QrCode::encode_text(&[silver0, silver1].concat(), QrCodeEcc::Low).unwrap();
	print_qr(&qr);
	
	let segs = vec![
		QrSegment::make_alphanumeric(&to_chars(silver0)),
		QrSegment::make_numeric(&to_chars(silver1)),
	];
	let qr = QrCode::encode_segments(&segs, QrCodeEcc::Low).unwrap();
	print_qr(&qr);
	
	// Illustration "golden"
	let golden0 = "Golden ratio φ = 1.";
	let golden1 = "6180339887498948482045868343656381177203091798057628621354486227052604628189024497072072041893911374";
	let golden2 = "......";
	let qr = QrCode::encode_text(&[golden0, golden1, golden2].concat(), QrCodeEcc::Low).unwrap();
	print_qr(&qr);
	
	let segs = vec![
		QrSegment::make_bytes(golden0.as_bytes()),
		QrSegment::make_numeric(&to_chars(golden1)),
		QrSegment::make_alphanumeric(&to_chars(golden2)),
	];
	let qr = QrCode::encode_segments(&segs, QrCodeEcc::Low).unwrap();
	print_qr(&qr);
	
	// Illustration "Madoka": kanji, kana, Greek, Cyrillic, full-width Latin characters
	let madoka = "「魔法少女まどか☆マギカ」って、　ИАИ　ｄｅｓｕ　κα？";
	let qr = QrCode::encode_text(madoka, QrCodeEcc::Low).unwrap();
	print_qr(&qr);
	
	let kanjichars: Vec<u32> = vec![  // Kanji mode encoding (13 bits per character)
		0x0035, 0x1002, 0x0FC0, 0x0AED, 0x0AD7,
		0x015C, 0x0147, 0x0129, 0x0059, 0x01BD,
		0x018D, 0x018A, 0x0036, 0x0141, 0x0144,
		0x0001, 0x0000, 0x0249, 0x0240, 0x0249,
		0x0000, 0x0104, 0x0105, 0x0113, 0x0115,
		0x0000, 0x0208, 0x01FF, 0x0008,
	];
	let mut bb = qrcodegen::BitBuffer(Vec::new());
	for c in &kanjichars {
		bb.append_bits(*c, 13);
	}
	let segs = vec![
		QrSegment::new(qrcodegen::QrSegmentMode::Kanji, kanjichars.len(), bb.0),
	];
	let qr = QrCode::encode_segments(&segs, QrCodeEcc::Low).unwrap();
	print_qr(&qr);
}


// Creates QR Codes with the same size and contents but different mask patterns.
fn do_mask_demo() {
	// Project Nayuki URL
	let segs = QrSegment::make_segments(&to_chars("https://www.nayuki.io/"));
	let qr = QrCode::encode_segments_advanced(&segs, QrCodeEcc::High, QrCode_MIN_VERSION, QrCode_MAX_VERSION, None, true).unwrap();  // Automatic mask
	print_qr(&qr);
	let qr = QrCode::encode_segments_advanced(&segs, QrCodeEcc::High, QrCode_MIN_VERSION, QrCode_MAX_VERSION, Some(Mask::new(3)), true).unwrap();  // Force mask 3
	print_qr(&qr);
	
	// Chinese text as UTF-8
	let segs = QrSegment::make_segments(&to_chars("維基百科（Wikipedia，聆聽i/ˌwɪkᵻˈpiːdi.ə/）是一個自由內容、公開編輯且多語言的網路百科全書協作計畫"));
	let qr = QrCode::encode_segments_advanced(&segs, QrCodeEcc::Medium, QrCode_MIN_VERSION, QrCode_MAX_VERSION, Some(Mask::new(0)), true).unwrap();  // Force mask 0
	print_qr(&qr);
	let qr = QrCode::encode_segments_advanced(&segs, QrCodeEcc::Medium, QrCode_MIN_VERSION, QrCode_MAX_VERSION, Some(Mask::new(1)), true).unwrap();  // Force mask 1
	print_qr(&qr);
	let qr = QrCode::encode_segments_advanced(&segs, QrCodeEcc::Medium, QrCode_MIN_VERSION, QrCode_MAX_VERSION, Some(Mask::new(5)), true).unwrap();  // Force mask 5
	print_qr(&qr);
	let qr = QrCode::encode_segments_advanced(&segs, QrCodeEcc::Medium, QrCode_MIN_VERSION, QrCode_MAX_VERSION, Some(Mask::new(7)), true).unwrap();  // Force mask 7
	print_qr(&qr);
}



/*---- Utilities ----*/

// Prints the given QrCode object to the console.
fn print_qr(qr: &QrCode) {
	let border: i32 = 4;
	for y in -border .. qr.size() + border {
		for x in -border .. qr.size() + border {
			let c: char = if qr.get_module(x, y) { '█' } else { ' ' };
			print!("{0}{0}", c);
		}
		println!();
	}
	println!();
}


// Converts the given borrowed string slice to a new character vector.
fn to_chars(text: &str) -> Vec<char> {
	text.chars().collect()
}
