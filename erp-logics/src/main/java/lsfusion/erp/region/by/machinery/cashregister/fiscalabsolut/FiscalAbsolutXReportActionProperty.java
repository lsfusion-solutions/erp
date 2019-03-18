package lsfusion.erp.region.by.machinery.cashregister.fiscalabsolut;

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

public class FiscalAbsolutXReportActionProperty extends InternalAction {

    public FiscalAbsolutXReportActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context);
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            String fiscalAbsolutZReportTitle = (String) findProperty("fiscalAbsolutReceiptTitle[]").read(context);
            boolean saveCommentOnFiscalTape = findProperty("saveCommentOnFiscalTapeAbsolut[]").read(context) != null;
            boolean useSKNO = findProperty("useSKNOAbsolutCurrentCashRegister[]").read(context) != null;

            String result = (String) context.requestUserInteraction(new FiscalAbsolutCustomOperationClientAction(logPath, comPort, baudRate, 1,
                    fiscalAbsolutZReportTitle, saveCommentOnFiscalTape, useSKNO));
            if (result == null) {
                context.apply();
            }
            else {
                ServerLoggers.systemLogger.error("FiscalAbsolutXReport Error: " + result);
                context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}
