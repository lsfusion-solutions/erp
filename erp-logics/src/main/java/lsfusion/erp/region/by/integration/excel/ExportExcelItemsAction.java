package lsfusion.erp.region.by.integration.excel;

import com.google.common.base.Throwables;
import jxl.write.WriteException;
import lsfusion.base.Pair;
import lsfusion.base.file.RawFileData;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.server.logics.classes.data.time.DateClass;
import lsfusion.server.logics.classes.data.StringClass;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.session.DataSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.*;

public class ExportExcelItemsAction extends ExportExcelAction {

    public ExportExcelItemsAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public Pair<String, RawFileData> createFile(ExecutionContext<ClassPropertyInterface> context) throws IOException, WriteException {
        return Pair.create("exportItems", createFile(getTitles(), getRows(context)));

    }

    private List<String> getTitles() {
        return Arrays.asList("Код товара", "Код группы", "Наименование", "Ед.изм.", "Краткая ед.изм.",
                "Код ед.изм.", "Название бренда", "Код бренда", "Страна", "Штрихкод", "Весовой",
                "Вес нетто", "Вес брутто", "Состав", "НДС, %", "Код посуды", "Цена посуды", "НДС посуды, %",
                "Код нормы отходов", "Оптовая наценка", "Розничная наценка", "Кол-во в упаковке (закупка)",
                "Кол-во в упаковке (продажа)");
    }

    private List<List<String>> getRows(ExecutionContext<ClassPropertyInterface> context) {

        ScriptingLogicsModule wareItemLM = context.getBL().getModule("WareItem");
        ScriptingLogicsModule writeOffRateItemLM = context.getBL().getModule("WriteOffPurchaseItem");
        ScriptingLogicsModule salePackLM = context.getBL().getModule("SalePack");
        
        List<List<String>> data = new ArrayList<>();

        DataSession session = context.getSession();

        try {
            ObjectValue retailCPLT = findProperty("id[CalcPriceListType]").readClasses(session, new DataObject("retail", StringClass.get(100)));
            ObjectValue wholesaleCPLT = findProperty("id[CalcPriceListType]").readClasses(session, new DataObject("wholesale", StringClass.get(100)));

            KeyExpr itemExpr = new KeyExpr("Item");
            ImRevMap<Object, KeyExpr> itemKeys = MapFact.singletonRev((Object) "Item", itemExpr);

            QueryBuilder<Object, Object> itemQuery = new QueryBuilder<>(itemKeys);
            String[] itemNames = new String[]{"itemGroupItem", "nameAttributeItem", "UOMItem",
                    "brandItem", "countryItem", "idBarcodeSku", "splitItem", "netWeightItem", "grossWeightItem",
                    "compositionItem", "Purchase.amountPackSku"};
            LP[] itemProperties = findProperties("itemGroup[Item]", "nameAttribute[Item]", "UOM[Item]",
                    "brand[Item]", "country[Item]", "idBarcode[Sku]", "split[Item]", "netWeight[Item]", "grossWeight[Item]",
                    "composition[Item]", "amountPack[Sku]");
            for (int i = 0; i < itemProperties.length; i++) {
                itemQuery.addProperty(itemNames[i], itemProperties[i].getExpr(context.getModifier(), itemExpr));
            }
            if(salePackLM != null) {
                itemQuery.addProperty("Sale.amountPackSku", salePackLM.findProperty("amountPack[Sku]").getExpr(context.getModifier(), itemExpr));
            }

            if (wareItemLM != null) {
                String[] wareItemNames = new String[]{"wareItem"};
                LP[] wareItemProperties = findProperties("ware[Item]");
                for (int i = 0; i < wareItemProperties.length; i++) {
                    itemQuery.addProperty(wareItemNames[i], wareItemProperties[i].getExpr(context.getModifier(), itemExpr));
                }
            }

            itemQuery.and(findProperty("nameAttribute[Item]").getExpr(context.getModifier(), itemQuery.getMapExprs().get("Item")).getWhere());

            ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> itemResult = itemQuery.executeClasses(session);

            for (int i = 0, size = itemResult.size(); i < size; i++) {

                ImMap<Object, ObjectValue> itemValue = itemResult.getValue(i);

                Long itemID = (Long) itemResult.getKey(i).get("Item").getValue();
                String name = trim((String) itemValue.get("nameAttributeItem").getValue(), "");
                String idBarcodeSku = trim((String) itemValue.get("idBarcodeSku").getValue(), "");
                String isWeightItem = itemValue.get("idBarcodeSku").getValue() != null ? "True" : "False";
                BigDecimal netWeightItem = (BigDecimal) itemValue.get("netWeightItem").getValue();
                BigDecimal grossWeightItem = (BigDecimal) itemValue.get("grossWeightItem").getValue();
                String compositionItem = trim((String) itemValue.get("compositionItem").getValue(), "");
                BigDecimal purchaseAmount = (BigDecimal) itemValue.get("Purchase.amountPackSku").getValue();
                BigDecimal saleAmount = (BigDecimal) itemValue.get("Sale.amountPackSku").getValue();
                Long itemGroupID = (Long) itemValue.get("itemGroupItem").getValue();

                ObjectValue uomItemObject = itemValue.get("UOMItem");
                String nameUOM = trim((String) findProperty("name[UOM]").read(session, uomItemObject), "");
                String shortNameUOM = trim((String) findProperty("shortName[UOM]").read(session, uomItemObject), "");

                ObjectValue brandItemObject = itemValue.get("brandItem");
                String nameBrand = trim((String) findProperty("name[Brand]").read(session, brandItemObject), "");

                ObjectValue wareItemObject = itemValue.get("wareItem");
                ObjectValue countryItemObject = itemValue.get("countryItem");
                BigDecimal priceWare = wareItemObject == null ? null : (BigDecimal) findProperty("price[Ware]").read(session, wareItemObject);
                BigDecimal vatWare = wareItemObject == null || countryItemObject == null ? null : (BigDecimal) findProperty("valueVAT[Ware,Country]").read(session, wareItemObject, countryItemObject);

                DataObject itemObject = itemResult.getKey(i).get("Item");
                DataObject dateObject = new DataObject(new Date(System.currentTimeMillis()), DateClass.instance);
                BigDecimal vatItem = (BigDecimal) findProperty("valueVAT[Item,Country,DATE]").read(session, itemObject, countryItemObject, dateObject);
                String nameCountry = trim((String) findProperty("name[Country]").read(session, countryItemObject), "");

                Long writeOffRateID = writeOffRateItemLM == null ? null : (Long) writeOffRateItemLM.findProperty("writeOffRate[Country,Item]").read(session, countryItemObject, itemObject);

                BigDecimal retailMarkup = (BigDecimal) findProperty("markup[CalcPriceListType,Sku]").read(session, retailCPLT, itemObject);
                BigDecimal wholesaleMarkup = (BigDecimal) findProperty("markup[CalcPriceListType,Sku]").read(session, wholesaleCPLT, itemObject);

                data.add(Arrays.asList(formatValue(itemID), formatValue(itemGroupID), name, nameUOM, shortNameUOM, 
                        formatValue(uomItemObject.getValue()), nameBrand, formatValue(brandItemObject.getValue()), 
                        nameCountry, idBarcodeSku, isWeightItem, formatValue(netWeightItem), formatValue(grossWeightItem),
                        compositionItem, formatValue(vatItem), formatValue(wareItemObject == null ? null : wareItemObject.getValue()), formatValue(priceWare),
                        formatValue(vatWare), formatValue(writeOffRateID), formatValue(retailMarkup), formatValue(wholesaleMarkup),
                        formatValue(purchaseAmount), formatValue(saleAmount)));
            }

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }

        return data;
    }
}