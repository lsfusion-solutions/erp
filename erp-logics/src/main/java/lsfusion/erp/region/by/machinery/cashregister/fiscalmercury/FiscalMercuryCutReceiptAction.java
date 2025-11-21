package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import lsfusion.erp.integration.DefaultIntegrationAction;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingLogicsModule;

public class FiscalMercuryCutReceiptAction extends DefaultIntegrationAction {

    public FiscalMercuryCutReceiptAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) {

        String result = (String) context.requestUserInteraction(new FiscalMercuryCustomOperationClientAction(5));
        if (result != null)
            messageClientAction(context, result, "Ошибка");
    }
}
