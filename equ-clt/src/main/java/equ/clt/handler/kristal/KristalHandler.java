package equ.clt.handler.kristal;

import com.google.common.base.Throwables;
import com.hexiong.jdbf.DBFWriter;
import com.hexiong.jdbf.JDBFException;
import com.hexiong.jdbf.JDBField;
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
            
            String exchangeDirectory = directory.trim();
            
            File folder = new File(exchangeDirectory);
            if (!folder.exists() && !folder.mkdir())
                throw new RuntimeException("The folder " + folder.getAbsolutePath() + " can not be created");
            folder = new File(exchangeDirectory + "/Import");
            if (!folder.exists() && !folder.mkdir())
                throw new RuntimeException("The folder " + folder.getAbsolutePath() + " can not be created");

            Util.setxBaseJProperty("ignoreMissingMDX", "true");


            //plu.txt
            File flagPluFile = new File(exchangeDirectory + "/Import/WAIT_PLU");
            if (flagPluFile.createNewFile()) {

                File pluFile = new File(directory + "/Import/plu.txt");
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(pluFile), "windows-1251"));

                for (ItemInfo item : transactionInfo.itemsList) {
                    String record = "+|" + item.idBarcode + "|" + item.idBarcode + "|" + item.name + "|" +
                            (item.isWeightItem ? "кг.|" : "ШТ|") + (item.isWeightItem ? "1|" : "0|") + "1|"/*section*/ +
                            item.price.intValue() + "|" + "0|"/*fixprice*/ + (item.isWeightItem ? "0.001|" : "1|") +
                            item.numberGroupItem + "|0|0|0|0";
                    writer.println(record);
                }
                writer.close();

                if (flagPluFile.delete()) {
                    while (pluFile.exists()) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw Throwables.propagate(e);
                        }
                    }
                }
            }

            //message.txt
            File flagMessageFile = new File(exchangeDirectory + "/Import/WAIT_MESSAGE");
            if (flagMessageFile.createNewFile()) {

                File messageFile = new File(directory + "/Import/message.txt");
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(messageFile), "windows-1251"));

                for (ItemInfo item : transactionInfo.itemsList) {
                    if (item.composition != null && !item.composition.equals("")) {
                        String record = "+|" + item.idBarcode + "|" + item.composition + "|||";
                        writer.println(record);
                    }
                }
                writer.close();
                if (flagMessageFile.delete()) {
                    while (messageFile.exists()) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw Throwables.propagate(e);
                        }
                    }
                }
            }

            //scale.txt
            File flagScaleFile = new File(exchangeDirectory + "/Import/WAIT_SCALE");
            if (flagScaleFile.createNewFile()) {
                File scaleFile = new File(directory + "/Import/scales.txt");
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(scaleFile), "windows-1251"));

                for (ItemInfo item : transactionInfo.itemsList) {
                    String record = "+|" + item.idBarcode + "|" + item.idBarcode + "|" + "22|" + item.name + "||" +
                            "1|0|1|"/*effectiveLife & GoodLinkToScales*/ +
                            (item.composition != null ? item.idBarcode : "0")/*ingredientNumber*/ + "|" +
                            item.price.intValue();
                    writer.println(record);
                }
                writer.close();
                if (flagScaleFile.delete()) {
                    while (scaleFile.exists()) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw Throwables.propagate(e);
                        }
                    }
                }
            }

            //groups.txt
            File flagGroupsFile = new File(exchangeDirectory + "/Import/WAIT_GROUPS");
            if (flagGroupsFile.createNewFile()) {

                File groupsFile = new File(directory + "/Import/groups.txt");

                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(groupsFile), "windows-1251"));

                Set<Integer> numberGroupItems = new HashSet<Integer>();
                for (ItemInfo item : transactionInfo.itemsList) {
                    if (!numberGroupItems.contains(item.numberGroupItem)) {
                        String record = "+|" + item.nameGroupItem + "|" + item.numberGroupItem + "|0|0|0|0";
                        writer.println(record);
                        numberGroupItems.add(item.numberGroupItem);
                    }

                }
                writer.close();
                if (flagGroupsFile.delete()) {
                    while (groupsFile.exists()) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw Throwables.propagate(e);
                        }
                    }
                }
            }   
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

                    String exchangeDirectory = entry.getValue().trim();

                    createQueryFile(exchangeDirectory + "/Export/Query/12.dbf");
                    createQueryFile(exchangeDirectory + "/Export/Query/14.dbf");
                    String receiptFilePath = exchangeDirectory + "/Export/data/OCHHEAD.dbf";
                    File receiptFile = new File(receiptFilePath);
                    File waitReceiptFile = new File(exchangeDirectory + "/Export/data/WAIT_OCHHEAD");
                    String receiptDetailFilePath = exchangeDirectory + "/Export/data/OCH_POS.dbf";
                    File receiptDetailFile = new File(receiptDetailFilePath);
                    File waitReceiptDetailFile = new File(exchangeDirectory + "/Export/data/WAIT_OCHPOS");
                    
                    while((!receiptFile.exists() || !receiptDetailFile.exists()) && (waitReceiptFile.exists() || waitReceiptDetailFile.exists())) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw Throwables.propagate(e);
                        }
                    }

                    if (receiptFile.exists() && receiptDetailFile.exists()) {
                        DBF receiptFileDBF = new DBF(receiptFilePath);

                        Map<Integer, ReceiptInfo> receiptInfoMap = new HashMap<Integer, ReceiptInfo>();

                        for (int i = 0; i < receiptFileDBF.getRecordCount(); i++) {
                            receiptFileDBF.read();
                            Integer zReportNumber = getDBFIntegerFieldValue(receiptFileDBF, "CREG", "Cp1251", false, null);
                            Integer receiptNumber = getDBFIntegerFieldValue(receiptFileDBF, "ID", "Cp1251", false, null);
                            java.sql.Date date = getDBFDateFieldValue(receiptFileDBF, "DATE", "Cp1251", null);
                            Time time = new Time(date.getTime());
                            BigDecimal cost1 = getDBFBigDecimalFieldValue(receiptFileDBF, "COST1", "Cp1251", null); //cash
                            BigDecimal cost3 = getDBFBigDecimalFieldValue(receiptFileDBF, "COST3", "Cp1251", null); //card
                            BigDecimal discountSum = getDBFBigDecimalFieldValue(receiptFileDBF, "SUMDISC", "Cp1251", null);
                            String cashRegisterNumber = getDBFFieldValue(receiptFileDBF, "CASHIER", "Cp1251", null);
                            receiptInfoMap.put(receiptNumber, new ReceiptInfo(zReportNumber, date, time, safeAdd(cost1, cost3), cost3, cost1, discountSum, cashRegisterNumber));
                        }
                        receiptFileDBF.close();

                        DBF receiptDetailFileDBF = new DBF(receiptDetailFilePath);
                        for (int i = 0; i < receiptDetailFileDBF.getRecordCount(); i++) {
                            receiptDetailFileDBF.read();
                            Integer receiptNumber = getDBFIntegerFieldValue(receiptDetailFileDBF, "IDHEAD", "Cp1251", false, null);
                            ReceiptInfo receiptInfo = receiptInfoMap.get(receiptNumber);
                            if (receiptInfo != null) {
                                String cashRegisterNumber = getDBFFieldValue(receiptDetailFileDBF, "CASHIER", "Cp1251", null);
                                String zReportNumber = getDBFFieldValue(receiptDetailFileDBF, "CREG", "Cp1251", null);
                                String barcode = getDBFFieldValue(receiptDetailFileDBF, "BARCODE", "Cp1251", null);
                                BigDecimal quantity = getDBFBigDecimalFieldValue(receiptDetailFileDBF, "COUNT", "Cp1251", null);
                                BigDecimal price = getDBFBigDecimalFieldValue(receiptDetailFileDBF, "PRICE", "Cp1251", null);
                                BigDecimal sumReceiptDetail = getDBFBigDecimalFieldValue(receiptDetailFileDBF, "SUM", "Cp1251", null);
                                salesInfoList.add(new SalesInfo(cashRegisterNumber, zReportNumber, receiptNumber, receiptInfo.date,
                                        receiptInfo.time, receiptInfo.sumReceipt, receiptInfo.sumCard, receiptInfo.sumCash, barcode, quantity, price, sumReceiptDetail, null, receiptInfo.discountSum, null, receiptInfo.numberReceiptDetail++, null));
                            }
                        }
                        receiptDetailFileDBF.close();

                        filePathList.add(receiptFilePath);
                        filePathList.add(receiptDetailFilePath);
                    }
                }
            } catch (xBaseJException e) {
                throw Throwables.propagate(e);
            } catch (JDBFException e) {
                throw Throwables.propagate(e);
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

    private File createQueryFile(String path) throws JDBFException {
        JDBField[] fields = {new JDBField("DATA", 'D', 8, 0), new JDBField("DEVICELIST", 'C', 8, 0)};
        File dbfFile = new File(path);
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), fields, "CP866");
        dbfwriter.addRecord(new Object[]{Calendar.getInstance().getTime(), "*"});                     
        dbfwriter.close();
        return dbfFile;
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
