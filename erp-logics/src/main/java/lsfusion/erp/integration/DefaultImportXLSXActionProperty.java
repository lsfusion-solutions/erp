package lsfusion.erp.integration;

import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingLogicsModule;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.ParseException;

public class DefaultImportXLSXActionProperty extends DefaultImportActionProperty {

    public final static int A = 0, B = 1, C = 2, D = 3, E = 4, F = 5, G = 6, H = 7, I = 8, J = 9, K = 10, L = 11, M = 12,
            N = 13, O = 14, P = 15, Q = 16, R = 17, S = 18, T = 19, U = 20, V = 21, W = 22, X = 23, Y = 24, Z = 25,
            AA = 26, AB = 27, AC = 28, AD = 29, AE = 30, AF = 31, AG = 32, AH = 33, AI = 34, AJ = 35, AK = 36, AL = 37, AM = 38, AN = 39, AO = 40,
            AQ = 42, AS = 44, AX = 49;

    public DefaultImportXLSXActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultImportXLSXActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) {
        super(LM, valueClass);
    }

    public DefaultImportXLSXActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, Integer cell) throws ParseException {
        return getXLSXFieldValue(sheet, row, cell, null);
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, Integer cell, String defaultValue) throws ParseException {
        if (cell == null) return defaultValue;
        XSSFRow xssfRow = sheet.getRow(row);
        if (xssfRow == null) return defaultValue;
        XSSFCell xssfCell = xssfRow.getCell(cell);
        if (xssfCell == null) return defaultValue;
        String result;
        switch (xssfCell.getCellType()) {
            case Cell.CELL_TYPE_ERROR:
                result = null;
                break;
            case Cell.CELL_TYPE_NUMERIC:
                result = new DecimalFormat("#.#####").format(xssfCell.getNumericCellValue());
                result = result.endsWith(".0") ? result.substring(0, result.length() - 2) : result;
                break;
            case Cell.CELL_TYPE_FORMULA:
                result = xssfCell.getCellFormula();
                break;
            case Cell.CELL_TYPE_STRING:
            default:
                result = (xssfCell.getStringCellValue().isEmpty()) ? defaultValue : xssfCell.getStringCellValue().trim();
                break;
        }
        return result;
    }

    protected BigDecimal getXLSXBigDecimalFieldValue(XSSFSheet sheet, Integer row, Integer cell) throws ParseException {
        return getXLSXBigDecimalFieldValue(sheet, row, cell, null);
    }

    protected BigDecimal getXLSXBigDecimalFieldValue(XSSFSheet sheet, Integer row, Integer cell, BigDecimal defaultValue) throws ParseException {
        if (cell == null) return defaultValue;
        XSSFRow xssfRow = sheet.getRow(row);
        if (xssfRow == null) return defaultValue;
        XSSFCell xssfCell = xssfRow.getCell(cell);
        if (xssfCell == null) return defaultValue;
        switch (xssfCell.getCellType()) {
            case Cell.CELL_TYPE_ERROR:
                return null;
            case Cell.CELL_TYPE_NUMERIC:
            case Cell.CELL_TYPE_FORMULA:
                return BigDecimal.valueOf(xssfCell.getNumericCellValue());
            case Cell.CELL_TYPE_STRING:
            default:
                String result = xssfCell.getStringCellValue().trim();
                try {
                    return result.isEmpty() ? defaultValue : new BigDecimal(result);
                } catch (Exception e) {
                    return null;
                }
        }
    }

    protected Date getXLSXDateFieldValue(XSSFSheet sheet, Integer row, Integer cell) throws ParseException {
        return getXLSXDateFieldValue(sheet, row, cell, null);
    }

    protected Date getXLSXDateFieldValue(XSSFSheet sheet, Integer row, Integer cell, Date defaultValue) throws ParseException {
        if (cell == null) return defaultValue;
        XSSFRow xssfRow = sheet.getRow(row);
        if (xssfRow == null) return defaultValue;
        XSSFCell xssfCell = xssfRow.getCell(cell);
        if (xssfCell == null) return defaultValue;
        switch (xssfCell.getCellType()) {
            case Cell.CELL_TYPE_ERROR:
                return null;
            case Cell.CELL_TYPE_NUMERIC:
                return new Date(xssfCell.getDateCellValue().getTime());
            default:
                return parseDate(getXLSXFieldValue(sheet, row, cell, String.valueOf(defaultValue)));
        }
    }
}