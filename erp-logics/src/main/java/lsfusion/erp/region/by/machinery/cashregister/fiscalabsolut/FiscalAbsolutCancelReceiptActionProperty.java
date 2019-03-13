package lsfusion.erp.region.by.machinery.cashregister.fiscalabsolut;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingActionProperty;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;

public class FiscalAbsolutCancelReceiptActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface receiptInterface;

    public FiscalAbsolutCancelReceiptActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            DataObject receiptObject = context.getDataKeyValue(receiptInterface);

            boolean skipReceipt = findProperty("fiscalSkip[Receipt]").read(context.getSession(), receiptObject) != null;
            if (!skipReceipt) {
                String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context);
                Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context.getSession());
                Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context.getSession());
                boolean saveCommentOnFiscalTape = findProperty("saveCommentOnFiscalTapeAbsolut[]").read(context) != null;
                boolean useSKNO = findProperty("useSKNOAbsolutCurrentCashRegister[]").read(context) != null;

                String result = (String) context.requestUserInteraction(
                        new FiscalAbsolutCustomOperationClientAction(logPath, comPort, baudRate, 4, saveCommentOnFiscalTape, useSKNO));
                if (result != null) {
                    ServerLoggers.systemLogger.error("FiscalAbsolutCancelReceipt Error: " + result);
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
                }
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}
