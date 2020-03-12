package equ.clt.handler.ukm4mysql;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import equ.clt.handler.DefaultCashRegisterHandler;
import equ.clt.handler.HandlerUtils;
import lsfusion.base.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

import static equ.clt.handler.HandlerUtils.trim;

public class UKM4MySQLHandler extends DefaultCashRegisterHandler<UKM4MySQLSalesBatch> {

    private static String logPrefix = "ukm4 mysql: ";

    private FileSystemXmlApplicationContext springContext;

    public UKM4MySQLHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        String groupId = null;
        for (CashRegisterInfo cashRegister : transactionInfo.machineryInfoList) {
            if (cashRegister.directory != null) {
                String connectionString = new UKM4MySQLConnectionString(cashRegister.directory, 0).connectionString;
                groupId = connectionString != null ? connectionString : cashRegister.directory;
            }
        }
        return "ukm4MySql" + groupId;
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) {

        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        if (transactionList != null) {

            try {

                Class.forName("com.mysql.jdbc.Driver");

                UKM4MySQLSettings ukm4MySQLSettings = springContext.containsBean("ukm4MySQLSettings") ? (UKM4MySQLSettings) springContext.getBean("ukm4MySQLSettings") : null;
                Integer timeout = ukm4MySQLSettings == null ? null : ukm4MySQLSettings.getTimeout();
                timeout = timeout == null ? 300 : timeout;
                boolean skipItems = ukm4MySQLSettings == null || ukm4MySQLSettings.getSkipItems() != null && ukm4MySQLSettings.getSkipItems();
                boolean skipClassif = ukm4MySQLSettings == null || ukm4MySQLSettings.getSkipClassif() != null && ukm4MySQLSettings.getSkipClassif();
                boolean skipBarcodes = ukm4MySQLSettings == null || ukm4MySQLSettings.getSkipBarcodes() != null && ukm4MySQLSettings.getSkipBarcodes();
                boolean useBarcodeAsId = ukm4MySQLSettings == null || ukm4MySQLSettings.getUseBarcodeAsId() != null && ukm4MySQLSettings.getUseBarcodeAsId();
                boolean appendBarcode = ukm4MySQLSettings == null || ukm4MySQLSettings.getAppendBarcode() != null && ukm4MySQLSettings.getAppendBarcode();
                boolean exportTaxes = ukm4MySQLSettings != null && ukm4MySQLSettings.isExportTaxes();
                boolean sendZeroQuantityForWeightItems = ukm4MySQLSettings == null || ukm4MySQLSettings.getSendZeroQuantityForWeightItems() != null && ukm4MySQLSettings.getSendZeroQuantityForWeightItems();

                Map<String, List<TransactionCashRegisterInfo>> transactionsMap = new HashMap<>();
                for (TransactionCashRegisterInfo transaction : transactionList) {

                    String directory = null;
                    for (CashRegisterInfo cashRegister : transaction.machineryInfoList) {
                        if (cashRegister.directory != null) {
                            directory = cashRegister.directory;
                        }
                    }

                    List<TransactionCashRegisterInfo> transactions = transactionsMap.get(directory);
                    if(transactions == null) {
                        transactions = new ArrayList<>();
                    }
                    transactions.add(transaction);
                    transactionsMap.put(directory, transactions);

                }

                for (Map.Entry<String, List<TransactionCashRegisterInfo>> transactionsMapEntry : transactionsMap.entrySet()) {

                    Map<Integer, Long> versionTransactionMap = new HashMap<>();
                    Integer version = null;

                    String directory = transactionsMapEntry.getKey();
                    UKM4MySQLConnectionString params = new UKM4MySQLConnectionString(directory, 0);

                    for (TransactionCashRegisterInfo transaction : transactionsMapEntry.getValue()) {

                        Long failedTransaction = null;
                        for (Map.Entry<Long, SendTransactionBatch> entry : sendTransactionBatchMap.entrySet()) {
                            if (entry.getValue().exception != null) {
                                failedTransaction = entry.getKey();
                                break;
                            }
                        }

                        if (failedTransaction != null) {
                            String error = "One of previous transactions failed: " + failedTransaction;
                            processTransactionLogger.error(logPrefix + error);
                            sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(new RuntimeException(error)));
                        } else {
                            if (params.connectionString == null)
                                processTransactionLogger.error("No connectionString found");
                            else {
                                String weightCode = transaction.weightCodeGroupCashRegister;

                                String section = null;
                                for (CashRegisterInfo cashRegister : transaction.machineryInfoList) {
                                    if (cashRegister.section != null)
                                        section = cashRegister.section;
                                }
                                //String departmentNumber = getDepartmentNumber(transaction, section);
                                Integer nppGroupMachinery = transaction.departmentNumberGroupCashRegister != null ?
                                        transaction.departmentNumberGroupCashRegister : transaction.nppGroupMachinery;

                                Exception exception = null;
                                try(Connection conn = DriverManager.getConnection(params.connectionString, params.user, params.password)) {

                                    if (version == null)
                                        version = getVersion(conn);

                                    if (!skipItems) {
                                        version++;

                                        if (!skipClassif) {
                                            processTransactionLogger.info(logPrefix + String.format("transaction %s, table classif", transaction.id));
                                            exportClassif(conn, transaction, version);
                                        }

                                        if (exportTaxes) {
                                            processTransactionLogger.info(logPrefix + String.format("transaction %s, table taxes", transaction.id));
                                            exportTaxes(conn, transaction, version);
                                            exportTaxGroups(conn, transaction, version);
                                        }

                                        processTransactionLogger.info(logPrefix + String.format("transaction %s, table items", transaction.id));
                                        exportItems(conn, transaction, useBarcodeAsId, appendBarcode, exportTaxes, version);

                                        processTransactionLogger.info(logPrefix + String.format("transaction %s, table items_stocks", transaction.id));
                                        exportItemsStocks(conn, transaction, section/*departmentNumber*/, version);

                                        processTransactionLogger.info(logPrefix + String.format("transaction %s, table stocks", transaction.id));
                                        exportStocks(conn, transaction, section/*departmentNumber*/, version);

                                        processTransactionLogger.info(logPrefix + String.format("transaction %s, table pricelist", transaction.id));
                                        exportPriceList(conn, transaction, nppGroupMachinery, version);

                                        processTransactionLogger.info(logPrefix + String.format("transaction %s, table pricetype", transaction.id));
                                        exportPriceType(conn, version);

                                        processTransactionLogger.info(logPrefix + String.format("transaction %s, table pricetype_store_pricelist", transaction.id));
                                        exportPriceTypeStorePriceList(conn, transaction, nppGroupMachinery, section/*departmentNumber*/, version);

                                        processTransactionLogger.info(logPrefix + String.format("transaction %s, table var", transaction.id));
                                        exportVar(conn, transaction, useBarcodeAsId, weightCode, appendBarcode, sendZeroQuantityForWeightItems, version);

                                        processTransactionLogger.info(logPrefix + String.format("transaction %s, table properties", transaction.id));
                                        exportProperties(conn, transaction, version);

                                        processTransactionLogger.info(logPrefix + String.format("transaction %s, table property_values", transaction.id));
                                        exportPropertyValues(conn, transaction, version);

                                        processTransactionLogger.info(logPrefix + String.format("transaction %s, table item_property_values", transaction.id));
                                        exportItemPropertyValues(conn, transaction, useBarcodeAsId, appendBarcode, version);

                                        processTransactionLogger.info(logPrefix + String.format("transaction %s, table signal (%s)", transaction.id, "incr"));
                                        exportSignals(conn, transaction, version, true, timeout, false);
                                        versionTransactionMap.put(version, transaction.id);
                                    }

                                    version++;
                                    if (!skipBarcodes) {
                                        processTransactionLogger.info(logPrefix + String.format("transaction %s, table pricelist_var", transaction.id));
                                        exportPriceListVar(conn, transaction, nppGroupMachinery, weightCode, version);
                                    }

                                    processTransactionLogger.info(logPrefix + String.format("transaction %s, table pricelist_items", transaction.id));
                                    exportPriceListItems(conn, transaction, nppGroupMachinery, useBarcodeAsId, appendBarcode, version);

                                    processTransactionLogger.info(logPrefix + String.format("transaction %s, table signal (%s)", transaction.id,
                                            transaction.snapshot ? "cumm" : "incr"));
                                    exportSignals(conn, transaction, version, false, timeout, false);
                                    versionTransactionMap.put(version, transaction.id);

                                } catch (Exception e) {
                                    exception = e;
                                }
                                sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(exception));
                            }

                        }
                    }

                    if (params.connectionString != null) {
                        try (Connection conn = DriverManager.getConnection(params.connectionString, params.user, params.password)) {
                            processTransactionLogger.info(logPrefix + String.format("export to table signal %s records", versionTransactionMap.size()));
                            sendTransactionBatchMap.putAll(waitSignals(conn, versionTransactionMap, timeout));
                        }
                    }
                }
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
        return sendTransactionBatchMap;
    }

    private int getVersion(Connection conn) {
        int version;
        try(Statement statement = conn.createStatement()) {
            String query = "select max(version) from `signal`";
            ResultSet rs = statement.executeQuery(query);
            version = rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            version = 0;
        }
        return version;
    }

    private void exportClassif(Connection conn, TransactionCashRegisterInfo transaction, int version) throws SQLException {

        if (transaction.itemsList != null) {

            Set<String> usedGroups = new HashSet<>();

            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO classif (id, owner, name, version, deleted) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE owner=VALUES(owner), name=VALUES(name), deleted=VALUES(deleted)")) {

                for (CashRegisterItemInfo item : transaction.itemsList) {
                    List<ItemGroup> itemGroupList = transaction.itemGroupMap.get(item.extIdItemGroup);
                    if (itemGroupList != null) {
                        for (ItemGroup itemGroup : itemGroupList) {
                            if (!usedGroups.contains(itemGroup.extIdItemGroup)) {
                                usedGroups.add(itemGroup.extIdItemGroup);
                                String idItemGroup = parseGroup(itemGroup.extIdItemGroup);
                                if (!idItemGroup.equals("0")) {
                                    ps.setString(1, idItemGroup); //id
                                    ps.setString(2, parseGroup(itemGroup.idParentItemGroup)); //owner
                                    ps.setString(3, trim(itemGroup.nameItemGroup, "", 80)); //name
                                    ps.setInt(4, version); //version
                                    ps.setInt(5, 0); //deleted
                                    ps.addBatch();
                                }
                            }
                        }
                    }
                }

                ps.executeBatch();
                conn.commit();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    private void exportTaxes(Connection conn, TransactionCashRegisterInfo transaction, int version) throws SQLException {
        if (transaction.itemsList != null) {
            conn.setAutoCommit(false);
            try (Statement statement = conn.createStatement()) {
                statement.execute(String.format("INSERT INTO taxes (id, name, priority, version, deleted) VALUES (%s, %s, %s, %s, %s) " +
                        "ON DUPLICATE KEY UPDATE name=VALUES(name), priority=VALUES(priority), deleted=VALUES(deleted)", 1, "'НДС'", 1, version, 0));
                conn.commit();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    private void exportTaxGroups(Connection conn, TransactionCashRegisterInfo transaction, int version) throws SQLException {
        if (transaction.itemsList != null) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO taxgroup (id, tax_id, percent, version, deleted) VALUES (?, ?, ?, ?, ?)" +
                            "ON DUPLICATE KEY UPDATE percent=VALUES(percent), deleted=VALUES(deleted)")) {

                Set<Integer> usedVAT = new HashSet<>();
                for (CashRegisterItemInfo item : transaction.itemsList) {
                    Integer vat = getTax(item);
                    if (vat != 0 && !usedVAT.contains(vat)) {
                        ps.setInt(1, vat); //id
                        ps.setInt(2, 1); //tax_id
                        ps.setString(3, vat + "%"); //percent
                        ps.setInt(4, version); //version
                        ps.setInt(5, 0); //deleted
                        ps.addBatch();
                        usedVAT.add(vat);
                    }
                }

                ps.executeBatch();
                conn.commit();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    private void exportItems(Connection conn, TransactionCashRegisterInfo transaction, boolean useBarcodeAsId, boolean appendBarcode, boolean exportTaxes, int version) throws SQLException {
        if (transaction.itemsList != null) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(exportTaxes ?
                    "INSERT INTO items (id, name, descr, measure, measprec, classif, prop, summary, exp_date, version, deleted, tax) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" +
                            "ON DUPLICATE KEY UPDATE name=VALUES(name), descr=VALUES(descr), measure=VALUES(measure), measprec=VALUES(measprec), classif=VALUES(classif)," +
                            "prop=VALUES(prop), summary=VALUES(summary), exp_date=VALUES(exp_date), deleted=VALUES(deleted), tax=VALUES(tax)" :
                    "INSERT INTO items (id, name, descr, measure, measprec, classif, prop, summary, exp_date, version, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" +
                            "ON DUPLICATE KEY UPDATE name=VALUES(name), descr=VALUES(descr), measure=VALUES(measure), measprec=VALUES(measprec), classif=VALUES(classif)," +
                            "prop=VALUES(prop), summary=VALUES(summary), exp_date=VALUES(exp_date), deleted=VALUES(deleted)")) {

                for (CashRegisterItemInfo item : transaction.itemsList) {
                    ps.setString(1, getId(item, useBarcodeAsId, appendBarcode)); //id
                    ps.setString(2, trim(item.name, "", 40)); //name
                    ps.setString(3, item.description == null ? "" : item.description); //descr
                    ps.setString(4, trim(item.shortNameUOM, "", 40)); //measure
                    ps.setInt(5, item.passScalesItem ? (isNonWeight(item) ? 0 : 3) : (item.splitItem ? 3 : 0)); //measprec
                    ps.setString(6, parseGroup(item.extIdItemGroup)); //classif
                    ps.setInt(7, 1); //prop - признак товара ?
                    ps.setString(8, trim(item.description, "", 100)); //summary
                    ps.setDate(9, item.expiryDate); //exp_date
                    ps.setInt(10, version); //version
                    ps.setInt(11, 0); //deleted
                    if (exportTaxes) {
                        ps.setInt(12, getTax(item));
                    }
                    ps.addBatch();
                }

                ps.executeBatch();
                conn.commit();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    private int getTax(CashRegisterItemInfo item) {
        return item.vat != null ? item.vat.intValue() : 0;
    }

    private void exportItemsStocks(Connection conn, TransactionCashRegisterInfo transaction, String departmentNumber, int version) throws SQLException {
        if (transaction.itemsList != null) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO items_stocks (store, item, stock, version, deleted) VALUES (?, ?, ?, ?, ?)" + "ON DUPLICATE KEY UPDATE deleted=VALUES(deleted)")) {

                for (CashRegisterItemInfo item : transaction.itemsList) {
                    if (item.section != null) {
                        for (String stock : item.section.split(",")) {
                            String[] splitted = stock.split("\\|");
                            ps.setString(1, departmentNumber); //store
                            ps.setString(2, trim(item.idItem, "", 40)); //item
                            ps.setInt(3, Integer.parseInt(splitted[0])); //stock
                            ps.setInt(4, version); //version
                            ps.setInt(5, 0); //deleted
                            ps.addBatch();
                        }
                    }
                    if (item.deleteSection != null) {
                        for (String stock : item.deleteSection.split(",")) {
                            ps.setString(1, departmentNumber); //store
                            ps.setString(2, trim(item.idItem, "", 40)); //item
                            ps.setInt(3, Integer.parseInt(stock)); //stock
                            ps.setInt(4, version); //version
                            ps.setInt(5, 1); //deleted
                            ps.addBatch();
                        }
                    }
                }

                ps.executeBatch();
                conn.commit();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    private void exportStocks(Connection conn, TransactionCashRegisterInfo transaction, String departmentNumber, int version) throws SQLException {
        if (transaction.itemsList != null) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO stocks (store, id, name, version, deleted) VALUES (?, ?, ?, ?, 0)" + "ON DUPLICATE KEY UPDATE name=VALUES(name), deleted=VALUES(deleted)")) {

                Set<String> sections = new HashSet<>();
                for (CashRegisterItemInfo item : transaction.itemsList) {
                    if (item.section != null) {
                        for (String stock : item.section.split(",")) {
                            if (!sections.contains(stock)) {
                                sections.add(stock);
                                String[] splitted = stock.split("\\|");
                                Integer id = Integer.parseInt(splitted[0]);
                                String name = splitted.length > 1 ? splitted[1] : null;
                                ps.setString(1, departmentNumber); //store
                                ps.setInt(2, id); //id
                                ps.setString(3, trim(name, 80)); //name
                                ps.setInt(4, version); //version
                                ps.addBatch();
                            }
                        }
                    }
                }

                ps.executeBatch();
                conn.commit();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    private void exportPriceList(Connection conn, TransactionCashRegisterInfo transaction, Integer npp, int version) throws SQLException {
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO pricelist (id, name, version, deleted) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE name=VALUES(name), deleted=VALUES(deleted)")) {

            ps.setInt(1, npp); //id
            ps.setString(2, "Прайс-лист " + trim(String.valueOf(transaction.nameStockGroupCashRegister), "", 89)); //name
            ps.setInt(3, version); //version
            ps.setInt(4, 0); //deleted
            ps.addBatch();

            ps.executeBatch();
            conn.commit();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private void exportPriceListItems(Connection conn, TransactionCashRegisterInfo transaction, Integer npp, boolean useBarcodeAsId, boolean appendBarcode, int version) throws SQLException {
        if (transaction.itemsList != null) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO pricelist_items (pricelist, item, price, minprice, version, deleted) VALUES (?, ?, ?, ?, ?, ?) " + "ON DUPLICATE KEY UPDATE price=VALUES(price), minprice=VALUES(minprice), deleted=VALUES(deleted)")) {

                for (CashRegisterItemInfo item : transaction.itemsList) {
                    ps.setInt(1, npp); //pricelist
                    ps.setString(2, getId(item, useBarcodeAsId, appendBarcode)); //item
                    ps.setBigDecimal(3, item.price); //price
                    BigDecimal minPrice = item.flags == null || ((item.flags & 16) == 0) ? item.price : item.minPrice != null ? item.minPrice : BigDecimal.ZERO;
                    ps.setBigDecimal(4, minPrice); //minprice
                    ps.setInt(5, version); //version
                    ps.setInt(6, 0); //deleted
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    private void exportPriceListVar(Connection conn, TransactionCashRegisterInfo transaction, Integer npp, String weightCode, int version) throws SQLException {
        if (transaction.itemsList != null) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO pricelist_var (pricelist, var, price, version, deleted) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE price=VALUES(price), deleted=VALUES(deleted)")) {

                for (CashRegisterItemInfo item : transaction.itemsList) {
                    String barcode = makeBarcode(item.idBarcode, item.passScalesItem, weightCode);
                    if (barcode != null) {
                        ps.setInt(1, npp); //pricelist
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
            }
        }
    }

    private void exportPriceType(Connection conn, int version) throws SQLException {
            conn.setAutoCommit(false);
            try (Statement statement = conn.createStatement()) {
                statement.execute(String.format("INSERT INTO pricetype (id, name, version, deleted) VALUES (%s, %s, %s, %s) " +
                        "ON DUPLICATE KEY UPDATE name=VALUES(name), deleted=VALUES(deleted)", 123, "'fusion'", version, 0));
                conn.commit();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
    }

    private void exportPriceTypeStorePriceList(Connection conn, TransactionCashRegisterInfo transaction, Integer npp, String departmentNumber, int version) throws SQLException {
        conn.setAutoCommit(false);
        PreparedStatement ps = null;
        try {
            if (transaction.nppGroupMachinery != null) {
                ps = conn.prepareStatement(
                        "INSERT INTO pricetype_store_pricelist (pricetype, store, pricelist, version, deleted) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE pricelist=VALUES(pricelist), deleted=VALUES(deleted)");
                ps.setInt(1, 123); //pricetype
                ps.setString(2, String.valueOf(departmentNumber)); //store
                ps.setInt(3, npp); //pricelist
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

    private void exportVar(Connection conn, TransactionCashRegisterInfo transaction, boolean useBarcodeAsId, String weightCode, boolean appendBarcode, boolean sendZeroQuantityForWeightItems, int version) throws SQLException {
        if (transaction.itemsList != null) {
            conn.setAutoCommit(false);
            PreparedStatement ps = null;
            try {

                checkIndex(conn, "item", "var", "item");

                ps = conn.prepareStatement(
                        "INSERT INTO var (id, item, quantity, stock, version, deleted) VALUES (?, ?, ?, ?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE item=VALUES(item), quantity=VALUES(quantity), stock=VALUES(stock), deleted=VALUES(deleted)");
                for (CashRegisterItemInfo item : transaction.itemsList) {
                    String barcode = makeBarcode(removeCheckDigitFromBarcode(item.idBarcode, appendBarcode), item.passScalesItem, weightCode);
                    if (barcode != null && item.idItem != null) {
                        ps.setString(1, trim(barcode, 40)); //id
                        ps.setString(2, getId(item, useBarcodeAsId, appendBarcode)); //item
                        ps.setDouble(3, sendZeroQuantityForWeightItems && item.passScalesItem && !isNonWeight(item) ? 0 :
                                (item.amountBarcode != null ? item.amountBarcode.doubleValue() : 1)); //quantity
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
    }

    private void exportVarDeleteBarcode(Connection conn, List<CashRegisterItemInfo> barcodeList, int version) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO var (id, version, deleted) VALUES (?, ?, ?) " + "ON DUPLICATE KEY UPDATE deleted=VALUES(deleted)")) {
            for (CashRegisterItemInfo item : barcodeList) {
                ps.setString(1, trim(item.idBarcode, 40)); //id
                ps.setInt(2, version); //version
                ps.setInt(3, 1); //deleted
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private void exportProperties(Connection conn, TransactionCashRegisterInfo transaction, int version) throws SQLException, JSONException {
        if (transaction.info != null) {
            JSONObject infoJSON = new JSONObject(transaction.info).optJSONObject("ukm");
            if (infoJSON != null) {
                JSONArray properties = infoJSON.optJSONArray("properties");
                if (properties != null) {
                    try (PreparedStatement ps = conn.prepareStatement("INSERT INTO properties (code, name, flags, description, version, deleted) VALUES (?, ?, ?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE name=VALUES(name), flags=VALUES(flags), description=VALUES(description), deleted=VALUES(deleted)")) {
                        for (int i = 0; i < properties.length(); i++) {
                            JSONObject property = properties.getJSONObject(i);
                            ps.setString(1, property.getString("code"));
                            ps.setString(2, property.getString("name"));
                            ps.setInt(3, property.getInt("flags"));
                            ps.setString(4, property.optString("description"));
                            ps.setInt(5, version);
                            ps.setInt(6, 0);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                        conn.commit();
                    }
                }
            }
        }
    }

    private void exportPropertyValues(Connection conn, TransactionCashRegisterInfo transaction, int version) throws SQLException, JSONException {
        if (transaction.info != null) {
            JSONObject infoJSON = new JSONObject(transaction.info).optJSONObject("ukm");
            if (infoJSON != null) {
                JSONArray propertyValues = infoJSON.optJSONArray("property_values");
                if (propertyValues != null) {
                    try (PreparedStatement ps = conn.prepareStatement("INSERT INTO property_values (property_code, id, const, description, comment, version, deleted) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE const=VALUES(const), description=VALUES(description), comment=VALUES(comment), deleted=VALUES(deleted)")) {
                        for (int i = 0; i < propertyValues.length(); i++) {
                            JSONObject propertyValue = propertyValues.getJSONObject(i);
                            ps.setString(1, propertyValue.getString("property_code"));
                            ps.setInt(2, propertyValue.getInt("id"));
                            ps.setString(3, propertyValue.optString("const"));
                            ps.setString(4, propertyValue.getString("description"));
                            ps.setString(5, propertyValue.optString("comment"));
                            ps.setInt(6, version);
                            ps.setInt(7, 0);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                        conn.commit();
                    }
                }
            }
        }
    }

    private void exportItemPropertyValues(Connection conn, TransactionCashRegisterInfo transaction, boolean useBarcodeAsId, boolean appendBarcode, int version) throws SQLException, JSONException {
        if (transaction.itemsList != null) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO item_property_values (item_id, property_code, property_id, sequence, version, deleted) VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE sequence=VALUES(sequence), deleted=VALUES(deleted)")) {
                for (CashRegisterItemInfo item : transaction.itemsList) {
                    if (item.info != null) {
                        JSONObject infoJSON = new JSONObject(item.info).optJSONObject("ukm");
                        if (infoJSON != null) {
                            JSONArray itemPropertyValues = infoJSON.optJSONArray("item_property_values");
                            if (itemPropertyValues != null) {
                                for (int i = 0; i < itemPropertyValues.length(); i++) {
                                    JSONObject propertyValue = itemPropertyValues.getJSONObject(i);
                                    ps.setString(1, getId(item, useBarcodeAsId, appendBarcode));
                                    ps.setString(2, propertyValue.getString("property_code"));
                                    ps.setInt(3, propertyValue.getInt("property_id"));
                                    ps.setInt(4, propertyValue.getInt("sequence"));
                                    ps.setInt(5, version);
                                    ps.setInt(6, 0);
                                    ps.addBatch();
                                }
                            }
                        }
                    }
                }
                ps.executeBatch();
                conn.commit();
            }
        }
    }

    private void exportSignals(Connection conn, TransactionCashRegisterInfo transaction, int version, boolean ignoreSnapshot, int timeout, boolean wait) throws SQLException {
        conn.setAutoCommit(true);
        try (Statement statement = conn.createStatement()) {

            String sql = String.format("INSERT INTO `signal` (`signal`, version) VALUES('%s', '%s') ON DUPLICATE KEY UPDATE `signal`=VALUES(`signal`);", (transaction != null && transaction.snapshot && !ignoreSnapshot) ? "cumm" : "incr", version);
            statement.executeUpdate(sql);

            if (wait) {
                int count = 0;
                while (!waitForSignalExecution(conn, version)) {
                    if (count > (timeout / 5)) {
                        String message = String.format("data was sent to db but signal record %s was not deleted", version);
                        processTransactionLogger.error(message);
                        throw new RuntimeException(message);
                    } else {
                        count++;
                        processTransactionLogger.info(String.format("Waiting for deletion of signal record %s in base", version));
                        Thread.sleep(5000);
                    }
                }
            }

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private boolean waitForSignalExecution(Connection conn, int version) {
        try (Statement statement = conn.createStatement()) {
            String sql = "SELECT COUNT(*) FROM `signal` WHERE version = " + version;
            ResultSet resultSet = statement.executeQuery(sql);
            return !resultSet.next() || resultSet.getInt(1) == 0;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private Map<Long, SendTransactionBatch> waitSignals(Connection conn, Map<Integer, Long> versionMap, int timeout) {
        Map<Long, SendTransactionBatch> batchResult = new HashMap<>();
        try {
            int count = 0;
            while (!versionMap.isEmpty()) {
                versionMap = waitForSignalsExecution(conn, versionMap);
                if (!versionMap.isEmpty()) {
                    if (count > (timeout / 5)) {
                        String message = String.format("UKM transaction(s) %s: data was sent to db but signal record(s) %s was not deleted", versionMap.values(), versionMap.keySet());
                        processTransactionLogger.error(message);
                        for (Long transaction : versionMap.values()) {
                            batchResult.put(transaction, new SendTransactionBatch(new RuntimeException(message)));
                        }
                        break;
                    } else {
                        count++;
                        processTransactionLogger.info(String.format("UKM transaction(s) %s: waiting for deletion of signal record(s) %s in base", versionMap.values(), versionMap.keySet()));
                        Thread.sleep(5000);
                    }
                }
            }
        } catch (Exception e) {
            for (Long transaction : versionMap.values()) {
                batchResult.put(transaction, new SendTransactionBatch(new RuntimeException(e.getMessage())));
            }
        }
        return batchResult;
    }

    private Map<Integer, Long> waitForSignalsExecution(Connection conn, Map<Integer, Long> versionMap) throws SQLException {
        Statement statement = null;
        try {
            StringBuilder inVersions = new StringBuilder();
            for (Integer version : versionMap.keySet())
                inVersions.append((inVersions.length() == 0) ? "" : ",").append(version);

            statement = conn.createStatement();
            String sql = "SELECT version FROM `signal` WHERE version IN (" + inVersions + ")";
            ResultSet resultSet = statement.executeQuery(sql);
            Map<Integer, Long> result = new HashMap<>();
            while (resultSet.next()) {
                Integer version = resultSet.getInt(1);
                result.put(version, versionMap.get(version));
            }
            return result;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            if (statement != null)
                statement.close();
        }
    }

/*    private String getDepartmentNumber(TransactionCashRegisterInfo transaction, String section) {
       return section != null ? section : String.valueOf(transaction.departmentNumberGroupCashRegister);
    }*/

    private boolean isNonWeight(CashRegisterItemInfo item) {
        return item.shortNameUOM != null && item.shortNameUOM.toUpperCase().startsWith("ШТ");
    }

    private String makeBarcode(String idBarcode, boolean passScalesItem, String weightCode) {
        return idBarcode != null && idBarcode.length() == 5 && passScalesItem && weightCode != null ? (weightCode + idBarcode) : idBarcode;
    }

    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet) throws ClassNotFoundException {
        List<CashDocument> result = new ArrayList<>();

        UKM4MySQLSettings ukm4MySQLSettings = springContext.containsBean("ukm4MySQLSettings") ? (UKM4MySQLSettings) springContext.getBean("ukm4MySQLSettings") : null;
        Integer lastDaysCashDocument = ukm4MySQLSettings != null ? ukm4MySQLSettings.getLastDaysCashDocument() : null;

        Set<String> directorySet = new HashSet<>();
        Map<String, CashRegisterInfo> directoryCashRegisterMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (fitHandler(c)) {
                directorySet.add(c.directory);
                if (c.number != null)
                    directoryCashRegisterMap.put(c.directory + "_" + c.number, c);
            }
        }

        Class.forName("com.mysql.jdbc.Driver");

        for (String directory : directorySet) {
            List<CashDocument> cashDocumentList = new ArrayList<>();
            UKM4MySQLConnectionString params = new UKM4MySQLConnectionString(directory, 1);

            Connection conn = null;
            if (params.connectionString != null) {
                try {
                    conn = DriverManager.getConnection(params.connectionString, params.user, params.password);

                    checkIndex(conn, "shift", "moneyoperation", "cash_id, shift_number");

                    Statement statement = conn.createStatement();
                    String queryString = "select m.cash_id, m.id, m.date, m.type, m.amount, m.shift_number, s.id, s.date from moneyoperation m join shift s on m.shift_number = s.number AND m.cash_id = s.cash_id";
                    if (lastDaysCashDocument != null) {
                        Calendar c = Calendar.getInstance();
                        c.add(Calendar.DATE, -lastDaysCashDocument);
                        queryString += " where m.date >='" + new SimpleDateFormat("yyyyMMdd").format(c.getTime()) + "'";
                    }
                    ResultSet rs = statement.executeQuery(queryString);
                    Time midnight = new Time(23, 59, 59);
                    while (rs.next()) {
                        int nppMachinery = rs.getInt("m.cash_id");
                        CashRegisterInfo cashRegister = directoryCashRegisterMap.get(directory + "_" + nppMachinery);
                        if (cashRegister != null) {
                            String numberCashDocument = rs.getString("m.id");
                            Timestamp dateTimeMoneyOperation = rs.getTimestamp("m.date");
                            Timestamp dateTimeShift = rs.getTimestamp("s.date");
                            Date date = new Date(dateTimeShift.getTime());
                            Time time = new Time(dateTimeMoneyOperation.getTime());
                            Time twoAM = new Time(time.getTime());
                            twoAM.setHours(2);
                            twoAM.setMinutes(0);
                            twoAM.setSeconds(0);
                            if (time.getTime() < twoAM.getTime())
                                time = midnight;
                            int type = rs.getInt("m.type");
                            String numberZReport = rs.getString("s.id");
                            BigDecimal sum = type == 100 ? rs.getBigDecimal("m.amount") : type == 101 ? HandlerUtils.safeNegate(rs.getBigDecimal("m.amount")) : null;
                            if (sum != null) {
                                String idCashDocument = params.connectionString + "/" + nppMachinery + "/" + numberCashDocument;
                                if (!cashDocumentSet.contains(idCashDocument))
                                    cashDocumentList.add(new CashDocument(idCashDocument, numberCashDocument, date, time, cashRegister.numberGroup, nppMachinery, numberZReport, sum));
                            }
                        }
                    }

                } catch (SQLException e) {
                    sendSalesLogger.error("ukm4 mysql:", e);
                    e.printStackTrace();
                } finally {
                    try {
                        if (conn != null)
                            conn.close();
                    } catch (SQLException e) {
                        sendSalesLogger.error(logPrefix, e);
                    }
                }

                if (cashDocumentList.size() == 0)
                    sendSalesLogger.info(logPrefix + params.connectionString + " no CashDocuments found");
                else
                    sendSalesLogger.info(logPrefix + params.connectionString + String.format(" found %s CashDocument(s)", cashDocumentList.size()));
            }
            result.addAll(cashDocumentList);

        }
        return new CashDocumentBatch(result, null);
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) {
        if (!stopListInfo.exclude) {

            for (String directory : directorySet) {

                UKM4MySQLSettings ukm4MySQLSettings = springContext.containsBean("ukm4MySQLSettings") ? (UKM4MySQLSettings) springContext.getBean("ukm4MySQLSettings") : null;
                Integer timeout = ukm4MySQLSettings == null ? null : ukm4MySQLSettings.getTimeout();
                timeout = timeout == null ? 300 : timeout;
                boolean skipBarcodes = ukm4MySQLSettings == null || ukm4MySQLSettings.getSkipBarcodes() != null && ukm4MySQLSettings.getSkipBarcodes();
                boolean useBarcodeAsId = ukm4MySQLSettings == null || ukm4MySQLSettings.getUseBarcodeAsId() != null && ukm4MySQLSettings.getUseBarcodeAsId();
                boolean appendBarcode = ukm4MySQLSettings == null || ukm4MySQLSettings.getAppendBarcode() != null && ukm4MySQLSettings.getAppendBarcode();


                UKM4MySQLConnectionString params = new UKM4MySQLConnectionString(directory, 0);
                if (params.connectionString != null) {
                    Connection conn = null;
                    PreparedStatement ps = null;
                    try {
                        Class.forName("com.mysql.jdbc.Driver");
                        conn = DriverManager.getConnection(params.connectionString, params.user, params.password);

                        int version = getVersion(conn);
                        version++;
                        conn.setAutoCommit(false);
                        if (!skipBarcodes) {
                            processStopListLogger.info(logPrefix + "executing stopLists, table pricelist_var");

                            ps = conn.prepareStatement(
                                    "INSERT INTO pricelist_var (pricelist, var, price, version, deleted) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE price=VALUES(price), deleted=VALUES(deleted)");
                            for (ItemInfo item : stopListInfo.stopListItemMap.values()) {
                                if (item.idBarcode != null) {
                                    for (Integer nppGroupMachinery : stopListInfo.inGroupMachineryItemMap.keySet()) {
                                        ps.setInt(1, nppGroupMachinery); //pricelist
                                        ps.setString(2, item.idBarcode); //var
                                        ps.setBigDecimal(3, BigDecimal.ZERO); //price
                                        ps.setInt(4, version); //version
                                        ps.setInt(5, stopListInfo.exclude ? 0 : 1); //deleted
                                        ps.addBatch();
                                    }
                                }
                            }
                            ps.executeBatch();
                            conn.commit();
                        }

                        Map<Integer, Integer> overDepartNumberMap = new HashMap<>();
                        for(MachineryInfo machinery : stopListInfo.handlerMachineryMap.get(getClass().getName())) {
                            if(machinery.directory != null && machinery.directory.equals(directory)) {
                                overDepartNumberMap.put(machinery.numberGroup, ((CashRegisterInfo) machinery).overDepartNumber != null ? ((CashRegisterInfo) machinery).overDepartNumber : ((CashRegisterInfo) machinery).numberGroup);
                            }
                        }

                        processStopListLogger.info(logPrefix + "executing stopLists, table pricelist_items");
                        ps = conn.prepareStatement(
                                "INSERT INTO pricelist_items (pricelist, item, price, minprice, version, deleted) VALUES (?, ?, ?, ?, ?, ?) " +
                                        "ON DUPLICATE KEY UPDATE price=VALUES(price), minprice=VALUES(minprice), deleted=VALUES(deleted)");

                        for (ItemInfo item : stopListInfo.stopListItemMap.values()) {
                            if (item.idItem != null) {
                                for (Integer nppGroupMachinery : stopListInfo.inGroupMachineryItemMap.keySet()) {
                                    Integer priceList = overDepartNumberMap.get(nppGroupMachinery);
                                    if(priceList != null) {
                                        String idItem = getId(item, useBarcodeAsId, appendBarcode);
                                        processStopListLogger.info(logPrefix + String.format("table pricelist_items, nppGroupMachinery %s, item %s", priceList, idItem));
                                        ps.setInt(1, priceList); //pricelist
                                        ps.setString(2, idItem); //item
                                        ps.setBigDecimal(3, BigDecimal.ZERO); //price
                                        ps.setBigDecimal(4, BigDecimal.ZERO); //minprice
                                        ps.setInt(5, version); //version
                                        ps.setInt(6, 1); //deleted
                                        ps.addBatch();
                                    }
                                }
                            }
                        }
                        ps.executeBatch();
                        conn.commit();

                        processStopListLogger.info(logPrefix + "executing stopLists, table signal");
                        exportSignals(conn, null, version, true, timeout, true);

                    } catch (Exception e) {
                        processStopListLogger.error("ukm4 mysql:", e);
                        e.printStackTrace();
                    } finally {
                        try {
                            if (ps != null)
                                ps.close();
                            if (conn != null)
                                conn.close();
                        } catch (SQLException e) {
                            processStopListLogger.error("ukm4 mysql:", e);
                        }
                    }
                }

            }
        }
    }

    @Override
    public boolean sendDeleteBarcodeInfo(DeleteBarcodeInfo deleteBarcodeInfo) {

        try {
            if (!deleteBarcodeInfo.barcodeList.isEmpty()) {
                Class.forName("com.mysql.jdbc.Driver");

                UKM4MySQLSettings ukm4MySQLSettings = springContext.containsBean("ukm4MySQLSettings") ? (UKM4MySQLSettings) springContext.getBean("ukm4MySQLSettings") : null;
                Integer timeout = ukm4MySQLSettings == null ? null : ukm4MySQLSettings.getTimeout();
                timeout = timeout == null ? 300 : timeout;
                UKM4MySQLConnectionString params = new UKM4MySQLConnectionString(deleteBarcodeInfo.directoryGroupMachinery, 0);

                if (params.connectionString == null) {
                    deleteBarcodeLogger.error("No importConnectionString in ukm4MySQLSettings found");
                } else {
                    try (Connection conn = DriverManager.getConnection(params.connectionString, params.user, params.password)) {
                        conn.setAutoCommit(false);
                        Integer version = getVersion(conn) + 1;

                        deleteBarcodeLogger.info(logPrefix + "deleteBarcode, table var");
                        exportVarDeleteBarcode(conn, deleteBarcodeInfo.barcodeList, version);

                        processTransactionLogger.info(logPrefix + "deleteBarcode, table signal");
                        exportSignals(conn, null, version, false, timeout, true);
                    }
                }
            }
        } catch (ClassNotFoundException | SQLException e) {
            throw Throwables.propagate(e);
        }
        return true;
    }

    @Override
    public UKM4MySQLSalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) {

        UKM4MySQLSalesBatch salesBatch = null;

        String weightCode = null;
        Map<Integer, CashRegisterInfo> machineryMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (fitHandler(c)) {
                if (c.number != null && c.numberGroup != null)
                    machineryMap.put(c.number, c);
                if (c.weightCodeGroupCashRegister != null) {
                    weightCode = c.weightCodeGroupCashRegister;
                }
            }
        }

        UKM4MySQLSettings ukm4MySQLSettings = springContext.containsBean("ukm4MySQLSettings") ? (UKM4MySQLSettings) springContext.getBean("ukm4MySQLSettings") : null;
        Set<Integer> cashPayments = ukm4MySQLSettings == null ? new HashSet<>() : parsePayments(ukm4MySQLSettings.getCashPayments());
        Set<Integer> cardPayments = ukm4MySQLSettings == null ? new HashSet<>() : parsePayments(ukm4MySQLSettings.getCardPayments());
        Set<Integer> giftCardPayments = ukm4MySQLSettings == null ? new HashSet<>() : parsePayments(ukm4MySQLSettings.getGiftCardPayments());
        List<String> giftCardList = ukm4MySQLSettings == null ? new ArrayList<>() : ukm4MySQLSettings.getGiftCardList();
        boolean useBarcodeAsId = ukm4MySQLSettings == null || ukm4MySQLSettings.getUseBarcodeAsId() != null && ukm4MySQLSettings.getUseBarcodeAsId();
        boolean appendBarcode = ukm4MySQLSettings == null || ukm4MySQLSettings.getAppendBarcode() != null && ukm4MySQLSettings.getAppendBarcode();
        boolean useShiftNumberAsNumberZReport = ukm4MySQLSettings != null && ukm4MySQLSettings.isUseShiftNumberAsNumberZReport();
        boolean zeroPaymentForZeroSumReceipt = ukm4MySQLSettings != null && ukm4MySQLSettings.isZeroPaymentForZeroSumReceipt();

        UKM4MySQLConnectionString params = new UKM4MySQLConnectionString(directory, 1);

        try {

            Class.forName("com.mysql.jdbc.Driver");

            if (params.connectionString == null) {
                processTransactionLogger.error("No connectionString found");
            } else {

                Connection conn = null;

                try {
                    sendSalesLogger.info(String.format(logPrefix + "connecting to %s", params.connectionString));
                    conn = DriverManager.getConnection(params.connectionString, params.user, params.password);
                    checkIndices(conn);
                    salesBatch = readSalesInfoFromSQL(conn, weightCode, machineryMap, cashPayments, cardPayments, giftCardPayments,
                            giftCardList, useBarcodeAsId, appendBarcode, useShiftNumberAsNumberZReport, zeroPaymentForZeroSumReceipt, directory);

                } finally {
                    if (conn != null)
                        conn.close();
                }
            }
        } catch (Exception e) {
            sendSalesLogger.error("UKM: failed to read sales. ConnectionString: " + params.connectionString, e);
            throw Throwables.propagate(e);
        }
        return salesBatch;
    }

    private Set<Integer> parsePayments(String payments) {
        Set<Integer> paymentsSet = new HashSet<>();
        try {
            if (payments != null && !payments.isEmpty()) {
                for (String payment : payments.split(",")) {
                    paymentsSet.add(Integer.parseInt(payment.trim()));
                }
            }
        } catch (Exception e) {
            sendSalesLogger.error("UKM: invalid payment settings: " + payments);
        }
        return paymentsSet;
    }

    private Map<String, Payment> readPaymentMap(Connection conn, Set<Integer> cashPayments,
                                                Set<Integer> cardPayments, Set<Integer> giftCardPayments, List<String> giftCardList) {

        Map<String, Payment> paymentMap = new HashMap<>();

        try (Statement statement = conn.createStatement()) {
            //sql_no_cache is workaround of the bug: https://bugs.mysql.com/bug.php?id=31353
            String query = "select sql_no_cache p.cash_id, p.receipt_header, p.payment_id, p.amount, r.type, p.card_number " + "from receipt_payment p left join receipt r on p.cash_id = r.cash_id and p.receipt_header = r.id " + "where r.ext_processed = 0 AND r.result = 0 AND p.type = 0"; // type 3 это сдача, type 2 - аннулирование
            ResultSet rs = statement.executeQuery(query);
            while (rs.next()) {
                Integer cash_id = rs.getInt(1); //cash_id
                Integer idReceipt = rs.getInt(2); //receipt_header
                String key = cash_id + "/" + idReceipt;
                Integer paymentType = rs.getInt(3);//payment_id
                if (cashPayments.contains(paymentType)) //нал
                    paymentType = 0;
                else if (cardPayments.contains(paymentType)) //безнал
                    paymentType = 1;
                else if (giftCardPayments.contains(paymentType)) //сертификат
                    paymentType = 2;
                BigDecimal amount = rs.getBigDecimal(4);
                Integer receiptType = rs.getInt(5); //r.type
                boolean isReturn = receiptType == 1 || receiptType == 4 || receiptType == 9;
                amount = isReturn ? amount.negate() : amount;
                String giftCard = rs.getString(6); //p.card_number
                if (giftCard.isEmpty())
                    giftCard = null;

                if (giftCard != null && giftCardList != null) {
                    for (String prefix : giftCardList) {
                        if (giftCard.startsWith(prefix)) {
                            paymentType = 2;
                            break;
                        }
                    }
                }
                //если тип оплаты не найден, считаем налом
                if(paymentType != 0 && paymentType != 1 && paymentType != 2)
                    paymentType = 0;

                Payment paymentEntry = paymentMap.get(key);
                if (paymentEntry == null)
                    paymentEntry = new Payment();
                if (paymentType == 0)
                    paymentEntry.sumCash = HandlerUtils.safeAdd(paymentEntry.sumCash, amount);
                else if (paymentType == 1) {
                    paymentEntry.sumCard = HandlerUtils.safeAdd(paymentEntry.sumCard, amount);
                } else { //paymentType == 2
                    BigDecimal sumGiftCard = paymentEntry.sumGiftCardMap.get(giftCard);
                    paymentEntry.sumGiftCardMap.put(giftCard, HandlerUtils.safeAdd(sumGiftCard, amount));
                }
                paymentMap.put(key, paymentEntry);
            }
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
        return paymentMap;
    }

    private void checkIndices(Connection conn) throws SQLException {
        checkIndex(conn, "ext_processed_index", "receipt", "ext_processed");
        checkIndex(conn, "receipt", "receipt_item", "cash_id, receipt_header");
        checkIndex(conn, "item", "receipt_item_properties", "cash_id, receipt_item");
        checkIndex(conn, "receipt", "receipt_payment", "cash_id, receipt_header");
    }

    private void checkIndex(Connection conn, String indexName, String tableName, String fields) throws SQLException {
        try(Statement selectStatement = conn.createStatement()) {
            String query = String.format("SELECT COUNT(1) IndexIsThere FROM INFORMATION_SCHEMA.STATISTICS WHERE table_schema=DATABASE() AND table_name='%s' AND index_name='%s'", tableName, indexName);
            ResultSet rs = selectStatement.executeQuery(query);

            while (rs.next()) {
                boolean indexExists = rs.getInt(1) > 0;
                if (!indexExists) {
                    try(Statement createStatement = conn.createStatement()) {
                        String command = String.format("CREATE INDEX %s ON %s(%s);", indexName, tableName, fields);
                        sendSalesLogger.info(logPrefix + command);
                        createStatement.execute(command);
                    }
                }
            }
        }
    }

    private UKM4MySQLSalesBatch readSalesInfoFromSQL(Connection conn, String weightCode, Map<Integer, CashRegisterInfo> machineryMap,
                                                     Set<Integer> cashPayments, Set<Integer> cardPayments, Set<Integer> giftCardPayments,
                                                     List<String> giftCardList, boolean useBarcodeAsId, boolean appendBarcode,
                                                     boolean useShiftNumberAsNumberZReport, boolean zeroPaymentForZeroSumReceipt,
                                                     String directory) {
        List<SalesInfo> salesInfoList = new ArrayList<>();

        //Map<Integer, String> loginMap = readLoginMap(conn);
        Set<Pair<Integer, Integer>> receiptSet = new HashSet<>();
        Set<String> usedBarcodes = new HashSet<>();

        try (Statement statement = conn.createStatement()) {
            String query = "SELECT sql_no_cache i.store, i.cash_number, i.cash_id, i.id, i.receipt_header, i.var, i.item, i.total_quantity, i.price, i.total," +
                    " i.position, i.real_amount, i.stock_id, r.type, r.shift_open, r.global_number, r.date, r.cash_id, r.id, r.login, s.date, rip.value, l.user_id, l.user_name, s.number, r.client_card_code " +
                    " FROM receipt_item AS i" +
                    " JOIN receipt AS r ON i.receipt_header = r.id AND i.cash_id = r.cash_id" +
                    " JOIN shift AS s ON r.shift_open = s.id AND r.cash_id = s.cash_id" +
                    " LEFT JOIN receipt_item_properties AS rip ON i.cash_id = rip.cash_id AND i.id = rip.receipt_item AND rip.code = '$GiftCard_Number$' " +
                    " LEFT JOIN login AS l ON r.cash_id = l.cash_id AND r.login = l.id  "+
                    " WHERE r.ext_processed = 0 AND r.result = 0 AND i.type = 0";
            ResultSet rs = statement.executeQuery(query);

            Map<String, Payment> paymentMap = readPaymentMap(conn, cashPayments, cardPayments, giftCardPayments, giftCardList);
            while (rs.next()) {

                //Integer nppGroupMachinery = Integer.parseInt(rs.getString(1)); //i.store
                //Integer nppMachinery = rs.getInt(2); //i.cash_number

                Integer cash_id = rs.getInt(3); //i.cash_id
                CashRegisterInfo cashRegister = machineryMap.get(cash_id);
                Integer nppGroupMachinery = cashRegister == null ? null : cashRegister.numberGroup;

                //Integer id = rs.getInt(4); //i.id
                Integer idReceipt = rs.getInt(5); //i.receipt_header
                String idBarcode = useBarcodeAsId ? rs.getString(7) : rs.getString(6); //i.item : i.var
                idBarcode = appendBarcode ? appendCheckDigitToBarcode(idBarcode, 5) : idBarcode;
                if (idBarcode != null && weightCode != null && (idBarcode.length() == 13 || idBarcode.length() == 7) && idBarcode.startsWith(weightCode))
                    idBarcode = idBarcode.substring(2, 7);
                String idItem = useBarcodeAsId && appendBarcode ? appendCheckDigitToBarcode(rs.getString(7), 5) : rs.getString(7); //i.item
                BigDecimal totalQuantity = rs.getBigDecimal(8); //i.total_quantity
                BigDecimal price = rs.getBigDecimal(9); //i.price
                BigDecimal sum = rs.getBigDecimal(10); //i.total
                Integer position = rs.getInt(11) + 1;
                BigDecimal realAmount = rs.getBigDecimal(12); //i.real_amount
                String idSection = rs.getString(13);

                Payment paymentEntry = paymentMap.get(cash_id + "/" + idReceipt);
                if(paymentEntry == null && zeroPaymentForZeroSumReceipt) {
                    paymentEntry = new Payment(BigDecimal.ZERO);
                }
                if (paymentEntry != null && totalQuantity != null) {
                    Integer receiptType = rs.getInt(14); //r.type
                    boolean isSale = receiptType == 0 || receiptType == 8;
                    boolean isReturn = receiptType == 1 || receiptType == 4 || receiptType == 9;
                    String numberZReport = useShiftNumberAsNumberZReport ? String.valueOf(rs.getInt(25)) : rs.getString(15); //s.number or r.shift_open
                    Integer numberReceipt = rs.getInt(16); //r.global_number
                    Date dateReceipt = rs.getDate(17); // r.date
                    Time timeReceipt = rs.getTime(17); //r.date
                    //Integer login = rs.getInt(18); //r.login
                    Date dateZReport = rs.getDate(21); //s.date
                    Time timeZReport = rs.getTime(21); //s.date
                    //String idEmployee = loginMap.get(login);

                    String giftCardValue = rs.getString(22); //rip.value
                    String idEmployee = String.valueOf(rs.getInt(23)); //l.user_id
                    String lastNameContact = rs.getString(24); //l.user_name

                    String discountCard = rs.getString("client_card_code"); //26

                    boolean isGiftCard = giftCardValue != null && !giftCardValue.isEmpty();
                    if (isGiftCard)
                        idBarcode = giftCardValue;
                    else {
                        isGiftCard = giftCardList.contains(idBarcode);
                        if (isGiftCard) {
                            long dateTimeReceipt = rs.getTimestamp(17) == null ? 0 : rs.getTimestamp(17).getTime();
                            int count = 1;
                            while (usedBarcodes.contains(idBarcode + "/" + dateTimeReceipt + "/" + count)) {
                                count++;
                            }
                            idBarcode = idBarcode + "/" + dateTimeReceipt + "/" + count;
                            usedBarcodes.add(idBarcode);
                        }
                    }

                    Map<String, GiftCard> sumGiftCardMap = new HashMap<>();
                    for (Map.Entry<String, BigDecimal> entry : paymentEntry.sumGiftCardMap.entrySet()) {
                        sumGiftCardMap.put(entry.getKey(), new GiftCard(entry.getValue()));
                    }

                    totalQuantity = isSale ? totalQuantity : isReturn ? totalQuantity.negate() : null;
                    BigDecimal discountSumReceiptDetail = HandlerUtils.safeSubtract(sum, realAmount);
                    if (totalQuantity != null) {
                        if (cashRegister == null || cashRegister.startDate == null || (dateReceipt != null && dateReceipt.compareTo(cashRegister.startDate) >= 0)) {
                            salesInfoList.add(getSalesInfo(isGiftCard, false, nppGroupMachinery, cash_id, numberZReport,
                                    dateZReport, timeZReport, numberReceipt, dateReceipt, timeReceipt, idEmployee,
                                    null, lastNameContact, paymentEntry.sumCard, paymentEntry.sumCash, sumGiftCardMap, null, idBarcode, idItem, null, null, totalQuantity,
                                    price, isSale ? realAmount : realAmount.negate(), null, discountSumReceiptDetail, null, discountCard,
                                    position, null, idSection, false, cashRegister));
                        }
                        receiptSet.add(Pair.create(idReceipt, cash_id));
                    }
                }
            }
            if (salesInfoList.size() > 0)
                sendSalesLogger.info(String.format(logPrefix + "found %s records", salesInfoList.size()));
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
        return new UKM4MySQLSalesBatch(salesInfoList, receiptSet, directory);
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList,
                                 Set<Long> succeededRequests, Map<Long, Throwable> failedRequests, Map<Long, Throwable> ignoredRequests) {
        for (RequestExchange requestExchange : requestExchangeList) {
            for (String directory : getDirectorySet(requestExchange)) {
                Connection conn = null;
                Statement statement = null;
                try {
                    UKM4MySQLConnectionString params = new UKM4MySQLConnectionString(directory, 1);
                    if (params.connectionString != null) {
                        Class.forName("com.mysql.jdbc.Driver");
                        conn = DriverManager.getConnection(params.connectionString, params.user, params.password);
                        String dateFrom = new SimpleDateFormat("yyyy-MM-dd").format(requestExchange.dateFrom);
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(requestExchange.dateTo);
                        cal.add(Calendar.DATE, 1);
                        machineryExchangeLogger.info(logPrefix + "RequestSalesInfo: dateTo is " + cal.getTime());
                        String dateTo = new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());

                        Set<CashRegisterInfo> cashRegisterSet = getCashRegisterSet(requestExchange, false);
                        StringBuilder cashIdWhere = null;
                        if (!cashRegisterSet.isEmpty()) {
                            cashIdWhere = new StringBuilder("AND cash_id IN (");
                            for (CashRegisterInfo cashRegister : cashRegisterSet) {
                                cashIdWhere.append(cashRegister.number == null ? "" : (cashRegister.number + ","));
                            }
                            cashIdWhere = new StringBuilder(cashIdWhere.substring(0, cashIdWhere.length() - 1) + ")");
                        }

                        statement = conn.createStatement();
                        String query = String.format("UPDATE receipt SET ext_processed = 0 WHERE date >= '%s' AND date <= '%s'", dateFrom, dateTo) +
                                (cashIdWhere == null ? "" : cashIdWhere.toString());
                        machineryExchangeLogger.info(logPrefix + "RequestSalesInfo: " + query);
                        statement.execute(query);
                        succeededRequests.add(requestExchange.requestExchange);
                    }
                } catch (Exception e) {
                    failedRequests.put(requestExchange.requestExchange, e);
                    e.printStackTrace();
                } finally {
                    try {
                        if (statement != null)
                            statement.close();
                        if (conn != null)
                            conn.close();
                    } catch (SQLException ignored) {
                    }
                }
            }
        }
    }

    @Override
    public void finishReadingSalesInfo(UKM4MySQLSalesBatch salesBatch) {

        for (String directory : salesBatch.directorySet) {

            UKM4MySQLConnectionString params = new UKM4MySQLConnectionString(directory, 1);
            if (params.connectionString != null && salesBatch.receiptSet != null) {

                Connection conn = null;
                PreparedStatement ps = null;

                try {
                    conn = DriverManager.getConnection(params.connectionString, params.user, params.password);

                    conn.setAutoCommit(false);
                    ps = conn.prepareStatement("UPDATE receipt SET ext_processed = 1 WHERE id = ? AND cash_id = ?");
                    for (Pair<Integer, Integer> receiptEntry : salesBatch.receiptSet) {
                        ps.setInt(1, receiptEntry.first); //id
                        ps.setInt(2, receiptEntry.second); //cash_id
                        ps.addBatch();
                    }
                    ps.executeBatch();
                    conn.commit();

                } catch (SQLException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (ps != null)
                            ps.close();
                        if (conn != null)
                            conn.close();
                    } catch (SQLException ignored) {
                    }
                }
            }
        }
    }

    private String parseGroup(String idItemGroup) {
        try {
            return idItemGroup == null || idItemGroup.equals("Все") ? "0" : idItemGroup;
        } catch (Exception e) {
            return "0";
        }
    }

    private String appendCheckDigitToBarcode(String barcode, Integer minLength) {

        if (barcode == null || (minLength != null && barcode.length() < minLength))
            return null;

        try {
            if (barcode.length() == 11) {
                return appendEAN13("0" + barcode).substring(1, 13);
            } else if (barcode.length() == 12) {
                return appendEAN13(barcode);
            } else if (barcode.length() == 7) {  //EAN-8
                int checkSum = 0;
                for (int i = 0; i <= 6; i = i + 2) {
                    checkSum += Integer.parseInt(String.valueOf(barcode.charAt(i))) * 3;
                    checkSum += i == 6 ? 0 : Integer.parseInt(String.valueOf(barcode.charAt(i + 1)));
                }
                checkSum %= 10;
                if (checkSum != 0)
                    checkSum = 10 - checkSum;
                return barcode.concat(String.valueOf(checkSum));
            } else
                return barcode;
        } catch (Exception e) {
            return barcode;
        }
    }

    private String removeCheckDigitFromBarcode(String barcode, boolean appendBarcode) {
        if (appendBarcode && barcode != null && (barcode.length() == 13 || barcode.length() == 12 || barcode.length() == 8)) {
            return barcode.substring(0, barcode.length() - 1);
        } else
            return barcode;
    }

    private String getId(ItemInfo item, boolean useBarcodeAsId, boolean appendBarcode) {
        return trim(useBarcodeAsId ? removeCheckDigitFromBarcode(item.idBarcode, appendBarcode) : item.idItem, 40);
    }

    private String appendEAN13(String barcode) {
        int checkSum = 0;
        for (int i = 0; i <= 10; i = i + 2) {
            checkSum += Integer.parseInt(String.valueOf(barcode.charAt(i)));
            checkSum += Integer.parseInt(String.valueOf(barcode.charAt(i + 1))) * 3;
        }
        checkSum %= 10;
        if (checkSum != 0)
            checkSum = 10 - checkSum;
        return barcode.concat(String.valueOf(checkSum));
    }

    private class Payment {
        BigDecimal sumCash;
        BigDecimal sumCard;
        Map<String, BigDecimal> sumGiftCardMap;

        Payment() {
            this.sumGiftCardMap = new HashMap<>();
        }

        public Payment(BigDecimal sumCash) {
            this.sumCash = sumCash;
            this.sumGiftCardMap = new HashMap<>();
        }
    }
}
