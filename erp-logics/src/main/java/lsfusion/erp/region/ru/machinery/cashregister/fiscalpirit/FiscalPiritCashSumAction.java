package lsfusion.erp.region.ru.machinery.cashregister.fiscalpirit;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.math.BigDecimal;
import java.sql.SQLException;

public class FiscalPiritCashSumAction extends InternalAction {

    public FiscalPiritCashSumAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {

            boolean isUnix = findProperty("isUnix[]").read(context) != null;
            String comPort = (String) findProperty("stringComPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            String cashier = (String) findProperty("currentUserName[]").read(context);

            Object result = context.requestUserInteraction(new FiscalPiritCustomOperationClientAction(isUnix, comPort, baudRate, cashier, 7));
            if (result instanceof BigDecimal) {
                context.requestUserInteraction(new MessageClientAction(String.valueOf(result), "Сумма наличных в кассе"));
            } else if (result instanceof String) {
                ServerLoggers.systemLogger.error("FiscalPiritCashSum Error: " + result);
                context.requestUserInteraction(new MessageClientAction((String) result, "Ошибка"));
            }

        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}
