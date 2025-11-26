package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import com.google.common.base.Throwables;
import lsfusion.erp.integration.DefaultIntegrationAction;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalVMKAdvancePaperAction extends DefaultIntegrationAction {

    public FiscalVMKAdvancePaperAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {

            boolean isUnix = findProperty("isUnix[]").read(context) != null;
            String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context);
            String ip = (String) findProperty("ipCurrentCashRegister[]").read(context);
            String comPort = (String) findProperty("stringComPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);

            String result = (String) context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(isUnix, logPath, ip, comPort, baudRate, 3));
            if (result != null) {
                ServerLoggers.systemLogger.error("FiscalVMKAdvancePaper Error: " + result);
                messageClientAction(context, result, "Ошибка");
            }
            
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}
