package equ.api.cashregister;

import lsfusion.interop.server.RmiServerInterface;

import java.rmi.RemoteException;

public interface PromotionInterface extends RmiServerInterface {
    
    PromotionInfo readPromotionInfo() throws RemoteException;
    
}
