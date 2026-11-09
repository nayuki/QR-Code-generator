# QR Code generator end-to-end checks (Nim)
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

import std/strutils
import qrcodegen

when defined(qrcodegenBench):
  import std/[monotimes, times]
  var checksum = 0
  for (version, length) in [(1,10), (10,250), (25,1200), (40,2953)]:
    let data = repeat('a', length)
    let start = getMonoTime()
    for i in 0..<20:
      let qr = encodeBinary(data, Ecc.low, version, version, boostEcl = false)
      checksum += qr.mask
    let micros = float((getMonoTime() - start).inNanoseconds) / 20_000.0
    echo "Version ", version, ": ", formatFloat(micros, ffDecimal, 1), " us/symbol"
  echo "Checksum: ", checksum
else:
  import qrcodegen_demo
  func signature(qr: QrCode): uint64 =
    result = 0xCBF29CE484222325'u64
    for value in [qr.version, ord(qr.errorCorrectionLevel), qr.mask]:
      result = (result xor uint64(value)) * 0x100000001B3'u64
    for y in 0..<qr.size:
      for x in 0..<qr.size:
        result = (result xor uint64(ord(qr.getModule(x, y)))) * 0x100000001B3'u64

  # Standard text, binary, and manually segmented QR symbols.
  let text = encodeText("Hello, world!", Ecc.medium)
  doAssert text.signature == 0x5E117ADD25D2D0F9'u64
  let binary = encodeBinary([0'u8,128,255], Ecc.medium)
  doAssert binary.signature == 0xEB1593B15D9412A9'u64
  let segments = encodeSegments([makeNumeric("3141592653589793238462643383")],
    Ecc.high, minVersion = 5, maxVersion = 5, mask = 2, boostEcl = false)
  doAssert segments.signature == 0x0CA0BC4AC04BAADB'u64
  doAssert text.toSvgString().contains("viewBox=\"0 0 29 29\"")
  echo "Passed text, binary, segment, and SVG checks."
