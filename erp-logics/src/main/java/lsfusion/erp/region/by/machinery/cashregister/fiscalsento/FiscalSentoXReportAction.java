package lsfusion.erp.region.by.machinery.cashregister.fiscalsento;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.sql.SQLException;

public class FiscalSentoXReportAction extends InternalAction {

    public FiscalSentoXReportAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {

            String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context);
            String comPort = (String) findProperty("stringComPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            String fiscalSentoZReportTitle = (String) findProperty("fiscalSentoReceiptTitle[]").read(context);
            
            String result = (String) context.requestUserInteraction(new FiscalSentoCustomOperationClientAction(false, logPath, comPort, baudRate, 1, fiscalSentoZReportTitle));
            if (result == null) {
                context.apply();
            }
            else {
                ServerLoggers.systemLogger.error("FiscalSentoXReport Error: " + result);
                context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}
