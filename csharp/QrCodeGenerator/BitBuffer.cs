using System;
using System.Collections;
using System.Diagnostics;
using Util;

namespace QrCodeGenerator
{
    /// <summary>
    /// An appendable sequence of bits (0s and 1s). Mainly used by <see cref="QrSegment"/>.
    /// </summary>
    public class BitBuffer
    {
        private BitSet Data;
        private int BitLength;

        /// <summary>
        ///  Constructs an empty bit buffer (length 0).
        /// </summary>
        public BitBuffer() 
        {
            Data = new BitSet();
            BitLength = 0;
        }

        /// <summary>
        /// Returns the length of this sequence, which is a non-negative value.
        /// </summary>
        /// <returns>The length of this sequence</returns>
        public int GetBitLength()
        {
            return BitLength;
        }

        /// <summary>
        /// Returns the bit at the specified index, yielding 0 or 1.
        /// </summary>
        /// <param name="index">The index to get the bit at</param>
        /// <returns>The bit at the specified index</returns>
        /// <exception cref="IndexOutOfRangeException">If index &lt; 0 or index &#x2265; bitLength</exception>
        public int GetBit(int index)
        {
            if (index < 0 || index >= BitLength) 
                throw new IndexOutOfRangeException();
            return Data.Get(index) ? 1 : 0;
        }

        /// <summary>
        /// Appends the specified number of low-order bits of the specified value to this
        /// buffer. Requires 0 &#x2264; len &#x2264; 31 and 0 &#x2264; val &lt; 2<sup>len</sup>.
        /// </summary>
        /// <param name="value">The value to append</param>
        /// <param name="length">The number of low-order bits in the value to take</param>
        /// <exception cref="ArgumentException">If the value or number of bits is out of range</exception>
        /// <exception cref="InvalidOperationException">If appending the data</exception>
        public void AppendBits(int value, int length)
        {
            if (length < 0 || length > 31 || value >> length != 0) 
                throw new ArgumentException("Value out of range");
            if (int.MaxValue - BitLength < length) 
                throw new InvalidOperationException("Maximum length reached");

            for (int i = length - 1; i >= 0; i--, BitLength++)
                Data.Set(BitLength, QrCode.GetBit(value, i));
        }

        /// <summary>
        /// Appends the content of the specified bit buffer to this buffer.
        /// </summary>
        /// <param name="bitBuffer">The bit buffer whose data to append (not <c>null</c>)</param>
        /// <exception cref="ArgumentNullException">If the bit buffer is <c>null</c></exception>
        /// <exception cref="InvalidOperationException">If appending the data would make bitLength exceed Integer.MaxValue</exception>
        public void AppendData(BitBuffer bitBuffer)
        {
            if (bitBuffer == null) 
                throw new ArgumentNullException("BitBuffer is null!"); 
            if (int.MaxValue - BitLength < bitBuffer.GetBitLength()) 
                throw new Exception("Maximum length reached");
            
            for (int index = 0; index < bitBuffer.GetBitLength(); index++, BitLength++)
                Data.Set(BitLength, bitBuffer.Data.Get(index));
        }

        /// <summary>
        /// Returns a new copy of this buffer.
        /// </summary>
        /// <returns>A new copy of this buffer (not <c>null</c>)</returns>
        public BitBuffer Clone()
        {
            var result = new BitBuffer().Clone();
            result.Data = (BitSet)result.Data.Clone();
            return result;
        }
    }
}
