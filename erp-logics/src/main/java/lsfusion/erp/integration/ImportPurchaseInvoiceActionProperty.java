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

public class ImportPurchaseInvoiceActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface userInvoiceInterface;

    public ImportPurchaseInvoiceActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, new ValueClass[]{LM.findClassByCompoundName("Purchase.UserInvoice")});

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        userInvoiceInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {

        try {

            DataObject userInvoiceObject = context.getDataKeyValue(userInvoiceInterface);

            ObjectValue importTypeObject = LM.getLCPByName("importTypeUserInvoice").readClasses(context, userInvoiceObject);

            if (!(importTypeObject instanceof NullValue)) {

                String fileExtension = (String) LM.getLCPByName("captionImportTypeFileExtensionImportType").read(context, importTypeObject);
                Boolean byBarcode = LM.getLCPByName("byBarcodeImportType").read(context, importTypeObject) !=null;
                Integer startRow = (Integer) LM.getLCPByName("startRowImportType").read(context, importTypeObject);
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

                            importUserInvoices(context, userInvoiceObject, importColumns, file, fileExtension.trim(), startRow, byBarcode);

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

    private void importUserInvoices(ExecutionContext context, DataObject userInvoiceObject, Map<String, String> importColumns,
                                    byte[] file, String fileExtension, Integer startRow, Boolean byBarcode)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException {

        List<List<Object>> userInvoiceDetailsList;

        if (fileExtension.equals("DBF"))
            userInvoiceDetailsList = importUserInvoicesFromDBF(file, importColumns, startRow, (Integer) userInvoiceObject.object);
        else if (fileExtension.equals("XLS"))
            userInvoiceDetailsList = importUserInvoicesFromXLS(file, importColumns, startRow, (Integer) userInvoiceObject.object);
        else if (fileExtension.equals("XLSX"))
            userInvoiceDetailsList = importUserInvoicesFromXLSX(file, importColumns, startRow, (Integer) userInvoiceObject.object);
        else if (fileExtension.equals("CSV"))
            userInvoiceDetailsList = importUserInvoicesFromCSV(file, importColumns, startRow, (Integer) userInvoiceObject.object);
        else
            userInvoiceDetailsList = null;

        if (userInvoiceDetailsList != null) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            ImportField idUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("idUserInvoiceDetail"));
            ImportKey<?> userInvoiceDetailKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Purchase.UserInvoiceDetail"),
                    LM.findLCPByCompoundName("userInvoiceDetailId").getMapping(idUserInvoiceDetailField));
            keys.add(userInvoiceDetailKey);
            props.add(new ImportProperty(idUserInvoiceDetailField, LM.findLCPByCompoundName("idUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            props.add(new ImportProperty(userInvoiceObject, LM.findLCPByCompoundName("Purchase.userInvoiceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            fields.add(idUserInvoiceDetailField);

            ImportField idItemField = new ImportField(LM.findLCPByCompoundName(byBarcode ? "idBarcodeSku" : "idItem"));
            ImportKey<?> itemKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Item"),
                    LM.findLCPByCompoundName(byBarcode ? "skuIdBarcode" : "itemId").getMapping(idItemField));
            keys.add(itemKey);
            props.add(new ImportProperty(idItemField, LM.findLCPByCompoundName("Purchase.skuInvoiceDetail").getMapping(userInvoiceDetailKey),
                    LM.object(LM.findClassByCompoundName("Sku")).getMapping(itemKey)));
            fields.add(idItemField);

            ImportField quantityUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.quantityUserInvoiceDetail"));
            props.add(new ImportProperty(quantityUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.quantityUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            fields.add(quantityUserInvoiceDetailField);

            ImportField priceUserInvoiceDetail = new ImportField(LM.findLCPByCompoundName("Purchase.priceUserInvoiceDetail"));
            props.add(new ImportProperty(priceUserInvoiceDetail, LM.findLCPByCompoundName("Purchase.priceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            fields.add(priceUserInvoiceDetail);

            ImportField sumUserInvoiceDetail = new ImportField(LM.findLCPByCompoundName("Purchase.sumUserInvoiceDetail"));
            props.add(new ImportProperty(sumUserInvoiceDetail, LM.findLCPByCompoundName("Purchase.sumUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            fields.add(sumUserInvoiceDetail);

            ImportField valueVATUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.valueVATUserInvoiceDetail"));
            ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Range"),
                    LM.findLCPByCompoundName("valueCurrentVATDefaultValue").getMapping(valueVATUserInvoiceDetailField));
            keys.add(VATKey);
            props.add(new ImportProperty(valueVATUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.VATUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                    LM.object(LM.findClassByCompoundName("Range")).getMapping(VATKey)));
            fields.add(valueVATUserInvoiceDetailField);

            ImportField VATSumUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.VATSumUserInvoiceDetail"));
            props.add(new ImportProperty(VATSumUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.VATSumUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            fields.add(VATSumUserInvoiceDetailField);

            ImportField invoiceSumUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.invoiceSumUserInvoiceDetail"));
            props.add(new ImportProperty(invoiceSumUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.invoiceSumUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            fields.add(invoiceSumUserInvoiceDetailField);

            ImportTable table = new ImportTable(fields, userInvoiceDetailsList);

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

    private List<List<Object>> importUserInvoicesFromXLS(byte[] importFile, Map<String, String> importColumns, Integer startRow, Integer userInvoiceObject) throws BiffException, IOException, ParseException {

        List<List<Object>> userInvoiceDetailList = new ArrayList<List<Object>>();

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
            String idUserInvoiceDetail = String.valueOf(userInvoiceObject) + i;
            String idItem = idItemColumn == null ? null : getXLSFieldValue(sheet, i, idItemColumn, null);
            BigDecimal quantity = quantityColumn == null ? null : getXLSBigDecimalFieldValue(sheet, i, quantityColumn, null);
            BigDecimal price = priceColumn == null ? null : getXLSBigDecimalFieldValue(sheet, i, priceColumn, null);
            BigDecimal sum = sumColumn == null ? null : getXLSBigDecimalFieldValue(sheet, i, sumColumn, null);
            BigDecimal VAT = VATColumn == null ? null : getXLSBigDecimalFieldValue(sheet, i, VATColumn, null);
            BigDecimal VATSum = VATSumColumn == null ? null : getXLSBigDecimalFieldValue(sheet, i, VATSumColumn, null);
            BigDecimal invoiceSum = invoiceSumColumn == null ? null : getXLSBigDecimalFieldValue(sheet, i, invoiceSumColumn, null);

            userInvoiceDetailList.add(Arrays.asList((Object) idUserInvoiceDetail, idItem, quantity, price, sum,
                    allowedVAT.contains(VAT) ? VAT : null, VATSum, invoiceSum));
        }

        return userInvoiceDetailList;
    }

    private List<List<Object>> importUserInvoicesFromCSV(byte[] importFile, Map<String, String> importColumns, Integer startRow, Integer userInvoiceObject) throws BiffException, IOException, ParseException {

        List<List<Object>> userInvoiceDetailList = new ArrayList<List<Object>>();

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

                String idUserInvoiceDetail = String.valueOf(userInvoiceObject) + count;
                String idItem = idItemColumn == null ? null : getCSVFieldValue(values, idItemColumn, null);
                BigDecimal quantity = quantityColumn == null ? null : getCSVBigDecimalFieldValue(values, quantityColumn, null);
                BigDecimal price = priceColumn == null ? null : getCSVBigDecimalFieldValue(values, priceColumn, null);
                BigDecimal sum = sumColumn == null ? null : getCSVBigDecimalFieldValue(values, sumColumn, null);
                BigDecimal VAT = VATColumn == null ? null : getCSVBigDecimalFieldValue(values, VATColumn, null);
                BigDecimal VATSum = VATSumColumn == null ? null : getCSVBigDecimalFieldValue(values, VATSumColumn, null);
                BigDecimal invoiceSum = invoiceSumColumn == null ? null : getCSVBigDecimalFieldValue(values, invoiceSumColumn, null);

                userInvoiceDetailList.add(Arrays.asList((Object) idUserInvoiceDetail, idItem, quantity, price, sum,
                        allowedVAT.contains(VAT) ? VAT : null, VATSum, invoiceSum));

            }
        }

        return userInvoiceDetailList;
    }

    private List<List<Object>> importUserInvoicesFromXLSX(byte[] importFile, Map<String, String> importColumns, Integer startRow, Integer userInvoiceObject) throws BiffException, IOException, ParseException {

        List<List<Object>> userInvoiceDetailList = new ArrayList<List<Object>>();

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
            String idUserInvoiceDetail = String.valueOf(userInvoiceObject) + i;
            String idItem = idItemColumn == null ? null : getXLSXFieldValue(sheet, i, idItemColumn, null);
            BigDecimal quantity = quantityColumn == null ? null : getXLSXBigDecimalFieldValue(sheet, i, quantityColumn, null);
            BigDecimal price = priceColumn == null ? null : getXLSXBigDecimalFieldValue(sheet, i, priceColumn, null);
            BigDecimal sum = sumColumn == null ? null : getXLSXBigDecimalFieldValue(sheet, i, sumColumn, null);
            BigDecimal VAT = VATColumn == null ? null : getXLSXBigDecimalFieldValue(sheet, i, VATColumn, null);
            BigDecimal VATSum = VATSumColumn == null ? null : getXLSXBigDecimalFieldValue(sheet, i, VATSumColumn, null);
            BigDecimal invoiceSum = invoiceSumColumn == null ? null : getXLSXBigDecimalFieldValue(sheet, i, invoiceSumColumn, null);

            userInvoiceDetailList.add(Arrays.asList((Object) idUserInvoiceDetail, idItem, quantity, price, sum, allowedVAT.contains(VAT) ? VAT : null,
                    VATSum, invoiceSum));
        }

        return userInvoiceDetailList;
    }

    private List<List<Object>> importUserInvoicesFromDBF(byte[] importFile, Map<String, String> importColumns, Integer startRow, Integer userInvoiceObject) throws IOException, xBaseJException {

        List<List<Object>> userInvoicesList = new ArrayList<List<Object>>();

        File tempFile = File.createTempFile("dutiesTNVED", ".dbf");
        IOUtils.putFileBytes(tempFile, importFile);



        DBF file = new DBF(tempFile.getPath());

        int totalRecordCount = file.getRecordCount();

        for (int i = startRow - 1; i < totalRecordCount; i++) {

            file.read();

            String idUserInvoiceDetail = String.valueOf(userInvoiceObject) + i;
            String idItem = getDBFFieldValue(file, importColumns.get("idItem"), "Cp866", "");
            BigDecimal quantity = getDBFBigDecimalFieldValue(file, importColumns.get("quantity"), "Cp866", "0");
            BigDecimal price = getDBFBigDecimalFieldValue(file, importColumns.get("price"), "Cp866", null);
            BigDecimal sum = getDBFBigDecimalFieldValue(file, importColumns.get("sum"), "Cp866", null);
            BigDecimal VAT = getDBFBigDecimalFieldValue(file, importColumns.get("VAT"), "Cp866", null);
            BigDecimal VATSum = getDBFBigDecimalFieldValue(file, importColumns.get("VATSum"), "Cp866", null);
            BigDecimal invoiceSum = getDBFBigDecimalFieldValue(file, importColumns.get("invoiceSum"), "Cp866", null);

            userInvoicesList.add(Arrays.asList((Object) idUserInvoiceDetail, idItem, quantity, price, sum, allowedVAT.contains(VAT) ? VAT : null, VATSum, invoiceSum));
        }

        return userInvoicesList;
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

