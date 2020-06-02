package lsfusion.erp.integration;

import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.ss.usermodel.CellType;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.DecimalFormat;

public class DefaultImportXLSPOIAction extends DefaultImportAction {

    public final static int A = 0, B = 1, C = 2, D = 3, E = 4, F = 5, G = 6, H = 7, I = 8, J = 9, K = 10, L = 11, M = 12,
            N = 13, O = 14, P = 15, Q = 16, R = 17, S = 18, T = 19, U = 20, V = 21, W = 22, X = 23, Y = 24, Z = 25,
            AA = 26, AB = 27, AC = 28, AD = 29, AE = 30, AF = 31, AG = 32, AH = 33, AI = 34, AJ = 35, AK = 36, AL = 37, AM = 38, AN = 39, AO = 40,
            AP = 41, AQ = 42, AR = 43, AS = 44, AT = 45, AU = 46, AV = 47, AW = 48, AX = 49, AY = 50, AZ = 51, BA = 52, BB = 53, BC = 54;
    
    public DefaultImportXLSPOIAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultImportXLSPOIAction(ScriptingLogicsModule LM, ValueClass valueClass) {
        super(LM, valueClass);
    }

    public DefaultImportXLSPOIAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    protected String getXLSFieldValue(HSSFSheet sheet, int row, int cell) {
        return getXLSFieldValue(sheet, row, cell, null);
    }

    protected String getXLSFieldValue(HSSFSheet sheet, int row, int cell, String defaultValue) {
        HSSFRow hssfRow = sheet.getRow(row);
        if (hssfRow == null) return defaultValue;
        HSSFCell hssfCell = hssfRow.getCell(cell);
        if (hssfCell == null) return defaultValue;
        switch (hssfCell.getCellTypeEnum()) {
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
        return (hssfCell == null || hssfCell.getCellTypeEnum() != CellType.NUMERIC) ? defaultValue : BigDecimal.valueOf(hssfCell.getNumericCellValue());
    }
}