/**
 * Generates QR Codes from text strings and byte arrays.
 * 
 * <p>This library generates QR Code symbols, and its design is optimized for speed. It contrasts with another QR library by the same author which is slow but which optimizes for clarity and conciseness. The functionality of this library and its API are nearly identical to the slow library, but it runs anywhere from 1.5&#xD7; to 6&#xD7; as fast.</p>
 * <p>Home page for the fast library (design explanation, benchmarks): <a href="https://www.nayuki.io/page/fast-qr-code-generator-library">https://www.nayuki.io/page/fast-qr-code-generator-library</a></p>
 * <p>Home page for the slow library (live demo, QR Code introduction, competitor comparisons): <a href="https://www.nayuki.io/page/qr-code-generator-library">https://www.nayuki.io/page/qr-code-generator-library</a></p>
 * 
 * <h2>Features</h2>
 * <p>Core features:</p>
 * <ul>
 *   <li><p>Available in 7 programming languages, all with nearly equal functionality: Java, JavaScript, TypeScript, Python, C++, C, Rust</p></li>
 *   <li><p>Significantly shorter code but more documentation comments compared to competing libraries</p></li>
 *   <li><p>Supports encoding all 40 versions (sizes) and all 4 error correction levels, as per the QR Code Model 2 standard</p></li>
 *   <li><p>Output formats: Raw modules/pixels of the QR symbol, SVG XML string, {@code BufferedImage} raster bitmap</p></li>
 *   <li><p>Encodes numeric and special-alphanumeric text in less space than general text</p></li>
 *   <li><p>Open source code under the permissive MIT License</p></li>
 * </ul>
 * <p>Manual parameters:</p>
 * <ul>
 *   <li><p>User can specify minimum and maximum version numbers allowed, then library will automatically choose smallest version in the range that fits the data</p></li>
 *   <li><p>User can specify mask pattern manually, otherwise library will automatically evaluate all 8 masks and select the optimal one</p></li>
 *   <li><p>User can specify absolute error correction level, or allow the library to boost it if it doesn't increase the version number</p></li>
 *   <li><p>User can create a list of data segments manually and add ECI segments</p></li>
 * </ul>
 * 
 * <h2>Examples</h2>
 * <p>Simple operation:</p>
 * <pre style="margin-left:2em">import java.awt.image.BufferedImage;
 *import java.io.File;
 *import javax.imageio.ImageIO;
 *import io.nayuki.fastqrcodegen.*;
 *
 *QrCode qr = QrCode.encodeText("Hello, world!", QrCode.Ecc.MEDIUM);
 *BufferedImage img = qr.toImage(4, 10);
 *ImageIO.write(img, "png", new File("qr-code.png"));</pre>
 * <p>Manual operation:</p>
 * <pre style="margin-left:2em">import java.util.List;
 *import io.nayuki.fastqrcodegen.*;
 *
 *List&lt;QrSegment&gt; segs = QrSegment.makeSegments("3141592653589793238462643383");
 *QrCode qr = QrCode.encodeSegments(segs, QrCode.Ecc.HIGH, 5, 5, 2, false);
 *for (int y = 0; y &lt; qr.size; y++) {
 *    for (int x = 0; x &lt; qr.size; x++) {
 *        (... paint qr.getModule(x, y) ...)
 *    }
 *}</pre>
 */
package io.nayuki.fastqrcodegen;
