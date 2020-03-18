package equ.api;

import equ.api.cashregister.CashierTime;
import lsfusion.interop.server.RmiServerInterface;

import java.rmi.RemoteException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface SoftCheckInterface extends RmiServerInterface {
    
    String sendSucceededSoftCheckInfo(String sidEquipmentServer, Map<String, LocalDateTime> invoiceMap) throws RemoteException;

    String sendCashierTimeList(List<CashierTime> cashierTimeList) throws RemoteException;
}
