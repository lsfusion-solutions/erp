package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

public class FiscalMercuryCutReceiptActionProperty extends ScriptingActionProperty {

    public FiscalMercuryCutReceiptActionProperty(ScriptingLogicsModule LM) {
        super(LM, new ValueClass[]{});
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) {

        String result = (String) context.requestUserInteraction(new FiscalMercuryCustomOperationClientAction(5));
        if (result != null)
            context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));

    }
}
