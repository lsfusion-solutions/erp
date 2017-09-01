package equ.clt.handler.astron;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
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

import static equ.clt.handler.HandlerUtils.trim;

public class AstronHandler extends DefaultCashRegisterHandler<AstronSalesBatch> {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger processStopListLogger = Logger.getLogger("StopListLogger");
    protected final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");

    private FileSystemXmlApplicationContext springContext;

    public AstronHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        return "astron";
    }

    @Override
    public Map<Integer, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) throws IOException {

        Map<Integer, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        if (transactionList != null) {

            try {

                Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

                AstronSettings astronSettings = springContext.containsBean("astronSettings") ? (AstronSettings) springContext.getBean("astronSettings") : null;
                String connectionString = astronSettings == null ? null : astronSettings.getConnectionString();
                String user = astronSettings == null ? null : astronSettings.getUser();
                String password = astronSettings == null ? null : astronSettings.getPassword();
                Integer timeout = astronSettings == null ? null : astronSettings.getTimeout();
                timeout = timeout == null ? 300 : timeout;

                if (connectionString == null) {
                    processTransactionLogger.error("No connectionString in astronSettings found");
                } else {
                    Connection conn = null;
                    try {
                        conn = getConnection(connectionString, user, password);

                        int flags = checkFlags(conn);
                        if (flags > 0) {
                            for (TransactionCashRegisterInfo transaction : transactionList) {
                                sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(
                                        new RuntimeException(String.format("data from previous transactions was not processed (%s flags not set to zero)", flags))));
                            }
                        } else {
                            for (TransactionCashRegisterInfo transaction : transactionList) {
                                Exception exception = null;
                                try {
                                    if (transaction.itemsList != null) {
                                        processTransactionLogger.info(String.format("astron: transaction %s, table grp", transaction.id));
                                        exportGrp(conn, transaction);

                                        processTransactionLogger.info(String.format("astron: transaction %s, table art", transaction.id));
                                        exportArt(conn, transaction);

                                        processTransactionLogger.info(String.format("astron: transaction %s, table unit", transaction.id));
                                        exportUnit(conn, transaction);

                                        processTransactionLogger.info(String.format("astron: transaction %s, table pack", transaction.id));
                                        exportPack(conn, transaction);

                                        processTransactionLogger.info(String.format("astron: transaction %s, table exbarc", transaction.id));
                                        exportExBarc(conn, transaction);

                                        processTransactionLogger.info(String.format("astron: transaction %s, table packprc", transaction.id));
                                        exportPackPrc(conn, transaction);
                                    }
                                } catch (Exception e) {
                                    exception = e;
                                }
                                sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(exception));
                            }
                            processTransactionLogger.info("astron: waiting for processing transactions");
                            exportFlags(conn, "'GRP', 'ART', 'UNIT', 'PACK', 'EXBARC', 'PACKPRC'");
                            Throwable waitResult = waitFlags(conn, timeout);
                            if (waitResult != null) {
                                for (Integer id : sendTransactionBatchMap.keySet()) {
                                    sendTransactionBatchMap.put(id, new SendTransactionBatch(waitResult));
                                }
                            }
                        }

                    } finally {
                        closeConnection(conn);
                    }
                }
            } catch (ClassNotFoundException | SQLException | InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }
        return sendTransactionBatchMap;
    }

    private void exportGrp(Connection conn, TransactionCashRegisterInfo transaction) throws SQLException {
        PreparedStatement ps = null;
        try {
            String[] columns = new String[]{"GRPID", "PARENTGRPID", "GRPNAME", "DELFLAG", "UPDATENUM"};
            String[] keys = new String[]{"GRPID"};
            int offset = columns.length + keys.length;
            ps = getPreparedStatement(conn, "GRP", columns, keys);

            for (int i = 0; i < transaction.itemsList.size(); i++) {
                List<ItemGroup> itemGroupList = transaction.itemGroupMap.get(transaction.itemsList.get(i).extIdItemGroup);
                if (itemGroupList != null) {
                    for (ItemGroup itemGroup : itemGroupList) {
                        String idGroup = parseGroup(itemGroup.extIdItemGroup);
                        if (idGroup != null) {
                            setObject(ps, idGroup, 1, offset); //GRPID
                            setObject(ps, parseGroup(itemGroup.idParentItemGroup), 2, offset); //PARENTGRPID
                            setObject(ps, trim(itemGroup.nameItemGroup, "", 50), 3, offset); //GRPNAME
                            setObject(ps, "0", 4, offset); //DELFLAG
                            setObject(ps, "1", 5, offset); //UPDATENUM

                            setObject(ps, parseGroup(itemGroup.extIdItemGroup), 6); //GRPID

                            ps.addBatch();
                        }
                    }
                }
            }
            ps.executeBatch();
            conn.commit();
        } finally {
            closeStatement(ps);
        }
    }

    private void exportArt(Connection conn, TransactionCashRegisterInfo transaction) throws SQLException {
        PreparedStatement ps = null;
        try {
            String[] columns = new String[]{"ARTID", "GRPID", "TAXGRPID", "ARTCODE", "ARTNAME", "ARTSNAME", "DELFLAG", "UPDATENUM"};
            String[] keys = new String[]{"ARTID"};
            int offset = columns.length + keys.length;
            ps = getPreparedStatement(conn, "ART", columns, keys);

            for (int i = 0; i < transaction.itemsList.size(); i++) {
                CashRegisterItemInfo item = transaction.itemsList.get(i);
                setObject(ps, item.idItem, 1, offset); //ARTID
                setObject(ps, parseGroup(item.extIdItemGroup), 2, offset); //GRPID
                setObject(ps, item.vat == null ? "0" : item.vat, 3, offset); //TAXGRPID
                setObject(ps, item.idItem, 4, offset); //ARTCODE
                setObject(ps, trim(item.name, "", 50), 5, offset); //ARTNAME
                setObject(ps, trim(item.name, "", 50), 6, offset); //ARTSNAME
                setObject(ps, "0", 7, offset); //DELFLAG
                setObject(ps, "1", 8, offset); //UPDATENUM

                setObject(ps, item.idItem, 9); //ARTID

                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } finally {
            closeStatement(ps);
        }
    }

    private void exportUnit(Connection conn, TransactionCashRegisterInfo transaction) throws SQLException {
        PreparedStatement ps = null;
        try {
            String[] columns = new String[]{"UNITID", "UNITNAME", "UNITFULLNAME", "DELFLAG", "UPDATENUM"};
            String[] keys = new String[]{"UNITID"};
            int offset = columns.length + keys.length;
            ps = getPreparedStatement(conn, "UNIT", columns, keys);

            for (int i = 0; i < transaction.itemsList.size(); i++) {
                CashRegisterItemInfo item = transaction.itemsList.get(i);
                Integer idUOM = parseUOM(item.idUOM);
                if (idUOM != null) {
                    setObject(ps, idUOM, 1, offset); //UNITID
                    setObject(ps, item.shortNameUOM, 2, offset); //UNITNAME
                    setObject(ps, item.shortNameUOM, 3, offset); //UNITFULLNAME
                    setObject(ps, "0", 4, offset); //DELFLAG
                    setObject(ps, "1", 5, offset); //UPDATENUM

                    setObject(ps, idUOM, 6); //UNITID

                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            }
        } finally {
            closeStatement(ps);
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
        PreparedStatement ps = null;
        try {
            String[] columns = new String[]{"PACKID", "ARTID", "PACKQUANT", "PACKSHELFLIFE", "ISDEFAULT", "UNITID",
                    "QUANTMASK", "PACKDTYPE", "PACKNAME", "DELFLAG", "UPDATENUM"};
            String[] keys = new String[]{"PACKID"};
            int offset = columns.length + keys.length;
            ps = getPreparedStatement(conn, "PACK", columns, keys);

            for (int i = 0; i < transaction.itemsList.size(); i++) {
                CashRegisterItemInfo item = transaction.itemsList.get(i);
                Integer idUOM = parseUOM(item.idUOM);
                if (idUOM != null) {
                    setObject(ps, item.idItem, 1, offset); //PACKID
                    setObject(ps, item.idItem, 2, offset); //ARTID
                    setObject(ps, "1", 3, offset); //PACKQUANT
                    setObject(ps, "0", 4, offset); //PACKSHELFLIFE
                    setObject(ps, true, 5, offset); //ISDEFAULT
                    setObject(ps, idUOM, 6, offset); //UNITID
                    setObject(ps, "", 7, offset); //QUANTMASK
                    setObject(ps, item.passScalesItem ? 0 : 1, 8, offset); //PACKDTYPE
                    setObject(ps, trim(item.name, "", 50), 9, offset); //PACKNAME
                    setObject(ps, "0", 10, offset); //DELFLAG
                    setObject(ps, "1", 11, offset); //UPDATENUM

                    setObject(ps, item.idItem, 12); //PACKID

                    ps.addBatch();
                }
            }
            ps.executeBatch();
            conn.commit();
        } finally {
            closeStatement(ps);
        }
    }

    private void exportExBarc(Connection conn, TransactionCashRegisterInfo transaction) throws SQLException {
        PreparedStatement ps = null;
        try {
            String[] columns = new String[]{"EXBARCID", "PACKID", "EXBARCTYPE", "EXBARCBODY", "DELFLAG", "UPDATENUM"};
            String[] keys = new String[]{"EXBARCID"};
            int offset = columns.length + keys.length;
            ps = getPreparedStatement(conn, "EXBARC", columns, keys);

            for (int i = 0; i < transaction.itemsList.size(); i++) {
                CashRegisterItemInfo item = transaction.itemsList.get(i);
                setObject(ps, item.idItem, 1, offset); //EXBARCID
                setObject(ps, item.idItem, 2, offset); //PACKID
                setObject(ps, "", 3, offset); //EXBARCTYPE
                setObject(ps, item.idBarcode, 4, offset); //EXBARCBODY
                setObject(ps, "0", 5, offset); //DELFLAG
                setObject(ps, "1", 6, offset); //UPDATENUM

                setObject(ps, item.idItem, 7); //EXBARCID

                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } finally {
            closeStatement(ps);
        }
    }

    private void exportPackPrc(Connection conn, TransactionCashRegisterInfo transaction) throws SQLException {
        PreparedStatement ps = null;
        try {
            String[] columns = new String[]{"PACKID", "PRCLEVELID", "PACKPRICE", "PACKMINPRICE", "PACKBONUSMINPRICE", "DELFLAG", "UPDATENUM"};
            String[] keys = new String[]{"PACKID", "PRCLEVELID"};
            int offset = columns.length + keys.length;
            ps = getPreparedStatement(conn, "PACKPRC", columns, keys);

            for (int i = 0; i < transaction.itemsList.size(); i++) {
                CashRegisterItemInfo item = transaction.itemsList.get(i);
                setObject(ps, item.idItem, 1, offset); //PACKID
                setObject(ps, transaction.nppGroupMachinery, 2, offset); //PRCLEVELID
                setObject(ps, HandlerUtils.safeMultiply(item.price, 100), 3, offset); //PACKPRICE
                setObject(ps, item.minPrice, 4, offset); //PACKMINPRICE
                setObject(ps, 0, 5, offset); //PACKBONUSMINPRICE
                setObject(ps, "0", 6, offset); //DELFLAG
                setObject(ps, "1", 7, offset); //UPDATENUM

                setObject(ps, item.idItem, 8); //PACKID
                setObject(ps, transaction.nppGroupMachinery, 9); //PRCLEVELID

                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } finally {
            closeStatement(ps);
        }
    }

    private void exportFlags(Connection conn, String tables) throws SQLException {
        conn.setAutoCommit(true);
        Statement statement = null;
        try {
            statement = conn.createStatement();
            String sql = "UPDATE [DATAPUMP] SET recordnum = 1 WHERE dirname in (" + tables + ")";
            statement.executeUpdate(sql);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            if (statement != null)
                statement.close();
        }
    }

    private Throwable waitFlags(Connection conn, int timeout) throws SQLException, InterruptedException {
        int count = 0;
        int flags;
        while ((flags = checkFlags(conn)) != 0) {
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
        return null;
    }

    private int checkFlags(Connection conn) throws SQLException {
        Statement statement = null;
        try {
            statement = conn.createStatement();
            String sql = "SELECT COUNT(*) FROM [DATAPUMP] WHERE dirname in ('GRP', 'ART', 'UNIT', 'PACK', 'EXBARC', 'PACKPRC') AND recordnum = 1";
            ResultSet resultSet = statement.executeQuery(sql);
            return resultSet.next() ? resultSet.getInt(1) : 0;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            if (statement != null)
                statement.close();
        }
    }

    private void setObject(PreparedStatement ps, Object value, int index, int columnsSize) throws SQLException {
        setObject(ps, value, index);
        setObject(ps, value, index + columnsSize);
    }

    private void setObject(PreparedStatement ps, Object value, int index) throws SQLException {
        if (value instanceof Date)
            ps.setDate(index, (Date) value);
        else if (value instanceof Timestamp)
            ps.setTimestamp(index, ((Timestamp) value));
        else if (value instanceof String)
            ps.setString(index, ((String) value).trim());
        else
            ps.setObject(index, value);
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
        return conn.prepareStatement(String.format("UPDATE [%s] SET %s WHERE %s IF @@ROWCOUNT=0 INSERT INTO %s(%s) VALUES (%s)",
                table, set, wheres, table, columns, params));
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) throws IOException {
        AstronSettings astronSettings = springContext.containsBean("astronSettings") ? (AstronSettings) springContext.getBean("astronSettings") : null;
        String connectionString = astronSettings == null ? null : astronSettings.getConnectionString();
        String user = astronSettings == null ? null : astronSettings.getUser();
        String password = astronSettings == null ? null : astronSettings.getPassword();
        if (connectionString != null) {
            Connection conn = null;
            PreparedStatement ps = null;
            try {
                conn = getConnection(connectionString, user, password);

                processStopListLogger.info("astron: executing stopLists, table packprc");
                ps = conn.prepareStatement(String.format("UPDATE [PACKPRC] SET DELFLAG = %s WHERE PACKID=? AND PRCLEVELID=?", stopListInfo.exclude ? "0" : "1"));
                for (ItemInfo item : stopListInfo.stopListItemMap.values()) {
                    for (Integer nppGroupMachinery : stopListInfo.inGroupMachineryItemMap.keySet()) {
                        ps.setObject(1, item.idItem); //PACKID
                        ps.setObject(2, nppGroupMachinery); //PRCLEVELID
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
                conn.commit();

                processTransactionLogger.info("astron: waiting for processing stopLists");
                exportFlags(conn, "'ART'");

            } catch (Exception e) {
                processStopListLogger.error("astron:", e);
                e.printStackTrace();
            } finally {
                try {
                    closeStatement(ps);
                    closeConnection(conn);
                } catch (SQLException e) {
                    processStopListLogger.error("astron:", e);
                }
            }
        }
    }

    @Override
    public SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException {

        AstronSalesBatch salesBatch = null;

        Map<Integer, CashRegisterInfo> machineryMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (c.handlerModel != null && c.handlerModel.endsWith("AstronHandler")) {
                if (c.number != null && c.numberGroup != null)
                    machineryMap.put(c.number, c);
            }
        }

        try {

            AstronSettings astronSettings = springContext.containsBean("astronSettings") ? (AstronSettings) springContext.getBean("astronSettings") : null;
            String connectionString = astronSettings == null ? null : astronSettings.getConnectionString(); //"jdbc:mysql://172.16.0.35/export_axapta"
            String user = astronSettings == null ? null : astronSettings.getUser(); //luxsoft
            String password = astronSettings == null ? null : astronSettings.getPassword(); //123456

            if (connectionString == null) {
                processTransactionLogger.error("No exportConnectionString in astronSettings found");
            } else {
                Connection conn = null;
                try {
                    conn = getConnection(connectionString, user, password);
                    salesBatch = readSalesInfoFromSQL(conn, machineryMap);
                } finally {
                    closeConnection(conn);
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return salesBatch;
    }

    private AstronSalesBatch readSalesInfoFromSQL(Connection conn, Map<Integer, CashRegisterInfo> machineryMap) throws SQLException {

        List<SalesInfo> salesInfoList = new ArrayList<>();
        List<AstronRecord> recordList = new ArrayList<>();

        Statement statement = null;
        try {
            statement = conn.createStatement();
            String query = "SELECT SALESCANC, SALESREFUND, SYSTEMID, SESSID, SALESTIME, FRECNUM, CASHIERID, SALESTAG, SALESBARC, SALESCODE, " +
                    "SALESCOUNT, SALESPRICE, SALESSUM, SALESDISC, SALESTYPE, SALESNUM, SAREAID FROM [SALES]  WHERE FUSION_PROCESSED IS NULL OR FUSION_PROCESSED = 0";
            ResultSet rs = statement.executeQuery(query);

            List<SalesInfo> curSalesInfoList = new ArrayList<>();

            BigDecimal sumCash = null;
            BigDecimal sumCard = null;
            BigDecimal sumGiftCard = null;
            while (rs.next()) {

                boolean cancelled = rs.getInt(1) == 1; //SALESCANC
                boolean repealed = rs.getInt(2) == 2; //SALESREFUND
                if (!cancelled && !repealed) {

                    Integer nppCashRegister = rs.getInt(3); //SYSTEMID
                    CashRegisterInfo cashRegister = machineryMap.get(nppCashRegister);
                    Integer nppGroupMachinery = cashRegister == null ? null : cashRegister.numberGroup;

                    Integer sessionId = rs.getInt(4); //SESSID
                    String numberZReport = String.valueOf(sessionId);
                    long salesTime = DateUtils.parseDate(rs.getString(5), "yyyyMMddHHmmss").getTime(); //SALESTIME
                    Date dateReceipt = new Date(salesTime);
                    Time timeReceipt = new Time(salesTime);

                    Integer numberReceipt = rs.getInt(6); //FRECNUM
                    String idEmployee = String.valueOf(rs.getInt(7)); //CASHIERID

                    Integer recordType = rs.getInt(8); //SALESTAG
                    boolean isSale = recordType == 0;

                    switch (recordType) {
                        case 0: //товарная позиция
                        case 3: //Возвращенная товарная позиция
                            String idBarcode = rs.getString(9); //SALESBARC
                            String idItem = String.valueOf(rs.getInt(10)); //SALESCODE
                            boolean isWeight = rs.getInt(15) == 0; //SALESTYPE
                            BigDecimal totalQuantity = HandlerUtils.safeDivide(rs.getBigDecimal(11), isWeight ? 1000 : 1); //SALESCOUNT
                            BigDecimal price = HandlerUtils.safeDivide(rs.getBigDecimal(12), 100); //SALESPRICE
                            BigDecimal sumReceiptDetail = HandlerUtils.safeDivide(rs.getBigDecimal(13), 100); //SALESSUM
                            BigDecimal discountSumReceiptDetail = HandlerUtils.safeDivide(rs.getBigDecimal(14), 100); //SALESDISC
                            curSalesInfoList.add(new SalesInfo(false, nppGroupMachinery, nppCashRegister, numberZReport,
                                    dateReceipt, timeReceipt, numberReceipt, dateReceipt, timeReceipt, idEmployee, null,
                                    null, sumCard, sumCash, sumGiftCard, idBarcode, idItem, null, null, totalQuantity,
                                    price, isSale ? sumReceiptDetail : sumReceiptDetail.negate(), discountSumReceiptDetail,
                                    null, null, curSalesInfoList.size() + 1, null, null));
                            break;
                        case 1: //оплата
                            BigDecimal sum = HandlerUtils.safeDivide(rs.getBigDecimal(13), 100); //SALESSUM
                            Integer type = rs.getInt(15); //SALESTYPE
                            switch (type) {
                                case 1:
                                    sumCard = HandlerUtils.safeAdd(sumCard, sum);
                                    break;
                                case 2:
                                    sumGiftCard = HandlerUtils.safeAdd(sumGiftCard, sum);
                                    break;
                                case 0:
                                default:
                                    sumCash = HandlerUtils.safeAdd(sumCash, sum);
                                    break;
                            }

                            break;
                        case 2: //пролог чека
                            sumCash = null;
                            sumCard = null;
                            sumGiftCard = null;
                            salesInfoList.addAll(curSalesInfoList);
                            curSalesInfoList = new ArrayList<>();
                            break;
                    }

                    Integer salesNum = rs.getInt(16); //SALESNUM
                    Integer sAreaId = rs.getInt(17); //SAREAID
                    recordList.add(new AstronRecord(salesNum, sessionId, nppCashRegister, sAreaId));
                }
            }

            salesInfoList.addAll(curSalesInfoList);

            if (salesInfoList.size() > 0)
                sendSalesLogger.info(String.format("Astron: found %s records", salesInfoList.size()));
        } catch (SQLException | ParseException e) {
            throw Throwables.propagate(e);
        } finally {
            if (statement != null)
                statement.close();
        }
        return new AstronSalesBatch(salesInfoList, recordList);
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList, Set<String> directorySet,
                                 Set<Integer> succeededRequests, Map<Integer, Throwable> failedRequests, Map<Integer, Throwable> ignoredRequests) throws IOException, ParseException {
        AstronSettings astronSettings = springContext.containsBean("astronSettings") ? (AstronSettings) springContext.getBean("astronSettings") : null;
        String connectionString = astronSettings == null ? null : astronSettings.getConnectionString(); //"jdbc:mysql://172.16.0.35/export_axapta"
        String user = astronSettings == null ? null : astronSettings.getUser(); //luxsoft
        String password = astronSettings == null ? null : astronSettings.getPassword(); //123456

        if (connectionString != null) {

            try {

                Connection conn = null;
                try {
                    conn = getConnection(connectionString, user, password);

                    for (RequestExchange entry : requestExchangeList) {
                        Statement statement = null;
                        try {
                            StringBuilder where = new StringBuilder();
                            Long dateFrom = entry.dateFrom.getTime();
                            Long dateTo = entry.dateTo.getTime();
                            while (dateFrom <= dateTo) {
                                String dateString = new SimpleDateFormat("yyyyMMdd").format(new Date(dateFrom));
                                where.append((where.length() == 0) ? "" : " OR ").append("SALESTIME LIKE '").append(dateString).append("%'");
                                dateFrom += 86400000;
                            }
                            if (where.length() > 0) {
                                statement = conn.createStatement();
                                String query = "UPDATE [SALES] SET FUSION_PROCESSED = 0 WHERE " + where;
                                sendSalesLogger.info("Astron RequestSalesInfo: " + query);
                                statement.executeUpdate(query);
                                conn.commit();
                            }
                            succeededRequests.add(entry.requestExchange);

                        } catch (SQLException e) {
                            failedRequests.put(entry.requestExchange, e);
                            e.printStackTrace();
                        } finally {
                            if (statement != null)
                                statement.close();
                        }
                    }
                } finally {
                    closeConnection(conn);
                }
            } catch (ClassNotFoundException | SQLException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public void finishReadingSalesInfo(AstronSalesBatch salesBatch) {

        AstronSettings astronMySQLSettings = springContext.containsBean("astronSettings") ? (AstronSettings) springContext.getBean("astronSettings") : null;
        String connectionString = astronMySQLSettings == null ? null : astronMySQLSettings.getConnectionString(); //"jdbc:mysql://172.16.0.35/export_axapta"
        String user = astronMySQLSettings == null ? null : astronMySQLSettings.getUser(); //luxsoft
        String password = astronMySQLSettings == null ? null : astronMySQLSettings.getPassword(); //123456

        if (connectionString != null && salesBatch.recordList != null) {

            Connection conn = null;
            PreparedStatement ps = null;

            try {
                conn = getConnection(connectionString, user, password);
                ps = conn.prepareStatement("UPDATE [SALES] SET FUSION_PROCESSED = 1 WHERE SALESNUM = ? AND SESSID = ? AND SYSTEMID = ? AND SAREAID = ?");
                for (AstronRecord record : salesBatch.recordList) {
                    ps.setInt(1, record.salesNum);
                    ps.setInt(2, record.sessId);
                    ps.setInt(3, record.systemId);
                    ps.setInt(4, record.sAreaId);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();

            } catch (SQLException | ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                try {
                    closeStatement(ps);
                    closeConnection(conn);
                } catch (SQLException ignored) {
                }
            }
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
        Connection conn = DriverManager.getConnection(connectionString, user, password);
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

    private void closeStatement(PreparedStatement ps) throws SQLException {
        if (ps != null)
            ps.close();
    }

    private void closeConnection(Connection conn) throws SQLException {
        if (conn != null)
            conn.close();
    }
}