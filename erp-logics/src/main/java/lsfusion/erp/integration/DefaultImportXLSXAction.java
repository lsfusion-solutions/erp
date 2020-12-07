package lsfusion.erp.integration;

import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.time.LocalDate;

public class DefaultImportXLSXAction extends DefaultImportAction {

    public final static int A = 0, B = 1, C = 2, D = 3, E = 4, F = 5, G = 6, H = 7, I = 8, J = 9, K = 10, L = 11, M = 12,
            N = 13, O = 14, P = 15, Q = 16, R = 17, S = 18, T = 19, U = 20, V = 21, W = 22, X = 23, Y = 24, Z = 25,
            AA = 26, AB = 27, AC = 28, AD = 29, AE = 30, AF = 31, AG = 32, AH = 33, AI = 34, AJ = 35, AK = 36, AL = 37, AM = 38, AN = 39, AO = 40,
            AQ = 42, AS = 44, AX = 49;

    public DefaultImportXLSXAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultImportXLSXAction(ScriptingLogicsModule LM, ValueClass valueClass) {
        super(LM, valueClass);
    }

    public DefaultImportXLSXAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
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
        switch (xssfCell.getCellTypeEnum()) {
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
        switch (xssfCell.getCellTypeEnum()) {
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

    protected LocalDate getXLSXDateFieldValue(XSSFSheet sheet, Integer row, Integer cell, Date defaultValue) throws ParseException {
        if (cell != null) {
            XSSFRow xssfRow = sheet.getRow(row);
            if (xssfRow != null) {
                XSSFCell xssfCell = xssfRow.getCell(cell);
                if (xssfCell != null) {
                    switch (xssfCell.getCellTypeEnum()) {
                        case ERROR:
                            return null;
                        case NUMERIC:
                            return sqlDateToLocalDate(new Date(xssfCell.getDateCellValue().getTime()));
                        default:
                            return sqlDateToLocalDate(parseDate(getXLSXFieldValue(sheet, row, cell, String.valueOf(defaultValue))));
                    }
                }
            }
        }
        return null;
    }
}