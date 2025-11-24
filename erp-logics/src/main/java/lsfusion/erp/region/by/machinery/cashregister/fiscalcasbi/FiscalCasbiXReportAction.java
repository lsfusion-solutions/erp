package lsfusion.erp.region.by.machinery.cashregister.fiscalcasbi;

import lsfusion.erp.integration.DefaultIntegrationAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalCasbiXReportAction extends DefaultIntegrationAction {

    public FiscalCasbiXReportAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);

            String result = (String) context.requestUserInteraction(new FiscalCasbiCustomOperationClientAction(1, comPort, baudRate));
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
