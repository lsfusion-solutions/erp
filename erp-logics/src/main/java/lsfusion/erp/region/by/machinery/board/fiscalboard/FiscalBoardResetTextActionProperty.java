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

import static lsfusion.base.BaseUtils.trimToEmpty;

public class FiscalBoardResetTextActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface receiptInterface;

    public FiscalBoardResetTextActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
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
                String defaultTextBoard = trimToEmpty((String) findProperty("defaultTextBoard[]").read(context));

                String[] lines = generateText(defaultTextBoard, 20);

                context.requestUserInteraction(new FiscalBoardDisplayTextClientAction(lines[0], lines[1], baudRateBoard, comPortBoard));

            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }

    private static String[] generateText(String text, int len) {
        String firstLine = fillSpaces(text.substring(0, Math.min(len, text.length())), len);
        String secondLine = fillSpaces(text.substring(Math.min(len, text.length()), Math.min(len * 2, text.length())), len);
        return new String[]{firstLine, secondLine};
    }

    private static String fillSpaces(String value, int length) {
        while (value.length() < length)
            value += " ";
        return value;
    }
}
