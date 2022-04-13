package lsfusion.erp.utils;

import uk.org.okapibarcode.backend.DataMatrix;
import uk.org.okapibarcode.backend.HumanReadableLocation;
import uk.org.okapibarcode.backend.Symbol;
import uk.org.okapibarcode.output.Java2DRenderer;

import java.awt.*;
import java.awt.image.BufferedImage;

public class BarcodeUtils {

    // sample
    //lsfusion.erp.utils.BarcodeUtils.generateGS1DataMatrix($F{code(l)},uk.org.okapibarcode.backend.Symbol.DataType.GS1,10)
    // value should be :
    //[01]04810140842325[21]swsVofj0irNCT[91]EE06[92]WriL160vD8L7UaOcw4zNUw91zE/FVKM1owyF/EiGx7Y=
    public static BufferedImage generateGS1DataMatrix(String value, Symbol.DataType dataType, int magnification) {
        DataMatrix barcode = new DataMatrix();

        // We need a GS1 DataMatrix barcode.
        barcode.setDataType(dataType);
        // 0 means size will be set automatically according to amount of data (smallest possible).
        barcode.setPreferredSize(0);
        // Don't want no funky rectangle shapes, if we can avoid it.
        barcode.setForceMode(DataMatrix.ForceMode.SQUARE);

        barcode.setContent(value);

        BufferedImage image = new BufferedImage(barcode.getWidth() * magnification, barcode.getHeight() * magnification, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = image.createGraphics();
        Java2DRenderer renderer = new Java2DRenderer(g2d, magnification, Color.WHITE, Color.BLACK);
        renderer.render(barcode);

        return image;
    }
}
