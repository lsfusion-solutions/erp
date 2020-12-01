package lsfusion.erp.region.ua.machinery.cashregister.fiscaldatecs;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;

public class FiscalDatecsDisplayTextAction extends InternalAction {
    private final ClassPropertyInterface receiptDetailInterface;

    public FiscalDatecsDisplayTextAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        receiptDetailInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {

        DataObject receiptDetailObject = context.getDataKeyValue(receiptDetailInterface);

        try {
            ObjectValue receiptObject = findProperty("receipt[ReceiptDetail]").readClasses(context, receiptDetailObject);
            boolean skipReceipt = findProperty("fiscalSkip[Receipt]").read(context, receiptObject) != null;
            if (!skipReceipt) {
                Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
                Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);

                String name = (String) findProperty("nameSku[ReceiptDetail]").read(context, receiptDetailObject);
                String barcode = (String) findProperty("idBarcode[ReceiptDetail]").read(context, receiptDetailObject);
                Double quantity = (Double) findProperty("quantity[ReceiptDetail]").read(context, receiptDetailObject);
                Double price = (Double) findProperty("price[ReceiptDetail]").read(context, receiptDetailObject);
                Double sum = (Double) findProperty("sumReceiptDetail[Receipt]").read(context, (DataObject) receiptObject);
                Double articleDisc = (Double) findProperty("discountPercent[ReceiptSaleDetail]").read(context, receiptDetailObject);
                Double articleDiscSum = (Double) findProperty("discountSum[ReceiptDetail]").read(context, receiptDetailObject);


                String result = (String) context.requestUserInteraction(new FiscalDatecsDisplayTextClientAction(baudRate, comPort, new ReceiptItem(price, quantity, barcode, name, sum, articleDisc, articleDiscSum, 0, 0)));
                if (result != null)
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }


    }
}
