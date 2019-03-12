package lsfusion.erp.utils;

import com.google.common.base.Throwables;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import jasperapi.ReportGenerationData;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.instance.FormInstance;
import lsfusion.server.form.view.PropertyDrawView;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.form.stat.InteractiveFormReportManager;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

//Ждём новых версий apache.poi, так как в 3.17 для сводных таблиц нет возможности задания формата ячеек, ширины колонок, имён колонок и столбцов.

// Syntax:
// xxx=yyy[zzz]~n/m-
// xxx is formula 
// yyy is caption (optional)
// zzz is number format (optional)
// n is column width (optional)
// m is width for column total (optional)
// - disables subtotals for rows (optional)
//IFERROR(xxx, a)
// xxx is formula
// a is default value
public abstract class ExportExcelXSSFPivotActionProperty extends ScriptingActionProperty {
    String idForm;
    String titleProperty;
    Integer titleRowHeight;
    String idGroupObject;
    List<List<String>> rows;
    List<List<String>> columns;
    List<List<String>> filters;
    List<List<String>> cells;

    public ExportExcelXSSFPivotActionProperty(ScriptingLogicsModule LM, String idForm, String idGroupObject,
                                              List<String> rows, List<String> columns, List<String> filters, List<String> cells,
                                              ValueClass... classes) {
        this(LM, Arrays.asList(rows), Arrays.asList(columns), Arrays.asList(filters), Arrays.asList(cells), idForm, null, null, idGroupObject, classes);
    }

    public ExportExcelXSSFPivotActionProperty(ScriptingLogicsModule LM, String idForm, Integer titleRowHeight, String idGroupObject,
                                              List<String> rows, List<String> columns, List<String> filters, List<String> cells,
                                              ValueClass... classes) {
        this(LM, Arrays.asList(rows), Arrays.asList(columns), Arrays.asList(filters), Arrays.asList(cells), idForm, null, titleRowHeight, idGroupObject, classes);
    }

    public ExportExcelXSSFPivotActionProperty(ScriptingLogicsModule LM, String idForm, String titleProperty, Integer titleRowHeight, String idGroupObject,
                                              List<String> rows, List<String> columns, List<String> filters, List<String> cells,
                                              ValueClass... classes) {
        this(LM, Arrays.asList(rows), Arrays.asList(columns), Arrays.asList(filters), Arrays.asList(cells), idForm, titleProperty, titleRowHeight, idGroupObject, classes);
    }

    public ExportExcelXSSFPivotActionProperty(ScriptingLogicsModule LM, String idForm, String titleProperty, String idGroupObject,
                                              List<String> rows, List<String> columns, List<String> filters, List<String> cells,
                                              ValueClass... classes) {
        this(LM, Arrays.asList(rows), Arrays.asList(columns), Arrays.asList(filters), Arrays.asList(cells), idForm, titleProperty, null, idGroupObject, classes);
    }

    public ExportExcelXSSFPivotActionProperty(ScriptingLogicsModule LM,
                                              List<List<String>> rows, List<List<String>> columns, List<List<String>> filters, List<List<String>> cells,
                                              String idForm, String titleProperty, Integer titleRowHeight, String idGroupObject, ValueClass... classes) {
        super(LM, classes);
        this.idForm = idForm;
        this.titleProperty = titleProperty;
        this.titleRowHeight = titleRowHeight;
        this.idGroupObject = idGroupObject;
        this.rows = rows;
        this.columns = columns;
        this.filters = filters;
        this.cells = cells;
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context, Map<String, DataObject> valuesMap) throws SQLException, SQLHandledException {

        try {

            if (idForm != null && idGroupObject != null) {

                FormEntity formEntity = findForm(idForm);
                FormInstance formInstance = context.createFormInstance(formEntity);
                ImOrderSet<PropertyDrawView> properties = formEntity.getRichDesign().getPropertiesList();

                if (valuesMap != null)
                    for (Map.Entry<String, DataObject> entry : valuesMap.entrySet())
                        formInstance.forceChangeObject(formInstance.instanceFactory.getInstance(LM.getObjectEntityByName(formEntity, entry.getKey())), entry.getValue());


                ReportGenerationData reportData = new InteractiveFormReportManager(formInstance).getReportData(
                        formEntity.getGroupObject(idGroupObject).getID(), true, formInstance.loadUserPreferences());

                context.requestUserInteraction(new ExportExcelXSSFPivotAction(reportData, readTitle(context, valuesMap, titleProperty), titleRowHeight,
                        readFieldCaptions(properties, rows), readFieldCaptions(properties, columns), readFieldCaptions(properties, filters), readFieldCaptions(properties, cells)));
            }

        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }

    //по id свойств получаем заголовки колонок
    public List<List<List<Object>>> readFieldCaptions(ImOrderSet<PropertyDrawView> properties, List<List<String>> fields) throws ScriptingErrorLog.SemanticErrorException {
        List<List<List<Object>>> result = new ArrayList<>();
        if (fields != null) {
            for (List<String> fieldsEntry : fields) {
                List<List<Object>> resultEntry = new ArrayList<>();
                for (String field : fieldsEntry) {

                    //column width
                    Integer columnWidth = null;
                    Integer columnTotalWidth = null;
                    if (field.matches("(.*)~(.*)")) {
                        String[] splittedEntry = field.split("~");
                        try {
                            if(splittedEntry[1].contains("/")) {
                                columnWidth = Integer.parseInt(splittedEntry[1].split("/")[0]);
                                columnTotalWidth = Integer.parseInt(splittedEntry[1].split("/")[1]);
                            } else {
                                columnWidth = Integer.parseInt(splittedEntry[1]);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException("Invalid Formula: " + field);
                        }
                        field = splittedEntry[0];
                    }
                    
                    //number format
                    String numberFormat = null;
                    if (field.matches("(.*)\\[(.*)\\]")) {
                        String[] splittedEntry = field.split("\\[|\\]");
                        field = splittedEntry[0];
                        numberFormat = splittedEntry[1];
                    }

                    //caption
                    String caption = null;
                    if (field.matches(".*=.*")) {
                        String[] splittedEntry = field.split("=");
                        field = splittedEntry[0];
                        caption = splittedEntry[1];
                    }

                    String fieldValue = null;
                    for (PropertyDrawView property : properties) {
                        if (property.entity.getSID().equals(field)) {
                            fieldValue = property.getCaption().toString();
                            break;
                        }
                    }                    
                    String formula = fieldValue == null ? field : null;
                    resultEntry.add(Arrays.asList((Object) (fieldValue == null ? field : fieldValue), formula, caption, numberFormat, columnWidth, columnTotalWidth));
                }
                result.add(resultEntry);
            }
        }
        return result;
    }

    public String readTitle(ExecutionContext context, Map<String, DataObject> valuesMap, String idTitle) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String result = null;
        if (idTitle != null) {
            
            String[] splittedTitle = idTitle.split("\\(|\\)");
            String property = splittedTitle[0];
            String params = splittedTitle.length > 1 ? splittedTitle[1] : null;
            List<DataObject> objects = new ArrayList<>();
            if(params != null) {
                for(String param : params.split(",")) {
                    if(valuesMap.containsKey(param))
                        objects.add(valuesMap.get(param));
                }
            }          
            if(objects.isEmpty())
                result = (String) findProperty(property).read(context);
            else
                result = (String) findProperty(property).read(context, objects.toArray(new DataObject[objects.size()]));
            
        }
        return result;
    }
}

//Example of implementation ExportExcelPivotActionProperty

//package lsfusion.erp.utils;
//
//import lsfusion.server.classes.DateClass;
//import lsfusion.server.classes.ValueClass;
//import lsfusion.server.data.SQLHandledException;
//import lsfusion.server.logics.DataObject;
//import lsfusion.server.logics.property.ClassPropertyInterface;
//import lsfusion.server.logics.property.ExecutionContext;
//import lsfusion.server.logics.scripted.ScriptingLogicsModule;
//
//import java.sql.SQLException;
//import java.util.Arrays;
//import java.util.HashMap;
//import java.util.Iterator;
//import java.util.Map;
//
//public class TestFormExportExcelPivotActionProperty extends ExportExcelPivotActionProperty {
//    private final ClassPropertyInterface dateFromInterface;
//    private final ClassPropertyInterface dateToInterface;
//
//    public TestFormExportExcelPivotActionProperty(ScriptingLogicsModule LM) {
//        super(LM, "testForm", "i",
//                Arrays.asList("Purchase.nameCustomerStockInvoice", "Purchase.nameSupplierStockInvoice"),
//                Arrays.asList("Purchase.dateInvoice"),
//                Arrays.asList("Purchase.timeInvoice"),
//                Arrays.asList("Purchase.sumInvoiceDetailInvoice", "Purchase.VATSumInvoiceDetailInvoice"),
//                DateClass.instance, DateClass.instance);
//
//        Iterator<ClassPropertyInterface> i = interfaces.iterator();
//        dateFromInterface = i.next();
//        dateToInterface = i.next();
//    }
//
//
//    @Override
//    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
//
//        DataObject dateFromObject = context.getDataKeyValue(dateFromInterface);
//        DataObject dateToObject = context.getDataKeyValue(dateToInterface);
//
//        Map<String, DataObject> valuesMap = new HashMap<String, DataObject>();
//        valuesMap.put("dFrom", dateFromObject);
//        valuesMap.put("dTo", dateToObject);
//        super.executeCustom(context, valuesMap);
//    }
//}