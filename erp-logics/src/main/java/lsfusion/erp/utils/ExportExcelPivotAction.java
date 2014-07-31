package lsfusion.erp.utils;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.ComThread;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;
import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

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

    File reportFile;
    List<String> rowFields;
    List<String> columnFields;
    List<String> filterFields;
    List<String> cellFields;

    public ExportExcelPivotAction(File reportFile, List<String> rowFields, List<String> columnFields,
                                  List<String> filterFields, List<String> cellFields) {
        this.reportFile = reportFile;
        this.rowFields = rowFields;
        this.columnFields = columnFields;
        this.filterFields = filterFields;
        this.cellFields = cellFields;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {
        runExcelPivot();
        return null;
    }

    private void runExcelPivot() throws IOException {

        ActiveXComponent excelComponent = new ActiveXComponent("Excel.Application");

        Dispatch workbooks = excelComponent.getProperty("Workbooks").toDispatch();
        Dispatch workbook = Dispatch.call(workbooks, "Open", reportFile.getAbsolutePath()).toDispatch();

        Dispatch sourceSheet = Dispatch.get(workbook, "ActiveSheet").toDispatch();
        Dispatch sheets = Dispatch.get(workbook, "Worksheets").toDispatch();
        Dispatch destinationSheet = Dispatch.get(sheets, "Add").toDispatch();
        Dispatch.put(destinationSheet, "Name", "PivotTable");

        Dispatch usedRange = Dispatch.get(sourceSheet, "UsedRange").toDispatch();
        Integer rowsCount = Dispatch.get(Dispatch.get(usedRange, "Rows").toDispatch(), "Count").getInt();
        Integer columnsCount = Dispatch.get(Dispatch.get(usedRange, "Columns").toDispatch(), "Count").getInt();

        String lastCell = getCellIndex(columnsCount - 1, rowsCount == 0 ? 2 : (rowsCount + 1));
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

        LinkedHashMap<Integer, Integer> fields = getFields(getFieldsMap(sourceSheet, columnsCount));
        for (int i = fields.size(); i > 0; i--) {
            Integer orientation = fields.get(i);
            if(orientation != null) {
                Dispatch fieldDispatch = Dispatch.call(pivotTableWizard, "HiddenFields", new Variant(i)).toDispatch();
                Dispatch.put(fieldDispatch, "Orientation", new Variant(orientation));
                if(orientation.equals(xlDataField))
                    Dispatch.put(fieldDispatch, "Function", new Variant(xlSum));
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
            columnIndex = (column <=26 ? letters.charAt(column - 1) : letters.charAt(column % 26 - 1)) + columnIndex;
            column = column - 26;
        }
        return columnIndex + row;
    }

    public LinkedHashMap<String, Integer> getFieldsMap(Dispatch sheet, Integer columnsCount) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<String, Integer>();
        for (int i = columnsCount; i >= 0; i--) {
            Variant cell = Dispatch.get(Dispatch.invoke(sheet, "Range", Dispatch.Get, new Object[]{getCellIndex(i + 1, 2)}, new int[1]).toDispatch(), "Value");
            if (!cell.isNull())
                result.put(cell.getString(), i);
        }
        return result;
    }

    public LinkedHashMap<Integer, Integer> getFields(LinkedHashMap<String, Integer> fieldsMap) {
        LinkedHashMap<Integer, Integer> fields = new LinkedHashMap<Integer, Integer>();

        for (Map.Entry<String, Integer> entry : fieldsMap.entrySet()) {
            if (rowFields.contains(entry.getKey()))
                fields.put(entry.getValue(), xlRowField);
            else if (columnFields.contains(entry.getKey()))
                fields.put(entry.getValue(), xlColumnField);
            else if (filterFields.contains(entry.getKey()))
                fields.put(entry.getValue(), xlFilterField);
            else if (cellFields.contains(entry.getKey()))
                fields.put(entry.getValue(), xlDataField);
            else fields.put(entry.getValue(), null);
        }
        return fields;
    }
}
