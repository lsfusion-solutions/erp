package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Iterator;

import static lsfusion.base.BaseUtils.trim;

public class FiscalEpsonResetReceiptActionProperty extends InternalAction {
    private final ClassPropertyInterface receiptInterface;

    public FiscalEpsonResetReceiptActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            DataObject receiptObject = context.getDataKeyValue(receiptInterface);

            boolean skipReceipt = findProperty("fiscalSkip[Receipt]").read(context.getSession(), receiptObject) != null;
            if (!skipReceipt) {
                Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context.getSession());
                Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context.getSession());

                Integer cardType = (Integer) findProperty("cardTypeCurrentCashRegister[]").read(context.getSession());
                Integer giftCardType = (Integer) findProperty("giftCardTypeCurrentCashRegister[]").read(context.getSession());

                String cashier = trim((String) findProperty("currentUserName[]").read(context));
                Integer numberReceipt = (Integer) findProperty("documentNumber[Receipt]").read(context.getSession(), receiptObject);
                BigDecimal totalSum = (BigDecimal) findProperty("sumReceiptDetail[Receipt]").read(context.getSession(), receiptObject);
                BigDecimal sumCash = (BigDecimal) findProperty("sumCashPayment[Receipt]").read(context.getSession(), receiptObject);
                BigDecimal sumCard = (BigDecimal) findProperty("sumCardPayment[Receipt]").read(context.getSession(), receiptObject);
                ScriptingLogicsModule giftCardLM = context.getBL().getModule("GiftCard");
                BigDecimal sumGiftCard = giftCardLM == null ? null : (BigDecimal) giftCardLM.findProperty("sumGiftCardPayment[Receipt]").read(context.getSession(), receiptObject);

                String result = (String) context.requestUserInteraction(new FiscalEpsonResetReceiptClientAction(comPort, baudRate, cashier, numberReceipt, totalSum, sumCash, sumCard, sumGiftCard, cardType, giftCardType));
                if (result == null) {
                    findProperty("resetted[Receipt]").change(true, context, receiptObject);
                    findProperty("dataSkip[Receipt]").change(true, context, receiptObject);
                    if (!context.apply())
                        context.requestUserInteraction(new MessageClientAction("Ошибка при аннулировании чека", "Ошибка"));
                } else {
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
                }
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}