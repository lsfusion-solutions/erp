package equ.srv.terminal;

import com.google.common.base.Throwables;
import equ.api.terminal.*;
import equ.srv.TerminalEquipmentServer;
import lsfusion.base.IOUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.context.ExecutionStack;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.logics.*;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.sql.*;
import java.sql.Date;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DefaultTerminalHandler implements TerminalHandlerInterface {

    private LogicsInstance logicsInstance;

    public void setLogicsInstance(LogicsInstance logicsInstance) {
        this.logicsInstance = logicsInstance;
    }

    public LogicsInstance getLogicsInstance() {
        return logicsInstance;
    }

    public DefaultTerminalHandler() {
        super();
    }

    @Override
    public List<Object> readHostPort(DataSession session) throws RemoteException, SQLException {
        try {
            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
            if (terminalHandlerLM != null) {
                String host = (String) terminalHandlerLM.findProperty("hostTerminalServer[]").read(session);
                Integer port = (Integer) terminalHandlerLM.findProperty("portTerminalServer[]").read(session);
                return Arrays.asList((Object) host, port);
            } else return new ArrayList<>();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public List<String> readItem(DataSession session, DataObject user, String barcode) throws RemoteException, SQLException {
        try {
            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
            if(terminalHandlerLM != null) {
                boolean currentPrice = terminalHandlerLM.findProperty("useCurrentPriceInTerminal").read(session) != null;
                ObjectValue barcodeObject = terminalHandlerLM.findProperty("barcode[STRING[15]]").readClasses(session, new DataObject(barcode));
                String nameSkuBarcode = (String) terminalHandlerLM.findProperty("nameSku[Barcode]").read(session, barcodeObject);
                if(nameSkuBarcode == null)
                    return null;
                String isWeight = terminalHandlerLM.findProperty("passScales[Barcode]").read(session, barcodeObject) != null ? "1" : "0";
                ObjectValue skuObject = terminalHandlerLM.findProperty("skuBarcode[STRING[15]]").readClasses(session, new DataObject(barcode));
                ObjectValue stockObject = user == null ? NullValue.instance : terminalHandlerLM.findProperty("stock[Employee]").readClasses(session, user);
                BigDecimal price = null;
                BigDecimal quantity = null;
                if(skuObject instanceof DataObject && stockObject instanceof DataObject) {
                    if(currentPrice)
                        price = (BigDecimal) terminalHandlerLM.findProperty("currentRetailPricingPrice[Sku,Stock]").read(session, skuObject, stockObject);
                    else
                        price = (BigDecimal) terminalHandlerLM.findProperty("transactionPrice[Sku,Stock]").read(session, skuObject, stockObject);
                    quantity = (BigDecimal) terminalHandlerLM.findProperty("currentBalance[Sku,Stock]").read(session, skuObject, stockObject);
                }
                String priceValue = null;
                if(price != null) {
                    price = price.setScale(2, BigDecimal.ROUND_HALF_UP);
                    DecimalFormat df = new DecimalFormat();
                    df.setMaximumFractionDigits(2);
                    df.setMinimumFractionDigits(0);
                    df.setGroupingUsed(false);
                    priceValue = df.format(price).replace(",", ".");
                }
                return Arrays.asList(barcode, nameSkuBarcode, priceValue == null ? "0" : priceValue,
                        quantity == null ? "0" : String.valueOf(quantity.longValue()), "", "", "", "", "", isWeight);
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public String readItemHtml(DataSession session, String barcode, String idStock) throws RemoteException, SQLException {
        try {
            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
            if(terminalHandlerLM != null) {
                String nameSkuBarcode = (String) terminalHandlerLM.findProperty("nameSku[Barcode]").read(session, terminalHandlerLM.findProperty("barcode[STRING[15]]").readClasses(session, new DataObject(barcode)));
                if(nameSkuBarcode == null)
                    return null;

                ObjectValue skuObject = terminalHandlerLM.findProperty("skuBarcode[STRING[15]]").readClasses(session, new DataObject(barcode));
                ObjectValue stockObject = terminalHandlerLM.findProperty("stock[VARSTRING[100]]").readClasses(session, new DataObject(idStock));
                BigDecimal price = null;
                BigDecimal oldPrice = null;
                if(skuObject instanceof DataObject && stockObject instanceof DataObject) {
                    price = (BigDecimal) terminalHandlerLM.findProperty("transactionPrice[Sku,Stock]").read(session, skuObject, stockObject);
                    oldPrice = (BigDecimal) terminalHandlerLM.findProperty("transactionPrice[Sku,Stock]").read(session, skuObject, stockObject);
                }
                boolean action = price != null && oldPrice != null && price.compareTo(oldPrice) == 0;

                return action ?
                        String.format("<html><body bgcolor=\"#FFFF00\">Наименование: <b>%s</b><br/><b><font color=\"#FF0000\">Акция</font></b> Цена: <b>%s</b>, Скидка: <b>%s</b></body></html>",
                                nameSkuBarcode, String.valueOf(price.doubleValue()), String.valueOf(oldPrice.doubleValue() - price.doubleValue()))
                        : String.format("<html><body>Наименование: <b>%s</b><br/>Цена: <b>%s</b></body></html>",
                        nameSkuBarcode, price == null ? "0" : String.valueOf(price.doubleValue()));
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public byte[] readBase(DataSession session, DataObject userObject) throws RemoteException, SQLException {
        Connection connection = null;
        File file = null;
        File zipFile = null;
        try {
            BusinessLogics BL = getLogicsInstance().getBusinessLogics();
            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
            if (terminalHandlerLM != null) {

                ObjectValue stockObject = terminalHandlerLM.findProperty("stock[Employee]").readClasses(session, userObject);
                ObjectValue priceListTypeObject = terminalHandlerLM.findProperty("priceListTypeTerminal[]").readClasses(session);
                //если prefix null, то таблицу не выгружаем. Если prefix пустой (skipPrefix), то таблицу выгружаем, но без префикса
                String prefix = (String) terminalHandlerLM.findProperty("exportId[]").read(session);
                List<List<Object>> barcodeList = readBarcodeList(session, stockObject);
                List<TerminalOrder> orderList = TerminalEquipmentServer.readTerminalOrderList(session, stockObject);
                List<TerminalAssortment> assortmentList = TerminalEquipmentServer.readTerminalAssortmentList(session, BL, priceListTypeObject, stockObject);
                List<TerminalHandbookType> handbookTypeList = TerminalEquipmentServer.readTerminalHandbookTypeList(session, BL);
                List<TerminalDocumentType> terminalDocumentTypeList = TerminalEquipmentServer.readTerminalDocumentTypeList(session, BL);
                List<TerminalLegalEntity> customANAList = TerminalEquipmentServer.readCustomANAList(session, BL);
                file = File.createTempFile("terminalHandler", ".db");

                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());

                createGoodsTable(connection);
                updateGoodsTable(connection, barcodeList, orderList);

                createOrderTable(connection);
                updateOrderTable(connection, orderList, prefix);

                createAssortTable(connection);
                updateAssortTable(connection, assortmentList, prefix);

                createVANTable(connection);
                updateVANTable(connection, handbookTypeList);

                createANATable(connection);
                updateANATable(connection, customANAList);

                createVOPTable(connection);
                updateVOPTable(connection, terminalDocumentTypeList);

                //copy base to exchange directory
                FileInputStream fis = new FileInputStream(file);
                zipFile = File.createTempFile("base", ".zip");
                FileOutputStream fos = new FileOutputStream(zipFile);
                ZipOutputStream zos = new ZipOutputStream(fos);
                zos.putNextEntry(new ZipEntry("tsd.db"));
                byte[] buf = new byte[1024];
                int len;
                while ((len = fis.read(buf)) > 0) {
                    zos.write(buf, 0, len);
                }
                fis.close();
                zos.close();

                return IOUtils.getFileBytes(zipFile);
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            if (connection != null)
                connection.close();
            if(file != null && !file.delete())
                file.deleteOnExit();
            if(zipFile != null && !zipFile.delete())
                zipFile.deleteOnExit();
        }
    }

    private List<List<Object>> readBarcodeList(DataSession session, ObjectValue stockObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<List<Object>> result = new ArrayList<>();
        ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
        if(terminalHandlerLM != null) {
            boolean currentPrice = terminalHandlerLM.findProperty("useCurrentPriceInTerminal").read(session) != null;
            boolean currentQuantity = terminalHandlerLM.findProperty("useCurrentQuantityInTerminal").read(session) != null;

            KeyExpr barcodeExpr = new KeyExpr("barcode");
            ImRevMap<Object, KeyExpr> barcodeKeys = MapFact.singletonRev((Object) "barcode", barcodeExpr);

            QueryBuilder<Object, Object> barcodeQuery = new QueryBuilder<>(barcodeKeys);
            barcodeQuery.addProperty("idBarcode", terminalHandlerLM.findProperty("id[Barcode]").getExpr(barcodeExpr));
            barcodeQuery.addProperty("nameSkuBarcode", terminalHandlerLM.findProperty("nameSku[Barcode]").getExpr(barcodeExpr));
            barcodeQuery.addProperty("idSkuBarcode", terminalHandlerLM.findProperty("idSku[Barcode]").getExpr(barcodeExpr));
            barcodeQuery.addProperty("nameManufacturer", terminalHandlerLM.findProperty("nameManufacturer[Barcode]").getExpr(barcodeExpr));
            barcodeQuery.addProperty("passScales", terminalHandlerLM.findProperty("passScales[Barcode]").getExpr(barcodeExpr));
            if(currentPrice) {
                barcodeQuery.addProperty("price", terminalHandlerLM.findProperty("currentPriceInTerminal[Barcode,Stock]").getExpr(barcodeExpr, stockObject.getExpr()));
                barcodeQuery.and(terminalHandlerLM.findProperty("currentPriceInTerminal[Barcode,Stock]").getExpr(barcodeExpr, stockObject.getExpr()).getWhere());
            } else {
                barcodeQuery.addProperty("price", terminalHandlerLM.findProperty("transactionPrice[Barcode,Stock]").getExpr(barcodeExpr, stockObject.getExpr()));
                barcodeQuery.and(terminalHandlerLM.findProperty("transactionPrice[Barcode,Stock]").getExpr(barcodeExpr, stockObject.getExpr()).getWhere());
            }
            if(currentQuantity)
                barcodeQuery.addProperty("quantity", terminalHandlerLM.findProperty("currentBalance[Barcode,Stock]").getExpr(barcodeExpr, stockObject.getExpr()));
            barcodeQuery.and(terminalHandlerLM.findProperty("id[Barcode]").getExpr(barcodeExpr).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> barcodeResult = barcodeQuery.execute(session);
            for (ImMap<Object, Object> entry : barcodeResult.values()) {

                String idBarcode = trim((String) entry.get("idBarcode"));
                String nameSkuBarcode = trim((String) entry.get("nameSkuBarcode"));
                BigDecimal price = (BigDecimal) entry.get("price");
                BigDecimal quantityBarcodeStock = currentQuantity ? (BigDecimal) entry.get("quantity") : BigDecimal.ONE;
                if(quantityBarcodeStock == null)
                    quantityBarcodeStock = BigDecimal.ZERO;

                String idSkuBarcode = trim((String) entry.get("idSkuBarcode"));
                String nameManufacturer = trim((String) entry.get("nameManufacturer"));
                String isWeight = entry.get("passScales") != null ? "1" : "0";

                result.add(Arrays.<Object>asList(idBarcode, nameSkuBarcode, price, quantityBarcodeStock, idSkuBarcode, nameManufacturer, isWeight));

            }
        }
        return result;
    }

    private String trim(String input) {
        return input == null ? null : input.trim();
    }

    private void createOrderTable(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE zayavki " +
                "(dv     TEXT," +
                " num   TEXT," +
                " post  TEXT," +
                " barcode   TEXT," +
                " quant   REAL," +
                " price    REAL," +
                " minquant    REAL," +
                " maxquant    REAL," +
                " minprice    REAL," +
                " maxprice    REAL," +
                "PRIMARY KEY (num, barcode))";
        statement.executeUpdate(sql);
        statement.execute("CREATE INDEX zayavki_post ON zayavki (post);");
        statement.close();
    }

    private void updateOrderTable(Connection connection, List<TerminalOrder> terminalOrderList, String prefix) throws SQLException {
        if (!terminalOrderList.isEmpty() && prefix != null) {
            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO zayavki VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
                statement = connection.prepareStatement(sql);
                for (TerminalOrder order : terminalOrderList) {
                    if (order.number != null) {
                        String supplier = order.supplier == null ? "" : (prefix + formatValue(order.supplier));
                        statement.setObject(1, formatValue(order.date));
                        statement.setObject(2, formatValue(order.number));
                        statement.setObject(3,supplier);
                        statement.setObject(4,formatValue(order.barcode));
                        statement.setObject(5,formatValue(order.quantity));
                        statement.setObject(6,formatValue(order.price));
                        statement.setObject(7,formatValue(order.minQuantity));
                        statement.setObject(8,formatValue(order.maxQuantity));
                        statement.setObject(9,formatValue(order.minPrice));
                        statement.setObject(10,formatValue(order.maxPrice));
                        statement.addBatch();
                    }
                }
                statement.executeBatch();
                connection.commit();
            } finally {
                if (statement != null)
                    statement.close();
                connection.setAutoCommit(true);
            }
        }
    }

    private void createGoodsTable(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE goods " +
                "(barcode TEXT PRIMARY KEY," +
                " naim    TEXT," +
                " price   REAL," +
                " quant   REAL," +
                " fld1    TEXT," +
                " fld2    TEXT," +
                " fld3    TEXT," +
                " fld4    TEXT," +
                " fld5    TEXT," +
                " image   TEXT," +
                " weight TEXT)";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateGoodsTable(Connection connection, List<List<Object>> barcodeList, List<TerminalOrder> orderList) throws SQLException {
        if (!barcodeList.isEmpty() || !orderList.isEmpty()) {
            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO goods VALUES(?, ?, ?, ?, ?, ?, '', '', '', '', ?);";
                statement = connection.prepareStatement(sql);
                Set<String> usedBarcodes = new HashSet<>();
                for (List<Object> barcode : barcodeList) {
                    if (barcode.get(0) != null) {
                        statement.setObject(1, formatValue(barcode.get(0))); //idBarcode
                        statement.setObject(2, formatValue(barcode.get(1))); //name
                        statement.setObject(3, formatValue(barcode.get(2))); //price
                        statement.setObject(4, formatValue(barcode.get(3))); //quantity
                        statement.setObject(5, formatValue(barcode.get(4))); //idItem
                        statement.setObject(6, formatValue(barcode.get(5))); //manufacturer
                        statement.setObject(7, formatValue(barcode.get(6))); //weight
                        statement.addBatch();
                        usedBarcodes.add((String) barcode.get(0));
                    }
                }
                for(TerminalOrder order : orderList) {
                    if (order.barcode != null && !usedBarcodes.contains(order.barcode)) {
                        statement.setObject(1, formatValue(order.barcode)); //idBarcode
                        statement.setObject(2, formatValue(order.name)); //name
                        statement.setObject(3, formatValue(order.price)); //price
                        statement.setObject(4, formatValue(order.quantity)); //quantity
                        statement.setObject(5, formatValue(order.idItem)); //idItem
                        statement.setObject(6, formatValue(order.manufacturer)); //manufacturer
                        statement.setObject(7, formatValue(order.weight)); //weight
                        statement.addBatch();
                    }
                }
                statement.executeBatch();
                connection.commit();
                connection.createStatement().execute("CREATE INDEX goods_naim ON goods (naim ASC);");
                connection.commit();

            } finally {
                if(statement != null)
                    statement.close();
                connection.setAutoCommit(true);
            }
        }
    }

    private void createAssortTable(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE assort " +
                "(post    TEXT," +
                " barcode TEXT," +
                "PRIMARY KEY ( post, barcode))";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateAssortTable(Connection connection, List<TerminalAssortment> terminalAssortmentList, String prefix) throws SQLException {
        if (!terminalAssortmentList.isEmpty() && prefix != null) {
            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO assort VALUES(?, ?);";
                statement = connection.prepareStatement(sql);
                for (TerminalAssortment assortment : terminalAssortmentList) {
                    if (assortment.idSupplier != null && assortment.idBarcode != null) {
                        statement.setObject(1, formatValue((prefix + assortment.idSupplier)));
                        statement.setObject(2, formatValue(assortment.idBarcode));
                        statement.addBatch();
                    }
                }
                statement.executeBatch();
                connection.commit();
            } finally {
                if (statement != null)
                    statement.close();
                connection.setAutoCommit(true);
            }
        }
    }

    private void createVANTable(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE van " +
                "(van    TEXT PRIMARY KEY," +
                " naim   TEXT)";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateVANTable(Connection connection, List<TerminalHandbookType> terminalHandbookTypeList) throws SQLException {
        if (!terminalHandbookTypeList.isEmpty()) {
            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO van VALUES(?, ?);";
                statement = connection.prepareStatement(sql);
                for (TerminalHandbookType terminalHandbookType : terminalHandbookTypeList) {
                    if (terminalHandbookType.id != null && terminalHandbookType.name != null) {
                        statement.setObject(1, formatValue(terminalHandbookType.id)); //id
                        statement.setObject(2, formatValue(terminalHandbookType.name)); //name
                        statement.addBatch();
                    }
                }
                statement.executeBatch();
                connection.commit();

            } finally {
                if(statement != null)
                    statement.close();
                connection.setAutoCommit(true);
            }
        }
    }

    private void createANATable(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE ana " +
                "(ana  TEXT PRIMARY KEY," +
                " naim TEXT," +
                " fld1 TEXT," +
                " fld2 TEXT," +
                " fld3 TEXT)";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateANATable(Connection connection, List<TerminalLegalEntity> customANAList) throws SQLException {
        if (!customANAList.isEmpty()) {
            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO ana VALUES(?, ?, '', '', '');";
                statement = connection.prepareStatement(sql);
                for (TerminalLegalEntity legalEntity : customANAList) {
                    if (legalEntity.idLegalEntity != null) {
                        statement.setObject(1, formatValue(legalEntity.idLegalEntity));
                        statement.setObject(2, formatValue(legalEntity.nameLegalEntity));
                        statement.addBatch();
                    }
                }
                statement.executeBatch();
                connection.commit();
                connection.createStatement().execute("CREATE INDEX ana_naim ON ana (naim ASC);");
                connection.commit();
            } finally {
                if (statement != null)
                    statement.close();
                connection.setAutoCommit(true);
            }
        }
    }

    private void createVOPTable(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE vop " +
                "(vop  TEXT PRIMARY KEY," +
                " rvop TEXT," +
                " naim TEXT," +
                " van1 TEXT," +
                " van2 TEXT," +
                " van3 TEXT," +
                " FLAGS INTEGER )";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateVOPTable(Connection connection, List<TerminalDocumentType> terminalDocumentTypeList) throws SQLException {
        if (!terminalDocumentTypeList.isEmpty()) {

            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO vop VALUES(?, ?, ?, ?, ?, ?, ?);";
                statement = connection.prepareStatement(sql);
                for (TerminalDocumentType tdt : terminalDocumentTypeList) {
                    if (tdt.id != null) {
                        statement.setObject(1, formatValue(tdt.id));
                        statement.setObject(2, "");
                        statement.setObject(3, formatValue(tdt.name));
                        statement.setObject(4, formatValue(tdt.analytics1));
                        statement.setObject(5, formatValue(tdt.analytics2));
                        statement.setObject(6, "");
                        statement.setObject(7, formatValue(tdt.flag == null ? "1" : tdt.flag));
                        statement.addBatch();
                    }
                }
                statement.executeBatch();
            } finally {
                if (statement != null)
                    statement.close();
                connection.setAutoCommit(true);
            }
        }
    }

    private Object formatValue(Object value) {
        return value == null ? "" : value instanceof Date ? new SimpleDateFormat("dd.MM.yyyy").format(value) : value;
    }

    @Override
    public String importTerminalDocument(DataSession session, ExecutionStack stack, DataObject userObject, String idTerminalDocument, List<List<Object>> terminalDocumentDetailList, boolean emptyDocument) throws RemoteException, SQLException {
        try {

            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
            if(terminalHandlerLM != null) {

                List<ImportProperty<?>> props = new ArrayList<>();
                List<ImportField> fields = new ArrayList<>();
                List<ImportKey<?>> keys = new ArrayList<>();

                ImportField idTerminalDocumentField = new ImportField(terminalHandlerLM.findProperty("id[TerminalDocument]"));
                ImportKey<?> terminalDocumentKey = new ImportKey((ConcreteCustomClass) terminalHandlerLM.findClass("TerminalDocument"),
                        terminalHandlerLM.findProperty("terminalDocument[VARSTRING[100]]").getMapping(idTerminalDocumentField));
                keys.add(terminalDocumentKey);
                props.add(new ImportProperty(idTerminalDocumentField, terminalHandlerLM.findProperty("id[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(idTerminalDocumentField);

                ImportField numberTerminalDocumentField = new ImportField(terminalHandlerLM.findProperty("title[TerminalDocument]"));
                props.add(new ImportProperty(numberTerminalDocumentField, terminalHandlerLM.findProperty("title[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(numberTerminalDocumentField);

                ImportField idTerminalDocumentTypeField = new ImportField(terminalHandlerLM.findProperty("id[TerminalDocumentType]"));
                ImportKey<?> terminalDocumentTypeKey = new ImportKey((ConcreteCustomClass) terminalHandlerLM.findClass("TerminalDocumentType"),
                        terminalHandlerLM.findProperty("terminalDocumentType[VARSTRING[100]]").getMapping(idTerminalDocumentTypeField));
                terminalDocumentTypeKey.skipKey = true;
                keys.add(terminalDocumentTypeKey);
                props.add(new ImportProperty(idTerminalDocumentTypeField, terminalHandlerLM.findProperty("terminalDocumentType[TerminalDocument]").getMapping(terminalDocumentKey),
                        terminalHandlerLM.object(terminalHandlerLM.findClass("TerminalDocumentType")).getMapping(terminalDocumentTypeKey)));
                fields.add(idTerminalDocumentTypeField);

                ImportField idTerminalHandbookType1Field = new ImportField(terminalHandlerLM.findProperty("idTerminalHandbookType1[TerminalDocument]"));
                props.add(new ImportProperty(idTerminalHandbookType1Field, terminalHandlerLM.findProperty("idTerminalHandbookType1[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(idTerminalHandbookType1Field);

                ImportField idTerminalHandbookType2Field = new ImportField(terminalHandlerLM.findProperty("idTerminalHandbookType2[TerminalDocument]"));
                props.add(new ImportProperty(idTerminalHandbookType2Field, terminalHandlerLM.findProperty("idTerminalHandbookType2[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(idTerminalHandbookType2Field);

                ImportField commentTerminalDocumentField = new ImportField(terminalHandlerLM.findProperty("comment[TerminalDocument]"));
                props.add(new ImportProperty(commentTerminalDocumentField, terminalHandlerLM.findProperty("comment[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(commentTerminalDocumentField);

                if (!emptyDocument) {

                    ImportField idTerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("id[TerminalDocumentDetail]"));
                    ImportKey<?> terminalDocumentDetailKey = new ImportKey((ConcreteCustomClass) terminalHandlerLM.findClass("TerminalDocumentDetail"),
                            terminalHandlerLM.findProperty("terminalIdTerminalId[VARSTRING[100],VARSTRING[100]]").getMapping(idTerminalDocumentField, idTerminalDocumentDetailField));
                    keys.add(terminalDocumentDetailKey);
                    props.add(new ImportProperty(idTerminalDocumentDetailField, terminalHandlerLM.findProperty("id[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    props.add(new ImportProperty(idTerminalDocumentField, terminalHandlerLM.findProperty("terminalDocument[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey),
                            terminalHandlerLM.object(terminalHandlerLM.findClass("TerminalDocument")).getMapping(terminalDocumentKey)));
                    fields.add(idTerminalDocumentDetailField);

                    ImportField numberTerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("number[TerminalDocumentDetail]"));
                    props.add(new ImportProperty(numberTerminalDocumentDetailField, terminalHandlerLM.findProperty("number[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    fields.add(numberTerminalDocumentDetailField);

                    ImportField barcodeTerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("barcode[TerminalDocumentDetail]"));
                    props.add(new ImportProperty(barcodeTerminalDocumentDetailField, terminalHandlerLM.findProperty("barcode[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    fields.add(barcodeTerminalDocumentDetailField);

                    ImportField quantityTerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("quantity[TerminalDocumentDetail]"));
                    props.add(new ImportProperty(quantityTerminalDocumentDetailField, terminalHandlerLM.findProperty("quantity[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    fields.add(quantityTerminalDocumentDetailField);

                    ImportField priceTerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("price[TerminalDocumentDetail]"));
                    props.add(new ImportProperty(priceTerminalDocumentDetailField, terminalHandlerLM.findProperty("price[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    fields.add(priceTerminalDocumentDetailField);

                    ImportField commentTerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("comment[TerminalDocumentDetail]"));
                    props.add(new ImportProperty(commentTerminalDocumentDetailField, terminalHandlerLM.findProperty("comment[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    fields.add(commentTerminalDocumentDetailField);

                    ImportField dateTimeScanTerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("dateTimeScan[TerminalDocumentDetail]"));
                    props.add(new ImportProperty(dateTimeScanTerminalDocumentDetailField, terminalHandlerLM.findProperty("dateTimeScan[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    fields.add(dateTimeScanTerminalDocumentDetailField);

                    ImportField extraDate1TerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("extraDate1[TerminalDocumentDetail]"));
                    props.add(new ImportProperty(extraDate1TerminalDocumentDetailField, terminalHandlerLM.findProperty("extraDate1[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    fields.add(extraDate1TerminalDocumentDetailField);

                    ImportField extraDate2TerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("extraDate2[TerminalDocumentDetail]"));
                    props.add(new ImportProperty(extraDate2TerminalDocumentDetailField, terminalHandlerLM.findProperty("extraDate2[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    fields.add(extraDate2TerminalDocumentDetailField);
                }

                ImportTable table = new ImportTable(fields, terminalDocumentDetailList);

                ServerLoggers.importLogger.info("start importing terminal document " + idTerminalDocument);                
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);

                ObjectValue terminalDocumentObject = terminalHandlerLM.findProperty("terminalDocument[VARSTRING[100]]").readClasses(session, session.getModifier(), session.getQueryEnv(), new DataObject(idTerminalDocument));
                terminalHandlerLM.findProperty("createdUser[TerminalDocument]").change(userObject.object, session, (DataObject) terminalDocumentObject);
                terminalHandlerLM.findAction("process[TerminalDocument]").execute(session, stack, terminalDocumentObject);
                ServerLoggers.importLogger.info("start applying terminal document " + idTerminalDocument);
                return session.applyMessage(getLogicsInstance().getBusinessLogics(), stack);

            } else return "-1";

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public boolean isActiveTerminal(DataSession session, String idTerminal) throws RemoteException, SQLException {
        try {
            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
            if (terminalHandlerLM != null) {
                boolean checkIdTerminal = terminalHandlerLM.findProperty("checkIdTerminal[]").read(session) != null;
                if(checkIdTerminal) {
                    ObjectValue terminalObject = terminalHandlerLM.findProperty("terminal[VARSTRING[100]]").readClasses(session, new DataObject(idTerminal));
                    return terminalObject instanceof DataObject && terminalHandlerLM.findProperty("blocked[Terminal]").read(session, terminalObject) == null;
                }
            }
            return true;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public DataObject login(DataSession session, ExecutionStack stack, String login, String password, String idTerminal) throws RemoteException, SQLException {
        try {

            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
            if(terminalHandlerLM != null) {
                ObjectValue customUser = terminalHandlerLM.findProperty("customUserUpcase[?]").readClasses(session, new DataObject(login.toUpperCase()));
                boolean authenticated = customUser instanceof DataObject && getLogicsInstance().getBusinessLogics().authenticationLM.checkPassword((DataObject) customUser, password, stack);
                DataObject result = authenticated ? (DataObject) customUser : null;
                if(result != null) {
                    ObjectValue terminalObject = terminalHandlerLM.findProperty("terminal[VARSTRING[100]]").readClasses(session, new DataObject(idTerminal));
                    if(terminalObject instanceof DataObject) {
                        terminalHandlerLM.findProperty("lastConnectionTime[Terminal]").change(new Timestamp(Calendar.getInstance().getTime().getTime()), session, (DataObject) terminalObject);
                        terminalHandlerLM.findProperty("lastUser[Terminal]").change(result.getValue(), session, (DataObject) terminalObject);
                        String applyMessage = session.applyMessage(getLogicsInstance().getBusinessLogics(), stack);
                        if(applyMessage != null)
                            ServerLoggers.systemLogger.error(String.format("Terminal Login error: %s, login %s, terminal %s", applyMessage, login, idTerminal));
                    }
                }
                return result;
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }


}