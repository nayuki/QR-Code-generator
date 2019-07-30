/* 
 * QR Code generator input demo (TypeScript)
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

"use strict";


namespace app {
	
	function initialize(): void {
		getElem("loading").style.display = "none";
		getElem("loaded").style.removeProperty("display");
		let elems = document.querySelectorAll("input[type=number], textarea");
		for (let el of elems) {
			if (el.id.indexOf("version-") != 0)
				(el as any).oninput = redrawQrCode;
		}
		elems = document.querySelectorAll("input[type=radio], input[type=checkbox]");
		for (let el of elems)
			(el as HTMLInputElement).onchange = redrawQrCode;
		redrawQrCode();
	}
	
	
	function redrawQrCode(): void {
		// Show/hide rows based on bitmap/vector image output
		const bitmapOutput: boolean = getInput("output-format-bitmap").checked;
		const scaleRow : HTMLElement = getElem("scale-row");
		const svgXmlRow: HTMLElement = getElem("svg-xml-row");
		if (bitmapOutput) {
			scaleRow.style.removeProperty("display");
			svgXmlRow.style.display = "none";
		} else {
			scaleRow.style.display = "none";
			svgXmlRow.style.removeProperty("display");
		}
		const svgXml = getElem("svg-xml-output") as HTMLTextAreaElement;
		svgXml.value = "";
		
		// Reset output images in case of early termination
		const canvas = getElem("qrcode-canvas") as HTMLCanvasElement;
		const svg = (document.getElementById("qrcode-svg") as Element) as SVGElement;
		canvas.style.display = "none";
		svg.style.display = "none";
		
		// Returns a QrCode.Ecc object based on the radio buttons in the HTML form.
		function getInputErrorCorrectionLevel(): qrcodegen.QrCode.Ecc {
			if (getInput("errcorlvl-medium").checked)
				return qrcodegen.QrCode.Ecc.MEDIUM;
			else if (getInput("errcorlvl-quartile").checked)
				return qrcodegen.QrCode.Ecc.QUARTILE;
			else if (getInput("errcorlvl-high").checked)
				return qrcodegen.QrCode.Ecc.HIGH;
			else  // In case no radio button is depressed
				return qrcodegen.QrCode.Ecc.LOW;
		}
		
		// Get form inputs and compute QR Code
		const ecl: qrcodegen.QrCode.Ecc = getInputErrorCorrectionLevel();
		const text: string = (getElem("text-input") as HTMLTextAreaElement).value;
		const segs: Array<qrcodegen.QrSegment> = qrcodegen.QrSegment.makeSegments(text);
		const minVer: number = parseInt(getInput("version-min-input").value, 10);
		const maxVer: number = parseInt(getInput("version-max-input").value, 10);
		const mask: number = parseInt(getInput("mask-input").value, 10);
		const boostEcc: boolean = getInput("boost-ecc-input").checked;
		const qr: qrcodegen.QrCode = qrcodegen.QrCode.encodeSegments(segs, ecl, minVer, maxVer, mask, boostEcc);
		
		// Draw image output
		const border: number = parseInt(getInput("border-input").value, 10);
		if (border < 0 || border > 100)
			return;
		if (bitmapOutput) {
			const scale: number = parseInt(getInput("scale-input").value, 10);
			if (scale <= 0 || scale > 30)
				return;
			qr.drawCanvas(scale, border, canvas);
			canvas.style.removeProperty("display");
		} else {
			const code: string = qr.toSvgString(border);
			const viewBox: string = (/ viewBox="([^"]*)"/.exec(code) as RegExpExecArray)[1];
			const pathD: string = (/ d="([^"]*)"/.exec(code) as RegExpExecArray)[1];
			svg.setAttribute("viewBox", viewBox);
			(svg.querySelector("path") as Element).setAttribute("d", pathD);
			svg.style.removeProperty("display");
			svgXml.value = qr.toSvgString(border);
		}
		
		// Returns a string to describe the given list of segments.
		function describeSegments(segs: Array<qrcodegen.QrSegment>): string {
			if (segs.length == 0)
				return "none";
			else if (segs.length == 1) {
				const mode: qrcodegen.QrSegment.Mode = segs[0].mode;
				const Mode = qrcodegen.QrSegment.Mode;
				if (mode == Mode.NUMERIC     )  return "numeric";
				if (mode == Mode.ALPHANUMERIC)  return "alphanumeric";
				if (mode == Mode.BYTE        )  return "byte";
				if (mode == Mode.KANJI       )  return "kanji";
				return "unknown";
			} else
				return "multiple";
		}
		
		// Returns the number of Unicode code points in the given UTF-16 string.
		function countUnicodeChars(str: string): number {
			let result: number = 0;
			for (let i = 0; i < str.length; i++, result++) {
				const c: number = str.charCodeAt(i);
				if (c < 0xD800 || c >= 0xE000)
					continue;
				else if (0xD800 <= c && c < 0xDC00 && i + 1 < str.length) {  // High surrogate
					i++;
					const d: number = str.charCodeAt(i);
					if (0xDC00 <= d && d < 0xE000)  // Low surrogate
						continue;
				}
				throw "Invalid UTF-16 string";
			}
			return result;
		}
		
		// Show the QR Code symbol's statistics as a string
		getElem("statistics-output").textContent = `QR Code version = ${qr.version}, ` +
			`mask pattern = ${qr.mask}, ` +
			`character count = ${countUnicodeChars(text)},\n` +
			`encoding mode = ${describeSegments(segs)}, ` +
			`error correction = level ${"LMQH".charAt(qr.errorCorrectionLevel.ordinal)}, ` +
			`data bits = ${qrcodegen.QrSegment.getTotalBits(segs, qr.version) as number}.`;
	}
	
	
	export function handleVersionMinMax(which: "min"|"max"): void {
		const minElem: HTMLInputElement = getInput("version-min-input");
		const maxElem: HTMLInputElement = getInput("version-max-input");
		let minVal: number = parseInt(minElem.value, 10);
		let maxVal: number = parseInt(maxElem.value, 10);
		minVal = Math.max(Math.min(minVal, qrcodegen.QrCode.MAX_VERSION), qrcodegen.QrCode.MIN_VERSION);
		maxVal = Math.max(Math.min(maxVal, qrcodegen.QrCode.MAX_VERSION), qrcodegen.QrCode.MIN_VERSION);
		if (which == "min" && minVal > maxVal)
			maxVal = minVal;
		else if (which == "max" && maxVal < minVal)
			minVal = maxVal;
		minElem.value = minVal.toString();
		maxElem.value = maxVal.toString();
		redrawQrCode();
	}
	
	
	function getElem(id: string): HTMLElement {
		const result: HTMLElement|null = document.getElementById(id);
		if (result instanceof HTMLElement)
			return result;
		throw "Assertion error";
	}
	
	
	function getInput(id: string): HTMLInputElement {
		const result: HTMLElement = getElem(id);
		if (result instanceof HTMLInputElement)
			return result;
		throw "Assertion error";
	}
	
	
	initialize();
}
