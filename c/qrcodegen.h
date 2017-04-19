/* 
 * QR Code generator library (C)
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

#pragma once

#include <stdbool.h>
#include <stdint.h>


/*---- Enumeration types and constants ----*/

/* 
 * Represents the error correction level used in a QR Code symbol.
 */
enum qrcodegen_Ecc {
	qrcodegen_Ecc_LOW,
	qrcodegen_Ecc_MEDIUM,
	qrcodegen_Ecc_QUARTILE,
	qrcodegen_Ecc_HIGH,
};


/* 
 * Represents the mask pattern used in a QR Code symbol.
 */
enum qrcodegen_Mask {
	qrcodegen_Mask_AUTO = -1,
	qrcodegen_Mask_0 = 0,
	qrcodegen_Mask_1,
	qrcodegen_Mask_2,
	qrcodegen_Mask_3,
	qrcodegen_Mask_4,
	qrcodegen_Mask_5,
	qrcodegen_Mask_6,
	qrcodegen_Mask_7,
};



/*---- Macro constants and functions ----*/

// The minimum and maximum defined QR Code version numbers.
#define qrcodegen_VERSION_MIN  1
#define qrcodegen_VERSION_MAX  40

// Calculates the number of bytes needed to store any QR Code up to and including the given version number,
// as a compile-time constant. For example, 'uint8_t buffer[qrcodegen_BUFFER_LEN_FOR_VERSION(25)];'
// can store any single QR Code from version 1 to 25, inclusive.
#define qrcodegen_BUFFER_LEN_FOR_VERSION(n)  ((((n) * 4 + 17) * ((n) * 4 + 17) + 7) / 8)

// The worst-case number of bytes needed to store one QR Code, up to and including
// version 40. This value equals 3917, which is just under 4 kilobytes.
#define qrcodegen_BUFFER_LEN_MAX  qrcodegen_BUFFER_LEN_FOR_VERSION(qrcodegen_VERSION_MAX)



/*---- Top-level QR Code functions ----*/

/* 
 * Encodes the given text data to a QR Code symbol, returning the actual version number used.
 * If the data is too long to fit in any version in the given range at the given ECC level,
 * then 0 is returned. Both dataAndTemp and qrcode each must have length at least
 * qrcodegen_BUFFER_LEN_FOR_VERSION(maxVersion).
 */
int qrcodegen_encodeText(const char *text, uint8_t tempBuffer[], uint8_t qrcode[],
	enum qrcodegen_Ecc ecl, int minVersion, int maxVersion, enum qrcodegen_Mask mask, bool boostEcl);


/* 
 * Encodes the given binary data to a QR Code symbol, returning the actual version number used.
 * If the data is too long to fit in any version in the given range at the given ECC level,
 * then 0 is returned. dataAndTemp[0 : dataLen] represents the input data, and the function
 * may overwrite the array's contents as a temporary work area. Both dataAndTemp and qrcode
 * must have length at least qrcodegen_BUFFER_LEN_FOR_VERSION(maxVersion).
 */
int qrcodegen_encodeBinary(uint8_t dataAndTemp[], size_t dataLen, uint8_t qrcode[],
	enum qrcodegen_Ecc ecl, int minVersion, int maxVersion, enum qrcodegen_Mask mask, bool boostEcl);



/*---- Low-level QR Code functions ----*/

/* 
 * Returns the side length of any QR Code of the given version number.
 * The version must be in the range [1, 40]. The result is in the range [21, 177].
 * Note that every 'uint8_t qrcode[]' buffer must have a length of at least
 * ceil(size^2 / 8), which also equals qrcodegen_BUFFER_LEN_FOR_VERSION(version).
 */
int qrcodegen_getSize(int version);


/* 
 * Returns the color of the module (pixel) at the given coordinates, which is either
 * true for white or false for black. The top left corner has the coordinates (x=0, y=0).
 * If the given coordinates are out of bounds, then false (white) is returned.
 */
bool qrcodegen_getModule(const uint8_t qrcode[], int version, int x, int y);
