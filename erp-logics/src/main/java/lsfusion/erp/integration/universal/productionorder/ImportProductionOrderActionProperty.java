package lsfusion.erp.integration.universal.productionorder;

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import lsfusion.base.IOUtils;
import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.erp.integration.universal.ImportDocumentActionProperty;
import lsfusion.erp.integration.universal.ImportDocumentSettings;
import lsfusion.erp.integration.universal.UniversalImportException;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.integration.*;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.context.ExecutionStack;
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
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

public class ImportProductionOrderActionProperty extends ImportDocumentActionProperty {
    private final ClassPropertyInterface orderInterface;

    public ImportProductionOrderActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        this(LM, LM.findClass("Production.Order"));
    }
    
    public ImportProductionOrderActionProperty(ScriptingLogicsModule LM, ValueClass... classes) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        orderInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        super.executeCustom(context);
        try {

            DataSession session = context.getSession();

            DataObject orderObject = context.getDataKeyValue(orderInterface);

            ObjectValue importTypeObject = findProperty("importType[Order]").readClasses(session, orderObject);

            if (!(importTypeObject instanceof NullValue)) {

                ObjectValue operationObject = findProperty("autoImportOperation[ImportType]").readClasses(session, (DataObject) importTypeObject);

                Map<String, ImportColumnDetail> importColumns = readImportColumns(session, importTypeObject).get(0);
                ImportDocumentSettings settings = readImportDocumentSettings(session, importTypeObject);
                String fileExtension = settings.getFileExtension();

                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, fileExtension + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                        for (byte[] file : fileList) {

                            makeImport(context.getBL(), session, context.stack, orderObject, importColumns, file, settings, fileExtension, operationObject);

                            session.apply(context);
                            
                            findAction("formRefresh[]").execute(context);
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException | xBaseJException | IOException | BiffException | ParseException e) {
            throw new RuntimeException(e);
        } catch (UniversalImportException e) {
            e.printStackTrace();
            context.requestUserInteraction(new MessageClientAction(e.getMessage(), e.getTitle()));
        }
    }

    public boolean makeImport(BusinessLogics BL, DataSession session, ExecutionStack stack, DataObject orderObject, Map<String, ImportColumnDetail> importColumns,
                              byte[] file, ImportDocumentSettings settings, String fileExtension, ObjectValue operationObject)
            throws ParseException, IOException, SQLException, BiffException, xBaseJException, ScriptingErrorLog.SemanticErrorException, UniversalImportException, SQLHandledException {

        List<ProductionOrderDetail> orderDetailsList = importOrdersFromFile(orderObject, importColumns, file, fileExtension, settings.getStartRow(), settings.isPosted(), settings.getSeparator());

        boolean importResult = importOrders(orderDetailsList, BL, session, stack, orderObject, importColumns, operationObject);

        findAction("formRefresh[]").execute(session, stack);

        return importResult;
    }

    public boolean importOrders(List<ProductionOrderDetail> orderDetailsList, BusinessLogics BL, DataSession session,
                                ExecutionStack stack, DataObject orderObject, Map<String, ImportColumnDetail> importColumns, ObjectValue operationObject)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException, SQLHandledException {

        if (orderDetailsList != null && (orderObject !=null || showField(orderDetailsList, "idOrder"))) {

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(orderDetailsList.size());

            ImportKey<?> orderKey = null;
            ImportField idOrderField = null;
            if (orderObject == null && showField(orderDetailsList, "idOrder")) {
                idOrderField = new ImportField(findProperty("id[Order]"));
                orderKey = new ImportKey((CustomClass) findClass("Production.Order"),
                        findProperty("order[VARSTRING[100]]").getMapping(idOrderField));
                keys.add(orderKey);
                props.add(new ImportProperty(idOrderField, findProperty("id[Order]").getMapping(orderKey)));
                fields.add(idOrderField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).idOrder);
            }
            
            if (showField(orderDetailsList, "isPosted")) {
                if(orderObject == null)
                    addDataField(props, fields, importColumns, findProperty("isPosted[Order]"), "isPosted", orderKey);
                else
                    addDataField(props, fields, importColumns, findProperty("isPosted[Order]"), "isPosted", orderObject);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).isPosted);
            }
            
            if (showField(orderDetailsList, "numberOrder")) {
                if(orderObject == null)
                    addDataField(props, fields, importColumns, findProperty("number[Order]"), "numberOrder", orderKey);
                else
                    addDataField(props, fields, importColumns, findProperty("number[Order]"), "numberOrder", orderObject);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).numberOrder);
            }

            if (showField(orderDetailsList, "dateDocument")) {
                if(orderObject == null)
                    addDataField(props, fields, importColumns, findProperty("date[Order]"), "dateDocument", orderKey);
                else
                    addDataField(props, fields, importColumns, findProperty("date[Order]"), "dateDocument", orderObject);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("dateDocument"));
            }

            if (showField(orderDetailsList, "idProductsStock")) {
                ImportField idProductsStockOrderField = new ImportField(findProperty("id[Stock]"));
                ImportKey<?> productsStockOrderKey = new ImportKey((CustomClass) findClass("Stock"),
                        findProperty("stock[VARSTRING[100]]").getMapping(idProductsStockOrderField));
                productsStockOrderKey.skipKey = true;
                keys.add(productsStockOrderKey);
                props.add(new ImportProperty(idProductsStockOrderField, findProperty("productsStock[Order]").getMapping(orderObject == null ? orderKey : orderObject),
                        object(findClass("Stock")).getMapping(productsStockOrderKey)));
                fields.add(idProductsStockOrderField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("idProductsStock"));
            }

            ImportField idSkuField = new ImportField(findProperty("id[Sku]"));
            ImportKey<?> skuKey = new ImportKey((CustomClass) findClass("Sku"),
                    findProperty("sku[VARSTRING[100]]").getMapping(idSkuField));
            keys.add(skuKey);
            fields.add(idSkuField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).getFieldValue("idItem"));

            ImportField idBOMField = new ImportField(findProperty("id[BOM]"));
            ImportKey<?> BOMKey = new ImportKey((ConcreteCustomClass) findClass("BOM"),
                    findProperty("BOM[VARSTRING[100]]").getMapping(idBOMField));
            keys.add(BOMKey);
            props.add(new ImportProperty(idBOMField, findProperty("id[BOM]").getMapping(BOMKey)));
            props.add(new ImportProperty(idBOMField, findProperty("number[BOM]").getMapping(BOMKey)));
            fields.add(idBOMField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).getFieldValue("idItem"));
            
            ImportField extIdProductField = new ImportField(findProperty("extId[Product]"));
            ImportKey<?> productKey = new ImportKey((CustomClass) findClass("Product"),
                    findProperty("extProduct[VARSTRING[100]]").getMapping(extIdProductField));
            keys.add(productKey);
            props.add(new ImportProperty(extIdProductField, findProperty("extId[Product]").getMapping(productKey)));
            props.add(new ImportProperty(idBOMField, findProperty("BOM[Product]").getMapping(productKey),
                    object(findClass("BOM")).getMapping(BOMKey)));
            props.add(new ImportProperty(idSkuField, findProperty("sku[Product]").getMapping(productKey),
                    object(findClass("Sku")).getMapping(skuKey)));
            fields.add(extIdProductField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).getFieldValue("idProduct"));

            ImportField idProductDetailField = new ImportField(findProperty("id[ProductDetail]"));
            ImportKey<?> productDetailKey = new ImportKey((CustomClass) findClass("Production.ProductDetail"),
                    findProperty("productDetail[VARSTRING[100]]").getMapping(idProductDetailField));
            keys.add(productDetailKey);
            props.add(new ImportProperty(idProductDetailField, findProperty("id[ProductDetail]").getMapping(productDetailKey)));
            if(orderObject == null)
                props.add(new ImportProperty(idOrderField, findProperty("order[ProductDetail]").getMapping(productDetailKey),
                        object(findClass("Production.Order")).getMapping(orderKey)));
            else
                props.add(new ImportProperty(orderObject, findProperty("order[ProductDetail]").getMapping(productDetailKey)));
            props.add(new ImportProperty(idSkuField, findProperty("product[ProductDetail]").getMapping(productDetailKey),
                    object(findClass("Product")).getMapping(productKey)));
            props.add(new ImportProperty(idSkuField, findProperty("sku[ProductDetail]").getMapping(productDetailKey),
                    object(findClass("Sku")).getMapping(skuKey)));
            fields.add(idProductDetailField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).idProductDetail);

            if (operationObject instanceof DataObject)
                props.add(new ImportProperty((DataObject) operationObject, findProperty("operation[Order]").getMapping(orderObject == null ? orderKey : orderObject)));

            if (showField(orderDetailsList, "dataIndex")) {
                ImportField dataIndexOrderDetailField = new ImportField(findProperty("dataIndex[ProductDetail]"));
                props.add(new ImportProperty(dataIndexOrderDetailField, findProperty("dataIndex[ProductDetail]").getMapping(productDetailKey)));
                fields.add(dataIndexOrderDetailField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("dataIndex"));
            }
            
            if (showField(orderDetailsList, "outputQuantity")) {
                addDataField(props, fields, importColumns, findProperty("outputQuantity[ProductDetail]"), "outputQuantity", productDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("outputQuantity"));
            }

            if (showField(orderDetailsList, "price")) {
                addDataField(props, fields, importColumns, findProperty("price[ProductDetail]"), "price", productDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("price"));
            }
            
            if (showField(orderDetailsList, "componentsPrice")) {
                addDataField(props, fields, importColumns, findProperty("componentsPrice[ProductDetail]"), "componentsPrice", productDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("componentsPrice"));
            }

            if (showField(orderDetailsList, "valueVAT")) {
                addDataField(props, fields, importColumns, findProperty("valueVAT[ProductDetail]"), "valueVAT", productDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("valueVAT"));
            }

            if (showField(orderDetailsList, "markup")) {
                addDataField(props, fields, importColumns, findProperty("markup[ProductDetail]"), "markup", productDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("markup"));
            }

            if (showField(orderDetailsList, "sum")) {
                addDataField(props, fields, importColumns, findProperty("sum[ProductDetail]"), "sum", productDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("sum"));
            }

            if (showField(orderDetailsList, "costPrice")) {
                addDataField(props, fields, importColumns, findProperty("costPrice[ProductDetail]"), "costPrice", productDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("costPrice"));
            }

            ImportTable table = new ImportTable(fields, data);

            session.pushVolatileStats("POA_IO");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            String result = session.applyMessage(BL, stack);
            session.popVolatileStats();

            return result == null;
        }
        return false;
    }

    public List<ProductionOrderDetail> importOrdersFromFile(DataObject orderObject, Map<String, ImportColumnDetail> importColumns, 
                                                                  byte[] file, String fileExtension, Integer startRow, Boolean isPosted, String separator)
            throws ParseException, UniversalImportException, IOException, SQLException, xBaseJException, ScriptingErrorLog.SemanticErrorException, BiffException, SQLHandledException {

        List<ProductionOrderDetail> orderDetailsList;

        List<String> stringFields = Arrays.asList("idProduct", "idProductsStock", "idItem");
        
        List<String> bigDecimalFields = Arrays.asList("price", "componentsPrice", "valueVAT", "markup", "sum", "outputQuantity", "dataIndex", "costPrice");

        List<String> dateFields = Arrays.asList("dateDocument");
        
        if (fileExtension.equals("DBF"))
            orderDetailsList = importOrdersFromDBF(file, importColumns, stringFields, bigDecimalFields, dateFields, startRow, isPosted, orderObject);
        else if (fileExtension.equals("XLS"))
            orderDetailsList = importOrdersFromXLS(file, importColumns, stringFields, bigDecimalFields, dateFields, startRow, isPosted, orderObject);
        else if (fileExtension.equals("XLSX"))
            orderDetailsList = importOrdersFromXLSX(file, importColumns, stringFields, bigDecimalFields, dateFields, startRow, isPosted, orderObject);
        else if (fileExtension.equals("CSV") || fileExtension.equals("TXT"))
            orderDetailsList = importOrdersFromCSV(file, importColumns, stringFields, bigDecimalFields, dateFields, startRow, isPosted, separator, orderObject);
        else
            orderDetailsList = null;

        return orderDetailsList;
    }

    private List<ProductionOrderDetail> importOrdersFromXLS(byte[] importFile, Map<String, ImportColumnDetail> importColumns,
                                                            List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields, 
                                                            Integer startRow, Boolean isPosted, DataObject orderObject)
            throws IOException, BiffException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<ProductionOrderDetail> result = new ArrayList<>();

        WorkbookSettings ws = new WorkbookSettings();
        ws.setEncoding("cp1251");
        ws.setGCDisabled(true);
        Workbook wb = Workbook.getWorkbook(new ByteArrayInputStream(importFile), ws);
        Sheet sheet = wb.getSheet(0);

        for (int i = startRow - 1; i < sheet.getRows(); i++) {
            Map<String, Object> fieldValues = new HashMap<>();
            for(String field : stringFields) {
                fieldValues.put(field, getXLSFieldValue(sheet, i, importColumns.get(field)));
            }
            for(String field : bigDecimalFields) {
                BigDecimal value = getXLSBigDecimalFieldValue(sheet, i, importColumns.get(field));
                if(field.equals("dataIndex"))
                    fieldValues.put(field, value == null ? null : value.intValue());
                else
                    fieldValues.put(field, value);
            }
            for(String field : dateFields) {
                fieldValues.put(field, getXLSDateFieldValue(sheet, i, importColumns.get(field)));
            }
            
            String numberOrder = getXLSFieldValue(sheet, i, importColumns.get("numberDocument"));
            String idOrder = getXLSFieldValue(sheet, i, importColumns.get("idDocument"), numberOrder);
            String idOrderDetail = makeIdOrderDetail(orderObject, numberOrder, i);
            
            result.add(new ProductionOrderDetail(fieldValues, isPosted, idOrder, numberOrder, idOrderDetail));
        }

        return result;
    }

    private List<ProductionOrderDetail> importOrdersFromCSV(byte[] importFile, Map<String, ImportColumnDetail> importColumns,
                                                            List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields, 
                                                            Integer startRow, Boolean isPosted, String separator, DataObject orderObject)
            throws UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, IOException, SQLHandledException {

        List<ProductionOrderDetail> result = new ArrayList<>();
        
        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(importFile), "utf-8"));
        String line;
        
        List<String[]> valuesList = new ArrayList<>();
        while ((line = br.readLine()) != null) {
            valuesList.add(line.split(separator));              
        }

        for (int count = startRow; count <= valuesList.size(); count++) {
            Map<String, Object> fieldValues = new HashMap<>();
            for(String field : stringFields) {
                fieldValues.put(field, getCSVFieldValue(valuesList, importColumns.get(field), count));
            }
            for(String field : bigDecimalFields) {
                BigDecimal value = getCSVBigDecimalFieldValue(valuesList, importColumns.get(field), count);
                if(field.equals("dataIndex"))
                    fieldValues.put(field, value == null ? null : value.intValue());
                else
                    fieldValues.put(field, value);
            }
            for(String field : dateFields) {
                fieldValues.put(field, getCSVDateFieldValue(valuesList, importColumns.get(field), count));
            }

            String numberOrder = getCSVFieldValue(valuesList, importColumns.get("numberDocument"), count);
            String idOrder = getCSVFieldValue(valuesList, importColumns.get("idDocument"), count, numberOrder);
            String idOrderDetail = makeIdOrderDetail(orderObject, numberOrder, count);
           
            result.add(new ProductionOrderDetail(fieldValues, isPosted, idOrder, numberOrder, idOrderDetail));
        }

        return result;
    }

    private List<ProductionOrderDetail> importOrdersFromXLSX(byte[] importFile, Map<String, ImportColumnDetail> importColumns,
                                                             List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields, 
                                                             Integer startRow, Boolean isPosted, DataObject orderObject)
            throws IOException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<ProductionOrderDetail> result = new ArrayList<>();
        
        XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(importFile));
        XSSFSheet sheet = Wb.getSheetAt(0);

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {
            Map<String, Object> fieldValues = new HashMap<>();
            for(String field : stringFields) {
                fieldValues.put(field, getXLSXFieldValue(sheet, i, importColumns.get(field)));
            }
            for(String field : bigDecimalFields) {
                BigDecimal value = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get(field));
                if(field.equals("dataIndex"))
                    fieldValues.put(field, value == null ? null : value.intValue());
                else               
                    fieldValues.put(field, value);
            }
            for(String field : dateFields) {
                fieldValues.put(field, getXLSXDateFieldValue(sheet, i, importColumns.get(field)));
            }
            
            String numberOrder = getXLSXFieldValue(sheet, i, importColumns.get("numberDocument"));
            String idOrder = getXLSXFieldValue(sheet, i, importColumns.get("idDocument"), numberOrder);
            String idOrderDetail = makeIdOrderDetail(orderObject, numberOrder, i);
            
            result.add(new ProductionOrderDetail(fieldValues, isPosted, idOrder, numberOrder, idOrderDetail));
        }

        return result;
    }

    private List<ProductionOrderDetail> importOrdersFromDBF(byte[] importFile, Map<String, ImportColumnDetail> importColumns,
                                                            List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields,
                                                            Integer startRow, Boolean isPosted, DataObject orderObject) throws IOException, xBaseJException, UniversalImportException {

        List<ProductionOrderDetail> result = new ArrayList<>();

        DBF file = null;
        File tempFile = null;
        try {

            tempFile = File.createTempFile("productionOrder", ".dbf");
            IOUtils.putFileBytes(tempFile, importFile);

            file = new DBF(tempFile.getPath());
            String charset = getDBFCharset(tempFile);

            int totalRecordCount = file.getRecordCount();

            for (int i = 0; i < startRow - 1; i++) {
                file.read();
            }

            for (int i = startRow - 1; i < totalRecordCount; i++) {

                file.read();

                Map<String, Object> fieldValues = new HashMap<>();
                for (String field : stringFields) {
                    String value = getDBFFieldValue(file, importColumns.get(field), i, charset);
                    fieldValues.put(field, value);
                }
                for (String field : bigDecimalFields) {
                    BigDecimal value = getDBFBigDecimalFieldValue(file, importColumns.get(field), i, charset);
                    if (field.equals("dataIndex"))
                        fieldValues.put(field, value == null ? null : value.intValue());
                    else
                        fieldValues.put(field, value);
                }
                for (String field : dateFields) {
                    fieldValues.put(field, getDBFDateFieldValue(file, importColumns.get(field), i, charset));
                }

                String numberOrder = getDBFFieldValue(file, importColumns.get("numberDocument"), i, charset);
                String idOrder = getDBFFieldValue(file, importColumns.get("idDocument"), i, charset, numberOrder);
                String idOrderDetail = makeIdOrderDetail(orderObject, numberOrder, i);

                result.add(new ProductionOrderDetail(fieldValues, isPosted, idOrder, numberOrder, idOrderDetail));
            }
        } finally {
            if (file != null)
                file.close();
            if (tempFile != null)
                tempFile.delete();
        }
        return result;
    }
    
    private String makeIdOrderDetail(DataObject orderObject, String numberOrder, int i) {
        Long order = orderObject == null ? null : (Long) orderObject.object;
        return (order == null ? numberOrder : String.valueOf(order)) + i;
    }

    protected Boolean showField(List<ProductionOrderDetail> data, String fieldName) {
        try {

            boolean found = false;
            Field fieldValues = ProductionOrderDetail.class.getField("fieldValues");
            for (ProductionOrderDetail entry : data) {
                Map<String, Object> values = (Map<String, Object>) fieldValues.get(entry);
                if(!found) {
                    if (values.containsKey(fieldName))
                        found = true;
                    else
                        break;
                }
                if (values.get(fieldName) != null)
                    return true;
            }

            if(!found) {
                Field field = ProductionOrderDetail.class.getField(fieldName);

                for (ProductionOrderDetail entry : data) {
                    if (field.get(entry) != null)
                        return true;
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return true;
        }
        return false;
    }
}