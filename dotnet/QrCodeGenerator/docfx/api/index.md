# QR Code generator library for .NET

Generates QR codes from text strings and byte arrays.

This project aims to be the best, clearest QR code generator library. The primary goals are flexible options
and absolute correctness. Secondary goals are compact implementation size and good documentation comments.

This .NET version is built for .NET Standard 2.0 and therefore runs on most modern .NET platforms (.NET Core, .NET Framework, Mono etc.).

Home page with live JavaScript demo, extensive descriptions, and competitor comparisons:
[https://www.nayuki.io/page/qr-code-generator-library](https://www.nayuki.io/page/qr-code-generator-library)


## .NET API Documention

* [QrCode](xref:IO.Nayuki.QrCodeGen.QrCode): Creates and represents QR codes

* [QrSegment](xref:IO.Nayuki.QrCodeGen.QrSegment): Represents a segment of character/binary/control data in a QR code symbol

* [QrSegmentAdvanced](xref:IO.Nayuki.QrCodeGen.QrSegmentAdvanced): Advanced methods for encoding QR codes using Kanji mode or using multiple segments with different encodings.

* [All types and classes](xref:IO.Nayuki.QrCodeGen)


## Features

Core features:

 * Available in 8 programming languages, all with nearly equal functionality: C#, Java, JavaScript, TypeScript, Python, C++, C, Rust

 * Significantly shorter code but more documentation comments compared to competing libraries

 * Supports encoding all 40 versions (sizes) and all 4 error correction levels, as per the QR Code Model 2 standard

 * Output formats: Raw modules/pixels of the QR symbol, SVG XML string, raster bitmap

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

```cslang
using IO.Nayuki.QrCodeGen;

namespace Examples
{
    class SimpleOperation
    {
        static void Main(string[] args)
        {
            var qr = QrCode.EncodeText("Hello, world!", QrCode.Ecc.Medium);
            using (var bitmap = qr.ToBitmap(4, 10))
            {
                bitmap.Save("qr-code.png", ImageFormat.Png);
            }
        }
    }
}
```

Manual operation:

```cslang
using IO.Nayuki.QrCodeGen;

namespace Examples
{
    class ManualOperation
    {
        static void Main(string[] args)
        {
            var segments = QrCode.MakeSegments("3141592653589793238462643383");
            var qr = QrCode.EncodeSegments(segments, QrCode.Ecc.High, 5, 5, 2, false);
            for (int y = 0; y < qr.Size; y++)
            {
                for (int x = 0; x < qr.Size; x++)
                {
                    ... paint qr.GetModule(x,y) ...
                }
            }
        }
    }
}
```


## Requirements

QR code generator library for .NET requires .NET Standard 2.0 or higher, i.e. any of:

- .NET Core 2.0 or higher
- .NET Framework 4.6.1 or higher
- Mono 5.4 or higher
- Universal Windows Platform 10.0.16299 or higher
- Xamarin
