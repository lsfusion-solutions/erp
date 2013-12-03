package lsfusion.erp.region.by.machinery.cashregister.fiscalcasbi;

import lsfusion.interop.action.MessageClientAction;
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

public class FiscalCasbiDisplayTextActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface receiptDetailInterface;

    public FiscalCasbiDisplayTextActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, new ValueClass[]{LM.findClassByCompoundName("ReceiptDetail")});

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptDetailInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) {

        DataSession session = context.getSession();
        DataObject receiptDetailObject = context.getDataKeyValue(receiptDetailInterface);

        try {
            ObjectValue receiptObject = LM.findLCPByCompoundOldName("receiptReceiptDetail").readClasses(session, receiptDetailObject);
            Integer comPort = (Integer) LM.findLCPByCompoundOldName("comPortCurrentCashRegister").read(session);
            Integer baudRate = (Integer) LM.findLCPByCompoundOldName("baudRateCurrentCashRegister").read(session);

            String name = (String) LM.findLCPByCompoundOldName("nameSkuReceiptDetail").read(session, receiptDetailObject);
            String barcode = (String) LM.findLCPByCompoundOldName("idBarcodeReceiptDetail").read(session, receiptDetailObject);
            BigDecimal quantity = (BigDecimal) LM.findLCPByCompoundOldName("quantityReceiptDetail").read(session, receiptDetailObject);
            BigDecimal price = (BigDecimal) LM.findLCPByCompoundOldName("priceReceiptDetail").read(session, receiptDetailObject);
            BigDecimal sum = (BigDecimal) LM.findLCPByCompoundOldName("sumReceiptDetailReceipt").read(session, (DataObject)receiptObject);
            BigDecimal articleDisc = (BigDecimal) LM.findLCPByCompoundOldName("discountPercentReceiptSaleDetail").read(session, receiptDetailObject);

            String typeReceiptDetail = (String) LM.findLCPByCompoundOldName("typeReceiptDetail").read(session, receiptDetailObject);
            Boolean isGiftCard = typeReceiptDetail != null && typeReceiptDetail.equals("Сертификат");

            if (sum == null) sum = BigDecimal.ZERO;
            String result = (String)context.requestUserInteraction(new FiscalCasbiDisplayTextClientAction(comPort, baudRate, new ReceiptItem(isGiftCard, price, quantity, barcode, name, sum, articleDisc)));
            if(result!=null)
                context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
        } catch (SQLException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }


    }
}
