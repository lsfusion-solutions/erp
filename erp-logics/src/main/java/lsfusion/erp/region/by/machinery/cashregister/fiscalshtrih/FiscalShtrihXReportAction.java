package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import lsfusion.erp.integration.DefaultIntegrationAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalShtrihXReportAction extends DefaultIntegrationAction {

    public FiscalShtrihXReportAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            Integer pass = (Integer) findProperty("operatorNumberCurrentCashRegisterCurrentUser[]").read(context);
            int password = pass==null ? 30000 : pass * 1000;
            
            String result = (String) context.requestUserInteraction(new FiscalShtrihCustomOperationClientAction(1, password, comPort, baudRate));
            if (result == null) {
                context.apply();
            }
            else
                messageClientAction(context, result, "Ошибка");
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}
