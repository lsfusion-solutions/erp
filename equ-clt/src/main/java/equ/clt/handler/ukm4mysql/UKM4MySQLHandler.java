package equ.clt.handler.ukm4mysql;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import org.apache.commons.lang.time.DateUtils;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.sql.*;
import java.sql.Date;
import java.text.ParseException;
import java.util.*;

public class UKM4MySQLHandler extends CashRegisterHandler<UKM4MySQLSalesBatch> {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    String defaultCharset = "Cp1251";

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
            String connectionString = ukm4MySQLSettings == null ? null : ukm4MySQLSettings.getConnectionString(); //"jdbc:mysql://172.16.0.35/import"
            String user = ukm4MySQLSettings == null ? null : ukm4MySQLSettings.getUser(); //luxsoft
            String password = ukm4MySQLSettings == null ? null : ukm4MySQLSettings.getPassword(); //123456

            if(connectionString == null) {
                processTransactionLogger.error("No ukm4MySQLSettings found");
            } else {

                for (TransactionCashRegisterInfo transaction : transactionList) {

                    Connection conn = DriverManager.getConnection(connectionString, user, password);

                    Exception exception = null;
                    try {

                        int version = getVersion(conn);

                        exportClassif(conn, transaction, version);

                        exportItems(conn, transaction, version);

                        exportPriceList(conn, transaction, version);

                        exportPriceListItems(conn, transaction, version);

                        exportPriceListVar(conn, transaction, version);

                        exportPriceTypeStorePriceList(conn, transaction, version);

                        exportVar(conn, transaction, version);

                        exportSignals(conn, transaction, version);

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
                        ps.setLong(1, parseGroup(itemGroup.idItemGroup)); //id
                        ps.setLong(2, parseGroup(itemGroup.idParentItemGroup)); //owner
                        ps.setString(3, trim(itemGroup.nameItemGroup, 80, "")); //name
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
                ps.setString(3, trim(item.description, 80, "")); //descr
                ps.setString(4, trim(item.shortNameUOM, 40, "")); //measure
                ps.setInt(5, item.passScalesItem ? 3 : 0); //measprec
                ps.setLong(6, parseGroup(item.idItemGroup)); //classif
                ps.setInt(7, 1); //prop - признак товара ?
                ps.setString(8, item.description); //summary
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

    private void exportPriceListVar(Connection conn, TransactionCashRegisterInfo transaction, int version) throws SQLException {
        conn.setAutoCommit(false);
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(
                    "INSERT INTO pricelist_var (pricelist, var, price, version, deleted) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE price=VALUES(price), deleted=VALUES(deleted)");
            for (CashRegisterItemInfo item : transaction.itemsList) {
                if(item.idBarcode != null) {
                    ps.setInt(1, transaction.nppGroupMachinery); //pricelist
                    ps.setString(2, trim(item.idBarcode, 40)); //var
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

    private void exportVar(Connection conn, TransactionCashRegisterInfo transaction, int version) throws SQLException {
        conn.setAutoCommit(false);
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(
                    "INSERT INTO var (id, item, quantity, stock, version, deleted) VALUES (?, ?, ?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE item=VALUES(item), quantity=VALUES(quantity), stock=VALUES(stock), deleted=VALUES(deleted)");
            for (CashRegisterItemInfo item : transaction.itemsList) {
                if(item.idBarcode != null && item.idItem != null) {
                    ps.setString(1, trim(item.idBarcode, 40)); //id
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

    private void exportSignals(Connection conn, TransactionCashRegisterInfo transaction, int version) throws SQLException {
        conn.setAutoCommit(true);
        Statement statement = null;
        try {
            statement = conn.createStatement();

            String sql = String.format("INSERT INTO `signal` (`signal`, version) VALUES('%s', '%s') ON DUPLICATE KEY UPDATE `signal`=VALUES(`signal`);",
                    transaction.snapshot ? "cumm" : "incr", version);
            statement.executeUpdate(sql);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            if (statement != null)
                statement.close();
        }
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

        /*try {

            Class.forName("com.mysql.jdbc.Driver");

            UKM4MySQLSettings ukm4MySQLSettings = springContext.containsBean("ukm4MySQLSettings") ? (UKM4MySQLSettings) springContext.getBean("ukm4MySQLSettings") : null;
            String connectionString = ukm4MySQLSettings == null ? null : ukm4MySQLSettings.getConnectionString(); //"jdbc:mysql://172.16.0.35/import"
            String user = ukm4MySQLSettings == null ? null : ukm4MySQLSettings.getUser(); //luxsoft
            String password = ukm4MySQLSettings == null ? null : ukm4MySQLSettings.getPassword(); //123456

            if(connectionString == null) {
                processTransactionLogger.error("No ukm4MySQLSettings found");
            } else {

                Connection conn = null;

                try {
                    conn = DriverManager.getConnection(connectionString, user, password);

                    readReceiptTable(conn);
                    readReceiptItemTable(conn);

                } finally {
                    if (conn != null)
                        conn.close();
                }
            }
        } catch (ClassNotFoundException e) {
            throw Throwables.propagate(e);
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }*/
        return null;




        /*Map<String, Integer> directoryGroupCashRegisterMap = new HashMap<String, Integer>();
        for (CashRegisterInfo cashRegister : cashRegisterInfoList) {
            if (cashRegister.number != null && cashRegister.numberGroup != null)
                directoryGroupCashRegisterMap.put(cashRegister.directory + "_" + cashRegister.number, cashRegister.numberGroup);
        }
        List<SalesInfo> salesInfoList = new ArrayList<SalesInfo>();
        List<String> readFiles = new ArrayList<String>();
        DBF importSailFile = null;
        DBF importDiscFile = null;
        DBF importCardFile = null;
        Map<String, BigDecimal> discountMap = new HashMap<String, BigDecimal>();
        Map<String, String> discountCardMap = new HashMap<String, String>();
        try {
            String fileDiscPath = directory + "/sail/CASHDISC.DBF";
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
                    BigDecimal discountSum = getDBFBigDecimalFieldValue(importDiscFile, "DISCOUNTCU", defaultCharset);

                    String sid = cashRegisterNumber + "_" + zNumber + "_" + receiptNumber + "_" + numberReceiptDetail;
                    BigDecimal tempSum = discountMap.get(sid);
                    discountMap.put(sid, safeAdd(discountSum, tempSum));
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
                Map<Integer, BigDecimal[]> receiptNumberSumReceipt = new HashMap<Integer, BigDecimal[]>();

                for (int i = 0; i < recordSailCount; i++) {
                    importSailFile.read();

                    Integer operation = getDBFIntegerFieldValue(importSailFile, "OPERATION", defaultCharset);
                    //0 - возврат cash, 1 - продажа cash, 2,4 - возврат card, 3,5 - продажа card

                    String numberCashRegister = getDBFFieldValue(importSailFile, "CASHNUMBER", defaultCharset);
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
                    BigDecimal discountSumReceiptDetail = discountMap.get(numberCashRegister + "_" + zNumber + "_" + receiptNumber + "_" + numberReceiptDetail);
                    String discountCardNumber = discountCardMap.get(numberCashRegister + "_" + zNumber + "_" + receiptNumber);

                    BigDecimal[] tempSumReceipt = receiptNumberSumReceipt.get(receiptNumber);
                    BigDecimal tempSum1 = tempSumReceipt != null ? tempSumReceipt[0] : null;
                    BigDecimal tempSum2 = tempSumReceipt != null ? tempSumReceipt[1] : null;
                    receiptNumberSumReceipt.put(receiptNumber, new BigDecimal[]{safeAdd(tempSum1, (operation <= 1 ? sumReceiptDetail : null)),
                            safeAdd(tempSum2, (operation > 1 ? sumReceiptDetail : null))});

                    salesInfoList.add(new SalesInfo(false, directoryGroupCashRegisterMap.get(directory + "_" + numberCashRegister), Integer.parseInt(numberCashRegister), zNumber,
                            receiptNumber, date, time, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, barcodeReceiptDetail,
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
        return new UKM4MySQLSalesBatch(salesInfoList, readFiles);*/
    }

    private void readReceiptTable(Connection conn) throws SQLException {
        Statement statement = null;
        try {
            statement = conn.createStatement();
            String query = "select store, cash_number, cash_id, id, global_number, local_number, type, stock_id, stock_name, " +
                    "client, login, shift_open, date, pos, invoice_number, link_receipt, link_cash_id, amount, items_count, " +
                    "result, footer_date, client_card_code, ext_processed from receipt";
            ResultSet rs = statement.executeQuery(query);
            while(rs.next()) {
                String store = rs.getString(1);
                Integer cash_number = rs.getInt(2);
                Integer cash_id = rs.getInt(3);
                Integer id = rs.getInt(4);
                Integer global_number = rs.getInt(5);
                Integer local_number = rs.getInt(6);
                Integer type = rs.getInt(7);
                Integer stock_id = rs.getInt(8);
                String stock_name = rs.getString(9);
                String client = rs.getString(10);
                Integer login = rs.getInt(11);
                Integer shift_open = rs.getInt(12);
                Date date = rs.getDate(13);
                Integer pos = rs.getInt(14);
                String invoice_number = rs.getString(15);
                Integer link_receipt = rs.getInt(16);
                Integer link_cash_id = rs.getInt(17);
                BigDecimal amount = rs.getBigDecimal(18);
                Integer items_count = rs.getInt(19);
                Integer result = rs.getInt(20);
                Date footer_date = rs.getDate(21);
                String client_card_code = rs.getString(22);
                Integer ext_processed = rs.getInt(23);
            }
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        } finally {
            if (statement != null)
                statement.close();
        }
    }

    private void readReceiptItemTable(Connection conn) throws SQLException {
        Statement statement = null;
        try {
            statement = conn.createStatement();
            String query = "select store, cash_number, cash_id, id, receipt_header, var, item, name, var_quantity," +
                    " quantity, total_quantity, price, min_price, blocked_discount, total, stock_id, stock_name," +
                    " measurement, measurement_precision, classif, type, input, tax, position, remain, pricelist," +
                    " real_amount from receipt";
            ResultSet rs = statement.executeQuery(query);

            List<SalesInfo> salesInfoList = new ArrayList<SalesInfo>();

            while(rs.next()) {

                Integer nppGroupMachinery = Integer.parseInt(rs.getString(1)); //store
                Integer nppMachinery = rs.getInt(2); //cash_number
                Integer cash_id = rs.getInt(3);
                Integer id = 	rs.getInt(4);
                Integer idReceipt = rs.getInt(5); //receipt_header
                String var = rs.getString(6);
                String item = rs.getString(7);
                String name = rs.getString(8);
                BigDecimal var_quantity = rs.getBigDecimal(9);
                BigDecimal quantity = 	rs.getBigDecimal(10);
                BigDecimal total_quantity = rs.getBigDecimal(11);
                BigDecimal price = rs.getBigDecimal(12);
                BigDecimal min_price = 	rs.getBigDecimal(13);
                Integer blocked_discount = rs.getInt(14);
                BigDecimal total = rs.getBigDecimal(15);
                Integer stock_id = rs.getInt(16);
                String stock_name = rs.getString(17);
                String measurement = rs.getString(18);
                Integer measurement_precision = rs.getInt(19);
                Integer classif = rs.getInt(20);
                Integer type = 	rs.getInt(21);
                Integer input = 	rs.getInt(22);
                Integer tax = 	rs.getInt(23);
                Integer position = 	rs.getInt(24);
                BigDecimal remain = rs.getBigDecimal(25);
                Integer pricelist = rs.getInt(26);
                BigDecimal real_amount = rs.getBigDecimal(27);


                /*List<Object> receiptEntry = receiptMap.get(idReceipt);
                String numberZReport = receiptEntry == null ? null : (String) receiptEntry.get(0);
                Integer numberReceipt = receiptEntry == null ? null : (Integer) receiptEntry.get(1);
                Date dateReceipt = receiptEntry == null ? null : (Date) receiptEntry.get(2);
                Time timeReceipt = receiptEntry == null ? null : (Time) receiptEntry.get(3);
                String idEmployee = receiptEntry == null ? null : (String) receiptEntry.get(4);
                String firstNameContact = receiptEntry == null ? null : (String) receiptEntry.get(5);
                String lastNameContact = receiptEntry == null ? null : (String) receiptEntry.get(6);*/

                /*salesInfoList.add(new SalesInfo(false, nppGroupMachinery, nppMachinery, numberZReport,
                        numberReceipt, dateReceipt, timeReceipt, idEmployee, firstNameContact, lastNameContact,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, barcodeReceiptDetail,
                        null, operation % 2 == 1 ? quantityReceiptDetail : quantityReceiptDetail.negate(),
                        priceReceiptDetail,
                        operation % 2 == 1 ? sumReceiptDetail : sumReceiptDetail.negate(),
                        discountSumReceiptDetail, null, discountCardNumber, numberReceiptDetail, null));*/

            }
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        } finally {
            if (statement != null)
                statement.close();
        }
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
