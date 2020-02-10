package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalShtrihZReportAction extends InternalAction {

    public FiscalShtrihZReportAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            Integer pass = (Integer) findProperty("operatorNumberCurrentCashRegisterCurrentUser[]").read(context);
            int password = pass==null ? 30000 : pass * 1000;
            
            if (context.checkApply()) {
               String result = (String)context.requestUserInteraction(new FiscalShtrihCustomOperationClientAction(2, password, comPort, baudRate));
                if (result != null)
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }
            findAction("closeCurrentZReport[]").execute(context);
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}
