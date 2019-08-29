package equ.srv;

import com.google.common.base.Throwables;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.server.data.OperationOwner;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.Join;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.SQLSession;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.data.where.Where;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.action.session.change.PropertyChange;
import lsfusion.server.logics.action.session.table.SingleKeyTableUsage;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.data.integral.LongClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class EncodeIdsAction extends InternalAction {

    public EncodeIdsAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            ImMap<Long, String> ids = readIds(context);
            writeIds(context, ids);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }

    private ImMap<Long, String> readIds(ExecutionContext<ClassPropertyInterface> context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        boolean timeInId = findProperty("timeInId[]").read(context) != null;

        IdEncoder encoder = timeInId ? new IdEncoder(5) : new IdEncoder();

        Map<Long, String> ids = new LinkedHashMap<>();
        KeyExpr expr = new KeyExpr("long");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev("long", expr);

        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("id", findProperty("encodingId[LONG]").getExpr(context.getModifier(), expr));
        query.and(findProperty("encodingId[LONG]").getExpr(context.getModifier(), expr).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context);
        long i = 0;
        for (ImMap<Object, Object> entry : result.values()) {
            ids.put(i++, encoder.encode((String) entry.get("id")));
        }
        return MapFact.fromJavaMap(ids);
    }

    private void writeIds(ExecutionContext context, ImMap<Long, String> processes) throws SQLException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {

        ImOrderSet<LP> props = SetFact.fromJavaOrderSet(Collections.singletonList(findProperty("encodingId[LONG]")));

        ImMap<ImMap<String, DataObject>, ImMap<LP, ObjectValue>> rows;

        rows = processes.mapKeyValues(value -> MapFact.singleton("key", new DataObject(value, LongClass.instance)), (key, value) -> props.getSet().mapValues(getMapValueGetter(value)));

        SingleKeyTableUsage<LP> importTable = new SingleKeyTableUsage<>("updpm:wr", LongClass.instance, props, key -> ((LP<?>)key).property.getType());
        OperationOwner owner = context.getSession().getOwner();
        SQLSession sql = context.getSession().sql;
        importTable.writeRows(sql, rows, owner);

        ImRevMap<String, KeyExpr> mapKeys = importTable.getMapKeys();
        Join<LP> importJoin = importTable.join(mapKeys);
        Where where = importJoin.getWhere();
        try {
            LP lp = props.get(0);
            PropertyChange propChange = new PropertyChange(MapFact.singletonRev(lp.listInterfaces.single(), mapKeys.singleValue()), importJoin.getExpr(lp), where);
            context.getEnv().change(lp.property, propChange);
        } finally {
            importTable.drop(sql, owner);
        }
    }

    private Function<LP, ObjectValue> getMapValueGetter(final String id) {
        return prop -> new DataObject(id);
    }
}