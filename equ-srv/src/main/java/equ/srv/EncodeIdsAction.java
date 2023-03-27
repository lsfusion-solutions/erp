package equ.srv;

import com.google.common.base.Throwables;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.data.StringClass;
import lsfusion.server.logics.classes.data.integral.LongClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.sql.SQLException;
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
        for (int i = 0, size = result.size(); i < size; i++) {
            ids.put((long) result.getKey(i).get("long"), encoder.encodeLessMemory((String) result.getValue(i).get("id")));
        }
        return MapFact.fromJavaMap(ids);
    }

    private void writeIds(ExecutionContext<ClassPropertyInterface> context, ImMap<Long, String> processes) throws SQLException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
        LP<?> property = findProperty("encodingId[LONG]");
        property.change(context, processes, LongClass.instance, StringClass.instance);
    }

    private Function<LP, ObjectValue> getMapValueGetter(final String id) {
        return prop -> new DataObject(id);
    }
}