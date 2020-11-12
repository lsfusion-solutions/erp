package equ.clt;

import equ.api.EquipmentServerInterface;
import equ.api.MachineryInfo;
import equ.api.stoplist.StopListInfo;
import equ.api.cashregister.CashRegisterHandler;
import equ.api.scales.ScalesHandler;
import org.apache.log4j.Logger;

import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class StopListEquipmentServer {
    private final static Logger processStopListLogger = Logger.getLogger("StopListLogger");

    static void processStopListInfo(EquipmentServerInterface remote) throws RemoteException, SQLException {
        processStopListLogger.info("Process StopListInfo");
        List<StopListInfo> stopListInfoList = remote.readStopListInfo();
        for (StopListInfo stopListInfo : stopListInfoList) {
            boolean succeeded = true;
            processStopListLogger.info("Start sending stop-list # " + stopListInfo.number);
            for (Map.Entry<String, Set<MachineryInfo>> entry : stopListInfo.handlerMachineryMap.entrySet()) {
                Set<MachineryInfo> machineryInfoSet = entry.getValue();
                try {
                    processStopListLogger.info("Sending stop-list to " + entry.getKey());
                    Object clsHandler = EquipmentServer.getHandler(entry.getKey(), remote);
                    if (clsHandler instanceof CashRegisterHandler)
                        ((CashRegisterHandler) clsHandler).sendStopListInfo(stopListInfo, getDirectorySet(machineryInfoSet));
                    else if (clsHandler instanceof ScalesHandler) {
                        ((ScalesHandler) clsHandler).sendStopListInfo(stopListInfo, machineryInfoSet);
                    }
                } catch (Exception e) {
                    remote.errorStopListReport(stopListInfo.number, e);
                    succeeded = false;
                }
            }
            if (succeeded)
                remote.succeedStopList(stopListInfo.number, stopListInfo.idStockSet);
        }
        if(!stopListInfoList.isEmpty())
            processStopListLogger.info(String.format("Processed %s StopListInfo", stopListInfoList.size()));
    }

    private static Set<String> getDirectorySet(Set<MachineryInfo> machineryInfoSet) {
        Set<String> directorySet = new HashSet<>();
        for(MachineryInfo machinery : machineryInfoSet) {
            if(machinery.directory != null)
                directorySet.add(machinery.directory);
        }
        return directorySet;
    }
}
