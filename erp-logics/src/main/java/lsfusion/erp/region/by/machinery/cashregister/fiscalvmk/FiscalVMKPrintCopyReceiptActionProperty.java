package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalVMKPrintCopyReceiptActionProperty extends ScriptingActionProperty {

    public FiscalVMKPrintCopyReceiptActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, new ValueClass[]{});

    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) {


        try {
            Integer comPort = (Integer) LM.findLCPByCompoundOldName("comPortCurrentCashRegister").read(context);
            Integer baudRate = (Integer) LM.findLCPByCompoundOldName("baudRateCurrentCashRegister").read(context);

            String result = (String) context.requestUserInteraction(new FiscalVMKPrintCopyReceiptClientAction(baudRate, comPort));
            if (result != null) {
                context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }


    }
}
