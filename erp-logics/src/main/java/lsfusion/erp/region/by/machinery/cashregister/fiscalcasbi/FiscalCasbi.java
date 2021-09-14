package lsfusion.erp.region.by.machinery.cashregister.fiscalcasbi;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class FiscalCasbi {

    public static final int SALE = 0;
    public static final int RETURN = 1;

    public interface casbiDLL extends Library {

        casbiDLL casbi = Native.load("ksb", casbiDLL.class);

        int ksb_errcode();

        void ksb_errdescription(int error, byte[] buffer, int length);

        Boolean ksb_open(byte[] comport, int baudrate);

        void ksb_close();

        Boolean ksb_clearsales();

        Boolean ksb_sale2(int num, int dept, byte[] qty, byte[] price, byte[] disc, byte[] sum, byte[] code, byte[] description, int refund);

        Boolean ksb_closereceipt(int type, long sum, int confirm);

        Boolean ksb_reportx();

        Boolean ksb_kl();

        Boolean ksb_reportz();

        Boolean ksb_feed();

        Boolean ksb_cashin(long sum);

        Boolean ksb_cashout(long sum);

        Boolean ksb_opendrw();

        Boolean ksb_display(int index, byte[] value);
    }

    static void init() {

        try {
            System.loadLibrary("ksb");
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public static String getError(boolean closePort) {
        int lastError = casbiDLL.casbi.ksb_errcode();
        int length = 255;
        byte[] lastErrorText = new byte[length];
        casbiDLL.casbi.ksb_errdescription(lastError, lastErrorText, length);
        if (closePort)
            closePort();
        return Native.toString(lastErrorText, "cp1251");
    }

    public static void openPort(int comPort, int baudRate) throws UnsupportedEncodingException {
        if (!casbiDLL.casbi.ksb_open(getBytes("COM" + comPort), baudRate))
            checkErrors(true);
    }

    public static void closePort() {
        casbiDLL.casbi.ksb_close();
    }

    public static void displayText(ReceiptItem item) throws UnsupportedEncodingException {
        String backLine = String.valueOf(item.sumPos.longValue());
        while (backLine.length() < 11)
            backLine = " " + backLine;
        if (!casbiDLL.casbi.ksb_display(16, getBytes("ИТОГ " + backLine)))
            checkErrors(true);

        String frontLine = item.quantity + "x" + item.price;
        while (frontLine.length() < 16)
            frontLine = " " + frontLine;
        if (!casbiDLL.casbi.ksb_display(0, getBytes(frontLine)))
            checkErrors(true);
    }

    public static boolean cancelReceipt() {
        return casbiDLL.casbi.ksb_clearsales();
    }

    public static boolean totalCash(BigDecimal sum) {
        if (sum == null)
            return true;
        return casbiDLL.casbi.ksb_closereceipt(0, Math.abs(sum.intValue()), 0);
    }

    public static boolean totalCard(BigDecimal sum) {
        if (sum == null)
            return true;
        return casbiDLL.casbi.ksb_closereceipt(1, Math.abs(sum.intValue()), 0);
    }

    public static void xReport() {
        if (!casbiDLL.casbi.ksb_reportx())
            checkErrors(true);
    }

    public static void closeKL() {
        if (!casbiDLL.casbi.ksb_kl())
            checkErrors(true);
    }

    public static void zReport() {
        if (!casbiDLL.casbi.ksb_reportz())
            checkErrors(true);
    }

    public static void advancePaper() {
        if (!casbiDLL.casbi.ksb_feed())
            checkErrors(true);
    }

    public static void inOut(Long sum) {

        if (sum > 0) {
            if (!casbiDLL.casbi.ksb_cashin(sum))
                checkErrors(true);
        } else {
            if (!casbiDLL.casbi.ksb_cashout(-sum))
                checkErrors(true);
        }
    }

    public static void openDrawer() {
        if (casbiDLL.casbi.ksb_opendrw())
            checkErrors(true);
    }

    public static boolean registerItem(ReceiptItem item, int index, boolean sale) throws UnsupportedEncodingException {

        BigDecimal discountSum = item.articleDiscSum == null ? BigDecimal.ZERO : item.articleDiscSum;
        BigDecimal discount = BigDecimal.ONE.subtract(BigDecimal.valueOf(item.sumPos.doubleValue() / item.sumPos.add(sale ? discountSum : discountSum.negate()).doubleValue())).multiply(BigDecimal.valueOf(100));
        BigDecimal sumPos = item.sumPos.add(sale ? discountSum : discountSum.negate());
        return casbiDLL.casbi.ksb_sale2(index, item.isGiftCard ? 2 : 1, getBytes(item.quantity), getBytes(String.valueOf(item.price)),
                getBytes(discount.setScale(2, RoundingMode.HALF_UP).negate()), getBytes(sumPos), getBytes(item.barCode), getBytes(item.name), sale ? SALE : RETURN);

    }

    public static int checkErrors(Boolean throwException) {
        int lastError = casbiDLL.casbi.ksb_errcode();
        if (lastError != 0) {
            if (throwException)
                throw new RuntimeException("Casbi Exception: " + lastError);
        }
        return lastError;
    }

    private static byte[] getBytes(BigDecimal input) throws UnsupportedEncodingException {
        return getBytes(toStr(input));
    }

    private static byte[] getBytes(String input) throws UnsupportedEncodingException {
        return (input + "\0").getBytes("cp1251");
    }

    private static String toStr(BigDecimal value) {
        if (value == null)
            return "0";
        else {
            boolean isInt = (value.subtract(BigDecimal.valueOf(value.intValue()))).equals(BigDecimal.ZERO);
            return isInt ? String.valueOf(value.intValue()) : String.valueOf(value);
        }
    }
}

