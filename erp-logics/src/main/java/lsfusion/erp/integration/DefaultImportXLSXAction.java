package lsfusion.erp.integration;

import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.classes.ValueClass;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import java.math.BigDecimal;
import java.text.DecimalFormat;

public class DefaultImportXLSXAction extends DefaultImportAction {

    public DefaultImportXLSXAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultImportXLSXAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, Integer cell) {
        return getXLSXFieldValue(sheet, row, cell, null);
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, Integer cell, String defaultValue) {
        if (cell == null) return defaultValue;
        XSSFRow xssfRow = sheet.getRow(row);
        if (xssfRow == null) return defaultValue;
        XSSFCell xssfCell = xssfRow.getCell(cell);
        if (xssfCell == null) return defaultValue;
        String result;
        switch (xssfCell.getCellType()) {
            case ERROR:
                result = null;
                break;
            case NUMERIC:
                result = new DecimalFormat("#.#####").format(xssfCell.getNumericCellValue());
                result = result.endsWith(".0") ? result.substring(0, result.length() - 2) : result;
                break;
            case FORMULA:
                result = xssfCell.getCellFormula();
                break;
            case STRING:
            default:
                result = (xssfCell.getStringCellValue().isEmpty()) ? defaultValue : xssfCell.getStringCellValue().trim();
                break;
        }
        return result;
    }

    protected BigDecimal getXLSXBigDecimalFieldValue(XSSFSheet sheet, Integer row, Integer cell) {
        return getXLSXBigDecimalFieldValue(sheet, row, cell, null);
    }

    protected BigDecimal getXLSXBigDecimalFieldValue(XSSFSheet sheet, Integer row, Integer cell, BigDecimal defaultValue) {
        if (cell == null) return defaultValue;
        XSSFRow xssfRow = sheet.getRow(row);
        if (xssfRow == null) return defaultValue;
        XSSFCell xssfCell = xssfRow.getCell(cell);
        if (xssfCell == null) return defaultValue;
        switch (xssfCell.getCellType()) {
            case ERROR:
                return null;
            case NUMERIC:
            case FORMULA:
                return BigDecimal.valueOf(xssfCell.getNumericCellValue());
            case STRING:
            default:
                String result = xssfCell.getStringCellValue().trim();
                try {
                    return result.isEmpty() ? defaultValue : new BigDecimal(result);
                } catch (Exception e) {
                    return null;
                }
        }
    }
}