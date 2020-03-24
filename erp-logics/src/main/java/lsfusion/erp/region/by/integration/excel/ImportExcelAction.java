package lsfusion.erp.region.by.integration.excel;

import jxl.*;
import jxl.read.biff.BiffException;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.integration.DefaultImportAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalDate;

public class ImportExcelAction extends DefaultImportAction {

    static long minDate = new Date(2001 - 1900, 0, 1).getTime();
    static long maxDate = new Date(2030 - 1900, 0, 1).getTime();

    public ImportExcelAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    protected static Sheet getSheet(RawFileData file, int columnsCount) throws IOException, BiffException {
        WorkbookSettings ws = new WorkbookSettings();
        ws.setGCDisabled(true);
        Workbook Wb = Workbook.getWorkbook(file.getInputStream(), ws);
        Sheet sheet = Wb.getSheet(0);
        if(sheet.getRows() > 0 && sheet.getRow(0).length < columnsCount)
            throw new RuntimeException(String.format("Недостаточно колонок для импорта (%s при необходимых %s)", sheet.getRow(0).length, columnsCount));
        return sheet;
    }

    protected static String parseString(Cell cell) {
        String value = cell == null ? null : (cell instanceof NumberCell ? String.valueOf(new DecimalFormat("#.#####").format(((NumberCell) cell).getValue())) : cell.getContents());
        return value == null || value.isEmpty() ? null : value.trim();
    }

    protected static BigDecimal parseBigDecimal(Cell cell) throws ParseException {
        String value = cell == null ? null : cell.getContents();
        return value == null || value.trim().isEmpty() ? null : BigDecimal.valueOf(NumberFormat.getInstance().parse(value.trim()).doubleValue());
    }

    protected static Boolean parseBoolean(Cell cell) {
        return cell != null && cell.getContents().equals("1") ? true : null;
    }

    protected static LocalDate parseDateValue(Cell cell) throws ParseException {
        return parseDateValue(cell, null);
    }

    protected static LocalDate parseDateValue(Cell cell, Date defaultDate) throws ParseException {
        Date date = cell == null ? null : parseDate(cell.getContents(), defaultDate);
        if (date == null || date.getTime() < minDate || date.getTime() >= maxDate)
            date = defaultDate;
        return sqlDateToLocalDate(date);
    }
}