package equ.clt;

import equ.api.*;
import equ.api.cashregister.*;
import equ.api.terminal.TerminalDocumentBatch;
import equ.api.terminal.TerminalHandler;
import equ.api.terminal.TerminalInfo;
import equ.api.terminal.TransactionTerminalInfo;
import lsfusion.interop.remote.RMIUtils;
import org.apache.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.rmi.ConnectException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Date;
import java.util.*;

public class EquipmentServer {

    private Thread thread;

    protected final static Logger logger = Logger.getLogger(EquipmentServer.class);
    Map<String, Object> handlerMap = new HashMap<String, Object>();
    EquipmentServerSettings equipmentServerSettings;
    
    DBSettings dbSettings;

    public EquipmentServer(final String sidEquipmentServer, final String serverHost, final int serverPort, final String serverDB, final String sqlUsername,
                           final String sqlPassword, final String sqlIp, final String sqlPort, final String sqlDBName) {
        this.dbSettings = new DBSettings(sqlUsername, sqlPassword, sqlIp, sqlPort, sqlDBName);
        
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
                                    if(equipmentServerSettings == null) {
                                        logger.error("Equipment Server " + sidEquipmentServer + " not found"); 
                                    } else if (equipmentServerSettings.delay != null)
                                        millis = equipmentServerSettings.delay;
                                } catch (RemoteException e) {
                                    logger.error("Get remote logics error : ", e);
                                }
                            }
                        }

                        if (remote != null) {

                            processTransactionInfo(remote, sidEquipmentServer);
                            sendSalesInfo(remote, sidEquipmentServer, equipmentServerSettings == null ? null : equipmentServerSettings.numberAtATime);
                            sendSoftCheckInfo(remote);
                            sendTerminalDocumentInfo(remote, sidEquipmentServer);
                            logger.info("Transaction completed");
                        }

                    } catch (Exception e) {
                        logger.error("Unhandled exception : ", e);
                        remote = null;
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


    private void processTransactionInfo(EquipmentServerInterface remote, String sidEquipmentServer) throws SQLException, RemoteException, FileNotFoundException, UnsupportedEncodingException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        logger.info("Process TransactionInfo");
        List<TransactionInfo> transactionInfoList = remote.readTransactionInfo(sidEquipmentServer);
        Collections.sort(transactionInfoList, COMPARATOR);
        for (TransactionInfo transactionInfo : transactionInfoList) {

            Map<String, List<MachineryInfo>> handlerModelMap = getHandlerModelMap(transactionInfo);

            logger.info("Sending transactions started");
            for (Map.Entry<String, List<MachineryInfo>> entry : handlerModelMap.entrySet()) {
                if (entry.getKey() != null) {
                    try {
                        Object clsHandler = getHandler(entry.getValue().get(0).handlerModel.trim(), remote);
                        if(clsHandler instanceof TerminalHandler)
                            ((TerminalHandler) clsHandler).saveTransactionTerminalInfo((TransactionTerminalInfo) transactionInfo);
                        transactionInfo.sendTransaction(clsHandler, entry.getValue());
                    } catch (Exception e) {
                        remote.errorTransactionReport(transactionInfo.id, e);
                        return;
                    }
                }
            }
            remote.succeedTransaction(transactionInfo.id, new Timestamp(Calendar.getInstance().getTime().getTime()));
            logger.info("Sending transactions finished");
        }
    }
    
    private void sendSalesInfo(EquipmentServerInterface remote, String sidEquipmentServer, Integer numberAtATime) throws SQLException, IOException {
        logger.info("Send SalesInfo");
        List<CashRegisterInfo> cashRegisterInfoList = remote.readCashRegisterInfo(sidEquipmentServer);
        Map<Date, Set<String>> requestSalesInfo = remote.readRequestSalesInfo(sidEquipmentServer);

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
                    for(CashRegisterInfo cashRegisterInfo : entry.getValue()) {
                        directorySet.add(cashRegisterInfo.directory);   
                    }
                    
                    Map succeededSoftCheckInfo = clsHandler.requestSucceededSoftCheckInfo(directorySet, dbSettings);
                    if (succeededSoftCheckInfo != null && !succeededSoftCheckInfo.isEmpty()) {
                        logger.info("Sending succeeded SoftCheckInfo");
                        String result = remote.sendSucceededSoftCheckInfo(succeededSoftCheckInfo);
                        if (result != null)
                            reportEquipmentServerError(remote, sidEquipmentServer, result);
                    }

                    if (!requestSalesInfo.isEmpty()) {
                        logger.info("Requesting SalesInfo");
                        String result = clsHandler.requestSalesInfo(requestSalesInfo);
                        if (result != null) {
                            reportEquipmentServerError(remote, sidEquipmentServer, result);
                        }
                    }

                    Set<String> cashDocumentSet = remote.readCashDocumentSet(sidEquipmentServer);
                    CashDocumentBatch cashDocumentBatch = clsHandler.readCashDocumentInfo(cashRegisterInfoList, cashDocumentSet, dbSettings);
                    if (cashDocumentBatch != null && cashDocumentBatch.cashDocumentList != null && !cashDocumentBatch.cashDocumentList.isEmpty()) {
                        logger.info("Sending CashDocuments");
                        String result = remote.sendCashDocumentInfo(cashDocumentBatch.cashDocumentList, sidEquipmentServer);
                        if (result != null) {
                            reportEquipmentServerError(remote, sidEquipmentServer, result);
                        } else {
                            clsHandler.finishReadingCashDocumentInfo(cashDocumentBatch);
                        }
                    }

                    SalesBatch salesBatch = clsHandler.readSalesInfo(cashRegisterInfoList, dbSettings);
                    if (salesBatch == null) {
                        logger.info("SalesInfo is empty");
                    } else {
                        logger.info("Sending SalesInfo");
                        String result = remote.sendSalesInfo(salesBatch.salesInfoList, sidEquipmentServer, numberAtATime);
                        if (result != null) {
                            reportEquipmentServerError(remote, sidEquipmentServer, result);
                        }
                        else {
                            logger.info("Finish Reading starts");
                            clsHandler.finishReadingSalesInfo(salesBatch);
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
            if (entry.getKey() != null) {

                try {
                    String handlerModel = entry.getKey();
                    if (handlerModel != null) {
                        TerminalHandler clsHandler = (TerminalHandler) getHandler(handlerModel, remote);

                        TerminalDocumentBatch documentBatch = clsHandler.readTerminalDocumentInfo(terminalInfoList);
                        if (documentBatch == null || documentBatch.documentDetailList == null || documentBatch.documentDetailList.isEmpty()) {
                            logger.info("TerminalDocumentInfo is empty");
                        } else {
                            logger.info("Sending TerminalDocumentInfo");
                            String result = remote.sendTerminalInfo(documentBatch.documentDetailList, sidEquipmentServer);
                            if (result != null) {
                                reportEquipmentServerError(remote, sidEquipmentServer, result);
                            }
                            else {
                                logger.info("Finish Reading starts");
                                clsHandler.finishReadingTerminalDocumentInfo(documentBatch);
                            }
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
    
    private Object getHandler(String handlerModel, EquipmentServerInterface remote) throws ClassNotFoundException, IllegalAccessException, InstantiationException, InvocationTargetException {
        Object clsHandler;
        if (handlerMap.containsKey(handlerModel))
            clsHandler = handlerMap.get(handlerModel);
        else {
            if (handlerModel.split("\\$").length == 1) {
                Class cls = Class.forName(handlerModel);
                clsHandler = cls.newInstance();
            } else {
                Class outerClass = Class.forName(handlerModel.split("\\$")[0]);
                Class innerClass = Class.forName(handlerModel);
                clsHandler = innerClass.getDeclaredConstructors()[0].newInstance(outerClass.newInstance());
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
}