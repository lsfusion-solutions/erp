package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

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

public class FiscalEpsonPrintCopyReceiptActionProperty extends ScriptingActionProperty {

    public FiscalEpsonPrintCopyReceiptActionProperty(ScriptingLogicsModule LM) throws ScriptingModuleErrorLog.SemanticError {
        super(LM);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);

            Integer electronicJournalReadOffset = (Integer) findProperty("fiscalEpsonElectronicJournalReadOffset[]").read(context);
            Integer electronicJournalReadSize = (Integer) findProperty("fiscalEpsonElectronicJournalReadSize[]").read(context);
            Integer sessionNumber = (Integer) findProperty("fiscalEpsonSessionNumber[]").read(context);

            if (electronicJournalReadOffset != null && electronicJournalReadSize != null && sessionNumber != null) {
                String result = (String) context.requestUserInteraction(new FiscalEpsonPrintCopyReceiptClientAction(comPort, baudRate,
                        electronicJournalReadOffset, electronicJournalReadSize, sessionNumber));
                if (result != null) {
                    ServerLoggers.systemLogger.error("FiscalEpsonPrintCopyReceipt Error: " + result);
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
                }
            } else {
                context.requestUserInteraction(new MessageClientAction("Ошибка! Не удалось получить данные о последнем чеке", "Копия чека"));
            }

        } catch (SQLException | ScriptingModuleErrorLog.SemanticError e) {
            throw Throwables.propagate(e);
        }

    }
}