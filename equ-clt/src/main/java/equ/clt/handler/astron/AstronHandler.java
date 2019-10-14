package equ.clt.handler.astron;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.CashRegisterInfo;
import equ.api.cashregister.CashRegisterItemInfo;
import equ.api.cashregister.DiscountCard;
import equ.api.cashregister.TransactionCashRegisterInfo;
import equ.clt.handler.DefaultCashRegisterHandler;
import equ.clt.handler.HandlerUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static equ.clt.handler.HandlerUtils.*;

@SuppressWarnings("SqlDialectInspection")
public class AstronHandler extends DefaultCashRegisterHandler<AstronSalesBatch> {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger processStopListLogger = Logger.getLogger("StopListLogger");
    protected final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");

    static Logger astronLogger;
    static {
        try {
            astronLogger = Logger.getLogger("astronLog");
            astronLogger.setLevel(Level.INFO);
            FileAppender fileAppender = new FileAppender(new EnhancedPatternLayout("%d{DATE} %5p %c{1} - %m%n%throwable{1000}"),
                    "logs/astron.log");
            astronLogger.removeAllAppenders();
            astronLogger.addAppender(fileAppender);

        } catch (Exception ignored) {
        }
    }

    private static String logPrefix = "Astron: ";

    private static Map<String, Map<String, CashRegisterItemInfo>> deleteBarcodeConnectionStringMap = new HashMap<>();

    private FileSystemXmlApplicationContext springContext;

    public AstronHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        return "astron";
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) {

        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        if (transactionList != null) {

            AstronSettings astronSettings = springContext.containsBean("astronSettings") ? (AstronSettings) springContext.getBean("astronSettings") : null;
            Integer timeout = astronSettings == null || astronSettings.getTimeout() == null ? 300 : astronSettings.getTimeout();
            Map<Integer, Integer> groupMachineryMap = astronSettings == null ? new HashMap<>() : astronSettings.getGroupMachineryMap();
            boolean exportExtraTables = astronSettings != null && astronSettings.isExportExtraTables();
            boolean exportSAreaPrc = astronSettings != null && astronSettings.isExportSAreaPrc();

            for (TransactionCashRegisterInfo transaction : transactionList) {

                Set<String> deleteBarcodeSet = new HashSet<>();

                String directory = null;
                for (CashRegisterInfo cashRegister : transaction.machineryInfoList) {
                    if (cashRegister.directory != null) {
                        directory = cashRegister.directory;
                    }
                }

                Throwable exception = null;

                AstronConnectionString params = new AstronConnectionString(directory);
                if (params.connectionString == null) {
                    processTransactionLogger.error(logPrefix + "no connectionString found");
                    exception = new RuntimeException("no connectionString found");
                } else {

                    try (Connection conn = getConnection(params)) {

                        String deleteBarcodeKey = getDeleteBarcodeKey(params.connectionString, transaction.nppGroupMachinery);
                        Map<String, CashRegisterItemInfo> deleteBarcodeMap = deleteBarcodeConnectionStringMap.get(deleteBarcodeKey);

                        Integer extGrpId = groupMachineryMap.get(transaction.nppGroupMachinery);
                        String tables = "'GRP', 'ART', 'UNIT', 'PACK', 'EXBARC', 'PACKPRC'" + (extGrpId != null ? ", 'ARTEXTGRP'" : "") +
                                (exportExtraTables ? ", 'PRCLEVEL', 'SAREA'" + (exportSAreaPrc ? ", 'SAREAPRC'" : "") : "");

                        int flags = checkFlags(conn, params, tables);
                        if (flags > 0) {
                            exception = new RuntimeException(String.format("data from previous transactions was not processed (%s flags not set to zero)", flags));
                        } else {
                            truncateTables(conn, extGrpId);

                            List<CashRegisterItemInfo> usedDeleteBarcodeList = new ArrayList<>();

                            ListIterator<CashRegisterItemInfo> iter = transaction.itemsList.listIterator();
                            while (iter.hasNext()) {
                                CashRegisterItemInfo item = iter.next();
                                if (!isValidItem(item)) {
                                    processTransactionLogger.info(logPrefix + String.format("transaction %s, invalid item: barcode %s, id %s, uom %s", transaction.id, item.idBarcode, item.idItem, item.idUOM));
                                    iter.remove();
                                } else if(deleteBarcodeMap != null && deleteBarcodeMap.containsKey(item.idItem)) {
                                    usedDeleteBarcodeList.add(deleteBarcodeMap.get(item.idItem));
                                }
                            }

                            if (transaction.itemsList != null) {
                                processTransactionLogger.info(logPrefix + String.format("transaction %s, table grp", transaction.id));
                                exportGrp(conn, params, transaction);

                                processTransactionLogger.info(logPrefix + String.format("transaction %s, table art", transaction.id));
                                exportArt(conn, params, transaction);

                                processTransactionLogger.info(logPrefix + String.format("transaction %s, table unit", transaction.id));
                                exportUnit(conn, params, transaction);

                                processTransactionLogger.info(logPrefix + String.format("transaction %s, table pack", transaction.id));
                                exportPack(conn, params, transaction);
                                exportPackDeleteBarcode(conn, params, usedDeleteBarcodeList);

                                processTransactionLogger.info(logPrefix + String.format("transaction %s, table exbarc", transaction.id));
                                exportExBarc(conn, params, transaction);
                                exportExBarcDeleteBarcode(conn, params, usedDeleteBarcodeList);

                                processTransactionLogger.info(logPrefix + String.format("transaction %s, table packprc", transaction.id));
                                exportPackPrc(conn, params, transaction, exportExtraTables);
                                exportPackPrcDeleteBarcode(conn, params, transaction, usedDeleteBarcodeList, exportExtraTables);

                                if(exportExtraTables) {
                                    processTransactionLogger.info(logPrefix + String.format("transaction %s, table prclevel", transaction.id));
                                    exportPrcLevel(conn, params, transaction);
                                    processTransactionLogger.info(logPrefix + String.format("transaction %s, table sarea", transaction.id));
                                    exportSArea(conn, params, transaction);
                                    if(exportSAreaPrc) {
                                        processTransactionLogger.info(logPrefix + String.format("transaction %s, table sareaprc", transaction.id));
                                        exportSAreaPrc(conn, params, transaction);
                                    }
                                }

                                if(extGrpId != null) {
                                    processTransactionLogger.info(logPrefix + String.format("transaction %s, table ARTEXTGRP", transaction.id));
                                    exportArtExtgrp(conn, params, transaction, extGrpId);
                                }

                                processTransactionLogger.info(logPrefix + "waiting for processing transactions");
                                exportFlags(conn, params, tables);
                                exception = waitFlags(conn, params, tables, usedDeleteBarcodeList, deleteBarcodeKey, timeout, false);
                                if(exception == null) {
                                    for(CashRegisterItemInfo usedDeleteBarcode : usedDeleteBarcodeList) {
                                        deleteBarcodeSet.add(usedDeleteBarcode.idBarcode);
                                    }
                                }
                            }

                        }

                    } catch (Exception e) {
                        processTransactionLogger.error(logPrefix, e);
                        exception = e;
                    }
                }
                sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(null, null, transaction.nppGroupMachinery, deleteBarcodeSet, exception));
            }
        }
        return sendTransactionBatchMap;
    }

    private void exportGrp(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction) throws SQLException {
        String[] keys = new String[]{"GRPID"};
        String[] columns = new String[]{"GRPID", "PARENTGRPID", "GRPNAME", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "GRP", columns, keys)) {
            int offset = columns.length + keys.length;

            for (int i = 0; i < transaction.itemsList.size(); i++) {
                List<ItemGroup> itemGroupList = transaction.itemGroupMap.get(transaction.itemsList.get(i).extIdItemGroup);
                if (itemGroupList != null) {
                    for (ItemGroup itemGroup : itemGroupList) {
                        if (!Thread.currentThread().isInterrupted()) {
                            String idGroup = parseGroup(itemGroup.extIdItemGroup);
                            if (idGroup != null && !idGroup.isEmpty()) {
                                if(params.pgsql) {
                                    String parentId = parseGroup(itemGroup.idParentItemGroup, true);
                                    setObject(ps, Integer.parseInt(idGroup), 1); //GRPID
                                    setObject(ps, parentId != null ? Integer.parseInt(parentId) : parentId, 2); //PARENTGRPID
                                    setObject(ps, trim(itemGroup.nameItemGroup, "", 50), 3); //GRPNAME
                                    setObject(ps, 0, 4); //DELFLAG
                                } else {
                                    setObject(ps, idGroup, 1, offset); //GRPID
                                    setObject(ps, parseGroup(itemGroup.idParentItemGroup), 2, offset); //PARENTGRPID
                                    setObject(ps, trim(itemGroup.nameItemGroup, "", 50), 3, offset); //GRPNAME
                                    setObject(ps, "0", 4, offset); //DELFLAG

                                    setObject(ps, parseGroup(itemGroup.extIdItemGroup), 5); //GRPID
                                }
                                ps.addBatch();
                            }
                        } else break;
                    }
                }
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private void exportArt(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction) throws SQLException {
        String[] keys = new String[]{"ARTID"};
        String[] columns = new String[]{"ARTID", "GRPID", "TAXGRPID", "ARTCODE", "ARTNAME", "ARTSNAME", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "ART", columns, keys)) {
            int offset = columns.length + keys.length;

            for (int i = 0; i < transaction.itemsList.size(); i++) {
                if (!Thread.currentThread().isInterrupted()) {
                    CashRegisterItemInfo item = transaction.itemsList.get(i);
                    Integer idItem = parseIdItem(item);
                    if(params.pgsql) {
                        String grpId = parseGroup(item.extIdItemGroup);
                        if(grpId != null && !grpId.isEmpty()) {
                            setObject(ps, idItem, 1); //ARTID
                            setObject(ps, Integer.parseInt(grpId), 2); //GRPID
                            setObject(ps, getIdVAT(item.vat), 3); //TAXGRPID
                            setObject(ps, idItem, 4); //ARTCODE
                            setObject(ps, trim(item.name, "", 50), 5); //ARTNAME
                            setObject(ps, trim(item.name, "", 50), 6); //ARTSNAME
                            setObject(ps, 0, 7); //DELFLAG
                        }
                    } else {
                        setObject(ps, idItem, 1, offset); //ARTID
                        setObject(ps, parseGroup(item.extIdItemGroup), 2, offset); //GRPID
                        setObject(ps, getIdVAT(item.vat), 3, offset); //TAXGRPID
                        setObject(ps, idItem, 4, offset); //ARTCODE
                        setObject(ps, trim(item.name, "", 50), 5, offset); //ARTNAME
                        setObject(ps, trim(item.name, "", 50), 6, offset); //ARTSNAME
                        setObject(ps, "0", 7, offset); //DELFLAG

                        setObject(ps, idItem, 8); //ARTID
                    }

                    ps.addBatch();
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private Integer getIdVAT(BigDecimal vat) {
        Integer result = 0;
        if (vat != null) {
            if (vat.compareTo(BigDecimal.valueOf(10)) == 0) {
                result = 1;
            } else if (vat.compareTo(BigDecimal.valueOf(20)) == 0) {
                result = 2;
            }
        }
        return result;
    }

    private void exportArtExtgrp(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction, Integer extGrpId) throws SQLException {
        String[] keys = new String[]{"ARTID"};
        String[] columns = new String[]{"ARTID", "EXTGRPID", "DELFLAG "};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "ARTEXTGRP", columns, keys)) {
            int offset = columns.length + keys.length;

            for (int i = 0; i < transaction.itemsList.size(); i++) {
                if (!Thread.currentThread().isInterrupted()) {
                    CashRegisterItemInfo item = transaction.itemsList.get(i);
                    Integer idItem = parseIdItem(item);
                    if(params.pgsql) {
                        setObject(ps, idItem, 1); //ARTID
                        setObject(ps, extGrpId, 2); //EXTGRPID
                        setObject(ps, 0, 3); //DELFLAG
                    } else {
                        setObject(ps, idItem, 1, offset); //ARTID
                        setObject(ps, extGrpId, 2, offset); //EXTGRPID
                        setObject(ps, 0, 3, offset); //DELFLAG

                        setObject(ps, idItem, 4); //ARTID
                    }

                    ps.addBatch();
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }


    private void exportUnit(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction) throws SQLException {
        String[] keys = new String[]{"UNITID"};
        String[] columns = new String[]{"UNITID", "UNITNAME", "UNITFULLNAME", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "UNIT", columns, keys)) {
            int offset = columns.length + keys.length;

            for (int i = 0; i < transaction.itemsList.size(); i++) {
                if (!Thread.currentThread().isInterrupted()) {
                    CashRegisterItemInfo item = transaction.itemsList.get(i);
                    Integer idUOM = parseUOM(item.idUOM);
                    if(params.pgsql) {
                        setObject(ps, idUOM, 1); //UNITID
                        setObject(ps, item.shortNameUOM, 2); //UNITNAME
                        setObject(ps, item.shortNameUOM, 3); //UNITFULLNAME
                        setObject(ps, 0, 4); //DELFLAG
                    } else {
                        setObject(ps, idUOM, 1, offset); //UNITID
                        setObject(ps, item.shortNameUOM, 2, offset); //UNITNAME
                        setObject(ps, item.shortNameUOM, 3, offset); //UNITFULLNAME
                        setObject(ps, "0", 4, offset); //DELFLAG

                        setObject(ps, idUOM, 5); //UNITID
                    }

                    ps.addBatch();
                    ps.executeBatch();
                    conn.commit();
                } else break;
            }
        }
    }

    private Integer parseUOM(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            processTransactionLogger.error("Unable to parse UOM " + value, e);
            return null;
        }
    }

    private void exportPack(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction) throws SQLException {
        String[] keys = new String[]{"PACKID"};
        String[] columns = new String[]{"PACKID", "ARTID", "PACKQUANT", "PACKSHELFLIFE", "ISDEFAULT", "UNITID", "QUANTMASK", "PACKDTYPE", "PACKNAME", "DELFLAG", "BARCID"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "PACK", columns, keys)) {
            int offset = columns.length + keys.length;

            Set<Integer> idItems = new HashSet<>();
            for (int i = 0; i < transaction.itemsList.size(); i++) {
                if (!Thread.currentThread().isInterrupted()) {
                    CashRegisterItemInfo item = transaction.itemsList.get(i);
                    Integer idUOM = parseUOM(item.idUOM);
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
                        setObject(ps, idUOM, 6); //UNITID
                        setObject(ps, item.splitItem ? 2 : 0, 7); //QUANTMASK
                        setObject(ps, item.passScalesItem ? 0 : item.splitItem ? 2 : 1, 8); //PACKDTYPE
                        setObject(ps, trim(item.name, "", 50), 9); //PACKNAME
                        setObject(ps, 0, 10); //DELFLAG
                        setObject(ps, item.passScalesItem ? 2 : null, 11); //BARCID
                    } else {
                        setObject(ps, packId, 1, offset); //PACKID
                        setObject(ps, idItem, 2, offset); //ARTID
                        setObject(ps, item.passScalesItem || item.splitItem ? "1000" : "1", 3, offset); //PACKQUANT
                        setObject(ps, "0", 4, offset); //PACKSHELFLIFE
                        if (idItems.contains(idItem)) {
                            setObject(ps, false, 5, offset); //ISDEFAULT
                        } else {
                            setObject(ps, true, 5, offset); //ISDEFAULT
                            idItems.add(idItem);
                        }
                        setObject(ps, idUOM, 6, offset); //UNITID
                        setObject(ps, item.splitItem ? 2 : "", 7, offset); //QUANTMASK
                        setObject(ps, item.passScalesItem ? 0 : item.splitItem ? 2 : 1, 8, offset); //PACKDTYPE
                        setObject(ps, trim(item.name, "", 50), 9, offset); //PACKNAME
                        setObject(ps, "0", 10, offset); //DELFLAG
                        setObject(ps, item.passScalesItem ? "2" : null, 11, offset); //BARCID

                        setObject(ps, packId, 12); //PACKID
                    }

                    ps.addBatch();
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private void exportPackDeleteBarcode(Connection conn, AstronConnectionString params, List<CashRegisterItemInfo> usedDeleteBarcodeList) throws SQLException {
        String[] keys = new String[]{"PACKID"};
        String[] columns = new String[]{"PACKID", "ARTID", "PACKQUANT", "PACKSHELFLIFE", "ISDEFAULT", "UNITID", "QUANTMASK", "PACKDTYPE", "PACKNAME", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "PACK", columns, keys)) {
            int offset = columns.length + keys.length;

            Set<Integer> idItems = new HashSet<>();
            for (CashRegisterItemInfo item : usedDeleteBarcodeList) {
                if (!Thread.currentThread().isInterrupted()) {
                    Integer idItem = parseIdItem(item);
                    Integer packId = getPackId(item);
                    if(params.pgsql) {
                        setObject(ps, packId, 1); //PACKID
                        setObject(ps, idItem, 2); //ARTID
                        setObject(ps, item.passScalesItem || item.splitItem ? "1000" : "1", 3); //PACKQUANT
                        setObject(ps, 0, 4); //PACKSHELFLIFE
                        if (idItems.contains(idItem)) {
                            setObject(ps, false, 5); //ISDEFAULT
                        } else {
                            setObject(ps, true, 5); //ISDEFAULT
                            idItems.add(idItem);
                        }
                        setObject(ps, "0", 6); //UNITID
                        setObject(ps, null, 7); //QUANTMASK
                        setObject(ps, item.passScalesItem ? 0 : 1, 8); //PACKDTYPE
                        setObject(ps, trim(item.name, "", 50), 9); //PACKNAME
                        setObject(ps, 1, 10); //DELFLAG
                    } else {
                        setObject(ps, packId, 1, offset); //PACKID
                        setObject(ps, idItem, 2, offset); //ARTID
                        setObject(ps, item.passScalesItem || item.splitItem ? "1000" : "1", 3, offset); //PACKQUANT
                        setObject(ps, "0", 4, offset); //PACKSHELFLIFE
                        if (idItems.contains(idItem)) {
                            setObject(ps, false, 5, offset); //ISDEFAULT
                        } else {
                            setObject(ps, true, 5, offset); //ISDEFAULT
                            idItems.add(idItem);
                        }
                        setObject(ps, "0", 6, offset); //UNITID
                        setObject(ps, "", 7, offset); //QUANTMASK
                        setObject(ps, item.passScalesItem ? 0 : 1, 8, offset); //PACKDTYPE
                        setObject(ps, trim(item.name, "", 50), 9, offset); //PACKNAME
                        setObject(ps, "1", 10, offset); //DELFLAG

                        setObject(ps, packId, 11); //PACKID
                    }

                    ps.addBatch();
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private Integer getPackId(CashRegisterItemInfo item) {
        //Потенциальная опасность, если база перейдёт границу Integer.MAX_VALUE
        return item.barcodeObject.intValue();
    }

    private Integer parseIdItem(CashRegisterItemInfo item) {
        try {
            return Integer.parseInt(item.idItem);
        } catch (Exception e) {
            return null;
        }
    }

    private void exportExBarc(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction) throws SQLException {
        String[] keys = new String[]{"EXBARCID"};
        String[] columns = new String[]{"EXBARCID", "PACKID", "EXBARCTYPE", "EXBARCBODY", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "EXBARC", columns, keys)) {
            int offset = columns.length + keys.length;

            for (int i = 0; i < transaction.itemsList.size(); i++) {
                if (!Thread.currentThread().isInterrupted()) {
                    CashRegisterItemInfo item = transaction.itemsList.get(i);
                    if (item.idBarcode != null) {
                        Integer packId = getPackId(item);
                        if(params.pgsql) {
                            setObject(ps, packId, 1); //EXBARCID
                            setObject(ps, packId, 2); //PACKID
                            setObject(ps, "", 3); //EXBARCTYPE
                            setObject(ps, item.idBarcode, 4); //EXBARCBODY
                            setObject(ps, 0, 5); //DELFLAG
                        } else {
                            setObject(ps, packId, 1, offset); //EXBARCID
                            setObject(ps, packId, 2, offset); //PACKID
                            setObject(ps, "", 3, offset); //EXBARCTYPE
                            setObject(ps, item.idBarcode, 4, offset); //EXBARCBODY
                            setObject(ps, "0", 5, offset); //DELFLAG

                            setObject(ps, packId, 6); //EXBARCID
                        }

                        ps.addBatch();
                    }
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private void exportExBarcDeleteBarcode(Connection conn, AstronConnectionString params, List<CashRegisterItemInfo> usedDeleteBarcodeList) throws SQLException {
        String[] keys = new String[]{"EXBARCID"};
        String[] columns = new String[]{"EXBARCID", "PACKID", "EXBARCTYPE", "EXBARCBODY", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "EXBARC", columns, keys)) {
            int offset = columns.length + keys.length;

            for (CashRegisterItemInfo item : usedDeleteBarcodeList) {
                if (!Thread.currentThread().isInterrupted()) {
                    if (item.idBarcode != null) {
                        Integer packId = getPackId(item);
                        if(params.pgsql) {
                            setObject(ps, packId, 1); //EXBARCID
                            setObject(ps, packId, 2); //PACKID
                            setObject(ps, "", 3); //EXBARCTYPE
                            setObject(ps, item.idBarcode, 4); //EXBARCBODY
                            setObject(ps, 1, 5); //DELFLAG
                        } else {
                            setObject(ps, packId, 1, offset); //EXBARCID
                            setObject(ps, packId, 2, offset); //PACKID
                            setObject(ps, "", 3, offset); //EXBARCTYPE
                            setObject(ps, item.idBarcode, 4, offset); //EXBARCBODY
                            setObject(ps, "1", 5, offset); //DELFLAG

                            setObject(ps, packId, 6); //EXBARCID
                        }

                        ps.addBatch();
                    }
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private void exportPackPrc(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction, boolean exportExtraTables) throws SQLException {
        String[] keys = new String[]{"PACKID", "PRCLEVELID"};
        String[] columns = new String[]{"PACKID", "PRCLEVELID", "PACKPRICE", "PACKMINPRICE", "PACKBONUSMINPRICE", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "PACKPRC", columns, keys)) {
            int offset = columns.length + keys.length;

            for (int i = 0; i < transaction.itemsList.size(); i++) {
                if (!Thread.currentThread().isInterrupted()) {
                    CashRegisterItemInfo item = transaction.itemsList.get(i);
                    if (item.price != null) {
                        Integer packId = getPackId(item);
                        if(params.pgsql) {
                            setObject(ps, packId, 1); //PACKID
                            setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, exportExtraTables), 2); //PRCLEVELID
                            BigDecimal packPrice = item.price.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : HandlerUtils.safeMultiply(item.price, 100);
                            setObject(ps, packPrice, 3); //PACKPRICE
                            setObject(ps, (item.flags == null || ((item.flags & 16) == 0)) && HandlerUtils.safeMultiply(item.price, 100) != null ? HandlerUtils.safeMultiply(item.price, 100) : HandlerUtils.safeMultiply(item.minPrice, 100) != null ? HandlerUtils.safeMultiply(item.minPrice, 100) : BigDecimal.ZERO, 4); //PACKMINPRICE
                            setObject(ps, 0, 5); //PACKBONUSMINPRICE
                            setObject(ps, 0, 6); //DELFLAG
                        } else {
                            setObject(ps, packId, 1, offset); //PACKID
                            setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, exportExtraTables), 2, offset); //PRCLEVELID
                            BigDecimal packPrice = item.price.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : HandlerUtils.safeMultiply(item.price, 100);
                            setObject(ps, packPrice, 3, offset); //PACKPRICE
                            setObject(ps, (item.flags == null || ((item.flags & 16) == 0)) && HandlerUtils.safeMultiply(item.price, 100) != null ? HandlerUtils.safeMultiply(item.price, 100) : HandlerUtils.safeMultiply(item.minPrice, 100) != null ? HandlerUtils.safeMultiply(item.minPrice, 100) : BigDecimal.ZERO, 4, offset); //PACKMINPRICE
                            setObject(ps, 0, 5, offset); //PACKBONUSMINPRICE
                            setObject(ps, "0", 6, offset); //DELFLAG

                            setObject(ps, packId, 7); //PACKID
                            setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, exportExtraTables), 8); //PRCLEVELID
                        }

                        ps.addBatch();
                    } else {
                        processTransactionLogger.error(logPrefix + String.format("transaction %s, table packprc, item %s without price", transaction.id, item.idItem));
                    }
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private void exportPackPrcDeleteBarcode(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction, List<CashRegisterItemInfo> usedDeleteBarcodeList, boolean exportExtraTables) throws SQLException {
        String[] keys = new String[]{"PACKID", "PRCLEVELID"};
        String[] columns = new String[]{"PACKID", "PRCLEVELID", "PACKPRICE", "PACKMINPRICE", "PACKBONUSMINPRICE", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "PACKPRC", columns, keys)) {
            int offset = columns.length + keys.length;

            for (CashRegisterItemInfo item : usedDeleteBarcodeList) {
                if (!Thread.currentThread().isInterrupted()) {
                    Integer packId = getPackId(item);
                    if(params.pgsql) {
                        setObject(ps, packId, 1); //PACKID
                        setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, exportExtraTables), 2); //PRCLEVELID
                        setObject(ps, BigDecimal.ZERO, 3); //PACKPRICE
                        setObject(ps, BigDecimal.ZERO, 4); //PACKMINPRICE
                        setObject(ps, 0, 5); //PACKBONUSMINPRICE
                        setObject(ps, 0, 6); //DELFLAG
                    } else {
                        setObject(ps, packId, 1, offset); //PACKID
                        setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, exportExtraTables), 2, offset); //PRCLEVELID
                        setObject(ps, BigDecimal.ZERO, 3, offset); //PACKPRICE
                        setObject(ps, BigDecimal.ZERO, 4, offset); //PACKMINPRICE
                        setObject(ps, 0, 5, offset); //PACKBONUSMINPRICE
                        setObject(ps, "0", 6, offset); //DELFLAG

                        setObject(ps, packId, 7); //PACKID
                        setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, exportExtraTables), 8); //PRCLEVELID
                    }

                    ps.addBatch();
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private void exportPrcLevel(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction) throws SQLException {
        String[] keys = new String[]{"PRCLEVELID"};
        String[] columns = new String[]{"PRCLEVELID", "PRCLEVELNAME", "PRCLEVELKEY", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "PRCLEVEL", columns, keys)) {
            int offset = columns.length + keys.length;

            if(params.pgsql) {
                setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, true), 1); //PRCLEVELID
                setObject(ps, transaction.nameGroupMachinery, 2); //PRCLEVELNAME
                setObject(ps, 0, 3); //PRCLEVELKEY
                setObject(ps, 0, 4); //DELFLAG
            } else {
                setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, true), 1, offset); //PRCLEVELID
                setObject(ps, transaction.nameGroupMachinery, 2, offset); //PRCLEVELNAME
                setObject(ps, 0, 3, offset); //PRCLEVELKEY
                setObject(ps, "0", 4, offset); //DELFLAG

                setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, true), 5); //PRCLEVELID
            }

            ps.addBatch();

            ps.executeBatch();
            conn.commit();
        }
    }

    private void exportSArea(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction) throws SQLException {
        String[] keys = new String[]{"SAREAID"};
        String[] columns = new String[]{"SAREAID", "PRCLEVELID", "CASHPROFILEID", "FIRMID", "CURRENCYID", "SAREANAME", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "SAREA", columns, keys)) {
            int offset = columns.length + keys.length;
            if(params.pgsql) {
                setObject(ps, transaction.nppGroupMachinery, 1); //SAREAID
                setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, true), 2); //PRCLEVELID
                setObject(ps, 1, 3); //CASHPROFILEID
                setObject(ps, 1, 4); //FIRMID
                setObject(ps, 933, 5); //CURRENCYID
                setObject(ps, transaction.nameGroupMachinery, 6); //SAREANAME
                setObject(ps, 0, 7); //DELFLAG
            } else {
                setObject(ps, transaction.nppGroupMachinery, 1, offset); //SAREAID
                setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, true), 2, offset); //PRCLEVELID
                setObject(ps, 1, 3, offset); //CASHPROFILEID
                setObject(ps, 1, 4, offset); //FIRMID
                setObject(ps, 933, 5, offset); //CURRENCYID
                setObject(ps, transaction.nameGroupMachinery, 6, offset); //SAREANAME
                setObject(ps, "0", 7, offset); //DELFLAG

                setObject(ps, transaction.nppGroupMachinery, 8); //SAREAID
            }

            ps.addBatch();

            ps.executeBatch();
            conn.commit();
        }
    }

    private void exportSAreaPrc(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction) throws SQLException {
        String[] keys = new String[]{"SAREAID", "PRCLEVELID"};
        String[] columns = new String[]{"SAREAID", "PRCLEVELID", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "SAREAPRC", columns, keys)) {
            if(params.pgsql) {
                setObject(ps, transaction.nppGroupMachinery, 1); //SAREAID
                setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, true), 2); //PRCLEVELID
                setObject(ps, 0, 3); //DELFLAG
            } else {
                int offset = columns.length + keys.length;
                setObject(ps, transaction.nppGroupMachinery, 1, offset); //SAREAID
                setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, true), 2, offset); //PRCLEVELID
                setObject(ps, "0", 3, offset); //DELFLAG

                setObject(ps, transaction.nppGroupMachinery, 4); //SAREAID
                setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, true), 5); //PRCLEVELID
            }

            ps.addBatch();

            ps.executeBatch();
            conn.commit();
        }
    }

    private void exportDCard(Connection conn, AstronConnectionString params, List<DiscountCard> discountCardList) throws SQLException {
        String[] keys = new String[]{"DCARDID"};
        String[] columns = new String[]{"DCARDID", "CLNTID", "DCARDCODE", "DCARDNAME", "ISPAYMENT", "DELFLAG", "LOCKED"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "DCARD", columns, keys)) {
            int offset = columns.length + keys.length;

            for (DiscountCard discountCard : discountCardList) {
                if (!Thread.currentThread().isInterrupted()) {
                    if(params.pgsql) {
                        setObject(ps, discountCard.idDiscountCard, 1); //DCARDID
                        setObject(ps, Integer.parseInt(discountCard.idDiscountCard), 2); //CLNTID
                        setObject(ps, discountCard.idDiscountCard, 3); //DCARDCODE
                        setObject(ps, discountCard.nameDiscountCard, 4); //DCARDNAME
                        setObject(ps, 0, 5); //ISPAYMENT
                        setObject(ps, 0, 6); //DELFLAG
                        setObject(ps, 0, 7); //LOCKED
                    } else {
                        setObject(ps, discountCard.idDiscountCard, 1, offset); //DCARDID
                        setObject(ps, discountCard.idDiscountCard, 2, offset); //CLNTID
                        setObject(ps, discountCard.idDiscountCard, 3, offset); //DCARDCODE
                        setObject(ps, discountCard.nameDiscountCard, 4, offset); //DCARDNAME
                        setObject(ps, false, 5, offset); //ISPAYMENT
                        setObject(ps, "0", 6, offset); //DELFLAG
                        setObject(ps, 0, 7, offset); //LOCKED

                        setObject(ps, discountCard.idDiscountCard, 8, offset); //DCARDID
                    }

                    ps.addBatch();
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private boolean isValidItem(CashRegisterItemInfo item) {
        return parseUOM(item.idUOM) != null && parseIdItem(item) != null;
    }

    private void exportFlags(Connection conn, AstronConnectionString params, String tables) throws SQLException {
        conn.setAutoCommit(true);
        try (Statement statement = conn.createStatement()) {
            String sql = params.pgsql ?
                    "UPDATE DATAPUMP SET recordnum = 1 WHERE dirname in (" + tables + ")" :
                    "UPDATE [DATAPUMP] SET recordnum = 1 WHERE dirname in (" + tables + ")";
            statement.executeUpdate(sql);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private Throwable waitFlags(Connection conn, AstronConnectionString params, String tables, List<CashRegisterItemInfo> usedDeleteBarcodeList, String deleteBarcodeKey, int timeout, boolean skipDeleteBarcode) throws InterruptedException {
        int count = 0;
        int flags;
        while ((flags = checkFlags(conn, params, tables)) != 0) {
            if (count > (timeout / 5)) {
                String message = String.format("data was sent to db but %s flag records were not set to zero", flags);
                processTransactionLogger.error(message);
                return new RuntimeException(message);
            } else {
                count++;
                processTransactionLogger.info(String.format("Waiting for setting to zero %s flag records", flags));
                Thread.sleep(5000);
            }
        }
        if(!skipDeleteBarcode) {
            Map<String, CashRegisterItemInfo> deleteBarcodeMap = deleteBarcodeConnectionStringMap.get(deleteBarcodeKey);
            for (CashRegisterItemInfo usedDeleteBarcode : usedDeleteBarcodeList) {
                deleteBarcodeMap.remove(usedDeleteBarcode.idItem);
            }
            deleteBarcodeConnectionStringMap.put(deleteBarcodeKey, deleteBarcodeMap);
        }
        return null;
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

    private void truncateTables(Connection conn, Integer extGrpId) throws SQLException {
        String[] tables = extGrpId != null ? new String[]{"GRP", "ART", "UNIT", "PACK", "EXBARC", "PACKPRC", "ARTEXTGRP"} : new String[]{"GRP", "ART", "UNIT", "PACK", "EXBARC", "PACKPRC"};
        for (String table : tables) {
            try (Statement s = conn.createStatement()) {
                s.execute("TRUNCATE TABLE " + table);
            }
        }
        conn.commit();
    }

    private void truncateTable(Connection conn, String name) throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("TRUNCATE TABLE " + name);
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

    private PreparedStatement getPreparedStatement(Connection conn, AstronConnectionString connectionParams, String table, String[] columnNames, /*List<String> integerColumnNames, */String[] keyNames) throws SQLException {
        String set = "";
        String columns = "";
        String params = "";
        for (String columnName : columnNames) {
            columns = concat(columns, columnName, ",");
            set = concat(set, columnName + "=?", ",");
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
    public void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) {
        AstronSettings astronSettings = springContext.containsBean("astronSettings") ? (AstronSettings) springContext.getBean("astronSettings") : null;
        boolean exportExtraTables = astronSettings != null && astronSettings.isExportExtraTables();

        for (String directory : directorySet) {
            AstronConnectionString params = new AstronConnectionString(directory);
            if (params.connectionString != null) {
                try (Connection conn = getConnection(params);
                     PreparedStatement ps = params.pgsql ?
                             conn.prepareStatement(String.format("UPDATE PACKPRC SET DELFLAG = %s WHERE PACKID=? AND PRCLEVELID=?", stopListInfo.exclude ? "0" : "1")) :
                             conn.prepareStatement(String.format("UPDATE [PACKPRC] SET DELFLAG = %s WHERE PACKID=? AND PRCLEVELID=?", stopListInfo.exclude ? "0" : "1"))) {

                    processStopListLogger.info(logPrefix + "executing stopLists, table packprc");
                    for (ItemInfo item : stopListInfo.stopListItemMap.values()) {
                        for (Integer nppGroupMachinery : stopListInfo.inGroupMachineryItemMap.keySet()) {
                            if (item instanceof CashRegisterItemInfo) { //todo: похоже, это условие никогда не выполняется
                                ps.setObject(1, getPackId((CashRegisterItemInfo) item)); //PACKID
                                ps.setObject(2, getPriceLevelId(nppGroupMachinery, exportExtraTables)); //PRCLEVELID
                                ps.addBatch();
                            }
                        }
                    }
                    ps.executeBatch();
                    conn.commit();

                    processTransactionLogger.info(logPrefix + "waiting for processing stopLists");
                    exportFlags(conn, params, "'PACKPRC'");

                } catch (Exception e) {
                    processStopListLogger.error(logPrefix, e);
                }
            }
        }
    }

    @Override
    public boolean sendDeleteBarcodeInfo(DeleteBarcodeInfo deleteBarcodeInfo) {
        AstronConnectionString params = new AstronConnectionString(deleteBarcodeInfo.directoryGroupMachinery);
        if (params.connectionString == null) {
            processTransactionLogger.error(logPrefix + "no connectionString found");
        } else if (deleteBarcodeInfo.directoryGroupMachinery != null && !deleteBarcodeInfo.barcodeList.isEmpty()) {

            String key = getDeleteBarcodeKey(params.connectionString, deleteBarcodeInfo.nppGroupMachinery);
            Map<String, CashRegisterItemInfo> deleteBarcodeMap = deleteBarcodeConnectionStringMap.get(key);
            if (deleteBarcodeMap == null)
                deleteBarcodeMap = new HashMap<>();
            for (CashRegisterItemInfo item : deleteBarcodeInfo.barcodeList) {
                if (!deleteBarcodeMap.containsKey(item.idItem)) {
                    deleteBarcodeMap.put(item.idItem, item);
                }
            }
            if (!deleteBarcodeMap.isEmpty())
                deleteBarcodeConnectionStringMap.put(key, deleteBarcodeMap);
            return false;
        }
        return true;
    }

    @Override
    public void sendDiscountCardList(List<DiscountCard> discountCardList, RequestExchange requestExchange) {

        AstronSettings astronSettings = springContext.containsBean("astronSettings") ? (AstronSettings) springContext.getBean("astronSettings") : null;
        Integer timeout = astronSettings == null || astronSettings.getTimeout() == null ? 300 : astronSettings.getTimeout();

        for (String directory : getDirectorySet(requestExchange)) {

            Throwable exception;

            AstronConnectionString params = new AstronConnectionString(directory);
            if (params.connectionString == null) {
                processTransactionLogger.error(logPrefix + "no connectionString found");
                exception = new RuntimeException("no connectionString found");
            } else {

                try (Connection conn = getConnection(params)) {
                    String table = "'DCARD'";

                    int flags = checkFlags(conn, params, table);
                    if (flags > 0) {
                        exception = new RuntimeException(String.format("data from previous transactions was not processed (%s flags not set to zero)", flags));
                    } else {
                        truncateTable(conn, "DCARD");

                        processTransactionLogger.info(logPrefix + "export table dcard");
                        exportDCard(conn, params, discountCardList);

                        processTransactionLogger.info(logPrefix + "waiting for processing transactions");
                        exportFlags(conn, params, table);
                        exception = waitFlags(conn, params, table, null, null, timeout, true);
                    }
                } catch (Exception e) {
                    processTransactionLogger.error(logPrefix, e);
                    exception = e;
                }
            }

            if(exception != null) {
                throw new RuntimeException(exception);
            }
        }
    }

    @Override
    public SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) {

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
                sendSalesLogger.error(logPrefix + "no connectionString found");
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

    private AstronSalesBatch readSalesInfoFromSQL(Connection conn, AstronConnectionString params, Map<Integer, CashRegisterInfo> machineryMap, String directory) {

        List<SalesInfo> salesInfoList = new ArrayList<>();
        List<AstronRecord> recordList = new ArrayList<>();

        checkExtraColumns(conn, params);
        createFusionProcessedIndex(conn, params);
        createSalesIndex(conn, params);

        try (Statement statement = conn.createStatement()) {
            String query = "SELECT sales.SALESATTRS, sales.SYSTEMID, sales.SESSID, sales.SALESTIME, sales.FRECNUM, sales.SRECNUM, sales.CASHIERID, cashier.CASHIERNAME, " +
                    "sales.SALESTAG, sales.SALESBARC, sales.SALESCODE, sales.SALESCOUNT, sales.SALESPRICE, sales.SALESSUM, sales.SALESDISC, sales.SALESTYPE, " +
                    "sales." + getSalesNumField() + ", sales.SAREAID, sales." + getSalesRefundField() + ", COALESCE(sess.SESSSTART,sales.SALESTIME) AS SESSSTART FROM SALES sales " +
                    "LEFT JOIN (SELECT SESSID, SYSTEMID, SAREAID, max(SESSSTART) AS SESSSTART FROM SESS GROUP BY SESSID, SYSTEMID, SAREAID) sess " +
                    "ON sales.SESSID=sess.SESSID AND sales.SYSTEMID=sess.SYSTEMID AND sales.SAREAID=sess.SAREAID " +
                    "LEFT JOIN CASHIER cashier ON sales.CASHIERID=cashier.CASHIERID " +
                    "WHERE (FUSION_PROCESSED IS NULL OR FUSION_PROCESSED = 0) AND SALESCANC = 0 ORDER BY SAREAID, SYSTEMID, SALESTIME, " + getSalesNumField() + ", SALESTAG DESC";
            ResultSet rs = statement.executeQuery(query);

            List<SalesInfo> curSalesInfoList = new ArrayList<>();
            List<AstronRecord> curRecordList = new ArrayList<>();
            String currentUniqueReceiptId = null;
            BigDecimal prologSum = BigDecimal.ZERO;

            BigDecimal sumCash = null;
            BigDecimal sumCard = null;
            BigDecimal sumGiftCard = null;
            String idSaleReceiptReceiptReturnDetail = null;
            while (rs.next()) {

                Integer sAreaId = rs.getInt("SAREAID");
                Integer nppCashRegister = rs.getInt("SYSTEMID");
                CashRegisterInfo cashRegister = machineryMap.get(nppCashRegister);
                Integer nppGroupMachinery = cashRegister == null ? null : cashRegister.numberGroup;

                Integer salesNum = rs.getInt(getSalesNumField());

                Integer sessionId = rs.getInt("SESSID");
                String numberZReport = String.valueOf(sessionId);

                long sessStart = DateUtils.parseDate(rs.getString("SESSSTART"), "yyyyMMddHHmmss").getTime();
                Date dateZReport = new Date(sessStart);
                Time timeZReport = new Time(sessStart);

                long salesTime = DateUtils.parseDate(rs.getString("SALESTIME"), "yyyyMMddHHmmss").getTime();
                Date dateReceipt = new Date(salesTime);
                Time timeReceipt = new Time(salesTime);

                Integer numberReceipt = rs.getInt("FRECNUM");
                String idEmployee = String.valueOf(rs.getInt("CASHIERID"));
                String nameEmployee = rs.getString("CASHIERNAME");
                Integer type = rs.getInt("SALESTYPE");
                boolean isWeight = type == 0 || type == 2;

                Integer recordType = rs.getInt("SALESTAG");
                boolean isReturn = rs.getInt(getSalesRefundField()) != 0; // 0 - продажа, 1 - возврат, 2 - аннулирование

                switch (recordType) {
                    case 0: {//товарная позиция
                        //временный лог для того, чтобы выявить, откуда попадают лишние оплаты в чек
//                        sendSalesLogger.info(String.format("sale: SAREAID %s, SYSTEMID %s, dateReceipt %s, timeReceipt %s, SALESNUM %s, SESSIONID %s, FRECNUM %s",
//                                rs.getInt("SAREAID"), nppCashRegister, dateReceipt, timeReceipt, salesNum, sessionId, numberReceipt));
                        String idBarcode = rs.getString("SALESBARC");
                        String idItem = String.valueOf(rs.getInt("SALESCODE"));
                        BigDecimal totalQuantity = safeDivide(rs.getBigDecimal("SALESCOUNT"), isWeight ? 1000 : 1);
                        BigDecimal price = safeDivide(rs.getBigDecimal("SALESPRICE"), 100);
                        BigDecimal sumReceiptDetail = safeDivide(rs.getBigDecimal("SALESSUM"), 100);
                        BigDecimal discountSumReceiptDetail = safeDivide(rs.getBigDecimal("SALESDISC"), 100);
                        totalQuantity = isReturn ? totalQuantity.negate() : totalQuantity;
                        sumReceiptDetail = isReturn ? sumReceiptDetail.negate() : sumReceiptDetail;
                        curSalesInfoList.add(new SalesInfo(false, nppGroupMachinery, nppCashRegister, numberZReport, dateZReport, timeZReport,
                                numberReceipt, dateReceipt, timeReceipt, idEmployee, nameEmployee, null, sumCard, sumCash, sumGiftCard, idBarcode, idItem,
                                null, idSaleReceiptReceiptReturnDetail, totalQuantity, price, sumReceiptDetail, discountSumReceiptDetail, null, null,
                                salesNum, null, null, cashRegister));
                        curRecordList.add(new AstronRecord(salesNum, sessionId, nppCashRegister, sAreaId));
                        prologSum = safeSubtract(prologSum, rs.getBigDecimal("SALESSUM"));
                        break;
                    }
                    case 1: {//оплата
                        //временный лог для того, чтобы выявить, откуда попадают лишние оплаты в чек
//                        sendSalesLogger.info(String.format("payment: SAREAID %s, SYSTEMID %s, dateReceipt %s, timeReceipt %s, SALESNUM %s, SESSIONID %s, FRECNUM %s",
//                                rs.getInt("SAREAID"), nppCashRegister, dateReceipt, timeReceipt, salesNum, sessionId, numberReceipt));
                        BigDecimal sum = safeDivide(rs.getBigDecimal("SALESSUM"), 100);
                        if(isReturn)
                            sum = safeNegate(sum);
                        switch (type) {
                            case 1:
                                sumCard = safeAdd(sumCard, sum);
                                break;
                            case 2:
                                sumGiftCard = safeAdd(sumGiftCard, sum);
                                break;
                            case 0:
                            default:
                                sumCash = safeAdd(sumCash, sum);
                                break;
                        }
                        curRecordList.add(new AstronRecord(salesNum, sessionId, nppCashRegister, sAreaId));
                        break;
                    }
                    case 2: {//пролог чека
                        //временный лог для того, чтобы выявить, откуда попадают лишние оплаты в чек
//                        sendSalesLogger.info(String.format("prolog: SAREAID %s, SYSTEMID %s, dateReceipt %s, timeReceipt %s, SALESNUM %s, SESSIONID %s, FRECNUM %s",
//                                rs.getInt("SAREAID"), nppCashRegister, dateReceipt, timeReceipt, salesNum, sessionId, numberReceipt));
                        String uniqueReceiptId = sAreaId + "/" + nppCashRegister + "/" + sessionId + "/" + numberReceipt;
                        if (uniqueReceiptId.equals(currentUniqueReceiptId)) {
                            astronLogger.info("Некорректные строки в чеке " + uniqueReceiptId);
                        } else {
                            currentUniqueReceiptId = uniqueReceiptId;
                        }

                        sumCash = null;
                        sumCard = null;
                        sumGiftCard = null;
                        if(prologSum.compareTo(BigDecimal.ZERO) == 0) {
                            salesInfoList.addAll(curSalesInfoList);
                            recordList.addAll(curRecordList);
                        } else {
                            sendSalesLogger.info(String.format("prolog sum differs: SAREAID %s, SYSTEMID %s, dateReceipt %s, timeReceipt %s, SALESNUM %s, SESSIONID %s, FRECNUM %s",
                                    rs.getInt("SAREAID"), nppCashRegister, dateReceipt, timeReceipt, salesNum, sessionId, numberReceipt));
                        }
                        curSalesInfoList = new ArrayList<>();
                        curRecordList = new ArrayList<>();
                        prologSum = rs.getBigDecimal("SALESSUM");

                        if (isReturn) { //чек возврата
                            String salesAttrs = rs.getString("SALESATTRS");
                            String[] salesAttrsSplitted = salesAttrs != null ? salesAttrs.split(":") : new String[0];
                            String numberReceiptOriginal = salesAttrsSplitted.length > 3 ? salesAttrsSplitted[3] : null;
                            String numberZReportOriginal = salesAttrsSplitted.length > 4 ? salesAttrsSplitted[4] : null;
                            String numberCashRegisterOriginal = salesAttrsSplitted.length > 5 ? salesAttrsSplitted[5] : null;
                            Date dateReceiptOriginal = salesAttrsSplitted.length > 7 ? new Date(DateUtils.parseDate(salesAttrsSplitted[7], "yyyyMMddHHmmss").getTime()) : null;
                            idSaleReceiptReceiptReturnDetail = nppGroupMachinery + "_" + numberCashRegisterOriginal + "_" + numberZReportOriginal + "_"
                                    + new SimpleDateFormat("ddMMyyyy").format(dateReceiptOriginal) + "_" + numberReceiptOriginal;
                        } else {
                            idSaleReceiptReceiptReturnDetail = null;
                        }
                        curRecordList.add(new AstronRecord(salesNum, sessionId, nppCashRegister, sAreaId));
                        break;
                    }
                    case 3: {//Возвращенная товарная позиция - игнорируем эту запись. В дополнение к ней создаётся новая, с SALESTAG = 0 и SALESREFUND = 1
                        curRecordList.add(new AstronRecord(salesNum, sessionId, nppCashRegister, sAreaId));
                        break;
                    }
                }
            }

            if(prologSum.compareTo(BigDecimal.ZERO) == 0) {
                salesInfoList.addAll(curSalesInfoList);
                recordList.addAll(curRecordList);
            }

            if (salesInfoList.size() > 0)
                sendSalesLogger.info(logPrefix + String.format("found %s records", salesInfoList.size()));
        } catch (SQLException | ParseException e) {
            throw Throwables.propagate(e);
        }
        return new AstronSalesBatch(salesInfoList, recordList, directory);
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList, Set<Long> succeededRequests, Map<Long, Throwable> failedRequests, Map<Long, Throwable> ignoredRequests) {
        for (RequestExchange entry : requestExchangeList) {
            for (String directory : getDirectorySet(entry)) {
                AstronConnectionString params = new AstronConnectionString(directory);
                if (params.connectionString != null) {
                    try (Connection conn = getConnection(params)) {

                        checkExtraColumns(conn, params);
                        createFusionProcessedIndex(conn, params);

                        Statement statement = null;
                        try {
                            StringBuilder dateWhere = new StringBuilder();
                            Long dateFrom = entry.dateFrom.getTime();
                            Long dateTo = entry.dateTo.getTime();
                            while (dateFrom <= dateTo) {
                                String dateString = new SimpleDateFormat("yyyyMMdd").format(new Date(dateFrom));
                                dateWhere.append((dateWhere.length() == 0) ? "" : " OR ").append("SALESTIME LIKE '").append(dateString).append("%'");
                                dateFrom += 86400000;
                            }
                            StringBuilder stockWhere = new StringBuilder();
                            for(CashRegisterInfo cashRegister : getCashRegisterSet(entry, true)) {
                                stockWhere.append((stockWhere.length() == 0) ? "" : " OR ").append("SYSTEMID = ").append(cashRegister.number);
                            }
                            if (dateWhere.length() > 0) {
                                statement = conn.createStatement();
                                String query = params.pgsql ?
                                        "UPDATE sales SET fusion_processed = 0 WHERE (" + dateWhere + ")" + (stockWhere.length() > 0 ? (" AND (" + stockWhere + ")") : "") :
                                        "UPDATE [SALES] SET FUSION_PROCESSED = 0 WHERE (" + dateWhere + ")" + (stockWhere.length() > 0 ? (" AND (" + stockWhere + ")") : "");
                                statement.executeUpdate(query);
                                conn.commit();
                            }
                            succeededRequests.add(entry.requestExchange);

                        } catch (SQLException e) {
                            failedRequests.put(entry.requestExchange, e);
                            sendSalesLogger.info(logPrefix, e);
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
                             "UPDATE sales SET fusion_processed = 1 WHERE " + getSalesNumField() + " = ? AND SESSID = ? AND SYSTEMID = ? AND SAREAID = ?" :
                             "UPDATE [SALES] SET FUSION_PROCESSED = 1 WHERE " + getSalesNumField() + " = ? AND SESSID = ? AND SYSTEMID = ? AND SAREAID = ?" )) {
                    int count = 0;
                    for (AstronRecord record : salesBatch.recordList) {
                        ps.setInt(1, record.salesNum);
                        ps.setInt(2, record.sessId);
                        ps.setInt(3, record.systemId);
                        ps.setInt(4, record.sAreaId);
                        ps.addBatch();
                        count++;
                        if(count > 20000) {
                            ps.executeBatch();
                            conn.commit();
                            count = 0;
                        }
                    }
                    ps.executeBatch();
                    conn.commit();

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
                    String.format("CREATE INDEX IF NOT EXISTS %s ON sales(fusion_processed)", getFusionProcessedIndexName()) :
                    String.format("IF NOT EXISTS (SELECT 1 WHERE IndexProperty(Object_Id('SALES'), '%s', 'IndexId') > 0) BEGIN CREATE INDEX %s ON SALES (FUSION_PROCESSED) END",
                    getFusionProcessedIndexName(), getFusionProcessedIndexName());
            statement.execute(query);
            conn.commit();
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
                    String.format("CREATE INDEX IF NOT EXISTS sale ON sales(%s, SESSID, SYSTEMID, SAREAID)", getSalesNumField()) :
                    "IF NOT EXISTS (SELECT 1 WHERE IndexProperty(Object_Id('SALES'), 'sale', 'IndexId') > 0) BEGIN CREATE INDEX sale ON SALES (" + getSalesNumField() + ", SESSID, SYSTEMID, SAREAID) END";
            statement.execute(query);
            conn.commit();
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
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
            conn.setAutoCommit(false);
        } else {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            //https://github.com/Microsoft/mssql-jdbc/wiki/QueryTimeout
            conn = DriverManager.getConnection(params.connectionString + ";queryTimeout=5", params.user, params.password);
            conn.setAutoCommit(false);
        }
        return conn;
    }

    private String parseGroup(String idItemGroup) {
        try {
            return idItemGroup == null || idItemGroup.equals("Все") ? null : idItemGroup.replaceAll("[^0-9]", "");
        } catch (Exception e) {
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

    private String getDeleteBarcodeKey(String connectionString, Integer nppGroupMachinery) {
        return  connectionString + "/" + nppGroupMachinery;
    }

    private Integer getPriceLevelId(Integer nppGroupMachinery, boolean exportExtraTables) {
       return exportExtraTables ? (nppGroupMachinery * 1000 + 1) : nppGroupMachinery;
    }

    protected String getSalesNumField() {
        return "SALESNUM";
    }

    protected String getSalesRefundField() {
        return "SALESREFUND";
    }
}