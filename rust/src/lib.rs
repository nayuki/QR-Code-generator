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


/*---- QrCode functionality ----*/

// Represents an immutable square grid of black and white cells for a QR Code symbol, and
// provides static functions to create a QR Code from user-supplied textual or binary data.
// This struct covers the QR Code model 2 specification, supporting all versions (sizes)
// from 1 to 40, all 4 error correction levels, and only 3 character encoding modes.
pub struct QrCode {
	
	// This QR Code symbol's version number, which is always between 1 and 40 (inclusive).
	version: Version,
	
	// The width and height of this QR Code symbol, measured in modules.
	// Always equal to version &times; 4 + 17, in the range 21 to 177.
	size: i32,
	
	// The error correction level used in this QR Code symbol.
	errorcorrectionlevel: QrCodeEcc,
	
	// The mask pattern used in this QR Code symbol, in the range 0 to 7 (i.e. unsigned 3-bit integer).
	// Note that even if a constructor was called with automatic masking requested
	// (mask = -1), the resulting object will still have a mask value between 0 and 7.
	mask: Mask,
	
	// The modules of this QR Code symbol (false = white, true = black)
	modules: Vec<bool>,
	
	// Indicates function modules that are not subjected to masking
	isfunction: Vec<bool>,
	
}


impl QrCode {
	
	/*---- Public static factory functions ----*/
	
	// Returns a QR Code symbol representing the given Unicode text string at the given error correction level.
	// As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer Unicode
	// code points (not UTF-8 code units) if the low error correction level is used. The smallest possible
	// QR Code version is automatically chosen for the output. The ECC level of the result may be higher than
	// the ecl argument if it can be done without increasing the version. Returns a wrapped QrCode if successful,
	// or None if the data is too long to fit in any version at the given ECC level.
	pub fn encode_text(text: &str, ecl: QrCodeEcc) -> Option<QrCode> {
		let chrs: Vec<char> = text.chars().collect();
		let segs: Vec<QrSegment> = QrSegment::make_segments(&chrs);
		QrCode::encode_segments(&segs, ecl)
	}
	
	
	// Returns a QR Code symbol representing the given binary data string at the given error correction level.
	// This function always encodes using the binary segment mode, not any text mode. The maximum number of
	// bytes allowed is 2953. The smallest possible QR Code version is automatically chosen for the output.
	// The ECC level of the result may be higher than the ecl argument if it can be done without increasing the version.
	// Returns a wrapped QrCode if successful, or None if the data is too long to fit in any version at the given ECC level.
	pub fn encode_binary(data: &[u8], ecl: QrCodeEcc) -> Option<QrCode> {
		let segs: Vec<QrSegment> = vec![QrSegment::make_bytes(data)];
		QrCode::encode_segments(&segs, ecl)
	}
	
	
	// Returns a QR Code symbol representing the given data segments at the given error correction
	// level or higher. The smallest possible QR Code version is automatically chosen for the output.
	// This function allows the user to create a custom sequence of segments that switches
	// between modes (such as alphanumeric and binary) to encode text more efficiently.
	// This function is considered to be lower level than simply encoding text or binary data.
	// Returns a wrapped QrCode if successful, or None if the data is too long to fit in any version at the given ECC level.
	pub fn encode_segments(segs: &[QrSegment], ecl: QrCodeEcc) -> Option<QrCode> {
		QrCode::encode_segments_advanced(segs, ecl, QrCode_MIN_VERSION, QrCode_MAX_VERSION, None, true)
	}
	
	
	// Returns a QR Code symbol representing the given data segments with the given encoding parameters.
	// The smallest possible QR Code version within the given range is automatically chosen for the output.
	// This function allows the user to create a custom sequence of segments that switches
	// between modes (such as alphanumeric and binary) to encode text more efficiently.
	// This function is considered to be lower level than simply encoding text or binary data.
	// Returns a wrapped QrCode if successful, or None if the data is too long to fit
	// in any version in the given range at the given ECC level.
	pub fn encode_segments_advanced(segs: &[QrSegment], mut ecl: QrCodeEcc,
			minversion: Version, maxversion: Version, mask: Option<Mask>, boostecl: bool) -> Option<QrCode> {
		assert!(minversion.value() <= maxversion.value(), "Invalid value");
		
		// Find the minimal version number to use
		let mut version = minversion;
		let datausedbits: usize;
		loop {
			// Number of data bits available
			let datacapacitybits: usize = QrCode::get_num_data_codewords(version, ecl) * 8;
			if let Some(n) = QrSegment::get_total_bits(segs, version) {
				if n <= datacapacitybits {
					datausedbits = n;
					break;  // This version number is found to be suitable
				}
			}
			if version.value() >= maxversion.value() {  // All versions in the range could not fit the given data
				return None;
			}
			version = Version::new(version.value() + 1);
		}
		
		// Increase the error correction level while the data still fits in the current version number
		for newecl in &[QrCodeEcc::Medium, QrCodeEcc::Quartile, QrCodeEcc::High] {
			if boostecl && datausedbits <= QrCode::get_num_data_codewords(version, *newecl) * 8 {
				ecl = *newecl;
			}
		}
		
		// Create the data bit string by concatenating all segments
		let datacapacitybits: usize = QrCode::get_num_data_codewords(version, ecl) * 8;
		let mut bb = BitBuffer(Vec::new());
		for seg in segs {
			bb.append_bits(seg.mode.mode_bits(), 4);
			bb.append_bits(seg.numchars as u32, seg.mode.num_char_count_bits(version));
			bb.0.extend_from_slice(&seg.data);
		}
		
		// Add terminator and pad up to a byte if applicable
		let numzerobits = std::cmp::min(4, datacapacitybits - bb.0.len());
		bb.append_bits(0, numzerobits as u8);
		let numzerobits = bb.0.len().wrapping_neg() & 7;
		bb.append_bits(0, numzerobits as u8);
		
		// Pad with alternate bytes until data capacity is reached
		let mut padbyte: u32 = 0xEC;
		while bb.0.len() < datacapacitybits {
			bb.append_bits(padbyte, 8);
			padbyte ^= 0xEC ^ 0x11;
		}
		assert_eq!(bb.0.len() % 8, 0, "Assertion error");
		
		let mut bytes = vec![0u8; bb.0.len() / 8];
		for (i, bit) in bb.0.iter().enumerate() {
			bytes[i >> 3] |= (*bit as u8) << (7 - (i & 7));
		}
		
		// Create the QR Code symbol
		Some(QrCode::encode_codewords(version, ecl, &bytes, mask))
	}
	
	
	/*---- Constructors ----*/
	
	// Creates a new QR Code symbol with the given version number, error correction level,
	// binary data array, and mask number. This is a cumbersome low-level constructor that
	// should not be invoked directly by the user. To go one level up, see the encode_segments() function.
	pub fn encode_codewords(ver: Version, ecl: QrCodeEcc, datacodewords: &[u8], mask: Option<Mask>) -> QrCode {
		// Initialize fields
		let size: usize = (ver.value() as usize) * 4 + 17;
		let mut result = QrCode {
			version: ver,
			size: size as i32,
			mask: Mask::new(0),  // Dummy value
			errorcorrectionlevel: ecl,
			modules: vec![false; size * size],  // Entirely white grid
			isfunction: vec![false; size * size],
		};
		
		// Draw function patterns, draw all codewords, do masking
		result.draw_function_patterns();
		let allcodewords: Vec<u8> = result.append_error_correction(datacodewords);
		result.draw_codewords(&allcodewords);
		result.handle_constructor_masking(mask);
		result
	}
	
	
	// Returns this QR Code's version, in the range [1, 40].
	pub fn version(&self) -> Version {
		self.version
	}
	
	
	// Returns this QR Code's size, in the range [21, 177].
	pub fn size(&self) -> i32 {
		self.size
	}
	
	
	// Returns this QR Code's error correction level.
	pub fn error_correction_level(&self) -> QrCodeEcc {
		self.errorcorrectionlevel
	}
	
	
	// Returns this QR Code's mask, in the range [0, 7].
	pub fn mask(&self) -> Mask {
		self.mask
	}
	
	
	// Returns the color of the module (pixel) at the given coordinates, which is either
	// false for white or true for black. The top left corner has the coordinates (x=0, y=0).
	// If the given coordinates are out of bounds, then 0 (white) is returned.
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
	
	
	// Based on the given number of border modules to add as padding, this returns a
	// string whose contents represents an SVG XML file that depicts this QR Code symbol.
	// Note that Unix newlines (\n) are always used, regardless of the platform.
	pub fn to_svg_string(&self, border: i32) -> String {
		assert!(border >= 0, "Border must be non-negative");
		let mut result: String = String::new();
		result.push_str("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		result.push_str("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n");
		let dimension = self.size.checked_add(border.checked_mul(2).unwrap()).unwrap();
		result.push_str(&format!(
			"<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 {0} {0}\" stroke=\"none\">\n", dimension));
		result.push_str("\t<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n");
		result.push_str("\t<path d=\"");
		let mut head: bool = true;
		for y in -border .. self.size + border {
			for x in -border .. self.size + border {
				if self.get_module(x, y) {
					if head {
						head = false;
					} else {
						result.push_str(" ");
					}
					result.push_str(&format!("M{},{}h1v1h-1z", x + border, y + border));
				}
			}
		}
		result.push_str("\" fill=\"#000000\"/>\n");
		result.push_str("</svg>\n");
		result
	}
	
	
	/*---- Private helper methods for constructor: Drawing function modules ----*/
	
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
		let alignpatpos: Vec<i32> = QrCode::get_alignment_pattern_positions(self.version);
		let numalign: usize = alignpatpos.len();
		for i in 0 .. numalign {
			for j in 0 .. numalign {
				if i == 0 && j == 0 || i == 0 && j == numalign - 1 || i == numalign - 1 && j == 0 {
					continue;  // Skip the three finder corners
				} else {
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
		let size: i32 = self.size;
		// errcorrlvl is uint2, mask is uint3
		let mut data: u32 = self.errorcorrectionlevel.format_bits() << 3 | (mask.value() as u32);
		let mut rem: u32 = data;
		for _ in 0 .. 10 {
			rem = (rem << 1) ^ ((rem >> 9) * 0x537);
		}
		data = data << 10 | rem;
		data ^= 0x5412;  // uint15
		assert_eq!(data >> 15, 0, "Assertion error");
		
		// Draw first copy
		for i in 0 .. 6 {
			self.set_function_module(8, i, (data >> i) & 1 != 0);
		}
		self.set_function_module(8, 7, (data >> 6) & 1 != 0);
		self.set_function_module(8, 8, (data >> 7) & 1 != 0);
		self.set_function_module(7, 8, (data >> 8) & 1 != 0);
		for i in 9 .. 15 {
			self.set_function_module(14 - i, 8, (data >> i) & 1 != 0);
		}
		
		// Draw second copy
		for i in 0 .. 8 {
			self.set_function_module(size - 1 - i, 8, (data >> i) & 1 != 0);
		}
		for i in 8 .. 15 {
			self.set_function_module(8, size - 15 + i, (data >> i) & 1 != 0);
		}
		self.set_function_module(8, size - 8, true);
	}
	
	
	// Draws two copies of the version bits (with its own error correction code),
	// based on this object's version field (which only has an effect for 7 <= version <= 40).
	fn draw_version(&mut self) {
		if self.version.value() < 7 {
			return;
		}
		
		// Calculate error correction code and pack bits
		let mut rem: u32 = self.version.value() as u32;  // version is uint6, in the range [7, 40]
		for _ in 0 .. 12 {
			rem = (rem << 1) ^ ((rem >> 11) * 0x1F25);
		}
		let data: u32 = (self.version.value() as u32) << 12 | rem;  // uint18
		assert!(data >> 18 == 0, "Assertion error");
		
		// Draw two copies
		for i in 0 .. 18 {
			let bit: bool = (data >> i) & 1 != 0;
			let a: i32 = self.size - 11 + i % 3;
			let b: i32 = i / 3;
			self.set_function_module(a, b, bit);
			self.set_function_module(b, a, bit);
		}
	}
	
	
	// Draws a 9*9 finder pattern including the border separator, with the center module at (x, y).
	fn draw_finder_pattern(&mut self, x: i32, y: i32) {
		for i in -4 .. 5 {
			for j in -4 .. 5 {
				let xx: i32 = x + j;
				let yy: i32 = y + i;
				if 0 <= xx && xx < self.size && 0 <= yy && yy < self.size {
					let dist: i32 = std::cmp::max(i.abs(), j.abs());  // Chebyshev/infinity norm
					self.set_function_module(xx, yy, dist != 2 && dist != 4);
				}
			}
		}
	}
	
	
	// Draws a 5*5 alignment pattern, with the center module at (x, y).
	fn draw_alignment_pattern(&mut self, x: i32, y: i32) {
		for i in -2 .. 3 {
			for j in -2 .. 3 {
				self.set_function_module(x + j, y + i, std::cmp::max(i.abs(), j.abs()) != 1);
			}
		}
	}
	
	
	// Sets the color of a module and marks it as a function module.
	// Only used by the constructor. Coordinates must be in range.
	fn set_function_module(&mut self, x: i32, y: i32, isblack: bool) {
		*self.module_mut(x, y) = isblack;
		self.isfunction[(y * self.size + x) as usize] = true;
	}
	
	
	/*---- Private helper methods for constructor: Codewords and masking ----*/
	
	// Returns a new byte string representing the given data with the appropriate error correction
	// codewords appended to it, based on this object's version and error correction level.
	fn append_error_correction(&self, data: &[u8]) -> Vec<u8> {
		assert_eq!(data.len(), QrCode::get_num_data_codewords(self.version, self.errorcorrectionlevel), "Illegal argument");
		
		// Calculate parameter numbers
		let numblocks: usize = QrCode::table_get(&NUM_ERROR_CORRECTION_BLOCKS, self.version, self.errorcorrectionlevel);
		let blockecclen: usize = QrCode::table_get(&ECC_CODEWORDS_PER_BLOCK, self.version, self.errorcorrectionlevel);
		let rawcodewords: usize = QrCode::get_num_raw_data_modules(self.version) / 8;
		let numshortblocks: usize = numblocks - rawcodewords % numblocks;
		let shortblocklen: usize = rawcodewords / numblocks;
		
		// Split data into blocks and append ECC to each block
		let mut blocks = Vec::<Vec<u8>>::with_capacity(numblocks);
		let rs = ReedSolomonGenerator::new(blockecclen);
		let mut k: usize = 0;
		for i in 0 .. numblocks {
			let mut dat = Vec::<u8>::with_capacity(shortblocklen + 1);
			dat.extend_from_slice(&data[k .. k + shortblocklen - blockecclen + ((i >= numshortblocks) as usize)]);
			k += dat.len();
			let ecc: Vec<u8> = rs.get_remainder(&dat);
			if i < numshortblocks {
				dat.push(0);
			}
			dat.extend_from_slice(&ecc);
			blocks.push(dat);
		}
		
		// Interleave (not concatenate) the bytes from every block into a single sequence
		let mut result = Vec::<u8>::with_capacity(rawcodewords);
		for i in 0 .. shortblocklen + 1 {
			for j in 0 .. numblocks {
				// Skip the padding byte in short blocks
				if i != shortblocklen - blockecclen || j >= numshortblocks {
					result.push(blocks[j][i]);
				}
			}
		}
		result
	}
	
	
	// Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
	// data area of this QR Code symbol. Function modules need to be marked off before this is called.
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
						*self.module_mut(x, y) = (data[i >> 3] >> (7 - (i & 7))) & 1 != 0;
						i += 1;
					}
					// If there are any remainder bits (0 to 7), they are already
					// set to 0/false/white when the grid of modules was initialized
				}
			}
			right -= 2;
		}
		assert_eq!(i, data.len() * 8, "Assertion error");
	}
	
	
	// XORs the data modules in this QR Code with the given mask pattern. Due to XOR's mathematical
	// properties, calling applyMask(m) twice with the same value is equivalent to no change at all.
	// This means it is possible to apply a mask, undo it, and try another mask. Note that a final
	// well-formed QR Code symbol needs exactly one mask applied (not zero, not two, etc.).
	fn apply_mask(&mut self, mask: Mask) {
		let mask = mask.value();
		for y in 0 .. self.size {
			for x in 0 .. self.size {
				let invert: bool = match mask {
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
	
	
	// A messy helper function for the constructors. This QR Code must be in an unmasked state when this
	// method is called. The given argument is the requested mask, which is -1 for auto or 0 to 7 for fixed.
	// This method applies and returns the actual mask chosen, from 0 to 7.
	fn handle_constructor_masking(&mut self, mut mask: Option<Mask>) {
		if mask.is_none() {  // Automatically choose best mask
			let mut minpenalty: i32 = std::i32::MAX;
			for i in 0u8 .. 8 {
				let newmask = Mask::new(i);
				self.draw_format_bits(newmask);
				self.apply_mask(newmask);
				let penalty: i32 = self.get_penalty_score();
				if penalty < minpenalty {
					mask = Some(newmask);
					minpenalty = penalty;
				}
				self.apply_mask(newmask);  // Undoes the mask due to XOR
			}
		}
		let msk: Mask = mask.unwrap();
		self.draw_format_bits(msk);  // Overwrite old format bits
		self.apply_mask(msk);  // Apply the final choice of mask
		self.mask = msk;
	}
	
	
	// Calculates and returns the penalty score based on state of this QR Code's current modules.
	// This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
	fn get_penalty_score(&self) -> i32 {
		let mut result: i32 = 0;
		let size: i32 = self.size;
		
		// Adjacent modules in row having same color
		for y in 0 .. size {
			let mut colorx: bool = false;
			let mut runx: i32 = 0;
			for x in 0 .. size {
				if x == 0 || self.module(x, y) != colorx {
					colorx = self.module(x, y);
					runx = 1;
				} else {
					runx += 1;
					if runx == 5 {
						result += PENALTY_N1;
					} else if runx > 5 {
						result += 1;
					}
				}
			}
		}
		// Adjacent modules in column having same color
		for x in 0 .. size {
			let mut colory: bool = false;
			let mut runy: i32 = 0;
			for y in 0 .. size {
				if y == 0 || self.module(x, y) != colory {
					colory = self.module(x, y);
					runy = 1;
				} else {
					runy += 1;
					if runy == 5 {
						result += PENALTY_N1;
					} else if runy > 5 {
						result += 1;
					}
				}
			}
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
		
		// Finder-like pattern in rows
		for y in 0 .. size {
			let mut bits: u32 = 0;
			for x in 0 .. size {
				bits = ((bits << 1) & 0x7FF) | (self.module(x, y) as u32);
				if x >= 10 && (bits == 0x05D || bits == 0x5D0) {  // Needs 11 bits accumulated
					result += PENALTY_N3;
				}
			}
		}
		// Finder-like pattern in columns
		for x in 0 .. size {
			let mut bits: u32 = 0;
			for y in 0 .. size {
				bits = ((bits << 1) & 0x7FF) | (self.module(x, y) as u32);
				if y >= 10 && (bits == 0x05D || bits == 0x5D0) {  // Needs 11 bits accumulated
					result += PENALTY_N3;
				}
			}
		}
		
		// Balance of black and white modules
		let mut black: i32 = 0;
		for color in &self.modules {
			black += *color as i32;
		}
		let total: i32 = size * size;
		// Find smallest k such that (45-5k)% <= dark/total <= (55+5k)%
		let mut k: i32 = 0;
		while black*20 < (9-k)*total || black*20 > (11+k)*total {
			result += PENALTY_N4;
			k += 1;
		}
		result
	}
	
	
	/*---- Private static helper functions ----*/
	
	// Returns a set of positions of the alignment patterns in ascending order. These positions are
	// used on both the x and y axes. Each value in the resulting list is in the range [0, 177).
	// This stateless pure function could be implemented as table of 40 variable-length lists of unsigned bytes.
	fn get_alignment_pattern_positions(ver: Version) -> Vec<i32> {
		let ver = ver.value();
		if ver == 1 {
			vec![]
		} else {
			let numalign: i32 = (ver as i32) / 7 + 2;
			let step: i32 = if ver != 32 {
				// ceil((size - 13) / (2*numAlign - 2)) * 2
				((ver as i32) * 4 + numalign * 2 + 1) / (2 * numalign - 2) * 2
			} else {  // C-C-C-Combo breaker!
				26
			};
			let mut result = vec![6i32];
			let mut pos: i32 = (ver as i32) * 4 + 10;
			for _ in 0 .. numalign - 1 {
				result.insert(1, pos);
				pos -= step;
			}
			result
		}
	}
	
	
	// Returns the number of data bits that can be stored in a QR Code of the given version number, after
	// all function modules are excluded. This includes remainder bits, so it might not be a multiple of 8.
	// The result is in the range [208, 29648]. This could be implemented as a 40-entry lookup table.
	fn get_num_raw_data_modules(ver: Version) -> usize {
		let ver = ver.value();
		let mut result: usize = (16 * (ver as usize) + 128) * (ver as usize) + 64;
		if ver >= 2 {
			let numalign: usize = (ver as usize) / 7 + 2;
			result -= (25 * numalign - 10) * numalign - 55;
			if ver >= 7 {
				result -= 18 * 2;  // Subtract version information
			}
		}
		result
	}
	
	
	// Returns the number of 8-bit data (i.e. not error correction) codewords contained in any
	// QR Code of the given version number and error correction level, with remainder bits discarded.
	// This stateless pure function could be implemented as a (40*4)-cell lookup table.
	fn get_num_data_codewords(ver: Version, ecl: QrCodeEcc) -> usize {
		QrCode::get_num_raw_data_modules(ver) / 8
			- QrCode::table_get(&ECC_CODEWORDS_PER_BLOCK, ver, ecl)
			* QrCode::table_get(&NUM_ERROR_CORRECTION_BLOCKS, ver, ecl)
	}
	
	
	// Returns an entry from the given table based on the given values.
	fn table_get(table: &'static [[i8; 41]; 4], ver: Version, ecl: QrCodeEcc) -> usize {
		table[ecl.ordinal()][ver.value() as usize] as usize
	}
	
}


/*---- Public constants ----*/

pub const QrCode_MIN_VERSION: Version = Version( 1);
pub const QrCode_MAX_VERSION: Version = Version(40);


/*---- Private tables of constants ----*/

// For use in get_penalty_score(), when evaluating which mask is best.
const PENALTY_N1: i32 = 3;
const PENALTY_N2: i32 = 3;
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

// Represents the error correction level used in a QR Code symbol. Immutable.
#[derive(Clone, Copy)]
pub enum QrCodeEcc {
	Low,
	Medium,
	Quartile,
	High,
}


impl QrCodeEcc {
	
	// Returns an unsigned 2-bit integer (in the range 0 to 3).
	fn ordinal(&self) -> usize {
		match *self {
			QrCodeEcc::Low      => 0,
			QrCodeEcc::Medium   => 1,
			QrCodeEcc::Quartile => 2,
			QrCodeEcc::High     => 3,
		}
	}
	
	
	// Returns an unsigned 2-bit integer (in the range 0 to 3).
	fn format_bits(&self) -> u32 {
		match *self {
			QrCodeEcc::Low      => 1,
			QrCodeEcc::Medium   => 0,
			QrCodeEcc::Quartile => 3,
			QrCodeEcc::High     => 2,
		}
	}
	
}



/*---- ReedSolomonGenerator functionality ----*/

// Computes the Reed-Solomon error correction codewords for a sequence of data codewords
// at a given degree. Objects are immutable, and the state only depends on the degree.
// This class exists because each data block in a QR Code shares the same the divisor polynomial.
struct ReedSolomonGenerator {
	
	// Coefficients of the divisor polynomial, stored from highest to lowest power, excluding the leading term which
	// is always 1. For example the polynomial x^3 + 255x^2 + 8x + 93 is stored as the uint8 array {255, 8, 93}.
	coefficients: Vec<u8>,
	
}


impl ReedSolomonGenerator {
	
	// Creates a Reed-Solomon ECC generator for the given degree. This could be implemented
	// as a lookup table over all possible parameter values, instead of as an algorithm.
	fn new(degree: usize) -> ReedSolomonGenerator {
		assert!(1 <= degree && degree <= 255, "Degree out of range");
		// Start with the monomial x^0
		let mut coefs = vec![0u8; degree - 1];
		coefs.push(1);
		
		// Compute the product polynomial (x - r^0) * (x - r^1) * (x - r^2) * ... * (x - r^{degree-1}),
		// drop the highest term, and store the rest of the coefficients in order of descending powers.
		// Note that r = 0x02, which is a generator element of this field GF(2^8/0x11D).
		let mut root: u8 = 1;
		for _ in 0 .. degree {  // Unused variable i
			// Multiply the current product by (x - r^i)
			for j in 0 .. degree {
				coefs[j] = ReedSolomonGenerator::multiply(coefs[j], root);
				if j + 1 < coefs.len() {
					coefs[j] ^= coefs[j + 1];
				}
			}
			root = ReedSolomonGenerator::multiply(root, 0x02);
		}
		ReedSolomonGenerator {
			coefficients: coefs
		}
	}
	
	
	// Computes and returns the Reed-Solomon error correction codewords for the given sequence of data codewords.
	fn get_remainder(&self, data: &[u8]) -> Vec<u8> {
		// Compute the remainder by performing polynomial division
		let mut result = vec![0u8; self.coefficients.len()];
		for b in data {
			let factor: u8 = b ^ result.remove(0);
			result.push(0);
			for (x, y) in result.iter_mut().zip(self.coefficients.iter()) {
				*x ^= ReedSolomonGenerator::multiply(*y, factor);
			}
		}
		result
	}
	
	
	// Returns the product of the two given field elements modulo GF(2^8/0x11D). The arguments and result
	// are unsigned 8-bit integers. This could be implemented as a lookup table of 256*256 entries of uint8.
	fn multiply(x: u8, y: u8) -> u8 {
		// Russian peasant multiplication
		let mut z: u8 = 0;
		for i in (0 .. 8).rev() {
			z = (z << 1) ^ ((z >> 7) * 0x1D);
			z ^= ((y >> i) & 1) * x;
		}
		z
	}
	
}



/*---- QrSegment functionality ----*/

// Represents a character string to be encoded in a QR Code symbol.
// Each segment has a mode, and a sequence of characters that is already
// encoded as a sequence of bits. Instances of this struct are immutable.
pub struct QrSegment {
	
	// The mode indicator for this segment.
	mode: QrSegmentMode,
	
	// The length of this segment's unencoded data, measured in characters.
	numchars: usize,
	
	// The bits of this segment.
	data: Vec<bool>,
	
}


impl QrSegment {
	
	/*---- Static factory functions ----*/
	
	// Returns a segment representing the given binary data encoded in byte mode.
	pub fn make_bytes(data: &[u8]) -> QrSegment {
		let mut bb = BitBuffer(Vec::with_capacity(data.len() * 8));
		for b in data {
			bb.append_bits(*b as u32, 8);
		}
		QrSegment::new(QrSegmentMode::Byte, data.len(), bb.0)
	}
	
	
	// Returns a segment representing the given string of decimal digits encoded in numeric mode.
	// Panics if the string contains non-digit characters.
	pub fn make_numeric(text: &[char]) -> QrSegment {
		let mut bb = BitBuffer(Vec::with_capacity(text.len() * 3 + (text.len() + 2) / 3));
		let mut accumdata: u32 = 0;
		let mut accumcount: u32 = 0;
		for c in text {
			assert!('0' <= *c && *c <= '9', "String contains non-numeric characters");
			accumdata = accumdata * 10 + ((*c as u32) - ('0' as u32));
			accumcount += 1;
			if accumcount == 3 {
				bb.append_bits(accumdata, 10);
				accumdata = 0;
				accumcount = 0;
			}
		}
		if accumcount > 0 {  // 1 or 2 digits remaining
			bb.append_bits(accumdata, (accumcount as u8) * 3 + 1);
		}
		QrSegment::new(QrSegmentMode::Numeric, text.len(), bb.0)
	}
	
	
	// Returns a segment representing the given text string encoded in alphanumeric mode.
	// The characters allowed are: 0 to 9, A to Z (uppercase only), space, dollar, percent, asterisk,
	// plus, hyphen, period, slash, colon. Panics if the string contains non-encodable characters.
	pub fn make_alphanumeric(text: &[char]) -> QrSegment {
		let mut bb = BitBuffer(Vec::with_capacity(text.len() * 5 + (text.len() + 1) / 2));
		let mut accumdata: u32 = 0;
		let mut accumcount: u32 = 0;
		for c in text {
			let i = match ALPHANUMERIC_CHARSET.iter().position(|x| *x == *c) {
				None => panic!("String contains unencodable characters in alphanumeric mode"),
				Some(j) => j,
			};
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
	
	
	// Returns a new mutable list of zero or more segments to represent the given Unicode text string.
	// The result may use various segment modes and switch modes to optimize the length of the bit stream.
	pub fn make_segments(text: &[char]) -> Vec<QrSegment> {
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
	
	
	// Returns a segment representing an Extended Channel Interpretation
	// (ECI) designator with the given assignment value.
	pub fn make_eci(assignval: u32) -> QrSegment {
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
	
	
	// Creates a new QR Code data segment with the given parameters and data.
	pub fn new(mode: QrSegmentMode, numchars: usize, data: Vec<bool>) -> QrSegment {
		QrSegment {
			mode: mode,
			numchars: numchars,
			data: data,
		}
	}
	
	
	/*---- Instance field getters ----*/
	
	// Returns the mode indicator for this segment.
	pub fn mode(&self) -> QrSegmentMode {
		self.mode
	}
	
	
	// Returns the length of this segment's unencoded data, measured in characters.
	pub fn num_chars(&self) -> usize {
		self.numchars
	}
	
	
	// Returns a view of the bits of this segment.
	pub fn data(&self) -> &Vec<bool> {
		&self.data
	}
	
	
	/*---- Other static functions ----*/
	
	// Package-private helper function.
	fn get_total_bits(segs: &[QrSegment], version: Version) -> Option<usize> {
		let mut result: usize = 0;
		for seg in segs {
			let ccbits = seg.mode.num_char_count_bits(version);
			if seg.numchars >= 1 << ccbits {
				return None;
			}
			match result.checked_add(4 + (ccbits as usize) + seg.data.len()) {
				None => return None,
				Some(val) => result = val,
			}
		}
		Some(result)
	}
	
	
	// Tests whether the given string can be encoded as a segment in alphanumeric mode.
	fn is_alphanumeric(text: &[char]) -> bool {
		text.iter().all(|c| ALPHANUMERIC_CHARSET.contains(c))
	}
	
	
	// Tests whether the given string can be encoded as a segment in numeric mode.
	fn is_numeric(text: &[char]) -> bool {
		text.iter().all(|c| '0' <= *c && *c <= '9')
	}
	
}


// The set of all legal characters in alphanumeric mode,
// where each character value maps to the index in the string.
static ALPHANUMERIC_CHARSET: [char; 45] = ['0','1','2','3','4','5','6','7','8','9',
	'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z',
	' ','$','%','*','+','-','.','/',':'];



/*---- QrSegmentMode functionality ----*/

// The mode field of a segment. Immutable.
#[derive(Clone, Copy)]
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
	fn mode_bits(&self) -> u32 {
		match *self {
			QrSegmentMode::Numeric      => 0x1,
			QrSegmentMode::Alphanumeric => 0x2,
			QrSegmentMode::Byte         => 0x4,
			QrSegmentMode::Kanji        => 0x8,
			QrSegmentMode::Eci          => 0x7,
		}
	}
	
	
	// Returns the bit width of the segment character count field
	// for this mode object at the given version number.
	pub fn num_char_count_bits(&self, ver: Version) -> u8 {
		let array: [u8; 3] = match *self {
			QrSegmentMode::Numeric      => [10, 12, 14],
			QrSegmentMode::Alphanumeric => [ 9, 11, 13],
			QrSegmentMode::Byte         => [ 8, 16, 16],
			QrSegmentMode::Kanji        => [ 8, 10, 12],
			QrSegmentMode::Eci          => [ 0,  0,  0],
		};
		
		let ver = ver.value();
		if 1 <= ver && ver <= 9 {
			array[0]
		} else if 10 <= ver && ver <= 26 {
			array[1]
		} else if 27 <= ver && ver <= 40 {
			array[2]
		} else {
			panic!("Version number out of range");
		}
	}
	
}



/*---- Bit buffer functionality ----*/

pub struct BitBuffer(pub Vec<bool>);


impl BitBuffer {
	// Appends the given number of low bits of the given value
	// to this sequence. Requires 0 <= val < 2^len.
	pub fn append_bits(&mut self, val: u32, len: u8) {
		assert!(len < 32 && (val >> len) == 0 || len == 32, "Value out of range");
		for i in (0 .. len).rev() {  // Append bit by bit
			self.0.push((val >> i) & 1 != 0);
		}
	}
}



/*---- Miscellaneous values ----*/

#[derive(Copy, Clone)]
pub struct Version(u8);

impl Version {
	pub fn new(ver: u8) -> Self {
		assert!(QrCode_MIN_VERSION.value() <= ver && ver <= QrCode_MAX_VERSION.value(), "Version number out of range");
		Version(ver)
	}
	
	pub fn value(&self) -> u8 {
		self.0
	}
}


#[derive(Copy, Clone)]
pub struct Mask(u8);

impl Mask {
	pub fn new(mask: u8) -> Self {
		assert!(mask <= 7, "Mask value out of range");
		Mask(mask)
	}
	
	pub fn value(&self) -> u8 {
		self.0
	}
}
