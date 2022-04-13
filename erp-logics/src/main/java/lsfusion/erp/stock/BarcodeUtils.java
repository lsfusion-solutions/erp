package lsfusion.erp.stock;

import uk.org.okapibarcode.backend.DataMatrix;
import uk.org.okapibarcode.backend.Symbol;
import uk.org.okapibarcode.output.Java2DRenderer;

import java.awt.*;
import java.awt.image.BufferedImage;

public class BarcodeUtils {

    public static String appendCheckDigitToBarcode(String barcode) {
        return appendCheckDigitToBarcode(barcode, false);
    }
    
    public static String appendCheckDigitToBarcode(String barcode, boolean maybeUPC) {
        return appendCheckDigitToBarcode(barcode, null, maybeUPC);
    }
    
    public static String appendCheckDigitToBarcode(String barcode, Integer minLength) {
        return appendCheckDigitToBarcode(barcode, minLength, false);
    }
    
    public static String appendCheckDigitToBarcode(String barcode, Integer minLength, boolean maybeUPC) {
        
        if(barcode == null || (minLength != null && barcode.length() < minLength))
            return null;
        
        try {
            if (barcode.length() == 12) {
                //upc can't start from 2,4,5,9
                if(!barcode.startsWith("2") && !barcode.startsWith("4") && !barcode.startsWith("5") && !barcode.startsWith("9")) {
                    String upc = "0" + barcode; //UPC
                    if (upc.equals(appendEAN13(upc.substring(0, 12))))
                        return barcode;
                }
                if (maybeUPC)
                    return "0" + barcode;
                else { //EAN-13
                    return appendEAN13(barcode);
                }
            } else if (barcode.length() == 7) {  //EAN-8
                int checkSum = 0;
                for (int i = 0; i <= 6; i = i + 2) {
                    checkSum += Integer.parseInt(String.valueOf(barcode.charAt(i))) * 3;
                    checkSum += i == 6 ? 0 : Integer.parseInt(String.valueOf(barcode.charAt(i + 1)));
                }
                checkSum %= 10;
                if (checkSum != 0)
                    checkSum = 10 - checkSum;
                return barcode.concat(String.valueOf(checkSum));
            } else
                return barcode;
        } catch (Exception e) {
            return barcode;
        }
    }

    public static String appendCheckDigitToBarcodeWithoutUPC(String barcode) {
        if(barcode == null)
            return null;

        try {
            if (barcode.length() == 12) { //EAN-13
                return appendEAN13(barcode);
            } else if (barcode.length() == 7) {  //EAN-8
                int checkSum = 0;
                for (int i = 0; i <= 6; i = i + 2) {
                    checkSum += Integer.parseInt(String.valueOf(barcode.charAt(i))) * 3;
                    checkSum += i == 6 ? 0 : Integer.parseInt(String.valueOf(barcode.charAt(i + 1)));
                }
                checkSum %= 10;
                if (checkSum != 0)
                    checkSum = 10 - checkSum;
                return barcode.concat(String.valueOf(checkSum));
            } else
                return barcode;
        } catch (Exception e) {
            return barcode;
        }
    }

    private static String appendEAN13(String barcode) {
        int checkSum = 0;
        for (int i = 0; i <= 10; i = i + 2) {
            checkSum += Integer.parseInt(String.valueOf(barcode.charAt(i)));
            checkSum += Integer.parseInt(String.valueOf(barcode.charAt(i + 1))) * 3;
        }
        checkSum %= 10;
        if (checkSum != 0)
            checkSum = 10 - checkSum;
        return barcode.concat(String.valueOf(checkSum));
    }

    public static boolean isCheckDigitCorrect(String barcode) {
        if (barcode != null && (barcode.length() == 13 || barcode.length() == 8)) {
            return barcode.equals(appendCheckDigitToBarcodeWithoutUPC(barcode.substring(0, barcode.length() - 1)));
        }
        return true;
    }

    // sample
    //lsfusion.erp.stock.BarcodeUtils.generateDataMatrix($F{code(l)},uk.org.okapibarcode.backend.Symbol.DataType.GS1,10)
    // value should be :
    //[01]04810140842325[21]swsVofj0irNCT[91]EE06[92]WriL160vD8L7UaOcw4zNUw91zE/FVKM1owyF/EiGx7Y=

    public static BufferedImage generateDataMatrix(String value, Symbol.DataType dataType, int magnification) {
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