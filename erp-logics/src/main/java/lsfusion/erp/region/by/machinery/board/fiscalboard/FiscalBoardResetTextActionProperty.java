package lsfusion.erp.region.by.machinery.board.fiscalboard;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;

public class FiscalBoardResetTextActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface receiptInterface;

    public FiscalBoardResetTextActionProperty(ScriptingLogicsModule LM, ValueClass... classes) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataObject receiptObject = context.getDataKeyValue(receiptInterface);

        try {
            boolean skipReceipt = findProperty("fiscalSkip[Receipt]").read(context.getSession(), receiptObject) != null;
            if (!skipReceipt) {
                Integer comPortBoard = (Integer) findProperty("comPortBoardCurrentCashRegister[]").read(context);
                Integer baudRateBoard = (Integer) findProperty("baudRateBoardCurrentCashRegister[]").read(context);

                String line = "";
                for (int i = 0; i < 20; i++)
                    line += " ";

                context.requestUserInteraction(new FiscalBoardDisplayTextClientAction(line, line, baudRateBoard, comPortBoard));

            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}
