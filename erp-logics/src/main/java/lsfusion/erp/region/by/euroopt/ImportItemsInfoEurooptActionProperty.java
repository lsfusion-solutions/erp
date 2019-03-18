package lsfusion.erp.region.by.euroopt;

import com.google.common.base.Throwables;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.logics.classes.user.CustomClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.builder.QueryBuilder;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.physics.dev.integration.service.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.silvertunnel_ng.netlib.api.NetLayer;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImportItemsInfoEurooptActionProperty extends EurooptActionProperty {

    public ImportItemsInfoEurooptActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            String mainPage = (String) findProperty("captionMainPage[]").read(context);
            if(mainPage != null) {
                boolean useTor = findProperty("ImportEuroopt.useTor[]").read(context) != null;
                boolean onlyBarcode = findProperty("onlyBarcode[]").read(context) != null;

                List<List<Object>> data = getItemsInfo(context, mainPage, useTor, onlyBarcode);
                if(!data.isEmpty()) {
                    importItems(context, data, onlyBarcode);
                    context.delayUserInteraction(new MessageClientAction("Импорт успешно завершён.\nКоличество обновлённых товаров: " + data.size(), "Импорт товаров Евроопт"));
                }
            } else {
                context.delayUserInteraction(new MessageClientAction("Не выбрана главная страница", "Ошибка"));
            }

        } catch (IOException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }

    }

    private void importItems(ExecutionContext context, List<List<Object>> data, boolean onlyBarcode) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<ImportProperty<?>> props = new ArrayList<>();
        List<ImportField> fields = new ArrayList<>();
        List<ImportKey<?>> keys = new ArrayList<>();

        ImportField urlEurooptItemField = new ImportField(findProperty("url[EurooptItem]"));
        ImportKey<?> eurooptItemKey = new ImportKey((CustomClass) findClass("EurooptItem"),
                findProperty("eurooptItem[STRING[255]]").getMapping(urlEurooptItemField));
        eurooptItemKey.skipKey = true;
        keys.add(eurooptItemKey);
        fields.add(urlEurooptItemField);

        ImportField idBarcodeEurooptItemField = new ImportField(findProperty("idBarcode[EurooptItem]"));
        ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                findProperty("skuBarcode[STRING[15]]").getMapping(idBarcodeEurooptItemField));
        itemKey.skipKey = true;
        keys.add(itemKey);
        props.add(new ImportProperty(idBarcodeEurooptItemField, findProperty("idBarcode[EurooptItem]").getMapping(eurooptItemKey)));
        fields.add(idBarcodeEurooptItemField);

        ImportField noBarcodeEurooptItemField = new ImportField(findProperty("noBarcode[EurooptItem]"));
        props.add(new ImportProperty(noBarcodeEurooptItemField, findProperty("noBarcode[EurooptItem]").getMapping(eurooptItemKey)));
        fields.add(noBarcodeEurooptItemField);

        if(!onlyBarcode) {

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

            ImportField idManufacturerField = new ImportField(findProperty("id[Manufacturer]"));
            ImportKey<?> manufacturerKey = new ImportKey((CustomClass) findClass("Manufacturer"),
                    findProperty("manufacturer[VARSTRING[100]]").getMapping(idManufacturerField));
            manufacturerKey.skipKey = true;
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

        }

        ImportTable table = new ImportTable(fields, data);

        try (ExecutionContext.NewSession newContext = context.newSession()) {
            IntegrationService service = new IntegrationService(newContext, table, keys, props);
            service.synchronize(true, false);
            newContext.apply();
        }
    }

    private List<List<Object>> getItemsInfo(ExecutionContext context, String mainPage, boolean useTor, boolean onlyBarcode) throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, IOException {
        List<List<Object>> itemsList = new ArrayList<>();
        List<String> itemURLs = getItemURLs(context);
        if (!itemURLs.isEmpty()) {
            ServerLoggers.importLogger.info(String.format(logPrefix + "import %s item(s)", itemURLs.size()));
            int noBarcodeCount = 0;
            NetLayer lowerNetLayer = useTor ? getNetLayer() : null;

            int i = 1;
            for (String itemURL : itemURLs) {
                ServerLoggers.importLogger.info(String.format(logPrefix + "parsing item page #%s of %s: %s", i, itemURLs.size(), (useTor ? mainPage : "") + itemURL));
                Document doc = getDocument(lowerNetLayer, mainPage, itemURL);
                if (doc != null) {
                    String title = doc.getElementsByTag("title").text().replace(" - Каталог товаров", "");
                    Elements descriptionElement = doc.getElementsByClass("description");
                    List<Node> descriptionAttributes = descriptionElement.size() == 0 ? new ArrayList<Node>() : descriptionElement.get(0).childNodes();
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

                    if (onlyBarcode) {
                        itemsList.add(Arrays.asList((Object) itemURL, idBarcode, noBarcode));
                    } else {

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

                        itemsList.add(Arrays.asList((Object) itemURL, idBarcode, noBarcode, captionItem, netWeight,
                                descriptionItem, compositionItem, proteinsItem, fatsItem, carbohydratesItem,
                                energyItem, manufacturerItem, UOMItem, brandItem));
                    }
                    ServerLoggers.importLogger.info(String.format(logPrefix + "parsed item page #%s of %s: %s", i, itemURLs.size(), title));

                }
                i++;
            }
            ServerLoggers.importLogger.info(String.format(logPrefix + "read finished. %s items, %s items without barcode skipped", itemsList.size(), noBarcodeCount));
            if (itemsList.isEmpty())
                context.delayUserInteraction(new MessageClientAction("Не найдёно ни одного существующего в базе товара!", "Ошибка"));
        } else {
            context.delayUserInteraction(new MessageClientAction("Не выбрано ни одного товара!", "Ошибка"));
        }
        return itemsList;
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
