package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.logics.scripted.ScriptingModuleErrorLog;

import java.sql.SQLException;

public class FiscalVMKZReportActionProperty extends ScriptingActionProperty {

    public FiscalVMKZReportActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            DataObject zReportObject = (DataObject) findProperty("currentZReport[]").readClasses(context);

            String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context.getSession());
            String ip = (String) findProperty("ipCurrentCashRegister[]").read(context.getSession());
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            String fiscalVMKReportTop = (String) findProperty("fiscalVMKReportTop[]").read(context);

            if (context.checkApply()) {
                if(ip == null) { //rs-232
                    Object result = context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(logPath, ip, comPort, baudRate, 2, fiscalVMKReportTop));
                    if (result instanceof Integer) {
                        if ((Integer) result != 0)
                            findProperty("number[ZReport]").change(String.valueOf(result), context, zReportObject);
                    } else if (result instanceof String) {
                        context.requestUserInteraction(new MessageClientAction((String) result, "Ошибка"));
                    }
                } else { //ethernet
                    Object result = context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(logPath, ip, comPort, baudRate, 6, fiscalVMKReportTop));
                    if(result == null) {
                        context.requestUserInteraction(new MessageClientAction("Дождитесь окончания печати z-отчета и нажмите ОК", "Подождите..."));
                        result = context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(logPath, ip, comPort, baudRate, 7, fiscalVMKReportTop));
                        if (result instanceof Integer) {
                            if ((Integer) result != 0)
                                findProperty("number[ZReport]").change(String.valueOf(result), context, zReportObject);
                        } else if (result instanceof String) {
                            context.requestUserInteraction(new MessageClientAction((String) result, "Ошибка"));
                        }
                    } else if (result instanceof String) {
                        ServerLoggers.systemLogger.error("FiscalVMKZReport Error: " + result);
                        context.requestUserInteraction(new MessageClientAction((String) result, "Ошибка"));
                        context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(logPath, ip, comPort, baudRate, 8, fiscalVMKReportTop));
                    }

                }
            }
            findAction("closeCurrentZReport[]").execute(context);
        } catch (SQLException | ScriptingModuleErrorLog.SemanticError e) {
            throw Throwables.propagate(e);
        }
    }
}
