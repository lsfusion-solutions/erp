package equ.clt;

import equ.api.EquipmentServerInterface;
import equ.api.RequestExchange;
import equ.api.cashregister.CashRegisterHandler;
import org.apache.log4j.Logger;

import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SendSalesEquipmentServer {
    private final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");

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
