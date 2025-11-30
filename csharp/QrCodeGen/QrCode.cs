/*
 * QR Code generator library (C# port)
 *
 * Ported from Java version in this repository.
 * Copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/qr-code-generator-library
 */
using System;
using System.Collections.Generic;
using System.Linq;

namespace Io.Nayuki.QrCodeGen;

/// <summary>
/// A QR Code symbol, which is a type of two-dimension barcode.
/// Instances of this class represent an immutable square grid of dark and light cells.
/// </summary>
public sealed class QrCode {
    /*---- Public constants ----*/
    public const int MIN_VERSION = 1;
    public const int MAX_VERSION = 40;

    /*---- Fields ----*/
    public int Version { get; }
    public Ecc ErrorCorrectionLevel { get; }
    public int Mask { get; }
    public int Size { get; }

    // 2D array of modules: true = dark, false = light, null = unset
    private readonly bool?[][] modules;
    private readonly bool[][] isFunction;

    /*---- Static factory functions (high level) ----*/
    public static QrCode EncodeText(string text, Ecc ecl) {
        if (text == null) throw new ArgumentNullException(nameof(text));
        var segs = QrSegment.MakeSegments(text);
        return EncodeSegments(segs, ecl);
    }

    public static QrCode EncodeBinary(byte[] data, Ecc ecl) {
        if (data == null) throw new ArgumentNullException(nameof(data));
        var seg = QrSegment.MakeBytes(data);
        return EncodeSegments(new List<QrSegment> { seg }, ecl);
    }

    /*---- Static factory functions (mid level) ----*/
    public static QrCode EncodeSegments(List<QrSegment> segs, Ecc ecl, int minVersion = MIN_VERSION, int maxVersion = MAX_VERSION, int mask = -1, bool boostEcl = true) {
        if (segs == null) throw new ArgumentNullException(nameof(segs));
        if (!(MIN_VERSION <= minVersion && minVersion <= maxVersion && maxVersion <= MAX_VERSION) || !(mask == -1 || (0 <= mask && mask <= 7)))
            throw new ArgumentException("Invalid value");

        int version;
        int dataUsedBits = -1;
        for (version = minVersion; version <= maxVersion; version++) {
            int dataCapacityBits = GetNumDataCodewords(version, ecl) * 8;
            dataUsedBits = QrSegment.GetTotalBits(segs, version);
            if (dataUsedBits != -1 && dataUsedBits <= dataCapacityBits)
                break;
            if (version >= maxVersion) {
                string msg = "Segment too long";
                if (dataUsedBits != -1)
                    msg = $"Data length = {dataUsedBits} bits, Max capacity = {dataCapacityBits} bits";
                throw new DataTooLongException(msg);
            }
        }

        if (boostEcl) {
            foreach (var ne in new[] { Ecc.MEDIUM, Ecc.QUARTILE, Ecc.HIGH }) {
                if (dataUsedBits <= GetNumDataCodewords(version, ne) * 8)
                    ecl = ne;
            }
        }

        // Concatenate all segments to create the data bit string
        var bb = new BitBuffer();
        foreach (var seg in segs) {
            bb.AppendBits(seg.mode.ModeBits(), 4);
            bb.AppendBits(seg.numChars, seg.mode.NumCharCountBits(version));
            bb.AppendData(seg.GetData());
        }
        // Add terminator & pad to a byte array of data codewords
        int dataCapacityBits2 = GetNumDataCodewords(version, ecl) * 8;  // Number of data bits available
        if (bb.BitLength > dataCapacityBits2)
            throw new DataTooLongException("Data length inconsistent with capacity");
        int terminatorBits = Math.Min(4, dataCapacityBits2 - bb.BitLength);
        bb.AppendBits(0, terminatorBits);
        int pad = (8 - (bb.BitLength % 8)) % 8;
        bb.AppendBits(0, pad);
        var data = new byte[GetNumDataCodewords(version, ecl)];
        int numDataBytes = bb.BitLength / 8; // bb is padded to byte boundary above
        for (int i = 0; i < numDataBytes; i++) {
            int val = 0;
            for (int j = 0; j < 8; j++)
                val = (val << 1) | bb.GetBit(i * 8 + j);
            data[i] = (byte)val;
        }
        for (int i = numDataBytes; i < data.Length; i++)
            data[i] = (byte)((i % 2 == 0) ? 0xEC : 0x11);

        var qr = new QrCode(version, ecl, data, mask);
        return qr;
    }

    /*---- Constructor (low level) ----*/
    public QrCode(int version, Ecc ecl, byte[] dataCodewords, int mask) {
        if (!(MIN_VERSION <= version && version <= MAX_VERSION)) throw new ArgumentException("Version out of range");
        if (!(mask == -1 || (0 <= mask && mask <= 7))) throw new ArgumentException("Mask out of range");
        Version = version;
        ErrorCorrectionLevel = ecl;
        Size = version * 4 + 17;
        modules = Enumerable.Range(0, Size).Select(_ => new bool?[Size]).ToArray();
        isFunction = Enumerable.Range(0, Size).Select(_ => new bool[Size]).ToArray();

        // Draw function patterns, then draw data & ECC codewords interleaved
        DrawFunctionPatterns();
        var full = AddEccAndInterleave(dataCodewords);
        DrawCodewords(full);

        // Handle masking
        if (mask == -1) {
            int minPenalty = int.MaxValue;
            int bestMask = 0;
            for (int i = 0; i < 8; i++) {
                ApplyMask(i);
                DrawFormatBits(i);
                int penalty = GetPenaltyScore();
                if (penalty < minPenalty) { minPenalty = penalty; bestMask = i; }
                ApplyMask(i); // Undo
            }
            Mask = bestMask;
            ApplyMask(Mask);
            DrawFormatBits(Mask);
        } else {
            Mask = mask;
            ApplyMask(Mask);
            DrawFormatBits(Mask);
        }
    }

    /*---- Public instance methods ----*/
    public bool GetModule(int x, int y) {
        return modules[y][x] ?? false;
    }

    /*---- Private drawing methods ----*/
    private void DrawFunctionPatterns() {
        // Timing patterns
        for (int i = 0; i < Size; i++) {
            SetFunctionModule(6, i, i % 2 == 0);
            SetFunctionModule(i, 6, i % 2 == 0);
        }
        // Finder patterns
        DrawFinderPattern(3, 3);
        DrawFinderPattern(Size - 4, 3);
        DrawFinderPattern(3, Size - 4);
        // Alignment patterns
        var align = GetAlignmentPatternPositions();
        int numAlign = align.Length;
        for (int i = 0; i < numAlign; i++) {
            for (int j = 0; j < numAlign; j++) {
                if (!(i == 0 && j == 0 || i == 0 && j == numAlign - 1 || i == numAlign - 1 && j == 0))
                    DrawAlignmentPattern(align[i], align[j]);
            }
        }
        // Format and version (format dummy, overwritten after masking)
        DrawFormatBits(0);
        DrawVersion();
    }

    // Draws a 9x9 finder pattern including the border separator,
    // with the center module at (x, y). Modules can be out of bounds.
    private void DrawFinderPattern(int x, int y) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.Max(Math.Abs(dx), Math.Abs(dy));
                int xx = x + dx, yy = y + dy;
                if (0 <= xx && xx < Size && 0 <= yy && yy < Size)
                    SetFunctionModule(xx, yy, dist != 2 && dist != 4);
            }
        }
    }

    private void DrawVersion() {
        if (Version < 7) return;
        int rem = Version;
        for (int i = 0; i < 12; i++)
            rem = (rem << 1) ^ ((rem >> 11) * 0x1F25);
        int bits = (Version << 12) | rem; // 18 bits
        for (int i = 0; i < 18; i++) {
            bool bit = ((bits >> i) & 1) != 0;
            int a = Size - 11 + (i % 3);
            int b = i / 3;
            SetFunctionModule(a, b, bit);
            SetFunctionModule(b, a, bit);
        }
    }

    private void DrawAlignmentPattern(int x, int y) {
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++)
                SetFunctionModule(x + dx, y + dy, Math.Max(Math.Abs(dx), Math.Abs(dy)) != 1);
    }

    private void SetFunctionModule(int x, int y, bool dark) {
        modules[y][x] = dark;
        isFunction[y][x] = true;
    }

    private void DrawCodewords(byte[] data) {
        if (data.Length != GetNumRawDataModules(Version) / 8)
            throw new ArgumentException();
        int i = 0;
        for (int right = Size - 1; right >= 1; right -= 2) {
            if (right == 6)
                right = 5;
            for (int vert = 0; vert < Size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    bool upward = ((right + 1) & 2) == 0;
                    int y = upward ? Size - 1 - vert : vert;
                    if (!isFunction[y][x] && i < data.Length * 8) {
                        modules[y][x] = GetBit(data[i >> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void DrawFormatBits(int msk) {
        int data = ErrorCorrectionLevel.FormatBits() << 3 | msk;
        int rem = data;
        for (int i = 0; i < 10; i++)
            rem = (rem << 1) ^ ((rem >> 9) * 0x537);
        int bits = (data << 10) | rem;
        bits ^= 0x5412;
        for (int i = 0; i <= 5; i++) SetFunctionModule(8, i, ((bits >> i) & 1) != 0);
        SetFunctionModule(8, 7, ((bits >> 6) & 1) != 0);
        SetFunctionModule(8, 8, ((bits >> 7) & 1) != 0);
        SetFunctionModule(7, 8, ((bits >> 8) & 1) != 0);
        for (int i = 9; i < 15; i++) SetFunctionModule(14 - i, 8, ((bits >> i) & 1) != 0);
        for (int i = 0; i < 8; i++) SetFunctionModule(Size - 1 - i, 8, ((bits >> i) & 1) != 0);
        for (int i = 8; i < 15; i++) SetFunctionModule(8, Size - 15 + i, ((bits >> i) & 1) != 0);
        SetFunctionModule(8, Size - 8, true);
    }

    private void ApplyMask(int mask) {
        for (int y = 0; y < Size; y++) {
            for (int x = 0; x < Size; x++) {
                if (isFunction[y][x]) continue;
                bool invert = MaskFunc(mask, x, y);
                if (invert) modules[y][x] = !(modules[y][x] ?? false);
            }
        }
    }

    private static bool MaskFunc(int mask, int x, int y) => mask switch {
        0 => (x + y) % 2 == 0,
        1 => y % 2 == 0,
        2 => x % 3 == 0,
        3 => (x + y) % 3 == 0,
        4 => ((x / 3) + (y / 2)) % 2 == 0,
        5 => (x * y) % 2 + (x * y) % 3 == 0,
        6 => ((x * y) % 2 + (x * y) % 3) % 2 == 0,
        7 => ((x + y) % 2 + (x * y) % 3) % 2 == 0,
        _ => throw new ArgumentOutOfRangeException(nameof(mask))
    };

    private int GetPenaltyScore() {
        int result = 0;
        // Rows
        for (int y = 0; y < Size; y++) {
            bool runColor = false;
            int runX = 0;
            int[] runHistory = new int[7];
            for (int x = 0; x < Size; x++) {
                if ((modules[y][x] ?? false) == runColor) {
                    runX++;
                    if (runX == 5) result += 3;
                    else if (runX > 5) result++;
                } else {
                    FinderPenaltyAddHistory(runX, runHistory);
                    if (!runColor)
                        result += FinderPenaltyCountPatterns(runHistory) * 40;
                    runColor = modules[y][x] ?? false;
                    runX = 1;
                }
            }
            result += FinderPenaltyTerminateAndCount(runColor, runX, runHistory) * 40;
        }
        // Columns
        for (int x = 0; x < Size; x++) {
            bool runColor = false;
            int runY = 0;
            int[] runHistory = new int[7];
            for (int y = 0; y < Size; y++) {
                if ((modules[y][x] ?? false) == runColor) {
                    runY++;
                    if (runY == 5) result += 3;
                    else if (runY > 5) result++;
                } else {
                    FinderPenaltyAddHistory(runY, runHistory);
                    if (!runColor)
                        result += FinderPenaltyCountPatterns(runHistory) * 40;
                    runColor = modules[y][x] ?? false;
                    runY = 1;
                }
            }
            result += FinderPenaltyTerminateAndCount(runColor, runY, runHistory) * 40;
        }
        // 2x2 blocks
        for (int y = 0; y < Size - 1; y++)
            for (int x = 0; x < Size - 1; x++) {
                bool c = modules[y][x] ?? false;
                if (c == (modules[y][x + 1] ?? false) && c == (modules[y + 1][x] ?? false) && c == (modules[y + 1][x + 1] ?? false))
                    result += 3;
            }
        // Balance of dark modules
        int dark = 0;
        foreach (var row in modules)
            foreach (var m in row)
                if (m == true) dark++;
        int total = Size * Size;
        int k = (Math.Abs(dark * 20 - total * 10) + total - 1) / total - 1;
        result += k * 10;
        return result;
    }

    private int FinderPenaltyCountPatterns(int[] runHistory) {
        int n = runHistory[1];
        bool core = n > 0 && runHistory[2] == n && runHistory[3] == n * 3 && runHistory[4] == n && runHistory[5] == n;
        return (core && runHistory[0] >= n * 4 && runHistory[6] >= n ? 1 : 0)
             + (core && runHistory[6] >= n * 4 && runHistory[0] >= n ? 1 : 0);
    }

    private int FinderPenaltyTerminateAndCount(bool currentRunColor, int currentRunLength, int[] runHistory) {
        if (currentRunColor) {
            FinderPenaltyAddHistory(currentRunLength, runHistory);
            currentRunLength = 0;
        }
        currentRunLength += Size;
        FinderPenaltyAddHistory(currentRunLength, runHistory);
        return FinderPenaltyCountPatterns(runHistory);
    }

    private void FinderPenaltyAddHistory(int currentRunLength, int[] runHistory) {
        if (runHistory[0] == 0)
            currentRunLength += Size;
        Array.Copy(runHistory, 0, runHistory, 1, runHistory.Length - 1);
        runHistory[0] = currentRunLength;
    }

    /*---- Reed-Solomon and interleaving ----*/
    private byte[] AddEccAndInterleave(byte[] data) {
        if (data == null) throw new ArgumentNullException(nameof(data));
        if (data.Length != GetNumDataCodewords(Version, ErrorCorrectionLevel))
            throw new ArgumentException();

        int numBlocks = NUM_ERROR_CORRECTION_BLOCKS[(int)ErrorCorrectionLevel][Version];
        int blockEccLen = ECC_CODEWORDS_PER_BLOCK[(int)ErrorCorrectionLevel][Version];
        int rawCodewords = GetNumRawDataModules(Version) / 8;
        int numShortBlocks = numBlocks - rawCodewords % numBlocks;
        int shortBlockLen = rawCodewords / numBlocks;

        var blocks = new byte[numBlocks][];
        var rsDiv = ReedSolomonComputeDivisor(blockEccLen);
        for (int i = 0, k = 0; i < numBlocks; i++) {
            int datLen = shortBlockLen - blockEccLen + (i < numShortBlocks ? 0 : 1);
            var dat = new byte[datLen];
            Array.Copy(data, k, dat, 0, datLen);
            k += datLen;
            var block = new byte[shortBlockLen + 1];
            Array.Copy(dat, 0, block, 0, datLen);
            var ecc = ReedSolomonComputeRemainder(dat, rsDiv);
            Array.Copy(ecc, 0, block, block.Length - blockEccLen, ecc.Length);
            blocks[i] = block;
        }

        var result = new byte[rawCodewords];
        for (int i = 0, k = 0; i < blocks[0].Length; i++) {
            for (int j = 0; j < blocks.Length; j++) {
                if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) {
                    result[k] = blocks[j][i];
                    k++;
                }
            }
        }
        return result;
    }

    private static byte[] ReedSolomonComputeDivisor(int degree) {
        if (degree < 1 || degree > 255) throw new ArgumentException("Degree out of range");
        var result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < result.Length; j++) {
                result[j] = (byte)ReedSolomonMultiply(result[j] & 0xFF, root);
                if (j + 1 < result.Length)
                    result[j] ^= result[j + 1];
            }
            root = ReedSolomonMultiply(root, 0x02);
        }
        return result;
    }

    private static byte[] ReedSolomonComputeRemainder(byte[] data, byte[] divisor) {
        var result = new byte[divisor.Length];
        foreach (byte b in data) {
            int factor = (b ^ result[0]) & 0xFF;
            Array.Copy(result, 1, result, 0, result.Length - 1);
            result[^1] = 0;
            for (int i = 0; i < result.Length; i++)
                result[i] ^= (byte)ReedSolomonMultiply(divisor[i] & 0xFF, factor);
        }
        return result;
    }

    private static int ReedSolomonMultiply(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >> 7) * 0x11D);
            z ^= ((y >> i) & 1) * x;
        }
        return z & 0xFF;
    }

    /*---- Tables ----*/
    public enum Ecc { LOW = 0, MEDIUM = 1, QUARTILE = 2, HIGH = 3 }

    private static readonly sbyte[][] ECC_CODEWORDS_PER_BLOCK = new sbyte[][] {
        new sbyte[] { -1,  7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30 },
        new sbyte[] { -1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28 },
        new sbyte[] { -1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30 },
        new sbyte[] { -1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30 }
    };
    private static readonly sbyte[][] NUM_ERROR_CORRECTION_BLOCKS = new sbyte[][] {
        new sbyte[] { -1,  1, 1, 1, 1, 1, 2, 2, 2, 2, 4,  4,  4,  4,  4,  6,  6,  6,  6,  7,  8,  8,  9,  9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25 },
        new sbyte[] { -1,  1, 1, 1, 2, 2, 4, 4, 4, 5, 5,  5,  8,  9,  9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49 },
        new sbyte[] { -1,  1, 1, 2, 2, 4, 4, 6, 6, 8, 8,  8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68 },
        new sbyte[] { -1,  1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81 }
    };

    internal static int GetNumDataCodewords(int ver, Ecc ecl) => GetNumRawDataModules(ver) / 8
        - (ECC_CODEWORDS_PER_BLOCK[(int)ecl][ver] & 0xFF) * (NUM_ERROR_CORRECTION_BLOCKS[(int)ecl][ver] & 0xFF);

    private static int GetNumRawDataModules(int ver) {
        if (ver < MIN_VERSION || ver > MAX_VERSION) throw new ArgumentException("Version number out of range");
        int size = ver * 4 + 17;
        int result = size * size;
        result -= 8 * 8 * 3;               // finders with separators
        result -= 15 * 2 + 1;              // format info and dark module
        result -= (size - 16) * 2;         // timing patterns
        if (ver >= 2) {
            int numAlign = ver / 7 + 2;
            result -= (numAlign - 1) * (numAlign - 1) * 25;
            result -= (numAlign - 2) * 2 * 20;
            if (ver >= 7)
                result -= 6 * 3 * 2;      // version info
        }
        return result;
    }

    private int[] GetAlignmentPatternPositions() {
        if (Version == 1) return Array.Empty<int>();
        int numAlign = Version / 7 + 2;
        int step = (Version * 8 + numAlign * 3 + 5) / (numAlign * 4 - 4) * 2;
        int[] result = new int[numAlign];
        result[0] = 6;
        for (int i = result.Length - 1, pos = Size - 7; i >= 1; i--, pos -= step)
            result[i] = pos;
        return result;
    }

    /*---- Utility ----*/
    internal static bool GetBit(int x, int i) => ((x >> i) & 1) != 0;
}

internal static class EccExtensions {
    public static int FormatBits(this QrCode.Ecc e) => e switch {
        QrCode.Ecc.LOW => 1,
        QrCode.Ecc.MEDIUM => 0,
        QrCode.Ecc.QUARTILE => 3,
        QrCode.Ecc.HIGH => 2,
        _ => throw new ArgumentOutOfRangeException(nameof(e))
    };
}
