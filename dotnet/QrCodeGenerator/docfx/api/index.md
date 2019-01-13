# Swiss QR Bill for .NET Reference Documentation

## Reference Documentation

**[Codecrete.SwissQRBill.Generator.QRBill](xref:Codecrete.SwissQRBill.Generator.QRBill)**: Generates Swiss QR bill (receipt and payment part). Also validates the bill data and encode and decode the text embedded in the QR code.

**[Codecrete.SwissQRBill.Generator.QRBill](xref:Codecrete.SwissQRBill.Generator.Bill)**: QR bill data as input for generation or output from decoding

**[Codecrete.SwissQRBill.Generator.Payments](xref:Codecrete.SwissQRBill.Generator.Payments)**: Utility for generating and validation payment related data such as IBAN and reference numbers.

[All types and classes](xref:Codecrete.SwissQRBill.Generator)


Generates QR Codes from text strings and byte arrays.

This project aims to be the best, clearest QR Code generator library. The primary goals are flexible options
and absolute correctness. Secondary goals are compact implementation size and good documentation comments.

Home page with live JavaScript demo, extensive descriptions, and competitor comparisons:
[https://www.nayuki.io/page/qr-code-generator-library](https://www.nayuki.io/page/qr-code-generator-library)

## Features

Core features:

 * Available in 7 programming languages, all with nearly equal functionality: Java, JavaScript, TypeScript, Python, C++, C, Rust

 * Significantly shorter code but more documentation comments compared to competing libraries

 * Supports encoding all 40 versions (sizes) and all 4 error correction levels, as per the QR Code Model 2 standard

 * Output formats: Raw modules/pixels of the QR symbol, SVG XML string, {@code BufferedImage} raster bitmap

 * Encodes numeric and special-alphanumeric text in less space than general text

 * Open source code under the permissive MIT License


Manual parameters:

 * User can specify minimum and maximum version numbers allowed, then library will automatically choose smallest version in the range that fits the data
 
 * User can specify mask pattern manually, otherwise library will automatically evaluate all 8 masks and select the optimal one

 * User can specify absolute error correction level, or allow the library to boost it if it doesn't increase the version number

 * User can create a list of data segments manually and add ECI segments


Optional advanced features:

 * Encodes Japanese Unicode text in kanji mode to save a lot of space compared to UTF-8 bytes

 * Computes optimal segment mode switching for text with mixed numeric/alphanumeric/general/kanji parts

## Examples

Simple operation:

```csharp
namespace QrCode {

}
 * <pre style="margin-left:2em">import java.awt.image.BufferedImage;
 *import java.io.File;
 *import javax.imageio.ImageIO;
 *import io.nayuki.qrcodegen.*;
 *
 *QrCode qr = QrCode.encodeText("Hello, world!", QrCode.Ecc.MEDIUM);
 *BufferedImage img = qr.toImage(4, 10);
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
 ```



## Requirements

Swiss QR Bill for .NET requires .NET Standard 2.0 or higher, i.e. any of:

- .NET Core 2.0 or higher
- .NET Framework 4.6.1 or higher
- Mono 5.4 or higher
- Universal Windows Platform 10.0.16299 or higher
- Xamarin
