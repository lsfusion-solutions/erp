package lsfusion.erp.region.ru.machinery.cashregister.fiscalpirit;

import com.google.common.base.Throwables;
import lsfusion.erp.integration.DefaultIntegrationAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;

import java.sql.SQLException;

import static lsfusion.base.BaseUtils.nvl;

public class FiscalPiritZReportAction extends DefaultIntegrationAction {

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
            Integer versionPirit = nvl((Integer) findProperty("versionPiritCurrentCashRegister[]").read(context), 0);

            if (context.checkApply()) {
                Object result = context.requestUserInteraction(new FiscalPiritCustomOperationClientAction(isUnix, comPort, baudRate, cashier, 2, versionPirit));
                if (result instanceof String) {
                    messageClientAction(context, (String) result, "Ошибка");
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