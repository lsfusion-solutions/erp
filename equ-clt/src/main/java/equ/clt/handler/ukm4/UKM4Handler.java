package equ.clt.handler.ukm4;

import com.google.common.base.Throwables;
import com.hexiong.jdbf.DBFWriter;
import equ.api.*;
import equ.api.cashregister.*;
import equ.clt.handler.HandlerUtils;
import org.apache.commons.lang3.time.DateUtils;
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

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        return "ukm4";
    }

    @Override
    public Map<Integer, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) throws IOException {

        Map<Integer, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        for(TransactionCashRegisterInfo transaction : transactionList) {

            Exception exception = null;
            try {

                DBFWriter barDBFWriter = null;
                DBFWriter classifDBFWriter = null;
                DBFWriter pluCashDBFWriter = null;
                DBFWriter pluLimDBFWriter = null;

                try {

                    List<String> directoriesList = new ArrayList<>();
                    for (CashRegisterInfo cashRegisterInfo : transaction.machineryInfoList) {
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
                                new OverJDBField("BARCODE", 'C', 15, 0),
                                new OverJDBField("CARDARTICU", 'C', 30, 0),
                                new OverJDBField("CARDSIZE", 'C', 10, 0),
                                new OverJDBField("QUANTITY", 'N', 16, 6)
                        };
                        barDBFWriter = new DBFWriter(directory + "/BAR.DBF", barFields, "CP866");
                        for (CashRegisterItemInfo item : transaction.itemsList) {
                            if (!Thread.currentThread().isInterrupted()) {
                                barDBFWriter.addRecord(new Object[]{HandlerUtils.trim(item.idBarcode, 15), HandlerUtils.trim(item.idBarcode, 30)/*или что туда надо писать?*/,
                                        "NOSIZE", 1/*без разницы, что писать в количество?*/});
                            }
                        }
                        barDBFWriter.close();


                        //CLASSIF.DBF
                        OverJDBField[] classifFields = {
                                new OverJDBField("GROOP1", 'N', 6, 0),
                                new OverJDBField("GROOP2", 'N', 6, 0),
                                new OverJDBField("GROOP3", 'N', 6, 0),
                                new OverJDBField("GROOP4", 'N', 6, 0),
                                new OverJDBField("GROOP5", 'N', 6, 0),
                                new OverJDBField("NAME", 'C', 80, 0)
                        };
                        classifDBFWriter = new DBFWriter(directory + "/CLASSIF.DBF", classifFields, "CP866");
                        Set<Long> idItemGroups = new HashSet<>();
                        for (CashRegisterItemInfo item : transaction.itemsList) {
                            if (!Thread.currentThread().isInterrupted()) {
                                List<ItemGroup> hierarchyItemGroup = transaction.itemGroupMap.get(item.idItemGroup);
                                if(hierarchyItemGroup != null) {
                                    int size = hierarchyItemGroup.size();
                                    Long group1 = parseGroup(size >= 1 ? HandlerUtils.trim(hierarchyItemGroup.get(0).idItemGroup, 6) : "0");
                                    Long group2 = parseGroup(size >= 2 ? HandlerUtils.trim(hierarchyItemGroup.get(1).idItemGroup, 6) : "0");
                                    Long group3 = parseGroup(size >= 3 ? HandlerUtils.trim(hierarchyItemGroup.get(2).idItemGroup, 6) : "0");
                                    Long group4 = parseGroup(size >= 4 ? HandlerUtils.trim(hierarchyItemGroup.get(3).idItemGroup, 6) : "0");
                                    Long group5 = parseGroup(size >= 5 ? HandlerUtils.trim(hierarchyItemGroup.get(4).idItemGroup, 6) : "0");
                                    String name = HandlerUtils.trim(item.nameItemGroup, 80);
                                    if (!idItemGroups.contains(group1)) {
                                        idItemGroups.add(group1);
                                        if (size == 5) {
                                            classifDBFWriter.addRecord(new Object[]{group5, group4, group3, group2, group1, name});
                                            classifDBFWriter.addRecord(new Object[]{group5, group4, group3, group2, 0, hierarchyItemGroup.get(1).nameItemGroup});
                                            classifDBFWriter.addRecord(new Object[]{group5, group4, group3, 0, 0, hierarchyItemGroup.get(2).nameItemGroup});
                                            classifDBFWriter.addRecord(new Object[]{group5, group4, 0, 0, 0, hierarchyItemGroup.get(3).nameItemGroup});
                                            classifDBFWriter.addRecord(new Object[]{group5, 0, 0, 0, 0, hierarchyItemGroup.get(4).nameItemGroup});
                                        } else if (size == 4) {
                                            classifDBFWriter.addRecord(new Object[]{group4, group3, group2, group1, 0, name});
                                            classifDBFWriter.addRecord(new Object[]{group4, group3, group2, 0, 0, hierarchyItemGroup.get(1).nameItemGroup});
                                            classifDBFWriter.addRecord(new Object[]{group4, group3, 0, 0, 0, hierarchyItemGroup.get(2).nameItemGroup});
                                            classifDBFWriter.addRecord(new Object[]{group4, 0, 0, 0, 0, hierarchyItemGroup.get(3).nameItemGroup});
                                        } else if (size == 3) {
                                            classifDBFWriter.addRecord(new Object[]{group3, group2, group1, 0, 0, name});
                                            classifDBFWriter.addRecord(new Object[]{group3, group2, 0, 0, 0, hierarchyItemGroup.get(1).nameItemGroup});
                                            classifDBFWriter.addRecord(new Object[]{group3, 0, 0, 0, 0, hierarchyItemGroup.get(2).nameItemGroup});
                                        } else if (size == 2) {
                                            classifDBFWriter.addRecord(new Object[]{group2, group1, 0, 0, 0, name});
                                            classifDBFWriter.addRecord(new Object[]{group2, 0, 0, 0, 0, hierarchyItemGroup.get(1).nameItemGroup});
                                        } else if (size == 1)
                                            classifDBFWriter.addRecord(new Object[]{group1, 0, 0, 0, 0, name});
                                    }
                                }
                            }
                        }
                        classifDBFWriter.close();


                        //PLUCASH.DBF
                        OverJDBField[] pluCashFields = {
                                new OverJDBField("ARTICUL", 'C', 30, 0),
                                new OverJDBField("NAME", 'C', 80, 0),
                                new OverJDBField("MESURIMENT", 'C', 10, 0),
                                new OverJDBField("MESPRESISI", 'N', 16, 6),
                                new OverJDBField("ADD1", 'C', 20, 0),
                                new OverJDBField("ADD2", 'C', 20, 0),
                                new OverJDBField("ADD3", 'C', 20, 0),
                                new OverJDBField("ADDNUM1", 'N', 16, 6),
                                new OverJDBField("ADDNUM2", 'N', 16, 6),
                                new OverJDBField("ADDNUM3", 'N', 16, 6),
                                new OverJDBField("SCALE", 'C', 10, 0),
                                new OverJDBField("GROOP1", 'N', 6, 0),
                                new OverJDBField("GROOP2", 'N', 6, 0),
                                new OverJDBField("GROOP3", 'N', 6, 0),
                                new OverJDBField("GROOP4", 'N', 6, 0),
                                new OverJDBField("GROOP5", 'N', 6, 0),
                                new OverJDBField("PRICERUB", 'N', 16, 2),
                                new OverJDBField("PRICECUR", 'N', 16, 2),
                                new OverJDBField("CLIENTINDE", 'N', 6, 0),
                                new OverJDBField("COMMENTARY", 'C', 80, 0),
                                new OverJDBField("DELETED", 'N', 6, 0),
                                new OverJDBField("MODDATE", 'D', 8, 0),
                                new OverJDBField("MODTIME", 'N', 6, 0),
                                new OverJDBField("MODPERSONI", 'N', 6, 0)
                        };
                        pluCashDBFWriter = new DBFWriter(directory + "/PLUCASH.DBF", pluCashFields, "CP866");

                        for (CashRegisterItemInfo item : transaction.itemsList) {
                            if (!Thread.currentThread().isInterrupted()) {
                                String mesuriment = item.passScalesItem && item.splitItem ? "кг" : "1";
                                double mespresisi = item.splitItem ? 0.001 : 1.000;
                                List<ItemGroup> hierarchyItemGroup = transaction.itemGroupMap.get(item.idItemGroup);
                                if(hierarchyItemGroup != null) {
                                    int size = hierarchyItemGroup.size();
                                    Long group1 = parseGroup(size >= 1 ? HandlerUtils.trim(hierarchyItemGroup.get(size - 1).idItemGroup, 6) : "0");
                                    Long group2 = parseGroup(size >= 2 ? HandlerUtils.trim(hierarchyItemGroup.get(size - 2).idItemGroup, 6) : "0");
                                    Long group3 = parseGroup(size >= 3 ? HandlerUtils.trim(hierarchyItemGroup.get(size - 3).idItemGroup, 6) : "0");
                                    Long group4 = parseGroup(size >= 4 ? HandlerUtils.trim(hierarchyItemGroup.get(size - 4).idItemGroup, 6) : "0");
                                    Long group5 = parseGroup(size >= 5 ? HandlerUtils.trim(hierarchyItemGroup.get(size - 5).idItemGroup, 6) : "0");

                                    pluCashDBFWriter.addRecord(new Object[]{HandlerUtils.trim(item.idBarcode, 30), HandlerUtils.trim(item.name, 80), mesuriment, mespresisi, null, null,
                                            null, null, null, null, "NOSIZE", group1, group2, group3, group4, group5,
                                            denominateMultiplyType2(item.price, transaction.denominationStage), null, 0, null, 1, transaction.date, null, null});
                                }
                            }
                        }
                        pluCashDBFWriter.close();


                        //PLULIM.DBF
                        OverJDBField[] pluLimFields = {
                                new OverJDBField("CARDARTICU", 'C', 30, 0),
                                new OverJDBField("PERCENT", 'N', 16, 2)
                        };
                        pluLimDBFWriter = new DBFWriter(directory + "/PLULIM.DBF", pluLimFields, "CP866");
                        for (CashRegisterItemInfo item : transaction.itemsList) {
                            if (!Thread.currentThread().isInterrupted()) {
                                pluLimDBFWriter.addRecord(new Object[]{HandlerUtils.trim(item.idBarcode, 30), 0/*откуда брать макс. процент скидки?*/});
                            }
                        }
                        pluLimDBFWriter.close();

                        File flagFile = new File(directory + "/cash01." + (transaction.snapshot ? "cng" : "upd"));
                        flagFile.createNewFile();
                        waitForDeletion(flagFile);

                    }
                } finally {
                    if (barDBFWriter != null)
                        barDBFWriter.close();
                    if (pluCashDBFWriter != null)
                        pluCashDBFWriter.close();
                    if (pluLimDBFWriter != null)
                        pluLimDBFWriter.close();
                    if (classifDBFWriter != null)
                        classifDBFWriter.close();
                }
            } catch(Exception e) {
                exception = e;
            }
            sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(exception));
        }
        return sendTransactionBatchMap;
    }

    private void waitForDeletion(File file) {
        while (!Thread.currentThread().isInterrupted() && file.exists()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
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
    public void sendDiscountCardList(List<DiscountCard> discountCardList, RequestExchange requestExchange) throws IOException {
    }

    @Override
    public void sendPromotionInfo(PromotionInfo promotionInfo, RequestExchange requestExchange) throws IOException {
    }

    @Override
    public void sendCashierInfoList(List<CashierInfo> cashierInfoList, Map<String, Set<String>> directoryStockMap) throws IOException {
    }

    @Override
    public List<CashierTime> requestCashierTime(List<MachineryInfo> cashRegisterInfoList) throws IOException, ClassNotFoundException, SQLException {
        return null;
    }

    @Override
    public SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException {
        Map<String, CashRegisterInfo> directoryCashRegisterMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (c.handlerModel != null && c.directory != null && c.number != null && c.handlerModel.endsWith("UKM4Handler")) {
                directoryCashRegisterMap.put(c.directory + "_" + c.number, c);
            }
        }
        List<SalesInfo> salesInfoList = new ArrayList<>();
        List<String> readFiles = new ArrayList<>();
        DBF importSailFile = null;
        DBF importDiscFile = null;
        DBF importCardFile = null;
        Map<String, BigDecimal> discountMap = new HashMap<>();
        Map<String, String> discountCardMap = new HashMap<>();
        try {
            String fileDiscPath = directory + "/sail/CASHDISC.DBF";
            if (new File(fileDiscPath).exists()) {
                importDiscFile = new DBF(fileDiscPath);
                readFiles.add(fileDiscPath);
                int recordDiscCount = importDiscFile.getRecordCount();
                for (int i = 0; i < recordDiscCount; i++) {
                    importDiscFile.read();

                    String numberCashRegister = getDBFFieldValue(importDiscFile, "CASHNUMBER", defaultCharset);
                    CashRegisterInfo cashRegister = directoryCashRegisterMap.get(directory + "_" + numberCashRegister);
                    String denominationStage = cashRegister == null ? null : cashRegister.denominationStage;

                    String zNumber = getDBFFieldValue(importDiscFile, "ZNUMBER", defaultCharset);
                    Integer receiptNumber = getDBFIntegerFieldValue(importDiscFile, "CHECKNUMBE", defaultCharset);
                    Integer numberReceiptDetail = getDBFIntegerFieldValue(importDiscFile, "ID", defaultCharset);
                    BigDecimal discountSum = denominateDivideType2(getDBFBigDecimalFieldValue(importDiscFile, "DISCOUNTCU", defaultCharset), denominationStage);

                    String sid = numberCashRegister + "_" + zNumber + "_" + receiptNumber + "_" + numberReceiptDetail;
                    BigDecimal tempSum = discountMap.get(sid);
                    discountMap.put(sid, HandlerUtils.safeAdd(discountSum, tempSum));
                }
                importDiscFile.close();
            }

            String fileCardPath = directory + "/sail/CASHDCRD.DBF";
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

            String fileSailPath = directory + "/sail/CASHSAIL.DBF";
            if (new File(fileSailPath).exists()) {
                importSailFile = new DBF(fileSailPath);
                readFiles.add(fileSailPath);
                int recordSailCount = importSailFile.getRecordCount();
                Map<Integer, BigDecimal[]> receiptNumberSumReceipt = new HashMap<>();

                for (int i = 0; i < recordSailCount; i++) {
                    importSailFile.read();

                    Integer operation = getDBFIntegerFieldValue(importSailFile, "OPERATION", defaultCharset);
                    //0 - возврат cash, 1 - продажа cash, 2,4 - возврат card, 3,5 - продажа card

                    String numberCashRegister = getDBFFieldValue(importSailFile, "CASHNUMBER", defaultCharset);
                    CashRegisterInfo cashRegister = directoryCashRegisterMap.get(directory + "_" + numberCashRegister);
                    Integer numberGroup = cashRegister == null ? null : cashRegister.numberGroup;
                    String denominationStage = cashRegister == null ? null : cashRegister.denominationStage;

                    String zNumber = getDBFFieldValue(importSailFile, "ZNUMBER", defaultCharset);
                    Integer receiptNumber = getDBFIntegerFieldValue(importSailFile, "CHECKNUMBE", defaultCharset);
                    Integer numberReceiptDetail = getDBFIntegerFieldValue(importSailFile, "ID", defaultCharset);
                    Date date = getDBFDateFieldValue(importSailFile, "DATE", defaultCharset);
                    String timeString = getDBFFieldValue(importSailFile, "TIME", defaultCharset);
                    timeString = timeString.length() == 3 ? ("0" + timeString) : timeString;
                    Time time = new Time(DateUtils.parseDate(timeString, new String[]{"HHmm"}).getTime());
                    String barcodeReceiptDetail = getDBFFieldValue(importSailFile, "CARDARTICU", defaultCharset);
                    BigDecimal quantityReceiptDetail = getDBFBigDecimalFieldValue(importSailFile, "QUANTITY", defaultCharset);
                    BigDecimal priceReceiptDetail = denominateDivideType2(getDBFBigDecimalFieldValue(importSailFile, "PRICERUB", defaultCharset), denominationStage);
                    BigDecimal sumReceiptDetail = denominateDivideType2(getDBFBigDecimalFieldValue(importSailFile, "TOTALRUB", defaultCharset), denominationStage);
                    BigDecimal discountSumReceiptDetail = discountMap.get(numberCashRegister + "_" + zNumber + "_" + receiptNumber + "_" + numberReceiptDetail);
                    String discountCardNumber = discountCardMap.get(numberCashRegister + "_" + zNumber + "_" + receiptNumber);

                    BigDecimal[] tempSumReceipt = receiptNumberSumReceipt.get(receiptNumber);
                    BigDecimal tempSum1 = tempSumReceipt != null ? tempSumReceipt[0] : null;
                    BigDecimal tempSum2 = tempSumReceipt != null ? tempSumReceipt[1] : null;
                    receiptNumberSumReceipt.put(receiptNumber, new BigDecimal[]{HandlerUtils.safeAdd(tempSum1, (operation <= 1 ? sumReceiptDetail : null)),
                            HandlerUtils.safeAdd(tempSum2, (operation > 1 ? sumReceiptDetail : null))});

                    salesInfoList.add(new SalesInfo(false, numberGroup, Integer.parseInt(numberCashRegister), zNumber,
                            date, time, receiptNumber, date, time, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, barcodeReceiptDetail,
                            null, null, null, operation % 2 == 1 ? quantityReceiptDetail : quantityReceiptDetail.negate(),
                            priceReceiptDetail,
                            operation % 2 == 1 ? sumReceiptDetail : sumReceiptDetail.negate(),
                            discountSumReceiptDetail, null, discountCardNumber, numberReceiptDetail, null, null));
                }
                for (SalesInfo salesInfo : salesInfoList) {
                    salesInfo.sumCash = receiptNumberSumReceipt.get(salesInfo.numberReceipt)[0];
                    salesInfo.sumCard = receiptNumberSumReceipt.get(salesInfo.numberReceipt)[1];
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
        return new UKM4SalesBatch(salesInfoList, readFiles);
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList, Set<String> directorySet,
                                 Set<Integer> succeededRequests, Map<Integer, String> failedRequests, Map<Integer, String> ignoredRequests) throws IOException, ParseException {
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
    public List<List<Object>> checkZReportSum(Map<String, List<Object>> zReportSumMap, List<List<Object>> cashRegisterList) throws ClassNotFoundException, SQLException {
        return null;
    }

    @Override
    public Map<String, List<Object>> readExtraCheckZReport(List<CashRegisterInfo> cashRegisterInfoList) throws ClassNotFoundException, SQLException {
        return null;
    }

    @Override
    public ExtraCheckZReportBatch compareExtraCheckZReport(Map<String, List<Object>> handlerZReportSumMap, Map<String, BigDecimal> baseZReportSumMap) throws ClassNotFoundException, SQLException {
        return null;
    }
    
    protected Long parseGroup(String idItemGroup) {
        return idItemGroup == null ? 0 : Long.parseLong(idItemGroup.equals("Все") ? "0" : idItemGroup.replace("_", ""));
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
        return (result == null || result.isEmpty() || (zeroIsNull && Double.valueOf(result).equals(0d))) ? null : new BigDecimal(result.replace(",", "."));
    }

    protected Integer getDBFIntegerFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException {
        return getDBFIntegerFieldValue(importFile, fieldName, charset, false, null);
    }
    
    protected Integer getDBFIntegerFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull, String defaultValue) throws UnsupportedEncodingException {
        String result = getDBFFieldValue(importFile, fieldName, charset, zeroIsNull, defaultValue);
        return (result == null || (zeroIsNull && Double.valueOf(result).equals(0d))) ? null : new Double(result).intValue();
    }

    protected Date getDBFDateFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException, ParseException {
        return getDBFDateFieldValue(importFile, fieldName, charset, null);
    }
    
    protected Date getDBFDateFieldValue(DBF importFile, String fieldName, String charset, Date defaultValue) throws UnsupportedEncodingException, ParseException {
        String dateString = getDBFFieldValue(importFile, fieldName, charset, false, "");
        return dateString.isEmpty() ? defaultValue : new Date(DateUtils.parseDate(dateString, new String[]{"yyyyMMdd", "dd.MM.yyyy"}).getTime());
    }
}
