package lsfusion.erp.region.by.euroopt;

import com.google.common.base.Throwables;
import lsfusion.base.Pair;
import lsfusion.base.file.RawFileData;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.form.property.Compare;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.silvertunnel_ng.netlib.api.NetLayer;

import java.io.*;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLConnection;
import java.sql.SQLException;
import java.util.*;

public class ImportImageAndPriceEurooptActionProperty extends EurooptActionProperty {

    public ImportImageAndPriceEurooptActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            String mainPage = (String) findProperty("captionMainPage[]").read(context);
            if(mainPage != null) {
                boolean useTor = findProperty("ImportEuroopt.useTor[]").read(context) != null;
                boolean importImages = findProperty("importImages[]").read(context) != null;
                boolean importPrices = findProperty("importPrices[]").read(context) != null;

                if (importImages || importPrices) {
                    List<List<List<Object>>> data = getData(context, mainPage, useTor, importImages, importPrices);
                    if (!data.get(0).isEmpty() || !data.get(1).isEmpty()) {
                        if (importImages)
                            importImages(context, data.get(0));
                        if (importPrices)
                            importPrices(context, data.get(1));

                        context.delayUserInteraction(new MessageClientAction("Импорт успешно завершён", "Импорт Евроопт"));
                    }
                } else {
                    context.delayUserInteraction(new MessageClientAction("Выберите хотя бы одну из опций импорта", "Ошибка"));
                }
            } else {
                context.delayUserInteraction(new MessageClientAction("Не выбрана главная страница", "Ошибка"));
            }

        } catch (IOException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }

    }

    private void importImages(ExecutionContext context, List<List<Object>> data) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<ImportProperty<?>> props = new ArrayList<>();
        List<ImportField> fields = new ArrayList<>();
        List<ImportKey<?>> keys = new ArrayList<>();

        ImportField idBarcodeSkuField = new ImportField(findProperty("idBarcode[Sku]"));
        ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                findProperty("skuBarcode[STRING[15]]").getMapping(idBarcodeSkuField));
        itemKey.skipKey = true;
        keys.add(itemKey);
        fields.add(idBarcodeSkuField);

        ImportField dataImageItemField = new ImportField(findProperty("dataImage[Item]"));
        props.add(new ImportProperty(dataImageItemField, findProperty("dataImage[Item]").getMapping(itemKey), true));
        props.add(new ImportProperty(idBarcodeSkuField, findProperty("id[Item]").getMapping(itemKey), true));
        fields.add(dataImageItemField);

        ImportTable table = new ImportTable(fields, data);

        try (ExecutionContext.NewSession newContext = context.newSession()) {
            IntegrationService service = new IntegrationService(newContext, table, keys, props);
            service.synchronize(true, false);
            newContext.apply();
        }
    }

    private void importPrices(ExecutionContext context, List<List<Object>> data) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<ImportProperty<?>> props = new ArrayList<>();
        List<ImportField> fields = new ArrayList<>();
        List<ImportKey<?>> keys = new ArrayList<>();

        ImportField idUserPriceListField = new ImportField(findProperty("id[UserPriceList]"));
        ImportKey<?> userPriceListKey = new ImportKey((CustomClass) findClass("UserPriceList"),
                findProperty("userPriceList[VARSTRING[100]]").getMapping(idUserPriceListField));
        keys.add(userPriceListKey);
        props.add(new ImportProperty(idUserPriceListField, findProperty("id[UserPriceList]").getMapping(userPriceListKey)));
        fields.add(idUserPriceListField);

        ImportField isPostedUserPriceListField = new ImportField(findProperty("isPosted[UserPriceList]"));
        props.add(new ImportProperty(isPostedUserPriceListField, findProperty("isPosted[UserPriceList]").getMapping(userPriceListKey)));
        fields.add(isPostedUserPriceListField);

        ImportField idOperationField = new ImportField(findProperty("id[PriceList.Operation]"));
        ImportKey<?> operationKey = new ImportKey((CustomClass) findClass("PriceList.Operation"),
                findProperty("PriceList.operation[VARISTRING[100]]").getMapping(idOperationField));
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
        itemKey.skipKey = true;
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

        try (ExecutionContext.NewSession newContext = context.newSession()) {
            IntegrationService service = new IntegrationService(newContext, table, keys, props);
            service.synchronize(true, false);
            newContext.apply();
        }
    }

    private List<List<List<Object>>> getData(ExecutionContext context, String mainPage, boolean useTor, boolean importImages, boolean importPrices)
            throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, IOException {
        List<List<Object>> imagesList = new ArrayList<>();
        List<List<Object>> userPriceListsList = new ArrayList<>();

        String id = mainPage.contains("gipermall") ? "gipermall" : "edostavka";
        String name = mainPage.contains("gipermall") ? "Гипермолл" : "Е-доставка";

        NetLayer lowerNetLayer = useTor ? getNetLayer() : null;
        String idPriceList = id + String.valueOf(Calendar.getInstance().getTimeInMillis());
        Pair<List<String>, Map<String, String>> itemListData = getItemListData(context);
        List<String> itemListURLList = itemListData.first;
        Map<String, String> barcodeMap = itemListData.second;

        if(!itemListURLList.isEmpty()) {

            int idPriceListDetail = 1;
            int i = 1;
            for (String itemListURL : itemListURLList) {
                ServerLoggers.importLogger.info(String.format(logPrefix + "parsing itemGroup page #%s of %s: %s", i, itemListURLList.size(), (useTor ? mainPage : "") + itemListURL));
                Document doc = getDocument(lowerNetLayer, mainPage, itemListURL);
                if (doc != null) {

                    for (Element productCard : doc.getElementsByClass("products_card")) {

                        Element titleElement = productCard.getElementsByClass("title").first();
                        if (titleElement != null) {
                            Element itemURLElement = titleElement.getElementsByTag("a").first();
                            if (itemURLElement != null) {
                                String itemURL = itemURLElement.attr("href");
                                String idBarcode = barcodeMap.get(itemURL);
                                if (idBarcode != null) {

                                    if (importImages) {
                                        String image = productCard.getElementsByClass("img").get(0).getElementsByClass("retina_redy").get(0).attr("src");
                                        RawFileData imageBytes = getImage(lowerNetLayer, idBarcode, mainPage, image);
                                        if (imageBytes != null)
                                            imagesList.add(Arrays.asList((Object) idBarcode, imageBytes));
                                    }

                                    if (importPrices) {
                                        List<BigDecimal> prices = getPrices(productCard);
                                        if (prices.size() >= 1) {
                                            BigDecimal price1 = prices.get(0);
                                            if (price1 != null)
                                                userPriceListsList.add(Arrays.asList((Object) idPriceList, true, id, idPriceList + "/" + idPriceListDetail, idBarcode, id + "_p", name + "(акция)", price1, true));
                                            if (prices.size() >= 2) {
                                                BigDecimal price2 = prices.get(1);
                                                if (price2 != null)
                                                    userPriceListsList.add(Arrays.asList((Object) idPriceList, true, id, idPriceList + "/" + idPriceListDetail, idBarcode, id, name, price2, true));
                                            }
                                            idPriceListDetail++;
                                        }
                                    }
                                }
                            }
                        }
                    }

                }
                i++;
            }
            if (importImages)
                ServerLoggers.importLogger.info(String.format(logPrefix + "reading images finished. %s items with images found", imagesList.size()));
            if (importPrices)
                ServerLoggers.importLogger.info(String.format(logPrefix + "reading prices finished. %s prices for %s items found", userPriceListsList.size(), idPriceListDetail - 1));
        } else {
            context.delayUserInteraction(new MessageClientAction("Не выбрано ни одного импортированного товара", "Ошибка"));
        }
        return Arrays.asList(imagesList, userPriceListsList);
    }

    private RawFileData getImage(NetLayer lowerNetLayer, String barcode, String mainPage, String smallImage) {
        File imageItem = null;
        try {
            imageItem = readImage(lowerNetLayer, mainPage, smallImage);
            RawFileData imageBytes = imageItem == null ? null : new RawFileData(imageItem);
            ServerLoggers.importLogger.info(logPrefix + (imageBytes != null ? "image read successful" : smallImage == null ? "no image found" : "image read failed") + " for barcode " + barcode);
            return imageBytes;
        } catch (IOException e) {
            ServerLoggers.importLogger.info(logPrefix + "image read failed for barcode " + barcode);
            return null;
        } finally {
            if (imageItem != null && !imageItem.delete())
                imageItem.deleteOnExit();
        }
    }


    private List<BigDecimal> getPrices(Element productCard) {
        BigDecimal newPrice = null;
        BigDecimal oldPrice = null;
        try {
            Element priceElement = productCard.getElementsByClass("price").first();
            if (priceElement != null) {
                boolean red = productCard.classNames().contains("red");
                Elements oldPriceElement = priceElement.getElementsByClass("Old_price");
                String oldPriceValue = oldPriceElement == null || !red ? null : oldPriceElement.text().replace(" ", "");
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

    protected File readImage(NetLayer lowerNetLayer, String mainPage, String url) throws IOException {
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
                        URLConnection urlConnection = getTorConnection(lowerNetLayer, mainPage, url);
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

    private Pair<List<String>, Map<String, String>> getItemListData(ExecutionContext context) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<String> itemListURLList = new ArrayList<>();
        Map<String, String> barcodeMap = new HashMap<>();
        KeyExpr itemListExpr = new KeyExpr("eurooptItemList");
        KeyExpr itemExpr = new KeyExpr("eurooptItem");
        ImRevMap<Object, KeyExpr> itemListKeys = MapFact.toRevMap((Object) "eurooptItemList", itemListExpr, "eurooptItem", itemExpr);

        QueryBuilder<Object, Object> query = new QueryBuilder<>(itemListKeys);
        query.addProperty("urlItemList", findProperty("url[EurooptItemList]").getExpr(context.getModifier(), itemListExpr));
        query.addProperty("urlItem", findProperty("url[EurooptItem]").getExpr(context.getModifier(), itemExpr));
        query.addProperty("barcode", findProperty("idBarcode[EurooptItem]").getExpr(context.getModifier(), itemExpr));
        query.and(findProperty("in[EurooptItem]").getExpr(context.getModifier(), itemExpr).getWhere());
        query.and(findProperty("sku[EurooptItem]").getExpr(context.getModifier(), itemExpr).getWhere());
        query.and(findProperty("eurooptItemList[EurooptItem]").getExpr(context.getModifier(), itemExpr).compare(itemListExpr, Compare.EQUALS));
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context);
        for (ImMap<Object, Object> entry : result.values()) {
            String urlItemList = trim((String) entry.get("urlItemList"));
            String urlItem = trim((String) entry.get("urlItem"));
            String barcode = trim((String) entry.get("barcode"));
            if(!itemListURLList.contains(urlItemList))
                itemListURLList.add(urlItemList);
            barcodeMap.put(urlItem, barcode);
        }
        return Pair.create(itemListURLList, barcodeMap);
    }

}
