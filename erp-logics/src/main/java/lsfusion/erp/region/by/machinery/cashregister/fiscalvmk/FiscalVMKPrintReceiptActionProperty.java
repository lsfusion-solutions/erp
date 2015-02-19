package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.ValueClass;
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

    public FiscalVMKPrintReceiptActionProperty(ScriptingLogicsModule LM, ValueClass... classes) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
      
        try {
            DataObject receiptObject = context.getDataKeyValue(receiptInterface);
            DataObject zReportObject = (DataObject) findProperty("zReportReceipt").readClasses(context, receiptObject);
            
            String fiscalVMKZReportTop = (String) findProperty("fiscalVMKZReportTop").read(context);
            String fiscalVMKZReportBottom = (String) findProperty("fiscalVMKZReportBottom").read(context);
            
            ScriptingLogicsModule giftCardLM = context.getBL().getModule("GiftCard");

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
                BigDecimal maxSum = (BigDecimal) findProperty("maxSumCurrentCashRegister").read(context);
                ScriptingLogicsModule posGiftCardLM = context.getBL().getModule("POSGiftCard");
                boolean giftCardAsDiscount = posGiftCardLM != null && (posGiftCardLM.findProperty("giftCardAsDiscountCurrentCashRegister").read(context) != null);
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
                paymentQuery.addProperty("sumPayment", findProperty("sumPayment").getExpr(context.getModifier(), paymentExpr));
                paymentQuery.addProperty("paymentMeansPayment", findProperty("paymentMeansPayment").getExpr(context.getModifier(), paymentExpr));
                paymentQuery.and(findProperty("receiptPayment").getExpr(context.getModifier(), paymentQuery.getMapExprs().get("payment")).compare(receiptObject.getExpr(), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> paymentResult = paymentQuery.execute(context.getSession());
                for (ImMap<Object, Object> paymentValues : paymentResult.valueIt()) {
                    DataObject paymentMeansCashObject = ((ConcreteCustomClass) findClass("PaymentMeans")).getDataObject("paymentMeansCash");
                    DataObject paymentMeansCardObject = ((ConcreteCustomClass) findClass("PaymentMeans")).getDataObject("paymentMeansCard");
                    BigDecimal sumPayment = (BigDecimal) paymentValues.get("sumPayment");
                    if(sumPayment != null) {
                        //DataObject paymentMeansGiftCardObject = giftCardLM == null ? null : ((ConcreteCustomClass) giftCardLM.findClass("PaymentMeans")).getDataObject("paymentMeansGiftCard");
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
                receiptDetailQuery.addProperty("nameSkuReceiptDetail", findProperty("nameSkuReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("quantityReceiptDetail", findProperty("quantityReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("quantityReceiptSaleDetail", findProperty("quantityReceiptSaleDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("quantityReceiptReturnDetail", findProperty("quantityReceiptReturnDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("priceReceiptDetail", findProperty("priceReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("idBarcodeReceiptDetail", findProperty("idBarcodeReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("sumReceiptDetail", findProperty("sumReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("discountPercentReceiptSaleDetail", findProperty("discountPercentReceiptSaleDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("discountSumReceiptDetail", findProperty("discountSumReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("numberVATReceiptDetail", findProperty("numberVATReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("typeReceiptDetail", findProperty("typeReceiptDetail").getExpr(context.getModifier(), receiptDetailExpr));

                receiptDetailQuery.and(findProperty("receiptReceiptDetail").getExpr(context.getModifier(), receiptDetailQuery.getMapExprs().get("receiptDetail")).compare(receiptObject.getExpr(), Compare.EQUALS));

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
                            sumGiftCard == null ? null : sumGiftCard.abs(), sumTotal, receiptSaleItemList, receiptReturnItemList), 
                            fiscalVMKZReportTop, fiscalVMKZReportBottom));
                    if (result instanceof Integer) {
                        findProperty("numberReceipt").change(result, context, receiptObject);
                        context.apply();
                        findAction("createCurrentReceipt").execute(context);
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
