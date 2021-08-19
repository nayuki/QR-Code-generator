package bitx

// GetBit returns true iff the i'th bit of x is set to 1.
func GetBit(x uint32, i int32) bool {
	return (x>>i)&1 != 0
}
