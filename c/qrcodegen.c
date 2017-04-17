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
#include <stdlib.h>
#include <string.h>
#include "qrcodegen.h"


/*---- Forward declarations for private functions ----*/

static bool getModule(const uint8_t qrcode[], int size, int x, int y);
static void setModule(uint8_t qrcode[], int size, int x, int y, bool isBlack);
static void setModuleBounded(uint8_t qrcode[], int size, int x, int y, bool isBlack);

static void initializeFunctionalModules(int version, uint8_t qrcode[]);
static void drawWhiteFunctionModules(uint8_t qrcode[], int version);
static void drawFormatBits(enum qrcodegen_Ecc ecl, enum qrcodegen_Mask mask, uint8_t qrcode[], int size);
static int getAlignmentPatternPositions(int version, uint8_t result[7]);

static void calcReedSolomonGenerator(int degree, uint8_t result[]);
static void calcReedSolomonRemainder(const uint8_t data[], int dataLen, const uint8_t generator[], int degree, uint8_t result[]);
static uint8_t finiteFieldMultiply(uint8_t x, uint8_t y);



/*---- Function implementations ----*/

// Public function - see documentation comment in header file.
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


// Public function - see documentation comment in header file.
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


// Fills the given QR Code grid with white modules for the given version's size,
// then marks every function module in the QR Code as black.
static void initializeFunctionalModules(int version, uint8_t qrcode[]) {
	// Initialize QR Code
	int size = qrcodegen_getSize(version);
	memset(qrcode, 0, (size * size + 7) / 8 * sizeof(qrcode[0]));
	
	// Fill horizontal and vertical timing patterns
	for (int i = 0; i < size; i++) {
		setModule(qrcode, size, 6, i, true);
		setModule(qrcode, size, i, 6, true);
	}
	
	// Fill 3 finder patterns (all corners except bottom right)
	for (int i = 0; i < 8; i++) {
		for (int j = 0; j < 8; j++) {
			setModule(qrcode, size, j, i, true);
			setModule(qrcode, size, size - 1 - j, i, true);
			setModule(qrcode, size, j, size - 1 - i, true);
		}
	}
	
	// Fill numerous alignment patterns
	uint8_t alignPatPos[7] = {0};
	int numAlign = getAlignmentPatternPositions(version, alignPatPos);
	for (int i = 0; i < numAlign; i++) {
		for (int j = 0; j < numAlign; j++) {
			if ((i == 0 && j == 0) || (i == 0 && j == numAlign - 1) || (i == numAlign - 1 && j == 0))
				continue;  // Skip the three finder corners
			else {
				for (int k = -2; k <= 2; k++) {
					for (int l = -2; l <= 2; l++)
						setModule(qrcode, size, alignPatPos[i] + l, alignPatPos[j] + k, true);
				}
			}
		}
	}
	
	// Fill format bits
	for (int i = 0; i < 8; i++) {
		setModule(qrcode, size, i, 8, true);
		setModule(qrcode, size, 8, i, true);
		setModule(qrcode, size, size - 1 - i, 8, true);
		setModule(qrcode, size, 8, size - 1 - i, true);
	}
	setModule(qrcode, size, 8, 8, true);
	
	// Fill version
	if (version >= 7) {
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 6; j++) {
				int k = size - 11 + i;
				setModule(qrcode, size, k, j, true);
				setModule(qrcode, size, j, k, true);
			}
		}
	}
}


// Draws white function modules and possibly some black modules onto the given QR Code, without changing
// non-function modules. This does not draw the format bits. This requires all function modules to be previously
// marked black (namely by initializeFunctionalModules()), because this may skip redrawing black function modules.
static void drawWhiteFunctionModules(uint8_t qrcode[], int version) {
	// Draw horizontal and vertical timing patterns
	int size = qrcodegen_getSize(version);
	for (int i = 7; i < size - 7; i += 2) {
		setModule(qrcode, size, 6, i, false);
		setModule(qrcode, size, i, 6, false);
	}
	
	// Draw 3 finder patterns
	for (int i = -4; i <= 4; i++) {
		for (int j = -4; j <= 4; j++) {
			int dist = abs(i);
			if (abs(j) > dist)
				dist = abs(j);
			if (dist == 2 || dist == 4) {
				setModuleBounded(qrcode, size, 3 + j, 3 + i, false);
				setModuleBounded(qrcode, size, size - 4 + j, 3 + i, false);
				setModuleBounded(qrcode, size, 3 + j, size - 4 + i, false);
			}
		}
	}
	
	// Draw numerous alignment patterns
	uint8_t alignPatPos[7] = {0};
	int numAlign = getAlignmentPatternPositions(version, alignPatPos);
	for (int i = 0; i < numAlign; i++) {
		for (int j = 0; j < numAlign; j++) {
			if ((i == 0 && j == 0) || (i == 0 && j == numAlign - 1) || (i == numAlign - 1 && j == 0))
				continue;  // Skip the three finder corners
			else {
				for (int k = -1; k <= 1; k++) {
					for (int l = -1; l <= 1; l++)
						setModule(qrcode, size, alignPatPos[i] + l, alignPatPos[j] + k, k == 0 && l == 0);
				}
			}
		}
	}
	
	// Draw version block
	if (version >= 7) {
		// Calculate error correction code and pack bits
		int rem = version;  // version is uint6, in the range [7, 40]
		for (int i = 0; i < 12; i++)
			rem = (rem << 1) ^ ((rem >> 11) * 0x1F25);
		long data = (long)version << 12 | rem;  // uint18
		assert(data >> 18 == 0);
		
		// Draw two copies
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 6; j++) {
				int k = size - 11 + i;
				setModule(qrcode, size, k, j, (data & 1) != 0);
				setModule(qrcode, size, j, k, (data & 1) != 0);
				data >>= 1;
			}
		}
	}
}


// Based on the given ECC level and mask, this calculates the format bits
// and draws their black and white modules onto the given QR Code.
static void drawFormatBits(enum qrcodegen_Ecc ecl, enum qrcodegen_Mask mask, uint8_t qrcode[], int size) {
	// Calculate error correction code and pack bits
	assert(0 <= (int)mask && (int)mask <= 7);
	int data;
	switch (ecl) {
		case qrcodegen_Ecc_LOW     :  data = 1;  break;
		case qrcodegen_Ecc_MEDIUM  :  data = 0;  break;
		case qrcodegen_Ecc_QUARTILE:  data = 3;  break;
		case qrcodegen_Ecc_HIGH    :  data = 2;  break;
		default:  assert(false);
	}
	data = data << 3 | (int)mask;  // ecl-derived value is uint2, mask is uint3
	int rem = data;
	for (int i = 0; i < 10; i++)
		rem = (rem << 1) ^ ((rem >> 9) * 0x537);
	data = data << 10 | rem;
	data ^= 0x5412;  // uint15
	assert(data >> 15 == 0);
	
	// Draw first copy
	for (int i = 0; i <= 5; i++)
		setModule(qrcode, size, 8, i, ((data >> i) & 1) != 0);
	setModule(qrcode, size, 8, 7, ((data >> 6) & 1) != 0);
	setModule(qrcode, size, 8, 8, ((data >> 7) & 1) != 0);
	setModule(qrcode, size, 7, 8, ((data >> 8) & 1) != 0);
	for (int i = 9; i < 15; i++)
		setModule(qrcode, size, 14 - i, 8, ((data >> i) & 1) != 0);
	
	// Draw second copy
	for (int i = 0; i <= 7; i++)
		setModule(qrcode, size, size - 1 - i, 8, ((data >> i) & 1) != 0);
	for (int i = 8; i < 15; i++)
		setModule(qrcode, size, 8, size - 15 + i, ((data >> i) & 1) != 0);
	setModule(qrcode, size, 8, size - 8, true);
}


// Calculates the positions of alignment patterns in ascending order for the given version number,
// storing them to the given array and returning an array length in the range [0, 7].
static int getAlignmentPatternPositions(int version, uint8_t result[7]) {
	if (version == 1)
		return 0;
	int size = qrcodegen_getSize(version);
	int numAlign = version / 7 + 2;
	int step;
	if (version != 32)
		step = (version * 4 + numAlign * 2 + 1) / (2 * numAlign - 2) * 2;  // ceil((size - 13) / (2*numAlign - 2)) * 2
	else  // C-C-C-Combo breaker!
		step = 26;
	for (int i = numAlign - 1, pos = size - 7; i >= 1; i--, pos -= step)
		result[i] = pos;
	result[0] = 6;
	return numAlign;
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
