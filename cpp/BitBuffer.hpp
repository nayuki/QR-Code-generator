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
	
	private: std::vector<std::uint8_t> data;
	private: int bitLength;
	
	
	
	/*---- Constructor ----*/
	
	// Creates an empty bit buffer (length 0).
	public: BitBuffer();
	
	
	
	/*---- Methods ----*/
	
	// Returns the number of bits in the buffer, which is a non-negative value.
	public: int getBitLength() const;
	
	
	// Returns a copy of all bytes, padding up to the nearest byte.
	public: std::vector<std::uint8_t> getBytes() const;
	
	
	// Appends the given number of bits of the given value to this sequence.
	// If 0 <= len <= 31, then this requires 0 <= val < 2^len.
	public: void appendBits(std::uint32_t val, int len);
	
	
	// Appends the data of the given segment to this bit buffer.
	public: void appendData(const QrSegment &seg);
	
};

}
