package equ.api;

import equ.api.cashregister.*;
import equ.api.terminal.TerminalDocumentDetail;
import equ.api.terminal.TerminalInfo;
import equ.api.terminal.TerminalOrder;
import lsfusion.interop.remote.RmiServerInterface;

import java.io.IOException;
import java.math.BigDecimal;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface EquipmentServerInterface extends RmiServerInterface {

    //sendSoftCheck consumer
    boolean enabledSoftCheckInfo() throws RemoteException, SQLException;
    List<SoftCheckInfo> readSoftCheckInfo() throws RemoteException, SQLException;
    void finishSoftCheckInfo(Map<String, SoftCheckInvoice> invoiceMap) throws RemoteException, SQLException;
    String sendSucceededSoftCheckInfo(String sidEquipmentServer, Map<String, Timestamp> invoiceSet) throws RemoteException, SQLException;

    //processStopList consumer
    boolean enabledStopListInfo() throws RemoteException, SQLException;
    List<StopListInfo> readStopListInfo() throws RemoteException, SQLException;
    void errorStopListReport(String numberStopList, Exception exception) throws RemoteException, SQLException;
    void succeedStopList(String numberStopList, Set<String> idStockSet) throws RemoteException, SQLException;

    //processDeleteBarcode consumer
    boolean enabledDeleteBarcodeInfo() throws RemoteException, SQLException;
    List<DeleteBarcodeInfo> readDeleteBarcodeInfoList() throws RemoteException, SQLException;
    void errorDeleteBarcodeReport(Integer nppGroupMachinery, Exception exception) throws RemoteException, SQLException;
    void succeedDeleteBarcode(Integer nppGroupMachinery) throws RemoteException, SQLException;

    //sendTerminalDocument consumer
    boolean enabledTerminalInfo() throws RemoteException, SQLException;
    List<TerminalInfo> readTerminalInfo(String sidEquipmentServer) throws RemoteException, SQLException;
    String sendTerminalInfo(List<TerminalDocumentDetail> terminalDocumentDetailList, String sidEquipmentServer) throws RemoteException, SQLException;

    //machineryExchange consumer
    List<MachineryInfo> readMachineryInfo(String sidEquipmentServer) throws RemoteException, SQLException;
    List<RequestExchange> readRequestExchange(String sidEquipmentServer) throws RemoteException, SQLException;
    void finishRequestExchange(Set<Integer> succeededRequestsSet) throws RemoteException, SQLException;
    void errorRequestExchange(Map<Integer, String> succeededRequestsMap) throws RemoteException, SQLException;

    String sendCashierTimeList(List<CashierTime> cashierTimeList) throws RemoteException, SQLException;
    List<CashierInfo> readCashierInfoList() throws RemoteException, SQLException;
    List<DiscountCard> readDiscountCardList(String idDiscountCardFrom, String idDiscountCardTo) throws RemoteException, SQLException;
    List<TerminalOrder> readTerminalOrderList(RequestExchange requestExchange) throws RemoteException, SQLException;
    PromotionInfo readPromotionInfo() throws RemoteException, SQLException;

    //sendSales consumer
    List<CashRegisterInfo> readCashRegisterInfo(String sidEquipmentServer) throws RemoteException, SQLException;
    String sendSalesInfo(List<SalesInfo> salesInfoList, String sidEquipmentServer) throws IOException, SQLException;
    Set<String> readCashDocumentSet(String sidEquipmentServer) throws IOException, SQLException;
    String sendCashDocumentInfo(List<CashDocument> cashDocumentList, String sidEquipmentServer) throws IOException, SQLException;

    //checkZReportSum
    Map<String, List<Object>> readRequestZReportSumMap(String idStock, Date dateFrom, Date dateTo) throws RemoteException, SQLException;
    Map<Integer, List<List<Object>>> readCashRegistersStock(String idStock) throws RemoteException, SQLException;
    void logRequestZReportSumCheck(Integer idRequestExchange, Integer nppGroupMachinery, List<List<Object>> checkSumResult) throws RemoteException, SQLException;

    //extraCheckZReportSum
    Map<String, BigDecimal> readZReportSumMap() throws RemoteException, SQLException;
    void succeedExtraCheckZReport(List<String> idZReportList) throws RemoteException, SQLException;

    //processTransaction consumer
    List<TransactionInfo> readTransactionInfo(String sidEquipmentServer) throws RemoteException, SQLException;
    void processingTransaction(Integer transactionId, Timestamp dateTime) throws RemoteException, SQLException;
    void succeedTransaction(Integer transactionId, Timestamp dateTime) throws RemoteException, SQLException;
    void clearedMachineryTransaction(Integer transactionId, List<MachineryInfo> machineryInfoList) throws RemoteException, SQLException;
    void succeedMachineryTransaction(Integer transactionId, List<MachineryInfo> machineryInfoList, Timestamp dateTime) throws RemoteException, SQLException;
    
    void errorTransactionReport(Integer transactionID, Throwable exception) throws RemoteException, SQLException;

    void errorEquipmentServerReport(String equipmentServer, Throwable exception) throws RemoteException, SQLException;

    EquipmentServerSettings readEquipmentServerSettings(String equipmentServer) throws RemoteException, SQLException;
}
