package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalVMKZReportAction extends InternalAction {

    public FiscalVMKZReportAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {

            DataObject zReportObject = (DataObject) findProperty("currentZReport[]").readClasses(context);

            boolean isUnix = findProperty("isUnix[]").read(context) != null;
            String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context.getSession());
            String ip = (String) findProperty("ipCurrentCashRegister[]").read(context.getSession());
            String comPort = (String) findProperty("stringComPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            String fiscalVMKReportTop = (String) findProperty("fiscalVMKReportTop[]").read(context);

            if (context.checkApply()) {
                if(ip == null) { //rs-232
                    Object result = context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(isUnix, logPath, ip, comPort, baudRate, 2, fiscalVMKReportTop));
                    if (result instanceof Integer) {
                        if ((Integer) result != 0)
                            findProperty("fiscalNumber[ZReport]").change(String.valueOf(result), context, zReportObject);
                    } else if (result instanceof String) {
                        context.requestUserInteraction(new MessageClientAction((String) result, "Ошибка"));
                    }
                } else { //ethernet
                    Object result = context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(isUnix, logPath, ip, comPort, baudRate, 6, fiscalVMKReportTop));
                    if(result == null) {
                        context.requestUserInteraction(new MessageClientAction("Дождитесь окончания печати z-отчета и нажмите ОК", "Подождите..."));
                        result = context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(isUnix, logPath, ip, comPort, baudRate, 7, fiscalVMKReportTop));
                        if (result instanceof Integer) {
                            if ((Integer) result != 0)
                                findProperty("fiscalNumber[ZReport]").change(String.valueOf(result), context, zReportObject);
                        } else if (result instanceof String) {
                            context.requestUserInteraction(new MessageClientAction((String) result, "Ошибка"));
                        }
                    } else if (result instanceof String) {
                        ServerLoggers.systemLogger.error("FiscalVMKZReport Error: " + result);
                        context.requestUserInteraction(new MessageClientAction((String) result, "Ошибка"));
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
