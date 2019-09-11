package lsfusion.erp.integration.universal.productionorder;

import lsfusion.base.file.RawFileData;
import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.erp.integration.universal.ImportDocumentAction;
import lsfusion.erp.integration.universal.ImportDocumentSettings;
import lsfusion.erp.integration.universal.UniversalImportException;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.classes.user.CustomClass;
import lsfusion.server.logics.classes.data.file.CustomStaticFormatFileClass;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.NullValue;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.physics.dev.integration.service.*;
import lsfusion.server.logics.action.session.DataSession;
import org.apache.poi.hssf.usermodel.HSSFFormulaEvaluator;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

public class ImportProductionOrderAction extends ImportDocumentAction {
    private final ClassPropertyInterface orderInterface;

    public ImportProductionOrderAction(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        this(LM, LM.findClass("Production.Order"));
    }
    
    public ImportProductionOrderAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        orderInterface = i.next();
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        super.executeInternal(context);
        try {

            DataObject orderObject = context.getDataKeyValue(orderInterface);

            ObjectValue importTypeObject = findProperty("importType[Order]").readClasses(context, orderObject);

            if (!(importTypeObject instanceof NullValue)) {

                ObjectValue operationObject = findProperty("autoImportOperation[ImportType]").readClasses(context, (DataObject) importTypeObject);

                Map<String, ImportColumnDetail> importColumns = readImportColumns(context, importTypeObject).get(0);
                ImportDocumentSettings settings = readImportDocumentSettings(context.getSession(), importTypeObject);
                String fileExtension = settings.getFileExtension();

                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(fileExtension + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        makeImport(context, orderObject, importColumns, (RawFileData) objectValue.getValue(), settings, fileExtension, operationObject);

                        context.apply();
                        
                        findAction("formRefresh[]").execute(context);
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException | xBaseJException | IOException | ParseException e) {
            throw new RuntimeException(e);
        } catch (UniversalImportException e) {
            e.printStackTrace();
            context.requestUserInteraction(new MessageClientAction(e.getMessage(), e.getTitle()));
        }
    }

    public boolean makeImport(ExecutionContext context, DataObject orderObject, Map<String, ImportColumnDetail> importColumns, RawFileData file, ImportDocumentSettings settings, String fileExtension, ObjectValue operationObject)
            throws ParseException, IOException, SQLException, xBaseJException, ScriptingErrorLog.SemanticErrorException, UniversalImportException, SQLHandledException {

        List<ProductionOrderDetail> orderDetailsList = importOrdersFromFile(orderObject, importColumns, file, fileExtension, settings.getStartRow(), settings.isPosted(), settings.getSeparator());

        boolean importResult = importOrders(orderDetailsList, context, orderObject, importColumns, operationObject);

        findAction("formRefresh[]").execute(context);

        return importResult;
    }

    public boolean importOrders(List<ProductionOrderDetail> orderDetailsList, ExecutionContext context, DataObject orderObject, Map<String, ImportColumnDetail> importColumns, ObjectValue operationObject)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

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
                        findProperty("order[STRING[100]]").getMapping(idOrderField));
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
                        findProperty("stock[STRING[100]]").getMapping(idProductsStockOrderField));
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
                    findProperty("sku[STRING[100]]").getMapping(idSkuField));
            keys.add(skuKey);
            fields.add(idSkuField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).getFieldValue("idItem"));

            ImportField idBOMField = new ImportField(findProperty("id[BOM]"));
            ImportKey<?> BOMKey = new ImportKey((ConcreteCustomClass) findClass("BOM"),
                    findProperty("BOM[STRING[100]]").getMapping(idBOMField));
            keys.add(BOMKey);
            props.add(new ImportProperty(idBOMField, findProperty("id[BOM]").getMapping(BOMKey)));
            props.add(new ImportProperty(idBOMField, findProperty("number[BOM]").getMapping(BOMKey)));
            fields.add(idBOMField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).getFieldValue("idItem"));
            
            ImportField extIdProductField = new ImportField(findProperty("extId[Product]"));
            ImportKey<?> productKey = new ImportKey((CustomClass) findClass("Product"),
                    findProperty("extProduct[STRING[100]]").getMapping(extIdProductField));
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
                    findProperty("productDetail[STRING[100]]").getMapping(idProductDetailField));
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

            IntegrationService service = new IntegrationService(context, table, keys, props);
            service.synchronize(true, false);
            String result = context.applyMessage();

            return result == null;
        }
        return false;
    }

    public List<ProductionOrderDetail> importOrdersFromFile(DataObject orderObject, Map<String, ImportColumnDetail> importColumns,
                                                            RawFileData file, String fileExtension, Integer startRow, Boolean isPosted, String separator)
            throws UniversalImportException, IOException, xBaseJException {

        List<ProductionOrderDetail> orderDetailsList;

        List<String> stringFields = Arrays.asList("idProduct", "idProductsStock", "idItem");
        
        List<String> bigDecimalFields = Arrays.asList("price", "componentsPrice", "valueVAT", "markup", "sum", "outputQuantity", "dataIndex", "costPrice");

        List<String> dateFields = Collections.singletonList("dateDocument");

        switch (fileExtension) {
            case "DBF":
                orderDetailsList = importOrdersFromDBF(file, importColumns, stringFields, bigDecimalFields, dateFields, startRow, isPosted, orderObject);
                break;
            case "XLS":
                orderDetailsList = importOrdersFromXLS(file, importColumns, stringFields, bigDecimalFields, dateFields, startRow, isPosted, orderObject);
                break;
            case "XLSX":
                orderDetailsList = importOrdersFromXLSX(file, importColumns, stringFields, bigDecimalFields, dateFields, startRow, isPosted, orderObject);
                break;
            case "CSV":
            case "TXT":
                orderDetailsList = importOrdersFromCSV(file, importColumns, stringFields, bigDecimalFields, dateFields, startRow, isPosted, separator, orderObject);
                break;
            default:
                orderDetailsList = null;
                break;
        }

        return orderDetailsList;
    }

    private List<ProductionOrderDetail> importOrdersFromXLS(RawFileData importFile, Map<String, ImportColumnDetail> importColumns,
                                                            List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields,
                                                            Integer startRow, Boolean isPosted, DataObject orderObject)
            throws IOException, UniversalImportException {

        List<ProductionOrderDetail> result = new ArrayList<>();

        HSSFWorkbook wb = new HSSFWorkbook(importFile.getInputStream());
        FormulaEvaluator formulaEvaluator = new HSSFFormulaEvaluator(wb);
        HSSFSheet sheet = wb.getSheetAt(0);

        for (int i = startRow - 1; i < sheet.getLastRowNum(); i++) {
            Map<String, Object> fieldValues = new HashMap<>();
            for(String field : stringFields) {
                fieldValues.put(field, getXLSFieldValue(formulaEvaluator, sheet, i, importColumns.get(field)));
            }
            for(String field : bigDecimalFields) {
                BigDecimal value = getXLSBigDecimalFieldValue(formulaEvaluator, sheet, i, importColumns.get(field));
                if(field.equals("dataIndex"))
                    fieldValues.put(field, value == null ? null : value.intValue());
                else
                    fieldValues.put(field, value);
            }
            for(String field : dateFields) {
                fieldValues.put(field, getXLSDateFieldValue(formulaEvaluator, sheet, i, importColumns.get(field)));
            }
            
            String numberOrder = getXLSFieldValue(formulaEvaluator, sheet, i, importColumns.get("numberDocument"));
            String idOrder = getXLSFieldValue(formulaEvaluator, sheet, i, importColumns.get("idDocument"), numberOrder);
            String idOrderDetail = makeIdOrderDetail(orderObject, numberOrder, i);
            
            result.add(new ProductionOrderDetail(fieldValues, isPosted, idOrder, numberOrder, idOrderDetail));
        }

        return result;
    }

    private List<ProductionOrderDetail> importOrdersFromCSV(RawFileData importFile, Map<String, ImportColumnDetail> importColumns,
                                                            List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields,
                                                            Integer startRow, Boolean isPosted, String separator, DataObject orderObject)
            throws UniversalImportException, IOException {

        List<ProductionOrderDetail> result = new ArrayList<>();
        
        BufferedReader br = new BufferedReader(new InputStreamReader(importFile.getInputStream(), StandardCharsets.UTF_8));
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

    private List<ProductionOrderDetail> importOrdersFromXLSX(RawFileData importFile, Map<String, ImportColumnDetail> importColumns,
                                                             List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields,
                                                             Integer startRow, Boolean isPosted, DataObject orderObject)
            throws IOException, UniversalImportException {

        List<ProductionOrderDetail> result = new ArrayList<>();
        
        XSSFWorkbook Wb = new XSSFWorkbook(importFile.getInputStream());
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

    private List<ProductionOrderDetail> importOrdersFromDBF(RawFileData importFile, Map<String, ImportColumnDetail> importColumns,
                                                            List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields,
                                                            Integer startRow, Boolean isPosted, DataObject orderObject) throws IOException, xBaseJException, UniversalImportException {

        List<ProductionOrderDetail> result = new ArrayList<>();

        DBF file = null;
        File tempFile = null;
        try {

            tempFile = File.createTempFile("productionOrder", ".dbf");
            importFile.write(tempFile);

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
            if (tempFile != null && !tempFile.delete())
                tempFile.deleteOnExit();
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