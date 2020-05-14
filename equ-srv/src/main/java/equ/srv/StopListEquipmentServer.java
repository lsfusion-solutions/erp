package equ.srv;

import com.google.common.base.Throwables;
import equ.api.ItemInfo;
import equ.api.MachineryInfo;
import equ.api.StopListInfo;
import equ.api.cashregister.CashRegisterInfo;
import equ.api.scales.ScalesInfo;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.action.controller.stack.ExecutionStack;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.logics.classes.ConcreteClass;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static lsfusion.erp.integration.DefaultIntegrationAction.*;
import static org.apache.commons.lang3.StringUtils.trim;

public class StopListEquipmentServer {
    static ScriptingLogicsModule cashRegisterLM;
    static ScriptingLogicsModule equipmentLM;
    static ScriptingLogicsModule machineryLM;
    static ScriptingLogicsModule scalesLM;
    static ScriptingLogicsModule scalesItemLM;
    static ScriptingLogicsModule stopListLM;

    public static void init(BusinessLogics BL) {
        cashRegisterLM = BL.getModule("EquipmentCashRegister");
        equipmentLM = BL.getModule("Equipment");
        machineryLM = BL.getModule("Machinery");
        scalesLM = BL.getModule("EquipmentScales");
        scalesItemLM = BL.getModule("ScalesItem");
        stopListLM = BL.getModule("StopList");
    }

    public static boolean enabledStopListInfo() {
        return (cashRegisterLM != null || scalesLM != null)  && stopListLM != null;
    }

    public static List<StopListInfo> readStopListInfo(EquipmentServer server) throws SQLException {

        List<StopListInfo> stopListInfoList = new ArrayList<>();
        Map<String, StopListInfo> stopListInfoMap = new HashMap<>();
        if(machineryLM != null && stopListLM != null) {
            try (DataSession session = server.createSession()) {
                Map<String, Map<String, Set<MachineryInfo>>> stockMap = null;
                KeyExpr stopListExpr = new KeyExpr("stopList");
                ImRevMap<Object, KeyExpr> slKeys = MapFact.singletonRev("stopList", stopListExpr);
                QueryBuilder<Object, Object> slQuery = new QueryBuilder<>(slKeys);
                String[] slNames = new String[]{"excludeStopList", "numberStopList", "fromDateStopList", "fromTimeStopList",
                        "toDateStopList", "toTimeStopList"};
                LP<?>[] slProperties = stopListLM.findProperties("exclude[StopList]", "number[StopList]", "fromDate[StopList]", "fromTime[StopList]",
                        "toDate[StopList]", "toTime[StopList]");
                for (int i = 0; i < slProperties.length; i++) {
                    slQuery.addProperty(slNames[i], slProperties[i].getExpr(stopListExpr));
                }
                slQuery.and(stopListLM.findProperty("number[StopList]").getExpr(stopListExpr).getWhere());
                slQuery.and(stopListLM.findProperty("isPosted[StopList]").getExpr(stopListExpr).getWhere());
                slQuery.and(stopListLM.findProperty("toExport[StopList]").getExpr(stopListExpr).getWhere());
                ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> slResult = slQuery.executeClasses(session);
                for (int i = 0, size = slResult.size(); i < size; i++) {
                    DataObject stopListObject = slResult.getKey(i).get("stopList");
                    ImMap<Object, ObjectValue> slEntry = slResult.getValue(i);
                    String numberStopList = trim((String) slEntry.get("numberStopList").getValue());
                    boolean excludeStopList = slEntry.get("excludeStopList").getValue() != null;
                    LocalDate dateFrom = (LocalDate) slEntry.get("fromDateStopList").getValue();
                    LocalDate dateTo = (LocalDate) slEntry.get("toDateStopList").getValue();
                    LocalTime timeFrom = (LocalTime) slEntry.get("fromTimeStopList").getValue();
                    LocalTime timeTo = (LocalTime) slEntry.get("toTimeStopList").getValue();

                    Set<String> idStockSet = new HashSet<>();
                    Map<String, Set<MachineryInfo>> handlerMachineryMap = new HashMap<>();
                    Map<Integer, Set<String>> itemsInGroupMachineryMap = new HashMap();
                    KeyExpr stockExpr = new KeyExpr("stock");
                    KeyExpr groupMachineryExpr = new KeyExpr("groupMachinery");
                    ImRevMap<Object, KeyExpr> stockKeys = MapFact.toRevMap("stock", stockExpr, "groupMachinery", groupMachineryExpr);
                    QueryBuilder<Object, Object> stockQuery = new QueryBuilder<>(stockKeys);
                    stockQuery.addProperty("idStock", stopListLM.findProperty("id[Stock]").getExpr(stockExpr));
                    stockQuery.addProperty("nppGroupMachinery", machineryLM.findProperty("npp[GroupMachinery]").getExpr(groupMachineryExpr));
                    stockQuery.and(stopListLM.findProperty("id[Stock]").getExpr(stockExpr).getWhere());
                    stockQuery.and(stopListLM.findProperty("in[Stock,StopList]").getExpr(stockExpr, stopListObject.getExpr()).getWhere());
                    stockQuery.and(stopListLM.findProperty("overIn[GroupMachinery,StopList]").getExpr(groupMachineryExpr, stopListObject.getExpr()).getWhere());
                    stockQuery.and(stopListLM.findProperty("notSucceeded[Stock,StopList]").getExpr(stockExpr, stopListObject.getExpr()).getWhere());
                    stockQuery.and(machineryLM.findProperty("stock[GroupMachinery]").getExpr(groupMachineryExpr).compare(stockExpr, Compare.EQUALS));
                    stockQuery.and(equipmentLM.findProperty("equipmentServer[GroupMachinery]").getExpr(groupMachineryExpr).getWhere());
                    stockQuery.and(machineryLM.findProperty("active[GroupMachinery]").getExpr(groupMachineryExpr).getWhere());
                    ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> stockResult = stockQuery.executeClasses(session);
                    for (int j = 0; j < stockResult.size(); j++) {
                        ImMap<Object, ObjectValue> stockEntry = stockResult.getValue(j);
                        String idStock = trim((String) stockEntry.get("idStock").getValue());
                        idStockSet.add(idStock);
                        if(stockMap == null)
                            stockMap = getStockMap(session, stopListObject);
                        if(stockMap.containsKey(idStock))
                            for (Map.Entry<String, Set<MachineryInfo>> entry : stockMap.get(idStock).entrySet()) {
                                if (handlerMachineryMap.containsKey(entry.getKey()))
                                    handlerMachineryMap.get(entry.getKey()).addAll(entry.getValue());
                                else
                                    handlerMachineryMap.put(entry.getKey(), entry.getValue());
                            }
                        Integer groupMachinery = (Integer) stockEntry.get("nppGroupMachinery").getValue();
                        Set<String> itemsInGroupMachinerySet = new HashSet<>();
                        if(!itemsInGroupMachineryMap.containsKey(groupMachinery)) {
                            itemsInGroupMachinerySet.addAll(getInGroupMachineryItemSet(session, stopListObject, stockResult.getKey(j).get("groupMachinery")));
                            itemsInGroupMachineryMap.put(groupMachinery, itemsInGroupMachinerySet);
                        }
                    }

                    if(!handlerMachineryMap.isEmpty()) {
                        Map<String, ItemInfo> stopListItemMap = getStopListItemMap(session, stopListObject, idStockSet);
                        StopListInfo stopList = stopListInfoMap.get(numberStopList);
                        Map<Integer, Set<String>> inGroupMachineryItemMap = stopList == null ? new HashMap<>() : stopList.inGroupMachineryItemMap;
                        inGroupMachineryItemMap.putAll(itemsInGroupMachineryMap);
                        stopListInfoMap.put(numberStopList, new StopListInfo(excludeStopList, numberStopList,
                                dateFrom, timeFrom, dateTo, timeTo, idStockSet, inGroupMachineryItemMap, stopListItemMap, handlerMachineryMap));
                    }
                }

                stopListInfoList.addAll(stopListInfoMap.values());
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }

        return stopListInfoList;
    }

    private static Map<String, Map<String, Set<MachineryInfo>>> getStockMap(DataSession session, DataObject stopListObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, Map<String, Set<MachineryInfo>>> stockMap = new HashMap<>();

        KeyExpr groupMachineryExpr = new KeyExpr("groupMachinery");
        KeyExpr machineryExpr = new KeyExpr("machinery");
        ImRevMap<Object, KeyExpr> machineryKeys = MapFact.toRevMap("groupMachinery", groupMachineryExpr, "machinery", machineryExpr);
        QueryBuilder<Object, Object> machineryQuery = new QueryBuilder<>(machineryKeys);

        String[] groupMachineryNames = new String[] {"nppGroupMachinery", "handlerModelGroupMachinery", "idStockGroupMachinery"};
        LP[] groupMachineryProperties = machineryLM.findProperties("npp[GroupMachinery]", "handlerModel[GroupMachinery]", "idStock[GroupMachinery]");
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
        machineryQuery.and(stopListLM.findProperty("overIn[GroupMachinery,StopList]").getExpr(groupMachineryExpr, stopListObject.getExpr()).getWhere());
        machineryQuery.and(equipmentLM.findProperty("equipmentServer[GroupMachinery]").getExpr(groupMachineryExpr).getWhere());
        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> machineryResult = machineryQuery.executeClasses(session);
        ValueClass cashRegisterClass = cashRegisterLM == null ? null : cashRegisterLM.findClass("CashRegister");
        ValueClass scalesClass = scalesLM == null ? null : scalesLM.findClass("Scales");
        for (int i = 0; i < machineryResult.size(); i++) {
            ImMap<Object, ObjectValue> values = machineryResult.getValue(i);
            Integer nppGroupMachinery = (Integer) values.get("nppGroupMachinery").getValue();
            String handlerModel = (String) values.get("handlerModelGroupMachinery").getValue();
            String directory = trim((String) values.get("overDirectoryMachinery").getValue());
            String port = trim((String) values.get("portMachinery").getValue());
            Integer nppMachinery = (Integer) values.get("nppMachinery").getValue();
            ConcreteClass machineryClass = machineryResult.getKey(i).get("machinery").objectClass;
            boolean isCashRegister = machineryClass != null && machineryClass.equals(cashRegisterClass);
            boolean isScales = machineryClass != null && machineryClass.equals(scalesClass);
            String idStockGroupMachinery = (String) values.get("idStockGroupMachinery").getValue();
            Integer overDepartNumber = (Integer) values.get("overDepartmentNumber").getValue();

            Map<String, Set<MachineryInfo>> handlerMap = stockMap.containsKey(idStockGroupMachinery) ? stockMap.get(idStockGroupMachinery) : new HashMap<>();
            if(!handlerMap.containsKey(handlerModel))
                handlerMap.put(handlerModel, new HashSet<>());
            if(isCashRegister) {
                handlerMap.get(handlerModel).add(new CashRegisterInfo(nppGroupMachinery, nppMachinery, handlerModel, port, directory, idStockGroupMachinery, overDepartNumber));
            } else if(isScales){
                handlerMap.get(handlerModel).add(new ScalesInfo(nppGroupMachinery, nppMachinery, handlerModel, port, directory, idStockGroupMachinery));
            }
            stockMap.put(idStockGroupMachinery, handlerMap);
        }
        return stockMap;
    }

    private static Map<String, ItemInfo> getStopListItemMap(DataSession session, DataObject stopListObject, Set<String> idStockSet) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, ItemInfo> stopListItemList = new HashMap<>();

        KeyExpr sldExpr = new KeyExpr("stopListDetail");
        ImRevMap<Object, KeyExpr> sldKeys = MapFact.singletonRev("stopListDetail", sldExpr);
        QueryBuilder<Object, Object> sldQuery = new QueryBuilder<>(sldKeys);
        String[] sldNames = new String[] {"idBarcodeSkuStopListDetail", "idSkuStopListDetail", "nameSkuStopListDetail", "idSkuGroupStopListDetail",
                "nameSkuGroupStopListDetail", "idUOMSkuStopListDetail", "shortNameUOMSkuStopListDetail", "splitSkuStopListDetail", "passScalesSkuStopListDetail",
                "flagsSkuStopListDetail", "valueVATSkuStopListDetail"};
        LP[] sldProperties = stopListLM.findProperties("idBarcodeSku[StopListDetail]", "idSku[StopListDetail]", "nameSku[StopListDetail]", "idSkuGroup[StopListDetail]",
                "nameSkuGroup[StopListDetail]", "idUOMSku[StopListDetail]", "shortNameUOMSku[StopListDetail]", "splitSku[StopListDetail]", "passScalesSku[StopListDetail]",
                "flagsSku[StopListDetail]", "valueVATSku[StopListDetail]");
        for (int i = 0; i < sldProperties.length; i++) {
            sldQuery.addProperty(sldNames[i], sldProperties[i].getExpr(sldExpr));
        }
        if(scalesItemLM != null) {
            sldQuery.addProperty("skuStopListDetail", stopListLM.findProperty("sku[StopListDetail]").getExpr(sldExpr));
            sldQuery.and(stopListLM.findProperty("sku[StopListDetail]").getExpr(sldExpr).getWhere());
        }
        sldQuery.and(stopListLM.findProperty("idBarcodeSku[StopListDetail]").getExpr(sldExpr).getWhere());
        sldQuery.and(stopListLM.findProperty("stopList[StopListDetail]").getExpr(sldExpr).compare(stopListObject, Compare.EQUALS));
        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> sldResult = sldQuery.executeClasses(session);
        for (int i = 0; i < sldResult.size(); i++) {
            ImMap<Object, ObjectValue> values = sldResult.getValue(i);
            ObjectValue skuObject = values.get("skuStopListDetail");
            String idBarcode = trim((String) values.get("idBarcodeSkuStopListDetail").getValue());
            String idItem = trim((String) values.get("idSkuStopListDetail").getValue());
            String nameItem = trim((String) values.get("nameSkuStopListDetail").getValue());
            String idSkuGroup = trim((String) values.get("idSkuGroupStopListDetail").getValue());
            String nameSkuGroup = trim((String) values.get("nameSkuGroupStopListDetail").getValue());
            String idUOM = trim((String) values.get("idUOMSkuStopListDetail").getValue());
            String shortNameUOM = trim((String) values.get("shortNameUOMSkuStopListDetail").getValue());
            boolean split = values.get("splitSkuStopListDetail").getValue() != null;
            boolean passScales = values.get("passScalesSkuStopListDetail").getValue() != null;
            Integer flags = (Integer) values.get("flagsSkuStopListDetail").getValue();
            BigDecimal valueVAT = (BigDecimal) values.get("valueVATSkuStopListDetail").getValue();
            Map<String, Integer> stockPluNumberMap = new HashMap();
            for(String idStock : idStockSet) {
                Integer pluNumber = (Integer) scalesItemLM.findProperty("pluIdStockSku[STRING[100],Item]").read(session, new DataObject(idStock), skuObject);
                stockPluNumberMap.put(idStock, pluNumber);
            }
            stopListItemList.put(idBarcode, new ItemInfo(stockPluNumberMap, idItem, idBarcode, nameItem, null, split, null, null, passScales,
                    valueVAT, null, flags, idSkuGroup, nameSkuGroup, idUOM, shortNameUOM, null));
        }
        return stopListItemList;
    }

    private static Set<String> getInGroupMachineryItemSet(DataSession session, DataObject stopListObject, DataObject groupMachineryObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Set<String> inGroupMachineryItemSet = new HashSet<>();

        KeyExpr sldExpr = new KeyExpr("stopListDetail");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev("stopListDetail", sldExpr);
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



    public static void errorStopListReport(BusinessLogics BL, ExecutionStack stack, EquipmentServer server, String numberStopList, Exception e) {
        if(stopListLM != null) {
            try (DataSession session = server.createSession()) {
                DataObject errorObject = session.addObject((ConcreteCustomClass) stopListLM.findClass("StopListError"));
                ObjectValue stopListObject = stopListLM.findProperty("stopList[BPSTRING[18]]").readClasses(session, new DataObject(numberStopList));
                stopListLM.findProperty("stopList[StopListError]").change(stopListObject, session, errorObject);
                stopListLM.findProperty("data[StopListError]").change(e.toString(), session, errorObject);
                stopListLM.findProperty("date[StopListError]").change(getWriteDateTime(LocalDateTime.now()), session, errorObject);
                OutputStream os = new ByteArrayOutputStream();
                e.printStackTrace(new PrintStream(os));
                stopListLM.findProperty("errorTrace[StopListError]").change(os.toString(), session, errorObject);

                session.applyException(BL, stack);
            } catch (Exception e2) {
                throw Throwables.propagate(e2);
            }
        }
    }

    public static void succeedStopList(BusinessLogics BL, ExecutionStack stack, EquipmentServer server, String numberStopList, Set<String> idStockSet) {
        if(stopListLM != null) {
            try (DataSession session = server.createSession()) {
                DataObject stopListObject = (DataObject) stopListLM.findProperty("stopList[BPSTRING[18]]").readClasses(session, new DataObject(numberStopList));
                for (String idStock : idStockSet) {
                    DataObject stockObject = (DataObject) stopListLM.findProperty("stock[STRING[100]]").readClasses(session, new DataObject(idStock));
                    stopListLM.findProperty("succeeded[Stock,StopList]").change(true, session, stockObject, stopListObject);
                }
                session.applyException(BL, stack);
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

}