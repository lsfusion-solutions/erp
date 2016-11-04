package equ.clt;

import equ.api.*;
import equ.api.cashregister.*;
import equ.api.scales.ScalesHandler;
import equ.api.terminal.*;
import lsfusion.base.OrderedMap;
import lsfusion.base.Pair;
import lsfusion.interop.DaemonThreadFactory;
import lsfusion.interop.remote.RMIUtils;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.io.InvalidClassException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.rmi.registry.Registry;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;

public class EquipmentServer {

    private Thread thread;

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    private final static Logger processStopListLogger = Logger.getLogger("StopListLogger");
    private final static Logger processDeleteBarcodeLogger = Logger.getLogger("DeleteBarcodeLogger");
    protected final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");
    protected final static Logger sendSoftCheckLogger = Logger.getLogger("SoftCheckLogger");
    protected final static Logger sendTerminalDocumentLogger = Logger.getLogger("TerminalDocumentLogger");
    protected final static Logger machineryExchangeLogger = Logger.getLogger("MachineryExchangeLogger");
    protected final static Logger logger = Logger.getLogger(EquipmentServer.class);
    
    static Map<String, Object> handlerMap = new HashMap<>();
    EquipmentServerSettings equipmentServerSettings;

    TaskPool taskPool;
    Consumer processTransactionConsumer;
    Thread processTransactionThread;

    private Consumer processStopListConsumer;
    private Thread processStopListThread;

    private Consumer processDeleteBarcodeConsumer;
    private Thread processDeleteBarcodeThread;

    Consumer sendSalesConsumer;
    Thread sendSalesThread;

    Consumer sendSoftCheckConsumer;
    Thread sendSoftCheckThread;

    Consumer sendTerminalDocumentConsumer;
    Thread sendTerminalDocumentThread;

    Consumer machineryExchangeConsumer;
    Thread machineryExchangeThread;

    ExecutorService singleTransactionExecutor;
    List<Future> futures;

    boolean needReconnect = false;

    private Integer transactionThreadCount;
    private boolean mergeBatches = false;
    private boolean disableSales = false;

    public EquipmentServer(final String sidEquipmentServer, final String serverHost, final int serverPort, final String serverDB) {
        
        final int connectPort = serverPort > 0 ? serverPort : Registry.REGISTRY_PORT;

        thread = new Thread(new Runnable() {

            private EquipmentServerInterface remote = null;

            @Override
            public void run() {

                transactionThreadCount = transactionThreadCount == null ? 20 : transactionThreadCount;
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
                            } catch (NotBoundException | MalformedURLException | RemoteException e) {
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

                                    initDaemonThreads(remote, sidEquipmentServer, millis);
                                    
                                } catch (RemoteException e) {
                                    logger.error("Get remote logics error : ", e);
                                }
                            }
                        }

                        if (remote != null) {
                            processTransactionConsumer.scheduleIfNotScheduledYet();

                            if(remote.enabledStopListInfo())
                                processStopListConsumer.scheduleIfNotScheduledYet();

                            if(remote.enabledDeleteBarcodeInfo())
                                processDeleteBarcodeConsumer.scheduleIfNotScheduledYet();

                            if(!disableSales) {
                                if (sendSalesDelay == 0 || sendSalesDelayCounter >= sendSalesDelay || sendSalesDelayCounter == -1) {
                                    sendSalesConsumer.scheduleIfNotScheduledYet();
                                    sendSalesDelayCounter = 0;
                                } else {
                                    sendSalesDelayCounter++;
                                }
                            }

                            if(remote.enabledSoftCheckInfo())
                                sendSoftCheckConsumer.scheduleIfNotScheduledYet();

                            if(remote.enabledTerminalInfo())
                                sendTerminalDocumentConsumer.scheduleIfNotScheduledYet();

                            machineryExchangeConsumer.scheduleIfNotScheduledYet();

                            if(singleTransactionExecutor.isShutdown())
                                singleTransactionExecutor = Executors.newFixedThreadPool(transactionThreadCount);
                        }

                    } catch (Exception e) {
                        logger.error("Unhandled exception : ", e);
                        remote = null;
                        processTransactionThread.interrupt();
                        processTransactionThread = null;
                        processStopListThread.interrupt();
                        processStopListThread = null;
                        processDeleteBarcodeThread.interrupt();
                        processDeleteBarcodeThread = null;
                        if(sendSalesThread != null) {
                            sendSalesThread.interrupt();
                            sendSalesThread = null;
                        }
                        sendSoftCheckThread.interrupt();
                        sendSoftCheckThread = null;
                        sendTerminalDocumentThread.interrupt();
                        sendTerminalDocumentThread = null;
                        machineryExchangeThread.interrupt();
                        machineryExchangeThread = null;

                        singleTransactionExecutor.shutdown();
                        singleTransactionExecutor = null;
                        if(futures != null)
                            for(Future future : futures) {
                                logger.error("future cancel");
                                future.cancel(true);
                            }
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

    private void initDaemonThreads(final EquipmentServerInterface remote, final String sidEquipmentServer, final long millis) {

        taskPool = new TaskPool(remote, sidEquipmentServer);
        processTransactionConsumer = new Consumer() {
            @Override
            void runTask() throws Exception {
                processTransactionLogger.info("ReadTransactionInfo started");
                taskPool.addTasks(remote.readTransactionInfo(sidEquipmentServer));
                processTransactionLogger.info("ReadTransactionInfo finished");
            }
        };
        processTransactionThread = new Thread(processTransactionConsumer);
        processTransactionThread.setDaemon(true);
        processTransactionThread.start();
        singleTransactionExecutor = Executors.newFixedThreadPool(transactionThreadCount);
        futures = new ArrayList<>();
        for (int i = 0; i < transactionThreadCount; i++) {
            futures.add(singleTransactionExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    while (!Thread.currentThread().isInterrupted() && !singleTransactionExecutor.isShutdown()) {
                        try {
                            SingleTransactionTask task = taskPool.getTask();
                            if (task == null)
                                Thread.sleep(millis);
                            else {
                                logger.info("task group started: " + task.groupId);
                                task.run();
                                logger.info("task group done: " + task.groupId);
                            }
                        } catch (Exception e) {
                            logger.error("Unhandled exception : ", e);
                        }
                    }
                }
            }));
        }

        processStopListConsumer = new Consumer() {
            @Override
            void runTask() throws Exception{
                try {
                    StopListEquipmentServer.processStopListInfo(remote);
                } catch (ConnectException e) {
                    needReconnect = true;
                } catch (UnmarshalException e) {
                    if(e.getCause() instanceof InvalidClassException)
                        processStopListLogger.error("API changed! InvalidClassException");
                    throw e;
                }
            }
        };
        processStopListThread = new Thread(processStopListConsumer);
        processStopListThread.setDaemon(true);
        processStopListThread.start();

        processDeleteBarcodeConsumer = new Consumer() {
            @Override
            void runTask() throws Exception{
                try {
                    DeleteBarcodeEquipmentServer.processDeleteBarcodeInfo(remote);
                } catch (ConnectException e) {
                    needReconnect = true;
                } catch (UnmarshalException e) {
                    if(e.getCause() instanceof InvalidClassException)
                        processDeleteBarcodeLogger.error("API changed! InvalidClassException");
                    throw e;
                }
            }
        };
        processDeleteBarcodeThread = new Thread(processDeleteBarcodeConsumer);
        processDeleteBarcodeThread.setDaemon(true);
        processDeleteBarcodeThread.start();

        if(!disableSales) {
            sendSalesConsumer = new Consumer() {
                @Override
                void runTask() throws Exception {
                    try {
                        sendSalesInfo(remote, sidEquipmentServer);
                    } catch (ConnectException e) {
                        needReconnect = true;
                    } catch (UnmarshalException e) {
                        if (e.getCause() instanceof InvalidClassException)
                            sendSalesLogger.error("API changed! InvalidClassException");
                        throw e;
                    }
                }
            };
            sendSalesThread = new Thread(sendSalesConsumer);
            sendSalesThread.setDaemon(true);
            sendSalesThread.start();
        }

        sendSoftCheckConsumer = new Consumer() {
            @Override
            void runTask() throws Exception{
                try {
                    sendSoftCheckInfo(remote);
                } catch (ConnectException e) {
                    needReconnect = true;
                } catch (UnmarshalException e) {
                    if(e.getCause() instanceof InvalidClassException)
                        sendSoftCheckLogger.error("API changed! InvalidClassException");
                    throw e;
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
                } catch (UnmarshalException e) {
                    if(e.getCause() instanceof InvalidClassException)
                        sendTerminalDocumentLogger.error("API changed! InvalidClassException");
                    throw e;
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
                } catch (UnmarshalException e) {
                    if(e.getCause() instanceof InvalidClassException)
                        machineryExchangeLogger.error("API changed! InvalidClassException");
                    throw e;
                }
            }
        };
        machineryExchangeThread = new Thread(machineryExchangeConsumer);
        machineryExchangeThread.setDaemon(true);
        machineryExchangeThread.start();

    }

    private Set<String> getDirectorySet(Set<MachineryInfo> machineryInfoSet) {
        Set<String> directorySet = new HashSet<>();
        for(MachineryInfo machinery : machineryInfoSet) {
            if(machinery.directory != null)
                directorySet.add(machinery.directory);
        }
        return directorySet;
    }

    private void sendSalesInfo(EquipmentServerInterface remote, String sidEquipmentServer) throws SQLException, IOException {
        sendSalesLogger.info("Send SalesInfo");

        //временно, для динамического изменения кол-ва чеков за раз. Потом переделать эти три обращения к remote в одно
        EquipmentServerSettings settings = remote.readEquipmentServerSettings(sidEquipmentServer);
        Integer numberAtATime = settings == null ? null : settings.numberAtATime;
        //Integer numberAtATime = equipmentServerSettings == null ? null : equipmentServerSettings.numberAtATime;

        List<CashRegisterInfo> cashRegisterInfoList = remote.readCashRegisterInfo(sidEquipmentServer);

        List<RequestExchange> requestExchangeList = remote.readRequestExchange(sidEquipmentServer);
        
        Map<String, Set<String>> handlerModelDirectoryMap = new HashMap<>();
        Map<String, Set<Integer>> handlerModelCashRegisterMap = new HashMap<>();
        for (CashRegisterInfo cashRegister : cashRegisterInfoList) {
            if(!cashRegister.disableSales) {
                Set<String> directorySet = handlerModelDirectoryMap.containsKey(cashRegister.handlerModel) ? handlerModelDirectoryMap.get(cashRegister.handlerModel) : new HashSet<String>();
                directorySet.add(cashRegister.directory);
                handlerModelDirectoryMap.put(cashRegister.handlerModel, directorySet);
                Set<Integer> cashRegisterSet = handlerModelCashRegisterMap.containsKey(cashRegister.handlerModel) ? handlerModelCashRegisterMap.get(cashRegister.handlerModel) : new HashSet<Integer>();
                cashRegisterSet.add(cashRegister.number);
                handlerModelCashRegisterMap.put(cashRegister.handlerModel, cashRegisterSet);
            }
        }

        try {
            for (Map.Entry<String, Set<String>> entry : handlerModelDirectoryMap.entrySet()) {
                String handlerModel = entry.getKey();
                Set<String> directorySet = entry.getValue();
    
                if (handlerModel != null && !handlerModel.isEmpty()) {

                    MachineryHandler clsHandler = (MachineryHandler) getHandler(handlerModel, remote);

                    if(clsHandler instanceof CashRegisterHandler) {
                        CashRegisterHandler handler = (CashRegisterHandler) clsHandler;

                        sendSucceededSoftCheckInfo(remote, sidEquipmentServer, handler, directorySet);

                        requestSalesInfo(remote, requestExchangeList, handler, directorySet);

                        sendCashDocument(remote, sidEquipmentServer, handler, cashRegisterInfoList);

                        readSalesInfo(remote, sidEquipmentServer, handler, directorySet, cashRegisterInfoList, numberAtATime);

                        extraCheckZReportSum(remote, sidEquipmentServer, handler, cashRegisterInfoList);

                        checkZReportSum(remote, handler, requestExchangeList);

                    }
                }
            }
        } catch (Throwable e) {
            sendSalesLogger.error("Equipment server error: ", e);
            remote.errorEquipmentServerReport(sidEquipmentServer, e.fillInStackTrace());
        }
    }

    private void sendSucceededSoftCheckInfo(EquipmentServerInterface remote, String sidEquipmentServer, CashRegisterHandler handler, Set<String> directorySet)
            throws RemoteException, SQLException, ClassNotFoundException {
        Map succeededSoftCheckInfo = handler.requestSucceededSoftCheckInfo(directorySet);
        if (succeededSoftCheckInfo != null && !succeededSoftCheckInfo.isEmpty()) {
            sendSoftCheckLogger.info("Sending succeeded SoftCheckInfo (" + succeededSoftCheckInfo.size() + ")");
            String result = remote.sendSucceededSoftCheckInfo(succeededSoftCheckInfo);
            if (result != null)
                reportEquipmentServerError(remote, sidEquipmentServer, result);
        }
    }

    private void sendCashierTime(EquipmentServerInterface remote, String sidEquipmentServer, CashRegisterHandler handler, List<MachineryInfo> cashRegisterInfoList)
            throws IOException, SQLException, ClassNotFoundException {
        List<CashierTime> cashierTimeList = handler.requestCashierTime(cashRegisterInfoList);
        if (cashierTimeList != null && !cashierTimeList.isEmpty()) {
            sendSalesLogger.info("Sending cashier time (" + cashierTimeList.size() + ")");
            String result = remote.sendCashierTimeList(cashierTimeList);
            if (result != null)
                reportEquipmentServerError(remote, sidEquipmentServer, result);
        }
    }

    private void requestSalesInfo(EquipmentServerInterface remote, List<RequestExchange> requestExchangeList, CashRegisterHandler handler, Set<String> directorySet)
            throws IOException, ParseException, SQLException {
        if (!requestExchangeList.isEmpty()) {
            sendSalesLogger.info("Requesting SalesInfo");
            Set<Integer> succeededRequests = new HashSet<>();
            Map<Integer, String> failedRequests = new HashMap<>();
            Map<Integer, String> ignoredRequests = new HashMap<>();

            handler.requestSalesInfo(requestExchangeList, directorySet, succeededRequests, failedRequests, ignoredRequests);
            if (!succeededRequests.isEmpty())
                remote.finishRequestExchange(succeededRequests);
            if (!failedRequests.isEmpty())
                remote.errorRequestExchange(failedRequests);
            if (!ignoredRequests.isEmpty()) {
                remote.finishRequestExchange(new HashSet<>(ignoredRequests.keySet()));
                remote.errorRequestExchange(ignoredRequests);
            }
        }
    }

    private void sendCashDocument(EquipmentServerInterface remote, String sidEquipmentServer, CashRegisterHandler handler, List<CashRegisterInfo> cashRegisterInfoList)
            throws IOException, SQLException, ClassNotFoundException {
        Set<String> cashDocumentSet = remote.readCashDocumentSet(sidEquipmentServer);
        CashDocumentBatch cashDocumentBatch = handler.readCashDocumentInfo(cashRegisterInfoList, cashDocumentSet);
        if (cashDocumentBatch != null && cashDocumentBatch.cashDocumentList != null && !cashDocumentBatch.cashDocumentList.isEmpty()) {
            sendSalesLogger.info("Sending CashDocuments");
            String result = remote.sendCashDocumentInfo(cashDocumentBatch.cashDocumentList, sidEquipmentServer);
            if (result != null) {
                reportEquipmentServerError(remote, sidEquipmentServer, result);
            } else {
                handler.finishReadingCashDocumentInfo(cashDocumentBatch);
            }
        }
    }

    private void readSalesInfo(EquipmentServerInterface remote, String sidEquipmentServer, CashRegisterHandler handler,
                               Set<String> directorySet, List<CashRegisterInfo> cashRegisterInfoList, Integer numberAtATime)
            throws ParseException, IOException, ClassNotFoundException, SQLException {
        if(directorySet != null) {

            if(mergeBatches) {

                SalesBatch mergedSalesBatch = null;
                for (String directory : directorySet) {
                    SalesBatch salesBatch = handler.readSalesInfo(directory, cashRegisterInfoList);
                    if (salesBatch!= null) {
                        if(mergedSalesBatch == null)
                            mergedSalesBatch = salesBatch;
                        else
                            mergedSalesBatch.merge(salesBatch);
                    }
                }

                if(mergedSalesBatch == null || mergedSalesBatch.salesInfoList == null || mergedSalesBatch.salesInfoList.size() == 0) {
                    sendSalesLogger.info("SalesInfo is empty");
                } else {
                    sendSalesLogger.info("Sending SalesInfo : " + mergedSalesBatch.salesInfoList.size() + " records");
                    try {
                        String result = remote.sendSalesInfo(mergedSalesBatch.salesInfoList, sidEquipmentServer, numberAtATime);
                        if (result != null) {
                            reportEquipmentServerError(remote, sidEquipmentServer, result);
                        } else {
                            sendSalesLogger.info("Finish Reading starts");
                            handler.finishReadingSalesInfo(mergedSalesBatch);
                        }
                    } catch (Exception e) {
                        logger.error("Equipment server error", e);
                        remote.errorEquipmentServerReport(sidEquipmentServer, e);
                    }
                }

            } else {

                for (String directory : directorySet) {
                    SalesBatch salesBatch = handler.readSalesInfo(directory, cashRegisterInfoList);
                    if (salesBatch == null || salesBatch.salesInfoList == null || salesBatch.salesInfoList.size() == 0) {
                        sendSalesLogger.info("SalesInfo is empty");
                    } else {
                        sendSalesLogger.info("Sending SalesInfo : " + salesBatch.salesInfoList.size() + " records");
                        try {
                            String result = remote.sendSalesInfo(salesBatch.salesInfoList, sidEquipmentServer, numberAtATime);
                            if (result != null) {
                                reportEquipmentServerError(remote, sidEquipmentServer, result);
                            } else {
                                sendSalesLogger.info("Finish Reading starts");
                                handler.finishReadingSalesInfo(salesBatch);
                            }
                        } catch (Exception e) {
                            sendSalesLogger.error("Sending SalesInfo", e);
                            remote.errorEquipmentServerReport(sidEquipmentServer, e);
                        }
                    }
                }
            }
        }
    }

    private void extraCheckZReportSum(EquipmentServerInterface remote, String sidEquipmentServer, CashRegisterHandler handler, List<CashRegisterInfo> cashRegisterInfoList)
            throws RemoteException, SQLException, ClassNotFoundException {
        Map<String, List<Object>> handlerZReportSumMap = handler.readExtraCheckZReport(cashRegisterInfoList);
        if (handlerZReportSumMap != null) {
            ExtraCheckZReportBatch extraCheckResult = handler.compareExtraCheckZReport(handlerZReportSumMap, remote.readZReportSumMap());
            if (extraCheckResult.message.isEmpty()) {
                remote.succeedExtraCheckZReport(extraCheckResult.idZReportList);
            } else {
                reportEquipmentServerError(remote, sidEquipmentServer, extraCheckResult.message);
            }
        }
    }

    private void checkZReportSum(EquipmentServerInterface remote, CashRegisterHandler handler, List<RequestExchange> requestExchangeList)
            throws RemoteException, SQLException, ClassNotFoundException {
        if (!requestExchangeList.isEmpty()) {
            Set<Integer> succeededRequestsSet = new HashSet<>();
            for (RequestExchange request : requestExchangeList) {
                if (request.isCheckZReportExchange()) {
                    sendSalesLogger.info("Executing checkZReportSum");

                    Set<String> stockSet = new HashSet<>();
                    for(Set<String> entry : request.directoryStockMap.values())
                        stockSet.addAll(entry);

                    for (String idStock : stockSet) {
                        Map<String, List<Object>> zReportSumMap = remote.readRequestZReportSumMap(idStock, request.dateFrom, request.dateTo);
                        Map<Integer, List<List<Object>>> cashRegisterMap = remote.readCashRegistersStock(idStock);
                        for(Map.Entry<Integer, List<List<Object>>> cashRegisterEntry : cashRegisterMap.entrySet()) {
                            List<List<Object>> checkSumResult = zReportSumMap.isEmpty() ? null :
                                    handler.checkZReportSum(zReportSumMap, cashRegisterEntry.getValue());
                            if (checkSumResult != null) {
                                remote.logRequestZReportSumCheck(request.requestExchange, cashRegisterEntry.getKey(), checkSumResult);
                            }
                        }
                    }
                    succeededRequestsSet.add(request.requestExchange);
                }
            }
            if (!succeededRequestsSet.isEmpty()) {
                remote.finishRequestExchange(succeededRequestsSet);
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

        Map<String, List<TerminalInfo>> handlerModelTerminalMap = new HashMap<>();
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

            Map<String, Set<MachineryInfo>> handlerModelMachineryMap = new HashMap<>();
            for (MachineryInfo machinery : machineryInfoList) {
                if (!handlerModelMachineryMap.containsKey(machinery.handlerModel))
                    handlerModelMachineryMap.put(machinery.handlerModel, new HashSet<MachineryInfo>());
                handlerModelMachineryMap.get(machinery.handlerModel).add(machinery);
            }

            for (Map.Entry<String, Set<MachineryInfo>> entry : handlerModelMachineryMap.entrySet()) {
                String handlerModel = entry.getKey();
                Set<MachineryInfo> machineryMap = entry.getValue();
                if (handlerModel != null) {
                    try {

                        MachineryHandler clsHandler = (MachineryHandler) getHandler(handlerModel, remote);
                        boolean isCashRegisterHandler = clsHandler instanceof CashRegisterHandler;
                        boolean isTerminalHandler = clsHandler instanceof TerminalHandler;

                        for (RequestExchange requestExchange : requestExchangeList) {
                            try {

                                if(isCashRegisterHandler) {

                                    //Cashier
                                    if (requestExchange.isCashier()) {
                                        List<CashierInfo> cashierInfoList = remote.readCashierInfoList();
                                        if (cashierInfoList != null && !cashierInfoList.isEmpty()) {
                                            ((CashRegisterHandler) clsHandler).sendCashierInfoList(cashierInfoList, requestExchange.directoryStockMap);
                                        }
                                        sendCashierTime(remote, sidEquipmentServer, (CashRegisterHandler) clsHandler, machineryInfoList);
                                        remote.finishRequestExchange(new HashSet<>(Collections.singletonList(requestExchange.requestExchange)));
                                    }

                                    //DiscountCard
                                    else if (requestExchange.isDiscountCard()) {
                                        List<DiscountCard> discountCardList = remote.readDiscountCardList(requestExchange.idDiscountCardFrom, requestExchange.idDiscountCardTo);
                                        if (discountCardList != null && !discountCardList.isEmpty())
                                            ((CashRegisterHandler) clsHandler).sendDiscountCardList(discountCardList, requestExchange);
                                        remote.finishRequestExchange(new HashSet<>(Collections.singletonList(requestExchange.requestExchange)));
                                    }

                                    //Promotion
                                    else if (requestExchange.isPromotion()) {
                                        PromotionInfo promotionInfo = remote.readPromotionInfo();
                                        if (promotionInfo != null)
                                            ((CashRegisterHandler) clsHandler).sendPromotionInfo(promotionInfo, requestExchange);
                                        remote.finishRequestExchange(new HashSet<>(Collections.singletonList(requestExchange.requestExchange)));
                                    }
                                }

                                //TerminalOrder
                                else if (requestExchange.isTerminalOrderExchange() && isTerminalHandler) {

                                    for (MachineryInfo machinery : machineryMap) {
                                        List<TerminalOrder> terminalOrderList = remote.readTerminalOrderList(requestExchange);
                                        if (terminalOrderList != null && !terminalOrderList.isEmpty())
                                            ((TerminalHandler) clsHandler).sendTerminalOrderList(terminalOrderList, machinery);
                                        remote.finishRequestExchange(new HashSet<>(Collections.singletonList(requestExchange.requestExchange)));
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

    static Object getHandler(String handlerModel, EquipmentServerInterface remote) throws ClassNotFoundException, IllegalAccessException, InstantiationException, InvocationTargetException, NoSuchMethodException {
        if(handlerModel == null) return null;
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
        remote.errorEquipmentServerReport(sidEquipmentServer, new Throwable(result).fillInStackTrace());
    }

    private static Comparator<TransactionInfo> COMPARATOR = new Comparator<TransactionInfo>() {
        public int compare(TransactionInfo o1, TransactionInfo o2) {
            return o1.dateTimeCode.compareTo(o2.dateTimeCode);
        }
    };

    public void stop() {
        if(processTransactionThread != null)
            processTransactionThread.interrupt();
        if(processStopListThread != null)
            processStopListThread.interrupt();
        if(processDeleteBarcodeThread != null)
            processDeleteBarcodeThread.interrupt();
        if(sendSalesThread != null)
            sendSalesThread.interrupt();
        if(sendSoftCheckThread != null)
            sendSoftCheckThread.interrupt();
        if(sendTerminalDocumentThread != null)
            sendTerminalDocumentThread.interrupt();
        if(machineryExchangeThread != null)
            machineryExchangeThread.interrupt();
        if (singleTransactionExecutor != null)
            singleTransactionExecutor.shutdown();
        if(futures != null)
            for(Future future : futures) {
                future.cancel(true);
            }
        thread.interrupt();
    }

    public void setMergeBatches(boolean mergeBatches) {
        this.mergeBatches = mergeBatches;
    }

    public void setDisableSales(boolean disableSales) {
        this.disableSales = disableSales;
    }

    public Integer getTransactionThreadCount() {
        return transactionThreadCount;
    }

    public void setTransactionThreadCount(Integer transactionThreadCount) {
        this.transactionThreadCount = transactionThreadCount;
    }

    abstract class Consumer implements Runnable {
        private SynchronousQueue<Object> queue;
        public Consumer() {
            this.queue = new SynchronousQueue<>();
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


    public class TaskPool {

        EquipmentServerInterface remote;
        String sidEquipmentServer;

        //groupId заданий, находящихся в обработке
        Set<String> currentlyProceededGroups;
        //задания в очереди
        Map<Integer, TransactionInfo> waitingTaskQueueMap;
        //выполняющиеся задания
        List<Integer> proceededTaskList;
        //выполненные задания
        List<Integer> succeededTaskList;

        public TaskPool(EquipmentServerInterface remote, String sidEquipmentServer) {
            this.remote = remote;
            this.sidEquipmentServer = sidEquipmentServer;
            currentlyProceededGroups = new HashSet<>();
            waitingTaskQueueMap = new OrderedMap<>();
            proceededTaskList = new ArrayList<>();
            succeededTaskList = new ArrayList<>();
        }

        //метод, выдающий задания подпотокам
        synchronized SingleTransactionTask getTask() {
            SingleTransactionTask resultTask = null;

            //находим transactionInfo без ошибок либо с самой старой последней ошибкой в очереди
            String minGroupId = null;
            Timestamp minLastErrorDate = null;
            MachineryHandler minClsHandler = null;
            Set<String> checkedGroupIdSet = new HashSet<>();
            for(TransactionInfo transactionInfo : waitingTaskQueueMap.values()) {
                try {
                    MachineryHandler clsHandler = (MachineryHandler) getHandler(transactionInfo.handlerModel, remote);
                    String groupId = clsHandler == null ? "No handler" : clsHandler.getGroupId(transactionInfo);
                    if(!checkedGroupIdSet.contains(groupId) && !currentlyProceededGroups.contains(groupId)) {
                        checkedGroupIdSet.add(groupId);
                        if(transactionInfo.lastErrorDate == null || minLastErrorDate == null || minLastErrorDate.compareTo(transactionInfo.lastErrorDate) > 0) {
                            minGroupId = groupId;
                            minLastErrorDate = transactionInfo.lastErrorDate;
                            minClsHandler = clsHandler;
                            if(minLastErrorDate == null)
                                break;
                        }
                    }
                } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException | ClassNotFoundException | IOException e) {
                    logger.error("EquipmentServer Error: ", e);
                }
            }
            //находим все transactionInfo с таким же groupId
            if(minGroupId != null) {
                currentlyProceededGroups.add(minGroupId);
                resultTask = new SingleTransactionTask(remote, minGroupId, minClsHandler, new ArrayList<TransactionInfo>(), sidEquipmentServer);
                Set<Integer> removingTaskSet = new HashSet<>();
                for (Map.Entry<Integer, TransactionInfo> transactionInfo : waitingTaskQueueMap.entrySet()) {
                    if(resultTask.groupId.equals(getTransactionInfoGroupId(transactionInfo.getValue()))) {
                        processTransactionLogger.info(String.format("Task Pool : starting transaction %s", transactionInfo.getValue().id));
                        resultTask.transactionEntry.add(transactionInfo.getValue());
                        proceededTaskList.add(transactionInfo.getKey());
                        removingTaskSet.add(transactionInfo.getKey());
                    }
                }
                Collections.sort(resultTask.transactionEntry, COMPARATOR);
                for(Integer task : removingTaskSet)
                    waitingTaskQueueMap.remove(task);
            }
            return resultTask;
        }

        //метод, считывающий задания из базы
        synchronized void addTasks(List<TransactionInfo> transactionInfoList) throws Exception {
            Map<Integer, TransactionInfo> newWaitingTaskQueueMap = new OrderedMap<>();
            for(TransactionInfo transaction : transactionInfoList) {
                if(!succeededTaskList.contains(transaction.id) && !proceededTaskList.contains(transaction.id)) {
                    if(!waitingTaskQueueMap.containsKey(transaction.id))
                        processTransactionLogger.info(String.format("Task Pool : adding transaction %s to queue", transaction.id));
                    newWaitingTaskQueueMap.put(transaction.id, transaction);
                }
            }
            waitingTaskQueueMap = newWaitingTaskQueueMap;
            succeededTaskList.clear();
        }

        //метод, помечающий задание как выполненное
        synchronized void markProceeded(String groupId, Map<TransactionInfo, Boolean> transactionInfoMap) {
            for (Map.Entry<TransactionInfo, Boolean> transactionEntry : transactionInfoMap.entrySet()) {
                processTransactionLogger.info(String.format("Task Pool : marking transaction %s as succeeded", transactionEntry.getKey().id));
                if(transactionEntry.getValue())
                    succeededTaskList.add(transactionEntry.getKey().id);
                else {
                    for(Iterator<Map.Entry<Integer, TransactionInfo>> it = waitingTaskQueueMap.entrySet().iterator(); it.hasNext(); ) {
                        Map.Entry<Integer, TransactionInfo> entry = it.next();
                        if(groupId != null && groupId.equals(getTransactionInfoGroupId(entry.getValue()))) {
                            it.remove();
                        }
                    }
                }
                proceededTaskList.remove(transactionEntry.getKey().id);
            }
            currentlyProceededGroups.remove(groupId);
        }

        private String getTransactionInfoGroupId(TransactionInfo transactionInfo) {
            try {
                MachineryHandler clsHandler = (MachineryHandler) getHandler(transactionInfo.handlerModel, remote);
                return clsHandler == null ? "No handler" : clsHandler.getGroupId(transactionInfo);

            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException | ClassNotFoundException | IOException e) {
                logger.error("EquipmentServer Error: ", e);
                return "No handler";
            }
        }

    }

    /*public Integer getUniqueId(TransactionInfo transactionInfo) {
        String result = String.valueOf(transactionInfo.id) + transactionInfo.idGroupMachinery;
        for(Object machineryInfo : transactionInfo.machineryInfoList)
            result += ((MachineryInfo)machineryInfo).number;
        for(Object item : transactionInfo.itemsList)
            result += ((ItemInfo) item).idBarcode;
        return result.hashCode();
    }*/

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

            processTransactionLogger.info(String.format("   Sending transaction group %s: start, count : %s", groupId, transactionEntry.size()));
            Map<TransactionInfo, Boolean> transactionInfoMap = new HashMap<>();
            //transactions without handler
            if (groupId != null && groupId.equals("No handler")) {
                for (TransactionInfo transactionInfo : transactionEntry) {
                    transactionInfoMap.put(transactionInfo, false);
                    errorTransactionReport(transactionInfo.id, new Throwable(String.format("Transaction %s: No handler", transactionInfo.id)));
                }
            } else {

                // actions before sending transactions
                for (TransactionInfo transactionInfo : transactionEntry) {
                    try {
                        transactionInfoMap.put(transactionInfo, false);

                        remote.processingTransaction(transactionInfo.id, new Timestamp(Calendar.getInstance().getTime().getTime()));

                        if (clsHandler instanceof TerminalHandler)
                            ((TerminalHandler) clsHandler).saveTransactionTerminalInfo((TransactionTerminalInfo) transactionInfo);

                    } catch (Exception e) {
                        errorTransactionReport(transactionInfo.id, e);
                    }
                }

                try {
                    Map<Integer, SendTransactionBatch> succeededMachineryInfoMap = clsHandler.sendTransaction(transactionEntry);
                    
                    processTransactionLogger.info(String.format("   Sending transaction group %s: confirm to server, count : %s ", groupId, succeededMachineryInfoMap.size()));

                    for (TransactionInfo transactionInfo : transactionEntry) {
                        boolean noErrors = true;
                        SendTransactionBatch batch = succeededMachineryInfoMap.get(transactionInfo.id);
                        if(batch != null && batch.exception != null) {
                            noErrors = false;
                            errorTransactionReport(transactionInfo.id, batch.exception);
                        }

                        try {
                            if (batch != null) {
                                List<MachineryInfo> succeededMachineryInfoList = batch.succeededMachineryList;
                                if (succeededMachineryInfoList != null && getEnabledMachineryInfoList(succeededMachineryInfoList).size() != getEnabledMachineryInfoList(transactionInfo.machineryInfoList).size())
                                    noErrors = false;
                                if ((clsHandler instanceof CashRegisterHandler || clsHandler instanceof ScalesHandler) && succeededMachineryInfoList != null)
                                    remote.succeedMachineryTransaction(transactionInfo.id, succeededMachineryInfoList, new Timestamp(Calendar.getInstance().getTime().getTime()));
                                if(batch.clearedMachineryList != null && !batch.clearedMachineryList.isEmpty())
                                    remote.clearedMachineryTransaction(transactionInfo.id, batch.clearedMachineryList);
                            }
                        } catch (Exception e) {
                            noErrors = false;
                            errorTransactionReport(transactionInfo.id, e);
                        }

                        if (noErrors) {
                            succeededTransaction(transactionInfo.id);
                            transactionInfoMap.put(transactionInfo, true);
                        }
                    }
                } catch (Exception e) {
                    processTransactionLogger.error("EquipmentServerError: ", e);
                    errorEquipmentServerReport(e);
                }

            }
            taskPool.markProceeded(groupId, transactionInfoMap);
            processTransactionLogger.info(String.format("   Sending transaction group %s: finish", groupId));

        }

        public List<MachineryInfo> getEnabledMachineryInfoList (List<MachineryInfo> machineryInfoList) {
            List<MachineryInfo> enabledMachineryInfoList = new ArrayList<>();
            for(MachineryInfo machinery : machineryInfoList) {
                if(machinery.enabled)
                    enabledMachineryInfoList.add(machinery);
            }
            return enabledMachineryInfoList.isEmpty() ? machineryInfoList : enabledMachineryInfoList;
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

    public static ExecutorService getFixedThreadPool(int nThreads, String name) {
        return Executors.newFixedThreadPool(nThreads, new DaemonThreadFactory(name));
    }
}