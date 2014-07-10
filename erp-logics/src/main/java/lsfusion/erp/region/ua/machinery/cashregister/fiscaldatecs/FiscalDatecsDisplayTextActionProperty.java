package lsfusion.erp.region.ua.machinery.cashregister.fiscaldatecs;

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

import java.sql.SQLException;
import java.util.Iterator;

public class FiscalDatecsDisplayTextActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface receiptDetailInterface;

    public FiscalDatecsDisplayTextActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClass("ReceiptDetail"));

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
                Integer comPort = (Integer) findProperty("comPortCurrentCashRegister").read(session);
                Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister").read(session);

                String name = (String) findProperty("nameSkuReceiptDetail").read(session, receiptDetailObject);
                String barcode = (String) findProperty("idBarcodeReceiptDetail").read(session, receiptDetailObject);
                Double quantity = (Double) findProperty("quantityReceiptDetail").read(session, receiptDetailObject);
                Double price = (Double) findProperty("priceReceiptDetail").read(session, receiptDetailObject);
                Double sum = (Double) findProperty("sumReceiptDetailReceipt").read(session, (DataObject) receiptObject);
                Double articleDisc = (Double) findProperty("discountPercentReceiptSaleDetail").read(session, receiptDetailObject);
                Double articleDiscSum = (Double) findProperty("discountSumReceiptDetail").read(session, receiptDetailObject);


                String result = (String) context.requestUserInteraction(new FiscalDatecsDisplayTextClientAction(baudRate, comPort, new ReceiptItem(price, quantity, barcode, name, sum, articleDisc, articleDiscSum, 0, 0)));
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
