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

namespace IO.Nayuki.QrCodeGen
{
    /// <summary>
    /// Provides extension methods for the <see cref="BitArray"/> class.
    /// </summary>
    public static class BitArrayExtensions
    {

        /// <summary>
        /// Appends the specified number of low-order bits of the specified value to this
        /// bit array. Requires 0 &#x2264; len &#x2264; 31 and 0 &#x2264; val &lt; 2<sup>len</sup>.
        /// </summary>
        /// <param name="bitArray">this bit array</param>
        /// <param name="val">the value to append</param>
        /// <param name="len">the number of low-order bits in the value to take</param>
        /// <exception cref="ArgumentOutOfRangeException">Thrown if the value or number of bits is out of range</exception>
        /// <exception cref="InvalidOperationException">Thrown if appending the data would make bit length exceed <see cref="int.MaxValue"/></exception>
        public static void AppendBits(this BitArray bitArray, uint val, int len)
        {
            if (len < 0 || len > 31 || (val >> len) != 0)
            {
                throw new ArgumentOutOfRangeException(nameof(len), "'len' out of range");
            }

            if (len < 0 || len > 31 || (val >> len) != 0)
            {
                throw new ArgumentOutOfRangeException(nameof(val), "'val' out of range");
            }

            int bitLength = bitArray.Length;
            if (int.MaxValue - bitLength < len)
            {
                throw new InvalidOperationException("Maximum length reached");
            }

            bitArray.Length = bitLength + len;
            uint mask = 1U << (len - 1);
            for (int i = bitLength; i < bitLength + len; i++) // Append bit by bit
            {
                if ((val & mask) != 0)
                {
                    bitArray.Set(i, true);
                }

                mask >>= 1;
            }
        }


        /// <summary>
        /// Appends the content of the specified bit array to this array.
        /// </summary>
        /// <param name="bitArray">this bit array</param>
        /// <param name="otherArray">the bit array whose data to append (not <c>null</c>)</param>
        /// <exception cref="ArgumentNullException">Thrown if the bit array is <c>null</c></exception>
        /// <exception cref="InvalidOperationException">Thrown if appending the data would make the bit length exceed <see cref="int.MaxValue"/></exception>
        public static void AppendData(this BitArray bitArray, BitArray otherArray)
        {
            Objects.RequireNonNull(otherArray);
            int bitLength = bitArray.Length;
            if (int.MaxValue - bitLength < otherArray.Length)
            {
                throw new InvalidOperationException("Maximum length reached");
            }

            bitArray.Length = bitLength + otherArray.Length;
            for (int i = 0; i < otherArray.Length; i++, bitLength++)  // Append bit by bit
            {
                if (otherArray[i])
                {
                    bitArray.Set(bitLength, true);
                }
            }
        }

    }
}

