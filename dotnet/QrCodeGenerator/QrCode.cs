/* 
 * QR code generator library (.NET)
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


using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.Text;

namespace IO.Nayuki.QrCodeGen
{
    /// <summary>
    /// Represents a QR code containing text or binary data.
    /// <para>
    /// Instances of this class represent an immutable square grid of black and white pixels
    /// (called <i>modules</i> by the QR code specification).
    /// Static factory methods are provided to create QR codes from text or binary data.
    /// Some of the methods provide detailed control about the encoding parameters such a QR
    /// code size (called <i>version</i> by the standard), error correction level and mask.
    /// </para>
    /// <para>
    /// QR codes are a type of two-dimensional barcodes, invented by Denso Wave and
    /// described in the ISO/IEC 18004 standard.
    /// </para>
    /// <para>
    /// This class covers the QR Code Model 2 specification, supporting all versions (sizes)
    /// from 1 to 40, all 4 error correction levels, and 4 character encoding modes.</para>
    /// </summary>
    /// <remarks>
    /// <para>
    /// To create a QR code instance:
    /// </para>
    /// <ul>
    ///   <li>High level: Take the payload data and call <see cref="EncodeText(string, Ecc)"/>
    ///       or <see cref="EncodeBinary(byte[], Ecc)"/>.</li>
    ///   <li>Mid level: Custom-make a list of <see cref="QrSegment"/> instances and call
    ///       <see cref="EncodeSegments"/></li>
    ///   <li>Low level: Custom-make an array of data codeword bytes (including segment headers and
    ///       final padding, excluding error correction codewords), supply the appropriate version number,
    ///       and call the <see cref="QrCode(int, Ecc, byte[], int)"/>.</li>
    /// </ul>
    /// </remarks>
    /// <seealso cref="QrSegment"/>
    public class QrCode
    {
        #region Static factory functions (high level)

        /// <summary>
        /// Creates a QR code representing the specified text using the specified error correction level.
        /// <para>
        /// As a conservative upper bound, this function is guaranteed to succeed for strings with up to 738
        /// Unicode code points (not UTF-16 code units) if the low error correction level is used. The smallest possible
        /// QR code version (size) is automatically chosen. The resulting ECC level will be higher than the one
        /// specified if it can be achieved without increasing the size (version).
        /// </para>
        /// </summary>
        /// <param name="text">The text to be encoded. The full range of Unicode characters may be used.</param>
        /// <param name="ecl">The minimum error correction level to use.</param>
        /// <returns>The created QR code instance representing the specified text.</returns>
        /// <exception cref="ArgumentNullException"><paramref name="text"/> or <paramref name="ecl"/> is <c>null</c>.</exception>
        /// <exception cref="DataTooLongException">The text is too long to fit in the largest QR code size (version)
        /// at the specified error correction level.</exception>
        public static QrCode EncodeText(string text, Ecc ecl)
        {
            Objects.RequireNonNull(text);
            Objects.RequireNonNull(ecl);
            List<QrSegment> segs = QrSegment.MakeSegments(text);
            return EncodeSegments(segs, ecl);
        }

        /// <summary>
        /// Creates a QR code representing the specified binary data using the specified error correction level.
        /// <para>
        /// This function encodes the data in the binary segment mode. The maximum number of
        /// bytes allowed is 2953. The smallest possible QR code version is automatically chosen.
        /// The resulting ECC level will be higher than the one specified if it can be achieved without increasing the size (version).
        /// </para>
        /// </summary>
        /// <param name="data">The binary data to encode.</param>
        /// <param name="ecl">The minimum error correction level to use.</param>
        /// <returns>The created QR code representing the specified data.</returns>
        /// <exception cref="ArgumentNullException"><paramref name="data"/> or <paramref name="ecl"/> is <c>null</c>.</exception>
        /// <exception cref="DataTooLongException">The specified data is too long to fit in the largest QR code size (version)
        /// at the specified error correction level.</exception>
        public static QrCode EncodeBinary(byte[] data, Ecc ecl)
        {
            Objects.RequireNonNull(data);
            Objects.RequireNonNull(ecl);
            QrSegment seg = QrSegment.MakeBytes(data);
            return EncodeSegments(new List<QrSegment> { seg }, ecl);
        }

        #endregion


        #region Static factory functions (mid level)

        /// <summary>
        /// Creates a QR code representing the specified segments with the specified encoding parameters.
        /// <para>
        /// The smallest possible QR code version (size) is used. The range of versions can be
        /// restricted by the <paramref name="minVersion"/> and <paramref name="maxVersion"/> parameters.
        /// </para>
        /// <para>
        /// If <paramref name="boostEcl"/> is <c>true</c>, the resulting ECC level will be higher than the
        /// one specified if it can be achieved without increasing the size (version).
        /// </para>
        /// <para>
        /// The QR code mask is usually automatically chosen. It can be explicitly set with the <paramref name="mask"/>
        /// parameter by using a value between 0 to 7 (inclusive). -1 is for automatic mode (which may be slow).
        /// </para>
        /// <para>
        /// This function allows the user to create a custom sequence of segments that switches
        /// between modes (such as alphanumeric and byte) to encode text in less space and gives full control over all
        /// encoding paramters.
        /// </para>
        /// </summary>
        /// <remarks>
        /// This is a mid-level API; the high-level APIs are <see cref="EncodeText(string, Ecc)"/>
        /// and <see cref="EncodeBinary(byte[], Ecc)"/>.
        /// </remarks>
        /// <param name="segments">The segments to encode.</param>
        /// <param name="ecl">The minimal or fixed error correction level to use .</param>
        /// <param name="minVersion">The minimum version (size) of the QR code (between 1 and 40).</param>
        /// <param name="maxVersion">The maximum version (size) of the QR code (between 1 and 40).</param>
        /// <param name="mask">The mask number to use (between 0 and 7), or -1 for automatic mask selection.</param>
        /// <param name="boostEcl">If <c>true</c> the ECC level wil be increased if it can be achieved without increasing the size (version).</param>
        /// <returns>The created QR code representing the segments.</returns>
        /// <exception cref="ArgumentNullException"><paramref name="segments"/>, any list element, or <paramref name="ecl"/> is <c>null</c>.</exception>
        /// <exception cref="ArgumentOutOfRangeException">1 &#x2264; minVersion &#x2264; maxVersion &#x2264; 40
        /// or -1 &#x2264; mask &#x2264; 7 is violated.</exception>
        /// <exception cref="DataTooLongException">The segments are too long to fit in the largest QR code size (version)
        /// at the specified error correction level.</exception>
        public static QrCode EncodeSegments(List<QrSegment> segments, Ecc ecl, int minVersion = MinVersion, int maxVersion = MaxVersion, int mask = -1, bool boostEcl = true)
        {
            Objects.RequireNonNull(segments);
            Objects.RequireNonNull(ecl);
            if (minVersion < MinVersion || minVersion > maxVersion)
            {
                throw new ArgumentOutOfRangeException(nameof(minVersion), "Invalid value");
            }
            if (maxVersion > MaxVersion)
            {
                throw new ArgumentOutOfRangeException(nameof(maxVersion), "Invalid value");
            }
            if (mask < -1 || mask > 7)
            {
                throw new ArgumentOutOfRangeException(nameof(mask), "Invalid value");
            }

            // Find the minimal version number to use
            int version, dataUsedBits;
            for (version = minVersion; ; version++)
            {
                int numDataBits = GetNumDataCodewords(version, ecl) * 8;  // Number of data bits available
                dataUsedBits = QrSegment.GetTotalBits(segments, version);
                if (dataUsedBits != -1 && dataUsedBits <= numDataBits)
                {
                    break;  // This version number is found to be suitable
                }

                if (version >= maxVersion)
                {  // All versions in the range could not fit the given data
                    string msg = "Segment too long";
                    if (dataUsedBits != -1)
                    {
                        msg = $"Data length = {dataUsedBits} bits, Max capacity = {numDataBits} bits";
                    }

                    throw new DataTooLongException(msg);
                }
            }
            Debug.Assert(dataUsedBits != -1);

            // Increase the error correction level while the data still fits in the current version number
            foreach (Ecc newEcl in Ecc.AllValues)
            {  // From low to high
                if (boostEcl && dataUsedBits <= GetNumDataCodewords(version, newEcl) * 8)
                {
                    ecl = newEcl;
                }
            }

            // Concatenate all segments to create the data bit string
            BitArray ba = new BitArray(0);
            foreach (QrSegment seg in segments)
            {
                ba.AppendBits(seg.EncodingMode.ModeBits, 4);
                ba.AppendBits((uint)seg.NumChars, seg.EncodingMode.NumCharCountBits(version));
                ba.AppendData(seg.GetData());
            }
            Debug.Assert(ba.Length == dataUsedBits);

            // Add terminator and pad up to a byte if applicable
            int dataCapacityBits = GetNumDataCodewords(version, ecl) * 8;
            Debug.Assert(ba.Length <= dataCapacityBits);
            ba.AppendBits(0, Math.Min(4, dataCapacityBits - ba.Length));
            ba.AppendBits(0, (8 - ba.Length % 8) % 8);
            Debug.Assert(ba.Length % 8 == 0);

            // Pad with alternating bytes until data capacity is reached
            for (uint padByte = 0xEC; ba.Length < dataCapacityBits; padByte ^= 0xEC ^ 0x11)
            {
                ba.AppendBits(padByte, 8);
            }

            // Pack bits into bytes in big endian
            byte[] dataCodewords = new byte[ba.Length / 8];
            for (int i = 0; i < ba.Length; i++)
            {
                if (ba.Get(i))
                {
                    dataCodewords[i >> 3] |= (byte)(1 << (7 - (i & 7)));
                }
            }

            // Create the QR code object
            return new QrCode(version, ecl, dataCodewords, mask);
        }

        #endregion


        #region Public immutable properties

        /// <summary>
        /// The version (size) of this QR code (between 1 for the smallest and 40 for the biggest).
        /// </summary>
        /// <value>The QR code version (size).</value>
        public int Version { get; }

        /// <summary>
        /// The width and height of this QR code, in modules (pixels).
        /// The size is a value between 21 and 177.
        /// This is equal to version &#xD7; 4 + 17.
        /// </summary>
        /// <value>The QR code size.</value>
        public int Size { get; }

        /// <summary>
        /// The error correction level used for this QR code.
        /// </summary>
        /// <value>The error correction level.</value>
        public Ecc ErrorCorrectionLevel { get; }

        /// <summary>
        /// The index of the mask pattern used fort this QR code (between 0 and 7).
        /// <para>
        /// Even if a QR code is created with automatic mask selection (<c>mask</c> = 1),
        /// this property returns the effective mask used.
        /// </para>
        /// </summary>
        /// <value>The mask pattern index.</value>
        public int Mask { get; }

        #endregion


        #region Private grids of modules/pixels, with dimensions of size * size

        // The modules of this QR code (false = white, true = black).
        // Immutable after constructor finishes. Accessed through GetModule().
        private readonly bool[,] _modules;

        // Indicates function modules that are not subjected to masking. Discarded when constructor finishes.
        private readonly bool[,] _isFunction;

        #endregion


        #region  Constructor (low level)

        /// <summary>
        /// Constructs a QR code with the specified version number,
        /// error correction level, data codeword bytes, and mask number.
        /// </summary>
        /// <remarks>
        /// This is a low-level API that most users should not use directly. A mid-level
        /// API is the <see cref="EncodeSegments"/> function.
        /// </remarks>
        /// <param name="version">The version (size) to use (between 1 to 40).</param>
        /// <param name="ecl">The error correction level to use.</param>
        /// <param name="dataCodewords">The bytes representing segments to encode (without ECC).</param>
        /// <param name="mask">The mask pattern to use (either -1 for automatic selection, or a value from 0 to 7 for fixed choice).</param>
        /// <exception cref="ArgumentNullException"><paramref name="ecl"/> or <paramref name="dataCodewords"/> is <c>null</c>.</exception>
        /// <exception cref="ArgumentOutOfRangeException">The version or mask value is out of range,
        /// or the data has an invalid length for the specified version and error correction level.</exception>
        public QrCode(int version, Ecc ecl, byte[] dataCodewords, int mask = -1)
        {
            // Check arguments and initialize fields
            if (version < MinVersion || version > MaxVersion)
            {
                throw new ArgumentOutOfRangeException(nameof(version), "Version value out of range");
            }

            if (mask < -1 || mask > 7)
            {
                throw new ArgumentOutOfRangeException(nameof(mask), "Mask value out of range");
            }

            Version = version;
            Size = version * 4 + 17;
            Objects.RequireNonNull(ecl);
            ErrorCorrectionLevel = ecl;
            Objects.RequireNonNull(dataCodewords);
            _modules = new bool[Size, Size];  // Initially all white
            _isFunction = new bool[Size, Size];

            // Compute ECC, draw modules, do masking
            DrawFunctionPatterns();
            byte[] allCodewords = AddEccAndInterleave(dataCodewords);
            DrawCodewords(allCodewords);
            Mask = HandleConstructorMasking(mask);
            _isFunction = null;
        }


        #endregion


        #region Public methods

        /// <summary>
        /// Gets the color of the module (pixel) at the specified coordinates.
        /// <para>
        /// The top left corner has the coordinates (x=0, y=0). <i>x</i>-coordinates extend from left to right,
        /// <i>y</i>-coordinates extend from top to bottom.
        /// </para>
        /// <para>
        /// If coordinates outside the bounds of this QR code are specified, white (<c>false</c>) is returned.
        /// </para>
        /// </summary>
        /// <param name="x">The x coordinate.</param>
        /// <param name="y">The y coordinate.</param>
        /// <returns>The color of the specified module: <c>true</c> for black modules and <c>false</c>
        /// for white modules (or if the coordinates are outside the bounds).</returns>
        public bool GetModule(int x, int y)
        {
            return 0 <= x && x < Size && 0 <= y && y < Size && _modules[y, x];
        }


        /// <summary>
        /// Creates a bitmap (raster image) of this QR code.
        /// <para>
        /// The <paramref name="scale"/> parameter specifies the scale of the image, which is
        /// equivalent to the width and height of each QR code module. Additionally, the number
        /// of modules to add as a border to all four sides can be specified.
        /// </para>
        /// <para>
        /// For example, <c>ToBitmap(scale: 10, border: 4)</c> means to pad the QR code with 4 white
        /// border modules on all four sides, and use 10&#xD7;10 pixels to represent each module.
        /// </para>
        /// <para>
        /// The resulting bitmap uses the pixel format <see cref="PixelFormat.Format24bppRgb"/> and
        /// only contains black (0x000000) and white (0xFFFFFF) pixels.
        /// </para>
        /// </summary>
        /// <param name="scale">The width and height, in pixels, of each module.</param>
        /// <param name="border">The number of border modules to add to each of the four sides.</param>
        /// <returns>The created bitmap representing this QR code.</returns>
        /// <exception cref="ArgumentOutOfRangeException"><paramref name="scale"/> is 0 or negative, <paramref name="border"/> is negative
        /// or the resulting image is wider than 32,768 pixels.</exception>
        public Bitmap ToBitmap(int scale, int border)
        {
            if (scale <= 0)
            {
                throw new ArgumentOutOfRangeException(nameof(scale), "Value out of range");
            }
            if (border < 0)
            {
                throw new ArgumentOutOfRangeException(nameof(border), "Value out of range");
            }

            int dim = (Size + border * 2) * scale;

            if (dim > short.MaxValue)
            {
                throw new ArgumentOutOfRangeException(nameof(scale), "Scale or border too large");
            }

            var bitmap = new Bitmap(dim, dim, PixelFormat.Format24bppRgb);

            // simple and inefficient
            for (var y = 0; y < dim; y++)
            {
                for (var x = 0; x < dim; x++)
                {
                    bool color = GetModule(x / scale - border, y / scale - border);
                    bitmap.SetPixel(x, y, color ? Color.Black : Color.White);
                }
            }

            return bitmap;
        }


        /// <summary>
        /// Creates an SVG image of this QR code.
        /// <para>
        /// The images uses Unix newlines (\n), regardless of the platform.
        /// </para>
        /// </summary>
        /// <param name="border">The number of border modules to add on all four sides.</param>
        /// <returns>The created SVG XML document of this QR code as a string.</returns>
        /// <exception cref="ArgumentOutOfRangeException"><paramref name="border"/> is negative.</exception>
        public string ToSvgString(int border)
        {
            if (border < 0)
            {
                throw new ArgumentOutOfRangeException(nameof(border), "Border must be non-negative");
            }

            int dim = Size + border * 2;
            StringBuilder sb = new StringBuilder()
                .Append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .Append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n")
                .Append($"<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 {dim} {dim}\" stroke=\"none\">\n")
                .Append("\t<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n")
                .Append("\t<path d=\"");

            for (int y = 0; y < Size; y++)
            {
                for (int x = 0; x < Size; x++)
                {
                    if (!GetModule(x, y)) continue;

                    if (x != 0 || y != 0)
                        sb.Append(" ");
                    sb.Append($"M{x + border},{y + border}h1v1h-1z");
                }
            }

            return sb
                .Append("\" fill=\"#000000\"/>\n")
                .Append("</svg>\n")
                .ToString();
        }

        #endregion


        #region Private helper methods for constructor: Drawing function modules

        // Reads this object's version field, and draws and marks all function modules.
        private void DrawFunctionPatterns()
        {
            // Draw horizontal and vertical timing patterns
            for (int i = 0; i < Size; i++)
            {
                SetFunctionModule(6, i, i % 2 == 0);
                SetFunctionModule(i, 6, i % 2 == 0);
            }

            // Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
            DrawFinderPattern(3, 3);
            DrawFinderPattern(Size - 4, 3);
            DrawFinderPattern(3, Size - 4);

            // Draw numerous alignment patterns
            int[] alignPatPos = GetAlignmentPatternPositions();
            int numAlign = alignPatPos.Length;
            for (int i = 0; i < numAlign; i++)
            {
                for (int j = 0; j < numAlign; j++)
                {
                    // Don't draw on the three finder corners
                    if (!(i == 0 && j == 0 || i == 0 && j == numAlign - 1 || i == numAlign - 1 && j == 0))
                    {
                        DrawAlignmentPattern(alignPatPos[i], alignPatPos[j]);
                    }
                }
            }

            // Draw configuration data
            DrawFormatBits(0);  // Dummy mask value; overwritten later in the constructor
            DrawVersion();
        }


        // Draws two copies of the format bits (with its own error correction code)
        // based on the given mask and this object's error correction level field.
        private void DrawFormatBits(uint mask)
        {
            // Calculate error correction code and pack bits
            uint data = (ErrorCorrectionLevel.FormatBits << 3) | mask;  // errCorrLvl is uint2, mask is uint3
            uint rem = data;
            for (int i = 0; i < 10; i++)
            {
                rem = (rem << 1) ^ ((rem >> 9) * 0x537);
            }

            uint bits = ((data << 10) | rem) ^ 0x5412;  // uint15
            Debug.Assert(bits >> 15 == 0);

            // Draw first copy
            for (int i = 0; i <= 5; i++)
            {
                SetFunctionModule(8, i, GetBit(bits, i));
            }

            SetFunctionModule(8, 7, GetBit(bits, 6));
            SetFunctionModule(8, 8, GetBit(bits, 7));
            SetFunctionModule(7, 8, GetBit(bits, 8));
            for (int i = 9; i < 15; i++)
            {
                SetFunctionModule(14 - i, 8, GetBit(bits, i));
            }

            // Draw second copy
            for (int i = 0; i < 8; i++)
            {
                SetFunctionModule(Size - 1 - i, 8, GetBit(bits, i));
            }

            for (int i = 8; i < 15; i++)
            {
                SetFunctionModule(8, Size - 15 + i, GetBit(bits, i));
            }

            SetFunctionModule(8, Size - 8, true);  // Always black
        }


        // Draws two copies of the version bits (with its own error correction code),
        // based on this object's version field, iff 7 <= version <= 40.
        private void DrawVersion()
        {
            if (Version < 7)
            {
                return;
            }

            // Calculate error correction code and pack bits
            uint rem = (uint)Version;  // version is uint6, in the range [7, 40]
            for (int i = 0; i < 12; i++)
            {
                rem = (rem << 1) ^ ((rem >> 11) * 0x1F25);
            }

            uint bits = ((uint)Version << 12) | rem;  // uint18
            Debug.Assert(bits >> 18 == 0);

            // Draw two copies
            for (int i = 0; i < 18; i++)
            {
                bool bit = GetBit(bits, i);
                int a = Size - 11 + i % 3;
                int b = i / 3;
                SetFunctionModule(a, b, bit);
                SetFunctionModule(b, a, bit);
            }
        }

        // Draws a 9*9 finder pattern including the border separator,
        // with the center module at (x, y). Modules can be out of bounds.
        private void DrawFinderPattern(int x, int y)
        {
            for (int dy = -4; dy <= 4; dy++)
            {
                for (int dx = -4; dx <= 4; dx++)
                {
                    int dist = Math.Max(Math.Abs(dx), Math.Abs(dy));  // Chebyshev/infinity norm
                    int xx = x + dx, yy = y + dy;
                    if (0 <= xx && xx < Size && 0 <= yy && yy < Size)
                    {
                        SetFunctionModule(xx, yy, dist != 2 && dist != 4);
                    }
                }
            }
        }


        // Draws a 5*5 alignment pattern, with the center module
        // at (x, y). All modules must be in bounds.
        private void DrawAlignmentPattern(int x, int y)
        {
            for (int dy = -2; dy <= 2; dy++)
            {
                for (int dx = -2; dx <= 2; dx++)
                {
                    SetFunctionModule(x + dx, y + dy, Math.Max(Math.Abs(dx), Math.Abs(dy)) != 1);
                }
            }
        }


        // Sets the color of a module and marks it as a function module.
        // Only used by the constructor. Coordinates must be in bounds.
        private void SetFunctionModule(int x, int y, bool isBlack)
        {
            _modules[y, x] = isBlack;
            _isFunction[y, x] = true;
        }

        #endregion


        #region Private helper methods for constructor: Codewords and masking

        // Returns a new byte string representing the given data with the appropriate error correction
        // codewords appended to it, based on this object's version and error correction level.
        private byte[] AddEccAndInterleave(byte[] data)
        {
            Objects.RequireNonNull(data);
            if (data.Length != GetNumDataCodewords(Version, ErrorCorrectionLevel))
            {
                throw new ArgumentOutOfRangeException();
            }

            // Calculate parameter numbers
            int numBlocks = NumErrorCorrectionBlocks[ErrorCorrectionLevel.Ordinal, Version];
            int blockEccLen = EccCodewordsPerBlock[ErrorCorrectionLevel.Ordinal, Version];
            int rawCodewords = GetNumRawDataModules(Version) / 8;
            int numShortBlocks = numBlocks - rawCodewords % numBlocks;
            int shortBlockLen = rawCodewords / numBlocks;

            // Split data into blocks and append ECC to each block
            byte[][] blocks = new byte[numBlocks][];
            ReedSolomonGenerator rs = new ReedSolomonGenerator(blockEccLen);
            for (int i = 0, k = 0; i < numBlocks; i++)
            {
                byte[] dat = CopyOfRange(data, k, k + shortBlockLen - blockEccLen + (i < numShortBlocks ? 0 : 1));
                k += dat.Length;
                byte[] block = CopyOf(dat, shortBlockLen + 1);
                byte[] ecc = rs.GetRemainder(dat);
                Array.Copy(ecc, 0, block, block.Length - blockEccLen, ecc.Length);
                blocks[i] = block;
            }

            // Interleave (not concatenate) the bytes from every block into a single sequence
            byte[] result = new byte[rawCodewords];
            for (int i = 0, k = 0; i < blocks[0].Length; i++)
            {
                for (int j = 0; j < blocks.Length; j++)
                {
                    // Skip the padding byte in short blocks
                    if (i != shortBlockLen - blockEccLen || j >= numShortBlocks)
                    {
                        result[k] = blocks[j][i];
                        k++;
                    }
                }
            }
            return result;
        }


        // Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
        // data area of this QR code. Function modules need to be marked off before this is called.
        private void DrawCodewords(byte[] data)
        {
            Objects.RequireNonNull(data);
            if (data.Length != GetNumRawDataModules(Version) / 8)
            {
                throw new ArgumentOutOfRangeException();
            }

            int i = 0;  // Bit index into the data
                        // Do the funny zigzag scan
            for (int right = Size - 1; right >= 1; right -= 2)
            {
                // Index of right column in each column pair
                if (right == 6)
                {
                    right = 5;
                }

                for (int vert = 0; vert < Size; vert++)
                {
                    // Vertical counter
                    for (int j = 0; j < 2; j++)
                    {
                        int x = right - j;  // Actual x coordinate
                        bool upward = ((right + 1) & 2) == 0;
                        int y = upward ? Size - 1 - vert : vert;  // Actual y coordinate
                        if (!_isFunction[y, x] && i < data.Length * 8)
                        {
                            _modules[y, x] = GetBit(data[(uint)i >> 3], 7 - (i & 7));
                            i++;
                        }
                        // If this QR code has any remainder bits (0 to 7), they were assigned as
                        // 0/false/white by the constructor and are left unchanged by this method
                    }
                }
            }
            Debug.Assert(i == data.Length * 8);
        }


        // XORs the codeword modules in this QR code with the given mask pattern.
        // The function modules must be marked and the codeword bits must be drawn
        // before masking. Due to the arithmetic of XOR, calling applyMask() with
        // the same mask value a second time will undo the mask. A final well-formed
        // QR code needs exactly one (not zero, two, etc.) mask applied.
        private void ApplyMask(uint mask)
        {
            if (mask > 7)
            {
                throw new ArgumentOutOfRangeException(nameof(mask), "Mask value out of range");
            }

            for (int y = 0; y < Size; y++)
            {
                for (int x = 0; x < Size; x++)
                {
                    bool invert;
                    switch (mask)
                    {
                        case 0: invert = (x + y) % 2 == 0; break;
                        case 1: invert = y % 2 == 0; break;
                        case 2: invert = x % 3 == 0; break;
                        case 3: invert = (x + y) % 3 == 0; break;
                        case 4: invert = (x / 3 + y / 2) % 2 == 0; break;
                        case 5: invert = x * y % 2 + x * y % 3 == 0; break;
                        case 6: invert = (x * y % 2 + x * y % 3) % 2 == 0; break;
                        case 7: invert = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
                        default: Debug.Assert(false); return;
                    }
                    _modules[y, x] ^= invert & !_isFunction[y, x];
                }
            }
        }


        // A messy helper function for the constructor. This QR code must be in an unmasked state when this
        // method is called. The given argument is the requested mask, which is -1 for auto or 0 to 7 for fixed.
        // This method applies and returns the actual mask chosen, from 0 to 7.
        private int HandleConstructorMasking(int mask)
        {
            if (mask == -1)
            {
                // Automatically choose best mask
                int minPenalty = int.MaxValue;
                for (uint i = 0; i < 8; i++)
                {
                    ApplyMask(i);
                    DrawFormatBits(i);
                    int penalty = GetPenaltyScore();
                    if (penalty < minPenalty)
                    {
                        mask = (int)i;
                        minPenalty = penalty;
                    }
                    ApplyMask(i);  // Undoes the mask due to XOR
                }
            }
            Debug.Assert(0 <= mask && mask <= 7);
            ApplyMask((uint)mask);  // Apply the final choice of mask
            DrawFormatBits((uint)mask);  // Overwrite old format bits
            return mask;  // The caller shall assign this value to the final-declared field
        }


        // Calculates and returns the penalty score based on state of this QR code's current modules.
        // This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
        private int GetPenaltyScore()
        {
            int result = 0;

            // Adjacent modules in row having same color, and finder-like patterns
            for (int y = 0; y < Size; y++)
            {
                int[] runHistory = new int[7];
                bool color = false;
                int runX = 0;
                for (int x = 0; x < Size; x++)
                {
                    if (_modules[y, x] == color)
                    {
                        runX++;
                        if (runX == 5)
                        {
                            result += PenaltyN1;
                        }
                        else if (runX > 5)
                        {
                            result++;
                        }
                    }
                    else
                    {
                        AddRunToHistory(runX, runHistory);
                        if (!color && HasFinderLikePattern(runHistory))
                        {
                            result += PenaltyN3;
                        }

                        color = _modules[y, x];
                        runX = 1;
                    }
                }
                AddRunToHistory(runX, runHistory);
                if (color)
                {
                    AddRunToHistory(0, runHistory);  // Dummy run of white
                }

                if (HasFinderLikePattern(runHistory))
                {
                    result += PenaltyN3;
                }
            }
            // Adjacent modules in column having same color, and finder-like patterns
            for (int x = 0; x < Size; x++)
            {
                int[] runHistory = new int[7];
                bool color = false;
                int runY = 0;
                for (int y = 0; y < Size; y++)
                {
                    if (_modules[y, x] == color)
                    {
                        runY++;
                        if (runY == 5)
                        {
                            result += PenaltyN1;
                        }
                        else if (runY > 5)
                        {
                            result++;
                        }
                    }
                    else
                    {
                        AddRunToHistory(runY, runHistory);
                        if (!color && HasFinderLikePattern(runHistory))
                        {
                            result += PenaltyN3;
                        }

                        color = _modules[y, x];
                        runY = 1;
                    }
                }
                AddRunToHistory(runY, runHistory);
                if (color)
                {
                    AddRunToHistory(0, runHistory);  // Dummy run of white
                }

                if (HasFinderLikePattern(runHistory))
                {
                    result += PenaltyN3;
                }
            }

            // 2*2 blocks of modules having same color
            for (int y = 0; y < Size - 1; y++)
            {
                for (int x = 0; x < Size - 1; x++)
                {
                    bool color = _modules[y, x];
                    if (color == _modules[y, x + 1] &&
                          color == _modules[y + 1, x] &&
                          color == _modules[y + 1, x + 1])
                    {
                        result += PenaltyN2;
                    }
                }
            }

            // Balance of black and white modules
            int black = 0;
            for (int y = 0; y < Size; y++)
            {
                for (int x = 0; x < Size; x++)

                {
                    if (_modules[y, x])
                    {
                        black++;
                    }
                }
            }
            int total = Size * Size;  // Note that size is odd, so black/total != 1/2
                                      // Compute the smallest integer k >= 0 such that (45-5k)% <= black/total <= (55+5k)%
            int k = (Math.Abs(black * 20 - total * 10) + total - 1) / total - 1;
            result += k * PenaltyN4;
            return result;
        }


        #endregion


        #region Private helper functions

        // Returns an ascending list of positions of alignment patterns for this version number.
        // Each position is in the range [0,177), and are used on both the x and y axes.
        // This could be implemented as lookup table of 40 variable-length lists of unsigned bytes.
        private int[] GetAlignmentPatternPositions()
        {
            if (Version == 1)
            {
                return new int[] { };
            }
            else
            {
                int numAlign = Version / 7 + 2;
                int step;
                if (Version == 32)  // Special snowflake
                {
                    step = 26;
                }
                else  // step = ceil[(size - 13) / (numAlign*2 - 2)] * 2
                {
                    step = (Version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2;
                }

                int[] result = new int[numAlign];
                result[0] = 6;
                for (int i = result.Length - 1, pos = Size - 7; i >= 1; i--, pos -= step)
                {
                    result[i] = pos;
                }

                return result;
            }
        }

        // Returns the number of data bits that can be stored in a QR code of the given version number, after
        // all function modules are excluded. This includes remainder bits, so it might not be a multiple of 8.
        // The result is in the range [208, 29648]. This could be implemented as a 40-entry lookup table.
        private static int GetNumRawDataModules(int ver)
        {
            if (ver < MinVersion || ver > MaxVersion)
            {
                throw new ArgumentOutOfRangeException(nameof(ver), "Version number out of range");
            }

            int size = ver * 4 + 17;
            int result = size * size;   // Number of modules in the whole QR code square
            result -= 8 * 8 * 3;        // Subtract the three finders with separators
            result -= 15 * 2 + 1;       // Subtract the format information and black module
            result -= (size - 16) * 2;  // Subtract the timing patterns (excluding finders)
                                        // The five lines above are equivalent to: int result = (16 * ver + 128) * ver + 64;

            if (ver < 2) return result;

            int numAlign = ver / 7 + 2;
            result -= (numAlign - 1) * (numAlign - 1) * 25;  // Subtract alignment patterns not overlapping with timing patterns
            result -= (numAlign - 2) * 2 * 20;  // Subtract alignment patterns that overlap with timing patterns
            // The two lines above are equivalent to: result -= (25 * numAlign - 10) * numAlign - 55;
            if (ver >= 7)
            {
                result -= 6 * 3 * 2;  // Subtract version information
            }
            return result;
        }


        // Returns the number of 8-bit data (i.e. not error correction) codewords contained in any
        // QR code of the given version number and error correction level, with remainder bits discarded.
        // This stateless pure function could be implemented as a (40*4)-cell lookup table.
        internal static int GetNumDataCodewords(int ver, Ecc ecl)
        {
            return GetNumRawDataModules(ver) / 8
                - EccCodewordsPerBlock[ecl.Ordinal, ver]
                * NumErrorCorrectionBlocks[ecl.Ordinal, ver];
        }


        // Inserts the given value to the front of the given array, which shifts over the
        // existing values and deletes the last value. A helper function for GetPenaltyScore().
        private static void AddRunToHistory(int run, int[] history)
        {
            Array.Copy(history, 0, history, 1, history.Length - 1);
            history[0] = run;
        }


        // Tests whether the given run history has the pattern of ratio 1:1:3:1:1 in the middle, and
        // surrounded by at least 4 on either or both ends. A helper function for GetPenaltyScore().
        // Must only be called immediately after a run of white modules has ended.
        private static bool HasFinderLikePattern(int[] runHistory)
        {
            int n = runHistory[1];
            return n > 0 && runHistory[2] == n && runHistory[4] == n && runHistory[5] == n
                && runHistory[3] == n * 3 && Math.Max(runHistory[0], runHistory[6]) >= n * 4;
        }


        private static byte[] CopyOfRange(byte[] original, int from, int to)
        {
            byte[] result = new byte[to - from];
            Array.Copy(original, from, result, 0, to - from);
            return result;
        }


        private static byte[] CopyOf(byte[] original, int newLength)
        {
            byte[] result = new byte[newLength];
            Array.Copy(original, result, Math.Min(original.Length, newLength));
            return result;
        }


        // Returns true iff the i'th bit of x is set to 1.
        private static bool GetBit(uint x, int i)
        {
            return ((x >> i) & 1) != 0;
        }

        #endregion


        #region Constants and tables

        /// <summary>
        /// The minimum version (size) supported in the QR Code Model 2 standard – namely 1.
        /// </summary>
        /// <value>The minimum version.</value>
        public const int MinVersion = 1;

        /// <summary>
        /// The maximum version (size) supported in the QR Code Model 2 standard – namely 40.
        /// </summary>
        /// <value>The maximum version.</value>
        public const int MaxVersion = 40;


        // For use in getPenaltyScore(), when evaluating which mask is best.
        private const int PenaltyN1 = 3;
        private const int PenaltyN2 = 3;
        private const int PenaltyN3 = 40;
        private const int PenaltyN4 = 10;


        private static readonly byte[,] EccCodewordsPerBlock = {
		    // Version: (note that index 0 is for padding, and is set to an illegal value)
		    //  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40     Error correction level
		    { 255,  7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30 },  // Low
		    { 255, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28 },  // Medium
		    { 255, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30 },  // Quartile
		    { 255, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30 },  // High
	    };

        private static readonly byte[,] NumErrorCorrectionBlocks = {
		    // Version: (note that index 0 is for padding, and is set to an illegal value)
		    //  0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40     Error correction level
		    { 255, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4,  4,  4,  4,  4,  6,  6,  6,  6,  7,  8,  8,  9,  9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25 },  // Low
		    { 255, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5,  5,  8,  9,  9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49 },  // Medium
		    { 255, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8,  8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68 },  // Quartile
		    { 255, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81 },  // High
	    };

        #endregion


        #region Public helper enumeration

        /// <summary>
        /// Error correction level in QR code symbol.
        /// </summary>
        public sealed class Ecc
        {
            /// <summary>
            /// Low error correction level. The QR code can tolerate about 7% erroneous codewords.
            /// </summary>
            /// <value>Low error correction level.</value>
            public static readonly Ecc Low = new Ecc(0, 1);

            /// <summary>
            /// Medium error correction level. The QR code can tolerate about 15% erroneous codewords.
            /// </summary>
            /// <value>Medium error correction level.</value>
            public static readonly Ecc Medium = new Ecc(1, 0);

            /// <summary>
            /// Quartile error correction level. The QR code can tolerate about 25% erroneous codewords.
            /// </summary>
            /// <value>Quartile error correction level.</value>
            public static readonly Ecc Quartile = new Ecc(2, 3);

            /// <summary>
            /// High error correction level. The QR code can tolerate about 30% erroneous codewords.
            /// </summary>
            /// <value>High error correction level.</value>
            public static readonly Ecc High = new Ecc(3, 2);


            internal static Ecc[] AllValues = { Low, Medium, Quartile, High };

            /// <summary>
            /// Ordinal number of error correction level (in the range 0 to 3).
            /// </summary>
            /// <remarks>
            /// Higher number represent a higher amount of error tolerance.
            /// </remarks>
            /// <value>Ordinal number.</value>
            public int Ordinal { get; }

            // In the range 0 to 3 (unsigned 2-bit integer).
            internal uint FormatBits { get; }

            // Constructor.
            private Ecc(int ordinal, uint fb)
            {
                Ordinal = ordinal;
                FormatBits = fb;
            }
        }


        #endregion
    }
}
