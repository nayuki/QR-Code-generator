package io.nayuki.qrcodegen;

import java.util.Locale;

public class QrCodeJavaSVG {

    /**
     * Based on the specified number of border modules to add as padding, this returns a
     * string whose contents represents an SVG XML file that depicts this QR Code symbol.
     * Note that Unix newlines (\n) are always used, regardless of the platform.
     *
     * @param border the number of border modules to add, which must be non-negative
     * @return a string representing this QR Code as an SVG document
     */
    public static String toSvgString(QrCode qr, int border) {
        if (border < 0)
            throw new IllegalArgumentException("Border must be non-negative");
        final int size = qr.size;

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n");
        sb.append(String.format(Locale.US, "<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 %1$d %1$d\">\n", size + border * 2));
        sb.append("\t<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\" stroke-width=\"0\"/>\n");
        sb.append("\t<path d=\"");
        boolean head = true;
        for (int y = -border; y < size + border; y++) {
            for (int x = -border; x < size + border; x++) {
                if (qr.getModule(x, y) == 1) {
                    if (head)
                        head = false;
                    else
                        sb.append(" ");
                    sb.append(String.format(Locale.US, "M%d,%dh1v1h-1z", x + border, y + border));
                }
            }
        }
        sb.append("\" fill=\"#000000\" stroke-width=\"0\"/>\n");
        sb.append("</svg>\n");
        return sb.toString();
    }

}
