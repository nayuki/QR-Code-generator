/* 
 * QR Code generator demo (C)
 * 
 * Run this command-line program with no arguments. The program
 * computes a demonstration QR Codes and print it to the console.
 * 
 * Copyright (c) Project Nayuki
 * https://www.nayuki.io/page/qr-code-generator-library
 * 
 * (MIT License)
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

#include <stdio.h>
#include <stdlib.h>
#include "qrcodegen.h"


// Function prototypes
static void doBasicDemo(void);
static void doVarietyDemo(void);
static void printQr(const uint8_t qrcode[], int version);



// The main application program.
int main(void) {
	doBasicDemo();
	doVarietyDemo();
	return EXIT_SUCCESS;
}


// Creates a single QR Code, then prints it to the console.
static void doBasicDemo() {
	const char *text = "Hello, world!";  // User-supplied text
	enum qrcodegen_Ecc errCorLvl = qrcodegen_Ecc_LOW;  // Error correction level
	
	// Make and print the QR Code symbol
	uint8_t qrcode[qrcodegen_BUFFER_LEN_MAX];
	uint8_t tempBuffer[qrcodegen_BUFFER_LEN_MAX];
	int version = qrcodegen_encodeText(text, tempBuffer, qrcode, errCorLvl,
		qrcodegen_VERSION_MIN, qrcodegen_VERSION_MAX, qrcodegen_Mask_AUTO, true);
	if (version != 0)
		printQr(qrcode, version);
}


// Creates a variety of QR Codes that exercise different features of the library, and prints each one to the console.
static void doVarietyDemo() {
	// Project Nayuki URL
	{
		uint8_t qrcode[qrcodegen_BUFFER_LEN_MAX];
		uint8_t tempBuffer[qrcodegen_BUFFER_LEN_MAX];
		int version = qrcodegen_encodeText("https://www.nayuki.io/", tempBuffer, qrcode,
			qrcodegen_Ecc_HIGH, qrcodegen_VERSION_MIN, qrcodegen_VERSION_MAX, qrcodegen_Mask_3, true);
		if (version != 0)
			printQr(qrcode, version);
	}
	
	// Numeric mode encoding (3.33 bits per digit)
	{
		uint8_t qrcode[qrcodegen_BUFFER_LEN_MAX];
		uint8_t tempBuffer[qrcodegen_BUFFER_LEN_MAX];
		int version = qrcodegen_encodeText("314159265358979323846264338327950288419716939937510", tempBuffer, qrcode,
			qrcodegen_Ecc_MEDIUM, qrcodegen_VERSION_MIN, qrcodegen_VERSION_MAX, qrcodegen_Mask_AUTO, true);
		if (version != 0)
			printQr(qrcode, version);
	}
	
	// Alphanumeric mode encoding (5.5 bits per character)
	{
		uint8_t qrcode[qrcodegen_BUFFER_LEN_MAX];
		uint8_t tempBuffer[qrcodegen_BUFFER_LEN_MAX];
		int version = qrcodegen_encodeText("DOLLAR-AMOUNT:$39.87 PERCENTAGE:100.00% OPERATIONS:+-*/", tempBuffer, qrcode,
			qrcodegen_Ecc_HIGH, qrcodegen_VERSION_MIN, qrcodegen_VERSION_MAX, qrcodegen_Mask_AUTO, true);
		if (version != 0)
			printQr(qrcode, version);
	}
	
	// Unicode text as UTF-8, and different masks
	{
		const char *text = "\xE3\x81\x93\xE3\x82\x93\xE3\x81\xAB\xE3\x81\xA1wa\xE3\x80\x81\xE4\xB8\x96\xE7\x95\x8C\xEF\xBC\x81\x20\xCE\xB1\xCE\xB2\xCE\xB3\xCE\xB4";
		uint8_t qrcode[qrcodegen_BUFFER_LEN_MAX];
		uint8_t tempBuffer[qrcodegen_BUFFER_LEN_MAX];
		int version;
		
		version = qrcodegen_encodeText(text, tempBuffer, qrcode,
			qrcodegen_Ecc_QUARTILE, qrcodegen_VERSION_MIN, qrcodegen_VERSION_MAX, qrcodegen_Mask_0, true);
		if (version != 0)
			printQr(qrcode, version);
		
		version = qrcodegen_encodeText(text, tempBuffer, qrcode,
			qrcodegen_Ecc_QUARTILE, qrcodegen_VERSION_MIN, qrcodegen_VERSION_MAX, qrcodegen_Mask_1, true);
		if (version != 0)
			printQr(qrcode, version);
		
		version = qrcodegen_encodeText(text, tempBuffer, qrcode,
			qrcodegen_Ecc_QUARTILE, qrcodegen_VERSION_MIN, qrcodegen_VERSION_MAX, qrcodegen_Mask_5, true);
		if (version != 0)
			printQr(qrcode, version);
		
		version = qrcodegen_encodeText(text, tempBuffer, qrcode,
			qrcodegen_Ecc_QUARTILE, qrcodegen_VERSION_MIN, qrcodegen_VERSION_MAX, qrcodegen_Mask_7, true);
		if (version != 0)
			printQr(qrcode, version);
	}
	
	// Moderately large QR Code using longer text (from Lewis Carroll's Alice in Wonderland)
	{
		const char *text =
			"Alice was beginning to get very tired of sitting by her sister on the bank, "
			"and of having nothing to do: once or twice she had peeped into the book her sister was reading, "
			"but it had no pictures or conversations in it, 'and what is the use of a book,' thought Alice "
			"'without pictures or conversations?' So she was considering in her own mind (as well as she could, "
			"for the hot day made her feel very sleepy and stupid), whether the pleasure of making a "
			"daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly "
			"a White Rabbit with pink eyes ran close by her.";
		uint8_t qrcode[qrcodegen_BUFFER_LEN_MAX];
		uint8_t tempBuffer[qrcodegen_BUFFER_LEN_MAX];
		int version = qrcodegen_encodeText(text, tempBuffer, qrcode,
			qrcodegen_Ecc_HIGH, qrcodegen_VERSION_MIN, qrcodegen_VERSION_MAX, qrcodegen_Mask_AUTO, true);
		if (version != 0)
			printQr(qrcode, version);
	}
}


// Prints the given QR Code to the console.
static void printQr(const uint8_t qrcode[], int version) {
	int size = qrcodegen_getSize(version);
	int border = 4;
	for (int y = -border; y < size + border; y++) {
		for (int x = -border; x < size + border; x++) {
			fputs((qrcodegen_getModule(qrcode, version, x, y) ? "##" : "  "), stdout);
		}
		fputs("\n", stdout);
	}
}
