package lsfusion.erp.region.by.ukm;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.ERPLoggers;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
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

public class DeleteUnexistingCategoriesLoyaAction extends LoyaAction {
    String failCaption = "Loya: Ошибка при удалении несуществующих категорий";

    public DeleteUnexistingCategoriesLoyaAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        try {
            settings = login(context, true);
            if (settings.error == null) {

                Set<Long> existingCategories = readExistingCategories(context);
                if ((deleteUnexistingCategories(context, existingCategories)))
                    context.delayUserInteraction(new MessageClientAction("Удаление несуществующих категорий завершено", "Loya"));

            } else context.delayUserInteraction(new MessageClientAction(settings.error, failCaption));
        } catch (Exception e) {
            ERPLoggers.importLogger.error(failCaption, e);
            context.delayUserInteraction(new MessageClientAction(e.getMessage(), failCaption));
        }
    }

    private Set<Long> readExistingCategories(ExecutionContext<ClassPropertyInterface> context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Set<Long> itemGroupSet = new HashSet<>();

        KeyExpr itemGroupExpr = new KeyExpr("ItemGroup");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev("itemGroup", itemGroupExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("overId", findProperty("overIdLoya[ItemGroup]").getExpr(itemGroupExpr));
        query.and(findProperty("overIdLoya[ItemGroup]").getExpr(itemGroupExpr).getWhere());

        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> itemGroupResult = query.execute(context);

        for (ImMap<Object, Object> row : itemGroupResult.valueIt()) {
            Long overId = parseGroup((String) row.get("overId"));
            itemGroupSet.add(overId);
        }
        return itemGroupSet;
    }

    protected boolean deleteUnexistingCategories(ExecutionContext<ClassPropertyInterface> context, Set<Long> existingCategories) throws IOException, JSONException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {

        ERPLoggers.importLogger.info("Loya: deleting unexisting categories started");
        List<Long> categories = getLoyaCategories(context);
        boolean succeeded = true;
        for (Long category : categories) {
            if (!existingCategories.contains(category) && !deleteCategory(context, category)) {
                succeeded = false;
            }
        }
        return succeeded;
    }

    private List<Long> getLoyaCategories(ExecutionContext<ClassPropertyInterface> context) throws IOException, JSONException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        String requestURL = settings.url + "category";
        if (settings.logRequests) {
            ERPLoggers.importLogger.info(String.format("Log Request to URL %s", requestURL));
        }
        HttpGet getRequest = new HttpGet(requestURL);
        LoyaResponse response = executeRequestWithRelogin(context, getRequest);
        List<Long> categories = new ArrayList<>();
        if (response.succeeded) {
            JSONArray categoriesArray = new JSONArray(response.message);
            ERPLoggers.importLogger.info(String.format("Loya: found %s categories", categoriesArray.length()));
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

    private boolean deleteCategory(ExecutionContext<ClassPropertyInterface> context, Long categoryId) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        ERPLoggers.importLogger.info(String.format("Loya: deleting category %s", categoryId));
        String requestURL = settings.url + "category/" + settings.partnerId + "/" + categoryId;
        String requestBody = "[" + categoryId + "]";
        if (settings.logRequests) {
            ERPLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpDeleteWithBody request = new HttpDeleteWithBody(requestURL);
        request.setEntity(new StringEntity(requestBody));
        LoyaResponse response = executeRequestWithRelogin(context, request);
        if (!response.succeeded)
            context.delayUserInteraction(new MessageClientAction(response.message, "Loya: Delete Category Error"));
        return response.succeeded;
    }
}