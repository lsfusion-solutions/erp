package lsfusion.erp.region.by.machinery.cashregister.fiscalsento;

import com.google.common.base.Throwables;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.NullValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FiscalSentoPrintInvoicePaymentAction extends InternalAction {
    private final ClassPropertyInterface invoiceInterface;
    private final ClassPropertyInterface paymentInterface;
    private final ClassPropertyInterface detailInterface;

    public FiscalSentoPrintInvoicePaymentAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        invoiceInterface = i.next();
        paymentInterface = i.next();
        detailInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {

        try {
            DataObject invoiceObject = context.getDataKeyValue(invoiceInterface);
            DataObject paymentObject = context.getDataKeyValue(paymentInterface);
            boolean detail = context.getKeyValue(detailInterface).getValue() != null;

            boolean isUnix = findProperty("isUnix[]").read(context) != null;
            String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context);
            String comPort = (String) findProperty("stringComPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            BigDecimal maxSum = (BigDecimal) findProperty("maxSumCurrentCashRegister[]").read(context);

            BigDecimal sumPayment = (BigDecimal) findProperty("sum[Payment.Payment]").read(context, paymentObject);
            Integer typePayment = (Integer) findProperty("fiscalType[Payment.Payment]").read(context, paymentObject);

            Integer numberSection = (Integer) findProperty("numberSection[Sale.Invoice]").read(context, invoiceObject);

            List<InvoiceDetail> invoiceDetailList = new ArrayList<>();
            if(detail) {
                KeyExpr detailExpr = new KeyExpr("invoiceDetail");
                ImRevMap<Object, KeyExpr> detailKeys = MapFact.singletonRev("invoiceDetail", detailExpr);

                QueryBuilder<Object, Object> detailQuery = new QueryBuilder<>(detailKeys);
                String[] detailNames = new String[]{"name", "price", "quantity", "sum"};
                LP<?>[] itemProperties = findProperties("nameSku[Sale.InvoiceDetail]", "invoicePrice[Sale.InvoiceDetail]", "quantity[Sale.InvoiceDetail]", "invoiceSum[Sale.InvoiceDetail]");
                for (int i = 0; i < itemProperties.length; i++) {
                    detailQuery.addProperty(detailNames[i], itemProperties[i].getExpr(context.getModifier(), detailExpr));
                }
                detailQuery.and(findProperty("invoice[Sale.InvoiceDetail]").getExpr(context.getModifier(), detailExpr).compare(invoiceObject.getExpr(), Compare.EQUALS));
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> detailResult = detailQuery.execute(context);
                BigDecimal totalSum = BigDecimal.ZERO;
                for (ImMap<Object, Object> entry : detailResult.values()) {
                    BigDecimal sum = (BigDecimal) entry.get("sum");
                    invoiceDetailList.add(new InvoiceDetail((String) entry.get("name"), (BigDecimal) entry.get("price"), (BigDecimal) entry.get("quantity"), sum));
                    totalSum = totalSum.add(sum);
                }
                if(totalSum.compareTo(sumPayment) != 0) {
                    throw new RuntimeException(String.format("Сумма платежа (%s) должна совпадать с суммой чека (%s)", sumPayment, totalSum));
                }
            }

            if (sumPayment != null && typePayment != null) {
                if (maxSum != null && sumPayment.compareTo(maxSum) > 0) {
                    context.requestUserInteraction(new MessageClientAction("Сумма платежа превышает " + maxSum.intValue() + " рублей", "Ошибка!"));
                    return;
                }
            }
            
            Object result = context.requestUserInteraction(new FiscalSentoPrintInvoicePaymentClientAction(isUnix, logPath, comPort,
                    baudRate, sumPayment, typePayment, numberSection, true, invoiceDetailList));
            boolean error = false;
            if(result instanceof Integer) {
                findProperty("note[Sale.Invoice]").change(String.valueOf(result), context, invoiceObject);
                findProperty("number[Payment.Payment]").change(String.valueOf(result), context, paymentObject);
            } else if(result != null) {
                error = true;
                ServerLoggers.systemLogger.error("FiscalSentoPrintInvoicePayment Error: " + result);
            }
            findProperty("printReceiptResult[]").change(error ? NullValue.instance : new DataObject(true), context);
            findProperty("printReceiptError[]").change(error ? result : null, context);

        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }


    }

    @Override
    protected boolean allowNulls() {
        return true;
    }
}
