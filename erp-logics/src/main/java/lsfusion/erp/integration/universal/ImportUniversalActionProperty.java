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
import java.sql.Time;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

public abstract class ImportUniversalActionProperty extends DefaultImportActionProperty {

    // syntax : 
    // ":xxx_yyy" - value from cell (xxx - column, yyy - row) (for xls, xlsx, csv)
    // "=xxx" - constant value  
    // "=CDT" - currentDateTime
    // "xxx^(i,j) - substring(i,j), i & j inclusive 
    // "xxx^(i,'a') - substring from i to index of first 'a' symbol, i inclusive, symbol 'a' - exclusive. 'a' also may be in 'from' index 
    // "xxx+yyy" - concatenate (for strings) or sum (for numbers)
    // "xxx-yyy" - subtract (for numbers)
    // "xxx/yyy" - divide (for numbers)
    // "xxx*yyy" - multiply (for numbers)
    // "xxx | yyy" - yyy == null ? xxx : yyy
    // "xxx~d=1~M=12~y=2006~h=12~m=30~s=15" - value ~ d= default value for day ~ M= for month ~ y= for year, h= for hour, m= for minute, s= for second 
    // "xxx[-2]" - round up xxx with scale = -2 . [scale] is always at the end of numeric expression, it rounds the result of the whole expression

    public ImportUniversalActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, valueClass);
    }

    public ImportUniversalActionProperty(ScriptingLogicsModule LM, ValueClass... classes) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, classes);
    }

    public ImportUniversalActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    String splitPattern = "\\^\\(|\\)|,";
    String substringPattern = ".*\\^\\((('.*')|(\\d+)),((\'.*\')|(\\d+))?\\)";
    String patternedDateTimePattern = "(.*)(~(.*))+";
    String roundedPattern = "(.*)\\[(\\-?)\\d+\\]";
    String columnRowPattern = ":(\\d+)_(\\d+)";
    DecimalFormat decimalFormat = new DecimalFormat("#.#####");
    protected String currentTimestamp;

    protected String getCSVFieldValue(List<String[]> valuesList, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        return getCSVFieldValue(valuesList, importColumnDetail, row, false, false);
    }

    protected String getCSVFieldValue(List<String[]> valuesList, ImportColumnDetail importColumnDetail, int row, String defaultValue) throws UniversalImportException {
        return getCSVFieldValue(valuesList, importColumnDetail, row, defaultValue, false, false);
    }

    protected String getCSVFieldValue(List<String[]> valuesList, ImportColumnDetail importColumnDetail, int row, boolean isNumeric, boolean ignoreException) throws UniversalImportException {
        return getCSVFieldValue(valuesList, importColumnDetail, row, null, isNumeric, ignoreException);
    }
    
    protected String getCSVFieldValue(List<String[]> valuesList, ImportColumnDetail importColumnDetail, int row, String defaultValue, 
                                      boolean isNumeric, boolean ignoreException) throws UniversalImportException {
        try {

            if (importColumnDetail == null) return defaultValue;
            String result = "";
            for (String cell : importColumnDetail.indexes) {
                if (cell == null) return defaultValue;
                String value;
                if (isConstantValue(cell))
                    value = parseConstantFieldPattern(cell);
                else if(isColumnRowValue(cell)) {
                    String[] splittedCell = cell.replace(":", "").split("_");
                    value = getCSVFieldValue(valuesList.get(parseIndex(splittedCell, 1)), parseIndex(splittedCell, 0), defaultValue);
                } else if (isRoundedValue(cell)) {
                    String[] splittedCell = splitRoundedCell(cell);
                    value = getRoundedValue(getCSVFieldValue(valuesList, importColumnDetail.clone(trim(splittedCell[0])), row), splittedCell[1]);
                } else if (isDivisionValue(cell)) {
                    String[] splittedField = splitCell(cell, "/");
                    BigDecimal dividedValue = null;
                    for (String arg : splittedField) {
                        dividedValue = checkedDivide(dividedValue, getCSVBigDecimalFieldValue(valuesList, importColumnDetail.clone(arg), row));
                    }
                    value = formatValue(dividedValue);
                } else if (isMultiplyValue(cell)) {
                    String[] splittedField = splitCell(cell, "\\*");
                    BigDecimal multipliedValue = null;
                    for (String arg : splittedField) {
                        multipliedValue = checkedMultiply(multipliedValue, getCSVBigDecimalFieldValue(valuesList, importColumnDetail.clone(arg), row));
                    }
                    value = formatValue(multipliedValue);
                } else if (isSubtractValue(cell)) {
                    String[] splittedField = splitCell(cell, "\\-");
                    BigDecimal subtractedValue = null;
                    for (String arg : splittedField) {
                        subtractedValue = checkedSubtract(subtractedValue, getCSVBigDecimalFieldValue(valuesList, importColumnDetail.clone(arg), row));
                    }
                    value = formatValue(subtractedValue);
                } else if (isOrValue(cell)) {
                    value = "";
                    String[] splittedField = splitCell(cell, "\\|");
                    for (int i = splittedField.length - 1; i >= 0; i--) {
                        String orValue = getCSVFieldValue(valuesList, importColumnDetail.clone(splittedField[i]), row, isNumeric, ignoreException);
                        if (orValue != null) {
                            value = orValue;
                            break;
                        }
                    }
                } else if (isSubstringValue(cell)) {
                    String[] splittedCell = splitCell(cell, splitPattern);
                    value = getSubstring(getCSVFieldValue(valuesList, importColumnDetail.clone(splittedCell[0]), row, isNumeric, ignoreException), 
                            parseSubstring(splittedCell, 1), parseSubstring(splittedCell, 2));
                } else if (isPatternedDateTimeValue(cell)) {
                    String[] splittedCell = cell.split("~");
                    Calendar calendar = Calendar.getInstance();
                    Date date = parseDate(getCSVFieldValue(valuesList, importColumnDetail.clone(trim(splittedCell[0])), row, false, ignoreException));
                    if (date != null)
                        calendar.setTime(date);
                    return parseDateTimePattern(splittedCell, calendar);
                } else {
                    value = cell.isEmpty() ? defaultValue : getCSVFieldValue(valuesList.get(row - 1), parseIndex(cell), "");
                }
                result = trySum(result, value, isNumeric);
            }
            return result.isEmpty() ? defaultValue : result;
        } catch (Exception e) {    
            if(ignoreException)
                return defaultValue;
            else
                throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected String getCSVFieldValue(String[] values, Integer column, String defaultValue) {
        if (column == null) return defaultValue;
        return values.length <= column ? defaultValue : values[column];
    }
    
    protected BigDecimal getCSVBigDecimalFieldValue(List<String[]> valuesList, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        if (importColumnDetail == null) return null;
        try {
            return parseBigDecimal(getCSVFieldValue(valuesList, importColumnDetail, row, true, false));
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected Time getCSVTimeFieldValue(List<String[]> valuesList, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        Date date = getCSVDateFieldValue(valuesList, importColumnDetail, row, false);
        return date == null ? null : new Time(date.getTime());
    }
    
    protected Date getCSVDateFieldValue(List<String[]> valuesList, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
       return getCSVDateFieldValue(valuesList, importColumnDetail, row, false);
    }

    protected Date getCSVDateFieldValue(List<String[]> valuesList, ImportColumnDetail importColumnDetail, int row, boolean ignoreException) throws UniversalImportException {
        return getCSVDateFieldValue(valuesList, importColumnDetail, row, null, ignoreException);
    }
    
    protected Date getCSVDateFieldValue(List<String[]> valuesList, ImportColumnDetail importColumnDetail, int row, Date defaultValue) throws UniversalImportException {
       return getCSVDateFieldValue(valuesList, importColumnDetail, row, defaultValue, false);
    }

    protected Date getCSVDateFieldValue(List<String[]> valuesList, ImportColumnDetail importColumnDetail, int row, Date defaultDate, boolean ignoreException)
            throws UniversalImportException {
        if (importColumnDetail == null) return defaultDate;
        try {
            return parseDate(getCSVFieldValue(valuesList, importColumnDetail, row), defaultDate);
        } catch (ParseException e) {
            if (ignoreException)
                return defaultDate;
            else
                throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected String getXLSFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        return getXLSFieldValue(sheet, row, importColumnDetail, null, false, false);
    }

    protected String getXLSFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail, String defaultValue)
            throws UniversalImportException {
        return getXLSFieldValue(sheet, row, importColumnDetail, defaultValue, false, false);
    }
    
    protected String getXLSFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail, String defaultValue, 
                                      boolean isNumeric, boolean ignoreException) throws UniversalImportException {
        try {
            if (importColumnDetail == null) return defaultValue;
            String result = "";
            for (String cell : importColumnDetail.indexes) {
                if (cell == null) return defaultValue;
                String value;
                if (isConstantValue(cell))
                    value = parseConstantFieldPattern(cell);
                else if(isColumnRowValue(cell)) {
                    String[] splittedCell = cell.replace(":", "").split("_");
                    value = getXLSFieldValue(sheet, importColumnDetail, parseIndex(splittedCell, 1), parseIndex(splittedCell, 0), defaultValue);
                } else if (isRoundedValue(cell)) {
                    String[] splittedCell = splitRoundedCell(cell);
                    value = getRoundedValue(getXLSFieldValue(sheet, row, importColumnDetail.clone(trim(splittedCell[0])), defaultValue, isNumeric, ignoreException), splittedCell[1]);
                } else if (isDivisionValue(cell)) {
                    String[] splittedField = splitCell(cell, "/");
                    BigDecimal dividedValue = null;
                    for (String arg : splittedField) {
                        dividedValue = checkedDivide(dividedValue, getXLSBigDecimalFieldValue(sheet, row, importColumnDetail.clone(arg)));
                    }
                    value = formatValue(dividedValue);
                } else if (isMultiplyValue(cell)) {
                    String[] splittedField = splitCell(cell, "\\*");
                    BigDecimal multipliedValue = null;
                    for (String arg : splittedField) {
                        multipliedValue = checkedMultiply(multipliedValue, getXLSBigDecimalFieldValue(sheet, row, importColumnDetail.clone(arg)));
                    }
                    value = formatValue(multipliedValue);
                } else if (isSubtractValue(cell)) {
                    String[] splittedField = splitCell(cell, "\\-");
                    BigDecimal subtractedValue = null;
                    for (String arg : splittedField) {
                        subtractedValue = checkedSubtract(subtractedValue, getXLSBigDecimalFieldValue(sheet, row, importColumnDetail.clone(arg)));
                    }
                    value = formatValue(subtractedValue);
                } else if (isOrValue(cell)) {
                    value = "";
                    String[] splittedField = splitCell(cell, "\\|");
                    for (int i = splittedField.length - 1; i >= 0; i--) {
                        String orValue = getXLSFieldValue(sheet, row, importColumnDetail.clone(splittedField[i]), null, isNumeric, ignoreException);
                        if (orValue != null) {
                            value = orValue;
                            break;
                        }
                    }
                } else if (isSubstringValue(cell)) {
                    String[] splittedCell = splitCell(cell, splitPattern);          
                    value = getSubstring(getXLSFieldValue(sheet, row, importColumnDetail.clone(splittedCell[0]), defaultValue, isNumeric, ignoreException), 
                            parseSubstring(splittedCell, 1), parseSubstring(splittedCell, 2));
                } else if (isPatternedDateTimeValue(cell)) {
                    String[] splittedCell = cell.split("~");
                    Calendar calendar = Calendar.getInstance();
                    Date date = parseDate(getXLSFieldValue(sheet, row, importColumnDetail.clone(trim(splittedCell[0])), null, false, ignoreException));
                    if (date != null)
                        calendar.setTime(date);
                    return parseDateTimePattern(splittedCell, calendar);
                } else {
                    value = cell.isEmpty() ? defaultValue : getXLSFieldValue(sheet, importColumnDetail, row, parseIndex(cell), "");
                }
                result = trySum(result, value, isNumeric);
            } 
            return result.isEmpty() ? defaultValue : result;
        } catch (Exception e) {
            if(ignoreException)
                return defaultValue;
            else
                throw new UniversalImportException(importColumnDetail, row, e);
        }
    }    
    
    protected String getXLSFieldValue(Sheet sheet, ImportColumnDetail importColumnDetail, Integer row, Integer column, String defaultValue) throws UniversalImportException {
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
                result = (cell.getContents().isEmpty()) ? defaultValue : trim(cell.getContents());
            }
            return result;
        } catch (ArrayIndexOutOfBoundsException e) {
            return defaultValue;  
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected BigDecimal getXLSBigDecimalFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        return parseBigDecimal(getXLSFieldValue(sheet, row, importColumnDetail, null, true, false));
    }

    protected Time getXLSTimeFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        Date date = getXLSDateFieldValue(sheet, row, importColumnDetail, null);
        return date == null ? null : new Time(date.getTime());
    }
    
    protected Date getXLSDateFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        return getXLSDateFieldValue(sheet, row, importColumnDetail, null);
    }

    protected Date getXLSDateFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail, boolean ignoreException) throws UniversalImportException {
        return getXLSDateFieldValue(sheet, row, importColumnDetail, null, ignoreException);
    }
    
    protected Date getXLSDateFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail, Date defaultDate) throws UniversalImportException {
        return getXLSDateFieldValue(sheet, row, importColumnDetail, defaultDate, false);
    }

    protected Date getXLSDateFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail, Date defaultDate, boolean ignoreException) throws UniversalImportException {
        try {
            return parseDate(getXLSFieldValue(sheet, row, importColumnDetail, null, false, ignoreException), defaultDate);
        } catch (ParseException e) {
            if(ignoreException)
                return defaultDate;
            else
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
        return getXLSXFieldValue(sheet, row, importColumnDetail, isDate, defaultValue, false, false);
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail, boolean isDate, 
                                       String defaultValue, boolean isNumeric, boolean ignoreException) throws UniversalImportException {
        try {
            if (importColumnDetail == null) return defaultValue;
            String result = "";
            for (String cell : importColumnDetail.indexes) {
                if (cell == null) return defaultValue;
                String value;
                if (isConstantValue(cell))
                    value = parseConstantFieldPattern(cell);
                else if(isColumnRowValue(cell)) {
                    String[] splittedCell = cell.replace(":", "").split("_");
                    value = getXLSXFieldValue(sheet, importColumnDetail, parseIndex(splittedCell, 1), parseIndex(splittedCell, 0), isDate, defaultValue);
                }
                else if (isRoundedValue(cell)) {
                    String[] splittedCell = splitRoundedCell(cell);
                    value = getRoundedValue(getXLSXFieldValue(sheet, row, importColumnDetail.clone(trim(splittedCell[0])), isDate, defaultValue, isNumeric, ignoreException), splittedCell[1]);
                } else if (isDivisionValue(cell)) {
                    String[] splittedField = splitCell(cell, "/");
                    BigDecimal dividedValue = null;
                    for (String arg : splittedField) {
                        dividedValue = checkedDivide(dividedValue, getXLSXBigDecimalFieldValue(sheet, row, importColumnDetail.clone(arg)));
                    }
                    value = formatValue(dividedValue);
                } else if (isMultiplyValue(cell)) {
                    String[] splittedField = splitCell(cell, "\\*");
                    BigDecimal multipliedValue = null;
                    for (String arg : splittedField) {
                        multipliedValue = checkedMultiply(multipliedValue, getXLSXBigDecimalFieldValue(sheet, row, importColumnDetail.clone(arg)));
                    }
                    value = formatValue(multipliedValue);
                } else if (isSubtractValue(cell)) {
                    String[] splittedField = splitCell(cell, "\\-");
                    BigDecimal subtractedValue = null;
                    for (String arg : splittedField) {
                        subtractedValue = checkedSubtract(subtractedValue, getXLSXBigDecimalFieldValue(sheet, row, importColumnDetail.clone(arg)));
                    }
                    value = formatValue(subtractedValue);
                } else if (isOrValue(cell)) {
                    value = "";
                    String[] splittedField = splitCell(cell, "\\|");
                    for (int i = splittedField.length - 1; i >= 0; i--) {
                        String orValue = getXLSXFieldValue(sheet, row, importColumnDetail.clone(splittedField[i]), isDate, null, isNumeric, ignoreException);
                        if (orValue != null) {
                            value = orValue;
                            break;
                        }
                    }
                } else if (isSubstringValue(cell)) {
                    String[] splittedCell = splitCell(cell, splitPattern);
                    value = getSubstring(getXLSXFieldValue(sheet, row, importColumnDetail.clone(splittedCell[0]),
                            isDate, defaultValue, isNumeric, ignoreException), parseSubstring(splittedCell, 1), parseSubstring(splittedCell, 2));
                }  else if (isPatternedDateTimeValue(cell)) {
                    String[] splittedCell = cell.split("~");
                    Calendar calendar = Calendar.getInstance();
                    Date date = parseDate(getXLSXFieldValue(sheet, row, importColumnDetail.clone(trim(splittedCell[0])), isDate, null, false, ignoreException));
                    if (date != null)
                        calendar.setTime(date);
                    return parseDateTimePattern(splittedCell, calendar);
                } else {
                    value = cell.isEmpty() ? defaultValue : getXLSXFieldValue(sheet, importColumnDetail, row, parseIndex(cell), isDate, "");
                }
                result = trySum(result, value, isNumeric);
            }
            return result.isEmpty() ? defaultValue : result;
        } catch (Exception e) {
            if(ignoreException)
                return defaultValue;
            else
                throw new UniversalImportException(importColumnDetail, row, e);
        }
    }   
    
    protected String getXLSXFieldValue(XSSFSheet sheet, ImportColumnDetail importColumnDetail, Integer row, Integer cell, boolean isDate, String defaultValue) throws UniversalImportException {
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
                    result = (xssfCell.getStringCellValue().isEmpty()) ? defaultValue : trim(xssfCell.getStringCellValue());
                    break;
            }
            return result;
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected BigDecimal getXLSXBigDecimalFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        return parseBigDecimal(getXLSXFieldValue(sheet, row, importColumnDetail));
    }

    protected Time getXLSXTimeFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        Date date = getXLSXDateFieldValue(sheet, row, importColumnDetail, null, false);
        return date == null ? null : new Time(date.getTime());
    }
    
    protected Date getXLSXDateFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        return getXLSXDateFieldValue(sheet, row, importColumnDetail, null, false);
    }

    protected Date getXLSXDateFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail, boolean ignoreException) 
            throws UniversalImportException {
        return getXLSXDateFieldValue(sheet, row, importColumnDetail, null, ignoreException);
    }
    
    protected Date getXLSXDateFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail, Date defaultDate) throws UniversalImportException {
        return getXLSXDateFieldValue(sheet, row, importColumnDetail, defaultDate, false);
    }

    protected Date getXLSXDateFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail, Date defaultDate, boolean ignoreException) 
            throws UniversalImportException {
        try {
            return parseDate(getXLSXFieldValue(sheet, row, importColumnDetail, true, null, false, ignoreException), defaultDate);
        } catch (ParseException e) {
            if(ignoreException)
                return defaultDate;
            else
                throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected String getDBFFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset) throws UniversalImportException {
        return getDBFFieldValue(importFile, importColumnDetail, row, charset, null);
    }

    protected String getDBFFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset, String defaultValue) throws UniversalImportException {
        if (importColumnDetail == null) return defaultValue;
        return getDBFFieldValue(importFile, importColumnDetail, row, charset, defaultValue, false, false);
    }

    protected String getDBFFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset,
                                      String defaultValue, boolean isNumeric, boolean ignoreException) throws UniversalImportException {
        try {
            if (importColumnDetail == null) return defaultValue;
            String result = "";
            for (String column : importColumnDetail.indexes) {
                if (column == null) return defaultValue;
                String value;
                if (isConstantValue(column))
                    value = parseConstantFieldPattern(column);
                else if (isRoundedValue(column)) {
                    String[] splittedField = splitRoundedCell(column);
                    value = getRoundedValue(getDBFFieldValue(importFile, importColumnDetail.clone(trim(splittedField[0])), 
                            row, charset, defaultValue, isNumeric, ignoreException), splittedField[1]);
                } else if (isDivisionValue(column)) {
                    String[] splittedField = splitCell(column, "/");
                    BigDecimal dividedValue = null;
                    for (String arg : splittedField) {
                        dividedValue = checkedDivide(dividedValue, getDBFBigDecimalFieldValue(importFile, importColumnDetail.clone(arg), row, charset));
                    }
                    value = formatValue(dividedValue);
                } else if (isMultiplyValue(column)) {
                    String[] splittedField = splitCell(column, "\\*");
                    BigDecimal multipliedValue = null;
                    for (String arg : splittedField) {
                        multipliedValue = checkedMultiply(multipliedValue, getDBFBigDecimalFieldValue(importFile, importColumnDetail.clone(arg), row, charset));
                    }
                    value = formatValue(multipliedValue);
                } else if (isSubtractValue(column)) {
                    String[] splittedField = splitCell(column, "\\-");
                    BigDecimal subtractedValue = null;
                    for (String arg : splittedField) {
                        subtractedValue = checkedSubtract(subtractedValue, getDBFBigDecimalFieldValue(importFile, importColumnDetail.clone(arg), row, charset));
                    }
                    value = formatValue(subtractedValue);
                } else if (isOrValue(column)) {
                    value = "";
                    String[] splittedField = splitCell(column, "\\|");
                    for (int i = splittedField.length - 1; i >= 0; i--) {
                        String orValue = getDBFFieldValue(importFile, importColumnDetail.clone(splittedField[i]), 
                                row, charset, null, isNumeric, ignoreException);
                        if (orValue != null) {
                            value = orValue;
                            break;
                        }
                    }
                } else if (isSubstringValue(column)) {
                    String[] splittedField = splitCell(column, splitPattern);
                    value = getSubstring(getDBFFieldValue(importFile, importColumnDetail.clone(splittedField[0]),
                            row, charset, null, isNumeric, ignoreException), parseSubstring(splittedField, 1), parseSubstring(splittedField, 2));
                } else if (isPatternedDateTimeValue(column)) {
                    String[] splittedField = column.split("~");
                    Calendar calendar = Calendar.getInstance();
                    Date date = parseDate(getDBFFieldValue(importFile, importColumnDetail.clone(trim(splittedField[0])), 
                            row, charset, defaultValue, false, ignoreException));
                    if (date != null)
                        calendar.setTime(date);
                    return parseDateTimePattern(splittedField, calendar);
                } else {
                    value = column.isEmpty() ? defaultValue : trim(new String(importFile.getField(column).getBytes(), charset));
                }
                result = trySum(result, value, isNumeric);
            }
            return result.isEmpty() ? defaultValue : result;
        } catch (Exception e) {
            if(ignoreException)
                return defaultValue;
            else
                throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset) throws UniversalImportException {
        return getDBFBigDecimalFieldValue(importFile, importColumnDetail, row, charset, null);
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset, BigDecimal defaultValue) throws UniversalImportException {
        try {
            if (importColumnDetail == null) return defaultValue;
            return parseBigDecimal(getDBFFieldValue(importFile, importColumnDetail, row, charset, null, true, false), defaultValue);
        } catch (NumberFormatException e) {
            throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected Time getDBFTimeFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset) throws UniversalImportException {
        Date date = getDBFDateFieldValue(importFile, importColumnDetail, row, charset, false);
        return date == null ? null : new Time(date.getTime());
    }
    
    protected Date getDBFDateFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset) throws UniversalImportException {
        return getDBFDateFieldValue(importFile, importColumnDetail, row, charset, false);
    }

    protected Date getDBFDateFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset, boolean ignoreException) 
            throws UniversalImportException {
        return getDBFDateFieldValue(importFile, importColumnDetail, row, charset, null, ignoreException);
    }

    protected Date getDBFDateFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset, Date defaultDate, 
                                        boolean ignoreException) throws UniversalImportException {
        try {
            if (importColumnDetail == null) return defaultDate;
            return parseDate(getDBFFieldValue(importFile, importColumnDetail, row, charset, null, false, ignoreException), defaultDate);
        } catch (ParseException e) {
            if(ignoreException)
                return defaultDate;
            else
                throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected String getJDBFFieldValue(Object[] entry, Map<String, Integer> fieldNamesMap, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        return getJDBFFieldValue(entry, fieldNamesMap, importColumnDetail, row, null);
    }

    protected String getJDBFFieldValue(Object[] entry, Map<String, Integer> fieldNamesMap, ImportColumnDetail importColumnDetail, int row, String defaultValue) throws UniversalImportException {
        if (importColumnDetail == null) return defaultValue;
        return getJDBFFieldValue(entry, fieldNamesMap, importColumnDetail, row, defaultValue, false, true, false);
    }

    protected String getJDBFFieldValue(Object[] entry, Map<String, Integer> fieldNamesMap, ImportColumnDetail importColumnDetail, 
                                       int row, String defaultValue, boolean isNumeric, boolean zeroIsNull, boolean ignoreException) throws UniversalImportException {
        try {
            if (importColumnDetail == null) return defaultValue;
            String result = "";
            for (String column : importColumnDetail.indexes) {
                if (column == null) return defaultValue;
                String value;
                if (isConstantValue(column))
                    value = parseConstantFieldPattern(column);
                else if (isRoundedValue(column)) {
                    String[] splittedField = splitRoundedCell(column);
                    value = getRoundedValue(getJDBFFieldValue(entry, fieldNamesMap, importColumnDetail.clone(trim(splittedField[0])),
                            row, "", true, zeroIsNull, ignoreException), splittedField[1]);
                } else if (isDivisionValue(column)) {
                    String[] splittedField = splitCell(column, "/");
                    BigDecimal dividedValue = null;
                    for (String arg : splittedField) {
                        dividedValue = checkedDivide(dividedValue, getJDBFBigDecimalFieldValue(entry, fieldNamesMap, importColumnDetail.clone(arg), row, null));
                    }
                    value = formatValue(dividedValue);
                } else if (isMultiplyValue(column)) {
                    String[] splittedField = splitCell(column, "\\*");
                    BigDecimal multipliedValue = null;
                    for (String arg : splittedField) {
                        multipliedValue = checkedMultiply(multipliedValue, getJDBFBigDecimalFieldValue(entry, fieldNamesMap, importColumnDetail.clone(arg), row, null));
                    }
                    value = formatValue(multipliedValue);
                } else if (isSubtractValue(column)) {
                    String[] splittedField = splitCell(column, "\\-");
                    BigDecimal subtractedValue = null;
                    for (String arg : splittedField) {
                        subtractedValue = checkedSubtract(subtractedValue, getJDBFBigDecimalFieldValue(entry, fieldNamesMap, importColumnDetail.clone(arg), row, null));
                    }
                    value = formatValue(subtractedValue);
                } else if (isOrValue(column)) {
                    value = "";
                    String[] splittedField = splitCell(column, "\\|");
                    for (int i = splittedField.length - 1; i >= 0; i--) {
                        String orValue = getJDBFFieldValue(entry, fieldNamesMap, importColumnDetail.clone(splittedField[i]), 
                                i, null, isNumeric, zeroIsNull, ignoreException);
                        if (orValue != null) {
                            value = orValue;
                            break;
                        }
                    }
                } else if (isSubstringValue(column)) {
                    String[] splittedField = splitCell(column, splitPattern);
                    value = getSubstring(getJDBFFieldValue(entry, fieldNamesMap, importColumnDetail.clone(splittedField[0]),
                            row, defaultValue, isNumeric, zeroIsNull, ignoreException), parseSubstring(splittedField, 1), parseSubstring(splittedField, 2));
                } else if (isPatternedDateTimeValue(column)) {
                    String[] splittedField = column.split("~");
                    Calendar calendar = Calendar.getInstance();
                    Date date = parseDate(getJDBFFieldValue(entry, fieldNamesMap, importColumnDetail.clone(trim(splittedField[0])), 
                            row, defaultValue, false, zeroIsNull, ignoreException));
                    if (date != null)
                        calendar.setTime(date);
                    return parseDateTimePattern(splittedField, calendar);
                } else {
                    value = column.isEmpty() ? defaultValue : formatValue(entry[fieldNamesMap.get(column.toUpperCase())]);
                    if(value != null && value.equals("0") && !isNumeric && zeroIsNull) {
                        value = null;
                    }
                }
                result = trySum(result, value, isNumeric);
            }
            return result.isEmpty() ? defaultValue : result;
        } catch (Exception e) {
            if(ignoreException)
                return defaultValue;
            else
                throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected BigDecimal getJDBFBigDecimalFieldValue(Object[] entry, Map<String, Integer> fieldNamesMap, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        return getJDBFBigDecimalFieldValue(entry, fieldNamesMap, importColumnDetail, row, null);
    }

    protected BigDecimal getJDBFBigDecimalFieldValue(Object[] entry, Map<String, Integer> fieldNamesMap, ImportColumnDetail importColumnDetail, int row, BigDecimal defaultValue) throws UniversalImportException {
        try {
            if (importColumnDetail == null) return defaultValue;
            return parseBigDecimal(getJDBFFieldValue(entry, fieldNamesMap, importColumnDetail, row, null, true, false, false), defaultValue);
        } catch (NumberFormatException e) {
            throw new UniversalImportException(importColumnDetail, row, e);
        }
    }

    protected Time getJDBFTimeFieldValue(Object[] entry, Map<String, Integer> fieldNamesMap, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        Date date = getJDBFDateFieldValue(entry, fieldNamesMap, importColumnDetail, row, null);
        return date == null ? null : new Time(date.getTime());
    }
    
    protected Date getJDBFDateFieldValue(Object[] entry, Map<String, Integer> fieldNamesMap, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        return getJDBFDateFieldValue(entry, fieldNamesMap, importColumnDetail, row, null);
    }

    protected Date getJDBFDateFieldValue(Object[] entry, Map<String, Integer> fieldNamesMap, ImportColumnDetail importColumnDetail, int row, Date defaultDate) throws UniversalImportException {
        try {
            if (importColumnDetail == null) return defaultDate;
            return parseDate(getJDBFFieldValue(entry, fieldNamesMap, importColumnDetail, row, null, false, false, false), defaultDate);
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

    private Integer parseIndex(String[] splittedValue, int index) {
        return splittedValue.length <= index ? null : parseIndex(splittedValue[index]);
    }
    
    private Integer parseIndex(String index) {
        if(index == null) return null; 
        try {
            return index.matches("\\d+") ? (Integer.parseInt(index) - 1) : getCellIndex(index); 
        } catch (Exception e) {
            return null;
        }
    }

    private Integer getCellIndex(String column) {
        Integer result = 0;
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for(int i = 0; i < column.length(); i++) {
            result += letters.indexOf(column.charAt(i)) + i * 26;
        }
        return result;
    }

    private String getSubstring(String value, Object fromSymbol, Object toSymbol) {
        if (value == null) return null;
        if (fromSymbol == null || fromSymbol instanceof Integer) {
            Integer from = (Integer) fromSymbol;
            if (toSymbol == null || toSymbol instanceof Integer) {
                return getSubstring(value, from, (Integer) toSymbol);
            } else {
                String fromSubstring = (from == null || from < 0 || from > value.length()) ? value : trim(value.substring(Math.min(value.length(), from)));
                return (fromSubstring != null && fromSubstring.contains((String) toSymbol)) ? fromSubstring.substring(0, fromSubstring.indexOf((String) toSymbol)) : fromSubstring;
            }
        } else {
            String from = (String) fromSymbol;
            String fromSubstring = trim(value.substring(Math.min(value.length(), value.indexOf(from) + from.length())));
            return (fromSubstring != null && fromSubstring.contains((String) toSymbol)) ? fromSubstring.substring(0, fromSubstring.indexOf((String) toSymbol)) : fromSubstring;
        }
    }
    
    private String getSubstring(String value, Integer from, Integer to) {
        return (value == null || from == null || from < 0 || from > value.length()) ? value :
                ((to == null || to > value.length())) ? trim(value.substring(Math.min(value.length(), from))) : trim(value.substring(from, Math.min(value.length(), to + 1)));
    }

    private String getRoundedValue(String value, String scale) throws UniversalImportException {
        return value == null ? null : decimalFormat.format(new BigDecimal(value).setScale(Integer.parseInt(scale), RoundingMode.HALF_UP));
    }

    private Object parseSubstring(String[] splittedValue, int index) {
        if(splittedValue.length <= index) return null;
        String value = splittedValue[index];
        if(value != null && value.startsWith("'") && value.endsWith("'") && value.length() >= 3) {
            return value.substring(1, value.length() - 1);    
        } else return parseIndex(value);
    }
    
    private String parseDateTimePattern(String[] splittedField, Calendar calendar) {
        for (int i = 1; i < splittedField.length; i++) {
            String pattern = splittedField[i];
            if (pattern.startsWith("y="))
                calendar.set(Calendar.YEAR, parseDateFieldPattern("y=", pattern, calendar.get(Calendar.YEAR)));
            else if (pattern.startsWith("M="))
                calendar.set(Calendar.MONTH, parseDateFieldPattern("M=", pattern, 12) - 1);
            else if(pattern.startsWith("h="))
                calendar.set(Calendar.HOUR, parseDateFieldPattern("h=", pattern, 23));
            else if(pattern.startsWith("m="))
                calendar.set(Calendar.MINUTE, parseDateFieldPattern("m=", pattern, 59));
            else if(pattern.startsWith("s="))
                calendar.set(Calendar.SECOND, parseDateFieldPattern("s=", pattern, 59));
        }
        for (int i = 1; i < splittedField.length; i++) {
            String pattern = splittedField[i];
            if (pattern.startsWith("d="))
                calendar.set(Calendar.DAY_OF_MONTH, parseDateFieldPattern("d=", pattern, calendar.getActualMaximum(Calendar.DAY_OF_MONTH)));
        }
        return new SimpleDateFormat("dd.MM.yyyy hh:mm:ss").format(calendar.getTime());
    }

    private int parseDateFieldPattern(String type, String value, int maxValue) {
        try {
            return Math.min(Integer.parseInt(value.replace(type, "")), maxValue);
        } catch (Exception e) {
            return maxValue;
        }
    }

    private String parseConstantFieldPattern(String value) {
        return (value.toLowerCase().contains("cdt") && currentTimestamp != null) ? value.replaceAll("(\\=CDT)|(\\=cdt)", currentTimestamp) : value.substring(1);
    }

    private boolean isConstantValue(String input) {
        return input != null && input.startsWith("=") && !isColumnRowValue(input) && !isRoundedValue(input) 
                && !isDivisionValue(input) && !isMultiplyValue(input) && !(isSubtractValue(input)) && !isOrValue(input)
                && !isSubstringValue(input) && !isPatternedDateTimeValue(input);
    }

    private boolean isColumnRowValue(String input) {
        return input != null && input.matches(columnRowPattern);
    }
    
    private boolean isRoundedValue(String input) {
        return input != null && input.matches(roundedPattern);
    }

    private String[] splitRoundedCell(String cell) {
        return cell.split("\\[|\\]");
    }

    private boolean isDivisionValue(String input) {
        return input != null && input.contains("/") && !input.equals("=/");
    }

    private boolean isMultiplyValue(String input) {
        return input != null && input.contains("*") && !input.equals("=*");
    }

    private boolean isSubtractValue(String input) {
        return input != null && input.contains("-") && !input.equals("=-");
    }    

    private boolean isOrValue(String input) {
        return input != null && input.contains("|") && !input.equals("=|");
    }

    private boolean isSubstringValue(String input) {
        return input != null && input.matches(substringPattern);
    }

    private boolean isPatternedDateTimeValue(String input) {
        return input != null && input.matches(patternedDateTimePattern);
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
            return value == null ? defaultValue : new BigDecimal(trim(value).replace(",", "."));
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
            if (isNumeric) {
                BigDecimal value1Numeric = value1.isEmpty() ? BigDecimal.ZERO : new BigDecimal(value1);
                BigDecimal value2Numeric = value2.isEmpty() ? BigDecimal.ZERO : new BigDecimal(value2);
                return String.valueOf(value1Numeric.add(value2Numeric));
            } else return value1 + value2;
        } catch (Exception e) {
            return value1 + value2;
        }
    }
    
    private String[] splitCell(String cell, String pattern) {
        String[] splittedCell = cell.split(pattern);
        for(int i = 0; i<splittedCell.length; i++)
            splittedCell[i] = trim(splittedCell[i]);
        return splittedCell;
    }
    
    protected String formatValue(Object value) {
        if (value == null) return null;
        if (value instanceof Date || value instanceof java.util.Date) {
            return new SimpleDateFormat("dd.MM.yyyy").format(value);
        } else return String.valueOf(value);
    }
    
    private BigDecimal checkedSubtract(BigDecimal subtractedValue, BigDecimal argument) {
        return subtractedValue == null ? argument : safeSubtract(subtractedValue, argument);
    }
    
    private BigDecimal checkedMultiply(BigDecimal multipliedValue, BigDecimal argument) {
        return multipliedValue == null ? argument : safeMultiply(multipliedValue, argument);
    }
    
    private BigDecimal checkedDivide(BigDecimal dividedValue, BigDecimal argument) {
        return dividedValue == null ? argument : safeDivide(dividedValue, argument);
    }
}