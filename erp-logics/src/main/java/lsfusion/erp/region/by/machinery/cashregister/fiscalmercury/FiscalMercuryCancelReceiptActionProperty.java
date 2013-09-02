package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

public class FiscalMercuryCancelReceiptActionProperty extends ScriptingActionProperty {

    public FiscalMercuryCancelReceiptActionProperty(ScriptingLogicsModule LM) {
        super(LM, new ValueClass[]{});
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) {

        String result = (String) context.requestUserInteraction(new FiscalMercuryCustomOperationClientAction(4));
        if (result != null)
            context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));

    }
}
