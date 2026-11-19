/*
 * QR Code generator demo (WebAssembly)
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
import {writeFile} from "node:fs/promises";
import {createQrCodeGenerator, Ecc} from "./qrcodegen.mjs";

try {
  const [text = "Hello, world!", output] = process.argv.slice(2);
  const generator = await createQrCodeGenerator();
  const qr = generator.encodeText(text, {ecl: Ecc.MEDIUM});
  if (output) {
    await writeFile(output, qr.toSvgString());
    console.log(`Wrote ${output} (version ${qr.version}, ECC ${qr.errorCorrectionLevel}, mask ${qr.mask})`);
  } else {
    const border = 4;
    for (let y = -border; y < qr.size + border; y++) {
      let line = "";
      for (let x = -border; x < qr.size + border; x++) line += qr.getModule(x, y) ? "##" : "  ";
      console.log(line);
    }
    console.log(`Version ${qr.version}, ECC ${qr.errorCorrectionLevel}, mask ${qr.mask}`);
  }
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
