package lsfusion.erp.integration.universal;

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import lsfusion.base.IOUtils;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.integration.*;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

public class ImportProductionOrderActionProperty extends ImportDocumentActionProperty {
    private final ClassPropertyInterface orderInterface;

    public ImportProductionOrderActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClass("Production.Order"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        orderInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataSession session = context.getSession();

            DataObject orderObject = context.getDataKeyValue(orderInterface);

            ObjectValue importTypeObject = findProperty("importTypeOrder").readClasses(session, orderObject);

            if (!(importTypeObject instanceof NullValue)) {

                ObjectValue operationObject = findProperty("autoImportOperationImportType").readClasses(session, (DataObject) importTypeObject);

                Map<String, ImportColumnDetail> importColumns = readImportColumns(session, importTypeObject).get(0);
                ImportDocumentSettings settings = readImportDocumentSettings(session, importTypeObject);
                String fileExtension = settings.getFileExtension();

                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, fileExtension + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                        for (byte[] file : fileList) {

                            makeImport(context.getBL(), session, orderObject, importColumns, file, settings, fileExtension, operationObject);

                            session.apply(context);
                            
                            findAction("formRefresh").execute(context);
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        } catch (xBaseJException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (BiffException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        } catch (UniversalImportException e) {
            e.printStackTrace();
            context.requestUserInteraction(new MessageClientAction(e.getMessage(), e.getTitle()));
        }
    }

    public boolean makeImport(BusinessLogics BL, DataSession session, DataObject orderObject, Map<String, ImportColumnDetail> importColumns,
                              byte[] file, ImportDocumentSettings settings, String fileExtension, ObjectValue operationObject)
            throws ParseException, IOException, SQLException, BiffException, xBaseJException, ScriptingErrorLog.SemanticErrorException, UniversalImportException, SQLHandledException {

        List<ProductionOrderDetail> orderDetailsList = importOrdersFromFile(orderObject, importColumns, file, fileExtension, settings.getStartRow(), settings.isPosted(), settings.getSeparator());

        boolean importResult = importOrders(orderDetailsList, BL, session, orderObject, importColumns, operationObject);

        findAction("formRefresh").execute(session);

        return importResult;
    }

    public boolean importOrders(List<ProductionOrderDetail> orderDetailsList, BusinessLogics BL, DataSession session,
                                DataObject orderObject, Map<String, ImportColumnDetail> importColumns, ObjectValue operationObject)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException, SQLHandledException {

        if (orderDetailsList != null && (orderObject !=null || showField(orderDetailsList, "idOrder"))) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(orderDetailsList.size());

            ImportKey<?> orderKey = null;
            ImportField idOrderField = null;
            if (orderObject == null && showField(orderDetailsList, "idOrder")) {
                idOrderField = new ImportField(findProperty("Production.idOrder"));
                orderKey = new ImportKey((CustomClass) findClass("Production.Order"),
                        findProperty("Production.orderId").getMapping(idOrderField));
                keys.add(orderKey);
                props.add(new ImportProperty(idOrderField, findProperty("Production.idOrder").getMapping(orderKey)));
                fields.add(idOrderField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).idOrder);
            }
            
            if (showField(orderDetailsList, "isPosted")) {
                if(orderObject == null)
                    addDataField(props, fields, importColumns, findProperty("Production.isPostedOrder"), "isPosted", orderKey);
                else
                    addDataField(props, fields, importColumns, findProperty("Production.isPostedOrder"), "isPosted", orderObject);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).isPosted);
            }
            
            if (showField(orderDetailsList, "numberOrder")) {
                if(orderObject == null)
                    addDataField(props, fields, importColumns, findProperty("Production.numberOrder"), "numberOrder", orderKey);
                else
                    addDataField(props, fields, importColumns, findProperty("Production.numberOrder"), "numberOrder", orderObject);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).numberOrder);
            }

            if (showField(orderDetailsList, "dateOrder")) {
                if(orderObject == null)
                    addDataField(props, fields, importColumns, findProperty("Production.dateOrder"), "dateOrder", orderKey);
                else
                    addDataField(props, fields, importColumns, findProperty("Production.dateOrder"), "dateOrder", orderObject);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).dateOrder);
            }

            if (showField(orderDetailsList, "idProductsStock")) {
                ImportField idProductsStockOrderField = new ImportField(findProperty("idStock"));
                ImportKey<?> productsStockOrderKey = new ImportKey((CustomClass) findClass("Stock"),
                        findProperty("stockId").getMapping(idProductsStockOrderField));
                productsStockOrderKey.skipKey = true;
                keys.add(productsStockOrderKey);
                props.add(new ImportProperty(idProductsStockOrderField, findProperty("Production.productsStockOrder").getMapping(orderObject == null ? orderKey : orderObject),
                        object(findClass("Stock")).getMapping(productsStockOrderKey)));
                fields.add(idProductsStockOrderField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).idProductsStock);
            }

            ImportField idSkuField = new ImportField(findProperty("idSku"));
            ImportKey<?> skuKey = new ImportKey((CustomClass) findClass("Sku"),
                    findProperty("skuId").getMapping(idSkuField));
            keys.add(skuKey);
            fields.add(idSkuField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).idItem);

            ImportField idBOMField = new ImportField(findProperty("idBOM"));
            ImportKey<?> BOMKey = new ImportKey((ConcreteCustomClass) findClass("BOM"),
                    findProperty("BOMId").getMapping(idBOMField));
            keys.add(BOMKey);
            props.add(new ImportProperty(idBOMField, findProperty("idBOM").getMapping(BOMKey)));
            props.add(new ImportProperty(idBOMField, findProperty("numberBOM").getMapping(BOMKey)));
            fields.add(idBOMField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).idItem);
            
            ImportField extIdProductField = new ImportField(findProperty("extIdProduct"));
            ImportKey<?> productKey = new ImportKey((CustomClass) findClass("Product"),
                    findProperty("extProductId").getMapping(extIdProductField));
            keys.add(productKey);
            props.add(new ImportProperty(extIdProductField, findProperty("extIdProduct").getMapping(productKey)));
            props.add(new ImportProperty(idBOMField, findProperty("BOMProduct").getMapping(productKey),
                    object(findClass("BOM")).getMapping(BOMKey)));
            props.add(new ImportProperty(idSkuField, findProperty("skuProduct").getMapping(productKey), 
                    object(findClass("Sku")).getMapping(skuKey)));
            fields.add(extIdProductField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).idProduct);

            ImportField idProductDetailField = new ImportField(findProperty("Production.idProductDetail"));
            ImportKey<?> productDetailKey = new ImportKey((CustomClass) findClass("Production.ProductDetail"),
                    findProperty("Production.productDetailId").getMapping(idProductDetailField));
            keys.add(productDetailKey);
            props.add(new ImportProperty(idProductDetailField, findProperty("Production.idProductDetail").getMapping(productDetailKey)));
            if(orderObject == null)
                props.add(new ImportProperty(idOrderField, findProperty("Production.orderProductDetail").getMapping(productDetailKey),
                        object(findClass("Production.Order")).getMapping(orderKey)));
            else
                props.add(new ImportProperty(orderObject, findProperty("Production.orderProductDetail").getMapping(productDetailKey)));
            props.add(new ImportProperty(idSkuField, findProperty("Production.productProductDetail").getMapping(productDetailKey),
                    object(findClass("Product")).getMapping(productKey)));
            props.add(new ImportProperty(idSkuField, findProperty("Production.skuProductDetail").getMapping(productDetailKey),
                    object(findClass("Sku")).getMapping(skuKey)));
            fields.add(idProductDetailField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).idProductDetail);

            if (operationObject instanceof DataObject)
                props.add(new ImportProperty((DataObject) operationObject, findProperty("Production.operationOrder").getMapping(orderObject == null ? orderKey : orderObject)));

            if (showField(orderDetailsList, "dataIndex")) {
                ImportField dataIndexOrderDetailField = new ImportField(findProperty("Production.dataIndexProductDetail"));
                props.add(new ImportProperty(dataIndexOrderDetailField, findProperty("Production.dataIndexProductDetail").getMapping(productDetailKey)));
                fields.add(dataIndexOrderDetailField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).dataIndex);
            }
            
            if (showField(orderDetailsList, "outputQuantity")) {
                addDataField(props, fields, importColumns, findProperty("Production.outputQuantityProductDetail"), "outputQuantity", productDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).outputQuantity);
            }

            if (showField(orderDetailsList, "price")) {
                addDataField(props, fields, importColumns, findProperty("Production.priceProductDetail"), "price", productDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).price);
            }
            
            if (showField(orderDetailsList, "componentsPrice")) {
                addDataField(props, fields, importColumns, findProperty("Production.componentsPriceProductDetail"), "componentsPrice", productDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).componentsPrice);
            }

            if (showField(orderDetailsList, "valueVAT")) {
                addDataField(props, fields, importColumns, findProperty("Production.valueVATProductDetail"), "valueVAT", productDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).valueVAT);
            }

            if (showField(orderDetailsList, "markup")) {
                addDataField(props, fields, importColumns, findProperty("Production.markupProductDetail"), "markup", productDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).markup);
            }

            if (showField(orderDetailsList, "sum")) {
                addDataField(props, fields, importColumns, findProperty("Production.sumProductDetail"), "sum", productDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).sum);
            }

            ImportTable table = new ImportTable(fields, data);

            session.pushVolatileStats("POA_IO");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            String result = session.applyMessage(BL);
            session.popVolatileStats();

            return result == null;
        }
        return false;
    }

    public List<ProductionOrderDetail> importOrdersFromFile(DataObject orderObject, Map<String, ImportColumnDetail> importColumns, 
                                                                  byte[] file, String fileExtension, Integer startRow, Boolean isPosted, String separator)
            throws ParseException, UniversalImportException, IOException, SQLException, xBaseJException, ScriptingErrorLog.SemanticErrorException, BiffException, SQLHandledException {

        List<ProductionOrderDetail> orderDetailsList;

        if (fileExtension.equals("DBF"))
            orderDetailsList = importOrdersFromDBF(file, importColumns, startRow, isPosted, orderObject);
        else if (fileExtension.equals("XLS"))
            orderDetailsList = importOrdersFromXLS(file, importColumns, startRow, isPosted, orderObject);
        else if (fileExtension.equals("XLSX"))
            orderDetailsList = importOrdersFromXLSX(file, importColumns, startRow, isPosted, orderObject);
        else if (fileExtension.equals("CSV") || fileExtension.equals("TXT"))
            orderDetailsList = importOrdersFromCSV(file, importColumns, startRow, isPosted, separator, orderObject);
        else
            orderDetailsList = null;

        return orderDetailsList;
    }

    private List<ProductionOrderDetail> importOrdersFromXLS(byte[] importFile, Map<String, ImportColumnDetail> importColumns, Integer startRow, Boolean isPosted, DataObject orderObject)
            throws IOException, BiffException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<ProductionOrderDetail> result = new ArrayList<ProductionOrderDetail>();

        WorkbookSettings ws = new WorkbookSettings();
        ws.setEncoding("cp1251");
        Workbook wb = Workbook.getWorkbook(new ByteArrayInputStream(importFile), ws);
        Sheet sheet = wb.getSheet(0);

        for (int i = startRow - 1; i < sheet.getRows(); i++) {
            String numberOrder = getXLSFieldValue(sheet, i, importColumns.get("numberDocument"));
            String idOrder = getXLSFieldValue(sheet, i, importColumns.get("idDocument"), numberOrder);
            Date dateOrder = getXLSDateFieldValue(sheet, i, importColumns.get("dateDocument"));
            String idProductsStock = getXLSFieldValue(sheet, i, importColumns.get("idProductsStock"));
            String idOrderDetail = makeIdOrderDetail(orderObject, numberOrder, i);
            String dataIndexString = getXLSFieldValue(sheet, i, importColumns.get("dataIndex"));
            Integer dataIndex = dataIndexString == null ? null : Integer.parseInt(dataIndexString);
            String idItem = getXLSFieldValue(sheet, i, importColumns.get("idItem"));
            String idProduct = getXLSFieldValue(sheet, i, importColumns.get("idProduct"));
            BigDecimal outputQuantity = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("outputQuantity"));
            BigDecimal price = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("price"));
            BigDecimal componentsPrice = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("componentsPrice"));
            BigDecimal valueVAT = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("valueVAT"));
            BigDecimal markup = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("markup"));
            BigDecimal sum = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("sum"));
            
            result.add(new ProductionOrderDetail(isPosted, idOrder, numberOrder, dateOrder,
                    idProductsStock, idOrderDetail, dataIndex, idItem, idProduct, outputQuantity, price, componentsPrice,
                    valueVAT, markup, sum));
        }

        return result;
    }

    private List<ProductionOrderDetail> importOrdersFromCSV(byte[] importFile, Map<String, ImportColumnDetail> importColumns, Integer startRow, Boolean isPosted, String separator, DataObject orderObject)
            throws UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, IOException, SQLHandledException {

        List<ProductionOrderDetail> result = new ArrayList<ProductionOrderDetail>();
        
        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(importFile), "utf-8"));
        String line;
        
        List<String[]> valuesList = new ArrayList<String[]>();
        while ((line = br.readLine()) != null) {
            valuesList.add(line.split(separator));              
        }

        for (int count = startRow; count <= valuesList.size(); count++) {

            String numberOrder = getCSVFieldValue(valuesList, importColumns.get("numberDocument"), count);
            String idOrder = getCSVFieldValue(valuesList, importColumns.get("idDocument"), count, numberOrder);
            Date dateOrder = getCSVDateFieldValue(valuesList, importColumns.get("dateDocument"), count);
            String idProductsStock = getCSVFieldValue(valuesList, importColumns.get("idProductsStock"), count);
            String idOrderDetail = makeIdOrderDetail(orderObject, numberOrder, count);
            String dataIndexString = getCSVFieldValue(valuesList, importColumns.get("dataIndex"), count);
            Integer dataIndex = dataIndexString == null ? null : Integer.parseInt(dataIndexString);
            String idItem = getCSVFieldValue(valuesList, importColumns.get("idItem"), count);
            String idProduct = getCSVFieldValue(valuesList, importColumns.get("idProduct"), count);
            BigDecimal outputQuantity = getCSVBigDecimalFieldValue(valuesList, importColumns.get("outputQuantity"), count);
            BigDecimal price = getCSVBigDecimalFieldValue(valuesList, importColumns.get("price"), count);
            BigDecimal componentsPrice = getCSVBigDecimalFieldValue(valuesList, importColumns.get("componentsPrice"), count);
            BigDecimal valueVAT = getCSVBigDecimalFieldValue(valuesList, importColumns.get("valueVAT"), count);
            BigDecimal markup = getCSVBigDecimalFieldValue(valuesList, importColumns.get("markup"), count);
            BigDecimal sum = getCSVBigDecimalFieldValue(valuesList, importColumns.get("sum"), count);
           
            result.add(new ProductionOrderDetail(isPosted, idOrder, numberOrder, dateOrder,
                    idProductsStock, idOrderDetail, dataIndex, idItem, idProduct, outputQuantity, price, componentsPrice,
                    valueVAT, markup, sum));
        }

        return result;
    }

    private List<ProductionOrderDetail> importOrdersFromXLSX(byte[] importFile, Map<String, ImportColumnDetail> importColumns, Integer startRow, Boolean isPosted, DataObject orderObject)
            throws IOException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<ProductionOrderDetail> result = new ArrayList<ProductionOrderDetail>();
        
        XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(importFile));
        XSSFSheet sheet = Wb.getSheetAt(0);

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {

            String numberOrder = getXLSXFieldValue(sheet, i, importColumns.get("numberDocument"));
            String idOrder = getXLSXFieldValue(sheet, i, importColumns.get("idDocument"), numberOrder);
            Date dateOrder = getXLSXDateFieldValue(sheet, i, importColumns.get("dateDocument"));
            String idProductsStock = getXLSXFieldValue(sheet, i, importColumns.get("idProductsStock"));
            String idOrderDetail = makeIdOrderDetail(orderObject, numberOrder, i);
            String dataIndexString = getXLSXFieldValue(sheet, i, importColumns.get("dataIndex"));
            Integer dataIndex = dataIndexString == null ? null : Integer.parseInt(dataIndexString);
            String idItem = getXLSXFieldValue(sheet, i, importColumns.get("idItem"));
            String idProduct = getXLSXFieldValue(sheet, i, importColumns.get("idProduct"));
            BigDecimal outputQuantity = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("outputQuantity"));
            BigDecimal price = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("price"));
            BigDecimal componentsPrice = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("componentsPrice"));
            BigDecimal valueVAT = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("valueVAT"));
            BigDecimal markup = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("markup"));
            BigDecimal sum = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("sum"));
            
            result.add(new ProductionOrderDetail(isPosted, idOrder, numberOrder, dateOrder,
                    idProductsStock, idOrderDetail, dataIndex, idItem, idProduct, outputQuantity, price, componentsPrice,
                    valueVAT, markup, sum));
        }

        return result;
    }

    private List<ProductionOrderDetail> importOrdersFromDBF(byte[] importFile, Map<String, ImportColumnDetail> importColumns, Integer startRow, Boolean isPosted, DataObject orderObject)
            throws IOException, xBaseJException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, UniversalImportException, SQLHandledException {

        List<ProductionOrderDetail> result = new ArrayList<ProductionOrderDetail>();

        File tempFile = File.createTempFile("productionOrder", ".dbf");
        IOUtils.putFileBytes(tempFile, importFile);

        DBF file = new DBF(tempFile.getPath());
        String charset = getDBFCharset(tempFile);

        int totalRecordCount = file.getRecordCount();

        for (int i = startRow - 1; i < totalRecordCount; i++) {

            file.read();

            String numberOrder = getDBFFieldValue(file, importColumns.get("numberDocument"), i, charset);
            String idOrder = getDBFFieldValue(file, importColumns.get("idDocument"), i, charset, numberOrder);
            Date dateOrder = getDBFDateFieldValue(file, importColumns.get("dateDocument"), i, charset);
            String idProductsStock = getDBFFieldValue(file, importColumns.get("idProductsStock"), i, charset);
            String idOrderDetail = makeIdOrderDetail(orderObject, numberOrder, i);
            String dataIndexString = getDBFFieldValue(file, importColumns.get("dataIndex"), i, charset);
            Integer dataIndex = dataIndexString == null ? null : Integer.parseInt(dataIndexString);
            String idItem = getDBFFieldValue(file, importColumns.get("idItem"), i, charset);
            String idProduct = getDBFFieldValue(file, importColumns.get("idProduct"), i, charset);
            BigDecimal outputQuantity = getDBFBigDecimalFieldValue(file, importColumns.get("outputQuantity"), i, charset);
            BigDecimal price = getDBFBigDecimalFieldValue(file, importColumns.get("price"), i, charset);
            BigDecimal componentsPrice = getDBFBigDecimalFieldValue(file, importColumns.get("componentsPrice"), i, charset);
            BigDecimal valueVAT = getDBFBigDecimalFieldValue(file, importColumns.get("valueVAT"), i, charset);
            BigDecimal markup = getDBFBigDecimalFieldValue(file, importColumns.get("markup"), i, charset);
            BigDecimal sum = getDBFBigDecimalFieldValue(file, importColumns.get("sum"), i, charset);
            
            result.add(new ProductionOrderDetail(isPosted, idOrder, numberOrder, dateOrder,
                    idProductsStock, idOrderDetail, dataIndex, idItem, idProduct, outputQuantity, price, componentsPrice,
                    valueVAT, markup, sum));
        }
        file.close();
        tempFile.delete();

        return result;
    }
    
    private String makeIdOrderDetail(DataObject orderObject, String numberOrder, int i) {
        Integer order = orderObject == null ? null : (Integer) orderObject.object;
        return (order == null ? numberOrder : String.valueOf(order)) + i;
    }

    private Boolean showField(List<ProductionOrderDetail> data, String fieldName) {
        try {
            Field field = ProductionOrderDetail.class.getField(fieldName);

            for (ProductionOrderDetail aData : data) {
                if (field.get(aData) != null)
                    return true;
            }
        } catch (NoSuchFieldException e) {
            return true;
        } catch (IllegalAccessException e) {
            return true;
        }
        return false;
    }
}