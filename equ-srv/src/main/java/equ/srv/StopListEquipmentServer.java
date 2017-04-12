package equ.srv;

import com.google.common.base.Throwables;
import equ.api.MachineryInfo;
import equ.api.cashregister.CashRegisterInfo;
import equ.api.scales.ScalesInfo;
import lsfusion.base.DateConverter;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.server.classes.ConcreteClass;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.context.ExecutionStack;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.DBManager;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.trim;

public class StopListEquipmentServer {
    static ScriptingLogicsModule cashRegisterLM;
    static ScriptingLogicsModule equipmentLM;
    static ScriptingLogicsModule machineryLM;
    static ScriptingLogicsModule scalesLM;
    static ScriptingLogicsModule stopListLM;

    public static void init(BusinessLogics BL) {
        cashRegisterLM = BL.getModule("EquipmentCashRegister");
        equipmentLM = BL.getModule("Equipment");
        machineryLM = BL.getModule("Machinery");
        scalesLM = BL.getModule("EquipmentScales");
        stopListLM = BL.getModule("StopList");
    }

    public static Map<String, Map<String, Set<MachineryInfo>>> getStockMap(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, Map<String, Set<MachineryInfo>>> stockMap = new HashMap<>();

        KeyExpr groupMachineryExpr = new KeyExpr("groupMachinery");
        KeyExpr machineryExpr = new KeyExpr("machinery");
        ImRevMap<Object, KeyExpr> machineryKeys = MapFact.toRevMap((Object) "groupMachinery", groupMachineryExpr, "machinery", machineryExpr);
        QueryBuilder<Object, Object> machineryQuery = new QueryBuilder<>(machineryKeys);

        String[] groupMachineryNames = new String[] {"nppGroupMachinery", "handlerModelGroupMachinery", "idStockGroupMachinery"};
        LCP[] groupMachineryProperties = machineryLM.findProperties("npp[GroupMachinery]", "handlerModel[GroupMachinery]", "idStock[GroupMachinery]");
        for (int i = 0; i < groupMachineryProperties.length; i++) {
            machineryQuery.addProperty(groupMachineryNames[i], groupMachineryProperties[i].getExpr(groupMachineryExpr));
        }
        machineryQuery.addProperty("overDirectoryMachinery", machineryLM.findProperty("overDirectory[Machinery]").getExpr(machineryExpr));
        machineryQuery.addProperty("portMachinery", machineryLM.findProperty("port[Machinery]").getExpr(machineryExpr));
        machineryQuery.addProperty("nppMachinery", machineryLM.findProperty("npp[Machinery]").getExpr(machineryExpr));
        if(cashRegisterLM != null)
            machineryQuery.addProperty("overDepartmentNumber", cashRegisterLM.findProperty("overDepartmentNumber[Machinery]").getExpr(machineryExpr));

        machineryQuery.and(machineryLM.findProperty("handlerModel[GroupMachinery]").getExpr(groupMachineryExpr).getWhere());
        machineryQuery.and(machineryLM.findProperty("idStock[GroupMachinery]").getExpr(groupMachineryExpr).getWhere());
        machineryQuery.and(machineryLM.findProperty("groupMachinery[Machinery]").getExpr(machineryExpr).compare(groupMachineryExpr, Compare.EQUALS));
        machineryQuery.and(machineryLM.findProperty("active[GroupMachinery]").getExpr(groupMachineryExpr).getWhere());
        machineryQuery.and(equipmentLM.findProperty("equipmentServer[GroupMachinery]").getExpr(groupMachineryExpr).getWhere());
        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> machineryResult = machineryQuery.executeClasses(session);
        ValueClass cashRegisterClass = cashRegisterLM == null ? null : cashRegisterLM.findClass("CashRegister");
        ValueClass scalesClass = scalesLM == null ? null : scalesLM.findClass("Scales");
        for (int i = 0; i < machineryResult.size(); i++) {
            ImMap<Object, ObjectValue> values = machineryResult.getValue(i);
            Integer nppGroupMachinery = (Integer) values.get("nppGroupMachinery").getValue();
            String handlerModel = (String) values.get("handlerModelGroupMachinery").getValue();
            String directory = (String) values.get("overDirectoryMachinery").getValue();
            String port = (String) values.get("portMachinery").getValue();
            Integer nppMachinery = (Integer) values.get("nppMachinery").getValue();
            ConcreteClass machineryClass = machineryResult.getKey(i).get("machinery").objectClass;
            boolean isCashRegister = machineryClass != null && machineryClass.equals(cashRegisterClass);
            boolean isScales = machineryClass != null && machineryClass.equals(scalesClass);
            String idStockGroupMachinery = (String) values.get("idStockGroupMachinery").getValue();
            Integer overDepartNumber = (Integer) values.get("overDepartmentNumber").getValue();

            Map<String, Set<MachineryInfo>> handlerMap = stockMap.containsKey(idStockGroupMachinery) ? stockMap.get(idStockGroupMachinery) : new HashMap<String, Set<MachineryInfo>>();
            if(!handlerMap.containsKey(handlerModel))
                handlerMap.put(handlerModel, new HashSet<MachineryInfo>());
            if(isCashRegister) {
                handlerMap.get(handlerModel).add(new CashRegisterInfo(nppGroupMachinery, nppMachinery, handlerModel, port, directory, idStockGroupMachinery, overDepartNumber));
            } else if(isScales){
                handlerMap.get(handlerModel).add(new ScalesInfo(nppGroupMachinery, nppMachinery, handlerModel, port, directory, idStockGroupMachinery));
            }
            stockMap.put(idStockGroupMachinery, handlerMap);
        }
        return stockMap;
    }

    public static Set<String> getInGroupMachineryItemSet(DataSession session, DataObject stopListObject, DataObject groupMachineryObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Set<String> inGroupMachineryItemSet = new HashSet<>();

        KeyExpr sldExpr = new KeyExpr("stopListDetail");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "stopListDetail", sldExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("idSkuStopListDetail", stopListLM.findProperty("idSku[StopListDetail]").getExpr(sldExpr));
        query.and(stopListLM.findProperty("in[GroupMachinery,StopListDetail]").getExpr(groupMachineryObject.getExpr(), sldExpr).getWhere());
        query.and(stopListLM.findProperty("stopList[StopListDetail]").getExpr(sldExpr).compare(stopListObject, Compare.EQUALS));
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
        for (int i = 0; i < result.size(); i++) {
            String idItem = trim((String) result.getValue(i).get("idSkuStopListDetail"));
            inGroupMachineryItemSet.add(idItem);
        }
        return inGroupMachineryItemSet;
    }



    public static void errorStopListReport(BusinessLogics BL, ExecutionStack stack, DBManager dbManager, String numberStopList, Exception e) throws RemoteException, SQLException {
        if(stopListLM != null) {
            try (DataSession session = dbManager.createSession()) {
                DataObject errorObject = session.addObject((ConcreteCustomClass) stopListLM.findClass("StopListError"));
                ObjectValue stopListObject = stopListLM.findProperty("stopList[STRING[18]]").readClasses(session, new DataObject(numberStopList));
                stopListLM.findProperty("stopList[StopListError]").change(stopListObject.getValue(), session, errorObject);
                stopListLM.findProperty("data[StopListError]").change(e.toString(), session, errorObject);
                stopListLM.findProperty("date[StopListError]").change(getCurrentTimestamp(), session, errorObject);
                OutputStream os = new ByteArrayOutputStream();
                e.printStackTrace(new PrintStream(os));
                stopListLM.findProperty("errorTrace[StopListError]").change(os.toString(), session, errorObject);

                session.apply(BL, stack);
            } catch (Exception e2) {
                throw Throwables.propagate(e2);
            }
        }
    }

    public static void succeedStopList(BusinessLogics BL, ExecutionStack stack, DBManager dbManager, String numberStopList, Set<String> idStockSet) throws RemoteException, SQLException {
        if(stopListLM != null) {
            try (DataSession session = dbManager.createSession()) {
                DataObject stopListObject = (DataObject) stopListLM.findProperty("stopList[STRING[18]]").readClasses(session, new DataObject(numberStopList));
                for (String idStock : idStockSet) {
                    DataObject stockObject = (DataObject) stopListLM.findProperty("stock[VARSTRING[100]]").readClasses(session, new DataObject(idStock));
                    stopListLM.findProperty("succeeded[Stock,StopList]").change(true, session, stockObject, stopListObject);
                }
                session.apply(BL, stack);
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    private static Timestamp getCurrentTimestamp() {
        return DateConverter.dateToStamp(Calendar.getInstance().getTime());
    }

}