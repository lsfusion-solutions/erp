package lsfusion.erp.region.by.euroopt;

import com.google.common.base.Throwables;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.ERPLoggers;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.silvertunnel_ng.netlib.api.NetLayer;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ImportItemsInfoEurooptAction extends EurooptAction {

    public ImportItemsInfoEurooptAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            String mainPage = (String) findProperty("captionMainPage[]").read(context);
            if(mainPage != null) {
                boolean useTor = findProperty("ImportEuroopt.useTor[]").read(context) != null;
                boolean onlyBarcode = findProperty("onlyBarcode[]").read(context) != null;

                JSONArray itemsJSON = getItemsInfo(context, mainPage, useTor, onlyBarcode);
                findProperty("importItemsInfoFile[]").change(new RawFileData(itemsJSON.toString().getBytes(StandardCharsets.UTF_8)), context);
            } else {
                context.delayUserInteraction(new MessageClientAction("Не выбрана главная страница", "Ошибка"));
            }

        } catch (IOException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }

    }

    private JSONArray getItemsInfo(ExecutionContext context, String mainPage, boolean useTor, boolean onlyBarcode) throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, IOException {
        JSONArray itemsJSON = new JSONArray();
        List<String> itemURLs = getItemURLs(context);
        if (!itemURLs.isEmpty()) {
            ERPLoggers.importLogger.info(String.format(logPrefix + "import %s item(s)", itemURLs.size()));
            int noBarcodeCount = 0;
            NetLayer lowerNetLayer = useTor ? getNetLayer() : null;

            int i = 1;
            for (String itemURL : itemURLs) {
                JSONObject itemJSON = new JSONObject();
                ERPLoggers.importLogger.info(String.format(logPrefix + "parsing item page #%s of %s: %s", i, itemURLs.size(), (useTor ? mainPage : "") + itemURL));
                Document doc = getDocument(lowerNetLayer, mainPage, itemURL);
                if (doc != null) {
                    String title = doc.getElementsByTag("title").text().replace(" - Каталог товаров", "");
                    Elements descriptionElement = doc.getElementsByClass("description");
                    List<Node> descriptionAttributes = descriptionElement.size() == 0 ? new ArrayList<>() : descriptionElement.get(0).childNodes();
                    String idBarcode = null;
                    String captionItem = doc.getElementsByTag("h1").text();
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

                    Boolean noBarcode = null;
                    if (idBarcode == null) {
                        noBarcode = true;
                        noBarcodeCount++;
                    }

                    itemJSON.put("itemURL", itemURL);
                    itemJSON.put("idBarcode", idBarcode);
                    itemJSON.put("noBarcode", noBarcode);

                    if (!onlyBarcode) {
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

                        itemJSON.put("captionItem", captionItem);
                        itemJSON.put("netWeight", netWeight);
                        itemJSON.put("descriptionItem", descriptionItem);
                        itemJSON.put("compositionItem", compositionItem);
                        itemJSON.put("proteinsItem", proteinsItem);
                        itemJSON.put("fatsItem", fatsItem);
                        itemJSON.put("carbohydratesItem", carbohydratesItem);
                        itemJSON.put("energyItem", energyItem);
                        itemJSON.put("manufacturerItem", manufacturerItem);
                        itemJSON.put("UOMItem", UOMItem);
                        itemJSON.put("brandItem", brandItem);

                    }
                    itemsJSON.put(itemJSON);
                    ERPLoggers.importLogger.info(String.format(logPrefix + "parsed item page #%s of %s: %s", i, itemURLs.size(), title));

                }
                i++;
            }
            ERPLoggers.importLogger.info(String.format(logPrefix + "read finished. %s items, %s items without barcode skipped", itemsJSON.length(), noBarcodeCount));
            if (itemsJSON.isEmpty())
                context.delayUserInteraction(new MessageClientAction("Не найдёно ни одного существующего в базе товара!", "Ошибка"));
        } else {
            context.delayUserInteraction(new MessageClientAction("Не выбрано ни одного товара!", "Ошибка"));
        }
        return itemsJSON;
    }

    private String parseChild(Element element, int child) {
        return element.children().size() > child ? Jsoup.parse(element.childNode(child).outerHtml()).text() : "";
    }

    private List<String> getItemURLs(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<String> itemURLs = new ArrayList<>();
        KeyExpr itemExpr = new KeyExpr("eurooptItem");
        ImRevMap<Object, KeyExpr> itemKeys = MapFact.singletonRev((Object) "eurooptItem", itemExpr);

        QueryBuilder<Object, Object> itemQuery = new QueryBuilder<>(itemKeys);
        itemQuery.addProperty("url", findProperty("url[EurooptItem]").getExpr(context.getModifier(), itemExpr));
        itemQuery.and(findProperty("in[EurooptItem]").getExpr(context.getModifier(), itemExpr).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> itemResult = itemQuery.execute(context);
        for (ImMap<Object, Object> entry : itemResult.values()) {
            itemURLs.add(trim((String) entry.get("url")));
        }
        return itemURLs;
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
