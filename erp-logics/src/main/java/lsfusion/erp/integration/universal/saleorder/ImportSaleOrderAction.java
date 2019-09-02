package lsfusion.erp.integration.universal.saleorder;

import com.google.common.base.Throwables;
import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.ERPLoggers;
import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.erp.integration.universal.ImportDocumentAction;
import lsfusion.erp.integration.universal.ImportDocumentSettings;
import lsfusion.erp.integration.universal.UniversalImportException;
import lsfusion.erp.stock.BarcodeUtils;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.classes.user.CustomClass;
import lsfusion.server.logics.classes.data.file.CustomStaticFormatFileClass;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.NullValue;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.physics.dev.integration.service.*;
import lsfusion.server.logics.action.session.DataSession;
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

public class ImportSaleOrderAction extends ImportDocumentAction {
    private final ClassPropertyInterface orderInterface;

    // Опциональные модули
    ScriptingLogicsModule saleManufacturingPriceLM;

    public ImportSaleOrderAction(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        this(LM, LM.findClass("Sale.UserOrder"));
    }

    public ImportSaleOrderAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        orderInterface = i.next();
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        super.executeInternal(context);
        try {

            DataSession session = context.getSession();

            DataObject orderObject = context.getDataKeyValue(orderInterface);

            ObjectValue importTypeObject = findProperty("importType[Sale.Order]").readClasses(session, orderObject);

            if (!(importTypeObject instanceof NullValue)) {

                ObjectValue operationObject = findProperty("autoImportOperation[ImportType]").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierObject = findProperty("autoImportSupplier[ImportType]").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierStockObject = findProperty("autoImportSupplierStock[ImportType]").readClasses(session, (DataObject) importTypeObject);
                ObjectValue customerObject = findProperty("autoImportCustomer[ImportType]").readClasses(session, (DataObject) importTypeObject);
                ObjectValue customerStockObject = findProperty("autoImportCustomerStock[ImportType]").readClasses(session, (DataObject) importTypeObject);

                Map<String, ImportColumnDetail> importColumns = readImportColumns(context, importTypeObject).get(0);
                ImportDocumentSettings settings = readImportDocumentSettings(session, importTypeObject);
                String fileExtension = settings.getFileExtension();

                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(true, true, fileExtension + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        Map<String, RawFileData> fileList = valueClass.getMultipleNamedFiles(objectValue.getValue());

                        for (Map.Entry<String, RawFileData> file : fileList.entrySet()) {

                            try {
                                makeImport(context, orderObject, importColumns, file.getValue(), settings, fileExtension,
                                        operationObject, supplierObject, supplierStockObject, customerObject, customerStockObject);

                                context.apply();

                                findAction("formRefresh[]").execute(context);
                            } catch (IOException | xBaseJException | ParseException | ScriptingErrorLog.SemanticErrorException | BiffException e) {
                                ERPLoggers.importLogger.error("ImportSaleOrder failed, file " + file.getKey(), e);
                                throw Throwables.propagate(e);
                            }
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        } catch (UniversalImportException e) {
            e.printStackTrace();
            context.requestUserInteraction(new MessageClientAction(e.getMessage(), e.getTitle()));
        }
    }

    public boolean makeImport(ExecutionContext context, DataObject orderObject, Map<String, ImportColumnDetail> importColumns, RawFileData file, ImportDocumentSettings settings, String fileExtension, ObjectValue operationObject, ObjectValue supplierObject, ObjectValue supplierStockObject, ObjectValue customerObject, ObjectValue customerStockObject)
            throws ParseException, IOException, SQLException, BiffException, xBaseJException, ScriptingErrorLog.SemanticErrorException, UniversalImportException, SQLHandledException {

        this.saleManufacturingPriceLM = context.getBL().getModule("SaleManufacturingPrice");

        List<List<SaleOrderDetail>> orderDetailsList = importOrdersFromFile(context.getSession(), (Long) orderObject.object,
                importColumns, file, fileExtension, settings.getStartRow(), settings.isPosted(), settings.getSeparator(),
                settings.getPrimaryKeyType(), settings.isCheckExistence(), settings.getSecondaryKeyType(), settings.isKeyIsDigit());

        boolean importResult1 = (orderDetailsList != null && orderDetailsList.size() >= 1) && importOrders(orderDetailsList.get(0), context, orderObject, importColumns, settings.getPrimaryKeyType(), operationObject, supplierObject, supplierStockObject,
                customerObject, customerStockObject);

        boolean importResult2 = (orderDetailsList != null && orderDetailsList.size() >= 2) && importOrders(orderDetailsList.get(1), context, orderObject, importColumns, settings.getSecondaryKeyType(), operationObject, supplierObject, supplierStockObject,
                customerObject, customerStockObject);

        findAction("formRefresh[]").execute(context);

        return importResult1 && importResult2;
    }

    public boolean importOrders(List<SaleOrderDetail> orderDetailsList, ExecutionContext context, DataObject orderObject, Map<String, ImportColumnDetail> importColumns, String keyType, ObjectValue operationObject, ObjectValue supplierObject, ObjectValue supplierStockObject, ObjectValue customerObject, ObjectValue customerStockObject)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (orderDetailsList != null) {
            
            if(orderDetailsList.isEmpty())
                return true;

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(orderDetailsList.size());

            if (showField(orderDetailsList, "numberDocument")) {
                addDataField(props, fields, importColumns, findProperty("number[UserOrder]"), "numberDocument", orderObject);
                addDataField(props, fields, importColumns, findProperty("number[UserOrder]"), "numberDocument", orderObject);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("numberDocument"));
            }

            if (showField(orderDetailsList, "dateDocument")) {
                addDataField(props, fields, importColumns, findProperty("date[UserOrder]"), "dateDocument", orderObject);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("dateDocument"));
            }

            ImportField idUserOrderDetailField = new ImportField(findProperty("id[UserOrderDetail]"));
            ImportKey<?> orderDetailKey = new ImportKey((CustomClass) findClass("Sale.UserOrderDetail"),
                    findProperty("userOrderDetail[STRING[100]]").getMapping(idUserOrderDetailField));
            keys.add(orderDetailKey);
            props.add(new ImportProperty(idUserOrderDetailField, findProperty("id[UserOrderDetail]").getMapping(orderDetailKey)));
            props.add(new ImportProperty(orderObject, findProperty("userOrder[UserOrderDetail]").getMapping(orderDetailKey)));
            fields.add(idUserOrderDetailField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).getFieldValue("idDocumentDetail"));

            if (operationObject instanceof DataObject)
                props.add(new ImportProperty((DataObject) operationObject, findProperty("operation[UserOrder]").getMapping(orderObject)));

            if (supplierObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) supplierObject, findProperty("supplier[UserOrderDetail]").getMapping(orderDetailKey)));
                props.add(new ImportProperty((DataObject) supplierObject, findProperty("supplier[UserOrder]").getMapping(orderObject)));
            }

            if (supplierStockObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) supplierStockObject, findProperty("supplierStock[UserOrderDetail]").getMapping(orderDetailKey)));
                props.add(new ImportProperty((DataObject) supplierStockObject, findProperty("supplierStock[UserOrder]").getMapping(orderObject)));
            }

            if (customerObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) customerObject, findProperty("customer[UserOrderDetail]").getMapping(orderDetailKey)));
                props.add(new ImportProperty((DataObject) customerObject, findProperty("customer[UserOrder]").getMapping(orderObject)));
            }

            if (customerStockObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) customerStockObject, findProperty("customerStock[UserOrderDetail]").getMapping(orderDetailKey)));
                props.add(new ImportProperty((DataObject) customerStockObject, findProperty("customerStock[UserOrder]").getMapping(orderObject)));
            }

            ImportField idBarcodeSkuField = new ImportField(findProperty("idBarcode[Sku]"));
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) findClass("Barcode"),
                    findProperty("extBarcode[STRING[100]]").getMapping(idBarcodeSkuField));
            keys.add(barcodeKey);
            barcodeKey.skipKey = true;
            fields.add(idBarcodeSkuField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).getFieldValue("barcodeItem"));

            ImportField idBatchField = new ImportField(findProperty("id[Batch]"));
            ImportKey<?> batchKey = new ImportKey((CustomClass) findClass("Batch"),
                    findProperty("batch[STRING[100]]").getMapping(idBatchField));
            props.add(new ImportProperty(idBatchField, findProperty("batch[UserOrderDetail]").getMapping(orderDetailKey),
                    object(findClass("Batch")).getMapping(batchKey)));
            fields.add(idBatchField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).getFieldValue("idBatch"));

            ImportField dataIndexOrderDetailField = new ImportField(findProperty("dataIndex[UserOrderDetail]"));
            props.add(new ImportProperty(dataIndexOrderDetailField, findProperty("dataIndex[UserOrderDetail]").getMapping(orderDetailKey)));
            fields.add(dataIndexOrderDetailField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).getFieldValue("dataIndex"));

            ImportField idItemField = new ImportField(findProperty("id[Item]"));
            fields.add(idItemField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).getFieldValue("idItem"));

            LP iGroupAggr = getItemKeyGroupAggr(keyType);
            ImportField iField = (keyType == null || keyType.equals("item")) ? idItemField : keyType.equals("barcode") ? idBarcodeSkuField : idBatchField;
            ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                    iGroupAggr.getMapping(iField));
            keys.add(itemKey);
            itemKey.skipKey = true;
            props.add(new ImportProperty(iField, findProperty("sku[UserOrderDetail]").getMapping(orderDetailKey),
                    object(findClass("Sku")).getMapping(itemKey)));
            props.add(new ImportProperty(iField, findProperty("sku[Barcode]").getMapping(barcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));

            if (showField(orderDetailsList, "idManufacturer")) {
                ImportField idManufacturerField = new ImportField(findProperty("id[Manufacturer]"));
                ImportKey<?> manufacturerKey = new ImportKey((ConcreteCustomClass) findClass("Manufacturer"),
                        findProperty("manufacturer[STRING[100]]").getMapping(idManufacturerField));
                keys.add(manufacturerKey);
                props.add(new ImportProperty(idManufacturerField, findProperty("id[Manufacturer]").getMapping(manufacturerKey)));
                props.add(new ImportProperty(idManufacturerField, findProperty("manufacturer[Item]").getMapping(itemKey),
                        object(findClass("Manufacturer")).getMapping(manufacturerKey), getReplaceOnlyNull(importColumns, "idManufacturer")));
                fields.add(idManufacturerField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("idManufacturer"));
            }

            if (showField(orderDetailsList, "idCustomer")) {
                ImportField idCustomerField = new ImportField(findProperty("id[LegalEntity]"));
                ImportKey<?> customerKey = new ImportKey((ConcreteCustomClass) findClass("LegalEntity"),
                        findProperty("legalEntity[STRING[100]]").getMapping(idCustomerField));
                keys.add(customerKey);
                props.add(new ImportProperty(idCustomerField, findProperty("customer[UserOrder]").getMapping(orderObject),
                        object(findClass("LegalEntity")).getMapping(customerKey), getReplaceOnlyNull(importColumns, "idCustomer")));
                fields.add(idCustomerField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("idCustomer"));
            }

            if (showField(orderDetailsList, "idCustomerStock")) {
                ImportField idCustomerStockField = new ImportField(findProperty("id[Stock]"));
                ImportKey<?> customerStockKey = new ImportKey((CustomClass) findClass("Stock"),
                        findProperty("stock[STRING[100]]").getMapping(idCustomerStockField));
                keys.add(customerStockKey);
                props.add(new ImportProperty(idCustomerStockField, findProperty("customerStock[UserOrder]").getMapping(orderObject),
                        object(findClass("Stock")).getMapping(customerStockKey), getReplaceOnlyNull(importColumns, "idCustomerStock")));
                fields.add(idCustomerStockField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("idCustomerStock"));
            }

            if (showField(orderDetailsList, "quantity")) {
                addDataField(props, fields, importColumns, findProperty("quantity[UserOrderDetail]"), "quantity", orderDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("quantity"));
            }

            if (showField(orderDetailsList, "price")) {
                addDataField(props, fields, importColumns, findProperty("price[UserOrderDetail]"), "price", orderDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("price"));
            }

            if (showField(orderDetailsList, "sum")) {
                addDataField(props, fields, importColumns, findProperty("sum[UserOrderDetail]"), "sum", orderDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("sum"));
            }

            if (showField(orderDetailsList, "valueVAT")) {
                ImportField valueVATOrderDetailField = new ImportField(findProperty("valueVAT[UserOrderDetail]"));
                ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) findClass("Range"),
                        findProperty("valueCurrentVATDefault[NUMERIC[10,5]]").getMapping(valueVATOrderDetailField));
                keys.add(VATKey);
                props.add(new ImportProperty(valueVATOrderDetailField, findProperty("VAT[UserOrderDetail]").getMapping(orderDetailKey),
                        object(findClass("Range")).getMapping(VATKey), getReplaceOnlyNull(importColumns, "valueVAT")));
                fields.add(valueVATOrderDetailField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("valueVAT"));
            }

            if (showField(orderDetailsList, "sumVAT")) {
                addDataField(props, fields, importColumns, findProperty("VATSum[UserOrderDetail]"), "sumVAT", orderDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("sumVAT"));
            }

            if (showField(orderDetailsList, "invoiceSum")) {
                addDataField(props, fields, importColumns, findProperty("invoiceSum[UserOrderDetail]"), "invoiceSum", orderDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("invoiceSum"));
            }

            if ((saleManufacturingPriceLM != null) && showField(orderDetailsList, "manufacturingPrice")) {
                addDataField(props, fields, importColumns, saleManufacturingPriceLM.findProperty("manufacturingPrice[Sale.UserOrderDetail]"), "manufacturingPrice", orderDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).getFieldValue("manufacturingPrice"));
            }

            if (showField(orderDetailsList, "isPosted")) {
                addDataField(props, fields, importColumns, findProperty("isPosted[UserOrder]"), "isPosted", orderObject);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).isPosted);
            }

            ImportTable table = new ImportTable(fields, data);

            IntegrationService service = new IntegrationService(context, table, keys, props);
            service.synchronize(true, false);
            String result = context.applyMessage();

            return result == null;
        }
        return false;
    }

    public List<List<SaleOrderDetail>> importOrdersFromFile(DataSession session, Long orderObject, Map<String, ImportColumnDetail> importColumns,
                                                            RawFileData file, String fileExtension, Integer startRow, Boolean isPosted, String separator,
                                                            String primaryKeyType, boolean checkExistence, String secondaryKeyType, boolean keyIsDigit)
            throws UniversalImportException, IOException, SQLException, xBaseJException, ScriptingErrorLog.SemanticErrorException, BiffException, SQLHandledException {

        List<List<SaleOrderDetail>> orderDetailsList;

        List<String> stringFields = Arrays.asList("idDocumentDetail", "numberDocument", "idBatch", "barcodeItem", "idItem", "idManufacturer", "valueVAT", "idCustomerStock");
        
        List<String> bigDecimalFields = Arrays.asList("dataIndex", "quantity", "price", "sum", "sumVAT", "invoiceSum", "manufacturingPrice");

        List<String> dateFields = Arrays.asList("dateDocument");

        switch (fileExtension) {
            case "DBF":
                orderDetailsList = importOrdersFromDBF(session, file, importColumns, stringFields, bigDecimalFields, dateFields,
                        primaryKeyType, checkExistence, secondaryKeyType, keyIsDigit, startRow, isPosted, orderObject);
                break;
            case "XLS":
                orderDetailsList = importOrdersFromXLS(session, file, importColumns, stringFields, bigDecimalFields, dateFields,
                        primaryKeyType, checkExistence, secondaryKeyType, keyIsDigit, startRow, isPosted, orderObject);
                break;
            case "XLSX":
                orderDetailsList = importOrdersFromXLSX(session, file, importColumns, stringFields, bigDecimalFields, dateFields,
                        primaryKeyType, checkExistence, secondaryKeyType, keyIsDigit, startRow, isPosted, orderObject);
                break;
            case "CSV":
            case "TXT":
                orderDetailsList = importOrdersFromCSV(session, file, importColumns, stringFields, bigDecimalFields, dateFields,
                        primaryKeyType, checkExistence, secondaryKeyType, keyIsDigit, startRow, isPosted, separator, orderObject);
                break;
            default:
                orderDetailsList = null;
                break;
        }

        return orderDetailsList;
    }

    private List<List<SaleOrderDetail>> importOrdersFromXLS(DataSession session, RawFileData importFile, Map<String, ImportColumnDetail> importColumns,
                                                            List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields,
                                                            String primaryKeyType, boolean checkExistence, String secondaryKeyType, boolean keyIsDigit,
                                                            Integer startRow, Boolean isPosted, Long orderObject)
            throws IOException, BiffException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<SaleOrderDetail> primaryList = new ArrayList<>();
        List<SaleOrderDetail> secondaryList = new ArrayList<>();

        String primaryKeyColumn = getItemKeyColumn(primaryKeyType);
        String secondaryKeyColumn = getItemKeyColumn(secondaryKeyType);

        WorkbookSettings ws = new WorkbookSettings();
        ws.setEncoding("cp1251");
        ws.setGCDisabled(true);
        Workbook wb = Workbook.getWorkbook(importFile.getInputStream(), ws);
        Sheet sheet = wb.getSheet(0);

        for (int i = startRow - 1; i < sheet.getRows(); i++) {
            Map<String, Object> fieldValues = new HashMap<>();
            for(String field : stringFields) {
                String value = getXLSFieldValue(sheet, i, importColumns.get(field));
                switch (field) {
                    case "idDocumentDetail":
                        fieldValues.put(field, String.valueOf(orderObject) + i);
                        break;
                    case "valueVAT":
                        fieldValues.put(field, VATifAllowed(parseVAT(value)));
                        break;
                    case "barcodeItem":
                        fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value, 7));
                        break;
                    case "idCustomerStock":
                        String idCustomer = readIdCustomer(session, value);
                        fieldValues.put(field, value);
                        fieldValues.put("idCustomer", idCustomer);
                        break;
                    default:
                        fieldValues.put(field, value);
                        break;
                }
            }
            for(String field : bigDecimalFields) {
                BigDecimal value = getXLSBigDecimalFieldValue(sheet, i, importColumns.get(field));
                if (field.equals("dataIndex"))
                    fieldValues.put(field, value == null ? (primaryList.size() + secondaryList.size() + 1) : value.intValue());
                else
                    fieldValues.put(field, value);
            }
            for(String field : dateFields) {
                fieldValues.put(field, getXLSDateFieldValue(sheet, i, importColumns.get(field)));
            }
            
            SaleOrderDetail saleOrderDetail = new SaleOrderDetail(fieldValues, isPosted);

            String primaryKeyColumnValue = getXLSFieldValue(sheet, i, importColumns.get(primaryKeyColumn));
            String secondaryKeyColumnValue = getXLSFieldValue(sheet, i, importColumns.get(secondaryKeyColumn));
            if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, keyIsDigit, session, primaryKeyType, checkExistence))
                primaryList.add(saleOrderDetail);
            else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, keyIsDigit))
                primaryList.add(saleOrderDetail);
        }

        return Arrays.asList(primaryList, secondaryList);
    }

    private List<List<SaleOrderDetail>> importOrdersFromCSV(DataSession session, RawFileData importFile, Map<String, ImportColumnDetail> importColumns,
                                                            List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields, String primaryKeyType, boolean checkExistence,
                                                            String secondaryKeyType, boolean keyIsDigit, Integer startRow, Boolean isPosted, String separator, Long orderObject)
            throws UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, IOException, SQLHandledException {

        List<SaleOrderDetail> primaryList = new ArrayList<>();
        List<SaleOrderDetail> secondaryList = new ArrayList<>();

        String primaryKeyColumn = getItemKeyColumn(primaryKeyType);
        String secondaryKeyColumn = getItemKeyColumn(secondaryKeyType);
        
        BufferedReader br = new BufferedReader(new InputStreamReader(importFile.getInputStream()));
        String line;
        
        List<String[]> valuesList = new ArrayList<>();
        while ((line = br.readLine()) != null) {
            valuesList.add(line.split(separator));              
        }

        for (int count = startRow; count <= valuesList.size(); count++) {
            Map<String, Object> fieldValues = new HashMap<>();
            for(String field : stringFields) {
                String value = getCSVFieldValue(valuesList, importColumns.get(field), count);
                switch (field) {
                    case "idDocumentDetail":
                        fieldValues.put(field, String.valueOf(orderObject) + count);
                        break;
                    case "valueVAT":
                        fieldValues.put(field, VATifAllowed(parseVAT(value)));
                        break;
                    case "barcodeItem":
                        fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value, 7));
                        break;
                    case "idCustomerStock":
                        String idCustomer = readIdCustomer(session, value);
                        fieldValues.put(field, value);
                        fieldValues.put("idCustomer", idCustomer);
                        break;
                    default:
                        fieldValues.put(field, value);
                        break;
                }
            }
            for(String field : bigDecimalFields) {
                BigDecimal value = getCSVBigDecimalFieldValue(valuesList, importColumns.get(field), count);
                if (field.equals("dataIndex"))
                    fieldValues.put(field, value == null ? (primaryList.size() + secondaryList.size() + 1) : value.intValue());
                else
                    fieldValues.put(field, value);
            }
            for(String field : dateFields) {
                fieldValues.put(field, getCSVDateFieldValue(valuesList, importColumns.get(field), count));
            }
            
            SaleOrderDetail saleOrderDetail = new SaleOrderDetail(fieldValues, isPosted);

            String primaryKeyColumnValue = getCSVFieldValue(valuesList, importColumns.get(primaryKeyColumn), count);
            String secondaryKeyColumnValue = getCSVFieldValue(valuesList, importColumns.get(secondaryKeyColumn), count);
            if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, keyIsDigit, session, primaryKeyType, checkExistence))
                primaryList.add(saleOrderDetail);
            else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, keyIsDigit))
                secondaryList.add(saleOrderDetail);

        }

        return Arrays.asList(primaryList, secondaryList);
    }

    private List<List<SaleOrderDetail>> importOrdersFromXLSX(DataSession session, RawFileData importFile, Map<String, ImportColumnDetail> importColumns,
                                                             List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields,
                                                             String primaryKeyType, boolean checkExistence, String secondaryKeyType, boolean keyIsDigit,
                                                             Integer startRow, Boolean isPosted, Long orderObject)
            throws IOException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<SaleOrderDetail> primaryList = new ArrayList<>();
        List<SaleOrderDetail> secondaryList = new ArrayList<>();

        String primaryKeyColumn = getItemKeyColumn(primaryKeyType);
        String secondaryKeyColumn = getItemKeyColumn(secondaryKeyType);
        
        XSSFWorkbook Wb = new XSSFWorkbook(importFile.getInputStream());
        XSSFSheet sheet = Wb.getSheetAt(0);

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {
            Map<String, Object> fieldValues = new HashMap<>();
            for(String field : stringFields) {
                String value = getXLSXFieldValue(sheet, i, importColumns.get(field));
                switch (field) {
                    case "idDocumentDetail":
                        fieldValues.put(field, String.valueOf(orderObject) + i);
                        break;
                    case "valueVAT":
                        fieldValues.put(field, VATifAllowed(parseVAT(value)));
                        break;
                    case "barcodeItem":
                        fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value, 7));
                        break;
                    case "idCustomerStock":
                        String idCustomer = readIdCustomer(session, value);
                        fieldValues.put(field, value);
                        fieldValues.put("idCustomer", idCustomer);
                        break;
                    default:
                        fieldValues.put(field, value);
                        break;
                }
            }
            for(String field : bigDecimalFields) {
                BigDecimal value = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get(field));
                if (field.equals("dataIndex"))
                    fieldValues.put(field, value == null ? (primaryList.size() + secondaryList.size() + 1) : value.intValue());
                else
                    fieldValues.put(field, value);
            }
            for(String field : dateFields) {
               fieldValues.put(field, getXLSXFieldValue(sheet, i, importColumns.get(field))); 
            }                       

            SaleOrderDetail saleOrderDetail = new SaleOrderDetail(fieldValues, isPosted);

            String primaryKeyColumnValue = getXLSXFieldValue(sheet, i, importColumns.get(primaryKeyColumn));
            String secondaryKeyColumnValue = getXLSXFieldValue(sheet, i, importColumns.get(secondaryKeyColumn));
            if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, keyIsDigit, session, primaryKeyType, checkExistence))
                primaryList.add(saleOrderDetail);
            else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, keyIsDigit))
                secondaryList.add(saleOrderDetail);
        }

        return Arrays.asList(primaryList, secondaryList);
    }

    private List<List<SaleOrderDetail>> importOrdersFromDBF(DataSession session, RawFileData importFile, Map<String, ImportColumnDetail> importColumns,
                                                            List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields,
                                                            String primaryKeyType, boolean checkExistence, String secondaryKeyType, boolean keyIsDigit,
                                                            Integer startRow, Boolean isPosted, Long orderObject)
            throws IOException, xBaseJException, ScriptingErrorLog.SemanticErrorException, SQLException, UniversalImportException, SQLHandledException {

        List<SaleOrderDetail> primaryList = new ArrayList<>();
        List<SaleOrderDetail> secondaryList = new ArrayList<>();

        String primaryKeyColumn = getItemKeyColumn(primaryKeyType);
        String secondaryKeyColumn = getItemKeyColumn(secondaryKeyType);

        File tempFile = null;
        DBF file = null;
        try {
            tempFile = File.createTempFile("saleOrder", ".dbf");
            importFile.write(tempFile);

            file = new DBF(tempFile.getPath());
            String charset = getDBFCharset(tempFile);

            int totalRecordCount = file.getRecordCount();

            for (int i = 0; i < startRow - 1; i++) {
                file.read();
            }

            for (int i = startRow - 1; i < totalRecordCount; i++) {
                Map<String, Object> fieldValues = new HashMap<>();

                file.read();

                for (String field : stringFields) {
                    String value = getDBFFieldValue(file, importColumns.get(field), i, charset);
                    switch (field) {
                        case "idDocumentDetail":
                            fieldValues.put(field, String.valueOf(orderObject) + i);
                            break;
                        case "valueVAT":
                            fieldValues.put(field, VATifAllowed(parseVAT(value)));
                            break;
                        case "barcodeItem":
                            fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value, 7));
                            break;
                        case "idCustomerStock":
                            String idCustomer = readIdCustomer(session, value);
                            fieldValues.put(field, value);
                            fieldValues.put("idCustomer", idCustomer);
                            break;
                        default:
                            fieldValues.put(field, value);
                            break;
                    }
                }
                for (String field : bigDecimalFields) {
                    BigDecimal value = getDBFBigDecimalFieldValue(file, importColumns.get(field), i, charset);
                    if (field.equals("dataIndex"))
                        fieldValues.put(field, value == null ? (primaryList.size() + secondaryList.size() + 1) : value.intValue());
                    else
                        fieldValues.put(field, value);
                }
                for (String field : dateFields) {
                    fieldValues.put(field, getDBFDateFieldValue(file, importColumns.get(field), i, charset));
                }

                SaleOrderDetail saleOrderDetail = new SaleOrderDetail(fieldValues, isPosted);

                String primaryKeyColumnValue = getDBFFieldValue(file, importColumns.get(primaryKeyColumn), i, charset);
                String secondaryKeyColumnValue = getDBFFieldValue(file, importColumns.get(secondaryKeyColumn), i, charset);
                if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, keyIsDigit, session, primaryKeyType, checkExistence))
                    primaryList.add(saleOrderDetail);
                else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, keyIsDigit))
                    secondaryList.add(saleOrderDetail);
            }
        } finally {
            if(file != null)
                file.close();
            if(tempFile != null && !tempFile.delete())
                tempFile.deleteOnExit();
        }

        return Arrays.asList(primaryList, secondaryList);
    }

    private String readIdCustomer(DataSession session, String idCustomerStock) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        ObjectValue customerStockObject = idCustomerStock == null ? null : findProperty("stock[STRING[100]]").readClasses(session, new DataObject(idCustomerStock));
        ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : findProperty("legalEntity[Stock]").readClasses(session, (DataObject) customerStockObject));
        return (String) (customerObject == null ? null : findProperty("id[LegalEntity]").read(session, customerObject));
    }

    protected Boolean showField(List<SaleOrderDetail> data, String fieldName) {
        try {

            boolean found = false;
            Field fieldValues = SaleOrderDetail.class.getField("fieldValues");
            for (SaleOrderDetail entry : data) {
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
                Field field = SaleOrderDetail.class.getField(fieldName);

                for (SaleOrderDetail entry : data) {
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