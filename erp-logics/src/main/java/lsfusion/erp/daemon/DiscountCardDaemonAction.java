package lsfusion.erp.daemon;

import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

public class DiscountCardDaemonAction extends InternalAction {

    public DiscountCardDaemonAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        context.requestUserInteraction(new DiscountCardDaemonClientAction());
    }
}