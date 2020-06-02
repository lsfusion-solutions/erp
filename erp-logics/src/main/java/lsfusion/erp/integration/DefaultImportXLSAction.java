package lsfusion.erp.integration;

import jxl.*;
import lsfusion.base.file.RawFileData;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class DefaultImportXLSAction extends DefaultImportAction {

    public final static int A = 0, B = 1, C = 2, D = 3, E = 4, F = 5, G = 6, H = 7, I = 8, J = 9, K = 10, L = 11, M = 12,
            N = 13, O = 14, P = 15, Q = 16, R = 17, S = 18, T = 19, U = 20, V = 21, W = 22, X = 23, Y = 24, Z = 25,
            AA = 26, AB = 27, AC = 28, AD = 29, AE = 30, AF = 31, AG = 32, AH = 33, AI = 34, AJ = 35, AK = 36, AL = 37, AM = 38, AN = 39, AO = 40,
            AP = 41, AQ = 42, AR = 43, AS = 44, AT = 45, AU = 46, AV = 47, AW = 48, AX = 49, AY = 50, AZ = 51, BA = 52, BB = 53, BC = 54;
    
    public DefaultImportXLSAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultImportXLSAction(ScriptingLogicsModule LM, ValueClass... valueClass) {
        super(LM, valueClass);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
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

    protected Map<String, RawFileData> getXLSImageMap(Sheet sheet) {
        Map<String, RawFileData> imageMap = new HashMap<>();
        int count = sheet.getNumberOfImages();
        for (int i = 0; i < count; i++) {
            Image image = sheet.getDrawing(i); 
            try {
            imageMap.put((int) image.getRow() + "-" + (int) image.getColumn(), new RawFileData(image.getImageData()));
            } catch (ArrayIndexOutOfBoundsException ignored) {               
            }
        }
        return imageMap;
    }

    protected RawFileData getXLSImageFieldValue(Map<String, RawFileData> imageMap, Integer row, Integer column) {
        if (row == null || column == null)
            return null;
        else
            return imageMap.get(row + "-" + column);
    }

}