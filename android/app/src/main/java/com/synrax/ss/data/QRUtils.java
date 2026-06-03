package com.synrax.ss.data;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

/**
 * Utility helper to generate QR code Bitmaps using the ZXing library.
 */
public class QRUtils {

    /**
     * Encodes a string content into a QR Code bitmap.
     *
     * @param content The string text to encode in the QR code.
     * @param width   Target width of output bitmap.
     * @param height  Target height of output bitmap.
     * @return QR Code bitmap.
     * @throws WriterException If generation fails.
     */
    public static Bitmap generateQRCode(String content, int width, int height) throws WriterException {
        MultiFormatWriter writer = new MultiFormatWriter();
        BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height);
        
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
    }
}
