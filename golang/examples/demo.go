package main

import (
	"errors"
	"fmt"
	"strings"

	"github.com/nayuki/qrcodegen"
	"github.com/nayuki/qrcodegen/mask"
	"github.com/nayuki/qrcodegen/qrcodeecc"
	"github.com/nayuki/qrcodegen/qrsegment"
	"github.com/nayuki/qrcodegen/version"
)

// The main application program.
func main() {
	doBasicDemo()
	doVarietyDemo()
	doSegmentDemo()
	doMaskDemo()
	doErrorBoundDemo()
}

/*---- Demo suite ----*/

// Creates a single QR Code, then prints it to the console.
func doBasicDemo() {
	text := "Hello, world!"      // User-supplied Unicode text
	errcorlevel := qrcodeecc.Low // Error correction level

	// Make and print the QR Code symbol
	qr, _ := qrcodegen.EncodeText(text, errcorlevel)
	printQr(qr)
	svg, _ := toSvgString(qr, 4)
	fmt.Printf("%s", svg)
}

// Creates a variety of QR Codes that exercise different features of the library, and prints each one to the console.
func doVarietyDemo() {
	// Numeric mode encoding (3.33 bits per digit)
	qr, _ := qrcodegen.EncodeText("314159265358979323846264338327950288419716939937510", qrcodeecc.Medium)
	printQr(qr)

	// Alphanumeric mode encoding (5.5 bits per character)
	qr, _ = qrcodegen.EncodeText("DOLLAR-AMOUNT:$39.87 PERCENTAGE:100.00% OPERATIONS:+-*/", qrcodeecc.High)
	printQr(qr)

	// Unicode text as UTF-8
	qr, _ = qrcodegen.EncodeText("こんにちwa、世界！ αβγδ", qrcodeecc.Quartile)
	printQr(qr)

	// Moderately large QR Code using longer text (from Lewis Carroll's Alice in Wonderland)
	qr, _ = qrcodegen.EncodeText(
		strings.Join(
			[]string{
				"Alice was beginning to get very tired of sitting by her sister on the bank, ",
				"and of having nothing to do: once or twice she had peeped into the book her sister was reading, ",
				"but it had no pictures or conversations in it, 'and what is the use of a book,' thought Alice ",
				"'without pictures or conversations?' So she was considering in her own mind (as well as she could, ",
				"for the hot day made her feel very sleepy and stupid), whether the pleasure of making a ",
				"daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly ",
				"a White Rabbit with pink eyes ran close by her.",
			},
			"",
		),
		qrcodeecc.High,
	)
	printQr(qr)
}

// Creates QR Codes with manually specified segments for better compactness.
func doSegmentDemo() {
	// Illustration "silver"
	silver0 := "THE SQUARE ROOT OF 2 IS 1."
	silver1 := "41421356237309504880168872420969807856967187537694807317667973799"
	qr, _ := qrcodegen.EncodeText(
		strings.Join(
			[]string{
				silver0,
				silver1,
			},
			"",
		),
		qrcodeecc.Low,
	)
	printQr(qr)

	segs := []qrsegment.QrSegment{
		qrsegment.MakeAlphanumeric(toChars(silver0)),
		qrsegment.MakeNumeric(toChars(silver1)),
	}
	qr, _ = qrcodegen.EncodeSegments(segs, qrcodeecc.Low)
	printQr(qr)

	// Illustration "golden"
	golden0 := "Golden ratio φ = 1."
	golden1 := "6180339887498948482045868343656381177203091798057628621354486227052604628189024497072072041893911374"
	golden2 := "......"
	qr, _ = qrcodegen.EncodeText(
		strings.Join(
			[]string{
				golden0,
				golden1,
				golden2,
			},
			"",
		),
		qrcodeecc.Low,
	)
	printQr(qr)

	segs = []qrsegment.QrSegment{
		qrsegment.MakeBytes([]byte(golden0)),
		qrsegment.MakeNumeric(toChars(golden1)),
		qrsegment.MakeAlphanumeric(toChars(golden2)),
	}
	qr, _ = qrcodegen.EncodeSegments(segs, qrcodeecc.Low)
	printQr(qr)

	// Illustration "Madoka": kanji, kana, Cyrillic, full-width Latin, Greek characters
	madoka := "「魔法少女まどか☆マギカ」って、　ИАИ　ｄｅｓｕ　κα？"
	qr, _ = qrcodegen.EncodeText(madoka, qrcodeecc.Low)
	printQr(qr)

	kanjichars := []uint32{ // Kanji mode encoding (13 bits per character)
		0x0035, 0x1002, 0x0FC0, 0x0AED, 0x0AD7,
		0x015C, 0x0147, 0x0129, 0x0059, 0x01BD,
		0x018D, 0x018A, 0x0036, 0x0141, 0x0144,
		0x0001, 0x0000, 0x0249, 0x0240, 0x0249,
		0x0000, 0x0104, 0x0105, 0x0113, 0x0115,
		0x0000, 0x0208, 0x01FF, 0x0008,
	}
	bb := qrsegment.BitBuffer{}
	for _, c := range kanjichars {
		bb.AppendBits(c, 13)
	}
	segs = []qrsegment.QrSegment{
		qrsegment.New(
			qrsegment.ModeKanji,
			uint(len(kanjichars)),
			bb,
		),
	}
	qr, _ = qrcodegen.EncodeSegments(segs, qrcodeecc.Low)
	printQr(qr)
}

// Creates QR Codes with the same size and contents but different mask patterns.
func doMaskDemo() {
	// Project Nayuki URL
	segs := qrsegment.MakeSegments(toChars("https://www.nayuki.io/"))
	qr, _ := qrcodegen.EncodeSegmentsAdvanced(segs, qrcodeecc.High, version.Min, version.Max, nil, true) // Automatic mask
	printQr(qr)
	m := mask.New(3)
	qr, _ = qrcodegen.EncodeSegmentsAdvanced(segs, qrcodeecc.High, version.Min, version.Max, &m, true) // Force mask 3
	printQr(qr)

	// Chinese text as UTF-8
	segs = qrsegment.MakeSegments(toChars("維基百科（Wikipedia，聆聽i/ˌwɪkᵻˈpiːdi.ə/）是一個自由內容、公開編輯且多語言的網路百科全書協作計畫"))
	m = mask.New(0)
	qr, _ = qrcodegen.EncodeSegmentsAdvanced(segs, qrcodeecc.Medium, version.Min, version.Max, &m, true) // Force mask 0
	printQr(qr)
	m = mask.New(1)
	qr, _ = qrcodegen.EncodeSegmentsAdvanced(segs, qrcodeecc.Medium, version.Min, version.Max, &m, true) // Force mask 1
	printQr(qr)
	m = mask.New(5)
	qr, _ = qrcodegen.EncodeSegmentsAdvanced(segs, qrcodeecc.Medium, version.Min, version.Max, &m, true) // Force mask 5
	printQr(qr)
	m = mask.New(7)
	qr, _ = qrcodegen.EncodeSegmentsAdvanced(segs, qrcodeecc.Medium, version.Min, version.Max, &m, true) // Force mask 7
	printQr(qr)
}

func doErrorBoundDemo() {
	// numeric only, max 7,089 characters
	var sbNumeric strings.Builder
	sbNumeric.Grow(2953)
	for i := 0; i < 7089; i++ {
		fmt.Fprintf(&sbNumeric, "1")
	}
	numericStr := sbNumeric.String()
	_, err := qrcodegen.EncodeText(numericStr, qrcodeecc.Low)
	fmt.Println(fmt.Errorf("ok to encode numeric string <= 7089 characters, %w", err))

	numericStr += "1"
	_, err = qrcodegen.EncodeText(numericStr, qrcodeecc.Low)
	fmt.Println(fmt.Errorf("failed to encode numeric string > 7089 characters: %w", err))

	// alphanumeric, max 4,296 characters
	var sbAlphanumeric strings.Builder
	sbAlphanumeric.Grow(2953)
	for i := 0; i < 4296; i++ {
		fmt.Fprintf(&sbAlphanumeric, "A")
	}
	alphanumericStr := sbAlphanumeric.String()
	_, err = qrcodegen.EncodeText(alphanumericStr, qrcodeecc.Low)
	fmt.Println(fmt.Errorf("ok to encode alphanumeric string <= 4296 characters, %w", err))

	alphanumericStr += "A"
	_, err = qrcodegen.EncodeText(alphanumericStr, qrcodeecc.Low)
	fmt.Println(fmt.Errorf("failed to encode alphanumeric string > 4296 characters: %w", err))

	// binary/byte, max 2,953 characters
	var sbByte strings.Builder
	sbByte.Grow(2953)
	for i := 0; i < 2953; i++ {
		fmt.Fprintf(&sbByte, "a")
	}
	byteStr := sbByte.String()
	_, err = qrcodegen.EncodeText(byteStr, qrcodeecc.Low)
	fmt.Println(fmt.Errorf("ok to encode byte string <= 2953 characters, %w", err))

	byteStr += "a"
	_, err = qrcodegen.EncodeText(byteStr, qrcodeecc.Low)
	fmt.Println(fmt.Errorf("failed to encode byte string > 2953 characters: %w", err))

	// utf8 kanji, max 984 characters
	var sbKanji strings.Builder
	sbKanji.Grow(2953)
	for i := 0; i < 984; i++ {
		fmt.Fprintf(&sbKanji, "世")
	}
	kanjiStr := sbKanji.String()
	_, err = qrcodegen.EncodeText(kanjiStr, qrcodeecc.Low)
	fmt.Println(fmt.Errorf("ok to encode utf8 kanji string <= 984 characters, %w", err))

	kanjiStr += "世"
	_, err = qrcodegen.EncodeText(kanjiStr, qrcodeecc.Low)
	fmt.Println(fmt.Errorf("failed to encode utf8 kanji string > 984 characters: %w", err))
}

/*---- Utilities ----*/

// Returns a string of SVG code for an image depicting
// the given QR Code, with the given number of border modules.
// The string always uses Unix newlines (\n), regardless of the platform.
func toSvgString(qr *qrcodegen.QrCode, border int32) (string, error) {
	if border < 0 {
		return "", errors.New("Border must be non-negative")
	}

	var sb strings.Builder
	sb.WriteString("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
	sb.WriteString("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n")

	// TODO: check overflow?
	dimension := qr.Size() + border*2
	sb.WriteString(fmt.Sprintf("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 %d %d\" stroke=\"none\">\n", dimension, dimension))
	sb.WriteString("\t<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n")
	sb.WriteString("\t<path d=\"")

	for y := int32(0); y < qr.Size(); y++ {
		for x := int32(0); x < qr.Size(); x++ {
			if qr.GetModule(x, y) {
				if x != 0 || y != 0 {
					sb.WriteString(" ")
				}
				sb.WriteString(fmt.Sprintf("M%d,%dh1v1h-1z", x+border, y+border))
			}
		}
	}
	sb.WriteString("\" fill=\"#000000\"/>\n")
	sb.WriteString("</svg>\n")

	return sb.String(), nil
}

// Prints the given QrCode object to the console.
func printQr(qr *qrcodegen.QrCode) {
	border := int32(4)

	for y := -border; y < qr.Size()+border; y++ {
		for x := -border; x < qr.Size()+border; x++ {
			var c rune
			if qr.GetModule(x, y) {
				c = '█'
			} else {
				c = ' '
			}
			fmt.Printf("%c%c", c, c)
		}
		fmt.Println()
	}
	fmt.Println()
}

// Converts the given borrowed string slice to a new character vector.
func toChars(str string) []rune {
	return []rune(str)
}
