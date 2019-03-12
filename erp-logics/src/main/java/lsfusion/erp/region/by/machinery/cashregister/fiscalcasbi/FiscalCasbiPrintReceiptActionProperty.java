package lsfusion.erp.region.by.machinery.cashregister.fiscalcasbi;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.form.property.Compare;
import lsfusion.interop.action.MessageClientAction;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FiscalCasbiPrintReceiptActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface receiptInterface;

    public FiscalCasbiPrintReceiptActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataObject receiptObject = context.getDataKeyValue(receiptInterface);

        try {

            boolean skipReceipt = findProperty("fiscalSkip[Receipt]").read(context.getSession(), receiptObject) != null;
            if (skipReceipt) {
                context.apply();
                findAction("createCurrentReceipt[]").execute(context);
            } else {            
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
            BigDecimal sumTotal = (BigDecimal) findProperty("sumReceiptDetail[Receipt]").read(context, receiptObject);
            BigDecimal sumDisc = (BigDecimal) findProperty("discountSumReceiptDetail[Receipt]").read(context, receiptObject);
            BigDecimal sumCard = null;
            BigDecimal sumCash = null;

            KeyExpr paymentExpr = new KeyExpr("payment");
            ImRevMap<Object, KeyExpr> paymentKeys = MapFact.singletonRev((Object)"payment", paymentExpr);

            QueryBuilder<Object, Object> paymentQuery = new QueryBuilder<>(paymentKeys);
            paymentQuery.addProperty("sumPayment", findProperty("sum[Payment]").getExpr(context.getModifier(), paymentExpr));
            paymentQuery.addProperty("paymentMeansPayment", findProperty("paymentMeans[Payment]").getExpr(context.getModifier(), paymentExpr));

            paymentQuery.and(findProperty("receipt[Payment]").getExpr(context.getModifier(), paymentQuery.getMapExprs().get("payment")).compare(receiptObject.getExpr(), Compare.EQUALS));

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> paymentResult = paymentQuery.execute(context);
            for (ImMap<Object, Object> paymentValues : paymentResult.valueIt()) {
                DataObject paymentMeansCashObject = ((ConcreteCustomClass) findClass("PaymentMeans")).getDataObject("paymentMeansCash");
                if (paymentMeansCashObject.getValue().equals(paymentValues.get("paymentMeansPayment"))) {
                    sumCash = (BigDecimal) paymentValues.get("sumPayment");
                } else  {
                    sumCard = (BigDecimal) paymentValues.get("sumPayment");
                }
            }

            KeyExpr receiptDetailExpr = new KeyExpr("receiptDetail");
            ImRevMap<Object, KeyExpr> receiptDetailKeys = MapFact.singletonRev((Object)"receiptDetail", receiptDetailExpr);

            QueryBuilder<Object, Object> receiptDetailQuery = new QueryBuilder<>(receiptDetailKeys);
            receiptDetailQuery.addProperty("nameSkuReceiptDetail", findProperty("nameSku[ReceiptDetail]").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.addProperty("quantityReceiptSaleDetail", findProperty("quantity[ReceiptSaleDetail]").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.addProperty("quantityReceiptReturnDetail", findProperty("quantity[ReceiptReturnDetail]").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.addProperty("priceReceiptDetail", findProperty("price[ReceiptDetail]").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.addProperty("idBarcodeReceiptDetail", findProperty("idBarcode[ReceiptDetail]").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.addProperty("sumReceiptDetail", findProperty("sum[ReceiptDetail]").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.addProperty("discountSumReceiptDetail", findProperty("discountSum[ReceiptDetail]").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.addProperty("numberVATReceiptDetail", findProperty("numberVAT[ReceiptDetail]").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.addProperty("typeReceiptDetail", findProperty("type[ReceiptDetail]").getExpr(context.getModifier(), receiptDetailExpr));
            
            receiptDetailQuery.and(findProperty("receipt[ReceiptDetail]").getExpr(context.getModifier(), receiptDetailQuery.getMapExprs().get("receiptDetail")).compare(receiptObject.getExpr(), Compare.EQUALS));

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> receiptDetailResult = receiptDetailQuery.execute(context);
            List<ReceiptItem> receiptSaleItemList = new ArrayList<>();
            List<ReceiptItem> receiptReturnItemList = new ArrayList<>();
            for (ImMap<Object, Object> receiptDetailValues : receiptDetailResult.valueIt()) {
                BigDecimal price = (BigDecimal) receiptDetailValues.get("priceReceiptDetail");
                BigDecimal quantitySale = (BigDecimal) receiptDetailValues.get("quantityReceiptSaleDetail");
                BigDecimal quantityReturn = (BigDecimal) receiptDetailValues.get("quantityReceiptReturnDetail");
                BigDecimal quantity = (BigDecimal) receiptDetailValues.get("quantityReceiptDetail");
                String barcode = (String) receiptDetailValues.get("idBarcodeReceiptDetail");
                String name = (String) receiptDetailValues.get("nameSkuReceiptDetail");
                BigDecimal sumReceiptDetail = (BigDecimal) receiptDetailValues.get("sumReceiptDetail");
                BigDecimal discountSumReceiptDetail = (BigDecimal) receiptDetailValues.get("discountSumReceiptDetail");
                String typeReceiptDetail = (String) receiptDetailValues.get("typeReceiptDetail");
                Boolean isGiftCard = typeReceiptDetail != null && typeReceiptDetail.equals("Сертификат");

                if (quantitySale != null && !isGiftCard)
                    receiptSaleItemList.add(new ReceiptItem(isGiftCard, price, quantitySale, barcode, name,
                            sumReceiptDetail, discountSumReceiptDetail));
                if (quantity != null && isGiftCard) 
                    receiptSaleItemList.add(new ReceiptItem(isGiftCard, price, quantity, barcode, "Подарочный сертификат",
                            sumReceiptDetail, discountSumReceiptDetail));                               
                if (quantityReturn != null)
                    receiptReturnItemList.add(new ReceiptItem(false, price, quantityReturn, barcode, name.trim(), sumReceiptDetail,
                            discountSumReceiptDetail==null ? null : discountSumReceiptDetail.negate()));
            }

            if (context.checkApply()){
                String result = (String) context.requestUserInteraction(new FiscalCasbiPrintReceiptClientAction(baudRate, comPort, new ReceiptInstance(sumDisc, sumCard, sumCash, sumTotal, receiptSaleItemList, receiptReturnItemList)));
                if (result == null) {
                    context.apply();
                    findAction("createCurrentReceipt[]").execute(context);
                }
                else
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }   }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }


    }
}
