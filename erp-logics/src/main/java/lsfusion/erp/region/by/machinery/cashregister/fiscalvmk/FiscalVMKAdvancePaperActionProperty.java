package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalVMKAdvancePaperActionProperty extends ScriptingActionProperty {

    public FiscalVMKAdvancePaperActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            String ip = (String) findProperty("ipCurrentCashRegister[]").read(context.getSession());
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context.getSession());
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context.getSession());

            String result = (String) context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(ip, comPort, baudRate, 3));
            if (result != null) {
                ServerLoggers.systemLogger.error("FiscalVMKAdvancePaper Error: " + result);
                context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }
            
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}
