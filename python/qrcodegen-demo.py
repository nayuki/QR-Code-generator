# 
# QR Code generator demo (Python 2, 3)
# 
# Run this command-line program with no arguments. The program computes a bunch of demonstration
# QR Codes and prints them to the console. Also, the SVG code for one QR Code is printed as a sample.
# 
# Copyright (c) Project Nayuki. (MIT License)
# https://www.nayuki.io/page/qr-code-generator-library
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy of
# this software and associated documentation files (the "Software"), to deal in
# the Software without restriction, including without limitation the rights to
# use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
# the Software, and to permit persons to whom the Software is furnished to do so,
# subject to the following conditions:
# - The above copyright notice and this permission notice shall be included in
#   all copies or substantial portions of the Software.
# - The Software is provided "as is", without warranty of any kind, express or
#   implied, including but not limited to the warranties of merchantability,
#   fitness for a particular purpose and noninfringement. In no event shall the
#   authors or copyright holders be liable for any claim, damages or other
#   liability, whether in an action of contract, tort or otherwise, arising from,
#   out of or in connection with the Software or the use or other dealings in the
#   Software.
# 

from __future__ import print_function
import qrcodegen


# ---- Main program ----

def main():
	"""The main application program."""
	do_basic_demo()
	do_variety_demo()
	do_segment_demo()


def do_basic_demo():
	"""Creates a single QR Code, then prints it to the console."""
	text = u"Hello, world!"               # User-supplied Unicode text
	errcorlvl = qrcodegen.QrCode.Ecc.LOW  # Error correction level
	qr = qrcodegen.QrCode.encode_text(text, errcorlvl)
	print_qr(qr)
	print(qr.to_svg_str(4))


def do_variety_demo():
	"""Creates a variety of QR Codes that exercise different features of the library, and prints each one to the console."""
	
	# Project Nayuki URL
	qr = qrcodegen.QrCode.encode_text("https://www.nayuki.io/", qrcodegen.QrCode.Ecc.HIGH)
	qr = qrcodegen.QrCode(qrcode=qr, mask=3)  # Change mask, forcing to mask #3
	print_qr(qr)
	
	# Numeric mode encoding (3.33 bits per digit)
	qr = qrcodegen.QrCode.encode_text("314159265358979323846264338327950288419716939937510", qrcodegen.QrCode.Ecc.MEDIUM)
	print_qr(qr)
	
	# Alphanumeric mode encoding (5.5 bits per character)
	qr = qrcodegen.QrCode.encode_text("DOLLAR-AMOUNT:$39.87 PERCENTAGE:100.00% OPERATIONS:+-*/", qrcodegen.QrCode.Ecc.HIGH)
	print_qr(qr)
	
	# Unicode text as UTF-8, and different masks
	qr = qrcodegen.QrCode.encode_text(u"\u3053\u3093\u306B\u3061\u0077\u0061\u3001\u4E16\u754C\uFF01\u0020\u03B1\u03B2\u03B3\u03B4", qrcodegen.QrCode.Ecc.QUARTILE)
	print_qr(qrcodegen.QrCode(qrcode=qr, mask=0))
	print_qr(qrcodegen.QrCode(qrcode=qr, mask=1))
	print_qr(qrcodegen.QrCode(qrcode=qr, mask=5))
	print_qr(qrcodegen.QrCode(qrcode=qr, mask=7))
	
	# Moderately large QR Code using longer text (from Lewis Carroll's Alice in Wonderland)
	qr = qrcodegen.QrCode.encode_text(
		"Alice was beginning to get very tired of sitting by her sister on the bank, "
		"and of having nothing to do: once or twice she had peeped into the book her sister was reading, "
		"but it had no pictures or conversations in it, 'and what is the use of a book,' thought Alice "
		"'without pictures or conversations?' So she was considering in her own mind (as well as she could, "
		"for the hot day made her feel very sleepy and stupid), whether the pleasure of making a "
		"daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly "
		"a White Rabbit with pink eyes ran close by her.", qrcodegen.QrCode.Ecc.HIGH)
	print_qr(qr)


def do_segment_demo():
	"""Creates QR Codes with manually specified segments for better compactness."""
	
	# Illustration "silver"
	silver0 = "THE SQUARE ROOT OF 2 IS 1."
	silver1 = "41421356237309504880168872420969807856967187537694807317667973799"
	qr = qrcodegen.QrCode.encode_text(silver0 + silver1, qrcodegen.QrCode.Ecc.LOW)
	print_qr(qr)
	
	segs = [
		qrcodegen.QrSegment.make_alphanumeric(silver0),
		qrcodegen.QrSegment.make_numeric(silver1)]
	qr = qrcodegen.QrCode.encode_segments(segs, qrcodegen.QrCode.Ecc.LOW)
	print_qr(qr)
	
	# Illustration "golden"
	golden0 = u"Golden ratio \u03C6 = 1."
	golden1 = u"6180339887498948482045868343656381177203091798057628621354486227052604628189024497072072041893911374"
	golden2 = u"......"
	qr = qrcodegen.QrCode.encode_text(golden0 + golden1 + golden2, qrcodegen.QrCode.Ecc.LOW)
	print_qr(qr)
	
	segs = [
		qrcodegen.QrSegment.make_bytes(golden0.encode("UTF-8")),
		qrcodegen.QrSegment.make_numeric(golden1),
		qrcodegen.QrSegment.make_alphanumeric(golden2)]
	qr = qrcodegen.QrCode.encode_segments(segs, qrcodegen.QrCode.Ecc.LOW)
	print_qr(qr)
	
	# Illustration "Madoka": kanji, kana, Greek, Cyrillic, full-width Latin characters
	madoka = u"\u300C\u9B54\u6CD5\u5C11\u5973\u307E\u3069\u304B\u2606\u30DE\u30AE\u30AB\u300D\u3063\u3066\u3001\u3000\u0418\u0410\u0418\u3000\uFF44\uFF45\uFF53\uFF55\u3000\u03BA\u03B1\uFF1F"
	qr = qrcodegen.QrCode.encode_text(madoka, qrcodegen.QrCode.Ecc.LOW)
	print_qr(qr)
	
	packedkanjidata = [  # Kanji mode encoding (13 bits per character)
		0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 0, 1,
		1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0,
		0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0,
		0, 1, 0, 1, 0, 1, 1, 1, 0, 1, 1, 0, 1,
		0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, 1, 1,
		0, 0, 0, 0, 1, 0, 1, 0, 1, 1, 1, 0, 0,
		0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1,
		0, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1,
		0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 0, 1,
		0, 0, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1,
		0, 0, 0, 0, 1, 1, 0, 0, 0, 1, 1, 0, 1,
		0, 0, 0, 0, 1, 1, 0, 0, 0, 1, 0, 1, 0,
		0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0,
		0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 1,
		0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1,
		0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0,
		0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 1,
		0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 1,
		0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 1, 0, 1,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0,
		0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0,
	]
	segs = [qrcodegen.QrSegment(qrcodegen.QrSegment.Mode.KANJI, len(packedkanjidata) // 13, packedkanjidata)]
	qr = qrcodegen.QrCode.encode_segments(segs, qrcodegen.QrCode.Ecc.LOW)
	print_qr(qr)



# ---- Utilities ----

def print_qr(qrcode):
	"""Prints the given QrCode object to the console."""
	border = 4
	for y in range(-border, qrcode.get_size() + border):
		for x in range(-border, qrcode.get_size() + border):
			print(u"\u2588 "[qrcode.get_module(x,y)] * 2, end="")
		print()
	print()


# Run the main program
if __name__ == "__main__":
	main()
