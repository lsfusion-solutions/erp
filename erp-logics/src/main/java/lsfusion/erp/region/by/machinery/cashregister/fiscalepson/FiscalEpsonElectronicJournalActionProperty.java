package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.logics.scripted.ScriptingModuleErrorLog;

import java.sql.SQLException;

public class FiscalEpsonElectronicJournalActionProperty extends ScriptingActionProperty {

    public FiscalEpsonElectronicJournalActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context.getSession());
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context.getSession());
            
            if (context.checkApply()) {
               String result = (String)context.requestUserInteraction(new FiscalEpsonCustomOperationClientAction(3, comPort, baudRate));
                if (result != null)
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }
        } catch (SQLException | ScriptingModuleErrorLog.SemanticError e) {
            throw new RuntimeException(e);
        }
    }
}