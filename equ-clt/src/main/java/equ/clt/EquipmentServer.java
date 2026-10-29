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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static equ.clt.ProcessMonitorEquipmentServer.removeInterruptedTransaction;

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

    //periodic jobs (reading transactions, stop-lists, sales etc.), each job in its own thread and never in parallel with itself
    private final AtomicReference<ScheduledExecutorService> jobsExecutor = new AtomicReference<>(); //is also stopped from the thread calling stop()

    ExecutorService singleTransactionExecutor;
    List<Future> futures;

    volatile boolean needReconnect = false; //set by periodic jobs, read by the main cycle

    //depend only on the modules of the server, so they can change only after server restart (and reconnect)
    private boolean enabledStopListInfo;
    private boolean enabledDeleteBarcodeInfo;
    private boolean enabledTerminalInfo;

    private Integer transactionThreadCount;
    private boolean mergeBatches = false;
    private boolean disableSales = false;
    private boolean disableStopLists = false;
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

                while (!Thread.currentThread().isInterrupted()) { //stop()

                    try {

                        if(needReconnect) {
                            remote = null;
                            stopJobs(); //otherwise they keep working with the old remote until reconnect
                            if (Thread.currentThread().isInterrupted()) //stop() during stopJobs
                                break;
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
                                        //after reconnect jobs and workers of the previous connection are still running
                                        stopJobs();
                                        if (singleTransactionExecutor != null)
                                            singleTransactionExecutor.shutdownNow();
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

                                    enabledStopListInfo = remote.enabledStopListInfo();
                                    enabledDeleteBarcodeInfo = remote.enabledDeleteBarcodeInfo();
                                    enabledTerminalInfo = remote.enabledTerminalInfo();

                                    initDaemonThreads(remote, sidEquipmentServer, millis, sendSalesDelay);
                                    
                                } catch (RemoteException e) {
                                    equipmentLogger.error("Get remote logics error : ", e);
                                    remote = null; //initialization is not finished, it will be repeated in the next cycle
                                }
                            }
                        }

                        if (remote != null) {
                            //checks the connection: if the server has been restarted, exception leads to reconnect
                            remote.getEquApiVersion();
                        }

                    } catch (Exception e) {
                        equipmentLogger.error("Unhandled exception in main cycle: ", e);
                        remote = null;
                        stopJobs();

                        if (singleTransactionExecutor != null)
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

    private void initDaemonThreads(final EquipmentServerInterface remote, final String sidEquipmentServer, final long millis, int sendSalesDelay) {

        stopJobs(); //jobs of the previous connection are normally already stopped by the main cycle
        jobsExecutor.set(Executors.newScheduledThreadPool(7, new DaemonThreadFactory("EquipmentServerJob"))); //at most 7 jobs

        if (singleTransactionExecutor != null && !singleTransactionExecutor.isShutdown()) {
            //reconnect after ConnectException in a job: workers and the queue are kept (they may be sending transactions now),
            //the next tasks are created with the new remote
            taskPool.setRemote(remote);
        } else {
            TaskPool pool = new TaskPool(remote, sidEquipmentServer);
            taskPool = pool;
            //workers use their own executor and pool: the fields are replaced after restart of the main cycle
            ExecutorService executor = Executors.newFixedThreadPool(transactionThreadCount);
            singleTransactionExecutor = executor;
            futures = new ArrayList<>();
            for (int i = 0; i < transactionThreadCount; i++) {
                futures.add(executor.submit(() -> {
                    while (!Thread.currentThread().isInterrupted() && !executor.isShutdown()) {
                        try {
                            SingleTransactionTask task = pool.getTask(millis);
                            if (task != null) {
                                equipmentLogger.info("task group started: " + task.groupId);
                                try {
                                    task.run();
                                } finally {
                                    //interrupt of a transaction by ProcessMonitor (notInterruptedTransaction) mustn't stop the worker
                                    if (!executor.isShutdown())
                                        Thread.interrupted();
                                }
                                equipmentLogger.info("task group done: " + task.groupId);
                            }
                        } catch (InterruptedException e) {
                            //shutdown or future.cancel(true)
                            Thread.currentThread().interrupt();
                            break;
                        } catch (Throwable t) { //Throwable: errors of handlers (e.g. NoClassDefFoundError) mustn't kill the worker
                            equipmentLogger.error("Unhandled exception in singleTransactionExecutor: ", t);
                        }
                    }
                }));
            }
        }

        TaskPool jobTaskPool = taskPool;
        scheduleJob("ReadTransactionInfo", processTransactionLogger, millis, () -> {
            processTransactionLogger.info("ReadTransactionInfo started");
            jobTaskPool.addTasks(remote.readTransactionInfo(sidEquipmentServer)); //errors are logged by PeriodicJob
            processTransactionLogger.info("ReadTransactionInfo finished");
        });

        if (!disableStopLists && enabledStopListInfo)
            scheduleJob("StopList", processStopListLogger, millis, () -> StopListEquipmentServer.processStopListInfo(remote));

        if (enabledDeleteBarcodeInfo)
            scheduleJob("DeleteBarcode", processDeleteBarcodeLogger, millis, () -> DeleteBarcodeEquipmentServer.processDeleteBarcodeInfo(remote));

        //sendSalesDelay - number of skipped cycles between sending sales
        if (!disableSales)
            scheduleJob("SendSales", sendSalesLogger, millis * (Math.max(sendSalesDelay, 0) + 1), () -> SendSalesEquipmentServer.sendSalesInfo(remote, sidEquipmentServer, mergeBatches));

        if (enabledTerminalInfo)
            scheduleJob("TerminalDocument", sendTerminalDocumentLogger, millis, () -> TerminalDocumentEquipmentServer.sendTerminalDocumentInfo(remote, sidEquipmentServer));

        scheduleJob("MachineryExchange", machineryExchangeLogger, millis, () -> MachineryExchangeEquipmentServer.processMachineryExchange(remote, sidEquipmentServer));

        scheduleJob("ProcessMonitor", processMonitorLogger, millis, () -> ProcessMonitorEquipmentServer.process(EquipmentServer.this, remote, sidEquipmentServer));
    }

    private void scheduleJob(String name, Logger logger, long delay, RemoteAction action) {
        //fixed delay: the next run starts delay ms after the end of the previous one, so the job never runs in parallel with itself
        jobsExecutor.get().scheduleWithFixedDelay(new PeriodicJob(name, logger, action), 0, Math.max(delay, 1), TimeUnit.MILLISECONDS);
    }

    private void stopJobs() {
        ScheduledExecutorService executor = jobsExecutor.getAndSet(null); //can be called from the main cycle and from stop() at the same time
        if (executor != null) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(waitForThreadDeath != null ? waitForThreadDeath : 300, TimeUnit.SECONDS)) //5 minutes
                    equipmentLogger.error("Periodic jobs have not been stopped in time");
            } catch (InterruptedException e) {
                equipmentLogger.error("Thread has been interrupted while waiting for periodic jobs: ", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    //logs and swallows all errors: if a job throws, ScheduledExecutorService silently stops scheduling it
    private class PeriodicJob implements Runnable {
        private final String name;
        private final Logger logger;
        private final RemoteAction action;

        PeriodicJob(String name, Logger logger, RemoteAction action) {
            this.name = name;
            this.logger = logger;
            this.action = action;
        }

        @Override
        public void run() {
            Thread thread = Thread.currentThread();
            String threadName = thread.getName();
            thread.setName("EquipmentServer " + name); //for thread dumps and ProcessMonitor
            try {
                if (isTimeToRun())
                    action.run();
            } catch (ConnectException e) {
                logger.error("Connect Exception: ", e);
                needReconnect = true;
            } catch (InterruptedException e) {
                //stopJobs
                thread.interrupt();
            } catch (Throwable t) {
                if (t instanceof UnmarshalException && t.getCause() instanceof InvalidClassException)
                    logger.error("API changed! InvalidClassException");
                logger.error("Unhandled exception in " + name + ": ", t);
            } finally {
                thread.setName(threadName); //the pool thread can run another job next time
            }
        }
    }

    private interface RemoteAction {
        void run() throws Exception;
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

    public static void reportEquipmentServerError(EquipmentServerInterface remote, String sidEquipmentServer, String result, String extraData) throws RemoteException, SQLException {
        equipmentLogger.error("Equipment server error: " + result);
        remote.errorEquipmentServerReport(sidEquipmentServer, new Throwable(result).fillInStackTrace(), extraData);
    }


    public static void reportEquipmentServerError(EquipmentServerInterface remote, String sidEquipmentServer, Throwable throwable) throws RemoteException, SQLException {
        reportEquipmentServerError(remote, sidEquipmentServer, throwable, null);
    }

    static void reportEquipmentServerError(EquipmentServerInterface remote, String sidEquipmentServer, Throwable throwable, String extraData) throws RemoteException, SQLException {
        remote.errorEquipmentServerReport(sidEquipmentServer, throwable, extraData);
    }


    private static Comparator<TransactionInfo> COMPARATOR = Comparator.comparing(o -> o.dateTimeCode);

    public void stop() {
        //main cycle first: otherwise it can create new jobs and workers after they are stopped
        interruptThread(thread);
        stopJobs();
        if (singleTransactionExecutor != null)
            singleTransactionExecutor.shutdown();
        if(futures != null)
            for(Future future : futures) {
                future.cancel(true);
            }
    }

    public void setMergeBatches(boolean mergeBatches) {
        this.mergeBatches = mergeBatches;
    }

    public void setDisableSales(boolean disableSales) {
        this.disableSales = disableSales;
    }

    public void setDisableStopLists(boolean disableStopLists) {
        this.disableStopLists = disableStopLists;
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
            Thread.currentThread().interrupt();
        }
    }

    private boolean isTimeToRun() {
        if (equipmentServerSettings == null)
            return false;
        return isTimeToRun(LocalTime.now(), equipmentServerSettings.timeFrom, equipmentServerSettings.timeTo);
    }

    static boolean isTimeToRun(LocalTime now, LocalTime timeFrom, LocalTime timeTo) {
        if (timeFrom == null || timeTo == null)
            return true;
        if (timeFrom.isAfter(timeTo))
            return !now.isBefore(timeFrom) || !now.isAfter(timeTo);
        else
            return !now.isBefore(timeFrom) && !now.isAfter(timeTo);
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

        //after reconnect
        synchronized void setRemote(EquipmentServerInterface remote) {
            this.remote = remote;
        }

        //gives tasks to worker threads
        //waits for the task up to timeout ms, woken up by addTasks and markProceeded; returns null if there is no task
        synchronized SingleTransactionTask getTask(long timeout) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeout;
            while (true) {
                SingleTransactionTask task = findTask();
                if (task != null)
                    return task;
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0)
                    return null;
                wait(remaining);
            }
        }

        private SingleTransactionTask findTask() {
            SingleTransactionTask resultTask = null;

            //находим transactionInfo без ошибок либо с самой старой последней ошибкой в очереди
            String minGroupId = null;
            Timestamp minLastErrorDate = null;
            MachineryHandler minClsHandler = null;
            Set<String> checkedGroupIdSet = new HashSet<>();
            for(TransactionInfo transactionInfo : waitingTaskQueueMap.values()) {
                MachineryHandler clsHandler = getTransactionInfoHandler(transactionInfo);
                String groupId = getTransactionInfoGroupId(clsHandler, transactionInfo);
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
            }
            //находим все transactionInfo с таким же groupId
            if(minGroupId != null) {
                currentlyProceededGroups.add(minGroupId);
                resultTask = new SingleTransactionTask(this, remote, minGroupId, minClsHandler, new ArrayList<>(), sidEquipmentServer);
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
            notifyAll();
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
            //the group is free now, its next transactions can be taken by any worker
            notifyAll();
        }

        private String getTransactionInfoGroupId(TransactionInfo transactionInfo) {
            return getTransactionInfoGroupId(getTransactionInfoHandler(transactionInfo), transactionInfo);
        }

        //errors (also unchecked, e.g. NoClassDefFoundError in a handler) put the transaction to the "No handler" group:
        //it gets an error report instead of being stuck in the queue or breaking getTask / markProceeded
        private MachineryHandler getTransactionInfoHandler(TransactionInfo transactionInfo) {
            try {
                return (MachineryHandler) getHandler(transactionInfo.handlerModel, remote);
            } catch (Throwable t) {
                equipmentLogger.error("EquipmentServer Error: ", t);
                return null;
            }
        }

        private String getTransactionInfoGroupId(MachineryHandler clsHandler, TransactionInfo transactionInfo) {
            try {
                return clsHandler == null ? "No handler" : clsHandler.getGroupId(transactionInfo);
            } catch (Throwable t) {
                equipmentLogger.error("EquipmentServer Error: ", t);
                return "No handler";
            }
        }

        public synchronized String getTaskInfo() {
            return String.format("Task Pool. currentlyProceededGroups: %s; waiting: %s; proceeded %s; succeeded %s",
                    currentlyProceededGroups, waitingTaskQueueMap.keySet(), proceededTaskList, succeededTaskList);
        }

    }

    class SingleTransactionTask implements Runnable {
        //pool that gave the task: after restart of the main cycle the taskPool field already refers to a new pool
        TaskPool pool;
        EquipmentServerInterface remote;
        String groupId;
        MachineryHandler clsHandler;
        List<TransactionInfo> transactionEntry;
        String sidEquipmentServer;

        public SingleTransactionTask(TaskPool pool, EquipmentServerInterface remote, String groupId, MachineryHandler clsHandler, List<TransactionInfo> transactionEntry, String sidEquipmentServer) {
            this.pool = pool;
            this.remote = remote;
            this.groupId = groupId;
            this.clsHandler = clsHandler;
            this.transactionEntry = transactionEntry;
            this.sidEquipmentServer = sidEquipmentServer;
        }

        public void run() {
            Map<TransactionInfo, Boolean> transactionInfoMap = new HashMap<>();
            try {
                sendTransactions(transactionInfoMap);
            } finally {
                //otherwise after an Error of the handler the group stays in currentlyProceededGroups and is never sent again
                for (TransactionInfo transactionInfo : transactionEntry)
                    transactionInfoMap.putIfAbsent(transactionInfo, false);
                pool.markProceeded(groupId, transactionInfoMap);
            }
        }

        private void sendTransactions(Map<TransactionInfo, Boolean> transactionInfoMap) {

            processTransactionLogger.info(String.format("   Sending transaction group %s: start, count : %s", groupId, transactionEntry.size()));
            //transactions without handler
            if (groupId != null && groupId.equals("No handler")) {
                for (TransactionInfo transactionInfo : transactionEntry) {
                    transactionInfoMap.put(transactionInfo, false);
                    errorTransactionReport(transactionInfo.id, new Throwable(transactionInfo.handlerModel == null ? String.format("Transaction %s: No handler", transactionInfo.id)
                            : String.format("Transaction %s: handler %s can't be used, see equ logs", transactionInfo.id, transactionInfo.handlerModel)));
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
                } catch (Throwable t) { //Throwable: errors of handlers (e.g. NoClassDefFoundError) are reported as exceptions
                    processTransactionLogger.error("EquipmentServerError: ", t);
                    errorEquipmentServerReport(t);
                }

            }
        }

        public List<MachineryInfo> getEnabledMachineryInfoList (List<MachineryInfo> machineryInfoList) {
            List<MachineryInfo> enabledMachineryInfoList = new ArrayList<>();
            for(MachineryInfo machinery : machineryInfoList) {
                if(machinery.enabled)
                    enabledMachineryInfoList.add(machinery);
            }
            return enabledMachineryInfoList.isEmpty() ? machineryInfoList : enabledMachineryInfoList;
        }

        private void errorTransactionReport(Long transactionId, Throwable e) {
            try {
                removeInterruptedTransaction(transactionId);
                remote.errorTransactionReport(transactionId, e);
            } catch (Exception e1) {
                equipmentLogger.error("Equipment server error at transaction " + transactionId, e);
                errorEquipmentServerReport(e1);
            }
        }

        private void succeededTransaction(Long idTransactionInfo) {
            try {
                remote.succeedTransaction(idTransactionInfo, LocalDateTime.now());
            } catch (Exception ignored) {
            }
        }

        private void errorEquipmentServerReport(Throwable e) {
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