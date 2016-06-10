package equ.api;

import equ.api.cashregister.CashierTime;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

public interface SoftCheckInterface extends Remote {

    List<SoftCheckInfo> readSoftCheckInfo() throws RemoteException, SQLException;

    void finishSoftCheckInfo(Map<String, SoftCheckInvoice> invoiceSet) throws RemoteException, SQLException;
    
    String sendSucceededSoftCheckInfo(Map<String, Timestamp> invoiceMap) throws RemoteException, SQLException;

    String sendCashierTimeList(List<CashierTime> cashierTimeList) throws RemoteException, SQLException;
}
