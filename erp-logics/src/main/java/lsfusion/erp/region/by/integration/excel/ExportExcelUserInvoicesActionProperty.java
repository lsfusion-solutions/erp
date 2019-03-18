package lsfusion.erp.region.by.integration.excel;

import com.google.common.base.Throwables;
import jxl.write.WriteException;
import lsfusion.base.Pair;
import lsfusion.base.file.RawFileData;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.classes.data.time.DateClass;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.builder.QueryBuilder;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.session.DataSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

public class ExportExcelUserInvoicesActionProperty extends ExportExcelActionProperty {
    private final ClassPropertyInterface dateFromInterface;
    private final ClassPropertyInterface dateToInterface;

    public ExportExcelUserInvoicesActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        dateFromInterface = i.next();
        dateToInterface = i.next();
    }

    public ExportExcelUserInvoicesActionProperty(ScriptingLogicsModule LM, ClassPropertyInterface dateFrom, ClassPropertyInterface dateTo) {
        super(LM, DateClass.instance, DateClass.instance);

        dateFromInterface = dateFrom;
        dateToInterface = dateTo;
    }

    @Override
    public Pair<String, RawFileData> createFile(ExecutionContext<ClassPropertyInterface> context) throws IOException, WriteException {
        return Pair.create("exportUserInvoices", createFile(getTitles(), getRows(context)));

    }

    private List<String> getTitles() {
        return Arrays.asList("Серия", "Номер", "Дата", "Код товара", "Кол-во", "Поставщик", "Склад покупателя",
                "Склад поставщика", "Цена", "Цена услуг", "Розничная цена", "Розничная надбавка",
                "Оптовая цена", "Оптовая надбавка", "Сертификат");
    }

    private List<List<String>> getRows(ExecutionContext<ClassPropertyInterface> context) {

        ScriptingLogicsModule pricingPurchaseLM = context.getBL().getModule("PricingPurchase");
        ScriptingLogicsModule purchaseInvoiceWholesaleLM = context.getBL().getModule("PurchaseInvoiceWholesalePrice");

        List<List<String>> data = new ArrayList<>();

        DataSession session = context.getSession();

        try {

            DataObject dateFromObject = context.getDataKeyValue(dateFromInterface);
            DataObject dateToObject = context.getDataKeyValue(dateToInterface);

            KeyExpr userInvoiceExpr = new KeyExpr("UserInvoice");
            ImRevMap<Object, KeyExpr> userInvoiceKeys = MapFact.singletonRev((Object) "UserInvoice", userInvoiceExpr);

            String[] userInvoiceNames = new String[]{"seriesUserInvoice", "numberUserInvoice",
                    "Purchase.dateUserInvoice", "supplierUserInvoice", "Purchase.customerStockInvoice", "Purchase.supplierStockInvoice"};
            LP[] userInvoiceProperties = findProperties("series[UserInvoice]", "number[UserInvoice]",
                    "date[UserInvoice]", "supplier[UserInvoice]", "customerStock[Purchase.Invoice]", "supplierStock[Purchase.Invoice]");
            QueryBuilder<Object, Object> userInvoiceQuery = new QueryBuilder<>(userInvoiceKeys);
            for (int j = 0; j < userInvoiceProperties.length; j++) {
                userInvoiceQuery.addProperty(userInvoiceNames[j], userInvoiceProperties[j].getExpr(context.getModifier(), userInvoiceExpr));
            }
            userInvoiceQuery.and(findProperty("number[UserInvoice]").getExpr(context.getModifier(), userInvoiceQuery.getMapExprs().get("UserInvoice")).getWhere());
            userInvoiceQuery.and(findProperty("date[UserInvoice]").getExpr(context.getModifier(), userInvoiceQuery.getMapExprs().get("UserInvoice")).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> userInvoiceResult = userInvoiceQuery.execute(session);

            for (int i = 0, size = userInvoiceResult.size(); i < size; i++) {
                DataObject userInvoiceObject = new DataObject((Long)userInvoiceResult.getKey(i).get("UserInvoice"), (ConcreteCustomClass) findClass("UserInvoice"));

                Date date = (Date) userInvoiceResult.getValue(i).get("Purchase.dateUserInvoice");

                if ((dateFromObject == null || date.after((Date) dateFromObject.object)) && (dateToObject == null || date.before((Date) dateToObject.object))) {
                    ImMap<Object, Object> userInvoiceValue = userInvoiceResult.getValue(i);

                    String seriesUserInvoice = trim((String) userInvoiceValue.get("seriesUserInvoice"), "");
                    String numberUserInvoice = trim((String) userInvoiceValue.get("numberUserInvoice"), "");
                    String dateInvoice = date == null ? null : new SimpleDateFormat("dd.MM.yyyy").format(date);

                    Long supplierID = (Long) userInvoiceValue.get("supplierUserInvoice");
                    Long customerStockID = (Long) userInvoiceValue.get("Purchase.customerStockInvoice");
                    Long supplierStockID = (Long) userInvoiceValue.get("Purchase.supplierStockInvoice");

                    KeyExpr userInvoiceDetailExpr = new KeyExpr("UserInvoiceDetail");
                    ImRevMap<Object, KeyExpr> userInvoiceDetailKeys = MapFact.singletonRev((Object) "UserInvoiceDetail", userInvoiceDetailExpr);

                    QueryBuilder<Object, Object> userInvoiceDetailQuery = new QueryBuilder<>(userInvoiceDetailKeys);
                    String[] userInvoiceDetailNames = new String[]{"Purchase.idBarcodeSkuInvoiceDetail", "quantityUserInvoiceDetail",
                            "priceUserInvoiceDetail", "Purchase.chargePriceUserInvoiceDetail", "certificateTextInvoiceDetail"};
                    LP<?>[] userInvoiceDetailProperties = findProperties("idBarcodeSku[Purchase.InvoiceDetail]", "quantity[UserInvoiceDetail]",
                            "price[UserInvoiceDetail]", "chargePrice[UserInvoiceDetail]", "certificateText[Purchase.InvoiceDetail]");
                    for (int j = 0; j < userInvoiceDetailProperties.length; j++) {
                        userInvoiceDetailQuery.addProperty(userInvoiceDetailNames[j], userInvoiceDetailProperties[j].getExpr(context.getModifier(), userInvoiceDetailExpr));
                    }

                    if (purchaseInvoiceWholesaleLM != null) {
                        String[] purchaseInvoiceWholesaleUserInvoiceDetailNames = new String[]{"Purchase.wholesalePriceUserInvoiceDetail", "Purchase.wholesaleMarkupUserInvoiceDetail"};
                        LP[] purchaseInvoiceWholesaleUserInvoiceDetailProperties = new LP[] {
                                purchaseInvoiceWholesaleLM.findProperty("wholesalePrice[UserInvoiceDetail]"), purchaseInvoiceWholesaleLM.findProperty("wholesaleMarkup[UserInvoiceDetail]")
                        };
                        for (int j = 0; j < purchaseInvoiceWholesaleUserInvoiceDetailProperties.length; j++) {
                            userInvoiceDetailQuery.addProperty(purchaseInvoiceWholesaleUserInvoiceDetailNames[j], purchaseInvoiceWholesaleUserInvoiceDetailProperties[j].getExpr(context.getModifier(), userInvoiceDetailExpr));
                        }
                    }

                    if (pricingPurchaseLM != null) {
                        String[] pricingPurchaseUserInvoiceDetailNames = new String[]{"Purchase.retailPriceUserInvoiceDetail", "Purchase.retailMarkupUserInvoiceDetail"};
                        LP[] pricingPurchaseUserInvoiceDetailProperties = new LP[]{
                                pricingPurchaseLM.findProperty("retailPrice[UserInvoiceDetail]"),
                                pricingPurchaseLM.findProperty("retailMarkup[UserInvoiceDetail]")};
                        for (int j = 0; j < pricingPurchaseUserInvoiceDetailProperties.length; j++) {
                            userInvoiceDetailQuery.addProperty(pricingPurchaseUserInvoiceDetailNames[j], pricingPurchaseUserInvoiceDetailProperties[j].getExpr(context.getModifier(), userInvoiceDetailExpr));
                        }
                    }

                    userInvoiceDetailQuery.and(findProperty("userInvoice[UserInvoiceDetail]").getExpr(context.getModifier(), userInvoiceDetailQuery.getMapExprs().get("UserInvoiceDetail")).compare(userInvoiceObject.getExpr(), Compare.EQUALS));

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> userInvoiceDetailResult = userInvoiceDetailQuery.execute(context);

                    for (ImMap<Object, Object> userInvoiceDetailValues : userInvoiceDetailResult.valueIt()) {

                        String idBarcodeSkuInvoiceDetail = trim((String) userInvoiceDetailValues.get("Purchase.idBarcodeSkuInvoiceDetail"), "");
                        BigDecimal quantityUserInvoiceDetail = (BigDecimal) userInvoiceDetailValues.get("quantityUserInvoiceDetail");
                        BigDecimal priceUserInvoiceDetail = (BigDecimal) userInvoiceDetailValues.get("priceUserInvoiceDetail");
                        BigDecimal chargePriceUserInvoiceDetail = (BigDecimal) userInvoiceDetailValues.get("Purchase.chargePriceUserInvoiceDetail");
                        BigDecimal retailPriceUserInvoiceDetail = (BigDecimal) userInvoiceDetailValues.get("Purchase.retailPriceUserInvoiceDetail");
                        BigDecimal retailMarkupUserInvoiceDetail = (BigDecimal) userInvoiceDetailValues.get("Purchase.retailMarkupUserInvoiceDetail");
                        BigDecimal wholesalePriceUserInvoiceDetail = (BigDecimal) userInvoiceDetailValues.get("Purchase.wholesalePriceUserInvoiceDetail");
                        BigDecimal wholesaleMarkupUserInvoiceDetail = (BigDecimal) userInvoiceDetailValues.get("Purchase.wholesaleMarkupUserInvoiceDetail");
                        String certificateTextInvoiceDetail = trim((String) userInvoiceDetailValues.get("certificateTextInvoiceDetail"), "");

                        data.add(Arrays.asList(seriesUserInvoice, numberUserInvoice, dateInvoice, idBarcodeSkuInvoiceDetail, 
                                formatValue(quantityUserInvoiceDetail), formatValue(supplierID), formatValue(customerStockID),
                                formatValue(supplierStockID), formatValue(priceUserInvoiceDetail), formatValue(chargePriceUserInvoiceDetail),
                                formatValue(retailPriceUserInvoiceDetail), formatValue(retailMarkupUserInvoiceDetail),
                                formatValue(wholesalePriceUserInvoiceDetail), formatValue(wholesaleMarkupUserInvoiceDetail),
                                certificateTextInvoiceDetail));
                    }
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }

        return data;
    }

    /*
            Arrays.asList(Arrays.asList("AA", "12345678", "12.12.2012", "1111", "150", "ПС0010325", "4444", "3333",
            "5000", "300", "7000", "30", "№123456789")));
            */



}