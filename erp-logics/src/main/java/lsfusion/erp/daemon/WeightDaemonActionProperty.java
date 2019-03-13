package lsfusion.erp.daemon;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingActionProperty;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;

public class WeightDaemonActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface comPortInterface;

    public WeightDaemonActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        comPortInterface = i.next();
    }


    @Override
    protected void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        Integer comPort = (Integer) context.getKeyValue(comPortInterface).getValue();
        context.requestUserInteraction(new WeightDaemonClientAction(comPort));
    }
}