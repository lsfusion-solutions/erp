package lsfusion.erp.region.by.machinery.board.fiscalboard;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;

public class FiscalBoardResetTextActionProperty extends FiscalBoardActionProperty {
    private final ClassPropertyInterface timeoutInterface;

    public FiscalBoardResetTextActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        timeoutInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        Integer timeout = (Integer) context.getKeyValue(timeoutInterface).getValue();

        try {
            Integer comPortBoard = (Integer) findProperty("comPortBoardCurrentCashRegister[]").read(context);
            Integer baudRateBoard = (Integer) findProperty("baudRateBoardCurrentCashRegister[]").read(context);
            boolean uppercase = findProperty("uppercaseBoardCurrentCashRegister[]").read(context) != null;
            String defaultTextBoard = (String) findProperty("defaultTextBoard[]").read(context);

            String[] lines = generateText(defaultTextBoard);
            context.requestUserInteraction(new FiscalBoardDisplayTextClientAction(lines[0], lines[1], baudRateBoard, comPortBoard, uppercase, timeout));

        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }

    private String[] generateText(String text) {
        String firstLine = fillSpaces(text == null ? "" : text.substring(0, Math.min(lineLength, text.length())), lineLength, true);
        String secondLine = fillSpaces(text == null ? "" : text.substring(Math.min(lineLength, text.length()), Math.min(lineLength * 2, text.length())), lineLength, true);
        return new String[]{firstLine, secondLine};
    }
}