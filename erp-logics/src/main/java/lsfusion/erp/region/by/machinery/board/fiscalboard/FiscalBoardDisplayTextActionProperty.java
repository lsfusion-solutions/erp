package lsfusion.erp.region.by.machinery.board.fiscalboard;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Iterator;

public class FiscalBoardDisplayTextActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface receiptDetailInterface;

    public FiscalBoardDisplayTextActionProperty(ScriptingLogicsModule LM, ValueClass... classes) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptDetailInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataSession session = context.getSession();
        DataObject receiptDetailObject = context.getDataKeyValue(receiptDetailInterface);

        try {
            ObjectValue receiptObject = findProperty("receiptReceiptDetail").readClasses(session, receiptDetailObject);
            boolean skipReceipt = findProperty("fiscalSkipReceipt").read(context.getSession(), receiptObject) != null;
            if (!skipReceipt) {
                Integer comPortBoard = (Integer) findProperty("comPortBoardCurrentCashRegister").read(context);
                Integer baudRateBoard = (Integer) findProperty("baudRateBoardCurrentCashRegister").read(context);

                String name = null;
                ScriptingLogicsModule zReportNlibLM = context.getBL().getModule("ZReportNlib");
                if(zReportNlibLM != null)
                    name = (String) zReportNlibLM.findProperty("shortNameSkuReceiptDetail").read(session, receiptDetailObject);
                name = trim(name == null ? (String) findProperty("nameSkuReceiptDetail").read(session, receiptDetailObject) : name);
                BigDecimal quantityValue = (BigDecimal) findProperty("quantityReceiptDetail").read(session, receiptDetailObject);
                double quantity = quantityValue == null ? 0.0 : quantityValue.doubleValue();
                BigDecimal priceValue = (BigDecimal) findProperty("priceReceiptDetail").read(session, receiptDetailObject);
                long price = priceValue == null ? 0 : priceValue.longValue();
                BigDecimal sumValue = (BigDecimal) findProperty("sumReceiptDetailReceipt").read(session, (DataObject) receiptObject);
                long sum = sumValue == null ? 0 : sumValue.longValue();

                String[] lines = generateText(price, quantity, sum, name, 20);

                context.requestUserInteraction(new FiscalBoardDisplayTextClientAction(lines[0], lines[1], baudRateBoard, comPortBoard));

            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }

    private static String[] generateText(long price, double quantity, long sum, String nameItem, int len) {
        String firstLine = " " + toStr(quantity) + "x" + String.valueOf(price);
        int length = len - Math.min(len, firstLine.length());
        String name = nameItem.substring(0, Math.min(length, nameItem.length()));
        while ((name + firstLine).length() < 20)
            name = name + " ";
        firstLine = name + firstLine;
        String secondLine = String.valueOf(sum);
        while (secondLine.length() < (len - 5))
            secondLine = " " + secondLine;
        secondLine = "ИТОГ:" + secondLine;
        return new String[]{firstLine, secondLine};
    }

    private static String toStr(double value) {
        boolean isInt = (value - (int) value) == 0;
        return isInt ? String.valueOf((int) value) : String.valueOf(value);
    }

    protected String trim(String input) {
        return input == null ? null : input.trim();
    }
}
