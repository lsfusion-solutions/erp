package equ.clt.handler.ukm4mysql;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import lsfusion.base.Pair;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.sql.Date;
import java.text.ParseException;
import java.util.*;

public class UKM4MySQLHandler extends CashRegisterHandler<UKM4MySQLSalesBatch> {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");

    private FileSystemXmlApplicationContext springContext;

    public UKM4MySQLHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        return "ukm4MySql";
    }

    @Override
    public Map<Integer, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) throws IOException {

        Map<Integer, SendTransactionBatch> sendTransactionBatchMap = new HashMap<Integer, SendTransactionBatch>();

        try {

            Class.forName("com.mysql.jdbc.Driver");

            UKM4MySQLSettings ukm4MySQLSettings = springContext.containsBean("ukm4MySQLSettings") ? (UKM4MySQLSettings) springContext.getBean("ukm4MySQLSettings") : null;
            String connectionString = ukm4MySQLSettings == null ? null : ukm4MySQLSettings.getImportConnectionString(); //"jdbc:mysql://172.16.0.35/import"
            String user = ukm4MySQLSettings == null ? null : ukm4MySQLSettings.getUser(); //luxsoft
            String password = ukm4MySQLSettings == null ? null : ukm4MySQLSettings.getPassword(); //123456

            if(connectionString == null) {
                processTransactionLogger.error("No importConnectionString in ukm4MySQLSettings found");
            } else {

                for (TransactionCashRegisterInfo transaction : transactionList) {

                    String weightCode = transaction.weightCodeGroupCashRegister;

                    Connection conn = DriverManager.getConnection(connectionString, user, password);

                    Exception exception = null;
                    try {

                        int version = getVersion(conn);

                        processTransactionLogger.info(String.format("ukm4 mysql: transaction %s, table classif", transaction.id));
                        exportClassif(conn, transaction, version);

                        processTransactionLogger.info(String.format("ukm4 mysql: transaction %s, table items", transaction.id));
                        exportItems(conn, transaction, version);

                        processTransactionLogger.info(String.format("ukm4 mysql: transaction %s, table items_stocks", transaction.id));
                        exportItemsStocks(conn, transaction, version);

                        processTransactionLogger.info(String.format("ukm4 mysql: transaction %s, table pricelist", transaction.id));
                        exportPriceList(conn, transaction, version);

                        processTransactionLogger.info(String.format("ukm4 mysql: transaction %s, table pricelist_var", transaction.id));
                        exportPriceListVar(conn, transaction, weightCode, version);

                        processTransactionLogger.info(String.format("ukm4 mysql: transaction %s, table pricetype_store_pricelist", transaction.id));
                        exportPriceTypeStorePriceList(conn, transaction, version);

                        processTransactionLogger.info(String.format("ukm4 mysql: transaction %s, table var", transaction.id));
                        exportVar(conn, transaction, weightCode, version);

                        processTransactionLogger.info(String.format("ukm4 mysql: transaction %s, table signal", transaction.id));
                        exportSignals(conn, transaction, version, true);

                        processTransactionLogger.info(String.format("ukm4 mysql: transaction %s, table pricelist_items", transaction.id));
                        exportPriceListItems(conn, transaction, version + 1);

                        processTransactionLogger.info(String.format("ukm4 mysql: transaction %s, table signal", transaction.id));
                        exportSignals(conn, transaction, version + 1, false);

                    } catch (Exception e) {
                        exception = e;
                    } finally {
                        if (conn != null)
                            conn.close();
                    }
                    sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(exception));
                }
            }
        } catch (ClassNotFoundException e) {
            throw Throwables.propagate(e);
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
        return sendTransactionBatchMap;
    }

    private int getVersion(Connection conn) throws SQLException {
        int version;
        Statement statement = null;
        try {
            statement = conn.createStatement();
            String query = "select max(version) from `signal`";
            ResultSet rs = statement.executeQuery(query);
            version = (rs.next() ? rs.getInt(1) : 0) + 1;
        } catch (SQLException e) {
            version = 1;
        } finally {
            if (statement != null)
                statement.close();
        }
        return version;
    }

    private void exportClassif(Connection conn, TransactionCashRegisterInfo transaction, int version) throws SQLException {

        Set<String> usedGroups = new HashSet<String>();

        conn.setAutoCommit(false);
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(
                    "INSERT INTO classif (id, owner, name, version, deleted) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE owner=VALUES(owner), name=VALUES(name), deleted=VALUES(deleted)");

            for (CashRegisterItemInfo item : transaction.itemsList) {
                List<ItemGroup> itemGroupList = transaction.itemGroupMap.get(item.idItemGroup);
                for (ItemGroup itemGroup : itemGroupList) {
                    if (!usedGroups.contains(itemGroup.idItemGroup)) {
                        usedGroups.add(itemGroup.idItemGroup);
                        Long idItemGroup = parseGroup(itemGroup.idItemGroup);
                        if(idItemGroup != 0) {
                            ps.setLong(1, idItemGroup); //id
                            ps.setLong(2, parseGroup(itemGroup.idParentItemGroup)); //owner
                            ps.setString(3, trim(itemGroup.nameItemGroup, 80, "")); //name
                            ps.setInt(4, version); //version
                            ps.setInt(5, 0); //deleted
                            ps.addBatch();
                        }
                    }
                }
            }

            ps.executeBatch();
            conn.commit();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            if (ps != null)
                ps.close();
        }
    }

    private void exportItems(Connection conn, TransactionCashRegisterInfo transaction, int version) throws SQLException {
        conn.setAutoCommit(false);
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(
                    "INSERT INTO items (id, name, descr, measure, measprec, classif, prop, summary, exp_date, version, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" +
                            "ON DUPLICATE KEY UPDATE name=VALUES(name), descr=VALUES(descr), measure=VALUES(measure), measprec=VALUES(measprec), classif=VALUES(classif)," +
                            "prop=VALUES(prop), summary=VALUES(summary), exp_date=VALUES(exp_date), deleted=VALUES(deleted)");

            for (CashRegisterItemInfo item : transaction.itemsList) {
                ps.setString(1, trim(item.idItem, 40)); //id
                ps.setString(2, trim(item.name, 40, "")); //name
                ps.setString(3, item.description == null ? "" : item.description); //descr
                ps.setString(4, trim(item.shortNameUOM, 40, "")); //measure
                ps.setInt(5, item.passScalesItem ? 3 : 0); //measprec
                ps.setLong(6, parseGroup(item.idItemGroup)); //classif
                ps.setInt(7, 1); //prop - признак товара ?
                ps.setString(8, trim(item.description, 100, "")); //summary
                ps.setDate(9, item.expiryDate); //exp_date
                ps.setInt(10, version); //version
                ps.setInt(11, 0); //deleted
                ps.addBatch();
            }

            ps.executeBatch();
            conn.commit();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            if (ps != null)
                ps.close();
        }
    }

    private void exportItemsStocks(Connection conn, TransactionCashRegisterInfo transaction, int version) throws SQLException {
        conn.setAutoCommit(false);
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(
                    "INSERT INTO items_stocks (store, item, stock, version, deleted) VALUES (?, ?, ?, ?, ?)" +
                            "ON DUPLICATE KEY UPDATE deleted=VALUES(deleted)");

            for (CashRegisterItemInfo item : transaction.itemsList) {
                if(item.section != null) {
                    for (String stock : item.section.split(",")) {
                        ps.setString(1, String.valueOf(transaction.departmentNumberGroupCashRegister)); //store
                        ps.setString(2, trim(item.idItem, 40, "")); //item
                        ps.setInt(3, Integer.parseInt(stock)); //stock
                        ps.setInt(4, version); //version
                        ps.setInt(5, 0); //deleted
                        ps.addBatch();
                    }
                }
            }

            ps.executeBatch();
            conn.commit();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            if (ps != null)
                ps.close();
        }
    }

    private void exportPriceList(Connection conn, TransactionCashRegisterInfo transaction, int version) throws SQLException {
        conn.setAutoCommit(false);
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(
                    "INSERT INTO pricelist (id, name, version, deleted) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE name=VALUES(name), deleted=VALUES(deleted)");

            ps.setInt(1, transaction.nppGroupMachinery); //id
            ps.setString(2, trim(String.valueOf(transaction.nppGroupMachinery), 100, "")); //name
            ps.setInt(3, version); //version
            ps.setInt(4, 0); //deleted
            ps.addBatch();

            ps.executeBatch();
            conn.commit();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            if (ps != null)
                ps.close();
        }
    }

    private void exportPriceListItems(Connection conn, TransactionCashRegisterInfo transaction, int version) throws SQLException {
        conn.setAutoCommit(false);
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(
                    "INSERT INTO pricelist_items (pricelist, item, price, minprice, version, deleted) VALUES (?, ?, ?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE price=VALUES(price), minprice=VALUES(minprice), deleted=VALUES(deleted)");
            for (CashRegisterItemInfo item : transaction.itemsList) {
                ps.setInt(1, transaction.nppGroupMachinery); //pricelist
                ps.setString(2, trim(item.idItem, 40, "")); //item
                ps.setBigDecimal(3, item.price); //price
                ps.setBigDecimal(4, BigDecimal.ZERO); //minprice
                ps.setInt(5, version); //version
                ps.setInt(6, 0); //deleted
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            if (ps != null)
                ps.close();
        }
    }

    private void exportPriceListVar(Connection conn, TransactionCashRegisterInfo transaction, String weightCode, int version) throws SQLException {
        conn.setAutoCommit(false);
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(
                    "INSERT INTO pricelist_var (pricelist, var, price, version, deleted) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE price=VALUES(price), deleted=VALUES(deleted)");
            for (CashRegisterItemInfo item : transaction.itemsList) {
                String barcode = makeBarcode(item.idBarcode, item.passScalesItem, weightCode);
                if(barcode != null) {
                    ps.setInt(1, transaction.nppGroupMachinery); //pricelist
                    ps.setString(2, trim(barcode, 40)); //var
                    ps.setBigDecimal(3, item.price); //price
                    ps.setInt(4, version); //version
                    ps.setInt(5, 0); //deleted
                    ps.addBatch();
                }
            }
            ps.executeBatch();
            conn.commit();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            if (ps != null)
                ps.close();
        }
    }

    private void exportPriceTypeStorePriceList(Connection conn, TransactionCashRegisterInfo transaction, int version) throws SQLException {
        conn.setAutoCommit(false);
        PreparedStatement ps = null;
        try {
            if(transaction.departmentNumberGroupCashRegister != null && transaction.nppGroupMachinery != null) {
                ps = conn.prepareStatement(
                        "INSERT INTO pricetype_store_pricelist (pricetype, store, pricelist, version, deleted) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE pricelist=VALUES(pricelist), deleted=VALUES(deleted)");
                ps.setInt(1, 123); //pricetype
                ps.setString(2, String.valueOf(transaction.departmentNumberGroupCashRegister)); //store
                ps.setInt(3, transaction.nppGroupMachinery); //pricelist
                ps.setInt(4, version); //version
                ps.setInt(5, 0); //deleted
                ps.addBatch();
                ps.executeBatch();
                conn.commit();
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            if (ps != null)
                ps.close();
        }
    }

    private void exportVar(Connection conn, TransactionCashRegisterInfo transaction, String weightCode, int version) throws SQLException {
        conn.setAutoCommit(false);
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(
                    "INSERT INTO var (id, item, quantity, stock, version, deleted) VALUES (?, ?, ?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE item=VALUES(item), quantity=VALUES(quantity), stock=VALUES(stock), deleted=VALUES(deleted)");
            for (CashRegisterItemInfo item : transaction.itemsList) {
                String barcode = makeBarcode(item.idBarcode, item.passScalesItem, weightCode);
                if(barcode != null && item.idItem != null) {
                    ps.setString(1, trim(barcode, 40)); //id
                    ps.setString(2, trim(item.idItem, 40)); //item
                    ps.setInt(3, 1); //quantity
                    ps.setInt(4, 1); //stock
                    ps.setInt(5, version); //version
                    ps.setInt(6, 0); //deleted
                    ps.addBatch();
                }
            }
            ps.executeBatch();
            conn.commit();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            if (ps != null)
                ps.close();
        }
    }

    private void exportSignals(Connection conn, TransactionCashRegisterInfo transaction, int version, boolean ignoreSnapshot) throws SQLException {
        conn.setAutoCommit(true);
        Statement statement = null;
        try {
            statement = conn.createStatement();

            String sql = String.format("INSERT INTO `signal` (`signal`, version) VALUES('%s', '%s') ON DUPLICATE KEY UPDATE `signal`=VALUES(`signal`);",
                    (transaction.snapshot && !ignoreSnapshot) ? "cumm" : "incr", version);
            statement.executeUpdate(sql);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            if (statement != null)
                statement.close();
        }
    }

    private String makeBarcode(String idBarcode, boolean passScalesItem, String weightCode) {
        return idBarcode != null && idBarcode.length() == 5 && passScalesItem && weightCode != null ? (weightCode + idBarcode) : idBarcode;
    }

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {

    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) throws IOException {

    }

    @Override
    public void sendDiscountCardList(List<DiscountCard> discountCardList, Date startDate, Set<String> directory) throws IOException {
    }

    @Override
    public void sendPromotionInfo(PromotionInfo promotionInfo, Set<String> directory) throws IOException {
    }

    @Override
    public SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException {

        UKM4MySQLSalesBatch salesBatch = null;

        try {

            Class.forName("com.mysql.jdbc.Driver");

            UKM4MySQLSettings ukm4MySQLSettings = springContext.containsBean("ukm4MySQLSettings") ? (UKM4MySQLSettings) springContext.getBean("ukm4MySQLSettings") : null;
            String connectionString = ukm4MySQLSettings == null ? null : ukm4MySQLSettings.getExportConnectionString(); //"jdbc:mysql://172.16.0.35/export_axapta"
            String user = ukm4MySQLSettings == null ? null : ukm4MySQLSettings.getUser(); //luxsoft
            String password = ukm4MySQLSettings == null ? null : ukm4MySQLSettings.getPassword(); //123456

            if(connectionString == null) {
                processTransactionLogger.error("No exportConnectionString in ukm4MySQLSettings found");
            } else {

                Connection conn = null;

                try {
                    conn = DriverManager.getConnection(connectionString, user, password);

                    salesBatch = readSalesInfoFromSQL(conn);

                } finally {
                    if (conn != null)
                        conn.close();
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return salesBatch;
    }

    private List<Object> readReceiptTable(Connection conn, Map<Integer, String> loginMap) throws SQLException {

        Map<Integer, List<Object>> receiptMap = new HashMap<Integer, List<Object>>();
        Set<Pair<Integer, Integer>> receiptSet = new HashSet<Pair<Integer, Integer>>();
        Statement statement = null;
        try {
            statement = conn.createStatement();
            String query = "select cash_id, id, global_number, type, login, shift_open, date from receipt";
            ResultSet rs = statement.executeQuery(query);
            while(rs.next()) {
                Integer cash_id = rs.getInt(1);
                Integer id = rs.getInt(2);
                Integer numberReceipt = rs.getInt(3); //global_number
                Integer receiptType = rs.getInt(4); //type
                Integer login = rs.getInt(5); //login
                String idEmployee = loginMap.get(login);
                String numberZReport = String.valueOf(rs.getInt(6)); //shift_open
                Date date = rs.getDate(7);
                Time time = rs.getTime(7);

                receiptMap.put(id, Arrays.asList((Object) receiptType, numberZReport, numberReceipt, date, time, idEmployee, null, null));
                receiptSet.add(Pair.create(cash_id, id));
            }
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        } finally {
            if (statement != null)
                statement.close();
        }
        return Arrays.asList(receiptMap, receiptSet);
    }

    private Map<Integer, String> readLoginMap(Connection conn) throws SQLException {

        Map<Integer, String> loginMap = new HashMap<Integer, String>();

        Statement statement = null;
        try {
            statement = conn.createStatement();
            String query = "select id, user_id from login";
            ResultSet rs = statement.executeQuery(query);
            while(rs.next()) {
                Integer id = rs.getInt(1);
                String idEmployee = String.valueOf(rs.getInt(2));

                loginMap.put(id, idEmployee);
            }
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        } finally {
            if (statement != null)
                statement.close();
        }
        return loginMap;
    }

    private Map<String, Map<Integer, BigDecimal>> readPaymentMap(Connection conn) throws SQLException {

        Map<String, Map<Integer, BigDecimal>> paymentMap = new HashMap<String, Map<Integer, BigDecimal>>();

        Statement statement = null;
        try {
            statement = conn.createStatement();
            String query = "select cash_id, receipt_header, payment_id, amount from receipt_payment";
            ResultSet rs = statement.executeQuery(query);
            while(rs.next()) {
                Integer cash_id = rs.getInt(1); //cash_id
                Integer idReceipt = rs.getInt(2); //receipt_header
                String key = String.valueOf(cash_id) + "/" + String.valueOf(idReceipt);
                Integer paymentType = rs.getInt(3); //payment_id
                if(paymentType.equals(2) || paymentType.equals(3))
                    paymentType = 1; //1, 2 и 3 - безнал
                BigDecimal amount = rs.getBigDecimal(4);

                Map<Integer, BigDecimal> paymentEntry = paymentMap.containsKey(key) ? paymentMap.get(key) : new HashMap<Integer, BigDecimal>();
                paymentEntry.put(paymentType, amount);
                paymentMap.put(key, paymentEntry);
            }
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        } finally {
            if (statement != null)
                statement.close();
        }
        return paymentMap;
    }

    private UKM4MySQLSalesBatch readSalesInfoFromSQL(Connection conn) throws SQLException {

        List<SalesInfo> salesInfoList = new ArrayList<SalesInfo>();

        Map<Integer, String> loginMap = readLoginMap(conn);
        List<Object> receiptTable = readReceiptTable(conn, loginMap);
        Map<Integer, List<Object>> receiptMap = (Map<Integer, List<Object>>) receiptTable.get(0);
        Set<Pair<Integer, Integer>> receiptSet = (Set<Pair<Integer, Integer>>) receiptTable.get(1);
        Map<String, Map<Integer, BigDecimal>> paymentMap = readPaymentMap(conn);

        Set<Pair<Integer, Integer>> receiptItemSet = new HashSet<Pair<Integer, Integer>>();
        if(receiptMap != null && receiptSet != null && paymentMap != null) {
            Statement statement = null;
            try {
                statement = conn.createStatement();
                String query = "select store, cash_number, cash_id, id, receipt_header, var, item, total_quantity, price, total," +
                        " position, real_amount from receipt_item";
                ResultSet rs = statement.executeQuery(query);

                while (rs.next()) {

                    Integer nppGroupMachinery = Integer.parseInt(rs.getString(1)); //store
                    Integer nppMachinery = rs.getInt(2); //cash_number
                    Integer cash_id = rs.getInt(3); //cash_id
                    Integer id = rs.getInt(4); //cash_id
                    Integer idReceipt = rs.getInt(5); //receipt_header
                    String idBarcode = rs.getString(6); //var
                    String idItem = rs.getString(7); //item
                    BigDecimal totalQuantity = rs.getBigDecimal(8); //total_quantity
                    BigDecimal price = rs.getBigDecimal(9);
                    BigDecimal sum = rs.getBigDecimal(10); //total
                    Integer position = rs.getInt(11) + 1;
                    BigDecimal realAmount = rs.getBigDecimal(12); //real_amount

                    List<Object> receiptEntry = receiptMap.get(idReceipt);
                    Map<Integer, BigDecimal> paymentEntry = paymentMap.get(cash_id + "/" + idReceipt);
                    if (receiptEntry != null && paymentEntry != null && totalQuantity != null) {
                        Integer receiptType = (Integer) receiptEntry.get(0);
                        boolean isSale = receiptType != null && (receiptType == 0 || receiptType == 8);
                        boolean isReturn = receiptType != null && (receiptType == 1 || receiptType == 4 || receiptType == 9);
                        String numberZReport = (String) receiptEntry.get(1);
                        Integer numberReceipt = (Integer) receiptEntry.get(2);
                        Date dateReceipt = (Date) receiptEntry.get(3);
                        Time timeReceipt = (Time) receiptEntry.get(4);
                        String idEmployee = (String) receiptEntry.get(5);
                        String firstNameContact = (String) receiptEntry.get(6);
                        String lastNameContact = (String) receiptEntry.get(7);

                        BigDecimal sumCash = paymentEntry.get(0);
                        BigDecimal sumCard = paymentEntry.get(1);
                        BigDecimal sumGiftCard = paymentEntry.get(2);

                        totalQuantity = isSale ? totalQuantity : isReturn ? totalQuantity.negate() : null;
                        BigDecimal discountSumReceiptDetail = safeSubtract(sum, realAmount);
                        salesInfoList.add(new SalesInfo(false, nppGroupMachinery, nppMachinery, numberZReport,
                                numberReceipt, dateReceipt, timeReceipt, idEmployee, firstNameContact, lastNameContact,
                                sumCard, sumCash, sumGiftCard, idBarcode, null, totalQuantity, price,
                                isSale ? sum : sum.negate(), discountSumReceiptDetail, null, null, position, null));
                        receiptItemSet.add(Pair.create(cash_id, id));
                    }

                }
            } catch (SQLException e) {
                throw Throwables.propagate(e);
            } finally {
                if (statement != null)
                    statement.close();
            }
        }
        return new UKM4MySQLSalesBatch(salesInfoList, receiptSet, receiptItemSet);
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
    public void finishReadingSalesInfo(UKM4MySQLSalesBatch salesBatch) {

        UKM4MySQLSettings ukm4MySQLSettings = springContext.containsBean("ukm4MySQLSettings") ? (UKM4MySQLSettings) springContext.getBean("ukm4MySQLSettings") : null;
        String connectionString = ukm4MySQLSettings == null ? null : ukm4MySQLSettings.getExportConnectionString(); //"jdbc:mysql://172.16.0.35/export_axapta"
        String user = ukm4MySQLSettings == null ? null : ukm4MySQLSettings.getUser(); //luxsoft
        String password = ukm4MySQLSettings == null ? null : ukm4MySQLSettings.getPassword(); //123456

        if (connectionString != null) {

            Connection conn = null;
            PreparedStatement ps = null;

            try {
                conn = DriverManager.getConnection(connectionString, user, password);

                conn.setAutoCommit(false);
                ps = conn.prepareStatement("DELETE FROM receipt WHERE cash_id = ? AND id = ?");
                for (Pair<Integer, Integer> receiptEntry : salesBatch.receiptSet) {
                    ps.setInt(1, receiptEntry.first); //id
                    ps.setInt(2, receiptEntry.second); //cash_id
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();

                ps = conn.prepareStatement("DELETE FROM receipt_item WHERE cash_id = ? AND id = ?");
                for (Pair<Integer, Integer> receiptItemEntry : salesBatch.receiptItemSet) {
                    ps.setInt(1, receiptItemEntry.first); //id
                    ps.setInt(2, receiptItemEntry.second); //cash_id
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();

            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                try {
                    if(ps != null)
                        ps.close();
                    if (conn != null)
                        conn.close();
                } catch (SQLException ignored) {
                }
            }
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
    public ExtraCheckZReportBatch extraCheckZReportSum(List<CashRegisterInfo> cashRegisterInfoList, Map<String, BigDecimal> zReportSumMap) throws ClassNotFoundException, SQLException {
        return null;
    }

    protected String trim(String input, Integer length) {
        return trim(input, length, null);
    }

    protected String trim(String input, Integer length, String defaultValue) {
        return input == null ? defaultValue : (length == null || length >= input.trim().length() ? input.trim() : input.trim().substring(0, length));
    }

    protected Long parseGroup(String idItemGroup) {
        try {
            return idItemGroup == null ? 0 : Long.parseLong(idItemGroup.equals("Все") ? "0" : idItemGroup.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return (long) 0;
        }
    }

    protected BigDecimal safeSubtract(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else
            return (operand1 == null ? operand2.negate() : (operand2 == null ? operand1 : operand1.subtract((operand2))));
    }

}
