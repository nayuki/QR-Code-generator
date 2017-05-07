/* 
 * QR Code generator library (C)
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

#include <stdbool.h>
#include <stdint.h>


/*---- Enumeration types and values ----*/

/* 
 * Represents the error correction level used in a QR Code symbol.
 */
enum qrcodegen_Ecc {
	qrcodegen_Ecc_LOW = 0,
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

// The minimum and maximum defined QR Code version numbers for Model 2.
#define qrcodegen_VERSION_MIN  1
#define qrcodegen_VERSION_MAX  40

// Calculates the number of bytes needed to store any QR Code up to and including the given version number,
// as a compile-time constant. For example, 'uint8_t buffer[qrcodegen_BUFFER_LEN_FOR_VERSION(25)];'
// can store any single QR Code from version 1 to 25, inclusive.
#define qrcodegen_BUFFER_LEN_FOR_VERSION(n)  ((((n) * 4 + 17) * ((n) * 4 + 17) + 7) / 8 + 1)

// The worst-case number of bytes needed to store one QR Code, up to and including
// version 40. This value equals 3918, which is just under 4 kilobytes.
#define qrcodegen_BUFFER_LEN_MAX  qrcodegen_BUFFER_LEN_FOR_VERSION(qrcodegen_VERSION_MAX)



/*---- Functions to generate QR Codes ----*/

#ifdef __cplusplus
extern "C" {
#endif

/* 
 * Encodes the given text string to a QR Code symbol, returning true if encoding succeeded.
 * If the data is too long to fit in any version in the given range
 * at the given ECC level, then false is returned.
 * - The input text must be encoded in UTF-8 and contain no NULs.
 * - The variables ecl and mask must correspond to enum constant values.
 * - Requires 1 <= minVersion <= maxVersion <= 40.
 * - The arrays tempBuffer and qrcode must each have a length
 *   of at least qrcodegen_BUFFER_LEN_FOR_VERSION(maxVersion).
 * - After the function returns, tempBuffer contains no useful data.
 * - If successful, the resulting QR Code may use numeric,
 *   alphanumeric, or byte mode to encode the text.
 * - In the most optimistic case, a QR Code at version 40 with low ECC
 *   can hold any UTF-8 string up to 2953 bytes, or any alphanumeric string
 *   up to 4296 characters, or any digit string up to 7089 characters.
 *   These numbers represent the hard upper limit of the QR Code standard.
 * - Please consult the QR Code specification for information on
 *   data capacities per version, ECC level, and text encoding mode.
 */
bool qrcodegen_encodeText(const char *text, uint8_t tempBuffer[], uint8_t qrcode[],
	enum qrcodegen_Ecc ecl, int minVersion, int maxVersion, enum qrcodegen_Mask mask, bool boostEcl);


/* 
 * Encodes the given binary data to a QR Code symbol, returning true if encoding succeeded.
 * If the data is too long to fit in any version in the given range
 * at the given ECC level, then false is returned.
 * - The input array range dataAndTemp[0 : dataLen] should normally be
 *   valid UTF-8 text, but is not required by the QR Code standard.
 * - The variables ecl and mask must correspond to enum constant values.
 * - Requires 1 <= minVersion <= maxVersion <= 40.
 * - The arrays dataAndTemp and qrcode must each have a length
 *   of at least qrcodegen_BUFFER_LEN_FOR_VERSION(maxVersion).
 * - After the function returns, the contents of dataAndTemp may have changed,
 *   and does not represent useful data anymore.
 * - If successful, the resulting QR Code will use byte mode to encode the data.
 * - In the most optimistic case, a QR Code at version 40 with low ECC can hold any byte
 *   sequence up to length 2953. This is the hard upper limit of the QR Code standard.
 * - Please consult the QR Code specification for information on
 *   data capacities per version, ECC level, and text encoding mode.
 */
bool qrcodegen_encodeBinary(uint8_t dataAndTemp[], size_t dataLen, uint8_t qrcode[],
	enum qrcodegen_Ecc ecl, int minVersion, int maxVersion, enum qrcodegen_Mask mask, bool boostEcl);



/*---- Functions to extract raw data from QR Codes ----*/

/* 
 * Returns the side length of the given QR Code, assuming that encoding succeeded.
 * The result is in the range [21, 177]. Note that the length of the array buffer
 * is related to the side length - every 'uint8_t qrcode[]' must have length at least
 * qrcodegen_BUFFER_LEN_FOR_VERSION(version), which equals ceil(size^2 / 8 + 1).
 */
int qrcodegen_getSize(const uint8_t qrcode[]);


/* 
 * Returns the color of the module (pixel) at the given coordinates, which is either
 * false for white or true for black. The top left corner has the coordinates (x=0, y=0).
 * If the given coordinates are out of bounds, then false (white) is returned.
 */
bool qrcodegen_getModule(const uint8_t qrcode[], int x, int y);

#ifdef __cplusplus
}
#endif
