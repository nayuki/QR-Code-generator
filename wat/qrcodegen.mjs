/*
 * QR Code generator host interface (WebAssembly)
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

export const Ecc = Object.freeze({LOW: 0, MEDIUM: 1, QUARTILE: 2, HIGH: 3});
export const Mode = Object.freeze({NUMERIC: 1, ALPHANUMERIC: 2, BYTE: 4, ECI: 7, KANJI: 8});
const utf8 = new TextEncoder();

export class DataTooLongError extends RangeError {
  constructor() {
    super("Data does not fit in the requested QR version range and error correction level");
    this.name = "DataTooLongError";
  }
}

function integer(value, min, max, name) {
  if (!Number.isInteger(value) || value < min || value > max)
    throw new RangeError(`Invalid ${name}`);
  return value;
}

function bytes(data) {
  if (!(data instanceof Uint8Array))
    throw new TypeError("Expected a Uint8Array");
  return data;
}

function textBytes(text) {
  if (typeof text !== "string") throw new TypeError("Expected a string");
  // TextEncoder would silently replace unpaired UTF-16 surrogates with U+FFFD.
  // Match the upstream text encoder's URIError instead of changing the payload.
  for (let i = 0; i < text.length; i++) {
    const unit = text.charCodeAt(i);
    if (unit >= 0xD800 && unit <= 0xDBFF) {
      const next = text.charCodeAt(++i);
      if (!(next >= 0xDC00 && next <= 0xDFFF)) throw new URIError("URI malformed");
    } else if (unit >= 0xDC00 && unit <= 0xDFFF) {
      throw new URIError("URI malformed");
    }
  }
  return utf8.encode(text);
}

function checkStatus(status) {
  if (status === -1) throw new DataTooLongError();
  if (status < 0) throw new RangeError("Invalid QR options, segment data, or memory range");
  return status;
}

// Raw segments allow callers to supply their own MSB-first bit strings.
// Factories on QrCodeGenerator build these bit strings inside WebAssembly.
export class QrSegment {
  constructor(mode, numChars, data, bitLength) {
    if (!Object.values(Mode).includes(mode)) throw new RangeError("Invalid segment mode");
    integer(numChars, 0, 32767, "segment character count");
    integer(bitLength, 0, 32767, "segment bit length");
    if (bytes(data).length < Math.ceil(bitLength / 8)) throw new RangeError("Segment buffer is too short");
    this.mode = mode;
    this.numChars = numChars;
    this.bitLength = bitLength;
    this.data = data.slice(0, Math.ceil(bitLength / 8));
    Object.freeze(this);
  }
}

export class QrCode {
  #modules;
  constructor(wasm) {
    this.size = wasm.get_size();
    this.version = wasm.get_version();
    this.errorCorrectionLevel = wasm.get_error_correction_level();
    this.mask = wasm.get_mask();
    this.#modules = new Uint8Array(wasm.memory.buffer, wasm.get_modules_ptr(), this.size ** 2).slice();
    Object.freeze(this);
  }
  getModule(x, y) {
    return Number.isInteger(x) && Number.isInteger(y) && x >= 0 && y >= 0 && x < this.size && y < this.size
      && this.#modules[y * this.size + x] !== 0;
  }
  getModules() { return this.#modules.slice(); }
  toSvgString(border = 4) {
    integer(border, 0, 10000, "border");
    const dimension = this.size + border * 2;
    const path = [];
    for (let y = 0; y < this.size; y++) {
      for (let x = 0; x < this.size; x++) {
        if (this.getModule(x, y)) path.push(`M${x + border},${y + border}h1v1h-1z`);
      }
    }
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${dimension} ${dimension}" shape-rendering="crispEdges">\n`
      + `<rect width="100%" height="100%" fill="#fff"/>\n<path d="${path.join(" ")}" fill="#000"/>\n</svg>\n`;
  }
}

export class QrCodeGenerator {
  constructor(instance) {
    if (!(instance instanceof WebAssembly.Instance)) throw new TypeError("Expected a WebAssembly.Instance");
    this.wasm = instance.exports;
    Object.freeze(this);
  }
  #options(options) {
    const {ecl = Ecc.MEDIUM, minVersion = 1, maxVersion = 40, mask = -1, boostEcl = true} = options;
    integer(ecl, 0, 3, "error correction level");
    integer(minVersion, 1, 40, "minimum version");
    integer(maxVersion, minVersion, 40, "maximum version");
    integer(mask, -1, 7, "mask");
    if (typeof boostEcl !== "boolean") throw new TypeError("boostEcl must be a boolean");
    return [ecl, minVersion, maxVersion, mask, Number(boostEcl)];
  }
  #copy(data, offset = this.wasm.get_input_ptr()) {
    if (data.length > this.wasm.memory.buffer.byteLength - offset)
      throw new RangeError("Input workspace exhausted");
    new Uint8Array(this.wasm.memory.buffer, offset, data.length).set(data);
    return offset;
  }
  #encode(data, options, entry) {
    const args = this.#options(options);
    const ptr = this.#copy(data);
    checkStatus(this.wasm[entry](ptr, data.length, ...args));
    return new QrCode(this.wasm);
  }
  encodeText(text, options = {}) { return this.#encode(textBytes(text), options, "encode_text"); }
  encodeBinary(data, options = {}) { return this.#encode(bytes(data), options, "encode_binary"); }
  // Low-level API: supply all segment headers, terminator, and padding bytes,
  // excluding ECC. The byte count must exactly match the version/ECC capacity.
  // Unlike encodeSegments(), this does not choose a version or boost the ECC.
  encodeCodewords(version, ecl, dataCodewords, mask = -1) {
    integer(version, 1, 40, "version");
    integer(ecl, 0, 3, "error correction level");
    integer(mask, -1, 7, "mask");
    const data = bytes(dataCodewords);
    const ptr = this.#copy(data);
    checkStatus(this.wasm.encode_codewords(ptr, data.length, version, ecl, mask));
    return new QrCode(this.wasm);
  }
  #make(mode, data) {
    const ptr = this.#copy(data);
    const out = ptr + data.length;
    const capacity = this.wasm.memory.buffer.byteLength - out;
    const bitLength = checkStatus(this.wasm.make_segment(mode, ptr, data.length, out, capacity));
    return new QrSegment(mode, mode === Mode.KANJI ? data.length / 2 : data.length,
      new Uint8Array(this.wasm.memory.buffer, out, Math.ceil(bitLength / 8)), bitLength);
  }
  makeNumeric(text) { return this.#make(Mode.NUMERIC, textBytes(text)); }
  makeAlphanumeric(text) { return this.#make(Mode.ALPHANUMERIC, textBytes(text)); }
  makeBytes(data) { return this.#make(Mode.BYTE, bytes(data)); }
  // Provide Shift JIS bytes; conversion from Unicode is the host's responsibility.
  makeKanji(shiftJis) { return this.#make(Mode.KANJI, bytes(shiftJis)); }
  makeEci(assignment) {
    integer(assignment, 0, 999999, "ECI assignment");
    const ptr = this.wasm.get_input_ptr();
    const bitLength = checkStatus(this.wasm.make_eci(assignment, ptr, 3));
    return new QrSegment(Mode.ECI, 0, new Uint8Array(this.wasm.memory.buffer, ptr, bitLength / 8), bitLength);
  }
  encodeSegments(segments, options = {}) {
    const args = this.#options(options);
    if (!Array.isArray(segments) || segments.some(seg => !(seg instanceof QrSegment)))
      throw new TypeError("Expected an array of QrSegments");
    const ptr = this.wasm.get_input_ptr();
    let end = ptr + segments.length * 16;
    if (end > this.wasm.memory.buffer.byteLength) throw new RangeError("Input workspace exhausted");
    const descriptors = new DataView(this.wasm.memory.buffer);
    segments.forEach((seg, i) => {
      const descriptor = ptr + i * 16;
      this.#copy(seg.data, end);
      descriptors.setInt32(descriptor, seg.mode, true);
      descriptors.setInt32(descriptor + 4, seg.numChars, true);
      descriptors.setInt32(descriptor + 8, end, true);
      descriptors.setInt32(descriptor + 12, seg.bitLength, true);
      end += seg.data.length;
    });
    checkStatus(this.wasm.encode_segments(ptr, segments.length, ...args));
    return new QrCode(this.wasm);
  }
}

// Accept a URL, Response, BufferSource, or compiled WebAssembly.Module.
// The default URL works in both Node.js and browsers with ES module support.
export async function createQrCodeGenerator(source = new URL("qrcodegen.wasm", import.meta.url)) {
  if (typeof source === "string" || source instanceof URL) {
    const url = new URL(source, import.meta.url);
    if (url.protocol === "file:" && typeof process !== "undefined" && process.versions?.node) {
      const {readFile} = await import("node:fs/promises");
      source = await readFile(url);
    } else {
      source = await fetch(url);
    }
  }
  if (typeof Response !== "undefined" && source instanceof Response) {
    if (!source.ok) throw new Error(`Could not load WebAssembly: HTTP ${source.status}`);
    source = await source.arrayBuffer();
  }
  const result = await WebAssembly.instantiate(source, {});
  return new QrCodeGenerator(result instanceof WebAssembly.Instance ? result : result.instance);
}
