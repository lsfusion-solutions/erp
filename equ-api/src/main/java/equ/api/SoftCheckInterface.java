package equ.api;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.sql.Date;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface SoftCheckInterface extends Remote {

    List<SoftCheckInfo> readSoftCheckInfo() throws RemoteException, SQLException;

    void finishSoftCheckInfo(Map<String, SoftCheckInvoice> invoiceSet) throws RemoteException, SQLException;
    
    String sendSucceededSoftCheckInfo(Map<String, Date> invoiceMap) throws RemoteException, SQLException;
}
