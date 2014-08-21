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

            int dataCount = 0;
            int hiddenCount = 0;
            int filterCount = 0;
            //поля-фильтры обрабатываем в конце для сохранения заданного порядка
            List<Dispatch> filterFields = new ArrayList<Dispatch>();
            LinkedHashMap<Integer, Integer> fields = getFields(getFieldsMap(sourceSheet, columnsCount));
            for (Map.Entry<Integer, Integer> entry : fields.entrySet()) {
                Integer orientation = entry.getValue();
                if (orientation != null) {
                    Dispatch fieldDispatch = Dispatch.call(pivotTableWizard, "HiddenFields", new Variant(1 + hiddenCount + filterCount/*entry.getKey()*/)).toDispatch();
                    if(orientation.equals(xlFilterField)) {
                        filterCount++;    
                        filterFields.add(fieldDispatch);
                    } else {
                        Dispatch.put(fieldDispatch, "Orientation", new Variant(orientation));
                        if (orientation.equals(xlDataField)) {
                            dataCount++;
                            Dispatch.put(fieldDispatch, "Function", new Variant(xlSum));
                            String caption = Dispatch.get(fieldDispatch, "Caption").getString().replace("Сумма по полю ", "");
                            Dispatch.put(fieldDispatch, "Caption", new Variant(caption + "*"));
                        }
                    }
                } else hiddenCount++;
            } 
            
            for(Dispatch filter : Lists.reverse(filterFields)) {
                Dispatch.put(filter, "Orientation", new Variant(xlFilterField));
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

    public LinkedHashMap<String, List<Integer>> getFieldsMap(Dispatch sheet, Integer columnsCount) {
        LinkedHashMap<String, List<Integer>> fieldsMap = new LinkedHashMap<String, List<Integer>>();
        for (int i = 0; i <= columnsCount; i++) {
            Variant cell = Dispatch.get(Dispatch.invoke(sheet, "Range", Dispatch.Get, new Object[]{getCellIndex(i + 1, 2)}, new int[1]).toDispatch(), "Value");
            if (!cell.isNull()) {
                String field = cell.getString();
                List<Integer> entry = fieldsMap.containsKey(field) ? fieldsMap.get(field) : new ArrayList<Integer>();
                entry.add(i);
                fieldsMap.put(field, entry);
            }
        }
        return fieldsMap;
    }

    public LinkedHashMap<Integer, Integer> getFields(LinkedHashMap<String, List<Integer>> fieldsMap) {
        LinkedHashMap<Integer, Integer> fields = new LinkedHashMap<Integer, Integer>();

        for (Map.Entry<String, List<Integer>> entry : fieldsMap.entrySet()) {
                       
            for(Integer field : entry.getValue()) {

                if (rowFields.contains(entry.getKey())) {
                    fields.put(field, xlRowField);
                    rowFields.remove(entry.getKey());
                } else if (columnFields.contains(entry.getKey())) {
                    fields.put(field, xlColumnField);
                    columnFields.remove(entry.getKey());
                } else if (filterFields.contains(entry.getKey())) {
                    fields.put(field, xlFilterField);
                    filterFields.remove(entry.getKey());
                } else if (cellFields.contains(entry.getKey())) {
                    fields.put(field, xlDataField);
                    cellFields.remove(entry.getKey());
                } else fields.put(field, null);
                
            }
            
        }
        return fields;
    }
}
