package equ.clt;

import equ.api.DeleteBarcodeInfo;
import equ.api.EquipmentServerInterface;
import equ.api.cashregister.CashRegisterHandler;
import org.apache.log4j.Logger;

import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.List;

class DeleteBarcodeEquipmentServer {
    private final static Logger processDeleteBarcodeLogger = Logger.getLogger("DeleteBarcodeLogger");

    static void processDeleteBarcodeInfo(EquipmentServerInterface remote) throws RemoteException, SQLException {
        processDeleteBarcodeLogger.info("Process DeleteBarcodeInfo");
        List<DeleteBarcodeInfo> deleteBarcodeInfoList = remote.readDeleteBarcodeInfoList();
        for(DeleteBarcodeInfo deleteBarcodeInfo : deleteBarcodeInfoList) {
            boolean succeeded = true;
            boolean markSucceeded = true;
            try {
                Object clsHandler = EquipmentServer.getHandler(deleteBarcodeInfo.handlerModelGroupMachinery, remote);
                if (clsHandler instanceof CashRegisterHandler) {
                    markSucceeded = ((CashRegisterHandler) clsHandler).sendDeleteBarcodeInfo(deleteBarcodeInfo);
                }
            } catch (Exception e) {
                remote.errorDeleteBarcodeReport(deleteBarcodeInfo.nppGroupMachinery, e);
                succeeded = false;
            }

            if (succeeded)
                remote.finishDeleteBarcode(deleteBarcodeInfo.nppGroupMachinery, markSucceeded);
            if (!deleteBarcodeInfo.barcodeList.isEmpty() && markSucceeded)
                processDeleteBarcodeLogger.info(String.format("Deleted %s barcodes for GroupMachinery %s", deleteBarcodeInfo.barcodeList.size(), deleteBarcodeInfo.nppGroupMachinery));
        }
    }
}