package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalEpsonElectronicJournalAction extends InternalAction {

    public FiscalEpsonElectronicJournalAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {

            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            
            if (context.checkApply()) {
               String result = (String)context.requestUserInteraction(new FiscalEpsonCustomOperationClientAction(3, comPort, baudRate));
                if (result != null)
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}