package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Iterator;

import static lsfusion.base.BaseUtils.trim;

public class FiscalEpsonServiceInOutActionProperty extends InternalAction {
    private final ClassPropertyInterface cashOperationInterface;

    public FiscalEpsonServiceInOutActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        cashOperationInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            DataObject cashOperationObject = context.getDataKeyValue(cashOperationInterface);

            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context.getSession());
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context.getSession());
            
            Boolean isDone = findProperty("isComplete[CashOperation]").read(context.getSession(), cashOperationObject) != null;
            BigDecimal sum = (BigDecimal) findProperty("sum[CashOperation]").read(context.getSession(), cashOperationObject);
            String cashier = trim((String) findProperty("currentUserName[]").read(context));

            if (!isDone) {
                String result = (String) context.requestUserInteraction(new FiscalEpsonServiceInOutClientAction(comPort, baudRate, cashier, sum));
                if (result == null) {
                    findProperty("isComplete[CashOperation]").change(true, context.getSession(), cashOperationObject);
                } else
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }

        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}