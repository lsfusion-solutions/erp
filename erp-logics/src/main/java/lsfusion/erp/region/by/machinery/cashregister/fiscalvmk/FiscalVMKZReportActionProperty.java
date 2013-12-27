package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalVMKZReportActionProperty extends ScriptingActionProperty {

    public FiscalVMKZReportActionProperty(ScriptingLogicsModule LM) {
        super(LM, new ValueClass[]{});
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) {
        try {

            DataObject zReportObject = (DataObject) getLCP("currentZReport").readClasses(context);

            Integer comPort = (Integer) getLCP("comPortCurrentCashRegister").read(context);
            Integer baudRate = (Integer) getLCP("baudRateCurrentCashRegister").read(context);

            if (context.checkApply()) {
                Object result = context.requestUserInteraction(new FiscalVMKCustomOperationClientAction(2, baudRate, comPort));
                if (result instanceof Integer) {
                    if ((Integer) result != 0)
                        getLCP("numberZReport").change(String.valueOf(result), context, zReportObject);
                } else if (result instanceof String) {
                    context.requestUserInteraction(new MessageClientAction((String) result, "Ошибка"));
                }
            }
            getLAP("closeCurrentZReport").execute(context);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}
