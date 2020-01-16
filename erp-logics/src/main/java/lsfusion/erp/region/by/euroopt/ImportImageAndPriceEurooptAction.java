package lsfusion.erp.region.by.euroopt;

import com.google.common.base.Throwables;
import lsfusion.base.Pair;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.ERPLoggers;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import org.castor.core.util.Base64Encoder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.silvertunnel_ng.netlib.api.NetLayer;

import java.io.*;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;

public class ImportImageAndPriceEurooptAction extends EurooptAction {

    public ImportImageAndPriceEurooptAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            String mainPage = (String) findProperty("captionMainPage[]").read(context);
            if(mainPage != null) {
                boolean useTor = findProperty("ImportEuroopt.useTor[]").read(context) != null;
                boolean importImages = findProperty("importImages[]").read(context) != null;
                boolean importPrices = findProperty("importPrices[]").read(context) != null;

                if (importImages || importPrices) {
                    List<JSONArray> data = getData(context, mainPage, useTor, importImages, importPrices);
                    if (!data.get(0).isEmpty() || !data.get(1).isEmpty()) {
                        if (importImages)
                            findProperty("importImageFile[]").change(new RawFileData(data.get(0).toString().getBytes(StandardCharsets.UTF_8)), context);
                        if (importPrices)
                            findProperty("importPriceFile[]").change(new RawFileData(data.get(1).toString().getBytes(StandardCharsets.UTF_8)), context);

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

    private List<JSONArray> getData(ExecutionContext<ClassPropertyInterface> context, String mainPage, boolean useTor, boolean importImages, boolean importPrices)
            throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, IOException {
        JSONArray imagesJSON = new JSONArray();
        JSONArray pricesJSON = new JSONArray();

        String id = mainPage.contains("gipermall") ? "gipermall" : "edostavka";
        String name = mainPage.contains("gipermall") ? "Гипермолл" : "Е-доставка";

        NetLayer lowerNetLayer = useTor ? getNetLayer() : null;
        String idPriceList = id + Calendar.getInstance().getTimeInMillis();
        Pair<List<String>, Map<String, String>> itemListData = getItemListData(context);
        List<String> itemListURLList = itemListData.first;
        Map<String, String> barcodeMap = itemListData.second;

        if(!itemListURLList.isEmpty()) {

            int idPriceListDetail = 1;
            int i = 1;
            for (String itemListURL : itemListURLList) {
                ERPLoggers.importLogger.info(String.format(logPrefix + "parsing itemGroup page #%s of %s: %s", i, itemListURLList.size(), (useTor ? mainPage : "") + itemListURL));
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
                                        if (imageBytes != null) {
                                            JSONObject imageJSON = new JSONObject();
                                            imageJSON.put("idBarcode", idBarcode);
                                            imageJSON.put("image", new String(Base64Encoder.encode(imageBytes.getBytes())));
                                            imagesJSON.put(imageJSON);
                                        }
                                    }

                                    if (importPrices) {
                                        List<BigDecimal> prices = getPrices(productCard);
                                        if (prices.size() >= 1) {
                                            BigDecimal price1 = prices.get(0);
                                            if (price1 != null) {
                                                pricesJSON.put(getPriceJSON(idPriceList, id, idPriceListDetail, idBarcode, name, price1, true));
                                            }
                                            if (prices.size() >= 2) {
                                                BigDecimal price2 = prices.get(1);
                                                if (price2 != null) {
                                                    pricesJSON.put(getPriceJSON(idPriceList, id, idPriceListDetail, idBarcode, name, price2, false));
                                                }
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
                ERPLoggers.importLogger.info(String.format(logPrefix + "reading images finished. %s items with images found", imagesJSON.length()));
            if (importPrices)
                ERPLoggers.importLogger.info(String.format(logPrefix + "reading prices finished. %s prices for %s items found", pricesJSON.length(), idPriceListDetail - 1));
        } else {
            context.delayUserInteraction(new MessageClientAction("Не выбрано ни одного импортированного товара", "Ошибка"));
        }
        return Arrays.asList(imagesJSON, pricesJSON);
    }

    private JSONObject getPriceJSON(String idPriceList, String id, int idPriceListDetail, String idBarcode, String namePriceListType, BigDecimal price, boolean action) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("idPriceList", idPriceList);
        jsonObject.put("idOperation", id);
        jsonObject.put("idUserPriceListDetail", idPriceList + "/" + idPriceListDetail);
        jsonObject.put("idBarcodeSku", idBarcode);
        jsonObject.put("idDataPriceListType", id);
        jsonObject.put("namePriceListType", namePriceListType);
        jsonObject.put("pricePriceListDetail", price);
        jsonObject.put("action", action);
        return jsonObject;
    }

    private RawFileData getImage(NetLayer lowerNetLayer, String barcode, String mainPage, String smallImage) {
        File imageItem = null;
        try {
            imageItem = readImage(lowerNetLayer, mainPage, smallImage);
            RawFileData imageBytes = imageItem == null ? null : new RawFileData(imageItem);
            ERPLoggers.importLogger.info(logPrefix + (imageBytes != null ? "image read successful" : smallImage == null ? "no image found" : "image read failed") + " for barcode " + barcode);
            return imageBytes;
        } catch (IOException e) {
            ERPLoggers.importLogger.info(logPrefix + "image read failed for barcode " + barcode);
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
                        ERPLoggers.importLogger.error(logPrefix + "error for url " + url + ": ", e);
                } catch (InterruptedException e) {
                    throw Throwables.propagate(e);
                }
            }
        }
        return null;
    }

    private Pair<List<String>, Map<String, String>> getItemListData(ExecutionContext<ClassPropertyInterface> context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<String> itemListURLList = new ArrayList<>();
        Map<String, String> barcodeMap = new HashMap<>();
        KeyExpr itemListExpr = new KeyExpr("eurooptItemList");
        KeyExpr itemExpr = new KeyExpr("eurooptItem");
        ImRevMap<Object, KeyExpr> itemListKeys = MapFact.toRevMap("eurooptItemList", itemListExpr, "eurooptItem", itemExpr);

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
