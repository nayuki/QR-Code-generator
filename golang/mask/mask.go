package mask

// Mask is a number between 0 and 7 (inclusive).
type Mask uint8

// New creates a mask object from the given number.
func New(mask uint8) Mask {
	// Panics if the number is outside the range [0, 7].
	if mask > 7 {
		panic("Mask value out of range")
	}

	return Mask(mask)
}

// Value returns the value, which is in the range [0, 7].
func (m Mask) Value() uint8 {
	return uint8(m)
}
