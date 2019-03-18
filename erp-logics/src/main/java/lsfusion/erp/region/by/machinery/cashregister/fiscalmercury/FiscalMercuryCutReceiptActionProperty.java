package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalMercuryCutReceiptActionProperty extends InternalAction {

    public FiscalMercuryCutReceiptActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        String result = (String) context.requestUserInteraction(new FiscalMercuryCustomOperationClientAction(5));
        if (result != null)
            context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
    }
}
