package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalVMKCashSumActionProperty extends ScriptingActionProperty {

    public FiscalVMKCashSumActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            DataObject zReportObject = (DataObject) findProperty("currentZReport").readClasses(context);

            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister").read(context);

            Object result = context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(5, baudRate, comPort));
            if (result instanceof Long) {
                context.requestUserInteraction(new MessageClientAction(String.valueOf(result), "Сумма наличных в кассе"));
            } else if (result instanceof String) {
                context.requestUserInteraction(new MessageClientAction((String) result, "Ошибка"));
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}
