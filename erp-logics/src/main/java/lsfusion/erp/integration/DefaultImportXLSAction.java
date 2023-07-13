package lsfusion.erp.integration;

import jxl.*;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.classes.ValueClass;

import java.math.BigDecimal;
import java.text.DecimalFormat;

public class DefaultImportXLSAction extends DefaultImportAction {

    public DefaultImportXLSAction(ScriptingLogicsModule LM, ValueClass... valueClass) {
        super(LM, valueClass);
    }

    protected String getXLSFieldValue(Sheet sheet, Integer row, Integer column) {
        return getXLSFieldValue(sheet, row, column, null);
    }

    protected String getXLSFieldValue(Sheet sheet, Integer row, Integer column, String defaultValue) {
        if (row == null || column == null) return defaultValue;
        Cell cell = sheet.getCell(column, row);
        if (cell == null) return defaultValue;
        String result;
        CellType cellType = cell.getType();
        if (cellType.equals(CellType.NUMBER)) {
            result = new DecimalFormat("#.#####").format(((NumberCell) cell).getValue());
        } else if (cellType.equals(CellType.NUMBER_FORMULA)) {
            result = new DecimalFormat("#.#####").format(((NumberFormulaCell) cell).getValue());
        } else {
            result = (cell.getContents().isEmpty()) ? defaultValue : cell.getContents().trim();
        }
        return result;
    }

    protected BigDecimal getXLSBigDecimalFieldValue(Sheet sheet, Integer row, Integer column) {
        return getXLSBigDecimalFieldValue(sheet, row, column, null);
    }

    protected BigDecimal getXLSBigDecimalFieldValue(Sheet sheet, Integer row, Integer column, BigDecimal defaultValue) {
        try {
            if (column == null || row == null) return defaultValue;
            Cell cell = sheet.getCell(column, row);
            if (cell == null) return defaultValue;
            CellType cellType = cell.getType();
            if (cellType.equals(CellType.NUMBER))
                return BigDecimal.valueOf(((NumberCell) cell).getValue());
            else if (cellType.equals(CellType.NUMBER_FORMULA))
                return BigDecimal.valueOf(((NumberFormulaCell) cell).getValue());
            else {
                String result = cell.getContents().trim();
                return result.isEmpty() ? defaultValue : new BigDecimal(result);
            }
        } catch (NumberFormatException e) {
            throw new RuntimeException(String.format("Error parsing cell value: row %s, column %s", row + 1, column + 1), e);
        }
    }

}