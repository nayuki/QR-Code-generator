package qrsegment

import "github.com/nayuki/qrcodegen/version"

/*---- QrSegmentMode functionality ----*/

// QrSegmentMode describes how a segment's data bits are interpreted.
type QrSegmentMode uint32

const (
	ModeNumeric QrSegmentMode = iota
	ModeAlphanumeric
	ModeByte
	ModeKanji
	ModeEci
)

// ModeBits returns an unsigned 4-bit integer value (range 0 to 15)
// representing the mode indicator bits for this mode object.
func (m QrSegmentMode) ModeBits() uint32 {
	switch m {
	case ModeNumeric:
		return 0x1
	case ModeAlphanumeric:
		return 0x2
	case ModeByte:
		return 0x4
	case ModeKanji:
		return 0x8
	case ModeEci:
		return 0x7
	default:
		panic("unknown QrSegmentMode")
	}
}

// NumCharCountBits returns the bit width of the character count field for a segment in this mode
// in a QR Code at the given version number. The result is in the range [0, 16].
func (m QrSegmentMode) NumCharCountBits(ver version.Version) uint8 {
	var tmp [3]uint8

	switch m {
	case ModeNumeric:
		tmp = [3]uint8{10, 12, 14}
	case ModeAlphanumeric:
		tmp = [3]uint8{9, 11, 13}
	case ModeByte:
		tmp = [3]uint8{8, 16, 16}
	case ModeKanji:
		tmp = [3]uint8{8, 10, 12}
	case ModeEci:
		tmp = [3]uint8{0, 0, 0}
	default:
		panic("unknown QrSegmentMode")
	}

	idx := (ver.Value() + 7) / 17
	return tmp[idx]
}
