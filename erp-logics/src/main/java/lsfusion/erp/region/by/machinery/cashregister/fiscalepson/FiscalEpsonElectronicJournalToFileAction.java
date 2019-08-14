package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.base.file.RawFileData;
import lsfusion.interop.action.OpenFileClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalEpsonElectronicJournalToFileAction extends InternalAction {

    public FiscalEpsonElectronicJournalToFileAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
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