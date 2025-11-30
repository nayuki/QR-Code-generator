/*
 * QR Code generator library (C# port)
 *
 * Ported from Java version in this repository.
 * Copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/qr-code-generator-library
 */
using System;
using System.Collections.Generic;
using System.Text;
using System.Text.RegularExpressions;

namespace Io.Nayuki.QrCodeGen;

/// <summary>
/// A segment of character/binary/control data in a QR Code symbol. Instances are immutable.
/// </summary>
public sealed class QrSegment {
    /*---- Static factory functions (mid level) ----*/

    /// <summary>
    /// Returns a segment representing the specified binary data encoded in byte mode.
    /// </summary>
    public static QrSegment MakeBytes(byte[] data) {
        if (data == null) throw new ArgumentNullException(nameof(data));
        var bb = new BitBuffer();
        foreach (byte b in data)
            bb.AppendBits(b & 0xFF, 8);
        return new QrSegment(Mode.BYTE, data.Length, bb);
    }

    /// <summary>
    /// Returns a segment representing the specified string of decimal digits encoded in numeric mode.
    /// </summary>
    public static QrSegment MakeNumeric(ReadOnlySpan<char> digits) {
        if (!IsNumeric(digits))
            throw new ArgumentException("String contains non-numeric characters");
        var bb = new BitBuffer();
        int i = 0;
        while (i < digits.Length) {
            int n = Math.Min(digits.Length - i, 3);
            int val = int.Parse(digits.Slice(i, n));
            bb.AppendBits(val, n * 3 + 1);
            i += n;
        }
        return new QrSegment(Mode.NUMERIC, digits.Length, bb);
    }

    /// <summary>
    /// Returns a segment representing the specified text string encoded in alphanumeric mode.
    /// </summary>
    public static QrSegment MakeAlphanumeric(ReadOnlySpan<char> text) {
        if (!IsAlphanumeric(text))
            throw new ArgumentException("String contains unencodable characters in alphanumeric mode");
        var bb = new BitBuffer();
        int i;
        for (i = 0; i <= text.Length - 2; i += 2) {
            int temp = AlphanumericCharset.IndexOf(text[i]) * 45;
            temp += AlphanumericCharset.IndexOf(text[i + 1]);
            bb.AppendBits(temp, 11);
        }
        if (i < text.Length)
            bb.AppendBits(AlphanumericCharset.IndexOf(text[i]), 6);
        return new QrSegment(Mode.ALPHANUMERIC, text.Length, bb);
    }

    /// <summary>
    /// Returns a list of zero or more segments to represent the specified Unicode text string.
    /// </summary>
    public static List<QrSegment> MakeSegments(string text) {
        if (text == null) throw new ArgumentNullException(nameof(text));
        var result = new List<QrSegment>();
        if (text.Length == 0) {
            // leave empty
        } else if (IsNumeric(text.AsSpan()))
            result.Add(MakeNumeric(text.AsSpan()));
        else if (IsAlphanumeric(text.AsSpan()))
            result.Add(MakeAlphanumeric(text.AsSpan()));
        else
            result.Add(MakeBytes(Encoding.UTF8.GetBytes(text)));
        return result;
    }

    /// <summary>
    /// Returns a segment representing an Extended Channel Interpretation (ECI) designator with the specified assignment value.
    /// </summary>
    public static QrSegment MakeEci(int assignVal) {
        var bb = new BitBuffer();
        if (assignVal < 0)
            throw new ArgumentException("ECI assignment value out of range");
        else if (assignVal < (1 << 7))
            bb.AppendBits(assignVal, 8);
        else if (assignVal < (1 << 14)) {
            bb.AppendBits(0b10, 2);
            bb.AppendBits(assignVal, 14);
        } else if (assignVal < 1_000_000) {
            bb.AppendBits(0b110, 3);
            bb.AppendBits(assignVal, 21);
        } else
            throw new ArgumentException("ECI assignment value out of range");
        return new QrSegment(Mode.ECI, 0, bb);
    }

    /// <summary>Tests whether the specified string can be encoded as a segment in numeric mode.</summary>
    public static bool IsNumeric(ReadOnlySpan<char> text) => NumericRegex.IsMatch(text.ToString());

    /// <summary>Tests whether the specified string can be encoded as a segment in alphanumeric mode.</summary>
    public static bool IsAlphanumeric(ReadOnlySpan<char> text) => AlnumRegex.IsMatch(text.ToString());


    /*---- Instance fields ----*/
    public Mode mode; // public final in Java
    public int numChars; // length in characters/bytes
    internal BitBuffer data; // stored clone

    /*---- Constructor (low level) ----*/
    public QrSegment(Mode md, int numCh, BitBuffer data) {
        // 'Mode' is an enum (non-nullable), so no null-coalescing needed
        mode = md;
        this.data = (BitBuffer)data.Clone();
        if (numCh < 0) throw new ArgumentException("Invalid value");
        numChars = numCh;
    }

    /*---- Methods ----*/
    public BitBuffer GetData() => (BitBuffer)data.Clone();

    // Calculates total bits for segments at given version, or -1 on overflow/too-long
    internal static int GetTotalBits(List<QrSegment> segs, int version) {
        if (segs == null) throw new ArgumentNullException(nameof(segs));
        long result = 0;
        foreach (var seg in segs) {
            if (seg == null) throw new ArgumentNullException(nameof(segs));
            int ccbits = seg.mode.NumCharCountBits(version);
            if (seg.numChars >= (1 << ccbits))
                return -1;
            result += 4L + ccbits + seg.data.BitLength;
            if (result > int.MaxValue)
                return -1;
        }
        return (int)result;
    }

    /*---- Constants ----*/
    private static readonly Regex NumericRegex = new("^[0-9]*$");
    private static readonly Regex AlnumRegex = new("^[A-Z0-9 $%*+./:-]*$");
    internal const string AlphanumericCharset = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";

    /*---- Public helper enumeration ----*/
    public enum Mode {
        NUMERIC = 0,
        ALPHANUMERIC = 1,
        BYTE = 2,
        KANJI = 3,
        ECI = 4,
    }
}

internal static class QrSegmentModeExtensions {
    // Map to mode bits and char count bits as per Java enum
    public static int ModeBits(this QrSegment.Mode mode) => mode switch {
        QrSegment.Mode.NUMERIC => 0x1,
        QrSegment.Mode.ALPHANUMERIC => 0x2,
        QrSegment.Mode.BYTE => 0x4,
        QrSegment.Mode.KANJI => 0x8,
        QrSegment.Mode.ECI => 0x7,
        _ => throw new ArgumentOutOfRangeException(nameof(mode))
    };

    private static ReadOnlySpan<int> CharCountBits(this QrSegment.Mode mode) => mode switch {
        QrSegment.Mode.NUMERIC => new int[] { 10, 12, 14 },
        QrSegment.Mode.ALPHANUMERIC => new int[] { 9, 11, 13 },
        QrSegment.Mode.BYTE => new int[] { 8, 16, 16 },
        QrSegment.Mode.KANJI => new int[] { 8, 10, 12 },
        QrSegment.Mode.ECI => new int[] { 0, 0, 0 },
        _ => throw new ArgumentOutOfRangeException(nameof(mode))
    };

    public static int NumCharCountBits(this QrSegment.Mode mode, int ver) {
        if (ver >= 1 && ver <= 9) return mode.CharCountBits()[0];
        else if (ver <= 26) return mode.CharCountBits()[1];
        else if (ver <= 40) return mode.CharCountBits()[2];
        else throw new ArgumentOutOfRangeException(nameof(ver));
    }
}
