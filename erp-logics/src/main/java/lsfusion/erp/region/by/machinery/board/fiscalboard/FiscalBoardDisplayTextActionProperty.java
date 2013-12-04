package lsfusion.erp.region.by.machinery.board.fiscalboard;

import lsfusion.server.classes.ValueClass;
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

    public FiscalBoardDisplayTextActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, new ValueClass[]{LM.findClassByCompoundName("ReceiptDetail")});

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptDetailInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) {

        DataSession session = context.getSession();
        DataObject receiptDetailObject = context.getDataKeyValue(receiptDetailInterface);
        
        try {

            Integer comPortBoard = (Integer) getLCP("comPortBoardCurrentCashRegister").read(context);
            Integer baudRateBoard = (Integer) getLCP("baudRateBoardCurrentCashRegister").read(context);

            ObjectValue receiptObject = getLCP("receiptReceiptDetail").readClasses(session, receiptDetailObject);

            String name = (String) getLCP("nameSkuReceiptDetail").read(session, receiptDetailObject);
            name = name == null ? "" : name.trim();
            BigDecimal quantityValue = (BigDecimal) getLCP("quantityReceiptDetail").read(session, receiptDetailObject);
            double quantity = quantityValue == null ? 0.0 : quantityValue.doubleValue();
            BigDecimal priceValue = (BigDecimal) getLCP("priceReceiptDetail").read(session, receiptDetailObject);
            long price = priceValue == null ? 0 : priceValue.longValue();
            BigDecimal sumValue = (BigDecimal) getLCP("sumReceiptDetailReceipt").read(session, (DataObject)receiptObject);
            long sum = sumValue == null ? 0 : sumValue.longValue();

            context.requestUserInteraction(new FiscalBoardDisplayTextClientAction(new ReceiptItem(price, quantity, name, sum), baudRateBoard, comPortBoard));


        } catch (SQLException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}
