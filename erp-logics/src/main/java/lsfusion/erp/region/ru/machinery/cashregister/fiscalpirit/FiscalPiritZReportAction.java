package lsfusion.erp.region.ru.machinery.cashregister.fiscalpirit;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.sql.SQLException;

public class FiscalPiritZReportAction extends InternalAction {

    public FiscalPiritZReportAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {

            DataObject zReportObject = (DataObject) findProperty("currentZReport[]").readClasses(context);

            boolean isUnix = findProperty("isUnix[]").read(context) != null;
            String comPort = (String) findProperty("stringComPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            String cashier = (String) findProperty("userName[]").read(context);

            if (context.checkApply()) {
                Object result = context.requestUserInteraction(new FiscalPiritCustomOperationClientAction(isUnix, comPort, baudRate, cashier, 2));
                if (result instanceof String) {
                    context.requestUserInteraction(new MessageClientAction((String) result, "Ошибка"));
                } else if(result instanceof Integer) {
                    findProperty("fiscalNumber[ZReport]").change(String.valueOf(result), context, zReportObject);
                }
            }

            findAction("closeCurrentZReport[]").execute(context);
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}