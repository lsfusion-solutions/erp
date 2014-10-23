package equ.api.cashregister;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.sql.SQLException;

public interface PromotionInterface extends Remote {
    
    PromotionInfo readPromotionInfo() throws RemoteException, SQLException;
    
}
