package version

// Version is a number between 1 and 40 (inclusive).
type Version uint8

const (
	// Min is the minimum version number supported in the QR Code Model 2 standard.
	Min = Version(1)
	// Max is the maximum version number supported in the QR Code Model 2 standard.
	Max = Version(40)
)

// New creates a version object from the given number.
//
// Panics if the number is outside the range [1, 40].
func New(ver uint8) Version {
	if ver < uint8(Min) || ver > uint8(Max) {
		panic("Version number out of range")
	}

	return Version(ver)
}

// Value returns the value, which is in the range [1, 40].
func (v Version) Value() uint8 {
	return uint8(v)
}
