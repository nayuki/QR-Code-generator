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
using Xunit;

namespace Io.Nayuki.QrCodeGen.Test
{
    public class BitArrayExtensionsTest
    {
        [Fact]
        private void AppendInt1()
        {
            var ba = new BitArray(0);
            ba.AppendBits(18, 6);

            Assert.Equal(6, ba.Length);

            Assert.False(ba[0]);
            Assert.True(ba[1]);
            Assert.False(ba[2]);
            Assert.False(ba[3]);
            Assert.True(ba[4]);
            Assert.False(ba[5]);
        }

        [Fact]
        private void AppendInt2()
        {
            var ba = new BitArray(0);
            ba.AppendBits(18, 6);

            ba.AppendBits(3, 2);

            Assert.Equal(8, ba.Length);

            Assert.False(ba[0]);
            Assert.True(ba[1]);
            Assert.False(ba[2]);
            Assert.False(ba[3]);
            Assert.True(ba[4]);
            Assert.False(ba[5]);
            Assert.True(ba[6]);
            Assert.True(ba[7]);
        }

        [Fact]
        private void AppendExtraBits()
        {
            var ba = new BitArray(0);

            Assert.Throws<ArgumentOutOfRangeException>(() =>
            {
                ba.AppendBits(128, 4);
            });
        }
    }
}
