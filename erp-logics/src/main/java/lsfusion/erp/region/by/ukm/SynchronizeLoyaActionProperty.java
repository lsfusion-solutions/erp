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
            boolean disableSynchronizeItems = findProperty("disableSynchronizeItemsLoya[]").read(context) != null;
            boolean deleteInactiveItemGroups = findProperty("deleteInactiveItemGroupsLoya[]").read(context) != null;
            Map<String, Integer> discountLimits = getDiscountLimits(context);

            boolean succeeded = true;

            SettingsLoya settings = login(context);
            if (settings.error == null) {

                //synchronize brands
                if(!disableSynchronizeItems) {
                    List<Brand> brandsList = readBrands(context);
                    if(!uploadBrands(context, settings, brandsList)) {
                        succeeded = false;
                    }
                }

                if(succeeded) {

                    SynchronizeData data = readItems(context, deleteInactiveItemGroups);
                    List<Category> categoriesList = readCategories(context);

                    if ((disableSynchronizeItems || uploadCategories(context, settings, categoriesList, discountLimits)) &&
                            (disableSynchronizeItems || uploadItems(context, settings, data.itemsList, discountLimits)) &&
                            uploadItemGroups(context, settings, data.itemItemGroupsMap, data.itemGroupsMap, data.deleteItemGroupsList, discountLimits) &&
                            uploadItemItemGroups(context, settings, data.itemItemGroupsMap))
                        context.delayUserInteraction(new MessageClientAction("Синхронизация успешно завершена", "Loya"));

                }
            } else
                context.delayUserInteraction(new MessageClientAction(settings.error, failCaption));
        } catch (Exception e) {
            ServerLoggers.importLogger.error(failCaption, e);
            context.delayUserInteraction(new MessageClientAction(e.getMessage(), failCaption));
        }
    }

    private List<Brand> readBrands(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<Brand> result = new ArrayList<>();

        KeyExpr brandExpr = new KeyExpr("Brand");
        ImRevMap<Object, KeyExpr> brandKeys = MapFact.singletonRev((Object) "brand", brandExpr);
        QueryBuilder<Object, Object> brandQuery = new QueryBuilder<>(brandKeys);

        String[] brandNames = new String[]{"idLoya", "name"};
        LCP[] brandProperties = findProperties("idLoya[Brand]", "name[Brand]");
        for (int i = 0; i < brandProperties.length; i++) {
            brandQuery.addProperty(brandNames[i], brandProperties[i].getExpr(brandExpr));
        }
        brandQuery.and(findProperty("Loya.in[Brand]").getExpr(brandExpr).getWhere());
        brandQuery.and(findProperty("name[Brand]").getExpr(brandExpr).getWhere());

        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> itemGroupResult = brandQuery.executeClasses(context);

        for (int i = 0; i < itemGroupResult.size(); i++) {
            DataObject brandObject = itemGroupResult.getKey(i).singleValue();
            Integer idLoya = (Integer) itemGroupResult.getValue(i).get("idLoya").getValue();
            String name = (String) itemGroupResult.getValue(i).get("name").getValue();
            result.add(new Brand(idLoya, name, brandObject));
        }
        return result;
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

    private SynchronizeData readItems(ExecutionContext<ClassPropertyInterface> context, boolean deleteInactiveItemGroups) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<Item> itemsList = new ArrayList<>();
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
        query.addProperty("idLoyaBrand", findProperty("idLoyaBrand[Item]").getExpr(skuExpr));
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
            Integer idLoyaBrand = (Integer) valueEntry.get("idLoyaBrand").getValue();
            itemsList.add(new Item(idSku, idLoyaItemGroup, captionItem, idUOMItem, splitItem, idSkuGroup, idLoyaBrand));
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
        return new SynchronizeData(itemsList, itemGroupsMap, itemItemGroupsMap, deleteItemGroupsList);
    }

    private boolean uploadItemGroups(ExecutionContext context, SettingsLoya settings, Map<Long, List<GoodGroupLink>> itemItemGroupsMap, Map<DataObject, List<Object>> itemGroupsMap,
                                     List<Long> deleteItemGroupsList, Map<String, Integer> discountLimits) throws JSONException, IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        boolean succeeded = true;
        for (Map.Entry<DataObject, List<Object>> entry : itemGroupsMap.entrySet()) {
            DataObject itemGroupObject = entry.getKey();
            List<Object> itemGroupData = entry.getValue();
            if (!uploadItemGroup(context, settings, itemItemGroupsMap, itemGroupData, itemGroupObject, discountLimits))
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

    private boolean uploadItemGroup(ExecutionContext context, SettingsLoya settings, Map<Long, List<GoodGroupLink>> itemItemGroupsMap,
                                    List<Object> itemGroupData, DataObject itemGroupObject, Map<String, Integer> discountLimits)
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
        requestBody.put("limits", discountLimits);

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

    private boolean existsItemGroup(SettingsLoya settings, Long idItemGroup) throws IOException {
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

    private boolean deleteItemGroup(ExecutionContext context, SettingsLoya settings, Long idItemGroup) throws IOException {
        HttpDeleteWithBody deleteRequest = new HttpDeleteWithBody(settings.url + "goodgroup/" + settings.partnerId + "/" + idItemGroup);
        deleteRequest.setEntity(new StringEntity("[" + idItemGroup + "]"));
        HttpResponse response = executeRequest(deleteRequest, settings.sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (!succeeded)
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Delete ItemGroup Error"));
        return succeeded;
    }

    private boolean uploadBrands(ExecutionContext context, SettingsLoya settings, List<Brand> brandsList) throws IOException, JSONException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        boolean succeeded = true;
        for (Brand brand : brandsList) {
            if (!uploadBrand(context, settings, brand))
                succeeded = false;
        }
        return succeeded;
    }

    private boolean uploadBrand(ExecutionContext context, SettingsLoya settings, Brand brand) throws JSONException, IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        ServerLoggers.importLogger.info("Loya: synchronizing brand " + brand.name + " started");

        JSONObject requestBody = new JSONObject();
        requestBody.put("id", brand.id);
        requestBody.put("name", brand.name);
        requestBody.put("description", brand.name);

        if(existsBrand(settings, brand.id)) {
            ServerLoggers.importLogger.info("Loya: modifying brand " + brand.name);
            return modifyBrand(context, settings, brand.id, requestBody);
        } else {
            ServerLoggers.importLogger.info("Loya: creating brand " + brand.name);
            return createBrand(context, brand.brandObject, settings.url, settings.sessionKey, requestBody);
        }
    }

    private boolean existsBrand(SettingsLoya settings, Integer idBrand) throws IOException {
        HttpGet getRequest = new HttpGet(settings.url + "brand/" + idBrand);
        return requestSucceeded(executeRequest(getRequest, settings.sessionKey));
    }

    private boolean modifyBrand(ExecutionContext context, SettingsLoya settings, Integer idBrand, JSONObject requestBody) throws IOException {
        HttpPost postRequest = new HttpPost(settings.url + "brand/" + idBrand);
        HttpResponse response = executeRequest(postRequest, requestBody, settings.sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (!succeeded)
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Modify Brand Error"));
        return succeeded;
    }

    private boolean createBrand(ExecutionContext context, DataObject brandObject, String url, String sessionKey, JSONObject requestBody)
            throws IOException, JSONException, SQLException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
        HttpPut putRequest = new HttpPut(url + "brand");
        HttpResponse response = executeRequest(putRequest, requestBody, sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (succeeded) {
            JSONObject responseObject = new JSONObject(getResponseMessage(response));
            setIdLoyaBrand(context, brandObject, responseObject.getInt("id"));
        } else {
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Create Brand Error"));
        }
        return succeeded;
    }

    private boolean uploadCategories(ExecutionContext context, SettingsLoya settings, List<Category> categoriesList, Map<String, Integer> discountLimits) throws IOException, JSONException {
        boolean succeeded = true;
        for (Category category : categoriesList) {
            if (!uploadCategory(context, settings, category, discountLimits))
                succeeded = false;
        }
        return succeeded;
    }

    private boolean uploadCategory(ExecutionContext context, SettingsLoya settings, Category category, Map<String, Integer> discountLimits) throws JSONException, IOException {

        ServerLoggers.importLogger.info("Loya: synchronizing category " + category.overId + " started");
        JSONObject requestBody = new JSONObject();
        requestBody.put("partnerId", settings.partnerId);
        requestBody.put("categoryId", category.overId);
        requestBody.put("parentId", category.parentId);
        requestBody.put("name", category.name);
        requestBody.put("limits", discountLimits);
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

    private boolean modifyCategory(ExecutionContext context, SettingsLoya settings, Long categoryId, JSONObject requestBody) throws IOException {
        HttpPost postRequest = new HttpPost(settings.url + "category/" + settings.partnerId + "/" + categoryId);
        HttpResponse response = executeRequest(postRequest, requestBody, settings.sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (!succeeded)
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Modify Category Error"));
        return succeeded;
    }

    private boolean createCategory(ExecutionContext context, String url, String sessionKey, JSONObject requestBody) throws IOException {
        HttpPut putRequest = new HttpPut(url + "category");
        HttpResponse response = executeRequest(putRequest, requestBody, sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (!succeeded)
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Create Category Error"));
        return succeeded;
    }

    private boolean uploadItems(ExecutionContext context, SettingsLoya settings, List<Item> itemsList, Map<String, Integer> discountLimits) throws IOException, JSONException {
        boolean succeeded = true;
        for (Item item : itemsList) {
            if (!uploadItem(context, settings, item, discountLimits))
                succeeded = false;
        }
        return succeeded;
    }

    private boolean uploadItem(ExecutionContext context, SettingsLoya settings, Item item, Map<String, Integer> discountLimits) throws JSONException, IOException {
        ServerLoggers.importLogger.info("Loya: synchronizing good " + item.id + " started");
        JSONObject requestBody = new JSONObject();
        requestBody.put("partnerId", settings.partnerId);
        requestBody.put("sku", item.id);
        requestBody.put("categoryId", parseGroup(item.idSkuGroup));
        requestBody.put("brandId", item.idLoyaBrand);
        requestBody.put("name", item.caption);
        requestBody.put("measurement", item.idUOM);
        requestBody.put("margin", 0);
        requestBody.put("dimension", item.split ? "weight" : "piece");
        requestBody.put("limits", discountLimits);

        if (existsItem(settings, item.id)) {
            ServerLoggers.importLogger.info("Loya: modifying good " + item.id);
            return modifyItem(context, settings, item.id, requestBody);
        } else {
            ServerLoggers.importLogger.info("Loya: creating good " + item.id);
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

    private boolean modifyItem(ExecutionContext context, SettingsLoya settings, String idItem, JSONObject requestBody) throws IOException {
        HttpPost postRequest = new HttpPost(settings.url + "good/" + settings.partnerId + "/" + idItem);
        HttpResponse response = executeRequest(postRequest, requestBody, settings.sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (!succeeded)
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Modify Item Error"));
        return succeeded;
    }

    private boolean createItem(ExecutionContext context, String url, String sessionKey, JSONObject requestBody) throws IOException {
        HttpPut putRequest = new HttpPut(url + "good");
        HttpResponse response = executeRequest(putRequest, requestBody, sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (!succeeded)
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Create Item Error"));
        return succeeded;
    }

    private boolean uploadItemItemGroups(ExecutionContext context, SettingsLoya settings, Map<Long, List<GoodGroupLink>> itemItemGroupsMap) throws IOException {
        boolean succeeded = true;
        ServerLoggers.importLogger.info("Loya: synchronizing goodGroupLinks");
        for (Map.Entry<Long, List<GoodGroupLink>> entry : itemItemGroupsMap.entrySet()) {
            Long idItemGroup = entry.getKey();
            List<GoodGroupLink> itemsList = entry.getValue();

            String deleteList = "";
            int deleteCount = 0;
            HttpResponse getResponse = executeRequest(new HttpGet(settings.url + "goodgrouplink/" + settings.partnerId + "/" + idItemGroup), settings.sessionKey);
            String responseMessage = getResponseMessage(getResponse);
            try {
                JSONArray itemsArray = new JSONArray(responseMessage);
                ServerLoggers.importLogger.info(String.format("Loya: synchronizing goodGroupLinks. Group %s: %s items before synchronization", idItemGroup, itemsArray.length()));
                for (int i = 0; i < itemsArray.length(); i++) {
                    JSONObject item = itemsArray.getJSONObject(i);
                    String sku = item.getString("sku");
                    Iterator<GoodGroupLink> iterator = itemsList.iterator();
                    boolean found = false;
                    while(iterator.hasNext() && !found) {
                        GoodGroupLink goodGroupLink = iterator.next();
                        if (goodGroupLink.sku.equals(sku)) {
                            iterator.remove();
                            found = true;
                        }
                    }
                    if(!found) {
                        deleteList += (deleteList.isEmpty() ? "" : ",") + "\"" + sku + "\"";
                        deleteCount++;
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
            ServerLoggers.importLogger.info(String.format("Loya: synchronizing goodGroupLinks. Group %s: deleted %s items, added %s items", idItemGroup, deleteCount, itemsList.size()));
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

    private Map<String, Integer> getDiscountLimits(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, Integer> limitsMap = new HashMap<>();
        limitsMap.put("maxDiscount", (Integer) findProperty("maxDiscountLoya[]").read(context));
        limitsMap.put("maxAllowBonus", (Integer) findProperty("maxAllowBonusLoya[]").read(context));
        limitsMap.put("maxAwardBonus", (Integer) findProperty("maxAwardBonusLoya[]").read(context));
        return limitsMap;
    }

    private class SynchronizeData {
        public List<Item> itemsList;
        public Map<DataObject, List<Object>> itemGroupsMap;
        public Map<Long, List<GoodGroupLink>> itemItemGroupsMap;
        public List<Long> deleteItemGroupsList;

        public SynchronizeData(List<Item> itemsList, Map<DataObject, List<Object>> itemGroupsMap, Map<Long, List<GoodGroupLink>> itemItemGroupsMap, List<Long> deleteItemGroupsList) {
            this.itemsList = itemsList;
            this.itemGroupsMap = itemGroupsMap;
            this.itemItemGroupsMap = itemItemGroupsMap;
            this.deleteItemGroupsList = deleteItemGroupsList;
        }
    }

    private class Brand {
        Integer id;
        String name;
        DataObject brandObject;

        public Brand(Integer id, String name, DataObject brandObject) {
            this.id = id;
            this.name = name;
            this.brandObject = brandObject;
        }
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

    private class Item {
        String id;
        Long idLoyaItemGroup;
        String caption;
        String idUOM;
        Boolean split;
        String idSkuGroup;
        Integer idLoyaBrand;

        public Item(String id, Long idLoyaItemGroup, String caption, String idUOM, Boolean split, String idSkuGroup, Integer idLoyaBrand) {
            this.id = id;
            this.idLoyaItemGroup = idLoyaItemGroup;
            this.caption = caption;
            this.idUOM = idUOM;
            this.split = split;
            this.idSkuGroup = idSkuGroup;
            this.idLoyaBrand = idLoyaBrand;
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
