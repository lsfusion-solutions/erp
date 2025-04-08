package lsfusion.erp.daemon;

import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.util.Iterator;

import static lsfusion.base.BaseUtils.trim;

public class WeightDaemonAction extends InternalAction {
    private final ClassPropertyInterface comPortInterface;
    private final ClassPropertyInterface comLibraryInterface;

    public WeightDaemonAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        comPortInterface = i.next();
        comLibraryInterface = i.next();
    }


    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        Integer comPort = (Integer) context.getKeyValue(comPortInterface).getValue();
        if(comPort != null) {
            String comLibrary = trim((String) context.getKeyValue(comLibraryInterface).getValue());
            boolean useJssc = comLibrary != null && comLibrary.equals("ScannerDaemon_ComLibrary.jssc");
            boolean usePureJavaComm = comLibrary != null && comLibrary.equals("ScannerDaemon_ComLibrary.pureJavaComm");
            if (usePureJavaComm) {
                throw new RuntimeException("Pure Java Comm not supported for Weight Daemon");
            }
            context.requestUserInteraction(new WeightDaemonClientAction(comPort, useJssc));
        }
    }

    @Override
    protected boolean allowNulls() {
        return true;
    }
}