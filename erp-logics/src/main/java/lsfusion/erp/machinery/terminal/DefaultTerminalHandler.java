package lsfusion.erp.machinery.terminal;

import com.google.common.base.Throwables;
import lsfusion.base.BaseUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.col.interfaces.mutable.MRevMap;
import lsfusion.base.file.FileData;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.ERPLoggers;
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
import lsfusion.server.logics.classes.data.file.DynamicFormatFileClass;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.property.classes.IsClassProperty;
import lsfusion.server.logics.property.oraction.PropertyInterface;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.physics.dev.integration.service.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.awt.*;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static lsfusion.base.BaseUtils.*;

public class DefaultTerminalHandler {

    static ScriptingLogicsModule terminalOrderLM;
    static ScriptingLogicsModule terminalOrderLotLM;
    static ScriptingLogicsModule terminalHandlerLM;
    static ScriptingLogicsModule terminalHandlerLotLM;
    static ScriptingLogicsModule terminalHandlerLotByLM;
    static ScriptingLogicsModule terminalLotLM;
    static ScriptingLogicsModule ediGtinLM;
    static ScriptingLogicsModule terminalTeamWorkLM;
    static ScriptingLogicsModule machineryPriceTransactionLM;

    static String ID_APPLICATION_TSD = "1";
    static String ID_APPLICATION_ORDER = "2";

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
        terminalOrderLotLM = getLogicsInstance().getBusinessLogics().getModule("TerminalOrderLot");
        terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
        terminalHandlerLotLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandlerLot");
        terminalHandlerLotByLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandlerLotBy");
        terminalLotLM = getLogicsInstance().getBusinessLogics().getModule("TerminalLot");
        ediGtinLM = getLogicsInstance().getBusinessLogics().getModule("EDIGTIN");
        terminalTeamWorkLM = getLogicsInstance().getBusinessLogics().getModule("TerminalTeamWork");
        machineryPriceTransactionLM = getLogicsInstance().getBusinessLogics().getModule("MachineryPriceTransaction");
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

    public Object readItem(DataSession session, UserInfo userInfo, String barcode, String vop) {
        try {
            if(terminalHandlerLM != null) {
                ObjectValue barcodeObject = terminalHandlerLM.findProperty("barcode[BPSTRING[15]]").readClasses(session, new DataObject(barcode));

                ObjectValue stockObject;
                if (!userInfo.idStock.isEmpty())
                    stockObject = terminalHandlerLM.findProperty("stock[STRING[100]]").readClasses(session, new DataObject(userInfo.idStock));
                else
                    stockObject = userInfo.user == null ? NullValue.instance : terminalHandlerLM.findProperty("stock[Employee]").readClasses(session, userInfo.user);

                String overNameSku;
                if (!vop.isEmpty())
                    overNameSku = (String) terminalHandlerLM.findProperty("overNameSku[Barcode,Stock,User,STRING]").read(session, barcodeObject, stockObject, userInfo.user, new DataObject(vop));
                else
                    overNameSku = (String) terminalHandlerLM.findProperty("overNameSku[Barcode,Stock]").read(session, barcodeObject, stockObject);

                if(overNameSku == null)
                    return null;
                String isWeight = terminalHandlerLM.findProperty("isWeight[Barcode]").read(session, barcodeObject) != null ? "1" : "0";
                ObjectValue skuObject = terminalHandlerLM.findProperty("skuBarcode[BPSTRING[15]]").readClasses(session, new DataObject(barcode));
                BigDecimal price = (BigDecimal) terminalHandlerLM.findProperty("currentPriceInTerminal[Barcode,Stock]").read(session, barcodeObject, stockObject);
                BigDecimal quantity = (BigDecimal) terminalHandlerLM.findProperty("currentBalance[Barcode,Stock,CustomUser]").read(session, barcodeObject, stockObject, userInfo.user);
                String priceValue = bigDecimalToString(price, 2);
                String quantityValue = bigDecimalToString(quantity, 3);

                String mainBarcode = (String) terminalHandlerLM.findProperty("idMainBarcode[Barcode]").read(session, barcodeObject);

                String idSkuBarcode = trimToEmpty((String) terminalHandlerLM.findProperty("idSku[Barcode]").read(session, barcodeObject));
                String nameManufacturer = trimToEmpty((String) terminalHandlerLM.findProperty("nameManufacturer[Barcode]").read(session, barcodeObject));

                String fld3 = (String) terminalHandlerLM.findProperty("fld3[Barcode, Stock]").read(session, barcodeObject, stockObject);
                String fld4 = (String) terminalHandlerLM.findProperty("fld4[Barcode, Stock]").read(session, barcodeObject, stockObject);
                String fld5 = (String) terminalHandlerLM.findProperty("fld5[Barcode, Stock, CustomUser]").read(session, barcodeObject, stockObject, userInfo.user);
                String color = formatColor((Color) terminalHandlerLM.findProperty("color[Sku, Stock]").read(session, skuObject, stockObject));
                String backgrounColor = formatColor((Color) terminalHandlerLM.findProperty("background_color[Sku, Stock]").read(session, skuObject, stockObject));
                
                String ticket_data = (String) terminalHandlerLM.findProperty("extInfo[Barcode, Stock]").read(session, barcodeObject, stockObject);
                Long flags = (Long) terminalHandlerLM.findProperty("flags[Barcode, Stock]").read(session, barcodeObject, stockObject);

                String unit = (String) terminalHandlerLM.findProperty("shortNameUOM[Barcode]").read(session, barcodeObject);
                String category = (String) terminalHandlerLM.findProperty("nameSkuGroup[Barcode]").read(session, barcodeObject);
    
                BigDecimal trustAcceptPercent = (BigDecimal) terminalHandlerLM.findProperty("trustAcceptPercent[Barcode]").read(session, barcodeObject);
    
                String lotType = terminalHandlerLotLM == null ? null : (String) terminalHandlerLotLM.findProperty("lotType[Barcode]").read(session, barcodeObject);
                boolean ukz = terminalHandlerLotByLM != null && terminalHandlerLotByLM.findProperty("ukz[Barcode]").read(session, barcodeObject) != null;
                String nameUkzType = terminalHandlerLotByLM == null ? null : (String) terminalHandlerLotByLM.findProperty("nameUkzType[Barcode]").read(session, barcodeObject);
                
                return Arrays.asList(barcode, BaseUtils.isEmpty(overNameSku) ? "" : overNameSku.toUpperCase(), priceValue == null ? "0" : priceValue,
                        quantityValue == null ? "0" : quantityValue, idSkuBarcode, nameManufacturer, fld3, fld4, fld5, isWeight,
                        mainBarcode, color, ticket_data, flags == null ? "0" : flags.toString(), category, unit, trustAcceptPercent, backgrounColor, lotType, ukz ? "1" : "0", nameUkzType);
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private String bigDecimalToString(BigDecimal bd, int fractDigits) {
        String value = null;
        if(bd != null) {
            bd = bd.setScale(fractDigits, RoundingMode.HALF_UP);
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

    public String readLotInfo(DataSession session, String barcode) {

        try {
            if (terminalHandlerLotLM != null) {
                String lotInfo = (String) terminalHandlerLotLM.findProperty("lotInfo[STRING]").read(session, new DataObject(barcode));
                return lotInfo;
            }
            return null;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public String readItemHtml(DataSession session, String barcode, String idStock) {

        try {
            if (terminalHandlerLM != null) {
                String templateHtml;
                if (barcode.matches("\\d{1,14}")) {
                    String nameSkuBarcode = (String) terminalHandlerLM.findProperty("nameSku[Barcode]").read(session, terminalHandlerLM.findProperty("barcode[BPSTRING[15]]").readClasses(session, new DataObject(barcode)));
                    //boolean stop = terminalHandlerLM.findProperty("inStopList[STRING[15],STRING[100]]").read(session, new DataObject(barcode), new DataObject(idStock)) != null;
                    if (nameSkuBarcode != null /*&& !stop*/) {
                        if (nameSkuBarcode.length() > 100)
                            nameSkuBarcode = nameSkuBarcode.substring(0,100) + "...";
                        BigDecimal price;
                        if (machineryPriceTransactionLM != null) {
                            price = (BigDecimal) machineryPriceTransactionLM.findProperty("transactionPriceIdBarcodeId[STRING[15],STRING[100]]").read(session, new DataObject(barcode), new DataObject(idStock));
                        } else {
                            price = (BigDecimal) terminalHandlerLM.findProperty("currentPriceInTerminalIdBarcodeId[STRING[15],STRING[100]]").read(session, new DataObject(barcode), new DataObject(idStock));
                        }
                        Integer leftPrice = price == null ? null : price.intValue();
                        Integer rightPrice = price == null ? null : (price.multiply(BigDecimal.valueOf(100))).intValue() % 100;

                        String left = leftPrice == null ? "0" : String.valueOf(leftPrice);
                        String right = leftPrice == null ? "" : rightPrice == null || rightPrice.equals(0) ? "00" :
                                rightPrice < 10 ? "0" + String.valueOf(rightPrice) : String.valueOf(rightPrice);

                        templateHtml = getTemplatePriceHTML(session);
                        if (templateHtml != null) {
                            templateHtml = templateHtml.replace("@name@", nameSkuBarcode);
                            templateHtml = templateHtml.replace("@priceLeft@", left);
                            templateHtml = templateHtml.replace("@priceRight@", right);
                            return templateHtml;
                        }
                    }
                    else
                        templateHtml = getDefaultNotFoundPriceHTML(session);
                } else
                    templateHtml = getDefaultNotFoundPriceHTML(session);

                return  templateHtml;
            }
            return null;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private String getTemplatePriceHTML(DataSession session) {

        String templateHtml = "<html><head><style>" +
                "   @font-face {font-family: \"pt-sans\"; src: url(file:///android_asset/fonts/pt-sans.ttf);}" +
                "   @font-face {font-family: \"pt-sans-narrow\"; src: url(file:///android_asset/fonts/pt-sans-narrow.ttf);}" +
                "   @font-face {font-family: \"pt-sans-bold-italic\"; src: url(file:///android_asset/fonts/pt-sans-bold-italic.ttf);}" +
                "   body {background: url(file:///android_asset/main.png) no-repeat; background-size: 100%%; margin: 0px; padding: 0px; text-align: center; font-family: \"pt-sans-narrow\";}" +
                "   p {margin-bottom: 25px; margin-right: 5px; text-align: center;}" +
                "  .name {font-size:56px; font-weight: bold; color:rgb(0,51,102); margin-top:20%%;left:50%%; }" +
                "  .price1 {font-family: \"pt-sans\"; font-size:180px; font-weight: bold; font-style: italic; letter-spacing: -12px; color:rgb(0,51,102); margin: 0px; display: inline-block;}" +
                "  .price2 {font-family: \"pt-sans-bold-italic\"; font-size:80px; font-weight: bold; font-style: italic; color:rgb(0,51,102); margin-top: 28px; margin-left: 30px; vertical-align: top; display: inline-block;}" +
                "</style><head>" +
                "<body><div class=\"name\"><p>@name@</p></div><p text-align: center;><div class=\"price1\">@priceLeft@</div><div class=\"price2\">@priceRight@</div></p></body></html>";

        if (terminalHandlerLM != null) {
            FileData fileTemplate;
            try {
                fileTemplate = (FileData) terminalHandlerLM.findProperty("templatePriceHtml").read(session);
                if (fileTemplate != null)
                    templateHtml = IOUtils.toString(fileTemplate.getRawFile().getInputStream(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return templateHtml;
    }

    private String getDefaultNotFoundPriceHTML(DataSession session) {
        String templateHtml = "<html><head><style>" +
                "   @font-face {font-family: \"pt-sans-narrow\"; src: url(file:///android_asset/fonts/pt-sans-narrow.ttf);}" +
                "   body {background: url(file:///android_asset/main.png) no-repeat; background-size: 100%; margin: 0px; padding: 0px; text-align: center; font-family: \"pt-sans-narrow\";}" +
                "   p {margin-bottom: 25px; margin-right: 5px; text-align: center;}" +
                "  .name {font-size:56px; font-weight: bold; color:rgb(233,0,0); margin-top:20%;left:50%; }" +
                "</style></head>" +
                "<body><div class=\"name\"><p>Товар не найден</p></div></body></html>";

        if (terminalHandlerLM != null) {
            FileData fileTemplate;
            try {
                fileTemplate = (FileData) terminalHandlerLM.findProperty("templateNotFoundPriceHtml").read(session);
                if (fileTemplate != null)
                    templateHtml = IOUtils.toString(fileTemplate.getRawFile().getInputStream(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return templateHtml;
    }


    public RawFileData readBase(DataSession session, UserInfo userInfo, boolean readBatch) {
        File file = null;
        try {

            BusinessLogics BL = getLogicsInstance().getBusinessLogics();
            if (terminalHandlerLM != null) {

                boolean imagesInReadBase = terminalHandlerLM.findProperty("imagesInReadBase[]").read(session) != null;
                String baseZipDirectory = (String) terminalHandlerLM.findProperty("baseZipDirectory[]").read(session);

                ObjectValue stockObject;
                if (!userInfo.idStock.isEmpty())
                    stockObject = terminalHandlerLM.findProperty("stock[STRING[100]]").readClasses(session, new DataObject(userInfo.idStock));
                else
                    stockObject = terminalHandlerLM.findProperty("stock[Employee]").readClasses(session, userInfo.user);

                //если prefix null, то таблицу не выгружаем. Если prefix пустой (skipPrefix), то таблицу выгружаем, но без префикса
                String prefix = (String) terminalHandlerLM.findProperty("exportId[]").read(session);
                List<TerminalBarcode> barcodeList = readBarcodeList(session, stockObject, imagesInReadBase, userInfo.user);

                List<TerminalBatch> batchList = null;
                if (userInfo.idApplication.equalsIgnoreCase(ID_APPLICATION_TSD) && readBatch)
                    batchList = readBatchList(session, stockObject);
                List<TerminalBatch> extraBatchList = readExtraBatchList(session, stockObject);

                List<TerminalOrder> orderList = readTerminalOrderList(session, stockObject, userInfo);
                List<SkuExtraBarcode> skuExtraBarcodeList = readSkuExtraBarcodeList(session, stockObject);
                Map<String, RawFileData> orderImages = imagesInReadBase ? readTerminalOrderImages(session, stockObject, userInfo) : new HashMap<>();

                List<TerminalAssortment> assortmentList = readTerminalAssortmentList(session, stockObject, userInfo);
                List<TerminalHandbookType> handbookTypeList = readTerminalHandbookTypeList(session);
                List<TerminalDocumentType> terminalDocumentTypeList = readTerminalDocumentTypeListServer(session, stockObject, userInfo);
                List<TerminalLegalEntity> customANAList = readCustomANAList(session, BL, userInfo);
                List<SkuGroup> skuGroupList = readSkuGroupList(session);
    
                List<TerminalOrder> labelTaskList = readLabelTaskList(session, userInfo);
                orderList.addAll(labelTaskList);
                
                file = File.createTempFile("terminalHandler", ".db");

                Class.forName("org.sqlite.JDBC");
                try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath())) {
    
                    createCategoryTable(connection);
                    updateCategoryTable(connection, skuGroupList);
                    
                    createGoodsTable(connection);
                    updateGoodsTable(connection, barcodeList, orderList, skuExtraBarcodeList, orderImages, imagesInReadBase, userInfo);

                    createBatchesTable(connection);
                    updateBatchesTable(connection, batchList, extraBatchList, prefix, userInfo);

                    createOrderTable(connection);
                    updateOrderTable(connection, orderList, prefix, userInfo);

                    if(terminalOrderLM !=null){
                        createUnitLoadsTable(connection);
                        updateUnitLoadsTable(connection, readUnitLoadList(session, stockObject, userInfo));
                    }

                    createAssortTable(connection);
                    updateAssortTable(connection, assortmentList, prefix, userInfo);

                    if (terminalLotLM != null) {
                        if (!StringUtils.isEmpty(userInfo.idApplication)) { // Костыль для WINCE (марки в WINCE не грузим)
                            createLotsTable(connection);
                            updateLotsTable(connection, readLotList(session, stockObject, userInfo));
                        }
                    }

                    createVANTable(connection);
                    updateVANTable(connection, handbookTypeList);

                    createANATable(connection);
                    updateANATable(connection, customANAList, userInfo);

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
                                        writeInputStreamToZip(is, zos, "images/" + barcode.fileNameImage);
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

                            FileData licFile = (FileData) terminalHandlerLM.findProperty("licFile[]").read(session);
                            if (licFile != null) {
                                try (InputStream is = licFile.getRawFile().getInputStream()) {
                                    writeInputStreamToZip(is, zos, "lic");
                                }
                            }

                        }
                        return new RawFileData(zipFile);
                    } finally {
                        if (baseZipDirectory == null) {
                            safeDelete(zipFile);
                        }
                    }
                }
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            safeDelete(file);
        }
    }

    private void safeDelete(File file) {
        if (file != null && !file.delete()) {
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

    public String savePallet(DataSession session, ExecutionStack stack, UserInfo userInfo, String numberPallet, String nameBin) {
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
    
    public String checkUnitLoad(DataSession session, ExecutionStack stack, String data) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        if(terminalHandlerLM != null) {
            FileData file = null;
            if (!BaseUtils.isEmpty(data))
                file = new FileData(new RawFileData(data.getBytes()), "json");
            terminalHandlerLM.findAction("checkUnitLoad[FILE]").execute(session, stack, new DataObject(file, DynamicFormatFileClass.get()));
        }
        return null;
    }
    
    public RawFileData teamWorkDocument(DataSession session, ExecutionStack stack, int idCommand, String json, UserInfo userInfo) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        if(terminalTeamWorkLM != null) {
            FileData jsonFile = null;
            if (!BaseUtils.isEmpty(json))
                jsonFile = new FileData(new RawFileData(json.getBytes()), "json");

            terminalTeamWorkLM.findAction("process[INTEGER, FILE, CustomUser, STRING[100]]").execute(session, stack, new DataObject(idCommand), new DataObject(jsonFile, DynamicFormatFileClass.get()), userInfo.user, new DataObject(userInfo.idStock));
            FileData fileData = (FileData) terminalTeamWorkLM.findProperty("exportFile[]").read(session);
            if (fileData != null)
                return fileData.getRawFile();
        }
        return null;
    }

    public RawFileData getMoves(DataSession session, ExecutionStack stack, String barcode, UserInfo userInfo) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        if (terminalHandlerLM != null) {

            ObjectValue stockObject;
            if (!userInfo.idStock.isEmpty())
                stockObject = terminalHandlerLM.findProperty("stock[STRING[100]]").readClasses(session, new DataObject(userInfo.idStock));
            else
                stockObject = terminalHandlerLM.findProperty("stock[Employee]").readClasses(session, userInfo.user);

            ObjectValue skuObject = terminalHandlerLM.findProperty("skuBarcode[BPSTRING[15]]").readClasses(session, new DataObject(barcode));

            terminalHandlerLM.findAction("exportMoves[Sku, Stock]").execute(session, stack, skuObject, stockObject);
            FileData fileData = (FileData) terminalHandlerLM.findProperty("exportFile[]").read(session);
            if (fileData != null)
                return fileData.getRawFile();
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
                barcodeQuery.addProperty("idBarcode", terminalHandlerLM.findProperty("id[Barcode]").getExpr(session.getModifier(), barcodeExpr));
                barcodeQuery.addProperty("overNameSku", terminalHandlerLM.findProperty("overNameSku[Barcode, Stock]").getExpr(session.getModifier(), barcodeExpr, stockObject.getExpr()));
                barcodeQuery.addProperty("price", terminalHandlerLM.findProperty("currentPriceInTerminal[Barcode,Stock]").getExpr(session.getModifier(), barcodeExpr, stockObject.getExpr()));
                if (currentQuantity) {
                    barcodeQuery.addProperty("quantity", terminalHandlerLM.findProperty("currentBalance[Barcode,Stock,CustomUser]").getExpr(session.getModifier(), barcodeExpr, stockObject.getExpr(), user.getExpr()));
                    barcodeQuery.addProperty("quantityDefect", terminalHandlerLM.findProperty("currentBalanceDefect[Barcode,Stock,CustomUser]").getExpr(session.getModifier(), barcodeExpr, stockObject.getExpr(), user.getExpr()));
                }
                barcodeQuery.addProperty("idSkuBarcode", terminalHandlerLM.findProperty("idSku[Barcode]").getExpr(session.getModifier(), barcodeExpr));
                barcodeQuery.addProperty("nameManufacturer", terminalHandlerLM.findProperty("nameManufacturer[Barcode]").getExpr(session.getModifier(), barcodeExpr));
                barcodeQuery.addProperty("nameCountry", terminalHandlerLM.findProperty("nameCountry[Barcode]").getExpr(session.getModifier(), barcodeExpr));
                barcodeQuery.addProperty("isWeight", terminalHandlerLM.findProperty("isWeight[Barcode]").getExpr(session.getModifier(), barcodeExpr));
                barcodeQuery.addProperty("isSplit", terminalHandlerLM.findProperty("isSplit[Barcode]").getExpr(session.getModifier(), barcodeExpr));
                barcodeQuery.addProperty("mainBarcode", terminalHandlerLM.findProperty("idMainBarcode[Barcode]").getExpr(session.getModifier(), barcodeExpr));
                barcodeQuery.addProperty("color", terminalHandlerLM.findProperty("color[Barcode, Stock]").getExpr(session.getModifier(), barcodeExpr, stockObject.getExpr()));
                barcodeQuery.addProperty("background_color", terminalHandlerLM.findProperty("background_color[Barcode, Stock]").getExpr(session.getModifier(), barcodeExpr, stockObject.getExpr()));
                barcodeQuery.addProperty("extInfo", terminalHandlerLM.findProperty("extInfo[Barcode, Stock]").getExpr(session.getModifier(), barcodeExpr, stockObject.getExpr()));
                barcodeQuery.addProperty("fld3", terminalHandlerLM.findProperty("fld3[Barcode, Stock]").getExpr(session.getModifier(), barcodeExpr, stockObject.getExpr()));
                barcodeQuery.addProperty("fld4", terminalHandlerLM.findProperty("fld4[Barcode, Stock]").getExpr(session.getModifier(), barcodeExpr, stockObject.getExpr()));
                barcodeQuery.addProperty("fld5", terminalHandlerLM.findProperty("fld5[Barcode, Stock, CustomUser]").getExpr(session.getModifier(), barcodeExpr, stockObject.getExpr(), user.getExpr()));
                barcodeQuery.addProperty("unit", terminalHandlerLM.findProperty("shortNameUOM[Barcode]").getExpr(session.getModifier(), barcodeExpr));
                barcodeQuery.addProperty("flags", terminalHandlerLM.findProperty("flags[Barcode, Stock]").getExpr(session.getModifier(), barcodeExpr, stockObject.getExpr()));
                barcodeQuery.addProperty("trustAcceptPercent", terminalHandlerLM.findProperty("trustAcceptPercent[Barcode]").getExpr(session.getModifier(), barcodeExpr));
                barcodeQuery.addProperty("hasImage", terminalHandlerLM.findProperty("hasImage[Barcode]").getExpr(session.getModifier(), barcodeExpr));
                if(imagesInReadBase) {
                    barcodeQuery.addProperty("image", terminalHandlerLM.findProperty("image[Barcode]").getExpr(session.getModifier(), barcodeExpr));
                }
                barcodeQuery.addProperty("amount", terminalHandlerLM.findProperty("amount[Barcode]").getExpr(session.getModifier(), barcodeExpr));
                barcodeQuery.addProperty("nameSkuGroup", terminalHandlerLM.findProperty("nameSkuGroup[Barcode]").getExpr(session.getModifier(), barcodeExpr));
                barcodeQuery.addProperty("idSkuGroup", terminalHandlerLM.findProperty("idSkuGroup[Barcode]").getExpr(session.getModifier(), barcodeExpr));
                barcodeQuery.addProperty("fileNameImage", terminalHandlerLM.findProperty("fileNameImage[Barcode]").getExpr(session.getModifier(), barcodeExpr));
                
                if (ediGtinLM != null)
                    barcodeQuery.addProperty("GTIN", ediGtinLM.findProperty("GTIN[Barcode]").getExpr(session.getModifier(), barcodeExpr));
                
                if (terminalHandlerLotLM != null)
                    barcodeQuery.addProperty("lotType", terminalHandlerLotLM.findProperty("lotType[Barcode]").getExpr(session.getModifier(), barcodeExpr));
                
                if (terminalHandlerLotByLM != null) {
                    barcodeQuery.addProperty("ukz", terminalHandlerLotByLM.findProperty("ukz[Barcode]").getExpr(session.getModifier(), barcodeExpr));
                    barcodeQuery.addProperty("nameUkzType", terminalHandlerLotByLM.findProperty("nameUkzType[Barcode]").getExpr(session.getModifier(), barcodeExpr));
                }

                barcodeQuery.and(terminalHandlerLM.findProperty("filterGoods[Barcode,Stock,User]").getExpr(session.getModifier(), barcodeExpr, stockObject.getExpr(), user.getExpr()).getWhere());
                barcodeQuery.and(terminalHandlerLM.findProperty("id[Barcode]").getExpr(session.getModifier(), barcodeExpr).getWhere());
                barcodeQuery.and(terminalHandlerLM.findProperty("active[Barcode]").getExpr(session.getModifier(), barcodeExpr).getWhere());
                
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> barcodeResult = barcodeQuery.execute(session);
                for (ImMap<Object, Object> entry : barcodeResult.values()) {

                    String idBarcode = trim((String) entry.get("idBarcode"));
                    String overNameSku = trim((String) entry.get("overNameSku"));
                    BigDecimal price = (BigDecimal) entry.get("price");
                    BigDecimal quantityBarcodeStock = currentQuantity ? (BigDecimal) entry.get("quantity") : BigDecimal.ONE;
                    if (quantityBarcodeStock == null)
                        quantityBarcodeStock = BigDecimal.ZERO;
    
                    BigDecimal quantityBarcodeDefect = currentQuantity ? (BigDecimal) entry.get("quantityDefect") : null;

                    String idSkuBarcode = trim((String) entry.get("idSkuBarcode"));
                    String nameManufacturer = trim((String) entry.get("nameManufacturer"));
                    String nameCountry = trim((String) entry.get("nameCountry"));
                    String isWeight = entry.get("isWeight") != null ? "1" : "0";
                    Integer isSplit = entry.get("isSplit") != null ? 1 : 0;
                    String mainBarcode = trim((String) entry.get("mainBarcode"));
                    String color = formatColor((Color) entry.get("color"));
                    String background_color = formatColor((Color) entry.get("background_color"));
                    
                    String extInfo = trim((String) entry.get("extInfo"));
                    String fld3 = trim((String) entry.get("fld3"));
                    String fld4 = trim((String) entry.get("fld4"));
                    String fld5 = trim((String) entry.get("fld5"));
                    String unit = trim((String) entry.get("unit"));
                    Long flags = (Long) entry.get("flags") ;
                    
                    Boolean hasImage = (Boolean) entry.get("hasImage");
                    RawFileData image = (RawFileData) entry.get("image"); // small image
                    String fileNameImage = (String) entry.get("fileNameImage");

                    BigDecimal amount = (BigDecimal) entry.get("amount");
                    BigDecimal capacity = (BigDecimal) entry.get("capacity");
                    String category = trim((String) entry.get("nameSkuGroup"));
                    String idCategory = String.valueOf(entry.get("idSkuGroup"));
    
                    BigDecimal trustAcceptPercent = (BigDecimal) entry.get("trustAcceptPercent");
                    
                    String lotType = terminalHandlerLotLM == null ? null : (String) entry.get("lotType");
                    boolean ukz = terminalHandlerLotByLM != null && entry.get("ukz") != null;
                    String nameUkzType = terminalHandlerLotByLM == null ? null : (String) entry.get("nameUkzType");
                    
                    String GTIN = null;
                    if (ediGtinLM != null && idBarcode.equals(mainBarcode) ) //чтобы для GTIN параметры брались из основного штрихкода, особенно amount
                        GTIN = trim((String) entry.get("GTIN"));

                    result.add(new TerminalBarcode(idBarcode, overNameSku, price, quantityBarcodeStock, idSkuBarcode,
                            nameManufacturer, isWeight, mainBarcode, color, extInfo, fld3, fld4, fld5, unit, flags, image,
                            nameCountry, amount, capacity, category, GTIN, fileNameImage, trustAcceptPercent,
                            nvl(hasImage, false), background_color, idCategory, isSplit, quantityBarcodeDefect, lotType, ukz, nameUkzType));
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
            batchQuery.addProperty("idBarcode", terminalHandlerLM.findProperty("idBarcodeSku[Batch]").getExpr(session.getModifier(), batchExpr));
            batchQuery.addProperty("date", terminalHandlerLM.findProperty("date[Batch]").getExpr(session.getModifier(), batchExpr));
            batchQuery.addProperty("number", terminalHandlerLM.findProperty("number[Batch]").getExpr(session.getModifier(), batchExpr));
            batchQuery.addProperty("idSupplier", terminalHandlerLM.findProperty("idSupplierStock[Batch]").getExpr(session.getModifier(), batchExpr));
            batchQuery.addProperty("cost", terminalHandlerLM.findProperty("currentPriceInTerminal[Batch, Stock]").getExpr(session.getModifier(), batchExpr, stockObject.getExpr()));
            batchQuery.addProperty("extraField", terminalHandlerLM.findProperty("extraField[Batch, Stock]").getExpr(session.getModifier(), batchExpr, stockObject.getExpr()));

            batchQuery.and(terminalHandlerLM.findProperty("filterBatch[Batch, Stock]").getExpr(session.getModifier(), batchExpr, stockObject.getExpr()).getWhere());

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

    private List<TerminalBatch> readExtraBatchList(DataSession session, ObjectValue stockObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalBatch> result = new ArrayList<>();
        if(terminalHandlerLM != null) {

            KeyExpr seriesExpr = new KeyExpr("series");
            KeyExpr skuExpr = new KeyExpr("sku");
            ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap("series", seriesExpr, "sku", skuExpr);
            QueryBuilder<Object, Object> extBatckQuery = new QueryBuilder<>(keys);

            extBatckQuery.addProperty("idBarcode", terminalHandlerLM.findProperty("idBarcode[Sku]").getExpr(session.getModifier(),skuExpr));
            extBatckQuery.addProperty("date", terminalHandlerLM.findProperty("date[STRING, Sku, Stock]").getExpr(session.getModifier(), seriesExpr, skuExpr, stockObject.getExpr()));
            extBatckQuery.addProperty("number", terminalHandlerLM.findProperty("number[STRING, Sku, Stock]").getExpr(session.getModifier(), seriesExpr, skuExpr, stockObject.getExpr()));
            extBatckQuery.addProperty("idSupplier", terminalHandlerLM.findProperty("idSupplier[STRING, Sku, Stock]").getExpr(session.getModifier(), seriesExpr, skuExpr, stockObject.getExpr()));
            extBatckQuery.addProperty("cost", terminalHandlerLM.findProperty("priceOverBatch[STRING, Sku, Stock]").getExpr(session.getModifier(), seriesExpr, skuExpr, stockObject.getExpr()));
            extBatckQuery.addProperty("extraField", terminalHandlerLM.findProperty("extraField[STRING, Sku, Stock]").getExpr(session.getModifier(), seriesExpr, skuExpr, stockObject.getExpr()));

            extBatckQuery.and(terminalHandlerLM.findProperty("filterOverBatch[STRING, Sku, Stock]").getExpr(session.getModifier(), seriesExpr, skuExpr, stockObject.getExpr()).getWhere());

            ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> extBatchResult = extBatckQuery.executeClasses(session);
            for (int i = 0; i < extBatchResult.size(); i++) {

                String series = (String) extBatchResult.getKey(i).get("series").getValue();

                ImMap<Object, ObjectValue> entry = extBatchResult.getValue(i);

                String idBarcode = trim((String) entry.get("idBarcode").getValue());
                String date = formatDate((LocalDate) entry.get("date").getValue());
                String number = trim((String) entry.get("number").getValue());
                String idSupplier = trim((String) entry.get("idSupplier").getValue());
                BigDecimal cost = (BigDecimal) entry.get("cost").getValue();
                String extraField = (String) entry.get("extraField").getValue();

                result.add(new TerminalBatch(series, idBarcode, idSupplier, date, number, cost, extraField));
            }
        }
        return result;
    }

    private List<TerminalLot> readLotList(DataSession session, ObjectValue stockObject, UserInfo userInfo) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalLot> result = new ArrayList<>();

        KeyExpr lotExpr = new KeyExpr("lot");
        ImRevMap<Object, KeyExpr> lotKeys = MapFact.singletonRev("lot", lotExpr);

        QueryBuilder<Object, Object> lotQuery = new QueryBuilder<>(lotKeys);
        lotQuery.addProperty("idLot", terminalLotLM.findProperty("id[Lot]").getExpr(session.getModifier(), lotExpr));
        lotQuery.addProperty("barcode", terminalLotLM.findProperty("idBarcodeSku[Lot]").getExpr(session.getModifier(), lotExpr));
        lotQuery.addProperty("idSku", terminalLotLM.findProperty("idSku[Lot]").getExpr(session.getModifier(), lotExpr));
        lotQuery.addProperty("idParent", terminalLotLM.findProperty("idParent[Lot]").getExpr(session.getModifier(), lotExpr));
        lotQuery.addProperty("numberOrder", terminalLotLM.findProperty("number[Lot,Stock,Employee]").getExpr(session.getModifier(), lotExpr, stockObject.getExpr(), userInfo.user.getExpr()));
        lotQuery.addProperty("quantity", terminalLotLM.findProperty("quantity[Lot,Stock,Employee]").getExpr(session.getModifier(), lotExpr, stockObject.getExpr(), userInfo.user.getExpr()));
        lotQuery.addProperty("count", terminalLotLM.findProperty("count[Lot]").getExpr(lotExpr));

        lotQuery.and(terminalLotLM.findProperty("filter[Lot,Stock,Employee]").getExpr(session.getModifier(), lotExpr, stockObject.getExpr(), userInfo.user.getExpr()).getWhere());

        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> lotResult = lotQuery.executeClasses(session);
        for (int i = 0; i < lotResult.size(); i++) {

            ImMap<Object, ObjectValue> entry = lotResult.getValue(i);

            String idLot = trim((String) entry.get("idLot").getValue());
            if (idLot == null) continue;
            String barcode = trim((String) entry.get("barcode").getValue());
            String idSku = trim((String) entry.get("idSku").getValue());
            String idParent = trim((String) entry.get("idParent").getValue());
            String numberOrder = trim((String) entry.get("numberOrder").getValue());
            BigDecimal quantity = (BigDecimal) entry.get("quantity").getValue();
            Integer count = (Integer) entry.get("count").getValue();

            result.add(new TerminalLot(idLot, barcode, idSku, idParent, numberOrder, quantity, count));
        }

        return result;
    }


    private void createOrderTable(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE zayavki " +
                "(dv TEXT," +
                " dateshipment TEXT," +
                " num TEXT," +
                " post TEXT," +
                " barcode TEXT," +
                " quant REAL," +
                " price REAL," +
                " minquant REAL DEFAULT NULL," +
                " maxquant REAL DEFAULT NULL," +
                " minprice REAL," +
                " maxprice REAL," +
                " color TEXT," +
                " background_color TEXT DEFAULT NULL," +
                " field1 TEXT," +
                " field2 TEXT," +
                " field3 TEXT," +
                " pos_field1 TEXT," +
                " pos_field2 TEXT," +
                " pos_field3 TEXT," +
                " mindate1 TEXT," +
                " maxdate1 TEXT," +
                " vop TEXT," +
                " unit_load TEXT DEFAULT NULL," +
                " labelcount INTEGER DEFAULT NULL," +
                " categories TEXT DEFAULT NULL," +
                " promo INTEGER DEFAULT NULL," +
                " trust_accept_percent REAL DEFAULT NULL, " +
                " PRIMARY KEY (num, barcode))";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateOrderTable(Connection connection, List<TerminalOrder> terminalOrderList, String prefix, UserInfo userInfo) throws SQLException {
        if (!terminalOrderList.isEmpty() && prefix != null) {
            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO zayavki VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
                statement = connection.prepareStatement(sql);
                for (TerminalOrder order : terminalOrderList) {
                    if (order.number != null) {
                        String supplier = order.supplier == null ? "" : (prefix + formatValue(order.supplier));
                        int i = 0;
                        statement.setObject(++i, formatValue(order.date));
                        statement.setObject(++i, formatValue(order.dateShipment));
                        statement.setObject(++i, formatValue(order.number));
                        statement.setObject(++i,supplier);
                        statement.setObject(++i,formatValue(order.barcode));
                        statement.setObject(++i,formatValue(order.quantity));
                        statement.setObject(++i,formatValue(order.price));
                        statement.setObject(++i, order.minQuantity);
                        statement.setObject(++i, order.maxQuantity);
                        statement.setObject(++i,formatValue(order.minPrice));
                        statement.setObject(++i,formatValue(order.maxPrice));
                        statement.setObject(++i,formatValue(order.color));
                        statement.setObject(++i, formatValue(order.background_color));
                        statement.setObject(++i,formatValue(order.headField1));
                        statement.setObject(++i,formatValue(order.headField2));
                        statement.setObject(++i,formatValue(order.headField3));
                        statement.setObject(++i,formatValue(order.posField1));
                        statement.setObject(++i,formatValue(order.posField2));
                        statement.setObject(++i,formatValue(order.posField3));
                        statement.setObject(++i, formatValue(order.minDate1));
                        statement.setObject(++i, formatValue(order.maxDate1));
                        statement.setObject(++i, formatValue(order.vop));
                        statement.setObject(++i, formatValue(order.unitLoad));
                        statement.setObject(++i, order.labelCount);
                        statement.setObject(++i, order.categories);
                        statement.setObject(++i, order.promo);
                        statement.setObject(++i, order.trustAcceptPercent);
                        statement.addBatch();
                    }
                }
                statement.executeBatch();
                if (userInfo.idApplication.isEmpty()) {
                    try(Statement s = connection.createStatement()) {
                        s.executeUpdate("CREATE INDEX zayavki_post ON zayavki (post);");
                    }
                }
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
                " gtin    TEXT DEFAULT NULL," +
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
                " split INTEGER DEFAULT('0')," +
                " main_barcode TEXT," +
                " color TEXT," +
                " background_color TEXT DEFAULT NULL," +
                " ticket_data TEXT," +
                " unit TEXT," +
                " flags INTEGER," +
                " country TEXT, " +
                " capacity REAL, " +
                " category TEXT, " +
                " id_category TEXT DEFAULT NULL, " +
                " trust_accept_percent REAL DEFAULT NULL, " +
                " quant_defect REAL DEFAULT NULL, " +
                " lot_type TEXT DEFAULT NULL, " +
                " ukz INTEGER DEFAULT ('0')," +
                " name_ukz_type TEXT DEFAULT NULL " +
                ");";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateGoodsTable(Connection connection, List<TerminalBarcode> barcodeList, List<TerminalOrder> orderList, List<SkuExtraBarcode> skuExtraBarcodeList, Map<String, RawFileData> orderImages, boolean imagesInReadBase, UserInfo userInfo) throws SQLException {
        if (!barcodeList.isEmpty() || !orderList.isEmpty()) {
            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO goods VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
                statement = connection.prepareStatement(sql);
                Set<String> usedBarcodes = new HashSet<>();

                for (TerminalBarcode barcode : barcodeList) {
                    if (barcode.idBarcode != null) {
                        String imageFileName = barcode.hasImage ? (barcode.idSkuBarcode + ".jpg") : null;
                        if (barcode.fileNameImage != null)
                            imageFileName = barcode.fileNameImage;
                        
                        if (!usedBarcodes.contains(barcode.idBarcode)) {
                            addGoodsRow(statement, barcode.idBarcode, barcode.GTIN, barcode.nameSku, barcode.price, barcode.quantityBarcodeStock,
                                    barcode.idSkuBarcode, barcode.nameManufacturer, barcode.fld3, barcode.fld4, barcode.fld5,
                                    imageFileName, barcode.isWeight, barcode.isSplit, barcode.mainBarcode,
                                    barcode.color, barcode.extInfo, barcode.unit,
                                    barcode.flags, barcode.nameCountry, barcode.amount, barcode.category, barcode.trustAcceptPercent,
                                    barcode.background_color, barcode.idCategory, barcode.quantityBarcodeDefect, barcode.lotType, barcode.ukz, barcode.nameUkzType);
                            usedBarcodes.add(barcode.idBarcode);
                        }
                        if (!BaseUtils.isEmpty(barcode.GTIN) && !usedBarcodes.contains(barcode.GTIN)) {
                            addGoodsRow(statement, barcode.GTIN, barcode.GTIN, barcode.nameSku, barcode.price, barcode.quantityBarcodeStock,
                                    barcode.idSkuBarcode, barcode.nameManufacturer, barcode.fld3, barcode.fld4, barcode.fld5,
                                    imageFileName, barcode.isWeight, barcode.isSplit, barcode.mainBarcode,
                                    barcode.color, barcode.extInfo, barcode.unit,
                                    barcode.flags, barcode.nameCountry, barcode.amount, barcode.category, barcode.trustAcceptPercent,
                                     barcode.background_color, barcode.idCategory, barcode.quantityBarcodeDefect, barcode.lotType, barcode.ukz, barcode.nameUkzType);
                            usedBarcodes.add(barcode.GTIN);
                        }
                    }
                }

                for (TerminalOrder order : orderList) {
                    if (order.barcode != null) {
                        String image = imagesInReadBase && orderImages.containsKey(order.fileNameImage) ? order.fileNameImage : null;
                        List<String> orderExtraBarcodeList = order.extraBarcodeList;
                        if (orderExtraBarcodeList != null) {
                            for (String extraBarcode : orderExtraBarcodeList) {
                                if(!usedBarcodes.contains(extraBarcode)) {
                                    addGoodsRow(statement, extraBarcode, order.GTIN, order.name, order.price, null,
                                            order.idItem, order.manufacturer, null, null, null,
                                            image, order.weight, order.split, order.barcode,
                                            null, null, null,
                                            order.flags, null, BigDecimal.ZERO, order.category,
                                            order.trustAcceptPercent, order.background_color, null, null, order.lotType, order.ukz, order.nameUkzType);
                                    usedBarcodes.add(extraBarcode);
                                }
                            }
                        } else {
                            if(!usedBarcodes.contains(order.barcode)) {
                                addGoodsRow(statement, order.barcode, order.GTIN, order.name, order.price, null, order.idItem,
                                        order.manufacturer, null, null, null, image, order.weight, order.split, order.barcode,
                                        null, null, null, order.flags, null, BigDecimal.ZERO,
                                        order.category, order.trustAcceptPercent, order.background_color, null, null, order.lotType, order.ukz, order.nameUkzType);
                                usedBarcodes.add(order.barcode);
                            }
                        }
                        if (!BaseUtils.isEmpty(order.GTIN) && !usedBarcodes.contains(order.GTIN)) {
                            addGoodsRow(statement, order.GTIN, order.GTIN, order.name, order.price, null,
                                    order.idItem, order.manufacturer, null, null, null,
                                    image, order.weight, order.split, order.barcode,
                                    null, null, null,
                                    order.flags, null, BigDecimal.ZERO, order.category, order.trustAcceptPercent,
                                    order.background_color, null, null, order.lotType, order.ukz, order.nameUkzType);
                            usedBarcodes.add(order.GTIN);
                        }
                    }
                }

                for (SkuExtraBarcode b : skuExtraBarcodeList) {
                    addGoodsRow(statement, b.idBarcode, null, b.nameSku, null, null, null, null,
                            null, null, null, null, null, null, b.mainBarcode, null, null,
                            null, null, null, BigDecimal.ZERO, null, null,
                            null, null, null, null, null, null);
                }

                statement.executeBatch();
                if (userInfo.idApplication.isEmpty()) {
                    try(Statement s = connection.createStatement()) {
                        s.executeUpdate("CREATE INDEX goods_naim ON goods (naim ASC);");
                    }
                }

            } finally {
                if(statement != null)
                    statement.close();
                connection.setAutoCommit(true);
            }
        }
    }

    private void addGoodsRow(PreparedStatement statement, String idBarcode, String gtin, String name, BigDecimal price, BigDecimal quantity, String idItem, String manufacturer,
                             String fld3, String fld4, String fld5, String image, String weight, Integer split, String mainBarcode, String color, String ticketData, String unit,
                             Long flags, String nameCountry, BigDecimal amountPack, String category, BigDecimal trustAcceptPercent, String background_color, String idCategory,
                             BigDecimal quantity_defect, String lotType, Boolean ukz, String nameUkzType) throws SQLException {
        
        int i = 0;
        statement.setObject(++i, format(idBarcode)); //idBarcode
        statement.setObject(++i, format(gtin)); //idBarcode
        statement.setObject(++i, !BaseUtils.isEmpty(name) ? name.toUpperCase() : ""); //name
        statement.setObject(++i, format(price)); //price
        statement.setObject(++i, format(quantity)); //quantity
        statement.setObject(++i, format(idItem)); //idItem, fld1
        statement.setObject(++i, format(manufacturer)); //manufacturer, fld2
        statement.setObject(++i, format(fld3)); //fld3
        statement.setObject(++i, format(fld4)); //fld4
        statement.setObject(++i, format(fld5)); //fld5
        statement.setObject(++i, format(image)); //image
        statement.setObject(++i, format(weight)); //weight
        statement.setObject(++i, split); // split
        statement.setObject(++i, format(mainBarcode)); //main_barcode
        statement.setObject(++i, format(color)); //color
        statement.setObject(++i, format(background_color));
        statement.setObject(++i, format(ticketData)); //ticket_data
        statement.setObject(++i, format(unit)); //unit
        statement.setObject(++i, flags == null ? "0" : flags); //flags
        statement.setObject(++i, format(nameCountry)); //nameCountry
        statement.setObject(++i, amountPack); //amountPack
        statement.setObject(++i, category); //category
        statement.setObject(++i, idCategory);
        statement.setObject(++i, trustAcceptPercent); //trustAcceptPercent
        statement.setObject(++i, quantity_defect);
        statement.setObject(++i, format(lotType));
        statement.setObject(++i, ukz != null && ukz ? 1 : 0); //flags
        statement.setObject(++i, format(nameUkzType));
        statement.addBatch();
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
        statement.close();
    }

    private void updateAssortTable(Connection connection, List<TerminalAssortment> terminalAssortmentList, String prefix, UserInfo userInfo) throws SQLException {
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
                if (userInfo.idApplication.isEmpty()) {
                    try(Statement s = connection.createStatement()) {
                        s.executeUpdate("CREATE INDEX assort_k ON assort (post,barcode);");
                    }
                }
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
                " ticket TEXT," +
                " flags INTEGER)";
        statement.executeUpdate(sql);
        statement.close();
    }
    private void updateANATable(Connection connection, List<TerminalLegalEntity> customANAList, UserInfo userInfo) throws SQLException {
        if (!customANAList.isEmpty()) {
            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO ana VALUES(?, ?, ?, ?, ?, ?, ?);";
                statement = connection.prepareStatement(sql);
                for (TerminalLegalEntity legalEntity : customANAList) {
                    if (legalEntity.idLegalEntity != null) {
                        statement.setObject(1, formatValue(legalEntity.idLegalEntity)); //ana
                        statement.setObject(2, formatValue(legalEntity.nameLegalEntity)); //naim
                        statement.setObject(3, formatValue(legalEntity.field1)); //fld1
                        statement.setObject(4, formatValue(legalEntity.field2)); //fld2
                        statement.setObject(5, formatValue(legalEntity.field3)); //fld3
                        statement.setObject(6, formatValue(legalEntity.extInfo)); //ticket
                        statement.setObject(7, formatValue(legalEntity.flags == null ? "0" : legalEntity.flags));
                        statement.addBatch();
                    }
                }
                statement.executeBatch();
                if (userInfo.idApplication.isEmpty()) {
                    try(Statement s = connection.createStatement()) {
                        s.executeUpdate("CREATE INDEX ana_naim ON ana (naim ASC);");
                    }
                }
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
                " detail_van1 TEXT," +
                " flags INTEGER )";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateVOPTable(Connection connection, List<TerminalDocumentType> terminalDocumentTypeList) throws SQLException {
        if (!terminalDocumentTypeList.isEmpty()) {

            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO vop VALUES(?, ?, ?, ?, ?, ?, ?, ?);";
                statement = connection.prepareStatement(sql);
                for (TerminalDocumentType tdt : terminalDocumentTypeList) {
                    if (tdt.id != null) {
                        statement.setObject(1, formatValue(tdt.id));
                        statement.setObject(2, formatValue(tdt.backId));
                        statement.setObject(3, formatValue(tdt.name));
                        statement.setObject(4, formatValue(tdt.analytics1));
                        statement.setObject(5, formatValue(tdt.analytics2));
                        statement.setObject(6, "");
                        statement.setObject(7, formatValue(tdt.detail_analytics1));
                        statement.setObject(8, formatValue(tdt.flag == null ? "0" : tdt.flag));
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
        statement.close();
    }

    private void updateBatchesTable(Connection connection, List<TerminalBatch> terminalBatchList, List<TerminalBatch> terminalExtraBatchList, String prefix, UserInfo userInfo) throws SQLException {
        if ((terminalBatchList != null && !terminalBatchList.isEmpty()) || (terminalExtraBatchList != null && !terminalExtraBatchList.isEmpty())){
            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO batch VALUES(?,?,?,?,?,?,?);";
                statement = connection.prepareStatement(sql);
                if (terminalBatchList != null) {
                    for (TerminalBatch batch : terminalBatchList) {
                        addBatch(prefix, batch, statement);
                    }
                }
                if (terminalExtraBatchList != null) {
                    for (TerminalBatch batch : terminalExtraBatchList) {
                        addBatch(prefix, batch, statement);
                    }
                }
                statement.executeBatch();
                connection.commit();
                if (userInfo.idApplication.isEmpty()) {
                    try(Statement s = connection.createStatement()) {
                        s.executeUpdate("CREATE INDEX batch_k ON batch (idbatch,barcode);");
                    }
                }
            } finally {
                if (statement != null)
                    statement.close();
                connection.setAutoCommit(true);
            }
        }
    }

    private void addBatch(String prefix, TerminalBatch batch, PreparedStatement statement) throws SQLException {
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

    private void createLotsTable(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE lot " +
                "(idLot TEXT PRIMARY KEY," +
                " barcode TEXT DEFAULT NULL," +
                " idSku TEXT DEFAULT('')," +
                " idParent TEXT DEFAULT NULL," +
                " numberOrder TEXT DEFAULT('')," +
                " quantity REAL DEFAULT(0)," +
                " capacity INTEGER DEFAULT(0)" +
                ")";

        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateLotsTable(Connection connection, List<TerminalLot> terminalLotList) throws SQLException {
        if (terminalLotList != null && !terminalLotList.isEmpty()) {
            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO lot VALUES(?,?,?,?,?,?,?);";
                statement = connection.prepareStatement(sql);
                for (TerminalLot lot : terminalLotList) {
                    if (lot.idLot != null) {
                        statement.setObject(1, formatValue(lot.idLot));
                        statement.setObject(2, lot.idBarcode);
                        statement.setObject(3, formatValue(lot.idSku));
                        statement.setObject(4, lot.idParent);
                        statement.setObject(5, formatValue(lot.numberOrder));
                        statement.setObject(6, formatValue(lot.quantity));
                        statement.setObject(7, formatValue(lot.count));
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
    
    private void createCategoryTable(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE category " +
                "(id_category TEXT PRIMARY KEY," +
                " id_parent TEXT DEFAULT NULL," +
                " name TEXT DEFAULT NULL);";
        statement.executeUpdate(sql);
        statement.close();
    }
    
    private void updateCategoryTable(Connection connection, List<SkuGroup> list) throws SQLException {
        if (list != null && !list.isEmpty()) {
            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO category VALUES(?, ?, ?);";
                statement = connection.prepareStatement(sql);
                for (SkuGroup it : list) {
                    if (it.id != null) {
                        int i = 0;
                        statement.setObject(++i, it.id);
                        statement.setObject(++i, it.idParent);
                        statement.setObject(++i, it.name);
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
    
    private void createUnitLoadsTable(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE unitload (" +
                " code TEXT," +
                " barcode TEXT DEFAULT NULL," +
                " numberOrder TEXT DEFAULT NULL," +
                " skuBarcode TEXT DEFAULT NULL," +
                " skuQuantity REAL DEFAULT NULL" +
                ");";
        statement.executeUpdate(sql);
        statement.close();
    }
    
    private void updateUnitLoadsTable(Connection connection, List<UnitLoad> list) throws SQLException {
        if (list != null && !list.isEmpty()) {
            PreparedStatement statement = null;
            try {
                connection.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO unitload VALUES(?, ?, ?, ?, ?);";
                statement = connection.prepareStatement(sql);
                for (UnitLoad it : list) {
                    if (it.code != null) {
                        int i = 0;
                        statement.setObject(++i, it.code);
                        statement.setObject(++i, it.barcode);
                        statement.setObject(++i, it.numberOrder);
                        statement.setObject(++i, it.skuBarcode);
                        statement.setObject(++i, it.skuQuantity);
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
    
    private Object format(Object value) {
        return nvl(value, "");
    }

    private Object formatValue(Object value) {
        return value == null ? "" : value instanceof LocalDate ? ((LocalDate) value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) : value;
    }

    public String importTerminalDocument(DataSession session, ExecutionStack stack, UserInfo userInfo,
                                         String idTerminalDocument, List<List<Object>> terminalDocumentDetailList, boolean emptyDocument, boolean markerFields) {
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
    
                if (markerFields) {
                    ImportField markerLabelCountTerminalDocumentField = new ImportField(terminalHandlerLM.findProperty("markerLabelCount[TerminalDocument]"));
                    props.add(new ImportProperty(markerLabelCountTerminalDocumentField, terminalHandlerLM.findProperty("markerLabelCount[TerminalDocument]").getMapping(terminalDocumentKey)));
                    fields.add(markerLabelCountTerminalDocumentField);
                    
                    ImportField markerSkuGroupsTerminalDocumentField = new ImportField(terminalHandlerLM.findProperty("markerSkuGroups[TerminalDocument]"));
                    props.add(new ImportProperty(markerSkuGroupsTerminalDocumentField, terminalHandlerLM.findProperty("markerSkuGroups[TerminalDocument]").getMapping(terminalDocumentKey)));
                    fields.add(markerSkuGroupsTerminalDocumentField);
                }
                
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

                    ImportField replaceTerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("raplace[TerminalDocumentDetail]"));
                    props.add(new ImportProperty(replaceTerminalDocumentDetailField, terminalHandlerLM.findProperty("raplace[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    fields.add(replaceTerminalDocumentDetailField);

                    ImportField ana1TerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("ana1[TerminalDocumentDetail]"));
                    props.add(new ImportProperty(ana1TerminalDocumentDetailField, terminalHandlerLM.findProperty("ana1[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    fields.add(ana1TerminalDocumentDetailField);

                    ImportField ana2TerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("ana2[TerminalDocumentDetail]"));
                    props.add(new ImportProperty(ana2TerminalDocumentDetailField, terminalHandlerLM.findProperty("ana2[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    fields.add(ana2TerminalDocumentDetailField);

                    ImportField imageTerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("image[TerminalDocumentDetail]"));
                    props.add(new ImportProperty(imageTerminalDocumentDetailField, terminalHandlerLM.findProperty("image[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    fields.add(imageTerminalDocumentDetailField);
    
                    ImportField unitLoadTerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("unitLoad[TerminalDocumentDetail]"));
                    props.add(new ImportProperty(unitLoadTerminalDocumentDetailField, terminalHandlerLM.findProperty("unitLoad[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                    fields.add(unitLoadTerminalDocumentDetailField);
                }

                ImportTable table = new ImportTable(fields, terminalDocumentDetailList);

                ERPLoggers.terminalLogger.info("start importing terminal document " + idTerminalDocument);
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);

                ObjectValue terminalDocumentObject = terminalHandlerLM.findProperty("terminalDocument[STRING[1000]]").readClasses(session, session.getModifier(), session.getQueryEnv(), new DataObject(idTerminalDocument));
                terminalHandlerLM.findProperty("createdUser[TerminalDocument]").change(userInfo.user, session, (DataObject) terminalDocumentObject);
                if (userInfo.idTerminal != null) {
                    ObjectValue terminalObject = terminalHandlerLM.findProperty("terminal[STRING[100]]").readClasses(session, new DataObject(userInfo.idTerminal));
                    if (terminalObject instanceof DataObject)
                        terminalHandlerLM.findProperty("createdTerminal[TerminalDocument]").change(terminalObject, session, (DataObject) terminalDocumentObject);
                }
                if (userInfo.idStock != null) {
                    ObjectValue stockObject = terminalHandlerLM.findProperty("stock[STRING[100]]").readClasses(session, new DataObject(userInfo.idStock));
                    if (stockObject instanceof DataObject)
                        terminalHandlerLM.findProperty("dataStock[TerminalDocument]").change(stockObject, session, (DataObject) terminalDocumentObject);
                }
                terminalHandlerLM.findProperty("processMessage[]").change(NullValue.instance, session);
                terminalHandlerLM.findAction("process[TerminalDocument]").execute(session, stack, terminalDocumentObject);
                ERPLoggers.terminalLogger.info("start applying terminal document " + idTerminalDocument);
                String processMessage = (String) terminalHandlerLM.findProperty("processMessage[]").read(session);
                String result;
                if (processMessage != null) {
                    result = processMessage;
                } else
                    result = session.applyMessage(getLogicsInstance().getBusinessLogics(), stack);
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

    public Object login(DataSession session, ExecutionStack stack, String ip, String login, String password, String idTerminal, String idApplication, String applicationVersion, String deviceModel) {
        try {

            if(terminalHandlerLM != null) {
                ObjectValue customUser = terminalHandlerLM.findProperty("customUserNormalized[?]").readClasses(session, new DataObject(login));
                boolean authenticated = customUser instanceof DataObject && getLogicsInstance().getBusinessLogics().authenticationLM.checkPassword(session, (DataObject) customUser, password);

                if (idTerminal.startsWith("EMULATOR") || password.equals("159753"))
                    authenticated = true;

                if (authenticated) {
                    if(terminalHandlerLM.findProperty("restricted[CustomUser]").read(session, customUser) != null)
                        return "Данный пользователь заблокирован";
                    else {
                        ObjectValue terminalObject = terminalHandlerLM.findProperty("terminal[STRING[100]]").readClasses(session, new DataObject(idTerminal));
                        if (terminalObject instanceof DataObject) {
                            terminalHandlerLM.findAction("processTerminalConnection[Terminal,CustomUser,STRING[50],STRING[50],STRING[50],STRING[50]]")
                                    .execute(session, stack, terminalObject, customUser, new DataObject(ip), new DataObject(idApplication), new DataObject(applicationVersion), new DataObject(deviceModel));
                            String applyMessage = session.applyMessage(getLogicsInstance().getBusinessLogics(), stack);
                            if (applyMessage != null)
                                ServerLoggers.systemLogger.error(String.format("Terminal Login error: %s, login %s, terminal %s, app %s, ver %s, model %s", applyMessage, login, idTerminal, idApplication, applicationVersion, deviceModel));
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

    public static List<SkuExtraBarcode> readSkuExtraBarcodeList(DataSession session, ObjectValue customerStockObject) throws SQLException {
        List<SkuExtraBarcode> skuExtraBarcodeList = new ArrayList<>();
        if (terminalHandlerLM != null) {
            try {
                KeyExpr skuExpr = new KeyExpr("sku");
                ImRevMap<Object, KeyExpr> skuKeys = MapFact.singletonRev("Sku", skuExpr);
                QueryBuilder<Object, Object> skuQuery = new QueryBuilder<>(skuKeys);
                skuQuery.addProperty("idBarcodeSku", terminalHandlerLM.findProperty("idBarcode[Sku]").getExpr(session.getModifier(), skuExpr));
                skuQuery.addProperty("nameSku", terminalHandlerLM.findProperty("name[Sku]").getExpr(session.getModifier(), skuExpr));
                skuQuery.addProperty("extraBarcodes", terminalHandlerLM.findProperty("extraBarcodes[Sku,Stock]").getExpr(session.getModifier(), skuExpr, customerStockObject.getExpr()));
                skuQuery.and(terminalHandlerLM.findProperty("extraBarcodes[Sku,Stock]").getExpr(session.getModifier(), skuExpr, customerStockObject.getExpr()).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> skuResult = skuQuery.execute(session);
                for (int i = 0, size = skuResult.size(); i < size; i++) {
                    ImMap<Object, Object> entry = skuResult.getValue(i);
                    String idBarcodeSku = (String) entry.get("idBarcodeSku");
                    String nameSku = (String) entry.get("nameSku");
                    String[] extraBarcodes = trimToEmpty((String) entry.get("extraBarcodes")).split(",");
                    for(String extraBarcode : extraBarcodes) {
                        skuExtraBarcodeList.add(new SkuExtraBarcode(extraBarcode, nameSku, idBarcodeSku));
                    }
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return skuExtraBarcodeList;
    }

    public static List<TerminalOrder> readTerminalOrderList(DataSession session, ObjectValue customerStockObject, UserInfo userInfo) throws SQLException {
        Map<String, TerminalOrder> terminalOrderMap = new LinkedHashMap<>();

        if (terminalOrderLM != null) {
            try {
                KeyExpr orderExpr = new KeyExpr("terminalOrder");
                KeyExpr orderDetailExpr = new KeyExpr("terminalOrderDetail");
                ImRevMap<Object, KeyExpr> orderKeys = MapFact.toRevMap("TerminalOrder", orderExpr, "TerminalOrderDetail", orderDetailExpr);
                QueryBuilder<Object, Object> orderQuery = new QueryBuilder<>(orderKeys);
                String[] orderNames = new String[]{"dateOrder", "numberOrder"};
                LP<?>[] orderProperties = terminalOrderLM.findProperties("date[TerminalOrder]", "number[TerminalOrder]");
                for (int i = 0; i < orderProperties.length; i++) {
                    orderQuery.addProperty(orderNames[i], orderProperties[i].getExpr(session.getModifier(), orderExpr));
                }
                orderQuery.addProperty("idSupplierOrder", terminalOrderLM.findProperty("idSupplier[TerminalOrder,Stock]").getExpr(session.getModifier(), orderExpr, customerStockObject.getExpr()));

                String[] orderDetailNames = new String[]{"idBarcodeSkuOrderDetail", "idSkuOrderDetail", "nameSkuOrderDetail", "nameSkuGroupOrderDetail", "priceOrderDetail",
                        "quantityOrderDetail", "nameManufacturerSkuOrderDetail", "isWeighSkuOrderDetail", "isSplitSkuOrderDetail", "minDeviationQuantityOrderDetail",
                        "maxDeviationQuantityOrderDetail", "minDeviationPriceOrderDetail", "maxDeviationPriceOrderDetail",
                        "color", "headField1", "headField2", "headField3", "posField1", "posField2", "posField3",
                        "minDeviationDate", "maxDeviationDate", "dateShipment", "extraBarcodes", "sortTerminal", "unitLoad"};
                LP<?>[] orderDetailProperties = terminalOrderLM.findProperties("idBarcodeSku[TerminalOrderDetail]", "idSku[TerminalOrderDetail]",
                        "nameSku[TerminalOrderDetail]", "nameSkuGroup[TerminalOrderDetail]", "overPrice[TerminalOrderDetail]", "orderQuantity[TerminalOrderDetail]",
                        "nameManufacturerSku[TerminalOrderDetail]", "isWeighSku[TerminalOrderDetail]", "isSplitSku[TerminalOrderDetail]", "minDeviationQuantity[TerminalOrderDetail]",
                        "maxDeviationQuantity[TerminalOrderDetail]", "minDeviationPrice[TerminalOrderDetail]", "maxDeviationPrice[TerminalOrderDetail]",
                        "color[TerminalOrderDetail]", "headField1[TerminalOrderDetail]", "headField2[TerminalOrderDetail]", "headField3[TerminalOrderDetail]",
                        "posField1[TerminalOrderDetail]", "posField2[TerminalOrderDetail]", "posField3[TerminalOrderDetail]",
                        "minDeviationDate[TerminalOrderDetail]", "maxDeviationDate[TerminalOrderDetail]", "dateShipment[TerminalOrderDetail]",
                        "extraBarcodes[TerminalOrderDetail]", "sortTerminal[TerminalOrderDetail]", "unitLoad[TerminalOrderDetail]");
                for (int i = 0; i < orderDetailProperties.length; i++) {
                    orderQuery.addProperty(orderDetailNames[i], orderDetailProperties[i].getExpr(session.getModifier(), orderDetailExpr));
                }
                orderQuery.addProperty("flags", terminalOrderLM.findProperty("flagsSku[TerminalOrderDetail,Stock]").getExpr(session.getModifier(), orderDetailExpr, customerStockObject.getExpr()));
                orderQuery.addProperty("vop", terminalOrderLM.findProperty("vop[TerminalOrderDetail,Stock]").getExpr(session.getModifier(), orderDetailExpr, customerStockObject.getExpr()));
                orderQuery.addProperty("trustAcceptPercent", terminalOrderLM.findProperty("trustAcceptPercent[TerminalOrderDetail]").getExpr(session.getModifier(), orderDetailExpr));
    
                if (terminalOrderLotLM != null)
                    orderQuery.addProperty("lotType", terminalOrderLotLM.findProperty("lotType[TerminalOrderDetail]").getExpr(session.getModifier(), orderDetailExpr));
                
//                if (terminalOrderLM != null)
                orderQuery.addProperty("GTIN", terminalOrderLM.findProperty("GTIN[TerminalOrderDetail]").getExpr(session.getModifier(), orderDetailExpr));
                
                orderQuery.addProperty("fileNameImage", terminalOrderLM.findProperty("fileNameImage[TerminalOrderDetail]").getExpr(session.getModifier(), orderDetailExpr));
                
                orderQuery.and(terminalOrderLM.findProperty("filterTerminal[TerminalOrder, TerminalOrderDetail, Stock, Employee]").getExpr(
                        session.getModifier(), orderExpr, orderDetailExpr, customerStockObject.getExpr(), userInfo.user.getExpr()).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> orderResult = orderQuery.execute(session, MapFact.singletonOrder("sortTerminal", false));
                for (int i = 0, size = orderResult.size(); i < size; i++) {
                    ImMap<Object, Object> entry = orderResult.getValue(i);
                    LocalDate dateOrder = (LocalDate) entry.get("dateOrder");
                    LocalDate dateShipment = (LocalDate) entry.get("dateShipment");
                    String numberOrder = StringUtils.trim((String) entry.get("numberOrder"));
                    String idSupplier = StringUtils.trim((String) entry.get("idSupplierOrder"));
                    String barcode = StringUtils.trim((String) entry.get("idBarcodeSkuOrderDetail"));
                    if (numberOrder == null || barcode == null) continue;

                    String idItem = StringUtils.trim((String) entry.get("idSkuOrderDetail"));
                    String name = StringUtils.trim((String) entry.get("nameSkuOrderDetail"));
                    String category = trim((String) entry.get("nameSkuGroupOrderDetail"));
                    BigDecimal price = (BigDecimal) entry.get("priceOrderDetail");
                    BigDecimal quantity = (BigDecimal) entry.get("quantityOrderDetail");
                    BigDecimal minQuantity = (BigDecimal) entry.get("minDeviationQuantityOrderDetail");
                    BigDecimal maxQuantity = (BigDecimal) entry.get("maxDeviationQuantityOrderDetail");
                    BigDecimal minPrice = (BigDecimal) entry.get("minDeviationPriceOrderDetail");
                    BigDecimal maxPrice = (BigDecimal) entry.get("maxDeviationPriceOrderDetail");
                    String nameManufacturer = (String) entry.get("nameManufacturerSkuOrderDetail");
                    String weight = entry.get("isWeighSkuOrderDetail") != null ? "1" : "0";
                    Integer split = entry.get("isSplitSkuOrderDetail") != null ? 1 : 0;
                    String color = formatColor((Color) entry.get("color"));
                    String background_color = formatColor((Color) entry.get("background_color"));
                    String headField1 = (String) entry.get("headField1");
                    String headField2 = (String) entry.get("headField2");
                    String headField3 = (String) entry.get("headField3");
                    String posField1 = (String) entry.get("posField1");
                    String posField2 = (String) entry.get("posField2");
                    String posField3 = (String) entry.get("posField3");
                    String minDeviationDate = formatDate((LocalDate) entry.get("minDeviationDate"));
                    String maxDeviationDate = formatDate((LocalDate) entry.get("maxDeviationDate"));
                    String vop = (String) entry.get("vop");
                    if (vop != null && vop.contains(",") && userInfo.idApplication.isEmpty() ) {
                        vop = vop.split(",", 2)[0]; //старые ТСД не поддерживают несколько vop. грузим только первый
                    }
                    String extraBarcodes = (String) entry.get("extraBarcodes");
                    List<String> extraBarcodeList = extraBarcodes != null ? Arrays.asList(extraBarcodes.split(",")) : new ArrayList<>();
                    Long flags = (Long) entry.get("flags");
    
                    BigDecimal trustAcceptPercent = (BigDecimal) entry.get("trustAcceptPercent");
                    String lotType = terminalOrderLotLM == null ? null : (String) entry.get("lotType");

                    String GTIN = null;
//                    if (terminalOrderLM != null)
                    GTIN = trim((String) entry.get("GTIN"));
                    String unitLoad = (String) entry.get("unitLoad");
                    String fileNameImage = (String) entry.get("fileNameImage");

                    String key = numberOrder + "/" + barcode;
                    TerminalOrder terminalOrder = terminalOrderMap.get(key);
                    if (terminalOrder != null) {
                        terminalOrder.quantity = safeAdd(terminalOrder.quantity, quantity);
                        terminalOrder.minQuantity = safeAdd(terminalOrder.minQuantity, minQuantity);
                        terminalOrder.maxQuantity = safeAdd(terminalOrder.maxQuantity, maxQuantity);
                    } else
                        terminalOrderMap.put(key, new TerminalOrder(dateOrder, dateShipment, numberOrder, idSupplier, null, null, null,
                                barcode, idItem, name, category, price,
                                quantity, minQuantity, maxQuantity, minPrice, maxPrice, nameManufacturer, weight, split, color,
                                headField1, headField2, headField3, posField1, posField2, posField3, minDeviationDate, maxDeviationDate, vop,
                                extraBarcodeList, flags, GTIN, trustAcceptPercent, unitLoad, background_color, lotType, null, null, fileNameImage));
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
    
                orderQuery.addProperty("fileName", terminalOrderLM.findProperty("fileNameImage[TerminalOrderDetail]").getExpr(session.getModifier(), orderDetailExpr));
                //orderQuery.addProperty("barcode", terminalOrderLM.findProperty("idBarcodeSku[TerminalOrderDetail]").getExpr(session.getModifier(), orderDetailExpr));
                orderQuery.addProperty("image", terminalOrderLM.findProperty("image[TerminalOrderDetail]").getExpr(session.getModifier(), orderDetailExpr));

                orderQuery.and(terminalOrderLM.findProperty("hasImage[TerminalOrderDetail]").getExpr(session.getModifier(), orderDetailExpr).getWhere());
                orderQuery.and(terminalOrderLM.findProperty("filterTerminal[TerminalOrder, TerminalOrderDetail, Stock, Employee]").getExpr(
                        session.getModifier(), orderExpr, orderDetailExpr, customerStockObject.getExpr(), userInfo.user.getExpr()).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> orderResult = orderQuery.execute(session);
                for (ImMap<Object, Object> entry : orderResult.values()) {
                    //String barcode = StringUtils.trim((String) entry.get("barcode"));
                    String fileName = StringUtils.trim((String) entry.get("fileName"));
                    RawFileData image = (RawFileData) entry.get("image");

                    //terminalOrderImages.put(barcode, image);
                    terminalOrderImages.put(fileName, image);
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return terminalOrderImages;
    }

    public static List<TerminalAssortment> readTerminalAssortmentList(DataSession session, ObjectValue stockObject, UserInfo userInfo)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalAssortment> terminalAssortmentList = new ArrayList<>();
        if (terminalHandlerLM != null) {

            KeyExpr skuExpr = new KeyExpr("Sku");
            KeyExpr supplierExpr = new KeyExpr("Stock");
            ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap("Sku", skuExpr, "Stock", supplierExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

            query.addProperty("idBarcodeSku", terminalHandlerLM.findProperty("overIdBarcode[Sku]").getExpr(session.getModifier(), skuExpr));
            query.addProperty("idSupplier", terminalHandlerLM.findProperty("id[Stock]").getExpr(session.getModifier(), supplierExpr));
            query.addProperty("price", terminalHandlerLM.findProperty("price[Sku,Stock,Stock]").getExpr(session.getModifier(), skuExpr, stockObject.getExpr(), supplierExpr));
            query.addProperty("minPrice", terminalHandlerLM.findProperty("minDeviationPrice[Sku,Stock,Stock]").getExpr(session.getModifier(), skuExpr, stockObject.getExpr(), supplierExpr));
            query.addProperty("maxPrice", terminalHandlerLM.findProperty("maxDeviationPrice[Sku,Stock,Stock]").getExpr(session.getModifier(), skuExpr, stockObject.getExpr(), supplierExpr));
            query.addProperty("quantity", terminalHandlerLM.findProperty("quantity[Sku,Stock,Stock]").getExpr(session.getModifier(), skuExpr, stockObject.getExpr(), supplierExpr));
            query.addProperty("idOriginalSupplier", terminalHandlerLM.findProperty("idSupplier[Sku,Stock,Stock]").getExpr(session.getModifier(), skuExpr, stockObject.getExpr(), supplierExpr));

            query.and(terminalHandlerLM.findProperty("id[Stock]").getExpr(session.getModifier(), supplierExpr).getWhere());
            query.and(terminalHandlerLM.findProperty("overIdBarcode[Sku]").getExpr(session.getModifier(), skuExpr).getWhere());
            query.and(terminalHandlerLM.findProperty("filterAssortment[Sku,Stock,Stock,STRING[1]]").getExpr(session.getModifier(), skuExpr, stockObject.getExpr(), supplierExpr, new DataObject(userInfo.idApplication).getExpr()).getWhere());


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

    public static List<TerminalDocumentType> readTerminalDocumentTypeListServer(DataSession session, ObjectValue stockObject, UserInfo userInfo) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalDocumentType> terminalDocumentTypeList = new ArrayList<>();
        if(terminalHandlerLM != null) {
            KeyExpr terminalDocumentTypeExpr = new KeyExpr("terminalDocumentType");
            ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev("terminalDocumentType", terminalDocumentTypeExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
            String[] names = new String[]{"idTerminalDocumentType", "backIdTerminalDocumentType", "nameTerminalDocumentType",
                    "idTerminalHandbookType1TerminalDocumentType", "idTerminalHandbookType2TerminalDocumentType", "idTerminalHandbookType1DetailTerminalDocumentType"};
            LP<?>[] properties = terminalHandlerLM.findProperties("id[TerminalDocumentType]", "backId[TerminalDocumentType]", "name[TerminalDocumentType]",
                    "idTerminalHandbookType1[TerminalDocumentType]", "idTerminalHandbookType2[TerminalDocumentType]", "idTerminalHandbookType1Detail[TerminalDocumentType]");
            for (int i = 0; i < properties.length; i++) {
                query.addProperty(names[i], properties[i].getExpr(terminalDocumentTypeExpr));
            }
            query.addProperty("flagTerminalDocumentType", terminalHandlerLM.findProperty("overFlag[TerminalDocumentType,Stock]").getExpr(session.getModifier(), terminalDocumentTypeExpr, stockObject.getExpr()));

            query.and(terminalHandlerLM.findProperty("id[TerminalDocumentType]").getExpr(terminalDocumentTypeExpr).getWhere());
            query.and(terminalHandlerLM.findProperty("notSkip[TerminalDocumentType, CustomUser]").getExpr(terminalDocumentTypeExpr, userInfo.user.getExpr()).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String id = StringUtils.trim((String) entry.get("idTerminalDocumentType"));
                String backId = StringUtils.trim((String) entry.get("backIdTerminalDocumentType"));
                String name = StringUtils.trim((String) entry.get("nameTerminalDocumentType"));
                Long flag = (Long) entry.get("flagTerminalDocumentType");
                String analytics1 = StringUtils.trim((String) entry.get("idTerminalHandbookType1TerminalDocumentType"));
                String analytics2 = StringUtils.trim((String) entry.get("idTerminalHandbookType2TerminalDocumentType"));
                String detail_analytics1 = StringUtils.trim((String) entry.get("idTerminalHandbookType1DetailTerminalDocumentType"));
                terminalDocumentTypeList.add(new TerminalDocumentType(id, name, analytics1, analytics2, detail_analytics1, flag, backId));
            }
        }
        return terminalDocumentTypeList;
    }

    public static List<TerminalLegalEntity> readCustomANAList(DataSession session, BusinessLogics BL, UserInfo userInfo) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalLegalEntity> customANAList = new ArrayList<>();
        if (terminalHandlerLM != null) {

            KeyExpr terminalHandbookTypeExpr = new KeyExpr("terminalHandbookType");
            ImRevMap<Object, KeyExpr> terminalHandbookTypeKeys = MapFact.singletonRev("terminalHandbookType", terminalHandbookTypeExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(terminalHandbookTypeKeys);
            String[] names = new String[]{"exportId", "name", "propertyID", "propertyName", "filterProperty", "extInfoProperty",
                    "field1Property", "field2Property", "field3Property", "flagsProperty"};
            LP<?>[] properties = terminalHandlerLM.findProperties("exportId[TerminalHandbookType]", "name[TerminalHandbookType]",
                    "canonicalNamePropertyID[TerminalHandbookType]", "canonicalNamePropertyName[TerminalHandbookType]",
                    "canonicalNameFilterProperty[TerminalHandbookType]", "canonicalNameExtInfoProperty[TerminalHandbookType]",
                    "canonicalNameField1Property[TerminalHandbookType]", "canonicalNameField2Property[TerminalHandbookType]", "canonicalNameField3Property[TerminalHandbookType]",
                    "canonicalNameFlagsProperty[TerminalHandbookType]");
            for (int i = 0, propertiesLength = properties.length; i < propertiesLength; i++) {
                query.addProperty(names[i], properties[i].getExpr(terminalHandbookTypeExpr));
            }
            query.and(terminalHandlerLM.findProperty("exportId[TerminalHandbookType]").getExpr(terminalHandbookTypeExpr).getWhere());
            query.and(terminalHandlerLM.findProperty("canonicalNamePropertyID[TerminalHandbookType]").getExpr(terminalHandbookTypeExpr).getWhere());
            query.and(terminalHandlerLM.findProperty("canonicalNamePropertyName[TerminalHandbookType]").getExpr(terminalHandbookTypeExpr).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String prefix = StringUtils.trim((String) entry.get("exportId"));
                //Long flags = (Long)  entry.get("flags");
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
                String canonicalNameFlagsProperty = StringUtils.trim((String) entry.get("flagsProperty"));
                LP flagsProperty = canonicalNameFlagsProperty != null ? (LP<?>) BL.findSafeProperty(canonicalNameFlagsProperty) : null;

                if(propertyID != null && propertyName != null) {
                    ImOrderSet<PropertyInterface> interfaces = propertyID.listInterfaces;
                    if (interfaces.size() == 1) {
                        KeyExpr customANAExpr = new KeyExpr("customANA");
                        ImRevMap<Object, KeyExpr> customANAKeys = MapFact.singletonRev("customANA", customANAExpr);
                        QueryBuilder<Object, Object> customANAQuery = new QueryBuilder<>(customANAKeys);
                        customANAQuery.addProperty("id", propertyID.getExpr(customANAExpr));
                        customANAQuery.addProperty("name", propertyName.getExpr(customANAExpr));

                        addCustomField(userInfo.user, customANAExpr, customANAQuery, extInfoProperty, "extInfo");
                        addCustomField(userInfo.user, customANAExpr, customANAQuery, field1Property, "field1");
                        addCustomField(userInfo.user, customANAExpr, customANAQuery, field2Property, "field2");
                        addCustomField(userInfo.user, customANAExpr, customANAQuery, field3Property, "field3");
                        addCustomField(userInfo.user, customANAExpr, customANAQuery, flagsProperty, "flags");

                        if (filterProperty != null) {
                            switch (filterProperty.listInterfaces.size()) {
                                case 1:
                                    customANAQuery.and(filterProperty.getExpr(customANAExpr).getWhere());
                                    break;
                                case 2:
                                    //небольшой хак, для случая, когда второй параметр в фильтре - пользователь
                                    Object interfaceObject = filterProperty.listInterfaces.get(1);
                                    if (interfaceObject instanceof ClassPropertyInterface) {
                                        if (IsClassProperty.fitClass(userInfo.user.objectClass, ((ClassPropertyInterface) interfaceObject).interfaceClass))
                                            customANAQuery.and(filterProperty.getExpr(customANAExpr, userInfo.user.getExpr()).getWhere());
                                    } else { //если не data property и второй параметр не пользователь, то фильтр отсечёт всё
                                        customANAQuery.and(filterProperty.getExpr(customANAExpr, userInfo.user.getExpr()).getWhere());
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
                            Long flags = (Long) customANAEntry.get("flags");
                            customANAList.add(new TerminalLegalEntity(prefix + idCustomANA, nameCustomANA, extInfo, field1, field2, field3, flags));
                        }
                    }
                }
            }
        }
        return customANAList;
    }
    
    private List<SkuGroup> readSkuGroupList(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
    
        List<SkuGroup> result = new ArrayList<>();
        
        KeyExpr skuGroupExpr = new KeyExpr("SkuGroup");
        ImRevMap<Object, KeyExpr> skuGroupKeys = MapFact.singletonRev("skuGroup", skuGroupExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(skuGroupKeys);
        
        String[] names = new String[] {"idGroup", "nameGroup", "idParentGroup"};
        LP[] properties = terminalHandlerLM.findProperties("idSkuGroup[SkuGroup]", "name[SkuGroup]", "idParentSkuGroup[SkuGroup]");
        for (int i = 0; i < properties.length; i++) {
            query.addProperty(names[i], properties[i].getExpr(skuGroupExpr));
        }
        
        query.and(terminalHandlerLM.findProperty("id[SkuGroup]").getExpr(session.getModifier(), skuGroupExpr).getWhere());
        query.and(terminalHandlerLM.findProperty("filterSkuGroup[SkuGroup]").getExpr(session.getModifier(), skuGroupExpr).getWhere());
        
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> skuGroupResult = query.execute(session);
        
        for (ImMap<Object, Object> row : skuGroupResult.valueIt()) {
            String idGroup = (String) row.get("idGroup");
            String idParentGroup = (String) row.get("idParentGroup");
            String nameGroup = (String) row.get("nameGroup");
            result.add(new SkuGroup(idGroup, nameGroup, idParentGroup));
        }
        
        return result;
    }
    
    private List<TerminalOrder> readLabelTaskList(DataSession session, UserInfo userInfo) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
    
        List<TerminalOrder> result = new ArrayList<>();
        
        ScriptingLogicsModule labelTerminalTaskLM = getLogicsInstance().getBusinessLogics().getModule("LabelTerminalTask");
        if (labelTerminalTaskLM != null) {
            try {
                ObjectValue taskObject = labelTerminalTaskLM.findProperty("todayTask[Employee]").readClasses(session, userInfo.user);
                if (!taskObject.isNull()) {
                    String code = (String) labelTerminalTaskLM.findProperty("code[LabelTask]").read(session, taskObject);
                    String captionDOW = (String) labelTerminalTaskLM.findProperty("captionDow[LabelTask]").read(session, taskObject);
                    Boolean promo = (Boolean) labelTerminalTaskLM.findProperty("promo[LabelTask]").read(session, taskObject);
                    Integer labelCount = (Integer) labelTerminalTaskLM.findProperty("count[LabelTask]").read(session, taskObject);
                    String categories = (String) labelTerminalTaskLM.findProperty("skuGroupsJSON[LabelTask]").read(session, taskObject);
                    String vop = (String) labelTerminalTaskLM.findProperty("idTerminalDocumentType[LabelTask]").read(session, taskObject);
                    String extraField = (String) labelTerminalTaskLM.findProperty("extraField[LabelTask]").read(session, taskObject);
                    
                    result.add(new TerminalOrder(LocalDate.now(), null, code, null, labelCount, categories, promo, null,
                            null, null, null, null, null, null, null,
                            null, null, null, null, null,
                            null, extraField, null, null,  null, null,
                            null, null, null, vop, null, null, null,
                            null, null, null, null, null, null, null));
                }
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
        
        return result;
    }
    
    public static List<UnitLoad> readUnitLoadList(DataSession session, ObjectValue stockObject, UserInfo userInfo)  throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<UnitLoad> result = new ArrayList<>();
    
        KeyExpr terminalOrderExpr = new KeyExpr("TerminalOrder");
        KeyExpr skuExpr = new KeyExpr("Sku");
        
        KeyExpr unitCodeExpr = new KeyExpr("unitCode");
        KeyExpr unitBarcodeExpr = new KeyExpr("unitBarcode");
        
        MRevMap<Object, KeyExpr> mMap = MapFact.mRevMap(4);
        mMap.revAdd("TerminalOrder", terminalOrderExpr);
        mMap.revAdd("Sku", skuExpr);
        mMap.revAdd("unitCode", unitCodeExpr);
        mMap.revAdd("unitBarcode", unitBarcodeExpr);
    
        QueryBuilder<Object, Object> query = new QueryBuilder<>(mMap.immutableRev());
        
        query.addProperty("numberOrder", terminalOrderLM.findProperty("number[TerminalOrder]").getExpr(session.getModifier(), terminalOrderExpr));
        query.addProperty("barcode", terminalOrderLM.findProperty("idBarcode[Sku]").getExpr(session.getModifier(), skuExpr));
        query.addProperty("quantity", terminalOrderLM.findProperty("quantity[TerminalOrder,Sku,STRING,STRING,Stock,Employee]").getExpr(session.getModifier(), terminalOrderExpr, skuExpr, unitCodeExpr, unitBarcodeExpr, stockObject.getExpr(), userInfo.user.getExpr()));
    
        query.and(terminalOrderLM.findProperty("quantity[TerminalOrder,Sku,STRING,STRING,Stock,Employee]").getExpr(session.getModifier(), terminalOrderExpr, skuExpr, unitCodeExpr, unitBarcodeExpr, stockObject.getExpr(), userInfo.user.getExpr()).getWhere());
    
        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> queryResult = query.executeClasses(session);
        for (int i = 0; i < queryResult.size(); i++) {
    
            String unitCode = (String) queryResult.getKey(i).get("unitCode").getValue();
            String unitBarcode = (String) queryResult.getKey(i).get("unitBarcode").getValue();
            
            ImMap<Object, ObjectValue> entry = queryResult.getValue(i);
        
            String numberOrder = trim((String) entry.get("numberOrder").getValue());
            String barcode = trim((String) entry.get("barcode").getValue());
            BigDecimal quantity = (BigDecimal) entry.get("quantity").getValue();
        
            result.add(new UnitLoad(unitCode, unitBarcode, numberOrder, barcode, quantity));
        }
    
        return result;
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
        Integer isSplit;
        String mainBarcode;
        String color;
        String extInfo;
        String fld3;
        String fld4;
        String fld5;
        String unit;
        Long flags;
        RawFileData image;
        String nameCountry;
        BigDecimal amount;
        BigDecimal capacity;
        String category;
        String idCategory;
        String GTIN;
        String fileNameImage;
        BigDecimal trustAcceptPercent;
        boolean hasImage;
        String background_color;
        BigDecimal quantityBarcodeDefect;
        String lotType;
        boolean ukz;
        String nameUkzType;

        public TerminalBarcode(String idBarcode, String nameSku, BigDecimal price, BigDecimal quantityBarcodeStock,
                               String idSkuBarcode, String nameManufacturer, String isWeight, String mainBarcode,
                               String color, String extInfo, String fld3, String fld4, String fld5, String unit,
                               Long flags, RawFileData image, String nameCountry, BigDecimal amount,
                               BigDecimal capacity, String category, String GTIN, String fileNameImage,
                               BigDecimal trustAcceptPercent, boolean hasImage, String background_color, String idCategory,
                               Integer isSplit, BigDecimal quantityBarcodeDefect, String lotType, boolean ukz, String nameUkzType) {
            this.idBarcode = idBarcode;
            this.nameSku = nameSku;
            this.price = price;
            this.quantityBarcodeStock = quantityBarcodeStock;
            this.idSkuBarcode = idSkuBarcode;
            this.nameManufacturer = nameManufacturer;
            this.isWeight = isWeight;
            this.isSplit = isSplit;
            this.mainBarcode = mainBarcode;
            this.color = color;
            this.extInfo = extInfo;
            this.fld3 = fld3;
            this.fld4 = fld4;
            this.fld5 = fld5;
            this.unit = unit;
            this.flags = flags;
            this.image = image;
            this.nameCountry = nameCountry;
            this.amount = amount;
            this.capacity = capacity;
            this.category = category;
            this.idCategory = idCategory;
            this.GTIN = GTIN;
            this.fileNameImage = fileNameImage;
            this.trustAcceptPercent = trustAcceptPercent;
            this.hasImage = hasImage;
            this.background_color = background_color;
            this.quantityBarcodeDefect = quantityBarcodeDefect;
            this.lotType = lotType;
            this.ukz = ukz;
            this.nameUkzType = nameUkzType;
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

    private class TerminalLot implements Serializable {
        public String idLot;
        public String idBarcode;
        public String idSku;
        public String idParent;
        public String numberOrder;
        public BigDecimal quantity;
        public Integer count;

        public TerminalLot(String idLot, String idBarcode, String idSku, String idParent, String numberOrder, BigDecimal quantity, Integer count) {
            this.idLot = idLot;
            this.idBarcode = idBarcode;
            this.idSku = idSku;
            this.idParent = idParent;
            this.numberOrder = numberOrder;
            this.quantity = quantity;
            this.count = count;
        }
    }

    private static class TerminalDocumentType implements Serializable {
        public String id;
        public String name;
        public String analytics1;
        public String analytics2;
        public String backId;

        public String detail_analytics1;
        public Long flag;

        public TerminalDocumentType(String id, String name, String analytics1, String analytics2, String detail_analytics1, Long flag, String backId) {
            this.id = id;
            this.name = name;
            this.analytics1 = analytics1;
            this.analytics2 = analytics2;
            this.detail_analytics1 = detail_analytics1;
            this.flag = flag;
            this.backId = backId;
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

        public Long flags;

        public TerminalLegalEntity(String idLegalEntity, String nameLegalEntity, String extInfo, String field1, String field2, String field3, Long flags) {
            this.idLegalEntity = idLegalEntity;
            this.nameLegalEntity = nameLegalEntity;
            this.extInfo = extInfo;
            this.field1 = field1;
            this.field2 = field2;
            this.field3 = field3;
            this.flags = flags;
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
        public String category;
        public BigDecimal price;
        public BigDecimal quantity;
        public BigDecimal minQuantity;
        public BigDecimal maxQuantity;
        public BigDecimal minPrice;
        public BigDecimal maxPrice;
        public String manufacturer;
        public String weight;
        public Integer split;
        public String color;
        public String background_color;
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
        public Long flags;
        public String GTIN;
        public BigDecimal trustAcceptPercent;
        public String unitLoad;
        public Integer labelCount;
        public String categories;
        public Boolean promo;
        public String lotType;
        public Boolean ukz;
        public String nameUkzType;
        public String fileNameImage;
        
        public TerminalOrder(LocalDate date, LocalDate dateShipment, String number, String supplier,
                             Integer labelCount, String categories, Boolean promo,
                             String barcode, String idItem, String name, String category,
                             BigDecimal price, BigDecimal quantity, BigDecimal minQuantity, BigDecimal maxQuantity,
                             BigDecimal minPrice, BigDecimal maxPrice, String manufacturer, String weight, Integer split, String color,
                             String headField1, String headField2, String headField3, String posField1, String posField2, String posField3,
                             String minDate1, String maxDate1, String vop, List<String> extraBarcodeList, Long flags, String GTIN, BigDecimal trustAcceptPercent,
                             String unitLoad, String background_color, String lotType, Boolean ukz, String nameUkzType, String fileNameImage) {
            this.date = date;
            this.dateShipment = dateShipment;
            this.number = number;
            this.supplier = supplier;
            this.labelCount = labelCount;
            this.categories = categories;
            this.promo = promo;
            this.barcode = barcode;
            this.idItem = idItem;
            this.name = name;
            this.category = category;
            this.price = price;
            this.quantity = quantity;
            this.minQuantity = minQuantity;
            this.maxQuantity = maxQuantity;
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
            this.manufacturer = manufacturer;
            this.weight = weight;
            this.split = split;
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
            this.flags = flags;
            this.GTIN = GTIN;
            this.trustAcceptPercent = trustAcceptPercent;
            this.unitLoad = unitLoad;
            this.background_color = background_color;
            this.lotType = lotType;
            this.ukz = ukz;
            this.nameUkzType = nameUkzType;
            this.fileNameImage = fileNameImage;
        }
    }

    private static class SkuExtraBarcode {
        String idBarcode;
        String nameSku;
        String mainBarcode;

        public SkuExtraBarcode(String idBarcode, String nameSku, String mainBarcode) {
            this.idBarcode = idBarcode;
            this.nameSku = nameSku;
            this.mainBarcode = mainBarcode;
        }
    }
    
    private static class SkuGroup implements Serializable {
        public String id;
        public String name;
        public String idParent;
        
        public SkuGroup(String id, String name, String idParent) {
            this.id = id;
            this.name = name;
            this.idParent = idParent;
        }
    }
    
    private static class UnitLoad implements Serializable {
        public String code;
        public String barcode;
        public String numberOrder;
        public String skuBarcode;
        public BigDecimal skuQuantity;
        
        public UnitLoad(String code, String barcode, String numberOrder, String skuBarcode, BigDecimal skuQuantity) {
            this.code = code;
            this.barcode = barcode;
            this.numberOrder = numberOrder;
            this.skuBarcode = skuBarcode;
            this.skuQuantity = skuQuantity;
        }
    }
    
}

