package lsfusion.erp.integration.universal;

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
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

            this.saleManufacturingPriceLM = (ScriptingLogicsModule) context.getBL().getModule("SaleManufacturingPrice");

            DataObject orderObject = context.getDataKeyValue(orderInterface);

            ObjectValue importTypeObject = LM.findLCPByCompoundName("importTypeOrder").readClasses(context, orderObject);

            if (!(importTypeObject instanceof NullValue)) {

                String fileExtension = (String) LM.findLCPByCompoundName("captionImportTypeFileExtensionImportType").read(context, importTypeObject);
                String itemKeyType = (String) LM.findLCPByCompoundName("nameImportKeyTypeImportType").read(context, importTypeObject);
                String[] parts = itemKeyType == null ? null : itemKeyType.split("\\.");
                itemKeyType = parts == null ? null : parts[parts.length - 1].trim();
                String csvSeparator = (String) LM.findLCPByCompoundName("separatorImportType").read(context, importTypeObject);
                csvSeparator = csvSeparator == null ? ";" : csvSeparator;
                Integer startRow = (Integer) LM.findLCPByCompoundName("startRowImportType").read(context, importTypeObject);
                startRow = startRow == null ? 1 : startRow;

                ObjectValue operation = LM.findLCPByCompoundName("autoImportOperationImportType").readClasses(context, (DataObject) importTypeObject);
                DataObject operationObject = operation instanceof NullValue ? null : (DataObject) operation;
                ObjectValue supplier = LM.findLCPByCompoundName("autoImportSupplierImportType").readClasses(context, (DataObject) importTypeObject);
                DataObject supplierObject = supplier instanceof NullValue ? null : (DataObject) supplier;
                ObjectValue supplierStock = LM.findLCPByCompoundName("autoImportSupplierStockImportType").readClasses(context, (DataObject) importTypeObject);
                DataObject supplierStockObject = supplierStock instanceof NullValue ? null : (DataObject) supplierStock;
                ObjectValue customer = LM.findLCPByCompoundName("autoImportCustomerImportType").readClasses(context, (DataObject) importTypeObject);
                DataObject customerObject = customer instanceof NullValue ? null : (DataObject) customer;
                ObjectValue customerStock = LM.findLCPByCompoundName("autoImportCustomerStockImportType").readClasses(context, (DataObject) importTypeObject);
                DataObject customerStockObject = customerStock instanceof NullValue ? null : (DataObject) customerStock;

                Map<String, String> importColumns = readImportColumns(context, importTypeObject);

                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, fileExtension.trim() + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                        for (byte[] file : fileList) {

                            importOrders(context, orderObject, importColumns, file, fileExtension.trim(), startRow,
                                    csvSeparator, itemKeyType, operationObject, supplierObject, supplierStockObject,
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

    public void importOrders(ExecutionContext context, DataObject orderObject, Map<String, String> importColumns,
                             byte[] file, String fileExtension, Integer startRow, String csvSeparator, String itemKeyType,
                             DataObject operationObject, DataObject supplierObject, DataObject supplierStockObject,
                             DataObject customerObject, DataObject customerStockObject)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException {

        List<SaleOrderDetail> orderDetailsList;

        if (fileExtension.equals("DBF"))
            orderDetailsList = importOrdersFromDBF(context, file, importColumns, startRow, (Integer) orderObject.object);
        else if (fileExtension.equals("XLS"))
            orderDetailsList = importOrdersFromXLS(context, file, importColumns, startRow, (Integer) orderObject.object);
        else if (fileExtension.equals("XLSX"))
            orderDetailsList = importOrdersFromXLSX(context, file, importColumns, startRow, (Integer) orderObject.object);
        else if (fileExtension.equals("CSV"))
            orderDetailsList = importOrdersFromCSV(context, file, importColumns, startRow, csvSeparator, (Integer) orderObject.object);
        else
            orderDetailsList = null;

        if (orderDetailsList != null) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(orderDetailsList.size());

            if (showField(orderDetailsList, "numberOrder")) {
                ImportField numberOrderField = new ImportField(LM.findLCPByCompoundName("numberOrder"));
                props.add(new ImportProperty(numberOrderField, LM.findLCPByCompoundName("numberOrder").getMapping(orderObject)));
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

            if (operationObject != null)
                props.add(new ImportProperty(operationObject, LM.findLCPByCompoundName("Sale.operationOrder").getMapping(orderObject)));

            if (supplierObject != null) {
                props.add(new ImportProperty(supplierObject, LM.findLCPByCompoundName("Sale.supplierOrderDetail").getMapping(orderDetailKey)));
                props.add(new ImportProperty(supplierObject, LM.findLCPByCompoundName("Sale.supplierOrder").getMapping(orderObject)));
            }

            if (supplierStockObject != null) {
                props.add(new ImportProperty(supplierStockObject, LM.findLCPByCompoundName("Sale.supplierStockOrderDetail").getMapping(orderDetailKey)));
                props.add(new ImportProperty(supplierStockObject, LM.findLCPByCompoundName("Sale.supplierStockOrder").getMapping(orderObject)));
            }

            if (customerObject != null) {
                props.add(new ImportProperty(customerObject, LM.findLCPByCompoundName("Sale.customerOrderDetail").getMapping(orderDetailKey)));
                props.add(new ImportProperty(customerObject, LM.findLCPByCompoundName("Sale.customerOrder").getMapping(orderObject)));
            }

            if (customerStockObject != null) {
                props.add(new ImportProperty(customerStockObject, LM.findLCPByCompoundName("Sale.customerStockOrderDetail").getMapping(orderDetailKey)));
                props.add(new ImportProperty(customerStockObject, LM.findLCPByCompoundName("Sale.customerStockOrder").getMapping(orderObject)));
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

            ImportField idItemField = new ImportField(LM.findLCPByCompoundName("idItem"));
            fields.add(idItemField);
            for (int i = 0; i < orderDetailsList.size(); i++)
                data.get(i).add(orderDetailsList.get(i).idItem);

            String iGroupAggr = (itemKeyType == null || itemKeyType.equals("item")) ? "itemId" : itemKeyType.equals("barcode") ? "skuIdBarcode" : "skuBatchId";
            ImportField iField = (itemKeyType == null || itemKeyType.equals("item")) ? idItemField : itemKeyType.equals("barcode") ? idBarcodeSkuField : idBatchField;
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

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.getSession();
            session.sql.pushVolatileStats(null);
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context.getBL());
            session.sql.popVolatileStats(null);
            session.close();

        }
    }

    private List<SaleOrderDetail> importOrdersFromXLS(ExecutionContext context, byte[] importFile, Map<String, String> importColumns, Integer startRow, Integer orderObject) throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<SaleOrderDetail> saleOrderDetailList = new ArrayList<SaleOrderDetail>();

        HSSFWorkbook Wb = new HSSFWorkbook(new ByteArrayInputStream(importFile));

        HSSFSheet sheet = Wb.getSheetAt(0);

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {
            String numberOrder = getXLSFieldValue(sheet, i, getColumnNumber(importColumns.get("numberDocument")));
            String idOrderDetail = String.valueOf(orderObject) + i;
            String barcodeItem = BarcodeUtils.convertBarcode12To13(getXLSFieldValue(sheet, i, getColumnNumber(importColumns.get("barcodeItem"))));
            String idBatch = getXLSFieldValue(sheet, i, getColumnNumber(importColumns.get("idBatch")));
            String idItem = getXLSFieldValue(sheet, i, getColumnNumber(importColumns.get("idItem")));
            String manufacturerItem = getXLSFieldValue(sheet, i, getColumnNumber(importColumns.get("manufacturerItem")));
            String idCustomerStock = getXLSFieldValue(sheet, i, getColumnNumber(importColumns.get("idCustomerStock")));
            ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(context, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(context, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(context, customerObject));
            BigDecimal quantity = getXLSBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("quantity")));
            BigDecimal price = getXLSBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("price")));
            BigDecimal sum = getXLSBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("sum")));
            BigDecimal valueVAT = getXLSBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("valueVAT")));
            BigDecimal sumVAT = getXLSBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("sumVAT")));
            BigDecimal invoiceSum = getXLSBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("invoiceSum")));
            BigDecimal manufacturingPrice = getXLSBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("manufacturingPrice")));

            saleOrderDetailList.add(new SaleOrderDetail(numberOrder, idOrderDetail, barcodeItem, idBatch, idItem,
                    manufacturerItem, idCustomer, idCustomerStock, quantity, price, sum, VATifAllowed(valueVAT),
                    sumVAT, invoiceSum, manufacturingPrice));
        }

        return saleOrderDetailList;
    }

    private List<SaleOrderDetail> importOrdersFromCSV(ExecutionContext context, byte[] importFile, Map<String, String> importColumns,
                                                      Integer startRow, String csvSeparator, Integer orderObject)
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<SaleOrderDetail> saleOrderDetailList = new ArrayList<SaleOrderDetail>();

        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(importFile)));
        String line;
        int count = 0;

        while ((line = br.readLine()) != null) {

            count++;

            if (count >= startRow) {

                String[] values = line.split(csvSeparator);

                String numberOrder = getCSVFieldValue(values, getColumnNumber(importColumns.get("numberDocument")));
                String idOrderDetail = String.valueOf(orderObject) + count;
                String barcodeItem = BarcodeUtils.convertBarcode12To13(getCSVFieldValue(values, getColumnNumber(importColumns.get("barcodeItem"))));
                String idBatch = getCSVFieldValue(values, getColumnNumber(importColumns.get("idBatch")));
                String idItem = getCSVFieldValue(values, getColumnNumber(importColumns.get("idItem")));
                String manufacturerItem = getCSVFieldValue(values, getColumnNumber(importColumns.get("manufacturerItem")));
                String idCustomerStock = getCSVFieldValue(values, getColumnNumber(importColumns.get("idCustomerStock")));
                ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(context, new DataObject(idCustomerStock));
                ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(context, (DataObject) customerStockObject));
                String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(context, customerObject));
                BigDecimal quantity = getCSVBigDecimalFieldValue(values, getColumnNumber(importColumns.get("quantity")));
                BigDecimal price = getCSVBigDecimalFieldValue(values, getColumnNumber(importColumns.get("price")));
                BigDecimal sum = getCSVBigDecimalFieldValue(values, getColumnNumber(importColumns.get("sum")));
                BigDecimal valueVAT = getCSVBigDecimalFieldValue(values, getColumnNumber(importColumns.get("valueVAT")));
                BigDecimal sumVAT = getCSVBigDecimalFieldValue(values, getColumnNumber(importColumns.get("sumVAT")));
                BigDecimal invoiceSum = getCSVBigDecimalFieldValue(values, getColumnNumber(importColumns.get("invoiceSum")));
                BigDecimal manufacturingPrice = getCSVBigDecimalFieldValue(values, getColumnNumber(importColumns.get("manufacturingPrice")));

                saleOrderDetailList.add(new SaleOrderDetail(numberOrder, idOrderDetail, barcodeItem, idBatch, idItem,
                        manufacturerItem, idCustomer, idCustomerStock, quantity, price, sum, VATifAllowed(valueVAT),
                        sumVAT, invoiceSum, manufacturingPrice));
            }
        }

        return saleOrderDetailList;
    }

    private List<SaleOrderDetail> importOrdersFromXLSX(ExecutionContext context, byte[] importFile, Map<String, String> importColumns, Integer startRow, Integer orderObject) throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<SaleOrderDetail> saleOrderDetailList = new ArrayList<SaleOrderDetail>();

        XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(importFile));
        XSSFSheet sheet = Wb.getSheetAt(0);

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {

            String numberOrder = getXLSXFieldValue(sheet, i, getColumnNumber(importColumns.get("numberDocument")));
            String idOrderDetail = String.valueOf(orderObject) + i;
            String barcodeItem = BarcodeUtils.convertBarcode12To13(getXLSXFieldValue(sheet, i, getColumnNumber(importColumns.get("barcodeItem"))));
            String idBatch = getXLSXFieldValue(sheet, i, getColumnNumber(importColumns.get("idBatch")));
            String idItem = getXLSXFieldValue(sheet, i, getColumnNumber(importColumns.get("idItem")));
            String manufacturerItem = getXLSXFieldValue(sheet, i, getColumnNumber(importColumns.get("manufacturerItem")));
            String idCustomerStock = getXLSXFieldValue(sheet, i, getColumnNumber(importColumns.get("idCustomerStock")));
            ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(context, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(context, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(context, customerObject));
            BigDecimal quantity = getXLSXBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("quantity")));
            BigDecimal price = getXLSXBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("price")));
            BigDecimal sum = getXLSXBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("sum")));
            BigDecimal valueVAT = getXLSXBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("valueVAT")));
            BigDecimal sumVAT = getXLSXBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("sumVAT")));
            BigDecimal invoiceSum = getXLSXBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("invoiceSum")));
            BigDecimal manufacturingPrice = getXLSXBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("manufacturingPrice")));

            saleOrderDetailList.add(new SaleOrderDetail(numberOrder, idOrderDetail, barcodeItem, idBatch, idItem,
                    manufacturerItem, idCustomer, idCustomerStock, quantity, price, sum, VATifAllowed(valueVAT), sumVAT, invoiceSum,
                    manufacturingPrice));
        }

        return saleOrderDetailList;
    }

    private List<SaleOrderDetail> importOrdersFromDBF(ExecutionContext context, byte[] importFile, Map<String, String> importColumns, Integer startRow, Integer orderObject) throws IOException, xBaseJException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<SaleOrderDetail> saleOrderDetailList = new ArrayList<SaleOrderDetail>();

        File tempFile = File.createTempFile("dutiesTNVED", ".dbf");
        IOUtils.putFileBytes(tempFile, importFile);


        DBF file = new DBF(tempFile.getPath());

        int totalRecordCount = file.getRecordCount();

        for (int i = startRow - 1; i < totalRecordCount; i++) {

            file.read();

            String numberOrder = getDBFFieldValue(file, importColumns.get("numberDocument"));
            String idOrderDetail = String.valueOf(orderObject) + i;
            String barcodeItem = BarcodeUtils.convertBarcode12To13(getDBFFieldValue(file, importColumns.get("barcodeItem")));
            String idBatch = getDBFFieldValue(file, importColumns.get("idBatch"));
            String idItem = getDBFFieldValue(file, importColumns.get("idItem"));
            String manufacturerItem = getDBFFieldValue(file, importColumns.get("manufacturerItem"));
            String idCustomerStock = getDBFFieldValue(file, importColumns.get("idCustomerStock"));
            ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(context, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(context, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(context, customerObject));
            BigDecimal quantity = getDBFBigDecimalFieldValue(file, importColumns.get("quantity"));
            BigDecimal price = getDBFBigDecimalFieldValue(file, importColumns.get("price"));
            BigDecimal sum = getDBFBigDecimalFieldValue(file, importColumns.get("sum"));
            BigDecimal valueVAT = getDBFBigDecimalFieldValue(file, importColumns.get("valueVAT"));
            BigDecimal sumVAT = getDBFBigDecimalFieldValue(file, importColumns.get("sumVAT"));
            BigDecimal invoiceSum = getDBFBigDecimalFieldValue(file, importColumns.get("invoiceSum"));
            BigDecimal manufacturingPrice = getDBFBigDecimalFieldValue(file, importColumns.get("manufacturingPrice"));

            saleOrderDetailList.add(new SaleOrderDetail(numberOrder, idOrderDetail, barcodeItem, idBatch, idItem,
                    manufacturerItem, idCustomer, idCustomerStock, quantity, price, sum, VATifAllowed(valueVAT), sumVAT,
                    invoiceSum, manufacturingPrice));
        }               
        file.close();

        return saleOrderDetailList;
    }

    private List<List<Object>> initData(int size) {
        List<List<Object>> data = new ArrayList<List<Object>>();
        for (int i = 0; i < size; i++) {
            data.add(new ArrayList<Object>());
        }
        return data;
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