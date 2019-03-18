package lsfusion.erp.region.by.machinery.cashregister.fiscalcasbi;

import lsfusion.interop.action.ConfirmClientAction;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.base.controller.thread.ThreadLocalContext;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.session.DataSession;

import javax.swing.*;
import java.sql.SQLException;

public class FiscalCasbiZReportActionProperty extends InternalAction {

    public FiscalCasbiZReportActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            DataSession session = context.getSession();

            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context.getSession());
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context.getSession());

            if (context.checkApply()) {
                String result = (String) context.requestUserInteraction(new FiscalCasbiCustomOperationClientAction(2, comPort, baudRate));
                if (result != null)
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
                else {
                    int dialogResult = (Integer) ThreadLocalContext.requestUserInteraction(new ConfirmClientAction("Печать контрольной ленты", "Продолжить печать Z-отчёта, когда печать контрольной ленты будет завершена"));
                    if (dialogResult == JOptionPane.YES_OPTION) {
                        result = (String) context.requestUserInteraction(new FiscalCasbiCustomOperationClientAction(5, comPort, baudRate));
                        if (result != null)
                            context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
                        else
                            findAction("closeCurrentZReport[]").execute(session, context.stack);
                    }
                }
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}
