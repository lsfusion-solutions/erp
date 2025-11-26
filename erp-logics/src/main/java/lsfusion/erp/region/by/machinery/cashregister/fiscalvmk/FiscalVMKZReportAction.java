package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import com.google.common.base.Throwables;
import lsfusion.erp.integration.DefaultIntegrationAction;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalVMKZReportAction extends DefaultIntegrationAction {

    public FiscalVMKZReportAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {

            boolean isUnix = findProperty("isUnix[]").read(context) != null;
            String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context);
            String ip = (String) findProperty("ipCurrentCashRegister[]").read(context);
            String comPort = (String) findProperty("stringComPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            String fiscalVMKReportTop = (String) findProperty("fiscalVMKReportTop[]").read(context);

            if (context.checkApply()) {
                if(ip == null) { //rs-232
                    Object result = context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(isUnix, logPath, ip, comPort, baudRate, 2, fiscalVMKReportTop));
                    if (result instanceof Integer) {
                        if ((Integer) result != 0)
                            findAction("setFiscalNumber[STRING[28]]").execute(context, new DataObject(String.valueOf(result)));
                    } else if (result instanceof String) {
                        messageClientAction(context, (String) result, "Ошибка");
                    }
                } else { //ethernet
                    Object result = context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(isUnix, logPath, ip, comPort, baudRate, 6, fiscalVMKReportTop));
                    if(result == null) {
                        messageClientAction(context,"Дождитесь окончания печати z-отчета и нажмите ОК", "Подождите...");
                        result = context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(isUnix, logPath, ip, comPort, baudRate, 7, fiscalVMKReportTop));
                        if (result instanceof Integer) {
                            if ((Integer) result != 0)
                                findAction("setFiscalNumber[STRING[28]]").execute(context, new DataObject(String.valueOf(result)));
                        } else if (result instanceof String) {
                            messageClientAction(context, (String) result, "Ошибка");
                        }
                    } else if (result instanceof String) {
                        ServerLoggers.systemLogger.error("FiscalVMKZReport Error: " + result);
                        messageClientAction(context, (String) result, "Ошибка");
                        context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(isUnix, logPath, ip, comPort, baudRate, 8, fiscalVMKReportTop));
                    }

                }
            }
            findAction("closeCurrentZReport[]").execute(context);
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}
