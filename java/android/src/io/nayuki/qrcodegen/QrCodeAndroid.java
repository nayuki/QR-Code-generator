package io.nayuki.qrcodegen;

import android.graphics.Bitmap;


public class QrCodeAndroid {

    /**
     * Returns a new bitmap object representing this QR Code, with the specified module scale and number
     * of border modules. For example, the arguments scale=10, border=4 means to pad the QR Code symbol
     * with 4 white border modules on all four edges, then use 10*10 pixels to represent each module.
     * The resulting image only contains the hex colors FF000000 and FFFFFFFF.
     *
     * @param scale  the module scale factor, which must be positive
     * @param border the number of border modules to add, which must be non-negative
     * @return a bitmap representing this QR Code, with padding and scaling
     * @throws IllegalArgumentException if the scale or border is out of range
     */
    public static Bitmap toBitmap(QrCode qrCode, int scale, int border, Bitmap.Config config) {
        if (scale <= 0 || border < 0)
            throw new IllegalArgumentException("Value out of range");
        final int size = qrCode.size;
        Bitmap result = Bitmap.createBitmap((size + border * 2) * scale, (size + border * 2) * scale,
                config);
        for (int y = 0; y < result.getHeight(); y++) {
            for (int x = 0; x < result.getWidth(); x++) {
                int val = qrCode.getModule(x / scale - border, y / scale - border);  // 0 or 1
                result.setPixel(x, y, val == 1 ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return result;
    }

}
