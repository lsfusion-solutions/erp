package lsfusion.erp.region.by.machinery.cashregister.fiscalsento;

import com.google.common.base.Throwables;
import com.sun.jna.Library;
import com.sun.jna.Native;
import org.apache.commons.lang3.StringUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.BitSet;
import java.util.concurrent.*;

import static lsfusion.base.BaseUtils.trimToEmpty;
import static lsfusion.erp.ERPLoggers.cashRegisterlogger;

public class FiscalSento {

    static final int FLG_EXT_SALE = 0; // 1 - Расширенная регистрация покупки
    
    public interface sentoDLL extends Library {

        sentoDLL sento = Native.load("sento", sentoDLL.class);

        Integer lastError();

        void errorString(Integer error, byte[] buffer, Integer length);

        boolean openPort(String comport, Integer baudrate);

        void closePort();

        Integer getVersion();

        boolean openDay(double value);

        boolean openRefundDocument(int department, byte[] plu, int vat, double price, double quant, double amount, byte[] name);
    
        boolean openRefundDocumentMark(int department, byte[] plu, int vat, double price, double quant, double amount, byte[] name, byte[] ukzCode, byte[] siCode);

        boolean cancelDocument();
    
        boolean annulDocument(int documentNumber);
        
        boolean sale(int operation, int department, byte[] plu, int vat, double price, double quant, double amount, byte[] naim, byte[] comment);
    
        boolean saleMark(int operation, int department, byte[] plu, int vat, double price, double quant, double amount, byte[] naim, byte[] ukzCode, byte[] siCode);

        boolean discount(int operation, double value);

        int printText(int operation, byte[] text);

        int printLastDocument();

        boolean closeDocument(double total, double cash, double check, double paycard, double coupon, double credit);

        boolean report(int tip);

        boolean cashIn(double sum);

        boolean cashOut(double sum);
        
        boolean openDrawer();

        boolean message(int operation, byte[] text);

        boolean statusDocument(byte[] buffer, int buflen);

        void setLogPath(byte[] path);
    }

    public static String getError(int lastError) {
        int length = 255;
        byte[] lastErrorText = new byte[length];
        logAction("errorString");
        sentoDLL.sento.errorString(lastError, lastErrorText, length);
        String error = Native.toString(lastErrorText, "cp1251");
        cashRegisterlogger.info(String.format("Ошибка %s: %s", lastError, error));
        return error;
    }

    public static void openPort(boolean isUnix, String logPath, String comPort, int baudRate) {
        setLogPath(logPath);
        openPort(getPort(comPort, isUnix), baudRate);
    }

    public static boolean safeOpenPort(final boolean isUnix, final String logPath, final String comPort, final int baudRate, final int timeout) {
        try {
            final Future<Boolean> future = Executors.newSingleThreadExecutor().submit((Callable) () -> {
                setLogPath(logPath);
                openPort(getPort(comPort, isUnix), baudRate);
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

    private static void setLogPath(String logPath) {
        if (logPath != null) {
            logAction("setLogPath", logPath);
            sentoDLL.sento.setLogPath((getBytes(logPath)));
        }
    }

    private static void openPort(String comPort, Integer baudRate) {
        logAction("openPort", comPort, baudRate);
        if (!sentoDLL.sento.openPort(comPort, baudRate))
            checkErrors();
        else {
            Integer version = sentoDLL.sento.getVersion();
            logAction("version", version);
        }
    }

    private static String getPort(String port, boolean isUnix) {
        return (isUnix ? "tty" : "COM") + port;
    }

    public static void closePort() {
        logAction("closePort");
        sentoDLL.sento.closePort();
    }

    public static void openRefundDocument(ReceiptItem item, long flags) {
        BitSet bits = BitSet.valueOf(new long[]{flags});
        double price = item.price == null ? 0.0 : item.price.abs().doubleValue();
        double sum = item.sumPos - item.articleDiscSum; //we need sum without discount
        logAction("openRefundDocument", flags, 1, item.barcode, getVAT(item.numberSection), price, item.quantity, sum, item.name);
        
        boolean result;
    
        Integer version = sentoDLL.sento.getVersion();
        
        if (version >= 200 && !StringUtils.isEmpty(item.marka) || !StringUtils.isEmpty(item.ukz)) {
            result = sentoDLL.sento.openRefundDocumentMark(
                    item.numberDepartment, getBytes(item.barcode), getVAT(item.numberSection),
                    price, item.quantity, sum, getBytes(item.name),
                    getBytes(item.marka != null ? item.marka : ""),
                    getBytes(item.ukz != null ? item.ukz : "")
            );
        }
        else {
            if (bits.get(FLG_EXT_SALE))
                result = sentoDLL.sento.openRefundDocument(1, getBytes(item.barcode), getVAT(item.numberSection), sum, 1, sum, getBytes(item.name));
            else
                result = sentoDLL.sento.openRefundDocument(1, getBytes(item.barcode), getVAT(item.numberSection), price, item.quantity, sum, getBytes(item.name));
        }

        if(!result)
            checkErrors();
    }

    private static int getVAT(String section) {
        if (section != null) {
            return section.charAt(0);
        } else return 'A';
    }

    public static void cancelReceipt() {
        logAction("cancelDocument");
        if(!sentoDLL.sento.cancelDocument())
            checkErrors();
    }
    
    public static void annulDocument(int documentNumber) {
        logAction("annulDocument", documentNumber);
        if(!sentoDLL.sento.annulDocument(documentNumber))
            checkErrors();
    }

    public static void opensmIfClose() {
        int shiftNum = getZReportNumber();
        if (shiftNum == 0) {
            if (!sentoDLL.sento.openDay(0))
                checkErrors();
        }
    }

    public static int getZReportNumber() {
        String[] statusDocument = statusDocument();
        String zReportNumber = statusDocument.length > 4 ? statusDocument[4] : "0";
        return Integer.parseInt(zReportNumber);
    }

    public static int getReceiptNumber() {
        String[] statusDocument = statusDocument();
        String receiptNumber = statusDocument.length > 9 ? statusDocument[9] : "0";
        return Integer.parseInt(receiptNumber);
    }

    public static String[] statusDocument() {
        byte[] buffer = new byte[255];
        logAction("statusDocument");
        if (!sentoDLL.sento.statusDocument(buffer, 255)) {
            checkErrors();
        }
        return new String(buffer).split(",");
    }

    public static void printFiscalText(String msg) {
        if (msg != null && !msg.isEmpty()) {
            if(sentoDLL.sento.printText(1, getBytes(msg)) == 0)
                checkErrors();
        }
    }

    public static void repeatReceipt() {
        logAction("printLastDocument");
        if(sentoDLL.sento.printLastDocument() == 0)
            checkErrors();
    }

    public static void closeDocument(ReceiptInstance receipt) {
        double total = receipt.sumTotal == null ? 0 : receipt.sumTotal.abs().doubleValue();
        double cash = receipt.sumCash == null ? 0 : receipt.sumCash.abs().doubleValue();
        double check = receipt.sumCheck == null ? 0 : receipt.sumCheck.abs().doubleValue();
        double payCard = receipt.sumCard == null ? 0 : receipt.sumCard.abs().doubleValue();
        double coupon = receipt.sumGiftCard == null ? 0 : receipt.sumGiftCard.abs().doubleValue();
        double credit = receipt.sumSalary == null ? 0 : receipt.sumSalary.abs().doubleValue();

        logAction("closeDocument", total, cash, 0, payCard, coupon, credit);
        if(!sentoDLL.sento.closeDocument(total, cash, check, payCard, coupon, credit))
            checkErrors();
    }

    public static void xReport() {
        logAction("report", 2);
        if (!sentoDLL.sento.report(2))
            checkErrors();
    }

    public static void zReport() {
        logAction("report", 1);
        if (!sentoDLL.sento.report(1))
            checkErrors();
    }

    public static void inOut(BigDecimal sum) {
        double sumValue = sum.doubleValue();
        if (sumValue > 0) {
            logAction("cashIn", sumValue);
            if (!sentoDLL.sento.cashIn(sumValue))
                checkErrors();
        } else {
            logAction("cashOut", -sumValue);
            if (!sentoDLL.sento.cashOut(-sumValue))
                checkErrors();
        }
    }

    public static void openDrawer() {
        Integer version = sentoDLL.sento.getVersion();
        if (version >= 104) {
            if (!sentoDLL.sento.openDrawer())
                checkErrors();
        }
    }

    public static void displayText(ReceiptItem item) {
        String firstLine = item.name.substring(0, Math.min(22, item.name.length()));

        String quantity = toStr(item.quantity);
        String price = toStr(item.price);
        String secondLine = "x " + quantity + getSpaces(20 - quantity.length() - price.length()) + price;

        String sumPos = toStr(item.sumPos);
        String thirdLine = "ИТОГ: " + getSpaces(16 - sumPos.length()) + sumPos;

        logAction("message", firstLine, secondLine, thirdLine);

        Integer version = sentoDLL.sento.getVersion();
        if (version >= 104) {
            if (!sentoDLL.sento.message(3, getBytes(firstLine + secondLine + thirdLine)))
                checkErrors();
        }
    }

    private static String getSpaces(int length) {
        StringBuilder result = new StringBuilder();
        while (result.length() < length)
            result.append(" ");
        return result.toString();
    }

    public static void registerItem(ReceiptItem item, String comment, Integer giftCardDepartment, Integer flags) {
        BitSet bits = BitSet.valueOf(new long[]{flags});
        double price = item.price == null ? 0.0 : item.price.abs().doubleValue();
        double sum = item.sumPos - item.articleDiscSum; //we need sum without discount
        int department = item.isGiftCard && giftCardDepartment != null ? giftCardDepartment : 1;
        logAction("sale", flags, 6, department, item.barcode, getVAT(item.numberSection), price, item.quantity, sum, item.name, comment != null ? comment : "");
        
        boolean result;
    
        Integer version = sentoDLL.sento.getVersion();
        
        if (version >= 200 && !StringUtils.isEmpty(item.marka) || !StringUtils.isEmpty(item.ukz)) {
            result = sentoDLL.sento.saleMark(
                    (short) 6, item.numberDepartment, getBytes(item.barcode), getVAT(item.numberSection),
                    price, item.quantity, sum, getBytes(item.name),
                    getBytes(item.marka != null ? item.marka : ""),
                    getBytes(item.ukz != null ? item.ukz : "")
            );
        }
        else {
            if (bits.get(FLG_EXT_SALE)) {
                String textPrice = String.format("Цена: %.2f", item.price);
                String description = String.format("Кол-во: %.4f", item.quantity);
                description = StringUtils.rightPad(description, 35 - textPrice.length(), '.') + textPrice;
                result = sentoDLL.sento.sale((short) 6, department, getBytes(item.barcode), getVAT(item.numberSection), sum, 1, sum, getBytes(description), getBytes(item.name));
            } else
                result = sentoDLL.sento.sale((short) 6, department, getBytes(item.barcode), getVAT(item.numberSection), price, item.quantity, sum, getBytes(item.name), getBytes(comment != null ? comment : ""));
        }

        if(!result)
            checkErrors();
    }

    public static void discountItem(ReceiptItem item) {
        if (item.articleDiscSum != 0) {
            logAction("discount", item.articleDiscSum < 0 ? 0xD7 : 0xDB, Math.abs(item.articleDiscSum));
            if (!sentoDLL.sento.discount(item.articleDiscSum < 0 ? 0xD7 : 0xDB, Math.abs(item.articleDiscSum)))
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
            value = value.setScale(2, RoundingMode.HALF_UP);
            DecimalFormat df = new DecimalFormat();
            df.setMaximumFractionDigits(2);
            df.setMinimumFractionDigits(2);
            df.setGroupingUsed(false);
            result = df.format(value).replace(",", ".");
        }
        return result;
    }

    public static void checkErrors() {
        logAction("lastError");
        Integer lastError = sentoDLL.sento.lastError();
        int length = 255;
        byte[] lastErrorText = new byte[length];
        logAction("errorString");
        sentoDLL.sento.errorString(lastError, lastErrorText, length);
        String error = Native.toString(lastErrorText, "cp1251");
        cashRegisterlogger.info(String.format("Ошибка %s: %s", lastError, error));
        if (!error.isEmpty()) {
            throw new RuntimeException("Sento Exception: " + error);
        }
    }

    public static void logReceipt(ReceiptInstance receipt, Integer numberReceipt) {
        OutputStreamWriter sw = null;
        try {

            sw = new OutputStreamWriter(new FileOutputStream("logs/sento.txt", true), StandardCharsets.UTF_8);
            String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            for(ReceiptItem item : receipt.receiptSaleList) {
                sw.write(String.format("%s|1|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s\r\n", dateTime, numberReceipt,
                        trimToEmpty(item.barcode), item.name, toStr(item.price), item.quantity, item.sumPos, item.articleDiscSum,
                        trim(receipt.sumDisc), trim(receipt.sumCard), trim(receipt.sumCash),
                        trim(receipt.sumGiftCard), trim(receipt.sumTotal)));
            }

            for(ReceiptItem item : receipt.receiptReturnList) {
                sw.write(String.format("%s|2|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s\r\n", dateTime, numberReceipt,
                        trimToEmpty(item.barcode), item.name, item.price, item.quantity, item.sumPos, item.articleDiscSum,
                        trim(receipt.sumDisc), trim(receipt.sumCard), trim(receipt.sumCash),
                        trim(receipt.sumGiftCard), trim(receipt.sumTotal)));
            }
        } catch (IOException e) {
            cashRegisterlogger.error("FiscalSento Error: ", e);
        } finally {
            if (sw != null) {
                try {
                    sw.flush();
                    sw.close();
                } catch (IOException e) {
                    cashRegisterlogger.error("FiscalSento Error: ", e);
                }
            }
        }
    }

    private static void logAction(Object... actionParams) {
        String pattern = "";
        for(Object param : actionParams)
            pattern += "%s;";
        cashRegisterlogger.info(String.format(pattern, actionParams));
    }

    private static String trim(BigDecimal value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static byte[] getBytes(String value) {
        return (value + "\0").getBytes(Charset.forName("cp1251"));
    }
}

