package lsfusion.erp.region.by.machinery.board.fiscalboard;

import com.google.common.base.Throwables;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Iterator;

import static lsfusion.base.BaseUtils.trimToEmpty;

public class FiscalBoardDisplayTextAction extends FiscalBoardAction {
    private final ClassPropertyInterface receiptDetailInterface;

    public FiscalBoardDisplayTextAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptDetailInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {

        DataObject receiptDetailObject = context.getDataKeyValue(receiptDetailInterface);

        try {
            ObjectValue receiptObject = findProperty("receipt[ReceiptDetail]").readClasses(context, receiptDetailObject);
            Integer comPortBoard = (Integer) findProperty("comPortBoardCurrentCashRegister[]").read(context);
            Integer baudRateBoard = (Integer) findProperty("baudRateBoardCurrentCashRegister[]").read(context);
            boolean uppercase = findProperty("uppercaseBoardCurrentCashRegister[]").read(context) != null;

            String name = trimToEmpty((String) findProperty("overNameSku[ReceiptDetail]").read(context, receiptDetailObject));
            BigDecimal quantity = (BigDecimal) findProperty("quantity[ReceiptDetail]").read(context, receiptDetailObject);
            BigDecimal price = (BigDecimal) findProperty("price[ReceiptDetail]").read(context, receiptDetailObject);
            BigDecimal sum = (BigDecimal) findProperty("sumReceiptDetail[Receipt]").read(context, receiptObject);

            String[] lines = generateText(price, quantity == null ? BigDecimal.ZERO : quantity, sum, name);
            context.requestUserInteraction(new FiscalBoardDisplayTextClientAction(lines[0], lines[1], baudRateBoard, comPortBoard, uppercase, null));

        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }

    private String[] generateText(BigDecimal price, BigDecimal quantity, BigDecimal sum, String nameItem) {
        String firstLine = " " + toStr(quantity) + "x" + toStr(price);
        if(firstLine.length() > lineLength) {
            throw new RuntimeException("Проверьте цену и количество: " + firstLine);
        }
        firstLine = fillSpaces(nameItem, lineLength - firstLine.length(), true) + firstLine;
        String secondLine = "ИТОГ:" + fillSpaces(toStr(sum), lineLength - 5);
        return new String[]{firstLine, secondLine};
    }
}