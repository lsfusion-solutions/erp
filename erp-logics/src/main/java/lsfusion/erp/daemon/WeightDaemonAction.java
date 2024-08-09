package lsfusion.erp.daemon;

import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.util.Iterator;

public class WeightDaemonAction extends InternalAction {
    private final ClassPropertyInterface comPortInterface;
    private final ClassPropertyInterface useJsscInterface;

    public WeightDaemonAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        comPortInterface = i.next();
        useJsscInterface = i.next();
    }


    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        Integer comPort = (Integer) context.getKeyValue(comPortInterface).getValue();
        boolean useJssc = context.getKeyValue(useJsscInterface).getValue() != null;
        context.requestUserInteraction(new WeightDaemonClientAction(comPort, useJssc));
    }

    @Override
    protected boolean allowNulls() {
        return true;
    }
}