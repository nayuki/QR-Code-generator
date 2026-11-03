# 
# QR Code generator library (Python)
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

from __future__ import annotations
import array, base64, collections, itertools, re, sys
from collections.abc import Sequence
from typing import cast


# ---- QR Code symbol class ----

class QrCode:
	"""A QR Code symbol, which is a type of two-dimensional barcode.
	Invented by Denso Wave and described in the ISO/IEC 18004 standard.
	Instances of this class represent an immutable square grid of dark and light cells.
	The class provides static factory functions to create a QR Code from text or binary data.
	The class covers the QR Code Model 2 specification, supporting all versions (sizes)
	from 1 to 40, all 4 error correction levels, and 4 character encoding modes.
	
	Ways to create a QR Code object:
	- High level: Take the payload data and call QrCode.encode_text() or QrCode.encode_binary().
	- Mid level: Custom-make the list of segments and call QrCode.encode_segments().
	- Low level: Custom-make the array of data codeword bytes (including
	  segment headers and final padding, excluding error correction codewords),
	  supply the appropriate version number, and call the QrCode() constructor.
	(Note that all ways require supplying the desired error correction level.)"""
	
	# ---- Static factory functions (high level) ----
	
	@staticmethod
	def encode_text(text: str, ecl: QrCode.Ecc) -> QrCode:
		"""Returns a QR Code representing the given Unicode text string at the given error correction level.
		As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer
		Unicode code points (not UTF-16 code units) if the low error correction level is used. The smallest possible
		QR Code version is automatically chosen for the output. The ECC level of the result may be higher than the
		ecl argument if it can be done without increasing the version."""
		segs: list[QrSegment] = QrSegment.make_segments(text)
		return QrCode.encode_segments(segs, ecl)
	
	
	@staticmethod
	def encode_binary(data: bytes|Sequence[int], ecl: QrCode.Ecc) -> QrCode:
		"""Returns a QR Code representing the given binary data at the given error correction level.
		This function always encodes using the binary segment mode, not any text mode. The maximum number of
		bytes allowed is 2953. The smallest possible QR Code version is automatically chosen for the output.
		The ECC level of the result may be higher than the ecl argument if it can be done without increasing the version."""
		return QrCode.encode_segments([QrSegment.make_bytes(data)], ecl)
	
	
	# ---- Static factory functions (mid level) ----
	
	@staticmethod
	def encode_segments(segs: Sequence[QrSegment], ecl: QrCode.Ecc, minversion: int = 1, maxversion: int = 40, mask: int = -1, boostecl: bool = True) -> QrCode:
		"""Returns a QR Code representing the given segments with the given encoding parameters.
		The smallest possible QR Code version within the given range is automatically
		chosen for the output. Iff boostecl is true, then the ECC level of the result
		may be higher than the ecl argument if it can be done without increasing the
		version. The mask number is either between 0 to 7 (inclusive) to force that
		mask, or -1 to automatically choose an appropriate mask (which may be slow).
		This function allows the user to create a custom sequence of segments that switches
		between modes (such as alphanumeric and byte) to encode text in less space.
		This is a mid-level API; the high-level API is encode_text() and encode_binary()."""
		
		if not (QrCode.MIN_VERSION <= minversion <= maxversion <= QrCode.MAX_VERSION) or not (-1 <= mask <= 7):
			raise ValueError("Invalid value")
		
		# Find the minimal version number to use
		for version in range(minversion, maxversion + 1):
			datacapacitybits: int = QrCode._get_num_data_codewords(version, ecl) * 8  # Number of data bits available
			datausedbits: int|None = QrSegment.get_total_bits(segs, version)
			if (datausedbits is not None) and (datausedbits <= datacapacitybits):
				break  # This version number is found to be suitable
			if version >= maxversion:  # All versions in the range could not fit the given data
				msg: str = "Segment too long"
				if datausedbits is not None:
					msg = f"Data length = {datausedbits} bits, Max capacity = {datacapacitybits} bits"
				raise DataTooLongError(msg)
		assert datausedbits is not None
		
		# Increase the error correction level while the data still fits in the current version number
		for newecl in (QrCode.Ecc.MEDIUM, QrCode.Ecc.QUARTILE, QrCode.Ecc.HIGH):  # From low to high
			if boostecl and (datausedbits <= QrCode._get_num_data_codewords(version, newecl) * 8):
				ecl = newecl
		
		# Concatenate all segments to create the data bit string
		bb = _BitBuffer()
		for seg in segs:
			bb.append_bits(seg.get_mode().get_mode_bits(), 4)
			bb.append_bits(seg.get_num_chars(), seg.get_mode().num_char_count_bits(version))
			bb.extend(seg._bitdata)
		assert len(bb) == datausedbits
		
		# Add terminator and pad up to a byte if applicable
		datacapacitybits = QrCode._get_num_data_codewords(version, ecl) * 8
		assert len(bb) <= datacapacitybits
		bb.append_bits(0, min(4, datacapacitybits - len(bb)))
		bb.append_bits(0, -len(bb) % 8)  # Note: Python's modulo on negative numbers behaves better than C family languages
		assert len(bb) % 8 == 0
		
		# Pad with alternating bytes until data capacity is reached
		for padbyte in itertools.cycle((0xEC, 0x11)):
			if len(bb) >= datacapacitybits:
				break
			bb.append_bits(padbyte, 8)
		
		# Pack bits into bytes in big endian
		datacodewords = bytearray([0] * (len(bb) // 8))
		for (i, bit) in enumerate(bb):
			datacodewords[i >> 3] |= bit << (7 - (i & 7))
		
		# Create the QR Code object
		return QrCode(version, ecl, datacodewords, mask)
	
	
	# ---- Private fields ----
	
	# The version number of this QR Code, which is between 1 and 40 (inclusive).
	# This determines the size of this barcode.
	_version: int
	
	# The width and height of this QR Code, measured in modules, between
	# 21 and 177 (inclusive). This is equal to version * 4 + 17.
	_size: int
	
	# The error correction level used in this QR Code.
	_errcorlvl: QrCode.Ecc
	
	# The index of the mask pattern used in this QR Code, which is between 0 and 7 (inclusive).
	# Even if a QR Code is created with automatic masking requested (mask = -1),
	# the resulting object still has a mask value between 0 and 7.
	_mask: int
	
	# The modules of this QR Code (False = light, True = dark).
	# Immutable after constructor finishes. Accessed through get_module().
	_modules: list[list[bool]]
	
	# Indicates function modules that are not subjected to masking. Discarded when constructor finishes.
	_isfunction: list[list[bool]]
	
	
	# ---- Constructor (low level) ----
	
	def __init__(self, version: int, errcorlvl: QrCode.Ecc, datacodewords: bytes|Sequence[int], msk: int) -> None:
		"""Creates a new QR Code with the given version number,
		error correction level, data codeword bytes, and mask number.
		This is a low-level API that most users should not use directly.
		A mid-level API is the encode_segments() function."""
		
		# Check scalar arguments and set fields
		if not (QrCode.MIN_VERSION <= version <= QrCode.MAX_VERSION):
			raise ValueError("Version value out of range")
		if not (-1 <= msk <= 7):
			raise ValueError("Mask value out of range")
		
		self._version = version
		self._size = version * 4 + 17
		self._errcorlvl = errcorlvl
		
		# Initialize both grids to be size*size arrays of Boolean false
		self._modules    = [[False] * self._size for _ in range(self._size)]  # Initially all light
		self._isfunction = [[False] * self._size for _ in range(self._size)]
		
		# Compute ECC, draw modules
		self._draw_function_patterns()
		allcodewords: bytes = self._add_ecc_and_interleave(bytearray(datacodewords))
		self._draw_codewords(allcodewords)
		
		# Do masking
		if msk == -1:  # Automatically choose best mask
			minpenalty: int = 1 << 32
			for i in range(8):
				self._apply_mask(i)
				self._draw_format_bits(i)
				penalty = self._get_penalty_score()
				if penalty < minpenalty:
					msk = i
					minpenalty = penalty
				self._apply_mask(i)  # Undoes the mask due to XOR
		assert 0 <= msk <= 7
		self._mask = msk
		self._apply_mask(msk)  # Apply the final choice of mask
		self._draw_format_bits(msk)  # Overwrite old format bits
		
		del self._isfunction
	
	
	# ---- Accessor methods ----
	
	def get_version(self) -> int:
		"""Returns this QR Code's version number, in the range [1, 40]."""
		return self._version
	
	def get_size(self) -> int:
		"""Returns this QR Code's size, in the range [21, 177]."""
		return self._size
	
	def get_error_correction_level(self) -> QrCode.Ecc:
		"""Returns this QR Code's error correction level."""
		return self._errcorlvl
	
	def get_mask(self) -> int:
		"""Returns this QR Code's mask, in the range [0, 7]."""
		return self._mask
	
	def get_module(self, x: int, y: int) -> bool:
		"""Returns the color of the module (pixel) at the given coordinates, which is False
		for light or True for dark. The top left corner has the coordinates (x=0, y=0).
		If the given coordinates are out of bounds, then False (light) is returned."""
		return (0 <= x < self._size) and (0 <= y < self._size) and self._modules[y][x]
	
	
	# ---- Private helper methods for constructor: Drawing function modules ----
	
	def _draw_function_patterns(self) -> None:
		"""Reads this object's version field, and draws and marks all function modules."""
		# Draw horizontal and vertical timing patterns
		for i in range(self._size):
			self._set_function_module(6, i, i % 2 == 0)
			self._set_function_module(i, 6, i % 2 == 0)
		
		# Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
		self._draw_finder_pattern(3, 3)
		self._draw_finder_pattern(self._size - 4, 3)
		self._draw_finder_pattern(3, self._size - 4)
		
		# Draw numerous alignment patterns
		alignpatpos: list[int] = self._get_alignment_pattern_positions()
		skips: Sequence[tuple[int,int]] = ((0, 0), (0, len(alignpatpos) - 1), (len(alignpatpos) - 1, 0))
		for (i, x) in enumerate(alignpatpos):
			for (j, y) in enumerate(alignpatpos):
				if (i, j) not in skips:  # Don't draw on the three finder corners
					self._draw_alignment_pattern(x, y)
		
		# Draw configuration data
		self._draw_format_bits(0)  # Dummy mask value; overwritten later in the constructor
		self._draw_version()
	
	
	def _draw_format_bits(self, mask: int) -> None:
		"""Draws two copies of the format bits (with its own error correction code)
		based on the given mask and this object's error correction level field."""
		# Calculate error correction code and pack bits
		data: int = self._errcorlvl.formatbits << 3 | mask  # errCorrLvl is uint2, mask is uint3
		rem: int = data
		for _ in range(10):
			rem = (rem << 1) ^ ((rem >> 9) * 0x537)
		bits: int = (data << 10 | rem) ^ 0x5412  # uint15
		assert bits >> 15 == 0
		
		# Draw first copy
		for i in range(0, 6):
			self._set_function_module(8, i, _get_bit(bits, i))
		self._set_function_module(8, 7, _get_bit(bits, 6))
		self._set_function_module(8, 8, _get_bit(bits, 7))
		self._set_function_module(7, 8, _get_bit(bits, 8))
		for i in range(9, 15):
			self._set_function_module(14 - i, 8, _get_bit(bits, i))
		
		# Draw second copy
		for i in range(0, 8):
			self._set_function_module(self._size - 1 - i, 8, _get_bit(bits, i))
		for i in range(8, 15):
			self._set_function_module(8, self._size - 15 + i, _get_bit(bits, i))
		self._set_function_module(8, self._size - 8, True)  # Always dark
	
	
	def _draw_version(self) -> None:
		"""Draws two copies of the version bits (with its own error correction code),
		based on this object's version field, iff 7 <= version <= 40."""
		if self._version < 7:
			return
		
		# Calculate error correction code and pack bits
		rem: int = self._version  # version is uint6, in the range [7, 40]
		for _ in range(12):
			rem = (rem << 1) ^ ((rem >> 11) * 0x1F25)
		bits: int = self._version << 12 | rem  # uint18
		assert bits >> 18 == 0
		
		# Draw two copies
		for i in range(18):
			bit: bool = _get_bit(bits, i)
			a: int = self._size - 11 + i % 3
			b: int = i // 3
			self._set_function_module(a, b, bit)
			self._set_function_module(b, a, bit)
	
	
	def _draw_finder_pattern(self, x: int, y: int) -> None:
		"""Draws a 9*9 finder pattern including the border separator,
		with the center module at (x, y). Modules can be out of bounds."""
		for dy in range(-4, 5):
			for dx in range(-4, 5):
				xx, yy = x + dx, y + dy
				if (0 <= xx < self._size) and (0 <= yy < self._size):
					# Chebyshev/infinity norm
					self._set_function_module(xx, yy, max(abs(dx), abs(dy)) not in (2, 4))
	
	
	def _draw_alignment_pattern(self, x: int, y: int) -> None:
		"""Draws a 5*5 alignment pattern, with the center module
		at (x, y). All modules must be in bounds."""
		for dy in range(-2, 3):
			for dx in range(-2, 3):
				self._set_function_module(x + dx, y + dy, max(abs(dx), abs(dy)) != 1)
	
	
	def _set_function_module(self, x: int, y: int, isdark: bool) -> None:
		"""Sets the color of a module and marks it as a function module.
		Only used by the constructor. Coordinates must be in bounds."""
		assert type(isdark) is bool
		self._modules[y][x] = isdark
		self._isfunction[y][x] = True
	
	
	# ---- Private helper methods for constructor: Codewords and masking ----
	
	def _add_ecc_and_interleave(self, data: bytearray) -> bytes:
		"""Returns a new byte string representing the given data with the appropriate error correction
		codewords appended to it, based on this object's version and error correction level."""
		version: int = self._version
		assert len(data) == QrCode._get_num_data_codewords(version, self._errcorlvl)
		
		# Calculate parameter numbers
		numblocks: int = QrCode._NUM_ERROR_CORRECTION_BLOCKS[self._errcorlvl.ordinal][version]
		blockecclen: int = QrCode._ECC_CODEWORDS_PER_BLOCK  [self._errcorlvl.ordinal][version]
		rawcodewords: int = QrCode._get_num_raw_data_modules(version) // 8
		numshortblocks: int = numblocks - rawcodewords % numblocks
		shortblocklen: int = rawcodewords // numblocks
		
		# Split data into blocks and append ECC to each block
		blocks: list[bytes] = []
		rsdiv: bytes = QrCode._reed_solomon_compute_divisor(blockecclen)
		k: int = 0
		for i in range(numblocks):
			dat: bytearray = data[k : k + shortblocklen - blockecclen + (0 if i < numshortblocks else 1)]
			k += len(dat)
			ecc: bytes = QrCode._reed_solomon_compute_remainder(dat, rsdiv)
			if i < numshortblocks:
				dat.append(0)
			blocks.append(dat + ecc)
		assert k == len(data)
		
		# Interleave (not concatenate) the bytes from every block into a single sequence
		result = bytearray()
		for i in range(len(blocks[0])):
			for (j, blk) in enumerate(blocks):
				# Skip the padding byte in short blocks
				if (i != shortblocklen - blockecclen) or (j >= numshortblocks):
					result.append(blk[i])
		assert len(result) == rawcodewords
		return result
	
	
	def _draw_codewords(self, data: bytes) -> None:
		"""Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
		data area of this QR Code. Function modules need to be marked off before this is called."""
		assert len(data) == QrCode._get_num_raw_data_modules(self._version) // 8
		
		i: int = 0  # Bit index into the data
		# Do the funny zigzag scan
		for right in range(self._size - 1, 0, -2):  # Index of right column in each column pair
			if right <= 6:
				right -= 1
			for vert in range(self._size):  # Vertical counter
				for j in range(2):
					x: int = right - j  # Actual x coordinate
					upward: bool = (right + 1) & 2 == 0
					y: int = (self._size - 1 - vert) if upward else vert  # Actual y coordinate
					if (not self._isfunction[y][x]) and (i < len(data) * 8):
						self._modules[y][x] = _get_bit(data[i >> 3], 7 - (i & 7))
						i += 1
					# If this QR Code has any remainder bits (0 to 7), they were assigned as
					# 0/false/light by the constructor and are left unchanged by this method
		assert i == len(data) * 8
	
	
	def _apply_mask(self, mask: int) -> None:
		"""XORs the codeword modules in this QR Code with the given mask pattern.
		The function modules must be marked and the codeword bits must be drawn
		before masking. Due to the arithmetic of XOR, calling _apply_mask() with
		the same mask value a second time will undo the mask. A final well-formed
		QR Code needs exactly one (not zero, two, etc.) mask applied."""
		if not (0 <= mask <= 7):
			raise ValueError("Mask value out of range")
		masker: collections.abc.Callable[[int,int],int] = QrCode._MASK_PATTERNS[mask]
		for y in range(self._size):
			for x in range(self._size):
				self._modules[y][x] ^= (masker(x, y) == 0) and (not self._isfunction[y][x])
	
	
	def _get_penalty_score(self) -> int:
		"""Calculates and returns the penalty score based on state of this QR Code's current modules.
		This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score."""
		result: int = 0
		size: int = self._size
		modules: list[list[bool]] = self._modules
		
		# Adjacent modules in row having same color, and finder-like patterns
		for y in range(size):
			runcolor: bool = False
			runx: int = 0
			runhistory = collections.deque([0] * 7, 7)
			for x in range(size):
				if modules[y][x] == runcolor:
					runx += 1
					if runx == 5:
						result += QrCode._PENALTY_N1
					elif runx > 5:
						result += 1
				else:
					self._finder_penalty_add_history(runx, runhistory)
					if not runcolor:
						result += self._finder_penalty_count_patterns(runhistory) * QrCode._PENALTY_N3
					runcolor = modules[y][x]
					runx = 1
			result += self._finder_penalty_terminate_and_count(runcolor, runx, runhistory) * QrCode._PENALTY_N3
		# Adjacent modules in column having same color, and finder-like patterns
		for x in range(size):
			runcolor = False
			runy: int = 0
			runhistory = collections.deque([0] * 7, 7)
			for y in range(size):
				if modules[y][x] == runcolor:
					runy += 1
					if runy == 5:
						result += QrCode._PENALTY_N1
					elif runy > 5:
						result += 1
				else:
					self._finder_penalty_add_history(runy, runhistory)
					if not runcolor:
						result += self._finder_penalty_count_patterns(runhistory) * QrCode._PENALTY_N3
					runcolor = modules[y][x]
					runy = 1
			result += self._finder_penalty_terminate_and_count(runcolor, runy, runhistory) * QrCode._PENALTY_N3
		
		# 2*2 blocks of modules having same color
		for y in range(size - 1):
			for x in range(size - 1):
				if modules[y][x] == modules[y][x + 1] == modules[y + 1][x] == modules[y + 1][x + 1]:
					result += QrCode._PENALTY_N2
		
		# Balance of dark and light modules
		dark: int = sum((1 if cell else 0) for row in modules for cell in row)
		total: int = size**2  # Note that size is odd, so dark/total != 1/2
		# Compute the smallest integer k >= 0 such that (45-5k)% <= dark/total <= (55+5k)%
		k: int = (abs(dark * 20 - total * 10) + total - 1) // total - 1
		assert 0 <= k <= 9
		result += k * QrCode._PENALTY_N4
		assert 0 <= result <= 2568888  # Non-tight upper bound based on default values of PENALTY_N1, ..., N4
		return result
	
	
	# ---- Private helper functions ----
	
	def _get_alignment_pattern_positions(self) -> list[int]:
		"""Returns an ascending list of positions of alignment patterns for this version number.
		Each position is in the range [0,177), and are used on both the x and y axes.
		This could be implemented as lookup table of 40 variable-length lists of integers."""
		if self._version == 1:
			return []
		else:
			numalign: int = self._version // 7 + 2
			step: int = (self._version * 8 + numalign * 3 + 5) // (numalign * 4 - 4) * 2
			result: list[int] = [(self._size - 7 - i * step) for i in range(numalign - 1)] + [6]
			return list(reversed(result))
	
	
	@staticmethod
	def _get_num_raw_data_modules(ver: int) -> int:
		"""Returns the number of data bits that can be stored in a QR Code of the given version number, after
		all function modules are excluded. This includes remainder bits, so it might not be a multiple of 8.
		The result is in the range [208, 29648]. This could be implemented as a 40-entry lookup table."""
		if not (QrCode.MIN_VERSION <= ver <= QrCode.MAX_VERSION):
			raise ValueError("Version number out of range")
		result: int = (16 * ver + 128) * ver + 64
		if ver >= 2:
			numalign: int = ver // 7 + 2
			result -= (25 * numalign - 10) * numalign - 55
			if ver >= 7:
				result -= 36
		assert 208 <= result <= 29648
		return result
	
	
	@staticmethod
	def _get_num_data_codewords(ver: int, ecl: QrCode.Ecc) -> int:
		"""Returns the number of 8-bit data (i.e. not error correction) codewords contained in any
		QR Code of the given version number and error correction level, with remainder bits discarded.
		This stateless pure function could be implemented as a (40*4)-cell lookup table."""
		return QrCode._get_num_raw_data_modules(ver) // 8 \
			- QrCode._ECC_CODEWORDS_PER_BLOCK    [ecl.ordinal][ver] \
			* QrCode._NUM_ERROR_CORRECTION_BLOCKS[ecl.ordinal][ver]
	
	
	@staticmethod
	def _reed_solomon_compute_divisor(degree: int) -> bytes:
		"""Returns a Reed-Solomon ECC generator polynomial for the given degree. This could be
		implemented as a lookup table over all possible parameter values, instead of as an algorithm."""
		if not (1 <= degree <= 255):
			raise ValueError("Degree out of range")
		# Polynomial coefficients are stored from highest to lowest power, excluding the leading term which is always 1.
		# For example the polynomial x^3 + 255x^2 + 8x + 93 is stored as the uint8 array [255, 8, 93].
		result = bytearray([0] * (degree - 1) + [1])  # Start off with the monomial x^0
		
		# Compute the product polynomial (x - r^0) * (x - r^1) * (x - r^2) * ... * (x - r^{degree-1}),
		# and drop the highest monomial term which is always 1x^degree.
		# Note that r = 0x02, which is a generator element of this field GF(2^8/0x11D).
		root: int = 1
		for _ in range(degree):  # Unused variable i
			# Multiply the current product by (x - r^i)
			for j in range(degree):
				result[j] = QrCode._reed_solomon_multiply(result[j], root)
				if j + 1 < degree:
					result[j] ^= result[j + 1]
			root = QrCode._reed_solomon_multiply(root, 0x02)
		return result
	
	
	@staticmethod
	def _reed_solomon_compute_remainder(data: bytes, divisor: bytes) -> bytes:
		"""Returns the Reed-Solomon error correction codeword for the given data and divisor polynomials."""
		result = bytearray([0] * len(divisor))
		for b in data:  # Polynomial division
			factor: int = b ^ result.pop(0)
			result.append(0)
			for (i, coef) in enumerate(divisor):
				result[i] ^= QrCode._reed_solomon_multiply(coef, factor)
		return result
	
	
	@staticmethod
	def _reed_solomon_multiply(x: int, y: int) -> int:
		"""Returns the product of the two given field elements modulo GF(2^8/0x11D). The arguments and result
		are unsigned 8-bit integers. This could be implemented as a lookup table of 256*256 entries of uint8."""
		if (x >> 8 != 0) or (y >> 8 != 0):
			raise ValueError("Byte out of range")
		# Russian peasant multiplication
		z: int = 0
		for i in reversed(range(8)):
			z = (z << 1) ^ ((z >> 7) * 0x11D)
			z ^= ((y >> i) & 1) * x
		assert z >> 8 == 0
		return z
	
	
	def _finder_penalty_count_patterns(self, runhistory: collections.deque[int]) -> int:
		"""Can only be called immediately after a light run is added, and
		returns either 0, 1, or 2. A helper function for _get_penalty_score()."""
		n: int = runhistory[1]
		assert n <= self._size * 3
		core: bool = n > 0 and (runhistory[2] == runhistory[4] == runhistory[5] == n) and runhistory[3] == n * 3
		return (1 if (core and runhistory[0] >= n * 4 and runhistory[6] >= n) else 0) \
		     + (1 if (core and runhistory[6] >= n * 4 and runhistory[0] >= n) else 0)
	
	
	def _finder_penalty_terminate_and_count(self, currentruncolor: bool, currentrunlength: int, runhistory: collections.deque[int]) -> int:
		"""Must be called at the end of a line (row or column) of modules. A helper function for _get_penalty_score()."""
		if currentruncolor:  # Terminate dark run
			self._finder_penalty_add_history(currentrunlength, runhistory)
			currentrunlength = 0
		currentrunlength += self._size  # Add light border to final run
		self._finder_penalty_add_history(currentrunlength, runhistory)
		return self._finder_penalty_count_patterns(runhistory)
	
	
	def _finder_penalty_add_history(self, currentrunlength: int, runhistory: collections.deque[int]) -> None:
		if runhistory[0] == 0:
			currentrunlength += self._size  # Add light border to initial run
		runhistory.appendleft(currentrunlength)
	
	
	# ---- Constants and tables ----
	
	MIN_VERSION: int =  1  # The minimum version number supported in the QR Code Model 2 standard
	MAX_VERSION: int = 40  # The maximum version number supported in the QR Code Model 2 standard
	
	# For use in _get_penalty_score(), when evaluating which mask is best.
	_PENALTY_N1: int =  3
	_PENALTY_N2: int =  3
	_PENALTY_N3: int = 40
	_PENALTY_N4: int = 10
	
	_ECC_CODEWORDS_PER_BLOCK: Sequence[Sequence[int]] = (
		# Version: (note that index 0 is for padding, and is set to an illegal value)
		# 0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
		(-1,  7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),  # Low
		(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28),  # Medium
		(-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),  # Quartile
		(-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30))  # High
	
	_NUM_ERROR_CORRECTION_BLOCKS: Sequence[Sequence[int]] = (
		# Version: (note that index 0 is for padding, and is set to an illegal value)
		# 0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
		(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4,  4,  4,  4,  4,  6,  6,  6,  6,  7,  8,  8,  9,  9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25),  # Low
		(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5,  5,  8,  9,  9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49),  # Medium
		(-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8,  8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68),  # Quartile
		(-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81))  # High
	
	_MASK_PATTERNS: Sequence[collections.abc.Callable[[int,int],int]] = (
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
	
	class Ecc:
		ordinal: int  # (Public) In the range 0 to 3 (unsigned 2-bit integer)
		formatbits: int  # (Package-private) In the range 0 to 3 (unsigned 2-bit integer)
		
		"""The error correction level in a QR Code symbol. Immutable."""
		# Private constructor
		def __init__(self, i: int, fb: int) -> None:
			self.ordinal = i
			self.formatbits = fb
		
		# Placeholders
		LOW     : QrCode.Ecc
		MEDIUM  : QrCode.Ecc
		QUARTILE: QrCode.Ecc
		HIGH    : QrCode.Ecc
	
	# Public constants. Create them outside the class.
	Ecc.LOW      = Ecc(0, 1)  # The QR Code can tolerate about  7% erroneous codewords
	Ecc.MEDIUM   = Ecc(1, 0)  # The QR Code can tolerate about 15% erroneous codewords
	Ecc.QUARTILE = Ecc(2, 3)  # The QR Code can tolerate about 25% erroneous codewords
	Ecc.HIGH     = Ecc(3, 2)  # The QR Code can tolerate about 30% erroneous codewords



# ---- Data segment class ----

class QrSegment:
	"""A segment of character/binary/control data in a QR Code symbol.
	Instances of this class are immutable.
	The mid-level way to create a segment is to take the payload data
	and call a static factory function such as QrSegment.make_numeric().
	The low-level way to create a segment is to custom-make the bit buffer
	and call the QrSegment() constructor with appropriate values.
	This segment class imposes no length restrictions, but QR Codes have restrictions.
	Even in the most favorable conditions, a QR Code can only hold 7089 characters of data.
	Any segment longer than this is meaningless for the purpose of generating QR Codes."""
	
	# ---- Static factory functions (mid level) ----
	
	@staticmethod
	def make_bytes(data: bytes|Sequence[int]) -> QrSegment:
		"""Returns a segment representing the given binary data encoded in byte mode.
		All input byte lists are acceptable. Any text string can be converted to
		UTF-8 bytes (s.encode("UTF-8")) and encoded as a byte mode segment."""
		bb = _BitBuffer()
		for b in data:
			bb.append_bits(b, 8)
		return QrSegment(QrSegment.Mode.BYTE, len(data), bb)
	
	
	@staticmethod
	def make_numeric(digits: str) -> QrSegment:
		"""Returns a segment representing the given string of decimal digits encoded in numeric mode."""
		if not QrSegment.is_numeric(digits):
			raise ValueError("String contains non-numeric characters")
		bb = _BitBuffer()
		i: int = 0
		while i < len(digits):  # Consume up to 3 digits per iteration
			n: int = min(len(digits) - i, 3)
			bb.append_bits(int(digits[i : i + n]), n * 3 + 1)
			i += n
		return QrSegment(QrSegment.Mode.NUMERIC, len(digits), bb)
	
	
	@staticmethod
	def make_alphanumeric(text: str) -> QrSegment:
		"""Returns a segment representing the given text string encoded in alphanumeric mode.
		The characters allowed are: 0 to 9, A to Z (uppercase only), space,
		dollar, percent, asterisk, plus, hyphen, period, slash, colon."""
		if not QrSegment.is_alphanumeric(text):
			raise ValueError("String contains unencodable characters in alphanumeric mode")
		bb = _BitBuffer()
		for i in range(0, len(text) - 1, 2):  # Process groups of 2
			temp: int = QrSegment._ALPHANUMERIC_ENCODING_TABLE[text[i]] * 45
			temp += QrSegment._ALPHANUMERIC_ENCODING_TABLE[text[i + 1]]
			bb.append_bits(temp, 11)
		if len(text) % 2 > 0:  # 1 character remaining
			bb.append_bits(QrSegment._ALPHANUMERIC_ENCODING_TABLE[text[-1]], 6)
		return QrSegment(QrSegment.Mode.ALPHANUMERIC, len(text), bb)
	
	
	@staticmethod
	def make_segments(text: str) -> list[QrSegment]:
		"""Returns a new mutable list of zero or more segments to represent the given Unicode text string.
		The result may use various segment modes and switch modes to optimize the length of the bit stream."""
		
		# Select the most efficient segment encoding automatically
		if text == "":
			return []
		elif QrSegment.is_numeric(text):
			return [QrSegment.make_numeric(text)]
		elif QrSegment.is_alphanumeric(text):
			return [QrSegment.make_alphanumeric(text)]
		else:
			return [QrSegment.make_bytes(text.encode("UTF-8"))]
	
	
	@staticmethod
	def make_eci(assignval: int) -> QrSegment:
		"""Returns a segment representing an Extended Channel Interpretation
		(ECI) designator with the given assignment value."""
		bb = _BitBuffer()
		if assignval < 0:
			raise ValueError("ECI assignment value out of range")
		elif assignval < (1 << 7):
			bb.append_bits(assignval, 8)
		elif assignval < (1 << 14):
			bb.append_bits(0b10, 2)
			bb.append_bits(assignval, 14)
		elif assignval < 1000000:
			bb.append_bits(0b110, 3)
			bb.append_bits(assignval, 21)
		else:
			raise ValueError("ECI assignment value out of range")
		return QrSegment(QrSegment.Mode.ECI, 0, bb)
	
	
	# Tests whether the given string can be encoded as a segment in numeric mode.
	# A string is encodable iff each character is in the range 0 to 9.
	@staticmethod
	def is_numeric(text: str) -> bool:
		return QrSegment._NUMERIC_REGEX.fullmatch(text) is not None
	
	
	# Tests whether the given string can be encoded as a segment in alphanumeric mode.
	# A string is encodable iff each character is in the following set: 0 to 9, A to Z
	# (uppercase only), space, dollar, percent, asterisk, plus, hyphen, period, slash, colon.
	@staticmethod
	def is_alphanumeric(text: str) -> bool:
		return QrSegment._ALPHANUMERIC_REGEX.fullmatch(text) is not None
	
	
	# ---- Private fields ----
	
	# The mode indicator of this segment. Accessed through get_mode().
	_mode: QrSegment.Mode
	
	# The length of this segment's unencoded data. Measured in characters for
	# numeric/alphanumeric/kanji mode, bytes for byte mode, and 0 for ECI mode.
	# Always zero or positive. Not the same as the data's bit length.
	# Accessed through get_num_chars().
	_numchars: int
	
	# The data bits of this segment. Accessed through get_data().
	_bitdata: list[int]
	
	
	# ---- Constructor (low level) ----
	
	def __init__(self, mode: QrSegment.Mode, numch: int, bitdata: Sequence[int]) -> None:
		"""Creates a new QR Code segment with the given attributes and data.
		The character count (numch) must agree with the mode and the bit buffer length,
		but the constraint isn't checked. The given bit buffer is cloned and stored."""
		if numch < 0:
			raise ValueError()
		self._mode = mode
		self._numchars = numch
		self._bitdata = list(bitdata)  # Make defensive copy
	
	
	# ---- Accessor methods ----
	
	def get_mode(self) -> QrSegment.Mode:
		"""Returns the mode field of this segment."""
		return self._mode
	
	def get_num_chars(self) -> int:
		"""Returns the character count field of this segment."""
		return self._numchars
	
	def get_data(self) -> list[int]:
		"""Returns a new copy of the data bits of this segment."""
		return list(self._bitdata)  # Make defensive copy
	
	
	# Package-private function
	@staticmethod
	def get_total_bits(segs: Sequence[QrSegment], version: int) -> int|None:
		"""Calculates the number of bits needed to encode the given segments at
		the given version. Returns a non-negative number if successful. Otherwise
		returns None if a segment has too many characters to fit its length field."""
		result = 0
		for seg in segs:
			ccbits: int = seg.get_mode().num_char_count_bits(version)
			if seg.get_num_chars() >= (1 << ccbits):
				return None  # The segment's length doesn't fit the field's bit width
			result += 4 + ccbits + len(seg._bitdata)
		return result
	
	
	# ---- Constants ----
	
	# Describes precisely all strings that are encodable in numeric mode.
	_NUMERIC_REGEX: re.Pattern[str] = re.compile(r"[0-9]*")
	
	# Describes precisely all strings that are encodable in alphanumeric mode.
	_ALPHANUMERIC_REGEX: re.Pattern[str] = re.compile(r"[A-Z0-9 $%*+./:-]*")
	
	# Dictionary of "0"->0, "A"->10, "$"->37, etc.
	_ALPHANUMERIC_ENCODING_TABLE: dict[str,int] = {ch: i for (i, ch) in enumerate("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:")}
	
	
	# ---- Public helper enumeration ----
	
	class Mode:
		"""Describes how a segment's data bits are interpreted. Immutable."""
		
		_modebits: int  # The mode indicator bits, which is a uint4 value (range 0 to 15)
		_charcounts: tuple[int,int,int]  # Number of character count bits for three different version ranges
		
		# Private constructor
		def __init__(self, modebits: int, charcounts: tuple[int,int,int]):
			self._modebits = modebits
			self._charcounts = charcounts
		
		# Package-private method
		def get_mode_bits(self) -> int:
			"""Returns an unsigned 4-bit integer value (range 0 to 15) representing the mode indicator bits for this mode object."""
			return self._modebits
		
		# Package-private method
		def num_char_count_bits(self, ver: int) -> int:
			"""Returns the bit width of the character count field for a segment in this mode
			in a QR Code at the given version number. The result is in the range [0, 16]."""
			return self._charcounts[(ver + 7) // 17]
		
		# Placeholders
		NUMERIC     : QrSegment.Mode
		ALPHANUMERIC: QrSegment.Mode
		BYTE        : QrSegment.Mode
		KANJI       : QrSegment.Mode
		ECI         : QrSegment.Mode
	
	# Public constants. Create them outside the class.
	Mode.NUMERIC      = Mode(0x1, (10, 12, 14))
	Mode.ALPHANUMERIC = Mode(0x2, ( 9, 11, 13))
	Mode.BYTE         = Mode(0x4, ( 8, 16, 16))
	Mode.KANJI        = Mode(0x8, ( 8, 10, 12))
	Mode.ECI          = Mode(0x7, ( 0,  0,  0))



class QrSegmentAdvanced:
	
	"""Splits text into optimal segments and encodes kanji segments.
	Provides static functions only; not instantiable."""
	
	@staticmethod
	def make_segments_optimally(text: str, ecl: QrCode.Ecc, minversion: int, maxversion: int) -> list[QrSegment]:
		"""Returns a list of zero or more segments to represent the given Unicode text string.
		The resulting list optimally minimizes the total encoded bit length, subjected to the
		constraints in the given {error correction level, minimum version number, maximum version number}.
		
		This function can utilize all four text encoding modes: numeric, alphanumeric,
		byte (UTF-8), and kanji. This can be considered as a sophisticated but slower
		replacement for QrSegment.make_segments(). This requires more input parameters
		because it searches a range of versions, like QrCode.encode_segments()."""
		
		# Check arguments
		if not (QrCode.MIN_VERSION <= minversion <= maxversion <= QrCode.MAX_VERSION):
			raise ValueError("Invalid value")
		
		# Iterate through version numbers, and make tentative segments
		for version in itertools.count(minversion):
			if version in (minversion, 10, 27):
				segs: list[QrSegment] = QrSegmentAdvanced._make_segments_optimally(text, version)
			assert segs is not None
			
			# Check if the segments fit
			datacapacitybits: int = QrCode._get_num_data_codewords(version, ecl) * 8  # Number of data bits available
			datausedbits: int|None = QrSegment.get_total_bits(segs, version)
			if (datausedbits is not None) and (datausedbits <= datacapacitybits):
				return segs  # This version number is found to be suitable
			if version >= maxversion:  # All versions in the range could not fit the given text
				msg: str = "Segment too long"
				if datausedbits is not None:
					msg = f"Data length = {datausedbits} bits, Max capacity = {datacapacitybits} bits"
				raise DataTooLongError(msg)
		raise AssertionError()
	
	
	# Returns a new list of segments that is optimal for the given text at the given version number.
	@staticmethod
	def _make_segments_optimally(codepoints: str, version: int) -> list[QrSegment]:
		if len(codepoints) == 0:
			return []
		if len(codepoints) > 7089:  # Upper bound is the number of characters that fit in QR Code version 40, low error correction, numeric mode
			raise DataTooLongError("String too long")
		modetypes: set[QrSegment.Mode] = {QrSegment.Mode.BYTE, QrSegment.Mode.ALPHANUMERIC, QrSegment.Mode.NUMERIC, QrSegment.Mode.KANJI}
		
		# Segment header sizes, measured in 1/6 bits
		headcosts: dict[QrSegment.Mode,int] = {mode: (4 + mode.num_char_count_bits(version)) * 6 for mode in modetypes}
		assert all((0 <= cost <= (4 + 16) * 6) for cost in headcosts.values())
		
		# charmodes[i][m] is the mode to encode the code point at index i such that the initial
		# segment (possibly empty) uses m and the total number of bits to encode
		# the suffix codepoints[i : len(codepoints)] is minimized over all possible choices
		charmodes: list[dict[QrSegment.Mode,QrSegment.Mode]] = []
		
		# At the beginning of each iteration of the loop below, rightcosts[j] is the exact
		# minimum number of 1/6 bits needed to encode the entire suffix codepoints[i + 1 :
		# len(codepoints)] such that the initial segment (possibly empty) uses modetypes[j]
		rightcosts: dict[QrSegment.Mode,int] = dict(headcosts)
		
		# Calculate costs using dynamic programming
		for c in reversed(codepoints):
			cmodes: dict[QrSegment.Mode,QrSegment.Mode] = {}
			leftcosts: dict[QrSegment.Mode,int] = {}
			for rightmode in modetypes:
				basecost: int = rightcosts[rightmode]
				if rightmode == QrSegment.Mode.BYTE:  # Always extend a byte mode segment
					basecost += len(c.encode("UTF-8")) * 8 * 6
				elif (rightmode == QrSegment.Mode.ALPHANUMERIC) and QrSegment.is_alphanumeric(c):
					basecost += 33  # 5.5 bits per alphanumeric char
				elif (rightmode == QrSegment.Mode.NUMERIC) and QrSegment.is_numeric(c):
					basecost += 20  # 3.33 bits per digit
				elif (rightmode == QrSegment.Mode.KANJI) and QrSegmentAdvanced._is_kanji(c):
					basecost += 13 * 6  # 13 bits per Shift JIS char
				else:  # Cannot extend the segment to include the current character
					continue
				# Account for the cost of possibly starting a new segment before this character
				for leftmode in modetypes:
					cost: int = basecost
					if leftmode != rightmode:  # Round up to whole bit and append new initially empty segment
						cost = (cost + 5) // 6 * 6 + headcosts[leftmode]
					if (leftmode not in cmodes) or (cost < leftcosts[leftmode]):
						leftcosts[leftmode] = cost
						cmodes[leftmode] = rightmode
			
			# A non-tight upper bound is when each of 7089 characters switches to
			# byte mode (4-bit header + 16-bit count) and requires 4 bytes in UTF-8
			assert((0 <= cost <= (4 + 16 + 4 * 8) * 6 * 7089) for cost in leftcosts.values())
			charmodes.append(cmodes)
			rightcosts = leftcosts
		charmodes.reverse()
		
		# Find a starting mode that minimizes the total bit length
		prevmode: QrSegment.Mode = min(modetypes, key=(lambda mode: rightcosts[mode]))
		
		# Trace the chain of modes forward, accumulate runs, make segments
		result: list[QrSegment] = []
		runstart: int = 0
		for i in itertools.count(1):
			curmode: QrSegment.Mode|None = charmodes[i][prevmode] if (i < len(codepoints)) else None
			if (i >= len(codepoints)) or (curmode != prevmode):
				s: str = codepoints[runstart : i]
				match prevmode:
					case QrSegment.Mode.BYTE:
						result.append(QrSegment.make_bytes(s.encode("UTF-8")))
					case QrSegment.Mode.NUMERIC:
						result.append(QrSegment.make_numeric(s))
					case QrSegment.Mode.ALPHANUMERIC:
						result.append(QrSegment.make_alphanumeric(s))
					case QrSegment.Mode.KANJI:
						result.append(QrSegmentAdvanced.make_kanji(s))
					case _:
						raise AssertionError()
				if i >= len(codepoints):
					break
				if curmode is None:
					raise AssertionError()
				prevmode = curmode
				runstart = i
		return result
	
	
	@staticmethod
	def make_kanji(text: str) -> QrSegment:
		"""Returns a segment representing the given text string encoded in kanji mode.
		Broadly speaking, the set of encodable characters are {kanji used in Japan,
		hiragana, katakana, East Asian punctuation, full-width ASCII, Greek, Cyrillic}.
		Examples of non-encodable characters include {ordinary ASCII, half-width katakana,
		more extensive Chinese hanzi}."""
		bb = _BitBuffer()
		for c in text:
			if c not in QrSegmentAdvanced._UNICODE_TO_QR_KANJI:
				raise ValueError("String contains non-kanji-mode characters");
			bb.append_bits(QrSegmentAdvanced._UNICODE_TO_QR_KANJI[c], 13)
		return QrSegment(QrSegment.Mode.KANJI, len(text), bb)
	
	
	@staticmethod
	def _is_kanji(text: str) -> bool:
		return all(c in QrSegmentAdvanced._UNICODE_TO_QR_KANJI for c in text)
	
	
	# Data derived from ftp://ftp.unicode.org/Public/MAPPINGS/OBSOLETE/EASTASIA/JIS/SHIFTJIS.TXT
	_PACKED_QR_KANJI_TO_UNICODE: str = """
		MAAwATAC/wz/DjD7/xr/G/8f/wEwmzCcALT/QACo/z7/4/8/MP0w/jCdMJ4wA07dMAUwBjAHMPwgFSAQ/w8AXDAcIBb/XCAmICUgGCAZIBwgHf8I/wkwFDAV/zv/Pf9b/10wCDAJMAowCzAMMA0wDjAPMBAwEf8LIhIAsQDX//8A9/8dImD/HP8eImYiZyIeIjQmQiZA
		ALAgMiAzIQP/5f8EAKIAo/8F/wP/Bv8K/yAApyYGJgUlyyXPJc4lxyXGJaEloCWzJbIlvSW8IDswEiGSIZAhkSGTMBP/////////////////////////////IggiCyKGIocigiKDIioiKf////////////////////8iJyIoAKwh0iHUIgAiA///////////////////
		//////////8iICKlIxIiAiIHImEiUiJqImsiGiI9Ih0iNSIrIiz//////////////////yErIDAmbyZtJmogICAhALb//////////yXv/////////////////////////////////////////////////xD/Ef8S/xP/FP8V/xb/F/8Y/xn///////////////////8h
		/yL/I/8k/yX/Jv8n/yj/Kf8q/yv/LP8t/y7/L/8w/zH/Mv8z/zT/Nf82/zf/OP85/zr///////////////////9B/0L/Q/9E/0X/Rv9H/0j/Sf9K/0v/TP9N/07/T/9Q/1H/Uv9T/1T/Vf9W/1f/WP9Z/1r//////////zBBMEIwQzBEMEUwRjBHMEgwSTBKMEswTDBN
		ME4wTzBQMFEwUjBTMFQwVTBWMFcwWDBZMFowWzBcMF0wXjBfMGAwYTBiMGMwZDBlMGYwZzBoMGkwajBrMGwwbTBuMG8wcDBxMHIwczB0MHUwdjB3MHgweTB6MHswfDB9MH4wfzCAMIEwgjCDMIQwhTCGMIcwiDCJMIowizCMMI0wjjCPMJAwkTCSMJP/////////////
		////////////////////////MKEwojCjMKQwpTCmMKcwqDCpMKowqzCsMK0wrjCvMLAwsTCyMLMwtDC1MLYwtzC4MLkwujC7MLwwvTC+ML8wwDDBMMIwwzDEMMUwxjDHMMgwyTDKMMswzDDNMM4wzzDQMNEw0jDTMNQw1TDWMNcw2DDZMNow2zDcMN0w3jDf//8w4DDh
		MOIw4zDkMOUw5jDnMOgw6TDqMOsw7DDtMO4w7zDwMPEw8jDzMPQw9TD2/////////////////////wORA5IDkwOUA5UDlgOXA5gDmQOaA5sDnAOdA54DnwOgA6EDowOkA6UDpgOnA6gDqf////////////////////8DsQOyA7MDtAO1A7YDtwO4A7kDugO7A7wDvQO+
		A78DwAPBA8MDxAPFA8YDxwPIA8n/////////////////////////////////////////////////////////////////////////////////////////////////////////////BBAEEQQSBBMEFAQVBAEEFgQXBBgEGQQaBBsEHAQdBB4EHwQgBCEEIgQjBCQEJQQm
		BCcEKAQpBCoEKwQsBC0ELgQv////////////////////////////////////////BDAEMQQyBDMENAQ1BFEENgQ3BDgEOQQ6BDsEPAQ9//8EPgQ/BEAEQQRCBEMERARFBEYERwRIBEkESgRLBEwETQROBE///////////////////////////////////yUAJQIlDCUQ
		JRglFCUcJSwlJCU0JTwlASUDJQ8lEyUbJRclIyUzJSslOyVLJSAlLyUoJTclPyUdJTAlJSU4JUL/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		/////////////////////////////////////06cVRZaA5Y/VMBhG2MoWfaQIoR1gxx6UGCqY+FuJWXthGaCppv1aJNXJ2WhYnFbm1nQhnuY9H1ifb6bjmIWfJ+It1uJXrVjCWaXaEiVx5eNZ09O5U8KT01PnVBJVvJZN1nUWgFcCWDfYQ9hcGYTaQVwunVPdXB5+32t
		fe+Aw4QOiGOLApBVkHpTO06VTqVX34CykMF4704AWPFuopA4ejKDKIKLnC9RQVNwVL1U4VbgWftfFZjybeuA5IUt////////lmKWcJagl/tUC1PzW4dwz3+9j8KW6FNvnVx6uk4ReJOB/G4mVhhVBGsdhRqcO1nlU6ltZnTclY9WQk6RkEuW8oNPmQxT4VW2WzBfcWYg
		ZvNoBGw4bPNtKXRbdsh6Tpg0gvGIW4pgku1tsnWrdsqZxWCmiwGNipWyaY5TrVGG//9XElgwWURbtF72YChjqWP0bL9vFHCOcRRxWXHVcz9+AYJ2gtGFl5BgkludG1hpZbxsWnUlUflZLlllX4Bf3GK8ZfpqKmsna7Rzi3/BiVadLJ0OnsRcoWyWg3tRBFxLYbaBxmh2
		cmFOWU/6U3hgaW4pek+X804LUxZO7k9VTz1PoU9zUqBT71YJWQ9awVu2W+F50WaHZ5xntmtMbLNwa3PCeY15vno8e4eCsYLbgwSDd4Pvg9OHZoqyVimMqI/mkE6XHoaKT8Rc6GIRcll1O4Hlgr2G/ozAlsWZE5nVTstPGonjVt5YSljKXvtf62AqYJRgYmHQYhJi0GU5
		////////m0FmZmiwbXdwcHVMdoZ9dYKlh/mVi5aOjJ1R8VK+WRZUs1uzXRZhaGmCba94jYTLiFeKcpOnmrhtbJmohtlXo2f/hs6SDlKDVodUBF7TYuFkuWg8aDhru3NyeLp6a4maidKNa48DkO2Vo5aUl2lbZlyzaX2YTZhOY5t7IGor//9qf2i2nA1vX1JyVZ1gcGLs
		bTtuB27RhFuJEI9EThScOVP2aRtqOpeEaCpRXHrDhLKR3JOMVludKGgigwWEMXylUgiCxXTmTn5Pg1GgW9JSClLYUudd+1WaWCpZ5luMW5hb215yXnlgo2EfYWNhvmPbZWJn0WhTaPprPmtTbFdvIm+Xb0V0sHUYduN3C3r/e6F8IX3pfzZ/8ICdgmaDnomzisyMq5CE
		lFGVk5WRlaKWZZfTmSiCGE44VCtcuF3Mc6l2THc8XKl/640LlsGYEZhUmFhPAU8OU3FVnFZoV/pZR1sJW8RckF4MXn5fzGPuZzpl12XiZx9oy2jE////////al9eMGvFbBdsfXV/eUhbY3oAfQBfvYmPihiMtI13jsyPHZjimg6bPE6AUH1RAFmTW5xiL2KAZOxrOnKg
		dZF5R3+ph/uKvItwY6yDypegVAlUA1WraFRqWIpweCdndZ7NU3RbooEahlCQBk4YTkVOx08RU8pUOFuuXxNgJWVR//9nPWxCbHJs43B4dAN6dnquewh9Gnz+fWZl53JbU7tcRV3oYtJi4GMZbiCGWooxjd2S+G8BeaabWk6oTqtOrE+bT6BQ0VFHevZRcVH2U1RTIVN/
		U+tVrFiDXOFfN19KYC9gUGBtYx9lWWpLbMFywnLtd++A+IEFggiFTpD3k+GX/5lXmlpO8FHdXC1mgWltXEBm8ml1c4loUHyBUMVS5FdHXf6TJmWkayNrPXQ0eYF5vXtLfcqCuYPMiH+JX4s5j9GR0VQfkoBOXVA2U+VTOnLXc5Z36YLmjq+ZxpnImdJRd2Eahl5VsHp6
		UHZb05BHloVOMmrbkedcUVxI////////Y5h6n2yTl3SPYXqqcYqWiHyCaBd+cGhRk2xS8lQbhauKE3+kjs2Q4VNmiIh5QU/CUL5SEVFEVVNXLXPqV4tZUV9iX4RgdWF2YWdhqWOyZDplbGZvaEJuE3Vmej18+31MfZl+S39rgw6DSobNigiKY4tmjv2YGp2PgriPzpvo
		//9Sh2IfZINvwJaZaEFQkWsgbHpvVHp0fVCIQIojZwhO9lA5UCZQZVF8UjhSY1WnVw9YBVrMXvphsmH4YvNjcmkcailyfXKscy54FHhvfXl3DICpiYuLGYzijtKQY5N1lnqYVZoTnnhRQ1OfU7Nee18mbhtukHOEc/59Q4I3igCK+pZQTk5QC1PkVHxW+lnRW2Rd8V6r
		XydiOGVFZ69uVnLQfMqItIChgOGD8IZOioeN6JI3lseYZ58TTpROkk8NU0hUSVQ+Wi9fjF+hYJ9op2qOdFp4gYqeiqSLd5GQTl6byU6kT3xPr1AZUBZRSVFsUp9SuVL+U5pT41QR////////VA5ViVdRV6JZfVtUW11bj13lXedd9154XoNeml63XxhgUmFMYpdi2GOn
		ZTtmAmZDZvRnbWghaJdpy2xfbSptaW4vbp11MnaHeGx6P3zgfQV9GH1efbGAFYADgK+AsYFUgY+CKoNSiEyIYYsbjKKM/JDKkXWScXg/kvyVpJZN//+YBZmZmtidO1JbUqtT91QIWNVi92/gjGqPX565UUtSO1RKVv16QJF3nWCe0nNEbwmBcHURX/1g2pqoctuPvGtk
		mANOylbwV2RYvlpaYGhhx2YPZgZoOWixbfd11X06gm6bQk6bT1BTyVUGXW9d5l3uZ/tsmXRzeAKKUJOWiN9XUF6nYytQtVCsUY1nAFTJWF5Zu1uwX2liTWOhaD1rc24IcH2Rx3KAeBV4JnltZY59MIPciMGPCZabUmRXKGdQf2qMoVG0V0KWKlg6aYqAtFSyXQ5X/HiV
		nfpPXFJKVItkPmYoZxRn9XqEe1Z9IpMvaFybrXs5UxlRilI3////////W99i9mSuZOZnLWu6hamW0XaQm9ZjTJMGm6t2v2ZSTglQmFPCXHFg6GSSZWNoX3Hmc8p1I3uXfoKGlYuDjNuReJkQZaxmq2uLTtVO1E86T39SOlP4U/JV41bbWOtZy1nJWf9bUFxNXgJeK1/X
		YB1jB2UvW1xlr2W9ZehnnWti//9re2wPc0V5SXnBfPh9GX0rgKKBAoHziZaKXoppimaKjIrujMeM3JbMmPxrb06LTzxPjVFQW1db+mFIYwFmQmshbstsu3I+dL111HjBeTqADIAzgeqElI+ebFCef18Pi1idK3r6jvhbjZbrTgNT8Vf3WTFayVukYIluf28Gdb6M6luf
		hQB74FByZ/SCnVxhhUp+HoIOUZlcBGNojWZlnHFueT59F4AFix2OypBuhseQqlAfUvpcOmdTcHxyNZFMkciTK4LlW8JfMWD5TjtT1luIYktnMWuKculz4HougWuNo5FSmZZRElPXVGpb/2OIajl9rJcAVtpTzlRo////////W5dcMV3eT+5hAWL+bTJ5wHnLfUJ+TX/S
		ge2CH4SQiEaJcouQjnSPL5AxkUuRbJbGkZxOwE9PUUVTQV+TYg5n1GxBbgtzY34mkc2Sg1PUWRlbv23ReV1+LnybWH5xn1H6iFOP8E/KXPtmJXeseuOCHJn/UcZfqmXsaW9riW3z//9ulm9kdv59FF3hkHWRh5gGUeZSHWJAZpFm2W4aXrZ90n9yZviFr4X3ivhSqVPZ
		WXNej1+QYFWS5JZkULdRH1LdUyBTR1PsVOhVRlUxVhdZaFm+WjxbtVwGXA9cEVwaXoReil7gX3Bif2KEYttjjGN3ZgdmDGYtZnZnfmiiah9qNWy8bYhuCW5YcTxxJnFndcd3AXhdeQF5ZXnweuB7EXynfTmAloPWhIuFSYhdiPOKH4o8ilSKc4xhjN6RpJJmk36UGJac
		l5hOCk4ITh5OV1GXUnBXzlg0WMxbIl44YMVk/mdhZ1ZtRHK2dXN6Y4S4i3KRuJMgVjFX9Jj+////////Yu1pDWuWce1+VIB3gnKJ5pjfh1WPsVw7TzhP4U+1VQdaIFvdW+lfw2FOYy9lsGZLaO5pm214bfF1M3W5dx95XnnmfTOB44KvhaqJqoo6jquPm5Aykd2XB066
		TsFSA1h1WOxcC3UaXD2BTooKj8WWY5dteyWKz5gIkWJW81Oo//+QF1Q5V4JeJWOobDRwindhfIt/4IhwkEKRVJMQkxiWj3RemsRdB11pZXBnoo2olttjbmdJaRmDxZgXlsCI/m+EZHpb+E4WcCx1XWYvUcRSNlLiWdNfgWAnYhBlP2V0Zh9mdGjyaBZrY24FcnJ1H3bb
		fL6AVljwiP2Jf4qgipOKy5AdkZKXUpdZZYl6DoEGlrteLWDcYhplpWYUZ5B383pNfE1+PoEKjKyNZI3hjl94qVIHYtljpWRCYpiKLXqDe8CKrJbqfXaCDIdJTtlRSFNDU2Bbo1wCXBZd3WImYkdksGgTaDRsyW1FbRdn029ccU5xfWXLen97rX3a////////fkp/qIF6
		ghuCOYWmim6Mzo31kHiQd5KtkpGVg5uuUk1VhG84cTZRaHmFflWBs3zOVkxYUVyoY6pm/mb9aVpy2XWPdY55DnlWed98l30gfUSGB4o0ljuQYZ8gUOdSdVPMU+JQCVWqWO5ZT3I9W4tcZFMdYONg82NcY4NjP2O7//9kzWXpZvld42nNaf1vFXHlTol16Xb4epN8333P
		fZyAYYNJg1iEbIS8hfuIxY1wkAGQbZOXlxyaElDPWJdhjoHThTWNCJAgT8NQdFJHU3Ngb2NJZ19uLI2zkB9P11xejMplz32aU1KIllF2Y8NbWFtrXApkDWdRkFxO1lkaWSpscIpRVT5YFVmlYPBiU2fBgjVpVZZAmcSaKE9TWAZb/oAQXLFeL1+FYCBhS2I0Zv9s8G7e
		gM6Bf4LUiIuMuJAAkC6Wip7bm9tO41PwWSd7LJGNmEyd+W7dcCdTU1VEW4ViWGKeYtNsom/vdCKKF5Q4b8GK/oM4UeeG+FPq////////U+lPRpBUj7BZaoExXf166o+/aNqMN3L4nEhqPYqwTjlTWFYGV2ZixWOiZeZrTm3hbltwrXfteu97qn27gD2AxobLipWTW1bj
		WMdfPmWtZpZqgGu1dTeKx1Akd+VXMF8bYGVmemxgdfR6Gn9ugfSHGJBFmbN7yXVcevl7UYTE//+QEHnpepKDNlrhd0BOLU7yW5lf4GK9Zjxn8WzohmuId4o7kU6S85nQahdwJnMqgueEV4yvTgFRRlHLVYtb9V4WXjNegV8UXzVfa1+0YfJjEWaiZx1vbnJSdTp3OoB0
		gTmBeId2ir+K3I2FjfOSmpV3mAKc5VLFY1d29GcVbIhzzYzDk66Wc20lWJxpDmnMj/2TmnXbkBpYWmgCY7Rp+09Dbyxn2I+7hSZ9tJNUaT9vcFdqWPdbLH0scipUCpHjnbROrU9OUFxQdVJDjJ5USFgkW5peHV6VXq1e918fYIxitWM6Y9Bor2xAeId5jnoLfeCCR4oC
		iuaORJAT////////kLiRLZHYnw5s5WRYZOJldW70doR7G5Bpk9FuulTyX7lkpI9Nj+2SRFF4WGtZKVxVXpdt+36PdRyMvI7imFtwuU8da79vsXUwlvtRTlQQWDVYV1msXGBfkmWXZ1xuIXZ7g9+M7ZAUkP2TTXgleDpSql6mVx9ZdGASUBJRWlGs//9RzVIAVRBYVFhY
		WVdblVz2XYtgvGKVZC1ncWhDaLxo33bXbdhub22bcG9xyF9Tddh5d3tJe1R7UnzWfXFSMIRjhWmF5IoOiwSMRo4PkAOQD5QZlnaYLZowldhQzVLVVAxYAlwOYadknm0ed7N65YD0hASQU5KFXOCdB1M/X5dfs22ccnl3Y3m/e+Rr0nLsiq1oA2phUfh6gWk0XEqc9oLr
		W8WRSXAeVnhcb2DHZWZsjIxakEGYE1RRZseSDVlIkKNRhU5NUeqFmYsOcFhjepNLaWKZtH4EdXdTV2lgjt+W42xdToxcPF8Qj+lTAozRgImGeV7/ZeVOc1Fl////////WYJcP5fuTvtZil/Nio1v4XmweWJb54RxcytxsV50X/Vje2SaccN8mE5DXvxOS1fcVqJgqW/D
		fQ2A/YEzgb+PsomXhqRd9GKKZK2Jh2d3bOJtPnQ2eDRaRn91gq2ZrE/zXsNi3WOSZVdnb3bDckyAzIC6jymRTVANV/lakmiF//9pc3Fkcv2Mt1jyjOCWapAZh3955HfnhClPL1JlU1pizWfPbMp2fXuUfJWCNoWEj+tm3W8gcgZ+G4OrmcGeplH9e7F4cnu4gId7SGro
		XmGAjHVRdWBRa5Jibox2epGXmupPEH9wYpx7T5WlnOlWelhZhuSWvE80UiRTSlPNU9teBmQsZZFnf2w+bE5ySHKvc+11VH5BgiyF6Yype8SRxnFpmBKY72M9Zml1anbkeNCFQ4buUypTUVQmWYNeh198YLJiSWJ5YqtlkGvUbMx1snaueJF52H3Lf3eApYirirmMu5B/
		l16Y22oLfDhQmVw+X65nh2vYdDV3CX+O////////nztnynoXUzl1i5rtX2aBnYPxgJhfPF/FdWJ7RpA8aGdZ61qbfRB2fossT/VfamoZbDdvAnTieWiIaIpVjHle32PPdcV50oLXkyiS8oSchu2cLVTBX2xljG1ccBWMp4zTmDtlT3T2Tg1O2FfgWStaZlvMUaheA16c
		YBZidmV3//9lp2ZubW5yNnsmgVCBmoKZi1yMoIzmjXSWHJZET65kq2tmgh6EYYVqkOhcAWlTmKiEeoVXTw9Sb1+pXkVnDXmPgXmJB4mGbfVfF2JVbLhOz3Jpm5JSBlQ7VnRYs2GkYm5xGllufIl83n0blvBlh4BeThlPdVF1WEBeY15zXwpnxE4mhT2ViZZbfHOYAVD7
		WMF2VninUiV3pYURe4ZQT1kJckd7x33oj7qP1JBNT79SyVopXwGXrU/dgheS6lcDY1VraXUriNyPFHpCUt9Yk2FVYgpmrmvNfD+D6VAjT/hTBVRGWDFZSVudXPBc710pXpZisWNnZT5luWcL////////bNVs4XD5eDJ+K4DegrOEDITshwKJEooqjEqQppLSmP2c851s
		Tk9OoVCNUlZXSlmoXj1f2F/ZYj9mtGcbZ9Bo0lGSfSGAqoGoiwCMjIy/kn6WMlQgmCxTF1DVU1xYqGSyZzRyZ3dmekaR5lLDbKFrhlgAXkxZVGcsf/tR4XbG//9kaXjom1Seu1fLWblmJ2eaa85U6WnZXlWBnGeVm6pn/pxSaF1Opk/jU8hiuWcrbKuPxE+tfm2ev04H
		YWJugG8rhRNUc2cqm0Vd83uVXKxbxoccbkqE0XoUgQhZmXyNbBF3IFLZWSJxIXJfd9uXJ51haQtaf1oYUaVUDVR9Zg5234/3kpic9Fnqcl1uxVFNaMl9v33sl2KeumR4aiGDAlmEW19r23MbdvJ9soAXhJlRMmcontl27mdiUv+ZBVwkYjt8foywVU9gtn0LlYBTAU5f
		UbZZHHI6gDaRzl8ld+JThF95fQSFrIozjo2XVmfzha6UU2EJYQhsuXZS////////iu2POFUvT1FRKlLHU8tbpV59YKBhgmPWZwln2m5nbYxzNnM3dTF5UIjVipiQSpCRkPWWxIeNWRVOiE9ZTg6KiY8/mBBQrV58WZZbuV64Y9pj+mTBZtxpSmnYbQtutnGUdSh6r3+K
		gACESYTJiYGLIY4KkGWWfZkKYX5ikWsy//9sg210f8x//G3Af4WHuoj4Z2WDsZg8lvdtG31hhD2Rak5xU3VdUGsEb+uFzYYtiadSKVQPXGVnTmiodAZ0g3XiiM+I4ZHMluKWeF+Lc4d6y4ROY6B1ZVKJbUFunHQJdVl4a3ySloZ63J+NT7ZhbmXFhlxOhk6uUNpOIVHM
		W+5lmWiBbbxzH3ZCd616HHzngm+K0pB8kc+WdZgYUpt90VArU5hnl23LcdB0M4HojyqWo5xXnp90YFhBbZl9L5heTuRPNk+LUbdSsV26YBxzsnk8gtOSNJa3lvaXCp6Xn2Jmpmt0UhdSo3DIiMJeyWBLYZBvI3FJfD599IBv////////hO6QI5MsVEKbb2rTcImMwo3v
		lzJStFpBXspfBGcXaXxplG1qbw9yYnL8e+2AAYB+h0uQzlFtnpN5hICLkzKK1lAtVIyKcWtqjMSBB2DRZ6Cd8k6ZTpicEIprhcGFaGkAbn54l4FV////////////////////////////////////////////////////////////////////////////////////////
		/////////////////////////////18MThBOFU4qTjFONk48Tj9OQk5WTlhOgk6FjGtOioISXw1Ojk6eTp9OoE6iTrBOs062Ts5OzU7ETsZOwk7XTt5O7U7fTvdPCU9aTzBPW09dT1dPR092T4hPj0+YT3tPaU9wT5FPb0+GT5ZRGE/UT99Pzk/YT9tP0U/aT9BP5E/l
		UBpQKFAUUCpQJVAFTxxP9lAhUClQLE/+T+9QEVAGUENQR2cDUFVQUFBIUFpQVlBsUHhQgFCaUIVQtFCy////////UMlQylCzUMJQ1lDeUOVQ7VDjUO5Q+VD1UQlRAVECURZRFVEUURpRIVE6UTdRPFE7UT9RQFFSUUxRVFFievhRaVFqUW5RgFGCVthRjFGJUY9RkVGT
		UZVRllGkUaZRolGpUapRq1GzUbFRslGwUbVRvVHFUclR21HghlVR6VHt//9R8FH1Uf5SBFILUhRSDlInUipSLlIzUjlST1JEUktSTFJeUlRSalJ0UmlSc1J/Un1SjVKUUpJScVKIUpGPqI+nUqxSrVK8UrVSwVLNUtdS3lLjUuaY7VLgUvNS9VL4UvlTBlMIdThTDVMQ
		Uw9TFVMaUyNTL1MxUzNTOFNAU0ZTRU4XU0lTTVHWU15TaVNuWRhTe1N3U4JTllOgU6ZTpVOuU7BTtlPDfBKW2VPfZvxx7lPuU+hT7VP6VAFUPVRAVCxULVQ8VC5UNlQpVB1UTlSPVHVUjlRfVHFUd1RwVJJUe1SAVHZUhFSQVIZUx1SiVLhUpVSsVMRUyFSo////////
		VKtUwlSkVL5UvFTYVOVU5lUPVRRU/VTuVO1U+lTiVTlVQFVjVUxVLlVcVUVVVlVXVThVM1VdVZlVgFSvVYpVn1V7VX5VmFWeVa5VfFWDValVh1WoVdpVxVXfVcRV3FXkVdRWFFX3VhZV/lX9VhtV+VZOVlBx31Y0VjZWMlY4//9Wa1ZkVi9WbFZqVoZWgFaKVqBWlFaP
		VqVWrla2VrRWwla8VsFWw1bAVshWzlbRVtNW11buVvlXAFb/VwRXCVcIVwtXDVcTVxhXFlXHVxxXJlc3VzhXTlc7V0BXT1dpV8BXiFdhV39XiVeTV6BXs1ekV6pXsFfDV8ZX1FfSV9NYClfWV+NYC1gZWB1YclghWGJYS1hwa8BYUlg9WHlYhVi5WJ9Yq1i6WN5Yu1i4
		WK5YxVjTWNFY11jZWNhY5VjcWORY31jvWPpY+Vj7WPxY/VkCWQpZEFkbaKZZJVksWS1ZMlk4WT560llVWVBZTllaWVhZYllgWWdZbFlp////////WXhZgVmdT15Pq1mjWbJZxlnoWdxZjVnZWdpaJVofWhFaHFoJWhpaQFpsWklaNVo2WmJaalqaWrxavlrLWsJavVrj
		Wtda5lrpWtZa+lr7WwxbC1sWWzJa0FsqWzZbPltDW0VbQFtRW1VbWltbW2VbaVtwW3NbdVt4ZYhbeluA//9bg1umW7hbw1vHW8lb1FvQW+Rb5lviW95b5VvrW/Bb9lvzXAVcB1wIXA1cE1wgXCJcKFw4XDlcQVxGXE5cU1xQXE9bcVxsXG5OYlx2XHlcjFyRXJRZm1yr
		XLtctly8XLdcxVy+XMdc2VzpXP1c+lztXYxc6l0LXRVdF11cXR9dG10RXRRdIl0aXRldGF1MXVJdTl1LXWxdc112XYddhF2CXaJdnV2sXa5dvV2QXbddvF3JXc1d013SXdZd213rXfJd9V4LXhpeGV4RXhteNl43XkReQ15AXk5eV15UXl9eYl5kXkdedV52XnqevF5/
		XqBewV7CXshe0F7P////////XtZe417dXtpe217iXuFe6F7pXuxe8V7zXvBe9F74Xv5fA18JX11fXF8LXxFfFl8pXy1fOF9BX0hfTF9OXy9fUV9WX1dfWV9hX21fc193X4Nfgl9/X4pfiF+RX4dfnl+ZX5hfoF+oX61fvF/WX/tf5F/4X/Ff3WCzX/9gIWBg//9gGWAQ
		YClgDmAxYBtgFWArYCZgD2A6YFpgQWBqYHdgX2BKYEZgTWBjYENgZGBCYGxga2BZYIFgjWDnYINgmmCEYJtglmCXYJJgp2CLYOFguGDgYNNgtF/wYL1gxmC1YNhhTWEVYQZg9mD3YQBg9GD6YQNhIWD7YPFhDWEOYUdhPmEoYSdhSmE/YTxhLGE0YT1hQmFEYXNhd2FY
		YVlhWmFrYXRhb2FlYXFhX2FdYVNhdWGZYZZhh2GsYZRhmmGKYZFhq2GuYcxhymHJYfdhyGHDYcZhumHLf3lhzWHmYeNh9mH6YfRh/2H9Yfxh/mIAYghiCWINYgxiFGIb////////Yh5iIWIqYi5iMGIyYjNiQWJOYl5iY2JbYmBiaGJ8YoJiiWJ+YpJik2KWYtRig2KU
		Ytdi0WK7Ys9i/2LGZNRiyGLcYsxiymLCYsdim2LJYwxi7mLxYydjAmMIYu9i9WNQYz5jTWQcY09jlmOOY4Bjq2N2Y6Njj2OJY59jtWNr//9jaWO+Y+ljwGPGY+NjyWPSY/ZjxGQWZDRkBmQTZCZkNmUdZBdkKGQPZGdkb2R2ZE5lKmSVZJNkpWSpZIhkvGTaZNJkxWTH
		ZLtk2GTCZPFk54IJZOBk4WKsZONk72UsZPZk9GTyZPplAGT9ZRhlHGUFZSRlI2UrZTRlNWU3ZTZlOHVLZUhlVmVVZU1lWGVeZV1lcmV4ZYJlg4uKZZtln2WrZbdlw2XGZcFlxGXMZdJl22XZZeBl4WXxZ3JmCmYDZftnc2Y1ZjZmNGYcZk9mRGZJZkFmXmZdZmRmZ2Zo
		Zl9mYmZwZoNmiGaOZolmhGaYZp1mwWa5Zslmvma8////////ZsRmuGbWZtpm4GY/ZuZm6WbwZvVm92cPZxZnHmcmZyeXOGcuZz9nNmdBZzhnN2dGZ15nYGdZZ2NnZGeJZ3BnqWd8Z2pnjGeLZ6ZnoWeFZ7dn72e0Z+xns2fpZ7hn5GfeZ91n4mfuZ7lnzmfGZ+dqnGge
		aEZoKWhAaE1oMmhO//9os2graFloY2h3aH9on2iPaK1olGidaJtog2quaLlodGi1aKBoumkPaI1ofmkBaMppCGjYaSJpJmjhaQxozWjUaOdo1Wk2aRJpBGjXaONpJWj5aOBo72koaSppGmkjaSFoxml5aXdpXGl4aWtpVGl+aW5pOWl0aT1pWWkwaWFpXmldaYFpammy
		aa5p0Gm/acFp02m+ac5b6GnKad1pu2nDaadqLmmRaaBpnGmVabRp3mnoagJqG2n/awpp+WnyaedqBWmxah5p7WoUaetqCmoSasFqI2oTakRqDGpyajZqeGpHamJqWWpmakhqOGoiapBqjWqgaoRqomqj////////apeGF2q7asNqwmq4arNqrGreatFq32qqatpq6mr7
		awWGFmr6axJrFpsxax9rOGs3dtxrOZjua0drQ2tJa1BrWWtUa1trX2tha3hreWt/a4BrhGuDa41rmGuVa55rpGuqa6trr2uya7Frs2u3a7xrxmvLa9Nr32vsa+tr82vv//+evmwIbBNsFGwbbCRsI2xebFVsYmxqbIJsjWyabIFsm2x+bGhsc2ySbJBsxGzxbNNsvWzX
		bMVs3WyubLFsvmy6bNts72zZbOptH4hNbTZtK209bThtGW01bTNtEm0MbWNtk21kbVpteW1ZbY5tlW/kbYVt+W4VbgpttW3HbeZtuG3Gbext3m3Mbeht0m3Fbfpt2W3kbdVt6m3ubi1ubm4ubhlucm5fbj5uI25rbitudm5Nbh9uQ246bk5uJG7/bh1uOG6CbqpumG7J
		brdu0269bq9uxG6ybtRu1W6PbqVuwm6fb0FvEXBMbuxu+G7+bz9u8m8xbu9vMm7M////////bz5vE273b4Zvem94b4FvgG9vb1tv829tb4JvfG9Yb45vkW/Cb2Zvs2+jb6FvpG+5b8Zvqm/fb9Vv7G/Ub9hv8W/ub9twCXALb/pwEXABcA9v/nAbcBpvdHAdcBhwH3Aw
		cD5wMnBRcGNwmXCScK9w8XCscLhws3CucN9wy3Dd//9w2XEJcP1xHHEZcWVxVXGIcWZxYnFMcVZxbHGPcftxhHGVcahxrHHXcblxvnHScclx1HHOceBx7HHncfVx/HH5cf9yDXIQchtyKHItcixyMHIycjtyPHI/ckByRnJLclhydHJ+coJygXKHcpJylnKicqdyuXKy
		csNyxnLEcs5y0nLicuBy4XL5cvdQD3MXcwpzHHMWcx1zNHMvcylzJXM+c05zT57Yc1dzanNoc3BzeHN1c3tzenPIc7NzznO7c8Bz5XPuc950onQFdG90JXP4dDJ0OnRVdD90X3RZdEF0XHRpdHB0Y3RqdHZ0fnSLdJ50p3TKdM901HPx////////dOB043TndOl07nTy
		dPB08XT4dPd1BHUDdQV1DHUOdQ11FXUTdR51JnUsdTx1RHVNdUp1SXVbdUZ1WnVpdWR1Z3VrdW11eHV2dYZ1h3V0dYp1iXWCdZR1mnWddaV1o3XCdbN1w3W1db11uHW8dbF1zXXKddJ12XXjdd51/nX///91/HYBdfB1+nXydfN2C3YNdgl2H3YndiB2IXYidiR2NHYw
		djt2R3ZIdkZ2XHZYdmF2YnZodml2anZndmx2cHZydnZ2eHZ8doB2g3aIdot2jnaWdpN2mXaadrB2tHa4drl2unbCds121nbSdt524Xbldud26oYvdvt3CHcHdwR3KXckdx53JXcmdxt3N3c4d0d3Wndod2t3W3dld393fnd5d453i3eRd6B3nnewd7Z3uXe/d7x3vXe7
		d8d3zXfXd9p33Hfjd+53/HgMeBJ5JnggeSp4RXiOeHR4hnh8eJp4jHijeLV4qniveNF4xnjLeNR4vni8eMV4ynjs////////eOd42nj9ePR5B3kSeRF5GXkseSt5QHlgeVd5X3laeVV5U3l6eX95inmdeaefS3mqea55s3m5ebp5yXnVeed57HnheeN6CHoNehh6GXog
		eh95gHoxejt6Pno3ekN6V3pJemF6Ynppn516cHp5en16iHqXepV6mHqWeql6yHqw//96tnrFesR6v5CDesd6ynrNes961XrTetl62nrdeuF64nrmeu168HsCew97CnsGezN7GHsZex57NXsoezZ7UHt6ewR7TXsLe0x7RXt1e2V7dHtne3B7cXtse257nXuYe597jXuc
		e5p7i3uSe497XXuZe8t7wXvMe897tHvGe9176XwRfBR75nvlfGB8AHwHfBN783v3fBd8DXv2fCN8J3wqfB98N3wrfD18THxDfFR8T3xAfFB8WHxffGR8VnxlfGx8dXyDfJB8pHytfKJ8q3yhfKh8s3yyfLF8rny5fL18wHzFfMJ82HzSfNx84ps7fO988nz0fPZ8+n0G
		////////fQJ9HH0VfQp9RX1LfS59Mn0/fTV9Rn1zfVZ9Tn1yfWh9bn1PfWN9k32JfVt9j319fZt9un2ufaN9tX3Hfb19q349faJ9r33cfbh9n32wfdh93X3kfd59+33yfeF+BX4KfiN+IX4SfjF+H34Jfgt+In5GfmZ+O341fjl+Q343//9+Mn46fmd+XX5Wfl5+WX5a
		fnl+an5pfnx+e36DfdV+fY+ufn9+iH6Jfox+kn6QfpN+lH6Wfo5+m36cfzh/On9Ff0x/TX9Of1B/UX9Vf1R/WH9ff2B/aH9pf2d/eH+Cf4Z/g3+If4d/jH+Uf55/nX+af6N/r3+yf7l/rn+2f7iLcX/Ff8Z/yn/Vf9R/4X/mf+l/83/5mNyABoAEgAuAEoAYgBmAHIAh
		gCiAP4A7gEqARoBSgFiAWoBfgGKAaIBzgHKAcIB2gHmAfYB/gISAhoCFgJuAk4CagK1RkICsgNuA5YDZgN2AxIDagNaBCYDvgPGBG4EpgSOBL4FL////////louBRoE+gVOBUYD8gXGBboFlgWaBdIGDgYiBioGAgYKBoIGVgaSBo4FfgZOBqYGwgbWBvoG4gb2BwIHC
		gbqByYHNgdGB2YHYgciB2oHfgeCB54H6gfuB/oIBggKCBYIHggqCDYIQghaCKYIrgjiCM4JAglmCWIJdglqCX4Jk//+CYoJogmqCa4IugnGCd4J4gn6CjYKSgquCn4K7gqyC4YLjgt+C0oL0gvOC+oOTgwOC+4L5gt6DBoLcgwmC2YM1gzSDFoMygzGDQIM5g1CDRYMv
		gyuDF4MYg4WDmoOqg5+DooOWgyODjoOHg4qDfIO1g3ODdYOgg4mDqIP0hBOD64POg/2EA4PYhAuDwYP3hAeD4IPyhA2EIoQgg72EOIUGg/uEbYQqhDyFWoSEhHeEa4SthG6EgoRphEaELIRvhHmENYTKhGKEuYS/hJ+E2YTNhLuE2oTQhMGExoTWhKGFIYT/hPSFF4UY
		hSyFH4UVhRSE/IVAhWOFWIVI////////hUGGAoVLhVWFgIWkhYiFkYWKhaiFbYWUhZuF6oWHhZyFd4V+hZCFyYW6hc+FuYXQhdWF3YXlhdyF+YYKhhOGC4X+hfqGBoYihhqGMIY/hk1OVYZUhl+GZ4ZxhpOGo4aphqqGi4aMhraGr4bEhsaGsIbJiCOGq4bUht6G6Ybs
		//+G34bbhu+HEocGhwiHAIcDhvuHEYcJhw2G+YcKhzSHP4c3hzuHJYcphxqHYIdfh3iHTIdOh3SHV4doh26HWYdTh2OHaogFh6KHn4eCh6+Hy4e9h8CH0JbWh6uHxIezh8eHxoe7h++H8ofgiA+IDYf+h/aH94gOh9KIEYgWiBWIIoghiDGINog5iCeIO4hEiEKIUohZ
		iF6IYohriIGIfoieiHWIfYi1iHKIgoiXiJKIroiZiKKIjYikiLCIv4ixiMOIxIjUiNiI2YjdiPmJAoj8iPSI6IjyiQSJDIkKiROJQ4keiSWJKokriUGJRIk7iTaJOIlMiR2JYIle////////iWaJZIltiWqJb4l0iXeJfomDiYiJiomTiZiJoYmpiaaJrImvibKJuom9
		ib+JwInaidyJ3YnnifSJ+IoDihaKEIoMihuKHYolijaKQYpbilKKRopIinyKbYpsimKKhYqCioSKqIqhipGKpYqmipqKo4rEis2KworaiuuK84rn//+K5IrxixSK4IriiveK3orbiwyLB4saiuGLFosQixeLIIszl6uLJosriz6LKItBi0yLT4tOi0mLVotbi1qLa4tf
		i2yLb4t0i32LgIuMi46LkouTi5aLmYuajDqMQYw/jEiMTIxOjFCMVYxijGyMeIx6jIKMiYyFjIqMjYyOjJSMfIyYYh2MrYyqjL2MsoyzjK6MtozIjMGM5IzjjNqM/Yz6jPuNBI0FjQqNB40PjQ2NEJ9OjROMzY0UjRaNZ41tjXGNc42BjZmNwo2+jbqNz43ajdaNzI3b
		jcuN6o3rjd+N4438jgiOCY3/jh2OHo4Qjh+OQo41jjCONI5K////////jkeOSY5MjlCOSI5ZjmSOYI4qjmOOVY52jnKOfI6BjoeOhY6EjouOio6TjpGOlI6ZjqqOoY6sjrCOxo6xjr6OxY7IjsuO247jjvyO+47rjv6PCo8FjxWPEo8ZjxOPHI8fjxuPDI8mjzOPO485
		j0WPQo8+j0yPSY9Gj06PV49c//+PYo9jj2SPnI+fj6OPrY+vj7eP2o/lj+KP6o/vkIeP9JAFj/mP+pARkBWQIZANkB6QFpALkCeQNpA1kDmP+JBPkFCQUZBSkA6QSZA+kFaQWJBekGiQb5B2lqiQcpCCkH2QgZCAkIqQiZCPkKiQr5CxkLWQ4pDkYkiQ25ECkRKRGZEy
		kTCRSpFWkViRY5FlkWmRc5FykYuRiZGCkaKRq5GvkaqRtZG0kbqRwJHBkcmRy5HQkdaR35HhkduR/JH1kfaSHpH/khSSLJIVkhGSXpJXkkWSSZJkkkiSlZI/kkuSUJKckpaSk5KbklqSz5K5kreS6ZMPkvqTRJMu////////kxmTIpMakyOTOpM1kzuTXJNgk3yTbpNW
		k7CTrJOtk5STuZPWk9eT6JPlk9iTw5Pdk9CTyJPklBqUFJQTlAOUB5QQlDaUK5Q1lCGUOpRBlFKURJRblGCUYpRelGqSKZRwlHWUd5R9lFqUfJR+lIGUf5WClYeVipWUlZaVmJWZ//+VoJWolaeVrZW8lbuVuZW+lcpv9pXDlc2VzJXVldSV1pXcleGV5ZXiliGWKJYu
		li+WQpZMlk+WS5Z3llyWXpZdll+WZpZylmyWjZaYlpWWl5aqlqeWsZaylrCWtJa2lriWuZbOlsuWyZbNiU2W3JcNltWW+ZcElwaXCJcTlw6XEZcPlxaXGZcklyqXMJc5lz2XPpdEl0aXSJdCl0mXXJdgl2SXZpdoUtKXa5dxl3mXhZd8l4GXepeGl4uXj5eQl5yXqJem
		l6OXs5e0l8OXxpfIl8uX3Jftn0+X8nrfl/aX9ZgPmAyYOJgkmCGYN5g9mEaYT5hLmGuYb5hw////////mHGYdJhzmKqYr5ixmLaYxJjDmMaY6ZjrmQOZCZkSmRSZGJkhmR2ZHpkkmSCZLJkumT2ZPplCmUmZRZlQmUuZUZlSmUyZVZmXmZiZpZmtma6ZvJnfmduZ3ZnY
		mdGZ7ZnumfGZ8pn7mfiaAZoPmgWZ4poZmiuaN5pFmkKaQJpD//+aPppVmk2aW5pXml+aYpplmmSaaZprmmqarZqwmryawJrPmtGa05rUmt6a35rimuOa5prvmuua7pr0mvGa95r7mwabGJsamx+bIpsjmyWbJ5somymbKpsumy+bMptEm0ObT5tNm06bUZtYm3Sbk5uD
		m5GblpuXm5+boJuom7SbwJvKm7mbxpvPm9Gb0pvjm+Kb5JvUm+GcOpvym/Gb8JwVnBScCZwTnAycBpwInBKcCpwEnC6cG5wlnCScIZwwnEecMpxGnD6cWpxgnGecdpx4nOec7JzwnQmdCJzrnQOdBp0qnSadr50jnR+dRJ0VnRKdQZ0/nT6dRp1I////////nV2dXp1k
		nVGdUJ1ZnXKdiZ2Hnaudb516nZqdpJ2pnbKdxJ3BnbuduJ26ncadz53Cndmd0534nead7Z3vnf2eGp4bnh6edZ55nn2egZ6InouejJ6SnpWekZ6dnqWeqZ64nqqerZdhnsyezp7PntCe1J7cnt6e3Z7gnuWe6J7v//+e9J72nvee+Z77nvye/Z8Hnwh2t58VnyGfLJ8+
		n0qfUp9Un2OfX59gn2GfZp9nn2yfap93n3Kfdp+Vn5yfoFgvaceQWXRkUdxxmf//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		/////////////////////////////////////////////w=="""
	
	
	_UNICODE_TO_QR_KANJI: dict[str,int] = {}

def _init_kanji_table() -> None:
	data: bytes = base64.standard_b64decode(QrSegmentAdvanced._PACKED_QR_KANJI_TO_UNICODE)
	arr: array.array[int] = array.array("H", data)
	if sys.byteorder == "little":
		arr.byteswap()
	for (i, c) in enumerate(arr):
		if c == 0xFFFF:
			continue
		s: str = chr(c)
		assert s not in QrSegmentAdvanced._UNICODE_TO_QR_KANJI
		QrSegmentAdvanced._UNICODE_TO_QR_KANJI[s] = i

# Unpack the Shift JIS table into a more computation-friendly form
_init_kanji_table()



# ---- Private helper class ----

class _BitBuffer(list[int]):
	"""An appendable sequence of bits (0s and 1s). Mainly used by QrSegment."""
	
	def append_bits(self, val: int, n: int) -> None:
		"""Appends the given number of low-order bits of the given
		value to this buffer. Requires n >= 0 and 0 <= val < 2^n."""
		if (n < 0) or (val >> n != 0):
			raise ValueError("Value out of range")
		self.extend(((val >> i) & 1) for i in reversed(range(n)))


def _get_bit(x: int, i: int) -> bool:
	"""Returns true iff the i'th bit of x is set to 1."""
	return (x >> i) & 1 != 0



class DataTooLongError(ValueError):
	"""Raised when the supplied data does not fit any QR Code version. Ways to handle this exception include:
	- Decrease the error correction level if it was greater than Ecc.LOW.
	- If the encode_segments() function was called with a maxversion argument, then increase
	  it if it was less than QrCode.MAX_VERSION. (This advice does not apply to the other
	  factory functions because they search all versions up to QrCode.MAX_VERSION.)
	- Split the text data into better or optimal segments in order to reduce the number of bits required.
	- Change the text or binary data to be shorter.
	- Change the text to fit the character set of a particular segment mode (e.g. alphanumeric).
	- Propagate the error upward to the caller/user."""
	pass
