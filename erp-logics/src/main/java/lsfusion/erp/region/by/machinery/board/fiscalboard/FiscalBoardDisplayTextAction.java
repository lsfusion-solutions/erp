package lsfusion.erp.region.by.machinery.board.fiscalboard;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
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

import static lsfusion.base.BaseUtils.nvl;
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
            boolean useJssc = findProperty("useJsscCurrentCashRegister[]").read(context) != null;

            String name = trimToEmpty((String) findProperty("overNameSku[ReceiptDetail]").read(context, receiptDetailObject));
            BigDecimal quantity = (BigDecimal) findProperty("quantity[ReceiptDetail]").read(context, receiptDetailObject);
            BigDecimal price = (BigDecimal) findProperty("price[ReceiptDetail]").read(context, receiptDetailObject);
            BigDecimal sum = (BigDecimal) findProperty("sumReceiptDetail[Receipt]").read(context, receiptObject);


            String firstLine = " " + toStr(nvl(quantity, BigDecimal.ZERO)) + "x" + toStr(price);
            if(firstLine.length() > lineLength) {
                context.requestUserInteraction(new MessageClientAction("Проверьте цену и количество: " + firstLine, "Ошибка"));
            } else {
                firstLine = fillSpaces(name, lineLength - firstLine.length(), true) + firstLine;
                String secondLine = "ИТОГ:" + fillSpaces(toStr(sum), lineLength - 5);

                context.requestUserInteraction(new FiscalBoardDisplayTextClientAction(firstLine, secondLine, baudRateBoard, comPortBoard, uppercase, useJssc, null));
            }

        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}