package lsfusion.erp.retail;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.File;
import java.sql.SQLException;

public class ExportReceiptsZReportFolderActionProperty extends ExportReceiptsZReportActionProperty {

    public ExportReceiptsZReportFolderActionProperty(ScriptingLogicsModule LM, ValueClass... classes) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, classes);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            String pathExportReceipts = (String) findProperty("pathExportReceipts[]").read(context);

            if (pathExportReceipts != null) {
                if (!new File(pathExportReceipts).exists() && !new File(pathExportReceipts).mkdir())
                    return;
                DataObject zReportObject = context.getDataKeyValue(zReportInterface);
                export(context, zReportObject, pathExportReceipts, false);
            }

        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }


    }


}
