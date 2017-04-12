package io.nayuki.qrcodegen;

import java.awt.image.BufferedImage;

public class QrCodeJavaSE {

    /**
     * Returns a new image object representing this QR Code, with the specified module scale and number
     * of border modules. For example, the arguments scale=10, border=4 means to pad the QR Code symbol
     * with 4 white border modules on all four edges, then use 10*10 pixels to represent each module.
     * The resulting image only contains the hex colors 000000 and FFFFFF.
     *
     * @param scale  the module scale factor, which must be positive
     * @param border the number of border modules to add, which must be non-negative
     * @return an image representing this QR Code, with padding and scaling
     * @throws IllegalArgumentException if the scale or border is out of range
     */
    public static BufferedImage toImage(QrCode qrCode, int scale, int border) {
        if (scale <= 0 || border < 0)
            throw new IllegalArgumentException("Value out of range");
        final int size = qrCode.size;
        BufferedImage result = new BufferedImage((size + border * 2) * scale, (size + border * 2) * scale, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < result.getHeight(); y++) {
            for (int x = 0; x < result.getWidth(); x++) {
                int val = qrCode.getModule(x / scale - border, y / scale - border);  // 0 or 1
                result.setRGB(x, y, val == 1 ? 0x000000 : 0xFFFFFF);
            }
        }
        return result;
    }

}
