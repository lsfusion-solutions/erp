package lsfusion.erp.integration.universal;

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import lsfusion.base.IOUtils;
import lsfusion.erp.stock.BarcodeUtils;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
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
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {

        try {

            DataSession session = context.getSession();

            DataObject orderObject = context.getDataKeyValue(orderInterface);

            ObjectValue importTypeObject = getLCP("importTypeOrder").readClasses(session, orderObject);

            if (!(importTypeObject instanceof NullValue)) {

                String fileExtension = trim((String) getLCP("captionFileExtensionImportType").read(session, importTypeObject));
                String primaryKeyType = parseKeyType((String) getLCP("namePrimaryKeyTypeImportType").read(session, importTypeObject));
                String secondaryKeyType = parseKeyType((String) getLCP("nameSecondaryKeyTypeImportType").read(session, importTypeObject));
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

                Map<String, ImportColumnDetail> importColumns = readImportColumns(session, LM, importTypeObject);

                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, fileExtension + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                        for (byte[] file : fileList) {

                            makeImport(context.getBL(), session, orderObject, importColumns, file, fileExtension, startRow, isPosted, csvSeparator,
                                    primaryKeyType, secondaryKeyType, operationObject, supplierObject, supplierStockObject,
                                    customerObject, customerStockObject);

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
                              String secondaryKeyType, ObjectValue operationObject, ObjectValue supplierObject,
                              ObjectValue supplierStockObject, ObjectValue customerObject, ObjectValue customerStockObject)
            throws ParseException, IOException, SQLException, BiffException, xBaseJException, ScriptingErrorLog.SemanticErrorException, UniversalImportException {

        this.saleManufacturingPriceLM = (ScriptingLogicsModule) BL.getModule("SaleManufacturingPrice");

        List<List<SaleOrderDetail>> orderDetailsList = importOrdersFromFile(session, (Integer) orderObject.object,
                importColumns, file, fileExtension, startRow, isPosted, csvSeparator, primaryKeyType, secondaryKeyType);

        boolean importResult1 = (orderDetailsList != null && orderDetailsList.size() >= 1) && importOrders(orderDetailsList.get(0),
                BL, session, orderObject, importColumns, primaryKeyType, operationObject, supplierObject, supplierStockObject,
                customerObject, customerStockObject);

        boolean importResult2 = (orderDetailsList != null && orderDetailsList.size() >= 2) && importOrders(orderDetailsList.get(1),
                BL, session, orderObject, importColumns, secondaryKeyType, operationObject, supplierObject, supplierStockObject,
                customerObject, customerStockObject);

        getLAP("formRefresh").execute(session);

        return importResult1 && importResult2;
    }

    public boolean importOrders(List<SaleOrderDetail> orderDetailsList, BusinessLogics BL, DataSession session, 
                                DataObject orderObject, Map<String, ImportColumnDetail> importColumns, String keyType, 
                                ObjectValue operationObject, ObjectValue supplierObject, ObjectValue supplierStockObject,
                                ObjectValue customerObject, ObjectValue customerStockObject)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException {

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
            ImportKey<?> orderDetailKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Sale.UserOrderDetail"),
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
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Barcode"),
                    getLCP("extBarcodeId").getMapping(idBarcodeSkuField));
            keys.add(barcodeKey);
            barcodeKey.skipKey = true;
            fields.add(idBarcodeSkuField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).idBarcodeSku);

            ImportField idBatchField = new ImportField(getLCP("idBatch"));
            ImportKey<?> batchKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Batch"),
                    getLCP("batchId").getMapping(idBatchField));
            props.add(new ImportProperty(idBatchField, getLCP("Sale.batchUserOrderDetail").getMapping(orderDetailKey),
                    LM.object(LM.findClassByCompoundName("Batch")).getMapping(batchKey)));
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

            String iGroupAggr = (keyType == null || keyType.equals("item")) ? "itemId" : keyType.equals("barcode") ? "skuIdBarcode" : "skuBatchId";
            ImportField iField = (keyType == null || keyType.equals("item")) ? idItemField : keyType.equals("barcode") ? idBarcodeSkuField : idBatchField;
            ImportKey<?> itemKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Item"),
                    getLCP(iGroupAggr).getMapping(iField));
            keys.add(itemKey);
            itemKey.skipKey = true;
            props.add(new ImportProperty(iField, getLCP("Sale.skuUserOrderDetail").getMapping(orderDetailKey),
                    LM.object(LM.findClassByCompoundName("Sku")).getMapping(itemKey)));
            props.add(new ImportProperty(iField, getLCP("skuBarcode").getMapping(barcodeKey),
                    LM.object(LM.findClassByCompoundName("Item")).getMapping(itemKey)));

            if (showField(orderDetailsList, "idManufacturer")) {
                ImportField idManufacturerField = new ImportField(getLCP("idManufacturer"));
                ImportKey<?> manufacturerKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Manufacturer"),
                        getLCP("manufacturerId").getMapping(idManufacturerField));
                keys.add(manufacturerKey);
                props.add(new ImportProperty(idManufacturerField, getLCP("idManufacturer").getMapping(manufacturerKey)));
                props.add(new ImportProperty(idManufacturerField, getLCP("manufacturerItem").getMapping(itemKey),
                        LM.object(LM.findClassByCompoundName("Manufacturer")).getMapping(manufacturerKey), getReplaceOnlyNull(importColumns, "idManufacturer")));
                fields.add(idManufacturerField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).idManufacturer);
            }

            if (showField(orderDetailsList, "idCustomer")) {
                ImportField idCustomerField = new ImportField(getLCP("idLegalEntity"));
                ImportKey<?> customerKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("LegalEntity"),
                        getLCP("legalEntityId").getMapping(idCustomerField));
                keys.add(customerKey);
                props.add(new ImportProperty(idCustomerField, getLCP("Sale.customerUserOrder").getMapping(orderObject),
                        LM.object(LM.findClassByCompoundName("LegalEntity")).getMapping(customerKey), getReplaceOnlyNull(importColumns, "idCustomer")));
                fields.add(idCustomerField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).idCustomer);
            }

            if (showField(orderDetailsList, "idCustomerStock")) {
                ImportField idCustomerStockField = new ImportField(getLCP("idStock"));
                ImportKey<?> customerStockKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Stock"),
                        getLCP("stockId").getMapping(idCustomerStockField));
                keys.add(customerStockKey);
                props.add(new ImportProperty(idCustomerStockField, getLCP("Sale.customerStockUserOrder").getMapping(orderObject),
                        LM.object(LM.findClassByCompoundName("Stock")).getMapping(customerStockKey), getReplaceOnlyNull(importColumns, "idCustomerStock")));
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
                ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Range"),
                        getLCP("valueCurrentVATDefaultValue").getMapping(valueVATOrderDetailField));
                keys.add(VATKey);
                props.add(new ImportProperty(valueVATOrderDetailField, getLCP("Sale.VATUserOrderDetail").getMapping(orderDetailKey),
                        LM.object(LM.findClassByCompoundName("Range")).getMapping(VATKey), getReplaceOnlyNull(importColumns, "valueVAT")));
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

            session.sql.pushVolatileStats(null);
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            String result = session.applyMessage(BL);
            session.sql.popVolatileStats(null);
            session.close();

            return result == null;
        }
        return false;
    }

    public List<List<SaleOrderDetail>> importOrdersFromFile(DataSession session, Integer orderObject, Map<String, ImportColumnDetail> importColumns,
                                                            byte[] file, String fileExtension, Integer startRow, Boolean isPosted,
                                                            String csvSeparator, String primaryKeyType, String secondaryKeyType)
            throws ParseException, UniversalImportException, IOException, SQLException, xBaseJException, ScriptingErrorLog.SemanticErrorException, BiffException {

        List<List<SaleOrderDetail>> orderDetailsList;

        String primaryKeyColumn = getKeyColumn(primaryKeyType);
        String secondaryKeyColumn = getKeyColumn(secondaryKeyType);

        if (fileExtension.equals("DBF"))
            orderDetailsList = importOrdersFromDBF(session, file, importColumns, primaryKeyColumn, secondaryKeyColumn, startRow, isPosted, orderObject);
        else if (fileExtension.equals("XLS"))
            orderDetailsList = importOrdersFromXLS(session, file, importColumns, primaryKeyColumn, secondaryKeyColumn, startRow, isPosted, orderObject);
        else if (fileExtension.equals("XLSX"))
            orderDetailsList = importOrdersFromXLSX(session, file, importColumns, primaryKeyColumn, secondaryKeyColumn, startRow, isPosted, orderObject);
        else if (fileExtension.equals("CSV"))
            orderDetailsList = importOrdersFromCSV(session, file, importColumns, primaryKeyColumn, secondaryKeyColumn, startRow, isPosted, csvSeparator, orderObject);
        else
            orderDetailsList = null;

        return orderDetailsList;
    }

    private List<List<SaleOrderDetail>> importOrdersFromXLS(DataSession session, byte[] importFile, Map<String, ImportColumnDetail> importColumns,
                                                            String primaryKeyColumn, String secondaryKeyColumn, Integer startRow, Boolean isPosted, Integer orderObject)
            throws IOException, BiffException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<SaleOrderDetail> primaryList = new ArrayList<SaleOrderDetail>();
        List<SaleOrderDetail> secondaryList = new ArrayList<SaleOrderDetail>();

        WorkbookSettings ws = new WorkbookSettings();
        ws.setEncoding("cp1251");
        Workbook wb = Workbook.getWorkbook(new ByteArrayInputStream(importFile), ws);
        Sheet sheet = wb.getSheet(0);

        for (int i = startRow - 1; i < sheet.getRows(); i++) {
            String numberOrder = getXLSFieldValue(sheet, i, importColumns.get("numberDocument"));
            Date dateOrder = getXLSDateFieldValue(sheet, i, importColumns.get("dateDocument"));
            String idOrderDetail = String.valueOf(orderObject) + i;
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getXLSFieldValue(sheet, i, importColumns.get("barcodeItem")));
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
            BigDecimal valueVAT = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("valueVAT"));
            BigDecimal sumVAT = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("sumVAT"));
            BigDecimal invoiceSum = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("invoiceSum"));
            BigDecimal manufacturingPrice = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("manufacturingPrice"));

            SaleOrderDetail saleOrderDetail = new SaleOrderDetail(isPosted, numberOrder, dateOrder, idOrderDetail, barcodeItem, idBatch,
                    dataIndex, idItem, manufacturerItem, idCustomer, idCustomerStock, quantity, price, sum,
                    VATifAllowed(valueVAT), sumVAT, invoiceSum, manufacturingPrice);

            String primaryKeyColumnValue = getXLSFieldValue(sheet, i, importColumns.get(primaryKeyColumn));
            String secondaryKeyColumnValue = getXLSFieldValue(sheet, i, importColumns.get(secondaryKeyColumn));
            if (primaryKeyColumnValue != null && !primaryKeyColumnValue.isEmpty())
                primaryList.add(saleOrderDetail);
            else if (secondaryKeyColumnValue != null && !secondaryKeyColumnValue.isEmpty())
                primaryList.add(saleOrderDetail);
        }

        return Arrays.asList(primaryList, secondaryList);
    }

    private List<List<SaleOrderDetail>> importOrdersFromCSV(DataSession session, byte[] importFile, Map<String, ImportColumnDetail> importColumns,
                                                            String primaryKeyColumn, String secondaryKeyColumn, Integer startRow, Boolean isPosted,
                                                            String csvSeparator, Integer orderObject)
            throws UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, IOException {

        List<SaleOrderDetail> primaryList = new ArrayList<SaleOrderDetail>();
        List<SaleOrderDetail> secondaryList = new ArrayList<SaleOrderDetail>();

        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(importFile)));
        String line;
        int count = 0;

        while ((line = br.readLine()) != null) {

            count++;

            if (count >= startRow) {

                String[] values = line.split(csvSeparator);

                String numberOrder = getCSVFieldValue(values, importColumns.get("numberDocument"), count);
                Date dateOrder = getCSVDateFieldValue(values, importColumns.get("dateDocument"), count);
                String idOrderDetail = String.valueOf(orderObject) + count;
                String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getCSVFieldValue(values, importColumns.get("barcodeItem"), count));
                String idBatch = getCSVFieldValue(values, importColumns.get("idBatch"), count);
                Integer dataIndex = Integer.parseInt(getCSVFieldValue(values, importColumns.get("idItem"), count, String.valueOf(primaryList.size() + secondaryList.size() + 1)));
                String idItem = getCSVFieldValue(values, importColumns.get("idItem"), count);
                String manufacturerItem = getCSVFieldValue(values, importColumns.get("manufacturerItem"), count);
                String idCustomerStock = getCSVFieldValue(values, importColumns.get("idCustomerStock"), count);
                ObjectValue customerStockObject = idCustomerStock == null ? null : getLCP("stockId").readClasses(session, new DataObject(idCustomerStock));
                ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : getLCP("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
                String idCustomer = (String) (customerObject == null ? null : getLCP("idLegalEntity").read(session, customerObject));
                BigDecimal quantity = getCSVBigDecimalFieldValue(values, importColumns.get("quantity"), count);
                BigDecimal price = getCSVBigDecimalFieldValue(values, importColumns.get("price"), count);
                BigDecimal sum = getCSVBigDecimalFieldValue(values, importColumns.get("sum"), count);
                BigDecimal valueVAT = getCSVBigDecimalFieldValue(values, importColumns.get("valueVAT"), count);
                BigDecimal sumVAT = getCSVBigDecimalFieldValue(values, importColumns.get("sumVAT"), count);
                BigDecimal invoiceSum = getCSVBigDecimalFieldValue(values, importColumns.get("invoiceSum"), count);
                BigDecimal manufacturingPrice = getCSVBigDecimalFieldValue(values, importColumns.get("manufacturingPrice"), count);

                SaleOrderDetail saleOrderDetail = new SaleOrderDetail(isPosted, numberOrder, dateOrder, idOrderDetail, barcodeItem, idBatch,
                        dataIndex, idItem, manufacturerItem, idCustomer, idCustomerStock, quantity, price, sum,
                        VATifAllowed(valueVAT), sumVAT, invoiceSum, manufacturingPrice);

                String primaryKeyColumnValue = getCSVFieldValue(values, importColumns.get(primaryKeyColumn), count);
                String secondaryKeyColumnValue = getCSVFieldValue(values, importColumns.get(secondaryKeyColumn), count);
                if (primaryKeyColumnValue != null && !primaryKeyColumnValue.isEmpty())
                    primaryList.add(saleOrderDetail);
                else if (secondaryKeyColumnValue != null && !secondaryKeyColumnValue.isEmpty())
                    secondaryList.add(saleOrderDetail);
            }
        }

        return Arrays.asList(primaryList, secondaryList);
    }

    private List<List<SaleOrderDetail>> importOrdersFromXLSX(DataSession session, byte[] importFile, Map<String, ImportColumnDetail> importColumns,
                                                             String primaryKeyColumn, String secondaryKeyColumn, Integer startRow,
                                                             Boolean isPosted, Integer orderObject)
            throws IOException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<SaleOrderDetail> primaryList = new ArrayList<SaleOrderDetail>();
        List<SaleOrderDetail> secondaryList = new ArrayList<SaleOrderDetail>();

        XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(importFile));
        XSSFSheet sheet = Wb.getSheetAt(0);

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {

            String numberOrder = getXLSXFieldValue(sheet, i, importColumns.get("numberDocument"));
            Date dateOrder = getXLSXDateFieldValue(sheet, i, importColumns.get("dateDocument"));
            String idOrderDetail = String.valueOf(orderObject) + i;
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getXLSXFieldValue(sheet, i, importColumns.get("barcodeItem")));
            String idBatch = getXLSXFieldValue(sheet, i, importColumns.get("idBatch"));
            Integer dataIndex = Integer.parseInt(getXLSXFieldValue(sheet, i, importColumns.get("idItem"), String.valueOf(primaryList.size() + secondaryList.size() + 1)));
            String idItem = getXLSXFieldValue(sheet, i, importColumns.get("idItem"));
            String manufacturerItem = getXLSXFieldValue(sheet, i, importColumns.get("manufacturerItem"));
            String idCustomerStock = getXLSXFieldValue(sheet, i, importColumns.get("idCustomerStock"));
            ObjectValue customerStockObject = idCustomerStock == null ? null : getLCP("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : getLCP("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : getLCP("idLegalEntity").read(session, customerObject));
            BigDecimal quantity = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("quantity"));
            BigDecimal price = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("price"));
            BigDecimal sum = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("sum"));
            BigDecimal valueVAT = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("valueVAT"));
            BigDecimal sumVAT = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("sumVAT"));
            BigDecimal invoiceSum = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("invoiceSum"));
            BigDecimal manufacturingPrice = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("manufacturingPrice"));

            SaleOrderDetail saleOrderDetail = new SaleOrderDetail(isPosted, numberOrder, dateOrder, idOrderDetail, barcodeItem, idBatch,
                    dataIndex, idItem, manufacturerItem, idCustomer, idCustomerStock, quantity, price, sum,
                    VATifAllowed(valueVAT), sumVAT, invoiceSum, manufacturingPrice);

            String primaryKeyColumnValue = getXLSXFieldValue(sheet, i, importColumns.get(primaryKeyColumn));
            String secondaryKeyColumnValue = getXLSXFieldValue(sheet, i, importColumns.get(secondaryKeyColumn));
            if (primaryKeyColumnValue != null && !primaryKeyColumnValue.isEmpty())
                primaryList.add(saleOrderDetail);
            else if (secondaryKeyColumnValue != null && !secondaryKeyColumnValue.isEmpty())
                secondaryList.add(saleOrderDetail);
        }

        return Arrays.asList(primaryList, secondaryList);
    }

    private List<List<SaleOrderDetail>> importOrdersFromDBF(DataSession session, byte[] importFile, Map<String, ImportColumnDetail> importColumns,
                                                            String primaryKeyColumn, String secondaryKeyColumn, Integer startRow,
                                                            Boolean isPosted, Integer orderObject)
            throws IOException, xBaseJException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, UniversalImportException {

        List<SaleOrderDetail> primaryList = new ArrayList<SaleOrderDetail>();
        List<SaleOrderDetail> secondaryList = new ArrayList<SaleOrderDetail>();

        File tempFile = File.createTempFile("dutiesTNVED", ".dbf");
        IOUtils.putFileBytes(tempFile, importFile);

        DBF file = new DBF(tempFile.getPath());
        String charset = getDBFCharset(tempFile);

        int totalRecordCount = file.getRecordCount();

        for (int i = startRow - 1; i < totalRecordCount; i++) {

            file.read();

            String numberOrder = getDBFFieldValue(file, importColumns.get("numberDocument"), i);
            Date dateOrder = getDBFDateFieldValue(file, importColumns.get("dateDocument"), i);
            String idOrderDetail = String.valueOf(orderObject) + i;
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getDBFFieldValue(file, importColumns.get("barcodeItem"), i));
            String idBatch = getDBFFieldValue(file, importColumns.get("idBatch"), i);
            Integer dataIndex = getDBFBigDecimalFieldValue(file, importColumns.get("dataIndex"), i, charset, String.valueOf(primaryList.size() + secondaryList.size() + 1)).intValue();
            String idItem = getDBFFieldValue(file, importColumns.get("idItem"), i);
            String manufacturerItem = getDBFFieldValue(file, importColumns.get("manufacturerItem"), i);
            String idCustomerStock = getDBFFieldValue(file, importColumns.get("idCustomerStock"), i);
            ObjectValue customerStockObject = idCustomerStock == null ? null : getLCP("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : getLCP("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : getLCP("idLegalEntity").read(session, customerObject));
            BigDecimal quantity = getDBFBigDecimalFieldValue(file, importColumns.get("quantity"), i);
            BigDecimal price = getDBFBigDecimalFieldValue(file, importColumns.get("price"), i);
            BigDecimal sum = getDBFBigDecimalFieldValue(file, importColumns.get("sum"), i);
            BigDecimal valueVAT = getDBFBigDecimalFieldValue(file, importColumns.get("valueVAT"), i);
            BigDecimal sumVAT = getDBFBigDecimalFieldValue(file, importColumns.get("sumVAT"), i);
            BigDecimal invoiceSum = getDBFBigDecimalFieldValue(file, importColumns.get("invoiceSum"), i);
            BigDecimal manufacturingPrice = getDBFBigDecimalFieldValue(file, importColumns.get("manufacturingPrice"), i);

            SaleOrderDetail saleOrderDetail = new SaleOrderDetail(isPosted, numberOrder, dateOrder, idOrderDetail, barcodeItem, idBatch,
                    dataIndex, idItem, manufacturerItem, idCustomer, idCustomerStock, quantity, price, sum, VATifAllowed(valueVAT), sumVAT,
                    invoiceSum, manufacturingPrice);

            String primaryKeyColumnValue = getDBFFieldValue(file, importColumns.get(primaryKeyColumn), i);
            String secondaryKeyColumnValue = getDBFFieldValue(file, importColumns.get(secondaryKeyColumn), i);
            if (primaryKeyColumnValue != null && !primaryKeyColumnValue.isEmpty())
                primaryList.add(saleOrderDetail);
            else if (secondaryKeyColumnValue != null && !secondaryKeyColumnValue.isEmpty())
                secondaryList.add(saleOrderDetail);
        }
        file.close();

        return Arrays.asList(primaryList, secondaryList);
    }

    private Boolean showField(List<SaleOrderDetail> data, String fieldName) {
        try {
            Field field = SaleOrderDetail.class.getField(fieldName);

            for (int i = 0; i < data.size(); i++) {
                if (field.get(data.get(i)) != null)
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