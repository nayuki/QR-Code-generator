/*
 * QR Code generator library (C# port)
 *
 * Ported from Java version in this repository.
 * Copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/qr-code-generator-library
 */
using System;
using System.Collections.Generic;

namespace Io.Nayuki.QrCodeGen;

/// <summary>
/// An appendable sequence of bits (0s and 1s). Mainly used by <see cref="QrSegment"/>.
/// </summary>
public sealed class BitBuffer : ICloneable {
    private readonly List<bool> data;
    private int length;

    /// <summary>Constructs an empty bit buffer (length 0).</summary>
    public BitBuffer() {
        data = new List<bool>(64);
        length = 0;
    }

    /// <summary>Returns the length of this sequence, which is a non-negative value.</summary>
    public int BitLength => length;

    /// <summary>Returns the bit at the specified index, yielding 0 or 1.</summary>
    public int GetBit(int index) {
        if (index < 0 || index >= length)
            throw new ArgumentOutOfRangeException(nameof(index));
        return data[index] ? 1 : 0;
    }

    /// <summary>
    /// Appends the specified number of low-order bits of the specified value to this buffer.
    /// Requires 0 ≤ len ≤ 31 and 0 ≤ val &lt; 2^len.
    /// </summary>
    public void AppendBits(int val, int len) {
        if (len < 0 || len > 31 || (val >> len) != 0)
            throw new ArgumentException("Value out of range");
        if (int.MaxValue - length < len)
            throw new InvalidOperationException("Maximum length reached");
        for (int i = len - 1; i >= 0; i--) { // Append bit by bit, from high to low
            bool bit = QrCode.GetBit(val, i);
            data.Add(bit);
            length++;
        }
    }

    /// <summary>Appends the content of the specified bit buffer to this buffer.</summary>
    public void AppendData(BitBuffer bb) {
        if (bb == null) throw new ArgumentNullException(nameof(bb));
        if (int.MaxValue - length < bb.length)
            throw new InvalidOperationException("Maximum length reached");
        for (int i = 0; i < bb.length; i++) {
            data.Add(bb.data[i]);
            length++;
        }
    }

    public object Clone() {
        var copy = new BitBuffer();
        copy.data.AddRange(this.data);
        copy.length = this.length;
        return copy;
    }
}
