package qrsegment

import "github.com/nayuki/qrcodegen/internal/bitx"

/*---- Bit buffer functionality ----*/

// BitBuffer is an appendable sequence of bits (0s and 1s).
//
// Mainly used by QrSegment.
type BitBuffer []bool

// AppendBits appends the given number of low-order bits of the given value to this buffer.
//
// Requires len &#x2264; 31 and val &lt; 2<sup>len</sup>.
func (b *BitBuffer) AppendBits(val uint32, len uint8) {
	if len > 31 || (val>>len) != 0 {
		panic("Value out of range")
	}

	// TODO: refactor to match closer to the semantics of rust counterpart, rev()
	if len == 0 {
		return
	}
	tmp := make([]bool, len)
	for i := int32(len - 1); i > -1; i-- { // Append bit by bit
		v := bitx.GetBit(val, i)
		tmp[int32(len-1)-i] = v
	}

	res := append([]bool(*b), tmp...)
	*b = BitBuffer(res)
}
