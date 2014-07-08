package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

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

public class FiscalVMKPrintReceiptActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface receiptInterface;

    public FiscalVMKPrintReceiptActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClassByCompoundName("Receipt"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
      
        try {
            DataObject receiptObject = context.getDataKeyValue(receiptInterface);
            DataObject zReportObject = (DataObject) getLCP("zReportReceipt").readClasses(context, receiptObject);

            ScriptingLogicsModule giftCardLM = context.getBL().getModule("GiftCard");

            boolean skipReceipt = getLCP("fiscalSkipReceipt").read(context.getSession(), receiptObject) != null;
            if (skipReceipt) {
                context.apply();
                getLAP("createCurrentReceipt").execute(context);
            } else {
                Integer comPort = (Integer) getLCP("comPortCurrentCashRegister").read(context);
                Integer baudRate = (Integer) getLCP("baudRateCurrentCashRegister").read(context);
                Integer placeNumber = (Integer) getLCP("nppMachineryCurrentCashRegister").read(context);
                ObjectValue userObject = getLCP("userReceipt").readClasses(context, receiptObject);
                Object operatorNumber = userObject.isNull() ? 0 : getLCP("operatorNumberCurrentCashRegister").read(context, (DataObject) userObject);
                BigDecimal sumTotal = (BigDecimal) getLCP("sumReceiptDetailReceipt").read(context, receiptObject);
                BigDecimal maxSum = (BigDecimal) getLCP("maxSumCurrentCashRegister").read(context);
                ScriptingLogicsModule posGiftCardLM = context.getBL().getModule("POSGiftCard");
                boolean giftCardAsDiscount = posGiftCardLM != null && (posGiftCardLM.findLCPByCompoundOldName("giftCardAsDiscountCurrentCashRegister").read(context) != null);
                if (sumTotal != null && maxSum != null && sumTotal.compareTo(maxSum) > 0) {
                    context.requestUserInteraction(new MessageClientAction("Сумма чека превышает " + maxSum.intValue() + " рублей", "Ошибка!"));
                    return;
                }
                
                BigDecimal sumDisc = null;
                BigDecimal sumCard = null;
                BigDecimal sumCash = null;
                BigDecimal sumGiftCard = null;

                KeyExpr paymentExpr = new KeyExpr("payment");
                ImRevMap<Object, KeyExpr> paymentKeys = MapFact.singletonRev((Object) "payment", paymentExpr);

                QueryBuilder<Object, Object> paymentQuery = new QueryBuilder<Object, Object>(paymentKeys);
                paymentQuery.addProperty("sumPayment", getLCP("sumPayment").getExpr(context.getModifier(), paymentExpr));
                paymentQuery.addProperty("paymentMeansPayment", getLCP("paymentMeansPayment").getExpr(context.getModifier(), paymentExpr));
                paymentQuery.and(getLCP("receiptPayment").getExpr(context.getModifier(), paymentQuery.getMapExprs().get("payment")).compare(receiptObject.getExpr(), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> paymentResult = paymentQuery.execute(context.getSession());
                for (ImMap<Object, Object> paymentValues : paymentResult.valueIt()) {
                    DataObject paymentMeansCashObject = ((ConcreteCustomClass) getClass("PaymentMeans")).getDataObject("paymentMeansCash");
                    DataObject paymentMeansCardObject = ((ConcreteCustomClass) getClass("PaymentMeans")).getDataObject("paymentMeansCard");
                    BigDecimal sumPayment = (BigDecimal) paymentValues.get("sumPayment");
                    if(sumPayment != null) {
                        //DataObject paymentMeansGiftCardObject = giftCardLM == null ? null : ((ConcreteCustomClass) giftCardLM.findClassByCompoundName("PaymentMeans")).getDataObject("paymentMeansGiftCard");
                        if (paymentMeansCashObject.getValue().equals(paymentValues.get("paymentMeansPayment"))) {
                            sumCash = sumCash == null ? sumPayment : sumCash.add(sumPayment);
                        } else if (paymentMeansCardObject.getValue().equals(paymentValues.get("paymentMeansPayment"))) {
                            sumCard = sumCard == null ? sumPayment : sumCard.add(sumPayment);
                        } else if (giftCardLM != null && !giftCardAsDiscount) {
                            sumGiftCard = sumGiftCard == null ? sumPayment : sumGiftCard.add(sumPayment);
                        } else
                            sumDisc = sumDisc == null ? sumPayment : sumDisc.add(sumPayment);
                    }
                }

                KeyExpr receiptDetailExpr = new KeyExpr("receiptDetail");
                ImRevMap<Object, KeyExpr> receiptDetailKeys = MapFact.singletonRev((Object) "receiptDetail", receiptDetailExpr);

                QueryBuilder<Object, Object> receiptDetailQuery = new QueryBuilder<Object, Object>(receiptDetailKeys);
                receiptDetailQuery.addProperty("nameSkuReceiptDetail", getLCP("nameSkuReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("quantityReceiptDetail", getLCP("quantityReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("quantityReceiptSaleDetail", getLCP("quantityReceiptSaleDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("quantityReceiptReturnDetail", getLCP("quantityReceiptReturnDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("priceReceiptDetail", getLCP("priceReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("idBarcodeReceiptDetail", getLCP("idBarcodeReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("sumReceiptDetail", getLCP("sumReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("discountPercentReceiptSaleDetail", getLCP("discountPercentReceiptSaleDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("discountSumReceiptDetail", getLCP("discountSumReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("numberVATReceiptDetail", getLCP("numberVATReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("typeReceiptDetail", getLCP("typeReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));

                receiptDetailQuery.and(getLCP("receiptReceiptDetail").getExpr(context.getModifier(), receiptDetailQuery.getMapExprs().get("receiptDetail")).compare(receiptObject.getExpr(), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> receiptDetailResult = receiptDetailQuery.execute(context.getSession());
                List<ReceiptItem> receiptSaleItemList = new ArrayList<ReceiptItem>();
                List<ReceiptItem> receiptReturnItemList = new ArrayList<ReceiptItem>();
                for (ImMap<Object, Object> receiptDetailValues : receiptDetailResult.valueIt()) {
                    String typeReceiptDetail = (String) receiptDetailValues.get("typeReceiptDetail");
                    Boolean isGiftCard = typeReceiptDetail != null && typeReceiptDetail.equals("Сертификат");
                    BigDecimal priceValue = (BigDecimal) receiptDetailValues.get("priceReceiptDetail");
                    long price = priceValue == null ? 0 : priceValue.longValue();
                    BigDecimal quantitySaleValue = (BigDecimal) receiptDetailValues.get("quantityReceiptSaleDetail");
                    double quantitySale = quantitySaleValue == null ? 0.0 : quantitySaleValue.doubleValue();
                    BigDecimal quantityReturnValue = (BigDecimal) receiptDetailValues.get("quantityReceiptReturnDetail");
                    double quantityReturn = quantityReturnValue == null ? 0.0 : quantityReturnValue.doubleValue();
                    BigDecimal quantityValue = (BigDecimal) receiptDetailValues.get("quantityReceiptDetail");
                    double quantity = quantityValue == null ? 0.0 : quantityValue.doubleValue();
                    String barcode = (String) receiptDetailValues.get("idBarcodeReceiptDetail");
                    String name = (String) receiptDetailValues.get("nameSkuReceiptDetail");
                    name = name == null ? "" : name.trim();
                    BigDecimal sumReceiptDetailValue = (BigDecimal) receiptDetailValues.get("sumReceiptDetail");
                    long sumReceiptDetail = sumReceiptDetailValue == null ? 0 : sumReceiptDetailValue.longValue();
                    BigDecimal discountSumReceiptDetailValue = (BigDecimal) receiptDetailValues.get("discountSumReceiptDetail");
                    long discountSumReceiptDetail = discountSumReceiptDetailValue == null ? 0 : discountSumReceiptDetailValue.negate().longValue();
                    if (quantitySale > 0 && !isGiftCard)
                        receiptSaleItemList.add(new ReceiptItem(isGiftCard, price, quantitySale, barcode, name, sumReceiptDetail,
                                discountSumReceiptDetail));
                    if (quantity > 0 && isGiftCard)
                        receiptSaleItemList.add(new ReceiptItem(isGiftCard, price, quantity, barcode, "Подарочный сертификат",
                                sumReceiptDetail, discountSumReceiptDetail));
                    if (quantityReturn > 0)
                        receiptReturnItemList.add(new ReceiptItem(isGiftCard, price, quantityReturn, barcode, name, sumReceiptDetail,
                                discountSumReceiptDetail));
                }

                if (context.checkApply()) {
                    Object result = context.requestUserInteraction(new FiscalVMKPrintReceiptClientAction(baudRate, comPort, placeNumber,
                            operatorNumber == null ? 1 : (Integer) operatorNumber, new ReceiptInstance(sumDisc, sumCard, sumCash,
                            sumGiftCard == null ? null : sumGiftCard.abs(), sumTotal, receiptSaleItemList, receiptReturnItemList)));
                    if (result instanceof Integer) {
                        getLCP("numberReceipt").change(result, context, receiptObject);
                        context.apply();
                        getLAP("createCurrentReceipt").execute(context);
                    } else
                        context.requestUserInteraction(new MessageClientAction((String) result, "Ошибка"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }


    }
}
