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

pub struct QrCode {
	
	// This QR Code symbol's version number, which is always between 1 and 40 (inclusive).
	version: u8,
	
	// The width and height of this QR Code symbol, measured in modules.
	// Always equal to version &times; 4 + 17, in the range 21 to 177.
	size: i32,
	
	// The error correction level used in this QR Code symbol.
	errorcorrectionlevel: &'static QrCodeEcc,
	
	// The mask pattern used in this QR Code symbol, in the range 0 to 7 (i.e. unsigned 3-bit integer).
	// Note that even if a constructor was called with automatic masking requested
	// (mask = -1), the resulting object will still have a mask value between 0 and 7.
	mask: u8,
	
	// The modules of this QR Code symbol (false = white, true = black)
	modules: Vec<bool>,
	
	// Indicates function modules that are not subjected to masking
	isfunction: Vec<bool>,
	
}


impl QrCode {
	
	pub fn encode_text(text: &str, ecl: &'static QrCodeEcc) -> QrCode {
		let chrs: Vec<char> = text.chars().collect();
		let segs: Vec<QrSegment> = QrSegment::make_segments(&chrs);
		QrCode::encode_segments(&segs, ecl)
	}
	
	
	pub fn encode_binary(data: &[u8], ecl: &'static QrCodeEcc) -> QrCode {
		let segs: Vec<QrSegment> = vec![QrSegment::make_bytes(data)];
		QrCode::encode_segments(&segs, ecl)
	}
	
	
	pub fn encode_segments(segs: &[QrSegment], ecl: &'static QrCodeEcc) -> QrCode {
		QrCode::encode_segments_advanced(segs, ecl, 1, 40, -1, true)
	}
	
	
	pub fn encode_segments_advanced(segs: &[QrSegment], mut ecl: &'static QrCodeEcc,
			minversion: u8, maxversion: u8, mask: i8, boostecl: bool) -> QrCode {
		assert!(1 <= minversion && minversion <= maxversion && maxversion <= 40 && -1 <= mask && mask <= 7, "Invalid value");
		
		// Find the minimal version number to use
		let mut version: u8 = minversion;
		let mut datausedbits: usize;
		loop {
			let datacapacitybits: usize = QrCode::get_num_data_codewords(version, ecl) * 8;  // Number of data bits available
			if let Some(n) = QrSegment::get_total_bits(segs, version) {
				if n <= datacapacitybits {
					datausedbits = n;
					break;  // This version number is found to be suitable
				}
			}
			if version >= maxversion {  // All versions in the range could not fit the given data
				panic!("Data too long");
			}
			version += 1;
		}
		
		// Increase the error correction level while the data still fits in the current version number
		if boostecl {
			if datausedbits <= QrCode::get_num_data_codewords(version, &QrCodeEcc_MEDIUM  ) * 8 { ecl = &QrCodeEcc_MEDIUM  ; }
			if datausedbits <= QrCode::get_num_data_codewords(version, &QrCodeEcc_QUARTILE) * 8 { ecl = &QrCodeEcc_QUARTILE; }
			if datausedbits <= QrCode::get_num_data_codewords(version, &QrCodeEcc_HIGH    ) * 8 { ecl = &QrCodeEcc_HIGH    ; }
		}
		
		// Create the data bit string by concatenating all segments
		let datacapacitybits: usize = QrCode::get_num_data_codewords(version, ecl) * 8;
		let mut bb: Vec<bool> = Vec::new();
		for seg in segs {
			append_bits(&mut bb, seg.mode.modebits as u32, 4);
			append_bits(&mut bb, seg.numchars as u32, seg.mode.num_char_count_bits(version));
			bb.extend_from_slice(&seg.data);
		}
		
		// Add terminator and pad up to a byte if applicable
		let numzerobits = std::cmp::min(4, datacapacitybits - bb.len());
		append_bits(&mut bb, 0, numzerobits as u8);
		let numzerobits = bb.len().wrapping_neg() & 7;
		append_bits(&mut bb, 0, numzerobits as u8);
		
		// Pad with alternate bytes until data capacity is reached
		let mut padbyte: u32 = 0xEC;
		while bb.len() < datacapacitybits {
			append_bits(&mut bb, padbyte, 8);
			padbyte ^= 0xEC ^ 0x11;
		}
		assert_eq!(bb.len() % 8, 0, "Assertion error");
		
		let mut bytes: Vec<u8> = vec![0; (bb.len() + 7) / 8];
		for (i, bit) in bb.iter().enumerate() {
			bytes[i >> 3] |= (*bit as u8) << (7 - (i & 7));
		}
		
		// Create the QR Code symbol
		QrCode::encode_codewords(version, ecl, &bytes, mask)
	}
	
	
	pub fn encode_codewords(ver: u8, ecl: &'static QrCodeEcc, datacodewords: &[u8], mask: i8) -> QrCode {
		// Check arguments
		assert!(1 <= ver && ver <= 40 && -1 <= mask && mask <= 7, "Value out of range");
		
		// Initialize fields
		let size: usize = (ver as usize) * 4 + 17;
		let mut result = QrCode {
			version: ver,
			size: size as i32,
			mask: 0,  // Dummy value
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
	
	
	pub fn remask(qr: &QrCode, mask: i8) -> QrCode {
		// Check arguments
		assert!(-1 <= mask && mask <= 7, "Mask out of range");
		
		// Copy fields
		let mut result = QrCode {
			version: qr.version,
			size: qr.size,
			mask: 0,  // Dummy value
			errorcorrectionlevel: qr.errorcorrectionlevel,
			modules: qr.modules.clone(),
			isfunction: qr.isfunction.clone(),
		};
		
		// Handle masking
		result.apply_mask(qr.mask);  // Undo old mask
		result.handle_constructor_masking(mask);
		result
	}
	
	
	// Returns this QR Code's version, in the range [1, 40].
	pub fn version(&self) -> u8 {
		self.version
	}
	
	
	// Returns this QR Code's size, in the range [21, 177].
	pub fn size(&self) -> i32 {
		self.size
	}
	
	
	// Returns this QR Code's error correction level.
	pub fn error_correction_level(&self) -> &'static QrCodeEcc {
		self.errorcorrectionlevel
	}
	
	
	// Returns this QR Code's mask, in the range [0, 7].
	pub fn mask(&self) -> u8 {
		self.mask
	}
	
	
	// Returns the color of the module (pixel) at the given coordinates, which is either false for white or true for black. The top
	// left corner has the coordinates (x=0, y=0). If the given coordinates are out of bounds, then 0 (white) is returned.
	pub fn get_module(&self, x: i32, y: i32) -> bool {
		0 <= x && x < self.size && 0 <= y && y < self.size && self.module(x, y)
	}
	
	
	fn module(&self, x: i32, y: i32) -> bool {
		self.modules[(y * self.size + x) as usize]
	}
	
	
	fn module_mut(&mut self, x: i32, y: i32) -> &mut bool {
		&mut self.modules[(y * self.size + x) as usize]
	}
	
	
	pub fn to_svg_string(&self, border: i32) -> String {
		assert!(border >= 0, "Border must be non-negative");
		let mut result: String = String::new();
		result.push_str("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		result.push_str("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n");
		result.push_str(&format!("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 {0} {0}\" stroke=\"none\">\n", self.size + border * 2));
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
		self.draw_format_bits(0);  // Dummy mask value; overwritten later in the constructor
		self.draw_version();
	}
	
	
	// Draws two copies of the format bits (with its own error correction code)
	// based on the given mask and this object's error correction level field.
	fn draw_format_bits(&mut self, mask: u8) {
		// Calculate error correction code and pack bits
		let size: i32 = self.size;
		let mut data: u32 = (self.errorcorrectionlevel.formatbits << 3 | mask) as u32;  // errcorrlvl is uint2, mask is uint3
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
		if self.version < 7 {
			return;
		}
		
		// Calculate error correction code and pack bits
		let mut rem: u32 = self.version as u32;  // version is uint6, in the range [7, 40]
		for _ in 0 .. 12 {
			rem = (rem << 1) ^ ((rem >> 11) * 0x1F25);
		}
		let data: u32 = (self.version as u32) << 12 | rem;  // uint18
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
		let numblocks: usize = QrCode::table_get(&QrCode_NUM_ERROR_CORRECTION_BLOCKS, self.version, self.errorcorrectionlevel);
		let blockecclen: usize = QrCode::table_get(&QrCode_ECC_CODEWORDS_PER_BLOCK, self.version, self.errorcorrectionlevel);
		let rawcodewords: usize = QrCode::get_num_raw_data_modules(self.version) / 8;
		let numshortblocks: usize = numblocks - rawcodewords % numblocks;
		let shortblocklen: usize = rawcodewords / numblocks;
		
		// Split data into blocks and append ECC to each block
		let mut blocks: Vec<Vec<u8>> = Vec::with_capacity(numblocks);
		let rs = ReedSolomonGenerator::new(blockecclen);
		let mut k: usize = 0;
		for i in 0 .. numblocks {
			let mut dat: Vec<u8> = Vec::with_capacity(shortblocklen + 1);
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
		let mut result: Vec<u8> = Vec::with_capacity(rawcodewords);
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
	fn apply_mask(&mut self, mask: u8) {
		assert!(mask <= 7, "Mask value out of range");
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
					_ => panic!("Assertion error"),
				};
				*self.module_mut(x, y) ^= invert & !self.isfunction[(y * self.size + x) as usize];
			}
		}
	}
	
	
	// A messy helper function for the constructors. This QR Code must be in an unmasked state when this
	// method is called. The given argument is the requested mask, which is -1 for auto or 0 to 7 for fixed.
	// This method applies and returns the actual mask chosen, from 0 to 7.
	fn handle_constructor_masking(&mut self, mut mask: i8) {
		if mask == -1 {  // Automatically choose best mask
			let mut minpenalty: i32 = std::i32::MAX;
			for i in 0u8 .. 8 {
				self.draw_format_bits(i);
				self.apply_mask(i);
				let penalty: i32 = self.get_penalty_score();
				if penalty < minpenalty {
					mask = i as i8;
					minpenalty = penalty;
				}
				self.apply_mask(i);  // Undoes the mask due to XOR
			}
		}
		assert!(0 <= mask && mask <= 7, "Assertion error");
		self.draw_format_bits(mask as u8);  // Overwrite old format bits
		self.apply_mask(mask as u8);  // Apply the final choice of mask
		self.mask = mask as u8;
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
						result += QrCode_PENALTY_N1;
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
						result += QrCode_PENALTY_N1;
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
					result += QrCode_PENALTY_N2;
				}
			}
		}
		
		// Finder-like pattern in rows
		for y in 0 .. size {
			let mut bits: u32 = 0;
			for x in 0 .. size {
				bits = ((bits << 1) & 0x7FF) | (self.module(x, y) as u32);
				if x >= 10 && (bits == 0x05D || bits == 0x5D0) {  // Needs 11 bits accumulated
					result += QrCode_PENALTY_N3;
				}
			}
		}
		// Finder-like pattern in columns
		for x in 0 .. size {
			let mut bits: u32 = 0;
			for y in 0 .. size {
				bits = ((bits << 1) & 0x7FF) | (self.module(x, y) as u32);
				if y >= 10 && (bits == 0x05D || bits == 0x5D0) {  // Needs 11 bits accumulated
					result += QrCode_PENALTY_N3;
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
			result += QrCode_PENALTY_N4;
			k += 1;
		}
		result
	}
	
	
	/*---- Private static helper functions ----*/
	
	// Returns a set of positions of the alignment patterns in ascending order. These positions are
	// used on both the x and y axes. Each value in the resulting array is in the range [0, 177).
	// This stateless pure function could be implemented as table of 40 variable-length lists of unsigned bytes.
	fn get_alignment_pattern_positions(ver: u8) -> Vec<i32> {
		assert!(1 <= ver && ver <= 40, "Version number out of range");
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
			let mut result: Vec<i32> = vec![6];
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
	fn get_num_raw_data_modules(ver: u8) -> usize {
		assert!(1 <= ver && ver <= 40, "Version number out of range");
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
	fn get_num_data_codewords(ver: u8, ecl: &QrCodeEcc) -> usize {
		assert!(1 <= ver && ver <= 40, "Version number out of range");
		QrCode::get_num_raw_data_modules(ver) / 8
			- QrCode::table_get(&QrCode_ECC_CODEWORDS_PER_BLOCK, ver, ecl)
			* QrCode::table_get(&QrCode_NUM_ERROR_CORRECTION_BLOCKS, ver, ecl)
	}
	
	
	fn table_get(table: &'static [[i8; 41]; 4], ver: u8, ecl: &QrCodeEcc) -> usize {
		table[ecl.ordinal as usize][ver as usize] as usize
	}
	
}


/*---- Private tables of constants ----*/

// For use in get_penalty_score(), when evaluating which mask is best.
const QrCode_PENALTY_N1: i32 = 3;
const QrCode_PENALTY_N2: i32 = 3;
const QrCode_PENALTY_N3: i32 = 40;
const QrCode_PENALTY_N4: i32 = 10;


static QrCode_ECC_CODEWORDS_PER_BLOCK: [[i8; 41]; 4] = [
	// Version: (note that index 0 is for padding, and is set to an illegal value)
	//0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
	[-1,  7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],  // Low
	[-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28],  // Medium
	[-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],  // Quartile
	[-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],  // High
];

static QrCode_NUM_ERROR_CORRECTION_BLOCKS: [[i8; 41]; 4] = [
	// Version: (note that index 0 is for padding, and is set to an illegal value)
	//0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
	[-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4,  4,  4,  4,  4,  6,  6,  6,  6,  7,  8,  8,  9,  9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25],  // Low
	[-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5,  5,  8,  9,  9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49],  // Medium
	[-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8,  8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68],  // Quartile
	[-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81],  // High
];



/*---- QrCodeEcc functionality ----*/

pub struct QrCodeEcc {
	
	// In the range 0 to 3 (unsigned 2-bit integer).
	pub ordinal: u8,
	
	// In the range 0 to 3 (unsigned 2-bit integer).
	formatbits: u8,
	
}


pub static QrCodeEcc_LOW     : QrCodeEcc = QrCodeEcc { ordinal: 0, formatbits: 1 };
pub static QrCodeEcc_MEDIUM  : QrCodeEcc = QrCodeEcc { ordinal: 1, formatbits: 0 };
pub static QrCodeEcc_QUARTILE: QrCodeEcc = QrCodeEcc { ordinal: 2, formatbits: 3 };
pub static QrCodeEcc_HIGH    : QrCodeEcc = QrCodeEcc { ordinal: 3, formatbits: 2 };



/*---- ReedSolomonGenerator functionality ----*/

struct ReedSolomonGenerator {
	
	// Coefficients of the divisor polynomial, stored from highest to lowest power, excluding the leading term which
	// is always 1. For example the polynomial x^3 + 255x^2 + 8x + 93 is stored as the uint8 array {255, 8, 93}.
	coefficients: Vec<u8>,
	
}


impl ReedSolomonGenerator {
	
	fn new(degree: usize) -> ReedSolomonGenerator {
		assert!(1 <= degree && degree <= 255, "Degree out of range");
		// Start with the monomial x^0
		let mut coefs = vec![0; degree - 1];
		coefs.push(1);
		
		// Compute the product polynomial (x - r^0) * (x - r^1) * (x - r^2) * ... * (x - r^{degree-1}),
		// drop the highest term, and store the rest of the coefficients in order of descending powers.
		// Note that r = 0x02, which is a generator element of this field GF(2^8/0x11D).
		let mut root: u8 = 1;
		for _ in 0 .. degree {
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
	
	
	fn get_remainder(&self, data: &[u8]) -> Vec<u8> {
		// Compute the remainder by performing polynomial division
		let mut result: Vec<u8> = vec![0; self.coefficients.len()];
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

pub struct QrSegment {
	
	// The mode indicator for this segment.
	mode: &'static QrSegmentMode,
	
	// The length of this segment's unencoded data, measured in characters.
	numchars: usize,
	
	// The bits of this segment.
	data: Vec<bool>,
	
}


impl QrSegment {
	
	/*---- Static factory functions ----*/
	
	pub fn make_bytes(data: &[u8]) -> QrSegment {
		let mut bb: Vec<bool> = Vec::with_capacity(data.len() * 8);
		for b in data {
			for i in (0 .. 8).rev() {
				bb.push((b >> i) & 1u8 != 0u8);
			}
		}
		QrSegment::new(&QrSegmentMode_BYTE, data.len(), bb)
	}
	
	
	pub fn make_numeric(text: &[char]) -> QrSegment {
		let mut bb: Vec<bool> = Vec::with_capacity(text.len() * 3 + (text.len() + 2) / 3);
		let mut accumdata: u32 = 0;
		let mut accumcount: u32 = 0;
		for c in text {
			assert!('0' <= *c && *c <= '9', "String contains non-numeric characters");
			accumdata = accumdata * 10 + ((*c as u32) - ('0' as u32));
			accumcount += 1;
			if accumcount == 3 {
				append_bits(&mut bb, accumdata, 10);
				accumdata = 0;
				accumcount = 0;
			}
		}
		if accumcount > 0 {  // 1 or 2 digits remaining
			append_bits(&mut bb, accumdata, (accumcount as u8) * 3 + 1);
		}
		QrSegment::new(&QrSegmentMode_NUMERIC, text.len(), bb)
	}
	
	
	pub fn make_alphanumeric(text: &[char]) -> QrSegment {
		let mut bb: Vec<bool> = Vec::with_capacity(text.len() * 5 + (text.len() + 1) / 2);
		let mut accumdata: u32 = 0;
		let mut accumcount: u32 = 0;
		for c in text {
			let i = match QrSegment_ALPHANUMERIC_CHARSET.iter().position(|x| *x == *c) {
				None => panic!("String contains unencodable characters in alphanumeric mode"),
				Some(j) => j,
			};
			accumdata = accumdata * 45 + (i as u32);
			accumcount += 1;
			if accumcount == 2 {
				append_bits(&mut bb, accumdata, 11);
				accumdata = 0;
				accumcount = 0;
			}
		}
		if accumcount > 0 {  // 1 character remaining
			append_bits(&mut bb, accumdata, 6);
		}
		QrSegment::new(&QrSegmentMode_ALPHANUMERIC, text.len(), bb)
	}
	
	
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
	
	
	pub fn make_eci(assignval: u32) -> QrSegment {
		let mut bb: Vec<bool> = Vec::with_capacity(24);
		if assignval < (1 << 7) {
			append_bits(&mut bb, assignval, 8);
		} else if assignval < (1 << 14) {
			append_bits(&mut bb, 2, 2);
			append_bits(&mut bb, assignval, 14);
		} else if assignval < 1_000_000 {
			append_bits(&mut bb, 6, 3);
			append_bits(&mut bb, assignval, 21);
		} else {
			panic!("ECI assignment value out of range");
		}
		QrSegment::new(&QrSegmentMode_ECI, 0, bb)
	}
	
	
	pub fn new(mode: &'static QrSegmentMode, numchars: usize, data: Vec<bool>) -> QrSegment {
		QrSegment {
			mode: mode,
			numchars: numchars,
			data: data,
		}
	}
	
	
	fn get_total_bits(segs: &[QrSegment], version: u8) -> Option<usize> {
		assert!(1 <= version && version <= 40, "Version number out of range");
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
	
	
	fn is_alphanumeric(text: &[char]) -> bool {
		text.iter().all(|c| QrSegment_ALPHANUMERIC_CHARSET.contains(c))
	}
	
	
	fn is_numeric(text: &[char]) -> bool {
		text.iter().all(|c| '0' <= *c && *c <= '9')
	}
	
}


static QrSegment_ALPHANUMERIC_CHARSET: [char; 45] = ['0','1','2','3','4','5','6','7','8','9',
	'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z',
	' ','$','%','*','+','-','.','/',':'];



/*---- QrSegmentMode functionality ----*/

pub struct QrSegmentMode {
	
	// An unsigned 4-bit integer value (range 0 to 15)
	// representing the mode indicator bits for this mode object.
	modebits: u8,
	
	numbitscharcount: [u8; 3],
	
}


impl QrSegmentMode {
	
	pub fn num_char_count_bits(&self, ver: u8) -> u8 {
		if 1 <= ver && ver <= 9 {
			self.numbitscharcount[0]
		} else if 10 <= ver && ver <= 26 {
			self.numbitscharcount[1]
		} else if 27 <= ver && ver <= 40 {
			self.numbitscharcount[2]
		} else {
			panic!("Version number out of range");
		}
	}
	
}


pub static QrSegmentMode_NUMERIC     : QrSegmentMode = QrSegmentMode { modebits: 0x1, numbitscharcount: [10, 12, 14] };
pub static QrSegmentMode_ALPHANUMERIC: QrSegmentMode = QrSegmentMode { modebits: 0x2, numbitscharcount: [ 9, 11, 13] };
pub static QrSegmentMode_BYTE        : QrSegmentMode = QrSegmentMode { modebits: 0x4, numbitscharcount: [ 8, 16, 16] };
pub static QrSegmentMode_KANJI       : QrSegmentMode = QrSegmentMode { modebits: 0x8, numbitscharcount: [ 8, 10, 12] };
pub static QrSegmentMode_ECI         : QrSegmentMode = QrSegmentMode { modebits: 0x7, numbitscharcount: [ 0,  0,  0] };



/*---- Bit buffer functionality ----*/

// Appends the given number of bits of the given value to this sequence.
fn append_bits(bb: &mut Vec<bool>, val: u32, len: u8) {
	assert!(len < 32 && (val >> len) == 0 || len == 32, "Value out of range");
	for i in (0 .. len).rev() {  // Append bit by bit
		bb.push((val >> i) & 1 != 0);
	}
}


// Appends the data of the given segment to this bit buffer.
fn append_data(bb: &mut Vec<bool>, seg: &QrSegment) {
	bb.extend_from_slice(&seg.data);
}
