package lsfusion.erp.integration.universal;

import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.commons.lang.time.DateUtils;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class ImportDocumentActionProperty extends ScriptingActionProperty {

    public ImportDocumentActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, new ValueClass[]{valueClass});
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
    }

    protected Map<String, String> readImportColumns(ExecutionContext context, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException {

        Map<String, String> importColumns = new HashMap<String, String>();

        LCP<?> isImportTypeDetail = LM.is(getClass("ImportTypeDetail"));
        ImRevMap<Object, KeyExpr> keys = (ImRevMap<Object, KeyExpr>) isImportTypeDetail.getMapKeys();
        KeyExpr key = keys.singleValue();
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
        query.addProperty("staticName", getLCP("staticName").getExpr(context.getModifier(), key));
        query.addProperty("indexImportTypeImportTypeDetail", getLCP("indexImportTypeImportTypeDetail").getExpr(context.getModifier(), importTypeObject.getExpr(), key));
        query.and(isImportTypeDetail.getExpr(key).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context.getSession().sql);

        for (ImMap<Object, Object> entry : result.valueIt()) {

            String[] field = ((String) entry.get("staticName")).trim().split("\\.");
            String index = (String) entry.get("indexImportTypeImportTypeDetail");
            if (index != null)
                importColumns.put(field[field.length - 1], index.trim());
        }
        return importColumns.isEmpty() ? null : importColumns;
    }

    private List<BigDecimal> allowedVAT = Arrays.asList(BigDecimal.valueOf(0.0), BigDecimal.valueOf(9.09), BigDecimal.valueOf(16.67), BigDecimal.valueOf(10.0), BigDecimal.valueOf(20.0), BigDecimal.valueOf(24.0));

    protected BigDecimal VATifAllowed(BigDecimal VAT) {
      return allowedVAT.contains(VAT) ? VAT : null;  
    } 
    
    protected String getCSVFieldValue(String[] values, Integer index, String defaultValue) throws ParseException {
        if (index == null) return defaultValue;
        return values.length <= index ? defaultValue : values[index];
    }

    protected BigDecimal getCSVBigDecimalFieldValue(String[] values, int index, BigDecimal defaultValue) throws ParseException {
        String value = getCSVFieldValue(values, index, null);
        return value == null ? defaultValue : new BigDecimal(value);
    }

    protected Date getCSVDateFieldValue(String[] values, int index, Date defaultValue) throws ParseException {
        String value = getCSVFieldValue(values, index, null);
        return value == null ? defaultValue : new Date(DateUtils.parseDate(value, new String[]{"dd.MM.yyyy"}).getTime());
    }

    protected String getXLSFieldValue(HSSFSheet sheet, Integer row, Integer cell, String defaultValue) throws ParseException {
        if (cell == null) return defaultValue;
        HSSFRow hssfRow = sheet.getRow(row);
        if (hssfRow == null) return defaultValue;
        HSSFCell hssfCell = hssfRow.getCell(cell);
        if (hssfCell == null) return defaultValue;
        switch (hssfCell.getCellType()) {
            case Cell.CELL_TYPE_NUMERIC:
                hssfCell.setCellType(HSSFCell.CELL_TYPE_STRING);
                String result = hssfCell.getStringCellValue();
                return result.endsWith(".0") ? result.substring(0, result.length() - 2) : result;
            case Cell.CELL_TYPE_STRING:
            default:
                return (hssfCell.getStringCellValue().isEmpty()) ? defaultValue : hssfCell.getStringCellValue().trim();
        }
    }

    protected BigDecimal getXLSBigDecimalFieldValue(HSSFSheet sheet, Integer row, Integer cell, BigDecimal defaultValue) throws ParseException {
        if (cell == null) return defaultValue;
        HSSFRow hssfRow = sheet.getRow(row);
        if (hssfRow == null) return defaultValue;
        HSSFCell hssfCell = hssfRow.getCell(cell);
        return (hssfCell == null || hssfCell.getCellType() != Cell.CELL_TYPE_NUMERIC) ? defaultValue : BigDecimal.valueOf(hssfCell.getNumericCellValue());
    }

    protected Date getXLSDateFieldValue(HSSFSheet sheet, Integer row, Integer cell, Date defaultValue) throws ParseException {
        if (cell == null) return defaultValue;
        HSSFRow hssfRow = sheet.getRow(row);
        if (hssfRow == null) return defaultValue;
        HSSFCell hssfCell = hssfRow.getCell(cell);
        return hssfCell == null ? defaultValue : new Date(hssfCell.getDateCellValue().getTime());
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, Integer cell, String defaultValue) throws ParseException {
        if (cell == null) return defaultValue;
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

    protected BigDecimal getXLSXBigDecimalFieldValue(XSSFSheet sheet, Integer row, Integer cell, BigDecimal defaultValue) throws ParseException {
        if (cell == null) return defaultValue;
        XSSFRow xssfRow = sheet.getRow(row);
        if (xssfRow == null) return defaultValue;
        XSSFCell xssfCell = xssfRow.getCell(cell);
        return (xssfCell == null || xssfCell.getCellType() != Cell.CELL_TYPE_NUMERIC) ? defaultValue : BigDecimal.valueOf(xssfCell.getNumericCellValue());
    }

    protected Date getXLSXDateFieldValue(XSSFSheet sheet, Integer row, Integer cell, Date defaultValue) throws ParseException {
        if (cell == null) return defaultValue;
        XSSFRow xssfRow = sheet.getRow(row);
        if (xssfRow == null) return defaultValue;
        XSSFCell xssfCell = xssfRow.getCell(cell);
        return xssfCell == null ? defaultValue : new Date(xssfCell.getDateCellValue().getTime());
    }

    protected String getDBFFieldValue(DBF importFile, String fieldName, String charset, String defaultValue) throws UnsupportedEncodingException {
        try {
            if (fieldName == null)
                return defaultValue;
            String result = new String(importFile.getField(fieldName).getBytes(), charset).trim();
            return result.isEmpty() ? defaultValue : result;
        } catch (xBaseJException e) {
            return defaultValue;
        }
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, String fieldName, String charset, String defaultValue) throws UnsupportedEncodingException {
        String value = getDBFFieldValue(importFile, fieldName, charset, defaultValue);
        return value == null ? null : new BigDecimal(value);
    }

    protected Date getDBFDateFieldValue(DBF importFile, String fieldName, String charset, Date defaultValue) throws UnsupportedEncodingException, ParseException {
        String dateString = getDBFFieldValue(importFile, fieldName, charset, "");
        return dateString.isEmpty() ? defaultValue : new Date(DateUtils.parseDate(dateString, new String[]{"yyyyMMdd"}).getTime());
    }

    protected Integer getColumnNumber(String importColumn) {
        return importColumn == null ? null : (Integer.parseInt(importColumn) - 1);
    }
}

