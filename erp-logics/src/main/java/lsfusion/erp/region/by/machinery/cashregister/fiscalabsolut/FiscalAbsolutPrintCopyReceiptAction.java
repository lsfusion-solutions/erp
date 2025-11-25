package lsfusion.erp.region.by.machinery.cashregister.fiscalabsolut;

import com.google.common.base.Throwables;
import lsfusion.erp.integration.DefaultIntegrationAction;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalAbsolutPrintCopyReceiptAction extends DefaultIntegrationAction {

    public FiscalAbsolutPrintCopyReceiptAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {

        try {

            String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context);
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);

            String result = (String) context.requestUserInteraction(new FiscalAbsolutPrintCopyReceiptClientAction(logPath, comPort, baudRate));
            if (result != null) {
                ServerLoggers.systemLogger.error("FiscalAbsolutPrintCopyReceipt Error: " + result);
                messageClientAction(context, result, "Ошибка");
            }

        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }


    }
}
