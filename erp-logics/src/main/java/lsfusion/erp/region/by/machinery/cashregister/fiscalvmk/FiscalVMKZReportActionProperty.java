package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.sql.SQLException;

public class FiscalVMKZReportActionProperty extends ScriptingActionProperty {

    public FiscalVMKZReportActionProperty(ScriptingLogicsModule LM) {
        super(LM, new ValueClass[]{});
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) {
        try {
            DataSession session = context.getSession();

            Integer comPort = (Integer) getLCP("comPortCurrentCashRegister").read(context.getSession());
            Integer baudRate = (Integer) getLCP("baudRateCurrentCashRegister").read(context.getSession());

            if (context.checkApply()) {
               String result = (String)context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(2, baudRate, comPort));
                if (result != null)
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }
            getLAP("closeCurrentZReport").execute(session);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}
