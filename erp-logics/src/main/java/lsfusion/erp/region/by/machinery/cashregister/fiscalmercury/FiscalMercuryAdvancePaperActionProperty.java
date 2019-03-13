package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingActionProperty;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalMercuryAdvancePaperActionProperty extends ScriptingActionProperty {

    public FiscalMercuryAdvancePaperActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        String result = (String) context.requestUserInteraction(new FiscalMercuryCustomOperationClientAction(3));
        if (result != null)
            context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
    }
}
