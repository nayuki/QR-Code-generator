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
using System.Text;
using Xunit;

namespace IO.Nayuki.QrCodeGen.Test
{
    public class QrSegmentEncodingTest
    {

        private static readonly string TextNumeric = "83930";

        private static readonly int BitLengthNumeric = 17;

        private static readonly byte[] BitsNumeric = { 139, 243, 0 };

        private static readonly string TextAlphanumeric = "$%*+-./ 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

        private static readonly int BitLengthAlphanumeric = 242;

        private static readonly byte[] BitsAlphanumeric = {
            43, 63,240, 245, 223, 12, 64, 232,
            162, 147, 168, 116,228, 172,  40, 21,
            170, 67, 243, 58, 211, 175, 81, 76,
            109, 33, 107, 218, 193, 225, 2
        };

        private static readonly string TextUtf8 = "😐ö€";

        private static readonly int BitLengthUtf8 = 72;

        private static readonly byte[] BitsUtf8 = { 15, 249, 25, 9, 195, 109, 71, 65, 53 };

        [Fact]
        void NumericEncoding()
        {
            QrSegment segment = QrSegment.MakeNumeric(TextNumeric);
            Assert.Equal(segment.EncodingMode, QrSegment.Mode.Numeric);
            Assert.Equal(TextNumeric.Length, segment.NumChars);

            BitArray data = segment.GetData();
            Assert.Equal(BitLengthNumeric, data.Length);

            Assert.Equal(BitsNumeric, BitArrayToByteArray(data));
        }

        [Fact]
        void RejectNonNumeric()
        {
            Assert.Throws<ArgumentOutOfRangeException>(() => QrSegment.MakeNumeric("abc"));
        }

        [Fact]
        void AlphanumericEncoding()
        {
            QrSegment segment = QrSegment.MakeAlphanumeric(TextAlphanumeric);
            Assert.Equal(segment.EncodingMode, QrSegment.Mode.Alphanumeric);
            Assert.Equal(TextAlphanumeric.Length, segment.NumChars);

            BitArray data = segment.GetData();
            Assert.Equal(BitLengthAlphanumeric, data.Length);

            Assert.Equal(BitsAlphanumeric, BitArrayToByteArray(data));
        }

        [Fact]
        void RejectNonAlphanumeric()
        {
            Assert.Throws<ArgumentOutOfRangeException>(() => QrSegment.MakeAlphanumeric("abc,def"));
        }

        [Fact]
        void AutoNumericEncoding()
        {
            List<QrSegment> segments = QrSegment.MakeSegments(TextNumeric);
            Assert.Single(segments);

            QrSegment segment = segments[0];
            Assert.Equal(segment.EncodingMode, QrSegment.Mode.Numeric);
            Assert.Equal(TextNumeric.Length, segment.NumChars);

            BitArray data = segment.GetData();
            Assert.Equal(BitLengthNumeric, data.Length);

            Assert.Equal(BitsNumeric, BitArrayToByteArray(data));
        }

        [Fact]
        void AutoAlphanumericEncoding()
        {
            List<QrSegment> segments = QrSegment.MakeSegments(TextAlphanumeric);
            Assert.Single(segments);

            QrSegment segment = segments[0];
            Assert.Equal(segment.EncodingMode, QrSegment.Mode.Alphanumeric);
            Assert.Equal(TextAlphanumeric.Length, segment.NumChars);

            BitArray data = segment.GetData();
            Assert.Equal(BitLengthAlphanumeric, data.Length);

            Assert.Equal(BitsAlphanumeric, BitArrayToByteArray(data));
        }

        [Fact]
        void Utf8Encoding()
        {
            List<QrSegment> segments = QrSegment.MakeSegments(TextUtf8);
            Assert.Single(segments);
            QrSegment segment = segments[0];
            Assert.Equal(segment.EncodingMode, QrSegment.Mode.Byte);
            Assert.Equal(Encoding.UTF8.GetBytes(TextUtf8).Length, segment.NumChars);

            BitArray data = segment.GetData();
            Assert.Equal(BitLengthUtf8, data.Length);

            Assert.Equal(BitsUtf8, BitArrayToByteArray(data));
        }

        [Fact]
        void EmptyTest()
        {
            List<QrSegment> segments = QrSegment.MakeSegments("");
            Assert.Empty(segments);
        }

        private static byte[] BitArrayToByteArray(BitArray buffer)
        {
            int len = buffer.Length;
            byte[] result = new byte[(len + 7) / 8];
            buffer.CopyTo(result, 0);
            return result;
        }
    }
}
