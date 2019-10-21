package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class FiscalEpsonZReportAction extends InternalAction {

    public FiscalEpsonZReportAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {

            DataObject zReportObject = (DataObject) findProperty("currentZReport[]").readClasses(context);

            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);

            if (context.checkApply()) {
                Object result = context.requestUserInteraction(new FiscalEpsonCustomOperationClientAction(2, comPort, baudRate));
                if (result instanceof String) {
                    context.requestUserInteraction(new MessageClientAction((String) result, "Ошибка"));
                } else if (result instanceof Integer) {
                    findProperty("fiscalNumber[ZReport]").change(String.valueOf(result), context, zReportObject);
                }
            }

            findAction("closeCurrentZReport[]").execute(context);
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}