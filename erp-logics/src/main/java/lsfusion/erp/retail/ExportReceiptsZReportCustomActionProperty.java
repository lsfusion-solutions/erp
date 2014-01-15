package lsfusion.erp.retail;

import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;

public class ExportReceiptsZReportCustomActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface zReportInterface;

    public ExportReceiptsZReportCustomActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClassByCompoundName("ZReport"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        zReportInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataObject zReportObject = context.getDataKeyValue(zReportInterface);
        new ExportReceiptsZReportActionProperty(LM).export(context, zReportObject, null, true);

    }


}
