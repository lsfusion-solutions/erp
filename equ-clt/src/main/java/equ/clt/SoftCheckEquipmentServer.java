package equ.clt;

import equ.api.EquipmentServerInterface;
import equ.api.SoftCheckInterface;
import equ.api.cashregister.CashRegisterHandler;
import org.apache.log4j.Logger;

import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.Map;

public class SoftCheckEquipmentServer {
    private final static Logger sendSoftCheckLogger = Logger.getLogger("SoftCheckLogger");

    static void sendSucceededSoftCheckInfo(EquipmentServerInterface remote, String sidEquipmentServer, CashRegisterHandler handler)
            throws RemoteException, SQLException, ClassNotFoundException {
        Map<String, SoftCheckInterface.SucceededSoftCheckInfo> succeededSoftCheckInfo = handler.requestSucceededSoftCheckInfo();
        if (succeededSoftCheckInfo != null && !succeededSoftCheckInfo.isEmpty()) {
            sendSoftCheckLogger.info("Sending succeeded SoftCheckInfo (" + succeededSoftCheckInfo.size() + ")");
            String result = remote.sendSucceededSoftCheckInfo(sidEquipmentServer, succeededSoftCheckInfo);
            if (result != null)
                EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, result);
        }
    }
}