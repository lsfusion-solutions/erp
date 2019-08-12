package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;

import java.util.HashMap;
import java.util.Map;

public class FiscalShtrih {

    static int systemPassword = 30000;
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

    public static void openPort(int password, int comPort, int baudRate) throws RuntimeException {

        closePort();

        int codeBaudRate = baudRateMap.getOrDefault(baudRate, 6);

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

    public static void openReceipt(int password, boolean sale) throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));
        shtrihActiveXComponent.setProperty("CheckType", new Variant(sale ? 0 : 2));

        Variant result = Dispatch.call(shtrihDispatch, "OpenCheck");
        checkErrors(result, true);
    }

    public static boolean cancelReceipt(int password, boolean throwException) throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));

        Variant result = Dispatch.call(shtrihDispatch, "CancelCheck");
        return checkErrors(result, throwException);
    }

    public static boolean cutReceipt(int password) throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));

        Variant result = Dispatch.call(shtrihDispatch, "CutCheck");
        return checkErrors(result, true);
    }

    public static boolean printFiscalText(int password, String msg) throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));
        shtrihActiveXComponent.setProperty("UseReceiptRibbon", new Variant(true));
        shtrihActiveXComponent.setProperty("UseJournalRibbon", new Variant(true));
        shtrihActiveXComponent.setProperty("StringForPrinting", new Variant(msg));

        Variant result = Dispatch.call(shtrihDispatch, "PrintString");

        shtrihActiveXComponent.setProperty("StringForPrinting", new Variant(null));
        return checkErrors(result, true);
    }

    public static void zReport() throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(systemPassword));

        Variant result = Dispatch.call(shtrihDispatch, "PrintReportWithCleaning");
        checkErrors(result, true);
    }

    public static void xReport() throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(systemPassword));

        Variant result = Dispatch.call(shtrihDispatch, "PrintReportWithoutCleaning");
        checkErrors(result, true);
    }

    public static void advancePaper(int password) throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));
        shtrihActiveXComponent.setProperty("StringQuantity", new Variant(3));
        shtrihActiveXComponent.setProperty("UseSlipDocument", new Variant(true));
        shtrihActiveXComponent.setProperty("UseReceiptRibbon", new Variant(true));
        shtrihActiveXComponent.setProperty("UseJournalRibbon", new Variant(true));

        Variant result = Dispatch.call(shtrihDispatch, "FeedDocument");
        checkErrors(result, true);
    }

    public static boolean inOut(int password, Long sum) throws RuntimeException {

        shtrihActiveXComponent.setProperty("Password", new Variant(password));
        shtrihActiveXComponent.setProperty("Summ1", new Variant(Math.abs(sum)));

        Variant result = Dispatch.call(shtrihDispatch, sum > 0 ? "CashIncome" : "CashOutcome");
        return checkErrors(result, true);
    }


    public static boolean openDrawer(int password) throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));
        shtrihActiveXComponent.setProperty("DrawerNumber", new Variant(0));

        Variant result = Dispatch.call(shtrihDispatch, "OpenDrawer");
        return checkErrors(result, true);
    }

    public static void registerItem(int password, boolean sale, ReceiptItem item, Integer taxRange) throws RuntimeException {

        printFiscalText(password, item.name.substring(0, Math.min(item.name.length(), 40)));

        shtrihActiveXComponent.setProperty("Password", new Variant(password));
        shtrihActiveXComponent.setProperty("Quantity", new Variant(item.quantity.doubleValue()));
        shtrihActiveXComponent.setProperty("Price", new Variant(item.price.doubleValue()));
        shtrihActiveXComponent.setProperty("Department", new Variant(item.isGiftCard ? 3 : 1));
        shtrihActiveXComponent.setProperty("Tax1", new Variant(taxRange));

        Variant result = Dispatch.call(shtrihDispatch, sale ? "Sale" : "ReturnSale");
        checkErrors(result, true);

    }

    public static void discountItem(int password, ReceiptItem item, Integer taxRange, Boolean isReturn) throws RuntimeException {
        if (item.discount != null) {
            shtrihActiveXComponent.setProperty("Password", new Variant(password));
            shtrihActiveXComponent.setProperty("Summ1", new Variant(Math.abs(isReturn ? item.quantity.multiply(item.discount).doubleValue() : item.discount.doubleValue())));
            shtrihActiveXComponent.setProperty("Tax1", new Variant(taxRange));

            Variant result = Dispatch.call(shtrihDispatch, item.discount.doubleValue() > 0 ? "Charge" : "Discount");
            checkErrors(result, true);
        }
    }

    public static void closeReceipt(int password, ReceiptInstance receipt) throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));
        shtrihActiveXComponent.setProperty("Summ1", new Variant(receipt.sumCash));
        shtrihActiveXComponent.setProperty("Summ2", new Variant(receipt.sumCard));
        shtrihActiveXComponent.setProperty("Summ3", new Variant(receipt.sumGiftCard));
        shtrihActiveXComponent.setProperty("Tax1", new Variant(0));

        Variant result = Dispatch.call(shtrihDispatch, "CloseCheck");
        checkErrors(result, true);
    }

    public static void beep(int password) throws RuntimeException {
        shtrihActiveXComponent.setProperty("Password", new Variant(password));

        Variant result = Dispatch.call(shtrihDispatch, "Beep");
        checkErrors(result, true);
    }

    public static void continuePrint(int password) throws RuntimeException {
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
        } else return checkRibbonState(systemPassword);
    }

    public static boolean checkRibbonState(int password) {
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
                continuePrint(password);
        }

        return true;
    }

    public static void printReceipt(int password, ReceiptInstance receipt, boolean sale) {

        Map<Integer, Integer> taxRanges = getTaxRanges();

        openReceipt(password, sale);

        for (ReceiptItem item : (receipt.receiptList)) {
            Integer taxRange = (item.valueVAT != null && taxRanges.containsKey(item.valueVAT.intValue())) ? taxRanges.get(item.valueVAT.intValue()) : 0;
            //System.out.println("Sale: " + item.name + " VAT= " + item.valueVAT + " - " + taxRange);   //лог, убрать
            registerItem(password, sale, item, taxRange);
            discountItem(password, item, taxRange, !sale);
        }

        closeReceipt(password, receipt);
    }

    public static void setOperatorName(UpdateDataOperator operator) {
        if (operator.operatorNumber <= 28) {

            prepareTableField(2, 2);

            shtrihActiveXComponent.setProperty("Password", new Variant(systemPassword));
            shtrihActiveXComponent.setProperty("TableNumber", new Variant(2));
            shtrihActiveXComponent.setProperty("RowNumber", new Variant(operator.operatorNumber));
            shtrihActiveXComponent.setProperty("FieldNumber", new Variant(2));  //Имя кассира
            shtrihActiveXComponent.setProperty("ValueOfFieldString", new Variant(operator.operatorName));
            Variant result = Dispatch.call(shtrihDispatch, "WriteTable");
            checkErrors(result, true);

            shtrihActiveXComponent.setProperty("FieldNumber", new Variant(1));  //Пароль кассира
            shtrihActiveXComponent.setProperty("ValueOfFieldInteger", new Variant(operator.operatorPassword));
            result = Dispatch.call(shtrihDispatch, "WriteTable");
            checkErrors(result, true);
        }
    }

    public static void setTaxRate(UpdateDataTaxRate taxRate) {

        //System.out.println("Tax # " + taxRate.taxRateNumber + " - " + taxRate.taxRateValue);   //лог, убрать

        prepareTableField(6, 1);

        shtrihActiveXComponent.setProperty("Password", systemPassword);
        shtrihActiveXComponent.setProperty("TableNumber", new Variant(6));
        shtrihActiveXComponent.setProperty("RowNumber", new Variant(taxRate.taxRateNumber));
        shtrihActiveXComponent.setProperty("FieldNumber", new Variant(1));  //Имя налога
        shtrihActiveXComponent.setProperty("ValueOfFieldString", new Variant("НДС"));
        Variant result = Dispatch.call(shtrihDispatch, "WriteTable");
        checkErrors(result, true);

        prepareTableField(6, 2);

        shtrihActiveXComponent.setProperty("Password", systemPassword);
        shtrihActiveXComponent.setProperty("TableNumber", new Variant(6));
        shtrihActiveXComponent.setProperty("RowNumber", new Variant(taxRate.taxRateNumber));
        shtrihActiveXComponent.setProperty("FieldNumber", new Variant(2));  //Ставка налога
        shtrihActiveXComponent.setProperty("ValueOfFieldString", new Variant(null));
        shtrihActiveXComponent.setProperty("ValueOfFieldInteger", new Variant(taxRate.taxRateValue.doubleValue() * 100));
        result = Dispatch.call(shtrihDispatch, "WriteTable");
        checkErrors(result, true);
    }

    public static void prepareTableField(int tableNumber, int fieldNumber) {
        shtrihActiveXComponent.setProperty("Password", new Variant(systemPassword));
        shtrihActiveXComponent.setProperty("TableNumber", new Variant(tableNumber));
        shtrihActiveXComponent.setProperty("FieldNumber", new Variant(fieldNumber));

        Variant result = Dispatch.call(shtrihDispatch, "GetFieldStruct");
        checkErrors(result, true);
    }

    public static Map<Integer, Integer> getTaxRanges() {
        Map<Integer, Integer> result = new HashMap<>();
        for (int i = 1; i <= 4; i++)
            result.put(readTaxRange(i), i);
        return result;
    }

    public static int readTaxRange(int rowNumber) {

        shtrihActiveXComponent.setProperty("Password", systemPassword);
        shtrihActiveXComponent.setProperty("TableNumber", new Variant(6));
        shtrihActiveXComponent.setProperty("RowNumber", new Variant(rowNumber));
        shtrihActiveXComponent.setProperty("FieldNumber", new Variant(1));
        Variant result = Dispatch.call(shtrihDispatch, "ReadTable");
        checkErrors(result, true);

        return shtrihActiveXComponent.getPropertyAsInt("ValueOfFieldInteger");
    }


}

