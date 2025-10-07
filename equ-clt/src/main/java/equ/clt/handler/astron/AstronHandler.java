package equ.clt.handler.astron;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import equ.api.stoplist.StopListInfo;
import equ.api.stoplist.StopListItem;
import equ.clt.handler.DefaultCashRegisterHandler;
import equ.clt.handler.HandlerUtils;
import lsfusion.base.Pair;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static equ.clt.ProcessMonitorEquipmentServer.notInterrupted;
import static equ.clt.ProcessMonitorEquipmentServer.notInterruptedTransaction;
import static equ.clt.handler.HandlerUtils.*;
import static lsfusion.base.BaseUtils.nvl;
import static lsfusion.base.BaseUtils.trimToEmpty;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@SuppressWarnings("SqlDialectInspection")
public class AstronHandler extends DefaultCashRegisterHandler<AstronSalesBatch, CashDocumentBatch> {

    protected final static Logger astronLogger = Logger.getLogger("AstronLogger");
    protected final static Logger astronSalesLogger = Logger.getLogger("AstronSalesLogger");
    protected final static Logger astronSqlLogger = Logger.getLogger("AstronSqlLogger");

    private static String PROPERTYGRP = "PROPERTYGRP";
    private static String NUMPROPERTY = "NUMPROPERTY";
    private static String STRINGS = "STRINGS";
    private static String NUMBERS = "NUMBERS";
    private static String BINARYDATA = "BINARYDATA";
    private static String BINPROPERTY = "BINPROPERTY";
    private static String EXTGRP = "EXTGRP";
    private static String ARTEXTGRP = "ARTEXTGRP";
    private static String ARTPRNGRP = "ARTPRNGRP";

    private static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    private FileSystemXmlApplicationContext springContext;

    public AstronHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        return "astron";
    }

    private final Set<String> connectionSemaphore = new HashSet<>();
    private void connectionSemaphoreAdd(AstronConnectionString params, String directory, String logInfo) {
        connectionSemaphore.add(params.connectionString);
        astronLogger.info(String.format("export %s to %s started", logInfo, directory));
    }
    private void connectionSemaphoreRemove(AstronConnectionString params, String directory, String logInfo) {
        connectionSemaphore.remove(params.connectionString);
        astronLogger.info(String.format("export %s to %s finished", logInfo, directory));
    }

    private final Set<String> updateTables = new HashSet<>();
    private final Map<String, Map<String, Integer>> directoryOutputUpdateNumsMap = new HashMap<>();
    private final Map<String, Map<String, Integer>> directoryInputUpdateNumsMap = new HashMap<>();

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) {
        AstronSettings astronSettings = getSettings();
        Integer timeout = nvl(astronSettings.getTimeout(), 300);
        boolean exportExtraTables = astronSettings.isExportExtraTables();
        Integer transactionsAtATime = astronSettings.getTransactionsAtATime();
        Integer itemsAtATime = astronSettings.getItemsAtATime();
        Integer maxBatchSize = astronSettings.getMaxBatchSize();
        boolean isVersionalScheme = astronSettings.isVersionalScheme();
        boolean deleteBarcodeInSeparateProcess = astronSettings.isDeleteBarcodeInSeparateProcess();
        boolean usePropertyGridFieldInPackTable = astronSettings.isUsePropertyGridFieldInPackTable();
        boolean waitSysLogInsteadOfDataPump = astronSettings.isWaitSysLogInsteadOfDataPump();
        boolean specialSplitMode = astronSettings.isSpecialSplitMode();
        boolean swap10And20VAT = astronSettings.isSwap10And20VAT();

        List<DeleteBarcodeInfo> deleteBarcodeList = new ArrayList<>();
        if (!deleteBarcodeInSeparateProcess) {
            try {
                List<DeleteBarcodeInfo> allDeleteBarcodeList = remote.readDeleteBarcodeInfoList();
                for (DeleteBarcodeInfo deleteBarcodeInfo : allDeleteBarcodeList) {
                    if (deleteBarcodeInfo.handlerModelGroupMachinery.equals(getClass().getName())) {
                        deleteBarcodeList.add(deleteBarcodeInfo);
                    }
                }
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }

        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        if (transactionList != null) {

            Map<String, List<TransactionCashRegisterInfo>> directoryTransactionMap = new HashMap<>();
            for (TransactionCashRegisterInfo transaction : transactionList) {
                directoryTransactionMap.computeIfAbsent(getDirectory(transaction), t -> new ArrayList<>()).add(transaction);
            }

            if(transactionsAtATime > 1 || itemsAtATime > 0) {

                for(Map.Entry<String, List<TransactionCashRegisterInfo>> directoryTransactionEntry : directoryTransactionMap.entrySet()) {

                    int transactionCount = 1;
                    int itemCount = 0;
                    int totalCount = directoryTransactionEntry.getValue().size();

                    Throwable exception = null;
                    Map<Long, SendTransactionBatch> currentSendTransactionBatchMap = new HashMap<>();
                    for (TransactionCashRegisterInfo transaction : directoryTransactionEntry.getValue()) {
                        itemCount += transaction.itemsList.size();
                        boolean firstTransaction = transactionCount == 1;
                        boolean lastTransaction = transactionCount == transactionsAtATime || transactionCount == totalCount || (itemsAtATime > 0 && itemCount >= itemsAtATime);

                        SendTransactionBatch batch = exportTransaction(transaction, exception, firstTransaction, lastTransaction, directoryTransactionEntry.getKey(),
                                exportExtraTables, deleteBarcodeList, timeout, maxBatchSize, isVersionalScheme, transactionCount, itemCount, usePropertyGridFieldInPackTable,
                                waitSysLogInsteadOfDataPump, specialSplitMode, swap10And20VAT);
                        exception = batch.exception;

                        currentSendTransactionBatchMap.put(transaction.id, batch);

                        if (lastTransaction) {
                            for(SendTransactionBatch batchEntry : currentSendTransactionBatchMap.values()) {
                                if(batchEntry.exception == null) {
                                    batchEntry.exception = exception;
                                }
                            }
                            sendTransactionBatchMap.putAll(currentSendTransactionBatchMap);
                            currentSendTransactionBatchMap = new HashMap<>();
                            totalCount -= transactionCount;
                            transactionCount = 1;
                            itemCount = 0;
                        } else {
                            transactionCount++;
                        }
                    }
                }

            } else {
                for(Map.Entry<String, List<TransactionCashRegisterInfo>> directoryTransactionEntry : directoryTransactionMap.entrySet()) {
                    Throwable exception = null;
                    for (TransactionCashRegisterInfo transaction : directoryTransactionEntry.getValue()) {
                        SendTransactionBatch batch = exportTransaction(transaction, exception, true, true, directoryTransactionEntry.getKey(),
                                    exportExtraTables, deleteBarcodeList, timeout, maxBatchSize, isVersionalScheme, 1, transaction.itemsList.size(),
                                usePropertyGridFieldInPackTable, waitSysLogInsteadOfDataPump, specialSplitMode, swap10And20VAT);
                        exception = batch.exception;

                        sendTransactionBatchMap.put(transaction.id, batch);
                    }
                }
            }
        }
        return sendTransactionBatchMap;
    }

    private String getDirectory(TransactionCashRegisterInfo transaction) {
        String directory = null;
        for (CashRegisterInfo cashRegister : transaction.machineryInfoList) {
            if (cashRegister.directory != null) {
                directory = cashRegister.directory;
            }
        }
        return directory;
    }

    private SendTransactionBatch exportTransaction(TransactionCashRegisterInfo transaction, Throwable exception, boolean firstTransaction, boolean lastTransaction, String directory,
                                        boolean exportExtraTables, List<DeleteBarcodeInfo> deleteBarcodeList,
                                        Integer timeout, Integer maxBatchSize, boolean versionalScheme, int transactionCount, int itemCount, boolean usePropertyGridFieldInPackTable,
                                        boolean waitSysLogInsteadOfDataPump, boolean specialSplitMode, boolean swap10And20VAT) {
        Set<String> deleteBarcodeSet = new HashSet<>();
        if(exception == null) {
            AstronConnectionString params = new AstronConnectionString(directory);
            if (params.connectionString == null) {
                exception = createException("no connectionString found");
            } else {
                exception = waitConnectionSemaphore(params, timeout);
                if (exception == null) {
                    String logInfo = "transaction " + transaction.id;
                    try (Connection conn = getConnection(params)) {
                        connectionSemaphoreAdd(params, directory, logInfo);

                        Map<String, CashRegisterItem> deleteBarcodeMap = new HashMap<>();
                        for (DeleteBarcodeInfo deleteBarcode : deleteBarcodeList) {
                            if (directory.equals(deleteBarcode.directoryGroupMachinery)) {
                                for (CashRegisterItem item : deleteBarcode.barcodeList) {
//                                    astronLogger.info(String.format("Transaction %s, deleteBarcode item %s, barcode %s", transaction.id, item.idItem, item.idBarcode));
                                    deleteBarcodeMap.put(item.idItem, item);
                                }
                            }
                        }

                        Map<String, List<JSONObject>> jsonTables = getJsonTables(transaction);

                        //Читаем статус и чистим все таблицы с которыми можем в теории работать
                        Set<String> truncateTables = new HashSet<>(Arrays.asList("GRP", "ART", "UNIT", "PACK", "EXBARC", "PACKPRC", PROPERTYGRP, NUMPROPERTY, STRINGS, NUMBERS, BINARYDATA, BINPROPERTY, EXTGRP, ARTEXTGRP, ARTPRNGRP));
                        if(exportExtraTables) {
                            truncateTables.add("PRCLEVEL");
                            truncateTables.add("SAREA");
                            truncateTables.add("SAREAPRC");
                        }
                        String prevTables = truncateTables.stream().collect(Collectors.joining("','", "'", "'"));

                        Map<String, Integer> processedUpdateNums = versionalScheme ? readProcessedUpdateNums(conn, prevTables, params) : new HashMap<>();

                        if (firstTransaction) {
                            if (versionalScheme) {
                                directoryInputUpdateNumsMap.put(directory, readUpdateNums(conn, prevTables));
                            } else  {
                                Exception waitFlagsResult = waitFlags(conn, params, prevTables, timeout);
                                if (waitFlagsResult != null) {
                                    throw new RuntimeException("data from previous transactions was not processed (flags not set to zero)");
                                }
                                truncateTables(conn, params, String.valueOf(transaction.id), truncateTables);
                            }
                        }

                        List<CashRegisterItem> usedDeleteBarcodeList = new ArrayList<>();
                        transaction.itemsList = transaction.itemsList.stream().filter(item -> isValidItem(transaction, deleteBarcodeMap, usedDeleteBarcodeList, item)).collect(Collectors.toList());

                        if (!transaction.itemsList.isEmpty()) {

                            String eventTime = getEventTime(conn, waitSysLogInsteadOfDataPump);

                            checkItems(params, transaction.itemsList, transaction.id);

                            Map<String, Integer> inputUpdateNums = directoryInputUpdateNumsMap.get(directory);
                            Map<String, Integer> outputUpdateNums = directoryOutputUpdateNumsMap.getOrDefault(directory, new HashMap<>());

                            if (notInterruptedTransaction(transaction.id)) {
                                Integer grpUpdateNum = getTransactionUpdateNum(transaction, versionalScheme, processedUpdateNums, inputUpdateNums, outputUpdateNums, "GRP");
                                exportGrp(conn, params, transaction, maxBatchSize, grpUpdateNum);
                            }

                            if (notInterruptedTransaction(transaction.id)) {
                                Integer artUpdateNum = getTransactionUpdateNum(transaction, versionalScheme, processedUpdateNums, inputUpdateNums, outputUpdateNums, "ART");
                                exportArt(conn, params, transaction.itemsList, swap10And20VAT, maxBatchSize, artUpdateNum);
                            }

                            if (notInterruptedTransaction(transaction.id)) {
                                Integer unitUpdateNum = getTransactionUpdateNum(transaction, versionalScheme, processedUpdateNums, inputUpdateNums, outputUpdateNums, "UNIT");
                                exportUnit(conn, params, transaction.itemsList, null, maxBatchSize, unitUpdateNum);
                            }

                            List<JSONObject> propertyGrpJsonTable = jsonTables.get(PROPERTYGRP);
                            if(!propertyGrpJsonTable.isEmpty()) {
                                Integer updateNum = getTransactionUpdateNum(transaction, versionalScheme, processedUpdateNums, inputUpdateNums, outputUpdateNums, PROPERTYGRP);
                                exportPropertyGrp(conn, params, propertyGrpJsonTable, updateNum);
                            }

                            if (notInterruptedTransaction(transaction.id)) {
                                Integer packUpdateNum = getTransactionUpdateNum(transaction, versionalScheme, processedUpdateNums, inputUpdateNums, outputUpdateNums, "PACK");
                                exportPack(conn, params, transaction.itemsList, null, false, maxBatchSize, packUpdateNum, usePropertyGridFieldInPackTable, specialSplitMode);
                                astronLogger.info(String.format("transaction %s, table pack delete : " + usedDeleteBarcodeList.size(), transaction.id));
                                exportPackDeleteBarcode(conn, params, usedDeleteBarcodeList, maxBatchSize, packUpdateNum);
                            }

                            if (notInterruptedTransaction(transaction.id)) {
                                Integer exBarcUpdateNum = getTransactionUpdateNum(transaction, versionalScheme, processedUpdateNums, inputUpdateNums, outputUpdateNums, "EXBARC");
                                exportExBarc(conn, params, transaction.itemsList, false, maxBatchSize, exBarcUpdateNum);
                                astronLogger.info(String.format("transaction %s, table exbarc delete", transaction.id));
                                exportExBarcDeleteBarcode(conn, params, usedDeleteBarcodeList, maxBatchSize, exBarcUpdateNum);
                            }

                            Set<Integer> hasExtraPrices = getHasExtraPrices(transaction);

                            //таблицы PRCLEVEL, SAREA, SAREAPRC должны выгружаться раньше, чем PACKPRC
                            if (exportExtraTables) {
                                Integer prcLevelUpdateNum = getTransactionUpdateNum(transaction, versionalScheme, processedUpdateNums, inputUpdateNums, outputUpdateNums, "PRCLEVEL");
                                exportPrcLevel(conn, params, transaction, hasExtraPrices, prcLevelUpdateNum);

                                Integer sareaUpdateNum = getTransactionUpdateNum(transaction, versionalScheme, processedUpdateNums, inputUpdateNums, outputUpdateNums, "SAREA");
                                exportSArea(conn, params, transaction, sareaUpdateNum);

                                Integer sareaPrcUpdateNum = getTransactionUpdateNum(transaction, versionalScheme, processedUpdateNums, inputUpdateNums, outputUpdateNums, "SAREAPRC");
                                exportSAreaPrc(conn, params, transaction, hasExtraPrices, sareaPrcUpdateNum);
                            }

                            Integer packPrcUpdateNum = getTransactionUpdateNum(transaction, versionalScheme, processedUpdateNums, inputUpdateNums, outputUpdateNums, "PACKPRC");
                            exportPackPrc(conn, params, transaction, exportExtraTables, maxBatchSize, packPrcUpdateNum);
                            //удаление выгружается только с одной из групп касс, и удаление только цены одной группы все равно имеет мало смысла
                            //полагаемся на то что удаления самого штрихкода достаточно
                            //astronLogger.info(String.format("transaction %s, table packprc delete", transaction.id));
                            //exportPackPrcDeleteBarcode(conn, params, transaction, usedDeleteBarcodeList, exportExtraTables, maxBatchSize, packPrcUpdateNum);

                            List<JSONObject> numbersJsonTable = jsonTables.get(NUMBERS);
                            if(!numbersJsonTable.isEmpty()) {
                                Integer updateNum = getTransactionUpdateNum(transaction, versionalScheme, processedUpdateNums, inputUpdateNums, outputUpdateNums, NUMBERS);
                                exportNumbers(conn, params, numbersJsonTable, updateNum);
                            }

                            List<JSONObject> numPropertyJsonTable = jsonTables.get(NUMPROPERTY);
                            if(!numPropertyJsonTable.isEmpty()) {
                                Integer updateNum = getTransactionUpdateNum(transaction, versionalScheme, processedUpdateNums, inputUpdateNums, outputUpdateNums, NUMPROPERTY);
                                exportNumProperty(conn, params, numPropertyJsonTable, updateNum);
                            }

                            List<JSONObject> stringsJsonTable = jsonTables.get(STRINGS);
                            if (!stringsJsonTable.isEmpty()) {
                                Integer updateNum = getTransactionUpdateNum(transaction, versionalScheme, processedUpdateNums, inputUpdateNums, outputUpdateNums, STRINGS);
                                exportStrings(conn, params, stringsJsonTable, updateNum);
                            }

                            List<JSONObject> binaryDataJsonTable = jsonTables.get(BINARYDATA);
                            if(!binaryDataJsonTable.isEmpty()) {
                                Integer updateNum = getTransactionUpdateNum(transaction, versionalScheme, processedUpdateNums, inputUpdateNums, outputUpdateNums, BINARYDATA);
                                exportBinaryData(conn, params, binaryDataJsonTable, updateNum);
                            }

                            List<JSONObject> binPropertyJsonTable = jsonTables.get(BINPROPERTY);
                            if(!binPropertyJsonTable.isEmpty()) {
                                Integer updateNum = getTransactionUpdateNum(transaction, versionalScheme, processedUpdateNums, inputUpdateNums, outputUpdateNums, BINPROPERTY);
                                exportBinProperty(conn, params, binPropertyJsonTable, updateNum);
                            }

                            List<JSONObject> extGrpJsonTable = jsonTables.get(EXTGRP);
                            if(!extGrpJsonTable.isEmpty()) {
                                Integer updateNum = getTransactionUpdateNum(transaction, versionalScheme, processedUpdateNums, inputUpdateNums, outputUpdateNums, EXTGRP);
                                exportExtGrp(conn, params, extGrpJsonTable, updateNum);
                            }

                            List<JSONObject> artExtGrpJsonTable = jsonTables.get(ARTEXTGRP);
                            if(!artExtGrpJsonTable.isEmpty()) {
                                Integer updateNum = getTransactionUpdateNum(transaction, versionalScheme, processedUpdateNums, inputUpdateNums, outputUpdateNums, ARTEXTGRP);
                                exportArtExtGrp(conn, params, artExtGrpJsonTable, updateNum);
                            }

                            List<JSONObject> artPrnGrpJsonTable = jsonTables.get(ARTPRNGRP);
                            if(!artPrnGrpJsonTable.isEmpty()) {
                                Integer updateNum = getTransactionUpdateNum(transaction, versionalScheme, processedUpdateNums, inputUpdateNums, outputUpdateNums, ARTPRNGRP);
                                exportArtPrnGrp(conn, params, artPrnGrpJsonTable, updateNum);
                            }

                            directoryOutputUpdateNumsMap.put(directory, outputUpdateNums);

                            if (lastTransaction) {
                                if (versionalScheme) {
                                    astronLogger.info(String.format("set updateNum for %s transaction(s) with %s item(s)", transactionCount, itemCount));
                                    exportUpdateNums(conn, outputUpdateNums);
                                    directoryInputUpdateNumsMap.remove(directory);
                                    directoryOutputUpdateNumsMap.remove(directory);
                                } else {
                                    astronLogger.info(String.format("waiting for processing %s transaction(s) with %s item(s)", transactionCount, itemCount));
                                    String newTables = updateTables.stream().collect(Collectors.joining("','", "'", "'"));
                                    updateTables.clear();
                                    exportFlags(conn, newTables, 1);
                                    Exception e = waitFlags(conn, params, newTables, timeout, eventTime, waitSysLogInsteadOfDataPump);
                                    if (e != null) {
                                        throw e;
                                    }
                                }
                            }

                            for (CashRegisterItem usedDeleteBarcode : usedDeleteBarcodeList) {
                                deleteBarcodeSet.add(usedDeleteBarcode.idBarcode);
                            }
                        }

                    } catch (Exception e) {
                        astronLogger.error("exportTransaction error", e);
                        exception = e;
                    } finally {
                        connectionSemaphoreRemove(params, directory, logInfo);
                    }
                } else {
                    astronLogger.error("semaphore transaction timeout", exception);
                }
            }
        }
        return new SendTransactionBatch(null, null, transaction.nppGroupMachinery, deleteBarcodeSet, exception);
    }

    private Integer getTransactionUpdateNum(boolean versionalScheme, Map<String, Integer> inputUpdateNums, String tbl) {
        return versionalScheme ? (inputUpdateNums.getOrDefault(tbl, 0) + 1) : null;
    }

    private Integer getTransactionUpdateNum(TransactionCashRegisterInfo transaction, boolean versionalScheme, Map<String, Integer> processedUpdateNums, Map<String, Integer> inputUpdateNums, Map<String, Integer> outputUpdateNums, String tbl) {
        Integer updateNum = versionalScheme ? (inputUpdateNums.getOrDefault(tbl, 0) + 1) : null;
        outputUpdateNums.put(tbl, updateNum);
        updateTables.add(tbl);
        astronLogger.info(String.format("transaction %s, table %s", transaction.id, tbl) +
                (versionalScheme ? String.format(" (updateNum processed %s, new %s)", processedUpdateNums.get(tbl), updateNum) : ""));
        return updateNum;
    }

    private void checkItems(AstronConnectionString params, List<CashRegisterItem> items, Long transactionId) {
        List<String> invalidItems = new ArrayList<>();
        if (params.pgsql) {
            for (CashRegisterItem item : items) {
                String grpId = parseGroup(item.extIdItemGroup);
                if (grpId == null || grpId.isEmpty()) {
                    invalidItems.add(item.idBarcode + "(" + item.extIdItemGroup + ")");
                }
            }
        }
        if (!invalidItems.isEmpty()) {
            throw new RuntimeException("failed to parse GRPID for barcodes " + StringUtils.join(invalidItems, ",") + (transactionId != null ? (", transaction " + transactionId) : ""));
        }
    }

    private void exportGrp(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction, Integer maxBatchSize, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"GRPID"};
        String[] columns = getColumns(new String[]{"GRPID", "PARENTGRPID", "GRPNAME", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, "GRP", columns, keys)) {
            int offset = columns.length + keys.length;

            int batchCount = 0;
            Set<String> usedGrp = new HashSet<>();
            for (int i = 0; i < transaction.itemsList.size(); i++) {
                List<ItemGroup> itemGroupList = transaction.itemGroupMap.get(transaction.itemsList.get(i).extIdItemGroup);
                if (itemGroupList != null) {
                    for (ItemGroup itemGroup : itemGroupList) {
                        if (!Thread.currentThread().isInterrupted()) {
                            String idGroup = parseGroup(itemGroup.extIdItemGroup);
                            if (idGroup != null && !idGroup.isEmpty() && !usedGrp.contains(idGroup)) {
                                if(params.pgsql) {
                                    String parentId = parseGroup(itemGroup.idParentItemGroup, true);
                                    setObject(ps, Integer.parseInt(idGroup), 1); //GRPID
                                    setObject(ps, parentId != null ? Integer.parseInt(parentId) : parentId, 2); //PARENTGRPID
                                    setObject(ps, trim(itemGroup.nameItemGroup, "", 50), 3); //GRPNAME
                                    setObject(ps, 0, 4); //DELFLAG
                                    if(updateNum != null)
                                        setObject(ps, updateNum, 5);
                                } else {
                                    setObject(ps, parseGroup(itemGroup.idParentItemGroup, true), 1, offset); //PARENTGRPID
                                    setObject(ps, trim(itemGroup.nameItemGroup, "", 50), 2, offset); //GRPNAME
                                    setObject(ps, "0", 3, offset); //DELFLAG
                                    if(updateNum != null)
                                        setObject(ps, updateNum, 4, offset);

                                    setObject(ps, parseGroup(itemGroup.extIdItemGroup), updateNum != null ? 5 : 4, keys.length); //GRPID
                                }
                                ps.addBatch();
                                batchCount++;
                                if(maxBatchSize != null && batchCount == maxBatchSize) {
                                    executeAndCommitBatch(ps, conn);
                                    batchCount = 0;
                                }
                                usedGrp.add(idGroup);
                            }
                        } else break;
                    }
                }
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private void exportArt(Connection conn, AstronConnectionString params, List<? extends ItemInfo> itemsList, boolean swap10And20VAT, Integer maxBatchSize, Integer updateNum) throws SQLException, UnsupportedEncodingException {
        String[] keys = new String[]{"ARTID"};
        String[] columns = getColumns(new String[]{"ARTID", "GRPID", "TAXGRPID", "ARTCODE", "ARTNAME", "ARTSNAME", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, "ART", columns, keys)) {
            int offset = columns.length + keys.length;

            int batchCount = 0;
            for (ItemInfo item : itemsList) {
                if (!Thread.currentThread().isInterrupted()) {
                    Integer idItem = parseIdItem(item);
                    String grpId = getExtIdItemGroup(item);
                    if (grpId != null && !grpId.isEmpty()) {
                        if (params.pgsql) {
                            setObject(ps, idItem, 1); //ARTID
                            setObject(ps, Integer.parseInt(grpId), 2); //GRPID
                            setObject(ps, getIdVAT(item, swap10And20VAT), 3); //TAXGRPID
                            setObject(ps, idItem, 4); //ARTCODE
                            setObject(ps, getItemName(item), 5); //ARTNAME
                            setObject(ps, getItemName(item), 6); //ARTSNAME
                            setObject(ps, 0, 7); //DELFLAG
                            if (updateNum != null) setObject(ps, updateNum, 8); //UPDATENUM
                        } else {
                            setObject(ps, grpId, 1, offset); //GRPID
                            setObject(ps, getIdVAT(item, swap10And20VAT), 2, offset); //TAXGRPID
                            setObject(ps, idItem, 3, offset); //ARTCODE
                            setObject(ps, getItemName(item), 4, offset); //ARTNAME
                            setObject(ps, getItemName(item), 5, offset); //ARTSNAME
                            setObject(ps, "0", 6, offset); //DELFLAG
                            if (updateNum != null) setObject(ps, updateNum, 7, offset); //UPDATENUM

                            setObject(ps, idItem, updateNum != null ? 8 : 7, keys.length); //ARTID

                        }
                    } else {
                        throw new RuntimeException(String.format("item %s, extIdItemGroup is empty", item.idItem));
                    }

                    ps.addBatch();
                    batchCount++;
                    if(maxBatchSize != null && batchCount == maxBatchSize) {
                        executeAndCommitBatch(ps, conn);
                        batchCount = 0;
                    }
                } else break;
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private Integer getIdVAT(ItemInfo item, boolean swap10And20VAT) {
        for(JSONObject infoJSON : getExtInfo(item.info).jsonObjects) {
            if(infoJSON.has("idvat")) {
                return infoJSON.getInt("idvat");
            }
        }
        Integer result = 0;
        if (item.vat != null) {
            if (item.vat.compareTo(BigDecimal.valueOf(10)) == 0) {
                result = swap10And20VAT ? 2 : 1;
            } else if (item.vat.compareTo(BigDecimal.valueOf(20)) == 0) {
                result = swap10And20VAT ? 3 : 2;
            }
        }
        return result;
    }

    private void exportPropertyGrp(Connection conn, AstronConnectionString params, List<JSONObject> jsonTable, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"PROPERTYGRPID"};
        String[] columns = getColumns(new String[]{"PROPERTYGRPID", "PROPERTYGRPNAME", "PROPERTYGRPTYPE", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, PROPERTYGRP, columns, keys)) {
            int offset = columns.length + keys.length;
            for (JSONObject jsonObject : jsonTable) {
                if (params.pgsql) {
                    setObject(ps, jsonObject.getInt("propertyGrpId"), 1);
                    setObject(ps, trim(jsonObject.getString("propertyGrpName"), 50), 2);
                    setObject(ps, 0, 3);
                    setObject(ps, 0, 4);
                    if (updateNum != null) {
                        setObject(ps, updateNum, 5);
                    }
                } else{
                    setObject(ps, trim(jsonObject.getString("propertyGrpName"), 50), 1, offset);
                    setObject(ps, 0, 2, offset);
                    setObject(ps, 0, 3, offset);
                    if (updateNum != null) {
                        setObject(ps, updateNum, 4, offset);
                    }
                    setObject(ps, jsonObject.getInt("propertyGrpId"), updateNum != null ? 5 : 4, keys.length);
                }
                ps.addBatch();
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private void exportNumProperty(Connection conn, AstronConnectionString params, List<JSONObject> jsonTable, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"NUMPROPERTYKEY","PROPERTYGRPID"};
        String[] columns = getColumns(new String[]{"NUMPROPERTYKEY", "PROPERTYGRPID", "NUMBERID", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, NUMPROPERTY, columns, keys)) {
            int offset = columns.length + keys.length;

            for (JSONObject jsonObject : jsonTable) {
                if (params.pgsql) {
                    setObject(ps, jsonObject.getInt("numPropertyKey"), 1);
                    setObject(ps, jsonObject.getInt("propertyGrpId"), 2);
                    setObject(ps, jsonObject.getInt("numberId"), 3);
                    setObject(ps, 0, 4);
                    if (updateNum != null) {
                        setObject(ps, updateNum, 5);
                    }
                } else {
                    setObject(ps, jsonObject.getInt("numberId"), 1, offset);
                    setObject(ps, 0, 2, offset);
                    if (updateNum != null) {
                        setObject(ps, updateNum, 3, offset);
                    }

                    setObject(ps, jsonObject.getInt("numPropertyKey"), updateNum != null ? 4 : 3, keys.length);
                    setObject(ps, jsonObject.getInt("propertyGrpId"), updateNum != null ? 5 : 4, keys.length);
                }
                ps.addBatch();
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private void exportStrings(Connection conn, AstronConnectionString params, List<JSONObject> jsonTable, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"STRINGID"};
        String[] columns = getColumns(new String[]{"STRINGID", "STRINGVALUE", "STRINGNAME", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, STRINGS, columns, keys)) {
            int offset = columns.length + keys.length;
            for (JSONObject jsonObject : jsonTable) {
                if (params.pgsql) {
                    setObject(ps, jsonObject.getInt("stringId"), 1);
                    setObject(ps, trim(jsonObject.getString("stringValue"), 1024), 2);
                    setObject(ps, trim(jsonObject.getString("stringName"), 50), 3);
                    setObject(ps, 0, 4);
                    if (updateNum != null) {
                        setObject(ps, updateNum, 5);
                    }
                } else{
                    setObject(ps, trim(jsonObject.getString("stringValue"), 1024), 1, offset);
                    setObject(ps, trim(jsonObject.getString("stringName"), 50), 2, offset);
                    setObject(ps, 0, 3, offset);
                    if (updateNum != null) {
                        setObject(ps, updateNum, 4, offset);
                    }
                    setObject(ps, jsonObject.getInt("stringId"), updateNum != null ? 5 : 4, keys.length);
                }
                ps.addBatch();
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private void exportNumbers(Connection conn, AstronConnectionString params, List<JSONObject> jsonTable, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"NUMBERID"};
        String[] columns = getColumns(new String[]{"NUMBERID", "NUMBERVALUE", "NUMBERNAME", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, NUMBERS, columns, keys)) {
            int offset = columns.length + keys.length;
            for (JSONObject jsonObject : jsonTable) {
                if (params.pgsql) {
                    setObject(ps, jsonObject.getInt("numberId"), 1);
                    setObject(ps, jsonObject.getBigDecimal("numberValue"), 2);
                    setObject(ps, trim(jsonObject.getString("numberName"), 50), 3);
                    setObject(ps, 0, 4);
                    if (updateNum != null) {
                        setObject(ps, updateNum, 5);
                    }
                } else{
                    setObject(ps, jsonObject.getBigDecimal("numberValue"), 1, offset);
                    setObject(ps, trim(jsonObject.getString("numberName"), 50), 2, offset);
                    setObject(ps, 0, 3, offset);
                    if (updateNum != null) {
                        setObject(ps, updateNum, 4, offset);
                    }
                    setObject(ps, jsonObject.getInt("numberId"), updateNum != null ? 5 : 4, keys.length);
                }
                ps.addBatch();
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private void exportBinaryData(Connection conn, AstronConnectionString params, List<JSONObject> jsonTable, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"BINARYDATAID"};
        String[] columns = getColumns(new String[]{"BINARYDATAID", "BINARYDATAVALUE", "BYNARYDATANAME", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, BINARYDATA, columns, keys)) {
            int offset = columns.length + keys.length;
            for (JSONObject jsonObject : jsonTable) {
                if (params.pgsql) {
                    setObject(ps, jsonObject.getInt("binaryDataId"), 1);
                    setObject(ps, Base64.decodeBase64(jsonObject.getString("binaryDataValue")), 2);
                    setObject(ps, jsonObject.getString("binaryDataName"), 3);
                    setObject(ps, 0, 4);
                    if (updateNum != null) {
                        setObject(ps, updateNum, 5);
                    }
                } else{
                    setObject(ps, Base64.decodeBase64(jsonObject.getString("binaryDataValue")), 1, offset);
                    setObject(ps, jsonObject.getString("binaryDataName"), 2, offset);
                    setObject(ps, 0, 3, offset);
                    if (updateNum != null) {
                        setObject(ps, updateNum, 4, offset);
                    }
                    setObject(ps, jsonObject.getInt("binaryDataId"), updateNum != null ? 5 : 4, keys.length);
                }
                ps.addBatch();
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private void exportBinProperty(Connection conn, AstronConnectionString params, List<JSONObject> jsonTable, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"BINPROPERTYKEY", "PROPERTYGRPID"};
        String[] columns = getColumns(new String[]{"BINPROPERTYKEY", "PROPERTYGRPID", "BINARYDATAID", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, BINPROPERTY, columns, keys)) {
            int offset = columns.length + keys.length;

            for (JSONObject jsonObject : jsonTable) {
                if (params.pgsql) {
                    setObject(ps, jsonObject.getInt("binPropertyKey"), 1);
                    setObject(ps, jsonObject.getInt("propertyGrpId"), 2);
                    setObject(ps, jsonObject.getInt("binaryDataId"), 3);
                    setObject(ps, 0, 4);
                    if (updateNum != null) {
                        setObject(ps, updateNum, 5);
                    }
                } else {
                    setObject(ps, jsonObject.getInt("binaryDataId"), 1, offset);
                    setObject(ps, 0, 2, offset);
                    if (updateNum != null) {
                        setObject(ps, updateNum, 3, offset);
                    }

                    setObject(ps, jsonObject.getInt("binPropertyKey"), updateNum != null ? 4 : 3, keys.length);
                    setObject(ps, jsonObject.getInt("propertyGrpId"), updateNum != null ? 5 : 4, keys.length);
                }
                ps.addBatch();
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private void exportExtGrp(Connection conn, AstronConnectionString params, List<JSONObject> jsonTable, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"EXTGRPID"};
        String[] columns = getColumns(new String[]{"EXTGRPID","SAREAID", "PARENTEXTGRPID", "EXTGRPNAME", "EXTGRPPICTURE", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, EXTGRP, columns, keys)) {
            int offset = columns.length + keys.length;
            for (JSONObject jsonObject : jsonTable) {
                int parentExtGrpId = jsonObject.optInt("parentExtGrpId");
                String extGrpPicture = jsonObject.optString("extGrpPicture", null);
                if (params.pgsql) {
                    setObject(ps, jsonObject.getInt("extGrpId"), 1);
                    setObject(ps, jsonObject.getInt("sareaId"), 2);
                    setObject(ps, parentExtGrpId > 0 ? parentExtGrpId : null, 3);
                    setObject(ps, trim(jsonObject.getString("extGrpName"), 50), 4);
                    setObject(ps, extGrpPicture != null ? Base64.decodeBase64(extGrpPicture) : null, 5);
                    setObject(ps, 0, 6);
                    if (updateNum != null) {
                        setObject(ps, updateNum, 7);
                    }
                } else{
                    setObject(ps, jsonObject.getInt("sareaId"), 1, offset);
                    setObject(ps, parentExtGrpId > 0 ? parentExtGrpId : null, 2, offset);
                    setObject(ps, trim(jsonObject.getString("extGrpName"), 50), 3, offset);
                    setObject(ps, extGrpPicture != null ? Base64.decodeBase64(extGrpPicture) : null, 4, offset);
                    setObject(ps, 0, 5, offset);
                    if (updateNum != null) {
                        setObject(ps, updateNum, 6);
                    }
                    setObject(ps, jsonObject.getInt("extGrpId"), updateNum != null ? 7 : 6, keys.length);
                }
                ps.addBatch();
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private void exportArtExtGrp(Connection conn, AstronConnectionString params, List<JSONObject> jsonTable, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"ARTID","EXTGRPID"};
        String[] columns = getColumns(new String[]{"ARTID", "EXTGRPID", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, ARTEXTGRP, columns, keys)) {
            int offset = columns.length + keys.length;

            for (JSONObject jsonObject : jsonTable) {
                    Integer artId = getIdItem(jsonObject);
                    Integer extGrpId = jsonObject.getInt("extGrpId");
                    Integer delFlag = jsonObject.optInt("delFlag", 0);
                    if (params.pgsql) {
                        setObject(ps, artId, 1); //ARTID
                        setObject(ps, extGrpId, 2); //EXTGRPID
                        setObject(ps, delFlag, 3); //DELFLAG
                        if (updateNum != null) {
                            setObject(ps, updateNum, 4); //UPDATENUM
                        }
                    } else {
                        setObject(ps, delFlag, 1, offset); //DELFLAG
                        if (updateNum != null) {
                            setObject(ps, updateNum, 2, offset); //UPDATENUM
                        }

                        setObject(ps, artId, updateNum != null ? 3 : 2, keys.length); //ARTID
                        setObject(ps, extGrpId, updateNum != null ? 4 : 3, keys.length); //EXTGRPID
                    }
                    ps.addBatch();
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private void exportArtPrnGrp(Connection conn, AstronConnectionString params, List<JSONObject> jsonTable, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"ARTID","PRNGRPID"};
        String[] columns = getColumns(new String[]{"ARTID", "PRNGRPID", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, ARTPRNGRP, columns, keys)) {
            int offset = columns.length + keys.length;

            for (JSONObject jsonObject : jsonTable) {
                Integer artId = getIdItem(jsonObject);
                Integer prnGrpId = jsonObject.getInt("prnGrpId");
                if (params.pgsql) {
                    setObject(ps, artId, 1); //ARTID
                    setObject(ps, prnGrpId, 2); //PRNGRPID
                    setObject(ps, 0, 3); //DELFLAG
                    if (updateNum != null) {
                        setObject(ps, updateNum, 4); //UPDATENUM
                    }
                } else {
                    setObject(ps, 0, 1, offset); //DELFLAG
                    if (updateNum != null) {
                        setObject(ps, updateNum, 2, offset); //UPDATENUM
                    }

                    setObject(ps, artId, updateNum != null ? 3 : 2, keys.length); //ARTID
                    setObject(ps, prnGrpId, updateNum != null ? 4 : 3, keys.length); //PRNGRPID
                }
                ps.addBatch();
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    //добавляется вручную на этапе чтения json
    private Integer getIdItem(JSONObject jsonObject) {
        try {
            return jsonObject.getInt("idItem");
        } catch (Exception e) {
            throw new RuntimeException("Failed to read idItem", e);
        }
    }

    private void exportUnit(Connection conn, AstronConnectionString params, List<? extends ItemInfo> itemsList, MachineryInfo machinery, Integer maxBatchSize, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"UNITID"};
        String[] columns = getColumns(new String[]{"UNITID", "UNITNAME", "UNITFULLNAME", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, "UNIT", columns, keys)) {
            int offset = columns.length + keys.length;

            Set<Integer> usedUOM = new HashSet<>();
            int batchCount = 0;
            for (ItemInfo item : itemsList) {
                if (!Thread.currentThread().isInterrupted()) {
                    Integer idUOM = parseUOM(item, machinery);
                    if (!usedUOM.contains(idUOM)) {
                        usedUOM.add(idUOM);
                        if (params.pgsql) {
                            setObject(ps, idUOM, 1); //UNITID
                            setObject(ps, item.shortNameUOM, 2); //UNITNAME
                            setObject(ps, item.shortNameUOM, 3); //UNITFULLNAME
                            setObject(ps, 0, 4); //DELFLAG
                            if(updateNum != null)
                                setObject(ps, updateNum, 5); //UNITFULLNAME

                        } else {
                            setObject(ps, item.shortNameUOM, 1, offset); //UNITNAME
                            setObject(ps, item.shortNameUOM, 2, offset); //UNITFULLNAME
                            setObject(ps, "0", 3, offset); //DELFLAG
                            if(updateNum != null)
                                setObject(ps, updateNum, 4, offset); //UNITFULLNAME

                            setObject(ps, idUOM, updateNum != null ? 5 : 4, keys.length); //UNITID
                        }

                        ps.addBatch();
                        batchCount++;
                        if(maxBatchSize != null && batchCount == maxBatchSize) {
                            executeAndCommitBatch(ps, conn);
                            batchCount = 0;
                        }
                    }

                } else break;
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private Integer parseUOM(ItemInfo item, MachineryInfo machinery) {
        if(item instanceof StopListItem && machinery instanceof CashRegisterInfo && ((CashRegisterInfo) machinery).useValueIdUOM) {
            return ((StopListItem) item).innerIdUOM;
        }
        String idUOM = item.idUOM;
        if (idUOM != null) {
            try {
                return Integer.parseInt(idUOM);
            } catch (Exception e) {
                throw new RuntimeException("Unable to cast UOM " + idUOM + " to integer", e);
            }
        }
        return null;
    }

    private void exportPack(Connection conn, AstronConnectionString params, List<? extends ItemInfo> itemsList, MachineryInfo machinery, boolean delFlag, Integer maxBatchSize, Integer updateNum,
                            boolean usePropertyGridFieldInPackTable, boolean specialSplitMode) throws SQLException, UnsupportedEncodingException {
        String[] keys = new String[]{"PACKID"};
        String[] columns = getColumns(
                usePropertyGridFieldInPackTable ?
                        new String[]{"PACKID", "ARTID", "PACKQUANT", "PACKSHELFLIFE", "ISDEFAULT", "UNITID", "QUANTMASK", "PACKDTYPE", "PACKNAME", "DELFLAG", "BARCID", "PROPERTYGRPID"} :
                        new String[]{"PACKID", "ARTID", "PACKQUANT", "PACKSHELFLIFE", "ISDEFAULT", "UNITID", "QUANTMASK", "PACKDTYPE", "PACKNAME", "DELFLAG", "BARCID"}
                , updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, "PACK", columns, keys)) {
            int offset = columns.length + keys.length;

            Set<Integer> idItems = new HashSet<>();
            int batchCount = 0;
            for (ItemInfo item : itemsList) {
                if (notInterrupted()) {
                    Integer idUOM = parseUOM(item, machinery);
                    Integer idItem = parseIdItem(item);
                    List<Integer> packIds = getPackIds(item);

                    int packDType = specialSplitMode && item.splitItem ? 0 : (item.passScalesItem ? 0 : item.splitItem ? 2 : 1);

                    for (Integer packId : packIds) {
                        if (params.pgsql) {
                            setObject(ps, packId, 1); //PACKID
                            setObject(ps, idItem, 2); //ARTID
                            setObject(ps, item.passScalesItem || item.splitItem ? 1000 : 1, 3); //PACKQUANT
                            setObject(ps, 0, 4); //PACKSHELFLIFE
                            if (idItems.contains(idItem)) {
                                setObject(ps, 0, 5); //ISDEFAULT
                            } else {
                                setObject(ps, 1, 5); //ISDEFAULT
                                idItems.add(idItem);
                            }
                            setObject(ps, idUOM, 6); //UNITID
                            setObject(ps, item.splitItem ? 2 : 0, 7); //QUANTMASK
                            setObject(ps, packDType, 8); //PACKDTYPE
                            setObject(ps, getPackName(item), 9); //PACKNAME
                            setObject(ps, delFlag ? 1 : 0, 10); //DELFLAG
                            setObject(ps, item.passScalesItem ? 2 : null, 11); //BARCID
                            int delta = 0;
                            if(usePropertyGridFieldInPackTable) {
                                setObject(ps, getPropertyGrpId(item), 11 + ++delta); //PROPERTYGRPID
                            }
                            if(updateNum != null) {
                                setObject(ps, updateNum, 11 + ++delta);
                            }
                        } else {
                            setObject(ps, idItem, 1, offset); //ARTID
                            setObject(ps, item.passScalesItem || item.splitItem ? "1000" : "1", 2, offset); //PACKQUANT
                            setObject(ps, "0", 3, offset); //PACKSHELFLIFE
                            if (idItems.contains(idItem)) {
                                setObject(ps, false, 4, offset); //ISDEFAULT
                            } else {
                                setObject(ps, true, 4, offset); //ISDEFAULT
                                idItems.add(idItem);
                            }
                            setObject(ps, idUOM, 5, offset); //UNITID
                            setObject(ps, item.splitItem ? 2 : "", 6, offset); //QUANTMASK
                            setObject(ps, packDType, 7, offset); //PACKDTYPE
                            setObject(ps, getPackName(item), 8, offset); //PACKNAME
                            setObject(ps, delFlag ? "1" : "0", 9, offset); //DELFLAG
                            setObject(ps, !specialSplitMode && item.passScalesItem ? "2" : null, 10, offset); //BARCID

                            int delta = 0;
                            if(usePropertyGridFieldInPackTable) {
                                setObject(ps, getPropertyGrpId(item), 10 + ++delta, offset); //PROPERTYGRPID
                            }

                            if(updateNum != null) {
                                setObject(ps, updateNum, 10 + ++delta, offset);
                            }

                            setObject(ps, packId, 10 + ++delta, keys.length); //PACKID
                        }

                        ps.addBatch();
                        batchCount++;
                        if(maxBatchSize != null && batchCount == maxBatchSize) {
                            executeAndCommitBatch(ps, conn);
                            batchCount = 0;
                        }
                    }
                } else break;
            }

            executeAndCommitBatch(ps, conn);

            //по какой-то причине packId может не записаться в таблицу pack. В таком случае, кидаем ошибку
            if(params.pgsql) {
                Set<Integer> failedPackIds = new HashSet<>();
                Set<Integer> readPackIds = readPackIds(conn, params);
                for (ItemInfo item : itemsList) {
                    List<Integer> packIds = getPackIds(item);
                    for (Integer packId : packIds) {
                        if(!readPackIds.contains(packId)) {
                            failedPackIds.add(packId);
                        }
                    }
                }
                if(!failedPackIds.isEmpty()) {
                    throw createException("Failed to write packIds: " + failedPackIds);
                }
            }
        }
    }

    private Set<Integer> readPackIds(Connection conn, AstronConnectionString params) {
        Set<Integer> packIds = new HashSet<>();
        if(params.pgsql) {
            try (Statement statement = conn.createStatement()) {
                ResultSet result = statement.executeQuery("SELECT PACKID FROM PACK");
                while (result.next()) {
                    packIds.add(result.getInt(1));
                }

            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
        return packIds;
    }

    private void exportPackDeleteBarcode(Connection conn, AstronConnectionString params, List<CashRegisterItem> usedDeleteBarcodeList, Integer maxBatchSize, Integer updateNum) throws SQLException, UnsupportedEncodingException {
        String[] keys = new String[]{"PACKID"};
        String[] columns = getColumns(new String[]{"PACKID", "ARTID", "PACKQUANT", "PACKSHELFLIFE", "ISDEFAULT", "UNITID", "QUANTMASK", "PACKDTYPE", "PACKNAME", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, "PACK", columns, keys)) {
            int offset = columns.length + keys.length;

            Set<Integer> idItems = new HashSet<>();
            int batchCount = 0;
            for (CashRegisterItem item : usedDeleteBarcodeList) {
                if (notInterrupted()) {
                    Integer idItem = parseIdItem(item);
                    Integer packId = getPackId(item);
                    if(params.pgsql) {
                        setObject(ps, packId, 1); //PACKID
                        setObject(ps, idItem, 2); //ARTID
                        setObject(ps, item.passScalesItem || item.splitItem ? 1000 : 1, 3); //PACKQUANT
                        setObject(ps, 0, 4); //PACKSHELFLIFE
                        if (idItems.contains(idItem)) {
                            setObject(ps, 0, 5); //ISDEFAULT
                        } else {
                            setObject(ps, 1, 5); //ISDEFAULT
                            idItems.add(idItem);
                        }
                        setObject(ps, 0, 6); //UNITID
                        setObject(ps, 0, 7); //QUANTMASK
                        setObject(ps, item.passScalesItem ? 0 : 1, 8); //PACKDTYPE
                        setObject(ps, getPackName(item), 9); //PACKNAME
                        setObject(ps, 1, 10); //DELFLAG
                        if(updateNum != null)
                            setObject(ps, updateNum, 11);
                    } else {
                        setObject(ps, idItem, 1, offset); //ARTID
                        setObject(ps, item.passScalesItem || item.splitItem ? "1000" : "1", 2, offset); //PACKQUANT
                        setObject(ps, "0", 3, offset); //PACKSHELFLIFE
                        if (idItems.contains(idItem)) {
                            setObject(ps, false, 4, offset); //ISDEFAULT
                        } else {
                            setObject(ps, true, 4, offset); //ISDEFAULT
                            idItems.add(idItem);
                        }
                        setObject(ps, "0", 5, offset); //UNITID
                        setObject(ps, "", 6, offset); //QUANTMASK
                        setObject(ps, item.passScalesItem ? 0 : 1, 7, offset); //PACKDTYPE
                        setObject(ps, getPackName(item), 8, offset); //PACKNAME
                        setObject(ps, "1", 9, offset); //DELFLAG

                        if(updateNum != null)
                            setObject(ps, updateNum, 10, offset);

                        setObject(ps, packId, updateNum != null ? 11 : 10, keys.length); //PACKID
                    }

                    ps.addBatch();
                    batchCount++;
                    if(maxBatchSize != null && batchCount == maxBatchSize) {
                        executeAndCommitBatch(ps, conn);
                        batchCount = 0;
                    }
                } else break;
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private String getPackName(ItemInfo item) throws UnsupportedEncodingException {
        String packName = null;
        for(JSONObject infoJSON : getExtInfo(item.info).jsonObjects) {
            if (infoJSON.has("packName")) {
                packName = infoJSON.getString("packName");
                break;
            }
        }
        return packName != null ? encode(packName) : getItemName(item);
    }

    private String getItemName(ItemInfo item) throws UnsupportedEncodingException {
        return encode(trim(item.name, "", 50));
    }

    //приведение к однобайтной кодировке cp1251. Все символы больше 1 байта не поддерживаются
    private String encode(String value) throws UnsupportedEncodingException {
        return new String(value.getBytes("cp1251"), "cp1251");
    }

    private List<Integer> getPackIds(ItemInfo item) {
        List<Integer> packIds = new ArrayList<>();
        if (item instanceof CashRegisterItem) {
            packIds.add(getPackId((CashRegisterItem) item));
        } else {
            for (Long barcodeObject : ((StopListItem) item).barcodeObjectList) {
                packIds.add(getPackId(barcodeObject));
            }
        }
        return packIds;
    }

    private Integer getPackId(CashRegisterItem item) {
        //Потенциальная опасность, если база перейдёт границу Integer.MAX_VALUE
        return item.barcodeObject.intValue();
    }

    private Integer getPackId(Long barcodeObject) {
        //Потенциальная опасность, если база перейдёт границу Integer.MAX_VALUE
        return barcodeObject.intValue();
    }

    private Integer parseIdItem(ItemInfo item) {
        try {
            return Integer.parseInt(item.idItem);
        } catch (Exception e) {
            return null;
        }
    }

    private String getGTIN(ItemInfo item) {
        String gtin = "";
        for(JSONObject infoJSON : getExtInfo(item.info).jsonObjects) {
            if (infoJSON.has("gtin")) {
                gtin = infoJSON.getString("gtin");
                break;
            }
        }
        return gtin;
    }

    private void exportExBarc(Connection conn, AstronConnectionString params, List<? extends ItemInfo> itemsList, boolean delFlag, Integer maxBatchSize, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"EXBARCID"};
        String[] columns = getColumns(new String[]{"EXBARCID", "PACKID", "EXBARCTYPE", "EXBARCBODY", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, "EXBARC", columns, keys)) {
            int offset = columns.length + keys.length;

            int batchCount = 0;
            for (ItemInfo item : itemsList) {
                if (notInterrupted()) {
                    if (item.idBarcode != null) {

                        List<Integer> packIds = getPackIds(item);

                        String gtin = getGTIN(item);
                        for (Integer packId : packIds) {
                            if (params.pgsql) {
                                setObject(ps, packId, 1); //EXBARCID
                                setObject(ps, packId, 2); //PACKID
                                setObject(ps, gtin, 3); //EXBARCTYPE
                                setObject(ps, getExBarcBody(item), 4); //EXBARCBODY
                                setObject(ps, delFlag ? 1 : 0, 5); //DELFLAG
                                if(updateNum != null)
                                    setObject(ps, updateNum, 6);
                            } else {
                                setObject(ps, packId, 1, offset); //PACKID
                                setObject(ps, gtin, 2, offset); //EXBARCTYPE
                                setObject(ps, getExBarcBody(item), 3, offset); //EXBARCBODY
                                setObject(ps, delFlag ? "1" : "0", 4, offset); //DELFLAG

                                if(updateNum != null)
                                    setObject(ps, updateNum, 5, offset);

                                setObject(ps, packId, updateNum != null ? 6 : 5, keys.length); //EXBARCID
                            }

                            ps.addBatch();
                            batchCount++;
                            if(maxBatchSize != null && batchCount == maxBatchSize) {
                                executeAndCommitBatch(ps, conn);
                                batchCount = 0;
                            }
                        }
                    }
                } else break;
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private String[] getColumns(String[] columns, Integer updateNum) {
        return updateNum != null ? ArrayUtils.add(columns, "UPDATENUM") : columns;
    }

    private void exportExBarcDeleteBarcode(Connection conn, AstronConnectionString params, List<CashRegisterItem> usedDeleteBarcodeList, Integer maxBatchSize, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"EXBARCID"};
        String[] columns = getColumns(new String[]{"EXBARCID", "PACKID", "EXBARCTYPE", "EXBARCBODY", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, "EXBARC", columns, keys)) {
            int offset = columns.length + keys.length;

            int batchCount = 0;
            for (CashRegisterItem item : usedDeleteBarcodeList) {
                if (notInterrupted()) {
                    if (item.idBarcode != null) {
                        Integer packId = getPackId(item);
                        String gtin = getGTIN(item);
                        if(params.pgsql) {
                            setObject(ps, packId, 1); //EXBARCID
                            setObject(ps, packId, 2); //PACKID
                            setObject(ps, gtin, 3); //EXBARCTYPE
                            setObject(ps, getExBarcBody(item), 4); //EXBARCBODY
                            setObject(ps, 1, 5); //DELFLAG
                            if(updateNum != null)
                                setObject(ps, updateNum, 6);
                        } else {
                            setObject(ps, packId, 1, offset); //PACKID
                            setObject(ps, gtin, 2, offset); //EXBARCTYPE
                            setObject(ps, getExBarcBody(item), 3, offset); //EXBARCBODY
                            setObject(ps, "1", 4, offset); //DELFLAG

                            if(updateNum != null)
                                setObject(ps, updateNum, 5, offset);

                            setObject(ps, packId, updateNum != null ? 6 : 5, keys.length); //EXBARCID
                        }

                        ps.addBatch();
                        batchCount++;
                        if(maxBatchSize != null && batchCount == maxBatchSize) {
                            executeAndCommitBatch(ps, conn);
                            batchCount = 0;
                        }
                    }
                } else break;
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private String getExBarcBody(ItemInfo item) {
        return item.idBarcode.replaceAll("[^0-9]", ""); //Должны быть только цифры, а откуда-то вдруг прилетает символ 0xe2 0x80 0x8e
    }

    private void exportPackPrc(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction, boolean exportExtraTables, Integer maxBatchSize, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"PACKID", "PRCLEVELID"};
        String[] columns = getColumns(new String[]{"PACKID", "PRCLEVELID", "PACKPRICE", "PACKMINPRICE", "PACKBONUSMINPRICE", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, "PACKPRC", columns, keys)) {
            int offset = columns.length + keys.length;

            int batchCount = 0;
            for (int i = 0; i < transaction.itemsList.size(); i++) {
                if (notInterrupted()) {
                    CashRegisterItem item = transaction.itemsList.get(i);
                    ExtInfo extInfo = getExtInfo(item.info);
                    if (item.price != null) {
                        Integer packId = getPackId(item);
                        addPackPrcRow(ps, params, transaction.nppGroupMachinery, item, extInfo, packId, offset, exportExtraTables, item.price, 1, null, false, keys.length, updateNum);
                        batchCount++;
                        if(maxBatchSize != null && batchCount == maxBatchSize) {
                            executeAndCommitBatch(ps, conn);
                            batchCount = 0;
                        }

                        if(exportExtraTables) {
                            //{"extraPrices": [{"id": 2, "name": "VIP", "price": 12.34}, {"id": 3, "name": "OPT", "price": 2.34}]}
                            for(JSONObject infoJSON : extInfo.jsonObjects) {
                                JSONArray extraPrices = infoJSON.optJSONArray("extraPrices");
                                if (extraPrices != null) {
                                    for (int j = 0; j < extraPrices.length(); j++) {
                                        JSONObject extraPrice = extraPrices.getJSONObject(j);
                                        Integer id = extraPrice.getInt("id");
                                        BigDecimal price = BigDecimal.valueOf(extraPrice.getDouble("price"));
                                        addPackPrcRow(ps, params, transaction.nppGroupMachinery, item, extInfo, packId, offset, true, price, id, getBigDecimalValue(extraPrice, "overPackMinPrice"), false, keys.length, updateNum);
                                    }
                                }
                            }
                        }
                        batchCount++;
                        if(maxBatchSize != null && batchCount == maxBatchSize) {
                            executeAndCommitBatch(ps, conn);
                            batchCount = 0;
                        }

                    } else {
                        astronLogger.error(String.format("transaction %s, table packprc, item %s without price", transaction.id, item.idItem));
                    }
                } else break;
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private BigDecimal getBigDecimalValue(JSONObject json, String key) {
        if(json != null) {
            Double value = json.optDouble(key);
            if(!value.isNaN()) {
                return BigDecimal.valueOf(value);
            }
        }
        return null;
    }

    private BigDecimal getBigDecimalValue(ExtInfo extInfo, String key) {
        if(extInfo != null) {
            for(JSONObject infoJSON : extInfo.jsonObjects) {
                if(infoJSON.has(key)) {
                    Double value = infoJSON.optDouble(key);
                    if (!value.isNaN()) {
                        return BigDecimal.valueOf(value);
                    }
                }
            }
        }
        return null;
    }

    private Set<Integer> getHasExtraPrices(TransactionCashRegisterInfo transaction) {
        Set<Integer> hasExtraPrices = new HashSet<>();
        for (CashRegisterItem item : transaction.itemsList) {
            if(item.price != null) {
                for (JSONObject infoJSON : getExtInfo(item.info).jsonObjects) {
                    JSONArray extraPrices = infoJSON.optJSONArray("extraPrices");
                    if (extraPrices != null) {
                        for (int i = 0; i < extraPrices.length(); i++) {
                            JSONObject extraPrice = extraPrices.getJSONObject(i);
                            Integer id = extraPrice.getInt("id");
                            hasExtraPrices.add(id);
                        }
                    }
                }
            }
        }
        return hasExtraPrices;
    }

    private Integer getPropertyGrpId(ItemInfo item) {
        for(JSONObject infoJSON : getExtInfo(item.info).jsonObjects) {
            if (item.price != null && infoJSON != null) {
                if(infoJSON.has("propertyGrpId")) {
                    int propertyGrpId = infoJSON.optInt("propertyGrpId");
                    return propertyGrpId != 0 ? propertyGrpId : null;
                }
            }
        }
        return null;
    }

    private Map<String, List<JSONObject>> getJsonTables(TransactionCashRegisterInfo transaction) {
        Map<String, List<JSONObject>> jsonTables = new HashMap<>();
        List<JSONObject> propertyGrpList = new ArrayList<>();
        List<JSONObject> numPropertyList = new ArrayList<>();
        List<JSONObject> stringsList = new ArrayList<>();
        List<JSONObject> numbersList = new ArrayList<>();
        List<JSONObject> binaryDataList = new ArrayList<>();
        List<JSONObject> binPropertyList = new ArrayList<>();
        List<JSONObject> extGrpList = new ArrayList<>();
        List<JSONObject> artExtGrpList = new ArrayList<>();
        List<JSONObject> artPrnGrpList = new ArrayList<>();
        for (CashRegisterItem item : transaction.itemsList) {
            for (JSONObject infoJSON : getExtInfo(item.info).jsonObjects) {
                propertyGrpList.addAll(getJSONObjectList(infoJSON, "propertyGrp"));
                numPropertyList.addAll(getJSONObjectList(infoJSON, "numProperty"));
                stringsList.addAll(getJSONObjectList(infoJSON, "strings"));
                numbersList.addAll(getJSONObjectList(infoJSON, "numbers"));
                binaryDataList.addAll(getJSONObjectList(infoJSON, "binaryData"));
                binPropertyList.addAll(getJSONObjectList(infoJSON, "binProperty"));
                extGrpList.addAll(getJSONObjectList(infoJSON, "extGrp"));

                //put idItem (see getIdItem)

                List<JSONObject> artExtGrps = getJSONObjectList(infoJSON, "artExtGrp");
                artExtGrps.forEach(artExtGrp -> artExtGrp.put("idItem", parseIdItem(item)));
                artExtGrpList.addAll(artExtGrps);

                List<JSONObject> artPrnGrps = getJSONObjectList(infoJSON, "artPrnGrp");
                artPrnGrps.forEach(artPrnGrp -> artPrnGrp.put("idItem", parseIdItem(item)));
                artPrnGrpList.addAll(artPrnGrps);
            }
        }
        jsonTables.put(PROPERTYGRP, getUniqueList(propertyGrpList));
        jsonTables.put(NUMPROPERTY, getUniqueList(numPropertyList));
        jsonTables.put(STRINGS, getUniqueList(stringsList));
        jsonTables.put(NUMBERS, getUniqueList(numbersList));
        jsonTables.put(BINARYDATA, getUniqueList(binaryDataList));
        jsonTables.put(BINPROPERTY, getUniqueList(binPropertyList));
        jsonTables.put(EXTGRP, getUniqueList(extGrpList));
        jsonTables.put(ARTEXTGRP, getUniqueList(artExtGrpList));
        jsonTables.put(ARTPRNGRP, getUniqueList(artPrnGrpList));
        return jsonTables;
    }

    private List<JSONObject> getJSONObjectList(JSONObject infoJSON, String key) {
        List<JSONObject> jsonObjectList = new ArrayList<>();
        JSONArray jsonArray = infoJSON.optJSONArray(key);
        if (jsonArray != null) {
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                jsonObjectList.add(jsonObject);
            }
        }
        return jsonObjectList;
    }

    private List<JSONObject> getUniqueList(List<JSONObject> jsonObjectList) {
        return jsonObjectList.stream().filter(distinctByKey(JSONObject::toString)).collect(Collectors.toList());
    }

    public <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    private void addPackPrcRow(PreparedStatement ps, AstronConnectionString params, Integer nppGroupMachinery,
                               ItemInfo item, ExtInfo extInfo, Integer packId, int offset, boolean exportExtraTables,
                               BigDecimal price, int priceNumber, BigDecimal overPackMinPrice, boolean delFlag, int keysOffset, Integer updateNum) throws SQLException {

        Integer priceLevelId = getPriceLevelId(nppGroupMachinery, exportExtraTables, priceNumber);
        BigDecimal packPrice = price == null || price.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : HandlerUtils.safeMultiply(price, 100);
        BigDecimal minPrice = item instanceof CashRegisterItem ? HandlerUtils.safeMultiply(((CashRegisterItem) item).minPrice, 100) : null;
        BigDecimal packMinPrice = HandlerUtils.safeMultiply(overPackMinPrice, 100) != null ? HandlerUtils.safeMultiply(overPackMinPrice, 100) :
                (item.flags == null || ((item.flags & 16) == 0)) && HandlerUtils.safeMultiply(price, 100) != null ? HandlerUtils.safeMultiply(price, 100) : minPrice != null ? minPrice : BigDecimal.ZERO;
        BigDecimal packBonusMinPrice = nvl(safeMultiply(getBigDecimalValue(extInfo, "packBonusMinPrice"), 100), packPrice);

        if(params.pgsql) {
            setObject(ps, packId, 1); //PACKID
            setObject(ps, priceLevelId, 2); //PRCLEVELID
            setObject(ps, packPrice, 3); //PACKPRICE
            setObject(ps, packMinPrice, 4); //PACKMINPRICE
            setObject(ps, packBonusMinPrice, 5); //PACKBONUSMINPRICE
            setObject(ps, delFlag ? 1 : 0, 6); //DELFLAG
            if(updateNum != null)
                setObject(ps, updateNum, 7);
        } else {
            setObject(ps, packPrice, 1, offset); //PACKPRICE
            setObject(ps, packMinPrice, 2, offset); //PACKMINPRICE
            setObject(ps, packBonusMinPrice, 3, offset); //PACKBONUSMINPRICE
            setObject(ps, delFlag ? "1" : "0", 4, offset); //DELFLAG

            if(updateNum != null)
                setObject(ps, updateNum, 5, offset);

            setObject(ps, packId, updateNum != null ? 6 : 5, keysOffset); //PACKID
            setObject(ps, priceLevelId, updateNum != null ? 7 : 6, keysOffset); //PRCLEVELID

        }

        ps.addBatch();
    }

    private void exportPackPrcDeleteBarcode(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction,
                                            List<CashRegisterItem> usedDeleteBarcodeList, boolean exportExtraTables, Integer maxBatchSize, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"PACKID", "PRCLEVELID"};
        String[] columns = getColumns(new String[]{"PACKID", "PRCLEVELID", "PACKPRICE", "PACKMINPRICE", "PACKBONUSMINPRICE", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, "PACKPRC", columns, keys)) {
            int offset = columns.length + keys.length;

            int batchCount = 0;
            for (CashRegisterItem item : usedDeleteBarcodeList) {
                if (notInterrupted()) {
                    Integer packId = getPackId(item);
                    if(params.pgsql) {
                        setObject(ps, packId, 1); //PACKID
                        setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, exportExtraTables), 2); //PRCLEVELID
                        setObject(ps, BigDecimal.ZERO, 3); //PACKPRICE
                        setObject(ps, BigDecimal.ZERO, 4); //PACKMINPRICE
                        setObject(ps, 0, 5); //PACKBONUSMINPRICE
                        setObject(ps, 0, 6); //DELFLAG
                    } else {
                        setObject(ps, BigDecimal.ZERO, 1, offset); //PACKPRICE
                        setObject(ps, BigDecimal.ZERO, 2, offset); //PACKMINPRICE
                        setObject(ps, 0, 3, offset); //PACKBONUSMINPRICE
                        setObject(ps, "0", 4, offset); //DELFLAG

                        if(updateNum != null)
                            setObject(ps, updateNum, 5, offset);

                        setObject(ps, packId, updateNum != null ? 6 : 5, keys.length); //PACKID
                        setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, exportExtraTables), updateNum != null ? 7 : 6, keys.length); //PRCLEVELID
                    }

                    ps.addBatch();
                    batchCount++;
                    if(maxBatchSize != null && batchCount == maxBatchSize) {
                        executeAndCommitBatch(ps, conn);
                        batchCount = 0;
                    }
                } else break;
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private void exportPackPrcStopList(Connection conn, AstronConnectionString params, StopListInfo stopListInfo, boolean exportExtraTables, boolean delFlag, Integer maxBatchSize, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"PACKID", "PRCLEVELID"};
        String[] columns = getColumns(new String[]{"PACKID", "PRCLEVELID", "PACKPRICE", "PACKMINPRICE", "PACKBONUSMINPRICE", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, "PACKPRC", columns, keys)) {
            int offset = columns.length + keys.length;

            //берём groupMachinery только своего хэндлера
            Set<Integer> groupMachinerySet = new HashSet<>();
            for(Map.Entry<String, Set<MachineryInfo>> entry : stopListInfo.handlerMachineryMap.entrySet()) {
                String handler = entry.getKey();
                if(handler.endsWith(getClass().getName())) {
                    for(MachineryInfo machinery : entry.getValue()) {
                        if(stopListInfo.inGroupMachineryItemMap.containsKey(machinery.numberGroup)) {
                            groupMachinerySet.add(machinery.numberGroup);
                        }
                    }
                }
            }

            int recordCount = 0;
            int packIdCount = 0;
            int itemCount = 0;
            for (ItemInfo item : stopListInfo.stopListItemMap.values()) {
                itemCount++;
                if (notInterrupted()) {
                    List<Integer> packIds = getPackIds(item);
                    for (Integer packId : packIds) {
                        packIdCount++;
                        for (Integer nppGroupMachinery : groupMachinerySet) {
                            recordCount++;
                            addPackPrcRow(ps, params, nppGroupMachinery, item, null, packId, offset, exportExtraTables, item.price, 1, null, delFlag, keys.length, updateNum);
                            if(maxBatchSize != null && recordCount == maxBatchSize) {
                                astronLogger.info(String.format("exportPackPrcStopList records: %s; items: %s; machineries: %s, packIds: %s",
                                        recordCount, itemCount, groupMachinerySet.size(), packIdCount));
                                executeAndCommitBatch(ps, conn);
                                recordCount = 0;
                                packIdCount = 0;
                                itemCount = 0;
                            }
                        }
                    }
                } else break;
            }
            astronLogger.info(String.format("exportPackPrcStopList records: %s; items: %s; machineries: %s, packIds: %s",
                    recordCount, itemCount, groupMachinerySet.size(), packIdCount));
            executeAndCommitBatch(ps, conn);
        }
    }

    private void exportPrcLevel(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction, Set<Integer> hasExtraPrices, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"PRCLEVELID"};
        String[] columns = getColumns(new String[]{"PRCLEVELID", "PRCLEVELNAME", "PRCLEVELKEY", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, "PRCLEVEL", columns, keys)) {
            int offset = columns.length + keys.length;

            addPrcLevelRow(ps, params, transaction, offset, 1, keys.length, updateNum);

            for(Integer priceNumber : hasExtraPrices) {
                addPrcLevelRow(ps, params, transaction, offset, priceNumber, keys.length, updateNum);
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private void addPrcLevelRow(PreparedStatement ps, AstronConnectionString params, TransactionCashRegisterInfo transaction, int offset, int priceNumber, int keysOffset, Integer updateNum) throws SQLException {
        Integer priceLevelId = getPriceLevelId(transaction.nppGroupMachinery, true, priceNumber);
        String priceLevelPostfix = priceNumber > 1 ? (" №" + priceNumber) : "";
        String priceLevelName = trim(transaction.nameGroupMachinery, 50 - priceLevelPostfix.length()) + priceLevelPostfix;
        if(params.pgsql) {
            setObject(ps, priceLevelId, 1); //PRCLEVELID
            setObject(ps, priceLevelName, 2); //PRCLEVELNAME
            setObject(ps, 0, 3); //PRCLEVELKEY
            setObject(ps, 0, 4); //DELFLAG
            if(updateNum != null)
                setObject(ps, updateNum, 5);
        } else {
            setObject(ps, priceLevelName, 1, offset); //PRCLEVELNAME
            setObject(ps, 0, 2, offset); //PRCLEVELKEY
            setObject(ps, "0", 3, offset); //DELFLAG

            if(updateNum != null)
                setObject(ps, updateNum, 4, offset);

            setObject(ps, priceLevelId, updateNum != null ? 5 : 4, keysOffset); //PRCLEVELID
        }

        ps.addBatch();
    }

    private void exportSArea(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"SAREAID"};
        String[] columns = getColumns(new String[]{"SAREAID", "PRCLEVELID", "CASHPROFILEID", "FIRMID", "CURRENCYID", "SAREANAME", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, "SAREA", columns, keys)) {
            int offset = columns.length + keys.length;
            if(params.pgsql) {
                setObject(ps, transaction.nppGroupMachinery, 1); //SAREAID
                setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, true), 2); //PRCLEVELID
                setObject(ps, 1, 3); //CASHPROFILEID
                setObject(ps, 1, 4); //FIRMID
                setObject(ps, 933, 5); //CURRENCYID
                setObject(ps, trim(transaction.nameGroupMachinery, 50), 6); //SAREANAME
                setObject(ps, 0, 7); //DELFLAG
                if(updateNum != null)
                    setObject(ps, updateNum, 8);
            } else {
                setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, true), 1, offset); //PRCLEVELID
                setObject(ps, 1, 2, offset); //CASHPROFILEID
                setObject(ps, 1, 3, offset); //FIRMID
                setObject(ps, 933, 4, offset); //CURRENCYID
                setObject(ps, trim(transaction.nameGroupMachinery, 50), 5, offset); //SAREANAME
                setObject(ps, "0", 6, offset); //DELFLAG

                if(updateNum != null)
                    setObject(ps, updateNum, 7, offset);

                setObject(ps, transaction.nppGroupMachinery, updateNum != null ? 8 : 7, keys.length); //SAREAID
            }

            ps.addBatch();

            executeAndCommitBatch(ps, conn);
        }
    }

    private void exportSAreaPrc(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction, Set<Integer> hasExtraPrices, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"SAREAID", "PRCLEVELID"};
        String[] columns = getColumns(new String[]{"SAREAID", "PRCLEVELID", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, "SAREAPRC", columns, keys)) {
            int offset = columns.length + keys.length;

            addSAreaPrcRow(ps, params, transaction, offset, 1, keys.length, updateNum);

            for(Integer priceNumber : hasExtraPrices) {
                addSAreaPrcRow(ps, params, transaction, offset, priceNumber, keys.length, updateNum);
            }

            executeAndCommitBatch(ps, conn);
        }
    }

    private void addSAreaPrcRow(PreparedStatement ps, AstronConnectionString params, TransactionCashRegisterInfo transaction, int offset, int priceNumber, int keysOffset, Integer updateNum) throws SQLException {
        Integer priceLevelId = getPriceLevelId(transaction.nppGroupMachinery, true, priceNumber);
        if(params.pgsql) {
            setObject(ps, transaction.nppGroupMachinery, 1); //SAREAID
            setObject(ps, priceLevelId, 2); //PRCLEVELID
            setObject(ps, 0, 3); //DELFLAG
            if(updateNum != null)
                setObject(ps, updateNum, 4);
        } else {
            setObject(ps, "0", 1, offset); //DELFLAG

            if(updateNum != null)
                setObject(ps, updateNum, 2, offset);

            setObject(ps, transaction.nppGroupMachinery, updateNum != null ? 3 : 2, keysOffset); //SAREAID
            setObject(ps, priceLevelId, updateNum != null ? 4 : 3, keysOffset); //PRCLEVELID
        }

        ps.addBatch();
    }

    private void exportDCard(Connection conn, AstronConnectionString params, List<DiscountCard> discountCardList, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"DCARDID"};
        String[] columns = getColumns(new String[]{"DCARDID", "CLNTID", "DCARDCODE", "DCARDNAME", "ISPAYMENT", "DELFLAG", "LOCKED"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, "DCARD", columns, keys)) {
            int offset = columns.length + keys.length;

            for (DiscountCard discountCard : discountCardList) {
                if (notInterrupted()) {
                    Integer clientId = getClientId(discountCard);
                    boolean isPayment = isSocial(discountCard);
                    int locked = getLocked(discountCard.extInfo);

                    if(params.pgsql) {
                        setObject(ps, discountCard.numberDiscountCard, 1); //DCARDID
                        setObject(ps, clientId, 2); //CLNTID
                        setObject(ps, discountCard.numberDiscountCard, 3); //DCARDCODE
                        setObject(ps, discountCard.numberDiscountCard, 4); //DCARDNAME
                        setObject(ps, isPayment ? 1 : 0, 5); //ISPAYMENT
                        setObject(ps, 0, 6); //DELFLAG
                        setObject(ps, locked, 7); //LOCKED
                        if(updateNum != null)
                            setObject(ps, updateNum, 8);
                    } else {
                        setObject(ps, discountCard.numberDiscountCard, 1, offset); //CLNTID
                        setObject(ps, discountCard.numberDiscountCard, 2, offset); //DCARDCODE
                        setObject(ps, discountCard.numberDiscountCard, 3, offset); //DCARDNAME
                        setObject(ps, isPayment, 4, offset); //ISPAYMENT
                        setObject(ps, "0", 5, offset); //DELFLAG
                        setObject(ps, locked, 6, offset); //LOCKED

                        if(updateNum != null)
                            setObject(ps, updateNum, 7, offset);

                        setObject(ps, clientId, updateNum != null ? 8 : 7, keys.length); //DCARDID
                    }

                    ps.addBatch();
                } else break;
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private void exportClnt(Connection conn, AstronConnectionString params, String tbl, List<DiscountCard> discountCardList, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"CLNTID"};
        String[] columns = getColumns(new String[]{"CLNTID", "CLNTGRPID", "COMPANYID", "PROPERTYGRPID", "CLNTNAME", "CLNTBIRTHDAY", "LOCKED", "DELFLAG", "PRIMARYEMAIL", "PRIMARYPHONE"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, tbl, columns, keys)) {
            int offset = columns.length + keys.length;

            for (DiscountCard d : discountCardList) {
                if (notInterrupted()) {
                    Integer clientId = getClientId(d);
                    Integer clientGroupId = isSocial(d) ? 7 : 1; //так захардкожено у БКС, обычные клиенты - 1, социальные - 7
                    String clientName = nvl(trim(d.nameDiscountCard, 50), "");
                    String clientBirthday = d.birthdayContact != null ? d.birthdayContact.format(dateFormatter) + "000000" : null;
                    int delflag = getLocked(d.extInfo);

                    if(params.pgsql) {
                        setObject(ps, clientId, 1); //CLNTID
                        setObject(ps, clientGroupId, 2); //CLNTGRPID
                        setObject(ps, null, 3); //COMPANYID
                        setObject(ps, null, 4); //PROPERTYGRPID
                        setObject(ps, clientName, 5); //CLNTNAME
                        setObject(ps, clientBirthday, 6); //CLNTBIRTHDAY
                        setObject(ps, 0, 7); //LOCKED
                        setObject(ps, delflag, 8); //DELFLAG
                        setObject(ps, null, 9); //PRIMARYEMAIL
                        setObject(ps, null, 10); //PRIMARYPHONE
                        if(updateNum != null)
                            setObject(ps, updateNum, 11);
                    } else {
                        setObject(ps, clientGroupId, 1, offset); //CLNTGRPID
                        setObject(ps, null, 2, offset); //COMPANYID
                        setObject(ps, null, 3, offset); //PROPERTYGRPID
                        setObject(ps, clientName, 4, offset); //CLNTNAME
                        setObject(ps, clientBirthday, 5, offset); //CLNTBIRTHDAY
                        setObject(ps, 0, 6, offset); //LOCKED
                        setObject(ps, delflag, 7, offset); //DELFLAG
                        setObject(ps, null, 8, offset); //PRIMARYEMAIL
                        setObject(ps, null, 9, offset); //PRIMARYPHONE

                        if(updateNum != null)
                            setObject(ps, updateNum, 10, offset);

                        setObject(ps, clientId, updateNum != null ? 11 : 10, keys.length); //CLNTID
                    }

                    ps.addBatch();
                } else break;
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private boolean isSocial(DiscountCard d) {
        for(JSONObject infoJSON : getExtInfo(d.extInfo).jsonObjects) {
            JSONArray clientAnswers = infoJSON.optJSONArray("clientAnswers");
            if (clientAnswers != null && clientAnswers.length() >= 4) {
                return clientAnswers.getString(3).equals("Да");
            }
        }
        return false;
    }
    private void exportClntForm(Connection conn, AstronConnectionString params, String tbl, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"CLNTFORMID"};
        String[] columns = getColumns(new String[]{"CLNTFORMID", "CLNTFORMNAME", "ORDERNUM", "USESAREA", "ACTIVEFROM", "ACTIVETO", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, tbl, columns, keys)) {
            int offset = columns.length + keys.length;

            Integer clientFormId = 1;
            String clientFormName = "Социальная";
            if(params.pgsql) {
                setObject(ps, clientFormId, 1); //CLNTFORMID
                setObject(ps, clientFormName, 2); //CLNTFORMNAME
                setObject(ps, 0, 3); //ORDERNUM
                setObject(ps, 0, 4); //USESAREA
                setObject(ps, null, 5); //ACTIVEFROM
                setObject(ps, null, 6); //ACTIVETO
                setObject(ps, 0, 7); //DELFLAG
                if(updateNum != null)
                    setObject(ps, updateNum, 8);
            } else {
                setObject(ps, clientFormName, 1, offset); //CLNTFORMNAME
                setObject(ps, 0, 2, offset); //ORDERNUM
                setObject(ps, 0, 3, offset); //USESAREA
                setObject(ps, null, 4, offset); //ACTIVEFROM
                setObject(ps, null, 5, offset); //ACTIVETO
                setObject(ps, 0, 6, offset); //DELFLAG

                if(updateNum != null)
                    setObject(ps, updateNum, 7, offset);

                setObject(ps, clientFormId, updateNum != null ? 8 : 7, keys.length); //CLNTFORMID
            }

            ps.addBatch();

            executeAndCommitBatch(ps, conn);
        }
    }

    private void exportClntFormItems(Connection conn, AstronConnectionString params, String tbl, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"CLNTFORMID", "CLNTFORMITEMID"};
        String[] columns = getColumns(new String[]{"CLNTFORMID", "CLNTFORMITEMID", "CLNTFORMITEM", "ORDERNUM", "ISREQUIRED", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, tbl, columns, keys)) {
            int offset = columns.length + keys.length;

            Integer clientFormId = 1;
            int questionId = 0;
            for (String question : new String[] {"Тип удостоверения", "Номер удостоверения", "Срок действия удостоверения", "Признак социальная карта"}) {
                if (notInterrupted()) {
                    questionId++;
                    if(params.pgsql) {
                        setObject(ps, clientFormId, 1); //CLNTFORMID
                        setObject(ps, questionId, 2); //CLNTFORMITEMID
                        setObject(ps, question, 3); //CLNTFORMITEM
                        setObject(ps, 0, 4); //ORDERNUM
                        setObject(ps, 0, 5); //ISREQUIRED
                        setObject(ps, 0, 6); //DELFLAG
                        if(updateNum != null)
                            setObject(ps, updateNum, 7);
                    } else {
                        setObject(ps, question, 1, offset); //CLNTFORMITEM
                        setObject(ps, 0, 2, offset); //ORDERNUM
                        setObject(ps, 0, 3, offset); //ISREQUIRED
                        setObject(ps, 0, 4, offset); //DELFLAG

                        if(updateNum != null)
                            setObject(ps, updateNum, 5, offset);

                        setObject(ps, clientFormId, updateNum != null ? 6 : 5, keys.length); //CLNTFORMID
                        setObject(ps, questionId, updateNum != null ? 7 : 6, keys.length); //CLNTFORMITEMID
                    }

                    ps.addBatch();
                } else break;
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private void exportClntFormProperty(Connection conn, AstronConnectionString params, String tbl, List<DiscountCard> discountCardList, Integer updateNum) throws SQLException {
        String[] keys = new String[]{"CLNTID", "CLNTFORMID", "CLNTFORMITEMID"};
        String[] columns = getColumns(new String[]{"CLNTID", "CLNTFORMID", "CLNTFORMITEMID", "CLNTPROPERTYVAL", "DELFLAG"}, updateNum);
        try (PreparedStatement ps = getPreparedStatement(conn, params, tbl, columns, keys)) {
            int offset = columns.length + keys.length;

            for (DiscountCard d : discountCardList) {
                if (notInterrupted()) {
                    for (JSONObject infoJSON : getExtInfo(d.extInfo).jsonObjects) {
                        JSONArray clientAnswers = infoJSON.optJSONArray("clientAnswers");
                        if (clientAnswers != null) {
                            for (int i = 0; i < clientAnswers.length(); i++) {
                                String clientAnswer = clientAnswers.getString(i);
                                Integer clientId = getClientId(d);
                                Integer clientFormId = 1;
                                Integer clientFormItemId = i + 1;
                                if (params.pgsql) {
                                    setObject(ps, clientId, 1); //CLNTID
                                    setObject(ps, clientFormId, 2); //CLNTFORMID
                                    setObject(ps, clientFormItemId, 3); //CLNTFORMITEMID
                                    setObject(ps, clientAnswer, 4); //CLNTPROPERTYVAL
                                    setObject(ps, 0, 5); //DELFLAG
                                    if (updateNum != null)
                                        setObject(ps, updateNum, 6);
                                } else {
                                    setObject(ps, clientAnswer, 1, offset); //CLNTPROPERTYVAL
                                    setObject(ps, 0, 2, offset); //DELFLAG

                                    if (updateNum != null)
                                        setObject(ps, updateNum, 3, offset);

                                    setObject(ps, clientId, updateNum != null ? 4 : 3, keys.length); //CLNTID
                                    setObject(ps, clientFormId, updateNum != null ? 5 : 4, keys.length); //CLNTFORMID
                                    setObject(ps, clientFormItemId, updateNum != null ? 6 : 5, keys.length); //CLNTFORMITEMID
                                }
                                ps.addBatch();
                            }
                        }
                    }
                } else break;
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private Integer getClientId(DiscountCard discountCard) {
        return Integer.parseInt(discountCard.idDiscountCard);
    }

    private boolean isValidItem(TransactionCashRegisterInfo transaction, Map<String, CashRegisterItem> deleteBarcodeMap, List<CashRegisterItem> usedDeleteBarcodeList, CashRegisterItem item) {
        boolean isValidItem = parseUOM(item, null) != null && parseIdItem(item) != null;
        if(isValidItem) {
            if(deleteBarcodeMap != null && deleteBarcodeMap.containsKey(item.idItem)) {
                CashRegisterItem deleteBarcode = deleteBarcodeMap.get(item.idItem);
                usedDeleteBarcodeList.add(deleteBarcode);
                astronLogger.info(String.format("Transaction %s, deleteBarcode item %s, barcode %s", transaction.id, deleteBarcode.idItem, deleteBarcode.idBarcode));
            }
        } else {
            String errorMessage = String.format("transaction %s, invalid item: barcode %s, id %s, uom %s", transaction.id, item.idBarcode, item.idItem, item.idUOM);
            astronLogger.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }
        return isValidItem;
    }

    private Map<String, Integer> readUpdateNums(Connection conn, String tables) {
        Map<String, Integer> recordNums = new HashMap<>();
        try (Statement statement = conn.createStatement()) {
            String query = "SELECT dirname, recordnum FROM DATAPUMP WHERE dirname IN (" + tables + ")";
            ResultSet result = statement.executeQuery(query);
            while (result.next()) {
                recordNums.put(result.getString("dirname"), result.getInt("recordnum"));

            }
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
        return recordNums;
    }

    private Map<String, Integer> readProcessedUpdateNums(Connection conn, String tables, AstronConnectionString params) {
        Map<String, Integer> recordNums = new HashMap<>();
        if(!params.pgsql) {  //можно в теории через dblink сделать и для psql
            try (Statement statement = conn.createStatement()) {
                String query = "SELECT dirname, pumpupdatenum FROM DataServer.dbo.DATAPUMPDIRS where SOURCETYPE=1 AND dirname IN (" + tables + ")";
                ResultSet result = statement.executeQuery(query);
                while (result.next()) {
                    recordNums.put(result.getString("dirname"), result.getInt("pumpupdatenum"));

                }
            } catch (SQLException e) {
                throw Throwables.propagate(e);
            }
        }
        return recordNums;
    }

    private void exportUpdateNums(Connection conn, Map<String, Integer> updateNums) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE DATAPUMP SET recordnum = ? WHERE dirname = ?")) {
            for (Map.Entry<String, Integer> entry : updateNums.entrySet()) {
                setObject(ps, entry.getValue(), 1); //recordnum
                setObject(ps, entry.getKey(), 2); //dirname
                ps.addBatch();
                astronLogger.info(String.format("UPDATE DATAPUMP SET recordnum = %s WHERE dirname = %s", entry.getValue(), entry.getKey()));
            }
            executeAndCommitBatch(ps, conn);
        }
    }

    private void exportFlags(Connection conn, String tables, int value) throws SQLException {
        astronLogger.info(String.format("UPDATE DATAPUMP SET recordnum = %s WHERE dirname in (%s)", value, tables));
        conn.setAutoCommit(true);
        try (Statement statement = conn.createStatement()) {
            String sql = "UPDATE DATAPUMP SET recordnum = " + value + " WHERE dirname in (" + tables + ")";
            statement.executeUpdate(sql);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private Exception waitFlags(Connection conn, AstronConnectionString params, String tables, int timeout) throws InterruptedException, SQLException {
        return waitFlags(conn, params, tables, timeout, "0", false);
    }

    private Exception waitFlags(Connection conn, AstronConnectionString params, String tables, int timeout, String eventTime, boolean waitSysLogInsteadOfDataPump) throws InterruptedException, SQLException {
        int count = 0;
        if (waitSysLogInsteadOfDataPump) {
            Pair<Boolean, Exception> sysLog;
            while (!(sysLog = checkSysLog(conn, params, eventTime)).first) {
                if (count > (timeout / 5)) {
                    exportFlags(conn, tables, 0);
                    return createException(String.format("Data was sent to db but %s no records in syslog found", sysLog));
                } else {
                    count++;
                    astronLogger.info("Waiting for syslog");
                    Thread.sleep(5000);
                }
            }
            return sysLog.second;
        } else {
            int flags;
            while ((flags = checkFlags(conn, params, tables)) != 0) {
                if (count > (timeout / 5)) {
                    return createException(String.format("Data was sent to db but %s flag records were not set to zero", flags));
                } else {
                    count++;
                    astronLogger.info(String.format("Waiting for setting to zero %s flag records", flags));
                    Thread.sleep(5000);
                }
            }
            return null;
        }
    }

    private String getEventTime(Connection conn, boolean waitSysLogInsteadOfDataPump) {
        if (!waitSysLogInsteadOfDataPump) {
            return null;
        }
        try (Statement statement = conn.createStatement()) {
            ResultSet rs = statement.executeQuery("SELECT MAX(EVENTTIME) AS EVENTTIME FROM \"Syslog_DataServer\"");
            if (rs.next()) {
                return rs.getString("EVENTTIME");
            }
            return "0";
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private Pair<Boolean, Exception> checkSysLog(Connection conn, AstronConnectionString params, String eventTime) {
        try (Statement statement = conn.createStatement()) {
            String sql = "SELECT EVENTCODE, EVENTDATA FROM \"Syslog_DataServer\" WHERE EVENTTIME > '" + eventTime + "' ORDER BY SEQ" + (params.pgsql ? "" : " DESC");
            ResultSet rs = statement.executeQuery(sql);

            List<String> errors = new ArrayList<>();
            boolean succeeded = false;
            while (rs.next()) {
                int eventCode = rs.getInt("EVENTCODE");
                String eventData = rs.getString("EVENTDATA");
                switch (eventCode) {
                    case 700:
                    case 701:
                        //do nothing
                        break;
                    case 702:
                        succeeded = true;
                        break;
                    default:
                        errors.add(eventData);

                }
            }
            boolean finished = succeeded || !errors.isEmpty();
            return Pair.create(finished, succeeded ? null : new RuntimeException(errors.isEmpty() ? "neither 702 nor error records in sysLog found" : String.join("\n", errors)));

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private int checkFlags(Connection conn, AstronConnectionString params, String tables) {
        try (Statement statement = conn.createStatement()) {
            String sql = params.pgsql ?
                    "SELECT COUNT(*) FROM DATAPUMP WHERE dirname in (" + tables + ") AND recordnum = 1" :
                    "SELECT COUNT(*) FROM [DATAPUMP] WHERE dirname in (" + tables + ") AND recordnum = 1";
            ResultSet resultSet = statement.executeQuery(sql);
            return resultSet.next() ? resultSet.getInt(1) : 0;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private Exception waitConnectionSemaphore(AstronConnectionString params, int timeout) {
        try {
            int count = 0;
            while (connectionSemaphore.contains(params.connectionString)) {
                if (count > (timeout / 5)) {
                    String message;
                    message = "Timeout exception. Another thread uses " + params.connectionString;
                    astronLogger.error(message);
                    return new Exception(message);
                } else {
                    count++;
                    astronLogger.info("Waiting connection semaphore");
                    Thread.sleep(5000);
                }
            }
        } catch (InterruptedException e) {
            astronLogger.error(e.getMessage());
            return e;
        }
        return null;
    }

    private void truncateTables(Connection conn, AstronConnectionString params, String transactionId, Set<String> tables) throws SQLException {
        astronLogger.info(String.format("transaction %s, truncate tables", transactionId));
        for (String table : tables) {
            try (Statement s = conn.createStatement()) {
                if (params.pgsql) {
                    s.execute("TRUNCATE TABLE " + table + " CASCADE");
                } else {
                    s.execute("DELETE FROM " + table);
                }
            }
        }
        conn.commit();
    }

    private void setObject(PreparedStatement ps, Object value, int index, int columnsSize) throws SQLException {
        setObject(ps, value, index);
        setObject(ps, value, index + columnsSize);
    }

    private void setObject(PreparedStatement ps, Object value, int index) throws SQLException {
        if (value instanceof Date) ps.setDate(index, (Date) value);
        else if (value instanceof Timestamp) ps.setTimestamp(index, ((Timestamp) value));
        else if (value instanceof String) ps.setString(index, ((String) value).trim());
        else ps.setObject(index, value);
    }

    private PreparedStatement getPreparedStatement(Connection conn, AstronConnectionString connectionParams, String table, String[] columnNames, String[] keyNames) throws SQLException {
        String set = "";
        String columns = "";
        String params = "";
        List<String> keyList = Arrays.asList(keyNames);
        for (String columnName : columnNames) {
            columns = concat(columns, columnName, ",");
            if(!connectionParams.newScheme() || !keyList.contains(columnName)) {
                set = concat(set, columnName + "=?", ",");
            }
            params = concat(params, "?", ",");
        }
        if(connectionParams.pgsql) {
            List<String> conflicts = new ArrayList<>();
            for(String columnName : columnNames) {
                conflicts.add(columnName + " = EXCLUDED." + columnName);
            }
            return conn.prepareStatement(String.format("INSERT INTO %s(%s) VALUES (%s) ON CONFLICT (%s) DO UPDATE SET %s",
                    table, columns, params, StringUtils.join(keyNames, ","),  StringUtils.join(conflicts, ",")));
        } else {
            String wheres = "";
            for (String keyName : keyNames) {
                wheres = concat(wheres, keyName + "=?", " AND ");
            }
            return conn.prepareStatement(String.format("UPDATE [%s] SET %s WHERE %s IF @@ROWCOUNT=0 INSERT INTO %s(%s) VALUES (%s)", table, set, wheres, table, columns, params));
        }
    }

    @Override
    public Pair<String, Set<String>> sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machinerySet) {
        AstronSettings astronSettings = getSettings();
        Integer timeout = nvl(astronSettings.getTimeout(), 300);
        boolean exportExtraTables = astronSettings.isExportExtraTables();
        Integer maxBatchSize =astronSettings.getMaxBatchSize();
        boolean versionalScheme = astronSettings.isVersionalScheme();
        boolean usePropertyGridFieldInPackTable = astronSettings.isUsePropertyGridFieldInPackTable();
        boolean waitSysLogInsteadOfDataPump = astronSettings.isWaitSysLogInsteadOfDataPump();
        boolean specialSplitMode = astronSettings.isSpecialSplitMode();
        boolean swap10And20VAT = astronSettings.isSwap10And20VAT();

        Set<String> usedDirectories = new HashSet<>();
        for (MachineryInfo machinery : machinerySet) {
            String directory = machinery.directory;
            if(usedDirectories.add(directory)) {
                AstronConnectionString params = new AstronConnectionString(directory);
                if (params.connectionString != null && !stopListInfo.stopListItemMap.isEmpty()) {
                    Exception exception = waitConnectionSemaphore(params, timeout);
                    if ((exception != null)) {
                        throw new RuntimeException("semaphore stopList timeout", exception);
                    } else {
                        String logInfo = "stopList " + stopListInfo.number;
                        try (Connection conn = getConnection(params)) {
                            connectionSemaphoreAdd(params, directory, logInfo);

                            String tables = "'ART', 'UNIT', 'PACK', 'EXBARC', 'PACKPRC'";

                            Map<String, Integer> processedUpdateNums = versionalScheme ? readProcessedUpdateNums(conn, tables, params) : new HashMap<>();
                            Map<String, Integer> inputUpdateNums = versionalScheme ? readUpdateNums(conn, tables) : new HashMap<>();
                            Map<String, Integer> outputUpdateNums = new HashMap<>();

                            List<StopListItem> itemsList = new ArrayList<>(stopListInfo.stopListItemMap.values());

                            String eventTime = getEventTime(conn, waitSysLogInsteadOfDataPump);

                            Integer artUpdateNum = getStopListUpdateNum(stopListInfo, versionalScheme, processedUpdateNums, inputUpdateNums, "ART");
                            exportArt(conn, params, itemsList, swap10And20VAT, maxBatchSize, artUpdateNum);
                            outputUpdateNums.put("ART", artUpdateNum);

                            Integer unitUpdateNum = getStopListUpdateNum(stopListInfo, versionalScheme, processedUpdateNums, inputUpdateNums, "UNIT");
                            exportUnit(conn, params, itemsList, machinery, maxBatchSize, unitUpdateNum);
                            outputUpdateNums.put("UNIT", unitUpdateNum);

                            Integer packUpdateNum = getStopListUpdateNum(stopListInfo, versionalScheme, processedUpdateNums, inputUpdateNums, "PACK");
                            exportPack(conn, params, itemsList, machinery, false, maxBatchSize, packUpdateNum, usePropertyGridFieldInPackTable, specialSplitMode);
                            outputUpdateNums.put("PACK", packUpdateNum);

                            Integer exBarcUpdateNum = getStopListUpdateNum(stopListInfo, versionalScheme, processedUpdateNums, inputUpdateNums, "EXBARC");
                            exportExBarc(conn, params, itemsList, false, maxBatchSize, exBarcUpdateNum);
                            outputUpdateNums.put("EXBARC", exBarcUpdateNum);

                            Integer packPrcUpdateNum = getStopListUpdateNum(stopListInfo, versionalScheme, processedUpdateNums, inputUpdateNums, "PACKPRC");
                            exportPackPrcStopList(conn, params, stopListInfo, exportExtraTables, !stopListInfo.exclude, maxBatchSize, packPrcUpdateNum);
                            outputUpdateNums.put("PACKPRC", packPrcUpdateNum);

                            if(versionalScheme) {
                                astronLogger.info(String.format("stoplist %s, table datapump", stopListInfo.number));
                                exportUpdateNums(conn, outputUpdateNums);
                            } else {
                                astronLogger.info("waiting for processing stopLists");
                                exportFlags(conn, tables, 1);

                                Exception e = waitFlags(conn, params, tables, timeout, eventTime, waitSysLogInsteadOfDataPump);
                                if (e != null) {
                                    throw e;
                                }
                            }

                        } catch (Exception e) {
                            astronLogger.error("sendStopListInfo error", e);
                            throw Throwables.propagate(e);
                        } finally {
                            connectionSemaphoreRemove(params, directory, logInfo);
                        }
                    }
                }
            }
        }
        return null;
    }

    private Integer getStopListUpdateNum(StopListInfo stopList, boolean versionalScheme, Map<String, Integer> processedUpdateNums, Map<String, Integer> inputUpdateNums, String tbl) {
        Integer updateNum = versionalScheme ? (inputUpdateNums.getOrDefault(tbl, 0) + 1) : null;
        astronLogger.info(String.format("stoplist %s, table %s", stopList.number, tbl) +
                (versionalScheme ? String.format(" (updateNum processed %s, new %s)", processedUpdateNums.get(tbl), updateNum) : ""));
        return updateNum;
    }

    @Override
    public boolean sendDeleteBarcodeInfo(DeleteBarcodeInfo deleteBarcode) {
        AstronSettings astronSettings = getSettings();
        Integer timeout = nvl(astronSettings.getTimeout(), 300);
        Integer maxBatchSize = astronSettings.getMaxBatchSize();
        boolean versionalScheme = astronSettings.isVersionalScheme();
        boolean deleteBarcodeInSeparateProcess = astronSettings.isDeleteBarcodeInSeparateProcess();
        boolean usePropertyGridFieldInPackTable = astronSettings.isUsePropertyGridFieldInPackTable();
        boolean waitSysLogInsteadOfDataPump = astronSettings.isWaitSysLogInsteadOfDataPump();
        boolean specialSplitMode = astronSettings.isSpecialSplitMode();
        boolean swap10And20VAT = astronSettings.isSwap10And20VAT();

        if(deleteBarcodeInSeparateProcess) {

            AstronConnectionString params = new AstronConnectionString(deleteBarcode.directoryGroupMachinery);
            if (params.connectionString == null) {
                astronLogger.error("no connectionString found");
            } else {
                Exception exception = waitConnectionSemaphore(params, timeout);
                if (exception == null) {
                    String logInfo = "deleteBarcode";
                    try (Connection conn = getConnection(params)) {
                        connectionSemaphoreAdd(params, deleteBarcode.directoryGroupMachinery, logInfo);

                        String tables = "'ART', 'UNIT', 'PACK', 'EXBARC'";

                        Map<String, Integer> inputUpdateNums = versionalScheme ? readUpdateNums(conn, tables) : new HashMap<>();
                        Map<String, Integer> outputUpdateNums = new HashMap<>();

                        if (!versionalScheme) {
                            Exception waitFlagsResult = waitFlags(conn, params, tables, timeout);
                            if (waitFlagsResult != null) {
                                throw new RuntimeException("data from previous transactions was not processed (flags not set to zero)");
                            }
                            truncateTables(conn, params, "DeleteBarcode", new HashSet<>(Arrays.asList("ART", "UNIT", "PACK", "EXBARC")));
                        }

                        deleteBarcode.barcodeList = deleteBarcode.barcodeList.stream().filter(item -> parseUOM(item, null) != null && parseIdItem(item) != null).collect(Collectors.toList());

                        if (!deleteBarcode.barcodeList.isEmpty()) {

                            checkItems(params, deleteBarcode.barcodeList, null);

                            String eventTime = getEventTime(conn, waitSysLogInsteadOfDataPump);

                            Integer artUpdateNum = getTransactionUpdateNum(versionalScheme, inputUpdateNums, "ART");
                            exportArt(conn, params, deleteBarcode.barcodeList, swap10And20VAT, maxBatchSize, artUpdateNum);
                            outputUpdateNums.put("ART", artUpdateNum);

                            Integer unitUpdateNum = getTransactionUpdateNum(versionalScheme, inputUpdateNums, "UNIT");
                            exportUnit(conn, params, deleteBarcode.barcodeList, null, maxBatchSize, unitUpdateNum);
                            outputUpdateNums.put("UNIT", unitUpdateNum);

                            Integer packUpdateNum = getTransactionUpdateNum(versionalScheme, inputUpdateNums, "PACK");
                            exportPack(conn, params, deleteBarcode.barcodeList, null, true, maxBatchSize, packUpdateNum, usePropertyGridFieldInPackTable, specialSplitMode);
                            outputUpdateNums.put("PACK", packUpdateNum);

                            Integer exBarcUpdateNum = getTransactionUpdateNum(versionalScheme, inputUpdateNums, "EXBARC");
                            exportExBarc(conn, params, deleteBarcode.barcodeList, true, maxBatchSize, exBarcUpdateNum);
                            outputUpdateNums.put("EXBARC", exBarcUpdateNum);

                            if (versionalScheme) {
                                astronLogger.info("deleteBarcode, table DATAPUMP");
                                exportUpdateNums(conn, outputUpdateNums);
                            } else {
                                astronLogger.info(String.format("waiting for processing %s deleteBarcode(s)", deleteBarcode.barcodeList.size()));

                                exportFlags(conn, tables, 1);
                                Exception e = waitFlags(conn, params, tables, timeout, eventTime, waitSysLogInsteadOfDataPump);
                                if (e != null) {
                                    throw e;
                                }
                            }
                            return true;
                        }

                    } catch (Exception e) {
                        astronLogger.error("deleteBarcode error", e);
                    } finally {
                        connectionSemaphoreRemove(params, deleteBarcode.directoryGroupMachinery,  logInfo);
                    }
                } else {
                    astronLogger.error("deleteBarcode semaphore timeout", exception);
                }
            }

        }
        return false;
    }

    @Override
    public void sendDiscountCardList(List<DiscountCard> discountCardList, RequestExchange requestExchange) {
        AstronSettings astronSettings = getSettings();
        Integer timeout = nvl(astronSettings.getTimeout(), 300);
        boolean versionalScheme = astronSettings.isVersionalScheme();
        boolean exportDiscountCardExtraTables = astronSettings.isExportDiscountCardExtraTables();
        boolean waitSysLogInsteadOfDataPump = astronSettings.isWaitSysLogInsteadOfDataPump();

        for (String directory : getDirectorySet(requestExchange)) {

            Throwable exception;

            AstronConnectionString params = new AstronConnectionString(directory);
            if (params.connectionString == null) {
                exception = createException("no connectionString found");
            } else {
                exception = waitConnectionSemaphore(params, timeout);
                if ((exception == null)) {
                    String logInfo = "discount cards";
                    try (Connection conn = getConnection(params)) {
                        connectionSemaphoreAdd(params, directory, logInfo);

                        String tables = exportDiscountCardExtraTables ? "'DCARD', 'CLNT', 'CLNTFORM', 'CLNTFORMITEMS', 'CLNTFORMPROPERTY'" : "'DCARD'";

                        Map<String, Integer> processedUpdateNums = versionalScheme ? readProcessedUpdateNums(conn, tables, params) : new HashMap<>();
                        Map<String, Integer> inputUpdateNums = versionalScheme ? readUpdateNums(conn, tables) : new HashMap<>();
                        Map<String, Integer> outputUpdateNums = new HashMap<>();

                        int flags = checkFlags(conn, params, tables);
                        if (flags > 0) {
                            exception = new RuntimeException(String.format("data from previous transactions was not processed (%s flags not set to zero)", flags));
                        } else {

                            String eventTime = getEventTime(conn, waitSysLogInsteadOfDataPump);

                            if (!versionalScheme) {
                                truncateTables(conn, params, "DiscountCard", exportDiscountCardExtraTables ? new HashSet<>(Arrays.asList("DCARD", "CLNT", "CLNTFORM", "CLNTFORMITEMS", "CLNTFORMPROPERTY")) : new HashSet<>(Collections.singletonList("DCARD")));
                            }

                            if (exportDiscountCardExtraTables) {

                                String clntTbl = "CLNT";
                                Integer clntUpdateNum = getDiscountCardUpdateNum(versionalScheme, processedUpdateNums, inputUpdateNums, clntTbl);
                                exportClnt(conn, params, clntTbl, discountCardList, clntUpdateNum);
                                outputUpdateNums.put(clntTbl, clntUpdateNum);

                                String clntFormTbl = "CLNTFORM";
                                Integer clntFormUpdateNum = getDiscountCardUpdateNum(versionalScheme, processedUpdateNums, inputUpdateNums, clntFormTbl);
                                exportClntForm(conn, params, clntFormTbl, clntUpdateNum);
                                outputUpdateNums.put(clntFormTbl, clntFormUpdateNum);

                                String clntFormItemsTbl = "CLNTFORMITEMS";
                                Integer clntFormItemsUpdateNum = getDiscountCardUpdateNum(versionalScheme, processedUpdateNums, inputUpdateNums, clntFormItemsTbl);
                                exportClntFormItems(conn, params, clntFormItemsTbl, clntFormItemsUpdateNum);
                                outputUpdateNums.put(clntFormItemsTbl, clntFormItemsUpdateNum);

                                String clntFormPropertyTbl = "CLNTFORMPROPERTY";
                                Integer clntFormPropertyUpdateNum = getDiscountCardUpdateNum(versionalScheme, processedUpdateNums, inputUpdateNums, clntFormPropertyTbl);
                                exportClntFormProperty(conn, params, clntFormPropertyTbl, discountCardList, clntUpdateNum);
                                outputUpdateNums.put(clntFormPropertyTbl, clntFormPropertyUpdateNum);
                            }

                            Integer dcardUpdateNum = getDiscountCardUpdateNum(versionalScheme, processedUpdateNums, inputUpdateNums, "DCARD");
                            exportDCard(conn, params, discountCardList, dcardUpdateNum);
                            outputUpdateNums.put("DCARD", dcardUpdateNum);

                            if (versionalScheme) {
                                exportUpdateNums(conn, outputUpdateNums);
                            } else {
                                astronLogger.info("waiting for processing discount cards");
                                exportFlags(conn, tables, 1);
                                exception = waitFlags(conn, params, tables, timeout, eventTime, waitSysLogInsteadOfDataPump);
                            }
                        }
                    } catch (Exception e) {
                        astronLogger.error("sendDiscountCardList error", e);
                        exception = e;
                    } finally {
                        connectionSemaphoreRemove(params, directory, logInfo);
                    }
                }
            }

            if (exception != null) {
                throw new RuntimeException(exception);
            }
        }
    }

    private Integer getDiscountCardUpdateNum(boolean versionalScheme, Map<String, Integer> processedUpdateNums, Map<String, Integer> inputUpdateNums, String tbl) {
        Integer updateNum = versionalScheme ? (inputUpdateNums.getOrDefault(tbl, 0) + 1) : null;
        astronLogger.info(String.format("table %s", tbl) +
                (versionalScheme ? String.format(" (updateNum processed %s, new %s)", processedUpdateNums.get(tbl), updateNum) : ""));
        return updateNum;
    }

    @Override
    public AstronSalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) {

        AstronSalesBatch salesBatch = null;

        Map<Integer, CashRegisterInfo> machineryMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (fitHandler(c)) {
                if (c.number != null && c.numberGroup != null) {
                    machineryMap.put(c.number, c);
                }
            }
        }

        try {
            AstronConnectionString params = new AstronConnectionString(directory);
            if (params.connectionString == null) {
                astronSalesLogger.error("readSalesInfo: no connectionString found");
            } else {
                try (Connection conn = getConnection(params)) {
                    salesBatch = readSalesInfoFromSQL(conn, params, machineryMap, directory);
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return salesBatch;
    }

    private AstronSalesBatch readSalesInfoFromSQL(Connection conn, AstronConnectionString params, Map<Integer, CashRegisterInfo> machineryMap, String directory) throws SQLException {

        List<SalesInfo> salesInfoList = new ArrayList<>();
        List<AstronRecord> recordList = new ArrayList<>();

        AstronSettings astronSettings = getSettings();
        Set<Integer> cashPayments = parsePayments(astronSettings.getCashPayments());
        Set<Integer> cardPayments = parsePayments(astronSettings.getCardPayments());
        Set<Integer> giftCardPayments = parsePayments(astronSettings.getGiftCardPayments());
        Set<Integer> customPayments = parsePayments(astronSettings.getCustomPayments());
        boolean ignoreSalesInfoWithoutCashRegister = astronSettings.isIgnoreSalesInfoWithoutCashRegister();
        boolean bonusPaymentAsDiscount = astronSettings.isBonusPaymentAsDiscount();
        boolean enableSqlLog = astronSettings.isEnableSqlLog();
        boolean newReadSalesQuery = astronSettings.isNewReadSalesQuery();

        conn.setAutoCommit(true); // autoCommit = false начинает транзакцию при первом запросе а reindex concurrently нельзя выполнять в транзакции
        checkExtraColumns(conn, params);
        createFusionProcessedIndex(conn, params);
        createSalesIndex(conn, params);
        if (newReadSalesQuery) {
            createSalesExtIndex(conn, params);
        }
        conn.setAutoCommit(false);

        try (Statement statement = conn.createStatement()) {
            String query = newReadSalesQuery ?
                    ("SELECT sales.SALESATTRS, sales.SYSTEMID, sales.SESSID, sales.SALESTIME, sales.FRECNUM, sales.CASHIERID, cashier.CASHIERNAME, " +
                    "sales.SALESTAG, sales.SALESBARC, sales.SALESCODE, sales.SALESCOUNT, sales.SALESPRICE, sales.SALESSUM, sales.SALESDISC, sales.SALESBONUS, " +
                    "sales.SALESTYPE, sales.SALESNUM, sales.SAREAID, sales.SALESREFUND, sales.PRCLEVELID, sales.SALESATTRI, " +
                    "COALESCE(sess.SESSSTART,sales.SALESTIME) AS SESSSTART, ext.SALESEXTVALUE AS idZReport, l.SALESEXTVALUE AS lot FROM SALES sales " +
                    "LEFT JOIN (SELECT SESSID, SYSTEMID, SAREAID, max(SESSSTART) AS SESSSTART FROM SESS GROUP BY SESSID, SYSTEMID, SAREAID) sess " +
                    "ON sales.SESSID=sess.SESSID AND sales.SYSTEMID=sess.SYSTEMID AND sales.SAREAID=sess.SAREAID " +
                    "LEFT JOIN CASHIER cashier ON sales.CASHIERID=cashier.CASHIERID " +
                    "LEFT JOIN SALESEXT ext ON sales.SAREAID=ext.SAREAID AND sales.SYSTEMID=ext.SYSTEMID AND sales.SESSID=ext.SESSID AND sales.SALESNUM=ext.SALESNUM AND ext.SALESEXTKEY = 38 " +
                    "LEFT JOIN SALESEXT l ON sales.SAREAID=l.SAREAID AND sales.SYSTEMID=l.SYSTEMID AND sales.SESSID=l.SESSID AND sales.SALESNUM=l.SALESNUM AND l.SALESEXTKEY = 65 " +
                    "WHERE FUSION_PROCESSED IS NULL AND SALESCANC = 0 ORDER BY SAREAID, SYSTEMID, SESSID, sales.FRECNUM, SALESTAG DESC, sales.SALESNUM")
                    :
                    ("SELECT sales.SALESATTRS, sales.SYSTEMID, sales.SESSID, sales.SALESTIME, sales.FRECNUM, sales.CASHIERID, cashier.CASHIERNAME, " +
                    "sales.SALESTAG, sales.SALESBARC, sales.SALESCODE, sales.SALESCOUNT, sales.SALESPRICE, sales.SALESSUM, sales.SALESDISC, sales.SALESBONUS, " +
                    "sales.SALESTYPE, sales.SALESNUM, sales.SAREAID, sales.SALESREFUND, sales.PRCLEVELID, sales.SALESATTRI, " +
                    "COALESCE(sess.SESSSTART,sales.SALESTIME) AS SESSSTART FROM SALES sales " +
                    "LEFT JOIN (SELECT SESSID, SYSTEMID, SAREAID, max(SESSSTART) AS SESSSTART FROM SESS GROUP BY SESSID, SYSTEMID, SAREAID) sess " +
                    "ON sales.SESSID=sess.SESSID AND sales.SYSTEMID=sess.SYSTEMID AND sales.SAREAID=sess.SAREAID " +
                    "LEFT JOIN CASHIER cashier ON sales.CASHIERID=cashier.CASHIERID " +
                    "WHERE FUSION_PROCESSED IS NULL AND SALESCANC = 0 ORDER BY SAREAID, SYSTEMID, SESSID, sales.FRECNUM, SALESTAG DESC, sales.SALESNUM");
            if (params.pgsql && newReadSalesQuery) {
                //Астрон использует postgresql 9.6 который неадекватно переоценивает Parallel Seq Scan. Выставляем заградительный cost если он включен
                statement.execute("SET parallel_tuple_cost = 10;");
            }
            ResultSet rs = statement.executeQuery(query);

            List<SalesInfo> curSalesInfoList = new ArrayList<>();
            List<AstronRecord> curRecordList = new ArrayList<>();
            String currentUniqueReceiptId = null;
            Map<String, Integer> uniqueReceiptIdNumberReceiptMap = new HashMap<>();
            Set<String> uniqueReceiptDetailIdSet = new HashSet<>();
            BigDecimal prologSum = BigDecimal.ZERO;
            String idDiscountCard = null;

            Map<String, GiftCard> sumGiftCardMap = new HashMap<>();
            List<Payment> payments = new ArrayList<>();
            String idSaleReceiptReceiptReturnDetail = null;

            Integer prevSAreaId = null;
            Integer prevNppCashRegister = null;
            Integer prevNumberReceipt = null;

            if(enableSqlLog) {
                astronSqlLogger.info("readSalesInfo");
                astronSqlLogger.info("SALESATTRS, SYSTEMID, SESSID, SALESTIME, FRECNUM, CASHIERID, CASHIERNAME, SALESTAG, " +
                                     "SALESBARC, SALESCODE, SALESCOUNT, SALESPRICE, SALESSUM, SALESDISC, SALESBONUS, " +
                                     " SALESTYPE, SALESNUM, SAREAID, SALESREFUND, PRCLEVELID, SALESATTRI, SESSSTART");
            }

            while (rs.next()) {

                String salesAttrs = rs.getString("SALESATTRS");
                Integer nppCashRegister = rs.getInt("SYSTEMID");
                Integer sessionId = rs.getInt("SESSID");
                String salesTime = rs.getString("SALESTIME");
                String idEmployee = String.valueOf(rs.getInt("CASHIERID"));
                String nameEmployee = rs.getString("CASHIERNAME");
                Integer salesTag = rs.getInt("SALESTAG");
                String salesBarc = rs.getString("SALESBARC");
                int salesCode = rs.getInt("SALESCODE");
                BigDecimal salesCount = rs.getBigDecimal("SALESCOUNT");
                BigDecimal salesPrice = rs.getBigDecimal("SALESPRICE");
                BigDecimal salesSum = rs.getBigDecimal("SALESSUM");
                BigDecimal salesDisc = rs.getBigDecimal("SALESDISC");
                BigDecimal salesBonus = rs.getBigDecimal("SALESBONUS");
                Integer salesType = rs.getInt("SALESTYPE");
                Integer originalSalesType = rs.getInt("SALESTYPE");
                Integer salesNum = rs.getInt("SALESNUM");
                Integer sAreaId = rs.getInt("SAREAID");
                int salesRefund = rs.getInt("SALESREFUND");
                int priceLevelId = rs.getInt("PRCLEVELID");
                long salesAttri = rs.getLong("SALESATTRI");
                String sessStart = rs.getString("SESSSTART");

                CashRegisterInfo cashRegister = machineryMap.get(nppCashRegister);
                Integer nppGroupMachinery = cashRegister == null ? null : cashRegister.numberGroup;
                String numberZReport = String.valueOf(sessionId);

                LocalDateTime salesDateTime = LocalDateTime.parse(salesTime, dateTimeFormatter);
                LocalDate dateReceipt = salesDateTime.toLocalDate();
                LocalTime timeReceipt = salesDateTime.toLocalTime();

                Integer numberReceipt;
                try {
                    numberReceipt = rs.getInt("FRECNUM");
                } catch (Exception e) {
                    //по какой-то причине есть чеки с FRECNUM = пустой строке
                    numberReceipt = 0;
                }

                if (numberReceipt == 0) {
                    astronSalesLogger.info(String.format("incorrect record with FRECNUM = 0: SAREAID %s, SYSTEMID %s, dateReceipt %s, timeReceipt %s, SALESNUM %s, SESSIONID %s", sAreaId, nppCashRegister, dateReceipt, timeReceipt, salesNum, sessionId));
                } else if ((!sAreaId.equals(prevSAreaId) || !nppCashRegister.equals(prevNppCashRegister) || !numberReceipt.equals(prevNumberReceipt)) && salesTag != 2 && salesTag != 3 && salesTag != 5) {
                    astronSalesLogger.info(String.format("incorrect record (new receipt started, but salesTag != 2 or 3 or 5) with SAREAID %s, SYSTEMID %s, FRECNUM %s, SALESTAG %s", sAreaId, nppCashRegister, numberReceipt, salesTag));
                } else {

                    prevSAreaId = sAreaId;
                    prevNppCashRegister = nppCashRegister;
                    prevNumberReceipt = numberReceipt;

                    String uniqueReceiptDetailId = getUniqueReceiptDetailId(sAreaId, nppCashRegister, sessionId, numberReceipt, salesNum);
                    //некоторые записи просто дублируются, такие игнорируем
                    if ((cashRegister != null || !ignoreSalesInfoWithoutCashRegister) && !uniqueReceiptDetailIdSet.contains(uniqueReceiptDetailId)) {
                        uniqueReceiptDetailIdSet.add(uniqueReceiptDetailId);

                        LocalDateTime sessStartDateTime = LocalDateTime.parse(sessStart, dateTimeFormatter);
                        LocalDate dateZReport = sessStartDateTime.toLocalDate();
                        LocalTime timeZReport = sessStartDateTime.toLocalTime();

                        Map<String, Object> receiptDetailExtraFields = new HashMap<>();

                        receiptDetailExtraFields.put("priceLevelId", priceLevelId);
                        receiptDetailExtraFields.put("salesAttri", salesAttri);

                        boolean isBonusPayment = bonusPaymentAsDiscount && salesType == 2;
                        boolean customPaymentType = customPayments.contains(salesType);
                        if (!customPaymentType) {
                            if (cashPayments.contains(salesType)) salesType = 0;
                            else if (cardPayments.contains(salesType)) salesType = 1;
                            else if (giftCardPayments.contains(salesType)) salesType = 2;
                        }

                        boolean isReturn = salesRefund != 0; // 0 - продажа, 1 - возврат, 2 - аннулирование

                        switch (salesTag) {
                            case 5:  //Аннулированная товарная позиция
                            case 3: {//Возвращенная товарная позиция
                                //Игнорируем эти записи. В дополнение к ним создаётся новая, с SALESTAG = 0 и SALESREFUND = 1
                                curRecordList.add(new AstronRecord(salesNum, sessionId, nppCashRegister, sAreaId));
                                break;
                            }
                            case 2: {//пролог чека
                                String uniqueReceiptId;
                                //увеличиваем id, если номер чека совпадает
                                while (uniqueReceiptIdNumberReceiptMap.containsKey(uniqueReceiptId = getUniqueReceiptId(sAreaId, nppCashRegister, sessionId, numberReceipt))) {
                                    numberReceipt += 100000000; //считаем, что касса никогда сама не достигнет стомиллионного чека. Может возникнуть переполнение, если таких чеков будет больше 21
                                    astronSalesLogger.info(String.format("Повтор номера чека: Касса %s, Z-отчёт %s, Чек %s", nppCashRegister, numberZReport, numberReceipt));
                                }
                                currentUniqueReceiptId = uniqueReceiptId;
                                uniqueReceiptIdNumberReceiptMap.put(uniqueReceiptId, numberReceipt);

                                sumGiftCardMap = new HashMap<>();
                                payments = new ArrayList<>();
                                if (prologSum.compareTo(BigDecimal.ZERO) == 0) {
                                    salesInfoList.addAll(curSalesInfoList);
                                    recordList.addAll(curRecordList);
                                } else {
                                    for(AstronRecord record : curRecordList) {
                                        astronSalesLogger.info(String.format("incorrect record: SAREAID %s, SYSTEMID %s, SALESNUM %s, SESSIONID %s", record.sAreaId, record.systemId, record.salesNum, record.sessId));
                                    }
                                }
                                curSalesInfoList = new ArrayList<>();
                                curRecordList = new ArrayList<>();
                                prologSum = salesSum;
                                idDiscountCard = trimToNull(salesBarc);

                                if (isReturn) { //чек возврата
                                    String[] salesAttrsSplitted = salesAttrs != null ? salesAttrs.split(":") : new String[0];
                                    String numberReceiptOriginal = salesAttrsSplitted.length > 3 ? salesAttrsSplitted[3] : null;
                                    String numberZReportOriginal = salesAttrsSplitted.length > 4 ? salesAttrsSplitted[4] : null;
                                    String numberCashRegisterOriginal = salesAttrsSplitted.length > 5 ? salesAttrsSplitted[5] : null;
                                    LocalDate dateReceiptOriginal = salesAttrsSplitted.length > 7 ? LocalDateTime.parse(salesAttrsSplitted[7], dateTimeFormatter).toLocalDate() : null;
                                    idSaleReceiptReceiptReturnDetail = nppGroupMachinery + "_" + numberCashRegisterOriginal + "_" + numberZReportOriginal + "_" + (dateReceiptOriginal != null ? dateReceiptOriginal.format(DateTimeFormatter.ofPattern("ddMMyyyy")) : "") + "_" + numberReceiptOriginal;
                                } else {
                                    idSaleReceiptReceiptReturnDetail = null;
                                }
                                curRecordList.add(new AstronRecord(salesNum, sessionId, nppCashRegister, sAreaId));
                                break;
                            }
                            case 1: {//оплата
                                if (!isBonusPayment) { //оплату бонусами игнорируем, она пойдёт как скидку на сумму SALESBONUS
                                    BigDecimal sum = safeDivide(salesSum, 100);
                                    if (isReturn) sum = safeNegate(sum);
                                    if (customPaymentType) {
                                        payments.add(new Payment(salesType, sum));
                                    } else {
                                        String[] salesBarcs = trimToEmpty(salesBarc).split(":");
                                        switch (salesType) {
                                            case 1:
                                                String cardNumber = null;
                                                if (salesBarcs.length > 0) {
                                                    cardNumber = salesBarcs[0];
                                                }
                                                payments.add(Payment.getCard(sum, "paymentCard", cardNumber));
                                                break;
                                            case 2:
                                                String numberGiftCard = salesBarcs.length > 0 ? salesBarcs[0] : null;
                                                GiftCard giftCard = sumGiftCardMap.getOrDefault(numberGiftCard, new GiftCard(BigDecimal.ZERO));
                                                giftCard.sum = safeAdd(giftCard.sum, sum);
                                                sumGiftCardMap.put(numberGiftCard, giftCard);
                                                break;
                                            case 0:
                                            default:
                                                payments.add(Payment.getCash(sum));
                                                break;
                                        }
                                    }
                                }
                                curRecordList.add(new AstronRecord(salesNum, sessionId, nppCashRegister, sAreaId));
                                break;
                            }
                            case 0: {//товарная позиция
                                numberReceipt = uniqueReceiptIdNumberReceiptMap.get(currentUniqueReceiptId);
                                String idBarcode = trimToNull(salesBarc);
                                String idItem = String.valueOf(salesCode);
                                boolean isWeight = !customPaymentType && (salesType == 0 || salesType == 2);
                                BigDecimal totalQuantity = safeDivide(salesCount, isWeight ? 1000 : 1);
                                BigDecimal price = safeDivide(salesPrice, 100);
                                BigDecimal sumReceiptDetail = safeDivide(salesSum, 100);
                                BigDecimal discountSumReceiptDetail = safeDivide(salesDisc, 100);
                                totalQuantity = isReturn ? totalQuantity.negate() : totalQuantity;
                                sumReceiptDetail = isReturn ? sumReceiptDetail.negate() : sumReceiptDetail;

                                if(bonusPaymentAsDiscount) {
                                    BigDecimal salesBonusValue = safeDivide(salesBonus, 100); //сумма социальной скидки для позиции
                                    if(salesBonusValue.compareTo(BigDecimal.ZERO) > 0) {
                                        receiptDetailExtraFields.put("salesBonus", salesBonusValue);
                                    }
                                }

                                SalesInfo salesInfo = getSalesInfo(false, false, nppGroupMachinery, nppCashRegister, numberZReport, dateZReport, timeZReport, numberReceipt, dateReceipt, timeReceipt,
                                        idEmployee, nameEmployee, null, sumGiftCardMap, payments, idBarcode, idItem, null,
                                        idSaleReceiptReceiptReturnDetail, totalQuantity, price, sumReceiptDetail, null, discountSumReceiptDetail,
                                        null, idDiscountCard, null, salesNum, null, null, false, null, receiptDetailExtraFields, cashRegister);

                                if (newReadSalesQuery) {
                                    String idZReport = rs.getString("idZReport");
                                    if (idZReport != null) {
                                        Map<String, Object> zReportExtraFields = new HashMap<>();
                                        zReportExtraFields.put("number", idZReport);
                                        salesInfo.zReportExtraFields = zReportExtraFields;
                                    }
                                    String idLot = rs.getString("lot");
                                    if (idLot!=null) {
                                        salesInfo.detailExtraFields = new HashMap<>();
                                        salesInfo.detailExtraFields.put("idLot", idLot.split("\\\\x1D",2)[0]); // марки приходят с хвостом, разделитель в виде строки x1D
                                    }
                                }
                                curSalesInfoList.add(salesInfo);
                                curRecordList.add(new AstronRecord(salesNum, sessionId, nppCashRegister, sAreaId));
                                prologSum = safeSubtract(prologSum, salesSum);
                                break;
                            }
                        }

                        if(enableSqlLog) {
                            astronSqlLogger.info(String.format("%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s",
                                    salesAttrs, nppCashRegister, sessionId, salesTime, numberReceipt, idEmployee, nameEmployee, salesTag,
                                    salesBarc, salesCode, salesCount, salesPrice, salesSum, salesDisc, salesBonus,
                                    originalSalesType, salesNum, sAreaId, salesRefund, priceLevelId, salesAttri, sessStart));
                        }

                    }
                }
            }

            if(prologSum.compareTo(BigDecimal.ZERO) == 0) {
                salesInfoList.addAll(curSalesInfoList);
                recordList.addAll(curRecordList);
            }

            if (salesInfoList.size() > 0)
                astronSalesLogger.info(String.format("found %s records", salesInfoList.size()));
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
        return new AstronSalesBatch(salesInfoList, recordList, directory);
    }

    private String getUniqueReceiptId(Integer sAreaId, Integer nppCashRegister, Integer sessionId, Integer numberReceipt) {
        return sAreaId + "/" + nppCashRegister + "/" + sessionId + "/" + numberReceipt;
    }

    private String getUniqueReceiptDetailId(Integer sAreaId, Integer nppCashRegister, Integer sessionId, Integer numberReceipt, Integer salesNum) {
        return getUniqueReceiptId(sAreaId, nppCashRegister, sessionId, numberReceipt) + "/" + salesNum;
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList, Set<Long> succeededRequests, Map<Long, Throwable> failedRequests, Map<Long, Throwable> ignoredRequests) {
        for (RequestExchange entry : requestExchangeList) {
            for (String directory : getDirectorySet(entry)) {
                AstronConnectionString params = new AstronConnectionString(directory);
                if (params.connectionString != null) {
                    astronSalesLogger.info("connecting to " + params.connectionString);
                    try (Connection conn = getConnection(params)) {

                        conn.setAutoCommit(true); // autoCommit = false начинает транзакцию при первом запросе а reindex concurrently нельзя выполнять в транзакции
                        checkExtraColumns(conn, params);
                        createFusionProcessedIndex(conn, params);
                        createSalestimeIndex(conn, params);
                        conn.setAutoCommit(false);

                        Statement statement = null;
                        try {

                            String dateFrom = entry.dateFrom.format(dateFormatter);
                            String dateTo = entry.dateTo.plusDays(1).format(dateFormatter);
                            String dateWhere = String.format("SALESTIME > '%s' AND SALESTIME < '%s'", dateFrom, dateTo);

                            StringBuilder stockWhere = new StringBuilder();
                            for(CashRegisterInfo cashRegister : getCashRegisterSet(entry, true)) {
                                stockWhere.append((stockWhere.length() == 0) ? "" : " OR ").append("SYSTEMID = ").append(cashRegister.number);
                            }

                            statement = conn.createStatement();
                            String query = params.pgsql ?
                                    "UPDATE sales SET fusion_processed = NULL WHERE (" + dateWhere + ")" + (stockWhere.length() > 0 ? (" AND (" + stockWhere + ")") : "") :
                                    "UPDATE [SALES] SET FUSION_PROCESSED = NULL WHERE (" + dateWhere + ")" + (stockWhere.length() > 0 ? (" AND (" + stockWhere + ")") : "");
                            astronSalesLogger.info("RequestSalesInfo: " + query);
                            statement.executeUpdate(query);
                            conn.commit();

                            succeededRequests.add(entry.requestExchange);

                        } catch (SQLException e) {
                            failedRequests.put(entry.requestExchange, e);
                            astronSalesLogger.error("RequestSalesInfo error", e);
                        } finally {
                            if (statement != null) statement.close();
                        }
                    } catch (ClassNotFoundException | SQLException e) {
                        throw Throwables.propagate(e);
                    }
                }
            }
        }
    }

    @Override
    public void finishReadingSalesInfo(AstronSalesBatch salesBatch) {
        for (String directory : salesBatch.directorySet) {
            AstronConnectionString params = new AstronConnectionString(directory);
            if (params.connectionString != null && salesBatch.recordList != null) {

                try (Connection conn = getConnection(params);
                     PreparedStatement ps = conn.prepareStatement(params.pgsql ?
                             "UPDATE sales SET fusion_processed = 1 WHERE SALESNUM = ? AND SESSID = ? AND SYSTEMID = ? AND SAREAID = ?" :
                             "UPDATE [SALES] SET FUSION_PROCESSED = 1 WHERE SALESNUM = ? AND SESSID = ? AND SYSTEMID = ? AND SAREAID = ?" )) {
                    int count = 0;
                    for (AstronRecord record : salesBatch.recordList) {
                        ps.setInt(1, record.salesNum);
                        ps.setInt(2, record.sessId);
                        ps.setInt(3, record.systemId);
                        ps.setInt(4, record.sAreaId);
                        ps.addBatch();
                        count++;
                        if(count > 20000) {
                            astronSalesLogger.info("FinishReadingSalesInfo: Commit " + count);
                            executeAndCommitBatch(ps, conn);
                            count = 0;
                        }
                    }
                    astronSalesLogger.info("FinishReadingSalesInfo: Commit " + count);
                    executeAndCommitBatch(ps, conn);

                } catch (SQLException | ClassNotFoundException e) {
                    throw Throwables.propagate(e);
                }
            }
        }
    }

    private void checkExtraColumns(Connection conn, AstronConnectionString params) {
        try (Statement statement = conn.createStatement()) {
            String query = params.pgsql ? "SELECT column_name FROM information_schema.columns WHERE table_name='sales' and column_name='fusion_processed'":
                                          "SELECT COL_LENGTH('SALES', 'FUSION_PROCESSED')";
            ResultSet result = statement.executeQuery(query);
            int exists = result.next() ? (params.pgsql ? 1 : result.getInt(1)) : 0;
            if(exists == 0) {
                throw new RuntimeException("Column 'FUSION_PROCESSED' doesn't exists");
            }
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    private void createFusionProcessedIndex(Connection conn, AstronConnectionString params) {
        try (Statement statement = conn.createStatement()) {
            String query = params.pgsql ?
                    String.format("CREATE INDEX CONCURRENTLY IF NOT EXISTS %s ON sales(salescanc, fusion_processed) WHERE fusion_processed IS NULL", getFusionProcessedIndexName()) :
                    String.format("IF NOT EXISTS (SELECT 1 WHERE IndexProperty(Object_Id('SALES'), '%s', 'IndexId') > 0) BEGIN CREATE INDEX %s ON SALES (SALESCANC, FUSION_PROCESSED) END",
                    getFusionProcessedIndexName(), getFusionProcessedIndexName());
            statement.execute(query);
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    protected String getFusionProcessedIndexName() {
        return "fusion";
    }


    protected void createSalesIndex(Connection conn, AstronConnectionString params) {
        try (Statement statement = conn.createStatement()) {
            String query = params.pgsql ?
                    "CREATE INDEX CONCURRENTLY IF NOT EXISTS sale ON sales(SALESNUM, SESSID, SYSTEMID, SAREAID)" :
                    "IF NOT EXISTS (SELECT 1 WHERE IndexProperty(Object_Id('SALES'), 'sale', 'IndexId') > 0) BEGIN CREATE INDEX sale ON SALES (SALESNUM, SESSID, SYSTEMID, SAREAID) END";
            statement.execute(query);
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    protected void createSalestimeIndex(Connection conn, AstronConnectionString params) {
        try (Statement statement = conn.createStatement()) {
            String query = params.pgsql ?
                    "CREATE INDEX CONCURRENTLY IF NOT EXISTS salestime ON sales(SALESTIME, SYSTEMID)" :
                    "IF NOT EXISTS (SELECT 1 WHERE IndexProperty(Object_Id('SALES'), 'salestime', 'IndexId') > 0) BEGIN CREATE INDEX salestime ON SALES (SALESTIME, SYSTEMID) END";
            statement.execute(query);
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    protected void createSalesExtIndex(Connection conn, AstronConnectionString params) {
        try (Statement statement = conn.createStatement()) {
            String query = params.pgsql ?
                    "CREATE INDEX CONCURRENTLY IF NOT EXISTS salesextid ON salesext(SAREAID, SYSTEMID, SESSID, SALESNUM, SALESEXTKEY)" :
                    "IF NOT EXISTS (SELECT 1 WHERE IndexProperty(Object_Id('SALESEXT'), 'salesextid', 'IndexId') > 0) BEGIN CREATE INDEX salesextid ON SALESEXT (SAREAID, SYSTEMID, SESSID, SALESNUM, SALESEXTKEY) END";
            statement.execute(query);
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    public void executeAndCommitBatch(PreparedStatement ps, Connection conn) throws SQLException {
        ps.executeBatch();
        conn.commit();
        astronLogger.info("execute and commit batch finished");
    }

    public class AstronRecord {
        Integer salesNum;
        Integer sessId;
        Integer systemId;
        Integer sAreaId;

        public AstronRecord(Integer salesNum, Integer sessId, Integer systemId, Integer sAreaId) {
            this.salesNum = salesNum;
            this.sessId = sessId;
            this.systemId = systemId;
            this.sAreaId = sAreaId;
        }
    }

    private Connection getConnection(AstronConnectionString params) throws SQLException, ClassNotFoundException {
        Connection conn;
        if(params.pgsql) {
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection(params.connectionString, params.user, params.password);
            conn.setNetworkTimeout(Executors.newSingleThreadExecutor(), 1800000); //30m
            conn.setAutoCommit(false);
        } else {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            //https://github.com/Microsoft/mssql-jdbc/wiki/QueryTimeout
            conn = DriverManager.getConnection(params.connectionString + ";queryTimeout=5", params.user, params.password);
            conn.setAutoCommit(false);
        }
        return conn;
    }

    private String getExtIdItemGroup(ItemInfo item) {
        if (item instanceof CashRegisterItem) return parseGroup(((CashRegisterItem) item).extIdItemGroup);
        if (item instanceof StopListItem) return parseGroup(item.idItemGroup);
        return null;
    }

    private String parseGroup(String idItemGroup) {
        try {
            return idItemGroup == null || idItemGroup.equals("Все") ? null : idItemGroup.replaceAll("[^0-9]", "");
        } catch (Exception e) {
            astronLogger.error("Failed to parse idItemGroup: " + idItemGroup);
            return null;
        }
    }

    private String parseGroup(String idItemGroup, boolean trimToNull) {
        try {
            String result = idItemGroup == null || idItemGroup.equals("Все") ? null : idItemGroup.replaceAll("[^0-9]", "");
            return trimToNull && result != null && result.isEmpty() ? null : result;
        } catch (Exception e) {
            return null;
        }
    }

    private String concat(String left, String right, String splitter) {
        return left + (left.isEmpty() ? "" : splitter) + right;
    }

    private Integer getPriceLevelId(Integer nppGroupMachinery, boolean exportExtraTables) {
       return getPriceLevelId(nppGroupMachinery, exportExtraTables, 1);
    }

    private Integer getPriceLevelId(Integer nppGroupMachinery, boolean exportExtraTables, int priceNumber) {
        return exportExtraTables ? (nppGroupMachinery * 1000 + priceNumber) : nppGroupMachinery;
    }

    private RuntimeException createException(String message) {
        astronLogger.error(message);
        return new RuntimeException(message);
    }

    private int getLocked(String extInfo) {
        int locked = 0;
        for (JSONObject infoJSON : getExtInfo(extInfo).jsonObjects) {
            if (infoJSON.has("locked")) {
                locked = infoJSON.getInt("locked");
                break;
            }
        }
        return locked;
    }

    private AstronSettings getSettings() {
        return springContext.containsBean("astronSettings") ? (AstronSettings) springContext.getBean("astronSettings") : new AstronSettings();
    }

    private ExtInfo getExtInfo(String extInfo) {
        return new ExtInfo(extInfo);
    }

    //{"astron", "astron2"} is used only in getJsonTables
    //In other cases is used only first
    private class ExtInfo {
        private List<JSONObject> jsonObjects;

        public ExtInfo(String extInfo) {
            this.jsonObjects = new ArrayList<>();
            if (extInfo != null && !extInfo.isEmpty()) {
                try {
                    JSONObject extInfoJSON = new JSONObject(extInfo);
                    for (String key : new String[]{"astron", "astron2"}) {
                        JSONObject jsonObject = extInfoJSON.optJSONObject(key);
                        if (jsonObject != null)
                            jsonObjects.add(jsonObject);
                    }
                } catch (Throwable t) {
                    throw new RuntimeException("Failed to parse extInfo: " + extInfo, t);
                }
            }
        }

        public JSONObject first() {
            return jsonObjects.isEmpty() ? null : jsonObjects.get(0);
        }
    }
}