package lsfusion.erp.region.by.machinery.cashregister.fiscalsento;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.sql.SQLException;
import java.util.Iterator;

public class FiscalSentoResetReceiptAction extends InternalAction {
    private final ClassPropertyInterface receiptInterface;

    public FiscalSentoResetReceiptAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        receiptInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {
            DataObject receiptObject = context.getDataKeyValue(receiptInterface);

            boolean skipReceipt = findProperty("fiscalSkip[Receipt]").read(context, receiptObject) != null;
            if (!skipReceipt) {
                String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context);
                String comPort = (String) findProperty("stringComPortCurrentCashRegister[]").read(context);
                Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
                Integer numberReceipt = (Integer) findProperty("number[Receipt]").read(context, receiptObject);

                String result = (String) context.requestUserInteraction(new FiscalSentoResetReceiptClientAction(false, logPath, comPort, baudRate, numberReceipt));
                if (result == null) {
                    findProperty("resetted[Receipt]").change(true, context, receiptObject);
                    findProperty("dataSkip[Receipt]").change(true, context, receiptObject);
                    if (!context.apply())
                        context.requestUserInteraction(new MessageClientAction("Ошибка при аннулировании чека", "Ошибка"));
                } else {
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
                }
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}