package lsfusion.erp.region.by.euroopt;

import com.google.common.base.Throwables;
import lsfusion.base.IOUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.DefaultImportActionProperty;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ConcreteCustomClass;
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
import org.jsoup.select.Elements;

import java.io.*;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLConnection;
import java.sql.SQLException;
import java.util.*;

public class ImportEurooptActionProperty extends DefaultImportActionProperty {

    String mainPage = "http://dostavka.evroopt.by/";
    String itemGroupPattern = "http:\\/\\/dostavka\\.evroopt\\.by\\/catalog\\/\\d+\\.html";
    String itemPattern = "http:\\/\\/dostavka\\.evroopt\\.by\\/catalog\\/\\d+_\\d+\\.html";
    String userAgent = "Mozilla/5.0 (Windows NT 6.2; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/32.0.1667.0 Safari/537.36";

    public ImportEurooptActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            boolean importItems = findProperty("importEurooptItems").read(context) != null;
            boolean importUserPriceLists = findProperty("importEurooptUserPriceLists").read(context) != null;
            boolean skipKeys = findProperty("importEurooptSkipKeys").read(context) != null;

            List<List<List<Object>>> data = importDataFromWeb(context, importItems, importUserPriceLists, skipKeys);

            if (importItems)
                importItems(context, data.get(0), skipKeys);
            if (importUserPriceLists)
                importUserPriceLists(context, data.get(1), skipKeys);

        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }

    }

    private void importItems(ExecutionContext context, List<List<Object>> data, boolean skipKeys) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
        List<ImportField> fields = new ArrayList<ImportField>();
        List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

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
        ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) findClass("ItemGroup"),
                findProperty("itemGroupId").getMapping(idItemGroupField));
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
        ImportKey<?> manufacturerKey = new ImportKey((ConcreteCustomClass) findClass("Manufacturer"),
                findProperty("manufacturerId").getMapping(idManufacturerField));
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
        keys.add(brandKey);
        props.add(new ImportProperty(idBrandField, findProperty("idBrand").getMapping(brandKey)));
        props.add(new ImportProperty(idBrandField, findProperty("nameBrand").getMapping(brandKey), true));
        props.add(new ImportProperty(idBrandField, findProperty("brandItem").getMapping(itemKey),
                object(findClass("Brand")).getMapping(brandKey), true));
        fields.add(idBrandField);

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

        List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
        List<ImportField> fields = new ArrayList<ImportField>();
        List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

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

    private List<List<List<Object>>> importDataFromWeb(ExecutionContext context, boolean importItems, boolean importUserPriceLists, boolean skipKeys)
            throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        List<List<Object>> itemsList = new ArrayList<List<Object>>();
        List<List<Object>> userPriceListsList = new ArrayList<List<Object>>();
        Map<String, String> barcodeSet = getBarcodeSet(context);
        try {

            String idPriceList = String.valueOf(Calendar.getInstance().getTimeInMillis());
            Set<String> itemsSet = getItemsSet();
            int idPriceListDetail = 1;
            for (String item : itemsSet) {
                    Document doc = getDocument(item);
                if (doc != null) {
                    Elements prodImage = doc.getElementsByClass("prodImage");
                    File imageItem = prodImage.size() == 0 ? null : readImage(doc.getElementsByClass("prodImage").get(0).attr("src"));
                    
                    String captionItem = doc.getElementsByTag("h1").text();
                    BigDecimal price = getPrice(doc);
                    Elements itemAttributes = doc.getElementsByClass("rubric_product_desc_block");
                    String idBarcode = null;
                    String idItemGroup = null;
                    String brandItem = null;
                    BigDecimal netWeight = null;
                    String UOMItem = null;
                    for (Element itemAttribute : itemAttributes) {
                        String[] textAttribute = itemAttribute.text().split(":");
                        if (textAttribute.length == 2) {
                            if (textAttribute[0].equals("Штрих-код")) {
                                idBarcode = textAttribute[1];
                                idItemGroup = barcodeSet.containsKey(idBarcode) ? barcodeSet.get(idBarcode) : "ВСЕ";
                            }
                            else if (textAttribute[0].equals("Торговая марка"))
                                brandItem = textAttribute[1];
                            else if (textAttribute[0].equals("Масса")) {
                                String[] split = textAttribute[1].split(" ");
                                netWeight = new BigDecimal(split[0]);
                                UOMItem = split.length >= 2 ? split[1] : null;
                            }
                        }
                    }
                    Elements descriptionAttributes = doc.getElementsByClass("field_desc");
                    String descriptionItem = null;
                    String compositionItem = null;
                    BigDecimal proteinsItem = null;
                    BigDecimal fatsItem = null;
                    BigDecimal carbohydratesItem = null;
                    BigDecimal energyItem = null;
                    String manufacturerItem = null;
                    for (Element descriptionAttribute : descriptionAttributes) {
                        String name = descriptionAttribute.parent().getElementsByClass("field_name").text();
                        String description = descriptionAttribute.parent().getElementsByClass("field_desc").text();
                        if (description != null && !description.isEmpty()) {
                            if (name.equals("Краткое описание"))
                                descriptionItem = description;
                            else if (name.equals("Состав"))
                                compositionItem = description;
                            else if (name.equals("Белки"))
                                proteinsItem = parseBigDecimalWeight(description);
                            else if (name.equals("Жиры"))
                                fatsItem = parseBigDecimalWeight(description);
                            else if (name.equals("Углеводы"))
                                carbohydratesItem = parseBigDecimalWeight(description);
                            else if (name.equals("Энергетическая ценность на 100 г"))
                                energyItem = parseBigDecimalWeight(description.split("\\s(ккал|калл)")[0]);
                            else if (name.equals("Производитель"))
                                manufacturerItem = description;
                        }
                    }
                    if (idBarcode != null && (!skipKeys || barcodeSet.containsKey(idBarcode))) {
                        if (importItems)
                            itemsList.add(Arrays.asList((Object) idBarcode, idItemGroup, captionItem, netWeight, descriptionItem, compositionItem, proteinsItem,
                                    fatsItem, carbohydratesItem, energyItem, IOUtils.getFileBytes(imageItem), manufacturerItem, UOMItem, brandItem));
                        if (importUserPriceLists) {
                            userPriceListsList.add(Arrays.asList((Object) idPriceList, String.valueOf(idPriceListDetail), idBarcode, "euroopt", "Цена (Евроопт)", price, true));
                            idPriceListDetail++;
                        }
                        //to avoid duplicates
                        barcodeSet.remove(idBarcode);
                    }
                    if(imageItem != null)
                        imageItem.delete();
                }
            }
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return Arrays.asList(itemsList, userPriceListsList);
    }

    private BigDecimal getPrice(Document doc) {
        BigDecimal price = null;
        try {
            Element priceElement = doc.getElementsByClass("price_val").first();
            if(priceElement != null) {
                String priceValue = priceElement.textNodes().get(0).text().replace(" ", "");
                if (priceValue.isEmpty()) {
                    priceValue = priceElement.getElementsByClass("new_price").first().textNodes().get(0).text().replace(" ", "").replace("\n", "");
                }
                price = new BigDecimal(priceValue);
            }
        } catch (Exception e) {
            price = null;
        }
        return price;
    }

    private Set<String> getItemsSet() throws IOException {
        Set<String> itemsSet = new LinkedHashSet<String>();
        for (String itemGroup : getItemGroupsSet()) {
            Document doc = getDocument(itemGroup);
            if (doc != null) {
                for (Element item : doc.getElementsByTag("a")) {
                    String href = item.attr("href");
                    if (href != null && href.matches(itemPattern))
                        itemsSet.add(href);
                }
            }
        }
        return itemsSet;
    }

    private Set<String> getItemGroupsSet() throws IOException {
        Set<String> itemGroupsSet = new HashSet<String>();
        Document doc = getDocument(mainPage);
        if(doc != null) {
            for (Element url : doc.getElementsByTag("a")) {
                String href = url.attr("href");
                if (href != null && href.matches(itemGroupPattern))
                    itemGroupsSet.add(href);
            }
        }
        return itemGroupsSet;
    }

    private Map<String, String> getBarcodeSet(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, String> barcodeSet = new HashMap<String, String>();
        KeyExpr barcodeExpr = new KeyExpr("barcode");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "barcode", barcodeExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
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
