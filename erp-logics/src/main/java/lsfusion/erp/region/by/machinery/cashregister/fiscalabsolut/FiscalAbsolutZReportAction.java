package lsfusion.erp.region.by.machinery.cashregister.fiscalabsolut;

import com.google.common.base.Throwables;
import lsfusion.interop.action.ConfirmClientAction;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.base.controller.thread.ThreadLocalContext;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import javax.swing.*;
import java.sql.SQLException;

public class FiscalAbsolutZReportAction extends InternalAction {

    public FiscalAbsolutZReportAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            boolean close = true;

            String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context);
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            String fiscalAbsolutReportTop = (String) findProperty("fiscalAbsolutReportTop[]").read(context);
            boolean saveCommentOnFiscalTape = findProperty("saveCommentOnFiscalTapeAbsolut[]").read(context) != null;
            boolean useSKNO = findProperty("useSKNOAbsolutCurrentCashRegister[]").read(context) != null;

            if (context.checkApply()) {
                Object result = context.requestUserInteraction(new FiscalAbsolutCustomOperationClientAction(logPath, comPort, baudRate, 2,
                        fiscalAbsolutReportTop, saveCommentOnFiscalTape, useSKNO));
                if (result != null) {
                    context.requestUserInteraction(new MessageClientAction((String) result, "Ошибка"));
                } else {
                    Integer dialogResult = (Integer) ThreadLocalContext.requestUserInteraction(new ConfirmClientAction(
                            "Печать Z-отчёта", "Нажмите 'Да', если печать Z-отчёта завершилась успешно " +
                            "или 'Нет', если печать завершилась с ошибкой"));
                    if (dialogResult == JOptionPane.YES_OPTION) {
                        result = context.requestUserInteraction(new FiscalAbsolutCustomOperationClientAction(logPath, comPort, baudRate, 3,
                                fiscalAbsolutReportTop, saveCommentOnFiscalTape, useSKNO));
                        if (result != null) {
                            context.requestUserInteraction(new MessageClientAction((String) result, "Ошибка"));
                        }
                    } else {
                        close = false;
                    }
                }
            }
            if (close)
                findAction("closeCurrentZReport[]").execute(context);
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}
