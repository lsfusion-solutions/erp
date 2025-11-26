package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.erp.integration.DefaultIntegrationAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.math.BigDecimal;
import java.sql.SQLException;

public class FiscalEpsonCashSumAction extends DefaultIntegrationAction {

    public FiscalEpsonCashSumAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {

            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            boolean version116 = findProperty("version116CurrentCashRegister[]").read(context) != null;
            String currencyCode = (String) findProperty("epsonCurrencyCodeCashRegister[]").read(context);

            if (!version116) {
                messageClientAction(context, "Операция не поддерживается","Внимание");
                return;
            }

            if (currencyCode == null) {
                messageClientAction(context, "В настройках не задан\nКод валюты","Внимание");
                return;
            }

            if (context.checkApply()) {
                Object result = context.requestUserInteraction(new FiscalEpsonCustomOperationClientAction(11, comPort, baudRate, null, currencyCode, true));
                if (result instanceof BigDecimal) {
                    messageClientAction(context, result.toString(), "Сумма наличных в кассе");
                } else {
                    messageClientAction(context, "Данные не получены","Внимание");
                }
            }

        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}