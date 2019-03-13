package lsfusion.erp.region.ua.machinery.cashregister.fiscaldatecs;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingActionProperty;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.sql.SQLException;
import java.util.Iterator;

public class FiscalDatecsDisplayTextActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface receiptDetailInterface;

    public FiscalDatecsDisplayTextActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptDetailInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataSession session = context.getSession();
        DataObject receiptDetailObject = context.getDataKeyValue(receiptDetailInterface);

        try {
            ObjectValue receiptObject = findProperty("receipt[ReceiptDetail]").readClasses(session, receiptDetailObject);
            boolean skipReceipt = findProperty("fiscalSkip[Receipt]").read(context.getSession(), receiptObject) != null;
            if (!skipReceipt) {
                Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(session);
                Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(session);

                String name = (String) findProperty("nameSku[ReceiptDetail]").read(session, receiptDetailObject);
                String barcode = (String) findProperty("idBarcode[ReceiptDetail]").read(session, receiptDetailObject);
                Double quantity = (Double) findProperty("quantity[ReceiptDetail]").read(session, receiptDetailObject);
                Double price = (Double) findProperty("price[ReceiptDetail]").read(session, receiptDetailObject);
                Double sum = (Double) findProperty("sumReceiptDetail[Receipt]").read(session, (DataObject) receiptObject);
                Double articleDisc = (Double) findProperty("discountPercent[ReceiptSaleDetail]").read(session, receiptDetailObject);
                Double articleDiscSum = (Double) findProperty("discountSum[ReceiptDetail]").read(session, receiptDetailObject);


                String result = (String) context.requestUserInteraction(new FiscalDatecsDisplayTextClientAction(baudRate, comPort, new ReceiptItem(price, quantity, barcode, name, sum, articleDisc, articleDiscSum, 0, 0)));
                if (result != null)
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }


    }
}
