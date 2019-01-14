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
using System.Text;
using System.Text.RegularExpressions;

namespace IO.Nayuki.QrCodeGen
{
    /// <summary>
    /// Represents a segment of character/binary/control data in a QR code symbol.
    /// </summary>
    /// <remarks>
    /// <para>
    /// The easiest way to deal with QR code segments is to call
    /// <see cref="QrCode.EncodeText"/> or <see cref="QrCode.EncodeBinary"/>, and not
    /// to use instances of this class directly. The mid-level way is to take the payload
    /// data and call a static factory function such as <see cref="MakeNumeric(string)"/>.
    /// The low-level way is to custom-make the bit array and call the
    /// <see cref="QrSegment(Mode, int, BitArray)"/> constructor with appropriate values.
    /// </para>
    /// <para>
    /// This segment class imposes no length restrictions, but QR codes have restrictions.
    /// Even in the most favorable conditions, a QR code can only hold 7089 characters of data.
    /// Any segment longer than this is meaningless for the purpose of generating QR codes.
    /// </para>
    /// <para>
    /// This class can represent kanji mode segments, but provides no help in encoding them
    /// - see <see cref="QrSegmentAdvanced"/> for full kanji support.
    /// </para>
    /// <para>
    /// Instances of this class are immutable.
    /// </para>
    /// </remarks>
    public class QrSegment
    {
        #region Static factory functions (mid level)

        /// <summary>
        /// Creates a segment representing the specified binary data
        /// encoded in byte mode. All input byte arrays are acceptable.
        /// <para>
        /// Any text string can be converted to UTF-8 bytes (using <c>Encoding.UTF8.GetBytes(str)</c>)
        /// and encoded as a byte mode segment.
        /// </para>
        /// </summary>
        /// <param name="data">The binary data to encode.</param>
        /// <returns>The created segment containing the specified data.</returns>
        /// <exception cref="ArgumentNullException"><c>data</c> is <c>null</c>.</exception>
        public static QrSegment MakeBytes(byte[] data)
        {
            Objects.RequireNonNull(data);
            BitArray ba = new BitArray(0);
            foreach (byte b in data)
            {
                ba.AppendBits(b, 8);
            }

            return new QrSegment(Mode.Byte, data.Length, ba);
        }


        /// <summary>
        /// Creates a segment representing the specified string of decimal digits.
        /// The segment is encoded in numeric mode.
        /// </summary>
        /// <param name="digits">The text to encode, consisting of digits from 0 to 9 only.</param>
        /// <returns>The created segment containing the text.</returns>
        /// <exception cref="ArgumentNullException"><c>digits</c> is <c>null</c>.</exception>
        /// <exception cref="ArgumentOutOfRangeException"><c>digits</c> contains non-digit characters</exception>
        public static QrSegment MakeNumeric(string digits)
        {
            Objects.RequireNonNull(digits);
            if (!NumericRegex.IsMatch(digits))
            {
                throw new ArgumentOutOfRangeException(nameof(digits), "String contains non-numeric characters");
            }

            BitArray ba = new BitArray(0);
            for (int i = 0; i < digits.Length;)
            {
                // Consume up to 3 digits per iteration
                int n = Math.Min(digits.Length - i, 3);
                ba.AppendBits(uint.Parse(digits.Substring(i, n)), n * 3 + 1);
                i += n;
            }
            return new QrSegment(Mode.Numeric, digits.Length, ba);
        }


        /// <summary>
        /// Creates a segment representing the specified text string.
        /// The segment is encoded in alphanumeric mode.
        /// <para>
        /// Allowed characters are: 0 to 9, A to Z (uppercase only), space,
        /// dollar, percent, asterisk, plus, hyphen, period, slash, colon.
        /// </para>
        /// </summary>
        /// <param name="text">The text to encode, consisting of allowed characters only.</param>
        /// <returns>The created segment containing the text.</returns>
        /// <exception cref="ArgumentNullException"><c>text</c> is <c>null</c>.</exception>
        /// <exception cref="ArgumentOutOfRangeException"><c>text</c> contains non-encodable characters.</exception>
        public static QrSegment MakeAlphanumeric(string text)
        {
            Objects.RequireNonNull(text);
            if (!AlphanumericRegex.IsMatch(text))
            {
                throw new ArgumentOutOfRangeException(nameof(text), "String contains unencodable characters in alphanumeric mode");
            }

            BitArray ba = new BitArray(0);
            int i;
            for (i = 0; i <= text.Length - 2; i += 2)
            {
                // Process groups of 2
                uint temp = (uint)AlphanumericCharset.IndexOf(text[i]) * 45;
                temp += (uint)AlphanumericCharset.IndexOf(text[i + 1]);
                ba.AppendBits(temp, 11);
            }
            if (i < text.Length)  // 1 character remaining
            {
                ba.AppendBits((uint)AlphanumericCharset.IndexOf(text[i]), 6);
            }

            return new QrSegment(Mode.Alphanumeric, text.Length, ba);
        }


        /// <summary>
        /// Creates a list of zero or more segments representing the specified text string.
        /// <para>
        /// The text may contain the full range of Unicode characters.
        /// </para>
        /// <para>
        /// The result may multiple segments with various encoding modes in order to minimize the length of the bit stream.
        /// </para>
        /// </summary>
        /// <param name="text">The text to be encoded.</param>
        /// <returns>The created mutable list of segments representing the specified text.</returns>
        /// <exception cref="ArgumentNullException"><c>text</c> is <c>null</c>.</exception>
        /// <remarks>
        /// The current implementation does not use multiple segments.
        /// </remarks>
        public static List<QrSegment> MakeSegments(string text)
        {
            Objects.RequireNonNull(text);

            // Select the most efficient segment encoding automatically
            var result = new List<QrSegment>();
            if (text == "")
            {
                // Leave result empty
            }
            else if (NumericRegex.IsMatch(text))
            {
                result.Add(MakeNumeric(text));
            }
            else if (AlphanumericRegex.IsMatch(text))
            {
                result.Add(MakeAlphanumeric(text));
            }
            else
            {
                result.Add(MakeBytes(Encoding.UTF8.GetBytes(text)));
            }

            return result;
        }


        /// <summary>
        /// Creates a segment representing an Extended Channel Interpretation
        /// (ECI) designator with the specified assignment value.
        /// </summary>
        /// <param name="assignVal">The ECI assignment number (see the AIM ECI specification).</param>
        /// <returns>The created segment containing the data.</returns>
        /// <exception cref="ArgumentOutOfRangeException"><c>assignVal</c>is outside the range [0, 10<sup>6</sup>).</exception>
        public static QrSegment MakeEci(int assignVal)
        {
            BitArray ba = new BitArray(0);
            if (assignVal < 0)
            {
                throw new ArgumentOutOfRangeException(nameof(assignVal), "ECI assignment value out of range");
            }

            if (assignVal < 1 << 7)
            {
                ba.AppendBits((uint)assignVal, 8);
            }
            else if (assignVal < 1 << 14)
            {
                ba.AppendBits(2, 2);
                ba.AppendBits((uint)assignVal, 14);
            }
            else if (assignVal < 1_000_000)
            {
                ba.AppendBits(6, 3);
                ba.AppendBits((uint)assignVal, 21);
            }
            else
            {
                throw new ArgumentOutOfRangeException(nameof(assignVal), "ECI assignment value out of range");
            }

            return new QrSegment(Mode.Eci, 0, ba);
        }

        #endregion


        #region Instance fields

        /// <summary>The encoding mode of this segment.</summary>
        /// <value>Encoding mode.</value>
        public Mode EncodingMode { get; }

        /// <summary>
        /// The length of this segment's unencoded data.
        /// <para>
        /// Measured in characters for numeric/alphanumeric/kanji mode,
        /// bytes for byte mode, and 0 for ECI mode.
        /// </para>
        /// <para>
        /// Different from the data's bit length.
        /// </para>
        /// </summary>
        /// <value>Length of the segment's unencoded data.</value>
        public int NumChars { get; }

        // The data bits of this segment. Not null. Accessed through GetData().
        private readonly BitArray _data;

        #endregion


        #region Constructor (low level)

        /// <summary>
        /// Initializes a QR code segment with the specified attributes and data.
        /// <para>
        /// The character count <paramref name="numChars"/> must agree with the mode and the bit array length,
        /// but the constraint isn't checked. The specified bit array is cloned.
        /// </para>
        /// </summary>
        /// <param name="mode">The segment mode used to encode this segment.</param>
        /// <param name="numChars">The data length in characters or bytes (depending on the segment mode).</param>
        /// <param name="data">The data bits.</param>
        /// <exception cref="ArgumentNullException"><paramref name="mode"/> or <paramref name="data"/> is <c>null</c>.</exception>
        /// <exception cref="ArgumentOutOfRangeException"><paramref name="numChars"/> is negative.</exception>
        public QrSegment(Mode mode, int numChars, BitArray data)
        {
            EncodingMode = Objects.RequireNonNull(mode);
            Objects.RequireNonNull(data);
            if (numChars < 0)
            {
                throw new ArgumentOutOfRangeException(nameof(numChars), "Invalid value");
            }

            NumChars = numChars;
            _data = (BitArray)data.Clone();  // Make defensive copy
        }

        #endregion


        #region Methods

        /// <summary>
        /// Returns a copy of this segment's data bits.
        /// </summary>
        /// <returns>A copy of the data bits.</returns>
        public BitArray GetData()
        {
            return (BitArray)_data.Clone();  // Make defensive copy
        }


        // Calculates the number of bits needed to encode the given segments at the given version.
        // Returns a non-negative number if successful. Otherwise returns -1 if a segment has too
        // many characters to fit its length field, or the total bits exceeds int.MaxValue.
        internal static int GetTotalBits(List<QrSegment> segs, int version)
        {
            Objects.RequireNonNull(segs);
            long result = 0;
            foreach (QrSegment seg in segs)
            {
                Objects.RequireNonNull(seg);
                int ccbits = seg.EncodingMode.NumCharCountBits(version);
                if (seg.NumChars >= 1 << ccbits)
                {
                    return -1;  // The segment's length doesn't fit the field's bit width
                }

                result += 4L + ccbits + seg._data.Length;
                if (result > int.MaxValue)
                {
                    return -1;  // The sum will overflow an int type
                }
            }
            return (int)result;
        }

        #endregion


        #region Constants

        /// <summary>
        /// Immutable regular expression describing all strings encodable in <i>numeric mode</i>.
        /// <para>
        /// A string is encodable iff each character is in the range 0 to 9.
        /// </para>
        /// </summary>
        /// <remarks>
        /// To test whether a string <c>s</c> is encodable:
        /// <code>
        /// bool ok = NumericRegex.IsMatch(s);
        /// </code> 
        /// </remarks>
        /// <value>Regular exprression describing strings encodable in numeric mode.</value>
        /// <seealso cref="MakeNumeric(string)"/>
        public static readonly Regex NumericRegex = new Regex("^[0-9]*$", RegexOptions.Compiled);

        /// <summary>
        /// Immutable regular expression describing all strings that are encodable in <i>alphanumeric mode</i>.
        /// <para>
        /// A string is encodable iff each character is in the following set: 0 to 9, A to Z
        /// (uppercase only), space, dollar, percent, asterisk, plus, hyphen, period, slash, colon.
        /// </para>
        /// </summary>
        /// <remarks>
        /// To test whether a string <c>s</c> is encodable:
        /// <code>
        /// bool ok = AlphanumericRegex.IsMatch(s);
        /// </code> 
        /// </remarks>
        /// <value>Regular exprression describing strings encodable in alphanumeric mode.</value>
        /// <seealso cref="MakeAlphanumeric(string)"/>
        public static readonly Regex AlphanumericRegex = new Regex("^[A-Z0-9 $%*+./:-]*$", RegexOptions.Compiled);


        // The set of all legal characters in alphanumeric mode, where
        // each character value maps to the index in the string.
        internal static readonly string AlphanumericCharset = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";

        #endregion


        #region Public helper enumeration

        /// <summary>
        /// Segment encoding mode.
        /// <para>
        /// Describes how text or binary data is encoded into bits.
        /// </para>
        /// </summary>
        public sealed class Mode
        {
            /// <summary>
            /// Numeric encoding mode.
            /// </summary>
            /// <value>Numeric encoding mode.</value>
            public static readonly Mode Numeric = new Mode(0x1, 10, 12, 14);

            /// <summary>
            /// Alphanumeric encoding mode.
            /// </summary>
            /// <value>Alphanumeric encoding mode.</value>
            public static readonly Mode Alphanumeric = new Mode(0x2, 9, 11, 13);

            /// <summary>
            /// Byte encoding mode.
            /// </summary>
            /// <value>Byte encoding mode.</value>
            public static readonly Mode Byte = new Mode(0x4, 8, 16, 16);

            /// <summary>
            /// Kanji encoding mode.
            /// </summary>
            /// <value>Kanji encoding mode.</value>
            public static readonly Mode Kanji = new Mode(0x8, 8, 10, 12);

            /// <summary>
            /// ECI encoding mode.
            /// </summary>
            /// <value>ECI encoding mode.</value>
            public static readonly Mode Eci = new Mode(0x7, 0, 0, 0);


            /// <summary>
            /// Mode indicator value.
            /// <para>
            /// 4 bit value in the QR segment header indicating the encoding mode.
            /// </para>
            /// </summary>
            /// <value>Mode indicator value</value>
            internal uint ModeBits { get; }


            /// <summary>
            /// Array of character count bit length.
            /// <para>
            /// Number of bits for character count in QR segment header.
            /// The three array values apply to versions 0 to 9, 10 to 26 and 27 to 40
            /// respectively. All array values are in the range [0, 16].
            /// </para>
            /// </summary>
            /// <value>Array of character count bit length</value>
            internal int[] NumBitsCharCount { get; }


            /// <summary>
            /// Returns the bith length of the character count in the QR segment header
            /// for the specified QR code version. The result is in the range [0, 16].
            /// </summary>
            /// <param name="ver">the QR code version (between 1 and 40)</param>
            /// <returns></returns>
            internal int NumCharCountBits(int ver)
            {
                Debug.Assert(QrCode.MinVersion <= ver && ver <= QrCode.MaxVersion);
                return NumBitsCharCount[(ver + 7) / 17];
            }

            // private constructor to initializes the constants
            private Mode(uint modeBits, params int[] numBitsCharCount)
            {
                ModeBits = modeBits;
                NumBitsCharCount = numBitsCharCount;
            }
        }

        #endregion

    }

}
