package lsfusion.erp.retail;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.File;
import java.sql.SQLException;
import java.util.Iterator;

public class ExportReceiptsZReportFolderActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface zReportInterface;

    public ExportReceiptsZReportFolderActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, new ValueClass[]{LM.findClassByCompoundName("ZReport")});

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        zReportInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) {

        try {
            String pathExportReceipts = (String) LM.findLCPByCompoundOldName("pathExportReceipts").read(context);

            if (pathExportReceipts != null) {
                if (!new File(pathExportReceipts).exists() && !new File(pathExportReceipts).mkdir())
                    return;
                DataObject zReportObject = context.getDataKeyValue(zReportInterface);
                new ExportReceiptsZReportActionProperty(LM).export(context, zReportObject, pathExportReceipts, false);
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }


    }


}
