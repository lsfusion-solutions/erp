package equ.clt.handler.ukm4;

import com.google.common.base.Throwables;
import com.hexiong.jdbf.DBFWriter;
import com.hexiong.jdbf.JDBFException;
import equ.api.*;
import equ.api.cashregister.*;
import org.apache.commons.lang.time.DateUtils;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.*;

public class UKM4Handler extends CashRegisterHandler<UKM4SalesBatch> {

    String defaultCharset = "Cp1251";
    
    public UKM4Handler() {
    }

    @Override
    public void sendTransaction(TransactionCashRegisterInfo transactionInfo, List<CashRegisterInfo> machineryInfoList) throws IOException {

        DBFWriter barDBFWriter = null;
        DBFWriter classifDBFWriter = null;
        DBFWriter pluCashDBFWriter = null;
        DBFWriter pluLimDBFWriter = null;

        try {

            List<String> directoriesList = new ArrayList<String>();
            for (CashRegisterInfo cashRegisterInfo : machineryInfoList) {
                if ((cashRegisterInfo.port != null) && (!directoriesList.contains(cashRegisterInfo.port.trim())))
                    directoriesList.add(cashRegisterInfo.port.trim());
                if ((cashRegisterInfo.directory != null) && (!directoriesList.contains(cashRegisterInfo.directory.trim())))
                    directoriesList.add(cashRegisterInfo.directory.trim());
            }

            for (String directory : directoriesList) {
                if (!new File(directory.trim()).exists())
                    throw new RuntimeException("The folder " + directory + " doesn't exist");

                //BAR.DBF
                OverJDBField[] barFields = {
                        new OverJDBField("BARCODE", 'F', 10, 0),
                        new OverJDBField("CARDARTICU", 'F', 14, 0),
                        new OverJDBField("CARDSIZE", 'C', 20, 0),
                        new OverJDBField("QUANTITY", 'F', 10, 0)
                };
                barDBFWriter = new DBFWriter(directory + "/BAR.DBF", barFields, "CP866");                                
                for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                    barDBFWriter.addRecord(new Object[]{Integer.parseInt(item.idBarcode), Integer.parseInt(item.idBarcode)/*или что туда надо писать?*/,
                            "NOSIZE", 1/*без разницы, что писать в количество?*/});
                }
                barDBFWriter.close();


                //CLASSIF.DBF
                OverJDBField[] classifFields = {
                        new OverJDBField("GROOP1", 'F', 10, 0),
                        new OverJDBField("GROOP2", 'F', 10, 0),
                        new OverJDBField("GROOP3", 'F', 10, 0),
                        new OverJDBField("GROOP4", 'F', 10, 0),
                        new OverJDBField("GROOP5", 'F', 10, 0),
                        new OverJDBField("NAME", 'C', 50, 0)
                };
                classifDBFWriter = new DBFWriter(directory + "/CLASSIF.DBF", classifFields, "CP866");
                for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                    int size = item.hierarchyItemGroup.size();
                    Integer group1 = Integer.parseInt(size >= 1 ? item.hierarchyItemGroup.get(size - 1).idItemGroup : "0");
                    Integer group2 = Integer.parseInt(size >= 2 ? item.hierarchyItemGroup.get(size - 2).idItemGroup : "0");
                    Integer group3 = Integer.parseInt(size >= 3 ? item.hierarchyItemGroup.get(size - 3).idItemGroup : "0");
                    Integer group4 = Integer.parseInt(size >= 4 ? item.hierarchyItemGroup.get(size - 4).idItemGroup : "0");
                    Integer group5 = Integer.parseInt(size >= 5 ? item.hierarchyItemGroup.get(size - 5).idItemGroup : "0");
                    String name = item.name.substring(0, Math.min(item.name.length(), 50));
                    classifDBFWriter.addRecord(new Object[]{group1, group2, group3, group4, group5, name});
                }
                classifDBFWriter.close();


                //PLUCASH.DBF
                OverJDBField[] pluCashFields = {
                        new OverJDBField("ARTICUL", 'F', 10, 0),
                        new OverJDBField("NAME", 'C', 50, 0),
                        new OverJDBField("MESURIMENT", 'C', 2, 0),
                        new OverJDBField("MESPRESISI", 'F', 10, 3),
                        new OverJDBField("ADD1", 'C', 10, 0),
                        new OverJDBField("ADD2", 'C', 10, 0),
                        new OverJDBField("ADD3", 'C', 10, 0),
                        new OverJDBField("ADDNUM1", 'C', 10, 0),
                        new OverJDBField("ADDNUM2", 'C', 10, 0),
                        new OverJDBField("ADDNUM3", 'C', 10, 0),
                        new OverJDBField("SCALE", 'C', 20, 0),
                        new OverJDBField("GROOP1", 'N', 10, 0),
                        new OverJDBField("GROOP2", 'N', 10, 0),
                        new OverJDBField("GROOP3", 'N', 10, 0),
                        new OverJDBField("GROOP4", 'N', 10, 0),
                        new OverJDBField("GROOP5", 'N', 10, 0),
                        new OverJDBField("PRICERUB", 'N', 10, 0),
                        new OverJDBField("PRICECUR", 'C', 10, 0),
                        new OverJDBField("CLIENTINDE", 'N', 10, 0),
                        new OverJDBField("COMMENTARY", 'C', 50, 0),
                        new OverJDBField("DELETED", 'N', 1, 0),
                        new OverJDBField("MODDATE", 'D', 8, 0),
                        new OverJDBField("MODTIME", 'C', 20, 0),
                        new OverJDBField("MODPERSONI", 'C', 50, 0)
                };
                pluCashDBFWriter = new DBFWriter(directory + "/PLUCASH.DBF", pluCashFields, "CP866");
                              
                for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                    
                    String name = item.name.substring(0, Math.min(item.name.length(), 50));
                    String mesuriment = item.passScalesItem && item.splitItem ? "кг" : "1";
                    double mespresisi = item.splitItem ? 0.001 : 1.000;
                    int size = item.hierarchyItemGroup.size();
                    Integer group1 = Integer.parseInt(size >= 1 ? item.hierarchyItemGroup.get(size - 1).idItemGroup : "0");
                    Integer group2 = Integer.parseInt(size >= 2 ? item.hierarchyItemGroup.get(size - 2).idItemGroup : "0");
                    Integer group3 = Integer.parseInt(size >= 3 ? item.hierarchyItemGroup.get(size - 3).idItemGroup : "0");
                    Integer group4 = Integer.parseInt(size >= 4 ? item.hierarchyItemGroup.get(size - 4).idItemGroup : "0");
                    Integer group5 = Integer.parseInt(size >= 5 ? item.hierarchyItemGroup.get(size - 5).idItemGroup : "0");

                    pluCashDBFWriter.addRecord(new Object[]{Integer.parseInt(item.idBarcode), name, mesuriment, mespresisi, null, null, 
                            null, null, null, null, "NOSIZE", group1, group2, group3, group4, group5, 
                            item.price.doubleValue(), null, 0, null, 1, transactionInfo.date, null, null});
                }
                pluCashDBFWriter.close();


                //PLULIM.DBF
                OverJDBField[] pluLimFields = {
                        new OverJDBField("CARDARTICU", 'F', 14, 0),
                        new OverJDBField("PERCENT", 'F', 5, 0)
                };
                pluLimDBFWriter = new DBFWriter(directory + "/PLULIM.DBF", pluLimFields, "CP866");
                for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                    pluLimDBFWriter.addRecord(new Object[]{Integer.parseInt(item.idBarcode), 0/*откуда брать макс. процент скидки?*/});
                }
                pluLimDBFWriter.close();                
                
                new File(directory + "\\cash01.upd").createNewFile();

            }
        } catch (JDBFException e) {
            throw Throwables.propagate(e);
        } finally {
            try {
                if (barDBFWriter != null)
                    barDBFWriter.close();
                if (pluCashDBFWriter != null)
                    pluCashDBFWriter.close();
                if (pluLimDBFWriter != null)
                    pluLimDBFWriter.close();
                if (classifDBFWriter != null)
                    classifDBFWriter.close();
            } catch (JDBFException ignored) {
            }
        }
    }

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {
        
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) throws IOException {
        
    }

    @Override
    public SalesBatch readSalesInfo(List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException {
        Map<Integer, String> cashRegisterDirectories = new HashMap<Integer, String>();
        for (CashRegisterInfo cashRegister : cashRegisterInfoList) {
            if ((cashRegister.directory != null) && (!cashRegisterDirectories.containsValue(cashRegister.directory)))
                cashRegisterDirectories.put(cashRegister.number, cashRegister.directory);
            if ((cashRegister.port != null) && (!cashRegisterDirectories.containsValue(cashRegister.port)))
                cashRegisterDirectories.put(cashRegister.number, cashRegister.port);
        }
        List<SalesInfo> salesInfoList = new ArrayList<SalesInfo>();
        List<String> readFiles = new ArrayList<String>();
        for (Map.Entry<Integer, String> entry : cashRegisterDirectories.entrySet()) {
            DBF importSailFile = null;
            DBF importDiscFile = null;
            DBF importCardFile = null;
            Map<String, BigDecimal> discountMap = new HashMap<String, BigDecimal>();
            Map<String, String> discountCardMap = new HashMap<String, String>();
            try {
                if (entry.getValue() != null) {

                    String fileDiscPath = entry.getValue().trim() + "/CASHDISC.DBF";
                    if (new File(fileDiscPath).exists()) {
                        importDiscFile = new DBF(fileDiscPath);
                        readFiles.add(fileDiscPath);
                        int recordDiscCount = importDiscFile.getRecordCount();
                        for (int i = 0; i < recordDiscCount; i++) {
                            importDiscFile.read();

                            String cashRegisterNumber = getDBFFieldValue(importDiscFile, "CASHNUMBER", defaultCharset);
                            String zNumber = getDBFFieldValue(importDiscFile, "ZNUMBER", defaultCharset);
                            Integer receiptNumber = getDBFIntegerFieldValue(importDiscFile, "CHECKNUMBE", defaultCharset);
                            Integer numberReceiptDetail = getDBFIntegerFieldValue(importDiscFile, "ID", defaultCharset);
                            Integer type = getDBFIntegerFieldValue(importDiscFile, "DISCOUNTIN", defaultCharset);
                            BigDecimal discountSum = getDBFBigDecimalFieldValue(importDiscFile, "DISCOUNTRU", defaultCharset);

                            String sid = cashRegisterNumber + "_" + zNumber + "_" + receiptNumber + "_" + numberReceiptDetail;
                            if (type.equals(4)) {
                                BigDecimal tempSum = discountMap.get(sid);
                                discountMap.put(sid, safeAdd(discountSum, tempSum));
                            }
                        }
                        importDiscFile.close();
                    }

                    String fileCardPath = entry.getValue().trim() + "/CASHDCRD.DBF";
                    if (new File(fileCardPath).exists()) {
                        importCardFile = new DBF(fileCardPath);
                        readFiles.add(fileCardPath);
                        int recordCardCount = importCardFile.getRecordCount();
                        for (int i = 0; i < recordCardCount; i++) {
                            importCardFile.read();

                            String cashRegisterNumber = getDBFFieldValue(importCardFile, "CASHNUMBER", defaultCharset);
                            String zNumber = getDBFFieldValue(importCardFile, "ZNUMBER", defaultCharset);
                            Integer receiptNumber = getDBFIntegerFieldValue(importCardFile, "CHECKNUMBE", "Cp1251");
                            String cardNumber = getDBFFieldValue(importCardFile, "CARDNUMBER", "Cp1251");

                            String sid = cashRegisterNumber + "_" + zNumber + "_" + receiptNumber;
                            discountCardMap.put(sid, cardNumber);
                        }
                        importCardFile.close();
                    }

                    String fileSailPath = entry.getValue().trim() + "/CASHSAIL.DBF";
                    if (new File(fileSailPath).exists()) {
                        importSailFile = new DBF(fileSailPath);
                        readFiles.add(fileSailPath);
                        int recordSailCount = importSailFile.getRecordCount();
                        Map<Integer, BigDecimal[]> receiptNumberSumReceipt = new HashMap<Integer, BigDecimal[]>();

                        for (int i = 0; i < recordSailCount; i++) {
                            importSailFile.read();

                            Integer operation = getDBFIntegerFieldValue(importSailFile, "OPERATION", defaultCharset);
                            //0 - возврат cash, 1 - продажа cash, 2,4 - возврат card, 3,5 - продажа card

                            String cashRegisterNumber = getDBFFieldValue(importSailFile, "CASHNUMBER", defaultCharset);
                            String zNumber = getDBFFieldValue(importSailFile, "ZNUMBER", defaultCharset);
                            Integer receiptNumber = getDBFIntegerFieldValue(importSailFile, "CHECKNUMBE", defaultCharset);
                            Integer numberReceiptDetail = getDBFIntegerFieldValue(importSailFile, "ID", defaultCharset);
                            Date date = getDBFDateFieldValue(importSailFile, "DATE", defaultCharset);
                            String timeString = getDBFFieldValue(importSailFile, "TIME", defaultCharset);
                            timeString = timeString.length() == 3 ? ("0" + timeString) : timeString;
                            Time time = new Time(DateUtils.parseDate(timeString, new String[]{"HHmm"}).getTime());
                            String barcodeReceiptDetail = getDBFFieldValue(importSailFile, "CARDARTICU", defaultCharset);
                            BigDecimal quantityReceiptDetail = getDBFBigDecimalFieldValue(importSailFile, "QUANTITY", defaultCharset);
                            BigDecimal priceReceiptDetail = getDBFBigDecimalFieldValue(importSailFile, "PRICERUB", defaultCharset);
                            BigDecimal sumReceiptDetail = getDBFBigDecimalFieldValue(importSailFile, "TOTALRUB", defaultCharset);
                            BigDecimal discountSumReceiptDetail = discountMap.get(cashRegisterNumber + "_" + zNumber + "_" + receiptNumber + "_" + numberReceiptDetail);
                            String discountCardNumber = discountCardMap.get(cashRegisterNumber + "_" + zNumber + "_" + receiptNumber);

                            BigDecimal[] tempSumReceipt = receiptNumberSumReceipt.get(receiptNumber);
                            BigDecimal tempSum1 = tempSumReceipt != null ? tempSumReceipt[0] : null;
                            BigDecimal tempSum2 = tempSumReceipt != null ? tempSumReceipt[1] : null;
                            receiptNumberSumReceipt.put(receiptNumber, new BigDecimal[]{safeAdd(tempSum1,(operation <= 1 ? sumReceiptDetail : null)),
                                    safeAdd(tempSum2, (operation > 1 ? sumReceiptDetail : null))});

                            salesInfoList.add(new SalesInfo(0, Integer.parseInt(cashRegisterNumber), zNumber, 
                                    receiptNumber, date, time, BigDecimal.ZERO, BigDecimal.ZERO, barcodeReceiptDetail,
                                    null, operation % 2 == 1 ? quantityReceiptDetail : quantityReceiptDetail.negate(),
                                    priceReceiptDetail,
                                    operation % 2 == 1 ? sumReceiptDetail : sumReceiptDetail.negate(),
                                    discountSumReceiptDetail, null, discountCardNumber, numberReceiptDetail, null));
                        }
                        for (SalesInfo salesInfo : salesInfoList) {
                            salesInfo.sumCash = receiptNumberSumReceipt.get(salesInfo.numberReceipt)[0];
                            salesInfo.sumCard = receiptNumberSumReceipt.get(salesInfo.numberReceipt)[1];
                        }
                    }
                }
            } catch (xBaseJException e) {
                throw new RuntimeException(e.toString(), e.getCause());
            } finally {
                if (importSailFile != null)
                    importSailFile.close();
                if (importCardFile != null)
                    importCardFile.close();
                if (importDiscFile != null)
                    importDiscFile.close();
            }
        }
        return new UKM4SalesBatch(salesInfoList, readFiles);
    }

    @Override
    public String requestSalesInfo(List<RequestExchange> requestExchangeList) throws IOException, ParseException {
        return null;
    }

    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet) throws ClassNotFoundException {
        return null;
    }

    @Override
    public void finishReadingCashDocumentInfo(CashDocumentBatch cashDocumentBatch) {       
    }

    @Override
    public void finishReadingSalesInfo(UKM4SalesBatch salesBatch) {
        for (String readFile : salesBatch.readFiles) {
            File f = new File(readFile);
            if (!f.delete())
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
        }
    }

    @Override
    public Map<String, Timestamp> requestSucceededSoftCheckInfo(Set<String> directorySet) {
        return null;
    }

    @Override
    public String checkZReportSum(Map<String, BigDecimal> zReportSumMap, List<String> idCashRegisterList) throws ClassNotFoundException, SQLException {
        return null;
    }

    protected BigDecimal safeAdd(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else return (operand1 == null ? operand2 : (operand2 == null ? operand1 : operand1.add(operand2)));
    }

    protected String getDBFFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException {
        return getDBFFieldValue(importFile, fieldName, charset, null);
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

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException {
        return getDBFBigDecimalFieldValue(importFile, fieldName, charset, null);
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, String fieldName, String charset, String defaultValue) throws UnsupportedEncodingException {
        return getDBFBigDecimalFieldValue(importFile, fieldName, charset, false, defaultValue);
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull, String defaultValue) throws UnsupportedEncodingException {
        String result = getDBFFieldValue(importFile, fieldName, charset, zeroIsNull, defaultValue);
        return (result == null || result.isEmpty() || (zeroIsNull && Double.valueOf(result).equals(new Double(0)))) ? null : new BigDecimal(result.replace(",", "."));
    }

    protected Integer getDBFIntegerFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException {
        return getDBFIntegerFieldValue(importFile, fieldName, charset, false, null);
    }
    
    protected Integer getDBFIntegerFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull, String defaultValue) throws UnsupportedEncodingException {
        String result = getDBFFieldValue(importFile, fieldName, charset, zeroIsNull, defaultValue);
        return (result == null || (zeroIsNull && Double.valueOf(result).equals(new Double(0)))) ? null : new Double(result).intValue();
    }

    protected Date getDBFDateFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException, ParseException {
        return getDBFDateFieldValue(importFile, fieldName, charset, null);
    }
    
    protected Date getDBFDateFieldValue(DBF importFile, String fieldName, String charset, Date defaultValue) throws UnsupportedEncodingException, ParseException {
        String dateString = getDBFFieldValue(importFile, fieldName, charset, false, "");
        return dateString.isEmpty() ? defaultValue : new Date(DateUtils.parseDate(dateString, new String[]{"yyyyMMdd", "dd.MM.yyyy"}).getTime());
    }
}
