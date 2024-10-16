package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.ComFailException;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import static lsfusion.erp.ERPLoggers.cashRegisterlogger;

public class FiscalEpson {

    static ActiveXComponent epsonActiveXComponent;
    static Dispatch epsonDispatch;

    static void init() {
        try {
            if(epsonDispatch == null) {
                epsonActiveXComponent = new ActiveXComponent("UniFiscalOcx.DrvUniFR");
                epsonDispatch = epsonActiveXComponent.getObject();
            }
        } catch (UnsatisfiedLinkError e) {
            System.out.println(e);
        }
    }

    public static void openPort(int comPort, int baudRate) throws RuntimeException {

        closePort();

        epsonActiveXComponent.setProperty("ComPort", new Variant(comPort));
        epsonActiveXComponent.setProperty("BaudRate", new Variant(baudRate));
        cashRegisterlogger.info(String.format("Epson Connect comPort %s, baudRate %s", comPort, baudRate));
        Dispatch.call(epsonDispatch, "Connect");
        checkErrors(true);
    }

    public static void closePort() throws RuntimeException {
        if(epsonDispatch != null) {
            cashRegisterlogger.info("Epson Disconnect");
            Dispatch.call(epsonDispatch, "Disconnect");
        }
    }

    public static void openReceipt(String cashier, int type) throws RuntimeException {
        setCashier(cashier);
        cashRegisterlogger.info("Epson OpenReceipt type " + type);
        epsonActiveXComponent.setProperty("ReceiptType", new Variant(type));
        Dispatch.call(epsonDispatch, "OpenReceipt");
        checkErrors(true);
    }

    public static Integer getElectronicJournalReadOffset() throws RuntimeException {
        cashRegisterlogger.info("Epson ElectronicJournalReadOffset");
        return toInt(epsonActiveXComponent.getProperty("ElectronicJournalReadOffset"));
    }

    public static void closeReceipt() {
        boolean checkErrors = true;
        try {
            cashRegisterlogger.info("Epson CloseReceipt started");
            long time = System.currentTimeMillis();
            Dispatch.call(epsonDispatch, "CloseReceipt");
            cashRegisterlogger.info(String.format("Epson CloseReceipt finished: %s ms", (System.currentTimeMillis() - time)));
        } catch (ComFailException e) {
            if (e.getMessage() != null && e.getMessage().contains("ФБ: таймаут связи с СКНО")) {
                checkErrors = false;
                cashRegisterlogger.info("Epson CloseReceipt error: ФБ: таймаут связи с СКНО");
            } else {
                cashRegisterlogger.error("Epson CloseReceipt error: ", e);
                throw e;
            }
        } catch (Exception e) {
            cashRegisterlogger.error("Epson CloseReceipt error: ", e);
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

    public static void resetReceipt(String cashier, Integer documentNumberReceipt, BigDecimal totalSum, BigDecimal sumCash, BigDecimal sumCard, BigDecimal sumGiftCard,
                                    Integer cardType, Integer giftCardType, boolean version116, ReceiptInstance receipt, boolean sendSKNO, boolean resetTypeOfGoods) throws RuntimeException {
        boolean sale = totalSum.doubleValue() > 0;
        epsonActiveXComponent.setProperty("CancellationDocumentNumber", new Variant(documentNumberReceipt));
        if (!version116)
            epsonActiveXComponent.setProperty("CancellationAmount", new Variant(totalSum));
        openReceipt(cashier, 5);

        if (version116) { // анулируем документ построчно
            for (ReceiptItem item : receipt.receiptList) {
                registerItem(item, sendSKNO, resetTypeOfGoods, true, 3);
            }
        }

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
        Integer zReportNumber = getReceiptInfo(false).sessionNumber;
        Dispatch.call(epsonDispatch, "PrintZReport");
        checkErrors(true);
        return zReportNumber;
    }

    public static void electronicJournal() throws RuntimeException {
        openDayIfClosed();
        Dispatch.call(epsonDispatch, "PrintElectronicJournal");
        checkErrors(true);
    }

    public static String readElectronicJournal(Integer offsetBefore, boolean version116) throws RuntimeException {
        Integer offset = getElectronicJournalReadOffset(); // возвращает ElectronicJournalReadOffset
        checkErrors(true);

        if (version116) {
            offsetBefore = 0;
        }

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

    public static void registerItem(ReceiptItem item, boolean sendSKNO, boolean resetTypeOfGoods, boolean version116, int operation) throws RuntimeException {
        if (!version116)
            printLine(sendSKNO && item.isGiftCard ? ("1 " + item.barcode) : item.barcode);

        boolean isGiftCardOrComission = item.isGiftCard || item.isCommission;

        boolean useBlisters = item.useBlisters && item.blisterQuantity != null;
        Integer department = item.section != null ? item.section : 0;
        double price = useBlisters ? item.blisterPrice.doubleValue() : item.price.doubleValue();
        double quantity = useBlisters ? item.blisterQuantity.doubleValue() : item.quantity.doubleValue();
        cashRegisterlogger.info(String.format("Epson Sale: department %s, name %s, price %s, quantity %s", department, item.name, price, quantity));
        epsonActiveXComponent.setProperty("Article", new Variant(getMultilineName(item.name)));
        epsonActiveXComponent.setProperty("Price", new Variant(price));
        epsonActiveXComponent.setProperty("Quantity", new Variant(quantity));
        epsonActiveXComponent.setProperty("QuantityUnit", new Variant(useBlisters ? "блист." : ""));
        epsonActiveXComponent.setProperty("ForcePrintSingleQuantity", new Variant(1));

        if (version116) {

            // соответствует не маркированному товару без GTIN (barcode)
            Integer typeOfGoods = 0;                // Тип маркировки
            String barcodeOfGoods = "";             // GTIN
            String firstMarkingOfGoods = "";        // СИ (средство индентификации ЕАЭС), для ERP только СИ
            String secondMarkingOfGoods = "";       // УКЗ (унифицированный контрольный знак РБ), для ERP не используется

            if (isGiftCardOrComission) { // авансовый платеж - продажа подарочного сертификата
                epsonActiveXComponent.setProperty("Department", department);
                typeOfGoods = 4;
            } else if (item.skuType == 3) { // услуга
                epsonActiveXComponent.setProperty("Department", department);
                typeOfGoods = 3;
            } else if (item.skuType == 1 && item.barcode != null && item.idLot == null) { // простой товар с GTIN
                // исходим из того что, item.barcode - это GTIN
                Integer tg = getTypeOfGoods(item.barcode); // 0 - не GTIN, 1 - до 13 символов, 16 - 14 символов
                if (tg > 0) {
                    typeOfGoods = tg;
                    barcodeOfGoods = item.barcode;
                }
                epsonActiveXComponent.setProperty("Department",item.numberVAT);
            } else if (item.skuType == 1 && item.barcode != null && item.idLot != null) { // GTIN + СИ + Криптохвост
                // из idLot выделяем GTIN - 14 символов и удаляем лидирующии нули
                String gtin = item.idLot.substring(2,16).replaceFirst("^0+(?!$)", "");
                typeOfGoods = 20; // Товар имеет одну маркировку (СКАН-1)
                barcodeOfGoods = gtin;
                if (item.tailLot.charAt(0)!=0x1d) {
                    firstMarkingOfGoods = item.idLot + 0x1d + item.tailLot;
                } else {
                    firstMarkingOfGoods = item.idLot + item.tailLot;
                }
                epsonActiveXComponent.setProperty("Department",item.numberVAT);
            }

            epsonActiveXComponent.setProperty("TypeOfGoods", new Variant( typeOfGoods));
            epsonActiveXComponent.setProperty("BarcodeOfGoogs", new Variant(barcodeOfGoods));
            epsonActiveXComponent.setProperty("FirstMarkingOfGoods", new Variant(firstMarkingOfGoods));
            epsonActiveXComponent.setProperty("SecondMarkingOfGoods", new Variant(secondMarkingOfGoods));

            // обработка скидок
            BigDecimal dsc = safeAdd(item.discount,item.bonusPaid);
            if (dsc != null && dsc.compareTo(BigDecimal.ZERO) > 0) {
                if (operation == 1 || operation == 3) { // продажа, аннулирование
                    cashRegisterlogger.info(String.format("Epson Discount: quantity %s, discount %s, bonusPaid %s, total discount %s", item.quantity, item.discount, item.bonusPaid, dsc));
                    epsonActiveXComponent.setProperty("CorrectionAmount", new Variant(dsc));
                    Dispatch.call(epsonDispatch, "DiscountSale");
                } else if (operation == 2) { // возврат
                    // скидки  в чеке запрещены, поэтому продаем, как 1 единицу по цене суммы,
                    // в комментарии указываем количество, цену, размер скидки
                    // отнимать скидку от цены плохо, могут возникнуть проблемы с округлением
                    epsonActiveXComponent.setProperty("Quantity", new Variant(1));
                    epsonActiveXComponent.setProperty("Price", new Variant(item.sumPos));
                    Dispatch.call(epsonDispatch, "Sale");
                    printLine(String.format("возврат %s единиц товара\nпо цене %s с учетом скидки %s",item.quantity.stripTrailingZeros(),item.price.stripTrailingZeros(),dsc.stripTrailingZeros()));
                }
            } else {
                Dispatch.call(epsonDispatch, "Sale");
            }

        } else {

            epsonActiveXComponent.setProperty("Department", department);
            if(sendSKNO && isGiftCardOrComission) { //подарочный сертификат должен начинаться с 99. Чтобы обойти это ограничение, можно для сертификата задавать TypeOfGoods = 4
                epsonActiveXComponent.setProperty("TypeOfGoods", new Variant(1));
                epsonActiveXComponent.setProperty("BarcodeOfGoogs", new Variant(appendZeroes(item.barcode)));
            }

            Dispatch.call(epsonDispatch, "Sale");

            if (sendSKNO && isGiftCardOrComission && resetTypeOfGoods) {
                epsonActiveXComponent.setProperty("TypeOfGoods", new Variant(0));
                epsonActiveXComponent.setProperty("BarcodeOfGoogs", new Variant(appendZeroes("")));
            }

        }

        checkErrors(true);

    }

    private static Integer getTypeOfGoods(String code) {
        if (code == null)
            return 0;
        code = code.trim();
        if (code.isEmpty())
            return 0;
        if (!code.matches("-?\\d+(\\.\\d+)?"))
            return 0; // если не цифры
        if (code.length() == 14) {
            return 16;
        } else {
            return 1;
        }
    }


    private static BigDecimal safeAdd(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else return (operand1 == null ? operand2 : (operand2 == null ? operand1 : operand1.add(operand2)));
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
            cashRegisterlogger.info(String.format("Epson Discount: quantity %s, discount %s, total discount %s", item.quantity, item.discount, correctionAmount));
            epsonActiveXComponent.setProperty("Article", new Variant(""));
            epsonActiveXComponent.setProperty("CorrectionAmount", new Variant(correctionAmount));
            Dispatch.call(epsonDispatch, item.discount.doubleValue() > 0 ? "Surcharge" : "Discount");
            checkErrors(true);
            printLine(getFiscalString("Сумма со скидкой", item.sumPos != null ? formatter.format(item.sumPos) : "0,00"));
        }
    }

    public static void printLine(String line) throws RuntimeException {
        if (line != null) {
            cashRegisterlogger.info("Epson PrintLine " + line);
            epsonActiveXComponent.setProperty("StringToPrint", new Variant(line.isEmpty() ? " " : line));
            Dispatch.call(epsonDispatch, "PrintLine");
            checkErrors(true);
        }
    }

    public static ReceiptInfo closeReceipt(ReceiptInstance receipt, boolean sale, Integer cardType, Integer giftCardType, boolean version116) throws RuntimeException {
        cashRegisterlogger.info(String.format("Epson CompleteReceipt: sumCard %s, sumGiftCard %s, sumCash %s", receipt.sumCard, receipt.sumGiftCard, receipt.sumCash));
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
        for (Map.Entry<Integer, BigDecimal> payment : receipt.paymentSumMap.entrySet()) {
            epsonActiveXComponent.setProperty("Amount", new Variant(payment.getValue()));
            epsonActiveXComponent.setProperty("NoncashType", new Variant(payment.getKey()));
            Dispatch.call(epsonDispatch, sale ? "PayNoncash" : "Repaynoncash");
            checkErrors(true);
        }

        ReceiptInfo receiptInfo = getReceiptInfo(version116);
        closeReceipt();
        return receiptInfo;
    }

    public static ReceiptInfo getReceiptInfo(boolean version116) {
        Dispatch.call(epsonDispatch, "ReadDocumentNumber");
        checkErrors(true);
        Variant sessionNumber = Dispatch.get(epsonDispatch, "SessionNumber");
        Variant documentNumber;
        Variant receiptNumber;
        if (version116) {
            receiptNumber = Dispatch.get(epsonDispatch, "DocumentThroughNumber");
            documentNumber = receiptNumber;
        } else {
            documentNumber = Dispatch.get(epsonDispatch, "DocumentNumber");
            receiptNumber = Dispatch.get(epsonDispatch, "ReceiptNumber");;
        }
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

    public static PrintReceiptResult printReceipt(ReceiptInstance receipt, boolean sale, Integer cardType, Integer giftCardType, boolean sendSKNO, boolean resetTypeOfGoods, boolean version116) {
        Integer offsetBefore = getElectronicJournalReadOffset();
        openReceipt(receipt.cashier, sale ? 1 : 2);
        DecimalFormat formatter = getFormatter();
        printLine(receipt.comment);
        for (ReceiptItem item : receipt.receiptList) {
            registerItem(item, sendSKNO, resetTypeOfGoods, version116, sale ? 1 : 2);
            if (!version116) {
                discountItem(item, !sale, formatter);
                printLine(item.vatString);
                printLine(item.comment);
            }
        }
        ReceiptInfo receiptInfo = closeReceipt(receipt, sale, cardType, giftCardType, version116);
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

    public static BigDecimal cashSum(String currencyCode) throws RuntimeException {
        epsonActiveXComponent.setProperty("RequestSession",new Variant(0));
        epsonActiveXComponent.setProperty("RequestCurrency",currencyCode);
        Dispatch.call(epsonDispatch,"RequestSessionCollectedAmounts");
        checkErrors(true);
        BigDecimal dResult = BigDecimal.valueOf(Dispatch.get(epsonDispatch,"CollectedAmount").getCurrency().longValue()).divide(BigDecimal.valueOf(10000), 2, RoundingMode.HALF_UP);
        return dResult;
    }
}

