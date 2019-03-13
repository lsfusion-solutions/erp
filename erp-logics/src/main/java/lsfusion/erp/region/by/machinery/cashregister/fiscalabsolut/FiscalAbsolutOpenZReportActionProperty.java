package lsfusion.erp.region.by.machinery.cashregister.fiscalabsolut;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingActionProperty;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalAbsolutOpenZReportActionProperty extends ScriptingActionProperty {

    public FiscalAbsolutOpenZReportActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context);
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            boolean saveCommentOnFiscalTape = findProperty("saveCommentOnFiscalTapeAbsolut[]").read(context) != null;
            boolean useSKNO = findProperty("useSKNOAbsolutCurrentCashRegister[]").read(context) != null;

            if (context.checkApply()) {
                Object result = context.requestUserInteraction(new FiscalAbsolutCustomOperationClientAction(logPath, comPort, baudRate, 9, saveCommentOnFiscalTape, useSKNO));
                if (result != null) {
                    context.requestUserInteraction(new MessageClientAction((String) result, "Ошибка"));
                }
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}