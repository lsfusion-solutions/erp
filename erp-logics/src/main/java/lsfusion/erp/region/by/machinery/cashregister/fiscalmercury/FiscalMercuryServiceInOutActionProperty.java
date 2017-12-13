package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.logics.scripted.ScriptingModuleErrorLog;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Iterator;

public class FiscalMercuryServiceInOutActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface cashOperationInterface;

    public FiscalMercuryServiceInOutActionProperty(ScriptingLogicsModule LM, ValueClass... classes) throws ScriptingModuleErrorLog.SemanticError {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        cashOperationInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            DataObject cashOperationObject = context.getDataKeyValue(cashOperationInterface);

            Boolean isDone = findProperty("isComplete[CashOperation]").read(context.getSession(), cashOperationObject) != null;
            BigDecimal sum = (BigDecimal) findProperty("sum[CashOperation]").read(context.getSession(), cashOperationObject);

            if (!isDone) {
                String result = (String) context.requestUserInteraction(new FiscalMercuryServiceInOutClientAction(sum));
                if (result == null){
                    findProperty("isComplete[CashOperation]").change(true, context.getSession(), cashOperationObject);
                }
                else
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }

        } catch (SQLException | ScriptingModuleErrorLog.SemanticError e) {
            throw new RuntimeException(e);
        }
    }
}
