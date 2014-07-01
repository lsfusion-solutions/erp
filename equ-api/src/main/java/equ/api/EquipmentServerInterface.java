package equ.api;

import equ.api.cashregister.CashDocument;
import equ.api.cashregister.CashRegisterInfo;
import equ.api.cashregister.RequestExchange;
import equ.api.cashregister.StopListInfo;
import equ.api.terminal.TerminalDocumentDetail;
import equ.api.terminal.TerminalInfo;

import java.io.IOException;
import java.math.BigDecimal;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface EquipmentServerInterface extends Remote {

    List<SoftCheckInfo> readSoftCheckInfo() throws RemoteException, SQLException;

    void finishSoftCheckInfo(Map<String, SoftCheckInvoice> invoiceMap) throws RemoteException, SQLException;
    
    String sendSucceededSoftCheckInfo(Map<String, Timestamp> invoiceSet) throws RemoteException, SQLException;
    
    List<TransactionInfo> readTransactionInfo(String sidEquipmentServer) throws RemoteException, SQLException;

    List<StopListInfo> readStopListInfo(String sidEquipmentServer) throws RemoteException, SQLException;

    void errorStopListReport(String numberStopList, Exception exception) throws RemoteException, SQLException;

    void succeedStopList(String numberStopList, Set<String> idStockSet) throws RemoteException, SQLException;

    List<CashRegisterInfo> readCashRegisterInfo(String sidEquipmentServer) throws RemoteException, SQLException;

    List<RequestExchange> readRequestExchange(String sidEquipmentServer) throws RemoteException, SQLException;

    void finishRequestExchange(Set<Integer> succeededRequestsSet) throws RemoteException, SQLException;

    String sendSalesInfo(List<SalesInfo> salesInfoList, String sidEquipmentServer, Integer numberAtATime) throws IOException, SQLException;

    Set<String> readCashDocumentSet(String sidEquipmentServer) throws IOException, SQLException;

    String sendCashDocumentInfo(List<CashDocument> cashDocumentList, String sidEquipmentServer) throws IOException, SQLException;

    void succeedTransaction(Integer transactionID, Timestamp dateTime) throws RemoteException, SQLException;

    void errorTransactionReport(Integer transactionID, Exception exception) throws RemoteException, SQLException;

    void errorEquipmentServerReport(String equipmentServer, Throwable exception) throws RemoteException, SQLException;

    EquipmentServerSettings readEquipmentServerSettings(String equipmentServer) throws RemoteException, SQLException;

    List<byte[][]> readLabelFormats (List<String> scalesModelsList) throws RemoteException, SQLException;

    List<TerminalInfo> readTerminalInfo(String sidEquipmentServer) throws RemoteException, SQLException;

    String sendTerminalInfo(List<TerminalDocumentDetail> terminalDocumentDetailList, String sidEquipmentServer) throws RemoteException, SQLException;

    Map<String,BigDecimal> readRequestZReportSumMap(RequestExchange request) throws RemoteException, SQLException;
}
