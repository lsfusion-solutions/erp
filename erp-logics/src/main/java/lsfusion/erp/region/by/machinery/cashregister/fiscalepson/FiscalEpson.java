package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.ComFailException;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Calendar;
import java.util.Date;

public class FiscalEpson {

    static ActiveXComponent epsonActiveXComponent;
    static Dispatch epsonDispatch;

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

    static void init() {
        try {
            if(epsonDispatch == null) {
                epsonActiveXComponent = new ActiveXComponent("UniFiscalOcx.DrvUniFR");
                epsonDispatch = epsonActiveXComponent.getObject();
            }
        } catch (UnsatisfiedLinkError e) {
            System.out.println(e.toString());
        }
    }

    public static void openPort(int comPort, int baudRate) throws RuntimeException {

        closePort();

        epsonActiveXComponent.setProperty("ComPort", new Variant(comPort));
        epsonActiveXComponent.setProperty("BaudRate", new Variant(baudRate));
        Dispatch.call(epsonDispatch, "Connect");
        checkErrors(true);
    }

    public static void closePort() throws RuntimeException {
        if(epsonDispatch != null) {
            Dispatch.call(epsonDispatch, "Disconnect");
        }
    }

    public static void openReceipt(String cashier, int type) throws RuntimeException {
        setCashier(cashier);
        epsonActiveXComponent.setProperty("ReceiptType", new Variant(type));
        Dispatch.call(epsonDispatch, "OpenReceipt");
        checkErrors(true);
    }

    public static Integer getElectronicJournalReadOffset() throws RuntimeException {
        return toInt(epsonActiveXComponent.getProperty("ElectronicJournalReadOffset"));
    }

    public static void closeReceipt() {
        boolean checkErrors = true;
        try {
            logger.info("Epson CloseReceipt started");
            long time = System.currentTimeMillis();
            Dispatch.call(epsonDispatch, "CloseReceipt");
            logger.info(String.format("Epson CloseReceipt finished: %s ms", (System.currentTimeMillis() - time)));
        } catch (ComFailException e) {
            if (e.getMessage() != null && e.getMessage().contains("ФБ: таймаут связи с СКНО")) {
                checkErrors = false;
                logger.info("Epson CloseReceipt error: ФБ: таймаут связи с СКНО");
            } else {
                logger.error("Epson CloseReceipt error: ", e);
                throw e;
            }
        } catch (Exception e) {
            logger.error("Epson CloseReceipt error: ", e);
            throw e;
        }
        if (checkErrors) {
            checkErrors(true);
        }
    }

    public static void cancelReceipt(boolean throwException) throws RuntimeException {
        if(epsonDispatch != null) {
            Dispatch.call(epsonDispatch, "CancelPrint");
            checkErrors(throwException);
        }
    }

    public static void resetReceipt(String cashier, Integer documentNumberReceipt, BigDecimal totalSum, BigDecimal sumCash, BigDecimal sumCard, BigDecimal sumGiftCard, Integer cardType, Integer giftCardType) throws RuntimeException {
        boolean sale = totalSum.doubleValue() > 0;
        epsonActiveXComponent.setProperty("CancellationDocumentNumber", new Variant(documentNumberReceipt));
        epsonActiveXComponent.setProperty("CancellationAmount", new Variant(totalSum));
        openReceipt(cashier, 5);

        Dispatch.call(epsonDispatch, "CompleteReceipt");
        checkErrors(true);
        if(sumCard != null) {
            epsonActiveXComponent.setProperty("Amount", new Variant(sumCard.doubleValue()));
            epsonActiveXComponent.setProperty("NoncashType", new Variant(cardType == null ? 0 : cardType));
            Dispatch.call(epsonDispatch, sale ? "Repaynoncash" : "PayNoncash");
            checkErrors(true);
        }
        if(sumGiftCard != null) {
            epsonActiveXComponent.setProperty("Amount", new Variant(sumGiftCard.doubleValue()));
            epsonActiveXComponent.setProperty("NonCashType", new Variant(giftCardType == null ? 1 : giftCardType));
            Dispatch.call(epsonDispatch, sale ? "Repaynoncash" : "PayNoncash");
            checkErrors(true);
        }
        if(sumCash != null) {
            epsonActiveXComponent.setProperty("Amount", new Variant(sumCash.doubleValue()));
            Dispatch.call(epsonDispatch, sale ? "RepayCash" : "PayCash");
            checkErrors(true);
        }
        closeReceipt();
    }

    public static Integer zReport() throws RuntimeException {
        openDayIfClosed();
        Integer zReportNumber = getReceiptInfo().sessionNumber;
        Dispatch.call(epsonDispatch, "PrintZReport");
        checkErrors(true);
        return zReportNumber;
    }

    public static void electronicJournal() throws RuntimeException {
        openDayIfClosed();
        Dispatch.call(epsonDispatch, "PrintElectronicJournal");
        checkErrors(true);
    }

    public static String readElectronicJournal(Integer offsetBefore) throws RuntimeException {
        Integer offset = getElectronicJournalReadOffset();
        checkErrors(true);

        epsonActiveXComponent.setProperty("ElectronicJournalReadOffset", offsetBefore);
        epsonActiveXComponent.setProperty("ElectronicJournalReadSize", offset - offsetBefore);

        Dispatch.call(epsonDispatch, "ReadElectronicJournal");
        checkErrors(true);

        return epsonActiveXComponent.getPropertyAsString("ElectronicJournalData");
    }

    public static void xReport() throws RuntimeException {
        openDayIfClosed();
        Dispatch.call(epsonDispatch, "PrintXReport");
        checkErrors(true);
    }

    public static void inOut(String cashier, Double sum) throws RuntimeException {
        setCashier(cashier);
        epsonActiveXComponent.setProperty("Amount", new Variant(Math.abs(sum)));
        Dispatch.call(epsonDispatch, sum > 0 ? "CashIncome" : "CashOutcome");
        checkErrors(true);
        closeReceipt();
    }


    public static void openDrawer() throws RuntimeException {
        Dispatch.call(epsonDispatch, "OpenCashDrawer");
        checkErrors(true);
    }

    public static void registerItem(ReceiptItem item, boolean sendSKNO) throws RuntimeException {
        printLine(sendSKNO ? ("1 " + item.barcode) : item.barcode);

        boolean useBlisters = item.useBlisters && item.blisterQuantity != null;
        double price = useBlisters ? item.blisterPrice.doubleValue() : item.price.doubleValue();
        double quantity = useBlisters ? item.blisterQuantity.doubleValue() : item.quantity.doubleValue();
        logger.info(String.format("Epson Sale: name %s, price %s, quantity %s", item.name, price, quantity));
        epsonActiveXComponent.setProperty("Article", new Variant(getMultilineName(item.name)));
        epsonActiveXComponent.setProperty("Price", new Variant(price));
        epsonActiveXComponent.setProperty("Quantity", new Variant(quantity));
        epsonActiveXComponent.setProperty("QuantityUnit", new Variant(useBlisters ? "блист." : ""));
        epsonActiveXComponent.setProperty("ForcePrintSingleQuantity", new Variant(1));
        epsonActiveXComponent.setProperty("Department", new Variant(item.section != null ? item.section : (item.isGiftCard ? 3 : 0)));

        if(sendSKNO) { //подарочный сертификат должен начинаться с 99. Чтобы обойти это ограничение, можно для сертификата задавать TypeOfGoods = 4
            epsonActiveXComponent.setProperty("TypeOfGoods", new Variant(1));
            epsonActiveXComponent.setProperty("BarcodeOfGoogs", new Variant(appendZeroes(item.barcode)));
        }

        Dispatch.call(epsonDispatch, "Sale");
        checkErrors(true);

    }

    private static String appendZeroes(String barcode) {
        String result = String.valueOf(barcode);
        while(result.length() < 13)
            result = "0" + result;
        return result;
    }

    private static String getMultilineName(String name) {
        String result = "";
        while(name.length() > 40) {
            result += name.substring(0, 40) + '\n';
            name = name.substring(40);
        }
        return result + name;
    }

    public static void discountItem(ReceiptItem item, Boolean isReturn, DecimalFormat formatter) throws RuntimeException {
        if (item.discount != null && item.discount.doubleValue() != 0.0) {
            double correctionAmount = Math.abs(isReturn ? item.quantity.multiply(item.discount).doubleValue() : item.discount.doubleValue());
            logger.info(String.format("Epson Discount: quantity %s, discount %s, total discount %s", item.quantity, item.discount, correctionAmount));
            epsonActiveXComponent.setProperty("Article", new Variant(""));
            epsonActiveXComponent.setProperty("CorrectionAmount", new Variant(correctionAmount));
            Dispatch.call(epsonDispatch, item.discount.doubleValue() > 0 ? "Surcharge" : "Discount");
            checkErrors(true);
            printLine(getFiscalString("Сумма со скидкой", item.sumPos != null ? formatter.format(item.sumPos) : "0,00"));
        }
    }

    public static void printLine(String line) throws RuntimeException {
        if (line != null) {
            epsonActiveXComponent.setProperty("StringToPrint", new Variant(line.isEmpty() ? " " : line));
            Dispatch.call(epsonDispatch, "PrintLine");
            checkErrors(true);
        }
    }

    public static ReceiptInfo closeReceipt(ReceiptInstance receipt, boolean sale, Integer cardType, Integer giftCardType) throws RuntimeException {
        logger.info(String.format("Epson CompleteReceipt: sumCard %s, sumGiftCard %s, sumCash %s", receipt.sumCard, receipt.sumGiftCard, receipt.sumCash));
        Dispatch.call(epsonDispatch, "CompleteReceipt");
        checkErrors(true);
        if(receipt.sumCard != null) {
            epsonActiveXComponent.setProperty("Amount", new Variant(receipt.sumCard.doubleValue()));
            epsonActiveXComponent.setProperty("NoncashType", new Variant(cardType == null ? 0 : cardType));
            Dispatch.call(epsonDispatch, sale ? "PayNoncash" : "Repaynoncash");
            checkErrors(true);
        }
        if(receipt.sumGiftCard != null) {
            epsonActiveXComponent.setProperty("Amount", new Variant(receipt.sumGiftCard.doubleValue()));
            epsonActiveXComponent.setProperty("NonCashType", new Variant(giftCardType == null ? 1 : giftCardType));
            Dispatch.call(epsonDispatch, sale ? "PayNoncash" : "Repaynoncash");
            checkErrors(true);
        }
        if(receipt.sumCash != null) {
            epsonActiveXComponent.setProperty("Amount", new Variant(receipt.sumCash.doubleValue()));
            Dispatch.call(epsonDispatch, sale ? "PayCash" : "RepayCash");
            checkErrors(true);
        }
        ReceiptInfo receiptInfo = getReceiptInfo();
        closeReceipt();
        return receiptInfo;
    }

    public static ReceiptInfo getReceiptInfo() {
        Dispatch.call(epsonDispatch, "ReadDocumentNumber");
        checkErrors(true);
        Variant documentNumber = Dispatch.get(epsonDispatch, "DocumentNumber");
        Variant receiptNumber = Dispatch.get(epsonDispatch, "ReceiptNumber");
        Variant sessionNumber = Dispatch.get(epsonDispatch, "SessionNumber");
        return new ReceiptInfo(toInt(documentNumber), toInt(receiptNumber), toInt(sessionNumber));
    }

    public static String checkErrors(Boolean throwException) throws RuntimeException {
        int result = epsonActiveXComponent.getPropertyAsInt("ResultCode");
        if (result != 0) {
            String resultCodeDescription = epsonActiveXComponent.getPropertyAsString("ResultCodeDescription");
            if (throwException)
                throw new RuntimeException(resultCodeDescription);
            else
                return resultCodeDescription;
        } else return null;
    }

    public static PrintReceiptResult printReceipt(ReceiptInstance receipt, boolean sale, Integer cardType, Integer giftCardType, boolean sendSKNO) {
        Integer offsetBefore = getElectronicJournalReadOffset();
        openReceipt(receipt.cashier, sale ? 1 : 2);
        DecimalFormat formatter = getFormatter();
        printLine(receipt.comment);
        for (ReceiptItem item : receipt.receiptList) {
            registerItem(item, sendSKNO);
            discountItem(item, !sale, formatter);
            printLine(item.vatString);
            printLine(item.comment);

        }
        ReceiptInfo receiptInfo = closeReceipt(receipt, sale, cardType, giftCardType);
        Integer offsetAfter = getElectronicJournalReadOffset();
        return new PrintReceiptResult(receiptInfo.documentNumber, receiptInfo.receiptNumber, offsetBefore, offsetAfter - offsetBefore, receiptInfo.sessionNumber);
    }

    public static void printReceiptCopy(Integer electronicJournalReadOffset, Integer electronicJournalReadSize, Integer sessionNumber) {
        epsonActiveXComponent.setProperty("ElectronicJournalReadOffset", electronicJournalReadOffset);
        epsonActiveXComponent.setProperty("ElectronicJournalReadSize", electronicJournalReadSize);
        epsonActiveXComponent.setProperty("SessionNumber", sessionNumber);

        Dispatch.call(epsonDispatch, "ReadElectronicJournal");
        checkErrors(true);

        String electronicJournalData = epsonActiveXComponent.getPropertyAsString("ElectronicJournalData");

        try {
            //открытие нефискального документа
            openReceipt(null, 0);
            printLine(" ");
            printLine("=============== КОПИЯ ЧЕКА ==============="); //центрируем: по 16 символов слева и справа (42 всего)
            printLine(" ");
            for (String line : electronicJournalData.split("\r\n"))
                printLine(line.isEmpty() ? " " : line);
        } catch (Exception e) {
            System.out.println(e);
        } finally {
            closeReceipt();
        }
    }

    private static void setCashier(String cashier) {
        if (cashier != null) {
            openDayIfClosed();
            epsonActiveXComponent.setProperty("CashierLogin", new Variant(cashier));
        }
    }

    public static void openDayIfClosed() {
        if (!isZReportOpen()) {
            Dispatch.call(epsonDispatch, "OpenDay");
            checkErrors(true);
        }
    }

    public static boolean isZReportOpen() {
        Variant stateDayOpen = epsonActiveXComponent.getProperty("StateDayOpen");
        return toInt(stateDayOpen) == 1;
    }

    public static Date getDateTime() {
       return epsonActiveXComponent.getProperty("DateTime").getJavaDate();
    }

    public static void synchronizeDateTime() {
        epsonActiveXComponent.setProperty("DateTime", new Variant(Calendar.getInstance().getTime()));
    }

    public static String checkSKNO() {
        try {
            Dispatch.call(epsonDispatch, "ReadTaxAuthoritiesControlUnitStatus");
        } catch (Exception e) {
            return "отсутствует";
        }
        checkErrors(true);
        return "OK";//epsonActiveXComponent.getPropertyAsString("TaxAuthoritiesControlUnitStatus");
    }

    private static Integer toInt(Variant variant) {
        return variant == null ? null : variant.toInt(); //getInt выдаёт ошибку для variant type = 18, 19
    }

    private static String getFiscalString(String prefix, String value) {
        while(value.length() < 40 - 1 - prefix.length()) //в комменте 40 символов
            value = " " + value;
        return prefix + " " + value;
    }

    private static DecimalFormat getFormatter() {
        DecimalFormat formatter = new DecimalFormat("#,###.##");
        formatter.setMinimumFractionDigits(2);
        DecimalFormatSymbols symbols = formatter.getDecimalFormatSymbols();
        symbols.setGroupingSeparator('`');
        formatter.setDecimalFormatSymbols(symbols);
        return formatter;
    }

    private static class ReceiptInfo {
        int documentNumber; //сквозной номер
        int receiptNumber; //номер в сессии
        int sessionNumber; //номер сессии

        public ReceiptInfo(int documentNumber, int receiptNumber, int sessionNumber) {
            this.documentNumber = documentNumber;
            this.receiptNumber = receiptNumber;
            this.sessionNumber = sessionNumber;
        }
    }
}

