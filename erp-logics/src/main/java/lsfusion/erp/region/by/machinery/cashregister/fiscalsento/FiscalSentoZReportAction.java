package lsfusion.erp.region.by.machinery.cashregister.fiscalsento;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.sql.SQLException;

public class FiscalSentoZReportAction extends InternalAction {

    public FiscalSentoZReportAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {

            DataObject zReportObject = (DataObject) findProperty("currentZReport[]").readClasses(context);

            String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context);
            String comPort = (String) findProperty("stringComPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            String fiscalSentoReportTop = (String) findProperty("fiscalSentoReportTop[]").read(context);

            if (context.checkApply()) {
                    Object result = context.requestUserInteraction(new FiscalSentoCustomOperationClientAction(false, logPath, comPort, baudRate, 2, fiscalSentoReportTop));
                    if(result instanceof Integer && (Integer) result != 0) {
                        findProperty("fiscalNumber[ZReport]").change(String.valueOf(result), context, zReportObject);
                    } else if (result instanceof String) {
                        ServerLoggers.systemLogger.error("FiscalSentoZReport Error: " + result);
                        context.requestUserInteraction(new MessageClientAction((String) result, "Ошибка"));
                    }

            }
            findAction("closeCurrentZReport[]").execute(context);
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}
