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

#include <assert.h>
#include <stdint.h>
#include <string.h>
#include "qrcodegen.h"


/*---- Forward declarations for private functions ----*/

static bool getModule(const uint8_t qrcode[], int size, int x, int y);
static void setModule(uint8_t qrcode[], int size, int x, int y, bool isBlack);
static void setModuleBounded(uint8_t qrcode[], int size, int x, int y, bool isBlack);

static void calcReedSolomonGenerator(int degree, uint8_t result[]);
static void calcReedSolomonRemainder(const uint8_t data[], int dataLen, const uint8_t generator[], int degree, uint8_t result[]);
static uint8_t finiteFieldMultiply(uint8_t x, uint8_t y);



/*---- Function implementations ----*/

bool qrcodegen_isAlphanumeric(const char *text) {
	for (; *text != '\0'; text++) {
		char c = *text;
		if (('0' <= c && c <= '9') || ('A' <= c && c <= 'Z'))
			continue;
		else switch (c) {
			case ' ':
			case '$':
			case '%':
			case '*':
			case '+':
			case '-':
			case '.':
			case '/':
			case ':':
				continue;
			default:
				return false;
		}
		return false;
	}
	return true;
}


bool qrcodegen_isNumeric(const char *text) {
	for (; *text != '\0'; text++) {
		char c = *text;
		if (c < '0' || c > '9')
			return false;
	}
	return true;
}


// Public function - see documentation comment in header file.
int qrcodegen_getSize(int version) {
	assert(1 <= version && version <= 40);
	return version * 4 + 17;
}


// Public function - see documentation comment in header file.
bool qrcodegen_getModule(const uint8_t qrcode[], int version, int x, int y) {
	int size = qrcodegen_getSize(version);
	return (0 <= x && x < size && 0 <= y && y < size) && getModule(qrcode, size, x, y);
}


// Gets the module at the given coordinates, which must be in bounds.
static bool getModule(const uint8_t qrcode[], int size, int x, int y) {
	assert(21 <= size && size <= 177 && 0 <= x && x < size && 0 <= y && y < size);
	int index = y * size + x;
	int bitIndex = index & 7;
	int byteIndex = index >> 3;
	return ((qrcode[byteIndex] >> bitIndex) & 1) != 0;
}


// Sets the module at the given coordinates, which must be in bounds.
static void setModule(uint8_t qrcode[], int size, int x, int y, bool isBlack) {
	assert(21 <= size && size <= 177 && 0 <= x && x < size && 0 <= y && y < size);
	int index = y * size + x;
	int bitIndex = index & 7;
	int byteIndex = index >> 3;
	if (isBlack)
		qrcode[byteIndex] |= 1 << bitIndex;
	else
		qrcode[byteIndex] &= (1 << bitIndex) ^ 0xFF;
}


// Sets the module at the given coordinates, doing nothing if out of bounds.
static void setModuleBounded(uint8_t qrcode[], int size, int x, int y, bool isBlack) {
	if (0 <= x && x < size && 0 <= y && y < size)
		setModule(qrcode, size, x, y, isBlack);
}


// Calculates the Reed-Solomon generator polynomial of the given degree, storing in result[0 : degree].
static void calcReedSolomonGenerator(int degree, uint8_t result[]) {
	// Start with the monomial x^0
	assert(1 <= degree && degree <= 30);
	memset(result, 0, degree * sizeof(result[0]));
	result[degree - 1] = 1;
	
	// Compute the product polynomial (x - r^0) * (x - r^1) * (x - r^2) * ... * (x - r^{degree-1}),
	// drop the highest term, and store the rest of the coefficients in order of descending powers.
	// Note that r = 0x02, which is a generator element of this field GF(2^8/0x11D).
	int root = 1;
	for (int i = 0; i < degree; i++) {
		// Multiply the current product by (x - r^i)
		for (int j = 0; j < degree; j++) {
			result[j] = finiteFieldMultiply(result[j], (uint8_t)root);
			if (j + 1 < degree)
				result[j] ^= result[j + 1];
		}
		root = (root << 1) ^ ((root >> 7) * 0x11D);  // Multiply by 0x02 mod GF(2^8/0x11D)
	}
}


// Calculates the remainder of the polynomial data[0 : dataLen] when divided by the generator[0 : degree], where all
// polynomials are in big endian and the generator has an implicit leading 1 term, storing the result in result[0 : degree].
static void calcReedSolomonRemainder(const uint8_t data[], int dataLen, const uint8_t generator[], int degree, uint8_t result[]) {
	// Perform polynomial division
	assert(1 <= degree && degree <= 30);
	memset(result, 0, degree * sizeof(result[0]));
	for (int i = 0; i < dataLen; i++) {
		uint8_t factor = data[i] ^ result[0];
		memmove(&result[0], &result[1], (degree - 1) * sizeof(result[0]));
		result[degree - 1] = 0;
		for (int j = 0; j < degree; j++)
			result[j] ^= finiteFieldMultiply(generator[j], factor);
	}
}


// Returns the product of the two given field elements modulo GF(2^8/0x11D). All argument values are valid.
static uint8_t finiteFieldMultiply(uint8_t x, uint8_t y) {
	// Russian peasant multiplication
	uint8_t z = 0;
	for (int i = 7; i >= 0; i--) {
		z = (z << 1) ^ ((z >> 7) * 0x11D);
		z ^= ((y >> i) & 1) * x;
	}
	return z;
}
