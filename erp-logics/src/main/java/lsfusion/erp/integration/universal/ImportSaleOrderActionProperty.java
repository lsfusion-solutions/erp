package lsfusion.erp.integration.universal;

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import lsfusion.base.IOUtils;
import lsfusion.erp.stock.BarcodeUtils;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.Settings;
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
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;
import java.sql.Date;

public class ImportSaleOrderActionProperty extends ImportDocumentActionProperty {
    private final ClassPropertyInterface orderInterface;

    // Опциональные модули
    ScriptingLogicsModule saleManufacturingPriceLM;

    public ImportSaleOrderActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClassByCompoundName("Sale.Order"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        orderInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataSession session = context.getSession();

            DataObject orderObject = context.getDataKeyValue(orderInterface);

            ObjectValue importTypeObject = getLCP("importTypeOrder").readClasses(session, orderObject);

            boolean disableVolatileStats = Settings.get().isDisableExplicitVolatileStats();

            if (!(importTypeObject instanceof NullValue)) {

                String fileExtension = trim((String) getLCP("captionFileExtensionImportType").read(session, importTypeObject));
                String primaryKeyType = parseKeyType((String) getLCP("namePrimaryKeyTypeImportType").read(session, importTypeObject));
                boolean checkExistence = getLCP("checkExistencePrimaryKeyImportType").read(session, importTypeObject) != null;
                String secondaryKeyType = parseKeyType((String) getLCP("nameSecondaryKeyTypeImportType").read(session, importTypeObject));
                boolean keyIsDigit = getLCP("keyIsDigitImportType").read(session, importTypeObject) != null;
                String csvSeparator = trim((String) getLCP("separatorImportType").read(session, importTypeObject));
                csvSeparator = csvSeparator == null ? ";" : csvSeparator;
                Integer startRow = (Integer) getLCP("startRowImportType").read(session, importTypeObject);
                startRow = startRow == null ? 1 : startRow;
                Boolean isPosted = (Boolean) getLCP("isPostedImportType").read(session, importTypeObject);

                ObjectValue operationObject = getLCP("autoImportOperationImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierObject = getLCP("autoImportSupplierImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierStockObject = getLCP("autoImportSupplierStockImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue customerObject = getLCP("autoImportCustomerImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue customerStockObject = getLCP("autoImportCustomerStockImportType").readClasses(session, (DataObject) importTypeObject);

                Map<String, ImportColumnDetail> importColumns = readImportColumns(session, LM, importTypeObject).get(0);

                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, fileExtension + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                        for (byte[] file : fileList) {

                            makeImport(context.getBL(), session, orderObject, importColumns, file, fileExtension, startRow, isPosted, csvSeparator,
                                    primaryKeyType, checkExistence, secondaryKeyType, keyIsDigit, operationObject, supplierObject, supplierStockObject,
                                    customerObject, customerStockObject, disableVolatileStats);

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
                              byte[] file, String fileExtension, Integer startRow, Boolean isPosted, String csvSeparator, String primaryKeyType,
                              boolean checkExistence, String secondaryKeyType, boolean keyIsDigit, ObjectValue operationObject, ObjectValue supplierObject,
                              ObjectValue supplierStockObject, ObjectValue customerObject, ObjectValue customerStockObject, boolean disableVolatileStats)
            throws ParseException, IOException, SQLException, BiffException, xBaseJException, ScriptingErrorLog.SemanticErrorException, UniversalImportException, SQLHandledException {

        this.saleManufacturingPriceLM = (ScriptingLogicsModule) BL.getModule("SaleManufacturingPrice");

        List<List<SaleOrderDetail>> orderDetailsList = importOrdersFromFile(session, (Integer) orderObject.object,
                importColumns, file, fileExtension, startRow, isPosted, csvSeparator, primaryKeyType, checkExistence, secondaryKeyType, keyIsDigit);

        boolean importResult1 = (orderDetailsList != null && orderDetailsList.size() >= 1) && importOrders(orderDetailsList.get(0),
                BL, session, orderObject, importColumns, primaryKeyType, operationObject, supplierObject, supplierStockObject,
                customerObject, customerStockObject, disableVolatileStats);

        boolean importResult2 = (orderDetailsList != null && orderDetailsList.size() >= 2) && importOrders(orderDetailsList.get(1),
                BL, session, orderObject, importColumns, secondaryKeyType, operationObject, supplierObject, supplierStockObject,
                customerObject, customerStockObject, disableVolatileStats);

        getLAP("formRefresh").execute(session);

        return importResult1 && importResult2;
    }

    public boolean importOrders(List<SaleOrderDetail> orderDetailsList, BusinessLogics BL, DataSession session, 
                                DataObject orderObject, Map<String, ImportColumnDetail> importColumns, String keyType, 
                                ObjectValue operationObject, ObjectValue supplierObject, ObjectValue supplierStockObject,
                                ObjectValue customerObject, ObjectValue customerStockObject, boolean disableVolatileStats)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException, SQLHandledException {

        if (orderDetailsList != null) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(orderDetailsList.size());

            if (showField(orderDetailsList, "numberOrder")) {
                addDataField(props, fields, importColumns, "Sale.numberUserOrder", "numberOrder", orderObject);
                addDataField(props, fields, importColumns, "Sale.numberUserOrder", "numberOrder", orderObject);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).numberOrder);
            }

            if (showField(orderDetailsList, "dateOrder")) {
                addDataField(props, fields, importColumns, "Sale.dateUserOrder", "dateOrder", orderObject);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).dateOrder);
            }

            ImportField idUserOrderDetailField = new ImportField(getLCP("Sale.idUserOrderDetail"));
            ImportKey<?> orderDetailKey = new ImportKey((CustomClass) getClass("Sale.UserOrderDetail"),
                    getLCP("Sale.userOrderDetailId").getMapping(idUserOrderDetailField));
            keys.add(orderDetailKey);
            props.add(new ImportProperty(idUserOrderDetailField, getLCP("Sale.idUserOrderDetail").getMapping(orderDetailKey)));
            props.add(new ImportProperty(orderObject, getLCP("Sale.userOrderUserOrderDetail").getMapping(orderDetailKey)));
            fields.add(idUserOrderDetailField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).idOrderDetail);

            if (operationObject instanceof DataObject)
                props.add(new ImportProperty((DataObject) operationObject, getLCP("Sale.operationUserOrder").getMapping(orderObject)));

            if (supplierObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) supplierObject, getLCP("Sale.supplierUserOrderDetail").getMapping(orderDetailKey)));
                props.add(new ImportProperty((DataObject) supplierObject, getLCP("Sale.supplierUserOrder").getMapping(orderObject)));
            }

            if (supplierStockObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) supplierStockObject, getLCP("Sale.supplierStockUserOrderDetail").getMapping(orderDetailKey)));
                props.add(new ImportProperty((DataObject) supplierStockObject, getLCP("Sale.supplierStockUserOrder").getMapping(orderObject)));
            }

            if (customerObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) customerObject, getLCP("Sale.customerUserOrderDetail").getMapping(orderDetailKey)));
                props.add(new ImportProperty((DataObject) customerObject, getLCP("Sale.customerUserOrder").getMapping(orderObject)));
            }

            if (customerStockObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) customerStockObject, getLCP("Sale.customerStockUserOrderDetail").getMapping(orderDetailKey)));
                props.add(new ImportProperty((DataObject) customerStockObject, getLCP("Sale.customerStockUserOrder").getMapping(orderObject)));
            }

            ImportField idBarcodeSkuField = new ImportField(getLCP("idBarcodeSku"));
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) getClass("Barcode"),
                    getLCP("extBarcodeId").getMapping(idBarcodeSkuField));
            keys.add(barcodeKey);
            barcodeKey.skipKey = true;
            fields.add(idBarcodeSkuField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).idBarcodeSku);

            ImportField idBatchField = new ImportField(getLCP("idBatch"));
            ImportKey<?> batchKey = new ImportKey((CustomClass) getClass("Batch"),
                    getLCP("batchId").getMapping(idBatchField));
            props.add(new ImportProperty(idBatchField, getLCP("Sale.batchUserOrderDetail").getMapping(orderDetailKey),
                    LM.object(getClass("Batch")).getMapping(batchKey)));
            fields.add(idBatchField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).idBatch);

            ImportField dataIndexOrderDetailField = new ImportField(getLCP("Sale.dataIndexUserOrderDetail"));
            props.add(new ImportProperty(dataIndexOrderDetailField, getLCP("Sale.dataIndexUserOrderDetail").getMapping(orderDetailKey)));
            fields.add(dataIndexOrderDetailField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).dataIndex);

            ImportField idItemField = new ImportField(getLCP("idItem"));
            fields.add(idItemField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).idItem);

            String iGroupAggr = getItemKeyGroupAggr(keyType);
            ImportField iField = (keyType == null || keyType.equals("item")) ? idItemField : keyType.equals("barcode") ? idBarcodeSkuField : idBatchField;
            ImportKey<?> itemKey = new ImportKey((CustomClass) getClass("Item"),
                    getLCP(iGroupAggr).getMapping(iField));
            keys.add(itemKey);
            itemKey.skipKey = true;
            props.add(new ImportProperty(iField, getLCP("Sale.skuUserOrderDetail").getMapping(orderDetailKey),
                    LM.object(getClass("Sku")).getMapping(itemKey)));
            props.add(new ImportProperty(iField, getLCP("skuBarcode").getMapping(barcodeKey),
                    LM.object(getClass("Item")).getMapping(itemKey)));

            if (showField(orderDetailsList, "idManufacturer")) {
                ImportField idManufacturerField = new ImportField(getLCP("idManufacturer"));
                ImportKey<?> manufacturerKey = new ImportKey((ConcreteCustomClass) getClass("Manufacturer"),
                        getLCP("manufacturerId").getMapping(idManufacturerField));
                keys.add(manufacturerKey);
                props.add(new ImportProperty(idManufacturerField, getLCP("idManufacturer").getMapping(manufacturerKey)));
                props.add(new ImportProperty(idManufacturerField, getLCP("manufacturerItem").getMapping(itemKey),
                        LM.object(getClass("Manufacturer")).getMapping(manufacturerKey), getReplaceOnlyNull(importColumns, "idManufacturer")));
                fields.add(idManufacturerField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).idManufacturer);
            }

            if (showField(orderDetailsList, "idCustomer")) {
                ImportField idCustomerField = new ImportField(getLCP("idLegalEntity"));
                ImportKey<?> customerKey = new ImportKey((ConcreteCustomClass) getClass("LegalEntity"),
                        getLCP("legalEntityId").getMapping(idCustomerField));
                keys.add(customerKey);
                props.add(new ImportProperty(idCustomerField, getLCP("Sale.customerUserOrder").getMapping(orderObject),
                        LM.object(getClass("LegalEntity")).getMapping(customerKey), getReplaceOnlyNull(importColumns, "idCustomer")));
                fields.add(idCustomerField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).idCustomer);
            }

            if (showField(orderDetailsList, "idCustomerStock")) {
                ImportField idCustomerStockField = new ImportField(getLCP("idStock"));
                ImportKey<?> customerStockKey = new ImportKey((CustomClass) getClass("Stock"),
                        getLCP("stockId").getMapping(idCustomerStockField));
                keys.add(customerStockKey);
                props.add(new ImportProperty(idCustomerStockField, getLCP("Sale.customerStockUserOrder").getMapping(orderObject),
                        LM.object(getClass("Stock")).getMapping(customerStockKey), getReplaceOnlyNull(importColumns, "idCustomerStock")));
                fields.add(idCustomerStockField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).idCustomerStock);
            }

            if (showField(orderDetailsList, "quantity")) {
                addDataField(props, fields, importColumns, "Sale.quantityUserOrderDetail", "quantity", orderDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).quantity);
            }

            if (showField(orderDetailsList, "price")) {
                addDataField(props, fields, importColumns, "Sale.priceUserOrderDetail", "price", orderDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).price);
            }

            if (showField(orderDetailsList, "sum")) {
                addDataField(props, fields, importColumns, "Sale.sumUserOrderDetail", "sum", orderDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).sum);
            }

            if (showField(orderDetailsList, "valueVAT")) {
                ImportField valueVATOrderDetailField = new ImportField(getLCP("Sale.valueVATUserOrderDetail"));
                ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) getClass("Range"),
                        getLCP("valueCurrentVATDefaultValue").getMapping(valueVATOrderDetailField));
                keys.add(VATKey);
                props.add(new ImportProperty(valueVATOrderDetailField, getLCP("Sale.VATUserOrderDetail").getMapping(orderDetailKey),
                        LM.object(getClass("Range")).getMapping(VATKey), getReplaceOnlyNull(importColumns, "valueVAT")));
                fields.add(valueVATOrderDetailField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).valueVAT);
            }

            if (showField(orderDetailsList, "sumVAT")) {
                addDataField(props, fields, importColumns, "Sale.VATSumUserOrderDetail", "sumVAT", orderDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).sumVAT);
            }

            if (showField(orderDetailsList, "invoiceSum")) {
                addDataField(props, fields, importColumns, "Sale.invoiceSumUserOrderDetail", "invoiceSum", orderDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).invoiceSum);
            }

            if ((saleManufacturingPriceLM != null) && showField(orderDetailsList, "manufacturingPrice")) {
                addDataField(saleManufacturingPriceLM, props, fields, importColumns, "Sale.manufacturingPriceUserOrderDetail", "manufacturingPrice", orderDetailKey);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).manufacturingPrice);
            }

            if (showField(orderDetailsList, "isPosted")) {
                addDataField(props, fields, importColumns, "Sale.isPostedUserOrder", "isPosted", orderObject);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).isPosted);
            }

            ImportTable table = new ImportTable(fields, data);

            if(!disableVolatileStats)
                session.pushVolatileStats();
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            String result = session.applyMessage(BL);
            if(!disableVolatileStats)
                session.popVolatileStats();

            return result == null;
        }
        return false;
    }

    public List<List<SaleOrderDetail>> importOrdersFromFile(DataSession session, Integer orderObject, Map<String, ImportColumnDetail> importColumns,
                                                            byte[] file, String fileExtension, Integer startRow, Boolean isPosted, String csvSeparator,
                                                            String primaryKeyType, boolean checkExistence, String secondaryKeyType, boolean keyIsDigit)
            throws ParseException, UniversalImportException, IOException, SQLException, xBaseJException, ScriptingErrorLog.SemanticErrorException, BiffException, SQLHandledException {

        List<List<SaleOrderDetail>> orderDetailsList;

        if (fileExtension.equals("DBF"))
            orderDetailsList = importOrdersFromDBF(session, file, importColumns, primaryKeyType, checkExistence, secondaryKeyType, keyIsDigit, startRow, isPosted, orderObject);
        else if (fileExtension.equals("XLS"))
            orderDetailsList = importOrdersFromXLS(session, file, importColumns, primaryKeyType, checkExistence, secondaryKeyType, keyIsDigit, startRow, isPosted, orderObject);
        else if (fileExtension.equals("XLSX"))
            orderDetailsList = importOrdersFromXLSX(session, file, importColumns, primaryKeyType, checkExistence, secondaryKeyType, keyIsDigit, startRow, isPosted, orderObject);
        else if (fileExtension.equals("CSV"))
            orderDetailsList = importOrdersFromCSV(session, file, importColumns, primaryKeyType, checkExistence, secondaryKeyType, keyIsDigit, startRow, isPosted, csvSeparator, orderObject);
        else
            orderDetailsList = null;

        return orderDetailsList;
    }

    private List<List<SaleOrderDetail>> importOrdersFromXLS(DataSession session, byte[] importFile, Map<String, ImportColumnDetail> importColumns,
                                                            String primaryKeyType, boolean checkExistence, String secondaryKeyType, boolean keyIsDigit, Integer startRow, 
                                                            Boolean isPosted, Integer orderObject)
            throws IOException, BiffException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<SaleOrderDetail> primaryList = new ArrayList<SaleOrderDetail>();
        List<SaleOrderDetail> secondaryList = new ArrayList<SaleOrderDetail>();

        String primaryKeyColumn = getItemKeyColumn(primaryKeyType);
        String secondaryKeyColumn = getItemKeyColumn(secondaryKeyType);
        
        WorkbookSettings ws = new WorkbookSettings();
        ws.setEncoding("cp1251");
        Workbook wb = Workbook.getWorkbook(new ByteArrayInputStream(importFile), ws);
        Sheet sheet = wb.getSheet(0);

        for (int i = startRow - 1; i < sheet.getRows(); i++) {
            String numberOrder = getXLSFieldValue(sheet, i, importColumns.get("numberDocument"));
            Date dateOrder = getXLSDateFieldValue(sheet, i, importColumns.get("dateDocument"));
            String idOrderDetail = String.valueOf(orderObject) + i;
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getXLSFieldValue(sheet, i, importColumns.get("barcodeItem")), 7);
            String idBatch = getXLSFieldValue(sheet, i, importColumns.get("idBatch"));
            Integer dataIndex = Integer.parseInt(getXLSFieldValue(sheet, i, importColumns.get("dataIndex"), String.valueOf(primaryList.size() + secondaryList.size() + 1)));
            String idItem = getXLSFieldValue(sheet, i, importColumns.get("idItem"));
            String manufacturerItem = getXLSFieldValue(sheet, i, importColumns.get("manufacturerItem"));
            String idCustomerStock = getXLSFieldValue(sheet, i, importColumns.get("idCustomerStock"));
            ObjectValue customerStockObject = idCustomerStock == null ? null : getLCP("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : getLCP("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : getLCP("idLegalEntity").read(session, customerObject));
            BigDecimal quantity = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("quantity"));
            BigDecimal price = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("price"));
            BigDecimal sum = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("sum"));
            BigDecimal valueVAT = parseVAT(getXLSFieldValue(sheet, i, importColumns.get("valueVAT")));
            BigDecimal sumVAT = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("sumVAT"));
            BigDecimal invoiceSum = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("invoiceSum"));
            BigDecimal manufacturingPrice = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("manufacturingPrice"));

            SaleOrderDetail saleOrderDetail = new SaleOrderDetail(isPosted, numberOrder, dateOrder, idOrderDetail, barcodeItem, idBatch,
                    dataIndex, idItem, manufacturerItem, idCustomer, idCustomerStock, quantity, price, sum,
                    VATifAllowed(valueVAT), sumVAT, invoiceSum, manufacturingPrice);

            String primaryKeyColumnValue = getXLSFieldValue(sheet, i, importColumns.get(primaryKeyColumn));
            String secondaryKeyColumnValue = getXLSFieldValue(sheet, i, importColumns.get(secondaryKeyColumn));
            if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, keyIsDigit, session, primaryKeyType, checkExistence))
                primaryList.add(saleOrderDetail);
            else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, keyIsDigit))
                primaryList.add(saleOrderDetail);
        }

        return Arrays.asList(primaryList, secondaryList);
    }

    private List<List<SaleOrderDetail>> importOrdersFromCSV(DataSession session, byte[] importFile, Map<String, ImportColumnDetail> importColumns,
                                                            String primaryKeyType, boolean checkExistence, String secondaryKeyType, boolean keyIsDigit, Integer startRow, 
                                                            Boolean isPosted, String csvSeparator, Integer orderObject)
            throws UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, IOException, SQLHandledException {

        List<SaleOrderDetail> primaryList = new ArrayList<SaleOrderDetail>();
        List<SaleOrderDetail> secondaryList = new ArrayList<SaleOrderDetail>();

        String primaryKeyColumn = getItemKeyColumn(primaryKeyType);
        String secondaryKeyColumn = getItemKeyColumn(secondaryKeyType);
        
        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(importFile)));
        String line;
        
        List<String[]> valuesList = new ArrayList<String[]>();
        while ((line = br.readLine()) != null) {
            valuesList.add(line.split(csvSeparator));              
        }

        for (int count = startRow; count <= valuesList.size(); count++) {

            String numberOrder = getCSVFieldValue(valuesList, importColumns.get("numberDocument"), count);
            Date dateOrder = getCSVDateFieldValue(valuesList, importColumns.get("dateDocument"), count);
            String idOrderDetail = String.valueOf(orderObject) + count;
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getCSVFieldValue(valuesList, importColumns.get("barcodeItem"), count), 7);
            String idBatch = getCSVFieldValue(valuesList, importColumns.get("idBatch"), count);
            Integer dataIndex = Integer.parseInt(getCSVFieldValue(valuesList, importColumns.get("idItem"), count, String.valueOf(primaryList.size() + secondaryList.size() + 1)));
            String idItem = getCSVFieldValue(valuesList, importColumns.get("idItem"), count);
            String manufacturerItem = getCSVFieldValue(valuesList, importColumns.get("manufacturerItem"), count);
            String idCustomerStock = getCSVFieldValue(valuesList, importColumns.get("idCustomerStock"), count);
            ObjectValue customerStockObject = idCustomerStock == null ? null : getLCP("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : getLCP("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : getLCP("idLegalEntity").read(session, customerObject));
            BigDecimal quantity = getCSVBigDecimalFieldValue(valuesList, importColumns.get("quantity"), count);
            BigDecimal price = getCSVBigDecimalFieldValue(valuesList, importColumns.get("price"), count);
            BigDecimal sum = getCSVBigDecimalFieldValue(valuesList, importColumns.get("sum"), count);
            BigDecimal valueVAT = parseVAT(getCSVFieldValue(valuesList, importColumns.get("valueVAT"), count));
            BigDecimal sumVAT = getCSVBigDecimalFieldValue(valuesList, importColumns.get("sumVAT"), count);
            BigDecimal invoiceSum = getCSVBigDecimalFieldValue(valuesList, importColumns.get("invoiceSum"), count);
            BigDecimal manufacturingPrice = getCSVBigDecimalFieldValue(valuesList, importColumns.get("manufacturingPrice"), count);

            SaleOrderDetail saleOrderDetail = new SaleOrderDetail(isPosted, numberOrder, dateOrder, idOrderDetail, barcodeItem, idBatch,
                    dataIndex, idItem, manufacturerItem, idCustomer, idCustomerStock, quantity, price, sum,
                    VATifAllowed(valueVAT), sumVAT, invoiceSum, manufacturingPrice);

            String primaryKeyColumnValue = getCSVFieldValue(valuesList, importColumns.get(primaryKeyColumn), count);
            String secondaryKeyColumnValue = getCSVFieldValue(valuesList, importColumns.get(secondaryKeyColumn), count);
            if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, keyIsDigit, session, primaryKeyType, checkExistence))
                primaryList.add(saleOrderDetail);
            else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, keyIsDigit))
                secondaryList.add(saleOrderDetail);

        }

        return Arrays.asList(primaryList, secondaryList);
    }

    private List<List<SaleOrderDetail>> importOrdersFromXLSX(DataSession session, byte[] importFile, Map<String, ImportColumnDetail> importColumns,
                                                             String primaryKeyType, boolean checkExistence, String secondaryKeyType, boolean keyIsDigit, 
                                                             Integer startRow, Boolean isPosted, Integer orderObject)
            throws IOException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<SaleOrderDetail> primaryList = new ArrayList<SaleOrderDetail>();
        List<SaleOrderDetail> secondaryList = new ArrayList<SaleOrderDetail>();

        String primaryKeyColumn = getItemKeyColumn(primaryKeyType);
        String secondaryKeyColumn = getItemKeyColumn(secondaryKeyType);
        
        XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(importFile));
        XSSFSheet sheet = Wb.getSheetAt(0);

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {

            String numberOrder = getXLSXFieldValue(sheet, i, importColumns.get("numberDocument"));
            Date dateOrder = getXLSXDateFieldValue(sheet, i, importColumns.get("dateDocument"));
            String idOrderDetail = String.valueOf(orderObject) + i;
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getXLSXFieldValue(sheet, i, importColumns.get("barcodeItem")), 7);
            String idBatch = getXLSXFieldValue(sheet, i, importColumns.get("idBatch"));
            Integer dataIndex = Integer.parseInt(getXLSXFieldValue(sheet, i, importColumns.get("idItem"), false, String.valueOf(primaryList.size() + secondaryList.size() + 1)));
            String idItem = getXLSXFieldValue(sheet, i, importColumns.get("idItem"));
            String manufacturerItem = getXLSXFieldValue(sheet, i, importColumns.get("manufacturerItem"));
            String idCustomerStock = getXLSXFieldValue(sheet, i, importColumns.get("idCustomerStock"));
            ObjectValue customerStockObject = idCustomerStock == null ? null : getLCP("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : getLCP("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : getLCP("idLegalEntity").read(session, customerObject));
            BigDecimal quantity = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("quantity"));
            BigDecimal price = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("price"));
            BigDecimal sum = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("sum"));
            BigDecimal valueVAT = parseVAT(getXLSXFieldValue(sheet, i, importColumns.get("valueVAT")));
            BigDecimal sumVAT = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("sumVAT"));
            BigDecimal invoiceSum = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("invoiceSum"));
            BigDecimal manufacturingPrice = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("manufacturingPrice"));

            SaleOrderDetail saleOrderDetail = new SaleOrderDetail(isPosted, numberOrder, dateOrder, idOrderDetail, barcodeItem, idBatch,
                    dataIndex, idItem, manufacturerItem, idCustomer, idCustomerStock, quantity, price, sum,
                    VATifAllowed(valueVAT), sumVAT, invoiceSum, manufacturingPrice);

            String primaryKeyColumnValue = getXLSXFieldValue(sheet, i, importColumns.get(primaryKeyColumn));
            String secondaryKeyColumnValue = getXLSXFieldValue(sheet, i, importColumns.get(secondaryKeyColumn));
            if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, keyIsDigit, session, primaryKeyType, checkExistence))
                primaryList.add(saleOrderDetail);
            else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, keyIsDigit))
                secondaryList.add(saleOrderDetail);
        }

        return Arrays.asList(primaryList, secondaryList);
    }

    private List<List<SaleOrderDetail>> importOrdersFromDBF(DataSession session, byte[] importFile, Map<String, ImportColumnDetail> importColumns,
                                                            String primaryKeyType, boolean checkExistence, String secondaryKeyType, boolean keyIsDigit, 
                                                            Integer startRow, Boolean isPosted, Integer orderObject)
            throws IOException, xBaseJException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, UniversalImportException, SQLHandledException {

        List<SaleOrderDetail> primaryList = new ArrayList<SaleOrderDetail>();
        List<SaleOrderDetail> secondaryList = new ArrayList<SaleOrderDetail>();

        String primaryKeyColumn = getItemKeyColumn(primaryKeyType);
        String secondaryKeyColumn = getItemKeyColumn(secondaryKeyType);

        File tempFile = File.createTempFile("saleOrder", ".dbf");
        IOUtils.putFileBytes(tempFile, importFile);

        DBF file = new DBF(tempFile.getPath());
        String charset = getDBFCharset(tempFile);

        int totalRecordCount = file.getRecordCount();

        for (int i = startRow - 1; i < totalRecordCount; i++) {

            file.read();

            String numberOrder = getDBFFieldValue(file, importColumns.get("numberDocument"), i, charset);
            Date dateOrder = getDBFDateFieldValue(file, importColumns.get("dateDocument"), i, charset);
            String idOrderDetail = String.valueOf(orderObject) + i;
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getDBFFieldValue(file, importColumns.get("barcodeItem"), i, charset), 7);
            String idBatch = getDBFFieldValue(file, importColumns.get("idBatch"), i, charset);
            Integer dataIndex = getDBFBigDecimalFieldValue(file, importColumns.get("dataIndex"), i, charset, new BigDecimal(primaryList.size() + secondaryList.size() + 1)).intValue();
            String idItem = getDBFFieldValue(file, importColumns.get("idItem"), i, charset);
            String manufacturerItem = getDBFFieldValue(file, importColumns.get("manufacturerItem"), i, charset);
            String idCustomerStock = getDBFFieldValue(file, importColumns.get("idCustomerStock"), i, charset);
            ObjectValue customerStockObject = idCustomerStock == null ? null : getLCP("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : getLCP("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : getLCP("idLegalEntity").read(session, customerObject));
            BigDecimal quantity = getDBFBigDecimalFieldValue(file, importColumns.get("quantity"), i, charset);
            BigDecimal price = getDBFBigDecimalFieldValue(file, importColumns.get("price"), i, charset);
            BigDecimal sum = getDBFBigDecimalFieldValue(file, importColumns.get("sum"), i, charset);
            BigDecimal valueVAT = parseVAT(getDBFFieldValue(file, importColumns.get("valueVAT"), i, charset));
            BigDecimal sumVAT = getDBFBigDecimalFieldValue(file, importColumns.get("sumVAT"), i, charset);
            BigDecimal invoiceSum = getDBFBigDecimalFieldValue(file, importColumns.get("invoiceSum"), i, charset);
            BigDecimal manufacturingPrice = getDBFBigDecimalFieldValue(file, importColumns.get("manufacturingPrice"), i, charset);

            SaleOrderDetail saleOrderDetail = new SaleOrderDetail(isPosted, numberOrder, dateOrder, idOrderDetail, barcodeItem, idBatch,
                    dataIndex, idItem, manufacturerItem, idCustomer, idCustomerStock, quantity, price, sum, VATifAllowed(valueVAT), sumVAT,
                    invoiceSum, manufacturingPrice);

            String primaryKeyColumnValue = getDBFFieldValue(file, importColumns.get(primaryKeyColumn), i, charset);
            String secondaryKeyColumnValue = getDBFFieldValue(file, importColumns.get(secondaryKeyColumn), i, charset);
            if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, keyIsDigit, session, primaryKeyType, checkExistence))
                primaryList.add(saleOrderDetail);
            else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, keyIsDigit))
                secondaryList.add(saleOrderDetail);
        }
        file.close();
        tempFile.delete();

        return Arrays.asList(primaryList, secondaryList);
    }

    private Boolean showField(List<SaleOrderDetail> data, String fieldName) {
        try {
            Field field = SaleOrderDetail.class.getField(fieldName);

            for (SaleOrderDetail aData : data) {
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