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

#include <algorithm>
#include <climits>
#include <cstddef>
#include <cstdlib>
#include <sstream>
#include <utility>
#include "BitBuffer.hpp"
#include "QrCode.hpp"

using std::int8_t;
using std::uint8_t;
using std::size_t;
using std::vector;


namespace qrcodegen {

int QrCode::getFormatBits(Ecc ecl) {
	switch (ecl) {
		case Ecc::LOW     :  return 1;
		case Ecc::MEDIUM  :  return 0;
		case Ecc::QUARTILE:  return 3;
		case Ecc::HIGH    :  return 2;
		default:  throw "Assertion error";
	}
}


QrCode QrCode::encodeText(const char *text, Ecc ecl) {
	vector<QrSegment> segs = QrSegment::makeSegments(text);
	return encodeSegments(segs, ecl);
}


QrCode QrCode::encodeBinary(const vector<uint8_t> &data, Ecc ecl) {
	vector<QrSegment> segs{QrSegment::makeBytes(data)};
	return encodeSegments(segs, ecl);
}


QrCode QrCode::encodeSegments(const vector<QrSegment> &segs, Ecc ecl,
		int minVersion, int maxVersion, int mask, bool boostEcl) {
	if (!(MIN_VERSION <= minVersion && minVersion <= maxVersion && maxVersion <= MAX_VERSION) || mask < -1 || mask > 7)
		throw "Invalid value";
	
	// Find the minimal version number to use
	int version, dataUsedBits;
	for (version = minVersion; ; version++) {
		int dataCapacityBits = getNumDataCodewords(version, ecl) * 8;  // Number of data bits available
		dataUsedBits = QrSegment::getTotalBits(segs, version);
		if (dataUsedBits != -1 && dataUsedBits <= dataCapacityBits)
			break;  // This version number is found to be suitable
		if (version >= maxVersion)  // All versions in the range could not fit the given data
			throw "Data too long";
	}
	if (dataUsedBits == -1)
		throw "Assertion error";
	
	// Increase the error correction level while the data still fits in the current version number
	for (Ecc newEcl : vector<Ecc>{Ecc::MEDIUM, Ecc::QUARTILE, Ecc::HIGH}) {
		if (boostEcl && dataUsedBits <= getNumDataCodewords(version, newEcl) * 8)
			ecl = newEcl;
	}
	
	// Create the data bit string by concatenating all segments
	size_t dataCapacityBits = getNumDataCodewords(version, ecl) * 8;
	BitBuffer bb;
	for (const QrSegment &seg : segs) {
		bb.appendBits(seg.getMode().getModeBits(), 4);
		bb.appendBits(seg.getNumChars(), seg.getMode().numCharCountBits(version));
		bb.insert(bb.end(), seg.getData().begin(), seg.getData().end());
	}
	
	// Add terminator and pad up to a byte if applicable
	bb.appendBits(0, std::min<size_t>(4, dataCapacityBits - bb.size()));
	bb.appendBits(0, (8 - bb.size() % 8) % 8);
	
	// Pad with alternate bytes until data capacity is reached
	for (uint8_t padByte = 0xEC; bb.size() < dataCapacityBits; padByte ^= 0xEC ^ 0x11)
		bb.appendBits(padByte, 8);
	if (bb.size() % 8 != 0)
		throw "Assertion error";
	
	// Create the QR Code symbol
	return QrCode(version, ecl, bb.getBytes(), mask);
}


QrCode::QrCode(int ver, Ecc ecl, const vector<uint8_t> &dataCodewords, int mask) :
		// Initialize fields
		version(ver),
		size(MIN_VERSION <= ver && ver <= MAX_VERSION ? ver * 4 + 17 : -1),  // Avoid signed overflow undefined behavior
		errorCorrectionLevel(ecl),
		modules(size, vector<bool>(size)),  // Entirely white grid
		isFunction(size, vector<bool>(size)) {
	
	// Check arguments
	if (ver < MIN_VERSION || ver > MAX_VERSION || mask < -1 || mask > 7)
		throw "Value out of range";
	
	// Draw function patterns, draw all codewords, do masking
	drawFunctionPatterns();
	const vector<uint8_t> allCodewords = appendErrorCorrection(dataCodewords);
	drawCodewords(allCodewords);
	this->mask = handleConstructorMasking(mask);
}


int QrCode::getVersion() const {
	return version;
}


int QrCode::getSize() const {
	return size;
}


QrCode::Ecc QrCode::getErrorCorrectionLevel() const {
	return errorCorrectionLevel;
}


int QrCode::getMask() const {
	return mask;
}


bool QrCode::getModule(int x, int y) const {
	return 0 <= x && x < size && 0 <= y && y < size && module(x, y);
}


std::string QrCode::toSvgString(int border) const {
	if (border < 0)
		throw "Border must be non-negative";
	if (border > INT_MAX / 2 || border * 2 > INT_MAX - size)
		throw "Border too large";
	
	std::ostringstream sb;
	sb << "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
	sb << "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n";
	sb << "<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 ";
	sb << (size + border * 2) << " " << (size + border * 2) << "\" stroke=\"none\">\n";
	sb << "\t<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n";
	sb << "\t<path d=\"";
	bool head = true;
	for (int y = -border; y < size + border; y++) {
		for (int x = -border; x < size + border; x++) {
			if (getModule(x, y)) {
				if (head)
					head = false;
				else
					sb << " ";
				sb << "M" << (x + border) << "," << (y + border) << "h1v1h-1z";
			}
		}
	}
	sb << "\" fill=\"#000000\"/>\n";
	sb << "</svg>\n";
	return sb.str();
}


void QrCode::drawFunctionPatterns() {
	// Draw horizontal and vertical timing patterns
	for (int i = 0; i < size; i++) {
		setFunctionModule(6, i, i % 2 == 0);
		setFunctionModule(i, 6, i % 2 == 0);
	}
	
	// Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
	drawFinderPattern(3, 3);
	drawFinderPattern(size - 4, 3);
	drawFinderPattern(3, size - 4);
	
	// Draw numerous alignment patterns
	const vector<int> alignPatPos = getAlignmentPatternPositions(version);
	int numAlign = alignPatPos.size();
	for (int i = 0; i < numAlign; i++) {
		for (int j = 0; j < numAlign; j++) {
			if ((i == 0 && j == 0) || (i == 0 && j == numAlign - 1) || (i == numAlign - 1 && j == 0))
				continue;  // Skip the three finder corners
			else
				drawAlignmentPattern(alignPatPos.at(i), alignPatPos.at(j));
		}
	}
	
	// Draw configuration data
	drawFormatBits(0);  // Dummy mask value; overwritten later in the constructor
	drawVersion();
}


void QrCode::drawFormatBits(int mask) {
	// Calculate error correction code and pack bits
	int data = getFormatBits(errorCorrectionLevel) << 3 | mask;  // errCorrLvl is uint2, mask is uint3
	int rem = data;
	for (int i = 0; i < 10; i++)
		rem = (rem << 1) ^ ((rem >> 9) * 0x537);
	data = data << 10 | rem;
	data ^= 0x5412;  // uint15
	if (data >> 15 != 0)
		throw "Assertion error";
	
	// Draw first copy
	for (int i = 0; i <= 5; i++)
		setFunctionModule(8, i, ((data >> i) & 1) != 0);
	setFunctionModule(8, 7, ((data >> 6) & 1) != 0);
	setFunctionModule(8, 8, ((data >> 7) & 1) != 0);
	setFunctionModule(7, 8, ((data >> 8) & 1) != 0);
	for (int i = 9; i < 15; i++)
		setFunctionModule(14 - i, 8, ((data >> i) & 1) != 0);
	
	// Draw second copy
	for (int i = 0; i <= 7; i++)
		setFunctionModule(size - 1 - i, 8, ((data >> i) & 1) != 0);
	for (int i = 8; i < 15; i++)
		setFunctionModule(8, size - 15 + i, ((data >> i) & 1) != 0);
	setFunctionModule(8, size - 8, true);
}


void QrCode::drawVersion() {
	if (version < 7)
		return;
	
	// Calculate error correction code and pack bits
	int rem = version;  // version is uint6, in the range [7, 40]
	for (int i = 0; i < 12; i++)
		rem = (rem << 1) ^ ((rem >> 11) * 0x1F25);
	long data = (long)version << 12 | rem;  // uint18
	if (data >> 18 != 0)
		throw "Assertion error";
	
	// Draw two copies
	for (int i = 0; i < 18; i++) {
		bool bit = ((data >> i) & 1) != 0;
		int a = size - 11 + i % 3, b = i / 3;
		setFunctionModule(a, b, bit);
		setFunctionModule(b, a, bit);
	}
}


void QrCode::drawFinderPattern(int x, int y) {
	for (int i = -4; i <= 4; i++) {
		for (int j = -4; j <= 4; j++) {
			int dist = std::max(std::abs(i), std::abs(j));  // Chebyshev/infinity norm
			int xx = x + j, yy = y + i;
			if (0 <= xx && xx < size && 0 <= yy && yy < size)
				setFunctionModule(xx, yy, dist != 2 && dist != 4);
		}
	}
}


void QrCode::drawAlignmentPattern(int x, int y) {
	for (int i = -2; i <= 2; i++) {
		for (int j = -2; j <= 2; j++)
			setFunctionModule(x + j, y + i, std::max(std::abs(i), std::abs(j)) != 1);
	}
}


void QrCode::setFunctionModule(int x, int y, bool isBlack) {
	modules.at(y).at(x) = isBlack;
	isFunction.at(y).at(x) = true;
}


bool QrCode::module(int x, int y) const {
	return modules.at(y).at(x);
}


vector<uint8_t> QrCode::appendErrorCorrection(const vector<uint8_t> &data) const {
	if (data.size() != static_cast<unsigned int>(getNumDataCodewords(version, errorCorrectionLevel)))
		throw "Invalid argument";
	
	// Calculate parameter numbers
	int numBlocks = NUM_ERROR_CORRECTION_BLOCKS[static_cast<int>(errorCorrectionLevel)][version];
	int blockEccLen = ECC_CODEWORDS_PER_BLOCK[static_cast<int>(errorCorrectionLevel)][version];
	int rawCodewords = getNumRawDataModules(version) / 8;
	int numShortBlocks = numBlocks - rawCodewords % numBlocks;
	int shortBlockLen = rawCodewords / numBlocks;
	
	// Split data into blocks and append ECC to each block
	vector<vector<uint8_t> > blocks;
	const ReedSolomonGenerator rs(blockEccLen);
	for (int i = 0, k = 0; i < numBlocks; i++) {
		vector<uint8_t> dat(data.cbegin() + k, data.cbegin() + (k + shortBlockLen - blockEccLen + (i < numShortBlocks ? 0 : 1)));
		k += dat.size();
		const vector<uint8_t> ecc = rs.getRemainder(dat);
		if (i < numShortBlocks)
			dat.push_back(0);
		dat.insert(dat.end(), ecc.cbegin(), ecc.cend());
		blocks.push_back(std::move(dat));
	}
	
	// Interleave (not concatenate) the bytes from every block into a single sequence
	vector<uint8_t> result;
	for (int i = 0; static_cast<unsigned int>(i) < blocks.at(0).size(); i++) {
		for (int j = 0; static_cast<unsigned int>(j) < blocks.size(); j++) {
			// Skip the padding byte in short blocks
			if (i != shortBlockLen - blockEccLen || j >= numShortBlocks)
				result.push_back(blocks.at(j).at(i));
		}
	}
	if (result.size() != static_cast<unsigned int>(rawCodewords))
		throw "Assertion error";
	return result;
}


void QrCode::drawCodewords(const vector<uint8_t> &data) {
	if (data.size() != static_cast<unsigned int>(getNumRawDataModules(version) / 8))
		throw "Invalid argument";
	
	size_t i = 0;  // Bit index into the data
	// Do the funny zigzag scan
	for (int right = size - 1; right >= 1; right -= 2) {  // Index of right column in each column pair
		if (right == 6)
			right = 5;
		for (int vert = 0; vert < size; vert++) {  // Vertical counter
			for (int j = 0; j < 2; j++) {
				int x = right - j;  // Actual x coordinate
				bool upward = ((right + 1) & 2) == 0;
				int y = upward ? size - 1 - vert : vert;  // Actual y coordinate
				if (!isFunction.at(y).at(x) && i < data.size() * 8) {
					modules.at(y).at(x) = ((data.at(i >> 3) >> (7 - (i & 7))) & 1) != 0;
					i++;
				}
				// If there are any remainder bits (0 to 7), they are already
				// set to 0/false/white when the grid of modules was initialized
			}
		}
	}
	if (static_cast<unsigned int>(i) != data.size() * 8)
		throw "Assertion error";
}


void QrCode::applyMask(int mask) {
	if (mask < 0 || mask > 7)
		throw "Mask value out of range";
	for (int y = 0; y < size; y++) {
		for (int x = 0; x < size; x++) {
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
				default:  throw "Assertion error";
			}
			modules.at(y).at(x) = modules.at(y).at(x) ^ (invert & !isFunction.at(y).at(x));
		}
	}
}


int QrCode::handleConstructorMasking(int mask) {
	if (mask == -1) {  // Automatically choose best mask
		long minPenalty = LONG_MAX;
		for (int i = 0; i < 8; i++) {
			drawFormatBits(i);
			applyMask(i);
			long penalty = getPenaltyScore();
			if (penalty < minPenalty) {
				mask = i;
				minPenalty = penalty;
			}
			applyMask(i);  // Undoes the mask due to XOR
		}
	}
	if (mask < 0 || mask > 7)
		throw "Assertion error";
	drawFormatBits(mask);  // Overwrite old format bits
	applyMask(mask);  // Apply the final choice of mask
	return mask;  // The caller shall assign this value to the final-declared field
}


long QrCode::getPenaltyScore() const {
	long result = 0;
	
	// Adjacent modules in row having same color
	for (int y = 0; y < size; y++) {
		bool colorX = false;
		for (int x = 0, runX = -1; x < size; x++) {
			if (x == 0 || module(x, y) != colorX) {
				colorX = module(x, y);
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
		bool colorY = false;
		for (int y = 0, runY = -1; y < size; y++) {
			if (y == 0 || module(x, y) != colorY) {
				colorY = module(x, y);
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
			bool  color = module(x, y);
			if (  color == module(x + 1, y) &&
			      color == module(x, y + 1) &&
			      color == module(x + 1, y + 1))
				result += PENALTY_N2;
		}
	}
	
	// Finder-like pattern in rows
	for (int y = 0; y < size; y++) {
		for (int x = 0, bits = 0; x < size; x++) {
			bits = ((bits << 1) & 0x7FF) | (module(x, y) ? 1 : 0);
			if (x >= 10 && (bits == 0x05D || bits == 0x5D0))  // Needs 11 bits accumulated
				result += PENALTY_N3;
		}
	}
	// Finder-like pattern in columns
	for (int x = 0; x < size; x++) {
		for (int y = 0, bits = 0; y < size; y++) {
			bits = ((bits << 1) & 0x7FF) | (module(x, y) ? 1 : 0);
			if (y >= 10 && (bits == 0x05D || bits == 0x5D0))  // Needs 11 bits accumulated
				result += PENALTY_N3;
		}
	}
	
	// Balance of black and white modules
	int black = 0;
	for (const vector<bool> &row : modules) {
		for (bool color : row) {
			if (color)
				black++;
		}
	}
	int total = size * size;
	// Find smallest k such that (45-5k)% <= dark/total <= (55+5k)%
	for (int k = 0; black*20L < (9L-k)*total || black*20L > (11L+k)*total; k++)
		result += PENALTY_N4;
	return result;
}


vector<int> QrCode::getAlignmentPatternPositions(int ver) {
	if (ver < MIN_VERSION || ver > MAX_VERSION)
		throw "Version number out of range";
	else if (ver == 1)
		return vector<int>();
	else {
		int numAlign = ver / 7 + 2;
		int step;
		if (ver != 32) {
			// ceil((size - 13) / (2*numAlign - 2)) * 2
			step = (ver * 4 + numAlign * 2 + 1) / (2 * numAlign - 2) * 2;
		} else  // C-C-C-Combo breaker!
			step = 26;
		
		vector<int> result;
		for (int i = 0, pos = ver * 4 + 10; i < numAlign - 1; i++, pos -= step)
			result.insert(result.begin(), pos);
		result.insert(result.begin(), 6);
		return result;
	}
}


int QrCode::getNumRawDataModules(int ver) {
	if (ver < MIN_VERSION || ver > MAX_VERSION)
		throw "Version number out of range";
	int result = (16 * ver + 128) * ver + 64;
	if (ver >= 2) {
		int numAlign = ver / 7 + 2;
		result -= (25 * numAlign - 10) * numAlign - 55;
		if (ver >= 7)
			result -= 18 * 2;  // Subtract version information
	}
	return result;
}


int QrCode::getNumDataCodewords(int ver, Ecc ecl) {
	if (ver < MIN_VERSION || ver > MAX_VERSION)
		throw "Version number out of range";
	return getNumRawDataModules(ver) / 8
		- ECC_CODEWORDS_PER_BLOCK[static_cast<int>(ecl)][ver]
		* NUM_ERROR_CORRECTION_BLOCKS[static_cast<int>(ecl)][ver];
}


/*---- Tables of constants ----*/

const int QrCode::PENALTY_N1 = 3;
const int QrCode::PENALTY_N2 = 3;
const int QrCode::PENALTY_N3 = 40;
const int QrCode::PENALTY_N4 = 10;


const int8_t QrCode::ECC_CODEWORDS_PER_BLOCK[4][41] = {
	// Version: (note that index 0 is for padding, and is set to an illegal value)
	//0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
	{-1,  7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},  // Low
	{-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28},  // Medium
	{-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},  // Quartile
	{-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},  // High
};

const int8_t QrCode::NUM_ERROR_CORRECTION_BLOCKS[4][41] = {
	// Version: (note that index 0 is for padding, and is set to an illegal value)
	//0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
	{-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4,  4,  4,  4,  4,  6,  6,  6,  6,  7,  8,  8,  9,  9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25},  // Low
	{-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5,  5,  8,  9,  9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49},  // Medium
	{-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8,  8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68},  // Quartile
	{-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81},  // High
};


QrCode::ReedSolomonGenerator::ReedSolomonGenerator(int degree) :
		coefficients() {
	if (degree < 1 || degree > 255)
		throw "Degree out of range";
	
	// Start with the monomial x^0
	coefficients.resize(degree);
	coefficients.at(degree - 1) = 1;
	
	// Compute the product polynomial (x - r^0) * (x - r^1) * (x - r^2) * ... * (x - r^{degree-1}),
	// drop the highest term, and store the rest of the coefficients in order of descending powers.
	// Note that r = 0x02, which is a generator element of this field GF(2^8/0x11D).
	uint8_t root = 1;
	for (int i = 0; i < degree; i++) {
		// Multiply the current product by (x - r^i)
		for (size_t j = 0; j < coefficients.size(); j++) {
			coefficients.at(j) = multiply(coefficients.at(j), root);
			if (j + 1 < coefficients.size())
				coefficients.at(j) ^= coefficients.at(j + 1);
		}
		root = multiply(root, 0x02);
	}
}


vector<uint8_t> QrCode::ReedSolomonGenerator::getRemainder(const vector<uint8_t> &data) const {
	// Compute the remainder by performing polynomial division
	vector<uint8_t> result(coefficients.size());
	for (uint8_t b : data) {
		uint8_t factor = b ^ result.at(0);
		result.erase(result.begin());
		result.push_back(0);
		for (size_t j = 0; j < result.size(); j++)
			result.at(j) ^= multiply(coefficients.at(j), factor);
	}
	return result;
}


uint8_t QrCode::ReedSolomonGenerator::multiply(uint8_t x, uint8_t y) {
	// Russian peasant multiplication
	int z = 0;
	for (int i = 7; i >= 0; i--) {
		z = (z << 1) ^ ((z >> 7) * 0x11D);
		z ^= ((y >> i) & 1) * x;
	}
	if (z >> 8 != 0)
		throw "Assertion error";
	return static_cast<uint8_t>(z);
}

}
