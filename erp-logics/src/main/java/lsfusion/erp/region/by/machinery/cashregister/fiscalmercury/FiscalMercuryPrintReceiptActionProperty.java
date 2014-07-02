package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

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
import lsfusion.server.logics.linear.LCP;
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

public class FiscalMercuryPrintReceiptActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface receiptInterface;

    public FiscalMercuryPrintReceiptActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClassByCompoundName("Receipt"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataObject receiptObject = context.getDataKeyValue(receiptInterface);

        ScriptingLogicsModule giftCardLM = (ScriptingLogicsModule) context.getBL().getModule("GiftCard");

        try {
            boolean skipReceipt = getLCP("fiscalSkipReceipt").read(context.getSession(), receiptObject) != null;
            if (skipReceipt) {
                context.apply();
                getLAP("createCurrentReceipt").execute(context);
            } else {
                String cashierName = (String) getLCP("nameUserReceipt").read(context, receiptObject);
                cashierName = cashierName == null ? "" : cashierName.trim();
                String holderDiscountCard = (String) getLCP("nameLegalEntityDiscountCardReceipt").read(context, receiptObject);
                holderDiscountCard = holderDiscountCard == null ? "" : holderDiscountCard.trim();
                String numberDiscountCard = (String) getLCP("numberDiscountCardReceipt").read(context, receiptObject);
                numberDiscountCard = numberDiscountCard == null ? "" : numberDiscountCard.trim();

                BigDecimal sumCard = null;
                BigDecimal sumCash = null;
                BigDecimal sumGiftCard = null;
                List<String> giftCardNumbers = new ArrayList<String>();

                KeyExpr paymentExpr = new KeyExpr("payment");
                ImRevMap<Object, KeyExpr> paymentKeys = MapFact.singletonRev((Object) "payment", paymentExpr);

                QueryBuilder<Object, Object> paymentQuery = new QueryBuilder<Object, Object>(paymentKeys);
                paymentQuery.addProperty("sumPayment", getLCP("sumPayment").getExpr(context.getModifier(), paymentExpr));
                paymentQuery.addProperty("paymentMeansPayment", getLCP("paymentMeansPayment").getExpr(context.getModifier(), paymentExpr));
                if (giftCardLM != null)
                    paymentQuery.addProperty("seriesNumberGiftCardPaymentGiftCard", giftCardLM.findLCPByCompoundOldName("seriesNumberGiftCardPaymentGiftCard").getExpr(context.getModifier(), paymentExpr));
                paymentQuery.and(getLCP("receiptPayment").getExpr(context.getModifier(), paymentQuery.getMapExprs().get("payment")).compare(receiptObject.getExpr(), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> paymentResult = paymentQuery.execute(context);
                for (ImMap<Object, Object> paymentValues : paymentResult.valueIt()) {
                    DataObject paymentMeansCashObject = ((ConcreteCustomClass) getClass("PaymentMeans")).getDataObject("paymentMeansCash");
                    DataObject paymentMeansCardObject = ((ConcreteCustomClass) getClass("PaymentMeans")).getDataObject("paymentMeansCard");
                    DataObject paymentMeansGiftCardObject = giftCardLM == null ? null : ((ConcreteCustomClass) giftCardLM.findClassByCompoundName("PaymentMeans")).getDataObject("paymentMeansGiftCard");
                    BigDecimal sumPayment = (BigDecimal) paymentValues.get("sumPayment");
                    String seriesNumber = giftCardLM == null ? null : (String) paymentValues.get("seriesNumberGiftCardPaymentGiftCard");
                    seriesNumber = seriesNumber == null ? null : seriesNumber.trim();
                    if (paymentMeansCashObject.getValue().equals(paymentValues.get("paymentMeansPayment"))) {
                        sumCash = sumCash == null ? sumPayment : sumCash.add(sumPayment);
                    } else if (paymentMeansCardObject.getValue().equals(paymentValues.get("paymentMeansPayment"))) {
                        sumCard = sumCard == null ? sumPayment : sumCard.add(sumPayment);
                    } else if (giftCardLM != null && paymentMeansGiftCardObject.getValue().equals(paymentValues.get("paymentMeansPayment"))) {
                        sumGiftCard = sumGiftCard == null ? sumPayment : sumGiftCard.add(sumPayment);
                        giftCardNumbers.add(seriesNumber);
                    }
                }

                KeyExpr receiptDetailExpr = new KeyExpr("receiptDetail");
                ImRevMap<Object, KeyExpr> receiptDetailKeys = MapFact.singletonRev((Object) "receiptDetail", receiptDetailExpr);

                QueryBuilder<Object, Object> receiptDetailQuery = new QueryBuilder<Object, Object>(receiptDetailKeys);
                String[] rdNames = new String[]{"nameSkuReceiptDetail", "typeReceiptDetail", "quantityReceiptDetail",
                        "quantityReceiptSaleDetail", "quantityReceiptReturnDetail", "priceReceiptDetail",
                        "idBarcodeReceiptDetail", "sumReceiptDetail", "discountSumReceiptDetail"};
                LCP[] rdProperties = getLCPs("nameSkuReceiptDetail", "typeReceiptDetail", "quantityReceiptDetail",
                        "quantityReceiptSaleDetail", "quantityReceiptReturnDetail", "priceReceiptDetail",
                        "idBarcodeReceiptDetail", "sumReceiptDetail", "discountSumReceiptDetail");
                for (int i = 0; i < rdProperties.length; i++) {
                    receiptDetailQuery.addProperty(rdNames[i], rdProperties[i].getExpr(context.getModifier(), receiptDetailExpr));
                }
                receiptDetailQuery.and(getLCP("receiptReceiptDetail").getExpr(context.getModifier(), receiptDetailQuery.getMapExprs().get("receiptDetail")).compare(receiptObject.getExpr(), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> receiptDetailResult = receiptDetailQuery.execute(context);
                List<ReceiptItem> receiptSaleItemList = new ArrayList<ReceiptItem>();
                List<ReceiptItem> receiptReturnItemList = new ArrayList<ReceiptItem>();
                for (ImMap<Object, Object> receiptDetailValues : receiptDetailResult.valueIt()) {
                    BigDecimal price = (BigDecimal) receiptDetailValues.get("priceReceiptDetail");
                    BigDecimal quantitySale = (BigDecimal) receiptDetailValues.get("quantityReceiptSaleDetail");
                    BigDecimal quantityReturn = (BigDecimal) receiptDetailValues.get("quantityReceiptReturnDetail");
                    BigDecimal quantity = (BigDecimal) receiptDetailValues.get("quantityReceiptDetail");
                    String barcode = (String) receiptDetailValues.get("idBarcodeReceiptDetail");
                    barcode = barcode == null ? null : barcode.trim();
                    String name = (String) receiptDetailValues.get("nameSkuReceiptDetail");
                    name = name == null ? null : name.trim();
                    BigDecimal sumReceiptDetail = (BigDecimal) receiptDetailValues.get("sumReceiptDetail");
                    BigDecimal discountSumReceiptDetail = (BigDecimal) receiptDetailValues.get("discountSumReceiptDetail");
                    discountSumReceiptDetail = discountSumReceiptDetail == null ? null : discountSumReceiptDetail.negate();
                    String typeReceiptDetail = (String) receiptDetailValues.get("typeReceiptDetail");
                    Boolean isGiftCard = typeReceiptDetail != null && typeReceiptDetail.equals("Сертификат");

                    if (quantitySale != null && !isGiftCard)
                        receiptSaleItemList.add(new ReceiptItem(isGiftCard, price, quantitySale, barcode, name,
                                sumReceiptDetail, discountSumReceiptDetail));
                    if (quantity != null && isGiftCard) {
                        receiptSaleItemList.add(new ReceiptItem(isGiftCard, price, quantity, barcode, "Подарочный сертификат",
                                sumReceiptDetail, discountSumReceiptDetail));
                    }
                    if (quantityReturn != null) {
                        BigDecimal discount = discountSumReceiptDetail == null ? BigDecimal.ZERO : discountSumReceiptDetail.divide(quantityReturn);
                        receiptReturnItemList.add(new ReceiptItem(isGiftCard,
                                price.add(discount), quantityReturn, barcode,
                                name, sumReceiptDetail, null));
                    }
                }

                if (!receiptSaleItemList.isEmpty() && !receiptReturnItemList.isEmpty())
                    context.requestUserInteraction(new MessageClientAction("В чеке обнаружены одновременно продажа и возврат", "Ошибка"));
                else {
                    if (context.checkApply()) {
                        Boolean isReturn = receiptReturnItemList.size() > 0;
                        String result = (String) context.requestUserInteraction(
                                new FiscalMercuryPrintReceiptClientAction(new ReceiptInstance(sumCash, sumCard, sumGiftCard,
                                        giftCardNumbers, cashierName, holderDiscountCard, numberDiscountCard,
                                        isReturn ? receiptReturnItemList : receiptSaleItemList), isReturn));
                        if (result == null) {
                            context.apply();
                            getLAP("createCurrentReceipt").execute(context);
                        } else
                            context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }


    }
}
