package lsfusion.erp.region.by.ukm;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ValueClass;
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

    public SynchronizeLoyaActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            boolean disableSynchronizeItems = findProperty("disableSynchronizeItemsLoya[]").read(context) != null;
            boolean deleteInactiveItemGroups = findProperty("deleteInactiveItemGroupsLoya[]").read(context) != null;
            boolean logRequests = findProperty("logRequestsLoya[]").read(context) != null;
            boolean useBarcodeAsId = findProperty("useBarcodeAsIdLoya[]").read(context) != null;
            boolean useMinPrice = findProperty("useMinPrice[]").read(context) != null;
            Map<String, Integer> discountLimits = getDiscountLimits(context);

            boolean succeeded = true;

            SettingsLoya settings = login(context);
            if (settings.error == null) {

                //synchronize brands
                if(!disableSynchronizeItems) {
                    List<Brand> brandsList = readBrands(context);
                    if(!uploadBrands(context, settings, brandsList, logRequests)) {
                        succeeded = false;
                    }
                }

                if(succeeded) {

                    SynchronizeData data = readItems(context, deleteInactiveItemGroups, useBarcodeAsId, useMinPrice);
                    List<Category> categoriesList = readCategories(context);

                    if ((disableSynchronizeItems || uploadCategories(context, settings, categoriesList, discountLimits, logRequests)) &&
                            (disableSynchronizeItems || uploadItems(context, settings, data.itemsList, discountLimits, data.minPriceLimitsMap, logRequests)) &&
                            uploadItemGroups(context, settings, data.itemItemGroupsMap, data.itemGroupsMap, data.deleteItemGroupsList, discountLimits, logRequests) &&
                            uploadItemItemGroups(context, settings, data.itemItemGroupsMap, logRequests))
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

    private SynchronizeData readItems(ExecutionContext<ClassPropertyInterface> context, boolean deleteInactiveItemGroups, boolean useBarcodeAsId, boolean useMinPrice) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<Item> itemsList = new ArrayList<>();
        Map<DataObject, GoodGroup> itemGroupsMap = new HashMap<>();
        Map<Long, List<GoodGroupLink>> itemItemGroupsMap = new HashMap<>();
        Map<DataObject, Long> deleteItemGroupsList = new HashMap<>();

        KeyExpr groupExpr = new KeyExpr("loyaItemGroup");
        KeyExpr skuExpr = new KeyExpr("sku");

        ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "loyaItemGroup", groupExpr, "sku", skuExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("idLoyaItemGroup", findProperty("id[LoyaItemGroup]").getExpr(groupExpr));
        query.addProperty("nameLoyaItemGroup", findProperty("name[LoyaItemGroup]").getExpr(groupExpr));
        query.addProperty("quantity", findProperty("quantity[Item, LoyaItemGroup]").getExpr(skuExpr, groupExpr));
        query.addProperty("descriptionLoyaItemGroup", findProperty("description[LoyaItemGroup]").getExpr(groupExpr));
        query.addProperty("idSku", findProperty("id[Sku]").getExpr(skuExpr));
        query.addProperty("barcode", findProperty("idBarcode[Sku]").getExpr(skuExpr));
        query.addProperty("captionItem", findProperty("nameAttribute[Item]").getExpr(skuExpr));
        query.addProperty("idUOMItem", findProperty("idUOM[Item]").getExpr(skuExpr));
        query.addProperty("splitItem", findProperty("passScales[Item]").getExpr(skuExpr));
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
            idLoyaItemGroup = idLoyaItemGroup == null ? (Long) groupObject.getValue() : idLoyaItemGroup;
            String nameItemGroup = trim((String) valueEntry.get("nameLoyaItemGroup").getValue());
            BigDecimal quantity = (BigDecimal) valueEntry.get("quantity").getValue();
            String descriptionItemGroup = trim((String) valueEntry.get("descriptionLoyaItemGroup").getValue());
            String idSku = trim((String) valueEntry.get("idSku").getValue());
            String barcode = trim((String) valueEntry.get("barcode").getValue());
            String id = useBarcodeAsId ? barcode : idSku;
            String captionItem = trimToEmpty((String) valueEntry.get("captionItem").getValue());
            String idUOMItem = trim((String) valueEntry.get("idUOMItem").getValue());
            Boolean splitItem = valueEntry.get("splitItem").getValue() != null;
            String idSkuGroup = trim((String) valueEntry.get("idSkuGroup").getValue());
            Integer idLoyaBrand = (Integer) valueEntry.get("idLoyaBrand").getValue();
            itemsList.add(new Item(id, captionItem, idUOMItem, splitItem, idSkuGroup, idLoyaBrand));
            itemGroupsMap.put(groupObject, new GoodGroup(idLoyaItemGroup, nameItemGroup, descriptionItemGroup));
            List<GoodGroupLink> skuList = itemItemGroupsMap.get(idLoyaItemGroup);
            if (skuList == null)
                skuList = new ArrayList<>();
            skuList.add(new GoodGroupLink(id, quantity));
            itemItemGroupsMap.put(idLoyaItemGroup, skuList);
        }

        //get loya groups without items and not active for deletion
        KeyExpr emptyGroupExpr = new KeyExpr("loyaItemGroup");
        QueryBuilder<Object, Object> emptyQuery = new QueryBuilder<>(MapFact.singletonRev((Object) "loyaItemGroup", emptyGroupExpr));
        String[] emptyGroupNames = new String[]{"idLoyaItemGroup", "nameLoyaItemGroup", "descriptionLoyaItemGroup", "empty", "active"};
        LCP[] emptyGroupProperties = findProperties("id[LoyaItemGroup]", "name[LoyaItemGroup]", "description[LoyaItemGroup]", "empty[LoyaItemGroup]",
                "active[LoyaItemGroup]");
        for (int i = 0; i < emptyGroupProperties.length; i++) {
            emptyQuery.addProperty(emptyGroupNames[i], emptyGroupProperties[i].getExpr(emptyGroupExpr));
        }
        emptyQuery.and(findProperty("empty[LoyaItemGroup]").getExpr(emptyGroupExpr).getWhere().or(findProperty("active[LoyaItemGroup]").getExpr(emptyGroupExpr).getWhere().not()));
        emptyQuery.and(findProperty("deleted[LoyaItemGroup]").getExpr(emptyGroupExpr).getWhere().not());
        emptyQuery.and(findProperty("name[LoyaItemGroup]").getExpr(emptyGroupExpr).getWhere());

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
                itemGroupsMap.put(groupObject, new GoodGroup(idLoyaItemGroup == null ? (Long) groupObject.getValue() : idLoyaItemGroup, nameItemGroup, descriptionItemGroup));
            if(!active && idLoyaItemGroup != null && deleteInactiveItemGroups)
                deleteItemGroupsList.put(groupObject, idLoyaItemGroup);
        }
        return new SynchronizeData(itemsList, itemGroupsMap, itemItemGroupsMap, deleteItemGroupsList, useMinPrice ? readMinPriceLimitsMap(context) : null);
    }

    private Map<String, List<MinPriceLimit>> readMinPriceLimitsMap(ExecutionContext<ClassPropertyInterface> context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, List<MinPriceLimit>> result = new HashMap<>();

        KeyExpr skuExpr = new KeyExpr("sku");
        KeyExpr departmentStoreExpr = new KeyExpr("departmentStore");

        ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "sku", skuExpr, "departmentStore", departmentStoreExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("idSku", findProperty("id[Sku]").getExpr(skuExpr));
        query.addProperty("idLoyaDepartmentStore", findProperty("idLoya[DepartmentStore]").getExpr(departmentStoreExpr));
        query.addProperty("loyaMinPrice", findProperty("loyaMinPrice[Item, DepartmentStore]").getExpr(context.getModifier(), skuExpr, departmentStoreExpr));
        query.and(findProperty("id[Sku]").getExpr(skuExpr).getWhere());
        query.and(findProperty("inLoya[DepartmentStore]").getExpr(departmentStoreExpr).getWhere());
        query.and(findProperty("idLoya[DepartmentStore]").getExpr(departmentStoreExpr).getWhere());
        query.and(findProperty("loyaMinPrice[Item, DepartmentStore]").getExpr(context.getModifier(), skuExpr, departmentStoreExpr).getWhere());

        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> queryResult = query.executeClasses(context);
        for (int i = 0; i < queryResult.size(); i++) {
            ImMap<Object, ObjectValue> valueEntry = queryResult.getValue(i);

            String idSku = trim((String) valueEntry.get("idSku").getValue());
            Integer idLoyaDepartmentStore = (Integer) valueEntry.get("idLoyaDepartmentStore").getValue();
            BigDecimal minPrice = (BigDecimal) valueEntry.get("loyaMinPrice").getValue();

            List<MinPriceLimit> minPriceLimits = result.get(idSku);
            if(minPriceLimits == null) {
                minPriceLimits = new ArrayList<>();
            }
            minPriceLimits.add(new MinPriceLimit(idLoyaDepartmentStore, minPrice));

            result.put(idSku, minPriceLimits);
        }
        return result;
    }

    private boolean uploadItemGroups(ExecutionContext context, SettingsLoya settings, Map<Long, List<GoodGroupLink>> itemItemGroupsMap, Map<DataObject, GoodGroup> itemGroupsMap,
                                     Map<DataObject, Long> deleteItemGroupsMap, Map<String, Integer> discountLimits, boolean logRequests) throws JSONException, IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        boolean succeeded = true;
        for (Map.Entry<DataObject, GoodGroup> entry : itemGroupsMap.entrySet()) {
            DataObject itemGroupObject = entry.getKey();
            GoodGroup itemGroupData = entry.getValue();
            if (!uploadItemGroup(context, settings, itemItemGroupsMap, itemGroupData, itemGroupObject, discountLimits, logRequests))
                succeeded = false;
        }
        for(Map.Entry<DataObject, Long> deleteItemGroup : deleteItemGroupsMap.entrySet()) {
            Long idItemGroup = deleteItemGroup.getValue();
            if (existsItemGroup(settings, idItemGroup, logRequests)) {
                ServerLoggers.importLogger.info("Loya: deleting goodGroup " + idItemGroup);
                if (!deleteItemGroup(context, settings, deleteItemGroup.getKey(), idItemGroup, logRequests))
                    succeeded = false;
            }
        }
        return succeeded;
    }

    private boolean uploadItemGroup(ExecutionContext context, SettingsLoya settings, Map<Long, List<GoodGroupLink>> itemItemGroupsMap,
                                    GoodGroup goodGroup, DataObject itemGroupObject, Map<String, Integer> discountLimits, boolean logRequests)
            throws JSONException, IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        ServerLoggers.importLogger.info("Loya: synchronizing goodGroup " + goodGroup.id + " started");
        JSONObject requestBody = new JSONObject();
        requestBody.put("partnerId", settings.partnerId);
        requestBody.put("id", goodGroup.id);
        requestBody.put("name", goodGroup.name == null ? "" : goodGroup.name);
        requestBody.put("description", goodGroup.description == null ? "" : goodGroup.description);
        requestBody.put("limits", discountLimits);

        if (existsItemGroup(settings, goodGroup.id, logRequests)) {
            ServerLoggers.importLogger.info("Loya: modifying goodGroup " + goodGroup.id);
            return modifyItemGroup(context, settings, goodGroup.id, itemGroupObject, requestBody, logRequests);
        } else {
            ServerLoggers.importLogger.info("Loya: creating goodGroup " + goodGroup.id);
            Long idItemGroup = createItemGroup(context, itemGroupObject, settings.url, settings.sessionKey, requestBody, logRequests);
            if (idItemGroup != null) {//id группы изменился
                List<GoodGroupLink> skuList = itemItemGroupsMap.get(goodGroup.id);
                if(skuList == null)
                    skuList = new ArrayList<>();
                itemItemGroupsMap.put(idItemGroup, skuList);
                itemItemGroupsMap.remove(goodGroup.id);
            }
            return idItemGroup != null;
        }
    }

    private boolean existsItemGroup(SettingsLoya settings, Long idItemGroup, boolean logRequests) throws IOException {
        String requestURL = settings.url + "goodgroup/" + settings.partnerId + "/" + idItemGroup;
        if(logRequests) {
            ServerLoggers.importLogger.info(String.format("Log Request to URL %s", requestURL));
        }
        HttpGet getRequest = new HttpGet(requestURL);
        return requestSucceeded(executeRequest(getRequest, settings.sessionKey));
    }

    private boolean modifyItemGroup(ExecutionContext context, SettingsLoya settings, Long idItemGroup, DataObject itemGroupObject, JSONObject requestBody, boolean logRequests)
            throws IOException, JSONException, SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        String requestURL = settings.url + "goodgroup/" + settings.partnerId + "/" + idItemGroup;
        if(logRequests) {
            ServerLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpPost postRequest = new HttpPost(requestURL);
        HttpResponse response = executeRequest(postRequest, requestBody, settings.sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (succeeded) {
            Long newIdItemGroup = new JSONObject(getResponseMessage(response)).getLong("id");
            if(!idItemGroup.equals(newIdItemGroup)) {
                setIdLoyaItemGroup(context, itemGroupObject, newIdItemGroup);
            }
        } else
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Modify ItemGroup Error"));
        return succeeded;
    }

    private Long createItemGroup(ExecutionContext context, DataObject itemGroupObject, String url, String sessionKey, JSONObject requestBody, boolean logRequests)
            throws IOException, JSONException, SQLException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
        String requestURL = url + "goodgroup";
        if(logRequests) {
            ServerLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpPut putRequest = new HttpPut(requestURL);
        HttpResponse response = executeRequest(putRequest, requestBody, sessionKey);
        boolean succeeded = requestSucceeded(response);
        String responseMessage = getResponseMessage(response);
        if (succeeded)
            setIdLoyaItemGroup(context, itemGroupObject, new JSONObject(responseMessage).getLong("id"));
        else
            context.delayUserInteraction(new MessageClientAction(responseMessage, "Loya: Create ItemGroup Error"));
        return succeeded ? new JSONObject(responseMessage).getLong("id") : null;
    }

    private void setIdLoyaItemGroup(ExecutionContext context, DataObject itemGroupObject, Long id) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        try (ExecutionContext.NewSession newContext = context.newSession()) {
            findProperty("id[LoyaItemGroup]").change(id, newContext, itemGroupObject);
            newContext.apply();
        }
    }

    private boolean deleteItemGroup(ExecutionContext context, SettingsLoya settings, DataObject itemGroupObject, Long idItemGroup, boolean logRequests) throws IOException, SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        String requestURL = settings.url + "goodgroup/" + settings.partnerId + "/" + idItemGroup;
        String requestBody = "[" + idItemGroup + "]";
        if(logRequests) {
            ServerLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpDeleteWithBody deleteRequest = new HttpDeleteWithBody(settings.url + "goodgroup/" + settings.partnerId + "/" + idItemGroup);
        deleteRequest.setEntity(new StringEntity(requestBody));
        HttpResponse response = executeRequest(deleteRequest, settings.sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (succeeded) {
            try (ExecutionContext.NewSession newContext = context.newSession()) {
                findProperty("deleted[LoyaItemGroup]").change(true, newContext, itemGroupObject);
                newContext.apply();
            }
        } else {
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Delete ItemGroup Error"));
        }
        return succeeded;
    }

    private boolean uploadBrands(ExecutionContext context, SettingsLoya settings, List<Brand> brandsList, boolean logRequests) throws IOException, JSONException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        boolean succeeded = true;
        for (Brand brand : brandsList) {
            if (!uploadBrand(context, settings, brand, logRequests))
                succeeded = false;
        }
        return succeeded;
    }

    protected boolean uploadBrand(ExecutionContext context, SettingsLoya settings, Brand brand, boolean logRequests) throws JSONException, IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        ServerLoggers.importLogger.info("Loya: synchronizing brand " + brand.name + " started");

        JSONObject requestBody = new JSONObject();
        requestBody.put("id", brand.id);
        requestBody.put("name", brand.name);
        requestBody.put("description", brand.name);

        if(existsBrand(settings, brand.id, logRequests)) {
            ServerLoggers.importLogger.info("Loya: modifying brand " + brand.name);
            return modifyBrand(context, settings, brand.id, requestBody, logRequests);
        } else {
            ServerLoggers.importLogger.info("Loya: creating brand " + brand.name);
            return createBrand(context, brand.brandObject, settings.url, settings.sessionKey, requestBody, logRequests);
        }
    }

    private boolean existsBrand(SettingsLoya settings, Integer idBrand, boolean logRequests) throws IOException {
        String requestURL = settings.url + "brand/" + idBrand;
        if(logRequests) {
            ServerLoggers.importLogger.info(String.format("Log Request to URL %s", requestURL));
        }
        HttpGet getRequest = new HttpGet(requestURL);
        return requestSucceeded(executeRequest(getRequest, settings.sessionKey));
    }

    private boolean modifyBrand(ExecutionContext context, SettingsLoya settings, Integer idBrand, JSONObject requestBody, boolean logRequests) throws IOException {
        String requestURL = settings.url + "brand/" + idBrand;
        if(logRequests) {
            ServerLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpPost postRequest = new HttpPost(requestURL);
        HttpResponse response = executeRequest(postRequest, requestBody, settings.sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (!succeeded)
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Modify Brand Error"));
        return succeeded;
    }

    private boolean createBrand(ExecutionContext context, DataObject brandObject, String url, String sessionKey, JSONObject requestBody, boolean logRequests)
            throws IOException, JSONException, SQLException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
        String requestURL = url + "brand";
        if(logRequests) {
            ServerLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpPut putRequest = new HttpPut(requestURL);
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

    private void setIdLoyaBrand(ExecutionContext context, DataObject brandObject, Integer idLoyaBrand) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        try (ExecutionContext.NewSession newContext = context.newSession()) {
            findProperty("idLoya[Brand]").change(idLoyaBrand, newContext, brandObject);
            newContext.apply();
        }
    }

    private boolean uploadCategories(ExecutionContext context, SettingsLoya settings, List<Category> categoriesList, Map<String, Integer> discountLimits, boolean logRequests) throws IOException, JSONException {
        boolean succeeded = true;
        for (Category category : categoriesList) {
            if (uploadCategory(context, settings, category, discountLimits, logRequests) != null)
                succeeded = false;
        }
        return succeeded;
    }

    protected String uploadCategory(ExecutionContext context, SettingsLoya settings, Category category, Map<String, Integer> discountLimits, boolean logRequests) throws JSONException, IOException {

        ServerLoggers.importLogger.info("Loya: synchronizing category " + category.overId + " started");
        JSONObject requestBody = new JSONObject();
        requestBody.put("partnerId", settings.partnerId);
        requestBody.put("categoryId", category.overId);
        requestBody.put("parentId", category.parentId);
        requestBody.put("name", category.name);
        requestBody.put("limits", discountLimits);
        requestBody.put("state", "active");

        String result;
        if (existsCategory(settings, category.overId, logRequests)) {
            ServerLoggers.importLogger.info("Loya: modifying category " + category.overId);
            result = modifyCategory(context, settings, category.overId, requestBody, logRequests);
        } else {
            ServerLoggers.importLogger.info("Loya: creating category " + category.overId);
            result = createCategory(context, settings.url, settings.sessionKey, requestBody, logRequests);
        }
        return result;
    }

    private boolean existsCategory(SettingsLoya settings, Long idCategory, boolean logRequests) throws IOException {
        String requestURL = settings.url + "category/" + settings.partnerId + "/" + idCategory;
        if(logRequests) {
            ServerLoggers.importLogger.info(String.format("Log Request to URL %s", requestURL));
        }
        HttpGet getRequest = new HttpGet(requestURL);
        return requestSucceeded(executeRequest(getRequest, settings.sessionKey));
    }

    private String modifyCategory(ExecutionContext context, SettingsLoya settings, Long categoryId, JSONObject requestBody, boolean logRequests) throws IOException {
        String result = null;
        String requestURL = settings.url + "category/" + settings.partnerId + "/" + categoryId;
        if(logRequests) {
            ServerLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpPost postRequest = new HttpPost(requestURL);
        HttpResponse response = executeRequest(postRequest, requestBody, settings.sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (!succeeded) {
            result = getResponseMessage(response);
            context.delayUserInteraction(new MessageClientAction(result, "Loya: Modify Category Error"));
        }
        return result;
    }

    private String createCategory(ExecutionContext context, String url, String sessionKey, JSONObject requestBody, boolean logRequests) throws IOException {
        String result = null;
        String requestURL = url + "category";
        if(logRequests) {
            ServerLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpPut putRequest = new HttpPut(url + "category");
        HttpResponse response = executeRequest(putRequest, requestBody, sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (!succeeded) {
            result = getResponseMessage(response);
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Create Category Error"));
        }
        return result;
    }

    private boolean uploadItems(ExecutionContext context, SettingsLoya settings, List<Item> itemsList, Map<String, Integer> discountLimits, Map<String, List<MinPriceLimit>> minPriceLimitsMap, boolean logRequests) throws IOException, JSONException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        boolean succeeded = true;
        for (Item item : itemsList) {
            List<MinPriceLimit> minPriceLimits = minPriceLimitsMap != null ? minPriceLimitsMap.get(item.id) : null;
            if (uploadItem(context, settings, item, discountLimits, minPriceLimits, logRequests) != null)
                succeeded = false;
        }
        return succeeded;
    }

    protected String uploadItem(ExecutionContext context, SettingsLoya settings, Item item, Map<String, Integer> discountLimits, List<MinPriceLimit> minPriceLimits, boolean logRequests) throws JSONException, IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
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

        if(minPriceLimits != null && !minPriceLimits.isEmpty()) {
            JSONArray limitByLocationsArray = new JSONArray();
            for (MinPriceLimit minPriceLimit : minPriceLimits) {
                JSONObject limitByLocation = new JSONObject();
                limitByLocation.put("locationId", minPriceLimit.idDepartmentStore);
                JSONObject limits = new JSONObject();
                limits.put("maxDiscount", minPriceLimit.minPrice);
                limits.put("maxDiscountType", "fixprice");
                limitByLocation.put("limits", limits);
                limitByLocationsArray.put(limitByLocation);
            }
            requestBody.put("limitByLocations", limitByLocationsArray);
        }

        String result;
        if (existsItem(settings, item.id, logRequests)) {
            ServerLoggers.importLogger.info("Loya: modifying good " + item.id);
            result = modifyItem(context, settings, item.id, requestBody, logRequests);
        } else {
            ServerLoggers.importLogger.info("Loya: creating good " + item.id);
            result = createItem(context, settings.url, settings.sessionKey, requestBody, logRequests);
        }
        return result;
    }

    private boolean existsItem(SettingsLoya settings, String idItem, boolean logRequests) throws IOException {
        String requestURL = settings.url + "good/" + settings.partnerId + "/" + idItem;
        if(logRequests) {
            ServerLoggers.importLogger.info(String.format("Log Request to URL %s", requestURL));
        }
        HttpGet getRequest = new HttpGet(requestURL);
        return requestSucceeded(executeRequest(getRequest, settings.sessionKey));
    }

    private String modifyItem(ExecutionContext context, SettingsLoya settings, String idItem, JSONObject requestBody, boolean logRequests) throws IOException {
        String result = null;
        String requestURL = settings.url + "good/" + settings.partnerId + "/" + idItem;
        if(logRequests) {
            ServerLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpPost postRequest = new HttpPost(requestURL);
        HttpResponse response = executeRequest(postRequest, requestBody, settings.sessionKey);
        if (!requestSucceeded(response)) {
            result = getResponseMessage(response);
            context.delayUserInteraction(new MessageClientAction(result, "Loya: Modify Item Error"));
        }
        return result;
    }

    private String createItem(ExecutionContext context, String url, String sessionKey, JSONObject requestBody, boolean logRequests) throws IOException {
        String result = null;
        String requestURL = url + "good";
        if(logRequests) {
            ServerLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpPut putRequest = new HttpPut(requestURL);
        HttpResponse response = executeRequest(putRequest, requestBody, sessionKey);
        if (!requestSucceeded(response)) {
            result = getResponseMessage(response);
            context.delayUserInteraction(new MessageClientAction(result, "Loya: Create Item Error"));
        }
        return result;
    }

    private boolean uploadItemItemGroups(ExecutionContext context, SettingsLoya settings, Map<Long, List<GoodGroupLink>> itemItemGroupsMap, boolean logRequests) throws IOException {
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
                String requestURL = settings.url + "goodgrouplink/" + settings.partnerId + "/" + idItemGroup + "/deleteList";
                String requestBody = "[" + deleteList + "]";
                if(logRequests) {
                    ServerLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
                }
                HttpPost postRequest = new HttpPost(requestURL);
                HttpResponse response = executeRequest(postRequest, requestBody, settings.sessionKey);
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
                    if (!createGoodGroupLink(context, settings, idItemGroup, goodGroupLink, logRequests))
                        succeeded = false;
                }
            }
            ServerLoggers.importLogger.info(String.format("Loya: synchronizing goodGroupLinks. Group %s: deleted %s items, added %s items", idItemGroup, deleteCount, itemsList.size()));
        }
        return succeeded;
    }

    private boolean createGoodGroupLink(ExecutionContext context, SettingsLoya settings, Long idItemGroup, GoodGroupLink goodGroupLink, boolean logRequests) throws IOException {
        String requestURL = settings.url + "goodgrouplink/" + settings.partnerId + "/" + idItemGroup + "/upload";
        HttpPost postRequest = new HttpPost(requestURL);
        String query = goodGroupLink.quantity == null ? String.format("[{\"sku\":\"%s\"}]", goodGroupLink.sku) :
                String.format("[{\"sku\":\"%s\",\"quantity\":%s}]", goodGroupLink.sku, goodGroupLink.quantity);
        if(logRequests) {
            ServerLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, query));
        }
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

    protected Map<String, Integer> getDiscountLimits(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        return getDiscountLimits(
                (Integer) findProperty("maxDiscountLoya[]").read(context),
                (Integer) findProperty("maxAllowBonusLoya[]").read(context),
                (Integer) findProperty("maxAwardBonusLoya[]").read(context));
    }

    protected Map<String, Integer> getDiscountLimits(Integer maxDiscount, Integer maxAllowBonus, Integer maxAwardBonus) {
        Map<String, Integer> limitsMap = new HashMap<>();
        limitsMap.put("maxDiscount", maxDiscount);
        limitsMap.put("maxAllowBonus", maxAllowBonus);
        limitsMap.put("maxAwardBonus", maxAwardBonus);
        return limitsMap;
    }

    private class SynchronizeData {
        public List<Item> itemsList;
        public Map<DataObject, GoodGroup> itemGroupsMap;
        public Map<Long, List<GoodGroupLink>> itemItemGroupsMap;
        public Map<DataObject, Long> deleteItemGroupsList;
        public Map<String, List<MinPriceLimit>> minPriceLimitsMap;

        public SynchronizeData(List<Item> itemsList, Map<DataObject, GoodGroup> itemGroupsMap, Map<Long, List<GoodGroupLink>> itemItemGroupsMap, Map<DataObject, Long> deleteItemGroupsList,
                               Map<String, List<MinPriceLimit>> minPriceLimitsMap) {
            this.itemsList = itemsList;
            this.itemGroupsMap = itemGroupsMap;
            this.itemItemGroupsMap = itemItemGroupsMap;
            this.deleteItemGroupsList = deleteItemGroupsList;
            this.minPriceLimitsMap = minPriceLimitsMap;
        }
    }

    protected class Brand {
        Integer id;
        String name;
        DataObject brandObject;

        public Brand(Integer id, String name, DataObject brandObject) {
            this.id = id;
            this.name = name;
            this.brandObject = brandObject;
        }
    }

    protected class Category {
        Long overId;
        String name;
        Long parentId;

        public Category(Long overId, String name, Long parentId) {
            this.overId = overId;
            this.name = name;
            this.parentId = parentId;
        }
    }

    protected class Item {
        String id;
        String caption;
        String idUOM;
        Boolean split;
        String idSkuGroup;
        Integer idLoyaBrand;

        public Item(String id, String caption, String idUOM, Boolean split, String idSkuGroup, Integer idLoyaBrand) {
            this.id = id;
            this.caption = caption;
            this.idUOM = idUOM;
            this.split = split;
            this.idSkuGroup = idSkuGroup;
            this.idLoyaBrand = idLoyaBrand;
        }
    }

    private class GoodGroup {
        Long id;
        String name;
        String description;

        public GoodGroup(Long id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
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

    protected class MinPriceLimit {
        Integer idDepartmentStore;
        BigDecimal minPrice;

        public MinPriceLimit(Integer idDepartmentStore, BigDecimal minPrice) {
            this.idDepartmentStore = idDepartmentStore;
            this.minPrice = minPrice;
        }
    }
}
