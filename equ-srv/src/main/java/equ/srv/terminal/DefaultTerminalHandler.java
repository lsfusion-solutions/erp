package equ.srv.terminal;

import com.google.common.base.Throwables;
import equ.api.terminal.*;
import equ.srv.EquipmentLoggers;
import equ.srv.ServerTerminalOrder;
import equ.srv.TerminalEquipmentServer;
import lsfusion.base.BaseUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.file.RawFileData;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.NullValue;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.LogicsInstance;
import lsfusion.server.logics.action.controller.stack.ExecutionStack;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.physics.dev.integration.service.*;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static lsfusion.base.BaseUtils.trim;
import static lsfusion.base.BaseUtils.trimToEmpty;

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
    public void init() {
        TerminalEquipmentServer.init(getLogicsInstance().getBusinessLogics());
    }

    @Override
    public List<Object> readHostPort(DataSession session) {
        try {
            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
            if (terminalHandlerLM != null) {
                String host = (String) terminalHandlerLM.findProperty("hostTerminalServer[]").read(session);
                Integer port = (Integer) terminalHandlerLM.findProperty("portTerminalServer[]").read(session);
                return Arrays.asList(host, port);
            } else return new ArrayList<>();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Object readItem(DataSession session, DataObject user, String barcode, String bin) {
        try {
            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
            if(terminalHandlerLM != null) {
                ObjectValue barcodeObject = terminalHandlerLM.findProperty("barcode[BPSTRING[15]]").readClasses(session, new DataObject(barcode));
                ObjectValue stockObject = user == null ? NullValue.instance : terminalHandlerLM.findProperty("stock[Employee]").readClasses(session, user);
                String overNameSku = (String) terminalHandlerLM.findProperty("overNameSku[Barcode,Stock]").read(session, barcodeObject, stockObject);
                if(overNameSku == null)
                    return null;
                String isWeight = terminalHandlerLM.findProperty("passScales[Barcode]").read(session, barcodeObject) != null ? "1" : "0";
                ObjectValue skuObject = terminalHandlerLM.findProperty("skuBarcode[BPSTRING[15]]").readClasses(session, new DataObject(barcode));
                BigDecimal price = (BigDecimal) terminalHandlerLM.findProperty("currentPriceInTerminal[Barcode,Stock]").read(session, barcodeObject, stockObject);
                BigDecimal quantity = (BigDecimal) terminalHandlerLM.findProperty("currentBalance[Sku,Stock]").read(session, skuObject, stockObject);
                String priceValue = null;
                if(price != null) {
                    price = price.setScale(2, BigDecimal.ROUND_HALF_UP);
                    DecimalFormat df = new DecimalFormat();
                    df.setMaximumFractionDigits(2);
                    df.setMinimumFractionDigits(0);
                    df.setGroupingUsed(false);
                    priceValue = df.format(price).replace(",", ".");
                }
                String mainBarcode = (String) terminalHandlerLM.findProperty("idMainBarcode[Barcode]").read(session, barcodeObject);

                String idSkuBarcode = trimToEmpty((String) terminalHandlerLM.findProperty("idSku[Barcode]").read(session, barcodeObject));
                String nameManufacturer = trimToEmpty((String) terminalHandlerLM.findProperty("nameManufacturer[Barcode]").read(session, barcodeObject));

                String fld3 = (String) terminalHandlerLM.findProperty("fld3[Barcode, Stock]").read(session, barcodeObject, stockObject);
                String fld4 = (String) terminalHandlerLM.findProperty("fld4[Barcode, Stock]").read(session, barcodeObject, stockObject);
                String color = formatColor((Color) terminalHandlerLM.findProperty("color[Sku, Stock]").read(session, skuObject, stockObject));
                String ticket_data = (String) terminalHandlerLM.findProperty("extInfo[Barcode, Stock]").read(session, barcodeObject, stockObject);
                String flags = terminalHandlerLM.findProperty("needManufacturingDate[Barcode]").read(session, barcodeObject) != null ? "1" : "0";
                return Arrays.asList(barcode, overNameSku, priceValue == null ? "0" : priceValue,
                        quantity == null ? "0" : String.valueOf(quantity.longValue()), idSkuBarcode, nameManufacturer, fld3, fld4, "", isWeight,
                        mainBarcode, color, ticket_data, flags);
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private String formatColor(Color color) {
        return color == null ? "" : String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    @Override
    public String readItemHtml(DataSession session, String barcode, String idStock) {
        try {
            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
            if(terminalHandlerLM != null) {
                ObjectValue stockObject = terminalHandlerLM.findProperty("stock[STRING[100]]").readClasses(session, new DataObject(idStock));
                String overNameSku = (String) terminalHandlerLM.findProperty("overNameSku[Barcode,Stock]").read(session,
                        terminalHandlerLM.findProperty("barcode[BPSTRING[15]]").readClasses(session, new DataObject(barcode)), stockObject);
                if(overNameSku == null)
                    return null;

                ObjectValue barcodeObject = terminalHandlerLM.findProperty("barcode[BPSTRING[15]]").readClasses(session, new DataObject(barcode));
                BigDecimal price = null;
                BigDecimal oldPrice = null;
                if(barcodeObject instanceof DataObject && stockObject instanceof DataObject) {
                    price = (BigDecimal) terminalHandlerLM.findProperty("currentPriceInTerminal[Barcode,Stock]").read(session, barcodeObject, stockObject);
                    oldPrice = (BigDecimal) terminalHandlerLM.findProperty("currentPriceInTerminal[Barcode,Stock]").read(session, barcodeObject, stockObject);
                }
                boolean action = price != null && oldPrice != null && price.compareTo(oldPrice) == 0;

                return action ?
                        String.format("<html><body bgcolor=\"#FFFF00\">Наименование: <b>%s</b><br/><b><font color=\"#FF0000\">Акция</font></b> Цена: <b>%s</b>, Скидка: <b>%s</b></body></html>",
                                overNameSku, String.valueOf(price.doubleValue()), String.valueOf(oldPrice.doubleValue() - price.doubleValue()))
                        : String.format("<html><body><div align=center><font size=+4><b>%s</b><br><br><br>Цена: <b>%s</b></font></div></body></html>",
                        overNameSku, price == null ? "0" : String.valueOf(price.doubleValue()));
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public RawFileData readBase(DataSession session, DataObject userObject) throws SQLException {
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
                List<TerminalBarcode> barcodeList = readBarcodeList(session, stockObject);

                List<ServerTerminalOrder> orderList = TerminalEquipmentServer.readTerminalOrderList(session, stockObject);
                Map<String, List<String>> extraBarcodeMap = readExtraBarcodeMap(session);

                List<TerminalAssortment> assortmentList = TerminalEquipmentServer.readTerminalAssortmentList(session, BL, priceListTypeObject, stockObject);
                List<TerminalHandbookType> handbookTypeList = TerminalEquipmentServer.readTerminalHandbookTypeList(session, BL);
                List<TerminalDocumentType> terminalDocumentTypeList = TerminalEquipmentServer.readTerminalDocumentTypeList(session, BL, userObject);
                List<TerminalLegalEntity> customANAList = TerminalEquipmentServer.readCustomANAList(session, BL, userObject);
                file = File.createTempFile("terminalHandler", ".db");

                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());

                createGoodsTable(connection);
                updateGoodsTable(connection, barcodeList, orderList, extraBarcodeMap);

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

                return new RawFileData(zipFile);
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

    @Override
    public String savePallet(DataSession session, ExecutionStack stack, DataObject userObject, String numberPallet, String nameBin) {
        return null;
    }

    @Override
    public String checkOrder(DataSession session, ExecutionStack stack, DataObject user, String numberOrder) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String result = null;
        ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
        if(terminalHandlerLM != null) {
            terminalHandlerLM.findAction("checkOrder[STRING]").execute(session, stack, new DataObject(numberOrder));
            result = (String) terminalHandlerLM.findProperty("checkOrderResult[]").read(session);
        }
        return result;
    }

    @Override
    public String getPreferences(DataSession session, ExecutionStack stack, String idTerminal) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String result = null;
        ScriptingLogicsModule terminalPreferencesLM = getLogicsInstance().getBusinessLogics().getModule("TerminalPreferences");
        if(terminalPreferencesLM != null) {
            terminalPreferencesLM.findAction("getTerminalPreferences[STRING]").execute(session, stack, new DataObject(idTerminal));
            result = (String) terminalPreferencesLM.findProperty("terminalPreferencesJSON[]").read(session);
        }
        return result;
    }

    private List<TerminalBarcode> readBarcodeList(DataSession session, ObjectValue stockObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalBarcode> result = new ArrayList<>();
        ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
        if(terminalHandlerLM != null) {
            boolean skipGoodsInReadBase = terminalHandlerLM.findProperty("skipGoodsInReadBase[]").read(session) != null;
            if(!skipGoodsInReadBase) {
                boolean currentPrice = terminalHandlerLM.findProperty("useCurrentPriceInTerminal").read(session) != null;
                boolean allItems = terminalHandlerLM.findProperty("sendAllItems").read(session) != null;
                boolean onlyActiveItems = terminalHandlerLM.findProperty("sendOnlyActiveItems").read(session) != null;
                boolean currentQuantity = terminalHandlerLM.findProperty("useCurrentQuantityInTerminal").read(session) != null;
                boolean filterCurrentQuantity = terminalHandlerLM.findProperty("filterCurrentQuantityInTerminal").read(session) != null;

                KeyExpr barcodeExpr = new KeyExpr("barcode");
                ImRevMap<Object, KeyExpr> barcodeKeys = MapFact.singletonRev("barcode", barcodeExpr);

                QueryBuilder<Object, Object> barcodeQuery = new QueryBuilder<>(barcodeKeys);
                barcodeQuery.addProperty("idBarcode", terminalHandlerLM.findProperty("id[Barcode]").getExpr(barcodeExpr));
                barcodeQuery.addProperty("idSkuBarcode", terminalHandlerLM.findProperty("idSku[Barcode]").getExpr(barcodeExpr));
                barcodeQuery.addProperty("nameManufacturer", terminalHandlerLM.findProperty("nameManufacturer[Barcode]").getExpr(barcodeExpr));
                barcodeQuery.addProperty("passScales", terminalHandlerLM.findProperty("passScales[Barcode]").getExpr(barcodeExpr));
                barcodeQuery.addProperty("extInfo", terminalHandlerLM.findProperty("extInfo[Barcode, Stock]").getExpr(barcodeExpr, stockObject.getExpr()));
                barcodeQuery.addProperty("fld3", terminalHandlerLM.findProperty("fld3[Barcode, Stock]").getExpr(barcodeExpr, stockObject.getExpr()));
                barcodeQuery.addProperty("fld4", terminalHandlerLM.findProperty("fld4[Barcode, Stock]").getExpr(barcodeExpr, stockObject.getExpr()));
                barcodeQuery.addProperty("needManufacturingDate", terminalHandlerLM.findProperty("needManufacturingDate[Barcode]").getExpr(barcodeExpr));
                barcodeQuery.addProperty("price", terminalHandlerLM.findProperty("currentPriceInTerminal[Barcode,Stock]").getExpr(barcodeExpr, stockObject.getExpr()));
                if(stockObject instanceof DataObject && !allItems)
                    barcodeQuery.and(terminalHandlerLM.findProperty("currentPriceInTerminal[Barcode,Stock]").getExpr(barcodeExpr, stockObject.getExpr()).getWhere());
                if (currentQuantity)
                    barcodeQuery.addProperty("quantity", terminalHandlerLM.findProperty("currentBalance[Barcode,Stock]").getExpr(barcodeExpr, stockObject.getExpr()));
                if (filterCurrentQuantity)
                    barcodeQuery.and(terminalHandlerLM.findProperty("currentBalance[Barcode,Stock]").getExpr(barcodeExpr, stockObject.getExpr()).getWhere());
                if (onlyActiveItems) {
                    barcodeQuery.and(terminalHandlerLM.findProperty("activeItem[Barcode]").getExpr(barcodeExpr).getWhere());
                }

                barcodeQuery.addProperty("mainBarcode", terminalHandlerLM.findProperty("idMainBarcode[Barcode]").getExpr(barcodeExpr));

                String[] barcodeStockNames = new String[]{"overNameSku", "color"};
                LP[] barcodeStockProperties = terminalHandlerLM.findProperties("overNameSku[Barcode,Stock]", "color[Barcode,Stock]");
                for (int i = 0; i < barcodeStockNames.length; i++) {
                    barcodeQuery.addProperty(barcodeStockNames[i], barcodeStockProperties[i].getExpr(barcodeExpr, stockObject.getExpr()));
                }

                barcodeQuery.and(terminalHandlerLM.findProperty("id[Barcode]").getExpr(barcodeExpr).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> barcodeResult = barcodeQuery.execute(session);
                for (ImMap<Object, Object> entry : barcodeResult.values()) {

                    String idBarcode = trim((String) entry.get("idBarcode"));
                    String overNameSku = trim((String) entry.get("overNameSku"));
                    BigDecimal price = (BigDecimal) entry.get("price");
                    BigDecimal quantityBarcodeStock = currentQuantity ? (BigDecimal) entry.get("quantity") : BigDecimal.ONE;
                    if (quantityBarcodeStock == null)
                        quantityBarcodeStock = BigDecimal.ZERO;

                    String idSkuBarcode = trim((String) entry.get("idSkuBarcode"));
                    String nameManufacturer = trim((String) entry.get("nameManufacturer"));
                    String isWeight = entry.get("passScales") != null ? "1" : "0";
                    String mainBarcode = trim((String) entry.get("mainBarcode"));
                    String color = formatColor((Color) entry.get("color"));
                    String extInfo = trim((String) entry.get("extInfo"));
                    String fld3 = trim((String) entry.get("fld3"));
                    String fld4 = trim((String) entry.get("fld4"));
                    boolean needManufacturingDate = entry.get("needManufacturingDate") != null;

                    result.add(new TerminalBarcode(idBarcode, overNameSku, price, quantityBarcodeStock, idSkuBarcode,
                            nameManufacturer, isWeight, mainBarcode, color, extInfo, fld3, fld4, needManufacturingDate));

                }
            }
        }
        return result;
    }

    private Map<String, List<String>> readExtraBarcodeMap(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, List<String>> result = new HashMap<>();
        ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
        if (terminalHandlerLM != null) {

            KeyExpr barcodeExpr = new KeyExpr("barcode");
            ImRevMap<Object, KeyExpr> barcodeKeys = MapFact.singletonRev("barcode", barcodeExpr);

            QueryBuilder<Object, Object> barcodeQuery = new QueryBuilder<>(barcodeKeys);
            barcodeQuery.addProperty("idBarcode", terminalHandlerLM.findProperty("id[Barcode]").getExpr(barcodeExpr));
            barcodeQuery.addProperty("mainBarcode", terminalHandlerLM.findProperty("idMainBarcode[Barcode]").getExpr(barcodeExpr));
            barcodeQuery.and(terminalHandlerLM.findProperty("id[Barcode]").getExpr(barcodeExpr).getWhere());
            barcodeQuery.and(terminalHandlerLM.findProperty("idMainBarcode[Barcode]").getExpr(barcodeExpr).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> barcodeResult = barcodeQuery.execute(session);
            for (ImMap<Object, Object> entry : barcodeResult.values()) {

                String idBarcode = BaseUtils.trim((String) entry.get("idBarcode"));
                String mainBarcode = BaseUtils.trim((String) entry.get("mainBarcode"));

                List<String> barcodeList = result.get(mainBarcode);
                if (barcodeList == null)
                    barcodeList = new ArrayList<>();
                barcodeList.add(idBarcode);
                result.put(mainBarcode, barcodeList);
            }
        }
        return result;
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
                " color TEXT," +
                " field1   TEXT," +
                " field2   TEXT," +
                " field3   TEXT," +
                " pos_field1   TEXT," +
                " pos_field2   TEXT," +
                " pos_field3   TEXT," +
                " mindate1 TEXT," +
                " maxdate1 TEXT," +
                " vop TEXT," +
                "PRIMARY KEY (num, barcode))";
        statement.executeUpdate(sql);
        statement.execute("CREATE INDEX zayavki_post ON zayavki (post);");
        statement.close();
    }

    private void updateOrderTable(Connection connection, List<ServerTerminalOrder> terminalOrderList, String prefix) throws SQLException {
        if (!terminalOrderList.isEmpty() && prefix != null) {
            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO zayavki VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
                statement = connection.prepareStatement(sql);
                for (ServerTerminalOrder order : terminalOrderList) {
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
                        statement.setObject(11,formatValue(order.color));
                        statement.setObject(12,formatValue(order.headField1));
                        statement.setObject(13,formatValue(order.headField2));
                        statement.setObject(14,formatValue(order.headField3));
                        statement.setObject(15,formatValue(order.posField1));
                        statement.setObject(16,formatValue(order.posField2));
                        statement.setObject(17,formatValue(order.posField3));
                        statement.setObject(18, formatValue(order.minDate1));
                        statement.setObject(19, formatValue(order.maxDate1));
                        statement.setObject(20, formatValue(order.vop));
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
                " weight  TEXT," +
                " main_barcode TEXT," +
                " color TEXT," +
                " ticket_data TEXT," +
                " flags INTEGER)";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateGoodsTable(Connection connection, List<TerminalBarcode> barcodeList, List<ServerTerminalOrder> orderList,
                                  Map<String, List<String>> extraBarcodeMap) throws SQLException {
        if (!barcodeList.isEmpty() || !orderList.isEmpty()) {
            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO goods VALUES(?, ?, ?, ?, ?, ?, ?, ?, '', '', ?, ?, ?, ?, ?);";
                statement = connection.prepareStatement(sql);
                Set<String> usedBarcodes = new HashSet<>();
                for (TerminalBarcode barcode : barcodeList) {
                    if (barcode.idBarcode != null) {
                        statement.setObject(1, formatValue(barcode.idBarcode)); //idBarcode
                        statement.setObject(2, formatValue(barcode.nameSku)); //name
                        statement.setObject(3, formatValue(barcode.price)); //price
                        statement.setObject(4, formatValue(barcode.quantityBarcodeStock)); //quantity
                        statement.setObject(5, formatValue(barcode.idSkuBarcode)); //idItem, fld1
                        statement.setObject(6, formatValue(barcode.nameManufacturer)); //manufacturer, fld2
                        statement.setObject(7, formatValue(barcode.fld3)); //fld3
                        statement.setObject(8, formatValue(barcode.fld4)); //fld4
                        statement.setObject(9, formatValue(barcode.isWeight)); //weight
                        statement.setObject(10, formatValue(barcode.mainBarcode)); //main_barcode
                        statement.setObject(11, formatValue(barcode.color)); //color
                        statement.setObject(12, formatValue(barcode.extInfo)); //ticket_data
                        statement.setObject(13, barcode.needManufacturingDate ? 1 : 0); //flags
                        statement.addBatch();
                        usedBarcodes.add(barcode.idBarcode);
                    }
                }
                for (TerminalOrder order : orderList) {
                    if (order.barcode != null) {
                        List<String> extraBarcodeList = extraBarcodeMap.get(order.barcode);
                        if (extraBarcodeList != null) {
                            for (String extraBarcode : extraBarcodeList) {
                                if(!usedBarcodes.contains(extraBarcode)) {
                                    statement.setObject(1, formatValue(extraBarcode)); //idBarcode
                                    statement.setObject(2, formatValue(order.name)); //name
                                    statement.setObject(3, formatValue(order.price)); //price
                                    statement.setObject(4, formatValue(order.quantity)); //quantity
                                    statement.setObject(5, formatValue(order.idItem)); //idItem, fld1
                                    statement.setObject(6, formatValue(order.manufacturer)); //manufacturer, fld2
                                    statement.setObject(7, ""); //fld3
                                    statement.setObject(8, ""); //fld4
                                    statement.setObject(9, formatValue(order.weight)); //weight
                                    statement.setObject(10, formatValue(order.barcode)); //main_barcode
                                    statement.setObject(11, ""); //color
                                    statement.setObject(12, ""); //ticket_data
                                    statement.addBatch();
                                }
                            }
                        } else {
                            if(!usedBarcodes.contains(order.barcode)) {
                                statement.setObject(1, formatValue(order.barcode)); //idBarcode
                                statement.setObject(2, formatValue(order.name)); //name
                                statement.setObject(3, formatValue(order.price)); //price
                                statement.setObject(4, formatValue(order.quantity)); //quantity
                                statement.setObject(5, formatValue(order.idItem)); //idItem, fld1
                                statement.setObject(6, formatValue(order.manufacturer)); //manufacturer, fld2
                                statement.setObject(7, ""); //fld3
                                statement.setObject(8, ""); //fld4
                                statement.setObject(9, formatValue(order.weight)); //weight
                                statement.setObject(10, formatValue(order.barcode)); //main_barcode
                                statement.setObject(11, ""); //color
                                statement.setObject(12, ""); //ticket_data
                                statement.addBatch();
                            }
                        }
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
        statement.execute("CREATE INDEX assort_k ON assort (post,barcode);");
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
                " fld3 TEXT," +
                " ticket TEXT)";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateANATable(Connection connection, List<TerminalLegalEntity> customANAList) throws SQLException {
        if (!customANAList.isEmpty()) {
            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO ana VALUES(?, ?, '', '', '', ?);";
                statement = connection.prepareStatement(sql);
                for (TerminalLegalEntity legalEntity : customANAList) {
                    if (legalEntity.idLegalEntity != null) {
                        statement.setObject(1, formatValue(legalEntity.idLegalEntity)); //ana
                        statement.setObject(2, formatValue(legalEntity.nameLegalEntity)); //naim
                        statement.setObject(3, formatValue(legalEntity.extInfo)); //ticket
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
                        statement.setObject(7, formatValue(tdt.flag == null ? "0" : tdt.flag));
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
        return value == null ? "" : value instanceof LocalDate ? ((LocalDate) value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) : value;
    }

    @Override
    public String importTerminalDocument(DataSession session, ExecutionStack stack, DataObject userObject, String idTerminal,
                                         String idTerminalDocument, List<List<Object>> terminalDocumentDetailList, boolean emptyDocument) {
        try {

            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
            if(terminalHandlerLM != null) {

                List<ImportProperty<?>> props = new ArrayList<>();
                List<ImportField> fields = new ArrayList<>();
                List<ImportKey<?>> keys = new ArrayList<>();

                ImportField idTerminalDocumentField = new ImportField(terminalHandlerLM.findProperty("id[TerminalDocument]"));
                ImportKey<?> terminalDocumentKey = new ImportKey((ConcreteCustomClass) terminalHandlerLM.findClass("TerminalDocument"),
                        terminalHandlerLM.findProperty("terminalDocument[STRING[1000]]").getMapping(idTerminalDocumentField));
                keys.add(terminalDocumentKey);
                props.add(new ImportProperty(idTerminalDocumentField, terminalHandlerLM.findProperty("id[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(idTerminalDocumentField);

                ImportField numberTerminalDocumentField = new ImportField(terminalHandlerLM.findProperty("title[TerminalDocument]"));
                props.add(new ImportProperty(numberTerminalDocumentField, terminalHandlerLM.findProperty("title[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(numberTerminalDocumentField);

                ImportField idTerminalDocumentTypeField = new ImportField(terminalHandlerLM.findProperty("id[TerminalDocumentType]"));
                ImportKey<?> terminalDocumentTypeKey = new ImportKey((ConcreteCustomClass) terminalHandlerLM.findClass("TerminalDocumentType"),
                        terminalHandlerLM.findProperty("terminalDocumentType[STRING[100]]").getMapping(idTerminalDocumentTypeField));
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
                            terminalHandlerLM.findProperty("terminalIdTerminalId[STRING[1000],STRING[1000]]").getMapping(idTerminalDocumentField, idTerminalDocumentDetailField));
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

                    ImportField extraField1TerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("extraField1[TerminalDocumentDetail]"));
                    props.add(new ImportProperty(extraField1TerminalDocumentDetailField, terminalHandlerLM.findProperty("extraField1[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    fields.add(extraField1TerminalDocumentDetailField);

                    ImportField extraField2TerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("extraField2[TerminalDocumentDetail]"));
                    props.add(new ImportProperty(extraField2TerminalDocumentDetailField, terminalHandlerLM.findProperty("extraField2[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    fields.add(extraField2TerminalDocumentDetailField);

                    ImportField extraField3TerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("extraField3[TerminalDocumentDetail]"));
                    props.add(new ImportProperty(extraField3TerminalDocumentDetailField, terminalHandlerLM.findProperty("extraField3[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    fields.add(extraField3TerminalDocumentDetailField);

                    ImportField parentDocumentTerminalDocumentField = new ImportField(terminalHandlerLM.findProperty("parentDocument[TerminalDocument]"));
                    props.add(new ImportProperty(parentDocumentTerminalDocumentField, terminalHandlerLM.findProperty("parentDocument[TerminalDocument]").getMapping(terminalDocumentKey)));
                    fields.add(parentDocumentTerminalDocumentField);
                }

                ImportTable table = new ImportTable(fields, terminalDocumentDetailList);

                EquipmentLoggers.terminalLogger.info("start importing terminal document " + idTerminalDocument);
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);

                ObjectValue terminalDocumentObject = terminalHandlerLM.findProperty("terminalDocument[STRING[1000]]").readClasses(session, session.getModifier(), session.getQueryEnv(), new DataObject(idTerminalDocument));
                terminalHandlerLM.findProperty("createdUser[TerminalDocument]").change(userObject, session, (DataObject) terminalDocumentObject);
                if(idTerminal != null) {
                    ObjectValue terminalObject = terminalHandlerLM.findProperty("terminal[STRING[100]]").readClasses(session, new DataObject(idTerminal));
                    if (terminalObject instanceof DataObject)
                        terminalHandlerLM.findProperty("createdTerminal[TerminalDocument]").change(terminalObject, session, (DataObject) terminalDocumentObject);
                }
                terminalHandlerLM.findAction("process[TerminalDocument]").execute(session, stack, terminalDocumentObject);
                EquipmentLoggers.terminalLogger.info("start applying terminal document " + idTerminalDocument);
                String result = session.applyMessage(getLogicsInstance().getBusinessLogics(), stack);
                if(result != null) {
                    EquipmentLoggers.terminalLogger.error(String.format("Apply terminal document %s error: %s", idTerminalDocument, result));
                }
                return result;

            } else return "-1";

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public boolean isActiveTerminal(DataSession session, ExecutionStack stack, String idTerminal) {
        try {
            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
            if (terminalHandlerLM != null) {
                boolean checkIdTerminal = terminalHandlerLM.findProperty("checkIdTerminal[]").read(session) != null;
                ObjectValue terminalObject = terminalHandlerLM.findProperty("terminal[STRING[100]]").readClasses(session, new DataObject(idTerminal));
                if(checkIdTerminal) {
                     if(terminalObject instanceof DataObject) {
                        return terminalHandlerLM.findProperty("blocked[Terminal]").read(session, terminalObject) == null;
                    } else {
                        ObjectValue defaultGroup = terminalHandlerLM.findProperty("defaultGroupTerminal[]").readClasses(session);
                        if(defaultGroup instanceof DataObject) {
                            terminalObject = session.addObject((ConcreteCustomClass) terminalHandlerLM.findClass("Terminal"));
                            terminalHandlerLM.findProperty("groupTerminal[Terminal]").change(defaultGroup, session, (DataObject) terminalObject);
                            terminalHandlerLM.findProperty("id[Terminal]").change(idTerminal, session, (DataObject) terminalObject);
                            Integer npp = (Integer) terminalHandlerLM.findProperty("maxNpp[]").read(session);
                            terminalHandlerLM.findProperty("npp[Terminal]").change(npp == null ? 1 : (npp + 1), session, (DataObject) terminalObject);
                            terminalHandlerLM.findProperty("blocked[Terminal]").change(true, session, (DataObject) terminalObject);
                            String applyMessage = session.applyMessage(getLogicsInstance().getBusinessLogics(), stack);
                            if (applyMessage != null)
                                ServerLoggers.systemLogger.error(String.format("Terminal IsActive error: %s, terminal %s", applyMessage, idTerminal));
                        }
                         return false;
                    }
                } else if (terminalObject instanceof NullValue) {
                    ObjectValue defaultGroup = terminalHandlerLM.findProperty("defaultGroupTerminal[]").readClasses(session);
                    if (defaultGroup instanceof DataObject) {
                        terminalObject = session.addObject((ConcreteCustomClass) terminalHandlerLM.findClass("Terminal"));
                        terminalHandlerLM.findProperty("groupTerminal[Terminal]").change(defaultGroup, session, (DataObject) terminalObject);
                        terminalHandlerLM.findProperty("id[Terminal]").change(idTerminal, session, (DataObject) terminalObject);
                        Integer npp = (Integer) terminalHandlerLM.findProperty("maxNpp[]").read(session);
                        terminalHandlerLM.findProperty("npp[Terminal]").change(npp == null ? 1 : (npp + 1), session, (DataObject) terminalObject);
                        terminalHandlerLM.findProperty("blocked[Terminal]").change(true, session, (DataObject) terminalObject);
                        String applyMessage = session.applyMessage(getLogicsInstance().getBusinessLogics(), stack);
                        if (applyMessage != null)
                            ServerLoggers.systemLogger.error(String.format("Terminal IsActive error: %s, terminal %s", applyMessage, idTerminal));
                    }
                }
            }
            return true;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public DataObject login(DataSession session, ExecutionStack stack, String login, String password, String idTerminal) {
        try {

            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
            if(terminalHandlerLM != null) {
                ObjectValue customUser = terminalHandlerLM.findProperty("customUserUpcase[?]").readClasses(session, new DataObject(login.toUpperCase()));
                boolean authenticated = customUser instanceof DataObject && getLogicsInstance().getBusinessLogics().authenticationLM.checkPassword(session, (DataObject) customUser, password, stack);
                DataObject result = authenticated ? (DataObject) customUser : null;
                if(result != null) {
                    ObjectValue terminalObject = terminalHandlerLM.findProperty("terminal[STRING[100]]").readClasses(session, new DataObject(idTerminal));
                    if(terminalObject instanceof DataObject) {
                        terminalHandlerLM.findProperty("lastConnectionTime[Terminal]").change(LocalDateTime.now(), session, (DataObject) terminalObject);
                        terminalHandlerLM.findProperty("lastUser[Terminal]").change(result, session, (DataObject) terminalObject);
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

    private class TerminalBarcode {
        String idBarcode;
        String nameSku;
        BigDecimal price;
        BigDecimal quantityBarcodeStock;
        String idSkuBarcode;
        String nameManufacturer;
        String isWeight;
        String mainBarcode;
        String color;
        String extInfo;
        String fld3;
        String fld4;
        boolean needManufacturingDate;

        public TerminalBarcode(String idBarcode, String nameSku, BigDecimal price, BigDecimal quantityBarcodeStock,
                               String idSkuBarcode, String nameManufacturer, String isWeight, String mainBarcode, String color,
                               String extInfo, String fld3, String fld4, boolean needManufacturingDate) {
            this.idBarcode = idBarcode;
            this.nameSku = nameSku;
            this.price = price;
            this.quantityBarcodeStock = quantityBarcodeStock;
            this.idSkuBarcode = idSkuBarcode;
            this.nameManufacturer = nameManufacturer;
            this.isWeight = isWeight;
            this.mainBarcode = mainBarcode;
            this.color = color;
            this.extInfo = extInfo;
            this.fld3 = fld3;
            this.fld4 = fld4;
            this.needManufacturingDate = needManufacturingDate;
        }
    }

}