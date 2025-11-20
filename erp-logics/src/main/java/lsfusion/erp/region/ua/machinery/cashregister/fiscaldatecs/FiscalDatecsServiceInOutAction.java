package lsfusion.erp.region.ua.machinery.cashregister.fiscaldatecs;

import lsfusion.erp.integration.DefaultIntegrationAction;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;

public class FiscalDatecsServiceInOutAction extends DefaultIntegrationAction {
    private final ClassPropertyInterface cashOperationInterface;

    public FiscalDatecsServiceInOutAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        cashOperationInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {
            DataObject cashOperationObject = context.getDataKeyValue(cashOperationInterface);

            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            Boolean isDone = findProperty("isComplete[CashOperation]").read(context, cashOperationObject) != null;
            Double sum = (Double) findProperty("sum[CashOperation]").read(context, cashOperationObject);

            if (!isDone) {
                String result = (String) context.requestUserInteraction(new FiscalDatecsServiceInOutClientAction(baudRate, comPort, sum));
                if (result == null){
                    findProperty("isComplete[CashOperation]").change(true, context, cashOperationObject);
                }
                else
                    messageClientAction(context, result, "Ошибка");
            }

        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}
