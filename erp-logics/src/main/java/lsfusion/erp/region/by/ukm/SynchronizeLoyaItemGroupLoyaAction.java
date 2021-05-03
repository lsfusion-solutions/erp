package lsfusion.erp.region.by.ukm;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.ERPLoggers;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
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

public class SynchronizeLoyaItemGroupLoyaAction extends LoyaAction {
    private final ClassPropertyInterface itemGroupLoyaInterface;
    String failCaption = "Loya: Ошибка при синхронизации";

    public SynchronizeLoyaItemGroupLoyaAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        itemGroupLoyaInterface = i.next();
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        try {

            DataObject itemGroupLoyaObject = context.getDataKeyValue(itemGroupLoyaInterface);

            boolean deleteInactiveItemGroups = findProperty("deleteInactiveItemGroupsLoya[]").read(context) != null;
            boolean useBarcodeAsId = findProperty("useBarcodeAsIdLoya[]").read(context) != null;

            settings = login(context, true);
            if (settings.error == null) {

                SynchronizeData data = readItems(context, deleteInactiveItemGroups, useBarcodeAsId, itemGroupLoyaObject);
                if (uploadItemGroup(context, data.goodGroup, data.deleteItemGroup, itemGroupLoyaObject) && uploadItemItemGroups(context, data.goodGroup.id, data.itemsList))
                    context.delayUserInteraction(new MessageClientAction("Синхронизация успешно завершена", "Loya"));

            } else
                showError(context, settings.error, failCaption, settings.error);
        } catch (Exception e) {
            showError(context, e.getMessage(), failCaption, failCaption, e);
        }
    }

    private SynchronizeData readItems(ExecutionContext<ClassPropertyInterface> context, boolean deleteInactiveItemGroups, boolean useBarcodeAsId, DataObject loyaItemGroupObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<GoodGroupLink> itemsList = new ArrayList<>();
        boolean deleteItemGroup = false;

        Long idLoyaItemGroup = (Long) findProperty("id[LoyaItemGroup]").read(context, loyaItemGroupObject);
        boolean forceNew = idLoyaItemGroup == null;
        idLoyaItemGroup = forceNew ? (Long) loyaItemGroupObject.getValue() : idLoyaItemGroup;
        String nameItemGroup = (String) findProperty("name[LoyaItemGroup]").read(context, loyaItemGroupObject);
        String descriptionItemGroup = (String) findProperty("description[LoyaItemGroup]").read(context, loyaItemGroupObject);
        Integer maxDiscountLoyaItemGroup = (Integer) findProperty("overMaxDiscountLoyaItemGroup[LoyaItemGroup]").read(context, loyaItemGroupObject);
        Integer maxAllowBonusLoyaItemGroup = (Integer) findProperty("overMaxAllowBonusLoyaItemGroup[LoyaItemGroup]").read(context, loyaItemGroupObject);
        Integer maxAwardBonusLoyaItemGroup = (Integer) findProperty("overMaxAwardBonusLoyaItemGroup[LoyaItemGroup]").read(context, loyaItemGroupObject);

        boolean empty = findProperty("empty[LoyaItemGroup]").read(context, loyaItemGroupObject) != null;
        boolean active = findProperty("active[LoyaItemGroup]").read(context, loyaItemGroupObject) != null;

        if(active && !empty) {

            KeyExpr skuExpr = new KeyExpr("sku");

            ImRevMap<String, KeyExpr> keys = MapFact.singletonRev("sku", skuExpr);
            QueryBuilder<String, Object> query = new QueryBuilder<>(keys);

            query.addProperty("quantity", findProperty("quantity[Item, LoyaItemGroup]").getExpr(skuExpr, loyaItemGroupObject.getExpr()));
            query.addProperty("idSku", findProperty("id[Sku]").getExpr(skuExpr));
            query.addProperty("barcode", findProperty("idBarcode[Sku]").getExpr(skuExpr));
            query.and(findProperty("active[LoyaItemGroup]").getExpr(loyaItemGroupObject.getExpr()).getWhere());
            query.and(findProperty("id[Sku]").getExpr(skuExpr).getWhere());
            query.and(findProperty("in[Item,LoyaItemGroup]").getExpr(skuExpr, loyaItemGroupObject.getExpr()).getWhere());

            ImOrderMap<ImMap<String, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(context);
            for (int i = 0; i < result.size(); i++) {
                ImMap<Object, ObjectValue> valueEntry = result.getValue(i);

                BigDecimal quantity = (BigDecimal) valueEntry.get("quantity").getValue();
                String idSku = trim((String) valueEntry.get("idSku").getValue());
                String barcode = trim((String) valueEntry.get("barcode").getValue());
                String id = useBarcodeAsId ? barcode : idSku;
                itemsList.add(new GoodGroupLink(id, quantity));
            }

        } else {
            //get loya group without items and not active for deletion
            deleteItemGroup = !active && deleteInactiveItemGroups;
        }

        GoodGroup goodGroup = active ? new GoodGroup(idLoyaItemGroup, nameItemGroup, descriptionItemGroup, getDiscountLimits(maxDiscountLoyaItemGroup, maxAllowBonusLoyaItemGroup, maxAwardBonusLoyaItemGroup), forceNew) : null;
        return new SynchronizeData(goodGroup, itemsList, deleteItemGroup);
    }

    private boolean uploadItemGroup(ExecutionContext<ClassPropertyInterface> context, GoodGroup goodGroup, boolean deleteItemGroup, DataObject itemGroupLoyaObject)
            throws JSONException, IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        boolean succeeded = true;
        if (deleteItemGroup) {
            if (existsItemGroup(context, goodGroup.id)) {
                ERPLoggers.importLogger.info("Loya: deleting goodGroup " + goodGroup.id);
                succeeded = deleteItemGroup(context, itemGroupLoyaObject, goodGroup.id) == null;
            }
        } else {
            succeeded = uploadItemGroup(context, goodGroup, itemGroupLoyaObject) == null;
        }
        return succeeded;
    }

    private String uploadItemGroup(ExecutionContext<ClassPropertyInterface> context, GoodGroup goodGroup, DataObject itemGroupObject)
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
            return result instanceof String ? (String) result : null;
        }
    }

    private boolean existsItemGroup(ExecutionContext<ClassPropertyInterface> context, Long idItemGroup) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        String requestURL = settings.url + "goodgroup/" + settings.partnerId + "/" + idItemGroup;
        if(settings.logRequests) {
            ERPLoggers.importLogger.info(String.format("Log Request to URL %s", requestURL));
        }
        HttpGet getRequest = new HttpGet(requestURL);
        return executeRequestWithRelogin(context, getRequest).succeeded;
    }

    private String modifyItemGroup(ExecutionContext<ClassPropertyInterface> context, Long idItemGroup, DataObject itemGroupObject, JSONObject requestBody)
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
            showError(context, result, "Loya: Modify ItemGroup Error", String.format("Modify ItemGroup Error: %s", result));
        }
        return result;
    }

    private Object createItemGroup(ExecutionContext<ClassPropertyInterface> context, DataObject itemGroupObject, String url, JSONObject requestBody)
            throws IOException, JSONException, SQLException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
        String requestURL = url + "goodgroup";
        if(settings.logRequests) {
            ERPLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpPut putRequest = new HttpPut(requestURL);
        LoyaResponse response = executeRequestWithRelogin(context, putRequest, requestBody);
        if (response.succeeded)
            setIdLoyaItemGroup(context, itemGroupObject, new JSONObject(response.message).getLong("id"));
        else {
            showError(context, response.message, "Loya: Create ItemGroup Error", String.format("Loya: Create ItemGroup Error: %s", response.message));
        }
        return response.succeeded ? new JSONObject(response.message).getLong("id") : response.message;
    }

    private void setIdLoyaItemGroup(ExecutionContext<ClassPropertyInterface> context, DataObject itemGroupObject, Long id) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        try (ExecutionContext.NewSession newContext = context.newSession()) {
            findProperty("id[LoyaItemGroup]").change(id, newContext, itemGroupObject);
            newContext.apply();
        }
    }

    private String deleteItemGroup(ExecutionContext<ClassPropertyInterface> context, DataObject itemGroupObject, Long idItemGroup) throws IOException, SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, JSONException {
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
            showError(context, result, "Loya: Delete ItemGroup Error", String.format("Loya: Create ItemGroup Error: %s", result));
        }
        return result;
    }

    private boolean uploadItemItemGroups(ExecutionContext<ClassPropertyInterface> context, Long idItemGroup, List<GoodGroupLink> itemsList) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        boolean succeeded = true;
        ERPLoggers.importLogger.info("Loya: synchronizing goodGroupLinks");
        String deleteList = "";
        int deleteCount = 0;
        LoyaResponse getResponse = executeRequestWithRelogin(context, new HttpGet(settings.url + "goodgrouplink/" + settings.partnerId + "/" + idItemGroup+ "?pager.limit=100000"));
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
                showError(context, error, failCaption, error + ": " + response.message);
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
        return succeeded;
    }

    private String createGoodGroupLink(ExecutionContext<ClassPropertyInterface> context, Long idItemGroup, GoodGroupLink goodGroupLink) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
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
            showError(context, error, "Loya: create GoodGroupLink Error", error);
        }
        return result;
    }

    protected Map<String, Integer> getDiscountLimits(Integer maxDiscount, Integer maxAllowBonus, Integer maxAwardBonus) {
        Map<String, Integer> limitsMap = new HashMap<>();
        limitsMap.put("maxDiscount", maxDiscount);
        limitsMap.put("maxAllowBonus", maxAllowBonus);
        limitsMap.put("maxAwardBonus", maxAwardBonus);
        return limitsMap;
    }

    private void showError(ExecutionContext<ClassPropertyInterface> context, String message, String caption, String log) {
        showError(context, message, caption, log, null);
    }

    private void showError(ExecutionContext<ClassPropertyInterface> context, String message, String caption, String log, Exception e) {
        if(e != null) {
            ERPLoggers.importLogger.error(log, e);
        } else {
            ERPLoggers.importLogger.error(log);
        }
        context.delayUserInteraction(new MessageClientAction(message, caption));
    }

    private class SynchronizeData {
        public GoodGroup goodGroup;
        public List<GoodGroupLink> itemsList;
        public boolean deleteItemGroup;

        public SynchronizeData(GoodGroup goodGroup, List<GoodGroupLink> itemsList, boolean deleteItemGroup) {
            this.goodGroup = goodGroup;
            this.itemsList = itemsList;
            this.deleteItemGroup = deleteItemGroup;
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

}
