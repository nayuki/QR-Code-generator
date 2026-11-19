/*
 * QR Code generator build (WebAssembly)
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
import {readFile, writeFile} from "node:fs/promises";
import wabtFactory from "wabt";

try {
  const wabt = await wabtFactory();
  const source = await readFile(new URL("qrcodegen.wat", import.meta.url), "utf8");
  const module = wabt.parseWat("qrcodegen.wat", source, {bulk_memory: true});
  try {
    module.resolveNames();
    module.validate();
    const {buffer} = module.toBinary({canonicalize_lebs: true, write_debug_names: false});
    await WebAssembly.compile(buffer);
    await writeFile(new URL("qrcodegen.wasm", import.meta.url), buffer);
    console.log(`Built qrcodegen.wasm (${buffer.length} bytes)`);
  } finally {
    module.destroy();
  }
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
