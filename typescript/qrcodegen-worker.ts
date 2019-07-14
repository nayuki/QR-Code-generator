/* 
 * QR Code generator test worker (TypeScript)
 * 
 * This program reads data and encoding parameters from standard input and writes
 * QR Code bitmaps to standard output. The I/O format is one integer per line.
 * Run with no command line arguments. The program is intended for automated
 * batch testing of end-to-end functionality of this QR Code generator library.
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


async function main(): Promise<void> {
	while (true) {
		// Read data or exit
		const length: number = await input.readInt();
		if (length == -1)
			break;
		let data: Array<number> = [];
		for (let i = 0; i < length; i++)
			data.push(await input.readInt());
		
		// Read encoding parameters
		const errCorLvl : number = await input.readInt();
		const minVersion: number = await input.readInt();
		const maxVersion: number = await input.readInt();
		const mask      : number = await input.readInt();
		const boostEcl  : number = await input.readInt();
		
		// Make segments for encoding
		let segs: Array<qrcodegen.QrSegment>;
		if (data.every(b => b < 128)) {  // Is ASCII
			const s: string = data.map(b => String.fromCharCode(b)).join("");
			segs = qrcodegen.QrSegment.makeSegments(s);
		} else
			segs = [qrcodegen.QrSegment.makeBytes(data)];
		
		try {  // Try to make QR Code symbol
			const qr = qrcodegen.QrCode.encodeSegments(
				segs, ECC_LEVELS[errCorLvl], minVersion, maxVersion, mask, boostEcl != 0);
			// Print grid of modules
			await printLine(qr.version);
			for (let y = 0; y < qr.size; y++) {
				for (let x = 0; x < qr.size; x++)
					await printLine(qr.getModule(x, y) ? 1 : 0);
			}
			
		} catch (e) {
			if (e == "Data too long")
				await printLine(-1);
		}
	}
}


namespace input {
	
	let queue: Array<string> = [];
	let callback: ((line:string)=>void)|null = null;
	
	const readline = require("readline");
	let reader = readline.createInterface({
		input: process.stdin,
		terminal: false,
	});
	reader.on("line", (line: string) => {
		queue.push(line);
		if (callback !== null) {
			callback(queue.shift() as string);
			callback = null;
		}
	});
	
	
	async function readLine(): Promise<string> {
		return new Promise(resolve => {
			if (callback !== null)
				throw "Illegal state";
			if (queue.length > 0)
				resolve(queue.shift() as string);
			else
				callback = resolve;
		});
	}
	
	
	export async function readInt(): Promise<number> {
		let s = await readLine();
		if (!/^-?\d+$/.test(s))
			throw "Invalid number syntax";
		return parseInt(s, 10);
	}
	
}


async function printLine(x: Object): Promise<void> {
	return new Promise(resolve =>
		process.stdout.write(x + "\n", "utf-8", ()=>resolve()));
}


const ECC_LEVELS: Array<qrcodegen.QrCode.Ecc> = [
	qrcodegen.QrCode.Ecc.LOW,
	qrcodegen.QrCode.Ecc.MEDIUM,
	qrcodegen.QrCode.Ecc.QUARTILE,
	qrcodegen.QrCode.Ecc.HIGH,
];


main();
