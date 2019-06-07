package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalVMKUpdateDataClientAction extends FiscalVMKClientAction {

    public FiscalVMKUpdateDataClientAction(boolean isUnix, String logPath, String ip, String comPort, Integer baudRate) {
        super(isUnix, logPath, ip, comPort, baudRate);
    }

    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {
        try {
            FiscalVMK.openPort(isUnix, logPath, ip, comPort, baudRate);
            FiscalVMK.closePort();
        } catch (RuntimeException e) {
            return FiscalVMK.getError(true);
        }
        return null;
    }
}
