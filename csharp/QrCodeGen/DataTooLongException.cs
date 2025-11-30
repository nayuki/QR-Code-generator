/*
 * QR Code generator library (C# port)
 *
 * Ported from Java version in this repository.
 * Copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/qr-code-generator-library
 */
using System;

namespace Io.Nayuki.QrCodeGen;

/// <summary>
/// Thrown when the supplied data does not fit any QR Code version.
/// </summary>
public class DataTooLongException : ArgumentException {
    public DataTooLongException() { }
    public DataTooLongException(string? message) : base(message) { }
}
