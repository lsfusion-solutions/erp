package lsfusion.erp.region.by.integration.edi;

import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.Iterator;

public class PrintToImportLogActionProperty extends InternalAction {
    private final ClassPropertyInterface stringInterface;

    public PrintToImportLogActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        stringInterface = i.next();
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        ServerLoggers.importLogger.info(context.getDataKeyValue(stringInterface).object);
    }
}