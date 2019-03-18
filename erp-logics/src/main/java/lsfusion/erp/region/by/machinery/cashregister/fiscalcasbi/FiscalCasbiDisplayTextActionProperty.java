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

public class FiscalCasbiDisplayTextActionProperty extends InternalAction {
    private final ClassPropertyInterface receiptDetailInterface;

    public FiscalCasbiDisplayTextActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptDetailInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

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
                BigDecimal quantity = (BigDecimal) findProperty("quantity[ReceiptDetail]").read(session, receiptDetailObject);
                BigDecimal price = (BigDecimal) findProperty("price[ReceiptDetail]").read(session, receiptDetailObject);
                BigDecimal sum = (BigDecimal) findProperty("sumReceiptDetail[Receipt]").read(session, (DataObject) receiptObject);
                BigDecimal articleDisc = (BigDecimal) findProperty("discountPercent[ReceiptSaleDetail]").read(session, receiptDetailObject);

                String typeReceiptDetail = (String) findProperty("type[ReceiptDetail]").read(session, receiptDetailObject);
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
