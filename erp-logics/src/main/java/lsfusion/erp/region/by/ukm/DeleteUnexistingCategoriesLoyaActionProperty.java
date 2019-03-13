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
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class DeleteUnexistingCategoriesLoyaActionProperty extends LoyaActionProperty {
    String failCaption = "Loya: Ошибка при удалении несуществующих категорий";

    public DeleteUnexistingCategoriesLoyaActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            settings = login(context, true);
            if (settings.error == null) {

                Set<Long> existingCategories = readExistingCategories(context);
                if ((deleteUnexistingCategories(context, existingCategories)))
                    context.delayUserInteraction(new MessageClientAction("Удаление несуществующих категорий завершено", "Loya"));

            } else context.delayUserInteraction(new MessageClientAction(settings.error, failCaption));
        } catch (Exception e) {
            ServerLoggers.importLogger.error(failCaption, e);
            context.delayUserInteraction(new MessageClientAction(e.getMessage(), failCaption));
        }
    }

    private Set<Long> readExistingCategories(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Set<Long> itemGroupSet = new HashSet<>();

        KeyExpr itemGroupExpr = new KeyExpr("ItemGroup");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "itemGroup", itemGroupExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("overId", findProperty("overId[ItemGroup]").getExpr(itemGroupExpr));
        query.and(findProperty("overId[ItemGroup]").getExpr(itemGroupExpr).getWhere());

        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> itemGroupResult = query.execute(context);

        for (ImMap<Object, Object> row : itemGroupResult.valueIt()) {
            Long overId = parseGroup((String) row.get("overId"));
            itemGroupSet.add(overId);
        }
        return itemGroupSet;
    }

    protected boolean deleteUnexistingCategories(ExecutionContext context, Set<Long> existingCategories) throws IOException, JSONException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {

        ServerLoggers.importLogger.info("Loya: deleting unexisting categories started");
        List<Long> categories = getLoyaCategories(context);
        boolean succeeded = true;
        for (Long category : categories) {
            if (!existingCategories.contains(category) && !deleteCategory(context, category)) {
                succeeded = false;
            }
        }
        return succeeded;
    }

    private List<Long> getLoyaCategories(ExecutionContext context) throws IOException, JSONException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        String requestURL = settings.url + "category";
        if (settings.logRequests) {
            ServerLoggers.importLogger.info(String.format("Log Request to URL %s", requestURL));
        }
        HttpGet getRequest = new HttpGet(requestURL);
        LoyaResponse response = executeRequestWithRelogin(context, getRequest);
        List<Long> categories = new ArrayList<>();
        if (response.succeeded) {
            JSONArray categoriesArray = new JSONArray(response.message);
            ServerLoggers.importLogger.info(String.format("Loya: found %s categories", categoriesArray.length()));
            for (int i = 0; i < categoriesArray.length(); i++) {
                JSONObject category = categoriesArray.getJSONObject(i);
                if(category.getString("state").equals("active")) {
                    categories.add(category.getLong("categoryId"));
                }
            }
        } else {
            context.delayUserInteraction(new MessageClientAction(response.message, "Loya: Get Categories Error"));
        }
        return categories;
    }

    private boolean deleteCategory(ExecutionContext context, Long categoryId) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        ServerLoggers.importLogger.info(String.format("Loya: deleting category %s", categoryId));
        String requestURL = settings.url + "category/" + settings.partnerId + "/" + categoryId;
        String requestBody = "[" + categoryId + "]";
        if (settings.logRequests) {
            ServerLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpDeleteWithBody request = new HttpDeleteWithBody(requestURL);
        request.setEntity(new StringEntity(requestBody));
        LoyaResponse response = executeRequestWithRelogin(context, request);
        if (!response.succeeded)
            context.delayUserInteraction(new MessageClientAction(response.message, "Loya: Delete Category Error"));
        return response.succeeded;
    }
}