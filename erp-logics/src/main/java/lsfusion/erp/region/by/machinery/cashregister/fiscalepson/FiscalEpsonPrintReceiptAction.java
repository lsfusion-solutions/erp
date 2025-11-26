package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.DefaultIntegrationAction;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

public class FiscalEpsonPrintReceiptAction extends DefaultIntegrationAction {
    private final ClassPropertyInterface receiptInterface;

    public FiscalEpsonPrintReceiptAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
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

                Integer cardType = (Integer) findProperty("cardTypeCurrentCashRegister[]").read(context);
                Integer giftCardType = (Integer) findProperty("giftCardTypeCurrentCashRegister[]").read(context);

                boolean sendSKNO = findProperty("sendSKNOCurrentCashRegister[]").read(context) != null;
                boolean resetTypeOfGoods = findProperty("resetTypeOfGoods[]").read(context) != null;
                boolean version116 = findProperty("version116CurrentCashRegister[]").read(context) != null;

                String cashier = trim((String) findProperty("currentUserName[]").read(context));
                String comment = (String) findProperty("fiscalEpsonComment[Receipt]").read(context, receiptObject);

                TreeMap<Integer, BigDecimal> paymentSumMap = new TreeMap<>();
                BigDecimal sumCard = null;
                BigDecimal sumCash = null;
                BigDecimal sumGiftCard = null;

                KeyExpr paymentExpr = new KeyExpr("payment");
                ImRevMap<Object, KeyExpr> paymentKeys = MapFact.singletonRev("payment", paymentExpr);

                QueryBuilder<Object, Object> paymentQuery = new QueryBuilder<>(paymentKeys);
                paymentQuery.addProperty("sumPayment", findProperty("sum[Payment]").getExpr(context.getModifier(), paymentExpr));
                paymentQuery.addProperty("paymentMeansPayment", findProperty("paymentMeans[Payment]").getExpr(context.getModifier(), paymentExpr));
                paymentQuery.addProperty("idTypeRegister", findProperty("idTypeRegister[Payment]").getExpr(context.getModifier(), paymentExpr));
                paymentQuery.and(findProperty("receipt[Payment]").getExpr(context.getModifier(), paymentQuery.getMapExprs().get("payment")).compare(receiptObject.getExpr(), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> paymentResult = paymentQuery.execute(context);
                for (ImMap<Object, Object> paymentValues : paymentResult.valueIt()) {
                    DataObject paymentMeansCashObject = ((ConcreteCustomClass) findClass("PaymentMeans")).getDataObject("paymentMeansCash");
                    DataObject paymentMeansCardObject = ((ConcreteCustomClass) findClass("PaymentMeans")).getDataObject("paymentMeansCard");
                    DataObject paymentMeansGiftCardObject = giftCardLM == null ? null : ((ConcreteCustomClass) giftCardLM.findClass("PaymentMeans")).getDataObject("paymentMeansGiftCard");
                    Integer idTypeRegister = (Integer) paymentValues.get("idTypeRegister");
                    BigDecimal sumPayment = (BigDecimal) paymentValues.get("sumPayment");
                    if (paymentMeansCashObject.getValue().equals(paymentValues.get("paymentMeansPayment"))) {
                        sumCash = sumCash == null ? sumPayment : sumCash.add(sumPayment);
                    } else if (paymentMeansCardObject.getValue().equals(paymentValues.get("paymentMeansPayment"))) {
                        sumCard = sumCard == null ? sumPayment : sumCard.add(sumPayment);
                    } else if (giftCardLM != null && paymentMeansGiftCardObject.getValue().equals(paymentValues.get("paymentMeansPayment"))) {
                        sumGiftCard = sumGiftCard == null ? sumPayment : sumGiftCard.add(sumPayment);
                    } else if (idTypeRegister != null) {
                        BigDecimal sum = paymentSumMap.get(idTypeRegister);
                        paymentSumMap.put(idTypeRegister, safeAdd(sum, sumPayment));
                    }
                }

                KeyExpr receiptDetailExpr = new KeyExpr("receiptDetail");
                ImRevMap<Object, KeyExpr> receiptDetailKeys = MapFact.singletonRev("receiptDetail", receiptDetailExpr);

                QueryBuilder<Object, Object> receiptDetailQuery = new QueryBuilder<>(receiptDetailKeys);
                String[] rdNames = new String[]{"nameSkuReceiptDetail", "typeReceiptDetail", "isCommission",
                        "quantityReceiptDetail", "quantityReceiptSaleDetail", "quantityReceiptReturnDetail", "priceReceiptDetail",
                        "idBarcodeReceiptDetail", "sumReceiptDetail", "discountSumReceiptDetail", "bonusPaidReceiptDetail", "valueVATReceiptDetail",
                        "calcSumVATReceiptDetail", "idSectionReceiptDetail", "commentReceiptDetail",
                        "epsonSkuTypeReceiptDetail","epsonIdLotReceiptDetail","epsonTailLotReceiptDetail","numberVATReceiptDetail"};
                LP<?>[] rdProperties = findProperties("nameSku[ReceiptDetail]", "type[ReceiptDetail]", "isCommission[ReceiptDetail]",
                        "quantity[ReceiptDetail]", "quantity[ReceiptSaleDetail]", "quantity[ReceiptReturnDetail]", "price[ReceiptDetail]",
                        "idBarcode[ReceiptDetail]", "sum[ReceiptDetail]", "discountSum[ReceiptDetail]", "bonusPaid[ReceiptDetail]", "valueVAT[ReceiptDetail]",
                        "calcSumVAT[ReceiptDetail]", "idSection[ReceiptDetail]", "fiscalEpsonComment[ReceiptDetail]",
                        "epsonSkuType[ReceiptDetail]","epsonIdLot[ReceiptDetail]","epsonTailLot[ReceiptDetail]","numberVAT[ReceiptDetail]");
                for (int i = 0; i < rdProperties.length; i++) {
                    receiptDetailQuery.addProperty(rdNames[i], rdProperties[i].getExpr(context.getModifier(), receiptDetailExpr));
                }
                receiptDetailQuery.and(findProperty("receipt[ReceiptDetail]").getExpr(context.getModifier(), receiptDetailQuery.getMapExprs().get("receiptDetail")).compare(receiptObject.getExpr(), Compare.EQUALS));

                addCustomProperties(context, receiptDetailQuery, receiptDetailExpr);

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> receiptDetailResult = receiptDetailQuery.execute(context);
                List<ReceiptItem> receiptSaleItemList = new ArrayList<>();
                List<ReceiptItem> receiptReturnItemList = new ArrayList<>();
                for (ImMap<Object, Object> receiptDetailValues : receiptDetailResult.valueIt()) {
                    BigDecimal price = (BigDecimal) receiptDetailValues.get("priceReceiptDetail");
                    BigDecimal quantitySale = (BigDecimal) receiptDetailValues.get("quantityReceiptSaleDetail");
                    BigDecimal quantityReturn = (BigDecimal) receiptDetailValues.get("quantityReceiptReturnDetail");
                    BigDecimal quantity = (BigDecimal) receiptDetailValues.get("quantityReceiptDetail");

                    //properties from POSPharm.lsf
                    boolean useBlisters = receiptDetailValues.get("useBlisterInFiscalPrint") != null;
                    BigDecimal blisterPrice = (BigDecimal) receiptDetailValues.get("blisterPriceReceiptDetail");
                    BigDecimal blisterQuantity = (BigDecimal) receiptDetailValues.get("blisterQuantityReceiptDetail");

                    String barcode = (String) receiptDetailValues.get("idBarcodeReceiptDetail");
                    barcode = barcode == null ? null : barcode.trim();
                    String name = (String) receiptDetailValues.get("nameSkuReceiptDetail");
                    name = name == null ? null : name.trim();
                    BigDecimal sumReceiptDetail = (BigDecimal) receiptDetailValues.get("sumReceiptDetail");
                    BigDecimal discountSumReceiptDetail = (BigDecimal) receiptDetailValues.get("discountSumReceiptDetail");
                    if (!version116) {
                        discountSumReceiptDetail = discountSumReceiptDetail == null ? null : discountSumReceiptDetail.negate();
                    }
                    BigDecimal bonusPaid = (BigDecimal) receiptDetailValues.get("bonusPaidReceiptDetail");
                    String typeReceiptDetail = (String) receiptDetailValues.get("typeReceiptDetail");
                    boolean isGiftCard = typeReceiptDetail != null && typeReceiptDetail.equals("Сертификат");
                    boolean isCommission = receiptDetailValues.get("isCommission") != null;
                    Integer section = parseInt((String) receiptDetailValues.get("idSectionReceiptDetail"));

                    BigDecimal valueVAT = (BigDecimal) receiptDetailValues.get("valueVATReceiptDetail");
                    BigDecimal calcSumVAT = (BigDecimal) receiptDetailValues.get("calcSumVATReceiptDetail");
                    String vatString = valueVAT == null || calcSumVAT == null ? null : String.format("НДС: %s (%s%%)", formatSumVAT(calcSumVAT), formatValueVAT(valueVAT));
                    String commentDetail = (String) receiptDetailValues.get("commentReceiptDetail");

                    String idLot = (String) receiptDetailValues.get("epsonIdLotReceiptDetail");
                    idLot = idLot == null ? null : idLot.trim();
                    String tailLot = (String) receiptDetailValues.get("epsonTailLotReceiptDetail");
                    tailLot = tailLot == null ? null : tailLot.trim();
                    Integer skuType = (Integer) receiptDetailValues.get("epsonSkuTypeReceiptDetail");
                    Integer numberVAT = (Integer) receiptDetailValues.get("numberVATReceiptDetail");

                    if (quantitySale != null && !isGiftCard)
                        receiptSaleItemList.add(new ReceiptItem(false, isCommission, price, quantitySale, useBlisters, blisterPrice, blisterQuantity, barcode, name,
                                sumReceiptDetail, discountSumReceiptDetail, bonusPaid, vatString, section, commentDetail, skuType, idLot, tailLot, numberVAT));
                    if (quantity != null && isGiftCard) {
                        receiptSaleItemList.add(new ReceiptItem(true, isCommission, price, quantity, useBlisters, blisterPrice, blisterQuantity, barcode, "Подарочный сертификат",
                                sumReceiptDetail, discountSumReceiptDetail, bonusPaid, vatString, section, commentDetail, skuType, idLot, tailLot, numberVAT));
                    }
                    if (quantityReturn != null) {
                        BigDecimal discount = discountSumReceiptDetail;
                        if (!version116) {
                            discount = discountSumReceiptDetail == null ? BigDecimal.ZERO : discountSumReceiptDetail.divide(quantityReturn, 2, RoundingMode.UP);
                        }
                        receiptReturnItemList.add(new ReceiptItem(isGiftCard, isCommission, price, quantityReturn, useBlisters, blisterPrice, blisterQuantity, barcode,
                                name, sumReceiptDetail, discount, bonusPaid, vatString, section, commentDetail, skuType, idLot, tailLot, numberVAT));
                    }
                }

                if (!receiptSaleItemList.isEmpty() && !receiptReturnItemList.isEmpty())
                    messageClientAction(context,"В чеке обнаружены одновременно продажа и возврат", "Ошибка");
                else {
                    if (context.checkApply()) {
                        Boolean isReturn = !receiptReturnItemList.isEmpty();
                        PrintReceiptResult result = (PrintReceiptResult) context.requestUserInteraction(
                                new FiscalEpsonPrintReceiptClientAction(comPort, baudRate, isReturn,
                                        new ReceiptInstance(paymentSumMap, sumCash == null ? null : sumCash.abs(),
                                                sumCard == null ? null : sumCard.abs(),
                                                sumGiftCard == null ? null : sumGiftCard.abs(), cashier,
                                                isReturn ? receiptReturnItemList : receiptSaleItemList, comment), cardType, giftCardType,
                                        sendSKNO, resetTypeOfGoods, version116));
                        if (result.receiptNumber != null) {
                            findProperty("number[Receipt]").change(result.receiptNumber, context, receiptObject);
                            findProperty("documentNumber[Receipt]").change(result.documentNumber, context, receiptObject);
                            findProperty("fiscalEpsonElectronicJournalReadOffset[]").change(result.electronicJournalReadOffset, context);
                            findProperty("fiscalEpsonElectronicJournalReadSize[]").change(result.electronicJournalReadSize, context);
                            findProperty("fiscalEpsonSessionNumber[]").change(result.sessionNumber, context);
                            if (context.apply())
                                findAction("createCurrentReceipt[]").execute(context);
                            else
                                ServerLoggers.systemLogger.error("FiscalEpsonPrintReceipt Apply Error");
                        } else {
                            ServerLoggers.systemLogger.error("FiscalEpsonPrintReceipt Error: " + result.error);
                            messageClientAction(context, result.error, "Ошибка");
                        }
                    }
                }
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }

    protected void addCustomProperties(ExecutionContext<ClassPropertyInterface> context, QueryBuilder<Object, Object> receiptDetailQuery, KeyExpr receiptDetailExpr) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
    }

    private String formatSumVAT(BigDecimal value) {
        return new DecimalFormat("#,##0.00").format(value.doubleValue()).replace(".", ",");
    }

    private String formatValueVAT(BigDecimal value) {
        return new DecimalFormat("#,###.##").format(value.doubleValue()).replace(".", ",");
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return null;
        }
    }

    protected BigDecimal safeAdd(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else return (operand1 == null ? operand2 : (operand2 == null ? operand1 : operand1.add(operand2)));
    }
}