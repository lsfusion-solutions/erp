package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;

import java.util.HashMap;
import java.util.Map;

public class FiscalShtrih {

    static String password = "30000";
    static ActiveXComponent shtrihActiveXComponent;
    static Dispatch shtrihDispatch;

    static void init() {
        try {
            shtrihActiveXComponent = new ActiveXComponent("AddIn.DrvFR");
            shtrihDispatch = shtrihActiveXComponent.getObject();
        } catch (UnsatisfiedLinkError e) {
            System.out.println(e.toString());
        }
    }

    private static Map<Integer, Integer> baudRateMap = new HashMap<Integer, Integer>() {
        {
            put(2400, 0);
            put(4800, 1);
            put(9600, 2);
            put(19200, 3);
            put(38400, 4);
            put(57600, 5);
            put(115200, 6);
        }
    };

    public static void openPort(int comPort, int baudRate) throws RuntimeException {

        closePort();

        int codeBaudRate = baudRateMap.containsKey(baudRate) ? baudRateMap.get(baudRate) : 6;

        shtrihActiveXComponent.setProperty("Password", new Variant(password));
        shtrihActiveXComponent.setProperty("ComNumber", new Variant(comPort));
        shtrihActiveXComponent.setProperty("BaudRate", new Variant(codeBaudRate));
        shtrihActiveXComponent.setProperty("TimeOut", new Variant(50));

        Variant result = Dispatch.call(shtrihDispatch, "Connect");
        checkErrors(result, true);
    }

    public static void closePort() throws RuntimeException {
        Dispatch.call(shtrihDispatch, "Disconnect");
    }

    public static void openReceipt(boolean sale) throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));
        shtrihActiveXComponent.setProperty("CheckType", new Variant(sale ? 0 : 2));

        Variant result = Dispatch.call(shtrihDispatch, "OpenCheck");
        checkErrors(result, true);
    }

    public static boolean cancelReceipt(boolean throwException) throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));

        Variant result = Dispatch.call(shtrihDispatch, "CancelCheck");
        return checkErrors(result, throwException);
    }

    public static boolean cutReceipt() throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));

        Variant result = Dispatch.call(shtrihDispatch, "CutCheck");
        return checkErrors(result, true);
    }

    public static boolean printFiscalText(String msg) throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));
        shtrihActiveXComponent.setProperty("UseReceiptRibbon", new Variant(true));
        shtrihActiveXComponent.setProperty("UseJournalRibbon", new Variant(true));
        shtrihActiveXComponent.setProperty("StringForPrinting", new Variant(msg));

        Variant result = Dispatch.call(shtrihDispatch, "PrintString");

        shtrihActiveXComponent.setProperty("StringForPrinting", new Variant(null));
        return checkErrors(result, true);
    }

    public static void zReport() throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));

        Variant result = Dispatch.call(shtrihDispatch, "PrintReportWithCleaning");
        checkErrors(result, true);
    }

    public static void xReport() throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));

        Variant result = Dispatch.call(shtrihDispatch, "PrintReportWithoutCleaning");
        checkErrors(result, true);
    }

    public static void advancePaper() throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));
        shtrihActiveXComponent.setProperty("StringQuantity", new Variant(3));
        shtrihActiveXComponent.setProperty("UseSlipDocument", new Variant(true));
        shtrihActiveXComponent.setProperty("UseReceiptRibbon", new Variant(true));
        shtrihActiveXComponent.setProperty("UseJournalRibbon", new Variant(true));

        Variant result = Dispatch.call(shtrihDispatch, "FeedDocument");
        checkErrors(result, true);
    }

    public static boolean inOut(Long sum) throws RuntimeException {

        shtrihActiveXComponent.setProperty("Password", new Variant(password));
        shtrihActiveXComponent.setProperty("Summ1", new Variant(Math.abs(sum)));

        Variant result = Dispatch.call(shtrihDispatch, sum > 0 ? "CashIncome" : "CashOutcome");
        return checkErrors(result, true);
    }


    public static boolean openDrawer() throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));
        shtrihActiveXComponent.setProperty("DrawerNumber", new Variant(0));

        Variant result = Dispatch.call(shtrihDispatch, "OpenDrawer");
        return checkErrors(result, true);
    }

    public static void registerItem(boolean sale, ReceiptItem item) throws RuntimeException {

        printFiscalText(item.name.substring(0, Math.min(item.name.length(), 40)));

        shtrihActiveXComponent.setProperty("Password", new Variant(password));
        shtrihActiveXComponent.setProperty("Quantity", new Variant(item.quantity.doubleValue()));
        shtrihActiveXComponent.setProperty("Price", new Variant(item.price.doubleValue()));
        shtrihActiveXComponent.setProperty("Department", new Variant(item.isGiftCard ? 3 : 1));

        Variant result = Dispatch.call(shtrihDispatch, sale ? "Sale" : "ReturnSale");
        checkErrors(result, true);

    }

    public static void discountItem(ReceiptItem item, Boolean isReturn) throws RuntimeException {
        if (item.discount != null) {
            shtrihActiveXComponent.setProperty("Password", new Variant(password));
            shtrihActiveXComponent.setProperty("Summ1", new Variant(Math.abs(isReturn ? item.quantity.multiply(item.discount).doubleValue() : item.discount.doubleValue())));

            Variant result = Dispatch.call(shtrihDispatch, item.discount.doubleValue() > 0 ? "Charge" : "Discount");
            checkErrors(result, true);
        }
    }

    public static void closeReceipt(ReceiptInstance receipt) throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));
        shtrihActiveXComponent.setProperty("Summ1", new Variant(receipt.sumCash));
        shtrihActiveXComponent.setProperty("Summ2", new Variant(receipt.sumCard));
        shtrihActiveXComponent.setProperty("Summ3", new Variant(receipt.sumGiftCard));

        Variant result = Dispatch.call(shtrihDispatch, "CloseCheck");
        checkErrors(result, true);
    }

    public static void beep() throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));

        Variant result = Dispatch.call(shtrihDispatch, "Beep");
        checkErrors(result, true);
    }

    public static void continuePrint() throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));

        Variant result = Dispatch.call(shtrihDispatch, "ContinuePrint");
        checkErrors(result, true);
    }

    public static boolean checkErrors(Variant result, Boolean throwException) throws RuntimeException {
        if (!result.toString().equals("0")) {
            String resultCodeDescription = shtrihActiveXComponent.getPropertyAsString("resultCodeDescription");
            if (throwException)
                throw new RuntimeException(resultCodeDescription);
            else return false;
        } else return checkRibbonState();
    }

    public static boolean checkRibbonState() {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));
        int result = 1;
        while (result != 0) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
            Dispatch.call(shtrihDispatch, "GetShortECRStatus");
            result = shtrihActiveXComponent.getPropertyAsInt("ECRAdvancedMode");
            if (result == 3)
                continuePrint();
        }

        return true;
    }

    public static void printReceipt(ReceiptInstance receipt, boolean sale) {

        openReceipt(sale);

        for (ReceiptItem item : (receipt.receiptList)) {
            registerItem(sale, item);
            discountItem(item, !sale);
        }

        closeReceipt(receipt);
    }
}

