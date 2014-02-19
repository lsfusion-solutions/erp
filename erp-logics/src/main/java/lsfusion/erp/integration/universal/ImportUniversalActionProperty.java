package lsfusion.erp.integration.universal;

import jxl.CellType;
import jxl.NumberCell;
import jxl.NumberFormulaCell;
import jxl.Sheet;
import lsfusion.base.IOUtils;
import lsfusion.erp.integration.DefaultImportActionProperty;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.xBaseJ.DBF;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;

public abstract class ImportUniversalActionProperty extends DefaultImportActionProperty {

    // syntax : 
    // "=xxx" - constant value  
    // "=CDT" - currentDateTime
    // "xxx^(1,6) - substring(1,6)
    // "xxx+yyy" - concatenate (for strings) or sum (for numbers)
    // "xxx-yyy" - subtract (for numbers)
    // "xxx/yyy" - divide (for numbers)
    // "xxx*yyy" - multiply (for numbers)
    // "xxx | yyy" - yyy == null ? xxx : yyy
    // "xxx~d=1~m=12~y=2006" - value ~ d= default value for day ~ m= for month ~ y= for year
    // "xxx[-2]" - round up xxx with scale = -2 . [scale] is always at the end of numeric expression, it rounds the result of the whole expression

    public ImportUniversalActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, valueClass);
    }

    public ImportUniversalActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    String splitPattern = "\\^\\(|\\)|,";
    String substringPattern = ".*\\^\\(\\d+,(?:\\d+)?\\)";
    String datePatternPattern = "(.*)(~(.*))+";
    String roundedPattern = "(.*)\\[(\\-?)\\d+\\]";
    DecimalFormat decimalFormat = new DecimalFormat("#.#####");
    String currentTimestamp;

    protected String getCSVFieldValue(String[] values, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        return getCSVFieldValue(values, importColumnDetail, row, null);
    }

    protected String getCSVFieldValue(String[] values, ImportColumnDetail importColumnDetail, int row, String defaultValue) throws UniversalImportException {
        return getCSVFieldValue(values, importColumnDetail, row, defaultValue, false);
    }

    protected String getCSVFieldValue(String[] values, ImportColumnDetail importColumnDetail, int row, String defaultValue, boolean isNumeric) throws UniversalImportException {

        try {

            if (importColumnDetail == null) return defaultValue;
            String result = "";
            for (String cell : importColumnDetail.indexes) {
                if (cell == null) return defaultValue;
                String value;
                if (isConstantValue(cell))
                    value = parseConstantFieldPattern(cell);
                else if (isRoundedValue(cell)) {
                    String[] splittedCell = cell.split("\\[|\\]");
                    value = getRoundedValue(getCSVFieldValue(values, parseIndex(splittedCell[0]), null, null, ""), splittedCell[1]);
                } else if (isDivisionValue(cell)) {
                    String[] splittedField = cell.split("/");
                    BigDecimal dividedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getCSVBigDecimalFieldValue(values, importColumnDetail.modify(arg.trim(), arg.trim()), row);
                        dividedValue = dividedValue == null ? argument : safeDivide(dividedValue, argument);
                    }
                    value = formatValue(dividedValue);
                } else if (isMultiplyValue(cell)) {
                    String[] splittedField = cell.split("\\*");
                    BigDecimal multipliedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getCSVBigDecimalFieldValue(values, importColumnDetail.modify(arg.trim(), arg.trim()), row);
                        multipliedValue = multipliedValue == null ? argument : safeMultiply(multipliedValue, argument);
                    }
                    value = formatValue(multipliedValue);
                } else if (isSubtractValue(cell)) {
                    String[] splittedField = cell.split("\\-");
                    BigDecimal subtractedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getCSVBigDecimalFieldValue(values, importColumnDetail.modify(arg.trim(), arg.trim()), row);
                        subtractedValue = subtractedValue == null ? argument : safeSubtract(subtractedValue, argument);
                    }
                    value = formatValue(subtractedValue);
                } else if (isOrValue(cell)) {
                    value = "";
                    String[] splittedField = cell.split("\\|");
                    for (int i = splittedField.length - 1; i >= 0; i--) {
                        String orValue = getCSVFieldValue(values, parseIndex(splittedField[i]), null, null, null);
                        if (orValue != null) {
                            value = orValue;
                            break;
                        }
                    }
                } else if (isSubstringValue(cell)) {
                    String[] splittedCell = cell.split(splitPattern);
                    value = getCSVFieldValue(values, parseIndex(splittedCell[0]), splittedCell.length > 1 ? parseIndex(splittedCell[1]) : null, splittedCell.length > 2 ? parseIndex(splittedCell[2]) : null, "");
                } else if (isDatePatternedValue(cell)) {
                    String[] splittedCell = cell.split("~");
                    Calendar calendar = Calendar.getInstance();
                    Date date = parseDate(getCSVFieldValue(values, importColumnDetail, parseIndex(splittedCell[0])));
                    if (date != null) {
                        calendar.setTime(date);
                        return parseDatePattern(splittedCell, calendar);
                    } else return null;
                } else {
                    value = getCSVFieldValue(values, parseIndex(cell), null, null, "");
                }
                result = trySum(result, value, isNumeric);
            }
            return result.isEmpty() ? defaultValue : result;
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected String getCSVFieldValue(String[] values, Integer index, Integer from, Integer to, String defaultValue) {
        if (index == null) return defaultValue;
        return values.length <= index ? defaultValue : getSubstring(values[index], from, to);
    }

    protected BigDecimal getCSVBigDecimalFieldValue(String[] values, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        if (importColumnDetail == null) return null;
        return getCSVBigDecimalFieldValue(values, importColumnDetail, importColumnDetail.indexes[0], row);
    }

    protected BigDecimal getCSVBigDecimalFieldValue(String[] values, ImportColumnDetail importColumnDetail, String index, int row) throws UniversalImportException {
        try {
            return parseBigDecimal(getCSVFieldValue(values, parseIndex(index), null, null, null));
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail.field, index, row, e);
        }
    }

    protected Date getCSVDateFieldValue(String[] values, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
       return getCSVDateFieldValue(values, importColumnDetail, row, null);
    }
    
    protected Date getCSVDateFieldValue(String[] values, ImportColumnDetail importColumnDetail, int row, Date defaultValue) throws UniversalImportException {
        if (importColumnDetail == null) return defaultValue;
        return getCSVDateFieldValue(values, importColumnDetail, importColumnDetail.indexes[0], row, defaultValue);
    }

    protected Date getCSVDateFieldValue(String[] values, ImportColumnDetail importColumnDetail, String index, int row, Date defaultDate) throws UniversalImportException {
        try {
            return parseDate(getCSVFieldValue(values, parseIndex(index), null, null, null), defaultDate);
        } catch (ParseException e) {
            throw new UniversalImportException(importColumnDetail.field, index, row, e);
        }
    }

    protected String getXLSFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        return getXLSFieldValue(sheet, row, importColumnDetail, null);
    }

    protected String getXLSFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail, String defaultValue) throws UniversalImportException {
        return getXLSFieldValue(sheet, row, importColumnDetail, defaultValue, false);
    }
    
    protected String getXLSFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail, String defaultValue, boolean isNumeric) throws UniversalImportException {
        try {
            if (importColumnDetail == null) return defaultValue;
            String result = "";
            for (String cell : importColumnDetail.indexes) {
                if (cell == null) return defaultValue;
                String value;
                if (isConstantValue(cell))
                    value = parseConstantFieldPattern(cell);
                else if (isRoundedValue(cell)) {
                    String[] splittedCell = cell.split("\\[|\\]");
                    value = getRoundedValue(getXLSFieldValue(sheet, importColumnDetail, row, parseIndex(splittedCell[0]), null, null, ""), splittedCell[1]);
                } else if (isDivisionValue(cell)) {
                    String[] splittedField = cell.split("/");
                    BigDecimal dividedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getXLSBigDecimalFieldValue(sheet, row, importColumnDetail.modify(arg.trim(), arg.trim()));
                        dividedValue = dividedValue == null ? argument : safeDivide(dividedValue, argument);
                    }
                    value = formatValue(dividedValue);
                } else if (isMultiplyValue(cell)) {
                    String[] splittedField = cell.split("\\*");
                    BigDecimal multipliedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getXLSBigDecimalFieldValue(sheet, row, importColumnDetail.modify(arg.trim(), arg.trim()));
                        multipliedValue = multipliedValue == null ? argument : safeMultiply(multipliedValue, argument);
                    }
                    value = formatValue(multipliedValue);
                } else if (isSubtractValue(cell)) {
                    String[] splittedField = cell.split("\\-");
                    BigDecimal subtractedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getXLSBigDecimalFieldValue(sheet, row, importColumnDetail.modify(arg.trim(), arg.trim()));
                        subtractedValue = subtractedValue == null ? argument : safeSubtract(subtractedValue, argument);
                    }
                    value = formatValue(subtractedValue);
                } else if (isOrValue(cell)) {
                    value = "";
                    String[] splittedField = cell.split("\\|");
                    for (int i = splittedField.length - 1; i >= 0; i--) {
                        String orValue = getXLSFieldValue(sheet, importColumnDetail, row, parseIndex(splittedField[i]), null, null, null);
                        if (orValue != null) {
                            value = orValue;
                            break;
                        }
                    }
                } else if (isSubstringValue(cell)) {
                    String[] splittedCell = cell.split(splitPattern);
                    value = getXLSFieldValue(sheet, importColumnDetail, row, parseIndex(splittedCell[0]), splittedCell.length > 1 ? parseIndex(splittedCell[1]) : null, splittedCell.length > 2 ? parseIndex(splittedCell[2]) : null, "");
                } else if (isDatePatternedValue(cell)) {
                    String[] splittedCell = cell.split("~");
                    Calendar calendar = Calendar.getInstance();
                    Date date = parseDate(getXLSFieldValue(sheet, row, importColumnDetail.modify(splittedCell[0], splittedCell[0])));
                    if (date != null) {
                        calendar.setTime(date);
                        return parseDatePattern(splittedCell, calendar);
                    } else return null;
                } else {
                    value = getXLSFieldValue(sheet, importColumnDetail, row, parseIndex(cell), null, null, "");
                }
                result = trySum(result, value, isNumeric);
            }
            return result.isEmpty() ? defaultValue : result;
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected String getXLSFieldValue(Sheet sheet, ImportColumnDetail importColumnDetail, Integer row, Integer column, Integer from, Integer to, 
                                      String defaultValue) throws UniversalImportException {
        try {
            if (column == null) return defaultValue;
            jxl.Cell cell = sheet.getCell(column, row);
            if (cell == null) return defaultValue;
            String result;
            CellType cellType = cell.getType();
            if (cellType.equals(CellType.NUMBER)) {
                result = decimalFormat.format(((NumberCell) cell).getValue());
            } else if (cellType.equals(CellType.NUMBER_FORMULA)) {
                result = decimalFormat.format(((NumberFormulaCell) cell).getValue());
            } else {
                result = (cell.getContents().isEmpty()) ? defaultValue : cell.getContents().trim();
            }
            return getSubstring(result, from, to);
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected BigDecimal getXLSBigDecimalFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        return parseBigDecimal(getXLSFieldValue(sheet, row, importColumnDetail, null, true));
    }

    protected Date getXLSDateFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        return getXLSDateFieldValue(sheet, row, importColumnDetail, null);
    }
    
    protected Date getXLSDateFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail, Date defaultDate) throws UniversalImportException {
        try {
            return parseDate(getXLSFieldValue(sheet, row, importColumnDetail), defaultDate);
        } catch (ParseException e) {
            throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        return getXLSXFieldValue(sheet, row, importColumnDetail, null);
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail, String defaultValue) throws UniversalImportException {
        return getXLSXFieldValue(sheet, row, importColumnDetail, false, defaultValue);
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail, boolean isDate, String defaultValue) throws UniversalImportException {
        return getXLSXFieldValue(sheet, row, importColumnDetail, isDate, defaultValue, false);
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, boolean isDate, ImportColumnDetail importColumnDetail, boolean isNumeric) throws UniversalImportException {
        return getXLSXFieldValue(sheet, row, isDate, importColumnDetail, null, isNumeric);
    }
    
    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, boolean isDate, ImportColumnDetail importColumnDetail, String defaultValue, boolean isNumeric) throws UniversalImportException {
        return getXLSXFieldValue(sheet, row, importColumnDetail, isDate, defaultValue, isNumeric);
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail, boolean isDate, 
                                       String defaultValue, boolean isNumeric) throws UniversalImportException {
        try {
            if (importColumnDetail == null) return defaultValue;
            String result = "";
            for (String cell : importColumnDetail.indexes) {
                if (cell == null) return defaultValue;
                String value;
                if (isConstantValue(cell))
                    value = parseConstantFieldPattern(cell);
                else if (isRoundedValue(cell)) {
                    String[] splittedCell = cell.split("\\[|\\]");
                    value = getRoundedValue(getXLSXFieldValue(sheet, importColumnDetail, row, parseIndex(splittedCell[0]), null, null, false, ""), splittedCell[1]);
                } else if (isDivisionValue(cell)) {
                    String[] splittedField = cell.split("/");
                    BigDecimal dividedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getXLSXBigDecimalFieldValue(sheet, row, importColumnDetail.modify(arg.trim(), arg.trim()));
                        dividedValue = dividedValue == null ? argument : safeDivide(dividedValue, argument);
                    }
                    value = formatValue(dividedValue);
                } else if (isMultiplyValue(cell)) {
                    String[] splittedField = cell.split("\\*");
                    BigDecimal multipliedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getXLSXBigDecimalFieldValue(sheet, row, importColumnDetail.modify(arg.trim(), arg.trim()));
                        multipliedValue = multipliedValue == null ? argument : safeMultiply(multipliedValue, argument);
                    }
                    value = formatValue(multipliedValue);
                } else if (isSubtractValue(cell)) {
                    String[] splittedField = cell.split("\\-");
                    BigDecimal subtractedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getXLSXBigDecimalFieldValue(sheet, row, importColumnDetail.modify(arg.trim(), arg.trim()));
                        subtractedValue = subtractedValue == null ? argument : safeSubtract(subtractedValue, argument);
                    }
                    value = formatValue(subtractedValue);
                } else if (isOrValue(cell)) {
                    value = "";
                    String[] splittedField = cell.split("\\|");
                    for (int i = splittedField.length - 1; i >= 0; i--) {
                        String orValue = getXLSXFieldValue(sheet, importColumnDetail, row, parseIndex(splittedField[i]), null, null, isDate, null);
                        if (orValue != null) {
                            value = orValue;
                            break;
                        }
                    }
                } else if (isSubstringValue(cell)) {
                    String[] splittedCell = cell.split(splitPattern);
                    value = getXLSXFieldValue(sheet, importColumnDetail, row, parseIndex(splittedCell[0]), splittedCell.length > 1 ? parseIndex(splittedCell[1]) : null, splittedCell.length > 2 ? parseIndex(splittedCell[2]) : null, isDate, "");
                } else if (isDatePatternedValue(cell)) {
                    String[] splittedCell = cell.split("~");
                    Calendar calendar = Calendar.getInstance();
                    Date date = parseDate(getXLSXFieldValue(sheet, row, isDate, importColumnDetail.modify(splittedCell[0], splittedCell[0]), false));
                    if (date != null) {
                        calendar.setTime(date);
                        return parseDatePattern(splittedCell, calendar);
                    } else return null;
                } else {
                    value = getXLSXFieldValue(sheet, importColumnDetail, row, parseIndex(cell), null, null, isDate, "");
                }
                result = trySum(result, value, isNumeric);
            }
            return result.isEmpty() ? defaultValue : result;
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, ImportColumnDetail importColumnDetail, Integer row, Integer cell, Integer from, Integer to, boolean isDate, String defaultValue) throws UniversalImportException {
        try {
            if (cell == null) return defaultValue;
            XSSFRow xssfRow = sheet.getRow(row);
            if (xssfRow == null) return defaultValue;
            XSSFCell xssfCell = xssfRow.getCell(cell);
            if (xssfCell == null) return defaultValue;
            String result;
            switch (xssfCell.getCellType()) {
                case Cell.CELL_TYPE_NUMERIC:
                case Cell.CELL_TYPE_FORMULA:    
                    if (isDate)
                        result = formatValue(xssfCell.getDateCellValue());
                    else {
                        result = decimalFormat.format(xssfCell.getNumericCellValue());
                        result = result.endsWith(".0") ? result.substring(0, result.length() - 2) : result;
                    }
                    break;
                case Cell.CELL_TYPE_STRING:
                default:
                    result = (xssfCell.getStringCellValue().isEmpty()) ? defaultValue : xssfCell.getStringCellValue().trim();
                    break;
            }
            return getSubstring(result, from, to);
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected BigDecimal getXLSXBigDecimalFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        return parseBigDecimal(getXLSXFieldValue(sheet, row, importColumnDetail));
    }

    protected BigDecimal getXLSXBigDecimalFieldValue(XSSFSheet sheet, ImportColumnDetail importColumnDetail, Integer row, String cell) throws UniversalImportException {
        if (cell == null) return null;
        return getXLSXBigDecimalFieldValue(sheet, importColumnDetail, row, parseIndex(cell), null);
    }

    protected BigDecimal getXLSXBigDecimalFieldValue(XSSFSheet sheet, ImportColumnDetail importColumnDetail, Integer row, Integer cell, BigDecimal defaultValue) throws UniversalImportException {
        try {
            if (cell == null) return defaultValue;
            XSSFRow xssfRow = sheet.getRow(row);
            if (xssfRow == null) return defaultValue;
            XSSFCell xssfCell = xssfRow.getCell(cell);
            if (xssfCell == null) return defaultValue;
            switch (xssfCell.getCellType()) {
                case Cell.CELL_TYPE_NUMERIC:
                case Cell.CELL_TYPE_FORMULA:
                    return BigDecimal.valueOf(xssfCell.getNumericCellValue());
                case Cell.CELL_TYPE_STRING:
                default:
                    String result = xssfCell.getStringCellValue().trim();
                    return result.isEmpty() ? defaultValue : new BigDecimal(result);
            }
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected Date getXLSXDateFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        return getXLSXDateFieldValue(sheet, row, importColumnDetail, null);
    }
    
    protected Date getXLSXDateFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail, Date defaultDate) throws UniversalImportException {
        try {
            return parseDate(getXLSXFieldValue(sheet, row, true, importColumnDetail, null, false), defaultDate);
        } catch (ParseException e) {
            throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected String getDBFFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset) throws UniversalImportException {
        return getDBFFieldValue(importFile, importColumnDetail, row, charset, null);
    }

    protected String getDBFFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset, String defaultValue) throws UniversalImportException {
        return getDBFFieldValue(importFile, importColumnDetail, importColumnDetail.indexes, row, charset, defaultValue, false);
    }

    protected String getDBFFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, String[] columns, int row, 
                                      String charset, String defaultValue, boolean isNumeric) throws UniversalImportException {
        try {
            if (importColumnDetail == null) return defaultValue;
            String result = "";
            for (String column : columns) {
                if (column == null) return defaultValue;
                String value;
                if (isConstantValue(column))
                    value = parseConstantFieldPattern(column);
                else if (isRoundedValue(column)) {
                    String[] splittedField = column.split("\\[|\\]");
                    value = getRoundedValue(getDBFFieldValue(importFile, importColumnDetail, new String[]{splittedField[0]}, row, charset, "", true), splittedField[1]);
                } else if (isDivisionValue(column)) {
                    String[] splittedField = column.split("/");
                    BigDecimal dividedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getDBFBigDecimalFieldValue(importFile, importColumnDetail.modify(arg.trim(), arg.trim()), row, charset);
                        dividedValue = dividedValue == null ? argument : safeDivide(dividedValue, argument);
                    }
                    value = formatValue(dividedValue);
                } else if (isMultiplyValue(column)) {
                    String[] splittedField = column.split("\\*");
                    BigDecimal multipliedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getDBFBigDecimalFieldValue(importFile, importColumnDetail.modify(arg.trim(), arg.trim()), row, charset);
                        multipliedValue = multipliedValue == null ? argument : safeMultiply(multipliedValue, argument);
                    }
                    value = formatValue(multipliedValue);
                } else if (isSubtractValue(column)) {
                    String[] splittedField = column.split("\\-");
                    BigDecimal subtractedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getDBFBigDecimalFieldValue(importFile, importColumnDetail.modify(arg.trim(), arg.trim()), row, charset);
                        subtractedValue = subtractedValue == null ? argument : safeSubtract(subtractedValue, argument);
                    }
                    value = formatValue(subtractedValue);
                } else if (isOrValue(column)) {
                    value = "";
                    String[] splittedField = column.split("\\|");
                    for (int i = splittedField.length - 1; i >= 0; i--) {
                        String orValue = getDBFFieldValue(importFile, importColumnDetail, new String[]{splittedField[i]}, i, charset, null, isNumeric);
                        if (orValue != null) {
                            value = orValue;
                            break;
                        }
                    }
                } else if (isSubstringValue(column)) {
                    String[] splittedField = column.split(splitPattern);
                    value = getSubstring(getDBFFieldValue(importFile, importColumnDetail, new String[]{splittedField[0]}, row, charset, defaultValue, isNumeric),
                            splittedField.length > 1 ? parseIndex(splittedField[1]) : null, splittedField.length > 2 ? parseIndex(splittedField[2]) : null);
                } else if (isDatePatternedValue(column)) {
                    String[] splittedField = column.split("~");
                    Calendar calendar = Calendar.getInstance();
                    Date date = parseDate(getDBFFieldValue(importFile, importColumnDetail, new String[]{splittedField[0]}, row, charset, defaultValue, false));
                    if (date != null) {
                        calendar.setTime(date);
                        return parseDatePattern(splittedField, calendar);
                    } else return null;
                } else {
                    value = new String(importFile.getField(column).getBytes(), charset).trim();
                }
                result = trySum(result, value, isNumeric);
            }
            return result.isEmpty() ? defaultValue : result;
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset) throws UniversalImportException {
        return getDBFBigDecimalFieldValue(importFile, importColumnDetail, row, charset, null);
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset, BigDecimal defaultValue) throws UniversalImportException {
        try {
            if (importColumnDetail == null) return defaultValue;
            return parseBigDecimal(getDBFFieldValue(importFile, importColumnDetail, importColumnDetail.indexes, row, charset, null, true), defaultValue);
        } catch (NumberFormatException e) {
            throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected Date getDBFDateFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset) throws UniversalImportException {
        return getDBFDateFieldValue(importFile, importColumnDetail, row, charset, null);
    }

    protected Date getDBFDateFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset, Date defaultDate) throws UniversalImportException {
        try {
            return parseDate(getDBFFieldValue(importFile, importColumnDetail, importColumnDetail.indexes, row, charset, null, false), defaultDate);
        } catch (ParseException e) {
            throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected String getJDBFFieldValue(Object[] entry, Map<String, Integer> fieldNamesMap, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        return getJDBFFieldValue(entry, fieldNamesMap, importColumnDetail, row, null);
    }

    protected String getJDBFFieldValue(Object[] entry, Map<String, Integer> fieldNamesMap, ImportColumnDetail importColumnDetail, int row, String defaultValue) throws UniversalImportException {
        return getJDBFFieldValue(entry, fieldNamesMap, importColumnDetail, importColumnDetail.indexes, row, defaultValue, false, true);
    }

    protected String getJDBFFieldValue(Object[] entry, Map<String, Integer> fieldNamesMap,
                                       ImportColumnDetail importColumnDetail, String[] columns, int row,
                                       String defaultValue, boolean isNumeric, boolean zeroIsNull) throws UniversalImportException {
        try {
            if (importColumnDetail == null) return defaultValue;
            String result = "";
            for (String column : columns) {
                if (column == null) return defaultValue;
                String value;
                if (isConstantValue(column))
                    value = parseConstantFieldPattern(column);
                else if (isRoundedValue(column)) {
                    String[] splittedField = column.split("\\[|\\]");
                    value = getRoundedValue(getJDBFFieldValue(entry, fieldNamesMap, importColumnDetail, new String[]{splittedField[0]}, row, "", true, zeroIsNull), splittedField[1]);
                } else if (isDivisionValue(column)) {
                    String[] splittedField = column.split("/");
                    BigDecimal dividedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getJDBFBigDecimalFieldValue(entry, fieldNamesMap, importColumnDetail.modify(arg.trim(), arg.trim()), row);
                        dividedValue = dividedValue == null ? argument : safeDivide(dividedValue, argument);
                    }
                    value = formatValue(dividedValue);
                } else if (isMultiplyValue(column)) {
                    String[] splittedField = column.split("\\*");
                    BigDecimal multipliedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getJDBFBigDecimalFieldValue(entry, fieldNamesMap, importColumnDetail.modify(arg.trim(), arg.trim()), row);
                        multipliedValue = multipliedValue == null ? argument : safeMultiply(multipliedValue, argument);
                    }
                    value = formatValue(multipliedValue);
                } else if (isSubtractValue(column)) {
                    String[] splittedField = column.split("\\-");
                    BigDecimal subtractedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getJDBFBigDecimalFieldValue(entry, fieldNamesMap, importColumnDetail.modify(arg.trim(), arg.trim()), row);
                        subtractedValue = subtractedValue == null ? argument : safeSubtract(subtractedValue, argument);
                    }
                    value = formatValue(subtractedValue);
                } else if (isOrValue(column)) {
                    value = "";
                    String[] splittedField = column.split("\\|");
                    for (int i = splittedField.length - 1; i >= 0; i--) {
                        String orValue = getJDBFFieldValue(entry, fieldNamesMap, importColumnDetail, new String[]{splittedField[i]}, i, null, isNumeric, zeroIsNull);
                        if (orValue != null) {
                            value = orValue;
                            break;
                        }
                    }
                } else if (isSubstringValue(column)) {
                    String[] splittedField = column.split(splitPattern);
                    value = getSubstring(getJDBFFieldValue(entry, fieldNamesMap, importColumnDetail, new String[]{splittedField[0]}, row, defaultValue, isNumeric, zeroIsNull),
                            splittedField.length > 1 ? parseIndex(splittedField[1]) : null, splittedField.length > 2 ? parseIndex(splittedField[2]) : null);
                } else if (isDatePatternedValue(column)) {
                    String[] splittedField = column.split("~");
                    Calendar calendar = Calendar.getInstance();
                    Date date = parseDate(getJDBFFieldValue(entry, fieldNamesMap, importColumnDetail, new String[]{splittedField[0]}, row, defaultValue, false, zeroIsNull));
                    if (date != null) {
                        calendar.setTime(date);
                        return parseDatePattern(splittedField, calendar);
                    } else return null;
                } else {
                    value = formatValue(entry[fieldNamesMap.get(column.toUpperCase())]);
                    if(value != null && value.equals("0") && !isNumeric && zeroIsNull) {
                        value = null;
                    }
                }
                result = trySum(result, value, isNumeric);
            }
            return result.isEmpty() ? defaultValue : result;
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected BigDecimal getJDBFBigDecimalFieldValue(Object[] entry, Map<String, Integer> fieldNamesMap, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        return getJDBFBigDecimalFieldValue(entry, fieldNamesMap, importColumnDetail, row, null);
    }

    protected BigDecimal getJDBFBigDecimalFieldValue(Object[] entry, Map<String, Integer> fieldNamesMap, ImportColumnDetail importColumnDetail, String column, int row) throws UniversalImportException {
        return getJDBFBigDecimalFieldValue(entry, fieldNamesMap, importColumnDetail, column, row, null);
    }

    protected BigDecimal getJDBFBigDecimalFieldValue(Object[] entry, Map<String, Integer> fieldNamesMap, ImportColumnDetail importColumnDetail, String column, int row, BigDecimal defaultValue) throws UniversalImportException {
        return getJDBFBigDecimalFieldValue(entry, fieldNamesMap, new ImportColumnDetail(importColumnDetail.field, column, false), row, defaultValue);
    }

    protected BigDecimal getJDBFBigDecimalFieldValue(Object[] entry, Map<String, Integer> fieldNamesMap, ImportColumnDetail importColumnDetail, int row, BigDecimal defaultValue) throws UniversalImportException {
        try {
            if (importColumnDetail == null) return defaultValue;
            return parseBigDecimal(getJDBFFieldValue(entry, fieldNamesMap, importColumnDetail, importColumnDetail.indexes, row, null, true, false), defaultValue);
        } catch (NumberFormatException e) {
            throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected Date getJDBFDateFieldValue(Object[] entry, Map<String, Integer> fieldNamesMap, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        return getJDBFDateFieldValue(entry, fieldNamesMap, importColumnDetail, row, null);
    }

    protected Date getJDBFDateFieldValue(Object[] entry, Map<String, Integer> fieldNamesMap, ImportColumnDetail importColumnDetail, int row, Date defaultDate) throws UniversalImportException {
        try {
            return parseDate(getJDBFFieldValue(entry, fieldNamesMap, importColumnDetail, importColumnDetail.indexes, row, null, false, false), defaultDate);
        } catch (ParseException e) {
            throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    public String getDBFCharset(File file) throws IOException {
        byte charsetByte = IOUtils.getFileBytes(file)[29];
        String charset;
        switch (charsetByte) {
            case (byte) 0x65:
                charset = "cp866";
                break;
            case (byte) 0xC9:
                charset = "cp1251";
                break;
            default:
                charset = "cp866";
        }
        return charset;
    }

    private Integer parseIndex(String index) {
        try {
            return Integer.parseInt(index) - 1;
        } catch (Exception e) {
            return null;
        }
    }

    private String getSubstring(String value, Integer from, Integer to) {
        return (value == null || from == null || from < 0 || from > value.length()) ? value :
                ((to == null || to > value.length())) ? value.substring(Math.min(value.length(), from)).trim() : value.substring(from, Math.min(value.length(), to + 1)).trim();
    }

    private String getRoundedValue(String value, String scale) throws UniversalImportException {
        return decimalFormat.format(new BigDecimal(value).setScale(Integer.parseInt(scale), RoundingMode.HALF_UP));
    }

    private String parseDatePattern(String[] splittedField, Calendar calendar) {
        for (int i = 1; i < splittedField.length; i++) {
            String pattern = splittedField[i];
            if (pattern.startsWith("y="))
                calendar.set(Calendar.YEAR, parseDateFieldPattern("y=", pattern, calendar.get(Calendar.YEAR)));
            else if (pattern.startsWith("m="))
                calendar.set(Calendar.MONTH, parseDateFieldPattern("m=", pattern, 12) - 1);
        }
        for (int i = 1; i < splittedField.length; i++) {
            String pattern = splittedField[i];
            if (pattern.startsWith("d="))
                calendar.set(Calendar.DAY_OF_MONTH, parseDateFieldPattern("d=", pattern, calendar.getActualMaximum(Calendar.DAY_OF_MONTH)));
        }
        return new SimpleDateFormat("dd.MM.yyyy").format(calendar.getTime());
    }

    private int parseDateFieldPattern(String type, String value, int defaultValue) {
        try {
            return Math.min(Integer.parseInt(value.replace(type, "")), defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String parseConstantFieldPattern(String value) {
        return (value.toLowerCase().contains("cdt") && currentTimestamp != null) ? value.replaceAll("(\\=CDT)|(\\=cdt)", currentTimestamp) : value.substring(1);
    }

    private boolean isConstantValue(String input) {
        return input != null && input.startsWith("=") && !isRoundedValue(input) && !isDivisionValue(input)
                && !isMultiplyValue(input) && !(isSubtractValue(input)) && !isOrValue(input) && !isSubstringValue(input)
                && !isDatePatternedValue(input);
    }

    private boolean isRoundedValue(String input) {
        return input.matches(roundedPattern);
    }

    private boolean isDivisionValue(String input) {
        return input != null && input.contains("/");
    }

    private boolean isMultiplyValue(String input) {
        return input != null && input.contains("*");
    }

    private boolean isSubtractValue(String input) {
        return input != null && input.contains("-");
    }    

    private boolean isOrValue(String input) {
        return input != null && input.contains("|");
    }

    private boolean isSubstringValue(String input) {
        return input.matches(substringPattern);
    }

    private boolean isDatePatternedValue(String input) {
        return input.matches(datePatternPattern);
    }

    protected boolean getReplaceOnlyNull(Map<String, ImportColumnDetail> importColumns, String columnName) {
        ImportColumnDetail column = importColumns.get(columnName);
        return column != null && column.replaceOnlyNull;
    }

    private BigDecimal parseBigDecimal(String value) {
        return parseBigDecimal(value, null);
    }
    
    private BigDecimal parseBigDecimal(String value, BigDecimal defaultValue) {
        try {
            return value == null ? defaultValue : new BigDecimal(value.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    //чит для того, чтобы обрабатывать "без НДС" как 0
    protected BigDecimal parseVAT(String value) {
        if (value == null) return null;
        if ((value.toLowerCase().replace(" ", "").equals("безндс")) || (value.toLowerCase().replace(" ", "").equals("бе")))
            return BigDecimal.ZERO;
        else return parseBigDecimal(value.replace("%", ""));
    }
    
    private String trySum(String value1, String value2, boolean isNumeric) {
        if (value2 == null || value2.isEmpty())
            return value1;
        try {
               return isNumeric ? String.valueOf(new BigDecimal(value1).add(new BigDecimal(value2))) : (value1 + value2);
        } catch(Exception e) {
            return value1 + value2;
        }
    }
    
    protected String formatValue(Object value) {
        if (value == null) return null;
        if (value instanceof Date || value instanceof java.util.Date) {
            return new SimpleDateFormat("dd.MM.yyyy").format(value);
        } else return String.valueOf(value);
    }
}