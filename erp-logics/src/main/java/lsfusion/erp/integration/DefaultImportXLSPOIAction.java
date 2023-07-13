package lsfusion.erp.integration;

import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.classes.ValueClass;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.ss.usermodel.CellType;

import java.math.BigDecimal;
import java.text.DecimalFormat;

public class DefaultImportXLSPOIAction extends DefaultImportAction {
    
    public DefaultImportXLSPOIAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultImportXLSPOIAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    protected String getXLSFieldValue(HSSFSheet sheet, int row, int cell) {
        return getXLSFieldValue(sheet, row, cell, null);
    }

    protected String getXLSFieldValue(HSSFSheet sheet, int row, int cell, String defaultValue) {
        HSSFRow hssfRow = sheet.getRow(row);
        if (hssfRow == null) return defaultValue;
        HSSFCell hssfCell = hssfRow.getCell(cell);
        if (hssfCell == null) return defaultValue;
        switch (hssfCell.getCellType()) {
            case NUMERIC:
            case FORMULA:
                String result;
                try {
                    result = new DecimalFormat("#.#####").format(hssfCell.getNumericCellValue());
                } catch (Exception e) {
                    result = hssfCell.getStringCellValue().isEmpty() ? defaultValue : trim(hssfCell.getStringCellValue());
                }
                return result.endsWith(".0") ? result.substring(0, result.length() - 2) : result;
            case ERROR:
                return defaultValue;
            case STRING:
            default:
                return (hssfCell.getStringCellValue().isEmpty()) ? defaultValue : trim(hssfCell.getStringCellValue());
        }
    }

    protected BigDecimal getXLSBigDecimalFieldValue(HSSFSheet sheet, int row, int cell) {
        return getXLSBigDecimalFieldValue(sheet, row, cell, null);
    }
    
    protected BigDecimal getXLSBigDecimalFieldValue(HSSFSheet sheet, int row, int cell, BigDecimal defaultValue) {
        HSSFRow hssfRow = sheet.getRow(row);
        if (hssfRow == null) return defaultValue;
        HSSFCell hssfCell = hssfRow.getCell(cell);
        return (hssfCell == null || hssfCell.getCellType() != CellType.NUMERIC) ? defaultValue : BigDecimal.valueOf(hssfCell.getNumericCellValue());
    }
}