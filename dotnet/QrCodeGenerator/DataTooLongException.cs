/* 
 * QR code generator library (.NET)
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
using System.Collections.Generic;

namespace IO.Nayuki.QrCodeGen
{
    /// <summary>
    /// The exception that is thrown when the supplied data does not fit in the QR code.
    /// </summary>
    /// <remarks>
    /// Ways to handle this exception include:
    /// <ul>
    ///   <li>Decrease the error correction level (if it was greater than <see cref="QrCode.Ecc.Low"/>)</li>
    ///   <li>Increase the <c>maxVersion</c> argument (if it was less than <see cref="QrCode.MaxVersion"/>).
    ///       This advice applies to the advanced factory functions
    ///       <see cref="QrCode.EncodeSegments"/> and
    ///       <see cref="QrSegmentAdvanced.MakeSegmentsOptimally(string, QrCode.Ecc, int, int)"/> only.
    ///       Other factory functions automatically try all versions up to <see cref="QrCode.MaxVersion"/>.</li>
    ///   <li>Split the text into several segments and encode them using different encoding modes
    ///     (see <see cref="QrSegmentAdvanced.MakeSegmentsOptimally(string, QrCode.Ecc, int, int)"/>.)</li>
    ///   <li>Make the text or binary data shorter.</li>
    ///   <li>Change the text to fit the character set of a particular segment mode (e.g. alphanumeric).</li>
    ///   <li>Reject the data and notify the caller/user.</li>
    /// </ul>
    /// </remarks>
    /// <seealso cref="QrCode.EncodeText(string, QrCode.Ecc)"/>
    /// <seealso cref="QrCode.EncodeBinary(byte[], QrCode.Ecc)"/>
    /// <seealso cref="QrCode.EncodeSegments(List{QrSegment}, QrCode.Ecc, int, int, int, bool)"/>
    /// <seealso cref="QrSegmentAdvanced.MakeSegmentsOptimally(string, QrCode.Ecc, int, int)"/>
    public class DataTooLongException : ArgumentException
    {
        /// <summary>
        /// Initializes a new instance of the <see cref="DataTooLongException"/> class.
        /// </summary>
        /// <param name="message">The message that describes the error.</param>
        public DataTooLongException(string message)
            : base(message)
        { }
    }
}
