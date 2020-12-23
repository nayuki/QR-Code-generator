using System;
using System.Collections.Generic;
using System.Text;

namespace QrCodeGenerator
{
	/// <summary>
	/// Describes how a segment's data bits are interpreted.
	/// </summary>
	public class Mode
	{
		/// <summary>
		/// The mode indicator bits, which is a uint4 value (range 0 to 15).
		/// </summary>
		public int ModeBits { get; set; }

		/// <summary>
		/// Number of character count bits for three different version ranges.
		/// </summary>
		public int[] NumBitsCharCount { get; set; }

		public static Mode Numeric = new Mode(0x1, new int[] { 10, 12, 14 });
		public static Mode AlphaNumeric = new Mode(0x2, new int[] { 9, 11, 13 });
		public static Mode Byte = new Mode(0x4, new int[] { 8, 16, 16 });
		public static Mode Kanji = new Mode(0x8, new int[] { 8, 10, 12 });
		public static Mode Eci = new Mode(0x7, new int[] { 0, 0, 0 });

		private Mode(int mode, int[] ccbits)
		{
			ModeBits = mode;
			NumBitsCharCount = ccbits;
		}

		/// <summary>
		/// Returns the bit width of the character count field for a segment in this mode
		/// in a QR Code at the given version number. The result is in the range [0, 16].
		/// </summary>
		/// <param name="ver"></param>
		/// <returns></returns>
		public int NumCharCountBits(int ver)
		{
			return NumBitsCharCount[(int)Math.Floor((decimal)(ver + 7) / 17)];
		}

	}
}
