package lsfusion.erp.integration.universal;

import jxl.CellType;
import jxl.NumberCell;
import jxl.NumberFormulaCell;
import jxl.Sheet;
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
    // "xxx+yyy" - concatenate
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
                        BigDecimal argument = getCSVBigDecimalFieldValue(values, new ImportColumnDetail(arg.trim(), arg.trim(), importColumnDetail.replaceOnlyNull), row);
                        dividedValue = dividedValue == null ? argument : (argument == null ? null : safeDivide(dividedValue, argument));
                    }
                    value = dividedValue == null ? null : String.valueOf(dividedValue);
                } else if (isMultiplyValue(cell)) {
                    String[] splittedField = cell.split("\\*");
                    BigDecimal multipliedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getCSVBigDecimalFieldValue(values, new ImportColumnDetail(arg.trim(), arg.trim(), importColumnDetail.replaceOnlyNull), row);
                        multipliedValue = multipliedValue == null ? argument : (argument == null ? null : safeMultiply(multipliedValue, argument));
                    }
                    value = multipliedValue == null ? null : String.valueOf(multipliedValue);
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
                if (value != null && !value.isEmpty())
                    result += value;
            }
            return result.isEmpty() ? defaultValue : result;
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail.field, importColumnDetail.getFullIndex(), row, e);
        }
    }

    protected String getCSVFieldValue(String[] values, Integer index, Integer from, Integer to, String defaultValue) {
        if (index == null) return defaultValue;
        return values.length <= index ? defaultValue : getSubstring(values[index], from, to);
    }

    protected BigDecimal getCSVBigDecimalFieldValue(String[] values, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        if (importColumnDetail == null) return null;
        return getCSVBigDecimalFieldValue(values, importColumnDetail, importColumnDetail.indexes[0], row, null);
    }

    protected BigDecimal getCSVBigDecimalFieldValue(String[] values, ImportColumnDetail importColumnDetail, String index, int row, BigDecimal defaultValue) throws UniversalImportException {
        try {
            String value = getCSVFieldValue(values, parseIndex(index), null, null, null);
            return value == null ? defaultValue : parseBigDecimal(value);
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail.field, index, row, e);
        }
    }

    protected Date getCSVDateFieldValue(String[] values, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        if (importColumnDetail == null) return null;
        return getCSVDateFieldValue(values, importColumnDetail, importColumnDetail.indexes[0], row, null);
    }

    protected Date getCSVDateFieldValue(String[] values, ImportColumnDetail importColumnDetail, String index, int row, Date defaultValue) throws UniversalImportException {
        String value = getCSVFieldValue(values, parseIndex(index), null, null, null);
        try {
            return value == null ? defaultValue : parseDate(value);
        } catch (ParseException e) {
            throw new UniversalImportException(importColumnDetail.field, index, row, e);
        }
    }

    protected String getXLSFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        return getXLSFieldValue(sheet, row, importColumnDetail, null);
    }

    protected String getXLSFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail, String defaultValue) throws UniversalImportException {
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
                        BigDecimal argument = getXLSBigDecimalFieldValue(sheet, row, new ImportColumnDetail(arg.trim(), arg.trim(), importColumnDetail.replaceOnlyNull));
                        dividedValue = dividedValue == null ? argument : (argument == null ? null : safeDivide(dividedValue, argument));
                    }
                    value = dividedValue == null ? null : String.valueOf(dividedValue);
                } else if (isMultiplyValue(cell)) {
                    String[] splittedField = cell.split("\\*");
                    BigDecimal multipliedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getXLSBigDecimalFieldValue(sheet, row, new ImportColumnDetail(arg.trim(), arg.trim(), importColumnDetail.replaceOnlyNull));
                        multipliedValue = multipliedValue == null ? argument : (argument == null ? null : safeMultiply(multipliedValue, argument));
                    }
                    value = multipliedValue == null ? null : String.valueOf(multipliedValue);
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
                    Date date = parseDate(getXLSFieldValue(sheet, row, new ImportColumnDetail(splittedCell[0], splittedCell[0], importColumnDetail.replaceOnlyNull)));
                    if (date != null) {
                        calendar.setTime(date);
                        return parseDatePattern(splittedCell, calendar);
                    } else return null;
                } else {
                    value = getXLSFieldValue(sheet, importColumnDetail, row, parseIndex(cell), null, null, "");
                }
                if (value != null && !value.isEmpty())
                    result += value;
            }
            return result.isEmpty() ? defaultValue : result;
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail.field, importColumnDetail.getFullIndex(), row, e);
        }
    }

    protected String getXLSFieldValue(Sheet sheet, ImportColumnDetail importColumnDetail, Integer row, Integer column, Integer from, Integer to, String defaultValue) throws UniversalImportException {
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
            throw new UniversalImportException(importColumnDetail.field, importColumnDetail.getFullIndex(), row, e);
        }
    }

    protected BigDecimal getXLSBigDecimalFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        if (importColumnDetail == null) return null;
        return parseBigDecimal(getXLSFieldValue(sheet, row, importColumnDetail));
    }

    protected Date getXLSDateFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        if (importColumnDetail == null) return null;
        try {
            return parseDate(getXLSFieldValue(sheet, row, importColumnDetail));
        } catch (ParseException e) {
            throw new UniversalImportException(importColumnDetail.field, importColumnDetail.getFullIndex(), row, e);
        }
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        return getXLSXFieldValue(sheet, row, importColumnDetail, null);
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail, String defaultValue) throws UniversalImportException {
        return getXLSXFieldValue(sheet, row, importColumnDetail, false, defaultValue);
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, boolean isDate, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        return getXLSXFieldValue(sheet, row, importColumnDetail, isDate, null);
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail, boolean isDate, String defaultValue) throws UniversalImportException {
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
                        BigDecimal argument = getXLSXBigDecimalFieldValue(sheet, row, new ImportColumnDetail(arg.trim(), arg.trim(), importColumnDetail.replaceOnlyNull));
                        dividedValue = dividedValue == null ? argument : (argument == null ? null : safeDivide(dividedValue, argument));
                    }
                    value = dividedValue == null ? null : String.valueOf(dividedValue);
                } else if (isMultiplyValue(cell)) {
                    String[] splittedField = cell.split("\\*");
                    BigDecimal multipliedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getXLSXBigDecimalFieldValue(sheet, row, new ImportColumnDetail(arg.trim(), arg.trim(), importColumnDetail.replaceOnlyNull));
                        multipliedValue = multipliedValue == null ? argument : (argument == null ? null : safeMultiply(multipliedValue, argument));
                    }
                    value = multipliedValue == null ? null : String.valueOf(multipliedValue);
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
                    Date date = parseDate(getXLSXFieldValue(sheet, row, isDate, new ImportColumnDetail(splittedCell[0], splittedCell[0], importColumnDetail.replaceOnlyNull)));
                    if (date != null) {
                        calendar.setTime(date);
                        return parseDatePattern(splittedCell, calendar);
                    } else return null;
                } else {
                    value = getXLSXFieldValue(sheet, importColumnDetail, row, parseIndex(cell), null, null, isDate, "");
                }
                if (value != null && !value.isEmpty())
                    result += value;
            }
            return result.isEmpty() ? defaultValue : result;
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail.field, importColumnDetail.getFullIndex(), row, e);
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
                        result = String.valueOf(new Date(xssfCell.getDateCellValue().getTime()));
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
            throw new UniversalImportException(importColumnDetail.field, importColumnDetail.getFullIndex(), row, e);
        }
    }

    protected BigDecimal getXLSXBigDecimalFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        if (importColumnDetail == null) return null;
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
            throw new UniversalImportException(importColumnDetail.field, importColumnDetail.getFullIndex(), row, e);
        }
    }

    protected Date getXLSXDateFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        if (importColumnDetail == null) return null;
        try {
            return parseDate(getXLSXFieldValue(sheet, row, true, importColumnDetail));
        } catch (ParseException e) {
            throw new UniversalImportException(importColumnDetail.field, importColumnDetail.getFullIndex(), row, e);
        }
    }

    protected String getDBFFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        return getDBFFieldValue(importFile, importColumnDetail, row, "cp866");
    }

    protected String getDBFFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset) throws UniversalImportException {
        return getDBFFieldValue(importFile, importColumnDetail, row, charset, null);
    }

    protected String getDBFFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset, String defaultValue) throws UniversalImportException {
        if (importColumnDetail == null) return defaultValue;
        return getDBFFieldValue(importFile, importColumnDetail, importColumnDetail.indexes, row, charset, defaultValue);
    }

    protected String getDBFFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, String[] columns, int row, String charset, String defaultValue) throws UniversalImportException {
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
                    value = getRoundedValue(getDBFFieldValue(importFile, importColumnDetail, new String[]{splittedField[0]}, row, charset, ""), splittedField[1]);
                } else if (isDivisionValue(column)) {
                    String[] splittedField = column.split("/");
                    BigDecimal dividedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getDBFBigDecimalFieldValue(importFile, new ImportColumnDetail(arg.trim(), arg.trim(), importColumnDetail.replaceOnlyNull), row);
                        dividedValue = dividedValue == null ? argument : (argument == null ? null : safeDivide(dividedValue, argument));
                    }
                    value = dividedValue == null ? null : String.valueOf(dividedValue);
                } else if (isMultiplyValue(column)) {
                    String[] splittedField = column.split("\\*");
                    BigDecimal multipliedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getDBFBigDecimalFieldValue(importFile, new ImportColumnDetail(arg.trim(), arg.trim(), importColumnDetail.replaceOnlyNull), row);
                        multipliedValue = multipliedValue == null ? argument : (argument == null ? null : safeMultiply(multipliedValue, argument));
                    }
                    value = multipliedValue == null ? null : String.valueOf(multipliedValue);
                } else if (isOrValue(column)) {
                    value = "";
                    String[] splittedField = column.split("\\|");
                    for (int i = splittedField.length - 1; i >= 0; i--) {
                        String orValue = getDBFFieldValue(importFile, importColumnDetail, new String[]{splittedField[i]}, i, charset, null);
                        if (orValue != null) {
                            value = orValue;
                            break;
                        }
                    }
                } else if (isSubstringValue(column)) {
                    String[] splittedField = column.split(splitPattern);
                    value = getSubstring(getDBFFieldValue(importFile, importColumnDetail, new String[]{splittedField[0]}, row, charset, defaultValue),
                            splittedField.length > 1 ? parseIndex(splittedField[1]) : null, splittedField.length > 2 ? parseIndex(splittedField[2]) : null);
                } else if (isDatePatternedValue(column)) {
                    String[] splittedField = column.split("~");
                    Calendar calendar = Calendar.getInstance();
                    Date date = parseDate(getDBFFieldValue(importFile, importColumnDetail, new String[]{splittedField[0]}, row, charset, defaultValue));
                    if (date != null) {
                        calendar.setTime(date);
                        return parseDatePattern(splittedField, calendar);
                    } else return null;
                } else {
                    value = new String(importFile.getField(column).getBytes(), charset).trim();
                }
                if (value != null && !value.isEmpty())
                    result += value;
            }
            return result.isEmpty() ? defaultValue : result;
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail.field, importColumnDetail.getFullIndex(), row, e);
        }
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        return getDBFBigDecimalFieldValue(importFile, importColumnDetail, row, "cp866", null);
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, String column, int row) throws UniversalImportException {
        return getDBFBigDecimalFieldValue(importFile, importColumnDetail, column, row, "cp866", null);
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, String column, int row, String charset, String defaultValue) throws UniversalImportException {
        return getDBFBigDecimalFieldValue(importFile, new ImportColumnDetail(importColumnDetail.field, column, false), row, charset, defaultValue);
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset, String defaultValue) throws UniversalImportException {
        try {
            if (importColumnDetail == null) return parseBigDecimal(defaultValue);
            String value = getDBFFieldValue(importFile, importColumnDetail, importColumnDetail.indexes, row, charset, defaultValue);
            if (value == null) return parseBigDecimal(defaultValue);
            return parseBigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new UniversalImportException(importColumnDetail == null ? null : importColumnDetail.field, importColumnDetail == null ? null : importColumnDetail.getFullIndex(), row, e);
        }
    }

    protected Date getDBFDateFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        return getDBFDateFieldValue(importFile, importColumnDetail, row, "cp866");
    }

    protected Date getDBFDateFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset) throws UniversalImportException {
        return getDBFDateFieldValue(importFile, importColumnDetail, row, charset, null);
    }

    protected Date getDBFDateFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset, Date defaultValue) throws UniversalImportException {
        if (importColumnDetail == null) return null;
        String dateString = getDBFFieldValue(importFile, importColumnDetail, importColumnDetail.indexes, row, charset, null);
        try {
            return dateString == null ? defaultValue : parseDate(dateString);
        } catch (ParseException e) {
            throw new UniversalImportException(importColumnDetail.field, importColumnDetail.getFullIndex(), row, e);
        }
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
                && !isMultiplyValue(input) && !isOrValue(input) && !isSubstringValue(input) && !isDatePatternedValue(input);
    }

    private boolean isRoundedValue(String input) {
        return input.matches(roundedPattern);
    }

    private boolean isDivisionValue(String input) {
        return input != null && input.contains("/") && !input.startsWith("=");
    }

    private boolean isMultiplyValue(String input) {
        return input != null && input.contains("*");
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
        try {
            return value == null ? null : new BigDecimal(value.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    //чит для того, чтобы обрабатывать "без НДС" как 0
    protected BigDecimal parseVAT(String value) {
        if (value == null) return null;
        if ((value.toLowerCase().replace(" ", "").equals("безндс")) || (value.toLowerCase().replace(" ", "").equals("бе")))
            return BigDecimal.ZERO;
        else return parseBigDecimal(value.replace("%", ""));
    }
}