package equ.clt;

import equ.api.*;
import equ.api.cashregister.*;
import equ.api.terminal.TerminalDocumentBatch;
import equ.api.terminal.TerminalHandler;
import equ.api.terminal.TerminalInfo;
import equ.api.terminal.TransactionTerminalInfo;
import lsfusion.interop.remote.RMIUtils;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.rmi.ConnectException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.SynchronousQueue;

public class EquipmentServer {

    private Thread thread;

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

    public EquipmentServer(final String sidEquipmentServer, final String serverHost, final int serverPort, final String serverDB) {
        
        final int connectPort = serverPort > 0 ? serverPort : Registry.REGISTRY_PORT;

        thread = new Thread(new Runnable() {

            private EquipmentServerInterface remote = null;

            @Override
            public void run() {

                int millis = 10000;               
                               
                while (true) {

                    try {
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
                                    } else if (equipmentServerSettings.delay != null)
                                        millis = equipmentServerSettings.delay;

                                    initDaemonThreads(remote, sidEquipmentServer);                                   
                                    
                                } catch (RemoteException e) {
                                    logger.error("Get remote logics error : ", e);
                                }
                            }
                        }

                        if (remote != null) {
                            processTransactionConsumer.scheduleIfNotScheduledYet();                         
                            processStopListConsumer.scheduleIfNotScheduledYet();
                            sendSalesConsumer.scheduleIfNotScheduledYet();                                                       
                            sendSoftCheckConsumer.scheduleIfNotScheduledYet();
                            sendTerminalDocumentConsumer.scheduleIfNotScheduledYet();
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
                processTransactionInfo(remote, sidEquipmentServer);
            }
        };
        processTransactionThread = new Thread(processTransactionConsumer);
        processTransactionThread.setDaemon(true);
        processTransactionThread.start();

        processStopListConsumer = new Consumer() {
            @Override
            void runTask() throws Exception{
                processStopListInfo(remote, sidEquipmentServer);
            }
        };
        processStopListThread = new Thread(processStopListConsumer);
        processStopListThread.setDaemon(true);
        processStopListThread.start();

        sendSalesConsumer = new Consumer() {
            @Override
            void runTask() throws Exception{
                sendSalesInfo(remote, sidEquipmentServer, equipmentServerSettings);
            }
        };
        sendSalesThread = new Thread(sendSalesConsumer);
        sendSalesThread.setDaemon(true);
        sendSalesThread.start();

        sendSoftCheckConsumer = new Consumer() {
            @Override
            void runTask() throws Exception{
                sendSoftCheckInfo(remote);
            }
        };
        sendSoftCheckThread = new Thread(sendSoftCheckConsumer);
        sendSoftCheckThread.setDaemon(true);
        sendSoftCheckThread.start();

        sendTerminalDocumentConsumer = new Consumer() {
            @Override
            void runTask() throws Exception{
                sendTerminalDocumentInfo(remote, sidEquipmentServer);
            }
        };
        sendTerminalDocumentThread = new Thread(sendTerminalDocumentConsumer);
        sendTerminalDocumentThread.setDaemon(true);
        sendTerminalDocumentThread.start();
    }


    private void processTransactionInfo(EquipmentServerInterface remote, String sidEquipmentServer) throws SQLException, RemoteException, FileNotFoundException, UnsupportedEncodingException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        logger.info("Process TransactionInfo");
        List<TransactionInfo> transactionInfoList = remote.readTransactionInfo(sidEquipmentServer);
        Collections.sort(transactionInfoList, COMPARATOR);
        for (TransactionInfo transactionInfo : transactionInfoList) {

            boolean noHandler = true;
            Map<String, List<MachineryInfo>> handlerModelMap = getHandlerModelMap(transactionInfo);

            logger.info("Sending transactions started");
            for (Map.Entry<String, List<MachineryInfo>> entry : handlerModelMap.entrySet()) {
                noHandler = false;
                if (entry.getKey() != null) {                    
                    try {
                        Object clsHandler = getHandler(entry.getValue().get(0).handlerModel.trim(), remote);
                        if (clsHandler instanceof TerminalHandler)
                            ((TerminalHandler) clsHandler).saveTransactionTerminalInfo((TransactionTerminalInfo) transactionInfo);
                        transactionInfo.sendTransaction(clsHandler, entry.getValue());
                    } catch (Exception e) {
                        remote.errorTransactionReport(transactionInfo.id, e);
                        return;
                    }
                }
            }
            if(noHandler)
                remote.errorTransactionReport(transactionInfo.id, new Throwable(String.format("Transaction %s: No handler", transactionInfo.id)));
            else
                remote.succeedTransaction(transactionInfo.id, new Timestamp(Calendar.getInstance().getTime().getTime()));
            logger.info("Sending transactions finished");
        }
    }

    private void processStopListInfo(EquipmentServerInterface remote, String sidEquipmentServer) throws RemoteException, SQLException {
        logger.info("Process StopListInfo");
        List<StopListInfo> stopListInfoList = remote.readStopListInfo(sidEquipmentServer);
        for (StopListInfo stopListInfo : stopListInfoList) {
            
            for(Map.Entry<String, Set<String>> entry : stopListInfo.handlerDirectoryMap.entrySet()) {

                try {
                    Object clsHandler = getHandler(entry.getKey(), remote);
                    if (clsHandler instanceof CashRegisterHandler)
                        ((CashRegisterHandler) clsHandler).sendStopListInfo(stopListInfo, entry.getValue());
                } catch (Exception e) {
                    remote.errorStopListReport(stopListInfo.number, e);
                    return;
                }
            }
            remote.succeedStopList(stopListInfo.number, stopListInfo.idStockSet);
        }        
        logger.info("Process StopListInfo finished");
    }

    private void sendSalesInfo(EquipmentServerInterface remote, String sidEquipmentServer, EquipmentServerSettings settings) throws SQLException, IOException {
        logger.info("Send SalesInfo");
        Integer numberAtATime = equipmentServerSettings == null ? null : equipmentServerSettings.numberAtATime;
        List<CashRegisterInfo> cashRegisterInfoList = remote.readCashRegisterInfo(sidEquipmentServer);
        List<RequestExchange> requestExchangeList = remote.readRequestExchange(sidEquipmentServer);
        Set<Integer> succeededRequestsSet = new HashSet<Integer>();
        
        Map<String, List<CashRegisterInfo>> handlerModelCashRegisterMap = new HashMap<String, List<CashRegisterInfo>>();
        for (CashRegisterInfo cashRegister : cashRegisterInfoList) {
            if (!handlerModelCashRegisterMap.containsKey(cashRegister.handlerModel))
                handlerModelCashRegisterMap.put(cashRegister.handlerModel, new ArrayList<CashRegisterInfo>());
            handlerModelCashRegisterMap.get(cashRegister.handlerModel).add(cashRegister);
        }

        for (Map.Entry<String, List<CashRegisterInfo>> entry : handlerModelCashRegisterMap.entrySet()) {
            String handlerModel = entry.getKey();

            if (handlerModel != null) {

                try {
                    CashRegisterHandler clsHandler = (CashRegisterHandler) getHandler(handlerModel, remote);

                    Set<String> directorySet = new HashSet<String>();
                    for (CashRegisterInfo cashRegisterInfo : entry.getValue()) {
                        directorySet.add(cashRegisterInfo.directory);
                    }

                    Map succeededSoftCheckInfo = clsHandler.requestSucceededSoftCheckInfo(directorySet);
                    if (succeededSoftCheckInfo != null && !succeededSoftCheckInfo.isEmpty()) {
                        logger.info("Sending succeeded SoftCheckInfo");
                        String result = remote.sendSucceededSoftCheckInfo(succeededSoftCheckInfo);
                        if (result != null)
                            reportEquipmentServerError(remote, sidEquipmentServer, result);
                    }

                    if (!requestExchangeList.isEmpty()) {
                        logger.info("Requesting SalesInfo");
                        String result = clsHandler.requestSalesInfo(requestExchangeList);
                        if (result == null) {
                            for (RequestExchange request : requestExchangeList) {
                                if (request.requestSalesInfo)
                                    succeededRequestsSet.add(request.requestExchange);
                            }
                        } else {
                            reportEquipmentServerError(remote, sidEquipmentServer, result);
                        }
                    }

                    Set<String> cashDocumentSet = remote.readCashDocumentSet(sidEquipmentServer);
                    CashDocumentBatch cashDocumentBatch = clsHandler.readCashDocumentInfo(cashRegisterInfoList, cashDocumentSet);
                    if (cashDocumentBatch != null && cashDocumentBatch.cashDocumentList != null && !cashDocumentBatch.cashDocumentList.isEmpty()) {
                        logger.info("Sending CashDocuments");
                        String result = remote.sendCashDocumentInfo(cashDocumentBatch.cashDocumentList, sidEquipmentServer);
                        if (result != null) {
                            reportEquipmentServerError(remote, sidEquipmentServer, result);
                        } else {
                            clsHandler.finishReadingCashDocumentInfo(cashDocumentBatch);
                        }
                    }

                    SalesBatch salesBatch = clsHandler.readSalesInfo(cashRegisterInfoList);
                    if (salesBatch == null) {
                        logger.info("SalesInfo is empty");
                    } else {
                        logger.info("Sending SalesInfo");
                        String result = remote.sendSalesInfo(salesBatch.salesInfoList, sidEquipmentServer, numberAtATime);
                        if (result != null) {
                            reportEquipmentServerError(remote, sidEquipmentServer, result);
                        } else {
                            logger.info("Finish Reading starts");
                            clsHandler.finishReadingSalesInfo(salesBatch);
                        }
                    }

                    if(!requestExchangeList.isEmpty()) {
                        for(RequestExchange request : requestExchangeList) {
                            if(!request.requestSalesInfo) {
                                Map<String, BigDecimal> zReportSumMap = remote.readRequestZReportSumMap(request);
                                String checkSumResult = zReportSumMap.isEmpty() ? null : clsHandler.checkZReportSum(zReportSumMap, request.idStock);
                                succeededRequestsSet.add(request.requestExchange);
                                if (checkSumResult != null) {
                                    reportEquipmentServerError(remote, sidEquipmentServer, checkSumResult);
                                } 
                            }
                        }
                    }
                    
                    if(!succeededRequestsSet.isEmpty())
                        remote.finishRequestExchange(succeededRequestsSet);
                        
                    
                } catch (Throwable e) {
                    logger.error("Equipment server error: ", e);
                    remote.errorEquipmentServerReport(sidEquipmentServer, e.fillInStackTrace());
                    return;
                }
            }
        }
    }

    private void sendSoftCheckInfo(EquipmentServerInterface remote) throws RemoteException, SQLException {
        logger.info("Send SoftCheckInfo");
        List<SoftCheckInfo> softCheckInfoList = remote.readSoftCheckInfo();
        if (softCheckInfoList != null && !softCheckInfoList.isEmpty()) {
            logger.info("Sending SoftCheckInfo started");
            for (SoftCheckInfo entry : softCheckInfoList) {
                if (entry.handler != null) {
                    try {
                        Object clsHandler = getHandler(entry.handler.trim(), remote);
                        entry.sendSoftCheckInfo(clsHandler);
                        remote.finishSoftCheckInfo(entry.invoiceMap);
                    } catch (Exception e) {
                        logger.error("Sending SoftCheckInfo error", e);
                        return;
                    }
                }
            }
            logger.info("Sending SoftCheckInfo finished");
        }
    }

    private void sendTerminalDocumentInfo(EquipmentServerInterface remote, String sidEquipmentServer) throws SQLException, IOException {
        logger.info("Send TerminalDocumentInfo");
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
                        logger.info("TerminalDocumentInfo is empty");
                    } else {
                        logger.info("Sending TerminalDocumentInfo");
                        String result = remote.sendTerminalInfo(documentBatch.documentDetailList, sidEquipmentServer);
                        if (result != null) {
                            reportEquipmentServerError(remote, sidEquipmentServer, result);
                        } else {
                            logger.info("Finish Reading starts");
                            clsHandler.finishReadingTerminalDocumentInfo(documentBatch);
                        }
                    }
                } catch (Throwable e) {
                    logger.error("Equipment server error: ", e);
                    remote.errorEquipmentServerReport(sidEquipmentServer, e.fillInStackTrace());
                    return;
                }
            }
        }
    }

    private Map<String, List<MachineryInfo>> getHandlerModelMap(TransactionInfo<MachineryInfo, ItemInfo> transactionInfo) {
        Map<String, List<MachineryInfo>> handlerModelMap = new HashMap<String, List<MachineryInfo>>();
        for (MachineryInfo machinery : transactionInfo.machineryInfoList) {
            if (machinery.handlerModel != null) {
                if (!handlerModelMap.containsKey(machinery.handlerModel))
                    handlerModelMap.put(machinery.handlerModel, new ArrayList<MachineryInfo>());
                handlerModelMap.get(machinery.handlerModel).add(machinery);
            }
        }
        return handlerModelMap;
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
}