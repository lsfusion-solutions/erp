package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.form.property.Compare;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.property.LP;
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

public class FiscalShtrihPrintReceiptAction extends InternalAction {
    private final ClassPropertyInterface receiptInterface;

    public FiscalShtrihPrintReceiptAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {

        DataObject receiptObject = context.getDataKeyValue(receiptInterface);

        ScriptingLogicsModule giftCardLM = context.getBL().getModule("GiftCard");

        try {
            boolean skipReceipt = findProperty("fiscalSkip[Receipt]").read(context, receiptObject) != null;
            if (skipReceipt) {
                context.apply();
                findAction("createCurrentReceipt[]").execute(context);
            } else {
                Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
                Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
                Integer pass = (Integer) findProperty("operatorNumberCurrentCashRegisterCurrentUser[]").read(context);
                int password = pass == null ? 30000 : pass * 1000;

                String cashierName = (String) findProperty("nameEmployee[Receipt]").read(context, receiptObject);
                cashierName = cashierName == null ? "" : cashierName.trim();
                String holderDiscountCard = (String) findProperty("nameLegalEntityDiscountCard[Receipt]").read(context, receiptObject);
                holderDiscountCard = holderDiscountCard == null ? "" : holderDiscountCard.trim();
                String numberDiscountCard = (String) findProperty("numberDiscountCard[Receipt]").read(context, receiptObject);
                numberDiscountCard = numberDiscountCard == null ? "" : numberDiscountCard.trim();

                BigDecimal sumCard = null;
                BigDecimal sumCash = null;
                BigDecimal sumGiftCard = null;
                List<String> giftCardNumbers = new ArrayList<>();

                KeyExpr paymentExpr = new KeyExpr("payment");
                ImRevMap<Object, KeyExpr> paymentKeys = MapFact.singletonRev("payment", paymentExpr);

                QueryBuilder<Object, Object> paymentQuery = new QueryBuilder<>(paymentKeys);
                paymentQuery.addProperty("sumPayment", findProperty("sum[Payment]").getExpr(context.getModifier(), paymentExpr));
                paymentQuery.addProperty("paymentMeansPayment", findProperty("paymentMeans[Payment]").getExpr(context.getModifier(), paymentExpr));
                if (giftCardLM != null)
                    paymentQuery.addProperty("seriesNumberGiftCardPaymentGiftCard", giftCardLM.findProperty("seriesNumberGiftCard[PaymentGiftCard]").getExpr(context.getModifier(), paymentExpr));
                paymentQuery.and(findProperty("receipt[Payment]").getExpr(context.getModifier(), paymentQuery.getMapExprs().get("payment")).compare(receiptObject.getExpr(), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> paymentResult = paymentQuery.execute(context);
                for (ImMap<Object, Object> paymentValues : paymentResult.valueIt()) {
                    DataObject paymentMeansCashObject = ((ConcreteCustomClass) findClass("PaymentMeans")).getDataObject("paymentMeansCash");
                    DataObject paymentMeansCardObject = ((ConcreteCustomClass) findClass("PaymentMeans")).getDataObject("paymentMeansCard");
                    DataObject paymentMeansGiftCardObject = giftCardLM == null ? null : ((ConcreteCustomClass) giftCardLM.findClass("PaymentMeans")).getDataObject("paymentMeansGiftCard");
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
                ImRevMap<Object, KeyExpr> receiptDetailKeys = MapFact.singletonRev("receiptDetail", receiptDetailExpr);

                QueryBuilder<Object, Object> receiptDetailQuery = new QueryBuilder<>(receiptDetailKeys);
                String[] rdNames = new String[]{"nameSkuReceiptDetail", "typeReceiptDetail", "quantityReceiptDetail",
                        "quantityReceiptSaleDetail", "quantityReceiptReturnDetail", "priceReceiptDetail",
                        "idBarcodeReceiptDetail", "sumReceiptDetail", "discountSumReceiptDetail", "valueVATReceiptDetail"};
                LP[] rdProperties = findProperties("nameSku[ReceiptDetail]", "type[ReceiptDetail]", "quantity[ReceiptDetail]",
                        "quantity[ReceiptSaleDetail]", "quantity[ReceiptReturnDetail]", "price[ReceiptDetail]",
                        "idBarcode[ReceiptDetail]", "sum[ReceiptDetail]", "discountSum[ReceiptDetail]", "valueVAT[ReceiptDetail]");
                for (int i = 0; i < rdProperties.length; i++) {
                    receiptDetailQuery.addProperty(rdNames[i], rdProperties[i].getExpr(context.getModifier(), receiptDetailExpr));
                }
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
                    barcode = barcode == null ? null : barcode.trim();
                    String name = (String) receiptDetailValues.get("nameSkuReceiptDetail");
                    name = name == null ? null : name.trim();
                    BigDecimal sumReceiptDetail = (BigDecimal) receiptDetailValues.get("sumReceiptDetail");
                    BigDecimal discountSumReceiptDetail = (BigDecimal) receiptDetailValues.get("discountSumReceiptDetail");
                    discountSumReceiptDetail = discountSumReceiptDetail == null ? null : discountSumReceiptDetail.negate();
                    BigDecimal valueVATReceiptDetail = (BigDecimal) receiptDetailValues.get("valueVATReceiptDetail");
                    String typeReceiptDetail = (String) receiptDetailValues.get("typeReceiptDetail");
                    Boolean isGiftCard = typeReceiptDetail != null && typeReceiptDetail.equals("Сертификат");

                    if (quantitySale != null && !isGiftCard)
                        receiptSaleItemList.add(new ReceiptItem(isGiftCard, price, quantitySale, barcode, name,
                                sumReceiptDetail, discountSumReceiptDetail, valueVATReceiptDetail));
                    if (quantity != null && isGiftCard) {
                        receiptSaleItemList.add(new ReceiptItem(isGiftCard, price, quantity, barcode, "Подарочный сертификат",
                                sumReceiptDetail, discountSumReceiptDetail, valueVATReceiptDetail));
                    }
                    if (quantityReturn != null) {
                        BigDecimal discount = discountSumReceiptDetail == null ? BigDecimal.ZERO : discountSumReceiptDetail.divide(quantityReturn);
                        receiptReturnItemList.add(new ReceiptItem(isGiftCard,
                                price, quantityReturn, barcode,
                                name, sumReceiptDetail, discount, valueVATReceiptDetail));
                    }
                }

                if (!receiptSaleItemList.isEmpty() && !receiptReturnItemList.isEmpty())
                    context.requestUserInteraction(new MessageClientAction("В чеке обнаружены одновременно продажа и возврат", "Ошибка"));
                else {
                    if (context.checkApply()) {
                        Boolean isReturn = receiptReturnItemList.size() > 0;
                        String result = (String) context.requestUserInteraction(
                                new FiscalShtrihPrintReceiptClientAction(password, comPort, baudRate, isReturn,
                                        new ReceiptInstance(sumCash == null ? null : sumCash.abs(),
                                                sumCard == null ? null : sumCard.abs(),
                                                sumGiftCard == null ? null : sumGiftCard.abs(),
                                                giftCardNumbers, cashierName, holderDiscountCard, numberDiscountCard,
                                                isReturn ? receiptReturnItemList : receiptSaleItemList)));
                        if (result == null) {
                            context.apply();
                            findAction("createCurrentReceipt[]").execute(context);
                        } else
                            context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
                    }
                }
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }


    }
}
