/* 
 * QR Code generator library (C++)
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

#include <cstddef>
#include "BitBuffer.hpp"
#include "QrSegment.hpp"


qrcodegen::QrSegment::Mode::Mode(int mode, int cc0, int cc1, int cc2) :
		modeBits(mode) {
	numBitsCharCount[0] = cc0;
	numBitsCharCount[1] = cc1;
	numBitsCharCount[2] = cc2;
}


int qrcodegen::QrSegment::Mode::numCharCountBits(int ver) const {
	if      ( 1 <= ver && ver <=  9)  return numBitsCharCount[0];
	else if (10 <= ver && ver <= 26)  return numBitsCharCount[1];
	else if (27 <= ver && ver <= 40)  return numBitsCharCount[2];
	else  throw "Version number out of range";
}


const qrcodegen::QrSegment::Mode qrcodegen::QrSegment::Mode::NUMERIC     (0x1, 10, 12, 14);
const qrcodegen::QrSegment::Mode qrcodegen::QrSegment::Mode::ALPHANUMERIC(0x2,  9, 11, 13);
const qrcodegen::QrSegment::Mode qrcodegen::QrSegment::Mode::BYTE        (0x4,  8, 16, 16);
const qrcodegen::QrSegment::Mode qrcodegen::QrSegment::Mode::KANJI       (0x8,  8, 10, 12);



qrcodegen::QrSegment qrcodegen::QrSegment::makeBytes(const std::vector<uint8_t> &data) {
	return QrSegment(Mode::BYTE, data.size(), data, data.size() * 8);
}


qrcodegen::QrSegment qrcodegen::QrSegment::makeNumeric(const char *digits) {
	BitBuffer bb;
	int accumData = 0;
	int accumCount = 0;
	int charCount = 0;
	for (; *digits != '\0'; digits++, charCount++) {
		char c = *digits;
		if (c < '0' || c > '9')
			throw "String contains non-numeric characters";
		accumData = accumData * 10 + (c - '0');
		accumCount++;
		if (accumCount == 3) {
			bb.appendBits(accumData, 10);
			accumData = 0;
			accumCount = 0;
		}
	}
	if (accumCount > 0)  // 1 or 2 digits remaining
		bb.appendBits(accumData, accumCount * 3 + 1);
	return QrSegment(Mode::NUMERIC, charCount, bb.getBytes(), bb.getBitLength());
}


qrcodegen::QrSegment qrcodegen::QrSegment::makeAlphanumeric(const char *text) {
	BitBuffer bb;
	int accumData = 0;
	int accumCount = 0;
	int charCount = 0;
	for (; *text != '\0'; text++, charCount++) {
		char c = *text;
		if (c < ' ' || c > 'Z')
			throw "String contains unencodable characters in alphanumeric mode";
		accumData = accumData * 45 + ALPHANUMERIC_ENCODING_TABLE[c - ' '];
		accumCount++;
		if (accumCount == 2) {
			bb.appendBits(accumData, 11);
			accumData = 0;
			accumCount = 0;
		}
	}
	if (accumCount > 0)  // 1 character remaining
		bb.appendBits(accumData, 6);
	return QrSegment(Mode::ALPHANUMERIC, charCount, bb.getBytes(), bb.getBitLength());
}


std::vector<qrcodegen::QrSegment> qrcodegen::QrSegment::makeSegments(const char *text) {
	// Select the most efficient segment encoding automatically
	std::vector<QrSegment> result;
	if (*text == '\0');  // Leave the vector empty
	else if (QrSegment::isNumeric(text))
		result.push_back(QrSegment::makeNumeric(text));
	else if (QrSegment::isAlphanumeric(text))
		result.push_back(QrSegment::makeAlphanumeric(text));
	else {
		std::vector<uint8_t> bytes;
		for (; *text != '\0'; text++)
			bytes.push_back(static_cast<uint8_t>(*text));
		result.push_back(QrSegment::makeBytes(bytes));
	}
	return result;
}


qrcodegen::QrSegment::QrSegment(const Mode &md, int numCh, const std::vector<uint8_t> &b, int bitLen) :
		mode(md),
		numChars(numCh),
		data(b),
		bitLength(bitLen) {
	if (numCh < 0 || bitLen < 0 || b.size() != static_cast<unsigned int>((bitLen + 7) / 8))
		throw "Invalid value";
}


int qrcodegen::QrSegment::getTotalBits(const std::vector<QrSegment> &segs, int version) {
	if (version < 1 || version > 40)
		throw "Version number out of range";
	int result = 0;
	for (size_t i = 0; i < segs.size(); i++) {
		const QrSegment &seg(segs.at(i));
		int ccbits = seg.mode.numCharCountBits(version);
		// Fail if segment length value doesn't fit in the length field's bit-width
		if (seg.numChars >= (1 << ccbits))
			return -1;
		result += 4 + ccbits + seg.bitLength;
	}
	return result;
}


bool qrcodegen::QrSegment::isAlphanumeric(const char *text) {
	for (; *text != '\0'; text++) {
		char c = *text;
		if (c < ' ' || c > 'Z' || ALPHANUMERIC_ENCODING_TABLE[c - ' '] == -1)
			return false;
	}
	return true;
}


bool qrcodegen::QrSegment::isNumeric(const char *text) {
	for (; *text != '\0'; text++) {
		char c = *text;
		if (c < '0' || c > '9')
			return false;
	}
	return true;
}


const int8_t qrcodegen::QrSegment::ALPHANUMERIC_ENCODING_TABLE[59] = {
	// SP,  !,  ",  #,  $,  %,  &,  ',  (,  ),  *,  +,  ,,  -,  .,  /,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9,  :,  ;,  <,  =,  >,  ?,  @,  // ASCII codes 32 to 64
	   36, -1, -1, -1, 37, 38, -1, -1, -1, -1, 39, 40, -1, 41, 42, 43,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 44, -1, -1, -1, -1, -1, -1,  // Array indices 0 to 32
	   10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,  // Array indices 33 to 58
	//  A,  B,  C,  D,  E,  F,  G,  H,  I,  J,  K,  L,  M,  N,  O,  P,  Q,  R,  S,  T,  U,  V,  W,  X,  Y,  Z,  // ASCII codes 65 to 90
};
