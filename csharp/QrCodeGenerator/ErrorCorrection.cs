using System;

namespace QrCodeGenerator
{
	/// <summary>
	///  The error correction level in a QR Code symbol.
	/// </summary>
	public class ErrorCorrection
	{
		public enum Level
		{
			Low, //The QR Code can tolerate about 7% erroneous codewords.
			Medium, //The QR Code can tolerate about 15% erroneous codewords.
			Quartile, //The QR Code can tolerate about 25% erroneous codewords.
			High //The QR Code can tolerate about 30% erroneous codewords.
		}

		public int FormatBits; // In the range 0 to 3 (unsigned 2-bit integer).
		public Level LevelCode;

		public ErrorCorrection(Level level)
		{
			FormatBits = GetFormatBits(level);
			LevelCode = level;
		}

		public int GetFormatBits(Level level)
		{
			if ((int)level == 0) return 1;
			else if ((int)level == 1) return 0;
			else if ((int)level == 2) return 3;
			else if ((int)level == 3) return 2;
			else throw new ArgumentException();
		}
	}
}
