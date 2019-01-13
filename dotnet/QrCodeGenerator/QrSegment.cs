/* 
 * QR Code generator library (.NET)
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

namespace Io.Nayuki.QrCodeGen
{
    /// <summary>
    /// A segment of character/binary/control data in a QR Code symbol.
    /// </summary>
    /// <remarks>
    /// <para>Instances of this class are immutable.</para>
    /// <para>The mid-level way to create a segment is to take the payload data and call a
    /// static factory function such as <see cref="MakeNumeric(string)"/>. The low-level
    /// way to create a segment is to custom-make the bit buffer and call the
    /// <see cref="QrSegment(Mode, int, BitArray)"/> with appropriate values.</para>
    /// <para>This segment class imposes no length restrictions, but QR Codes have restrictions.
    /// Even in the most favorable conditions, a QR Code can only hold 7089 characters of data.
    /// Any segment longer than this is meaningless for the purpose of generating QR Codes.
    /// This class can represent kanji mode segments, but provides no help in encoding them
    /// - see <see cref="QrSegmentAdvanced"/> for full kanji support.</para>
    /// </remarks>
    public class QrSegment
    {
        #region Static factory functions (mid level)

        /// <summary>
        /// Returns a segment representing the specified binary data
        /// encoded in byte mode. All input byte arrays are acceptable.
        /// </summary>
        /// <remarks>
        /// Any text string can be converted to UTF-8 bytes (<c>Encoding.UTF8.GetBytes(s)</c>)
        /// and encoded as a byte mode segment.
        /// </remarks>
        /// <param name="data">the binary data (not <c>null</c>)</param>
        /// <returns>a segment (not <c>null</c>) containing the data</returns>
        /// <exception cref="ArgumentNullException">Thrown if the array is <c>null</c></exception>
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
        /// Returns a segment representing the specified string of decimal digits encoded in numeric mode.
        /// </summary>
        /// <param name="digits">the text (not <c>null</c>), with only digits from 0 to 9 allowed</param>
        /// <returns>a segment (not <c>null</c>) containing the text</returns>
        /// <exception cref="ArgumentNullException">Thrown if the string is <c>null</c></exception>
        /// <exception cref="ArgumentOutOfRangeException">Thrown if the string contains non-digit characters</exception>
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
        /// Returns a segment representing the specified text string encoded in alphanumeric mode.
        /// The characters allowed are: 0 to 9, A to Z(uppercase only), space,
        /// dollar, percent, asterisk, plus, hyphen, period, slash, colon.
        /// </summary>
        /// <param name="text">the text (not <c>null</c>), with only certain characters allowed</param>
        /// <returns>a segment (not <c>null</c>) containing the text</returns>
        /// <exception cref="ArgumentNullException">Thrown if the string is <c>null</c></exception>
        /// <exception cref="ArgumentOutOfRangeException">Thrown iif the string contains non-encodable characters</exception>
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
        /// Returns a list of zero or more segments to represent the specified Unicode text string.
        /// The result may use various segment modes and switch modes to optimize the length of the bit stream.
        /// </summary>
        /// <param name="text">the text to be encoded, which can be any Unicode string</param>
        /// <returns>a new mutable list (not <c>null</c>) of segments (not <c>null</c>) containing the text</returns>
        /// <exception cref="ArgumentNullException">Thrown if the text is <c>null</c></exception>
        public static List<QrSegment> MakeSegments(string text)
        {
            Objects.RequireNonNull(text);

            // Select the most efficient segment encoding automatically
            List<QrSegment> result = new List<QrSegment>();
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
        /// Returns a segment representing an Extended Channel Interpretation
        /// (ECI) designator with the specified assignment value.
        /// </summary>
        /// <param name="assignVal">the ECI assignment number (see the AIM ECI specification)</param>
        /// <returns>a segment (not <c>null</c>) containing the data</returns>
        /// <exception cref="ArgumentOutOfRangeException">Thrown if the value is outside the range [0, 10<sup>6</sup>)</exception>
        public static QrSegment MakeEci(int assignVal)
        {
            BitArray ba = new BitArray(0);
            if (assignVal < 0)
            {
                throw new ArgumentOutOfRangeException(nameof(assignVal), "ECI assignment value out of range");
            }

            if (assignVal < (1 << 7))
            {
                ba.AppendBits((uint)assignVal, 8);
            }
            else if (assignVal < (1 << 14))
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

        /// <summary>
        /// The mode indicator of this segment. Not <c>null</c>.
        /// </summary>
        public Mode EncodingMode { get; }

        /// <summary>
        /// The length of this segment's unencoded data. Measured in characters for
        /// numeric/alphanumeric/kanji mode, bytes for byte mode, and 0 for ECI mode.
        /// Always zero or positive. Not the same as the data's bit length.
        /// </summary>
        public int NumChars { get; }

        // The data bits of this segment. Not null. Accessed through GetData().
        private readonly BitArray _data;

        #endregion


        #region Constructor (low level)

        /// <summary>
        /// Constructs a QR Code segment with the specified attributes and data.
        /// The character count(numCh) must agree with the mode and the bit buffer length,
        /// but the constraint isn't checked. The specified bit buffer is cloned and stored.
        /// </summary>
        /// <param name="md">the mode (not <c>null</c>)</param>
        /// <param name="numCh">the data length in characters or bytes, which is non-negative</param>
        /// <param name="data">the data bits (not <c>null</c>)</param>
        /// <exception cref="ArgumentNullException">Thrown if the mode or data is <c>null</c></exception>
        /// <exception cref="ArgumentOutOfRangeException">Thrown if the character count is negative</exception>
        public QrSegment(Mode md, int numCh, BitArray data)
        {
            EncodingMode = Objects.RequireNonNull(md);
            Objects.RequireNonNull(data);
            if (numCh < 0)
            {
                throw new ArgumentOutOfRangeException(nameof(numCh), "Invalid value");
            }

            NumChars = numCh;
            _data = (BitArray)data.Clone();  // Make defensive copy
        }

        #endregion


        #region Methods

        /// <summary>
        /// Returns the data bits of this segment.
        /// </summary>
        /// <returns>a new copy of the data bits(not<c>null</c>)</returns>
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
                if (seg.NumChars >= (1 << ccbits))
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
        /// Describes precisely all strings that are encodable in numeric mode.
        /// </summary>
        /// <remarks>
        /// To test whether a string <c>s</c> is encodable:
        /// <code>
        /// bool ok = NumericRegex.IsMatch(s);
        /// </code> 
        /// A string is encodable iff each character is in the range 0 to 9.
        /// </remarks>
        /// <seealso cref="MakeNumeric(string)"/>
        public static readonly Regex NumericRegex = new Regex("^[0-9]*$", RegexOptions.Compiled);

        /// <summary>
        /// Describes precisely all strings that are encodable in alphanumeric mode.
        /// </summary>
        /// <remarks>
        /// To test whether a string <c>s</c> is encodable:
        /// <code>
        /// bool ok = AlphanumericRegex.IsMatch(s);
        /// </code> 
        /// A string is encodable iff each character is in the following set: 0 to 9, A to Z
        /// (uppercase only), space, dollar, percent, asterisk, plus, hyphen, period, slash, colon.
        /// </remarks>
        /// <seealso cref="MakeAlphanumeric(string)"/>
        public static readonly Regex AlphanumericRegex = new Regex("^[A-Z0-9 $%*+./:-]*$", RegexOptions.Compiled);


        // The set of all legal characters in alphanumeric mode, where
        // each character value maps to the index in the string.
        internal static readonly string AlphanumericCharset = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";

        #endregion


        #region Public helper enumeration

        /// <summary>
        /// Describes how a segment's data bits are interpreted.
        /// </summary>
        public sealed class Mode
        {
            public static readonly Mode Numeric = new Mode(0x1, 10, 12, 14);

            public static readonly Mode Alphanumeric = new Mode(0x2, 9, 11, 13);

            public static readonly Mode Byte = new Mode(0x4, 8, 16, 16);

            public static readonly Mode Kanji = new Mode(0x8, 8, 10, 12);

            public static readonly Mode Eci = new Mode(0x7, 0, 0, 0);

            // The mode indicator bits, which is a uint4 value (range 0 to 15).
            internal uint ModeBits { get; }

            // Number of character count bits for three different version ranges.
            internal int[] NumBitsCharCount { get; }

            // Returns the bit width of the character count field for a segment in this mode
            // in a QR Code at the given version number. The result is in the range [0, 16].
            internal int NumCharCountBits(int ver)
            {
                Debug.Assert(QrCode.MinVersion <= ver && ver <= QrCode.MaxVersion);
                return NumBitsCharCount[(ver + 7) / 17];
            }

            private Mode(uint modeBits, params int[] numBitsCharCount)
            {
                ModeBits = modeBits;
                NumBitsCharCount = numBitsCharCount;
            }
        }

        #endregion

    }

}
