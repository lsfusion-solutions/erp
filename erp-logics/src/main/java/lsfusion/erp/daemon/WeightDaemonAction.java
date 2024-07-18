package lsfusion.erp.daemon;

import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.util.Iterator;

public class WeightDaemonAction extends InternalAction {
    private final ClassPropertyInterface comPortInterface;
    private final ClassPropertyInterface useJSerialCommInterface;

    public WeightDaemonAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        comPortInterface = i.next();
        useJSerialCommInterface = i.next();
    }


    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        Integer comPort = (Integer) context.getKeyValue(comPortInterface).getValue();
        boolean useJSerialComm = context.getKeyValue(useJSerialCommInterface).getValue() != null;
        context.requestUserInteraction(new WeightDaemonClientAction(comPort, useJSerialComm));
    }

    @Override
    protected boolean allowNulls() {
        return true;
    }
}