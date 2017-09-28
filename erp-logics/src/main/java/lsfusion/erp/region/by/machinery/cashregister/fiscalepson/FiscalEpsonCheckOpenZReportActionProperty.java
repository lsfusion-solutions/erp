package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalEpsonCheckOpenZReportActionProperty extends ScriptingActionProperty {

    public FiscalEpsonCheckOpenZReportActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context.getSession());
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context.getSession());
            boolean blockDesync = findProperty("blockDesync[]").read(context.getSession()) != null;
            Long maxDesync = (Long) findProperty("maxDesync[]").read(context.getSession());
            
            if (blockDesync) {
               String result = (String)context.requestUserInteraction(new FiscalEpsonCustomOperationClientAction(5, comPort, baudRate, maxDesync));
                if (result != null)
                    throw new RuntimeException("Ошибка синхронизации времени:" + result);
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}