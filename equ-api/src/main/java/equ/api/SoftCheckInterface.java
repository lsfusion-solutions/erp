package equ.api;

import equ.api.cashregister.CashierTime;
import lsfusion.interop.server.RmiServerInterface;

import java.rmi.RemoteException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

public interface SoftCheckInterface extends RmiServerInterface {

    List<SoftCheckInfo> readSoftCheckInfo() throws RemoteException, SQLException;

    void finishSoftCheckInfo(Map<String, SoftCheckInvoice> invoiceSet) throws RemoteException, SQLException;
    
    String sendSucceededSoftCheckInfo(String sidEquipmentServer, Map<String, Timestamp> invoiceMap) throws RemoteException, SQLException;

    String sendCashierTimeList(List<CashierTime> cashierTimeList) throws RemoteException, SQLException;
}
