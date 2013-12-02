package lsfusion.erp.integration.universal;

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import lsfusion.base.IOUtils;
import lsfusion.erp.stock.BarcodeUtils;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.integration.*;
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

            ObjectValue importTypeObject = LM.findLCPByCompoundName("importTypeOrder").readClasses(session, orderObject);

            if (!(importTypeObject instanceof NullValue)) {

                String fileExtension = trim((String) LM.findLCPByCompoundName("captionFileExtensionImportType").read(session, importTypeObject));
                String primaryKeyType = parseKeyType((String) LM.findLCPByCompoundName("namePrimaryKeyTypeImportType").read(session, importTypeObject));               
                String secondaryKeyType = parseKeyType((String) LM.findLCPByCompoundName("nameSecondaryKeyTypeImportType").read(session, importTypeObject));
                String csvSeparator = trim((String) LM.findLCPByCompoundName("separatorImportType").read(session, importTypeObject));
                csvSeparator = csvSeparator == null ? ";" : csvSeparator;
                Integer startRow = (Integer) LM.findLCPByCompoundName("startRowImportType").read(session, importTypeObject);
                startRow = startRow == null ? 1 : startRow;
                Boolean isPosted = (Boolean) LM.findLCPByCompoundName("isPostedImportType").read(session, importTypeObject);
                
                ObjectValue operationObject = LM.findLCPByCompoundName("autoImportOperationImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierObject = LM.findLCPByCompoundName("autoImportSupplierImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierStockObject = LM.findLCPByCompoundName("autoImportSupplierStockImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue customerObject = LM.findLCPByCompoundName("autoImportCustomerImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue customerStockObject = LM.findLCPByCompoundName("autoImportCustomerStockImportType").readClasses(session, (DataObject) importTypeObject);

                Map<String, String[]> importColumns = readImportColumns(context, LM, importTypeObject);

                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, fileExtension + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                        for (byte[] file : fileList) {
                            
                            makeImport(context, orderObject, importColumns, file, fileExtension, startRow, isPosted, csvSeparator,
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
        }
    }

    public boolean makeImport(ExecutionContext context, DataObject orderObject, Map<String, String[]> importColumns,
                              byte[] file, String fileExtension, Integer startRow, Boolean isPosted, String csvSeparator, String primaryKeyType,
                              String secondaryKeyType, ObjectValue operationObject, ObjectValue supplierObject,
                              ObjectValue supplierStockObject, ObjectValue customerObject, ObjectValue customerStockObject) 
            throws ParseException, IOException, SQLException, BiffException, xBaseJException, ScriptingErrorLog.SemanticErrorException {

        this.saleManufacturingPriceLM = (ScriptingLogicsModule) context.getBL().getModule("SaleManufacturingPrice");
        
        List<List<SaleOrderDetail>> orderDetailsList = importOrdersFromFile(context.getSession(), (Integer) orderObject.object,
                importColumns, file, fileExtension, startRow, isPosted, csvSeparator, primaryKeyType, secondaryKeyType);

        boolean importResult1 = (orderDetailsList != null && orderDetailsList.size() >= 1) && importOrders(orderDetailsList.get(0),
                context, orderObject, primaryKeyType, operationObject, supplierObject, supplierStockObject, customerObject,
                customerStockObject);

        boolean importResult2 = (orderDetailsList != null && orderDetailsList.size() >= 2) && importOrders(orderDetailsList.get(1),
                context, orderObject, secondaryKeyType, operationObject, supplierObject, supplierStockObject, customerObject,
                customerStockObject);

        LM.findLAPByCompoundName("formRefresh").execute(context);
        
        return importResult1 && importResult2;
    }
    
    public boolean importOrders(List<SaleOrderDetail> orderDetailsList, ExecutionContext context, DataObject orderObject,
                                String keyType, ObjectValue operationObject, ObjectValue supplierObject, ObjectValue supplierStockObject,
                                ObjectValue customerObject, ObjectValue customerStockObject)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException {

        if (orderDetailsList != null) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(orderDetailsList.size());

            if (showField(orderDetailsList, "numberOrder")) {
                ImportField numberOrderField = new ImportField(LM.findLCPByCompoundName("Sale.numberOrder"));
                props.add(new ImportProperty(numberOrderField, LM.findLCPByCompoundName("Sale.numberOrder").getMapping(orderObject)));
                fields.add(numberOrderField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).numberOrder);
            }

            ImportField idUserOrderDetailField = new ImportField(LM.findLCPByCompoundName("Sale.idUserOrderDetail"));
            ImportKey<?> orderDetailKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Sale.UserOrderDetail"),
                    LM.findLCPByCompoundName("Sale.userOrderDetailId").getMapping(idUserOrderDetailField));
            keys.add(orderDetailKey);
            props.add(new ImportProperty(idUserOrderDetailField, LM.findLCPByCompoundName("Sale.idUserOrderDetail").getMapping(orderDetailKey)));
            props.add(new ImportProperty(orderObject, LM.findLCPByCompoundName("Sale.orderOrderDetail").getMapping(orderDetailKey)));
            fields.add(idUserOrderDetailField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).idOrderDetail);

            if (operationObject instanceof DataObject)
                props.add(new ImportProperty((DataObject) operationObject, LM.findLCPByCompoundName("Sale.operationOrder").getMapping(orderObject)));

            if (supplierObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) supplierObject, LM.findLCPByCompoundName("Sale.supplierOrderDetail").getMapping(orderDetailKey)));
                props.add(new ImportProperty((DataObject) supplierObject, LM.findLCPByCompoundName("Sale.supplierOrder").getMapping(orderObject)));
            }

            if (supplierStockObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) supplierStockObject, LM.findLCPByCompoundName("Sale.supplierStockOrderDetail").getMapping(orderDetailKey)));
                props.add(new ImportProperty((DataObject) supplierStockObject, LM.findLCPByCompoundName("Sale.supplierStockOrder").getMapping(orderObject)));
            }

            if (customerObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) customerObject, LM.findLCPByCompoundName("Sale.customerOrderDetail").getMapping(orderDetailKey)));
                props.add(new ImportProperty((DataObject) customerObject, LM.findLCPByCompoundName("Sale.customerOrder").getMapping(orderObject)));
            }

            if (customerStockObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) customerStockObject, LM.findLCPByCompoundName("Sale.customerStockOrderDetail").getMapping(orderDetailKey)));
                props.add(new ImportProperty((DataObject) customerStockObject, LM.findLCPByCompoundName("Sale.customerStockOrder").getMapping(orderObject)));
            }

            ImportField idBarcodeSkuField = new ImportField(LM.findLCPByCompoundName("idBarcodeSku"));
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Barcode"),
                    LM.findLCPByCompoundName("extBarcodeId").getMapping(idBarcodeSkuField));
            keys.add(barcodeKey);
            barcodeKey.skipKey = true;
            fields.add(idBarcodeSkuField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).idBarcodeSku);

            ImportField idBatchField = new ImportField(LM.findLCPByCompoundName("idBatch"));
            ImportKey<?> batchKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Batch"),
                    LM.findLCPByCompoundName("batchId").getMapping(idBatchField));
            props.add(new ImportProperty(idBatchField, LM.findLCPByCompoundName("Sale.batchOrderDetail").getMapping(orderDetailKey),
                    LM.object(LM.findClassByCompoundName("Batch")).getMapping(batchKey)));
            fields.add(idBatchField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).idBatch);

            ImportField dataIndexOrderDetailField = new ImportField(LM.findLCPByCompoundName("dataIndexOrderDetail"));
            props.add(new ImportProperty(dataIndexOrderDetailField, LM.findLCPByCompoundName("dataIndexOrderDetail").getMapping(orderDetailKey)));
            fields.add(dataIndexOrderDetailField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).dataIndex);
            
            ImportField idItemField = new ImportField(LM.findLCPByCompoundName("idItem"));
            fields.add(idItemField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).idItem);

            String iGroupAggr = (keyType == null || keyType.equals("item")) ? "itemId" : keyType.equals("barcode") ? "skuIdBarcode" : "skuBatchId";
            ImportField iField = (keyType == null || keyType.equals("item")) ? idItemField : keyType.equals("barcode") ? idBarcodeSkuField : idBatchField;
            ImportKey<?> itemKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Item"),
                    LM.findLCPByCompoundName(iGroupAggr).getMapping(iField));
            keys.add(itemKey);
            itemKey.skipKey = true;
            props.add(new ImportProperty(iField, LM.findLCPByCompoundName("Sale.skuOrderDetail").getMapping(orderDetailKey),
                    LM.object(LM.findClassByCompoundName("Sku")).getMapping(itemKey)));
            props.add(new ImportProperty(iField, LM.findLCPByCompoundName("skuBarcode").getMapping(barcodeKey),
                    LM.object(LM.findClassByCompoundName("Item")).getMapping(itemKey)));

            if (showField(orderDetailsList, "idManufacturer")) {
                ImportField idManufacturerField = new ImportField(LM.findLCPByCompoundName("idManufacturer"));
                ImportKey<?> manufacturerKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Manufacturer"),
                        LM.findLCPByCompoundName("manufacturerId").getMapping(idManufacturerField));
                keys.add(manufacturerKey);
                props.add(new ImportProperty(idManufacturerField, LM.findLCPByCompoundName("idManufacturer").getMapping(manufacturerKey)));
                props.add(new ImportProperty(idManufacturerField, LM.findLCPByCompoundName("manufacturerItem").getMapping(itemKey),
                        LM.object(LM.findClassByCompoundName("Manufacturer")).getMapping(manufacturerKey)));
                fields.add(idManufacturerField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).idManufacturer);
            }

            if (showField(orderDetailsList, "idCustomer")) {
                ImportField idCustomerField = new ImportField(LM.findLCPByCompoundName("idLegalEntity"));
                ImportKey<?> customerKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("LegalEntity"),
                        LM.findLCPByCompoundName("legalEntityId").getMapping(idCustomerField));
                keys.add(customerKey);
                props.add(new ImportProperty(idCustomerField, LM.findLCPByCompoundName("Sale.customerOrder").getMapping(orderObject),
                        LM.object(LM.findClassByCompoundName("LegalEntity")).getMapping(customerKey)));
                fields.add(idCustomerField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).idCustomer);
            }

            if (showField(orderDetailsList, "idCustomerStock")) {
                ImportField idCustomerStockField = new ImportField(LM.findLCPByCompoundName("idStock"));
                ImportKey<?> customerStockKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Stock"),
                        LM.findLCPByCompoundName("stockId").getMapping(idCustomerStockField));
                keys.add(customerStockKey);
                props.add(new ImportProperty(idCustomerStockField, LM.findLCPByCompoundName("Sale.customerStockOrder").getMapping(orderObject),
                        LM.object(LM.findClassByCompoundName("Stock")).getMapping(customerStockKey)));
                fields.add(idCustomerStockField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).idCustomerStock);
            }

            if (showField(orderDetailsList, "quantity")) {
                ImportField quantityOrderDetailField = new ImportField(LM.findLCPByCompoundName("Sale.quantityOrderDetail"));
                props.add(new ImportProperty(quantityOrderDetailField, LM.findLCPByCompoundName("Sale.quantityOrderDetail").getMapping(orderDetailKey)));
                fields.add(quantityOrderDetailField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).quantity);
            }

            if (showField(orderDetailsList, "price")) {
                ImportField priceOrderDetail = new ImportField(LM.findLCPByCompoundName("Sale.priceOrderDetail"));
                props.add(new ImportProperty(priceOrderDetail, LM.findLCPByCompoundName("Sale.priceOrderDetail").getMapping(orderDetailKey)));
                fields.add(priceOrderDetail);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).price);
            }

            if (showField(orderDetailsList, "sum")) {
                ImportField sumOrderDetail = new ImportField(LM.findLCPByCompoundName("Sale.sumOrderDetail"));
                props.add(new ImportProperty(sumOrderDetail, LM.findLCPByCompoundName("Sale.sumOrderDetail").getMapping(orderDetailKey)));
                fields.add(sumOrderDetail);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).sum);
            }

            if (showField(orderDetailsList, "valueVAT")) {
                ImportField valueVATOrderDetailField = new ImportField(LM.findLCPByCompoundName("Sale.valueVATOrderDetail"));
                ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Range"),
                        LM.findLCPByCompoundName("valueCurrentVATDefaultValue").getMapping(valueVATOrderDetailField));
                keys.add(VATKey);
                props.add(new ImportProperty(valueVATOrderDetailField, LM.findLCPByCompoundName("Sale.VATOrderDetail").getMapping(orderDetailKey),
                        LM.object(LM.findClassByCompoundName("Range")).getMapping(VATKey)));
                fields.add(valueVATOrderDetailField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).valueVAT);
            }

            if (showField(orderDetailsList, "sumVAT")) {
                ImportField VATSumOrderDetailField = new ImportField(LM.findLCPByCompoundName("Sale.VATSumOrderDetail"));
                props.add(new ImportProperty(VATSumOrderDetailField, LM.findLCPByCompoundName("Sale.VATSumOrderDetail").getMapping(orderDetailKey)));
                fields.add(VATSumOrderDetailField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).sumVAT);
            }

            if (showField(orderDetailsList, "invoiceSum")) {
                ImportField invoiceSumOrderDetailField = new ImportField(LM.findLCPByCompoundName("Sale.invoiceSumOrderDetail"));
                props.add(new ImportProperty(invoiceSumOrderDetailField, LM.findLCPByCompoundName("Sale.invoiceSumOrderDetail").getMapping(orderDetailKey)));
                fields.add(invoiceSumOrderDetailField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).invoiceSum);
            }

            if ((saleManufacturingPriceLM != null) && showField(orderDetailsList, "manufacturingPrice")) {
                ImportField manufacturingPriceOrderDetailField = new ImportField(saleManufacturingPriceLM.findLCPByCompoundName("Sale.manufacturingPriceOrderDetail"));
                props.add(new ImportProperty(manufacturingPriceOrderDetailField, saleManufacturingPriceLM.findLCPByCompoundName("Sale.manufacturingPriceOrderDetail").getMapping(orderDetailKey)));
                fields.add(manufacturingPriceOrderDetailField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).manufacturingPrice);
            }

            if (showField(orderDetailsList, "isPosted")) {
                ImportField isPostedOrderField = new ImportField(LM.findLCPByCompoundName("isPostedOrder"));
                props.add(new ImportProperty(isPostedOrderField, LM.findLCPByCompoundName("isPostedOrder").getMapping(orderObject)));
                fields.add(isPostedOrderField);
                for (int i = 0; i < orderDetailsList.size(); i++)
                    data.get(i).add(orderDetailsList.get(i).isPosted);
            }

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.getSession();
            session.sql.pushVolatileStats(null);
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            String result = session.applyMessage(context.getBL());
            session.sql.popVolatileStats(null);
            session.close();

            return result == null;
        }
        return false;
    }

    public List<List<SaleOrderDetail>> importOrdersFromFile(DataSession session, Integer orderObject, Map<String, String[]> importColumns,
                                                            byte[] file, String fileExtension, Integer startRow, Boolean isPosted, 
                                                            String csvSeparator, String primaryKeyType, String secondaryKeyType)
            throws SQLException, xBaseJException, ScriptingErrorLog.SemanticErrorException, ParseException, IOException, BiffException {

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

    private List<List<SaleOrderDetail>> importOrdersFromXLS(DataSession session, byte[] importFile, Map<String, String[]> importColumns, 
                                                            String primaryKeyColumn, String secondaryKeyColumn, Integer startRow, Boolean isPosted, Integer orderObject) 
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<SaleOrderDetail> primaryList = new ArrayList<SaleOrderDetail>();
        List<SaleOrderDetail> secondaryList = new ArrayList<SaleOrderDetail>();

        WorkbookSettings ws = new WorkbookSettings();
        ws.setEncoding("cp1251");
        Workbook wb =  Workbook.getWorkbook(new ByteArrayInputStream(importFile), ws);
        Sheet sheet = wb.getSheet(0);

        for (int i = startRow - 1; i < sheet.getRows(); i++) {
            String numberOrder = getXLSFieldValue(sheet, i, importColumns.get("numberDocument"));
            String idOrderDetail = String.valueOf(orderObject) + i;
            String barcodeItem = BarcodeUtils.convertBarcode12To13(getXLSFieldValue(sheet, i, importColumns.get("barcodeItem")));
            String idBatch = getXLSFieldValue(sheet, i, importColumns.get("idBatch"));
            Integer dataIndex = Integer.parseInt(getXLSFieldValue(sheet, i, importColumns.get("dataIndex"), String.valueOf(primaryList.size() + secondaryList.size() + 1)));
            String idItem = getXLSFieldValue(sheet, i, importColumns.get("idItem"));
            String manufacturerItem = getXLSFieldValue(sheet, i, importColumns.get("manufacturerItem"));
            String idCustomerStock = getXLSFieldValue(sheet, i, importColumns.get("idCustomerStock"));
            ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(session, customerObject));
            BigDecimal quantity = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("quantity"));
            BigDecimal price = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("price"));
            BigDecimal sum = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("sum"));
            BigDecimal valueVAT = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("valueVAT"));
            BigDecimal sumVAT = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("sumVAT"));
            BigDecimal invoiceSum = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("invoiceSum"));
            BigDecimal manufacturingPrice = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("manufacturingPrice"));

            SaleOrderDetail saleOrderDetail = new SaleOrderDetail(isPosted, numberOrder, idOrderDetail, barcodeItem, idBatch, 
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

    private List<List<SaleOrderDetail>> importOrdersFromCSV(DataSession session, byte[] importFile, Map<String, String[]> importColumns,
                                                      String primaryKeyColumn, String secondaryKeyColumn,  Integer startRow, Boolean isPosted,
                                                      String csvSeparator, Integer orderObject)
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<SaleOrderDetail> primaryList = new ArrayList<SaleOrderDetail>();
        List<SaleOrderDetail> secondaryList = new ArrayList<SaleOrderDetail>();
        
        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(importFile)));
        String line;
        int count = 0;

        while ((line = br.readLine()) != null) {

            count++;

            if (count >= startRow) {

                String[] values = line.split(csvSeparator);

                String numberOrder = getCSVFieldValue(values, importColumns.get("numberDocument"));
                String idOrderDetail = String.valueOf(orderObject) + count;
                String barcodeItem = BarcodeUtils.convertBarcode12To13(getCSVFieldValue(values, importColumns.get("barcodeItem")));
                String idBatch = getCSVFieldValue(values, importColumns.get("idBatch"));
                Integer dataIndex = Integer.parseInt(getCSVFieldValue(values, importColumns.get("idItem"), String.valueOf(primaryList.size() + secondaryList.size() + 1)));
                String idItem = getCSVFieldValue(values, importColumns.get("idItem"));
                String manufacturerItem = getCSVFieldValue(values, importColumns.get("manufacturerItem"));
                String idCustomerStock = getCSVFieldValue(values, importColumns.get("idCustomerStock"));
                ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(session, new DataObject(idCustomerStock));
                ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
                String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(session, customerObject));
                BigDecimal quantity = getCSVBigDecimalFieldValue(values, importColumns.get("quantity"));
                BigDecimal price = getCSVBigDecimalFieldValue(values, importColumns.get("price"));
                BigDecimal sum = getCSVBigDecimalFieldValue(values, importColumns.get("sum"));
                BigDecimal valueVAT = getCSVBigDecimalFieldValue(values, importColumns.get("valueVAT"));
                BigDecimal sumVAT = getCSVBigDecimalFieldValue(values, importColumns.get("sumVAT"));
                BigDecimal invoiceSum = getCSVBigDecimalFieldValue(values, importColumns.get("invoiceSum"));
                BigDecimal manufacturingPrice = getCSVBigDecimalFieldValue(values, importColumns.get("manufacturingPrice"));

                SaleOrderDetail saleOrderDetail = new SaleOrderDetail(isPosted, numberOrder, idOrderDetail, barcodeItem, idBatch, 
                        dataIndex, idItem, manufacturerItem, idCustomer, idCustomerStock, quantity, price, sum, 
                        VATifAllowed(valueVAT), sumVAT, invoiceSum, manufacturingPrice);

                String primaryKeyColumnValue = getCSVFieldValue(values, importColumns.get(primaryKeyColumn));
                String secondaryKeyColumnValue = getCSVFieldValue(values, importColumns.get(secondaryKeyColumn));
                if (primaryKeyColumnValue != null && !primaryKeyColumnValue.isEmpty())
                    primaryList.add(saleOrderDetail);
                else if (secondaryKeyColumnValue != null && !secondaryKeyColumnValue.isEmpty())
                    secondaryList.add(saleOrderDetail);
            }
        }

        return Arrays.asList(primaryList, secondaryList);
    }

    private List<List<SaleOrderDetail>> importOrdersFromXLSX(DataSession session, byte[] importFile, Map<String, String[]> importColumns,
                                                       String primaryKeyColumn, String secondaryKeyColumn, Integer startRow, 
                                                       Boolean isPosted, Integer orderObject) 
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<SaleOrderDetail> primaryList = new ArrayList<SaleOrderDetail>();
        List<SaleOrderDetail> secondaryList = new ArrayList<SaleOrderDetail>();
        
        XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(importFile));
        XSSFSheet sheet = Wb.getSheetAt(0);

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {

            String numberOrder = getXLSXFieldValue(sheet, i, importColumns.get("numberDocument"));
            String idOrderDetail = String.valueOf(orderObject) + i;
            String barcodeItem = BarcodeUtils.convertBarcode12To13(getXLSXFieldValue(sheet, i, importColumns.get("barcodeItem")));
            String idBatch = getXLSXFieldValue(sheet, i, importColumns.get("idBatch"));
            Integer dataIndex = Integer.parseInt(getXLSXFieldValue(sheet, i, importColumns.get("idItem"), String.valueOf(primaryList.size() + secondaryList.size() + 1)));
            String idItem = getXLSXFieldValue(sheet, i, importColumns.get("idItem"));
            String manufacturerItem = getXLSXFieldValue(sheet, i, importColumns.get("manufacturerItem"));
            String idCustomerStock = getXLSXFieldValue(sheet, i, importColumns.get("idCustomerStock"));
            ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(session, customerObject));
            BigDecimal quantity = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("quantity"));
            BigDecimal price = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("price"));
            BigDecimal sum = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("sum"));
            BigDecimal valueVAT = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("valueVAT"));
            BigDecimal sumVAT = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("sumVAT"));
            BigDecimal invoiceSum = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("invoiceSum"));
            BigDecimal manufacturingPrice = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("manufacturingPrice"));

            SaleOrderDetail saleOrderDetail = new SaleOrderDetail(isPosted, numberOrder, idOrderDetail, barcodeItem, idBatch, 
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

    private List<List<SaleOrderDetail>> importOrdersFromDBF(DataSession session, byte[] importFile, Map<String, String[]> importColumns,
                                                      String primaryKeyColumn, String secondaryKeyColumn, Integer startRow,
                                                      Boolean isPosted, Integer orderObject) 
            throws IOException, xBaseJException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<SaleOrderDetail> primaryList = new ArrayList<SaleOrderDetail>();
        List<SaleOrderDetail> secondaryList = new ArrayList<SaleOrderDetail>();
        
        File tempFile = File.createTempFile("dutiesTNVED", ".dbf");
        IOUtils.putFileBytes(tempFile, importFile);

        DBF file = new DBF(tempFile.getPath());
        String charset = getDBFCharset(tempFile);
        
        int totalRecordCount = file.getRecordCount();

        for (int i = startRow - 1; i < totalRecordCount; i++) {

            file.read();

            String numberOrder = getDBFFieldValue(file, importColumns.get("numberDocument"));
            String idOrderDetail = String.valueOf(orderObject) + i;
            String barcodeItem = BarcodeUtils.convertBarcode12To13(getDBFFieldValue(file, importColumns.get("barcodeItem")));
            String idBatch = getDBFFieldValue(file, importColumns.get("idBatch"));
            Integer dataIndex = getDBFBigDecimalFieldValue(file, importColumns.get("dataIndex"), charset, String.valueOf(primaryList.size() + secondaryList.size() + 1)).intValue();
            String idItem = getDBFFieldValue(file, importColumns.get("idItem"));
            String manufacturerItem = getDBFFieldValue(file, importColumns.get("manufacturerItem"));
            String idCustomerStock = getDBFFieldValue(file, importColumns.get("idCustomerStock"));
            ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(session, customerObject));
            BigDecimal quantity = getDBFBigDecimalFieldValue(file, importColumns.get("quantity"));
            BigDecimal price = getDBFBigDecimalFieldValue(file, importColumns.get("price"));
            BigDecimal sum = getDBFBigDecimalFieldValue(file, importColumns.get("sum"));
            BigDecimal valueVAT = getDBFBigDecimalFieldValue(file, importColumns.get("valueVAT"));
            BigDecimal sumVAT = getDBFBigDecimalFieldValue(file, importColumns.get("sumVAT"));
            BigDecimal invoiceSum = getDBFBigDecimalFieldValue(file, importColumns.get("invoiceSum"));
            BigDecimal manufacturingPrice = getDBFBigDecimalFieldValue(file, importColumns.get("manufacturingPrice"));

            SaleOrderDetail saleOrderDetail = new SaleOrderDetail(isPosted, numberOrder, idOrderDetail, barcodeItem, idBatch, 
                    dataIndex, idItem, manufacturerItem, idCustomer, idCustomerStock, quantity, price, sum, VATifAllowed(valueVAT), sumVAT,
                    invoiceSum, manufacturingPrice);
            
            String primaryKeyColumnValue = getDBFFieldValue(file, importColumns.get(primaryKeyColumn));
            String secondaryKeyColumnValue = getDBFFieldValue(file, importColumns.get(secondaryKeyColumn));
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