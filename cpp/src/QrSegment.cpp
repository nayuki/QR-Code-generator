/* 
 * QR Code generator library (C++)
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

#include <climits>
#include <cstddef>
#include <cstring>
#include "BitBuffer.hpp"
#include "QrSegment.hpp"

using std::uint8_t;
using std::vector;


namespace qrcodegen {

QrSegment::Mode::Mode(int mode, int cc0, int cc1, int cc2) :
		modeBits(mode) {
	numBitsCharCount[0] = cc0;
	numBitsCharCount[1] = cc1;
	numBitsCharCount[2] = cc2;
}


int QrSegment::Mode::numCharCountBits(int ver) const {
	if      ( 1 <= ver && ver <=  9)  return numBitsCharCount[0];
	else if (10 <= ver && ver <= 26)  return numBitsCharCount[1];
	else if (27 <= ver && ver <= 40)  return numBitsCharCount[2];
	else  throw "Version number out of range";
}


const QrSegment::Mode QrSegment::Mode::NUMERIC     (0x1, 10, 12, 14);
const QrSegment::Mode QrSegment::Mode::ALPHANUMERIC(0x2,  9, 11, 13);
const QrSegment::Mode QrSegment::Mode::BYTE        (0x4,  8, 16, 16);
const QrSegment::Mode QrSegment::Mode::KANJI       (0x8,  8, 10, 12);
const QrSegment::Mode QrSegment::Mode::ECI         (0x7,  0,  0,  0);



QrSegment QrSegment::makeBytes(const vector<uint8_t> &data) {
	if (data.size() >= (unsigned int)INT_MAX / 8)
		throw "Buffer too long";
	return QrSegment(Mode::BYTE, (int)data.size(), data, (int)data.size() * 8);
}


QrSegment QrSegment::makeNumeric(const char *digits) {
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


QrSegment QrSegment::makeAlphanumeric(const char *text) {
	BitBuffer bb;
	int accumData = 0;
	int accumCount = 0;
	int charCount = 0;
	for (; *text != '\0'; text++, charCount++) {
		const char *temp = std::strchr(ALPHANUMERIC_CHARSET, *text);
		if (temp == nullptr)
			throw "String contains unencodable characters in alphanumeric mode";
		accumData = accumData * 45 + (temp - ALPHANUMERIC_CHARSET);
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


vector<QrSegment> QrSegment::makeSegments(const char *text) {
	// Select the most efficient segment encoding automatically
	vector<QrSegment> result;
	if (*text == '\0');  // Leave the vector empty
	else if (QrSegment::isNumeric(text))
		result.push_back(QrSegment::makeNumeric(text));
	else if (QrSegment::isAlphanumeric(text))
		result.push_back(QrSegment::makeAlphanumeric(text));
	else {
		vector<uint8_t> bytes;
		for (; *text != '\0'; text++)
			bytes.push_back(static_cast<uint8_t>(*text));
		result.push_back(QrSegment::makeBytes(bytes));
	}
	return result;
}


QrSegment QrSegment::makeEci(long assignVal) {
	vector<uint8_t> data;
	if (0 <= assignVal && assignVal < (1 << 7))
		data = {static_cast<uint8_t>(assignVal)};
	else if ((1 << 7) <= assignVal && assignVal < (1 << 14))
		data = {static_cast<uint8_t>(0x80 | (assignVal >> 8)), static_cast<uint8_t>(assignVal)};
	else if ((1 << 14) <= assignVal && assignVal < 999999L)
		data = {static_cast<uint8_t>(0xC0 | (assignVal >> 16)), static_cast<uint8_t>(assignVal >> 8), static_cast<uint8_t>(assignVal)};
	else
		throw "ECI assignment value out of range";
	return QrSegment(Mode::ECI, 0, data, data.size() * 8);
}


QrSegment::QrSegment(const Mode &md, int numCh, const vector<uint8_t> &b, int bitLen) :
		mode(md),
		numChars(numCh),
		data(b),
		bitLength(bitLen) {
	if (numCh < 0 || bitLen < 0 || b.size() != static_cast<unsigned int>((bitLen + 7) / 8))
		throw "Invalid value";
}


int QrSegment::getTotalBits(const vector<QrSegment> &segs, int version) {
	if (version < 1 || version > 40)
		throw "Version number out of range";
	int result = 0;
	for (std::size_t i = 0; i < segs.size(); i++) {
		const QrSegment &seg(segs.at(i));
		int ccbits = seg.mode.numCharCountBits(version);
		// Fail if segment length value doesn't fit in the length field's bit-width
		if (seg.numChars >= (1L << ccbits) || seg.bitLength > INT16_MAX)
			return -1;
		long temp = (long)result + 4 + ccbits + seg.bitLength;
		if (temp > INT_MAX)
			return -1;
		result = temp;
	}
	return result;
}


bool QrSegment::isAlphanumeric(const char *text) {
	for (; *text != '\0'; text++) {
		if (std::strchr(ALPHANUMERIC_CHARSET, *text) == nullptr)
			return false;
	}
	return true;
}


bool QrSegment::isNumeric(const char *text) {
	for (; *text != '\0'; text++) {
		char c = *text;
		if (c < '0' || c > '9')
			return false;
	}
	return true;
}


const char *QrSegment::ALPHANUMERIC_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";

}
