package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalVMKPrintCopyReceiptClientAction implements ClientAction {

    String logPath;
    String ip;
    int comPort;
    int baudRate;

    public FiscalVMKPrintCopyReceiptClientAction(String logPath, String ip, Integer comPort, Integer baudRate) {
        this.logPath = logPath;
        this.ip = ip;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {
            FiscalVMK.init();

            FiscalVMK.openPort(logPath, ip, comPort, baudRate);
            if (!FiscalVMK.repeatReceipt()) {
                String error = FiscalVMK.getError(false);
                FiscalVMK.cancelReceipt();
                return error;
            }
            FiscalVMK.closePort();
            return null;
        } catch (RuntimeException e) {
            FiscalVMK.cancelReceipt();
            return FiscalVMK.getError(true);
        }
    }
}
