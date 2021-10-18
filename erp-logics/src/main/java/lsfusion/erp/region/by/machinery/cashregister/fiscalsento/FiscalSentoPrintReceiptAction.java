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
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FiscalSentoPrintReceiptAction extends InternalAction {
    private final ClassPropertyInterface receiptInterface;

    public FiscalSentoPrintReceiptAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        receiptInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
      
        try {
            DataObject receiptObject = context.getDataKeyValue(receiptInterface);

            String fiscalSentoReceiptTop = (String) findProperty("fiscalSentoTop[Receipt]").read(context, receiptObject);
            String fiscalSentoReceiptBottom = (String) findProperty("fiscalSentoBottom[Receipt]").read(context, receiptObject);
            String numberDiscountCard = (String) findProperty("numberDiscountCard[Receipt]").read(context, receiptObject);

            ScriptingLogicsModule giftCardLM = context.getBL().getModule("GiftCard");
            ScriptingLogicsModule cashRegisterTaxLM = context.getBL().getModule("CashRegisterTax");

            boolean skipReceipt = findProperty("fiscalSkip[Receipt]").read(context, receiptObject) != null;
            if (skipReceipt) {
                if (context.apply())
                    findAction("createCurrentReceipt[]").execute(context);
                else
                    ServerLoggers.systemLogger.error("FiscalSentoPrintReceipt Apply Error (Not Fiscal)");
            } else {
                String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context);
                String comPort = (String) findProperty("stringComPortCurrentCashRegister[]").read(context);
                Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
                BigDecimal sumTotal = (BigDecimal) findProperty("sumReceiptDetail[Receipt]").read(context, receiptObject);
                BigDecimal maxSum = (BigDecimal) findProperty("maxSumCurrentCashRegister[]").read(context);

                if (sumTotal != null && maxSum != null && sumTotal.compareTo(maxSum) > 0) {
                    context.requestUserInteraction(new MessageClientAction("Сумма чека превышает " + maxSum.intValue() + " рублей", "Ошибка!"));
                    return;
                }

                ScriptingLogicsModule posGiftCardLM = context.getBL().getModule("POSGiftCard");
                Integer giftCardDepartment = posGiftCardLM != null ? (Integer) posGiftCardLM.findProperty("giftCardDepartmentCurrentCashRegister[]").read(context): null;

                BigDecimal sumDisc = null;

                BigDecimal sumCard = null;
                BigDecimal sumCash = null;
                BigDecimal sumCheck = null;
                BigDecimal sumSalary = null;
                BigDecimal sumGiftCard = null;

                KeyExpr paymentExpr = new KeyExpr("payment");
                ImRevMap<Object, KeyExpr> paymentKeys = MapFact.singletonRev("payment", paymentExpr);

                QueryBuilder<Object, Object> paymentQuery = new QueryBuilder<>(paymentKeys);
                paymentQuery.addProperty("sumPayment", findProperty("sum[Payment]").getExpr(context.getModifier(), paymentExpr));
                paymentQuery.addProperty("paymentMeansPayment", findProperty("paymentMeans[Payment]").getExpr(context.getModifier(), paymentExpr));
                paymentQuery.and(findProperty("receipt[Payment]").getExpr(context.getModifier(), paymentQuery.getMapExprs().get("payment")).compare(receiptObject.getExpr(), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> paymentResult = paymentQuery.execute(context);

                ScriptingLogicsModule posEpayHttpFormLM = context.getBL().getModule("POSEpayHttpForm");
                ScriptingLogicsModule posSalaryLM = context.getBL().getModule("POSSalary");

                DataObject paymentMeansCashObject = ((ConcreteCustomClass) findClass("PaymentMeans")).getDataObject("paymentMeansCash");
                DataObject paymentMeansCardObject = ((ConcreteCustomClass) findClass("PaymentMeans")).getDataObject("paymentMeansCard");
                DataObject paymentMeansEpayObject = posEpayHttpFormLM != null ? ((ConcreteCustomClass) findClass("PaymentMeans")).getDataObject("paymentMeansEpay") : null;
                DataObject paymentMeansSalaryObject = posSalaryLM != null ? ((ConcreteCustomClass) findClass("PaymentMeans")).getDataObject("paymentMeansSalary") : null;

                for (ImMap<Object, Object> paymentValues : paymentResult.valueIt()) {
                    BigDecimal sumPayment = (BigDecimal) paymentValues.get("sumPayment");
                    Object paymentMeans = paymentValues.get("paymentMeansPayment");
                    if(sumPayment != null) {
                        if (paymentMeansCashObject.getValue().equals(paymentMeans)) {
                            sumCash = safeAdd(sumCash, sumPayment);
                        } else if (paymentMeansCardObject.getValue().equals(paymentMeans)) {
                            sumCard = safeAdd(sumCard, sumPayment);
                        } else if (paymentMeansEpayObject != null && paymentMeansEpayObject.getValue().equals(paymentMeans)) {
                            sumCheck = safeAdd(sumCheck, sumPayment);
                        } else if (paymentMeansSalaryObject != null && paymentMeansSalaryObject.getValue().equals(paymentMeans)) {
                            sumSalary = safeAdd(sumSalary, sumPayment);
                        } else if (giftCardLM != null) {
                            sumGiftCard = safeAdd(sumGiftCard, sumPayment);
                        } else
                            sumDisc = safeAdd(sumDisc, sumPayment);
                    }
                }

                KeyExpr receiptDetailExpr = new KeyExpr("receiptDetail");
                ImRevMap<Object, KeyExpr> receiptDetailKeys = MapFact.singletonRev("receiptDetail", receiptDetailExpr);

                QueryBuilder<Object, Object> receiptDetailQuery = new QueryBuilder<>(receiptDetailKeys);
                String[] receiptDetailNames = new String[]{"typeReceiptDetail", "nameSkuReceiptDetail",
                        "quantityReceiptDetail", "quantityReceiptSaleDetail", "quantityReceiptReturnDetail",
                        "priceReceiptDetail", "idBarcodeReceiptDetail", "sumReceiptDetail",
                        "discountPercentReceiptSaleDetail", "discountSumReceiptDetail", "numberVATReceiptDetail",
                        "skuReceiptDetail", "boardNameSkuReceiptDetail"};
                LP<?>[] receiptDetailProperties = findProperties("type[ReceiptDetail]", "nameSku[ReceiptDetail]",
                        "quantity[ReceiptDetail]", "quantity[ReceiptSaleDetail]", "quantity[ReceiptReturnDetail]",
                        "price[ReceiptDetail]", "idBarcode[ReceiptDetail]", "sum[ReceiptDetail]",
                        "discountPercent[ReceiptSaleDetail]", "discountSum[ReceiptDetail]", "numberVAT[ReceiptDetail]",
                        "sku[ReceiptDetail]", "boardNameSku[ReceiptDetail]");
                for (int j = 0; j < receiptDetailProperties.length; j++) {
                    receiptDetailQuery.addProperty(receiptDetailNames[j], receiptDetailProperties[j].getExpr(context.getModifier(), receiptDetailExpr));
                }
                if (cashRegisterTaxLM != null) {
                    receiptDetailQuery.addProperty("numberSection", cashRegisterTaxLM.findProperty("numberSectionSento[ReceiptDetail]").getExpr(context.getModifier(), receiptDetailExpr));
                }
                receiptDetailQuery.and(findProperty("receipt[ReceiptDetail]").getExpr(context.getModifier(), receiptDetailQuery.getMapExprs().get("receiptDetail")).compare(receiptObject.getExpr(), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> receiptDetailResult = receiptDetailQuery.execute(context);
                List<ReceiptItem> receiptSaleItemList = new ArrayList<>();
                List<ReceiptItem> receiptReturnItemList = new ArrayList<>();
                for (ImMap<Object, Object> receiptDetailValues : receiptDetailResult.valueIt()) {
                    String typeReceiptDetail = (String) receiptDetailValues.get("typeReceiptDetail");
                    Boolean isGiftCard = typeReceiptDetail != null && typeReceiptDetail.equals("Сертификат");

                    BigDecimal price = (BigDecimal) receiptDetailValues.get("priceReceiptDetail");
                    double quantity = getDouble((BigDecimal) receiptDetailValues.get("quantityReceiptDetail"));
                    double quantitySale = getDouble((BigDecimal) receiptDetailValues.get("quantityReceiptSaleDetail"));
                    double quantityReturn = getDouble((BigDecimal) receiptDetailValues.get("quantityReceiptReturnDetail"));
                    String barcode = (String) receiptDetailValues.get("idBarcodeReceiptDetail");
                    if(barcode == null)
                        barcode = String.valueOf(receiptDetailValues.get("skuReceiptDetail"));
                    String boardName = (String) receiptDetailValues.get("boardNameSkuReceiptDetail");
                    String name = boardName != null ? boardName : (String) receiptDetailValues.get("nameSkuReceiptDetail");
                    name = name == null ? "" : name.trim();
                    BigDecimal sumReceiptDetailValue = (BigDecimal) receiptDetailValues.get("sumReceiptDetail");
                    double sumReceiptDetail = sumReceiptDetailValue == null ? 0 : sumReceiptDetailValue.doubleValue();
                    BigDecimal discountSumReceiptDetailValue = (BigDecimal) receiptDetailValues.get("discountSumReceiptDetail");
                    double discountSumReceiptDetail = discountSumReceiptDetailValue == null ? 0 : discountSumReceiptDetailValue.negate().doubleValue();
                    String numberSection = (String) receiptDetailValues.get("numberSection");
                    if (quantitySale > 0 && !isGiftCard)
                        receiptSaleItemList.add(new ReceiptItem(isGiftCard, price, quantitySale, barcode, name, sumReceiptDetail, discountSumReceiptDetail, numberSection));
                    if (quantity > 0 && isGiftCard)
                        receiptSaleItemList.add(new ReceiptItem(isGiftCard, price, quantity, barcode, "Подарочный сертификат", sumReceiptDetail, discountSumReceiptDetail, numberSection));
                    if (quantityReturn > 0)
                        receiptReturnItemList.add(new ReceiptItem(isGiftCard, price, quantityReturn, barcode, name, sumReceiptDetail, discountSumReceiptDetail, numberSection));
                }

                if (context.checkApply()) {
                    Object result = context.requestUserInteraction(new FiscalSentoPrintReceiptClientAction(false, logPath, comPort, baudRate,
                            new ReceiptInstance(sumDisc, sumCard, sumCash, sumCheck, sumSalary,
                            sumGiftCard == null ? null : sumGiftCard.abs(), sumTotal, numberDiscountCard, receiptSaleItemList, receiptReturnItemList),
                            fiscalSentoReceiptTop, fiscalSentoReceiptBottom, giftCardDepartment));
                    if (result instanceof Integer) {
                        findProperty("number[Receipt]").change((Integer)result, context, receiptObject);
                        if (context.apply())
                            findAction("createCurrentReceipt[]").execute(context);
                        else
                            ServerLoggers.systemLogger.error("FiscalSentoPrintReceipt Apply Error");
                    } else {
                        ServerLoggers.systemLogger.error("FiscalSentoPrintReceipt Error: " + result);
                        context.requestUserInteraction(new MessageClientAction((String) result, "Ошибка"));
                    }
                }
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }

    protected BigDecimal safeAdd(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else return (operand1 == null ? operand2 : (operand2 == null ? operand1 : operand1.add(operand2)));
    }

    private double getDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }
}
