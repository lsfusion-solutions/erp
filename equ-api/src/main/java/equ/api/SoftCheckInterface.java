package equ.api;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

public interface SoftCheckInterface extends Remote {

    List<SoftCheckInfo> readSoftCheckInfo() throws RemoteException, SQLException;

    void finishSoftCheckInfo(Set<String> invoiceSet) throws RemoteException, SQLException;
    
    String sendSucceededSoftCheckInfo(Set invoiceSet) throws RemoteException, SQLException;
}
