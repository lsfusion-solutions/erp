package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.session.DataSession;

import java.sql.SQLException;

public class FiscalMercuryZReportAction extends InternalAction {

    public FiscalMercuryZReportAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            DataSession session = context.getSession();

            if (context.checkApply()) {
                String result = (String) context.requestUserInteraction(new FiscalMercuryCustomOperationClientAction(2));
                if (result == null)
                    findAction("closeCurrentZReport[]").execute(session, context.stack);
                else
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}
