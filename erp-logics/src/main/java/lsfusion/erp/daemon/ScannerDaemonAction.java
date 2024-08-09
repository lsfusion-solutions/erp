package lsfusion.erp.daemon;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.util.Iterator;

public class ScannerDaemonAction extends InternalAction {
    private final ClassPropertyInterface comPortInterface;
    private final ClassPropertyInterface singleReadInterface;
    private final ClassPropertyInterface useJsscInterface;

    public ScannerDaemonAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        comPortInterface = i.next();
        singleReadInterface = i.next();
        useJsscInterface = i.next();
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        Integer comPort = (Integer) context.getKeyValue(comPortInterface).getValue();
        boolean singleRead = context.getKeyValue(singleReadInterface).getValue() != null;
        boolean useJssc = context.getKeyValue(useJsscInterface).getValue() != null;
        String result = (String) context.requestUserInteraction(new ScannerDaemonClientAction(comPort, singleRead, useJssc));
        if(result != null && !result.isEmpty()) {
            context.delayUserInteraction(new MessageClientAction(result, "Ошибка"));
        }
    }

    @Override
    protected boolean allowNulls() {
        return true;
    }
}