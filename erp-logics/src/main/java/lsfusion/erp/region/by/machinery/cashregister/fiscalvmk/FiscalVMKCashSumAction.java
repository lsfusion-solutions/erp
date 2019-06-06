package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.math.BigDecimal;
import java.sql.SQLException;

public class FiscalVMKCashSumAction extends InternalAction {

    public FiscalVMKCashSumAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            boolean isUnix = findProperty("isUnix[]").read(context) != null;
            String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context.getSession());
            String ip = (String) findProperty("ipCurrentCashRegister[]").read(context.getSession());
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);

            Object result = context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(isUnix, logPath, ip, comPort, baudRate, 5));
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
