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

(cond-expand
   ((library (srfi 151))
      (import (only (srfi 151) bit-set? bit-xor bit-or)))
   (else
      ; ad-hoc implementation without multiply or divide

      (define (bit-set? idx n)
         (define (cuth mask a)
            (if (> mask a)
               a
               (let ((na (cuth (+ mask mask) a)))
                  (if (> mask na) na (- na mask)))))
         (define (bset idx mask)
            (if (positive? idx)
               (bset (- idx 1) (+ mask mask))
               (>= (cuth (+ mask mask) n) mask)))
         (bset idx 1))

      (define (bit-xor a b)
         (define (bxor mask a b)
            (if (or (and (< a mask) (< b mask)) (zero? mask))
               (values 0 a b)
               (let-values (((r na nb) (bxor (+ mask mask) a b)))
                  (if (< na mask)
                     (if (< nb mask)
                        (values r na nb)
                        (values (+ r mask) na (- nb mask)))
                     (if (< nb mask)
                        (values (+ r mask) (- na mask) nb)
                        (values r (- na mask) (- nb mask)))))))
         (let-values (((r x y) (bxor 1 a b))) r))

      (define (bit-or a b)
         (define (bor mask a b)
            (if (or (and (< a mask) (< b mask)) (zero? mask))
               (values 0 a b)
               (let-values (((r na nb) (bor (+ mask mask) a b)))
                  (if (< na mask)
                     (if (< nb mask)
                        (values r na nb)
                        (values (+ r mask) na (- nb mask)))
                     (values (+ r mask) (- na mask) (if (< nb mask) nb (- nb mask)))))))
         (let-values (((r x y) (bor 1 a b))) r))))

