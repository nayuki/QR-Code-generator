using System;
using System.Collections.Generic;
using System.Text;
using System.Text.RegularExpressions;

namespace QrCodeGenerator
{
	/// <summary>
	/// A segment of character/binary/control data in a QR Code symbol.
	/// Instances of this class are immutable.
	/// <para>The mid-level way to create a segment is to take the payload data and call a
	/// static factory function such as <see cref="MakeNumeric(string)"/>.</para><para>The low-level
	/// way to create a segment is to custom-make the bit buffer and call the  
	/// <see cref="QrSegment(Mode, int, BitBuffer)"/> with appropriate values.</para>
	/// <para>This segment class imposes no length restrictions, but QR Codes have restrictions.
	/// Even in the most favorable conditions, a QR Code can only hold 7089 characters of data.
	/// Any segment longer than this is meaningless for the purpose of generating QR Codes.</para>
	/// </summary>
	public class QrSegment
    {
        public BitBuffer Data { get; set; }
        public int CharactersCount { get; set; }
		public Mode Mode { get; set; }

		/// <summary>
		/// Describes precisely all strings that are encodable in numeric mode. To test whether a
		/// string <c>s</c> is encodable: <c> bool ok = NumericRegex.Match(s).Success; </c>.
		/// A string is encodable iff each character is in the range 0 to 9.
		/// </summary>
		public static Regex NumericRegex = new Regex("^[0-9]*$");
		/// <summary>
		/// Describes precisely all strings that are encodable in alphanumeric mode. To test whether a
		/// string <c>s</c> is encodable: <c> bool ok = AlphaNumericRegex.Match(s).Success; </c>.
		/// A string is encodable iff each character is in the following set: 0 to 9, A to Z
		/// (uppercase only), space, dollar, percent, asterisk, plus, hyphen, period, slash, colon.
		/// </summary>
		public static Regex AlphaNumericRegex = new Regex("^[A-Z0-9 $%*+./:-]*$");
		/// <summary>
		/// The set of all legal characters in alphanumeric mode, where
		/// each character value maps to the index in the string.
		/// </summary>
		public static string AlphaNumericCharset = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";

		/// <summary>
		/// Constructs a QR Code segment with the specified attributes and data.
		/// The character count (numCh) must agree with the mode and the bit buffer length,
		/// but the constraint isn't checked. The specified bit buffer is cloned and stored.
		/// </summary>
		/// <param name="mode">the mode (not <c>null</c>)</param>
		/// <param name="charactersCount">the data length in characters or bytes, which is non-negative</param>
		/// <param name="data">the data bits (not <c>null</c>)</param>
		/// <exception cref="ArgumentNullException">if the mode or data is <c>null</c></exception>
		/// <exception cref="ArgumentException">if the character count is negative</exception>
		public QrSegment(Mode mode, int charactersCount, BitBuffer data)
		{
			if (mode == null || data == null) throw new ArgumentNullException(""); 
			if (charactersCount < 0) throw new ArgumentException("Invalid value");

			CharactersCount = charactersCount;
			Data = data;
			Mode = mode;
		}

		/// <summary>
		/// Returns a segment representing the specified binary data encoded in byte mode. All input byte arrays are acceptable.
		/// <p>Any text string can be converted to UTF-8 bytes and encoded as a byte mode segment.</p>
		/// </summary>
		/// <param name="data">the binary data (not <c>null</c>)</param>
		/// <returns> a segment (not <c>null</c>) containing the data</returns>
		/// <exception cref="ArgumentNullException">if the array is <c>null</c></exception>
		public static QrSegment MakeBytes(sbyte[] data)
		{
			if (data == null) throw new ArgumentNullException("");

			var bitBuffer = new BitBuffer();
			foreach (var @byte in data)
            {
				bitBuffer.AppendBits(@byte & 0xFF, 8);
            }
			return new QrSegment(Mode.Byte, data.Length, bitBuffer);
		}

		/// <summary>
		/// Returns a segment representing the specified string of decimal digits encoded in numeric mode.
		/// </summary>
		/// <param name="digits">the text (not <c>null</c>), with only digits from 0 to 9 allowed</param>
		/// <returns>a segment (not <c>null</c>) containing the text</returns>
		/// <exception cref="ArgumentNullException">if the string is <c>null</c></exception>
		/// <exception cref="ArgumentException">if the string contains non-digit characters</exception>
		public static QrSegment MakeNumeric(string digits)
		{
			if (digits == null) throw new ArgumentNullException("");
			if (!NumericRegex.Match(digits).Success) throw new ArgumentException("String contains non-numeric characters");

			var bitBuffer = new BitBuffer();
			for (var index = 0; index < digits.Length;)
			{  
				var n = Math.Min(digits.Length - index, 3);
				var digitsSubstring = digits.Substring(index, n);

				bitBuffer.AppendBits(int.Parse(digitsSubstring), n * 3 + 1);
				index += n;
			}
			return new QrSegment(Mode.Numeric, digits.Length, bitBuffer);
		}

		/// <summary>
		/// Returns a segment representing the specified text string encoded in alphanumeric mode.
		/// The characters allowed are: 0 to 9, A to Z (uppercase only), space,
		/// dollar, percent, asterisk, plus, hyphen, period, slash, colon.
		/// </summary> 
		/// <param name="text">the text (not <c>null</c>), with only certain characters allowed</param>
		/// <returns>a segment (not {@code null}) containing the text</returns>
		/// <exception cref="ArgumentNullException">if the string is <c>null</c></exception>
		/// <exception cref="ArgumentException">if the string contains non-encodable characters</exception>
		public static QrSegment MakeAlphanumeric(string text)
		{
			if (text == null) throw new ArgumentNullException("");
			if (!AlphaNumericRegex.Match(text).Success) throw new ArgumentException("String contains unencodable characters in alphanumeric mode");

			var bitBuffer = new BitBuffer();
			int index;
			for (index = 0; index <= text.Length - 2; index += 2)
			{ 
				var temp = AlphaNumericCharset.IndexOf(text[index]) * 45;
				temp += AlphaNumericCharset.IndexOf(text[index + 1]);
				bitBuffer.AppendBits(temp, 11);
			}
			if (index < text.Length)
            {
				bitBuffer.AppendBits(AlphaNumericCharset.IndexOf(text[index]), 6);
            }  
			return new QrSegment(Mode.AlphaNumeric, text.Length, bitBuffer);
		}

		/// <summary>
		/// Returns a list of zero or more segments to represent the specified Unicode text string.
		/// The result may use various segment modes and switch modes to optimize the length of the bit stream.
		/// </summary>
		/// <param name="text">the text to be encoded, which can be any Unicode string</param>
		/// <returns>a new mutable list (not <c>null</c>) of segments (not <c>null</c>) containing the text</returns>
		/// <exception cref="ArgumentNullException">if the text is <c>null</c></exception>
		public static List<QrSegment> MakeSegments(string text)
		{
			if (text == null) throw new ArgumentNullException("");

			var result = new List<QrSegment>();

			if (text.Equals(string.Empty)) return result;
            else if (NumericRegex.IsMatch(text))
            {
                result.Add(MakeNumeric(text));
                return result;
            }
            else if (AlphaNumericRegex.Match(text).Success)
            {
                result.Add(MakeAlphanumeric(text));
                return result;
            }
            else
            {
				var bytes = Encoding.UTF8.GetBytes(text);
				var sbytes = Array.ConvertAll(bytes, b => (sbyte)b);
				result.Add(MakeBytes(sbytes));
				return result;
			}
		}

		/// <summary>
		/// Returns a segment representing an Extended Channel Interpretation
		/// (ECI) designator with the specified assignment value.
		/// </summary>
		/// <param name="assignVal">the ECI assignment number (see the AIM ECI specification)</param>
		/// <returns>a segment (not <c>null</c>) containing the data</returns>
		/// <exception cref="ArgumentException">if the value is outside the range [0, 10<sup>6</sup>)</exception>
		public static QrSegment MakeEci(int assignVal)
		{
			var bitBuffer = new BitBuffer();

			if (assignVal < 0)
            {
				throw new ArgumentException("ECI assignment value out of range");
            }
			else if (assignVal < (1 << 7))
            {
				bitBuffer.AppendBits(assignVal, 8);
				return new QrSegment(Mode.Eci, 0, bitBuffer);
			}
			else if (assignVal < (1 << 14))
			{
				bitBuffer.AppendBits(2, 2);
				bitBuffer.AppendBits(assignVal, 14);
				return new QrSegment(Mode.Eci, 0, bitBuffer);
			}
			else if (assignVal < 1_000_000)
			{
				bitBuffer.AppendBits(6, 3);
				bitBuffer.AppendBits(assignVal, 21);
				return new QrSegment(Mode.Eci, 0, bitBuffer);
			}
            else
            {
				throw new ArgumentException("ECI assignment value out of range");
            }
		}

		/// <summary>
		/// Returns the data bits of this segment.
		/// </summary>
		/// <returns>a new copy of the data bits (not <c>null</c>)</returns>
		public BitBuffer GetData()
		{
			return Data.Clone();  
		}

		/// <summary>
		/// Calculates the number of bits needed to encode the given segments at the given version.
		/// Returns a non-negative number if successful. Otherwise returns -1 if a segment has too
		/// many characters to fit its length field, or the total bits exceeds int.MaxValue.
		/// </summary>
		/// <param name="segs"></param>
		/// <param name="version"></param>
		/// <returns></returns>
		public static int GetTotalBits(List<QrSegment> segs, int version)
		{
			long result = 0;
			foreach (QrSegment seg in segs)
			{
				var bits = seg.Mode.NumCharCountBits(version);
				if (seg.CharactersCount >= (1 << bits))
					return -1;  
				result += 4L + bits + seg.Data.GetBitLength();
				if (result > int.MaxValue)
					return -1;  
			}
			return (int)result;
		}
	}

}
