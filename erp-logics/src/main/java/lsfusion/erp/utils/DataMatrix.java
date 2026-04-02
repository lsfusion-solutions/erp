package lsfusion.erp.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.datamatrix.DataMatrixWriter;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class DataMatrix {

    public static BufferedImage toBufferedImage(String text, boolean hasGS1FormatHint) throws WriterException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        if(hasGS1FormatHint) {
            hints.put(EncodeHintType.GS1_FORMAT, Boolean.TRUE);
        }

        return MatrixToImageWriter.toBufferedImage(
                new DataMatrixWriter().encode(text, BarcodeFormat.DATA_MATRIX, 1000, 1000, hints)
        );
    }
}
