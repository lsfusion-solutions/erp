package equ.clt.handler.kristal;

import equ.api.*;
import org.apache.commons.lang.time.DateUtils;
import org.xBaseJ.DBF;
import org.xBaseJ.Util;
import org.xBaseJ.xBaseJException;

import java.io.*;
import java.math.BigDecimal;
import java.sql.Time;
import java.text.ParseException;
import java.util.*;

public class KristalHandler extends CashRegisterHandler<KristalSalesBatch> {

    public KristalHandler() {
    }

    @Override
    public void sendTransaction(TransactionCashRegisterInfo transactionInfo, List<CashRegisterInfo> machineryInfoList) throws IOException {

        List<String> directoriesList = new ArrayList<String>();
        for (CashRegisterInfo cashRegisterInfo : machineryInfoList) {
            if ((cashRegisterInfo.port != null) && (!directoriesList.contains(cashRegisterInfo.port.trim())))
                directoriesList.add(cashRegisterInfo.port.trim());
            if ((cashRegisterInfo.directory != null) && (!directoriesList.contains(cashRegisterInfo.directory.trim())))
                directoriesList.add(cashRegisterInfo.directory.trim());
        }

        for (String directory : directoriesList) {
            File folder = new File(directory.trim());
            if (!folder.exists() && !folder.mkdir())
                throw new RuntimeException("The folder " + folder.getAbsolutePath() + " can not be created");
            folder = new File(directory.trim() + "/Import");
            if (!folder.exists() && !folder.mkdir())
                throw new RuntimeException("The folder " + folder.getAbsolutePath() + " can not be created");

            Util.setxBaseJProperty("ignoreMissingMDX", "true");

            String path = directory + "/Import/groups.txt";

            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(path), "windows-1251"));

            Set<Integer> numberGroupItems = new HashSet<Integer>();
            for (ItemInfo item : transactionInfo.itemsList) {
                if (!numberGroupItems.contains(item.numberGroupItem)) {
                    String record = "+|" + item.nameGroupItem + "|" + item.numberGroupItem + "|0|0|0|0";
                    writer.println(record);
                    numberGroupItems.add(item.numberGroupItem);
                }

            }
            writer.close();

            path = directory + "/Import/message.txt";
            writer = new PrintWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(path), "windows-1251"));

            for (ItemInfo item : transactionInfo.itemsList) {
                if (item.composition != null && !item.composition.equals("")) {
                    String record = "+|" + item.idBarcode + "|" + item.composition + "|||";
                    writer.println(record);
                }
            }
            writer.close();

            path = directory + "/Import/plu.txt";
            writer = new PrintWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(path), "windows-1251"));

            for (ItemInfo item : transactionInfo.itemsList) {
                String record = "+|" + item.idBarcode + "|" + item.idBarcode + "|" + item.name + "|" +
                        (item.isWeightItem ? "кг.|" : "ШТ|") + (item.isWeightItem ? "1|" : "0|") + "1|"/*section*/ +
                        item.price.intValue() + "|" + "0|"/*fixprice*/ + (item.isWeightItem ? "0.001|" : "1|") +
                        item.numberGroupItem + "|0|0|0|0";
                writer.println(record);
            }
            writer.close();

            path = directory + "/Import/scales.txt";
            writer = new PrintWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(path), "windows-1251"));

            for (ItemInfo item : transactionInfo.itemsList) {
                String record = "+|" + item.idBarcode + "|" + item.idBarcode + "|" + "22|" + item.name + "||" +
                        "1|0|1|"/*effectiveLife & GoodLinkToScales*/ +
                        (item.composition != null ? item.idBarcode : "0")/*ingredientNumber*/ + "|" +
                        item.price.intValue();
                writer.println(record);
            }
            writer.close();
        }
    }

    @Override
    public SalesBatch readSalesInfo(List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException {

        Map<String, String> cashRegisterDirectories = new HashMap<String, String>();
        for (CashRegisterInfo cashRegister : cashRegisterInfoList) {
            if ((cashRegister.directory != null) && (!cashRegisterDirectories.containsValue(cashRegister.directory)))
                cashRegisterDirectories.put(cashRegister.cashRegisterNumber, cashRegister.directory);
            if ((cashRegister.port != null) && (!cashRegisterDirectories.containsValue(cashRegister.port)))
                cashRegisterDirectories.put(cashRegister.cashRegisterNumber, cashRegister.port);
        }

        List<SalesInfo> salesInfoList = new ArrayList<SalesInfo>();
        List<String> filePathList = new ArrayList<String>();
        for (Map.Entry<String, String> entry : cashRegisterDirectories.entrySet()) {

            try {
                if (entry.getValue() != null) {
                    String receiptFilePath = entry.getValue().trim() + "/Export/data/CH_HEAD.dbf";
                    if (new File(receiptFilePath).exists()) {
                        DBF receiptFile = new DBF(receiptFilePath);

                        Map<Integer, ReceiptInfo> receiptInfoMap = new HashMap<Integer, ReceiptInfo>();

                        for (int i = 0; i < receiptFile.getRecordCount(); i++) {

                            receiptFile.read();

                            Integer zReportNumber = getDBFIntegerFieldValue(receiptFile, "CREG", "Cp1251", false, null);
                            Integer receiptNumber = getDBFIntegerFieldValue(receiptFile, "ID", "Cp1251", false, null);
                            java.sql.Date date = getDBFDateFieldValue(receiptFile, "DATE", "Cp1251", null);
                            Time time = new Time(date.getTime());
                            BigDecimal cost1 = getDBFBigDecimalFieldValue(receiptFile, "COST1", "Cp1251", null); //cash
                            BigDecimal cost3 = getDBFBigDecimalFieldValue(receiptFile, "COST3", "Cp1251", null); //card
                            BigDecimal discountSum = getDBFBigDecimalFieldValue(receiptFile, "SUMDISC", "Cp1251", null);
                            String cashRegisterNumber = getDBFFieldValue(receiptFile, "CASHIER", "Cp1251", null);
                            receiptInfoMap.put(receiptNumber, new ReceiptInfo(zReportNumber, date, time, safeAdd(cost1, cost3), cost3, cost1, discountSum, cashRegisterNumber));
                        }
                        receiptFile.close();

                        String receiptDetailFilePath = entry.getValue().trim() + "/Export/data/CH_POS.dbf";
                        DBF receiptDetailFile = new DBF(receiptDetailFilePath);
                        for (int i = 0; i < receiptDetailFile.getRecordCount()/*111*/; i++) {

                            receiptDetailFile.read();
                            Integer receiptNumber = getDBFIntegerFieldValue(receiptDetailFile, "IDHEAD", "Cp1251", false, null);
                            ReceiptInfo receiptInfo = receiptInfoMap.get(receiptNumber);
                            if (receiptInfo != null) {
                                String cashRegisterNumber = getDBFFieldValue(receiptDetailFile, "CASHIER", "Cp1251", null);
                                String zReportNumber = getDBFFieldValue(receiptDetailFile, "CREG", "Cp1251", null);
                                String barcode = getDBFFieldValue(receiptDetailFile, "BARCODE", "Cp1251", null);
                                BigDecimal quantity = getDBFBigDecimalFieldValue(receiptDetailFile, "COUNT", "Cp1251", null);
                                BigDecimal price = getDBFBigDecimalFieldValue(receiptDetailFile, "PRICE", "Cp1251", null);
                                BigDecimal sumReceiptDetail = getDBFBigDecimalFieldValue(receiptDetailFile, "SUM", "Cp1251", null);
                                salesInfoList.add(new SalesInfo(cashRegisterNumber, zReportNumber, receiptNumber, receiptInfo.date,
                                        receiptInfo.time, receiptInfo.sumReceipt, receiptInfo.sumCard, receiptInfo.sumCash, barcode, quantity, price, sumReceiptDetail, null, receiptInfo.discountSum, null, receiptInfo.numberReceiptDetail++, null));
                            }
                        }
                        receiptDetailFile.close();
                        filePathList.add(receiptFilePath);
                        filePathList.add(receiptDetailFilePath);
                    }
                }
            } catch (xBaseJException e) {
                throw new RuntimeException(e.toString(), e.getCause());
            }
        }


        return new KristalSalesBatch(salesInfoList, filePathList);
    }

    @Override
    public void finishReadingSalesInfo(KristalSalesBatch salesBatch) {
        for (String readFile : salesBatch.readFiles) {
            File f = new File(readFile);
            if (!f.delete())
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
        }
    }

    private class ReceiptInfo {
        Integer zReportNumber;
        java.sql.Date date;
        Time time;
        BigDecimal sumReceipt;
        BigDecimal sumCard;
        BigDecimal sumCash;
        BigDecimal discountSum;
        String cashRegisterNumber;
        Integer numberReceiptDetail;

        ReceiptInfo(Integer zReportNumber, java.sql.Date date, Time time, BigDecimal sumReceipt, BigDecimal sumCard, BigDecimal sumCash, BigDecimal discountSum, String cashRegisterNumber) {
            this.zReportNumber = zReportNumber;
            this.date = date;
            this.time = time;
            this.sumReceipt = sumReceipt;
            this.sumCard = sumCard;
            this.sumCash = sumCash;
            this.discountSum = discountSum;
            this.cashRegisterNumber = cashRegisterNumber;
            this.numberReceiptDetail = 1;
        }
    }

    protected String getDBFFieldValue(DBF importFile, String fieldName, String charset, String defaultValue) throws UnsupportedEncodingException {
        return getDBFFieldValue(importFile, fieldName, charset, false, defaultValue);
    }

    protected String getDBFFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull, String defaultValue) throws UnsupportedEncodingException {
        try {
            String result = new String(importFile.getField(fieldName).getBytes(), charset).trim();
            return result.isEmpty() || (zeroIsNull && result.equals("0")) ? defaultValue : result;
        } catch (xBaseJException e) {
            return defaultValue;
        }
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, String fieldName, String charset, String defaultValue) throws UnsupportedEncodingException {
        return getDBFBigDecimalFieldValue(importFile, fieldName, charset, false, defaultValue);
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull, String defaultValue) throws UnsupportedEncodingException {
        String result = getDBFFieldValue(importFile, fieldName, charset, zeroIsNull, defaultValue);
        return (result == null || result.isEmpty() || (zeroIsNull && Double.valueOf(result).equals(new Double(0)))) ? null : new BigDecimal(result.replace(",", "."));
    }

    protected Integer getDBFIntegerFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull, String defaultValue) throws UnsupportedEncodingException {
        String result = getDBFFieldValue(importFile, fieldName, charset, zeroIsNull, defaultValue);
        return (result == null || (zeroIsNull && Double.valueOf(result).equals(new Double(0)))) ? null : new Double(result).intValue();
    }

    protected java.sql.Date getDBFDateFieldValue(DBF importFile, String fieldName, String charset, java.sql.Date defaultValue) throws UnsupportedEncodingException, ParseException {
        String dateString = getDBFFieldValue(importFile, fieldName, charset, false, "");
        return dateString.isEmpty() ? defaultValue : new java.sql.Date(DateUtils.parseDate(dateString, new String[]{"dd.MM.yyyy hh:mm", "dd.MM.yyyy hh:mm:"}).getTime());
    }

    protected BigDecimal safeAdd(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else return (operand1 == null ? operand2 : (operand2 == null ? operand1 : operand1.add(operand2)));
    }
}
