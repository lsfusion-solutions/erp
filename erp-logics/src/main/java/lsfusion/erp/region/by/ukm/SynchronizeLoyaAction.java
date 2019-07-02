package lsfusion.erp.region.by.ukm;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.ERPLoggers;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
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

public class SynchronizeLoyaAction extends LoyaAction {
    String failCaption = "Loya: Ошибка при синхронизации";

    public SynchronizeLoyaAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public SynchronizeLoyaAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            boolean disableSynchronizeItems = findProperty("disableSynchronizeItemsLoya[]").read(context) != null;
            boolean deleteInactiveItemGroups = findProperty("deleteInactiveItemGroupsLoya[]").read(context) != null;
            boolean useBarcodeAsId = findProperty("useBarcodeAsIdLoya[]").read(context) != null;
            boolean useMinPrice = findProperty("useMinPrice[]").read(context) != null;
            Map<String, Integer> discountLimits = getDiscountLimits(context);

            boolean succeeded = true;

            settings = login(context, true);
            if (settings.error == null) {

                //synchronize brands
                if(!disableSynchronizeItems) {
                    List<Brand> brandsList = readBrands(context);
                    if(!uploadBrands(context, brandsList)) {
                        succeeded = false;
                    }
                }

                if(succeeded) {

                    SynchronizeData data = readItems(context, deleteInactiveItemGroups, useBarcodeAsId, useMinPrice);
                    List<Category> categoriesList = readCategories(context);

                    if ((disableSynchronizeItems || uploadCategories(context, categoriesList, discountLimits)) &&
                            (disableSynchronizeItems || uploadItems(context, data.itemsList, discountLimits, data.minPriceLimitsMap)) &&
                            uploadItemGroups(context, data.itemItemGroupsMap, data.itemGroupsMap, data.deleteItemGroupsList) &&
                            uploadItemItemGroups(context, data.itemItemGroupsMap))
                        context.delayUserInteraction(new MessageClientAction("Синхронизация успешно завершена", "Loya"));

                }
            } else
                context.delayUserInteraction(new MessageClientAction(settings.error, failCaption));
        } catch (Exception e) {
            ERPLoggers.importLogger.error(failCaption, e);
            context.delayUserInteraction(new MessageClientAction(e.getMessage(), failCaption));
        }
    }

    private List<Brand> readBrands(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<Brand> result = new ArrayList<>();

        KeyExpr brandExpr = new KeyExpr("Brand");
        ImRevMap<Object, KeyExpr> brandKeys = MapFact.singletonRev((Object) "brand", brandExpr);
        QueryBuilder<Object, Object> brandQuery = new QueryBuilder<>(brandKeys);

        String[] brandNames = new String[]{"idLoya", "name"};
        LP[] brandProperties = findProperties("idLoya[Brand]", "name[Brand]");
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
        LP[] itemGroupProperties = findProperties("overId[ItemGroup]", "name[ItemGroup]", "idParent[ItemGroup]");
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

        String[] loyaItemGroupNames = new String[]{"idLoyaItemGroup", "nameLoyaItemGroup", "descriptionLoyaItemGroup",
                "maxDiscountLoyaItemGroup", "maxAllowBonusLoyaItemGroup", "maxAwardBonusLoyaItemGroup"};
        LP[] loyaItemGroupProperties = findProperties("id[LoyaItemGroup]", "name[LoyaItemGroup]", "description[LoyaItemGroup]",
                "overMaxDiscountLoyaItemGroup[LoyaItemGroup]", "overMaxAllowBonusLoyaItemGroup[LoyaItemGroup]", "overMaxAwardBonusLoyaItemGroup[LoyaItemGroup]");
        for (int i = 0; i < loyaItemGroupProperties.length; i++) {
            query.addProperty(loyaItemGroupNames[i], loyaItemGroupProperties[i].getExpr(groupExpr));
        }

        query.addProperty("quantity", findProperty("quantity[Item, LoyaItemGroup]").getExpr(skuExpr, groupExpr));
        query.addProperty("idSku", findProperty("id[Sku]").getExpr(skuExpr));
        query.addProperty("barcode", findProperty("idBarcode[Sku]").getExpr(skuExpr));
        query.addProperty("captionItem", findProperty("nameAttribute[Item]").getExpr(skuExpr));
        query.addProperty("idUOMItem", findProperty("idUOM[Item]").getExpr(skuExpr));
        query.addProperty("shortNameUOM", findProperty("shortNameUOM[Item]").getExpr(skuExpr));
        query.addProperty("passScales", findProperty("passScales[Item]").getExpr(skuExpr));
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
            boolean forceNew = idLoyaItemGroup == null;
            idLoyaItemGroup = forceNew ? (Long) groupObject.getValue() : idLoyaItemGroup;
            String nameItemGroup = trim((String) valueEntry.get("nameLoyaItemGroup").getValue());
            BigDecimal quantity = (BigDecimal) valueEntry.get("quantity").getValue();
            String descriptionItemGroup = trim((String) valueEntry.get("descriptionLoyaItemGroup").getValue());
            Integer maxDiscountItemGroup = (Integer) valueEntry.get("maxDiscountLoyaItemGroup").getValue();
            Integer maxAllowBonusItemGroup = (Integer) valueEntry.get("maxAllowBonusLoyaItemGroup").getValue();
            Integer maxAwardBonusItemGroup = (Integer) valueEntry.get("maxAwardBonusLoyaItemGroup").getValue();
            String idSku = trim((String) valueEntry.get("idSku").getValue());
            String barcode = trim((String) valueEntry.get("barcode").getValue());
            String id = useBarcodeAsId ? barcode : idSku;
            String captionItem = trimToEmpty((String) valueEntry.get("captionItem").getValue());
            String idUOMItem = trim((String) valueEntry.get("idUOMItem").getValue());
            String shortNameUOM = trim((String) valueEntry.get("shortNameUOM").getValue());
            boolean passScales = valueEntry.get("passScales").getValue() != null;
            String idSkuGroup = trim((String) valueEntry.get("idSkuGroup").getValue());
            Integer idLoyaBrand = (Integer) valueEntry.get("idLoyaBrand").getValue();
            itemsList.add(new Item(id, captionItem, idUOMItem, isWeight(passScales, shortNameUOM), idSkuGroup, idLoyaBrand));
            itemGroupsMap.put(groupObject, new GoodGroup(idLoyaItemGroup, nameItemGroup, descriptionItemGroup,
                    getDiscountLimits(maxDiscountItemGroup, maxAllowBonusItemGroup, maxAwardBonusItemGroup), forceNew));
            List<GoodGroupLink> skuList = itemItemGroupsMap.get(idLoyaItemGroup);
            if (skuList == null)
                skuList = new ArrayList<>();
            skuList.add(new GoodGroupLink(id, quantity));
            itemItemGroupsMap.put(idLoyaItemGroup, skuList);
        }

        //get loya groups without items and not active for deletion
        KeyExpr emptyGroupExpr = new KeyExpr("loyaItemGroup");
        QueryBuilder<Object, Object> emptyQuery = new QueryBuilder<>(MapFact.singletonRev((Object) "loyaItemGroup", emptyGroupExpr));
        String[] emptyGroupNames = new String[]{"idLoyaItemGroup", "nameLoyaItemGroup", "descriptionLoyaItemGroup", "empty", "active", "maxDiscountLoyaItemGroup", "maxAllowBonusLoyaItemGroup", "maxAwardBonusLoyaItemGroup"};
        LP[] emptyGroupProperties = findProperties("id[LoyaItemGroup]", "name[LoyaItemGroup]", "description[LoyaItemGroup]", "empty[LoyaItemGroup]",
                "active[LoyaItemGroup]", "overMaxDiscountLoyaItemGroup[LoyaItemGroup]", "overMaxAllowBonusLoyaItemGroup[LoyaItemGroup]", "overMaxAwardBonusLoyaItemGroup[LoyaItemGroup]");
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
            boolean forceNew = idLoyaItemGroup == null;
            String nameItemGroup = trim((String) valueEntry.get("nameLoyaItemGroup").getValue());
            String descriptionItemGroup = trim((String) valueEntry.get("descriptionLoyaItemGroup").getValue());
            Integer maxDiscountItemGroup = (Integer) valueEntry.get("maxDiscountLoyaItemGroup").getValue();
            Integer maxAllowBonusItemGroup = (Integer) valueEntry.get("maxAllowBonusLoyaItemGroup").getValue();
            Integer maxAwardBonusItemGroup = (Integer) valueEntry.get("maxAwardBonusLoyaItemGroup").getValue();
            boolean empty = valueEntry.get("empty").getValue() != null;
            boolean active = valueEntry.get("active").getValue() != null;
            if(active && empty)
                itemGroupsMap.put(groupObject, new GoodGroup(forceNew ? (Long) groupObject.getValue() : idLoyaItemGroup, nameItemGroup, descriptionItemGroup,
                        getDiscountLimits(maxDiscountItemGroup, maxAllowBonusItemGroup, maxAwardBonusItemGroup), forceNew));
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

    private boolean uploadItemGroups(ExecutionContext context, Map<Long, List<GoodGroupLink>> itemItemGroupsMap, Map<DataObject, GoodGroup> itemGroupsMap,
                                     Map<DataObject, Long> deleteItemGroupsMap) throws JSONException, IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        boolean succeeded = true;
        for (Map.Entry<DataObject, GoodGroup> entry : itemGroupsMap.entrySet()) {
            DataObject itemGroupObject = entry.getKey();
            GoodGroup itemGroupData = entry.getValue();
            if (uploadItemGroup(context, itemItemGroupsMap, itemGroupData, itemGroupObject) != null)
                succeeded = false;
        }
        for(Map.Entry<DataObject, Long> deleteItemGroup : deleteItemGroupsMap.entrySet()) {
            Long idItemGroup = deleteItemGroup.getValue();
            if (existsItemGroup(context, idItemGroup)) {
                ERPLoggers.importLogger.info("Loya: deleting goodGroup " + idItemGroup);
                if (deleteItemGroup(context, deleteItemGroup.getKey(), idItemGroup) != null)
                    succeeded = false;
            }
        }
        return succeeded;
    }

    private String uploadItemGroup(ExecutionContext context, Map<Long, List<GoodGroupLink>> itemItemGroupsMap,
                                    GoodGroup goodGroup, DataObject itemGroupObject)
            throws JSONException, IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        ERPLoggers.importLogger.info("Loya: synchronizing goodGroup " + goodGroup.id + " started");
        JSONObject requestBody = new JSONObject();
        requestBody.put("partnerId", settings.partnerId);
        if(!goodGroup.forceNew) {
            requestBody.put("id", goodGroup.id);
        }
        requestBody.put("name", goodGroup.name == null ? "" : goodGroup.name);
        requestBody.put("description", goodGroup.description == null ? "" : goodGroup.description);
        requestBody.put("limits", goodGroup.discountLimits);

        if (!goodGroup.forceNew && existsItemGroup(context, goodGroup.id)) {
            ERPLoggers.importLogger.info("Loya: modifying goodGroup " + goodGroup.id);
            return modifyItemGroup(context, goodGroup.id, itemGroupObject, requestBody);
        } else {
            ERPLoggers.importLogger.info("Loya: creating goodGroup " + goodGroup.id);
            Object result = createItemGroup(context, itemGroupObject, settings.url, requestBody);
            if (result instanceof Long) {//id группы изменился
                List<GoodGroupLink> skuList = itemItemGroupsMap.get(goodGroup.id);
                if(skuList == null)
                    skuList = new ArrayList<>();
                itemItemGroupsMap.put((Long) result, skuList);
                itemItemGroupsMap.remove(goodGroup.id);
            }
            return result instanceof String ? (String) result : null;
        }
    }

    private boolean existsItemGroup(ExecutionContext context, Long idItemGroup) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        String requestURL = settings.url + "goodgroup/" + settings.partnerId + "/" + idItemGroup;
        if(settings.logRequests) {
            ERPLoggers.importLogger.info(String.format("Log Request to URL %s", requestURL));
        }
        HttpGet getRequest = new HttpGet(requestURL);
        return executeRequestWithRelogin(context, getRequest).succeeded;
    }

    private String modifyItemGroup(ExecutionContext context, Long idItemGroup, DataObject itemGroupObject, JSONObject requestBody)
            throws IOException, JSONException, SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        String result = null;
        String requestURL = settings.url + "goodgroup/" + settings.partnerId + "/" + idItemGroup;
        if(settings.logRequests) {
            ERPLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpPost postRequest = new HttpPost(requestURL);
        LoyaResponse response = executeRequestWithRelogin(context, postRequest, requestBody);
        boolean succeeded = response.succeeded;
        if (succeeded) {
            Long newIdItemGroup = new JSONObject(response.message).getLong("id");
            if(!idItemGroup.equals(newIdItemGroup)) {
                setIdLoyaItemGroup(context, itemGroupObject, newIdItemGroup);
            }
        } else {
            result = response.message;
            context.delayUserInteraction(new MessageClientAction(result, "Loya: Modify ItemGroup Error"));
        }
        return result;
    }

    private Object createItemGroup(ExecutionContext context, DataObject itemGroupObject, String url, JSONObject requestBody)
            throws IOException, JSONException, SQLException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
        String requestURL = url + "goodgroup";
        if(settings.logRequests) {
            ERPLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpPut putRequest = new HttpPut(requestURL);
        LoyaResponse response = executeRequestWithRelogin(context, putRequest, requestBody);
        if (response.succeeded)
            setIdLoyaItemGroup(context, itemGroupObject, new JSONObject(response.message).getLong("id"));
        else
            context.delayUserInteraction(new MessageClientAction(response.message, "Loya: Create ItemGroup Error"));
        return response.succeeded ? new JSONObject(response.message).getLong("id") : response.message;
    }

    private void setIdLoyaItemGroup(ExecutionContext context, DataObject itemGroupObject, Long id) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        try (ExecutionContext.NewSession newContext = context.newSession()) {
            findProperty("id[LoyaItemGroup]").change(id, newContext, itemGroupObject);
            newContext.apply();
        }
    }

    private String deleteItemGroup(ExecutionContext context, DataObject itemGroupObject, Long idItemGroup) throws IOException, SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, JSONException {
        String result = null;
        String requestURL = settings.url + "goodgroup/" + settings.partnerId + "/" + idItemGroup;
        String requestBody = "[" + idItemGroup + "]";
        if(settings.logRequests) {
            ERPLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpDeleteWithBody deleteRequest = new HttpDeleteWithBody(settings.url + "goodgroup/" + settings.partnerId + "/" + idItemGroup);
        deleteRequest.setEntity(new StringEntity(requestBody));
        LoyaResponse response = executeRequestWithRelogin(context, deleteRequest);
        boolean succeeded = response.succeeded;
        if (succeeded) {
            try (ExecutionContext.NewSession newContext = context.newSession()) {
                findProperty("deleted[LoyaItemGroup]").change(true, newContext, itemGroupObject);
                newContext.apply();
            }
        } else {
            result = response.message;
            context.delayUserInteraction(new MessageClientAction(result, "Loya: Delete ItemGroup Error"));
        }
        return result;
    }

    private boolean uploadBrands(ExecutionContext context, List<Brand> brandsList) throws IOException, JSONException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        boolean succeeded = true;
        for (Brand brand : brandsList) {
            if (uploadBrand(context, brand, true) != null) {
                succeeded = false;
            }
        }
        return succeeded;
    }

    protected String uploadBrand(ExecutionContext context, Brand brand, boolean messageErrors) throws JSONException, IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        ERPLoggers.importLogger.info("Loya: synchronizing brand " + brand.name + " started");

        JSONObject requestBody = new JSONObject();
        requestBody.put("id", brand.id);
        requestBody.put("name", brand.name);
        requestBody.put("description", brand.name);

        String result;
        if(brand.id != null && existsBrand(context, brand.id)) {
            ERPLoggers.importLogger.info("Loya: modifying brand " + brand.name);
            result = modifyBrand(context, brand.id, requestBody);
        } else {
            ERPLoggers.importLogger.info("Loya: creating brand " + brand.name);
            result = createBrand(context, brand.brandObject, settings.url, requestBody);
        }
        if(result != null && messageErrors) {
            context.delayUserInteraction(new MessageClientAction(result, "Loya: Synchronize Brand Error"));
        }
        return result;
    }

    private boolean existsBrand(ExecutionContext context, Integer idBrand) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        String requestURL = settings.url + "brand/" + idBrand;
        if(settings.logRequests) {
            ERPLoggers.importLogger.info(String.format("Log Request to URL %s", requestURL));
        }
        HttpGet getRequest = new HttpGet(requestURL);
        return executeRequestWithRelogin(context, getRequest).succeeded;
    }

    private String modifyBrand(ExecutionContext context, Integer idBrand, JSONObject requestBody) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        String requestURL = settings.url + "brand/" + idBrand;
        if(settings.logRequests) {
            ERPLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpPost postRequest = new HttpPost(requestURL);
        LoyaResponse response = executeRequestWithRelogin(context, postRequest, requestBody);
        return response.succeeded ? null : response.message;
    }

    private String createBrand(ExecutionContext context, DataObject brandObject, String url, JSONObject requestBody)
            throws IOException, JSONException, SQLException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
        String result = null;
        String requestURL = url + "brand";
        if(settings.logRequests) {
            ERPLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpPut putRequest = new HttpPut(requestURL);
        LoyaResponse response = executeRequestWithRelogin(context, putRequest, requestBody);
        if (response.succeeded) {
            JSONObject responseObject = new JSONObject(response.message);
            setIdLoyaBrand(context, brandObject, responseObject.getInt("id"));
        } else {
            result = response.message;
        }
        return result;
    }

    private void setIdLoyaBrand(ExecutionContext context, DataObject brandObject, Integer idLoyaBrand) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        try (ExecutionContext.NewSession newContext = context.newSession()) {
            findProperty("idLoya[Brand]").change(idLoyaBrand, newContext, brandObject);
            newContext.apply();
        }
    }

    private boolean uploadCategories(ExecutionContext context, List<Category> categoriesList, Map<String, Integer> discountLimits) throws IOException, JSONException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        boolean succeeded = true;
        for (Category category : categoriesList) {
            if (uploadCategory(context, category, discountLimits, true) != null) {
                succeeded = false;
            }
        }
        return succeeded;
    }

    protected String uploadCategory(ExecutionContext context, Category category, Map<String, Integer> discountLimits, boolean messageErrors) throws JSONException, IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {

        ERPLoggers.importLogger.info("Loya: synchronizing category " + category.overId + " started");
        JSONObject requestBody = new JSONObject();
        requestBody.put("partnerId", settings.partnerId);
        requestBody.put("categoryId", category.overId);
        requestBody.put("parentId", category.parentId);
        requestBody.put("name", category.name);
        requestBody.put("limits", discountLimits);
        requestBody.put("state", "active");

        String result;
        if (existsCategory(context, category.overId)) {
            ERPLoggers.importLogger.info("Loya: modifying category " + category.overId);
            result = modifyCategory(context, category.overId, requestBody);
        } else {
            ERPLoggers.importLogger.info("Loya: creating category " + category.overId);
            result = createCategory(context, settings.url, requestBody);
        }
        if(result != null && messageErrors) {
            context.delayUserInteraction(new MessageClientAction(result, "Loya: Synchronize Category Error"));
        }
        return result;
    }

    private boolean existsCategory(ExecutionContext context, Long idCategory) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        String requestURL = settings.url + "category/" + settings.partnerId + "/" + idCategory;
        if(settings.logRequests) {
            ERPLoggers.importLogger.info(String.format("Log Request to URL %s", requestURL));
        }
        HttpGet getRequest = new HttpGet(requestURL);
        return executeRequestWithRelogin(context, getRequest).succeeded;
    }

    private String modifyCategory(ExecutionContext context, Long categoryId, JSONObject requestBody) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        String requestURL = settings.url + "category/" + settings.partnerId + "/" + categoryId;
        if(settings.logRequests) {
            ERPLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpPost postRequest = new HttpPost(requestURL);
        LoyaResponse response = executeRequestWithRelogin(context, postRequest, requestBody);
        return response.succeeded ? null : response.message;
    }

    private String createCategory(ExecutionContext context, String url, JSONObject requestBody) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        String requestURL = url + "category";
        if(settings.logRequests) {
            ERPLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpPut putRequest = new HttpPut(url + "category");
        LoyaResponse response = executeRequestWithRelogin(context, putRequest, requestBody);
        return response.succeeded ? null : response.message;
    }

    private boolean uploadItems(ExecutionContext context, List<Item> itemsList, Map<String, Integer> discountLimits, Map<String, List<MinPriceLimit>> minPriceLimitsMap) throws IOException, JSONException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        boolean succeeded = true;
        for (Item item : itemsList) {
            List<MinPriceLimit> minPriceLimits = minPriceLimitsMap != null ? minPriceLimitsMap.get(item.id) : null;
            if (uploadItem(context, item, discountLimits, minPriceLimits, true) != null)
                succeeded = false;
        }
        return succeeded;
    }

    protected String uploadItem(ExecutionContext context, Item item, Map<String, Integer> discountLimits, List<MinPriceLimit> minPriceLimits, boolean messageErrors) throws JSONException, IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        ERPLoggers.importLogger.info("Loya: synchronizing good " + item.id + " started");
        JSONObject requestBody = new JSONObject();
        requestBody.put("partnerId", settings.partnerId);
        requestBody.put("sku", item.id);
        requestBody.put("categoryId", parseGroup(item.idSkuGroup));
        requestBody.put("brandId", item.idLoyaBrand);
        requestBody.put("name", item.caption);
        requestBody.put("measurement", item.idUOM);
        requestBody.put("margin", 0);
        requestBody.put("dimension", item.isWeight ? "weight" : "piece");
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
        if (existsItem(context, item.id)) {
            ERPLoggers.importLogger.info("Loya: modifying good " + item.id);
            result = modifyItem(context, item.id, requestBody);
        } else {
            ERPLoggers.importLogger.info("Loya: creating good " + item.id);
            result = createItem(context, settings.url, requestBody);
        }
        if(result != null && messageErrors) {
            context.delayUserInteraction(new MessageClientAction(result, "Loya: Synchronize Item Error"));
        }
        return result;
    }

    private boolean existsItem(ExecutionContext context, String idItem) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        String requestURL = settings.url + "good/" + settings.partnerId + "/" + idItem;
        if(settings.logRequests) {
            ERPLoggers.importLogger.info(String.format("Log Request to URL %s", requestURL));
        }
        HttpGet getRequest = new HttpGet(requestURL);
        return executeRequestWithRelogin(context, getRequest).succeeded;
    }

    private String modifyItem(ExecutionContext context, String idItem, JSONObject requestBody) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        String requestURL = settings.url + "good/" + settings.partnerId + "/" + idItem;
        if(settings.logRequests) {
            ERPLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpPost postRequest = new HttpPost(requestURL);
        LoyaResponse response = executeRequestWithRelogin(context, postRequest, requestBody);
        return response.succeeded ? null : response.message;
    }

    private String createItem(ExecutionContext context, String url, JSONObject requestBody) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        String requestURL = url + "good";
        if(settings.logRequests) {
            ERPLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpPut putRequest = new HttpPut(requestURL);
        LoyaResponse response = executeRequestWithRelogin(context, putRequest, requestBody);
        return response.succeeded ? null : response.message;
    }

    private boolean uploadItemItemGroups(ExecutionContext context, Map<Long, List<GoodGroupLink>> itemItemGroupsMap) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        boolean succeeded = true;
        ERPLoggers.importLogger.info("Loya: synchronizing goodGroupLinks");
        for (Map.Entry<Long, List<GoodGroupLink>> entry : itemItemGroupsMap.entrySet()) {
            Long idItemGroup = entry.getKey();
            List<GoodGroupLink> itemsList = entry.getValue();

            String deleteList = "";
            int deleteCount = 0;
            LoyaResponse getResponse = executeRequestWithRelogin(context, new HttpGet(settings.url + "goodgrouplink/" + settings.partnerId + "/" + idItemGroup));
            try {
                JSONArray itemsArray = new JSONArray(getResponse.message);
                ERPLoggers.importLogger.info(String.format("Loya: synchronizing goodGroupLinks. Group %s: %s items before synchronization", idItemGroup, itemsArray.length()));
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
                ERPLoggers.importLogger.error(String.format("Loya: synchronizing goodGroupLinks incorrect response %s, isSucceeded: %s", getResponse.message, getResponse.succeeded));
            }
            if (!deleteList.isEmpty()) {
                //удаляем более не существующие
                String requestURL = settings.url + "goodgrouplink/" + settings.partnerId + "/" + idItemGroup + "/deleteList";
                String requestBody = "[" + deleteList + "]";
                if(settings.logRequests) {
                    ERPLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
                }
                HttpPost postRequest = new HttpPost(requestURL);
                LoyaResponse response = executeRequestWithRelogin(context, postRequest, requestBody);
                if (!response.succeeded) {
                    String error = String.format("Loya: delete GoodGroupLinks (%s) error", deleteList);
                    ERPLoggers.importLogger.error(error + ": " + response.message);
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
                    if (createGoodGroupLink(context, idItemGroup, goodGroupLink) != null)
                        succeeded = false;
                }
            }
            ERPLoggers.importLogger.info(String.format("Loya: synchronizing goodGroupLinks. Group %s: deleted %s items, added %s items", idItemGroup, deleteCount, itemsList.size()));
        }
        return succeeded;
    }

    private String createGoodGroupLink(ExecutionContext context, Long idItemGroup, GoodGroupLink goodGroupLink) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        String result = null;
        String requestURL = settings.url + "goodgrouplink/" + settings.partnerId + "/" + idItemGroup + "/upload";
        HttpPost postRequest = new HttpPost(requestURL);
        String query = goodGroupLink.quantity == null ? String.format("[{\"sku\":\"%s\"}]", goodGroupLink.sku) :
                String.format("[{\"sku\":\"%s\",\"quantity\":%s}]", goodGroupLink.sku, goodGroupLink.quantity);
        if(settings.logRequests) {
            ERPLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, query));
        }
        LoyaResponse response = executeRequestWithRelogin(context, postRequest, query);
        if (!response.succeeded) {
            result = response.message;
            String error = String.format("Loya: create GoodGroupLink Error: group %s, item %s, %s", idItemGroup, goodGroupLink.sku, result);
            ERPLoggers.importLogger.error(error);
            context.delayUserInteraction(new MessageClientAction(error, "Loya: create GoodGroupLink Error"));
        }
        return result;
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

    protected boolean isWeight(boolean passScales, String shortNameUOM) {
        return passScales && (shortNameUOM == null || !shortNameUOM.toUpperCase().startsWith("ШТ")); //as in ukm4mysqlhandler
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
        boolean isWeight;
        String idSkuGroup;
        Integer idLoyaBrand;

        public Item(String id, String caption, String idUOM, boolean isWeight, String idSkuGroup, Integer idLoyaBrand) {
            this.id = id;
            this.caption = caption;
            this.idUOM = idUOM;
            this.isWeight = isWeight;
            this.idSkuGroup = idSkuGroup;
            this.idLoyaBrand = idLoyaBrand;
        }
    }

    private class GoodGroup {
        Long id;
        String name;
        String description;
        Map<String, Integer> discountLimits;
        boolean forceNew;


        public GoodGroup(Long id, String name, String description, Map<String, Integer> discountLimits, boolean forceNew) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.discountLimits = discountLimits;
            this.forceNew = forceNew;
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
