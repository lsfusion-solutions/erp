package lsfusion.erp.integration.universal;

import lsfusion.erp.integration.DefaultImportActionProperty;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.commons.lang.time.DateUtils;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.DateFormatSymbols;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public abstract class ImportUniversalActionProperty extends DefaultImportActionProperty {

    public ImportUniversalActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, valueClass);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
    }

    String splitPattern = "\\^\\(|\\)|,";
    String substringPattern = ".*\\^\\(\\d+,\\d+\\)";

    protected String getCSVFieldValue(String[] values, String[] indexes) throws ParseException {
        return getCSVFieldValue(values, indexes, null);
    }

    protected String getCSVFieldValue(String[] values, String[] cells, String defaultValue) throws ParseException {
        if (cells == null) return defaultValue;
        String result = "";
        for (String cell : cells) {
            String value;
            if (cell.matches(substringPattern)) {
                String[] splittedCell = cell.split(splitPattern);
                value = getCSVFieldValue(values, parseIndex(splittedCell[0]), parseIndex(splittedCell[1]), parseIndex(splittedCell[2]), "");
            } else {
                value = getCSVFieldValue(values, parseIndex(cell), null, null, "");
            }
            result += ((result.isEmpty() || value.isEmpty()) ? "" : " ") + value;
        }
        return result.isEmpty() ? defaultValue : result;
    }

    protected String getCSVFieldValue(String[] values, Integer index, Integer from, Integer to, String defaultValue) throws ParseException {
        if (index == null) return defaultValue;
        return values.length <= index ? defaultValue : getSubstring(values[index], from, to);
    }

    //Пока разрешено склеивать несколько ячеек только как строки
    protected BigDecimal getCSVBigDecimalFieldValue(String[] values, String[] indexes) throws ParseException {
        if (indexes == null) return null;
        return getCSVBigDecimalFieldValue(values, indexes[0], null);
    }

    protected BigDecimal getCSVBigDecimalFieldValue(String[] values, String index, BigDecimal defaultValue) throws ParseException {
        String value = getCSVFieldValue(values, parseIndex(index), null, null, null);
        return value == null ? defaultValue : new BigDecimal(value);
    }

    //Пока разрешено склеивать несколько ячеек только как строки
    protected Date getCSVDateFieldValue(String[] values, String[] indexes) throws ParseException {
        if (indexes == null) return null;
        return getCSVDateFieldValue(values, indexes[0], null);
    }

    protected Date getCSVDateFieldValue(String[] values, String index, Date defaultValue) throws ParseException {
        String value = getCSVFieldValue(values, parseIndex(index), null, null, null);
        return value == null ? defaultValue : new Date(DateUtils.parseDate(value, new String[]{"dd.MM.yyyy"}).getTime());
    }

    protected String getXLSFieldValue(HSSFSheet sheet, Integer row, String[] cells) throws ParseException {
        return getXLSFieldValue(sheet, row, cells, null);
    }

    protected String getXLSFieldValue(HSSFSheet sheet, Integer row, String[] cells, String defaultValue) throws ParseException {
        if (cells == null) return defaultValue;
        String result = "";
        for (String cell : cells) {
            String value;
            if (cell.matches(substringPattern)) {
                String[] splittedCell = cell.split(splitPattern);
                value = getXLSFieldValue(sheet, row, parseIndex(splittedCell[0]), parseIndex(splittedCell[1]), parseIndex(splittedCell[2]), "");
            } else {
                value = getXLSFieldValue(sheet, row, parseIndex(cell), null, null, "");
            }
            result += ((result.isEmpty() || value.isEmpty()) ? "" : " ") + value;
        }
        return result.isEmpty() ? defaultValue : result;
    }

    //Пока подстроку разрешено брать только для строковых полей
    protected String getXLSFieldValue(HSSFSheet sheet, Integer row, Integer cell, Integer from, Integer to, String defaultValue) throws ParseException {
        if (cell == null) return defaultValue;
        HSSFRow hssfRow = sheet.getRow(row);
        if (hssfRow == null) return defaultValue;
        HSSFCell hssfCell = hssfRow.getCell(cell);
        if (hssfCell == null) return defaultValue;
        String result;
        switch (hssfCell.getCellType()) {
            case Cell.CELL_TYPE_NUMERIC:
                result = new DecimalFormat("#.#####").format(hssfCell.getNumericCellValue());
                result = result.endsWith(".0") ? result.substring(0, result.length() - 2) : result;
                break;
            case Cell.CELL_TYPE_STRING:
            default:
                result = (hssfCell.getStringCellValue().isEmpty()) ? defaultValue : hssfCell.getStringCellValue().trim();
                break;
        }
        return getSubstring(result, from, to);
    }

    //Пока разрешено склеивать несколько ячеек только как строки
    protected BigDecimal getXLSBigDecimalFieldValue(HSSFSheet sheet, Integer row, String[] cells) throws ParseException {
        if (cells == null) return null;
        return getXLSBigDecimalFieldValue(sheet, row, parseIndex(cells[0]), null);
    }

    protected BigDecimal getXLSBigDecimalFieldValue(HSSFSheet sheet, Integer row, Integer cell, BigDecimal defaultValue) throws ParseException, NumberFormatException {
        if (cell == null) return defaultValue;
        HSSFRow hssfRow = sheet.getRow(row);
        if (hssfRow == null) return defaultValue;
        HSSFCell hssfCell = hssfRow.getCell(cell);
        if (hssfCell == null) return defaultValue;
        switch (hssfCell.getCellType()) {
            case Cell.CELL_TYPE_NUMERIC:
            case Cell.CELL_TYPE_FORMULA:
                return BigDecimal.valueOf(hssfCell.getNumericCellValue());
            case Cell.CELL_TYPE_STRING:
            default:
                String result = hssfCell.getStringCellValue().trim();
                return result.isEmpty() ? defaultValue : new BigDecimal(result);
        }
    }

    //Пока разрешено склеивать несколько ячеек только как строки
    protected Date getXLSDateFieldValue(HSSFSheet sheet, Integer row, String[] cells) throws ParseException {
        if (cells == null) return null;
        return getXLSDateFieldValue(sheet, row, parseIndex(cells[0]), null);
    }

    protected Date getXLSDateFieldValue(HSSFSheet sheet, Integer row, String cell) throws ParseException {
        if (cell == null) return null;
        return getXLSDateFieldValue(sheet, row, parseIndex(cell), null);
    }

    protected Date getXLSDateFieldValue(HSSFSheet sheet, Integer row, Integer cell, Date defaultValue) throws ParseException {
        if (cell == null) return defaultValue;
        HSSFRow hssfRow = sheet.getRow(row);
        if (hssfRow == null) return defaultValue;
        HSSFCell hssfCell = hssfRow.getCell(cell);
        if (hssfCell == null) return defaultValue;
        if (hssfCell.getCellType() == Cell.CELL_TYPE_NUMERIC) {
            if (HSSFDateUtil.isCellDateFormatted(hssfCell)) {
                return new Date(hssfCell.getDateCellValue().getTime());
            } else {
                return parseDate(getXLSFieldValue(sheet, row, cell, null, null, String.valueOf(defaultValue)));
            }
        } else
            return parseDate(getXLSFieldValue(sheet, row, cell, null, null, String.valueOf(defaultValue)));
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, String[] cells) throws ParseException {
        return getXLSXFieldValue(sheet, row, cells, null);
    }

    //Пока подстроку разрешено брать только для строковых полей
    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, String[] cells, String defaultValue) throws ParseException {
        if (cells == null) return defaultValue;
        String result = "";
        for (String cell : cells) {
            String value;
            if (cell.matches(substringPattern)) {
                String[] splittedCell = cell.split(splitPattern);
                value = getXLSXFieldValue(sheet, row, parseIndex(splittedCell[0]), parseIndex(splittedCell[1]), parseIndex(splittedCell[2]), "");
            } else {
                value = getXLSXFieldValue(sheet, row, parseIndex(cell), null, null, "");
            }
            result += (result.isEmpty() || value.isEmpty() ? "" : " ") + value;
        }
        return result.isEmpty() ? defaultValue : result;
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, Integer cell, Integer from, Integer to, String defaultValue) throws ParseException {
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
    }

    //Пока разрешено склеивать несколько ячеек только как строки
    protected BigDecimal getXLSXBigDecimalFieldValue(XSSFSheet sheet, Integer row, String[] cells) throws ParseException {
        if (cells == null) return null;
        return getXLSXBigDecimalFieldValue(sheet, row, parseIndex(cells[0]), null);
    }

    protected BigDecimal getXLSXBigDecimalFieldValue(XSSFSheet sheet, Integer row, String cell) throws ParseException {
        if (cell == null) return null;
        return getXLSXBigDecimalFieldValue(sheet, row, parseIndex(cell), null);
    }

    protected BigDecimal getXLSXBigDecimalFieldValue(XSSFSheet sheet, Integer row, Integer cell, BigDecimal defaultValue) throws ParseException {
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
    }

    //Пока разрешено склеивать несколько ячеек только как строки
    protected Date getXLSXDateFieldValue(XSSFSheet sheet, Integer row, String[] cells) throws ParseException {
        if (cells == null) return null;
        return getXLSXDateFieldValue(sheet, row, parseIndex(cells[0]), null);
    }

    protected Date getXLSXDateFieldValue(XSSFSheet sheet, Integer row, String cell) throws ParseException {
        if (cell == null) return null;
        return getXLSXDateFieldValue(sheet, row, parseIndex(cell), null);
    }

    protected Date getXLSXDateFieldValue(XSSFSheet sheet, Integer row, Integer cell, Date defaultValue) throws ParseException {
        if (cell == null) return defaultValue;
        XSSFRow xssfRow = sheet.getRow(row);
        if (xssfRow == null) return defaultValue;
        XSSFCell xssfCell = xssfRow.getCell(cell);
        if (xssfCell == null) return defaultValue;
        if (xssfCell.getCellType() == Cell.CELL_TYPE_NUMERIC)
            return new Date(xssfCell.getDateCellValue().getTime());
        else
            return parseDate(getXLSXFieldValue(sheet, row, cell, null, null, String.valueOf(defaultValue)));
    }

    protected String getDBFFieldValue(DBF importFile, String[] fields) throws UnsupportedEncodingException {
        return getDBFFieldValue(importFile, fields, "cp866", null);
    }

    protected String getDBFFieldValue(DBF importFile, String[] fields, String charset, String defaultValue) throws UnsupportedEncodingException {
        try {
            if (fields == null) return defaultValue;
            String result = "";
            for (String field : fields) {
                String value;
                if (field.matches(substringPattern)) {
                    String[] splittedField = field.split(splitPattern);
                    value = getSubstring(new String(importFile.getField(splittedField[0]).getBytes(), charset).trim(),
                            parseIndex(splittedField[1]), parseIndex(splittedField[2]));
                } else {
                    value = new String(importFile.getField(field).getBytes(), charset).trim();
                }
                result += ((result.isEmpty() || value.isEmpty()) ? "" : " ") + value;
            }
            return result.isEmpty() ? defaultValue : result;
        } catch (xBaseJException e) {
            return defaultValue;
        }
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, String[] fields) throws UnsupportedEncodingException {
        return getDBFBigDecimalFieldValue(importFile, fields, "cp866", null);
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, String field) throws UnsupportedEncodingException {
        return getDBFBigDecimalFieldValue(importFile, new String[]{field}, "cp866", null);
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, String[] fields, String charset, String defaultValue) throws UnsupportedEncodingException {
        String value = getDBFFieldValue(importFile, fields, charset, defaultValue);
        if (value == null) return null;
        BigDecimal result = null;
        try {
            result = new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
        }

        return result;
    }

    protected Date getDBFDateFieldValue(DBF importFile, String[] fields) throws UnsupportedEncodingException, ParseException {
        return getDBFDateFieldValue(importFile, fields, "cp866", null);
    }

    protected Date getDBFDateFieldValue(DBF importFile, String[] fields, String charset, Date defaultValue) throws UnsupportedEncodingException, ParseException {
        String dateString = getDBFFieldValue(importFile, fields, charset, "");
        if (dateString.isEmpty()) return defaultValue;
        return dateString.isEmpty() ? defaultValue : parseDate(dateString);
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
                ((to == null || to > value.length())) ? value.substring(from) : value.substring(from, to + 1);
    }

    static final Locale RU_LOCALE = new Locale("ru");
    static final DateFormatSymbols RU_SYMBOLS = new DateFormatSymbols(RU_LOCALE);
    static final String[] RU_MONTHS = {"января", "февраля", "марта", "апреля", "мая",
            "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"};

    static {
        RU_SYMBOLS.setMonths(RU_MONTHS);
    }

    private Date parseDate(String value) throws ParseException {
        try {
            if (value.length() == 4 || value.length() == 7) {
                //чит для даты в формате MMyy / MM.yyyy / MM-yyyy (без дня) : выставляем последний день месяца 
                Calendar dateWithoutDay = Calendar.getInstance();
                dateWithoutDay.setTime(DateUtils.parseDate(value, new String[]{"MMyy", "MM.yyyy", "MM-yyyy"}));
                dateWithoutDay.set(Calendar.DAY_OF_MONTH, dateWithoutDay.getActualMaximum(Calendar.DAY_OF_MONTH));
                return new Date(dateWithoutDay.getTime().getTime());
            } else if (value.length() == 8 && Integer.parseInt(value.substring(3, 5)) > 12) {
                //чит для отличия ddMMyyyy от yyyyMMdd
                return new Date(DateUtils.parseDate(value, new String[]{"ddMMyyyy"}).getTime());
            } else if (value.length() > 12) {
                //чит для даты с месяцем прописью
                return new Date(new SimpleDateFormat("dd MMMM yyyy г.", RU_SYMBOLS).parse(value.toLowerCase()).getTime());
            }
            return new Date(DateUtils.parseDate(value, new String[]{"yyyyMMdd", "dd.MM.yy", "dd.MM.yyyy"}).getTime());
        } catch (ParseException e) {
            return null;
        }
    }
}

