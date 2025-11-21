package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import lsfusion.erp.integration.DefaultIntegrationAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalMercuryXReportAction extends DefaultIntegrationAction {

    public FiscalMercuryXReportAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {

            String result = (String) context.requestUserInteraction(new FiscalMercuryCustomOperationClientAction(1));
            if (result == null)
                context.apply();
            else
                messageClientAction(context, result, "Ошибка");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
