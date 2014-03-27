package equ.clt;

import equ.api.*;
import lsfusion.interop.remote.RMIUtils;
import org.apache.log4j.Logger;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.rmi.ConnectException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.sql.SQLException;
import java.util.*;

public class EquipmentServer {

    private Thread thread;

    protected final static Logger logger = Logger.getLogger(EquipmentServer.class);
    Map<String, Object> handlerMap = new HashMap<String, Object>();
    EquipmentServerSettings equipmentServerSettings;
    
    String sqlUsername;
    String sqlPassword;
    String sqlIp;
    String sqlPort;
    String sqlDBName;

    public EquipmentServer(final String equServerID, final String serverUrl, final String serverDB, final String sqlUsername,
                           final String sqlPassword, final String sqlIp, final String sqlPort, final String sqlDBName) {
        this.sqlUsername = sqlUsername;        
        this.sqlPassword = sqlPassword;
        this.sqlIp = sqlIp;
        this.sqlPort = sqlPort;
        this.sqlDBName = sqlDBName;
        
        final String serverHost;
        final int serverPort;
        int dotIndex = serverUrl.indexOf(":");
        if (dotIndex > 0) {
            serverHost = serverUrl.substring(0, dotIndex);
            serverPort = Integer.parseInt(serverUrl.substring(dotIndex + 1));
        } else {
            serverHost = serverUrl;
            //default port
            serverPort = Registry.REGISTRY_PORT;
        }

        thread = new Thread(new Runnable() {

            private EquipmentServerInterface remote = null;

            @Override
            public void run() {

                int millis = 10000;
                while (true) {

                    try {
                        if (remote == null) {
                            try {
                                remote = RMIUtils.rmiLookup(serverHost, serverPort, serverDB, "EquipmentServer");
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
                                    equipmentServerSettings = remote.readEquipmentServerSettings(equServerID);
                                    if(equipmentServerSettings == null) {
                                        logger.error("Equipment Server " + equServerID + " not found"); 
                                    } else if (equipmentServerSettings.delay != null)
                                        millis = equipmentServerSettings.delay;
                                } catch (RemoteException e) {
                                    logger.error("Get remote logics error : ", e);
                                }
                            }
                        }

                        if (remote != null) {

                            processTransactionInfo(remote, equServerID);
                            //saveTransactionInfo(remote, equServerID);
                            sendSalesInfo(remote, equServerID, equipmentServerSettings == null ? null : equipmentServerSettings.numberAtATime);
                            sendTerminalInfo(remote, equServerID);
                            sendSoftCheckInfo(remote);
                            //sendOrderInfo(remote, equServerID);
                            //sendLegalEntityInfo(remote, equServerID);
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


    private void processTransactionInfo(EquipmentServerInterface remote, String equServerID) throws SQLException, RemoteException, FileNotFoundException, UnsupportedEncodingException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {

        List<TransactionInfo> transactionInfoList = remote.readTransactionInfo(equServerID);
        Collections.sort(transactionInfoList, COMPARATOR);
        for (TransactionInfo<MachineryInfo, ItemInfo> transactionInfo : transactionInfoList) {

            Map<String, List<MachineryInfo>> handlerModelMap = getHandlerModelMap(transactionInfo);

            logger.info("Sending transactions started");
            for (Map.Entry<String, List<MachineryInfo>> entry : handlerModelMap.entrySet()) {
                if (entry.getKey() != null) {
                    try {
                        Object clsHandler = getHandler(entry.getValue().get(0).handlerModel.trim(), remote);
                        transactionInfo.sendTransaction(clsHandler, entry.getValue());
                    } catch (Exception e) {
                        remote.errorTransactionReport(transactionInfo.id, e);
                        return;
                    }
                }
            }
            remote.succeedTransaction(transactionInfo.id);
            logger.info("Sending transactions finished");
        }
    }

    private void saveTransactionInfo(EquipmentServerInterface remote, String equServerID) throws IOException, SQLException {
        logger.info("Saving transaction info");
        List<TransactionInfo> transactionInfoList = remote.readTransactionInfo(equServerID);
        Collections.sort(transactionInfoList, COMPARATOR);
        
        for (TransactionInfo<MachineryInfo, ItemInfo> transactionInfo : transactionInfoList) {

            Map<String, List<MachineryInfo>> handlerModelMap = getHandlerModelMap(transactionInfo);

            for (Map.Entry<String, List<MachineryInfo>> entry : handlerModelMap.entrySet()) {
                if (entry.getKey() != null) {
                    try {
                        String handlerModel = entry.getValue().get(0).handlerModel.trim();
                        Object clsHandler = getHandler(handlerModel, remote);
                        if(clsHandler instanceof TerminalHandler)
                            ((TerminalHandler) clsHandler).saveTransactionInfo(transactionInfo);
                    } catch (Exception e) {
                        remote.errorTransactionReport(transactionInfo.id, e);
                        return;
                    }
                }
            }          
        }
    }

    private void sendSalesInfo(EquipmentServerInterface remote, String equServerID, Integer numberAtATime) throws SQLException, IOException {
        logger.info("Reading CashRegisterInfo");
        List<CashRegisterInfo> cashRegisterInfoList = remote.readCashRegisterInfo(equServerID);
        logger.info("Reading RequestSalesInfo");
        Map<Date, Set<String>> requestSalesInfo = remote.readRequestSalesInfo(equServerID);

        Map<String, List<CashRegisterInfo>> handlerModelCashRegisterMap = new HashMap<String, List<CashRegisterInfo>>();
        for (CashRegisterInfo cashRegister : cashRegisterInfoList) {
            if (!handlerModelCashRegisterMap.containsKey(cashRegister.handlerModel))
                handlerModelCashRegisterMap.put(cashRegister.handlerModel, new ArrayList<CashRegisterInfo>());
            handlerModelCashRegisterMap.get(cashRegister.handlerModel).add(cashRegister);
        }

        for (Map.Entry<String, List<CashRegisterInfo>> entry : handlerModelCashRegisterMap.entrySet()) {
            if (entry.getKey() != null) {

                try {
                    String handlerModel = entry.getKey();//entry.getValue().get(0).handlerModel;
                    if (handlerModel != null) {
                        CashRegisterHandler clsHandler = (CashRegisterHandler) getHandler(handlerModel, remote);

                        Set succeededSoftCheckInfo = clsHandler.requestSucceededSoftCheckInfo(sqlUsername, sqlPassword, sqlIp, sqlPort, sqlDBName);
                        if (succeededSoftCheckInfo != null && !succeededSoftCheckInfo.isEmpty()) {
                            logger.info("Sending succeeded SoftCheckInfo");
                            String result = remote.sendSucceededSoftCheckInfo(succeededSoftCheckInfo);
                            if (result != null)
                                reportEquipmentServerError(remote, equServerID, result);
                        }

                        if (!requestSalesInfo.isEmpty()) {
                            logger.info("Requesting SalesInfo");
                            String result = clsHandler.requestSalesInfo(requestSalesInfo);
                            if (result != null) {
                                reportEquipmentServerError(remote, equServerID, result);
                            }
                        }

                        SalesBatch salesBatch = clsHandler.readSalesInfo(cashRegisterInfoList);
                        if (salesBatch == null) {
                            logger.info("SalesInfo is empty");
                        } else {
                            logger.info("Sending SalesInfo");
                            String result = remote.sendSalesInfo(salesBatch.salesInfoList, equServerID, numberAtATime);
                            if (result != null) {
                                reportEquipmentServerError(remote, equServerID, result);
                            }
                            else {
                                logger.info("Finish Reading starts");
                                clsHandler.finishReadingSalesInfo(salesBatch);
                            }
                        }
                    }
                } catch (Throwable e) {
                    logger.error("Equipment server error: ", e);
                    remote.errorEquipmentServerReport(equServerID, e.fillInStackTrace());
                    return;
                }
            }
        }
    }
    
    private void sendTerminalInfo(EquipmentServerInterface remote, String equServerID) throws RemoteException, SQLException {
        List<TerminalInfo> terminalInfoList = remote.readTerminalInfo(equServerID);

        Map<String, List<MachineryInfo>> handlerModelTerminalMap = new HashMap<String, List<MachineryInfo>>();
        for (TerminalInfo terminal : terminalInfoList) {
            if (!handlerModelTerminalMap.containsKey(terminal.nameModel))
                handlerModelTerminalMap.put(terminal.nameModel, new ArrayList<MachineryInfo>());
            handlerModelTerminalMap.get(terminal.nameModel).add(terminal);
        }

        for (Map.Entry<String, List<MachineryInfo>> entry : handlerModelTerminalMap.entrySet()) {
            if (entry.getKey() != null) {

                try {
                    Object clsHandler = getHandler(entry.getValue().get(0).handlerModel.trim(), remote);
                    List<TerminalDocumentInfo> terminalDocumentInfoList = ((TerminalHandler) clsHandler).readTerminalDocumentInfo(terminalInfoList);
                    String result = remote.sendTerminalDocumentInfo(terminalDocumentInfoList, equServerID);
                    if (result != null) {
                        reportEquipmentServerError(remote, equServerID, result);
                    } else
                        ((TerminalHandler) clsHandler).finishSendingTerminalDocumentInfo(terminalInfoList, terminalDocumentInfoList);
                } catch (Exception e) {
                    logger.error("Equipment server error", e);
                    remote.errorEquipmentServerReport(equServerID, e.fillInStackTrace());
                    return;
                }
            }
        }
    }
    
    private void sendSoftCheckInfo(EquipmentServerInterface remote) throws RemoteException, SQLException {
        logger.info("Reading SoftCheckInfo");
        List<SoftCheckInfo> softCheckInfoList = remote.readSoftCheckInfo();
        if (softCheckInfoList != null && !softCheckInfoList.isEmpty()) {
            logger.info("Sending SoftCheckInfo started");
            for (SoftCheckInfo entry : softCheckInfoList) {
                if (entry.handler != null) {
                    try {
                        Object clsHandler = getHandler(entry.handler.trim(), remote);                       
                        entry.sendSoftCheckInfo(clsHandler);
                        remote.finishSoftCheckInfo(entry.invoiceSet);                       
                    } catch (Exception e) {
                        logger.error("Sending Soft Check Info error", e);
                        return;
                    }
                }
            }
            logger.info("Sending Soft Check Info finished");
        }
    }
    
/*    private void sendOrderInfo(EquipmentServerInterface remote, String equServerID) throws RemoteException, SQLException {
        List<OrderInfo> orderInfoList = remote.readOrderInfo(equServerID);
    }

    private void sendLegalEntityInfo(EquipmentServerInterface remote, String equServerID) throws RemoteException, SQLException {
        List<LegalEntityInfo> legalEntityList = remote.readLegalEntityInfo(equServerID);
    }
    */
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
    
    private void reportEquipmentServerError(EquipmentServerInterface remote, String equServerID, String result) throws RemoteException, SQLException {
        logger.error("Equipment server error: " + result);
        remote.errorEquipmentServerReport(equServerID, new Throwable(result));
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