package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;

import java.math.BigDecimal;

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

    public static boolean closeReceipt() {
        Dispatch.call(epsonDispatch, "CloseReceipt");
        return checkErrors(true);
    }

    public static boolean cancelReceipt(boolean throwException) throws RuntimeException {
        if(epsonDispatch != null) {
            Dispatch.call(epsonDispatch, "CancelPrint");
            return checkErrors(throwException);
        } return true;
    }

    public static void resetReceipt(String cashier, Integer numberReceipt, BigDecimal totalSum, BigDecimal sumCash, BigDecimal sumCard, BigDecimal sumGiftCard) throws RuntimeException {
        boolean sale = totalSum.doubleValue() > 0;
        epsonActiveXComponent.setProperty("CancellationDocumentNumber", new Variant(numberReceipt));
        epsonActiveXComponent.setProperty("CancellationAmount", new Variant(totalSum));
        openReceipt(cashier, 5);

        Dispatch.call(epsonDispatch, "CompleteReceipt");
        checkErrors(true);
        if(sumCard != null) {
            epsonActiveXComponent.setProperty("Amount", new Variant(sumCard.doubleValue()));
            epsonActiveXComponent.setProperty("NoncashType", new Variant(0));
            Dispatch.call(epsonDispatch, sale ? "PayNoncash" : "Repaynoncash");
            checkErrors(true);
        }
        if(sumGiftCard != null) {
            epsonActiveXComponent.setProperty("Amount", new Variant(sumGiftCard.doubleValue()));
            epsonActiveXComponent.setProperty("NonCashType", new Variant(1));
            Dispatch.call(epsonDispatch, sale ? "PayNoncash" : "Repaynoncash");
            checkErrors(true);
        }
        if(sumCash != null) {
            epsonActiveXComponent.setProperty("Amount", new Variant(sumCash.doubleValue()));
            Dispatch.call(epsonDispatch, sale ? "PayCash" : "RepayCash");
            checkErrors(true);
        }
        closeReceipt();
    }

    public static void zReport() throws RuntimeException {
        Dispatch.call(epsonDispatch, "PrintZReport");
        checkErrors(true);
    }

    public static void electronicJournal() throws RuntimeException {
        Dispatch.call(epsonDispatch, "PrintElectronicJournal");
        checkErrors(true);
    }

    public static void xReport() throws RuntimeException {
        Dispatch.call(epsonDispatch, "PrintXReport");
        checkErrors(true);
    }

    public static boolean inOut(String cashier, Double sum) throws RuntimeException {
        setCashier(cashier);
        epsonActiveXComponent.setProperty("Amount", new Variant(Math.abs(sum)));
        Dispatch.call(epsonDispatch, sum > 0 ? "CashIncome" : "CashOutcome");
        checkErrors(true);
        return closeReceipt();
    }


    public static void openDrawer() throws RuntimeException {
        Dispatch.call(epsonDispatch, "OpenCashDrawer");
        checkErrors(true);
    }

    public static void registerItem(ReceiptItem item) throws RuntimeException {
        epsonActiveXComponent.setProperty("Article", new Variant(item.name));
        epsonActiveXComponent.setProperty("Price", new Variant(item.price.doubleValue()));
        epsonActiveXComponent.setProperty("Quantity", new Variant(item.quantity.doubleValue()));
        epsonActiveXComponent.setProperty("Department", new Variant(item.section != null ? item.section : (item.isGiftCard ? 3 : 0)));
        Dispatch.call(epsonDispatch, "Sale");
        checkErrors(true);

    }

    public static void discountItem(ReceiptItem item, Boolean isReturn) throws RuntimeException {
        if (item.discount != null && item.discount.doubleValue() != 0.0) {
            epsonActiveXComponent.setProperty("CorrectionAmount", new Variant(Math.abs(isReturn ? item.quantity.multiply(item.discount).doubleValue() : item.discount.doubleValue())));
            Dispatch.call(epsonDispatch, item.discount.doubleValue() > 0 ? "Surcharge" : "Discount");
            checkErrors(true);
        }
    }

    public static void printLine(String line) throws RuntimeException {
        if (line != null) {
            epsonActiveXComponent.setProperty("StringToPrint", new Variant(line));
            Dispatch.call(epsonDispatch, "PrintLine");
            checkErrors(true);
        }
    }

    public static Integer closeReceipt(ReceiptInstance receipt, boolean sale) throws RuntimeException {
        Dispatch.call(epsonDispatch, "CompleteReceipt");
        checkErrors(true);
        if(receipt.sumCard != null) {
            epsonActiveXComponent.setProperty("Amount", new Variant(receipt.sumCard.doubleValue()));
            epsonActiveXComponent.setProperty("NoncashType", new Variant(0));
            Dispatch.call(epsonDispatch, sale ? "PayNoncash" : "Repaynoncash");
            checkErrors(true);
        }
        if(receipt.sumGiftCard != null) {
            epsonActiveXComponent.setProperty("Amount", new Variant(receipt.sumGiftCard.doubleValue()));
            epsonActiveXComponent.setProperty("NonCashType", new Variant(1));
            Dispatch.call(epsonDispatch, sale ? "PayNoncash" : "Repaynoncash");
            checkErrors(true);
        }
        if(receipt.sumCash != null) {
            epsonActiveXComponent.setProperty("Amount", new Variant(receipt.sumCash.doubleValue()));
            Dispatch.call(epsonDispatch, sale ? "PayCash" : "RepayCash");
            checkErrors(true);
        }
        Integer receiptNumber = getReceiptNumber();
        closeReceipt();
        return receiptNumber;
    }

    public static Integer getReceiptNumber() {
        Dispatch.call(epsonDispatch, "ReadDocumentNumber");
        checkErrors(true);
        Variant receiptNumber = Dispatch.get(epsonDispatch, "ReceiptNumber");
        return receiptNumber == null ? null : receiptNumber.toInt(); //getInt выдаёт ошибку
    }

    public static boolean checkErrors(Boolean throwException) throws RuntimeException {
        int result = epsonActiveXComponent.getPropertyAsInt("ResultCode");
        if (result != 0) {
            String resultCodeDescription = epsonActiveXComponent.getPropertyAsString("ResultCodeDescription");
            if (throwException)
                throw new RuntimeException(resultCodeDescription);
            else return false;
        } else return true;
    }

    public static Integer printReceipt(ReceiptInstance receipt, boolean sale) {
        openReceipt(receipt.cashier, sale ? 1 : 2);
        for (ReceiptItem item : receipt.receiptList) {
            registerItem(item);
            discountItem(item, !sale);
            printLine(item.vatString);

        }
        return closeReceipt(receipt, sale);
    }

    private static void setCashier(String cashier) {
        if (cashier != null) {
            Variant stateDayOpen = epsonActiveXComponent.getProperty("StateDayOpen");
            if (stateDayOpen != null && stateDayOpen.toInt() == 0) { //getInt выдаёт ошибку
                Dispatch.call(epsonDispatch, "OpenDay");
                checkErrors(true);

            }
            epsonActiveXComponent.setProperty("CashierLogin", new Variant(cashier));
        }
    }
}

