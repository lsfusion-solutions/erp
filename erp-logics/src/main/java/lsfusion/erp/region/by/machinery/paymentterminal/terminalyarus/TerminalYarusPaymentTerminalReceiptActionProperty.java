package lsfusion.erp.region.by.machinery.paymentterminal.terminalyarus;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Iterator;

public class TerminalYarusPaymentTerminalReceiptActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface receiptInterface;

    public TerminalYarusPaymentTerminalReceiptActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataObject receiptObject = context.getDataKeyValue(receiptInterface);

        try {
            boolean skipReceipt = findProperty("fiscalSkip[Receipt]").read(context.getSession(), receiptObject) != null;
            if (!skipReceipt) {
                String host = (String) findProperty("hostPaymentTerminalCurrentCashRegister[]").read(context);
                Integer port = (Integer) findProperty("comPortCurrentPaymentTerminalModelCashRegister[]").read(context);
                if(host != null && port != null) {
                    BigDecimal sumCard = null;

                    KeyExpr paymentExpr = new KeyExpr("payment");
                    ImRevMap<Object, KeyExpr> paymentKeys = MapFact.singletonRev((Object) "payment", paymentExpr);

                    QueryBuilder<Object, Object> paymentQuery = new QueryBuilder<>(paymentKeys);
                    paymentQuery.addProperty("sumPayment", findProperty("sum[Payment]").getExpr(context.getModifier(), paymentExpr));
                    paymentQuery.addProperty("paymentMeansPayment", findProperty("paymentMeans[Payment]").getExpr(context.getModifier(), paymentExpr));

                    paymentQuery.and(findProperty("receipt[Payment]").getExpr(context.getModifier(), paymentQuery.getMapExprs().get("payment")).compare(receiptObject.getExpr(), Compare.EQUALS));

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> paymentResult = paymentQuery.execute(context);
                    for (ImMap<Object, Object> paymentValues : paymentResult.valueIt()) {
                        DataObject paymentMeansCardObject = ((ConcreteCustomClass) findClass("PaymentMeans")).getDataObject("paymentMeansCard");
                        if (paymentMeansCardObject.getValue().equals(paymentValues.get("paymentMeansPayment"))) {
                            sumCard = (BigDecimal) paymentValues.get("sumPayment");
                        }
                    }

                    KeyExpr receiptDetailExpr = new KeyExpr("receiptDetail");
                    ImRevMap<Object, KeyExpr> receiptDetailKeys = MapFact.singletonRev((Object) "receiptDetail", receiptDetailExpr);

                    QueryBuilder<Object, Object> receiptDetailQuery = new QueryBuilder<>(receiptDetailKeys);
                    receiptDetailQuery.addProperty("quantityReceiptSaleDetail", findProperty("quantity[ReceiptSaleDetail]").getExpr(context.getModifier(), receiptDetailExpr));
                    receiptDetailQuery.and(findProperty("receipt[ReceiptDetail]").getExpr(context.getModifier(), receiptDetailQuery.getMapExprs().get("receiptDetail")).compare(receiptObject.getExpr(), Compare.EQUALS));

                    boolean isSale = true;

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> receiptDetailResult = receiptDetailQuery.execute(context);
                    for (ImMap<Object, Object> receiptDetailValues : receiptDetailResult.valueIt()) {
                        BigDecimal quantitySale = (BigDecimal) receiptDetailValues.get("quantityReceiptSaleDetail");
                        isSale = quantitySale != null;
                        break;
                    }

                    String result = sumCard == null || sumCard.abs().equals(BigDecimal.ZERO) ? null :
                            (String) context.requestUserInteraction(new TerminalYarusPaymentTerminalReceiptClientAction(host, port, sumCard.abs(), isSale, null));

                    findProperty("postPaymentTerminalReceiptResult[]").change(result, context.getSession());
                } else {
                    throw new RuntimeException("Не задан хост / порт терминала Yarus");
                }
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}