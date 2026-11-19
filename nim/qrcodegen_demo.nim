# QR Code generator demo (Nim)
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

import qrcodegen

func toSvgString*(qr: QrCode, border = 4): string =
  ## Render a QR symbol as an SVG with a white quiet zone measured in modules.
  if border < 0 or border > (high(int) - qr.size) div 2:
    raise newException(ValueError, "Invalid border")
  let dimension = qr.size + border * 2
  result = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " &
    $dimension & " " & $dimension & "\" shape-rendering=\"crispEdges\">\n" &
    "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>\n<path d=\""
  for y in 0..<qr.size:
    for x in 0..<qr.size:
      if qr.getModule(x, y):
        result.add "M" & $(x + border) & "," & $(y + border) & "h1v1h-1z "
  result.add "\" fill=\"#000\"/>\n</svg>\n"

when isMainModule:
  import std/os
  try:
    let
      args = commandLineParams()
      text = if args.len > 0: args[0] else: "Hello, world!"
      qr = encodeText(text, Ecc.medium)
    if args.len > 1:
      writeFile(args[1], qr.toSvgString())
      echo "Wrote ", args[1]
    else:
      const border = 4
      for y in -border..<qr.size + border:
        var line = ""
        for x in -border..<qr.size + border:
          line.add(if qr.getModule(x, y): "##" else: "  ")
        echo line
    echo "Version ", qr.version, ", ECC ", qr.errorCorrectionLevel, ", mask ", qr.mask
  except CatchableError as error:
    stderr.writeLine(error.msg)
    quit(QuitFailure)
