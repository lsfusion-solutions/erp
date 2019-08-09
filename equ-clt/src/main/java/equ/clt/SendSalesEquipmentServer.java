package equ.clt;

import equ.api.EquipmentServerInterface;
import equ.api.MachineryHandler;
import equ.api.RequestExchange;
import equ.api.SalesBatch;
import equ.api.cashregister.CashDocumentBatch;
import equ.api.cashregister.CashRegisterHandler;
import equ.api.cashregister.CashRegisterInfo;
import equ.api.cashregister.ExtraCheckZReportBatch;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.*;

public class SendSalesEquipmentServer {
    private final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");

    static void sendSalesInfo(EquipmentServerInterface remote, String sidEquipmentServer, boolean mergeBatches) throws SQLException, IOException {
        sendSalesLogger.info("Send SalesInfo");

        List<CashRegisterInfo> cashRegisterInfoList = remote.readCashRegisterInfo(sidEquipmentServer);

        List<RequestExchange> requestExchangeList = remote.readRequestExchange();

        Map<String, List<String>> handlerModelDirectoryMap = new HashMap<>();
        for (CashRegisterInfo cashRegister : cashRegisterInfoList) {
            if(!cashRegister.disableSales) {
                List<String> directoryList = handlerModelDirectoryMap.containsKey(cashRegister.handlerModel) ? handlerModelDirectoryMap.get(cashRegister.handlerModel) : new ArrayList<>();
                if(!directoryList.contains(cashRegister.directory)) {
                    directoryList.add(cashRegister.directory);
                }
                handlerModelDirectoryMap.put(cashRegister.handlerModel, directoryList);
            }
        }

        try {
            for (Map.Entry<String, List<String>> entry : handlerModelDirectoryMap.entrySet()) {
                String handlerModel = entry.getKey();
                List<String> directoryList = entry.getValue();

                if (handlerModel != null && !handlerModel.isEmpty()) {

                    MachineryHandler clsHandler = (MachineryHandler) EquipmentServer.getHandler(handlerModel, remote);

                    if(clsHandler instanceof CashRegisterHandler) {
                        CashRegisterHandler handler = (CashRegisterHandler) clsHandler;

                        requestSalesInfo(remote, sidEquipmentServer, getSalesInfoExchangeList(requestExchangeList), handler);

                        SoftCheckEquipmentServer.sendSucceededSoftCheckInfo(remote, sidEquipmentServer, handler, directoryList);

                        sendCashDocument(remote, sidEquipmentServer, handler, cashRegisterInfoList);

                        readSalesInfo(remote, sidEquipmentServer, handler, directoryList, cashRegisterInfoList, mergeBatches);

                        extraCheckZReportSum(remote, sidEquipmentServer, handler, cashRegisterInfoList);

                        checkZReportSum(remote, handler, requestExchangeList);

                    }
                }
            }
        } catch (Throwable e) {
            sendSalesLogger.error("Equipment server error: ", e);
            EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, e);
        }
    }

    private static List<RequestExchange> getSalesInfoExchangeList(List<RequestExchange> requestExchangeList) {
        List<RequestExchange> salesInfoExchangeList = new ArrayList<>();
        for(RequestExchange requestExchange : requestExchangeList) {
            if(requestExchange.isSalesInfoExchange()) {
                salesInfoExchangeList.add(requestExchange);
            }
        }
        return salesInfoExchangeList;
    }

    static void requestSalesInfo(EquipmentServerInterface remote, String sidEquipmentServer, List<RequestExchange> requestExchangeList, CashRegisterHandler handler)
            throws IOException, SQLException {
        try {
            if (!requestExchangeList.isEmpty()) {
                sendSalesLogger.info("Requesting SalesInfo");
                Set<Long> succeededRequests = new HashSet<>();
                Map<Long, Throwable> failedRequests = new HashMap<>();
                Map<Long, Throwable> ignoredRequests = new HashMap<>();

                handler.requestSalesInfo(requestExchangeList, succeededRequests, failedRequests, ignoredRequests);
                if (!succeededRequests.isEmpty())
                    remote.finishRequestExchange(succeededRequests);
                if (!failedRequests.isEmpty())
                    remote.errorRequestExchange(failedRequests);
                if (!ignoredRequests.isEmpty()) {
                    remote.finishRequestExchange(new HashSet<>(ignoredRequests.keySet()));
                    remote.errorRequestExchange(ignoredRequests);
                }
            }
        } catch (Throwable t) {
            sendSalesLogger.error("Request SalesInfo error: ", t);
            EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, t);
        }
    }

    static void sendCashDocument(EquipmentServerInterface remote, String sidEquipmentServer, CashRegisterHandler handler, List<CashRegisterInfo> cashRegisterInfoList)
            throws IOException, SQLException, ClassNotFoundException {
        Set<String> cashDocumentSet = remote.readCashDocumentSet();
        CashDocumentBatch cashDocumentBatch = handler.readCashDocumentInfo(cashRegisterInfoList, cashDocumentSet);
        if (cashDocumentBatch != null && cashDocumentBatch.cashDocumentList != null && !cashDocumentBatch.cashDocumentList.isEmpty()) {
            sendSalesLogger.info("Sending CashDocuments");
            String result = remote.sendCashDocumentInfo(cashDocumentBatch.cashDocumentList);
            if (result != null) {
                EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, result);
            } else {
                handler.finishReadingCashDocumentInfo(cashDocumentBatch);
            }
        }
    }

    static void readSalesInfo(EquipmentServerInterface remote, String sidEquipmentServer, CashRegisterHandler handler,
                               List<String> directoryList, List<CashRegisterInfo> cashRegisterInfoList, boolean mergeBatches) throws IOException, SQLException {
        if(directoryList != null) {

            if (mergeBatches) {

                SalesBatch mergedSalesBatch = null;
                for (String directory : directoryList) {
                    try {
                        SalesBatch salesBatch = handler.readSalesInfo(directory, cashRegisterInfoList);
                        if (salesBatch != null) {
                            if (mergedSalesBatch == null)
                                mergedSalesBatch = salesBatch;
                            else
                                mergedSalesBatch.merge(salesBatch);
                        }
                    } catch (Exception e) {
                        sendSalesLogger.error("Reading SalesInfo", e);
                        EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, e, directory);
                    }
                }
                sendSalesInfo(remote, mergedSalesBatch, sidEquipmentServer, null, handler);

            } else {

                for (String directory : directoryList) {
                    try {
                        SalesBatch salesBatch = handler.readSalesInfo(directory, cashRegisterInfoList);
                        sendSalesInfo(remote, salesBatch, sidEquipmentServer, directory, handler);
                    } catch (Exception e) {
                        sendSalesLogger.error("Reading SalesInfo", e);
                        EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, e, directory);
                    }
                }
            }
        }
    }

    static void sendSalesInfo(EquipmentServerInterface remote, SalesBatch salesBatch, String sidEquipmentServer, String directory, CashRegisterHandler handler) throws RemoteException, SQLException {
        boolean noSalesInfo = salesBatch == null || salesBatch.salesInfoList == null || salesBatch.salesInfoList.isEmpty();
        boolean noCashierTime = salesBatch == null || salesBatch.cashierTimeList == null || salesBatch.cashierTimeList.isEmpty();
        if (noSalesInfo && noCashierTime) {
            sendSalesLogger.info("SalesBatch is empty");
        } else {
            String result = null;
            try {
                if (!noSalesInfo) {
                    sendSalesLogger.info("Sending SalesInfo: " + salesBatch.salesInfoList.size());
                    result = remote.sendSalesInfo(salesBatch.salesInfoList, sidEquipmentServer, directory);
                    if (result != null) {
                        EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, result, directory);
                    }
                }
                if (result == null && !noCashierTime) {
                    sendSalesLogger.info("Sending CashierTime: " + salesBatch.cashierTimeList.size());
                    result = remote.sendCashierTimeList(salesBatch.cashierTimeList);
                    if (result != null) {
                        EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, result, directory);
                    }
                }
                if (result == null) {
                    sendSalesLogger.info("Finish Reading starts");
                    handler.finishReadingSalesInfo(salesBatch);
                }
            } catch (Exception e) {
                sendSalesLogger.error("Sending SalesInfo", e);
                EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, e, directory);
            }
        }
    }

    static void extraCheckZReportSum(EquipmentServerInterface remote, String sidEquipmentServer, CashRegisterHandler handler, List<CashRegisterInfo> cashRegisterInfoList)
            throws RemoteException, SQLException, ClassNotFoundException {
        Map<String, List<Object>> handlerZReportSumMap = handler.readExtraCheckZReport(cashRegisterInfoList);
        if (handlerZReportSumMap != null) {
            ExtraCheckZReportBatch extraCheckResult = handler.compareExtraCheckZReport(handlerZReportSumMap, remote.readZReportSumMap());
            if (extraCheckResult.message.isEmpty()) {
                remote.succeedExtraCheckZReport(extraCheckResult.idZReportList);
            } else {
                EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, extraCheckResult.message);
            }
        }
    }

    static void checkZReportSum(EquipmentServerInterface remote, CashRegisterHandler handler, List<RequestExchange> requestExchangeList)
            throws RemoteException, SQLException, ClassNotFoundException {
        if (!requestExchangeList.isEmpty()) {
            Set<Long> succeededRequestsSet = new HashSet<>();
            for (RequestExchange request : requestExchangeList) {
                if (request.isCheckZReportExchange()) {
                    sendSalesLogger.info("Executing checkZReportSum");

                    Set<String> stockSet = new HashSet<>();
                    for(CashRegisterInfo entry : request.cashRegisterSet)
                        stockSet.add(entry.idDepartmentStore);
                    for(CashRegisterInfo entry : request.extraCashRegisterSet)
                        stockSet.add(entry.idDepartmentStore);

                    for (String idStock : stockSet) {
                        if(idStock != null) {
                            Map<String, List<Object>> zReportSumMap = remote.readRequestZReportSumMap(idStock, request.dateFrom, request.dateTo);
                            Map<Integer, List<List<Object>>> cashRegisterMap = remote.readCashRegistersStock(idStock);
                            for (Map.Entry<Integer, List<List<Object>>> cashRegisterEntry : cashRegisterMap.entrySet()) {
                                List<List<Object>> checkSumResult = zReportSumMap.isEmpty() ? null : handler.checkZReportSum(zReportSumMap, cashRegisterEntry.getValue());
                                if (checkSumResult != null) {
                                    remote.logRequestZReportSumCheck(request.requestExchange, cashRegisterEntry.getKey(), checkSumResult);
                                }
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

}
