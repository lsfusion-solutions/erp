package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import com.google.common.base.Throwables;
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
import lsfusion.server.language.ScriptingActionProperty;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.sql.SQLException;

public class FiscalVMKUpdateDataActionProperty extends ScriptingActionProperty {

    public FiscalVMKUpdateDataActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataSession session = context.getSession();

        try {
            String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context.getSession());
            String ip = (String) findProperty("ipCurrentCashRegister[]").read(context.getSession());
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(session);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(session);


            KeyExpr customUserExpr = new KeyExpr("customUser");
            KeyExpr groupCashRegisterExpr = new KeyExpr("groupCashRegister");
            ImRevMap<Object, KeyExpr> operatorKeys = MapFact.toRevMap((Object)"customUser", customUserExpr, "groupCashRegister", groupCashRegisterExpr);

            QueryBuilder<Object, Object> operatorQuery = new QueryBuilder<>(operatorKeys);
            operatorQuery.addProperty("operatorNumberGroupCashRegisterCustomUser", findProperty("operatorNumber[GroupCashRegister,CustomUser]").getExpr(context.getModifier(), groupCashRegisterExpr, customUserExpr));
            operatorQuery.addProperty("firstNameContact", findProperty("firstName[Contact]").getExpr(context.getModifier(), customUserExpr));
            operatorQuery.addProperty("lastNameContact", findProperty("lastName[Contact]").getExpr(context.getModifier(), customUserExpr));

            operatorQuery.and(findProperty("operatorNumber[GroupCashRegister,CustomUser]").getExpr(context.getModifier(), operatorQuery.getMapExprs().get("groupCashRegister"), operatorQuery.getMapExprs().get("customUser")).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> operatorResult = operatorQuery.execute(session);


            if (context.checkApply()) {
                String result = (String) context.requestUserInteraction(new FiscalVMKUpdateDataClientAction(logPath, ip, comPort, baudRate));
                if (result == null)
                    context.apply();
                else {
                    ServerLoggers.systemLogger.error("FiscalVMKUpdateData Error: " + result);
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
                }
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }


    }
}
