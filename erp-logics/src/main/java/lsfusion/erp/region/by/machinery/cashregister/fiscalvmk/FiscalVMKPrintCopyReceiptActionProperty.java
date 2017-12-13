package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.logics.scripted.ScriptingModuleErrorLog;

import java.sql.SQLException;

public class FiscalVMKPrintCopyReceiptActionProperty extends ScriptingActionProperty {

    public FiscalVMKPrintCopyReceiptActionProperty(ScriptingLogicsModule LM) throws ScriptingModuleErrorLog.SemanticError {
        super(LM);

    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context.getSession());
            String ip = (String) findProperty("ipCurrentCashRegister[]").read(context.getSession());
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);

            String result = (String) context.requestUserInteraction(new FiscalVMKPrintCopyReceiptClientAction(logPath, ip, comPort, baudRate));
            if (result != null) {
                ServerLoggers.systemLogger.error("FiscalVMKPrintCopyReceipt Error: " + result);
                context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }

        } catch (SQLException | ScriptingModuleErrorLog.SemanticError e) {
            throw Throwables.propagate(e);
        }


    }
}
