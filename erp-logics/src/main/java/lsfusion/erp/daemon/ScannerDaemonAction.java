package lsfusion.erp.daemon;

import com.google.common.base.Throwables;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.sql.SQLException;
import java.util.Iterator;

import static lsfusion.base.BaseUtils.trim;
import static lsfusion.base.BaseUtils.trimToNull;

public class ScannerDaemonAction extends InternalAction {
    private final ClassPropertyInterface comPortInterface;
    private final ClassPropertyInterface singleReadInterface;
    private final ClassPropertyInterface comLibraryInterface;

    public ScannerDaemonAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        comPortInterface = i.next();
        singleReadInterface = i.next();
        comLibraryInterface = i.next();
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        String result = null;
        Integer comPort = (Integer) context.getKeyValue(comPortInterface).getValue();
        if(comPort != null) {
            boolean singleRead = context.getKeyValue(singleReadInterface).getValue() != null;

            String comLibrary = trim((String) context.getKeyValue(comLibraryInterface).getValue());
            boolean useJssc = comLibrary != null && comLibrary.equals("ScannerDaemon_ComLibrary.jssc");
            boolean usePureJavaComm = comLibrary != null && comLibrary.equals("ScannerDaemon_ComLibrary.pureJavaComm");
            if (usePureJavaComm) {
                throw new RuntimeException("Pure Java Comm not supported for Scanner Daemon");
            }

            result = (String) context.requestUserInteraction(new ScannerDaemonClientAction(comPort, singleRead, useJssc));
        }
        try {
            findProperty("scannerDaemonError[]").change(trimToNull(result), context);
        } catch (SQLException | SQLHandledException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    protected boolean allowNulls() {
        return true;
    }
}