using System;
using System.Collections.Generic;
using System.Text;

namespace QrCodeGenerator
{
    /// <summary>
    /// Thrown when the supplied data does not fit any QR Code version. Ways to handle this exception include:
    /// <list type="bullet">
    /// <item><description>Decrease the error correction level if it was greater than <c>ErrorCorrection.Low</c>.</description></item>
    /// <item><description>If the advanced <c>EncodeSegments()</c> function with 6 arguments or the <c>MakeSegmentsOptimally()</c>
    /// function was called, then increase the maxVersion argument
    /// if it was less than <see cref="QrCode.MaxVersion"/>. <para>(This advice does not apply to the other
    /// factory functions because they search all versions up to <see cref="QrCode.MaxVersion"/>.)</para></description></item>
    /// <item><description>Change the text or binary data to be shorter.</description></item>
    /// <item><description>Change the text to fit the character set of a particular segment mode (e.g. alphanumeric).</description></item>
    /// <item><description>Propagate the error upward to the caller/user.</description> </item>
    /// </list>
    /// See also:
    /// <list type="bullet">
    /// <item><seealso cref="QrCode.EncodeText(string, ErrorCorrection)"></seealso></item>
    /// <item><seealso cref="QrCode.EncodeBinary(sbyte[], ErrorCorrection)"></seealso></item>
    /// <item><seealso cref="QrCode.EncodeSegments(List{QrSegment}, ErrorCorrection)"></seealso></item>
    /// <item><seealso cref="QrCode.EncodeSegments(List{QrSegment}, ErrorCorrection, int, int, int, bool)"></seealso></item>
    /// </list>
    /// </summary>
    public class DataTooLongException : Exception
    {
        public DataTooLongException()
        {
        }

        public DataTooLongException(string message): base(message)
        {
        }
        
    }
}
