/**
 * Generates QR Codes from text strings and byte arrays.
 * 
 * <p>This project aims to be the best, clearest QR Code generator library. The primary goals are flexible options and absolute correctness. Secondary goals are compact implementation size and good documentation comments.</p>
 * <p>Home page with live JavaScript demo, extensive descriptions, and competitor comparisons: <a href="https://www.nayuki.io/page/qr-code-generator-library">https://www.nayuki.io/page/qr-code-generator-library</a></p>
 * 
 * <h2>Features</h2>
 * <p>Core features:</p>
 * <ul>
 *   <li><p>Significantly shorter code but more documentation comments compared to competing libraries</p></li>
 *   <li><p>Supports encoding all 40 versions (sizes) and all 4 error correction levels, as per the QR Code Model 2 standard</p></li>
 *   <li><p>Output format: Raw modules/pixels of the QR symbol</p></li>
 *   <li><p>Detects finder-like penalty patterns more accurately than other implementations</p></li>
 *   <li><p>Encodes numeric and special-alphanumeric text in less space than general text</p></li>
 *   <li><p>Open-source code under the permissive MIT License</p></li>
 * </ul>
 * <p>Manual parameters:</p>
 * <ul>
 *   <li><p>User can specify minimum and maximum version numbers allowed, then library will automatically choose smallest version in the range that fits the data</p></li>
 *   <li><p>User can specify mask pattern manually, otherwise library will automatically evaluate all 8 masks and select the optimal one</p></li>
 *   <li><p>User can specify absolute error correction level, or allow the library to boost it if it doesn't increase the version number</p></li>
 *   <li><p>User can create a list of data segments manually and add ECI segments</p></li>
 * </ul>
 * <p>Optional advanced features:</p>
 * <ul>
 *   <li><p>Encodes Japanese Unicode text in kanji mode to save a lot of space compared to UTF-8 bytes</p></li>
 *   <li><p>Computes optimal segment mode switching for text with mixed numeric/alphanumeric/general/kanji parts</p></li>
 * </ul>
 * <p>More information about QR Code technology and this library's design can be found on the project home page.</p>
 * 
 * <h2>Examples</h2>
 * <p>Simple operation:</p>
 * <pre style="margin-left:2em">import java.awt.image.BufferedImage;
 *import java.io.File;
 *import javax.imageio.ImageIO;
 *import io.nayuki.qrcodegen.*;
 *
 *QrCode qr = QrCode.encodeText("Hello, world!", QrCode.Ecc.MEDIUM);
 *BufferedImage img = toImage(qr, 4, 10);  // See QrCodeGeneratorDemo
 *ImageIO.write(img, "png", new File("qr-code.png"));</pre>
 * <p>Manual operation:</p>
 * <pre style="margin-left:2em">import java.util.List;
 *import io.nayuki.qrcodegen.*;
 *
 *List&lt;QrSegment&gt; segs = QrSegment.makeSegments("3141592653589793238462643383");
 *QrCode qr = QrCode.encodeSegments(segs, QrCode.Ecc.HIGH, 5, 5, 2, false);
 *for (int y = 0; y &lt; qr.size; y++) {
 *    for (int x = 0; x &lt; qr.size; x++) {
 *        (... paint qr.getModule(x, y) ...)
 *    }
 *}</pre>
 */
package io.nayuki.qrcodegen;
