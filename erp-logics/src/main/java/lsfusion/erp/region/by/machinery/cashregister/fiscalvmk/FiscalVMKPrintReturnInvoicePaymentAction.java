package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import com.google.common.base.Throwables;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.language.property.LP;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FiscalVMKPrintReturnInvoicePaymentAction extends InternalAction {
    private final ClassPropertyInterface invoiceInterface;
    private final ClassPropertyInterface paymentInterface;
    private final ClassPropertyInterface detailInterface;

    public FiscalVMKPrintReturnInvoicePaymentAction(ScriptingLogicsModule LM, ValueClass... classes) {
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
            boolean detail = context.getKeyValue(detailInterface) != null;

            String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context);
            String ip = (String) findProperty("ipCurrentCashRegister[]").read(context);
            String comPort = (String) findProperty("stringComPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            BigDecimal maxSum = (BigDecimal) findProperty("maxSumCurrentCashRegister[]").read(context);

            BigDecimal sumPayment = (BigDecimal) findProperty("sum[Payment.Payment]").read(context, paymentObject);
            Integer typePayment = (Integer) findProperty("fiscalType[Payment.Payment]").read(context, paymentObject);
            boolean isUnix = findProperty("isUnix[]").read(context) != null;

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
                    invoiceDetailList.add(new InvoiceDetail((String) entry.get("name"), (BigDecimal)entry.get("price"), (BigDecimal)entry.get("quantity")));
                    totalSum = totalSum.add((BigDecimal)entry.get("sum"));
                }
                if(totalSum.compareTo(sumPayment) != 0) {
                    throw new RuntimeException(String.format("Сумма платежа (%s) должна совпадать с суммой чека (%s)", sumPayment, totalSum));
                }
            }

            if (sumPayment != null && typePayment != null) {
                if (maxSum != null && sumPayment.compareTo(maxSum) > 0) {
                    context.requestUserInteraction(new MessageClientAction("Сумма возврата превышает " + maxSum.intValue() + " рублей", "Ошибка!"));
                    return;
                }
            }
            
            Object result = context.requestUserInteraction(new FiscalVMKPrintInvoicePaymentClientAction(isUnix, logPath, ip, comPort, baudRate, sumPayment, typePayment, false, invoiceDetailList));
            if(result == null)
                findProperty("printReceiptResult[]").change(new DataObject(true), context);
            else {
                ServerLoggers.systemLogger.error("FiscalVMKPrintReturnInvoicePayment Error: " + result);
                findProperty("printReceiptResult[]").change((Boolean) null, context);
            }
            
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }


    }
}
