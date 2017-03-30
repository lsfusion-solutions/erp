package equ.clt;

import equ.api.EquipmentServerInterface;
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
import java.text.ParseException;
import java.util.*;

public class SendSalesEquipmentServer {
    private final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");

    static void requestSalesInfo(EquipmentServerInterface remote, List<RequestExchange> requestExchangeList, CashRegisterHandler handler, Set<String> directorySet)
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

    static void sendCashDocument(EquipmentServerInterface remote, String sidEquipmentServer, CashRegisterHandler handler, List<CashRegisterInfo> cashRegisterInfoList)
            throws IOException, SQLException, ClassNotFoundException {
        Set<String> cashDocumentSet = remote.readCashDocumentSet();
        CashDocumentBatch cashDocumentBatch = handler.readCashDocumentInfo(cashRegisterInfoList, cashDocumentSet);
        if (cashDocumentBatch != null && cashDocumentBatch.cashDocumentList != null && !cashDocumentBatch.cashDocumentList.isEmpty()) {
            sendSalesLogger.info("Sending CashDocuments");
            //todo: убрать sidEquipmentServer
            String result = remote.sendCashDocumentInfo(cashDocumentBatch.cashDocumentList, sidEquipmentServer);
            if (result != null) {
                EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, result);
            } else {
                handler.finishReadingCashDocumentInfo(cashDocumentBatch);
            }
        }
    }

    static void sendSalesInfo(EquipmentServerInterface remote, SalesBatch salesBatch, String sidEquipmentServer, CashRegisterHandler handler) throws RemoteException, SQLException {
        if (salesBatch == null || salesBatch.salesInfoList == null || salesBatch.salesInfoList.size() == 0)
            sendSalesLogger.info("SalesInfo is empty");
        else {
            sendSalesLogger.info("Sending SalesInfo : " + salesBatch.salesInfoList.size() + " records");
            try {
                String result = remote.sendSalesInfo(salesBatch.salesInfoList, sidEquipmentServer);
                if (result != null) {
                    EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, result);
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

}
