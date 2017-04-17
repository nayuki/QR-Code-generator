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


/* 
 * Tests whether the given string can be encoded in alphanumeric mode.
 */
bool qrcodegen_isAlphanumeric(const char *text);


/* 
 * Tests whether the given string can be encoded in numeric mode.
 */
bool qrcodegen_isNumeric(const char *text);


/* 
 * Returns the side length of any QR Code of the given version.
 * The version must be in the range [1, 40]. The result is in the range [21, 177].
 * Note that the length of any QR Code byte buffer must be at least ceil(size^2 / 8).
 */
int qrcodegen_getSize(int version);


/* 
 * Returns the color of the module (pixel) at the given coordinates, which is either
 * true for white or false for black. The top left corner has the coordinates (x=0, y=0).
 * If the given coordinates are out of bounds, then false (white) is returned.
 */
bool qrcodegen_getModule(const uint8_t qrcode[], int version, int x, int y);
