/* 
 * QR Code generator library (Rust)
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


//! Generates QR Codes from text strings and byte arrays.
//! 
//! This project aims to be the best, clearest QR Code generator library.
//! The primary goals are flexible options and absolute correctness.
//! Secondary goals are compact implementation size and good documentation comments.
//! 
//! Home page with live JavaScript demo, extensive descriptions, and competitor comparisons:
//! [https://www.nayuki.io/page/qr-code-generator-library](https://www.nayuki.io/page/qr-code-generator-library)
//! 
//! # Features
//! 
//! Core features:
//! 
//! - Available in 6 programming languages, all with nearly equal functionality: Java, TypeScript/JavaScript, Python, Rust, C++, C
//! - Significantly shorter code but more documentation comments compared to competing libraries
//! - Supports encoding all 40 versions (sizes) and all 4 error correction levels, as per the QR Code Model 2 standard
//! - Output formats: Raw modules/pixels of the QR symbol, SVG XML string
//! - Detects finder-like penalty patterns more accurately than other implementations
//! - Encodes numeric and special-alphanumeric text in less space than general text
//! - Open source code under the permissive MIT License
//! 
//! Manual parameters:
//! 
//! - User can specify minimum and maximum version numbers allowed, then library will automatically choose smallest version in the range that fits the data
//! - User can specify mask pattern manually, otherwise library will automatically evaluate all 8 masks and select the optimal one
//! - User can specify absolute error correction level, or allow the library to boost it if it doesn't increase the version number
//! - User can create a list of data segments manually and add ECI segments
//! 
//! # Examples
//! 
//! ```
//! extern crate qrcodegen;
//! use qrcodegen::QrCode;
//! use qrcodegen::QrCodeEcc;
//! use qrcodegen::QrSegment;
//! ```
//! 
//! Simple operation:
//! 
//! ```
//! let qr = QrCode::encode_text("Hello, world!",
//!     QrCodeEcc::Medium).unwrap();
//! let svg = qr.to_svg_string(4);
//! ```
//! 
//! Manual operation:
//! 
//! ```
//! let chrs: Vec<char> = "3141592653589793238462643383".chars().collect();
//! let segs = QrSegment::make_segments(&chrs);
//! let qr = QrCode::encode_segments_advanced(
//!     &segs, QrCodeEcc::High, 5, 5, Some(Mask::new(2)), false).unwrap();
//! for y in 0 .. qr.size() {
//!     for x in 0 .. qr.size() {
//!         (... paint qr.get_module(x, y) ...)
//!     }
//! }
//! ```


/*---- QrCode functionality ----*/

/// A QR Code symbol, which is a type of two-dimension barcode.
/// 
/// Invented by Denso Wave and described in the ISO/IEC 18004 standard.
/// 
/// Instances of this struct represent an immutable square grid of black and white cells.
/// The impl provides static factory functions to create a QR Code from text or binary data.
/// The struct and impl cover the QR Code Model 2 specification, supporting all versions
/// (sizes) from 1 to 40, all 4 error correction levels, and 4 character encoding modes.
/// 
/// Ways to create a QR Code object:
/// 
/// - High level: Take the payload data and call `QrCode::encode_text()` or `QrCode::encode_binary()`.
/// - Mid level: Custom-make the list of segments and call
///   `QrCode::encode_segments()` or `QrCode::encode_segments_advanced()`.
/// - Low level: Custom-make the array of data codeword bytes (including segment
///   headers and final padding, excluding error correction codewords), supply the
///   appropriate version number, and call the `QrCode::encode_codewords()` constructor.
/// 
/// (Note that all ways require supplying the desired error correction level.)
#[derive(Clone, PartialEq, Eq)]
pub struct QrCode {
	
	// Scalar parameters:
	
	// The version number of this QR Code, which is between 1 and 40 (inclusive).
	// This determines the size of this barcode.
	version: Version,
	
	// The width and height of this QR Code, measured in modules, between
	// 21 and 177 (inclusive). This is equal to version * 4 + 17.
	size: i32,
	
	// The error correction level used in this QR Code.
	errorcorrectionlevel: QrCodeEcc,
	
	// The index of the mask pattern used in this QR Code, which is between 0 and 7 (inclusive).
	// Even if a QR Code is created with automatic masking requested (mask = None),
	// the resulting object still has a mask value between 0 and 7.
	mask: Mask,
	
	// Grids of modules/pixels, with dimensions of size*size:
	
	// The modules of this QR Code (false = white, true = black).
	// Immutable after constructor finishes. Accessed through get_module().
	modules: Vec<bool>,
	
	// Indicates function modules that are not subjected to masking. Discarded when constructor finishes.
	isfunction: Vec<bool>,
	
}


impl QrCode {
	
	/*---- Static factory functions (high level) ----*/
	
	/// Returns a QR Code representing the given Unicode text string at the given error correction level.
	/// 
	/// As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer Unicode
	/// code points (not UTF-8 code units) if the low error correction level is used. The smallest possible
	/// QR Code version is automatically chosen for the output. The ECC level of the result may be higher than
	/// the ecl argument if it can be done without increasing the version.
	/// 
	/// Returns a wrapped `QrCode` if successful, or `Err` if the
	/// data is too long to fit in any version at the given ECC level.
	pub fn encode_text(text: &str, ecl: QrCodeEcc) -> Result<Self,DataTooLong> {
		let chrs: Vec<char> = text.chars().collect();
		let segs: Vec<QrSegment> = QrSegment::make_segments(&chrs);
		QrCode::encode_segments(&segs, ecl)
	}
	
	
	/// Returns a QR Code representing the given binary data at the given error correction level.
	/// 
	/// This function always encodes using the binary segment mode, not any text mode. The maximum number of
	/// bytes allowed is 2953. The smallest possible QR Code version is automatically chosen for the output.
	/// The ECC level of the result may be higher than the ecl argument if it can be done without increasing the version.
	/// 
	/// Returns a wrapped `QrCode` if successful, or `Err` if the
	/// data is too long to fit in any version at the given ECC level.
	pub fn encode_binary(data: &[u8], ecl: QrCodeEcc) -> Result<Self,DataTooLong> {
		let segs: [QrSegment; 1] = [QrSegment::make_bytes(data)];
		QrCode::encode_segments(&segs, ecl)
	}
	
	
	/*---- Static factory functions (mid level) ----*/
	
	/// Returns a QR Code representing the given segments at the given error correction level.
	/// 
	/// The smallest possible QR Code version is automatically chosen for the output. The ECC level
	/// of the result may be higher than the ecl argument if it can be done without increasing the version.
	/// 
	/// This function allows the user to create a custom sequence of segments that switches
	/// between modes (such as alphanumeric and byte) to encode text in less space.
	/// This is a mid-level API; the high-level API is `encode_text()` and `encode_binary()`.
	/// 
	/// Returns a wrapped `QrCode` if successful, or `Err` if the
	/// data is too long to fit in any version at the given ECC level.
	pub fn encode_segments(segs: &[QrSegment], ecl: QrCodeEcc) -> Result<Self,DataTooLong> {
		QrCode::encode_segments_advanced(segs, ecl, Version::MIN, Version::MAX, None, true)
	}
	
	
	/// Returns a QR Code representing the given segments with the given encoding parameters.
	/// 
	/// The smallest possible QR Code version within the given range is automatically
	/// chosen for the output. Iff boostecl is `true`, then the ECC level of the result
	/// may be higher than the ecl argument if it can be done without increasing the
	/// version. The mask number is either between 0 to 7 (inclusive) to force that
	/// mask, or `None` to automatically choose an appropriate mask (which may be slow).
	/// 
	/// This function allows the user to create a custom sequence of segments that switches
	/// between modes (such as alphanumeric and byte) to encode text in less space.
	/// This is a mid-level API; the high-level API is `encode_text()` and `encode_binary()`.
	/// 
	/// Returns a wrapped `QrCode` if successful, or `Err` if the data is too
	/// long to fit in any version in the given range at the given ECC level.
	pub fn encode_segments_advanced(segs: &[QrSegment], mut ecl: QrCodeEcc,
			minversion: Version, maxversion: Version, mask: Option<Mask>, boostecl: bool) -> Result<Self,DataTooLong> {
		assert!(minversion.value() <= maxversion.value(), "Invalid value");
		
		// Find the minimal version number to use
		let mut version: Version = minversion;
		let datausedbits: usize = loop {
			// Number of data bits available
			let datacapacitybits: usize = QrCode::get_num_data_codewords(version, ecl) * 8;
			let dataused: Option<usize> = QrSegment::get_total_bits(segs, version);
			if dataused.map_or(false, |n| n <= datacapacitybits) {
				break dataused.unwrap();  // This version number is found to be suitable
			} else if version.value() >= maxversion.value() {  // All versions in the range could not fit the given data
				let msg: String = match dataused {
					None => String::from("Segment too long"),
					Some(n) => format!("Data length = {} bits, Max capacity = {} bits",
						n, datacapacitybits),
				};
				return Err(DataTooLong(msg));
			} else {
				version = Version::new(version.value() + 1);
			}
		};
		
		// Increase the error correction level while the data still fits in the current version number
		for &newecl in &[QrCodeEcc::Medium, QrCodeEcc::Quartile, QrCodeEcc::High] {  // From low to high
			if boostecl && datausedbits <= QrCode::get_num_data_codewords(version, newecl) * 8 {
				ecl = newecl;
			}
		}
		
		// Concatenate all segments to create the data bit string
		let mut bb = BitBuffer(Vec::new());
		for seg in segs {
			bb.append_bits(seg.mode.mode_bits(), 4);
			bb.append_bits(seg.numchars as u32, seg.mode.num_char_count_bits(version));
			bb.0.extend_from_slice(&seg.data);
		}
		assert_eq!(bb.0.len(), datausedbits);
		
		// Add terminator and pad up to a byte if applicable
		let datacapacitybits: usize = QrCode::get_num_data_codewords(version, ecl) * 8;
		assert!(bb.0.len() <= datacapacitybits);
		let numzerobits: usize = std::cmp::min(4, datacapacitybits - bb.0.len());
		bb.append_bits(0, numzerobits as u8);
		let numzerobits: usize = bb.0.len().wrapping_neg() & 7;
		bb.append_bits(0, numzerobits as u8);
		assert_eq!(bb.0.len() % 8, 0, "Assertion error");
		
		// Pad with alternating bytes until data capacity is reached
		for &padbyte in [0xEC, 0x11].iter().cycle() {
			if bb.0.len() >= datacapacitybits {
				break;
			}
			bb.append_bits(padbyte, 8);
		}
		
		// Pack bits into bytes in big endian
		let mut datacodewords = vec![0u8; bb.0.len() / 8];
		for (i, &bit) in bb.0.iter().enumerate() {
			datacodewords[i >> 3] |= u8::from(bit) << (7 - (i & 7));
		}
		
		// Create the QR Code object
		Ok(QrCode::encode_codewords(version, ecl, &datacodewords, mask))
	}
	
	
	/*---- Constructor (low level) ----*/
	
	/// Creates a new QR Code with the given version number,
	/// error correction level, data codeword bytes, and mask number.
	/// 
	/// This is a low-level API that most users should not use directly.
	/// A mid-level API is the `encode_segments()` function.
	pub fn encode_codewords(ver: Version, ecl: QrCodeEcc, datacodewords: &[u8], mut mask: Option<Mask>) -> Self {
		// Initialize fields
		let size = usize::from(ver.value()) * 4 + 17;
		let mut result = Self {
			version: ver,
			size: size as i32,
			mask: Mask::new(0),  // Dummy value
			errorcorrectionlevel: ecl,
			modules   : vec![false; size * size],  // Initially all white
			isfunction: vec![false; size * size],
		};
		
		// Compute ECC, draw modules
		result.draw_function_patterns();
		let allcodewords: Vec<u8> = result.add_ecc_and_interleave(datacodewords);
		result.draw_codewords(&allcodewords);
		
		// Do masking
		if mask.is_none() {  // Automatically choose best mask
			let mut minpenalty = std::i32::MAX;
			for i in 0u8 .. 8 {
				let newmask = Mask::new(i);
				result.apply_mask(newmask);
				result.draw_format_bits(newmask);
				let penalty: i32 = result.get_penalty_score();
				if penalty < minpenalty {
					mask = Some(newmask);
					minpenalty = penalty;
				}
				result.apply_mask(newmask);  // Undoes the mask due to XOR
			}
		}
		let mask: Mask = mask.unwrap();
		result.mask = mask;
		result.apply_mask(mask);  // Apply the final choice of mask
		result.draw_format_bits(mask);  // Overwrite old format bits
		
		result.isfunction.clear();
		result.isfunction.shrink_to_fit();
		result
	}
	
	
	/*---- Public methods ----*/
	
	/// Returns this QR Code's version, in the range [1, 40].
	pub fn version(&self) -> Version {
		self.version
	}
	
	
	/// Returns this QR Code's size, in the range [21, 177].
	pub fn size(&self) -> i32 {
		self.size
	}
	
	
	/// Returns this QR Code's error correction level.
	pub fn error_correction_level(&self) -> QrCodeEcc {
		self.errorcorrectionlevel
	}
	
	
	/// Returns this QR Code's mask, in the range [0, 7].
	pub fn mask(&self) -> Mask {
		self.mask
	}
	
	
	/// Returns the color of the module (pixel) at the given coordinates,
	/// which is `false` for white or `true` for black.
	/// 
	/// The top left corner has the coordinates (x=0, y=0). If the given
	/// coordinates are out of bounds, then `false` (white) is returned.
	pub fn get_module(&self, x: i32, y: i32) -> bool {
		0 <= x && x < self.size && 0 <= y && y < self.size && self.module(x, y)
	}
	
	
	// Returns the color of the module at the given coordinates, which must be in bounds.
	fn module(&self, x: i32, y: i32) -> bool {
		self.modules[(y * self.size + x) as usize]
	}
	
	
	// Returns a mutable reference to the module's color at the given coordinates, which must be in bounds.
	fn module_mut(&mut self, x: i32, y: i32) -> &mut bool {
		&mut self.modules[(y * self.size + x) as usize]
	}
	
	
	/// Returns a string of SVG code for an image depicting
	/// this QR Code, with the given number of border modules.
	/// 
	/// The string always uses Unix newlines (\n), regardless of the platform.
	pub fn to_svg_string(&self, border: i32) -> String {
		assert!(border >= 0, "Border must be non-negative");
		let mut result = String::new();
		result += "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
		result += "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n";
		let dimension = self.size.checked_add(border.checked_mul(2).unwrap()).unwrap();
		result += &format!(
			"<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 {0} {0}\" stroke=\"none\">\n", dimension);
		result += "\t<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n";
		result += "\t<path d=\"";
		for y in 0 .. self.size {
			for x in 0 .. self.size {
				if self.get_module(x, y) {
					if x != 0 || y != 0 {
						result += " ";
					}
					result += &format!("M{},{}h1v1h-1z", x + border, y + border);
				}
			}
		}
		result += "\" fill=\"#000000\"/>\n";
		result += "</svg>\n";
		result
	}
	
	
	/*---- Private helper methods for constructor: Drawing function modules ----*/
	
	// Reads this object's version field, and draws and marks all function modules.
	fn draw_function_patterns(&mut self) {
		// Draw horizontal and vertical timing patterns
		let size: i32 = self.size;
		for i in 0 .. size {
			self.set_function_module(6, i, i % 2 == 0);
			self.set_function_module(i, 6, i % 2 == 0);
		}
		
		// Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
		self.draw_finder_pattern(3, 3);
		self.draw_finder_pattern(size - 4, 3);
		self.draw_finder_pattern(3, size - 4);
		
		// Draw numerous alignment patterns
		let alignpatpos: Vec<i32> = self.get_alignment_pattern_positions();
		let numalign: usize = alignpatpos.len();
		for i in 0 .. numalign {
			for j in 0 .. numalign {
				// Don't draw on the three finder corners
				if !(i == 0 && j == 0 || i == 0 && j == numalign - 1 || i == numalign - 1 && j == 0) {
					self.draw_alignment_pattern(alignpatpos[i], alignpatpos[j]);
				}
			}
		}
		
		// Draw configuration data
		self.draw_format_bits(Mask::new(0));  // Dummy mask value; overwritten later in the constructor
		self.draw_version();
	}
	
	
	// Draws two copies of the format bits (with its own error correction code)
	// based on the given mask and this object's error correction level field.
	fn draw_format_bits(&mut self, mask: Mask) {
		// Calculate error correction code and pack bits
		let bits: u32 = {
			// errcorrlvl is uint2, mask is uint3
			let data: u32 = u32::from(self.errorcorrectionlevel.format_bits() << 3 | mask.value());
			let mut rem: u32 = data;
			for _ in 0 .. 10 {
				rem = (rem << 1) ^ ((rem >> 9) * 0x537);
			}
			(data << 10 | rem) ^ 0x5412  // uint15
		};
		assert_eq!(bits >> 15, 0, "Assertion error");
		
		// Draw first copy
		for i in 0 .. 6 {
			self.set_function_module(8, i, get_bit(bits, i));
		}
		self.set_function_module(8, 7, get_bit(bits, 6));
		self.set_function_module(8, 8, get_bit(bits, 7));
		self.set_function_module(7, 8, get_bit(bits, 8));
		for i in 9 .. 15 {
			self.set_function_module(14 - i, 8, get_bit(bits, i));
		}
		
		// Draw second copy
		let size: i32 = self.size;
		for i in 0 .. 8 {
			self.set_function_module(size - 1 - i, 8, get_bit(bits, i));
		}
		for i in 8 .. 15 {
			self.set_function_module(8, size - 15 + i, get_bit(bits, i));
		}
		self.set_function_module(8, size - 8, true);  // Always black
	}
	
	
	// Draws two copies of the version bits (with its own error correction code),
	// based on this object's version field, iff 7 <= version <= 40.
	fn draw_version(&mut self) {
		if self.version.value() < 7 {
			return;
		}
		
		// Calculate error correction code and pack bits
		let bits: u32 = {
			let data = u32::from(self.version.value());  // uint6, in the range [7, 40]
			let mut rem: u32 = data;
			for _ in 0 .. 12 {
				rem = (rem << 1) ^ ((rem >> 11) * 0x1F25);
			}
			data << 12 | rem  // uint18
		};
		assert!(bits >> 18 == 0, "Assertion error");
		
		// Draw two copies
		for i in 0 .. 18 {
			let bit: bool = get_bit(bits, i);
			let a: i32 = self.size - 11 + i % 3;
			let b: i32 = i / 3;
			self.set_function_module(a, b, bit);
			self.set_function_module(b, a, bit);
		}
	}
	
	
	// Draws a 9*9 finder pattern including the border separator,
	// with the center module at (x, y). Modules can be out of bounds.
	fn draw_finder_pattern(&mut self, x: i32, y: i32) {
		for dy in -4 ..= 4 {
			for dx in -4 ..= 4 {
				let xx: i32 = x + dx;
				let yy: i32 = y + dy;
				if 0 <= xx && xx < self.size && 0 <= yy && yy < self.size {
					let dist: i32 = std::cmp::max(dx.abs(), dy.abs());  // Chebyshev/infinity norm
					self.set_function_module(xx, yy, dist != 2 && dist != 4);
				}
			}
		}
	}
	
	
	// Draws a 5*5 alignment pattern, with the center module
	// at (x, y). All modules must be in bounds.
	fn draw_alignment_pattern(&mut self, x: i32, y: i32) {
		for dy in -2 ..= 2 {
			for dx in -2 ..= 2 {
				self.set_function_module(x + dx, y + dy, std::cmp::max(dx.abs(), dy.abs()) != 1);
			}
		}
	}
	
	
	// Sets the color of a module and marks it as a function module.
	// Only used by the constructor. Coordinates must be in bounds.
	fn set_function_module(&mut self, x: i32, y: i32, isblack: bool) {
		*self.module_mut(x, y) = isblack;
		self.isfunction[(y * self.size + x) as usize] = true;
	}
	
	
	/*---- Private helper methods for constructor: Codewords and masking ----*/
	
	// Returns a new byte string representing the given data with the appropriate error correction
	// codewords appended to it, based on this object's version and error correction level.
	fn add_ecc_and_interleave(&self, data: &[u8]) -> Vec<u8> {
		let ver: Version = self.version;
		let ecl: QrCodeEcc = self.errorcorrectionlevel;
		assert_eq!(data.len(), QrCode::get_num_data_codewords(ver, ecl), "Illegal argument");
		
		// Calculate parameter numbers
		let numblocks: usize = QrCode::table_get(&NUM_ERROR_CORRECTION_BLOCKS, ver, ecl);
		let blockecclen: usize = QrCode::table_get(&ECC_CODEWORDS_PER_BLOCK  , ver, ecl);
		let rawcodewords: usize = QrCode::get_num_raw_data_modules(ver) / 8;
		let numshortblocks: usize = numblocks - rawcodewords % numblocks;
		let shortblocklen: usize = rawcodewords / numblocks;
		
		// Split data into blocks and append ECC to each block
		let mut blocks = Vec::<Vec<u8>>::with_capacity(numblocks);
		let rsdiv: Vec<u8> = QrCode::reed_solomon_compute_divisor(blockecclen);
		let mut k: usize = 0;
		for i in 0 .. numblocks {
			let datlen: usize = shortblocklen - blockecclen + usize::from(i >= numshortblocks);
			let mut dat = data[k .. k + datlen].to_vec();
			k += datlen;
			let ecc: Vec<u8> = QrCode::reed_solomon_compute_remainder(&dat, &rsdiv);
			if i < numshortblocks {
				dat.push(0);
			}
			dat.extend_from_slice(&ecc);
			blocks.push(dat);
		}
		
		// Interleave (not concatenate) the bytes from every block into a single sequence
		let mut result = Vec::<u8>::with_capacity(rawcodewords);
		for i in 0 ..= shortblocklen {
			for (j, block) in blocks.iter().enumerate() {
				// Skip the padding byte in short blocks
				if i != shortblocklen - blockecclen || j >= numshortblocks {
					result.push(block[i]);
				}
			}
		}
		result
	}
	
	
	// Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
	// data area of this QR Code. Function modules need to be marked off before this is called.
	fn draw_codewords(&mut self, data: &[u8]) {
		assert_eq!(data.len(), QrCode::get_num_raw_data_modules(self.version) / 8, "Illegal argument");
		
		let mut i: usize = 0;  // Bit index into the data
		// Do the funny zigzag scan
		let mut right: i32 = self.size - 1;
		while right >= 1 {  // Index of right column in each column pair
			if right == 6 {
				right = 5;
			}
			for vert in 0 .. self.size {  // Vertical counter
				for j in 0 .. 2 {
					let x: i32 = right - j;  // Actual x coordinate
					let upward: bool = (right + 1) & 2 == 0;
					let y: i32 = if upward { self.size - 1 - vert } else { vert };  // Actual y coordinate
					if !self.isfunction[(y * self.size + x) as usize] && i < data.len() * 8 {
						*self.module_mut(x, y) = get_bit(u32::from(data[i >> 3]), 7 - ((i & 7) as i32));
						i += 1;
					}
					// If this QR Code has any remainder bits (0 to 7), they were assigned as
					// 0/false/white by the constructor and are left unchanged by this method
				}
			}
			right -= 2;
		}
		assert_eq!(i, data.len() * 8, "Assertion error");
	}
	
	
	// XORs the codeword modules in this QR Code with the given mask pattern.
	// The function modules must be marked and the codeword bits must be drawn
	// before masking. Due to the arithmetic of XOR, calling applyMask() with
	// the same mask value a second time will undo the mask. A final well-formed
	// QR Code needs exactly one (not zero, two, etc.) mask applied.
	fn apply_mask(&mut self, mask: Mask) {
		for y in 0 .. self.size {
			for x in 0 .. self.size {
				let invert: bool = match mask.value() {
					0 => (x + y) % 2 == 0,
					1 => y % 2 == 0,
					2 => x % 3 == 0,
					3 => (x + y) % 3 == 0,
					4 => (x / 3 + y / 2) % 2 == 0,
					5 => x * y % 2 + x * y % 3 == 0,
					6 => (x * y % 2 + x * y % 3) % 2 == 0,
					7 => ((x + y) % 2 + x * y % 3) % 2 == 0,
					_ => unreachable!(),
				};
				*self.module_mut(x, y) ^= invert & !self.isfunction[(y * self.size + x) as usize];
			}
		}
	}
	
	
	// Calculates and returns the penalty score based on state of this QR Code's current modules.
	// This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
	fn get_penalty_score(&self) -> i32 {
		let mut result: i32 = 0;
		let size: i32 = self.size;
		
		// Adjacent modules in row having same color, and finder-like patterns
		for y in 0 .. size {
			let mut runcolor = false;
			let mut runx: i32 = 0;
			let mut runhistory = FinderPenalty::new(size);
			for x in 0 .. size {
				if self.module(x, y) == runcolor {
					runx += 1;
					if runx == 5 {
						result += PENALTY_N1;
					} else if runx > 5 {
						result += 1;
					}
				} else {
					runhistory.add_history(runx);
					if !runcolor {
						result += runhistory.count_patterns() * PENALTY_N3;
					}
					runcolor = self.module(x, y);
					runx = 1;
				}
			}
			result += runhistory.terminate_and_count(runcolor, runx) * PENALTY_N3;
		}
		// Adjacent modules in column having same color, and finder-like patterns
		for x in 0 .. size {
			let mut runcolor = false;
			let mut runy: i32 = 0;
			let mut runhistory = FinderPenalty::new(size);
			for y in 0 .. size {
				if self.module(x, y) == runcolor {
					runy += 1;
					if runy == 5 {
						result += PENALTY_N1;
					} else if runy > 5 {
						result += 1;
					}
				} else {
					runhistory.add_history(runy);
					if !runcolor {
						result += runhistory.count_patterns() * PENALTY_N3;
					}
					runcolor = self.module(x, y);
					runy = 1;
				}
			}
			result += runhistory.terminate_and_count(runcolor, runy) * PENALTY_N3;
		}
		
		// 2*2 blocks of modules having same color
		for y in 0 .. size - 1 {
			for x in 0 .. size - 1 {
				let color: bool = self.module(x, y);
				if color == self.module(x + 1, y) &&
				   color == self.module(x, y + 1) &&
				   color == self.module(x + 1, y + 1) {
					result += PENALTY_N2;
				}
			}
		}
		
		// Balance of black and white modules
		let black: i32 = self.modules.iter().copied().map(i32::from).sum();
		let total: i32 = size * size;  // Note that size is odd, so black/total != 1/2
		// Compute the smallest integer k >= 0 such that (45-5k)% <= black/total <= (55+5k)%
		let k: i32 = ((black * 20 - total * 10).abs() + total - 1) / total - 1;
		result += k * PENALTY_N4;
		result
	}
	
	
	/*---- Private helper functions ----*/
	
	// Returns an ascending list of positions of alignment patterns for this version number.
	// Each position is in the range [0,177), and are used on both the x and y axes.
	// This could be implemented as lookup table of 40 variable-length lists of unsigned bytes.
	fn get_alignment_pattern_positions(&self) -> Vec<i32> {
		let ver: u8 = self.version.value();
		if ver == 1 {
			vec![]
		} else {
			let numalign = i32::from(ver) / 7 + 2;
			let step: i32 = if ver == 32 { 26 } else
				{(i32::from(ver)*4 + numalign*2 + 1) / (numalign*2 - 2) * 2};
			let mut result: Vec<i32> = (0 .. numalign - 1).map(
				|i| self.size - 7 - i * step).collect();
			result.push(6);
			result.reverse();
			result
		}
	}
	
	
	// Returns the number of data bits that can be stored in a QR Code of the given version number, after
	// all function modules are excluded. This includes remainder bits, so it might not be a multiple of 8.
	// The result is in the range [208, 29648]. This could be implemented as a 40-entry lookup table.
	fn get_num_raw_data_modules(ver: Version) -> usize {
		let ver = usize::from(ver.value());
		let mut result: usize = (16 * ver + 128) * ver + 64;
		if ver >= 2 {
			let numalign: usize = ver / 7 + 2;
			result -= (25 * numalign - 10) * numalign - 55;
			if ver >= 7 {
				result -= 36;
			}
		}
		assert!(208 <= result && result <= 29648);
		result
	}
	
	
	// Returns the number of 8-bit data (i.e. not error correction) codewords contained in any
	// QR Code of the given version number and error correction level, with remainder bits discarded.
	// This stateless pure function could be implemented as a (40*4)-cell lookup table.
	fn get_num_data_codewords(ver: Version, ecl: QrCodeEcc) -> usize {
		QrCode::get_num_raw_data_modules(ver) / 8
			- QrCode::table_get(&ECC_CODEWORDS_PER_BLOCK    , ver, ecl)
			* QrCode::table_get(&NUM_ERROR_CORRECTION_BLOCKS, ver, ecl)
	}
	
	
	// Returns an entry from the given table based on the given values.
	fn table_get(table: &'static [[i8; 41]; 4], ver: Version, ecl: QrCodeEcc) -> usize {
		table[ecl.ordinal()][usize::from(ver.value())] as usize
	}
	
	
	// Returns a Reed-Solomon ECC generator polynomial for the given degree. This could be
	// implemented as a lookup table over all possible parameter values, instead of as an algorithm.
	fn reed_solomon_compute_divisor(degree: usize) -> Vec<u8> {
		assert!(1 <= degree && degree <= 255, "Degree out of range");
		// Polynomial coefficients are stored from highest to lowest power, excluding the leading term which is always 1.
		// For example the polynomial x^3 + 255x^2 + 8x + 93 is stored as the uint8 array [255, 8, 93].
		let mut result = vec![0u8; degree - 1];
		result.push(1);  // Start off with the monomial x^0
		
		// Compute the product polynomial (x - r^0) * (x - r^1) * (x - r^2) * ... * (x - r^{degree-1}),
		// and drop the highest monomial term which is always 1x^degree.
		// Note that r = 0x02, which is a generator element of this field GF(2^8/0x11D).
		let mut root: u8 = 1;
		for _ in 0 .. degree {  // Unused variable i
			// Multiply the current product by (x - r^i)
			for j in 0 .. degree {
				result[j] = QrCode::reed_solomon_multiply(result[j], root);
				if j + 1 < result.len() {
					result[j] ^= result[j + 1];
				}
			}
			root = QrCode::reed_solomon_multiply(root, 0x02);
		}
		result
	}
	
	
	// Returns the Reed-Solomon error correction codeword for the given data and divisor polynomials.
	fn reed_solomon_compute_remainder(data: &[u8], divisor: &[u8]) -> Vec<u8> {
		let mut result = vec![0u8; divisor.len()];
		for b in data {  // Polynomial division
			let factor: u8 = b ^ result.remove(0);
			result.push(0);
			for (x, &y) in result.iter_mut().zip(divisor.iter()) {
				*x ^= QrCode::reed_solomon_multiply(y, factor);
			}
		}
		result
	}
	
	
	// Returns the product of the two given field elements modulo GF(2^8/0x11D).
	// All inputs are valid. This could be implemented as a 256*256 lookup table.
	fn reed_solomon_multiply(x: u8, y: u8) -> u8 {
		// Russian peasant multiplication
		let mut z: u8 = 0;
		for i in (0 .. 8).rev() {
			z = (z << 1) ^ ((z >> 7) * 0x1D);
			z ^= ((y >> i) & 1) * x;
		}
		z
	}
	
}


/*---- Helper struct for get_penalty_score() ----*/

struct FinderPenalty {
	qr_size: i32,
	run_history: [i32; 7],
}


impl FinderPenalty {
	
	pub fn new(size: i32) -> Self {
		Self {
			qr_size: size,
			run_history: [0i32; 7],
		}
	}
	
	
	// Pushes the given value to the front and drops the last value.
	pub fn add_history(&mut self, mut currentrunlength: i32) {
		if self.run_history[0] == 0 {
			currentrunlength += self.qr_size;  // Add white border to initial run
		}
		let rh = &mut self.run_history;
		for i in (0 .. rh.len()-1).rev() {
			rh[i + 1] = rh[i];
		}
		rh[0] = currentrunlength;
	}
	
	
	// Can only be called immediately after a white run is added, and returns either 0, 1, or 2.
	pub fn count_patterns(&self) -> i32 {
		let rh = &self.run_history;
		let n = rh[1];
		assert!(n <= self.qr_size * 3);
		let core = n > 0 && rh[2] == n && rh[3] == n * 3 && rh[4] == n && rh[5] == n;
		( i32::from(core && rh[0] >= n * 4 && rh[6] >= n)
		+ i32::from(core && rh[6] >= n * 4 && rh[0] >= n))
	}
	
	
	// Must be called at the end of a line (row or column) of modules.
	pub fn terminate_and_count(mut self, currentruncolor: bool, mut currentrunlength: i32) -> i32 {
		if currentruncolor {  // Terminate black run
			self.add_history(currentrunlength);
			currentrunlength = 0;
		}
		currentrunlength += self.qr_size;  // Add white border to final run
		self.add_history(currentrunlength);
		self.count_patterns()
	}
	
}


/*---- Constants and tables ----*/

// For use in get_penalty_score(), when evaluating which mask is best.
const PENALTY_N1: i32 =  3;
const PENALTY_N2: i32 =  3;
const PENALTY_N3: i32 = 40;
const PENALTY_N4: i32 = 10;


static ECC_CODEWORDS_PER_BLOCK: [[i8; 41]; 4] = [
	// Version: (note that index 0 is for padding, and is set to an illegal value)
	//0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
	[-1,  7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],  // Low
	[-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28],  // Medium
	[-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],  // Quartile
	[-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],  // High
];

static NUM_ERROR_CORRECTION_BLOCKS: [[i8; 41]; 4] = [
	// Version: (note that index 0 is for padding, and is set to an illegal value)
	//0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
	[-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4,  4,  4,  4,  4,  6,  6,  6,  6,  7,  8,  8,  9,  9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25],  // Low
	[-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5,  5,  8,  9,  9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49],  // Medium
	[-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8,  8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68],  // Quartile
	[-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81],  // High
];



/*---- QrCodeEcc functionality ----*/

/// The error correction level in a QR Code symbol.
#[derive(Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Debug)]
pub enum QrCodeEcc {
	/// The QR Code can tolerate about  7% erroneous codewords.
	Low     ,
	/// The QR Code can tolerate about 15% erroneous codewords.
	Medium  ,
	/// The QR Code can tolerate about 25% erroneous codewords.
	Quartile,
	/// The QR Code can tolerate about 30% erroneous codewords.
	High    ,
}


impl QrCodeEcc {
	
	// Returns an unsigned 2-bit integer (in the range 0 to 3).
	fn ordinal(self) -> usize {
		use QrCodeEcc::*;
		match self {
			Low      => 0,
			Medium   => 1,
			Quartile => 2,
			High     => 3,
		}
	}
	
	
	// Returns an unsigned 2-bit integer (in the range 0 to 3).
	fn format_bits(self) -> u8 {
		use QrCodeEcc::*;
		match self {
			Low      => 1,
			Medium   => 0,
			Quartile => 3,
			High     => 2,
		}
	}
	
}



/*---- QrSegment functionality ----*/

/// A segment of character/binary/control data in a QR Code symbol.
/// 
/// Instances of this struct are immutable.
/// 
/// The mid-level way to create a segment is to take the payload data
/// and call a static factory function such as `QrSegment::make_numeric()`.
/// The low-level way to create a segment is to custom-make the bit buffer
/// and call the `QrSegment::new()` constructor with appropriate values.
/// 
/// This segment struct imposes no length restrictions, but QR Codes have restrictions.
/// Even in the most favorable conditions, a QR Code can only hold 7089 characters of data.
/// Any segment longer than this is meaningless for the purpose of generating QR Codes.
#[derive(Clone, PartialEq, Eq)]
pub struct QrSegment {
	
	// The mode indicator of this segment. Accessed through mode().
	mode: QrSegmentMode,
	
	// The length of this segment's unencoded data. Measured in characters for
	// numeric/alphanumeric/kanji mode, bytes for byte mode, and 0 for ECI mode.
	// Not the same as the data's bit length. Accessed through num_chars().
	numchars: usize,
	
	// The data bits of this segment. Accessed through data().
	data: Vec<bool>,
	
}


impl QrSegment {
	
	/*---- Static factory functions (mid level) ----*/
	
	/// Returns a segment representing the given binary data encoded in byte mode.
	/// 
	/// All input byte slices are acceptable.
	/// 
	/// Any text string can be converted to UTF-8 bytes and encoded as a byte mode segment.
	pub fn make_bytes(data: &[u8]) -> Self {
		let mut bb = BitBuffer(Vec::with_capacity(data.len() * 8));
		for &b in data {
			bb.append_bits(u32::from(b), 8);
		}
		QrSegment::new(QrSegmentMode::Byte, data.len(), bb.0)
	}
	
	
	/// Returns a segment representing the given string of decimal digits encoded in numeric mode.
	/// 
	/// Panics if the string contains non-digit characters.
	pub fn make_numeric(text: &[char]) -> Self {
		let mut bb = BitBuffer(Vec::with_capacity(text.len() * 3 + (text.len() + 2) / 3));
		let mut accumdata: u32 = 0;
		let mut accumcount: u8 = 0;
		for &c in text {
			assert!('0' <= c && c <= '9', "String contains non-numeric characters");
			accumdata = accumdata * 10 + (u32::from(c) - u32::from('0'));
			accumcount += 1;
			if accumcount == 3 {
				bb.append_bits(accumdata, 10);
				accumdata = 0;
				accumcount = 0;
			}
		}
		if accumcount > 0 {  // 1 or 2 digits remaining
			bb.append_bits(accumdata, accumcount * 3 + 1);
		}
		QrSegment::new(QrSegmentMode::Numeric, text.len(), bb.0)
	}
	
	
	/// Returns a segment representing the given text string encoded in alphanumeric mode.
	/// 
	/// The characters allowed are: 0 to 9, A to Z (uppercase only), space,
	/// dollar, percent, asterisk, plus, hyphen, period, slash, colon.
	/// 
	/// Panics if the string contains non-encodable characters.
	pub fn make_alphanumeric(text: &[char]) -> Self {
		let mut bb = BitBuffer(Vec::with_capacity(text.len() * 5 + (text.len() + 1) / 2));
		let mut accumdata: u32 = 0;
		let mut accumcount: u32 = 0;
		for &c in text {
			let i: usize = ALPHANUMERIC_CHARSET.iter().position(|&x| x == c)
				.expect("String contains unencodable characters in alphanumeric mode");
			accumdata = accumdata * 45 + (i as u32);
			accumcount += 1;
			if accumcount == 2 {
				bb.append_bits(accumdata, 11);
				accumdata = 0;
				accumcount = 0;
			}
		}
		if accumcount > 0 {  // 1 character remaining
			bb.append_bits(accumdata, 6);
		}
		QrSegment::new(QrSegmentMode::Alphanumeric, text.len(), bb.0)
	}
	
	
	/// Returns a list of zero or more segments to represent the given Unicode text string.
	/// 
	/// The result may use various segment modes and switch
	/// modes to optimize the length of the bit stream.
	pub fn make_segments(text: &[char]) -> Vec<Self> {
		if text.is_empty() {
			vec![]
		} else if QrSegment::is_numeric(text) {
			vec![QrSegment::make_numeric(text)]
		} else if QrSegment::is_alphanumeric(text) {
			vec![QrSegment::make_alphanumeric(text)]
		} else {
			let s: String = text.iter().cloned().collect();
			vec![QrSegment::make_bytes(s.as_bytes())]
		}
	}
	
	
	/// Returns a segment representing an Extended Channel Interpretation
	/// (ECI) designator with the given assignment value.
	pub fn make_eci(assignval: u32) -> Self {
		let mut bb = BitBuffer(Vec::with_capacity(24));
		if assignval < (1 << 7) {
			bb.append_bits(assignval, 8);
		} else if assignval < (1 << 14) {
			bb.append_bits(2, 2);
			bb.append_bits(assignval, 14);
		} else if assignval < 1_000_000 {
			bb.append_bits(6, 3);
			bb.append_bits(assignval, 21);
		} else {
			panic!("ECI assignment value out of range");
		}
		QrSegment::new(QrSegmentMode::Eci, 0, bb.0)
	}
	
	
	/*---- Constructor (low level) ----*/
	
	/// Creates a new QR Code segment with the given attributes and data.
	/// 
	/// The character count (numchars) must agree with the mode and
	/// the bit buffer length, but the constraint isn't checked.
	pub fn new(mode: QrSegmentMode, numchars: usize, data: Vec<bool>) -> Self {
		Self { mode, numchars, data }
	}
	
	
	/*---- Instance field getters ----*/
	
	/// Returns the mode indicator of this segment.
	pub fn mode(&self) -> QrSegmentMode {
		self.mode
	}
	
	
	/// Returns the character count field of this segment.
	pub fn num_chars(&self) -> usize {
		self.numchars
	}
	
	
	/// Returns the data bits of this segment.
	pub fn data(&self) -> &Vec<bool> {
		&self.data
	}
	
	
	/*---- Other static functions ----*/
	
	// Calculates and returns the number of bits needed to encode the given
	// segments at the given version. The result is None if a segment has too many
	// characters to fit its length field, or the total bits exceeds usize::MAX.
	fn get_total_bits(segs: &[Self], version: Version) -> Option<usize> {
		let mut result: usize = 0;
		for seg in segs {
			let ccbits: u8 = seg.mode.num_char_count_bits(version);
			// ccbits can be as large as 16, but usize can be as small as 16
			if let Some(limit) = 1usize.checked_shl(u32::from(ccbits)) {
				if seg.numchars >= limit {
					return None;  // The segment's length doesn't fit the field's bit width
				}
			}
			result = result.checked_add(4 + usize::from(ccbits))?;
			result = result.checked_add(seg.data.len())?;
		}
		Some(result)
	}
	
	
	// Tests whether the given string can be encoded as a segment in alphanumeric mode.
	// A string is encodable iff each character is in the following set: 0 to 9, A to Z
	// (uppercase only), space, dollar, percent, asterisk, plus, hyphen, period, slash, colon.
	fn is_alphanumeric(text: &[char]) -> bool {
		text.iter().all(|c| ALPHANUMERIC_CHARSET.contains(c))
	}
	
	
	// Tests whether the given string can be encoded as a segment in numeric mode.
	// A string is encodable iff each character is in the range 0 to 9.
	fn is_numeric(text: &[char]) -> bool {
		text.iter().all(|&c| '0' <= c && c <= '9')
	}
	
}


// The set of all legal characters in alphanumeric mode,
// where each character value maps to the index in the string.
static ALPHANUMERIC_CHARSET: [char; 45] = ['0','1','2','3','4','5','6','7','8','9',
	'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z',
	' ','$','%','*','+','-','.','/',':'];



/*---- QrSegmentMode functionality ----*/

/// Describes how a segment's data bits are interpreted.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum QrSegmentMode {
	Numeric,
	Alphanumeric,
	Byte,
	Kanji,
	Eci,
}


impl QrSegmentMode {
	
	// Returns an unsigned 4-bit integer value (range 0 to 15)
	// representing the mode indicator bits for this mode object.
	fn mode_bits(self) -> u32 {
		use QrSegmentMode::*;
		match self {
			Numeric      => 0x1,
			Alphanumeric => 0x2,
			Byte         => 0x4,
			Kanji        => 0x8,
			Eci          => 0x7,
		}
	}
	
	
	// Returns the bit width of the character count field for a segment in this mode
	// in a QR Code at the given version number. The result is in the range [0, 16].
	fn num_char_count_bits(self, ver: Version) -> u8 {
		use QrSegmentMode::*;
		(match self {
			Numeric      => [10, 12, 14],
			Alphanumeric => [ 9, 11, 13],
			Byte         => [ 8, 16, 16],
			Kanji        => [ 8, 10, 12],
			Eci          => [ 0,  0,  0],
		})[usize::from((ver.value() + 7) / 17)]
	}
	
}



/*---- Bit buffer functionality ----*/

/// An appendable sequence of bits (0s and 1s).
/// 
/// Mainly used by QrSegment.
pub struct BitBuffer(pub Vec<bool>);


impl BitBuffer {
	/// Appends the given number of low-order bits of the given value to this buffer.
	/// 
	/// Requires len &#x2264; 31 and val &lt; 2<sup>len</sup>.
	pub fn append_bits(&mut self, val: u32, len: u8) {
		assert!(len <= 31 && (val >> len) == 0, "Value out of range");
		self.0.extend((0 .. i32::from(len)).rev().map(|i| get_bit(val, i)));  // Append bit by bit
	}
}



/*---- Miscellaneous values ----*/

/// The error type when the supplied data does not fit any QR Code version.
///
/// Ways to handle this exception include:
/// 
/// - Decrease the error correction level if it was greater than `QrCodeEcc::Low`.
/// - If the `encode_segments_advanced()` function was called, then increase the maxversion
///   argument if it was less than `Version::MAX`. (This advice does not apply to the
///   other factory functions because they search all versions up to `Version::MAX`.)
/// - Split the text data into better or optimal segments in order to reduce the number of bits required.
/// - Change the text or binary data to be shorter.
/// - Change the text to fit the character set of a particular segment mode (e.g. alphanumeric).
/// - Propagate the error upward to the caller/user.
#[derive(Debug, Clone)]
pub struct DataTooLong(String);

impl std::error::Error for DataTooLong {
	fn description(&self) -> &str {
		&self.0
	}
}

impl std::fmt::Display for DataTooLong {
	fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
		f.write_str(&self.0)
	}
}


/// A number between 1 and 40 (inclusive).
#[derive(Copy, Clone, PartialEq, Eq, PartialOrd, Ord, Debug)]
pub struct Version(u8);

impl Version {
	/// The minimum version number supported in the QR Code Model 2 standard.
	pub const MIN: Version = Version( 1);
	
	/// The maximum version number supported in the QR Code Model 2 standard.
	pub const MAX: Version = Version(40);
	
	/// Creates a version object from the given number.
	/// 
	/// Panics if the number is outside the range [1, 40].
	pub fn new(ver: u8) -> Self {
		assert!(Version::MIN.value() <= ver && ver <= Version::MAX.value(), "Version number out of range");
		Self(ver)
	}
	
	/// Returns the value, which is in the range [1, 40].
	pub fn value(self) -> u8 {
		self.0
	}
}


/// A number between 0 and 7 (inclusive).
#[derive(Copy, Clone, PartialEq, Eq, PartialOrd, Ord, Debug)]
pub struct Mask(u8);

impl Mask {
	/// Creates a mask object from the given number.
	/// 
	/// Panics if the number is outside the range [0, 7].
	pub fn new(mask: u8) -> Self {
		assert!(mask <= 7, "Mask value out of range");
		Self(mask)
	}
	
	/// Returns the value, which is in the range [0, 7].
	pub fn value(self) -> u8 {
		self.0
	}
}


// Returns true iff the i'th bit of x is set to 1.
fn get_bit(x: u32, i: i32) -> bool {
	(x >> i) & 1 != 0
}
