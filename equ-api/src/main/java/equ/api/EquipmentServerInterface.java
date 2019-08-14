package equ.api;

import equ.api.cashregister.*;
import equ.api.terminal.TerminalDocumentDetail;
import equ.api.terminal.TerminalInfo;
import equ.api.terminal.TerminalOrder;
import lsfusion.interop.server.RmiServerInterface;

import java.io.IOException;
import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface EquipmentServerInterface extends RmiServerInterface {

    //softCheck
    String sendSucceededSoftCheckInfo(String sidEquipmentServer, Map<String, Timestamp> invoiceSet) throws RemoteException, SQLException;

    //processStopList consumer
    boolean enabledStopListInfo() throws RemoteException, SQLException;
    List<StopListInfo> readStopListInfo() throws RemoteException, SQLException;
    void errorStopListReport(String numberStopList, Exception exception) throws RemoteException, SQLException;
    void succeedStopList(String numberStopList, Set<String> idStockSet) throws RemoteException, SQLException;

    //processDeleteBarcode consumer
    boolean enabledDeleteBarcodeInfo() throws RemoteException;
    List<DeleteBarcodeInfo> readDeleteBarcodeInfoList() throws RemoteException, SQLException;
    void errorDeleteBarcodeReport(Integer nppGroupMachinery, Exception exception) throws RemoteException;
    void finishDeleteBarcode(Integer nppGroupMachinery, boolean markSucceeded) throws RemoteException;
    void succeedDeleteBarcode(Integer nppGroupMachinery, Set<String> deleteBarcodeSet) throws RemoteException;

    //sendTerminalDocument consumer
    boolean enabledTerminalInfo() throws RemoteException;
    List<TerminalInfo> readTerminalInfo(String sidEquipmentServer) throws RemoteException, SQLException;
    String sendTerminalInfo(List<TerminalDocumentDetail> terminalDocumentDetailList) throws RemoteException, SQLException;

    //machineryExchange consumer
    List<MachineryInfo> readMachineryInfo(String sidEquipmentServer) throws RemoteException, SQLException;
    List<RequestExchange> readRequestExchange() throws RemoteException, SQLException;
    void finishRequestExchange(Set<Long> succeededRequestsSet) throws RemoteException, SQLException;
    void errorRequestExchange(Map<Long, Throwable> failedRequestsMap) throws RemoteException, SQLException;
    void errorRequestExchange(Long requestExchange, Throwable t) throws RemoteException, SQLException;

    String sendCashierTimeList(List<CashierTime> cashierTimeList) throws RemoteException, SQLException;
    List<CashierInfo> readCashierInfoList() throws RemoteException, SQLException;
    List<DiscountCard> readDiscountCardList(RequestExchange requestExchange) throws RemoteException, SQLException;
    List<TerminalOrder> readTerminalOrderList(RequestExchange requestExchange) throws RemoteException, SQLException;
    PromotionInfo readPromotionInfo() throws RemoteException, SQLException;

    //sendSales consumer
    List<CashRegisterInfo> readCashRegisterInfo(String sidEquipmentServer) throws RemoteException, SQLException;
    String sendSalesInfo(List<SalesInfo> salesInfoList, String sidEquipmentServer, String directory);
    Set<String> readCashDocumentSet() throws IOException, SQLException;
    String sendCashDocumentInfo(List<CashDocument> cashDocumentList) throws IOException, SQLException;

    //checkZReportSum
    Map<String, List<Object>> readRequestZReportSumMap(String idStock, Date dateFrom, Date dateTo) throws RemoteException;
    Map<Integer, List<List<Object>>> readCashRegistersStock(String idStock) throws RemoteException, SQLException;
    void logRequestZReportSumCheck(Long idRequestExchange, Integer nppGroupMachinery, List<List<Object>> checkSumResult) throws RemoteException, SQLException;

    //extraCheckZReportSum
    Map<String, BigDecimal> readZReportSumMap() throws RemoteException, SQLException;
    void succeedExtraCheckZReport(List<String> idZReportList) throws RemoteException, SQLException;

    //processTransaction consumer
    List<TransactionInfo> readTransactionInfo(String sidEquipmentServer) throws RemoteException, SQLException;
    void processingTransaction(Long transactionId, Timestamp dateTime) throws RemoteException;
    void succeedTransaction(Long transactionId, Timestamp dateTime) throws RemoteException;
    void clearedMachineryTransaction(Long transactionId, List<MachineryInfo> machineryInfoList) throws RemoteException;
    void succeedMachineryTransaction(Long transactionId, List<MachineryInfo> machineryInfoList, Timestamp dateTime) throws RemoteException;
    
    void errorTransactionReport(Long transactionID, Throwable exception) throws RemoteException;

    void errorEquipmentServerReport(String equipmentServer, Throwable exception, String extraData) throws RemoteException;

    EquipmentServerSettings readEquipmentServerSettings(String equipmentServer) throws RemoteException;

    //processMonitor consumer
    boolean needUpdateProcessMonitor(String sidEquipmentServer) throws RemoteException, SQLException;
    void logProcesses(String sidEquipmentServer, String data) throws RemoteException, SQLException;
}
