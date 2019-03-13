package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.base.file.RawFileData;
import lsfusion.interop.action.OpenFileClientAction;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingActionProperty;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

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
                    context.requestUserInteraction(new OpenFileClientAction(new RawFileData(result.getBytes()), "epson", "txt"));
                }
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}