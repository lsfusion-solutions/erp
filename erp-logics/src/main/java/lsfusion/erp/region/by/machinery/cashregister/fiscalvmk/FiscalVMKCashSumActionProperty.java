package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingActionProperty;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.math.BigDecimal;
import java.sql.SQLException;

public class FiscalVMKCashSumActionProperty extends ScriptingActionProperty {

    public FiscalVMKCashSumActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            //DataObject zReportObject = (DataObject) findProperty("currentZReport[]").readClasses(context);

            String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context.getSession());
            String ip = (String) findProperty("ipCurrentCashRegister[]").read(context.getSession());
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);

            Object result = context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(logPath, ip, comPort, baudRate, 5));
            if (result instanceof BigDecimal) {
                context.requestUserInteraction(new MessageClientAction(FiscalVMK.toStr((BigDecimal) result), "Сумма наличных в кассе"));
            } else if (result instanceof String) {
                ServerLoggers.systemLogger.error("FiscalVMKCashSum Error: " + result);
                context.requestUserInteraction(new MessageClientAction((String) result, "Ошибка"));
            }

        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}
