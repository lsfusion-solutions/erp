package lsfusion.erp.region.by.euroopt;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.logics.classes.user.CustomClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.physics.dev.integration.service.*;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.silvertunnel_ng.netlib.api.NetLayer;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

public class SynchronizeItemsEurooptActionProperty extends EurooptActionProperty {

    public SynchronizeItemsEurooptActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            ObjectValue mainPageObject = findProperty("mainPage[]").readClasses(context);
            if(mainPageObject instanceof DataObject) {
                String mainPage = (String) findProperty("captionMainPage[]").read(context);
                String itemGroupPattern = Pattern.quote(mainPage) + "\\/catalog\\/\\d{4}\\.html";
                String itemPattern = Pattern.quote(mainPage) + "\\/catalog\\/item_\\d+\\.html";
                boolean useTor = findProperty("ImportEuroopt.useTor[]").read(context) != null;

                List<List<Object>> data = getItems(useTor ? getNetLayer() : null, mainPage, itemGroupPattern, itemPattern);
                synchronizeItems(context, data, (DataObject) mainPageObject);

                context.delayUserInteraction(new MessageClientAction("Cинхронизация успешно завершёна", "Синхронизация товаров Евроопт"));
            } else {
                context.delayUserInteraction(new MessageClientAction("Не выбрана главная страница", "Ошибка"));
            }
        } catch (IOException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }

    }

    private void synchronizeItems(ExecutionContext context, List<List<Object>> data, DataObject mainPageObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<ImportProperty<?>> props = new ArrayList<>();
        List<ImportField> fields = new ArrayList<>();
        List<ImportKey<?>> keys = new ArrayList<>();

        ImportField urlEurooptItemGroupField = new ImportField(findProperty("url[EurooptItemGroup]"));
        ImportKey<?> eurooptItemGroupKey = new ImportKey((CustomClass) findClass("EurooptItemGroup"),
                findProperty("eurooptItemGroup[STRING[255]]").getMapping(urlEurooptItemGroupField));
        keys.add(eurooptItemGroupKey);
        props.add(new ImportProperty(urlEurooptItemGroupField, findProperty("url[EurooptItemGroup]").getMapping(eurooptItemGroupKey)));
        fields.add(urlEurooptItemGroupField);

        props.add(new ImportProperty(mainPageObject, findProperty("mainPage[EurooptItemGroup]").getMapping(eurooptItemGroupKey)));

        ImportField titleEurooptItemGroupField = new ImportField(findProperty("title[EurooptItemGroup]"));
        props.add(new ImportProperty(titleEurooptItemGroupField, findProperty("title[EurooptItemGroup]").getMapping(eurooptItemGroupKey)));
        fields.add(titleEurooptItemGroupField);

        ImportField urlEurooptItemListField = new ImportField(findProperty("url[EurooptItemList]"));
        ImportKey<?> eurooptItemListKey = new ImportKey((CustomClass) findClass("EurooptItemList"),
                findProperty("eurooptItemList[STRING[255]]").getMapping(urlEurooptItemListField));
        keys.add(eurooptItemListKey);
        props.add(new ImportProperty(urlEurooptItemGroupField, findProperty("eurooptItemGroup[EurooptItemList]").getMapping(eurooptItemListKey),
                object(findClass("EurooptItemGroup")).getMapping(eurooptItemGroupKey)));
        props.add(new ImportProperty(urlEurooptItemListField, findProperty("url[EurooptItemList]").getMapping(eurooptItemListKey)));
        fields.add(urlEurooptItemListField);

        ImportField urlEurooptItemField = new ImportField(findProperty("url[EurooptItem]"));
        ImportKey<?> eurooptItemKey = new ImportKey((CustomClass) findClass("EurooptItem"),
                findProperty("eurooptItem[STRING[255]]").getMapping(urlEurooptItemField));
        keys.add(eurooptItemKey);
        props.add(new ImportProperty(urlEurooptItemListField, findProperty("eurooptItemList[EurooptItem]").getMapping(eurooptItemKey),
                object(findClass("EurooptItemList")).getMapping(eurooptItemListKey)));
        props.add(new ImportProperty(urlEurooptItemField, findProperty("url[EurooptItem]").getMapping(eurooptItemKey)));
        fields.add(urlEurooptItemField);

        List<ImportDelete> deletes = new ArrayList<>();
        deletes.add(new ImportDelete(eurooptItemGroupKey, findProperty("delete[EurooptItemGroup, MainPage]").getMapping(eurooptItemGroupKey, mainPageObject), false));
        deletes.add(new ImportDelete(eurooptItemListKey, findProperty("delete[EurooptItemList, MainPage]").getMapping(eurooptItemListKey, mainPageObject), false));
        deletes.add(new ImportDelete(eurooptItemKey, findProperty("delete[EurooptItem, MainPage]").getMapping(eurooptItemKey, mainPageObject), false));

        ImportTable table = new ImportTable(fields, data);

        try (ExecutionContext.NewSession newContext = context.newSession()) {
            IntegrationService service = new IntegrationService(newContext, table, keys, props, deletes);
            service.synchronize(true, false);
            newContext.apply();
        }
    }

    private List<List<Object>> getItems(NetLayer lowerNetLayer, String mainPage, String itemGroupPattern, String itemPattern) throws IOException {
        List<List<Object>> itemsList = new ArrayList<>();
        int count = 0;
        Set<String> itemGroups = getItemGroupURLSet(lowerNetLayer, mainPage, itemGroupPattern);
        for (String itemGroupURL : itemGroups) {
            count++;
            int page = 1;
            String prevPageHash = null;
            boolean notLastPage = true;
            while (notLastPage) {
                Map<String, List<Object>> pageItemsMap = new LinkedHashMap<>();
                int step = 1;
                String prevStepHash = null;
                boolean notLastStep = true;
                String pageHash = "";
                while (notLastStep) {
                    Set<String> stepItemsSet = new LinkedHashSet<>();
                    String stepHash = "";
                    String stepUrl = itemGroupURL + "?page=" + page + "&lazy_steep=" + step;
                    ServerLoggers.importLogger.info(String.format(logPrefix + "reading itemGroup url %s (%s of %s)", stepUrl, count, itemGroups.size()));
                    Document doc = getDocument(lowerNetLayer, mainPage, stepUrl);
                    if (doc != null) {
                        String itemGroupTitle = doc.getElementsByTag("title").text().replace(" - Каталог товаров", "");
                        for (Element item : doc.getElementsByTag("a")) {
                            String href = item.attr("href");
                            if (href != null && href.matches(itemPattern)) {
                                if (lowerNetLayer != null)
                                    href = href.replace(mainPage, "");
                                if (!stepItemsSet.contains(href)) {
                                    stepItemsSet.add(href);
                                    stepHash += href;
                                    pageItemsMap.put(href, Arrays.<Object>asList(itemGroupURL, itemGroupTitle, stepUrl, href));
                                }
                            }
                        }
                        pageHash += stepHash;
                    }
                    notLastStep = !stepItemsSet.isEmpty() && !stepHash.equals(prevStepHash);
                    prevStepHash = stepHash;
                    step++;
                }
                page++;
                notLastPage = !pageHash.equals(prevPageHash);
                prevPageHash = pageHash;
                if(notLastPage)
                    itemsList.addAll(pageItemsMap.values());
            }
        }
        return itemsList;
    }

    private Set<String> getItemGroupURLSet(NetLayer lowerNetLayer, String mainPage, String itemGroupPattern) throws IOException {
        Set<String> itemGroupsSet = new HashSet<>();
        String mainUrl = lowerNetLayer == null ? mainPage + "/" : "/catalog/";
        ServerLoggers.importLogger.info(String.format(logPrefix + "reading url %s", mainUrl));
        Document doc = getDocument(lowerNetLayer, mainPage, mainUrl);
        if (doc != null) {
            for (Element url : doc.getElementsByTag("a")) {
                String href = url.attr("href");
                if (href != null && href.matches(itemGroupPattern)) {
                    if (lowerNetLayer != null)
                        href = href.replace(mainPage, "");
                    if (!itemGroupsSet.contains(href)) {
                        ServerLoggers.importLogger.info(String.format(logPrefix + "preparing itemGroup url #%s: %s", itemGroupsSet.size() + 1, href));
                        itemGroupsSet.add(href);
                    }
                }
            }
        }
        return itemGroupsSet;
    }

}
