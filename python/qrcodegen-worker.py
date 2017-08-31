# 
# QR Code generator test worker (Python 2, 3)
# 
# This program reads data and encoding parameters from standard input and writes
# QR Code bitmaps to standard output. The I/O format is one integer per line.
# Run with no command line arguments. The program is intended for automated
# batch testing of end-to-end functionality of this QR Code generator library.
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
import sys
import qrcodegen
py3 = sys.version_info.major >= 3


def read_int():
	return int((input if py3 else raw_input)())


def main():
	while True:
		
		# Read data or exit
		length = read_int()
		if length == -1:
			break
		data = [read_int() for _ in range(length)]
		
		# Read encoding parameters
		errcorlvl  = read_int()
		minversion = read_int()
		maxversion = read_int()
		mask       = read_int()
		boostecl   = read_int()
		
		# Make segments for encoding
		if all((b < 128) for b in data):  # Is ASCII
			segs = qrcodegen.QrSegment.make_segments("".join(chr(b) for b in data))
		elif py3:
			segs = [qrcodegen.QrSegment.make_bytes(bytes(data))]
		else:
			segs = [qrcodegen.QrSegment.make_bytes("".join(chr(b) for b in data))]
		
		try:  # Try to make QR Code symbol
			qr = qrcodegen.QrCode.encode_segments(segs, ECC_LEVELS[errcorlvl], minversion, maxversion, mask, boostecl != 0)
			# Print grid of modules
			print(qr.get_version())
			for y in range(qr.get_size()):
				for x in range(qr.get_size()):
					print(1 if qr.get_module(x, y) else 0)
			
		except ValueError as e:
			if e.args[0] != "Data too long":
				raise
			print(-1)
		sys.stdout.flush()


ECC_LEVELS = (
	qrcodegen.QrCode.Ecc.LOW,
	qrcodegen.QrCode.Ecc.MEDIUM,
	qrcodegen.QrCode.Ecc.QUARTILE,
	qrcodegen.QrCode.Ecc.HIGH,
)


if __name__ == "__main__":
	main()
