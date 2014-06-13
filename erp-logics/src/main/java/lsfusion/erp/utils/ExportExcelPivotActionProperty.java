package lsfusion.erp.utils;

import com.google.common.base.Throwables;
import jasperapi.ReportGenerator;
import lsfusion.base.col.MapFact;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.form.entity.ObjectEntity;
import lsfusion.server.form.instance.FormInstance;
import lsfusion.server.form.instance.FormSessionScope;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.linear.LCP;
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

public abstract class ExportExcelPivotActionProperty extends ScriptingActionProperty {
    
    String form;
    List<String> rows;
    List<String> columns;
    List<String> filters;
    List<String> cells;

    public ExportExcelPivotActionProperty(ScriptingLogicsModule LM, String form, List<String> rows,
                                          List<String> columns, List<String> filters, List<String> cells) {
        super(LM);
        this.form = form;
        this.rows = rows;
        this.columns = columns;
        this.filters = filters;
        this.cells = cells;
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        
        try {

            if(form != null && rows != null && columns != null && cells != null) {
                
                FormInstance formInstance = context.createFormInstance(getForm(form), MapFact.<ObjectEntity, DataObject>EMPTY(),
                        context.getSession(), true, FormSessionScope.OLDSESSION, false, false, false, null);
                
                File file = ReportGenerator.exportToExcel(new FormReportManager(formInstance).getReportData(null, false, formInstance.loadUserPreferences()));

                List<String> rowFields = readFieldCaptions(rows);
                List<String> columnFields = readFieldCaptions(columns);
                List<String> filterFields = readFieldCaptions(filters);
                List<String> cellFields = readFieldCaptions(cells);
                
                context.requestUserInteraction(new ExportExcelPivotAction(file, rowFields, columnFields, filterFields, cellFields));
                
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
        for(String field : fields) {
            LCP property = getLCP(field);
            if(property != null)
            result.add(property.property.caption);
        }
        return result;
    }
}

//Example of implementation ExportExcelPivotActionProperty

//package lsfusion.erp.utils;
//
//import lsfusion.server.data.SQLHandledException;
//import lsfusion.server.logics.property.ClassPropertyInterface;
//import lsfusion.server.logics.property.ExecutionContext;
//import lsfusion.server.logics.scripted.ScriptingLogicsModule;
//
//import java.sql.SQLException;
//import java.util.Arrays;
//
//public class TestFormExportExcelPivotActionProperty extends ExportExcelPivotActionProperty {
//
//    public TestFormExportExcelPivotActionProperty(ScriptingLogicsModule LM) {
//        super(LM, "testForm",
//                Arrays.asList("Purchase.nameCustomerStockInvoice", "Purchase.nameSupplierStockInvoice"),
//                Arrays.asList("Purchase.dateInvoice"),
//                Arrays.asList("Purchase.timeInvoice"),
//                Arrays.asList("Purchase.sumInvoiceDetailInvoice"/*, "Purchase.VATSumInvoiceDetailInvoice"*/));
//    }
//
//
//    @Override
//    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
//        super.executeCustom(context);
//    }
//}