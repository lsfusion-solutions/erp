package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;

public class FiscalVMKCancelReceiptAction extends InternalAction {
    private final ClassPropertyInterface receiptInterface;

    public FiscalVMKCancelReceiptAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            DataObject receiptObject = context.getDataKeyValue(receiptInterface);

            boolean skipReceipt = findProperty("fiscalSkip[Receipt]").read(context.getSession(), receiptObject) != null;
            if (!skipReceipt) {
                boolean isUnix = findProperty("isUnix[]").read(context) != null;
                String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context.getSession());
                String ip = (String) findProperty("ipCurrentCashRegister[]").read(context.getSession());
                Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context.getSession());
                Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context.getSession());

                String result = (String) context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(isUnix, logPath, ip, comPort, baudRate, 4));
                if (result != null) {
                    ServerLoggers.systemLogger.error("FiscalVMKCancelReceipt Error: " + result);
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
                }
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}
