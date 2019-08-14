package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.Charset;

public class FiscalMercury {

    private static BigDecimal multiplier = BigDecimal.valueOf(10000);

    public interface mercuryDLL extends Library {

        mercuryDLL mercury = (mercuryDLL) Native.loadLibrary("megawdriver", mercuryDLL.class);

        //подключение
        int FrWConnect();

        //логин
        int FrWLogIn(int code, String password, int mode, byte[] username); //code=1, password=1111111, Mode=1, username=Кассир

        //логаут
        int FrWLogOut();

        //отрезка чека
        int FrWCutReceipt();

        //прокрутка
        int FrWFeed(int count);

        //открытие денежного ящика
        int FrWOpenDrawer(); //непроверено

        //печать произвольных данных
        int FrWPrintData(byte[] strData, int station, String strFName);
        //station=0, strFName=null

        //проверка ошибок
        int FrWGetErrorDescription(int errorCode, byte[] description, int len);

        //открытие чека
        int FrWCreateDoc(char docType, int slip, boolean fLinePrn, String pathOne, String pathTwo);
        //docType=1 (продажа), slip=0, fLinePrn=true, pathOne=null, pathTwo=null

        int FrWAddItem(char Dept, Long Quantity, Long Price, Long/*String*/ code, String name, String comment, long[] sum, Short iStrCheck);

        //добавление строки чека
        int FrWAddItem2(short dept, String quantity, String price, String itemCode, String itemName, String itemComment, String somethingElse, String somethingElse2);
        //dept=1(номер секции)

        //добавление скидки к строке чека
        int FrWAdjustment2(short fPercent, String sum, String realSum, String totalSum);

        //оплата и закрытие чека
        int FrWPayment2(String sumCash, String sumCard, String sum3, String sum4, String sum5);

        //аннулирование чека
        int FrWCancelReceipt(); //непроверено

        //внесение наличных
        int FrWCashIncome(long sum, int curr);//curr=0

        //изъятие наличных
        int FrWCashOutcome(long sum, int curr);//curr=0

        //печать отчёта
        int FrWPrintReport(int type, String beg, String end); //type: 0 - x, 1- z, beg=null, end=null
    }

    public final static char SALE = 1;
    public final static char RETURN = 3;
    public final static char CASH_IN = 9;
    public final static char CASH_OUT = 10;

    public final static int CASHIER = 2;
    public final static int ADMIN = 3;

    public final static char ITEM = 1;
    public final static char GIFT_CARD = 2;

    //old
    static void init() throws UnsupportedEncodingException {
        try {
            System.loadLibrary("megawdriver");
        } catch (Exception e) {
            System.out.println(e.toString());
        }

        connect();

        try {
            Thread.sleep(100);
        } catch (Exception ignored) {
        }

    }

    public static void connect() throws RuntimeException, UnsupportedEncodingException {
        int result = mercuryDLL.mercury.FrWConnect();
        checkErrors(result, true);
    }

    public static boolean login(int user, String password, String cashierName) throws RuntimeException, UnsupportedEncodingException {
        logout();
        int result = mercuryDLL.mercury.FrWLogIn(1, password + "\0", user, (cashierName + "\0").getBytes("cp1251"));
        checkErrors(result, true);

        return true;
    }

    public static void logout() throws RuntimeException, UnsupportedEncodingException {
        int result = mercuryDLL.mercury.FrWLogOut();
        checkErrors(result, true);
    }

    public static void advancePaper(int count) throws RuntimeException, UnsupportedEncodingException {
        int result = mercuryDLL.mercury.FrWFeed(count);
        checkErrors(result, true);
    }

    public static boolean openDrawer() throws RuntimeException, UnsupportedEncodingException {
        int result = mercuryDLL.mercury.FrWOpenDrawer();
        return checkErrors(result, false);
    }

    public static void printString(String input) throws RuntimeException, UnsupportedEncodingException {
        if (input != null) {
            int result = mercuryDLL.mercury.FrWPrintData((input + "\n\0").getBytes("cp1251"), 0, null);
            checkErrors(result, true);
        }
    }

    public static void openDocument(char type) throws RuntimeException, UnsupportedEncodingException {
        int result = mercuryDLL.mercury.FrWCreateDoc(type, 0, true, "Shablon.txt\0", "Shablon1.txt\0");
        checkErrors(result, true);
    }

    public static void addItem(char dept, Long quantity, Long price, Long roundSum, Long discountSum, String barcode, char type) throws RuntimeException, UnsupportedEncodingException {
        long[] sum = new long[1];
        while (barcode.length() < 26)
            barcode += " ";
        int result = mercuryDLL.mercury.FrWAddItem(dept, quantity, price, null/*barcode + "\0"*/, "                            \0", "\0", sum, (short) 0);
        checkErrors(result, true);
        Long adjustment = roundSum - Long.parseLong(String.valueOf(sum[0])) - discountSum;
        if (adjustment != 0)
            addDiscount(String.valueOf(adjustment * (type == RETURN ? (-1) : 1)));
    }

    public static void addDiscount(String sum) throws RuntimeException, UnsupportedEncodingException {
        String realSum = null;
        String totalSum = null;
        int result = mercuryDLL.mercury.FrWAdjustment2((short) 1, sum, realSum, totalSum);
        checkErrors(result, true);
    }

    public static void payment(String sumCash, String sumCard, String sumGiftCard) throws RuntimeException, UnsupportedEncodingException {
        int result = mercuryDLL.mercury.FrWPayment2(sumCash, sumCard, sumGiftCard, "0", "0");
        checkErrors(result, true);
    }

    public static void cancelReceipt() throws RuntimeException, UnsupportedEncodingException {
        int result = mercuryDLL.mercury.FrWCancelReceipt();
        checkErrors(result, true);
    }

    public static boolean cashIncome(long sum) throws RuntimeException, UnsupportedEncodingException {
        int result = mercuryDLL.mercury.FrWCashIncome(sum, 0);
        return checkErrors(result, true);
    }

    public static boolean cashOutcome(long sum) throws RuntimeException, UnsupportedEncodingException {
        int result = mercuryDLL.mercury.FrWCashOutcome(sum, 0);
        return checkErrors(result, true);
    }

    public static void xReport() throws RuntimeException, UnsupportedEncodingException {
        int result = mercuryDLL.mercury.FrWPrintReport(0, null, null);
        checkErrors(result, true);
    }

    public static void zReport() throws RuntimeException, UnsupportedEncodingException {
        int result = mercuryDLL.mercury.FrWPrintReport(1, null, null);
        checkErrors(result, true);
    }

    public static void inOut(char type, long sum) throws RuntimeException, UnsupportedEncodingException {
        openDocument(type);
        if (sum > 0)
            cashIncome(sum * 10000);
        else
            cashOutcome(-sum * 10000);
        cutReceipt();
    }

    public static boolean cutReceipt() throws RuntimeException, UnsupportedEncodingException {
        int result = mercuryDLL.mercury.FrWCutReceipt();
        return checkErrors(result, true);
    }

    private static boolean checkErrors(int errorCode, Boolean throwException) throws RuntimeException {
        if (errorCode < 0) {
            if (throwException) {
                byte[] description = new byte[100];
                mercuryDLL.mercury.FrWGetErrorDescription(errorCode, description, 100);
                throw new RuntimeException(new String(description, Charset.forName("cp1251")).trim());
            } else return false;
        }
        return true;
    }

    public static void printReceipt(char type, ReceiptInstance receipt) throws RuntimeException, UnsupportedEncodingException {

        openDocument(type);

        try {
            if (receipt.numberDiscountCard != null && !receipt.numberDiscountCard.isEmpty())
                printString("Дисконтная карта: " + receipt.numberDiscountCard);
            if (receipt.holderDiscountCard != null && !receipt.holderDiscountCard.isEmpty())
                printString("Владелец: " + receipt.holderDiscountCard);

            //печать товаров
            for (ReceiptItem item : receipt.receiptList) {
                printString(item.barcode + " " + item.name);

                Long quantity = item.quantity == null ? null : item.quantity.multiply(multiplier).longValue();
                Long price = item.price == null ? 0 : item.price.multiply(multiplier).longValue();
                Long sum = item.sumPos == null ? 0 : item.sumPos.multiply(multiplier).longValue();
                Long discountSum = item.articleDiscSum == null ? 0 : item.articleDiscSum.multiply(multiplier).longValue();
                addItem(item.isGiftCard ? GIFT_CARD : ITEM, quantity, price, sum, discountSum, "\0", type);

                if (item.articleDiscSum != null && item.articleDiscSum.doubleValue() != 0)
                    addDiscount(String.valueOf(item.articleDiscSum.multiply(multiplier).multiply(BigDecimal.valueOf(type == RETURN ? (-1) : 1))));
            }

            if (receipt.giftCardNumbers != null)
                for(String giftCardNumber : receipt.giftCardNumbers)
                    printString("Использован сертификат: " + giftCardNumber);

            String sumCash = (receipt.sumCash != null) ? String.valueOf((type==RETURN ? receipt.sumCash.negate() : receipt.sumCash).multiply(multiplier)) : "0";
            String sumCard = (receipt.sumCard != null) ? String.valueOf((type==RETURN ? receipt.sumCard.negate() : receipt.sumCard).multiply(multiplier)) : "0";
            String sumGiftCard = (receipt.sumGiftCard != null) ? String.valueOf((type==RETURN ? receipt.sumGiftCard.negate() : receipt.sumGiftCard).multiply(multiplier)) : "0";
            payment(sumCash, sumCard, sumGiftCard);
            cutReceipt();

        } catch (RuntimeException e) {
            cancelReceipt();
            throw e;
        }
    }
}
