package lsfusion.erp;

import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

public class GetVmInfo extends InternalAction {
    public GetVmInfo(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        Long freeMemory = Runtime.getRuntime().freeMemory() / 1024L / 1024L;
        Long maxMemory = Runtime.getRuntime().maxMemory() / 1024L / 1024L;
        Long totalMemory = Runtime.getRuntime().totalMemory() / 1024L / 1024L;

        try {
            this.findProperty("freeMemory[]").change(freeMemory, context);
            this.findProperty("maxMemory[]").change(maxMemory, context);
            this.findProperty("totalMemory[]").change(totalMemory, context);
        } catch (Exception ignored) {
        }
    }

}
