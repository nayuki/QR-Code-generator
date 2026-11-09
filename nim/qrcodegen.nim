# QR Code generator library (Nim)
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

## A standalone QR Code Model 2 encoder, supporting versions 1 through 40,
## all four error correction levels, and numeric, alphanumeric, byte, Kanji,
## and ECI segments. There are no dependencies outside Nim's standard library.
##
## Use `encodeText` or `encodeBinary` for ordinary data, `encodeSegments` for
## mixed modes, and `initQrCode` for already padded data codewords. QR symbols
## and segments are value objects with read-only accessors and owned sequences.

import std/options

type
  Ecc* {.pure.} = enum
    ## Error correction levels, in increasing order of protection.
    low, medium, quartile, high

  Mode* {.pure.} = enum
    ## Segment modes. Use `modeBits` for the on-wire indicator, not `ord`.
    numeric, alphanumeric, byte, kanji, eci

  DataTooLongError* = object of ValueError
    ## The payload does not fit in the requested version range and ECC level.

  QrSegment* = object
    modeValue: Mode
    numCharsValue: int
    bitData: seq[bool]

  QrCode* = object
    versionValue, sizeValue, maskValue: int
    eclValue: Ecc
    modules: seq[bool]       # Row-major; false means light, true means dark.
    isFunction: seq[bool]    # Construction scratch space; discarded afterward.

const
  minVersion* = 1
  maxVersion* = 40
  alphanumericCharset = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"
  alphanumericValues = block:
    var values: array[char, int8]
    for c in low(char)..high(char): values[c] = -1
    for i, c in alphanumericCharset: values[c] = int8(i)
    values
  # Powers and logarithms of 2 in GF(256/0x11D), computed at compile time.
  # Repeating the powers avoids a modulus in Reed-Solomon multiplication.
  fieldPowers = block:
    var values: array[512, uint8]
    var value = 1
    for i in 0..<values.len:
      values[i] = uint8(value)
      value = (value shl 1) xor ((value shr 7) * 0x11D)
    values
  fieldLogs = block:
    var values: array[uint8, uint8]
    for i in 0..<255: values[fieldPowers[i]] = uint8(i)
    values
  # The first entry in each row is unused; columns 1..40 are QR versions.
  eccCodewordsPerBlock: array[Ecc, array[41, int]] = [
    [-1, 7,10,15,20,26,18,20,24,30,18,20,24,26,30,22,24,28,30,28,28,28,28,30,30,26,28,30,30,30,30,30,30,30,30,30,30,30,30,30,30],
    [-1,10,16,26,18,24,16,18,22,22,26,30,22,22,24,24,28,28,26,26,26,26,28,28,28,28,28,28,28,28,28,28,28,28,28,28,28,28,28,28,28],
    [-1,13,22,18,26,18,24,18,22,20,24,28,26,24,20,30,24,28,28,26,30,28,30,30,30,30,28,30,30,30,30,30,30,30,30,30,30,30,30,30,30],
    [-1,17,28,22,16,22,28,26,26,24,28,24,28,22,24,24,30,28,28,26,28,30,24,30,30,30,30,30,30,30,30,30,30,30,30,30,30,30,30,30,30]]
  numErrorCorrectionBlocks: array[Ecc, array[41, int]] = [
    [-1,1,1,1,1,1,2,2,2,2,4,4,4,4,4,6,6,6,6,7,8,8,9,9,10,12,12,12,13,14,15,16,17,18,19,19,20,21,22,24,25],
    [-1,1,1,1,2,2,4,4,4,5,5,5,8,9,9,10,10,11,13,14,16,17,17,18,20,21,23,25,26,28,29,31,33,35,37,38,40,43,45,47,49],
    [-1,1,1,2,2,4,4,6,6,8,8,8,10,12,16,12,17,16,18,21,20,23,23,25,27,29,34,34,35,38,40,43,45,48,51,53,56,59,62,65,68],
    [-1,1,1,2,4,4,4,5,6,8,8,11,11,16,16,18,16,19,21,25,25,25,34,30,32,35,37,40,42,45,48,51,54,57,60,63,66,70,74,77,81]]

# ---- Segment construction and inspection ----

func getBit(value, index: int): bool {.inline.} =
  ((value shr index) and 1) != 0

func appendBits(buffer: var seq[bool], value, count: int) =
  if count notin 0..31 or value < 0 or (value shr count) != 0:
    raise newException(ValueError, "Bit value or length out of range")
  for i in countdown(count - 1, 0):
    buffer.add getBit(value, i)

func bitCapacity(length, numerator, denominator: int): int =
  if length > (high(int) - denominator + 1) div numerator:
    raise newException(ValueError, "Segment bit length overflows int")
  (length * numerator + denominator - 1) div denominator

func modeBits*(mode: Mode): int =
  ## The four-bit QR mode indicator.
  case mode
  of Mode.numeric: 1
  of Mode.alphanumeric: 2
  of Mode.byte: 4
  of Mode.kanji: 8
  of Mode.eci: 7

func charCountBits*(mode: Mode, version: int): int =
  ## Width of this mode's character-count field at the given QR version.
  if version notin minVersion..maxVersion:
    raise newException(ValueError, "Version out of range")
  let group = (version + 7) div 17
  case mode
  of Mode.numeric: [10, 12, 14][group]
  of Mode.alphanumeric: [9, 11, 13][group]
  of Mode.byte: [8, 16, 16][group]
  of Mode.kanji: [8, 10, 12][group]
  of Mode.eci: 0

func initQrSegment*(mode: Mode, numChars: int, data: openArray[bool]): QrSegment =
  ## Construct a raw segment from MSB-first data bits. Character count must
  ## agree with the mode and data; that semantic constraint is not checked.
  ## The bits are copied. Raises ValueError for a negative character count.
  if numChars < 0:
    raise newException(ValueError, "Negative segment character count")
  QrSegment(modeValue: mode, numCharsValue: numChars, bitData: @data)

func mode*(segment: QrSegment): Mode = segment.modeValue
func numChars*(segment: QrSegment): int = segment.numCharsValue
func getData*(segment: QrSegment): seq[bool] =
  ## Return an owned copy of the segment's bits.
  segment.bitData

func isNumeric*(text: string): bool =
  ## True if every byte is an ASCII digit (also true for the empty string).
  for c in text:
    if c notin {'0'..'9'}: return false
  true

func isAlphanumeric*(text: string): bool =
  ## True if every byte belongs to the QR alphanumeric character set.
  for c in text:
    if c notin {'0'..'9', 'A'..'Z', ' ', '$', '%', '*', '+', '-', '.', '/', ':'}:
      return false
  true

func makeBytes*(data: openArray[uint8]): QrSegment =
  ## Encode bytes verbatim. Does not perform text-mode detection.
  result.modeValue = Mode.byte
  result.numCharsValue = data.len
  result.bitData = newSeqOfCap[bool](bitCapacity(data.len, 8, 1))
  for b in data: result.bitData.appendBits(int(b), 8)

func makeBytes*(data: string): QrSegment =
  ## Encode a Nim string's bytes verbatim, including embedded NULs.
  result.modeValue = Mode.byte
  result.numCharsValue = data.len
  result.bitData = newSeqOfCap[bool](bitCapacity(data.len, 8, 1))
  for c in data: result.bitData.appendBits(ord(c), 8)

func makeNumeric*(digits: string): QrSegment =
  ## Encode ASCII decimal digits; raises ValueError for other characters.
  if not digits.isNumeric:
    raise newException(ValueError, "Non-numeric character in numeric segment")
  result.modeValue = Mode.numeric
  result.numCharsValue = digits.len
  result.bitData = newSeqOfCap[bool](bitCapacity(digits.len, 10, 3))
  var i = 0
  while i < digits.len:
    let count = min(3, digits.len - i)
    var value = 0
    for j in 0..<count: value = value * 10 + ord(digits[i + j]) - ord('0')
    result.bitData.appendBits(value, count * 3 + 1)
    i += count

func makeAlphanumeric*(text: string): QrSegment =
  ## Encode the QR alphanumeric alphabet; raises ValueError otherwise.
  if not text.isAlphanumeric:
    raise newException(ValueError, "Unencodable character in alphanumeric segment")
  result.modeValue = Mode.alphanumeric
  result.numCharsValue = text.len
  result.bitData = newSeqOfCap[bool](bitCapacity(text.len, 11, 2))
  var i = 0
  while i + 1 < text.len:
    let value = int(alphanumericValues[text[i]]) * 45 + int(alphanumericValues[text[i + 1]])
    result.bitData.appendBits(value, 11)
    i += 2
  if i < text.len: result.bitData.appendBits(int(alphanumericValues[text[i]]), 6)

func makeKanji*(shiftJis: openArray[uint8]): QrSegment =
  ## Encode pairs of Shift JIS bytes in the QR Kanji ranges. The caller handles
  ## Unicode-to-Shift-JIS conversion. Invalid pairs raise ValueError.
  if shiftJis.len mod 2 != 0:
    raise newException(ValueError, "Kanji input must contain pairs of Shift JIS bytes")
  result.modeValue = Mode.kanji
  result.numCharsValue = shiftJis.len div 2
  result.bitData = newSeqOfCap[bool](bitCapacity(result.numCharsValue, 13, 1))
  var i = 0
  while i < shiftJis.len:
    let trail = int(shiftJis[i + 1])
    var value = (int(shiftJis[i]) shl 8) or trail
    if trail notin 0x40..0xFC or trail == 0x7F:
      raise newException(ValueError, "Invalid Shift JIS trail byte")
    if value in 0x8140..0x9FFC: value -= 0x8140
    elif value in 0xE040..0xEBBF: value -= 0xC140
    else: raise newException(ValueError, "Shift JIS code outside QR Kanji ranges")
    result.bitData.appendBits((value shr 8) * 192 + (value and 0xFF), 13)
    i += 2

func makeEci*(assignment: int): QrSegment =
  ## An Extended Channel Interpretation segment; assignment must be 0..999999.
  result.modeValue = Mode.eci
  if assignment < 0 or assignment >= 1_000_000:
    raise newException(ValueError, "ECI assignment out of range")
  if assignment < 128:
    result.bitData.appendBits(assignment, 8)
  elif assignment < 16384:
    result.bitData.appendBits(2, 2)
    result.bitData.appendBits(assignment, 14)
  else:
    result.bitData.appendBits(6, 3)
    result.bitData.appendBits(assignment, 21)

func makeSegments*(text: string): seq[QrSegment] =
  ## Automatically choose one numeric/alphanumeric/byte segment, or no segment
  ## for empty text. Nim strings are byte strings; supply UTF-8 for Unicode.
  if text.len == 0: @[]
  elif text.isNumeric: @[makeNumeric(text)]
  elif text.isAlphanumeric: @[makeAlphanumeric(text)]
  else: @[makeBytes(text)]

func getTotalBits*(segments: openArray[QrSegment], version: int): Option[int] =
  ## Total segment/header bits, or none(int) if a count field or int overflows.
  ## Raises ValueError for an invalid version, even when segments is empty.
  if version notin minVersion..maxVersion:
    raise newException(ValueError, "Version out of range")
  var total = 0
  for segment in segments:
    let countBits = segment.modeValue.charCountBits(version)
    if segment.numCharsValue >= (1 shl countBits): return none(int)
    let header = 4 + countBits
    if total > high(int) - header or segment.bitData.len > high(int) - total - header:
      return none(int)
    total += header + segment.bitData.len
  some(total)

# ---- Symbol inspection ----

func version*(qr: QrCode): int = qr.versionValue
func size*(qr: QrCode): int = qr.sizeValue
func errorCorrectionLevel*(qr: QrCode): Ecc = qr.eclValue
func mask*(qr: QrCode): int = qr.maskValue
func getModule*(qr: QrCode, x, y: int): bool =
  ## Read one module. Coordinates outside the symbol are light (false).
  x >= 0 and x < qr.sizeValue and y >= 0 and y < qr.sizeValue and
    qr.modules[y * qr.sizeValue + x]
func getModules*(qr: QrCode): seq[bool] =
  ## Return an owned copy of the row-major modules, with no quiet-zone border.
  qr.modules

# ---- Capacities and Reed-Solomon error correction ----

func numRawDataModules(version: int): int =
  result = (16 * version + 128) * version + 64
  if version >= 2:
    let count = version div 7 + 2
    result -= (25 * count - 10) * count - 55
    if version >= 7: result -= 36

func numDataCodewords(version: int, ecl: Ecc): int =
  numRawDataModules(version) div 8 -
    eccCodewordsPerBlock[ecl][version] * numErrorCorrectionBlocks[ecl][version]

func reedSolomonMultiply(x, y: uint8): uint8 {.inline.} =
  if x == 0 or y == 0: 0'u8
  else: fieldPowers[int(fieldLogs[x]) + int(fieldLogs[y])]

func reedSolomonDivisor(degree: int): seq[uint8] =
  result = newSeq[uint8](degree)
  result[^1] = 1
  var root = 1'u8
  for i in 0..<degree:
    for j in 0..<degree:
      result[j] = reedSolomonMultiply(result[j], root)
      if j + 1 < degree: result[j] = result[j] xor result[j + 1]
    root = reedSolomonMultiply(root, 2)

func reedSolomonRemainder(data, divisor: openArray[uint8]): seq[uint8] =
  result = newSeq[uint8](divisor.len)
  for b in data:
    let factor = b xor result[0]
    for i in 0..<result.len - 1: result[i] = result[i + 1]
    result[^1] = 0
    for i, coefficient in divisor:
      result[i] = result[i] xor reedSolomonMultiply(coefficient, factor)

func addEccAndInterleave(data: openArray[uint8], version: int, ecl: Ecc): seq[uint8] =
  let
    blocks = numErrorCorrectionBlocks[ecl][version]
    degree = eccCodewordsPerBlock[ecl][version]
    rawCodewords = numRawDataModules(version) div 8
    shortBlocks = blocks - rawCodewords mod blocks
    shortDataLength = rawCodewords div blocks - degree
    divisor = reedSolomonDivisor(degree)
  result = newSeq[uint8](rawCodewords)
  var offset = 0
  for i in 0..<blocks:
    let length = shortDataLength + ord(i >= shortBlocks)
    let ecc = reedSolomonRemainder(data.toOpenArray(offset, offset + length - 1), divisor)
    var index = i
    for j in 0..<length:
      if j == shortDataLength: index -= shortBlocks
      result[index] = data[offset + j]
      index += blocks
    for j, b in ecc: result[data.len + i + j * blocks] = b
    offset += length
  assert offset == data.len

# ---- Function patterns, codeword placement, and masking ----

func setFunctionModule(qr: var QrCode, x, y: int, dark: bool) =
  let index = y * qr.sizeValue + x
  qr.modules[index] = dark
  qr.isFunction[index] = true

func drawFinderPattern(qr: var QrCode, x, y: int) =
  for dy in -4..4:
    for dx in -4..4:
      let
        xx = x + dx
        yy = y + dy
        distance = max(abs(dx), abs(dy))
      if xx >= 0 and xx < qr.sizeValue and yy >= 0 and yy < qr.sizeValue:
        qr.setFunctionModule(xx, yy, distance != 2 and distance != 4)

func drawAlignmentPattern(qr: var QrCode, x, y: int) =
  for dy in -2..2:
    for dx in -2..2:
      qr.setFunctionModule(x + dx, y + dy, max(abs(dx), abs(dy)) != 1)

func alignmentPositions(version: int): seq[int] =
  if version == 1: return @[]
  let
    count = version div 7 + 2
    step = (version * 8 + count * 3 + 5) div (count * 4 - 4) * 2
  result = newSeq[int](count)
  result[0] = 6
  var position = version * 4 + 10
  for i in countdown(count - 1, 1):
    result[i] = position
    position -= step

func drawFormatBits(qr: var QrCode, mask: int) =
  let data = ((ord(qr.eclValue) xor 1) shl 3) or mask
  var remainder = data
  for i in 0..<10:
    remainder = (remainder shl 1) xor ((remainder shr 9) * 0x537)
  let bits = ((data shl 10) or remainder) xor 0x5412
  for i in 0..5: qr.setFunctionModule(8, i, getBit(bits, i))
  qr.setFunctionModule(8, 7, getBit(bits, 6))
  qr.setFunctionModule(8, 8, getBit(bits, 7))
  qr.setFunctionModule(7, 8, getBit(bits, 8))
  for i in 9..<15: qr.setFunctionModule(14 - i, 8, getBit(bits, i))
  for i in 0..<8: qr.setFunctionModule(qr.sizeValue - 1 - i, 8, getBit(bits, i))
  for i in 8..<15: qr.setFunctionModule(8, qr.sizeValue - 15 + i, getBit(bits, i))
  qr.setFunctionModule(8, qr.sizeValue - 8, true)

func drawVersion(qr: var QrCode) =
  if qr.versionValue < 7: return
  var remainder = qr.versionValue
  for i in 0..<12:
    remainder = (remainder shl 1) xor ((remainder shr 11) * 0x1F25)
  let bits = (qr.versionValue shl 12) or remainder
  for i in 0..<18:
    let
      a = qr.sizeValue - 11 + i mod 3
      b = i div 3
    qr.setFunctionModule(a, b, getBit(bits, i))
    qr.setFunctionModule(b, a, getBit(bits, i))

func drawFunctionPatterns(qr: var QrCode) =
  for i in 0..<qr.sizeValue:
    qr.setFunctionModule(6, i, i mod 2 == 0)
    qr.setFunctionModule(i, 6, i mod 2 == 0)
  qr.drawFinderPattern(3, 3)
  qr.drawFinderPattern(qr.sizeValue - 4, 3)
  qr.drawFinderPattern(3, qr.sizeValue - 4)
  let positions = alignmentPositions(qr.versionValue)
  for i, x in positions:
    for j, y in positions:
      if not ((i == 0 and j == 0) or (i == 0 and j == positions.high) or
              (i == positions.high and j == 0)):
        qr.drawAlignmentPattern(x, y)
  qr.drawFormatBits(0)  # Reserve format modules; final mask overwrites the bits.
  qr.drawVersion()

func drawCodewords(qr: var QrCode, data: openArray[uint8]) =
  var
    bitIndex = 0
    right = qr.sizeValue - 1
  while right >= 1:
    if right == 6: right = 5
    for vertical in 0..<qr.sizeValue:
      let y = if ((right + 1) and 2) == 0: qr.sizeValue - 1 - vertical else: vertical
      for j in 0..<2:
        let index = y * qr.sizeValue + right - j
        if not qr.isFunction[index] and bitIndex < data.len * 8:
          qr.modules[index] = getBit(int(data[bitIndex shr 3]), 7 - (bitIndex and 7))
          inc bitIndex
    right -= 2
  assert bitIndex == data.len * 8

func applyMask(qr: var QrCode, mask: int) =
  # Select the formula once per mask, outside the loop over all modules.
  template invertWhen(condition: untyped) =
    for y {.inject.} in 0..<qr.sizeValue:
      for x {.inject.} in 0..<qr.sizeValue:
        let index = y * qr.sizeValue + x
        if not qr.isFunction[index] and condition:
          qr.modules[index] = not qr.modules[index]
  case mask
  of 0: invertWhen((x + y) mod 2 == 0)
  of 1: invertWhen(y mod 2 == 0)
  of 2: invertWhen(x mod 3 == 0)
  of 3: invertWhen((x + y) mod 3 == 0)
  of 4: invertWhen((x div 3 + y div 2) mod 2 == 0)
  of 5: invertWhen(x * y mod 2 + x * y mod 3 == 0)
  of 6: invertWhen((x * y mod 2 + x * y mod 3) mod 2 == 0)
  of 7: invertWhen(((x + y) mod 2 + x * y mod 3) mod 2 == 0)
  else: raise newException(ValueError, "Mask out of range")

# ---- Mask penalty scoring ----

func addHistory(history: var array[7, int], length, size: int) =
  let run = length + (if history[0] == 0: size else: 0)
  for i in countdown(6, 1): history[i] = history[i - 1]
  history[0] = run

func countFinderPatterns(history: array[7, int]): int =
  let n = history[1]
  let core = n > 0 and history[2] == n and history[3] == n * 3 and
    history[4] == n and history[5] == n
  ord(core and history[0] >= n * 4 and history[6] >= n) +
    ord(core and history[6] >= n * 4 and history[0] >= n)

func linePenalty(qr: QrCode, start, stride: int): int =
  var
    color = false
    run = 0
    history: array[7, int]
    index = start
  for i in 0..<qr.sizeValue:
    let current = qr.modules[index]
    if current == color:
      inc run
      if run == 5: result += 3
      elif run > 5: inc result
    else:
      history.addHistory(run, qr.sizeValue)
      if not color: result += countFinderPatterns(history) * 40
      color = current
      run = 1
    index += stride
  # Add virtual white borders so scaled finder-like patterns at edges count.
  if color:
    history.addHistory(run, qr.sizeValue)
    run = 0
  history.addHistory(run + qr.sizeValue, qr.sizeValue)
  result += countFinderPatterns(history) * 40

func penaltyScore(qr: QrCode): int =
  for i in 0..<qr.sizeValue:
    result += qr.linePenalty(i * qr.sizeValue, 1) + qr.linePenalty(i, qr.sizeValue)
  for y in 0..<qr.sizeValue - 1:
    for x in 0..<qr.sizeValue - 1:
      let
        index = y * qr.sizeValue + x
        color = qr.modules[index]
      if color == qr.modules[index + 1] and color == qr.modules[index + qr.sizeValue] and
          color == qr.modules[index + qr.sizeValue + 1]:
        result += 3
  var dark = 0
  for color in qr.modules: dark += ord(color)
  let total = qr.sizeValue * qr.sizeValue
  # Equivalent to ceil(abs(dark*20-total*10)/total)-1. Total is always odd.
  result += (abs(dark * 20 - total * 10) - 1) div total * 10

# ---- Public QR encoding functions ----

func initQrCode*(version: int, ecl: Ecc, dataCodewords: openArray[uint8],
                 mask = -1): QrCode =
  ## Low-level construction from exactly the data codewords for this version
  ## and ECC, including segment headers/terminator/padding but excluding ECC.
  ## Mask -1 selects automatically; 0..7 forces a mask. Invalid inputs raise
  ## ValueError. Most callers should use encodeText/Binary/Segments instead.
  if version notin minVersion..maxVersion:
    raise newException(ValueError, "Version out of range")
  if mask notin -1..7:
    raise newException(ValueError, "Mask out of range")
  if dataCodewords.len != numDataCodewords(version, ecl):
    raise newException(ValueError, "Incorrect number of data codewords")
  result.versionValue = version
  result.sizeValue = version * 4 + 17
  result.eclValue = ecl
  result.modules = newSeq[bool](result.sizeValue * result.sizeValue)
  result.isFunction = newSeq[bool](result.modules.len)
  result.drawFunctionPatterns()
  let allCodewords = addEccAndInterleave(dataCodewords, version, ecl)
  result.drawCodewords(allCodewords)
  var selectedMask = mask
  if selectedMask == -1:
    var bestPenalty = high(int)
    for candidate in 0..<8:
      result.applyMask(candidate)
      result.drawFormatBits(candidate)
      let penalty = result.penaltyScore()
      if penalty < bestPenalty:
        selectedMask = candidate
        bestPenalty = penalty
      result.applyMask(candidate)  # Undo XOR before trying the next mask.
  result.maskValue = selectedMask
  result.applyMask(selectedMask)
  result.drawFormatBits(selectedMask)
  result.isFunction = @[]

func encodeSegments*(segments: openArray[QrSegment], ecl = Ecc.medium,
                     minVersion = minVersion, maxVersion = maxVersion,
                     mask = -1, boostEcl = true): QrCode =
  ## Encode custom segments with optional version range, mask, and ECC boosting.
  ## Selects the smallest version that fits. Raises DataTooLongError if none
  ## fits, or ValueError for an invalid version range or mask.
  if minVersion < qrcodegen.minVersion or maxVersion > qrcodegen.maxVersion or minVersion > maxVersion:
    raise newException(ValueError, "Invalid version range")
  if mask notin -1..7:
    raise newException(ValueError, "Mask out of range")
  var
    version = minVersion
    usedBits = 0
  while true:
    let used = getTotalBits(segments, version)
    if used.isSome and used.get <= numDataCodewords(version, ecl) * 8:
      usedBits = used.get
      break
    if version == maxVersion:
      raise newException(DataTooLongError, "Data does not fit the requested QR version range and ECC level")
    inc version
  var actualEcl = ecl
  if boostEcl:
    for candidate in Ecc:
      if candidate > actualEcl and usedBits <= numDataCodewords(version, candidate) * 8:
        actualEcl = candidate
  let capacity = numDataCodewords(version, actualEcl) * 8
  var bits = newSeqOfCap[bool](capacity)
  for segment in segments:
    bits.appendBits(segment.modeValue.modeBits, 4)
    bits.appendBits(segment.numCharsValue, segment.modeValue.charCountBits(version))
    bits.add segment.bitData
  assert bits.len == usedBits
  bits.appendBits(0, min(4, capacity - bits.len))
  bits.appendBits(0, (8 - bits.len mod 8) mod 8)
  var pad = 0xEC
  while bits.len < capacity:
    bits.appendBits(pad, 8)
    pad = pad xor 0xEC xor 0x11
  var data = newSeq[uint8](bits.len div 8)
  for i, bit in bits:
    if bit: data[i shr 3] = data[i shr 3] or uint8(1 shl (7 - (i and 7)))
  initQrCode(version, actualEcl, data, mask)

func encodeText*(text: string, ecl = Ecc.medium,
                 minVersion = minVersion, maxVersion = maxVersion,
                 mask = -1, boostEcl = true): QrCode =
  ## Encode UTF-8 text, automatically selecting numeric/alphanumeric/byte mode.
  ## Text is length-delimited, so NUL bytes are preserved. No ECI is inserted.
  encodeSegments(makeSegments(text), ecl, minVersion, maxVersion, mask, boostEcl)

func encodeBinary*(data: openArray[uint8], ecl = Ecc.medium,
                   minVersion = minVersion, maxVersion = maxVersion,
                   mask = -1, boostEcl = true): QrCode =
  ## Encode bytes in byte mode, even if they happen to be digits or letters.
  encodeSegments([makeBytes(data)], ecl, minVersion, maxVersion, mask, boostEcl)

func encodeBinary*(data: string, ecl = Ecc.medium,
                   minVersion = minVersion, maxVersion = maxVersion,
                   mask = -1, boostEcl = true): QrCode =
  ## Encode a Nim string as binary bytes with no text-mode detection.
  encodeSegments([makeBytes(data)], ecl, minVersion, maxVersion, mask, boostEcl)
