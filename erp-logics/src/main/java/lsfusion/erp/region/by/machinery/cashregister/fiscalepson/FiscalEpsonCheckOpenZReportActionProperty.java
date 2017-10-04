package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.interop.action.ConfirmClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import javax.swing.*;
import java.sql.SQLException;
import java.util.Date;
import java.util.Iterator;

public class FiscalEpsonCheckOpenZReportActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface zReportInterface;

    public FiscalEpsonCheckOpenZReportActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        zReportInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            DataObject zReportObject = context.getDataKeyValue(zReportInterface);

            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context.getSession());
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context.getSession());
            boolean blockDesync = findProperty("blockDesync[]").read(context.getSession()) != null;
            Long maxDesync = (Long) findProperty("maxDesync[]").read(context.getSession());
            if(maxDesync == null)
                maxDesync = 0L;

            if (blockDesync) {
                Object result = context.requestUserInteraction(new FiscalEpsonCustomOperationClientAction(5, comPort, baudRate));
                if (result instanceof Date) {
                    long delta = Math.abs(((Date)result).getTime() - System.currentTimeMillis()) / 1000;
                    if(delta > maxDesync) {
                        String message = "Рассинхронизация времени на кассе " + delta + "с. Чтобы открыть смену, необходимо синхронизировать время. Синхронизировать?";
                        int confirmResult = (int) context.requestUserInteraction(new ConfirmClientAction("Синхронизация времени", message));
                        if (confirmResult == JOptionPane.YES_OPTION) {
                            result = context.requestUserInteraction(new FiscalEpsonCustomOperationClientAction(6, comPort, baudRate));
                        } else
                            throw new RuntimeException("Без синхронизации времени работа с кассой невозможна.");
                    }
                }
                if(result instanceof String)
                    throw new RuntimeException("Ошибка синхронизации времени:" + result);
            }

            Object result = context.requestUserInteraction(new FiscalEpsonCustomOperationClientAction(7, comPort, baudRate));
            if (result instanceof Integer) {
                findProperty("fiscalEpsonElectronicJournalReadOffset[ZReport]").change(result, context, zReportObject);
            } else
                throw new RuntimeException("Ошибка записи начала отчёта:" + result);

        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}