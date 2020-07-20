package equ.clt;

import equ.api.*;
import equ.api.cashregister.CashRegisterHandler;
import equ.api.scales.ScalesHandler;
import equ.api.terminal.TerminalHandler;
import equ.api.terminal.TransactionTerminalInfo;
import lsfusion.base.DaemonThreadFactory;
import lsfusion.base.col.heavy.OrderedMap;
import lsfusion.base.remote.RMIUtils;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.InvalidClassException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.rmi.registry.Registry;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;

public class EquipmentServer {

    private Thread thread;

    private final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    private final static Logger processStopListLogger = Logger.getLogger("StopListLogger");
    private final static Logger processDeleteBarcodeLogger = Logger.getLogger("DeleteBarcodeLogger");
    private final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");
    private final static Logger sendTerminalDocumentLogger = Logger.getLogger("TerminalDocumentLogger");
    private final static Logger machineryExchangeLogger = Logger.getLogger("MachineryExchangeLogger");
    private final static Logger processMonitorLogger = Logger.getLogger("ProcessMonitorLogger");
    private final static Logger equipmentLogger = Logger.getLogger("EquipmentLogger");
    
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

    private Consumer sendTerminalDocumentConsumer;
    private Thread sendTerminalDocumentThread;

    private Consumer machineryExchangeConsumer;
    private Thread machineryExchangeThread;

    private Consumer processMonitorConsumer;
    private Thread processMonitorThread;

    ExecutorService singleTransactionExecutor;
    List<Future> futures;

    boolean needReconnect = false;

    private Integer transactionThreadCount;
    private boolean mergeBatches = false;
    private boolean disableSales = false;
    private Integer loginTimeout;
    private Integer waitForThreadDeath;

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
                            } catch (NotBoundException e) {
                                equipmentLogger.error("Naming lookup error, possible Equipment Server is disabled, check serverComputer()", e);
                            } catch (RemoteException e) {
                                equipmentLogger.error("Naming lookup error : ", e);
                            }

                            if (remote != null) {
                                try {

                                    int serverVersion = remote.getEquApiVersion();
                                    int clientVersion = EquipmentUtils.getEquApiVersion();
                                    if(serverVersion != clientVersion) {
                                        equipmentLogger.error(String.format("Versions of the server (%s) and the equ (%s) do not match. Need to update %s. Equ will be stopped.",
                                                serverVersion, clientVersion, serverVersion < clientVersion ? "server" : "equ"));
                                        break;
                                    }

                                    equipmentServerSettings = remote.readEquipmentServerSettings(sidEquipmentServer);
                                    if (equipmentServerSettings == null) {
                                        equipmentLogger.error("Equipment Server " + sidEquipmentServer + " not found");
                                    } else {
                                        if (equipmentServerSettings.delay != null)
                                            millis = equipmentServerSettings.delay;
                                        if(equipmentServerSettings.sendSalesDelay != null)
                                            sendSalesDelay = equipmentServerSettings.sendSalesDelay;
                                        if(equipmentServerSettings.timeFrom != null && equipmentServerSettings.timeTo != null)
                                            equipmentLogger.info(String.format("EquipmentServer %s time to run: from %s to %s",
                                                    sidEquipmentServer, equipmentServerSettings.timeFrom, equipmentServerSettings.timeTo));
                                    }

                                    initDaemonThreads(remote, sidEquipmentServer, millis);
                                    
                                } catch (RemoteException e) {
                                    equipmentLogger.error("Get remote logics error : ", e);
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

                            if(remote.enabledTerminalInfo())
                                sendTerminalDocumentConsumer.scheduleIfNotScheduledYet();

                            machineryExchangeConsumer.scheduleIfNotScheduledYet();

                            processMonitorConsumer.scheduleIfNotScheduledYet();

                            if(singleTransactionExecutor.isShutdown())
                                singleTransactionExecutor = Executors.newFixedThreadPool(transactionThreadCount);
                        }

                    } catch (Exception e) {
                        equipmentLogger.error("Unhandled exception in main cycle: ", e);
                        remote = null;
                        interruptThread(processTransactionThread);
                        processTransactionThread = null;
                        interruptThread(processStopListThread);
                        processStopListThread = null;
                        interruptThread(processDeleteBarcodeThread);
                        processDeleteBarcodeThread = null;
                        interruptThread(sendSalesThread);
                        sendSalesThread = null;
                        interruptThread(sendTerminalDocumentThread);
                        sendTerminalDocumentThread = null;
                        interruptThread(machineryExchangeThread);
                        machineryExchangeThread = null;
                        interruptThread(processMonitorThread);
                        processMonitorThread = null;

                        singleTransactionExecutor.shutdown();
                        singleTransactionExecutor = null;
                        if(futures != null)
                            for(Future future : futures) {
                                equipmentLogger.error("future cancel");
                                future.cancel(true);
                            }
                    }

                    try {
                        Thread.sleep(millis);
                    } catch (InterruptedException e) {
                        equipmentLogger.error("Thread has been interrupted : ", e);
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
            futures.add(singleTransactionExecutor.submit(() -> {
                while (!Thread.currentThread().isInterrupted() && !singleTransactionExecutor.isShutdown()) {
                    try {
                        SingleTransactionTask task = taskPool.getTask();
                        if (task == null)
                            Thread.sleep(millis);
                        else {
                            equipmentLogger.info("task group started: " + task.groupId);
                            task.run();
                            equipmentLogger.info("task group done: " + task.groupId);
                        }
                    } catch (Exception e) {
                        equipmentLogger.error("Unhandled exception in singleTransactionExecutor: ", e);
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
                    processStopListLogger.error("Connect Exception: ", e);
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
                    processDeleteBarcodeLogger.error("Connect Exception: ", e);
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
                        //если проблема в этом, то вставить такие проверки для всех consumer'ов
                        if(sendSalesThread.isInterrupted()) {
                            sendSalesLogger.info("Interrupted SendSalesThread is still alive!");
                        } else if(isTimeToRun())
                            SendSalesEquipmentServer.sendSalesInfo(remote, sidEquipmentServer, mergeBatches);
                    } catch (ConnectException e) {
                        sendSalesLogger.error("Connect Exception: ", e);
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

        sendTerminalDocumentConsumer = new Consumer() {
            @Override
            void runTask() throws Exception{
                try {
                    if(isTimeToRun())
                        TerminalDocumentEquipmentServer.sendTerminalDocumentInfo(remote, sidEquipmentServer);
                } catch (ConnectException e) {
                    sendTerminalDocumentLogger.error("Connect Exception: ", e);
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
                    machineryExchangeLogger.error("Connect Exception: ", e);
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

        processMonitorConsumer = new Consumer() {
            @Override
            void runTask() throws Exception{
                try {
                    if(isTimeToRun())
                        ProcessMonitorEquipmentServer.process(EquipmentServer.this, remote, sidEquipmentServer);
                } catch (ConnectException e) {
                    processMonitorLogger.error("Connect Exception: ", e);
                    needReconnect = true;
                } catch (UnmarshalException e) {
                    if(e.getCause() instanceof InvalidClassException)
                        processMonitorLogger.error("API changed! InvalidClassException");
                    throw e;
                }
            }
        };
        processMonitorThread = new Thread(processMonitorConsumer);
        processMonitorThread.setDaemon(true);
        processMonitorThread.start();

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
        reportEquipmentServerError(remote, sidEquipmentServer, result, null);
    }

    static void reportEquipmentServerError(EquipmentServerInterface remote, String sidEquipmentServer, String result, String extraData) throws RemoteException, SQLException {
        equipmentLogger.error("Equipment server error: " + result);
        remote.errorEquipmentServerReport(sidEquipmentServer, new Throwable(result).fillInStackTrace(), extraData);
    }


    static void reportEquipmentServerError(EquipmentServerInterface remote, String sidEquipmentServer, Throwable throwable) throws RemoteException, SQLException {
        reportEquipmentServerError(remote, sidEquipmentServer, throwable, null);
    }

    static void reportEquipmentServerError(EquipmentServerInterface remote, String sidEquipmentServer, Throwable throwable, String extraData) throws RemoteException, SQLException {
        remote.errorEquipmentServerReport(sidEquipmentServer, throwable, extraData);
    }


    private static Comparator<TransactionInfo> COMPARATOR = Comparator.comparing(o -> o.dateTimeCode);

    public void stop() {
        interruptThread(processTransactionThread);
        interruptThread(processStopListThread);
        interruptThread(processDeleteBarcodeThread);
        interruptThread(sendSalesThread);
        interruptThread(sendTerminalDocumentThread);
        interruptThread(machineryExchangeThread);
        interruptThread(processMonitorThread);
        if (singleTransactionExecutor != null)
            singleTransactionExecutor.shutdown();
        if(futures != null)
            for(Future future : futures) {
                future.cancel(true);
            }
        interruptThread(thread);
    }

    public void setMergeBatches(boolean mergeBatches) {
        this.mergeBatches = mergeBatches;
    }

    public void setDisableSales(boolean disableSales) {
        this.disableSales = disableSales;
    }

    public void setLoginTimeout(Integer loginTimeout) {
        this.loginTimeout = loginTimeout;
        if(loginTimeout != null)
            DriverManager.setLoginTimeout(loginTimeout);
    }

    public Integer getWaitForThreadDeath() {
        return waitForThreadDeath;
    }

    public void setWaitForThreadDeath(Integer waitForThreadDeath) {
        this.waitForThreadDeath = waitForThreadDeath;
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
                    e.printStackTrace();
                    equipmentLogger.error("Unhandled exception in consumer: ", e);
                }
            }
        }

        abstract void runTask() throws Exception;
    }

    public static ExecutorService getFixedThreadPool(int nThreads, String name) {
        return Executors.newFixedThreadPool(nThreads, new DaemonThreadFactory(name));
    }

    private void interruptThread(Thread thread) {
        try {
            if (thread != null) {
                thread.interrupt();
                thread.join(waitForThreadDeath != null ? waitForThreadDeath * 1000 : 300000); //5 minutes
            }
        } catch (InterruptedException e) {
            equipmentLogger.error("Thread has been interrupted while join: ", e);
        }
    }

    private boolean isTimeToRun() {
        boolean start = true;
        if(equipmentServerSettings == null) {
            start = false;
        } else if (equipmentServerSettings.timeFrom != null && equipmentServerSettings.timeTo != null) {
            Calendar currentCal = Calendar.getInstance();

            Calendar calendarFrom = Calendar.getInstance();
            calendarFrom.setTime(localTimeToSqlTime(equipmentServerSettings.timeFrom));
            calendarFrom.set(Calendar.DAY_OF_MONTH, currentCal.get(Calendar.DAY_OF_MONTH));
            calendarFrom.set(Calendar.MONTH, currentCal.get(Calendar.MONTH));
            calendarFrom.set(Calendar.YEAR, currentCal.get(Calendar.YEAR));

            Calendar calendarTo = Calendar.getInstance();
            calendarTo.setTime(localTimeToSqlTime(equipmentServerSettings.timeTo));
            calendarTo.set(Calendar.DAY_OF_MONTH, currentCal.get(Calendar.DAY_OF_MONTH));
            calendarTo.set(Calendar.MONTH, currentCal.get(Calendar.MONTH));
            calendarTo.set(Calendar.YEAR, currentCal.get(Calendar.YEAR));
            if (equipmentServerSettings.timeFrom.compareTo(equipmentServerSettings.timeTo) > 0) {
                if(currentCal.compareTo(calendarFrom) < 0)
                    calendarFrom.add(Calendar.DAY_OF_MONTH, -1);
                else
                    calendarTo.add(Calendar.DAY_OF_MONTH, 1);
            }

            start = currentCal.getTimeInMillis() >= calendarFrom.getTimeInMillis() && currentCal.getTimeInMillis() <= calendarTo.getTimeInMillis();
        }
        return start;
    }

    public class TaskPool {

        EquipmentServerInterface remote;
        String sidEquipmentServer;

        //groupId заданий, находящихся в обработке
        Set<String> currentlyProceededGroups;
        //задания в очереди
        Map<Long, TransactionInfo> waitingTaskQueueMap;
        //выполняющиеся задания
        List<Long> proceededTaskList;
        //выполненные задания
        List<Long> succeededTaskList;

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
                        Timestamp lastErrorDate = localDateTimeToSqlTimestamp(transactionInfo.lastErrorDate);
                        if(lastErrorDate == null || minLastErrorDate == null || minLastErrorDate.compareTo(lastErrorDate) > 0) {
                            minGroupId = groupId;
                            minLastErrorDate = lastErrorDate;
                            minClsHandler = clsHandler;
                            if(minLastErrorDate == null)
                                break;
                        }
                    }
                } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
                    equipmentLogger.error("EquipmentServer Error: ", e);
                }
            }
            //находим все transactionInfo с таким же groupId
            if(minGroupId != null) {
                currentlyProceededGroups.add(minGroupId);
                resultTask = new SingleTransactionTask(remote, minGroupId, minClsHandler, new ArrayList<>(), sidEquipmentServer);
                Set<Long> removingTaskSet = new HashSet<>();
                for (Map.Entry<Long, TransactionInfo> transactionInfo : waitingTaskQueueMap.entrySet()) {
                    if(resultTask.groupId.equals(getTransactionInfoGroupId(transactionInfo.getValue()))) {
                        processTransactionLogger.info(String.format("Task Pool : starting transaction %s", transactionInfo.getValue().id));
                        resultTask.transactionEntry.add(transactionInfo.getValue());
                        proceededTaskList.add(transactionInfo.getKey());
                        removingTaskSet.add(transactionInfo.getKey());
                    }
                }
                resultTask.transactionEntry.sort(COMPARATOR);
                for(Long task : removingTaskSet)
                    waitingTaskQueueMap.remove(task);
            }
            return resultTask;
        }

        //метод, считывающий задания из базы
        synchronized void addTasks(List<TransactionInfo> transactionInfoList) {
            Map<Long, TransactionInfo> newWaitingTaskQueueMap = new OrderedMap<>();
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
                    waitingTaskQueueMap.entrySet().removeIf(entry -> groupId != null && groupId.equals(getTransactionInfoGroupId(entry.getValue())));
                }
                proceededTaskList.remove(transactionEntry.getKey().id);
            }
            currentlyProceededGroups.remove(groupId);
        }

        private String getTransactionInfoGroupId(TransactionInfo transactionInfo) {
            try {
                MachineryHandler clsHandler = (MachineryHandler) getHandler(transactionInfo.handlerModel, remote);
                return clsHandler == null ? "No handler" : clsHandler.getGroupId(transactionInfo);

            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
                equipmentLogger.error("EquipmentServer Error: ", e);
                return "No handler";
            }
        }

        public String getTaskInfo() {
            return String.format("Task Pool. currentlyProceededGroups: %s; waiting: %s; proceeded %s; succeeded %s",
                    currentlyProceededGroups, waitingTaskQueueMap.keySet(), proceededTaskList, succeededTaskList);
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

                        remote.processingTransaction(transactionInfo.id, LocalDateTime.now());

                        if (clsHandler instanceof TerminalHandler)
                            ((TerminalHandler) clsHandler).saveTransactionTerminalInfo((TransactionTerminalInfo) transactionInfo);

                    } catch (Exception e) {
                        errorTransactionReport(transactionInfo.id, e);
                    }
                }

                try {
                    Map<Long, SendTransactionBatch> succeededMachineryInfoMap = clsHandler.sendTransaction(transactionEntry);

                    processTransactionLogger.info(String.format("   Sending transaction group %s: confirm to server, count : %s ", groupId, succeededMachineryInfoMap.size()));

                    for (TransactionInfo transactionInfo : transactionEntry) {
                        boolean noErrors = true;
                        SendTransactionBatch batch = succeededMachineryInfoMap.get(transactionInfo.id);
                        if(batch != null && batch.exception != null) {
                            noErrors = false;
                            processTransactionLogger.info(String.format("   Sending transaction group %s (%s): found batch exception", groupId, transactionInfo.id));
                            errorTransactionReport(transactionInfo.id, batch.exception);
                        }

                        try {
                            if (batch != null) {
                                List<MachineryInfo> succeededMachineryInfoList = batch.succeededMachineryList;
                                if (succeededMachineryInfoList != null && getEnabledMachineryInfoList(succeededMachineryInfoList).size() != getEnabledMachineryInfoList(transactionInfo.machineryInfoList).size()) {
                                    processTransactionLogger.info(String.format("   Sending transaction group %s (%s): not all machinery " + getEnabledMachineryInfoList(succeededMachineryInfoList).size() + " - " + getEnabledMachineryInfoList(transactionInfo.machineryInfoList).size(), groupId, transactionInfo.id));
                                    noErrors = false;
                                }
                                if ((clsHandler instanceof CashRegisterHandler || clsHandler instanceof ScalesHandler) && succeededMachineryInfoList != null) {
                                    processTransactionLogger.info(String.format("   Sending transaction group %s (%s): confirm machinery to server", groupId, transactionInfo.id));
                                    remote.succeedMachineryTransaction(transactionInfo.id, succeededMachineryInfoList, LocalDateTime.now());
                                }
                                if(batch.clearedMachineryList != null && !batch.clearedMachineryList.isEmpty())
                                    remote.clearedMachineryTransaction(transactionInfo.id, batch.clearedMachineryList);
                                if(batch.deleteBarcodeSet != null && !batch.deleteBarcodeSet.isEmpty()) {
                                    remote.succeedDeleteBarcode(batch.nppGroupMachinery, batch.deleteBarcodeSet);
                                }
                            }
                        } catch (Exception e) {
                            noErrors = false;
                            processTransactionLogger.error("EquipmentServerError confirm: ", e);
                            errorTransactionReport(transactionInfo.id, e);
                        }

                        if (noErrors) {
                            processTransactionLogger.info(String.format("   Sending transaction group %s(%s): confirm transaction to server", groupId, transactionInfo.id));
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
        }

        public List<MachineryInfo> getEnabledMachineryInfoList (List<MachineryInfo> machineryInfoList) {
            List<MachineryInfo> enabledMachineryInfoList = new ArrayList<>();
            for(MachineryInfo machinery : machineryInfoList) {
                if(machinery.enabled)
                    enabledMachineryInfoList.add(machinery);
            }
            return enabledMachineryInfoList.isEmpty() ? machineryInfoList : enabledMachineryInfoList;
        }

        private void errorTransactionReport(Long idTransactionInfo, Throwable e) {
            try {
                remote.errorTransactionReport(idTransactionInfo, e);
            } catch (Exception e1) {
                errorEquipmentServerReport(e1);
            }
        }

        private void succeededTransaction(Long idTransactionInfo) {
            try {
                remote.succeedTransaction(idTransactionInfo, LocalDateTime.now());
            } catch (Exception ignored) {
            }
        }

        private void errorEquipmentServerReport(Exception e) {
            try {
                reportEquipmentServerError(remote, sidEquipmentServer, e);
            } catch (Exception ignored) {
            }
        }
    }

    public static java.sql.Time localTimeToSqlTime(LocalTime value) {
        return value != null ? java.sql.Time.valueOf(value) : null;
    }

    public static LocalTime sqlTimeToLocalTime(java.sql.Time value) {
        return value != null ? value.toLocalTime() : null;
    }

    public static java.sql.Date localDateToSqlDate(LocalDate value) {
        return value != null ? java.sql.Date.valueOf(value) : null;
    }

    public static LocalDate sqlDateToLocalDate(java.sql.Date value) {
        return value != null ? value.toLocalDate() : null;
    }

    public static java.sql.Timestamp localDateTimeToSqlTimestamp(LocalDateTime value) {
        return value != null ? java.sql.Timestamp.valueOf(value) : null;
    }

    public static LocalDateTime sqlTimestampToLocalDateTime(java.sql.Timestamp value) {
        return value != null ? value.toLocalDateTime() : null;
    }

}