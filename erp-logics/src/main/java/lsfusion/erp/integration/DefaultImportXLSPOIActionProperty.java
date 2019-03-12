package lsfusion.erp.integration;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.ParseException;

public class DefaultImportXLSPOIActionProperty extends DefaultImportActionProperty {

    public final static int A = 0, B = 1, C = 2, D = 3, E = 4, F = 5, G = 6, H = 7, I = 8, J = 9, K = 10, L = 11, M = 12,
            N = 13, O = 14, P = 15, Q = 16, R = 17, S = 18, T = 19, U = 20, V = 21, W = 22, X = 23, Y = 24, Z = 25,
            AA = 26, AB = 27, AC = 28, AD = 29, AE = 30, AF = 31, AG = 32, AH = 33, AI = 34, AJ = 35, AK = 36, AL = 37, AM = 38, AN = 39, AO = 40,
            AP = 41, AQ = 42, AR = 43, AS = 44, AT = 45, AU = 46, AV = 47, AW = 48, AX = 49, AY = 50, AZ = 51, BA = 52, BB = 53, BC = 54;
    
    public DefaultImportXLSPOIActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultImportXLSPOIActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) {
        super(LM, valueClass);
    }

    public DefaultImportXLSPOIActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    protected String getXLSFieldValue(HSSFSheet sheet, int row, int cell) throws ParseException {
        return getXLSFieldValue(sheet, row, cell, null);
    }

    protected String getXLSFieldValue(HSSFSheet sheet, int row, int cell, String defaultValue) {
        HSSFRow hssfRow = sheet.getRow(row);
        if (hssfRow == null) return defaultValue;
        HSSFCell hssfCell = hssfRow.getCell(cell);
        if (hssfCell == null) return defaultValue;
        switch (hssfCell.getCellType()) {
            case org.apache.poi.ss.usermodel.Cell.CELL_TYPE_NUMERIC:
            case org.apache.poi.ss.usermodel.Cell.CELL_TYPE_FORMULA:
                String result;
                try {
                    result = new DecimalFormat("#.#####").format(hssfCell.getNumericCellValue());
                } catch (Exception e) {
                    result = hssfCell.getStringCellValue().isEmpty() ? defaultValue : trim(hssfCell.getStringCellValue());
                }
                return result.endsWith(".0") ? result.substring(0, result.length() - 2) : result;
            case org.apache.poi.ss.usermodel.Cell.CELL_TYPE_ERROR:
                return defaultValue;
            case org.apache.poi.ss.usermodel.Cell.CELL_TYPE_STRING:
            default:
                return (hssfCell.getStringCellValue().isEmpty()) ? defaultValue : trim(hssfCell.getStringCellValue());
        }
    }

    protected BigDecimal getXLSBigDecimalFieldValue(HSSFSheet sheet, int row, int cell) throws ParseException {
        return getXLSBigDecimalFieldValue(sheet, row, cell, null);
    }
    
    protected BigDecimal getXLSBigDecimalFieldValue(HSSFSheet sheet, int row, int cell, BigDecimal defaultValue) throws ParseException {
        HSSFRow hssfRow = sheet.getRow(row);
        if (hssfRow == null) return defaultValue;
        HSSFCell hssfCell = hssfRow.getCell(cell);
        return (hssfCell == null || hssfCell.getCellType() != org.apache.poi.ss.usermodel.Cell.CELL_TYPE_NUMERIC) ? defaultValue : BigDecimal.valueOf(hssfCell.getNumericCellValue());
    }

    protected Integer getXLSIntegerFieldValue(HSSFSheet sheet, Integer row, Integer column) throws ParseException {
        BigDecimal value = getXLSBigDecimalFieldValue(sheet, row, column, null);
        return value == null ? null : value.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    protected BigDecimal getXLSStrictBigDecimalFieldValue(HSSFSheet sheet, Integer row, Integer column) throws ParseException {
        Integer value = getXLSIntegerFieldValue(sheet, row, column);
        return value == null ? null : new BigDecimal(value);
    }

    protected Date getXLSDateFieldValue(HSSFSheet sheet, Integer row, Integer column) throws ParseException {
        return getXLSDateFieldValue(sheet, row, column, null);
    }

    protected Date getXLSDateFieldValue(HSSFSheet sheet, Integer row, Integer column, Date defaultValue) throws ParseException {
        if (row == null || column == null) return defaultValue;
        try {
            HSSFRow hssfRow = sheet.getRow(row);
            if (hssfRow == null) return defaultValue;
            HSSFCell hssfCell = hssfRow.getCell(column);
            if (hssfCell == null) return defaultValue;
            if (hssfCell.getCellType() == org.apache.poi.ss.usermodel.Cell.CELL_TYPE_NUMERIC)
                return new Date(hssfCell.getDateCellValue().getTime());
            return parseDate(getXLSFieldValue(sheet, row, column));
        } catch (Exception e) {
            return parseDate(getXLSFieldValue(sheet, row, column), defaultValue);
        }
    }
}