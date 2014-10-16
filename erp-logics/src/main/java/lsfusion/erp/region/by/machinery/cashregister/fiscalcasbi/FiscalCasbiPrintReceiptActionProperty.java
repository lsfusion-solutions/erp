package lsfusion.erp.region.by.machinery.cashregister.fiscalcasbi;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
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

    public FiscalCasbiPrintReceiptActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClass("Receipt"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataObject receiptObject = context.getDataKeyValue(receiptInterface);

        try {

            boolean skipReceipt = findProperty("fiscalSkipReceipt").read(context.getSession(), receiptObject) != null;
            if (skipReceipt) {
                context.apply();
                findAction("createCurrentReceipt").execute(context);
            } else {            
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister").read(context);
            Integer placeNumber = (Integer) findProperty("nppMachineryCurrentCashRegister").read(context);
            ObjectValue userObject = findProperty("employeeReceipt").readClasses(context, receiptObject);
            Object operatorNumber = userObject.isNull() ? 0 : findProperty("operatorNumberCurrentCashRegister").read(context, (DataObject) userObject);
            BigDecimal sumTotal = (BigDecimal) findProperty("sumReceiptDetailReceipt").read(context, receiptObject);
            BigDecimal sumDisc = (BigDecimal) findProperty("discountSumReceiptDetailReceipt").read(context, receiptObject);
            BigDecimal sumCard = null;
            BigDecimal sumCash = null;

            KeyExpr paymentExpr = new KeyExpr("payment");
            ImRevMap<Object, KeyExpr> paymentKeys = MapFact.singletonRev((Object)"payment", paymentExpr);

            QueryBuilder<Object, Object> paymentQuery = new QueryBuilder<Object, Object>(paymentKeys);
            paymentQuery.addProperty("sumPayment", findProperty("sumPayment").getExpr(context.getModifier(), paymentExpr));
            paymentQuery.addProperty("paymentMeansPayment", findProperty("paymentMeansPayment").getExpr(context.getModifier(), paymentExpr));

            paymentQuery.and(findProperty("receiptPayment").getExpr(context.getModifier(), paymentQuery.getMapExprs().get("payment")).compare(receiptObject.getExpr(), Compare.EQUALS));

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

            QueryBuilder<Object, Object> receiptDetailQuery = new QueryBuilder<Object, Object>(receiptDetailKeys);
            receiptDetailQuery.addProperty("nameSkuReceiptDetail", findProperty("nameSkuReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.addProperty("quantityReceiptSaleDetail", findProperty("quantityReceiptSaleDetail").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.addProperty("quantityReceiptReturnDetail", findProperty("quantityReceiptReturnDetail").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.addProperty("priceReceiptDetail", findProperty("priceReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.addProperty("idBarcodeReceiptDetail", findProperty("idBarcodeReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.addProperty("sumReceiptDetail", findProperty("sumReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.addProperty("discountSumReceiptDetail", findProperty("discountSumReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.addProperty("numberVATReceiptDetail", findProperty("numberVATReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
            receiptDetailQuery.addProperty("typeReceiptDetail", findProperty("typeReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
            
            receiptDetailQuery.and(findProperty("receiptReceiptDetail").getExpr(context.getModifier(), receiptDetailQuery.getMapExprs().get("receiptDetail")).compare(receiptObject.getExpr(), Compare.EQUALS));

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> receiptDetailResult = receiptDetailQuery.execute(context);
            List<ReceiptItem> receiptSaleItemList = new ArrayList<ReceiptItem>();
            List<ReceiptItem> receiptReturnItemList = new ArrayList<ReceiptItem>();
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
                String result = (String) context.requestUserInteraction(new FiscalCasbiPrintReceiptClientAction(baudRate, comPort, placeNumber, operatorNumber == null ? 1 : (Integer) operatorNumber, new ReceiptInstance(sumDisc, sumCard, sumCash, sumTotal, receiptSaleItemList, receiptReturnItemList)));
                if (result == null) {
                    context.apply();
                    findAction("createCurrentReceipt").execute(context);
                }
                else
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }   }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }


    }
}
