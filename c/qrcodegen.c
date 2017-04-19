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
#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include "qrcodegen.h"


/*---- Forward declarations for private functions ----*/

static void encodeQrCodeTail(uint8_t dataAndQrcode[], uint8_t tempBuffer[], int version, enum qrcodegen_Ecc ecl, enum qrcodegen_Mask mask);
static long getPenaltyScore(const uint8_t qrcode[], int size);
static void appendBitsToBuffer(uint16_t val, int numBits, uint8_t buffer[], int *bitLen);
static int getNumDataCodewords(int version, enum qrcodegen_Ecc ecl);

static bool getModule(const uint8_t qrcode[], int size, int x, int y);
static void setModule(uint8_t qrcode[], int size, int x, int y, bool isBlack);
static void setModuleBounded(uint8_t qrcode[], int size, int x, int y, bool isBlack);

static void initializeFunctionalModules(int version, uint8_t qrcode[]);
static void drawWhiteFunctionModules(uint8_t qrcode[], int version);
static void drawFormatBits(enum qrcodegen_Ecc ecl, enum qrcodegen_Mask mask, uint8_t qrcode[], int size);
static int getAlignmentPatternPositions(int version, uint8_t result[7]);
static void fillRectangle(int left, int top, int width, int height, uint8_t qrcode[], int size);

static void appendErrorCorrection(uint8_t data[], int version, enum qrcodegen_Ecc ecl, uint8_t result[]);
static int getNumRawDataModules(int version);
static void drawCodewords(const uint8_t data[], int dataLen, uint8_t qrcode[], int size);
static void applyMask(const uint8_t functionModules[], uint8_t qrcode[], int size, int mask);

static void calcReedSolomonGenerator(int degree, uint8_t result[]);
static void calcReedSolomonRemainder(const uint8_t data[], int dataLen, const uint8_t generator[], int degree, uint8_t result[]);
static uint8_t finiteFieldMultiply(uint8_t x, uint8_t y);



/*---- Private tables of constants ----*/

static const char *ALPHANUMERIC_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";

static const int16_t NUM_ERROR_CORRECTION_CODEWORDS[4][41] = {
	// Version: (note that index 0 is for padding, and is set to an illegal value)
	//0,  1,  2,  3,  4,  5,   6,   7,   8,   9,  10,  11,  12,  13,  14,  15,  16,  17,  18,  19,  20,  21,  22,  23,  24,   25,   26,   27,   28,   29,   30,   31,   32,   33,   34,   35,   36,   37,   38,   39,   40    Error correction level
	{-1,  7, 10, 15, 20, 26,  36,  40,  48,  60,  72,  80,  96, 104, 120, 132, 144, 168, 180, 196, 224, 224, 252, 270, 300,  312,  336,  360,  390,  420,  450,  480,  510,  540,  570,  570,  600,  630,  660,  720,  750},  // Low
	{-1, 10, 16, 26, 36, 48,  64,  72,  88, 110, 130, 150, 176, 198, 216, 240, 280, 308, 338, 364, 416, 442, 476, 504, 560,  588,  644,  700,  728,  784,  812,  868,  924,  980, 1036, 1064, 1120, 1204, 1260, 1316, 1372},  // Medium
	{-1, 13, 22, 36, 52, 72,  96, 108, 132, 160, 192, 224, 260, 288, 320, 360, 408, 448, 504, 546, 600, 644, 690, 750, 810,  870,  952, 1020, 1050, 1140, 1200, 1290, 1350, 1440, 1530, 1590, 1680, 1770, 1860, 1950, 2040},  // Quartile
	{-1, 17, 28, 44, 64, 88, 112, 130, 156, 192, 224, 264, 308, 352, 384, 432, 480, 532, 588, 650, 700, 750, 816, 900, 960, 1050, 1110, 1200, 1260, 1350, 1440, 1530, 1620, 1710, 1800, 1890, 1980, 2100, 2220, 2310, 2430},  // High
};

const int8_t NUM_ERROR_CORRECTION_BLOCKS[4][41] = {
	// Version: (note that index 0 is for padding, and is set to an illegal value)
	//0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
	{-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4,  4,  4,  4,  4,  6,  6,  6,  6,  7,  8,  8,  9,  9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25},  // Low
	{-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5,  5,  8,  9,  9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49},  // Medium
	{-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8,  8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68},  // Quartile
	{-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81},  // High
};

static const int PENALTY_N1 = 3;
static const int PENALTY_N2 = 3;
static const int PENALTY_N3 = 40;
static const int PENALTY_N4 = 10;



/*---- Top-level QR Code encoding functions ----*/

// Public function - see documentation comment in header file.
int qrcodegen_encodeText(const char *text, uint8_t tempBuffer[], uint8_t qrcode[],
		enum qrcodegen_Ecc ecl, int minVersion, int maxVersion, enum qrcodegen_Mask mask, bool boostEcl) {
	assert(qrcodegen_VERSION_MIN <= minVersion && minVersion <= maxVersion && maxVersion <= qrcodegen_VERSION_MAX);
	assert(0 <= (int)ecl && (int)ecl <= 3 && -1 <= (int)mask && (int)mask <= 7);
	
	// Get text properties
	int textLen = 0;
	bool isNumeric = true;
	bool isAlphanumeric = true;
	for (const char *p = text; *p != '\0'; p++, textLen++) {
		if (textLen == INT16_MAX)  // Note: INT16_MAX < INT_MAX && INT16_MAX < SIZE_MAX
			return 0;
		char c = *p;
		if (c < '0' || c > '9') {
			isNumeric = false;
			isAlphanumeric &= strchr(ALPHANUMERIC_CHARSET, c) != NULL;
		}
	}
	
	int textBits;
	if (isNumeric) {  // textBits = textLen * 3 + ceil(textLen / 3)
		if (textLen > INT_MAX / 3)
			return 0;
		textBits = textLen * 3;
		if (textLen > INT_MAX - 2 || textLen > INT_MAX - textBits)
			return 0;
		textBits += (textLen + 2) / 3;
	} else if (isAlphanumeric) {  // textBits = textLen * 5 + ceil(textLen / 2)
		if (textLen > INT_MAX / 5)
			return 0;
		textBits = textLen * 5;
		if (textLen > INT_MAX - 1 || textLen > INT_MAX - textBits)
			return 0;
		textBits += (textLen + 1) / 2;
	} else {  // Use binary mode
		if (textLen > qrcodegen_BUFFER_LEN_FOR_VERSION(maxVersion))
			return 0;
		for (int i = 0; i < textLen; i++)
			tempBuffer[i] = (uint8_t)text[i];
		return qrcodegen_encodeBinary(tempBuffer, (size_t)textLen, qrcode, ecl, minVersion, maxVersion, mask, boostEcl);
	}
	
	int version;
	int dataUsedBits = -1;
	int dataCapacityBits = -1;
	int lengthBits = -1;
	for (version = minVersion; ; version++) {
		if (version <= 9)
			lengthBits = isNumeric ? 10 : 9;
		else if (version <= 26)
			lengthBits = isNumeric ? 12 : 11;
		else
			lengthBits = isNumeric ? 14 : 13;
		if (textLen < (1 << lengthBits)) {
			dataCapacityBits = getNumDataCodewords(version, ecl) * 8;  // Number of data bits available
			dataUsedBits = 4 + lengthBits;
			if (textBits > INT_MAX - dataUsedBits)
				continue;
			dataUsedBits += textBits;
			if (dataUsedBits <= dataCapacityBits)
				break;  // This version number is found to be suitable
		}
		if (version >= maxVersion)  // All versions in the range could not fit the given data
			return 0;
	}
	assert(dataUsedBits >= 0 && dataCapacityBits >= 0);
	if (boostEcl) {
		if (dataUsedBits <= getNumDataCodewords(version, qrcodegen_Ecc_MEDIUM  ) * 8) ecl = qrcodegen_Ecc_MEDIUM  ;
		if (dataUsedBits <= getNumDataCodewords(version, qrcodegen_Ecc_QUARTILE) * 8) ecl = qrcodegen_Ecc_QUARTILE;
		if (dataUsedBits <= getNumDataCodewords(version, qrcodegen_Ecc_HIGH    ) * 8) ecl = qrcodegen_Ecc_HIGH    ;
		dataCapacityBits = getNumDataCodewords(version, ecl) * 8;
	}
	
	memset(qrcode, 0, qrcodegen_BUFFER_LEN_FOR_VERSION(version) * sizeof(qrcode[0]));
	int bitLen = 0;
	appendBitsToBuffer(isNumeric ? 1 : 2, 4, qrcode, &bitLen);
	appendBitsToBuffer((uint16_t)textLen, lengthBits, qrcode, &bitLen);
	if (isNumeric) {
		int accumData = 0;
		int accumCount = 0;
		for (const char *p = text; *p != '\0'; p++) {
			accumData = accumData * 10 + (*p - '0');
			accumCount++;
			if (accumCount == 3) {
				appendBitsToBuffer(accumData, 10, qrcode, &bitLen);
				accumData = 0;
				accumCount = 0;
			}
		}
		if (accumCount > 0)  // 1 or 2 digits remaining
			appendBitsToBuffer(accumData, accumCount * 3 + 1, qrcode, &bitLen);
	} else {  // isAlphanumeric
		int accumData = 0;
		int accumCount = 0;
		for (const char *p = text; *p != '\0'; p++) {
			accumData = accumData * 45 + (strchr(ALPHANUMERIC_CHARSET, *p) - ALPHANUMERIC_CHARSET);
			accumCount++;
			if (accumCount == 2) {
				appendBitsToBuffer(accumData, 11, qrcode, &bitLen);
				accumData = 0;
				accumCount = 0;
			}
		}
		if (accumCount > 0)  // 1 character remaining
			appendBitsToBuffer(accumData, 6, qrcode, &bitLen);
	}
	int terminatorBits = dataCapacityBits - bitLen;
	if (terminatorBits > 4)
		terminatorBits = 4;
	appendBitsToBuffer(0, terminatorBits, qrcode, &bitLen);
	appendBitsToBuffer(0, (8 - bitLen % 8) % 8, qrcode, &bitLen);
	for (uint8_t padByte = 0xEC; bitLen < dataCapacityBits; padByte ^= 0xEC ^ 0x11)
		appendBitsToBuffer(padByte, 8, qrcode, &bitLen);
	assert(bitLen % 8 == 0);
	encodeQrCodeTail(qrcode, tempBuffer, version, ecl, mask);
	return version;
}


// Public function - see documentation comment in header file.
int qrcodegen_encodeBinary(uint8_t dataAndTemp[], size_t dataLen, uint8_t qrcode[],
		enum qrcodegen_Ecc ecl, int minVersion, int maxVersion, enum qrcodegen_Mask mask, bool boostEcl) {
	assert(qrcodegen_VERSION_MIN <= minVersion && minVersion <= maxVersion && maxVersion <= qrcodegen_VERSION_MAX);
	assert(0 <= (int)ecl && (int)ecl <= 3 && -1 <= (int)mask && (int)mask <= 7);
	
	int version;
	int dataUsedBits = -1;
	int dataCapacityBits = -1;
	for (version = minVersion; ; version++) {
		if ((version <= 9 && dataLen < (1U << 8)) || dataLen < (1U << 16)) {
			dataCapacityBits = getNumDataCodewords(version, ecl) * 8;  // Number of data bits available
			dataUsedBits = 4 + (version <= 9 ? 8 : 16);
			if (dataLen > (unsigned int)INT_MAX / 8 || (unsigned int)(INT_MAX - dataUsedBits) < dataLen * 8)
				continue;
			dataUsedBits += dataLen * 8;
			if (dataUsedBits <= dataCapacityBits)
				break;  // This version number is found to be suitable
		}
		if (version >= maxVersion)  // All versions in the range could not fit the given data
			return 0;
	}
	assert(dataUsedBits >= 0 && dataCapacityBits >= 0);
	if (boostEcl) {
		if (dataUsedBits <= getNumDataCodewords(version, qrcodegen_Ecc_MEDIUM  ) * 8) ecl = qrcodegen_Ecc_MEDIUM  ;
		if (dataUsedBits <= getNumDataCodewords(version, qrcodegen_Ecc_QUARTILE) * 8) ecl = qrcodegen_Ecc_QUARTILE;
		if (dataUsedBits <= getNumDataCodewords(version, qrcodegen_Ecc_HIGH    ) * 8) ecl = qrcodegen_Ecc_HIGH    ;
		dataCapacityBits = getNumDataCodewords(version, ecl) * 8;
	}
	
	memset(qrcode, 0, qrcodegen_BUFFER_LEN_FOR_VERSION(version) * sizeof(qrcode[0]));
	int bitLen = 0;
	appendBitsToBuffer(4, 4, qrcode, &bitLen);
	appendBitsToBuffer((uint16_t)dataLen, (version <= 9 ? 8 : 16), qrcode, &bitLen);
	for (size_t i = 0; i < dataLen; i++)
		appendBitsToBuffer(dataAndTemp[i], 8, qrcode, &bitLen);
	int terminatorBits = dataCapacityBits - bitLen;
	if (terminatorBits > 4)
		terminatorBits = 4;
	appendBitsToBuffer(0, terminatorBits, qrcode, &bitLen);
	appendBitsToBuffer(0, (8 - bitLen % 8) % 8, qrcode, &bitLen);
	for (uint8_t padByte = 0xEC; bitLen < dataCapacityBits; padByte ^= 0xEC ^ 0x11)
		appendBitsToBuffer(padByte, 8, qrcode, &bitLen);
	assert(bitLen % 8 == 0);
	encodeQrCodeTail(qrcode, dataAndTemp, version, ecl, mask);
	return version;
}


// Given data codewords in dataAndQrcode already padded to the length specified by the
// version and ECC level, this function adds ECC bytes, interleaves blocks, renders the
// QR Code symbol back to the array dataAndQrcode, and handles automatic mask selection.
static void encodeQrCodeTail(uint8_t dataAndQrcode[], uint8_t tempBuffer[], int version, enum qrcodegen_Ecc ecl, enum qrcodegen_Mask mask) {
	appendErrorCorrection(dataAndQrcode, version, ecl, tempBuffer);
	initializeFunctionalModules(version, dataAndQrcode);
	drawCodewords(tempBuffer, getNumRawDataModules(version) / 8, dataAndQrcode, qrcodegen_getSize(version));
	drawWhiteFunctionModules(dataAndQrcode, version);
	initializeFunctionalModules(version, tempBuffer);
	if (mask == qrcodegen_Mask_AUTO) {  // Automatically choose best mask
		long minPenalty = LONG_MAX;
		for (int i = 0; i < 8; i++) {
			drawFormatBits(ecl, i, dataAndQrcode, qrcodegen_getSize(version));
			applyMask(tempBuffer, dataAndQrcode, qrcodegen_getSize(version), i);
			long penalty = getPenaltyScore(dataAndQrcode, qrcodegen_getSize(version));
			if (penalty < minPenalty) {
				mask = (enum qrcodegen_Mask)i;
				minPenalty = penalty;
			}
			applyMask(tempBuffer, dataAndQrcode, qrcodegen_getSize(version), i);  // Undoes the mask due to XOR
		}
	}
	assert(0 <= (int)mask && (int)mask <= 7);
	drawFormatBits(ecl, (int)mask, dataAndQrcode, qrcodegen_getSize(version));
	applyMask(tempBuffer, dataAndQrcode, qrcodegen_getSize(version), (int)mask);
}


// Calculates and returns the penalty score based on state of the given QR Code's current modules.
// This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
static long getPenaltyScore(const uint8_t qrcode[], int size) {
	long result = 0;
	
	// Adjacent modules in row having same color
	for (int y = 0; y < size; y++) {
		bool colorX = getModule(qrcode, size, 0, y);
		for (int x = 1, runX = 1; x < size; x++) {
			if (getModule(qrcode, size, x, y) != colorX) {
				colorX = getModule(qrcode, size, x, y);
				runX = 1;
			} else {
				runX++;
				if (runX == 5)
					result += PENALTY_N1;
				else if (runX > 5)
					result++;
			}
		}
	}
	// Adjacent modules in column having same color
	for (int x = 0; x < size; x++) {
		bool colorY = getModule(qrcode, size, x, 0);
		for (int y = 1, runY = 1; y < size; y++) {
			if (getModule(qrcode, size, x, y) != colorY) {
				colorY = getModule(qrcode, size, x, y);
				runY = 1;
			} else {
				runY++;
				if (runY == 5)
					result += PENALTY_N1;
				else if (runY > 5)
					result++;
			}
		}
	}
	
	// 2*2 blocks of modules having same color
	for (int y = 0; y < size - 1; y++) {
		for (int x = 0; x < size - 1; x++) {
			bool  color = getModule(qrcode, size, x, y);
			if (  color == getModule(qrcode, size, x + 1, y) &&
			      color == getModule(qrcode, size, x, y + 1) &&
			      color == getModule(qrcode, size, x + 1, y + 1))
				result += PENALTY_N2;
		}
	}
	
	// Finder-like pattern in rows
	for (int y = 0; y < size; y++) {
		for (int x = 0, bits = 0; x < size; x++) {
			bits = ((bits << 1) & 0x7FF) | (getModule(qrcode, size, x, y) ? 1 : 0);
			if (x >= 10 && (bits == 0x05D || bits == 0x5D0))  // Needs 11 bits accumulated
				result += PENALTY_N3;
		}
	}
	// Finder-like pattern in columns
	for (int x = 0; x < size; x++) {
		for (int y = 0, bits = 0; y < size; y++) {
			bits = ((bits << 1) & 0x7FF) | (getModule(qrcode, size, x, y) ? 1 : 0);
			if (y >= 10 && (bits == 0x05D || bits == 0x5D0))  // Needs 11 bits accumulated
				result += PENALTY_N3;
		}
	}
	
	// Balance of black and white modules
	int black = 0;
	for (int y = 0; y < size; y++) {
		for (int x = 0; x < size; x++) {
			if (getModule(qrcode, size, x, y))
				black++;
		}
	}
	int total = size * size;
	// Find smallest k such that (45-5k)% <= dark/total <= (55+5k)%
	for (int k = 0; black*20 < (9-k)*total || black*20 > (11+k)*total; k++)
		result += PENALTY_N4;
	return result;
}


// Appends the given sequence of bits to the given byte-based bit buffer, increasing the bit length.
static void appendBitsToBuffer(uint16_t val, int numBits, uint8_t buffer[], int *bitLen) {
	assert(0 <= numBits && numBits <= 16 && (long)val >> numBits == 0);
	for (int i = numBits - 1; i >= 0; i--, (*bitLen)++)
		buffer[*bitLen >> 3] |= ((val >> i) & 1) << (7 - (*bitLen & 7));
}


// Returns the number of 8-bit codewords that can be used for storing data (not ECC),
// for the given version number and error correction level. The result is in the range [9, 2956].
static int getNumDataCodewords(int version, enum qrcodegen_Ecc ecl) {
	assert(0 <= (int)ecl && (int)ecl < 4 && qrcodegen_VERSION_MIN <= version && version <= qrcodegen_VERSION_MAX);
	return getNumRawDataModules(version) / 8 - NUM_ERROR_CORRECTION_CODEWORDS[(int)ecl][version];
}



/*---- Basic QR Code information functions ----*/

// Public function - see documentation comment in header file.
int qrcodegen_getSize(int version) {
	assert(qrcodegen_VERSION_MIN <= version && version <= qrcodegen_VERSION_MAX);
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



/*---- QR Code drawing functions ----*/

// Fills the given QR Code grid with white modules for the given version's size,
// then marks every function module in the QR Code as black.
static void initializeFunctionalModules(int version, uint8_t qrcode[]) {
	// Initialize QR Code
	int size = qrcodegen_getSize(version);
	memset(qrcode, 0, (size * size + 7) / 8 * sizeof(qrcode[0]));
	
	// Fill horizontal and vertical timing patterns
	fillRectangle(6, 0, 1, size, qrcode, size);
	fillRectangle(0, 6, size, 1, qrcode, size);
	
	// Fill 3 finder patterns (all corners except bottom right) and format bits
	fillRectangle(0, 0, 9, 9, qrcode, size);
	fillRectangle(size - 8, 0, 8, 9, qrcode, size);
	fillRectangle(0, size - 8, 9, 8, qrcode, size);
	
	// Fill numerous alignment patterns
	uint8_t alignPatPos[7] = {0};
	int numAlign = getAlignmentPatternPositions(version, alignPatPos);
	for (int i = 0; i < numAlign; i++) {
		for (int j = 0; j < numAlign; j++) {
			if ((i == 0 && j == 0) || (i == 0 && j == numAlign - 1) || (i == numAlign - 1 && j == 0))
				continue;  // Skip the three finder corners
			else
				fillRectangle(alignPatPos[i] - 2, alignPatPos[j] - 2, 5, 5, qrcode, size);
		}
	}
	
	// Fill version
	if (version >= 7) {
		fillRectangle(size - 11, 0, 3, 6, qrcode, size);
		fillRectangle(0, size - 11, 6, 3, qrcode, size);
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
		for (int i = 0; i < 6; i++) {
			for (int j = 0; j < 3; j++) {
				int k = size - 11 + j;
				setModule(qrcode, size, k, i, (data & 1) != 0);
				setModule(qrcode, size, i, k, (data & 1) != 0);
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


// Sets every pixel in the range [left : left + width] * [top : top + height] to black.
static void fillRectangle(int left, int top, int width, int height, uint8_t qrcode[], int size) {
	for (int dy = 0; dy < height; dy++) {
		for (int dx = 0; dx < width; dx++)
			setModule(qrcode, size, left + dx, top + dy, true);
	}
}


// Appends error correction bytes to each block of the given data array, then interleaves bytes
// from the blocks and stores them in the result array. data[0 : rawCodewords - totalEcc] contains
// the input data. data[rawCodewords - totalEcc : rawCodewords] is used as a temporary work area
// and will be clobbered by this function. The final answer is stored in result[0 : rawCodewords].
static void appendErrorCorrection(uint8_t data[], int version, enum qrcodegen_Ecc ecl, uint8_t result[]) {
	// Calculate parameter numbers
	assert(0 <= (int)ecl && (int)ecl < 4 && qrcodegen_VERSION_MIN <= version && version <= qrcodegen_VERSION_MAX);
	int numBlocks = NUM_ERROR_CORRECTION_BLOCKS[(int)ecl][version];
	int totalEcc = NUM_ERROR_CORRECTION_CODEWORDS[(int)ecl][version];
	assert(totalEcc % numBlocks == 0);
	int blockEccLen = totalEcc / numBlocks;
	int rawCodewords = getNumRawDataModules(version) / 8;
	int dataLen = rawCodewords - totalEcc;
	int numShortBlocks = numBlocks - rawCodewords % numBlocks;
	int shortBlockDataLen = rawCodewords / numBlocks - blockEccLen;
	
	// Split data into blocks and append ECC after all data
	uint8_t generator[30];
	calcReedSolomonGenerator(blockEccLen, generator);
	for (int i = 0, j = dataLen, k = 0; i < numBlocks; i++) {
		int blockLen = shortBlockDataLen;
		if (i >= numShortBlocks)
			blockLen++;
		calcReedSolomonRemainder(&data[k], blockLen, generator, blockEccLen, &data[j]);
		j += blockEccLen;
		k += blockLen;
	}
	
	// Interleave (not concatenate) the bytes from every block into a single sequence
	for (int i = 0, k = 0; i < numBlocks; i++) {
		for (int j = 0, l = i; j < shortBlockDataLen; j++, k++, l += numBlocks)
			result[l] = data[k];
		if (i >= numShortBlocks)
			k++;
	}
	for (int i = numShortBlocks, l = numBlocks * shortBlockDataLen, k = (numShortBlocks + 1) * shortBlockDataLen;
			i < numBlocks; i++, k += shortBlockDataLen + 1, l++)
		result[l] = data[k];
	for (int i = 0, k = dataLen; i < numBlocks; i++) {
		for (int j = 0, l = dataLen + i; j < blockEccLen; j++, k++, l += numBlocks)
			result[l] = data[k];
	}
}


// Returns the number of data bits that can be stored in a QR Code of the given version number, after
// all function modules are excluded. This includes remainder bits, so it may not be a multiple of 8.
static int getNumRawDataModules(int version) {
	assert(qrcodegen_VERSION_MIN <= version && version <= qrcodegen_VERSION_MAX);
	int result = (16 * version + 128) * version + 64;
	if (version >= 2) {
		int numAlign = version / 7 + 2;
		result -= (25 * numAlign - 10) * numAlign - 55;
		if (version >= 7)
			result -= 18 * 2;  // Subtract version information
	}
	return result;
}


// Draws the raw codewords (including data and ECC) onto the given QR Code. This requires the initial state of
// the QR Code to be black at function modules and white at codeword modules (including unused remainder bits).
static void drawCodewords(const uint8_t data[], int dataLen, uint8_t qrcode[], int size) {
	int i = 0;  // Bit index into the data
	// Do the funny zigzag scan
	for (int right = size - 1; right >= 1; right -= 2) {  // Index of right column in each column pair
		if (right == 6)
			right = 5;
		for (int vert = 0; vert < size; vert++) {  // Vertical counter
			for (int j = 0; j < 2; j++) {
				int x = right - j;  // Actual x coordinate
				bool upwards = ((right & 2) == 0) ^ (x < 6);
				int y = upwards ? size - 1 - vert : vert;  // Actual y coordinate
				if (!getModule(qrcode, size, x, y) && i < dataLen * 8) {
					bool black = ((data[i >> 3] >> (7 - (i & 7))) & 1) != 0;
					setModule(qrcode, size, x, y, black);
					i++;
				}
				// If there are any remainder bits (0 to 7), they are already
				// set to 0/false/white when the grid of modules was initialized
			}
		}
	}
	assert(i == dataLen * 8);
}



/*---- Reed-Solomon ECC generator functions ----*/

// XORs the data modules in this QR Code with the given mask pattern. Due to XOR's mathematical
// properties, calling applyMask(..., m) twice with the same value is equivalent to no change at all.
// This means it is possible to apply a mask, undo it, and try another mask. Note that a final
// well-formed QR Code symbol needs exactly one mask applied (not zero, not two, etc.).
static void applyMask(const uint8_t functionModules[], uint8_t qrcode[], int size, int mask) {
	assert(0 <= mask && mask <= 7);
	for (int y = 0; y < size; y++) {
		for (int x = 0; x < size; x++) {
			if (getModule(functionModules, size, x, y))
				continue;
			bool invert;
			switch (mask) {
				case 0:  invert = (x + y) % 2 == 0;                    break;
				case 1:  invert = y % 2 == 0;                          break;
				case 2:  invert = x % 3 == 0;                          break;
				case 3:  invert = (x + y) % 3 == 0;                    break;
				case 4:  invert = (x / 3 + y / 2) % 2 == 0;            break;
				case 5:  invert = x * y % 2 + x * y % 3 == 0;          break;
				case 6:  invert = (x * y % 2 + x * y % 3) % 2 == 0;    break;
				case 7:  invert = ((x + y) % 2 + x * y % 3) % 2 == 0;  break;
				default:  assert(false);
			}
			bool val = getModule(qrcode, size, x, y);
			setModule(qrcode, size, x, y, val ^ invert);
		}
	}
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
