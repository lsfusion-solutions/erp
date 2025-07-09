package equ.clt.handler.ukm4mysql;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import equ.api.stoplist.StopListInfo;
import equ.clt.handler.DefaultCashRegisterHandler;
import equ.clt.handler.HandlerUtils;
import lsfusion.base.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static equ.clt.EquipmentServer.*;
import static equ.clt.handler.HandlerUtils.safeDivide;
import static equ.clt.handler.HandlerUtils.trim;
import static lsfusion.base.BaseUtils.nvl;

public class UKM4MySQLHandler extends DefaultCashRegisterHandler<UKM4MySQLSalesBatch, UKM4MySQLCashDocumentBatch> {

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

                UKM4MySQLSettings ukm4MySQLSettings = springContext.containsBean("ukm4MySQLSettings") ? (UKM4MySQLSettings) springContext.getBean("ukm4MySQLSettings") : new UKM4MySQLSettings();
                Integer timeout = ukm4MySQLSettings.getTimeout();
                boolean skipItems = ukm4MySQLSettings.getSkipItems() != null && ukm4MySQLSettings.getSkipItems();
                boolean skipClassif = ukm4MySQLSettings.getSkipClassif() != null && ukm4MySQLSettings.getSkipClassif();
                boolean skipBarcodes = ukm4MySQLSettings.getSkipBarcodes() != null && ukm4MySQLSettings.getSkipBarcodes();
                boolean skipPriceListTables = ukm4MySQLSettings.isSkipPriceListTables();
                boolean sendItemsMark = ukm4MySQLSettings.isSendItemsMark();
                boolean sendItemsGTIN = ukm4MySQLSettings.isSendItemsGTIN();
                boolean useBarcodeAsId = ukm4MySQLSettings.getUseBarcodeAsId() != null && ukm4MySQLSettings.getUseBarcodeAsId();
                boolean appendBarcode = ukm4MySQLSettings.getAppendBarcode() != null && ukm4MySQLSettings.getAppendBarcode();
                boolean exportTaxes = ukm4MySQLSettings.isExportTaxes();
                boolean sendZeroQuantityForWeightItems = ukm4MySQLSettings.getSendZeroQuantityForWeightItems() != null && ukm4MySQLSettings.getSendZeroQuantityForWeightItems();
                boolean sendZeroQuantityForSplitItems = ukm4MySQLSettings.getSendZeroQuantityForSplitItems() != null && ukm4MySQLSettings.getSendZeroQuantityForSplitItems();
                List<String> forceGroups = ukm4MySQLSettings.getForceGroupsList();
                boolean tareWeightFieldInVarTable = ukm4MySQLSettings.isTareWeightFieldInVarTable();
                boolean usePieceCode = ukm4MySQLSettings.isUsePieceCode();

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

                                String pieceCode = null;
                                String section = null;
                                for (CashRegisterInfo cashRegister : transaction.machineryInfoList) {
                                    if(cashRegister.pieceCodeGroupCashRegister != null)
                                        pieceCode = cashRegister.pieceCodeGroupCashRegister;
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
                                            exportClassif(conn, transaction, forceGroups, version);
                                        }

                                        if (exportTaxes) {
                                            processTransactionLogger.info(logPrefix + String.format("transaction %s, table taxes", transaction.id));
                                            exportTaxes(conn, transaction, version);
                                            exportTaxGroups(conn, transaction, version);
                                        }

                                        processTransactionLogger.info(logPrefix + String.format("transaction %s, table items", transaction.id));
                                        exportItems(conn, transaction, useBarcodeAsId, appendBarcode, exportTaxes, version);

                                        if (sendItemsMark) {
                                            processTransactionLogger.info(logPrefix + String.format("transaction %s, table items_egais", transaction.id));
                                            exportItemsMark(conn, transaction, useBarcodeAsId, appendBarcode, version);
                                        }
                                        if (sendItemsGTIN) {
                                            processTransactionLogger.info(logPrefix + String.format("transaction %s, table for GTIN", transaction.id));
                                            exportItemsGTIN(conn, transaction, useBarcodeAsId, appendBarcode, version);
                                        }

                                        processTransactionLogger.info(logPrefix + String.format("transaction %s, table items_stocks", transaction.id));
                                        exportItemsStocks(conn, transaction, section/*departmentNumber*/, version);

                                        processTransactionLogger.info(logPrefix + String.format("transaction %s, table stocks", transaction.id));
                                        exportStocks(conn, transaction, section/*departmentNumber*/, version);

                                        if (!skipPriceListTables) {

                                            processTransactionLogger.info(logPrefix + String.format("transaction %s, table pricelist", transaction.id));
                                            exportPriceList(conn, transaction, nppGroupMachinery, version);

                                            processTransactionLogger.info(logPrefix + String.format("transaction %s, table pricetype", transaction.id));
                                            exportPriceType(conn, version);

                                            processTransactionLogger.info(logPrefix + String.format("transaction %s, table pricetype_store_pricelist", transaction.id));
                                            exportPriceTypeStorePriceList(conn, transaction, nppGroupMachinery, section/*departmentNumber*/, version);

                                        }

                                        processTransactionLogger.info(logPrefix + String.format("transaction %s, table var", transaction.id));
                                        exportVar(conn, transaction, useBarcodeAsId, weightCode, pieceCode, usePieceCode, appendBarcode,
                                                sendZeroQuantityForWeightItems, sendZeroQuantityForSplitItems, tareWeightFieldInVarTable, version);

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
                                        exportPriceListVar(conn, transaction, nppGroupMachinery, weightCode, pieceCode, usePieceCode, version);
                                    }

                                    processTransactionLogger.info(logPrefix + String.format("transaction %s, table pricelist_items", transaction.id));
                                    exportPriceListItems(conn, transaction, nppGroupMachinery, useBarcodeAsId, appendBarcode, version);

                                    processTransactionLogger.info(logPrefix + String.format("transaction %s, table signal (%s)", transaction.id,
                                            transaction.snapshot ? "cumm" : "incr"));
                                    exportSignals(conn, transaction, version, false, timeout, false);
                                    versionTransactionMap.put(version, transaction.id);

                                } catch (Exception e) {
                                    processTransactionLogger.error(logPrefix + "process transaction failed", e);
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

    private void exportClassif(Connection conn, TransactionCashRegisterInfo transaction, List<String> forceGroups, int version) throws SQLException {
        if (transaction.itemsList != null || !forceGroups.isEmpty()) {

            Set<String> usedGroups = new HashSet<>();

            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO classif (id, owner, name, version, deleted) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE owner=VALUES(owner), name=VALUES(name), deleted=VALUES(deleted)")) {

                for (CashRegisterItem item : transaction.itemsList) {
                    forceGroups.remove(item.extIdItemGroup);
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

                for (String forceGroup : forceGroups) {
                    List<ItemGroup> itemGroupList = transaction.itemGroupMap.get(forceGroup);
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
                for (CashRegisterItem item : transaction.itemsList) {
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

                for (CashRegisterItem item : transaction.itemsList) {
                    ps.setString(1, getId(item, useBarcodeAsId, appendBarcode)); //id
                    ps.setString(2, trim(item.name, "", 40)); //name
                    ps.setString(3, item.description == null ? "" : item.description); //descr
                    ps.setString(4, trim(item.shortNameUOM, "", 40)); //measure
                    ps.setInt(5, item.passScalesItem ? (isPieceUOM(item) ? 0 : 3) : (item.splitItem ? 3 : 0)); //measprec
                    ps.setString(6, parseGroup(item.extIdItemGroup)); //classif
                    ps.setInt(7, 1); //prop - признак товара ?
                    ps.setString(8, trim(item.description, "", 100)); //summary
                    ps.setDate(9, localDateToSqlDate(item.expiryDate)); //exp_date
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

    private int getTax(CashRegisterItem item) {
        return item.vat != null ? item.vat.intValue() : 0;
    }

    private void exportItemsStocks(Connection conn, TransactionCashRegisterInfo transaction, String departmentNumber, int version) throws SQLException {
        if (transaction.itemsList != null) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO items_stocks (store, item, stock, version, deleted) VALUES (?, ?, ?, ?, ?)" + "ON DUPLICATE KEY UPDATE deleted=VALUES(deleted)")) {

                for (CashRegisterItem item : transaction.itemsList) {
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

    private void exportItemsMark(Connection conn, TransactionCashRegisterInfo transaction, boolean useBarcodeAsId, boolean appendBarcode, int version) throws SQLException {
        if (transaction.itemsList != null) {
            conn.setAutoCommit(false);
            try (PreparedStatement p = conn.prepareStatement("INSERT INTO properties (code, name, flags, version, deleted) VALUES (?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE name=VALUES(name), flags=VALUES(flags), deleted=VALUES(deleted)");
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO items_egais (id, egais, sub_excise, crpt_not_unique, version, deleted) VALUES (?, ?, ?, ?, ?, ?)" +
                         "ON DUPLICATE KEY UPDATE egais=VALUES(egais), deleted=VALUES(deleted)");
                 PreparedStatement pv = conn.prepareStatement("INSERT INTO property_values (property_code, id, const, description, version, deleted) VALUES (?, ?, ?, ?, ?, ?) " +
                         "ON DUPLICATE KEY UPDATE const=VALUES(const), description=VALUES(description), comment=VALUES(comment), deleted=VALUES(deleted)");
                 PreparedStatement vs = conn.prepareStatement("INSERT INTO item_property_values (item_id, property_code, property_id, sequence, version, deleted) VALUES (?, ?, ?, ?, ?, ?) " +
                         "ON DUPLICATE KEY UPDATE sequence=VALUES(sequence), deleted=VALUES(deleted)")) {

                p.setString(1, "RB_Mark"); //code
                p.setString(2, "Маркировка УКЗ"); //name
                p.setInt(3, 0); //flags
                p.setInt(4, version);
                p.setInt(5, 0);
                p.addBatch();

                pv.setString(1, "RB_Mark"); //property_code
                pv.setInt(2, 888); //id
                pv.setString(3, "RB_Mark"); //const
                pv.setString(4, "<question><const>RB_Mark</const><displayname>RB_Mark</displayname></question>"); //description
                pv.setInt(5, version);
                pv.setInt(6, 0);
                pv.addBatch();

                for (CashRegisterItem item : transaction.itemsList) {

                    JSONObject extraInfo = item.extraInfo != null && !item.extraInfo.isEmpty() ? new JSONObject(item.extraInfo) : null;
                    String lotType = extraInfo != null ? extraInfo.optString("lottype") : null;
                    int ukz = extraInfo != null && extraInfo.optBoolean("ukz") || "ukz".equals(lotType) ? 0 : 1;
                    boolean optional = extraInfo != null && extraInfo.has("optionalLot");

                    ps.setString(1, getId(item, useBarcodeAsId, appendBarcode)); //store
                    ps.setInt(2, lotType != null && !lotType.isEmpty() ? (optional ? 4: 3) : 0); //item
                    ps.setInt(3, 0); //stock
                    ps.setInt(4, 0); //stock
                    ps.setInt(5, version); //version
                    ps.setInt(6, 0); //deleted
                    ps.addBatch();

                    vs.setString(1, getId(item, useBarcodeAsId, appendBarcode));
                    vs.setString(2, "RB_Mark");
                    vs.setInt(3, 888);
                    vs.setInt(4, 0);
                    vs.setInt(5, version);
                    vs.setInt(6, ukz);
                    vs.addBatch();

                }
                p.executeBatch();
                pv.executeBatch();
                ps.executeBatch();
                vs.executeBatch();
                conn.commit();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

private void exportItemsGTIN(Connection conn, TransactionCashRegisterInfo transaction, boolean useBarcodeAsId, boolean appendBarcode, int version) throws SQLException {
        if (transaction.itemsList != null) {
            conn.setAutoCommit(false);
            try (PreparedStatement p = conn.prepareStatement("INSERT INTO properties (code, name, flags, version, deleted) VALUES (?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE name=VALUES(name), flags=VALUES(flags), deleted=VALUES(deleted)");
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO property_values (property_code, id, const, description, version, deleted) VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE const=VALUES(const), description=VALUES(description), comment=VALUES(comment), deleted=VALUES(deleted)");
                 PreparedStatement vs = conn.prepareStatement("INSERT INTO item_property_values (item_id, property_code, property_id, sequence, version, deleted) VALUES (?, ?, ?, ?, ?, ?) " +
                         "ON DUPLICATE KEY UPDATE sequence=VALUES(sequence), deleted=VALUES(deleted)")) {

                p.setString(1, "GTIN"); //code
                p.setString(2, "GTIN"); //name
                p.setInt(3, 0); //flags
                p.setInt(4, version);
                p.setInt(5, 0);
                p.addBatch();

                for (CashRegisterItem item : transaction.itemsList) {

                    JSONObject extraInfo = item.extraInfo != null && !item.extraInfo.isEmpty() ? new JSONObject(item.extraInfo) : null;
                    String gtin = extraInfo != null ? extraInfo.optString("gtin") : null;
                    int idGtin = Integer.parseInt(item.idItem);
                    if (gtin != null && idGtin != 0) {
                        ps.setString(1, "GTIN"); //property_code
                        ps.setInt(2, idGtin); //id
                        ps.setString(3, gtin); //const
                        ps.setString(4, String.format("<question><const>%s</const><displayname>GTIN</displayname></question>",gtin)); //description
                        ps.setInt(5, version);
                        ps.setInt(6, 0);
                        ps.addBatch();
                    }

                    if (idGtin != 0) {
                        vs.setString(1, getId(item, useBarcodeAsId, appendBarcode));
                        vs.setString(2, "GTIN");
                        vs.setInt(3, idGtin);
                        vs.setInt(4, 0);
                        vs.setInt(5, version);
                        vs.setInt(6, gtin != null ? 0 : 1);
                        vs.addBatch();
                    }

                }
                p.executeBatch();
                ps.executeBatch();
                vs.executeBatch();
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
                for (CashRegisterItem item : transaction.itemsList) {
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

                for (CashRegisterItem item : transaction.itemsList) {
                    ps.setInt(1, npp); //pricelist
                    ps.setString(2, getId(item, useBarcodeAsId, appendBarcode)); //item
                    ps.setBigDecimal(3, item.price); //price
                    //Есть флаг 16 - скидка разрешена, грузим минимальную цену. 16&16 = 16; 30&16 = 16
                    //Нет флага 16 - скидка запрещена, в минимальную цену грузим розничную. 0&16 = 0; 15&16 = 0
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

    private void exportPriceListVar(Connection conn, TransactionCashRegisterInfo transaction, Integer npp, String weightCode, String pieceCode, boolean usePieceCode, int version) throws SQLException {
        if (transaction.itemsList != null) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO pricelist_var (pricelist, var, price, version, deleted) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE price=VALUES(price), deleted=VALUES(deleted)")) {

                for (CashRegisterItem item : transaction.itemsList) {
                    String prefix = getPrefix(item, weightCode, pieceCode, usePieceCode);
                    String barcode = makeBarcode(item.idBarcode, item.passScalesItem, prefix);
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

    private void exportVar(Connection conn, TransactionCashRegisterInfo transaction, boolean useBarcodeAsId, String weightCode, String pieceCode, boolean usePieceCode,
                           boolean appendBarcode, boolean sendZeroQuantityForWeightItems, boolean sendZeroQuantityForSplitItems, boolean tareWeightFieldInVarTable, int version) throws SQLException {
        if (transaction.itemsList != null) {
            conn.setAutoCommit(false);
            PreparedStatement ps = null;
            try {

                checkIndex(conn, "item", "var", "item");

                ps = conn.prepareStatement(
                        tareWeightFieldInVarTable ?
                                "INSERT INTO var (id, item, quantity, stock, version, deleted, tare_weight) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                                        "ON DUPLICATE KEY UPDATE item=VALUES(item), quantity=VALUES(quantity), stock=VALUES(stock), deleted=VALUES(deleted), " +
                                        "tare_weight=VALUES(tare_weight)" :
                                "INSERT INTO var (id, item, quantity, stock, version, deleted) VALUES (?, ?, ?, ?, ?, ?) " +
                                        "ON DUPLICATE KEY UPDATE item=VALUES(item), quantity=VALUES(quantity), stock=VALUES(stock), deleted=VALUES(deleted)");
                for (CashRegisterItem item : transaction.itemsList) {
                    String prefix = getPrefix(item, weightCode, pieceCode, usePieceCode);
                    String barcode = makeBarcode(removeCheckDigitFromBarcode(item.idBarcode, appendBarcode), item.passScalesItem, prefix);
                    if (barcode != null && item.idItem != null) {
                        ps.setString(1, trim(barcode, 40)); //id
                        ps.setString(2, getId(item, useBarcodeAsId, appendBarcode)); //item
                        ps.setDouble(3, ((sendZeroQuantityForWeightItems && item.passScalesItem) || (sendZeroQuantityForSplitItems && item.splitItem)) ? 0 :
                                (item.amountBarcode != null ? item.amountBarcode.doubleValue() : 1)); //quantity
                        ps.setInt(4, 1); //stock
                        ps.setInt(5, version); //version
                        ps.setInt(6, 0); //deleted

                        if (tareWeightFieldInVarTable) {
                            BigDecimal tareWeight = null;
                            JSONObject infoJSON = getExtInfo(item.info);
                            if (infoJSON != null) { //приходит в килограммах, выгружаем в граммах
                                tareWeight = safeDivide(infoJSON.optBigDecimal("tareWeight", null), BigDecimal.valueOf(1000));
                            }
                            ps.setBigDecimal(7, tareWeight != null ? tareWeight : BigDecimal.ZERO); //tare_weight
                        }

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

    private void exportVarDeleteBarcode(Connection conn, List<CashRegisterItem> barcodeList, int version) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO var (id, version, deleted) VALUES (?, ?, ?) " + "ON DUPLICATE KEY UPDATE deleted=VALUES(deleted)")) {
            for (CashRegisterItem item : barcodeList) {
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
        if (transaction.itemsList != null) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO properties (code, name, flags, description, version, deleted) VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE name=VALUES(name), flags=VALUES(flags), description=VALUES(description), deleted=VALUES(deleted)")) {
                for (CashRegisterItem item : transaction.itemsList) {
                    JSONObject infoJSON = getExtInfo(item.info);
                    if (infoJSON != null) {
                        JSONArray properties = infoJSON.optJSONArray("properties");
                        if(properties != null) {
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
                        }
                    }
                }
                ps.executeBatch();
                conn.commit();
            }
        }
    }

    private void exportPropertyValues(Connection conn, TransactionCashRegisterInfo transaction, int version) throws SQLException, JSONException {
        if (transaction.itemsList != null) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO property_values (property_code, id, const, description, comment, version, deleted) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE const=VALUES(const), description=VALUES(description), comment=VALUES(comment), deleted=VALUES(deleted)")) {
                for (CashRegisterItem item : transaction.itemsList) {
                    JSONObject infoJSON = getExtInfo(item.info);
                    if (infoJSON != null) {
                        JSONArray propertyValues = infoJSON.optJSONArray("property_values");
                        if (propertyValues != null) {
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
                ps.executeBatch();
                conn.commit();
            }
        }
    }

    private void exportItemPropertyValues(Connection conn, TransactionCashRegisterInfo transaction, boolean useBarcodeAsId, boolean appendBarcode, int version) throws SQLException, JSONException {
        if (transaction.itemsList != null) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO item_property_values (item_id, property_code, property_id, sequence, version, deleted) VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE sequence=VALUES(sequence), deleted=VALUES(deleted)")) {
                for (CashRegisterItem item : transaction.itemsList) {
                    JSONObject infoJSON = getExtInfo(item.info);
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
                ps.executeBatch();
                conn.commit();
            }
        }
    }

    private JSONObject getExtInfo(String extInfo) {
        return getExtInfo(extInfo, "ukm");
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

    private String getPrefix(CashRegisterItem item, String weightCode, String pieceCode, boolean usePieceCode) {
        return usePieceCode && isPieceUOM(item) ? pieceCode : weightCode;
    }

    private String makeBarcode(String idBarcode, boolean passScalesItem, String prefix) {
        return idBarcode != null && idBarcode.length() == 5 && passScalesItem && prefix != null ? (prefix + idBarcode) : idBarcode;
    }

    @Override
    public UKM4MySQLCashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList) throws ClassNotFoundException {
        List<CashDocument> result = new ArrayList<>();
        Map<String, List<CashDocument>> directoryListCashDocumentMap = new HashMap<>();

        UKM4MySQLSettings ukm4MySQLSettings = springContext.containsBean("ukm4MySQLSettings") ? (UKM4MySQLSettings) springContext.getBean("ukm4MySQLSettings") : new UKM4MySQLSettings();
        Integer lastDaysCashDocument = ukm4MySQLSettings.getLastDaysCashDocument();

        Set<String> directorySet = new HashSet<>();
        Map<String, CashRegisterInfo> directoryCashRegisterMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (fitHandler(c)) {
                directorySet.add(c.directory);
                if (c.number != null)
                    directoryCashRegisterMap.put(c.directory + "_" + c.number, c);
            }
        }

        for (String directory : directorySet) {
            List<CashDocument> cashDocumentList = new ArrayList<>();
            UKM4MySQLConnectionString params = new UKM4MySQLConnectionString(directory, 1);

            Connection conn = null;
            if (params.connectionString != null) {
                try {
                    conn = DriverManager.getConnection(params.connectionString, params.user, params.password);

                    checkCashDocumentColumnsAndIndices(conn);

                    Statement statement = conn.createStatement();
                    String queryString = "select m.cash_id, m.id, m.date, m.type, m.amount, m.shift_number, s.id, s.date " +
                                         "from moneyoperation m join shift s on m.shift_number = s.number AND m.cash_id = s.cash_id " +
                                         "where m.ext_processed = 0";
                    if (lastDaysCashDocument != null) {
                        queryString += " and m.date >='" + LocalDate.now().minusDays(lastDaysCashDocument).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "'";
                    }
                    ResultSet rs = statement.executeQuery(queryString);
                    LocalTime midnight = LocalTime.of(23, 59, 59);
                    while (rs.next()) {
                        int nppMachinery = rs.getInt("m.cash_id");
                        CashRegisterInfo cashRegister = directoryCashRegisterMap.get(directory + "_" + nppMachinery);
                        if (cashRegister != null) {
                            String numberCashDocument = rs.getString("m.id");
                            LocalDateTime dateTimeMoneyOperation = sqlTimestampToLocalDateTime(rs.getTimestamp("m.date"));
                            LocalDateTime dateTimeShift = sqlTimestampToLocalDateTime(rs.getTimestamp("s.date"));
                            LocalDate date = dateTimeShift.toLocalDate();
                            LocalTime time = dateTimeMoneyOperation.toLocalTime();
                            LocalTime twoAM = LocalTime.of(2, 0, 0);
                            if (time.isBefore(twoAM))
                                time = midnight;
                            int type = rs.getInt("m.type");
                            String numberZReport = rs.getString("s.id");
                            BigDecimal sum = type == 100 ? rs.getBigDecimal("m.amount") : type == 101 ? HandlerUtils.safeNegate(rs.getBigDecimal("m.amount")) : null;
                            if (sum != null) {
                                String idCashDocument = params.connectionString + "/" + nppMachinery + "/" + numberCashDocument;
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
            directoryListCashDocumentMap.put(directory, cashDocumentList);

        }
        return new UKM4MySQLCashDocumentBatch(result, directoryListCashDocumentMap);
    }

    @Override
    public void finishReadingCashDocumentInfo(UKM4MySQLCashDocumentBatch cashDocumentBatch) {
        for (Map.Entry<String, List<CashDocument>> entry : cashDocumentBatch.directoryListCashDocumentMap.entrySet()) {

            String directory = entry.getKey();
            List<CashDocument> cashDocumentList = entry.getValue();

            UKM4MySQLConnectionString params = new UKM4MySQLConnectionString(directory, 1);
            if (params.connectionString != null && !cashDocumentList.isEmpty()) {

                try(Connection conn = DriverManager.getConnection(params.connectionString, params.user, params.password)) {
                    conn.setAutoCommit(false);
                    try(PreparedStatement ps = conn.prepareStatement("UPDATE moneyoperation SET ext_processed = 1 WHERE id = ? AND cash_id = ?")) {
                        for (CashDocument cashDocument : cashDocumentList) {
                            ps.setString(1, cashDocument.numberCashDocument); //id
                            ps.setInt(2, cashDocument.nppMachinery); //cash_id
                            ps.addBatch();
                        }
                        ps.executeBatch();
                        conn.commit();
                    }

                } catch (SQLException e) {
                    throw new RuntimeException("finishReadingCashDocumentInfo failed", e);
                }
            }
        }
    }

    @Override
    public Pair<String, Set<String>> sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machinerySet) {
        if (!stopListInfo.exclude) {

            for (String directory : HandlerUtils.getDirectorySet(machinerySet)) {

                UKM4MySQLSettings ukm4MySQLSettings = springContext.containsBean("ukm4MySQLSettings") ? (UKM4MySQLSettings) springContext.getBean("ukm4MySQLSettings") : new UKM4MySQLSettings();
                Integer timeout = ukm4MySQLSettings.getTimeout();
                boolean skipBarcodes = ukm4MySQLSettings.getSkipBarcodes() != null && ukm4MySQLSettings.getSkipBarcodes();
                boolean useBarcodeAsId = ukm4MySQLSettings.getUseBarcodeAsId() != null && ukm4MySQLSettings.getUseBarcodeAsId();
                boolean appendBarcode = ukm4MySQLSettings.getAppendBarcode() != null && ukm4MySQLSettings.getAppendBarcode();


                UKM4MySQLConnectionString params = new UKM4MySQLConnectionString(directory, 0);
                if (params.connectionString != null) {
                    Connection conn = null;
                    PreparedStatement ps = null;
                    try {
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
        return null;
    }

    @Override
    public boolean sendDeleteBarcodeInfo(DeleteBarcodeInfo deleteBarcodeInfo) {

        try {
            if (!deleteBarcodeInfo.barcodeList.isEmpty()) {

                UKM4MySQLSettings ukm4MySQLSettings = springContext.containsBean("ukm4MySQLSettings") ? (UKM4MySQLSettings) springContext.getBean("ukm4MySQLSettings") : new UKM4MySQLSettings();
                Integer timeout = ukm4MySQLSettings.getTimeout();
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
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
        return true;
    }

    @Override
    public UKM4MySQLSalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) {

        UKM4MySQLSalesBatch salesBatch = null;

        UKM4MySQLSettings ukm4MySQLSettings = springContext.containsBean("ukm4MySQLSettings") ? (UKM4MySQLSettings) springContext.getBean("ukm4MySQLSettings") : new UKM4MySQLSettings();
        Set<Integer> cashPayments = parsePayments(ukm4MySQLSettings.getCashPayments());
        Set<Integer> cardPayments = parsePayments(ukm4MySQLSettings.getCardPayments());
        Set<Integer> giftCardPayments = parsePayments(ukm4MySQLSettings.getGiftCardPayments());
        Set<Integer> customPayments = parsePayments(ukm4MySQLSettings.getCustomPayments());
        List<String> giftCardList = ukm4MySQLSettings.getGiftCardList();
        boolean useBarcodeAsId = ukm4MySQLSettings.getUseBarcodeAsId() != null && ukm4MySQLSettings.getUseBarcodeAsId();
        boolean appendBarcode = ukm4MySQLSettings.getAppendBarcode() != null && ukm4MySQLSettings.getAppendBarcode();
        boolean useShiftNumberAsNumberZReport = ukm4MySQLSettings.isUseShiftNumberAsNumberZReport();
        boolean zeroPaymentForZeroSumReceipt = ukm4MySQLSettings.isZeroPaymentForZeroSumReceipt();
        boolean cashRegisterByStoreAndNumber = ukm4MySQLSettings.isCashRegisterByStoreAndNumber();
        boolean useLocalNumber = ukm4MySQLSettings.isUseLocalNumber();
        boolean useStoreInIdEmployee = ukm4MySQLSettings.isUseStoreInIdEmployee();
        boolean useCashNumberInsteadOfCashId = ukm4MySQLSettings.isUseCashNumberInsteadOfCashId();
        boolean usePieceCode = ukm4MySQLSettings.isUsePieceCode();
        boolean checkCardType = ukm4MySQLSettings.isCheckCardType();
        int maxReceiptCount = ukm4MySQLSettings.getMaxReceiptCount();

        UKM4MySQLConnectionString params = new UKM4MySQLConnectionString(directory, 1);

        String weightCode = null;
        Map<String, CashRegisterInfo> machineryMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (fitHandler(c)) {
                if (c.number != null)
                    machineryMap.put(getMachineryKey(c.section, c.number, cashRegisterByStoreAndNumber), c);
                if (c.weightCodeGroupCashRegister != null) {
                    weightCode = c.weightCodeGroupCashRegister;
                }
            }
        }

        try {

            if (params.connectionString == null) {
                processTransactionLogger.error("No connectionString found");
            } else {

                Connection conn = null;

                try {
                    sendSalesLogger.info(String.format(logPrefix + "connecting to %s", params.connectionString));
                    conn = DriverManager.getConnection(params.connectionString, params.user, params.password);
                    checkSalesColumnsAndIndices(conn);
                    salesBatch = readSalesInfoFromSQL(conn, weightCode, usePieceCode, machineryMap, cashPayments, cardPayments, giftCardPayments, customPayments,
                            giftCardList, useBarcodeAsId, appendBarcode, useShiftNumberAsNumberZReport, zeroPaymentForZeroSumReceipt,
                            cashRegisterByStoreAndNumber, useLocalNumber, useStoreInIdEmployee, useCashNumberInsteadOfCashId, checkCardType,
                            maxReceiptCount, directory);

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

    private Map<String, UKMPayment> readPaymentMap(Connection conn, Set<Integer> cashPayments, Set<Integer> cardPayments,
                                                   Set<Integer> giftCardPayments, Set<Integer> customPayments, List<String> giftCardList,
                                                   boolean checkCardType) {

        Map<String, UKMPayment> paymentMap = new HashMap<>();

        try (Statement statement = conn.createStatement()) {
            //sql_no_cache is workaround of the bug: https://bugs.mysql.com/bug.php?id=31353
            String query;
            if(checkCardType) {
                query = "select sql_no_cache p.id, p.cash_id, p.receipt_header, p.payment_id, p.amount, r.type, IF(p.card_type!='', p.card_type,p.card_number), p.type "
                        + "from receipt_payment p left join receipt r on p.cash_id = r.cash_id and p.receipt_header = r.id "
                        + "where r.ext_processed = 0 AND r.result = 0 AND (p.type = 0 OR p.type = 2)"; // type 3 это сдача, type 2 - аннулирование
            } else {
                query = "select sql_no_cache p.id, p.cash_id, p.receipt_header, p.payment_id, p.amount, r.type, p.card_number, p.type "
                        + "from receipt_payment p left join receipt r on p.cash_id = r.cash_id and p.receipt_header = r.id "
                        + "where r.ext_processed = 0 AND r.result = 0 AND (p.type = 0 OR p.type = 2)"; // type 3 это сдача, type 2 - аннулирование
            }
            ResultSet rs = statement.executeQuery(query);
            int count = 0;
            while (rs.next()) {
                Integer id = rs.getInt(1);
                Integer cash_id = rs.getInt(2); //cash_id
                Integer idReceipt = rs.getInt(3); //receipt_header
                String key = cash_id + "/" + idReceipt;
                Integer paymentType = rs.getInt(4);//payment_id
                BigDecimal amount = rs.getBigDecimal(5);
                Integer receiptType = rs.getInt(6); //r.type
                boolean isReturn = receiptType == 1 || receiptType == 4 || receiptType == 9;
                amount = isReturn ? amount.negate() : amount;

                //если пришёл payment с type = 2, то ищем такой же с type = 0 и удаляем (это аннуляция плате
                Integer pType = rs.getInt(8); //p.type
                if(pType == 2) {
                    paymentMap.remove(key);
                } else {

                    Map<String, Object> extraFields = new HashMap<>();
                    extraFields.put("externalId", cash_id + "_" + id);

                    if (customPayments.contains(paymentType)) {
                        UKMPayment paymentEntry = paymentMap.getOrDefault(key, new UKMPayment());
                        paymentEntry.payments.add(new Payment(String.valueOf(paymentType), amount, extraFields));
                        paymentMap.put(key, paymentEntry);
                    } else {
                        if (cashPayments.contains(paymentType)) //нал
                            paymentType = 0;
                        else if (cardPayments.contains(paymentType)) //безнал
                            paymentType = 1;
                        else if (giftCardPayments.contains(paymentType)) //сертификат
                            paymentType = 2;
                        String giftCard = rs.getString(7); //p.card_number
                        if (giftCard.isEmpty()) giftCard = null;

                        if (giftCard != null && giftCardList != null) {
                            for (String prefix : giftCardList) {
                                if (giftCard.startsWith(prefix)) {
                                    paymentType = 2;
                                    break;
                                }
                            }
                        }
                        //если тип оплаты не найден, считаем налом
                        if (paymentType != 0 && paymentType != 1 && paymentType != 2)
                            paymentType = 0;

                        UKMPayment paymentEntry = paymentMap.getOrDefault(key, new UKMPayment());
                        if (paymentType == 0) {
                            paymentEntry.payments.add(Payment.getCash(amount, extraFields));
                        } else if (paymentType == 1) {
                            paymentEntry.payments.add(Payment.getCard(amount, extraFields));
                        } else { //paymentType == 2
                            BigDecimal sumGiftCard = paymentEntry.sumGiftCardMap.get(giftCard);
                            paymentEntry.sumGiftCardMap.put(giftCard, HandlerUtils.safeAdd(sumGiftCard, amount));
                        }
                        paymentMap.put(key, paymentEntry);
                    }
                }
                count++;
            }
            sendSalesLogger.info(String.format(logPrefix + "read payments %s entries (query returned %s rows)", paymentMap.size(), count));
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
        return paymentMap;
    }

    private void checkCashDocumentColumnsAndIndices(Connection conn) throws SQLException {
        checkColumn(conn, "moneyoperation", "ext_processed");

        checkIndex(conn, "shift", "moneyoperation", "cash_id, shift_number");
    }

    private void checkSalesColumnsAndIndices(Connection conn) throws SQLException {
        checkColumn(conn, "receipt", "ext_processed");

        checkIndex(conn, "ext_processed_index", "receipt", "ext_processed");
        checkIndex(conn, "receipt", "receipt_item", "cash_id, receipt_header");
        checkIndex(conn, "item", "receipt_item_properties", "cash_id, receipt_item");
        checkIndex(conn, "receipt", "receipt_payment", "cash_id, receipt_header");
    }

    private void checkColumn(Connection conn, String tableName, String columnName) throws SQLException {
        try(Statement selectStatement = conn.createStatement()) {
            String query = String.format("SELECT COUNT(1) ColumnIsThere FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema=DATABASE() AND table_name='%s' AND column_name='%s'", tableName, columnName);
            ResultSet rs = selectStatement.executeQuery(query);

            while (rs.next()) {
                boolean columnExists = rs.getInt(1) > 0;
                if (!columnExists) {
                    try(Statement createStatement = conn.createStatement()) {
                        String command = String.format("ALTER TABLE %s ADD COLUMN %s TINYINT NOT NULL DEFAULT '0';", tableName, columnName);
                        sendSalesLogger.info(logPrefix + command);
                        createStatement.execute(command);
                    }
                }
            }
        }
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

    private UKM4MySQLSalesBatch readSalesInfoFromSQL(Connection conn, String weightCode, boolean usePieceCode, Map<String, CashRegisterInfo> machineryMap,
                                                     Set<Integer> cashPayments, Set<Integer> cardPayments, Set<Integer> giftCardPayments,
                                                     Set<Integer> customPayments, List<String> giftCardList, boolean useBarcodeAsId, boolean appendBarcode,
                                                     boolean useShiftNumberAsNumberZReport, boolean zeroPaymentForZeroSumReceipt,
                                                     boolean cashRegisterByStoreAndNumber, boolean useLocalNumber, boolean useStoreInIdEmployee,
                                                     boolean useCashNumberInsteadOfCashId, boolean checkCardType, int maxReceiptCount, String directory) {
        List<SalesInfo> salesInfoList = new ArrayList<>();

        //Map<Integer, String> loginMap = readLoginMap(conn);
        Set<Pair<Integer, Integer>> receiptSet = new HashSet<>();
        Set<String> usedBarcodes = new HashSet<>();

        try (Statement statement = conn.createStatement()) {
            String numberReceiptField = useLocalNumber ? "r.local_number" : "r.global_number";
            String query = "SELECT sql_no_cache i.store, i.cash_number, i.cash_id, i.id, i.receipt_header, i.var, i.item, i.total_quantity, i.price, i.total," +
                    " i.position, i.real_amount, i.stock_id, r.type, r.shift_open, " + numberReceiptField + ", r.date, r.cash_id, r.id, r.login, s.date, rip.value, l.user_id, l.user_name, s.number, r.client_card_code " +
                    " FROM receipt_item AS i" +
                    " JOIN receipt AS r ON i.receipt_header = r.id AND i.cash_id = r.cash_id" +
                    " JOIN shift AS s ON r.shift_open = s.id AND r.cash_id = s.cash_id" +
                    " LEFT JOIN receipt_item_properties AS rip ON i.cash_id = rip.cash_id AND i.id = rip.receipt_item AND rip.code = '$GiftCard_Number$' " +
                    " LEFT JOIN login AS l ON r.cash_id = l.cash_id AND r.login = l.id  "+
                    " WHERE r.ext_processed = 0 AND r.result = 0 AND i.type = 0";
            ResultSet rs = statement.executeQuery(query);

            Map<String, UKMPayment> paymentMap = readPaymentMap(conn, cashPayments, cardPayments, giftCardPayments, customPayments, giftCardList, checkCardType);
            int rowCount = 0;
            while (rs.next()) {

                String store = rs.getString(1); //i.store = при выгрузке цен выгружаем section
                Integer cash_number = rs.getInt(2); //i.cash_number
                Integer cash_id = rs.getInt(3); //i.cash_id
                String machineryKey = getMachineryKey(store, useCashNumberInsteadOfCashId ? cash_number : cash_id, cashRegisterByStoreAndNumber);
                CashRegisterInfo cashRegister = machineryMap.get(getMachineryKey(store, useCashNumberInsteadOfCashId ? cash_number : cash_id, cashRegisterByStoreAndNumber));
                if(cashRegister == null) {
                    throw new RuntimeException(logPrefix + String.format("cashRegister %s not found", machineryKey));
                }

                //Integer id = rs.getInt(4); //i.id
                Integer idReceipt = rs.getInt(5); //i.receipt_header
                String idBarcode = useBarcodeAsId ? rs.getString(7) : rs.getString(6); //i.item : i.var
                idBarcode = appendBarcode ? appendCheckDigitToBarcode(idBarcode, 5) : idBarcode;
                if(idBarcode != null) {
                    boolean startsWithWeightCode = weightCode != null && idBarcode.startsWith(weightCode);
                    boolean startsWithPieceCode = usePieceCode && cashRegister.pieceCodeGroupCashRegister != null && idBarcode.startsWith(cashRegister.pieceCodeGroupCashRegister);
                    if ((idBarcode.length() == 13 || idBarcode.length() == 7) && (startsWithWeightCode || startsWithPieceCode))
                        idBarcode = idBarcode.substring(2, 7);
                }
                String idItem = useBarcodeAsId && appendBarcode ? appendCheckDigitToBarcode(rs.getString(7), 5) : rs.getString(7); //i.item
                BigDecimal totalQuantity = rs.getBigDecimal(8); //i.total_quantity
                BigDecimal price = rs.getBigDecimal(9); //i.price
                BigDecimal sum = rs.getBigDecimal(10); //i.total
                Integer position = rs.getInt(11) + 1;
                BigDecimal realAmount = rs.getBigDecimal(12); //i.real_amount
                String idSection = rs.getString(13);

                UKMPayment paymentEntry = paymentMap.get(cash_id + "/" + idReceipt);
                if(paymentEntry == null && zeroPaymentForZeroSumReceipt && realAmount.compareTo(BigDecimal.ZERO) == 0) {
                    paymentEntry = new UKMPayment();
                    paymentEntry.payments.add(Payment.getCash(BigDecimal.ZERO));
                }
                if (paymentEntry != null && totalQuantity != null) {
                    Integer receiptType = rs.getInt(14); //r.type
                    boolean isSale = receiptType == 0 || receiptType == 5 || receiptType == 8;
                    boolean isReturn = receiptType == 1 || receiptType == 4 || receiptType == 9;
                    String numberZReport = useShiftNumberAsNumberZReport ? String.valueOf(rs.getInt(25)) : rs.getString(15); //s.number or r.shift_open
                    Integer numberReceipt = rs.getInt(16); //r.local_number or r.global_number
                    LocalDate dateReceipt = sqlDateToLocalDate(rs.getDate(17)); // r.date
                    LocalTime timeReceipt = sqlTimeToLocalTime(rs.getTime(17)); //r.date
                    //Integer login = rs.getInt(18); //r.login
                    LocalDate dateZReport = sqlDateToLocalDate(rs.getDate(21)); //s.date
                    LocalTime timeZReport = sqlTimeToLocalTime(rs.getTime(21)); //s.date
                    //String idEmployee = loginMap.get(login);

                    String giftCardValue = rs.getString(22); //rip.value
                    String idEmployee = (useStoreInIdEmployee ? (store + "_") : "") + rs.getInt(23); //l.user_id
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
//                        if (cashRegister == null || cashRegister.startDate == null || (dateReceipt != null && dateReceipt.compareTo(cashRegister.startDate) >= 0)) {
                            salesInfoList.add(getSalesInfo(isGiftCard, false, cashRegister.numberGroup, cashRegister.number, numberZReport,
                                    dateZReport, timeZReport, numberReceipt, dateReceipt, timeReceipt, idEmployee,
                                    null, lastNameContact, sumGiftCardMap, paymentEntry.payments, idBarcode, idItem, null, null, totalQuantity,
                                    price, isSale ? realAmount : realAmount.negate(), null, discountSumReceiptDetail, null, discountCard,
                                    null, position, null, idSection, false, null, null, cashRegister));
//                        }
                        receiptSet.add(Pair.create(idReceipt, cash_id));
                        if (maxReceiptCount > 0 && receiptSet.size() >= maxReceiptCount)
                            break;
                    }
                }
                rowCount++;
            }
            if (!salesInfoList.isEmpty()) {
                long time = System.currentTimeMillis();
                while (rs.next()) {
                    rowCount++;
                }
                sendSalesLogger.info(String.format(logPrefix + "rowCounter time: %s", System.currentTimeMillis() - time)); //todo: temp log, remove
                sendSalesLogger.info(String.format(logPrefix + "found %s records (query returned %s rows)", salesInfoList.size(), rowCount));
            }
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
        return new UKM4MySQLSalesBatch(salesInfoList, receiptSet, directory);
    }

    private String getMachineryKey(String store, Integer number, boolean cashRegisterByStoreAndNumber) {
        return cashRegisterByStoreAndNumber ? (store + "/" + number) : String.valueOf(number);
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
                        conn = DriverManager.getConnection(params.connectionString, params.user, params.password);
                        String dateFrom = requestExchange.dateFrom.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        String dateTo = requestExchange.dateTo.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                        Set<CashRegisterInfo> cashRegisterSet = getCashRegisterSet(requestExchange, true);
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
        return trim(useBarcodeAsId ? removeCheckDigitFromBarcode(item instanceof CashRegisterItem ? ((CashRegisterItem) item).mainBarcode : item.idBarcode, appendBarcode) : item.idItem, 40);
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

    private class UKMPayment {
        Map<String, BigDecimal> sumGiftCardMap;
        List<Payment> payments;

        public UKMPayment() {
            this.sumGiftCardMap = new HashMap<>();
            this.payments = new ArrayList<>();
        }
    }
}
