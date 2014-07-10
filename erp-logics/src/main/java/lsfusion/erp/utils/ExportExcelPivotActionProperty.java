package lsfusion.erp.utils;

import com.google.common.base.Throwables;
import jasperapi.ReportGenerator;
import lsfusion.base.col.MapFact;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.entity.ObjectEntity;
import lsfusion.server.form.instance.FormInstance;
import lsfusion.server.form.instance.FormSessionScope;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.mutables.Version;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.remote.FormReportManager;
import net.sf.jasperreports.engine.JRException;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class ExportExcelPivotActionProperty extends ScriptingActionProperty {
    String idForm;
    String idGroupObject;
    List<String> rows;
    List<String> columns;
    List<String> filters;
    List<String> cells;

    public ExportExcelPivotActionProperty(ScriptingLogicsModule LM, String idForm, String idGroupObject,
                                          List<String> rows, List<String> columns, List<String> filters, List<String> cells,
                                          ValueClass... classes) {
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

                if (valuesMap != null)
                    for (Map.Entry<String, DataObject> entry : valuesMap.entrySet())
                        formInstance.forceChangeObject(formInstance.instanceFactory.getInstance(LM.getObjectEntityByName(formEntity, entry.getKey())), entry.getValue());


                File file = ReportGenerator.exportToExcel(new FormReportManager(formInstance).getReportData(
                        formEntity.getNFGroupObject(idGroupObject, Version.CURRENT).getID(), false, formInstance.loadUserPreferences()));

                context.requestUserInteraction(new ExportExcelPivotAction(file,
                        readFieldCaptions(rows), readFieldCaptions(columns), readFieldCaptions(filters), readFieldCaptions(cells)));

            }

        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        } catch (ClassNotFoundException e) {
            throw Throwables.propagate(e);
        } catch (JRException e) {
            throw Throwables.propagate(e);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public List<String> readFieldCaptions(List<String> fields) throws ScriptingErrorLog.SemanticErrorException {
        List<String> result = new ArrayList<String>();
        if (fields != null) {
            for (String field : fields) {
                LCP property = findProperty(field);
                if (property != null)
                    result.add(property.property.caption);
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