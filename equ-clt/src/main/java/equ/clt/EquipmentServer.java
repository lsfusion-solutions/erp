package equ.clt;

import equ.api.*;
import equ.api.cashregister.*;
import equ.api.scales.ScalesHandler;
import equ.api.terminal.*;
import lsfusion.base.OrderedMap;
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

    private Consumer sendSalesConsumer;
    private Thread sendSalesThread;

    private Consumer sendSoftCheckConsumer;
    private Thread sendSoftCheckThread;

    private Consumer sendTerminalDocumentConsumer;
    private Thread sendTerminalDocumentThread;

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
                                        if(equipmentServerSettings.timeFrom != null && equipmentServerSettings.timeTo != null)
                                            logger.info(String.format("EquipmentServer %s time to run: from %s to %s",
                                                    sidEquipmentServer, equipmentServerSettings.timeFrom, equipmentServerSettings.timeTo));
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
                            sendSalesLogger.error("Extra Log: ", e);
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
                if(isTimeToRun()) {
                    processTransactionLogger.info("ReadTransactionInfo started");
                    taskPool.addTasks(remote.readTransactionInfo(sidEquipmentServer));
                    processTransactionLogger.info("ReadTransactionInfo finished");
                }
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
                    if(isTimeToRun())
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
                    if(isTimeToRun())
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
            sendSalesLogger.info("Extra Log: Starting SendSalesThread");
            sendSalesConsumer = new Consumer() {
                @Override
                void runTask() throws Exception {
                    try {
                        if(isTimeToRun())
                            sendSalesInfo(remote, sidEquipmentServer);
                        else
                            sendSalesLogger.info("Extra Log: not time to run");
                    } catch (ConnectException e) {
                        sendSalesLogger.error("Extra Log: ", e);
                        needReconnect = true;
                    } catch (UnmarshalException e) {
                        if (e.getCause() instanceof InvalidClassException)
                            sendSalesLogger.error("API changed! InvalidClassException");
                        sendSalesLogger.error("Extra Log: ", e);
                        throw e;
                    }
                }
            };
            sendSalesThread = new Thread(sendSalesConsumer);
            sendSalesThread.setDaemon(true);
            sendSalesThread.start();
            sendSalesLogger.info("Extra Log: Started SendSalesThread");
        }

        sendSoftCheckConsumer = new Consumer() {
            @Override
            void runTask() throws Exception{
                try {
                    if(isTimeToRun())
                        SoftCheckEquipmentServer.sendSoftCheckInfo(remote);
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
                    if(isTimeToRun())
                        TerminalDocumentEquipmentServer.sendTerminalDocumentInfo(remote, sidEquipmentServer);
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
                    if(isTimeToRun())
                        MachineryExchangeEquipmentServer.processMachineryExchange(remote, sidEquipmentServer);
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

    private boolean isTimeToRun() {
        boolean start = true;
        if (equipmentServerSettings.timeFrom != null && equipmentServerSettings.timeTo != null) {
            Calendar currentCal = Calendar.getInstance();

            Calendar calendarFrom = Calendar.getInstance();
            calendarFrom.setTime(equipmentServerSettings.timeFrom);
            calendarFrom.set(Calendar.DAY_OF_MONTH, currentCal.get(Calendar.DAY_OF_MONTH));
            calendarFrom.set(Calendar.MONTH, currentCal.get(Calendar.MONTH));
            calendarFrom.set(Calendar.YEAR, currentCal.get(Calendar.YEAR));

            Calendar calendarTo = Calendar.getInstance();
            calendarTo.setTime(equipmentServerSettings.timeTo);
            calendarTo.set(Calendar.DAY_OF_MONTH, currentCal.get(Calendar.DAY_OF_MONTH));
            calendarTo.set(Calendar.MONTH, currentCal.get(Calendar.MONTH));
            calendarTo.set(Calendar.YEAR, currentCal.get(Calendar.YEAR));
            if (equipmentServerSettings.timeFrom.compareTo(equipmentServerSettings.timeTo) > 0)
                calendarTo.add(Calendar.DAY_OF_MONTH, 1);

            start = currentCal.getTimeInMillis() >= calendarFrom.getTimeInMillis() && currentCal.getTimeInMillis() <= calendarTo.getTimeInMillis();
        }
        return start;
    }

    private void sendSalesInfo(EquipmentServerInterface remote, String sidEquipmentServer) throws SQLException, IOException {
        sendSalesLogger.info("Send SalesInfo");

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

                        SoftCheckEquipmentServer.sendSucceededSoftCheckInfo(remote, sidEquipmentServer, handler, directorySet);

                        SendSalesEquipmentServer.requestSalesInfo(remote, requestExchangeList, handler, directorySet);

                        SendSalesEquipmentServer.sendCashDocument(remote, sidEquipmentServer, handler, cashRegisterInfoList);

                        readSalesInfo(remote, sidEquipmentServer, handler, directorySet, cashRegisterInfoList);

                        SendSalesEquipmentServer.extraCheckZReportSum(remote, sidEquipmentServer, handler, cashRegisterInfoList);

                        SendSalesEquipmentServer.checkZReportSum(remote, handler, requestExchangeList);

                    }
                }
            }
        } catch (Throwable e) {
            sendSalesLogger.error("Equipment server error: ", e);
            remote.errorEquipmentServerReport(sidEquipmentServer, e);
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

    private void readSalesInfo(EquipmentServerInterface remote, String sidEquipmentServer, CashRegisterHandler handler,
                               Set<String> directorySet, List<CashRegisterInfo> cashRegisterInfoList)
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
                        String result = remote.sendSalesInfo(mergedSalesBatch.salesInfoList, sidEquipmentServer);
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
                            String result = remote.sendSalesInfo(salesBatch.salesInfoList, sidEquipmentServer);
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

    static void reportEquipmentServerError(EquipmentServerInterface remote, String sidEquipmentServer, String result) throws RemoteException, SQLException {
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
        if(sendSalesThread != null) {
            sendSalesLogger.info("Extra Log: Stop called");
            sendSalesThread.interrupt();
        }
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
                    logger.error("Extra Log: ", e);
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
                remote.errorEquipmentServerReport(sidEquipmentServer, e);
            } catch (Exception ignored) {
            }
        }
    }

    public static ExecutorService getFixedThreadPool(int nThreads, String name) {
        return Executors.newFixedThreadPool(nThreads, new DaemonThreadFactory(name));
    }
}