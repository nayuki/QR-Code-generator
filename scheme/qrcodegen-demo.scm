; 
; QR Code generator demo (Python)
; 
; Run this command-line program with no arguments. The program computes a bunch of demonstration
; QR Codes and prints them to the console. Also, the SVG code for one QR Code is printed as a sample.
; 
; Copyright (c) José Bollo. (MIT License)
; Copyright (c) Project Nayuki. (MIT License)
; https://www.nayuki.io/page/qr-code-generator-library
; 
; Permission is hereby granted, free of charge, to any person obtaining a copy of
; this software and associated documentation files (the "Software"), to deal in
; the Software without restriction, including without limitation the rights to
; use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
; the Software, and to permit persons to whom the Software is furnished to do so,
; subject to the following conditions:
; - The above copyright notice and this permission notice shall be included in
;   all copies or substantial portions of the Software.
; - The Software is provided "as is", without warranty of any kind, express or
;   implied, including but not limited to the warranties of merchantability,
;   fitness for a particular purpose and noninfringement. In no event shall the
;   authors or copyright holders be liable for any claim, damages or other
;   liability, whether in an action of contract, tort or otherwise, arising from,
;   out of or in connection with the Software or the use or other dealings in the
;   Software.
; 

;(import (prefix (QR-code encode) qr-))
(import (prefix (qrcodegen) qr-))

; ---- Utilities ----

(define (to-svg-str qr border)
	;;; Returns a string of SVG code for an image depicting the given QR Code, with the given number
	;;; of border modules. The string always uses Unix newlines (\n), regardless of the platform.
	(if (negative? border)
		(error "Border must be non-negative"))

	(let* ((size  (qr-get-size qr))
	       (parts (let loop ((r '())(y (- size 1))(x (- size 1)))
				(if (negative? y)
					r
					(if (negative? x)
						(loop r (- y 1) (- size 1))
						(let ((n (if (qr-get-module qr x y)
 								`(" " "M" ,(number->string (+ x border)) "," ,(number->string (+ y border)) "h1v1h-1z" . ,r)
								r)))
							(loop n y (- x 1))))))))
	               (apply string-append `(
"<?xml version='1.0' encoding='UTF-8'?>
<!DOCTYPE svg PUBLIC '-//W3C//DTD SVG 1.1//EN' 'http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd'>
<svg xmlns='http://www.w3.org/2000/svg' version='1.1' viewBox='0 0 " ,(number->string (+ size border border)) "  " ,(number->string (+ size border border)) "' stroke='none'>
	<rect width='100%' height='100%' fill='#FFFFFF'/>
	<path d='" ,@parts "' fill='#000000'/>
</svg>"))))


(define (print-qr qr) 
	;;; Prints the given QrCode object to the console.
	(let* ((border 4)
	       (low    (- border))
	       (high   (+ border (qr-get-size qr))))
		(do ((y low (+ y 1)))
			((>= y high))
			(do ((x low (+ x 1)))
				((>= x high))
				(let ((c (if (qr-get-module qr x y) #\space #\x2588)))
					(write-char c)
					(write-char c)))
			(newline))
		(newline)))

; ---- Demos ----

(define (basic-demo) 
	;;; Creates a single QR Code, then prints it to the console.
	(define text  "Hello, world!") ; User-supplied Unicode text
	(define errcorlvl qr-ecc-LOW)  ; Error correction level
	
	; Make and print the QR Code symbol
	(define qr (qr-encode-text text errcorlvl))
	(print-qr qr)
	(display (to-svg-str qr 4))
	(newline))


(define (variety-demo) 
	;;; Creates a variety of QR Codes that exercise different features of the library, and prints each one to the console.
	
	; Numeric mode encoding (3.33 bits per digit)
	(print-qr (qr-encode-text "314159265358979323846264338327950288419716939937510" qr-ecc-MEDIUM))
	
	; Alphanumeric mode encoding (5.5 bits per character)
	(print-qr (qr-encode-text "DOLLAR-AMOUNT:$39.87 PERCENTAGE:100.00% OPERATIONS:+-*/" qr-ecc-HIGH))
	
	; Unicode text as UTF-8
	(print-qr (qr-encode-text "\x3053;\x3093;\x306B;\x3061;\x77;\x61;\x3001;\x4E16;\x754C;\xFF01;\x20;\x3B1;\x3B2;\x3B3;\x3B4;" qr-ecc-QUARTILE))
	
	; Moderately large QR Code using longer text (from Lewis Carroll's Alice in Wonderland)
	(print-qr (qr-encode-text 
		"Alice was beginning to get very tired of sitting by her sister on the bank, \
		 and of having nothing to do: once or twice she had peeped into the book her sister was reading, \
		 but it had no pictures or conversations in it, 'and what is the use of a book,' thought Alice \
		 'without pictures or conversations?' So she was considering in her own mind (as well as she could, \
		 for the hot day made her feel very sleepy and stupid), whether the pleasure of making a \
		 daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly \
		 a White Rabbit with pink eyes ran close by her." qr-ecc-HIGH)))


(define (segment-demo) 
	;;; Creates QR Codes with manually specified segments for better compactness.
	
	; Illustration "silver"
	(let ((silver0 "THE SQUARE ROOT OF 2 IS 1.")
	      (silver1 "41421356237309504880168872420969807856967187537694807317667973799"))
		(print-qr (qr-encode-text (string-append silver0 silver1) qr-ecc-LOW))
		(let ((seg-silver0 (qr-make-segment-alpha-numeric silver0))
		      (seg-silver1 (qr-make-segment-numeric silver1)))
			(print-qr (qr-encode-segments (list seg-silver0 seg-silver1) qr-ecc-LOW))))

	; Illustration "golden"
	(let ((golden0 "Golden ratio \x3C6; = 1.")
	      (golden1 "6180339887498948482045868343656381177203091798057628621354486227052604628189024497072072041893911374")
	      (golden2 "......"))
		(print-qr (qr-encode-text (string-append golden0 golden1 golden2) qr-ecc-LOW))
		(let ((seg-golden0 (qr-make-segment-bytes golden0))
		      (seg-golden1 (qr-make-segment-numeric golden1))
		      (seg-golden2 (qr-make-segment-alpha-numeric golden2)))
			(print-qr (qr-encode-segments (list seg-golden0 seg-golden1 seg-golden2) qr-ecc-LOW))))

	; Illustration "Madoka": kanji, kana, Cyrillic, full-width Latin, Greek characters
	(let ((madoka "\x300C;\x9B54;\x6CD5;\x5C11;\x5973;\x307E;\x3069;\x304B;\x2606;\x30DE;\x30AE;\x30AB;\x300D;\x3063;\x3066;\x3001;\x3000;\x418;\x410;\x418;\x3000;\xFF44;\xFF45;\xFF53;\xFF55;\x3000;\x3BA;\x3B1;\xFF1F;"))
		(print-qr (qr-encode-text madoka qr-ecc-LOW)))
	
	; Kanji mode encoding (13 bits per character)
	(let* ((kanjicharbits #(
			0 0 0 0 0 0 0 1 1 0 1 0 1
			1 0 0 0 0 0 0 0 0 0 0 1 0
			0 1 1 1 1 1 1 0 0 0 0 0 0
			0 1 0 1 0 1 1 1 0 1 1 0 1
			0 1 0 1 0 1 1 0 1 0 1 1 1
			0 0 0 0 1 0 1 0 1 1 1 0 0
			0 0 0 0 1 0 1 0 0 0 1 1 1
			0 0 0 0 1 0 0 1 0 1 0 0 1
			0 0 0 0 0 0 1 0 1 1 0 0 1
			0 0 0 0 1 1 0 1 1 1 1 0 1
			0 0 0 0 1 1 0 0 0 1 1 0 1
			0 0 0 0 1 1 0 0 0 1 0 1 0
			0 0 0 0 0 0 0 1 1 0 1 1 0
			0 0 0 0 1 0 1 0 0 0 0 0 1
			0 0 0 0 1 0 1 0 0 0 1 0 0
			0 0 0 0 0 0 0 0 0 0 0 0 1
			0 0 0 0 0 0 0 0 0 0 0 0 0
			0 0 0 1 0 0 1 0 0 1 0 0 1
			0 0 0 1 0 0 1 0 0 0 0 0 0
			0 0 0 1 0 0 1 0 0 1 0 0 1
			0 0 0 0 0 0 0 0 0 0 0 0 0
			0 0 0 0 1 0 0 0 0 0 1 0 0
			0 0 0 0 1 0 0 0 0 0 1 0 1
			0 0 0 0 1 0 0 0 1 0 0 1 1
			0 0 0 0 1 0 0 0 1 0 1 0 1
			0 0 0 0 0 0 0 0 0 0 0 0 0
			0 0 0 1 0 0 0 0 0 1 0 0 0
			0 0 0 0 1 1 1 1 1 1 1 1 1
			0 0 0 0 0 0 0 0 0 1 0 0 0))
	       (kanjiseg (qr-make-segment qr-mode-KANJI (quotient (vector-length kanjicharbits) 13) kanjicharbits)))
		(print-qr (qr-encode-segments (list kanjiseg) qr-ecc-LOW))))

(define (mask-demo) 
	;;; Creates QR Codes with the same size and contents but different mask patterns.
	
	; Project Nayuki URL
	(let ((segs (list (qr-make-segment-text "https://www.nayuki.io/"))))
		(print-qr (qr-encode-segments segs qr-ecc-HIGH 'mask -1))  ; Automatic mask
		(print-qr (qr-encode-segments segs qr-ecc-HIGH 'mask 3)))  ; Force mask 3
	
	; Chinese text as UTF-8
	(let ((segs (list (qr-make-segment-text "\
		\x7DAD;\x57FA;\x767E;\x79D1;\xFF08;\x0057;\x0069;\x006B;\x0069;\x0070;\x0065;\x0064;\x0069;\x0061;\xFF0C;\
		\x8046;\x807D;\x0069;\x002F;\x02CC;\x0077;\x026A;\x006B;\x1D7B;\x02C8;\x0070;\x0069;\x02D0;\x0064;\x0069;\
		\x002E;\x0259;\x002F;\xFF09;\x662F;\x4E00;\x500B;\x81EA;\x7531;\x5167;\x5BB9;\x3001;\x516C;\x958B;\x7DE8;\
		\x8F2F;\x4E14;\x591A;\x8A9E;\x8A00;\x7684;\x7DB2;\x8DEF;\x767E;\x79D1;\x5168;\x66F8;\x5354;\x4F5C;\x8A08;\
		\x756B;"))))
		(print-qr (qr-encode-segments segs qr-ecc-MEDIUM 'mask 0))  ; Force mask 0
		(print-qr (qr-encode-segments segs qr-ecc-MEDIUM 'mask 1))  ; Force mask 1
		(print-qr (qr-encode-segments segs qr-ecc-MEDIUM 'mask 5))  ; Force mask 5
		(print-qr (qr-encode-segments segs qr-ecc-MEDIUM 'mask 7))))  ; Force mask 7


;;; The main application program.

(basic-demo)
(variety-demo)
(segment-demo)
(mask-demo)


