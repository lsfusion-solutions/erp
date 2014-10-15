package lsfusion.erp.utils;

import com.google.common.base.Throwables;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.interop.form.ReportGenerationData;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.entity.ObjectEntity;
import lsfusion.server.form.instance.FormInstance;
import lsfusion.server.form.instance.FormSessionScope;
import lsfusion.server.form.view.PropertyDrawView;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.remote.FormReportManager;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public abstract class ExportExcelPivotActionProperty extends ScriptingActionProperty {
    String idForm;
    String idGroupObject;
    List<List<String>> rows;
    List<List<String>> columns;
    List<List<String>> filters;
    List<List<String>> cells;

    public ExportExcelPivotActionProperty(ScriptingLogicsModule LM, String idForm, String idGroupObject,
                                          List<String> rows, List<String> columns, List<String> filters, List<String> cells,
                                          ValueClass... classes) {
        this(LM, Arrays.asList(rows), Arrays.asList(columns), Arrays.asList(filters), Arrays.asList(cells), idForm, idGroupObject, classes);
    }
    
    public ExportExcelPivotActionProperty(ScriptingLogicsModule LM, 
                                          List<List<String>> rows, List<List<String>> columns, List<List<String>> filters, List<List<String>> cells,
                                          String idForm, String idGroupObject, ValueClass... classes) {
        super(LM, classes);
        this.idForm = idForm;
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
                FormInstance formInstance = context.createFormInstance(formEntity, MapFact.<ObjectEntity, DataObject>EMPTY(),
                        context.getSession(), true, FormSessionScope.OLDSESSION, false, false, false, null);
                ImOrderSet<PropertyDrawView> properties = formEntity.getRichDesign().getPropertiesList();

                if (valuesMap != null)
                    for (Map.Entry<String, DataObject> entry : valuesMap.entrySet())
                        formInstance.forceChangeObject(formInstance.instanceFactory.getInstance(LM.getObjectEntityByName(formEntity, entry.getKey())), entry.getValue());


                ReportGenerationData reportData = new FormReportManager(formInstance).getReportData(
                        formEntity.getGroupObject(idGroupObject).getID(), true, formInstance.loadUserPreferences());

                context.requestUserInteraction(new ExportExcelPivotAction(reportData,
                        readFieldCaptions(properties, rows), readFieldCaptions(properties, columns), readFieldCaptions(properties, filters), readFieldCaptions(properties, cells)));

            }

        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }

    public List<List<String>> readFieldCaptions(ImOrderSet<PropertyDrawView> properties, List<List<String>> fields) throws ScriptingErrorLog.SemanticErrorException {
        List<List<String>> result = new ArrayList<List<String>>();
        if (fields != null) {
            for (List<String> fieldsEntry : fields) {
                List<String> resultEntry = new ArrayList<String>();
                for (String field : fieldsEntry) {
                    if (field.matches(".*=.*"))
                        resultEntry.add(field);
                    else
                        for (PropertyDrawView property : properties) {
                            if (property.getSID().equals(field)) {
                                resultEntry.add(property.getCaption());
                                break;
                            }
                        }
                }
                result.add(resultEntry);
            }
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