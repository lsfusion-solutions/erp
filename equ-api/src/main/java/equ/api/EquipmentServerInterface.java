package equ.api;

import equ.api.terminal.TerminalDocumentDetail;
import equ.api.terminal.TerminalInfo;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface EquipmentServerInterface extends Remote {

    List<SoftCheckInfo> readSoftCheckInfo() throws RemoteException, SQLException;

    void finishSoftCheckInfo(Set<String> invoiceSet) throws RemoteException, SQLException;
    
    String sendSucceededSoftCheckInfo(Set invoiceSet) throws RemoteException, SQLException;
    
    List<TransactionInfo> readTransactionInfo(String equServerID) throws RemoteException, SQLException;

    List<CashRegisterInfo> readCashRegisterInfo(String equServerID) throws RemoteException, SQLException;

    Map<Date, Set<String>> readRequestSalesInfo(String equServerID) throws RemoteException, SQLException;

    String sendSalesInfo(List<SalesInfo> salesInfoList, String equServerID, Integer numberAtATime) throws IOException, SQLException;

    void succeedTransaction(Integer transactionID) throws RemoteException, SQLException;

    void errorTransactionReport(Integer transactionID, Exception exception) throws RemoteException, SQLException;

    void errorEquipmentServerReport(String equipmentServer, Throwable exception) throws RemoteException, SQLException;

    EquipmentServerSettings readEquipmentServerSettings(String equipmentServer) throws RemoteException, SQLException;

    List<byte[][]> readLabelFormats (List<String> scalesModelsList) throws RemoteException, SQLException;

    List<TerminalInfo> readTerminalInfo(String equServerID) throws RemoteException, SQLException;

    String sendTerminalInfo(List<TerminalDocumentDetail> terminalDocumentDetailList, String equServerID) throws RemoteException, SQLException;

}
