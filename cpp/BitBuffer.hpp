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


namespace qrcodegen {

/* 
 * An appendable sequence of bits (0's and 1's).
 */
class BitBuffer final : public std::vector<bool> {
	
	/*---- Constructor ----*/
	
	// Creates an empty bit buffer (length 0).
	public: BitBuffer();
	
	
	
	/*---- Methods ----*/
	
	// Packs this buffer's bits into bytes in big endian,
	// padding with '0' bit values, and returns the new vector.
	public: std::vector<std::uint8_t> getBytes() const;
	
	
	// Appends the given number of low bits of the given value
	// to this sequence. Requires 0 <= val < 2^len.
	public: void appendBits(std::uint32_t val, int len);
	
};

}
