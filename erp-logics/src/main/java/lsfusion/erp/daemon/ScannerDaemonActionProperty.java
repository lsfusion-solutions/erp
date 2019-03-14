package lsfusion.erp.daemon;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingActionProperty;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;

public class ScannerDaemonActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface comPortInterface;
    private final ClassPropertyInterface singleReadInterface;

    public ScannerDaemonActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        comPortInterface = i.next();
        singleReadInterface = i.next();
    }

    @Override
    protected void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
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