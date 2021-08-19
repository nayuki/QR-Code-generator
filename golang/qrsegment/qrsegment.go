package qrsegment

import (
	"github.com/nayuki/qrcodegen/version"
)

// The set of all legal characters in alphanumeric mode,
// where each character value maps to the index in the string.
var (
	ALPHANUMERIC_CHARSET = [45]rune{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
		'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
		' ', '$', '%', '*', '+', '-', '.', '/', ':'}
	alphanumericCharset = make(map[rune]int, 45)
)

func init() {
	for i, c := range ALPHANUMERIC_CHARSET {
		alphanumericCharset[c] = i
	}
}

/*---- QrSegment functionality ----*/

// QrSegment is a segment of character/binary/control data in a QR Code symbol.
//
// Instances of this struct are immutable.
//
// The mid-level way to create a segment is to take the payload data
// and call a static factory function such as `QrSegment::make_numeric()`.
// The low-level way to create a segment is to custom-make the bit buffer
// and call the `QrSegment::new()` constructor with appropriate values.
//
// This segment struct imposes no length restrictions, but QR Codes have restrictions.
// Even in the most favorable conditions, a QR Code can only hold 7089 characters of data.
// Any segment longer than this is meaningless for the purpose of generating QR Codes.
type QrSegment struct {
	// The mode indicator of this segment. Accessed through mode().
	mode QrSegmentMode
	// The length of this segment's unencoded data. Measured in characters for
	// numeric/alphanumeric/kanji mode, bytes for byte mode, and 0 for ECI mode.
	// Not the same as the data's bit length. Accessed through num_chars().
	numchars uint
	// The data bits of this segment. Accessed through data().
	data []bool
}

/*---- Static factory functions (mid level) ----*/

// MakeBytes returns a segment representing the given binary data encoded in byte mode.
//
// All input byte slices are acceptable.
//
// Any text string can be converted to UTF-8 bytes and encoded as a byte mode segment.
func MakeBytes(data []uint8) QrSegment {
	bb := make(BitBuffer, 0, len(data)*8)
	for _, b := range data {
		bb.AppendBits(uint32(b), 8)
	}

	return QrSegment{
		mode:     ModeByte,
		numchars: uint(len(data)),
		data:     bb,
	}
}

// MakeNumeric returns a segment representing the given string of decimal digits encoded in numeric mode.
//
// Panics if the string contains non-digit characters.
func MakeNumeric(text []rune) QrSegment {
	bb := make(BitBuffer, 0, len(text)*3+(len(text)+2)/3)
	var accumdata uint32
	var accumcount uint8
	for _, c := range text {
		if '0' > c || c > '9' {
			panic("String contains non-numeric characters")
		}
		accumdata = accumdata*10 + uint32(c) - uint32('0')
		accumcount += 1
		if accumcount == 3 {
			bb.AppendBits(accumdata, 10)
			accumdata = 0
			accumcount = 0
		}
	}
	if accumcount > 0 { // 1 or 2 digits remaining
		bb.AppendBits(accumdata, accumcount*3+1)
	}

	return QrSegment{
		mode:     ModeNumeric,
		numchars: uint(len(text)),
		data:     bb,
	}
}

// MakeAlphanumeric returns a segment representing the given text string encoded in alphanumeric mode.
//
// The characters allowed are: 0 to 9, A to Z (uppercase only), space,
// dollar, percent, asterisk, plus, hyphen, period, slash, colon.
//
// Panics if the string contains non-encodable characters.
func MakeAlphanumeric(text []rune) QrSegment {
	bb := make(BitBuffer, 0, len(text)*5+(len(text)+1)/2)
	var accumdata uint32
	var accumcount uint32
	for _, c := range text {
		idx, ok := alphanumericCharset[c]
		if !ok {
			panic("String contains unencodable characters in alphanumeric mode")
		}
		accumdata = accumdata*45 + uint32(idx)
		accumcount += 1
		if accumcount == 2 {
			bb.AppendBits(accumdata, 11)
			accumdata = 0
			accumcount = 0
		}
	}
	if accumcount > 0 { // 1 character remaining
		bb.AppendBits(accumdata, 6)
	}

	return QrSegment{
		mode:     ModeAlphanumeric,
		numchars: uint(len(text)),
		data:     bb,
	}
}

// MakeSegments returns a list of zero or more segments to represent the given Unicode text string.
//
// The result may use various segment modes and switch
// modes to optimize the length of the bit stream.
func MakeSegments(text []rune) []QrSegment {
	if len(text) == 0 {
		return []QrSegment{}
	}

	var seg QrSegment
	if IsNumeric(text) {
		seg = MakeNumeric(text)
	} else if IsAlphanumeric(text) {
		seg = MakeAlphanumeric(text)
	} else {
		seg = MakeBytes([]byte(string(text)))
	}

	return []QrSegment{seg}
}

// MakeEci returns a segment representing an Extended Channel Interpretation
// (ECI) designator with the given assignment value.
func MakeEci(assignval uint32) QrSegment {
	bb := make(BitBuffer, 0, 24)
	if assignval < (1 << 7) {
		bb.AppendBits(assignval, 0)
	} else if assignval < (1 << 14) {
		bb.AppendBits(2, 2)
		bb.AppendBits(assignval, 14)
	} else if assignval < 1_000_000 {
		bb.AppendBits(6, 3)
		bb.AppendBits(assignval, 21)
	} else {
		panic("ECI assignment value out of range")
	}

	return QrSegment{
		mode:     ModeEci,
		numchars: 0,
		data:     bb,
	}
}

/*---- Constructor (low level) ----*/

// New creates a new QR Code segment with the given attributes and data.
//
// The character count (numchars) must agree with the mode and
// the bit buffer length, but the constraint isn't checked.
func New(mode QrSegmentMode, numchars uint, data []bool) QrSegment {
	return QrSegment{
		mode:     mode,
		numchars: numchars,
		data:     data,
	}
}

/*---- Instance field getters ----*/

// Mode returns the mode indicator of this segment.
func (s QrSegment) Mode() QrSegmentMode {
	return s.mode
}

// NumChars returns the character count field of this segment.
func (s QrSegment) NumChars() uint {
	return s.numchars
}

// Data returns the data bits of this segment.
func (s QrSegment) Data() []bool {
	return s.data
}

/*---- Other static functions ----*/

// GetTotalBits calculates and returns the number of bits needed to encode the given
// segments at the given version. The result is None if a segment has too many
// characters to fit its length field, or the total bits exceeds usize::MAX.
func GetTotalBits(segs []QrSegment, ver version.Version) *uint {
	var result uint
	for _, seg := range segs {
		ccbits := seg.mode.NumCharCountBits(ver)
		// TODO: refactor to match closer to the semantics of rust counterpart to check overflow
		// // ccbits can be as large as 16, but usize can be as small as 16
		// if let Some(limit) = 1usize.checked_shl(u32::from(ccbits)) {
		// 	if seg.numchars >= limit {
		// 		return None;  // The segment's length doesn't fit the field's bit width
		// 	}
		// }
		limit := uint(1) << ccbits
		if seg.numchars >= limit {
			return nil // The segment's length doesn't fit the field's bit width
		}

		result += 4 + uint(ccbits)
		result += uint(len(seg.data))
	}

	return &result
}

// IsNumeric tests whether the given string can be encoded as a segment in numeric mode.
//
// A string is encodable iff each character is in the range 0 to 9.
func IsNumeric(text []rune) bool {
	for _, c := range text {
		if c < '0' || c > '9' {
			return false
		}
	}

	return true
}

// IsAlphanumeric tests whether the given string can be encoded as a segment in alphanumeric mode.
//
// A string is encodable iff each character is in the following set: 0 to 9, A to Z
// (uppercase only), space, dollar, percent, asterisk, plus, hyphen, period, slash, colon.
func IsAlphanumeric(text []rune) bool {
	for _, c := range text {
		_, ok := alphanumericCharset[c]
		if !ok {
			return false
		}
	}

	return true
}
