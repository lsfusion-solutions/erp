package equ.clt.handler.belcoopsoyuz;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import net.iryndin.jdbf.core.DbfRecord;
import net.iryndin.jdbf.reader.DbfReader;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.util.FileCopyUtils;
import org.xBaseJ.DBF;
import org.xBaseJ.fields.CharField;
import org.xBaseJ.fields.Field;
import org.xBaseJ.xBaseJException;

import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class BelCoopSoyuzHandler extends CashRegisterHandler<BelCoopSoyuzSalesBatch> {

    private FileSystemXmlApplicationContext springContext;

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");

    String charset = "cp1251";

    public BelCoopSoyuzHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        return "belcoopsoyuz" + transactionInfo.nameGroupMachinery;
    }

    @Override
    public Map<Integer, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) throws IOException {

        Map<Integer, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        Map<String, Exception> brokenDirectoriesMap = new HashMap<>();
        for (TransactionCashRegisterInfo transaction : transactionList) {

            Exception exception = null;
            try {

                if (transaction.itemsList.isEmpty()) {
                    processTransactionLogger.info(String.format("BelCoopSoyuz: Transaction # %s has no items", transaction.id));
                } else {
                    processTransactionLogger.info(String.format("BelCoopSoyuz: Send Transaction # %s", transaction.id));

                    Map<String, CashRegisterInfo> directoryMap = new HashMap<>();
                    for (CashRegisterInfo cashRegister : transaction.machineryInfoList) {
                        if(cashRegister.directory != null) {
                            String directory = cashRegister.directory.trim();
                            directoryMap.put(directory, cashRegister);
                        }
                    }

                    try {

                        CharField CEUNIKEY = new CharField("CEUNIKEY", 25);
                        CharField CEDOCCOD = new CharField("CEDOCCOD", 25);
                        CharField CEOBIDE = new CharField("CEOBIDE", 25);
                        CharField CEOBMEA = new CharField("CEOBMEA", 25);
                        CharField MEOBNAM = new CharField("MEOBNAM", 100);
                        NumField2 NEOBFREE = new NumField2("NEOBFREE", 18, 6);
                        NumField2 NEOPLOS = new NumField2("NEOPLOS", 18, 6);
                        NumField2 NERECOST = new NumField2("NERECOST", 18, 6);
                        CharField CEOPCURO = new CharField("CEOPCURO", 25);
                        NumField2 NEOPPRIC = new NumField2("NEOPPRIC", 18, 2);
                        NumField2 NEOPPRIE = new NumField2("NEOPPRIE", 18, 2);
                        NumField2 NEOPNDS = new NumField2("NEOPNDS", 6, 2);
                        CharField CECUCOD = new CharField("CECUCOD", 25);
                        CharField FORMAT = new CharField("FORMAT", 10);

                        File cachedPriceFile = null;
                        File cachedPriceMdxFile = null;

                        List<List<Object>> waitList = new ArrayList<>();
                        for (Map.Entry<String, CashRegisterInfo> entry : directoryMap.entrySet()) {

                            String directory = entry.getKey();
                            if (brokenDirectoriesMap.containsKey(directory)) {
                                exception = brokenDirectoriesMap.get(directory);
                            } else {
                                if (new File(directory).exists() || new File(directory).mkdirs()) {
                                    String fileName = "a9sk34lsf";
                                    processTransactionLogger.info(String.format("BelCoopSoyuz: Transaction # %s creating %s file", transaction.id, directory + "/" + fileName));
                                    File priceFile = new File(directory + "/" + fileName + ".dbf");
                                    File flagPriceFile = new File(directory + "/" + fileName + ".lsf");

                                    if (priceFile.exists() && priceFile.length() == 0)
                                        safeFileDelete(priceFile, processTransactionLogger);

                                    boolean append = !transaction.snapshot && priceFile.exists();

                                    DBF dbfFile = null;
                                    try {
                                        if (append || cachedPriceFile == null) {

                                            if (append) {
                                                dbfFile = new DBF(priceFile.getAbsolutePath(), charset);
                                            } else {
                                                cachedPriceFile = File.createTempFile("cachedPrice", ".dbf");
                                                cachedPriceMdxFile = new File(cachedPriceFile.getAbsolutePath().replace(".dbf", ".mdx"));
                                                dbfFile = new DBF(cachedPriceFile.getAbsolutePath(), DBF.DBASEIV, true, charset);
                                                if (!append)
                                                    dbfFile.addField(new Field[]{CEUNIKEY, CEDOCCOD, CEOBIDE, CEOBMEA, MEOBNAM,
                                                            NEOBFREE, NEOPLOS, NERECOST, CEOPCURO, NEOPPRIC, NEOPPRIE, NEOPNDS, CECUCOD});
                                            }

                                            Set<String> usedBarcodes = new HashSet<>();
                                            Map<String, Integer> barcodeRecordMap = new HashMap<>();
                                            for (int i = 1; i <= dbfFile.getRecordCount(); i++) {
                                                dbfFile.read();
                                                String barcode = getDBFFieldValue(dbfFile, "CEUNIKEY", charset);
                                                barcodeRecordMap.put(barcode, i);
                                            }
                                            dbfFile.startTop();

                                            putField(dbfFile, CEDOCCOD, "Прайс-лист", 25, append); //константа
                                            putNumField(dbfFile, NEOPLOS, -1, append); //остаток не контролируется
                                            putField(dbfFile, NEOBFREE, "9999", 25, append); //остаток
                                            putField(dbfFile, CECUCOD, entry.getValue().idStock, 25, append); //секция, "600358416 MF"
                                            putField(dbfFile, CEOPCURO, "BYR 974 1", 25, append); //валюта
                                            for (CashRegisterItemInfo item : transaction.itemsList) {
                                                if (!Thread.currentThread().isInterrupted()) {

                                                    String barcode = appendBarcode(item.idBarcode);
                                                    if (!usedBarcodes.contains(barcode)) {
                                                        Integer recordNumber = null;
                                                        if (append) {
                                                            recordNumber = barcodeRecordMap.get(barcode);
                                                            if (recordNumber != null)
                                                                dbfFile.gotoRecord(recordNumber);
                                                        }

                                                        putField(dbfFile, CEUNIKEY, barcode, 25, append);
                                                        putField(dbfFile, CEOBIDE, barcode, 25, append);
                                                        putField(dbfFile, CEOBMEA, item.shortNameUOM, 25, append);
                                                        putField(dbfFile, MEOBNAM, trim(item.name, 100), 25, append);
                                                        BigDecimal price = /*denominateMultiplyType2(*/item.price/*, transaction.denominationStage)*/;
                                                        putNumField(dbfFile, NERECOST, price, append);
                                                        putNumField(dbfFile, NEOPPRIC, price, append);
                                                        putNumField(dbfFile, NEOPPRIE, price, append);
                                                        putNumField(dbfFile, NEOPNDS, item.vat, append);
                                                        putField(dbfFile, FORMAT, item.splitItem ? "999.999" : "999", 10, append);

                                                        if (recordNumber != null)
                                                            dbfFile.update();
                                                        else {
                                                            dbfFile.write();
                                                            dbfFile.file.setLength(dbfFile.file.length() - 1);
                                                            if (append)
                                                                barcodeRecordMap.put(barcode, barcodeRecordMap.size() + 1);
                                                        }
                                                        usedBarcodes.add(barcode);
                                                    }
                                                }
                                            }

                                            putField(dbfFile, CEUNIKEY, null, 25, append);
                                            putField(dbfFile, CEDOCCOD, null, 25, append);
                                            putField(dbfFile, CEOBIDE, null, 25, append);
                                            putField(dbfFile, CEOBMEA, null, 25, append);
                                            putField(dbfFile, MEOBNAM, null, 100, append);
                                            putNumField(dbfFile, NEOBFREE, null, append);
                                            putNumField(dbfFile, NEOPLOS, 25, append);
                                            putNumField(dbfFile, NERECOST, 25, append);
                                            putField(dbfFile, CEOPCURO, null, 25, append);
                                            putNumField(dbfFile, NEOPPRIC, 25, append);
                                            putNumField(dbfFile, NEOPPRIE, 25, append);
                                            putNumField(dbfFile, NEOPNDS, 25, append);
                                            putField(dbfFile, CECUCOD, null, 25, append);
                                            putField(dbfFile, FORMAT, null, 10, append);
                                        }

                                        try {
                                            if (!append) {
                                                processTransactionLogger.info(String.format("BelCoopSoyuz: Transaction # %s copying %s file", transaction.id, directory + "/" + fileName));
                                                FileCopyUtils.copy(cachedPriceFile, priceFile);
                                                processTransactionLogger.info(String.format("BelCoopSoyuz: Transaction # %s finished copying %s file", transaction.id, directory + "/" + fileName));
                                            }
                                            if (flagPriceFile.createNewFile())
                                                waitList.add(Arrays.<Object>asList(priceFile, flagPriceFile, directory, entry.getValue()));
                                            else {
                                                processTransactionLogger.error("BelCoopSoyuz: error while create flag file " + flagPriceFile.getAbsolutePath());
                                            }
                                        } catch (IOException e) {
                                            brokenDirectoriesMap.put(priceFile.getParent(), e);
                                            exception = e;
                                            processTransactionLogger.error("BelCoopSoyuz: error while create files", e);
                                        }
                                    } finally {
                                        if (dbfFile != null)
                                            dbfFile.close();
                                    }
                                } else {
                                    processTransactionLogger.error("BelCoopSoyuz: error while create files: unable to create dir " + directory);
                                }
                            }
                        }
                        processTransactionLogger.info(String.format("BelCoopSoyuz: Transaction # %s wait for deletion", transaction.id));
                        Exception deletionException = waitForDeletion(waitList, brokenDirectoriesMap);
                        processTransactionLogger.info(String.format("BelCoopSoyuz: Transaction # %s end waiting for deletion", transaction.id));
                        exception = exception == null ? deletionException : exception;

                        safeFileDelete(cachedPriceFile, processTransactionLogger);
                        safeFileDelete(cachedPriceMdxFile, processTransactionLogger);

                    } catch (xBaseJException e) {
                        throw Throwables.propagate(e);
                    }
                }
            } catch (Exception e) {
                exception = e;
            }
            sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(exception));
        }
        return sendTransactionBatchMap;
    }

    @Override
    public void sendDiscountCardList(List<DiscountCard> discountCardList, Date startDate, Set<String> directorySet) throws IOException {
    }

    @Override
    public void sendPromotionInfo(PromotionInfo promotionInfo, Set<String> directorySet) throws IOException {
    }

    @Override
    public void sendCashierInfoList(List<CashierInfo> cashierInfoList, Map<String, Set<String>> directoryStockMap) throws IOException {
    }

    @Override
    public List<CashierTime> requestCashierTime(List<MachineryInfo> cashRegisterInfoList) throws IOException, ClassNotFoundException, SQLException {
        return null;
    }

    private void putField(DBF dbfFile, Field field, String value, int length, boolean append) throws xBaseJException {
        value = value == null ? "null" : value;
        while (value.length() < length)
            value += " ";
        if (append)
            dbfFile.getField(field.getName()).put(value);
        else
            field.put(value);
    }

    private void putNumField(DBF dbfFile, NumField2 field, BigDecimal value, boolean append) throws xBaseJException {
        if(value != null)
            putNumField(dbfFile, field, value.doubleValue(), append);
    }

    private void putNumField(DBF dbfFile, NumField2 field, double value, boolean append) throws xBaseJException {
        if (append)
            ((NumField2) dbfFile.getField(field.getName())).put(value);
        else
            field.put(value);
    }

    private Exception waitForDeletion(List<List<Object>> waitList, Map<String, Exception> brokenDirectoriesMap) {
        int count = 0;
        while (!Thread.currentThread().isInterrupted() && !waitList.isEmpty()) {
            try {
                List<List<Object>> nextWaitList = new ArrayList<>();
                count++;
                if (count >= 120) {
                    break;
                }
                for (List<Object> waitEntry : waitList) {
                    File file = (File) waitEntry.get(0);
                    File flagFile = (File) waitEntry.get(1);
                    String directory = (String) waitEntry.get(2);
                    if (flagFile.exists() || file.exists())
                        nextWaitList.add(Arrays.<Object>asList(file, flagFile, directory));
                }
                waitList = nextWaitList;
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }

        String exception = waitList.isEmpty() ? null : "BelCoopSoyuz: files has been created but not processed by cash register machine: ";
        for (List<Object> waitEntry : waitList) {
            File file = (File) waitEntry.get(0);
            File flagFile = (File) waitEntry.get(1);
            String directory = (String) waitEntry.get(2);
            if (file.exists())
                exception += file.getAbsolutePath() + "; ";
            if (flagFile.exists())
                exception += flagFile.getAbsolutePath() + "; ";
            brokenDirectoriesMap.put(directory, new RuntimeException(exception));
        }
        return exception == null ? null : new RuntimeException(exception);
    }

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {
    }

    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet) throws ClassNotFoundException {
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

    @Override
    public void finishReadingCashDocumentInfo(CashDocumentBatch cashDocumentBatch) {
    }

    @Override
    public Map<String, Timestamp> requestSucceededSoftCheckInfo(Set<String> directorySet) throws ClassNotFoundException, SQLException {
        return null;
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) throws IOException {

    }

    @Override
    public SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException, ClassNotFoundException {

        Map<String, CashRegisterInfo> sectionCashRegisterMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (c.idStock != null && c.handlerModel != null && c.handlerModel.endsWith("BelCoopSoyuzHandler")) {
                sectionCashRegisterMap.put(c.idStock, c);
            }
        }

        List<SalesInfo> salesInfoList = new ArrayList<>();
        List<String> filePathList = new ArrayList<>();

        String timestamp = getCurrentTimestamp();
        String filename = "a9ck07lsf";
        File remoteSalesFile = new File(directory + "/" + filename + ".dbf");
        File audFlagFile = new File(directory + "/" + filename + ".lsf");
        File lsfFlagFile = new File(directory + "/" + timestamp + "-" + filename + ".lsf");

        try {
            if (!remoteSalesFile.exists()) {
                sendSalesLogger.info(String.format("BelCoopSoyuz: %s.dbf not found in %s", filename, directory));
            } else if (audFlagFile.exists()) {
                sendSalesLogger.info(String.format("BelCoopSoyuz: found %s.dbf with stop flag in %s", filename, directory));
            } else if (!lsfFlagFile.createNewFile()) {
                sendSalesLogger.info(String.format("BelCoopSoyuz: found %s.dbf in %s, lsf flag creation failed", filename, directory));
            } else {
                sendSalesLogger.info(String.format("BelCoopSoyuz: found %s.dbf in %s, lsf flag created", filename, directory));

                File salesFile = null;
                boolean copyError = false;
                try {
                    salesFile = File.createTempFile(filename, ".dbf");
                    sendSalesLogger.info(String.format("Start copying %s.dbf from %s to %s", filename, remoteSalesFile.getAbsolutePath(), salesFile.getAbsolutePath()));
                    FileCopyUtils.copy(remoteSalesFile, salesFile);
                    sendSalesLogger.info(String.format("End copying %s.dbf from %s to %s", filename, remoteSalesFile.getAbsolutePath(), salesFile.getAbsolutePath()));
                } catch (Exception e) {
                    copyError = true;
                    sendSalesLogger.error("File: " + remoteSalesFile.getAbsolutePath(), e);
                }

                try {
                    if (!copyError) {

                        //используем jdbf, а не xbasej, т.к. xbasej не умеет работать с foxpro файлами

                        Charset stringCharset = Charset.forName(charset);

                        InputStream dbf = new FileInputStream(salesFile);

                        try (DbfReader reader = new DbfReader(dbf)) {
                            DbfRecord rec;
                            Map<Integer, Integer> numberReceiptDetailMap = new HashMap<>();
                            List<SalesInfo> curSalesInfoList = new ArrayList<>();
                            while ((rec = reader.read()) != null) {
                                rec.setStringCharset(stringCharset);
                                if(!rec.isDeleted()) {

                                    Integer numberReceipt = getJDBFIntegerFieldValue(rec, "CEDOCCOD");
                                    Date dateReceipt = getJDBFDateFieldValue(rec, "TEDOCINS");
                                    Time timeReceipt = getJDBFTimeFieldValue(rec, "TEDOCINS");
                                    String barcodeItem = getJDBFFieldValue(rec, "CEOBIDE");
                                    String section = getJDBFFieldValue(rec, "CESUCOD");

                                    CashRegisterInfo cashRegister = sectionCashRegisterMap.get(section);
                                    Integer nppMachinery = cashRegister == null ? null : cashRegister.number;
                                    Integer nppGroupMachinery = cashRegister == null ? null : cashRegister.numberGroup;
                                    String denominationStage = cashRegister == null ? null : cashRegister.denominationStage;

                                    BigDecimal quantityReceiptDetail = getJDBFBigDecimalFieldValue(rec, "NEOPEXP");
                                    BigDecimal priceReceiptDetail = /*denominateDivideType2(*/getJDBFBigDecimalFieldValue(rec, "NEOPPRIC")/*, denominationStage)*/;
                                    BigDecimal sumReceiptDetail = /*denominateDivideType2(*/getJDBFBigDecimalFieldValue(rec, "NEOPSUMC")/*, denominationStage)*/;
                                    BigDecimal discountSum1ReceiptDetail = /*denominateDivideType2(*/getJDBFBigDecimalFieldValue(rec, "NEOPSDELC")/*, denominationStage)*/;
                                    BigDecimal discountSum2ReceiptDetail = /*denominateDivideType2(*/getJDBFBigDecimalFieldValue(rec, "NEOPPDELC")/*, denominationStage)*/;
                                    BigDecimal discountSumReceiptDetail = safeNegate(safeAdd(discountSum1ReceiptDetail, discountSum2ReceiptDetail));

                                    Integer numberReceiptDetail = numberReceiptDetailMap.get(numberReceipt);
                                    numberReceiptDetail = numberReceiptDetail == null ? 1 : (numberReceiptDetail + 1);
                                    numberReceiptDetailMap.put(numberReceipt, numberReceiptDetail);
                                    String numberZReport = getJDBFFieldValue(rec, "CEDOCNUM");

                                    String idEmployee = getJDBFFieldValue(rec, "CEOPDEV");
                                    String idSaleReceiptReceiptReturnDetail = getJDBFFieldValue(rec, "CEUNIREV");
                                    String idSection = getJDBFFieldValue(rec, "CESUCOD");

                                    String type = getJDBFFieldValue(rec, "CEOBTYP");
                                    if(type != null) {
                                        switch (type) {
                                            case "ТОВАР":
                                                curSalesInfoList.add(new SalesInfo(false, nppGroupMachinery, nppMachinery, numberZReport, dateReceipt, timeReceipt, numberReceipt, dateReceipt,
                                                        timeReceipt, idEmployee, null, null, null/*sumCard*/, null/*sumCash*/, null, barcodeItem, null, null, idSaleReceiptReceiptReturnDetail, quantityReceiptDetail,
                                                        priceReceiptDetail, sumReceiptDetail, discountSumReceiptDetail, null, null/*idDiscountCard*/, numberReceiptDetail, filename + "dbf", idSection));
                                                break;
                                            case "ТОВАР ВОЗВРАТ":
                                                curSalesInfoList.add(new SalesInfo(false, nppGroupMachinery, nppMachinery, numberZReport, dateReceipt, timeReceipt, numberReceipt, dateReceipt,
                                                        timeReceipt, idEmployee, null, null, null/*sumCard*/, null/*sumCash*/, null, barcodeItem, null, null, idSaleReceiptReceiptReturnDetail, safeNegate(quantityReceiptDetail),
                                                        priceReceiptDetail, safeNegate(sumReceiptDetail), discountSumReceiptDetail, null, null/*idDiscountCard*/, numberReceiptDetail, filename + "dbf", idSection));
                                                break;
                                            case "ВСЕГО":
                                                for(SalesInfo salesInfo : curSalesInfoList) {
                                                    salesInfo.sumCash = getJDBFBigDecimalFieldValue(rec, "NEOPSUMCT");
                                                    salesInfoList.add(salesInfo);
                                                }
                                                curSalesInfoList = new ArrayList<>();
                                                break;
                                            case "ВОЗВРАТ":
                                                for(SalesInfo salesInfo : curSalesInfoList) {
                                                    salesInfo.sumCash = safeNegate(getJDBFBigDecimalFieldValue(rec, "NEOPSUMCT"));
                                                    salesInfoList.add(salesInfo);
                                                }
                                                curSalesInfoList = new ArrayList<>();
                                                break;
                                        }
                                    }

                                }
                            }
                        }
                    }

                    if (salesFile != null) {
                        new File(directory + "/backup").mkdir();
                        File backupSalesFile = new File(directory + "/backup/" + filename + "-" + timestamp + ".dbf");
                        sendSalesLogger.info(String.format("Start copying %s.dbf from %s to %s", filename, salesFile.getAbsolutePath(), backupSalesFile.getAbsolutePath()));
                        FileCopyUtils.copy(salesFile, backupSalesFile);
                        sendSalesLogger.info(String.format("End copying %s.dbf from %s to %s", filename, salesFile.getAbsolutePath(), backupSalesFile.getAbsolutePath()));
                    }

                } catch (Throwable e) {
                    sendSalesLogger.error("File: " + remoteSalesFile.getAbsolutePath(), e);
                } finally {
                    if (!copyError) {
                        if(salesInfoList.isEmpty())
                            safeFileDelete(remoteSalesFile, sendSalesLogger);
                        else
                            filePathList.add(remoteSalesFile.getAbsolutePath());
                    }
                    safeFileDelete(salesFile, sendSalesLogger);
                }
            }
        } finally {
            safeFileDelete(lsfFlagFile, sendSalesLogger);
        }
        return (salesInfoList.isEmpty() && filePathList.isEmpty()) ? null :
                new BelCoopSoyuzSalesBatch(salesInfoList, filePathList);
    }

    @Override
    public void finishReadingSalesInfo(BelCoopSoyuzSalesBatch salesBatch) {
        sendSalesLogger.info("BelCoopSoyuz: Finish Reading started");
        for (String readFile : salesBatch.readFiles) {
            File f = new File(readFile);
            if (f.delete()) {
                sendSalesLogger.info("BelCoopSoyuz: file " + readFile + " has been deleted");
            } else {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
            }
        }
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList, Set<String> directorySet,
                                 Set<Integer> succeededRequests, Map<Integer, String> failedRequests, Map<Integer, String> ignoredRequests) throws IOException, ParseException {
    }

    protected BigDecimal safeAdd(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else return (operand1 == null ? operand2 : (operand2 == null ? operand1 : operand1.add(operand2)));
    }

    protected BigDecimal safeNegate(BigDecimal operand) {
        return operand == null ? null : operand.negate();
    }

    protected String trim(String input, Integer length) {
        return input == null ? null : (length == null || length >= input.trim().length() ? input.trim() : input.trim().substring(0, length));
    }

    public String appendBarcode(String barcode) {
        if (barcode == null || barcode.length() != 5) return barcode;
        barcode = "22" + barcode + "00000";
        try {
            int checkSum = 0;
            for (int i = 0; i <= 10; i = i + 2) {
                checkSum += Integer.valueOf(String.valueOf(barcode.charAt(i)));
                checkSum += Integer.valueOf(String.valueOf(barcode.charAt(i + 1))) * 3;
            }
            checkSum %= 10;
            if (checkSum != 0)
                checkSum = 10 - checkSum;
            return barcode.concat(String.valueOf(checkSum));
        } catch (Exception e) {
            return barcode;
        }
    }

    protected String getCurrentTimestamp() {
        return new SimpleDateFormat("dd-MM-yyyy-hh-mm-ss").format(Calendar.getInstance().getTime());
    }

    protected String getDBFFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException {
        try {
            String result = new String(importFile.getField(fieldName).getBytes(), charset).trim();
            return result.isEmpty() ? null : result;
        } catch (xBaseJException e) {
            return null;
        }
    }

    protected String getJDBFFieldValue(DbfRecord rec, String column) {
        return rec.getString(column);
    }

    protected BigDecimal getJDBFBigDecimalFieldValue(DbfRecord rec, String column) {
        return rec.getBigDecimal(column);
    }

    protected Integer getJDBFIntegerFieldValue(DbfRecord rec, String column) {
        Integer result;
        String value = rec.getString(column);
        try {
            result = value == null ? null : Integer.parseInt(value);
        } catch (Exception e) {
            result = null;
        }
        return result;
    }

    protected Date getJDBFDateFieldValue(DbfRecord rec, String column) {
        return new Date(convertFoxProDate(rec.getBytes(column)).getTime());
    }

    protected Time getJDBFTimeFieldValue(DbfRecord rec, String column) {
        return new Time(convertFoxProDate(rec.getBytes(column)).getTime());
    }

    public static Date convertFoxProDate( byte[] foxProDate ) {
        long BASE_FOXPRO_MILLIS = 210866803200000L;
        long DAY_TO_MILLIS_FACTOR = 24L * 60L * 60L * 1000L;

        if ( foxProDate.length != 8 ) {
            throw new IllegalArgumentException("FoxPro date must be 8 bytes long");
        }

        // FoxPro date is stored with bytes reversed.
        byte[] reversedBytes = new byte[8];
        for ( int i = 0;  i < 8; i++ ) {
            reversedBytes[i] = foxProDate[8 - i - 1];
        }

        // Grab the two integer fields from the byte array
        ByteBuffer buf = ByteBuffer.wrap(reversedBytes);

        long timeFieldMillis = buf.getInt();
        long dateFieldDays = buf.getInt();

        // Convert to Java date by converting days to milliseconds and adjusting to Java epoch.
        return new Date(dateFieldDays * DAY_TO_MILLIS_FACTOR - BASE_FOXPRO_MILLIS + timeFieldMillis);
    }


    private void safeFileDelete(File file, Logger logger) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
            logger.info(String.format("BelCoopSoyuz: cannot delete file %s, will try to deleteOnExit", file.getAbsolutePath()));
        }
    }
}
