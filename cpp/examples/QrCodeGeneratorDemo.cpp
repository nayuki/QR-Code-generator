/* 
 * QR Code generator demo (C++)
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

#include <cstdint>
#include <cstdlib>
#include <iostream>
#include <string>
#include <vector>
#include "QrCode.hpp"

using std::uint8_t;
using qrcodegen::QrCode;
using qrcodegen::QrSegment;


// Function prototypes
static void doBasicDemo();
static void doVarietyDemo();
static void doSegmentDemo();
static void printQr(const QrCode &qr);



// The main application program.
int main() {
	doBasicDemo();
	doVarietyDemo();
	doSegmentDemo();
	return EXIT_SUCCESS;
}


// Creates a single QR Code, then prints it to the console.
static void doBasicDemo() {
	const char *text = "Hello, world!";  // User-supplied text
	const QrCode::Ecc &errCorLvl = QrCode::Ecc::LOW;  // Error correction level
	
	// Make and print the QR Code symbol
	const QrCode qr = QrCode::encodeText(text, errCorLvl);
	std::cout << qr.toSvgString(4) << std::endl;
	printQr(qr);
}


// Creates a variety of QR Codes that exercise different features of the library, and prints each one to the console.
static void doVarietyDemo() {
	// Project Nayuki URL
	const QrCode qr0 = QrCode::encodeText("https://www.nayuki.io/", QrCode::Ecc::HIGH);
	printQr(QrCode(qr0, 3));  // Change mask, forcing to mask #3
	
	// Numeric mode encoding (3.33 bits per digit)
	const QrCode qr1 = QrCode::encodeText("314159265358979323846264338327950288419716939937510", QrCode::Ecc::MEDIUM);
	printQr(qr1);
	
	// Alphanumeric mode encoding (5.5 bits per character)
	const QrCode qr2 = QrCode::encodeText("DOLLAR-AMOUNT:$39.87 PERCENTAGE:100.00% OPERATIONS:+-*/", QrCode::Ecc::HIGH);
	printQr(qr2);
	
	// Unicode text as UTF-8, and different masks
	const QrCode qr3 = QrCode::encodeText("\xE3\x81\x93\xE3\x82\x93\xE3\x81\xAB\xE3\x81\xA1wa\xE3\x80\x81\xE4\xB8\x96\xE7\x95\x8C\xEF\xBC\x81\x20\xCE\xB1\xCE\xB2\xCE\xB3\xCE\xB4", QrCode::Ecc::QUARTILE);
	printQr(QrCode(qr3, 0));
	printQr(QrCode(qr3, 1));
	printQr(QrCode(qr3, 5));
	printQr(QrCode(qr3, 7));
	
	// Moderately large QR Code using longer text (from Lewis Carroll's Alice in Wonderland)
	const QrCode qr4 = QrCode::encodeText(
		"Alice was beginning to get very tired of sitting by her sister on the bank, "
		"and of having nothing to do: once or twice she had peeped into the book her sister was reading, "
		"but it had no pictures or conversations in it, 'and what is the use of a book,' thought Alice "
		"'without pictures or conversations?' So she was considering in her own mind (as well as she could, "
		"for the hot day made her feel very sleepy and stupid), whether the pleasure of making a "
		"daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly "
		"a White Rabbit with pink eyes ran close by her.", QrCode::Ecc::HIGH);
	printQr(qr4);
}


// Creates QR Codes with manually specified segments for better compactness.
static void doSegmentDemo() {
	// Illustration "silver"
	const char *silver0 = "THE SQUARE ROOT OF 2 IS 1.";
	const char *silver1 = "41421356237309504880168872420969807856967187537694807317667973799";
	const QrCode qr0 = QrCode::encodeText(
		(std::string(silver0) + silver1).c_str(),
		QrCode::Ecc::LOW);
	printQr(qr0);
	
	std::vector<QrSegment> segs;
	segs.push_back(QrSegment::makeAlphanumeric(silver0));
	segs.push_back(QrSegment::makeNumeric(silver1));
	const QrCode qr1 = QrCode::encodeSegments(segs, QrCode::Ecc::LOW);
	printQr(qr1);
	
	// Illustration "golden"
	const char *golden0 = "Golden ratio \xCF\x86 = 1.";
	const char *golden1 = "6180339887498948482045868343656381177203091798057628621354486227052604628189024497072072041893911374";
	const char *golden2 = "......";
	const QrCode qr2 = QrCode::encodeText(
		(std::string(golden0) + golden1 + golden2).c_str(),
		QrCode::Ecc::LOW);
	printQr(qr2);
	
	segs.clear();
	std::vector<uint8_t> bytes;
	for (const char *temp = golden0; *temp != '\0'; temp++)
		bytes.push_back(static_cast<uint8_t>(*temp));
	segs.push_back(QrSegment::makeBytes(bytes));
	segs.push_back(QrSegment::makeNumeric(golden1));
	segs.push_back(QrSegment::makeAlphanumeric(golden2));
	const QrCode qr3 = QrCode::encodeSegments(segs, QrCode::Ecc::LOW);
	printQr(qr3);
	
	// Illustration "Madoka": kanji, kana, Greek, Cyrillic, full-width Latin characters
	const char *madoka = "\xE3\x80\x8C\xE9\xAD\x94\xE6\xB3\x95\xE5\xB0\x91\xE5\xA5\xB3\xE3\x81\xBE\xE3\x81\xA9\xE3\x81\x8B\xE2\x98\x86\xE3\x83\x9E\xE3\x82\xAE\xE3\x82\xAB\xE3\x80\x8D\xE3\x81\xA3\xE3\x81\xA6\xE3\x80\x81\xE3\x80\x80\xD0\x98\xD0\x90\xD0\x98\xE3\x80\x80\xEF\xBD\x84\xEF\xBD\x85\xEF\xBD\x93\xEF\xBD\x95\xE3\x80\x80\xCE\xBA\xCE\xB1\xEF\xBC\x9F";
	const QrCode qr4 = QrCode::encodeText(madoka, QrCode::Ecc::LOW);
	printQr(qr4);
	
	const std::vector<uint8_t> packedKanjiData{  // Kanji mode encoding (13 bits per character)
		0x01, 0xAC, 0x00, 0x9F, 0x80, 0xAE, 0xD5, 0x6B, 0x85, 0x70,
		0x28, 0xE1, 0x29, 0x02, 0xC8, 0x6F, 0x43, 0x1A, 0x18, 0xA0,
		0x1B, 0x05, 0x04, 0x28, 0x80, 0x01, 0x00, 0x00, 0x92, 0x44,
		0x80, 0x24, 0x90, 0x00, 0x04, 0x10, 0x20, 0xA1, 0x13, 0x08,
		0xA8, 0x00, 0x04, 0x10, 0x1F, 0xF0, 0x04, 0x00,
	};
	segs.clear();
	segs.push_back(QrSegment(QrSegment::Mode::KANJI, 29, packedKanjiData, 377));
	const QrCode qr5 = QrCode::encodeSegments(segs, QrCode::Ecc::LOW);
	printQr(qr5);
}


// Prints the given QR Code to the console.
static void printQr(const QrCode &qr) {
	int border = 4;
	for (int y = -border; y < qr.size + border; y++) {
		for (int x = -border; x < qr.size + border; x++) {
			std::cout << (qr.getModule(x, y) == 1 ? "##" : "  ");
		}
		std::cout << std::endl;
	}
}
