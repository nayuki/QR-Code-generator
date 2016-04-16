/* 
 * QR Code generator library (C++)
 * 
 * Copyright (c) 2016 Project Nayuki
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

#pragma once

#include <cstdint>
#include <vector>
#include "QrSegment.hpp"


namespace qrcodegen {

/* 
 * An appendable sequence of bits. Bits are packed in big endian within a byte.
 */
class BitBuffer final {
	
	/*---- Fields ----*/
private:
	
	std::vector<uint8_t> data;
	int bitLength;
	
	
	
	/*---- Constructor ----*/
public:
	
	// Creates an empty bit buffer (length 0).
	BitBuffer();
	
	
	
	/*---- Methods ----*/
public:
	
	// Returns the number of bits in the buffer, which is a non-negative value.
	int getBitLength() const;
	
	
	// Returns a copy of all bytes, padding up to the nearest byte.
	std::vector<uint8_t> getBytes() const;
	
	
	// Appends the given number of bits of the given value to this sequence.
	// If 0 <= len <= 31, then this requires 0 <= val < 2^len.
	void appendBits(uint32_t val, int len);
	
	
	// Appends the data of the given segment to this bit buffer.
	void appendData(const QrSegment &seg);
	
};

}
