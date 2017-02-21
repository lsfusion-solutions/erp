package equ.srv;

import com.google.common.base.Throwables;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.server.classes.DateClass;
import lsfusion.server.context.ExecutionStack;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.DBManager;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.trim;

public class SendSalesEquipmentServer {

    public static Map<String, List<Object>> readRequestZReportSumMap(BusinessLogics BL, DBManager dbManager, ExecutionStack stack, String idStock, Date dateFrom, Date dateTo) {
        Map<String, List<Object>> zReportSumMap = new HashMap<>();
        ScriptingLogicsModule equipmentCashRegisterLM = BL.getModule("EquipmentCashRegister");
        ScriptingLogicsModule zReportLM = BL.getModule("ZReport");
        if (zReportLM != null && equipmentCashRegisterLM != null) {
            try (DataSession session = dbManager.createSession()) {

                DataObject stockObject = (DataObject) equipmentCashRegisterLM.findProperty("stock[VARSTRING[100]]").readClasses(session, new DataObject(idStock));

                KeyExpr zReportExpr = new KeyExpr("zReport");
                ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "zReport", zReportExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
                String[] names = new String[]{"sumReceiptDetailZReport", "numberZReport", "numberCashRegisterZReport",
                        "dateZReport", "nameDepartmentStore"};
                LCP<?>[] properties = zReportLM.findProperties("sumReceiptDetail[ZReport]", "number[ZReport]", "numberCashRegister[ZReport]",
                        "date[ZReport]", "nameDepartmentStore[ZReport]");
                for (int i = 0; i < properties.length; i++) {
                    query.addProperty(names[i], properties[i].getExpr(zReportExpr));
                }
                query.and(zReportLM.findProperty("date[ZReport]").getExpr(zReportExpr).compare(new DataObject(dateFrom, DateClass.instance), Compare.GREATER_EQUALS));
                query.and(zReportLM.findProperty("date[ZReport]").getExpr(zReportExpr).compare(new DataObject(dateTo, DateClass.instance), Compare.LESS_EQUALS));
                query.and(zReportLM.findProperty("departmentStore[ZReport]").getExpr(zReportExpr).compare(stockObject.getExpr(), Compare.EQUALS));
                query.and(zReportLM.findProperty("number[ZReport]").getExpr(zReportExpr).getWhere());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> zReportResult = query.execute(session);
                for (ImMap<Object, Object> entry : zReportResult.values()) {
                    String numberZReport = trim((String) entry.get("numberZReport"));
                    Integer numberCashRegisterZReport = (Integer) entry.get("numberCashRegisterZReport");
                    BigDecimal sumZReport = (BigDecimal) entry.get("sumReceiptDetailZReport");
                    Date dateZReport = (Date) entry.get("dateZReport");
                    String nameDepartmentStore = (String) entry.get("nameDepartmentStore");
                    zReportSumMap.put(numberZReport + "/" + numberCashRegisterZReport, Arrays.asList((Object) sumZReport,
                            dateZReport, nameDepartmentStore));
                }

                session.apply(BL, stack);
            } catch (ScriptingErrorLog.SemanticErrorException | SQLException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return zReportSumMap;
    }

}