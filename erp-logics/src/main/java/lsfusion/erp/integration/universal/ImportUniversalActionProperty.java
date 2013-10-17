package lsfusion.erp.integration.universal;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.commons.lang.time.DateUtils;
import org.apache.poi.hssf.usermodel.HSSFCell;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public abstract class ImportUniversalActionProperty extends ScriptingActionProperty {

    public ImportUniversalActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, new ValueClass[]{valueClass});
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
    }

    public List<List<Object>> initData(int size) {
        List<List<Object>> data = new ArrayList<List<Object>>();
        for (int i = 0; i < size; i++) {
            data.add(new ArrayList<Object>());
        }
        return data;
    }

    private List<BigDecimal> allowedVAT = Arrays.asList(BigDecimal.valueOf(0.0), BigDecimal.valueOf(9.09), BigDecimal.valueOf(16.67), BigDecimal.valueOf(10.0), BigDecimal.valueOf(20.0), BigDecimal.valueOf(24.0));

    protected BigDecimal VATifAllowed(BigDecimal VAT) {
        return allowedVAT.contains(VAT) ? VAT : null;
    }

    protected String getCSVFieldValue(String[] values, Integer[] indexes) throws ParseException {
        return getCSVFieldValue(values, indexes, null);
    }

    protected String getCSVFieldValue(String[] values, Integer[] indexes, String defaultValue) throws ParseException {
        if (indexes == null) return defaultValue;
        String result = "";
        for (Integer index : indexes) {
            String value = getCSVFieldValue(values, index, "");
            result += ((result.isEmpty() || value.isEmpty()) ? "" : " ") + value;
        }
        return result.isEmpty() ? defaultValue : result;
    }

    protected String getCSVFieldValue(String[] values, Integer index, String defaultValue) throws ParseException {
        if (index == null) return defaultValue;
        return values.length <= index ? defaultValue : values[index];
    }

    //Пока разрешено склеивать несколько ячеек только как строки
    protected BigDecimal getCSVBigDecimalFieldValue(String[] values, Integer[] indexes) throws ParseException {
        if (indexes == null) return null;
        return getCSVBigDecimalFieldValue(values, indexes[0], null);
    }

    protected BigDecimal getCSVBigDecimalFieldValue(String[] values, Integer index, BigDecimal defaultValue) throws ParseException {
        String value = getCSVFieldValue(values, index, null);
        return value == null ? defaultValue : new BigDecimal(value);
    }

    //Пока разрешено склеивать несколько ячеек только как строки
    protected Date getCSVDateFieldValue(String[] values, Integer[] indexes) throws ParseException {
        if (indexes == null) return null;
        return getCSVDateFieldValue(values, indexes[0], null);
    }

    protected Date getCSVDateFieldValue(String[] values, Integer index, Date defaultValue) throws ParseException {
        String value = getCSVFieldValue(values, index, null);
        return value == null ? defaultValue : new Date(DateUtils.parseDate(value, new String[]{"dd.MM.yyyy"}).getTime());
    }

    protected String getXLSFieldValue(HSSFSheet sheet, Integer row, Integer[] cells) throws ParseException {
        return getXLSFieldValue(sheet, row, cells, null);
    }

    protected String getXLSFieldValue(HSSFSheet sheet, Integer row, Integer[] cells, String defaultValue) throws ParseException {
        if (cells == null) return defaultValue;
        String result = "";
        for (Integer cell : cells) {
            String value = getXLSFieldValue(sheet, row, cell, "");
            result += ((result.isEmpty() || value.isEmpty()) ? "" : " ") + value;
        }
        return result.isEmpty() ? defaultValue : result;
    }

    protected String getXLSFieldValue(HSSFSheet sheet, Integer row, Integer cell, String defaultValue) throws ParseException {
        if (cell == null) return defaultValue;
        HSSFRow hssfRow = sheet.getRow(row);
        if (hssfRow == null) return defaultValue;
        HSSFCell hssfCell = hssfRow.getCell(cell);
        if (hssfCell == null) return defaultValue;
        switch (hssfCell.getCellType()) {
            case Cell.CELL_TYPE_NUMERIC:
                String result = new DecimalFormat("#.#####").format(hssfCell.getNumericCellValue());
                return result.endsWith(".0") ? result.substring(0, result.length() - 2) : result;
            case Cell.CELL_TYPE_STRING:
            default:
                return (hssfCell.getStringCellValue().isEmpty()) ? defaultValue : hssfCell.getStringCellValue().trim();
        }
    }

    //Пока разрешено склеивать несколько ячеек только как строки
    protected BigDecimal getXLSBigDecimalFieldValue(HSSFSheet sheet, Integer row, Integer[] cells) throws ParseException {
        if (cells == null) return null;
        return getXLSBigDecimalFieldValue(sheet, row, cells[0], null);
    }

    protected BigDecimal getXLSBigDecimalFieldValue(HSSFSheet sheet, Integer row, Integer cell, BigDecimal defaultValue) throws ParseException, NumberFormatException {
        if (cell == null) return defaultValue;
        HSSFRow hssfRow = sheet.getRow(row);
        if (hssfRow == null) return defaultValue;
        HSSFCell hssfCell = hssfRow.getCell(cell);
        if (hssfCell == null) return defaultValue;
        switch (hssfCell.getCellType()) {
            case Cell.CELL_TYPE_NUMERIC:
                return BigDecimal.valueOf(hssfCell.getNumericCellValue());
            case Cell.CELL_TYPE_STRING:
            default:
                String result = hssfCell.getStringCellValue().trim();
                return result.isEmpty() ? defaultValue : new BigDecimal(result);
        }
    }

    //Пока разрешено склеивать несколько ячеек только как строки
    protected Date getXLSDateFieldValue(HSSFSheet sheet, Integer row, Integer[] cells) throws ParseException {
        if (cells == null) return null;
        return getXLSDateFieldValue(sheet, row, cells[0], null);
    }

    protected Date getXLSDateFieldValue(HSSFSheet sheet, Integer row, Integer cell, Date defaultValue) throws ParseException {
        if (cell == null) return defaultValue;
        HSSFRow hssfRow = sheet.getRow(row);
        if (hssfRow == null) return defaultValue;
        HSSFCell hssfCell = hssfRow.getCell(cell);
        if(hssfCell==null) return defaultValue;
        if (hssfCell.getCellType() == Cell.CELL_TYPE_NUMERIC)
            return new Date(hssfCell.getDateCellValue().getTime());
        else
            return parseDate(getXLSFieldValue(sheet, row, cell, String.valueOf(defaultValue)));

    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, Integer[] cells) throws ParseException {
        return getXLSXFieldValue(sheet, row, cells, null);
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, Integer[] cells, String defaultValue) throws ParseException {
        if (cells == null) return defaultValue;
        String result = "";
        for (Integer cell : cells) {
            String value = getXLSXFieldValue(sheet, row, cell, "");
            result += (result.isEmpty() || value.isEmpty() ? "" : " ") + value;
        }
        return result.isEmpty() ? defaultValue : result;
    }

    protected String getXLSXFieldValue(XSSFSheet sheet, Integer row, Integer cell, String defaultValue) throws ParseException {
        if (cell == null) return defaultValue;
        XSSFRow xssfRow = sheet.getRow(row);
        if (xssfRow == null) return defaultValue;
        XSSFCell xssfCell = xssfRow.getCell(cell);
        if (xssfCell == null) return defaultValue;
        switch (xssfCell.getCellType()) {
            case Cell.CELL_TYPE_NUMERIC:
                String result = new DecimalFormat("#.#####").format(xssfCell.getNumericCellValue());
                return result.endsWith(".0") ? result.substring(0, result.length() - 2) : result;
            case Cell.CELL_TYPE_STRING:
            default:
                return (xssfCell.getStringCellValue().isEmpty()) ? defaultValue : xssfCell.getStringCellValue().trim();
        }
    }

    //Пока разрешено склеивать несколько ячеек только как строки
    protected BigDecimal getXLSXBigDecimalFieldValue(XSSFSheet sheet, Integer row, Integer[] cells) throws ParseException {
        if (cells == null) return null;
        return getXLSXBigDecimalFieldValue(sheet, row, cells[0], null);
    }

    protected BigDecimal getXLSXBigDecimalFieldValue(XSSFSheet sheet, Integer row, Integer cell, BigDecimal defaultValue) throws ParseException {
        if (cell == null) return defaultValue;
        XSSFRow xssfRow = sheet.getRow(row);
        if (xssfRow == null) return defaultValue;
        XSSFCell xssfCell = xssfRow.getCell(cell);
        if (xssfCell == null) return defaultValue;
        switch (xssfCell.getCellType()) {
            case Cell.CELL_TYPE_NUMERIC:
                return BigDecimal.valueOf(xssfCell.getNumericCellValue());
            case Cell.CELL_TYPE_STRING:
            default:
                String result = xssfCell.getStringCellValue().trim();
                return result.isEmpty() ? defaultValue : new BigDecimal(result);
        }
    }

    //Пока разрешено склеивать несколько ячеек только как строки
    protected Date getXLSXDateFieldValue(XSSFSheet sheet, Integer row, Integer[] cells) throws ParseException {
        if (cells == null) return null;
        return getXLSXDateFieldValue(sheet, row, cells[0], null);
    }

    protected Date getXLSXDateFieldValue(XSSFSheet sheet, Integer row, Integer cell, Date defaultValue) throws ParseException {
        if (cell == null) return defaultValue;
        XSSFRow xssfRow = sheet.getRow(row);
        if (xssfRow == null) return defaultValue;
        XSSFCell xssfCell = xssfRow.getCell(cell);
        if(xssfCell==null) return defaultValue;
        if (xssfCell.getCellType() == Cell.CELL_TYPE_NUMERIC)
            return new Date(xssfCell.getDateCellValue().getTime());
        else
            return parseDate(getXLSXFieldValue(sheet, row, cell, String.valueOf(defaultValue)));                        
    }

    protected String getDBFFieldValue(DBF importFile, String[] fields) throws UnsupportedEncodingException {
        return getDBFFieldValue(importFile, fields, "cp866", null);
    }

    protected String getDBFFieldValue(DBF importFile, String[] fields, String charset, String defaultValue) throws UnsupportedEncodingException {
        try {
            if (fields == null) return defaultValue;
            String result = "";
            for (String field : fields) {
                String value = new String(importFile.getField(field).getBytes(), charset).trim();
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

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, String[] fields, String charset, String defaultValue) throws UnsupportedEncodingException {
        String value = getDBFFieldValue(importFile, fields, charset, defaultValue);
        return value == null ? null : new BigDecimal(value);
    }

    protected Date getDBFDateFieldValue(DBF importFile, String[] fields) throws UnsupportedEncodingException, ParseException {
        return getDBFDateFieldValue(importFile, fields, "cp866", null);
    }

    protected Date getDBFDateFieldValue(DBF importFile, String[] fields, String charset, Date defaultValue) throws UnsupportedEncodingException, ParseException {
        String dateString = getDBFFieldValue(importFile, fields, charset, "");
        return dateString.isEmpty() ? defaultValue : new Date(DateUtils.parseDate(dateString, new String[]{"yyyyMMdd"}).getTime());
    }

    protected Integer[] getColumnNumbers(String[] importColumns) {
        if (importColumns == null || importColumns.length == 0)
            return null;
        Integer[] result = new Integer[importColumns.length];
        for (int i = 0; i < importColumns.length; i++)
            result[i] = Integer.parseInt(importColumns[i]) - 1;
        return result;
    }

    static final Locale RU_LOCALE = new Locale("ru");
    static final DateFormatSymbols RU_SYMBOLS = new DateFormatSymbols(RU_LOCALE);
    static final String[] RU_MONTHS = {"января", "февраля", "марта", "апреля", "мая",
            "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"};
    static {
        RU_SYMBOLS.setMonths(RU_MONTHS);
    }
    
    private Date parseDate(String value) {
        try {
            return new Date(new SimpleDateFormat("dd MMMM yyyy г.", RU_SYMBOLS).parse(value.toLowerCase()).getTime());
        } catch (ParseException e) {
            return null;
        }
    }
}

