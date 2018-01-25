/* 
 * QR Code generator test worker (Java)
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

package io.nayuki.qrcodegen;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;


public final class QrCodeGeneratorWorker {
	
	public static void main(String[] args) {
		// Set up input stream and start loop
		try (Scanner input = new Scanner(System.in, "US-ASCII")) {
			input.useDelimiter("\r\n|\n|\r");
			while (processCase(input));
		}
	}
	
	
	private static boolean processCase(Scanner input) {
		// Read data length or exit
		int length = input.nextInt();
		if (length == -1)
			return false;
		if (length > Short.MAX_VALUE)
			throw new RuntimeException();
		
		// Read data bytes
		boolean isAscii = true;
		byte[] data = new byte[length];
		for (int i = 0; i < data.length; i++) {
			int b = input.nextInt();
			if (b < 0 || b > 255)
				throw new RuntimeException();
			data[i] = (byte)b;
			isAscii &= b < 128;
		}
		
		// Read encoding parameters
		int errCorLvl  = input.nextInt();
		int minVersion = input.nextInt();
		int maxVersion = input.nextInt();
		int mask       = input.nextInt();
		int boostEcl   = input.nextInt();
		if (!(0 <= errCorLvl && errCorLvl <= 3) || !(-1 <= mask && mask <= 7) || (boostEcl >>> 1) != 0
				|| !(QrCode.MIN_VERSION <= minVersion && minVersion <= maxVersion && maxVersion <= QrCode.MAX_VERSION))
			throw new RuntimeException();
		
		// Make segments for encoding
		List<QrSegment> segs;
		if (isAscii)
			segs = QrSegment.makeSegments(new String(data, StandardCharsets.US_ASCII));
		else
			segs = Arrays.asList(QrSegment.makeBytes(data));
		
		
		try {  // Try to make QR Code symbol
			QrCode qr = QrCode.encodeSegments(segs, QrCode.Ecc.values()[errCorLvl], minVersion, maxVersion, mask, boostEcl != 0);
			// Print grid of modules
			System.out.println(qr.version);
			for (int y = 0; y < qr.size; y++) {
				for (int x = 0; x < qr.size; x++)
					System.out.println(qr.getModule(x, y) ? 1 : 0);
			}
			
		} catch (IllegalArgumentException e) {
			if (!e.getMessage().equals("Data too long"))
				throw e;
			System.out.println(-1);
		}
		System.out.flush();
		return true;
	}
	
}
