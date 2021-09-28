package lsfusion.erp.machinery.terminal;

import com.google.common.base.Throwables;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.ERPLoggers;
import lsfusion.interop.form.property.Compare;
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
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.property.classes.IsClassProperty;
import lsfusion.server.logics.property.oraction.PropertyInterface;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.physics.dev.integration.service.*;
import org.apache.commons.lang3.StringUtils;

import java.awt.*;
import java.io.*;
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

public class DefaultTerminalHandler {

    static ScriptingLogicsModule terminalOrderLM;
    static ScriptingLogicsModule terminalHandlerLM;

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

    public void init() {
        terminalOrderLM = getLogicsInstance().getBusinessLogics().getModule("TerminalOrder");
        terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
    }

    public List<Object> readHostPort(DataSession session) {
        try {
            if (terminalHandlerLM != null) {
                String host = (String) terminalHandlerLM.findProperty("hostTerminalServer[]").read(session);
                Integer port = (Integer) terminalHandlerLM.findProperty("portTerminalServer[]").read(session);
                return Arrays.asList(host, port);
            } else return new ArrayList<>();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public Object readItem(DataSession session, DataObject user, String barcode, String bin) {
        try {
            if(terminalHandlerLM != null) {
                ObjectValue barcodeObject = terminalHandlerLM.findProperty("barcode[BPSTRING[15]]").readClasses(session, new DataObject(barcode));
                ObjectValue stockObject = user == null ? NullValue.instance : terminalHandlerLM.findProperty("stock[Employee]").readClasses(session, user);
                String overNameSku = (String) terminalHandlerLM.findProperty("overNameSku[Barcode,Stock]").read(session, barcodeObject, stockObject);
                if(overNameSku == null)
                    return null;
                String isWeight = terminalHandlerLM.findProperty("passScales[Barcode]").read(session, barcodeObject) != null ? "1" : "0";
                ObjectValue skuObject = terminalHandlerLM.findProperty("skuBarcode[BPSTRING[15]]").readClasses(session, new DataObject(barcode));
                BigDecimal price = (BigDecimal) terminalHandlerLM.findProperty("currentPriceInTerminal[Barcode,Stock]").read(session, barcodeObject, stockObject);
                BigDecimal quantity = (BigDecimal) terminalHandlerLM.findProperty("currentBalance[Barcode,CustomUser]").read(session, barcodeObject, user);
                String priceValue = bigDecimalToString(price, 2);
                String quantityValue = bigDecimalToString(quantity, 3);

                String mainBarcode = (String) terminalHandlerLM.findProperty("idMainBarcode[Barcode]").read(session, barcodeObject);

                String idSkuBarcode = trimToEmpty((String) terminalHandlerLM.findProperty("idSku[Barcode]").read(session, barcodeObject));
                String nameManufacturer = trimToEmpty((String) terminalHandlerLM.findProperty("nameManufacturer[Barcode]").read(session, barcodeObject));

                String fld3 = (String) terminalHandlerLM.findProperty("fld3[Barcode, Stock]").read(session, barcodeObject, stockObject);
                String fld4 = (String) terminalHandlerLM.findProperty("fld4[Barcode, Stock]").read(session, barcodeObject, stockObject);
                String color = formatColor((Color) terminalHandlerLM.findProperty("color[Sku, Stock]").read(session, skuObject, stockObject));
                String ticket_data = (String) terminalHandlerLM.findProperty("extInfo[Barcode, Stock]").read(session, barcodeObject, stockObject);
                String flags = terminalHandlerLM.findProperty("needManufacturingDate[Barcode]").read(session, barcodeObject) != null ? "1" : "0";
                return Arrays.asList(barcode, overNameSku, priceValue == null ? "0" : priceValue,
                        quantityValue == null ? "0" : quantityValue, idSkuBarcode, nameManufacturer, fld3, fld4, "", isWeight,
                        mainBarcode, color, ticket_data, flags);
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private String bigDecimalToString(BigDecimal bd, int fractDigits) {
        String value = null;
        if(bd != null) {
            bd = bd.setScale(fractDigits, BigDecimal.ROUND_HALF_UP);
            DecimalFormat df = new DecimalFormat();
            df.setMaximumFractionDigits(fractDigits);
            df.setMinimumFractionDigits(0);
            df.setGroupingUsed(false);
            value = df.format(bd).replace(",", ".");
        }
        return value;
    }

    private static String formatDate(LocalDate date) {
        return date != null ? date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : null;
    }

    private static String formatColor(Color color) {
        return color == null ? null : String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    public String readItemHtml(DataSession session, String barcode, String idStock) {
        try {
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
                                overNameSku, price.doubleValue(), (oldPrice.doubleValue() - price.doubleValue()))
                        : String.format("<html><body><div align=center><font size=+4><b>%s</b><br><br><br>Цена: <b>%s</b></font></div></body></html>",
                        overNameSku, price == null ? "0" : String.valueOf(price.doubleValue()));
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public RawFileData readBase(DataSession session, UserInfo userInfo, boolean readBatch) {
        File file = null;
        try {

            BusinessLogics BL = getLogicsInstance().getBusinessLogics();
            if (terminalHandlerLM != null) {

                boolean imagesInReadBase = terminalHandlerLM.findProperty("imagesInReadBase[]").read(session) != null;
                String baseZipDirectory = (String) terminalHandlerLM.findProperty("baseZipDirectory[]").read(session);
                ObjectValue stockObject = terminalHandlerLM.findProperty("stock[Employee]").readClasses(session, userInfo.user);
                //если prefix null, то таблицу не выгружаем. Если prefix пустой (skipPrefix), то таблицу выгружаем, но без префикса
                String prefix = (String) terminalHandlerLM.findProperty("exportId[]").read(session);
                List<TerminalBarcode> barcodeList = readBarcodeList(session, stockObject, imagesInReadBase, userInfo.user);

                List<TerminalBatch> batchList = null;
                if (readBatch)
                    batchList = readBatchList(session, stockObject);

                List<TerminalOrder> orderList = readTerminalOrderList(session, stockObject, userInfo);
                Map<String, RawFileData> orderImages = imagesInReadBase ? readTerminalOrderImages(session, stockObject, userInfo) : new HashMap<>();

                List<TerminalAssortment> assortmentList = readTerminalAssortmentList(session, stockObject);
                List<TerminalHandbookType> handbookTypeList = readTerminalHandbookTypeList(session);
                List<TerminalDocumentType> terminalDocumentTypeList = readTerminalDocumentTypeListServer(session, userInfo.user);
                List<TerminalLegalEntity> customANAList = readCustomANAList(session, BL, userInfo.user);
                file = File.createTempFile("terminalHandler", ".db");

                Class.forName("org.sqlite.JDBC");
                try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath())) {

                    createGoodsTable(connection);
                    updateGoodsTable(connection, barcodeList, orderList, orderImages, imagesInReadBase);

                    createBatchesTable(connection);
                    updateBatchesTable(connection, batchList, prefix);

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
                    File zipFile = baseZipDirectory == null ? File.createTempFile("base", ".zip") :
                            new File(String.format("%s/%s_%s.zip", baseZipDirectory,
                            terminalHandlerLM.findProperty("login[CustomUser]").read(session, userInfo.user),
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))));
                    try (FileOutputStream fos = new FileOutputStream(zipFile)) {
                        try (ZipOutputStream zos = new ZipOutputStream(fos)) {

                            try (FileInputStream is = new FileInputStream(file)) {
                                writeInputStreamToZip(is, zos, "tsd.db");
                            }

                            Set<String> usedImages = new HashSet<>();
                            for (TerminalBarcode barcode : barcodeList) {
                                if (barcode.image != null && !usedImages.contains(barcode.idBarcode)) {
                                    try (InputStream is = barcode.image.getInputStream()) {
                                        writeInputStreamToZip(is, zos, "images/" + barcode.idBarcode + ".jpg");
                                        usedImages.add(barcode.idBarcode);
                                    }
                                }
                            }

                            for (TerminalOrder order : orderList) {
                                RawFileData image = orderImages.get(order.barcode);
                                if (image != null && !usedImages.contains(order.barcode)) {
                                    try (InputStream is = image.getInputStream()) {
                                        writeInputStreamToZip(is, zos, "images/" + order.barcode + ".jpg");
                                        usedImages.add(order.barcode);
                                    }
                                }
                            }

                        }
                        return new RawFileData(zipFile);
                    } finally {
                        if (baseZipDirectory == null && !zipFile.delete()) {
                            zipFile.deleteOnExit();
                        }
                    }
                }
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            if(file != null && !file.delete())
                file.deleteOnExit();
        }
    }

    private void writeInputStreamToZip(InputStream fis, ZipOutputStream zos, String fileName) throws IOException {
        zos.putNextEntry(new ZipEntry(fileName));
        byte[] buf = new byte[1024];
        int len;
        while ((len = fis.read(buf)) > 0) {
            zos.write(buf, 0, len);
        }
    }

    public String savePallet(DataSession session, ExecutionStack stack, DataObject userObject, String numberPallet, String nameBin) {
        return null;
    }

    public String checkOrder(DataSession session, ExecutionStack stack, String numberOrder) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String result = null;
        if(terminalHandlerLM != null) {
            terminalHandlerLM.findAction("checkOrder[STRING]").execute(session, stack, new DataObject(numberOrder));
            result = (String) terminalHandlerLM.findProperty("checkOrderResult[]").read(session);
        }
        return result;
    }

    public String changeStatusOrder(DataSession session, ExecutionStack stack, String vop, String status, String numberOrder) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        if(terminalHandlerLM != null) {
            terminalHandlerLM.findAction("changeStatusTerminalOrder[STRING, STRING, STRING]").execute(session, stack, new DataObject(vop), new DataObject(status), new DataObject(numberOrder));
        }
        return null;
    }

    public String getPreferences(DataSession session, ExecutionStack stack, String idTerminal) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String result = null;
        ScriptingLogicsModule terminalPreferencesLM = getLogicsInstance().getBusinessLogics().getModule("TerminalPreferences");
        if(terminalPreferencesLM != null) {
            terminalPreferencesLM.findAction("getTerminalPreferences[STRING]").execute(session, stack, new DataObject(idTerminal));
            result = (String) terminalPreferencesLM.findProperty("terminalPreferencesJSON[]").read(session);
        }
        return result;
    }

    private List<TerminalBarcode> readBarcodeList(DataSession session, ObjectValue stockObject, boolean imagesInReadBase, DataObject user) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalBarcode> result = new ArrayList<>();
        if(terminalHandlerLM != null) {
            boolean skipGoodsInReadBase = terminalHandlerLM.findProperty("skipGoodsInReadBase[]").read(session) != null;
            if(!skipGoodsInReadBase) {
                boolean currentQuantity = terminalHandlerLM.findProperty("useCurrentQuantityInTerminal[]").read(session) != null;

                KeyExpr barcodeExpr = new KeyExpr("barcode");
                ImRevMap<Object, KeyExpr> barcodeKeys = MapFact.singletonRev("barcode", barcodeExpr);

                QueryBuilder<Object, Object> barcodeQuery = new QueryBuilder<>(barcodeKeys);
                barcodeQuery.addProperty("idBarcode", terminalHandlerLM.findProperty("id[Barcode]").getExpr(barcodeExpr));
                barcodeQuery.addProperty("overNameSku", terminalHandlerLM.findProperty("overNameSku[Barcode, Stock]").getExpr(barcodeExpr, stockObject.getExpr()));
                barcodeQuery.addProperty("price", terminalHandlerLM.findProperty("currentPriceInTerminal[Barcode,Stock]").getExpr(barcodeExpr, stockObject.getExpr()));
                if (currentQuantity) {
                    barcodeQuery.addProperty("quantity", terminalHandlerLM.findProperty("currentBalance[Barcode,CustomUser]").getExpr(barcodeExpr, user.getExpr()));
                }
                barcodeQuery.addProperty("idSkuBarcode", terminalHandlerLM.findProperty("idSku[Barcode]").getExpr(barcodeExpr));
                barcodeQuery.addProperty("nameManufacturer", terminalHandlerLM.findProperty("nameManufacturer[Barcode]").getExpr(barcodeExpr));
                barcodeQuery.addProperty("nameCountry", terminalHandlerLM.findProperty("nameCountry[Barcode]").getExpr(barcodeExpr));
                barcodeQuery.addProperty("passScales", terminalHandlerLM.findProperty("passScales[Barcode]").getExpr(barcodeExpr));
                barcodeQuery.addProperty("mainBarcode", terminalHandlerLM.findProperty("idMainBarcode[Barcode]").getExpr(barcodeExpr));
                barcodeQuery.addProperty("color", terminalHandlerLM.findProperty("color[Barcode, Stock]").getExpr(barcodeExpr, stockObject.getExpr()));
                barcodeQuery.addProperty("extInfo", terminalHandlerLM.findProperty("extInfo[Barcode, Stock]").getExpr(barcodeExpr, stockObject.getExpr()));
                barcodeQuery.addProperty("fld3", terminalHandlerLM.findProperty("fld3[Barcode, Stock]").getExpr(barcodeExpr, stockObject.getExpr()));
                barcodeQuery.addProperty("fld4", terminalHandlerLM.findProperty("fld4[Barcode, Stock]").getExpr(barcodeExpr, stockObject.getExpr()));
                barcodeQuery.addProperty("fld5", terminalHandlerLM.findProperty("fld5[Barcode, Stock]").getExpr(barcodeExpr, stockObject.getExpr()));
                barcodeQuery.addProperty("unit", terminalHandlerLM.findProperty("shortNameUOM[Barcode]").getExpr(barcodeExpr));
                barcodeQuery.addProperty("needManufacturingDate", terminalHandlerLM.findProperty("needManufacturingDate[Barcode]").getExpr(barcodeExpr));
                if(imagesInReadBase) {
                    barcodeQuery.addProperty("image", terminalHandlerLM.findProperty("image[Barcode]").getExpr(barcodeExpr));
                }
                barcodeQuery.addProperty("amountPack", terminalHandlerLM.findProperty("amountPack[Barcode]").getExpr(barcodeExpr));

                barcodeQuery.and(terminalHandlerLM.findProperty("filterGoods[Barcode,Stock]").getExpr(barcodeExpr, stockObject.getExpr()).getWhere());
                barcodeQuery.and(terminalHandlerLM.findProperty("id[Barcode]").getExpr(barcodeExpr).getWhere());
                barcodeQuery.and(terminalHandlerLM.findProperty("active[Barcode]").getExpr(barcodeExpr).getWhere());

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
                    String nameCountry = trim((String) entry.get("nameCountry"));
                    String isWeight = entry.get("passScales") != null ? "1" : "0";
                    String mainBarcode = trim((String) entry.get("mainBarcode"));
                    String color = formatColor((Color) entry.get("color"));
                    String extInfo = trim((String) entry.get("extInfo"));
                    String fld3 = trim((String) entry.get("fld3"));
                    String fld4 = trim((String) entry.get("fld4"));
                    String fld5 = trim((String) entry.get("fld5"));
                    String unit = trim((String) entry.get("unit"));
                    boolean needManufacturingDate = entry.get("needManufacturingDate") != null;
                    RawFileData image = (RawFileData) entry.get("image");
                    BigDecimal amountPack = (BigDecimal) entry.get("amountPack");

                    result.add(new TerminalBarcode(idBarcode, overNameSku, price, quantityBarcodeStock, idSkuBarcode,
                            nameManufacturer, isWeight, mainBarcode, color, extInfo, fld3, fld4, fld5, unit, needManufacturingDate, image, nameCountry, amountPack));

                }
            }
        }
        return result;
    }

    private List<TerminalBatch> readBatchList(DataSession session, ObjectValue stockObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalBatch> result = new ArrayList<>();
        if(terminalHandlerLM != null) {

            KeyExpr batchExpr = new KeyExpr("batch");
            ImRevMap<Object, KeyExpr> batchKeys = MapFact.singletonRev("batch", batchExpr);

            QueryBuilder<Object, Object> batchQuery = new QueryBuilder<>(batchKeys);
            batchQuery.addProperty("idBarcode", terminalHandlerLM.findProperty("idBarcodeSku[Batch]").getExpr(batchExpr));
            batchQuery.addProperty("date", terminalHandlerLM.findProperty("date[Batch]").getExpr(batchExpr));
            batchQuery.addProperty("number", terminalHandlerLM.findProperty("number[Batch]").getExpr(batchExpr));
            batchQuery.addProperty("idSupplier", terminalHandlerLM.findProperty("idSupplierStock[Batch]").getExpr(batchExpr));
            batchQuery.addProperty("cost", terminalHandlerLM.findProperty("cost[Batch]").getExpr(batchExpr));
            batchQuery.addProperty("extraField", terminalHandlerLM.findProperty("extraField[Batch, Stock]").getExpr(batchExpr, stockObject.getExpr()));

            batchQuery.and(terminalHandlerLM.findProperty("filterBatch[Batch, Stock]").getExpr(batchExpr, stockObject.getExpr()).getWhere());

            ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> batchResult = batchQuery.executeClasses(session);
            for (int i = 0; i < batchResult.size(); i++) {

                Long idBatch = (Long)batchResult.getKey(i).get("batch").getValue();

                ImMap<Object, ObjectValue> entry = batchResult.getValue(i);

                String idBarcode = trim((String) entry.get("idBarcode").getValue());
                String date = formatDate((LocalDate) entry.get("date").getValue());
                String number = trim((String) entry.get("number").getValue());
                String idSupplier = trim((String) entry.get("idSupplier").getValue());
                BigDecimal cost = (BigDecimal) entry.get("cost").getValue();
                String extraField = trim((String) entry.get("extraField").getValue());

                result.add(new TerminalBatch(String.valueOf(idBatch), idBarcode, idSupplier, date, number, cost, extraField));
            }
        }
        return result;
    }


    private void createOrderTable(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE zayavki " +
                "(dv     TEXT," +
                " dateshipment   TEXT," +
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

    private void updateOrderTable(Connection connection, List<TerminalOrder> terminalOrderList, String prefix) throws SQLException {
        if (!terminalOrderList.isEmpty() && prefix != null) {
            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO zayavki VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
                statement = connection.prepareStatement(sql);
                for (TerminalOrder order : terminalOrderList) {
                    if (order.number != null) {
                        String supplier = order.supplier == null ? "" : (prefix + formatValue(order.supplier));
                        statement.setObject(1, formatValue(order.date));
                        statement.setObject(2, formatValue(order.dateShipment));
                        statement.setObject(3, formatValue(order.number));
                        statement.setObject(4,supplier);
                        statement.setObject(5,formatValue(order.barcode));
                        statement.setObject(6,formatValue(order.quantity));
                        statement.setObject(7,formatValue(order.price));
                        statement.setObject(8,formatValue(order.minQuantity));
                        statement.setObject(9,formatValue(order.maxQuantity));
                        statement.setObject(10,formatValue(order.minPrice));
                        statement.setObject(11,formatValue(order.maxPrice));
                        statement.setObject(12,formatValue(order.color));
                        statement.setObject(13,formatValue(order.headField1));
                        statement.setObject(14,formatValue(order.headField2));
                        statement.setObject(15,formatValue(order.headField3));
                        statement.setObject(16,formatValue(order.posField1));
                        statement.setObject(17,formatValue(order.posField2));
                        statement.setObject(18,formatValue(order.posField3));
                        statement.setObject(19, formatValue(order.minDate1));
                        statement.setObject(20, formatValue(order.maxDate1));
                        statement.setObject(21, formatValue(order.vop));
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
                " unit TEXT," +
                " flags INTEGER," +
                " country TEXT, " +
                " amount_pack REAL);";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateGoodsTable(Connection connection, List<TerminalBarcode> barcodeList, List<TerminalOrder> orderList, Map<String, RawFileData> orderImages, boolean imagesInReadBase) throws SQLException {
        if (!barcodeList.isEmpty() || !orderList.isEmpty()) {
            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO goods VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
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
                        statement.setObject(9, formatValue(barcode.fld5)); //fld5
                        statement.setObject(10, imagesInReadBase && barcode.image != null ? (barcode.idBarcode + ".jpg") : ""); //image
                        statement.setObject(11, formatValue(barcode.isWeight)); //weight
                        statement.setObject(12, formatValue(barcode.mainBarcode)); //main_barcode
                        statement.setObject(13, formatValue(barcode.color)); //color
                        statement.setObject(14, formatValue(barcode.extInfo)); //ticket_data
                        statement.setObject(15, formatValue(barcode.unit)); //unit
                        statement.setObject(16, barcode.needManufacturingDate ? 1 : 0); //flags
                        statement.setObject(17, formatValue(barcode.nameCountry)); //nameCountry
                        statement.setObject(18, formatValue(barcode.amountPack)); //amountPack
                        statement.addBatch();
                        usedBarcodes.add(barcode.idBarcode);
                    }
                }
                for (TerminalOrder order : orderList) {
                    if (order.barcode != null) {
                        List<String> extraBarcodeList = order.extraBarcodeList;
                        if (extraBarcodeList != null) {
                            for (String extraBarcode : extraBarcodeList) {
                                if(!usedBarcodes.contains(extraBarcode)) {
                                    statement.setObject(1, formatValue(extraBarcode)); //idBarcode
                                    statement.setObject(2, formatValue(order.name)); //name
                                    statement.setObject(3, formatValue(order.price)); //price
                                    statement.setObject(4, ""); //quantity
                                    statement.setObject(5, formatValue(order.idItem)); //idItem, fld1
                                    statement.setObject(6, formatValue(order.manufacturer)); //manufacturer, fld2
                                    statement.setObject(7, ""); //fld3
                                    statement.setObject(8, ""); //fld4
                                    statement.setObject(9, ""); //fld5
                                    statement.setObject(10, imagesInReadBase && orderImages.containsKey(order.barcode) ? (order.barcode + ".jpg") : ""); //image
                                    statement.setObject(11, formatValue(order.weight)); //weight
                                    statement.setObject(12, formatValue(order.barcode)); //main_barcode
                                    statement.setObject(13, ""); //color
                                    statement.setObject(14, ""); //ticket_data
                                    statement.setObject(15, ""); //unit
                                    statement.setObject(16, 0); //flags
                                    statement.setObject(17, ""); //nameCountry
                                    statement.setObject(18, 0); //amountPack
                                    statement.addBatch();
                                }
                            }
                        } else {
                            if(!usedBarcodes.contains(order.barcode)) {
                                statement.setObject(1, formatValue(order.barcode)); //idBarcode
                                statement.setObject(2, formatValue(order.name)); //name
                                statement.setObject(3, formatValue(order.price)); //price
                                statement.setObject(4, ""); //quantity
                                statement.setObject(5, formatValue(order.idItem)); //idItem, fld1
                                statement.setObject(6, formatValue(order.manufacturer)); //manufacturer, fld2
                                statement.setObject(7, ""); //fld3
                                statement.setObject(8, ""); //fld4
                                statement.setObject(9, ""); //fld5
                                statement.setObject(10, imagesInReadBase && orderImages.containsKey(order.barcode) ? (order.barcode + ".jpg") : ""); //image
                                statement.setObject(11, formatValue(order.weight)); //weight
                                statement.setObject(12, formatValue(order.barcode)); //main_barcode
                                statement.setObject(13, ""); //color
                                statement.setObject(14, ""); //ticket_data
                                statement.setObject(15, ""); //unit
                                statement.setObject(16, 0); //flags
                                statement.setObject(17, ""); //nameCountry
                                statement.setObject(18, 0); //amountPack
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
                " price REAL," +
                " minprice REAL," +
                " maxprice REAL," +
                " quant   REAL," +
                " mainpost TEXT," +
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
                String sql = "INSERT OR REPLACE INTO assort VALUES(?, ?, ?, ?, ?, ?, ?);";
                statement = connection.prepareStatement(sql);
                for (TerminalAssortment assortment : terminalAssortmentList) {
                    if (assortment.idSupplier != null && assortment.idBarcode != null) {
                        statement.setObject(1, formatValue((prefix + assortment.idSupplier)));
                        statement.setObject(2, formatValue(assortment.idBarcode));
                        statement.setObject(3, formatValue(assortment.price));
                        statement.setObject(4, formatValue(assortment.minPrice));
                        statement.setObject(5, formatValue(assortment.maxPrice));
                        statement.setObject(6, formatValue(assortment.quantity));
                        String idOriginalSupplier = null;
                        if (assortment.idOriginalSupplier!=null)
                            idOriginalSupplier = prefix + assortment.idOriginalSupplier;
                        statement.setObject(7, formatValue(idOriginalSupplier));
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
                String sql = "INSERT OR REPLACE INTO ana VALUES(?, ?, ?, ?, ?, ?);";
                statement = connection.prepareStatement(sql);
                for (TerminalLegalEntity legalEntity : customANAList) {
                    if (legalEntity.idLegalEntity != null) {
                        statement.setObject(1, formatValue(legalEntity.idLegalEntity)); //ana
                        statement.setObject(2, formatValue(legalEntity.nameLegalEntity)); //naim
                        statement.setObject(3, formatValue(legalEntity.field1)); //fld1
                        statement.setObject(4, formatValue(legalEntity.field2)); //fld2
                        statement.setObject(5, formatValue(legalEntity.field3)); //fld3
                        statement.setObject(6, formatValue(legalEntity.extInfo)); //ticket
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
                " flags INTEGER )";
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

    private void createBatchesTable(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE batch " +
                "(idbatch TEXT DEFAULT ('')," +
                " barcode TEXT DEFAULT('')," +
                " date TEXT DEFAULT('')," +
                " number TEXT DEFAULT('')," +
                " idSupplier TEXT DEFAULT('')," +
                " price REAL DEFAULT(0)," +
                " extrafield TEXT DEFAULT('')" +
                ")";

        statement.executeUpdate(sql);
        statement.execute("CREATE INDEX batch_k ON batch (idbatch,barcode);");
        statement.close();
    }

    private void updateBatchesTable(Connection connection, List<TerminalBatch> terminalBatchList, String prefix) throws SQLException {
        if (terminalBatchList != null && !terminalBatchList.isEmpty()) {
            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO batch VALUES(?,?,?,?,?,?,?);";
                statement = connection.prepareStatement(sql);
                for (TerminalBatch batch : terminalBatchList) {
                    if (batch.idBatch != null && batch.idBarcode != null) {
                        statement.setObject(1, formatValue(batch.idBatch));
                        statement.setObject(2, formatValue(batch.idBarcode));
                        statement.setObject(3, formatValue(batch.date));
                        statement.setObject(4, formatValue(batch.number));
                        String idSupplier = null;
                        if (batch.idSupplier != null && prefix != null)
                            idSupplier = prefix + batch.idSupplier;
                        statement.setObject(5, formatValue(idSupplier));
                        statement.setObject(6, formatValue(batch.price));
                        statement.setObject(7, formatValue(batch.extraField));
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

    private Object formatValue(Object value) {
        return value == null ? "" : value instanceof LocalDate ? ((LocalDate) value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) : value;
    }

    public String importTerminalDocument(DataSession session, ExecutionStack stack, DataObject userObject, String idTerminal,
                                         String idTerminalDocument, List<List<Object>> terminalDocumentDetailList, boolean emptyDocument) {
        try {

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

                    ImportField extraQuantityTerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("extraQuantity[TerminalDocumentDetail]"));
                    props.add(new ImportProperty(extraQuantityTerminalDocumentDetailField, terminalHandlerLM.findProperty("extraQuantity[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    fields.add(extraQuantityTerminalDocumentDetailField);

                    ImportField batchTerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("batch[TerminalDocumentDetail]"));
                    props.add(new ImportProperty(batchTerminalDocumentDetailField, terminalHandlerLM.findProperty("batch[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    fields.add(batchTerminalDocumentDetailField);

                    ImportField markingTerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("marking[TerminalDocumentDetail]"));
                    props.add(new ImportProperty(markingTerminalDocumentDetailField, terminalHandlerLM.findProperty("marking[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    fields.add(markingTerminalDocumentDetailField);
                }

                ImportTable table = new ImportTable(fields, terminalDocumentDetailList);

                ERPLoggers.terminalLogger.info("start importing terminal document " + idTerminalDocument);
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
                ERPLoggers.terminalLogger.info("start applying terminal document " + idTerminalDocument);
                String result = session.applyMessage(getLogicsInstance().getBusinessLogics(), stack);
                if(result != null) {
                    ERPLoggers.terminalLogger.error(String.format("Apply terminal document %s error: %s", idTerminalDocument, result));
                }
                return result;

            } else return "-1";

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

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

    public Object login(DataSession session, ExecutionStack stack, String login, String password, String idTerminal) {
        try {

            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
            if(terminalHandlerLM != null) {
                ObjectValue customUser = terminalHandlerLM.findProperty("customUserUpcase[?]").readClasses(session, new DataObject(login.toUpperCase()));
                boolean authenticated = customUser instanceof DataObject && getLogicsInstance().getBusinessLogics().authenticationLM.checkPassword(session, (DataObject) customUser, password, stack);
                if(authenticated) {
                    if(terminalHandlerLM.findProperty("restricted[CustomUser]").read(session, customUser) != null)
                        return "Данный пользователь заблокирован";
                    else {
                        ObjectValue terminalObject = terminalHandlerLM.findProperty("terminal[STRING[100]]").readClasses(session, new DataObject(idTerminal));
                        if (terminalObject instanceof DataObject) {
                            terminalHandlerLM.findProperty("lastConnectionTime[Terminal]").change(LocalDateTime.now(), session, (DataObject) terminalObject);
                            terminalHandlerLM.findProperty("lastUser[Terminal]").change(customUser, session, (DataObject) terminalObject);
                            String applyMessage = session.applyMessage(getLogicsInstance().getBusinessLogics(), stack);
                            if (applyMessage != null)
                                ServerLoggers.systemLogger.error(String.format("Terminal Login error: %s, login %s, terminal %s", applyMessage, login, idTerminal));
                        }
                        return customUser; //DataObject
                    }
                }

            }
            return null;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public static List<TerminalOrder> readTerminalOrderList(DataSession session, ObjectValue customerStockObject, UserInfo userInfo) throws SQLException {
        Map<String, TerminalOrder> terminalOrderMap = new LinkedHashMap<>();

        if (terminalOrderLM != null) {
            try {
                KeyExpr orderExpr = new KeyExpr("terminalOrder");
                KeyExpr orderDetailExpr = new KeyExpr("terminalOrderDetail");
                ImRevMap<Object, KeyExpr> orderKeys = MapFact.toRevMap("TerminalOrder", orderExpr, "TerminalOrderDetail", orderDetailExpr);
                QueryBuilder<Object, Object> orderQuery = new QueryBuilder<>(orderKeys);
                String[] orderNames = new String[]{"dateOrder", "numberOrder", "idSupplierOrder"};
                LP<?>[] orderProperties = terminalOrderLM.findProperties("date[TerminalOrder]", "number[TerminalOrder]", "idSupplier[TerminalOrder]");
                for (int i = 0; i < orderProperties.length; i++) {
                    orderQuery.addProperty(orderNames[i], orderProperties[i].getExpr(orderExpr));
                }
                String[] orderDetailNames = new String[]{"idBarcodeSkuOrderDetail", "idSkuOrderDetail", "nameSkuOrderDetail", "priceOrderDetail",
                        "quantityOrderDetail", "nameManufacturerSkuOrderDetail", "passScalesSkuOrderDetail", "minDeviationQuantityOrderDetail",
                        "maxDeviationQuantityOrderDetail", "minDeviationPriceOrderDetail", "maxDeviationPriceOrderDetail",
                        "color", "headField1", "headField2", "headField3", "posField1", "posField2", "posField3",
                        "minDeviationDate", "maxDeviationDate", "vop", "dateShipment", "extraBarcodes", "sortTerminal"};
                LP<?>[] orderDetailProperties = terminalOrderLM.findProperties("idBarcodeSku[TerminalOrderDetail]", "idSku[TerminalOrderDetail]",
                        "nameSku[TerminalOrderDetail]", "price[TerminalOrderDetail]", "orderQuantity[TerminalOrderDetail]",
                        "nameManufacturerSku[TerminalOrderDetail]", "passScalesSku[TerminalOrderDetail]", "minDeviationQuantity[TerminalOrderDetail]",
                        "maxDeviationQuantity[TerminalOrderDetail]", "minDeviationPrice[TerminalOrderDetail]", "maxDeviationPrice[TerminalOrderDetail]",
                        "color[TerminalOrderDetail]", "headField1[TerminalOrderDetail]", "headField2[TerminalOrderDetail]", "headField3[TerminalOrderDetail]",
                        "posField1[TerminalOrderDetail]", "posField2[TerminalOrderDetail]", "posField3[TerminalOrderDetail]",
                        "minDeviationDate[TerminalOrderDetail]", "maxDeviationDate[TerminalOrderDetail]", "vop[TerminalOrderDetail]", "dateShipment[TerminalOrderDetail]",
                        "extraBarcodes[TerminalOrderDetail]", "sortTerminal[TerminalOrderDetail]");
                for (int i = 0; i < orderDetailProperties.length; i++) {
                    orderQuery.addProperty(orderDetailNames[i], orderDetailProperties[i].getExpr(orderDetailExpr));
                }
                
                orderQuery.and(terminalOrderLM.findProperty("filterTerminal[TerminalOrder, TerminalOrderDetail, Stock, Employee]").getExpr(
                        orderExpr, orderDetailExpr, customerStockObject.getExpr(), userInfo.user.getExpr()).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> orderResult = orderQuery.execute(session, MapFact.singletonOrder("sortTerminal", false));
                for (ImMap<Object, Object> entry : orderResult.values()) {
                    LocalDate dateOrder = (LocalDate) entry.get("dateOrder");
                    LocalDate dateShipment = (LocalDate) entry.get("dateShipment");
                    String numberOrder = StringUtils.trim((String) entry.get("numberOrder"));
                    String idSupplier = StringUtils.trim((String) entry.get("idSupplierOrder"));
                    String barcode = StringUtils.trim((String) entry.get("idBarcodeSkuOrderDetail"));
                    String idItem = StringUtils.trim((String) entry.get("idSkuOrderDetail"));
                    String name = StringUtils.trim((String) entry.get("nameSkuOrderDetail"));
                    BigDecimal price = (BigDecimal) entry.get("priceOrderDetail");
                    BigDecimal quantity = (BigDecimal) entry.get("quantityOrderDetail");
                    BigDecimal minQuantity = (BigDecimal) entry.get("minDeviationQuantityOrderDetail");
                    BigDecimal maxQuantity = (BigDecimal) entry.get("maxDeviationQuantityOrderDetail");
                    BigDecimal minPrice = (BigDecimal) entry.get("minDeviationPriceOrderDetail");
                    BigDecimal maxPrice = (BigDecimal) entry.get("maxDeviationPriceOrderDetail");
                    String nameManufacturer = (String) entry.get("nameManufacturerSkuOrderDetail");
                    String weight = entry.get("passScalesSkuOrderDetail") != null ? "1" : "0";
                    String color = formatColor((Color) entry.get("color"));

                    String headField1 = (String) entry.get("headField1");
                    String headField2 = (String) entry.get("headField2");
                    String headField3 = (String) entry.get("headField3");
                    String posField1 = (String) entry.get("posField1");
                    String posField2 = (String) entry.get("posField2");
                    String posField3 = (String) entry.get("posField3");
                    String minDeviationDate = formatDate((LocalDate) entry.get("minDeviationDate"));
                    String maxDeviationDate = formatDate((LocalDate) entry.get("maxDeviationDate"));
                    String vop = (String) entry.get("vop");
                    String extraBarcodes = (String) entry.get("extraBarcodes");
                    List<String> extraBarcodeList = extraBarcodes != null ? Arrays.asList(extraBarcodes.split(",")) : new ArrayList<>();

                    String key = numberOrder + "/" + barcode;
                    TerminalOrder terminalOrder = terminalOrderMap.get(key);
                    if (terminalOrder != null) {
                        terminalOrder.quantity = safeAdd(terminalOrder.quantity, quantity);
                        terminalOrder.minQuantity = safeAdd(terminalOrder.minQuantity, minQuantity);
                        terminalOrder.maxQuantity = safeAdd(terminalOrder.maxQuantity, maxQuantity);
                    } else
                        terminalOrderMap.put(key, new TerminalOrder(dateOrder, dateShipment, numberOrder, idSupplier, barcode, idItem, name, price,
                                quantity, minQuantity, maxQuantity, minPrice, maxPrice, nameManufacturer, weight, color,
                                headField1, headField2, headField3, posField1, posField2, posField3, minDeviationDate, maxDeviationDate, vop,
                                extraBarcodeList));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return new ArrayList<>(terminalOrderMap.values());
    }

    public static Map<String, RawFileData> readTerminalOrderImages(DataSession session, ObjectValue customerStockObject, UserInfo userInfo) throws SQLException {
        Map<String, RawFileData> terminalOrderImages = new HashMap<>();

        if (terminalOrderLM != null) {
            try {
                KeyExpr orderExpr = new KeyExpr("terminalOrder");
                KeyExpr orderDetailExpr = new KeyExpr("terminalOrderDetail");
                ImRevMap<Object, KeyExpr> orderKeys = MapFact.toRevMap("TerminalOrder", orderExpr, "TerminalOrderDetail", orderDetailExpr);
                QueryBuilder<Object, Object> orderQuery = new QueryBuilder<>(orderKeys);

                orderQuery.addProperty("barcode", terminalOrderLM.findProperty("idBarcodeSku[TerminalOrderDetail]").getExpr(orderDetailExpr));
                orderQuery.addProperty("image", terminalOrderLM.findProperty("image[TerminalOrderDetail]").getExpr(orderDetailExpr));

                //todo: заменить на одно свойство в lsf
                orderQuery.and(terminalOrderLM.findProperty("filter[TerminalOrder, Stock]").getExpr(orderExpr, customerStockObject.getExpr()).getWhere());
                orderQuery.and(terminalOrderLM.findProperty("checkUser[TerminalOrder, Employee]").getExpr(orderExpr, userInfo.user.getExpr()).getWhere());
                orderQuery.and((terminalOrderLM.findProperty("isOpened[TerminalOrder]")).getExpr(orderExpr).getWhere());
                orderQuery.and(terminalOrderLM.findProperty("order[TerminalOrderDetail]").getExpr(orderDetailExpr).compare(orderExpr, Compare.EQUALS));
                orderQuery.and(terminalOrderLM.findProperty("number[TerminalOrder]").getExpr(orderExpr).getWhere());
                orderQuery.and(terminalOrderLM.findProperty("idBarcodeSku[TerminalOrderDetail]").getExpr(orderDetailExpr).getWhere());
                orderQuery.and(terminalOrderLM.findProperty("hasImage[TerminalOrderDetail]").getExpr(orderDetailExpr).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> orderResult = orderQuery.execute(session);
                for (ImMap<Object, Object> entry : orderResult.values()) {
                    String barcode = StringUtils.trim((String) entry.get("barcode"));
                    RawFileData image = (RawFileData) entry.get("image");

                    terminalOrderImages.put(barcode, image);
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return terminalOrderImages;
    }

    public static List<TerminalAssortment> readTerminalAssortmentList(DataSession session, ObjectValue stockObject)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalAssortment> terminalAssortmentList = new ArrayList<>();
        if (terminalHandlerLM != null) {

            KeyExpr skuExpr = new KeyExpr("Sku");
            KeyExpr supplierExpr = new KeyExpr("Stock");
            ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap("Sku", skuExpr, "Stock", supplierExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

            query.addProperty("idBarcodeSku", terminalHandlerLM.findProperty("idBarcode[Sku]").getExpr(skuExpr));
            query.addProperty("idSupplier", terminalHandlerLM.findProperty("id[Stock]").getExpr(supplierExpr));
            query.addProperty("price", terminalHandlerLM.findProperty("price[Sku,Stock,Stock]").getExpr(skuExpr, stockObject.getExpr(), supplierExpr));
            query.addProperty("minPrice", terminalHandlerLM.findProperty("minDeviationPrice[Sku,Stock,Stock]").getExpr(skuExpr, stockObject.getExpr(), supplierExpr));
            query.addProperty("maxPrice", terminalHandlerLM.findProperty("maxDeviationPrice[Sku,Stock,Stock]").getExpr(skuExpr, stockObject.getExpr(), supplierExpr));
            query.addProperty("quantity", terminalHandlerLM.findProperty("quantity[Sku,Stock,Stock]").getExpr(skuExpr, stockObject.getExpr(), supplierExpr));
            query.addProperty("idOriginalSupplier", terminalHandlerLM.findProperty("idSupplier[Sku,Stock,Stock]").getExpr(skuExpr, stockObject.getExpr(), supplierExpr));

            query.and(terminalHandlerLM.findProperty("id[Stock]").getExpr(supplierExpr).getWhere());
            query.and(terminalHandlerLM.findProperty("idBarcode[Sku]").getExpr(skuExpr).getWhere());
            query.and(terminalHandlerLM.findProperty("filterAssortment[Sku,Stock,Stock]").getExpr(skuExpr, stockObject.getExpr(), supplierExpr).getWhere());


            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String idBarcodeSku = StringUtils.trim((String) entry.get("idBarcodeSku"));
                String idSupplier = StringUtils.trim((String) entry.get("idSupplier"));
                BigDecimal price = (BigDecimal) entry.get("price");
                BigDecimal minPrice = (BigDecimal) entry.get("minPrice");
                BigDecimal maxPrice = (BigDecimal) entry.get("maxPrice");
                BigDecimal quantity = (BigDecimal) entry.get("quantity");
                String idOriginalSupplier = StringUtils.trim((String) entry.get("idOriginalSupplier"));

                terminalAssortmentList.add(new TerminalAssortment(idBarcodeSku, idSupplier, price, minPrice, maxPrice, quantity, idOriginalSupplier));
            }
        }
        return terminalAssortmentList;
    }

    public static List<TerminalHandbookType> readTerminalHandbookTypeList(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalHandbookType> terminalHandbookTypeList = new ArrayList<>();
        if(terminalHandlerLM != null) {
            KeyExpr terminalHandbookTypeExpr = new KeyExpr("terminalHandbookType");
            ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev("terminalHandbookType", terminalHandbookTypeExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
            String[] names = new String[]{"idTerminalHandbookType", "nameTerminalHandbookType"};
            LP<?>[] properties = terminalHandlerLM.findProperties("id[TerminalHandbookType]", "name[TerminalHandbookType]");
            for (int i = 0, propertiesLength = properties.length; i < propertiesLength; i++) {
                query.addProperty(names[i], properties[i].getExpr(terminalHandbookTypeExpr));
            }
            query.and(terminalHandlerLM.findProperty("id[TerminalHandbookType]").getExpr(terminalHandbookTypeExpr).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String id = StringUtils.trim((String) entry.get("idTerminalHandbookType"));
                String name = StringUtils.trim((String) entry.get("nameTerminalHandbookType"));
                terminalHandbookTypeList.add(new TerminalHandbookType(id, name));
            }
        }
        return terminalHandbookTypeList;
    }

    public static List<TerminalDocumentType> readTerminalDocumentTypeListServer(DataSession session,DataObject userObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalDocumentType> terminalDocumentTypeList = new ArrayList<>();
        if(terminalHandlerLM != null) {
            KeyExpr terminalDocumentTypeExpr = new KeyExpr("terminalDocumentType");
            ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev("terminalDocumentType", terminalDocumentTypeExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
            String[] names = new String[]{"idTerminalDocumentType", "nameTerminalDocumentType", "flagTerminalDocumentType",
                    "idTerminalHandbookType1TerminalDocumentType", "idTerminalHandbookType2TerminalDocumentType"};
            LP<?>[] properties = terminalHandlerLM.findProperties("id[TerminalDocumentType]", "name[TerminalDocumentType]", "flag[TerminalDocumentType]",
                    "idTerminalHandbookType1[TerminalDocumentType]", "idTerminalHandbookType2[TerminalDocumentType]");
            for (int i = 0; i < properties.length; i++) {
                query.addProperty(names[i], properties[i].getExpr(terminalDocumentTypeExpr));
            }
            query.and(terminalHandlerLM.findProperty("id[TerminalDocumentType]").getExpr(terminalDocumentTypeExpr).getWhere());
            query.and(terminalHandlerLM.findProperty("notSkip[TerminalDocumentType, CustomUser]").getExpr(terminalDocumentTypeExpr, userObject.getExpr()).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String id = StringUtils.trim((String) entry.get("idTerminalDocumentType"));
                String name = StringUtils.trim((String) entry.get("nameTerminalDocumentType"));
                Long flag = (Long) entry.get("flagTerminalDocumentType");
                String analytics1 = StringUtils.trim((String) entry.get("idTerminalHandbookType1TerminalDocumentType"));
                String analytics2 = StringUtils.trim((String) entry.get("idTerminalHandbookType2TerminalDocumentType"));
                terminalDocumentTypeList.add(new TerminalDocumentType(id, name, analytics1, analytics2, flag));
            }
        }
        return terminalDocumentTypeList;
    }

    public static List<TerminalLegalEntity> readCustomANAList(DataSession session, BusinessLogics BL, DataObject userObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalLegalEntity> customANAList = new ArrayList<>();
        if (terminalHandlerLM != null) {

            KeyExpr terminalHandbookTypeExpr = new KeyExpr("terminalHandbookType");
            ImRevMap<Object, KeyExpr> terminalHandbookTypeKeys = MapFact.singletonRev("terminalHandbookType", terminalHandbookTypeExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(terminalHandbookTypeKeys);
            String[] names = new String[]{"exportId", "name", "propertyID", "propertyName", "filterProperty", "extInfoProperty",
                    "field1Property", "field2Property", "field3Property"};
            LP<?>[] properties = terminalHandlerLM.findProperties("exportId[TerminalHandbookType]", "name[TerminalHandbookType]",
                    "canonicalNamePropertyID[TerminalHandbookType]", "canonicalNamePropertyName[TerminalHandbookType]",
                    "canonicalNameFilterProperty[TerminalHandbookType]", "canonicalNameExtInfoProperty[TerminalHandbookType]",
                    "canonicalNameField1Property[TerminalHandbookType]", "canonicalNameField2Property[TerminalHandbookType]", "canonicalNameField3Property[TerminalHandbookType]");
            for (int i = 0, propertiesLength = properties.length; i < propertiesLength; i++) {
                query.addProperty(names[i], properties[i].getExpr(terminalHandbookTypeExpr));
            }
            query.and(terminalHandlerLM.findProperty("exportId[TerminalHandbookType]").getExpr(terminalHandbookTypeExpr).getWhere());
            query.and(terminalHandlerLM.findProperty("canonicalNamePropertyID[TerminalHandbookType]").getExpr(terminalHandbookTypeExpr).getWhere());
            query.and(terminalHandlerLM.findProperty("canonicalNamePropertyName[TerminalHandbookType]").getExpr(terminalHandbookTypeExpr).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String prefix = StringUtils.trim((String) entry.get("exportId"));
                LP propertyID = (LP<?>) BL.findSafeProperty(StringUtils.trim((String) entry.get("propertyID")));
                LP propertyName = (LP<?>) BL.findSafeProperty(StringUtils.trim((String) entry.get("propertyName")));
                String canonicalNameFilterProperty = StringUtils.trim((String) entry.get("filterProperty"));
                LP filterProperty = canonicalNameFilterProperty != null ? (LP<?>) BL.findSafeProperty(canonicalNameFilterProperty) : null;
                String canonicalNameExtInfoProperty = StringUtils.trim((String) entry.get("extInfoProperty"));
                LP extInfoProperty = canonicalNameExtInfoProperty != null ? (LP<?>) BL.findSafeProperty(canonicalNameExtInfoProperty) : null;
                String canonicalNameField1Property = StringUtils.trim((String) entry.get("field1Property"));
                LP field1Property = canonicalNameField1Property != null ? (LP<?>) BL.findSafeProperty(canonicalNameField1Property) : null;
                String canonicalNameField2Property = StringUtils.trim((String) entry.get("field2Property"));
                LP field2Property = canonicalNameField2Property != null ? (LP<?>) BL.findSafeProperty(canonicalNameField2Property) : null;
                String canonicalNameField3Property = StringUtils.trim((String) entry.get("field3Property"));
                LP field3Property = canonicalNameField3Property != null ? (LP<?>) BL.findSafeProperty(canonicalNameField3Property) : null;

                if(propertyID != null && propertyName != null) {
                    ImOrderSet<PropertyInterface> interfaces = propertyID.listInterfaces;
                    if (interfaces.size() == 1) {
                        KeyExpr customANAExpr = new KeyExpr("customANA");
                        ImRevMap<Object, KeyExpr> customANAKeys = MapFact.singletonRev("customANA", customANAExpr);
                        QueryBuilder<Object, Object> customANAQuery = new QueryBuilder<>(customANAKeys);
                        customANAQuery.addProperty("id", propertyID.getExpr(customANAExpr));
                        customANAQuery.addProperty("name", propertyName.getExpr(customANAExpr));

                        addCustomField(userObject, customANAExpr, customANAQuery, extInfoProperty, "extInfo");
                        addCustomField(userObject, customANAExpr, customANAQuery, field1Property, "field1");
                        addCustomField(userObject, customANAExpr, customANAQuery, field2Property, "field2");
                        addCustomField(userObject, customANAExpr, customANAQuery, field3Property, "field3");

                        if (filterProperty != null) {
                            switch (filterProperty.listInterfaces.size()) {
                                case 1:
                                    customANAQuery.and(filterProperty.getExpr(customANAExpr).getWhere());
                                    break;
                                case 2:
                                    //небольшой хак, для случая, когда второй параметр в фильтре - пользователь
                                    Object interfaceObject = filterProperty.listInterfaces.get(1);
                                    if (interfaceObject instanceof ClassPropertyInterface) {
                                        if (IsClassProperty.fitClass(userObject.objectClass, ((ClassPropertyInterface) interfaceObject).interfaceClass))
                                            customANAQuery.and(filterProperty.getExpr(customANAExpr, userObject.getExpr()).getWhere());
                                    } else { //если не data property и второй параметр не пользователь, то фильтр отсечёт всё
                                        customANAQuery.and(filterProperty.getExpr(customANAExpr, userObject.getExpr()).getWhere());
                                    }
                                    break;
                            }
                        }

                        customANAQuery.and(propertyID.getExpr(customANAExpr).getWhere());
                        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> customANAResult = customANAQuery.execute(session);
                        for (ImMap<Object, Object> customANAEntry : customANAResult.values()) {
                            String idCustomANA = StringUtils.trim((String) customANAEntry.get("id"));
                            String nameCustomANA = StringUtils.trim((String) customANAEntry.get("name"));
                            String extInfo = StringUtils.trim((String) customANAEntry.get("extInfo"));
                            String field1 = StringUtils.trim((String) customANAEntry.get("field1"));
                            String field2 = StringUtils.trim((String) customANAEntry.get("field2"));
                            String field3 = StringUtils.trim((String) customANAEntry.get("field3"));
                            customANAList.add(new TerminalLegalEntity(prefix + idCustomANA, nameCustomANA, extInfo, field1, field2, field3));
                        }
                    }
                }
            }
        }
        return customANAList;
    }

    private static void addCustomField(DataObject userObject, KeyExpr customANAExpr, QueryBuilder<Object, Object> customANAQuery, LP extInfoProperty, String name) {
        if(extInfoProperty != null) {
            switch (extInfoProperty.listInterfaces.size()) {
                case 1:
                    customANAQuery.addProperty(name, extInfoProperty.getExpr(customANAExpr));
                    break;
                case 2:
                    //небольшой хак, для случая, когда второй параметр в фильтре - пользователь
                    Object interfaceObject = extInfoProperty.listInterfaces.get(1);
                    if (interfaceObject instanceof ClassPropertyInterface) {
                        if (IsClassProperty.fitClass(userObject.objectClass, ((ClassPropertyInterface) interfaceObject).interfaceClass))
                            customANAQuery.addProperty(name, extInfoProperty.getExpr(customANAExpr, userObject.getExpr()));
                    } else {
                        customANAQuery.addProperty(name, extInfoProperty.getExpr(customANAExpr, userObject.getExpr()));
                    }
                    break;
            }
        }
    }

    private static BigDecimal safeAdd(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else return (operand1 == null ? operand2 : (operand2 == null ? operand1 : operand1.add(operand2)));
    }


    private static class TerminalAssortment implements Serializable {
        public String idBarcode;
        public String idSupplier;
        public String idOriginalSupplier;
        public BigDecimal price;
        public BigDecimal minPrice;
        public BigDecimal maxPrice;
        public BigDecimal quantity;

        public TerminalAssortment(String idBarcode, String idSupplier, BigDecimal price, BigDecimal minPrice, BigDecimal maxPrice, BigDecimal quantity, String idOriginalSupplier) {
            this.idBarcode = idBarcode;
            this.idSupplier = idSupplier;
            this.price = price;
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
            this.quantity = quantity;
            this.idOriginalSupplier = idOriginalSupplier;
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
        String fld5;
        String unit;
        boolean needManufacturingDate;
        RawFileData image;
        String nameCountry;
        BigDecimal amountPack;

        public TerminalBarcode(String idBarcode, String nameSku, BigDecimal price, BigDecimal quantityBarcodeStock, String idSkuBarcode, String nameManufacturer, String isWeight, String mainBarcode, String color, String extInfo, String fld3, String fld4, String fld5, String unit, boolean needManufacturingDate, RawFileData image, String nameCountry, BigDecimal amountPack) {
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
            this.fld5 = fld5;
            this.unit = unit;
            this.needManufacturingDate = needManufacturingDate;
            this.image = image;
            this.nameCountry = nameCountry;
            this.amountPack = amountPack;
        }
    }

    private class TerminalBatch implements Serializable {
        public String idBatch;
        public String idBarcode;
        public String idSupplier;
        public String date;
        public String number;
        public BigDecimal price;
        public String extraField;

        public TerminalBatch(String idBatch, String idBarcode, String idSupplier, String date, String number, BigDecimal price, String extraField) {
            this.idBatch = idBatch;
            this.idBarcode = idBarcode;
            this.idSupplier = idSupplier;
            this.date = date;
            this.number = number;
            this.price = price;
            this.extraField = extraField;
        }
    }

    private static class TerminalDocumentType implements Serializable {
        public String id;
        public String name;
        public String analytics1;
        public String analytics2;
        public Long flag;

        public TerminalDocumentType(String id, String name, String analytics1, String analytics2, Long flag) {
            this.id = id;
            this.name = name;
            this.analytics1 = analytics1;
            this.analytics2 = analytics2;
            this.flag = flag;
        }
    }

    private static class TerminalHandbookType implements Serializable {
        public String id;
        public String name;

        public TerminalHandbookType(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static class TerminalLegalEntity implements Serializable {
        public String idLegalEntity;
        public String nameLegalEntity;
        public String extInfo;
        public String field1;
        public String field2;
        public String field3;

        public TerminalLegalEntity(String idLegalEntity, String nameLegalEntity, String extInfo, String field1, String field2, String field3) {
            this.idLegalEntity = idLegalEntity;
            this.nameLegalEntity = nameLegalEntity;
            this.extInfo = extInfo;
            this.field1 = field1;
            this.field2 = field2;
            this.field3 = field3;
        }
    }

    private static class TerminalOrder implements Serializable {
        public LocalDate date;
        public LocalDate dateShipment;
        public String number;
        public String supplier;
        public String barcode;
        public String idItem;
        public String name;
        public BigDecimal price;
        public BigDecimal quantity;
        public BigDecimal minQuantity;
        public BigDecimal maxQuantity;
        public BigDecimal minPrice;
        public BigDecimal maxPrice;
        public String manufacturer;
        public String weight;
        public String color;
        public String headField1;
        public String headField2;
        public String headField3;
        public String posField1;
        public String posField2;
        public String posField3;

        public String minDate1;
        public String maxDate1;
        public String vop;
        public List<String> extraBarcodeList;
        public RawFileData image;

        public TerminalOrder(LocalDate date, LocalDate dateShipment, String number, String supplier, String barcode, String idItem, String name,
                             BigDecimal price, BigDecimal quantity, BigDecimal minQuantity, BigDecimal maxQuantity,
                             BigDecimal minPrice, BigDecimal maxPrice, String manufacturer, String weight, String color,
                             String headField1, String headField2, String headField3, String posField1, String posField2, String posField3,
                             String minDate1, String maxDate1, String vop, List<String> extraBarcodeList) {
            this.date = date;
            this.dateShipment = dateShipment;
            this.number = number;
            this.supplier = supplier;
            this.barcode = barcode;
            this.idItem = idItem;
            this.name = name;
            this.price = price;
            this.quantity = quantity;
            this.minQuantity = minQuantity;
            this.maxQuantity = maxQuantity;
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
            this.manufacturer = manufacturer;
            this.weight = weight;
            this.color = color;
            this.headField1 = headField1;
            this.headField2 = headField2;
            this.headField3 = headField3;
            this.posField1 = posField1;
            this.posField2 = posField2;
            this.posField3 = posField3;

            this.minDate1 = minDate1;
            this.maxDate1 = maxDate1;
            this.vop = vop;
            this.extraBarcodeList = extraBarcodeList;
        }
    }

}

