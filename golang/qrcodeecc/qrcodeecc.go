package qrcodeecc

/*---- QrCodeEcc functionality ----*/

// QrCodeEcc is the error correction level in a QR Code symbol.
type QrCodeEcc uint

const (
	// Low means the QR Code can tolerate about  7% erroneous codewords.
	Low QrCodeEcc = 0
	// Medium means the QR Code can tolerate about 15% erroneous codewords.
	Medium QrCodeEcc = 1
	// Quartile means the QR Code can tolerate about 25% erroneous codewords.
	Quartile QrCodeEcc = 2
	// High means the QR Code can tolerate about 30% erroneous codewords.
	High QrCodeEcc = 3
)

// Ordinal returns an unsigned 2-bit integer (in the range 0 to 3).
func (q QrCodeEcc) Ordinal() uint {
	switch q {
	case Low:
		return 0
	case Medium:
		return 1
	case Quartile:
		return 2
	case High:
		return 3
	default:
		panic("unknown QrCodeEcc")
	}
}

// FormatBits returns an unsigned 2-bit integer (in the range 0 to 3).
func (q QrCodeEcc) FormatBits() uint8 {
	switch q {
	case Low:
		return 1
	case Medium:
		return 0
	case Quartile:
		return 3
	case High:
		return 2
	default:
		panic("unknown QrCodeEcc")
	}
}
