package lsfusion.erp.region.by.euroopt;

import com.google.common.base.Throwables;
import lsfusion.base.IOUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.DefaultImportActionProperty;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.silvertunnel_ng.netlib.api.NetFactory;
import org.silvertunnel_ng.netlib.api.NetLayer;
import org.silvertunnel_ng.netlib.api.NetLayerIDs;
import org.silvertunnel_ng.netlib.api.util.TcpipNetAddress;
import org.silvertunnel_ng.netlib.util.HttpUtil;

import java.io.*;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLConnection;
import java.sql.SQLException;
import java.util.*;

public class ImportEurooptActionProperty extends DefaultImportActionProperty {

    String mainPage = "http://e-dostavka.by";
    String mainPage2 = "e-dostavka.by";
    String itemGroupPattern = "http:\\/\\/e-dostavka\\.by\\/catalog\\/\\d+\\.html";
    String itemPattern = "http:\\/\\/e-dostavka\\.by\\/catalog\\/\\d+_\\d+\\.html";
    String userAgent = "Mozilla/5.0 (Windows NT 6.2; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/32.0.1667.0 Safari/537.36";

    public ImportEurooptActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            boolean useTor = findProperty("importEurooptUseTor").read(context) != null;
            boolean importItems = findProperty("importEurooptItems").read(context) != null;
            boolean importUserPriceLists = findProperty("importEurooptUserPriceLists").read(context) != null;
            boolean skipKeys = findProperty("importEurooptSkipKeys").read(context) != null;

            List<List<List<Object>>> data = importDataFromWeb(context, useTor, importItems, importUserPriceLists, skipKeys);

            if (importItems)
                importItems(context, data.get(0), skipKeys);
            if (importUserPriceLists)
                importUserPriceLists(context, data.get(1), skipKeys);

        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }

    }

    private void importItems(ExecutionContext context, List<List<Object>> data, boolean skipKeys) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<ImportProperty<?>> props = new ArrayList<>();
        List<ImportField> fields = new ArrayList<>();
        List<ImportKey<?>> keys = new ArrayList<>();

        ImportField idBarcodeSkuField = new ImportField(findProperty("idBarcodeSku"));
        ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                findProperty("skuBarcodeId").getMapping(idBarcodeSkuField));
        itemKey.skipKey = skipKeys;
        keys.add(itemKey);
        fields.add(idBarcodeSkuField);

        ImportKey<?> barcodeKey = new ImportKey((CustomClass) findClass("Barcode"),
                findProperty("extBarcodeId").getMapping(idBarcodeSkuField));
        barcodeKey.skipKey = skipKeys;
        keys.add(barcodeKey);
        props.add(new ImportProperty(idBarcodeSkuField, findProperty("skuBarcode").getMapping(barcodeKey),
                object(findClass("Item")).getMapping(itemKey)));
        props.add(new ImportProperty(idBarcodeSkuField, findProperty("extIdBarcode").getMapping(barcodeKey), true));
        props.add(new ImportProperty(idBarcodeSkuField, findProperty("idBarcode").getMapping(barcodeKey), true));

        ImportField idItemGroupField = new ImportField(findProperty("idItemGroup"));
        ImportKey<?> itemGroupKey = new ImportKey((CustomClass) findClass("ItemGroup"),
                findProperty("itemGroupId").getMapping(idItemGroupField));
        itemGroupKey.skipKey = skipKeys;
        keys.add(itemGroupKey);
        props.add(new ImportProperty(idItemGroupField, findProperty("idItemGroup").getMapping(itemGroupKey), true));
        props.add(new ImportProperty(idItemGroupField, findProperty("nameItemGroup").getMapping(itemGroupKey), true));
        props.add(new ImportProperty(idItemGroupField, findProperty("itemGroupItem").getMapping(itemKey),
                LM.object(findClass("ItemGroup")).getMapping(itemGroupKey), true));
        fields.add(idItemGroupField);

        ImportField captionItemField = new ImportField(findProperty("captionItem"));
        props.add(new ImportProperty(captionItemField, findProperty("captionItem").getMapping(itemKey), true));
        fields.add(captionItemField);

        ImportField netWeightItemField = new ImportField(findProperty("netWeightItem"));
        props.add(new ImportProperty(netWeightItemField, findProperty("netWeightItem").getMapping(itemKey), true));
        fields.add(netWeightItemField);

        ImportField descriptionItemField = new ImportField(findProperty("descriptionItem"));
        props.add(new ImportProperty(descriptionItemField, findProperty("descriptionItem").getMapping(itemKey), true));
        fields.add(descriptionItemField);

        ImportField compositionItemField = new ImportField(findProperty("compositionItem"));
        props.add(new ImportProperty(compositionItemField, findProperty("compositionItem").getMapping(itemKey), true));
        fields.add(compositionItemField);

        ImportField proteinsItemField = new ImportField(findProperty("proteinsItem"));
        props.add(new ImportProperty(proteinsItemField, findProperty("proteinsItem").getMapping(itemKey), true));
        fields.add(proteinsItemField);

        ImportField fatsItemField = new ImportField(findProperty("fatsItem"));
        props.add(new ImportProperty(fatsItemField, findProperty("fatsItem").getMapping(itemKey), true));
        fields.add(fatsItemField);

        ImportField carbohydratesItemField = new ImportField(findProperty("carbohydratesItem"));
        props.add(new ImportProperty(carbohydratesItemField, findProperty("carbohydratesItem").getMapping(itemKey), true));
        fields.add(carbohydratesItemField);

        ImportField energyItemField = new ImportField(findProperty("energyItem"));
        props.add(new ImportProperty(energyItemField, findProperty("energyItem").getMapping(itemKey), true));
        fields.add(energyItemField);

        ImportField dataImageItemField = new ImportField(findProperty("dataImageItem"));
        props.add(new ImportProperty(dataImageItemField, findProperty("dataImageItem").getMapping(itemKey), true));
        fields.add(dataImageItemField);

        ImportField idManufacturerField = new ImportField(findProperty("idManufacturer"));
        ImportKey<?> manufacturerKey = new ImportKey((CustomClass) findClass("Manufacturer"),
                findProperty("manufacturerId").getMapping(idManufacturerField));
        manufacturerKey.skipKey = skipKeys;
        keys.add(manufacturerKey);
        props.add(new ImportProperty(idManufacturerField, findProperty("idManufacturer").getMapping(manufacturerKey), true));
        props.add(new ImportProperty(idManufacturerField, findProperty("nameManufacturer").getMapping(manufacturerKey), true));
        props.add(new ImportProperty(idManufacturerField, findProperty("manufacturerItem").getMapping(itemKey),
                LM.object(findClass("Manufacturer")).getMapping(manufacturerKey), true));
        fields.add(idManufacturerField);
        
        ImportField idUOMField = new ImportField(findProperty("idUOM"));
        ImportKey<?> UOMKey = new ImportKey((CustomClass) findClass("UOM"),
                findProperty("UOMId").getMapping(idUOMField));
        UOMKey.skipKey = true;
        keys.add(UOMKey);
        props.add(new ImportProperty(idUOMField, findProperty("UOMItem").getMapping(itemKey),
                object(findClass("UOM")).getMapping(UOMKey), true));
        props.add(new ImportProperty(idUOMField, findProperty("UOMBarcode").getMapping(barcodeKey),
                object(findClass("UOM")).getMapping(UOMKey), true));
        fields.add(idUOMField);

        ImportField idBrandField = new ImportField(findProperty("idBrand"));
        ImportKey<?> brandKey = new ImportKey((CustomClass) findClass("Brand"),
                findProperty("brandId").getMapping(idBrandField));
        brandKey.skipKey = true;
        keys.add(brandKey);
        props.add(new ImportProperty(idBrandField, findProperty("idBrand").getMapping(brandKey), true));
        props.add(new ImportProperty(idBrandField, findProperty("nameBrand").getMapping(brandKey), true));
        props.add(new ImportProperty(idBrandField, findProperty("brandItem").getMapping(itemKey),
                object(findClass("Brand")).getMapping(brandKey), true));
        fields.add(idBrandField);

//        ImportField extIdPackBarcodeSkuField = new ImportField(findProperty("extIdBarcode"));
//        ImportKey<?> packBarcodeKey = new ImportKey((CustomClass) findClass("Barcode"),
//                findProperty("extBarcodeId").getMapping(extIdPackBarcodeSkuField));
//        keys.add(packBarcodeKey);
//        packBarcodeKey.skipKey = skipKeys;
//        props.add(new ImportProperty(extIdPackBarcodeSkuField, findProperty("extIdBarcode").getMapping(packBarcodeKey)));
//        props.add(new ImportProperty(extIdPackBarcodeSkuField, findProperty("skuBarcode").getMapping(packBarcodeKey),
//                object(findClass("Item")).getMapping(itemKey)));
//        props.add(new ImportProperty(extIdPackBarcodeSkuField, findProperty("Purchase.packBarcodeSku").getMapping(itemKey),
//                object(findClass("Barcode")).getMapping(packBarcodeKey)));
//        fields.add(extIdPackBarcodeSkuField);
//
//        ImportField amountPackBarcodeField = new ImportField(findProperty("amountBarcode"));
//        props.add(new ImportProperty(amountPackBarcodeField, findProperty("amountBarcode").getMapping(packBarcodeKey)));
//        fields.add(amountPackBarcodeField);

        ImportTable table = new ImportTable(fields, data);

        DataSession session = context.createSession();
        session.pushVolatileStats("IE_IT");
        IntegrationService service = new IntegrationService(session, table, keys, props);
        service.synchronize(true, false);
        session.apply(context);
        session.popVolatileStats();
        session.close();
    }

    private void importUserPriceLists(ExecutionContext context, List<List<Object>> data, boolean skipKeys) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<ImportProperty<?>> props = new ArrayList<>();
        List<ImportField> fields = new ArrayList<>();
        List<ImportKey<?>> keys = new ArrayList<>();

        ImportField idUserPriceListField = new ImportField(findProperty("idUserPriceList"));
        ImportKey<?> userPriceListKey = new ImportKey((CustomClass) findClass("UserPriceList"),
                findProperty("userPriceListId").getMapping(idUserPriceListField));
        keys.add(userPriceListKey);
        props.add(new ImportProperty(idUserPriceListField, findProperty("idUserPriceList").getMapping(userPriceListKey)));
        fields.add(idUserPriceListField);

        ImportField idUserPriceListDetailField = new ImportField(findProperty("idUserPriceListDetail"));
        ImportKey<?> userPriceListDetailKey = new ImportKey((CustomClass) findClass("UserPriceListDetail"),
                findProperty("userPriceListDetailIdIdUserPriceList").getMapping(idUserPriceListDetailField, idUserPriceListField));
        keys.add(userPriceListDetailKey);
        props.add(new ImportProperty(idUserPriceListField, findProperty("userPriceListUserPriceListDetail").getMapping(userPriceListDetailKey),
                object(findClass("UserPriceList")).getMapping(userPriceListKey)));
        props.add(new ImportProperty(idUserPriceListDetailField, findProperty("idUserPriceListDetail").getMapping(userPriceListDetailKey)));
        fields.add(idUserPriceListDetailField);

        ImportField idBarcodeSkuField = new ImportField(findProperty("idBarcodeSku"));
        ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"), findProperty("skuBarcodeId").getMapping(idBarcodeSkuField));
        itemKey.skipKey = skipKeys;
        keys.add(itemKey);
        props.add(new ImportProperty(idBarcodeSkuField, findProperty("originalIdBarcodeSkuUserPriceListDetail").getMapping(userPriceListDetailKey)));
        props.add(new ImportProperty(idBarcodeSkuField, findProperty("skuUserPriceListDetail").getMapping(userPriceListDetailKey),
                object(findClass("Item")).getMapping(itemKey)));
        fields.add(idBarcodeSkuField);

        ImportField idDataPriceListTypeField = new ImportField(findProperty("idDataPriceListType"));
        ImportKey<?> dataPriceListTypeKey = new ImportKey((CustomClass) findClass("DataPriceListType"),
                findProperty("dataPriceListTypeId").getMapping(idDataPriceListTypeField));
        keys.add(dataPriceListTypeKey);
        props.add(new ImportProperty(idDataPriceListTypeField, findProperty("idPriceListType").getMapping(dataPriceListTypeKey)));
        fields.add(idDataPriceListTypeField);

        ImportField namePriceListTypeField = new ImportField(findProperty("namePriceListType"));
        props.add(new ImportProperty(namePriceListTypeField, findProperty("namePriceListType").getMapping(dataPriceListTypeKey), true));
        fields.add(namePriceListTypeField);
        
        ImportField pricePriceListDetailField = new ImportField(findProperty("pricePriceListDetailDataPriceListType"));
        props.add(new ImportProperty(pricePriceListDetailField, findProperty("priceUserPriceListDetailDataPriceListType").getMapping(userPriceListDetailKey, dataPriceListTypeKey)));
        fields.add(pricePriceListDetailField);

        ImportField inPriceListPriceListTypeField = new ImportField(findProperty("inUserPriceListDataPriceListType"));
        props.add(new ImportProperty(inPriceListPriceListTypeField, findProperty("inUserPriceListDataPriceListType").getMapping(userPriceListKey, dataPriceListTypeKey), true));
        fields.add(inPriceListPriceListTypeField);

        ImportTable table = new ImportTable(fields, data);

        DataSession session = context.createSession();
        session.pushVolatileStats("IE_PL");
        IntegrationService service = new IntegrationService(session, table, keys, props);
        service.synchronize(true, false);
        session.apply(context);
        session.popVolatileStats();
        session.close();
    }

    private List<List<List<Object>>> importDataFromWeb(ExecutionContext context, boolean useTor, boolean importItems, boolean importUserPriceLists, boolean skipKeys)
            throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        List<List<Object>> itemsList = new ArrayList<>();
        List<List<Object>> userPriceListsList = new ArrayList<>();
        Map<String, String> barcodeSet = getBarcodeSet(context);
        //Set<String> amountPackSkuSet = getAmountPackSkuSet(context);
        try {

            NetLayer lowerNetLayer = useTor ? getNetLayer() : null;
            String idPriceList = "euroopt" + String.valueOf(Calendar.getInstance().getTimeInMillis());
            Set<String> itemURLSet = useTor ? getItemURLSetTor(lowerNetLayer) : getItemURLSet();
            int idPriceListDetail = 1;
            int i = 1;
            for (String itemURL : itemURLSet) {
                ServerLoggers.systemLogger.info(String.format("Import Euroopt: parsing item page #%s: %s", i, itemURL));
                Document doc = useTor ? getDocumentTor(lowerNetLayer, itemURL) : getDocument(itemURL);
                if (doc != null) {
                    Elements prodImage = doc.getElementsByClass("increaseImage");
                    File imageItem = prodImage.size() == 0 ? null : readImage(prodImage.get(0).attr("href"));
                    
                    String captionItem = doc.getElementsByTag("h1").text();
                    BigDecimal price = getPrice(doc);
                    Elements descriptionElement = doc.getElementsByClass("description");
                    List<Node> descriptionAttributes = descriptionElement.size() == 0 ? new ArrayList<Node>() : descriptionElement.get(0).childNodes();
                    String idBarcode = null;
                    String idItemGroup = null;
                    String brandItem = null;
                    BigDecimal netWeight = null;
                    String UOMItem = null;
                    BigDecimal quantityPack = null;
                    String idBarcodePack = null;
                    for (Node attribute : descriptionAttributes) {
                        if (((Element) attribute).children().size() == 2) {
                        String type = parseChild((Element) attribute, 0);
                        String value = parseChild((Element) attribute, 1);
                            switch (type) {
                                case "Штрих-код:":
                                    idBarcode = value;
                                    idItemGroup = barcodeSet.containsKey(idBarcode) ? barcodeSet.get(idBarcode) : "ВСЕ";
                                    break;
                                case "Торговая марка:":
                                    brandItem = value;
                                    break;
                                case "Масса:":
                                    String[] split = value.split(" ");
                                    netWeight = new BigDecimal(split[0]);
                                    UOMItem = split.length >= 2 ? split[1] : null;
                                    break;
                                case "Кол-во товара в заводской таре:":
                                    try {
                                        quantityPack = new BigDecimal(value);
                                    } catch (Exception e) {
                                        quantityPack = null;
                                    }
                                    idBarcodePack = idBarcode + "pack";
                                    break;
                            }
                        }
                    }
                    Elements propertyAttributes = doc.getElementsByClass("property_group");
                    String descriptionItem = null;
                    String compositionItem = null;
                    BigDecimal proteinsItem = null;
                    BigDecimal fatsItem = null;
                    BigDecimal carbohydratesItem = null;
                    BigDecimal energyItem = null;
                    String manufacturerItem = null;
                    for (Element propertyAttribute : propertyAttributes) {
                        Elements propertyRows = propertyAttribute.select("tr");
                        for(Element propertyRow : propertyRows) {
                            String type = parseChild(propertyRow, 0);
                            String value = parseChild(propertyRow, 1);
                                switch (type) {
                                    case "Краткое описание":
                                        descriptionItem = value;
                                        break;
                                    case "Состав":
                                        compositionItem = value;
                                        break;
                                    case "Белки":
                                        proteinsItem = parseBigDecimalWeight(value);
                                        break;
                                    case "Жиры":
                                        fatsItem = parseBigDecimalWeight(value);
                                        break;
                                    case "Углеводы":
                                        carbohydratesItem = parseBigDecimalWeight(value);
                                        break;
                                    case "Энергетическая ценность на 100 г":
                                        energyItem = parseBigDecimalWeight(value.split("\\s(ккал|калл)")[0]);
                                        break;
                                    case "Производитель":
                                        manufacturerItem = value;
                                        break;
                                }
                        }
                    }
                    if (idBarcode != null && (!skipKeys || barcodeSet.containsKey(idBarcode))) {
                        if (importItems)
                            itemsList.add(Arrays.asList((Object) idBarcode, idItemGroup, captionItem, netWeight, descriptionItem, compositionItem, proteinsItem,
                                    fatsItem, carbohydratesItem, energyItem, imageItem == null ? null : IOUtils.getFileBytes(imageItem), manufacturerItem, UOMItem,
                                    brandItem)); //, idBarcodePack, quantityPack));
                        if (importUserPriceLists) {
                            userPriceListsList.add(Arrays.asList((Object) idPriceList, idPriceList + "/" + String.valueOf(idPriceListDetail), idBarcode, "euroopt", "Цена (Евроопт)", price, true));
                            idPriceListDetail++;
                        }
                        //to avoid duplicates
                        barcodeSet.remove(idBarcode);
                    }
                    if(imageItem != null)
                        imageItem.delete();
                }
                i++;
            }
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return Arrays.asList(itemsList, userPriceListsList);
    }

    private String parseChild(Element element, int child) {
        return element.children().size() > child ? Jsoup.parse(element.childNode(child).outerHtml()).text() : "";
    }

    private BigDecimal getPrice(Document doc) {
        BigDecimal price = null;
        try {
            Element priceElement = doc.getElementsByClass("price").first();
            if(priceElement != null) {
                Elements oldPrice = priceElement.getElementsByClass("Old_price");
                String priceValue = (oldPrice != null && oldPrice.size() != 0 ? priceElement.text().replace(oldPrice.first().text(), "") : priceElement.text()).replace(" ", "");
                price = priceValue == null || priceValue.isEmpty() ? null : new BigDecimal(priceValue);
            }
        } catch (Exception e) {
            price = null;
        }
        return price;
    }

    private Set<String> getItemURLSet() throws IOException {
        Set<String> itemsSet = new LinkedHashSet<>();

        for (String itemGroupURL : getItemGroupURLSet()) {
            int i = 1;
            String prevHash = null;
            boolean notLast = true;
            while (notLast) {
                String hash = "";
                Document doc = getDocument(itemGroupURL + "?page=" + i);
                if (doc != null) {
                    for (Element item : doc.getElementsByTag("a")) {
                        String href = item.attr("href");
                        if (href != null && href.matches(itemPattern)) {
                            if(!itemsSet.contains(href)) {
                                ServerLoggers.systemLogger.info(String.format("Import Euroopt: preparing item page #%s: %s", itemsSet.size() + 1, href));
                                itemsSet.add(href);
                            }
                            hash += href;
                        }
                    }
                }
                i++;
                notLast = !hash.equals(prevHash);
                prevHash = hash;
            }
        }
        return itemsSet;
    }
    
    private Set<String> getItemURLSetTor(NetLayer lowerNetLayer) throws IOException {
        Set<String> itemsSet = new LinkedHashSet<>();
        for (String itemGroupURL : getItemGroupURLSetTor(lowerNetLayer)) {
            int i = 1;
            String prevHash = null;
            boolean notLast = true;
            while (notLast) {
                String hash = "";
                Document doc = getDocumentTor(lowerNetLayer, itemGroupURL + "?page=" + i);
                if (doc != null) {
                    for (Element item : doc.getElementsByTag("a")) {
                        String href = item.attr("href");
                        if (href != null && href.matches(itemPattern)) {
                            href = href.replace(mainPage, "");
                            if(!itemsSet.contains(href)) {
                                ServerLoggers.systemLogger.info(String.format("Import Euroopt: preparing item page #%s: %s", itemsSet.size() + 1, href));
                                itemsSet.add(href);
                            }
                            hash += href;
                        }
                    }
                }
                i++;
                notLast = !hash.equals(prevHash);
                prevHash = hash;
            }
        }
        return itemsSet;
    }


    private Set<String> getItemGroupURLSet() throws IOException {
        Set<String> itemGroupsSet = new HashSet<>();
        Document doc = getDocument(mainPage + "/");
        if(doc != null) {
            for (Element url : doc.getElementsByTag("a")) {
                String href = url.attr("href");
                if (href != null && href.matches(itemGroupPattern) && !itemGroupsSet.contains(href)) {
                    ServerLoggers.systemLogger.info(String.format("Import Euroopt: preparing item group page #%s: %s", itemGroupsSet.size() + 1, href));
                    itemGroupsSet.add(href);
                }
            }
        }
        return itemGroupsSet;
    }

    private Set<String> getItemGroupURLSetTor(NetLayer lowerNetLayer) throws IOException {
        Set<String> itemGroupsSet = new HashSet<>();
        Document doc = getDocumentTor(lowerNetLayer, "/catalog/");
        if(doc != null) {
            for (Element url : doc.getElementsByTag("a")) {
                String href = url.attr("href");
                if (href != null && href.matches(itemGroupPattern) && !itemGroupsSet.contains(href)) {
                    ServerLoggers.systemLogger.info(String.format("Import Euroopt: preparing item group page #%s: %s", itemGroupsSet.size() + 1, href));
                    itemGroupsSet.add(href.replace(mainPage, ""));
                }
            }
        }
        return itemGroupsSet;
    }

    private Map<String, String> getBarcodeSet(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, String> barcodeSet = new HashMap<>();
        KeyExpr barcodeExpr = new KeyExpr("barcode");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "barcode", barcodeExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("idBarcode", findProperty("idBarcode").getExpr(context.getModifier(), barcodeExpr));
        query.addProperty("idItemGroupBarcode", findProperty("idItemGroupBarcode").getExpr(context.getModifier(), barcodeExpr));
        query.and(findProperty("idBarcode").getExpr(barcodeExpr).getWhere());
        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> itemResult = query.executeClasses(context);
        for (ImMap<Object, ObjectValue> entry : itemResult.values()) {
            String idBarcode = trim((String) entry.get("idBarcode").getValue());
            String idItemGroupBarcode = trim((String) entry.get("idItemGroupBarcode").getValue());
            barcodeSet.put(idBarcode, idItemGroupBarcode);
        }
        return barcodeSet;
    }

    /*private Set<String> getAmountPackSkuSet(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Set<String> amountPackSkuSet = new HashSet<String>();
        KeyExpr skuExpr = new KeyExpr("sku");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "sku", skuExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
        query.addProperty("idBarcodeSku", findProperty("idBarcodeSku").getExpr(skuExpr));
        query.addProperty("amountPackSku", findProperty("Purchase.amountPackSku").getExpr(skuExpr));
        query.and(findProperty("amountPackSku").getExpr(skuExpr).getWhere());
        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> itemResult = query.executeClasses(context);
        for (ImMap<Object, ObjectValue> entry : itemResult.values()) {
            String idBarcodeSku = trim((String) entry.get("idBarcodeSku").getValue());
            BigDecimal amountPackSku = (BigDecimal) entry.get("amountPackSku").getValue();
            if(amountPackSku != null)
            amountPackSkuSet.add(idBarcodeSku);
        }
        return amountPackSkuSet;
    }*/

    private NetLayer getNetLayer() throws IOException {
        NetLayer lowerNetLayer = NetFactory.getInstance().getNetLayerById(NetLayerIDs.TOR);
        // wait until TOR is ready (optional):
        lowerNetLayer.waitUntilReady();
        return lowerNetLayer;
    }
    
    private Document getDocumentTor(NetLayer lowerNetLayer, String url) throws IOException {
        int count = 2;
        while (count > 0) {
            try {
                Thread.sleep(50);
                
                // prepare parameters
                TcpipNetAddress httpServerNetAddress = new TcpipNetAddress(mainPage2, 80);
                long timeoutInMs = 5000;

                // do the request and wait for the response
                byte[] responseBody = new HttpUtil().get(lowerNetLayer, httpServerNetAddress, url, timeoutInMs);
                return Jsoup.parse(new ByteArrayInputStream(responseBody), "utf-8", "");
            } catch (HttpStatusException e) {
                count--;
                if(count <= 0)
                    ServerLoggers.systemLogger.error(e);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }
        return null;
    }
    
    private Document getDocument(String url) throws IOException {
        int count = 2;
        while (count > 0) {
            try {
                Thread.sleep(50);
                Connection connection = Jsoup.connect(url);
                connection.timeout(0);
                connection.userAgent(userAgent);
                return connection.get();
            } catch (HttpStatusException e) {
                count--;
                if(count <= 0)
                    ServerLoggers.systemLogger.error(e);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }
        return null;
    }

    protected File readImage(String url) {
        if(url == null) return null;
        File file;
        try {
            URLConnection connection = new URL(url).openConnection();
            InputStream input = connection.getInputStream();
            byte[] buffer = new byte[4096];
            int n;
            file = File.createTempFile("image", ".tmp");
            OutputStream output = new FileOutputStream(file);
            while ((n = input.read(buffer)) != -1) {
                output.write(buffer, 0, n);
            }
            output.close();
        } catch (IOException e) {
            file = null;
        }
        return file;
    }

    private BigDecimal parseBigDecimalWeight(String input) {
        try {
            input = input.replace("г", "").replace("Г", "").trim();
            return input.isEmpty() ? null : new BigDecimal(input);
        } catch (Exception e) {
            return null;
        }
    }
}
