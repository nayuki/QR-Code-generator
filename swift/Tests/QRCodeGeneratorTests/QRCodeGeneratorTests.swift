/* 
 * QR Code generator library (Swift)
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

import XCTest
@testable import QRCodeGenerator

final class QRCodeGeneratorTests: XCTestCase {
    static var allTests = [
        ("testQRCodeGeneration", testQRCodeGeneration),
    ]

    func testQRCodeGeneration() throws {
        // Tested against the JS implementation using https://www.nayuki.io/page/qr-code-generator-library

        let text = "test"
        let qr = try QRCode.encode(text: text, ecl: .low)
        let svg = qr.toSVGString(border: 4)

        XCTAssertEqual(svg, """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
            <svg xmlns="http://www.w3.org/2000/svg" version="1.1" viewBox="0 0 29 29" stroke="none">
              <rect width="100%" height="100%" fill="#FFFFFF"/>
              <path d="M4,4h1v1h-1z M5,4h1v1h-1z M6,4h1v1h-1z M7,4h1v1h-1z M8,4h1v1h-1z M9,4h1v1h-1z M10,4h1v1h-1z M12,4h1v1h-1z M13,4h1v1h-1z M14,4h1v1h-1z M16,4h1v1h-1z M18,4h1v1h-1z M19,4h1v1h-1z M20,4h1v1h-1z M21,4h1v1h-1z M22,4h1v1h-1z M23,4h1v1h-1z M24,4h1v1h-1z M4,5h1v1h-1z M10,5h1v1h-1z M13,5h1v1h-1z M14,5h1v1h-1z M18,5h1v1h-1z M24,5h1v1h-1z M4,6h1v1h-1z M6,6h1v1h-1z M7,6h1v1h-1z M8,6h1v1h-1z M10,6h1v1h-1z M14,6h1v1h-1z M18,6h1v1h-1z M20,6h1v1h-1z M21,6h1v1h-1z M22,6h1v1h-1z M24,6h1v1h-1z M4,7h1v1h-1z M6,7h1v1h-1z M7,7h1v1h-1z M8,7h1v1h-1z M10,7h1v1h-1z M12,7h1v1h-1z M15,7h1v1h-1z M16,7h1v1h-1z M18,7h1v1h-1z M20,7h1v1h-1z M21,7h1v1h-1z M22,7h1v1h-1z M24,7h1v1h-1z M4,8h1v1h-1z M6,8h1v1h-1z M7,8h1v1h-1z M8,8h1v1h-1z M10,8h1v1h-1z M14,8h1v1h-1z M15,8h1v1h-1z M18,8h1v1h-1z M20,8h1v1h-1z M21,8h1v1h-1z M22,8h1v1h-1z M24,8h1v1h-1z M4,9h1v1h-1z M10,9h1v1h-1z M13,9h1v1h-1z M15,9h1v1h-1z M18,9h1v1h-1z M24,9h1v1h-1z M4,10h1v1h-1z M5,10h1v1h-1z M6,10h1v1h-1z M7,10h1v1h-1z M8,10h1v1h-1z M9,10h1v1h-1z M10,10h1v1h-1z M12,10h1v1h-1z M14,10h1v1h-1z M16,10h1v1h-1z M18,10h1v1h-1z M19,10h1v1h-1z M20,10h1v1h-1z M21,10h1v1h-1z M22,10h1v1h-1z M23,10h1v1h-1z M24,10h1v1h-1z M13,11h1v1h-1z M16,11h1v1h-1z M6,12h1v1h-1z M8,12h1v1h-1z M9,12h1v1h-1z M10,12h1v1h-1z M12,12h1v1h-1z M13,12h1v1h-1z M17,12h1v1h-1z M21,12h1v1h-1z M24,12h1v1h-1z M7,13h1v1h-1z M8,13h1v1h-1z M11,13h1v1h-1z M12,13h1v1h-1z M16,13h1v1h-1z M18,13h1v1h-1z M19,13h1v1h-1z M23,13h1v1h-1z M24,13h1v1h-1z M4,14h1v1h-1z M5,14h1v1h-1z M6,14h1v1h-1z M7,14h1v1h-1z M8,14h1v1h-1z M10,14h1v1h-1z M12,14h1v1h-1z M16,14h1v1h-1z M17,14h1v1h-1z M18,14h1v1h-1z M19,14h1v1h-1z M21,14h1v1h-1z M22,14h1v1h-1z M23,14h1v1h-1z M24,14h1v1h-1z M4,15h1v1h-1z M5,15h1v1h-1z M6,15h1v1h-1z M7,15h1v1h-1z M9,15h1v1h-1z M12,15h1v1h-1z M13,15h1v1h-1z M15,15h1v1h-1z M16,15h1v1h-1z M19,15h1v1h-1z M20,15h1v1h-1z M23,15h1v1h-1z M4,16h1v1h-1z M5,16h1v1h-1z M9,16h1v1h-1z M10,16h1v1h-1z M12,16h1v1h-1z M13,16h1v1h-1z M14,16h1v1h-1z M19,16h1v1h-1z M21,16h1v1h-1z M23,16h1v1h-1z M24,16h1v1h-1z M12,17h1v1h-1z M14,17h1v1h-1z M15,17h1v1h-1z M16,17h1v1h-1z M21,17h1v1h-1z M24,17h1v1h-1z M4,18h1v1h-1z M5,18h1v1h-1z M6,18h1v1h-1z M7,18h1v1h-1z M8,18h1v1h-1z M9,18h1v1h-1z M10,18h1v1h-1z M13,18h1v1h-1z M14,18h1v1h-1z M20,18h1v1h-1z M21,18h1v1h-1z M23,18h1v1h-1z M24,18h1v1h-1z M4,19h1v1h-1z M10,19h1v1h-1z M12,19h1v1h-1z M13,19h1v1h-1z M14,19h1v1h-1z M16,19h1v1h-1z M18,19h1v1h-1z M23,19h1v1h-1z M4,20h1v1h-1z M6,20h1v1h-1z M7,20h1v1h-1z M8,20h1v1h-1z M10,20h1v1h-1z M12,20h1v1h-1z M14,20h1v1h-1z M15,20h1v1h-1z M18,20h1v1h-1z M20,20h1v1h-1z M21,20h1v1h-1z M23,20h1v1h-1z M24,20h1v1h-1z M4,21h1v1h-1z M6,21h1v1h-1z M7,21h1v1h-1z M8,21h1v1h-1z M10,21h1v1h-1z M13,21h1v1h-1z M18,21h1v1h-1z M19,21h1v1h-1z M23,21h1v1h-1z M4,22h1v1h-1z M6,22h1v1h-1z M7,22h1v1h-1z M8,22h1v1h-1z M10,22h1v1h-1z M12,22h1v1h-1z M16,22h1v1h-1z M17,22h1v1h-1z M19,22h1v1h-1z M22,22h1v1h-1z M24,22h1v1h-1z M4,23h1v1h-1z M10,23h1v1h-1z M13,23h1v1h-1z M14,23h1v1h-1z M15,23h1v1h-1z M17,23h1v1h-1z M18,23h1v1h-1z M19,23h1v1h-1z M20,23h1v1h-1z M21,23h1v1h-1z M23,23h1v1h-1z M4,24h1v1h-1z M5,24h1v1h-1z M6,24h1v1h-1z M7,24h1v1h-1z M8,24h1v1h-1z M9,24h1v1h-1z M10,24h1v1h-1z M14,24h1v1h-1z M17,24h1v1h-1z M18,24h1v1h-1z M19,24h1v1h-1z M22,24h1v1h-1z M23,24h1v1h-1z M24,24h1v1h-1z" fill="#000000"/>
            </svg>
            """)
    }
}
