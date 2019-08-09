package lsfusion.erp.utils;

import com.google.common.collect.Lists;
import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.ComThread;
import com.jacob.com.Dispatch;
import com.jacob.com.SafeArray;
import com.jacob.com.Variant;
import lsfusion.interop.form.print.ReportGenerator;
import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;
import lsfusion.interop.form.print.ReportGenerationData;
import net.sf.jasperreports.engine.JRException;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ExportExcelPivotClientAction implements ClientAction {

    Integer xlRowField = 1;
    Integer xlColumnField = 2;
    Integer xlFilterField = 3;
    Integer xlDataField = 4;
    
    Integer xlSum = -4157;
    Integer xlCount = -4112;
    Integer xlAverage = -4106;
    
    Integer firstRow = 2;
    Integer firstColumn = 2;

    ReportGenerationData reportData;   
    String title;
    Integer titleRowHeight;
    List<List<List<Object>>> rowFields;
    List<List<List<Object>>> columnFields;
    List<List<List<Object>>> filterFields;
    List<List<List<Object>>> cellFields;

    public ExportExcelPivotClientAction(ReportGenerationData reportData, String title, Integer titleRowHeight, List<List<List<Object>>> rowFields, List<List<List<Object>>> columnFields,
                                        List<List<List<Object>>> filterFields, List<List<List<Object>>> cellFields) {
        this.reportData = reportData;
        this.title = title;
        this.titleRowHeight = titleRowHeight;
        this.rowFields = rowFields;
        this.columnFields = columnFields;
        this.filterFields = filterFields;
        this.cellFields = cellFields;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {
        if(rowFields.size()!=columnFields.size() || columnFields.size()!=filterFields.size() || filterFields.size()!=cellFields.size())
            throw new RuntimeException("Некорректное количество параметров сводных таблиц");
        try {
            runExcelPivot();
        } catch (JRException | ClassNotFoundException e) {
            throw new RuntimeException("Ошибка при формировании сводной таблицы", e);
        }
        return null;
    }

    private void runExcelPivot() throws IOException, JRException, ClassNotFoundException {

        ActiveXComponent excelComponent = new ActiveXComponent("Excel.Application");

        File reportFile = ReportGenerator.exportToXlsx(reportData);
        Dispatch workbooks = excelComponent.getProperty("Workbooks").toDispatch();
        Dispatch workbook = Dispatch.call(workbooks, "Open", reportFile.getAbsolutePath()).toDispatch();

        Dispatch sourceSheet = Dispatch.get(workbook, "ActiveSheet").toDispatch();
        Dispatch sheets = Dispatch.get(workbook, "Worksheets").toDispatch();

        int pivotTableCount = rowFields.size();
        for (int i = pivotTableCount - 1; i >= 0; i--) {
            
            List<List<Object>> rowFieldsEntry = rowFields.get(i);
            List<List<Object>> columnFieldsEntry = columnFields.get(i);
            List<List<Object>> filterFieldsEntry = filterFields.get(i);
            List<List<Object>> cellFieldsEntry = cellFields.get(i);
            
            Dispatch destinationSheet = Dispatch.get(sheets, "Add").toDispatch();
            Dispatch.put(destinationSheet, "Name", "PivotTable" + (i + 1));

            Dispatch usedRange = Dispatch.get(sourceSheet, "UsedRange").toDispatch();
            Integer rowsCount = Dispatch.get(Dispatch.get(usedRange, "Rows").toDispatch(), "Count").getInt();
            Integer columnsCount = Dispatch.get(Dispatch.get(usedRange, "Columns").toDispatch(), "Count").getInt();

            int j = 1;
            if(title != null) {
                for (String titleString : title.split("\\\\n|\\n")) {                   
                    Dispatch cell = Dispatch.invoke(destinationSheet, "Range", Dispatch.Get, new Object[] {"A" + j}, new int[1]).toDispatch();
                    Dispatch.put(cell, "Value", titleString);
                    j++;
                }
            }

            if (rowsCount > 2) {
                String lastCell = getCellIndex(columnsCount - 1, rowsCount == 0 ? 2 : (rowsCount - 1));
                Integer firstRowIndex = j + (filterFieldsEntry == null ? 0 : filterFieldsEntry.size()) + 1;
                Dispatch sourceDataNativePeer = Dispatch.invoke(sourceSheet, "Range", Dispatch.Get, new Object[]{"B2:" + lastCell}, new int[1]).toDispatch();
                String destinationIndex = "A" + firstRowIndex;
                Dispatch destinationNativePeer = Dispatch.invoke(destinationSheet, "Range", Dispatch.Get, new Object[]{destinationIndex}, new int[1]).toDispatch();

                Variant unspecified = Variant.VT_MISSING;
                Dispatch pivotTableWizard = Dispatch.invoke(workbook, "PivotTableWizard", Dispatch.Get, new Object[]{new Variant(1),  //SourceType
                        new Variant(sourceDataNativePeer), //SourceData
                        new Variant(destinationNativePeer), //TableDestination
                        new Variant("PivotTable"), //TableName
                        new Variant(true), //RowGrand
                        new Variant(true), //ColumnGrand
                        new Variant(true), //SaveData
                        new Variant(true), //HasAutoFormat
                        unspecified, //AutoPage
                        unspecified, //Reserved
                        new Variant(false), //BackgroundQuery
                        new Variant(false), //OptimizeCache
                        new Variant(1), //PageFieldOrder
                        unspecified, //PageFieldWrapCount
                        unspecified, //ReadData
                        unspecified //Connection
                }, new int[1]).toDispatch();

                LinkedHashMap<Integer, Dispatch> fieldDispatchMap = getIndexFieldDispatchMap(pivotTableWizard, columnsCount);

                int count = firstRow;
                LinkedHashMap<Integer, String> fieldCaptionMap = getFieldCaptionMap(sourceSheet, columnsCount);

                LinkedHashMap<Integer, Integer> fieldsMap = getFieldsMap(sourceSheet, columnsCount, i);

                LinkedHashMap<String, Dispatch> rowDispatchFieldsMap = new LinkedHashMap<>();
                LinkedHashMap<String, Dispatch> columnDispatchFieldsMap = new LinkedHashMap<>();
                LinkedHashMap<String, Dispatch> filterDispatchFieldsMap = new LinkedHashMap<>();
                LinkedHashMap<String, Dispatch> cellDispatchFieldsMap = new LinkedHashMap<>();


                for (Map.Entry<Integer, Integer> entry : fieldsMap.entrySet()) {
                    Integer orientation = entry.getValue();
                    if (orientation != null) {
                        Dispatch fieldDispatch = fieldDispatchMap.get(count);
                        if (orientation.equals(xlRowField)) {
                            rowDispatchFieldsMap.put(fieldCaptionMap.get(count), fieldDispatch);
                        } else if (orientation.equals(xlColumnField)) {
                            columnDispatchFieldsMap.put(fieldCaptionMap.get(count), fieldDispatch);
                        } else if (orientation.equals(xlFilterField)) {
                            filterDispatchFieldsMap.put(fieldCaptionMap.get(count), fieldDispatch);
                        } else if (orientation.equals(xlDataField)) {
                            cellDispatchFieldsMap.put(fieldCaptionMap.get(count), fieldDispatch);
                        }
                    }
                    count++;
                }

                for (List<Object> entry : rowFieldsEntry) {
                    Dispatch field = rowDispatchFieldsMap.get(entry.get(0));
                    if (field != null) {
                        Dispatch.put(field, "Orientation", new Variant(xlRowField));
                        boolean noSubTotal = (boolean) entry.get(6);
                        if(noSubTotal) {
                            SafeArray array = new SafeArray(Variant.VariantBoolean, 12);
                            for (int c = 0; c < 12; c++)
                                array.setBoolean(c, false);
                            Dispatch.put(field, "Subtotals", array);
                        }
                    }
                }

                for (List<Object> entry : columnFieldsEntry) {
                    Dispatch field = columnDispatchFieldsMap.get(entry.get(0));
                    if (field != null)
                        Dispatch.put(field, "Orientation", new Variant(xlColumnField));
                }

                //фильтры по какой-то причине требуют обратного порядка
                if(filterFieldsEntry != null) {
                    for (List<Object> entry : Lists.reverse(filterFieldsEntry)) {
                        Dispatch field = filterDispatchFieldsMap.get(entry.get(0));
                        if (field != null)
                            Dispatch.put(field, "Orientation", new Variant(xlFilterField));
                    }
                }

                int fieldCount = 0;
                for (List<Object> entry : cellFieldsEntry) {
                    String fieldValue = (String) entry.get(0);
                    String formula = (String) entry.get(1);
                    String caption = (String) entry.get(2);
                    String numberFormat = (String) entry.get(3);
                    Integer columnWidth = (Integer) entry.get(4);

                    if (fieldValue != null) {

                        try {
                            Dispatch field;

                            if (formula != null) {
                                String resultFormula = getResultFormula(cellFieldsEntry, formula);
                                Dispatch calculatedFields = Dispatch.call(pivotTableWizard, "CalculatedFields").toDispatch();
                                field = Dispatch.call(calculatedFields, "Add", caption, resultFormula, true).toDispatch();
                                cellDispatchFieldsMap.put(fieldValue, field);
                            } else {
                                field = cellDispatchFieldsMap.get(fieldValue);
                            }

                            if (field == null) {
                                throw new RuntimeException("Field not found");
                            } else {
                                fieldCount++;
                                Dispatch.put(field, "Orientation", new Variant(xlDataField));
                                if (formula == null)
                                    Dispatch.put(field, "Function", new Variant(xlSum));
                                caption = Dispatch.get(field, "Caption").getString().replace("Сумма по полю ", "");
                                Dispatch.put(field, "Caption", new Variant(caption + "*"));
                                if (numberFormat != null)
                                    Dispatch.put(field, "NumberFormat", new Variant(numberFormat));
                                if (columnWidth != null)
                                    Dispatch.put(getColumn(destinationSheet, rowFieldsEntry.size() + fieldCount + 1), "ColumnWidth", new Variant(columnWidth));
                            }
                        } catch (IllegalStateException e) {
                            throw new RuntimeException(String.format("Incorrect Field %s: ", fieldValue), e);
                        }
                    }
                }
                
                if (i == pivotTableCount - 1) {
                    Dispatch field = Dispatch.get(pivotTableWizard, "DataPivotField").toDispatch();
                    if (fieldCount > 1)
                        Dispatch.put(field, "Orientation", new Variant(xlColumnField));
                }

                //set Fit to page
                Dispatch.put(Dispatch.invoke(destinationSheet, "Rows", Dispatch.Get, new Object[] {firstRowIndex + columnFieldsEntry.size() + 1}, new int[1]).toDispatch(), 
                        "RowHeight", new Variant((titleRowHeight == null ? 1 : titleRowHeight) * 15));
                Dispatch pageSetup = Dispatch.get(destinationSheet, "PageSetup").getDispatch();
                Dispatch.put(pageSetup, "Zoom", new Variant(false));
                Dispatch.put(pageSetup, "FitToPagesWide", new Variant(1));
                Dispatch.put(pageSetup, "FitToPagesTall", new Variant(false));

                Dispatch pivotUsedRange = Dispatch.get(destinationSheet, "UsedRange").toDispatch();
                Integer pivotColumnsCount = Dispatch.get(Dispatch.get(pivotUsedRange, "Columns").toDispatch(), "Count").getInt();
                
                //set WrapText
                for(int c = 1; c <= pivotColumnsCount; c++) {
                    int row = j + columnFieldsEntry.size() + (filterFieldsEntry == null ? 0 : filterFieldsEntry.size()) + 2;
                    Dispatch cell = getCell(destinationSheet, c, row);
                    Dispatch.put(cell, "WrapText", new Variant(true));
                }

                count = 0;
                for (List<Object> entry : cellFieldsEntry) {
                    String fieldValue = (String) entry.get(0);
                    if (fieldValue != null) {
                        count++;
                    }
                }

                //set column width
                for (List<Object> entry : cellFieldsEntry) {
                    String fieldValue = (String) entry.get(0);
                    Integer columnWidth = (Integer) entry.get(4);
                    Integer columnTotalWidth = (Integer) entry.get(5);
                    if (fieldValue != null) {
                        Dispatch field = cellDispatchFieldsMap.get(fieldValue);
                        if (field == null)
                            throw new RuntimeException("Set column width failed (field not found)");
                        String captionField = Dispatch.get(field, "Caption").getString();
                        int rowIndex = firstRowIndex + columnFieldsEntry.size() + 1;
                        int rowTotalIndex = firstRowIndex + columnFieldsEntry.size();
                        for (int c = 1; c <= pivotColumnsCount; c++) {
                            if (columnWidth != null) {
                                Variant cell = getCellVariant(destinationSheet, c, rowIndex);
                                String cellCaption = cell.isNull() || cell.getvt() != 8 ? "" : cell.getString();
                                if (captionField.equals(cellCaption)) {
                                    Dispatch column = getColumn(destinationSheet, c);
                                    Dispatch.put(column, "ColumnWidth", new Variant(columnWidth));
                                }
                            }
                            if (columnTotalWidth != null) {
                                Dispatch cell = getCell(destinationSheet, c, rowTotalIndex);
                                Variant cellVariant = getCellVariant(destinationSheet, c, rowTotalIndex);
                                String cellCaption = cellVariant.isNull() ? "" : cellVariant.getString();
                                if (cellCaption.equals("Итог " + captionField) || (cellFieldsEntry.size() == 1 && cellCaption.equals("Общий итог"))) {
                                    Dispatch column = getColumn(destinationSheet, c);
                                    Dispatch.put(column, "ColumnWidth", new Variant(columnTotalWidth));
                                    Dispatch.put(cell, "WrapText", new Variant(true));
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Dispatch.get(workbook, "Save");
        Dispatch.call(workbooks, "Close");
        excelComponent.invoke("Quit", new Variant[0]);
        ComThread.Release();

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(reportFile);
        }
    }

    private String getCellIndex(int column, int row) {
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String columnIndex = "";
        while (column > 0) {
            columnIndex = letters.charAt((column-1) % 26) + columnIndex;
            column = (column - 1) / 26;
        }
        return columnIndex + row;
    }

    private LinkedHashMap<Integer, Dispatch> getIndexFieldDispatchMap(Dispatch pivotTableWizard, Integer columnsCount) {
        LinkedHashMap<Integer, Dispatch> fieldDispatchMap = new LinkedHashMap<>();
        for (int i = 0; i<columnsCount;i++) {
            try {
                Dispatch fieldDispatch = Dispatch.call(pivotTableWizard, "HiddenFields", new Variant(i + 1)).toDispatch();
                fieldDispatchMap.put(firstColumn + i, fieldDispatch);
            } catch(Exception ignored) {                
            }
        }
        return fieldDispatchMap;
    }
    
    public LinkedHashMap<Integer, String> getFieldCaptionMap(Dispatch sheet, Integer columnsCount) {
        LinkedHashMap<Integer, String> fieldCaptionMap = new LinkedHashMap<>();
        for (int i = 0; i <= columnsCount; i++) {
            Variant cell = getCellVariant(sheet, i + 1, firstRow);
            if (!cell.isNull()) {
                String field = cell.getString();
                fieldCaptionMap.put(i + 1, field);
            }
        }
        return fieldCaptionMap;
    }

    public LinkedHashMap<Integer, Integer> getFieldsMap(Dispatch sheet, Integer columnsCount, Integer pivotTableNumber) {

        LinkedHashMap<String, List<Integer>> captionFieldsMap = new LinkedHashMap<>();
        for (int i = 0; i <= columnsCount; i++) {
            Variant cell = getCellVariant(sheet, i + 1, firstRow);
            if (!cell.isNull()) {
                String field = cell.getString();
                List<Integer> entry = captionFieldsMap.containsKey(field) ? captionFieldsMap.get(field) : new ArrayList<>();
                entry.add(i);
                captionFieldsMap.put(field, entry);
            }
        }
        
        LinkedHashMap<Integer, Integer> fieldsMap = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : captionFieldsMap.entrySet()) {                       
            for(Integer field : entry.getValue()) {
                if (listContainsField(rowFields.get(pivotTableNumber), entry.getKey())) {
                    fieldsMap.put(field, xlRowField);
                } else if (listContainsField(columnFields.get(pivotTableNumber), entry.getKey())) {
                    fieldsMap.put(field, xlColumnField);
                } else if ((listContainsField(filterFields.get(pivotTableNumber), entry.getKey()))) {
                    fieldsMap.put(field, xlFilterField);
                } else if ((listContainsField(cellFields.get(pivotTableNumber), entry.getKey()))) {
                    fieldsMap.put(field, xlDataField);
                } else fieldsMap.put(field, null);               
            }           
        }
        return fieldsMap;
    }
    
    private boolean listContainsField(List<List<Object>> list, String field) {
        if(list.isEmpty()) return false;
        boolean result = false;
        for(List<Object> entry : list) {
            if(entry.get(0) != null && entry.get(0).equals(field))
                result = true;
        }
        return result;
    }
    
    private String getResultFormula(List<List<Object>> cellFieldsEntry, String formula) {
        String resultFormula = "";
        Pattern pattern = Pattern.compile("(\\$?[\\d]+)?(\\+|\\-|\\*|\\/|\\(|\\)|IFERROR|,)?");
        Matcher matcher = pattern.matcher(formula);
        while (matcher.find()) {
            resultFormula += getFormulaCell(cellFieldsEntry, matcher.group(1), formula) + (matcher.group(2) == null ? "" : matcher.group(2));
        }
        if (resultFormula.isEmpty()) {
            throw new RuntimeException("Error Formula: " + formula);
        }
        return resultFormula;
    }

    private String getFormulaCell(List<List<Object>> cellFieldsEntry, String field, String formula) {
        try {
            if (field == null) return "";
            if (field.startsWith("$")) {
                List<Object> indexEntry = cellFieldsEntry.get(Integer.parseInt(field.replace("$", "")) - 1);
                return "'" + indexEntry.get(0) + "'";
            } else {
                return field;
            }
        } catch (Exception e) {
            throw new RuntimeException("Error Formula: " + formula, e);
        }
    }
    
    private Dispatch getColumn(Dispatch sheet, int index) {
        return Dispatch.invoke(sheet, "Columns", Dispatch.Get, new Object[]{index}, new int[1]).toDispatch();
    }
    
    private Variant getCellVariant(Dispatch sheet, int column, int row) {
        return Dispatch.get(Dispatch.invoke(sheet, "Range", Dispatch.Get, new Object[]{getCellIndex(column, row)}, new int[1]).toDispatch(), "Value");
    }
    
    private Dispatch getCell(Dispatch sheet, int column, int row) {
        return Dispatch.invoke(sheet, "Range", Dispatch.Get, new Object[]{getCellIndex(column, row)}, new int[1]).toDispatch();
    }
}
