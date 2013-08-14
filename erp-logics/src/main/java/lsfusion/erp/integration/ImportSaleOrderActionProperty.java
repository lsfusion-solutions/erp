package lsfusion.erp.integration;

import jxl.read.biff.BiffException;
import lsfusion.base.IOUtils;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

public class ImportSaleOrderActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface orderInterface;

    public ImportSaleOrderActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, new ValueClass[]{LM.findClassByCompoundName("Sale.Order")});

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
                Boolean byBarcode = LM.findLCPByCompoundName("byBarcodeImportType").read(context, importTypeObject) !=null;
                Integer startRow = (Integer) LM.findLCPByCompoundName("startRowImportType").read(context, importTypeObject);
                startRow = startRow == null ? 1 : startRow;

                LCP<?> isImportTypeDetail = LM.is(getClass("ImportTypeDetail"));
                ImRevMap<Object, KeyExpr> keys = (ImRevMap<Object, KeyExpr>) isImportTypeDetail.getMapKeys();
                KeyExpr key = keys.singleValue();
                QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
                query.addProperty("staticName", getLCP("staticName").getExpr(context.getModifier(), key));
                query.addProperty("indexImportTypeImportTypeDetail", getLCP("indexImportTypeImportTypeDetail").getExpr(context.getModifier(), importTypeObject.getExpr(), key));
                query.and(isImportTypeDetail.getExpr(key).getWhere());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context.getSession().sql);

                Map<String, String> importColumns = new HashMap<String, String>();

                for (ImMap<Object, Object> entry : result.valueIt()) {

                    String[] field = ((String) entry.get("staticName")).trim().split("\\.");
                    String index = (String) entry.get("indexImportTypeImportTypeDetail");
                    if(index!=null)
                    importColumns.put(field[field.length - 1], index.trim());
                }

                if (!importColumns.isEmpty() && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, fileExtension + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                        for (byte[] file : fileList) {

                            importOrders(context, orderObject, importColumns, file, fileExtension.trim(), startRow, byBarcode);

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

    private void importOrders(ExecutionContext context, DataObject orderObject, Map<String, String> importColumns,
                              byte[] file, String fileExtension, Integer startRow, Boolean byBarcode)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException {

        List<List<Object>> orderDetailsList;

        if (fileExtension.equals("DBF"))
            orderDetailsList = importOrdersFromDBF(file, importColumns, startRow, (Integer) orderObject.object);
        else if (fileExtension.equals("XLS"))
            orderDetailsList = importOrdersFromXLS(file, importColumns, startRow, (Integer) orderObject.object);
        else if (fileExtension.equals("XLSX"))
            orderDetailsList = importOrdersFromXLSX(file, importColumns, startRow, (Integer) orderObject.object);
        else if (fileExtension.equals("CSV"))
            orderDetailsList = importOrdersFromCSV(file, importColumns, startRow, (Integer) orderObject.object);
        else
            orderDetailsList = null;

        if (orderDetailsList != null) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            ImportField idUserOrderDetailField = new ImportField(LM.findLCPByCompoundName("idUserOrderDetail"));
            ImportKey<?> orderDetailKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Sale.UserOrderDetail"),
                    LM.findLCPByCompoundName("userOrderDetailId").getMapping(idUserOrderDetailField));
            keys.add(orderDetailKey);
            props.add(new ImportProperty(idUserOrderDetailField, LM.findLCPByCompoundName("idUserOrderDetail").getMapping(orderDetailKey)));
            props.add(new ImportProperty(orderObject, LM.findLCPByCompoundName("Sale.orderOrderDetail").getMapping(orderDetailKey)));
            fields.add(idUserOrderDetailField);

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

            ImportField pricOrderDetail = new ImportField(LM.findLCPByCompoundName("Sale.priceOrderDetail"));
            props.add(new ImportProperty(pricOrderDetail, LM.findLCPByCompoundName("Sale.priceOrderDetail").getMapping(orderDetailKey)));
            fields.add(pricOrderDetail);

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

    List<BigDecimal> allowedVAT = Arrays.asList(BigDecimal.valueOf(0.0), BigDecimal.valueOf(9.09), BigDecimal.valueOf(16.67), BigDecimal.valueOf(10.0), BigDecimal.valueOf(20.0), BigDecimal.valueOf(24.0));

    private List<List<Object>> importOrdersFromXLS(byte[] importFile, Map<String, String> importColumns, Integer startRow, Integer orderObject) throws BiffException, IOException, ParseException {

        List<List<Object>> orderDetailList = new ArrayList<List<Object>>();

        HSSFWorkbook Wb = new HSSFWorkbook(new ByteArrayInputStream(importFile));

        HSSFSheet sheet = Wb.getSheetAt(0);

        Integer idItemColumn = getColumnNumber(importColumns.get("idItem"));
        Integer quantityColumn = getColumnNumber(importColumns.get("quantity"));
        Integer priceColumn = getColumnNumber(importColumns.get("price"));
        Integer sumColumn = getColumnNumber(importColumns.get("sum"));
        Integer VATColumn = getColumnNumber(importColumns.get("VAT"));
        Integer VATSumColumn = getColumnNumber(importColumns.get("VATSum"));
        Integer invoiceSumColumn = getColumnNumber(importColumns.get("invoiceSum"));

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {
            String idUserOrderDetail = String.valueOf(orderObject) + i;
            String idItem = idItemColumn == null ? null : getXLSFieldValue(sheet, i, idItemColumn, null);
            BigDecimal quantity = quantityColumn == null ? null : getXLSBigDecimalFieldValue(sheet, i, quantityColumn, null);
            BigDecimal price = priceColumn == null ? null : getXLSBigDecimalFieldValue(sheet, i, priceColumn, null);
            BigDecimal sum = sumColumn == null ? null : getXLSBigDecimalFieldValue(sheet, i, sumColumn, null);
            BigDecimal VAT = VATColumn == null ? null : getXLSBigDecimalFieldValue(sheet, i, VATColumn, null);
            BigDecimal VATSum = VATSumColumn == null ? null : getXLSBigDecimalFieldValue(sheet, i, VATSumColumn, null);
            BigDecimal invoiceSum = invoiceSumColumn == null ? null : getXLSBigDecimalFieldValue(sheet, i, invoiceSumColumn, null);

            orderDetailList.add(Arrays.asList((Object) idUserOrderDetail, idItem, quantity, price, sum,
                    allowedVAT.contains(VAT) ? VAT : null, VATSum, invoiceSum));
        }

        return orderDetailList;
    }

    private List<List<Object>> importOrdersFromCSV(byte[] importFile, Map<String, String> importColumns, Integer startRow, Integer orderObject) throws BiffException, IOException, ParseException {

        List<List<Object>> orderDetailList = new ArrayList<List<Object>>();

        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(importFile)));
        String line;
        int count = 0;

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

                String[] values = line.split(";");

                String idUserOrderDetail = String.valueOf(orderObject) + count;
                String idItem = idItemColumn == null ? null : getCSVFieldValue(values, idItemColumn, null);
                BigDecimal quantity = quantityColumn == null ? null : getCSVBigDecimalFieldValue(values, quantityColumn, null);
                BigDecimal price = priceColumn == null ? null : getCSVBigDecimalFieldValue(values, priceColumn, null);
                BigDecimal sum = sumColumn == null ? null : getCSVBigDecimalFieldValue(values, sumColumn, null);
                BigDecimal VAT = VATColumn == null ? null : getCSVBigDecimalFieldValue(values, VATColumn, null);
                BigDecimal VATSum = VATSumColumn == null ? null : getCSVBigDecimalFieldValue(values, VATSumColumn, null);
                BigDecimal invoiceSum = invoiceSumColumn == null ? null : getCSVBigDecimalFieldValue(values, invoiceSumColumn, null);

                orderDetailList.add(Arrays.asList((Object) idUserOrderDetail, idItem, quantity, price, sum,
                        allowedVAT.contains(VAT) ? VAT : null, VATSum, invoiceSum));

            }
        }

        return orderDetailList;
    }

    private List<List<Object>> importOrdersFromXLSX(byte[] importFile, Map<String, String> importColumns, Integer startRow, Integer orderObject) throws BiffException, IOException, ParseException {

        List<List<Object>> orderDetailList = new ArrayList<List<Object>>();

        XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(importFile));

        XSSFSheet sheet = Wb.getSheetAt(0);

        Integer idItemColumn = getColumnNumber(importColumns.get("idItem"));
        Integer quantityColumn = getColumnNumber(importColumns.get("quantity"));
        Integer priceColumn = getColumnNumber(importColumns.get("price"));
        Integer sumColumn = getColumnNumber(importColumns.get("sum"));
        Integer VATColumn = getColumnNumber(importColumns.get("VAT"));
        Integer VATSumColumn = getColumnNumber(importColumns.get("VATSum"));
        Integer invoiceSumColumn = getColumnNumber(importColumns.get("invoiceSum"));

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {
            String idUserOrderDetail = String.valueOf(orderObject) + i;
            String idItem = idItemColumn == null ? null : getXLSXFieldValue(sheet, i, idItemColumn, null);
            BigDecimal quantity = quantityColumn == null ? null : getXLSXBigDecimalFieldValue(sheet, i, quantityColumn, null);
            BigDecimal price = priceColumn == null ? null : getXLSXBigDecimalFieldValue(sheet, i, priceColumn, null);
            BigDecimal sum = sumColumn == null ? null : getXLSXBigDecimalFieldValue(sheet, i, sumColumn, null);
            BigDecimal VAT = VATColumn == null ? null : getXLSXBigDecimalFieldValue(sheet, i, VATColumn, null);
            BigDecimal VATSum = VATSumColumn == null ? null : getXLSXBigDecimalFieldValue(sheet, i, VATSumColumn, null);
            BigDecimal invoiceSum = invoiceSumColumn == null ? null : getXLSXBigDecimalFieldValue(sheet, i, invoiceSumColumn, null);

            orderDetailList.add(Arrays.asList((Object) idUserOrderDetail, idItem, quantity, price, sum, allowedVAT.contains(VAT) ? VAT : null,
                    VATSum, invoiceSum));
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

            String idUserOrderDetail = String.valueOf(orderObject) + i;
            String idItem = getDBFFieldValue(file, importColumns.get("idItem"), "Cp866", "");
            BigDecimal quantity = getDBFBigDecimalFieldValue(file, importColumns.get("quantity"), "Cp866", "0");
            BigDecimal price = getDBFBigDecimalFieldValue(file, importColumns.get("price"), "Cp866", null);
            BigDecimal sum = getDBFBigDecimalFieldValue(file, importColumns.get("sum"), "Cp866", null);
            BigDecimal VAT = getDBFBigDecimalFieldValue(file, importColumns.get("VAT"), "Cp866", null);
            BigDecimal VATSum = getDBFBigDecimalFieldValue(file, importColumns.get("VATSum"), "Cp866", null);
            BigDecimal invoiceSum = getDBFBigDecimalFieldValue(file, importColumns.get("invoiceSum"), "Cp866", null);

            ordersList.add(Arrays.asList((Object) idUserOrderDetail, idItem, quantity, price, sum, allowedVAT.contains(VAT) ? VAT : null, VATSum, invoiceSum));
        }

        return ordersList;
    }

    protected static String getCSVFieldValue(String[] values, int index, String defaultValue) throws ParseException {
        return values.length <= index ? defaultValue : values[index];
    }

    protected static BigDecimal getCSVBigDecimalFieldValue(String[] values, int index, String defaultValue) throws ParseException {
        String value = getCSVFieldValue(values, index, defaultValue);
        return value == null ? null : new BigDecimal(value);
    }

    protected static String getXLSFieldValue(HSSFSheet sheet, int row, int cell, String defaultValue) throws ParseException {
        HSSFRow hssfRow = sheet.getRow(row);
        if (hssfRow == null) return defaultValue;
        HSSFCell hssfCell = hssfRow.getCell(cell);
        if (hssfCell == null) return defaultValue;
        switch (hssfCell.getCellType()) {
            case Cell.CELL_TYPE_NUMERIC:
                String result = String.valueOf(hssfCell.getNumericCellValue());
                return result.endsWith(".0") ? result.substring(0, result.length() - 2) : result;
            case Cell.CELL_TYPE_STRING:
            default:
                return (hssfCell.getStringCellValue().isEmpty()) ? defaultValue : hssfCell.getStringCellValue().trim();
        }
    }

    protected static BigDecimal getXLSBigDecimalFieldValue(HSSFSheet sheet, int row, int cell, BigDecimal defaultValue) throws ParseException {
        HSSFRow hssfRow = sheet.getRow(row);
        if (hssfRow == null) return defaultValue;
        HSSFCell hssfCell = hssfRow.getCell(cell);
        return (hssfCell == null || hssfCell.getCellType() != Cell.CELL_TYPE_NUMERIC) ? defaultValue : BigDecimal.valueOf(hssfCell.getNumericCellValue());
    }

    protected static String getXLSXFieldValue(XSSFSheet sheet, int row, int cell, String defaultValue) throws ParseException {
        XSSFRow xssfRow = sheet.getRow(row);
        if (xssfRow == null) return defaultValue;
        XSSFCell xssfCell = xssfRow.getCell(cell);
        if (xssfCell == null) return defaultValue;
        switch (xssfCell.getCellType()) {
            case Cell.CELL_TYPE_NUMERIC:
                String result = String.valueOf(xssfCell.getNumericCellValue());
                return result.endsWith(".0") ? result.substring(0, result.length() - 2) : result;
            case Cell.CELL_TYPE_STRING:
            default:
                return (xssfCell.getStringCellValue().isEmpty()) ? defaultValue : xssfCell.getStringCellValue().trim();
        }
    }

    protected static BigDecimal getXLSXBigDecimalFieldValue(XSSFSheet sheet, int row, int cell, BigDecimal defaultValue) throws ParseException {
        XSSFRow xssfRow = sheet.getRow(row);
        if (xssfRow == null) return defaultValue;
        XSSFCell xssfCell = xssfRow.getCell(cell);
        return (xssfCell == null || xssfCell.getCellType() != Cell.CELL_TYPE_NUMERIC) ? defaultValue : BigDecimal.valueOf(xssfCell.getNumericCellValue());
    }

    private String getDBFFieldValue(DBF importFile, String fieldName, String charset, String defaultValue) throws UnsupportedEncodingException {
        try {
            if (fieldName == null)
                return null;
            String result = new String(importFile.getField(fieldName).getBytes(), charset).trim();
            return result.isEmpty() ? defaultValue : result;
        } catch (xBaseJException e) {
            return defaultValue;
        }
    }

    private BigDecimal getDBFBigDecimalFieldValue(DBF importFile, String fieldName, String charset, String defaultValue) throws UnsupportedEncodingException {
        String value = getDBFFieldValue(importFile, fieldName, charset, defaultValue);
        return value == null ? null : new BigDecimal(value);
    }

    private Integer getColumnNumber(String importColumn) {
        return importColumn == null ? null : (Integer.parseInt(importColumn) - 1);
    }
}

