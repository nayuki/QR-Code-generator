/* 
 * QR Code generator library (Rust, no heap)
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
//! - Significantly shorter code but more documentation comments compared to competing libraries
//! - Supports encoding all 40 versions (sizes) and all 4 error correction levels, as per the QR Code Model 2 standard
//! - Output format: Raw modules/pixels of the QR symbol
//! - Detects finder-like penalty patterns more accurately than other implementations
//! - Encodes numeric and special-alphanumeric text in less space than general text
//! - Open-source code under the permissive MIT License
//! 
//! Manual parameters:
//! 
//! - User can specify minimum and maximum version numbers allowed, then library will automatically choose smallest version in the range that fits the data
//! - User can specify mask pattern manually, otherwise library will automatically evaluate all 8 masks and select the optimal one
//! - User can specify absolute error correction level, or allow the library to boost it if it doesn't increase the version number
//! - User can create a list of data segments manually and add ECI segments
//! 
//! More information about QR Code technology and this library's design can be found on the project home page.
//! 
//! # Examples
//! 
//! ```
//! extern crate qrcodegen_no_heap;
//! use qrcodegen_no_heap::Mask;
//! use qrcodegen_no_heap::QrCode;
//! use qrcodegen_no_heap::QrCodeEcc;
//! use qrcodegen_no_heap::Version;
//! ```
//! 
//! Text data:
//! 
//! ```
//! let mut outbuffer  = vec![0u8; Version::MAX.buffer_len()];
//! let mut tempbuffer = vec![0u8; Version::MAX.buffer_len()];
//! let qr = QrCode::encode_text("Hello, world!", &mut tempbuffer, &mut outbuffer,
//!     QrCodeEcc::Medium, Version::MIN, Version:MAX, None, true).unwrap();
//! let svg = to_svg_string(&qr, 4);  // See qrcodegen-demo
//! ```
//! 
//! Binary data:
//! 
//! ```
//! let mut outbuffer   = vec![0u8; Version::MAX.buffer_len()];
//! let mut dataandtemp = vec![0u8; Version::MAX.buffer_len()];
//! dataandtemp[0] = 0xE3;
//! dataandtemp[1] = 0x81;
//! dataandtemp[2] = 0x82;
//! let qr = QrCode::encode_binary(&mut dataandtemp, 3, &mut outbuffer, QrCodeEcc::High,
//!     Version::new(2), Version::new(7), Some(Mask::new(4)), false).unwrap();
//! for y in 0 .. qr.size() {
//!     for x in 0 .. qr.size() {
//!         (... paint qr.get_module(x, y) ...)
//!     }
//! }
//! ```


#![no_std]
#![forbid(unsafe_code)]
use core::convert::TryFrom;


/*---- QrCode functionality ----*/

/// A QR Code symbol, which is a type of two-dimension barcode.
/// 
/// Invented by Denso Wave and described in the ISO/IEC 18004 standard.
/// 
/// Instances of this struct represent an immutable square grid of dark and light cells.
/// The impl provides static factory functions to create a QR Code from text or binary data.
/// The struct and impl cover the QR Code Model 2 specification, supporting all versions
/// (sizes) from 1 to 40, all 4 error correction levels, and 4 character encoding modes.
/// 
/// Ways to create a QR Code object:
/// 
/// - High level: Take the payload data and call `QrCode::encode_text()` or `QrCode::encode_binary()`.
/// - Mid level: Custom-make the list of segments and call
///   `QrCode::encode_segments_to_codewords()` and then `QrCode::encode_codewords()`.
/// - Low level: Custom-make the array of data codeword bytes (including segment
///   headers and final padding, excluding error correction codewords), supply the
///   appropriate version number, and call the `QrCode::encode_codewords()` constructor.
/// 
/// (Note that all ways require supplying the desired error correction level and various byte buffers.)
pub struct QrCode<'a> {
	
	// The width and height of this QR Code, measured in modules, between
	// 21 and 177 (inclusive). This is equal to version * 4 + 17.
	size: &'a mut u8,
	
	// The modules of this QR Code (0 = light, 1 = dark), packed bitwise into bytes.
	// Immutable after constructor finishes. Accessed through get_module().
	modules: &'a mut [u8],
	
}


impl<'a> QrCode<'a> {
	
	/*---- Static factory functions (high level) ----*/
	
	/// Encodes the given text string to a QR Code, returning a wrapped `QrCode` if successful.
	/// If the data is too long to fit in any version in the given range
	/// at the given ECC level, then `Err` is returned.
	/// 
	/// The smallest possible QR Code version within the given range is automatically
	/// chosen for the output. Iff boostecl is `true`, then the ECC level of the result
	/// may be higher than the ecl argument if it can be done without increasing the
	/// version. The mask number is either between 0 to 7 (inclusive) to force that
	/// mask, or `None` to automatically choose an appropriate mask (which may be slow).
	/// 
	/// About the slices, letting len = maxversion.buffer_len():
	/// - Before calling the function:
	///   - The slices tempbuffer and outbuffer each must have a length of at least len.
	///   - If a slice is longer than len, then the function will not
	///     read from or write to the suffix array[len .. array.len()].
	///   - The initial values of both slices can be arbitrary
	///     because the function always writes before reading.
	/// - After the function returns, both slices have no guarantee on what values are stored.
	/// 
	/// If successful, the resulting QR Code may use numeric,
	/// alphanumeric, or byte mode to encode the text.
	/// 
	/// In the most optimistic case, a QR Code at version 40 with low ECC
	/// can hold any UTF-8 string up to 2953 bytes, or any alphanumeric string
	/// up to 4296 characters, or any digit string up to 7089 characters.
	/// These numbers represent the hard upper limit of the QR Code standard.
	/// 
	/// Please consult the QR Code specification for information on
	/// data capacities per version, ECC level, and text encoding mode.
	pub fn encode_text<'b>(text: &str, tempbuffer: &'b mut [u8], mut outbuffer: &'a mut [u8], ecl: QrCodeEcc,
			minversion: Version, maxversion: Version, mask: Option<Mask>, boostecl: bool) -> Result<QrCode<'a>,DataTooLong> {
		
		let minlen: usize = outbuffer.len().min(tempbuffer.len());
		outbuffer = &mut outbuffer[ .. minlen];
		
		let textlen: usize = text.len();  // In bytes
		if textlen == 0 {
			let (datacodewordslen, ecl, version) = QrCode::encode_segments_to_codewords(&[], outbuffer, ecl, minversion, maxversion, boostecl)?;
			return Ok(Self::encode_codewords(outbuffer, datacodewordslen, tempbuffer, ecl, version, mask));
		}
		
		use QrSegmentMode::*;
		let buflen: usize = outbuffer.len();
		let seg: QrSegment = if QrSegment::is_numeric(text) && QrSegment::calc_buffer_size(Numeric, textlen).map_or(false, |x| x <= buflen) {
			QrSegment::make_numeric(text, tempbuffer)
		} else if QrSegment::is_alphanumeric(text) && QrSegment::calc_buffer_size(Alphanumeric, textlen).map_or(false, |x| x <= buflen) {
			QrSegment::make_alphanumeric(text, tempbuffer)
		} else if QrSegment::calc_buffer_size(Byte, textlen).map_or(false, |x| x <= buflen) {
			QrSegment::make_bytes(text.as_bytes())
		} else {
			return Err(DataTooLong::SegmentTooLong);
		};
		let (datacodewordslen, ecl, version) = QrCode::encode_segments_to_codewords(&[seg], outbuffer, ecl, minversion, maxversion, boostecl)?;
		Ok(Self::encode_codewords(outbuffer, datacodewordslen, tempbuffer, ecl, version, mask))
	}
	
	
	/// Encodes the given binary data to a QR Code, returning a wrapped `QrCode` if successful.
	/// If the data is too long to fit in any version in the given range
	/// at the given ECC level, then `Err` is returned.
	/// 
	/// The smallest possible QR Code version within the given range is automatically
	/// chosen for the output. Iff boostecl is `true`, then the ECC level of the result
	/// may be higher than the ecl argument if it can be done without increasing the
	/// version. The mask number is either between 0 to 7 (inclusive) to force that
	/// mask, or `None` to automatically choose an appropriate mask (which may be slow).
	/// 
	/// About the slices, letting len = maxversion.buffer_len():
	/// - Before calling the function:
	///   - The slices dataandtempbuffer and outbuffer each must have a length of at least len.
	///   - If a slice is longer than len, then the function will not
	///     read from or write to the suffix array[len .. array.len()].
	///   - The input slice range dataandtempbuffer[0 .. datalen] should normally be
	///     valid UTF-8 text, but is not required by the QR Code standard.
	///   - The initial values of dataandtempbuffer[datalen .. len] and outbuffer[0 .. len]
	///     can be arbitrary because the function always writes before reading.
	/// - After the function returns, both slices have no guarantee on what values are stored.
	/// 
	/// If successful, the resulting QR Code will use byte mode to encode the data.
	/// 
	/// In the most optimistic case, a QR Code at version 40 with low ECC can hold any byte
	/// sequence up to length 2953. This is the hard upper limit of the QR Code standard.
	/// 
	/// Please consult the QR Code specification for information on
	/// data capacities per version, ECC level, and text encoding mode.
	pub fn encode_binary<'b>(dataandtempbuffer: &'b mut [u8], datalen: usize, mut outbuffer: &'a mut [u8], ecl: QrCodeEcc,
			minversion: Version, maxversion: Version, mask: Option<Mask>, boostecl: bool) -> Result<QrCode<'a>,DataTooLong> {
		
		assert!(datalen <= dataandtempbuffer.len(), "Invalid data length");
		let minlen: usize = outbuffer.len().min(dataandtempbuffer.len());
		outbuffer = &mut outbuffer[ .. minlen];
		
		if QrSegment::calc_buffer_size(QrSegmentMode::Byte, datalen).map_or(true, |x| x > outbuffer.len()) {
			return Err(DataTooLong::SegmentTooLong);
		}
		let seg: QrSegment = QrSegment::make_bytes(&dataandtempbuffer[ .. datalen]);
		let (datacodewordslen, ecl, version) = QrCode::encode_segments_to_codewords(&[seg], outbuffer, ecl, minversion, maxversion, boostecl)?;
		Ok(Self::encode_codewords(outbuffer, datacodewordslen, dataandtempbuffer, ecl, version, mask))
	}
	
	
	/*---- Static factory functions (mid level) ----*/
	
	/// Returns an intermediate state representing the given segments
	/// with the given encoding parameters being encoded into codewords.
	/// 
	/// The smallest possible QR Code version within the given range is automatically
	/// chosen for the output. Iff boostecl is `true`, then the ECC level of the result
	/// may be higher than the ecl argument if it can be done without increasing the
	/// version. The mask number is either between 0 to 7 (inclusive) to force that
	/// mask, or `None` to automatically choose an appropriate mask (which may be slow).
	/// 
	/// This function exists to allow segments to use parts of a temporary buffer,
	/// then have the segments be encoded to an output buffer, then invalidate all the segments,
	/// and finally have the output buffer and temporary buffer be encoded to a QR Code.
	pub fn encode_segments_to_codewords(segs: &[QrSegment], outbuffer: &'a mut [u8],
			mut ecl: QrCodeEcc, minversion: Version, maxversion: Version, boostecl: bool)
			-> Result<(usize,QrCodeEcc,Version),DataTooLong> {
		
		assert!(minversion <= maxversion, "Invalid value");
		assert!(outbuffer.len() >= QrCode::get_num_data_codewords(maxversion, ecl), "Invalid buffer length");
		
		// Find the minimal version number to use
		let mut version: Version = minversion;
		let datausedbits: usize = loop {
			let datacapacitybits: usize = QrCode::get_num_data_codewords(version, ecl) * 8;  // Number of data bits available
			let dataused: Option<usize> = QrSegment::get_total_bits(segs, version);
			if dataused.map_or(false, |n| n <= datacapacitybits) {
				break dataused.unwrap();  // This version number is found to be suitable
			} else if version >= maxversion {  // All versions in the range could not fit the given data
				return Err(match dataused {
					None => DataTooLong::SegmentTooLong,
					Some(n) => DataTooLong::DataOverCapacity(n, datacapacitybits),
				});
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
		let datacapacitybits: usize = QrCode::get_num_data_codewords(version, ecl) * 8;
		let mut bb = BitBuffer::new(&mut outbuffer[ .. datacapacitybits/8]);
		for seg in segs {
			bb.append_bits(seg.mode.mode_bits(), 4);
			bb.append_bits(u32::try_from(seg.numchars).unwrap(), seg.mode.num_char_count_bits(version));
			for i in 0 .. seg.bitlength {
				let bit: u8 = (seg.data[i >> 3] >> (7 - (i & 7))) & 1;
				bb.append_bits(bit.into(), 1);
			}
		}
		debug_assert_eq!(bb.length, datausedbits);
		
		// Add terminator and pad up to a byte if applicable
		let numzerobits: usize = core::cmp::min(4, datacapacitybits - bb.length);
		bb.append_bits(0, u8::try_from(numzerobits).unwrap());
		let numzerobits: usize = bb.length.wrapping_neg() & 7;
		bb.append_bits(0, u8::try_from(numzerobits).unwrap());
		debug_assert_eq!(bb.length % 8, 0);
		
		// Pad with alternating bytes until data capacity is reached
		for &padbyte in [0xEC, 0x11].iter().cycle() {
			if bb.length >= datacapacitybits {
				break;
			}
			bb.append_bits(padbyte, 8);
		}
		Ok((bb.length / 8, ecl, version))
	}
	
	
	/*---- Constructor (low level) ----*/
	
	/// Creates a new QR Code with the given version number,
	/// error correction level, data codeword bytes, and mask number.
	/// 
	/// This is a low-level API that most users should not use directly.
	/// A mid-level API is the `encode_segments_to_codewords()` function.
	pub fn encode_codewords<'b>(mut datacodewordsandoutbuffer: &'a mut [u8], datacodewordslen: usize, mut tempbuffer: &'b mut [u8],
			ecl: QrCodeEcc, version: Version, mut msk: Option<Mask>) -> QrCode<'a> {
		
		datacodewordsandoutbuffer = &mut datacodewordsandoutbuffer[ .. version.buffer_len()];
		tempbuffer                = &mut tempbuffer               [ .. version.buffer_len()];
		
		// Compute ECC
		let rawcodewords: usize = QrCode::get_num_raw_data_modules(version) / 8;
		assert!(datacodewordslen <= rawcodewords);
		let (data, temp) = datacodewordsandoutbuffer.split_at_mut(datacodewordslen);
		let allcodewords = Self::add_ecc_and_interleave(data, version, ecl, temp, tempbuffer);
		
		// Draw modules
		let mut result: QrCode = QrCode::<'a>::function_modules_marked(datacodewordsandoutbuffer, version);
		result.draw_codewords(allcodewords);
		result.draw_light_function_modules();
		let funcmods: QrCode = QrCode::<'b>::function_modules_marked(tempbuffer, version);  // Just a grid, not a real QR Code
		
		// Do masking
		if msk.is_none() {  // Automatically choose best mask
			let mut minpenalty = core::i32::MAX;
			for i in 0u8 .. 8 {
				let i = Mask::new(i);
				result.apply_mask(&funcmods, i);
				result.draw_format_bits(ecl, i);
				let penalty: i32 = result.get_penalty_score();
				if penalty < minpenalty {
					msk = Some(i);
					minpenalty = penalty;
				}
				result.apply_mask(&funcmods, i);  // Undoes the mask due to XOR
			}
		}
		let msk: Mask = msk.unwrap();
		result.apply_mask(&funcmods, msk);  // Apply the final choice of mask
		result.draw_format_bits(ecl, msk);  // Overwrite old format bits
		result
	}
	
	
	/*---- Public methods ----*/
	
	/// Returns this QR Code's version, in the range [1, 40].
	pub fn version(&self) -> Version {
		Version::new((*self.size - 17) / 4)
	}
	
	
	/// Returns this QR Code's size, in the range [21, 177].
	pub fn size(&self) -> i32 {
		i32::from(*self.size)
	}
	
	
	/// Returns this QR Code's error correction level.
	pub fn error_correction_level(&self) -> QrCodeEcc {
		let index =
			usize::from(self.get_module_bounded(0, 8)) << 1 |
			usize::from(self.get_module_bounded(1, 8)) << 0;
		use QrCodeEcc::*;
		[Medium, Low, High, Quartile][index]
	}
	
	
	/// Returns this QR Code's mask, in the range [0, 7].
	pub fn mask(&self) -> Mask {
		Mask::new(
			u8::from(self.get_module_bounded(2, 8)) << 2 |
			u8::from(self.get_module_bounded(3, 8)) << 1 |
			u8::from(self.get_module_bounded(4, 8)) << 0)
	}
	
	
	/// Returns the color of the module (pixel) at the given coordinates,
	/// which is `false` for light or `true` for dark.
	/// 
	/// The top left corner has the coordinates (x=0, y=0). If the given
	/// coordinates are out of bounds, then `false` (light) is returned.
	pub fn get_module(&self, x: i32, y: i32) -> bool {
		let range = 0 .. self.size();
		range.contains(&x) && range.contains(&y) && self.get_module_bounded(x as u8, y as u8)
	}
	
	
	// Returns the color of the module at the given coordinates, which must be in bounds.
	fn get_module_bounded(&self, x: u8, y: u8) -> bool {
		let range = 0 .. *self.size;
		assert!(range.contains(&x) && range.contains(&y));
		let index = usize::from(y) * usize::from(*self.size) + usize::from(x);
		let byteindex: usize = index >> 3;
		let bitindex: usize = index & 7;
		get_bit(self.modules[byteindex].into(), bitindex as u8)
	}
	
	
	// Sets the color of the module at the given coordinates, doing nothing if out of bounds.
	fn set_module_unbounded(&mut self, x: i32, y: i32, isdark: bool) {
		let range = 0 .. self.size();
		if range.contains(&x) && range.contains(&y) {
			self.set_module_bounded(x as u8, y as u8, isdark);
		}
	}
	
	
	// Sets the color of the module at the given coordinates, which must be in bounds.
	fn set_module_bounded(&mut self, x: u8, y: u8, isdark: bool) {
		let range = 0 .. *self.size;
		assert!(range.contains(&x) && range.contains(&y));
		let index = usize::from(y) * usize::from(*self.size) + usize::from(x);
		let byteindex: usize = index >> 3;
		let bitindex: usize = index & 7;
		if isdark {
			self.modules[byteindex] |= 1u8 << bitindex;
		} else {
			self.modules[byteindex] &= !(1u8 << bitindex);
		}
	}
	
	
	/*---- Error correction code generation ----*/
	
	// Appends error correction bytes to each block of the given data array, then interleaves
	// bytes from the blocks, stores them in the output array, and returns a slice of resultbuf.
	// temp is used as a temporary work area and will be clobbered by this function.
	fn add_ecc_and_interleave<'b>(data: &[u8], ver: Version, ecl: QrCodeEcc, temp: &mut [u8], resultbuf: &'b mut [u8]) -> &'b [u8] {
		assert_eq!(data.len(), QrCode::get_num_data_codewords(ver, ecl));
		
		// Calculate parameter numbers
		let numblocks: usize = QrCode::table_get(&NUM_ERROR_CORRECTION_BLOCKS, ver, ecl);
		let blockecclen: usize = QrCode::table_get(&ECC_CODEWORDS_PER_BLOCK  , ver, ecl);
		let rawcodewords: usize = QrCode::get_num_raw_data_modules(ver) / 8;
		let numshortblocks: usize = numblocks - rawcodewords % numblocks;
		let shortblockdatalen: usize = rawcodewords / numblocks - blockecclen;
		let result = &mut resultbuf[ .. rawcodewords];
		
		// Split data into blocks, calculate ECC, and interleave
		// (not concatenate) the bytes into a single sequence
		let rs = ReedSolomonGenerator::new(blockecclen);
		let mut dat: &[u8] = data;
		let ecc: &mut [u8] = &mut temp[ .. blockecclen];  // Temporary storage
		for i in 0 .. numblocks {
			let datlen: usize = shortblockdatalen + usize::from(i >= numshortblocks);
			rs.compute_remainder(&dat[ .. datlen], ecc);
			let mut k: usize = i;
			for j in 0 .. datlen {  // Copy data
				if j == shortblockdatalen {
					k -= numshortblocks;
				}
				result[k] = dat[j];
				k += numblocks;
			}
			let mut k: usize = data.len() + i;
			for j in 0 .. blockecclen {  // Copy ECC
				result[k] = ecc[j];
				k += numblocks;
			}
			dat = &dat[datlen .. ];
		}
		debug_assert_eq!(dat.len(), 0);
		result
	}
	
	
	/*---- Drawing function modules ----*/
	
	// Creates a QR Code grid with light modules for the given
	// version's size, then marks every function module as dark.
	fn function_modules_marked(outbuffer: &'a mut [u8], ver: Version) -> Self {
		assert_eq!(outbuffer.len(), ver.buffer_len());
		let parts: (&mut u8, &mut [u8]) = outbuffer.split_first_mut().unwrap();
		let mut result = Self {
			size: parts.0,
			modules: parts.1,
		};
		let size: u8 = ver.value() * 4 + 17;
		*result.size = size;
		result.modules.fill(0);
		
		// Fill horizontal and vertical timing patterns
		result.fill_rectangle(6, 0, 1, size);
		result.fill_rectangle(0, 6, size, 1);
		
		// Fill 3 finder patterns (all corners except bottom right) and format bits
		result.fill_rectangle(0, 0, 9, 9);
		result.fill_rectangle(size - 8, 0, 8, 9);
		result.fill_rectangle(0, size - 8, 9, 8);
		
		// Fill numerous alignment patterns
		let mut alignpatposbuf = [0u8; 7];
		let alignpatpos: &[u8] = result.get_alignment_pattern_positions(&mut alignpatposbuf);
		for (i, pos0) in alignpatpos.iter().enumerate() {
			for (j, pos1) in alignpatpos.iter().enumerate() {
				// Don't draw on the three finder corners
				if !((i == 0 && j == 0) || (i == 0 && j == alignpatpos.len() - 1) || (i == alignpatpos.len() - 1 && j == 0)) {
					result.fill_rectangle(pos0 - 2, pos1 - 2, 5, 5);
				}
			}
		}
		
		// Fill version blocks
		if ver.value() >= 7 {
			result.fill_rectangle(size - 11, 0, 3, 6);
			result.fill_rectangle(0, size - 11, 6, 3);
		}
		
		result
	}
	
	
	// Draws light function modules and possibly some dark modules onto this QR Code, without changing
	// non-function modules. This does not draw the format bits. This requires all function modules to be previously
	// marked dark (namely by function_modules_marked()), because this may skip redrawing dark function modules.
	fn draw_light_function_modules(&mut self) {
		// Draw horizontal and vertical timing patterns
		let size: u8 = *self.size;
		for i in (7 .. size-7).step_by(2) {
			self.set_module_bounded(6, i, false);
			self.set_module_bounded(i, 6, false);
		}
		
		// Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
		for dy in -4i32 ..= 4 {
			for dx in -4i32 ..= 4 {
				let dist: i32 = dx.abs().max(dy.abs());
				if dist == 2 || dist == 4 {
					self.set_module_unbounded(3 + dx, 3 + dy, false);
					self.set_module_unbounded(i32::from(size) - 4 + dx, 3 + dy, false);
					self.set_module_unbounded(3 + dx, i32::from(size) - 4 + dy, false);
				}
			}
		}
		
		// Draw numerous alignment patterns
		let mut alignpatposbuf = [0u8; 7];
		let alignpatpos: &[u8] = self.get_alignment_pattern_positions(&mut alignpatposbuf);
		for (i, &pos0) in alignpatpos.iter().enumerate() {
			for (j, &pos1) in alignpatpos.iter().enumerate() {
				if (i == 0 && j == 0) || (i == 0 && j == alignpatpos.len() - 1) || (i == alignpatpos.len() - 1 && j == 0) {
					continue;  // Don't draw on the three finder corners
				}
				for dy in -1 ..= 1 {
					for dx in -1 ..= 1 {
						self.set_module_bounded((i32::from(pos0) + dx) as u8, (i32::from(pos1) + dy) as u8, dx == 0 && dy == 0);
					}
				}
			}
		}
		
		// Draw version blocks
		let ver = u32::from(self.version().value());  // uint6, in the range [7, 40]
		if ver >= 7 {
			// Calculate error correction code and pack bits
			let bits: u32 = {
				let mut rem: u32 = ver;
				for _ in 0 .. 12 {
					rem = (rem << 1) ^ ((rem >> 11) * 0x1F25);
				}
				ver << 12 | rem  // uint18
			};
			debug_assert_eq!(bits >> 18, 0);
			
			// Draw two copies
			for i in 0u8 .. 18 {
				let bit: bool = get_bit(bits, i);
				let a: u8 = size - 11 + i % 3;
				let b: u8 = i / 3;
				self.set_module_bounded(a, b, bit);
				self.set_module_bounded(b, a, bit);
			}
		}
	}
	
	
	// Draws two copies of the format bits (with its own error correction code) based
	// on the given mask and error correction level. This always draws all modules of
	// the format bits, unlike draw_light_function_modules() which might skip dark modules.
	fn draw_format_bits(&mut self, ecl: QrCodeEcc, mask: Mask) {
		// Calculate error correction code and pack bits
		let bits: u32 = {
			// errcorrlvl is uint2, mask is uint3
			let data = u32::from(ecl.format_bits() << 3 | mask.value());
			let mut rem: u32 = data;
			for _ in 0 .. 10 {
				rem = (rem << 1) ^ ((rem >> 9) * 0x537);
			}
			(data << 10 | rem) ^ 0x5412  // uint15
		};
		debug_assert_eq!(bits >> 15, 0);
		
		// Draw first copy
		for i in 0 .. 6 {
			self.set_module_bounded(8, i, get_bit(bits, i));
		}
		self.set_module_bounded(8, 7, get_bit(bits, 6));
		self.set_module_bounded(8, 8, get_bit(bits, 7));
		self.set_module_bounded(7, 8, get_bit(bits, 8));
		for i in 9 .. 15 {
			self.set_module_bounded(14 - i, 8, get_bit(bits, i));
		}
		
		// Draw second copy
		let size: u8 = *self.size;
		for i in 0 .. 8 {
			self.set_module_bounded(size - 1 - i, 8, get_bit(bits, i));
		}
		for i in 8 .. 15 {
			self.set_module_bounded(8, size - 15 + i, get_bit(bits, i));
		}
		self.set_module_bounded(8, size - 8, true);  // Always dark
	}
	
	
	// Sets every module in the range [left : left + width] * [top : top + height] to dark.
	fn fill_rectangle(&mut self, left: u8, top: u8, width: u8, height: u8) {
		for dy in 0 .. height {
			for dx in 0 .. width {
				self.set_module_bounded(left + dx, top + dy, true);
			}
		}
	}
	
	
	/*---- Drawing data modules and masking ----*/
	
	// Draws the raw codewords (including data and ECC) onto this QR Code. This requires the initial state of
	// the QR Code to be dark at function modules and light at codeword modules (including unused remainder bits).
	fn draw_codewords(&mut self, data: &[u8]) {
		assert_eq!(data.len(), QrCode::get_num_raw_data_modules(self.version()) / 8, "Illegal argument");
		
		let size: i32 = self.size();
		let mut i: usize = 0;  // Bit index into the data
		// Do the funny zigzag scan
		let mut right: i32 = size - 1;
		while right >= 1 {  // Index of right column in each column pair
			if right == 6 {
				right = 5;
			}
			for vert in 0 .. size {  // Vertical counter
				for j in 0 .. 2 {
					let x = (right - j) as u8;  // Actual x coordinate
					let upward: bool = (right + 1) & 2 == 0;
					let y = (if upward { size - 1 - vert } else { vert }) as u8;  // Actual y coordinate
					if !self.get_module_bounded(x, y) && i < data.len() * 8 {
						self.set_module_bounded(x, y, get_bit(data[i >> 3].into(), 7 - ((i as u8) & 7)));
						i += 1;
					}
					// If this QR Code has any remainder bits (0 to 7), they were assigned as
					// 0/false/light by the constructor and are left unchanged by this method
				}
			}
			right -= 2;
		}
		debug_assert_eq!(i, data.len() * 8);
	}
	
	
	// XORs the codeword modules in this QR Code with the given mask pattern
	// and given pattern of function modules. The codeword bits must be drawn
	// before masking. Due to the arithmetic of XOR, calling apply_mask() with
	// the same mask value a second time will undo the mask. A final well-formed
	// QR Code needs exactly one (not zero, two, etc.) mask applied.
	fn apply_mask(&mut self, functionmodules: &QrCode, mask: Mask) {
		for y in 0 .. *self.size {
			for x in 0 .. *self.size {
				if functionmodules.get_module_bounded(x, y) {
					continue;
				}
				let invert: bool = {
					let x = i32::from(x);
					let y = i32::from(y);
					match mask.value() {
						0 => (x + y) % 2 == 0,
						1 => y % 2 == 0,
						2 => x % 3 == 0,
						3 => (x + y) % 3 == 0,
						4 => (x / 3 + y / 2) % 2 == 0,
						5 => x * y % 2 + x * y % 3 == 0,
						6 => (x * y % 2 + x * y % 3) % 2 == 0,
						7 => ((x + y) % 2 + x * y % 3) % 2 == 0,
						_ => unreachable!(),
					}
				};
				self.set_module_bounded(x, y,
					self.get_module_bounded(x, y) ^ invert);
			}
		}
	}
	
	
	// Calculates and returns the penalty score based on state of this QR Code's current modules.
	// This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
	fn get_penalty_score(&self) -> i32 {
		let mut result: i32 = 0;
		let size: u8 = *self.size;
		
		// Adjacent modules in row having same color, and finder-like patterns
		for y in 0 .. size {
			let mut runcolor = false;
			let mut runx: i32 = 0;
			let mut runhistory = FinderPenalty::new(size);
			for x in 0 .. size {
				if self.get_module_bounded(x, y) == runcolor {
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
					runcolor = self.get_module_bounded(x, y);
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
				if self.get_module_bounded(x, y) == runcolor {
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
					runcolor = self.get_module_bounded(x, y);
					runy = 1;
				}
			}
			result += runhistory.terminate_and_count(runcolor, runy) * PENALTY_N3;
		}
		
		// 2*2 blocks of modules having same color
		for y in 0 .. size-1 {
			for x in 0 .. size-1 {
				let color: bool = self.get_module_bounded(x, y);
				if color == self.get_module_bounded(x + 1, y) &&
				   color == self.get_module_bounded(x, y + 1) &&
				   color == self.get_module_bounded(x + 1, y + 1) {
					result += PENALTY_N2;
				}
			}
		}
		
		// Balance of dark and light modules
		let dark = self.modules.iter().map(|x| x.count_ones()).sum::<u32>() as i32;
		let total = i32::from(size) * i32::from(size);  // Note that size is odd, so dark/total != 1/2
		// Compute the smallest integer k >= 0 such that (45-5k)% <= dark/total <= (55+5k)%
		let k: i32 = ((dark * 20 - total * 10).abs() + total - 1) / total - 1;
		debug_assert!(0 <= k && k <= 9);
		result += k * PENALTY_N4;
		debug_assert!(0 <= result && result <= 2568888);  // Non-tight upper bound based on default values of PENALTY_N1, ..., N4
		result
	}
	
	
	/*---- Private helper functions ----*/
	
	// Calculates and stores an ascending list of positions of alignment patterns
	// for this version number, returning a slice of resultbuf.
	// Each position is in the range [0,177), and are used on both the x and y axes.
	// This could be implemented as lookup table of 40 variable-length lists of unsigned bytes.
	fn get_alignment_pattern_positions<'b>(&self, resultbuf: &'b mut [u8; 7]) -> &'b [u8] {
		let ver: u8 = self.version().value();
		if ver == 1 {
			&resultbuf[ .. 0]
		} else {
			let numalign: u8 = ver / 7 + 2;
			let step = u8::try_from((i32::from(ver) * 8 + i32::from(numalign) * 3 + 5)
				/ (i32::from(numalign) * 4 - 4) * 2).unwrap();
			let result = &mut resultbuf[ .. usize::from(numalign)];
			for i in 0 .. numalign-1 {
				result[usize::from(i)] = *self.size - 7 - i * step;
			}
			*result.last_mut().unwrap() = 6;
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
		debug_assert!((208 ..= 29648).contains(&result));
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
	
}


impl PartialEq for QrCode<'_> {
	fn eq(&self, other: &QrCode<'_>) -> bool{
		*self.size    == *other.size    &&
		*self.modules == *other.modules
	}
}

impl Eq for QrCode<'_> {}


/*---- Helper struct for add_ecc_and_interleave() ----*/

struct ReedSolomonGenerator {
	
	// Polynomial coefficients are stored from highest to lowest power, excluding the leading term which is always 1.
	// For example the polynomial x^3 + 255x^2 + 8x + 93 is stored as the uint8 array [255, 8, 93].
	divisor: [u8; 30],
	
	// The degree of the divisor polynomial, in the range [1, 30].
	degree: usize,
	
}


impl ReedSolomonGenerator {
	
	// Creates a Reed-Solomon ECC generator polynomial for the given degree. This could be
	// implemented as a lookup table over all possible parameter values, instead of as an algorithm.
	fn new(degree: usize) -> Self {
		let mut result = Self {
			divisor: [0u8; 30],
			degree: degree,
		};
		assert!((1 ..= result.divisor.len()).contains(&degree), "Degree out of range");
		let divisor: &mut [u8] = &mut result.divisor[ .. degree];
		divisor[degree - 1] = 1;  // Start off with the monomial x^0
		
		// Compute the product polynomial (x - r^0) * (x - r^1) * (x - r^2) * ... * (x - r^{degree-1}),
		// and drop the highest monomial term which is always 1x^degree.
		// Note that r = 0x02, which is a generator element of this field GF(2^8/0x11D).
		let mut root: u8 = 1;
		for _ in 0 .. degree {  // Unused variable i
			// Multiply the current product by (x - r^i)
			for j in 0 .. degree {
				divisor[j] = Self::multiply(divisor[j], root);
				if j + 1 < divisor.len() {
					divisor[j] ^= divisor[j + 1];
				}
			}
			root = Self::multiply(root, 0x02);
		}
		result
	}
	
	
	// Returns the Reed-Solomon error correction codeword for the given data polynomial and this divisor polynomial.
	fn compute_remainder(&self, data: &[u8], result: &mut [u8]) {
		assert_eq!(result.len(), self.degree);
		result.fill(0);
		for b in data {  // Polynomial division
			let factor: u8 = b ^ result[0];
			result.copy_within(1 .. , 0);
			result[result.len() - 1] = 0;
			for (x, &y) in result.iter_mut().zip(self.divisor.iter()) {
				*x ^= Self::multiply(y, factor);
			}
		}
	}
	
	
	// Returns the product of the two given field elements modulo GF(2^8/0x11D).
	// All inputs are valid. This could be implemented as a 256*256 lookup table.
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


/*---- Helper struct for get_penalty_score() ----*/

struct FinderPenalty {
	qr_size: i32,
	run_history: [i32; 7],
}


impl FinderPenalty {
	
	pub fn new(size: u8) -> Self {
		Self {
			qr_size: i32::from(size),
			run_history: [0; 7],
		}
	}
	
	
	// Pushes the given value to the front and drops the last value.
	pub fn add_history(&mut self, mut currentrunlength: i32) {
		if self.run_history[0] == 0 {
			currentrunlength += self.qr_size;  // Add light border to initial run
		}
		let len: usize = self.run_history.len();
		self.run_history.copy_within(0 .. len-1, 1);
		self.run_history[0] = currentrunlength;
	}
	
	
	// Can only be called immediately after a light run is added, and returns either 0, 1, or 2.
	pub fn count_patterns(&self) -> i32 {
		let rh = &self.run_history;
		let n = rh[1];
		debug_assert!(n <= self.qr_size * 3);
		let core = n > 0 && rh[2] == n && rh[3] == n * 3 && rh[4] == n && rh[5] == n;
		#[allow(unused_parens)]
		( i32::from(core && rh[0] >= n * 4 && rh[6] >= n)
		+ i32::from(core && rh[6] >= n * 4 && rh[0] >= n))
	}
	
	
	// Must be called at the end of a line (row or column) of modules.
	pub fn terminate_and_count(mut self, currentruncolor: bool, mut currentrunlength: i32) -> i32 {
		if currentruncolor {  // Terminate dark run
			self.add_history(currentrunlength);
			currentrunlength = 0;
		}
		currentrunlength += self.qr_size;  // Add light border to final run
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
pub struct QrSegment<'a> {
	
	// The mode indicator of this segment. Accessed through mode().
	mode: QrSegmentMode,
	
	// The length of this segment's unencoded data. Measured in characters for
	// numeric/alphanumeric/kanji mode, bytes for byte mode, and 0 for ECI mode.
	// Not the same as the data's bit length. Accessed through num_chars().
	numchars: usize,
	
	// The data bits of this segment, packed in bitwise big endian.
	data: &'a [u8],
	
	// The number of valid data bits used in the buffer. Requires bitlength <= data.len() * 8.
	// The character count (numchars) must agree with the mode and the bit buffer length.
	bitlength: usize,
	
}


impl<'a> QrSegment<'a> {
	
	/*---- Static factory functions (mid level) ----*/
	
	/// Returns a segment representing the given binary data encoded in byte mode.
	/// 
	/// All input byte slices are acceptable.
	/// 
	/// Any text string can be converted to UTF-8 bytes and encoded as a byte mode segment.
	pub fn make_bytes(data: &'a [u8]) -> Self {
		QrSegment::new(QrSegmentMode::Byte, data.len(), data, data.len().checked_mul(8).unwrap())
	}
	
	
	/// Returns a segment representing the given string of decimal digits encoded in numeric mode.
	/// 
	/// Panics if the string contains non-digit characters.
	pub fn make_numeric(text: &str, buf: &'a mut [u8]) -> Self {
		assert!(text.bytes().all(|b| (b'0' ..= b'9').contains(&b)), "String contains non-numeric characters");
		let mut bb = BitBuffer::new(buf);
		for chunk in text.as_bytes().chunks(3) {
			let data: u32 = chunk.iter().fold(0u32,
				|acc, &b| acc * 10 + u32::from(b - b'0'));
			bb.append_bits(data, (chunk.len() as u8) * 3 + 1);
		}
		QrSegment::new(QrSegmentMode::Numeric, text.len(), bb.data, bb.length)
	}
	
	
	/// Returns a segment representing the given text string encoded in alphanumeric mode.
	/// 
	/// The characters allowed are: 0 to 9, A to Z (uppercase only), space,
	/// dollar, percent, asterisk, plus, hyphen, period, slash, colon.
	/// 
	/// Panics if the string contains non-encodable characters.
	pub fn make_alphanumeric(text: &str, buf: &'a mut [u8]) -> Self {
		let mut bb = BitBuffer::new(buf);
		for chunk in text.as_bytes().chunks(2) {
			let data: u32 = chunk.iter().fold(0u32, |acc, &b| acc * 45 + u32::try_from(
				ALPHANUMERIC_CHARSET.find(char::from(b)).expect("String contains unencodable characters in alphanumeric mode")).unwrap());
			bb.append_bits(data, (chunk.len() as u8) * 5 + 1);
		}
		QrSegment::new(QrSegmentMode::Alphanumeric, text.len(), bb.data, bb.length)
	}
	
	
	/// Returns a segment representing an Extended Channel Interpretation
	/// (ECI) designator with the given assignment value.
	pub fn make_eci(assignval: u32, buf: &'a mut [u8]) -> Self {
		let mut bb = BitBuffer::new(buf);
		if assignval < (1 << 7) {
			bb.append_bits(assignval, 8);
		} else if assignval < (1 << 14) {
			bb.append_bits(0b10, 2);
			bb.append_bits(assignval, 14);
		} else if assignval < 1_000_000 {
			bb.append_bits(0b110, 3);
			bb.append_bits(assignval, 21);
		} else {
			panic!("ECI assignment value out of range");
		}
		QrSegment::new(QrSegmentMode::Eci, 0, bb.data, bb.length)
	}
	
	
	/*---- Constructor (low level) ----*/
	
	/// Creates a new QR Code segment with the given attributes and data.
	/// 
	/// The character count (numchars) must agree with the mode and
	/// the bit buffer length, but the constraint isn't checked.
	pub fn new(mode: QrSegmentMode, numchars: usize, data: &'a [u8], bitlength: usize) -> Self {
		assert!(bitlength == 0 || (bitlength - 1) / 8 < data.len());
		Self { mode, numchars, data, bitlength }
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
	
	
	/*---- Other static functions ----*/
	
	/// Returns the number of bytes needed for the data buffer of a segment
	/// containing the given number of characters using the given mode, or None if the
	/// internal calculation of the number of needed bits exceeds usize::MAX. Notes:
	/// 
	/// - It is okay for the user to allocate more bytes for the buffer than needed.
	/// - For byte mode, numchars measures the number of bytes, not Unicode code points.
	/// - For ECI mode, numchars must be 0, and the worst-case number of bytes is returned.
	///   An actual ECI segment can have shorter data. For non-ECI modes, the result is exact.
	pub fn calc_buffer_size(mode: QrSegmentMode, numchars: usize) -> Option<usize> {
		let temp = Self::calc_bit_length(mode, numchars)?;
		Some(temp / 8 + usize::from(temp % 8 != 0))  // ceil(temp / 8)
	}
	
	
	// Returns the number of data bits needed to represent a segment
	// containing the given number of characters using the given mode,
	// or None if the the number of needed bits exceeds usize::MAX. Notes:
	// - For byte mode, numchars measures the number of bytes, not Unicode code points.
	// - For ECI mode, numchars must be 0, and the worst-case number of bits is returned.
	//   An actual ECI segment can have shorter data. For non-ECI modes, the result is exact.
	fn calc_bit_length(mode: QrSegmentMode, numchars: usize) -> Option<usize> {
		// Returns ceil((numer / denom) * numchars)
		let mul_frac_ceil = |numer: usize, denom: usize|
			Some(numchars)
				.and_then(|x| x.checked_mul(numer))
				.and_then(|x| x.checked_add(denom - 1))
				.map(|x| x / denom);
		
		use QrSegmentMode::*;
		match mode {
			Numeric      => mul_frac_ceil(10, 3),
			Alphanumeric => mul_frac_ceil(11, 2),
			Byte         => mul_frac_ceil( 8, 1),
			Kanji        => mul_frac_ceil(13, 1),
			Eci => {
				assert_eq!(numchars, 0);
				Some(3 * 8)
			},
		}
	}
	
	
	// Calculates and returns the number of bits needed to encode the given
	// segments at the given version. The result is None if a segment has too many
	// characters to fit its length field, or the total bits exceeds usize::MAX.
	fn get_total_bits(segs: &[Self], version: Version) -> Option<usize> {
		let mut result: usize = 0;
		for seg in segs {
			let ccbits: u8 = seg.mode.num_char_count_bits(version);
			// ccbits can be as large as 16, but usize can be as small as 16
			if let Some(limit) = 1usize.checked_shl(ccbits.into()) {
				if seg.numchars >= limit {
					return None;  // The segment's length doesn't fit the field's bit width
				}
			}
			result = result.checked_add(4 + usize::from(ccbits))?;
			result = result.checked_add(seg.bitlength)?;
		}
		Some(result)
	}
	
	
	/// Tests whether the given string can be encoded as a segment in numeric mode.
	/// A string is encodable iff each character is in the range 0 to 9.
	pub fn is_numeric(text: &str) -> bool {
		text.chars().all(|c| ('0' ..= '9').contains(&c))
	}
	
	
	/// Tests whether the given string can be encoded as a segment in alphanumeric mode.
	/// A string is encodable iff each character is in the following set: 0 to 9, A to Z
	/// (uppercase only), space, dollar, percent, asterisk, plus, hyphen, period, slash, colon.
	pub fn is_alphanumeric(text: &str) -> bool {
		text.chars().all(|c| ALPHANUMERIC_CHARSET.contains(c))
	}
	
}


// The set of all legal characters in alphanumeric mode,
// where each character value maps to the index in the string.
static ALPHANUMERIC_CHARSET: &str = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";



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


/*---- BitBuffer functionality ----*/

/// An appendable sequence of bits (0s and 1s).
/// 
/// Mainly used by QrSegment.
pub struct BitBuffer<'a> {
	
	data: &'a mut [u8],
	
	length: usize,
	
}


impl<'a> BitBuffer<'a> {
	
	// Creates a bit buffer based on the given byte array.
	pub fn new(buffer: &'a mut [u8]) -> Self {
		Self {
			data: buffer,
			length: 0,
		}
	}
	
	
	// Returns the length of this bit buffer, in bits.
	pub fn len(&self) -> usize {
		self.length
	}
	
	
	// Appends the given number of low-order bits of the given value to this byte-based
	// bit buffer, increasing the bit length. Requires 0 <= numBits <= 31 and val < 2^numBits.
	pub fn append_bits(&mut self, val: u32, len: u8) {
		assert!(len <= 31 && val >> len == 0);
		assert!(usize::from(len) <= usize::MAX - self.length);
		for i in (0 .. len).rev() {
			let index: usize = self.length >> 3;
			let shift: u8 = 7 - ((self.length as u8) & 7);
			let bit: u8 = ((val >> i) as u8) & 1;
			if shift == 7 {
				self.data[index] = bit << shift;
			} else {
				self.data[index] |= bit << shift;
			}
			self.length += 1;
		}
	}
	
}



/*---- Miscellaneous values ----*/

/// The error type when the supplied data does not fit any QR Code version.
///
/// Ways to handle this exception include:
/// 
/// - Decrease the error correction level if it was greater than `QrCodeEcc::Low`.
/// - Increase the maxversion argument if it was less than `Version::MAX`.
/// - Split the text data into better or optimal segments in order to reduce the number of bits required.
/// - Change the text or binary data to be shorter.
/// - Change the text to fit the character set of a particular segment mode (e.g. alphanumeric).
/// - Propagate the error upward to the caller/user.
#[derive(Debug, Clone)]
pub enum DataTooLong {
	SegmentTooLong,
	DataOverCapacity(usize, usize),
}

impl core::fmt::Display for DataTooLong {
	fn fmt(&self, f: &mut core::fmt::Formatter) -> core::fmt::Result {
		match *self {
			Self::SegmentTooLong => write!(f, "Segment too long"),
			Self::DataOverCapacity(datalen, maxcapacity) =>
				write!(f, "Data length = {} bits, Max capacity = {} bits", datalen, maxcapacity),
		}
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
	pub const fn new(ver: u8) -> Self {
		assert!(Version::MIN.value() <= ver && ver <= Version::MAX.value(), "Version number out of range");
		Self(ver)
	}
	
	/// Returns the value, which is in the range [1, 40].
	pub const fn value(self) -> u8 {
		self.0
	}
	
	/// Returns the minimum length required for the output and temporary
	/// buffers when creating a QR Code of this version number.
	pub const fn buffer_len(self) -> usize {
		let sidelen = (self.0 as usize) * 4 + 17;
		(sidelen * sidelen + 7) / 8 + 1
	}
}


/// A number between 0 and 7 (inclusive).
#[derive(Copy, Clone, PartialEq, Eq, PartialOrd, Ord, Debug)]
pub struct Mask(u8);

impl Mask {
	/// Creates a mask object from the given number.
	/// 
	/// Panics if the number is outside the range [0, 7].
	pub const fn new(mask: u8) -> Self {
		assert!(mask <= 7, "Mask value out of range");
		Self(mask)
	}
	
	/// Returns the value, which is in the range [0, 7].
	pub const fn value(self) -> u8 {
		self.0
	}
}


// Returns true iff the i'th bit of x is set to 1.
fn get_bit(x: u32, i: u8) -> bool {
	(x >> i) & 1 != 0
}
