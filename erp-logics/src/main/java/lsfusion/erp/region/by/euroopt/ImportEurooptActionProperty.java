package lsfusion.erp.region.by.euroopt;

import com.google.common.base.Throwables;
import lsfusion.base.IOUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.action.MessageClientAction;
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
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.silvertunnel_ng.netlib.api.NetLayer;

import java.io.*;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLConnection;
import java.sql.SQLException;
import java.util.*;

public class ImportEurooptActionProperty extends EurooptActionProperty {

    public ImportEurooptActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            boolean useTor = findProperty("importEurooptUseTor[]").read(context) != null;
            boolean importItems = findProperty("importEurooptItems[]").read(context) != null;
            boolean onlyImages = findProperty("importEurooptOnlyImages[]").read(context) != null;
            boolean smallImages = findProperty("importEurooptSmallImages[]").read(context) != null;
            boolean importUserPriceLists = findProperty("importEurooptUserPriceLists[]").read(context) != null;
            boolean skipKeys = findProperty("importEurooptSkipKeys[]").read(context) != null;

            List<List<List<Object>>> data = importDataFromWeb(context, useTor, importItems, onlyImages, smallImages, importUserPriceLists, skipKeys);

            if (importItems) {
                if (onlyImages)
                    importImages(context, data.get(0), skipKeys);
                else
                    importItems(context, data.get(0), skipKeys);
            }
            if (importUserPriceLists)
                importUserPriceLists(context, data.get(1), skipKeys);

            context.delayUserInteraction(new MessageClientAction("Импорт успешно завершён", "Импорт Евроопт"));

        } catch (IOException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }

    }

    private void importItems(ExecutionContext context, List<List<Object>> data, boolean skipKeys) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<ImportProperty<?>> props = new ArrayList<>();
        List<ImportField> fields = new ArrayList<>();
        List<ImportKey<?>> keys = new ArrayList<>();

        ImportField idBarcodeSkuField = new ImportField(findProperty("idBarcode[Sku]"));
        ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                findProperty("skuBarcode[STRING[15]]").getMapping(idBarcodeSkuField));
        itemKey.skipKey = skipKeys;
        keys.add(itemKey);
        fields.add(idBarcodeSkuField);

        ImportKey<?> barcodeKey = new ImportKey((CustomClass) findClass("Barcode"),
                findProperty("extBarcode[VARSTRING[100]]").getMapping(idBarcodeSkuField));
        barcodeKey.skipKey = skipKeys;
        keys.add(barcodeKey);
        props.add(new ImportProperty(idBarcodeSkuField, findProperty("sku[Barcode]").getMapping(barcodeKey),
                object(findClass("Item")).getMapping(itemKey)));
        props.add(new ImportProperty(idBarcodeSkuField, findProperty("extId[Barcode]").getMapping(barcodeKey), true));
        props.add(new ImportProperty(idBarcodeSkuField, findProperty("id[Barcode]").getMapping(barcodeKey), true));
        props.add(new ImportProperty(idBarcodeSkuField, findProperty("id[Item]").getMapping(itemKey), true));

        ImportField idItemGroupField = new ImportField(findProperty("id[ItemGroup]"));
        ImportKey<?> itemGroupKey = new ImportKey((CustomClass) findClass("ItemGroup"),
                findProperty("itemGroup[VARSTRING[100]]").getMapping(idItemGroupField));
        itemGroupKey.skipKey = skipKeys;
        keys.add(itemGroupKey);
        props.add(new ImportProperty(idItemGroupField, findProperty("id[ItemGroup]").getMapping(itemGroupKey), true));
        props.add(new ImportProperty(idItemGroupField, findProperty("name[ItemGroup]").getMapping(itemGroupKey), true));
        props.add(new ImportProperty(idItemGroupField, findProperty("itemGroup[Item]").getMapping(itemKey),
                LM.object(findClass("ItemGroup")).getMapping(itemGroupKey), true));
        fields.add(idItemGroupField);

        ImportField captionItemField = new ImportField(findProperty("caption[Item]"));
        props.add(new ImportProperty(captionItemField, findProperty("caption[Item]").getMapping(itemKey), true));
        fields.add(captionItemField);

        ImportField netWeightItemField = new ImportField(findProperty("netWeight[Item]"));
        props.add(new ImportProperty(netWeightItemField, findProperty("netWeight[Item]").getMapping(itemKey), true));
        fields.add(netWeightItemField);

        ImportField descriptionItemField = new ImportField(findProperty("description[Item]"));
        props.add(new ImportProperty(descriptionItemField, findProperty("description[Item]").getMapping(itemKey), true));
        fields.add(descriptionItemField);

        ImportField compositionItemField = new ImportField(findProperty("composition[Item]"));
        props.add(new ImportProperty(compositionItemField, findProperty("composition[Item]").getMapping(itemKey), true));
        fields.add(compositionItemField);

        ImportField proteinsItemField = new ImportField(findProperty("proteins[Item]"));
        props.add(new ImportProperty(proteinsItemField, findProperty("proteins[Item]").getMapping(itemKey), true));
        fields.add(proteinsItemField);

        ImportField fatsItemField = new ImportField(findProperty("fats[Item]"));
        props.add(new ImportProperty(fatsItemField, findProperty("fats[Item]").getMapping(itemKey), true));
        fields.add(fatsItemField);

        ImportField carbohydratesItemField = new ImportField(findProperty("carbohydrates[Item]"));
        props.add(new ImportProperty(carbohydratesItemField, findProperty("carbohydrates[Item]").getMapping(itemKey), true));
        fields.add(carbohydratesItemField);

        ImportField energyItemField = new ImportField(findProperty("energy[Item]"));
        props.add(new ImportProperty(energyItemField, findProperty("energy[Item]").getMapping(itemKey), true));
        fields.add(energyItemField);

        ImportField dataImageItemField = new ImportField(findProperty("dataImage[Item]"));
        props.add(new ImportProperty(dataImageItemField, findProperty("dataImage[Item]").getMapping(itemKey), true));
        fields.add(dataImageItemField);

        ImportField idManufacturerField = new ImportField(findProperty("id[Manufacturer]"));
        ImportKey<?> manufacturerKey = new ImportKey((CustomClass) findClass("Manufacturer"),
                findProperty("manufacturer[VARSTRING[100]]").getMapping(idManufacturerField));
        manufacturerKey.skipKey = skipKeys;
        keys.add(manufacturerKey);
        props.add(new ImportProperty(idManufacturerField, findProperty("id[Manufacturer]").getMapping(manufacturerKey), true));
        props.add(new ImportProperty(idManufacturerField, findProperty("name[Manufacturer]").getMapping(manufacturerKey), true));
        props.add(new ImportProperty(idManufacturerField, findProperty("manufacturer[Item]").getMapping(itemKey),
                LM.object(findClass("Manufacturer")).getMapping(manufacturerKey), true));
        fields.add(idManufacturerField);

        ImportField idUOMField = new ImportField(findProperty("id[UOM]"));
        ImportKey<?> UOMKey = new ImportKey((CustomClass) findClass("UOM"),
                findProperty("UOM[VARSTRING[100]]").getMapping(idUOMField));
        UOMKey.skipKey = true;
        keys.add(UOMKey);
        props.add(new ImportProperty(idUOMField, findProperty("UOM[Item]").getMapping(itemKey),
                object(findClass("UOM")).getMapping(UOMKey), true));
        props.add(new ImportProperty(idUOMField, findProperty("UOM[Barcode]").getMapping(barcodeKey),
                object(findClass("UOM")).getMapping(UOMKey), true));
        fields.add(idUOMField);

        ImportField idBrandField = new ImportField(findProperty("id[Brand]"));
        ImportKey<?> brandKey = new ImportKey((CustomClass) findClass("Brand"),
                findProperty("brand[VARSTRING[100]]").getMapping(idBrandField));
        brandKey.skipKey = true;
        keys.add(brandKey);
        props.add(new ImportProperty(idBrandField, findProperty("id[Brand]").getMapping(brandKey), true));
        props.add(new ImportProperty(idBrandField, findProperty("name[Brand]").getMapping(brandKey), true));
        props.add(new ImportProperty(idBrandField, findProperty("brand[Item]").getMapping(itemKey),
                object(findClass("Brand")).getMapping(brandKey), true));
        fields.add(idBrandField);

        ImportTable table = new ImportTable(fields, data);

        try (DataSession session = context.createSession()) {
            session.pushVolatileStats("IE_IT");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
        }
    }

    private void importImages(ExecutionContext context, List<List<Object>> data, boolean skipKeys) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<ImportProperty<?>> props = new ArrayList<>();
        List<ImportField> fields = new ArrayList<>();
        List<ImportKey<?>> keys = new ArrayList<>();

        ImportField idBarcodeSkuField = new ImportField(findProperty("idBarcode[Sku]"));
        ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                findProperty("skuBarcode[STRING[15]]").getMapping(idBarcodeSkuField));
        itemKey.skipKey = skipKeys;
        keys.add(itemKey);
        fields.add(idBarcodeSkuField);

        ImportField dataImageItemField = new ImportField(findProperty("dataImage[Item]"));
        props.add(new ImportProperty(dataImageItemField, findProperty("dataImage[Item]").getMapping(itemKey), true));
        props.add(new ImportProperty(idBarcodeSkuField, findProperty("id[Item]").getMapping(itemKey), true));
        fields.add(dataImageItemField);

        ImportTable table = new ImportTable(fields, data);

        try (DataSession session = context.createSession()) {
            session.pushVolatileStats("IE_ITI");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
        }
    }

    private void importUserPriceLists(ExecutionContext context, List<List<Object>> data, boolean skipKeys) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<ImportProperty<?>> props = new ArrayList<>();
        List<ImportField> fields = new ArrayList<>();
        List<ImportKey<?>> keys = new ArrayList<>();

        ImportField idUserPriceListField = new ImportField(findProperty("id[UserPriceList]"));
        ImportKey<?> userPriceListKey = new ImportKey((CustomClass) findClass("UserPriceList"),
                findProperty("userPriceList[VARSTRING[100]]").getMapping(idUserPriceListField));
        keys.add(userPriceListKey);
        props.add(new ImportProperty(idUserPriceListField, findProperty("id[UserPriceList]").getMapping(userPriceListKey)));
        fields.add(idUserPriceListField);

        ImportField idOperationField = new ImportField(findProperty("id[PriceList.Operation]"));
        ImportKey<?> operationKey = new ImportKey((CustomClass) findClass("PriceList.Operation"),
                findProperty("operation[VARISTRING[100]]").getMapping(idOperationField));
        keys.add(operationKey);
        props.add(new ImportProperty(idOperationField, findProperty("operation[PriceList]").getMapping(userPriceListKey),
                object(findClass("PriceList.Operation")).getMapping(operationKey)));
        props.add(new ImportProperty(idOperationField, findProperty("id[PriceList.Operation]").getMapping(operationKey)));
        fields.add(idOperationField);

        ImportField idUserPriceListDetailField = new ImportField(findProperty("id[UserPriceListDetail]"));
        ImportKey<?> userPriceListDetailKey = new ImportKey((CustomClass) findClass("UserPriceListDetail"),
                findProperty("userPriceListDetail[VARSTRING[100],VARSTRING[100]]").getMapping(idUserPriceListDetailField, idUserPriceListField));
        keys.add(userPriceListDetailKey);
        props.add(new ImportProperty(idUserPriceListField, findProperty("userPriceList[UserPriceListDetail]").getMapping(userPriceListDetailKey),
                object(findClass("UserPriceList")).getMapping(userPriceListKey)));
        props.add(new ImportProperty(idUserPriceListDetailField, findProperty("id[UserPriceListDetail]").getMapping(userPriceListDetailKey)));
        fields.add(idUserPriceListDetailField);

        ImportField idBarcodeSkuField = new ImportField(findProperty("idBarcode[Sku]"));
        ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"), findProperty("skuBarcode[STRING[15]]").getMapping(idBarcodeSkuField));
        itemKey.skipKey = skipKeys;
        keys.add(itemKey);
        props.add(new ImportProperty(idBarcodeSkuField, findProperty("originalIdBarcodeSku[UserPriceListDetail]").getMapping(userPriceListDetailKey)));
        props.add(new ImportProperty(idBarcodeSkuField, findProperty("sku[UserPriceListDetail]").getMapping(userPriceListDetailKey),
                object(findClass("Item")).getMapping(itemKey)));
        fields.add(idBarcodeSkuField);

        ImportField idDataPriceListTypeField = new ImportField(findProperty("id[DataPriceListType]"));
        ImportKey<?> dataPriceListTypeKey = new ImportKey((CustomClass) findClass("DataPriceListType"),
                findProperty("dataPriceListType[VARSTRING[100]]").getMapping(idDataPriceListTypeField));
        keys.add(dataPriceListTypeKey);
        props.add(new ImportProperty(idDataPriceListTypeField, findProperty("id[PriceListType]").getMapping(dataPriceListTypeKey)));
        fields.add(idDataPriceListTypeField);

        ImportField namePriceListTypeField = new ImportField(findProperty("name[PriceListType]"));
        props.add(new ImportProperty(namePriceListTypeField, findProperty("name[PriceListType]").getMapping(dataPriceListTypeKey), true));
        fields.add(namePriceListTypeField);

        ImportField pricePriceListDetailField = new ImportField(findProperty("price[PriceListDetail,DataPriceListType]"));
        props.add(new ImportProperty(pricePriceListDetailField, findProperty("price[UserPriceListDetail,DataPriceListType]").getMapping(userPriceListDetailKey, dataPriceListTypeKey)));
        fields.add(pricePriceListDetailField);

        ImportField inPriceListPriceListTypeField = new ImportField(findProperty("in[UserPriceList,DataPriceListType]"));
        props.add(new ImportProperty(inPriceListPriceListTypeField, findProperty("in[UserPriceList,DataPriceListType]").getMapping(userPriceListKey, dataPriceListTypeKey), true));
        fields.add(inPriceListPriceListTypeField);

        ImportTable table = new ImportTable(fields, data);

        try (DataSession session = context.createSession()) {
            session.pushVolatileStats("IE_PL");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
        }
    }

    private List<List<List<Object>>> importDataFromWeb(ExecutionContext context, boolean useTor, boolean importItems, boolean onlyImages,
                                                       boolean smallImages, boolean importUserPriceLists, boolean skipKeys)
            throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, IOException {
        List<List<Object>> itemsList = new ArrayList<>();
        List<List<Object>> userPriceListsList = new ArrayList<>();
        Map<String, String> barcodeSet = getBarcodeSet(context);
        int imageCount = 0;
        int skipped = 0;

        NetLayer lowerNetLayer = useTor ? getNetLayer() : null;
        String idPriceList = "euroopt" + String.valueOf(Calendar.getInstance().getTimeInMillis());
        Map<String, String> itemURLMap = getItemURLMap(lowerNetLayer);
        int idPriceListDetail = 1;
        int i = 1;
        for (Map.Entry<String, String> itemURLEntry : itemURLMap.entrySet()) {
            String itemURL = itemURLEntry.getKey();
            String smallImage = itemURLEntry.getValue();
            ServerLoggers.importLogger.info(String.format(logPrefix + "parsing item page #%s: %s", i, (useTor ? mainPage : "") + itemURL));
            Document doc = getDocument(lowerNetLayer, itemURL);
            if (doc != null) {
                String title = doc.getElementsByTag("title").text();
                List<BigDecimal> price = getPrice(doc);
                Elements descriptionElement = doc.getElementsByClass("description");
                List<Node> descriptionAttributes = descriptionElement.size() == 0 ? new ArrayList<Node>() : descriptionElement.get(0).childNodes();
                String idBarcode = null;
                if (onlyImages) {
                    for (Node attribute : descriptionAttributes) {
                        if (attribute instanceof Element && ((Element) attribute).children().size() == 2) {
                            String type = parseChild((Element) attribute, 0);
                            String value = parseChild((Element) attribute, 1);
                            switch (type) {
                                case "Штрих-код:":
                                    idBarcode = value;
                                    break;
                            }
                        }
                    }
                    if (idBarcode != null && (!skipKeys || barcodeSet.containsKey(idBarcode))) {
                        if (importItems) {
                            byte[] imageBytes = getImage(lowerNetLayer, doc, title, smallImage, smallImages);
                            if (imageBytes != null)
                                imageCount++;
                            itemsList.add(Arrays.asList((Object) idBarcode, imageBytes));
                        }
                        if (importUserPriceLists) {
                            if (price.size() >= 1)
                                userPriceListsList.add(Arrays.asList((Object) idPriceList, "euroopt", idPriceList + "/" + idPriceListDetail, idBarcode, "euroopt_p", "Евроопт (акция)", price.get(0), true));
                            if (price.size() >= 2)
                                userPriceListsList.add(Arrays.asList((Object) idPriceList, "euroopt", idPriceList + "/" + idPriceListDetail, idBarcode, "euroopt", "Евроопт", price.get(1), true));
                            idPriceListDetail++;
                        }
                        //to avoid duplicates
                        barcodeSet.remove(idBarcode);
                    } else {
                        ServerLoggers.importLogger.info(logPrefix + (idBarcode == null ? "no barcode, item skipped" : "not in base, item skipped") + " (" + title + ")");
                        skipped++;
                    }
                } else {
                    String captionItem = doc.getElementsByTag("h1").text();
                    String idItemGroup = null;
                    String brandItem = null;
                    BigDecimal netWeight = null;
                    String UOMItem = null;
                    for (Node attribute : descriptionAttributes) {
                        if (attribute instanceof Element && ((Element) attribute).children().size() == 2) {
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
                        for (Element propertyRow : propertyRows) {
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

                        if (importItems) {
                            byte[] imageBytes = getImage(lowerNetLayer, doc, title, smallImage, smallImages);
                            if (imageBytes != null)
                                imageCount++;
                            itemsList.add(Arrays.asList((Object) idBarcode, idItemGroup, captionItem, netWeight, descriptionItem, compositionItem, proteinsItem,
                                    fatsItem, carbohydratesItem, energyItem, imageBytes, manufacturerItem, UOMItem,
                                    brandItem));
                        }
                        if (importUserPriceLists) {
                            if (price.size() >= 1)
                                userPriceListsList.add(Arrays.asList((Object) idPriceList, "euroopt", idPriceList + "/" + idPriceListDetail, idBarcode, "euroopt_p", "Евроопт (акция)", price.get(0), true));
                            if (price.size() >= 2)
                                userPriceListsList.add(Arrays.asList((Object) idPriceList, "euroopt", idPriceList + "/" + idPriceListDetail, idBarcode, "euroopt", "Евроопт", price.get(1), true));
                            idPriceListDetail++;
                        }
                        //to avoid duplicates
                        barcodeSet.remove(idBarcode);
                    } else {
                        ServerLoggers.importLogger.info(logPrefix + (idBarcode == null ? "no barcode, item skipped" : "not in base, item skipped") + " (" + title + ")");
                        skipped++;
                    }
                }
            }
            i++;
        }
        ServerLoggers.importLogger.info(String.format(logPrefix + "read finished. %s items (%s with images), %s items without barcode skipped, %s priceLists", itemsList.size(), imageCount, skipped, userPriceListsList.size()));
        return Arrays.asList(itemsList, userPriceListsList);
    }

    private byte[] getImage(NetLayer lowerNetLayer, Document doc, String title, String smallImage, boolean smallImages) throws IOException {
        File imageItem = null;
        try {
            String imageURL;
            if (smallImages) {
                imageURL = smallImage;
            } else {
                Elements prodImage = doc.getElementsByClass("increaseImage");
                imageURL = prodImage.size() == 0 ? null : prodImage.get(0).attr("href");
            }
            imageItem = readImage(lowerNetLayer, imageURL);
            byte[] imageBytes = imageItem == null ? null : IOUtils.getFileBytes(imageItem);
            ServerLoggers.importLogger.info(logPrefix + (imageBytes != null ? "image read successful" : imageURL == null ? "no image found" : "image read failed") + " " + title);
            return imageBytes;
        } finally {
            if (imageItem != null && !imageItem.delete())
                imageItem.deleteOnExit();
        }
    }

    private String parseChild(Element element, int child) {
        return element.children().size() > child ? Jsoup.parse(element.childNode(child).outerHtml()).text() : "";
    }

    private List<BigDecimal> getPrice(Document doc) {
        BigDecimal newPrice = null;
        BigDecimal oldPrice = null;
        try {
            boolean redPrice = doc.getElementsByClass("product_card").first().classNames().contains("red");
            Element priceElement = doc.getElementsByClass("price").first();
            if (priceElement != null) {
                Elements oldPriceElement = priceElement.getElementsByClass("Old_price");
                String oldPriceValue = oldPriceElement == null || !redPrice ? null : oldPriceElement.text().replace(" ", "");
                oldPrice = formatPrice(oldPriceValue);
                String priceValue = (oldPriceElement != null && oldPriceElement.size() != 0 ? priceElement.text().replace(oldPriceElement.first().text(), "") : priceElement.text()).replace(" ", "");
                newPrice = formatPrice(priceValue);
            }
        } catch (Exception e) {
            newPrice = null;
        }
        return oldPrice == null ? Arrays.asList(null, newPrice) : Arrays.asList(newPrice, oldPrice);
    }

    private BigDecimal formatPrice(String value) {
        return value == null || value.isEmpty() ? null : new BigDecimal(value.replace("р", "").replace("к.", ""));
    }

    private Map<String, String> getItemURLMap(NetLayer lowerNetLayer) throws IOException {
        Map<String, String> itemsMap = new LinkedHashMap<>();
        for (String itemGroupURL : getItemGroupURLSet(lowerNetLayer)) {
            int page = 1;
            String prevPageHash = null;
            boolean notLastPage = true;
            while (notLastPage) {
                int step = 1;
                String prevStepHash = null;
                boolean notLastStep = true;
                String pageHash = "";
                while (notLastStep) {
                    Map<String, String> stepItemsMap = new LinkedHashMap<>();
                    String stepHash = "";
                    String url = itemGroupURL + "?page=" + page + "&lazy_steep=" + step;
                    ServerLoggers.importLogger.info(String.format(logPrefix + "reading itemGroup url %s", url));
                    Document doc = getDocument(lowerNetLayer, url);
                    if (doc != null) {
                        String title = doc.getElementsByTag("title").text();
                        for (Element item : doc.getElementsByTag("a")) {
                            String href = item.attr("href");
                            if (href != null && href.matches(itemPattern)) {
                                Elements images = item.getElementsByTag("img");
                                String image = images.isEmpty() ? null : images.get(0).attr("src");
                                if (lowerNetLayer != null)
                                    href = href.replace(mainPage, "");
                                if(stepItemsMap.get(href) == null)
                                    stepItemsMap.put(href, image);
                            }
                        }
                        for (Map.Entry<String, String> stepItem : stepItemsMap.entrySet()) {
                            if (!itemsMap.containsKey(stepItem.getKey())) {
                                ServerLoggers.importLogger.info(String.format(logPrefix + "preparing item page #%s: %s (%s)", itemsMap.size() + 1, stepItem, title));
                                itemsMap.put(stepItem.getKey(), stepItem.getValue());
                            }
                            stepHash += stepItem;
                        }
                        pageHash += stepHash;
                    }
                    notLastStep = !stepItemsMap.isEmpty() && !stepHash.equals(prevStepHash);
                    prevStepHash = stepHash;
                    step++;
                }
                page++;
                notLastPage = !pageHash.equals(prevPageHash);
                prevPageHash = pageHash;
            }
        }
        return itemsMap;
    }

    private Set<String> getItemGroupURLSet(NetLayer lowerNetLayer) throws IOException {
        Set<String> itemGroupsSet = new HashSet<>();
        String mainUrl = lowerNetLayer == null ? mainPage + "/" : "/catalog/";
        ServerLoggers.importLogger.info(String.format(logPrefix + "reading url %s", mainUrl));
        Document doc = getDocument(lowerNetLayer, mainUrl);
        if (doc != null) {
            for (Element url : doc.getElementsByTag("a")) {
                String href = url.attr("href");
                if (href != null && href.matches(itemGroupPattern)) {
                    if (lowerNetLayer != null)
                        href = href.replace(mainPage, "");
                    if (!itemGroupsSet.contains(href)) {
                        ServerLoggers.importLogger.info(String.format(logPrefix + "preparing item group page #%s: %s", itemGroupsSet.size() + 1, href));
                        itemGroupsSet.add(href);
                    }
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
        query.addProperty("idBarcode", findProperty("id[Barcode]").getExpr(context.getModifier(), barcodeExpr));
        query.addProperty("idItemGroupBarcode", findProperty("idItemGroup[Barcode]").getExpr(context.getModifier(), barcodeExpr));
        query.and(findProperty("id[Barcode]").getExpr(barcodeExpr).getWhere());
        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> itemResult = query.executeClasses(context);
        for (ImMap<Object, ObjectValue> entry : itemResult.values()) {
            String idBarcode = trim((String) entry.get("idBarcode").getValue());
            String idItemGroupBarcode = trim((String) entry.get("idItemGroupBarcode").getValue());
            barcodeSet.put(idBarcode, idItemGroupBarcode);
        }
        return barcodeSet;
    }

    protected File readImage(NetLayer lowerNetLayer, String url) throws IOException {
        if (url != null) {
            int count = 3;
            while (count > 0) {
                File file;
                try {
                    Thread.sleep(50);
                    if (lowerNetLayer == null) {
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
                        return file;
                    } else {
                        URLConnection urlConnection = getTorConnection(lowerNetLayer, url);
                        try (InputStream responseBodyIS = urlConnection.getInputStream()) {
                            file = File.createTempFile("image", ".tmp");
                            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                                int read;
                                byte[] bytes = new byte[1024];
                                while ((read = responseBodyIS.read(bytes)) != -1) {
                                    outputStream.write(bytes, 0, read);
                                }
                            }
                        }
                        return file;
                    }
                } catch (HttpStatusException e) {
                    count--;
                    if (count <= 0)
                        ServerLoggers.importLogger.error(logPrefix + "error for url " + url + ": ", e);
                } catch (InterruptedException e) {
                    throw Throwables.propagate(e);
                }
            }
        }
        return null;
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
