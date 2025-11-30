using System;
using System.Text;
using Io.Nayuki.QrCodeGen;

internal class Program {
    private static void Main() {
        // Simple demo: encode text and print size
        var qr = QrCode.EncodeText("Hello, world!", QrCode.Ecc.MEDIUM);
        Console.WriteLine($"QR size: {qr.Size}x{qr.Size}, mask={qr.Mask}, ecc={qr.ErrorCorrectionLevel}");

        // Render to ASCII
        int border = 2;
        for (int y = -border; y < qr.Size + border; y++) {
            for (int x = -border; x < qr.Size + border; x++) {
                bool m = (x >= 0 && x < qr.Size && y >= 0 && y < qr.Size) && qr.GetModule(x, y);
                Console.Write(m ? "██" : "  ");
            }
            Console.WriteLine();
        }
    }
}
