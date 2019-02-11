package equ.clt;

import equ.api.EquipmentServerInterface;
import equ.api.SoftCheckInfo;
import equ.api.cashregister.CashRegisterHandler;
import org.apache.log4j.Logger;

import java.rmi.RemoteException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SoftCheckEquipmentServer {
    private final static Logger sendSoftCheckLogger = Logger.getLogger("SoftCheckLogger");

    static void sendSoftCheckInfo(EquipmentServerInterface remote) throws RemoteException, SQLException {
        sendSoftCheckLogger.info("Send SoftCheckInfo");
        List<SoftCheckInfo> softCheckInfoList = remote.readSoftCheckInfo();
        if (softCheckInfoList != null && !softCheckInfoList.isEmpty()) {
            sendSoftCheckLogger.info("Sending SoftCheckInfo started");
            for (SoftCheckInfo entry : softCheckInfoList) {
                if (entry.handler != null) {
                    try {
                        Object clsHandler = EquipmentServer.getHandler(entry.handler.trim(), remote);
                        entry.sendSoftCheckInfo(clsHandler);
                        remote.finishSoftCheckInfo(entry.invoiceMap);
                    } catch (Exception e) {
                        sendSoftCheckLogger.error("Sending SoftCheckInfo error", e);
                        return;
                    }
                }
            }
            sendSoftCheckLogger.info("Sending SoftCheckInfo finished");
        }
    }

    static void sendSucceededSoftCheckInfo(EquipmentServerInterface remote, String sidEquipmentServer, CashRegisterHandler handler, List<String> directoryList)
            throws RemoteException, SQLException, ClassNotFoundException {
        Map<String, Timestamp> succeededSoftCheckInfo = handler.requestSucceededSoftCheckInfo(directoryList);
        if (succeededSoftCheckInfo != null && !succeededSoftCheckInfo.isEmpty()) {
            sendSoftCheckLogger.info("Sending succeeded SoftCheckInfo (" + succeededSoftCheckInfo.size() + ")");
            String result = remote.sendSucceededSoftCheckInfo(sidEquipmentServer, succeededSoftCheckInfo);
            if (result != null)
                EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, result);
        }
    }
}