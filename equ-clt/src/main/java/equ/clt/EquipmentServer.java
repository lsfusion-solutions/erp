package equ.clt;

import equ.api.*;
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
import java.util.*;

public class EquipmentServer {

    private Thread thread;

    protected final static Logger logger = Logger.getLogger(EquipmentServer.class);
    Map<String, Object> handlerMap = new HashMap<String, Object>();
    EquipmentServerSettings equipmentServerSettings;

    public EquipmentServer(final String equServerID, final String serverUrl, final String serverDB) {
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
                            sendSalesInfo(remote, equServerID);
                            logger.info("transaction complete");
                        }

                    } catch (Exception e) {
                        logger.error("Unhandled exception : ", e);
                        remote = null;
                    }

                    try {
                        Thread.sleep(millis);
                    } catch (InterruptedException e) {
                        logger.info("Thread has been interrupted : ", e);
                        break;
                    }
                }
            }
        });

        thread.start();
    }


    private void processTransactionInfo(EquipmentServerInterface remote, String equServerID) throws SQLException, RemoteException, FileNotFoundException, UnsupportedEncodingException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        List<SoftCheckInfo> softCheckInfoList = remote.readSoftCheckInfo();
        if (softCheckInfoList != null) {

            for (SoftCheckInfo entry : softCheckInfoList) {
                if (entry.handler != null) {
                    try {
                        Object clsHandler = getHandler(entry.handler.trim(), remote);
                        entry.sendSoftCheckInfo(clsHandler);
                        remote.finishSoftCheckInfo(entry.invoiceSet);
                    } catch (Exception e) {
                        logger.error("sendSoftCheckInfo error", e);
                        return;
                    }
                }
            }
        }

        List<TransactionInfo> transactionInfoList = remote.readTransactionInfo(equServerID);
        Collections.sort(transactionInfoList, COMPARATOR);
        for (TransactionInfo<MachineryInfo> transaction : transactionInfoList) {

            Map<String, List<MachineryInfo>> handlerModelMap = new HashMap<String, List<MachineryInfo>>();
            for (MachineryInfo machinery : transaction.machineryInfoList) {
                if (machinery.handlerModel != null) {
                    if (!handlerModelMap.containsKey(machinery.handlerModel))
                        handlerModelMap.put(machinery.handlerModel, new ArrayList<MachineryInfo>());
                    handlerModelMap.get(machinery.handlerModel).add(machinery);
                }
            }

            for (Map.Entry<String, List<MachineryInfo>> entry : handlerModelMap.entrySet()) {
                if (entry.getKey() != null) {
                    try {
                        Object clsHandler = getHandler(entry.getValue().get(0).handlerModel.trim(), remote);
                        transaction.sendTransaction(clsHandler, entry.getValue());
                    } catch (Exception e) {
                        remote.errorTransactionReport(transaction.id, e);
                        return;
                    }
                }
            }
            remote.succeedTransaction(transaction.id);
        }
    }

    private void sendSalesInfo(EquipmentServerInterface remote, String equServerID) throws SQLException, IOException {
        List<CashRegisterInfo> cashRegisterInfoList = remote.readCashRegisterInfo(equServerID);
        Map<Date, Set<String>> requestSalesInfo = remote.readRequestSalesInfo(equServerID);

        Map<String, List<MachineryInfo>> handlerModelCashRegisterMap = new HashMap<String, List<MachineryInfo>>();
        for (CashRegisterInfo cashRegister : cashRegisterInfoList) {
            if (!handlerModelCashRegisterMap.containsKey(cashRegister.nameModel))
                handlerModelCashRegisterMap.put(cashRegister.nameModel, new ArrayList<MachineryInfo>());
            handlerModelCashRegisterMap.get(cashRegister.nameModel).add(cashRegister);
        }

        for (Map.Entry<String, List<MachineryInfo>> entry : handlerModelCashRegisterMap.entrySet()) {
            if (entry.getKey() != null) {

                try {
                    String handlerModel = entry.getValue().get(0).handlerModel;
                    if (handlerModel != null) {
                        CashRegisterHandler clsHandler = (CashRegisterHandler) getHandler(handlerModel, remote);

                        Set succeededSoftCheckInfo = clsHandler.requestSucceededSoftCheckInfo();
                        if (succeededSoftCheckInfo != null && !succeededSoftCheckInfo.isEmpty()) {
                            String result = remote.sendSucceededSoftCheckInfo(succeededSoftCheckInfo);
                            if (result != null)
                                remote.errorEquipmentServerReport(equServerID, new Throwable(result));
                        }

                        if (!requestSalesInfo.isEmpty()) {
                            String result = clsHandler.requestSalesInfo(requestSalesInfo);
                            if (result != null)
                                remote.errorEquipmentServerReport(equServerID, new Throwable(result));
                        }

                        SalesBatch salesBatch = clsHandler.readSalesInfo(cashRegisterInfoList);
                        if (salesBatch != null) {
                            String result = remote.sendSalesInfo(salesBatch.salesInfoList, equServerID);
                            if (result != null)
                                remote.errorEquipmentServerReport(equServerID, new Throwable(result));
                            else
                                clsHandler.finishReadingSalesInfo(salesBatch);
                        }
                    }
                } catch (Exception e) {
                    remote.errorEquipmentServerReport(equServerID, e.fillInStackTrace());
                    return;
                }
            }
        }

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
                    if (result != null)
                        remote.errorEquipmentServerReport(equServerID, new Throwable(result));
                    else
                        ((TerminalHandler) clsHandler).finishSendingTerminalDocumentInfo(terminalInfoList, terminalDocumentInfoList);
                } catch (Exception e) {
                    remote.errorEquipmentServerReport(equServerID, e.fillInStackTrace());
                    return;
                }
            }
        }
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

    private static Comparator<TransactionInfo> COMPARATOR = new Comparator<TransactionInfo>() {
        public int compare(TransactionInfo o1, TransactionInfo o2) {
            return o1.dateTimeCode.compareTo(o2.dateTimeCode);
        }
    };

    public void stop() {
        thread.interrupt();
    }
}