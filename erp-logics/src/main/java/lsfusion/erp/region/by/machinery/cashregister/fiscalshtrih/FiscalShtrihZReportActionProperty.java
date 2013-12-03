package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.sql.SQLException;

public class FiscalShtrihZReportActionProperty extends ScriptingActionProperty {

    public FiscalShtrihZReportActionProperty(ScriptingLogicsModule LM) {
        super(LM, new ValueClass[]{});
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) {
        try {
            DataSession session = context.getSession();

            Integer comPort = (Integer) LM.findLCPByCompoundOldName("comPortCurrentCashRegister").read(context.getSession());
            Integer baudRate = (Integer) LM.findLCPByCompoundOldName("baudRateCurrentCashRegister").read(context.getSession());
            Integer pass = (Integer) LM.findLCPByCompoundOldName("operatorNumberCurrentCashRegisterCurrentUser").read(context.getSession());
            int password = pass==null ? 30000 : pass * 1000;
            
            if (context.checkApply()) {
               String result = (String)context.requestUserInteraction(new FiscalShtrihCustomOperationClientAction(2, password, comPort, baudRate));
                if (result != null)
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }
            LM.findLAPByCompoundOldName("closeCurrentZReport").execute(session);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}
