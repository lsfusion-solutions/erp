package equ.api.cashregister;

import lsfusion.interop.server.RmiServerInterface;

import java.rmi.RemoteException;
import java.sql.SQLException;

public interface PromotionInterface extends RmiServerInterface {
    
    PromotionInfo readPromotionInfo() throws RemoteException;
    
}
