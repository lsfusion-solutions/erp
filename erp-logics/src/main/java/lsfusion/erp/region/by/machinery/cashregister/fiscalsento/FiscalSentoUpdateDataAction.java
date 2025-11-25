package lsfusion.erp.region.by.machinery.cashregister.fiscalsento;

import com.google.common.base.Throwables;
import lsfusion.erp.integration.DefaultIntegrationAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.admin.log.ServerLoggers;

import java.sql.SQLException;

public class FiscalSentoUpdateDataAction extends DefaultIntegrationAction {

    public FiscalSentoUpdateDataAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {

        try {
            String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context);
            String comPort = (String) findProperty("stringComPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);

            if (context.checkApply()) {
                String result = (String) context.requestUserInteraction(new FiscalSentoUpdateDataClientAction(false, logPath, comPort, baudRate));
                if (result == null)
                    context.apply();
                else {
                    ServerLoggers.systemLogger.error("FiscalSentoUpdateData Error: " + result);
                    messageClientAction(context, result, "Ошибка");
                }
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }


    }
}
