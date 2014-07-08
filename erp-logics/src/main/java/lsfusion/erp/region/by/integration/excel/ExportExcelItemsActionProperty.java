package lsfusion.erp.region.by.integration.excel;

import com.google.common.base.Throwables;
import jxl.write.WriteException;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.server.classes.ConcreteClass;
import lsfusion.server.classes.DateClass;
import lsfusion.server.classes.StringClass;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ExportExcelItemsActionProperty extends ExportExcelActionProperty {

    public ExportExcelItemsActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public Map<String, byte[]> createFile(ExecutionContext<ClassPropertyInterface> context) throws IOException, WriteException {
        return createFile("exportItems", getTitles(), getRows(context));

    }

    private List<String> getTitles() {
        return Arrays.asList("Код товара", "Код группы", "Наименование", "Ед.изм.", "Краткая ед.изм.",
                "Код ед.изм.", "Название бренда", "Код бренда", "Страна", "Штрихкод", "Весовой",
                "Вес нетто", "Вес брутто", "Состав", "НДС, %", "Код посуды", "Цена посуды", "НДС посуды, %",
                "Код нормы отходов", "Оптовая наценка", "Розничная наценка", "Кол-во в упаковке (закупка)",
                "Кол-во в упаковке (продажа)");
    }

    private List<List<String>> getRows(ExecutionContext<ClassPropertyInterface> context) {

        ScriptingLogicsModule wareItemLM = context.getBL().getModule("WareItemLM");
        ScriptingLogicsModule writeOffRateItemLM = context.getBL().getModule("WriteOffPurchaseItem");
        ScriptingLogicsModule salePackLM = context.getBL().getModule("SalePack");
        
        List<List<String>> data = new ArrayList<List<String>>();

        DataSession session = context.getSession();

        try {
            ObjectValue retailCPLT = getLCP("idCalcPriceListType").readClasses(session, new DataObject("retail", StringClass.get(100)));
            ObjectValue wholesaleCPLT = getLCP("idCalcPriceListType").readClasses(session, new DataObject("wholesale", StringClass.get(100)));

            KeyExpr itemExpr = new KeyExpr("Item");
            ImRevMap<Object, KeyExpr> itemKeys = MapFact.singletonRev((Object) "Item", itemExpr);

            QueryBuilder<Object, Object> itemQuery = new QueryBuilder<Object, Object>(itemKeys);
            String[] itemNames = new String[]{"itemGroupItem", "nameAttributeItem", "UOMItem",
                    "brandItem", "countryItem", "idBarcodeSku", "isWeightItem", "netWeightItem", "grossWeightItem",
                    "compositionItem", "Purchase.amountPackSku"};
            LCP[] itemProperties = getLCPs("itemGroupItem", "nameAttributeItem", "UOMItem",
                    "brandItem", "countryItem", "idBarcodeSku", "isWeightItem", "netWeightItem", "grossWeightItem",
                    "compositionItem", "Purchase.amountPackSku");
            for (int i = 0; i < itemProperties.length; i++) {
                itemQuery.addProperty(itemNames[i], itemProperties[i].getExpr(context.getModifier(), itemExpr));
            }
            if(salePackLM != null) {
                itemQuery.addProperty("Sale.amountPackSku", salePackLM.findLCPByCompoundOldName("Sale.amountPackSku").getExpr(context.getModifier(), itemExpr)); 
            }

            if (wareItemLM != null) {
                String[] wareItemNames = new String[]{"wareItem"};
                LCP[] wareItemProperties = getLCPs("wareItem");
                for (int i = 0; i < wareItemProperties.length; i++) {
                    itemQuery.addProperty(wareItemNames[i], wareItemProperties[i].getExpr(context.getModifier(), itemExpr));
                }
            }

            itemQuery.and(getLCP("nameAttributeItem").getExpr(context.getModifier(), itemQuery.getMapExprs().get("Item")).getWhere());

            ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> itemResult = itemQuery.executeClasses(session);

            for (int i = 0, size = itemResult.size(); i < size; i++) {

                ImMap<Object, ObjectValue> itemValue = itemResult.getValue(i);

                Integer itemID = (Integer) itemResult.getKey(i).get("Item").getValue();
                String name = trim((String) itemValue.get("nameAttributeItem").getValue(), "");
                String idBarcodeSku = trim((String) itemValue.get("idBarcodeSku").getValue(), "");
                String isWeightItem = itemValue.get("idBarcodeSku").getValue() != null ? "True" : "False";
                BigDecimal netWeightItem = (BigDecimal) itemValue.get("netWeightItem").getValue();
                BigDecimal grossWeightItem = (BigDecimal) itemValue.get("grossWeightItem").getValue();
                String compositionItem = trim((String) itemValue.get("compositionItem").getValue(), "");
                BigDecimal purchaseAmount = (BigDecimal) itemValue.get("Purchase.amountPackSku").getValue();
                BigDecimal saleAmount = (BigDecimal) itemValue.get("Sale.amountPackSku").getValue();
                Integer itemGroupID = (Integer) itemValue.get("itemGroupItem").getValue();

                ObjectValue uomItemObject = itemValue.get("UOMItem");
                String nameUOM = trim((String) getLCP("nameUOM").read(session, uomItemObject), "");
                String shortNameUOM = trim((String) getLCP("shortNameUOM").read(session, uomItemObject), "");

                ObjectValue brandItemObject = itemValue.get("brandItem");
                String nameBrand = trim((String) getLCP("nameBrand").read(session, brandItemObject), "");

                ObjectValue wareItemObject = itemValue.get("wareItem");
                BigDecimal priceWare = (BigDecimal) getLCP("warePrice").read(session, wareItemObject);
                BigDecimal vatWare = (BigDecimal) getLCP("valueCurrentRateRangeWare").read(session, wareItemObject);

                DataObject itemObject = itemResult.getKey(i).get("Item");
                ObjectValue countryItemObject = itemValue.get("countryItem");
                DataObject dateObject = new DataObject(new Date(System.currentTimeMillis()), DateClass.instance);
                BigDecimal vatItem = (BigDecimal) getLCP("valueVATItemCountryDate").read(session, itemObject, countryItemObject, dateObject);
                String nameCountry = trim((String) getLCP("nameCountry").read(session, countryItemObject), "");

                Integer writeOffRateID = writeOffRateItemLM == null ? null : (Integer) writeOffRateItemLM.findLCPByCompoundOldName("writeOffRateCountryItem").read(session, countryItemObject, itemObject);

                BigDecimal retailMarkup = (BigDecimal) getLCP("markupCalcPriceListTypeSku").read(session, retailCPLT, itemObject);
                BigDecimal wholesaleMarkup = (BigDecimal) getLCP("markupCalcPriceListTypeSku").read(session, wholesaleCPLT, itemObject);

                data.add(Arrays.asList(formatValue(itemID), formatValue(itemGroupID), name, nameUOM, shortNameUOM, 
                        formatValue(uomItemObject.getValue()), nameBrand, formatValue(brandItemObject.getValue()), 
                        nameCountry, idBarcodeSku, isWeightItem, formatValue(netWeightItem), formatValue(grossWeightItem),
                        compositionItem, formatValue(vatItem), formatValue(wareItemObject.getValue()), formatValue(priceWare), 
                        formatValue(vatWare), formatValue(writeOffRateID), formatValue(retailMarkup), formatValue(wholesaleMarkup),
                        formatValue(purchaseAmount), formatValue(saleAmount)));
            }

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }

        return data;
    }
}