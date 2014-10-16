package equ.clt.handler.htc;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import equ.api.*;
import equ.api.cashregister.*;
import equ.clt.EquipmentServer;
import org.apache.commons.lang.time.DateUtils;
import org.apache.log4j.Logger;
import org.springframework.util.FileCopyUtils;
import org.xBaseJ.DBF;
import org.xBaseJ.fields.CharField;
import org.xBaseJ.fields.Field;
import org.xBaseJ.fields.LogicalField;
import org.xBaseJ.fields.NumField;
import org.xBaseJ.xBaseJException;

import java.io.*;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class HTCHandler extends CashRegisterHandler<HTCSalesBatch> {

    protected final static Logger logger = Logger.getLogger(EquipmentServer.class);
    String charset = "cp866";

    public HTCHandler() {
    }

    @Override
    public List<MachineryInfo> sendTransaction(TransactionCashRegisterInfo transactionInfo, List<CashRegisterInfo> machineryInfoList) throws IOException {

        List<MachineryInfo> succeededMachineryInfoList = new ArrayList<MachineryInfo>();
        
        if (transactionInfo.itemsList.isEmpty()) {
            logger.info(String.format("HTC: Transaction # %s has no items", transactionInfo.id));
        } else {
            logger.info(String.format("HTC: Send Transaction # %s", transactionInfo.id));

            Map<String, List<CashRegisterInfo>> directoryMap = new HashMap<String, List<CashRegisterInfo>>();
            for (CashRegisterInfo cashRegisterInfo : machineryInfoList) {
                if (cashRegisterInfo.succeeded)
                    succeededMachineryInfoList.add(cashRegisterInfo);
                else if (cashRegisterInfo.directory != null) {
                    String directory = cashRegisterInfo.directory.trim();
                    List<CashRegisterInfo> cashRegisterInfoEntry = directoryMap.containsKey(directory) ? directoryMap.get(directory) : new ArrayList<CashRegisterInfo>();
                    cashRegisterInfoEntry.add(cashRegisterInfo);
                    directoryMap.put(directory, cashRegisterInfoEntry);
                }

            }

            try {

                NumField CODE = new NumField("CODE", 6, 0);
                NumField GROUP = new NumField("GROUP", 6, 0);
                LogicalField ISGROUP = new LogicalField("ISGROUP");
                CharField ARTICUL = new CharField("ARTICUL", 20);
                NumField BAR_CODE = new NumField("BAR_CODE", 13, 0);
                CharField PRODUCT_ID = new CharField("PRODUCT_ID", 64);
                CharField TABLO_ID = new CharField("TABLO_ID", 20);
                NumField PRICE = new NumField("PRICE", 10, 0);
                NumField QUANTITY = new NumField("QUANTITY", 10, 3);
                LogicalField WEIGHT = new LogicalField("WEIGHT");
                NumField SECTION = new NumField("SECTION", 1, 0);
                NumField FLAGS = new NumField("FLAGS", 3, 0);
                NumField CMD = new NumField("CMD", 2, 0);
                CharField UNIT = new CharField("UNIT", 20);

                File cachedPriceFile = null;
                File cachedPriceMdxFile = null;
                File cachedDiscFile = null;

                for (Map.Entry<String, List<CashRegisterInfo>> entry : directoryMap.entrySet()) {
                    
                    String directory = entry.getKey();
                    String fileName = transactionInfo.snapshot ? "NewPrice.dbf" : "UpdPrice.dbf";
                    logger.info(String.format("HTC: creating %s file", fileName));
                    File priceFile = new File(directory + "\\" + fileName);
                    File flagPriceFile = new File(directory + "\\price.qry");

                    boolean append = priceFile.exists();

                    if (append || cachedPriceFile == null) {

                        DBF dbfFile;
                        if (append) {
                            dbfFile = new DBF(priceFile.getAbsolutePath(), charset);
                        } else {
                            cachedPriceFile = new File(directory + "\\cachedPrice.dbf");
                            cachedPriceMdxFile = new File(directory + "\\cachedPrice.mdx");
                            dbfFile = new DBF(cachedPriceFile.getAbsolutePath(), DBF.DBASEIV, true, charset);
                            if (!append)
                                dbfFile.addField(new Field[]{CODE, GROUP, ISGROUP, ARTICUL, BAR_CODE, PRODUCT_ID, TABLO_ID, PRICE, QUANTITY, WEIGHT, SECTION, FLAGS, CMD, UNIT});
                        }
                        
                        Set<String> usedBarcodes = new HashSet<String>();
                        Map<String, Integer> barcodeRecordMap = new HashMap<String, Integer>();
                        for (int i = 1; i <= dbfFile.getRecordCount(); i++) {
                            dbfFile.read();
                            String barcode = getDBFFieldValue(dbfFile, "BAR_CODE", charset);
                            barcodeRecordMap.put(barcode, i);
                        }
                        dbfFile.startTop();

                        // item groups
                        putField(dbfFile, ISGROUP, "T", append);
                        for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                            if (item.idItemGroup != null) {
                                String parent = null;
                                for (ItemGroup itemGroup : Lists.reverse(transactionInfo.itemGroupMap.get(item.idItemGroup))) {
                                    String idItemGroup = (itemGroup.idItemGroup.equals("Все") ? "0" : itemGroup.idItemGroup.replace("_", ""));
                                    if (!usedBarcodes.contains(idItemGroup)) {
                                        Integer recordNumber = null;
                                        if (append) {
                                            recordNumber = barcodeRecordMap.get(idItemGroup);
                                            if (recordNumber != null)
                                                dbfFile.gotoRecord(recordNumber);
                                        }
                                        putField(dbfFile, CODE, trim(idItemGroup, 6), append);
                                        putField(dbfFile, GROUP, parent, append);
                                        putField(dbfFile, BAR_CODE, trim(idItemGroup, 13), append);
                                        putField(dbfFile, PRODUCT_ID, trim(itemGroup.nameItemGroup, 64), append);
                                        putField(dbfFile, TABLO_ID, trim(itemGroup.nameItemGroup, 20), append);
                                        putField(dbfFile, PRICE, null, append);
                                        putField(dbfFile, WEIGHT, "F", append);
                                        putField(dbfFile, FLAGS, "0", append);
                                        if (recordNumber != null)
                                            dbfFile.update();
                                        else {
                                            dbfFile.write();
                                            dbfFile.file.setLength(dbfFile.file.length() - 1);
                                            barcodeRecordMap.put(itemGroup.idItemGroup, barcodeRecordMap.size() + 1);
                                        }
                                        usedBarcodes.add(idItemGroup);
                                    }
                                    parent = trim(idItemGroup, 6);
                                }
                            }
                        }

                        //items

                        String lastCode = null;
                        String lastGroup = null;
                        String lastBarcode = null;
                        String lastName = null;
                        BigDecimal lastPrice = null;
                        Boolean lastSplitItem = null;
                        Integer lastFlags = null;
                        String lastUnit = null;

                        putField(dbfFile, ISGROUP, "F", append);
                        for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                            if (!usedBarcodes.contains(item.idBarcode)) {
                                Integer recordNumber = null;
                                if (append) {
                                    recordNumber = barcodeRecordMap.get(item.idBarcode);
                                    if (recordNumber != null)
                                        dbfFile.gotoRecord(recordNumber);
                                }

                                boolean isWeight = item.idBarcode != null && item.idBarcode.length() <= 5;
                                String code = isWeight ? item.idBarcode : "999999";
                                if (lastCode == null || !lastCode.equals(code)) {
                                    putField(dbfFile, CODE, code, append);
                                    lastCode = code;
                                }

                                String group = item.idItemGroup == null ? null : trim(item.idItemGroup.replace("_", ""), 6);
                                if (lastGroup == null || !lastGroup.equals(group)) {
                                    putField(dbfFile, GROUP, group, append);
                                    lastGroup = group;
                                }

                                if (lastBarcode == null || !lastBarcode.equals(item.idBarcode)) {
                                    putField(dbfFile, BAR_CODE, item.idBarcode, append);
                                    lastBarcode = item.idBarcode;
                                }

                                if (lastName == null || !lastName.equals(item.name)) {
                                    putField(dbfFile, PRODUCT_ID, trim(item.name, 64), append);
                                    putField(dbfFile, TABLO_ID, trim(item.name, 20), append);
                                    lastName = item.name;
                                }

                                if (lastPrice == null || !lastPrice.equals(item.price)) {
                                    putField(dbfFile, PRICE, String.valueOf(item.price.intValue()), append);
                                    lastPrice = item.price;
                                }

                                if (lastSplitItem == null || !lastSplitItem.equals(item.splitItem)) {
                                    putField(dbfFile, WEIGHT, item.splitItem ? "T" : "F", append);
                                    lastSplitItem = item.splitItem;
                                }

                                Integer flags = (item.notPromotionItem ? 0 : 248) + (item.splitItem ? 1 : 0);
                                if (lastFlags == null || !lastFlags.equals(flags)) {
                                    putField(dbfFile, FLAGS, String.valueOf(flags), append);
                                    lastFlags = flags;
                                }

                                if (lastUnit == null || !lastUnit.equals(item.shortNameUOM)) {
                                    putField(dbfFile, UNIT, item.shortNameUOM, append);
                                    lastUnit = item.shortNameUOM;
                                }

                                if (recordNumber != null)
                                    dbfFile.update();
                                else {
                                    dbfFile.write();
                                    dbfFile.file.setLength(dbfFile.file.length() - 1);
                                    if (append)
                                        barcodeRecordMap.put(item.idBarcode, barcodeRecordMap.size() + 1);
                                }
                                usedBarcodes.add(item.idBarcode);
                            }
                        }

                        putField(dbfFile, CODE, null, append);
                        putField(dbfFile, GROUP, null, append);
                        putField(dbfFile, ARTICUL, null, append);
                        putField(dbfFile, BAR_CODE, null, append);
                        putField(dbfFile, PRODUCT_ID, null, append);
                        putField(dbfFile, TABLO_ID, null, append);
                        putField(dbfFile, PRICE, null, append);
                        putField(dbfFile, QUANTITY, null, append);
                        putField(dbfFile, SECTION, null, append);
                        putField(dbfFile, FLAGS, null, append);
                        putField(dbfFile, CMD, null, append);
                        putField(dbfFile, UNIT, null, append);
                        
                        dbfFile.close();
                    }

                    if (transactionInfo.snapshot && transactionInfo.discountCardList != null)
                        cachedDiscFile = createDiscountCardFile(transactionInfo.discountCardList, directory, cachedDiscFile);
                    
                    if(!append)
                        FileCopyUtils.copy(cachedPriceFile, priceFile);
                    flagPriceFile.createNewFile();
                    logger.info("HTC: waiting for deletion of price.qry file");
                    if(waitForDeletion(priceFile, flagPriceFile))                 
                        succeededMachineryInfoList.addAll(entry.getValue());
                }
                
                if(cachedPriceFile != null)
                    cachedPriceFile.delete();
                if(cachedPriceMdxFile != null)
                    cachedPriceMdxFile.delete();
                
            } catch (xBaseJException e) {
                throw Throwables.propagate(e);
            }
        }
        return succeededMachineryInfoList;
    }

    private File createDiscountCardFile(List<DiscountCard> discountCardList, String directory, File cachedDiscFile) throws IOException, xBaseJException {

        File discountCardFile = new File(directory + "\\Discnew.dbf");
        if (cachedDiscFile != null) {
            FileCopyUtils.copy(cachedDiscFile, discountCardFile);
        } else {
            CharField DISC = new CharField("DISC", 32);
            CharField NAME = new CharField("NAME", 40);
            NumField PERCENT = new NumField("PERCENT", 6, 2);
            LogicalField ISSTOP = new LogicalField("ISSTOP");

            DBF dbfFile = new DBF(discountCardFile.getAbsolutePath(), DBF.DBASEIV, true, charset);
            dbfFile.addField(new Field[]{DISC, NAME, PERCENT, ISSTOP});

            String lastName = null;
            BigDecimal lastPercent = null;
            for (DiscountCard discountCard : discountCardList) {
                putField(dbfFile, DISC, discountCard.numberDiscountCard, false);
                
                if(lastName == null || !lastName.equals(discountCard.nameDiscountCard)) {
                    putField(dbfFile, NAME, discountCard.nameDiscountCard == null ? "" : discountCard.nameDiscountCard, false);
                    lastName = discountCard.nameDiscountCard;
                }
                
                if(lastPercent == null || !lastPercent.equals(discountCard.percentDiscountCard)) {
                    putField(dbfFile, PERCENT, String.valueOf(discountCard.percentDiscountCard), false);
                    lastPercent = discountCard.percentDiscountCard;
                }
                dbfFile.write();
            }
            dbfFile.close();
        }
        new File(directory + "\\Discnew.mdx").delete();
        File discountFlag = new File(directory + "\\TMC.dcn");
        discountFlag.createNewFile();
        return cachedDiscFile==null ? discountCardFile : cachedDiscFile;
    }

    private void putField(DBF dbfFile, Field field, String value, boolean append) throws xBaseJException {
        if(append)
            dbfFile.getField(field.getName()).put(value == null ? "null" : value);
        else
            field.put(value == null ? "null" : value);
    }
    
    private void putField(DBF dbfFile, String field, String value) throws xBaseJException {
        dbfFile.getField(field).put(value == null ? "null" : value);
    }

    private boolean waitForDeletion(File file, File flagFile) {
        int count = 0;
        while ((flagFile.exists() || file.exists()) && count < 10) {
            try {
                Thread.sleep(1000);
                count++;
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }
        return count < 10;
    }

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {
    }

    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet) throws ClassNotFoundException {
        return null;
    }

    @Override
    public String checkZReportSum(Map<String, BigDecimal> zReportSumMap, List<String> idCashRegisterList) throws ClassNotFoundException, SQLException {
        return null;
    }

    @Override
    public ExtraCheckZReportBatch extraCheckZReportSum(List<CashRegisterInfo> cashRegisterInfoList, Map<String, BigDecimal> zReportSumMap) throws ClassNotFoundException, SQLException {
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
    public SalesBatch readSalesInfo(List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException, ClassNotFoundException {

        Set<String> directorySet = new HashSet<String>();
        Map<String, Integer> directoryCashRegisterMap = new HashMap<String, Integer>();
        Map<String, Integer> directoryGroupCashRegisterMap = new HashMap<String, Integer>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (c.handlerModel != null && c.directory != null && c.handlerModel.endsWith("HTCHandler")) {
                directorySet.add(c.directory);
                if (c.number != null)
                    directoryCashRegisterMap.put(c.directory, c.number);
                if (c.numberGroup != null)
                    directoryGroupCashRegisterMap.put(c.directory, c.numberGroup);
            }
        }

        List<SalesInfo> salesInfoList = new ArrayList<SalesInfo>();
        List<String> filePathList = new ArrayList<String>();
        for (String directory : directorySet) {

            File salesFile = new File(directory + "\\Sales.dbf");
            File receiptFile = new File(directory + "\\Receipt.dbf");

            if (!salesFile.exists() || !receiptFile.exists())
                logger.info("HTC: No sale or receipt file found in " + directory);
            else {
                logger.info("HTC: found sale and receipt files in " + directory);

                try {

                    Map<Integer, List<Object>> receiptMap = new HashMap<Integer, List<Object>>();
                    DBF receiptDBFFile = new DBF(receiptFile.getAbsolutePath());
                    int receiptRecordCount = receiptDBFFile.getRecordCount();
                    for (int i = 0; i < receiptRecordCount; i++) {
                        receiptDBFFile.read();
                        Integer numberReceipt = getDBFIntegerFieldValue(receiptDBFFile, "NUMDOC", charset);
                        BigDecimal sumCash = getDBFBigDecimalFieldValue(receiptDBFFile, "COST1", charset);
                        BigDecimal sumCard = getDBFBigDecimalFieldValue(receiptDBFFile, "COST2", charset);
                        receiptMap.put(numberReceipt, Arrays.asList((Object) sumCash, sumCard));

                    }
                    receiptDBFFile.close();

                    DBF dbfFile = new DBF(salesFile.getAbsolutePath());
                    int recordCount = dbfFile.getRecordCount();

                    Map<Integer, Integer> numberReceiptDetailMap = new HashMap<Integer, Integer>();
                    for (int i = 0; i < recordCount; i++) {

                        dbfFile.read();
                        Integer numberReceipt = getDBFIntegerFieldValue(dbfFile, "CHECK", charset);

                        List<Object> receiptEntry = receiptMap.get(numberReceipt);
                        BigDecimal sumCash = receiptEntry == null ? null : (BigDecimal) receiptEntry.get(0);
                        BigDecimal sumCard = receiptEntry == null ? null : (BigDecimal) receiptEntry.get(1);

                        Date dateReceipt = getDBFDateFieldValue(dbfFile, "DATE", charset);
                        Time timeReceipt = new Time(DateUtils.parseDate(getDBFFieldValue(dbfFile, "TIME", charset), new String[]{"HH:mm:ss"}).getTime());
                        
                        String barcodeItem = getDBFFieldValue(dbfFile, "BAR_CODE", charset);
                        //временный чит для корректировки весовых штрихкодов
                        barcodeItem = (barcodeItem != null && barcodeItem.startsWith("22") && barcodeItem.length()==13) ? barcodeItem.substring(2, 7) : barcodeItem;
                        
                        BigDecimal quantityReceiptDetail = getDBFBigDecimalFieldValue(dbfFile, "COUNT", charset);
                        BigDecimal priceReceiptDetail = getDBFBigDecimalFieldValue(dbfFile, "PRICE", charset);
                        BigDecimal sumReceiptDetail = getDBFBigDecimalFieldValue(dbfFile, "COST", charset);
                        BigDecimal discountSumReceiptDetail = getDBFBigDecimalFieldValue(dbfFile, "SUMDISC_ON", charset);
                        BigDecimal discountSumReceipt = getDBFBigDecimalFieldValue(dbfFile, "SUMDISC_OF", charset);

                        Integer nppMachinery = directoryCashRegisterMap.get(directory);
                        Integer nppGroupMachinery = directoryGroupCashRegisterMap.get(directory);
                        Integer numberReceiptDetail = numberReceiptDetailMap.get(numberReceipt);
                        numberReceiptDetail = numberReceiptDetail == null ? 1 : (numberReceiptDetail + 1);
                        numberReceiptDetailMap.put(numberReceipt, numberReceiptDetail);
                        String numberZReport = new SimpleDateFormat("ddMMyy").format(dateReceipt) + "/" + nppGroupMachinery + "/" + nppMachinery;
                        
                        salesInfoList.add(new SalesInfo(nppGroupMachinery, nppMachinery, numberZReport, numberReceipt, dateReceipt,
                                timeReceipt, null, null, null, sumCard, sumCash, barcodeItem, null, quantityReceiptDetail, priceReceiptDetail,
                                sumReceiptDetail, discountSumReceiptDetail, discountSumReceipt, null, numberReceiptDetail,
                                salesFile.getName()));

                    }

                    dbfFile.close();

                } catch (Throwable e) {
                    logger.error("File: " + salesFile.getAbsolutePath(), e);
                }
                filePathList.add(salesFile.getAbsolutePath());
            }
            if (receiptFile.exists())
                filePathList.add(receiptFile.getAbsolutePath());
        }
        return (salesInfoList.isEmpty() && filePathList.isEmpty()) ? null :
                new HTCSalesBatch(salesInfoList, filePathList);
    }

    @Override
    public void finishReadingSalesInfo(HTCSalesBatch salesBatch) {
        logger.info("HTC: Finish Reading started");
        for (String readFile : salesBatch.readFiles) {
            File f = new File(readFile);
            if (f.delete()) {
                logger.info("HTC: file " + readFile + " has been deleted");
            } else {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
            }
        }
    }

    @Override
    public String requestSalesInfo(List<RequestExchange> requestExchangeList) throws IOException, ParseException {
        for (RequestExchange entry : requestExchangeList) {
            if(entry.requestSalesInfo) {
                logger.info("HTC: creating request files");
                for (String directory : entry.directorySet) {

                    //если запрос с даты по дату, мы всё равно можем запросить только за 1 день
                    String date = new SimpleDateFormat("dd.MM.yyyy").format(entry.dateFrom);                    

                    if (new File(directory).exists()) {
                        Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(directory + "\\" + "sales.qry"), "utf-8"));
                        writer.write(date);
                        writer.close();
                    } else
                        return "Error: " + directory + " doesn't exist. Request creation failed.";
                }
            }
        }
        return null;
    }

    protected String getDBFFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException {
        try {
            String result = new String(importFile.getField(fieldName).getBytes(), charset).trim();
            return result.isEmpty() ? null : result;
        } catch (xBaseJException e) {
            return null;
        }
    }

    protected Date getDBFDateFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException, ParseException {
        String dateString = getDBFFieldValue(importFile, fieldName, charset);
        return dateString.isEmpty() ? null : new Date(DateUtils.parseDate(dateString, new String[]{"yyyyMMdd", "dd.MM.yyyy"}).getTime());
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException {
        String result = getDBFFieldValue(importFile, fieldName, charset);
        return (result == null || result.isEmpty() ? null : new BigDecimal(result.replace(",", ".")));
    }

    protected Integer getDBFIntegerFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException {
        String result = getDBFFieldValue(importFile, fieldName, charset);
        return result == null ? null : new Double(result).intValue();
    }

    protected String trim(String input, Integer length) {
        return input == null ? null : (length == null || length >= input.trim().length() ? input.trim() : input.trim().substring(0, length));
    }
}
