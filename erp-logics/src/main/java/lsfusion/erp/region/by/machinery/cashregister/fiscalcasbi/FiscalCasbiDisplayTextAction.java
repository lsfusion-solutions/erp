package lsfusion.erp.region.by.machinery.cashregister.fiscalcasbi;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.session.DataSession;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Iterator;

public class FiscalCasbiDisplayTextAction extends InternalAction {
    private final ClassPropertyInterface receiptDetailInterface;

    public FiscalCasbiDisplayTextAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
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
                BigDecimal quantity = (BigDecimal) findProperty("quantity[ReceiptDetail]").read(context, receiptDetailObject);
                BigDecimal price = (BigDecimal) findProperty("price[ReceiptDetail]").read(context, receiptDetailObject);
                BigDecimal sum = (BigDecimal) findProperty("sumReceiptDetail[Receipt]").read(context, (DataObject) receiptObject);
                BigDecimal articleDisc = (BigDecimal) findProperty("discountPercent[ReceiptSaleDetail]").read(context, receiptDetailObject);

                String typeReceiptDetail = (String) findProperty("type[ReceiptDetail]").read(context, receiptDetailObject);
                Boolean isGiftCard = typeReceiptDetail != null && typeReceiptDetail.equals("Сертификат");

                if (sum == null) sum = BigDecimal.ZERO;
                String result = (String) context.requestUserInteraction(new FiscalCasbiDisplayTextClientAction(comPort, baudRate, new ReceiptItem(isGiftCard, price, quantity, barcode, name, sum, articleDisc)));
                if (result != null)
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }


    }
}
