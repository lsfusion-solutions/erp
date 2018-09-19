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
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.commons.lang.StringUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static lsfusion.base.BaseUtils.trimToEmpty;
import static org.apache.commons.lang.StringUtils.trim;

public class SynchronizeItemLoyaActionProperty extends SynchronizeLoyaActionProperty {
    private final ClassPropertyInterface itemInterface;

    public SynchronizeItemLoyaActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        itemInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            boolean logRequests = findProperty("logRequestsLoya[]").read(context) != null;
            boolean useBarcodeAsId = findProperty("useBarcodeAsIdLoya[]").read(context) != null;

            DataObject itemObject = context.getDataKeyValue(itemInterface);

            SettingsLoya settings = login(context);
            if (settings.error == null) {

                Map<String, Integer> discountLimits = getDiscountLimits(context);
                List<Category> categoriesList = readCategories(context, itemObject);

                if (uploadCategories(context, settings, categoriesList, discountLimits, logRequests)) {
                    String idSku = (String) findProperty("id[Item]").read(context, itemObject);
                    String barcode = (String) findProperty("idBarcode[Item]").read(context, itemObject);
                    String id = useBarcodeAsId ? barcode : idSku;
                    String caption = StringUtils.trimToEmpty((String) findProperty("caption[Item]").read(context, itemObject));
                    String idUOM = (String) findProperty("idUOM[Item]").read(context, itemObject);
                    boolean split = findProperty("split[Item]").read(context, itemObject) != null;
                    String idSkuGroup = trim((String) findProperty("idSkuGroup[Item]").read(context, itemObject));
                    Integer idLoyaBrand = (Integer) findProperty("idLoyaBrand[Item]").read(context, itemObject);
                    Item item = new Item(id, caption, idUOM, split, idSkuGroup, idLoyaBrand);
                    uploadItem(context, settings, item, discountLimits, logRequests);
                }

            } else context.delayUserInteraction(new MessageClientAction(settings.error, failCaption));
        } catch (Exception e) {
            ServerLoggers.importLogger.error(failCaption, e);
            context.delayUserInteraction(new MessageClientAction(e.getMessage(), failCaption));
        }
    }

    private List<Category> readCategories(ExecutionContext context, DataObject itemObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<Category> result = new ArrayList<>();

        KeyExpr itemGroupExpr = new KeyExpr("ItemGroup");
        ImRevMap<Object, KeyExpr> itemGroupKeys = MapFact.singletonRev((Object) "itemGroup", itemGroupExpr);
        QueryBuilder<Object, Object> itemGroupQuery = new QueryBuilder<>(itemGroupKeys);

        String[] itemGroupNames = new String[]{"overIdItemGroup", "nameItemGroup", "idParentItemGroup"};
        LCP[] itemGroupProperties = findProperties("overId[ItemGroup]", "name[ItemGroup]", "idParent[ItemGroup]");
        for (int i = 0; i < itemGroupProperties.length; i++) {
            itemGroupQuery.addProperty(itemGroupNames[i], itemGroupProperties[i].getExpr(itemGroupExpr));
        }
        itemGroupQuery.and(findProperty("isParent[SkuGroup, Sku]").getExpr(itemGroupExpr, itemObject.getExpr()).getWhere());

        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> itemGroupResult = itemGroupQuery.execute(context);

        for (ImMap<Object, Object> row : itemGroupResult.valueIt()) {
            Long overId = parseGroup((String) row.get("overIdItemGroup"));
            String name = trimToEmpty((String) row.get("nameItemGroup"));
            Long idParent = parseGroup((String) row.get("idParentItemGroup"));
            result.add(new Category(overId, name, overId == 0 ? null : idParent));
        }
        return result;
    }
}