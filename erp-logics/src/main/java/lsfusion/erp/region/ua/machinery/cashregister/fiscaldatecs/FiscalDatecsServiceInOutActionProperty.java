package lsfusion.erp.region.ua.machinery.cashregister.fiscaldatecs;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;

public class FiscalDatecsServiceInOutActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface cashOperationInterface;

    public FiscalDatecsServiceInOutActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClass("CashOperation"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        cashOperationInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            DataObject cashOperationObject = context.getDataKeyValue(cashOperationInterface);

            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister").read(context.getSession());
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister").read(context.getSession());
            Boolean isDone = findProperty("isCompleteCashOperation").read(context.getSession(), cashOperationObject) != null;
            Double sum = (Double) findProperty("sumCashOperation").read(context.getSession(), cashOperationObject);

            if (!isDone) {
                String result = (String) context.requestUserInteraction(new FiscalDatecsServiceInOutClientAction(baudRate, comPort, sum));
                if (result == null){
                    findProperty("isCompleteCashOperation").change(true, context.getSession(), cashOperationObject);
                }
                else
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}
