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

using System.Drawing.Imaging;
using Xunit;
using static IO.Nayuki.QrCodeGen.QrCode;

namespace IO.Nayuki.QrCodeGen.Test
{
    public class PngTest
    {
        [Fact]
        private void PngImage()
        {
            var qrCode = EncodeText("The quick brown fox jumps over the lazy dog", Ecc.High);
            using (var bitmap = qrCode.ToBitmap(3, 4))
            {
                Assert.Equal(135, bitmap.Width);
                Assert.Equal(135, bitmap.Height);

                bitmap.Save("qrcode.png", ImageFormat.Png);
            }
        }
    }
}
