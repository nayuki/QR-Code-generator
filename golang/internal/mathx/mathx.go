package mathx

// TODO: refer to rust wrapping_neg(), not sure if same behavior on edge cases
func WrappingNeg(x int) int {
	if x > 0 {
		return -x
	}

	return x
}

func MinUint(left, right uint) uint {
	if left < right {
		return left
	}

	return right
}

func MaxInt32(left, right int32) int32 {
	if left > right {
		return left
	}

	return right
}

func AbsInt32(x int32) int32 {
	if x < 0 {
		return -x
	}
	return x
}

func BoolToUint(b bool) uint {
	if b {
		return 1
	} else {
		return 0
	}
}

func BoolToInt32(b bool) int32 {
	if b {
		return 1
	} else {
		return 0
	}
}
