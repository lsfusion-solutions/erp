package equ.api;

import equ.api.cashregister.CashierTime;
import lsfusion.interop.server.RmiServerInterface;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface SoftCheckInterface extends RmiServerInterface {
    
    String sendSucceededSoftCheckInfo(String sidEquipmentServer, Map<String, SucceededSoftCheckInfo> invoiceMap) throws RemoteException;

    String sendCashierTimeList(List<CashierTime> cashierTimeList) throws RemoteException;

    class SucceededSoftCheckInfo implements Serializable {
        public LocalDateTime dateTime;
        public Integer nppGroupMachinery;

        public SucceededSoftCheckInfo(LocalDateTime dateTime, Integer nppGroupMachinery) {
            this.dateTime = dateTime;
            this.nppGroupMachinery = nppGroupMachinery;
        }
    }
}
