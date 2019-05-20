package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.sql.SQLException;

public class FiscalVMKUpdateDataActionProperty extends InternalAction {

    public FiscalVMKUpdateDataActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataSession session = context.getSession();

        try {
            String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context.getSession());
            String ip = (String) findProperty("ipCurrentCashRegister[]").read(context.getSession());
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(session);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(session);

            if (context.checkApply()) {
                String result = (String) context.requestUserInteraction(new FiscalVMKUpdateDataClientAction(logPath, ip, comPort, baudRate));
                if (result == null)
                    context.apply();
                else {
                    ServerLoggers.systemLogger.error("FiscalVMKUpdateData Error: " + result);
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
                }
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }


    }
}
