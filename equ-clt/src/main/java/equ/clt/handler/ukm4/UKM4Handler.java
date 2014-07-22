package equ.clt.handler.ukm4;

import equ.api.*;
import equ.api.cashregister.*;
import org.apache.commons.lang.time.DateUtils;
import org.xBaseJ.DBF;
import org.xBaseJ.Util;
import org.xBaseJ.fields.CharField;
import org.xBaseJ.fields.DateField;
import org.xBaseJ.fields.Field;
import org.xBaseJ.fields.NumField;
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

        DBF fileBar = null;
        DBF fileClassif = null;
        DBF filePlucash = null;
        DBF filePlulim = null;

        try {
            NumField BARCODE = new NumField("BARCODE", 10, 0);
            NumField CARDARTICU = new NumField("CARDARTICU", 14, 0);
            CharField CARDSIZE = new CharField("CARDSIZE", 20);
            NumField QUANTITY = new NumField("QUANTITY", 10, 0);

            NumField ARTICUL = new NumField("ARTICUL", 10, 0);
            CharField NAME = new CharField("NAME", 50);
            CharField MESURIMENT = new CharField("MESURIMENT", 2);
            NumField MESPRESISI = new NumField("MESPRESISI", 10, 0);
            CharField ADD1 = new CharField("ADD1", 10);
            CharField ADD2 = new CharField("ADD2", 10);
            CharField ADD3 = new CharField("ADD3", 10);
            CharField ADDNUM1 = new CharField("ADDNUM1", 10);
            CharField ADDNUM2 = new CharField("ADDNUM2", 10);
            CharField ADDNUM3 = new CharField("ADDNUM3", 10);
            CharField SCALE = new CharField("SCALE", 20);
            NumField GROOP1 = new NumField("GROOP1", 10, 0);
            NumField GROOP2 = new NumField("GROOP2", 10, 0);
            NumField GROOP3 = new NumField("GROOP3", 10, 0);
            NumField GROOP4 = new NumField("GROOP4", 10, 0);
            NumField GROOP5 = new NumField("GROOP5", 10, 0);
            NumField PRICERUB = new NumField("PRICERUB", 10, 0);
            CharField PRICECUR = new CharField("PRICECUR", 10);
            NumField CLIENTINDE = new NumField("CLIENTINDE", 10, 0);
            CharField COMMENTARY = new CharField("COMMENTARY", 50);
            NumField DELETED = new NumField("DELETED", 1, 0);
            DateField MODDATE = new DateField("MODDATE");
            CharField MODTIME = new CharField("MODTIME", 20);
            CharField MODPERSONI = new CharField("MODPERSONI", 50);

            NumField PERCENT = new NumField("PERCENT", 5, 0);

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
                folder = new File(directory.trim() + "/tovar");
                if (!folder.exists() && !folder.mkdir())
                    throw new RuntimeException("The folder " + folder.getAbsolutePath() + " can not be created");

                Util.setxBaseJProperty("ignoreMissingMDX", "true");

                String path = directory + "/tovar/BAR.DBF";
                fileBar = new DBF(path, DBF.DBASEIV, true, "CP866");
                fileBar.addField(new Field[]{BARCODE, CARDARTICU, CARDSIZE, QUANTITY});

                for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                    BARCODE.put(item.idBarcode);
                    CARDARTICU.put(item.idBarcode); //или что туда надо писать?
                    CARDSIZE.put("NOSIZE");
                    QUANTITY.put(1); //без разницы, что писать в количество?
                    fileBar.write();
                    fileBar.file.setLength(fileBar.file.length() - 1);
                }


                path = directory + "/tovar/CLASSIF.DBF";
                fileClassif = new DBF(path, DBF.DBASEIV, true, "CP866");
                fileClassif.addField(new Field[]{GROOP1, GROOP2, GROOP3, GROOP4, GROOP5, NAME});

                for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                    NAME.put(item.name.substring(0, Math.min(item.name.length(), 50)));
                    int size = item.hierarchyItemGroup.size();
                    GROOP1.put(size >= 1 ? item.hierarchyItemGroup.get(item.hierarchyItemGroup.size() - 1).idItemGroup : "0");
                    GROOP2.put(size >= 2 ? item.hierarchyItemGroup.get(item.hierarchyItemGroup.size() - 2).idItemGroup : "0");
                    GROOP3.put(size >= 3 ? item.hierarchyItemGroup.get(item.hierarchyItemGroup.size() - 3).idItemGroup : "0");
                    GROOP4.put(size >= 4 ? item.hierarchyItemGroup.get(item.hierarchyItemGroup.size() - 4).idItemGroup : "0");
                    GROOP5.put(size >= 5 ? item.hierarchyItemGroup.get(item.hierarchyItemGroup.size() - 5).idItemGroup : "0");
                    fileClassif.write();
                    fileClassif.file.setLength(fileClassif.file.length() - 1);
                }

                path = directory + "/tovar/PLUCASH.DBF";
                filePlucash = new DBF(path, DBF.DBASEIV, true, "CP866");
                filePlucash.addField(new Field[]{ARTICUL, NAME, MESURIMENT, MESPRESISI, ADD1, ADD2, ADD3, ADDNUM1,
                        ADDNUM2, ADDNUM3, SCALE, GROOP1, GROOP2, GROOP3, GROOP4, GROOP5, PRICERUB, PRICECUR,
                        CLIENTINDE, COMMENTARY, DELETED, MODDATE, MODTIME, MODPERSONI
                });

                for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                    ARTICUL.put(item.idBarcode);
                    NAME.put(item.name.substring(0, Math.min(item.name.length(), 50)));
                    MESURIMENT.put(item.isWeightItem ? "кг" : "1");
                    MESPRESISI.put(item.isWeightItem ? 0.001 : 1.000);
                    SCALE.put("NOSIZE");
                    int size = item.hierarchyItemGroup.size();
                    GROOP1.put(size >= 1 ? item.hierarchyItemGroup.get(item.hierarchyItemGroup.size() - 1).idItemGroup : "0");
                    GROOP2.put(size >= 2 ? item.hierarchyItemGroup.get(item.hierarchyItemGroup.size() - 2).idItemGroup : "0");
                    GROOP3.put(size >= 3 ? item.hierarchyItemGroup.get(item.hierarchyItemGroup.size() - 3).idItemGroup : "0");
                    GROOP4.put(size >= 4 ? item.hierarchyItemGroup.get(item.hierarchyItemGroup.size() - 4).idItemGroup : "0");
                    GROOP5.put(size >= 5 ? item.hierarchyItemGroup.get(item.hierarchyItemGroup.size() - 5).idItemGroup : "0");
                    PRICERUB.put(item.price.doubleValue());
                    CLIENTINDE.put(0);
                    DELETED.put(1);
                    MODDATE.put(new GregorianCalendar(transactionInfo.date.getYear() + 1900, transactionInfo.date.getMonth() + 1, transactionInfo.date.getDay()));/*transactionInfo.date*/
                    filePlucash.write();
                    filePlucash.file.setLength(filePlucash.file.length() - 1);
                }

                path = directory + "/tovar/PLULIM.DBF";
                filePlulim = new DBF(path, DBF.DBASEIV, true, "CP866");
                filePlulim.addField(new Field[]{CARDARTICU, PERCENT});

                for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                    CARDARTICU.put(item.idBarcode);
                    PERCENT.put(0); //откуда брать макс. процент скидки?
                    filePlulim.write();
                    filePlulim.file.setLength(filePlulim.file.length() - 1);
                }

            }
        } catch (xBaseJException e) {
            throw new RuntimeException(e.toString(), e.getCause());
        } finally {
            if (fileBar != null)
                fileBar.close();
            if (filePlucash != null)
                filePlucash.close();
            if (filePlulim != null)
                filePlulim.close();
            if (fileClassif != null)
                fileClassif.close();
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
    public String checkZReportSum(Map<String, BigDecimal> zReportSumMap, String idStock) throws ClassNotFoundException, SQLException {
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
