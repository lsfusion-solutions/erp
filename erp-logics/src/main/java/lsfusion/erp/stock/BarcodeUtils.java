package lsfusion.erp.stock;

public class BarcodeUtils {

    public static String convertBarcode12To13(String barcode) {
        if (barcode != null && barcode.length() == 12) {
            int checkSum = 0;
            for (int i = 0; i <= 10; i = i + 2) {
                checkSum += Integer.valueOf(String.valueOf(barcode.charAt(i)));
                checkSum += Integer.valueOf(String.valueOf(barcode.charAt(i + 1))) * 3;
            }
            checkSum %= 10;
            if (checkSum != 0)
                checkSum = 10 - checkSum;
            return barcode.concat(String.valueOf(checkSum));
        } else
            return barcode;
    }
}