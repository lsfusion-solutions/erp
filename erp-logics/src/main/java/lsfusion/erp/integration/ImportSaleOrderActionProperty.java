package lsfusion.erp.integration;

import jxl.read.biff.BiffException;
import lsfusion.base.IOUtils;
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
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

public class ImportSaleOrderActionProperty extends ImportDocumentActionProperty {
    private final ClassPropertyInterface orderInterface;

    public ImportSaleOrderActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClassByCompoundName("Sale.Order"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        orderInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {

        try {

            DataObject orderObject = context.getDataKeyValue(orderInterface);

            ObjectValue importTypeObject = LM.findLCPByCompoundName("importTypeOrder").readClasses(context, orderObject);

            if (!(importTypeObject instanceof NullValue)) {

                String fileExtension = (String) LM.findLCPByCompoundName("captionImportTypeFileExtensionImportType").read(context, importTypeObject);
                Boolean byBarcode = LM.findLCPByCompoundName("byBarcodeImportType").read(context, importTypeObject) != null;
                String csvSeparator = (String) LM.findLCPByCompoundName("separatorImportType").read(context, importTypeObject);
                csvSeparator = csvSeparator == null ? ";" : csvSeparator;
                Integer startRow = (Integer) LM.findLCPByCompoundName("startRowImportType").read(context, importTypeObject);
                startRow = startRow == null ? 1 : startRow;

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

                            importOrders(context, orderObject, importColumns, file, fileExtension.trim(), startRow, csvSeparator, byBarcode,
                                    supplierObject, supplierStockObject, customerObject, customerStockObject);

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
                             byte[] file, String fileExtension, Integer startRow, String csvSeparator, Boolean byBarcode,
                             DataObject supplierObject, DataObject supplierStockObject, DataObject customerObject, DataObject customerStockObject)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException {

        List<List<Object>> orderDetailsList;

        if (fileExtension.equals("DBF"))
            orderDetailsList = importOrdersFromDBF(file, importColumns, startRow, (Integer) orderObject.object);
        else if (fileExtension.equals("XLS"))
            orderDetailsList = importOrdersFromXLS(file, importColumns, startRow, (Integer) orderObject.object);
        else if (fileExtension.equals("XLSX"))
            orderDetailsList = importOrdersFromXLSX(file, importColumns, startRow, (Integer) orderObject.object);
        else if (fileExtension.equals("CSV"))
            orderDetailsList = importOrdersFromCSV(file, importColumns, startRow, csvSeparator, (Integer) orderObject.object);
        else
            orderDetailsList = null;

        if (orderDetailsList != null) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            ImportField numberOrderField = new ImportField(LM.findLCPByCompoundName("numberObject"));
            props.add(new ImportProperty(numberOrderField, LM.findLCPByCompoundName("numberObject").getMapping(orderObject)));
            fields.add(numberOrderField);

            ImportField idUserOrderDetailField = new ImportField(LM.findLCPByCompoundName("idUserOrderDetail"));
            ImportKey<?> orderDetailKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Sale.UserOrderDetail"),
                    LM.findLCPByCompoundName("userOrderDetailId").getMapping(idUserOrderDetailField));
            keys.add(orderDetailKey);
            props.add(new ImportProperty(idUserOrderDetailField, LM.findLCPByCompoundName("idUserOrderDetail").getMapping(orderDetailKey)));
            props.add(new ImportProperty(orderObject, LM.findLCPByCompoundName("Sale.orderOrderDetail").getMapping(orderDetailKey)));
            fields.add(idUserOrderDetailField);

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

            ImportField idItemField = new ImportField(LM.findLCPByCompoundName(byBarcode ? "idBarcodeSku" : "idItem"));
            ImportKey<?> itemKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Item"),
                    LM.findLCPByCompoundName(byBarcode ? "skuIdBarcode" : "itemId").getMapping(idItemField));
            keys.add(itemKey);
            props.add(new ImportProperty(idItemField, LM.findLCPByCompoundName("Sale.skuOrderDetail").getMapping(orderDetailKey),
                    LM.object(LM.findClassByCompoundName("Sku")).getMapping(itemKey)));
            fields.add(idItemField);

            ImportField quantityOrderDetailField = new ImportField(LM.findLCPByCompoundName("Sale.quantityOrderDetail"));
            props.add(new ImportProperty(quantityOrderDetailField, LM.findLCPByCompoundName("Sale.quantityOrderDetail").getMapping(orderDetailKey)));
            fields.add(quantityOrderDetailField);

            ImportField priceOrderDetail = new ImportField(LM.findLCPByCompoundName("Sale.priceOrderDetail"));
            props.add(new ImportProperty(priceOrderDetail, LM.findLCPByCompoundName("Sale.priceOrderDetail").getMapping(orderDetailKey)));
            fields.add(priceOrderDetail);

            ImportField sumOrderDetail = new ImportField(LM.findLCPByCompoundName("Sale.sumOrderDetail"));
            props.add(new ImportProperty(sumOrderDetail, LM.findLCPByCompoundName("Sale.sumOrderDetail").getMapping(orderDetailKey)));
            fields.add(sumOrderDetail);

            ImportField valueVATOrderDetailField = new ImportField(LM.findLCPByCompoundName("Sale.valueVATOrderDetail"));
            ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Range"),
                    LM.findLCPByCompoundName("valueCurrentVATDefaultValue").getMapping(valueVATOrderDetailField));
            keys.add(VATKey);
            props.add(new ImportProperty(valueVATOrderDetailField, LM.findLCPByCompoundName("Sale.VATOrderDetail").getMapping(orderDetailKey),
                    LM.object(LM.findClassByCompoundName("Range")).getMapping(VATKey)));
            fields.add(valueVATOrderDetailField);

            ImportField VATSumOrderDetailField = new ImportField(LM.findLCPByCompoundName("Sale.VATSumOrderDetail"));
            props.add(new ImportProperty(VATSumOrderDetailField, LM.findLCPByCompoundName("Sale.VATSumOrderDetail").getMapping(orderDetailKey)));
            fields.add(VATSumOrderDetailField);

            ImportField invoiceSumOrderDetailField = new ImportField(LM.findLCPByCompoundName("Sale.invoiceSumOrderDetail"));
            props.add(new ImportProperty(invoiceSumOrderDetailField, LM.findLCPByCompoundName("Sale.invoiceSumOrderDetail").getMapping(orderDetailKey)));
            fields.add(invoiceSumOrderDetailField);

            ImportTable table = new ImportTable(fields, orderDetailsList);

            DataSession session = context.getSession();
            session.sql.pushVolatileStats(null);
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context.getBL());
            session.sql.popVolatileStats(null);
            session.close();

        }
    }

    private List<List<Object>> importOrdersFromXLS(byte[] importFile, Map<String, String> importColumns, Integer startRow, Integer orderObject) throws BiffException, IOException, ParseException {

        List<List<Object>> orderDetailList = new ArrayList<List<Object>>();

        HSSFWorkbook Wb = new HSSFWorkbook(new ByteArrayInputStream(importFile));

        HSSFSheet sheet = Wb.getSheetAt(0);

        Integer numberOrderColumn = getColumnNumber(importColumns.get("numberDocument"));
        Integer idItemColumn = getColumnNumber(importColumns.get("idItem"));
        Integer quantityColumn = getColumnNumber(importColumns.get("quantity"));
        Integer priceColumn = getColumnNumber(importColumns.get("price"));
        Integer sumColumn = getColumnNumber(importColumns.get("sum"));
        Integer VATColumn = getColumnNumber(importColumns.get("VAT"));
        Integer VATSumColumn = getColumnNumber(importColumns.get("VATSum"));
        Integer invoiceSumColumn = getColumnNumber(importColumns.get("invoiceSum"));

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {
            String numberOrder = getXLSFieldValue(sheet, i, numberOrderColumn, null);
            String idUserOrderDetail = String.valueOf(orderObject) + i;
            String idItem = getXLSFieldValue(sheet, i, idItemColumn, null);
            BigDecimal quantity = getXLSBigDecimalFieldValue(sheet, i, quantityColumn, null);
            BigDecimal price = getXLSBigDecimalFieldValue(sheet, i, priceColumn, null);
            BigDecimal sum = getXLSBigDecimalFieldValue(sheet, i, sumColumn, null);
            BigDecimal VAT = getXLSBigDecimalFieldValue(sheet, i, VATColumn, null);
            BigDecimal VATSum = getXLSBigDecimalFieldValue(sheet, i, VATSumColumn, null);
            BigDecimal invoiceSum = getXLSBigDecimalFieldValue(sheet, i, invoiceSumColumn, null);

            orderDetailList.add(Arrays.asList((Object) numberOrder, idUserOrderDetail, idItem, quantity, price, sum,
                    allowedVAT.contains(VAT) ? VAT : null, VATSum, invoiceSum));
        }

        return orderDetailList;
    }

    private List<List<Object>> importOrdersFromCSV(byte[] importFile, Map<String, String> importColumns,
                                                   Integer startRow, String csvSeparator, Integer orderObject)
            throws BiffException, IOException, ParseException {

        List<List<Object>> orderDetailList = new ArrayList<List<Object>>();

        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(importFile)));
        String line;
        int count = 0;

        Integer numberOrderColumn = getColumnNumber(importColumns.get("numberDocument"));
        Integer idItemColumn = getColumnNumber(importColumns.get("idItem"));
        Integer quantityColumn = getColumnNumber(importColumns.get("quantity"));
        Integer priceColumn = getColumnNumber(importColumns.get("price"));
        Integer sumColumn = getColumnNumber(importColumns.get("sum"));
        Integer VATColumn = getColumnNumber(importColumns.get("VAT"));
        Integer VATSumColumn = getColumnNumber(importColumns.get("VATSum"));
        Integer invoiceSumColumn = getColumnNumber(importColumns.get("invoiceSum"));

        while ((line = br.readLine()) != null) {

            count++;

            if (count >= startRow) {

                String[] values = line.split(csvSeparator);

                String numberOrder = getCSVFieldValue(values, numberOrderColumn, null);
                String idUserOrderDetail = String.valueOf(orderObject) + count;
                String idItem = getCSVFieldValue(values, idItemColumn, null);
                BigDecimal quantity = getCSVBigDecimalFieldValue(values, quantityColumn, null);
                BigDecimal price = getCSVBigDecimalFieldValue(values, priceColumn, null);
                BigDecimal sum = getCSVBigDecimalFieldValue(values, sumColumn, null);
                BigDecimal VAT = getCSVBigDecimalFieldValue(values, VATColumn, null);
                BigDecimal VATSum = getCSVBigDecimalFieldValue(values, VATSumColumn, null);
                BigDecimal invoiceSum = getCSVBigDecimalFieldValue(values, invoiceSumColumn, null);

                orderDetailList.add(Arrays.asList((Object) numberOrder, idUserOrderDetail, idItem, quantity, price, sum,
                        allowedVAT.contains(VAT) ? VAT : null, VATSum, invoiceSum));

            }
        }

        return orderDetailList;
    }

    private List<List<Object>> importOrdersFromXLSX(byte[] importFile, Map<String, String> importColumns, Integer startRow, Integer orderObject) throws BiffException, IOException, ParseException {

        List<List<Object>> orderDetailList = new ArrayList<List<Object>>();

        XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(importFile));

        XSSFSheet sheet = Wb.getSheetAt(0);

        Integer numberOrderColumn = getColumnNumber(importColumns.get("numberDocument"));
        Integer idItemColumn = getColumnNumber(importColumns.get("idItem"));
        Integer quantityColumn = getColumnNumber(importColumns.get("quantity"));
        Integer priceColumn = getColumnNumber(importColumns.get("price"));
        Integer sumColumn = getColumnNumber(importColumns.get("sum"));
        Integer VATColumn = getColumnNumber(importColumns.get("VAT"));
        Integer VATSumColumn = getColumnNumber(importColumns.get("VATSum"));
        Integer invoiceSumColumn = getColumnNumber(importColumns.get("invoiceSum"));

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {
            String numberOrder = getXLSXFieldValue(sheet, i, numberOrderColumn, null);
            String idUserOrderDetail = String.valueOf(orderObject) + i;
            String idItem = getXLSXFieldValue(sheet, i, idItemColumn, null);
            BigDecimal quantity = getXLSXBigDecimalFieldValue(sheet, i, quantityColumn, null);
            BigDecimal price = getXLSXBigDecimalFieldValue(sheet, i, priceColumn, null);
            BigDecimal sum = getXLSXBigDecimalFieldValue(sheet, i, sumColumn, null);
            BigDecimal VAT = getXLSXBigDecimalFieldValue(sheet, i, VATColumn, null);
            BigDecimal VATSum = getXLSXBigDecimalFieldValue(sheet, i, VATSumColumn, null);
            BigDecimal invoiceSum = getXLSXBigDecimalFieldValue(sheet, i, invoiceSumColumn, null);

            orderDetailList.add(Arrays.asList((Object) numberOrder, idUserOrderDetail, idItem, quantity, price, sum,
                    allowedVAT.contains(VAT) ? VAT : null, VATSum, invoiceSum));
        }

        return orderDetailList;
    }

    private List<List<Object>> importOrdersFromDBF(byte[] importFile, Map<String, String> importColumns, Integer startRow, Integer orderObject) throws IOException, xBaseJException {

        List<List<Object>> ordersList = new ArrayList<List<Object>>();

        File tempFile = File.createTempFile("dutiesTNVED", ".dbf");
        IOUtils.putFileBytes(tempFile, importFile);


        DBF file = new DBF(tempFile.getPath());

        int totalRecordCount = file.getRecordCount();

        for (int i = startRow - 1; i < totalRecordCount; i++) {

            file.read();

            String numberOrder = getDBFFieldValue(file, importColumns.get("numberDocument"), "Cp866", "");
            String idUserOrderDetail = String.valueOf(orderObject) + i;
            String idItem = getDBFFieldValue(file, importColumns.get("idItem"), "Cp866", "");
            BigDecimal quantity = getDBFBigDecimalFieldValue(file, importColumns.get("quantity"), "Cp866", "0");
            BigDecimal price = getDBFBigDecimalFieldValue(file, importColumns.get("price"), "Cp866", null);
            BigDecimal sum = getDBFBigDecimalFieldValue(file, importColumns.get("sum"), "Cp866", null);
            BigDecimal VAT = getDBFBigDecimalFieldValue(file, importColumns.get("VAT"), "Cp866", null);
            BigDecimal VATSum = getDBFBigDecimalFieldValue(file, importColumns.get("VATSum"), "Cp866", null);
            BigDecimal invoiceSum = getDBFBigDecimalFieldValue(file, importColumns.get("invoiceSum"), "Cp866", null);

            ordersList.add(Arrays.asList((Object) numberOrder, idUserOrderDetail, idItem, quantity, price, sum,
                    allowedVAT.contains(VAT) ? VAT : null, VATSum, invoiceSum));
        }

        return ordersList;
    }
}