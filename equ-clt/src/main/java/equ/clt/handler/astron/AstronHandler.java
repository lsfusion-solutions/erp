package equ.clt.handler.astron;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.CashRegisterInfo;
import equ.api.cashregister.CashRegisterItemInfo;
import equ.api.cashregister.TransactionCashRegisterInfo;
import equ.clt.handler.DefaultCashRegisterHandler;
import equ.clt.handler.HandlerUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static equ.clt.handler.HandlerUtils.*;

public class AstronHandler extends DefaultCashRegisterHandler<AstronSalesBatch> {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger processStopListLogger = Logger.getLogger("StopListLogger");
    protected final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");

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
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) throws IOException {

        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        if (transactionList != null) {

            try {

                Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

                AstronSettings astronSettings = springContext.containsBean("astronSettings") ? (AstronSettings) springContext.getBean("astronSettings") : null;
                Integer timeout = astronSettings == null || astronSettings.getTimeout() == null ? 300 : astronSettings.getTimeout();
                Map<Integer, Integer> groupMachineryMap = astronSettings == null ? new HashMap<Integer, Integer>() : astronSettings.getGroupMachineryMap();

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

                        try (Connection conn = getConnection(params.connectionString, params.user, params.password)) {

                            String deleteBarcodeKey = getDeleteBarcodeKey(params.connectionString, transaction.nppGroupMachinery);
                            Map<String, CashRegisterItemInfo> deleteBarcodeMap = deleteBarcodeConnectionStringMap.get(deleteBarcodeKey);

                            Integer extGrpId = groupMachineryMap.get(transaction.nppGroupMachinery);
                            String tables = "'GRP', 'ART', 'UNIT', 'PACK', 'EXBARC', 'PACKPRC'" + (extGrpId != null ? ", 'ARTEXTGRP'" : "");

                            int flags = checkFlags(conn, tables);
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
                                    } else if(deleteBarcodeMap != null && deleteBarcodeMap.containsKey(item.idBarcode)) {
                                        usedDeleteBarcodeList.add(item);
                                        iter.remove();
                                    }
                                }

                                if (transaction.itemsList != null) {
                                    processTransactionLogger.info(logPrefix + String.format("transaction %s, table grp", transaction.id));
                                    exportGrp(conn, transaction);

                                    processTransactionLogger.info(logPrefix + String.format("transaction %s, table art", transaction.id));
                                    exportArt(conn, transaction);

                                    processTransactionLogger.info(logPrefix + String.format("transaction %s, table unit", transaction.id));
                                    exportUnit(conn, transaction);

                                    processTransactionLogger.info(logPrefix + String.format("transaction %s, table pack", transaction.id));
                                    exportPack(conn, transaction);
                                    exportPackDeleteBarcode(conn, usedDeleteBarcodeList);

                                    processTransactionLogger.info(logPrefix + String.format("transaction %s, table exbarc", transaction.id));
                                    exportExBarc(conn, transaction);
                                    exportExBarcDeleteBarcode(conn, usedDeleteBarcodeList);

                                    processTransactionLogger.info(logPrefix + String.format("transaction %s, table packprc", transaction.id));
                                    exportPackPrc(conn, transaction);
                                    exportPackPrcDeleteBarcode(conn, transaction, usedDeleteBarcodeList);

                                    if(extGrpId != null) {
                                        processTransactionLogger.info(logPrefix + String.format("transaction %s, table ARTEXTGRP", transaction.id));
                                        exportArtExtgrp(conn, transaction, extGrpId);
                                    }

                                    processTransactionLogger.info(logPrefix + "waiting for processing transactions");
                                    exportFlags(conn, tables);
                                    exception = waitFlags(conn, tables, usedDeleteBarcodeList, deleteBarcodeKey, timeout);
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
            } catch (ClassNotFoundException e) {
                throw Throwables.propagate(e);
            }
        }
        return sendTransactionBatchMap;
    }

    private void exportGrp(Connection conn, TransactionCashRegisterInfo transaction) throws SQLException {
        String[] keys = new String[]{"GRPID"};
        String[] columns = new String[]{"GRPID", "PARENTGRPID", "GRPNAME", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, "GRP", columns, keys)) {
            int offset = columns.length + keys.length;

            for (int i = 0; i < transaction.itemsList.size(); i++) {
                List<ItemGroup> itemGroupList = transaction.itemGroupMap.get(transaction.itemsList.get(i).extIdItemGroup);
                if (itemGroupList != null) {
                    for (ItemGroup itemGroup : itemGroupList) {
                        if (!Thread.currentThread().isInterrupted()) {
                            String idGroup = parseGroup(itemGroup.extIdItemGroup);
                            if (idGroup != null) {
                                setObject(ps, idGroup, 1, offset); //GRPID
                                setObject(ps, parseGroup(itemGroup.idParentItemGroup), 2, offset); //PARENTGRPID
                                setObject(ps, trim(itemGroup.nameItemGroup, "", 50), 3, offset); //GRPNAME
                                setObject(ps, "0", 4, offset); //DELFLAG

                                setObject(ps, parseGroup(itemGroup.extIdItemGroup), 5); //GRPID

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

    private void exportArt(Connection conn, TransactionCashRegisterInfo transaction) throws SQLException {
        String[] keys = new String[]{"ARTID"};
        String[] columns = new String[]{"ARTID", "GRPID", "TAXGRPID", "ARTCODE", "ARTNAME", "ARTSNAME", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, "ART", columns, keys)) {
            int offset = columns.length + keys.length;

            for (int i = 0; i < transaction.itemsList.size(); i++) {
                if (!Thread.currentThread().isInterrupted()) {
                    CashRegisterItemInfo item = transaction.itemsList.get(i);
                    Integer idItem = parseIdItem(item);
                    setObject(ps, idItem, 1, offset); //ARTID
                    setObject(ps, parseGroup(item.extIdItemGroup), 2, offset); //GRPID
                    setObject(ps, getIdVAT(item.vat), 3, offset); //TAXGRPID
                    setObject(ps, idItem, 4, offset); //ARTCODE
                    setObject(ps, trim(item.name, "", 50), 5, offset); //ARTNAME
                    setObject(ps, trim(item.name, "", 50), 6, offset); //ARTSNAME
                    setObject(ps, "0", 7, offset); //DELFLAG

                    setObject(ps, idItem, 8); //ARTID

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

    private void exportArtExtgrp(Connection conn, TransactionCashRegisterInfo transaction, Integer extGrpId) throws SQLException {
        String[] keys = new String[]{"ARTID"};
        String[] columns = new String[]{"ARTID", "EXTGRPID", "DELFLAG "};
        try (PreparedStatement ps = getPreparedStatement(conn, "ARTEXTGRP", columns, keys)) {
            int offset = columns.length + keys.length;

            for (int i = 0; i < transaction.itemsList.size(); i++) {
                if (!Thread.currentThread().isInterrupted()) {
                    CashRegisterItemInfo item = transaction.itemsList.get(i);
                    Integer idItem = parseIdItem(item);
                    setObject(ps, idItem, 1, offset); //ARTID
                    setObject(ps, extGrpId, 2, offset); //EXTGRPID
                    setObject(ps, 0, 3, offset); //DELFLAG

                    setObject(ps, idItem, 4); //ARTID

                    ps.addBatch();
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }


    private void exportUnit(Connection conn, TransactionCashRegisterInfo transaction) throws SQLException {
        String[] keys = new String[]{"UNITID"};
        String[] columns = new String[]{"UNITID", "UNITNAME", "UNITFULLNAME", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, "UNIT", columns, keys)) {
            int offset = columns.length + keys.length;

            for (int i = 0; i < transaction.itemsList.size(); i++) {
                if (!Thread.currentThread().isInterrupted()) {
                    CashRegisterItemInfo item = transaction.itemsList.get(i);
                    Integer idUOM = parseUOM(item.idUOM);
                    setObject(ps, idUOM, 1, offset); //UNITID
                    setObject(ps, item.shortNameUOM, 2, offset); //UNITNAME
                    setObject(ps, item.shortNameUOM, 3, offset); //UNITFULLNAME
                    setObject(ps, "0", 4, offset); //DELFLAG

                    setObject(ps, idUOM, 5); //UNITID

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

    private void exportPack(Connection conn, TransactionCashRegisterInfo transaction) throws SQLException {
        String[] keys = new String[]{"PACKID"};
        String[] columns = new String[]{"PACKID", "ARTID", "PACKQUANT", "PACKSHELFLIFE", "ISDEFAULT", "UNITID", "QUANTMASK", "PACKDTYPE", "PACKNAME", "DELFLAG", "BARCID"};
        try (PreparedStatement ps = getPreparedStatement(conn, "PACK", columns, keys)) {
            int offset = columns.length + keys.length;

            Set<Integer> idItems = new HashSet<>();
            for (int i = 0; i < transaction.itemsList.size(); i++) {
                if (!Thread.currentThread().isInterrupted()) {
                    CashRegisterItemInfo item = transaction.itemsList.get(i);
                    Integer idUOM = parseUOM(item.idUOM);
                    Integer idItem = parseIdItem(item);
                    Integer packId = getPackId(item);
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

                    ps.addBatch();
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private void exportPackDeleteBarcode(Connection conn, List<CashRegisterItemInfo> usedDeleteBarcodeList) throws SQLException {
        String[] keys = new String[]{"PACKID"};
        String[] columns = new String[]{"PACKID", "ARTID", "PACKQUANT", "PACKSHELFLIFE", "ISDEFAULT", "UNITID", "QUANTMASK", "PACKDTYPE", "PACKNAME", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, "PACK", columns, keys)) {
            int offset = columns.length + keys.length;

            Set<Integer> idItems = new HashSet<>();
            for (CashRegisterItemInfo item : usedDeleteBarcodeList) {
                if (!Thread.currentThread().isInterrupted()) {
                    Integer idItem = parseIdItem(item);
                    Integer packId = getPackId(item);
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

    private void exportExBarc(Connection conn, TransactionCashRegisterInfo transaction) throws SQLException {
        String[] keys = new String[]{"EXBARCID"};
        String[] columns = new String[]{"EXBARCID", "PACKID", "EXBARCTYPE", "EXBARCBODY", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, "EXBARC", columns, keys)) {
            int offset = columns.length + keys.length;

            for (int i = 0; i < transaction.itemsList.size(); i++) {
                if (!Thread.currentThread().isInterrupted()) {
                    CashRegisterItemInfo item = transaction.itemsList.get(i);
                    if (item.idBarcode != null) {
                        Integer packId = getPackId(item);
                        setObject(ps, packId, 1, offset); //EXBARCID
                        setObject(ps, packId, 2, offset); //PACKID
                        setObject(ps, "", 3, offset); //EXBARCTYPE
                        setObject(ps, item.idBarcode, 4, offset); //EXBARCBODY
                        setObject(ps, "0", 5, offset); //DELFLAG

                        setObject(ps, packId, 6); //EXBARCID

                        ps.addBatch();
                    }
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private void exportExBarcDeleteBarcode(Connection conn, List<CashRegisterItemInfo> usedDeleteBarcodeList) throws SQLException {
        String[] keys = new String[]{"EXBARCID"};
        String[] columns = new String[]{"EXBARCID", "PACKID", "EXBARCTYPE", "EXBARCBODY", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, "EXBARC", columns, keys)) {
            int offset = columns.length + keys.length;

            for (CashRegisterItemInfo item : usedDeleteBarcodeList) {
                if (!Thread.currentThread().isInterrupted()) {
                    if (item.idBarcode != null) {
                        Integer packId = getPackId(item);
                        setObject(ps, packId, 1, offset); //EXBARCID
                        setObject(ps, packId, 2, offset); //PACKID
                        setObject(ps, "", 3, offset); //EXBARCTYPE
                        setObject(ps, item.idBarcode, 4, offset); //EXBARCBODY
                        setObject(ps, "1", 5, offset); //DELFLAG

                        setObject(ps, packId, 6); //EXBARCID

                        ps.addBatch();
                    }
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private void exportPackPrc(Connection conn, TransactionCashRegisterInfo transaction) throws SQLException {
        String[] keys = new String[]{"PACKID", "PRCLEVELID"};
        String[] columns = new String[]{"PACKID", "PRCLEVELID", "PACKPRICE", "PACKMINPRICE", "PACKBONUSMINPRICE", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, "PACKPRC", columns, keys)) {
            int offset = columns.length + keys.length;

            for (int i = 0; i < transaction.itemsList.size(); i++) {
                if (!Thread.currentThread().isInterrupted()) {
                    CashRegisterItemInfo item = transaction.itemsList.get(i);
                    if (item.price != null) {
                        Integer packId = getPackId(item);
                        setObject(ps, packId, 1, offset); //PACKID
                        setObject(ps, transaction.nppGroupMachinery, 2, offset); //PRCLEVELID
                        BigDecimal packPrice = item.price.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : HandlerUtils.safeMultiply(item.price, 100);
                        setObject(ps, packPrice, 3, offset); //PACKPRICE
                        setObject(ps, (item.flags == null || ((item.flags & 16) == 0)) && HandlerUtils.safeMultiply(item.price, 100) != null ? HandlerUtils.safeMultiply(item.price, 100) : HandlerUtils.safeMultiply(item.minPrice, 100) != null ? HandlerUtils.safeMultiply(item.minPrice, 100) : BigDecimal.ZERO, 4, offset); //PACKMINPRICE
                        setObject(ps, 0, 5, offset); //PACKBONUSMINPRICE
                        setObject(ps, "0", 6, offset); //DELFLAG

                        setObject(ps, packId, 7); //PACKID
                        setObject(ps, transaction.nppGroupMachinery, 8); //PRCLEVELID

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

    private void exportPackPrcDeleteBarcode(Connection conn, TransactionCashRegisterInfo transaction, List<CashRegisterItemInfo> usedDeleteBarcodeList) throws SQLException {
        String[] keys = new String[]{"PACKID", "PRCLEVELID"};
        String[] columns = new String[]{"PACKID", "PRCLEVELID", "PACKPRICE", "PACKMINPRICE", "PACKBONUSMINPRICE", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, "PACKPRC", columns, keys)) {
            int offset = columns.length + keys.length;

            for (CashRegisterItemInfo item : usedDeleteBarcodeList) {
                if (!Thread.currentThread().isInterrupted()) {
                    Integer packId = getPackId(item);
                    setObject(ps, packId, 1, offset); //PACKID
                    setObject(ps, transaction.nppGroupMachinery, 2, offset); //PRCLEVELID
                    setObject(ps, BigDecimal.ZERO, 3, offset); //PACKPRICE
                    setObject(ps, BigDecimal.ZERO, 4, offset); //PACKMINPRICE
                    setObject(ps, 0, 5, offset); //PACKBONUSMINPRICE
                    setObject(ps, "0", 6, offset); //DELFLAG

                    setObject(ps, packId, 7); //PACKID
                    setObject(ps, transaction.nppGroupMachinery, 8); //PRCLEVELID

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

    private void exportFlags(Connection conn, String tables) throws SQLException {
        conn.setAutoCommit(true);
        try (Statement statement = conn.createStatement()) {
            String sql = "UPDATE [DATAPUMP] SET recordnum = 1 WHERE dirname in (" + tables + ")";
            statement.executeUpdate(sql);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private Throwable waitFlags(Connection conn, String tables, List<CashRegisterItemInfo> usedDeleteBarcodeList, String deleteBarcodeKey, int timeout) throws InterruptedException {
        int count = 0;
        int flags;
        while ((flags = checkFlags(conn, tables)) != 0) {
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
        Map<String, CashRegisterItemInfo> deleteBarcodeMap = deleteBarcodeConnectionStringMap.get(deleteBarcodeKey);
        for(CashRegisterItemInfo usedDeleteBarcode : usedDeleteBarcodeList) {
            deleteBarcodeMap.remove(usedDeleteBarcode.idBarcode);
        }
        deleteBarcodeConnectionStringMap.put(deleteBarcodeKey, deleteBarcodeMap);
        return null;
    }

    private int checkFlags(Connection conn, String tables) {
        try (Statement statement = conn.createStatement()) {
            String sql = "SELECT COUNT(*) FROM [DATAPUMP] WHERE dirname in (" + tables + ") AND recordnum = 1";
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

    private PreparedStatement getPreparedStatement(Connection conn, String table, String[] columnNames, String[] keyNames) throws SQLException {
        String set = "";
        String columns = "";
        String params = "";
        for (String columnName : columnNames) {
            columns = concat(columns, columnName, ",");
            set = concat(set, columnName + "=?", ",");
            params = concat(params, "?", ",");
        }
        String wheres = "";
        for (String keyName : keyNames) {
            wheres = concat(wheres, keyName + "=?", " AND ");
        }
        return conn.prepareStatement(String.format("UPDATE [%s] SET %s WHERE %s IF @@ROWCOUNT=0 INSERT INTO %s(%s) VALUES (%s)", table, set, wheres, table, columns, params));
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) {
        for (String directory : directorySet) {
            AstronConnectionString params = new AstronConnectionString(directory);
            if (params.connectionString != null) {
                try (Connection conn = getConnection(params.connectionString, params.user, params.password); PreparedStatement ps = conn.prepareStatement(String.format("UPDATE [PACKPRC] SET DELFLAG = %s WHERE PACKID=? AND PRCLEVELID=?", stopListInfo.exclude ? "0" : "1"))) {

                    processStopListLogger.info(logPrefix + "executing stopLists, table packprc");
                    for (ItemInfo item : stopListInfo.stopListItemMap.values()) {
                        for (Integer nppGroupMachinery : stopListInfo.inGroupMachineryItemMap.keySet()) {
                            if (item instanceof CashRegisterItemInfo) {
                                ps.setObject(1, getPackId((CashRegisterItemInfo) item)); //PACKID
                                ps.setObject(2, nppGroupMachinery); //PRCLEVELID
                                ps.addBatch();
                            }
                        }
                    }
                    ps.executeBatch();
                    conn.commit();

                    processTransactionLogger.info(logPrefix + "waiting for processing stopLists");
                    exportFlags(conn, "'ART'");

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
                if (!deleteBarcodeMap.containsKey(item.idBarcode)) {
                    deleteBarcodeMap.put(item.idBarcode, item);
                }
            }
            if (!deleteBarcodeMap.isEmpty())
                deleteBarcodeConnectionStringMap.put(key, deleteBarcodeMap);
            return false;
        }
        return true;
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
                try (Connection conn = getConnection(params.connectionString, params.user, params.password)) {
                    salesBatch = readSalesInfoFromSQL(conn, machineryMap, directory);
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return salesBatch;
    }

    private AstronSalesBatch readSalesInfoFromSQL(Connection conn, Map<Integer, CashRegisterInfo> machineryMap, String directory) {

        List<SalesInfo> salesInfoList = new ArrayList<>();
        List<AstronRecord> recordList = new ArrayList<>();

        createExtraColumns(conn);
        createFusionProcessedIndex(conn);
        createSalesIndex(conn);

        try (Statement statement = conn.createStatement()) {
            String query = "SELECT sales.SALESATTRS, sales.SYSTEMID, sales.SESSID, sales.SALESTIME, sales.FRECNUM, sales.CASHIERID, sales.SALESTAG, sales.SALESBARC, " +
                    "sales.SALESCODE, sales.SALESCOUNT, sales.SALESPRICE, sales.SALESSUM, sales.SALESDISC, sales.SALESTYPE, sales.SALESNUM, sales.SAREAID, " +
                    "sales.SALESREFUND, COALESCE(sess.SESSSTART,sales.SALESTIME) AS SESSSTART " +
                    "FROM SALES sales LEFT JOIN (SELECT SESSID, SYSTEMID, SAREAID, max(SESSSTART) AS SESSSTART FROM SESS GROUP BY SESSID, SYSTEMID, SAREAID) sess " +
                    "ON sales.SESSID=sess.SESSID AND sales.SYSTEMID=sess.SYSTEMID AND sales.SAREAID=sess.SAREAID AND NOT (sales.SYSTEMID = 301 AND sales.SESSID < 3) " + // временная доп проверка
                    "WHERE (FUSION_PROCESSED IS NULL OR FUSION_PROCESSED = 0) AND SALESCANC = 0 ORDER BY SAREAID, SYSTEMID, SALESTIME, SALESNUM";
            ResultSet rs = statement.executeQuery(query);

            List<SalesInfo> curSalesInfoList = new ArrayList<>();
            List<AstronRecord> curRecordList = new ArrayList<>();
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

                Integer salesNum = rs.getInt("SALESNUM");

                Integer sessionId = rs.getInt("SESSID");
                String numberZReport = String.valueOf(sessionId);

                long sessStart = DateUtils.parseDate(rs.getString("SESSSTART"), "yyyyMMddHHmmss").getTime();
                Date dateZReport = new Date(sessStart);
                Time timeZReport = new Time(sessStart);

                long salesTime = DateUtils.parseDate(rs.getString("SALESTIME"), "yyyyMMddHHmmss").getTime();
                Date dateReceipt = new Date(salesTime);
                Time timeReceipt = new Time(salesTime);

                Integer numberReceipt = rs.getInt("FRECNUM");
                //пока импорт кассиров отключён, поскольку нет их имён. Имена можно взять в таблице CASHIER, если её нам начнут выгружать
                String idEmployee = null;//String.valueOf(rs.getInt("CASHIERID"));
                Integer type = rs.getInt("SALESTYPE");
                boolean isWeight = type == 0 || type == 2;

                Integer recordType = rs.getInt("SALESTAG");
                boolean isReturn = rs.getInt("SALESREFUND") != 0; // 0 - продажа, 1 - возврат, 2 - аннулирование

                switch (recordType) {
                    case 0: {//товарная позиция
                        //временный лог для того, чтобы выявить, откуда попадают лишние оплаты в чек
                        sendSalesLogger.info(String.format("sale: SAREAID %s, SYSTEMID %s, dateReceipt %s, timeReceipt %s, SALESNUM %s, SESSIONID %s, FRECNUM %s",
                                rs.getInt("SAREAID"), nppCashRegister, dateReceipt, timeReceipt, salesNum, sessionId, numberReceipt));
                        String idBarcode = rs.getString("SALESBARC");
                        String idItem = String.valueOf(rs.getInt("SALESCODE"));
                        BigDecimal totalQuantity = safeDivide(rs.getBigDecimal("SALESCOUNT"), isWeight ? 1000 : 1);
                        BigDecimal price = safeDivide(rs.getBigDecimal("SALESPRICE"), 100);
                        BigDecimal sumReceiptDetail = safeDivide(rs.getBigDecimal("SALESSUM"), 100);
                        BigDecimal discountSumReceiptDetail = safeDivide(rs.getBigDecimal("SALESDISC"), 100);
                        totalQuantity = isReturn ? totalQuantity.negate() : totalQuantity;
                        sumReceiptDetail = isReturn ? sumReceiptDetail.negate() : sumReceiptDetail;
                        curSalesInfoList.add(new SalesInfo(false, nppGroupMachinery, nppCashRegister, numberZReport, dateZReport, timeZReport,
                                numberReceipt, dateReceipt, timeReceipt, idEmployee, null, null, sumCard, sumCash, sumGiftCard, idBarcode, idItem,
                                null, idSaleReceiptReceiptReturnDetail, totalQuantity, price, sumReceiptDetail, discountSumReceiptDetail, null, null,
                                salesNum, null, null, cashRegister));
                        curRecordList.add(new AstronRecord(salesNum, sessionId, nppCashRegister, sAreaId));
                        prologSum = safeSubtract(prologSum, rs.getBigDecimal("SALESSUM"));
                        break;
                    }
                    case 1: {//оплата
                        //временный лог для того, чтобы выявить, откуда попадают лишние оплаты в чек
                        sendSalesLogger.info(String.format("payment: SAREAID %s, SYSTEMID %s, dateReceipt %s, timeReceipt %s, SALESNUM %s, SESSIONID %s, FRECNUM %s",
                                rs.getInt("SAREAID"), nppCashRegister, dateReceipt, timeReceipt, salesNum, sessionId, numberReceipt));
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
                        sendSalesLogger.info(String.format("prolog: SAREAID %s, SYSTEMID %s, dateReceipt %s, timeReceipt %s, SALESNUM %s, SESSIONID %s, FRECNUM %s",
                                rs.getInt("SAREAID"), nppCashRegister, dateReceipt, timeReceipt, salesNum, sessionId, numberReceipt));
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
                    try (Connection conn = getConnection(params.connectionString, params.user, params.password)) {

                        createExtraColumns(conn);
                        createFusionProcessedIndex(conn);

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
                                String query = "UPDATE [SALES] SET FUSION_PROCESSED = 0 WHERE (" + dateWhere + ")" + (stockWhere.length() > 0 ? (" AND (" + stockWhere + ")") : "");
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

                try (Connection conn = getConnection(params.connectionString, params.user, params.password); PreparedStatement ps = conn.prepareStatement("UPDATE [SALES] SET FUSION_PROCESSED = 1 WHERE SALESNUM = ? AND SESSID = ? AND SYSTEMID = ? AND SAREAID = ?")) {
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

    private void createExtraColumns(Connection conn) {
        try (Statement statement = conn.createStatement()) {
            String query = "IF COL_LENGTH('SALES', 'FUSION_PROCESSED') IS NULL BEGIN ALTER TABLE SALES ADD FUSION_PROCESSED INT NULL; END";
            statement.execute(query);
            conn.commit();
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    private void createFusionProcessedIndex(Connection conn) {
        try (Statement statement = conn.createStatement()) {
            String query = "IF NOT EXISTS (SELECT 1 WHERE IndexProperty(Object_Id('SALES'), 'fusion', 'IndexId') > 0) BEGIN CREATE INDEX fusion ON SALES (FUSION_PROCESSED) END";
            statement.execute(query);
            conn.commit();
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }


    private void createSalesIndex(Connection conn) {
        try (Statement statement = conn.createStatement()) {
            String query = "IF NOT EXISTS (SELECT 1 WHERE IndexProperty(Object_Id('SALES'), 'sale', 'IndexId') > 0) BEGIN CREATE INDEX sale ON SALES (SALESNUM, SESSID, SYSTEMID, SAREAID) END";
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

    private Connection getConnection(String connectionString, String user, String password) throws SQLException, ClassNotFoundException {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        //https://github.com/Microsoft/mssql-jdbc/wiki/QueryTimeout
        Connection conn = DriverManager.getConnection(connectionString + ";queryTimeout=5", user, password);
        conn.setAutoCommit(false);
        return conn;
    }

    private String parseGroup(String idItemGroup) {
        try {
            return idItemGroup == null || idItemGroup.equals("Все") ? null : idItemGroup.replaceAll("[^0-9]", "");
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
}