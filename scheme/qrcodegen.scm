; 
; QR Code generator library (Scheme R7RS)
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

;# ---- code of QR Code library ----

(import (scheme base) (scheme char))

;;; A QR Code symbol, which is a type of two-dimension barcode.
;;; Invented by Denso Wave and described in the ISO/IEC 18004 standard.
;;; Instances of this class represent an immutable square grid of dark and light cells.
;;; The class provides static factory functions to create a QR Code from text or binary data.
;;; The class covers the QR Code Model 2 specification, supporting all versions (sizes)
;;; from 1 to 40, all 4 error correction levels, and 4 character encoding modes.
;;; 
;;; Ways to create a QR Code object:
;;; - High level: Take the payload data and call QrCode.encode_text() or QrCode.encode_binary().
;;; - Mid level: Custom-make the list of segments and call QrCode.encode_segments().
;;; - Low level: Custom-make the array of data codeword bytes (including
;;;   segment headers and final padding, excluding error correction codewords),
;;;   supply the appropriate version number, and call the QrCode() constructor.
;;; (Note that all ways require supplying the desired error correction level.)

(include "bits.scm")
(include "reed-solomon.scm")

; helpers
(define-syntax assert
   (syntax-rules ()
      ((assert expr tag irritants ... ) (unless expr (error tag (quote expr) irritants ...)))
      ((assert expr)                    (assert expr "assertion failed"))))

(define (string-each? pred? str)
   (let ((len (string-length str)))
      (let loop ((idx 0))
         (or (>= idx len) (and (pred? (string-ref str idx)) (loop (+ idx 1)))))))

(define (bytevector-for-each func bv)
   (let ((len (bytevector-length bv)))
      (do ((idx 0 (+ idx 1)))
         ((>= idx len))
         (func (bytevector-u8-ref bv idx)))))

(define (^ x n)
   (if (zero? n)
      1
      (let ((u (^ (square x) (quotient n 2))))
         (if (odd? n)
            (* u x)
            u))))
(define (2^ n)
   (^ 2 n))

; ecc
   ;;; The error correction level in a QR Code symbol.
   ;;;  - ordinal: integer in the range 0 to 3 (unsigned 2-bit integer)
   ;;;  - format-bits: integer in the range 0 to 3 (unsigned 2-bit integer)
   (define-record-type
      <ecc>
      (!ecc! ordinal format-bits)
      ecc?
      (ordinal     ecc-ordinal)
      (format-bits ecc-format-bits))

   (define ecc-LOW      (!ecc! 0 1)) ; The QR Code can tolerate about  7% erroneous codewords
   (define ecc-MEDIUM   (!ecc! 1 0)) ; The QR Code can tolerate about 15% erroneous codewords
   (define ecc-QUARTILE (!ecc! 2 3)) ; The QR Code can tolerate about 25% erroneous codewords
   (define ecc-HIGH     (!ecc! 3 2)) ; The QR Code can tolerate about 30% erroneous codewords

; mode
   ;;; Describes how a segment's data bits are interpreted.
   ;;;  - bits: The mode indicator bits, which is a uint4 value (range 0 to 15)
   ;;;  - counts: Number of character count bits for three different version ranges
   (define-record-type
      <mode>
      (!mode! bits counts)
      mode?
      (bits   mode-bits)
      (counts %mode-counts))

   ;;; # Public constants. Create them outside the class.
   (define mode-NUMERIC      (!mode! #x1 #(10 12 14)))
   (define mode-ALPHANUMERIC (!mode! #x2 #( 9 11 13)))
   (define mode-BYTE         (!mode! #x4 #( 8 16 16)))
   (define mode-KANJI        (!mode! #x8 #( 8 10 12)))
   (define mode-ECI          (!mode! #x7 #( 0  0  0)))

; version
   ; The minimum version number supported in the QR Code Model 2 standard
   (define MIN-VERSION 1)
   ; The maximum version number supported in the QR Code Model 2 standard
   (define MAX-VERSION 40)

   (define ECC_CODEWORDS_PER_BLOCK #(
      ; Version: (note that index 0 is for padding, and is set to an illegal value)
      ; 0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20
      ;    21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40
      #(-1  7 10 15 20 26 18 20 24 30 18 20 24 26 30 22 24 28 30 28 28
         28 28 30 30 26 28 30 30 30 30 30 30 30 30 30 30 30 30 30 30)  ; Low
      #(-1 10 16 26 18 24 16 18 22 22 26 30 22 22 24 24 28 28 26 26 26
         26 28 28 28 28 28 28 28 28 28 28 28 28 28 28 28 28 28 28 28)  ; Medium
      #(-1 13 22 18 26 18 24 18 22 20 24 28 26 24 20 30 24 28 28 26 30
         28 30 30 30 30 28 30 30 30 30 30 30 30 30 30 30 30 30 30 30)  ; Quartile
      #(-1 17 28 22 16 22 28 26 26 24 28 24 28 22 24 24 30 28 28 26 28
         30 24 30 30 30 30 30 30 30 30 30 30 30 30 30 30 30 30 30 30))) ; High

   (define NUM_ERROR_CORRECTION_BLOCKS #(
      ; Version: (note that index 0 is for padding, and is set to an illegal value)
      ; 0 1 2 3 4 5 6 7 8 910 11 12 13 14 15 16 17 18 19 20
      ;   21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40
      #(-1 1 1 1 1 1 2 2 2 2 4  4  4  4  4  6  6  6  6  7  8
         8  9  9 10 12 12 12 13 14 15 16 17 18 19 19 20 21 22 24 25)   ; Low
      #(-1 1 1 1 2 2 4 4 4 5 5  5  8  9  9 10 10 11 13 14 16
         17 17 18 20 21 23 25 26 28 29 31 33 35 37 38 40 43 45 47 49)  ; Medium
      #(-1 1 1 2 2 4 4 6 6 8 8  8 10 12 16 12 17 16 18 21 20
         23 23 25 27 29 34 34 35 38 40 43 45 48 51 53 56 59 62 65 68)  ; Quartile
      #(-1 1 1 2 4 4 4 5 6 8 8 11 11 16 16 18 16 19 21 25 25
         25 34 30 32 35 37 40 42 45 48 51 54 57 60 63 66 70 74 77 81))) ; High

   (define (ecc-codeword-per-block ecc ver)
      (vector-ref (vector-ref ECC_CODEWORDS_PER_BLOCK (ecc-ordinal ecc)) ver))

   (define (ecc-num-error-per-block ecc ver)
      (vector-ref (vector-ref NUM_ERROR_CORRECTION_BLOCKS (ecc-ordinal ecc)) ver))

   ;;; Returns the number of data bits that can be stored in a QR Code of the given version number, after
   ;;; all function modules are excluded. This includes remainder bits, so it might not be a multiple of 8.
   ;;; The result is in the range [208, 29648]. This could be implemented as a 40-entry lookup table.
   (define (get-num-raw-data-modules ver)
      (assert (<= MIN-VERSION ver MAX-VERSION) "Version number out of range")
      (let ((result (+ 64 (* 128 ver) (* 16 (square ver)))))
         (if (< ver 2)
            result
            (let* ((numalign (+ 2 (quotient ver 7)))
                   (cntalign (- (* 25 (square numalign)) (* 10 numalign) 55))
                   (result   (- result cntalign)))
               (if (< ver 7)
                  result
                  (- result 36))))))

   ;;; Returns the number of 8-bit data (i.e. not error correction) codewords contained in any
   ;;; QR Code of the given version number and error correction level, with remainder bits discarded.
   ;;; This stateless pure function could be implemented as a (40*4)-cell lookup table.
   (define (get-num-data-codewords version ecl)
      (let ((numblocks       (ecc-num-error-per-block ecl version))
            (blockecclen     (ecc-codeword-per-block  ecl version))
            (rawcodewords    (quotient (get-num-raw-data-modules version) 8)))
         (- rawcodewords (* numblocks blockecclen))))

   ;;; Returns the bit width of the character count field for a segment in this mode
   ;;; in a QR Code at the given version number. The result is in the range [0, 16].
   (define (mode-char-count-bits mode ver)
      (vector-ref (%mode-counts mode) (quotient (+ ver 7) 17)))

; alpha-numeric encoding
   ;;; Describes precisely all strings that are encodable in alphanumeric mode.
   ;;; Dictionary of "0"->0, "A"->10, "$"->37, etc.
   (define ALPHANUMERIC_ENCODING_TABLE
      (let* ((characters "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:")
             (len        (string-length characters)))
         (do ((r   '()       (cons (cons (string-ref characters idx) idx) r))
              (idx (- len 1) (- idx 1)))
            ((< idx 0) r))))

   ;;; returns the code for the alphanumeric char
   (define (alpha-numeric-code char)
      (cdr (assv char ALPHANUMERIC_ENCODING_TABLE)))

   ;;; Predicate testing if char is a valid alpha-numeric character
   (define (char-alpha-numeric? char)
      (assv char ALPHANUMERIC_ENCODING_TABLE))


   ;;; Predicate testing if str is only made of valid numeric characters
   (define (string-numeric? str)
      (string-each? char-numeric? str))

   ;;; Predicate testing if str is only made of valid alpha-numeric characters
   (define (string-alpha-numeric? str)
      (string-each? char-alpha-numeric? str))

; bitbuffer -naive impl-
   (define-record-type
      <bitbuffer>
      (!bitbuffer! first last)
      bitbuffer?
      (first %bitbuffer-first %bitbuffer-set-first!)
      (last  %bitbuffer-last  %bitbuffer-set-last!))

   (define (bitbuffer)
      (!bitbuffer! #false #false))

   (define (bitbuffer-add-bit bb bit)
      (let ((end (%bitbuffer-last bb))
            (lst (list bit)))
         (if end
            (set-cdr! end lst)
            (%bitbuffer-set-first! bb lst))
         (%bitbuffer-set-last! bb lst)))

   (define (bitbuffer-add bb val nbits)
      (when (positive? nbits)
         (bitbuffer-add bb (quotient val 2) (- nbits 1))
         (bitbuffer-add-bit bb (odd? val))))

   (define (bitbuffer-append bb data)
      (for-each (lambda (value) (bitbuffer-add-bit bb value)) data))

   (define (bitbuffer-data bb)
      (list-copy (or (%bitbuffer-first bb) '())))

   (define (bitbuffer-length bb)
      (length (or (%bitbuffer-first bb) '())))

   (define (bitbuffer-code bb)
      (let* ((lst (or (%bitbuffer-first bb) '()))
             (bv  (make-bytevector (quotient (+ 7 (length lst)) 8) 0)))
         (let loop ((head lst)
                    (idx  0)
                    (val  0)
                    (mask 128))
            (if (null? head)
               (if (not (zero? val))
                  (bytevector-u8-set! bv idx val))
               (let ((nv (if (car head) (+ val mask) val)))
                  (if (> mask 1)
                     (loop (cdr head) idx nv (quotient mask 2))
                     (begin
                        (bytevector-u8-set! bv idx nv)
                        (loop (cdr head) (+ idx 1) 0 128))))))
         bv))

; segment
   ;;; """A segment of character/binary/control data in a QR Code symbol.
   ;;; Instances of this class are immutable.
   ;;; The mid-level way to create a segment is to take the payload data
   ;;; and call a static factory function such as QrSegment.make_numeric().
   ;;; The low-level way to create a segment is to custom-make the bit buffer
   ;;; and call the QrSegment() constructor with appropriate values.
   ;;; This segment class imposes no length restrictions, but QR Codes have restrictions.
   ;;; Even in the most favorable conditions, a QR Code can only hold 7089 characters of data.
   ;;; Any segment longer than this is meaningless for the purpose of generating QR Codes."""

   (define-record-type
      <segment>
      (!segment! mode numchars bitdata)
      segment?

      ;; The <mode> indicator of this segment.
      (mode     segment-mode)

      ;; The length of this segment's unencoded data. Measured in characters for
      ;; numeric/alphanumeric/kanji mode, bytes for byte mode, and 0 for ECI mode.
      ;; Not the same as the data's bit length.
      (numchars segment-num-chars)

      ;; The data bits of this segment
      (bitdata  segment-bitdata))

   ;;; """Creates a new QR Code segment with the given attributes and data.
   ;;; The character count (numchars) must agree with the mode and the bit buffer length,
   ;;; but the constraint isn't checked. The given bit buffer is cloned and stored."""
   (define (make-segment mode numchars bitdata)
      (define (asbool x)
           (case x
                ((0 #f) #f)
                ((1 #t) #t)
                (else (error "invalid bit data"))))
      (let ((data (cond
               ((bitbuffer? bitdata) (bitbuffer-data bitdata))
               ((vector? bitdata)    (map asbool (vector->list bitdata)))
               ((list? bitdata)      (map asbool bitdata))
               (else (error "invalid bitdata")))))
         (!segment! mode numchars data)))


   ;;; Returns a segment representing the given binary data encoded in byte mode.
   ;;; All input byte lists are acceptable: list, vector, bytevector.
   ;;; Any text string can be converted to UTF-8 bytes (string->utf8 s) and encoded
   ;;; as a byte mode segment.
   (define (make-segment-bytes data)
      (let* ((bb  (bitbuffer))
             (len 0)
             (add (lambda (byte)
               (unless (and (number? byte) (<= 0 byte 255))
                  (error "invalid byte data"))
;(for-each display (list "byte = " byte "\n"))
               (bitbuffer-add bb byte 8)
;(for-each display (list "bb = " (map (lambda(x)(if x 1 0))(bitbuffer-data bb)) "\n"))
               (set! len (+ len 1)))))
         (cond
            ((bytevector? data) (bytevector-for-each add data))
            ((list? data)       (for-each add data))
            ((vector? data)     (vector-for-each add data))
            ((string? data)     (bytevector-for-each add (string->utf8 data)))
            (else (error "Invalid data type for bytes")))
         (make-segment mode-BYTE len bb)))



   ;;; """Returns a segment representing the given string of decimal digits encoded in numeric mode."""
   (define (make-segment-numeric str)
      ; check validity
      (assert (string-numeric? str) "String contains non-numeric characters")
      (let ((bb  (bitbuffer))
            (len (string-length str)))
         (let loop ((idx 0))
            (if (< idx len)
               (let ((idx (+ idx 1))
                     (num (digit-value (string-ref str idx))))
                  (if (>= idx len)
                     (bitbuffer-add bb num 4)
                     (let ((idx (+ idx 1))
                           (num (+ (* 10 num) (digit-value (string-ref str idx)))))
                        (if (>= idx len)
                           (bitbuffer-add bb num 7)
                           (let ((num (+ (* 10 num) (digit-value (string-ref str idx)))))
                              (bitbuffer-add bb num 10)
                              (loop (+ idx 1)))))))))
         (make-segment mode-NUMERIC len bb)))



   ;;; """Returns a segment representing the given text string encoded in alphanumeric mode.
   ;;; The characters allowed are: 0 to 9, A to Z (uppercase only), space,
   ;;; dollar, percent, asterisk, plus, hyphen, period, slash, colon."""
   (define (make-segment-alpha-numeric str)
      ; check validity
      (assert (string-alpha-numeric? str) "String contains unencodable characters in alphanumeric mode")
      (let ((bb  (bitbuffer))
            (len (string-length str)))
         (let loop ((idx 0))
            (if (< idx len)
               (let ((idx (+ idx 1))
                     (num (alpha-numeric-code (string-ref str idx))))
                  (if (>= idx len)
                     (bitbuffer-add bb num 6)
                     (let ((num (+ (* 45 num) (alpha-numeric-code (string-ref str idx)))))
                        (bitbuffer-add bb num 11)
                        (loop (+ idx 1)))))))
         (make-segment mode-ALPHANUMERIC len bb)))

   ;;; """Returns a new segment to represent the given Unicode text string.
   ;;; The result may use various segment modes and switch modes to optimize the length of the bit stream."""
   (define (make-segment-text str)
;(for-each display (list "str = " str "\n"))
      (cond
            ((equal? str "")             #false)
            ((string-numeric? str)       (make-segment-numeric str))
            ((string-alpha-numeric? str) (make-segment-alpha-numeric str))
            (else                        (make-segment-bytes (string->utf8 str)))))
   
   ;;; """Returns a segment representing an Extended Channel Interpretation
   ;;; (ECI) designator with the given assignment value."""
   (define (make-segment-eci assignval)
      (assert (<= 0 assignval 999999) "ECI assignment value out of range")
      (let ((bb  (bitbuffer)))
         (cond
            ((< assignval 128)
               (bitbuffer-add bb assignval 8))
            ((< assignval 16384)
               (bitbuffer-add bb #b10 2)
               (bitbuffer-add bb assignval 14))
            (else
               (bitbuffer-add bb #b110 3)
               (bitbuffer-add bb assignval 21)))
         (make-segment mode-ECI 0 bb)))

   (define (segment-add-to-bitbuffer segment bitbuf version)
      (let* ((mode (segment-mode segment))
             (numchars (segment-num-chars segment))
             (modebits (mode-bits mode))
             (charbits (mode-char-count-bits mode version))
             (data     (segment-bitdata segment)))
         (bitbuffer-add    bitbuf modebits 4)
         (bitbuffer-add    bitbuf numchars charbits)
         (bitbuffer-append bitbuf data)
))

;(import (scheme write))
   ;;; Calculates the number of bits needed to encode the segment at
   ;;; the given version. Returns a non-negative number if successful. Otherwise
   ;;; returns #false if a segment has too many characters to fit its length field.
   (define (segment-bits seg version)
;(for-each display (list "seg " seg "\n"))
;(for-each display (list "numcha " (segment-num-chars seg) "\n"))
;(for-each display (list "data " (segment-bitdata seg) "\n"))
;(for-each display (list "mode " (segment-mode seg) "\n"))
      (let ((ccbits  (mode-char-count-bits (segment-mode seg) version))
            (numcha  (segment-num-chars seg))
            (lendata (length (segment-bitdata seg))))
;(for-each display (list "ccbits " ccbits " numcha " numcha " lendata " lendata "\n"))
         (and (< numcha (2^ ccbits)) (+ 4 ccbits lendata))))




; segment set



   ;;; Calculates the number of bits needed to encode the given segments at
   ;;; the given version. Returns a non-negative number if successful. Otherwise
   ;;; returns #false if a segment has too many characters to fit its length field.
   (define (get-total-bits segments version)
      (let loop ((segments segments)
            (sum      0))
         (if (null? segments)
            sum
            (let ((sb (segment-bits (car segments) version)))
               (and sb (loop (cdr segments) (+ sum sb)))))))








; QR-code
   (define-record-type
      <QR-code>
      (!QR-code! version size errcorlvl mask modules function?)
      QR-code?

      ; The version number of this QR Code, which is between 1 and 40 (inclusive).
      ; This determines the size of this barcode.
      (version   QR-code-version)

      ; The width and height of this QR Code, measured in modules, between
      ; 21 and 177 (inclusive). This is equal to version * 4 + 17.
      (size      QR-code-size)

      ; The error correction level used in this QR Code.
      (errcorlvl QR-code-error-correction-level)

      ; The index of the mask pattern used in this QR Code, which is between 0 and 7 (inclusive).
      ; Even if a QR Code is created with automatic masking requested (mask = -1),
      ; the resulting object still has a mask value between 0 and 7.
      (mask      QR-code-mask %QR-code-mask-set!)
      
      ; The modules of this QR Code (False = light, True = dark).
      ; Immutable after constructor finishes. Accessed through get_module().
      (modules   QR-code-modules)
      
      ; Indicates function modules that are not subjected to masking. Discarded when constructor finishes.
      (function? QR-code-function? %QR-code-function?-set!))


   















; ...















; ....

   ;;; Returns a QR Code representing the given Unicode text string at the given error correction level.
   ;;; As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer
   ;;; Unicode code points (not UTF-16 code units) if the low error correction level is used. The smallest possible
   ;;; QR Code version is automatically chosen for the output. The ECC level of the result may be higher than the
   ;;; ecl argument if it can be done without increasing the version.
   (define (encode-text str ecl)
      (encode-segments (list (make-segment-text str)) ecl))

   ;;; Returns a QR Code representing the given binary data at the given error correction level.
   ;;; This function always encodes using the binary segment mode, not any text mode. The maximum number of
   ;;; bytes allowed is 2953. The smallest possible QR Code version is automatically chosen for the output.
   ;;; The ECC level of the result may be higher than the ecl argument if it can be done without increasing the version.
   (define (encode-binary bin ecl)
      (encode-segments (list (make-segment-bytes bin)) ecl))

   ;;; Returns a QR Code representing the given segments with the given encoding parameters.
   (define (encode-segments segments ecl . rest)
      (let ((minver (cond ((memq 'min-version rest) => cadr)(else MIN-VERSION)))
            (maxver (cond ((memq 'max-version rest) => cadr)(else MAX-VERSION)))
            (mask   (cond ((memq 'mask rest)        => cadr)(else -1)))
            (boost  (cond ((memq 'boost-ecl rest)   => cadr)(else #t))))
         (encode-segments-advanced segments ecl minver maxver mask boost)))

   ;;; Returns a QR Code representing the given segments with the given encoding parameters.
   ;;; The smallest possible QR Code version within the given range is automatically
   ;;; chosen for the output. Iff boostecl is true, then the ECC level of the result
   ;;; may be higher than the ecl argument if it can be done without increasing the
   ;;; version. The mask number is either between 0 to 7 (inclusive) to force that
   ;;; mask, or -1 to automatically choose an appropriate mask (which may be slow).
   ;;; This function allows the user to create a custom sequence of segments that switches
   ;;; between modes (such as alphanumeric and byte) to encode text in less space.
   ;;; This is a mid-level API; the high-level API is encode-text and encode-binary
   (define (encode-segments-advanced segments ecl minversion maxversion mask boostecl)

      (define version #f)
      (define datacapacitybits #f)
      (define datausedbits #f)
      (define bb (bitbuffer))

      (assert (<= MIN-VERSION minversion maxversion MAX-VERSION) "invalid version")
      (assert (<= -1 mask 7) "invalid mask")
   
      ; Find the minimal version number to use
      (let try-version ((vers minversion))
         (set! version vers)
         (set! datacapacitybits (* 8 (get-num-data-codewords version ecl))) ; Number of data bits available
         (set! datausedbits     (get-total-bits segments version))
         (unless (and datausedbits (<= datausedbits datacapacitybits))
            (if (>= version maxversion)
               (error "Segment too long" datausedbits datacapacitybits))
            (try-version (+ version 1))))
      (assert datausedbits)

      ; Increase the error correction level while the data still fits in the current version number
      (when boostecl
         (for-each (lambda (e)
                      (if (<= datausedbits (* 8 (get-num-data-codewords version e)))
                        (set! ecl e)))
            (list ecc-MEDIUM ecc-QUARTILE ecc-HIGH)))

      ; Concatenate all segments to create the data bit string
      (for-each (lambda (seg) (segment-add-to-bitbuffer seg bb version))
         segments)
      (assert (equal? datausedbits (bitbuffer-length bb)))

      ; Add terminator and pad up to a byte if applicable
      (let ((datacapacitybits (* 8 (get-num-data-codewords version ecl))))
         (assert (<= (bitbuffer-length bb) datacapacitybits))
         (bitbuffer-add bb 0 (min 4 (- datacapacitybits (bitbuffer-length bb))))
         (bitbuffer-add bb 0 (modulo (- (bitbuffer-length bb)) 8))
         (assert (zero? (modulo (bitbuffer-length bb) 8)))
         
         ; Pad with alternating bytes until data capacity is reached
         (do ((val #xEC (- #xFD val))) ; #xFD = #xEC + #x11 so it alternates the value
            ((= datacapacitybits (bitbuffer-length bb)))
            (bitbuffer-add bb val 8)))

      ; Create the QR Code object
;(for-each display (list "bb = " (map (lambda(x)(if x 1 0))(bitbuffer-data bb)) "\n"))
      (make-QR-code version ecl (bitbuffer-code bb) mask))


; ---- Private fields ----

#;(define (print-qr qr) 
	;;; Prints the given QrCode object to the console.
	(let* ((border 4)
	       (low    (- border))
	       (high   (+ border (QR-code-size qr))))
		(do ((y low (+ y 1)))
			((>= y high))
			(do ((x low (+ x 1)))
				((>= x high))
				(let ((c (if (QR-code-module qr x y) #\space #\x2588)))
					(write-char c)
					(write-char c)))
			(newline))
		(newline)))


; ---- Constructor (low level) ----

; """Creates a new QR Code with the given version number,
; error correction level, data codeword bytes, and mask number.
; This is a low-level API that most users should not use directly.
; A mid-level API is the encode_segments() function."""
(define (make-QR-code version ecl datacodewords mask)

   ; Check scalar arguments and set fields
   (unless (<= MIN-VERSION version MAX-VERSION)
      (error "Version value out of range"))
   (unless (<= -1 mask 7)
      (error "Mask value out of range"))

   (define size (+ 17 (* 4 version)))
   (define (make-bool-array)
      (list->vector (map (lambda x (make-vector size #f)) (make-list size))))

   (define qrcode (!QR-code! version size ecl mask (make-bool-array) (make-bool-array)))
   
;(for-each display (list "version = " version "\n" "errcorlvl = " (ecc-ordinal ecl) "\n" "data = " datacodewords "\n" "mask = " mask "\n"))

   ; Compute ECC, draw modules
   (draw-function-patterns qrcode)
;(display "=[A]=\n")(print-qr qrcode)
   (let ((allcodewords (add-ecc-and-interleave qrcode datacodewords)))
;(for-each display (list "datacodewords = " datacodewords "\n" "allcodewords = " allcodewords "\n"))
      (draw-codewords qrcode allcodewords))
;(display "=[B]=\n")(print-qr qrcode)

   ; Do masking
   (if (negative? mask) ; Automatically choose best mask
      (let ((minpenalty #x100000000))
         (do ((i 0 (+ i 1)))
            ((= i 8))
            (apply-mask qrcode i)
            (draw-format-bits qrcode i)
            (let ((penalty (get-penalty-score qrcode)))
;(for-each display (list "MASK " i " penalty " penalty "\n"))
;(print-qr qrcode)
               (when (< penalty minpenalty)
                  (%QR-code-mask-set! qrcode i)
                  (set! minpenalty penalty)))
            (apply-mask qrcode i)))) ; Undoes the mask due to XOR
;(for-each display (list "MASK = " (QR-code-mask qrcode) "\n"))
;(display "=[C]=\n")(print-qr qrcode)
   (assert (<= 0 (QR-code-mask qrcode) 7))
   (apply-mask qrcode (QR-code-mask qrcode)) ; Apply the final choice of mask
;(display "=[D]=\n")(print-qr qrcode)
   (draw-format-bits qrcode (QR-code-mask qrcode))
   (%QR-code-function?-set! qrcode #f)
;(display "=[E]=\n")(print-qr qrcode)
   qrcode)




; """Returns the color of the module (pixel) at the given coordinates, which is False
; for light or True for dark. The top left corner has the coordinates (x=0, y=0).
; If the given coordinates are out of bounds, then False (light) is returned."""
(define (QR-code-module qrcode x y)
   (and (< -1 x (QR-code-size qrcode))
        (< -1 y (QR-code-size qrcode))
        (vector-ref (vector-ref (QR-code-modules qrcode) y) x)))




; """Returns a new byte string representing the given data with the appropriate error correction
; codewords appended to it, based on this object's version and error correction level."""
; version: int = self._version
; assert len(data) == (get-num-data-codewords version (QR-code-error-correction-level qrcode))
(define (add-ecc-and-interleave qrcode data)

   ; Calculate parameter numbers
   (define version         (QR-code-version qrcode))
   (define ecl             (QR-code-error-correction-level qrcode))
   (define numblocks       (ecc-num-error-per-block ecl version))
   (define blockecclen     (ecc-codeword-per-block ecl version))
   (define rawcodewords    (quotient (get-num-raw-data-modules version) 8))
   (define numshortblocks  (- numblocks (modulo rawcodewords  numblocks)))
   (define shortblocklen   (quotient rawcodewords numblocks))
   (define rsdiv           (reed-solomon-compute-divisor blockecclen))
   (define dblocklen       (- shortblocklen blockecclen))
   (define blocks          (make-vector numblocks #f))
   (define result          (make-bytevector rawcodewords 0))

   ; Split data into blocks and append ECC to each block
   (let loop ((iblk 0) (pos  0))
      (if (< iblk numblocks)
         (let* ((short? (< iblk numshortblocks))
                (dlen   (if short? dblocklen (+ dblocklen 1)))
                (pend   (+ pos dlen))
                (dat    (bytevector-copy data pos pend))
                (ecc    (reed-solomon-compute-remainder dat rsdiv))
                (blk    (if short?
                            (bytevector-append dat #u8(0) ecc)
                            (bytevector-append dat ecc))))
            (vector-set! blocks iblk blk)
            (loop (+ iblk 1) pend))))

   ; Interleave (not concatenate) the bytes from every block into a single sequence
   (let ((nbytes (bytevector-length (vector-ref blocks 0))))
      (let fill ((ibyte 0)
                 (iblk  0)
                 (pos   0))
         (cond
            ((= iblk numblocks)
               (let ((ibyte (+ ibyte 1)))
                  (if (< ibyte nbytes)
                     (fill ibyte 0 pos))))
            ; Skip the padding byte in short blocks
            ((and (= ibyte dblocklen) (< iblk numshortblocks))
               (fill ibyte (+ iblk 1) pos))
            (else
               (let* ((blk (vector-ref blocks iblk))
                      (val (bytevector-u8-ref blk ibyte)))
                  (bytevector-u8-set! result pos val)
                  (fill ibyte (+ iblk 1) (+ pos 1))))))

      result))


; ---- Private helper methods for constructor: Drawing function modules ----

(define (set-module qrcode x y isdark)
   ; """Sets the color of a module and marks it as a function module.
   ; Only used by the constructor. Coordinates must be in bounds."""
   (vector-set! (vector-ref (QR-code-modules qrcode) y) x isdark))

(define (set-function-module qrcode x y isdark)
   ; """Sets the color of a module and marks it as a function module.
   ; Only used by the constructor. Coordinates must be in bounds."""
   (vector-set! (vector-ref (QR-code-modules qrcode) y) x isdark)
   (vector-set! (vector-ref (QR-code-function? qrcode) y) x #t))

(define (draw-finder-pattern qrcode x y)
   ; """Draws a 9*9 finder pattern including the border separator,
   ; with the center module at (x, y). Modules can be out of bounds."""
   (do ((dy -4 (+ dy 1)))
      ((> dy 4))
      (do ((dx -4 (+ dx 1)))
         ((> dx 4))
         (let ((xx (+ x dx)) (yy (+ y dy)))
            (if (and (< -1 xx (QR-code-size qrcode)) (< -1 yy (QR-code-size qrcode)))
               ;# Chebyshev/infinity norm
               (let ((norm (max (abs dx) (abs dy))))
                  (set-function-module qrcode xx yy (not (= 1 (abs (- norm 3)))))))))))



(define (get-alignment-pattern-positions qrcode)
   ; """Returns an ascending list of positions of alignment patterns for this version number.
   ; Each position is in the range [0,177[, and are used on both the x and y axes.
   ; This could be implemented as lookup table of 40 variable-length lists of integers."""
   (let ((version (QR-code-version qrcode)))
      (if (= version 1)
         '()
         (let* ((numalign (+ (quotient version 7) 2))
                (step     (if (= version 32)
                               26
                               (* 2 (quotient (+ (* 4 version) (* 2 numalign) 1)
                                              (- (* 2 numalign) 2))))))
            (let loop ((i    0)
                             (val  (- (QR-code-size qrcode) 7))
                       (tail '()))
               (if (= numalign (+ i 1))
                  (cons 6 tail)
                  (loop (+ i 1) (- val step) (cons val tail))))))))
      
(define (draw-function-patterns qrcode)
   ; """Reads this object's version field, and draws and marks all function modules."""
   ; Draw horizontal and vertical timing patterns
   (define size (QR-code-size qrcode))
   (do ((i 0 (+ i 1)))
      ((= i size))
         (set-function-module qrcode 6 i (even? i))
         (set-function-module qrcode i 6 (even? i)))
   
;(display "=[1]=\n")(print-qr qrcode)
   ; Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
   (draw-finder-pattern qrcode 3 3)
   (draw-finder-pattern qrcode (- size 4) 3)
   (draw-finder-pattern qrcode 3 (- size 4))
   
;(display "=[2]=\n")(print-qr qrcode)
   ; Draw numerous alignment patterns
   (let ((alignpatpos (get-alignment-pattern-positions qrcode)))
      (do ((ilist alignpatpos (cdr ilist)))
         ((null? ilist))
         (do ((jlist alignpatpos (cdr jlist)))
            ((null? jlist))
            (unless (or (and (eq? ilist alignpatpos) (eq? jlist alignpatpos))
                        (and (eq? ilist alignpatpos) (null? (cdr jlist)))
                        (and (eq? jlist alignpatpos) (null? (cdr ilist))))
                (draw-alignment-pattern qrcode (car ilist) (car jlist))))))

;(display "=[3]=\n")(print-qr qrcode)
   ; Draw configuration data
   (draw-format-bits qrcode 0) ; Dummy mask value, overwritten later in the constructor
;(display "=[4]=\n")(print-qr qrcode)
   (draw-version qrcode))
   

(define (draw-alignment-pattern qrcode x y)
   ; """Draws a 5*5 alignment pattern, with the center module
   ; at (x, y). All modules must be in bounds."""
   (let loop ((dy -2) (dx -2))
;(for-each display (list "dx " dx " dy " dy "\n"))
      (set-function-module qrcode (+ x dx) (+ y dy) (not (= (max (abs dx) (abs dy)) 1)))
      (if (< dx 2)
         (loop dy (+ dx 1))
         (if (< dy 2)
            (loop (+ dy 1) -2)))))
         










(define (draw-format-bits qrcode mask)
   ; """Draws two copies of the format bits (with its own error correction code)
   ; based on the given mask and this object's error correction level field."""
   ; # Calculate error correction code and pack bits
   (define size (QR-code-size qrcode))
   (define data (+ (* 8 (ecc-format-bits (QR-code-error-correction-level qrcode))) mask))
   (define rem  (let loop ((count 10)(rem data))
                     (if (zero? count)
                        rem
                        (loop (- count 1) (bit-xor (* rem 2) (* #x537 (quotient rem 512)))))))
   (define bits (bit-xor (bit-or (* data 1024) rem) #x5412))
   (assert (zero? (quotient bits 32768)))
   
   ; Draw first copy
   (do ((i 0 (+ i 1)))
      ((= i 6))
      (set-function-module qrcode 8 i (bit-set? i bits)))
   (set-function-module qrcode 8 7 (bit-set? 6 bits))
   (set-function-module qrcode 8 8 (bit-set? 7 bits))
   (set-function-module qrcode 7 8 (bit-set? 8 bits))
   (do ((i 9 (+ i 1)))
      ((= i 15))
      (set-function-module qrcode (- 14 i) 8 (bit-set? i bits)))
   
   ; Draw second copy
   (do ((i 0 (+ i 1)))
      ((= i 8))
      (set-function-module qrcode (- size i 1) 8 (bit-set? i bits)))
   (do ((i 8 (+ i 1)))
      ((= i 15))
      (set-function-module qrcode 8 (+ size i -15) (bit-set? i bits)))
   (set-function-module qrcode 8 (- size 8) #true)) ; Always dark


(define (draw-version qrcode)
   ; """Draws two copies of the version bits (with its own error correction code),
   ; based on this object's version field, iff 7 <= version <= 40."""
   (define version (QR-code-version qrcode))

   (unless (< version 7)

      ; Calculate error correction code and pack bits
      (let* ((sz  (- (QR-code-size qrcode) 11))
             (rem (let loop ((count 12)(rem version))
                     (if (zero? count)
                        rem
                        (loop (- count 1) (bit-xor (* rem 2) (* #x1F25 (quotient rem 2048)))))))
             (bits (bit-or (* version 4096) rem)))
         (assert (zero? (quotient bits 262144)))
   
         ; Draw two copies
         (do ((i 0 (+ i 1)))
            ((= i 18))
            (let ((bit (bit-set? i bits))
                  (a   (+ sz (modulo i 3)))
                  (b   (quotient i 3)))
               (set-function-module qrcode a b bit)
               (set-function-module qrcode b a bit))))))




;---- Private helper methods for constructor: Codewords and masking ----

(define (draw-codewords qrcode data)
   ; """Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
   ; data area of this QR Code. Function modules need to be marked off before this is called."""
   (define size (QR-code-size qrcode))
   (define lend (bytevector-length data))
   (define lenb (* lend 8))
   (define modules (QR-code-modules qrcode))
   (define function? (QR-code-function? qrcode))
   (define i 0) ; Bit index into the data

   (assert (= lend (quotient (get-num-raw-data-modules (QR-code-version qrcode)) 8)))

   ; Do the funny zigzag scan
   (do ((right (- size 1) (- right 2))) ; Index of right column in each column pair
      ((<= right 0))
      (let ((right (if (<= right 6) (- right 1) right))) ; Vertical counter
         (do ((vert 0 (+ vert 1)))
            ((>= vert size))
            (do ((j 0 (+ j 1)))
               ((= j 2))
               (let* ((x       (- right j)) ; Actual x coordinate
                      (upward? (even? (quotient (+ right 1) 2)))
                      (y       (if upward? (- size 1 vert) vert))) ; Actual y coordinate
                  (if (and (not (vector-ref (vector-ref function? y) x)) (< i lenb))
                     (let*-values (((ibyte ibit) (floor/ i 8))
                                   ((byte)       (bytevector-u8-ref data ibyte))
                                   ((isdark)     (bit-set? (- 7 ibit) byte)))
                        (vector-set! (vector-ref modules y) x isdark)
                        (set! i (+ i 1)))))))))
                  ; If this QR Code has any remainder bits (0 to 7), they were assigned as
                  ; 0/false/light by the constructor and are left unchanged by this method
   (assert (= i lenb)))


(define MASK-PATTERNS (list
   (lambda (x y)  (modulo (+ x y) 2))
   (lambda (x y)  (modulo y 2))
   (lambda (x y)  (modulo x 3))
   (lambda (x y)  (modulo (+ x y) 3))
   (lambda (x y)  (modulo (+ (quotient x 3) (quotient y 2))  2))
   (lambda (x y)  (let ((p (* x y))) (+ (modulo p 2) (modulo p 3))))
   (lambda (x y)  (let ((p (* x y))) (modulo (+ (modulo p 2) (modulo p 3)) 2)))
   (lambda (x y)  (modulo (+ (modulo (+ x  y) 2) (modulo (* x y) 3)) 2))))



(define (apply-mask qrcode mask)
   ; """XORs the codeword modules in this QR Code with the given mask pattern.
   ; The function modules must be marked and the codeword bits must be drawn
   ; before masking. Due to the arithmetic of XOR, calling _apply_mask() with
   ; the same mask value a second time will undo the mask. A final well-formed
   ; QR Code needs exactly one (not zero, two, etc.) mask applied."""
   (assert (<= 0 mask 7))
   (let ((size      (QR-code-size qrcode))
         (modules   (QR-code-modules qrcode))
         (function? (QR-code-function? qrcode))
         (masker    (list-ref MASK-PATTERNS mask)))
      (do ((y 0 (+ y 1)))
         ((= y size))
         (do ((my (vector-ref modules y))
              (fy (vector-ref function? y))
              (x 0 (+ x 1)))
            ((= x size))
            (if (and (zero? (masker x y))
                     (not (vector-ref fy x)))
               (vector-set! my x (not (vector-ref my x))))))))

; For use in get-penalty-score when evaluating which mask is best.
(define PENALTY-N1  3)
(define PENALTY-N2  3)
(define PENALTY-N3 40)
(define PENALTY-N4 10)

(define (get-penalty-score qrcode)
   ; """Calculates and returns the penalty score based on state of this QR Code's current modules.
   ; This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score."""
   (define result 0)
   (define size (QR-code-size qrcode))
   (define modules (QR-code-modules qrcode))
   
   (define (color x y)
      (vector-ref (vector-ref modules y) x))

   (define (finder-penalty-add-history currentrunlength runhistory)
      (cons (if (null? runhistory) (+ currentrunlength size) currentrunlength) runhistory))

   (define (finder-penalty-count-pattern runhistory)
      ; """Can only be called immediately after a light run is added, and
      ; returns either 0, 1, or 2"""
      (define (at idx)
         (let @ ((head runhistory)
                 (idx idx))
            (if (null? head)
               0
               (if (positive? idx)
                  (@ (cdr head) (- idx 1))
                  (car head)))))
      (let ((n    (at 1)))
         (assert (<= n (* size 3)))
         (let ((core (and (positive? n) (= (at 2) (at 4) (at 5) n) (= (at 3) (* 3 n)))))
            (+ (if (and core (>= (at 0) (* 4 n)) (>= (at 6) n)) 1 0)
               (if (and core (>= (at 6) (* 4 n)) (>= (at 0) n)) 1 0)))))

   (define (finder-penalty-terminate-and-count currentruncolor currentrunlength runhistory)
      ; """Must be called at the end of a line (row or column) of modules. A helper function for get-penalty-score."""
      (finder-penalty-count-pattern
         (finder-penalty-add-history
            (+ size (if currentruncolor 0 currentrunlength))
            (if currentruncolor
               (finder-penalty-add-history currentrunlength runhistory)
               runhistory))))

   ; Adjacent modules in row having same color, and finder-like patterns
   (define penalty1
      (let loop-y ((y 0) (result 0))
         (if (= y size)
            result
            (loop-y (+ y 1)
               (let loop-x ((x 0) (runcolor #f) (runx 0) (runhistory  '()) (result result))
                  (if (= x size)
                     (+ result (* PENALTY-N3 (finder-penalty-terminate-and-count runcolor runx runhistory)))
                     (if (eq? runcolor (color x y))
                        (loop-x (+ x 1) runcolor (+ runx 1) runhistory
                           (if (< runx 4) result (+ result (if (> runx 4) 1 PENALTY-N1))))
                        (let ((runhistory (finder-penalty-add-history runx runhistory)))
                           (loop-x (+ x 1) (not runcolor) 1 runhistory
                              (if runcolor result (+ result (* PENALTY-N3 (finder-penalty-count-pattern runhistory)))))))))))))

   ; Adjacent modules in column having same color, and finder-like patterns
   (define penalty2
      (let loop-x ((x 0) (result 0))
         (if (= x size)
            result
            (loop-x (+ x 1)
               (let loop-y ((y 0) (runcolor #f) (runy 0) (runhistory  '()) (result result))
                  (if (= y size)
                     (+ result (* PENALTY-N3 (finder-penalty-terminate-and-count runcolor runy runhistory)))
                     (if (eq? runcolor (color x y))
                        (loop-y (+ y 1) runcolor (+ runy 1) runhistory
                           (if (< runy 4) result (+ result (if (> runy 4) 1 PENALTY-N1))))
                        (let ((runhistory (finder-penalty-add-history runy runhistory)))
                           (loop-y (+ y 1) (not runcolor) 1 runhistory
                              (if runcolor result (+ result (* PENALTY-N3 (finder-penalty-count-pattern runhistory)))))))))))))

   ; 2*2 blocks of modules having same color
   (define (same-color x y)
      (let ((c (color x y)))
         (and (eq? c (color x (+ y 1)))
              (eq? c (color (+ x 1) y))
              (eq? c (color (+ x 1) (+ y 1))))))
   (define penalty3
      (let loop-y ((y (- size 2)) (result 0))
         (if (negative? y)
            result
            (loop-y (- y 1) (let loop-x ((x (- size 2)) (result result))
               (if (negative? x)
                  result
                  (loop-x (- x 1) (if (same-color x y)
                     (+ result PENALTY-N2)
                     result))))))))
   
   ; Balance of dark and light modules
   (define dark
      (let loop-y ((y 0) (result 0))
         (if (= y size)
            result
            (loop-y (+ y 1)
               (let loop-x ((x 0) (result result))
                  (if (= x size)
                     result
                     (loop-x (+ x 1) (if (color x y) (+ 1 result) result))))))))
   (define total (square size))

   ; Compute the smallest integer k >= 0 such that (45-5k)% <= dark/total <= (55+5k)%
   (define k (- (quotient (+ (abs (- (* dark 20) (* total 10))) total -1) total) 1))
   (define penalty4 (begin (assert (<= 0 k 9)) (* k PENALTY-N4)))

   ; Non-tight upper bound based on default values of PENALTY_N1, ..., N4
   (define result (+ penalty1 penalty2 penalty3 penalty4))
;(for-each display (list "p1 " penalty1 " p2 " penalty2 " p3 " penalty3 " p4 " penalty4 "\n"))
   (assert (<= 0 result 2568888)) ; Non-tight upper bound based on default values of PENALTY-N1, ..., N4
   result)









