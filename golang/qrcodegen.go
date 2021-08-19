package qrcodegen

import (
	"errors"
	"fmt"
	"math"

	"github.com/nayuki/qrcodegen/internal/bitx"
	"github.com/nayuki/qrcodegen/internal/mathx"
	"github.com/nayuki/qrcodegen/mask"
	"github.com/nayuki/qrcodegen/qrcodeecc"
	"github.com/nayuki/qrcodegen/qrsegment"
	"github.com/nayuki/qrcodegen/version"
)

/*---- Miscellaneous values ----*/

var (
	// ErrDataTooLong is the error type when the supplied data does not fit any QR Code version.
	//
	// Ways to handle this exception include:
	//
	// - Decrease the error correction level if it was greater than `QrCodeEcc::Low`.
	// - If the `encode_segments_advanced()` function was called, then increase the maxversion
	//   argument if it was less than `Version::MAX`. (This advice does not apply to the
	//   other factory functions because they search all versions up to `Version::MAX`.)
	// - Split the text data into better or optimal segments in order to reduce the number of bits required.
	// - Change the text or binary data to be shorter.
	// - Change the text to fit the character set of a particular segment mode (e.g. alphanumeric).
	// - Propagate the error upward to the caller/user.
	ErrDataTooLong = errors.New("DataTooLong")
)

// alias
type Mask = mask.Mask
type QrCodeEcc = qrcodeecc.QrCodeEcc
type QrSegment = qrsegment.QrSegment
type Version = version.Version

/*---- QrCode functionality ----*/

// QrCode is a QR Code symbol, which is a type of two-dimension barcode.
//
// Invented by Denso Wave and described in the ISO/IEC 18004 standard.
//
// Instances of this struct represent an immutable square grid of dark and light cells.
// The impl provides static factory functions to create a QR Code from text or binary data.
// The struct and impl cover the QR Code Model 2 specification, supporting all versions
// (sizes) from 1 to 40, all 4 error correction levels, and 4 character encoding modes.
//
// Ways to create a QR Code object:
//
// - High level: Take the payload data and call `QrCode::encode_text()` or `QrCode::encode_binary()`.
// - Mid level: Custom-make the list of segments and call
//   `QrCode::encode_segments()` or `QrCode::encode_segments_advanced()`.
// - Low level: Custom-make the array of data codeword bytes (including segment
//   headers and final padding, excluding error correction codewords), supply the
//   appropriate version number, and call the `QrCode::encode_codewords()` constructor.
//
// (Note that all ways require supplying the desired error correction level.)
type QrCode struct {
	// Scalar parameters:

	// The version number of this QR Code, which is between 1 and 40 (inclusive).
	// This determines the size of this barcode.
	version Version
	// The width and height of this QR Code, measured in modules, between
	// 21 and 177 (inclusive). This is equal to version * 4 + 17.
	size int32
	// The error correction level used in this QR Code.
	errorcorrectionlevel QrCodeEcc
	// The index of the mask pattern used in this QR Code, which is between 0 and 7 (inclusive).
	// Even if a QR Code is created with automatic masking requested (mask = None),
	// the resulting object still has a mask value between 0 and 7.
	mask Mask

	// Grids of modules/pixels, with dimensions of size*size:

	// The modules of this QR Code (false = light, true = dark).
	// Immutable after constructor finishes. Accessed through get_module().
	modules []bool
	// Indicates function modules that are not subjected to masking. Discarded when constructor finishes.
	isfunction []bool
}

/*---- Static factory functions (high level) ----*/

// EncodeText returns a QR Code representing the given Unicode text string at the given error correction level.
//
// As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer Unicode
// code points (not UTF-8 code units) if the low error correction level is used. The smallest possible
// QR Code version is automatically chosen for the output. The ECC level of the result may be higher than
// the ecl argument if it can be done without increasing the version.
//
// Returns a wrapped `QrCode` if successful, or `Err` if the
// data is too long to fit in any version at the given ECC level.
func EncodeText(text string, ecl QrCodeEcc) (*QrCode, error) {
	chrs := []rune(text)
	segs := qrsegment.MakeSegments(chrs)

	return EncodeSegments(segs, ecl)
}

// EncodeBinary returns a QR Code representing the given binary data at the given error correction level.
//
// This function always encodes using the binary segment mode, not any text mode. The maximum number of
// bytes allowed is 2953. The smallest possible QR Code version is automatically chosen for the output.
// The ECC level of the result may be higher than the ecl argument if it can be done without increasing the version.
//
// Returns a wrapped `QrCode` if successful, or `Err` if the
// data is too long to fit in any version at the given ECC level.
func EncodeBinary(data []uint8, ecl QrCodeEcc) (*QrCode, error) {
	seg := qrsegment.MakeBytes(data)
	segs := []QrSegment{seg}

	return EncodeSegments(segs, ecl)
}

/*---- Static factory functions (mid level) ----*/

// EncodeSegments returns a QR Code representing the given segments at the given error correction level.
//
// The smallest possible QR Code version is automatically chosen for the output. The ECC level
// of the result may be higher than the ecl argument if it can be done without increasing the version.
//
// This function allows the user to create a custom sequence of segments that switches
// between modes (such as alphanumeric and byte) to encode text in less space.
// This is a mid-level API; the high-level API is `encode_text()` and `encode_binary()`.
//
// Returns a wrapped `QrCode` if successful, or `Err` if the
// data is too long to fit in any version at the given ECC level.
func EncodeSegments(segs []QrSegment, ecl QrCodeEcc) (*QrCode, error) {
	return EncodeSegmentsAdvanced(segs, ecl, version.Min, version.Max, nil, true)
}

// EncodeSegmentsAdvanced returns a QR Code representing the given segments with the given encoding parameters.
//
// The smallest possible QR Code version within the given range is automatically
// chosen for the output. Iff boostecl is `true`, then the ECC level of the result
// may be higher than the ecl argument if it can be done without increasing the
// version. The mask number is either between 0 to 7 (inclusive) to force that
// mask, or `None` to automatically choose an appropriate mask (which may be slow).
//
// This function allows the user to create a custom sequence of segments that switches
// between modes (such as alphanumeric and byte) to encode text in less space.
// This is a mid-level API; the high-level API is `encode_text()` and `encode_binary()`.
//
// Returns a wrapped `QrCode` if successful, or `Err` if the data is too
// long to fit in any version in the given range at the given ECC level.
func EncodeSegmentsAdvanced(
	segs []QrSegment,
	ecl QrCodeEcc,
	minversion Version,
	maxversion Version,
	mask *Mask,
	boostecl bool,
) (q *QrCode, err error) {
	if minversion > maxversion {
		panic("Invalid value")
	}

	// Find the minimal version number to use
	ver := minversion
	var datausedbits uint
	for {
		// Number of data bits available
		datacapacitybits := getNumDataCodewords(ver, ecl) * 8
		dataused := qrsegment.GetTotalBits(segs, ver)

		// TODO: refactor to match closer to the semantics of rust counterpart map_or
		mapOr := false
		if dataused != nil {
			mapOr = *dataused <= datacapacitybits
		}

		if mapOr {
			datausedbits = *dataused // This version number is found to be suitable
			break
		} else if ver.Value() >= maxversion.Value() { // All versions in the range could not fit the given data
			if dataused == nil {
				return nil, fmt.Errorf("%w: Segment too long", ErrDataTooLong)
			}
			return nil, fmt.Errorf("%w: Data length = %v bits, Max capacity = %v bits", ErrDataTooLong, *dataused, datacapacitybits)
		} else {
			ver = version.New(ver.Value() + 1)
		}
	}

	// Increase the error correction level while the data still fits in the current version number
	for _, newecl := range []QrCodeEcc{qrcodeecc.Medium, qrcodeecc.Quartile, qrcodeecc.High} { // From low to high
		if boostecl && datausedbits <= getNumDataCodewords(ver, newecl)*8 {
			ecl = newecl
		}
	}

	// Concatenate all segments to create the data bit string
	bb := qrsegment.BitBuffer{}
	for _, seg := range segs {
		bb.AppendBits(seg.Mode().ModeBits(), 4)
		bb.AppendBits(uint32(seg.NumChars()), seg.Mode().NumCharCountBits(ver))
		bb = append(bb, seg.Data()...)
	}
	if uint(len(bb)) != datausedbits {
		panic("uint(len(bb)) != datausedbits")
	}

	// Add terminator and pad up to a byte if applicable
	datacapacitybits := getNumDataCodewords(ver, ecl) * 8
	if uint(len(bb)) > datacapacitybits {
		panic("uint(len(bb)) > datacapacitybits")
	}
	numzerobits := mathx.MinUint(4, datacapacitybits-uint(len(bb)))
	bb.AppendBits(0, uint8(numzerobits))

	// TODO: check edge case for WrappingNeg
	numzerobits = uint(mathx.WrappingNeg(len(bb)) & 7)
	bb.AppendBits(0, uint8(numzerobits))
	if len(bb)%8 != 0 {
		panic("len(bb)%8 != 0")
	}

	// TODO: refactor to match closer to the semantics of rust counterpart .iter().cycle()
	// Pad with alternating bytes until data capacity is reached
	for {
		for _, padByte := range []uint32{0xEC, 0x11} {
			if len(bb) >= int(datacapacitybits) {
				goto Donepad
			}
			bb.AppendBits(padByte, 8)
		}
	}
Donepad:

	// Pack bits into bytes in big endian
	datacodewords := make([]uint8, len(bb)/8)
	for i, bit := range bb {
		datacodewords[i>>3] |= mathx.BoolToUint8(bit) << (7 - (i & 7))
	}

	// Create the QR Code object
	q = EncodeCodewords(ver, ecl, datacodewords, mask)

	return q, nil
}

/*---- Constructor (low level) ----*/

// EncodeCodewords creates a new QR Code with the given version number,
// error correction level, data codeword bytes, and mask number.
//
// This is a low-level API that most users should not use directly.
// A mid-level API is the `encode_segments()` function.
func EncodeCodewords(ver Version, ecl QrCodeEcc, datacodewords []uint8, m *Mask) *QrCode {
	size := uint(ver.Value())*4 + 17

	result := &QrCode{
		version:              ver,
		size:                 int32(size),
		mask:                 mask.New(0), // Dummy value
		errorcorrectionlevel: ecl,
		modules:              make([]bool, size*size), // Initially all light
		isfunction:           make([]bool, size*size),
	}

	// Compute ECC, draw modules
	result.drawFunctionPatterns()
	allcodewords := result.addEccAndInterleave(datacodewords)
	result.drawCodewords(allcodewords)

	// Do masking
	if m == nil { // Automatically choose best mask
		minpenalty := int32(math.MaxInt32)
		for i, max := uint8(0), uint8(8); i < max; i++ {
			newmask := mask.New(i)
			result.applyMask(newmask)
			result.drawFormatBits(newmask)
			penalty := result.getPenaltyScore()
			if penalty < minpenalty {
				m = &newmask
				minpenalty = penalty
			}

			result.applyMask(newmask) // Undoes the mask due to XOR
		}
	}
	newmask := *m
	result.mask = newmask
	result.applyMask(newmask)      // Apply the final choice of mask
	result.drawFormatBits(newmask) // Overwrite old format bits

	result.isfunction = result.isfunction[:0]
	// TODO: need to implement rust shrink_to_fit() ?

	return result
}

/*---- Public methods ----*/

// Version returns this QR Code's version, in the range [1, 40].
func (q QrCode) Version() Version {
	return q.version
}

// Size returns this QR Code's size, in the range [21, 177].
func (q QrCode) Size() int32 {
	return q.size
}

// ErrorCorrectionLevel returns this QR Code's error correction level.
func (q QrCode) ErrorCorrectionLevel() QrCodeEcc {
	return q.errorcorrectionlevel
}

// Mask returns this QR Code's mask, in the range [0, 7].
func (q QrCode) Mask() Mask {
	return q.mask
}

// GetModule returns the color of the module (pixel) at the given coordinates,
// which is `false` for light or `true` for dark.
//
// The top left corner has the coordinates (x=0, y=0). If the given
// coordinates are out of bounds, then `false` (light) is returned.
func (q QrCode) GetModule(x, y int32) bool {
	return 0 <= x && x < q.size && 0 <= y && y < q.size && q.module(x, y)
}

// Returns the color of the module at the given coordinates, which must be in bounds.
func (q QrCode) module(x, y int32) bool {
	return q.modules[uint(y*q.size+x)]
}

// TODO: refactor to match closer to the semantics of rust counterpart
// Returns a mutable reference to the module's color at the given coordinates, which must be in bounds.
func (q *QrCode) moduleMut(x, y int32, mut bool) {
	q.modules[uint(y*q.size+x)] = mut
}

/*---- Private helper methods for constructor: Drawing function modules ----*/

// Reads this object's version field, and draws and marks all function modules.
func (q *QrCode) drawFunctionPatterns() {
	// Draw horizontal and vertical timing patterns
	size := q.size
	for i := int32(0); i < size; i++ {
		q.setFunctionModule(6, i, i%2 == 0)
		q.setFunctionModule(i, 6, i%2 == 0)
	}

	// Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
	q.drawFinderPattern(3, 3)
	q.drawFinderPattern(q.size-4, 3)
	q.drawFinderPattern(3, q.size-4)

	// Draw numerous alignment patterns
	alignpatpos := q.getAlignmentPatternPositions()
	numalign := len(alignpatpos)
	for i := 0; i < numalign; i++ {
		for j := 0; j < numalign; j++ {
			// Don't draw on the three finder corners
			if !(i == 0 && j == 0 || i == 0 && j == numalign-1 || i == numalign-1 && j == 0) {
				q.drawAlignmentPattern(alignpatpos[i], alignpatpos[j])
			}
		}
	}

	// Draw configuration data
	q.drawFormatBits(mask.New(0)) // Dummy mask value; overwritten later in the constructor
	q.drawVersion()
}

// Draws two copies of the format bits (with its own error correction code)
// based on the given mask and this object's error correction level field.
func (q *QrCode) drawFormatBits(mask Mask) {
	// Calculate error correction code and pack bits
	var bits uint32
	{
		// errorcorrectionlevel is uint2, mask is uint3
		data := uint32(q.errorcorrectionlevel.FormatBits()<<3 | mask.Value())
		rem := data
		for i := 0; i < 10; i++ {
			rem = (rem << 1) ^ ((rem >> 9) * 0x537)
		}
		bits = (data<<10 | rem) ^ 0x5412 // uint15
	}
	if bits>>15 != 0 {
		panic("bits>>15 != 0")
	}

	// Draw first copy
	for i := int32(0); i < 6; i++ {
		q.setFunctionModule(8, i, bitx.GetBit(bits, i))
	}
	q.setFunctionModule(8, 7, bitx.GetBit(bits, 6))
	q.setFunctionModule(8, 8, bitx.GetBit(bits, 7))
	q.setFunctionModule(7, 8, bitx.GetBit(bits, 8))
	for i := int32(9); i < 15; i++ {
		q.setFunctionModule(14-i, 8, bitx.GetBit(bits, i))
	}

	// Draw second copy
	size := q.size
	for i := int32(0); i < 8; i++ {
		q.setFunctionModule(size-1-i, 8, bitx.GetBit(bits, i))
	}
	for i := int32(8); i < 15; i++ {
		q.setFunctionModule(8, size-15+i, bitx.GetBit(bits, i))
	}
	q.setFunctionModule(8, size-8, true) // Always dark
}

// Draws two copies of the version bits (with its own error correction code),
// based on this object's version field, iff 7 <= version <= 40.
func (q *QrCode) drawVersion() {
	if q.version < 7 {
		return
	}

	// Calculate error correction code and pack bits
	var bits uint32
	{
		data := uint32(q.version.Value()) // uint6, in the range [7, 40]
		rem := data
		for i := 0; i < 12; i++ {
			rem = (rem << 1) ^ ((rem >> 11) * 0x1F25)
		}
		bits = data<<12 | rem // uint18
	}
	if bits>>18 != 0 {
		panic("bits>>18 != 0")
	}

	// Draw two copies
	for i := int32(0); i < 18; i++ {
		bit := bitx.GetBit(bits, i)
		a := q.size - 11 + i%3
		b := i / 3
		q.setFunctionModule(a, b, bit)
		q.setFunctionModule(b, a, bit)
	}
}

// Draws a 9*9 finder pattern including the border separator,
// with the center module at (x, y). Modules can be out of bounds.
func (q *QrCode) drawFinderPattern(x, y int32) {
	for dy := int32(-4); dy <= 4; dy++ {
		for dx := int32(-4); dx <= 4; dx++ {
			xx := x + dx
			yy := y + dy
			if 0 <= xx && xx < q.size && 0 <= yy && yy < q.size {
				dist := mathx.MaxInt32(mathx.AbsInt32(dx), mathx.AbsInt32(dy)) // Chebyshev/infinity norm
				q.setFunctionModule(xx, yy, dist != 2 && dist != 4)
			}
		}
	}
}

// Draws a 5*5 alignment pattern, with the center module
// at (x, y). All modules must be in bounds.
func (q *QrCode) drawAlignmentPattern(x, y int32) {
	for dy := int32(-2); dy <= 2; dy++ {
		for dx := int32(-2); dx <= 2; dx++ {
			q.setFunctionModule(x+dx, y+dy, mathx.MaxInt32(mathx.AbsInt32(dx), mathx.AbsInt32(dy)) != 1)
		}
	}
}

// Sets the color of a module and marks it as a function module.
// Only used by the constructor. Coordinates must be in bounds.
func (q *QrCode) setFunctionModule(x int32, y int32, isdark bool) {
	q.moduleMut(x, y, isdark)
	q.isfunction[(y*q.size + x)] = true
}

/*---- Private helper methods for constructor: Codewords and masking ----*/

// Returns a new byte string representing the given data with the appropriate error correction
// codewords appended to it, based on this object's version and error correction level.
func (q *QrCode) addEccAndInterleave(data []uint8) []uint8 {
	ver := q.version
	ecl := q.errorcorrectionlevel
	if len(data) != int(getNumDataCodewords(ver, ecl)) {
		panic("Illegal argument")
	}

	// Calculate parameter numbers
	numblocks := tableGet(NUM_ERROR_CORRECTION_BLOCKS, ver, ecl)
	blockecclen := tableGet(ECC_CODEWORDS_PER_BLOCK, ver, ecl)
	rawcodewords := getNumRawDataModules(ver) / 8
	numshortblocks := numblocks - (rawcodewords % numblocks)
	shortblocklen := rawcodewords / numblocks

	// Split data into blocks and append ECC to each block
	blocks := make([][]uint8, 0, numblocks)
	rsdiv := reedSolomonComputeDivisor(blockecclen)

	var k uint
	for i, max := uint(0), numblocks; i < max; i++ {
		datlen := shortblocklen - blockecclen + uint(mathx.BoolToUint8(i >= numshortblocks))
		dat := make([]uint8, datlen)
		_ = copy(dat, data[k:k+datlen])
		k += datlen
		ecc := reedSolomonComputeRemainder(dat, rsdiv)

		if i < numshortblocks {
			dat = append(dat, 0)
		}
		dat = append(dat, ecc...)
		blocks = append(blocks, dat)
	}

	// Interleave (not concatenate) the bytes from every block into a single sequence
	result := make([]uint8, 0, rawcodewords)
	for i, max := uint(0), shortblocklen; i <= max; i++ {
		for j, block := range blocks {
			// Skip the padding byte in short blocks
			if i != shortblocklen-blockecclen || uint(j) >= numshortblocks {
				result = append(result, block[i])
			}
		}
	}

	return result
}

// Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
// data area of this QR Code. Function modules need to be marked off before this is called.
func (q *QrCode) drawCodewords(data []uint8) {
	if uint(len(data)) != getNumRawDataModules(q.version)/8 {
		panic("Illegal argument")
	}

	var i uint // Bit index into the data
	// Do the funny zigzag scan
	right := q.size - 1
	for right >= 1 { // Index of right column in each column pair
		if right == 6 {
			right = 5
		}
		for vert := int32(0); vert < q.size; vert++ { // Vertical counter
			for j := int32(0); j < 2; j++ {
				x := right - j // Actual x coordinate
				upward := (right+1)&2 == 0
				var y int32
				if upward {
					y = q.size - 1 - vert
				} else {
					y = vert
				}
				if !q.isfunction[(y*q.size+x)] && i < uint(len(data)*8) {
					q.moduleMut(x, y, bitx.GetBit(uint32(data[i>>3]), int32(7-(i&7))))
					i += 1
				}
				// If this QR Code has any remainder bits (0 to 7), they were assigned as
				// 0/false/light by the constructor and are left unchanged by this method
			}
		}
		right -= 2
	}

	if i != uint(len(data)*8) {
		panic("i != uint(len(data)*8)")
	}
}

// XORs the codeword modules in this QR Code with the given mask pattern.
// The function modules must be marked and the codeword bits must be drawn
// before masking. Due to the arithmetic of XOR, calling apply_mask() with
// the same mask value a second time will undo the mask. A final well-formed
// QR Code needs exactly one (not zero, two, etc.) mask applied.
func (q *QrCode) applyMask(mask Mask) {
	for y := int32(0); y < q.size; y++ {
		for x := int32(0); x < q.size; x++ {
			var invert bool
			switch mask.Value() {
			case 0:
				invert = (x+y)%2 == 0
			case 1:
				invert = y%2 == 0
			case 2:
				invert = x%3 == 0
			case 3:
				invert = (x+y)%3 == 0
			case 4:
				invert = (x/3+y/2)%2 == 0
			case 5:
				invert = x*y%2+x*y%3 == 0
			case 6:
				invert = (x*y%2+x*y%3)%2 == 0
			case 7:
				invert = ((x+y)%2+x*y%3)%2 == 0
			default:
				panic("unreachable")
			}
			newModule := q.module(x, y) != (invert && !q.isfunction[(y*q.size+x)])
			q.moduleMut(x, y, newModule)
		}
	}
}

// Calculates and returns the penalty score based on state of this QR Code's current modules.
// This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
func (q QrCode) getPenaltyScore() int32 {
	var result int32
	size := q.size

	// Adjacent modules in row having same color, and finder-like patterns
	for y := int32(0); y < size; y++ {
		var runcolor bool
		var runx int32
		runhistory := newFinderPenalty(size)
		for x := int32(0); x < size; x++ {
			if q.module(x, y) == runcolor {
				runx += 1
				if runx == 5 {
					result += PENALTY_N1
				} else if runx > 5 {
					result += 1
				}
			} else {
				runhistory.addHistory(runx)
				if !runcolor {
					result += runhistory.countPatterns() * PENALTY_N3
				}
				runcolor = q.module(x, y)
				runx = 1
			}
		}
		result += runhistory.terminateAndCount(runcolor, runx) * PENALTY_N3
	}

	// Adjacent modules in column having same color, and finder-like patterns
	for x := int32(0); x < size; x++ {
		var runcolor bool
		var runy int32
		runhistory := newFinderPenalty(size)
		for y := int32(0); y < size; y++ {
			if q.module(x, y) == runcolor {
				runy += 1
				if runy == 5 {
					result += PENALTY_N1
				} else if runy > 5 {
					result += 1
				}
			} else {
				runhistory.addHistory(runy)
				if !runcolor {
					result += runhistory.countPatterns() * PENALTY_N3
				}
				runcolor = q.module(x, y)
				runy = 1
			}
		}
		result += runhistory.terminateAndCount(runcolor, runy) * PENALTY_N3
	}

	// 2*2 blocks of modules having same color
	for y := int32(0); y < size-1; y++ {
		for x := int32(0); x < size-1; x++ {
			color := q.module(x, y)
			if color == q.module(x+1, y) &&
				color == q.module(x, y+1) &&
				color == q.module(x+1, y+1) {
				result += PENALTY_N2
			}
		}
	}

	// TODO: refactor to match closer to the semantics of rust counterpart for map().sum()
	// Balance of dark and light modules
	var dark int32
	for _, mod := range q.modules {
		dark += mathx.BoolToInt32(mod)
	}
	total := size * size // Note that size is odd, so dark/total != 1/2
	// Compute the smallest integer k >= 0 such that (45-5k)% <= dark/total <= (55+5k)%
	k := (mathx.AbsInt32((dark*20-total*10))+total-1)/total - 1
	result += k * PENALTY_N4

	return result
}

/*---- Private helper functions ----*/

// Returns an ascending list of positions of alignment patterns for this version number.
// Each position is in the range [0,177), and are used on both the x and y axes.
// This could be implemented as lookup table of 40 variable-length lists of unsigned bytes.
func (q QrCode) getAlignmentPatternPositions() []int32 {
	ver := q.version.Value()
	if ver == 1 {
		return []int32{}
	} else {
		numalign := int32(ver)/7 + 2
		var step int32
		if ver == 32 {
			step = 26
		} else {
			step = (int32(ver)*4 + numalign*2 + 1) / (numalign*2 - 2) * 2
		}
		result := make([]int32, numalign)
		for i := int32(0); i < numalign-1; i++ {
			result[i] = q.size - 7 - i*step
		}
		result[numalign-1] = 6

		// TODO: refactor to match closer to the semantics of rust counterpart, reverse()
		invertedResult := make([]int32, numalign)
		for i, val := range result {
			invertedResult[numalign-1-int32(i)] = val
		}

		return invertedResult
	}
}

// Returns the number of data bits that can be stored in a QR Code of the given version number, after
// all function modules are excluded. This includes remainder bits, so it might not be a multiple of 8.
// The result is in the range [208, 29648]. This could be implemented as a 40-entry lookup table.
func getNumRawDataModules(v Version) uint {
	ver := uint(v.Value())
	result := (16*ver+128)*ver + 64
	if ver >= 2 {
		numalign := ver/7 + 2
		result -= (25*numalign-10)*numalign - 55
		if ver >= 7 {
			result -= 36
		}
	}
	if result < 208 || result > 29648 {
		panic("result < 208 || result > 29648")
	}

	return result
}

// Returns the number of 8-bit data (i.e. not error correction) codewords contained in any
// QR Code of the given version number and error correction level, with remainder bits discarded.
// This stateless pure function could be implemented as a (40*4)-cell lookup table.
func getNumDataCodewords(ver Version, ecl QrCodeEcc) uint {
	return getNumRawDataModules(ver)/8 - tableGet(ECC_CODEWORDS_PER_BLOCK, ver, ecl)*tableGet(NUM_ERROR_CORRECTION_BLOCKS, ver, ecl)
}

// Returns an entry from the given table based on the given values.
func tableGet(table [4][41]int8, ver Version, ecl QrCodeEcc) uint {
	return uint(table[ecl.Ordinal()][uint(ver.Value())])
}

// Returns a Reed-Solomon ECC generator polynomial for the given degree. This could be
// implemented as a lookup table over all possible parameter values, instead of as an algorithm.
func reedSolomonComputeDivisor(degree uint) []uint8 {
	if degree < 1 || degree > 255 {
		panic("Degree out of range")
	}

	// Polynomial coefficients are stored from highest to lowest power, excluding the leading term which is always 1.
	// For example the polynomial x^3 + 255x^2 + 8x + 93 is stored as the uint8 array [255, 8, 93].
	result := make([]uint8, degree-1)
	result = append(result, 1) // Start off with the monomial x^0

	// Compute the product polynomial (x - r^0) * (x - r^1) * (x - r^2) * ... * (x - r^{degree-1}),
	// and drop the highest monomial term which is always 1x^degree.
	// Note that r = 0x02, which is a generator element of this field GF(2^8/0x11D).
	root := uint8(1)
	for i := uint(0); i < degree; i++ { // Unused variable i
		// Multiply the current product by (x - r^i)
		for j := uint(0); j < degree; j++ {
			result[j] = reedSolomonMultiply(result[j], root)
			if j+1 < uint(len(result)) {
				result[j] ^= result[j+1]
			}
		}
		root = reedSolomonMultiply(root, 0x02)
	}
	return result
}

// Returns the Reed-Solomon error correction codeword for the given data and divisor polynomials.
func reedSolomonComputeRemainder(data []uint8, divisor []uint8) []uint8 {
	result := make([]uint8, len(divisor))
	for _, b := range data { // Polynomial division
		var pop uint8
		pop, result = result[0], result[1:]
		factor := b ^ pop
		result = append(result, 0)

		// TODO: refactor to match closer to the semantics of rust counterpart, zip()
		iterLen := mathx.MinUint(uint(len(result)), uint(len(divisor)))
		for i := uint(0); i < iterLen; i++ {
			// x := result[i]
			y := divisor[i]

			result[i] ^= reedSolomonMultiply(y, factor)
		}
	}

	return result
}

// Returns the product of the two given field elements modulo GF(2^8/0x11D).
// All inputs are valid. This could be implemented as a 256*256 lookup table.
func reedSolomonMultiply(x, y uint8) uint8 {
	// Russian peasant multiplication
	var z uint8
	// TODO: refactor to match closer to the semantics of rust counterpart, rev()
	for i := 7; i > -1; i-- {
		z = (z << 1) ^ ((z >> 7) * 0x1D)
		z ^= ((y >> i) & 1) * x
	}

	return z
}

/*---- Helper struct for get_penalty_score() ----*/

type finderPenalty struct {
	qrSize     int32
	runHistory [7]int32
}

func newFinderPenalty(size int32) *finderPenalty {
	return &finderPenalty{
		qrSize:     size,
		runHistory: [7]int32{},
	}
}

// Pushes the given value to the front and drops the last value.
func (p *finderPenalty) addHistory(currentrunlength int32) {
	if p.runHistory[0] == 0 {
		currentrunlength += p.qrSize // Add light border to initial run
	}
	rh := &p.runHistory
	// TODO: refactor to match closer to the semantics of rust counterpart, rev()
	for i := len(rh) - 1 - 1; i > -1; i-- {
		p.runHistory[i+1] = rh[i]
	}
	rh[0] = currentrunlength
}

// Can only be called immediately after a light run is added, and returns either 0, 1, or 2.
func (p finderPenalty) countPatterns() int32 {
	rh := p.runHistory
	n := rh[1]
	if n > p.qrSize*3 {
		panic("n > p.qrSize*3")
	}
	core := n > 0 && rh[2] == n && rh[3] == n*3 && rh[4] == n && rh[5] == n
	return mathx.BoolToInt32(core && rh[0] >= n*4 && rh[6] >= n) + mathx.BoolToInt32(core && rh[6] >= n*4 && rh[0] >= n)
}

// Must be called at the end of a line (row or column) of modules.
func (p *finderPenalty) terminateAndCount(currentruncolor bool, currentrunlength int32) int32 {
	if currentruncolor { // Terminate dark run
		p.addHistory(currentrunlength)
		currentrunlength = 0
	}
	currentrunlength += p.qrSize // Add light border to final run
	p.addHistory(currentrunlength)
	return p.countPatterns()
}

/*---- Constants and tables ----*/

// For use in getPenaltyScore(), when evaluating which mask is best.
const (
	PENALTY_N1 int32 = 3
	PENALTY_N2 int32 = 3
	PENALTY_N3 int32 = 40
	PENALTY_N4 int32 = 10
)

var (
	ECC_CODEWORDS_PER_BLOCK [4][41]int8 = [4][41]int8{
		// Version: (note that index 0 is for padding, and is set to an illegal value)
		// 0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
		{-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},  // Low
		{-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28}, // Medium
		{-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30}, // Quartile
		{-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30}, // High
	}

	NUM_ERROR_CORRECTION_BLOCKS [4][41]int8 = [4][41]int8{
		// Version: (note that index 0 is for padding, and is set to an illegal value)
		// 0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
		{-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25},              // Low
		{-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49},     // Medium
		{-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68},  // Quartile
		{-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81}, // High
	}
)
