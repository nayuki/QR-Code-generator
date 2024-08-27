package com.fuseanalytics.archiver.util;

import io.nayuki.qrcodegen.QrCode;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Use this class to generate images of your {@link io.nayuki.qrcodegen.QrCode} instances.
 * It uses sensible defaults to make the resulting QRCode look presentable without much
 * tweaking.  This class can be used by multiple threads provided after construction and 
 * configuration.  So any render* method will not cause threading issues, but calling the
 * configuration methods {@link #colors(Color, Color)}, {@link #border(int)}, {@link #scale}
 * will cause unpredictable errors.  As long as you use the configuration methods on one
 * thread before sharing the instance and only use render* methods from multiple threads
 * this class is thread safe.
 * <p> 
 * Here is a simple example of generating a {@see java.awt.BufferedImage}:
 * </p>
 *
 * <pre>{@code
 *  QrCode qr = QrCode.encodeText( "Hello World!", QrCode.Ecc.MEDIUM );
 *  BufferedImage img = new QrCodeRenderer().renderImage(qrCode);
 * }</pre>
 *
 * <p>
 * Here is an example, for rendering a PNG to a file:
 * </p>
 * <pre>{@code
 *  QrCode qr = QrCode.encodeText( "Hello World!", QrCode.Ecc.MEDIUM );
 *  File target = new QrCodeRenderer().renderFile(qrCode, "png", new File("output.png"));
 * }</pre>
 *
 * <p>
 * Here's an example of tweaking the defaults and rendering a PNG to an OutputStream:
 * </p>
 * <pre>{@code
 *  QrCode qr = QrCode.encodeText( "Hello World!", QrCode.Ecc.MEDIUM );
 *  ByteArrayOutputStream baos = new ByteArrayOutputStream();
 *  File target = new QrCodeRenderer()
 *      .scale(4)
 *      .border(10)
 *      .colors(Color.BLUE, Color.WHITE)
 *      .renderStream(qrCode, "png", baos);
 * }</pre>
 *
 * <p>
 * Here's an example, for rendering to SVG:
 * </p>
 * <pre>{@code
 *  QrCode qr = QrCode.encodeText( "Hello World!", QrCode.Ecc.MEDIUM );
 *  String svg = new QrCodeRenderer().renderSvg(qrCode);
 * }</pre>
 */
public class QrCodeRenderer {

    /**
     * The scale to use when rendering bitmap based images.  Default is 5.
     */
    private int scale = 5;

    /**
     * The size of border in pixels.  THe border will be scaled by the scale factor.
     * This provides some whitespace around QR code. This area will be rendered using
     * the light color.  Default is 2.
     */
    private int border = 2;

    /**
     * This the dark color used to render the QR code.  The default is black.
     */
    private Color darkColor = Color.BLACK;

    /**
     * This is the light color used to render the background of the QR Code.
     */
    private Color lightColor = Color.WHITE;

    /**
     * Renders a BufferedImage of the given QRCode object.
     * @param qrCode the QR code to use to render into a BufferedImage.
     * @return The bitmap rendering of the given QR Code.
     */
    public BufferedImage renderImage(QrCode qrCode) {
        BufferedImage img = new BufferedImage((qrCode.size + border * 2) * scale, (qrCode.size + border * 2) * scale, BufferedImage.TYPE_INT_RGB);
        for( int y = 0; y < img.getHeight(); y++ ) {
            for( int x = 0; x < img.getWidth(); x++ ) {
                int sx = x / scale - border;
                int sy = y / scale - border;
                boolean color = qrCode.getModule(sx,sy);
                img.setRGB(x, y, color ? darkColor.getRGB() : lightColor.getRGB() );
            }
        }
        return img;
    }

    /**
     * Renders an SVG document of the given QR Code.
     * @param qrCode the QRCode to render into the SVG document.
     * @return a String that contains the SVG markup of the QR Code.
     */
    public String renderSvg(QrCode qrCode) {
        long brd = border;

        StringBuilder buffer = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n")
                .append(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 %1$d %1$d\" stroke=\"none\">\n",
                        qrCode.size + brd * 2))
                .append("\t<rect width=\"100%\" height=\"100%\" fill=\"").append(toRgb(lightColor)).append("\"/>\n")
                .append("\t<path d=\"");
        for (int y = 0; y < qrCode.size; y++) {
            for (int x = 0; x < qrCode.size; x++) {
                if (qrCode.getModule(x, y)) {
                    if (x != 0 || y != 0)
                        buffer.append(" ");
                    buffer.append(String.format("M%d,%dh1v1h-1z", x + brd, y + brd));
                }
            }
        }

        return buffer
                .append("\" fill=\"").append(toRgb(darkColor)).append("\"/>\n")
                .append("</svg>\n")
                .toString();
    }

    /**
     * Renders the QR Code to a bitmap and writes that to the given file in the given format provided.
     * @param qrCode The QRCode to generate the bitmap of.
     * @param formatName The image format to write (png, jpeg, etc).  These are the same formats supported by {@see javax.imageio.ImageIO}.
     * @param output A java.io.File you want to write the given QRCode in the given format into.
     * @return Returns the given File for convenience that contains the bitmap of the QRCode.
     * @throws IOException if any IOException happens while writing to the given File.
     * @throws IllegalArgumentException if the given formatName cannot be found in ImageIO library.
     */
    public File renderFile(QrCode qrCode, String formatName, File output) throws IOException {
        BufferedImage img = renderImage(qrCode);
        if( ImageIO.write( img, formatName, output ) ) {
            return output;
        } else {
            throw new IllegalArgumentException("No appropriate writer found for formatName=" + formatName );
        }
    }

    /**
     * Renders the QR Code to a bitmap and writes that to the given OutputStream in the given format provided.
     * @param qrCode The QRCode to generate the bitmap of.
     * @param formatName The image format to write (png, jpeg, etc).  These are the same formats supported by {@see javax.imageio.ImageIO}.
     * @param stream The OutputStream in which to write the bitmap image of the given QRCode in the given formatName into.
     * @throws IOException if any IOException happens while writing to the given OutputStream.
     * @throws IllegalArgumentException if the given formatName cannot be found in ImageIO library.
     */
    public void renderStream(QrCode qrCode, String formatName, OutputStream stream ) throws IOException {
        BufferedImage img = renderImage( qrCode );
        if( !ImageIO.write( img, formatName, stream ) ) {
            throw new IllegalArgumentException("No appropriate writer found for formatName=" + formatName );
        }
    }

    private String toRgb(Color color) {
        return "rgb(" + color.getRed() + " " + color.getGreen() + " " + color.getBlue() + ")";
    }

    /**
     * Overwrite the border default by specifying the number of pixels to use as a border.  This is
     * the number of pixels to use before scaling is applied so the resulting border will be scaled
     * up if scale > 1.
     * @param border pixels to add around the resulting QRCode bitmap
     * @return this
     */
    public QrCodeRenderer border( int border ) {
        if( border < 0 ) throw new IllegalArgumentException("Border must be non-negative: " + border );
        if( border > Integer.MAX_VALUE / 2 ) throw new IllegalArgumentException("Border size is too large.");
        this.border = border;
        return this;
    }

    /**
     * Sets the scale to use when writing into the Bitmap image.
     *
     * @param scale the amount to scale the image by.  1 performs no sale, 2 doubles it, 3 triples, etc.
     * @return this
     */
    public QrCodeRenderer scale( int scale ) {
        if( scale < 0 ) throw new IllegalArgumentException("Scale must be non-negative: " + scale );
        if( (QrCode.MAX_VERSION * 4 + 17) * scale + border*2 > Integer.MAX_VALUE / scale ) throw new IllegalArgumentException("Scale is too large: " + scale);
        this.scale = scale;
        return this;
    }

    /**
     * Sets the colors to use when rendering the QRCode.  The dark color will be used to write
     * the QR Code, and the light color will be used as the background.
     * @param darkColor the color to use for the QRCode
     * @param lightColor the color to use as the background color.
     * @return this
     */
    public QrCodeRenderer colors( Color darkColor, Color lightColor ) {
        this.darkColor = darkColor;
        this.lightColor = lightColor;
        return this;
    }
}
