package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.logics.scripted.ScriptingModuleErrorLog;

import java.sql.SQLException;

public class FiscalMercuryAdvancePaperActionProperty extends ScriptingActionProperty {

    public FiscalMercuryAdvancePaperActionProperty(ScriptingLogicsModule LM) throws ScriptingModuleErrorLog.SemanticError {
        super(LM);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        String result = (String) context.requestUserInteraction(new FiscalMercuryCustomOperationClientAction(3));
        if (result != null)
            context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
    }
}
