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
#include "BitBuffer.hpp"


namespace qrcodegen {

BitBuffer::BitBuffer() :
	data(),
	bitLength(0) {}


int BitBuffer::getBitLength() const {
	return bitLength;
}


std::vector<std::uint8_t> BitBuffer::getBytes() const {
	return data;
}


void BitBuffer::appendBits(std::uint32_t val, int len) {
	if (len < 0 || len > 32 || (len < 32 && (val >> len) != 0))
		throw "Value out of range";
	if (len > INT_MAX - bitLength)
		throw "Buffer too long";
	unsigned int newByteLen = ((unsigned int)bitLength + len + 7) / 8;
	while (data.size() < newByteLen)
		data.push_back(0);
	for (int i = len - 1; i >= 0; i--, bitLength++)  // Append bit by bit
		data.at(bitLength >> 3) |= ((val >> i) & 1) << (7 - (bitLength & 7));
}


void BitBuffer::appendData(const QrSegment &seg) {
	if (seg.bitLength > INT_MAX - bitLength)
		throw "Buffer too long";
	unsigned int newByteLen = ((unsigned int)bitLength + seg.bitLength + 7) / 8;
	while (data.size() < newByteLen)
		data.push_back(0);
	for (int i = 0; i < seg.bitLength; i++, bitLength++) {  // Append bit by bit
		int bit = (seg.data.at(i >> 3) >> (7 - (i & 7))) & 1;
		data.at(bitLength >> 3) |= bit << (7 - (bitLength & 7));
	}
}

}
