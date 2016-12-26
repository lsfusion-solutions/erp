package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalVMKUpdateDataClientAction implements ClientAction {

    String ip;
    int comPort;
    int baudRate;

    public FiscalVMKUpdateDataClientAction(String ip, Integer comPort, Integer baudRate) {
        this.ip = ip;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;

    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {
        synchronized (FiscalVMK.lock) {
            FiscalVMK.init();
            try {

                FiscalVMK.openPort(ip, comPort, baudRate);
                FiscalVMK.closePort();

            } catch (RuntimeException e) {
                return FiscalVMK.getError(true);
            }
            return null;
        }
    }
}
