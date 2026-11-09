/*
 * QR Code generator end-to-end checks (WebAssembly)
 *
 * Copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/qr-code-generator-library
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * - The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 * - The Software is provided "as is", without warranty of any kind, express or
 *   implied, including but not limited to the warranties of merchantability,
 *   fitness for a particular purpose and noninfringement. In no event shall the
 *   authors or copyright holders be liable for any claim, damages or other
 *   liability, whether in an action of contract, tort or otherwise, arising from,
 *   out of or in connection with the Software or the use or other dealings in the
 *   Software.
 */

import assert from "node:assert/strict";
import {createQrCodeGenerator, Ecc} from "./qrcodegen.mjs";

const generator = await createQrCodeGenerator();
function signature(qr) {
  let value = 0xCBF29CE484222325n;
  for (const byte of [qr.version, qr.errorCorrectionLevel, qr.mask, ...qr.getModules()])
    value = BigInt.asUintN(64, (value ^ BigInt(byte)) * 0x100000001B3n);
  return value;
}

// Standard text, binary, and manually segmented QR symbols.
const text = generator.encodeText("Hello, world!", {ecl: Ecc.MEDIUM});
assert.equal(signature(text), 0x5E117ADD25D2D0F9n);
const binary = generator.encodeBinary(new Uint8Array([0,128,255]), {ecl: Ecc.MEDIUM});
assert.equal(signature(binary), 0xEB1593B15D9412A9n);
const segments = generator.encodeSegments([generator.makeNumeric("3141592653589793238462643383")],
  {ecl: Ecc.HIGH, minVersion: 5, maxVersion: 5, mask: 2, boostEcl: false});
assert.equal(signature(segments), 0x0CA0BC4AC04BAADBn);
assert.match(text.toSvgString(), /viewBox="0 0 29 29"/);
console.log("Passed text, binary, segment, and SVG checks.");
