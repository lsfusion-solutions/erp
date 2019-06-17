package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.form.property.Compare;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.session.DataSession;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FiscalShtrihUpdateDataAction extends InternalAction {

    public FiscalShtrihUpdateDataAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataSession session = context.getSession();

        try {
            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(session);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(session);
            Integer pass = (Integer) findProperty("operatorNumberCurrentCashRegisterCurrentUser[]").read(context.getSession());
            int password = pass == null ? 30000 : pass * 1000;

            KeyExpr customUserExpr = new KeyExpr("customUser");
            KeyExpr groupCashRegisterExpr = new KeyExpr("groupCashRegister");
            ImRevMap<Object, KeyExpr> operatorKeys = MapFact.toRevMap((Object) "customUser", customUserExpr, "groupCashRegister", groupCashRegisterExpr);

            QueryBuilder<Object, Object> operatorQuery = new QueryBuilder<>(operatorKeys);
            operatorQuery.addProperty("operatorNumberGroupCashRegisterCustomUser", findProperty("operatorNumber[GroupCashRegister,CustomUser]").getExpr(context.getModifier(), groupCashRegisterExpr, customUserExpr));
            operatorQuery.addProperty("firstNameContact", findProperty("firstName[Contact]").getExpr(context.getModifier(), customUserExpr));
            operatorQuery.addProperty("lastNameContact", findProperty("lastName[Contact]").getExpr(context.getModifier(), customUserExpr));

            operatorQuery.and(findProperty("operatorNumber[GroupCashRegister,CustomUser]").getExpr(context.getModifier(), operatorQuery.getMapExprs().get("groupCashRegister"), operatorQuery.getMapExprs().get("customUser")).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> operatorResult = operatorQuery.execute(session);
            List<UpdateDataOperator> operatorList = new ArrayList<>();
            for (ImMap<Object, Object> operatorValues : operatorResult.valueIt()) {
                Integer number = (Integer) operatorValues.get("operatorNumberGroupCashRegisterCustomUser");
                String firstNameContact = (String) operatorValues.get("firstNameContact");
                String lastNameContact = (String) operatorValues.get("lastNameContact");
                if (number != null)
                    operatorList.add(new UpdateDataOperator(number * 1000, number, (firstNameContact == null ? "" : firstNameContact.trim()) + " " + (lastNameContact == null ? "" : lastNameContact.trim())));
            }

            List<UpdateDataTaxRate> taxRateList = new ArrayList<>();
            ObjectValue countryObject = findProperty("countryCurrentCashRegister[]").readClasses(session);
            DataObject taxVATObject = ((ConcreteCustomClass) findClass("Tax")).getDataObject("taxVAT");
            KeyExpr rangeExpr = new KeyExpr("range");
            KeyExpr taxExpr = new KeyExpr("tax");
            ImRevMap<Object, KeyExpr> rangeKeys = MapFact.toRevMap((Object) "range", rangeExpr, "tax", taxExpr);

            QueryBuilder<Object, Object> rangeQuery = new QueryBuilder<>(rangeKeys);
            rangeQuery.addProperty("numberRange", findProperty("number[Range]").getExpr(context.getModifier(), rangeExpr));
            rangeQuery.addProperty("valueCurrentRateRange", findProperty("valueCurrentRate[Range]").getExpr(context.getModifier(), rangeExpr));
            rangeQuery.addProperty("countryRange", findProperty("country[Range]").getExpr(context.getModifier(), rangeExpr));
            rangeQuery.addProperty("reverseRange", findProperty("reverse[Range]").getExpr(context.getModifier(), rangeExpr));

            rangeQuery.and(findProperty("country[Range]").getExpr(context.getModifier(), rangeQuery.getMapExprs().get("range")).compare(countryObject.getExpr(), Compare.EQUALS));
            rangeQuery.and(findProperty("tax[Range]").getExpr(context.getModifier(), rangeQuery.getMapExprs().get("tax")).compare(taxVATObject.getExpr(), Compare.EQUALS));
            rangeQuery.and(findProperty("number[Range]").getExpr(context.getModifier(), rangeQuery.getMapExprs().get("range")).getWhere());

            Set<Integer> taxNumbers = new HashSet<>();
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> rangeResult = rangeQuery.execute(session, MapFact.singletonOrder((Object) "numberRange", false));
            int i = 1;
            for (ImMap<Object, Object> rangeValues : rangeResult.valueIt()) {
                Integer number = (Integer) rangeValues.get("numberRange");
                boolean reverseRange = rangeValues.get("reverseRange") != null;
                BigDecimal value = (BigDecimal) rangeValues.get("valueCurrentRateRange");
                if (number != null && value != null && value.intValue() > 0 && !taxNumbers.contains(number)) {
                    taxNumbers.add(number);
                    if (!reverseRange) {
                        taxRateList.add(new UpdateDataTaxRate(i, value));
                        i++;
                    }
                }
            }

            if (context.checkApply()) {
                String result = (String) context.requestUserInteraction(new FiscalShtrihUpdateDataClientAction(password, comPort, baudRate, new UpdateDataInstance(operatorList, taxRateList)));
                if (result == null)
                    context.apply();
                else
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }


    }
}
