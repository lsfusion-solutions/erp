package lsfusion.erp.region.by.ukm;

import com.google.common.base.Throwables;
import lsfusion.base.BaseUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.ERPLoggers;
import lsfusion.interop.action.MessageClientAction;
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

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang.StringUtils.trim;

public class SynchronizeItemLoyaAction extends SynchronizeLoyaAction {
    private final ClassPropertyInterface itemInterface;

    public SynchronizeItemLoyaAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        itemInterface = i.next();
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            boolean useBarcodeAsId = findProperty("useBarcodeAsIdLoya[]").read(context) != null;
            boolean useMinPrice = findProperty("useMinPrice[]").read(context) != null;

            DataObject itemObject = context.getDataKeyValue(itemInterface);

            settings = login(context, false);
            if (settings.error == null) {

                boolean nearestForbidPromotion = findProperty("nearestForbidPromotion[Item]").read(context, itemObject) != null;
                Integer maxDiscount = (Integer) (nearestForbidPromotion ? 0 : findProperty("maxDiscountLoya[]").read(context));
                Integer maxAllowBonus = (Integer) (nearestForbidPromotion ? 0 : findProperty("maxAllowBonusLoya[]").read(context));
                Integer maxAwardBonus = (Integer) findProperty("maxAwardBonusLoya[]").read(context);

                Map<String, Integer> discountLimits = getDiscountLimits(maxDiscount, maxAllowBonus, maxAwardBonus);
                String idSku = (String) findProperty("id[Item]").read(context, itemObject);
                String barcode = (String) findProperty("idBarcode[Item]").read(context, itemObject);
                String id = useBarcodeAsId ? barcode : idSku;
                String caption = BaseUtils.trimToEmpty((String) findProperty("nameAttribute[Item]").read(context, itemObject));
                if(caption.length() > 254)
                    caption = caption.substring(0, 254);
                String idUOM = (String) findProperty("idUOM[Item]").read(context, itemObject);
                String shortNameUOM = (String) findProperty("shortNameUOM[Item]").read(context, itemObject);
                boolean passScales = findProperty("passScales[Item]").read(context, itemObject) != null;
                String idSkuGroup = trim((String) findProperty("overIdSkuGroup[Item]").read(context, itemObject));
                Integer idLoyaBrand = (Integer) findProperty("idLoyaBrand[Item]").read(context, itemObject);
                Item item = new Item(id, caption, idUOM, isWeight(passScales, shortNameUOM), idSkuGroup, idLoyaBrand);
                List<MinPriceLimit> minPriceLimits = useMinPrice ? readMinPriceLimits(context, itemObject) : null;
                String result = uploadItem(context, item, discountLimits, minPriceLimits, false);
                if(authenticationFailed(result)) {
                    settings = login(context, true);
                    if(settings.error == null) {
                        result = uploadItem(context, item, discountLimits, minPriceLimits, true);
                    }
                }
                findProperty("synchronizeItemResult[]").change(result, context);

            } else {
                findProperty("synchronizeItemResult[]").change(settings.error, context);
                context.delayUserInteraction(new MessageClientAction(settings.error, failCaption));
            }
        } catch (Exception e) {
            ERPLoggers.importLogger.error(failCaption, e);
            try {
                findProperty("synchronizeItemResult[]").change(String.valueOf(e), context);
            } catch (ScriptingErrorLog.SemanticErrorException e1) {
                throw Throwables.propagate(e1);
            } context.delayUserInteraction(new MessageClientAction(e.getMessage(), failCaption));
        }
    }

    private List<MinPriceLimit> readMinPriceLimits(ExecutionContext<ClassPropertyInterface> context, DataObject itemObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<MinPriceLimit> result = new ArrayList<>();
        KeyExpr departmentStoreExpr = new KeyExpr("departmentStore");
        ImRevMap<String, KeyExpr> keys = MapFact.singletonRev("departmentStore", departmentStoreExpr);
        QueryBuilder<String, Object> query = new QueryBuilder<>(keys);
        query.addProperty("idLoyaDepartmentStore", findProperty("idLoya[DepartmentStore]").getExpr(departmentStoreExpr));
        query.addProperty("loyaMinPrice", findProperty("loyaMinPrice[Item, DepartmentStore]").getExpr(context.getModifier(), itemObject.getExpr(), departmentStoreExpr));
        query.and(findProperty("inLoya[DepartmentStore]").getExpr(departmentStoreExpr).getWhere());
        query.and(findProperty("idLoya[DepartmentStore]").getExpr(departmentStoreExpr).getWhere());
        query.and(findProperty("loyaMinPrice[Item, DepartmentStore]").getExpr(context.getModifier(), itemObject.getExpr(), departmentStoreExpr).getWhere());
        ImOrderMap<ImMap<String, DataObject>, ImMap<Object, ObjectValue>> queryResult = query.executeClasses(context);
        for (int i = 0; i < queryResult.size(); i++) {
            ImMap<Object, ObjectValue> valueEntry = queryResult.getValue(i);
            Integer idLoyaDepartmentStore = (Integer) valueEntry.get("idLoyaDepartmentStore").getValue();
            BigDecimal minPrice = (BigDecimal) valueEntry.get("loyaMinPrice").getValue();
            result.add(new MinPriceLimit(idLoyaDepartmentStore, minPrice));
        }
        return result;
    }
}