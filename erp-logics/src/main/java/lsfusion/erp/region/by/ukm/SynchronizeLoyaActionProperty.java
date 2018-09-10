package lsfusion.erp.region.by.ukm;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

import static org.apache.commons.lang.StringUtils.trim;
import static org.apache.commons.lang.StringUtils.trimToEmpty;

public class SynchronizeLoyaActionProperty extends LoyaActionProperty {
    String failCaption = "Loya: Ошибка при синхронизации";

    public SynchronizeLoyaActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            boolean disableSynchronizeItems = findProperty("disableSynchronizeItemsLoya").read(context) != null;
            boolean deleteInactiveItemGroups = findProperty("deleteInactiveItemGroupsLoya").read(context) != null;
            List<Object> data = readItems(context, deleteInactiveItemGroups);
            List<List<Object>> itemsList = (List<List<Object>>) data.get(0);
            Map<DataObject, List<Object>> itemGroupsMap = (Map<DataObject, List<Object>>) data.get(1);
            Map<Long, List<GoodGroupLink>> itemItemGroupsMap = (Map<Long, List<GoodGroupLink>>) data.get(2);
            List<Long> deleteItemGroupsList = (List<Long>) data.get(3);
            List<Category> categoriesList = readCategories(context);

            SettingsLoya settings = login(context);
            if (settings.error == null) {
                if ((disableSynchronizeItems || uploadCategories(context, settings, categoriesList)) &&
                        (disableSynchronizeItems || uploadItems(context, settings, itemsList)) &&
                        uploadItemGroups(context, settings, itemItemGroupsMap, itemGroupsMap, deleteItemGroupsList) &&
                        uploadItemItemGroups(context, settings, itemItemGroupsMap))
                    context.delayUserInteraction(new MessageClientAction("Синхронизация успешно завершена", "Loya"));
            } else
                context.delayUserInteraction(new MessageClientAction(settings.error, failCaption));
        } catch (Exception e) {
            ServerLoggers.importLogger.error(failCaption, e);
            context.delayUserInteraction(new MessageClientAction(e.getMessage(), failCaption));
        }
    }

    private List<Category> readCategories(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<Category> result = new ArrayList<>();
        Map<Long, Category> itemGroupMap = new HashMap<>();

        KeyExpr itemGroupExpr = new KeyExpr("ItemGroup");
        ImRevMap<Object, KeyExpr> itemGroupKeys = MapFact.singletonRev((Object) "itemGroup", itemGroupExpr);
        QueryBuilder<Object, Object> itemGroupQuery = new QueryBuilder<>(itemGroupKeys);

        String[] itemGroupNames = new String[]{"overIdItemGroup", "nameItemGroup", "idParentItemGroup"};
        LCP[] itemGroupProperties = findProperties("overId[ItemGroup]", "name[ItemGroup]", "idParent[ItemGroup]");
        for (int i = 0; i < itemGroupProperties.length; i++) {
            itemGroupQuery.addProperty(itemGroupNames[i], itemGroupProperties[i].getExpr(itemGroupExpr));
        }
        itemGroupQuery.and(findProperty("in[SkuGroup]").getExpr(itemGroupExpr).getWhere());

        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> itemGroupResult = itemGroupQuery.execute(context);

        for (ImMap<Object, Object> row : itemGroupResult.valueIt()) {
            Long overId = parseGroup((String) row.get("overIdItemGroup"));
            String name = (String) row.get("nameItemGroup");
            Long idParent = parseGroup((String) row.get("idParentItemGroup"));
            itemGroupMap.put(overId, new Category(overId, name == null ? "" : name, overId == 0 ? null : idParent));
        }

        Map<Long, List<Category>> resultMap = new HashMap<>();
        for (Map.Entry<Long, Category> entry : itemGroupMap.entrySet()) {
            ArrayList<Category> hierarchy = new ArrayList<>(Collections.singletonList(entry.getValue()));
            Category parent = itemGroupMap.get(entry.getValue().parentId);
            while (parent != null && itemGroupMap.containsKey(parent.overId) && !hierarchy.contains(parent)) {
                hierarchy.add(parent);
                parent = itemGroupMap.get(parent.parentId);
            }
            resultMap.put(entry.getKey(), hierarchy);
        }

        Set<Long> ids = new HashSet<>();
        for (List<Category> entry : resultMap.values()) {
            for (int i = entry.size() - 1; i >= 0; i--) {
                Long id = entry.get(i).overId;
                if (!ids.contains(id)) {
                    result.add(entry.get(i));
                    ids.add(id);
                }
            }
        }

        return result;
    }

    private List<Object> readItems(ExecutionContext<ClassPropertyInterface> context, boolean deleteInactiveItemGroups) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<List<Object>> itemsList = new ArrayList<>();
        Map<DataObject, List<Object>> itemGroupsMap = new HashMap<>();
        Map<Long, List<GoodGroupLink>> itemItemGroupsMap = new HashMap<>();
        List<Long> deleteItemGroupsList = new ArrayList<>();

        KeyExpr groupExpr = new KeyExpr("loyaItemGroup");
        KeyExpr skuExpr = new KeyExpr("sku");

        ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "loyaItemGroup", groupExpr, "sku", skuExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("idLoyaItemGroup", findProperty("id[LoyaItemGroup]").getExpr(groupExpr));
        query.addProperty("nameLoyaItemGroup", findProperty("name[LoyaItemGroup]").getExpr(groupExpr));
        query.addProperty("quantity", findProperty("quantity[Item, LoyaItemGroup]").getExpr(skuExpr, groupExpr));
        query.addProperty("descriptionLoyaItemGroup", findProperty("description[LoyaItemGroup]").getExpr(groupExpr));
        query.addProperty("idSku", findProperty("id[Sku]").getExpr(skuExpr));
        query.addProperty("captionItem", findProperty("nameAttribute[Item]").getExpr(skuExpr));
        query.addProperty("idUOMItem", findProperty("idUOM[Item]").getExpr(skuExpr));
        query.addProperty("splitItem", findProperty("split[Item]").getExpr(skuExpr));
        query.addProperty("idSkuGroup", findProperty("overIdSkuGroup[Item]").getExpr(skuExpr));
        query.and(findProperty("active[LoyaItemGroup]").getExpr(groupExpr).getWhere());
        query.and(findProperty("id[Sku]").getExpr(skuExpr).getWhere());
        query.and(findProperty("in[Item,LoyaItemGroup]").getExpr(skuExpr, groupExpr).getWhere());

        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(context);
        for (int i = 0; i < result.size(); i++) {
            DataObject groupObject = result.getKey(i).get("loyaItemGroup");
            ImMap<Object, ObjectValue> valueEntry = result.getValue(i);
            Long idLoyaItemGroup = (Long) valueEntry.get("idLoyaItemGroup").getValue();
            String nameItemGroup = trim((String) valueEntry.get("nameLoyaItemGroup").getValue());
            BigDecimal quantity = (BigDecimal) valueEntry.get("quantity").getValue();
            String descriptionItemGroup = trim((String) valueEntry.get("descriptionLoyaItemGroup").getValue());
            String idSku = trim((String) valueEntry.get("idSku").getValue());
            String captionItem = trimToEmpty((String) valueEntry.get("captionItem").getValue());
            String idUOMItem = trim((String) valueEntry.get("idUOMItem").getValue());
            Boolean splitItem = valueEntry.get("splitItem").getValue() != null;
            String idSkuGroup = trim((String) valueEntry.get("idSkuGroup").getValue());
            itemsList.add(Arrays.asList((Object) idSku, idLoyaItemGroup, captionItem, idUOMItem, splitItem, idSkuGroup));
            itemGroupsMap.put(groupObject, Arrays.asList(idLoyaItemGroup == null ? groupObject.getValue() : idLoyaItemGroup, nameItemGroup, descriptionItemGroup));
            List<GoodGroupLink> skuList = itemItemGroupsMap.get(idLoyaItemGroup);
            if (skuList == null)
                skuList = new ArrayList<>();
            skuList.add(new GoodGroupLink(idSku, quantity));
            itemItemGroupsMap.put(idLoyaItemGroup, skuList);
        }

        //get loya groups without items and not active for deletion
        KeyExpr emptyGroupExpr = new KeyExpr("loyaItemGroup");
        QueryBuilder<Object, Object> emptyQuery = new QueryBuilder<>(MapFact.singletonRev((Object) "loyaItemGroup", emptyGroupExpr));
        emptyQuery.addProperty("idLoyaItemGroup", findProperty("id[LoyaItemGroup]").getExpr(emptyGroupExpr));
        emptyQuery.addProperty("nameLoyaItemGroup", findProperty("name[LoyaItemGroup]").getExpr(emptyGroupExpr));
        emptyQuery.addProperty("descriptionLoyaItemGroup", findProperty("description[LoyaItemGroup]").getExpr(emptyGroupExpr));
        emptyQuery.addProperty("empty", findProperty("empty[LoyaItemGroup]").getExpr(emptyGroupExpr));
        emptyQuery.addProperty("active", findProperty("active[LoyaItemGroup]").getExpr(emptyGroupExpr));
        emptyQuery.and(findProperty("empty[LoyaItemGroup]").getExpr(emptyGroupExpr).getWhere().or(findProperty("active[LoyaItemGroup]").getExpr(emptyGroupExpr).getWhere().not()));
        emptyQuery.and(findProperty("name[LoyaItemGroup]").getExpr(emptyGroupExpr).getWhere());//emptyQuery.and(findProperty("empty[LoyaItemGroup]").getExpr(emptyGroupExpr).getWhere().or(findProperty("active[LoyaItemGroup]").getExpr(emptyGroupExpr).getWhere().not()));

        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> emptyResult = emptyQuery.executeClasses(context);
        for (int i = 0; i < emptyResult.size(); i++) {
            DataObject groupObject = emptyResult.getKey(i).get("loyaItemGroup");
            ImMap<Object, ObjectValue> valueEntry = emptyResult.getValue(i);
            Long idLoyaItemGroup = (Long) valueEntry.get("idLoyaItemGroup").getValue();
            String nameItemGroup = trim((String) valueEntry.get("nameLoyaItemGroup").getValue());
            String descriptionItemGroup = trim((String) valueEntry.get("descriptionLoyaItemGroup").getValue());
            boolean empty = valueEntry.get("empty").getValue() != null;
            boolean active = valueEntry.get("active").getValue() != null;
            if(active && empty)
                itemGroupsMap.put(groupObject, Arrays.asList(idLoyaItemGroup == null ? groupObject.getValue() : idLoyaItemGroup, nameItemGroup, descriptionItemGroup));
            if(!active && idLoyaItemGroup != null && deleteInactiveItemGroups)
                deleteItemGroupsList.add(idLoyaItemGroup);
        }
        return Arrays.asList(itemsList, itemGroupsMap, itemItemGroupsMap, deleteItemGroupsList);
    }

    private boolean uploadItemGroups(ExecutionContext context, SettingsLoya settings, Map<Long, List<GoodGroupLink>> itemItemGroupsMap, Map<DataObject, List<Object>> itemGroupsMap,
                                     List<Long> deleteItemGroupsList) throws JSONException, IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        boolean succeeded = true;
        for (Map.Entry<DataObject, List<Object>> entry : itemGroupsMap.entrySet()) {
            DataObject itemGroupObject = entry.getKey();
            List<Object> itemGroupData = entry.getValue();
            if (!uploadItemGroup(context, settings, itemItemGroupsMap, itemGroupData, itemGroupObject))
                succeeded = false;
        }
        for(Long idItemGroup : deleteItemGroupsList) {
            if (existsItemGroup(settings, idItemGroup)) {
                ServerLoggers.importLogger.info("Loya: deleting goodGroup " + idItemGroup);
                if (!deleteItemGroup(context, settings, idItemGroup))
                    succeeded = false;
            }
        }
        return succeeded;
    }

    private boolean uploadItemGroup(ExecutionContext context, SettingsLoya settings, Map<Long, List<GoodGroupLink>> itemItemGroupsMap, List<Object> itemGroupData, DataObject itemGroupObject)
            throws JSONException, IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        Long id = (Long) itemGroupData.get(0);
        String name = (String) itemGroupData.get(1);
        String description = (String) itemGroupData.get(2);

        ServerLoggers.importLogger.info("Loya: synchronizing goodGroup " + id + " started");
        JSONObject requestBody = new JSONObject();
        requestBody.put("partnerId", settings.partnerId);
        requestBody.put("id", id);
        requestBody.put("name", name == null ? "" : name);
        requestBody.put("description", description == null ? "" : description);
        requestBody.put("limits", getLimits());

        if (existsItemGroup(settings, id)) {
            ServerLoggers.importLogger.info("Loya: modifying goodGroup " + id);
            return modifyItemGroup(context, settings, id, itemGroupObject, requestBody);
        } else {
            ServerLoggers.importLogger.info("Loya: creating goodGroup " + id);
            Long idItemGroup = createItemGroup(context, itemGroupObject, settings.url, settings.sessionKey, requestBody);
            if (idItemGroup != null) {//id группы изменился
                List<GoodGroupLink> skuList = itemItemGroupsMap.get(id);
                if(skuList == null)
                    skuList = new ArrayList<>();
                itemItemGroupsMap.put(idItemGroup, skuList);
                itemItemGroupsMap.remove(id);
            }
            return idItemGroup != null;
        }
    }

    private boolean existsItemGroup(SettingsLoya settings, Long idItemGroup) throws IOException, JSONException {
        HttpGet getRequest = new HttpGet(settings.url + "goodgroup/" + settings.partnerId + "/" + idItemGroup);
        return requestSucceeded(executeRequest(getRequest, settings.sessionKey));
    }

    private boolean modifyItemGroup(ExecutionContext context, SettingsLoya settings, Long idItemGroup, DataObject itemGroupObject, JSONObject requestBody)
            throws IOException, JSONException, SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        HttpPost postRequest = new HttpPost(settings.url + "goodgroup/" + settings.partnerId + "/" + idItemGroup);
        HttpResponse response = executeRequest(postRequest, requestBody, settings.sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (succeeded)
            setIdLoyaItemGroup(context, itemGroupObject, new JSONObject(getResponseMessage(response)).getLong("id"));
        else
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Modify ItemGroup Error"));
        return succeeded;
    }

    private Long createItemGroup(ExecutionContext context, DataObject itemGroupObject, String url, String sessionKey, JSONObject requestBody)
            throws IOException, JSONException, SQLException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
        HttpPut putRequest = new HttpPut(url + "goodgroup");
        HttpResponse response = executeRequest(putRequest, requestBody, sessionKey);
        boolean succeeded = requestSucceeded(response);
        String responseMessage = getResponseMessage(response);
        if (succeeded)
            setIdLoyaItemGroup(context, itemGroupObject, new JSONObject(responseMessage).getLong("id"));
        else
            context.delayUserInteraction(new MessageClientAction(responseMessage, "Loya: Create ItemGroup Error"));
        return succeeded ? new JSONObject(responseMessage).getLong("id") : null;
    }

    private boolean deleteItemGroup(ExecutionContext context, SettingsLoya settings, Long idItemGroup)
            throws IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException, JSONException {
        HttpDeleteWithBody deleteRequest = new HttpDeleteWithBody(settings.url + "goodgroup/" + settings.partnerId + "/" + idItemGroup);
        deleteRequest.setEntity(new StringEntity("[" + idItemGroup + "]"));
        HttpResponse response = executeRequest(deleteRequest, settings.sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (!succeeded)
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Delete ItemGroup Error"));
        return succeeded;
    }

    private boolean uploadCategories(ExecutionContext context, SettingsLoya settings, List<Category> categoriesList) throws IOException, JSONException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        boolean succeeded = true;
        for (Category category : categoriesList) {
            if (!uploadCategory(context, settings, category))
                succeeded = false;
        }
        return succeeded;
    }

    private boolean uploadCategory(ExecutionContext context, SettingsLoya settings, Category category)
            throws JSONException, IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {

        ServerLoggers.importLogger.info("Loya: synchronizing category " + category.overId + " started");
        JSONObject requestBody = new JSONObject();
        requestBody.put("partnerId", settings.partnerId);
        requestBody.put("categoryId", category.overId);
        requestBody.put("parentId", category.parentId);
        requestBody.put("name", category.name);
        requestBody.put("limits", getLimits());
        requestBody.put("state", "active");

        if (existsCategory(settings, category.overId)) {
            ServerLoggers.importLogger.info("Loya: modifying category " + category.overId);
            return modifyCategory(context, settings, category.overId, requestBody);
        } else {
            ServerLoggers.importLogger.info("Loya: creating category " + category.overId);
            return createCategory(context, settings.url, settings.sessionKey, requestBody);
        }
    }

    private boolean existsCategory(SettingsLoya settings, Long idCategory) throws IOException {
        HttpGet getRequest = new HttpGet(settings.url + "category/" + settings.partnerId + "/" + idCategory);
        return requestSucceeded(executeRequest(getRequest, settings.sessionKey));
    }

    private boolean modifyCategory(ExecutionContext context, SettingsLoya settings, Long categoryId, JSONObject requestBody) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        HttpPost postRequest = new HttpPost(settings.url + "category/" + settings.partnerId + "/" + categoryId);
        HttpResponse response = executeRequest(postRequest, requestBody, settings.sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (!succeeded)
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Modify Category Error"));
        return succeeded;
    }

    private boolean createCategory(ExecutionContext context, String url, String sessionKey, JSONObject requestBody)
            throws IOException, JSONException, SQLException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
        HttpPut putRequest = new HttpPut(url + "category");
        HttpResponse response = executeRequest(putRequest, requestBody, sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (!succeeded)
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Create Category Error"));
        return succeeded;
    }

    private boolean uploadItems(ExecutionContext context, SettingsLoya settings, List<List<Object>> itemsList) throws IOException, JSONException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        boolean succeeded = true;
        for (List<Object> entry : itemsList) {
            if (!uploadItem(context, settings, entry))
                succeeded = false;
        }
        return succeeded;
    }

    private boolean uploadItem(ExecutionContext context, SettingsLoya settings, List<Object> itemData)
            throws JSONException, IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {

        String idItem = (String) itemData.get(0);
        String captionItem = (String) itemData.get(2);
        String idUOMItem = (String) itemData.get(3);
        boolean isWeight = (Boolean) itemData.get(4);
        Long categoryId = parseGroup((String) itemData.get(5));

        ServerLoggers.importLogger.info("Loya: synchronizing good " + idItem + " started");
        JSONObject requestBody = new JSONObject();
        requestBody.put("partnerId", settings.partnerId);
        requestBody.put("sku", idItem);
        requestBody.put("categoryId", categoryId);
        requestBody.put("name", captionItem);
        requestBody.put("measurement", idUOMItem);
        requestBody.put("margin", 0);
        requestBody.put("dimension", isWeight ? "weight" : "piece");
        requestBody.put("limits", getLimits());

        if (existsItem(settings, idItem)) {
            ServerLoggers.importLogger.info("Loya: modifying good " + idItem);
            return modifyItem(context, settings, idItem, requestBody);
        } else {
            ServerLoggers.importLogger.info("Loya: creating good " + idItem);
            return createItem(context, settings.url, settings.sessionKey, requestBody);
        }
    }

    //as in ukm4mysqlhandler
    protected Long parseGroup(String idItemGroup) {
        try {
            return idItemGroup == null ? 0 : Long.parseLong(idItemGroup.equals("Все") ? "0" : idItemGroup.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return (long) 0;
        }
    }

    private boolean existsItem(SettingsLoya settings, String idItem) throws IOException {
        HttpGet getRequest = new HttpGet(settings.url + "good/" + settings.partnerId + "/" + idItem);
        return requestSucceeded(executeRequest(getRequest, settings.sessionKey));
    }

    private boolean modifyItem(ExecutionContext context, SettingsLoya settings, String idItem, JSONObject requestBody) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        HttpPost postRequest = new HttpPost(settings.url + "good/" + settings.partnerId + "/" + idItem);
        HttpResponse response = executeRequest(postRequest, requestBody, settings.sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (!succeeded)
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Modify Item Error"));
        return succeeded;
    }

    private boolean createItem(ExecutionContext context, String url, String sessionKey, JSONObject requestBody)
            throws IOException, JSONException, SQLException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
        HttpPut putRequest = new HttpPut(url + "good");
        HttpResponse response = executeRequest(putRequest, requestBody, sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (!succeeded)
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Create Item Error"));
        return succeeded;
    }

    private boolean uploadItemItemGroups(ExecutionContext context, SettingsLoya settings, Map<Long, List<GoodGroupLink>> itemItemGroupsMap) throws JSONException, IOException {
        boolean succeeded = true;
        ServerLoggers.importLogger.info("Loya: synchronizing goodGroupLinks");
        for (Map.Entry<Long, List<GoodGroupLink>> entry : itemItemGroupsMap.entrySet()) {
            Long idItemGroup = entry.getKey();
            List<GoodGroupLink> itemsList = entry.getValue();

            String deleteList = "";
            HttpResponse getResponse = executeRequest(new HttpGet(settings.url + "goodgrouplink/" + settings.partnerId + "/" + idItemGroup), settings.sessionKey);
            String responseMessage = getResponseMessage(getResponse);
            try {
                JSONArray itemsArray = new JSONArray(responseMessage);
                ServerLoggers.importLogger.info(String.format("Loya: synchronizing goodGroupLinks. Group %s: %s items before synchronization", idItemGroup, itemsArray.length()));
                for (int i = 0; i < itemsArray.length(); i++) {
                    JSONObject item = itemsArray.getJSONObject(i);
                    String sku = item.getString("sku");
                    if (itemsList.contains(sku)) {
                        itemsList.remove(sku);
                    } else {
                        deleteList += (deleteList.isEmpty() ? "" : ",") + "\"" + sku + "\"";
                    }
                }
            } catch (Exception e) {
                ServerLoggers.importLogger.error(String.format("Loya: synchronizing goodGroupLinks incorrect response %s, isSucceeded: %s", responseMessage, requestSucceeded(getResponse)));
            }
            if (!deleteList.isEmpty()) {
                //удаляем более не существующие
                HttpPost postRequest = new HttpPost(settings.url + "goodgrouplink/" + settings.partnerId + "/" + idItemGroup + "/deleteList");
                HttpResponse response = executeRequest(postRequest, "[" + deleteList + "]", settings.sessionKey);
                if (!requestSucceeded(response)) {
                    String error = String.format("Loya: delete GoodGroupLinks (%s) error", deleteList);
                    ServerLoggers.importLogger.error(error + ": " + getResponseMessage(response));
                    context.delayUserInteraction(new MessageClientAction(error, failCaption));
                    succeeded = false;
                }
            }

            //добавление списком
            /*String addList = "";
            for (String item : itemsList) {
                addList += (addList.isEmpty() ? "" : ",") + "\"" + item + "\"";
            }
            if (!addList.isEmpty()) {
                //добавляем новые
                if (!createGoodGroupLink(context, settings, idItemGroup, addList))
                    succeeded = false;
            }*/

            //добавление по одному
            if (!itemsList.isEmpty()) {
                for(GoodGroupLink goodGroupLink : itemsList) {
                    if (!createGoodGroupLink(context, settings, idItemGroup, goodGroupLink))
                        succeeded = false;
                }
            }
            ServerLoggers.importLogger.info(String.format("Loya: synchronizing goodGroupLinks. Group %s: deleted %s items, added %s items", idItemGroup, deleteList.length(), itemsList.size()));
        }
        return succeeded;
    }

    private boolean createGoodGroupLink(ExecutionContext context, SettingsLoya settings, Long idItemGroup, GoodGroupLink goodGroupLink) throws IOException {
        HttpPost postRequest = new HttpPost(settings.url + "goodgrouplink/" + settings.partnerId + "/" + idItemGroup + "/upload");
        String query = goodGroupLink.quantity == null ? String.format("[{\"sku\":\"%s\"}]", goodGroupLink.sku) :
                String.format("[{\"sku\":\"%s\",\"quantity\":%s}]", goodGroupLink.sku, goodGroupLink.quantity);
        HttpResponse response = executeRequest(postRequest, query, settings.sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (!succeeded) {
            String result = getResponseMessage(response);
            String error = String.format("Loya: create GoodGroupLink Error: group %s, item %s", idItemGroup, goodGroupLink.sku);
            ServerLoggers.importLogger.error(error + ", " + result);
            context.delayUserInteraction(new MessageClientAction(error, "Loya: create GoodGroupLink Error"));
        }
        return succeeded;
    }

    private Map<String, Integer> getLimits() {
        Map<String, Integer> limitsMap = new HashMap<>();
        limitsMap.put("maxDiscount", null);
        limitsMap.put("maxAllowBonus", null);
        limitsMap.put("maxAwardBonus", null);
        return limitsMap;
    }

    private class Category {
        Long overId;
        String name;
        Long parentId;

        public Category(Long overId, String name, Long parentId) {
            this.overId = overId;
            this.name = name;
            this.parentId = parentId;
        }
    }

    private class GoodGroupLink {
        String sku;
        BigDecimal quantity;

        public GoodGroupLink(String sku, BigDecimal quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }
    }
}
