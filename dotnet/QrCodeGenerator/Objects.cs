﻿/* 
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

namespace IO.Nayuki.QrCodeGen
{
    /// <summary>
    /// Helper functions to check for valid arguments.
    /// </summary>
    internal class Objects
    {
        /// <summary>
        /// Ensures that the specified argument is <i>not null</i>.
        /// <para>
        /// Throws a <see cref="ArgumentNullException"/> exception if the argument is <c>null</c>.
        /// </para>
        /// </summary>
        /// <typeparam name="T">The type of the argument.</typeparam>
        /// <param name="arg">The argument to check.</param>
        /// <returns>Argument passed to function.</returns>
        /// <exception cref="ArgumentNullException">The specified argument is <c>null</c>.</exception>
        internal static T RequireNonNull<T>(T arg)
        {
            if (arg == null)
            {
                throw new ArgumentNullException();
            }
            return arg;
        }
    }
}
