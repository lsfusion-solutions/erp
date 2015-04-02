package equ.clt;

import equ.api.*;
import equ.api.cashregister.*;
import equ.api.scales.ScalesHandler;
import equ.api.terminal.*;
import lsfusion.interop.remote.RMIUtils;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.net.*;
import java.rmi.ConnectException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;

public class EquipmentServer {

    private Thread thread;

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger processStopListLogger = Logger.getLogger("StopListLogger");
    protected final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");
    protected final static Logger sendSoftCheckLogger = Logger.getLogger("SoftCheckLogger");
    protected final static Logger sendTerminalDocumentLogger = Logger.getLogger("TerminalDocumentLogger");
    protected final static Logger machineryExchangeLogger = Logger.getLogger("MachineryExchangeLogger");
    protected final static Logger logger = Logger.getLogger(EquipmentServer.class);
    
    Map<String, Object> handlerMap = new HashMap<String, Object>();
    EquipmentServerSettings equipmentServerSettings;

    Consumer processTransactionConsumer;
    Thread processTransactionThread;

    Consumer processStopListConsumer;
    Thread processStopListThread;

    Consumer sendSalesConsumer;
    Thread sendSalesThread;

    Consumer sendSoftCheckConsumer;
    Thread sendSoftCheckThread;

    Consumer sendTerminalDocumentConsumer;
    Thread sendTerminalDocumentThread;

    Consumer machineryExchangeConsumer;
    Thread machineryExchangeThread;

    ExecutorService singleTransactionExecutor;
    
    boolean needReconnect = false;

    public EquipmentServer(final String sidEquipmentServer, final String serverHost, final int serverPort, final String serverDB) {
        
        final int connectPort = serverPort > 0 ? serverPort : Registry.REGISTRY_PORT;

        thread = new Thread(new Runnable() {

            private EquipmentServerInterface remote = null;

            @Override
            public void run() {

                int millis = 10000;
                int sendSalesDelay = 0;
                int sendSalesDelayCounter = -1;
                               
                while (true) {

                    try {
                        
                        if(needReconnect) {
                            remote = null;
                            needReconnect = false;
                        }
                        
                        if (remote == null) {
                            try {
                                remote = RMIUtils.rmiLookup(serverHost, connectPort, serverDB, "EquipmentServer");
                            } catch (ConnectException e) {
                                logger.error("Naming lookup error : ", e);
                            } catch (NoSuchObjectException e) {
                                logger.error("Naming lookup error : ", e);
                            } catch (RemoteException e) {
                                logger.error("Naming lookup error : ", e);
                            } catch (MalformedURLException e) {
                                logger.error("Naming lookup error : ", e);
                            } catch (NotBoundException e) {
                                logger.error("Naming lookup error : ", e);
                            }

                            if (remote != null) {
                                try {
                                    equipmentServerSettings = remote.readEquipmentServerSettings(sidEquipmentServer);
                                    if (equipmentServerSettings == null) {
                                        logger.error("Equipment Server " + sidEquipmentServer + " not found");
                                    } else {
                                        if (equipmentServerSettings.delay != null)
                                            millis = equipmentServerSettings.delay;
                                        if(equipmentServerSettings.sendSalesDelay != null)
                                            sendSalesDelay = equipmentServerSettings.sendSalesDelay;
                                    }

                                    initDaemonThreads(remote, sidEquipmentServer);                                   
                                    
                                } catch (RemoteException e) {
                                    logger.error("Get remote logics error : ", e);
                                }
                            }
                        }

                        if (remote != null) {
                            processTransactionConsumer.scheduleIfNotScheduledYet();    

                            if(remote.enabledStopListInfo())
                                processStopListConsumer.scheduleIfNotScheduledYet();

                            if (sendSalesDelay == 0 || sendSalesDelayCounter >= sendSalesDelay || sendSalesDelayCounter == -1) {
                                sendSalesConsumer.scheduleIfNotScheduledYet();
                                sendSalesDelayCounter = 0;
                            } else {
                                sendSalesDelayCounter++;
                            }

                            if(remote.enabledSoftCheckInfo())
                                sendSoftCheckConsumer.scheduleIfNotScheduledYet();

                            if(remote.enabledTerminalInfo())
                                sendTerminalDocumentConsumer.scheduleIfNotScheduledYet();

                            machineryExchangeConsumer.scheduleIfNotScheduledYet();
                            
                            if(singleTransactionExecutor.isShutdown())
                                singleTransactionExecutor = Executors.newFixedThreadPool(5);
                        }

                    } catch (Exception e) {
                        logger.error("Unhandled exception : ", e);
                        remote = null;
                        processTransactionThread.interrupt();
                        processTransactionThread = null;
                        processStopListThread.interrupt();
                        processStopListThread = null;
                        sendSalesThread.interrupt();
                        sendSalesThread = null;
                        sendSoftCheckThread.interrupt();
                        sendSoftCheckThread = null;
                        sendTerminalDocumentThread.interrupt();
                        sendTerminalDocumentThread = null;
                        machineryExchangeThread.interrupt();
                        machineryExchangeThread = null;
                        
                        singleTransactionExecutor.shutdown();
                        singleTransactionExecutor = null;
                    }

                    try {
                        Thread.sleep(millis);
                    } catch (InterruptedException e) {
                        logger.error("Thread has been interrupted : ", e);
                        break;
                    }
                }
            }
        });
        thread.start();
    }

    private void initDaemonThreads(final EquipmentServerInterface remote, final String sidEquipmentServer) {
        processTransactionConsumer = new Consumer() {
            @Override
            void runTask() throws Exception{
                try {
                    processTransactionInfo(remote, sidEquipmentServer);
                } catch (ConnectException e) {
                    needReconnect = true;
                }
            }
         };
        processTransactionThread = new Thread(processTransactionConsumer);
        processTransactionThread.setDaemon(true);
        processTransactionThread.start();

        processStopListConsumer = new Consumer() {
            @Override
            void runTask() throws Exception{
                try {
                    processStopListInfo(remote, sidEquipmentServer);
                } catch (ConnectException e) {
                    needReconnect = true;
                }
            }
        };
        processStopListThread = new Thread(processStopListConsumer);
        processStopListThread.setDaemon(true);
        processStopListThread.start();

        sendSalesConsumer = new Consumer() {
            @Override
            void runTask() throws Exception{
                try {
                    sendSalesInfo(remote, sidEquipmentServer, equipmentServerSettings);
                } catch (ConnectException e) {
                    needReconnect = true;
                }
            }
        };
        sendSalesThread = new Thread(sendSalesConsumer);
        sendSalesThread.setDaemon(true);
        sendSalesThread.start();

        sendSoftCheckConsumer = new Consumer() {
            @Override
            void runTask() throws Exception{
                try {
                    sendSoftCheckInfo(remote);
                } catch (ConnectException e) {
                    needReconnect = true;
                }
            }
        };
        sendSoftCheckThread = new Thread(sendSoftCheckConsumer);
        sendSoftCheckThread.setDaemon(true);
        sendSoftCheckThread.start();

        sendTerminalDocumentConsumer = new Consumer() {
            @Override
            void runTask() throws Exception{
                try {
                    sendTerminalDocumentInfo(remote, sidEquipmentServer);
                } catch (ConnectException e) {
                    needReconnect = true;
                }
            }
        };
        sendTerminalDocumentThread = new Thread(sendTerminalDocumentConsumer);
        sendTerminalDocumentThread.setDaemon(true);
        sendTerminalDocumentThread.start();

        machineryExchangeConsumer = new Consumer() {
            @Override
            void runTask() throws Exception{
                try {
                    processMachineryExchange(remote, sidEquipmentServer);
                } catch (ConnectException e) {
                    needReconnect = true;
                }
            }
        };
        machineryExchangeThread = new Thread(machineryExchangeConsumer);
        machineryExchangeThread.setDaemon(true);
        machineryExchangeThread.start();

        singleTransactionExecutor = Executors.newFixedThreadPool(5);
    }


    private void processTransactionInfo(EquipmentServerInterface remote, String sidEquipmentServer) throws SQLException, RemoteException, FileNotFoundException, UnsupportedEncodingException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        processTransactionLogger.info("Process TransactionInfo: Start");
        List<TransactionInfo> transactionInfoList = remote.readTransactionInfo(sidEquipmentServer);
        Map<String, List<Object>> groupTransactionInfoMap = groupTransactionInfoList(remote, transactionInfoList);
        if (!groupTransactionInfoMap.isEmpty()) {
            Collection<Callable<Object>> taskList = new LinkedList<Callable<Object>>();
            for (Map.Entry<String, List<Object>> entry : groupTransactionInfoMap.entrySet()) {
                String groupId = entry.getKey();
                List<TransactionInfo> transactionEntry = (List<TransactionInfo>) entry.getValue().get(0);
                MachineryHandler clsHandler = (MachineryHandler) entry.getValue().get(1);
                taskList.add(Executors.callable(new SingleTransactionTask(remote, groupId, clsHandler, transactionEntry, sidEquipmentServer)));
            }
            
            try{
                singleTransactionExecutor.invokeAll(taskList);
            }catch(InterruptedException e){
                remote.errorEquipmentServerReport(sidEquipmentServer, e.fillInStackTrace());
            }
        }
        processTransactionLogger.info("Process TransactionInfo : Finish");
    }
    
    private Map<String, List<Object>> groupTransactionInfoList(EquipmentServerInterface remote, List<TransactionInfo> transactionInfoList) throws RemoteException, SQLException {
        Map<String, List<Object>> result = new HashMap<String, List<Object>>();
        Collections.sort(transactionInfoList, COMPARATOR);
        for(TransactionInfo transactionInfo : transactionInfoList) {
            try {
                Object clsHandler = transactionInfo.handlerModel == null ? null : getHandler(transactionInfo.handlerModel.trim(), remote);
                String groupId = clsHandler == null ? "No handler" : ((MachineryHandler) clsHandler).getGroupId(transactionInfo);
                List<TransactionInfo> entry = (List<TransactionInfo>) (result.containsKey(groupId) ? result.get(groupId).get(0) : new ArrayList<TransactionInfo>());
                entry.add(transactionInfo);
                result.put(groupId, Arrays.asList(entry, clsHandler));
            } catch (Exception e) {
                remote.errorTransactionReport(transactionInfo.id, e);
            }
        }
        return result;
    }

    private void processStopListInfo(EquipmentServerInterface remote, String sidEquipmentServer) throws RemoteException, SQLException {
        processStopListLogger.info("Process StopListInfo");
        List<StopListInfo> stopListInfoList = remote.readStopListInfo(sidEquipmentServer);
        for (StopListInfo stopListInfo : stopListInfoList) {
            
            boolean succeeded = true;
            for(Map.Entry<String, Set<String>> entry : stopListInfo.handlerDirectoryMap.entrySet()) {

                try {
                    Object clsHandler = getHandler(entry.getKey(), remote);
                    if (clsHandler instanceof CashRegisterHandler)
                        ((CashRegisterHandler) clsHandler).sendStopListInfo(stopListInfo, entry.getValue());
                } catch (Exception e) {
                    remote.errorStopListReport(stopListInfo.number, e);
                    succeeded = false;
                }
            }
            if(succeeded)
                remote.succeedStopList(stopListInfo.number, stopListInfo.idStockSet);
        }
        processStopListLogger.info("Process StopListInfo finished");
    }

    private void sendSalesInfo(EquipmentServerInterface remote, String sidEquipmentServer, EquipmentServerSettings settings) throws SQLException, IOException {
        sendSalesLogger.info("Send SalesInfo");
        Integer numberAtATime = equipmentServerSettings == null ? null : equipmentServerSettings.numberAtATime;
        List<CashRegisterInfo> cashRegisterInfoList = remote.readCashRegisterInfo(sidEquipmentServer);
        List<RequestExchange> requestExchangeList = remote.readRequestExchange(sidEquipmentServer);
        Set<Integer> succeededRequestsSet = new HashSet<Integer>();
        
        Map<String, Set<String>> handlerModelDirectoryMap = new HashMap<String, Set<String>>();
        for (CashRegisterInfo cashRegister : cashRegisterInfoList) {
            Set<String> directorySet = handlerModelDirectoryMap.containsKey(cashRegister.handlerModel) ? handlerModelDirectoryMap.get(cashRegister.handlerModel) : new HashSet<String>();
            directorySet.add(cashRegister.directory);
            handlerModelDirectoryMap.put(cashRegister.handlerModel, directorySet);
        }

        for (Map.Entry<String, Set<String>> entry : handlerModelDirectoryMap.entrySet()) {
            String handlerModel = entry.getKey();
            Set<String> directorySet = entry.getValue();

            if (handlerModel != null && !handlerModel.isEmpty()) {

                try {
                    
                    MachineryHandler clsHandler = (MachineryHandler) getHandler(handlerModel, remote);

                    if(clsHandler instanceof CashRegisterHandler) {
                    
                    Map succeededSoftCheckInfo = ((CashRegisterHandler) clsHandler).requestSucceededSoftCheckInfo(directorySet);
                    if (succeededSoftCheckInfo != null && !succeededSoftCheckInfo.isEmpty()) {
                        sendSalesLogger.info("Sending succeeded SoftCheckInfo (" + succeededSoftCheckInfo.size() + ")");
                        String result = remote.sendSucceededSoftCheckInfo(succeededSoftCheckInfo);
                        if (result != null)
                            reportEquipmentServerError(remote, sidEquipmentServer, result);
                    }

                    if (!requestExchangeList.isEmpty()) {
                        sendSalesLogger.info("Requesting SalesInfo");
                        String result = ((CashRegisterHandler) clsHandler).requestSalesInfo(requestExchangeList);
                        if (result == null) {
                            for (RequestExchange request : requestExchangeList) {
                                if (request.isSalesInfoExchange())
                                    succeededRequestsSet.add(request.requestExchange);
                            }
                        } else {
                            reportEquipmentServerError(remote, sidEquipmentServer, result);
                        }
                    }

                    Set<String> cashDocumentSet = remote.readCashDocumentSet(sidEquipmentServer);
                    CashDocumentBatch cashDocumentBatch = ((CashRegisterHandler) clsHandler).readCashDocumentInfo(cashRegisterInfoList, cashDocumentSet);
                    if (cashDocumentBatch != null && cashDocumentBatch.cashDocumentList != null && !cashDocumentBatch.cashDocumentList.isEmpty()) {
                        sendSalesLogger.info("Sending CashDocuments");
                        String result = remote.sendCashDocumentInfo(cashDocumentBatch.cashDocumentList, sidEquipmentServer);
                        if (result != null) {
                            reportEquipmentServerError(remote, sidEquipmentServer, result);
                        } else {
                            ((CashRegisterHandler) clsHandler).finishReadingCashDocumentInfo(cashDocumentBatch);
                        }
                    }

                    if(directorySet != null) {
                        for (String directory : directorySet) {
                            SalesBatch salesBatch = ((CashRegisterHandler) clsHandler).readSalesInfo(directory, cashRegisterInfoList);
                            if (salesBatch == null) {
                                sendSalesLogger.info("SalesInfo is empty");
                            } else {
                                sendSalesLogger.info("Sending SalesInfo");
                                String result = remote.sendSalesInfo(salesBatch.salesInfoList, sidEquipmentServer, numberAtATime);
                                if (result != null) {
                                    reportEquipmentServerError(remote, sidEquipmentServer, result);
                                } else {
                                    sendSalesLogger.info("Finish Reading starts");
                                    try {
                                        ((CashRegisterHandler) clsHandler).finishReadingSalesInfo(salesBatch);
                                    } catch (Exception e) {
                                        reportEquipmentServerError(remote, sidEquipmentServer, e.getMessage());
                                    }
                                }
                            }
                        }
                    }

                    ExtraCheckZReportBatch extraCheckResult = ((CashRegisterHandler) clsHandler).extraCheckZReportSum(cashRegisterInfoList, remote.readZReportSumMap());
                    if (extraCheckResult != null) {
                        if (extraCheckResult.message.isEmpty()) {
                            remote.succeedExtraCheckZReport(extraCheckResult.idZReportList);
                        } else {
                            reportEquipmentServerError(remote, sidEquipmentServer, extraCheckResult.message);
                        }
                    }

                    if (!requestExchangeList.isEmpty()) {
                        for (RequestExchange request : requestExchangeList) {
                            if (request.isCheckZReportExchange()) {
                                request.extraStockSet.add(request.idStock);
                                for (String idStock : request.extraStockSet) {
                                    Map<String, List<Object>> zReportSumMap = remote.readRequestZReportSumMap(idStock, request.dateFrom, request.dateTo);
                                    Map<Integer, List<List<Object>>> cashRegisterMap = remote.readCashRegistersStock(idStock);
                                    for(Map.Entry<Integer, List<List<Object>>> cashRegisterEntry : cashRegisterMap.entrySet()) {
                                        List<List<Object>> checkSumResult = zReportSumMap.isEmpty() ? null : 
                                                ((CashRegisterHandler) clsHandler).checkZReportSum(zReportSumMap, cashRegisterEntry.getValue());
                                        if (checkSumResult != null) {
                                            remote.logRequestZReportSumCheck(request.requestExchange, cashRegisterEntry.getKey(), checkSumResult);
                                        }
                                    }
                                }
                                succeededRequestsSet.add(request.requestExchange);
                            }
                        }
                    }

                    if (!succeededRequestsSet.isEmpty())
                        remote.finishRequestExchange(succeededRequestsSet);

                }
                } catch (Throwable e) {
                    sendSalesLogger.error("Equipment server error: ", e);
                    remote.errorEquipmentServerReport(sidEquipmentServer, e.fillInStackTrace());
                    return;
                }
            }
        }
    }

    private void sendSoftCheckInfo(EquipmentServerInterface remote) throws RemoteException, SQLException {
        sendSoftCheckLogger.info("Send SoftCheckInfo");
        List<SoftCheckInfo> softCheckInfoList = remote.readSoftCheckInfo();
        if (softCheckInfoList != null && !softCheckInfoList.isEmpty()) {
            sendSoftCheckLogger.info("Sending SoftCheckInfo started");
            for (SoftCheckInfo entry : softCheckInfoList) {
                if (entry.handler != null) {
                    try {
                        Object clsHandler = getHandler(entry.handler.trim(), remote);
                        entry.sendSoftCheckInfo(clsHandler);
                        remote.finishSoftCheckInfo(entry.invoiceMap);
                    } catch (Exception e) {
                        sendSoftCheckLogger.error("Sending SoftCheckInfo error", e);
                        return;
                    }
                }
            }
            sendSoftCheckLogger.info("Sending SoftCheckInfo finished");
        }
    }

    private void sendTerminalDocumentInfo(EquipmentServerInterface remote, String sidEquipmentServer) throws SQLException, IOException {
        sendTerminalDocumentLogger.info("Send TerminalDocumentInfo");
        List<TerminalInfo> terminalInfoList = remote.readTerminalInfo(sidEquipmentServer);

        Map<String, List<TerminalInfo>> handlerModelTerminalMap = new HashMap<String, List<TerminalInfo>>();
        for (TerminalInfo terminal : terminalInfoList) {
            if (!handlerModelTerminalMap.containsKey(terminal.handlerModel))
                handlerModelTerminalMap.put(terminal.handlerModel, new ArrayList<TerminalInfo>());
            handlerModelTerminalMap.get(terminal.handlerModel).add(terminal);
        }

        for (Map.Entry<String, List<TerminalInfo>> entry : handlerModelTerminalMap.entrySet()) {
            String handlerModel = entry.getKey();
            if (handlerModel != null) {

                try {
                    TerminalHandler clsHandler = (TerminalHandler) getHandler(handlerModel, remote);

                    TerminalDocumentBatch documentBatch = clsHandler.readTerminalDocumentInfo(terminalInfoList);
                    if (documentBatch == null || documentBatch.documentDetailList == null || documentBatch.documentDetailList.isEmpty()) {
                        sendTerminalDocumentLogger.info("TerminalDocumentInfo is empty");
                    } else {
                        sendTerminalDocumentLogger.info("Sending TerminalDocumentInfo");
                        String result = remote.sendTerminalInfo(documentBatch.documentDetailList, sidEquipmentServer);
                        if (result != null) {
                            reportEquipmentServerError(remote, sidEquipmentServer, result);
                        } else {
                            sendTerminalDocumentLogger.info("Finish Reading starts");
                            clsHandler.finishReadingTerminalDocumentInfo(documentBatch);
                        }
                    }
                } catch (Throwable e) {
                    sendTerminalDocumentLogger.error("Equipment server error: ", e);
                    remote.errorEquipmentServerReport(sidEquipmentServer, e.fillInStackTrace());
                    return;
                }
            }
        }
    }

    private void processMachineryExchange(EquipmentServerInterface remote, String sidEquipmentServer) throws SQLException, IOException {
        machineryExchangeLogger.info("Process MachineryExchange");
        List<MachineryInfo> machineryInfoList = remote.readMachineryInfo(sidEquipmentServer);
        List<RequestExchange> requestExchangeList = remote.readRequestExchange(sidEquipmentServer);

        if (!requestExchangeList.isEmpty()) {

            Map<String, Map<Integer, String>> handlerModelMachineryMap = new HashMap<String, Map<Integer, String>>();
            for (MachineryInfo machinery : machineryInfoList) {
                if (!handlerModelMachineryMap.containsKey(machinery.handlerModel))
                    handlerModelMachineryMap.put(machinery.handlerModel, new HashMap<Integer, String>());
                handlerModelMachineryMap.get(machinery.handlerModel).put(machinery.numberGroup, machinery.directory);
            }

            for (Map.Entry<String, Map<Integer, String>> entry : handlerModelMachineryMap.entrySet()) {
                String handlerModel = entry.getKey();
                Map<Integer, String> machineryMap = entry.getValue();
                if (handlerModel != null) {
                    try {
                        for (RequestExchange requestExchange : requestExchangeList) {
                            try {
                                for (Map.Entry<Integer, String> machineryEntry : machineryMap.entrySet()) {
                                    Integer nppGroupMachinery = machineryEntry.getKey();
                                    String directoryGroupMachinery = machineryEntry.getValue();

                                    MachineryHandler clsHandler = (MachineryHandler) getHandler(handlerModel, remote);
                                    boolean isCashRegisterHandler = clsHandler instanceof CashRegisterHandler;
                                    boolean isTerminalHandler = clsHandler instanceof TerminalHandler;

                                    if(isCashRegisterHandler) {
                                        Set<String> directorySet = new HashSet<String>(entry.getValue().values());
                                        
                                        //DiscountCard
                                        if (requestExchange.isDiscountCard()) {
                                            List<DiscountCard> discountCardList = remote.readDiscountCardList();
                                            if (discountCardList != null && !discountCardList.isEmpty())
                                                ((CashRegisterHandler) clsHandler).sendDiscountCardList(discountCardList, requestExchange.startDate, directorySet);
                                            remote.finishRequestExchange(new HashSet<Integer>(Arrays.asList(requestExchange.requestExchange)));
                                        } 

                                        //Promotion
                                        else if (requestExchange.isPromotion()) {
                                            PromotionInfo promotionInfo = remote.readPromotionInfo();
                                            if (promotionInfo != null)
                                                ((CashRegisterHandler) clsHandler).sendPromotionInfo(promotionInfo, directorySet);
                                            remote.finishRequestExchange(new HashSet<Integer>(Arrays.asList(requestExchange.requestExchange)));
                                        }
                                    }
                                    
                                    //TerminalOrder
                                    else if (requestExchange.isTerminalOrderExchange() && isTerminalHandler) {
                                        List<TerminalOrder> terminalOrderList = remote.readTerminalOrderList(requestExchange);
                                        if (terminalOrderList != null && !terminalOrderList.isEmpty())
                                            ((TerminalHandler) clsHandler).sendTerminalOrderList(terminalOrderList, nppGroupMachinery, directoryGroupMachinery);
                                        remote.finishRequestExchange(new HashSet<Integer>(Arrays.asList(requestExchange.requestExchange)));
                                    }
                                }
                            } catch (Exception e) {
                                remote.errorEquipmentServerReport(sidEquipmentServer, e.fillInStackTrace());
                            }
                        }
                    } catch (Throwable e) {
                        machineryExchangeLogger.error("Equipment server error: ", e);
                        remote.errorEquipmentServerReport(sidEquipmentServer, e.fillInStackTrace());
                        return;
                    }
                }
            }
        }
    }

    private Object getHandler(String handlerModel, EquipmentServerInterface remote) throws ClassNotFoundException, IllegalAccessException, InstantiationException, InvocationTargetException, NoSuchMethodException {
        Object clsHandler;
        if (handlerMap.containsKey(handlerModel))
            clsHandler = handlerMap.get(handlerModel);
        else {
            if (handlerModel.split("\\$").length == 1) {
                Class cls = Class.forName(handlerModel);
                int paramCount = cls.getConstructors()[0].getParameterTypes().length;
                if (paramCount == 0) {
                    clsHandler = cls.newInstance();
                } else {
                    Constructor constructor = cls.getConstructor(FileSystemXmlApplicationContext.class);
                    clsHandler = constructor.newInstance(EquipmentServerBootstrap.getSpringContext());
                }

            } else {
                Class outerClass = Class.forName(handlerModel.split("\\$")[0]);
                Class innerClass = Class.forName(handlerModel);
                int paramCount = outerClass.getConstructors()[0].getParameterTypes().length;
                if(paramCount == 0) {
                    clsHandler = innerClass.getDeclaredConstructors()[0].newInstance(outerClass.newInstance());
                } else {
                    Constructor constructor = outerClass.getConstructor(FileSystemXmlApplicationContext.class);
                    clsHandler = innerClass.getDeclaredConstructors()[0].newInstance(constructor.newInstance(EquipmentServerBootstrap.getSpringContext()));
                }
            }
            handlerMap.put(handlerModel, clsHandler);
        }
        ((MachineryHandler) clsHandler).setRemoteObject(remote);
        return clsHandler;
    }

    private void reportEquipmentServerError(EquipmentServerInterface remote, String sidEquipmentServer, String result) throws RemoteException, SQLException {
        logger.error("Equipment server error: " + result);
        remote.errorEquipmentServerReport(sidEquipmentServer, new Throwable(result));
    }

    private static Comparator<TransactionInfo> COMPARATOR = new Comparator<TransactionInfo>() {
        public int compare(TransactionInfo o1, TransactionInfo o2) {
            return o1.dateTimeCode.compareTo(o2.dateTimeCode);
        }
    };

    public void stop() {
        if (singleTransactionExecutor != null)
            singleTransactionExecutor.shutdownNow();
        thread.interrupt();
    }

    abstract class Consumer implements Runnable {
        private SynchronousQueue<Object> queue;
        public Consumer() {
            this.queue = new SynchronousQueue<Object>();
        }

        public void scheduleIfNotScheduledYet() {
            queue.offer(Consumer.class);
        }

        @Override
        public void run() {
            while (true) {
                try {
                    queue.take();
                    runTask();
                } catch (InterruptedException e) {
                    return;
                } catch (Exception e) {
                    logger.error("Unhandled exception : ", e);
                }
            }
        }

        abstract void runTask() throws Exception;
    }

    class SingleTransactionTask implements Runnable {
        EquipmentServerInterface remote;
        String groupId;
        MachineryHandler clsHandler;
        List<TransactionInfo> transactionEntry;
        String sidEquipmentServer;

        public SingleTransactionTask(EquipmentServerInterface remote, String groupId, MachineryHandler clsHandler, List<TransactionInfo> transactionEntry, String sidEquipmentServer) {
            this.remote = remote;
            this.groupId = groupId;
            this.clsHandler = clsHandler;
            this.transactionEntry = transactionEntry;
            this.sidEquipmentServer = sidEquipmentServer;
        }

        public void run() {

            processTransactionLogger.info(String.format("Sending transaction group %s: start", groupId));
            //transactions without handler
            if (groupId != null && groupId.equals("No handler")) {
                for (TransactionInfo transactionInfo : transactionEntry) {
                    errorTransactionReport(transactionInfo.id, new Throwable(String.format("Transaction %s: No handler", transactionInfo.id)));
                }
            } else {

                // actions before sending transactions
                for (TransactionInfo transactionInfo : transactionEntry) {
                    try {
                        if (clsHandler instanceof TerminalHandler)
                            ((TerminalHandler) clsHandler).saveTransactionTerminalInfo((TransactionTerminalInfo) transactionInfo);

                        remote.processingTransaction(transactionInfo.id, new Timestamp(Calendar.getInstance().getTime().getTime()));

                    } catch (Exception e) {
                        errorTransactionReport(transactionInfo.id, e);
                    }
                }

                try {
                    Map<Integer, SendTransactionBatch> succeededMachineryInfoMap = clsHandler.sendTransaction(transactionEntry);

                    for (TransactionInfo transactionInfo : transactionEntry) {
                        boolean noErrors = true;
                        Throwable exception = succeededMachineryInfoMap.get(transactionInfo.id).exception;
                        if(exception != null) {
                            noErrors = false;
                            errorTransactionReport(transactionInfo.id, exception);
                        } else {
                            try {

                                List<MachineryInfo> succeededMachineryInfoList = succeededMachineryInfoMap.get(transactionInfo.id).succeededMachineryList;
                                if (succeededMachineryInfoList != null && succeededMachineryInfoList.size() != transactionInfo.machineryInfoList.size())
                                    noErrors = false;
                                if ((clsHandler instanceof CashRegisterHandler || clsHandler instanceof ScalesHandler) && succeededMachineryInfoList != null)
                                    remote.succeedCashRegisterTransaction(transactionInfo.id, succeededMachineryInfoList, new Timestamp(Calendar.getInstance().getTime().getTime()));
                            } catch (Exception e) {
                                noErrors = false;
                                errorTransactionReport(transactionInfo.id, e);
                            }
                        }
                        if (noErrors) {
                            succeededTransaction(transactionInfo.id);
                        }
                    }
                } catch (IOException e) {
                    errorEquipmentServerReport(e);
                }

            }
            processTransactionLogger.info(String.format("Sending transaction group %s: finish", groupId));

        }

        private void errorTransactionReport(Integer idTransactionInfo, Throwable e) {
            try {
                remote.errorTransactionReport(idTransactionInfo, e);
            } catch (Exception ignored) {
                errorEquipmentServerReport(ignored);
            }
        }

        private void succeededTransaction(Integer idTransactionInfo) {
            try {
                remote.succeedTransaction(idTransactionInfo, new Timestamp(Calendar.getInstance().getTime().getTime()));
            } catch (Exception ignored) {
            }
        }

        private void errorEquipmentServerReport(Exception e) {
            try {
                remote.errorEquipmentServerReport(sidEquipmentServer, e.fillInStackTrace());
            } catch (Exception ignored) {
            }
        }
    }
}