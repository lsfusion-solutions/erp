package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalVMKPrintCopyReceiptClientAction implements ClientAction {

    int baudRate;
    int comPort;

    public FiscalVMKPrintCopyReceiptClientAction(Integer baudRate, Integer comPort) {
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.comPort = comPort == null ? 0 : comPort;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {
            FiscalVMK.init();

            FiscalVMK.openPort(comPort, baudRate);
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
