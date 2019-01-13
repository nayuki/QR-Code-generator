/* 
 * QR Code generator library (.NET)
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

namespace Io.Nayuki.QrCodeGen
{
    /// <summary>
    /// Thrown when the supplied data does not fit any QR Code version.
    /// </summary>
    /// <remarks>
    /// Ways to handle this exception include:
    /// <ul>
    ///   <li>Decrease the error correction level if it was greater than <see cref="QrCode.Ecc.Low"/></li>
    ///   <li><p>If the advanced <see cref=QrCode.EncodeSegments(List{QrSegment}, QrCode.Ecc, int, int, int, bool)"/>
    ///     function or the <see cref="QrSegmentAdvanced.MakeSegmentsOptimally(string, QrCode.Ecc, int, int)"/> function was called,
    ///     then increase the maxVersion argument if it was less than <see cref="QrCode.MaxVersion"/>.
    ///     (This advice does not apply to the other factory functions because they search all versions up to
    ///     <see cref="QrCode.MaxVersion"/></li>
    ///   <li>Split the text data into better or optimal segments in order to reduce the number of bits required.
    ///     (See <see cref="QrSegmentAdvanced.MakeSegmentsOptimally(string, QrCode.Ecc, int, int)"/>.)</li>
    ///   <li>Change the text or binary data to be shorter.</li>
    ///   <li>Change the text to fit the character set of a particular segment mode (e.g. alphanumeric).</li>
    ///   <li>Propagate the error upward to the caller/user.</li>
    /// </ul>
    /// </remarks>
    /// <seealso cref="QrCode.EncodeText(string, QrCode.Ecc)"/>
    /// <seealso cref="QrCode.EncodeBinary(byte[], QrCode.Ecc)"/>
    /// <seealso cref="QrCode.EncodeSegments(List{QrSegment}, QrCode.Ecc)"/>
    /// <seealso cref="QrCode.EncodeSegments(List{QrSegment}, QrCode.Ecc, int, int, int, bool)"/>
    /// <seealso cref="QrSegmentAdvanced.MakeSegmentsOptimally(string, QrCode.Ecc, int, int)"/>
    public class DataTooLongException : ArgumentException
    {
        public DataTooLongException(string message)
            : base(message)
        { }
    }
}
