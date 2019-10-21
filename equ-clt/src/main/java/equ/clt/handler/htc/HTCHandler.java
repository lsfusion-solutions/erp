package equ.clt.handler.htc;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import equ.clt.EquipmentServer;
import equ.clt.handler.DefaultCashRegisterHandler;
import equ.clt.handler.HandlerUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.util.FileCopyUtils;
import org.xBaseJ.DBF;
import org.xBaseJ.fields.CharField;
import org.xBaseJ.fields.Field;
import org.xBaseJ.fields.LogicalField;
import org.xBaseJ.fields.NumField;
import org.xBaseJ.xBaseJException;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Time;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static equ.clt.handler.HandlerUtils.trim;

public class HTCHandler extends DefaultCashRegisterHandler<HTCSalesBatch> {

    private FileSystemXmlApplicationContext springContext;
    
    String charset = "cp866";

    public HTCHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        return "htc" + transactionInfo.nameGroupMachinery;
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) {

        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        Map<String, Exception> brokenDirectoriesMap = new HashMap<>();
        for(TransactionCashRegisterInfo transaction : transactionList) {

            List<MachineryInfo> succeededCashRegisterList = new ArrayList<>();
            Exception exception = null;
            try {

                if (transaction.itemsList.isEmpty()) {
                    processTransactionLogger.info(String.format("HTC: Transaction # %s has no items", transaction.id));
                } else {
                    processTransactionLogger.info(String.format("HTC: Send Transaction # %s", transaction.id));

                    List<CashRegisterInfo> enabledCashRegisterList = new ArrayList<>();
                    for (CashRegisterInfo cashRegister : transaction.machineryInfoList) {
                        if (cashRegister.enabled)
                            enabledCashRegisterList.add(cashRegister);
                    }

                    Map<String, List<CashRegisterInfo>> directoryMap = new HashMap<>();
                    for (CashRegisterInfo cashRegister : enabledCashRegisterList.isEmpty() ? transaction.machineryInfoList : enabledCashRegisterList) {
                        if (cashRegister.succeeded || cashRegister.directory == null)
                            succeededCashRegisterList.add(cashRegister);
                        else {
                            String directory = cashRegister.directory;
                            List<CashRegisterInfo> cashRegisterEntry = directoryMap.containsKey(directory) ? directoryMap.get(directory) : new ArrayList<>();
                            cashRegisterEntry.add(cashRegister);
                            directoryMap.put(directory, cashRegisterEntry);
                        }
                    }

                    File cachedPriceFile = null;
                    File cachedPriceMdxFile = null;
                    try {

                        NumField CODE = new NumField("CODE", 6, 0);
                        NumField GROUP = new NumField("GROUP", 6, 0);
                        LogicalField ISGROUP = new LogicalField("ISGROUP");
                        CharField ARTICUL = new CharField("ARTICUL", 20);
                        NumField BAR_CODE = new NumField("BAR_CODE", 15, 0);
                        CharField PRODUCT_ID = new CharField("PRODUCT_ID", 64);
                        CharField TABLO_ID = new CharField("TABLO_ID", 32);
                        NumField PRICE = new NumField("PRICE", 15, 2);
                        NumField QUANTITY = new NumField("QUANTITY", 15, 3);
                        LogicalField WEIGHT = new LogicalField("WEIGHT");
                        NumField SECTION = new NumField("SECTION", 2, 0);
                        NumField FLAGS = new NumField("FLAGS", 4, 0);
                        CharField UNIT = new CharField("UNIT", 20);
                        NumField CMD = new NumField("CMD", 2, 0);
                        CharField STRFLAGS = new CharField("STRFLAGS", 16);
                        NumField NDS = new NumField("NDS", 5, 2);
                        NumField NALOG = new NumField("NALOG", 1, 0);

                        List<List<Object>> waitList = new ArrayList<>();
                        for (Map.Entry<String, List<CashRegisterInfo>> entry : directoryMap.entrySet()) {

                            String directory = entry.getKey();
                            if (brokenDirectoriesMap.containsKey(directory)) {
                                exception = brokenDirectoriesMap.get(directory);
                            } else {
                                String fileName = transaction.snapshot ? "NewPrice.dbf" : "UpdPrice.dbf";
                                processTransactionLogger.info(String.format("HTC: Transaction # %s creating %s file", transaction.id, directory + "/" + fileName));
                                File priceFile = new File(directory + "/" + fileName);
                                File flagPriceFile = new File(directory + "/price.qry");

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
                                                if (transaction.snapshot)
                                                    dbfFile.addField(new Field[]{CODE, GROUP, ISGROUP, ARTICUL, BAR_CODE, PRODUCT_ID, TABLO_ID, PRICE, QUANTITY, WEIGHT, SECTION, FLAGS, UNIT, STRFLAGS, NDS, NALOG});
                                                else
                                                    dbfFile.addField(new Field[]{CODE, GROUP, ISGROUP, ARTICUL, BAR_CODE, PRODUCT_ID, TABLO_ID, PRICE, QUANTITY, WEIGHT, SECTION, FLAGS, UNIT, CMD, NDS, NALOG});
                                        }

                                        Set<String> usedBarcodes = new HashSet<>();
                                        Map<String, Integer> barcodeRecordMap = new HashMap<>();
                                        for (int i = 1; i <= dbfFile.getRecordCount(); i++) {
                                            dbfFile.read();
                                            String barcode = getDBFFieldValue(dbfFile, "BAR_CODE", charset);
                                            barcodeRecordMap.put(barcode, i);
                                        }
                                        dbfFile.startTop();

                                    // Временно отключено, так как НТС позволяет продавать группы
                                    // item groups
//                            putField(dbfFile, ISGROUP, "T", append);
//                            for (CashRegisterItemInfo item : transaction.itemsList) {
//                                if (item.idItemGroup != null) {
//                                    String parent = null;
//                                    for (ItemGroup itemGroup : Lists.reverse(transaction.itemGroupMap.get(item.idItemGroup))) {
//                                        String idItemGroup = (itemGroup.idItemGroup.equals("Все") ? "0" : itemGroup.idItemGroup.replace("_", "").replace(".", ""));
//                                        if (!usedBarcodes.contains(idItemGroup)) {
//                                            Integer recordNumber = null;
//                                            if (append) {
//                                                recordNumber = barcodeRecordMap.get(idItemGroup);
//                                                if (recordNumber != null)
//                                                    dbfFile.gotoRecord(recordNumber);
//                                            }
//                                            putField(dbfFile, CODE, trim(idItemGroup, 6), append);
//                                            putField(dbfFile, GROUP, parent, append);
//                                            putField(dbfFile, BAR_CODE, trim(idItemGroup, 13), append);
//                                            putField(dbfFile, PRODUCT_ID, trim(itemGroup.nameItemGroup, 64), append);
//                                            putField(dbfFile, TABLO_ID, trim(itemGroup.nameItemGroup, 20), append);
//                                            putField(dbfFile, PRICE, null, append);
//                                            putField(dbfFile, WEIGHT, "F", append);
//                                            putField(dbfFile, FLAGS, "0", append);
//                                            if (recordNumber != null)
//                                                dbfFile.update();
//                                            else {
//                                                dbfFile.write();
//                                                dbfFile.file.setLength(dbfFile.file.length() - 1);
//                                                barcodeRecordMap.put(itemGroup.idItemGroup, barcodeRecordMap.size() + 1);
//                                            }
//                                            usedBarcodes.add(idItemGroup);
//                                        }
//                                        parent = trim(idItemGroup, 6);
//                                    }
//                                }
//                            }

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

                                                    String code = item.idItem;
                                                    if (lastCode == null || !lastCode.equals(code)) {
                                                        putField(dbfFile, CODE, code, append);
                                                        putField(dbfFile, ARTICUL, code, append);
                                                        lastCode = code;
                                                    }

                                                    String group = item.idItemGroup == null ? null : trim(item.idItemGroup.replace("_", ""), 6);
                                                    if (lastGroup == null || !lastGroup.equals(group)) {
                                                        putField(dbfFile, GROUP, group, append);
                                                        lastGroup = group;
                                                    }

                                                    if (lastBarcode == null || !lastBarcode.equals(barcode)) {
                                                        putField(dbfFile, BAR_CODE, barcode, append);
                                                        lastBarcode = barcode;
                                                    }

                                                    if (lastName == null || !lastName.equals(item.name)) {
                                                        putField(dbfFile, PRODUCT_ID, trim(item.name, 64), append);
                                                        putField(dbfFile, TABLO_ID, trim(item.name, 20), append);
                                                        lastName = item.name;
                                                    }

                                                    if (lastPrice == null || !lastPrice.equals(item.price)) {
                                                        putField(dbfFile, PRICE, String.valueOf(item.price), append);
                                                        lastPrice = item.price;
                                                    }

                                                    if (lastSplitItem == null || !lastSplitItem.equals(item.splitItem)) {
                                                        putField(dbfFile, WEIGHT, item.splitItem ? "T" : "F", append);
                                                        lastSplitItem = item.splitItem;
                                                    }

                                                    Integer flags = item.flags != null ? (item.flags >= 256 ? (item.flags - 256) : item.flags) : 248 + (item.splitItem ? 1 : 0);
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
                                                            barcodeRecordMap.put(barcode, barcodeRecordMap.size() + 1);
                                                    }
                                                    usedBarcodes.add(barcode);
                                                }
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
                                        putField(dbfFile, UNIT, null, append);
                                        if (transaction.snapshot)
                                            putField(dbfFile, STRFLAGS, null, append);
                                        else
                                            putField(dbfFile, CMD, null, append);
                                        putField(dbfFile, NDS, null, append);
                                        putField(dbfFile, NALOG, null, append);

                                    }

                                    try {
                                        if (!append) {
                                            processTransactionLogger.info(String.format("HTC: Transaction # %s copying %s file", transaction.id, directory + "/" + fileName));
                                            FileCopyUtils.copy(cachedPriceFile, priceFile);
                                            processTransactionLogger.info(String.format("HTC: Transaction # %s finished copying %s file", transaction.id, directory + "/" + fileName));
                                        }
                                        flagPriceFile.createNewFile();
                                        waitList.add(Arrays.asList(priceFile, flagPriceFile, directory, entry.getValue()));
                                    } catch (IOException e) {
                                        brokenDirectoriesMap.put(priceFile.getParent(), e);
                                        exception = e;
                                        processTransactionLogger.error("HTC: error while create files", e);
                                    }
                                } finally {
                                    if (dbfFile != null)
                                        dbfFile.close();
                                }
                            }
                        }
                        processTransactionLogger.info(String.format("HTC: Transaction # %s wait for deletion", transaction.id));
                        Exception deletionException = waitForDeletion(waitList, succeededCashRegisterList, brokenDirectoriesMap);
                        processTransactionLogger.info(String.format("HTC: Transaction # %s end waiting for deletion", transaction.id));
                        exception = exception == null ? deletionException : exception;

                    } catch (xBaseJException e) {
                        throw Throwables.propagate(e);
                    } finally {
                        safeFileDelete(cachedPriceFile, processTransactionLogger);
                        safeFileDelete(cachedPriceMdxFile, processTransactionLogger);
                    }
                }
            } catch (Exception e) {
                exception = e;
            }
            sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(succeededCashRegisterList, exception));
        }
        return sendTransactionBatchMap;
    }

    @Override
    public void sendDiscountCardList(List<DiscountCard> discountCardList, RequestExchange requestExchange) throws IOException {

        machineryExchangeLogger.info("HTCHandler: sending discount cards");
        
        Date currentDate = new java.sql.Date(Calendar.getInstance().getTimeInMillis());
        try {

            File cachedDiscFile = null;
            
            for(String directory : getDirectorySet(requestExchange)) {
                Date startDate = requestExchange.startDate;

                machineryExchangeLogger.info("HTCHandler: sending " + discountCardList.size() + " discount cards to : " + directory + (startDate != null ? (" starting from " + startDate) : "") );

                File discountCardFile = new File(directory + (startDate == null ? "/Discnew.dbf" : "/Discupd.dbf"));
                if (cachedDiscFile != null) {
                    FileCopyUtils.copy(cachedDiscFile, discountCardFile);
                } else {
                    cachedDiscFile = File.createTempFile("cachedDiscnew", ".dbf");
                    
                    CharField DISC = new CharField("DISC", 32);
                    CharField NAME = new CharField("NAME", 40);
                    NumField PERCENT = new NumField("PERCENT", 6, 2);
                    LogicalField ISSTOP = new LogicalField("ISSTOP");

                    DBF dbfFile = new DBF(cachedDiscFile.getAbsolutePath(), DBF.DBASEIV, true, charset);
                    dbfFile.addField(new Field[]{DISC, NAME, PERCENT, ISSTOP});

                    String lastName = null;
                    BigDecimal lastPercent = null;
                    for (DiscountCard discountCard : discountCardList) {

                        //todo: убрать после обновления сервера - эта проверка будет проводится на сервере
                        boolean active = discountCard.dateFromDiscountCard != null && startDate != null && discountCard.dateFromDiscountCard.compareTo(startDate) >= 0;
                        boolean isStop = (discountCard.dateFromDiscountCard != null && discountCard.dateFromDiscountCard.compareTo(currentDate) > 0) ||
                                (discountCard.dateToDiscountCard != null && discountCard.dateToDiscountCard.compareTo(currentDate) < 0);
                        if (startDate == null || active || isStop) {

                            putField(dbfFile, DISC, discountCard.numberDiscountCard, false);

                            if (lastName == null || !lastName.equals(discountCard.nameDiscountCard)) {
                                putField(dbfFile, NAME, discountCard.nameDiscountCard == null ? "" : discountCard.nameDiscountCard, false);
                                lastName = discountCard.nameDiscountCard;
                            }

                            if (lastPercent == null || !lastPercent.equals(discountCard.percentDiscountCard)) {
                                putField(dbfFile, PERCENT, String.valueOf(discountCard.percentDiscountCard), false);
                                lastPercent = discountCard.percentDiscountCard;
                            }

                            putField(dbfFile, ISSTOP, isStop ? "T" : "F", false);

                            dbfFile.write();
                        }
                    }
                    dbfFile.close();
                    FileCopyUtils.copy(cachedDiscFile, discountCardFile);
                }
                File discountFlag = new File(directory + (startDate == null ? "/TMC.dcn" : "/TMC.dcu"));
                discountFlag.createNewFile();

                machineryExchangeLogger.info("HTCHandler: finish sending to " + directory);
            }
            if(cachedDiscFile != null) {
                safeFileDelete(new File(cachedDiscFile.getAbsolutePath().replace(".dbf", ".mdx")), machineryExchangeLogger);
                safeFileDelete(cachedDiscFile, machineryExchangeLogger);
            }
        } catch (xBaseJException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void sendPromotionInfo(PromotionInfo promotionInfo, RequestExchange requestExchange) throws IOException {
        machineryExchangeLogger.info("HTCHandler: sending PromotionInfo");
        try {

            File cachedTimeFile = null;
            File cachedQuantityFile = null;
            File cachedSumFile = null;
            
            for(String directory : getDirectorySet(requestExchange)) {

                cachedTimeFile = sendPromotionTimeFile(promotionInfo.promotionTimeList, directory, cachedTimeFile);
                cachedQuantityFile = sendPromotionQuantityFile(promotionInfo.promotionQuantityList, directory, cachedQuantityFile);
                cachedSumFile = sendPromotionSumFile(promotionInfo.promotionSumList, directory, cachedSumFile);
                
            }
            
            if(cachedTimeFile != null) {
                safeFileDelete(cachedTimeFile, machineryExchangeLogger);
                safeFileDelete(new File(cachedTimeFile.getAbsolutePath().replace(".dbf", ".mdx")), machineryExchangeLogger);
            }

            if(cachedQuantityFile != null) {
                safeFileDelete(cachedQuantityFile, machineryExchangeLogger);
                safeFileDelete(new File(cachedQuantityFile.getAbsolutePath().replace(".dbf", ".mdx")), machineryExchangeLogger);
            }

            if(cachedSumFile != null) {
                safeFileDelete(cachedSumFile, machineryExchangeLogger);
                safeFileDelete(new File(cachedSumFile.getAbsolutePath().replace(".dbf", ".mdx")), machineryExchangeLogger);
            }
            
        } catch (xBaseJException e) {
            throw Throwables.propagate(e);
        }
    }

    private File sendPromotionTimeFile(List<PromotionTime> promotionTimeList, String directory, File cachedTimeFile) throws IOException, xBaseJException {
        if(promotionTimeList != null && !promotionTimeList.isEmpty()) {
            File timeFile = new File(directory + "/Timenew.dbf");
            if (cachedTimeFile != null) {
                FileCopyUtils.copy(cachedTimeFile, timeFile);
            } else {
                cachedTimeFile = File.createTempFile("cachedTimenew", ".dbf");
                
                CharField DAY = new CharField("DAY", 15);
                CharField BEGIN = new CharField("BEGIN", 4);
                CharField END = new CharField("END", 4);
                NumField PERCENT = new NumField("PERCENT", 6, 2);
                LogicalField ISSTOP = new LogicalField("ISSTOP");

                DBF dbfFile = null;
                try {

                    dbfFile = new DBF(cachedTimeFile.getAbsolutePath(), DBF.DBASEIV, true, charset);
                    dbfFile.addField(new Field[]{DAY, BEGIN, END, PERCENT, ISSTOP});

                    for (PromotionTime promotionTime : promotionTimeList) {
                        putField(dbfFile, DAY, promotionTime.day == null ? null : trim(promotionTime.day, 15).toUpperCase(), false);
                        putField(dbfFile, BEGIN, formatTime(promotionTime.beginTime), false);
                        putField(dbfFile, END, formatTime(promotionTime.endTime), false);
                        putField(dbfFile, PERCENT, promotionTime.percent == null ? null : String.valueOf(promotionTime.percent.doubleValue()), false);
                        putField(dbfFile, ISSTOP, promotionTime.isStop ? "T" : "F", false);
                        dbfFile.write();
                    }
                } finally {
                    if (dbfFile != null)
                        dbfFile.close();
                }
                FileCopyUtils.copy(cachedTimeFile, timeFile);
            }
            new File(directory + "/TMC.tmn").createNewFile();
            return cachedTimeFile;
        } else return null;
    }

    private File sendPromotionQuantityFile(List<PromotionQuantity> promotionQuantityList, String directory, File cachedQuantityFile) throws IOException, xBaseJException {
        if (promotionQuantityList != null && !promotionQuantityList.isEmpty()) {
            File quantityFile = new File(directory + "/Bonnew.dbf");
            if (cachedQuantityFile != null) {
                FileCopyUtils.copy(cachedQuantityFile, quantityFile);
            } else {
                cachedQuantityFile = File.createTempFile("cachedBonnew", ".dbf");
                
                CharField BAR1 = new CharField("BAR1", 15);
                NumField KOL1 = new NumField("KOL1", 12, 3);
                CharField BAR2 = new CharField("BAR2", 15);
                NumField KOL2 = new NumField("KOL2", 12, 3);
                NumField PERCENT = new NumField("PERCENT", 6, 2);
                LogicalField ISSTOP = new LogicalField("ISSTOP");

                DBF dbfFile = null;
                try {

                    dbfFile = new DBF(cachedQuantityFile.getAbsolutePath(), DBF.DBASEIV, true, charset);
                    dbfFile.addField(new Field[]{BAR1, KOL1, BAR2, KOL2, PERCENT, ISSTOP});

                    for (PromotionQuantity promotionQuantity : promotionQuantityList) {
                        putField(dbfFile, BAR1, trim(promotionQuantity.barcodeItem, 15), false);
                        putField(dbfFile, KOL1, promotionQuantity.quantity == null ? null : String.valueOf(promotionQuantity.quantity.doubleValue()), false);
                        putField(dbfFile, PERCENT, promotionQuantity.percent == null ? null : String.valueOf(promotionQuantity.percent.doubleValue()), false);
                        putField(dbfFile, ISSTOP, promotionQuantity.isStop ? "T" : "F", false);
                        dbfFile.write();
                    }
                } finally {
                    if (dbfFile != null)
                        dbfFile.close();
                }
                FileCopyUtils.copy(cachedQuantityFile, quantityFile);
            }
            new File(directory + "/TMC.bnn").createNewFile();
            return cachedQuantityFile;
        } else return null;
    }

    private File sendPromotionSumFile(List<PromotionSum> promotionSumList, String directory, File cachedSumFile) throws IOException, xBaseJException {
        if (promotionSumList != null && !promotionSumList.isEmpty()) {
            File sumFile = new File(directory + "/Sumnew.dbf");
            if (cachedSumFile != null) {
                FileCopyUtils.copy(cachedSumFile, sumFile);
            } else {
                cachedSumFile = File.createTempFile("cachedSumnew", ".dbf");
                NumField SUM = new NumField("SUM", 12, 0);
                NumField PERCENT = new NumField("PERCENT", 6, 2);
                LogicalField ISSTOP = new LogicalField("ISSTOP");

                DBF dbfFile = null;
                try {

                    dbfFile = new DBF(cachedSumFile.getAbsolutePath(), DBF.DBASEIV, true, charset);
                    dbfFile.addField(new Field[]{SUM, PERCENT, ISSTOP});

                    for (PromotionSum promotionSum : promotionSumList) {
                        putField(dbfFile, SUM, promotionSum.sum == null ? null : String.valueOf(promotionSum.sum), false);
                        putField(dbfFile, PERCENT, promotionSum.percent == null ? null : String.valueOf(promotionSum.percent.doubleValue()), false);
                        putField(dbfFile, ISSTOP, promotionSum.isStop ? "T" : "F", false);
                        dbfFile.write();
                    }
                } finally {
                    if (dbfFile != null)
                        dbfFile.close();
                }
                FileCopyUtils.copy(cachedSumFile, sumFile);
            }
            new File(directory + "/TMC.smn").createNewFile();
            return cachedSumFile;
        } else return null;
    }
    
    private String formatTime(Time time) {
        return time == null ? null : new SimpleDateFormat("HHmm").format(new Date(time.getTime()));
    }

    private void putField(DBF dbfFile, Field field, String value, boolean append) throws xBaseJException {
        if(append)
            dbfFile.getField(field.getName()).put(value == null ? "null" : value);
        else
            field.put(value == null ? "null" : value);
    }

    private Exception waitForDeletion(List<List<Object>> waitList, List<MachineryInfo> succeededCashRegisterList, Map<String, Exception> brokenDirectoriesMap) {
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
                    List<CashRegisterInfo> cashRegisterInfoList = (List<CashRegisterInfo>) waitEntry.get(3);
                    if (flagFile.exists() || file.exists())
                        nextWaitList.add(Arrays.asList(file, flagFile, directory, cashRegisterInfoList));
                    else
                        succeededCashRegisterList.addAll(cashRegisterInfoList);
                }
                waitList = nextWaitList;
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }

        String exception = waitList.isEmpty() ? null : "HTC: files has been created but not processed by cash register machine: ";
        for(List<Object> waitEntry : waitList) {
            File file = (File) waitEntry.get(0);
            File flagFile = (File) waitEntry.get(1);
            String directory = (String) waitEntry.get(2);
            if(file.exists())
                exception += file.getAbsolutePath() + "; ";
            if(flagFile.exists())
                exception += flagFile.getAbsolutePath() + "; ";
            brokenDirectoriesMap.put(directory, new RuntimeException(exception));
        }
        return exception == null ? null : new RuntimeException(exception);
    }

    @Override
    public SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) throws IOException {

        HTCSettings htcSettings = springContext.containsBean("htcSettings") ? (HTCSettings) springContext.getBean("htcSettings") : null;
        boolean makeBackup = htcSettings == null || htcSettings.isMakeBackup();

        Map<String, CashRegisterInfo> directoryCashRegisterMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (fitHandler(c) && c.directory != null) {
                    directoryCashRegisterMap.put(c.directory, c);
            }
        }

        CashRegisterInfo cashRegister = directoryCashRegisterMap.get(directory);
        Integer nppMachinery = cashRegister == null ? null : cashRegister.number;
        Integer nppGroupMachinery = cashRegister == null ? null : cashRegister.numberGroup;

        List<SalesInfo> salesInfoList = new ArrayList<>();
        List<String> filePathList = new ArrayList<>();

        String nameSalesFile = null;
        File salesFile = null;
        File receiptFile = null;

        List<File> salesFilesList = new ArrayList<>();
        List<File> receiptFilesList = new ArrayList<>();
        File[] filesList = new File(directory).listFiles();
        if(filesList == null) {
            sendSalesLogger.info("HTC: Failed to access directory : " + directory);
            return null;
        }

        for(File file : filesList) {
            if(file != null) {
                String name = file.getName();
                String path = file.getPath();
                if(name.startsWith("Sales") && path.endsWith(".dbf"))
                    salesFilesList.add(file);
                if(name.startsWith("Receipt") && path.endsWith(".dbf"))
                    receiptFilesList.add(file);
            }
        }
        if (salesFilesList.isEmpty() || receiptFilesList.isEmpty()) {
            sendSalesLogger.info("HTC: No sale or receipt file found in " + directory);
            for(File file : salesFilesList) {
                filePathList.add(file.getAbsolutePath());
            }
            for(File file : receiptFilesList) {
                filePathList.add(file.getAbsolutePath());
            }
        }
        else {
            sendSalesLogger.info("HTC: found sale and receipt files in " + directory);

            List<String> receiptFilesPathList = new ArrayList<>();
            for(File file : receiptFilesList) {
                receiptFilesPathList.add(file.getAbsolutePath());
            }

            for(File remoteSalesFile : salesFilesList) {

                String[] splitted = remoteSalesFile.getAbsolutePath().split("Sales");
                String postfix = splitted[splitted.length - 1];
                File remoteReceiptFile = new File(directory + "/Receipt" + postfix);
                if(!remoteReceiptFile.exists()) {
                    filePathList.add(remoteSalesFile.getAbsolutePath());
                    continue;
                }

                boolean copyError = false;
                try {
                    nameSalesFile = remoteSalesFile.getName();
                    salesFile = File.createTempFile("Sales", ".dbf");
                    sendSalesLogger.info(String.format("Start copying sales.dbf from %s to %s", remoteSalesFile.getAbsolutePath(), salesFile.getAbsolutePath()));
                    FileCopyUtils.copy(remoteSalesFile, salesFile);
                    sendSalesLogger.info(String.format("End copying sales.dbf from %s to %s", remoteSalesFile.getAbsolutePath(), salesFile.getAbsolutePath()));
                    receiptFile = File.createTempFile("Receipt", ".dbf");
                    sendSalesLogger.info(String.format("Start copying receipt.dbf from %s to %s", remoteReceiptFile.getAbsolutePath(), receiptFile.getAbsolutePath()));
                    FileCopyUtils.copy(remoteReceiptFile, receiptFile);
                    sendSalesLogger.info(String.format("End copying receipt.dbf from %s to %s", remoteReceiptFile.getAbsolutePath(), receiptFile.getAbsolutePath()));
                } catch (Exception e) {
                    copyError = true;
                    sendSalesLogger.error("File: " + remoteSalesFile.getAbsolutePath(), e);
                }

                DBF salesDBFFile = null;
                try {
                    if (!copyError) {
                        Map<Integer, List<Object>> receiptMap = new HashMap<>();
                        DBF receiptDBFFile = new DBF(receiptFile.getAbsolutePath());
                        int receiptRecordCount = receiptDBFFile.getRecordCount();
                        for (int i = 0; i < receiptRecordCount; i++) {
                            receiptDBFFile.read();
                            if (!receiptDBFFile.deleted()) {
                                Integer numberReceipt = getDBFIntegerFieldValue(receiptDBFFile, "NUMDOC", charset);
                                BigDecimal sumCash = getDBFBigDecimalFieldValue(receiptDBFFile, "COST1", charset);
                                BigDecimal sumCard = getDBFBigDecimalFieldValue(receiptDBFFile, "COST2", charset);
                                String idDiscountCard = getDBFFieldValue(receiptDBFFile, "CODEKLIENT", charset);
                                receiptMap.put(numberReceipt, Arrays.asList(sumCash, sumCard, idDiscountCard));
                            }

                        }
                        receiptDBFFile.close();

                        salesDBFFile = new DBF(salesFile.getAbsolutePath());
                        int recordCount = salesDBFFile.getRecordCount();

                        Map<Integer, Integer> numberReceiptDetailMap = new HashMap<>();
                        for (int i = 0; i < recordCount; i++) {

                            salesDBFFile.read();
                            if (!salesDBFFile.deleted()) {
                                Integer numberReceipt = getDBFIntegerFieldValue(salesDBFFile, "CHECK", charset);

                                List<Object> receiptEntry = receiptMap.get(numberReceipt);
                                BigDecimal sumCash = receiptEntry == null ? null : (BigDecimal) receiptEntry.get(0);
                                BigDecimal sumCard = receiptEntry == null ? null : (BigDecimal) receiptEntry.get(1);
                                String idDiscountCard = receiptEntry == null ? null : (String) receiptEntry.get(2);

                                String idEmployee = getDBFFieldValue(salesDBFFile, "CASHIER", charset);
                                Date dateReceipt = getDBFDateFieldValue(salesDBFFile, "DATE", charset);
                                if (dateReceipt != null) {
                                    Time timeReceipt = new Time(DateUtils.parseDate(getDBFFieldValue(salesDBFFile, "TIME", charset), "HH:mm", "HH:mm:ss").getTime());

                                    String codeItem = getDBFFieldValue(salesDBFFile, "CODE", charset);
                                    String barcodeItem = getDBFFieldValue(salesDBFFile, "BAR_CODE", charset);
                                    //временный чит для корректировки весовых штрихкодов
                                    if (barcodeItem != null && barcodeItem.startsWith("22") && barcodeItem.length() == 13) {
                                        barcodeItem = barcodeItem.substring(2, 7).equals("00000") ? barcodeItem.substring(7, 12) : barcodeItem.substring(2, 7);
                                    }
                                    if ("00000".equals(barcodeItem)) {
                                        barcodeItem = codeItem;
                                    }

                                    BigDecimal quantityReceiptDetail = getDBFBigDecimalFieldValue(salesDBFFile, "COUNT", charset);
                                    BigDecimal priceReceiptDetail = getDBFBigDecimalFieldValue(salesDBFFile, "PRICE", charset);
                                    BigDecimal sumReceiptDetail = getDBFBigDecimalFieldValue(salesDBFFile, "COST", charset);
                                    BigDecimal discountSumReceiptDetail = getDBFBigDecimalFieldValue(salesDBFFile, "SUMDISC_ON", charset);
                                    BigDecimal discountSumReceipt = getDBFBigDecimalFieldValue(salesDBFFile, "SUMDISC_OF", charset);
                                    discountSumReceiptDetail = HandlerUtils.safeAdd(discountSumReceiptDetail, discountSumReceipt);

                                    Integer numberReceiptDetail = numberReceiptDetailMap.get(numberReceipt);
                                    numberReceiptDetail = numberReceiptDetail == null ? 1 : (numberReceiptDetail + 1);
                                    numberReceiptDetailMap.put(numberReceipt, numberReceiptDetail);
                                    String numberZReport = new SimpleDateFormat("ddMMyy").format(dateReceipt) + "/" + nppGroupMachinery + "/" + nppMachinery;

                                    salesInfoList.add(new SalesInfo(false, nppGroupMachinery, nppMachinery, numberZReport, dateReceipt, timeReceipt, numberReceipt, dateReceipt,
                                            timeReceipt, idEmployee, null, null, sumCard, sumCash, null, barcodeItem, null, null, null, quantityReceiptDetail,
                                            priceReceiptDetail, sumReceiptDetail, discountSumReceiptDetail, null, idDiscountCard, numberReceiptDetail,
                                            nameSalesFile, null, cashRegister));
                                }
                            }
                        }


                        if(makeBackup) {
                            String timePostfix = postfix == null ? (getCurrentTimestamp() + ".dbf") : postfix;
                            new File(directory + "/backup").mkdir();
                            File backupSalesFile = new File(directory + "/backup/Sales" + timePostfix);
                            sendSalesLogger.info(String.format("Start copying sales.dbf from %s to %s", salesFile.getAbsolutePath(), backupSalesFile.getAbsolutePath()));
                            FileCopyUtils.copy(salesFile, backupSalesFile);
                            sendSalesLogger.info(String.format("End copying sales.dbf from %s to %s", salesFile.getAbsolutePath(), backupSalesFile.getAbsolutePath()));
                            File backupReceiptFile = new File(directory + "/backup/Receipt" + timePostfix);
                            sendSalesLogger.info(String.format("Start copying receipt.dbf from %s to %s", receiptFile.getAbsolutePath(), backupReceiptFile.getAbsolutePath()));
                            FileCopyUtils.copy(receiptFile, backupReceiptFile);
                            sendSalesLogger.info(String.format("End copying receipt.dbf from %s to %s", receiptFile.getAbsolutePath(), backupReceiptFile.getAbsolutePath()));

                        }
                    }
                } catch (Throwable e) {
                    sendSalesLogger.error("File: " + remoteSalesFile.getAbsolutePath(), e);
                } finally {
                    if(salesDBFFile != null)
                        salesDBFFile.close();
                    receiptFilesPathList.remove(remoteReceiptFile.getAbsolutePath());
                    if (!copyError) {
                        filePathList.add(remoteSalesFile.getAbsolutePath());
                        filePathList.add(remoteReceiptFile.getAbsolutePath());
                    }
                    safeFileDelete(salesFile, sendSalesLogger);
                    safeFileDelete(receiptFile, sendSalesLogger);
                    safeFileDelete(new File(directory + "/Sales.ans"), sendSalesLogger);
                }
            }
            filePathList.addAll(receiptFilesPathList);
        }
        return (salesInfoList.isEmpty() && filePathList.isEmpty()) ? null :
                new HTCSalesBatch(salesInfoList, filePathList);
    }

    @Override
    public void finishReadingSalesInfo(HTCSalesBatch salesBatch) {
        sendSalesLogger.info("HTC: Finish Reading started");
        for (String readFile : salesBatch.readFiles) {
            File f = new File(readFile);
            if (f.delete()) {
                sendSalesLogger.info("HTC: file " + readFile + " has been deleted");
            } else {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
            }
        }
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList,
                                 Set<Long> succeededRequests, Map<Long, Throwable> failedRequests, Map<Long, Throwable> ignoredRequests) {
        Map<String, List<RequestExchange>> requestExchangeMap = new HashMap<>();

        HTCSettings htcSettings = springContext.containsBean("htcSettings") ? (HTCSettings) springContext.getBean("htcSettings") : null;
        boolean useDataDirectory = htcSettings == null || htcSettings.isUseDataDirectory();

        for (RequestExchange entry : requestExchangeList) {
            for (String directory : getDirectorySet(entry)) {
                List<RequestExchange> requestExchangeEntry = requestExchangeMap.containsKey(directory) ? requestExchangeMap.get(directory) : new ArrayList<>();
                requestExchangeEntry.add(entry);
                requestExchangeMap.put(directory, requestExchangeEntry);
            }
        }

        try {

            Collection<Callable<Map<Long, String>>> taskList = new LinkedList<>();
            for (Map.Entry<String, List<RequestExchange>> entry : requestExchangeMap.entrySet()) {
                String directory = entry.getKey();
                List<RequestExchange> requestExchanges = entry.getValue();
                if(directory != null)
                    taskList.add(new RequestSalesInfoTask(directory, requestExchanges, useDataDirectory));
            }
            if(!taskList.isEmpty()) {
                ExecutorService singleTransactionExecutor = EquipmentServer.getFixedThreadPool(taskList.size(), "HTCRequestSales");

                List<Future<Map<Long, String>>> threadResults = singleTransactionExecutor.invokeAll(taskList);

                for (Future<Map<Long, String>> threadResult : threadResults) {
                    for (Map.Entry<Long, String> entry : threadResult.get().entrySet()) {
                        if (entry.getValue() == null)
                            succeededRequests.add(entry.getKey());
                        else
                            ignoredRequests.put(entry.getKey(), new RuntimeException(entry.getValue()));
                    }
                }
                singleTransactionExecutor.shutdown();
            }

        } catch (InterruptedException | ExecutionException e) {
            machineryExchangeLogger.error("HTC: error while creating request files", e);
        }

        for(Long failedRequest : failedRequests.keySet()) {
            succeededRequests.remove(failedRequest);
        }
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
        return dateString == null || dateString.isEmpty() ? null : new Date(DateUtils.parseDate(dateString, "yyyyMMdd", "dd.MM.yyyy").getTime());
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException {
        String result = getDBFFieldValue(importFile, fieldName, charset);
        return (result == null || result.isEmpty() ? null : new BigDecimal(result.replace(",", ".")));
    }

    protected Integer getDBFIntegerFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException {
        String result = getDBFFieldValue(importFile, fieldName, charset);
        return result == null ? null : new Double(result).intValue();
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

    class RequestSalesInfoTask implements Callable<Map<Long, String>> {
        String directory;
        List<RequestExchange> requestExchanges;
        boolean useDataDirectory;

        public RequestSalesInfoTask(String directory, List<RequestExchange> requestExchanges, boolean useDataDirectory) {
            this.directory = directory;
            this.requestExchanges = requestExchanges;
            this.useDataDirectory = useDataDirectory;
        }

        @Override
        public Map<Long, String> call() {
            Map<Long, String> result = new HashMap<>();
            boolean failed = false;
            for (RequestExchange requestExchange : requestExchanges) {

                try {
                    String requestResult = null;
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(requestExchange.dateFrom);
                    while (cal.getTime().compareTo(requestExchange.dateTo) <= 0) {
                        if (failed) {
                            requestResult = requestResult == null ? String.format("Previous query to directory %s failed", directory) : requestResult;
                        } else {
                            boolean skip = false;
                            if(useDataDirectory) {
                                File receiptFile = new File(new File(directory).getParent() + "/Data/jc" + new SimpleDateFormat("yyMMdd").format(cal.getTime()) + ".dbf");
                                File salesFile = new File(new File(directory).getParent() + "/Data/js" + new SimpleDateFormat("yyMMdd").format(cal.getTime()) + ".dbf");
                                if(receiptFile.exists() && salesFile.exists()) {
                                    try {
                                        FileCopyUtils.copy(receiptFile, new File(directory + "/Receipt" + getCurrentTimestamp() + ".dbf"));
                                        FileCopyUtils.copy(salesFile, new File(directory + "/Sales" + getCurrentTimestamp() + ".dbf"));
                                        skip = true;
                                    } catch (Exception e) {
                                        sendSalesLogger.error("HTC: error copying files from data directory");
                                    }
                                }
                            }
                            if(!skip) {
                                requestResult = createRequest(cal, directory);
                                if (requestResult != null)
                                    failed = true;
                            }
                        }
                        cal.add(Calendar.DATE, 1);
                    }
                    if (requestResult != null || !result.containsKey(requestExchange.requestExchange))
                        result.put(requestExchange.requestExchange, requestResult);
                } catch (Exception e) {
                    result.put(requestExchange.requestExchange, e.getMessage());
                }
            }
            return result;
        }

        private String createRequest(Calendar cal, String directory) throws IOException {
            String result;
            String date = new SimpleDateFormat("dd.MM.yyyy").format(cal.getTime());
            sendSalesLogger.info(String.format("HTC: creating request file in %s for date %s", directory, date));

            File queryFile = new File(directory + "/" + "sales.qry");
            File ansFile = new File(directory + "/" + "sales.ans");
            File salesFile = new File(directory + "/Sales.dbf");
            File receiptFile = new File(directory + "/Receipt.dbf");
            if (new File(directory).exists()) {
                safeFileDelete(ansFile, sendSalesLogger);
                Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(queryFile), StandardCharsets.UTF_8));
                writer.write(date);
                writer.close();
                result = waitRequestSalesInfo(queryFile, ansFile, salesFile, receiptFile);
                safeFileDelete(ansFile, sendSalesLogger);
            } else
                result = "Error: " + directory + " doesn't exist. Request creation failed.";
            if (result != null)
                sendSalesLogger.error("HTC: " + result);
            return result;
        }

        private String waitRequestSalesInfo(File queryFile, File ansFile, File salesFile, File receiptFile) {
            int count = 0;
            while (!Thread.currentThread().isInterrupted() && (queryFile.exists() || !ansFile.exists() || !salesFile.exists() || !receiptFile.exists())) {
                try {
                    count++;
                    if (count >= 120)
                        if(queryFile.exists())
                            return String.format("Request file %s has been created but not processed by server", queryFile.getAbsolutePath());
                        else
                            return String.format("Request file %s has been processed by server but no sales data found", queryFile.getAbsolutePath());
                    else
                        Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return e.getMessage();
                }
            }
            String salesPath = salesFile.getAbsolutePath();
            String receiptPath = receiptFile.getAbsolutePath();
            boolean renamed = salesFile.renameTo(new File(salesPath.substring(0, salesPath.length() - 4) + getCurrentTimestamp() + ".dbf"));
            if(renamed) {
                renamed = receiptFile.renameTo(new File(receiptPath.substring(0, receiptPath.length() - 4) + getCurrentTimestamp() + ".dbf"));
                return renamed ? null : "Renaming of receipt file failed";
            } else
                return "Renaming of sales file failed";
        }
    }

    private void safeFileDelete(File file, Logger logger) {
        if (file != null && !file.delete()) {
            file.deleteOnExit();
            logger.info(String.format("HTC: cannot delete file %s, will try to deleteOnExit", file.getAbsolutePath()));
        }
    }
}
