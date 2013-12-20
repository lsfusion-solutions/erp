package lsfusion.erp.integration;

import jxl.*;
import lsfusion.erp.integration.universal.UniversalImportException;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

public class DefaultImportXLSActionProperty extends DefaultImportActionProperty {

    public DefaultImportXLSActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultImportXLSActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, valueClass);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
    }

    protected String getXLSFieldValue(Sheet sheet, Integer row, Integer column) {
        return getXLSFieldValue(sheet, row, column, null);
    }

    protected String getXLSFieldValue(Sheet sheet, Integer row, Integer column, String defaultValue) {
        if (row == null || column == null) return defaultValue;
        jxl.Cell cell = sheet.getCell(column, row);
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
        if (column == null || row == null) return defaultValue;
        jxl.Cell cell = sheet.getCell(column, row);
        if (cell == null) return defaultValue;
        CellType cellType = cell.getType();
        if (cellType.equals(CellType.NUMBER))
            return new BigDecimal(((NumberCell) cell).getValue());
        else if (cellType.equals(CellType.NUMBER_FORMULA))
            return new BigDecimal(((NumberFormulaCell) cell).getValue());
        else {
            String result = cell.getContents().trim();
            return result.isEmpty() ? defaultValue : new BigDecimal(result);
        }
    }

    protected Date getXLSDateFieldValue(Sheet sheet, Integer row, Integer column) {
        return getXLSDateFieldValue(sheet, row, column, null);
    }
    
    protected Date getXLSDateFieldValue(Sheet sheet, Integer row, Integer column, Date defaultValue) {
        if (row == null || column == null) return defaultValue;
        try {
            return parseDate(getXLSFieldValue(sheet, row, column));
        } catch (ParseException e) {
            return defaultValue;
        }
    }

    protected Map<String, byte[]> getXLSImageMap(Sheet sheet) {
        Map<String, byte[]> imageMap = new HashMap<String, byte[]>();
        int count = sheet.getNumberOfImages();
        for (int i = 0; i < count; i++) {
            Image image = sheet.getDrawing(i); 
            try {
            imageMap.put(String.valueOf((int) image.getRow() + "-" + (int) image.getColumn()), image.getImageData());
            } catch (ArrayIndexOutOfBoundsException ignored) {               
            }
        }
        return imageMap;
    }

    protected byte[] getXLSImageFieldValue(Map<String, byte[]> imageMap, Integer row, Integer column) throws UniversalImportException {
        if (row == null || column == null)
            return null;
        else
            return imageMap.get(row + "-" + column);
    }

}