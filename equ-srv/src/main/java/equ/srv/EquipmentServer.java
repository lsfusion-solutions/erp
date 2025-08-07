package equ.srv;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItem;
import equ.api.scales.TransactionScalesInfo;
import equ.api.stoplist.StopListInfo;
import equ.api.terminal.*;
import lsfusion.base.BaseUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.col.interfaces.mutable.MSet;
import lsfusion.base.file.RawFileData;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.base.controller.lifecycle.LifecycleEvent;
import lsfusion.server.base.controller.remote.RmiManager;
import lsfusion.server.base.controller.remote.manager.RmiServer;
import lsfusion.server.base.controller.thread.ExecutorFactory;
import lsfusion.server.base.controller.thread.ThreadLocalContext;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.expr.value.ValueExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.LogicsInstance;
import lsfusion.server.logics.action.controller.stack.ExecutionStack;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.logics.classes.data.StringClass;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.classes.user.CustomClass;
import lsfusion.server.physics.dev.integration.service.*;
import lsfusion.server.physics.exec.db.controller.manager.DBManager;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.InitializingBean;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.rmi.RemoteException;
import java.rmi.server.ServerNotActiveException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static java.rmi.server.RemoteServer.getClientHost;
import static lsfusion.erp.integration.DefaultIntegrationAction.*;
import static org.apache.commons.lang3.StringUtils.trim;

public class EquipmentServer extends RmiServer implements EquipmentServerInterface, InitializingBean {
    private static final Logger logger = EquipmentLoggers.equipmentLogger;

    public static final String EXPORT_NAME = "EquipmentServer";

    private LogicsInstance logicsInstance;

    private SoftCheckInterface softCheck;

    private PromotionInterface promotionInterface;
    
    private ScriptingLogicsModule equLM;
    
    public DataSession createSession() throws SQLException {
        return super.createSession();
    }

    //Опциональные модули
    private ScriptingLogicsModule cashRegisterLM;
    private ScriptingLogicsModule cashRegisterItemLM;
    private ScriptingLogicsModule equipmentLM;
    private ScriptingLogicsModule giftCardLM;
    private ScriptingLogicsModule itemLM;
    private ScriptingLogicsModule itemFashionLM;
    private ScriptingLogicsModule machineryLM;
    private ScriptingLogicsModule machineryPriceTransactionLM;
    private ScriptingLogicsModule machineryPriceTransactionBatchLM;
    private ScriptingLogicsModule machineryPriceTransactionSectionLM;
    private ScriptingLogicsModule machineryPriceTransactionBalanceLM;
    private ScriptingLogicsModule machineryPriceTransactionPartLM;
    private ScriptingLogicsModule machineryPriceTransactionPromotionLM;
    private ScriptingLogicsModule machineryPriceTransactionStockTaxLM;
    private ScriptingLogicsModule priceCheckerLM;
    private ScriptingLogicsModule scalesLM;
    private ScriptingLogicsModule scalesItemLM;
    private ScriptingLogicsModule terminalLM;
    private ScriptingLogicsModule zReportLM;
    private ScriptingLogicsModule zReportDiscountCardLM;
    private ScriptingLogicsModule zReportSectionLM;
    private ScriptingLogicsModule zReportLotLM;
    private ScriptingLogicsModule zReportExternalLM;
    private ScriptingLogicsModule zReportExternalNumberLM;
    private ScriptingLogicsModule zReportBonusLM;
    private ScriptingLogicsModule zReportBatchLM;

    private boolean started = false;

    private Map<String, Integer> skipTroubleCounter = new HashMap<>();

    public void setLogicsInstance(LogicsInstance logicsInstance) {
        this.logicsInstance = logicsInstance;
    }

    public LogicsInstance getLogicsInstance() {
        return logicsInstance;
    }

    public void setSoftCheckHandler(SoftCheckInterface softCheck) {
        this.softCheck = softCheck;
    }

    public SoftCheckInterface getSoftCheckHandler() {
        return softCheck;
    }

    public void setPromotionHandler(PromotionInterface promotionInterface) {
        this.promotionInterface = promotionInterface;
    }   
    
    public RmiManager getRmiManager() {
        return logicsInstance.getRmiManager();
    }

    public BusinessLogics getBusinessLogics() {
        return logicsInstance.getBusinessLogics();
    }

    public DBManager getDbManager() {
        return logicsInstance.getDbManager();
    }

    @Override
    public void afterPropertiesSet() {
    }

    @Override
    protected void onInit(LifecycleEvent event) {
        equLM = getBusinessLogics().getModule("Equipment");
//        Assert.notNull(equLM, "can't find Equipment module");
        cashRegisterLM = getBusinessLogics().getModule("EquipmentCashRegister");
        cashRegisterItemLM = getBusinessLogics().getModule("CashRegisterItem");
        equipmentLM = getBusinessLogics().getModule("Equipment");
        giftCardLM = getBusinessLogics().getModule("GiftCard");
        itemLM = getBusinessLogics().getModule("Item");
        itemFashionLM = getBusinessLogics().getModule("ItemFashion");
        machineryLM = getBusinessLogics().getModule("Machinery");
        machineryPriceTransactionLM = getBusinessLogics().getModule("MachineryPriceTransaction");
        machineryPriceTransactionBatchLM = getBusinessLogics().getModule("MachineryPriceTransactionBatch");
        machineryPriceTransactionSectionLM = getBusinessLogics().getModule("MachineryPriceTransactionSection");
        machineryPriceTransactionBalanceLM = getBusinessLogics().getModule("MachineryPriceTransactionBalance");
        machineryPriceTransactionPartLM = getBusinessLogics().getModule("MachineryPriceTransactionPart");
        machineryPriceTransactionPromotionLM = getBusinessLogics().getModule("MachineryPriceTransactionPromotion");
        machineryPriceTransactionStockTaxLM = getBusinessLogics().getModule("MachineryPriceTransactionStockTax");
        priceCheckerLM = getBusinessLogics().getModule("EquipmentPriceChecker");
        scalesLM = getBusinessLogics().getModule("EquipmentScales");
        scalesItemLM = getBusinessLogics().getModule("ScalesItem");
        terminalLM = getBusinessLogics().getModule("EquipmentTerminal");
        zReportLM = getBusinessLogics().getModule("ZReport");
        zReportDiscountCardLM = getBusinessLogics().getModule("ZReportDiscountCard");
        zReportSectionLM = getBusinessLogics().getModule("ZReportSection");
        zReportExternalLM = getBusinessLogics().getModule("ZReportExternal");
        zReportExternalNumberLM = getBusinessLogics().getModule("ZReportExternalNumber");
        zReportBonusLM = getBusinessLogics().getModule("ZReportBonus");
        zReportBatchLM = getBusinessLogics().getModule("ZReportBatch");
        zReportLotLM = getBusinessLogics().getModule("ZReportLot");
        DeleteBarcodeEquipmentServer.init(getBusinessLogics());
        MachineryExchangeEquipmentServer.init(getBusinessLogics());
        SendSalesEquipmentServer.init(getBusinessLogics());
        StopListEquipmentServer.init(getBusinessLogics());
        TerminalDocumentEquipmentServer.init(getBusinessLogics());
        ProcessMonitorEquipmentServer.init(getBusinessLogics());
    }

    @Override
    protected void onStarted(LifecycleEvent event) {
        if(getDbManager().isServer()) {
            startEquipmentServer();
        } else {
            logger.info("Equipment Server disabled, change serverComputer() to enable");
        }
    }

    @Override
    protected void onStopping(LifecycleEvent event) {
        stopEquipmentServer();
    }

    public void restartEquipmentServer() {
        stopEquipmentServer();
        startEquipmentServer();
    }

    private void startEquipmentServer() {
        logger.info("Starting Equipment Server");
        try {
            getRmiManager().bindAndExport(EXPORT_NAME, this);
            started = true;
        } catch (Exception e) {
            throw new RuntimeException("Error starting Equipment Server: ", e);
        }
    }

    private void stopEquipmentServer() {
        if (started) {
            logger.info("Stopping Equipment Server");
            try {
                getRmiManager().unbindAndUnexport(EXPORT_NAME, this);
                started = false;
            } catch (Exception e) {
                throw new RuntimeException("Error stopping Equipment Server: ", e);
            }
        }
    }

    public EquipmentServer() {
        super(DAEMON_ORDER);
    }

    @Override
    public int getEquApiVersion() {
        return EquipmentUtils.getEquApiVersion();
    }

    private static String getIdZReport(SalesInfo s) {
        return s.nppGroupMachinery + "_" + s.nppMachinery + "_" + s.numberZReport + (s.dateZReport != null ? ("_" + s.dateZReport.format(DateTimeFormatter.ofPattern("ddMMyyyy"))) : "");
    }

    @Override
    public String sendCashierTimeList(List<CashierTime> cashierTimeList) throws RemoteException {
        return softCheck == null ? null : softCheck.sendCashierTimeList(cashierTimeList);
    }

    @Override
    public String sendExtraData(String extraData) throws RemoteException, SQLException {
        if(cashRegisterLM != null) {
            try (DataSession session = createSession()) {
                ExecutionStack stack = getStack();
                cashRegisterLM.findAction("processExtraData[STRING]").execute(session, stack, new DataObject(extraData));
                return session.applyMessage(getBusinessLogics(), stack);
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return null;
    }

    private List<Integer> readTroubleMachineryGroups(DataSession session, Integer minutes) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<Integer> result = new ArrayList<>();
        if(minutes != null) {
            KeyExpr groupMachineryExpr = new KeyExpr("GroupMachinery");
            ImRevMap<Object, KeyExpr> groupMachineryKeys = MapFact.singletonRev("groupMachinery", groupMachineryExpr);
            QueryBuilder<Object, Object> groupMachineryQuery = new QueryBuilder<>(groupMachineryKeys);

            String[] groupMachineryNames = new String[]{"npp", "lastErrorTime"};
            LP[] groupMachineryProperties = machineryPriceTransactionLM.findProperties("npp[GroupMachinery]", "lastErrorTime[GroupMachinery]");
            for (int i = 0; i < groupMachineryProperties.length; i++) {
                groupMachineryQuery.addProperty(groupMachineryNames[i], groupMachineryProperties[i].getExpr(groupMachineryExpr));
            }
            groupMachineryQuery.and(machineryPriceTransactionLM.findProperty("lastErrorTime[GroupMachinery]").getExpr(groupMachineryExpr).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> groupMachineryResult = groupMachineryQuery.execute(session);

            LocalDateTime minTime = LocalDateTime.now().minusMinutes(minutes);
            for (ImMap<Object, Object> row : groupMachineryResult.valueIt()) {
                Integer npp = (Integer) row.get("npp");
                LocalDateTime lastErrorTime = (LocalDateTime) row.get("lastErrorTime");
                if (lastErrorTime != null && lastErrorTime.compareTo(minTime) <= 0)
                    result.add(npp);
            }
        }
        return result;
    }

    private Map<String, List<ItemGroup>> readItemGroupMap(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, List<ItemGroup>> result = new HashMap<>();
        Map<String, ItemGroup> itemGroupMap = new HashMap<>();
        
        KeyExpr itemGroupExpr = new KeyExpr("ItemGroup");        
        ImRevMap<Object, KeyExpr> itemGroupKeys = MapFact.singletonRev("itemGroup", itemGroupExpr);
        QueryBuilder<Object, Object> itemGroupQuery = new QueryBuilder<>(itemGroupKeys);

        String[] itemGroupNames = new String[] {"idItemGroup", "overIdItemGroup", "nameItemGroup", "idParentItemGroup"};
        LP[] itemGroupProperties = itemLM.findProperties("id[ItemGroup]", "overId[ItemGroup]", "name[ItemGroup]", "idParent[ItemGroup]");
        for (int i = 0; i < itemGroupProperties.length; i++) {
            itemGroupQuery.addProperty(itemGroupNames[i], itemGroupProperties[i].getExpr(itemGroupExpr));
        }
        
        itemGroupQuery.and(itemLM.findProperty("id[ItemGroup]").getExpr(itemGroupExpr).getWhere());
        
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> itemGroupResult = itemGroupQuery.execute(session);

        for (ImMap<Object, Object> row : itemGroupResult.valueIt()) {
            String idItemGroup = (String) row.get("idItemGroup");
            String overIdItemGroup = (String) row.get("overIdItemGroup");
            String nameItemGroup = (String) row.get("nameItemGroup");
            String idParentItemGroup = (String) row.get("idParentItemGroup");
            itemGroupMap.put(overIdItemGroup, new ItemGroup(idItemGroup, overIdItemGroup, nameItemGroup, idParentItemGroup));
        }
        
        for(Map.Entry<String, ItemGroup> entry : itemGroupMap.entrySet()) {
            List<ItemGroup> hierarchy = new ArrayList<>(Collections.singletonList(entry.getValue()));
            String idParent = entry.getValue().idParentItemGroup;
            while(itemGroupMap.containsKey(idParent)) {
                ItemGroup parentItemGroup = itemGroupMap.get(idParent);
                hierarchy.add(parentItemGroup);
                idParent = parentItemGroup.idParentItemGroup;
            }
            result.put(entry.getKey(), hierarchy);
        }
        return result;
    }

    @Override
    public List<DiscountCard> readDiscountCardList(RequestExchange requestExchange) {
        return MachineryExchangeEquipmentServer.readDiscountCardList(this, getBusinessLogics(), getStack(), requestExchange);
    }

    @Override
    public List<CashierInfo> readCashierInfoList() throws SQLException {
        return MachineryExchangeEquipmentServer.readCashierInfoList(this);
    }

    private void logSourceHost(String requestType) {
        try {
            logger.info(requestType + " request from host: " + getClientHost());
        } catch (ServerNotActiveException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public boolean enabledStopListInfo() {
        return StopListEquipmentServer.enabledStopListInfo();
    }

    @Override
    public List<StopListInfo> readStopListInfo() throws SQLException {
        logSourceHost("readStopList");
        return StopListEquipmentServer.readStopListInfo(this);
    }

    @Override
    public boolean enabledDeleteBarcodeInfo() {
        return DeleteBarcodeEquipmentServer.enabledDeleteBarcodeInfo();
    }

    @Override
    public List<DeleteBarcodeInfo> readDeleteBarcodeInfoList() throws SQLException {
        return DeleteBarcodeEquipmentServer.readDeleteBarcodeInfo(this);
    }

    @Override
    public void errorDeleteBarcodeReport(Integer nppGroupMachinery, Exception exception) {
        DeleteBarcodeEquipmentServer.errorDeleteBarcodeReport(getBusinessLogics(), this, getStack(), nppGroupMachinery, exception);
    }

    @Override
    public void finishDeleteBarcode(Integer nppGroupMachinery, boolean markSucceeded) {
        DeleteBarcodeEquipmentServer.finishDeleteBarcode(getBusinessLogics(), this, getStack(), nppGroupMachinery, markSucceeded);
    }

    @Override
    public void succeedDeleteBarcode(Integer nppGroupMachinery, Set<String> deleteBarcodeSet) {
        DeleteBarcodeEquipmentServer.succeedDeleteBarcode(getBusinessLogics(), this, getStack(), nppGroupMachinery, deleteBarcodeSet);
    }

    @Override
    public void errorStopListReport(String numberStopList, Exception e) {
        StopListEquipmentServer.errorStopListReport(getBusinessLogics(), getStack(), this, numberStopList, e);
    }

    @Override
    public void succeedStopList(String numberStopList, Set<String> idStockSet) {
        StopListEquipmentServer.succeedStopList(getBusinessLogics(), getStack(), this, numberStopList, idStockSet);
    }

    @Override
    public List<TerminalOrder> readTerminalOrderList(RequestExchange requestExchange) throws SQLException {
        return MachineryExchangeEquipmentServer.readTerminalOrderList(this, requestExchange);
    }

    @Override
    public List<RequestExchange> readRequestExchange(String sidEquipmentServer) throws SQLException {
        return MachineryExchangeEquipmentServer.readRequestExchange(this, sidEquipmentServer, getBusinessLogics(), getStack());
    }

    @Override
    public void finishRequestExchange(String sidEquipmentServer, Set<Long> succeededRequestsSet) throws SQLException {
        MachineryExchangeEquipmentServer.finishRequestExchange(this, sidEquipmentServer, getBusinessLogics(), getStack(), succeededRequestsSet);
    }

    @Override
    public void errorRequestExchange(Map<Long, Throwable> failedRequestsMap) throws SQLException {
        MachineryExchangeEquipmentServer.errorRequestExchange(this, getBusinessLogics(), getStack(), failedRequestsMap);
    }

    @Override
    public void errorRequestExchange(Long requestExchange, Throwable t) throws SQLException {
        MachineryExchangeEquipmentServer.errorRequestExchange(this, getBusinessLogics(), getStack(), requestExchange, t);
    }

    @Override
    public Map<String, BigDecimal> readZReportSumMap(Set<String> idZReportSet) throws SQLException {
        return SendSalesEquipmentServer.readZReportSumMap(this, idZReportSet);
    }
    
    @Override
    public void succeedExtraCheckZReport(Set<String> idZReportSet) throws SQLException {
        SendSalesEquipmentServer.succeedExtraCheckZReport(getBusinessLogics(), this, getStack(), idZReportSet);
    }

    @Override
    public List<CashRegisterInfo> readCashRegisterInfo(String sidEquipmentServer) throws SQLException {
        return SendSalesEquipmentServer.readCashRegisterInfo(this, sidEquipmentServer);
    }

    @Override
    public boolean enabledTerminalInfo() {
        return terminalLM != null;
    }

    @Override
    public List<TerminalInfo> readTerminalInfo(String sidEquipmentServer) throws SQLException {
        return TerminalDocumentEquipmentServer.readTerminalInfo(this, sidEquipmentServer);
    }

    @Override
    public String sendTerminalInfo(List<TerminalDocumentDetail> terminalDocumentDetailList) {
        return TerminalDocumentEquipmentServer.sendTerminalInfo(getBusinessLogics(), this, getStack(), terminalDocumentDetailList);
    }

    @Override
    public List<MachineryInfo> readMachineryInfo(String sidEquipmentServer) throws SQLException {
        return MachineryExchangeEquipmentServer.readMachineryInfo(this, sidEquipmentServer);
    }

    @Override
    public void logRequestZReportSumCheck(Long idRequestExchange, Integer nppGroupMachinery, List<List<Object>> checkSumResult) {
        SendSalesEquipmentServer.logRequestZReportSumCheck(this, getBusinessLogics(), getStack(), idRequestExchange, nppGroupMachinery, checkSumResult);
    }

    @Override
    public Map<Integer, List<List<Object>>> readCashRegistersStock(String idStock) {
        return SendSalesEquipmentServer.readCashRegistersStock(this, idStock);
    }

    @Override
    public PromotionInfo readPromotionInfo() throws RemoteException {
        return promotionInterface == null ? null : promotionInterface.readPromotionInfo();
    }

    @Override
    public String sendSalesInfo(List<SalesInfo> salesInfoList, String sidEquipmentServer, String directory) {
        return sendSalesInfoNonRemote(getStack(), salesInfoList, sidEquipmentServer, directory);
    }


    public String sendSalesInfoNonRemote(ExecutionStack stack, List<SalesInfo> salesInfoList, String sidEquipmentServer, String directory) {
        try {

            if (zReportLM != null && notNullNorEmpty(salesInfoList)) {

                salesInfoList.sort(COMPARATOR);

                try (DataSession outerSession = createSession()) {

                    EquipmentServerOptions options = readEquipmentServerOptions(sidEquipmentServer, outerSession);
                    if (options.skipReceiveSales)
                        return "Временно отключен прием реализации";

                    //временная опция для Табака
                    if(cashRegisterLM.findProperty("disableSalesForClosedZReports[]").read(outerSession) != null) {
                        Set<String> closedZReportSet = readClosedZReportSet(outerSession);
                        salesInfoList.removeIf(salesInfo -> closedZReportSet.contains(getIdZReport(salesInfo, options)));
                    }

                    if (options.maxThreads == null || options.maxThreads <= 1)
                        return importSalesInfoSingleThread(stack, sidEquipmentServer, directory, salesInfoList, options);
                    else {
                        return importSalesInfoMultiThread(stack, sidEquipmentServer, directory, salesInfoList, options);
                    }
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return null;
    }

    private String importSalesInfoMultiThread(final ExecutionStack stack, final String sidEquipmentServer, final String directory, final List<SalesInfo> salesInfoList, EquipmentServerOptions options) throws ExecutionException {
        String result = null;

        if (options.numberAtATime == null) {
            options.numberAtATime = salesInfoList.size();
        }

        final List<Integer> allowReceiptsAfterDocumentsClosedDateCashRegisterList = SendSalesEquipmentServer.readAllowReceiptsAfterDocumentsClosedDateCashRegisterList(this);

        ExecutorService executor = ExecutorFactory.createRMIThreadService(options.maxThreads, EquipmentServer.this);
        List<Future<String>> futures = new ArrayList<>();

        final List<List<SalesInfo>> groupedSalesInfo = groupSalesInfoByNppGroupMachinery(salesInfoList);

        final int taskSize = groupedSalesInfo.size();
        for (int i = 0; i < taskSize; i++) {
            final int taskIndex = i;
            Future<String> importResult = executor.submit((Callable) () ->
                    runMultithreadTask(stack, groupedSalesInfo.get(taskIndex), sidEquipmentServer, taskIndex, taskSize,
                            directory, options, allowReceiptsAfterDocumentsClosedDateCashRegisterList));
            futures.add(importResult);
        }

        executor.shutdown();

        try {
            for (Future<String> future : futures) {
                String futureResult = future.get();
                if (result == null && futureResult != null) {
                    result = futureResult;
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            logger.info("Interrupted ImportSalesInfo executor");
            for (Future<String> future : futures) {
                boolean done = future.isDone();
                boolean canceled = future.cancel(true);
                if(!done) {
                    logger.info("Interrupted ImportSalesInfo thread: " + canceled);
                }
            }
            throw Throwables.propagate(e);
        }
        return result;
    }

    private String runMultithreadTask(ExecutionStack stack, List<SalesInfo> salesInfoList, String sidEquipmentServer, int taskIndex, int taskSize, final String directory,
                                      EquipmentServerOptions options, List<Integer> allowReceiptsAfterDocumentsClosedDateCashRegisterList)
            throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        int start = 0;
        while (start < salesInfoList.size()) {
            int finish = Math.min((start + options.numberAtATime), salesInfoList.size());

            Integer lastNumberReceipt = start < finish ? salesInfoList.get(finish - 1).numberReceipt : null;
            if (lastNumberReceipt != null) {
                while (start < finish && salesInfoList.size() > finish && salesInfoList.get(finish).numberReceipt.equals(lastNumberReceipt))
                    finish++;
            }

            if (start < finish) {
                String result = importSalesInfo(stack, sidEquipmentServer, salesInfoList, start, finish, salesInfoList.size() - finish, taskIndex, taskSize, directory, options, allowReceiptsAfterDocumentsClosedDateCashRegisterList);
                if(result != null) {
                    return result;
                }
            }
            start = finish;
        }
        return null;
    }

    private String importSalesInfo(ExecutionStack stack, String sidEquipmentServer, List<SalesInfo> salesInfoList, int start, int finish, int left,
                                   int taskIndex, int taskSize, String directory, EquipmentServerOptions options, List<Integer> allowReceiptsAfterDocumentsClosedDateCashRegisterList)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        try (DataSession session = createSession()) {
            logger.info(String.format("Sending SalesInfo from %s to %s (%s)", start, finish, !salesInfoList.isEmpty() ? (salesInfoList.get(0).nppGroupMachinery + " - " + salesInfoList.get(0).nppMachinery) : ""));

            LocalDateTime timeStart = LocalDateTime.now();

            ObjectValue equipmentServerObject = equLM.findProperty("sidTo[STRING[20]]").readClasses(session, new DataObject(sidEquipmentServer));

            Set<String> settingsSet = new HashSet<>();
            KeyExpr settingExpr = new KeyExpr("setting");
            QueryBuilder<Object, Object> settingQuery = new QueryBuilder<>(MapFact.singletonRev("setting", settingExpr));
            settingQuery.addProperty("name", equLM.findProperty("name[Setting]").getExpr(settingExpr));
            settingQuery.addProperty("overValue", equLM.findProperty("overValue[EquipmentServer, Setting]").getExpr(equipmentServerObject.getExpr(), settingExpr));
            settingQuery.and(equLM.findProperty("overValue[EquipmentServer, Setting]").getExpr(equipmentServerObject.getExpr(), settingExpr).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> itemResult = settingQuery.execute(session);
            for (ImMap<Object, Object> entry : itemResult.values()) {
                String settingName = (String) entry.get("name");
                ThreadLocalContext.pushSettings(settingName, (String) entry.get("overValue"));
                settingsSet.add(settingName);
            }

            List<ImportProperty<?>> saleProperties = new ArrayList<>();
            List<ImportKey<?>> saleKeys = new ArrayList<>();

            List<ImportProperty<?>> returnProperties = new ArrayList<>();
            List<ImportKey<?>> returnKeys = new ArrayList<>();

            List<ImportProperty<?>> giftCardProperties = new ArrayList<>();
            List<ImportKey<?>> giftCardKeys = new ArrayList<>();

            //commonZReportFields
            ImportField nppGroupMachineryField = new ImportField(zReportLM.findProperty("npp[GroupMachinery]"));
            ImportField nppMachineryField = new ImportField(zReportLM.findProperty("npp[Machinery]"));
            ImportField idZReportField = new ImportField(zReportLM.findProperty("id[ZReport]"));
            ImportField numberZReportField = new ImportField(zReportLM.findProperty("number[ZReport]"));
            ImportField dateZReportField = new ImportField(zReportLM.findProperty("date[ZReport]"));
            ImportField timeZReportField = new ImportField(zReportLM.findProperty("time[ZReport]"));
            ImportField sumCashEndZReportField = new ImportField(zReportLM.findProperty("sumCashEnd[ZReport]"));
            ImportField sumProtectedEndZReportField = new ImportField(zReportLM.findProperty("sumProtectedEnd[ZReport]"));
            ImportField sumBackZReportField = new ImportField(zReportLM.findProperty("sumBack[ZReport]"));
            ImportField isPostedZReportField = new ImportField(zReportLM.findProperty("isPosted[ZReport]"));

            ImportKey<?> cashRegisterKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("CashRegister"), zReportLM.findProperty("cashRegisterNppGroupCashRegister[INTEGER,INTEGER]").getMapping(nppGroupMachineryField, nppMachineryField));
            cashRegisterKey.skipKey = true;
            saleKeys.add(cashRegisterKey);
            returnKeys.add(cashRegisterKey);
            giftCardKeys.add(cashRegisterKey);

            ImportKey<?> zReportKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ZReport"), zReportLM.findProperty("zReport[STRING[100]]").getMapping(idZReportField));
            saleKeys.add(zReportKey);
            returnKeys.add(zReportKey);
            giftCardKeys.add(zReportKey);

            List<ImportField> commonZReportFields = Arrays.asList(nppGroupMachineryField, nppMachineryField, idZReportField, numberZReportField,
                    dateZReportField, timeZReportField, sumCashEndZReportField, sumProtectedEndZReportField, sumBackZReportField, isPostedZReportField);


            //commonReceiptFields
            ImportField idReceiptField = new ImportField(zReportLM.findProperty("id[Receipt]"));
            ImportField numberReceiptField = new ImportField(zReportLM.findProperty("number[Receipt]"));
            ImportField dateReceiptField = new ImportField(zReportLM.findProperty("date[Receipt]"));
            ImportField timeReceiptField = new ImportField(zReportLM.findProperty("time[Receipt]"));
            ImportField skipReceiptField = new ImportField(zReportLM.findProperty("dataSkip[Receipt]"));
            ImportField externalNumberReceiptField = zReportExternalNumberLM != null ? new ImportField(zReportExternalNumberLM.findProperty("externalNumber[Receipt]")) : null;

            ImportKey<?> receiptKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("Receipt"), zReportLM.findProperty("receipt[STRING[100]]").getMapping(idReceiptField));
            saleKeys.add(receiptKey);
            returnKeys.add(receiptKey);
            giftCardKeys.add(receiptKey);

            List<ImportField> commonReceiptFields = getCommonReceiptFields(idReceiptField, numberReceiptField, dateReceiptField, timeReceiptField, skipReceiptField, externalNumberReceiptField);

            List<ImportField> saleFields = new ArrayList<>(commonZReportFields);
            saleFields.addAll(commonReceiptFields);
            List<ImportField> returnFields = new ArrayList<>(commonZReportFields);
            returnFields.addAll(commonReceiptFields);
            List<ImportField> giftCardFields = new ArrayList<>(commonZReportFields);
            giftCardFields.addAll(commonReceiptFields);

            ImportField idEmployeeField = new ImportField(zReportLM.findProperty("id[Employee]"));
            saleFields.add(idEmployeeField);
            returnFields.add(idEmployeeField);
            giftCardFields.add(idEmployeeField);

            ImportKey<?> employeeKey = new ImportKey((CustomClass) zReportLM.findClass("Employee"), zReportLM.findProperty("employee[STRING[100]]").getMapping(idEmployeeField));
            saleKeys.add(employeeKey);
            returnKeys.add(employeeKey);
            giftCardKeys.add(employeeKey);

            ImportField firstNameContactField = new ImportField(zReportLM.findProperty("firstName[Contact]"));
            saleFields.add(firstNameContactField);
            returnFields.add(firstNameContactField);
            giftCardFields.add(firstNameContactField);

            ImportField lastNameContactField = new ImportField(zReportLM.findProperty("lastName[Contact]"));
            saleFields.add(lastNameContactField);
            returnFields.add(lastNameContactField);
            giftCardFields.add(lastNameContactField);

            ImportField idReceiptDetailField = new ImportField(zReportLM.findProperty("id[ReceiptDetail]"));
            saleFields.add(idReceiptDetailField);
            returnFields.add(idReceiptDetailField);
            giftCardFields.add(idReceiptDetailField);

            ImportField numberReceiptDetailField = new ImportField(zReportLM.findProperty("number[ReceiptDetail]"));
            saleFields.add(numberReceiptDetailField);
            returnFields.add(numberReceiptDetailField);
            giftCardFields.add(numberReceiptDetailField);

            ImportField idBarcodeReceiptDetailField = new ImportField(zReportLM.findProperty("idBarcode[ReceiptDetail]"));
            saleFields.add(idBarcodeReceiptDetailField);
            returnFields.add(idBarcodeReceiptDetailField);

            ImportKey<?> skuKey = new ImportKey((CustomClass) zReportLM.findClass("Sku"), zReportLM.findProperty("skuBarcode[STRING[15],DATE]").getMapping(idBarcodeReceiptDetailField, dateReceiptField));
            saleKeys.add(skuKey);
            returnKeys.add(skuKey);

            ImportKey<?> receiptSaleDetailKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ReceiptSaleDetail"), zReportLM.findProperty("receiptDetail[STRING[100]]").getMapping(idReceiptDetailField));
            ImportKey<?> receiptReturnDetailKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ReceiptReturnDetail"), zReportLM.findProperty("receiptDetail[STRING[100]]").getMapping(idReceiptDetailField));

            //optional properties
            OptionalProperties optionalProperties = new OptionalProperties(zReportKey, receiptSaleDetailKey, receiptReturnDetailKey, options);

            //sale 1
            ImportField quantityReceiptSaleDetailField = new ImportField(zReportLM.findProperty("quantity[ReceiptSaleDetail]"));
            saleFields.add(quantityReceiptSaleDetailField);

            ImportField priceReceiptSaleDetailField = new ImportField(zReportLM.findProperty("price[ReceiptSaleDetail]"));
            saleFields.add(priceReceiptSaleDetailField);

            ImportField sumReceiptSaleDetailField = new ImportField(zReportLM.findProperty("sum[ReceiptSaleDetail]"));
            saleFields.add(sumReceiptSaleDetailField);

            ImportField discountPercentReceiptSaleDetailField = new ImportField(zReportLM.findProperty("discountPercent[ReceiptSaleDetail]"));
            saleFields.add(discountPercentReceiptSaleDetailField);

            ImportField discountSumReceiptSaleDetailField = new ImportField(zReportLM.findProperty("discountSum[ReceiptSaleDetail]"));
            saleFields.add(discountSumReceiptSaleDetailField);

            ImportField discountSumSaleReceiptField = new ImportField(zReportLM.findProperty("discountSumSale[Receipt]"));
            saleFields.add(discountSumSaleReceiptField);

            ImportField seriesNumberDiscountCardField = zReportDiscountCardLM == null ? null : new ImportField(zReportDiscountCardLM.findProperty("seriesNumber[DiscountCard]"));
            if (zReportDiscountCardLM != null) {
                saleFields.add(seriesNumberDiscountCardField);
            }
            ImportKey<?> discountCardKey = zReportDiscountCardLM == null ? null : new ImportKey((ConcreteCustomClass) zReportDiscountCardLM.findClass("DiscountCard"), zReportDiscountCardLM.findProperty("discountSeriesNumber[BPSTRING[18]]").getMapping(seriesNumberDiscountCardField));
            if (discountCardKey != null) {
                saleKeys.add(discountCardKey);
                returnKeys.add(discountCardKey);
            }

            ImportField idSectionField = zReportSectionLM == null ? null : new ImportField(zReportSectionLM.findProperty("id[Section]"));
            if (zReportSectionLM != null) {
                saleFields.add(idSectionField);
            }
            ImportKey<?> sectionKey = zReportSectionLM == null ? null : new ImportKey((ConcreteCustomClass) zReportSectionLM.findClass("Section"), zReportSectionLM.findProperty("section[STRING[100]]").getMapping(idSectionField));
            if (sectionKey != null) {
                saleKeys.add(sectionKey);
                returnKeys.add(sectionKey);
            }
            
            //optional properties
            saleFields.addAll(optionalProperties.saleFields);
            saleKeys.addAll(optionalProperties.saleKeys);
            
            //return 1
            ImportField quantityReceiptReturnDetailField = new ImportField(zReportLM.findProperty("quantity[ReceiptReturnDetail]"));
            returnFields.add(quantityReceiptReturnDetailField);

            ImportField priceReceiptReturnDetailField = new ImportField(zReportLM.findProperty("price[ReceiptReturnDetail]"));
            returnFields.add(priceReceiptReturnDetailField);

            ImportField retailSumReceiptReturnDetailField = new ImportField(zReportLM.findProperty("sum[ReceiptReturnDetail]"));
            returnFields.add(retailSumReceiptReturnDetailField);

            ImportField discountSumReceiptReturnDetailField = new ImportField(zReportLM.findProperty("discountSum[ReceiptReturnDetail]"));
            returnFields.add(discountSumReceiptReturnDetailField);

            ImportField discountSumReturnReceiptField = new ImportField(zReportLM.findProperty("discountSumReturn[Receipt]"));
            returnFields.add(discountSumReturnReceiptField);

            ImportField idSaleReceiptReceiptReturnDetailField = new ImportField(zReportLM.findProperty("id[Receipt]"));
            returnFields.add(idSaleReceiptReceiptReturnDetailField);

            if (zReportDiscountCardLM != null) {
                returnFields.add(seriesNumberDiscountCardField);
            }

            if (zReportSectionLM != null) {
                returnFields.add(idSectionField);
            }

            //optional properties
            returnFields.addAll(optionalProperties.returnFields);
            returnKeys.addAll(optionalProperties.returnKeys);

            //giftCard 1
            ImportField priceReceiptGiftCardSaleDetailField = null;
            ImportField sumReceiptGiftCardSaleDetailField = null;
            ImportField idGiftCardField = null;
            ImportKey<?> giftCardKey = null;
            ImportField isReturnReceiptGiftCardSaleDetailField = null;
            if (giftCardLM != null) {
                idGiftCardField = new ImportField(giftCardLM.findProperty("id[GiftCard]"));
                giftCardFields.add(idGiftCardField);

                priceReceiptGiftCardSaleDetailField = new ImportField(giftCardLM.findProperty("price[ReceiptGiftCardSaleDetail]"));
                giftCardFields.add(priceReceiptGiftCardSaleDetailField);

                sumReceiptGiftCardSaleDetailField = new ImportField(giftCardLM.findProperty("sum[ReceiptGiftCardSaleDetail]"));
                giftCardFields.add(sumReceiptGiftCardSaleDetailField);

                giftCardKey = new ImportKey((ConcreteCustomClass) giftCardLM.findClass("GiftCard"), giftCardLM.findProperty("giftCard[STRING[100]]").getMapping(idGiftCardField));
                giftCardKey.skipKey = options.skipGiftCardKeys;
                giftCardKeys.add(giftCardKey);

                isReturnReceiptGiftCardSaleDetailField = new ImportField(giftCardLM.findProperty("isReturn[ReceiptGiftCardSaleDetail]"));
                giftCardFields.add(isReturnReceiptGiftCardSaleDetailField);

                if (zReportSectionLM != null) {
                    giftCardFields.add(idSectionField);
                }

                //optional properties
                giftCardFields.addAll(optionalProperties.giftCardFields);
                giftCardKeys.addAll(optionalProperties.giftCardKeys);
            }

            List<ImportProperty<?>> commonProperties = getCommonProperties(nppMachineryField, idZReportField, numberZReportField, dateZReportField,
                    timeZReportField, sumCashEndZReportField, sumProtectedEndZReportField, sumBackZReportField, isPostedZReportField, zReportKey, cashRegisterKey,
                    idReceiptField, numberReceiptField, dateReceiptField, timeReceiptField, skipReceiptField, externalNumberReceiptField, receiptKey, options);

            //sale 2
            saleProperties.addAll(commonProperties);

            saleProperties.add(new ImportProperty(discountSumSaleReceiptField, zReportLM.findProperty("discountSumSale[Receipt]").getMapping(receiptKey)));
            saleProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("zReport[Receipt]").getMapping(receiptKey),
                    zReportLM.object(zReportLM.findClass("ZReport")).getMapping(zReportKey)));
            if (zReportDiscountCardLM != null) {
                saleProperties.add(new ImportProperty(seriesNumberDiscountCardField, zReportDiscountCardLM.findProperty("number[DiscountCard]").getMapping(discountCardKey), true));
                saleProperties.add(new ImportProperty(seriesNumberDiscountCardField, zReportDiscountCardLM.findProperty("discountCard[Receipt]").getMapping(receiptKey),
                        zReportDiscountCardLM.object(zReportDiscountCardLM.findClass("DiscountCard")).getMapping(discountCardKey)));
            }
            saleProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("id[Employee]").getMapping(employeeKey)));
            saleProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("employee[Receipt]").getMapping(receiptKey),
                    zReportLM.object(zReportLM.findClass("Employee")).getMapping(employeeKey)));
            saleProperties.add(new ImportProperty(firstNameContactField, zReportLM.findProperty("firstName[Contact]").getMapping(employeeKey), !options.overrideCashiers));
            saleProperties.add(new ImportProperty(lastNameContactField, zReportLM.findProperty("lastName[Contact]").getMapping(employeeKey), !options.overrideCashiers));

            saleKeys.add(receiptSaleDetailKey);

            saleProperties.add(new ImportProperty(idReceiptDetailField, zReportLM.findProperty("id[ReceiptDetail]").getMapping(receiptSaleDetailKey)));
            saleProperties.add(new ImportProperty(numberReceiptDetailField, zReportLM.findProperty("number[ReceiptDetail]").getMapping(receiptSaleDetailKey)));
            saleProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("idBarcode[ReceiptDetail]").getMapping(receiptSaleDetailKey)));
            saleProperties.add(new ImportProperty(quantityReceiptSaleDetailField, zReportLM.findProperty("quantity[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey)));
            saleProperties.add(new ImportProperty(priceReceiptSaleDetailField, zReportLM.findProperty("price[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey)));
            saleProperties.add(new ImportProperty(sumReceiptSaleDetailField, zReportLM.findProperty("sum[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey)));

            saleProperties.add(new ImportProperty(discountPercentReceiptSaleDetailField, zReportLM.findProperty("discountPercent[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey)));

            saleProperties.add(new ImportProperty(discountSumReceiptSaleDetailField, zReportLM.findProperty("discountSum[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey)));

            saleProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("receipt[ReceiptDetail]").getMapping(receiptSaleDetailKey),
                    zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptKey)));

            saleProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("sku[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey),
                    zReportLM.object(zReportLM.findClass("Sku")).getMapping(skuKey)));

            if (zReportSectionLM != null) {
                saleProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("id[Section]").getMapping(sectionKey), true));
                saleProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("section[ReceiptDetail]").getMapping(receiptSaleDetailKey),
                        zReportSectionLM.object(zReportSectionLM.findClass("Section")).getMapping(sectionKey)));
            }

            //optional properties
            saleProperties.addAll(optionalProperties.saleProperties);

            //return 2
            returnProperties.addAll(commonProperties);

            if (zReportDiscountCardLM != null) {
                returnProperties.add(new ImportProperty(discountSumReturnReceiptField, zReportLM.findProperty("discountSumReturn[Receipt]").getMapping(receiptKey)));
            }
            returnProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("zReport[Receipt]").getMapping(receiptKey),
                    zReportLM.object(zReportLM.findClass("ZReport")).getMapping(zReportKey)));
            if (zReportDiscountCardLM != null) {
                returnProperties.add(new ImportProperty(seriesNumberDiscountCardField, zReportDiscountCardLM.findProperty("number[DiscountCard]").getMapping(discountCardKey), true));
                returnProperties.add(new ImportProperty(seriesNumberDiscountCardField, zReportDiscountCardLM.findProperty("discountCard[Receipt]").getMapping(receiptKey),
                        zReportDiscountCardLM.object(zReportDiscountCardLM.findClass("DiscountCard")).getMapping(discountCardKey)));
            }

            returnProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("id[Employee]").getMapping(employeeKey)));
            returnProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("employee[Receipt]").getMapping(receiptKey),
                    zReportLM.object(zReportLM.findClass("CustomUser")).getMapping(employeeKey)));
            returnProperties.add(new ImportProperty(firstNameContactField, zReportLM.findProperty("firstName[Contact]").getMapping(employeeKey), true));
            returnProperties.add(new ImportProperty(lastNameContactField, zReportLM.findProperty("lastName[Contact]").getMapping(employeeKey), true));

            returnKeys.add(receiptReturnDetailKey);
            returnProperties.add(new ImportProperty(idReceiptDetailField, zReportLM.findProperty("id[ReceiptDetail]").getMapping(receiptReturnDetailKey)));
            returnProperties.add(new ImportProperty(numberReceiptDetailField, zReportLM.findProperty("number[ReceiptDetail]").getMapping(receiptReturnDetailKey)));
            returnProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("idBarcode[ReceiptDetail]").getMapping(receiptReturnDetailKey)));
            returnProperties.add(new ImportProperty(quantityReceiptReturnDetailField, zReportLM.findProperty("quantity[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey)));
            returnProperties.add(new ImportProperty(priceReceiptReturnDetailField, zReportLM.findProperty("price[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey)));
            returnProperties.add(new ImportProperty(retailSumReceiptReturnDetailField, zReportLM.findProperty("sum[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey)));
            returnProperties.add(new ImportProperty(discountSumReceiptReturnDetailField, zReportLM.findProperty("discountSum[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey)));
            returnProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("receipt[ReceiptDetail]").getMapping(receiptReturnDetailKey),
                    zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptKey)));

            returnProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("sku[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey),
                    zReportLM.object(zReportLM.findClass("Sku")).getMapping(skuKey)));

            ImportKey<?> receiptSaleDetailReceiptReturnDetailKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("Receipt"), zReportLM.findProperty("receipt[STRING[100]]").getMapping(idSaleReceiptReceiptReturnDetailField));
            receiptSaleDetailReceiptReturnDetailKey.skipKey = true;
            returnKeys.add(receiptSaleDetailReceiptReturnDetailKey);
            returnProperties.add(new ImportProperty(idSaleReceiptReceiptReturnDetailField, zReportLM.findProperty("saleReceipt[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey),
                    zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptSaleDetailReceiptReturnDetailKey)));

            if (zReportSectionLM != null) {
                returnProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("id[Section]").getMapping(sectionKey), true));
                returnProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("section[ReceiptDetail]").getMapping(receiptReturnDetailKey),
                        zReportSectionLM.object(zReportSectionLM.findClass("Section")).getMapping(sectionKey)));
            }

            //optional properties
            returnProperties.addAll(optionalProperties.returnProperties);

            //giftCard 2
            ImportKey<?> receiptGiftCardSaleDetailKey = null;
            if (giftCardLM != null) {
                giftCardProperties.addAll(commonProperties);

                giftCardProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("zReport[Receipt]").getMapping(receiptKey),
                        zReportLM.object(zReportLM.findClass("ZReport")).getMapping(zReportKey)));

                giftCardProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("id[Employee]").getMapping(employeeKey)));
                giftCardProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("employee[Receipt]").getMapping(receiptKey),
                        zReportLM.object(zReportLM.findClass("CustomUser")).getMapping(employeeKey)));
                giftCardProperties.add(new ImportProperty(firstNameContactField, zReportLM.findProperty("firstName[Contact]").getMapping(employeeKey), true));
                giftCardProperties.add(new ImportProperty(lastNameContactField, zReportLM.findProperty("lastName[Contact]").getMapping(employeeKey), true));

                receiptGiftCardSaleDetailKey = new ImportKey((ConcreteCustomClass) giftCardLM.findClass("ReceiptGiftCardSaleDetail"), zReportLM.findProperty("receiptDetail[STRING[100]]").getMapping(idReceiptDetailField));
                giftCardKeys.add(receiptGiftCardSaleDetailKey);
                giftCardProperties.add(new ImportProperty(idReceiptDetailField, zReportLM.findProperty("id[ReceiptDetail]").getMapping(receiptGiftCardSaleDetailKey)));
                giftCardProperties.add(new ImportProperty(numberReceiptDetailField, zReportLM.findProperty("number[ReceiptDetail]").getMapping(receiptGiftCardSaleDetailKey)));
                giftCardProperties.add(new ImportProperty(priceReceiptGiftCardSaleDetailField, giftCardLM.findProperty("price[ReceiptGiftCardSaleDetail]").getMapping(receiptGiftCardSaleDetailKey)));
                giftCardProperties.add(new ImportProperty(sumReceiptGiftCardSaleDetailField, giftCardLM.findProperty("sum[ReceiptGiftCardSaleDetail]").getMapping(receiptGiftCardSaleDetailKey)));
                giftCardProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("receipt[ReceiptDetail]").getMapping(receiptGiftCardSaleDetailKey),
                        zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptKey)));

                giftCardProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("id[GiftCard]").getMapping(giftCardKey)));
                giftCardProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("number[GiftCard]").getMapping(giftCardKey)));
                giftCardProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("giftCard[ReceiptGiftCardSaleDetail]").getMapping(receiptGiftCardSaleDetailKey),
                        zReportLM.object(giftCardLM.findClass("GiftCard")).getMapping(giftCardKey)));
                giftCardProperties.add(new ImportProperty(isReturnReceiptGiftCardSaleDetailField, giftCardLM.findProperty("isReturn[ReceiptGiftCardSaleDetail]").getMapping(receiptGiftCardSaleDetailKey)));
            }

            if (zReportSectionLM != null) {
                giftCardProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("id[Section]").getMapping(sectionKey), true));
                giftCardProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("section[ReceiptDetail]").getMapping(receiptGiftCardSaleDetailKey),
                        zReportSectionLM.object(zReportSectionLM.findClass("Section")).getMapping(sectionKey)));
            }

            //optional properties
            giftCardProperties.addAll(optionalProperties.giftCardProperties);

            RowsData rowsData = getRowsData(session, salesInfoList, start, finish, options, allowReceiptsAfterDocumentsClosedDateCashRegisterList);

            //sale 5
            new IntegrationService(session, new ImportTable(saleFields, rowsData.dataSale), saleKeys, saleProperties).synchronize(true);

            //return 5
            new IntegrationService(session, new ImportTable(returnFields, rowsData.dataReturn), returnKeys, returnProperties).synchronize(true);

            //giftCard 5
            if (giftCardLM != null)
                new IntegrationService(session, new ImportTable(giftCardFields, rowsData.dataGiftCard), giftCardKeys, giftCardProperties).synchronize(true);

            EquipmentServerImport.importPayments(getBusinessLogics(), session, stack, salesInfoList.subList(start, finish), rowsData.ignoredIdReceipts, options);

            EquipmentServerImport.importPaymentGiftCardMultiThread(getBusinessLogics(), session, salesInfoList, rowsData.ignoredIdReceipts, start, finish, options);

            EquipmentServerImport.importDiscounts(getBusinessLogics(), session, salesInfoList.subList(start, finish), rowsData.ignoredIdReceipts, options);

            processExtraFields(session, stack, rowsData);

            session.setKeepLastAttemptCountMap(true);
            if (options.ignoreConstraints)
                session.setNoCancelInTransaction(true);
            String result = session.applyMessage(getBusinessLogics(), stack);

            for (String setting : settingsSet) {
                ThreadLocalContext.popSettings(setting);
            }

            if (result == null) {
                logCompleteMessage(stack, sidEquipmentServer, formatCompleteMessageMultiThread(session, salesInfoList, start, finish,
                        rowsData.dataSale.size() + rowsData.dataReturn.size() + rowsData.dataGiftCard.size(), left, rowsData.ignoredReceiptDetailCount, taskIndex, taskSize, timeStart, directory));
            } else
                return result;
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | CloneNotSupportedException | SQLException e) {
            logger.info(String.format("Error reading sales for (%s) - %s", (!salesInfoList.isEmpty() ? (salesInfoList.get(0).nppGroupMachinery + " - " + salesInfoList.get(0).nppMachinery) : ""), e));
            throw Throwables.propagate(e);
        }
        return null;
    }

    private String importSalesInfoSingleThread(ExecutionStack stack, String sidEquipmentServer, String directory, List<SalesInfo> salesInfoList, EquipmentServerOptions options) throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        for (int start = 0; true;) {

            try (DataSession session = createSession()) {

                if(start > 0) {
                    options = readEquipmentServerOptions(sidEquipmentServer, session);
                }
                if (options.numberAtATime == null) {
                    options.numberAtATime = salesInfoList.size();
                }

                ObjectValue equipmentServerObject = equLM.findProperty("sidTo[STRING[20]]").readClasses(session, new DataObject(sidEquipmentServer));

                List<Integer> allowReceiptsAfterDocumentsClosedDateCashRegisterList = SendSalesEquipmentServer.readAllowReceiptsAfterDocumentsClosedDateCashRegisterList(this);

                LocalDateTime timeStart = LocalDateTime.now();

                int finish = (start + options.numberAtATime) < salesInfoList.size() ? (start + options.numberAtATime) : salesInfoList.size();

                Integer lastNumberReceipt = start < finish ? salesInfoList.get(finish - 1).numberReceipt : null;
                if (lastNumberReceipt != null) {
                    while (start < finish && salesInfoList.size() > finish && salesInfoList.get(finish).numberReceipt.equals(lastNumberReceipt))
                        finish++;
                }

                List<SalesInfo> data = start < finish ? salesInfoList.subList(start, finish) : new ArrayList<>();
                start = finish;
                int left = salesInfoList.size() - finish;
                if (!notNullNorEmpty(data))
                    return null;

                logger.info(String.format("Sending SalesInfo from %s to %s", start, finish));

                Set<String> settingsSet = new HashSet<>();
                KeyExpr settingExpr = new KeyExpr("setting");
                QueryBuilder<Object, Object> settingQuery = new QueryBuilder<>(MapFact.singletonRev("setting", settingExpr));
                settingQuery.addProperty("name", equLM.findProperty("name[Setting]").getExpr(settingExpr));
                settingQuery.addProperty("overValue", equLM.findProperty("overValue[EquipmentServer, Setting]").getExpr(equipmentServerObject.getExpr(), settingExpr));
                settingQuery.and(equLM.findProperty("overValue[EquipmentServer, Setting]").getExpr(equipmentServerObject.getExpr(), settingExpr).getWhere());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> itemResult = settingQuery.execute(session);
                for (ImMap<Object, Object> entry : itemResult.values()) {
                    String settingName = (String) entry.get("name");
                    ThreadLocalContext.pushSettings(settingName, (String) entry.get("overValue"));
                    settingsSet.add(settingName);
                }

                List<ImportKey<?>> saleKeys = new ArrayList<>();

                ImportField idReceiptDetailField = new ImportField(zReportLM.findProperty("id[ReceiptDetail]"));
                ImportKey<?> receiptSaleDetailKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ReceiptSaleDetail"), zReportLM.findProperty("receiptDetail[STRING[100]]").getMapping(idReceiptDetailField));
                ImportKey<?> receiptReturnDetailKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ReceiptReturnDetail"), zReportLM.findProperty("receiptDetail[STRING[100]]").getMapping(idReceiptDetailField));

                //commonZReportFields
                ImportField nppGroupMachineryField = new ImportField(zReportLM.findProperty("npp[GroupMachinery]"));
                ImportField nppMachineryField = new ImportField(zReportLM.findProperty("npp[Machinery]"));
                ImportField idZReportField = new ImportField(zReportLM.findProperty("id[ZReport]"));
                ImportField numberZReportField = new ImportField(zReportLM.findProperty("number[ZReport]"));
                ImportField dateZReportField = new ImportField(zReportLM.findProperty("date[ZReport]"));
                ImportField timeZReportField = new ImportField(zReportLM.findProperty("time[ZReport]"));
                ImportField sumCashEndZReportField = new ImportField(zReportLM.findProperty("sumCashEnd[ZReport]"));
                ImportField sumProtectedEndZReportField = new ImportField(zReportLM.findProperty("sumProtectedEnd[ZReport]"));
                ImportField sumBackZReportField = new ImportField(zReportLM.findProperty("sumBack[ZReport]"));
                ImportField isPostedZReportField = new ImportField(zReportLM.findProperty("isPosted[ZReport]"));

                List<ImportField> commonZReportFields = Arrays.asList(nppGroupMachineryField, nppMachineryField, idZReportField, numberZReportField,
                        dateZReportField, timeZReportField, sumCashEndZReportField, sumProtectedEndZReportField, sumBackZReportField, isPostedZReportField);


                //commonReceiptFields
                ImportField idReceiptField = new ImportField(zReportLM.findProperty("id[Receipt]"));
                ImportField numberReceiptField = new ImportField(zReportLM.findProperty("number[Receipt]"));
                ImportField dateReceiptField = new ImportField(zReportLM.findProperty("date[Receipt]"));
                ImportField timeReceiptField = new ImportField(zReportLM.findProperty("time[Receipt]"));
                ImportField skipReceiptField = new ImportField(zReportLM.findProperty("dataSkip[Receipt]"));
                ImportField externalNumberReceiptField = zReportExternalNumberLM != null ? new ImportField(zReportExternalNumberLM.findProperty("externalNumber[Receipt]")) : null;

                List<ImportField> commonReceiptFields = getCommonReceiptFields(idReceiptField, numberReceiptField, dateReceiptField, timeReceiptField, skipReceiptField, externalNumberReceiptField);


                ImportField idEmployeeField = new ImportField(zReportLM.findProperty("id[Employee]"));
                ImportField firstNameContactField = new ImportField(zReportLM.findProperty("firstName[Contact]"));
                ImportField lastNameContactField = new ImportField(zReportLM.findProperty("lastName[Contact]"));
                
                ImportField numberReceiptDetailField = new ImportField(zReportLM.findProperty("number[ReceiptDetail]"));
                ImportField idBarcodeReceiptDetailField = new ImportField(zReportLM.findProperty("idBarcode[ReceiptDetail]"));

                //sale 1
                ImportField quantityReceiptSaleDetailField = new ImportField(zReportLM.findProperty("quantity[ReceiptSaleDetail]"));
                ImportField priceReceiptSaleDetailField = new ImportField(zReportLM.findProperty("price[ReceiptSaleDetail]"));
                ImportField sumReceiptSaleDetailField = new ImportField(zReportLM.findProperty("sum[ReceiptSaleDetail]"));
                ImportField discountSumSaleReceiptField = new ImportField(zReportLM.findProperty("discountSumSale[Receipt]"));

                //return 1
                ImportField idSaleReceiptReceiptReturnDetailField = new ImportField(zReportLM.findProperty("id[Receipt]"));
                ImportField quantityReceiptReturnDetailField = new ImportField(zReportLM.findProperty("quantity[ReceiptReturnDetail]"));
                ImportField priceReceiptReturnDetailField = new ImportField(zReportLM.findProperty("price[ReceiptReturnDetail]"));
                ImportField retailSumReceiptReturnDetailField = new ImportField(zReportLM.findProperty("sum[ReceiptReturnDetail]"));
                ImportField discountSumReceiptReturnDetailField = new ImportField(zReportLM.findProperty("discountSum[ReceiptReturnDetail]"));
                ImportField discountSumReturnReceiptField = new ImportField(zReportLM.findProperty("discountSumReturn[Receipt]"));

                //giftCard 1
                ImportField priceReceiptGiftCardSaleDetailField = null;
                ImportField sumReceiptGiftCardSaleDetailField = null;
                ImportField idGiftCardField = null;
                ImportField isReturnReceiptGiftCardSaleDetailField = null;
                if (giftCardLM != null) {
                    priceReceiptGiftCardSaleDetailField = new ImportField(giftCardLM.findProperty("price[ReceiptGiftCardSaleDetail]"));
                    sumReceiptGiftCardSaleDetailField = new ImportField(giftCardLM.findProperty("sum[ReceiptGiftCardSaleDetail]"));
                    idGiftCardField = new ImportField(giftCardLM.findProperty("id[GiftCard]"));
                    isReturnReceiptGiftCardSaleDetailField = new ImportField(giftCardLM.findProperty("isReturn[ReceiptGiftCardSaleDetail]"));
                }

                ImportField seriesNumberDiscountCardField = zReportDiscountCardLM == null ? null : new ImportField(zReportDiscountCardLM.findProperty("seriesNumber[DiscountCard]"));
                ImportField idSectionField = zReportSectionLM == null ? null : new ImportField(zReportSectionLM.findProperty("id[Section]"));

                List<ImportProperty<?>> saleProperties = new ArrayList<>();
                List<ImportProperty<?>> returnProperties = new ArrayList<>();
                List<ImportProperty<?>> giftCardProperties = new ArrayList<>();

                ImportKey<?> zReportKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ZReport"), zReportLM.findProperty("zReport[STRING[100]]").getMapping(idZReportField));
                saleKeys.add(zReportKey);

                //optional properties
                OptionalProperties optionalProperties = new OptionalProperties(zReportKey, receiptSaleDetailKey, receiptReturnDetailKey, options);

                ImportKey<?> cashRegisterKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("CashRegister"), zReportLM.findProperty("cashRegisterNppGroupCashRegister[INTEGER,INTEGER]").getMapping(nppGroupMachineryField, nppMachineryField));
                cashRegisterKey.skipKey = true;
                saleKeys.add(cashRegisterKey);

                ImportKey<?> receiptKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("Receipt"), zReportLM.findProperty("receipt[STRING[100]]").getMapping(idReceiptField));
                saleKeys.add(receiptKey);

                ImportKey<?> skuKey = new ImportKey((CustomClass) zReportLM.findClass("Sku"), zReportLM.findProperty("skuBarcode[STRING[15],DATE]").getMapping(idBarcodeReceiptDetailField, dateReceiptField));
                saleKeys.add(skuKey);

                ImportKey<?> employeeKey = new ImportKey((CustomClass) zReportLM.findClass("Employee"), zReportLM.findProperty("employee[STRING[100]]").getMapping(idEmployeeField));
                saleKeys.add(employeeKey);

                ImportKey<?> discountCardKey = zReportDiscountCardLM == null ? null : new ImportKey((ConcreteCustomClass) zReportDiscountCardLM.findClass("DiscountCard"), zReportDiscountCardLM.findProperty("discountSeriesNumber[BPSTRING[18]]").getMapping(seriesNumberDiscountCardField, dateReceiptField));
                if(discountCardKey != null)
                    saleKeys.add(discountCardKey);

                ImportKey<?> giftCardKey = null;
                if(giftCardLM != null) {
                    giftCardKey = new ImportKey((ConcreteCustomClass) giftCardLM.findClass("GiftCard"), giftCardLM.findProperty("giftCard[STRING[100]]").getMapping(idGiftCardField));
                    giftCardKey.skipKey = options.skipGiftCardKeys;
                }

                ImportKey<?> sectionKey = zReportSectionLM == null ? null : new ImportKey((ConcreteCustomClass) zReportSectionLM.findClass("Section"), zReportSectionLM.findProperty("section[STRING[100]]").getMapping(idSectionField));
                if(sectionKey != null)
                    saleKeys.add(sectionKey);

                //optional properties
                saleKeys.addAll(optionalProperties.saleKeys);

                List<ImportProperty<?>> commonProperties = getCommonProperties(nppMachineryField, idZReportField, numberZReportField, dateZReportField,
                        timeZReportField, sumCashEndZReportField, sumProtectedEndZReportField, sumBackZReportField, isPostedZReportField, zReportKey, cashRegisterKey,
                        idReceiptField, numberReceiptField, dateReceiptField, timeReceiptField, skipReceiptField, externalNumberReceiptField, receiptKey, options);

                //sale 2
                saleProperties.addAll(commonProperties);

                saleProperties.add(new ImportProperty(discountSumSaleReceiptField, zReportLM.findProperty("discountSumSale[Receipt]").getMapping(receiptKey)));
                saleProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("zReport[Receipt]").getMapping(receiptKey),
                        zReportLM.object(zReportLM.findClass("ZReport")).getMapping(zReportKey)));
                if (zReportDiscountCardLM != null) {
                    saleProperties.add(new ImportProperty(seriesNumberDiscountCardField, zReportDiscountCardLM.findProperty("number[DiscountCard]").getMapping(discountCardKey), true));
                    saleProperties.add(new ImportProperty(seriesNumberDiscountCardField, zReportDiscountCardLM.findProperty("discountCard[Receipt]").getMapping(receiptKey),
                            zReportDiscountCardLM.object(zReportDiscountCardLM.findClass("DiscountCard")).getMapping(discountCardKey)));
                }
                saleProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("id[Employee]").getMapping(employeeKey)));
                saleProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("employee[Receipt]").getMapping(receiptKey),
                        zReportLM.object(zReportLM.findClass("Employee")).getMapping(employeeKey)));
                saleProperties.add(new ImportProperty(firstNameContactField, zReportLM.findProperty("firstName[Contact]").getMapping(employeeKey), !options.overrideCashiers));
                saleProperties.add(new ImportProperty(lastNameContactField, zReportLM.findProperty("lastName[Contact]").getMapping(employeeKey), !options.overrideCashiers));

                saleKeys.add(receiptSaleDetailKey);

                saleProperties.add(new ImportProperty(idReceiptDetailField, zReportLM.findProperty("id[ReceiptDetail]").getMapping(receiptSaleDetailKey)));
                saleProperties.add(new ImportProperty(numberReceiptDetailField, zReportLM.findProperty("number[ReceiptDetail]").getMapping(receiptSaleDetailKey)));
                saleProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("idBarcode[ReceiptDetail]").getMapping(receiptSaleDetailKey)));
                saleProperties.add(new ImportProperty(quantityReceiptSaleDetailField, zReportLM.findProperty("quantity[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey)));
                saleProperties.add(new ImportProperty(priceReceiptSaleDetailField, zReportLM.findProperty("price[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey)));
                saleProperties.add(new ImportProperty(sumReceiptSaleDetailField, zReportLM.findProperty("sum[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey)));

                ImportField discountPercentReceiptSaleDetailField = new ImportField(zReportLM.findProperty("discountPercent[ReceiptSaleDetail]"));
                saleProperties.add(new ImportProperty(discountPercentReceiptSaleDetailField, zReportLM.findProperty("discountPercent[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey)));

                ImportField discountSumReceiptSaleDetailField = new ImportField(zReportLM.findProperty("discountSum[ReceiptSaleDetail]"));
                saleProperties.add(new ImportProperty(discountSumReceiptSaleDetailField, zReportLM.findProperty("discountSum[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey)));

                saleProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("receipt[ReceiptDetail]").getMapping(receiptSaleDetailKey),
                        zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptKey)));

                saleProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("sku[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey),
                        zReportLM.object(zReportLM.findClass("Sku")).getMapping(skuKey)));

                if(zReportSectionLM != null) {
                    saleProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("id[Section]").getMapping(sectionKey), true));
                    saleProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("section[ReceiptDetail]").getMapping(receiptSaleDetailKey),
                            zReportSectionLM.object(zReportSectionLM.findClass("Section")).getMapping(sectionKey)));
                }

                //optional properties
                saleProperties.addAll(optionalProperties.saleProperties);

                //return 2
                returnProperties.addAll(commonProperties);

                if (zReportDiscountCardLM != null) {
                    returnProperties.add(new ImportProperty(discountSumReturnReceiptField, zReportLM.findProperty("discountSumReturn[Receipt]").getMapping(receiptKey)));
                }
                returnProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("zReport[Receipt]").getMapping(receiptKey),
                        zReportLM.object(zReportLM.findClass("ZReport")).getMapping(zReportKey)));
                if (zReportDiscountCardLM != null) {
                    returnProperties.add(new ImportProperty(seriesNumberDiscountCardField, zReportDiscountCardLM.findProperty("number[DiscountCard]").getMapping(discountCardKey), true));
                    returnProperties.add(new ImportProperty(seriesNumberDiscountCardField, zReportDiscountCardLM.findProperty("discountCard[Receipt]").getMapping(receiptKey),
                            zReportDiscountCardLM.object(zReportDiscountCardLM.findClass("DiscountCard")).getMapping(discountCardKey)));
                }

                returnProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("id[Employee]").getMapping(employeeKey)));
                returnProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("employee[Receipt]").getMapping(receiptKey),
                        zReportLM.object(zReportLM.findClass("CustomUser")).getMapping(employeeKey)));
                returnProperties.add(new ImportProperty(firstNameContactField, zReportLM.findProperty("firstName[Contact]").getMapping(employeeKey), true));
                returnProperties.add(new ImportProperty(lastNameContactField, zReportLM.findProperty("lastName[Contact]").getMapping(employeeKey), true));

                returnProperties.add(new ImportProperty(idReceiptDetailField, zReportLM.findProperty("id[ReceiptDetail]").getMapping(receiptReturnDetailKey)));
                returnProperties.add(new ImportProperty(numberReceiptDetailField, zReportLM.findProperty("number[ReceiptDetail]").getMapping(receiptReturnDetailKey)));
                returnProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("idBarcode[ReceiptDetail]").getMapping(receiptReturnDetailKey)));
                returnProperties.add(new ImportProperty(quantityReceiptReturnDetailField, zReportLM.findProperty("quantity[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey)));
                returnProperties.add(new ImportProperty(priceReceiptReturnDetailField, zReportLM.findProperty("price[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey)));
                returnProperties.add(new ImportProperty(retailSumReceiptReturnDetailField, zReportLM.findProperty("sum[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey)));
                returnProperties.add(new ImportProperty(discountSumReceiptReturnDetailField, zReportLM.findProperty("discountSum[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey)));
                returnProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("receipt[ReceiptDetail]").getMapping(receiptReturnDetailKey),
                        zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptKey)));

                returnProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("sku[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey),
                        zReportLM.object(zReportLM.findClass("Sku")).getMapping(skuKey)));

                ImportKey<?> receiptSaleDetailReceiptReturnDetailKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("Receipt"), zReportLM.findProperty("receipt[STRING[100]]").getMapping(idSaleReceiptReceiptReturnDetailField));
                receiptSaleDetailReceiptReturnDetailKey.skipKey = true;
                returnProperties.add(new ImportProperty(idSaleReceiptReceiptReturnDetailField, zReportLM.findProperty("saleReceipt[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey),
                        zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptSaleDetailReceiptReturnDetailKey)));

                if(zReportSectionLM != null) {
                    returnProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("id[Section]").getMapping(sectionKey), true));
                    returnProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("section[ReceiptDetail]").getMapping(receiptReturnDetailKey),
                            zReportSectionLM.object(zReportSectionLM.findClass("Section")).getMapping(sectionKey)));
                }

                //optional properties
                returnProperties.addAll(optionalProperties.returnProperties);

                //giftCard 2
                ImportKey<?> receiptGiftCardSaleDetailKey = null;
                if (giftCardLM != null) {
                    giftCardProperties.addAll(commonProperties);

                    giftCardProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("zReport[Receipt]").getMapping(receiptKey),
                            zReportLM.object(zReportLM.findClass("ZReport")).getMapping(zReportKey)));

                    giftCardProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("id[Employee]").getMapping(employeeKey)));
                    giftCardProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("employee[Receipt]").getMapping(receiptKey),
                            zReportLM.object(zReportLM.findClass("CustomUser")).getMapping(employeeKey)));
                    giftCardProperties.add(new ImportProperty(firstNameContactField, zReportLM.findProperty("firstName[Contact]").getMapping(employeeKey), true));
                    giftCardProperties.add(new ImportProperty(lastNameContactField, zReportLM.findProperty("lastName[Contact]").getMapping(employeeKey), true));

                    receiptGiftCardSaleDetailKey = new ImportKey((ConcreteCustomClass) giftCardLM.findClass("ReceiptGiftCardSaleDetail"), zReportLM.findProperty("receiptDetail[STRING[100]]").getMapping(idReceiptDetailField));
                    giftCardProperties.add(new ImportProperty(idReceiptDetailField, zReportLM.findProperty("id[ReceiptDetail]").getMapping(receiptGiftCardSaleDetailKey)));
                    giftCardProperties.add(new ImportProperty(numberReceiptDetailField, zReportLM.findProperty("number[ReceiptDetail]").getMapping(receiptGiftCardSaleDetailKey)));
                    giftCardProperties.add(new ImportProperty(priceReceiptGiftCardSaleDetailField, giftCardLM.findProperty("price[ReceiptGiftCardSaleDetail]").getMapping(receiptGiftCardSaleDetailKey)));
                    giftCardProperties.add(new ImportProperty(sumReceiptGiftCardSaleDetailField, giftCardLM.findProperty("sum[ReceiptGiftCardSaleDetail]").getMapping(receiptGiftCardSaleDetailKey)));
                    giftCardProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("receipt[ReceiptDetail]").getMapping(receiptGiftCardSaleDetailKey),
                            zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptKey)));

                    giftCardProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("id[GiftCard]").getMapping(giftCardKey)));
                    giftCardProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("number[GiftCard]").getMapping(giftCardKey)));
                    giftCardProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("giftCard[ReceiptGiftCardSaleDetail]").getMapping(receiptGiftCardSaleDetailKey),
                            zReportLM.object(giftCardLM.findClass("GiftCard")).getMapping(giftCardKey)));
                    giftCardProperties.add(new ImportProperty(isReturnReceiptGiftCardSaleDetailField, giftCardLM.findProperty("isReturn[ReceiptGiftCardSaleDetail]").getMapping(receiptGiftCardSaleDetailKey)));
                }

                if(zReportSectionLM != null) {
                    giftCardProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("id[Section]").getMapping(sectionKey), true));
                    giftCardProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("section[ReceiptDetail]").getMapping(receiptGiftCardSaleDetailKey),
                            zReportSectionLM.object(zReportSectionLM.findClass("Section")).getMapping(sectionKey)));
                }

                RowsData rowsData = getRowsData(session, data, 0, data.size(), options, allowReceiptsAfterDocumentsClosedDateCashRegisterList);

                //sale 4
                List<ImportField> saleImportFields = new ArrayList<>(commonZReportFields);
                saleImportFields.addAll(commonReceiptFields);
                saleImportFields.addAll(Arrays.asList(idEmployeeField, firstNameContactField, lastNameContactField,
                        idReceiptDetailField, numberReceiptDetailField, idBarcodeReceiptDetailField,
                        quantityReceiptSaleDetailField, priceReceiptSaleDetailField, sumReceiptSaleDetailField,
                        discountPercentReceiptSaleDetailField, discountSumReceiptSaleDetailField, discountSumSaleReceiptField));
                if (zReportDiscountCardLM != null) {
                    saleImportFields = new ArrayList<>(saleImportFields);
                    saleImportFields.add(seriesNumberDiscountCardField);
                }
                if (zReportSectionLM != null) {
                    saleImportFields = new ArrayList<>(saleImportFields);
                    saleImportFields.add(idSectionField);
                }
                //optional properties
                if (!optionalProperties.saleFields.isEmpty()) {
                    saleImportFields = new ArrayList<>(saleImportFields);
                    saleImportFields.addAll(optionalProperties.saleFields);
                }

                //return 4
                List<ImportField> returnImportFields = new ArrayList<>(commonZReportFields);
                returnImportFields.addAll(commonReceiptFields);
                returnImportFields.addAll(Arrays.asList(idEmployeeField, firstNameContactField, lastNameContactField,
                        idReceiptDetailField, numberReceiptDetailField, idBarcodeReceiptDetailField,
                        quantityReceiptReturnDetailField, priceReceiptReturnDetailField, retailSumReceiptReturnDetailField,
                        discountSumReceiptReturnDetailField, discountSumReturnReceiptField, idSaleReceiptReceiptReturnDetailField));
                if (zReportDiscountCardLM != null) {
                    returnImportFields = new ArrayList<>(returnImportFields);
                    returnImportFields.add(seriesNumberDiscountCardField);
                }
                if (zReportSectionLM != null) {
                    returnImportFields = new ArrayList<>(returnImportFields);
                    returnImportFields.add(idSectionField);
                }
                //optional properties
                if (optionalProperties.returnFields != null) {
                    returnImportFields = new ArrayList<>(returnImportFields);
                    returnImportFields.addAll(optionalProperties.returnFields);
                }

                //giftCard 4
                List<ImportField> giftCardImportFields = new ArrayList<>(commonZReportFields);
                giftCardImportFields.addAll(commonReceiptFields);
                giftCardImportFields.addAll(Arrays.asList(idEmployeeField, firstNameContactField, lastNameContactField,
                        idReceiptDetailField, numberReceiptDetailField, idGiftCardField,
                        priceReceiptGiftCardSaleDetailField, sumReceiptGiftCardSaleDetailField, isReturnReceiptGiftCardSaleDetailField));
                if (zReportSectionLM != null) {
                    giftCardImportFields = new ArrayList<>(giftCardImportFields);
                    giftCardImportFields.add(idSectionField);
                }
                //optional properties
                if (optionalProperties.giftCardFields != null) {
                    giftCardImportFields = new ArrayList<>(giftCardImportFields);
                    giftCardImportFields.addAll(optionalProperties.giftCardFields);
                }

                //sale 5
                new IntegrationService(session, new ImportTable(saleImportFields, rowsData.dataSale), saleKeys, saleProperties).synchronize(true);

                //return 5
                List<ImportKey<?>> returnKeys = Arrays.asList(zReportKey, cashRegisterKey, receiptKey, receiptReturnDetailKey, skuKey, employeeKey, receiptSaleDetailReceiptReturnDetailKey);
                if (zReportDiscountCardLM != null) {
                    returnKeys = new ArrayList<>(returnKeys);
                    returnKeys.add(discountCardKey);
                }
                if (zReportSectionLM != null) {
                    returnKeys = new ArrayList<>(returnKeys);
                    returnKeys.add(sectionKey);
                }
                new IntegrationService(session, new ImportTable(returnImportFields, rowsData.dataReturn), returnKeys, returnProperties).synchronize(true);

                //giftCard 5
                if (giftCardLM != null) {
                    List<ImportKey<?>> giftCardKeys = Arrays.asList(zReportKey, cashRegisterKey, receiptKey, receiptGiftCardSaleDetailKey, giftCardKey, employeeKey);
                    new IntegrationService(session, new ImportTable(giftCardImportFields, rowsData.dataGiftCard), giftCardKeys, giftCardProperties).synchronize(true);
                }

                EquipmentServerImport.importPayments(getBusinessLogics(), session, stack, data, rowsData.ignoredIdReceipts, options);

                EquipmentServerImport.importPaymentGiftCard(getBusinessLogics(), session, data, rowsData.ignoredIdReceipts, options);

                processExtraFields(session, stack, rowsData);

                session.setKeepLastAttemptCountMap(true);
                if (options.ignoreConstraints)
                    session.setNoCancelInTransaction(true);
                String result = session.applyMessage(getBusinessLogics(), stack);

                for (String setting : settingsSet) {
                    ThreadLocalContext.popSettings(setting);
                }

                if (result == null) {
                    logCompleteMessage(stack, sidEquipmentServer, formatCompleteMessageSingleThread(session, data,
                            rowsData.dataSale.size() + rowsData.dataReturn.size() + rowsData.dataGiftCard.size(), left, rowsData.ignoredReceiptDetailCount, timeStart, directory));
                } else
                    return result;
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | CloneNotSupportedException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    private List<ImportField> getCommonReceiptFields(ImportField idReceiptField, ImportField numberReceiptField, ImportField dateReceiptField,
                                                     ImportField timeReceiptField, ImportField skipReceiptField, ImportField externalNumberReceiptField) {
        List<ImportField> commonReceiptFields = new ArrayList<>(Arrays.asList(idReceiptField, numberReceiptField, dateReceiptField, timeReceiptField, skipReceiptField));
        if(zReportExternalNumberLM != null) {
            commonReceiptFields.add(externalNumberReceiptField);
        }
        return commonReceiptFields;
    }

    private List<ImportProperty<?>> getCommonProperties(ImportField nppMachineryField, ImportField idZReportField, ImportField numberZReportField, ImportField dateZReportField,
                                                        ImportField timeZReportField, ImportField sumCashEndZReportField, ImportField sumProtectedEndZReportField,
                                                        ImportField sumBackZReportField, ImportField isPostedZReportField,
                                                        ImportKey<?> zReportKey, ImportKey<?> cashRegisterKey,
                                                        ImportField idReceiptField, ImportField numberReceiptField, ImportField dateReceiptField,
                                                        ImportField timeReceiptField, ImportField skipReceiptField, ImportField externalNumberReceiptField,
                                                        ImportKey<?> receiptKey, EquipmentServerOptions options) throws ScriptingErrorLog.SemanticErrorException {

        List<ImportProperty<?>> commonProperties = new ArrayList<>();

        //commonZReportProperties
        commonProperties.add(new ImportProperty(idZReportField, zReportLM.findProperty("id[ZReport]").getMapping(zReportKey)));
        commonProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("number[ZReport]").getMapping(zReportKey)));
        commonProperties.add(new ImportProperty(nppMachineryField, zReportLM.findProperty("cashRegister[ZReport]").getMapping(zReportKey),
                zReportLM.object(zReportLM.findClass("CashRegister")).getMapping(cashRegisterKey)));
        commonProperties.add(new ImportProperty(dateZReportField, zReportLM.findProperty("date[ZReport]").getMapping(zReportKey), options.doNotReplaceZReportDateTime));
        commonProperties.add(new ImportProperty(timeZReportField, zReportLM.findProperty("time[ZReport]").getMapping(zReportKey), options.doNotReplaceZReportDateTime));
        commonProperties.add(new ImportProperty(sumCashEndZReportField, zReportLM.findProperty("sumCashEnd[ZReport]").getMapping(zReportKey)));
        commonProperties.add(new ImportProperty(sumProtectedEndZReportField, zReportLM.findProperty("sumProtectedEnd[ZReport]").getMapping(zReportKey)));
        commonProperties.add(new ImportProperty(sumBackZReportField, zReportLM.findProperty("sumBack[ZReport]").getMapping(zReportKey)));
        commonProperties.add(new ImportProperty(isPostedZReportField, zReportLM.findProperty("isPosted[ZReport]").getMapping(zReportKey)));

        //commonReceiptProperties
        commonProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("id[Receipt]").getMapping(receiptKey)));
        commonProperties.add(new ImportProperty(numberReceiptField, zReportLM.findProperty("number[Receipt]").getMapping(receiptKey)));
        commonProperties.add(new ImportProperty(dateReceiptField, zReportLM.findProperty("date[Receipt]").getMapping(receiptKey)));
        commonProperties.add(new ImportProperty(timeReceiptField, zReportLM.findProperty("time[Receipt]").getMapping(receiptKey)));
        commonProperties.add(new ImportProperty(skipReceiptField, zReportLM.findProperty("dataSkip[Receipt]").getMapping(receiptKey)));
        if(zReportExternalNumberLM != null) {
            commonProperties.add(new ImportProperty(externalNumberReceiptField, zReportExternalNumberLM.findProperty("externalNumber[Receipt]").getMapping(receiptKey)));
        }

        return commonProperties;
    }

    private String appendCheckDigitToBarcode(String barcode, Integer minLength, boolean appendBarcode) {
        if(appendBarcode) {
            if (barcode == null || (minLength != null && barcode.length() < minLength))
                return null;

            try {
                if (barcode.length() == 11) {
                    return appendEAN13("0" + barcode).substring(1, 13);
                } else if (barcode.length() == 12) {
                    return appendEAN13(barcode);
                } else if (barcode.length() == 7) {  //EAN-8
                    int checkSum = 0;
                    for (int i = 0; i <= 6; i = i + 2) {
                        checkSum += Integer.parseInt(String.valueOf(barcode.charAt(i))) * 3;
                        checkSum += i == 6 ? 0 : Integer.parseInt(String.valueOf(barcode.charAt(i + 1)));
                    }
                    checkSum %= 10;
                    if (checkSum != 0)
                        checkSum = 10 - checkSum;
                    return barcode.concat(String.valueOf(checkSum));
                } else
                    return barcode;
            } catch (Exception e) {
                return barcode;
            }
        } else
            return barcode;
    }

    private String appendEAN13(String barcode) {
        int checkSum = 0;
        for (int i = 0; i <= 10; i = i + 2) {
            checkSum += Integer.parseInt(String.valueOf(barcode.charAt(i)));
            checkSum += Integer.parseInt(String.valueOf(barcode.charAt(i + 1))) * 3;
        }
        checkSum %= 10;
        if (checkSum != 0)
            checkSum = 10 - checkSum;
        return barcode.concat(String.valueOf(checkSum));
    }

    private boolean overDocumentsClosedDate(SalesInfo salesInfo, boolean ignoreReceiptsAfterDocumentsClosedDate, List<Integer> allowReceiptsAfterDocumentsClosedDateCashRegisterList) {
        return ignoreReceiptsAfterDocumentsClosedDate && !allowReceiptsAfterDocumentsClosedDateCashRegisterList.contains(salesInfo.nppGroupMachinery) &&
                ((salesInfo.dateReceipt != null && salesInfo.cashRegisterInfo != null && salesInfo.cashRegisterInfo.documentsClosedDate != null &&
                salesInfo.dateReceipt.compareTo(salesInfo.cashRegisterInfo.documentsClosedDate) < 0) ||
                (salesInfo.dateZReport != null && salesInfo.cashRegisterInfo != null && salesInfo.cashRegisterInfo.documentsClosedDate != null &&
                salesInfo.dateZReport.compareTo(salesInfo.cashRegisterInfo.documentsClosedDate) < 0));
    }

    private String logCompleteMessage(ExecutionStack stack, String sidEquipmentServer, String message) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        try (DataSession session = createSession()) {
            ObjectValue equipmentServerObject = equLM.findProperty("sidTo[STRING[20]]").readClasses(session, new DataObject(sidEquipmentServer));
            DataObject logObject = session.addObject((ConcreteCustomClass) equLM.findClass("EquipmentServerLog"));
            equLM.findProperty("equipmentServer[EquipmentServerLog]").change(equipmentServerObject, session, logObject);
            equLM.findProperty("data[EquipmentServerLog]").change(message, session, logObject);
            equLM.findProperty("date[EquipmentServerLog]").change(LocalDateTime.now(), session, logObject);
            return session.applyMessage(getBusinessLogics(), stack);
        }
    }

    private String formatCompleteMessageMultiThread(DataSession session, List<SalesInfo> salesInfoList, int start, int finish, int dataSize, int left, int ignoredReceiptDetailCount, int taskIndex, int taskSize, LocalDateTime timeStart, String directory) {
        Map<Integer, Set<Integer>> nppCashRegisterMap = new HashMap<>();
        List<String> fileNames = new ArrayList<>();
        Set<String> dates = new HashSet<>();
        for (int i = start; i < finish; i++) {
            SalesInfo salesInfo = salesInfoList.get(i);
            if(nppCashRegisterMap.containsKey(salesInfo.nppGroupMachinery))
                nppCashRegisterMap.get(salesInfo.nppGroupMachinery).add(salesInfo.nppMachinery);
            else
                nppCashRegisterMap.put(salesInfo.nppGroupMachinery, new HashSet<>(Collections.singletonList(salesInfo.nppMachinery)));
            if ((salesInfo.filename != null) && (!fileNames.contains(salesInfo.filename.trim())))
                fileNames.add(salesInfo.filename.trim());
            if(salesInfo.dateZReport != null)
                dates.add(formatDate(salesInfo.dateZReport));
        }

        return formatCompleteMessage(session, nppCashRegisterMap, dates, fileNames, dataSize, left, ignoredReceiptDetailCount, timeStart, directory, String.format("Задание %s из %s. ", taskIndex + 1, taskSize));
    }

    private String formatCompleteMessageSingleThread(DataSession session, List<SalesInfo> data, int dataSize, int left, int ignoredReceiptDetailCount, LocalDateTime timeStart, String directory) {
        Map<Integer, Set<Integer>> nppCashRegisterMap = new HashMap<>();
        List<String> fileNames = new ArrayList<>();
        Set<String> dates = new HashSet<>();
        for (SalesInfo salesInfo : data) {
            if(nppCashRegisterMap.containsKey(salesInfo.nppGroupMachinery))
                nppCashRegisterMap.get(salesInfo.nppGroupMachinery).add(salesInfo.nppMachinery);
            else
                nppCashRegisterMap.put(salesInfo.nppGroupMachinery, new HashSet<>(Collections.singletonList(salesInfo.nppMachinery)));
            if ((salesInfo.filename != null) && (!fileNames.contains(salesInfo.filename.trim())))
                fileNames.add(salesInfo.filename.trim());
            if(salesInfo.dateZReport != null)
                dates.add(formatDate(salesInfo.dateZReport));
        }

        return formatCompleteMessage(session, nppCashRegisterMap, dates, fileNames, dataSize, left, ignoredReceiptDetailCount, timeStart, directory, "");
    }

    private String formatCompleteMessage(DataSession session, Map<Integer, Set<Integer>> nppCashRegisterMap, Set<String> dates, List<String> fileNames,
                                         int dataSize, int left, int ignoredReceiptDetailCount, LocalDateTime timeStart, String directory, String prefix) {
        LocalDateTime timeFinish = LocalDateTime.now();
        String message = String.format("%sЗатрачено времени: %s с (%s - %s)\nЗагружено записей: %s, Осталось записей: %s",
                prefix, Duration.between(timeStart, timeFinish).getSeconds(), formatDateTime(timeStart), formatDateTime(timeFinish), dataSize, left);

        if(ignoredReceiptDetailCount > 0) {
            message += "\nПроигнорировано записей из-за даты закрытия документов: " + ignoredReceiptDetailCount;
        }

        String conflicts = session.getLastAttemptCountMap();
        if(conflicts != null)
            message += "\nКонфликты: " + conflicts;


        message += "\nИз касс: ";
        for (Map.Entry<Integer, Set<Integer>> cashRegisterEntry : nppCashRegisterMap.entrySet()) {
            for(Integer cashRegister : cashRegisterEntry.getValue())
                message += String.format("%s(%s), ", cashRegister, cashRegisterEntry.getKey());
        }
        message = message.substring(0, message.length() - 2);

        if(directory != null) {
            message+= "\nДиректория: " + directory;
        }
        if(!fileNames.isEmpty()) {
            message += "\nИз файлов: ";
            for (String filename : fileNames)
                message += filename + ", ";
            message = message.substring(0, message.length() - 2);
        }

        if(notNullNorEmpty(dates)) {
            message += "\nЗа даты: ";
            for (String date : dates)
                message += date + ", ";
            message = message.substring(0, message.length() - 2);
        }
        return message;
    }

    @Override
    public String sendCashDocumentInfo(List<CashDocument> cashDocumentList) {
        return SendSalesEquipmentServer.sendCashDocumentInfo(getBusinessLogics(), this, getStack(), cashDocumentList);
    }

    @Override
    public void clearedMachineryTransaction(Long transactionId, List<MachineryInfo> machineryInfoList) {
        if(machineryPriceTransactionLM != null) {
            try (DataSession session = createSession()) {
                DataObject transactionObject = session.getDataObject((CustomClass)equLM.findClass("MachineryPriceTransaction"), transactionId);
                for (MachineryInfo machineryInfo : machineryInfoList) {
                    ObjectValue machineryObject = null;
                    if (machineryInfo instanceof CashRegisterInfo && cashRegisterLM != null)
                        machineryObject = cashRegisterLM.findProperty("cashRegisterNppGroupCashRegister[INTEGER,INTEGER]").readClasses(session, new DataObject(machineryInfo.numberGroup), new DataObject(machineryInfo.number));
                    else if (machineryInfo instanceof ScalesInfo && scalesLM != null)
                        machineryObject = scalesLM.findProperty("scalesNppGroupScales[INTEGER,INTEGER]").readClasses(session, new DataObject(machineryInfo.numberGroup), new DataObject(machineryInfo.number));
                    if (machineryObject != null && !machineryInfo.cleared)
                        machineryPriceTransactionLM.findProperty("cleared[Machinery,MachineryPriceTransaction]").change(true, session, (DataObject) machineryObject, transactionObject);
                }
                session.applyException(getBusinessLogics(), getStack());
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public void errorTransactionReport(Long transactionID, Throwable e) {
        try (DataSession session = createSession()) {
            DataObject errorObject = session.addObject((ConcreteCustomClass) equLM.findClass("MachineryPriceTransactionError"));
            equLM.findProperty("machineryPriceTransaction[MachineryPriceTransactionError]").change(transactionID, session, errorObject);
            equLM.findProperty("data[MachineryPriceTransactionError]").change(BaseUtils.nvl(e.getMessage(), e.toString()), session, errorObject);
            equLM.findProperty("date[MachineryPriceTransactionError]").change(LocalDateTime.now(), session, errorObject);

            DataObject transactionObject = session.getDataObject(((CustomClass) equLM.findClass("MachineryPriceTransaction")), transactionID);
            boolean logStackTrace = equLM.findProperty("logStackTrace[MachineryPriceTransaction]").read(session, transactionObject) != null;
            if(logStackTrace) {
                OutputStream os = new ByteArrayOutputStream();
                e.printStackTrace(new PrintStream(os));
                equLM.findProperty("errorTrace[MachineryPriceTransactionError]").change(os.toString(), session, errorObject);
            }
            session.applyException(getBusinessLogics(), getStack());
        } catch (Exception e2) {
            throw Throwables.propagate(e2);
        }
    }

    @Override
    public void errorEquipmentServerReport(String equipmentServer, Throwable exception, String extraData) {
        try (DataSession session = createSession()) {
            DataObject errorObject = session.addObject((ConcreteCustomClass) equLM.findClass("EquipmentServerError"));
            ObjectValue equipmentServerObject = equLM.findProperty("sidTo[STRING[20]]").readClasses(session, new DataObject(equipmentServer, StringClass.get(20)));
            equLM.findProperty("equipmentServer[EquipmentServerError]").change(equipmentServerObject, session, errorObject);
            equLM.findProperty("data[EquipmentServerError]").change(exception.toString(), session, errorObject);
            if(extraData != null)
                equLM.findProperty("extraData[EquipmentServerError]").change(extraData, session, errorObject);
            OutputStream os = new ByteArrayOutputStream();
            exception.printStackTrace(new PrintStream(os));
            equLM.findProperty("erTrace[EquipmentServerError]").change(os.toString(), session, errorObject);

            equLM.findProperty("date[EquipmentServerError]").change(LocalDateTime.now(), session, errorObject);

            session.applyException(getBusinessLogics(), getStack());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private String dateTimeCode(LocalDateTime timeStamp) {
        String result = "";
        long time = localDateTimeToSqlTimestamp(timeStamp).getTime() / 1000;
        while (time > 26) {
            result = (char) (time % 26 + 97) + result;
            time = time / 26;
        }
        result = (char) (time + 97) + result;
        return result;
    }

    private String getRowValue(ImMap<Object, Object> row, String key) {
        return trim((String) row.get(key));
    }

    protected boolean notNullNorEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    protected static boolean notNullNorEmpty(List value) {
        return value != null && !value.isEmpty();
    }

    protected boolean notNullNorEmpty(Set value) {
        return value != null && !value.isEmpty();
    }
    
    protected String formatDateTime(LocalDateTime date) {
        return date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
    }

    private static Comparator<SalesInfo> COMPARATOR = (o1, o2) -> {
        int compareGroupCashRegister = BaseUtils.nullCompareTo(o1.nppGroupMachinery, o2.nppGroupMachinery);
        if (compareGroupCashRegister == 0) {
            int compareCashRegister = BaseUtils.nullCompareTo(o1.nppMachinery, o2.nppMachinery);
            if (compareCashRegister == 0)
                return BaseUtils.nullCompareTo(o1.numberZReport, o2.numberZReport);
            else
                return compareCashRegister;
        } else
            return compareGroupCashRegister;
    };

    @Override
    public String getEventName() {
        return "equipment-server";
    }

    @Override
    public String getProcessMonitorTaskJSON(String sidEquipmentServer) {
        return ProcessMonitorEquipmentServer.getProcessMonitorTaskJSON(this, sidEquipmentServer);
    }

    @Override
    public void finishProcessMonitorTask(String sidEquipmentServer, String resultJSON) {
        ProcessMonitorEquipmentServer.finishProcessMonitorTask(getBusinessLogics(), this, getStack(), sidEquipmentServer, resultJSON);
    }

    private Set<String> readClosedZReportSet(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Set<String> result = new HashSet<>();
        KeyExpr zReportExpr = new KeyExpr("ZReport");
        ImRevMap<Object, KeyExpr> zReportKeys = MapFact.singletonRev("ZReport", zReportExpr);
        QueryBuilder<Object, Object> zReportQuery = new QueryBuilder<>(zReportKeys);

        zReportQuery.addProperty("idZReport", zReportLM.findProperty("id[ZReport]").getExpr(zReportExpr));
        zReportQuery.and(zReportLM.findProperty("isClosed[ZReport]").getExpr(zReportExpr).getWhere());

        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> zReportResult = zReportQuery.execute(session);
        for (ImMap<Object, Object> row : zReportResult.valueIt()) {
            String idZReport = (String) row.get("idZReport");
            result.add(idZReport);
        }
        return result;
    }

    private Map<String, DataObject> readPartedBarcodes(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, DataObject> result = new HashMap<>();
        if(machineryPriceTransactionPartLM != null) {
            KeyExpr barcodeExpr = new KeyExpr("Barcode");
            ImRevMap<Object, KeyExpr> barcodeKeys = MapFact.singletonRev("barcode", barcodeExpr);
            QueryBuilder<Object, Object> barcodeQuery = new QueryBuilder<>(barcodeKeys);

            barcodeQuery.addProperty("id", machineryPriceTransactionPartLM.findProperty("id[Barcode]").getExpr(barcodeExpr));
            barcodeQuery.and(machineryPriceTransactionPartLM.findProperty("hasSet[Barcode]").getExpr(barcodeExpr).getWhere());

            ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> barcodeResult = barcodeQuery.executeClasses(session);
            for (int i = 0; i < barcodeResult.size(); i++) {
                DataObject barcodeObject = barcodeResult.getKey(i).get("barcode");
                String idBarcode = (String) barcodeResult.getValue(i).get("id").getValue();
                logger.info("Read parted barcode: " + idBarcode);
                result.put(idBarcode, barcodeObject);
            }
        }
        return result;
    }

    private List<BarcodePart> readBarcodeParts(DataSession session, DataObject barcodeObject, SalesInfo sale)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<BarcodePart> result = new ArrayList<>();
        if(machineryPriceTransactionPartLM != null && sale.nppGroupMachinery != null) {
            KeyExpr barcodeExpr = new KeyExpr("Barcode");
            KeyExpr partExpr = new KeyExpr("Part");
            ImRevMap<Object, KeyExpr> barcodeKeys = MapFact.toRevMap("barcode", barcodeExpr, "part", partExpr);
            QueryBuilder<Object, Object> barcodeQuery = new QueryBuilder<>(barcodeKeys);

            barcodeQuery.addProperty("id", machineryPriceTransactionPartLM.findProperty("idBarcodePart[Barcode,Part]").getExpr(barcodeExpr, partExpr));
            barcodeQuery.addProperty("quantity", machineryPriceTransactionPartLM.findProperty("quantityPart[Barcode,Part]").getExpr(barcodeExpr, partExpr));
            barcodeQuery.addProperty("price", machineryPriceTransactionPartLM.findProperty("pricePart[Barcode,INTEGER,Part]").getExpr(barcodeExpr, new DataObject(sale.nppGroupMachinery).getExpr(), partExpr));
            barcodeQuery.and(machineryPriceTransactionPartLM.findProperty("idBarcodePart[Barcode,Part]").getExpr(barcodeExpr, partExpr).getWhere());
            barcodeQuery.and(machineryPriceTransactionPartLM.findProperty("quantityPart[Barcode,Part]").getExpr(barcodeExpr, partExpr).getWhere());
//            barcodeQuery.and(machineryPriceTransactionPartLM.findProperty("pricePart[Barcode,INTEGER,Part]").getExpr(barcodeExpr, new DataObject(sale.nppGroupMachinery).getExpr(), partExpr).getWhere());
            barcodeQuery.and(barcodeExpr.compare(barcodeObject.getExpr(), Compare.EQUALS));

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> barcodeResult = barcodeQuery.execute(session);
            BigDecimal currentSum = BigDecimal.ZERO;
            int i = 1;
            for (ImMap<Object, Object> barcodeEntry : barcodeResult.valueIt()) {
                String id = (String) barcodeEntry.get("id");
                BigDecimal quantity = safeMultiply((BigDecimal) barcodeEntry.get("quantity"), sale.quantityReceiptDetail);
                BigDecimal price = (BigDecimal) barcodeEntry.get("price");

                BigDecimal sum = safeMultiply(quantity, price);
                currentSum = safeAdd(currentSum, sum);

                logger.info(String.format("BarcodePart: npp %s, id %s, quantity %s, price %s, sum %s", sale.nppGroupMachinery, id, quantity, price, sum));
                result.add(new BarcodePart(i++, id, quantity, price, sum));
            }

            //если сумма не совпала, докидываем разницу на первую часть
            BigDecimal diff = safeSubtract(safeAdd(sale.sumReceiptDetail, sale.discountSumReceiptDetail), currentSum);
            if(!result.isEmpty() && diff != null && diff.compareTo(BigDecimal.ZERO) != 0) {
                result.get(0).sum = safeAdd(result.get(0).sum, diff);
            }

            //распределяем сумму скидки по частям
            BigDecimal currentDiscountSum = BigDecimal.ZERO;
            for(BarcodePart barcodePart : result) {
                BigDecimal discountSum = safeDivide(safeMultiply(sale.discountSumReceiptDetail, barcodePart.sum), safeAdd(sale.sumReceiptDetail, sale.discountSumReceiptDetail), 2);
                currentDiscountSum = safeAdd(currentDiscountSum, discountSum);
                barcodePart.discountSum = discountSum;
                barcodePart.sum = safeSubtract(barcodePart.sum, barcodePart.discountSum);
            }

            //если сумма скидки не совпала, докидываем разницу на первую часть
            BigDecimal discountDiff = safeSubtract(sale.discountSumReceiptDetail, currentDiscountSum);
            if(!result.isEmpty() && discountDiff != null && discountDiff.compareTo(BigDecimal.ZERO) != 0) {
                result.get(0).discountSum = safeAdd(result.get(0).discountSum, discountDiff);
                result.get(0).sum = safeSubtract(result.get(0).sum, discountDiff);
            }

        }
        return result;
    }

    private class BarcodePart {
        int index;
        String id;
        BigDecimal quantity;
        BigDecimal price;
        BigDecimal sum;
        BigDecimal discountSum;

        public BarcodePart(int index, String id, BigDecimal quantity, BigDecimal price, BigDecimal sum) {
            this.index = index;
            this.id = id;
            this.quantity = quantity;
            this.price = price;
            this.sum = sum;
        }
    }

    private RowsData getRowsData(DataSession session, List<SalesInfo> data, int start, int finish, EquipmentServerOptions options,
                                 List<Integer> allowReceiptsAfterDocumentsClosedDateCashRegisterList) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<List<Object>> dataSale = new ArrayList<>();
        List<List<Object>> dataReturn = new ArrayList<>();
        List<List<Object>> dataGiftCard = new ArrayList<>();
        Map<Object, String> barcodeMap = new HashMap<>();

        //todo: refactor old usages of zReportExtraFields (sumCashEnd, sumProtectedEnd, sumBack, externalSum, beginShift, endShift)
        // to processZReportExtraFields
        JSONObject zReportExtraFields = new JSONObject();
        for (int i = start; i < finish; i++) {
            SalesInfo sale = data.get(i);
            if(sale.zReportExtraFields != null) {
                for(Map.Entry<String, Object> zReportExtraField : sale.zReportExtraFields.entrySet()) {
                    JSONArray dataArray = zReportExtraFields.optJSONArray(zReportExtraField.getKey());
                    if(dataArray == null) {
                        dataArray = new JSONArray();
                    }
                    JSONObject fieldReceipt = new JSONObject();
                    fieldReceipt.put("id", getIdZReport(sale, options));
                    fieldReceipt.put("value", zReportExtraField.getValue());
                    dataArray.put(fieldReceipt);

                    zReportExtraFields.put(zReportExtraField.getKey(), dataArray);
                }
            }
        }

        JSONObject receiptExtraFields = new JSONObject();
        for (int i = start; i < finish; i++) {
            SalesInfo sale = data.get(i);
            if(sale.receiptExtraFields != null) {
                for(Map.Entry<String, Object> receiptExtraField : sale.receiptExtraFields.entrySet()) {
                    JSONArray dataArray = receiptExtraFields.optJSONArray(receiptExtraField.getKey());
                    if(dataArray == null) {
                        dataArray = new JSONArray();
                    }
                    JSONObject fieldReceipt = new JSONObject();
                    fieldReceipt.put("id", getIdReceipt(sale, options));
                    fieldReceipt.put("value", receiptExtraField.getValue());
                    dataArray.put(fieldReceipt);

                    receiptExtraFields.put(receiptExtraField.getKey(), dataArray);
                }
            }
        }

        JSONObject receiptDetailExtraFields = new JSONObject();
        for (int i = start; i < finish; i++) {
            SalesInfo sale = data.get(i);
            if(sale.receiptDetailExtraFields != null) {
                for(Map.Entry<String, Object> receiptDetailExtraField : sale.receiptDetailExtraFields.entrySet()) {
                    JSONArray dataArray = receiptDetailExtraFields.optJSONArray(receiptDetailExtraField.getKey());
                    if(dataArray == null) {
                        dataArray = new JSONArray();
                    }
                    JSONObject fieldReceiptDetail = new JSONObject();
                    fieldReceiptDetail.put("id", getIdReceiptDetail(sale, options));
                    fieldReceiptDetail.put("value", receiptDetailExtraField.getValue());
                    dataArray.put(fieldReceiptDetail);

                    receiptDetailExtraFields.put(receiptDetailExtraField.getKey(), dataArray);
                }
            }
        }

        Set<String> ignoredIdReceipts = new HashSet<>();
        int ignoredReceiptDetailCount = 0;

        ImMap<String, Object> idItemBarcodeMap = null;
        if(options.readSalesByIdItem) {
            MSet<String> mIdItems = SetFact.mSet();
            for (int i = start; i < finish; i++) {
                SalesInfo sale = data.get(i);
                if(!sale.isGiftCard) {
                    mIdItems.add(sale.idItem);
                }
            }
            idItemBarcodeMap = equipmentLM.findProperty("idBarcodeSkuOverId[STRING]").readAll(session, mIdItems.immutable().toArray(new String[mIdItems.size()]));
        }

        for (int i = start; i < finish; i++) {
            SalesInfo sale = data.get(i);
            if (!overDocumentsClosedDate(sale, options.ignoreReceiptsAfterDocumentsClosedDate, allowReceiptsAfterDocumentsClosedDateCashRegisterList)) {

                String barcode = null;

                //1. Если включена опция "Прием реализации по коду товара", ищем ШК по sale.idItem и свойству idBarcodeSkuOverId[STRING]
                if(idItemBarcodeMap != null) {
                    barcode = (String) idItemBarcodeMap.get(sale.idItem);
                }

                //2. Если задан sale.barcodeItem, используем его; иначе пытаемся получить прочитанное ранее значение из barcodeMap
                if (barcode == null) {
                    if (notNullNorEmpty(sale.barcodeItem))
                        barcode = sale.barcodeItem;
                    else if (sale.itemObject != null)
                        barcode = barcodeMap.get(sale.itemObject);
                    else if (sale.idItem != null)
                        barcode = barcodeMap.get(sale.idItem);
                }
                //3. Ищем ШК по sale.itemObject и свойству idBarcode[Sku]
                if (barcode == null && sale.itemObject != null) {
                    barcode = trim((String) itemLM.findProperty("idBarcode[Sku]").read(session, new DataObject(sale.itemObject, (ConcreteCustomClass) itemLM.findClass("Item"))));
                    barcodeMap.put(sale.itemObject, barcode);
                }
                //4. Ищем ШК по sale.idItem и свойству idBarcodeSku[STRING[100]]
                if (barcode == null && sale.idItem != null) {
                    barcode = trim((String) itemLM.findProperty("idBarcodeSku[STRING[100]]").read(session, new DataObject(sale.idItem, StringClass.get((100)))));
                    //чит на случай, когда штрихкод приходит в код товара, copy-paste from ArtixHandler
                    if (barcode == null)
                        barcode = appendCheckDigitToBarcode(sale.idItem, 7, true);
                    barcodeMap.put(sale.idItem, barcode);
                }

                DataObject barcodeObject = options.barcodeParts.get(barcode);
                if(barcodeObject != null) {

                    logger.info("Read part info for barcode: " + barcode);
                    for(BarcodePart barcodePart : readBarcodeParts(session, barcodeObject, sale)) {
                        List<Object> row = getReceiptDetailRow(sale, barcodePart, barcode, options);
                        if (sale.isGiftCard) {
                            dataGiftCard.add(row);
                        } else if (sale.quantityReceiptDetail.doubleValue() < 0) {
                            dataReturn.add(row);
                        } else {
                            dataSale.add(row);
                        }
                    }
                } else {
                    List<Object> row = getReceiptDetailRow(sale, null, barcode, options);
                    if (sale.isGiftCard) {
                        dataGiftCard.add(row);
                    } else if (sale.quantityReceiptDetail.doubleValue() < 0) {
                        dataReturn.add(row);
                    } else {
                        dataSale.add(row);
                    }
                }


            } else {
                ignoredIdReceipts.add(getIdReceipt(sale, options));
                ignoredReceiptDetailCount++;
            }
        }
        return new RowsData(dataSale, dataReturn, dataGiftCard, zReportExtraFields, receiptExtraFields, receiptDetailExtraFields, ignoredIdReceipts, ignoredReceiptDetailCount);
    }

    private class RowsData {
        List<List<Object>> dataSale;
        List<List<Object>> dataReturn;
        List<List<Object>> dataGiftCard;
        JSONObject zReportExtraFields;
        JSONObject receiptExtraFields;
        JSONObject receiptDetailExtraFields;
        Set<String> ignoredIdReceipts;
        int ignoredReceiptDetailCount;

        public RowsData(List<List<Object>> dataSale, List<List<Object>> dataReturn, List<List<Object>> dataGiftCard,
                        JSONObject zReportExtraFields, JSONObject receiptExtraFields, JSONObject receiptDetailExtraFields,
                        Set<String> ignoredIdReceipts, int ignoredReceiptDetailCount) {
            this.dataSale = dataSale;
            this.dataReturn = dataReturn;
            this.dataGiftCard = dataGiftCard;
            this.zReportExtraFields = zReportExtraFields;
            this.receiptExtraFields = receiptExtraFields;
            this.receiptDetailExtraFields = receiptDetailExtraFields;
            this.ignoredIdReceipts = ignoredIdReceipts;
            this.ignoredReceiptDetailCount = ignoredReceiptDetailCount;
        }
    }

    private List<List<SalesInfo>> groupSalesInfoByNppGroupMachinery(List<SalesInfo> salesInfoList) {
        //todo: one-line in java8: https://stackoverflow.com/questions/30755949/java-8-lambdas-group-list-into-map
        int start = 0;
        Map<Integer, List<SalesInfo>> groupedSalesInfo = new HashMap<>();
        while (start < salesInfoList.size()) {
            SalesInfo salesInfo = salesInfoList.get(start);
            Integer nppGroupMachinery = salesInfo.nppGroupMachinery;
            List<SalesInfo> currentList = groupedSalesInfo.get(nppGroupMachinery);
            if (currentList == null) {
                currentList = new ArrayList<>();
                groupedSalesInfo.put(nppGroupMachinery, currentList);
            }
            currentList.add(salesInfo);
            start++;
        }
        return new ArrayList<>(groupedSalesInfo.values());
    }

    private void processExtraFields(DataSession session, ExecutionStack stack, RowsData rowsData) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        if(cashRegisterLM != null) {
            if (rowsData.zReportExtraFields != null && cashRegisterLM.findProperty("executeProcessZReportExtraFields[]").read(session) != null) {
                cashRegisterLM.findAction("processZReportExtraFields[STRING]").execute(session, stack, new DataObject(rowsData.zReportExtraFields.toString()));
            }
            if (rowsData.receiptExtraFields != null && cashRegisterLM.findProperty("executeProcessReceiptExtraFields[]").read(session) != null) {
                cashRegisterLM.findAction("processReceiptExtraFields[STRING]").execute(session, stack, new DataObject(rowsData.receiptExtraFields.toString()));
            }
            if (rowsData.receiptDetailExtraFields != null && cashRegisterLM.findProperty("executeProcessReceiptDetailExtraFields[]").read(session) != null) {
                cashRegisterLM.findAction("processReceiptDetailExtraFields[STRING]").execute(session, stack, new DataObject(rowsData.receiptDetailExtraFields.toString()));
            }
        }
    }

    public static String getIdZReport(SalesInfo s, EquipmentServerOptions options) {
        String idZReport = getIdZReport(s);
        return options.encoder != null && !idZReport.startsWith("null") ? options.encoder.encodeLessMemory(idZReport) : idZReport;
    }

    public static String getIdReceipt(SalesInfo s, EquipmentServerOptions options) {
        String idReceipt = getIdReceipt(s, options.timeId);
        return options.encoder != null && !idReceipt.startsWith("null") ? options.encoder.encodeLessMemory(idReceipt) : idReceipt;
    }

    public static String getIdReceiptDetail(SalesInfo s, EquipmentServerOptions options) {
        String idReceiptDetail = getIdReceiptDetail(s, options.timeId);
        return options.encoder != null && !idReceiptDetail.startsWith("null") ? options.encoder.encodeLessMemory(idReceiptDetail) : idReceiptDetail;
    }

    @Override
    public String sendSucceededSoftCheckInfo(String sidEquipmentServer, Map<String, LocalDateTime> invoiceSet) throws RemoteException {
        return softCheck == null ? null : softCheck.sendSucceededSoftCheckInfo(sidEquipmentServer, invoiceSet);
    }

    private static String getIdReceipt(SalesInfo s, boolean timeId) {
        return getIdZReport(s) + "_" + s.numberReceipt + (timeId ? "_" + s.timeReceipt.format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "");
    }

    private static String getIdReceiptDetail(SalesInfo s, boolean timeId) {
        return getIdReceipt(s, timeId) + "_" + s.numberReceiptDetail;
    }

    private EquipmentServerOptions readEquipmentServerOptions(String sidEquipmentServer, DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        ObjectValue equipmentServerObject = equLM.findProperty("sidTo[STRING[20]]").readClasses(session, new DataObject(sidEquipmentServer));
        Integer maxThreads = (Integer) equLM.findProperty("maxThreads[EquipmentServer]").read(session, equipmentServerObject);
        Integer numberAtATime = (Integer) equLM.findProperty("numberAtATime[EquipmentServer]").read(session, equipmentServerObject);
        boolean timeId = equLM.findProperty("timeId[EquipmentServer]").read(session, equipmentServerObject) != null;
        boolean ignoreReceiptsAfterDocumentsClosedDate = equLM.findProperty("ignoreReceiptsAfterDocumentsClosedDate[EquipmentServer]").read(session, equipmentServerObject) != null;
        boolean overrideCashiers = equLM.findProperty("overrideCashiers[EquipmentServer]").read(session, equipmentServerObject) != null;
        boolean skipGiftCardKeys = equLM.findProperty("skipGiftCardKeys[EquipmentServer]").read(session, equipmentServerObject) != null;
        boolean useNewIds = equLM.findProperty("useNewIds[EquipmentServer]").read(session, equipmentServerObject) != null;
        boolean skipReceiveSales = equLM.findProperty("skipReceiveSales[EquipmentServer]").read(session, equipmentServerObject) != null;
        boolean receiveVATSales = equLM.findProperty("receiveVATSales[EquipmentServer]").read(session, equipmentServerObject) != null;
        boolean ignoreConstraints = equLM.findProperty("ignoreConstraints[EquipmentServer]").read(session, equipmentServerObject) != null;
        boolean doNotReplaceZReportDateTime = equLM.findProperty("doNotReplaceZReportDateTime[EquipmentServer]").read(session, equipmentServerObject) != null;
        boolean readSalesByIdItem = equLM.findProperty("readSalesByIdItem[EquipmentServer]").read(session, equipmentServerObject) != null;
        IdEncoder encoder = useNewIds ? (timeId ? new IdEncoder(5) : new IdEncoder()) : null;
        Map<String, DataObject> barcodeParts = readPartedBarcodes(session);
        return new EquipmentServerOptions(maxThreads, numberAtATime, timeId, ignoreReceiptsAfterDocumentsClosedDate, overrideCashiers,
                skipGiftCardKeys, skipReceiveSales, receiveVATSales, ignoreConstraints, doNotReplaceZReportDateTime,
                readSalesByIdItem, encoder, barcodeParts);
    }

    public class EquipmentServerOptions {
        Integer maxThreads;
        Integer numberAtATime;
        boolean timeId;
        boolean ignoreReceiptsAfterDocumentsClosedDate;
        boolean overrideCashiers;
        boolean skipGiftCardKeys;
        boolean skipReceiveSales;
        boolean receiveVATSales;
        boolean ignoreConstraints;
        boolean doNotReplaceZReportDateTime;
        boolean readSalesByIdItem;
        IdEncoder encoder;
        Map<String, DataObject> barcodeParts;

        public EquipmentServerOptions(Integer maxThreads, Integer numberAtATime, boolean timeId, boolean ignoreReceiptsAfterDocumentsClosedDate,
                                      boolean overrideCashiers, boolean skipGiftCardKeys, boolean skipReceiveSales, boolean receiveVATSales,
                                      boolean ignoreConstraints, boolean doNotReplaceZReportDateTime, boolean readSalesByIdItem,
                                      IdEncoder encoder, Map<String, DataObject> barcodeParts) {
            this.maxThreads = maxThreads;
            this.numberAtATime = numberAtATime;
            this.timeId = timeId;
            this.ignoreReceiptsAfterDocumentsClosedDate = ignoreReceiptsAfterDocumentsClosedDate;
            this.overrideCashiers = overrideCashiers;
            this.skipGiftCardKeys = skipGiftCardKeys;
            this.skipReceiveSales = skipReceiveSales;
            this.receiveVATSales = receiveVATSales;
            this.ignoreConstraints = ignoreConstraints;
            this.doNotReplaceZReportDateTime = doNotReplaceZReportDateTime;
            this.readSalesByIdItem = readSalesByIdItem;
            this.encoder = encoder;
            this.barcodeParts = barcodeParts;
        }
    }

    private BigDecimal safeNegate(BigDecimal operand) {
        return operand == null ? null : operand.negate();
    }

    private BigDecimal safeAdd(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else return (operand1 == null ? operand2 : (operand2 == null ? operand1 : operand1.add(operand2)));
    }

    public static BigDecimal safeSubtract(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else
            return (operand1 == null ? operand2.negate() : (operand2 == null ? operand1 : operand1.subtract((operand2))));
    }

    private BigDecimal safeMultiply(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null || operand1.doubleValue() == 0 || operand2 == null || operand2.doubleValue() == 0)
            return null;
        else return operand1.multiply(operand2);
    }

    private BigDecimal safeDivide(BigDecimal dividend, BigDecimal quotient, int scale) {
        if (dividend == null || quotient == null || quotient.doubleValue() == 0)
            return null;
        return dividend.divide(quotient, scale, RoundingMode.HALF_UP);
    }

    @Override
    public List<TransactionInfo> readTransactionInfo(String sidEquipmentServer) throws SQLException {
        logSourceHost("readTransaction");
        try (DataSession session = createSession()) {
            List<TransactionInfo> transactionList = new ArrayList<>();

            ObjectValue equipmentServerObject = equipmentLM.findProperty("sidTo[STRING[20]]").readClasses(session, new DataObject(sidEquipmentServer));
            Integer minutesTroubleMachineryGroups = (Integer) equipmentLM.findProperty("minutesTroubleMachineryGroup[EquipmentServer]").read(session, equipmentServerObject);
            Integer skipTroubles = (Integer) equipmentLM.findProperty("skipTroublesDelay[EquipmentServer]").read(session, equipmentServerObject);
            Integer selectTop = (Integer) equipmentLM.findProperty("selectTop[EquipmentServer]").read(session, equipmentServerObject);
            if(selectTop == null)
                selectTop = 0;

            KeyExpr machineryPriceTransactionExpr = new KeyExpr("machineryPriceTransaction");
            ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev("machineryPriceTransaction", machineryPriceTransactionExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

            String[] mptNames = new String[]{"dateTimeMachineryPriceTransaction", "groupMachineryMachineryPriceTransaction",
                    "nppGroupMachineryMachineryPriceTransaction", "nameGroupMachineryMachineryPriceTransaction", "snapshotMachineryPriceTransaction",
                    "descriptionMachineryPriceTransaction", "lastDateMachineryPriceTransactionErrorMachineryPriceTransaction",
                    "priorityMPT", "filterMPT", "infoMPT"};
            LP[] mptProperties = equLM.findProperties("dateTime[MachineryPriceTransaction]", "groupMachinery[MachineryPriceTransaction]",
                    "nppGroupMachinery[MachineryPriceTransaction]", "nameGroupMachinery[MachineryPriceTransaction]", "snapshot[MachineryPriceTransaction]",
                    "description[MachineryPriceTransaction]", "lastDateMachineryPriceTransactionError[MachineryPriceTransaction]",
                    "priority[MachineryPriceTransaction]", "filter[MachineryPriceTransaction]", "info[MachineryPriceTransaction]");
            for (int i = 0; i < mptProperties.length; i++) {
                query.addProperty(mptNames[i], mptProperties[i].getExpr(machineryPriceTransactionExpr));
            }
            query.and(equLM.findProperty("sidEquipmentServer[MachineryPriceTransaction]").getExpr(machineryPriceTransactionExpr).compare(new DataObject(sidEquipmentServer), Compare.EQUALS));
            query.and(equLM.findProperty("process[MachineryPriceTransaction]").getExpr(machineryPriceTransactionExpr).getWhere());

            logger.info(String.format("Starting to read top %s transactions", selectTop));
            ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(session.sql, MapFact.toOrderMap("priorityMPT", true, "filterMPT", false), selectTop, session);

            List<Object[]> transactionObjects = new ArrayList<>();
            for (int i = 0, size = result.size(); i < size; i++) {
                ImMap<Object, ObjectValue> value = result.getValue(i);
                ObjectValue dateTimeMPT = value.get("dateTimeMachineryPriceTransaction");
                if(dateTimeMPT instanceof DataObject) {
                    DataObject groupMachineryMPT = (DataObject) value.get("groupMachineryMachineryPriceTransaction");
                    Integer nppGroupMachineryMPT = (Integer) value.get("nppGroupMachineryMachineryPriceTransaction").getValue();
                    String nameGroupMachineryMPT = (String) value.get("nameGroupMachineryMachineryPriceTransaction").getValue();
                    DataObject transactionObject = result.getKey(i).singleValue();
                    boolean snapshotMPT = value.get("snapshotMachineryPriceTransaction") instanceof DataObject;
                    String descriptionMPT = (String) value.get("descriptionMachineryPriceTransaction").getValue();
                    LocalDateTime lastErrorDate = (LocalDateTime) value.get("lastDateMachineryPriceTransactionErrorMachineryPriceTransaction").getValue();
                    String infoMPT = (String) value.get("infoMPT").getValue();
                    transactionObjects.add(new Object[]{groupMachineryMPT, nppGroupMachineryMPT, nameGroupMachineryMPT, transactionObject,
                            dateTimeCode((LocalDateTime) dateTimeMPT.getValue()), ((LocalDateTime) dateTimeMPT.getValue()).toLocalDate(), snapshotMPT, descriptionMPT, lastErrorDate, infoMPT});
                }
            }

            Map<String, List<ItemGroup>> itemGroupMap = transactionObjects.isEmpty() ? null : readItemGroupMap(session);
            List<Integer> troubleMachineryGroups = readTroubleMachineryGroups(session, minutesTroubleMachineryGroups);
            Integer skipCounter = skipTroubleCounter.get(sidEquipmentServer);
            if (skipCounter == null) skipCounter = 1;
            logger.info("skip problem transactions counter for " + sidEquipmentServer + " : " + skipCounter);
            boolean skipTroubleMachineryGroups = skipTroubles != null && skipCounter < skipTroubles;
            if (skipTroubleMachineryGroups)
                skipTroubleCounter.put(sidEquipmentServer, skipCounter + 1);
            else
                skipTroubleCounter.put(sidEquipmentServer, 1);

            logger.info("" + transactionObjects.size() + " transactions read");
            int count = 0;
            for (Object[] transaction : transactionObjects) {

                count++;
                DataObject groupMachineryObject = (DataObject) transaction[0];
                Integer nppGroupMachinery = (Integer) transaction[1];
                if (troubleMachineryGroups.contains(nppGroupMachinery) && skipTroubleMachineryGroups) {
                    logger.info("Skipped transaction number " + count + " of " + transactionObjects.size());
                    continue;
                } else {
                    logger.info("Reading transaction number " + count + " of " + transactionObjects.size());
                }

                String nameGroupMachinery = (String) transaction[2];
                DataObject transactionObject = (DataObject) transaction[3];
                String dateTimeCode = (String) transaction[4];
                LocalDate date = (LocalDate) transaction[5];
                boolean snapshotTransaction = (boolean) transaction[6];
                String descriptionTransaction = (String) transaction[7];
                LocalDateTime lastErrorDateTransaction = (LocalDateTime) transaction[8];
                String infoMPT = (String) transaction[9];

                boolean isCashRegisterPriceTransaction = cashRegisterLM != null && transactionObject.objectClass.equals(cashRegisterLM.findClass("CashRegisterPriceTransaction"));
                boolean isScalesPriceTransaction = scalesLM != null && transactionObject.objectClass.equals(scalesLM.findClass("ScalesPriceTransaction"));
                boolean isPriceCheckerPriceTransaction = priceCheckerLM != null && transactionObject.objectClass.equals(priceCheckerLM.findClass("PriceCheckerPriceTransaction"));
                boolean isTerminalPriceTransaction = terminalLM != null && transactionObject.objectClass.equals(terminalLM.findClass("TerminalPriceTransaction"));

                String handlerModelGroupMachinery = (String) equLM.findProperty("handlerModel[GroupMachinery]").read(session, groupMachineryObject);

                ValueExpr transactionExpr = transactionObject.getExpr();
                KeyExpr barcodeExpr = new KeyExpr("barcode");
                ImRevMap<Object, KeyExpr> skuKeys = MapFact.singletonRev("barcode", barcodeExpr);

                QueryBuilder<Object, Object> skuQuery = new QueryBuilder<>(skuKeys);

                String[] skuNames = new String[]{"nameMachineryPriceTransactionBarcode", "priceMachineryPriceTransactionBarcode",
                        "expiryDateMachineryPriceTransactionBarcode", "splitMachineryPriceTransactionBarcode", "passScalesMachineryPriceTransactionBarcode",
                        "idUOMMachineryPriceTransactionBarcode", "shortNameUOMMachineryPriceTransactionBarcode", "infoMPTBarcode", "extraInfoMPTBarcode", "pluNumberMachineryPriceTransactionBarcode",
                        "flagsMachineryPriceTransactionBarcode", "expiryDaysMachineryPriceTransactionBarcode", "minPriceMachineryPriceTransactionBarcode",
                        "canonicalNameSkuGroupMachineryPriceTransactionBarcode", "retailPrice", "idSkuBarcode"};
                LP[] skuProperties = equLM.findProperties("name[MachineryPriceTransaction,Barcode]", "price[MachineryPriceTransaction,Barcode]",
                        "expiryDate[MachineryPriceTransaction,Barcode]", "split[MachineryPriceTransaction,Barcode]", "passScales[MachineryPriceTransaction,Barcode]",
                        "idUOM[MachineryPriceTransaction,Barcode]", "shortNameUOM[MachineryPriceTransaction,Barcode]", "info[MachineryPriceTransaction,Barcode]", "extraInfo[MachineryPriceTransaction,Barcode]",
                        "pluNumber[MachineryPriceTransaction,Barcode]", "flags[MachineryPriceTransaction,Barcode]", "expiryDays[MachineryPriceTransaction,Barcode]",
                        "minPrice[MachineryPriceTransaction,Barcode]", "canonicalNameSkuGroup[MachineryPriceTransaction,Barcode]", "retailPrice[MachineryPriceTransaction, Barcode]",
                        "idSku[MachineryPriceTransaction, Barcode]");
                for (int i = 0; i < skuProperties.length; i++) {
                    skuQuery.addProperty(skuNames[i], skuProperties[i].getExpr(transactionExpr, barcodeExpr));
                }

                String[] barcodeNames = new String[]{"valueBarcode", "idBarcode", "skuBarcode", "skuGroupBarcode"};
                LP[] barcodeProperties = equLM.findProperties("value[Barcode]", "id[Barcode]", "sku[Barcode]", "skuGroup[Barcode]");
                for (int i = 0; i < barcodeProperties.length; i++) {
                    skuQuery.addProperty(barcodeNames[i], barcodeProperties[i].getExpr(barcodeExpr));
                }

                if (isCashRegisterPriceTransaction) {
                    skuQuery.addProperty("amountBarcode", equLM.findProperty("amount[Barcode]").getExpr(barcodeExpr));
                    skuQuery.addProperty("mainBarcode", equLM.findProperty("idMainBarcode[Barcode]").getExpr(barcodeExpr));
                }

                if(itemLM != null) {
                    skuQuery.addProperty("idBrandBarcode", itemLM.findProperty("idBrand[Barcode]").getExpr(barcodeExpr));
                    skuQuery.addProperty("nameBrandBarcode", itemLM.findProperty("nameBrand[Barcode]").getExpr(barcodeExpr));
                }

                if(itemFashionLM != null) {
                    skuQuery.addProperty("idSeasonBarcode", itemFashionLM.findProperty("idSeason[Barcode]").getExpr(barcodeExpr));
                    skuQuery.addProperty("nameSeasonBarcode", itemFashionLM.findProperty("nameSeason[Barcode]").getExpr(barcodeExpr));
                }

                if(cashRegisterItemLM != null) {
                    skuQuery.addProperty("CashRegisterItem.idSkuGroupMachineryPriceTransactionBarcode",
                            cashRegisterItemLM.findProperty("idSkuGroup[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
                    skuQuery.addProperty("CashRegisterItem.overIdSkuGroupMachineryPriceTransactionBarcode",
                            cashRegisterItemLM.findProperty("overIdSkuGroup[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
                }

                if(scalesItemLM != null) {
                    skuQuery.addProperty("ScalesItem.idSkuGroupMachineryPriceTransactionBarcode",
                            scalesItemLM.findProperty("idSkuGroup[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
                }

                if (scalesItemLM != null) {
                    String[] scalesSkuNames = new String[]{"hoursExpiryMachineryPriceTransactionBarcode",
                            "descriptionMachineryPriceTransactionBarcode", "descriptionNumberMachineryPriceTransactionBarcode",
                            "groupImageMPTBarcode", "itemImageMPTBarcode"};
                    LP[] scalesSkuProperties = scalesItemLM.findProperties("hoursExpiry[MachineryPriceTransaction,Barcode]",
                            "description[MachineryPriceTransaction,Barcode]", "descriptionNumber[MachineryPriceTransaction,Barcode]",
                            "groupImage[MachineryPriceTransaction,Barcode]", "itemImage[MachineryPriceTransaction,Barcode]");
                    for (int i = 0; i < scalesSkuProperties.length; i++) {
                        skuQuery.addProperty(scalesSkuNames[i], scalesSkuProperties[i].getExpr(transactionExpr, barcodeExpr));
                    }
                    skuQuery.addProperty("extraPercent", scalesItemLM.findProperty("extraPercent[MachineryPriceTransaction, Barcode]").getExpr(transactionExpr, barcodeExpr));
                    skuQuery.addProperty("imagesCount", scalesItemLM.findProperty("imagesCount[Barcode]").getExpr(barcodeExpr));
                }

                if (machineryPriceTransactionStockTaxLM != null) {
                    String[] taxNames = new String[]{"VATMachineryPriceTransactionBarcode"};
                    LP[] taxProperties = machineryPriceTransactionStockTaxLM.findProperties("VAT[MachineryPriceTransaction,Barcode]");
                    for (int i = 0; i < taxProperties.length; i++) {
                        skuQuery.addProperty(taxNames[i], taxProperties[i].getExpr(transactionExpr, barcodeExpr));
                    }
                }

                if(machineryPriceTransactionSectionLM != null) {
                    skuQuery.addProperty("sectionMachineryPriceTransactionBarcode",
                            machineryPriceTransactionSectionLM.findProperty("section[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
                    skuQuery.addProperty("deleteSectionBarcode",
                            machineryPriceTransactionSectionLM.findProperty("deleteSectionBarcode[?]").getExpr(barcodeExpr));
                }

                if(machineryPriceTransactionBalanceLM != null) {
                    skuQuery.addProperty("balanceMachineryPriceTransactionBarcode",
                            machineryPriceTransactionBalanceLM.findProperty("balance[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
                    skuQuery.addProperty("balanceDateMachineryPriceTransactionBarcode",
                            machineryPriceTransactionBalanceLM.findProperty("balanceDate[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
                }

                if(machineryPriceTransactionPromotionLM != null) {
                    skuQuery.addProperty("restrictionToDateTimeMachineryPriceTransactionBarcode",
                            machineryPriceTransactionPromotionLM.findProperty("restrictionToDateTime[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
                }

                if(machineryPriceTransactionLM != null) {
                    skuQuery.addProperty("manufactureDays", machineryPriceTransactionLM.findProperty("manufactureDays[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
                }

                skuQuery.and(equLM.findProperty("in[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> skuResult = skuQuery.execute(session);

                String[] machineryNames = new String[]{"nppMachinery", "portMachinery", "overDirectoryMachinery"};
                LP[] machineryProperties = machineryLM.findProperties("npp[Machinery]", "port[Machinery]", "overDirectory[Machinery]");

                if (isCashRegisterPriceTransaction) {

                    Map<String, List<CashRegisterItemBatch>> barcodeBatchMap = readBarcodeBatchMap(session, transactionObject);

                    LocalDate startDateGroupCashRegister = (LocalDate) cashRegisterLM.findProperty("startDate[GroupCashRegister]").read(session, groupMachineryObject);
                    boolean notDetailedGroupCashRegister = cashRegisterLM.findProperty("notDetailed[GroupCashRegister]").read(session, groupMachineryObject) != null;
                    Integer overDepartmentNumberGroupCashRegister = (Integer) cashRegisterLM.findProperty("overDepartmentNumberCashRegister[GroupMachinery]").read(session, groupMachineryObject);
                    String idDepartmentStoreGroupCashRegister = (String) cashRegisterLM.findProperty("idStock[GroupCashRegister]").read(session, groupMachineryObject);
                    String pieceCodeGroupCashRegister = (String) cashRegisterLM.findProperty("pieceCode[GroupCashRegister]").read(session, groupMachineryObject);
                    String weightCodeGroupCashRegister = (String) cashRegisterLM.findProperty("weightCode[GroupCashRegister]").read(session, groupMachineryObject);
                    String nameStockGroupCashRegister = (String) cashRegisterLM.findProperty("nameStock[GroupMachinery]").read(session, groupMachineryObject);
                    String sectionGroupCashRegister = (String) cashRegisterLM.findProperty("section[GroupMachinery]").read(session, groupMachineryObject);

                    List<CashRegisterInfo> cashRegisterInfoList = new ArrayList<>();
                    KeyExpr cashRegisterExpr = new KeyExpr("cashRegister");
                    ImRevMap<Object, KeyExpr> cashRegisterKeys = MapFact.singletonRev("cashRegister", cashRegisterExpr);
                    QueryBuilder<Object, Object> cashRegisterQuery = new QueryBuilder<>(cashRegisterKeys);

                    for (int i = 0; i < machineryProperties.length; i++) {
                        cashRegisterQuery.addProperty(machineryNames[i], machineryProperties[i].getExpr(cashRegisterExpr));
                    }
                    cashRegisterQuery.addProperty("disableSalesCashRegister", cashRegisterLM.findProperty("disableSales[CashRegister]").getExpr(cashRegisterExpr));
                    cashRegisterQuery.addProperty("succeededMachineryMachineryPriceTransaction",
                            cashRegisterLM.findProperty("succeeded[Machinery,MachineryPriceTransaction]").getExpr(cashRegisterExpr, transactionExpr));
                    cashRegisterQuery.addProperty("clearedMachineryMachineryPriceTransaction",
                            cashRegisterLM.findProperty("cleared[Machinery,MachineryPriceTransaction]").getExpr(cashRegisterExpr, transactionExpr));
                    cashRegisterQuery.addProperty("inMachineryPriceTransactionMachinery",
                            equLM.findProperty("in[MachineryPriceTransaction,Machinery]").getExpr(transactionExpr, cashRegisterExpr));
                    cashRegisterQuery.and(cashRegisterLM.findProperty("groupCashRegister[CashRegister]").getExpr(cashRegisterExpr).compare(groupMachineryObject, Compare.EQUALS));
                    cashRegisterQuery.and(cashRegisterLM.findProperty("active[CashRegister]").getExpr(cashRegisterExpr).getWhere());

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> cashRegisterResult = cashRegisterQuery.execute(session);

                    for (ImMap<Object, Object> row : cashRegisterResult.valueIt()) {
                        Integer nppMachinery = (Integer) row.get("nppMachinery");
                        String portMachinery = trim((String) row.get("portMachinery"));
                        String directoryCashRegister = trim((String) row.get("overDirectoryMachinery"));
                        boolean succeeded = row.get("succeededMachineryMachineryPriceTransaction") != null;
                        boolean cleared = row.get("clearedMachineryPriceTransaction") != null;
                        Boolean disableSalesCashRegister = row.get("disableSalesCashRegister") != null;
                        boolean enabled = row.get("inMachineryPriceTransactionMachinery") != null;
                        cashRegisterInfoList.add(new CashRegisterInfo(enabled, cleared, succeeded, nppGroupMachinery, nppMachinery,
                                handlerModelGroupMachinery, portMachinery, directoryCashRegister,
                                startDateGroupCashRegister, overDepartmentNumberGroupCashRegister, idDepartmentStoreGroupCashRegister, notDetailedGroupCashRegister,
                                disableSalesCashRegister, pieceCodeGroupCashRegister, weightCodeGroupCashRegister, sectionGroupCashRegister, null, null, false));
                    }

                    List<CashRegisterItem> cashRegisterItemList = new ArrayList<>();

                    for (int i = 0, size = skuResult.size(); i < size; i++) {
                        ImMap<Object, Object> row = skuResult.getValue(i);

                        Long barcodeObject = (Long) row.get("valueBarcode");
                        String barcode = getRowValue(row, "idBarcode");
                        String mainBarcode = getRowValue(row, "mainBarcode");
                        BigDecimal amountBarcode = (BigDecimal) row.get("amountBarcode");
                        String name = getRowValue(row, "nameMachineryPriceTransactionBarcode");
                        BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode");
                        boolean split = row.get("splitMachineryPriceTransactionBarcode") != null;
                        Integer daysExpiry = (Integer) row.get("expiryDaysMachineryPriceTransactionBarcode");
                        Integer hoursExpiry = (Integer) row.get("hoursExpiryMachineryPriceTransactionBarcode");
                        LocalDate expiryDate = (LocalDate) row.get("expiryDateMachineryPriceTransactionBarcode");
                        Integer flags = (Integer) row.get("flagsMachineryPriceTransactionBarcode");
                        boolean passScales = row.get("passScalesMachineryPriceTransactionBarcode") != null;
                        String idUOM = (String) row.get("idUOMMachineryPriceTransactionBarcode");
                        String shortNameUOM = (String) row.get("shortNameUOMMachineryPriceTransactionBarcode");
                        String info = (String) row.get("infoMPTBarcode");
                        String extraInfo = (String) row.get("extraInfoMPTBarcode");
                        String idBrand = itemLM == null ? null : (String) row.get("idBrandBarcode");
                        String nameBrand = itemLM == null ? null : (String) row.get("nameBrandBarcode");
                        String idSeason = itemFashionLM == null ? null : (String) row.get("idSeasonBarcode");
                        String nameSeason = itemFashionLM == null ? null : (String) row.get("nameSeasonBarcode");
                        BigDecimal valueVAT = machineryPriceTransactionStockTaxLM == null ? null : (BigDecimal) row.get("VATMachineryPriceTransactionBarcode");
                        String idItem = (String) row.get("idSkuBarcode");
                        Long itemGroupObject = (Long) row.get("skuGroupBarcode");
                        Integer pluNumber = (Integer) row.get("pluNumberMachineryPriceTransactionBarcode");
                        String description = scalesItemLM == null ? null : (String) row.get("descriptionMachineryPriceTransactionBarcode");

                        String idItemGroup = cashRegisterItemLM == null ? null : (String) row.get("CashRegisterItem.idSkuGroupMachineryPriceTransactionBarcode");
                        String overIdItemGroup = cashRegisterItemLM == null ? null : (String) row.get("CashRegisterItem.overIdSkuGroupMachineryPriceTransactionBarcode");
                        String canonicalNameSkuGroup = (String) row.get("canonicalNameSkuGroupMachineryPriceTransactionBarcode");
                        String section = machineryPriceTransactionSectionLM == null ? null : (String) row.get("sectionMachineryPriceTransactionBarcode");
                        String deleteSection = machineryPriceTransactionSectionLM == null ? null : (String) row.get("deleteSectionBarcode");
                        BigDecimal balance = machineryPriceTransactionBalanceLM == null ? null : (BigDecimal) row.get("balanceMachineryPriceTransactionBarcode");
                        LocalDateTime balanceDate = machineryPriceTransactionBalanceLM == null ? null : (LocalDateTime) row.get("balanceDateMachineryPriceTransactionBarcode");
                        BigDecimal minPrice = (BigDecimal) row.get("minPriceMachineryPriceTransactionBarcode");
                        LocalDateTime restrictionToDateTime = (LocalDateTime) row.get("restrictionToDateTimeMachineryPriceTransactionBarcode");

                        Integer manufactureDays = (Integer) row.get("manufactureDays");

                        CashRegisterItem c = new CashRegisterItem(idItem, barcode, name, price, split, daysExpiry, hoursExpiry, expiryDate, passScales, valueVAT,
                                pluNumber, flags, idItemGroup, canonicalNameSkuGroup, idUOM, shortNameUOM, info, extraInfo, itemGroupObject, description, idBrand, nameBrand,
                                idSeason, nameSeason, section, deleteSection, minPrice, overIdItemGroup, amountBarcode,
                                balance, balanceDate, restrictionToDateTime, barcodeObject, mainBarcode, manufactureDays, barcodeBatchMap.get(barcode));
                        cashRegisterItemList.add(c);
                    }

                    transactionList.add(new TransactionCashRegisterInfo((Long) transactionObject.getValue(), dateTimeCode,
                            date, handlerModelGroupMachinery, (Long) groupMachineryObject.object, nppGroupMachinery,
                            nameGroupMachinery, descriptionTransaction, itemGroupMap, cashRegisterItemList,
                            cashRegisterInfoList, snapshotTransaction, lastErrorDateTransaction, overDepartmentNumberGroupCashRegister,
                            idDepartmentStoreGroupCashRegister, pieceCodeGroupCashRegister, weightCodeGroupCashRegister, nameStockGroupCashRegister, infoMPT));

                } else if (isScalesPriceTransaction) {
                    List<ScalesInfo> scalesInfoList = new ArrayList<>();
                    String directory = trim((String) scalesLM.findProperty("directory[GroupScales]").read(session, groupMachineryObject));
                    String pieceCodeGroupScales = (String) scalesLM.findProperty("pieceCode[GroupScales]").read(session, groupMachineryObject);
                    String weightCodeGroupScales = (String) scalesLM.findProperty("weightCode[GroupScales]").read(session, groupMachineryObject);

                    KeyExpr scalesExpr = new KeyExpr("scales");
                    ImRevMap<Object, KeyExpr> scalesKeys = MapFact.singletonRev("scales", scalesExpr);
                    QueryBuilder<Object, Object> scalesQuery = new QueryBuilder<>(scalesKeys);

                    for (int i = 0; i < machineryProperties.length; i++) {
                        scalesQuery.addProperty(machineryNames[i], machineryProperties[i].getExpr(scalesExpr));
                    }
                    scalesQuery.addProperty("inMachineryPriceTransactionMachinery",
                            scalesLM.findProperty("in[MachineryPriceTransaction,Machinery]").getExpr(transactionExpr, scalesExpr));
                    scalesQuery.addProperty("succeededMachineryMachineryPriceTransaction",
                                scalesLM.findProperty("succeeded[Machinery,MachineryPriceTransaction]").getExpr(scalesExpr, transactionExpr));
                    scalesQuery.addProperty("clearedMachineryMachineryPriceTransaction",
                            scalesLM.findProperty("cleared[Machinery,MachineryPriceTransaction]").getExpr(scalesExpr, transactionExpr));
                    scalesQuery.addProperty("activeScales", scalesLM.findProperty("active[Scales]").getExpr(scalesExpr));
                    scalesQuery.and(scalesLM.findProperty("groupScales[Scales]").getExpr(scalesExpr).compare(groupMachineryObject, Compare.EQUALS));
                    //scalesQuery.and(scalesLM.findProperty("active[Scales]").getExpr(scalesExpr).getWhere());

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> scalesResult = scalesQuery.execute(session);

                    int enabledCount = 0;
                    int enabledInactiveCount = 0;
                    for (ImMap<Object, Object> values : scalesResult.valueIt()) {
                        String portMachinery = trim((String) values.get("portMachinery"));
                        Integer nppMachinery = (Integer) values.get("nppMachinery");
                        boolean enabled = values.get("inMachineryPriceTransactionMachinery") != null;
                        boolean succeeded = values.get("succeededMachineryMachineryPriceTransaction") != null;
                        boolean cleared = values.get("clearedMachineryMachineryPriceTransaction") != null;
                        boolean active = values.get("activeScales") != null;
                        if(enabled) {
                            enabledCount++;
                            if(!active)
                                enabledInactiveCount++;
                        }
                        if(active)
                            scalesInfoList.add(new ScalesInfo(enabled, cleared, succeeded, nppGroupMachinery, nppMachinery,
                                handlerModelGroupMachinery, portMachinery, directory,
                                pieceCodeGroupScales, weightCodeGroupScales));
                    }
                    //если все отмеченные - неактивные, то не посылаем весы вообще
                    if(enabledCount > 0 && enabledCount==enabledInactiveCount)
                        scalesInfoList = new ArrayList<>();

                    List<ScalesItem> scalesItemList = new ArrayList<>();

                    for (ImMap<Object, Object> row : skuResult.valueIt()) {
                        String idItem = getRowValue(row, "idSkuBarcode");
                        String barcode = getRowValue(row, "idBarcode");
                        String name = getRowValue(row, "nameMachineryPriceTransactionBarcode");
                        BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode");
                        Integer pluNumber = (Integer) row.get("pluNumberMachineryPriceTransactionBarcode");
                        Integer flags = (Integer) row.get("flagsMachineryPriceTransactionBarcode");
                        LocalDate expiryDate = (LocalDate) row.get("expiryDateMachineryPriceTransactionBarcode");
                        boolean split = row.get("splitMachineryPriceTransactionBarcode") != null;
                        Integer daysExpiry = (Integer) row.get("expiryDaysMachineryPriceTransactionBarcode");
                        Integer hoursExpiry = (Integer) row.get("hoursExpiryMachineryPriceTransactionBarcode");
                        String description = (String) row.get("descriptionMachineryPriceTransactionBarcode");
                        Integer descriptionNumberCellScales = (Integer) row.get("descriptionNumberMachineryPriceTransactionBarcode");
                        boolean passScales = row.get("passScalesMachineryPriceTransactionBarcode") != null;
                        BigDecimal valueVAT = machineryPriceTransactionStockTaxLM == null ? null : (BigDecimal) row.get("VATMachineryPriceTransactionBarcode");
                        String idUOM = (String) row.get("idUOMMachineryPriceTransactionBarcode");
                        String shortNameUOM = (String) row.get("shortNameUOMMachineryPriceTransactionBarcode");
                        String info = (String) row.get("infoMPTBarcode");
                        String extraInfo = (String) row.get("extraInfoMPTBarcode");

                        String idItemGroup = scalesItemLM == null ? null : (String) row.get("ScalesItem.idSkuGroupMachineryPriceTransactionBarcode");
                        String canonicalNameSkuGroup = (String) row.get("canonicalNameSkuGroupMachineryPriceTransactionBarcode");
                        BigDecimal extraPercent = scalesItemLM == null ? null : (BigDecimal) row.get("extraPercent");

                        BigDecimal retailPrice = (BigDecimal) row.get("retailPrice");
                        Integer imagesCount = (Integer) row.get("imagesCount");

                        RawFileData groupImage = (RawFileData) row.get("groupImageMPTBarcode");
                        RawFileData itemImage = (RawFileData) row.get("itemImageMPTBarcode");

                        scalesItemList.add(new ScalesItem(idItem, barcode, name, price, split, daysExpiry, hoursExpiry,
                                expiryDate, passScales, valueVAT, pluNumber, flags, idItemGroup, canonicalNameSkuGroup,
                                null, description, descriptionNumberCellScales, idUOM, shortNameUOM, info, extraInfo, extraPercent,
                                retailPrice, imagesCount, groupImage, itemImage));
                    }

                    transactionList.add(new TransactionScalesInfo((Long) transactionObject.getValue(), dateTimeCode,
                            date, handlerModelGroupMachinery, (Long) groupMachineryObject.object, nppGroupMachinery,
                            nameGroupMachinery, descriptionTransaction, scalesItemList, scalesInfoList, snapshotTransaction,
                            lastErrorDateTransaction, infoMPT));

                } else if (isPriceCheckerPriceTransaction) {
                    List<PriceCheckerInfo> priceCheckerInfoList = new ArrayList<>();
                    KeyExpr priceCheckerExpr = new KeyExpr("priceChecker");
                    ImRevMap<Object, KeyExpr> priceCheckerKeys = MapFact.singletonRev("priceChecker", priceCheckerExpr);
                    QueryBuilder<Object, Object> priceCheckerQuery = new QueryBuilder<>(priceCheckerKeys);

                    for (int i = 0; i < machineryProperties.length; i++) {
                        priceCheckerQuery.addProperty(machineryNames[i], machineryProperties[i].getExpr(priceCheckerExpr));
                    }
                    priceCheckerQuery.addProperty("inMachineryPriceTransactionMachinery",
                            priceCheckerLM.findProperty("in[MachineryPriceTransaction,Machinery]").getExpr(transactionExpr, priceCheckerExpr));
                    priceCheckerQuery.addProperty("succeededMachineryMachineryPriceTransaction",
                            priceCheckerLM.findProperty("succeeded[Machinery,MachineryPriceTransaction]").getExpr(priceCheckerExpr, transactionExpr));
                    priceCheckerQuery.addProperty("clearedMachineryMachineryPriceTransaction",
                            priceCheckerLM.findProperty("cleared[Machinery,MachineryPriceTransaction]").getExpr(priceCheckerExpr, transactionExpr));
                    priceCheckerQuery.and(priceCheckerLM.findProperty("groupPriceChecker[PriceChecker]").getExpr(priceCheckerExpr).compare(groupMachineryObject, Compare.EQUALS));

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> priceCheckerResult = priceCheckerQuery.execute(session);

                    for (ImMap<Object, Object> row : priceCheckerResult.valueIt()) {
                        boolean enabled = row.get("inMachineryPriceTransactionMachinery") != null;
                        Boolean succeeded = row.get("succeededMachineryMachineryPriceTransaction") != null;
                        Boolean cleared = row.get("clearedMachineryMachineryPriceTransaction") != null;
                        priceCheckerInfoList.add(new PriceCheckerInfo(enabled, cleared, succeeded, nppGroupMachinery, (Integer) row.get("nppMachinery"),
                                handlerModelGroupMachinery, trim((String) row.get("portMachinery"))));
                    }

                    List<PriceCheckerItem> priceCheckerItemList = new ArrayList<>();
                    for (ImMap<Object, Object> row : skuResult.valueIt()) {
                        String idItem = getRowValue(row, "idSkuBarcode");
                        String barcode = getRowValue(row, "idBarcode");
                        String name = getRowValue(row, "nameMachineryPriceTransactionBarcode");
                        BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode");
                        boolean split = row.get("splitMachineryPriceTransactionBarcode") != null;
                        Integer daysExpiry = (Integer) row.get("expiryDaysMachineryPriceTransactionBarcode");
                        Integer hoursExpiry = (Integer) row.get("hoursExpiryMachineryPriceTransactionBarcode");
                        LocalDate expiryDate = (LocalDate) row.get("expiryDateMachineryPriceTransactionBarcode");
                        boolean passScales = row.get("passScalesMachineryPriceTransactionBarcode") != null;
                        BigDecimal valueVAT = machineryPriceTransactionStockTaxLM == null ? null : (BigDecimal) row.get("VATMachineryPriceTransactionBarcode");
                        Integer pluNumber = (Integer) row.get("pluNumberMachineryPriceTransactionBarcode");
                        Integer flags = (Integer) row.get("flagsMachineryPriceTransactionBarcode");

                        priceCheckerItemList.add(new PriceCheckerItem(idItem, barcode, name, price, split,
                                daysExpiry, hoursExpiry, expiryDate, passScales, valueVAT, pluNumber, flags, null, null, null, null));
                    }

                    transactionList.add(new TransactionPriceCheckerInfo((Long) transactionObject.getValue(), dateTimeCode,
                            date, handlerModelGroupMachinery, (Long) groupMachineryObject.object, nppGroupMachinery,
                            nameGroupMachinery, descriptionTransaction, priceCheckerItemList, priceCheckerInfoList,
                            snapshotTransaction, lastErrorDateTransaction, infoMPT));


                } else if (isTerminalPriceTransaction) {
                    List<TerminalInfo> terminalInfoList = new ArrayList<>();

                    Integer nppGroupTerminal = (Integer) terminalLM.findProperty("npp[GroupMachinery]").read(session, groupMachineryObject);
                    String directoryGroupTerminal = trim((String) terminalLM.findProperty("directory[GroupTerminal]").read(session, groupMachineryObject));
                    ObjectValue priceListTypeGroupMachinery = terminalLM.findProperty("priceListType[GroupMachinery]").readClasses(session, groupMachineryObject);
                    ObjectValue stockGroupTerminal = terminalLM.findProperty("stock[GroupTerminal]").readClasses(session, groupMachineryObject);
                    String idPriceListType = (String) terminalLM.findProperty("id[PriceListType]").read(session, priceListTypeGroupMachinery);

                    KeyExpr terminalExpr = new KeyExpr("terminal");
                    ImRevMap<Object, KeyExpr> terminalKeys = MapFact.singletonRev("terminal", terminalExpr);
                    QueryBuilder<Object, Object> terminalQuery = new QueryBuilder<>(terminalKeys);

                    for (int i = 0; i < machineryProperties.length; i++) {
                        terminalQuery.addProperty(machineryNames[i], machineryProperties[i].getExpr(terminalExpr));
                    }
                    terminalQuery.addProperty("inMachineryPriceTransactionMachinery",
                            terminalLM.findProperty("in[MachineryPriceTransaction,Machinery]").getExpr(transactionExpr, terminalExpr));
                    terminalQuery.addProperty("succeededMachineryMachineryPriceTransaction",
                            terminalLM.findProperty("succeeded[Machinery,MachineryPriceTransaction]").getExpr(terminalExpr, transactionExpr));
                    terminalQuery.addProperty("clearedMachineryMachineryPriceTransaction",
                            terminalLM.findProperty("cleared[Machinery,MachineryPriceTransaction]").getExpr(terminalExpr, transactionExpr));
                    terminalQuery.and(terminalLM.findProperty("groupTerminal[Terminal]").getExpr(terminalExpr).compare(groupMachineryObject, Compare.EQUALS));
                    terminalQuery.and(terminalLM.findProperty("active[Terminal]").getExpr(terminalExpr).getWhere());

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> terminalResult = terminalQuery.execute(session);

                    for (ImMap<Object, Object> row : terminalResult.valueIt()) {
                        boolean enabled = row.get("inMachineryPriceTransactionMachinery") != null;
                        Boolean succeeded = row.get("succeededMachineryMachineryPriceTransaction") != null;
                        Boolean cleared = row.get("clearedMachineryMachineryPriceTransaction") != null;
                        terminalInfoList.add(new TerminalInfo(enabled, cleared, succeeded, nppGroupMachinery, (Integer) row.get("nppMachinery"),
                                handlerModelGroupMachinery, getRowValue(row, "portMachinery"),
                                directoryGroupTerminal, idPriceListType));
                    }

                    List<TerminalItem> terminalItemList = new ArrayList<>();
                    for (ImMap<Object, Object> row : skuResult.valueIt()) {
                        String idItem = getRowValue(row, "idSkuBarcode");
                        String barcode = getRowValue(row, "idBarcode");
                        String name = getRowValue(row, "nameMachineryPriceTransactionBarcode");
                        BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode");
                        boolean split = row.get("splitMachineryPriceTransactionBarcode") != null;
                        Integer daysExpiry = (Integer) row.get("expiryDaysMachineryPriceTransactionBarcode");
                        Integer hoursExpiry = (Integer) row.get("hoursExpiryMachineryPriceTransactionBarcode");
                        LocalDate expiryDate = (LocalDate) row.get("expiryDateMachineryPriceTransactionBarcode");
                        Integer pluNumber = (Integer) row.get("pluNumberMachineryPriceTransactionBarcode");
                        Integer flags = (Integer) row.get("flagsMachineryPriceTransactionBarcode");
                        boolean passScales = row.get("passScalesMachineryPriceTransactionBarcode") != null;
                        BigDecimal valueVAT = machineryPriceTransactionStockTaxLM == null ? null : (BigDecimal) row.get("VATMachineryPriceTransactionBarcode");
                        String canonicalNameSkuGroup = (String) row.get("canonicalNameSkuGroupMachineryPriceTransactionBarcode");
                        String extraInfo = (String) row.get("extraInfoMPTBarcode");

                        terminalItemList.add(new TerminalItem(idItem, barcode, name, price, split, daysExpiry, hoursExpiry,
                                expiryDate, passScales, valueVAT, pluNumber, flags, null, canonicalNameSkuGroup, null, extraInfo, null, null));
                    }

                    //закомментировали, чтобы снизить обьём передаваемых данных
                    List<TerminalAssortment> terminalAssortmentList = new ArrayList<>();//TerminalEquipmentServer.readTerminalAssortmentList(session, getBusinessLogics(), priceListTypeGroupMachinery, stockGroupTerminal);
                    List<TerminalHandbookType> terminalHandbookTypeList = TerminalEquipmentServer.readTerminalHandbookTypeList(session, getBusinessLogics());
                    List<TerminalDocumentType> terminalDocumentTypeList = TerminalEquipmentServer.readTerminalDocumentTypeListEquipment(session, getBusinessLogics(), groupMachineryObject);
                    List<TerminalLegalEntity> terminalLegalEntityList = TerminalEquipmentServer.readTerminalLegalEntityList(session, getBusinessLogics());

                    transactionList.add(new TransactionTerminalInfo((Long) transactionObject.getValue(), dateTimeCode,
                            date, handlerModelGroupMachinery, (Long) groupMachineryObject.object, nppGroupMachinery, nameGroupMachinery,
                            descriptionTransaction, terminalItemList, terminalInfoList, snapshotTransaction, lastErrorDateTransaction,
                            terminalHandbookTypeList, terminalDocumentTypeList, terminalLegalEntityList, terminalAssortmentList,
                            nppGroupTerminal, directoryGroupTerminal, infoMPT));
                }
            }
            logger.info("Sending " + transactionList.size() + " transactions");
            return transactionList;
        } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
            throw Throwables.propagate(e);
        }
    }

    private Map<String, List<CashRegisterItemBatch>> readBarcodeBatchMap(DataSession session, DataObject transactionObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, List<CashRegisterItemBatch>> result = new HashMap<>();
        if(machineryPriceTransactionBatchLM != null) {

            ValueExpr transactionExpr = transactionObject.getExpr();
            KeyExpr barcodeExpr = new KeyExpr("barcode");
            KeyExpr batchExpr = new KeyExpr("batch");
            ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap("barcode", barcodeExpr, "batch", batchExpr);

            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

            query.addProperty("idBarcode", machineryPriceTransactionBatchLM.findProperty("id[Barcode]").getExpr(barcodeExpr));

            String[] names = new String[]{"idBatch", "dateBatch", "expiryDate", "seriesPharmacy", "nameManufacturer", "price",
                    "nameATCGroup", "balance", "balanceBlister", "balanceDate", "sidCountry", "nameCountry", "blisterAmount",
                    "idDosage", "desctiptionDosage", "flag", "info"};
            LP[] properties = machineryPriceTransactionBatchLM.findProperties("id[MachineryPriceTransaction,Barcode,Batch]", "date[MachineryPriceTransaction,Barcode,Batch]",
                    "expiryDate[MachineryPriceTransaction,Barcode,Batch]", "seriesPharmacy[MachineryPriceTransaction,Barcode,Batch]",
                    "nameManufacturer[MachineryPriceTransaction,Barcode,Batch]", "price[MachineryPriceTransaction,Barcode,Batch]",
                    "nameATCGroup[MachineryPriceTransaction,Barcode,Batch]", "balance[MachineryPriceTransaction,Barcode,Batch]", "balanceBlister[MachineryPriceTransaction,Barcode,Batch]",
                    "balanceDate[MachineryPriceTransaction,Barcode,Batch]", "sidCountry[MachineryPriceTransaction,Barcode,Batch]",
                    "nameCountry[MachineryPriceTransaction,Barcode,Batch]", "blisterAmount[MachineryPriceTransaction,Barcode,Batch]",
                    "idDosage[MachineryPriceTransaction,Barcode,Batch]", "descriptionDosage[MachineryPriceTransaction,Barcode,Batch]",
                    "flag[MachineryPriceTransaction,Barcode,Batch]", "info[MachineryPriceTransaction,Barcode,Batch]");
            for (int i = 0; i < properties.length; i++) {
                query.addProperty(names[i], properties[i].getExpr(transactionExpr, barcodeExpr, batchExpr));
            }

            query.and(machineryPriceTransactionBatchLM.findProperty("in[MachineryPriceTransaction,Barcode,Batch]").getExpr(transactionExpr, barcodeExpr, batchExpr).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> queryResult = query.execute(session);

            for (int i = 0, size = queryResult.size(); i < size; i++) {
                ImMap<Object, Object> row = queryResult.getValue(i);

                String barcode = getRowValue(row, "idBarcode");

                String idBatch = getRowValue(row, "idBatch");
                LocalDate dateBatch = (LocalDate) row.get("dateBatch");
                LocalDate expiryDate = (LocalDate) row.get("expiryDate");
                String seriesPharmacy = getRowValue(row, "seriesPharmacy");
                String nameManufacturer = getRowValue(row, "nameManufacturer");
                BigDecimal price = (BigDecimal) row.get("price");
                String nameSubstance = getRowValue(row, "nameATCGroup");
                BigDecimal balance = (BigDecimal) row.get("balance");
                BigDecimal balanceBlister = (BigDecimal) row.get("balanceBlister");
                LocalDateTime balanceDate = (LocalDateTime) row.get("balanceDate");
                String countryCode = getRowValue(row, "sidCountry");
                String countryName = getRowValue(row, "nameCountry");
                Integer blisterAmount = (Integer) row.get("blisterAmount");
                String idDosage = (String) row.get("idDosage");
                String desctiptionDosage = (String) row.get("desctiptionDosage");
                Integer flag = (Integer) row.get("flag");
                String info = (String) row.get("info");

                List<CashRegisterItemBatch> batchList = result.getOrDefault(barcode, new ArrayList<>());
                batchList.add(new CashRegisterItemBatch(idBatch, dateBatch, expiryDate, seriesPharmacy, nameManufacturer,
                        price, nameSubstance, balance, balanceBlister, balanceDate, countryCode, countryName, blisterAmount,
                        idDosage, desctiptionDosage, flag, info));
                result.put(barcode, batchList);
            }
        }
        return result;
    }

    @Override
    public Map<String, List<Object>> readRequestZReportSumMap(String idStock, LocalDate dateFrom, LocalDate dateTo) {
        return SendSalesEquipmentServer.readRequestZReportSumMap(getBusinessLogics(), this, getStack(), idStock, dateFrom, dateTo);
    }

    @Override
    public void succeedTransaction(Long transactionId, LocalDateTime dateTime) {
        synchronized (this) {
            try (DataSession session = createSession()) {
                DataObject transactionObject = session.getDataObject((CustomClass)equLM.findClass("MachineryPriceTransaction"), transactionId);
                equLM.findProperty("succeeded[MachineryPriceTransaction]").change(true, session, transactionObject);
                equLM.findProperty("dateTimeSucceeded[MachineryPriceTransaction]").change(dateTime, session, transactionObject);
                session.applyException(getBusinessLogics(), getStack());
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public void processingTransaction(Long transactionId, LocalDateTime dateTime) {
        if (machineryPriceTransactionLM != null) {
            try (DataSession session = createSession()) {
                DataObject transactionObject = session.getDataObject((CustomClass)equLM.findClass("MachineryPriceTransaction"), transactionId);
                machineryPriceTransactionLM.findProperty("dateTimeProcessing[MachineryPriceTransaction]").change(dateTime, session, transactionObject);
                session.applyException(getBusinessLogics(), getStack());
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public void succeedMachineryTransaction(Long transactionId, List<MachineryInfo> machineryInfoList, LocalDateTime dateTime) {
        synchronized (this) {
            if (machineryPriceTransactionLM != null) {
                try (DataSession session = createSession()) {
                    DataObject transactionObject = session.getDataObject((CustomClass)equLM.findClass("MachineryPriceTransaction"), transactionId);
                    for (MachineryInfo machineryInfo : machineryInfoList) {
                        ObjectValue machineryObject = null;
                        if (machineryInfo instanceof CashRegisterInfo && cashRegisterLM != null)
                            machineryObject = cashRegisterLM.findProperty("cashRegisterNppGroupCashRegister[INTEGER,INTEGER]").readClasses(session, new DataObject(machineryInfo.numberGroup), new DataObject(machineryInfo.number));
                        else if (machineryInfo instanceof ScalesInfo && scalesLM != null)
                            machineryObject = scalesLM.findProperty("scalesNppGroupScales[INTEGER,INTEGER]").readClasses(session, new DataObject(machineryInfo.numberGroup), new DataObject(machineryInfo.number));
                        if (machineryObject != null && (!(machineryInfo instanceof CashRegisterInfo) || !((CashRegisterInfo) machineryInfo).succeeded)) {
                            boolean alreadySucceeded = machineryPriceTransactionLM.findProperty("succeeded[Machinery,MachineryPriceTransaction]").read(session, machineryObject, transactionObject) != null;
                            if (!alreadySucceeded) {
                                machineryPriceTransactionLM.findProperty("succeeded[Machinery,MachineryPriceTransaction]").change(true, session,
                                        (DataObject) machineryObject, transactionObject);
                                machineryPriceTransactionLM.findProperty("dateTimeSucceeded[Machinery,MachineryPriceTransaction]").change(dateTime, session,
                                        (DataObject) machineryObject, transactionObject);
                            }
                        }
                    }
                    session.applyException(getBusinessLogics(), getStack());
                } catch (Exception e) {
                    throw Throwables.propagate(e);
                }
            }
        }
    }

    @Override
    public EquipmentServerSettings readEquipmentServerSettings(String equipmentServer) {
        try {
            ThreadLocalContext.assureRmi(this);
            try (DataSession session = createSession()) {
                ObjectValue equipmentServerObject = equLM.findProperty("sidTo[STRING[20]]").readClasses(session, new DataObject(equipmentServer));
                if (equipmentServerObject instanceof DataObject) {
                    LocalTime timeFrom = (LocalTime) equLM.findProperty("timeFrom[EquipmentServer]").read(session, equipmentServerObject);
                    LocalTime timeTo = (LocalTime) equLM.findProperty("timeTo[EquipmentServer]").read(session, equipmentServerObject);
                    Integer delay = (Integer) equLM.findProperty("delay[EquipmentServer]").read(session, equipmentServerObject);
                    Integer sendSalesDelay = (Integer) equLM.findProperty("sendSalesDelay[EquipmentServer]").read(session, equipmentServerObject);
                    return new EquipmentServerSettings(timeFrom, timeTo, delay, sendSalesDelay);
                } else return null;
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    protected String formatDate(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    private List<Object> getReceiptDetailRow(SalesInfo sale, BarcodePart barcodePart, String barcode, EquipmentServerOptions options) {
        BigDecimal sumCashEnd = sale.zReportExtraFields != null ? (BigDecimal) sale.zReportExtraFields.get("sumCashEnd") : null;
        if (sumCashEnd != null && sumCashEnd.compareTo(BigDecimal.valueOf(1000000000000L)) > 0) sumCashEnd = null;
        BigDecimal sumProtectedEnd = sale.zReportExtraFields != null ? (BigDecimal) sale.zReportExtraFields.get("sumProtectedEnd") : null;
        if (sumProtectedEnd != null && sumProtectedEnd.compareTo(BigDecimal.valueOf(1000000000000L)) > 0) sumProtectedEnd = null;
        BigDecimal sumBack = sale.zReportExtraFields != null ? (BigDecimal) sale.zReportExtraFields.get("sumBack") : null;
        if (sumBack != null && sumBack.compareTo(BigDecimal.valueOf(1000000000000L)) > 0) sumBack = null;

        BigDecimal externalSum = sale.zReportExtraFields != null ? (BigDecimal) sale.zReportExtraFields.get("externalSum") : null;
        if (externalSum != null && externalSum.compareTo(BigDecimal.valueOf(1000000000000L)) > 0) externalSum = null;
        LocalDateTime beginShift = sale.zReportExtraFields != null ? (LocalDateTime) sale.zReportExtraFields.get("beginShift") : null;
        LocalDateTime endShift = sale.zReportExtraFields != null ? (LocalDateTime) sale.zReportExtraFields.get("endShift") : null;

        BigDecimal bonusSum = sale.detailExtraFields != null ? (BigDecimal) sale.detailExtraFields.get("bonusSum") : null;
        BigDecimal bonusPaid = sale.detailExtraFields != null ? (BigDecimal) sale.detailExtraFields.get("bonusPaid") : null;
        if(bonusPaid != null && zReportBonusLM == null) {
            sale.discountSumReceiptDetail = safeAdd(sale.discountSumReceiptDetail, bonusPaid);
        }
        String externalNumber = sale.detailExtraFields != null ? (String) sale.detailExtraFields.get("externalNumber") : null;
        String idBatch = sale.detailExtraFields != null ? (String) sale.detailExtraFields.get("idBatch") : null;
        String idLot = sale.detailExtraFields != null ? (String) sale.detailExtraFields.get("idLot") : null;

        BigDecimal sumVAT = sale.detailExtraFields != null ? (BigDecimal) sale.detailExtraFields.get("sumVAT") : null;

        String idReceiptDetail = getIdReceiptDetail(sale, options) + (barcodePart != null ? ("_" + barcodePart.index) : "");
        BigDecimal quantity = barcodePart != null ? barcodePart.quantity : sale.quantityReceiptDetail;
        BigDecimal price = barcodePart != null ? barcodePart.price : sale.priceReceiptDetail;
        BigDecimal sum = barcodePart != null ? barcodePart.sum : sale.sumReceiptDetail;
        BigDecimal discount = barcodePart != null ? barcodePart.discountSum : sale.discountSumReceiptDetail;

        List<Object> row = new ArrayList<>(Arrays.asList(sale.nppGroupMachinery, sale.nppMachinery, getIdZReport(sale, options),
                sale.numberZReport, sale.dateZReport, sale.timeZReport, sumCashEnd, sumProtectedEnd, sumBack, true));

        List<Object> commonReceiptFields = new ArrayList<>(Arrays.asList(getIdReceipt(sale, options), sale.numberReceipt, sale.dateReceipt, sale.timeReceipt, sale.skipReceipt ? true : null));
        if(zReportExternalNumberLM != null) {
            commonReceiptFields.add(externalNumber);
        }
        row.addAll(commonReceiptFields);

        List<Object> receiptFields = Arrays.asList(sale.idEmployee, sale.firstNameContact, sale.lastNameContact, idReceiptDetail, sale.numberReceiptDetail, barcodePart != null ? barcodePart.id : barcode);
        row.addAll(receiptFields);

        if (sale.isGiftCard) {
            //giftCard 3
            row.addAll(Arrays.asList(sale.priceReceiptDetail, sale.sumReceiptDetail, sale.isReturnGiftCard ? true : null));
            if (zReportSectionLM != null) {
                row.add(sale.idSection);
            }
        } else if (sale.quantityReceiptDetail.doubleValue() < 0) {
            //return 3
            row.addAll(Arrays.asList(safeNegate(quantity), price, safeNegate(sum), discount, sale.discountSumReceipt, sale.idSaleReceiptReceiptReturnDetail));
            if (zReportDiscountCardLM != null) {
                row.add(sale.seriesNumberDiscountCard);
            }
            if (zReportSectionLM != null) {
                row.add(sale.idSection);
            }
        } else {
            //sale 3
            row.addAll(Arrays.asList(quantity, price, sum, sale.discountPercentReceiptDetail, discount, sale.discountSumReceipt));
            if (zReportDiscountCardLM != null) {
                row.add(sale.seriesNumberDiscountCard);
            }
            if (zReportSectionLM != null) {
                row.add(sale.idSection);
            }
        }

        //optional properties
        if (zReportExternalLM != null) {
            row.add(externalSum);
            row.add(beginShift);
            row.add(endShift);
        }

        if(!sale.isGiftCard) { //sale or return
            if(zReportBonusLM != null) {
                row.add(bonusSum);
                row.add(bonusPaid);
            }
            if(zReportBatchLM != null) {
                row.add(idBatch);
            }
            if(zReportLotLM != null)
                row.add(idLot);
            if (options.receiveVATSales)
                row.add(sumVAT);
        }

        return row;
    }
    
    private class OptionalProperties {
        List<ImportField> saleFields = new ArrayList<>();
        List<ImportKey<?>> saleKeys = new ArrayList<>();
        List<ImportProperty<?>> saleProperties = new ArrayList<>();

        List<ImportField> returnFields = new ArrayList<>();
        List<ImportKey<?>> returnKeys = new ArrayList<>();
        List<ImportProperty<?>> returnProperties = new ArrayList<>();

        List<ImportField> giftCardFields = new ArrayList<>();
        List<ImportKey<?>> giftCardKeys = new ArrayList<>();
        List<ImportProperty<?>> giftCardProperties = new ArrayList<>();
        
        public OptionalProperties(ImportKey<?> zReportKey, ImportKey<?> receiptSaleDetailKey, ImportKey<?> receiptReturnDetailKey, EquipmentServerOptions options) throws ScriptingErrorLog.SemanticErrorException {

            if (zReportExternalLM != null) {
                ImportField externalSumZReportField = new ImportField(zReportExternalLM.findProperty("externalSum[ZReport]"));
                ImportField beginShiftZReportField = new ImportField(zReportExternalLM.findProperty("beginShift[ZReport]"));
                ImportField endShiftZReportField = new ImportField(zReportExternalLM.findProperty("endShift[ZReport]"));

                List<ImportField> zReportExternalFields = Arrays.asList(externalSumZReportField, beginShiftZReportField, endShiftZReportField);

                List<ImportProperty<?>> zReportExternalProperties = new ArrayList<>();
                zReportExternalProperties.add(new ImportProperty(externalSumZReportField, zReportExternalLM.findProperty("externalSum[ZReport]").getMapping(zReportKey)));
                zReportExternalProperties.add(new ImportProperty(beginShiftZReportField, zReportExternalLM.findProperty("beginShift[ZReport]").getMapping(zReportKey)));
                zReportExternalProperties.add(new ImportProperty(endShiftZReportField, zReportExternalLM.findProperty("endShift[ZReport]").getMapping(zReportKey), true));

                saleFields.addAll(zReportExternalFields);
                returnFields.addAll(zReportExternalFields);
                giftCardFields.addAll(zReportExternalFields);

                saleProperties.addAll(zReportExternalProperties);
                returnProperties.addAll(zReportExternalProperties);
                giftCardProperties.addAll(zReportExternalProperties);
            }

            if(zReportBonusLM != null) {
                ImportField bonusSumReceiptSaleDetailField = new ImportField(zReportBonusLM.findProperty("bonusSum[ReceiptSaleDetail]"));
                ImportField bonusPaidReceiptSaleDetailField = new ImportField(zReportBonusLM.findProperty("bonusPaid[ReceiptSaleDetail]"));
                ImportField bonusSumReceiptReturnDetailField = new ImportField(zReportBonusLM.findProperty("bonusSum[ReceiptReturnDetail]"));
                ImportField bonusPaidReceiptReturnDetailField = new ImportField(zReportBonusLM.findProperty("bonusPaid[ReceiptReturnDetail]"));

                saleFields.add(bonusSumReceiptSaleDetailField);
                saleFields.add(bonusPaidReceiptSaleDetailField);

                saleProperties.add(new ImportProperty(bonusSumReceiptSaleDetailField, zReportBonusLM.findProperty("bonusSum[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey)));
                saleProperties.add(new ImportProperty(bonusPaidReceiptSaleDetailField, zReportBonusLM.findProperty("bonusPaid[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey)));
                
                returnFields.add(bonusSumReceiptReturnDetailField);
                returnFields.add(bonusPaidReceiptReturnDetailField);
                
                returnProperties.add(new ImportProperty(bonusSumReceiptReturnDetailField, zReportBonusLM.findProperty("bonusSum[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey)));
                returnProperties.add(new ImportProperty(bonusPaidReceiptReturnDetailField, zReportBonusLM.findProperty("bonusPaid[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey)));
            }

            if (zReportBatchLM != null) {
                ImportField idBatchField = new ImportField(zReportBatchLM.findProperty("id[Batch]"));
                saleFields.add(idBatchField);
                returnFields.add(idBatchField);

                ImportKey<?> batchKey = new ImportKey((CustomClass) zReportBatchLM.findClass("Batch"), zReportBatchLM.findProperty("batch[STRING[100]]").getMapping(idBatchField));
                saleKeys.add(batchKey);
                returnKeys.add(batchKey);
                
                saleProperties.add(new ImportProperty(idBatchField, zReportBatchLM.findProperty("id[Batch]").getMapping(batchKey), true));
                saleProperties.add(new ImportProperty(idBatchField, zReportBatchLM.findProperty("batch[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey),
                        zReportBatchLM.object(zReportBatchLM.findClass("Batch")).getMapping(batchKey), true));
                
                returnProperties.add(new ImportProperty(idBatchField, zReportBatchLM.findProperty("id[Batch]").getMapping(batchKey), true));
                returnProperties.add(new ImportProperty(idBatchField, zReportBatchLM.findProperty("batch[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey),
                        zReportBatchLM.object(zReportBatchLM.findClass("Batch")).getMapping(batchKey), true));
            }

            if (zReportLotLM != null) {
                ImportField idLotField = new ImportField(zReportLotLM.findProperty("id[Lot]"));
                saleFields.add(idLotField);
                returnFields.add(idLotField);

                ImportKey<?> lotKey = new ImportKey((CustomClass) zReportLotLM.findClass("Lot"), zReportLotLM.findProperty("lot[STRING[200]]").getMapping(idLotField));
                lotKey.skipKey = true;
                saleKeys.add(lotKey);
                returnKeys.add(lotKey);

                saleProperties.add(new ImportProperty(idLotField, zReportLotLM.findProperty("lot[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey),
                        zReportLotLM.object(zReportLotLM.findClass("Lot")).getMapping(lotKey)));

                returnProperties.add(new ImportProperty(idLotField, zReportLotLM.findProperty("lot[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey),
                        zReportLotLM.object(zReportLotLM.findClass("Lot")).getMapping(lotKey)));
            }

            if (options.receiveVATSales) {
                ImportField sumVATReceiptSaleDetailField = new ImportField(zReportLM.findProperty("sumVAT[ReceiptDetail]"));
                ImportField sumVATReceiptReturnDetailField = new ImportField(zReportLM.findProperty("sumVAT[ReceiptDetail]"));

                saleFields.add(sumVATReceiptSaleDetailField);
                saleProperties.add(new ImportProperty(sumVATReceiptSaleDetailField, zReportLM.findProperty("sumVAT[ReceiptDetail]").getMapping(receiptSaleDetailKey)));

                returnFields.add(sumVATReceiptReturnDetailField);
                returnProperties.add(new ImportProperty(sumVATReceiptReturnDetailField, zReportLM.findProperty("sumVAT[ReceiptDetail]").getMapping(receiptReturnDetailKey)));
            }
        }
    }
}
