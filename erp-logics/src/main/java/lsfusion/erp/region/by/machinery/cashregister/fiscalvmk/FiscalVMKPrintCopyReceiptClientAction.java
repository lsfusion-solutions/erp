package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalVMKPrintCopyReceiptClientAction extends FiscalVMKClientAction {

    public FiscalVMKPrintCopyReceiptClientAction(boolean isUnix, String logPath, String ip, String comPort, Integer baudRate) {
        super(isUnix, logPath, ip, comPort, baudRate);
    }

    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {
            FiscalVMK.openPort(isUnix, logPath, ip, comPort, baudRate);
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
