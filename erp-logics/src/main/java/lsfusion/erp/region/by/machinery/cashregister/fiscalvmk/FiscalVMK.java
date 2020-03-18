package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import com.google.common.base.Throwables;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.ptr.ByReference;
import com.sun.jna.ptr.IntByReference;
import org.apache.log4j.*;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

import static lsfusion.base.BaseUtils.trimToEmpty;

public class FiscalVMK {

    static Logger logger;
    static {
        try {
            logger = Logger.getLogger("cashRegisterLog");
            logger.setLevel(Level.INFO);
            FileAppender fileAppender = new FileAppender(new EnhancedPatternLayout("%d{DATE} %5p %c{1} - %m%n%throwable{1000}"),
                    "logs/cashregister.log");   
            logger.removeAllAppenders();
            logger.addAppender(fileAppender);
            
        } catch (Exception ignored) {
        }
    }
    
    public interface vmkDLL extends Library {

        vmkDLL vmk = (vmkDLL) Native.loadLibrary("vmkd", vmkDLL.class);

        Integer vmk_lasterror();

        void vmk_errorstring(Integer error, byte[] buffer, Integer length);

        Boolean vmk_open(String comport, Integer baudrate);

        void vmk_close();

        Boolean vmk_opensmn();

        Boolean vmk_opencheck(Integer type);

        Boolean vmk_cancel();

        Boolean vmk_sale(byte[] coddigit, byte[] codname, Double codcena, Integer ot, Double quantity,
                         Double sum);

        Boolean vmk_discount(byte[] name, Double value, int flag);

        Boolean vmk_discountpi(byte[] name, Double value, int flag);

        Boolean vmk_subtotal();

        Boolean vmk_prnch(byte[] message);

        Boolean vmk_prnbeg();

        Boolean vmk_repeat();

        Boolean vmk_oplat(Integer type, Double sum, Integer flagByte);

        Boolean vmk_xotch();

        Boolean vmk_zotch();

        Boolean vmk_feed(int type, int cnt_string, int cnt_dot_line);

        Boolean vmk_vnes(double sum);

        Boolean vmk_vyd(double sum);

        Boolean vmk_opendrawer(int cnt_msek);

        Boolean vmk_indik(byte[] firstLine, byte[] secondLine);

        Boolean vmk_indik2(byte[] firstLine);

        Boolean vmk_ksastat(ByReference rej, ByReference stat);

        Boolean vmk_ksainfo(byte[] buffer, int buflen);

        void vmk_setlogpath(byte[] path);
    }

    public static String getError(boolean closePort) {
        logAction("vmk_lasterror");
        Integer lastError = vmkDLL.vmk.vmk_lasterror();
        int length = 255;
        byte[] lastErrorText = new byte[length];
        logAction("vmk_errorstring");
        vmkDLL.vmk.vmk_errorstring(lastError, lastErrorText, length);
        if (closePort)
            closePort();
        String error = Native.toString(lastErrorText, "cp1251");
        logger.info(String.format("Ошибка %s: %s", lastError, error));
        return error;
    }

    public static void openPort(boolean isUnix, String logPath, String ip, String comPort, int baudRate) {
        try {
            setLogPath(logPath);
            if(ip != null) {
                openPort(ip, Integer.parseInt(comPort));
            } else {
                openPort(getPort(comPort, isUnix), baudRate);
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public static boolean safeOpenPort(final boolean isUnix, final String logPath, final String ip, final String comPort, final int baudRate, final int timeout) {
        try {
            final Future<Boolean> future = Executors.newSingleThreadExecutor().submit((Callable) () -> {
                setLogPath(logPath);
                if(ip != null) {
                    openPort(ip, Integer.parseInt(comPort));
                } else {
                    openPort(getPort(comPort, isUnix), baudRate);
                }
                return true;
            });

            boolean result = false;
            try {
                result = future.get(timeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
            }
            return result;
        } catch (InterruptedException | ExecutionException e) {
            throw Throwables.propagate(e);
        }
    }

    private static void setLogPath(String logPath) throws UnsupportedEncodingException {
        if (logPath != null) {
            logAction("vmk_setlogpath", logPath);
            vmkDLL.vmk.vmk_setlogpath((getBytes(logPath)));
        }
    }

    private static void openPort(String comPort, Integer baudRate) {
        logAction("vmk_open", comPort, baudRate);
        if (!vmkDLL.vmk.vmk_open(comPort, baudRate))
            checkErrors();
    }

    private static String getPort(String port, boolean isUnix) {
        return (isUnix ? "tty" : "COM") + port;
    }

    public static void closePort() {
        logAction("vmk_close");
        vmkDLL.vmk.vmk_close();
    }

    public static boolean openReceipt(int type) {    //0 - продажа, 1 - возврат
        logAction("vmk_opencheck", type);
        return vmkDLL.vmk.vmk_opencheck(type);
    }

    public static boolean cancelReceipt() {
        logAction("vmk_cancel");
        return vmkDLL.vmk.vmk_cancel();
    }

    public static boolean getFiscalClosureStatus() {
        IntByReference rej = new IntByReference();
        IntByReference stat = new IntByReference();
        logAction("vmk_ksastat");
        if (!vmkDLL.vmk.vmk_ksastat(rej, stat))
            return false;
        if (BigInteger.valueOf(stat.getValue()).testBit(14))
            if (!cancelReceipt())
                return false;
        return true;
    }

    static void simpleLogAction(String msg) {
        logger.info(msg);
    }

    public static boolean printMultilineFiscalText(String msg) {
        if (msg != null && !msg.isEmpty()) {
            int start = 0;
            while (start < msg.length()) {
                int end = Math.min(start + 24, msg.length());
                if (!printFiscalText(msg.substring(start, end)))
                    return false;
                start = end;
            }
        }
        return true;
    }

    //более правильный метод, но для обратной совместимости оставим оба
    public static boolean printMultilineFiscalText2(String msg) {
        if (msg != null && !msg.isEmpty()) {
            for(String line : msg.split("\n", -1)) {
                int start = 0;
                while (start < line.length()) {
                    int end = Math.min(start + 24, line.length());
                    if (!printFiscalText(line.substring(start, end)))
                        return false;
                    start = end;
                }
            }
        }
        return true;
    }

    public static boolean printFiscalText(String msg) {
        try {
        if(msg != null && !msg.isEmpty()) {
            for(String line : msg.split("\n", -1)) {
                if(line.isEmpty())
                    line = " ";
                logAction("vmk_prnch", line);
                boolean result = vmkDLL.vmk.vmk_prnch(getBytes(line));
                if(!result) return false;
            }
        }
        return true;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace(); 
            return false;
        }
    }

    public static boolean printHeaderReceipt() {
        logAction("vmk_prnbeg");
        return vmkDLL.vmk.vmk_prnbeg();
    }

    public static boolean repeatReceipt() {
        logAction("vmk_repeat");
        return vmkDLL.vmk.vmk_repeat();
    }

    public static boolean totalCash(BigDecimal sum) {
        if (sum == null)
            return true;
        double sumValue = sum.abs().doubleValue();
        logAction("vmk_oplat", 0, sumValue, 0);
        return vmkDLL.vmk.vmk_oplat(0, sumValue, 0/*"00000000"*/);
    }

    public static boolean totalCard(BigDecimal sum) {
        if (sum == null)
            return true;
        double sumValue = sum.abs().doubleValue();
        logAction("vmk_oplat", 1, sumValue, 0);
        return vmkDLL.vmk.vmk_oplat(1, sumValue, 0/*"00000000"*/);
    }

    public static boolean totalGiftCard(BigDecimal sum, Integer giftCardPaymentType) {
        if (sum == null)
            return true;
        int type = giftCardPaymentType != null ? giftCardPaymentType : 2;
        double sumValue = sum.doubleValue();
        logAction("vmk_oplat", type, Math.abs(sumValue), 0);
        return vmkDLL.vmk.vmk_oplat(type, Math.abs(sumValue), 0/*"00000000"*/);
    }

    public static boolean total(Integer type, BigDecimal sum) {
        if (sum == null)
            return true;
        double sumValue = sum.abs().doubleValue();
        logAction("vmk_oplat", type, sumValue, 0);
        return vmkDLL.vmk.vmk_oplat(type, sumValue, 0/*"00000000"*/);
    }

    public static boolean total(BigDecimal sumPayment, Integer typePayment) {
        double sumPaymentValue = sumPayment.abs().doubleValue();
        logAction("vmk_oplat", typePayment, sumPaymentValue, 0);
        if (!vmkDLL.vmk.vmk_oplat(typePayment, sumPaymentValue, 0/*"00000000"*/))
            return false;

        return true;
    }

    public static void xReport() {
        logAction("vmk_xotch");
        if (!vmkDLL.vmk.vmk_xotch())
            checkErrors();
    }

    public static void zReport() {
        logAction("vmk_zotch");
        if (!vmkDLL.vmk.vmk_zotch())
            checkErrors();
    }

    public static void advancePaper(int lines) {
        logAction("vmk_feed", 1, lines, 1);
        if (!vmkDLL.vmk.vmk_feed(1, lines, 1))
            checkErrors();
    }

    public static boolean inOut(BigDecimal sum) {
        double sumValue = sum.doubleValue();
        if (sumValue > 0) {
            logAction("vmk_vnes", sumValue);
            if (!vmkDLL.vmk.vmk_vnes(sumValue))
                checkErrors();
        } else {
            logAction("vmk_vyd", -sumValue);
            if (!vmkDLL.vmk.vmk_vyd(-sumValue))
                return false;
        }
        return true;
    }

    public static boolean openDrawer() {
        logAction("vmk_opendrawer");
        return vmkDLL.vmk.vmk_opendrawer(0);
    }

    public static void displayText(ReceiptItem item) {
        try {
            String firstLine = " " + toStr(item.quantity) + "x" + toStr(item.price);
            int length = 16 - Math.min(16, firstLine.length());
            firstLine = item.name.substring(0, Math.min(length, item.name.length())) + firstLine;
            String secondLine = String.valueOf(item.sumPos);
            while (secondLine.length() < 11)
                secondLine = " " + secondLine;
            secondLine = "ИТОГ:" + secondLine;
            logAction("vmk_indik", firstLine, secondLine);
            if(!vmkDLL.vmk.vmk_indik2(getBytes(secondLine)))
                checkErrors();
            if (!vmkDLL.vmk.vmk_indik(getBytes(firstLine), getBytes(secondLine)))
                checkErrors();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public static boolean registerAndDiscountItem(double sum, double discSum) {
        try {
            logAction("vmk_sale", "", "", sum, 1 /*отдел*/, 1, sum);
            if (!vmkDLL.vmk.vmk_sale(getBytes(""), getBytes(""), sum, 1/*отдел*/, 1.0, sum))
                checkErrors();
            if (discSum != 0) {
                boolean discount = discSum < 0;
                logAction("vmk_discount", discount ? "Скидка" : "Наценка", Math.abs(discSum), discount ? 3 : 1);
                return vmkDLL.vmk.vmk_discount(getBytes(discount ? "Скидка" : "Наценка"), Math.abs(discSum), discount ? 3 : 1);
            } else return true;
        } catch (UnsupportedEncodingException e) {
            return false;
        }
    }

    public static boolean registerItem(ReceiptItem item, Integer giftCardDepartment, Integer chargeDepartment) {
        try {
            double price = item.price == null ? 0.0 : item.price.abs().doubleValue();
            double sum = item.sumPos - item.articleDiscSum + item.bonusPaid;
            Integer department = item.isCharge ? (chargeDepartment != null ? chargeDepartment : 1) :
                    (item.isGiftCard ? (giftCardDepartment != null ? giftCardDepartment : 2) : (item.numberSection != null ? item.numberSection : 1)); /*отдел*/
            logAction("vmk_sale", item.barcode, item.name, price, department, item.quantity, sum);
            return vmkDLL.vmk.vmk_sale(getBytes(item.barcode), getBytes(item.name), //articleDiscSum is negative, bonusPaid is positive
                    price, department, item.quantity, sum);
        } catch (UnsupportedEncodingException e) {
            return false;
        }
    }

    public static boolean registerItemPayment(BigDecimal sumPayment) {
        try {
            double sum = sumPayment.doubleValue();
            logAction("vmk_sale", "", "ОПЛАТА", sum, 1 /*отдел*/, 1, 0);
            return vmkDLL.vmk.vmk_sale(getBytes(""), getBytes("ОПЛАТА"), sum, 1 /*отдел*/, 1.0, 0.0);
        } catch (UnsupportedEncodingException e) {
            return false;
        }
    }

    public static boolean registerItemPaymentDetail(InvoiceDetail detail) {
        try {
            logAction("vmk_sale", "", detail.name, detail.price, 1 /*отдел*/, detail.quantity, 0);
            return vmkDLL.vmk.vmk_sale(getBytes(""), getBytes(detail.name), detail.price.doubleValue(), 1 /*отдел*/, detail.quantity.doubleValue(), 0.0);
        } catch (UnsupportedEncodingException e) {
            return false;
        }
    }
    
    public static boolean discountItem(ReceiptItem item, String numberDiscountCard) {
        double discSum = item.articleDiscSum - item.bonusPaid; //articleDiscSum is negative, bonusPaid is positive
        if (discSum == 0)
            return true;
        boolean discount = discSum < 0;
        try {
            logAction("vmk_discount", discount ? "Скидка" : "Наценка", Math.abs(discSum), discount ? 3 : 1, "discountCard: " + numberDiscountCard);
            return vmkDLL.vmk.vmk_discount(getBytes(discount ? "Скидка" : "Наценка"), Math.abs(discSum), discount ? 3 : 1);
        } catch (UnsupportedEncodingException e) {
            return false;
        }
    }

    public static boolean discountReceipt(ReceiptInstance receipt) {
        if (receipt.sumDisc == null)
            return true;
        boolean discount = receipt.sumDisc.compareTo(BigDecimal.ZERO) < 0;
        try {
            double sumDisc =  receipt.sumDisc.abs().doubleValue();
            logAction("vmk_discountpi", discount ? "Скидка" : "Наценка", sumDisc, discount ? 3 : 1, "discountCard: " + receipt.numberDiscountCard);
            return vmkDLL.vmk.vmk_discountpi(getBytes(discount ? "Скидка" : "Наценка"), sumDisc, discount ? 3 : 1);
        } catch (UnsupportedEncodingException e) {
            return false;
        }
    }
    
    public static boolean subtotal() {
        logAction("vmk_subtotal");
        if (!vmkDLL.vmk.vmk_subtotal())
            return false;
        return true;
    }

    public static void opensmIfClose() {
        IntByReference rej = new IntByReference();
        IntByReference stat = new IntByReference();
        logAction("vmk_ksastat");
        if (!vmkDLL.vmk.vmk_ksastat(rej, stat))
            checkErrors();
        if (!BigInteger.valueOf(stat.getValue()).testBit(15)) {//15 - открыта ли смена 
            logAction("vmk_opensmn");
            if (!vmkDLL.vmk.vmk_opensmn())
                checkErrors();
        }
    }

    private static String toStr(double value) {
        boolean isInt = (value - (int) value) == 0;
        return isInt ? String.valueOf((int) value) : String.valueOf(value);
    }

    public static String toStr(BigDecimal value) {
        String result = null;
        if (value != null) {
            value = value.setScale(2, BigDecimal.ROUND_HALF_UP);
            DecimalFormat df = new DecimalFormat();
            df.setMaximumFractionDigits(2);
            df.setMinimumFractionDigits(2);
            df.setGroupingUsed(false);
            result = df.format(value).replace(",", ".");
        }
        return result;
    }

    public static void checkErrors() {
        logAction("vmk_lasterror");
        Integer lastError = vmkDLL.vmk.vmk_lasterror();
        if (lastError != 0) {
            throw new RuntimeException("VMK Exception: " + lastError);
        }
    }

    public static int getReceiptNumber() {
        byte[] buffer = new byte[50];
        logAction("vmk_ksainfo");
        if(!vmkDLL.vmk.vmk_ksainfo(buffer, 50))
            checkErrors();
        String result = Native.toString(buffer, "cp1251");
        String[] splitted = result.split(",");
        int receiptNumber = 0;
        try {
            if (splitted.length >= 5) {
                receiptNumber = (int) (Long.parseLong(splitted[4]) % Integer.MAX_VALUE);
            }
        } catch (Exception ignored) {
        }
        return receiptNumber != 0 ? receiptNumber : Integer.parseInt(splitted[0]);
    }

    public static int getZReportNumber() {
        byte[] buffer = new byte[50];
        logAction("vmk_ksainfo");
        if(!vmkDLL.vmk.vmk_ksainfo(buffer, 50))
            checkErrors();
        String result = Native.toString(buffer, "cp1251");
        return Integer.parseInt(result.split(",")[1]);
    }

    public static BigDecimal getCashSum() {
        byte[] buffer = new byte[50];
        logAction("vmk_ksainfo");
        if(!vmkDLL.vmk.vmk_ksainfo(buffer, 50))
            checkErrors();
        String result = Native.toString(buffer, "cp1251");
        return new BigDecimal(result.split(",")[2]);
    }

    public static void logReceipt(ReceiptInstance receipt, Integer numberReceipt) {
        OutputStreamWriter sw = null;
        try {

            sw = new OutputStreamWriter(new FileOutputStream(new File("logs/vmk.txt"), true), StandardCharsets.UTF_8);
            String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            for(ReceiptItem item : receipt.receiptSaleList) {
                sw.write(String.format("%s|%s|1|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s\r\n", dateTime, numberReceipt,
                        trimToEmpty(item.barcode), item.name, toStr(item.price), item.quantity, item.sumPos, item.articleDiscSum,
                        item.isGiftCard ? "1" : "0", trim(receipt.sumDisc), trim(receipt.sumCard), trim(receipt.sumCash),
                        trim(receipt.sumGiftCard), trim(receipt.sumTotal)));
            }

            for(ReceiptItem item : receipt.receiptReturnList) {
                sw.write(String.format("%s|%s|2|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s\r\n", dateTime, numberReceipt,
                        trimToEmpty(item.barcode), item.name, item.price, item.quantity, item.sumPos, item.articleDiscSum,
                        item.isGiftCard ? "1" : "0", trim(receipt.sumDisc), trim(receipt.sumCard), trim(receipt.sumCash),
                        trim(receipt.sumGiftCard), trim(receipt.sumTotal)));
            }
        } catch (IOException e) {
            logger.error("FiscalVMK Error: ", e);
        } finally {
            if (sw != null) {
                try {
                    sw.flush();
                    sw.close();
                } catch (IOException e) {
                    logger.error("FiscalVMK Error: ", e);
                }
            }
        }
    }

    private static void logAction(Object... actionParams) {
        String pattern = "";
        for(Object param : actionParams)
            pattern += "%s;";
        logger.info(String.format(pattern, actionParams));
    }

    private static String trim(BigDecimal value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static byte[] getBytes(String value) throws UnsupportedEncodingException {
        return (value + "\0").getBytes("cp1251");
    }
}

