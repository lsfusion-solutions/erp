package lsfusion.erp.region.ua.machinery.cashregister.fiscaldatecs;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.logics.scripted.ScriptingModuleErrorLog;

import java.sql.SQLException;

public class FiscalDatecsAdvancePaperActionProperty extends ScriptingActionProperty {

    public FiscalDatecsAdvancePaperActionProperty(ScriptingLogicsModule LM) throws ScriptingModuleErrorLog.SemanticError {
        super(LM);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context.getSession());
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context.getSession());

            String result = (String) context.requestUserInteraction(new FiscalDatecsCustomOperationClientAction(3, baudRate, comPort));
            if (result == null)
                context.apply();
            else
                context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));

        } catch (SQLException | ScriptingModuleErrorLog.SemanticError e) {
            throw new RuntimeException(e);
        }
    }
}
