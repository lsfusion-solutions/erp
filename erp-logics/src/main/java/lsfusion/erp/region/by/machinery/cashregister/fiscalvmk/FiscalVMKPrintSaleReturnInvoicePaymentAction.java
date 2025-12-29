package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import com.google.common.base.Throwables;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.DefaultIntegrationAction;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.NullValue;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.admin.log.ServerLoggers;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FiscalVMKPrintSaleReturnInvoicePaymentAction extends DefaultIntegrationAction {
    private final ClassPropertyInterface invoiceInterface;
    private final ClassPropertyInterface paymentInterface;
    private final ClassPropertyInterface detailInterface;

    public FiscalVMKPrintSaleReturnInvoicePaymentAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        invoiceInterface = i.next();
        paymentInterface = i.next();
        detailInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {

        try {
            ObjectValue invoiceObject = context.getKeyValue(invoiceInterface);
            ObjectValue paymentObject = context.getKeyValue(paymentInterface);
            boolean detail = context.getKeyValue(detailInterface).getValue() != null;

            String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context);
            String ip = (String) findProperty("ipCurrentCashRegister[]").read(context);
            String comPort = (String) findProperty("stringComPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            BigDecimal maxSum = (BigDecimal) findProperty("maxSumCurrentCashRegister[]").read(context);

            BigDecimal sumPayment = (BigDecimal) findProperty("sum[Payment.Payment]").read(context, paymentObject);
            Integer typePayment = (Integer) findProperty("fiscalType[Payment.Payment]").read(context, paymentObject);
            boolean isUnix = findProperty("isUnix[]").read(context) != null;

            Integer numberSection = (Integer) findProperty("numberSection[SaleReturn.Invoice]").read(context, invoiceObject);

            List<InvoiceDetail> invoiceDetailList = new ArrayList<>();
            if(detail) {
                KeyExpr detailExpr = new KeyExpr("invoiceDetail");
                ImRevMap<Object, KeyExpr> detailKeys = MapFact.singletonRev("invoiceDetail", detailExpr);

                QueryBuilder<Object, Object> detailQuery = new QueryBuilder<>(detailKeys);
                String[] detailNames = new String[]{"name", "price", "quantity", "sum"};
                LP<?>[] itemProperties = findProperties("nameSku[SaleReturn.InvoiceDetail]", "invoicePrice[SaleReturn.InvoiceDetail]", "quantity[SaleReturn.InvoiceDetail]", "invoiceSum[SaleReturn.InvoiceDetail]");
                for (int i = 0; i < itemProperties.length; i++) {
                    detailQuery.addProperty(detailNames[i], itemProperties[i].getExpr(context.getModifier(), detailExpr));
                }
                detailQuery.and(findProperty("invoice[SaleReturn.InvoiceDetail]").getExpr(context.getModifier(), detailExpr).compare(invoiceObject.getExpr(), Compare.EQUALS));
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
                    messageClientAction(context, "Сумма возврата превышает " + maxSum.intValue() + " рублей", "Ошибка!");
                    return;
                }
            }

            ServerLoggers.systemLogger.error("FiscalVMKPrintSaleReturnInvoicePayment: before client action");
            Object result = context.requestUserInteraction(new FiscalVMKPrintInvoicePaymentClientAction(isUnix, logPath, ip, comPort,
                    baudRate, sumPayment, typePayment, numberSection, false, invoiceDetailList));
            ServerLoggers.systemLogger.error("FiscalVMKPrintSaleReturnInvoicePayment: client action result = " + result);

            boolean error = false;
            if(result instanceof Integer) {
                ServerLoggers.systemLogger.error("FiscalVMKPrintSaleReturnInvoicePayment: result is Integer");
                if(invoiceObject instanceof DataObject) {
                    ServerLoggers.systemLogger.error("FiscalVMKPrintSaleReturnInvoicePayment: write note");
                    findProperty("note[SaleReturn.Invoice]").change(String.valueOf(result), context, (DataObject) invoiceObject);
                }
                if(paymentObject instanceof DataObject) {
                    ServerLoggers.systemLogger.error("FiscalVMKPrintSaleReturnInvoicePayment: write number");
                    findProperty("number[Payment.Payment]").change(String.valueOf(result), context, (DataObject) paymentObject);
                }
            } else if(result != null) {
                error = true;
                ServerLoggers.systemLogger.error("FiscalVMKPrintSaleReturnInvoicePayment error: " + result);
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
