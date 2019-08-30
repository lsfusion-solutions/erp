package lsfusion.erp.region.ru.machinery.cashregister.fiscalpirit;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Iterator;

public class FiscalPiritServiceInOutAction extends InternalAction {
    private final ClassPropertyInterface cashOperationInterface;

    public FiscalPiritServiceInOutAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        cashOperationInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {
            DataObject cashOperationObject = context.getDataKeyValue(cashOperationInterface);

            String comPort = (String) findProperty("stringComPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            String cashier = (String) findProperty("currentUserName[]").read(context);
            Boolean isDone = findProperty("isComplete[CashOperation]").read(context, cashOperationObject) != null;
            BigDecimal sum = (BigDecimal) findProperty("sum[CashOperation]").read(context, cashOperationObject);

            if (!isDone) {
                String result = (String) context.requestUserInteraction(new FiscalPiritServiceInOutClientAction(comPort, baudRate, cashier, sum));
                if (result == null){
                    findProperty("isComplete[CashOperation]").change(true, context, cashOperationObject);
                } else {
                    ServerLoggers.systemLogger.error("FiscalVMKServiceInOut Error: " + result);
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
                }
            }

        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}