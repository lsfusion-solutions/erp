package lsfusion.erp.region.by.machinery.krok;

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

// Выполняет запрос к библиотеке "Крок" на клиенте: результат пишется в krokResponse[], ошибка - в krokError[]
public class KrokExecuteAction extends InternalAction {
    private final ClassPropertyInterface requestInterface;
    private final ClassPropertyInterface libraryDirInterface;

    public KrokExecuteAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        requestInterface = i.next();
        libraryDirInterface = i.next();
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        Object request = context.getKeyValue(requestInterface).getValue();
        String libraryDir = (String) context.getKeyValue(libraryDirInterface).getValue();

        String[] result = request == null ? new String[]{null, "Пустой запрос к библиотеке КРОК"} :
                (String[]) context.requestUserInteraction(new KrokExecuteClientAction(request.toString(), libraryDir));
        try {
            findProperty("krokResponse[]").change(result[0], context);
            findProperty("krokError[]").change(result[1], context);
        } catch (SQLException | SQLHandledException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    protected boolean allowNulls() {
        return true;
    }
}
