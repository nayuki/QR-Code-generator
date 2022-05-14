package ru.ra66it.qrcodegeneratorexample;

import android.graphics.Bitmap;
import android.graphics.Color;

import ru.ra66it.qrcodegenerator.QrCode;

public class QRCodeGenerator {

    public Bitmap generate(String content) {
        QrCode qr = QrCode.encodeText(content, QrCode.Ecc.HIGH);
        int scale = 8;

        int size = qr.size * scale;

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                boolean isDark = qr.getModule(x / scale, y / scale);
                int color;
                if (isDark) {
                    color = Color.BLACK;
                } else {
                    color = Color.WHITE;
                }

                bitmap.setPixel(x, y, color);
            }
        }

        return bitmap;
    }
}
