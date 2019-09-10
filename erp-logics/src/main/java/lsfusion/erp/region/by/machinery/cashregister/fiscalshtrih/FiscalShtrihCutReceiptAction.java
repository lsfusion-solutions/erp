package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalShtrihCutReceiptAction extends InternalAction {

    public FiscalShtrihCutReceiptAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {

        try {

            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            Integer pass = (Integer) findProperty("operatorNumberCurrentCashRegisterCurrentUser[]").read(context);
            int password = pass == null ? 30000 : pass * 1000;

            String result = (String) context.requestUserInteraction(new FiscalShtrihCustomOperationClientAction(5, password, comPort, baudRate));
            if (result != null)
                context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));

        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }

    }
}
