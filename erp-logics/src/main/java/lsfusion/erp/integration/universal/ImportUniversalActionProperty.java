package lsfusion.erp.integration.universal;

import jxl.CellType;
import jxl.NumberCell;
import jxl.NumberFormulaCell;
import jxl.Sheet;
import lsfusion.erp.integration.DefaultImportActionProperty;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.commons.lang.time.DateUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.xBaseJ.DBF;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.DateFormatSymbols;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

public abstract class ImportUniversalActionProperty extends DefaultImportActionProperty {

    // syntax : 
    // "=xxx" - constant value                                         
    // "xxx^(1,6) - substring(1,6)
    // "xxx+yyy" - concatenate
    // "xxx/yyy" - divide (for numbers)
    // "xxx | yyy" - yyy == null ? xxx : yyy
    // "xxx~d=1~m=12~y=2006" - value ~ d= default value for day ~ m= for month ~ y= for year
    // пока нет поддержки одновременно divide и substring
    public ImportUniversalActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, valueClass);
    }

    public ImportUniversalActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
    }

    String splitPattern = "\\^\\(|\\)|,";
    String substringPattern = ".*\\^\\(\\d+,(?:\\d+)?\\)";
    String datePatternPattern = "(.*)(~(.*))+";

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
                    return cell.substring(1);
                if (isDivisionValue(cell)) {
                    String[] splittedField = cell.split("/");
                    BigDecimal dividedValue = BigDecimal.ZERO;
                    for (String arg : splittedField) {
                        BigDecimal argument = getCSVBigDecimalFieldValue(values, importColumnDetail, arg.trim(), row, null);
                        dividedValue = dividedValue == null ? argument : (argument == null ? BigDecimal.ZERO : safeDivide(dividedValue, argument));
                    }
                    value = String.valueOf(dividedValue);
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
                } else if (cell.matches(substringPattern)) {
                    String[] splittedCell = cell.split(splitPattern);
                    value = getCSVFieldValue(values, parseIndex(splittedCell[0]), parseIndex(splittedCell[1]), parseIndex(splittedCell[2]), "");
                } else if (cell.matches(datePatternPattern)) {
                    String[] splittedCell = cell.split("~");
                    Calendar calendar = Calendar.getInstance();
                    Date date = getCSVDateFieldValue(values, importColumnDetail, parseIndex(splittedCell[0]));
                    if (date != null) {
                        calendar.setTime(date);
                        return parseDatePattern(splittedCell, calendar);
                    } else return null;
                } else {
                    value = getCSVFieldValue(values, parseIndex(cell), null, null, "");
                }
                result += ((result.isEmpty() || value.isEmpty()) ? "" : " ") + value;
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

    //Пока разрешено склеивать несколько ячеек только как строки
    protected BigDecimal getCSVBigDecimalFieldValue(String[] values, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        if (importColumnDetail == null) return null;
        return getCSVBigDecimalFieldValue(values, importColumnDetail, importColumnDetail.indexes[0], row, null);
    }

    protected BigDecimal getCSVBigDecimalFieldValue(String[] values, ImportColumnDetail importColumnDetail, String index, int row, BigDecimal defaultValue) throws UniversalImportException {
        try {
            String value = getCSVFieldValue(values, parseIndex(index), null, null, null);
            return value == null ? defaultValue : new BigDecimal(value);
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail.field, index, row, e);
        }
    }

    //Пока разрешено склеивать несколько ячеек только как строки
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
                    return cell.substring(1);
                if (isDivisionValue(cell)) {
                    String[] splittedField = cell.split("/");
                    BigDecimal dividedValue = BigDecimal.ZERO;
                    for (String arg : splittedField) {
                        BigDecimal argument = getXLSBigDecimalFieldValue(sheet, importColumnDetail, row, parseIndex(arg.trim()), null);
                        dividedValue = dividedValue == null ? argument : (argument == null ? BigDecimal.ZERO : safeDivide(dividedValue, argument));
                    }
                    value = String.valueOf(dividedValue);
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
                } else if (cell.matches(substringPattern)) {
                    String[] splittedCell = cell.split(splitPattern);
                    value = getXLSFieldValue(sheet, importColumnDetail, row, parseIndex(splittedCell[0]), parseIndex(splittedCell[1]), parseIndex(splittedCell[2]), "");
                } else if (cell.matches(datePatternPattern)) {
                    String[] splittedCell = cell.split("~");
                    Calendar calendar = Calendar.getInstance();
                    Date date = getXLSDateFieldValue(sheet, parseIndex(splittedCell[0]), importColumnDetail);
                    if (date != null) {
                        calendar.setTime(date);
                        return parseDatePattern(splittedCell, calendar);
                    } else return null;
                } else {
                    value = getXLSFieldValue(sheet, importColumnDetail, row, parseIndex(cell), null, null, "");
                }
                result += ((result.isEmpty() || value.isEmpty()) ? "" : " ") + value;
            }
            return result.isEmpty() ? defaultValue : result;
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail.field, importColumnDetail.getFullIndex(), row, e);
        }
    }

    //Пока подстроку разрешено брать только для строковых полей
    protected String getXLSFieldValue(Sheet sheet, ImportColumnDetail importColumnDetail, Integer row, Integer column, Integer from, Integer to, String defaultValue) throws UniversalImportException {
        try {
            if (column == null) return defaultValue;
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
            return getSubstring(result, from, to);
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail.field, importColumnDetail.getFullIndex(), row, e);
        }
    }

    //Пока разрешено склеивать несколько ячеек только как строки
    protected BigDecimal getXLSBigDecimalFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        if (importColumnDetail == null) return null;
        return getXLSBigDecimalFieldValue(sheet, importColumnDetail, row, parseIndex(importColumnDetail.indexes[0]), null);
    }

    protected BigDecimal getXLSBigDecimalFieldValue(Sheet sheet, ImportColumnDetail importColumnDetail, Integer row, Integer column, BigDecimal defaultValue) throws UniversalImportException {
        try {
            if (column == null) return defaultValue;
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
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail.field, importColumnDetail.getFullIndex(), row, e);
        }
    }

    //Пока разрешено склеивать несколько ячеек только как строки
    protected Date getXLSDateFieldValue(Sheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        if (importColumnDetail == null) return null;
        return getXLSDateFieldValue(sheet, importColumnDetail, row, parseIndex(importColumnDetail.indexes[0]), null);
    }

    protected Date getXLSDateFieldValue(Sheet sheet, ImportColumnDetail importColumnDetail, Integer row, String cell) throws UniversalImportException {
        if (cell == null) return null;
        return getXLSDateFieldValue(sheet, importColumnDetail, row, parseIndex(cell), null);
    }

    protected Date getXLSDateFieldValue(Sheet sheet, ImportColumnDetail importColumnDetail, Integer row, Integer column, Date defaultValue) throws UniversalImportException {
        try {
            if (column == null) return defaultValue;
            jxl.Cell cell = sheet.getCell(column, row);
            if (cell == null) return defaultValue;
            if (cell.getType().equals(CellType.NUMBER)) {
                return new Date((long) ((NumberCell) cell).getValue());
            } else
                return parseDate(cell.getContents());
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail.field, importColumnDetail.getFullIndex(), row, e);
        }

    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        return getXLSXFieldValue(sheet, row, importColumnDetail, null);
    }

    //Пока подстроку разрешено брать только для строковых полей
    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail, String defaultValue) throws UniversalImportException {
        try {
            if (importColumnDetail == null) return defaultValue;
            String result = "";
            for (String cell : importColumnDetail.indexes) {
                if (cell == null) return defaultValue;
                String value;
                if (isConstantValue(cell))
                    return cell.substring(1);
                if (isDivisionValue(cell)) {
                    String[] splittedField = cell.split("/");
                    BigDecimal dividedValue = BigDecimal.ZERO;
                    for (String arg : splittedField) {
                        BigDecimal argument = getXLSXBigDecimalFieldValue(sheet, importColumnDetail, row, parseIndex(arg.trim()), null);
                        dividedValue = dividedValue == null ? argument : (argument == null ? BigDecimal.ZERO : safeDivide(dividedValue, argument));
                    }
                    value = String.valueOf(dividedValue);
                } else if (isOrValue(cell)) {
                    value = "";
                    String[] splittedField = cell.split("\\|");
                    for (int i = splittedField.length - 1; i >= 0; i--) {
                        String orValue = getXLSXFieldValue(sheet, importColumnDetail, row, parseIndex(splittedField[i]), null, null, null);
                        if (orValue != null) {
                            value = orValue;
                            break;
                        }
                    }
                } else if (cell.matches(substringPattern)) {
                    String[] splittedCell = cell.split(splitPattern);
                    value = getXLSXFieldValue(sheet, importColumnDetail, row, parseIndex(splittedCell[0]), parseIndex(splittedCell[1]), parseIndex(splittedCell[2]), "");
                } else if (cell.matches(datePatternPattern)) {
                    String[] splittedCell = cell.split("~");
                    Calendar calendar = Calendar.getInstance();
                    Date date = getXLSXDateFieldValue(sheet, parseIndex(splittedCell[0]), importColumnDetail);
                    if (date != null) {
                        calendar.setTime(date);
                        return parseDatePattern(splittedCell, calendar);
                    } else return null;
                } else {
                    value = getXLSXFieldValue(sheet, importColumnDetail, row, parseIndex(cell), null, null, "");
                }
                result += (result.isEmpty() || value.isEmpty() ? "" : " ") + value;
            }
            return result.isEmpty() ? defaultValue : result;
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail.field, importColumnDetail.getFullIndex(), row, e);
        }
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, ImportColumnDetail importColumnDetail, Integer row, Integer cell, Integer from, Integer to, String defaultValue) throws UniversalImportException {
        try {
            if (cell == null) return defaultValue;
            XSSFRow xssfRow = sheet.getRow(row);
            if (xssfRow == null) return defaultValue;
            XSSFCell xssfCell = xssfRow.getCell(cell);
            if (xssfCell == null) return defaultValue;
            String result;
            switch (xssfCell.getCellType()) {
                case Cell.CELL_TYPE_NUMERIC:
                    result = new DecimalFormat("#.#####").format(xssfCell.getNumericCellValue());
                    result = result.endsWith(".0") ? result.substring(0, result.length() - 2) : result;
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

    //Пока разрешено склеивать несколько ячеек только как строки
    protected BigDecimal getXLSXBigDecimalFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        if (importColumnDetail == null) return null;
        return getXLSXBigDecimalFieldValue(sheet, importColumnDetail, row, parseIndex(importColumnDetail.indexes[0]), null);
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

    //Пока разрешено склеивать несколько ячеек только как строки
    protected Date getXLSXDateFieldValue(XSSFSheet sheet, Integer row, ImportColumnDetail importColumnDetail) throws UniversalImportException {
        if (importColumnDetail == null) return null;
        return getXLSXDateFieldValue(sheet, importColumnDetail, row, parseIndex(importColumnDetail.indexes[0]), null);
    }

    protected Date getXLSXDateFieldValue(XSSFSheet sheet, ImportColumnDetail importColumnDetail, Integer row, String cell) throws UniversalImportException {
        if (cell == null) return null;
        return getXLSXDateFieldValue(sheet, importColumnDetail, row, parseIndex(cell), null);
    }

    protected Date getXLSXDateFieldValue(XSSFSheet sheet, ImportColumnDetail importColumnDetail, Integer row, Integer cell, Date defaultValue) throws UniversalImportException {
        try {
            if (cell == null) return defaultValue;
            XSSFRow xssfRow = sheet.getRow(row);
            if (xssfRow == null) return defaultValue;
            XSSFCell xssfCell = xssfRow.getCell(cell);
            if (xssfCell == null) return defaultValue;
            if (xssfCell.getCellType() == Cell.CELL_TYPE_NUMERIC)
                return new Date(xssfCell.getDateCellValue().getTime());
            else
                return parseDate(getXLSXFieldValue(sheet, importColumnDetail, row, cell, null, null, String.valueOf(defaultValue)));
        } catch (Exception e) {
            throw new UniversalImportException(importColumnDetail.field, importColumnDetail.getFullIndex(), row, e);
        }
    }

    protected String getDBFFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        return getDBFFieldValue(importFile, importColumnDetail, row, "cp866");
    }

    protected String getDBFFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset) throws UniversalImportException {
        return getDBFFieldValue(importFile, importColumnDetail, row, charset, null);
    }

    protected String getDBFFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, String column, int row, String charset, String defaultValue) throws UniversalImportException {
        return getDBFFieldValue(importFile, importColumnDetail, row, charset, defaultValue);
    }

    protected String getDBFFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset, String defaultValue) throws UniversalImportException {
        try {
            if (importColumnDetail == null) return defaultValue;
            String result = "";
            for (String column : importColumnDetail.indexes) {
                if (column == null) return defaultValue;
                String value;
                if (isConstantValue(column))
                    return column.substring(1);
                if (isDivisionValue(column)) {
                    String[] splittedField = column.split("/");
                    BigDecimal dividedValue = null;
                    for (String arg : splittedField) {
                        BigDecimal argument = getDBFBigDecimalFieldValue(importFile, importColumnDetail, arg.trim(), row, charset, null);
                        dividedValue = dividedValue == null ? argument : (argument == null ? BigDecimal.ZERO : safeDivide(dividedValue, argument));
                    }
                    value = String.valueOf(dividedValue);
                } else if (isOrValue(column)) {
                    value = "";
                    String[] splittedField = column.split("\\|");
                    for (int i = splittedField.length - 1; i >= 0; i--) {
                        String orValue = getDBFFieldValue(importFile, importColumnDetail, splittedField[i], i, charset, null);
                        if (orValue != null) {
                            value = orValue;
                            break;
                        }
                    }
                } else if (column.matches(substringPattern)) {
                    String[] splittedField = column.split(splitPattern);
                    value = getSubstring(new String(importFile.getField(splittedField[0]).getBytes(), charset).trim(),
                            parseIndex(splittedField[1]), splittedField.length > 2 ? parseIndex(splittedField[2]) : null);
                } else if (column.matches(datePatternPattern)) {
                    String[] splittedField = column.split("~");
                    Calendar calendar = Calendar.getInstance();
                    Date date = parseDate(new String(importFile.getField(splittedField[0]).getBytes(), charset).trim());
                    if (date != null) {
                        calendar.setTime(date);
                        return parseDatePattern(splittedField, calendar);
                    } else return null;
                } else {
                    value = new String(importFile.getField(column).getBytes(), charset).trim();
                }
                result += ((result.isEmpty() || value.isEmpty()) ? "" : " ") + value;
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
        String value = getDBFFieldValue(importFile, importColumnDetail, row, charset, defaultValue);
        if (value == null) return null;
        BigDecimal result;
        try {
            result = new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new UniversalImportException(importColumnDetail.field, importColumnDetail.getFullIndex(), row, e);
        }
        return result;
    }

    protected Date getDBFDateFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row) throws UniversalImportException {
        return getDBFDateFieldValue(importFile, importColumnDetail, row, "cp866");
    }

    protected Date getDBFDateFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset) throws UniversalImportException {
        return getDBFDateFieldValue(importFile, importColumnDetail, row, charset, null);
    }

    protected Date getDBFDateFieldValue(DBF importFile, ImportColumnDetail importColumnDetail, int row, String charset, Date defaultValue) throws UniversalImportException {
        String dateString = getDBFFieldValue(importFile, importColumnDetail, row, charset, null);
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
                ((to == null || to > value.length())) ? value.substring(Math.min(value.length(), from)) : value.substring(from, Math.min(value.length(), to + 1));
    }

    static final Locale RU_LOCALE = new Locale("ru");
    static final DateFormatSymbols RU_SYMBOLS = new DateFormatSymbols(RU_LOCALE);
    static final String[] RU_MONTHS = {"января", "февраля", "марта", "апреля", "мая",
            "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"};

    static {
        RU_SYMBOLS.setMonths(RU_MONTHS);
    }

    private Date parseDate(String value) throws ParseException {
        if (value == null || value.trim().isEmpty())
            return null;
        if (value.length() == 8 && !value.contains(".") && Integer.parseInt(value.substring(4, 6)) > 12) {
            //чит для отличия ddMMyyyy от yyyyMMdd
            return new Date(DateUtils.parseDate(value, new String[]{"ddMMyyyy"}).getTime());
        } else if (value.contains("г")) {
            //чит для даты с месяцем прописью
            return new Date(new SimpleDateFormat("dd MMMM yyyy г.", RU_SYMBOLS).parse(value.toLowerCase()).getTime());
        }
        switch(value.length()) {
            case 4:
                return new Date(DateUtils.parseDate(value, new String[]{"MMyy"}).getTime());
            case 6:
                return new Date(DateUtils.parseDate(value, new String[]{"MM,yy_"}).getTime());
            case 7:
                return new Date(DateUtils.parseDate(value, new String[]{"MM.yyyy", "MM-yyyy"}).getTime());
            case 8:
                return new Date(DateUtils.parseDate(value, new String[]{"yyyyMMdd", "dd.MM.yy", "dd/MM/yy"}).getTime());
            case 10:
                return new Date(DateUtils.parseDate(value, new String[]{"dd.MM.yyyy", "dd/MM/yyyy"}).getTime());
            case 19:
                return new Date(DateUtils.parseDate(value, new String[]{"dd.MM.yyyy hh:mm:ss"}).getTime());
        }
        return new Date(DateUtils.parseDate(value, new String[]{"MM,yy_", "MM.yyyy", "MM-yyyy", "MMyy", "yyyyMMdd", "dd.MM.yy", "dd/MM/yy", "dd.MM.yyyy", "dd/MM/yyyy", "dd.MM.yyyy hh:mm:ss"}).getTime());
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

    private boolean isConstantValue(String input) {
        return input != null && input.startsWith("=");
    }

    private boolean isDivisionValue(String input) {
        return input != null && input.contains("/");
    }

    private boolean isOrValue(String input) {
        return input != null && input.contains("|");
    }

    protected boolean getReplaceOnlyNull(Map<String, ImportColumnDetail> importColumns, String columnName) {
        ImportColumnDetail column = importColumns.get(columnName);
        return column != null && column.replaceOnlyNull;
    }
}