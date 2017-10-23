# 
# QR Code generator library (Python 2, 3)
# 
# Copyright (c) Project Nayuki. (MIT License)
# https://www.nayuki.io/page/qr-code-generator-library
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy of
# this software and associated documentation files (the "Software"), to deal in
# the Software without restriction, including without limitation the rights to
# use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
# the Software, and to permit persons to whom the Software is furnished to do so,
# subject to the following conditions:
# - The above copyright notice and this permission notice shall be included in
#   all copies or substantial portions of the Software.
# - The Software is provided "as is", without warranty of any kind, express or
#   implied, including but not limited to the warranties of merchantability,
#   fitness for a particular purpose and noninfringement. In no event shall the
#   authors or copyright holders be liable for any claim, damages or other
#   liability, whether in an action of contract, tort or otherwise, arising from,
#   out of or in connection with the Software or the use or other dealings in the
#   Software.
# 

import itertools, re, sys


"""
This module "qrcodegen", public members:
- Class QrCode:
  - Function encode_text(str text, QrCode.Ecc ecl) -> QrCode
  - Function encode_binary(bytes data, QrCode.Ecc ecl) -> QrCode
  - Function encode_segments(list<QrSegment> segs, QrCode.Ecc ecl,
        int minversion=1, int maxversion=40, mask=-1, boostecl=true) -> QrCode
  - Constants int MIN_VERSION, MAX_VERSION
  - Constructor QrCode(bytes datacodewords, int mask, int version, QrCode.Ecc ecl)
  - Method get_version() -> int
  - Method get_size() -> int
  - Method get_error_correction_level() -> QrCode.Ecc
  - Method get_mask() -> int
  - Method get_module(int x, int y) -> bool
  - Method to_svg_str(int border) -> str
  - Enum Ecc:
    - Constants LOW, MEDIUM, QUARTILE, HIGH
    - Field int ordinal
- Class QrSegment:
  - Function make_bytes(bytes data) -> QrSegment
  - Function make_numeric(str digits) -> QrSegment
  - Function make_alphanumeric(str text) -> QrSegment
  - Function make_segments(str text) -> list<QrSegment>
  - Function make_eci(int assignval) -> QrSegment
  - Constructor QrSegment(QrSegment.Mode mode, int numch, list<int> bitdata)
  - Method get_mode() -> QrSegment.Mode
  - Method get_num_chars() -> int
  - Method get_bits() -> list<int>
  - Constants regex NUMERIC_REGEX, ALPHANUMERIC_REGEX
  - Enum Mode:
    - Constants NUMERIC, ALPHANUMERIC, BYTE, KANJI, ECI
"""


# ---- QR Code symbol class ----

class QrCode(object):
	"""Represents an immutable square grid of black or white cells for a QR Code symbol. This class covers the
	QR Code model 2 specification, supporting all versions (sizes) from 1 to 40, all 4 error correction levels."""
	
	# ---- Public static factory functions ----
	
	@staticmethod
	def encode_text(text, ecl):
		"""Returns a QR Code symbol representing the specified Unicode text string at the specified error correction level.
		As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer
		Unicode code points (not UTF-16 code units) if the low error correction level is used. The smallest possible
		QR Code version is automatically chosen for the output. The ECC level of the result may be higher than the
		ecl argument if it can be done without increasing the version."""
		segs = QrSegment.make_segments(text)
		return QrCode.encode_segments(segs, ecl)
	
	
	@staticmethod
	def encode_binary(data, ecl):
		"""Returns a QR Code symbol representing the given binary data string at the given error correction level.
		This function always encodes using the binary segment mode, not any text mode. The maximum number of
		bytes allowed is 2953. The smallest possible QR Code version is automatically chosen for the output.
		The ECC level of the result may be higher than the ecl argument if it can be done without increasing the version."""
		if not isinstance(data, (bytes, bytearray)):
			raise TypeError("Byte string/list expected")
		return QrCode.encode_segments([QrSegment.make_bytes(data)], ecl)
	
	
	@staticmethod
	def encode_segments(segs, ecl, minversion=1, maxversion=40, mask=-1, boostecl=True):
		"""Returns a QR Code symbol representing the given data segments with the given encoding parameters.
		The smallest possible QR Code version within the given range is automatically chosen for the output.
		This function allows the user to create a custom sequence of segments that switches
		between modes (such as alphanumeric and binary) to encode text more efficiently.
		This function is considered to be lower level than simply encoding text or binary data."""
		
		if not (QrCode.MIN_VERSION <= minversion <= maxversion <= QrCode.MAX_VERSION) or not (-1 <= mask <= 7):
			raise ValueError("Invalid value")
		
		# Find the minimal version number to use
		for version in range(minversion, maxversion + 1):
			datacapacitybits = QrCode._get_num_data_codewords(version, ecl) * 8  # Number of data bits available
			datausedbits = QrSegment.get_total_bits(segs, version)
			if datausedbits is not None and datausedbits <= datacapacitybits:
				break  # This version number is found to be suitable
			if version >= maxversion:  # All versions in the range could not fit the given data
				raise ValueError("Data too long")
		if datausedbits is None:
			raise AssertionError()
		
		# Increase the error correction level while the data still fits in the current version number
		for newecl in (QrCode.Ecc.MEDIUM, QrCode.Ecc.QUARTILE, QrCode.Ecc.HIGH):
			if boostecl and datausedbits <= QrCode._get_num_data_codewords(version, newecl) * 8:
				ecl = newecl
		
		# Create the data bit string by concatenating all segments
		datacapacitybits = QrCode._get_num_data_codewords(version, ecl) * 8
		bb = _BitBuffer()
		for seg in segs:
			bb.append_bits(seg.get_mode().get_mode_bits(), 4)
			bb.append_bits(seg.get_num_chars(), seg.get_mode().num_char_count_bits(version))
			bb.extend(seg._bitdata)
		
		# Add terminator and pad up to a byte if applicable
		bb.append_bits(0, min(4, datacapacitybits - len(bb)))
		bb.append_bits(0, -len(bb) % 8)  # Note: Python's modulo on negative numbers behaves better than C family languages
		
		# Pad with alternate bytes until data capacity is reached
		for padbyte in itertools.cycle((0xEC, 0x11)):
			if len(bb) >= datacapacitybits:
				break
			bb.append_bits(padbyte, 8)
		assert len(bb) % 8 == 0
		
		# Create the QR Code symbol
		return QrCode(bb.get_bytes(), mask, version, ecl)
	
	
	# ---- Public constants ----
	
	MIN_VERSION =  1
	MAX_VERSION = 40
	
	
	# ---- Constructor ----
	
	def __init__(self, datacodewords, mask, version, errcorlvl):
		"""Creates a new QR Code symbol with the given version number, error correction level, binary data array,
		and mask number. mask = -1 is for automatic choice, or 0 to 7 for fixed choice. This is a cumbersome low-level constructor
		that should not be invoked directly by the user. To go one level up, see the QrCode.encode_segments() function."""
		
		# Check arguments and handle simple scalar fields
		if not (-1 <= mask <= 7):
			raise ValueError("Mask value out of range")
		if not (QrCode.MIN_VERSION <= version <= QrCode.MAX_VERSION):
			raise ValueError("Version value out of range")
		if not isinstance(errcorlvl, QrCode.Ecc):
			raise TypeError("QrCode.Ecc expected")
		self._version = version
		self._errcorlvl = errcorlvl
		self._size = version * 4 + 17
		
		if len(datacodewords) != QrCode._get_num_data_codewords(version, errcorlvl):
			raise ValueError("Invalid array length")
		# Initialize grids of modules
		self._modules    = [[False] * self._size for _ in range(self._size)]  # The modules of the QR symbol; start with entirely white grid
		self._isfunction = [[False] * self._size for _ in range(self._size)]  # Indicates function modules that are not subjected to masking
		# Draw function patterns, draw all codewords
		self._draw_function_patterns()
		allcodewords = self._append_error_correction(datacodewords)
		self._draw_codewords(allcodewords)
		
		# Handle masking
		if mask == -1:  # Automatically choose best mask
			minpenalty = 1 << 32
			for i in range(8):
				self._draw_format_bits(i)
				self._apply_mask(i)
				penalty = self._get_penalty_score()
				if penalty < minpenalty:
					mask = i
					minpenalty = penalty
				self._apply_mask(i)  # Undoes the mask due to XOR
		assert 0 <= mask <= 7
		self._draw_format_bits(mask)  # Overwrite old format bits
		self._apply_mask(mask)  # Apply the final choice of mask
		self._mask = mask
	
	
	# ---- Accessor methods ----
	
	def get_version(self):
		"""Returns this QR Code symbol's version number, which is always between 1 and 40 (inclusive)."""
		return self._version
	
	def get_size(self):
		"""Returns the width and height of this QR Code symbol, measured in modules.
		Always equal to version * 4 + 17, in the range 21 to 177."""
		return self._size
	
	def get_error_correction_level(self):
		"""Returns the error correction level used in this QR Code symbol."""
		return self._errcorlvl
	
	def get_mask(self):
		"""Returns the mask pattern used in this QR Code symbol, in the range 0 to 7 (i.e. unsigned 3-bit integer).
		Note that even if a constructor was called with automatic masking requested
		(mask = -1), the resulting object will still have a mask value between 0 and 7."""
		return self._mask
	
	def get_module(self, x, y):
		"""Returns the color of the module (pixel) at the given coordinates, which is either
		False for white or True for black. The top left corner has the coordinates (x=0, y=0).
		If the given coordinates are out of bounds, then False (white) is returned."""
		return (0 <= x < self._size) and (0 <= y < self._size) and self._modules[y][x]
	
	
	# ---- Public instance methods ----
	
	def to_svg_str(self, border):
		"""Based on the given number of border modules to add as padding, this returns a
		string whose contents represents an SVG XML file that depicts this QR Code symbol."""
		if border < 0:
			raise ValueError("Border must be non-negative")
		parts = []
		for y in range(-border, self._size + border):
			for x in range(-border, self._size + border):
				if self.get_module(x, y):
					parts.append("M{},{}h1v1h-1z".format(x + border, y + border))
		return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<svg xmlns="http://www.w3.org/2000/svg" version="1.1" viewBox="0 0 {0} {0}" stroke="none">
	<rect width="100%" height="100%" fill="#FFFFFF"/>
	<path d="{1}" fill="#000000"/>
</svg>
""".format(self._size + border * 2, " ".join(parts))
	
	
	# ---- Private helper methods for constructor: Drawing function modules ----
	
	def _draw_function_patterns(self):
		# Draw horizontal and vertical timing patterns
		for i in range(self._size):
			self._set_function_module(6, i, i % 2 == 0)
			self._set_function_module(i, 6, i % 2 == 0)
		
		# Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
		self._draw_finder_pattern(3, 3)
		self._draw_finder_pattern(self._size - 4, 3)
		self._draw_finder_pattern(3, self._size - 4)
		
		# Draw numerous alignment patterns
		alignpatpos = QrCode._get_alignment_pattern_positions(self._version)
		numalign = len(alignpatpos)
		skips = ((0, 0), (0, numalign - 1), (numalign - 1, 0))  # Skip the three finder corners
		for i in range(numalign):
			for j in range(numalign):
				if (i, j) not in skips:
					self._draw_alignment_pattern(alignpatpos[i], alignpatpos[j])
		
		# Draw configuration data
		self._draw_format_bits(0)  # Dummy mask value; overwritten later in the constructor
		self._draw_version()
	
	
	def _draw_format_bits(self, mask):
		"""Draws two copies of the format bits (with its own error correction code)
		based on the given mask and this object's error correction level field."""
		# Calculate error correction code and pack bits
		data = self._errcorlvl.formatbits << 3 | mask  # errCorrLvl is uint2, mask is uint3
		rem = data
		for _ in range(10):
			rem = (rem << 1) ^ ((rem >> 9) * 0x537)
		data = data << 10 | rem
		data ^= 0x5412  # uint15
		assert data >> 15 == 0
		
		# Draw first copy
		for i in range(0, 6):
			self._set_function_module(8, i, (data >> i) & 1 != 0)
		self._set_function_module(8, 7, (data >> 6) & 1 != 0)
		self._set_function_module(8, 8, (data >> 7) & 1 != 0)
		self._set_function_module(7, 8, (data >> 8) & 1 != 0)
		for i in range(9, 15):
			self._set_function_module(14 - i, 8, (data >> i) & 1 != 0)
		
		# Draw second copy
		for i in range(0, 8):
			self._set_function_module(self._size - 1 - i, 8, (data >> i) & 1 != 0)
		for i in range(8, 15):
			self._set_function_module(8, self._size - 15 + i, (data >> i) & 1 != 0)
		self._set_function_module(8, self._size - 8, True)
	
	
	def _draw_version(self):
		"""Draws two copies of the version bits (with its own error correction code),
		based on this object's version field (which only has an effect for 7 <= version <= 40)."""
		if self._version < 7:
			return
		
		# Calculate error correction code and pack bits
		rem = self._version  # version is uint6, in the range [7, 40]
		for _ in range(12):
			rem = (rem << 1) ^ ((rem >> 11) * 0x1F25)
		data = self._version << 12 | rem  # uint18
		assert data >> 18 == 0
		
		# Draw two copies
		for i in range(18):
			bit = (data >> i) & 1 != 0
			a, b = self._size - 11 + i % 3, i // 3
			self._set_function_module(a, b, bit)
			self._set_function_module(b, a, bit)
	
	
	def _draw_finder_pattern(self, x, y):
		"""Draws a 9*9 finder pattern including the border separator, with the center module at (x, y)."""
		for i in range(-4, 5):
			for j in range(-4, 5):
				xx, yy = x + j, y + i
				if (0 <= xx < self._size) and (0 <= yy < self._size):
					# Chebyshev/infinity norm
					self._set_function_module(xx, yy, max(abs(i), abs(j)) not in (2, 4))
	
	
	def _draw_alignment_pattern(self, x, y):
		"""Draws a 5*5 alignment pattern, with the center module at (x, y)."""
		for i in range(-2, 3):
			for j in range(-2, 3):
				self._set_function_module(x + j, y + i, max(abs(i), abs(j)) != 1)
	
	
	def _set_function_module(self, x, y, isblack):
		"""Sets the color of a module and marks it as a function module.
		Only used by the constructor. Coordinates must be in range."""
		assert type(isblack) is bool
		self._modules[y][x] = isblack
		self._isfunction[y][x] = True
	
	
	# ---- Private helper methods for constructor: Codewords and masking ----
	
	def _append_error_correction(self, data):
		"""Returns a new byte string representing the given data with the appropriate error correction
		codewords appended to it, based on this object's version and error correction level."""
		version = self._version
		assert len(data) == QrCode._get_num_data_codewords(version, self._errcorlvl)
		
		# Calculate parameter numbers
		numblocks = QrCode._NUM_ERROR_CORRECTION_BLOCKS[self._errcorlvl.ordinal][version]
		blockecclen = QrCode._ECC_CODEWORDS_PER_BLOCK[self._errcorlvl.ordinal][version]
		rawcodewords = QrCode._get_num_raw_data_modules(version) // 8
		numshortblocks = numblocks - rawcodewords % numblocks
		shortblocklen = rawcodewords // numblocks
		
		# Split data into blocks and append ECC to each block
		blocks = []
		rs = _ReedSolomonGenerator(blockecclen)
		k = 0
		for i in range(numblocks):
			dat = data[k : k + shortblocklen - blockecclen + (0 if i < numshortblocks else 1)]
			k += len(dat)
			ecc = rs.get_remainder(dat)
			if i < numshortblocks:
				dat.append(0)
			dat.extend(ecc)
			blocks.append(dat)
		assert k == len(data)
		
		# Interleave (not concatenate) the bytes from every block into a single sequence
		result = []
		for i in range(len(blocks[0])):
			for (j, blk) in enumerate(blocks):
				# Skip the padding byte in short blocks
				if i != shortblocklen - blockecclen or j >= numshortblocks:
					result.append(blk[i])
		assert len(result) == rawcodewords
		return result
	
	
	def _draw_codewords(self, data):
		"""Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
		data area of this QR Code symbol. Function modules need to be marked off before this is called."""
		assert len(data) == QrCode._get_num_raw_data_modules(self._version) // 8
		
		i = 0  # Bit index into the data
		# Do the funny zigzag scan
		for right in range(self._size - 1, 0, -2):  # Index of right column in each column pair
			if right <= 6:
				right -= 1
			for vert in range(self._size):  # Vertical counter
				for j in range(2):
					x = right - j  # Actual x coordinate
					upward = (right + 1) & 2 == 0
					y = (self._size - 1 - vert) if upward else vert  # Actual y coordinate
					if not self._isfunction[y][x] and i < len(data) * 8:
						self._modules[y][x] = (data[i >> 3] >> (7 - (i & 7))) & 1 != 0
						i += 1
					# If there are any remainder bits (0 to 7), they are already
					# set to 0/false/white when the grid of modules was initialized
		assert i == len(data) * 8
	
	
	def _apply_mask(self, mask):
		"""XORs the data modules in this QR Code with the given mask pattern. Due to XOR's mathematical
		properties, calling applyMask(m) twice with the same value is equivalent to no change at all.
		This means it is possible to apply a mask, undo it, and try another mask. Note that a final
		well-formed QR Code symbol needs exactly one mask applied (not zero, not two, etc.)."""
		if not (0 <= mask <= 7):
			raise ValueError("Mask value out of range")
		masker = QrCode._MASK_PATTERNS[mask]
		for y in range(self._size):
			for x in range(self._size):
				self._modules[y][x] ^= (masker(x, y) == 0) and (not self._isfunction[y][x])
	
	
	def _get_penalty_score(self):
		"""Calculates and returns the penalty score based on state of this QR Code's current modules.
		This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score."""
		result = 0
		size = self._size
		modules = self._modules
		
		# Adjacent modules in row having same color
		for y in range(size):
			for x in range(size):
				if x == 0 or modules[y][x] != colorx:
					colorx = modules[y][x]
					runx = 1
				else:
					runx += 1
					if runx == 5:
						result += QrCode._PENALTY_N1
					elif runx > 5:
						result += 1
		# Adjacent modules in column having same color
		for x in range(size):
			for y in range(size):
				if y == 0 or modules[y][x] != colory:
					colory = modules[y][x]
					runy = 1
				else:
					runy += 1
					if runy == 5:
						result += QrCode._PENALTY_N1
					elif runy > 5:
						result += 1
		
		# 2*2 blocks of modules having same color
		for y in range(size - 1):
			for x in range(size - 1):
				if modules[y][x] == modules[y][x + 1] == modules[y + 1][x] == modules[y + 1][x + 1]:
					result += QrCode._PENALTY_N2
		
		# Finder-like pattern in rows
		for y in range(size):
			bits = 0
			for x in range(size):
				bits = ((bits << 1) & 0x7FF) | (1 if modules[y][x] else 0)
				if x >= 10 and bits in (0x05D, 0x5D0):  # Needs 11 bits accumulated
					result += QrCode._PENALTY_N3
		# Finder-like pattern in columns
		for x in range(size):
			bits = 0
			for y in range(size):
				bits = ((bits << 1) & 0x7FF) | (1 if modules[y][x] else 0)
				if y >= 10 and bits in (0x05D, 0x5D0):  # Needs 11 bits accumulated
					result += QrCode._PENALTY_N3
		
		# Balance of black and white modules
		black = sum((1 if cell else 0) for row in modules for cell in row)
		total = size**2
		# Find smallest k such that (45-5k)% <= dark/total <= (55+5k)%
		for k in itertools.count():
			if (9-k)*total <= black*20 <= (11+k)*total:
				break
			result += QrCode._PENALTY_N4
		return result
	
	
	# ---- Private static helper functions ----
	
	@staticmethod
	def _get_alignment_pattern_positions(ver):
		"""Returns a sequence of positions of the alignment patterns in ascending order. These positions are
		used on both the x and y axes. Each value in the resulting sequence is in the range [0, 177).
		This stateless pure function could be implemented as table of 40 variable-length lists of integers."""
		if not (QrCode.MIN_VERSION <= ver <= QrCode.MAX_VERSION):
			raise ValueError("Version number out of range")
		elif ver == 1:
			return []
		else:
			numalign = ver // 7 + 2
			if ver != 32:
				# ceil((size - 13) / (2*numalign - 2)) * 2
				step = (ver * 4 + numalign * 2 + 1) // (2 * numalign - 2) * 2
			else:  # C-C-C-Combo breaker!
				step = 26
			result = [6]
			pos = ver * 4 + 10
			for _ in range(numalign - 1):
				result.insert(1, pos)
				pos -= step
			return result
	
	
	@staticmethod
	def _get_num_raw_data_modules(ver):
		"""Returns the number of data bits that can be stored in a QR Code of the given version number, after
		all function modules are excluded. This includes remainder bits, so it might not be a multiple of 8.
		The result is in the range [208, 29648]. This could be implemented as a 40-entry lookup table."""
		if not (QrCode.MIN_VERSION <= ver <= QrCode.MAX_VERSION):
			raise ValueError("Version number out of range")
		result = (16 * ver + 128) * ver + 64
		if ver >= 2:
			numalign = ver // 7 + 2
			result -= (25 * numalign - 10) * numalign - 55
			if ver >= 7:
				result -= 18 * 2  # Subtract version information
		return result
	
	
	@staticmethod
	def _get_num_data_codewords(ver, ecl):
		"""Returns the number of 8-bit data (i.e. not error correction) codewords contained in any
		QR Code of the given version number and error correction level, with remainder bits discarded.
		This stateless pure function could be implemented as a (40*4)-cell lookup table."""
		if not (QrCode.MIN_VERSION <= ver <= QrCode.MAX_VERSION):
			raise ValueError("Version number out of range")
		return QrCode._get_num_raw_data_modules(ver) // 8 \
			- QrCode._ECC_CODEWORDS_PER_BLOCK[ecl.ordinal][ver] \
			* QrCode._NUM_ERROR_CORRECTION_BLOCKS[ecl.ordinal][ver]
	
	
	# ---- Private tables of constants ----
	
	# For use in getPenaltyScore(), when evaluating which mask is best.
	_PENALTY_N1 = 3
	_PENALTY_N2 = 3
	_PENALTY_N3 = 40
	_PENALTY_N4 = 10
	
	_ECC_CODEWORDS_PER_BLOCK = (
		# Version: (note that index 0 is for padding, and is set to an illegal value)
		#   0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
		(None,  7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),  # Low
		(None, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28),  # Medium
		(None, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),  # Quartile
		(None, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30))  # High
	
	_NUM_ERROR_CORRECTION_BLOCKS = (
		# Version: (note that index 0 is for padding, and is set to an illegal value)
		#   0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
		(None, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4,  4,  4,  4,  4,  6,  6,  6,  6,  7,  8,  8,  9,  9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25),  # Low
		(None, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5,  5,  8,  9,  9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49),  # Medium
		(None, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8,  8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68),  # Quartile
		(None, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81))  # High
	
	_MASK_PATTERNS = (
		(lambda x, y:  (x + y) % 2                  ),
		(lambda x, y:  y % 2                        ),
		(lambda x, y:  x % 3                        ),
		(lambda x, y:  (x + y) % 3                  ),
		(lambda x, y:  (x // 3 + y // 2) % 2        ),
		(lambda x, y:  x * y % 2 + x * y % 3        ),
		(lambda x, y:  (x * y % 2 + x * y % 3) % 2  ),
		(lambda x, y:  ((x + y) % 2 + x * y % 3) % 2),
	)
	
	
	# ---- Public helper enumeration ----
	
	class Ecc(object):
		"""Represents the error correction level used in a QR Code symbol."""
		# Private constructor
		def __init__(self, i, fb):
			self.ordinal = i  # (Public) In the range 0 to 3 (unsigned 2-bit integer)
			self.formatbits = fb  # (Package-private) In the range 0 to 3 (unsigned 2-bit integer)
	
	# Public constants. Create them outside the class.
	Ecc.LOW      = Ecc(0, 1)
	Ecc.MEDIUM   = Ecc(1, 0)
	Ecc.QUARTILE = Ecc(2, 3)
	Ecc.HIGH     = Ecc(3, 2)



# ---- Data segment class ----

class QrSegment(object):
	"""Represents a character string to be encoded in a QR Code symbol. Each segment has
	a mode, and a sequence of characters that is already encoded as a sequence of bits.
	Instances of this class are immutable.
	This segment class imposes no length restrictions, but QR Codes have restrictions.
	Even in the most favorable conditions, a QR Code can only hold 7089 characters of data.
	Any segment longer than this is meaningless for the purpose of generating QR Codes."""
	
	# ---- Public static factory functions ----
	
	@staticmethod
	def make_bytes(data):
		"""Returns a segment representing the given binary data encoded in byte mode."""
		py3 = sys.version_info.major >= 3
		if (py3 and isinstance(data, str)) or (not py3 and isinstance(data, unicode)):
			raise TypeError("Byte string/list expected")
		if not py3 and isinstance(data, str):
			data = bytearray(data)
		bb = _BitBuffer()
		for b in data:
			bb.append_bits(b, 8)
		return QrSegment(QrSegment.Mode.BYTE, len(data), bb)
	
	
	@staticmethod
	def make_numeric(digits):
		"""Returns a segment representing the given string of decimal digits encoded in numeric mode."""
		if QrSegment.NUMERIC_REGEX.match(digits) is None:
			raise ValueError("String contains non-numeric characters")
		bb = _BitBuffer()
		for i in range(0, len(digits) - 2, 3):  # Process groups of 3
			bb.append_bits(int(digits[i : i + 3]), 10)
		rem = len(digits) % 3
		if rem > 0:  # 1 or 2 digits remaining
			bb.append_bits(int(digits[-rem : ]), rem * 3 + 1)
		return QrSegment(QrSegment.Mode.NUMERIC, len(digits), bb)
	
	
	@staticmethod
	def make_alphanumeric(text):
		"""Returns a segment representing the given text string encoded in alphanumeric mode.
		The characters allowed are: 0 to 9, A to Z (uppercase only), space,
		dollar, percent, asterisk, plus, hyphen, period, slash, colon."""
		if QrSegment.ALPHANUMERIC_REGEX.match(text) is None:
			raise ValueError("String contains unencodable characters in alphanumeric mode")
		bb = _BitBuffer()
		for i in range(0, len(text) - 1, 2):  # Process groups of 2
			temp = QrSegment._ALPHANUMERIC_ENCODING_TABLE[text[i]] * 45
			temp += QrSegment._ALPHANUMERIC_ENCODING_TABLE[text[i + 1]]
			bb.append_bits(temp, 11)
		if len(text) % 2 > 0:  # 1 character remaining
			bb.append_bits(QrSegment._ALPHANUMERIC_ENCODING_TABLE[text[-1]], 6)
		return QrSegment(QrSegment.Mode.ALPHANUMERIC, len(text), bb)
	
	
	@staticmethod
	def make_segments(text):
		"""Returns a new mutable list of zero or more segments to represent the given Unicode text string.
		The result may use various segment modes and switch modes to optimize the length of the bit stream."""
		if not (isinstance(text, str) or (sys.version_info.major < 3 and isinstance(text, unicode))):
			raise TypeError("Text string expected")
		
		# Select the most efficient segment encoding automatically
		if text == "":
			return []
		elif QrSegment.NUMERIC_REGEX.match(text) is not None:
			return [QrSegment.make_numeric(text)]
		elif QrSegment.ALPHANUMERIC_REGEX.match(text) is not None:
			return [QrSegment.make_alphanumeric(text)]
		else:
			return [QrSegment.make_bytes(text.encode("UTF-8"))]
	
	
	@staticmethod
	def make_eci(assignval):
		"""Returns a segment representing an Extended Channel Interpretation
		(ECI) designator with the given assignment value."""
		bb = _BitBuffer()
		if 0 <= assignval < (1 << 7):
			bb.append_bits(assignval, 8)
		elif (1 << 7) <= assignval < (1 << 14):
			bb.append_bits(2, 2)
			bb.append_bits(assignval, 14)
		elif (1 << 14) <= assignval < 1000000:
			bb.append_bits(6, 3)
			bb.append_bits(assignval, 21)
		else:
			raise ValueError("ECI assignment value out of range")
		return QrSegment(QrSegment.Mode.ECI, 0, bb)
	
	
	# ---- Constructor ----
	
	def __init__(self, mode, numch, bitdata):
		if numch < 0 or not isinstance(mode, QrSegment.Mode):
			raise ValueError()
		self._mode = mode
		self._numchars = numch
		self._bitdata = list(bitdata)  # Make defensive copy
	
	
	# ---- Accessor methods ----
	
	def get_mode(self):
		return self._mode
	
	def get_num_chars(self):
		return self._numchars
	
	def get_bits(self):
		return list(self._bitdata)  # Make defensive copy
	
	
	# Package-private helper function.
	@staticmethod
	def get_total_bits(segs, version):
		if not (QrCode.MIN_VERSION <= version <= QrCode.MAX_VERSION):
			raise ValueError("Version number out of range")
		result = 0
		for seg in segs:
			ccbits = seg.get_mode().num_char_count_bits(version)
			# Fail if segment length value doesn't fit in the length field's bit-width
			if seg.get_num_chars() >= (1 << ccbits):
				return None
			result += 4 + ccbits + len(seg._bitdata)
		return result
	
	
	# ---- Constants ----
	
	# (Public) Can test whether a string is encodable in numeric mode (such as by using make_numeric())
	NUMERIC_REGEX = re.compile(r"[0-9]*\Z")
	
	# (Public) Can test whether a string is encodable in alphanumeric mode (such as by using make_alphanumeric())
	ALPHANUMERIC_REGEX = re.compile(r"[A-Z0-9 $%*+./:-]*\Z")
	
	# (Private) Dictionary of "0"->0, "A"->10, "$"->37, etc.
	_ALPHANUMERIC_ENCODING_TABLE = {ch: i for (i, ch) in enumerate("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:")}
	
	
	# ---- Public helper enumeration ----
	
	class Mode(object):
		"""The mode field of a segment. Immutable."""
		
		# Private constructor
		def __init__(self, modebits, charcounts):
			self._modebits = modebits
			self._charcounts = charcounts
		
		# Package-private method
		def get_mode_bits(self):
			"""Returns an unsigned 4-bit integer value (range 0 to 15) representing the mode indicator bits for this mode object."""
			return self._modebits
		
		# Package-private method
		def num_char_count_bits(self, ver):
			"""Returns the bit width of the segment character count field for this mode object at the given version number."""
			if    1 <= ver <=  9:  return self._charcounts[0]
			elif 10 <= ver <= 26:  return self._charcounts[1]
			elif 27 <= ver <= 40:  return self._charcounts[2]
			else:  raise ValueError("Version number out of range")
	
	# Public constants. Create them outside the class.
	Mode.NUMERIC      = Mode(0x1, (10, 12, 14))
	Mode.ALPHANUMERIC = Mode(0x2, ( 9, 11, 13))
	Mode.BYTE         = Mode(0x4, ( 8, 16, 16))
	Mode.KANJI        = Mode(0x8, ( 8, 10, 12))
	Mode.ECI          = Mode(0x7, ( 0,  0,  0))



# ---- Private helper classes ----

class _ReedSolomonGenerator(object):
	"""Computes the Reed-Solomon error correction codewords for a sequence of data codewords
	at a given degree. Objects are immutable, and the state only depends on the degree.
	This class exists because each data block in a QR Code shares the same the divisor polynomial."""
	
	def __init__(self, degree):
		"""Creates a Reed-Solomon ECC generator for the given degree. This could be implemented
		as a lookup table over all possible parameter values, instead of as an algorithm."""
		if degree < 1 or degree > 255:
			raise ValueError("Degree out of range")
		
		# Start with the monomial x^0
		self.coefficients = [0] * (degree - 1) + [1]
		
		# Compute the product polynomial (x - r^0) * (x - r^1) * (x - r^2) * ... * (x - r^{degree-1}),
		# drop the highest term, and store the rest of the coefficients in order of descending powers.
		# Note that r = 0x02, which is a generator element of this field GF(2^8/0x11D).
		root = 1
		for _ in range(degree):  # Unused variable i
			# Multiply the current product by (x - r^i)
			for j in range(degree):
				self.coefficients[j] = _ReedSolomonGenerator._multiply(self.coefficients[j], root)
				if j + 1 < degree:
					self.coefficients[j] ^= self.coefficients[j + 1]
			root = _ReedSolomonGenerator._multiply(root, 0x02)
	
	
	def get_remainder(self, data):
		"""Computes and returns the Reed-Solomon error correction codewords for the given
		sequence of data codewords. The returned object is always a new byte list.
		This method does not alter this object's state (because it is immutable)."""
		# Compute the remainder by performing polynomial division
		result = [0] * len(self.coefficients)
		for b in data:
			factor = b ^ result.pop(0)
			result.append(0)
			for i in range(len(result)):
				result[i] ^= _ReedSolomonGenerator._multiply(self.coefficients[i], factor)
		return result
	
	
	@staticmethod
	def _multiply(x, y):
		"""Returns the product of the two given field elements modulo GF(2^8/0x11D). The arguments and result
		are unsigned 8-bit integers. This could be implemented as a lookup table of 256*256 entries of uint8."""
		if x >> 8 != 0 or y >> 8 != 0:
			raise ValueError("Byte out of range")
		# Russian peasant multiplication
		z = 0
		for i in reversed(range(8)):
			z = (z << 1) ^ ((z >> 7) * 0x11D)
			z ^= ((y >> i) & 1) * x
		assert z >> 8 == 0
		return z



class _BitBuffer(list):
	"""An appendable sequence of bits (0's and 1's)."""
	
	def get_bytes(self):
		"""Packs this buffer's bits into bytes in big endian,
		padding with '0' bit values, and returns the new list."""
		result = [0] * ((len(self) + 7) // 8)
		for (i, bit) in enumerate(self):
			result[i >> 3] |= bit << (7 - (i & 7))
		return result
	
	def append_bits(self, val, n):
		"""Appends the given number of low bits of the given value
		to this sequence. Requires 0 <= val < 2^n."""
		if n < 0 or val >> n != 0:
			raise ValueError("Value out of range")
		self.extend(((val >> i) & 1) for i in reversed(range(n)))
