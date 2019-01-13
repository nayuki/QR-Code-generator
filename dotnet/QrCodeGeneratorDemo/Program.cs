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

using System.Collections.Generic;
using System.IO;
using System.Drawing.Imaging;
using System.Text;

namespace Io.Nayuki.QrCodeGen.Demo
{
    internal class Program
    {
        // The main application program.
        internal static void Main(string[] args)
        {
            DoBasicDemo();
            DoVarietyDemo();
            DoSegmentDemo();
            DoMaskDemo();
        }


        #region Demo suite
	    
	    // Creates a single QR Code, then writes it to a PNG file and an SVG file.
        private static void DoBasicDemo()
        {
            const string text = "Hello, world!"; // User-supplied Unicode text
            var errCorLvl = QrCode.Ecc.Low; // Error correction level

            var qr = QrCode.EncodeText(text, errCorLvl); // Make the QR Code symbol

            using (var img = qr.ToBitmap(10, 4)) // Convert to bitmap image
            {
                img.Save("hello-world-QR.png", ImageFormat.Png); // Write image to file
            }

            string svg = qr.ToSvgString(4); // Convert to SVG XML code
            File.WriteAllText("hello-world-QR.svg", svg, Encoding.UTF8); // Write image to file
        }


        // Creates a variety of QR Codes that exercise different features of the library, and writes each one to file.
	    private static void DoVarietyDemo() {
            // Numeric mode encoding (3.33 bits per digit)
		    var qr = QrCode.EncodeText("314159265358979323846264338327950288419716939937510", QrCode.Ecc.Medium);
		    SaveAsPng(qr, "pi-digits-QR.png", 13, 1);
		    
		    // Alphanumeric mode encoding (5.5 bits per character)
		    qr = QrCode.EncodeText("DOLLAR-AMOUNT:$39.87 PERCENTAGE:100.00% OPERATIONS:+-*/", QrCode.Ecc.High);
		    SaveAsPng(qr, "alphanumeric-QR.png", 10, 2);
		    
		    // Unicode text as UTF-8
		    qr = QrCode.EncodeText("こんにちwa、世界！ αβγδ", QrCode.Ecc.Quartile);
		    SaveAsPng(qr, "unicode-QR.png", 10, 3);
		    
		    // Moderately large QR Code using longer text (from Lewis Carroll's Alice in Wonderland)
		    qr = QrCode.EncodeText(
			    "Alice was beginning to get very tired of sitting by her sister on the bank, "
			    + "and of having nothing to do: once or twice she had peeped into the book her sister was reading, "
			    + "but it had no pictures or conversations in it, 'and what is the use of a book,' thought Alice "
			    + "'without pictures or conversations?' So she was considering in her own mind (as well as she could, "
			    + "for the hot day made her feel very sleepy and stupid), whether the pleasure of making a "
			    + "daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly "
			    + "a White Rabbit with pink eyes ran close by her.", QrCode.Ecc.High);
		    SaveAsPng(qr, "alice-wonderland-QR.png", 6, 10);
	    }
	    
	    
	    // Creates QR Codes with manually specified segments for better compactness.
        private static void DoSegmentDemo()
        {
            // Illustration "silver"
            const string silver0 = "THE SQUARE ROOT OF 2 IS 1.";
            const string silver1 = "41421356237309504880168872420969807856967187537694807317667973799";
            var qr = QrCode.EncodeText(silver0 + silver1, QrCode.Ecc.Low);
            SaveAsPng(qr, "sqrt2-monolithic-QR.png", 10, 3);

            var segs = new List<QrSegment>
            {
                QrSegment.MakeAlphanumeric(silver0),
                QrSegment.MakeNumeric(silver1)
            };
            qr = QrCode.EncodeSegments(segs, QrCode.Ecc.Low);
            SaveAsPng(qr, "sqrt2-segmented-QR.png", 10, 3);

            // Illustration "golden"
            const string golden0 = "Golden ratio φ = 1.";
            const string golden1 =
                "6180339887498948482045868343656381177203091798057628621354486227052604628189024497072072041893911374";
            const string golden2 = "......";
            qr = QrCode.EncodeText(golden0 + golden1 + golden2, QrCode.Ecc.Low);
            SaveAsPng(qr, "phi-monolithic-QR.png", 8, 5);

            segs = new List<QrSegment>
            {
                QrSegment.MakeBytes(Encoding.UTF8.GetBytes(golden0)),
                QrSegment.MakeNumeric(golden1),
                QrSegment.MakeAlphanumeric(golden2)
            };

            qr = QrCode.EncodeSegments(segs, QrCode.Ecc.Low);
		    SaveAsPng(qr, "phi-segmented-QR.png", 8, 5);
		    
		    // Illustration "Madoka": kanji, kana, Cyrillic, full-width Latin, Greek characters
		    const string madoka = "「魔法少女まどか☆マギカ」って、　ИАИ　ｄｅｓｕ　κα？";
		    qr = QrCode.EncodeText(madoka, QrCode.Ecc.Low);
		    SaveAsPng(qr, "madoka-utf8-QR.png", 9, 4);

            segs = new List<QrSegment>  { QrSegmentAdvanced.MakeKanji(madoka) };
		    qr = QrCode.EncodeSegments(segs, QrCode.Ecc.Low);
		    SaveAsPng(qr, "madoka-kanji-QR.png", 9, 4);
	    }
	    
	    
	    // Creates QR Codes with the same size and contents but different mask patterns.
	    private static void DoMaskDemo() {
            // Project Nayuki URL
		    var segs = QrSegment.MakeSegments("https://www.nayuki.io/");
		    var qr = QrCode.EncodeSegments(segs, QrCode.Ecc.High, QrCode.MinVersion, QrCode.MaxVersion, -1, true);
		    SaveAsPng(qr, "project-nayuki-automask-QR.png", 8, 6);
		    qr = QrCode.EncodeSegments(segs, QrCode.Ecc.High, QrCode.MinVersion, QrCode.MaxVersion, 3, true);  // Force mask 3
		    SaveAsPng(qr, "project-nayuki-mask3-QR.png", 8, 6);
		    
		    // Chinese text as UTF-8
		    segs = QrSegment.MakeSegments("維基百科（Wikipedia，聆聽i/ˌwɪkᵻˈpiːdi.ə/）是一個自由內容、公開編輯且多語言的網路百科全書協作計畫");
		    qr = QrCode.EncodeSegments(segs, QrCode.Ecc.Medium, QrCode.MinVersion, QrCode.MaxVersion, 0, true);  // Force mask 0
		    SaveAsPng(qr, "unicode-mask0-QR.png", 10, 3);
		    qr = QrCode.EncodeSegments(segs, QrCode.Ecc.Medium, QrCode.MinVersion, QrCode.MaxVersion, 1, true);  // Force mask 1
		    SaveAsPng(qr, "unicode-mask1-QR.png", 10, 3);
		    qr = QrCode.EncodeSegments(segs, QrCode.Ecc.Medium, QrCode.MinVersion, QrCode.MaxVersion, 5, true);  // Force mask 5
		    SaveAsPng(qr, "unicode-mask5-QR.png", 10, 3);
		    qr = QrCode.EncodeSegments(segs, QrCode.Ecc.Medium, QrCode.MinVersion, QrCode.MaxVersion, 7, true);  // Force mask 7
		    SaveAsPng(qr, "unicode-mask7-QR.png", 10, 3);
	    }

        #endregion


        #region Utilities

        private static void SaveAsPng(QrCode qrCode, string filename, int scale, int border)
        {
            using (var bitmap = qrCode.ToBitmap(scale, border))
            {
                bitmap.Save(filename, ImageFormat.Png);
            }
        }

        #endregion
		
    }
}
