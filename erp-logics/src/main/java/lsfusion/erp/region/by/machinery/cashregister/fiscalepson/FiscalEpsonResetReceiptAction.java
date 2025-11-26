package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.DefaultIntegrationAction;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FiscalEpsonResetReceiptAction extends DefaultIntegrationAction {
    private final ClassPropertyInterface receiptInterface;

    public FiscalEpsonResetReceiptAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        receiptInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {
            DataObject receiptObject = context.getDataKeyValue(receiptInterface);

            boolean skipReceipt = findProperty("fiscalSkip[Receipt]").read(context, receiptObject) != null;
            if (!skipReceipt) {
                Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
                Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);

                Integer cardType = (Integer) findProperty("cardTypeCurrentCashRegister[]").read(context);
                Integer giftCardType = (Integer) findProperty("giftCardTypeCurrentCashRegister[]").read(context);

                boolean sendSKNO = findProperty("sendSKNOCurrentCashRegister[]").read(context) != null;
                boolean resetTypeOfGoods = findProperty("resetTypeOfGoods[]").read(context) != null;
                boolean version116 = findProperty("version116CurrentCashRegister[]").read(context) != null;

                String cashier = trim((String) findProperty("currentUserName[]").read(context));
                Integer numberReceipt = (Integer) findProperty("documentNumber[Receipt]").read(context, receiptObject);
                BigDecimal totalSum = (BigDecimal) findProperty("sumReceiptDetail[Receipt]").read(context, receiptObject);
                BigDecimal sumCash = (BigDecimal) findProperty("sumCashPayment[Receipt]").read(context, receiptObject);
                BigDecimal sumCard = (BigDecimal) findProperty("sumCardPayment[Receipt]").read(context, receiptObject);
                ScriptingLogicsModule giftCardLM = context.getBL().getModule("GiftCard");
                BigDecimal sumGiftCard = giftCardLM == null ? null : (BigDecimal) giftCardLM.findProperty("sumGiftCardPayment[Receipt]").read(context, receiptObject);

                String result = null;

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

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> receiptDetailResult = receiptDetailQuery.execute(context);
                    List<ReceiptItem> receiptSaleItemList = new ArrayList<>();
                    for (ImMap<Object, Object> receiptDetailValues : receiptDetailResult.valueIt()) {
                        BigDecimal price = (BigDecimal) receiptDetailValues.get("priceReceiptDetail");
                        BigDecimal quantitySale = (BigDecimal) receiptDetailValues.get("quantityReceiptSaleDetail");
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
                        BigDecimal bonusPaid = (BigDecimal) receiptDetailValues.get("bonusPaidReceiptDetail");
                        String typeReceiptDetail = (String) receiptDetailValues.get("typeReceiptDetail");
                        boolean isGiftCard = typeReceiptDetail != null && typeReceiptDetail.equals("Сертификат");
                        boolean isCommission = receiptDetailValues.get("isCommission") != null;
                        Integer section = parseInt((String) receiptDetailValues.get("idSectionReceiptDetail"));

                        BigDecimal valueVAT = (BigDecimal) receiptDetailValues.get("valueVATReceiptDetail");
                        BigDecimal calcSumVAT = (BigDecimal) receiptDetailValues.get("calcSumVATReceiptDetail");
                        String commentDetail = (String) receiptDetailValues.get("commentReceiptDetail");
                        String vatString = valueVAT == null || calcSumVAT == null ? null : String.format("НДС: %s (%s%%)", formatSumVAT(calcSumVAT), formatValueVAT(valueVAT));

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

                    }

                    result = (String) context.requestUserInteraction(new FiscalEpsonResetReceiptClientAction(comPort, baudRate, cashier, numberReceipt,
                            totalSum, sumCash, sumCard, sumGiftCard, cardType, giftCardType, version116,
                            new ReceiptInstance(null, sumCash, sumCard, sumGiftCard, cashier,receiptSaleItemList, null),
                            sendSKNO, resetTypeOfGoods));

                if (result == null) {
                    findProperty("resetted[Receipt]").change(true, context, receiptObject);
                    findProperty("dataSkip[Receipt]").change(true, context, receiptObject);
                    if (!context.apply())
                        messageClientAction(context,"Ошибка при аннулировании чека", "Ошибка");
                } else {
                    messageClientAction(context, result, "Ошибка");
                }
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String formatSumVAT(BigDecimal value) {
        return new DecimalFormat("#,##0.00").format(value.doubleValue()).replace(".", ",");
    }

    private String formatValueVAT(BigDecimal value) {
        return new DecimalFormat("#,###.##").format(value.doubleValue()).replace(".", ",");
    }

}