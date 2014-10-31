package lsfusion.erp.utils;

import com.google.common.collect.Lists;
import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.ComThread;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;
import jasperapi.ReportGenerator;
import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;
import lsfusion.interop.form.ReportGenerationData;
import net.sf.jasperreports.engine.JRException;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ExportExcelPivotAction implements ClientAction {

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
    List<List<String>> rowFields;
    List<List<String>> columnFields;
    List<List<String>> filterFields;
    List<List<String>> cellFields;

    public ExportExcelPivotAction(ReportGenerationData reportData, String title, List<List<String>> rowFields, List<List<String>> columnFields,
                                  List<List<String>> filterFields, List<List<String>> cellFields) {
        this.reportData = reportData;
        this.title = title;
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
        } catch (JRException e) {
            throw new RuntimeException("Ошибка при формировании сводной таблицы", e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Ошибка при формировании сводной таблицы", e);
        }
        return null;
    }

    private void runExcelPivot() throws IOException, JRException, ClassNotFoundException {

        ActiveXComponent excelComponent = new ActiveXComponent("Excel.Application");

        File reportFile = ReportGenerator.exportToExcel(reportData);
        Dispatch workbooks = excelComponent.getProperty("Workbooks").toDispatch();
        Dispatch workbook = Dispatch.call(workbooks, "Open", reportFile.getAbsolutePath()).toDispatch();

        Dispatch sourceSheet = Dispatch.get(workbook, "ActiveSheet").toDispatch();
        Dispatch sheets = Dispatch.get(workbook, "Worksheets").toDispatch();

        int pivotTableCount = rowFields.size();
        for (int i = pivotTableCount - 1; i >= 0; i--) {
            
            List<String> rowFieldsEntry = rowFields.get(i);
            List<String> columnFieldsEntry = columnFields.get(i);
            List<String> filterFieldsEntry = filterFields.get(i);
            List<String> cellFieldsEntry = cellFields.get(i);
            
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
                Dispatch sourceDataNativePeer = Dispatch.invoke(sourceSheet, "Range", Dispatch.Get, new Object[]{"B2:" + lastCell}, new int[1]).toDispatch();
                String destinationIndex = "A" + (j + (filterFieldsEntry == null ? 0 : filterFieldsEntry.size()) + 1);
                Dispatch destinationNativePeer = Dispatch.invoke(destinationSheet, "Range", Dispatch.Get, new Object[]{destinationIndex}, new int[1]).toDispatch();

                Variant unspecified = Variant.VT_MISSING;
                Dispatch pivotTableWizard = Dispatch.invoke(workbook, "PivotTableWizard", Dispatch.Get, new Object[]{new Variant(1),  //SourceType
                        new Variant(sourceDataNativePeer), //SourceData
                        new Variant(destinationNativePeer), //TableDestination
                        new Variant("PivotTable"), //TableName
                        new Variant(false), //RowGrand
                        new Variant(false), //ColumnGrand
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

                LinkedHashMap<String, Dispatch> rowDispatchFieldsMap = new LinkedHashMap<String, Dispatch>();
                LinkedHashMap<String, Dispatch> columnDispatchFieldsMap = new LinkedHashMap<String, Dispatch>();
                LinkedHashMap<String, Dispatch> filterDispatchFieldsMap = new LinkedHashMap<String, Dispatch>();
                LinkedHashMap<String, Dispatch> cellDispatchFieldsMap = new LinkedHashMap<String, Dispatch>();


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

                for (String entry : rowFieldsEntry) {
                    Dispatch field = rowDispatchFieldsMap.get(entry);
                    if (field != null)
                        Dispatch.put(rowDispatchFieldsMap.get(entry), "Orientation", new Variant(xlRowField));
                }

                for (String entry : columnFieldsEntry) {
                    Dispatch field = columnDispatchFieldsMap.get(entry);
                    if (field != null)
                        Dispatch.put(field, "Orientation", new Variant(xlColumnField));
                }

                //фильтры по какой-то причине требуют обратного порядка
                for (String entry : Lists.reverse(filterFieldsEntry)) {
                    Dispatch field = filterDispatchFieldsMap.get(entry);
                    if (field != null)
                        Dispatch.put(field, "Orientation", new Variant(xlFilterField));
                }

                int dataCount = 0;
                for (String entry : cellFieldsEntry) {

                    if (entry != null && entry.matches(".*=.*")) {
                        String[] splittedEntry = entry.split("=");
                        String formula = splittedEntry[0];
                        String caption = splittedEntry[1];

                        String resultFormula = "";
                        Pattern pattern = Pattern.compile("(\\$?[\\d]+)?(\\+|\\-|\\*|\\/|\\(|\\)|%)?");
                        Matcher matcher = pattern.matcher(formula);
                        while(matcher.find()) {
                            resultFormula += getFormulaCell(cellFieldsEntry, matcher.group(1)) + (matcher.group(2) == null ? "" : matcher.group(2));
                        } 
                        if(resultFormula.isEmpty()) {
                            throw new RuntimeException("Error Formula: " + formula);
                        } 
                        boolean percent = false;
                        if(resultFormula.endsWith("%")) {
                            resultFormula = resultFormula.substring(0, resultFormula.length() - 1);
                            percent = true;
                        }
                        Dispatch calculatedFields = Dispatch.call(pivotTableWizard, "CalculatedFields").toDispatch();
                        Dispatch field = Dispatch.call(calculatedFields, "Add", caption, resultFormula, true).toDispatch();
                        Dispatch.put(field, "Orientation", new Variant(xlDataField));
                        caption = Dispatch.get(field, "Caption").getString().replace("Сумма по полю ", "");
                        Dispatch.put(field, "Caption", new Variant(caption + "*"));
                        if(percent)
                            Dispatch.put(field, "NumberFormat", new Variant("0.00%"));
                    } else {

                        Dispatch field = cellDispatchFieldsMap.get(entry);
                        if (field != null) {
                            dataCount++;
                            Dispatch.put(field, "Orientation", new Variant(xlDataField));
                            Dispatch.put(field, "Function", new Variant(xlSum));
                            String caption = Dispatch.get(field, "Caption").getString().replace("Сумма по полю ", "");
                            Dispatch.put(field, "Caption", new Variant(caption + "*"));
                        }
                    }
                }

                if (i == pivotTableCount - 1) {
                    Dispatch field = Dispatch.get(pivotTableWizard, "DataPivotField").toDispatch();
                    if (dataCount > 1)
                        Dispatch.put(field, "Orientation", new Variant(xlColumnField));
                }

                //По какой-то причине ColumnGrand и RowGrand работают ровно наоборот
                Dispatch.put(pivotTableWizard, "ColumnGrand", new Variant(true));
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
        LinkedHashMap<Integer, Dispatch> fieldDispatchMap = new LinkedHashMap<Integer, Dispatch>();
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
        LinkedHashMap<Integer, String> fieldCaptionMap = new LinkedHashMap<Integer, String>();
        for (int i = 0; i <= columnsCount; i++) {
            Variant cell = Dispatch.get(Dispatch.invoke(sheet, "Range", Dispatch.Get, new Object[]{getCellIndex(i + 1, firstRow)}, new int[1]).toDispatch(), "Value");
            if (!cell.isNull()) {
                String field = cell.getString();
                fieldCaptionMap.put(i + 1, field);
            }
        }
        return fieldCaptionMap;
    }

    public LinkedHashMap<Integer, Integer> getFieldsMap(Dispatch sheet, Integer columnsCount, Integer pivotTableNumber) {

        LinkedHashMap<String, List<Integer>> captionFieldsMap = new LinkedHashMap<String, List<Integer>>();
        for (int i = 0; i <= columnsCount; i++) {
            Variant cell = Dispatch.get(Dispatch.invoke(sheet, "Range", Dispatch.Get, new Object[]{getCellIndex(i + 1, firstRow)}, new int[1]).toDispatch(), "Value");
            if (!cell.isNull()) {
                String field = cell.getString();
                List<Integer> entry = captionFieldsMap.containsKey(field) ? captionFieldsMap.get(field) : new ArrayList<Integer>();
                entry.add(i);
                captionFieldsMap.put(field, entry);
            }
        }
        
        LinkedHashMap<Integer, Integer> fieldsMap = new LinkedHashMap<Integer, Integer>();
        for (Map.Entry<String, List<Integer>> entry : captionFieldsMap.entrySet()) {                       
            for(Integer field : entry.getValue()) {
                if (rowFields.get(pivotTableNumber).contains(entry.getKey())) {
                    fieldsMap.put(field, xlRowField);
                } else if (columnFields.get(pivotTableNumber).contains(entry.getKey())) {
                    fieldsMap.put(field, xlColumnField);
                } else if (filterFields.get(pivotTableNumber).contains(entry.getKey())) {
                    fieldsMap.put(field, xlFilterField);
                } else if (cellFields.get(pivotTableNumber).contains(entry.getKey())) {
                    fieldsMap.put(field, xlDataField);
                } else fieldsMap.put(field, null);               
            }           
        }
        return fieldsMap;
    }
    
    private String getFormulaCell(List<String> cellFieldsEntry, String field) {
        return field== null ? "" : 
                (field.startsWith("$") ? ("'" + cellFieldsEntry.get(Integer.parseInt(field.replace("$", "").replace("%", "")) - 1) + "'") : field);
    }
}
