package lsfusion.erp.region.by.euroopt;

import com.google.common.base.Throwables;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.ERPLoggers;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.silvertunnel_ng.netlib.api.NetLayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

public class SynchronizeItemsEurooptAction extends EurooptAction {

    public SynchronizeItemsEurooptAction(ScriptingLogicsModule LM) {
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

                String ignoreItemGroups = (String) findProperty("ignoreItemGroups[]").read(context);

                List<String> ignoreItemGroupsList = ignoreItemGroups != null ? Arrays.asList(ignoreItemGroups.split(",\\s?")) : new ArrayList<>();

                JSONArray itemsJSON = getItems(useTor ? getNetLayer() : null, mainPage, itemGroupPattern, ignoreItemGroupsList, itemPattern);
                findProperty("synchronizeItemsFile[]").change(new RawFileData(itemsJSON.toString().getBytes(StandardCharsets.UTF_8)), context);

                context.delayUserInteraction(new MessageClientAction("Cинхронизация успешно завершёна", "Синхронизация товаров Евроопт"));
            } else {
                context.delayUserInteraction(new MessageClientAction("Не выбрана главная страница", "Ошибка"));
            }
        } catch (IOException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }

    }

    private JSONArray getItems(NetLayer lowerNetLayer, String mainPage, String itemGroupPattern, List<String> ignoreItemGroups, String itemPattern) throws IOException {
        JSONArray itemsJSON = new JSONArray();
        int count = 0;
        Set<String> itemGroups = getItemGroupURLSet(lowerNetLayer, mainPage, itemGroupPattern, ignoreItemGroups);
        for (String itemGroupURL : itemGroups) {
            count++;
            int page = 1;
            String prevPageHash = null;
            boolean notLastPage = true;
            while (notLastPage) {
                Map<String, JSONObject> pageItemsMap = new LinkedHashMap<>();
                int step = 1;
                String prevStepHash = null;
                boolean notLastStep = true;
                String pageHash = "";
                while (notLastStep) {
                    Set<String> stepItemsSet = new LinkedHashSet<>();
                    String stepHash = "";
                    String stepUrl = itemGroupURL + "?page=" + page + "&lazy_steep=" + step;
                    ERPLoggers.importLogger.info(String.format(logPrefix + "reading itemGroup url %s (%s of %s)", stepUrl, count, itemGroups.size()));
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
                                    JSONObject itemJSON = new JSONObject();
                                    itemJSON.put("itemGroupURL", itemGroupURL);
                                    itemJSON.put("itemGroupTitle", itemGroupTitle);
                                    itemJSON.put("stepUrl", stepUrl);
                                    itemJSON.put("href", href);
                                    pageItemsMap.put(href, itemJSON);
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
                if(notLastPage) {
                    for(JSONObject itemJSON : pageItemsMap.values()) {
                        itemsJSON.put(itemJSON);
                    }
                }
            }
        }
        return itemsJSON;
    }

    private Set<String> getItemGroupURLSet(NetLayer lowerNetLayer, String mainPage, String itemGroupPattern, List<String> ignoreItemGroups) throws IOException {
        Set<String> itemGroupsSet = new HashSet<>();
        String mainUrl = lowerNetLayer == null ? mainPage + "/" : "/catalog/";
        ERPLoggers.importLogger.info(String.format(logPrefix + "reading url %s", mainUrl));
        Document doc = getDocument(lowerNetLayer, mainPage, mainUrl);
        if (doc != null) {
            for (Element topGroup : doc.getElementsByClass("catalog_menu__subsubmenu")) {
                for(Element middleGroup : topGroup.getElementsByTag("li")) {
                    for(Element bottomGroup : middleGroup.getElementsByTag("li")) {
                        if(!bottomGroup.equals(middleGroup)) {
                            for (Element url : bottomGroup.getElementsByTag("a")) {
                                String href = url.attr("href");
                                if (href != null && href.matches(itemGroupPattern)) {
                                    if (lowerNetLayer != null) href = href.replace(mainPage, "");
                                    String idGroup = href.substring(href.length() - 9, href.length() - 5); //ссылка заканчивается на d{4}.html
                                    if (!itemGroupsSet.contains(href) && !ignoreItemGroups.contains(idGroup)) {
                                        ERPLoggers.importLogger.info(String.format(logPrefix + "preparing itemGroup url #%s: %s", itemGroupsSet.size() + 1, href));
                                        itemGroupsSet.add(href);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return itemGroupsSet;
    }

}
