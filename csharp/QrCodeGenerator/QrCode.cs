using System;
using System.Collections.Generic;
using System.Drawing;
using System.Text;

namespace QrCodeGenerator
{
	public class QrCode
	{
		/// <summary>
		/// The version number of this QR Code, which is between 1 and 40 (inclusive). This determines the size of this barcode.
		/// </summary>
		public int Version { get; set; }

		/// <summary>
		/// The width and height of this QR Code, measured in modules, between  21 and 177 (inclusive). This is equal to version &#xD7; 4 + 17.
		/// </summary> 
		public int Size { get; set; }

		/// <summary>
		/// The error correction level used in this QR Code, which is not <c>null</c>.
		/// </summary>
		public ErrorCorrection ErrorCorrectionLevel { get; set; }

		/// <summary>
		/// The index of the mask pattern used in this QR Code, which is between 0 and 7 (inclusive).
		/// <para>Even if a QR Code is created with automatic masking requested (mask =
		/// &#x2212;1), the resulting object still has a mask value between 0 and 7.</para>
		/// </summary>
		public int Mask { get; set; }

		/// <summary>
		/// The modules of this QR Code (false = white, true = black).
		/// Immutable after constructor finishes. Accessed through GetModule().
		/// </summary>
		public bool[,] Modules { get; set; }

		/// <summary>
		/// Indicates function modules that are not subjected to masking. Discarded when constructor finishes.
		/// </summary>
		public bool[,] IsFunction { get; set; }

		public static int MinVersion = 1; //The minimum version number  (1) supported in the QR Code Model 2 standard.
		public static int MaxVersion = 40; //The maximum version number (40) supported in the QR Code Model 2 standard.

		//For use in GetPenaltyScore(), when evaluating which mask is best.
		private static int PenaltyN1 = 3;
		private static int PenaltyN2 = 3;
		private static int PenaltyN3 = 40;
		private static int PenaltyN4 = 10;

		private static sbyte[][] ErrorCorrectionCodeWordsPerBlock = new sbyte[][] {
			// Version: (note that index 0 is for padding, and is set to an illegal value)
			new sbyte[]{-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},  // Low
			new sbyte[]{-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28},  // Medium
			new sbyte[]{-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},  // Quartile
			new sbyte[]{-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},  // High
		};

        private static sbyte[][] NumErrorCorrectionBlocks = new sbyte[][] {
			// Version: (note that index 0 is for padding, and is set to an illegal value)
			new sbyte[]{-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4,  4,  4,  4,  4,  6,  6,  6,  6,  7,  8,  8,  9,  9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25},  // Low
    		new sbyte[]{-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5,  5,  8,  9,  9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49},  // Medium
    		new sbyte[]{-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8,  8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68},  // Quartile
    		new sbyte[]{-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81},  // High
		};

		/// <summary>
		///	Returns a QR Code representing the specified Unicode text string at the specified error correction level.
		///	As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer
		///	Unicode code points(not UTF-16 code units) if the low error correction level is used.The smallest possible
		///	QR Code version is automatically chosen for the output.The ErrorCorrection level of the result may be higher than the
		///	ecl argument if it can be done without increasing the version.
		/// </summary>
		/// <param name="version">The version number to use, which must be in the range 1 to 40 (inclusive)</param>
		/// <param name="errorCorrectionLevel">The error correction level to use</param>
		/// <param name="dataCodeWords">The bytes representing segments to encode (without ErrorCorrection)</param>
		/// <param name="mask">The mask pattern to use, which is either &#x2212;1 for automatic choice or from 0 to 7 for fixed choice</param>
		/// <exception cref="ArgumentNullException"> If the byte array is <c>null</c></exception>
		/// <exception cref="ArgumentException"> If the version or mask value is out of range, or if the data is the wrong length for the specified version and error correction level</exception>
		public QrCode(int version, ErrorCorrection.Level errorCorrectionLevel, sbyte[] dataCodeWords, int mask)
		{
			if (version < MinVersion || version > MaxVersion)
				throw new ArgumentException("Version value out of range");
			if (mask < -1 || mask > 7)
				throw new ArgumentException("Mask value out of range");
			if (dataCodeWords == null)
				throw new ArgumentNullException("Requiered non null dataCodeWords array");

                Version = version;
                Size = version * 4 + 17;

                ErrorCorrectionLevel = new ErrorCorrection(errorCorrectionLevel);
                Modules = new bool[Size, Size];
                IsFunction = new bool[Size, Size];

                DrawFunctionPatterns();

                var allCodewords = AddErrorCorrectionAndInterleave(dataCodeWords);
                DrawCodewords(allCodewords);
                Mask = HandleConstructorMasking(mask);
                IsFunction = null;
		}

		/// <summary>
		/// Returns a QR Code representing the specified Unicode text string at the specified error correction level.
		/// As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer
		/// Unicode code points (not UTF-16 code units) if the low error correction level is used. The smallest possible
		/// QR Code version is automatically chosen for the output. The ErrorCorrection level of the result may be higher than the
		/// ecl argument if it can be done without increasing the version.
		/// </summary>
		/// <param name="text">The text to be encoded (not <c>null</c>), which can be any Unicode string</param>
		/// <param name="errorCorrectionLevel"></param>
		/// <returns>A QR Code (not <c>null</c>) representing the text</returns>
		/// <exception cref="ArgumentNullException">If the text is <c>null</c></exception>
		/// <exception cref="DataTooLongException">If the text fails to fit in the largest version QR Code at the ECL, which means it is too long</exception>
		public static QrCode EncodeText(string text, ErrorCorrection errorCorrectionLevel)
		{
			if (text == null) 
				throw new ArgumentNullException("Text is null!"); 

            var segments = QrSegment.MakeSegments(text);
            return EncodeSegments(segments, errorCorrectionLevel);
        }

		/// <summary>
		/// Returns a QR Code representing the specified binary data at the specified error correction level.
		/// This function always encodes using the binary segment mode, not any text mode. The maximum number of
		/// bytes allowed is 2953. The smallest possible QR Code version is automatically chosen for the output.
		/// The ErrorCorrection level of the result may be higher than the ecl argument if it can be done without increasing the version.
		/// </summary>
		/// <param name="data">The binary data to encode (not <c>null</c>)</param>
		/// <param name="errorCorrectionLevel">The error correction level to use (not <c>null</c>) (boostable)</param>
		/// <returns> A QR Code (not <c>null</c>) representing the data</returns>
		/// <exception cref="ArgumentNullException">If the data is <c>null</c></exception>
		/// <exception cref="DataTooLongException">If the data fails to fit in the largest version QR Code at the ECL, which means it is too long</exception>
		public static QrCode EncodeBinary(sbyte[] data, ErrorCorrection errorCorrectionLevel)
		{
			if (data == null)
				throw new ArgumentNullException("Requiered non null data array");

			return EncodeSegments(new List<QrSegment> { QrSegment.MakeBytes(data) }, errorCorrectionLevel);
		}

		/// <summary>
		/// Returns a QR Code representing the specified segments at the specified error correction
		/// level. The smallest possible QR Code version is automatically chosen for the output. The ErrorCorrection level
		/// of the result may be higher than the ecl argument if it can be done without increasing the version.
		/// <para>This function allows the user to create a custom sequence of segments that switches
		/// between modes (such as alphanumeric and byte) to encode text in less space.
		/// This is a mid-level API; the high-level API is <see cref="EncodeText(String,ErrorCorrection)"/>
		/// and <see cref="EncodeBinary(sbyte[],ErrorCorrection)"/>.</para>
		/// </summary>
		/// <param name="segments">The segments to encode</param>
		/// <param name="errorCorrectionLevel">The error correction level to use (not <c>null</c>) (boostable)</param>
		/// <returns>A QR Code (not <c>null</c>) representing the segments</returns>
		/// <exception cref="ArgumentNullException">If the list of segments or any segment is <c>null</c></exception>
		/// <exception cref="DataTooLongException">If the segments fails to fit in the largest version QR Code at the ECL, which means it is too long</exception>
		public static QrCode EncodeSegments(List<QrSegment> segments, ErrorCorrection errorCorrectionLevel)
		{
			if (segments == null)
				throw new ArgumentNullException("Requiered non null list");

			return EncodeSegments(segments, errorCorrectionLevel, MinVersion, MaxVersion, -1, true);
		}

		/// <summary>
		/// Returns a QR Code representing the specified segments with the specified encoding parameters.
		/// The smallest possible QR Code version within the specified range is automatically
		/// chosen for the output.Iff boostEcl is <c>true</c>, then the ErrorCorrection level of the
		/// result may be higher than the ecl argument if it can be done without increasing
		/// the version.The mask number is either between 0 to 7 (inclusive) to force that
		/// mask, or &#x2212;1 to automatically choose an appropriate mask (which may be slow).
		/// <para>This function allows the user to create a custom sequence of segments that switches
		/// between modes (such as alphanumeric and byte) to encode text in less space.
		/// This is a mid-level API; the high-level API is <see cref="EncodeText(string,ErrorCorrection)"/>
		/// and  <see cref="EncodeBinary(sbyte[],ErrorCorrection)"/>.</para>
		/// </summary>
		/// <param name="segments">The segments to encode</param>
		/// <param name="errorCorrectionLevel">The error correction level to use (not <c>null</c>) (boostable)</param>
		/// <param name="minVersion">The minimum allowed version of the QR Code (at least 1)</param>
		/// <param name="maxVersion">The maximum allowed version of the QR Code (at most 40)</param>
		/// <param name="mask">The mask number to use (between 0 and 7 (inclusive)), or &#x2212;1 for automatic mask</param>
		/// <param name="boostErrorCorrectionLevel">Increases the ErrorCorrection level as long as it doesn't increase the version number</param>
		/// <returns>A QR Code (not <c>null</c>) representing the segments</returns>
		/// <exception cref="ArgumentNullException">If the list of segments, any segment, or error correction level is <c>null</c></exception>
		/// <exception cref="ArgumentException">If 1 &#x2264; minVersion &#x2264; maxVersion &#x2264; 40 or &#x2212;1 &#x2264; mask &#x2264; 7 is violated</exception>
		/// <exception cref="DataTooLongException">If the segments fails to fit in the largest version QR Code at the ECL, which means it is too long</exception>
		public static QrCode EncodeSegments(List<QrSegment> segments, ErrorCorrection errorCorrectionLevel, int minVersion, int maxVersion, int mask, bool boostErrorCorrectionLevel)
		{
			if (segments == null)
				throw new ArgumentNullException("Requiered non null list");
			if (!(MinVersion <= minVersion && minVersion <= maxVersion && maxVersion <= MaxVersion) || mask < -1 || mask > 7)
				throw new ArgumentException("Invalid value");

			int version, dataUsedBits, dataCapacityBits;
			for (version = minVersion; ; version++)
			{
				dataCapacityBits = GetNumDataCodewords(version, errorCorrectionLevel.LevelCode) * 8;  
				dataUsedBits = QrSegment.GetTotalBits(segments, version);
				
				if (dataUsedBits != -1 && dataUsedBits <= dataCapacityBits) break; 

				if (version >= maxVersion)
				{ 
					var message = "Segment too long";
					if (dataUsedBits != -1)
						message = string.Format("Data length = %d bits, Max capacity = %d bits", dataUsedBits, dataCapacityBits);
					throw new DataTooLongException(message);
				}
			}

			foreach (var newEcl in (ErrorCorrection.Level[])Enum.GetValues(typeof(ErrorCorrection.Level)))
			{  
				if (boostErrorCorrectionLevel && dataUsedBits <= GetNumDataCodewords(version, newEcl) * 8)
					errorCorrectionLevel.LevelCode = newEcl;
			}

			var bitBuffer = new BitBuffer();
			foreach (var segment in segments)
			{
				bitBuffer.AppendBits(segment.Mode.ModeBits, 4);
				bitBuffer.AppendBits(segment.CharactersCount, segment.Mode.NumCharCountBits(version));
				bitBuffer.AppendData(segment.Data);
			}

			dataCapacityBits = GetNumDataCodewords(version, errorCorrectionLevel.LevelCode) * 8;

			bitBuffer.AppendBits(0, Math.Min(4, dataCapacityBits - bitBuffer.GetBitLength()));
			bitBuffer.AppendBits(0, (8 - bitBuffer.GetBitLength() % 8) % 8);

			for (int padByte = 0xEC; bitBuffer.GetBitLength() < dataCapacityBits; padByte ^= 0xEC ^ 0x11)
				bitBuffer.AppendBits(padByte, 8);

			var dataCodewords = new sbyte[bitBuffer.GetBitLength() / 8];
			for (int i = 0; i < bitBuffer.GetBitLength(); i++)
				dataCodewords[i >> 3] |= (sbyte)(bitBuffer.GetBit(i) << (7 - (i & 7)));

			return new QrCode(version, errorCorrectionLevel.LevelCode, dataCodewords, mask);
		}

		/// <summary>
		/// Returns the color of the module (pixel) at the specified coordinates, which is <c>false</c>
		/// for white or <c>true</c> for black. The top left corner has the coordinates (x=0, y=0).
		/// <para>If the specified coordinates are out of bounds, then <c>false</c> (white) is returned.</para>
		/// </summary>
		/// <param name="x">The x coordinate, where 0 is the left edge and size&#x2212;1 is the right edge</param>
		/// <param name="y">The y coordinate, where 0 is the top edge and size&#x2212;1 is the bottom edge</param>
		/// <returns><c>True</c> if the coordinates are in bounds and the module at that location is black, or <c>False</c> (white) otherwise</returns>
		public bool GetModule(int x, int y)
		{
			return 0 <= x && x < Size && 0 <= y && y < Size && Modules[y, x];
		}

		/// <summary>
		/// Returns a raster image depicting this QR Code, with the specified module scale and border modules.
		/// <para>For example,</para> ToImage(scale=10, border=4) means to pad the QR Code with 4 white
		/// border modules on all four sides, and use 10&#xD7;10 pixels to represent each module.
		/// The resulting image only contains the hex colors 000000 and FFFFFF.
		/// </summary>
		/// <param name="scale">The side length (measured in pixels, must be positive) of each module</param>
		/// <param name="border">The number of border modules to add, which must be non-negative</param>
		/// <returns>A new image representing this QR Code, with padding and scaling</returns>
		/// <exception cref="ArgumentException">If the scale or border is out of range, or if {scale, border, size} cause the image dimensions to exceed Integer.MAX_VALUE</exception>
		public Bitmap ToImage(int scale, int border)
		{
			if (scale <= 0 || border < 0)
				throw new ArgumentException("Value out of range");
			if (border > int.MaxValue / 2 || Size + border * 2L > int.MaxValue / scale)
				throw new ArgumentException("Scale or border too large");

			var result = new Bitmap((Size + border * 2) * scale, (Size + border * 2) * scale, System.Drawing.Imaging.PixelFormat.Format32bppArgb);
			for (int y = 0; y < result.Height; y++)
			{
				for (int x = 0; x < result.Width; x++)
				{
					var color = GetModule(x / scale - border, y / scale - border);
					result.SetPixel(x, y, color ? Color.Black : Color.White);
				}
			}
			return result;
		}

		/// <summary>
		/// Returns a string of SVG code for an image depicting this QR Code, with the specified number
		/// of border modules. The string always uses Unix newlines (\n), regardless of the platform.
		/// </summary>
		/// <param name="border">The number of border modules to add, which must be non-negative</param>
		/// <returns>A string representing this QR Code as an SVG XML document</returns>
		/// <exception cref="ArgumentException">If the border is negative</exception>
		public string ToSvgString(int border)
		{
			if (border < 0) 
				throw new ArgumentException("Border must be non-negative");

			var stringBuilder = new StringBuilder()
				.Append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.Append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n")
				.Append(string.Format("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 %1$d %1$d\" stroke=\"none\">\n", Size + border * 2))
				.Append("\t<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n")
				.Append("\t<path d=\"");

			for (int y = 0; y < Size; y++)
			{
				for (int x = 0; x < Size; x++)
				{
					if (GetModule(x, y))
					{
						if (x != 0 || y != 0) stringBuilder.Append(" ");
						stringBuilder.Append(string.Format("M%d,%dh1v1h-1z", x + border, y + border));
					}
				}
			}
			return stringBuilder.Append("\" fill=\"#000000\"/>\n").Append("</svg>\n").ToString();
		}

		#region Private helper methods for constructor - Drawing function modules

		// Reads this object's version field, and draws and marks all function modules.
		private void DrawFunctionPatterns()
		{
			for (int i = 0; i < Size; i++)
			{
				SetFunctionModule(6, i, i % 2 == 0);
				SetFunctionModule(i, 6, i % 2 == 0);
			}

			DrawFinderPattern(3, 3);
			DrawFinderPattern(Size - 4, 3);
			DrawFinderPattern(3, Size - 4);

			var alignPatPos = GetAlignmentPatternPositions();
			var numAlign = alignPatPos.Length;
			for (int i = 0; i < numAlign; i++)
			{
				for (int j = 0; j < numAlign; j++)
				{
					if (!(i == 0 && j == 0 || i == 0 && j == numAlign - 1 || i == numAlign - 1 && j == 0))
						DrawAlignmentPattern(alignPatPos[i], alignPatPos[j]);
				}
			}

			DrawFormatBits(0);  
			DrawVersion();
		}

		// Draws two copies of the format bits (with its own error correction code)
		// based on the given mask and this object's error correction level field.
		private void DrawFormatBits(int msk)
		{
			var data = ErrorCorrectionLevel.FormatBits << 3 | msk; 
			var rem = data;

			for (int i = 0; i < 10; i++)
				rem = (rem << 1) ^ ((rem >> 9) * 0x537);

			var bits = (data << 10 | rem) ^ 0x5412;  

			for (int i = 0; i <= 5; i++)
				SetFunctionModule(8, i, GetBit(bits, i));

			SetFunctionModule(8, 7, GetBit(bits, 6));
			SetFunctionModule(8, 8, GetBit(bits, 7));
			SetFunctionModule(7, 8, GetBit(bits, 8));
			
			for (int i = 9; i < 15; i++)
				SetFunctionModule(14 - i, 8, GetBit(bits, i));

			for (int i = 0; i < 8; i++)
				SetFunctionModule(Size - 1 - i, 8, GetBit(bits, i));

			for (int i = 8; i < 15; i++)
				SetFunctionModule(8, Size - 15 + i, GetBit(bits, i));

			SetFunctionModule(8, Size - 8, true);  
		}

		// Draws two copies of the version bits (with its own error correction code),
		// based on this object's version field, iff 7 <= version <= 40.
		private void DrawVersion()
		{
			if (Version < 7)
				return;

			var rem = Version;  
			for (int i = 0; i < 12; i++)
				rem = (rem << 1) ^ ((rem >> 11) * 0x1F25);

			var bits = Version << 12 | rem;  
			for (int i = 0; i < 18; i++)
			{
				var bit = GetBit(bits, i);
				var a = Size - 11 + i % 3;
				var b = i / 3;
				SetFunctionModule(a, b, bit);
				SetFunctionModule(b, a, bit);
			}
		}

		// Draws a 9*9 finder pattern including the border separator,
		// with the center module at (x, y). Modules can be out of bounds.
		private void DrawFinderPattern(int x, int y)
		{
			for (int dy = -4; dy <= 4; dy++)
			{
				for (int dx = -4; dx <= 4; dx++)
				{
					int dist = Math.Max(Math.Abs(dx), Math.Abs(dy));  
					int xx = x + dx, yy = y + dy;
					if (0 <= xx && xx < Size && 0 <= yy && yy < Size)
						SetFunctionModule(xx, yy, dist != 2 && dist != 4);
				}
			}
		}

		// Draws a 5*5 alignment pattern, with the center module
		// at (x, y). All modules must be in bounds.
		private void DrawAlignmentPattern(int x, int y)
		{
			for (int dy = -2; dy <= 2; dy++)
			{
				for (int dx = -2; dx <= 2; dx++)
					SetFunctionModule(x + dx, y + dy, Math.Max(Math.Abs(dx), Math.Abs(dy)) != 1);
			}
		}

		// Sets the color of a module and marks it as a function module.
		// Only used by the constructor. Coordinates must be in bounds.
		private void SetFunctionModule(int x, int y, bool isBlack)
		{
			Modules[y, x] = isBlack;
			IsFunction[y, x] = true;
		}

		#endregion

		#region Private helper methods for constructor - Codewords and masking 

		// Returns a new byte string representing the given data with the appropriate error correction
		// codewords appended to it, based on this object's version and error correction level.
		private sbyte[] AddErrorCorrectionAndInterleave(sbyte[] data)
		{
			if (data.Length != GetNumDataCodewords(Version, ErrorCorrectionLevel.LevelCode))
				throw new ArgumentException();

			var numBlocks = NumErrorCorrectionBlocks[(int)ErrorCorrectionLevel.LevelCode][Version];
			var blockErrorCorrectionLen = ErrorCorrectionCodeWordsPerBlock[(int)ErrorCorrectionLevel.LevelCode][Version];
			var rawCodewords = GetNumRawDataModules(Version) / 8;
			var numShortBlocks = numBlocks - rawCodewords % numBlocks;
			var shortBlockLen = rawCodewords / numBlocks;

			var blocks = new sbyte[numBlocks][];
			var rsDiv = ReedSolomonComputeDivisor(blockErrorCorrectionLen);
			for (int i = 0, k = 0; i < numBlocks; i++)
			{
				var dat = ArrayCopyOfRange(data, k, k + shortBlockLen - blockErrorCorrectionLen + (i < numShortBlocks ? 0 : 1));
				k += dat.Length;

				var block = new sbyte[shortBlockLen + 1];
				Array.Copy(dat, block, dat.Length) ;
				
				var ErrorCorrection = ReedSolomonComputeRemainder(dat, rsDiv);
				Array.Copy(ErrorCorrection, 0, block, block.Length - blockErrorCorrectionLen, ErrorCorrection.Length);
				blocks[i] = block;
			}

			var result = new sbyte[rawCodewords];
			for (int i = 0, k = 0; i < blocks[0].Length; i++)
			{
				for (int j = 0; j < blocks.Length; j++)
				{
					if (i != shortBlockLen - blockErrorCorrectionLen || j >= numShortBlocks)
					{
						result[k] = blocks[j][i];
						k++;
					}
				}
			}
			return result;
		}

		sbyte[] ArrayCopyOfRange(sbyte[] src, int start, int end)
		{
			var len = end - start;
			var dest = new sbyte[len];

			for (int i = 0; i < len; i++)
			{
				dest[i] = src[start + i]; 
			}
			return dest;
		}

		// Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
		// data area of this QR Code. Function modules need to be marked off before this is called.
		private void DrawCodewords(sbyte[] data)
		{
			if (data.Length != GetNumRawDataModules(Version) / 8)
				throw new ArgumentException();

			var i = 0;  
						
			for (int right = Size - 1; right >= 1; right -= 2)
			{ 
				if (right == 6)
					right = 5;
				for (int vert = 0; vert < Size; vert++)
				{  
					for (int j = 0; j < 2; j++)
					{
						var x = right - j; 
					    var upward = ((right + 1) & 2) == 0;
						var y = upward ? Size - 1 - vert : vert;  
						if (!IsFunction[y, x] && i < data.Length * 8)
						{
							Modules[y, x] = GetBit(data[i >> 3], 7 - (i & 7));
							i++;
						}
					}
				}
			}
		}

		// XORs the codeword modules in this QR Code with the given mask pattern.
		// The function modules must be marked and the codeword bits must be drawn
		// before masking. Due to the arithmetic of XOR, calling applyMask() with
		// the same mask value a second time will undo the mask. A final well-formed
		// QR Code needs exactly one (not zero, two, etc.) mask applied.
		private void ApplyMask(int msk)
		{
			if (msk < 0 || msk > 7)
				throw new ArgumentException("Mask value out of range");
			for (int y = 0; y < Size; y++)
			{
				for (int x = 0; x < Size; x++)
				{
					bool invert;
					switch (msk)
					{
						case 0: invert = (x + y) % 2 == 0; break;
						case 1: invert = y % 2 == 0; break;
						case 2: invert = x % 3 == 0; break;
						case 3: invert = (x + y) % 3 == 0; break;
						case 4: invert = (x / 3 + y / 2) % 2 == 0; break;
						case 5: invert = x * y % 2 + x * y % 3 == 0; break;
						case 6: invert = (x * y % 2 + x * y % 3) % 2 == 0; break;
						case 7: invert = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
						default: throw new InvalidOperationException();
					}
					Modules[y, x] ^= invert & !IsFunction[y,x];
				}
			}
		}

		// A messy helper function for the constructor. This QR Code must be in an unmasked state when this
		// method is called. The given argument is the requested mask, which is -1 for auto or 0 to 7 for fixed.
		// This method applies and returns the actual mask chosen, from 0 to 7.
		private int HandleConstructorMasking(int msk)
		{
			if (msk == -1)
			{  
				var minPenalty = int.MaxValue;
				for (int i = 0; i < 8; i++)
				{
					ApplyMask(i);
					DrawFormatBits(i);
					var penalty = GetPenaltyScore();
					if (penalty < minPenalty)
					{
						msk = i;
						minPenalty = penalty;
					}
					ApplyMask(i);  
				}
			}

			ApplyMask(msk);  
			DrawFormatBits(msk);
			return msk;  
		}

		// Calculates and returns the penalty score based on state of this QR Code's current modules.
		// This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
		private int GetPenaltyScore()
		{
			var result = 0;

			for (int y = 0; y < Size; y++)
			{
				var runColor = false;
				var runX = 0;
				var runHistory = new int[7];
				for (int x = 0; x < Size; x++)
				{
					if (Modules[y, x] == runColor)
					{
						runX++;
						if (runX == 5)
							result += PenaltyN1;
						else if (runX > 5)
							result++;
					}
					else
					{
						FinderPenaltyAddHistory(runX, runHistory);
						if (!runColor)
							result += FinderPenaltyCountPatterns(runHistory) * PenaltyN3;
						runColor = Modules[y, x];
						runX = 1;
					}
				}
				result += FinderPenaltyTerminateAndCount(runColor, runX, runHistory) * PenaltyN3;
			}

			for (int x = 0; x < Size; x++)
			{
				var runColor = false;
				var runY = 0;
				var runHistory = new int[7];
				for (int y = 0; y < Size; y++)
				{
					if (Modules[y, x] == runColor)
					{
						runY++;
						if (runY == 5)
							result += PenaltyN1;
						else if (runY > 5)
							result++;
					}
					else
					{
						FinderPenaltyAddHistory(runY, runHistory);
						if (!runColor)
							result += FinderPenaltyCountPatterns(runHistory) * PenaltyN3;
						runColor = Modules[y, x];
						runY = 1;
					}
				}
				result += FinderPenaltyTerminateAndCount(runColor, runY, runHistory) * PenaltyN3;
			}

			for (int y = 0; y < Size - 1; y++)
			{
				for (int x = 0; x < Size - 1; x++)
				{
					var color = Modules[y, x];
					if (color == Modules[y, x + 1] &&
						  color == Modules[y + 1, x] &&
						  color == Modules[y + 1, x + 1])
						result += PenaltyN2;
				}
			}

			var black = 0;
			foreach (var color in Modules)
			{
				if (color)
					black++;
			}
			var total = Size * Size;  
									 
			var k = (Math.Abs(black * 20 - total * 10) + total - 1) / total - 1;
			result += k * PenaltyN4;
			return result;
		}

		#endregion

		#region Private helper functions

		// Returns an ascending list of positions of alignment patterns for this version number.
		// Each position is in the range [0,177), and are used on both the x and y axes.
		// This could be implemented as lookup table of 40 variable-length lists of unsigned bytes.
		private int[] GetAlignmentPatternPositions()
		{
			if (Version == 1)
				return new int[] { };
			else
			{
				var numAlign = Version / 7 + 2;
				int step;
				if (Version == 32)  
					step = 26;
				else  
					step = (Version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2;
				var result = new int[numAlign];
				result[0] = 6;
				for (int i = result.Length - 1, pos = Size - 7; i >= 1; i--, pos -= step)
					result[i] = pos;
				return result;
			}
		}

		// Returns the number of data bits that can be stored in a QR Code of the given version number, after
		// all function modules are excluded. This includes remainder bits, so it might not be a multiple of 8.
		// The result is in the range [208, 29648]. This could be implemented as a 40-entry lookup table.
		private static int GetNumRawDataModules(int ver)
		{
			if (ver < MinVersion || ver > MaxVersion)
				throw new ArgumentException("Version number out of range");

			var size = ver * 4 + 17;
			var result = size * size;   
			result -= 8 * 8 * 3;        
			result -= 15 * 2 + 1;      
			result -= (size - 16) * 2;  
										
			if (ver >= 2)
			{
				var numAlign = ver / 7 + 2;
				result -= (numAlign - 1) * (numAlign - 1) * 25;  
				result -= (numAlign - 2) * 2 * 20;  
													
				if (ver >= 7)
					result -= 6 * 3 * 2; 
			}
		
			return result;
		}

		// Returns a Reed-Solomon ErrorCorrection generator polynomial for the given degree. This could be
		// implemented as a lookup table over all possible parameter values, instead of as an algorithm.
		private static sbyte[] ReedSolomonComputeDivisor(int degree)
		{
			if (degree < 1 || degree > 255)
				throw new ArgumentException("Degree out of range");
			var result = new sbyte[degree];
			result[degree - 1] = 1;  

			var root = 1;
			for (int i = 0; i < degree; i++)
			{
				for (int j = 0; j < result.Length; j++)
				{
					result[j] = (sbyte)ReedSolomonMultiply(result[j] & 0xFF, root);
					if (j + 1 < result.Length)
						result[j] ^= result[j + 1];
				}
				root = ReedSolomonMultiply(root, 0x02);
			}
			return result;
		}

		// Returns the Reed-Solomon error correction codeword for the given data and divisor polynomials.
		private static sbyte[] ReedSolomonComputeRemainder(sbyte[] data, sbyte[] divisor)
		{
			var result = new sbyte[divisor.Length];
			foreach (sbyte b in data)
			{ 
				var factor = (b ^ result[0]) & 0xFF;
				Array.Copy(result, 1, result, 0, result.Length - 1);
				result[result.Length - 1] = 0;
				for (int i = 0; i < result.Length; i++)
					result[i] ^= (sbyte)ReedSolomonMultiply(divisor[i] & 0xFF, factor);
			}
			return result;
		}

		// Returns the product of the two given field elements modulo GF(2^8/0x11D). The arguments and result
		// are unsigned 8-bit integers. This could be implemented as a lookup table of 256*256 entries of uint8.
		private static int ReedSolomonMultiply(int x, int y)
		{
			var z = 0;
			for (int i = 7; i >= 0; i--)
			{
				z = (z << 1) ^ ((z >> 7) * 0x11D);
				z ^= ((y >> i) & 1) * x;
			}
			return z;
		}

		// Returns the number of 8-bit data (i.e. not error correction) codewords contained in any
		// QR Code of the given version number and error correction level, with remainder bits discarded.
		// This stateless pure function could be implemented as a (40*4)-cell lookup table.
		static int GetNumDataCodewords(int ver, ErrorCorrection.Level ecl)
		{
			return GetNumRawDataModules(ver) / 8
				- ErrorCorrectionCodeWordsPerBlock[(int)ecl][ver]
				* NumErrorCorrectionBlocks[(int)ecl][ver];
		}

		// Can only be called immediately after a white run is added, and
		// returns either 0, 1, or 2. A helper function for getPenaltyScore().
		private int FinderPenaltyCountPatterns(int[] runHistory)
		{
			var n = runHistory[1];
			var core = n > 0 && runHistory[2] == n && runHistory[3] == n * 3 && runHistory[4] == n && runHistory[5] == n;
			return (core && runHistory[0] >= n * 4 && runHistory[6] >= n ? 1 : 0)
				 + (core && runHistory[6] >= n * 4 && runHistory[0] >= n ? 1 : 0);
		}

		// Must be called at the end of a line (row or column) of modules. A helper function for getPenaltyScore().
		private int FinderPenaltyTerminateAndCount(bool currentRunColor, int currentRunLength, int[] runHistory)
		{
			if (currentRunColor)
			{ 
				FinderPenaltyAddHistory(currentRunLength, runHistory);
				currentRunLength = 0;
			}
			currentRunLength += Size; 
			FinderPenaltyAddHistory(currentRunLength, runHistory);
			return FinderPenaltyCountPatterns(runHistory);
		}

		// Pushes the given value to the front and drops the last value. A helper function for getPenaltyScore().
		private void FinderPenaltyAddHistory(int currentRunLength, int[] runHistory)
		{
			if (runHistory[0] == 0)
				currentRunLength += Size;  
			Array.Copy(runHistory, 0, runHistory, 1, runHistory.Length - 1);
			runHistory[0] = currentRunLength;
		}

		// Returns true iff the i'th bit of x is set to 1.
		public static bool GetBit(int x, int i)
		{
			return ((x >> i) & 1) != 0;
		}

		#endregion
    }
}
