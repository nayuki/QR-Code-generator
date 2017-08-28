/* 
 * QR Code generator test worker (C)
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

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "qrcodegen.h"

#ifndef __cplusplus
	#define MALLOC(num, type)  malloc((num) * sizeof(type))
#else
	#define MALLOC(num, type)  static_cast<type*>(malloc((num) * sizeof(type)))
#endif


int main(void) {
	while (true) {
		
		// Read data length or exit
		int length;
		if (scanf("%d", &length) != 1)
			return EXIT_FAILURE;
		if (length == -1)
			break;
		
		// Read data bytes
		bool isAscii = true;
		uint8_t *data = MALLOC(length, uint8_t);
		if (data == NULL) {
			perror("malloc");
			return EXIT_FAILURE;
		}
		for (int i = 0; i < length; i++) {
			int b;
			if (scanf("%d", &b) != 1)
			return EXIT_FAILURE;
			data[i] = (uint8_t)b;
			isAscii &= 0 < b && b < 128;
		}
		
		// Read encoding parameters
		int errCorLvl, minVersion, maxVersion, mask, boostEcl;
		if (scanf("%d %d %d %d %d", &errCorLvl, &minVersion, &maxVersion, &mask, &boostEcl) != 5)
			return EXIT_FAILURE;
		
		// Allocate memory for QR Code
		int bufferLen = qrcodegen_BUFFER_LEN_FOR_VERSION(maxVersion);
		uint8_t *qrcode     = MALLOC(bufferLen, uint8_t);
		uint8_t *tempBuffer = MALLOC(bufferLen, uint8_t);
		if (qrcode == NULL || tempBuffer == NULL) {
			perror("malloc");
			return EXIT_FAILURE;
		}
		
		// Try to make QR Code symbol
		bool ok;
		if (isAscii) {
			char *text = MALLOC(length + 1, char);
			for (int i = 0; i < length; i++)
				text[i] = (char)data[i];
			text[length] = '\0';
			ok = qrcodegen_encodeText(text, tempBuffer, qrcode, (enum qrcodegen_Ecc)errCorLvl,
				minVersion, maxVersion, (enum qrcodegen_Mask)mask, boostEcl == 1);
			free(text);
		} else if (length <= bufferLen) {
			memcpy(tempBuffer, data, length * sizeof(data[0]));
			ok = qrcodegen_encodeBinary(tempBuffer, (size_t)length, qrcode, (enum qrcodegen_Ecc)errCorLvl,
				minVersion, maxVersion, (enum qrcodegen_Mask)mask, boostEcl == 1);
		} else
			ok = false;
		free(data);
		free(tempBuffer);
		
		if (ok) {
			// Print grid of modules
			int size = qrcodegen_getSize(qrcode);
			printf("%d\n", (size - 17) / 4);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++)
					printf("%d\n", qrcodegen_getModule(qrcode, x, y) ? 1 : 0);
			}
		} else
			printf("-1\n");
		free(qrcode);
		fflush(stdout);
	}
	return EXIT_SUCCESS;
}
