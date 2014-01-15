package lsfusion.erp.region.by.machinery.cashregister.fiscalcasbi;

import lsfusion.interop.action.MessageClientAction;
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

public class FiscalCasbiDisplayTextActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface receiptDetailInterface;

    public FiscalCasbiDisplayTextActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClassByCompoundName("ReceiptDetail"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptDetailInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataSession session = context.getSession();
        DataObject receiptDetailObject = context.getDataKeyValue(receiptDetailInterface);

        try {
            ObjectValue receiptObject = getLCP("receiptReceiptDetail").readClasses(session, receiptDetailObject);
            boolean skipReceipt = getLCP("fiscalSkipReceipt").read(context.getSession(), receiptObject) != null;
            if (!skipReceipt) {
                Integer comPort = (Integer) getLCP("comPortCurrentCashRegister").read(session);
                Integer baudRate = (Integer) getLCP("baudRateCurrentCashRegister").read(session);

                String name = (String) getLCP("nameSkuReceiptDetail").read(session, receiptDetailObject);
                String barcode = (String) getLCP("idBarcodeReceiptDetail").read(session, receiptDetailObject);
                BigDecimal quantity = (BigDecimal) getLCP("quantityReceiptDetail").read(session, receiptDetailObject);
                BigDecimal price = (BigDecimal) getLCP("priceReceiptDetail").read(session, receiptDetailObject);
                BigDecimal sum = (BigDecimal) getLCP("sumReceiptDetailReceipt").read(session, (DataObject) receiptObject);
                BigDecimal articleDisc = (BigDecimal) getLCP("discountPercentReceiptSaleDetail").read(session, receiptDetailObject);

                String typeReceiptDetail = (String) getLCP("typeReceiptDetail").read(session, receiptDetailObject);
                Boolean isGiftCard = typeReceiptDetail != null && typeReceiptDetail.equals("Сертификат");

                if (sum == null) sum = BigDecimal.ZERO;
                String result = (String) context.requestUserInteraction(new FiscalCasbiDisplayTextClientAction(comPort, baudRate, new ReceiptItem(isGiftCard, price, quantity, barcode, name, sum, articleDisc)));
                if (result != null)
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }


    }
}
