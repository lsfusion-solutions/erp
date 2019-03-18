package lsfusion.erp.daemon;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;

public class ScannerDaemonActionProperty extends InternalAction {
    private final ClassPropertyInterface comPortInterface;
    private final ClassPropertyInterface singleReadInterface;

    public ScannerDaemonActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        comPortInterface = i.next();
        singleReadInterface = i.next();
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        Integer comPort = (Integer) context.getKeyValue(comPortInterface).getValue();
        boolean singleRead = context.getKeyValue(singleReadInterface).getValue() != null;
        String result = (String) context.requestUserInteraction(new ScannerDaemonClientAction(comPort, singleRead));
        if(result != null && !result.isEmpty()) {
            context.delayUserInteraction(new MessageClientAction(result, "Ошибка"));
        }
    }

    @Override
    protected boolean allowNulls() {
        return true;
    }
}