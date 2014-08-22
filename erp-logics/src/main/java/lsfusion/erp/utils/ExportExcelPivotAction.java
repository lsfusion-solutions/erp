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


public class ExportExcelPivotAction implements ClientAction {

    Integer xlRowField = 1;
    Integer xlColumnField = 2;
    Integer xlFilterField = 3;
    Integer xlDataField = 4;
    
    Integer xlSum = -4157;
    Integer xlCount = -4112;
    Integer xlAverage = -4106;

    ReportGenerationData reportData;
    List<String> rowFields;
    List<String> columnFields;
    List<String> filterFields;
    List<String> cellFields;

    public ExportExcelPivotAction(ReportGenerationData reportData, List<String> rowFields, List<String> columnFields,
                                  List<String> filterFields, List<String> cellFields) {
        this.reportData = reportData;
        this.rowFields = rowFields;
        this.columnFields = columnFields;
        this.filterFields = filterFields;
        this.cellFields = cellFields;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {
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
        Dispatch destinationSheet = Dispatch.get(sheets, "Add").toDispatch();
        Dispatch.put(destinationSheet, "Name", "PivotTable");

        Dispatch usedRange = Dispatch.get(sourceSheet, "UsedRange").toDispatch();
        Integer rowsCount = Dispatch.get(Dispatch.get(usedRange, "Rows").toDispatch(), "Count").getInt();
        Integer columnsCount = Dispatch.get(Dispatch.get(usedRange, "Columns").toDispatch(), "Count").getInt();

        if (rowsCount > 2) {
            String lastCell = getCellIndex(columnsCount - 1, rowsCount == 0 ? 2 : (rowsCount - 1));
            Dispatch sourceDataNativePeer = Dispatch.invoke(sourceSheet, "Range", Dispatch.Get, new Object[]{"B2:" + lastCell}, new int[1]).toDispatch();
            Dispatch destinationNativePeer = Dispatch.invoke(destinationSheet, "Range", Dispatch.Get, new Object[]{"A1"}, new int[1]).toDispatch();

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

            int count = 1;
            LinkedHashMap<Integer, String> fieldCaptionMap = getFieldCaptionMap(sourceSheet, columnsCount);

            LinkedHashMap<Integer, Integer> fieldsMap = getFieldsMap(sourceSheet, columnsCount);

            LinkedHashMap<String, Dispatch> rowDispatchFieldsMap = new LinkedHashMap<String, Dispatch>();
            LinkedHashMap<String, Dispatch> columnDispatchFieldsMap = new LinkedHashMap<String, Dispatch>();
            LinkedHashMap<String, Dispatch> filterDispatchFieldsMap = new LinkedHashMap<String, Dispatch>();
            LinkedHashMap<String, Dispatch> cellDispatchFieldsMap = new LinkedHashMap<String, Dispatch>();

            for (Map.Entry<Integer, Integer> entry : fieldsMap.entrySet()) {
                Integer orientation = entry.getValue();
                if (orientation != null) {
                    Dispatch fieldDispatch = Dispatch.call(pivotTableWizard, "HiddenFields", new Variant(count)).toDispatch();
                    if(orientation.equals(xlRowField)) {
                        rowDispatchFieldsMap.put(fieldCaptionMap.get(count), fieldDispatch);
                    } else  if(orientation.equals(xlColumnField)) {
                        columnDispatchFieldsMap.put(fieldCaptionMap.get(count), fieldDispatch);
                    } else if(orientation.equals(xlFilterField)) {
                        filterDispatchFieldsMap.put(fieldCaptionMap.get(count), fieldDispatch);
                    }  else if(orientation.equals(xlDataField)) {
                        cellDispatchFieldsMap.put(fieldCaptionMap.get(count), fieldDispatch);
                    }
                }
                count++;
            }


            for(String entry : rowFields) {
                Dispatch field = rowDispatchFieldsMap.get(entry);
                if(field != null)
                Dispatch.put(rowDispatchFieldsMap.get(entry), "Orientation", new Variant(xlRowField));
            }

            for(String entry : columnFields) {
                Dispatch field = columnDispatchFieldsMap.get(entry);
                if(field != null)
                    Dispatch.put(field, "Orientation", new Variant(xlColumnField));
            }

            //фильтры по какой-то причине требуют обратного порядка
            for(String entry : Lists.reverse(filterFields)) {
                Dispatch field = filterDispatchFieldsMap.get(entry);
                if(field != null)
                    Dispatch.put(field, "Orientation", new Variant(xlFilterField));
            }
            
            int dataCount = 0;
            for(String entry : cellFields) {
                Dispatch field = cellDispatchFieldsMap.get(entry);
                if(field != null) {
                    dataCount++;
                    Dispatch.put(field, "Orientation", new Variant(xlDataField));
                    Dispatch.put(field, "Function", new Variant(xlSum));
                    String caption = Dispatch.get(field, "Caption").getString().replace("Сумма по полю ", "");
                    Dispatch.put(field, "Caption", new Variant(caption + "*"));
                }
            }                    

            Dispatch field = Dispatch.get(pivotTableWizard, "DataPivotField").toDispatch();
            if (dataCount > 1)
                Dispatch.put(field, "Orientation", new Variant(xlColumnField));
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

    public LinkedHashMap<Integer, String> getFieldCaptionMap(Dispatch sheet, Integer columnsCount) {
        LinkedHashMap<Integer, String> fieldCaptionMap = new LinkedHashMap<Integer, String>();
        for (int i = 0; i <= columnsCount; i++) {
            Variant cell = Dispatch.get(Dispatch.invoke(sheet, "Range", Dispatch.Get, new Object[]{getCellIndex(i + 1, 2)}, new int[1]).toDispatch(), "Value");
            if (!cell.isNull()) {
                String field = cell.getString();
                fieldCaptionMap.put(i, field);
            }
        }
        return fieldCaptionMap;
    }

    public LinkedHashMap<Integer, Integer> getFieldsMap(Dispatch sheet, Integer columnsCount) {

        LinkedHashMap<String, List<Integer>> captionFieldsMap = new LinkedHashMap<String, List<Integer>>();
        for (int i = 0; i <= columnsCount; i++) {
            Variant cell = Dispatch.get(Dispatch.invoke(sheet, "Range", Dispatch.Get, new Object[]{getCellIndex(i + 1, 2)}, new int[1]).toDispatch(), "Value");
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
                if (rowFields.contains(entry.getKey())) {
                    fieldsMap.put(field, xlRowField);
                } else if (columnFields.contains(entry.getKey())) {
                    fieldsMap.put(field, xlColumnField);
                } else if (filterFields.contains(entry.getKey())) {
                    fieldsMap.put(field, xlFilterField);
                } else if (cellFields.contains(entry.getKey())) {
                    fieldsMap.put(field, xlDataField);
                } else fieldsMap.put(field, null);               
            }           
        }
        return fieldsMap;
    }
}
