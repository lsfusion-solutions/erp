package lsfusion.erp.utils;

import jasperapi.ReportGenerator;
import lsfusion.base.file.IOUtils;
import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;
import jasperapi.ReportGenerationData;
import net.sf.jasperreports.engine.JRException;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.DataConsolidateFunction;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.*;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.*;

import java.awt.*;
import java.io.*;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//Ждём новых версий apache.poi. В актуальной на данный момент 3.14 для сводных таблиц нет возможности задания формата ячеек, ширины колонок, имён колонок и столбцов.
public class ExportExcelXSSFPivotAction implements ClientAction {

    Integer firstRow = 1; //null based

    ReportGenerationData reportData;
    String title;
    Integer titleRowHeight;
    List<List<List<Object>>> rowFields;
    List<List<List<Object>>> columnFields;
    List<List<List<Object>>> filterFields;
    List<List<List<Object>>> cellFields;

    public ExportExcelXSSFPivotAction(ReportGenerationData reportData, String title, Integer titleRowHeight, List<List<List<Object>>> rowFields, List<List<List<Object>>> columnFields,
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
        if (rowFields.size() != columnFields.size() || columnFields.size() != filterFields.size() || filterFields.size() != cellFields.size())
            throw new RuntimeException("Некорректное количество параметров сводных таблиц");
        try {
            runExcelPivot();
        } catch (JRException | ClassNotFoundException e) {
            throw new RuntimeException("Ошибка при формировании сводной таблицы", e);
        }
        return null;
    }

    private void runExcelPivot() throws IOException, JRException, ClassNotFoundException {

        File sourceFile = null;
        try {
            sourceFile = ReportGenerator.exportToXlsx(reportData);
            byte[] sourceFileBytes = IOUtils.getFileBytes(sourceFile);
            File targetFile = copySheetsToNewFile(sourceFileBytes);

            XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(IOUtils.getFileBytes(targetFile)));
            XSSFSheet sourceSheet = workbook.getSheetAt(0);

            int pivotTableCount = rowFields.size();
            for (int i = pivotTableCount - 1; i >= 0; i--) {

                List<List<Object>> rowFieldsEntry = rowFields.get(i);
                List<List<Object>> columnFieldsEntry = columnFields.get(i);
                List<List<Object>> filterFieldsEntry = filterFields.get(i);
                List<List<Object>> cellFieldsEntry = cellFields.get(i);

                XSSFSheet destinationSheet = workbook.createSheet("PivotTable" + (i + 1));
                //workbook.setActiveSheet(1);

                Integer columnsCount = (int) sourceSheet.getRow(0).getLastCellNum();
                Integer rowsCount = sourceSheet.getLastRowNum();

                int j = 1;
                if (title != null) {
                    for (String titleString : title.split("\\\\n|\\n")) {
                        destinationSheet.createRow(j - 1).createCell(0).setCellValue(titleString);
                        j++;
                    }
                }

                if (rowsCount > 2) {
                    String lastCell = getCellIndex(columnsCount - 1, rowsCount == 0 ? 2 : rowsCount);
                    XSSFPivotTable pivotTable = destinationSheet.createPivotTable(new AreaReference("B2:" + lastCell, SpreadsheetVersion.EXCEL2007), new CellReference("A" + (j + 2 + filterFieldsEntry.size())), sourceSheet);

                    //map index -> header caption
                    LinkedHashMap<String, Integer> columnIndexMap = getFieldCaptionMap(sourceSheet, columnsCount);

                    //Выставляем rows
                    for (List<Object> rowEntry : rowFieldsEntry) {
                        String column = (String) rowEntry.get(0);
                        int index = columnIndexMap.get(column);
                        pivotTable.addRowLabel(index);
                    }

                    //Выставляем columns
                    for (List<Object> columnEntry : columnFieldsEntry) {
                        String column = (String) columnEntry.get(0);
                        int index = columnIndexMap.get(column);
                        addColumnLabel(pivotTable, index);
                    }

                    //Выставляем filters
                    for (List<Object> filterEntry : filterFieldsEntry) {
                        String column = (String) filterEntry.get(0);
                        int index = columnIndexMap.get(column);
                        pivotTable.addReportFilter(index);
                    }

                    //Выставляем values
                    for (List<Object> entry : cellFieldsEntry) {
                        String column = (String) entry.get(0);
                        String formula = (String) entry.get(1);
                        String caption = (String) entry.get(2);
                        //String numberFormat = (String) entry.get(3);
                        //Integer columnWidth = (Integer) entry.get(4);
                        //Integer columnTotalWidth = (Integer) entry.get(5);
                        Integer index = columnIndexMap.get(column);

                        caption = caption == null ? column : caption;

                        if (formula == null)
                            pivotTable.addColumnLabel(DataConsolidateFunction.SUM, index, caption);
                        else
                            addFormulaField(pivotTable, getResultFormula(cellFieldsEntry, formula), caption);

//                    if (numberFormat != null)
//                        Dispatch.put(field, "NumberFormat", new Variant(numberFormat));
//                    if (columnWidth != null)
//                        Dispatch.put(getColumn(destinationSheet, rowFieldsEntry.size() + fieldCount + 1), "ColumnWidth", new Variant(columnWidth));

                    }

                    //set Fit to page
                    //Dispatch.put(Dispatch.invoke(destinationSheet, "Rows", Dispatch.Get, new Object[] {firstRowIndex + columnFieldsEntry.size() + 1}, new int[1]).toDispatch(),
                    //        "RowHeight", new Variant((titleRowHeight == null ? 1 : titleRowHeight) * 15));
                    //Dispatch pageSetup = Dispatch.get(destinationSheet, "PageSetup").getDispatch();
                    //Dispatch.put(pageSetup, "Zoom", new Variant(false));
                    //Dispatch.put(pageSetup, "FitToPagesWide", new Variant(1));
                    //Dispatch.put(pageSetup, "FitToPagesTall", new Variant(false));

                    //Dispatch pivotUsedRange = Dispatch.get(destinationSheet, "UsedRange").toDispatch();
                    //Integer pivotColumnsCount = Dispatch.get(Dispatch.get(pivotUsedRange, "Columns").toDispatch(), "Count").getInt();

                    //set WrapText
                    //for(int c = 1; c <= pivotColumnsCount; c++) {
                    //    int row = j + columnFieldsEntry.size() + (filterFieldsEntry == null ? 0 : filterFieldsEntry.size()) + 2;
                    //    Dispatch cell = getCell(destinationSheet, c, row);
                    //    Dispatch.put(cell, "WrapText", new Variant(true));
                    //}

                    //count = 0;
                    //for (List<Object> entry : cellFieldsEntry) {
                    //    String fieldValue = (String) entry.get(0);
                    //    if (fieldValue != null) {
                    //        count++;
                    //    }
                    //}

                    //set column width
//                for (List<Object> entry : cellFieldsEntry) {
//                    String fieldValue = (String) entry.get(0);
//                    Integer columnWidth = (Integer) entry.get(4);
//                    Integer columnTotalWidth = (Integer) entry.get(5);
//                    if (fieldValue != null) {
//                        Dispatch field = cellDispatchFieldsMap.get(fieldValue);
//                        if (field == null)
//                            throw new RuntimeException("Set column width failed (field not found)");
//                        String captionField = Dispatch.get(field, "Caption").getString();
//                        int rowIndex = firstRowIndex + columnFieldsEntry.size() + 1;
//                        int rowTotalIndex = firstRowIndex + columnFieldsEntry.size();
//                        for (int c = 1; c <= pivotColumnsCount; c++) {
//                            if (columnWidth != null) {
//                                Variant cell = getCellVariant(destinationSheet, c, rowIndex);
//                                String cellCaption = cell.isNull() || cell.getvt() != 8 ? "" : cell.getString();
//                                if (captionField.equals(cellCaption)) {
//                                    Dispatch column = getColumn(destinationSheet, c);
//                                    Dispatch.put(column, "ColumnWidth", new Variant(columnWidth));
//                                }
//                            }
//                            if (columnTotalWidth != null) {
//                                Dispatch cell = getCell(destinationSheet, c, rowTotalIndex);
//                                Variant cellVariant = getCellVariant(destinationSheet, c, rowTotalIndex);
//                                String cellCaption = cellVariant.isNull() ? "" : cellVariant.getString();
//                                if (cellCaption.equals("Итог " + captionField) || (cellFieldsEntry.size() == 1 && cellCaption.equals("Общий итог"))) {
//                                    Dispatch column = getColumn(destinationSheet, c);
//                                    Dispatch.put(column, "ColumnWidth", new Variant(columnTotalWidth));
//                                    Dispatch.put(cell, "WrapText", new Variant(true));
//                                }
//                            }
//                        }
//                    }
//                }
                }
            }

            FileOutputStream fileOut = new FileOutputStream(targetFile);
            workbook.write(fileOut);
            fileOut.close();
            workbook.close();

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(targetFile);
            }
        } finally {
            if (sourceFile != null && !sourceFile.delete())
                sourceFile.deleteOnExit();
        }
    }

    public File copySheetsToNewFile(byte[] sourceFileBytes) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(sourceFileBytes));
        XSSFWorkbook sourceWorkbook = new XSSFWorkbook(bis);
        XSSFWorkbook targetWorkbook = new XSSFWorkbook();
        XSSFSheet sheet;
        XSSFRow row;
        XSSFCell cell;
        XSSFSheet mySheet;
        XSSFRow myRow;
        XSSFCell myCell;
        int sheets = sourceWorkbook.getNumberOfSheets();
        int fCell;
        int lCell;
        int fRow;
        int lRow;
        for (int iSheet = 0; iSheet < sheets; iSheet++) {
            sheet = sourceWorkbook.getSheetAt(iSheet);
            if (sheet != null) {
                mySheet = targetWorkbook.createSheet(sheet.getSheetName());
                fRow = sheet.getFirstRowNum();
                lRow = sheet.getLastRowNum();
                for (int iRow = fRow; iRow <= lRow; iRow++) {
                    row = sheet.getRow(iRow);
                    myRow = mySheet.createRow(iRow);
                    if (row != null) {
                        fCell = row.getFirstCellNum();
                        lCell = row.getLastCellNum();
                        for (int iCell = fCell; iCell < lCell; iCell++) {
                            cell = row.getCell(iCell);
                            myCell = myRow.createCell(iCell);
                            if (cell != null) {
                                myCell.setCellType(cell.getCellType());
                                switch (cell.getCellType()) {
                                    case XSSFCell.CELL_TYPE_BLANK:
                                        myCell.setCellValue("");
                                        break;

                                    case XSSFCell.CELL_TYPE_BOOLEAN:
                                        myCell.setCellValue(cell.getBooleanCellValue());
                                        break;

                                    case XSSFCell.CELL_TYPE_ERROR:
                                        myCell.setCellErrorValue(cell.getErrorCellValue());
                                        break;

                                    case XSSFCell.CELL_TYPE_FORMULA:
                                        myCell.setCellFormula(cell.getCellFormula());
                                        break;

                                    case XSSFCell.CELL_TYPE_NUMERIC:
                                        myCell.setCellValue(cell.getNumericCellValue());
                                        break;

                                    case XSSFCell.CELL_TYPE_STRING:
                                        myCell.setCellValue(cell.getStringCellValue());
                                        break;
                                    default:
                                        myCell.setCellFormula(cell.getCellFormula());
                                }
                            }
                        }
                    }
                }
            }
        }
        bis.close();
        File targetFile = File.createTempFile("ExportExcel", ".xlsx");
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(targetFile));
        targetWorkbook.write(bos);
        bos.close();
        return targetFile;
    }

    private String getCellIndex(int column, int row) {
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String columnIndex = "";
        while (column > 0) {
            columnIndex = letters.charAt((column - 1) % 26) + columnIndex;
            column = (column - 1) / 26;
        }
        return columnIndex + row;
    }

    public LinkedHashMap<String, Integer> getFieldCaptionMap(XSSFSheet sheet, Integer columnsCount) {
        LinkedHashMap<String, Integer> fieldCaptionMap = new LinkedHashMap<>();
        for (int i = 1; i < columnsCount; i++) {
            XSSFCell cell = getCellVariant(sheet, i, firstRow);
            String result;
            switch (cell.getCellType()) {
                case org.apache.poi.ss.usermodel.Cell.CELL_TYPE_NUMERIC:
                case org.apache.poi.ss.usermodel.Cell.CELL_TYPE_FORMULA:
                    try {
                        result = new DecimalFormat("#.#####").format(cell.getNumericCellValue());
                    } catch (Exception e) {
                        result = cell.getStringCellValue();
                    }
                    result = result.endsWith(".0") ? result.substring(0, result.length() - 2) : result;
                    break;
                case org.apache.poi.ss.usermodel.Cell.CELL_TYPE_STRING:
                default:
                    result = cell.getStringCellValue();
            }
            if (result != null) {
                fieldCaptionMap.put(result, i - 1);
            }
        }
        return fieldCaptionMap;
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

    private XSSFCell getCellVariant(XSSFSheet sheet, int column, int row) {
        return sheet.getRow(row).getCell(column);
    }

    //стандартного добавления колонки без аггрегирующей функции в apache.poi нет, поэтому добавляем таким странным образом
    private void addColumnLabel(XSSFPivotTable pivotTable, int columnIndex) {
        AreaReference pivotArea = new AreaReference(pivotTable.getPivotCacheDefinition().getCTPivotCacheDefinition()
                .getCacheSource().getWorksheetSource().getRef(), SpreadsheetVersion.EXCEL2007);
        int lastRowIndex = pivotArea.getLastCell().getRow() - pivotArea.getFirstCell().getRow();
        int lastColIndex = pivotArea.getLastCell().getCol() - pivotArea.getFirstCell().getCol();

        if (columnIndex > lastColIndex) {
            throw new IndexOutOfBoundsException();
        }
        CTPivotFields pivotFields = pivotTable.getCTPivotTableDefinition().getPivotFields();

        CTPivotField pivotField = CTPivotField.Factory.newInstance();
        CTItems items = pivotField.addNewItems();

        pivotField.setAxis(STAxis.AXIS_COL);
        pivotField.setShowAll(false);
        for (int i = 0; i <= lastRowIndex; i++) {
            items.addNewItem().setT(STItemType.DEFAULT);
        }
        items.setCount(items.sizeOfItemArray());
        pivotFields.setPivotFieldArray(columnIndex, pivotField);

        CTColFields rowFields;
        if (pivotTable.getCTPivotTableDefinition().getColFields() != null) {
            rowFields = pivotTable.getCTPivotTableDefinition().getColFields();
        } else {
            rowFields = pivotTable.getCTPivotTableDefinition().addNewColFields();
        }

        rowFields.addNewField().setX(columnIndex);
        rowFields.setCount(rowFields.sizeOfFieldArray());
    }

    //стандартного добавления формулы в apache.poi нет, поэтому добавляем таким сложным образом
    private void addFormulaField(XSSFPivotTable pivotTable, String resultFormula, String caption) {
        addFormulaToCache(pivotTable, resultFormula, caption);
        addPivotFieldForNewColumn(pivotTable);
        addFormulaColumn(pivotTable, caption);
    }

    private void addFormulaToCache(XSSFPivotTable pivotTable, String formula, String caption) {
        CTCacheFields ctCacheFields = pivotTable.getPivotCacheDefinition().getCTPivotCacheDefinition().getCacheFields();
        CTCacheField ctCacheField = ctCacheFields.addNewCacheField();
        ctCacheField.setName(caption); // Any field name
        ctCacheField.setFormula(formula);
        ctCacheField.setDatabaseField(false);
        ctCacheField.setNumFmtId(0);
        ctCacheFields.setCount(ctCacheFields.sizeOfCacheFieldArray()); //!!! update count of fields directly
    }

    private void addPivotFieldForNewColumn(XSSFPivotTable pivotTable) {
        CTPivotField pivotField = pivotTable.getCTPivotTableDefinition().getPivotFields().addNewPivotField();
        pivotField.setDataField(true);
        pivotField.setDragToCol(false);
        pivotField.setDragToPage(false);
        pivotField.setDragToRow(false);
        pivotField.setShowAll(false);
        pivotField.setDefaultSubtotal(false);
    }

    private void addFormulaColumn(XSSFPivotTable pivotTable, String caption) {
        CTDataFields dataFields;
        if (pivotTable.getCTPivotTableDefinition().getDataFields() != null) {
            dataFields = pivotTable.getCTPivotTableDefinition().getDataFields();
        } else {
            // can be null if we have not added any column labels yet
            dataFields = pivotTable.getCTPivotTableDefinition().addNewDataFields();
        }
        CTDataField dataField = dataFields.addNewDataField();
        dataField.setName(caption.replace(",", "")); //TODO: разобраться. По непонятной пока причине запятая в названии приводит к игнорированию поля вообще.
        // set index of cached field with formula - it is the last one!!!
        dataField.setFld(pivotTable.getPivotCacheDefinition().getCTPivotCacheDefinition().getCacheFields().getCount() - 1);
        dataField.setBaseItem(0);
        dataField.setBaseField(0);
    }
}
