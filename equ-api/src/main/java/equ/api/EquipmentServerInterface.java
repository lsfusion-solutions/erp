package equ.api;

import equ.api.cashregister.*;
import equ.api.terminal.TerminalDocumentDetail;
import equ.api.terminal.TerminalInfo;
import equ.api.terminal.TerminalOrder;
import lsfusion.interop.server.RmiServerInterface;

import java.io.IOException;
import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface EquipmentServerInterface extends RmiServerInterface {

    int getEquApiVersion() throws RemoteException, SQLException;

    //softCheck
    String sendSucceededSoftCheckInfo(String sidEquipmentServer, Map<String, LocalDateTime> invoiceSet) throws RemoteException, SQLException;

    //processStopList consumer
    boolean enabledStopListInfo() throws RemoteException, SQLException;
    List<StopListInfo> readStopListInfo() throws RemoteException, SQLException;
    void errorStopListReport(String numberStopList, Exception exception) throws RemoteException, SQLException;
    void succeedStopList(String numberStopList, Set<String> idStockSet) throws RemoteException, SQLException;

    //processDeleteBarcode consumer
    boolean enabledDeleteBarcodeInfo() throws RemoteException, SQLException;
    List<DeleteBarcodeInfo> readDeleteBarcodeInfoList() throws RemoteException, SQLException;
    void errorDeleteBarcodeReport(Integer nppGroupMachinery, Exception exception) throws RemoteException, SQLException;
    void finishDeleteBarcode(Integer nppGroupMachinery, boolean markSucceeded) throws RemoteException, SQLException;
    void succeedDeleteBarcode(Integer nppGroupMachinery, Set<String> deleteBarcodeSet) throws RemoteException, SQLException;

    //sendTerminalDocument consumer
    boolean enabledTerminalInfo() throws RemoteException, SQLException;
    List<TerminalInfo> readTerminalInfo(String sidEquipmentServer) throws RemoteException, SQLException;
    String sendTerminalInfo(List<TerminalDocumentDetail> terminalDocumentDetailList) throws RemoteException, SQLException;

    //machineryExchange consumer
    List<MachineryInfo> readMachineryInfo(String sidEquipmentServer) throws RemoteException, SQLException;
    List<RequestExchange> readRequestExchange(String sidEquipmentServer) throws RemoteException, SQLException;
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
    String sendSalesInfo(List<SalesInfo> salesInfoList, String sidEquipmentServer, String directory) throws IOException, SQLException;
    Set<String> readCashDocumentSet() throws IOException, SQLException;
    String sendCashDocumentInfo(List<CashDocument> cashDocumentList) throws IOException, SQLException;

    //checkZReportSum
    Map<String, List<Object>> readRequestZReportSumMap(String idStock, LocalDate dateFrom, LocalDate dateTo) throws RemoteException, SQLException;
    Map<Integer, List<List<Object>>> readCashRegistersStock(String idStock) throws RemoteException, SQLException;
    void logRequestZReportSumCheck(Long idRequestExchange, Integer nppGroupMachinery, List<List<Object>> checkSumResult) throws RemoteException, SQLException;

    //extraCheckZReportSum
    Map<String, BigDecimal> readZReportSumMap() throws RemoteException, SQLException;
    void succeedExtraCheckZReport(List<String> idZReportList) throws RemoteException, SQLException;

    //processTransaction consumer
    List<TransactionInfo> readTransactionInfo(String sidEquipmentServer) throws RemoteException, SQLException;
    void processingTransaction(Long transactionId, LocalDateTime dateTime) throws RemoteException, SQLException;
    void succeedTransaction(Long transactionId, LocalDateTime dateTime) throws RemoteException, SQLException;
    void clearedMachineryTransaction(Long transactionId, List<MachineryInfo> machineryInfoList) throws RemoteException, SQLException;
    void succeedMachineryTransaction(Long transactionId, List<MachineryInfo> machineryInfoList, LocalDateTime dateTime) throws RemoteException, SQLException;
    
    void errorTransactionReport(Long transactionID, Throwable exception) throws RemoteException, SQLException;

    void errorEquipmentServerReport(String equipmentServer, Throwable exception, String extraData) throws RemoteException, SQLException;

    EquipmentServerSettings readEquipmentServerSettings(String equipmentServer) throws RemoteException, SQLException;

    //processMonitor consumer
    boolean needUpdateProcessMonitor(String sidEquipmentServer) throws RemoteException, SQLException;
    void logProcesses(String sidEquipmentServer, String data) throws RemoteException, SQLException;
}
