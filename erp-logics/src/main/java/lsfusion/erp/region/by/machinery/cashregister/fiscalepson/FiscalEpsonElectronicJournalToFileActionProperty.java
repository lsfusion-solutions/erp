package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.interop.action.OpenFileClientAction;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.logics.scripted.ScriptingModuleErrorLog;

import java.sql.SQLException;

public class FiscalEpsonElectronicJournalToFileActionProperty extends ScriptingActionProperty {

    public FiscalEpsonElectronicJournalToFileActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            Integer offsetBefore = (Integer) findProperty("fiscalEpsonElectronicJournalReadOffsetCurrentZReport[]").read(context);

            if (offsetBefore != null) {
               String result = (String)context.requestUserInteraction(new FiscalEpsonCustomOperationClientAction(9, comPort, baudRate, offsetBefore));
                if (result != null) {
                    context.requestUserInteraction(new OpenFileClientAction(result.getBytes(), "epson", "txt"));
                }
            }
        } catch (SQLException | ScriptingModuleErrorLog.SemanticError e) {
            throw new RuntimeException(e);
        }
    }
}