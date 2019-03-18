package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalEpsonPrintCopyReceiptActionProperty extends InternalAction {

    public FiscalEpsonPrintCopyReceiptActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

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

        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }

    }
}