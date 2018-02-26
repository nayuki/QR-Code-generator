/* 
 * QR Code generator test worker (C++)
 * 
 * This program reads data and encoding parameters from standard input and writes
 * QR Code bitmaps to standard output. The I/O format is one integer per line.
 * Run with no command line arguments. The program is intended for automated
 * batch testing of end-to-end functionality of this QR Code generator library.
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

#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <vector>
#include "QrCode.hpp"

using qrcodegen::QrCode;
using qrcodegen::QrSegment;


static const std::vector<QrCode::Ecc> ECC_LEVELS{
	QrCode::Ecc::LOW,
	QrCode::Ecc::MEDIUM,
	QrCode::Ecc::QUARTILE,
	QrCode::Ecc::HIGH,
};


int main() {
	while (true) {
		
		// Read data length or exit
		int length;
		std::cin >> length;
		if (length == -1)
			break;
		
		// Read data bytes
		bool isAscii = true;
		std::vector<uint8_t> data;
		for (int i = 0; i < length; i++) {
			int b;
			std::cin >> b;
			data.push_back(static_cast<uint8_t>(b));
			isAscii &= 0 < b && b < 128;
		}
		
		// Read encoding parameters
		int errCorLvl, minVersion, maxVersion, mask, boostEcl;
		std::cin >> errCorLvl;
		std::cin >> minVersion;
		std::cin >> maxVersion;
		std::cin >> mask;
		std::cin >> boostEcl;
		
		// Make list of segments
		std::vector<QrSegment> segs;
		if (isAscii) {
			std::vector<char> text(data.cbegin(), data.cend());
			text.push_back('\0');
			segs = QrSegment::makeSegments(text.data());
		} else
			segs.push_back(QrSegment::makeBytes(data));
		
		try {  // Try to make QR Code symbol
			const QrCode qr = QrCode::encodeSegments(segs,
				ECC_LEVELS.at(errCorLvl), minVersion, maxVersion, mask, boostEcl == 1);
			// Print grid of modules
			std::cout << qr.getVersion() << std::endl;
			for (int y = 0; y < qr.getSize(); y++) {
				for (int x = 0; x < qr.getSize(); x++)
					std::cout << (qr.getModule(x, y) ? 1 : 0) << std::endl;
			}
			
		} catch (const char *msg) {
			if (strcmp(msg, "Data too long") != 0) {
				std::cerr << msg << std::endl;
				return EXIT_FAILURE;
			}
			std::cout << -1 << std::endl;
		}
		std::cout << std::flush;
	}
	return EXIT_SUCCESS;
}
