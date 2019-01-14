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


namespace IO.Nayuki.QrCodeGen.Test
{
    internal class TestHelper
    {
        internal static string[] ToStringArray(QrCode qrCode)
        {
            int size = qrCode.Size;
            string[] result = new string[size];

            for (int y = 0; y < size; y++)
            {
                char[] row = new char[size];
                for (int x = 0; x < size; x++)
                {
                    row[x] = qrCode.GetModule(x, y) ? 'X' : ' ';
                }
                result[y] = new string(row);
            }

            return result;
        }
    }
}
