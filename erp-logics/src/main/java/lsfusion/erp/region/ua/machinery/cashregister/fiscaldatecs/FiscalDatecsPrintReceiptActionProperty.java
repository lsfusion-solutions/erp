package lsfusion.erp.region.ua.machinery.cashregister.fiscaldatecs;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.form.property.Compare;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FiscalDatecsPrintReceiptActionProperty extends InternalAction {
    private final ClassPropertyInterface receiptInterface;

    public FiscalDatecsPrintReceiptActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataObject receiptObject = context.getDataKeyValue(receiptInterface);

        try {

            boolean skipReceipt = findProperty("fiscalSkip[Receipt]").read(context.getSession(), receiptObject) != null;
            if (skipReceipt) {
                context.apply();
                findAction("createCurrentReceipt[]").execute(context);
            } else {
                Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context);
                Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);
                Integer placeNumber = (Integer) findProperty("nppMachineryCurrentCashRegister[]").read(context);
                ObjectValue userObject = findProperty("employee[Receipt]").readClasses(context, receiptObject);
                Object operatorNumber = userObject.isNull() ? 0 : findProperty("operatorNumberCurrentCashRegister[CustomUser]").read(context, (DataObject) userObject);
                Double sumTotal = (Double) findProperty("sumReceiptDetail[Receipt]").read(context, receiptObject);
                Double sumDisc = (Double) findProperty("discountSumReceiptDetail[Receipt]").read(context, receiptObject);
                Double sumCard = null;
                Double sumCash = null;

                KeyExpr paymentExpr = new KeyExpr("payment");
                ImRevMap<Object, KeyExpr> paymentKeys = MapFact.singletonRev((Object) "payment", paymentExpr);

                QueryBuilder<Object, Object> paymentQuery = new QueryBuilder<>(paymentKeys);
                paymentQuery.addProperty("sumPayment", findProperty("sum[Payment]").getExpr(context.getModifier(), paymentExpr));
                paymentQuery.addProperty("paymentMeansPayment", findProperty("paymentMeans[Payment]").getExpr(context.getModifier(), paymentExpr));

                paymentQuery.and(findProperty("receipt[Payment]").getExpr(context.getModifier(), paymentQuery.getMapExprs().get("payment")).compare(receiptObject.getExpr(), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> paymentResult = paymentQuery.execute(context);
                for (ImMap<Object, Object> paymentValues : paymentResult.valueIt()) {
                    DataObject paymentMeansCashObject = ((ConcreteCustomClass) findClass("PaymentMeans")).getDataObject("paymentMeansCash");
                    DataObject paymentMeansCardObject = ((ConcreteCustomClass) findClass("PaymentMeans")).getDataObject("paymentMeansCard");
                    if (paymentMeansCashObject.getValue().equals(paymentValues.get("paymentMeansPayment"))) {
                        sumCash = (Double) paymentValues.get("sumPayment");
                    } else if (paymentMeansCardObject.getValue().equals(paymentValues.get("paymentMeansPayment"))) {
                        sumCard = (Double) paymentValues.get("sumPayment");
                    }
                }

                KeyExpr receiptDetailExpr = new KeyExpr("receiptDetail");
                ImRevMap<Object, KeyExpr> receiptDetailKeys = MapFact.singletonRev((Object) "receiptDetail", receiptDetailExpr);

                QueryBuilder<Object, Object> receiptDetailQuery = new QueryBuilder<>(receiptDetailKeys);
                receiptDetailQuery.addProperty("nameSkuReceiptDetail", findProperty("nameSku[ReceiptDetail]").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("quantityReceiptSaleDetail", findProperty("quantity[ReceiptSaleDetail]").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("quantityReceiptReturnDetail", findProperty("quantity[ReceiptReturnDetail]").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("priceReceiptDetail", findProperty("price[ReceiptDetail]").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("idBarcodeReceiptDetail", findProperty("idBarcode[ReceiptDetail]").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("sumReceiptDetail", findProperty("sum[ReceiptDetail]").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("discountPercentReceiptSaleDetail", findProperty("discountPercent[ReceiptSaleDetail]").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("discountSumReceiptDetail", findProperty("discountSum[ReceiptDetail]").getExpr(context.getModifier(), receiptDetailExpr));
                receiptDetailQuery.addProperty("numberVATReceiptDetail", findProperty("numberVAT[ReceiptDetail]").getExpr(context.getModifier(), receiptDetailExpr));

                receiptDetailQuery.and(findProperty("receipt[ReceiptDetail]").getExpr(context.getModifier(), receiptDetailQuery.getMapExprs().get("receiptDetail")).compare(receiptObject.getExpr(), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> receiptDetailResult = receiptDetailQuery.execute(context);
                List<ReceiptItem> receiptSaleItemList = new ArrayList<>();
                List<ReceiptItem> receiptReturnItemList = new ArrayList<>();
                for (ImMap<Object, Object> receiptDetailValues : receiptDetailResult.valueIt()) {
                    Double price = (Double) receiptDetailValues.get("priceReceiptDetail");
                    Double quantitySale = (Double) receiptDetailValues.get("quantityReceiptSaleDetail");
                    Double quantityReturn = (Double) receiptDetailValues.get("quantityReceiptReturnDetail");
                    String barcode = (String) receiptDetailValues.get("idBarcodeReceiptDetail");
                    String name = (String) receiptDetailValues.get("nameSkuReceiptDetail");
                    Double sumReceiptDetail = (Double) receiptDetailValues.get("sumReceiptDetail");
                    Double discountPercentReceiptSaleDetail = (Double) receiptDetailValues.get("discountPercentReceiptSaleDetail");
                    Double discountSumReceiptDetail = (Double) receiptDetailValues.get("discountSumReceiptDetail");
                    Integer taxNumber = (Integer) receiptDetailValues.get("numberVATReceiptDetail");
                    if (quantitySale != null)
                        receiptSaleItemList.add(new ReceiptItem(price, quantitySale, barcode, name.trim(), sumReceiptDetail,
                                discountPercentReceiptSaleDetail, discountSumReceiptDetail == null ? null : -discountSumReceiptDetail, taxNumber, 1));
                    if (quantityReturn != null)
                        receiptReturnItemList.add(new ReceiptItem(price, quantityReturn, barcode, name.trim(), sumReceiptDetail,
                                discountPercentReceiptSaleDetail, discountSumReceiptDetail == null ? null : -discountSumReceiptDetail, taxNumber, 1));
                }

                if (context.checkApply()) {
                    String result = (String) context.requestUserInteraction(new FiscalDatecsPrintReceiptClientAction(baudRate, comPort, placeNumber, operatorNumber == null ? 1 : (Integer) operatorNumber, new ReceiptInstance(sumDisc, sumCard, sumCash, sumTotal, receiptSaleItemList, receiptReturnItemList)));
                    if (result == null) {
                        context.apply();
                        findAction("createCurrentReceipt[]").execute(context);
                    } else
                        context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
                }
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }


    }
}
