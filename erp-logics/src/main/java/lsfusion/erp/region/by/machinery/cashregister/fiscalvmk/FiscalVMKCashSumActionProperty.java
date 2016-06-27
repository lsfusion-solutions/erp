package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.math.BigDecimal;
import java.sql.SQLException;

public class FiscalVMKCashSumActionProperty extends ScriptingActionProperty {

    public FiscalVMKCashSumActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            DataObject zReportObject = (DataObject) findProperty("currentZReport[]").readClasses(context);

            String ip = (String) findProperty("ipCurrentCashRegister[]").read(context.getSession());
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            String denominationStage = (String) findProperty("denominationStageCurrentCashRegister[]").read(context);
            denominationStage = denominationStage == null ? null : denominationStage.trim();

            Object result = context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(ip, comPort, baudRate, 5, denominationStage));
            if (result instanceof Double) {
                context.requestUserInteraction(new MessageClientAction(String.valueOf(new BigDecimal((Double)result)), "Сумма наличных в кассе"));
            } else if (result instanceof String) {
                context.requestUserInteraction(new MessageClientAction((String) result, "Ошибка"));
            }

        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}
