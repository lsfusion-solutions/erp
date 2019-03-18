package lsfusion.erp.daemon;

import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;

public class WeightDaemonActionProperty extends InternalAction {
    private final ClassPropertyInterface comPortInterface;

    public WeightDaemonActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        comPortInterface = i.next();
    }


    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        Integer comPort = (Integer) context.getKeyValue(comPortInterface).getValue();
        context.requestUserInteraction(new WeightDaemonClientAction(comPort));
    }
}