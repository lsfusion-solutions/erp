package equ.srv;

import com.google.common.base.Throwables;
import equ.api.cashregister.CashDocument;
import equ.api.cashregister.CashRegisterInfo;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.DateClass;
import lsfusion.server.context.ExecutionStack;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.DBManager;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.sql.Date;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.trim;

public class SendSalesEquipmentServer {

    static ScriptingLogicsModule cashRegisterLM;
    static ScriptingLogicsModule cashOperationLM;
    static ScriptingLogicsModule equipmentCashRegisterLM;
    static ScriptingLogicsModule machineryPriceTransactionLM;
    static ScriptingLogicsModule zReportLM;

    public static void init(BusinessLogics BL) {
        cashRegisterLM = BL.getModule("EquipmentCashRegister");
        cashOperationLM = BL.getModule("CashOperation");
        equipmentCashRegisterLM = BL.getModule("EquipmentCashRegister");
        machineryPriceTransactionLM = BL.getModule("MachineryPriceTransaction");
        zReportLM = BL.getModule("ZReport");
    }

    public static List<CashRegisterInfo> readCashRegisterInfo(DBManager dbManager, EquipmentServer server, String sidEquipmentServer) throws RemoteException, SQLException {
        List<CashRegisterInfo> cashRegisterInfoList = new ArrayList<>();
        if (cashRegisterLM != null) {
            try (DataSession session = server.createSession()) {

                KeyExpr groupCashRegisterExpr = new KeyExpr("groupCashRegister");
                KeyExpr cashRegisterExpr = new KeyExpr("cashRegister");

                ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "groupCashRegister", groupCashRegisterExpr, "cashRegister", cashRegisterExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

                String[] cashRegisterNames = new String[]{"nppMachinery", "portMachinery", "overDirectoryMachinery", "disableSalesCashRegister"};
                LCP[] cashRegisterProperties = cashRegisterLM.findProperties("npp[Machinery]", "port[Machinery]", "overDirectory[Machinery]", "disableSales[CashRegister]");
                for (int i = 0; i < cashRegisterProperties.length; i++) {
                    query.addProperty(cashRegisterNames[i], cashRegisterProperties[i].getExpr(cashRegisterExpr));
                }

                String[] groupCashRegisterNames = new String[]{"nppGroupMachinery", "handlerModelGroupMachinery",
                        "overDepartmentNumberGroupCashRegister", "pieceCodeGroupCashRegister", "weightCodeGroupCashRegister",
                        "idStockGroupMachinery", "section", "documentsClosedDate"};
                LCP[] groupCashRegisterProperties = cashRegisterLM.findProperties("npp[GroupMachinery]", "handlerModel[GroupMachinery]",
                        "overDepartmentNumberCashRegister[GroupMachinery]", "pieceCode[GroupCashRegister]", "weightCode[GroupCashRegister]", "idStock[GroupMachinery]",
                        "section[GroupCashRegister]", "documentsClosedDate[GroupCashRegister]");
                for (int i = 0; i < groupCashRegisterProperties.length; i++) {
                    query.addProperty(groupCashRegisterNames[i], groupCashRegisterProperties[i].getExpr(groupCashRegisterExpr));
                }

                query.and(cashRegisterLM.findProperty("handlerModel[GroupMachinery]").getExpr(groupCashRegisterExpr).getWhere());
                //query.and(cashRegisterLM.findProperty("overDirectoryMachinery").getExpr(cashRegisterExpr).getWhere());
                query.and(cashRegisterLM.findProperty("groupMachinery[Machinery]").getExpr(cashRegisterExpr).compare(groupCashRegisterExpr, Compare.EQUALS));
                query.and(cashRegisterLM.findProperty("sidEquipmentServer[GroupMachinery]").getExpr(groupCashRegisterExpr).compare(new DataObject(sidEquipmentServer), Compare.EQUALS));
                query.and(cashRegisterLM.findProperty("active[GroupCashRegister]").getExpr(groupCashRegisterExpr).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    CashRegisterInfo c = new CashRegisterInfo((Integer) row.get("nppGroupMachinery"), (Integer) row.get("nppMachinery"),
                            null, (String) row.get("handlerModelGroupMachinery"), trim((String) row.get("portMachinery")),
                            trim((String) row.get("overDirectoryMachinery")), (Integer) row.get("overDepartmentNumberGroupCashRegister"),
                            (String) row.get("idStockGroupMachinery"), row.get("disableSalesCashRegister") != null, (String) row.get("pieceCodeGroupCashRegister"),
                            (String) row.get("weightCodeGroupCashRegister"), (String) row.get("section"), (Date) row.get("documentsClosedDate"));
                    cashRegisterInfoList.add(c);
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return cashRegisterInfoList;
    }

    public static Set<String> readCashDocumentSet(DBManager dbManager, EquipmentServer server) throws IOException, SQLException {
        Set<String> cashDocumentSet = new HashSet<>();
        if (cashOperationLM != null) {
            try (DataSession session = server.createSession()) {

                KeyExpr cashDocumentExpr = new KeyExpr("cashDocument");
                ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "CashDocument", cashDocumentExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
                query.addProperty("idCashDocument", cashOperationLM.findProperty("id[CashDocument]").getExpr(cashDocumentExpr));
                query.and(cashOperationLM.findProperty("id[CashDocument]").getExpr(cashDocumentExpr).getWhere());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    cashDocumentSet.add((String) row.get("idCashDocument"));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return cashDocumentSet;
    }

    public static String sendCashDocumentInfo(BusinessLogics BL, DBManager dbManager, EquipmentServer server, ExecutionStack stack, List<CashDocument> cashDocumentList) throws IOException, SQLException {
        if (cashOperationLM != null && cashDocumentList != null) {

            try {

                List<ImportField> fieldsIncome = new ArrayList<>();
                List<ImportField> fieldsOutcome = new ArrayList<>();

                List<ImportProperty<?>> propsIncome = new ArrayList<>();
                List<ImportProperty<?>> propsOutcome = new ArrayList<>();

                List<ImportKey<?>> keysIncome = new ArrayList<>();
                List<ImportKey<?>> keysOutcome = new ArrayList<>();

                List<List<Object>> dataIncome = new ArrayList<>();
                List<List<Object>> dataOutcome = new ArrayList<>();

                ImportField idCashDocumentField = new ImportField(cashOperationLM.findProperty("id[CashDocument]"));

                ImportKey<?> incomeCashOperationKey = new ImportKey((CustomClass) cashOperationLM.findClass("IncomeCashOperation"),
                        cashOperationLM.findProperty("cashDocument[VARSTRING[100]]").getMapping(idCashDocumentField));
                keysIncome.add(incomeCashOperationKey);
                propsIncome.add(new ImportProperty(idCashDocumentField, cashOperationLM.findProperty("id[CashDocument]").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(idCashDocumentField);

                ImportKey<?> outcomeCashOperationKey = new ImportKey((CustomClass) cashOperationLM.findClass("OutcomeCashOperation"),
                        cashOperationLM.findProperty("cashDocument[VARSTRING[100]]").getMapping(idCashDocumentField));
                keysOutcome.add(outcomeCashOperationKey);
                propsOutcome.add(new ImportProperty(idCashDocumentField, cashOperationLM.findProperty("id[CashDocument]").getMapping(outcomeCashOperationKey)));
                fieldsOutcome.add(idCashDocumentField);

                ImportField numberIncomeCashOperationField = new ImportField(cashOperationLM.findProperty("number[IncomeCashOperation]"));
                propsIncome.add(new ImportProperty(numberIncomeCashOperationField, cashOperationLM.findProperty("number[IncomeCashOperation]").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(numberIncomeCashOperationField);

                ImportField numberOutcomeCashOperationField = new ImportField(cashOperationLM.findProperty("number[OutcomeCashOperation]"));
                propsOutcome.add(new ImportProperty(numberOutcomeCashOperationField, cashOperationLM.findProperty("number[OutcomeCashOperation]").getMapping(outcomeCashOperationKey)));
                fieldsOutcome.add(numberOutcomeCashOperationField);

                ImportField dateIncomeCashOperationField = new ImportField(cashOperationLM.findProperty("date[IncomeCashOperation]"));
                propsIncome.add(new ImportProperty(dateIncomeCashOperationField, cashOperationLM.findProperty("date[IncomeCashOperation]").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(dateIncomeCashOperationField);

                ImportField dateOutcomeCashOperationField = new ImportField(cashOperationLM.findProperty("date[OutcomeCashOperation]"));
                propsOutcome.add(new ImportProperty(dateOutcomeCashOperationField, cashOperationLM.findProperty("date[OutcomeCashOperation]").getMapping(outcomeCashOperationKey)));
                fieldsOutcome.add(dateOutcomeCashOperationField);

                ImportField timeIncomeCashOperationField = new ImportField(cashOperationLM.findProperty("time[IncomeCashOperation]"));
                propsIncome.add(new ImportProperty(timeIncomeCashOperationField, cashOperationLM.findProperty("time[IncomeCashOperation]").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(timeIncomeCashOperationField);

                ImportField timeOutcomeCashOperationField = new ImportField(cashOperationLM.findProperty("time[OutcomeCashOperation]"));
                propsOutcome.add(new ImportProperty(timeOutcomeCashOperationField, cashOperationLM.findProperty("time[OutcomeCashOperation]").getMapping(outcomeCashOperationKey)));
                fieldsOutcome.add(timeOutcomeCashOperationField);

                ImportField nppGroupMachineryField = new ImportField(cashOperationLM.findProperty("npp[GroupMachinery]"));
                ImportField nppMachineryField = new ImportField(cashOperationLM.findProperty("npp[Machinery]"));
                ImportKey<?> cashRegisterKey = new ImportKey((ConcreteCustomClass) cashOperationLM.findClass("CashRegister"),
                        cashOperationLM.findProperty("cashRegisterNppGroupCashRegister[INTEGER,INTEGER]").getMapping(nppGroupMachineryField, nppMachineryField/*, sidEquipmentServerField*/));

                keysIncome.add(cashRegisterKey);
                propsIncome.add(new ImportProperty(nppMachineryField, cashOperationLM.findProperty("cashRegister[IncomeCashOperation]").getMapping(incomeCashOperationKey),
                        cashOperationLM.object(cashOperationLM.findClass("CashRegister")).getMapping(cashRegisterKey)));
                fieldsIncome.add(nppGroupMachineryField);
                fieldsIncome.add(nppMachineryField);

                keysOutcome.add(cashRegisterKey);
                propsOutcome.add(new ImportProperty(nppMachineryField, cashOperationLM.findProperty("cashRegister[OutcomeCashOperation]").getMapping(outcomeCashOperationKey),
                        cashOperationLM.object(cashOperationLM.findClass("CashRegister")).getMapping(cashRegisterKey)));
                fieldsOutcome.add(nppGroupMachineryField);
                fieldsOutcome.add(nppMachineryField);

                ImportField sumCashIncomeCashOperationField = new ImportField(cashOperationLM.findProperty("sumCash[IncomeCashOperation]"));
                propsIncome.add(new ImportProperty(sumCashIncomeCashOperationField, cashOperationLM.findProperty("sumCash[IncomeCashOperation]").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(sumCashIncomeCashOperationField);

                ImportField sumCashOutcomeCashOperationField = new ImportField(cashOperationLM.findProperty("sumCash[OutcomeCashOperation]"));
                propsOutcome.add(new ImportProperty(sumCashOutcomeCashOperationField, cashOperationLM.findProperty("sumCash[OutcomeCashOperation]").getMapping(outcomeCashOperationKey)));
                fieldsOutcome.add(sumCashOutcomeCashOperationField);

                ImportField idEmployeeField = new ImportField(cashOperationLM.findProperty("id[Employee]"));
                ImportKey<?> employeeKey = new ImportKey((CustomClass) cashOperationLM.findClass("Employee"), cashOperationLM.findProperty("employee[VARSTRING[100]]").getMapping(idEmployeeField));

                keysIncome.add(employeeKey);
                propsIncome.add(new ImportProperty(idEmployeeField, cashOperationLM.findProperty("id[Employee]").getMapping(employeeKey)));
                propsIncome.add(new ImportProperty(idEmployeeField, cashOperationLM.findProperty("employee[IncomeCashOperation]").getMapping(incomeCashOperationKey),
                        cashOperationLM.object(cashOperationLM.findClass("Employee")).getMapping(employeeKey)));
                fieldsIncome.add(idEmployeeField);

                keysOutcome.add(employeeKey);
                propsOutcome.add(new ImportProperty(idEmployeeField, cashOperationLM.findProperty("id[Employee]").getMapping(employeeKey)));
                propsOutcome.add(new ImportProperty(idEmployeeField, cashOperationLM.findProperty("employee[OutcomeCashOperation]").getMapping(outcomeCashOperationKey),
                        cashOperationLM.object(cashOperationLM.findClass("Employee")).getMapping(employeeKey)));
                fieldsOutcome.add(idEmployeeField);

                ImportField idZReportField = new ImportField(cashOperationLM.findProperty("id[ZReport]"));
                ImportKey<?> zReportKey = new ImportKey((ConcreteCustomClass) cashOperationLM.findClass("ZReport"), cashOperationLM.findProperty("zReport[VARSTRING[100]]").getMapping(idZReportField));
                zReportKey.skipKey = true;

                keysIncome.add(zReportKey);
                propsIncome.add(new ImportProperty(idZReportField, cashOperationLM.findProperty("zReport[IncomeCashOperation]").getMapping(incomeCashOperationKey),
                        cashOperationLM.object(cashOperationLM.findClass("ZReport")).getMapping(zReportKey)));
                fieldsIncome.add(idZReportField);

                keysOutcome.add(zReportKey);
                propsOutcome.add(new ImportProperty(idZReportField, cashOperationLM.findProperty("zReport[OutcomeCashOperation]").getMapping(outcomeCashOperationKey),
                        cashOperationLM.object(cashOperationLM.findClass("ZReport")).getMapping(zReportKey)));
                fieldsOutcome.add(idZReportField);

                for (CashDocument cashDocument : cashDocumentList) {
                    if (cashDocument.sumCashDocument != null) {
                        String idZReport = cashDocument.nppGroupMachinery + "_" + cashDocument.nppMachinery + "_" + cashDocument.numberZReport + "_" + new SimpleDateFormat("ddMMyyyy").format(cashDocument.dateCashDocument);
                        if (cashDocument.sumCashDocument.compareTo(BigDecimal.ZERO) >= 0)
                            dataIncome.add(Arrays.asList((Object) cashDocument.idCashDocument, cashDocument.numberCashDocument, cashDocument.dateCashDocument,
                                    cashDocument.timeCashDocument, cashDocument.nppGroupMachinery, cashDocument.nppMachinery, cashDocument.sumCashDocument, cashDocument.idEmployee, idZReport));
                        else
                            dataOutcome.add(Arrays.asList((Object) cashDocument.idCashDocument, cashDocument.numberCashDocument, cashDocument.dateCashDocument,
                                    cashDocument.timeCashDocument, cashDocument.nppGroupMachinery, cashDocument.nppMachinery, cashDocument.sumCashDocument.negate(), cashDocument.idEmployee, idZReport));
                    }
                }


                ImportTable table = new ImportTable(fieldsIncome, dataIncome);
                String resultIncome;
                try (DataSession session = server.createSession()) {
                    session.pushVolatileStats("ES_CDI");
                    IntegrationService service = new IntegrationService(session, table, keysIncome, propsIncome);
                    service.synchronize(true, false);
                    resultIncome = session.applyMessage(BL, stack);
                    session.popVolatileStats();
                }
                if(resultIncome != null)
                    return resultIncome;

                table = new ImportTable(fieldsOutcome, dataOutcome);
                String resultOutcome;

                try (DataSession session = server.createSession()) {
                    session.pushVolatileStats("ES_CDI");
                    IntegrationService service = new IntegrationService(session, table, keysOutcome, propsOutcome);
                    service.synchronize(true, false);
                    resultOutcome = session.applyMessage(BL, stack);
                    session.popVolatileStats();
                }

                return resultOutcome;
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        } else return null;
    }

    public static Map<String, List<Object>> readRequestZReportSumMap(BusinessLogics BL, DBManager dbManager, EquipmentServer server, ExecutionStack stack, String idStock, Date dateFrom, Date dateTo) {
        Map<String, List<Object>> zReportSumMap = new HashMap<>();
        if (zReportLM != null && equipmentCashRegisterLM != null) {
            try (DataSession session = server.createSession()) {

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

                session.applyException(BL, stack);
            } catch (ScriptingErrorLog.SemanticErrorException | SQLException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return zReportSumMap;
    }

    public static Map<String, BigDecimal> readZReportSumMap(DBManager dbManager, EquipmentServer server) throws RemoteException, SQLException {
        Map<String, BigDecimal> zReportSumMap = new HashMap<>();
        if (zReportLM != null) {
            try (DataSession session = server.createSession()) {

                KeyExpr zReportExpr = new KeyExpr("zReport");

                ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "ZReport", zReportExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

                query.addProperty("idZReport", zReportLM.findProperty("id[ZReport]").getExpr(zReportExpr));
                query.addProperty("sumReceiptDetailZReport", zReportLM.findProperty("sumReceiptDetail[ZReport]").getExpr(zReportExpr));

                query.and(zReportLM.findProperty("id[ZReport]").getExpr(zReportExpr).getWhere());
                query.and(zReportLM.findProperty("succeededExtraCheck[ZReport]").getExpr(zReportExpr).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    zReportSumMap.put((String) row.get("idZReport"), (BigDecimal) row.get("sumReceiptDetailZReport"));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return zReportSumMap;
    }

    public static void succeedExtraCheckZReport(BusinessLogics BL, DBManager dbManager, EquipmentServer server, ExecutionStack stack, List<String> idZReportList) throws RemoteException, SQLException {
        if (zReportLM != null) {
            try {
                for (String idZReport : idZReportList) {
                    try (DataSession session = server.createSession()) {
                        zReportLM.findProperty("succeededExtraCheck[ZReport]").change(true, session, (DataObject) zReportLM.findProperty("zReport[VARSTRING[100]]").readClasses(session, new DataObject(idZReport)));
                        session.applyException(BL, stack);
                    }
                }

            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    public static void logRequestZReportSumCheck(DBManager dbManager, EquipmentServer server, BusinessLogics BL, ExecutionStack stack, Long idRequestExchange, Integer nppGroupMachinery, List<List<Object>> checkSumResult) throws RemoteException, SQLException {
        if (machineryPriceTransactionLM != null && cashRegisterLM != null && EquipmentServer.notNullNorEmpty(checkSumResult)) {
            try (DataSession session = server.createSession()) {
                for (List<Object> entry : checkSumResult) {
                    Object nppMachinery = entry.get(0);
                    Object message = entry.get(1);
                    DataObject logObject = session.addObject((ConcreteCustomClass) machineryPriceTransactionLM.findClass("RequestExchangeLog"));
                    ObjectValue cashRegisterObject = cashRegisterLM.findProperty("cashRegisterNppGroupCashRegister[INTEGER,INTEGER]").readClasses(session, new DataObject(nppGroupMachinery), new DataObject((Integer) nppMachinery));
                    machineryPriceTransactionLM.findProperty("date[RequestExchangeLog]").change(EquipmentServer.getCurrentTimestamp(), session, logObject);
                    machineryPriceTransactionLM.findProperty("message[RequestExchangeLog]").change((String)message, session, logObject);
                    machineryPriceTransactionLM.findProperty("machinery[RequestExchangeLog]").change(cashRegisterObject, session, logObject);
                    machineryPriceTransactionLM.findProperty("requestExchange[RequestExchangeLog]").change(idRequestExchange, session, logObject);
                }
                session.applyException(BL, stack);
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    public static Map<Integer, List<List<Object>>> readCashRegistersStock(DBManager dbManager, EquipmentServer server, String idStock) throws RemoteException, SQLException {
        Map<Integer, List<List<Object>>> cashRegisterList = new HashMap<>();
        if(equipmentCashRegisterLM != null)
            try (DataSession session = server.createSession()) {

                DataObject stockObject = (DataObject) equipmentCashRegisterLM.findProperty("stock[VARSTRING[100]]").readClasses(session, new DataObject(idStock));

                KeyExpr cashRegisterExpr = new KeyExpr("cashRegister");
                ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "cashRegister", cashRegisterExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

                String[] machineryNames = new String[] {"nppMachinery", "nppGroupMachineryMachinery", "overDirectoryMachinery"};
                LCP[] machineryProperties = equipmentCashRegisterLM.findProperties("npp[Machinery]", "nppGroupMachinery[Machinery]",
                        "overDirectory[Machinery]");
                for (int i = 0; i < machineryProperties.length; i++) {
                    query.addProperty(machineryNames[i], machineryProperties[i].getExpr(cashRegisterExpr));
                }

                query.and(equipmentCashRegisterLM.findProperty("departmentStore[CashRegister]").getExpr(cashRegisterExpr).compare(stockObject.getExpr(), Compare.EQUALS));
                query.and(equipmentCashRegisterLM.findProperty("npp[Machinery]").getExpr(cashRegisterExpr).getWhere());
                query.and(equipmentCashRegisterLM.findProperty("active[CashRegister]").getExpr(cashRegisterExpr).getWhere());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> zReportResult = query.execute(session);
                for (ImMap<Object, Object> entry : zReportResult.values()) {
                    Integer nppMachinery = (Integer) entry.get("nppMachinery");
                    Integer nppGroupMachinery = (Integer) entry.get("nppGroupMachineryMachinery");
                    String overDirectoryMachinery = trim((String) entry.get("overDirectoryMachinery"));
                    if(nppMachinery != null && nppGroupMachinery != null && overDirectoryMachinery != null) {
                        List<List<Object>> nppMachineryList = cashRegisterList.containsKey(nppGroupMachinery) ? cashRegisterList.get(nppGroupMachinery) : new ArrayList<List<Object>>();
                        nppMachineryList.add(Arrays.asList((Object) nppMachinery, overDirectoryMachinery));
                        cashRegisterList.put(nppGroupMachinery, nppMachineryList);
                    }
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }

        return cashRegisterList;
    }

    public static List<Integer> readAllowReceiptsAfterDocumentsClosedDateCashRegisterList(DBManager dbManager, EquipmentServer server) {
        List<Integer> cashRegisterList = new ArrayList<>();
        if (equipmentCashRegisterLM != null) {
            try (DataSession session = server.createSession()) {

                KeyExpr groupCashRegisterExpr = new KeyExpr("groupCashRegister");
                ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "groupCashRegister", groupCashRegisterExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
                query.addProperty("npp", equipmentCashRegisterLM.findProperty("npp[GroupCashRegister]").getExpr(groupCashRegisterExpr));
                query.and(equipmentCashRegisterLM.findProperty("allowReceiptsAfterDocumentsClosedDate[GroupCashRegister]").getExpr(groupCashRegisterExpr).getWhere());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> zReportResult = query.execute(session);
                for (ImMap<Object, Object> entry : zReportResult.values()) {
                    cashRegisterList.add((Integer) entry.get("npp"));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return cashRegisterList;
    }

}