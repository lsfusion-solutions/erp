package lsfusion.erp.stock;

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

}