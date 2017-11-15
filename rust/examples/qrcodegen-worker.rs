/* 
 * QR Code generator test worker (Rust)
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

extern crate qrcodegen;
use qrcodegen::Mask;
use qrcodegen::QrCode;
use qrcodegen::QrCodeEcc;
use qrcodegen::QrSegment;
use qrcodegen::Version;


fn main() {
	loop {
		
		// Read data length or exit
		let length: i16 = read_int();
		if length == -1 {
			break;
		}
		
		// Read data bytes
		let mut data = Vec::<u8>::with_capacity(length as usize);
		for _ in 0 .. length {
			let b: i16 = read_int();
			assert_eq!((b as u8) as i16, b, "Byte value out of range");
			data.push(b as u8);
		}
		let isascii: bool = data.iter().all(|b| *b < 128);
		
		// Read encoding parameters
		let errcorlvl  = read_int();
		let minversion = read_int();
		let maxversion = read_int();
		let mask       = read_int();
		let boostecl   = read_int();
		assert!(0 <= errcorlvl && errcorlvl <= 3);
		assert!((qrcodegen::QrCode_MIN_VERSION.value() as i16) <= minversion
			&& minversion <= maxversion
			&& maxversion <= (qrcodegen::QrCode_MAX_VERSION.value() as i16));
		assert!(-1 <= mask && mask <= 7);
		assert!(boostecl >> 1 == 0);
		
		// Make segments for encoding
		let segs: Vec<QrSegment>;
		if isascii {
			let chrs: Vec<char> = std::str::from_utf8(&data).unwrap().chars().collect();
			segs = QrSegment::make_segments(&chrs);
		} else {
			segs = vec![QrSegment::make_bytes(&data)];
		}
		
		// Try to make QR Code symbol
		let msk = if mask == -1 { None } else { Some(Mask::new(mask as u8)) };
		match QrCode::encode_segments_advanced(&segs, ECC_LEVELS[errcorlvl as usize],
				Version::new(minversion as u8), Version::new(maxversion as u8), msk, boostecl != 0) {
		
			Some(qr) => {
				// Print grid of modules
				println!("{}", qr.version().value());
				for y in 0 .. qr.size() {
					for x in 0 .. qr.size() {
						println!("{}", qr.get_module(x, y) as i8);
					}
				}
			},
			None => println!("-1"),
		}
		use std::io::Write;
		std::io::stdout().flush().unwrap();
	}
}


fn read_int() -> i16 {
	let mut line = String::new();
	std::io::stdin().read_line(&mut line).unwrap();
	let mut chrs: Vec<char> = line.chars().collect();
	assert_eq!(chrs.pop().unwrap(), '\n');
	let line: String = chrs.iter().cloned().collect();
	match line.parse::<i16>() {
		Ok(x) => x,
		Err(_) => panic!("Invalid number"),
	}
}


static ECC_LEVELS: [QrCodeEcc; 4] = [
	QrCodeEcc::Low,
	QrCodeEcc::Medium,
	QrCodeEcc::Quartile,
	QrCodeEcc::High,
];
